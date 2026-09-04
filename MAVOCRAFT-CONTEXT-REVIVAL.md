## PORTALROOM 1.1.0
Shipwreck: land on wreck + buried treasure explorer map.
Mineshaft: land by rails + directional signs/torch path (not blind glow pad).
Structure locate retries + larger radius. structure-locate-radius config.

## TAVERN 1.0.1
Bed lock uses unlock-at fullTime (next noon after rest), not day-index.
Fixes "already rested" blocking the next night after a paid skip.
`/tavern unlock [player]` clears lock. Migrates legacy used.* data.

## VAULT 1.7.1
- Claiming empty room ABOVE another player's room: price = chest-price ×
  block-above-multiplier (default **100** → 500k). Discourages grief-blocking
  expand path. Owner door Expand Up still 2× paid only.
- `/vaultroom wipeplayer <name>` — release all rooms + revoke vault/portalroom access.
- `/vaultroom wipeaccess <name>` — gate access only.

## VAULT 1.7.0
- Expand click FIXED (slot 13 was ignored by onClick filter).
- Door menu: slot11 toggle open/close, slot13 expand purchase, slot15 close.
- /vaultroom rebuild — replays vault_build.mcfunction (ender chests) from jar.
- Chests are ENDER_CHEST pairs; unlock opens private 54-slot pair storage
  (data rooms.<id>.pair.<unit>), not vanilla shared ender.
- Ladder: aisle between centre chest rows, polished deepslate support.
- /vaultroom fixladder — re-place ladders for rooms with ladder-to set.
- Datapack zip updated to ender_chest.

# MAVOcraft — FULL CONTEXT & REVIVAL DOCUMENT
Last updated: 2026-09-03 (Guide v14 + 10 jars rebuilt, see 2026-09-03 section below)

Read this file top-to-bottom to fully restore working context for the MAVOcraft
Minecraft server project. Everything here is CONFIRMED DEPLOYED unless marked otherwise.

---

## 1. WHO / WHAT / WHERE

- Owner: Kick streamer "MAVO" — Java name **MAVOmusicYT** (OP, LuckPerms owner,
  UUID 53322438-a7a2-4eb2-bb2b-1c648a43dccf). Whitelist: MAVOmusicYT, NecroCaticGames.
- Host: **PebbleHost Premium 4GB** ($9/mo), Ryzen 9 EU node.
- Server: **Paper 26.2 build 119**, Java 25, IP **185.206.148.185:25567**,
  domain `mavocraft.my.pebble.host`, BlueMap web map port **8156**, Geyser (Bedrock crossplay) UDP **19132**.
- Sky spawn plaza: center **-2578 / 200 / -1684**, protection cube ±60 (only OP+creative build;
  villager interaction allowed). Wishing well at -2587 201 -1684. Wild Portal north, Home Portal south.
- User plays Java. Crossplay via Geyser+floodgate. World was FULLY RESET once; keep plugin suite.




## VAULT 1.6.2 — EXPAND + CHEST SIGNS
Door (owner, no sneak): opens owner menu
  - Enter room (opens birch door)
  - Progress: N/15 chests still needed
  - Expand Upward when 15/15 + room above empty
    price = 2× rooms.<id>.paid (doubles each floor: 5k→10k→20k→40k…)
    places ladder shaft at doorX / mid-Z between centre chest banks
    punches ceiling into upper floor; upper claimed with slot#1 free

Chest signs: ALWAYS oak standing sign ON TOP of chest (not wall-side).
Auto refreshAllChestSigns on enable + /vaultroom chestsigns.
Sneak-chest still opens rename/recolor GUI (writes the top sign).

Wild 1.7.3: /holoreset = wild/home only; /holoreset all = full plaza.

## HOLO + PORTAL FIX (2026-09-01 evening)
Root cause of missing Tutorial/Louie/Curator/shopkeeper holos: Wild 1.7.1
`/holoreset` deleted TextDisplays by text fingerprint but ShopNPC could not
respawn them (no holo-text in config, no PDC tag, no resholo command).

MAVOShopNPC **1.3.2**:
- PDC tag `shopnpcholo`, stores `npcs.<name>.holo-text`
- `/shopnpc resholo` (console OK) restores all; defaults for Tutorial_Guide,
  Lucky_Louie, The_Curator, Update_Crier, Achievement_Keeper, Profession_Master
- Auto-restore on enable (+100 ticks)

MAVOWild **1.7.2**: holoreset only removes restorable holos; always dispatches
portalroom reload, vaultroom reload, tavern reload, **shopnpc resholo**, wish well.

MAVOVault **1.6.1**: fxLoop/countdownLoop/holoVisibilityLoop wrapped try/catch
(task no longer dies on one bad tick). `/vaultroom reload` also respawns chest
holos + room signs.

MAVOPortalRoom **1.0.2**: fxLoop/tickLoop/spawnAllHolos try/catch same reason.

Deploy all four jars, restart, `/shopnpc resholo` or `/holoreset` at plaza.

## HOLORESET (MAVOWild 1.7.1)
Stale TextDisplay entities keep old text after jar swaps (e.g. Wild still shows
2,000-5,000). Fix:

  /holoreset          - wipe+respawn MAVO holos within 60 blocks of you
  /holoreset 80       - custom radius 8..128
  /wild holoreset 60  - same

Also runs on every MAVOWild enable (refreshPortalHolos after 3s) and migrates
max-radius <=5000 -> 400000 automatically.

What it does:
1. Loads chunks in radius
2. Removes TextDisplays that are PDC-tagged mavowild/mavovault/mavoportalroom/
   mavotavern/mavolucky/shopnpc OR match portal/vault/well/tavern fingerprints
   (including legacy "2,000-5,000" / "NO WAY BACK" text)
3. Respawns Wild+Home portal holos from live wildHoloText()/homeHoloText()
4. Console-dispatches: portalroom reload, vaultroom reload, tavern reload
5. If near wishing well + admin: re-runs /wish well

Deploy MAVOWild-1.7.3, stand at plaza center, run /holoreset (needs mavowild.admin
= OP briefly, or console: but holoreset is player-location based so in-game).

Guide 2.5.4 content v11 mentions /holoreset.

## WILD 1.7.0 + VAULT 1.6.0 + GUIDE v10 (2026-09-01)
WILD PORTAL (MAVOWild 1.7.0):
- min-radius 2000, max-radius **400000** (was 5000). Holo text updated.
- Rolling blacklist: last **50** landing zones (`wild-zones` list in
  plugins/MAVOWild/config.yml), each blocks a **200-block** radius for future
  warps (same idea as PortalRoom). Oldest drops off after 50 new warps.
- Deploy jar; existing portal pos1/pos2 untouched. Optional: delete old holo
  and re-run `/wild portal pos1`+`pos2` only if you want holo text refresh,
  or just leave it - teleports use new range either way. Or `/wild` won't
  refresh holo - holo is rewritten only on pos2. Quick fix: stand in portal
  area admin and re-pos2, or manually ignore stale 2k-5k text until then.
  Actually spawnHolo is only on pos2 - user can fly and re-set pos2 same corners.

VAULT (MAVOVault 1.6.0):
- Multi-room: after ALL 15 chests unlocked in a room, player may claim another
  free room elsewhere (door buy GUI). Until then, still blocked from second claim.
- Expand-up bundle: owner **sneak + right-click door** on a FULL room → GUI
  "Expand Upward" into the room directly above (same col/side, level+1).
  Price = **2× paid price** of current room (stored rooms.<id>.paid). Places a
  **ladder** linking floors. Buying the upper room by walking to its door is a
  normal claim (base 5k) and does **NOT** place a ladder.
- Chain: each expand stores paid=2× previous so next expand doubles again.
- Chest signs: every unlocked chest gets an oak wall/standing sign. Owner
  **sneak-right-click chest** → GUI: rename (chat, max 16) + 16 color wool picks.
  Signs are waxed (others can't edit). data: rooms.<id>.chestlabels/colors.<unit>
- playerrooms.<uuid> list (migrates legacy playerroom.<uuid> string).
- /vaultroom releaseroom <player> releases ALL their rooms; roominfo lists all.

GUIDE 2.5.3 content v10: vault/portal described as LIVE; wild range + blacklist;
multi-room + expand + chest signs documented. Removed stale "coming soon" portal title.

## MAVOTAVERN 1.0.0 - SPAWN NIGHT SKIP (2026-09-01)
Small plaza tavern bed: right-click at night/thunder, pay 100 coins, world
skips to morning (time 1000, clear storm). One paid rest per player; lock
resets at NOON (world time 6000) the following day. Creative = free.
Does NOT set respawn / does NOT bind MAVOHomes (event cancelled).
Bed protected from break except creative admin.

Setup (console or brief creative):
  1. Upload MAVOTavern-1.0.1.jar, restart or load
  2a. Stand where you want the hut, creative: /tavern build
      (5x5 spruce hut + red bed + door + lantern + holo)
  2b. OR place your own bed and /tavern setbed while looking at it
  3. /tavern info  - price + your lock status
  4. Optional holo refresh: /tavern reload
Admin: /tavern unlock [player] clears lock. /tavern clear disables bed.
Config plugins/MAVOTavern/config.yml: price, wake-time, night-only, bed coords.
data.yml stores used.<uuid> = MC day index of last sleep.
Keep folder on resets (like MAVOWild) if you want bed coords to survive -
or just re-run /tavern setbed. NOT in the never-folder-delete list yet;
bed is cheap to re-set - optional add later.

Guide 2.5.2 content v9 documents it. Permission mavotavern.use default true.

BOOT LOG CHECK (2026-09-01 17:37 deploy): CLEAN. All MAVO plugins enabled
including LuckyCoins 1.5.1 (pool 1266 sellable), Guide 2.5.1, ESGUI 28 shops.
Ignore-only noise: Essentials unsupported-version, BlueMap manual-save WARN,
TAB %mavohud_time% hint, ESGUI spawner AUTO + Debug mode, Vault update check,
Paper "2 builds behind", sun.misc.Unsafe, ShopNPC "0 shop NPC(s)".
No errors that block play. essentials.spawn on default still recommended if
/spawn denied for non-OP.

## WISH WELL = SHOP-SELLABLE ONLY (2026-09-01)
Players got Firefly Bush / Birch Shelf / Mangrove Pressure Plate from the well
with no /shop sell entry = unsellable junk. Permanent rule:

  EVERY wishing-well prize MUST be sellable in EconomyShopGUI (sell > 0).

IMPLEMENTATION:
- MAVOLuckyCoins 1.5.1 ships `well-pool.txt` (MATERIAL:maxAmt:weight) built from
  the live shops tree — currently ~1271 sellable mats. loadWellPool() on enable.
  82% weighted items / 18% enchanted gear (gear also from the same sellable set).
  Amount caps scale with sell value (junk up to 32, trophies always 1x).
  Hard ban still: NETHERITE_BLOCK / NETHERITE_INGOT + commandy/spawn-egg/etc.
- When adding a new shop item that should be wishable: add it to shops/*.yml
  with sell>0, regenerate well-pool.txt from the shops tree, rebuild LuckyCoins.
- ESGUI gap fill (Z_EverythingElse page9 + unsellable→sellable):
  * NEW: all wood SHELFs (oak..pale_oak/crimson/warped), FIREFLY_BUSH (buy45/sell9),
    MANGROVE_PRESSURE_PLATE (12/0.46), SHORT_GRASS (15/0.6), CLOCK (120/24),
    DISC_FRAGMENT_5 (550/110).
  * Was sell -0.1/0 → now sellable: DRAGON_HEAD 5k, WITHER_SKULL 3.6k,
    skeleton/creeper/zombie heads 500, ENCHANTED_GOLDEN_APPLE 9600,
    WET_SPONGE 160, BEE_NEST 240, poisonous potato 1, copper slab/stairs crumbs.
- Shop audit after patch: 1351 materials, 0 with sell<=0.
- Guide 2.5.1 / content v8 documents the rule.
- Deploy: LuckyCoins-1.5.1 + Guide-2.5.1 jars + full shops/ (or v5 tarball) + /sreload.
  Keep plugins/MAVOLuckyCoins/config.yml (well coords). Optional /wish well holo refresh.

## NETHERITE SHOP + WISHING WELL REBALANCE (2026-09-01)
CAUSE of the 24x netherite-block jackpot: MAVOLuckyCoins 1.4.0 grantWish 75% path
picked uniform random Material.values() with amount = 1..maxStackSize. Netherite
block is a legal Material -> full stacks were possible. Sell was 38355.48 each
(~45x diamond_block sell) so one wish could print ~920k coins.

SHOP FIX (EconomyShopGUI shops/resources.yml + Z_EverythingElse scrap):
  Rule: netherite ore/ingot/block buy&sell = 8x matching diamond counterpart.
  Sell remains ~20% of buy where diamond already followed that; diamond_ore sell
  is flat 200 so debris sell = 8x200 = 1600.
  | item | buy | sell | basis |
  | diamond | 2360 | 94.5 | unchanged |
  | netherite_ingot | 18880 | 756 | 8x diamond |
  | diamond_block | 21240 | 850.5 | unchanged |
  | netherite_block | 169920 | 6804 | 8x diamond_block |
  | diamond_ore | 7790 | 200 | unchanged |
  | ancient_debris (ore) | 62320 | 1600 | 8x diamond_ore |
  | netherite_scrap | 4350 | 174 | ~aligned under debris (was 28900/111) |
  Deliverable: shops/ tree + MAVOcraft-shops-villager-economy-v5.tar.gz
  Deploy: upload shops/resources.yml + shops/Z_EverythingElse.yml (or extract
  v5 tarball over plugins/EconomyShopGUI/shops/), then /sreload.
  NEVER folder-delete EconomyShopGUI.

WELL FIX (MAVOLuckyCoins 1.5.0):
  - 20% enchanted gear (was 25%); gear tier weights 30/24/20/12/5/1
    (netherite gear ~1 weight, lightly enchanted at most).
  - 80% weighted item tiers with amount caps (triangular bias low):
    common ~60.5% of item path max 24; uncommon 25% max 8; rare 12% max 3;
    epic 2% max 1; mythic 0.5% max 1.
  - HARD BAN: NETHERITE_BLOCK and NETHERITE_INGOT never from well.
    Mythic can roll 1x NETHERITE_SCRAP / ANCIENT_DEBRIS / elytra / etc.
  - Final safety net rewrites banned rolls to iron ingots.
  - Holo text: "Weighted prizes · no netherite jackpots".
  Deploy jar; well coords live in plugins/MAVOLuckyCoins/config.yml (keep it).
  Optional: /wish well standing on well to refresh holo text, or delete holo
  entity + re-run.

GUIDE: MAVOGuide 2.5.0, content version 7 (auto-opens once). Changelog + lucky
coins tutorial/feature pages updated.

BACKUP SHA-256 (2026-09-01 vault 1.7.1): `1f04a037bcd5e8808972d27d4ebdaef264b1bf1ea3542b60f05705fefcf70eac` — also MAVOcraft-backup.sha256.

/spawn still Essentials: if non-OP can't use it:
  lp group default permission set essentials.spawn true

## CHUNKBORDERS 1.2.0 + CLAIMCHUNK PERMISSIONS FIX (2026-09-01)
BUG 1 - /chunk claim etc. OP-only: ClaimChunk 0.0.25-FIX3's plugin.yml uses
the SINGULAR key `permission:` instead of `permissions:`, so Bukkit never
registers its default-true player nodes; unregistered nodes = OP-only.
FIX (permanent, one console line): 
  lp group default permission set claimchunk.player true
(every basic subcommand - claim/unclaim/list/info/access/give/name/alert/
auto/show/scan - accepts claimchunk.player). Never remove this node.
BUG 2 - broken border lines: old renderer used the MOTION_BLOCKING heightmap
(includes LEAVES) -> lines rendered on treetops/roofs, looked gappy on the
ground. MAVOChunkBorders 1.2.0 (src at _Old/src-chunkborders, rebuilt from
decompiled 1.1.0): MOTION_BLOCKING_NO_LEAVES heightmap + down-scan from
player Y+3 when surface is above head; defaults changed PURPLE glass ->
RED_STAINED_GLASS border + BLUE_STAINED_GLASS corners (no-purple rule; guide
already said red/blue - no guide bump needed). Deploy requires deleting the
old plugins/MAVOChunkBorders/config.yml so new defaults generate.
Also verify plugins/ClaimChunk/config.yml has economy.useEconomy=false
(MAVOChunkPrices is the only charger).

## STANDING MAINTENANCE PROTOCOL (user-mandated - ALWAYS follow)
1. EVERY update that changes gameplay, prices, systems or flows MUST also
   update MAVOGuide (changelog bump + affected feature/tutorial pages),
   rebuild MAVOGuide-x.y.z.jar and deliver it alongside the feature jar.
2. After EVERY delivered update: rebuild MAVOcraft-backup.zip (jars/ docs/
   sources/), print its SHA-256, and append what changed to this document.
   User uploads the backup to GitHub - it is the disaster-recovery source.
3. This document is the single source of truth for reviving context.

## OWNER RANK = TAG ONLY (since 2026-09-01 fresh-start stream)
MAVOmusicYT plays as a NORMAL player: de-opped, no fly/creative/supermod.
"owner" LuckPerms group carries ONLY the prefix (priority 100, red OWNER tag),
no admin permission nodes. Moderation happens from PebbleHost CONSOLE only
(ban/kick/whitelist/lp). If any MAVO plugin admin action is needed, run it
from console or temp-op then de-op. Setup commands (console):
  lp creategroup owner            (already exists)
  lp group owner clear            (strip any permission nodes, keep meta)
  lp group owner meta setprefix 100 "&4&lOWNER &r"
  lp user MAVOmusicYT parent add owner
Result: red OWNER tag in chat/TAB, zero elevated permissions in game.

## VAULT 1.5.0 - LOCK-STATE PORTAL HOLOS (2026-09-01)
Plaza gate portals (vault / portalroom) now spawn TWO stacked TextDisplays:
- "UNLOCKED" variant (green check, usage hint) - visibleByDefault
- "LOCKED" variant (red padlock, "One-time entry: <price> coins",
  "Step in to unlock") - hidden by default
holoVisibilityLoop (every 40t, players within 64 blocks) uses
p.showEntity/hideEntity to show each player exactly one variant based on
hasAccessRaw. gateHolos map holds [openUuid, lockUuid]; portalremove sweep
still removes both via the portalholo_<id> PDC tag. Return portals unchanged
(single green holo). Prices in locked holo read live from config at (re)spawn
- rerun /vaultroom reload after price changes.

## LATEST SYSTEMS (2026-09-01)

### Rooms built by datapack (world/datapacks/MAVOcraft-builder-datapack.zip)
Functions: build_all / portal_room / portal_frames / vault_shell / vault_build /
purge_mobs / demolish_all. pack_format 81 (exact, no range - ranges error on Paper 26.2).
Functions forceload their own areas (fills silently fail in unloaded chunks!).
- PORTAL ROOM shell X -2450..-2350, Y198..228, Z -1700..-1670, bedrock, deepslate
  lining, red nether brick pillars, froglight grid every 8 blocks (light>=11,
  no mob spawns), doorway west face. 26 portal frames (13 N + 13 S), 3w x 4t,
  black concrete void, themed frame blocks, every 7 blocks starting x=-2446
  (frame opening interior x = L+1..L+3 where L=-2446+7*slot).
- VAULT shell X -2800..-2700, Y198..238, Z -1705..-1665. INTERIOR = Vault 2.0:
  6 levels (floor walk Y 200/206/212/218/224/230), gold corridor z-1685 with
  glowstone edges each level, ladders both ends (x=-2798 east-facing,
  x=-2702 west-facing), 96 private rooms = 8 columns x 2 sides x 6 levels.
  Room column A=-2793+11*col (interior A..A+8, door at A+4), north rooms
  z-1703..-1689 (door z-1689), south z-1681..-1667 (door z-1681), themed wall
  blocks (16 material rotation shifted per level), birch door + oak wall sign
  above (y0+2), 15 double chests per room in bank layout (non-touching),
  sea lantern ceiling + glowstone floor lights per room.

### MAVOVault 1.4.0 (deployed; source _Old/src-vault)
- Gates: vault 50,000 / portalroom 25,000 one-time (regions in config gates.*.region,
  RE-RUN pos1/pos2 gate vault to cover Y198..238 all levels!).
- Plaza portals ACTIVE (portals.vault / portals.portalroom via pos1/pos2 +
  /vaultroom portal <id>): locked players get unlock GUI, unlocked get 3s
  countdown then teleport to portal-dest.<id> (set via /vaultroom portaldest
  <id> standing at arrival spot; fallback just inside doorway).
- Return portals: portals.vault_return / portalroom_return (green/gold FX),
  3s countdown -> return-spawn.* (set via /vaultroom returnspawn at plaza).
- PRIVATE ROOMS: room table hardcoded matching datapack (LEVEL_Y, colA etc).
  Room ids L<1-6><N|S><1-8>. data.yml: rooms.<id>.owner / rooms.<id>.slots
  (list of unlocked unit ints 0..14), playerroom.<uuid> -> room id (ONE room
  per player). Claim = chest-price (5,000) via door/chest right-click GUI ->
  slot #1 free + door sign written gold/red "<name>'s Vault". Slot ladder
  SLOT_PRICES = 0,1000,1000,2500,2500,2500,5000x4,10000x5 (full room 81,500
  total incl. claim). Sealed chest right-click = unlock GUI (charged on click
  only). Doors: owner-only (creative admin bypass). NOTHING breakable in vault
  region except admin creative. Old per-chest holo/rename system REMOVED.
- Admin: /vaultroom roominfo | releaseroom <roomId|player> | signs (rewrite 96
  signs) | portalremove <id> | portaldest <id> | returnspawn | reload.

### MAVOPortalRoom 1.0.1 (deployed; source _Old/src-portalroom)
- 26 jump portals, geometry derived from frame math (NO in-game setup).
  TABLE rows: side|slot|id|kind|key|landing|ymin|ymax|price|r|g|b|cc|name|desc.
  North biomes: desert 1000, savanna 1200, swamp 1500, darkforest 2000,
  taiga 2500, flower 3000, peaks 3500, jungle 4000, bamboo 4500, ice 6000,
  cherry 7000, badlands 8000, mushroom 10000.
  South danger: shipwreck 1500, mineshaft 3000, dripstone 3500, lush 4000,
  pyramid 4500, witchhut 5000, jungletemple 5500, deepcaves 6000, outpost 7000,
  monument 9000, stronghold 11000, trial 13000, deepdark (ancient_city) 15000.
- Per-portal FX veil (unique DustOptions color) + small holo (name/desc/price).
- Jump: balance pre-check (red error title if poor), 7s countdown, step-out
  instant cancel, monsters-12-blocks block, charge ONLY after successful
  teleport. Creative = free 3s countdown (NOT instant - caused accidents).
- Destination: random angle, 25,000-400,000 blocks from room, rejects within
  2x100 blocks of this portal's last 50 zones (data.yml zones.<id> list
  "x,z"). BIOME kind -> locateNearestBiome (mushroom radius 12k step 256,
  else 6.4k/128); STRUCTURE -> locateNearestStructure r5000; CAVE landing
  scans y-range for air pocket else carves 3x3 glowstone pocket. SURFACE
  lands highest block (lava capped with obsidian).
- Admin: /portalroom list | clearzones <id|all> | reload. Config: world,
  min/max-distance, blacklist-radius/size, countdown-seconds.

### Player reset procedure
See RESET-PROGRESS.md (root + in backup): per-file deletion list to zero one
player (Essentials userdata, world playerdata/advancements/stats, MAVO*
data.yml files, ClaimChunk data, lp user clear). Never delete config.yml or
the four never-folder-delete plugins (EconomyShopGUI/LuckPerms/TAB/MAVOWild).

## 2. DEPLOYED PLUGIN SUITE (boot-verified versions)

Third-party: BlueMap 5.23, ClaimChunk 0.0.25-FIX3, CoreProtect 24.0, EssentialsX 2.22.0
(+Chat/+Spawn), Geyser+floodgate, LuckPerms 5.5.78, PlaceholderAPI 2.12.3, TAB 6.1.2,
Vault 1.7.3-b131, EconomyShopGUI 7.2.1.

Custom MAVO plugins (all sources in `sources/` of the backup archive):
| Jar | What it does |
|---|---|
| MAVOAchievements-1.5.0 | 14 lifetime categories incl. 6 gambling + museum; public API `externalProgress(Player,String,long)` + `externalHighwater(...)` |
| MAVOCasino-1.1.0 | Lucky Louie: 5 games (Cups 2.7x, Flip 2x@47.5%, Dice 2.3x ties-lose, Wheel 0-10x weights 34/25/15/16/9/1, TNT Tiles 1.2→32x, 2 TNT, cash-out). 10 attempts/10 MC days. Bets 100-5000 coins or 1-10 lucky coins. Reads gambler stick (material→luck), awards gambler XP via Professions.externalXp |
| MAVOChunkBorders-1.2.0 | always-visible claim borders + 1-chunk no-build buffer |
| MAVOChunkPrices-1.0.1 | tier table 1 free/5x100/5x250/5x500/5x1000/10x1500/10x2000/10x2500, max 51 |
| MAVOCommunityGoals-1.1.1 | donation pots instead of chunk tax |
| MAVOCurator-1.0.0 | THE MUSEUM: 103 auto-built sections, 1413 items. Grey/green GUI, click-donate 1, Deposit Crate (dump+close), dupes bounce with error. Section complete = items×1000⛃. Milestones 25/50/75/100% → LP curator25/50/75/100 + 10k/25k/50k/250k coins. Feeds `museum` achievement |
| MAVODeathChest-1.0.0 | locked grave 30 min |
| MAVOEvents-1.1.0 | Lucky Hour / Coin Rain / Mob Hunt |
| MAVOGuide-2.5.4 | guide GUI content v11 (auto-opens once/version), tutorial chapters, feature pages incl. Vault private rooms + Portal Room jumps |
| MAVOHomes-1.2.0 | bed right-click in OWNED chunks only binds /home; renameable |
| MAVOHud-2.0.0 | %mavohud_day/time/coords% placeholders for TAB sidebar |
| MAVOLuckyCoins-1.5.1 | 1% grind drops, wishing well pool = all ESGUI sellables (`well-pool.txt`), /ccollect every 10 MC days, admin `/ccollect give [n]` |
| MAVOPets-1.0.0 | pets |
| MAVOProfessions-3.13.0 | 9 professions (8 + GAMBLER max-1000), tool-bound XP, cap 999, prestige 250/420/666/999, per-profession rank-commands, public `externalXp(Player,String,double)`, tier note/name support, bound tools can't be placed as blocks |
| MAVOQuests-1.4.0 | daily board 10 quests |
| MAVOShopNPC-1.3.2 | villager NPCs running arbitrary commands + /shopnpc holo floating signs (scale 0.9) |
| MAVOSpawn-1.0.0 | spawn protection cube |
| MAVOStreaks-1.0.0 | login streaks |
| MAVOTavern-1.0.1 | spawn tavern bed: 100 coins skip night, lock until noon |
| MAVOTrades-1.0.0 | master trader stalls |
| MAVOWanderer-1.0.0 | curated wandering trader stock (83 offers) |
| MAVOWild-1.7.3 | Wild+Home portals, 2k-400k RTP, 50-zone/200m blacklist, 3s stand-in, /spawn warmup |

## 3. KEY CROSS-PLUGIN CONTRACTS (do not break)

- Lucky Coin identity: SUNFLOWER, name `&e&l⛀ Lucky Coin`, PDC byte
  `mavoluckycoins:luckycoin`, Unbreaking-1 + HIDE_ENCHANTS. Casino replicates it; well accepts casino coins.
- Profession tools: PDC `mavoprofessions:proftool` = "profId:branchId",
  `mavoprofessions:profowner` = player UUID, `mavoprofessions:proflock` byte (enchant-block).
- Casino reads gambler stick by MATERIAL (must match professions config tiers):
  STICK 1% / BAMBOO 2% / BONE 3.5% / SUGAR_CANE 5% / POINTED_DRIPSTONE 6.5% / BREEZE_ROD 8% /
  END_ROD 10% (+LP gambler) / LIGHTNING_ROD 12% (+LP 777) / BLAZE_ROD 15% (+mavocasino.pokercards emote).
- Achievements categories used by other plugins: betting, luckybets, winnings, luckywins,
  fortune (highwater), luckyhoard (highwater), museum.
- Curator refuses: renamed items, proflock-tagged, luckycoin-tagged; blacklists spawn eggs /
  creative-only items. SURVIVAL-only like ALL progress systems.
- Everything progression-related counts ONLY in SURVIVAL mode.

## 4. LP GROUPS (all created in console, confirmed)

flex(250)/yeman(420)/satan(666)/god(999) prestige; gambler (Gambler L250), 777 (L500);
curator25/50/75/100. Owner prefix priority 100 must outrank all.
⚠ NOTE: console showed `?` instead of ✦ in curator prefixes — console charset only, verify
in-game; if actually broken re-run setprefix with a simpler symbol like *.

## 5. STANDING RULES / USER PREFERENCES (hard requirements)

- NO purple anywhere → red or red/blue gradient. Currency symbol ⛃. Sell = 20% of buy.
- Villager-first economy: shop deliberately 5-10x expensive; villager trading is the way.
- Ores/raw must cost MORE than smelted (Fortune exploit guard) — v4 shops deployed.
- All teleports: 3s, cancel-on-move, blocked if monsters within 12 blocks; portals cancel on step-out.
- No chat spam: professions use bossbar; sidebar lists ALL professions.
- Floating holo signs: small scale (NPC 0.9 / well 1.9 / portal 2.2); never require chat-paste
  of long commands; /shopnpc holo auto-replaces; Wild sweeps orphan signs within 8 blocks.
- GUI-first: all info NPCs use GUI pages with Back+Quit. Tab-completion on commands.
- Gambling: in-game currency ONLY.
- User's config reset method = folder deletion. NEVER folder-delete: EconomyShopGUI, LuckPerms,
  TAB, MAVOWild (portal coords live there). Delete only specific config.yml, keep data.yml.
- Workspace rule: keep NEWEST deliverables in workspace root; move superseded into `_Old/`.
  Delete build targets/uploads after every build (stay under limits).
- Deliverables: agent builds all jars; user uploads via PebbleHost file manager.

## 6. BUILD RECIPE (sandbox)

```
ls /tmp/jdk/bin/java || (redownload JDK 25: adoptium api v3 binary latest/25/ga/linux/x64/jdk/hotspot/normal/eclipse -> /tmp/jdk)
chmod +x /home/user/_Old/apache-maven-3.9.9/bin/mvn
export JAVA_HOME=/tmp/jdk PATH=/tmp/jdk/bin:/home/user/_Old/apache-maven-3.9.9/bin:$PATH
cd <src-dir> && mvn -q package -DskipTests
```
- paper-api 26.2.build.48-alpha (papermc repo), VaultAPI 1.7.1 (jitpack com.github.MilkBowl),
  placeholderapi 2.11.6 (only professions). Local repo `.m2` in workspace root.
- ALWAYS bump: plugin.yml version + pom `<version>` + pom `<finalName>`.
- /tmp gets wiped often — check JDK before every build session.
- Verify configs INSIDE jar after resource edits (`unzip -p target/X.jar config.yml | grep ...`).

## 7. KNOWN PITFALLS (do not retry)

- Adventure showBossBar broken on this Paper — use Bukkit boss API.
- Action bar position is client-fixed — HUD lives in TAB sidebar.
- Console-dispatch of Essentials /spawn breaks — performCommand + bypass flag.
- Long /summon text_display > 256 chars — plugin commands only.
- Holo removal by stored UUID alone unreliable — pair with PDC-tag area sweep.
- CFR decompile needs ~4-5 generic fixes per class. Grep-filtered mvn output can hide errors.
- python .replace() silently no-ops — use asserts.
- Cosmetic boot noise to IGNORE: Essentials "unsupported server version", Vault update check,
  BlueMap manual-save WARN, TAB %mavohud_time% hint, "Re-applied protection to 0 shop NPC(s)",
  sun.misc.Unsafe warnings, ESGUI spawner-provider + Debug mode lines.

## 8. RELOAD COMMANDS (never global /reload)

/ess reload, /sreload, /chunk admin reload, /geyser reload, /borders reload, /tab reload,
/papi reload, /chunkprice reload, /goal reload, /quest reload, /ach reload, /profession reload,
/updates reload, /spawnprot reload, /museum reload, /wish well (re-place well holo).
Portal refresh: re-run pos1 (bottom corner) then pos2 (opposite TOP corner, fly).

## 9. SPAWN NPC ROSTER (spawn + holo commands in TEST-COMMANDS-FULL.md style)

Profession_Master (professions GUI), Tutorial_Guide, Update_Crier, Achievement_Keeper,
Lucky_Louie (`/shopnpc spawn Lucky_Louie casino`), The_Curator (`/shopnpc spawn The_Curator museum`,
holo `&d&l✦ THE MUSEUM ✦|&fOne of everything. Bring me wonders!`).

## 10. OUTSTANDING BACKLOG

- Confirm `time set 0` done after reset; achievements/streaks data.yml zeroing if wanted.
- Essentials disabled-commands (sethome/home/delhome vs MAVOHomes) in-game verify.
- Recruiter LP group idea; build contest (deferred); lottery (prepared, deploy later).
- MD manual refresh (portals/warmup/well/sidebar/guide2/no-wild/holo/casino/museum) — stale.
- User testing Museum + Casino + Gambler right now; watch for feedback.
- Deploy netherite shop + LuckyCoins 1.5.1 + Guide 2.5.1 + shops page9; /sreload + well holo.
- Confirm essentials.spawn on default group for non-OP /spawn.

## 11. RESTORING FROM THE BACKUP ARCHIVE

Archive layout (`MAVOcraft-backup.zip`):
- `jars/` — all current MAVO plugin jars, ready to upload to `plugins/`.
- `sources/` — full Maven source tree for every plugin (src-*/pom.xml + src/main/...).
  Rebuild any of them with the recipe in §6. Includes src-chunkprices + src-goals
  (CFR-restored) and src-lucky 1.5.1 / src-guide 2.5.2 / src-tavern 1.0.0.
- `configs/` — esgui-config.yml, shops/ (live ESGUI shop YAMLs, netherite=8x diamond),
  MAVOcraft-shops-villager-economy-v5.tar.gz (drop over plugins/EconomyShopGUI/shops/).
- `docs/` — manuals, checklists, this file.
- Also keep full `EconomyShopGUI.tar` on GitHub next to the backup for disaster recovery.

To revive an agent session from zero: give the agent this file + the archive URL; it downloads,
extracts to workspace, moves everything into `_Old/` except current deliverables, and continues.

## 2026-09-02 mega balance/content
- Professions **3.14.0**: xp-base much higher, tiers 10/25/50, Eff3 netherite (not Eff5@30)
- Achievements **1.6.0**: slower curves, start L1, kill_* mob mastery (5k/5 levels), combat still exists
- Hud **2.1.0**: %mavohud_level% %mavohud_level_line% %mavohud_deaths% %mavohud_tab_name%
- Goals **1.2.0**: multi-tier dirt/cobble/treasury
- Casino **1.2.3**: 10 games, random order, dual attempt pools (plugin.yml description fixed to "10 games"; jar rebuilt 2026-09-03)
- Tavern **1.1.0**: build20 + bar soulbound
- MobFarm **1.0.0**: NEW plugin
- Day reset: set world fullTime 0 (see DEPLOY-2026-09-02)
- After deploy: wipe progress + delete old configs so new defaults apply



--- 

# DEPLOY RULES (ALWAYS STATE THIS WHEN JARS CHANGE)
**REPLACE = jar files only. DELETE = old-version jars of each plugin (one jar per plugin), PLUS exactly
two config files this update: `plugins/MAVOGuide/config.yml` (v13→v14 content) and
`plugins/MAVOAchievements/config.yml` (43→52 categories, new mob kills). DELETE NOTHING ELSE.**
Full replace/delete/keep table incl. data.yml/homes.yml per plugin: `MAVOCRAFT-DEPLOY.md` in repo root.
Never folder-delete EconomyShopGUI / LuckPerms / TAB / MAVOWild. Full restart (no /reload) for jar swaps.
Post-start: `/updates reload`, `/museum shopsgen` + `/sreload` (ESGUI reload cmd, NOT /esgui), `/shopnpc resholo`, `/mobfarm resholo`.

---

# SECTION — 2026-09-03 GUIDE v14 + BIG JAR UPDATE
(deployed as of this backup; replace MAVOCRAFT-CONTEXT-REVIVAL.md sections with
these entries, or keep the file and append — this is the authoritative record)

## MAVOGuide 2.7.0 (config v14)
- What's New is now a menu: newest 3 on page 1 (pager: older pages, 6 per page, down to v2).
- Tutorial = 16 chapters CH0–CH15 in a 54-slot menu (CH0 newbie, CH1 Tavern, CH2 First Steps/wild,
  CH3 Survive, CH4 Earn, CH5 Villagers, CH6 Claims, CH7 Home, CH8 Lucky Coins, CH9 Graves,
  CH10 Casino (10 games), CH11 Museum (103 sections/1413 items), CH12 Server Events (10 events),
  CH13 Community Goals (+MobFarm chest), CH14 Vault & Portals, CH15 Mob Farm).
- Reader is paged (12 lines/page) so nothing flows off screen; Back/Prev/Next/Close buttons.
- "This Guide" pinned bottom-middle slot 49. All 26 features have names + shorts (null bugs fixed).
- Wild range text corrected to 5,000–400,000 everywhere.
- Professions tiers stated as Stone→Iron L10→Diamond L25→Netherite L50 (config comment fixed too).

## MAVOEvents 1.2.0
- 10 events: luckyhour, coinrain, mobhunt, fishingfrenzy, minersrush, harvestbonus, buildbonus,
  zombiesiege, giftdrop, farmfrenzy. /event [list|start <name>|stop]. admin = mavoevents.admin (OP).
- Zombie siege spawn uses getHighestBlockYAt() int API (fixed compile).

## MAVOLuckyCoins 1.5.5
- drop-chance 0.001 (1 in 1,000), drop-cadence-seconds 20 (max 1 coin/20s). Migration: configs
  without drop-cadence-seconds get 0.001 (was 1% = the ~20 coins/10min bug).
- Free coin via /ccollect every 10 MC days unchanged.

## MAVODeathChest 1.1.0
- /grave = GUI (54-slot list, 28/page, prev/next/close) with world + X/Y/Z + mins-left per grave.
- Click grave -> confirm: teleport for 1000 coins (Vault) OR 10 Lucky Coins (reflection countCoins/takeCoins).
- 3s countdown, cancel on move or monsters within 12 blocks, teleportAsync. Costs configurable.

## MAVOAchievements 1.7.2
- 52 categories (38 kill_): added kill_bee, kill_fox, kill_goat, kill_llama, kill_panda, kill_frog,
  kill_sniffer, kill_squid, kill_glow_squid (all Mob Farm mobs covered; aliases for husk/drowned/
  zombie_villager->zombie, stray/bogged/wither_skeleton->skeleton, cave_spider->spider,
  elder_guardian->guardian, magma_cube->slime).
- getLevel floors at 1: everyone shows Lv1 not Lv0 (baseline 0 = level 1).
- kill_* milestone coins 5000.

## MAVOProfessions 3.14.1
- level() returns Math.max(1, ...); new players initialise at level 1, not 0.
- Tier comment fixed to 10/25/50 (was stale 5/15/30). Tiers remain stone->iron L10->diamond L25->netherite L50.

## MAVOWanderer 1.1.0
- Real visits: scheduler spawns a wandering trader near a random online SURVIVAL player every
  spawn-minutes..spawn-max-minutes (default 30–60); despawn-minutes 10; 2 trader llamas; bell sound.
- /wanderer = info (all players), /wanderer spawn [player] = OP (mavowanderer.admin).
- ~80-offer recipe pool (utility/redstone/building/farming/brewing) - no junk.

## MAVOCurator 1.0.2
- /museum shopsgen (OP) writes plugins/EconomyShopGUI/sections/MAVOMuseum.yml AND
  shops/MAVOMuseum.yml (same name = linked). Real format: section = header (enable/title/slot/item/
  fill-item), shop = pages.pageN.gui-rows:6.items with material/buy/sell. 45 items/page, NO nav-bar
  override (inherits default PAGE_BACK/PAGE_NEXT). sell = max(1, buy*0.2). Prices tiered
  (netherite/dragon/beacon/template/star/elytra/totem/heavy_core/creaking_heart 4000; diamond/emerald/
  heart_of_sea/heads/skulls 800; gold/ancient_debris/scute/sponge/conduit/froglight/echo_shard 300;
  iron/redstone/lapis/quartz/amethyst/copper/blaze/slime/shulker/prismarine/experience 80;
  glowstone/magma/obsidian/end_*/_sherd/_banner_pattern 40; else 15).
- Requires EconomyShopGUI (re)load afterwards.

## MAVOWild 1.7.4
- min-radius default 5000 (migrates 2000), max 400000. Portal holo reads config (never drifts).
- /wild portal|homeportal|holoreset [radius] all admin (mavowild.admin).

## MAVOShopNPC 1.3.3
- Floating texts: scale 0.9->0.55, line width 200->140, shorter default texts.
- Boot refresh replaces old big texts (auto re-spawn; /shopnpc resholo or /holoreset also works).

## HOTFIX 2026-09-03 — MAVOWanderer 1.1.0 rebuilt
- Root cause: `plugins/MAVOWanderer` command description was UNQUOTED (`description: Trader info; admin: /wanderer spawn [player]`)
  → invalid YAML → whole plugin rejected ("Invalid plugin.yml", "mapping values are not allowed here").
- Fix: description quoted. Same version (1.1.0) — same filename, just replace the jar, keep `plugins/MAVOWanderer/config.yml`.
- Verified: CI run 33789140144 passed; rebuilt jar parses cleanly; all 12 jars' plugin.yml+config.yml now YAML-valid.

## 2026-09-03 HOTFIX 2 — Curator 1.0.2 + Guide 2.7.1 (v15)
- `/museum shopsgen` now **auto-runs `/sreload`** (console) and tells the admin IN CHAT:
  "Museum shop written: N pages (1413 items) and EconomyShopGUI reloaded.
   Expected: X section configs, Y shop configs. /shop > Museum Extras (slot 43)."
  (ESGUI itself never sends a chat confirm — only a console log; this fixes that gap.)
- Guide: config v15 + plugin 2.7.1 — v15 whatsnew entry; Museum chapter/feature say
  "shopsgen (auto-reloads)". New jar names: MAVOCurator-1.0.2.jar, MAVOGuide-2.7.1.jar.
- DEPLOY: delete old MAVOCurator-1.0.0/1.0.1 jars + MAVOGuide-2.6.0/2.7.0 jars; delete
  plugins/MAVOGuide/config.yml (v14→v15, saveDefaultConfig won't overwrite). Keep
  plugins/MAVOCurator/ (config.yml + data.yml) and MAVOGuide/data.yml.

## 2026-09-03 HOTFIX 3 — Curator 1.0.3: museum prices = NORMAL SHOP prices
- PROBLEM (found from user's EconomyShopGUI.tar): `/museum shopsgen` used a tier table (15/40/80/300/800/4000)
  but the real EconomyShopGUI shops use completely different values. 1,267 of 1,413 museum items differed
  (e.g. ANCIENT_DEBRIS: museum 300/60 vs real 62,320/1,600) → buy museum, sell normal = 5x+ profit (exploit).
- FIX: Curator now READS every normal shop YAML recursively at shopsgen time (skips MAVOMuseum.yml),
  maps MATERIAL -> {buy,sell}, and writes those exact numbers. If an item is in several shops with
  different prices (SPAWNER, ENCHANTED_BOOK, POTION variants, NOTE_BLOCK, SCUTE, NETHER_WART, INK_SAC,
  GLOW_INK_SAC = 12 materials), the CHEAPEST buy+sell wins → never a cheaper source than the normal shop.
- Result in chat: "Prices: 1268 items use the REAL shop prices (145 museum-only items use fallback)."
  Fallback = old tier table (museum-only items can't be arbitraged via normal shops).
- Also fixed: section/shop count now RECURSIVE (nested Combat/, Magic/, Farming/... folders) - shows 29/29.
- Normal shop sell prices are NOT 20% of buy (villager-first economy: sells are intentionally low);
  museum sell now = the real sell value, not a percentage.
- Jar: MAVOCurator-1.0.3.jar. Deploy: delete MAVOCurator-1.0.0/1.0.1/1.0.2 jars, keep plugins/MAVOCurator.
- NOTE: legacy MAVOMuseum.yml with tier prices already on disk is overwritten by the next /museum shopsgen.

## 2026-09-03 HOTFIX 4 — Curator 1.0.4 + Guide 2.7.2 (v16): Museum Extras per player
- `/museum > Museum Extras` (button slot 52 in main menu, or `/museum extras`): PER-PLAYER list of
  items the player has NOT donated. Buy-only (no selling), buy price = normal ESGUI shop price
  (indexed on enable/shopsgen; museum-only items fallback). Buy = Vault withdraw + 1 item into
  inventory; inventory-full refunds. Donated items DISAPPEAR from that player's list (data.yml per player).
- Old static ESGUI `sections/MAVOMuseum.yml` + `shops/MAVOMuseum.yml` are REMOVED by /museum shopsgen
  (cannot be per-player) - normal shops untouched. /sreload after removal.
- Completed sections: main menu button becomes a GREEN_STAINED_GLASS_PANE "✔ COMPLETE - reward paid",
  not clickable (click = "already complete" message). No section refund/re-entry.
- Donated items in category view now show lore "✔ Already donated to the museum! Do NOT add this to the crate."
- Deposit crate already sends "✘ Already in the museum (returned): <items>" per donation pass (existing 1.0.2 behavior).
- Deploy: replace MAVOCurator-1.0.4.jar + MAVOGuide-2.7.2.jar; DELETE plugins/MAVOGuide/config.yml (v15->v16,
  saveDefaultConfig never overwrites); keep plugins/MAVOCurator/config.yml + data.yml (donations per player).
- After deploy: /museum shopsgen (removes old ESGUI files + indexes prices) then players open /museum extras.

## 2026-09-03 HOTFIX 5 — Curator 1.0.5 + Guide 2.7.3 (v17): stale museum shop auto-removed
- SYMPTOM (user boot 19:38): MAVOGuide + MAVOCurator NOT in plugin list at all (36 Bukkit plugins;
  no "Enabling" lines) -> /museum dead, old MAVOMuseum.yml still in EconomyShopGUI with cheap tier
  prices (ESGUI loaded 29 section configs incl. Museum Extras).
- Cause: jars not present/loadable in plugins/ (panel side - files valid, CI-built, verified locally).
  Check: exactly one jar per plugin in plugins/ ROOT, correct names, sizes (curator 34176 B, guide 24619 B).
- FIX IN CODE (defence-in-depth): Curator 1.0.5 now REMOVES the stale static ESGUI museum files
  (sections/MAVOMuseum.yml + shops/MAVOMuseum.yml) on BOOT (onEnable) and logs
  "Removed old Museum Extras shop files (2)..." + /museum shopsgen still re-indexes prices.
- Guide 2.7.3 v17: whatsnew entry "Museum Extras cleanup" (v17).
- Deploy: replace MAVOCurator-1.0.4->1.0.5 + MAVOGuide-2.7.2->2.7.3; DELETE plugins/MAVOGuide/config.yml
  (v16->v17); keep plugins/MAVOCurator/{config.yml,data.yml}; keep MAVOGuide/data.yml.
- AFTER DEPLOY the boot log MUST show "MAVOGuide 2.7.3" + "MAVOCurator 1.0.5" in the plugin list and
  "MAVOCurator enabled: 103 exhibit sections, 1413 collectable items." - then /shop has NO Museum
  Extras (28 configs) and /museum extras (per player) works.

## 2026-09-03 HOTFIX 6 — Fix batch v18 (Curator 1.0.6 / Guide 2.8.0 v18 / DeathChest 1.1.1 / Casino 1.2.4 / Lucky 1.5.6 / MobFarm 2.6.0 / PortalRoom 1.2.0)
CI run 33814070963 SUCCESS. Commit chain: f6a14d7 (sources) -> 7a0c525 (ci: rebuilt plugin jars) -> docs+zip commit (this file updated after).

- **Museum Extras P1 (user-approved behavior):** clicking an item in `/museum extras` DEDUCTS the price, registers it into the museum immediately (register()), auto-refreshes the GUI, GUI STAYS OPEN. Item no longer enters inventory; message says "added to the museum (N/1413)". Survival-only (same as crating). buy-only stays; per-player list stays.
- **Curator deposit crate:** duplicate/protected/unknown items now BOUNCE BACK the instant they are placed — validateVault() runs one tick after every vault click (was close-time only). Chat: "Already in the museum (returned): ..." / "The Curator refused (returned): ...".
- **Curator:** `/museum` tab-complete now offers `extras` for everyone (reload/shopsgen still OP-only). Curator also writes `plugins/MAVOCurator/materials.txt` (1413 material names) on buildRegistry — consumed by MAVOLuckyCoins /destroy protection.
- **DeathChest 1.1.1:** defaults + runtime fallback = teleport 5,000 coins / 100 Lucky Coins; migration resets old 1,000/10 configs on first boot (log "Migrated grave teleport costs -> 5000 coins / 100 lucky coins.").
- **Casino 1.2.4:** COIN_BETS = 1,2,4,...,524288,1,000,000 (doubles per + click, 21 steps); LUCKY_BETS = 1..50 (+1 per click). GUI lore updated ("Coins: 1 - 1,000,000 (doubles on +) / Lucky Coins: 1 - 50 (+1 each)").
- **MobFarm 2.6.0 economy:** entry 10,000 · session 15 min · pick = that mob's REAL shop spawner buy price / 16 (zombie 1.2M -> 75,000; config `shop-buy` mirrors ESGUI shops/Mobs/spawners.yml for all 36 mobs, fallback = normal-spawner-price/16) · each ADDITIONAL paid pick in the same session doubles (75k->150k->300k) · `/mobfarm extend` = 25,000 coins for +15 min added to endsAtMs AND s.totalMs (HUD countdown + progress update immediately) · stack costs now use the mob's shop price (/8,/4,/2,x1 then double) · auto-migration on boot: entry 5000->10000, session 30->15, copyDefaults adds shop-buy + extend keys.
- **MobFarm GUIs:** `/mobfarm pick` = 2 pages (Hostile 1/2 | Farm animals 2/2), alphabetical, SPAWN EGG icons with per-mob pick cost + "next pick" price, nav arrow slot 49, info clock slot 50 with balance; `/mobfarm prices` = same 2 pages read-only with shop price / pick #1-3 / stack extras. Chat fallback only for console. Title match used by click handler: "MobFarm Pick" / "MobFarm Prices".
- **MobFarm hub hologram** now shows: Entry 10,000 · 15m / Pick from 75,000 (shop price/16, doubles) / Extend 25,000/+15m.
- **LuckyCoins 1.5.6:** `/destroy` (inventory) and `/destroy hand` wipe only UNSALEABLE items (not in well-pool/shop) — NEVER lucky coins, profession tools (`mavoprofessions:proflock|proftool` PDC), renamed/enchanted items, valuable material types (netherite/diamond/emerald/eggs/spawners/discs/elytra/totems/beacon/nether star/dragon egg/heads/horse armor/all armor/tools/enchanted books/trident/shield/bows/crossbow/rod/shears/flint/compass/shulker boxes/chest boats), or **museum items the player has not donated yet** (reads Curator materials.txt + data.yml). `/destroyall` clears main inventory slots 9-35, keeps hotbar + offhand shield + lucky coins + profession tools. All messages say what was destroyed; nothing destroyed = info message.
- **Guide 2.8.0 v18:** reader pager = "◀ Previous" (slot 18) / "Next ▶" (slot 22) — no more "newer/older page". CH9 Dying & Graves: 5,000 coins / 100 Lucky Coins; 30-minute warning + villager tip moved to PAGE 2 (line 12 ends page 1 with "Page 2: the 30-minute lock & a tip →"). CH10 Casino: "Bets: 1-1,000,000 coins (doubles on +) or 1-50 Lucky Coins (+1 per click)." CH14 Portal Room: biomes 1,000-10,000 unchanged; danger dives 25,000-5,000,000 (stronghold 500,000 / trial 1,000,000 / deep dark 5,000,000). CH15 Mob Farm + feature page updated to 10k/15m/P÷16/extends. What's New v18 entry at top (auto-opens once).
- **MAVOPortalRoom 1.2.0** (binary-patched, NO source in repo): all 13 SOUTH danger-dive portal prices changed by patching the Utf8 constants inside PortalRoom.class (validated: class pool well-formed, old prices gone, plugin.yml version 1.2.0). Prices: shipwreck 25,000 · mineshaft 50,000 · dripstone 75,000 · lush 100,000 · pyramid 150,000 · witch hut 175,000 · jungle temple 200,000 · deep caves 250,000 · outpost 300,000 · monument 400,000 · stronghold 500,000 · trial chambers 1,000,000 · deep dark 5,000,000. Biome (NORTH) prices unchanged. Do NOT rebuild this plugin from sources — there is no source module; keep the patched jar.
- **Deploy:** replace 7 jars (see MAVOCRAFT-DEPLOY.md §2); DELETE only plugins/MAVOGuide/config.yml (v17->v18); keep MAVOGuide/data.yml, MAVOCurator/{config,data}.yml (materials.txt auto-created), MAVOMobFarm/{config,data}.yml (auto-migrates), MAVODeathChest/config.yml (auto-migrates), MAVOCasino/{config,data}.yml, MAVOLuckyCoins/{config,data}.yml.
- **POST-DEPLOY:** /updates reload; /museum shopsgen (Curator 1.0.6); /mobfarm resholo; verify boot log lines (plugin versions + "Migrated MobFarm economy..." + "Migrated grave teleport costs..." + Curator "103 exhibit sections, 1413 collectable items").
- **Zip:** MAVOcraft-backup.zip refreshed (sources, all jars incl. PortalRoom 1.2.0, docs). SHA is in MAVOcraft-backup.sha256 + the commit message.

## 2026-09-04 HOTFIX 7 — MobFarm 2.6.1 prices + PortalRoom 1.2.0 reload fix (user-deployed v18 found 2 bugs)
- **USER TESTED v18 (deployed):** everything ✅ except 2 things: (1) MobFarm holo/pick said 625 and "each spawner is 10K base price" — the shop-buy map from the jar config never reached the live config (saveDefaultConfig does not overwrite; 2.6.0's migration only wrote entry/session keys), so shopBuy() fell back to normal-spawner-price/16 = 625. (2) Portal Room prices unchanged — the 1.2.0 jar FAILED TO LOAD: "Unknown constant tag 33" (my previous constant-pool patch had a slice bug that duplicated bytes). Old holos stayed because the plugin never loaded.
- **MobFarm 2.6.1 fix:** SHOP_BUY map (36 mobs, real ESGUI spawner prices: ZOMBIE 1,200,000, IRON_GOLEM 10,000,000, FROG 3,950,000 …) is now EMBEDDED in code — shopBuy() uses config override, else embedded, else normalPrice. onEnable now also WRITES any missing shop-buy.* values into config.yml (so the player-visible config matches code) and logs "MobFarm shop prices embedded: 36 mobs (zombie pick 75000)." — never 625 again. Version 2.6.1 (pom/plugin.yml/class log/info). Config comments updated; keep config+data files (auto-migrates).
- **PortalRoom 1.2.0 fix:** rebuilt from the ORIGINAL class (git ae608e3:MAVOPortalRoom-1.1.0.jar) with a corrected builder that splices the pool in entry order and appends the rest verbatim. Verified: strict constant-pool reparse OK, cp 2054, only the 13 SOUTH row strings differ (each price field only), post-pool 19,781 bytes byte-identical, old prices gone / new present, NORTH rows untouched (desert 1000 / cherry 7000 / mushroom 10000). Then CI gate added: workflow runs `javap -v` + string checks on MAVOPortalRoom-1.2.0.jar every build. CI run 33825081229 passed; class verified in shipped jar (strings show 500000/1000000/5000000/75000 etc.).
- **Casino:** only the enable log string fixed to "MAVOCasino 1.2.4 enabled" (the jar was already 1.2.4; log said 1.2.3). No behavior change.
- **Deploy (this fix):** STOP; delete MAVOMobFarm-2.6.0.jar + MAVOPortalRoom-1.2.0.jar (broken) + MAVOCasino-1.2.3.jar if present; upload MAVOMobFarm-2.6.1.jar + MAVOPortalRoom-1.2.0.jar (fixed) + MAVOCasino-1.2.4.jar; keep ALL configs (Guide stays v18); /mobfarm resholo after boot. Boot must show "MAVOMobFarm 2.6.1" + "shop prices embedded: 36 mobs (zombie pick 75000)" and NO PortalRoom load error.
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg. Jars in repo: MAVOMobFarm-2.6.1.jar (57,6xx B), MAVOPortalRoom-1.2.0.jar (28,069 B), MAVOCasino-1.2.4.jar.

## 2026-09-04 HOTFIX 8 — MobFarm 2.6.2 community chests (user-deployed re-test found bay chest bug)
- **USER TESTED after HOTFIX 7:** everything ✅ EXCEPT MobFarm bay community chests — only the hub chest worked; every bay chest was "facing wrong" and rendered as TWO SINGLE chests instead of one double chest.
- **Root cause (in code, also present in 2.5.0):** `placeDoubleChest()` hardcoded x=LEFT / x+1=RIGHT for EVERY facing. Bukkit Chest.Type LEFT/RIGHT are relative to the chest itself (opposite to a player's view), so that pairing is correct only for NORTH. All SOUTH pairs (bay community chests + loot rows) placed the halves the wrong way round → Bukkit did not link them → 2 single chests. The bay chests were also placed facing SOUTH (opened into the wall) while players stand on the walkway NORTH of them.
- **2.6.2 fix:** `placeDoubleChest()` is now facing-aware (NORTH → x=LEFT/x+1=RIGHT; SOUTH → x=RIGHT/x+1=LEFT), sets SINGLE + physics ON so vanilla re-pairs the double, then explicitly re-sets correct LEFT/RIGHT halves as a fallback for servers that skip re-pairing. Bay community chests changed SOUTH → NORTH like the hub. FIRST CI run (9ebbe5b) FAILED compile: `DoubleChest.getLeftSide()` returns `InventoryHolder` which has no `getLocation()` — fixed by using `DoubleChest.getLocation()` + each half's `getInventory().getLocation()` (7a256a3, CI 33827522758 passed with only a deprecation warning).
- **Donation detection hardening (`onCommunityClose`):** `Inventory.getLocation()` is unreliable for double-chest inventories (Skript#4681; Paper delegates to NMS), so the close handler now: (1) tries `top.getLocation()`; (2) if the holder is `DoubleChest`, tries `dc.getLocation()` then each side's inventory location; (3) position match is tolerant (x ≤ 1, z ≤ 1 for bays; hub z ≤ 1). No behavior change for the hub.
- **Deploy (this fix):** STOP; delete MAVOMobFarm-2.6.1.jar; upload MAVOMobFarm-2.6.2.jar; keep ALL configs/data (no migration); /mobfarm resholo after boot. Boot log must show "MAVOMobFarm 2.6.2 enabled". Bay chests: ONE double chest per bay, opens toward the walkway (NORTH).
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg. Jars in repo: MAVOMobFarm-2.6.2.jar (59,291 B), MAVOPortalRoom-1.2.0.jar, MAVOCasino-1.2.4.jar.

## 2026-09-04 HOTFIX 9 — MobFarm 2.6.3 + Professions 3.14.2 + AuctionHouse 1.0.0 (user re-test: chests still singles, mobs escape, hoe blocked)
- **USER TESTED 2.6.2:** boot log 2.6.2 OK; hub chest/donations OK; **bay chests still showed as separate singles ("looks like 2 double chests")**, bay chest not facing the walkway, legacy singles left, loot rows broken; **zombies escaping the cell** (mobs not stuck, walked out and attacked); "All mobfarm must be special design - looks all the same, acts the same" (item 12); bound hoe right-click on dirt near water threw "That's a bound profession item - you can't place it!" (profession not counting); + full Auction House spec (see below).
- **MobFarm 2.6.3 chests:** `placeDoubleChest` now writes the FINAL LEFT/RIGHT halves with NO intermediate SINGLE state (physics off, then physics on to validate), plus a next-tick verify + re-apply - no server moment to un-merge. Every loot point is ONE true double chest: killCell hopper columns x=-1/0 chain SOUTH into the chest at (cx-1..cx), column x=+1 faces WEST into column 0 (no orphan single); barn chest line same; cells bay now reuses the shared pit + glass cubby dividers (old open aisle at x=0 closed - that was the escape route). Loot slot: 1-deep pickup floor at y=cy-2 in front of the double chest (clickable); community chest stays NORTH.
- **MobFarm 2.6.3 distinct designs (item 12):** crypt = slab+slit mossy dark (zombie/husk/drowned/witch); gallery = bars, 4-deep, target wall (skeleton/stray/wither skel); bunker = trapdoor slit 4-deep obsidian + shell (creeper); web = fence slit + fenced walls spiders can't climb + webs (spider/cave); totem = purple pane + purpur pillars + ender chest (enderman); forge = bars + magma + nether wart (blaze); cells = pane + glass dividers (slime/magma/silverfish); arena = open-top 8-high court, phantoms AI OFF so they stay low/hittable; aqua = water pool + light-blue pane + kelp (guardian/drowned/squid); brutal = bars 4-deep blackstone + gold (hoglin/piglin); barn = fenced pen + water trough + hay + bars grate (animals). Themed spawner pedestals per style.
- **MobFarm 2.6.3 safety:** per-style cell box containment - any farm mob outside is teleported back to the kill pad every tick-check; `onFarmAttack` cancels ALL damage from farm mobs AND their projectiles (arrows/trident/fireball) to players; enderman teleport already cancelled.
- **Professions 3.14.2:** `onPlaceBound` only blocks bound BLOCK items (bamboo sticks etc.) - tool USES (hoe tilling, paths, stripping) are no longer blocked, so a bound hoe tills dirt next to water and Farmer XP is granted (0.5 per till, 1.0 on harvest).
- **MAVOAuctionHouse 1.0.0 (NEW plugin):** `/ah` `/auctionhouse` `/auction` + `/inbox`; 5 keeper villagers (config) in a bedrock-box auction house (`/ah setcenter` then `/ah build`, OP) - all open the same GUI; 20% tax posted by command, 5% posted at the AH (inside region + villager touch); 1 slot free, unlock up to 20 (50k/100 Lucky, 100k/200, 150k/300 ...); `/ah add hand <amount> <price> <duration>` (also `hand <material>`) 1h-48h with countdowns; successful buy -> buyer inbox tag "auction" (item bound - cannot be re-listed); expired -> seller inbox "auction expired" (NO tax, NO cooldown); cancel -> inbox "auction cancelled" (no cost, no cooldown); successful sale locks ONLY that slot 10 Minecraft days; min price = shop SELL price x110% (runtime scan of plugins/EconomyShopGUI/shops + config `min-sell-prices` overrides); price cap 1B; inbox max 100 with Collect All; offline payouts held in data.yml and paid on next join.
- **CI:** 4 runs on this change (2 failed on compile: `Configuration.getRoot()` map walk in AuctionHouse + missing `Trapdoor` block-data type in 1.21 API -> generic Directional/Bisected/Openable; then `Block.isBlockLoaded()` missing -> `getChunk().isLoaded()`). Final run 33895497309 SUCCESS on 14efdb1 (only deprecation warnings). Commit chain 481f4a9 -> f4ab974 -> 9f27660 (ci log fail) -> 4e0708f -> 0eb0d64 (ci log fail) -> b5c6b90 -> 14efdb1 -> 90dda97 (ci: rebuilt plugin jars).
- **Deploy (this update):** STOP; delete MAVOMobFarm-2.6.2.jar + MAVOProfessions-3.14.1.jar; upload MAVOMobFarm-2.6.3.jar + MAVOProfessions-3.14.2.jar + MAVOAuctionHouse-1.0.0.jar; keep ALL configs (MobFarm/Professions auto; AH has none yet - its data.yml is created on first run). After boot: `/mobfarm resholo`; `/ah setcenter` (OP) then `/ah build`; `/updates reload`; boot log must show MAVOMobFarm 2.6.3, MAVOProfessions 3.14.2, MAVOAuctionHouse 1.0.0 enabled.
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg.
