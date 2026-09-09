# MAVOcraft 3.0.3 — CHECKLIST (OP + player side)

3.0.3 fixes two playtest findings: **boss hunts were an economy exploit** (90k coins + 45% Mythic key per kill)
and **/craft showed wrong recipes** (white bed = orange bed + white dye!). Every item has an **OP** and a
**Player** check. All 74 checks from `MAVOCRAFT-CHECKLIST-3.0.2.md` still apply — this file is the 3.0.3 delta.

| # | OP | Player |
|---|---|---|
| 01 | Download the refreshed `MAVOcraft-backup.zip`; `sha256sum -c MAVOcraft-backup.sha256` → `OK`. | — (n/a) |
| 02 | Extract; verify NO file is read-only (all `-rw-rw-rw-`). | — (n/a) |
| 03 | Stop server → delete ONLY `.jar` files in `plugins/` (keep all folders/configs) → copy all 59 jars from `3.0.0/plugins/` → start. | — (n/a, server offline) |
| 04 | Console MUST show:<br>`[MAVOCrafting] 3.0.3: beginner recipes re-verified - 100 real vanilla basics (correct amounts + 3x3 grid).`<br>`[MAVOCrafting] MAVOCrafting v3.0.3 enabled - 7 custom recipe(s), 100 beginner recipe(s).`<br>`[MAVOMiniboss] 3.0.3: boss table replaced - hard hunts (HP x2, damage x1.5), coins/key drops scaled down to event-fair values.`<br>`[MAVOMiniboss] MAVOMiniboss v3.0.3 enabled - 15 boss type(s), 2 arena(s)…`<br>`[MAVOGuide] Guide config v24 -> v25 (new pages/notes delivered; player data kept).` | — (n/a yet) |
| 05 | `/plugins` → MAVOCrafting 3.0.3, MAVOMiniboss 3.0.3, MAVOGuide 3.0.3; all other MAVO 3.0.0/3.0.2. `sha256sum -c 3.0.0/SHA256SUMS` → 59 `OK`. | Join; `/pl` shows the same. |
| 06 | Console, next boss spawn — the drop line matches the NEW table, e.g.:<br>`A CRIMSON BEHEMOTH has appeared … Drops: 100% 9,000 coins · 30% 1x Lucky Coin · 11% 1x Mythic Crate Key. HP 3200`<br>No boss may still say 50k–150k coins or 45–70% keys. | — (n/a — watch chat) |
| 07 | `/miniboss status` + `/miniboss reload` → 15 types, 2 arenas, no errors. | `/hunt` (30s cd) → drops you ~200 blocks from the boss; **fight feels hard** — boss hits noticeably harder and survives much longer; shield/enchants actually matter. |
| 08 | Kill check: `/hunt` a boss with an OP account → coins + LC + key awarded are small (≤15k coins market values, 1 key). | Kill a boss in a group → coins split line, 1–2 Lucky Coins at ~30%, 1 key at ~10–25% (Mythic rare) — no more "free 90k + key". |
| 09 | `/crafting reload` → no warnings; config.yml shows `recipes-version: 4`. | `/craft` → **Bed** shows: 3x **White Wool** top row + 3x **Oak Planks** below (NO orange bed/dye). Click it → same grid in the 3×3 preview + it unlocks in your recipe book. |
| 10 | Check `plugins/MAVOCrafting/config.yml` = bundled (version 4 list, 100 entries with `layout:` lines). | `/craft` spot-check the previously-wrong ones: **Oak Fence Gate** = 4 sticks + 2 planks; **Glass Bottle** = 3 glass → 3 bottles; **Glowstone** = 4 dust → 1 block. |
| 11 | Spot-check output counts in `/craft` lore: Cookies ×8, Glass Panes ×16, Oak Door ×3, Ladder ×3, Stone Slab ×6, Sticks ×4, Torches ×4, Planks ×4, Arrow ×4. | Click those recipes → preview shows the right count next to the result. |
| 12 | Console: no `[ERROR]`, no "bad result/ingredient skipped" from MAVOCrafting; no miniboss stack traces. | Play normally 5 min → nothing behaves differently except bosses harder + guide correct. |
| 13 | Known-fine lines (ignore, as before): Essentials "unsupported server version", ClaimChunk "Economy not enabled", CoreProtect "Development branch", TAB hint, Professions Sleeper note, BlueMap manual-save. | — (n/a) |
| 14 | Restart once more → **NO** "re-verified"/"boss table replaced" log again (versions already 4/2 on disk) — boot stays clean. | — (n/a) |
| 15 | Confirm `plugins/MAVOMiniboss/config.yml` = `bosses-version: 2`, `boss-damage-multiplier: 1.5`, new HP/coins per boss. | — (n/a, file check) |

**Rule reminder:** 3 jars at 3.0.3 (Crafting/Guide/Miniboss), 2 at 3.0.2 (ChestHunt/LuckyCoins), 41 MAVO at 3.0.0, 13 third-party real versions = 59. Any SHA mismatch → re-copy from step 03.
