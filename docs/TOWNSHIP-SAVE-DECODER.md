# Township save-file decoding journal

**Date:** 2026-11-23  
**Workspace:** C:\Township-Mod  
**Repository:** totemoflol/Township-Mod  
**Scope:** Read-only decoding and validation of the supplied Township 40.0.1 save exports.

## Outcome

A local Python decoder successfully decoded both supplied save files:

- mGameInfo.txt.xml — outer 0x79 transform, followed by an LZ4 block wrapper; final XML validates.
- LocalInfo.txt.xml — outer 0x79 transform produces XML directly; final XML validates.

The implementation is in scripts/township_decode.py. It accepts either the extracted encoded file or the ZIP wrapper containing the XML-named entry. It writes decoded copies to a separate output directory and does not overwrite the inputs.

## Verified format pipeline

### 1. Eight-byte 0x79 header

| Offset | Size | Meaning |
|---:|---:|---|
| 0 | 1 byte | Type marker; 0x79 |
| 1 | 3 bytes | Little-endian hash length |
| 4 | 4 bytes | Little-endian hash seed |

The repository decoder's type check is equivalent to type XOR 0x3C equals 0x45.

### 2. Hash-table and byte transform

The decoder ports the Gaolious parsing_encoded_xml implementation:

- MurmurHash2 with multiplier 0x5BD1E995.
- A generated 727-byte (0x2D7) hash table.
- The table is generated from the header hash length and the 32-bit seed plus four.
- Each payload byte is XORed with the corresponding repeating table byte, with the differential-byte transform reversed in place.
- The previous byte used by the inverse differential step is the already-decoded previous byte.

The script also ports the inverse encoder. For each sample, re-encoding the transformed payload with the original seed and effective capacity matched the original encoded bytes exactly.

### 3. Capacity-dependent length

The encoded header's size expression depends on the source vector capacity:

    size = (hash_length - ((capacity + 8) ^ 0xC5EED)) ^ 0x396A8

All arithmetic is 32-bit unsigned arithmetic. The sample exports establish these effective capacities:

| Sample | Effective capacity | Meaningful transformed bytes |
|---|---:|---:|
| mGameInfo | 300,000 | 234,197 |
| LocalInfo | 230,809 | 230,809 |

The mGameInfo export has 65,803 zero bytes after its meaningful encoded body. The decoder checks that any ignored tail bytes are all zero. Do not assume that the effective capacity for every future export is simply its file size or the C++ helper's allocation bucket; inspect the header and padding for each new sample.

### 4. LZ4 stage

The transformed mGameInfo payload begins with magic bytes 04 22 4D 18.

The next four bytes contain the declared decompressed size, little-endian. The raw LZ4 block follows the eight-byte wrapper. The decoder contains a small dependency-free LZ4 block decompressor with input/output bounds checks.

LocalInfo has no LZ4 wrapper and begins directly with XML markup.

The mGameInfo decompressed output declares 1,818,948 bytes and has one terminal NUL after the closing root element. The script strips terminal NUL padding only when the preceding bytes parse as a complete XML document.

## Sample validation record

### mGameInfo

- Encoded input: 300,008 bytes.
- Header: type 0x79, hash length 577,922 (0x08d182), seed 0x42c85e68.
- Effective capacity: 300,000.
- Meaningful transformed bytes: 234,197.
- Ignored tail: 65,803 bytes; all zero.
- 0x79 decode/re-encode: exact byte-for-byte match.
- LZ4 declared output: 1,818,948 bytes.
- One terminal NUL removed after XML validation.
- Final XML: 1,818,947 bytes; root root; 97 direct children.
- Final SHA-256: 98ea5e2e74b03203085251fe1b272b2158ab09513eb577e388f8bfa076a21fab.

### LocalInfo

- Encoded input: 230,817 bytes.
- Header: type 0x79, hash length 1,044,093 (0x0fee7d), seed 0xf135e888.
- Effective capacity: 230,809.
- Meaningful transformed bytes: 230,809.
- Ignored tail: none.
- 0x79 decode/re-encode: exact byte-for-byte match.
- LZ4 wrapper: absent; transformed payload is XML.
- Final XML: 230,809 bytes; root root; 43 direct children.
- Final SHA-256: 5dffc9461fbf83036d3d328471e11126e5f8152fb121b51364761117edfa2268.

## Reproduction

Run in PowerShell from the cloud Windows machine:

    python C:\Township-Mod\repo\scripts\township_decode.py C:\Township-Mod\analysis\incoming\mGameInfo.txt.zip C:\Township-Mod\analysis\incoming\LocalInfo.txt.zip -o C:\Township-Mod\analysis\out\repo_decode

The output folder is created if needed. The script reports header fields, capacity, intermediate stages, exact round-trip verification, XML validation, output size and SHA-256.

## Files and privacy

Committed to the repository:

- scripts/township_decode.py — reusable decoder.

Decoded save files are retained only in the local analysis workspace under C:\Township-Mod\analysis\out\repo_decode\. They are intentionally **not** committed: save XML can contain personal/device and game-state information. The raw save exports are also not added by this change.

## Limitations and safe-use boundary

- The result establishes a working decoder for these two supplied sample exports, not necessarily every Township version or every historical save variant.
- Capacity selection for future files must be validated, not guessed.
- This work is for local read-only inspection. It does not change balances, inventory, progression, or other game state, and does not attempt to bypass server-side validation.
