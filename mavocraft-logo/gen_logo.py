#!/usr/bin/env python3
"""MAVOCRAFT block logo generator - matches the reference art.

Reference look:
  - chunky cream "cracked stone" letters (bone_block = cream; swap to
    cracked_stone_bricks for real cracks)
  - black concrete background, NO frame
  - a CREEPER FACE built into both A's (black cutout)
  - 3D extrusion: a dark deepslate copy of every letter, offset to the
    bottom-left (world -x / +z) so the logo reads 3D from below

All /fill coordinates are RELATIVE (~) to the player, so:
  1. Fly so your FEET are exactly on the bottom plane of the plaza ceiling.
  2. Stand at the TOP-LEFT corner of where the logo goes, FACING NORTH.
  3. Paste fill-commands.txt. Look up: logo reads correctly from below.
"""

import sys, zlib, struct

# ---------------- palette (change these lines to re-tint) ----------------
BLOCK_BG      = "black_concrete"     # background
BLOCK_LETTER  = "bone_block"         # cream stone letters (alt: cracked_stone_bricks)
BLOCK_SHADOW  = "deepslate"          # 3D extrusion / shadow

# ---------------- layout ----------------
SCALE = 2                 # blocks per font pixel (2 = chunky)
GAP_PX = 2                # gap between letters
SHADOW_DX = -2            # extrusion offset in blocks (world x; -2 = west/left)
SHADOW_DZ = 2             # extrusion offset in blocks (world z; +2 = south/down)
WORD = "MAVOCRAFT"

# ---------------- 7 x 9 chunky font (X = letter, . = empty) ----------------
# The two A's carry the creeper face (same glyph; reference shows it in both).
FONT = {
    "M": [
        "X.....X",
        "XX...XX",
        "X.X.X.X",
        "X..X..X",
        "X.....X",
        "X.....X",
        "X.....X",
        "X.....X",
        "X.....X",
    ],
    "A": [
        "..XXX..",
        ".XXXXX.",
        "XX...XX",
        "X..X..X",   # creeper eyes (cut)
        "X..X..X",   # creeper eyes (cut)
        "XXXXXXX",   # mouth top
        "XX.X.XX",   # mouth teeth (cut)
        "XXXXXXX",   # mouth bottom
        "XX...XX",
    ],
    "V": [
        "X.....X",
        "X.....X",
        "X.....X",
        "X.....X",
        ".X...X.",
        ".X...X.",
        "..X.X..",
        "..X.X..",
        "...X...",
    ],
    "O": [
        ".XXXXX.",
        "XX...XX",
        "XX...XX",
        "XX...XX",
        "XX...XX",
        "XX...XX",
        "XX...XX",
        "XX...XX",
        ".XXXXX.",
    ],
    "C": [
        ".XXXXX.",
        "XX.....",
        "XX.....",
        "XX.....",
        "XX.....",
        "XX.....",
        "XX.....",
        "XX.....",
        ".XXXXX.",
    ],
    "R": [
        "XXXXX..",
        "XX...XX",
        "XX...XX",
        "XX...XX",
        "XXXXX..",
        "XX.XX..",
        "XX..XX.",
        "XX...XX",
        "XX...XX",
    ],
    "F": [
        "XXXXXXX",
        "XX.....",
        "XX.....",
        "XX.....",
        "XXXXX..",
        "XX.....",
        "XX.....",
        "XX.....",
        "XX.....",
    ],
    "T": [
        "XXXXXXX",
        "...X...",
        "...X...",
        "...X...",
        "...X...",
        "...X...",
        "...X...",
        "...X...",
        "...X...",
    ],
}

# ---------------- pixel grid ----------------
def pixel_grid():
    w = len(WORD) * 7 + (len(WORD) - 1) * GAP_PX
    h = 9
    grid = [[None] * w for _ in range(h)]
    x = 0
    for ch in WORD:
        glyph = FONT[ch]
        for ry in range(9):
            for rx in range(7):
                if glyph[ry][rx] == "X":
                    grid[ry][x + rx] = ch
        x += 7 + GAP_PX
    return w, h, grid

# ---------------- commands ----------------
def commands():
    wpx, hpx, grid = pixel_grid()
    margin = 2                       # black border all round (blocks)
    faceW = wpx * SCALE
    faceH = hpx * SCALE
    x0, z0 = margin, margin
    # canvas must also cover the shadow overhang
    cw = faceW + 2 * margin + max(0, SHADOW_DX) - min(0, SHADOW_DX)
    ch_ = faceH + 2 * margin + max(0, SHADOW_DZ) - min(0, SHADOW_DZ)
    out = ["# MAVOCRAFT logo - creeper edition, %dx%d blocks (1 thick)" % (cw, ch_),
           "# Stand at the TOP-LEFT corner (facing NORTH) at ceiling height, then paste.",
           "# Letters=%s  background=%s  extrusion/shadow=%s (offset %+d,%+d)"
           % (BLOCK_LETTER, BLOCK_BG, BLOCK_SHADOW, SHADOW_DX, SHADOW_DZ),
           ""]
    # background
    out.append("/fill ~0 ~0 ~0 ~%d ~0 ~%d minecraft:%s" % (cw - 1, ch_ - 1, BLOCK_BG))
    # face pixels
    face = set()
    for ry in range(hpx):
        z0row = z0 + ry * SCALE
        rx = 0
        while rx < wpx:
            if grid[ry][rx] is None:
                rx += 1
                continue
            start = rx
            while rx < wpx and grid[ry][rx] is not None:
                rx += 1
            xa = x0 + start * SCALE
            xb = x0 + (rx - 1) * SCALE + SCALE - 1
            za = z0row
            zb = z0row + SCALE - 1
            out.append("/fill ~%d ~0 ~%d ~%d ~0 ~%d minecraft:%s"
                       % (xa, za, xb, zb, BLOCK_LETTER))
            for gx in range(xa, xb + 1):
                for gz in range(za, zb + 1):
                    face.add((gx, gz))
    # shadow pixels (offset copy, only where no face pixel sits)
    shadow = set()
    for (gx, gz) in face:
        s = (gx + SHADOW_DX, gz + SHADOW_DZ)
        if s not in face and 0 <= s[0] < cw and 0 <= s[1] < ch_:
            shadow.add(s)
    # group shadow runs per row
    rows = {}
    for (gx, gz) in shadow:
        rows.setdefault(gz, []).append(gx)
    for gz in sorted(rows):
        xs = sorted(rows[gz])
        i = 0
        while i < len(xs):
            j = i
            while j + 1 < len(xs) and xs[j + 1] == xs[j] + 1:
                j += 1
            out.append("/fill ~%d ~0 ~%d ~%d ~0 ~%d minecraft:%s"
                       % (xs[i], gz, xs[j], gz, BLOCK_SHADOW))
            i = j + 1
    return out, cw, ch_, face, shadow

# ---------------- PNG preview ----------------
def write_png(path, w, h, pixels):
    def chunk(t, d):
        c = t + d
        return struct.pack(">I", len(d)) + c + struct.pack(">I", zlib.crc32(c) & 0xffffffff)
    raw = b"".join(b"\x00" + bytes(pixels[y * w * 3:(y + 1) * w * 3]) for y in range(h))
    png = (b"\x89PNG\r\n\x1a\n"
           + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9))
           + chunk(b"IEND", b""))
    with open(path, "wb") as f:
        f.write(png)

def preview(path, cw, ch_, face, shadow):
    px = 8
    W, H = cw * px, ch_ * px
    col = {"b": (13, 13, 15), "l": (236, 230, 218), "s": (40, 40, 46)}
    pix = [0] * (W * H * 3)
    def setb(x, y, c):
        r, g, b = col[c]
        for yy in range(y * px, (y + 1) * px):
            for xx in range(x * px, (x + 1) * px):
                i = (yy * W + xx) * 3
                pix[i], pix[i + 1], pix[i + 2] = r, g, b
    for (x, y) in face:
        setb(x, y, "l")
    for (x, y) in shadow:
        setb(x, y, "s")
    write_png(path, W, H, pix)

if __name__ == "__main__":
    cmds, cw, ch_, face, shadow = commands()
    with open(sys.argv[1] if len(sys.argv) > 1 else "fill-commands.txt", "w") as f:
        f.write("\n".join(cmds) + "\n")
    preview("MAVOCRAFT-logo-preview.png", cw, ch_, face, shadow)
    print("canvas %dx%d blocks | %d letter blocks | %d shadow blocks | %d commands"
          % (cw, ch_, len(face), len(shadow), len(cmds)))
