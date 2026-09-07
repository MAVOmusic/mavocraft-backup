# MAVOCRAFT block logo v4 — ceiling build, READ IT FROM SPAWN

**Viewing position:** stand at spawn **(-2579, 200, -1685)**, **face WEST**
(toward the 3 villagers), look **straight up** → you see `MAVOCRAFT` upright,
creeper faces in the A's, shadow at the bottom-left.

**Why this orientation:** from F3, facing west, looking up → screen-right =
north, screen-top = east. The word runs **south → north**, letters' tops
point **east**. (v3 had it running east→west = for a north-facing viewer;
rotated 90° in v4.)

**Build layer:** Y = 249 (one block BELOW the ceiling at Y=250 — ceiling never
touched). Center **(-2579, -1685)**. Canvas **22 × 76** (22 wide west→east,
76 long north→south) = ~75% of the 100×100 ceiling, inside
X -2590..-2569 / Z -1723..-1648 with 39+ block margins.

## Files (in this folder)

| File | Contents |
|---|---|
| `test-fill.txt` | 3×3 gold center + 4 corner blocks (Y=249) |
| `frame-fill.txt` | gold border of the canvas (22×76) |
| `fill-commands.txt` | FULL logo (undo `/fill air` line first) |
| `MAVOCRAFT-logo-preview.png` | exactly what you see looking up (left=south, top=east) |
| `gen_logo.py` | generator |

## Steps

1. **Test** — run `test-fill.txt`. From spawn (face west at the villagers,
   look up): 3×3 gold in the middle + 4 corners in a rectangle.
2. **Frame** — run `frame-fill.txt`. Check the border fits nicely inside the
   ceiling with even margins.
3. **Full** — run `fill-commands.txt`.

**Undo anytime** (Y=249 only, ceiling untouched):
`/fill -2590 249 -1723 -2569 249 -1648 minecraft:air`
