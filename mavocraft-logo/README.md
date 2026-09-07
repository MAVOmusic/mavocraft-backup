# MAVOCRAFT block logo v3 — ceiling build (absolute coordinates)

Build layer: **Y = 249** (one block BELOW the ceiling at Y=250, so the
ceiling is never touched). Center: **(-2579, 249, -1685)** — exactly above
spawn. Canvas: **75 × 23 blocks = 75% of the 100×100 ceiling**, margins
13+ blocks on every side. No standing/orientation guessing — every command
is absolute.

## Files (all in this folder)

| File | Contents |
|---|---|
| `test-fill.txt` | small test: 3×3 gold center marker + 4 corner blocks |
| `frame-fill.txt` | gold border of the logo canvas (75×23) |
| `fill-commands.txt` | FULL logo (starts with an undo /fill air line) |
| `MAVOCRAFT-logo-preview.png` | what it looks like from below |
| `gen_logo.py` | generator (palette / letters / size) |

## Steps

1. **Test** — run `test-fill.txt` (5 gold markers above spawn). Look up:
   center 3×3 + 4 corners. Wrong = and nothing else was placed.
2. **Frame** — run `frame-fill.txt` (gold border). Check it fits nicely
   inside the ceiling with even margins.
3. **Full build** — run `fill-commands.txt`.
4. **Undo anytime** — `/fill -2616 249 -1696 -2542 249 -1674 minecraft:air`
   (Y=249 only; ceiling at Y=250 untouched).

## Orientation

Standing under it facing north and looking up: screen-top = south,
screen-left = west. Letters are laid out so they read upright in that
view; the deepslate shadow is offset west+north = bottom-left of the view,
matching the reference art.
