# TOWNTOOL APK — static triage (Part A) + external-tool control-plane model (Part D)

Date: 2026-11-16 · Status: local research note. **Not committed, not pushed.**
Companion to `docs/MECHANISM-ANALYSIS.md` (first-brief notes) and
`docs/RESEARCH-REASSESSMENT.md` (Part C corrections).

Confidence legend used throughout:
- **VERIFIED** — read straight off a supplied artifact, reproducible by the cited command.
- **STRONGLY SUPPORTED** — multiple independent byte/string/offset signals, no single point of failure.
- **HYPOTHESIS** — inference from class mechanics or the described UI; not pinned to a byte.
- **UNKNOWN** — not resolvable from the materials on hand.

---

## Part A — TwnTool APK static triage

### A.0 Missing-input report (branch stopped at the gate)

The brief's Part A is a **static triage of a newly acquired TwnTool APK**. That
artifact was **not available at run time** and the branch is stopped at the input
gate rather than fabricated:

- `C:\Township-Mod\incoming\` — **exists but is EMPTY** (listing contains
  only `.` and `..`). No `TwnTool.apk`
  (or any `.apk`/`.apks`/`.apkm`) is present.
- No `mGameInfo.txt`, no `LocalInfo.txt.xml`, no ZIP bundle, no screenshot set.
  (These belong to Part B; see `docs/TOWNSHIP-SAVE-FILE-ANALYSIS.md`.)

Because the APK is absent, **no Part A table (permissions, DEX/native/assets
inventory, domains/URLs/WebSocket/MQTT/Firebase/Supabase/AWS/Cloudflare
backend indicators, analytics/crash SDKs, account/session storage) can be
populated with VERIFIED evidence.** Per the brief, this section stops here and
states exactly what is missing instead of inventing results.

**To unblock Part A, place in `incoming/`:**
1. `TwnTool.apk` (the tool's own Android package — a plain `.apk`, or a
   bundletool `.apks`/`.apkm` if that is what was acquired; note the form).
2. Any installer the tool ships alongside (Windows EXE, `*.dat` payload, etc.)
   if the "TwnTool" control surface is a desktop app that drives the phone.
3. A screen-capture set of the described UI (Clan ID / co-op / Rounds /
   estimate / account list / activity log) so the Part D model can be pinned to
   concrete widget IDs, not just prose.

When the APK arrives, the offline pipeline (no network, no Playrix
interaction) that Part A will run:

- **Manifest:** package name, `versionName`/`versionCode`, `minSdk`/`targetSdk`,
  `sharedUserId`; signing cert (SHA-256 digest + issuer).
- **Component surface:** exported activities/services/receivers/providers;
  `android:process` values; accessibility-service declarations;
  foreground-service + notification-permission posture; boot receivers;
  overlay / `SYSTEM_ALERT_WINDOW` request; `network_security_config`.
- **DEX/native/assets:** DEX class count + framework/language of interest
  (Xposed/Magisk/LSposed, Frida, XHook, virtual-space/`com.txy`/`VirtualXposed`,
  emulator-detection bypass), native libs inventory, asset blobs.
- **Backend indicators:** literal domains/URLs/API paths, WebSocket/MQTT,
  Firebase/Supabase/AWS/Cloudflare project or bucket IDs, with the **exact file
  + DEX/lib + offset** for each.
- **Account/session store:** where tokens/cookies/device-IDs/clan-IDs/town-IDs/
  round-queues/activity-logs are kept (SharedPreferences keys, SQLite tables,
  flat files) — **all secrets, tokens, keys, and personal account identifiers
  redacted** to `[REDACTED]` / `[token:…]` in the output.
- **Call-site evidence:** "a static endpoint string alone does not prove an
  endpoint is used." Each endpoint claim is paired with the call site (method
  + smali/native offset) that references it.

**Evidence table (to be filled when the APK is present) — claim / evidence /
confidence / unknowns.** No rows are asserted now; the structure is fixed:

| Claim | Evidence (file + class/method + offset, or command output) | Confidence | Unknowns |
|---|---|---|---|
| (rows populate on APK arrival) | — | — | — |

### A.1 Distinguish the tool's own backend from Playrix's backend

The brief's standing warning, applied up front: **the tool's control-plane
backend (if one exists) is a separate system from Playrix's backend.**
"Client-side" does **not** mean "offline"; a client app can and does talk to
remote servers. Any claim about *the tool's* infrastructure must not be
conflated with Playrix's, and vice versa. No row in the Part A table may
attribute a Playrix endpoint to the tool or a tool endpoint to Playrix without
offset-level evidence.

---

## Part D — Model the external tool from the described UI only

The tool's UI is described (screenshot set not yet on disk, so this is a
prose-level model; confidence capped accordingly):

- A **Clan ID field + "Check"** control (clan lookup).
- A **co-op (clan) name/ID** display.
- **Rounds ≈ 100** and an **estimate ≈ 1,485,000 pts over 5 towns**.
- An **account list "Town 1 … Town 5" (max 10)** with **Add / Remove**.
- A **status field ("Running")** and a **"Start Regatta"** control.
- An **activity log** with town tags, round counts, **"+15,000"** increments,
  and **cumulative totals**.
- UI copy that **"accounts are assigned by a server"** — this phrasing
  *suggests* a control-plane backend but **does not prove** how the in-game
  actions are actually generated.

### D.1 What the UI shows, and what it does not

The observable surface is a **multi-account Regatta farm dashboard**: an
operator enrolls up to 10 accounts (towns) into one clan, sets a target
(~100 rounds), estimates the achievable clan total (~1.485M ≈ 5 × ~297k), and
hits **Start Regatta** to begin a timed sweep; the log confirms per-town
rounds and +15,000-per-round increments accumulating to a running total. This
is the *control-plane UX*. It is **not**, by itself, evidence of the
*execution mechanism* (how each town's rounds actually get generated and
server-credited).

### D.2 Evidence-supported hypothesis set (compare, don't assert)

Per the brief, compare **only** evidence-supported families and do **not**
claim which is true without evidence. Four candidates:

| # | Hypothesis | Evidence that *supports* it | Evidence that *against / limits* it | Confidence |
|---|---|---|---|---|
| H1 | **Local UI / accessibility automation** of N real accounts on real hardware (rooted box / accessibility service / emulator) driving each town through the legit Regatta-earning flow. | "Rounds" + "+15,000" log increments look like per-flow completions; a local dashboard over N installs is a known tool-class pattern. | No tool binary in hand to confirm an accessibility/automation engine; "accounts are assigned by a server" points *away* from purely local assignment. | HYPOTHESIS |
| H2 | **Emulator / virtual-space orchestration** (VirtualXposed/`com.txy`-style multi-instance sandbox) hosting N Townships. | Multi-town (up to 10) on one control surface fits a virtual-instance farm; the apkvision mod (first brief) proves in-class client patching is feasible. | No APK to confirm a virtual-space runtime; 10 concurrent emulators is heavy — consistent but unproven. | HYPOTHESIS |
| H3 | **Remote control plane / backend** that holds sessions for N accounts and issues the reward calls server-side (protocol-level herding). | The literal UI copy "**accounts are assigned by a server**" is direct textual support for a remote assignment/coordination layer; "Rounds≈100" + "estimate" reads like a scheduler computing work. | The copy only says *assignment* is server-side, not that *actions* are; a backend could merely hand out accounts while the device executes. No endpoint/capture to confirm. | STRONGLY SUPPORTED (assignment); UNKNOWN (action generation) |
| H4 | **Network API use** — the tool (or the accounts it drives) talks to Playrix's live service under valid credentials to credit Regatta points. | Regatta points are server-credited and summed at the clan level; a persistent +15,000/round total implies the server accepted and recorded each round. | Requires valid session material; we have no capture confirming the tool itself issues the requests (as opposed to a real logged-in app doing so on-device). | HYPOTHESIS |

**Key discriminator the UI does not resolve:** H3's copy proves *assignment*
is server-mediated; it does **not** prove whether each town's rounds are
generated by a **real on-device app completing a flow** (H1/H2+H4) or by the
**tool's backend issuing requests directly** (H3+H4). Distinguishing these two
needs the APK (network client + token store) **or** a live capture — neither
is present. Per the brief, **no claim of which is true is made.**

### D.3 Persistence + cross-account properties (carried from first brief)

The first-brief `MECHANISM-ANALYSIS.md` §3 four-family read-off still applies
and is **the reason Part D cannot collapse to H1/H2 alone**: local memory /
PLT-hook classes (A/B) can make a *local* balance stop decreasing but **cannot**
(a) survive a server sync, or (b) credit *another* account's server state. The
observed behavior — points persist and land in a *shared clan* — is
consistent only with **C (local multi-account automation)** and **D (remote
control plane)**, i.e. the H1–H4 set above, and is **inconsistent with a
local-only mechanism**. This is a cross-account/clan property, not a
client-memory property.

### D.4 Standing constraint honored

"A client app can communicate with remote servers; 'client-side' does not
mean offline." No claim in this part treats the tool as offline. Conversely,
**no claim is made that persistent Regatta points are caused by local
save-file edits** — that would require the Part B save files (absent) plus a
proven server path, which do not exist here.

### D.5 What would settle D (offline, no Playrix live interaction)

- **APK in hand** → settle H1-vs-H3: presence of an accessibility/virtual-space
  engine (H1/H2) vs. a network client + remote assignment endpoint (H3).
- **Token/session store inspection** (redacted) → whether the tool holds N
  account sessions (supports H3/H4) vs. relies on N logged-in on-device apps
  (supports H1/H2).
- **No cheating code, no live-account interaction** — analysis is static
  and explanation-only, per the brief.

### D.6 Evidence summary (what exists now vs. what is asserted)

| Item | State |
|---|---|
| Tool's own backend exists (assignment) | **STRONGLY SUPPORTED** by the "accounts are assigned by a server" UI copy |
| Multi-town (≤10) single-operator farm | **STRONGLY SUPPORTED** by the account list + shared-clan display |
| Per-round +15,000, ~100 rounds, ~1.485M/5-town estimate | **VERIFIED as described** (UI prose; not yet pinned to widget bytes) |
| How each town's rounds are actually generated | **UNKNOWN** (needs APK/capture) |
| Tool backend vs. Playrix backend boundary | **UNKNOWN** (no endpoints on disk) |

---

## Provenance & reproducibility

- Inputs: none present for Part A/B (`incoming/` exists but is empty); Part D from brief's
  UI prose only. No file hashes are cited here because no tool artifact was
  read this run — see `docs/EVIDENCE-MANIFEST.txt` for the hash manifest of the
  first-brief artifacts this note builds on.
- Tools: static analysis offline; no network, no Playrix service, no account,
  no leaderboards, no cheating functionality.
- This file is a **local output**; not committed, not pushed.
