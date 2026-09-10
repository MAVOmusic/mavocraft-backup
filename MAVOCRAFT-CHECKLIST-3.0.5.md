# MAVOcraft 3.0.5 — CHECKLIST (OP + player side)

3.0.5 is the **exploit fix round** (audit `MAVOCRAFT-SECURITY-AUDIT.md`): 13 jars changed.
**Shop buyers actually receive items** (was: paid, got nothing) with exact-NBT stock matching;
**boss schedule** (1 per side, fresh 10:00 both sides, vanish 7:00, slain side rests till next
10:00, spawns only while online; `/miniboss broadcast` never spawns + 60s cd); **mail rejects**
when the target has 100 unclaimed (was: deleted oldest); **locks** block hoppers + pistons;
**/couple home, /couple tp, /guild home** get 5s warmup + 12-block monster check; **AH**
says explicitly when a full inbox destroys an item + escrow can never vanish; duels refund,
fishcomp pending, withdraw checks in gemshop/extras/warps. Guide v27 documents it all.
All checks from the 3.0.3/3.0.4 checklists + `CHECKLIST-OP.txt` + `CHECKLIST-PLAYER-A/B.txt`
still apply — this is the 3.0.5 delta.

| # | OP | Player |
|---|---|---|
| 01 | Download the refreshed `MAVOcraft-backup.zip`; `sha256sum -c MAVOcraft-backup.sha256` → `OK`. | — (n/a) |
| 02 | Extract; verify NO file is read-only (all `-rw-rw-rw-`), incl. the 13 new jars. | — (n/a) |
| 03 | Stop server → delete the 13 OLD jars in `plugins/` (ChestShops, Miniboss, Mail, Locks, Couples, Guilds, AuctionHouse, Duels, FishComp, Enchants, Curator, Warps, Guide — keep ALL folders/configs) → copy the 13 `*-3.0.5.jar` from `3.0.0/plugins/` → start. | — (n/a, server offline) |
| 04 | Console MUST show:<br>`[MAVOMiniboss] MAVOMiniboss v3.0.5 enabled - 15 boss type(s), 2 arena(s), schedule 10:00 spawn / 7:00 despawn, 1 per side…`<br>`[MAVOGuide] Guide config v26 -> v27 (new pages/notes delivered; player data kept).`<br>NO `boss table replaced` repeat. All 13 plugins log `v3.0.5 enabled`. | — (n/a yet) |
| 05 | `/plugins` → the 13 at 3.0.5; ChestHunt + LuckyCoins 3.0.2; Crafting 3.0.3; rest MAVO 3.0.0. `sha256sum -c 3.0.0/SHA256SUMS` → 59 `OK`. | Join; `/pl` shows the same. Guide v27 auto-popup opens once. |
| 06 | `/miniboss status` → per-side state lines (`HUNTABLE NOW` / `slain - back next 10:00` / `spawns 10:00`). Set time to 09:55 (`/time set 3500`), wait for 10:00 → BOTH sides spawn within ~1 min (only if someone online). | `/hunt`, fight, kill one side's boss → that side shows `slain - back next 10:00`; other side still hunting. |
| 07 | Set time to 06:55 (`/time set 500`), wait for 7:00 → `The minibosses have vanished with the dawn.` + all bosses gone, no loot. Set time to 10:00 again → both sides respawn. Restore normal time after. | Watch the vanish; confirm no drops/credit; re-hunt at 10:00. |
| 08 | `/miniboss broadcast` as OP with NO boss up → `No miniboss alive right now - fresh hunts spawn at 10:00` (NOTHING spawns). Repeat within 60s → cooldown message. | Same as non-OP: broadcast works, never spawns, 60s cooldown. |
| 09 | Stock a test shop (plain + 1 enchanted item of same material). | Buy 1 → coins taken AND **item received** (this was the critical bug). Enchanted stock is NOT counted/sold at base price. Info-item clicks don't buy. |
| 10 | Fill a test player's mailbox to 100 (OP: send junk), then send 1 more with item. | 101st send → `has 100 unclaimed mails - they must claim some first. Nothing was taken.` Sender keeps coins + item. |
| 11 | (With player) hopper under a locked chest; piston vs locked block. | Hopper pulls NOTHING; piston doesn't move it. Normal lock/trust/unlock still fine. |
| 12 | Watch: `/couple home`, `/couple tp`, `/guild home` with monsters <12 blocks. | `Monsters nearby - can't teleport`; with none: `Teleporting in 5s - stand still`; moving cancels. |
| 13 | Fill /inbox to 100, cancel a listing (junk item!). | `Cancelled - but your /inbox is FULL, so the item was DESTROYED…` (intended: hoarder's fault). Normal cancel/expire/buy/bid flows unchanged. |
| 14 | Restart once more → NO `v26 -> v27` repeat; schedule state persists (slain side stays slain till next 10:00; kill-day + despawn-day live in config `schedule-state`). | — (n/a) |
| 15 | Known-fine (ignore, as before): Essentials version ERROR, Paper behind, CoreProtect dev branch, TAB hint, BlueMap save WARN, EconomyShopGUI spawner INFO, Seasonal `0 season(s)` (restore snapshot if wanted), Tavern/PVault `?` glyphs. | — (n/a) |

**Rule reminder:** 30 MAVO @3.0.0, 2 @3.0.2 (ChestHunt, LuckyCoins), 1 @3.0.3 (Crafting),
13 @3.0.5 (Miniboss, Guide, ChestShops, Mail, Locks, Couples, Guilds, AuctionHouse, Duels,
FishComp, Enchants, Curator, Warps), 13 third-party real versions = 59. Any SHA mismatch → re-copy from step 03.
