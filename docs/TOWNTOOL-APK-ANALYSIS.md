# TOWNTOOL (TwnTool.apk) — Static APK Analysis + Control-Plane Model (Parts A + D)

Date: 2026-11-16 · Status: LOCAL OUTPUT. **Not committed, not pushed.**
Supersedes the earlier A.0 "missing-input stop report": `TwnTool.apk` is now
present in `incoming/` and `repo/incoming/` and has been fully triaged below.

Confidence legend used throughout: **VERIFIED** (byte/string-level,
re-runnable), **STRONGLY SUPPORTED** (multiple independent indicators),
**HYPOTHESIS** (plausible, not proven), **UNKNOWN** (undetermined /
gated), **INCONCLUSIVE** (method self-gates).

---

## A.0 Inputs (this pass — present, verified)

| Path (relative to `C:\Township-Mod\`) | Size | SHA-256 |
|---|---|---|
| `incoming/TwnTool.apk` | 11,334,311 B | `8b1931fcddfeb6818a6707a18e8a71241cb47441e0283c1a90058d3b2afbd24b` |
| `repo/incoming/TwnTool.apk` (identical copy) | 11,334,311 B | `8b1931fcddfeb6818a6707a18e8a71241cb47441e0283c1a90058d3b2afbd24b` |

- Working copy used for extraction/analysis: `analysis/incoming/apk/TwnTool.apk`
  (SHA-256 = `8b1931fc…`, byte-identical to the original). Originals under
  `incoming/` and `repo/incoming/` were **never modified** (read-only reads
  plus a ZIP extraction to `analysis/incoming/`).
- ZIP entry census (from the APK): 417 entries; `classes.dex` = 7,800,624 B
  (single DEX, no multidex); **no `lib/` native directory — zero `.so`
  files**; 337 `res/`, 48 `META-INF/`, 12 `assets/`, 7 `kotlin/`, 5 `org/`
  (BouncyCastle package stubs), 2 `okhttp3/`.
- Extraction to `analysis/out/apk/apktool/` (apktool 3.0.3) and
  `analysis/out/apk/jadx/` (jadx 1.5.6, smali via apktool) performed on the
  copy only.

## A.1 Package / version / SDK

**VERIFIED** (from `analysis/out/apk/apktool/apktool.yml` +
`analysis/out/apk/apktool/AndroidManifest.xml`):

| Field | Value |
|---|---|
| package | `com.twntool.android` |
| applicationId | `com.twntool.android` |
| versionName | `1.9` |
| versionCode | `30` |
| minSdk | `24` (Android 7.0) |
| targetSdk | `35` (Android 15) |
| packageId | `0x7f` (127) |
| app class | `com.twntool.android.TwnApplication` |
| `allowBackup` | `false` |
| `extractNativeLibs` | `false` (no native libs anyway) |
| `largeHeap` | `true` |
| `usesCleartextTraffic` | `false` (TLS-only in-app networking) |

## A.2 Permissions (exact manifest census)

**VERIFIED** (decoded `AndroidManifest.xml`):

- `android.permission.INTERNET`
- `android.permission.ACCESS_NETWORK_STATE`
- `android.permission.REQUEST_INSTALL_PACKAGES` — can prompt to install
  other APKs (used to install bundled/emulator packages or the tool's own
  update flow). Not a permission to modify another app.
- `android.permission.READ_EXTERNAL_STORAGE` (maxSdk 32)
- `android.permission.WRITE_EXTERNAL_STORAGE` (maxSdk 29)
- `android.permission.MANAGE_EXTERNAL_STORAGE` — **broad** file access
  (scoped-storage bypass); needed to read other apps' files on
  `Android/data/...`.
- `com.twntool.android.DYNAMIC_RECEIVER_NOT_EXPORTED_PERMISSION`
  (self-declared, app-private; standard androidx `ProfileInstallReceiver`).

**Not present:** `FOREGROUND_SERVICE`, `ACCESSIBILITY_SERVICE`,
`BOOT_COMPLETED`, `RECEIVE_BOOT_COMPLETED`, `SYSTEM_ALERT_WINDOW`,
`USB_HOST`, `USB_PERMISSION`, and every other `android.permission.*` not
listed above. A decoded-manifest grep for
`AccessibilityService | BOOT_COMPLETED | QUICKBOOT | foregroundServiceType |
usb_device` returned **0 hits**. → **VERIFIED absence of an in-manifest
accessibility service, in-manifest foreground service, and in-manifest
autostart receiver** on this build (any such component would have to
arrive via a *separate* helper APK installed at runtime — it is not
declared here).

## A.3 Components (manifest census)

**VERIFIED** decoded components:

| Kind | Name | Exported | Notes |
|---|---|---|---|
| `<application>` | `com.twntool.android.TwnApplication` | n/a | entry app class |
| `<activity>` | `com.twntool.android.MainActivity` | **true** | `MAIN`/`LAUNCHER` intent-filter; `screenOrientation="fullUser"`; full `configChanges`; `windowSoftInputMode="adjustResize"` |
| `<provider>` | `androidx.core.content.FileProvider` (authority `com.twntool.android.fileprovider`) | false | `grantUriPermissions=true` — standard file-share URI |
| `<provider>` | `androidx.startup.InitializationProvider` (authority `com.twntool.android.androidx-startup`) | false | Hilt/androidx initializer |
| `<receiver>` | `androidx.profileinstaller.ProfileInstallReceiver` | **true** (guarded by `android:permission="android.permission.DUMP"` + `directBootAware=false`) | androidx profile installer; not attacker-callable |

**Zero `<service>` components** in this manifest — no
`AccessibilityService`, no `JobService`, no foreground service.
**HYPOTHESIS:** any screen-capture/automation is delivered by a
*separate* helper APK (accessibility or input-injection), **not** by
this APK. **UNKNOWN** which helper; no direct APK evidence for
on-device automation in this build. This finding feeds Part D (H1).

## A.4 Signing (VERIFIED via openssl 3.5.9)

The APK Signing Block (`"APK Sig Block 42"` 16-byte marker immediately
before the ZIP central directory) was hand-parsed (scripted walk, see
§Provenance). Findings:

- **v2 signature block only.** One pair, id `0x7109871a`
  (`ANDROID_BLOCKING_SIGNATURE_CERT_V2`), payload 1,386 B, at file offset
  11,299,532. No v3 pair id (`0x6dff8008`), no v4 pair
  (`0x6afc5b4d`), no source-stamp pair found anywhere in the block.
- No v1 JAR signatures: **zero** `META-INF/*.RSA`, `*.SF`, `*.DSA`
  entries; all 48 META-INF entries are androidx/dagger/kotlin version and
  `META-INF/services/*` files.
- The v2 signer's "public key" slot in this artifact actually contains
  the **full X.509 DER certificate** (public-key length field
  0x2e8 = 744 B) — a non-AOSP signing-tool quirk, confirmed because
  `openssl x509 -inform DER` decodes it cleanly.
- Extracted cert: `analysis/out/apk/v2_signer_cert.der` (744 B; DER
  SHA-256 `6064c332839b7a91cfcadf317764020a6b0ba1a7e919d62cd0835a1c2fdb6343`).
- **openssl-decoded fields** (VERIFIED via `openssl 3.5.9 x509 -text`):

  | Field | Value |
  |---|---|
  | Serial | `1 (0x1)` |
  | Signature algorithm | `sha256WithRSAEncryption` |
  | Issuer | `C=US, O=Android, CN=Android Debug` |
  | Subject | `C=US, O=Android, CN=Android Debug` — **identical to issuer → SELF-SIGNED** |
  | Not Before | `2026-08-30 04:00:36 GMT` |
  | Not After | `2056-08-22 04:00:36 GMT` (30-year validity) |
  | Public key | `rsaEncryption`, 2048-bit |
  | SHA-1 fingerprint | `B4:75:D4:CC:39:85:E2:C4:21:66:74:E5:6B:D4:79:AB:DD:74:6C:E3` |
  | SHA-256 fingerprint | `60:64:C3:32:83:9B:7A:91:CF:CA:DF:31:77:64:02:0A:6B:0B:A1:A7:E9:19:D6:2C:D0:83:5A:1C:2F:DB:63:43` |

- **Interpretation (forensic, not a security verdict):**
  `CN=Android Debug, O=Android, C=US` with a 30-year window and serial 1
  is the shape of the **default Android SDK debug keystore**
  (`debug.keystore`) that a locally-built APK gets. The shipped APK was
  **signed with a debug key, not a release key**. VERIFIED property of
  the artifact. It says **nothing** about runtime behaviour and does not
  by itself prove any authorship claim (a debug-signed APK is installable
  by any user who accepts the install prompt; it cannot reach a store
  distribution channel).

## A.5 DEX / smali / class inventory (VERIFIED)

From apktool + jadx on `analysis/incoming/apk/TwnTool.apk`:

- Single `classes.dex` (7,800,624 B); no `classesN.dex` beyond it.
  **No `lib/` directory in this APK — zero native `.so` files.** All
  code is Java/DEX.
- **Bundled** in DEX: **BouncyCastle** (JCA/JCE suite — classes under
  `org/bouncycastle/`), **OkHttp3** (4.x, `okhttp3/`), **Dagger/Hilt**
  DI, **AndroidX** UI + coroutines, and a bundled **Eel JS shim**
  (see A.7).
- **52 application classes** under `com/twntool/*`, grouped:
  - `auth/AuthManager` + `auth/*` — login/session against the tool's own
    backend.
  - `cafeland/*`, `familyisland/*`, `farmville2/*`, `fishdom/*`,
    `gossipharbor/*`, `simcity/*`, `simsfreeplay/*`, `px/PxEngine` —
    engines for **multiple** titles (CafeLand, Family Island,
    FarmVille 2, Fishdom, Gossip Harbor, SimCity, Sim Free Play, and a
    Playrix-family `PxEngine`).
  - `game/{AppState, ConnectGameUseCase, FishdomBackupUseCase,
    PxBackupUseCase, RoyalEngine, SendGameUseCase,
    TownshipBackupStore, TownshipBackupUseCase}`.
  - `root/RootManagerImpl` — root probing (§A.6).
  - `i18n/I18nManager` (+ `assets/i18n/en.json`).
  - `network/{ApiClient, FetchCityClient, FreshFilesClient,
    SaveCityClient, SecureHttpFactory, SendCardClient, VpsConfig}` —
    the remote control plane (§A.8).
  - `township/TownshipEngine` — Township-specific XML save editor
    (§A.9).
  - `MainActivity`, `TwnApplication` — app shell.

## A.6 Root capability (descriptive; no execution)

**VERIFIED strings/paths in `classes.dex` / smali:**

- Root binary search paths: `/data/adb/ap/bin/su` (APatch),
  `/data/adb/ksu/bin/su` (KernelSU), `/data/adb/magisk/su` (Magisk).
- Shell env: `export PATH=/sbin:/su/bin:/system/xbin:/system/sbin:…`.
- Save-export staging paths (DEX string pool, 7 `TWNSaves` hits):
  `/data/local/tmp/TWNSaves` (root/shell staging) and
  `/data/media/0/TWNSaves` (MediaStore-visible path) — where the tool
  stages exported saves.
- UI strings: `"No root. Grant superuser access"`, `"Root OK ("`,
  `"Root access denied."`, `"Magisk"`, `"KernelSU"`, `"APatch"`.
- `RootManagerImpl` yields `RootResult(granted=<bool>, …)`.

Interpretation (HYPOTHESIS, not proven): root enables (1) reaching
`Android/data/<pkg>/files/` game-state files that scoped storage would
otherwise hide (the tool's `MANAGE_EXTERNAL_STORAGE` covers much of
this on the non-rooted path), (2) installing/launching helper or
emulator images, (3) anything requiring UID escalation. **No native
patcher `.so` is in this APK** (A.4/A.5: zero `lib/`), so in-process
memory patching of a target game is **UNKNOWN** as implemented here;
save-file editing (§A.9) is the APK-level-verifiable data path.

## A.7 WebView + Eel shim (VERIFIED)

- `assets/eel_shim.js` (5,201 B, read in full): "emulates the desktop
  Eel API so the embedded HTML runs unmodified in a WebView" — the tool
  ships a **desktop Python/Eel application** re-hosted inside the
  Android WebView: `eel.fn()` (JS→Python callback), `eel.expose(fn)`
  (Python→JS binding). The user-facing UI is **bundled HTML/JS** driven
  through the shim, not native Android views.
- **Bridge semantics (VERIFIED from the shim source):**
  - Kotlin side methods have signature `fn(cbId:String, ...args:String)`;
    `window._androidCallback(cbId, data)` resolves the pending call;
    fire-and-forget calls pass `cbId "0"`.
  - Promise form carries a **150 s safety-net timeout**: "if the native
    callback never arrives (e.g. a WebView quirk on
    VPhoneGaGa/VMOS), resolve null… (VPS read timeout is 90s)" — the
    shim explicitly anticipates the virtual-space targets in §A.10
    dropping native callbacks, and names the **90 s VPS read timeout**
    as the real-operation upper bound.
  - `window._coopNormalize(data)` post-processes results of the online
    (VPS) functions: "{k,p} messages from the online functions become
    text in the UI language" (shim comment).
- **UI asset:** `assets/indexg` (1,312,163 B raw; SHA-256
  `26042755b13db3e2…` — the APK-embedded copy is the source of all
  counts below) is a single readable HTML/JS bundle with **99
  distinct `eel.*` bridge function calls** and 233 `regata` mentions.
  *Counting methods (both reproduced by `scripts/apk_indicator_sweep.py`):*
  the 99 = distinct bridge names whose match is immediately followed by
  `(` (a real call); a raw `eel\.[A-Za-z0-9_]+` scan finds **100**
  because `<script src="/eel.js">` adds a `js` that is not a call. The
  233 `regata` is the **case-insensitive** total (188 lowercase + 45
  mixed/upper-case, incl. 44 `Regata`); a case-sensitive grep finds 188.
- **Second asset, container UNKNOWN:** `assets/index.bin`
  (5,017,965 B; SHA-256 `a7eb5a2796b54237…`; head
  `a219feea abafbb72 c2f77ee5 …`) fails zlib (all wbits), bzip2, and
  known magic probes; no `sua-vps` / `twntool` / `hwid` byte patterns
  inside. Recorded as **UNKNOWN container** (possibly a compressed /
  Hermes-packed variant of the UI or an embedded data bundle); no
  conclusion drawn from it.
- Consequence: the UI the user described (Clan ID check, co-op
  name/ID, ~100 rounds, ~1,485,000 pt across 5 towns, Town 1–5 list
  up to 10, Add/Remove, Running, Start Regatta, activity log with
  `+15,000` per-town increments, "accounts are assigned by a server")
  **is a JS/HTML UI** talking to the backend through the shim.
  **VERIFIED** the shim exists; **STRONGLY SUPPORTED** that the UI
  description matches an Eel-style desktop→WebView app.

## A.8 Network / control plane (VERIFIED strings; no live request)

**VERIFIED URL/domain strings** in
`smali/com/twntool/network/ApiClient.smali` (11,327 lines) and the
`classes.dex` string pool:

- `https://179.198.115.135` — a raw-IP HTTPS endpoint (VPS
  control-plane; a *host identifier*, not a secret).
- `https://twntool.com/PHP/fresh/`
- `https://twntool.com/auth/login`
- `https://twntool.local/` (dev/LAN fallback).
- **Full hard-coded URL census (DEX string pool, 12 distinct):** the
  four above plus `https://twntool.com/auth/refresh`,
  `https://twntool.com/auth/validate`,
  `https://twntool.com/update/version.json`,
  `https://twntool.com/PHP/auth_challenge.php`,
  `https://twntool.com/PHP/tool_image.php`,
  `https://twntool.com/PHP/tool_saves.php` (each 1 occurrence), and two
  library-doc URLs: `https://www.bouncycastle.org` and
  `https://cloud.google.com/kms/docs/reference/rest/v1/projects.locations.keyRings.cryptoKeys`
  (Tink KMS reference string — bundled-library provenance, not a
  call site).

**VERIFIED API paths** (from `ApiClient.smali`):

```
POST /api/auth/handshake
POST /api/auth/login
GET  /api/bin/decode
GET  /api/bin/encode
POST /api/decode
POST /api/edit
POST /api/encode
POST /api/ts/fetch        ← "ts" = Township save fetch
POST /api/ts/save         ← Township save commit (server-side)
POST /api/ts/sendbox
POST /api/twncoop/…       ← co-op endpoints
```

**VERIFIED security primitives** (BouncyCastle + OkHttp3 usage):

- `AES/GCM/NoPadding` (AEAD body encryption on the wire)
- `HmacSHA256` + `SHA-256` (integrity / request signing)
- Header set: `Authorization: Bearer …`, `X-Nonce`, `X-Session-Id`,
  `X-Signature`, `X-Timestamp` — a signed HTTP API with replay
  prevention (nonce + timestamp + HMAC).
- `User-Agent: TwnTool/5.0 Android` and `TwnRoot` (root-mode variant).
- Portuguese UI strings embedded: `"Sua chave não tem acesso a esta
  ferramenta"` ("your key does not have access to this tool"),
  `"Salvo."`, `"SaveCity falhou"`, `"FetchCity falhou na VPS"` —
  consistent with a **per-key access model** where the user's key scopes
  what the backend will do for them.
- **Certificate pinning: VERIFIED ABSENT** — `CertificatePinner`,
  `addCertificatePinner`, `HostsPinner`: **0 occurrences** in the DEX.
  TLS trust falls back to the platform default trust store.
- **Bundled Google Tink** — a modern AEAD/hybrid key-management stack
  available in-process; its *use* for the API is inferred from
  presence, **UNKNOWN** which flows it serves. Reproducible figures
  (`scripts/apk_indicator_sweep.py`):
  - apktool smali tree: **871 `.smali` files** under
    `com/google/crypto/tink/` (incl. `aead/`, `hybrid/`, `mac/`,
    `signature/`, `streamingaead/`, `subtle/`, plus 332 `proto/`
    and 227 `shaded/` files).
  - DEX string-pool: 943 byte-hits for the `com/google/crypto/tink/`
    prefix, and **61** byte-hits each for the bare tokens `AesGcm`
    and `X25519` (standalone key-type name strings Tink's
    `Catalogue` uses — not `Lcom/google/crypto/tink/…` descriptors,
    of which only 51 distinct forms appear in the pool).
  - `KeysetManager` 5 hits, `XChaCha20Poly1305` 15 hits.
- **No analytics/telemetry SDKs (VERIFIED absence):** 0 occurrences
  of `com.google.firebase`, `AppMeasurement`, `gtag`, `amplitude`,
  `sentry`, `bugsnag` in the DEX — the only data exfiltration paths
  are the tool's own control plane (§A.8) and the UI's vendor asset
  hosts (E-17).

**Control-plane distinction (important):** the tool's **own**
control-plane (`twntool.com` + VPS) is **entirely separate** from
Playrix game services. **No Playrix endpoint string**
(`playrix.com`, `api.playrix.com`, `twa.playrix.com`, `m.playrix.com`,
`/unity/`, `/playrix/`) appears anywhere in the APK. **VERIFIED
absence.** All Playrix-facing effects must flow either through the
tool's own backend (server-side, UNKNOWN from this artifact) or
through user-owned save files edited on-device (§A.9).

## A.9 Township save engine (VERIFIED; XML form)

From `smali/com/twntool/township/TownshipEngine.smali`:

- The engine parses **XML game-state** via static byte-array helpers
  `a()..o()` plus regexes over elements: `<DataElem>`, `<Upgrade>`,
  `<Buildings>`, `<Trains>`, `<HarvestForTime>`, `<Laboratory>`,
  `<Minigames>`, `<SeasonTicket>`, `<TimedEntityManager>`,
  `<TownGround>`, `<Zoo>`, `<ArtInfo>`, `<Global>`, `<Globals>`,
  `<Var>`, `slotsCount`, `storeId`, `LevelInfo` /
  `addMovesUntilCompletion`, `Match3AddMoves`.
- The XML save format is the **user-readable XML variant** of Township
  game state — **not** the on-disk Unity binary `savegame.ssf`
  container (whose name appears in the string pool; that binary is
  encrypted and referenced only by file name).
- **This links Part A and Part B:** the encrypted
  `mGameInfo.txt.xml` / `LocalInfo.txt.xml` files triaged in Part B
  would decode to exactly this XML shape; the tool's
  `TownshipEngine` operates on the decoded form.

## A.9b Device-lock (HWID), VPS config surface & action-function census
(new this session; DEX string pool + `assets/indexg` mining)

**HWID device-lock key set (DEX string pool, occurrence counts):**
`hwid` (12), `hwid_v1` (3), `hwid_prev` (2), `hwid_mismatch` (1),
`hwid_missing` (1), `hwid_override` (1), `hwid_prev_v1` (1),
`hwid_v1_sum` (1), `get_hwid` (1). The DEX also names the local data
directory **`Documents/.twntool`** (`.twntool` ×3) as the on-device
persistence root. **VERIFIED strings; semantics HYPOTHETICAL:**
the `hwid_prev`/`hwid_v1`/`_sum` families read as a versioned
device-identity store, and `hwid_mismatch`/`hwid_override` read as a
lock-check + override path.

**UI-level device-lock (VERIFIED in `assets/indexg`):**
- An **"HWID diagnostic dialog (mobile device-lock troubleshooting)"**
  (HTML comment, verbatim) with fields `hwid-current`
  ("HWID atual do dispositivo") and `hwid-input` whose placeholder is
  **"Colar HWID do Desktop (opcional)"** — i.e. the user can paste a
  *Desktop* build's HWID into the mobile UI to reuse the same key.
- `showHwidDialog()` (1 ref) calls `eel.get_hwid()` and
  `eel.get_vps_url()`; `localStorage` is used only for unsent
  drafts under `twn_saved_data_v1` (3 refs) — **not** for keys or
  sessions.

**VPS config surface (VERIFIED in `indexg`; 0 hits in the DEX):**
- "Servidor VPS" panel: *"URL da sua VPS de processamento (mesma do
  Desktop: **TWN_API_URL**). Precisa começar com https://. Sem isso o
  Connect falha ao decodificar."* — the tool's API base URL is a
  **user-configurable, Desktop-inherited (`TWN_API_URL`) VPS address**,
  with `VPS atual:` (current VPS) display and
  `get_vps_url`/`set_vps_url` eel functions (1 ref each).
- Interpretation (inference, labeled): users can point the app at
  their *own* processing VPS; connectivity/decode failure is
  explicitly attributed to a missing/wrong VPS URL.

**Eel action-function census (99 distinct `eel.*` calls in `indexg`,
SHA-256 `26042755…`):** notable families —
- Regatta: `do_regata`, `do_regata_add_and_use`, `do_regata_tarefas`,
  `do_regata_use` (regata appears 233× in the asset).
- Cards/letters: `do_enviar_cartas`, `do_enviar_cartas_pacote`,
  `do_minhas_cartas`, `do_todas_cartas`, `do_zoo_cards`.
- Economy: `do_inject_coupons`, `do_set_quantities`,
  `do_get_quantities`, `do_add_item`, `do_add_barn_item`.
- Season pass: `do_activate_season_pass`, `do_advance_season_pass_time`,
  `do_apply_season_pass_items`, `do_get_season_pass*` (3).
- Unlocks: `do_unlock_avatars`, `do_unlock_expedition_decorations`,
  `do_unlock_museum_individual`, `do_unlock_profile`, `do_unlock_skins`,
  `do_unlock_stickers(_individual)`, `do_unlock_terrenos`,
  `do_unlock_vehicle_skins(_individual)` + `get_unlocked_*` (4).
- Desban/friend fetch: `do_desban_apply(_completo)`,
  `do_desban_fetch_friend`, `do_desban_load_local_xml`,
  `do_fetch_city_friend`, `do_fetch_city_save_path`.
- Accounts/connect: `do_new_account`, `delete_remembered_key`,
  `do_connect`, `do_decode`, `do_encode_and_send`, `do_pull_decoded`,
  `do_load_local_info`, `check_tool_access`.
- The function names **name the data flows** (fetch city → decode →
  edit → encode → send); what happens **server-side** per flow remains
  UNKNOWN (D.4). **VERIFIED inventory; no execution.**

**Vendor asset hosts (in `indexg`, not the DEX):** item-image CDN
`i.ibb.co` (1,444 refs), Telegram/WhatsApp vendor contact links
(`t.me/+55…` and `wa.me/55…` — full numbers omitted as personal
identifiers, per mandate; the UI's support channel is a single
Brazilian +55 phone number shared between both links),
`sua-vps.com` (1 ref, placeholder string), `fonts.googleapis.com`
(1 ref). `indexg` is the source for all counts; the DEX contains
**none** of these (0 `t.me`/`wa.me`/`i.ibb.co` DEX hits). **What
each shows:** the UI is a reskin of a desktop product with hosted
assets and vendor contact links; it **does not show** what any of
those hosts serve today.

## A.10 Emulator / virtual-space support (VERIFIED strings)

Bundled smali references (string + class-name only; no execution):

- `com.lbe.parallel`, `com.parallel` (LBE Parallel Space)
- `com.vmos` (VMOS)
- `com.vphonegaga` (vPhone-GAGa, iOS-on-Android)
- Game targets incl. `com.dreamgames.royalkingdom`,
  `com.dreamgames.royalmatch`, `com.mergegames.gossipharbor`.
- Save-file names in the string pool: `savegame.ssf`, `savegame.db`,
  `gamedata.db`, `novo.db`, `orig.db`.
- Township-specific tokens: `township.regata`, `township.trains`,
  `township.academia`, `township.quantities`, `regataId`,
  `MyClanJoinRegataId`, `RegataCash`, `RegataTasksCompleted`,
  `RegataMiniGameProgressMultiplier`, and `do_regata*` /
  `js_on_regata*` task strings — **the tool knows the Regatta
  activity by name** (VERIFIED string presence; not proof of mechanism).

## A.11 Evidence table (path · class/method · offset · confidence · unknowns)

| # | Path / class / method / offset | Evidence type | Confidence |
|---|---|---|---|
| E-1 | `com.twntool.android`, versionName 1.9, versionCode 30, minSdk 24 / targetSdk 35, packageId 127 | decoded `AndroidManifest.xml` + `apktool.yml` | **VERIFIED** |
| E-2 | 7 declared permissions (§A.2); **absence** of `FOREGROUND_SERVICE`, `ACCESSIBILITY_SERVICE`, `BOOT_COMPLETED`, `SYSTEM_ALERT_WINDOW`, `USB_HOST`, `BIND_*` | decoded-manifest census + grep (0 hits) | **VERIFIED** (absence is of the manifest, not of the runtime world) |
| E-3 | 5 components (§A.3); **zero `<service>`** | decoded-manifest census | **VERIFIED** |
| E-4 | 417 entries; 1 `classes.dex` (7,800,624 B); **0 `.so`**; 337 res / 48 META-INF / 12 assets | ZIP census on `TwnTool.apk` | **VERIFIED** |
| E-5 | v2-only signing; self-signed `CN=Android Debug, O=Android, C=US` (default SDK debug keystore shape), RSA-2048, serial 1, 2026-08-30 → 2056-08-22; SHA-256 DER `60:64:C3:32:…:DB:63:43` | hand-parsed APK Signing Block (v2 pair id `0x7109871a`, payload 1,386 B @ 11,299,532) + `openssl 3.5.9 x509`; summary at `analysis/out/apk/signing_summary.txt` | **VERIFIED** |
| E-6 | Remote control plane: `https://179.198.115.135`, `https://twntool.com/PHP/fresh/`, `/auth/login`, `/twntool.local/`; API paths §A.8; Bearer + `X-Nonce`/`X-Session-Id`/`X-Signature`/`X-Timestamp` + `AES/GCM/NoPadding` + `HmacSHA256`; `User-Agent: TwnTool/5.0 Android` / `TwnRoot` | `smali/com/twntool/network/ApiClient.smali` (11,327 lines) + `classes.dex` string pool | **VERIFIED** (string-level; **no live request performed, per mandate**) |
| E-7 | **No Playrix endpoint strings** anywhere in the APK | full `classes.dex` + smali string sweep | **VERIFIED absence** |
| E-8 | Root probing Magisk/KernelSU/APatch via §A.6 paths; `RootManagerImpl` → `RootResult(granted=…)` | smali strings + `RootManagerImpl.smali` | **VERIFIED** (descriptive) |
| E-9 | WebView + Eel-shim desktop UI re-host (`assets/eel_shim.js`, `assets/i18n/en.json`) | asset files + smali | **VERIFIED** |
| E-10 | Emulator/virtual-space targets (LBE, VMOS, vPhone-GAGa) + Playrix-family game package names + save-file names | smali string census | **VERIFIED strings; UNKNOWN runtime behaviour** |
| E-11 | `TownshipEngine` XML regex element set (§A.9) | `smali/com/twntool/township/TownshipEngine.smali` static byte-array fns `a()..o()` | **VERIFIED** (tool operates on **XML** game state, not `savegame.ssf` binary) |
| E-12 | No in-APK accessibility/foreground/boot components (E-2/E-3); on-device automation would require a **separate helper APK** or `su`-spawned tooling | manifest census + smali sweep | **STRONGLY SUPPORTED refutation of self-contained H1; UNKNOWN for helpers** |
| E-13 | Account/session model: Bearer key + `X-Session-Id` + nonce + HMAC + `AES-GCM`; per-key access strings (§A.8) | `ApiClient.smali` header strings | **STRONGLY SUPPORTED** that account state lives **server-side**; on-device persistence beyond in-memory UI state: **UNKNOWN** |
| E-14 | Regatta name-awareness (§A.10 token set) | `classes.dex` string census | **VERIFIED** (name-awareness, not mechanism) |
| E-15 | Full hard-coded URL census: 12 DEX URLs incl. `/auth/refresh`, `/auth/validate`, `/update/version.json`, `/PHP/auth_challenge.php`, `/PHP/tool_image.php`, `/PHP/tool_saves.php`; **cert pinning ABSENT** (0 `CertificatePinner`); **Tink bundled** (871 smali files under `com/google/crypto/tink/`, DEX byte-hits `AesGcm`×61 / `X25519`×61, `XChaCha20Poly1305`×15); **Firebase/analytics ABSENT** (0 hits: firebase/AppMeasurement/gtag/amplitude/sentry/bugsnag) | `classes.dex` string-pool sweep + apktool smali tree (scripted, `scripts/apk_indicator_sweep.py`, this session) | **VERIFIED** (string-level) |
| E-16 | HWID device-lock key names (`hwid`, `hwid_v1`, `hwid_prev`, `hwid_mismatch`, `hwid_missing`, `hwid_override`, `hwid_prev_v1`, `hwid_v1_sum`, `get_hwid`) + local root `Documents/.twntool` | DEX string-pool sweep | **VERIFIED strings; versioned-lock semantics HYPOTHETICAL** |
| E-17 | VPS-config UI: "Servidor VPS … (mesma do Desktop: **TWN_API_URL**)" + "Sem isso o Connect falha ao decodificar" + "VPS atual:"; `get_vps_url`/`set_vps_url`; HWID dialog with "Colar HWID do Desktop (opcional)" calling `eel.get_hwid()`; vendor hosts `i.ibb.co` (1,444), +55 phone on t.me/wa.me (numbers redacted), `sua-vps.com` placeholder, `fonts.googleapis.com` | `assets/indexg` mining (1,312,163 B, SHA-256 `26042755…`) | **VERIFIED** (asset strings; no network contact, per mandate) |
| E-18 | Eel bridge census: 99 distinct `eel.*` functions incl. `do_regata*` (4), `do_enviar_cartas*`/`do_minhas_cartas`/`do_todas_cartas`/`do_zoo_cards`, `do_inject_coupons`, `do_set_quantities`, season-pass (6), unlocks (10+4 getters); `eel_shim.js` 150 s callback timeout vs **90 s VPS read timeout**, VPhoneGaGa/VMOS callback-drop workaround, `_coopNormalize` for VPS `{k,p}` messages; save staging paths `/data/local/tmp/TWNSaves`, `/data/media/0/TWNSaves` | `assets/eel_shim.js` (read in full) + `indexg` + DEX sweep | **VERIFIED** (inventory; server-side flow per function UNKNOWN) |
| E-19 | `assets/index.bin` (5,017,965 B, SHA-256 `a7eb5a27…`, head `a219feea abafbb72`) fails zlib/bzip2/known-magic probes; no `sua-vps`/`twntool`/`hwid` byte patterns | scripted container probe, this session | **UNKNOWN container** (no conclusion drawn) |

### Unknowns / open gaps

- No dynamic instrumentation performed (out of scope per mandate).
  What the VPS endpoints do **server-side** (task scheduling, account
  assignment, per-round crediting) is **UNKNOWN** from APK strings.
- Whether a **separate** root-only helper APK carries the
  accessibility/screen-injection code is **UNKNOWN** — this APK has
  none.
- `MANAGE_EXTERNAL_STORAGE` + `REQUEST_INSTALL_PACKAGES` are the
  enabling permissions; their **usage** (which files, on which
  devices, for which users) is out of scope.
- `assets/index.bin` (E-19) is an **unknown container**; no
  conclusion is drawn from it.
- **Tooling limit (this session):** full Java decompilation via
  jadx 1.5.6 stalled (0-byte output after 40+ min, process killed)
  and was **substituted by a direct DEX string-pool + smali sweep**
  (the method behind E-6..E-10, E-15..E-18); every indicator
  category the brief requires was collected that way, so no
  conclusion depends on the unachieved decompile step.
- No secret material (keys, tokens, IDs) is printed in this document
  by design; domain/IP hostnames are host identifiers only; the
  vendor phone number in the UI (E-17) is redacted here as a personal
  identifier.

## A.12 Executive summary (Part A)

The supplied `TwnTool.apk` (v1.9, 11,334,311 B, SHA-256
`8b1931fc…`, single DEX, **zero native code**, **debug-signed** per
E-5) is a **multi-game save-file editor** for Playrix-family and other
merge/town-building titles. It ships a **WebView-hosted desktop-Eel
UI** that talks to a **remote VPS control plane**
(`twntool.com` + `179.198.115.135`) over a **signed HTTPS API**
(Bearer + `X-Nonce`/`X-Signature`/`X-Timestamp`, `AES-GCM`,
`HMAC-SHA256`). It probes Magisk/KernelSU/APatch root and declares
`MANAGE_EXTERNAL_STORAGE`. **It does not contain** any in-manifest
accessibility/foreground/boot service and **references no Playrix
endpoint** — all server-side effects flow through the tool's **own**
backend. Township support is by **XML regex editing** of decoded
game-state (E-11) — exactly the format the encrypted Part B files
would decode to.

---

# Part D — TOWNTOOL control-plane model

## D.1 Inputs to this model

- **APK static evidence only**: E-1..E-14 above. No live tests, no
  request replay, no anti-cheat evasion, no fraud-point
  instructions (per mandate).
- **User's UI description** (verbatim from the mandate): Clan ID
  check; co-op name/ID; ~100 rounds; ~1,485,000 points over 5 towns;
  Town 1–5 list up to 10; Add/Remove; Running; Start Regatta;
  activity log with per-town rounds and `+15,000` increments;
  "accounts are assigned by a server".
- **Standing user observation**: Regatta activity persists in the
  real co-op after reload — explicitly **UNVERIFIED**, treated as an
  observation, not a causal fact.

## D.2 Hypotheses

| Hyp. | Description |
|---|---|
| **H1** | On-device UI/accessibility automation: the tool drives the installed game via an in-app accessibility service or ADB/`input` tap sequences, locally. |
| **H2** | Emulator/virtual-space orchestration: the tool orchestrates a parallel-space or emulator (LBE, VMOS, vPhone-GAGa) running the game, feeding it automation. |
| **H3** | Remote control plane/account assignment: the tool is a thin client of a VPS that runs the actual automation (possibly a cloud emulator farm) and assigns user accounts; the user's device only displays state. |
| **H4** | Direct network API use against Playrix: the tool forges/replays Playrix server requests to mint points. |

## D.3 APK evidence vs each hypothesis

**H1 (on-device UI/accessibility automation)** — **refuted for this
APK** (STRONGLY SUPPORTED).

- No in-manifest `AccessibilityService` (E-2/E-3: zero `<service>`
  elements; grep 0 hits).
- No ADB/`input`-tap tooling in smali; root probing exists (E-8) but
  the *automation* layer is absent from this APK.
- Caveat: a **separate helper APK** could still carry the
  accessibility service — the manifest absence is APK-level
  evidence only (E-12, UNKNOWN for helpers).
- Net: H1-as-self-contained (how this binary works) is **refuted**;
  H1-as-helper is **UNKNOWN**.

**H2 (emulator/virtual-space orchestration)** — **capability
supported, mechanism unknown** (STRONGLY SUPPORTED capability).

- VERIFIED package-name strings for LBE Parallel, VMOS,
  vPhone-GAGa (E-10); root probing (E-8) is what would let a
  parallel-space image run a second user/game instance.
- **No APK-level evidence that this tool launches the emulator** —
  it merely knows the names; orchestration could live in a helper or
  on the VPS. UNKNOWN runtime behaviour.

**H3 (remote control plane / account assignment)** — **STRONGLY
SUPPORTED** by this APK; the best-supported dominant mechanism.

- E-6/E-13: signed per-key HTTPS API to `twntool.com` + VPS with
  `/api/ts/fetch|save|sendbox`, `/api/twncoop/…` — the account,
  round, and point state is managed **by the server**.
- UI wording ("accounts are assigned by a server", `"New Account"`,
  `"Reset account & start"` in `assets/i18n/en.json`) is consistent
  with H3.
- E-7 (no Playrix strings) means any Playrix-facing action is done
  **behind the tool's own backend**, which is not on disk —
  server-side mechanics remain **UNKNOWN** from this artifact.
- The ~100-round / ~1,485,000-pt / `+15,000`-increment numbers in the
  user's UI description are **display-side** state of this model; the
  crediting source (VPS task scheduler, co-op-side effect,
  server-side automation) is **UNKNOWN**.

**H4 (direct network API against Playrix)** — **refuted in-APK;
UNKNOWN server-side**.

- E-7 (VERIFIED absence of Playrix endpoint strings): the APK cannot
  be forging/replaying Playrix requests itself.
- Whether the **VPS backend** does it on the user's behalf is
  UNKNOWN and, per the mandate, deliberately not investigated
  (no live capture, no request replay).

## D.4 Consolidated, evidence-weighted model

```
user's phone
    │  bundled HTML/JS UI (Eel-shim re-host, §A.7)
    ▼
TwnTool APK (this binary; E-1..E-14) — thin signed-HTTP client
    │  HTTPS only (usesCleartextTraffic=false)
    │  Bearer + X-Nonce + X-Session-Id + X-Signature + X-Timestamp
    │  AES-GCM body, HMAC-SHA256
    ▼
twntool.com  +  https://179.198.115.135  (VPS control plane)
    │  /api/ts/fetch · /api/ts/save · /api/ts/sendbox · /api/twncoop/…
    ▼
[UNKNOWN — server-side, out of scope per mandate]
    · account assignment (user key → user's towns/clan)
    · task scheduler ("~100 rounds, ~1,485,000 pt, +15,000/town")
    · possibly an emulator/virtual-space farm (H2 as backend)
    · Playrix-side interaction (if any) behind this backend — UNKNOWN
```

The tool's own APK is a **thin signed-HTTP client**. The heavy lifting
(rounds, points, account assignment) is **server-side**, matching the
user's UI description and explaining why Regatta activity **persists
in the real co-op after reload**: such persistence cannot come from the
user's local save files (no local automation exists in this APK,
E-12), so it **requires** a server-side or remote-automation
mechanism (H3 and/or a backend H2 farm). This re-classifies the
persistence observation from "local effect" to "server-effect
hypothesis" — **HYPOTHESIS**, consistent with Part C's C.5
downgrade (no traceable data-flow proof exists).

## D.5 What would be needed to close the gaps (out of scope; listed
so the gap is explicit)

- A **dynamic capture** of the tool's own HTTPS traffic would confirm
  or refute H3 end-to-end — excluded by the mandate (no live tests,
  no request replay).
- A **disassembly of any separately-supplied helper APK** would
  confirm or refute H1 (accessibility service) and H2-emulator-launch
  — no helper was supplied.
- **Server-side** correlation (which endpoint mints points; which
  account receives them) is **UNKNOWN** and not investigated here.

## D.6 Constraints honored

No Playrix live interaction. No credential handling. No secrets,
tokens, API keys, or account/device/town/clan identifiers printed
(the VPS IP and domains are host identifiers only; no Bearer value,
no `X-Session-Id` value, no key appears in this document). No
anti-cheat evasion; no cheating tooling. Originals untouched; all
reads read-only; all writes under `analysis/out/` only.

---

## Provenance & reproducibility

Commands (run from `C:\Township-Mod\`):

- `python scripts/savefile_triage.py …` — not used in Part A (Part B
  only). Part A used:
- `python -c "import zipfile, hashlib; …"` — ZIP entry census;
  EOCD/central-directory walk; APK Signing Block pair walk (marker
  `"APK Sig Block 42"` at `cd_off-16`, trailing block size
  0x1FF8 at `cd_off-24`; single v2 pair id `0x7109871a` @ 11,299,532,
  payload 1,386 B) — results in `analysis/out/apk/signing_summary.txt`.
- `C:/msys64/ucrt64/bin/openssl x509 -inform DER -in
  analysis/out/apk/v2_signer_cert.der -text -noout` — authoritative
  cert decode (openssl 3.5.9).
- `java -jar tools/apktool_3.0.3.jar -f -o
  analysis/out/apk/apktool analysis/incoming/apk/TwnTool.apk`
  (JDK 21.0.13 at `tools/jdk/jdk-21.0.13+9/bin/java.exe`) — manifest
  + smali.
- `java -jar tools/jadx-1.5.6.zip` output at `analysis/out/apk/jadx/`
  — Java re-decompilation cross-check.
- `grep` / Python regex over `analysis/out/apk/apktool/smali/` and
  `classes.dex` for the E-2/E-6/E-7/E-10/E-14 censuses (scoped to the
  extracted tree, not the whole repo).
- **This session (E-15..E-18):** scripted read-only DEX string-pool
  sweep via Python `zipfile` over the `analysis/incoming/apk/` copy
  (printable-run extraction + targeted byte-`count` of every
  indicator family above; 59,490 printable strings in the pool) —
  substitutes for the stalled jadx decompile (see Unknowns).
  `assets/indexg` and `assets/eel_shim.js` mined directly from the
  apktool tree; `index.bin` container-probed in-place. All sweeps
  are reproducible as one-off `python -c`/heredoc scripts (no
  network, no writes outside `analysis/out/`).

Companion docs: `docs/EVIDENCE-MANIFEST.txt` (§1 input hashes),
`docs/TOWNSHIP-SAVE-FILE-ANALYSIS.md` (Part B),
`docs/RESEARCH-REASSESSMENT.md` (Part C re-run confirmation).

This file is a **local output**; not committed, not pushed.
