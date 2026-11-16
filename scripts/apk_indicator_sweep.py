#!/usr/bin/env python3
r"""apk_indicator_sweep.py — read-only indicator sweep of TwnTool.apk.

Encapsulates the DEX string-pool + smali + asset sweep that produced
E-15..E-19 in docs/TOWNTOOL-APK-ANALYSIS.md (third investigation,
2026-11-16). Reads ONE copy of the APK with zipfile, writes NOTHING,
contacts NO network. Substitutes for the jadx full decompile, which
stalled (0-byte output 40+ min) on this 7.8 MB single-DEX APK.

Usage (from C:\Township-Mod):
    python scripts/apk_indicator_sweep.py [path-to-TwnTool.apk-copy]
                                         [apktool-smali-root]
Default input: analysis/incoming/apk/TwnTool.apk
Default smali root: analysis/out/apk/apktool/smali

Prints, per section:
  [dex]     pool run census + byte-hits for every documented
            indicator family (hwid* whole-DEX byte-hits = doc method,
            URL census, TWNSaves/.twntool staging, Tink refs)
  [dex-abs] VERIFIED-ABSENT families (count must be 0: cert pinning,
            Firebase/analytics/telemetry)
  [smali]   apktool-tree Tink census: .smali file count under
            com/google/crypto/tink/ (reproduces the doc's 871)
  [assets]  per-asset SHA-256 + size; indexg keyword counts
            (regata case-insensitive = doc method; eel.* bridge-call
            census = paren-followed names, excludes the /eel.js
            script-tag match); index.bin head-16 +
            zlib/bzip2/known-magic probes (UNKNOWN container, E-19);
            eel_shim.js marker hits
Exit code 0 on success.
"""
import hashlib
import re
import sys
import zipfile

DEFAULT_APK = r"analysis\incoming\apk\TwnTool.apk"

# Documented DEX key names (TOWNTOOL-APK-ANALYSIS.md §A.9b / E-16).
# Doc method = whole-DEX byte-hits via dex.count() -> 12/3/2/1/1/1/1/1/1.
HWID_KEYS = ["hwid", "hwid_v1", "hwid_prev", "hwid_mismatch",
             "hwid_missing", "hwid_override", "hwid_prev_v1",
             "hwid_v1_sum", "get_hwid"]
HWID_DOC = {"hwid": 12, "hwid_v1": 3, "hwid_prev": 2}  # others all 1
# Documented 12-URL census (E-15). bouncycastle.org / cloud.google.com
# KMS URLs are library-doc provenance strings (Tink), not call sites.
DEX_URLS = [
    "https://179.198.115.135",
    "https://twntool.com/PHP/fresh/",
    "/auth/login",
    "/auth/refresh",
    "/auth/validate",
    "/update/version.json",
    "/PHP/auth_challenge.php",
    "/PHP/tool_image.php",
    "/PHP/tool_saves.php",
    "https://twntool.local/",
    "https://www.bouncycastle.org",
    "https://cloud.google.com/kms/docs/reference/rest/v1/projects.locations.keyRings.cryptoKeys",
]
STAGING = [b"TWNSaves", b".twntool", b"/data/local/tmp/", b"/data/media/0/"]
# Tink probes = raw DEX byte-hits of the bare tokens (doc method).
# NOTE: the prefixed form (b"tink/AesGcm") is a different quantity
# (= 0 hits; the descriptor Lcom/google/crypto/tink/AesGcm; is not
# in the string pool; the 61 AesGcm hits are the standalone key-type
# name strings Tink's Catalogue uses). Expected values below come
# from the documented E-15 census.
TINK_PROBES = {b"com/google/crypto/tink/": 943,   # DEX-level prefix
               b"AesGcm": 61,                        # bare token (doc)
               b"X25519": 61,                        # bare token (doc)
               b"XChaCha20Poly1305": 15,
               b"KeysetManager": 5}
# Must be VERIFIED ABSENT at string level (E-15):
ABSENT_PIN = [b"CertificatePinner", b"addCertificatePinner", b"HostsPinner"]
ABSENT_TELEMETRY = [b"com/google/firebase", b"AppMeasurement", b"gtag",
                    b"amplitude", b"sentry", b"bugsnag"]

# indexg (assets) documented counts (E-17/E-18) to cross-check.
# "regata" is the case-INSENSITIVE total (188 lowercase + 45 "Regata"
# = 233 in the doc); the rest are case-sensitive whole-asset byte-hits.
INDEXG_COUNTS = {"i.ibb.co": 1444, "sua-vps.com": 1,
                 "fonts.googleapis.com": 1}
INDEXG_CI = {"regata": 233}  # case-insensitive total
# Doc "99 eel.* calls" = distinct bridge names that are actually CALLS
# (name immediately followed by '('). The raw `eel\.[A-Za-z0-9_]+` also
# matches `eel.js` from `<script src="/eel.js">` — not a call — which is
# why a naive distinct-name count gives 100. We count paren-followed
# names to reproduce the documented 99.
EEL_CALL_NAME_RE = re.compile(rb"eel\.([a-zA-Z0-9_]+)\s*\(")
LOCALSTORAGE_KEY = b"twn_saved_data_v1"  # unsent-drafts key ONLY (E-16)

ZLIB_MAGICS = [b"\x1f\x8b\x08", b"\x78\x01", b"\x78\x9c", b"\x78\xda"]
KNOWN_MAGICS = [b"PK\x03\x04", b"\x7fELF", b"SQLite format", b"BZh9",
                b"\x28\xb5\x2f\xfd"]  # zip/elf/sqlite/bzip2/zstd


def printable_runs(buf: bytes, min_len: int = 6):
    """Maximal ASCII-printable runs >= min_len, as byte strings."""
    out = []
    i, n = 0, len(buf)
    while i < n:
        if 32 <= buf[i] < 127:
            j = i
            while j < n and 32 <= buf[j] < 127:
                j += 1
            if j - i >= min_len:
                out.append(buf[i:j])
            i = j
        else:
            i += 1
    return out


def main():
    import io as _io
    # Rebind stdout UTF-8 (Windows console cp1252 would choke on "—")
    sys.stdout = _io.TextIOWrapper(sys.stdout.buffer, encoding="utf-8",
                                   errors="replace")
    apk = sys.argv[1] if len(sys.argv) > 1 else DEFAULT_APK
    zf = zipfile.ZipFile(apk)

    # ---- classes.dex ---------------------------------------------------
    dex = zf.read("classes.dex")
    print(f"[dex] classes.dex size={len(dex)} "
          f"sha256={hashlib.sha256(dex).hexdigest()}")
    runs = printable_runs(dex, min_len=6)
    print(f"[dex] printable runs >=6 ASCII chars: {len(runs)}")
    for key in HWID_KEYS:
        kb = key.encode()
        c = dex.count(kb)                     # whole-DEX byte-hits (doc method)
        exact = sum(1 for r in runs if r == kb)  # pool-run exact equality (alt)
        want = HWID_DOC.get(key, 1)
        flag = "" if c == want else f"   (doc {want} — MISMATCH)"
        print(f"[dex]   {key}: {c} byte-hits "
              f"(pool-run exact: {exact}){flag}")
    for u in DEX_URLS:
        ub = u.encode()
        print(f"[dex]   {u}: {dex.count(ub)} byte-hits")
    for p in STAGING:
        print(f"[dex]   {p.decode()}: {dex.count(p)} byte-hits")
    for t, want in TINK_PROBES.items():
        c = dex.count(t)
        flag = "" if c == want else f"   (doc {want} — MISMATCH)"
        print(f"[dex]   TINK {t.decode()}: {c} byte-hits{flag}")

    # ---- smali-tree census (apktool output; optional) -----------------
    # The doc's "871 com/google/crypto/tink class refs" reproduces as the
    # count of .smali files under com/google/crypto/tink/ in the apktool
    # tree. Run this section by passing a smali root as argv[2]:
    #     python scripts/apk_indicator_sweep.py [apk] [smali-root]
    smali_root = sys.argv[2] if len(sys.argv) > 2 else None
    if smali_root is None:
        smali_root = r"analysis\out\apk\apktool\smali"
    import os
    tink_root = os.path.join(smali_root, "com", "google", "crypto", "tink")
    if os.path.isdir(tink_root):
        n_files = 0
        fam = {}
        for root, _, files in os.walk(tink_root):
            for f in files:
                if not f.endswith(".smali"):
                    continue
                n_files += 1
                base = f[:-6]  # strip .smali
                for tok in ("AesGcm", "X25519", "XChaCha20Poly1305"):
                    if tok in base:
                        fam[tok] = fam.get(tok, 0) + 1
        print(f"[smali] Tink .smali files under com/google/crypto/tink/: "
              f"{n_files}   (doc: 871)")
        for tok in ("AesGcm", "X25519", "XChaCha20Poly1305"):
            print(f"[smali]   Tink .smali files named *{tok}*: "
                  f"{fam.get(tok, 0)}")
        print("[smali]   NOTE: the doc's '×61 AesGcm / ×61 X25519' "
              "figures are DEX byte-hits of the bare tokens "
              "(dex.count(b'AesGcm')=61, dex.count(b'X25519')=61, "
              "shown in the [dex] section above); the smali "
              "file-name counts here are a different quantity "
              "— .smali files under the Tink subtree whose "
              "filename contains the token (49 / 1 / 12 for "
              "AesGcm / X25519 / XChaCha20Poly1305). Both are "
              "recorded for reference.")
    else:
        print(f"[smali] Tink smali subtree not found at "
              f"{tink_root} — smali census skipped "
              "(run apktool, or pass a smali root as argv[2])")
    ok = True
    for p in ABSENT_PIN + ABSENT_TELEMETRY:
        c = dex.count(p)
        ok &= c == 0
        print(f"[dex-abs] {p.decode()}: {c} "
              f"({'VERIFIED ABSENT' if c == 0 else 'PRESENT -- re-audit!'})")
    print("[dex]   pinning/telemetry families all zero: "
          f"{'yes' if ok else 'NO — investigate'}")

    # ---- assets: indexg -------------------------------------------------
    ig = zf.read("assets/indexg")
    print(f"[assets] indexg size={len(ig)} "
          f"sha256={hashlib.sha256(ig).hexdigest()}")
    eel_calls = sorted(set(EEL_CALL_NAME_RE.findall(ig)))
    eel_all = sorted(set(re.findall(rb"eel\.([a-zA-Z0-9_]+)", ig)))
    extra = sorted(set(eel_all) - set(eel_calls))
    print(f"[assets] indexg distinct eel.* bridge-call names "
          f"(paren-followed): {len(eel_calls)}  (doc: 99)")
    if extra:
        print(f"[assets]   excluded from call census (no call parens): "
              f"{[e.decode() for e in extra]} "
              "(e.g. the /eel.js script-tag match)")
    for kw, doc in INDEXG_COUNTS.items():
        n = ig.count(kw.encode())
        flag = "" if n == doc else f"  (doc says {doc} — MISMATCH)"
        print(f"[assets]   {kw}: {n}{flag}")
    for kw, doc in INDEXG_CI.items():
        n = ig.lower().count(kw.encode())
        flag = "" if n == doc else f"  (doc says {doc} — MISMATCH)"
        print(f"[assets]   {kw} (case-insensitive): {n}{flag}")
    print(f"[assets]   {LOCALSTORAGE_KEY.decode()}: "
          f"{ig.count(LOCALSTORAGE_KEY)} byte-hits "
          "(unsent-drafts key only)")
    for vendor in (b"t.me/", b"wa.me/"):
        m = re.search(vendor + rb"[+\d]+", ig)
        if m:
            s = m.group().decode()  # bytes -> str for clean display
            print(f"[assets]   vendor contact REDACTED: {s[:10]}… "
                  f"(personal identifier, not reproduced)")

    # ---- assets: index.bin --------------------------------------------
    ib = zf.read("assets/index.bin")
    print(f"[assets] index.bin size={len(ib)} "
          f"sha256={hashlib.sha256(ib).hexdigest()} "
          f"head16={ib[:16].hex()}")
    probes = []
    import zlib as _z
    for wbits in (15, -15, 47, 31):
        try:
            _z.decompress(ib[:4096], wbits)
            probes.append(f"zlib wbits={wbits} OK")
        except Exception:
            pass
    for m in ZLIB_MAGICS + KNOWN_MAGICS:
        if ib[:len(m)] == m:
            probes.append(f"magic {m.hex()} at 0")
    if ib[:4] == b"BZh9":
        probes.append("bzip2 magic at 0")
    print(f"[assets] index.bin container probes: "
          f"{probes if probes else 'NONE matched — UNKNOWN container (E-19)'}")
    for pat in (b"sua-vps", b"twntool", b"hwid"):
        c = ib.count(pat)
        if c:
            print(f"[assets]   index.bin byte-hit {pat.decode()!r}: {c}")

    # ---- assets: eel_shim.js -------------------------------------------
    sh = zf.read("assets/eel_shim.js")
    print(f"[assets] eel_shim.js size={len(sh)} "
          f"sha256={hashlib.sha256(sh).hexdigest()}")
    for kw in (b"_androidCallback", b"_coopNormalize", b"timeout",
               b"VPhoneGaGa"):
        print(f"[assets]   eel_shim.js {kw.decode()!r}: {sh.count(kw)} hits")

    print("[sweep] done (read-only, no network, no writes)")
    return 0


if __name__ == "__main__":
    sys.exit(main())
