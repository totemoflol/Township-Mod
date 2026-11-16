# TOWNSHIP SAVE / INFO FILE ANALYSIS (Part B)

Date: 2026-11-16 · Status: LOCAL OUTPUT. **Not committed, not pushed.**
Supersedes the earlier missing-input stop report of this name: both ZIP
inputs are now present in `incoming/` and `repo/incoming/` and have been
triaged below.

Confidence legend: **VERIFIED** (byte-level, re-runnable),
**STRONGLY SUPPORTED**, **HYPOTHESIS**, **UNKNOWN**, **INCONCLUSIVE**.

Mandate constraint honored throughout: *"do not infer encryption from
garbled characters or high entropy alone"* and *"no brute-force, key
extraction, or decryption attempts."* Where the data is high-entropy we
report the **byte structure** and **stop at metadata** (B.4).

---

## B.0 Inputs (present, verified — originals untouched)

Archives (originals, read-only; extraction performed on copies only):

| Path | Size | SHA-256 |
|---|---|---|
| `incoming/mGameInfo.txt.zip` | 234,746 B | `ba43b57f5c821bb68deef45f1c119cdbf26b2c0ac4b0c780c7b385b5ddb0f496` |
| `incoming/LocalInfo.txt.zip` | 172,405 B | `53fa08eaf141a1048f0bcd15ac001d568f64127e20088a7c7f75e74fc1be2e38` |

Both identical in `repo/incoming/` (same SHA-256, byte-compare clean).

ZIP census (python `zipfile`, read-only; `testzip()` → OK on both, i.e.
every entry's CRC matches its content):

**`mGameInfo.txt.zip`** — 2 entries:

| Entry | Uncompressed | CRC-32 | Compress | Mod time |
|---|---|---|---|---|
| `mGameInfo.txt.xml` | 300,008 B | `0xa8c47fab` | 8 (DEFLATE) | 2026-11-16 13:53:34 |
| `__MACOSX/._mGameInfo.txt.xml` | 316 B | `0x293af0de` | 8 | — |

**`LocalInfo.txt.zip`** — 2 entries:

| Entry | Uncompressed | CRC-32 | Compress | Mod time |
|---|---|---|---|---|
| `LocalInfo.txt.xml` | 230,817 B | `0xac586baf` | 8 | 2026-11-16 13:53:26 |
| `__MACOSX/._LocalInfo.txt.xml` | 316 B | `0x26fee9e7` | 8 | — |

- **`__MACOSX/` presence → both archives were created on macOS** (the
  Finder/`ditto` AppleDouble sidecar pattern). STRONGLY SUPPORTED.
- **Name mismatch (documented, per mandate):** the archive
  *named* `mGameInfo.txt.zip` contains an entry named
  **`mGameInfo.txt.xml`** — not `mGameInfo.txt`. The `.txt.xml` suffix
  is part of the *entry name*; the payload itself is binary (B.2), so
  the `.xml` suffix is **not** evidence of XML content.
- Extracted copies (analysis working set; originals untouched):
  | Copy path | SHA-256 |
  |---|---|
  | `analysis/incoming/mGameInfo/mGameInfo.txt.xml` | `d839aac5e048e8f3513c4e9c7d3e228d2d4850c6d97ae998f4e733bea53008c6` |
  | `analysis/incoming/LocalInfo/LocalInfo.txt.xml` | `9f2d435a80d0e37fa3be428acb998104ccf9ee0a55efcb67a66ddaf0e8d9af2c` |

## B.1 Method & commands (reproducible)

Run from `C:\Township-Mod\`:

```
# ZIP census / CRC validation / copy-only extraction (read-only on the archives)
python -c "import zipfile, hashlib, shutil; …"
    → entries/CRC table above; copies to analysis/incoming/{mGameInfo,LocalInfo}/

# Scripted byte triage (improved this session; see B.5)
python scripts/savefile_triage.py analysis/incoming/mGameInfo/mGameInfo.txt.xml analysis/incoming/LocalInfo/LocalInfo.txt.xml
    → [OK] mGameInfo.txt.xml -> analysis/out/mGameInfo.txt.xml (size=300008 entropy=6.9793 magic=none)
       [OK] LocalInfo.txt.xml -> analysis/out/LocalInfo.txt.xml (size=230817 entropy=7.9900 magic=none)
```

Per-file outputs (nonzero, verified present):
- `analysis/out/mGameInfo.txt.xml/{triage_report.txt, first_256.hex, strings_sample.txt, bytefreq.csv}`
- `analysis/out/LocalInfo.txt.xml/{triage_report.txt, first_256.hex, strings_sample.txt, bytefreq.csv}`

`strings_sample.txt` holds the only **longest** printable runs
(≥8 chars) each file contains: 37 in mGameInfo (max length 11) and 20 in
LocalInfo (max length 9) — e.g. `D*%7skfvJ`, `{GFp~Una`. All are
random-entropy noise consistent with B.2 (expected count of such runs
in 200–300 KB of uniform 8-bit data ≈ 37 / ≈ 20). **No run reaches 16 B
in either file; none is legible text, XML, JSON, or account data**, so
nothing account-related is (or could be) printed in this report.

## B.2 Per-file byte triage (VERIFIED measurements)

### B.2.1 `mGameInfo.txt.xml` (300,008 B; extracted SHA-256 `d839aac5…`)

- Overall Shannon entropy **6.9793** bits/byte (dragged down by the
  null tail, B.4); null ratio **0.2224**; printable ratio 0.2855.
- Head (first 16 B): `79 82 d1 08 68 5e c8 42 …` — no known container,
  compressor, DB, or XML magic (full `MAGIC_TABLE` probe: **no match**).
- **Three distinct zones** (50 KB block profile, `_block_structure`):
  | Region | Entropy | Null ratio | Character |
  |---|---|---|---|
  | `[0 : 200000)` | 7.994–7.996 | ~0.004 | uniformly high-entropy (random-like) |
  | `[200000 : 234205)` | ~6.3 (block `[200000:250000)` = 6.346, null 0.319) | rising | **sparse but high-entropy**: 255 distinct nonzero bytes; top byte frequencies near-uniform (0xf3=168, 0xae=167, 0x20=163, 0x5b=162, 0xc8=161, 0xf9=161; 0x03/0x05/0x3f/0x2f=157–158) |
  | `[234205 : 300008)` | 0.0 | 1.0 | **all-null tail, 65,803 B** (offset 234,205 to EOF) |
- No ASCII run ≥ 16 B anywhere; no null run ≥ 32 B *inside* the
  head/sparse zones (the long null runs are the tail itself).
- `0x1f 0x8b` byte-pair coincidences at 33772, 36564, 51403, 124394,
  130474, 181484, 199401 — **expected in random data** (~7 observed over
  200 KB at p=2⁻¹⁶ per position ≈ 3–4 expected), and no `zlib`/`gzip`
  stream starting at offset 0 decompresses (tested at offset 0 only;
  per-position inflate is not a format signature). Recorded
  descriptively; no conclusion.
- **No user-owned parseable game-state fields** are legible (no XML
  tags, no JSON, no protobuf tag structure above noise, no
  SQLite/LevelDB/RocksDB magic) → nothing to parse. **VERIFIED
  negative.**

### B.2.2 `LocalInfo.txt.xml` (230,817 B; extracted SHA-256 `9f2d435a…`)

- Overall Shannon entropy **7.9900**; null ratio **0.0036**;
  printable ratio 0.3695 (printable bytes expected ≈0.36 under 8-bit
  uniform — i.e. the "printable" fraction is just the random
  expectation, no text structure).
- Head (first 16 B): `79 7d ee 0f 88 e8 35 f1 …` — **no known magic
  matched**.
- **Uniformly high-entropy end-to-end**: all six 50 KB blocks at
  7.984–7.989, null ratio 0.003–0.004, longest null run in the whole
  file = **2 bytes**. No tail structure, no zone transitions — the
  opposite of B.2.1's three-zone layout.
- `0x1f 0x8b` coincidences at 10139, 97888, 117008, 117735, 140272,
  164990 — same expectation argument as B.2.1; no offset-0
  decompression.

### B.2.3 Conservative format probe (both files)

Single-byte-XOR format probe (`_xor_format_probe`, 256 keys,
multi-byte magics matched at **offset 0 only**; single-character
probes deliberately excluded as false-positive traps):

- **mGameInfo.txt.xml:** no key maps the head to `<?xml` / `Salt` /
  gzip / ZIP / ELF / zero-header. Head-200000B byte-frequency
  variance **1776.2** over 256 distinct bytes (uniform random-data
  regime).
- **LocalInfo.txt.xml:** same — no key hits. Head frequency variance
  **8458.6**.
- Conclusion: **NOT trivially single-byte-XOR-obfuscated; stop here.**
  No key recovery attempted (mandate). Multi-byte/streaming ciphers,
  KDF outputs, and per-file keyed blobs are out of scope by mandate.

## B.3 LocalInfo vs mGameInfo — structural comparison

Per mandate, comparison is made **only where evidence supports it**:

| Property | mGameInfo.txt.xml | LocalInfo.txt.xml | Comparison |
|---|---|---|---|
| Size | 300,008 B | 230,817 B | different |
| Overall entropy | 6.9793 | 7.9900 | mGameInfo lower *because of* its null tail |
| Zone structure | 3 zones: ~200 KB entropic → ~34 KB sparse-entropic → 65,803 B null tail | uniform high-entropy, no zones | **different internal layouts** |
| Head bytes | `79 82 d1 08 …` | `79 7d ee 0f …` | first byte both 0x79; **coincidence only** (p≈2⁻⁸), no header-format conclusion |
| Magic | none | none | both non-matching |
| 1-byte-XOR probe | negative | negative | both negative |
| ZIP packaging | DEFLATE, CRC `0xa8c47fab`, macOS-created | DEFLATE, CRC `0xac586baf`, macOS-created | **same packaging** (macOS, same session 8 s apart: 13:53:26 vs 13:53:34) |
| Legible user fields | none | none | nothing comparable at field level |

Assessment: the two files share **packaging** (macOS ZIP, near-
identical export timestamps) but **not** internal layout. Both are
consistent with *separate, independently-serialized game-state
artifacts* of a Township-family save (mGameInfo = "game info"
payload; LocalInfo = "local" account/state payload, per their names) —
**HYPOTHESIS**; the names are user-supplied and the content is not
decodable without key material (B.4). They cannot be cross-checked
field-by-field: **no legitimate format path exists** for either.

## B.4 Determination — format ID & "stop at metadata"

**VERIFIED:**
- Both payloads are high-entropy binary data. mGameInfo: ~200 KB
  random-like head, ~34 KB sparse-entropic region, exact 65,803-byte
  all-null tail (offset 234,205–300,008). LocalInfo: uniformly
  random-like throughout.
- Neither matches any known container/compressor/database/XML/JSON
  magic; both fail the conservative 1-byte-XOR format probe; neither
  contains a legible user-owned game-state field; neither contains a
  printable ASCII run ≥ 16 B (longest observed runs: 11 B in
  mGameInfo, 9 B in LocalInfo — random noise, B.1).

**Deliberately NOT concluded (mandate):** the data is *not* labelled
"encrypted" on entropy grounds alone. The profiles are **consistent
with** encrypted or keyed-serialized payloads, and also **consistent
with** losslessly compressed payloads whose headers are stripped —
both interpretations remain **UNKNOWN** without key material or the
exporter's documentation. Per the mandate we **stop at metadata
here**: no brute-force, no key extraction, no decryption, no attempt
to invert the 1-byte-XOR negatives into key recovery.

Link to Part A: the `TownshipEngine` in `TwnTool.apk` operates on
**decoded XML** game state (`<DataElem>`, `<Upgrade>`,
`<Buildings>`, §A.9 of `docs/TOWNTOOL-APK-ANALYSIS.md`). These files
are the *encoded* form such an engine would consume after a
legitimate decoding step — which the static evidence does not provide
and we do not attempt to reverse-engineer.

## B.5 Script improvements (this session) & reproducibility

`scripts/savefile_triage.py` (read-only on inputs; writes only under
`analysis/out/`):

- **New** `_block_structure(data, block=50000)` — per-block entropy +
  null-ratio profile and longest-null-run; this is what surfaced
  mGameInfo's three-zone layout (B.2.1).
- **Rewritten** `_xor_format_probe(data, limit=200000)` — conservative
  single-byte-XOR *format* probe: for each of 256 keys tests whether
  the XORed stream **begins** with a multi-byte known magic (offset 0
  only); single-character probes removed (trivial false positives in
  random data); records head byte-frequency variance. No key
  material extracted.
- Both wired into `triage_file` after `_check_leveldb_heuristics`;
  outputs per file: `triage_report.txt`, `first_256.hex`,
  `strings_sample.txt`, `bytefreq.csv`.

Re-run commands (from `C:\Township-Mod\`):

```
python scripts/savefile_triage.py analysis/incoming/mGameInfo/mGameInfo.txt.xml
python scripts/savefile_triage.py analysis/incoming/LocalInfo/LocalInfo.txt.xml
```

(expected stdout lines as in B.1; report files land under
`analysis/out/` as listed in B.1.)

## B.6 Executive summary & next steps (Part B)

**Summary:** both supplied ZIPs validated (CRC-clean) and extracted to
copies. The two inner payloads (`mGameInfo.txt.xml` 300,008 B;
`LocalInfo.txt.xml` 230,817 B) are high-entropy binary files with
**no known format signature and no legible fields**; mGameInfo
additionally has a 65,803-byte all-null tail; LocalInfo is uniformly
entropic. Per the mandate, analysis **stops at metadata** — no
decryption or key recovery.

**Prioritized next steps (all outside this mandate's scope, listed
for the record):**
1. Obtain from the *exporter* (whoever produced these files) the
   format/key documentation — only a legitimate key path changes the
   "UNKNOWN" verdicts. **HYPOTHESIS-gate**: without it, both files
   remain undecodable by design.
2. If a separate TwnTool *helper* APK or its `VpsConfig` is later
   supplied, re-audit for any exported save-encoding (e.g. the
   `/api/bin/encode|decode` request/response schema) that could
   legitimately decode these files — static audit only, no request
   replay.
3. Do **not** attempt offline inversion (entropy analysis beyond
   B.2.3, XOR sweeps, ML-based structure detection): mandated stop.

## Provenance & reproducibility

- Inputs: B.0 table (all SHA-256 values recomputed this session;
  originals in `incoming/` untouched — re-hash at final verification).
- Tools: Python 3.x stdlib (`zipfile`, `hashlib`, `math`,
  `collections`); `scripts/savefile_triage.py` (improved, B.5).
  No network access; no decryption.
- Companion docs: `docs/TOWNTOOL-APK-ANALYSIS.md` (§A.9 links the XML
  engine), `docs/RESEARCH-REASSESSMENT.md` (Part C),
  `docs/EVIDENCE-MANIFEST.txt` (§1 hashes, §5 script status).

This file is a **local output**; not committed, not pushed.
