# MAVOcraft 3.0.2 — COMPLETE CHECKLIST (OP + player side)

Every item is numbered 01 → 99 in order. **Do them in order.** For each item there is an
**OP** column (console / admin command, what you type and what you must see) and a
**Player** column (in-game action + expected result). Where a check has no player part
(admin-only or server offline), it says `— (n/a …)` — that is intentional, not skipped.

**This round = the “old configs heal themselves” fix.** 4 jars move to 3.0.2:
`18_MAVO-ChestHunt`, `24_MAVO-Crafting`, `33_MAVO-Guide`, `38_MAVO-LuckyCoins`.
All other 42 MAVO jars stay 3.0.0; the 13 third-party jars keep their real versions.

---

## PART 1 — DEPLOY THE PACK (01–12)

| # | OP | Player |
|---|---|---|
| 01 | Download `MAVOcraft-backup.zip` from the GitHub repo. Verify it matches `MAVOcraft-backup.sha256` (same folder): `sha256sum -c MAVOcraft-backup.sha256` → `OK`. | — (server not involved yet) |
| 02 | Extract the zip anywhere. Confirm **no file is read-only**: right-click a jar → Properties → "Read-only" must be UNticked for ALL entries (or `find . -type f ! -perm -0666` returns nothing). | — (extraction only) |
| 03 | **Stop the server** (PebbleHost panel → Stop; console shows “Done”). Do NOT use `/reload`. | — (n/a, server stopping) |
| 04 | Open `plugins/` → **delete ONLY the .jar files** (59 of them). Keep every plugin folder, `config.yml`, `data.yml`, LuckPerms data, EconomyShopGUI `shops/`, Essentials userdata, BlueMap configs — nothing else. | — (n/a, server stopped) |
| 05 | Copy **all 59 jars** from `3.0.0/plugins/` into `plugins/` (numbers control load order: 01–13 third-party first, 14–59 MAVO alphabetical). | — (n/a, server stopped) |
| 06 | **Start** the server. Wait for `Done (…s)!` — expect ~19 s. | — (join after step 07 passes) |
| 07 | Console, MAVO* lines that MUST appear (4 new):<br>`[MAVOChestHunt] 3.0.2: repaired 1 legacy item name(s) in config.yml: EXP_BOTTLE -> EXPERIENCE_BOTTLE`<br>`[MAVOChestHunt] MAVOChestHunt v3.0.2 enabled - … pool 20 item type(s).`<br>`[MAVOCrafting] 3.0.2: repaired 2 legacy material name(s) in config.yml: EMPTY_MAP->MAP TERRA_COTTA->TERRACOTTA`<br>`[MAVOCrafting] MAVOCrafting v3.0.2 enabled - 7 custom recipe(s), 100 beginner recipe(s).`<br>`[MAVOLuckyCoins] Wishing well pool loaded: 1268 sellable items (1 skipped)`<br>`[MAVOLuckyCoins] MAVOLuckyCoins v3.0.2 enabled …`<br>`[MAVOGuide] Guide config v23 -> v24 (new pages/notes delivered; player data kept).`<br>**No new WARN/ERROR from any MAVO plugin.** | — (n/a yet — join after step 08) |
| 08 | Console: `[PluginInitializerManager] Initialized 59 plugins` + **46 MAVO lines all say v3.0.0 or v3.0.2** (list shows: ChestHunt 3.0.2, Crafting 3.0.2, Guide 3.0.2, LuckyCoins 3.0.2, other 42 MAVO 3.0.0). | Join the server — you can log in. |
| 09 | OP: `/plugins` (or `/pl`) → 59 plugins, no red/error entries. Every MAVO plugin shows its version (42 × 3.0.0, 4 × 3.0.2). | Player: `/pl` works for you too; server must not lag on join. |
| 10 | OP, from the pack folder: `sha256sum -c 3.0.0/SHA256SUMS` → **59 lines, all `: OK`**. | — (n/a, admin check) |
| 11 | Console: the following lines are **KNOWN & FINE — do not “fix” them**: `[Essentials] You are running an unsupported server version!` (third-party, still fully works); `[ClaimChunk] Economy not enabled.` (intentional config); `[CoreProtect] Development branch detected…` (community edition); `[TAB] [Hint] Refresh interval…` (cosmetic); `[MAVOProfessions] 3.15.3: Sleeper profession already present…` (one-time migration note); BlueMap `A manual (plugin-induced) save…` (expected at first BlueMap start). | — (n/a) |
| 12 | Console: `[MAVOPortalRoom] Spawned 26 jump-portal holograms.` · `[MAVOTavern] Spawned 4 bed holos.` · `[BlueMap] WebServer started` port 8156 · `[Geyser-Spigot] Started Geyser on UDP port 19132`. | — (n/a yet — players + bedrock test in Part 2) |

---

## PART 2 — EVERY PLUGIN, ONE CHECK EACH (13–71)

| # | Plugin | OP | Player |
|---|---|---|---|
| 13 | MAVOGuide | `/updates reload` → “reloaded”; no errors. | `/whatsnew` → **v24** entry at top: “old configs heal themselves!” — v23, v22 below. `/tutorial` opens CH0–CH16. `/updates` opens the menu. Guide auto-popup appears ONCE for the new version. |
| 14 | MAVOChestHunt (3.0.2 fix) | `/chesthunt spawn` → console announces coords; `/chesthunt reload` → pool still 20, no warn. | `/chesthunt` → distance ping; `/chesthunt status` → coords + items left. At noon a chest spawns within 100 blocks; right-click → loot GUI **with XP bottles among the 20 types**; taking an item removes it for everyone. |
| 15 | MAVOCrafting (3.0.2 fix) | `/crafting reload` → re-register lines, no “bad result” warns. | `/craft` → **100 beginner recipes** (45 per page); `/craft stone_pickaxe` jumps; page shows MAP + TERRACOTTA recipes. `/crafting list` → **7 custom** (name tag, saddle, lead, chainmail). Craft a name tag in a table → works, recipe unlocks in your recipe book. |
| 16 | MAVOLuckyCoins (3.0.2 fix) | `/wish well` (look at the well) optional; `/ccollect give 1` → gives coin. | `/wish` → info; stand at the well and press Q → pool rolls (no error). `/ccollect` → claim if 10 MC days passed. `/destroy` on junk → junk gone, sellables kept. |
| 17 | MAVOEvents | `/event start giftdrop` → event starts; `/event stop` → stops. | `/event` and `/event list` → all 10 events; during an event the effect is announced in chat. |
| 18 | MAVODeathChest | Die as test (or `difficulty` …) — click any grave chest. | Die → chest with your loot; `/grave` → GUI with coords + time left; click grave → travel for 1,000 coins **or** 10 Lucky Coins (3 s countdown); loot held 30 min then bursts. |
| 19 | MAVOAchievements | `/ach reload` → 52 categories reload. | `/ach` → menu opens; every achievement starts at **level 1**; mining one ore shows progress. |
| 20 | MAVOProfessions | `/profession addxp miner 100 <testplayer>`; `/profession reload`. | `/profession` menu + `/profession check` → 10 professions; `/profession top miner`; tool tier message (Stone→Iron L10→Diamond L25→Netherite L50). |
| 21 | MAVOWanderer | `/wanderer spawn` → trader appears near you. | `/wanderer` → next visit time; trader sells 83 offers (no junk items). |
| 22 | MAVOCurator | `/museum reload` → 103 sections / 1,413 items; `/museum shopsgen` if prices change. | `/museum` → collection book; `/museum extras` → buy-only list; buy 1 missing item → wallet charge + museum. |
| 23 | MAVOWild | `/wild portal` + `/wild homeportal` (look at blocks) OK; `/holoreset 60` → holos rebuild. | `/wild` → teleports far (5,000–400,000 blocks) with 3 s warmup + 5 min cooldown; portal signs show 1,000s format. |
| 24 | MAVOShopNPC | `/shopnpc list` (0 NPCs expected now) + `/shopnpc resholo` → 0 holos, no errors. | `—` (no NPCs placed yet; when one exists, its command fires on click). |
| 25 | MAVOMobFarm | `/mobfarm status` + `/mobfarm resholo`; no warnings. | `/mobfarm info` → cost 10,000 coins, 15 min; `/mobfarm enter` → session; `/mobfarm prices` → 36 mobs GUI; `/mobfarm pick` → pick #1 = shop price/16. |
| 26 | MAVOCasino | `—` (no admin command). | `/casino` → Louie’s menu, 10 games; bet 100–5,000 coins or 1–10 Lucky Coins; result message appears; 10 coin + 10 Lucky Coin attempts per 10 MC days. |
| 27 | MAVOTimber | `—` (no admin command). | `/timber status` → ON, max 10 logs; `/timber toggle`; break the bottom log of a **grown** tree → up to 10 logs fall; a player-built log house → “That’s not a grown tree” (vanilla break). |
| 28 | MAVOEnchants | `/maenchant gem <p> touch 5` → gem given. | `/gemshop` → 2 pages (tool/armor gems), tiers I–X prices 1M–512M; `/maenchant list` shows 8 enchants + odds; apply gem (main hand) + item (offhand), right-click; mine ore → tier I 0.1% drop per action. |
| 29 | MAVOMiniboss | `/miniboss reload` → 15 boss types, 2 arenas. | `/miniboss status` → both arenas + live bosses; `/hunt` → teleports near a live boss (~200 blocks, 30 s cooldown); `/miniboss locate` → exact coords; kill a boss → announced drop % rolls. |
| 30 | MAVOCrates | `/crate set common` (look at block) + `/crate resholo`; `/crate givekey <p> common 1`. | `/crate list` → 3 types + key drop % (1% / 0.05% / 0.01%); farm/mine/fish for a key; right-click crate → GUI with exact % + OPEN (uses 1 key). |
| 31 | MAVOChestShops | `/cshop`-based setup (or admin GUI if present) — currently 0 shops, 0 stalls. | `—` (no shops set yet; when a chest shop exists: put items → sold via GUI, coins added). |
| 32 | MAVOChunkBorders | `/borders` (toggle for self) — borders render for admins too. | `/borders` → visual chunk border toggle works. |
| 33 | MAVOChunkPrices | `/chunkprice` → tier table shown (1x 0 … 10x 2500, max 51 chunks). | Claim-owned chunk price line + claim cost matches the tier table when claiming. |
| 34 | MAVOCommunityGoals | `/goal` → 3 goals list; `/goal donate` (player-facable) — OP can `/donate` too. | `/goal` → goal progress; `/donate <items>` → contribution counted, goal bar updates. |
| 35 | MAVOCouples | `—`. | `/marry <player>` → request; other player `/accept` → married (0 marriages currently → expected); `/couple` info; `/divorce` works. |
| 36 | MAVODoubleXp | `—`. | `/xpboost` → shows weekly window Fri 18:00 → Sun 18:00 ×2.0 — outside window: “off”. |
| 37 | MAVODuels | `/duelarena set` (if used) — arena currently 0,0. | `/duel <player>` → request; `/daccept`; fight → `/dstats` shows results. |
| 38 | MAVOFishComp | `—`. | `/fishcomp` → current window (Sun 17:00 style) + leaderboard; catching a fish inside the window counts. |
| 39 | MAVOGuilds | `/guild` admin view — 0 guilds expected. | `/guild` → create/join; `/gchat <msg>` → guild chat works. |
| 40 | MAVOHomes | `—`. | `/sethome` → sets bed-home; die → respawn at bed; `/home` → teleport; `/delhome`. |
| 41 | MAVOHud | `/hud` → toggle (or check) — expansion mavohud registered. | HUD shows Player Level + deaths; tab list shows `%mavohud_time%` etc.; `/papi parse me %mavohud_level%` → a number (no error). |
| 42 | MAVOLocks | `—`. | `/lock` on a chest/door → locked; other player gets “locked” message; `/trust <p>` → they can open; `/untrust`; `/unlock`. |
| 43 | MAVOMail | `—`. | `/mail send <p> <text>`; `/mail` → inbox list (max 100, 7-day expiry); claim/delete works. |
| 44 | MAVOPersonalVault | `—`. | `/pvault` → personal vault GUI (packs of 5 slots); slots persist across relog. |
| 45 | MAVOPets | `—`. | `/pets` and `/pet` → 6 pet types (axolotl, cat, fox, parrot, turtle, wolf); adopt one → follows you, persists. |
| 46 | MAVOPortalRoom | `—`. | `/portalroom` → info; enter any of the 26 jump portals → teleports; prices shown per portal (1,000 … 5,000,000 coins). |
| 47 | MAVOQuests | `—`. | `/quest` → board 10/10, pool 19; accept a quest; progress updates; refresh after 1 MC day. |
| 48 | MAVOSeasonal | `/season` → “0 season(s) defined, active: none” (expected — no events set). | `/season` → same message in chat; no errors. |
| 49 | MAVOSpawn | `—`. | Try to break/place inside spawn radius 60 around (-2578,200,-1684) → protection message; `/spawnprot` status shows coverage. |
| 50 | MAVOSpawners | `—`. | `/spawner` → place/collect spawner flow works; mob spawns from it. |
| 51 | MAVOStreaks | `—`. | `/streak` → shows today’s status; logging in tomorrow keeps the streak alive. |
| 52 | MAVOTavern | `—`. | `/tavern` → bed info; entering bed costs 100 coins, locks until next noon; 4 bed holos visible. |
| 53 | MAVOTpa | `—`. | `/tpa <p>` allowed; `/tpahere`; `/tpaccept` / `/tpdeny` / `/tpacancel` all work. |
| 54 | MAVOTrades | `—`. | `/mtrade` → master traders list; trading with a master works. |
| 55 | MAVOVault | `—`. | `/vaultroom` → vault gate: 50,000 coins; portal room gate 25,000; room 5,000; multi-room + expand-up + chest signs work. |
| 56 | MAVOWarps | `—`. | `/warp <name>` (0 warps yet → “no warps” message); `/warps` lists them. |
| 57 | MAVOAuctionHouse | `/ah` admin view — listings 0, shopSell 886, region=true. | Open auction house → sell 1 item at a price → 886 prices indexed; buy works; keeper/stuck checks pass. |
| 58 | MAVOBossRaid | `—`. | On raid day (Sat 20:00): chat announces boss; participate → loot; `/boss` shows next raid. |
| 59 | LuckPerms | `/lp groups` → `owner` exists (prefix OWNER, priority 100, no admin nodes); `/lp user <you> info` → correct groups. | Tag above head / tab shows your rank prefix correctly. |
| 60 | Vault | Console shows `[Vault] [Economy] Essentials Economy hooked.` + `[Vault] [Permission] SuperPermissions loaded as backup…`. | `/balance` → works via Essentials/Vault; shop purchases deduct correctly. |
| 61 | PlaceholderAPI | `/papi list` → internal expansions: claimchunk, mavoprof, mavohud, esgui + external vault. | `/papi parse me %mavohud_level% %mavoprof_miner_level% %esgui_*%` → values, no `null`. |
| 62 | TAB | `/tab list` (or tab debug) → no errors; hint about refresh interval is cosmetic. | Tab list shows players + `%mavohud_time%` etc.; no blank names. |
| 63 | EssentialsX (+Chat/Spawn) | `/essentials version` → 2.22.0; the unsupported-version ERROR line is known & harmless. | `/balance`, `/kit` (if any), `/workbench` still work; **`/craft` is the MAVO recipe guide, not the workbench** — `/e craft` still workbench. |
| 64 | Geyser | `/geyser list` → 0 online bedrock (or players), Geyser on 19132. | Join from Bedrock → appears online; custom blocks/items registered (199 block overrides). |
| 65 | Floodgate | `/floodgate` → enabled; linked accounts OK. | Bedrock player gets a Java-style name with prefix; can play without Java account. |
| 66 | ClaimChunk | `/chunk info` (or claim admin) — Economy NOT enabled is intentional. | Claim a chunk (free), see border; unclaim works; placeholder `%claimchunk_*%` resolves. |
| 67 | EconomyShopGUI | `/essentials` console: `Completed loading 28 shop configs`. | Buy from a shop → item + coin change; `/es` menu works. |
| 68 | CoreProtect | `/co i` → inspector on; `/co lookup u:<player> a:block-break` → rows. | Place + break a block where OP can inspect → entry appears in CoreProtect log. |
| 69 | BlueMap | Console `[BlueMap] Loaded!` + webserver 8156; `/bluemap` if command available. | Open the web map (port 8156) → world / nether / end tiles render; spawn area visible. |
| 70 | spark | `/spark tps` → ~20 TPS after all checks; no big spikes from any MAVO plugin. | — (n/a — admin performance check; player just plays normally). |

## PART 3 — RESTART PERSISTENCE (71–74)

| # | OP | Player |
|---|---|---|
| 71 | Restart the server a second time → no **new** repair lines (ChestHunt/Crafting show nothing to repair → no “repaired …” log), pool still 20, recipes still 100. | — (n/a) |
| 72 | `/chesthunt status` before & after restart inside a chest lifetime → same chest persists. | — (n/a) |
| 73 | Check `plugins/MAVOChestHunt/config.yml` + `plugins/MAVOCrafting/config.yml`: old names are GONE (search `EXP_BOTTLE`, `EMPTY_MAP`, `TERRA_COTTA` → 0 hits). | — (n/a, file check) |
| 74 | Back up the WHOLE `plugins/` (or download a fresh MAVOcraft-backup zip) so the next restore carries the healed configs. | — (n/a) |

**Rule reminder:** the FOUR 3.0.2 jars + other 42 MAVO at 3.0.0 = 46. Any file that does not match `SHA256SUMS` means it was not copied — go back to step 05.
