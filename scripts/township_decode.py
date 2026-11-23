#!/usr/bin/env python3
"""
Read-only Township 0x79 save decoder.
Ports the Gaolious MurmurHash2/727-byte-table and differential XOR transform,
then handles the native 8-byte LZ4-block wrapper when present.

This writes decoded copies only; it never edits input files.
"""
from __future__ import annotations
import argparse
import hashlib
import os
import sys
import xml.etree.ElementTree as ET
import zipfile
from pathlib import Path

MASK = 0xFFFFFFFF
MURMUR_M = 0x5BD1E995
HASH_TABLE_SIZE = 0x2D7
HEADER_XOR = 0x396A8
CAPACITY_XOR = 0xC5EED
LZ4_MAGIC = bytes.fromhex("04 22 4D 18")


def murmurhash2_32(data: bytes, seed: int) -> int:
    """Faithful 32-bit little-endian port of Gaolious parsing_encoded_xml/src/hash.cpp."""
    length = len(data)
    h = (seed ^ length) & MASK
    pos = 0
    remaining = length
    while remaining >= 4:
        k = int.from_bytes(data[pos:pos + 4], "little")
        k = (k * MURMUR_M) & MASK
        k ^= k >> 24
        k = (k * MURMUR_M) & MASK
        h = (h * MURMUR_M) & MASK
        h ^= k
        pos += 4
        remaining -= 4

    if remaining == 3:
        h ^= data[pos + 2] << 16
        h ^= data[pos + 1] << 8
        h ^= data[pos]
        h = (h * MURMUR_M) & MASK
    elif remaining == 2:
        h ^= data[pos + 1] << 8
        h ^= data[pos]
        h = (h * MURMUR_M) & MASK
    elif remaining == 1:
        h ^= data[pos]
        h = (h * MURMUR_M) & MASK

    h ^= h >> 13
    h = (h * MURMUR_M) & MASK
    h ^= h >> 15
    return h & MASK


def generate_hash_table(hash_length: int, seed: int) -> bytes:
    """Faithful port of generateHashTable_0x2d7()."""
    value = seed & MASK
    table = bytearray()
    while len(table) < HASH_TABLE_SIZE:
        value = murmurhash2_32(value.to_bytes(4, "little"), hash_length)
        table.extend(value.to_bytes(4, "little"))
    return bytes(table[:HASH_TABLE_SIZE])


def decoded_length(hash_length: int, capacity: int) -> int:
    """Gaolious decode.cpp size expression, with uint32 wraparound."""
    rhs = ((capacity + 8) ^ CAPACITY_XOR) & MASK
    return (((hash_length - rhs) & MASK) ^ HEADER_XOR) & MASK


def encode_0x79(plain_body: bytes, seed: int, effective_capacity: int) -> bytes:
    """Faithful inverse port of Gaolious parsing_encoded_xml/src/encode.cpp."""
    hash_length = (
        (len(plain_body) ^ HEADER_XOR)
        + (((effective_capacity + 8) ^ CAPACITY_XOR) & MASK)
    ) & MASK
    if hash_length > 0xFFFFFF:
        raise ValueError("hash_length does not fit the format's 24-bit header")
    table = generate_hash_table(hash_length, (seed + 4) & MASK)
    header = bytes([0x79]) + hash_length.to_bytes(3, "little") + (seed & MASK).to_bytes(4, "little")
    body = bytearray(len(plain_body))
    previous_plain = 0
    for i, value in enumerate(plain_body):
        transformed = (value ^ table[i % HASH_TABLE_SIZE])
        body[i] = (transformed + previous_plain) & 0xFF
        previous_plain = value
    return header + bytes(body)


def decode_0x79(data: bytes, effective_capacity: int) -> tuple[bytes, dict]:
    """Decode the 8-byte 0x79 header and differential-XOR body."""
    if len(data) < 8:
        raise ValueError("Input is shorter than the 8-byte header")
    file_type = data[0]
    if ((file_type ^ 0x3C) != 0x45):
        raise ValueError(f"Unsupported type marker: 0x{file_type:02x}")

    hash_length = int.from_bytes(data[1:4], "little")
    hash_seed = int.from_bytes(data[4:8], "little")
    size = decoded_length(hash_length, effective_capacity)
    available = len(data) - 8
    if size > available:
        raise ValueError(
            f"Header/capacity imply {size:,} bytes but only {available:,} payload bytes exist. "
            f"Effective capacity supplied: {effective_capacity:,}."
        )

    table = generate_hash_table(hash_length, (hash_seed + 4) & MASK)
    output = bytearray(data[8:8 + size])

    # Exact in-place order from decode.cpp: subtract the already-decoded
    # previous byte, then XOR the current hash-table byte.
    for i in range(size):
        if i > 0:
            output[i] = (output[i] - output[i - 1]) & 0xFF
        output[i] ^= table[i % HASH_TABLE_SIZE]

    return bytes(output), {
        "type": file_type,
        "hash_length": hash_length,
        "hash_seed": hash_seed,
        "effective_capacity": effective_capacity,
        "decoded_length": size,
        "unused_tail_length": available - size,
    }


def lz4_block_decompress(block: bytes, expected_size: int) -> bytes:
    """Small dependency-free LZ4 raw-block decoder with bounds checks."""
    src = memoryview(block)
    i = 0
    out = bytearray()

    while i < len(src):
        token = src[i]
        i += 1

        literal_length = token >> 4
        if literal_length == 15:
            while True:
                if i >= len(src):
                    raise ValueError("Truncated LZ4 literal-length extension")
                extra = src[i]
                i += 1
                literal_length += extra
                if extra != 255:
                    break

        if i + literal_length > len(src):
            raise ValueError("LZ4 literal run exceeds input")
        if len(out) + literal_length > expected_size:
            raise ValueError("LZ4 literal run exceeds declared output size")
        out.extend(src[i:i + literal_length])
        i += literal_length

        # A block may end immediately after its last literal sequence.
        if i == len(src):
            break

        if i + 2 > len(src):
            raise ValueError("Truncated LZ4 match offset")
        offset = src[i] | (src[i + 1] << 8)
        i += 2
        if offset == 0 or offset > len(out):
            raise ValueError(f"Invalid LZ4 match offset: {offset}")

        match_length = token & 0x0F
        if match_length == 15:
            while True:
                if i >= len(src):
                    raise ValueError("Truncated LZ4 match-length extension")
                extra = src[i]
                i += 1
                match_length += extra
                if extra != 255:
                    break
        match_length += 4

        if len(out) + match_length > expected_size:
            raise ValueError("LZ4 match exceeds declared output size")

        # Byte-at-a-time copy is intentional: LZ4 matches can overlap.
        for _ in range(match_length):
            out.append(out[-offset])

    if len(out) != expected_size:
        raise ValueError(
            f"LZ4 output length mismatch: expected {expected_size:,}, got {len(out):,}"
        )
    return bytes(out)


def decode_file(input_path: Path, output_dir: Path, capacity: int | None = None) -> Path:
    # Accept either the extracted inner XML entry or its outer ZIP wrapper.
    if zipfile.is_zipfile(input_path):
        with zipfile.ZipFile(input_path, "r") as archive:
            entries = [n for n in archive.namelist() if n.lower().endswith(".xml")]
            preferred = input_path.stem + ".xml"
            if preferred in entries:
                entry = preferred
            elif len(entries) == 1:
                entry = entries[0]
            else:
                raise ValueError(f"Could not uniquely select XML entry in ZIP: {entries}")
            original = archive.read(entry)
        print(f"  source ZIP entry: {entry}")
    else:
        original = input_path.read_bytes()
    if len(original) < 8:
        raise ValueError("Input too short")

    # For these exported files the serialized body buffer is the bytes after
    # the 8-byte header. mGameInfo has a 300,000-byte backing body with zero
    # padding; LocalInfo is exact-length. Both are validated by their header
    # lengths and final XML parse.
    effective_capacity = capacity if capacity is not None else len(original) - 8
    transformed, meta = decode_0x79(original, effective_capacity)
    regenerated = encode_0x79(transformed, meta["hash_seed"], effective_capacity)
    expected_encoded = original[:8 + len(transformed)]
    if regenerated != expected_encoded:
        raise ValueError("0x79 round-trip verification failed; output was not accepted")
    meta["round_trip_verified"] = True

    print(f"\n[{input_path.name}]")
    print("  0x79 round-trip: exact byte-for-byte match")
    print(f"  input bytes: {len(original):,}")
    print(f"  effective capacity: {meta['effective_capacity']:,}")
    print(f"  header type: 0x{meta['type']:02x}")
    print(f"  hash_length: {meta['hash_length']:,} (0x{meta['hash_length']:06x})")
    print(f"  seed: 0x{meta['hash_seed']:08x}")
    print(f"  transformed bytes: {len(transformed):,}")
    print(f"  ignored tail bytes: {meta['unused_tail_length']:,}")
    print(f"  transformed prefix: {transformed[:16].hex(' ')}")

    if meta["unused_tail_length"]:
        tail = original[8 + meta["decoded_length"]:]
        nonzero = sum(1 for b in tail if b != 0)
        print(f"  ignored tail nonzero bytes: {nonzero:,}")
        if nonzero:
            raise ValueError("Unused tail is not all zero; refusing to silently discard it")

    if transformed.startswith(LZ4_MAGIC):
        if len(transformed) < 8:
            raise ValueError("LZ4 wrapper shorter than 8 bytes")
        expected_size = int.from_bytes(transformed[4:8], "little")
        print(f"  LZ4 wrapper: yes; declared output size: {expected_size:,}")
        final = lz4_block_decompress(transformed[8:], expected_size)
    else:
        print("  LZ4 wrapper: no; treating transformed data as XML")
        final = transformed

    # Township's main-save LZ4 output has a single NUL terminator after
    # the closing XML root. Strip terminal NUL padding only, and only keep
    # the stripped form if it parses as a complete XML document.
    trailing_nuls = len(final) - len(final.rstrip(bytes([0])))
    if trailing_nuls:
        candidate = final[:-trailing_nuls]
        try:
            ET.fromstring(candidate)
        except ET.ParseError:
            pass
        else:
            final = candidate
            print(f"  terminal NUL bytes stripped: {trailing_nuls}")

    # Verify XML before writing a .xml file.
    root = ET.fromstring(final)
    output_dir.mkdir(parents=True, exist_ok=True)
    out_path = output_dir / (input_path.stem.replace(".txt", "") + ".decoded.xml")
    out_path.write_bytes(final)

    print(f"  XML validation: PASS; root={root.tag!r}; direct children={len(root)}")
    print(f"  decoded bytes: {len(final):,}")
    print(f"  decoded SHA-256: {hashlib.sha256(final).hexdigest()}")
    print(f"  output: {out_path}")
    return out_path


def main() -> int:
    parser = argparse.ArgumentParser(description="Read-only Township 0x79 save decoder")
    parser.add_argument("inputs", nargs="+", type=Path, help="mGameInfo/LocalInfo encoded file(s)")
    parser.add_argument("-o", "--output-dir", type=Path, default=Path("decoded"))
    parser.add_argument("--capacity", type=int, default=None,
                        help="Override effective pre-header buffer capacity")
    args = parser.parse_args()

    failed = False
    for path in args.inputs:
        try:
            decode_file(path, args.output_dir, args.capacity)
        except Exception as exc:
            failed = True
            print(f"ERROR decoding {path}: {type(exc).__name__}: {exc}", file=sys.stderr)
    return 1 if failed else 0


if __name__ == "__main__":
    raise SystemExit(main())
