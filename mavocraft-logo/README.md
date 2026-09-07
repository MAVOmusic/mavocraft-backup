# MAVOCRAFT block logo — datapack edition

Two designs (build one; both read from the same spot). Each datapack adds
four functions run from the server console:

| Command | What it does |
|---|---|
| `/function mavocraft75:logo_test` (or `mavocraft95:`) | 3×3 gold center + 4 canvas corners |
| `/function ...:logo_frame` | gold border of the canvas |
| `/function ...:logo_build` | full build (clears first) |
| `/function ...:logo_clear` | remove the whole logo |
| `/function ...:logo_info` | reminder of where to stand |

## Viewing position

Stand at spawn **(-2579, 200, -1685)**, **face WEST** at the 3 villagers,
**look straight up** → you see `MAVOCRAFT` upright. Word runs south→north,
letter tops point east. A's are lime green (kick logo style).

## Design A — `MAVOcraft-75-datapack` (current, unchanged)

- Canvas **22 × 76** (~75% of ceiling): X −2590..−2569, Z −1723..−1648
- `bone_block` letters · **deepslate 3D shadow** (bottom-left) · gold frame
- Y = 249 (ceiling at 250 untouched)

## Design B — `MAVOcraft-95-datapack` (new)

- Canvas **24 × 95** (~95% of ceiling): X −2591..−2568, Z −1732..−1638
- `sea_lantern` letters (**glowing**, brighter) · **lime `lime_concrete` A's**
- **`glowstone` border around every letter** (no shadow) · gold frame
- Y = 249

## Install on the server

1. Put BOTH zips (or the one you want) into `world/datapacks/` (next to the
   MobFarm datapacks).
2. `/datapack list` → `/datapack enable "file/MAVOcraft-95-datapack"` (and the
   75 one if uploaded).
3. Run `/function mavocraft95:logo_test` (or 75) from **console**.
4. `/function mavocraft95:logo_frame`, then `/function mavocraft95:logo_build`.

**TIP:** `/tab scoreboard toggle` hides the TAB sidebar so the ceiling isn't
covered while you check (TAB plugin command).

**Undo:** `/function mavocraftXX:logo_clear` (Y=249 only, ceiling untouched).

## Files

- `datapacks/MAVOcraft-75-datapack.zip` + `MAVOcraft-95-datapack.zip`
- `datapacks-src/<name>/` — sources (pack.mcmeta pack_format 48 +
  supported_formats 48..999, same convention as MobFarm)
- `v75/` + `v95/` — plain `fill-commands.txt` / `test-fill.txt` / `frame-fill.txt`
  fallbacks + preview PNG
