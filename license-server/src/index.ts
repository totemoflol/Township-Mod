interface Env {
  LICENSE_DB: D1Database;
  ADMIN_TOKEN: string;
  LICENSE_PEPPER: string;
}

type LicenseRow = {
  id: string;
  key_hash: string;
  key_hint: string;
  status: "active" | "revoked";
  expires_at: string | null;
  device_id: string | null;
  created_at: string;
  activated_at: string | null;
  last_seen_at: string | null;
  note: string | null;
};

const KEY_ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ";
const KEY_PATTERN = /^TOT_[A-HJ-NP-Z]{5}$/;

function json(data: unknown, status = 200): Response {
  return new Response(JSON.stringify(data), {
    status,
    headers: {
      "content-type": "application/json; charset=utf-8",
      "cache-control": "no-store",
      "access-control-allow-origin": "*",
      "access-control-allow-methods": "GET,POST,OPTIONS",
      "access-control-allow-headers": "content-type,authorization",
    },
  });
}

function html(source: string): Response {
  return new Response(source, {
    headers: {
      "content-type": "text/html; charset=utf-8",
      "cache-control": "no-store",
      "x-content-type-options": "nosniff",
      "content-security-policy": "default-src 'self' 'unsafe-inline'; connect-src 'self'; frame-ancestors 'none'",
    },
  });
}

function randomId(): string {
  return crypto.randomUUID();
}

function randomPart(length = 5): string {
  const bytes = crypto.getRandomValues(new Uint8Array(length));
  return Array.from(bytes, (byte) => KEY_ALPHABET[byte % KEY_ALPHABET.length]).join("");
}

function generateLicenseKey(): string {
  return `TOT_${randomPart(5)}`;
}

async function sha256(value: string): Promise<string> {
  const bytes = new TextEncoder().encode(value);
  const digest = await crypto.subtle.digest("SHA-256", bytes);
  return Array.from(new Uint8Array(digest), (byte) => byte.toString(16).padStart(2, "0")).join("");
}

async function constantTimeEquals(a: string, b: string): Promise<boolean> {
  const [left, right] = await Promise.all([sha256(a), sha256(b)]);
  let diff = 0;
  for (let i = 0; i < left.length; i++) diff |= left.charCodeAt(i) ^ right.charCodeAt(i);
  return diff === 0;
}

function normalizeKey(value: unknown): string {
  if (typeof value !== "string") return "";
  return value.trim().toUpperCase().replace(/\s+/g, "");
}

function maskedHint(key: string): string {
  const parts = key.split("_");
  return key.length === 9 ? `TOT_•••${key.slice(-2)}` : "TOT_•••••";
}

async function readBody(request: Request): Promise<Record<string, unknown> | null> {
  try {
    const value: unknown = await request.json();
    if (!value || typeof value !== "object" || Array.isArray(value)) return null;
    return value as Record<string, unknown>;
  } catch {
    return null;
  }
}

async function adminAuthorized(request: Request, env: Env): Promise<boolean> {
  const header = request.headers.get("authorization") || "";
  if (!header.startsWith("Bearer ")) return false;
  return constantTimeEquals(header.slice(7), env.ADMIN_TOKEN);
}

async function recordAttempt(request: Request, env: Env): Promise<boolean> {
  const ip = request.headers.get("CF-Connecting-IP") || "unknown";
  const ipHash = await sha256(ip + ":" + env.LICENSE_PEPPER);
  const now = Math.floor(Date.now() / 1000);
  await env.LICENSE_DB.prepare("DELETE FROM activation_attempts WHERE attempted_at < ?")
    .bind(now - 86400).run();
  const recent = await env.LICENSE_DB.prepare(
    "SELECT COUNT(*) AS total FROM activation_attempts WHERE ip_hash = ? AND attempted_at > ?"
  ).bind(ipHash, now - 900).first<{ total: number }>();
  if ((recent?.total ?? 0) >= 30) return false;
  await env.LICENSE_DB.prepare(
    "INSERT INTO activation_attempts (ip_hash, attempted_at) VALUES (?, ?)"
  ).bind(ipHash, now).run();
  return true;
}

async function activate(request: Request, env: Env): Promise<Response> {
  if (!(await recordAttempt(request, env))) {
    return json({ ok: false, code: "RATE_LIMITED", message: "Too many attempts. Try again later." }, 429);
  }

  const body = await readBody(request);
  const key = normalizeKey(body?.key);
  const deviceId = typeof body?.deviceId === "string" ? body.deviceId.trim().toLowerCase() : "";

  if (!KEY_PATTERN.test(key)) {
    return json({ ok: false, code: "INVALID_KEY", message: "Enter a valid ToT licence key." }, 400);
  }
  if (!/^[a-f0-9]{64}$/.test(deviceId)) {
    return json({ ok: false, code: "INVALID_DEVICE", message: "Could not identify this device. Restart the app and try again." }, 400);
  }

  const keyHash = await sha256(key + ":" + env.LICENSE_PEPPER);
  let license = await env.LICENSE_DB.prepare(
    "SELECT * FROM licenses WHERE key_hash = ?"
  ).bind(keyHash).first<LicenseRow>();

  if (!license) {
    return json({ ok: false, code: "NOT_FOUND", message: "This licence key is not recognised." }, 404);
  }
  if (license.status !== "active") {
    return json({ ok: false, code: "REVOKED", message: "This licence has been disabled. Contact support." }, 403);
  }
  const now = new Date();
  if (license.expires_at && Date.parse(license.expires_at) <= now.getTime()) {
    return json({ ok: false, code: "EXPIRED", message: "This licence has expired." }, 403);
  }

  if (!license.device_id) {
    await env.LICENSE_DB.prepare(
      "UPDATE licenses SET device_id = ?, activated_at = ?, last_seen_at = ? WHERE id = ? AND device_id IS NULL AND status = 'active'"
    ).bind(deviceId, now.toISOString(), now.toISOString(), license.id).run();
    license = await env.LICENSE_DB.prepare(
      "SELECT * FROM licenses WHERE id = ?"
    ).bind(license.id).first<LicenseRow>();
  }

  if (!license || license.device_id !== deviceId) {
    return json({
      ok: false,
      code: "DEVICE_LIMIT",
      message: "This key is already linked to another device. Contact support to reset the binding.",
    }, 409);
  }

  await env.LICENSE_DB.prepare(
    "UPDATE licenses SET last_seen_at = ? WHERE id = ?"
  ).bind(now.toISOString(), license.id).run();

  return json({
    ok: true,
    plan: "paid",
    licenseId: license.id,
    deviceBound: true,
    expiresAt: license.expires_at,
    serverTime: now.toISOString(),
  });
}

async function adminApi(request: Request, env: Env, url: URL): Promise<Response> {
  if (!(await adminAuthorized(request, env))) {
    return json({ ok: false, message: "Admin authentication required." }, 401);
  }

  if (url.pathname === "/admin/api/licenses" && request.method === "GET") {
    const result = await env.LICENSE_DB.prepare(
      "SELECT id, key_hint, status, expires_at, device_id, created_at, activated_at, last_seen_at, note FROM licenses ORDER BY created_at DESC LIMIT 500"
    ).all();
    return json({ ok: true, licenses: result.results ?? [] });
  }

  if (url.pathname === "/admin/api/licenses" && request.method === "POST") {
    const body = await readBody(request);
    const count = Number(body?.count ?? 1);
    const expiryInput = body?.expiresInDays;
    const expiresInDays = expiryInput === null || expiryInput === "" ? null : Number(expiryInput);
    const note = typeof body?.note === "string" ? body.note.trim().slice(0, 120) : "";

    if (!Number.isInteger(count) || count < 1 || count > 100) {
      return json({ ok: false, message: "Generate between 1 and 100 keys at a time." }, 400);
    }
    if (expiresInDays !== null && (!Number.isInteger(expiresInDays) || expiresInDays < 1 || expiresInDays > 3650)) {
      return json({ ok: false, message: "Expiry must be between 1 and 3650 days, or set to never." }, 400);
    }

    const created: Array<{ id: string; key: string; expiresAt: string | null }> = [];
    const now = new Date();
    const expiresAt = expiresInDays === null
      ? null
      : new Date(now.getTime() + expiresInDays * 86400000).toISOString();

    for (let i = 0; i < count; i++) {
      let inserted = false;
      for (let attempt = 0; attempt < 5 && !inserted; attempt++) {
        const key = generateLicenseKey();
        const id = randomId();
        const hash = await sha256(key + ":" + env.LICENSE_PEPPER);
        try {
          await env.LICENSE_DB.prepare(
            "INSERT INTO licenses (id, key_hash, key_hint, status, expires_at, created_at, note) VALUES (?, ?, ?, 'active', ?, ?, ?)"
          ).bind(id, hash, maskedHint(key), expiresAt, now.toISOString(), note || null).run();
          created.push({ id, key, expiresAt });
          inserted = true;
        } catch (error) {
          if (attempt === 4) throw error;
        }
      }
    }
    return json({ ok: true, created }, 201);
  }

  const match = url.pathname.match(/^\/admin\/api\/licenses\/([0-9a-f-]+)\/(revoke|unbind)$/i);
  if (match && request.method === "POST") {
    const [, id, action] = match;
    if (action === "revoke") {
      const result = await env.LICENSE_DB.prepare(
        "UPDATE licenses SET status = 'revoked' WHERE id = ?"
      ).bind(id).run();
      if (!result.meta.changes) return json({ ok: false, message: "Licence not found." }, 404);
      return json({ ok: true });
    }
    const result = await env.LICENSE_DB.prepare(
      "UPDATE licenses SET device_id = NULL, activated_at = NULL, last_seen_at = NULL WHERE id = ? AND status = 'active'"
    ).bind(id).run();
    if (!result.meta.changes) return json({ ok: false, message: "Active licence not found." }, 404);
    return json({ ok: true });
  }

  return json({ ok: false, message: "Admin route not found." }, 404);
}

const ADMIN_HTML = `<!doctype html>
<html lang="en">
<head>
<meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">
<title>ToT Tools · Licence Admin</title>
<style>
:root{color-scheme:dark;font-family:Inter,system-ui,sans-serif;background:#07110b;color:#e8f2ea}
*{box-sizing:border-box}body{margin:0;padding:24px;background:radial-gradient(ellipse at 15% 0%,#163521 0,transparent 55%),#07110b}
main{max-width:1040px;margin:0 auto}.card{background:#101d14;border:1px solid #294233;border-radius:18px;padding:20px;margin:16px 0}
h1{margin:0;font-size:26px}h2{font-size:15px;color:#7df0a8;letter-spacing:1px}
p,small{color:#a6b8aa}.row{display:flex;gap:12px;flex-wrap:wrap;align-items:end}
input,select{background:#08140d;border:1px solid #345340;color:#e8f2ea;padding:12px;border-radius:10px;min-width:180px}
button{background:#7df0a8;color:#062311;border:0;border-radius:10px;padding:12px 16px;font-weight:800;cursor:pointer}
button.secondary{background:#263b2c;color:#d8eadc}button.danger{background:#512b2b;color:#ffdada}
button:disabled{opacity:.5;cursor:not-allowed}
table{width:100%;border-collapse:collapse;font-size:13px}th,td{padding:11px 8px;border-bottom:1px solid #294233;text-align:left;vertical-align:top}
th{color:#a6b8aa;font-weight:600}.pill{display:inline-block;padding:4px 8px;border-radius:30px;background:#1d442b;color:#9af1b6}
.pill.off{background:#482b2b;color:#ffb9b9}.keyout{font:700 15px ui-monospace,monospace;word-break:break-all;color:#9fffb8;background:#07110b;padding:12px;border-radius:10px}
#notice{white-space:pre-wrap;color:#eacb77;margin-top:10px}.hidden{display:none}.actions{display:flex;gap:6px;flex-wrap:wrap}
@media(max-width:640px){body{padding:12px}.card{padding:14px;overflow:auto}table{min-width:700px}}
</style></head>
<body><main>
<h1>ToT Tools <span style="color:#7df0a8">Licence Admin</span></h1>
<p>Generate paid keys, inspect device bindings, revoke keys or reset a device binding.</p>
<section class="card" id="login">
<h2>ADMIN ACCESS</h2><div class="row"><input id="token" type="password" autocomplete="off" placeholder="Admin API token"><button id="connect">Connect</button></div><div id="loginError"></div>
</section>
<div id="app" class="hidden">
<section class="card">
<h2>GENERATE LICENCE KEYS</h2>
<div class="row">
<label>Quantity<br><input id="count" type="number" min="1" max="100" value="1"></label>
<label>Expires in days<br><input id="expiry" type="number" min="1" max="3650" placeholder="Never expires"></label>
<label>Order / note<br><input id="note" maxlength="120" placeholder="Optional order reference"></label>
<button id="generate">Generate keys</button>
</div><p>Keys are shown only once. Copy and send them to the customer after payment.</p>
<div id="generated"></div>
</section>
<section class="card">
<div class="row" style="justify-content:space-between"><h2>LICENCES</h2><button class="secondary" id="refresh">Refresh</button></div>
<div style="overflow:auto"><table><thead><tr><th>Key</th><th>Status</th><th>Device</th><th>Expiry</th><th>Created</th><th>Actions</th></tr></thead><tbody id="rows"></tbody></table></div>
</section>
</div><div id="notice"></div>
</main>
<script>
let authToken=sessionStorage.getItem("totAdminToken")||"";
const $=id=>document.getElementById(id);
async function api(path,method="GET",body){
 const response=await fetch(path,{method,headers:{"Authorization":"Bearer "+authToken,"Content-Type":"application/json"},body:body?JSON.stringify(body):undefined});
 const data=await response.json().catch(()=>({message:"Invalid server response"}));
 if(!response.ok)throw new Error(data.message||"Request failed ("+response.status+")");
 return data;
}
function showNotice(message){$("notice").textContent=message||""}
async function connect(){
 authToken=$("token").value.trim();
 try{await refresh();sessionStorage.setItem("totAdminToken",authToken);$("login").classList.add("hidden");$("app").classList.remove("hidden");showNotice("")}
 catch(e){$("loginError").textContent=e.message;authToken=""}
}
async function refresh(){
 const data=await api("/admin/api/licenses");$("rows").innerHTML="";
 for(const item of data.licenses){
  const tr=document.createElement("tr");
  const values=[item.key_hint,item.status,item.device_id?"Bound":"Unbound",item.expires_at?new Date(item.expires_at).toLocaleString():"Never",new Date(item.created_at).toLocaleString()];
  for(const value of values){const td=document.createElement("td");td.textContent=value;tr.appendChild(td)}
  const actions=document.createElement("td"),wrap=document.createElement("div");wrap.className="actions";
  if(item.status==="active"&&item.device_id){const b=document.createElement("button");b.className="secondary";b.textContent="Unbind";b.onclick=()=>act(item.id,"unbind");wrap.appendChild(b)}
  if(item.status==="active"){const b=document.createElement("button");b.className="danger";b.textContent="Revoke";b.onclick=()=>{if(confirm("Revoke this key permanently?"))act(item.id,"revoke")};wrap.appendChild(b)}
  actions.appendChild(wrap);tr.appendChild(actions);$("rows").appendChild(tr);
 }
}
async function act(id,action){try{await api("/admin/api/licenses/"+id+"/"+action,"POST",{});showNotice("Licence updated.");await refresh()}catch(e){showNotice(e.message)}}
$("connect").onclick=connect;$("refresh").onclick=()=>refresh().catch(e=>showNotice(e.message));
$("generate").onclick=async()=>{
 try{
  const expiry=$("expiry").value;
  const result=await api("/admin/api/licenses","POST",{count:Number($("count").value),expiresInDays:expiry?Number(expiry):null,note:$("note").value});
  $("generated").innerHTML="";
  for(const item of result.created){const div=document.createElement("div");div.className="keyout";div.textContent=item.key;$("generated").appendChild(div)}
  showNotice(result.created.length+" key(s) generated. Copy them now; the full keys will not be shown again.");
  await refresh();
 }catch(e){showNotice(e.message)}
};
if(authToken){$("token").value=authToken;connect()}
</script></body></html>`;

export default {
  async fetch(request: Request, env: Env): Promise<Response> {
    const url = new URL(request.url);
    if (request.method === "OPTIONS") return json({ ok: true });
    if (url.pathname === "/health" && request.method === "GET") return json({ ok: true, service: "tot-tools-license-api" });
    if (url.pathname === "/api/v1/activate" && request.method === "POST") return activate(request, env);
    if (url.pathname === "/admin" && request.method === "GET") return html(ADMIN_HTML);
    if (url.pathname.startsWith("/admin/api/")) return adminApi(request, env, url);
    return json({ ok: false, message: "Not found." }, 404);
  },
};
