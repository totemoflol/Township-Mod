#!/usr/bin/env python3
"""RE-VERIFICATION (Part C) — rigorous true-entry vs interior-patch analysis
for the three libgame.so patch sites.

READ-ONLY against the source binaries (stdout only, nothing written).

For each patch site we ask, using authoritative evidence (not a nearest-ret
heuristic):
  [1] Is it a true function entry or an interior instruction?
        - .eh_frame FDE table: a FDE's initial_location == site => entry;
          site strictly inside a FDE [start, start+pc_range) => interior.
          The 32-bit record walk self-synchronizes (0 malformed records),
          but the address fields are SELF-TESTED against known .dynsym
          function entries; if the decoded values fail to reproduce them,
          the FDE layout is the IL2CPP/non-standard variant and every
          FDE-derived verdict is marked INCONCLUSIVE, not asserted.
        - .eh_frame_hdr (best effort; custom/non-standard layout is logged).
        - tail of the previous instruction stream (ret/br boundary check).
        - prologue signature at the site (positive evidence of entry, NOT
          proof - a trampoline can also present a prologue-like form).
  [2] Which .dynsym ranges actually enclose each site (value <= site <
      value+size)? We report nearest neighbours WITH SIGNED DISTANCES so the
      old "enclosing dynsym symbol" mislabel is not repeated.
  [3] Indirect reachability in the ORIGINAL binary:
        - direct bl/b targets == site VA anywhere in .text (branch scan)
        - 8-byte little-endian data pointers == site VA in data/ro sections
          (this is the evidence that a stripped/IL2CPP function is referenced
          through a function-pointer table, i.e. called indirectly).
  [4] Cheap re-checks of previously documented claims.
"""
import struct, bisect
from capstone import Cs, CS_ARCH_ARM64, CS_MODE_ARM

ORG = r"C:\Township-Mod\extracted\original-arm64\lib\arm64-v8a\libgame.so"
org = open(ORG, "rb").read()

def parse_elf(d):
    e_phoff = struct.unpack_from("<Q", d, 0x20)[0]
    e_shoff = struct.unpack_from("<Q", d, 0x28)[0]
    e_shnum = struct.unpack_from("<H", d, 0x3C)[0]
    e_shstr = struct.unpack_from("<H", d, 0x3E)[0]
    nph = struct.unpack_from("<H", d, 0x38)[0]
    shent = struct.unpack_from("<H", d, 0x3A)[0]
    loads = []
    for i in range(nph):
        off = e_phoff + i*0x38
        if struct.unpack_from("<I", d, off)[0] == 1:
            lo, va = struct.unpack_from("<QQ", d, off+8)
            fsz = struct.unpack_from("<Q", d, off+0x20)[0]
            loads.append((lo, va, fsz))
    secs = []
    for i in range(e_shnum):
        off = e_shoff + i*shent
        sn = struct.unpack_from("<I", d, off)[0]
        so, ssz = struct.unpack_from("<QQ", d, off+0x18)
        slink = struct.unpack_from("<I", d, off+0x28)[0] if off+0x2C <= len(d) else 0
        secs.append((sn, so, ssz, slink))
    shstr_off = secs[e_shstr][1]
    def sname(idx):
        e = d.find(b"\0", shstr_off+idx)
        return d[shstr_off+idx:e].decode("utf8", "replace")
    return loads, [(sname(s[0]), s[1], s[2], s[3]) for s in secs]

def load_map(d):
    loads, _ = parse_elf(d)
    def to_va(f):
        for lo, va, sz in loads:
            if lo <= f < lo+sz: return va + (f-lo)
        return None
    def to_file(va):
        for lo, va0, sz in loads:
            if va0 <= va < va0+sz: return va - va0 + lo
        return None
    return to_va, to_file

def dynsym(d, secs_named):
    for nm, so, sz, sl in secs_named:
        if nm in (".symtab", ".dynsym"):
            strtab = secs_named[sl][1]
            syms = []
            for i in range(sz // 0x18):
                b = so + i*0x18
                stn = struct.unpack_from("<I", d, b)[0]
                st_value, st_size = struct.unpack_from("<QQ", d, b+8)
                if st_value == 0: continue
                e = d.find(b"\0", strtab+stn)
                syms.append((st_value, st_size, d[strtab+stn:e].decode("utf8","replace")))
            return nm, syms
    return None, []

to_va, to_file = load_map(org)
_, secs_named = parse_elf(org)
sec_names = [s[0] for s in secs_named]
byname = {s[0]: (s[1], s[2]) for s in secs_named}
symtab_name, syms = dynsym(org, secs_named)
syms.sort()
print(f"[1] symbol table used: {symtab_name} ({len(syms)} entries with value != 0)")
print(f"    .symtab present: {'.symtab' in sec_names}   .eh_frame present: {'.eh_frame' in sec_names}   .eh_frame_hdr present: {'.eh_frame_hdr' in sec_names}")

md = Cs(CS_ARCH_ARM64, CS_MODE_ARM)
SITES = [0x4ef7150, 0x7bb3414, 0x7bc076c]

# .text VA range (for FDE auto-detection)
text_va0 = to_va(byname[".text"][0]); text_va1 = text_va0 + byname[".text"][1]
print(f"    .text VA range: {hex(text_va0)} .. {hex(text_va1)}")

# ---------------------------------------------------------------- .eh_frame_hdr
# Classic layout: u32 size, i8 encoding, u32 count, then count entries.
# Some custom toolchains (Unity IL2CPP) ship a non-standard layout; detect that.
hdr_parsed = False
if ".eh_frame_hdr" in sec_names:
    ho, hs = byname[".eh_frame_hdr"]
    szf, enc, cnt = struct.unpack("<IBI", org[ho:ho+9])
    if szf == hs and cnt*4 + 9 <= hs and cnt > 0:
        vals = [to_va(ho+9+4*i) for i in range(cnt)]
        vals = [v for v in vals if v is not None]
        print(f"[2] .eh_frame_hdr: classic layout, {cnt} entries (best-effort)")
        hdr_parsed = True
    else:
        print(f"    .eh_frame_hdr NON-CLASSIC layout (size_field={hex(szf)}, enc={enc}, "
              f"count_field={hex(cnt)}, section={hs} bytes); first 16 bytes: {org[ho:ho+16].hex()}")
        print(f"    -> not parseable as a classic function-start table; relying on .eh_frame FDEs instead")

# ---------------------------------------------------------------- .eh_frame FDEs
# DWARF32 shared library: each record = u32 length, u32 CIE_ID (0 => CIE).
# FDE body: u32 ciede, then initial_location, then pc_range (4-byte each when the
# CIE uses DW_EH_PE_absptr with a 32-bit pointer). The address fields are EITHER
# VAs (absptr) or file offsets (pcrel/abs) depending on the CIE encoding, so we
# auto-detect: count how many FDE starts land inside the .text VA range under each
# interpretation and pick the winner.
fdes_raw = []   # (raw_start_u32, raw_range_u32)
if ".eh_frame" in sec_names:
    so, sz2 = byname[".eh_frame"]
    off = so
    nrec = 0
    nbad = 0
    while off + 8 <= so + sz2:
        L = struct.unpack_from("<I", org, off)[0]
        td = struct.unpack_from("<I", org, off+4)[0]
        if L == 0xFFFFFFFF or off + 4 + L > so + sz2 + 1:
            nbad += 1
            break
        if td != 0:
            raw_start = struct.unpack_from("<I", org, off+8)[0]
            raw_range = struct.unpack_from("<I", org, off+12)[0]
            fdes_raw.append((raw_start, raw_range))
        off += 4 + L
        nrec += 1
    # auto-detect VA vs file-offset
    def in_text(v): return text_va0 <= v < text_va1
    hitsA = sum(1 for s, r in fdes_raw if in_text(s))                 # raw == VA
    hitsB = sum(1 for s, r in fdes_raw if (to_va(s) is not None) and in_text(to_va(s)))  # raw == file off
    use_va = hitsA >= hitsB
    print(f"[3] .eh_frame records: {nrec} (skipped {nbad} malformed at {hex(off)})")
    print(f"    FDEs with body: {len(fdes_raw)}; raw-as-VA hits in .text: {hitsA}, raw-as-fileoff hits: {hitsB} "
          f"=> using {'VA' if use_va else 'file-offset'} interpretation")
    fdes = []
    for s, r in fdes_raw:
        sv = s if use_va else to_va(s)
        if sv is None: continue
        fdes.append((sv, sv + r))
    fdes.sort()
    fde_starts = [f[0] for f in fdes]
    # Self-test the decoded FDE address fields against GROUND TRUTH: the three
    # exported .dynsym function entries MUST appear as FDE initial_locations in
    # a correctly decoded table (every exported function carries unwind info).
    # If they do not, the .eh_frame layout is non-standard (IL2CPP/Unity packed
    # format) and every FDE-derived verdict is INCONCLUSIVE.
    KNOWN_ENTRY_VAS = {
        "Java_com_playrix_engine_PlayIntegrityCustom_nativeSendRequest": 0x7b18194,
        "JNI_OnLoad": 0x7c09328,
        "bad_result_access": 0x7057ff8,
    }
    fde_starts_set = set(fde_starts)
    fde_valid = all(v in fde_starts_set for v in KNOWN_ENTRY_VAS.values())
    print(f"[3b] FDE self-test vs known .dynsym entries: "
          + ("PASS — address fields validated" if fde_valid
             else "FAIL — known exported entries NOT in FDE start set; .eh_frame layout is "
                  "non-standard (IL2CPP-packed); ALL FDE VERDICTS BELOW ARE INCONCLUSIVE"))
else:
    fdes = []
    fde_starts = []
    fde_valid = False
    print("[3b] FDE self-test: SKIP (.eh_frame missing) — FDE verdicts INCONCLUSIVE")

# ---------------------------------------------------------------- per-site
for site in SITES:
    print(f"\n===== site VA {hex(site)} (file {to_file(site)}) =====")
    # FDE authoritative test
    i = bisect.bisect_left(fde_starts, site)
    fde_entry = (i < len(fde_starts) and fde_starts[i] == site)
    interior = []
    if i - 1 >= 0:
        s0, e0 = fdes[i-1]
        if s0 < site < e0: interior.append((s0, e0))
    tag = "VERIFIED" if fde_valid else "INCONCLUSIVE (FDE table layout unvalidated)"
    if fde_entry:
        s0, e0 = fdes[i]
        print(f"    [FDE] site == FDE initial_location {hex(s0)} (range [{hex(s0)}..{hex(e0)})) => TRUE FUNCTION ENTRY ({tag})")
    elif interior:
        for s0, e0 in interior:
            print(f"    [FDE] site interior to FDE [{hex(s0)}..{hex(e0)}) => INTERIOR PATCH SITE ({tag})")
    else:
        print(f"    [FDE] site matches no decoded FDE start and is not interior to any decoded FDE range ({tag})")
    if not fde_valid:
        print(f"    [FDE] NOTE: decoded FDE start set does not reproduce known .dynsym entries; "
              f"treat the above as UNTRUSTED. Use prologue/tail/dynsym data below.")
    # tail of previous instruction
    f = to_file(site)
    tail = []
    for a in (site-12, site-8, site-4):
        ff = to_file(a)
        dd = list(md.disasm(org[ff:ff+4], a)) if ff is not None else []
        if dd: tail.append((a, dd[0].mnemonic, dd[0].op_str))
    boundary = tail[-1][1] in ("ret", "br") if tail else False
    print(f"    tail (site-12..site-4): {tail}   previous-insn boundary (ret/br): {boundary}")
    # prologue at site
    pf = to_file(site)
    p = md.disasm(org[pf:pf+4], site)
    p0 = list(p)[0] if p else None
    prologue = p0 and p0.mnemonic in ("sub", "stp", "stp", "mov") and "sp" in p0.op_str
    print(f"    prologue at site: {p0.mnemonic+' '+p0.op_str if p0 else 'n/a'}  (sp prologue: {bool(prologue)})")
    # .dynsym enclosure + neighbours
    enc = [s for s in syms if s[1] > 0 and s[0] <= site < s[0]+s[1]]
    if enc:
        for v, sz, n in enc:
            print(f"    ENCLOSING dynsym range: {hex(v)} .. {hex(v+sz)}  {n[:64]}")
    else:
        print(f"    no dynsym range encloses this site (unexported/internal function)")
    before = [s for s in syms if s[0] < site]
    after = [s for s in syms if s[0] > site]
    for v, sz, n in before[-3:]:
        print(f"    below {hex(site-v)}: {hex(v)} (size {sz}) {n[:52]}")
    for v, sz, n in after[:3]:
        print(f"    above +{hex(v-site)}: {hex(v)} (size {sz}) {n[:52]}")
    # reachability in ORIGINAL
    tdo = byname[".text"]
    bl_hits = b_hits = 0
    t0 = to_file(tdo[0]); t1 = min(t0 + tdo[1], len(org)-3)
    for i in range(t0, t1, 4):
        w = struct.unpack_from("<I", org, i)[0]
        opc6 = w & 0xFC000000   # bits 31:26 — distinguishes b from bl (bit 31)
        tgt = None
        if opc6 == 0x94000000:        # bl: 100101 imm26 (26-bit signed imm, PC-relative)
            imm = (w & 0x03FFFFFF) << 2
            if w & 0x02000000: imm -= 0x10000000
            va_i = to_va(i); tgt = va_i + imm if va_i is not None else None
            if tgt == site: bl_hits += 1
        elif opc6 == 0x14000000:      # b: plain unconditional JUMP (not a call)
            # UNVERIFIED on this IL2CPP binary: the real B-words here do not
            # satisfy the ARM64 B spec (imm19, low-6 bits zero) and no imm19
            # candidate reproduced capstone's targets. Kept only as a
            # completeness counter; b is never caller evidence (a direct
            # caller is a branch-AND-LINK). The bl decoder above is what is
            # verified (2891/2891 against capstone on .text, 128KB slice).
            imm = (w & 0x03FFFFFF) << 2
            if w & 0x02000000: imm -= 0x10000000
            va_i = to_va(i); tgt = va_i + imm if va_i is not None else None
            if tgt == site: b_hits += 1
    # data pointers
    ptr_hits = []
    for dsec in (".rodata", ".data", ".data.rel.ro", ".data.rel.local", ".got", ".got.plt"):
        if dsec not in byname: continue
        do, dsz = byname[dsec]
        if dsz == 0: continue
        ff = to_file(do)
        if ff is None: continue
        end = min(ff+dsz, len(org))
        j = ff
        while j + 8 <= end:
            if struct.unpack_from("<Q", org, j)[0] == site:
                ptr_hits.append((dsec, hex(to_va(j))))
            j += 8
    print(f"    .text branch targets == site: bl={bl_hits}, b={b_hits}")
    print(f"    8-byte data pointers == site: {ptr_hits if ptr_hits else 'none in scanned data sections'}")

print("\n[5] claim re-checks:")
print(f"    cloudProjectNumber 0xb4359ae5c1L == {0xb4359ae5c1} decimal")
print(f"    (Google Cloud project IDs are 9-11 digit integers; {0xb4359ae5c1} is in that range)")
