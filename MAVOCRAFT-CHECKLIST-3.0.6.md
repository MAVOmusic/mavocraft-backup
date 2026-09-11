# MAVOcraft 3.0.6 — CHECKLIST (OP + player side)

3.0.6 is the **live-test fix round** (OP checklist run 2026-09-10): 6 jars changed.
**/craft** gets a Customs browser (the 7 customs finally open from the GUI) + `/craft <name>`
jumps straight to the recipe (fuzzy) + sorted tab-complete incl. customs; **pets** have AI
(wander + look alive, still untouchable) + `/pet off` / Rest button; **boss raid** can never
leave stuck bossbars (cleanup at start + self-heal); **/event start** refuses while one runs;
**/crate inspect** explains offline + matches names case-insensitively. Guide v28 documents it.
All checks from the 3.0.3/3.0.4/3.0.5 checklists + `CHECKLIST-OP.txt` + `CHECKLIST-PLAYER-A/B.txt`
still apply — this is the 3.0.6 delta. (Also in this round's docs: 5 live-checklist rows
corrected — `lp info`, bluemap status-only, `almost`-fill, pvault self-only `setpacks`.)

| # | OP | Player |
|---|---|---|
| 01 | Download the refreshed `MAVOcraft-backup.zip`; `sha256sum -c MAVOcraft-backup.sha256` → `OK`. | — (n/a) |
| 02 | Extract; verify NO file is read-only (all `-rw-rw-rw-`), incl. the 6 new jars. | — (n/a) |
| 03 | Stop server → delete the 6 OLD jars in `plugins/` (Events, Crates, BossRaid, Crafting-3.0.3, Pets, Guide-3.0.5 — keep ALL folders/configs) → copy the 6 `*-3.0.6.jar` from `3.0.0/plugins/` → start. | — (n/a, server offline) |
| 04 | Console MUST show:<br>`[MAVOGuide] Guide config v27 -> v28 (new pages/notes delivered; player data kept).`<br>All 6 plugins log `v3.0.6 enabled`. NO repeats on 2nd boot. | — (n/a yet) |
| 05 | `/plugins` → the 6 at 3.0.6; 12 still at 3.0.5; ChestHunt + LuckyCoins 3.0.2; rest MAVO 3.0.0. `sha256sum -c 3.0.0/SHA256SUMS` → 59 `OK`. | Join; `/pl` shows the same. Guide v28 auto-popup opens once. |
| 06 | `/craft` → "Custom recipes (7)" button on the bottom row → opens customs list → click saddle → exact 3x3 (leather/iron) + Back to customs. | Same: browse customs, craft a saddle at a table. |
| 07 | `/craft barrel` → opens the barrel recipe DIRECTLY (not the page). `/craft sad` → saddle. `/craft xyz` → "No recipe matches". TAB after `/craft ` → sorted list incl. saddle/bed. | Same on your client (Java + Bedrock). |
| 08 | `/pet give <player> wolf` (or use your own) → pet wanders/idles nearby (alive!), hit it → no damage; right-click it → menu has Rest button → Rest → gone; `/pets` → click it → back. | `/pet off` → gone; `/pet xp` → "No active pet"; re-pick in `/pets`. |
| 09 | `/event start coinrain` then `/event start mobhunt` → "already running - /event stop it first". `/event stop` → ends + announces. | — (n/a) |
| 10 | `/boss now` → fight → `/boss cancel` → bar gone everywhere (incl. relog check). Start again → single bar, live HP numbers. | Raid normally; no stuck bars after end/cancel/timeout. |
| 11 | `/crate inspect <onlineplayer>` (try wrong case too) → GUI opens. `/crate inspect <offlineplayer>` → "is offline - inspect needs them online". | — (n/a) |
| 12 | Restart once more → NO `v27 -> v28` repeat; pets respawn active; customs still 7. | — (n/a) |
| 13 | Known-fine (ignore, as before): Essentials version ERROR, Paper behind, CoreProtect dev branch, TAB hint, BlueMap save WARN, EconomyShopGUI spawner INFO, Seasonal `0 season(s)` (restore snapshot if wanted), Tavern/PVault `?` glyphs. | — (n/a) |

**Rule reminder:** 26 MAVO @3.0.0, 2 @3.0.2 (ChestHunt, LuckyCoins),
12 @3.0.5 (Miniboss, ChestShops, Mail, Locks, Couples, Guilds, AuctionHouse, Duels,
FishComp, Enchants, Curator, Warps), 6 @3.0.6 (Events, Crates, BossRaid, Crafting, Pets,
Guide), 13 third-party real versions = 59. Any SHA mismatch → re-copy from step 03.
