#!/usr/bin/env python3
"""MAVOCRAFT block logo generator - datapack edition.

Builds TWO designs at Y=249 under the plaza ceiling (Y=250, center -2579,-1685):

  MAVOcraft-75-datapack  (frozen v4): 22 x 76 canvas (~75%), bone_block letters,
      deepslate 3D shadow, gold frame.
  MAVOcraft-95-datapack  (NEW):       24 x 95 canvas (~95%), sea_lantern letters
      (glowing), the two A's in kick-lime lime_concrete, 1-block GLOWSTONE border
      around EVERY letter (no shadow), gold frame, black background.

Each datapack = world/datapacks/<name>.zip with functions:
  /function mavocraft75:logo_test   (or mavocraft95:logo_test)
  /function mavocraft75:logo_frame
  /function mavocraft75:logo_build
  /function mavocraft75:logo_clear

Viewing (from your F3): stand at spawn (-2579,200,-1685), FACE WEST (-X) at the
3 villagers, then look straight UP. In that view:
    screen-right = SOUTH (+Z)      screen-top = WEST (-X)
  MAVOcraft-75 (frozen v4): as-built with the word running south->north and
      letter tops pointing EAST, so it shows 180 degrees rotated from spawn.
      DESIGN IS FROZEN - do not change it.
  MAVOcraft-95 (v5): built with the word running north->south and letter tops
      pointing WEST (flip=True) so it reads correctly from spawn, left -> right.
/tab scoreboard toggle hides the sidebar while you check the build.

Also writes plain fill-commands.txt per variant (paste fallback) + preview PNGs.
"""

import os, struct, sys, zlib

CX, CZ, CY = -2579, -1685, 249
CEIL = (-2629, -2529, -1735, -1635)   # x1,x2,z1,z2 at Y=250

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
SCALE = 2

VARIANT = {
    "75": dict(
        ns="mavocraft75", name="MAVOcraft-75-datapack", pct=75,
        gap=1, margin_e=3, margin_w=3, margin_s=3, margin_n=3,
        letter="bone_block", letter_a="bone_block", border=None,
        shadow=True, shadow_dx=-2, shadow_dz=2,
        frame="gold_block", bg="black_concrete",
        desc="frozen v4 - 22x76, bone letters + deepslate shadow"),
    "95": dict(
        ns="mavocraft95", name="MAVOcraft-95-datapack", pct=95,
        gap=3, margin_e=4, margin_w=4, margin_s=5, margin_n=4,
        letter="sea_lantern", letter_a="lime_concrete", border="glowstone",
        shadow=False, shadow_dx=0, shadow_dz=0, flip=True,
        frame="gold_block", bg="black_concrete",
        desc="v5 - 24x95, glowing sea_lantern letters, lime A's, glowstone letter border"),
}

def geometry(cfg):
    face_px = sum(len(FONT[c][0]) for c in WORD)
    faceW = face_px * SCALE + cfg["gap"] * (len(WORD) - 1)          # along Z
    faceH = len(FONT["M"]) * SCALE                                   # along X
    CW = faceH + cfg["margin_e"] + cfg["margin_w"]                   # along X
    CH = faceW + cfg["margin_s"] + cfg["margin_n"]                   # along Z
    X0 = CX - CW // 2
    Z0 = CZ - CH // 2
    XE, ZS = X0 + CW - 1, Z0 + CH - 1
    if cfg.get("flip"):
        # v95 (v5): letter tops point WEST, word runs north -> south (reads
        # correctly from spawn facing west / looking up). Same block footprint
        # as the un-flipped layout (margins preserved).
        xTop = X0 + cfg["margin_w"]
        zStart = Z0 + cfg["margin_n"]
    else:
        # v75 (frozen v4): letter tops point EAST, word runs south -> north.
        xTop = XE - cfg["margin_e"]
        zStart = ZS - cfg["margin_s"]
    return X0, XE, Z0, ZS, CW, CH, xTop, zStart

def _stroke(cfg, ch):
    """Blocks of one glyph (ch) or of every letter (ch=None), honoring the
    variant orientation so face_blocks and the A-recolour can never diverge."""
    X0, XE, Z0, ZS, CW, CH, xTop, zStart = geometry(cfg)
    flip = cfg.get("flip", False)
    out = set()
    cur = zStart
    for c in WORD:
        g = FONT[c]
        w = len(g[0]) * SCALE
        if ch is None or c == ch:
            for ry, row in enumerate(g):
                x1 = xTop + ry * SCALE if flip else xTop - ry * SCALE
                for rx, v in enumerate(row):
                    if v == "X":
                        for dx in range(SCALE):
                            for dz in range(SCALE):
                                x = x1 + dx if flip else x1 - dx
                                z = cur + rx * SCALE + dz if flip else cur - rx * SCALE - dz
                                out.add((x, z))
        cur = cur + w + cfg["gap"] if flip else cur - w - cfg["gap"]
    return out

def face_blocks(cfg):
    return _stroke(cfg, None)

def border_blocks(cfg, face):
    X0, XE, Z0, ZS, CW, CH, _, _ = geometry(cfg)
    out = set()
    for (x, z) in face:
        for dx in (-1, 0, 1):
            for dz in (-1, 0, 1):
                n = (x + dx, z + dz)
                if n not in face and X0 <= n[0] <= XE and Z0 <= n[1] <= ZS:
                    out.add(n)
    return out

def shadow_blocks(cfg, face):
    if not cfg["shadow"]:
        return set()
    X0, XE, Z0, ZS, CW, CH, _, _ = geometry(cfg)
    out = set()
    for (x, z) in face:
        s = (x + cfg["shadow_dx"], z + cfg["shadow_dz"])
        if s not in face and X0 <= s[0] <= XE and Z0 <= s[1] <= ZS:
            out.add(s)
    return out

def runs(blocks):
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

def fill(x1, z1, x2, z2, mat):
    return "/fill %d %d %d %d %d %d minecraft:%s" % (x1, CY, z1, x2, CY, z2, mat)

def fn(cmd):
    c = cmd[1:] if cmd.startswith("/") else cmd
    return "execute in minecraft:overworld run " + c

def gen_texts(cfg):
    face = face_blocks(cfg)
    bor = border_blocks(cfg, face)
    shad = shadow_blocks(cfg, face)
    X0, XE, Z0, ZS, CW, CH, _, _ = geometry(cfg)
    test = [fill(CX - 1, CZ - 1, CX + 1, CZ + 1, cfg["frame"])]
    for p in [(X0, Z0), (XE, Z0), (X0, ZS), (XE, ZS)]:
        test.append(fill(p[0], p[1], p[0], p[1], cfg["frame"]))
    frame = [fill(X0, Z0, XE, Z0, cfg["frame"]), fill(X0, ZS, XE, ZS, cfg["frame"]),
             fill(X0, Z0 + 1, X0, ZS - 1, cfg["frame"]), fill(XE, Z0 + 1, XE, ZS - 1, cfg["frame"])]
    build = [fill(X0, Z0, XE, ZS, "air"), fill(X0, Z0, XE, ZS, cfg["bg"])] + frame
    if cfg["border"]:
        for (a, b, c, d) in runs(bor):
            build.append(fill(a, b, c, d, cfg["border"]))
    for (a, b, c, d) in runs(shad):
        build.append(fill(a, b, c, d, "deepslate"))
    for (a, b, c, d) in runs(face):
        build.append(fill(a, b, c, d, cfg["letter"]))
    # recolor A's after all letters
    for (a, b, c, d) in runs(face_letter(cfg, "A")):
        build.append(fill(a, b, c, d, cfg["letter_a"]))
    return test, frame, build, face, bor, shad

def face_letter(cfg, ch):
    return _stroke(cfg, ch)

def write_mcfunction(path, cmds, header):
    with open(path, "w") as f:
        f.write("# " + header + "\n")
        for c in cmds:
            f.write(fn(c) + "\n")

def write_datapack(cfg, test, frame, build):
    root = os.path.join("datapacks-src", cfg["name"])
    data = os.path.join(root, "data", cfg["ns"], "function")
    os.makedirs(data, exist_ok=True)
    with open(os.path.join(root, "pack.mcmeta"), "w") as f:
        f.write('{\n  "pack": {\n    "min_format": 82,\n'
                '    "max_format": 107,\n'
                '    "description": "%s - MAVOCRAFT block logo (%d%% of ceiling, Y=%d), '
                'functions: logo_test / logo_frame / logo_build / logo_clear / logo_info"\n'
                '  }\n}\n' % (cfg["desc"], cfg["pct"], CY))
    write_mcfunction(os.path.join(data, "logo_test.mcfunction"), test,
                     "Test markers: 3x3 gold at the logo center + 4 canvas corners (Y=%d)" % CY)
    write_mcfunction(os.path.join(data, "logo_frame.mcfunction"), frame,
                     "Gold border of the %dx%d canvas (Y=%d)" % (*geometry(cfg)[4:6], CY))
    write_mcfunction(os.path.join(data, "logo_build.mcfunction"), build,
                     "FULL build: clear + background + frame + letter borders + letters")
    write_mcfunction(os.path.join(data, "logo_clear.mcfunction"),
                     [fill(X0, Z0, XE, ZS, "air") for (X0, XE, Z0, ZS, _, _, _, _) in [geometry(cfg)]],
                     "Remove the whole logo canvas (Y=%d only)" % CY)
    with open(os.path.join(data, "logo_info.mcfunction"), "w") as f:
        f.write("# /function %s:logo_info\n" % cfg["ns"])
        f.write('tellraw @a {"text":"[MAVOCRAFT logo] Stand at spawn, FACE WEST at the 3 villagers, look UP. Run logo_test, logo_frame, logo_build.","color":"gold"}\n')
        f.write('tellraw @a {"text":"TIP: /tab scoreboard toggle hides the sidebar so the ceiling is clear (TAB plugin).","color":"gray"}\n')

def zipdir(cfg):
    src = os.path.join("datapacks-src", cfg["name"])
    out = os.path.join("datapacks", cfg["name"] + ".zip")
    os.makedirs("datapacks", exist_ok=True)
    if os.path.exists(out):
        os.remove(out)
    entries = []
    for base, _, files in os.walk(src):
        for fn_ in files:
            p = os.path.join(base, fn_)
            entries.append((p, os.path.relpath(p, src)))
    import zipfile
    with zipfile.ZipFile(out, "w", zipfile.ZIP_DEFLATED) as z:
        for p, rel in entries:
            z.write(p, rel)
    return out

# ---------------- preview ----------------
def write_png(path, w, h, pixels):
    def chunk(ty, d):
        c = ty + d
        return struct.pack(">I", len(d)) + c + struct.pack(">I", zlib.crc32(c) & 0xffffffff)
    raw = b"".join(b"\x00" + bytes(pixels[y * w * 3:(y + 1) * w * 3]) for y in range(h))
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", w, h, 8, 2, 0, 0, 0))
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    with open(path, "wb") as fh:
        fh.write(png)

def preview(path, cfg, face, bor, shad, X0, XE, Z0, ZS, CW, CH):
    px = 10
    W, H = CH * px, CW * px
    col = {"b": (13, 13, 15), "f": (246, 214, 76), "bo": (250, 205, 110),
           "l": (188, 232, 224), "a": (120, 200, 40), "s": (40, 40, 60)}
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
    # True in-game view from spawn (stand at center, face west, look up):
    # screen-right = south (+Z), screen-top = west (-X).
    for (x, z) in shad:
        setb(z - Z0, x - X0, "s")
    if cfg["border"]:
        for (x, z) in bor:
            setb(z - Z0, x - X0, "bo")
    for (x, z) in face:
        setb(z - Z0, x - X0, "l")
    for (x, z) in face_letter(cfg, "A"):
        setb(z - Z0, x - X0, "a")
    write_png(path, W, H, pix)

def main():
    for key, cfg in VARIANT.items():
        test, frame, build, face, bor, shad = gen_texts(cfg)
        X0, XE, Z0, ZS, CW, CH, _, _ = geometry(cfg)
        # plain-text fallbacks
        d = os.path.join("v" + key)
        os.makedirs(d, exist_ok=True)
        with open(os.path.join(d, "test-fill.txt"), "w") as f:
            f.write("# %s test markers Y=%d\n" % (cfg["name"], CY) + "\n".join(test) + "\n")
        with open(os.path.join(d, "frame-fill.txt"), "w") as f:
            f.write("# %s frame Y=%d (canvas %dx%d)\n" % (cfg["name"], CY, CW, CH) + "\n".join(frame) + "\n")
        with open(os.path.join(d, "fill-commands.txt"), "w") as f:
            f.write("# %s FULL build Y=%d - undo: %s\n\n"
                    % (cfg["name"], CY, fill(X0, Z0, XE, ZS, "air")) + "\n".join(build) + "\n")
        preview(os.path.join(d, "MAVOCRAFT-logo-preview.png"), cfg, face, bor, shad,
                X0, XE, Z0, ZS, CW, CH)
        z = write_datapack(cfg, test, frame, build)
        zipdir(cfg)
        z = os.path.join("datapacks", cfg["name"] + ".zip")
        print("%s: canvas %dx%d | X %d..%d | Z %d..%d | %d letter / %d border / %d shadow blocks -> %s"
              % (cfg["name"], CW, CH, X0, XE, Z0, ZS, len(face), len(bor), len(shad), z))
        # safety
        assert CEIL[0] <= X0 and XE <= CEIL[1] and CEIL[2] <= Z0 and ZS <= CEIL[3], "exceeds ceiling"
        assert CW <= 95 and CH <= 95, "canvas over 95%"

if __name__ == "__main__":
    main()
