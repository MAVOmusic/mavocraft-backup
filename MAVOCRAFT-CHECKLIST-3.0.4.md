# MAVOcraft 3.0.4 — CHECKLIST (OP + player side)

3.0.4 fixes ONE real bug found in the 3.0.3 boot log: **the boss-table upgrade never landed on
old configs**. The 3.0.3 code ran `copyDefaults` BEFORE the merge check, so the bundled
`bosses-version: 2` key was written into old configs first — the merge then thought the table was
already upgraded and left the OLD rich values (90k coins, 45% Mythic keys) in place while the
damage x1.5 still applied. 3.0.4: the merge now runs FIRST + table generation **3** re-forces the
real table on any config a 3.0.3 boot touched. All checks from `MAVOCRAFT-CHECKLIST-3.0.3.md` still
apply — this is the 3.0.4 delta (2 jars changed: MAVOGuide, MAVOMiniboss).

| # | OP | Player |
|---|---|---|
| 01 | Download the refreshed `MAVOcraft-backup.zip`; `sha256sum -c MAVOcraft-backup.sha256` → `OK`. | — (n/a) |
| 02 | Extract; verify NO file is read-only (all `-rw-rw-rw-`), incl. the 2 new jars. | — (n/a) |
| 03 | Stop server → delete ONLY `33_MAVO-Guide-3.0.3.jar` + `40_MAVO-Miniboss-3.0.3.jar` in `plugins/` (delete the 3.0.3 versions of these TWO, keep ALL folders/configs) → copy `33_MAVO-Guide-3.0.4.jar` + `40_MAVO-Miniboss-3.0.4.jar` from `3.0.0/plugins/` → start. Crafting stays 3.0.3. | — (n/a, server offline) |
| 04 | Console MUST show exactly ONCE:<br>`[MAVOMiniboss] 3.0.4: boss table replaced - hard hunts (HP x2, damage x1.5), coins/key drops scaled down to event-fair values.`<br>`[MAVOMiniboss] MAVOMiniboss v3.0.4 enabled - 15 boss type(s), 2 arena(s)…`<br>`[MAVOGuide] Guide config … -> v26 (new pages/notes delivered; player data kept).`<br>Crafting line stays `MAVOCrafting 3.0.3 … 7 custom recipe(s), 100 beginner recipe(s)`. | — (n/a yet) |
| 05 | `/plugins` → MAVOGuide 3.0.4, MAVOMiniboss 3.0.4, MAVOCrafting 3.0.3; all other MAVO 3.0.0/3.0.2. `sha256sum -c 3.0.0/SHA256SUMS` → 59 `OK`. | Join; `/pl` shows the same. |
| 06 | `/miniboss status` + `/miniboss reload` → 15 types, 2 arenas, no errors. | `/hunt` (30s cd) → boss fight **really** hard now: HP 1600–4800, attacks x1.5 (the old config could have the new multiplier WITHOUT the new HP/drops — the bug — it can't anymore). |
| 07 | Check `plugins/MAVOMiniboss/config.yml` = `bosses-version: 3`, `boss-damage-multiplier: 1.5`, e.g. `crimson_behemoth: hp 3200, coins 9000, lucky 1@30, mythic@11`; `nether_overlord: hp 4000, coins 15000, lucky 2@35, mythic@12`. NO boss may say 90k/150k coins or 45–70% keys. | — (n/a, file check) |
| 08 | Next boss spawn broadcast uses the NEW table, e.g.: `A CRIMSON BEHEMOTH … Drops: 100% 9,000 coins · 30% 1x Lucky Coin · 11% 1x Mythic Crate Key. HP 3200`. Kill check on an OP account: ≤15k coins, 1–2 Lucky Coins, 1 key at ≤25% (Mythic ≤12%). | Kill in a group → coins split line, small amounts; no "free 90k + key". |
| 09 | Restart once more → **NO** `boss table replaced` line again (gen 3 already saved), no `re-verified` repeat — boot stays clean. | — (n/a) |
| 10 | Known-fine lines (ignore, as before): Essentials "unsupported server version" (ERROR-level, third-party, works), ClaimChunk "Economy not enabled", CoreProtect "Development branch", TAB hint, BlueMap manual-save WARN, EconomyShopGUI "Failed to automatically find compatible spawner provider" (falls back, shops fine), MAVOSeasonal "0 season(s) defined" (= your current server config has no seasons — restore from `3.0.0/live-configs/MAVOSeasonal/config.yml` only if you want seasonal events, not a bug). Console "?" glyphs in MAVOTavern/MAVOPersonalVault lines = the ⛃ currency symbol not rendering in the panel console; in-game it shows fine. | — (n/a) |

**Rule reminder:** 41 MAVO @3.0.0, 2 @3.0.2 (ChestHunt, LuckyCoins), 1 @3.0.3 (Crafting),
2 @3.0.4 (Guide, Miniboss), 13 third-party real versions = 59. Any SHA mismatch → re-copy from step 03.
