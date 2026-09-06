# MAVOCRAFT block logo (creeper edition)

Matches the reference art: chunky cream cracked-stone letters, black
background, a creeper face built into both A's, and a dark 3D extrusion/
shadow toward the bottom-left.

## Files

- `fill-commands.txt` — paste-ready `/fill` commands (all coordinates are
  `~` relative). 388 commands, canvas **164 × 24 blocks**, 1 block thick.
- `MAVOCRAFT-logo-preview.png` — what it looks like from below.
- `gen_logo.py` — generator. Edit the palette/word/scale at the top and
  run `python3 gen_logo.py fill-commands.txt` to customise.

## Palette (edit in gen_logo.py)

- Letters: `bone_block` (cream, glows dimly). Alt: `cracked_stone_bricks`
  for real cracks (grayer), `smooth_quartz` for whiter.
- Background: `black_concrete`
- Extrusion/shadow: `deepslate` (offset −2 x, +2 z)

## How to build it (ceiling of the plaza)

1. Fly so your **feet are on the bottom plane of the ceiling**.
2. Stand at the **top-left corner** of where the logo goes and **face
   north** (top of the logo points north, letters read upright when you
   look up).
3. Paste the commands from `fill-commands.txt` into console.
4. Look up — the logo reads correctly from below.

The 3D shadow is a real block layer offset to the south-west (deepslate);
it looks like an extrusion when seen from underneath at an angle.
