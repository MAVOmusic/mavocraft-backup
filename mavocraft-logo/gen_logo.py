#!/usr/bin/env python3
"""MAVOCRAFT block logo generator.

Draws "MAVOCRAFT" with a classic 5x7 pixel font (scaled 2x -> 2x2 blocks per
pixel) and emits ready-to-paste Minecraft /fill commands. All coordinates are
RELATIVE (~) to the player, so:

    1. Stand at the TOP-LEFT corner of where the logo should go (the block at
       the ceiling you want to be the top-left of the logo), looking NORTH.
    2. Paste the commands in mavocraft-logo/fill-commands.txt.
    3. Look up: the logo reads correctly when you stand under it facing north.

Layout (blocks):
    canvas 126 wide x 18 deep, 1 block thick at your eye-height Y when you
    stand under it. Frame = GOLD_BLOCK, letters = SEA_LANTERN (they glow),
    background = BLACK_CONCRETE.
"""
import sys, zlib, struct

# 5x7 pixel font
FONT = {
    "M": ["#...#", "##..#", "#.#.#", "#..##", "#...#", "#...#", "#...#"],
    "A": [".###.", "#...#", "#...#", "#####", "#...#", "#...#", "#...#"],
    "V": ["#...#", "#...#", "#...#", "#...#", "#...#", ".#.#.", "..#.."],
    "O": [".###.", "#...#", "#...#", "#...#", "#...#", "#...#", ".###."],
    "C": [".####", "#....", "#....", "#....", "#....", "#....", ".####"],
    "R": ["####.", "#...#", "#...#", "####.", "#.#..", "#..#.", "#...#"],
    "F": ["#####", "#....", "#....", "####.", "#....", "#....", "#...."],
    "T": ["#####", "..#..", "..#..", "..#..", "..#..", "..#..", "..#.."],
}

SCALE = 2          # blocks per font pixel (2 -> each pixel is 2x2 blocks)
GAP_PX = 2         # pixel gap between letters
NUM_LETTERS = 9    # M A V O C R A F T

BBG = "black_concrete"   # background
BLTR = "sea_lantern"     # letter fill (glows)
BFRAME = "gold_block"    # 2-block frame

def pixel_grid():
    """Return (w_px,h_px,grid) where grid[y][x] = letter char or None."""
    word = "MAVOCRAFT"
    w = NUM_LETTERS * 5 + (NUM_LETTERS - 1) * GAP_PX
    h = 7
    grid = [[None] * w for _ in range(h)]
    x = 0
    for ch in word:
        glyph = FONT[ch]
        for ry in range(7):
            for rx in range(5):
                if glyph[ry][rx] == "#":
                    grid[ry][x + rx] = ch
        x += 5 + GAP_PX
    return w, h, grid

def commands():
    out = []
    wpx, hpx, grid = pixel_grid()
    # canvas: 1px border (2 blocks) around everything
    cw = wpx * SCALE + 2 * SCALE          # full canvas width in blocks
    ch_ = hpx * SCALE + 2 * SCALE         # full canvas depth in blocks
    out.append(f"# MAVOCRAFT logo - {cw} x {ch_} blocks (1 thick) at your Y")
    out.append(f"# Stand at the TOP-LEFT corner (looking NORTH) then paste everything below.")
    out.append(f"# Palette: background=black_concrete  letters=sea_lantern  frame=gold_block")
    out.append("")
    out.append(f"/fill ~0 ~0 ~0 ~{cw - 1} ~0 ~{ch_ - 1} minecraft:{BBG}")
    # frame: two top rows, two bottom rows, two left cols, two right cols
    out.append(f"/fill ~0 ~0 ~0 ~{cw - 1} ~0 ~1 minecraft:{BFRAME}")
    out.append(f"/fill ~0 ~0 ~{ch_ - 2} ~{cw - 1} ~0 ~{ch_ - 1} minecraft:{BFRAME}")
    out.append(f"/fill ~0 ~0 ~0 ~1 ~0 ~{ch_ - 1} minecraft:{BFRAME}")
    out.append(f"/fill ~{cw - 2} ~0 ~0 ~{cw - 1} ~0 ~{ch_ - 1} minecraft:{BFRAME}")
    # letters: per 2-block row, per contiguous run of set pixels -> one fill
    for ry in range(hpx):
        z0 = SCALE + ry * SCALE
        z1 = z0 + SCALE - 1
        rx = 0
        while rx < wpx:
            if grid[ry][rx] is None:
                rx += 1
                continue
            start = rx
            while rx < wpx and grid[ry][rx] is not None:
                rx += 1
            x0 = SCALE + start * SCALE
            x1 = SCALE + (rx - 1) * SCALE + SCALE - 1
            out.append(f"/fill ~{x0} ~0 ~{z0} ~{x1} ~0 ~{z1} minecraft:{BLTR}")
            # two-pixel letters are 2 blocks tall -> next row band handled separately
    return out, cw, ch_

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

def preview(path, cw, ch_, grid, wpx, hpx):
    px = 8  # pixels per block in the preview
    W, H = cw * px, ch_ * px
    col = {"b": (29, 29, 33), "l": (207, 232, 234), "f": (246, 214, 76)}
    pix = [0] * (W * H * 3)
    def setb(x, y, c):
        r, g, b = col[c]
        for yy in range(y * px, (y + 1) * px):
            for xx in range(x * px, (x + 1) * px):
                i = (yy * W + xx) * 3
                pix[i], pix[i + 1], pix[i + 2] = r, g, b
    for y in range(ch_):
        for x in range(cw):
            c = "b"
            if x < 2 or x >= cw - 2 or y < 2 or y >= ch_ - 2:
                c = "f"
            else:
                gx = (x - 2) // SCALE
                gy = (y - 2) // SCALE
                if gy < hpx and gx < wpx and grid[gy][gx] is not None:
                    c = "l"
            setb(x, y, c)
    write_png(path, W, H, pix)

if __name__ == "__main__":
    cmds, cw, ch_ = commands()
    with open(sys.argv[1] if len(sys.argv) > 1 else "fill-commands.txt", "w") as f:
        f.write("\n".join(cmds) + "\n")
    wpx, hpx, grid = pixel_grid()
    preview("MAVOCRAFT-logo-preview.png", cw, ch_, grid, wpx, hpx)
    lit = sum(1 for row in grid for c in row if c is not None)
    print(f"canvas {cw}x{ch_} blocks, {lit*SCALE*SCALE} letter blocks, {len(cmds)} commands")
