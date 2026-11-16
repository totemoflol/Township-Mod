# RESEARCH REASSESSMENT (Part C) — re-verification of first-brief findings

Date: 2026-11-16 · Status: local research note. **Not committed, not pushed.**
Originals (`docs/REPORT.md`, `docs/MECHANISM-ANALYSIS.md`, both `libgame.so`,
`origin.apk`) are preserved untouched; this document records the corrections
instead of editing them.

Confidence legend (same as `docs/TOWNTOOL-APK-ANALYSIS.md`):
**VERIFIED / STRONGLY SUPPORTED / HYPOTHESIS / UNKNOWN**, and
**INCONCLUSIVE** where a method self-gates.

---

## C.1 Inputs & method

| Artifact | Role this pass | SHA-256 (full table in `docs/EVIDENCE-MANIFEST.txt`) |
|---|---|---|
| `extracted/original-arm64/lib/arm64-v8a/libgame.so` (186,157,568 B) | reachability + FDE/prologue ground truth | `B6D4C9…75DFF9` |
| `extracted/mod-apk/lib/arm64-v8a/libgame.so` (186,157,568 B) | gut/no-op rewrite verification | `A38B07…9EA3E6` |
| `extracted/mod-apk/lib/arm64-v8a/libSignatureKiller.so` | signature-spoof layer 2 | `482F00…F1076` |
| `extracted/mod-apk/assets/SignatureKiller/origin.apk` | pristine-baseline bundling | `04169E…0423` |
| `extracted/apktool-original-base/smali/com/playrix/engine/PlayIntegrityCustom.smali` | cloudProjectNumber literal | read-only |
| `docs/REPORT.md`, `docs/MECHANISM-ANALYSIS.md` | claims under audit | read-only |

Methods (all read-only, reproducible):

1. **FDE walk with self-gating** (`scripts/reverify_guts.py`): `.eh_frame`
   record walk (32-bit shared-library form), auto-detecting whether the FDE
   address fields are VAs or file offsets, then **self-testing the decoded
   address fields against three known `.dynsym` function entries**
   (`nativeSendRequest` @ 0x7b18194, `JNI_OnLoad` @ 0x7c09328,
   `bad_result_access` @ 0x7057ff8). If they fail to reproduce, every
   FDE-derived verdict is emitted as INCONCLUSIVE, never asserted. This is
   the authoritative alternative to the nearest-ret/prologue heuristic the
   brief forbids.
2. **ARM64 `bl` decoder, capstone-verified**: the direct-call branch
   decoder was validated against capstone 5.0.7 on a 128 KB `.text` slice
   (0x4471680..0x4491680): **2891/2891 exact target matches**. Only the
   verified `bl` (branch-and-link) decoder is used for caller evidence.
   The `b` (plain jump) decoder **could not be validated on this binary**
   — its B-words do not satisfy the ARM64 spec (non-zero low-6 bits, a
   non-standard IL2CPP encoding) and no imm19 candidate reproduced
   capstone targets (0/743). `b`-counters in the script are kept for
   completeness and are **not evidence**.
3. **Data-pointer scan**: 8-byte little-endian pointers equal to each site
   VA across `.rodata/.data/.data.rel.ro/.got/.got.plt` — the expected
   channel for indirect (function-pointer-table) reference of an
   unexported IL2CPP function.
4. **Diff classification re-run** (`scripts/classify_so_diff2.py`) — status
   in C.8.

## C.2 Corrections to prior documents

### C-1. `cloudProjectNumber` value was wrong in `MECHANISM-ANALYSIS.md:60`
- Prior: "0xb4359ae5c1L (= **1245155488737**)".
- Correct: **0xb4359ae5c1L = 773993457089** (plain base-conversion; the old
  note carried a bad decimal).
- Evidence: `PlayIntegrityCustom.smali:17`
  (`.field private static final cloudProjectNumber:J = 0xb4359ae5c1L`) and
  `:469` (`const-wide v3, 0xb4359ae5c1L`); re-emitted by
  `reverify_guts.py [5]`.
- **VERIFIED.** Not a credential — a public project-identifier constant;
  noted only to fix the record.

### C-2. "Neighbourhood of `nativeSendRequest`" overstates the distance
`REPORT.md` §3: "Two of them sit in the sparse-dynsym neighbourhood of
`Java_com_playrix_engine_PlayIntegrityCustom_nativeSendRequest`".
Signed distances measured against the actual `.dynsym` ranges (3670
entries; no `.symtab` in these binaries):

| Site | Nearest dynsym BELOW | Distance | Nearest ABOVE |
|---|---|---|---|
| 0x7bb3414 | 0x7b18194 `nativeSendRequest` | **−637,216 B** (0x9b280) | 0x7c09328 `JNI_OnLoad` (+0x55f14) |
| 0x7bc076c | 0x7b18194 `nativeSendRequest` | **−691,608 B** (0xa85d8) | 0x7c09328 `JNI_OnLoad` (+0x48bbc) |

- **No dynsym range encloses either site** (value ≤ site < value+size is
  empty for both) — they are unexported internals.
- 637 KB / 692 KB of code is not a "neighbourhood"; the old phrasing
  implied proximity. **STRONGLY SUPPORTED correction** (byte-level
  arithmetic on the symbol table). Per the brief, this is *proximity
  reporting, not a call/data-flow relationship* — and the data-pointer
  scan found **no 8-byte pointers to either site** in any scanned data
  section, so no indirect reference through a table is demonstrated.

### C-3. All `.eh_frame`-derived entry/interior verdicts are INCONCLUSIVE
- `.eh_frame_hdr` is **non-classic** in this IL2CPP binary
  (`size_field=0x3b031b01, enc=164, count_field=0x2d005af1`) — not a
  parseable classic function-start table.
- `.eh_frame` walk: **745,011 FDE bodies, 0 malformed**; the
  VA-vs-file-offset auto-detection produced a **dead tie** (320,487 :
  320,487 hits inside `.text` under each interpretation) — a further
  sign the layout is the packed IL2CPP variant.
- Self-test `[3b]` **FAIL**: none of the three known exported `.dynsym`
  entries appears in the decoded FDE start set. The script therefore
  emits every FDE verdict as INCONCLUSIVE.
- Consequence: any entry/interior claim resting on FDE matching from the
  first pass (and the prior `site_fde_deepdive.py`, which assumed a
  classic DWARF32 layout) is **withdrawn as evidence**.

### C-4. `REPORT.md` §3 "No direct `bl` callers exist in `.text`" is FALSE for sites 1–2
Corrected direct `bl`-caller counts in the **original** `libgame.so`
(verified decoder; full `.text` scan, 2,011,476 `bl` words decoded):

| Site | direct `bl` callers | Caller sites (VA, original binary) |
|---|---|---|
| 0x4ef7150 | **0** | — |
| 0x7bb3414 | **17** | 0x52da0a8, 0x52dee84, 0x52e1600, 0x5a4c414, 0x5df7b88, 0x5df8118, 0x602eb0c, 0x6036e20, 0x61a413c, 0x622b908, 0x622f018, 0x63aae68, 0x65ca094, 0x708e914, 0x73281fc, 0x7a76290, 0x7ade17c |
| 0x7bc076c | **6** | 0x4db3df0, 0x6d0fa80, 0x6e6f110, 0x7161c04, 0x78210c0, 0x7c071ec |

- **VERIFIED** (capstone-verified `bl` decode, 2891/2891 self-check).
- This is call-graph evidence *into* the sites, not proof of what the
  functions do; it does bear on entry status (C.5).

### C-5. Site-status verdicts, re-issued without the forbidden heuristic
Per the brief: nearest-ret/prologue proximity is **not** proof of
function boundaries. What survives after the C-3 withdrawal:

| Site | Prologue at site (descriptive only) | Direct `bl` in | FDE (INCONCLUSIVE) | Data ptrs to site | Verdict |
|---|---|---|---|---|---|
| 0x4ef7150 | `sub sp,sp,#0x80` | 0 | interior match [0x4ef6f64..0x4ef72e0) — untrusted | none | **UNKNOWN** (leans interior; not assertable) |
| 0x7bb3414 | `sub sp,sp,#0x1b0` | 17 | no FDE match (untrusted) | none | **STRONGLY SUPPORTED true entry** |
| 0x7bc076c | `stp x30,x21,[sp,#-0x20]!` | 6 | no FDE match (untrusted) | none | **STRONGLY SUPPORTED true entry** |

Rationale: 17 and 6 independent direct `bl` targets are the kind of
evidence that lands on entries (interior single-targets exist but are
rare, and multiple unrelated callers make an interior site very
unlikely); a plain jump's target set is **not** cited because the `b`
decoder is unvalidated on this binary. Site 0 has *no* direct callers,
an untrusted FDE interior hit, and only a prologue-like first
instruction — it stays UNKNOWN, and `REPORT.md` §3's blanket "All three
are true function entries" is **downgraded** to "2 STRONGLY SUPPORTED
entries, 1 UNKNOWN".

## C.4 The required §6 audit — DOWNGRADED

Brief: *"audit REPORT.md §6's claim that the three patched functions
disable balance deduction/commit — Either produce a traceable
call/data-flow proof or downgrade/remove the claim."*

Result: **downgraded.** Findings:

1. **Proven (byte-level):** in the mod `libgame.so`, the three sites'
   function entries are replaced (forward bodies `ret` / `mov x0,#0`-style
   no-ops) and 1,889 further instruction rewrites are no-op
   substitutions (counts per first-brief `classify_so_diff2.py`; re-run
   status C.8).
2. **Not demonstrated:** that those three functions "disable
   balance deduction/commit" or "force re-validation of the cached
   balance" (REPORT §6 wording). This session produced **no traceable
   call/data-flow path** from the gutted functions to any currency,
   balance, or commit code: no 8-byte data pointers to the sites, a
   sparse 3670-entry `.dynsym` (no `.symtab`), and the FDE table is
   unusable for range analysis (C-3). The gutted forward bodies do not
   reference currency/balance strings on read-off.
3. **Distance context:** the PlayIntegrityCustom JNI export is 637/691 KB
   from sites 1/2 (C-2) — proximity is not the enclosure or flow the old
   note implied.
4. **Reworded claim (to replace REPORT §6's mechanism sentence):**
   - **PROVEN:** three function entries (2 confirmed, 1 unknown, see
     C-5) are semantically gutted to no-op returns in the mod binary.
   - **UNKNOWN:** what those three functions do. The names in the first
     report were inferences from the nearest sparse-dynsym entries, as
     REPORT already caveated.
   - **HYPOTHESIS (unsupported by call/data-flow evidence in these
     artifacts):** that gutting them suppresses balance
     deduction/commit or re-validation. Plausible, and it is why the
     tool's *on-device* balance display can freeze — but "the balance
     deduction/commit step is a no-op" must not be restated as fact.
   - What would settle it: dynamic instrumentation or a full symbolized
     build (out of scope here — no Playrix live interaction, no
     cheating tooling, per the brief).

## C.5 Standing user observation — persistence, re-classified

User observation (from the brief): the tool's Regatta activity "appears in
the real Township co-op and persists after reload." Per the brief this is
**to verify carefully, not proof of mechanism**. Classification:

- Cross-account + post-reload persistence **cannot** be produced by local
  memory classes (family A) or PLT-hook classes (family B): a local no-op
  or hook neither credits *another* account nor survives a server sync.
- It **is** consistent with C (local multi-account automation) and/or D
  (remote control plane) — the H1–H4 set in `TOWNTOOL-APK-ANALYSIS.md`
  Part D. **No claim is made as to which**, and no claim is made that
  local save-file edits caused the persistence (save files are absent —
  Part B gate).
- The Regatta observation therefore **stands as an observation, not a
  verified causal fact** (STRONGLY SUPPORTED that a C/D-class mechanism
  is required; UNKNOWN which).

## C.6 Byte-level verified facts (kept, separated from interpretation)

**VERIFIED / STRONGLY SUPPORTED this pass:**
- All eight artifact SHA-256 digests (`docs/EVIDENCE-MANIFEST.txt`).
- `cloudProjectNumber` = 773993457089 (C-1).
- Direct `bl` counts 0 / 17 / 6 with caller-site lists (C-4).
- Prologue forms at the three sites (descriptive).
- `.eh_frame`/`.eh_frame_hdr` non-standard; FDE verdicts INCONCLUSIVE (C-3).
- No dynsym enclosure; no data pointers to sites 1–2 (C-2).
- First-brief carryovers re-confirmed by byte inspection this pass:
  two-tier signature spoof (Java `PackageInfo.CREATOR` + native
  open-family hooks in `libSignatureKiller.so`), XHook 1.2.0 aarch64
  PLT-hooking library present, pristine `origin.apk` bundled under
  `assets/SignatureKiller/`.
- 1,889 no-op rewrites + 3 semantic guttings (first-brief classifier;
  re-run C.8).

**Interpretations NOT verified by any byte-level evidence:**
- The §6 mechanism sentence (downgraded, C.4).
- Any statement that the tool's backend or Playrix's server performed a
  specific action (endpoints absent from disk — Part A gate).
- Which H1–H4 family generates the Regatta rounds (UNKNOWN, Part D).
- Regatta causation by local save edits (impossible to prove — inputs
  absent, and a proven path would also need a server-side correlation).

## C.7 Constraints honored

No commit/push (all outputs are local files under `C:\Township-Mod`).
Originals untouched (copies only where a tool needed scratch —
`analysis\out\` is the sole write target). No Playrix live interaction,
no credential handling, no secrets printed (the one constant above,
`cloudProjectNumber`, is a public identifier, not key material). No
brute-forcing, decryption attempts, or anti-cheat bypass.

## C.8 Diff-classifier re-run — 1,889 / 3 re-confirmed

`python scripts/classify_so_diff2.py` (background task `bdh922mkk`, exit 0):
**1,892 diff clusters = 1,889 equivalent zeroing-rewrite clusters + 3
semantic (non-equivalent) clusters**, 5 semantic instructions total.

- Equivalent instruction mix (orig → mod): 490 `mov`→`eor`, 484
  `mov`→`and`, 465 `mov`→`mov`, 455 `sub`… (1,894 instructions across
  the 1,889 clusters).
- Semantic clusters, at exactly the three Part C sites:
  - `0x4ef7150`: `sub sp,sp,#0x80`→`mov x0,#0` +
    `str x30,[sp,#0x60]`→`ret` (2 insns)
  - `0x7bb3414`: `sub sp,sp,#0x1b0`→`ret` (1 insn)
  - `0x7bc076c`: `stp x30,x21,[sp,#-0x20]!`→`mov x0,#0` +
    `stp x20,x19,[sp,#0x10]`→`ret` (2 insns)

**VERIFIED:** the first-brief 1,889 no-op / 3 semantic-gut counts are
reproduced exactly by an independent re-run; the re-worded §6 claim in
C-4 now rests on re-confirmed byte-level figures.

## Provenance & reproducibility

- Commands: `python scripts/reverify_guts.py` (read-only, stdout);
  `python scripts/classify_so_diff2.py` (C.8);
  `python scripts/semantic_gut.py` (prologue read-offs); full `.text`
  branch scan (capstone) as an independent cross-check of C-4 counts.
- Tools: Python 3.12.10, capstone 5.0.7; readelf/pyelftools/lief absent —
  ELF parsing done manually in Python (documented in
  `docs/EVIDENCE-MANIFEST.txt`).
- This file is a **local output**; not committed, not pushed.
