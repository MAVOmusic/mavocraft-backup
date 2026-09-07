#!/usr/bin/env python3
"""MAVOCRAFT block logo v3 - ceiling build, absolute coordinates.

Ceiling (Y=250): X -2629..-2529 (100), Z -1735..-1635 (100), center (-2579,-1685).
Logo is built ONE LAYER BELOW at Y=249 so the ceiling is never touched.

Everything here is ABSOLUTE (no ~, no placement guessing):
  - test-fill.txt  5 gold marker blocks (center + 4 corners) - check alignment
  - frame-fill.txt gold border of the logo canvas (75 x 23) - check size/position
  - fill-commands.txt full logo (black bg + gold frame + cream letters + deepslate
    3D shadow toward the bottom-left)

Orientation: a player standing under it FACING NORTH sees the letters upright
(looking up flips the view: screen-top = south, screen-right = east).
"""

# ---------------- palette ----------------
BLOCK_BG     = "black_concrete"   # background
BLOCK_FRAME  = "gold_block"       # canvas border
BLOCK_LETTER = "bone_block"       # cream letters (alt: cracked_stone_bricks)
BLOCK_SHADOW = "deepslate"        # 3D shadow (bottom-left of the view)

# ---------------- layout ----------------
SCALE = 2            # blocks per font pixel
GAP = 1              # blocks between letters
LM, RM = 2, 3        # canvas margins west/east (shadow needs 2 on the west)
TM, BM = 3, 4        # canvas margins south/north (screen top = south)
SHADOW_DX, SHADOW_DZ = -2, -2    # bottom-left of the looking-up view (west+north)

CX, CZ, CY = -2579, -1685, 249   # build center (below the ceiling)

# ---------------- font (X = filled; widths vary; A carries the creeper face) ----------------
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
# canvas (from the player's view): screen-left = west, screen-top = south
faceW = sum(2 * len(FONT[c][0]) for c in WORD) + GAP * (len(WORD) - 1)   # 70
faceH = len(FONT["M"]) * SCALE                                           # 16
CW = LM + faceW + RM                                                     # 75
CH = TM + faceH + BM                                                     # 23
X0, Z0 = CX - CW // 2, CZ - CH // 2      # canvas min corner (north-west)
# letter grid, row 0 = letter TOP = south (max z)
faceZTop = Z0 + CH - TM                  # largest z covered by row 0
faceX = X0 + LM

def face_blocks():
    """set of (x, z) blocks covered by letters, absolute coords."""
    out = set()
    cur = faceX
    for ch in WORD:
        g = FONT[ch]
        w = len(g[0]) * SCALE
        for ry, row in enumerate(g):
            z1 = faceZTop - ry * SCALE
            for rx, v in enumerate(row):
                if v == "X":
                    for dx in range(SCALE):
                        for dz in range(SCALE):
                            out.add((cur + rx * SCALE + dx, z1 - dz))
        cur += w + GAP
    return out

def shadow_blocks(face):
    out = set()
    for (x, z) in face:
        s = (x + SHADOW_DX, z + SHADOW_DZ)
        if s not in face and X0 <= s[0] < X0 + CW and Z0 <= s[1] < Z0 + CH:
            out.add(s)
    return out

def runs(blocks, axis):
    """group blocks into runs along x for each z (or along z for each x)."""
    rows = {}
    for (a, b) in blocks:
        rows.setdefault(b, []).append(a)
    out = []
    for b in sorted(rows):
        xs = sorted(rows[b])
        i = 0
        while i < len(xs):
            j = i
            while j + 1 < len(xs) and xs[j + 1] == xs[j] + 1:
                j += 1
            out.append((xs[i], b, xs[j], b))
            i = j + 1
    return out

def f(x1, z1, x2, z2, mat):
    return "/fill %d %d %d %d %d %d minecraft:%s" % (x1, CY, z1, x2, CY, z2, mat)

def write_commands():
    face = face_blocks()
    shadow = shadow_blocks(face)
    x1, z1 = X0, Z0
    x2, z2 = X0 + CW - 1, Z0 + CH - 1
    # ---- test markers ----
    t = ["# MAVOCRAFT logo test markers (Y=%d) - 3x3 gold at the logo center + 4 corner blocks" % CY,
         "# They sit directly above spawn. Look up and check, then undo with /fill ... air."]
    t.append(f(CX - 1, CZ - 1, CX + 1, CZ + 1, BLOCK_FRAME))
    for (cx0, cz0) in [(x1, z1), (x2, z1), (x1, z2), (x2, z2)]:
        t.append(f(cx0, cz0, cx0, cz0, BLOCK_FRAME))
    # ---- frame ----
    fr = ["# MAVOCRAFT logo FRAME (Y=%d) - gold border %dx%d, center (%d,%d)" % (CY, CW, CH, CX, CZ),
          "# Check it lines up inside the ceiling, then run the full build."]
    fr.append(f(x1, z1, x2, z1, BLOCK_FRAME))
    fr.append(f(x1, z2, x2, z2, BLOCK_FRAME))
    fr.append(f(x1, z1 + 1, x1, z2 - 1, BLOCK_FRAME))
    fr.append(f(x2, z1 + 1, x2, z2 - 1, BLOCK_FRAME))
    # ---- full ----
    out = ["# MAVOCRAFT logo - FULL BUILD (Y=%d), canvas %dx%d centered (%d,%d)" % (CY, CW, CH, CX, CZ),
           "# %s%s%s frame: %s / letters: %s / shadow: %s" % ("", "", "",
               BLOCK_FRAME, BLOCK_LETTER, BLOCK_SHADOW),
           "# Undo (ceiling is untouched at Y=250):", f(x1, z1, x2, z2, "air"), ""]
    out.append(f(x1, z1, x2, z2, BLOCK_BG))
    out += fr[2:]
    for (a, b, c, d) in runs(face, 0):
        out.append(f(a, b, c, d, BLOCK_LETTER))
    for (a, b, c, d) in runs(shadow, 0):
        out.append(f(a, b, c, d, BLOCK_SHADOW))
    return t, fr, out, face, shadow

# ---------------- preview (what the player sees: top = south, left = west) ----------------
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
    W, H = CW * px, CH * px
    col = {"b": (13, 13, 15), "f": (246, 214, 76), "l": (236, 230, 218), "s": (40, 40, 60)}
    pix = [0] * (W * H * 3)
    def setb(x, y, c):
        r, g, b = col[c]
        for yy in range(y * px, (y + 1) * px):
            for xx in range(x * px, (x + 1) * px):
                i = (yy * W + xx) * 3
                pix[i], pix[i + 1], pix[i + 2] = r, g, b
    # imgY: canvas z max (south, screen top) at y=0
    for i in range(CW):
        for j in range(CH):
            c = "b"
            if i == 0 or j == 0 or i == CW - 1 or j == CH - 1:
                c = "f"
            setb(i, j, c)
    for (x, z) in shadow:
        setb(x - X0, Z0 + CH - 1 - z, "s")
    for (x, z) in face:
        setb(x - X0, Z0 + CH - 1 - z, "l")
    write_png(path, W, H, pix)

if __name__ == "__main__":
    t, fr, out, face, shadow = write_commands()
    open("test-fill.txt", "w").write("\n".join(t) + "\n")
    open("frame-fill.txt", "w").write("\n".join(fr) + "\n")
    open("fill-commands.txt", "w").write("\n".join(out) + "\n")
    preview("MAVOCRAFT-logo-preview.png", face, shadow)
    print("canvas %dx%d at Y%d center (%d,%d) | face %dx%d | %d letter + %d shadow blocks"
          % (CW, CH, CY, CX, CZ, faceW, faceH, len(face), len(shadow)))
    print("test %d / frame %d / full %d commands" % (len(t), len(fr), len(out)))
