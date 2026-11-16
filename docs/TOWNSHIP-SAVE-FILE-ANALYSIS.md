# TOWNSHIP SAVE / INFO FILE — format triage (Part B)

Date: 2026-11-16 · Status: local research note. **Not committed, not pushed.**
Companion to `docs/TOWNTOOL-APK-ANALYSIS.md` (Parts A/D) and
`docs/RESEARCH-REASSESSMENT.md` (Part C).

---

## B.0 Missing-input report (branch stopped at the gate)

Part B of the brief asks for format triage of two Township files —
`mGameInfo.txt` and `LocalInfo.txt.xml` — "which may be available in
`C:\Township-Mod\incoming`".

**Status at run time: `C:\Township-Mod\incoming\` exists but is EMPTY**
(listing contains only `.` and `..`).

- `mGameInfo.txt` — **absent.**
- `LocalInfo.txt.xml` — **absent.**
- No ZIP / container bundles, no screenshots.

Per the brief ("Do not assume files have arrived; if missing, say so
clearly"), this branch is **stopped at the input gate**. No format
triage, magic, entropy, byte-frequency, string sampling, or crypto
metadata is recorded here — none of those measurements are meaningful
over bytes that are not present, and inventing them would fabricate
results.

**What to place in `incoming/` to unblock Part B:**

1. `mGameInfo.txt` — the file as-is (do not re-save through an editor,
   which can alter bytes/line-endings).
2. `LocalInfo.txt.xml` — same.
3. If the files arrived inside a ZIP or other container, the archive
   itself plus the tool used to extract it.
4. A one-line provenance note: which device/path they were captured
   from (this affects interpretation — e.g. whether the content is
   local plaintext state vs. an encrypted/obfuscated client cache).

## B.1 Reusable triage tooling (present, runnable, read-only)

`scripts/savefile_triage.py` — created by this investigation;
designed read-only per the brief ("create reproducible read-only
Python scripts in `scripts\` that write outputs only under analysis
output folders"):

- Inputs are opened read-only and never modified.
- All output goes ONLY under `C:\Township-Mod\analysis\out\<basename>\`:
  - `triage_report.txt` — size, SHA-256, entropy, null-byte ratio,
    magic-signature matches, first-256-byte hex dump,
    compressor/container probes (gzip/zlib/ZIP/ELF/SQLite/LevelDB/
    RocksDB/BSON/PNG/JPEG/7z/BZip2/zstd/LZ4), descriptive format
    heuristics (protobuf / MessagePack / BSON / LevelDB), printable
    string samples, and — only when no signature matches — a
    crypto-metadata note (possible IV/nonce/salt prefixes),
    explicitly labelled "no decryption attempted".
  - `first_256.hex`, `strings_sample.txt`, `bytefreq.csv`.

Usage (once the files are present):

```
python scripts/savefile_triage.py <file1> [<file2> ...]
```

## B.2 Protocol for when the files arrive (run in this order)

1. **Preserve originals; work on copies** — copy the received files
   into `analysis\incoming\`; run everything on the copies.
2. Record SHA-256 of each file and add rows to
   `docs/EVIDENCE-MANIFEST.txt`.
3. `python scripts/savefile_triage.py analysis\incoming\<files>`.
4. Read `analysis\out\<name>\triage_report.txt`: magic → hex →
   strings → probes, in that order.
5. Interpretation rules (per the brief, verbatim):
   - **"Do not infer encryption from garbled text or high
     entropy."** High entropy alone cannot distinguish compression,
     obfuscation, or encryption; record only which known signatures
     are present/absent.
   - If a format proves encrypted, identify only the cipher family /
     mode and the nonce/IV/tag/salt/KDF metadata at the front, and
     state whether key material appears in the supplied files or in
     the app package. **No brute-forcing, no key recovery.**
   - Extract only **non-sensitive user-owned game-state fields**,
     redacted — tokens, personal identifiers, and account
     identifiers are `[REDACTED]`.
   - **"Do not claim persistent Regatta points are caused by local
     save-file edits unless proven."** This triage alone cannot
     produce that proof; a proven path would need the files, a
     format read-off, AND a server-side correlation, none of which
     exist offline.
6. If `mGameInfo.txt` proves to be plaintext, record which fields are
   present (balances, town/clan IDs, last-sync timestamps —
   redacted) — and note that local fields are a **client-state
   snapshot**, not server state.

## B.3 Out of scope (per the brief)

- No attempts to defeat Playrix server-side protections.
- No credential handling or storage of secrets.
- No decryption; crypto signatures, if found, get metadata
  recording only.
- No modification of the supplied files in any way.

---

## Provenance & reproducibility

- Inputs: **none present** (`incoming/` empty); this document is the
  stop-report + tooling/protocol for when inputs arrive. No hashes
  are cited here because no file was read — see
  `docs/EVIDENCE-MANIFEST.txt` for the hash manifest of everything
  that WAS read this investigation.
- Tools: Python 3.12.10; `scripts/savefile_triage.py` (created this
  investigation, not yet run — nothing to run against).
- This file is a **local output**; not committed, not pushed.
