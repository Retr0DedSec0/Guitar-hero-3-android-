#!/usr/bin/env python3
"""Gera uma ISO XDVDFS sintética (sem conteúdo do jogo) para testar o extrator.
Uso: make_test_iso.py <saida.iso> <offset_da_particao> [--lower]
Escreve <saida.iso>.expected com "caminho tamanho sha256" do que deve ser extraído."""
import hashlib, random, struct, sys

SECTOR = 2048
out_path, base = sys.argv[1], int(sys.argv[2], 0)
lower = "--lower" in sys.argv

def blob(seed, n):
    return random.Random(seed).randbytes(n)

D = "data" if lower else "DATA"
S = "scripts" if lower else "SCRIPTS"
files = {  # caminho na ISO -> bytes
    "default.xex": blob(1, 5000),
    "other.bin": blob(2, 3000),
    f"{D}/MUSIC/song1.fsb": blob(3, 3_500_000),   # cruza vários blocos de 1 MiB
    f"{D}/MUSIC/song2.fsb": blob(4, 100),
    f"{D}/MOVIES/BIK/intro.bik.xen": blob(5, 70000),
    f"{D}/MOVIES/BIK/loading_flying.bik.xen": b"",   # arquivo vazio
    f"{D}/COMPRESSED/a.pak": blob(6, 2049),
    f"{D}/{S}/s.txt": blob(7, 10),
    f"{D}/_originais-antes-da-traducao/x.pak": blob(8, 999),  # deve ser ignorado
    f"{D}/MODS/m.bin": blob(9, 10),                           # deve ser ignorado
    "$SystemUpdate/su.bin": blob(10, 10),                      # deve ser ignorado
}
empty_dirs = [f"{D}/ANIMS"]

# --- árvore de diretórios ---
tree = {}
def node(path_parts):
    t = tree
    for p in path_parts:
        t = t.setdefault(p, {})
    return t
for p in files: 
    parts = p.split("/")
    node(parts[:-1])[parts[-1]] = None   # None = arquivo
for p in empty_dirs:
    node(p.split("/"))

next_sector = 33
placed = {}  # id(arquivo) -> (setor, tamanho)

def alloc(nbytes):
    global next_sector
    s = next_sector
    next_sector += max(1, -(-nbytes // SECTOR))
    return s

img = {}  # setor -> bytes (escrita esparsa)
def put(sector, data):
    img[sector] = data

def build_table(entries):
    """entries: lista de (nome, attr, setor, tamanho). Árvore binária balanceada, raiz no offset 0."""
    entries = sorted(entries, key=lambda e: e[0].lower())
    order, links = [], {}
    def rec(lo, hi):
        if lo > hi: return None
        mid = (lo + hi) // 2
        order.append(mid)
        l = rec(lo, mid - 1); r = rec(mid + 1, hi)
        links[mid] = (l, r)
        return mid
    rec(0, len(entries) - 1)
    offs, cur = {}, 0
    for i in order:
        offs[i] = cur
        nm = len(entries[i][0].encode("latin-1"))
        cur += (14 + nm + 3) // 4 * 4
    buf = bytearray(b"\xff" * (-(-cur // SECTOR) * SECTOR))
    for i in order:
        name, attr, sec, size = entries[i]
        l, r = links[i]
        nb = name.encode("latin-1")
        rec_ = struct.pack("<HHIIBB", 0 if l is None else offs[l] // 4, 0 if r is None else offs[r] // 4, sec, size, attr, len(nb)) + nb
        buf[offs[i]:offs[i] + len(rec_)] = rec_
    return bytes(buf)

def emit_dir(t, path):
    entries = []
    for name, child in t.items():
        full = path + [name]
        if child is None:
            data = files["/".join(full)]
            sec = alloc(len(data)) if data else 0
            if data: put(sec, data)
            entries.append((name, 0x20, sec, len(data)))
        elif child == {}:
            entries.append((name, 0x10, 0, 0))
        else:
            sec, size = emit_dir(child, full)
            entries.append((name, 0x10, sec, size))
    table = build_table(entries)
    sec = alloc(len(table)); put(sec, table)
    return sec, len(table)

root_sec, root_size = emit_dir(tree, [])

vd = bytearray(SECTOR)
magic = b"MICROSOFT*XBOX*MEDIA"
vd[0:20] = magic
vd[20:28] = struct.pack("<II", root_sec, root_size)
vd[0x7EC:0x7EC + 20] = magic
put(32, bytes(vd))

total = base + next_sector * SECTOR
with open(out_path, "wb") as f:
    f.truncate(total)
    for sec, data in img.items():
        f.seek(base + sec * SECTOR); f.write(data)

# --- esperado ---
def dest(p):
    parts = p.split("/")
    if len(parts) == 1: return p if p == "default.xex" else None
    if parts[0].lower() != "data" or len(parts) < 3: return None
    ok = {"COMPRESSED","MUSIC","MOVIES","STREAMS","FXFILES","ANIMS","MEMCARD","SCRIPTS"}
    if parts[1].upper() not in ok: return None
    return "/".join(["DATA", parts[1].upper()] + parts[2:])
with open(out_path + ".expected", "w") as f:
    for p in sorted(files):
        d = dest(p)
        if d: f.write(f"{d} {len(files[p])} {hashlib.sha256(files[p]).hexdigest()}\n")
print("ok", out_path, "tamanho", total)
