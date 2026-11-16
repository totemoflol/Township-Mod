#!/usr/bin/env python3
"""PART B — Township save/info file format triage (READ-ONLY).

Reproducible static triage for one or more supplied game files
(mGameInfo.txt, LocalInfo.txt.xml, or any ZIP/container bundle).

Guarantees:
  * Inputs are opened read-only and NEVER modified (originals preserved).
  * All output is written ONLY under C:\Township-Mod\analysis\out\<basename>\.
  * No network access, no password brute-forcing, no credential handling.
  * Entropy / byte-frequency / printable sampling are descriptive; this
    script explicitly does NOT infer "encrypted" from high entropy or
    garbled text (per the brief). It only records what byte signatures
    are actually present.

Usage:
    python scripts/savefile_triage.py <file1> [<file2> ...]

Per input file, writes:
    analysis/out/<basename>/triage_report.txt   (human-readable summary)
    analysis/out/<basename>/first_256.hex       (first 256 bytes, hex+dumps)
    analysis/out/<basename>/strings_sample.txt  (printable string samples)
    analysis/out/<basename>/bytefreq.csv        (byte-frequency table)
"""
import sys, os, csv, math, struct, shutil, zlib, tarfile, zipfile
import collections
import datetime

ROOT = r"C:\Township-Mod"
OUT_ROOT = os.path.join(ROOT, "analysis", "out")

# (label, magic prefix/regex, note)
MAGIC_TABLE = [
    (b"\x1f\x8b", "gzip (RFC 1952)"),
    (b"\x78\x9c", "zlib default CMF/FLG"),
    (b"\x78\x01", "zlib low-compression CMF/FLG"),
    (b"PK\x03\x04", "ZIP (local file header) / Office Open XML / Android APK"),
    (b"\x7fELF", "ELF executable"),
    (b"SQLite format 3", "SQLite database"),
    (b"PB", "LevelDB / RocksDB (version 0x06 check: 05 00 00 00 PB)"),
    (b"ROCKSDB:", "RocksDB"),
    (b"\xd2\x8a\x29\x1e\x84\x5c\x8b\x1e\x1a", "RocksDB (full 8-byte magic)"),
    (b"BSON", "BSON document (0x20 header)"),
    (b"\x89PNG", "PNG image"),
    (b"\xff\xd8\xff", "JPEG image"),
    (b"\x50K", "PK/zip family"),
    (b"MsgPack", "MessagePack marker (rare; usually 0x80-0xbf leading bytes)"),
    (b"PBZ", "possible MessagePack-zstd wrapper"),
    (b"\x28\xb5\x2f\xfd", "Zstandard frame magic"),
    (b"LZ4", "LZ4 (old block magic 0x02190000 little-endian)"),
    (b"0422\x19", "LZ4 block magic 0x042219 (as bytes \x04\x22\x19)"),
    (b"7z\xbc\xaf\x27\x1c", "7z archive"),
    (b"BZh", "BZip2"),
    (b"\x50K\x05\x06", "ZIP archive (central) / APK"),
]

def first_bytes_hex(data, n=256):
    lines = []
    for i in range(0, min(n, len(data)), 16):
        chunk = data[i:i+16]
        hexpart = " ".join(f"{b:02x}" for b in chunk)
        asc = "".join(chr(b) if 32 <= b < 127 else "." for b in chunk)
        lines.append(f"{i:06x}  {hexpart:<48}  {asc}")
    return "\n".join(lines)

def entropy(data):
    if not data:
        return 0.0
    freq = collections.Counter(data)
    total = len(data)
    return -sum((c/total)*math.log2(c/total) for c in freq.values())

def byte_frequency(data):
    freq = collections.Counter(data)
    return [(k, freq[k]) for k in range(256)]

def printable_strings(data, minlen=8, sample=500):
    out, cur = [], bytearray()
    for b in data:
        if 32 <= b < 127:
            cur.append(b)
        else:
            if len(cur) >= minlen:
                out.append(cur.decode("ascii"))
            cur = bytearray()
        if len(out) >= sample:
            break
    return out

def probe_compressors(data):
    """Descriptive probes only — records what signature IS present,
    does NOT conclude 'encrypted' from lack of a signature."""
    notes = []
    if data[:2] == b"\x1f\x8b":
        notes.append("gzip magic present; tried zlib.decompress(data, 31) -> "
                     + _try(lambda: zlib.decompress(data, 31), "ok" )
                     + "")
    if data[:2] in (b"\x78\x9c", b"\x78\x01"):
        r = _try(lambda: zlib.decompress(data))
        notes.append(f"zlib CMF/FLG present; inflate -> {r}")
    if data[:4] == b"PK\x03\x04":
        notes.append("ZIP local header present; tried zipfile -> "
                     + _try(lambda: _zf_names(data)))
    if data[:18] == b"ROCKSDB:01":
        notes.append("RocksDB magic; format confirmed")
    if data[:8] == b"SQLite f":
        notes.append("SQLite magic; format confirmed")
    if data[:4] == b"\x28\xb5\x2f\xfd":
        notes.append("Zstandard frame magic present")
    if data[:4] == b"\x04\x22\x19\x04":
        notes.append("LZ4 block magic present")
    if data[:4] == b"7z\xbc\xaf":
        notes.append("7z magic present")
    if data[:3] == b"BZh":
        notes.append("BZip2 magic present")
    return notes

def _try(fn, ok_msg="ok"):
    try:
        v = fn()
        return ok_msg + (f" : {v}" if v not in (None, "ok") else "")
    except Exception as e:
        return f"FAILED ({type(e).__name__}: {e})"

def _zf_names(data):
    import io
    with zipfile.ZipFile(io.BytesIO(data)) as z:
        names = z.namelist()
    return f"{len(names)} entries; first: {names[:5]}"

def _check_protobuf_heuristics(data):
    """Heuristic: count 0x08..0x1a tag+varint patterns — descriptive only."""
    tag_bytes = [b"\x08", b"\x0a", b"\x10", b"\x12", b"\x18", b"\x1a"]
    hits = 0
    for tb in tag_bytes:
        hits += data.count(tb)
    return f"tag-byte heuristic hits (08/0a/10/12/18/1a): {hits} (not proof of protobuf)"

def _check_msgpack_heuristics(data):
    """Leading-byte heuristics for MessagePack — descriptive only."""
    mb = [b"\x80", b"\x81", b"\x82", b"\x83", b"\x84", b"\x85",
          b"\x90", b"\x91", b"\x92", b"\xd4", b"\xd5", b"\x86", b"\x87"]
    return f"MessagePack leading-byte heuristic: {sum(1 for b in mb if b in data[:4096])} of 13 sampled types in first 4096B"

def _check_bson_heuristics(data):
    if len(data) < 5:
        return "BSON: file too small for 4-byte int32 length header"
    ln = struct.unpack_from("<i", data, 0)[0]
    return f"BSON header: int32 LE = {ln} ({'consistent with filesize' if ln == len(data) else 'MISMATCH — not a plain BSON doc'})"

def _check_leveldb_heuristics(data):
    # LevelDB: 80-byte zero header + 32-byte table meta + 8-byte version "06"
    hdr = data[:80]
    magic_pos = 4 + 32 + 8
    magic = data[magic_pos:magic_pos+2]
    return f"LevelDB magic at offset {magic_pos}: {magic.hex()} (expected 05 00 → LevelDB; PB → other)"

def _try_decrypt_metadata(data):
    """If no known signature, record possible crypto metadata:
    search for IV/nonce/salt field lengths at the start — purely descriptive,
    does NOT attempt decryption."""
    notes = []
    if data[:16] == b"\x00" * 16:
        notes.append("first 16 bytes all zero — possible AES-CTR/CBC IV or random padding")
    if len(data) >= 32 and data[:4] in (b"\x21\xff", b"\x00\x01\x00\x01"):
        notes.append("possible WebCrypto (AES-GCM/CBC) 12-16B IV prefix")
    notes.append("No auto-brute-forcing; if encryption is suspected, "
                 "the KDF/nonce/IV/tag/salt metadata must be identified "
                 "before key material can be located.")
    return notes

def triage_file(path):
    basename = os.path.basename(path)
    outdir = os.path.join(OUT_ROOT, basename)
    os.makedirs(outdir, exist_ok=True)
    with open(path, "rb") as f:
        data = f.read()
    size = len(data)
    ent = entropy(data)
    freq = byte_frequency(data)
    nfreq = collections.Counter(data)
    null_ratio = nfreq[0] / size if size else 0.0

    # magic
    magic_hits = []
    for magic, label in MAGIC_TABLE:
        if data[:len(magic)] == magic:
            magic_hits.append(label)

    lines = []
    lines.append(f"=== Triage report: {basename} ===")
    lines.append(f"Date: {datetime.datetime.now().isoformat()}")
    lines.append(f"Size: {size} bytes (0x{size:x})")
    lines.append(f"SHA-256: {__import__('hashlib').sha256(data).hexdigest()}")
    lines.append(f"Entropy: {ent:.4f} bits/byte (max 8.0)")
    lines.append(f"Null-byte ratio: {null_ratio:.6f}")
    lines.append(f"Magic signature match: {magic_hits if magic_hits else 'none of the known signatures matched'}")
    lines.append("")

    # first 256 hex
    hex_dump = first_bytes_hex(data, 256)
    lines.append("--- First 256 bytes (hex + ASCII) ---")
    lines.extend(hex_dump.splitlines())
    lines.append("")

    # compressors
    lines.append("--- Compressor / container probes ---")
    comp_notes = probe_compressors(data)
    if comp_notes:
        lines.extend(comp_notes)
    else:
        lines.append("No compressor/container magic signature matched in first 8 bytes")
    lines.append("")

    # protobuf / msgpack / bson / leveldb
    lines.append("--- Format heuristics (descriptive, not conclusive) ---")
    lines.append(_check_protobuf_heuristics(data))
    lines.append(_check_msgpack_heuristics(data))
    lines.append(_check_bson_heuristics(data))
    lines.append(_check_leveldb_heuristics(data))
    lines.append("")

    # printable strings sample
    strs = printable_strings(data, minlen=8, sample=500)
    lines.append(f"--- Printable string samples (min 8 chars, up to 500) ---")
    lines.extend(strs if strs else ["(none found)"])
    lines.append("")

    # no known signature → crypto metadata check (descriptive only)
    if not magic_hits:
        lines.append("--- No known magic — crypto metadata check (descriptive only, no decryption attempted) ---")
        for n in _try_decrypt_metadata(data):
            lines.append(f"  * {n}")
        lines.append("  Per brief: do NOT infer 'encrypted' from high entropy or garbled text alone.")
        lines.append("")

    # write outputs
    with open(os.path.join(outdir, "triage_report.txt"), "w") as f:
        f.write("\n".join(lines))
    with open(os.path.join(outdir, "first_256.hex"), "w") as f:
        f.write(hex_dump)
    with open(os.path.join(outdir, "strings_sample.txt"), "w") as f:
        f.write("\n".join(strs))
    with open(os.path.join(outdir, "bytefreq.csv"), "w", newline="") as f:
        w = csv.writer(f)
        w.writerow(["byte", "count", "pct"])
        for b, c in freq:
            w.writerow([hex(b), c, f"{c/size*100:.4f}" if size else "0"])

    print(f"[OK] {basename} -> {outdir}")
    print(f"     size={size} entropy={ent:.4f} magic={magic_hits or 'none'}")
    return outdir

if __name__ == "__main__":
    if len(sys.argv) < 2:
        print(__doc__)
        sys.exit(1)
    for p in sys.argv[1:]:
        if not os.path.exists(p):
            print(f"[MISSING] {p} — skipped")
            continue
        triage_file(p)
