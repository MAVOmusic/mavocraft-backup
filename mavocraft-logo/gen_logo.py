#!/usr/bin/env python3
"""MAVOCRAFT block logo v4 - ceiling build, absolute coordinates.

READING ORIENTATION (from your F3):
  You stand at spawn (-2579, 200, -1685), FACE WEST (-X) to look at the 3
  villagers, then look straight UP. In that view:
    screen-right = north (-Z)      screen-top = east (+X)
  So the word runs SOUTH -> NORTH and each letter's top points EAST. The
  3D shadow (bottom-left of the viewed image) = west + south (-2 x, +2 z).

Ceiling (Y=250): X -2629..-2529 (100), Z -1735..-1635 (100), center (-2579,-1685).
Logo is built ONE LAYER BELOW at Y=249 so the ceiling is never touched.

Files (absolute coords, no placement guessing):
  test-fill.txt      5 gold markers: 3x3 at the center + 4 canvas corners
  frame-fill.txt     gold border of the canvas
  fill-commands.txt  FULL logo (starts with an undo /fill air line)
"""

# ---------------- palette ----------------
BLOCK_BG     = "black_concrete"   # background
BLOCK_FRAME  = "gold_block"       # canvas border
BLOCK_LETTER = "bone_block"       # cream letters (alt: cracked_stone_bricks)
BLOCK_SHADOW = "deepslate"        # 3D shadow (bottom-left of the viewed image)

# ---------------- layout ----------------
SCALE = 2            # blocks per font pixel
GAP = 1              # blocks between letters
MARGIN = 3           # border + padding around the letters
SHADOW_DX, SHADOW_DZ = -2, 2      # bottom-left of the view = west + south

CX, CZ, CY = -2579, -1685, 249   # build center (below the ceiling)

# ---------------- font (X = filled; A carries the creeper face) ----------------
FONT = {
    "M": ["X.X", "XXX", "XXX", "X.X", "X.X", "X.X", "X.X", "X.X"],
    "A": [".XXX.", "X...X", "X.X.X", "X.X.X", "XXXXX", "XX.XX", "XXXXX", "X...X"],
    "V": ["X.X", "X.X", "X.X", "X.X", "X.X", ".X.", ".X.", ".X."],
    "O": ["XXX", "X.X", "X.X", "X.X", "X.X", "X.X", "X.X", "XXX"],
    "C": ["XXX", "X..", "X..", "X..", "X..", "X..", "X..", "XXX"],
    "R": ["XXX", "X.X", "X.X", "XXX", "XX.", "X.X", "X.X", "X.X"],
    "F": ["XXX", "X..", "X..", "XX.", "X..", "X..", "X..", "X.."],
    "T": ["XXX", ".X.", ".X.", ".X.", ".X.", ".X.", ".X.", ".X."],
}
WORD = "MAVOCRAFT"

# ---------------- geometry ----------------
# text width along Z (word start = south / max z), text height along X (top = east)
faceW = sum(2 * len(FONT[c][0]) for c in WORD) + GAP * (len(WORD) - 1)   # 66
faceH = len(FONT["M"]) * SCALE                                           # 16
CW = faceH + 2 * MARGIN                  # canvas size along X (24)
CH = faceW + 2 * MARGIN                  # canvas size along Z (74)
X0 = CX - CW // 2                        # west edge   (-2591)
Z0 = CZ - CH // 2                        # north edge  (-1722)
XE = X0 + CW - 1                         # east edge   (-2568)
ZS = Z0 + CH - 1                         # south edge  (-1649)
# letter top row = east side of the interior; word starts at the south side
xTop = XE - MARGIN                       # east edge of the interior
zStart = ZS - MARGIN                     # south edge of the interior

def face_blocks():
    """set of (x, z) blocks covered by letters."""
    out = set()
    cur = zStart
    for ch in WORD:
        g = FONT[ch]
        w = len(g[0]) * SCALE
        for ry, row in enumerate(g):
            x1 = xTop - ry * SCALE            # this row's 2 blocks: x1-1, x1
            for rx, v in enumerate(row):
                if v == "X":
                    for dx in range(SCALE):
                        for dz in range(SCALE):
                            out.add((x1 - dx, cur - rx * SCALE - dz))
        cur -= w + GAP
    return out

def shadow_blocks(face):
    out = set()
    for (x, z) in face:
        s = (x + SHADOW_DX, z + SHADOW_DZ)
        if s not in face and X0 <= s[0] <= XE and Z0 <= s[1] <= ZS:
            out.add(s)
    return out

def runs(blocks):
    """group into one /fill per horizontal run (same z, consecutive x)."""
    rows = {}
    for (x, z) in blocks:
        rows.setdefault(z, []).append(x)
    out = []
    for z in sorted(rows):
        xs = sorted(rows[z])
        i = 0
        while i < len(xs):
            j = i
            while j + 1 < len(xs) and xs[j + 1] == xs[j] + 1:
                j += 1
            out.append((xs[i], z, xs[j], z))
            i = j + 1
    return out

def f(x1, z1, x2, z2, mat):
    return "/fill %d %d %d %d %d %d minecraft:%s" % (x1, CY, z1, x2, CY, z2, mat)

def write_commands():
    face = face_blocks()
    shadow = shadow_blocks(face)
    # ---- test markers ----
    t = ["# MAVOCRAFT logo test markers (Y=%d) - 3x3 gold at the logo center + 4 canvas corners" % CY,
         "# Stand at spawn, FACE WEST (at the 3 villagers), look straight up to check.",
         "# Undo with: " + f(CX - 4, CZ - 4, CX + 4, CZ + 4, "air")]
    t.append(f(CX - 1, CZ - 1, CX + 1, CZ + 1, BLOCK_FRAME))
    for (cx0, cz0) in [(X0, Z0), (XE, Z0), (X0, ZS), (XE, ZS)]:
        t.append(f(cx0, cz0, cx0, cz0, BLOCK_FRAME))
    # ---- frame ----
    fr = ["# MAVOCRAFT logo FRAME (Y=%d) - gold border %dx%d, center (%d,%d)" % (CY, CW, CH, CX, CZ),
          "# Check alignment inside the ceiling, then run the full build."]
    fr.append(f(X0, Z0, XE, Z0, BLOCK_FRAME))
    fr.append(f(X0, ZS, XE, ZS, BLOCK_FRAME))
    fr.append(f(X0, Z0 + 1, X0, ZS - 1, BLOCK_FRAME))
    fr.append(f(XE, Z0 + 1, XE, ZS - 1, BLOCK_FRAME))
    # ---- full ----
    out = ["# MAVOCRAFT logo - FULL BUILD (Y=%d), canvas %dx%d centered (%d,%d)" % (CY, CW, CH, CX, CZ),
           "# Read it: stand at spawn, FACE WEST at the 3 villagers, look straight up.",
           "# Frame: %s / letters: %s / shadow: %s" % (BLOCK_FRAME, BLOCK_LETTER, BLOCK_SHADOW),
           "# Undo (ceiling untouched at Y=250):", f(X0, Z0, XE, ZS, "air"), ""]
    out.append(f(X0, Z0, XE, ZS, BLOCK_BG))
    out += fr[2:]
    for (a, b, c, d) in runs(face):
        out.append(f(a, b, c, d, BLOCK_LETTER))
    for (a, b, c, d) in runs(shadow):
        out.append(f(a, b, c, d, BLOCK_SHADOW))
    return t, fr, out, face, shadow

# ---------------- preview (EXACTLY what the player sees: left=south, top=east) ----------------
def write_png(path, w, h, pixels):
    import zlib, struct
    def chunk(ty, d):
        c = ty + d
        return struct.pack(">I", len(d)) + c + struct.pack(">I", zlib.crc32(c) & 0xffffffff)
    raw = b"".join(b"\x00" + bytes(pixels[y * w * 3:(y + 1) * w * 3]) for y in range(h))
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as fh:
        fh.write(png)

def preview(path, face, shadow):
    px = 10
    W, H = CH * px, CW * px     # image x = z (south..north), image y = x (east..west)
    col = {"b": (13, 13, 15), "f": (246, 214, 76), "l": (236, 230, 218), "s": (40, 40, 60)}
    pix = [0] * (W * H * 3)
    def setb(ix, iy, c):
        r, g, b = col[c]
        for yy in range(iy * px, (iy + 1) * px):
            for xx in range(ix * px, (ix + 1) * px):
                i = (yy * W + xx) * 3
                pix[i], pix[i + 1], pix[i + 2] = r, g, b
    for iz in range(CH):
        for ix in range(CW):
            c = "b"
            if iz == 0 or iz == CH - 1 or ix == 0 or ix == CW - 1:
                c = "f"
            setb(iz, ix, c)
    for (x, z) in shadow:
        setb(ZS - z, XE - x, "s")
    for (x, z) in face:
        setb(ZS - z, XE - x, "l")
    write_png(path, W, H, pix)

if __name__ == "__main__":
    t, fr, out, face, shadow = write_commands()
    open("test-fill.txt", "w").write("\n".join(t) + "\n")
    open("frame-fill.txt", "w").write("\n".join(fr) + "\n")
    open("fill-commands.txt", "w").write("\n".join(out) + "\n")
    preview("MAVOCRAFT-logo-preview.png", face, shadow)
    print("canvas %dx%d | X %d..%d  Z %d..%d | letters %d | shadow %d"
          % (CW, CH, X0, XE, Z0, ZS, len(face), len(shadow)))
    print("test %d / frame %d / full %d commands" % (len(t), len(fr), len(out)))
    # safety asserts: canvas must stay well inside the 100x100 ceiling
    assert X0 >= -2629 and XE <= -2529 and Z0 >= -1735 and ZS <= -1635, "canvas exceeds ceiling"
    assert CW < 75 and CH <= 76, "canvas exceeds ~75% of the ceiling"
    print("bounds OK: fits inside the ceiling with margin")
