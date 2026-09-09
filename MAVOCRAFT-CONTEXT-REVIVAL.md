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
   update MAVOGuide in ALL THREE places - Guide (feature pages), Tutorials
   (chapters) and Updates (What's New entry) - bump the guide version, then
   rebuild MAVOGuide-x.y.z.jar and deliver it alongside the feature jar.
   (User: "always create things into Guide, Tutorials and Updates and give
   those jars too.")
2. The Discord pack (DISCORD-PACK-2026-09-07.md) MUST be updated EVERY time
   something gets done - version-log block + affected feature threads.
3. After EVERY delivered update: rebuild MAVOcraft-backup.zip (jars/ docs/
   sources/), print its SHA-256, and append what changed to this document.
   User uploads the backup to GitHub - it is the disaster-recovery source.
4. This document is the single source of truth for reviving context.

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

## 2026-09-04 HOTFIX 10 — MobFarm 2.6.4 (loot reachable + every bay individually designed) + AuctionHouse 1.0.1 (keepers work)
- **USER TESTED 2.6.3:** hub + bay community chests merge and face correctly ✅; donations ✅; mobs never on walkway ✅; professions hoe ✅ all 3; AH: /ah GUI, posting, slots, cancel, protection ✅ BUT: loot chests covered/unreachable (1-deep slot with a slab above), all bays still copy-paste, AH keepers 1 block sunk + opened NOVICE TRADES instead of the AH, /ah slots not in tab-complete, boot log shopSell=0 (min-price floor disabled).
- **MobFarm 2.6.4:** killCell v4 restores the reachable loot (chest lid flush with the trench floor - click it in front of you; no more covered slot). Item 12: every bay individually designed - per-entity specs (depth 3-5, ceiling/open sky, slab/bars/trapdoor/fence/pane/glass window, water 1-2) + per-mob props: husk = open sunlit sand tomb + cactus; drowned = flooded crypt + kelp; witch = barred swamp den; skeleton = deep gallery + targets; stray = frozen gallery; wither skeleton = soul crypt; pillager = dark-oak raider cage + wool banners; spider = web cage + cobwebs; cave spider = 5-deep cave den; creeper = 5-deep obsidian blast vault + trapdoors; enderman = tall totem hall + purple pane; blaze = open-sky forge; slime = swamp pond + lily cells; magma cube = magma cells; silverfish = stone maze + infested stone; phantom = tall open court (AI off); guardian/squid/glow squid = flooded kelp tanks (guardian prismarine, glow squid sea lanterns); hoglin/piglin = blackstone gold fortresses; 14 unique pastures (cow meadow+trough, pig mud+hay, chicken roofed coop, sheep wool pen, rabbit carrot warren, villager village well, iron golem plaza+statue, bee flower meadow+beehives, fox berry grove, goat ice mountain, llama carpet caravan, panda bamboo grove, frog lily pond, sniffer torchflower dig). Containment cellBoxes match each layout (pads: water = cy+0.4, pen = cy+3.2).
- **AuctionHouse 1.0.1:** keepers open the AH GUI - the PDC keeper tag is set as STRING but the event/damage checks asked for BYTE, so the cancel never fired and vanilla trades opened (STR-ING check fixed everywhere). Keepers no longer sink: spawn y = floor top (cy+1) with pedestal under them; respawn drops sunk 1.0.0 keepers and fixes stored y. shopSell scan rewritten (flat getValues(true)) - boot log now says "scanned N EconomyShopGUI yml files -> M prices (netherite ingot X)" so min-price x110% is enforced again. /ah tab-complete (add/inbox/slots/cancel/help, durations, own listing IDs).
- **CI:** 1 failed run (illegal escape `\.` in materialFromPath split; fixed e4d7bfa), final run 33902754719 SUCCESS on e4d7bfa. Commit chain 5dcbee9 -> 08c4b24 -> 5298b14 (ci log fail) -> e4d7bfa -> ffd9e6a (ci: rebuilt plugin jars).
- **Deploy (this update):** STOP; delete MAVOMobFarm-2.6.3.jar + MAVOAuctionHouse-1.0.0.jar; upload MAVOMobFarm-2.6.4.jar + MAVOAuctionHouse-1.0.1.jar; keep all configs + AH data.yml. After boot: /mobfarm rebuild (REQUIRED to get new bays + reachable loot); /ah build (REQUIRED to fix keeper level + trades); /updates reload. Boot log: MAVOMobFarm 2.6.4, MAVOAuctionHouse 1.0.1 enabled + "scanned ... yml files".
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg.

## 2026-09-04 HOTFIX 11 — MobFarm 2.6.5 (rebuild crash) + AuctionHouse 1.0.2 (/ah hub + sell price scan)
- **USER DEPLOYED 2.6.4:** `/mobfarm rebuild` crashed: `java.lang.IllegalArgumentException: Provided material must be a block` at MobFarm.decorPasture line ~1309 — `Material.CARROT` is an ITEM, not the block (`CARROTS`); it killed the whole rebuild command.
- **MobFarm 2.6.5:** rabbit warren uses `CARROTS` (block). All bay decor now goes through `setBlock()` which checks `Material.isBlock()` and never throws, so no future item-vs-block typo can crash a rebuild. `buildComplex` also wraps each `buildBay` in try/catch - one bad bay logs a warning, the other 35 still build.
- **AuctionHouse 1.0.2:** added `/ah hub` (alias `/ah home`) - teleports to the auction house inside the box (any player; no more needing OP /tp), in tab-complete + help. Shop price scan corrected after boot showed 6 ERROR stack traces ("Cannot load plugins/EconomyShopGUI/shops-backup-2026082801/...") and netherite 5327 instead of 756: scanner now ONLY reads the live `EconomyShopGUI/shops` tree, skips any path containing "backup", reads only the real leaf `sell` key (ESGUI wiki: `sell` = the price players get; `sell-prices` = multi-currency list - skipped), resolves the material from the `shop-items.minecraft:xxx.sell` path OR the sibling `material:` value in `pages/…/items/N` format, and keeps the HIGHEST price per duplicate (matches ESGUI's own /sellall priority). `min-sell-prices` config still overrides. Boot log: "scanned N shop yml files (backups skipped) -> M prices (netherite ingot X)".
- **CI:** run 33911449968 SUCCESS on 3be3867 (only deprecation warnings). Commit chain d4001d6 -> 3be3867 -> 36b6da1 (ci: rebuilt plugin jars).
- **Deploy (this update):** STOP; delete MAVOMobFarm-2.6.4.jar + MAVOAuctionHouse-1.0.1.jar; upload MAVOMobFarm-2.6.5.jar + MAVOAuctionHouse-1.0.2.jar; keep ALL configs/data. After boot: /mobfarm rebuild (now safe - will finish), /ah build (re-place keepers if needed), /updates reload. Boot log: MAVOMobFarm 2.6.5 + "shop sell: scanned ... (backups skipped)" + netherite ingot price you expect (override with min-sell-prices if it differs).
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg.

## 2026-09-04 HOTFIX 12 — MobFarm 2.6.6 (real bay barriers + true per-bay shapes + signs face walkway) + AuctionHouse 1.0.3 (keeper dedupe + enclosed terrace)
- **USER TESTED 2.6.5:** five defects — (1) AH keepers DUPLICATED, a stuck copy stays at each spot; (2) MobFarm loot/community signs face the wrong way (not toward the walkway); (3) AH bedrock box has an open edge, players can fall off; (4) all MobFarm bays still look the same — block/decor swaps not enough, different shape/mode per mob required; (5) zombies walk out of the bay — no physical barrier.
- **ROOT CAUSE found for (4)+(5), ONE bug:** in `killCell` the front wall (slab slit + per-style barrier at z=+1) was built FIRST, then the trench loop did `setAir` over z=+1..+3 and WIPED it — every cell's player side was an open 3×3 hole, so mobs walked out AND all bays looked like the same open box. killCell v5: trench floor built first, FRONT WALL placed AFTER floors and never overwritten, trench air cleared ONLY at z=+2..+3, porch kept at z=+4..+6. The 1-high melee window (bottom slab, barrier, top slab) now really exists. Slit width per style: 3-wide (crypt/gallery/web/forge/cells/arena/aqua/brutal), 1-wide peep (bunker trapdoor, totem pane) and narrow pane/glass windows (aqua, cells).
- **MobFarm 2.6.6 unique shapes (2):** barns rewritten with `PenSpec(EntityType)` — per-animal half (3×3 or 4×4), fence height 1-3, fence material (oak/stone brick/iron block/packed ice) and roof pattern (none/full/half/corners): chicken & bee = full-roof hut, rabbit/fox/panda = half-roof den, villager & iron golem = corner-pillar plaza (golem 2-high iron pillars), goat = 3-high packed-ice arena, frog = water pit, pig = 3×3 mud pen, cow/sheep/llama/sniffer = open meadows (4×4, 1-high). These change the SILHOUETTE, not just blocks. All hostile styles keep their already-distinct depth/roof/water/front + per-mob props from 2.6.4/2.6.5.
- **MobFarm 2.6.6 signs face walkway (3):** new `faceSign()` sets rotation directly (Rotatable for standing signs, Directional for wall signs) — bay/zone/HIT signs face NORTH (read from the trench side), loot sign faces SOUTH (read from the trench). Standing sign default south rotation was the old mis-facing bug.
- **AuctionHouse 1.0.3 keeper dedupe (1):** root cause — 1.0.1's `/ah build` removed old keepers checking BYTE type while the tag is STRING, AND boot-time cleanup ran when the AH chunk was unloaded so old tagged villagers were never seen; `/ah build` then spawned new ones on top. `respawnKeepers()` now loads each keeper's chunk (`getChunk().load()`), removes EVERY villager with the STRING keeper tag within 4 blocks of the spawn point, then spawns one fresh keeper; logs `removed N duplicate/stuck keeper(s)` when it happened. Runs on enable AND `/ah build`.
- **AuctionHouse 1.0.3 enclosed terrace (5):** entrance now has a 5×3-floor balcony (polished blackstone) with 2-high obsidian parapets on both sides + the south edge, sea-lantern posts, and a 1-block step; the protection region was extended over the terrace (az <= regionHalf+4, y <= cy+3) so nobody can fall off the floating box and the terrace is safe.
- **Refined before ship:** cells bay slit was \"slab\" which hit the switch default -> OPEN 1.5-high front window (small slimes/silverfish could hop through); changed to \"pane\" (real glass-pane barrier in the 3-wide window, matches the intended cells design, players still hit through it). The AH terrace step block outside the parapet was removed (pointless fall perch); the balcony is sealed on all sides except the 3-wide door and the region covers only the parapets.
- **CI:** first run 33914686502 SUCCESS on 25efabe (only deprecation warnings), final run 33915101942 SUCCESS on 0782796. Commit chain 25efabe -> 96898d9 (ci: rebuilt plugin jars) -> 1cb97fe (finalize) -> 0782796 (refine) -> 05b8ac3 (ci: rebuilt plugin jars).
- **Deploy (this update):** STOP; delete MAVOMobFarm-2.6.5.jar + MAVOAuctionHouse-1.0.2.jar; upload MAVOMobFarm-2.6.6.jar + MAVOAuctionHouse-1.0.3.jar; keep ALL configs + AH data.yml. After boot: `/mobfarm purge` then `/mobfarm clear` then `/mobfarm setcenter` (x2, once per range) then `/mobfarm build`; `/ah build` (dedupes + replaces keepers, adds terrace). Boot log: MAVOMobFarm 2.6.6 + MAVOAuctionHouse 1.0.3 enabled + "shop sell: scanned ... (backups skipped)" + (log line if duplicates were removed).
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg.

## 2026-09-04 HOTFIX 13 — MobFarm 2.7.0 (per-mob `/mobfarm build <mob>` + per-mob datapack + 36 hand-built unique structures)
- **USER TESTED 2.6.6:** "All still same" — every bay still shared the killCell box (only blocks/decor differed); the mystery floating LADDER at each bay corner (finishBayCommon's "ladder back to path", floating at the void edge); loot chest UNREACHABLE again (2.6.6's closed front wall hid the chest behind the slab line); user's new workflow request: separate build per mob (build zombie / build husk / ...) with one datapack per mob so a bay can be cleared+rebuilt without purging everything ("delete what that datapack built, wait 5s, reload datapack, apply new build").
- **MobFarm 2.7.0 commands:** `/mobfarm build <mobid>` / `/mobfarm rebuild <mobid>` (tab-completes all 36 ids: zombie, husk, cave_spider, wither_skeleton, iron_golem, glow_squid...). Flow per mob: runs the mob's old pack clear function (fill bay box air) -> waits 5s -> Java rebuild -> re-writes world/datapacks/<id>-datapack.zip -> datapack disable+enable (reload) -> runs `function mavomobfarm:<id>/build` (all block states) -> plugin patches chest pair / spawner type / sign text (setblock cannot carry those). `/mobfarm build` (no arg) still builds everything (full first-time build, no waits); `/mobfarm rebuild` (no arg) = clear+build all; purge/clear/setcenter unchanged.
- **36 unique structures (BayGeometry.java, one hand-built layout per mob):** zombie = sunken octagonal mossy crypt; husk = 3-tier sandstone DESERT PYRAMID with a 1-wide shaft; drowned = flooded prismarine shrine (water 2, glass); witch = raised dark-oak swamp hut on mushroom stems; skeleton = 2-storey quartz gallery tower; stray = full packed-ice IGLOO DOME with glass window; wither skeleton = tapering nether-brick soul spire (1-wide purple pane slit); pillager = dark-oak raider cage on posts with banners; spider = wide fence web cage (5-wide, cobwebs); cave spider = deep burrow with stepped slab roof; creeper = thick obsidian blast vault (trapdoor slit + blast doors); enderman = purpur obelisk hall with end rods; blaze = open-sky forge courtyard with magma ring; slime = round swamp basin (water 1, lily pads); magma cube = stepped round caldera (glowstone ring); silverfish = stone maze with infested walls; phantom = tall octagonal sky court (8-high); guardian = prismarine aquarium DOME; squid = glass round tank with prismarine pillars; glow squid = dark-prismarine glow tank; hoglin = crimson fortress (crenellations); piglin = gold-trimmed bastion with corner towers; animals: cow open meadow+trough+tree, pig roofed mud sty (ridge), chicken raised coop (roost+nest), sheep white-wool pen, rabbit warren+mound+carrots, villager plaza (well+2 mini houses), iron golem court (iron pillars+poppies), bee apiary (3 beehives+flowers), fox moss den+berry bushes, goat stepped ice peak (3-high blue-ice), llama caravan (2 wool tents+carpets), panda bamboo grove+leaf canopy, frog round lily pond (fountain), sniffer dig site crater (torchflowers).
- **Kill method + positions identical everywhere:** hopper floor feeds ONE double loot chest that now sits ON the trench floor at z=+1, front face flush with the player's footing - fully clickable (the 2.7.0 loot fix; the chest is placed AFTER the trench floor so nothing can overwrite it). 2-high see-through barrier window on top of it (bars/fence/pane/glass/trapdoor per bay; full-block barriers so tiny slimes cannot hop through; aquatic bays use full GLASS so water stays in). Walkway (sea-lantern checker z=+4..+7), ZONE/HIT/COMMUNITY signs NORTH, LOOT sign SOUTH on the chest, spawner pedestal at (cx+6,cz-2) with its own floor pad, community chest pad added, ladder REMOVED. Containment per bay = m.cell {hx,hz,minY,maxY} set by each builder (fallback box if absent).
- **Datapacks:** world/datapacks/<id>-datapack.zip = pack.mcmeta (pack_format 48 + supported_formats 48..999) + data/mavomobfarm/function/<id>/{clear,build}.mcfunction; clear = one /fill air over the bay box (x±12, y-10..+16, z-12..+10); build = ~1-3k setblock lines from a block-state snapshot; chunk loads forced before functions; function + datapack commands dispatched via console. If a datapack cannot be enabled on the server, the Java build still happened (double-apply makes the bay correct either way) - the log warns "applied X via X-datapack.zip" only on success.
- **CI:** run 33918674013 FAILED (compile: MobDef is a nested class - BayGeometry needed `import mavo.mobfarm.MobFarm.MobDef`; fixed 6fcd45e), run 33918934184 SUCCESS on 6fcd45e. Commit chain a9be396 -> 4b9bc91 (ci: publish build log) -> 6fcd45e -> cac10c5 (ci: rebuilt plugin jars).
- **Deploy (this update):** STOP; delete MAVOMobFarm-2.6.6.jar; upload MAVOMobFarm-2.7.0.jar; keep ALL configs + data.yml. After boot: /mobfarm purge -> /mobfarm clear -> /mobfarm setcenter (x2, per range) -> /mobfarm build (full first build; writes the 36 datapacks). THEN iterate per mob with /mobfarm build zombie etc. (5s each, other 35 bays untouched). Boot log: MAVOMobFarm 2.7.0 enabled.
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg.

## 2026-09-04 HOTFIX 14 — MobFarm 2.7.1 (field test: packs actually load + real kill pad + sealed bays + `/mobfarm <mob> save`)
- **USER DEPLOYED 2.7.0 and FIELD-TESTED (2026-09-04 21:07+):** boot OK (mobs=36 center=-15000,-2000). `MAVOMobFarm: wrote <id>-datapack.zip` did NOT appear as loadable: `/mobfarm build` logged `Unknown data pack 'file/<id>-datapack'` + `Unknown function mavomobfarm:<id>/build` for all 36, and the file panel showed only MAVOcraft-builder-datapack.zip - yet the plugin then printed "applied zombie build via zombie-datapack.zip" (FALSE - the function never ran). In-game: zombies still reached the walkway/player, the HIT stand (z=+6.5) was too far from mobs and the HIT sign out of view.
- **ROOT CAUSE of the unloadable packs:** the zip mcmeta used `pack_format: 48 + supported_formats 48..999` - Paper 26.2 ERRORS on format RANGES and old formats (the known-good MAVOcraft-builder-datapack.zip uses pack_format 81 EXACT, no range). Range-less 81 is now used. Also: newly-written packs are only scanned at STARTUP, so `datapack enable/function` can never work on a running server for a pack added after boot - the workflow is now: `/mobfarm build` (writes 36 zips + hub only) -> RESTART -> `/mobfarm build <mob>` (clear -> wait 5s -> disable/enable -> function -> patch -> VERIFY).
- **True success reporting:** `verifyBuilt()` checks signature blocks after the function (pit: HOPPER (cx,cy-1,cz) + LIME_WOOL pad (cx,cy-1,cz+2) + SPAWNER stack (cx+6,cy+1,cz-2); pen: HOPPER (cx,cy+1,cz) + CHEST (cx-1,cy+1,cz+3) + SPAWNER). Green "Rebuilt ... via <id>-datapack.zip" ONLY when verified; otherwise red "Apply FAILED ... restart once, then retry". writePack now verifies the zip on disk (exists + non-empty) and logs the ABSOLUTE path, failing loudly if the file cannot be written/renamed.
- **Kill pad rework (land bays):** player now stands IN THE TRENCH at z=+2.5 (LIME_WOOL pad at (cx,cy-1,cz+2), HIT sign right at (cx+2,cy,cz+2) facing WEST, LOOT sign left at (cx-2,cy,cz+2) facing EAST, ZONE sign stays on the walkway) - mobs are ~2.4-3 blocks away: in player reach (3.0), out of mob reach (2.0). Front wall redesigned: solid sill at y=0 + 1-high OPEN slit at y=1 + wall above (mobs cannot fit a 1-high slit nor jump it - their feet stay below the sill line); water bays keep the full glass/barrier window (hoppers still harvest; aquatic bays are hopper-harvest). The old LOOT sign on the chest face was removed - it DESTROYED the front wall block it replaced (same class of bug as 2.6.6), and it sat in the new sill position.
- **Containment boxes are now the pit/pen INTERIOR** (absolute AABB {minX,maxX,minZ,maxZ,minY,maxY} set by pit()/pen(): pit = x cx±(halfW-0.3), z cz-depth+0.2..cz+0.35, y cy-1.3..cy+top-0.4 (openTop +2.6); pen = interior + y cy+2.3..cy+2+fenceH+1.8): mobs can never legally reach the trench/walkway; containMob teleports escapees back to the kill pad. The old box was pad-centered with hz up to 5.6 - it AUTHORIZED the trench up to z=+3.6, exactly where the user found zombies.
- **`/mobfarm build` (no arg) semantics changed:** hub/HUD platform + ALL 36 datapacks ONLY - each bay is Java-built, snapshotted into its zip, then CLEARED (no per-bay building, no false "built"); sets built=false (sessions stay closed until a bay exists). `/mobfarm build <mob>` / rebuild <mob> with no zip generates it first (then restart hint if packs not loaded); with a zip it clears -> 5s -> reload -> apply -> verify, and NEVER overwrites the zip (user saves survive).
- **NEW `/mobfarm <mob> save` (admin):** snapshots the bay EXACTLY as it stands (user edits included, spawner/hopper positions untouched) into world/datapacks/<id>-datapack.zip and prints the FULL PATH to copy to the PC as a version/backup. Packs now carry `state.txt` (pit flag + stand/killpad/stack/loot/community/cell relative to the bay origin) so applyPack restores geometry state (stand/pit/cell) from the zip itself - no Java rebuild needed and no state loss on restart.
- **CI:** run 33922776543 SUCCESS on 4d3e107 (only deprecation warnings); jars rebuilt by CI in 0cf5e63. Commit chain 4d3e107 -> 0cf5e63 (ci: rebuilt plugin jars). Jar = MAVOMobFarm-2.7.1.jar (80,264 B); plugin.yml/pom 2.7.1.
- **Deploy (this update):** STOP; delete MAVOMobFarm-2.7.0.jar; upload MAVOMobFarm-2.7.1.jar; keep ALL configs + data.yml + any world/datapacks zips you copied off-server. After boot: /mobfarm purge -> /mobfarm clear -> /mobfarm setcenter (x2) -> /mobfarm build (writes all 36 zips + hub; prints the datapack folder path) -> VERIFY the 36 zips in the file panel -> RESTART once -> /mobfarm build zombie (must print green Rebuilt/verified; other 35 untouched) -> test wall/slit + pad -> /mobfarm zombie save (prints the zip path) -> copy zips to PC. Boot log: MAVOMobFarm 2.7.1 enabled.
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg.

## 2026-09-05 HOTFIX 15 — MobFarm 2.7.2 (whole-farm protection, /mobfarm savehub + buildhub, /mobfarm current)
- **USER RE-TESTED 2.7.1 (screenshots in uploads/):** packs + per-mob flow WORKED - hub built, 36 zips generated, `/mobfarm build zombie` applied, `/mobfarm zombie save` printed `/home/container/world/dimensions/minecraft/overworld/datapacks/zombie-datapack.zip`; user hand-built walls + a footpath to the zombie bay. TOP THREE issues reported: (1) as a survival, DEPOPPED player they could still break blocks everywhere incl. the hub floor; (2) want to save the current hub + footpath (no redo) since they plan to build paths to every completed bay; (3) no command to get back to the bought mob zone without paying again.
- **Protection root cause:** `inFarmProtect()` collapsed to a HUB BALL when `data.built=false`, and 2.7.1's `/mobfarm build` sets built=false (nothing applied yet) - so paths/bays were unprotected after generation; `loadAll` also only computed the farm AABB when built=true. NOTE: the user's own deop test still bypassed because LuckPerms OWNER group grants mavomobfarm.admin/bypass (deop does NOT remove group perms) - protection for normal players was already functioning once built=true. Fix: protection is now ALWAYS the full farm AABB (hub ±20 + every bay origin ±14/16, y −12..+16) ± protect-radius (default 50) once center is set; `recomputeAABB()` runs at load and sets a new `aabbReady` flag; the built flag no longer gates it. Owners canBypass stays (they must keep building paths); chest open + item deposit/removal + mob killing remain allowed for normal players (onInteractProtect allows CHEST/TRAPPED_CHEST/BARREL/SIGN/HOPPER; onFarmAttack only cancels mob→player damage).
- **`/mobfarm savehub` (admin):** snapshots the ENTIRE farm footprint MINUS the 36 bay clear boxes (x±12, z−12..+10 each) into world/datapacks/hub-datapack.zip (pack_format 81, `function mavomobfarm:hub/build` = setblock lines ONLY, deliberately NO clear function so it can never wipe a bay). Region = recomputeAABB bounds; farm-wide chunks are loaded first (setblock in unloaded chunks silently fails). Re-run after each newly finished path; old blocks not in the new snapshot are not removed (documented).
- **`/mobfarm buildhub` (admin):** applies the saved hub zip (disable/enable file/hub-datapack → loadFarmChunks → function hub/build → patchHub re-pairs hub double chest + rewrite its sign → verify chest pair + sea lantern at (hx,hy−1,hz)) - use after `/mobfarm clear` etc.; needs a restart after savehub (startup pack scan). `/mobfarm build` (no arg) now SKIPS the bare-hub rebuild whenever hub-datapack.zip exists (a saved hub+paths is never touched; message tells you buildhub restores it). patchHub() extracted and shared by buildComplex/buildHub.
- **`/mobfarm current` (all players):** teleports to the session's bought mob (stand restored from the pack's state.txt - the trench pad) with yaw 180/pitch 15; requires an active session + unlocked pick (otherwise tells you to /mobfarm enter / pick). Tab-complete + help updated; `computeGeom()` no longer clobbers a saved stand: loadAll now calls `BayGeometry.loadState(...)` for every mob whose zip exists (loadState made package-visible).
- **CI:** run 33929846768 SUCCESS on dd2bd34; jars rebuilt by CI in cedaaea. Commit chain dd2bd34 -> cedaaea (ci: rebuilt plugin jars). Jar = MAVOMobFarm-2.7.2.jar (83,098 B); plugin.yml/pom 2.7.2.
- **Deploy (this update):** STOP; delete MAVOMobFarm-2.7.1.jar; upload MAVOMobFarm-2.7.2.jar; keep ALL configs/data + world/datapacks zips (zombie + hub + PC copies). (Optional reset: purge → clear → setcenter x2 → build → restart → build zombie → savehub.) Boot log: MAVOMobFarm 2.7.2 enabled. Test: break a block under the hub as a normal player (blocked), open chest + take items (allowed), kill zombie (allowed), /mobfarm current (teleports back), /mobfarm savehub → restart → /mobfarm buildhub (hub + path restored).
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg.

## 2026-09-05 HOTFIX 16 — MobFarm 2.7.3 (fix 36 boot warnings: loadState parsed `pit=true` as a double)
- **USER BOOT LOG (2.7.2 deployed):** 36 `[MAVOMobFarm] MAVOMobFarm: loadState <mob> failed: java.lang.NumberFormatException: For input string: "true"/"false"` warnings at enable; plugin still enabled fine (non-fatal).
- **Root cause:** `BayGeometry.loadState()` parsed every state.txt value into a `double[]` BEFORE the switch, but line 1 is `pit=true|false` (boolean). `Double.parseDouble("true")` threw → whole state.txt discarded for every mob → at boot `m.stand` stayed at the computeGeom default (walkway z=+6.5) instead of the saved trench pad in state.txt, and `m.pit/m.cell` were not restored (containment fell back, `/mobfarm current` would teleport to the wrong spot).
- **2.7.3 fix:** parse per key — `pit` handled as boolean FIRST; numeric keys parse doubles only in their case; a single bad line is skipped with a warning instead of failing the whole file. Same tolerances kept (`cell != null` = success).
- **CI:** run 33930664665 SUCCESS on dabe3e7; jars rebuilt by CI. Commit chain dabe3e7 -> (ci jars). Jar = MAVOMobFarm-2.7.3.jar; plugin.yml/pom 2.7.3.
- **Deploy (this update):** STOP; delete MAVOMobFarm-2.7.2.jar; upload MAVOMobFarm-2.7.3.jar; keep ALL configs/data + zips (they already contain state.txt - no regeneration needed). Boot: NO loadState warnings, "MAVOMobFarm 2.7.3 enabled". Optional check: /mobfarm current lands on the trench pad (not the walkway).
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg.

## 2026-09-05 HOTFIX 17 — MobFarm 2.7.4 (saves are never overwritten: build keeps zips unless `force`; savehub covers AABB+50; guide corrected)
- **USER QUESTION after 2.7.3 deploy:** "did you save my exact footpath config into the jar or do I rebuild it? what does buildhub contain if the hub never had saved? we never saved hub-path but guide mentions hub-datapack" - user had built hub walls + footpath to the zombie bay in 2.7.1, saved ONLY the zombie bay zip, and was about to follow guide step 6 (purge/clear) which would have WIPED the footpath (nothing in the jar can restore it).
- **Clarity (answer given to user):** the jar contains ONLY the bare hub platform + the 36 pristine layouts - NEVER the footpath. buildhub without hub-datapack.zip does nothing ("No hub-datapack.zip yet. /mobfarm savehub writes it first."). hub-datapack.zip = EVERYTHING in the farm footprint minus the 36 bay boxes (hub + walls + all footpaths). It does not exist until the first `/mobfarm savehub`. Correct order NOW: `/mobfarm savehub` -> copy zip + zombie zip to PC -> only then any purge/clear.
- **2.7.4 safety change:** `/mobfarm build` (no arg) now KEEPS every existing `<mob>-datapack.zip` (wasBuilt -> kept++) and only generates MISSING packs -> saved bay edits can never be silently overwritten by a plain build; `/mobfarm build force` (or `all`) regenerates all 36 from the pristine layouts (explicit, warns in chat). `/mobfarm rebuild` (no arg) no longer clear+build: identical to build (keeps zips) - reset is purge -> clear -> setcenter -> restart -> buildhub -> build <mob> per bay. Per-mob rebuild = build <mob>.
- **savehub region widened:** passes AABB ± protect-radius (x/z) and y minY-4..maxY+8 to writeHubPack so footpaths running around/past the farm edge are captured too. (writeHubPack load loop + inBayBox exclusion unchanged; still no clear function.)
- **CI:** run 33931716664 SUCCESS on e29f5e5; jars rebuilt by CI. Commit chain e29f5e5 -> (ci jars). Jar = MAVOMobFarm-2.7.4.jar; plugin.yml/pom 2.7.4.
- **Deploy (this update):** STOP; delete MAVOMobFarm-2.7.3.jar; upload MAVOMobFarm-2.7.4.jar; keep ALL configs/data + zips. Boot: no loadState warnings, "MAVOMobFarm 2.7.4 enabled". First command: `/mobfarm savehub` -> copy hub-datapack.zip + zombie-datapack.zip to PC. (See MAVOCRAFT-DEPLOY.md step 0/6/7 for the safe reset order - runs are no longer destructive.)
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg.

## 2026-09-05 HOTFIX 18 — SLEEP PACK: Professions 3.15.0 (Sleeper profession + night vote + bound bed + rest bonuses) + DeathChest 1.2.0 (Player Level scaled grave costs) + Guide 2.8.1
- **User spec (verbatim acceptance criteria):** DeathChest 1000 Coins OR 10 Lucky Coins at Player Level 1, each player level doubles both (tie costs to character progress, not just money); sleep vote table 1->1, 2->2, 3-4->2, 5-7->3 or vote, 8-9->4 or vote, 10->5 or vote; >4 online at 18:30 = "sleep time" chat message, `!sleep yes`/`!sleep no`, no bed needed if yes > no, at 19:30 tally + announce, skip to 06:00 next day only when yes wins with >=75% of eligible having voted; vote-skip must NOT grant sleep-profession XP; new Sleeper profession = bound bed (placed right-clicked = sleep only, or triggers the vote when >3 online at sleep time; no respawn point, no home), +1 sleep XP each, 50 sleeps to level up +50 more per level, +1 heart +100 Lucky Coins every 10 levels (L10/20/30...), cap 100 = SLEEPER RANK; Tavern sleep: +1 profession point to ALL active professions (rested); bound-bed sleep +2 instead (separate paths).
- **User decisions (asked 2026-09-05):** (1) build INSIDE MAVOProfessions (10th profession), not a new plugin and no uploaded Tavern/Homes source; (2) 1 profession point = 1 XP unit; (3) vote triggers with >4 online (5+); (4) keep Tavern 100-coin paid rest alongside the free vote.
- **Sleeper profession:** `no-tool` flag (no starter tool, no upgrades - `doClaim` skips the tool loop), `xp-per-level: 50` -> `xpNeeded = 50 * level` (L1->2 = 50, L2->3 = 100, ...), max-level 100, menu gets a 10th slot (18), `/sleeper` command (bind/unbind/status/vote/tavernset). Rewards in `sleeperClaimBonus`: every 10 levels `Attribute.GENERIC_MAX_HEALTH` +2 HP (applied on join + claim via `applySleeperHealth`) and +100 Lucky Coins via reflection into MAVOLuckyCoins.giveCoins; L100 per-prof `rank-commands` (default `lp user %player% parent add sleeper`) + "SLEEPER RANK" broadcast. PAPI `%mavoprof_sleeper_*%` works automatically.
- **Night vote (`sleep:` config):** 1s clock task on the configured world (default "world"); opens at tick 12500 (18:30) when online >= 5 (`vote-min-online`), closes 13500 (19:30); chat listener on `!sleep yes/no` (AsyncPlayerChatEvent, cancelled=false so votes show in chat) + `/sleeper vote`; tally = more yes than no AND voted >= ceil(75% * online) -> `skipNight(viaVote=true)` (next day 6000, wakes sleepers, NO XP); otherwise broadcast failure + bed quorum hint. Any skip (bed or vote) closes the vote and sets `ourSkipDays` so no double-skip and no false Tavern attribution.
- **Bed quorum (no vote):** `sleepersNeeded(online)` = 1/2/2/3/4/5 for 1/2/3-4/5-7/8-9/10+; counts players in bed in the sleep world (PlayerBedEnter MONITOR, removed on leave/quit); when met at night/storm `skipNight(viaVote=false)` awards +1 Sleeper XP to everyone in bed (once per day) and +2 rest bonus to bound-bed sleepers, then wakes all (prevents vanilla double-skip). XP award is per successful SLEEP only - vote skips grant nothing (spec).
- **Bound bed:** `/sleeper bind` while looking at a bed (stored `data.yml bed.<uuid>.*`, both bed halves resolved via block-data part/facing, unbound on break). LOWEST-priority PlayerBedEnter cancel -> no vanilla respawn point set -> `player.sleep(loc,false)` API (reentrancy-guarded). MAVOHomes may still receive the cancelled event (source-less integration; guide advises keeping the bound bed outside your claim for zero home interaction). Bound-bed right-click during an open vote = automatic yes; before 18:30 = info message; always counts toward quorum.
- **Tavern rest:** `/sleeper tavernset` (admin) stores the plaza bed in `sleep.tavern-bed`; right-clicks on it are recorded in `pendingTavern` and awarded ONLY when the clock detects a night skip we did NOT perform (paid rest actually went through): +1 XP to every OTHER active profession (`restBonus`, config `rest-bonus-includes-sleeper: false` default) and +1 Sleeper sleep (`tavern-rest-sleeper-xp: true` default, once per MC day).
- **DeathChest 1.2.0:** keys re-based `teleport-cost-coins: 1000` / `teleport-cost-lucky: 10` (level-1 base) + `teleport-scale` / `teleport-scale-cap (30)`; auto-migration from the 1.1.1 flat 5000/100; `playerLevel()` averages ALL profession levels (MAVOProfessions config professions x data.yml p.<uuid>.<id>.level, unstarted = 1) with ALL achievement categories (MAVOAchievements categories x data.yml players.<uuid>.<cat>.level, default 1) - matches the guide's Player Level definition; confirm GUI shows "cost scales with your Player Level: N" and both pay buttons use the same scaled values.
- **Guide 2.8.1:** professions page says 10 professions + Sleeper blurb, tavern page (+1 rest bonus + vote info), new sleeper info page (`/sleeper bind`, rewards), deathchest page = scaled costs, tavernbar page (+1 profession point line).
- **CI:** run 33935350928 SUCCESS on 8d61f33 (only pre-existing deprecation warnings); jars rebuilt by CI in 11f8b29. Jars = MAVOProfessions-3.15.0.jar (53,988 B) / MAVODeathChest-1.2.0.jar (18,996 B) / MAVOGuide-2.8.1.jar (25,740 B); plugin.yml/pom versions match. Signature-verified in jars (sleep strings + sleeper command + scaled-cost strings + guide sleeper page).
- **Deploy (this update):** STOP; delete MAVOProfessions-3.14.2.jar + MAVODeathChest-1.1.1.jar + MAVOGuide-2.8.0.jar; upload 3.15.0 / 1.2.0 / 2.8.1; keep ALL configs + data.yml (professions data untouched; DeathChest config cost keys auto-migrate). Boot: "MAVOProfessions v3.15 enabled: 10 professions". One-time: `/sleeper tavernset` while looking at the Tavern bed; optional `lp creategroup sleeper`. Test: night with 5+ accounts -> 18:30 vote message + !sleep yes/no; /sleeper bind + right-click bed; /grave shows 1000/10 at PL1.
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg.

## 2026-09-05 HOTFIX 19 — MobFarm 2.7.5 (buildhub fixed: hub + bay zips applied directly in Java - no "Unknown data pack", no restart)
- **USER HIT (running 2.7.4, mid step-7 reset):** `/mobfarm buildhub` -> chat "Apply FAILED: hub-datapack.zip is not loaded on this server (restart once...)" and console `Unknown data pack 'file/hub-datapack'` x2 + `Unknown function mavomobfarm:hub/build` (00:40:25). User had saved hub + zombie, zips confirmed in `/home/container/world/dimensions/minecraft/overworld/datapacks` (same folder as the zombie zip which loaded fine in 2.7.1 - it was registered at an earlier boot).
- **Root cause:** `buildHub` (and per-mob `applyPack`) ran `datapack disable/enable "file/..."` + `function mavomobfarm:...` - that only works for packs the server REGISTERED at STARTUP. The server scans the datapacks folder only at boot; a pack written later (or any pack not registered this boot) is unknown to the command engine even though the zip sits in the right folder -> "Unknown data pack". The pack ID `file/hub-datapack` shown in the error is just Minecraft's zip-derived ID, not a path; the folder itself was correct.
- **2.7.5 fix:** packs are applied DIRECTLY in Java. Both hub and bay zips carry pure `setblock x y z <blockstate>` lines (the hub pack is deliberately setblock-only with no clear function), so `applyPackFunction()` reads the zip entry, parses each line and `world.setBlockData(...)`s it - byte-identical to running the function, zero datapack-registry dependency. `buildHub` now: loadFarmChunks -> applyPackFunction(hub/build) -> patchHub -> verify (unchanged) -> markBuilt; `applyPack` (bays) now: loadState (or Java build fallback) -> clearBayBox (same fill as the pack's clear function, done in Java) -> loadBayChunks -> applyPackFunction(<id>/build) -> patch -> verifyBuilt. `buildMob` rebuild path dropped the 5s wait + function clear (covered inside applyPack); the "restart once" messages are gone; savehub now tells you buildhub applies it directly (no restart).
- **Net effect for the user:** after purge/clear/setcenter (already done), just `/mobfarm buildhub` then `/mobfarm build <mob>` per bay - no more restarts, no "Unknown data pack". Savehub -> buildhub also works within one session now.
- **CI:** run 33936106316 SUCCESS on d372d5b (only pre-existing deprecation warnings); jars rebuilt by CI in 1b7f3d5. Jar = MAVOMobFarm-2.7.5.jar (83,941 B); plugin.yml/pom 2.7.5. Signature-verified: plugin.yml 2.7.5 + applyPackFunction string + hub build entry in the class.
- **Deploy (this update):** STOP; delete MAVOMobFarm-2.7.4.jar; upload MAVOMobFarm-2.7.5.jar; keep ALL configs/data + zips (nothing to redo - the hub pack and zombie pack on disk are unchanged and directly applied). Then: /mobfarm buildhub (green "Hub + footpaths restored ... (N blocks)") -> /mobfarm build zombie -> /mobfarm build husk (per bay). Boot log: MAVOMobFarm 2.7.5 enabled. (Sleep pack 3.15.0/1.2.0/2.8.1 jars remain optional/next.)
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg.

## 2026-09-05 HOTFIX 20 — Professions 3.15.1 + Guide 2.8.2 (Sleeper config migration + bound Sleeper Bed item + Tavern rest real award)
- **USER FIELD TEST (3.15.0 deployed):** boot log `MAVOProfessions v3.15 enabled: 9 professions`; `/sleeper` said "not configured"; Sleeper missing from /profession menu + TAB showed raw `%mavoprof_sleeper_%`; tavern-bed right-click: MAVOHomes "chunk claim" message + "Morning! Night skipped - bedtime worked." but NO "+1 profession point"; user (in creative) asked whether XP was granted; user corrected spec: "No tools? You should get a bound bed - replacement from /prof if ever lost".
- **Root cause 1 (9 professions):** `saveDefaultConfig()` never overwrites an existing config, so the user's 3.14.2 `plugins/MAVOProfessions/config.yml` had no `sleeper:` profession and no `sleep:` keys -> loadCfg() loaded 9 professions and /sleeper fell back to "not configured"; the PAPI expansion returned null for the missing id (TAB showed the raw placeholder).
- **3.15.1 migration:** new `migrateCfg()` runs BEFORE loadCfg(): writes the `professions.sleeper` section (display/action/icon/no-tool/sleeper/xp-per-level 50/max-level 100/rank-commands 100 -> lp parent add sleeper) into an existing config and fills missing `sleep.*` keys WITHOUT touching a user-set `sleep.tavern-bed`; saves config once. Boot log now prints the real version from plugin.yml (v3.15.1) + profession count.
- **Root cause 2 (tavern rest no award + wrong skip source):** the player slept in the TAVERN bed; our 1-online bed quorum (1->1) recorded them, fired our own `skipNight` first ("Night skipped - bedtime worked."), woke them from the bed and marked ourSkipDays - so (a) MAVOTavern's paid flow was pre-empted/competing, (b) our external-skip detector never awarded +1. Fix: tavern beds are now EXCLUDED from our quorum (`isTavernBedBlock` helper, checked in onBedEnterLow + onBedEnter) - the paid tavern rest is MAVOTavern's job; when its skip is detected our clock awards +1 to every OTHER active profession (restBonus now returns count; sleepXp + restBonus are SURVIVAL-gated like all profession XP and send an honest "counted but SURVIVAL only" message instead of claiming XP). The MAVOHomes "chunk claim to set home" message is MAVOHomes reacting to the TAVERN bed (its own design; tavern beds set respawn) - unrelated to our bound sleeper beds.
- **User spec correction (bound bed):** Sleeper now grants a BOUND SLEEPER BED item on start (RED_BED, PDC locked `sleeper:main` + owner, lore how-to) - `onPlaceBound` lets it through, `onPlace` auto-binds the placed bed (setBoundBed) so right-click = sleep-only (no respawn/home) + +2 rested. Lost it? Clicking Sleeper in /profession gives a replacement (new noTool replacement path; previously the tool-replacement loop would have crashed on the empty tier map). `/sleeper bind` still works for binding any bed you look at. Breaking the bound bed unbinds + points to /profession replacement. Menu lore + guide page updated ("no tools" -> bound bed).
- **CI:** run 33938904553 SUCCESS on 121eb13 (only pre-existing deprecation warnings); jars rebuilt by CI in bfe2c6c. Jars = MAVOProfessions-3.15.1.jar (56,161 B) / MAVOGuide-2.8.2.jar (25,784 B); plugin.yml/pom versions match. Signature-verified: Sleeper Bed strings + migration string + guide sleeper page in the zips.
- **Deploy (this update):** STOP; delete MAVOProfessions-3.15.0.jar (+ MAVOGuide-2.8.1.jar optional); upload 3.15.1 (+ 2.8.2); keep configs (migration adds Sleeper + sleep keys on next boot). Boot: "MAVOProfessions v3.15.1 enabled: 10 professions". Test: /profession shows 10 icons incl. Sleeper (start -> bound Sleeper Bed in inventory), place it -> right-click at night (SURVIVAL) -> +1 XP, tavern bed rest -> "+1 profession point to N active profession(s)" when Tavern skips in SURVIVAL.
- **Zip:** refreshed after this commit; SHA in MAVOcraft-backup.sha256 + commit msg.

## 2026-09-05 HOTFIX 21 — Professions 3.15.2 + Guide 2.8.3 (Sleeper STILL 9 professions on live boot — self-heal + guide duplicate keys + TAB placeholder code)
- **USER REPORT (3.15.1 deployed - verified in boot log: MAVOProfessions 3.15.1 / MAVOGuide 2.8.2):** STILL `MAVOProfessions v3.15.1 enabled: 9 professions`; NO "3.15.1: added the Sleeper profession" migration line in the boot log (yet `tavern rest hooked at world -2548 200 -1647` = sleep section exists); TAB line had `%mavoprof_sleeper_*%`; join-time warnings `duplicate keys found : icon` + `lines` x2.
- **Root cause 1 (9 professions, no migration line):** `migrateCfg()` guarded on `profSection.contains("sleeper")` — TRUE for a stray/non-section value (e.g. `sleeper: true` typed at professions level, or any malformed `sleeper:` entry), so it never wrote defaults AND logged nothing; `loadCfg()` then does `sec.getConfigurationSection("sleeper")` -> null -> `continue` -> 9 professions, silently. Classic "contains says yes, section is null" trap.
- **3.15.2 fixes:** `migrateCfg()` now requires `professions.sleeper` to be a REAL ConfigurationSection with `display` + `sleeper: true`; otherwise it deletes the bad entry, writes the full Sleeper defaults and logs `3.15.2: added|repaired the Sleeper profession...` (with a WARNING naming the malformed entry). Same section-type guard for `sleep` (never touches a valid `sleep.tavern-bed`). `loadCfg()` now WARNs `config.yml: professions.<id> is not a section - skipped...` so a bad entry can never be silent again. Boot log now lists the ids: `10 professions [archer, ... , sleeper]`. PAPI placeholder `%mavoprof_sleeper%` (plain id, no suffix) prints `not started` until started - the correct TAB line is `&d😴 &f%mavoprof_sleeper%` (NOT `_` and NOT `_*`), plus `"%mavoprof_sleeper%": 1000` in placeholder-refresh-intervals + /tab reload (deploy guide 5c).
- **Root cause 2 (guide duplicate-key warnings at join):** the SHIPPED `MAVOGuide` config.yml had a real duplicate `icon:`/`lines:` pair inside the v16 whatsnew entry (its v15 "museum shopsgen" body was left appended when the title was renamed in guide 2.7.2) -> MAVOGuide parses `plugins/MAVOGuide/config.yml` at join -> SnakeYAML `duplicate keys found : icon/lines`. Fixed in 2.8.3: v16 keeps its own body, the stray block is a proper `v15 - Museum shop auto-reload` entry, new `v19 - Sleeper is LIVE` entry added, `version: 19`. DEPLOY NOTE: the user's data-folder `plugins/MAVOGuide/config.yml` holds the old duplicate, so 2.8.3 deploy DELETES that one file (default regenerates clean).
- **Build fix:** Maven `<finalName>` was hardcoded 3.15.1/2.8.2, so CI rebuilt the new content into the OLD filenames - poms now finalName 3.15.2/2.8.3.
- **CI:** 33939789816 SUCCESS on 8179d7f -> ed54139 (rebuilt, wrong names), 33939893399 SUCCESS on 42c95e8 -> 9d3ff3a (correct names). Jars: MAVOProfessions-3.15.2.jar 56,607 B / MAVOGuide-2.8.3.jar 25,975 B (root + jars/ identical). Verified: plugin.yml 3.15.2/2.8.3; class strings for 3.15.2 repair/warning/boot-id-list; guide config v19 + sleeper page + zero duplicate keys.
- **Deploy:** STOP; delete MAVOProfessions-3.15.1.jar + MAVOGuide-2.8.2.jar (+ keep 3.15.0/2.8.1 gone); upload 3.15.2 + 2.8.3; DELETE plugins/MAVOGuide/config.yml (one file); keep MAVOProfessions config (3.15.2 self-heals the sleeper section on boot). Boot must show: `3.15.2: added|repaired the Sleeper profession ...` + `MAVOProfessions v3.15.2 enabled: 10 professions [...]` (or 3.15.2 warning naming the bad `professions.sleeper` entry if it was REALLY missing/broken). If still 9 -> paste the PROFESSIONS section of plugins/MAVOProfessions/config.yml.
- **Known cosmetic (not fixed):** boot log line `MAVOMobFarm 2.7.4 enabled` is a hardcoded string in MobFarm.java#148 (plugin.yml is 2.7.5) - can be fixed in a future point release.

## 2026-09-05 HOTFIX 22 — MobFarm 2.7.6 (zone disable/enable + auto-pick + free return) + Events 1.2.1 (siege night window) + Professions 3.15.3 (Sleeper defaults-shadow fix)
- **USER REPORT (3.15.2 deployed - boot log proves it: `enabled: 9 professions [archer, ...miner]`, NO 3.15.2 migration/warning lines):** Sleeper STILL not in /profession, TAB placeholder still empty, /sleeper says "not configured". Also: MobFarm zones wiped -> players can buy missing bays and die of fall damage, so /mobfarm disable + enable wanted; pick menu should auto-open on /mobfarm enter; no double-buy of the same pick; Zombie Siege event must not start before 18:30 and must end at/after 06:00.
- **ROOT CAUSE (Sleeper, finally found):** `reloadConfig()` calls `config.setDefaults(jar config.yml)` - the JAR default contains the FULL `professions.sleeper` section, so `getConfig().contains("professions.sleeper")`/`.get("sleeper")` returned the DEFAULT section even though the on-disk file never had it: migration saw "valid" (3.15.1 contains-guard AND 3.15.2 section-guard) and silently skipped; `loadCfg()` uses `getKeys(false)` (defaults ignored) -> 9 professions, no warning. Both boots matched perfectly (no migration line, tavern hook present, 9 profs). Fix: 3.15.3 migration loads the RAW file (`YamlConfiguration.loadConfiguration(new File(dataFolder,"config.yml"))` - no defaults), checks/writes there, saves, reloads; onEnable re-checks after loadCfg and FORCES defaults + reload if sleeper still missing; `/sleeper debug` prints disk keys vs loaded professions (ground truth for any future report); migration always logs its decision ("already present/added/repaired").
- **MobFarm 2.7.6:** `disabled` Set persisted in data.yml ("disabled" list); `/mobfarm disable|enable <mob|all>` (admin) hides zones from pick + prices GUIs (BARRIER filler shows count), guards /mobfarm current + teleportToSession + onPickClick (hidden zones can't be bought), kicks live sessions off a disabled zone (spawn stopped, mobs cleared, hub teleport). `startSession` already auto-opened pick; `beginEnter` existing-session path now also opens pick on arrival. onPickClick: ACTIVE mob (unlocked + same id) = FREE return exactly like /mobfarm current (no rebuild/charge); switching mobs still pays, `s.picks` doubling unchanged (back-to-previous = pay again). Boot log now prints real version via getDescription().getVersion() (was hardcoded "2.7.4") + disabled count.
- **Events 1.2.1 (Zombie Siege):** night window = 18:30-06:00 (ticks 12500->6000, same as sleep vote). `pickEvent()` rerolls if the auto pick lands on zombiesiege in daytime; `/event start zombiesiege` rejected in daytime with message; the siege spawn task checks the clock every 20s and stops + broadcasts "the sun rose (06:00)".
- **Versions:** 2.7.6 / 1.2.1 / 3.15.3 (pom + plugin.yml + finalName all bumped; finalName lesson from 3.15.2 hotfix applied).
- **CI:** 33941414898 SUCCESS on 90d39ef -> 5b16aa8. Jars: MAVOMobFarm-2.7.6.jar 85,847 B / MAVOEvents-1.2.1.jar 13,371 B / MAVOProfessions-3.15.3.jar 57,738 B (root + jars/ identical). Verified plugin.yml versions + class strings (3.15.3 migration/debug, MobFarm disable/enable/FREE + new boot log, Events night window/sunrise). Old jars 2.7.5/1.2.0/3.15.2 removed in finalize.
- **Deploy:** STOP; delete MAVOMobFarm-2.7.5.jar, MAVOEvents-1.2.0.jar, MAVOProfessions-3.15.2.jar; upload 2.7.6 + 1.2.1 + 3.15.3; keep configs/data (MobFarm zones state is in data.yml, applied with /mobfarm disable|enable; guide stays 2.8.3). One-time: /mobfarm disable all + /mobfarm enable zombie; /mobfarm enable <mob> after each new build. Boot: "3.15.3: ... Sleeper ..." + "10 professions [...sleeper]" + "MAVOMobFarm v2.7.6 enabled". If still 9: /sleeper debug output.

## 2026-09-05 HOTFIX 23 — Professions 3.15.4 (Gambler rebalance + Sleeper bed anti-exploit) + MobFarm 2.7.7 (/mobfarm order) — CW1 + CW2 of the Discord current-work list
- **User request (Discord pack current work):** CW1: (a) sleep gives +1/+2 XP to every active profession incl. Gambler, and Gambler's xp-base 5 / growth 1.008 meant ~5 nights = a free Gambler level -> scale up requirements; (b) Sleeper bed exploit: place bound bed -> break -> drops as NORMAL sellable bed (~56 coins) -> /profession replacement -> repeat = infinite coins; fix options were sell-price-1 / keep-bound-on-pickup / non-sellable flag. CW2: full MobFarm build order (hostile + animal) so zones can be connected row by row, Zombie -> Husk next, etc. CW3 = the 10 approved ideas (queued, not yet started).
- **Gambler fix (3.15.4):** shipped config default + migrateCfg (disk-view) bump `professions.gambler.xp-base` 5->30 and `xp-growth` 1.008->1.012 (only if below new values; logs "3.15.4: Gambler rebalanced..."). Rationale: casino XP = bet/100 (or bet*5 Lucky), sleeping = +1/rest, so 30 rests = L2 now while a 5,000-coin bet = 50 XP keeps betting the real path; the old 5-XP L2 (5 rests or one 500-coin bet) was trivial.
- **Sleeper bed fix (3.15.4):** `onBreakBoundBed` now `setDropItems(false)` + drops the BOUND bed item (buildSleeperBed, keeps proflock PDC -> AH refuses it: "Profession-bound items cannot be auctioned") + unbinds + message; lore adds "NOT sellable / cannot be auctioned. Breaking the bed drops it back as this bound bed". Explosion protection: BlockExplodeEvent + EntityExplodeEvent remove any bound bed block from the explosion blockList (bed survives; TNT/creepers can't shred it into a normal bed). `boundBedOwners()` scans `bed.<uuid>` keys (data.yml) - fixed from an earlier draft that wrongly scanned `p.<uuid>.sleeper.bed`. Remaining: users should ALSO set RED_BED sell price to 1 in the EconomyShopGUI shops yml (defence in depth, exact folder = plugins/EconomyShopGUI/shops, file with `sell:` key - AH scans the same).
- **MobFarm 2.7.7:** new `/mobfarm order` (player) prints HOSTILE + ANIMAL build sequences row-by-row with [built]/[not built]/[DISABLED] per bay. Order derives from config offsets (rows z -40 -> -160, along the line x asc): hostile rows = zombie,husk,skeleton,stray,hoglin / spider,cave_spider,creeper,enderman,piglin / blaze,magma_cube,slime,wither_skeleton,silverfish / drowned,guardian,witch,pillager,phantom; animals = cow,pig,chicken,sheep / rabbit,villager,iron_golem,squid / glow_squid,bee,fox,goat / llama,panda,frog,sniffer.
- **Discord pack:** DISCORD-PACK-2026-09-05.md section 4 rewritten as "CURRENT WORK": CW1 (in progress), CW2 (ready - the two lists), CW3 = the 10 ideas queued. Pushed first (60442bf).
- **CI:** 33946699042 SUCCESS on 70ec6ce -> f5b27c2. Jars: MAVOProfessions-3.15.4.jar 59,029 B / MAVOMobFarm-2.7.7.jar 86,754 B (root + jars/ identical). Verified versions + strings (3.15.4 gambler rebalance + NOT-sellable lore, MobFarm build-order output). Old 3.15.3/2.7.6 jars removed in finalize.
- **Deploy:** STOP; delete MAVOMobFarm-2.7.6.jar + MAVOProfessions-3.15.3.jar (also 2.7.5/3.15.2 if still present); upload 2.7.7 + 3.15.4; keep configs/data (jar migrates gambler values + sleep keys itself). Boot: "3.15.4: Gambler rebalanced..." + "MAVOProfessions v3.15.4 enabled: 10 professions [...]". Test: /prof gambler page / gambler XP from a bet; place Sleeper bed -> break -> pickup keeps "Sleeper Bed" (not sellable); /mobfarm order shows both lists.

## 2026-09-05 HOTFIX 24 — MAVOWarps 1.0.0 (CW#3 idea 1: public player warps)
- **What:** NEW plugin. Players buy a warp slot (25,000 first / 50,000 second / 100,000 third - extra-warp-costs list, empty = capped) and register a public warp at their base; the whole server can /warp <name> or browse /warps (GUI, paginated, owner + coords + visits, click to teleport).
- **Safety (matches homes/portals):** 3s stand-still title countdown, move cancels, monsters within 12 blocks block it (no escaping fights), 30s personal cooldown, destination chunk loads + safe-air fallback (up to 4 above, else highest block).
- **Commands:** /warp (info+slots), /warp <name>, /warps, /warp create <name>, /warp remove <name>, /warp rename <old> <new>, /warp info <name>; admin /warp delete <name> + /warp tp <name> (console works). Names: 3-20 chars [a-z0-9_-]. Survival pays, creative free (admin testing).
- **Data:** plugins/MAVOWarps/data.yml (warps.<name> + visits). Config: costs/warmup/monster-radius/cooldown/max-per-page. Persist across restarts. No PAPI/TAB impact.
- **CI:** first run 33946862166 FAILED (VaultAPI not resolvable - warps pom lacked the jitpack repo), fixed in f02dcab; run 33946966957 SUCCESS -> 3b60eb6. Jar = MAVOWarps-1.0.0.jar 14,438 B (root + jars/ identical; plugin.yml 1.0.0, strings verified). build.log from the failed run removed in finalize.
- **Deploy:** STOP; upload MAVOWarps-1.0.0.jar (NEW - no old jar to delete); keep plugins/MAVOWarps (auto-created). No config touches.

## 2026-09-05 HOTFIX 25 — MAVOChestShops 1.0.0 (CW#3 idea 2: player chest shops)
- **Log check (user deploy 17d2e76 batch):** CLEAN. Professions 3.15.4 boot = `3.15.3: Sleeper profession already present` + `3.15.4: Gambler rebalanced xp-base 5.0->30 / 1.008->1.012` + `10 professions [...sleeper]` (Sleeper FINALLY loads); MobFarm 2.7.7 `mobs=36` real-version log; Events 1.2.1; Warps 1.0.0 `0 warp(s)`. Benign pre-existing noise only (Essentials unsupported-version, BlueMap manual-save WARN, JOML unsafe warning, Paper version check). Cosmetic: the "already present" line still prefixes 3.15.3 (string not updated when version bumped) - log text only.
- **What it is:** new plugin. Put items in a chest -> `/cshop create <price>` (looking at it) -> chest renamed [SHOP] item price owner; right-click = buy GUI (click item/buy-1 = 1, buy-64 = up to stock+affordable); owner sneak+right-click = storage; `/cshop price`, `/cshop remove`; `/cshop list` GUI (paged), `/cshop info <id>`; admin `/cshop tp|delete|reload` + permissions mavochestshop.admin. Console /cshop tp works.
- **Rules:** max-shops-per-player 3 (config), tax-percent 0 (config), survival pays -> seller gets coins (offline OK via Vault), creative = test mode (no money). Shop chests: explosion-proof (removed from blockList), piston-proof (extend/retract cancelled), non-owner break cancelled; double chests index+rename BOTH halves. Optional market district: require-market + market-regions config; optional rentable stalls: `stalls` list (id/world/chest xyz/price) + `/cshop rent <item-price>` at the stall chest (rent pays stall price + auto-creates shop, `/cshop remove` frees the stall).
- **Versions:** 1.0.0 (pom/plugin.yml/finalName). Softdepend Vault; jitpack repo included (warps lesson).
- **CI + jars:** filled below after build; zip refreshed in finalize.
- **Deploy:** STOP; upload MAVOChestShops-1.0.0.jar (NEW - no old jar, keep plugins/MAVOChestShops auto-created). Boot: `MAVOChestShops v1.0.0 enabled - 0 shop(s), stalls=0.`

## 2026-09-06 HOTFIX 26 — THE BIG BUILD (CW#3 ideas 3-10) + Professions 3.15.5 (bed asleep 12:00 -> 7:00)
- **User request:** create ALL remaining approved ideas; update version+features Discord MD; after the 10 ideas give another 10 (famous plugins -> own version); fix: sleeping woke at 12:00 (tick 6000 = Noon) halving each day's active time -> wake at 7:00 (tick 1000).
- **Professions 3.15.5 sleep fix:** `sleep.skip-to-tick` default 6000 -> 1000 shipped; `migrateCfg` bumps ONLY old default 6000 -> 1000 on boot (custom values untouched), logs `3.15.5: bedtime wake-up 12:00 -> 7:00`. Also added `xpBoost()` reflection hook into `addXp`: every profession XP grant is multiplied while a MAVODoubleXp boost is active (reads `mavo.doublexp.DoubleXp.boost()`), survival-only (creative check stays first).
- **MAVODoubleXp 1.0.0 (idea 5):** weekly window config (default Fri 18:00 -> Sun 18:00 Europe/London, x2.0), bossbar countdown, start/end broadcasts, `/xpboost status|now <minutes> [mult]|off|reload` (admin). Public API: `DoubleXp.boost()`, `active()`, `force(endMs,mult)`, `clearForce()` (used by Seasonal).
- **MAVOBossRaid 1.0.0 (idea 3):** `/boss status|join|leave|return`; queue opens 10 min before raid (default Sat 20:00, config); start teleports queue in a circle + spawns ONE boss (default ZOMBIE renamed "MAVO WARDEN OF THE WILDS"), HP = hp-base 2000 + 100/player; bossbar + `onHurt` damage map + `onBossDeath` clears vanilla drops and pays: coins/player 2500 + 50/1000 dmg + 2 Lucky Coins (reflection MAVOLuckyCoins.giveCoins) + trophy (top damage) + crate key (reflection MAVOCrates.giveKey; missing plugin = skipped); 45-min safety timeout; admin `/boss setarena|now|cancel|reload` (config rewards all tunable).
- **MAVOCrates 1.0.0 (idea 4):** `/crate list|info <name>`, admin `set`(target block within 6)/`unset`/`clear`/`givekey <player> <name> [n]`/`reload`; config pools common/rare/mythic (display, key-material/name/lore, cooldown-seconds, rewards `TYPE:AMOUNT:WEIGHT`, TYPE = material|coins|lucky); key = PDC-tagged item, right-click crate block -> roll (weighted), coin/Lucky payouts, firework + broadcast on jackpot-ish rolls; crate blocks unbreakable (break cancelled) and stored world/x/y/z in data.yml. Public `static giveKey(Player,String,int)`.
- **MAVOPets 2.0.0 (idea 7):** /pets shop (cat/wolf/fox/parrot/axolotl/turtle, prices 25k-60k), one active pet; follows (teleport > follow-range), invulnerable+no AI+silent, cannot be hurt; abilities: xorb (pulls XP orbs within 8 blocks, toggleable) + carry (one item slot in /pet menu, stored in data.yml); pet levels +1 XP/active minute (survival), lvl = xp/xp-per-level (100), max 100, name shows [Lv n]; /pet menu|recall|xp, admin /pet give; old 1.0.0 jar removed (data key `pets2` is new, old save ignored).
- **MAVOFishComp 1.0.0 (idea 8):** one deterministic random 2h window per ISO week (seeded RND, day+hour from config ranges, Sat/Sun 16-20h); any PlayerFishEvent CAUGHT_FISH in survival = 1 pt + bonus per material (SALMON 2, PUFFERFISH 3, TROPICAL_FISH 4); bossbar countdown; top3 pay config prizes (15k/8k/5k) + participation 500 for >=3 catches; offline payouts queue in data.yml and pay on join; /fishcomp status|top (all-time), admin start/end/reload; window restored after restart if still live.
- **MAVOMail 1.0.0 (idea 9):** /mail send <player> [coins] [message...] (+ held item when attach-hand=true), coins withdrawn incl. send-fee; /mail GUI paged (icon = item else gold ingot scaled) with claim per item (coins banked, items to inventory) + Collect All; 7-day expiry, max 100 (oldest dropped), join notification with unread count; offline target safe; admin /mail purge <player>; console purge only.
- **MAVOSeasonal 1.0.0 (idea 10):** yearly windows from config (Spooky 10-25..11-02 SUGAR 6%, Festive 12-20..01-05 COOKIE 6% year-wrap, Anniversary 09-01..09-08 CAKE 8% + xp-multiplier 2.0); EntityDeathEvent mob-drops candy during a season; xp-multiplier > 1 forces MAVODoubleXp via reflection `force(endMs,mult)` for the whole window, `clearForce()` on end/disable (DoubleXp got the force API); /season status, admin toggle <id> (manual force, saved) + reload; broadcast + bell on season start.
- **MAVOAuctionHouse 1.1.0 (idea 6):** Listing record += buyNow/bid/bidder/bidEnd; `/ah add ... [buy-now]` (Buy Now >= start price, <= max), listing lore shows Buy Now + current bid + bid countdown; click listing -> detail GUI: Buy at price / BUY NOW / Bid +10% / Bid +25% / custom hint; `/ah bid <id> [amount]` (min raise = 5% of current max(price,bid); bid at/above Buy Now = instant buy), `/ah buy <id>`; bids withdraw coins to escrow and refund the previous bidder (offline -> pending); last-60s bids extend bidEnd +5 min; expiry settles top bidder: item to their inbox (tag "auction won"), seller paid net, slot locked; buyer inbox full -> refund bidder + item back to seller; cancel returns escrow; buy-now also refunds current bidder; data.yml backward compatible (old listings no bid fields).
- **Versions:** all poms + plugin.yml + finalName bumped (3.15.5 / 1.1.0 / 1.0.0 x6 / 2.0.0); every new pom has papermc + jitpack repos (VaultAPI only where needed).
- **CI:** run 34017630060 FAILED (FishComp missing saveData() x5, Mail long->int icon cast) -> fixed 28a75d7 -> run 34017794799 SUCCESS -> 79f8d30 (22 modules, jars committed; old AH 1.0.3 / Pets 1.0.0 / Professions 3.15.4 removed by workflow `rm -f` lines added for them).
- **Jars:** MAVOBossRaid-1.0.0 15,465 B / MAVOCrates-1.0.0 16,868 B / MAVODoubleXp-1.0.0 9,463 B / MAVOPets-2.0.0 19,391 B / MAVOFishComp-1.0.0 14,050 B / MAVOMail-1.0.0 13,569 B / MAVOSeasonal-1.0.0 11,222 B / MAVOAuctionHouse-1.1.0 41,169 B / MAVOProfessions-3.15.5 59,436 B (root + jars/ identical; plugin.yml versions verified).
- **Logo:** `mavocraft-logo/` = generator + preview PNG + `fill-commands.txt` (106 relative /fill commands, 126x18 blocks: gold frame, black concrete background, glowing sea-lantern letters) - stand at the TOP-LEFT corner looking north at ceiling level and paste.
- **Deploy:** STOP; delete MAVOProfessions-3.15.4.jar + MAVOAuctionHouse-1.0.3.jar + MAVOPets-1.0.0.jar; upload the 9 jars; keep all configs (Professions migrates skip-to-tick itself; AH/Pets save-compatible; new plugins self-create folders). One-time: /boss setarena (+ /boss now test), /crate set per block, /xpboost status, /season status, /fishcomp status. Boot lines listed in DEPLOY.md section 5.

## 2026-09-06 HOTFIX 27 — MAVOCRAFT logo v2 (creeper edition, matches reference art)
- **User supplied reference image:** chunky cream cracked-stone "MAVOCRAFT" letters on black, creeper face cutouts inside BOTH A's, 3D extrusion/shadow to the bottom-left.
- **Rewrote `mavocraft-logo/gen_logo.py`:** 7x9 pixel font scaled x2 (letters 14x18 blocks), margin 2; palette bone_block (cream letters) / black_concrete (background) / deepslate (shadow); shadow = every letter pixel copied at offset (-2 x, +2 z) = south-west, so from below it reads as a 3D extrusion toward bottom-left; glyph A has 2x2 creeper eyes + 4x2 mouth with teeth gaps (both A's).
- **Output:** `mavocraft-logo/fill-commands.txt` = 388 relative /fill commands, canvas 164 x 24 blocks (1 thick, 1,052 letter + 672 shadow blocks); `MAVOCRAFT-logo-preview.png` = top-down render; `README.md` = palette + placement instructions (feet on ceiling bottom plane, stand top-left corner facing north, paste; alt `cracked_stone_bricks` for real cracks).
- **Placement rule:** facing north while looking up = screen-up is -z (north), screen-left is -x (west); glyph row 0 = north so letters read upright; shadow offset south-west matches the reference.

## 2026-09-06 HOTFIX 28 — MAVOCRAFT logo v3 (absolute coords, ceiling build)
- **User gave real ceiling bounds:** Y=250 ceiling spans X -2629..-2529 / Z -1735..-1635 (100x100), center (-2579,-1685); spawn center = (-2579,200,-1685); build UNDER the ceiling at Y=249 so an `/fill air` undo never touches the ceiling.
- **Logo v3:** absolute coordinates everywhere (no ~ or standing/orientation). Canvas 75x23 = 75% of the 100-wide ceiling, centered (-2579,-1685), margins 13/13 (x) + 39/39 (z). Face 70x16 (2px font, GAP 1), gold_frame border, black_concrete bg, bone_block letters, deepslate shadow offset (-2 x, -2 z) = bottom-left of the looking-up view (screen-top = south, screen-right = east when facing north looking up).
- **Files in mavocraft-logo/:** `test-fill.txt` (7 lines: 3x3 gold center + 4 corner blocks), `frame-fill.txt` (6 lines: gold border only), `fill-commands.txt` (440 lines: undo-air line + bg + frame + 616 letter + 402 shadow blocks), `MAVOCRAFT-logo-preview.png`, `gen_logo.py`, `README.md` (steps + undo command).
- **Undo:** `/fill -2616 249 -1696 -2542 249 -1674 minecraft:air` (full canvas at Y249; ceiling Y250 untouched).

## 2026-09-07 HOTFIX 29 — MAVOCRAFT logo v4 (orientation fix: read it facing west at the 3 villagers)
- **User feedback (screenshots):** frame built at Y249 aligns, but the logo faced the wrong way. F3 showing FACING WEST (-X) when looking at the 3 villagers at spawn (-2579,200,-1685). Looking straight up from that view: screen-right = north (-Z), screen-top = east (+X).
- **v4 rotation:** word now runs SOUTH->NORTH, letter tops point EAST (v3 ran west->east = north-facing viewer). Canvas 22 (X) x 76 (Z) = X -2590..-2569 / Z -1723..-1648, center (-2579,-1685), Y=249; margins 3, GAP 1, SCALE 2, 616 letter + 402 shadow blocks, shadow offset (-2 x, +2 z) = bottom-left of the viewed image (west+south). Fits inside the 100x100 ceiling with 39+ block margins (~75% size).
- **Files:** test-fill.txt (8 lines), frame-fill.txt (6 lines), fill-commands.txt (178 lines), preview PNG now renders the TRUE viewing orientation (imX = Z0..ZS -> ZS-z, imY = X -> XE-x).
- **Undo:** /fill -2590 249 -1723 -2569 249 -1648 minecraft:air.

## 2026-09-07 HOTFIX 30 — MAVOCRAFT logo v5: two datapacks (75% frozen + 95% glowing border edition)
- **User feedback (screenshot):** 75% build renders but shadow looks bad + letters don't stand out; wants 90-95% wide (letters bigger, less pushed); instead of shadow -> 1-block border AROUND each letter, brighter than letters; both A's = lime green (kick logo style); keep current as MAVOcraft-75-datapack, add MAVOcraft-95-datapack; datapack + command build; TAB toggle for easier checking.
- **TAB toggle (no code):** TAB plugin command `/tab scoreboard [on/off/toggle] [-s]` (permission tab.scoreboard.toggle) hides the sidebar overlay - user can hide it while looking at the ceiling. MAVOHud only supplies placeholders.
- **Two datapacks at Y=249 (ceiling Y=250 untouched), same viewing spot (spawn, face WEST at 3 villagers, look up; word runs south->north, letter tops east):**
  - MAVOcraft-75-datapack = frozen v4: 22x76 (X -2590..-2569, Z -1723..-1648), bone_block letters, deepslate shadow (-2x,+2z), gold frame.
  - MAVOcraft-95-datapack = new v5: 24x95 (X -2591..-2568, Z -1732..-1638, ~95% of the 100x100 ceiling, margins small), sea_lantern letters (bright), glowstone 1-block border around EVERY letter (no shadow), lime_concrete A's, gold frame.
- **Datapack layout (same convention as MobFarm, proven to load):** pack.mcmeta pack_format 48 + supported_formats [48,999]; data/mavocraft75|95/function/{logo_test,logo_frame,logo_build,logo_clear,logo_info}.mcfunction; every line = `execute in minecraft:overworld run fill ...` (NO leading /). Console: /datapack enable "file/MAVOcraft-95-datapack" then /function mavocraft95:logo_build.
- **Files:** mavocraft-logo/datapacks/*.zip (built), datapacks-src/, v75/ + v95/ (fill-commands/test/frame fallbacks + preview PNG), README.md. Generator gen_logo.py emits both + zips (asserts canvas <=95% and inside ceiling).

## 2026-09-07 HOTFIX 31 — CW#4 next-10 plugins (ideas 11-20) all built + COMPLETE-CHECKLIST.md
- **All 10 plugins built from scratch (no external jars needed), Paper 26.2 API, `MAVO<name>-1.0.0`:** MAVOTpa (idea 11), MAVOLocks (12), MAVOTimber (13), MAVOCrafting (14), MAVOEnchants (15), MAVOCouples (16), MAVOMiniboss (17), MAVOSpawners (18), MAVODuels (19), MAVOGuilds (20). Sources in `sources/src-*`; CI (workflow build-mavo.yml) built all, jars committed root + jars/.
- **Feature recap (short):** Tpa = /tpa|tpahere|tpaccept|tpdeny|tpacancel, 30s cd/120s expiry, 3s stand-still warmup, monsters cancel. Locks = /lock|unlock|trust|untrust (max 50), linked double-chest/door/trapdoor halves, explosion-proof, data.yml; owner break frees all halves. Timber = BFS fell (max 200) with axe, 1 durability/log, Lumberjack XP reflection, /timber toggle. Crafting = config-shaped recipes (name tag/saddle/lead/chainmail), /crafting list|reload. Enchants = gem PDC items (VEIN/SMELT/XP/LIFESTEAL, max 3/tool, tier 1-3), gem main-hand + tool offhand; /maenchant list|gem (OP). Couples = /marry|accept|deny|divorce|/couple info|sethome|home|tp; shared couple home (both sides write/read same key); heart particles <6 blocks. Miniboss = 5 bosses (witch lord/desert pharaoh/ice queen/jungle shaman/nether overlord) every 45 min, 1-5k from WORLD spawn, max 3 alive, surface y>50; coins + LuckyCoins/Crates reflection + trophy head; /miniboss status|locate|reload. Spawners = silk-touch gives tagged spawner item, /spawner info. Duels = /duel [bet]|daccept|ddeny|dstats [top], /duelarena (OP), radius 20, house cut 10%, Vault, walkout/quit = forfeit. Guilds = /guild create|invite|accept|decline|leave|disband|list|info|promote|demote|kick|chat|sethome|home|claim|unclaim|unclaimall|map [r]|desc|reload (+ /g <msg>, /gchat); chunk claims (max 8) must touch existing claim (require-adjacent), protection vs outsiders (build/interact/animals/pve/pvp/explosions per config), Vault create/claim costs, data.yml.
- **Paper 26.2 API gotchas hit (fixed, verified via jd.papermc.io/paper/26.2):** `Location.midpoint()` does NOT exist (compute manually); `Tag.ORES` removed (per-ore tags COAL_ORES etc. or explicit switch); `EntityDeathEvent.getDroppedExperience()` -> `getDroppedExp()`; `CreatureSpawner.getActivationRange()` -> `getRequiredPlayerRange()` + `getSpawnRange()` (BaseSpawner); `Bukkit.getEntity()` returns Entity (cast/accept Entity in helpers); variable shadowing `a` collision.
- **CI:** run 34074563640 FAILED (midpoint + Tag.ORES + getDroppedExperience + getActivationRange + Entity->LivingEntity), published build.log (fetched via `git show <sha>:build.log` since `gh run view --log` EOFs on this repo); fixed 9261467+dbac640; run 34075259729 SUCCESS -> 06e5415 (64 jar updates incl. 10 new MAVO*.jar at repo root + jars/).
- **Complete Checklist:** `COMPLETE-CHECKLIST.md` — 7 parts: A boot/health (OP), B vanilla, C third-party (TAB/LuckPerms/Vault/EconomyShopGUI/ClaimChunk/CoreProtect/EssentialsX/BlueMap/Geyser incl. `/tpa` conflict check), D all 45+ MAVO plugins (D-11..D-20 = the 10 new with OP + player rows), E big-test-run order for the 10 new jars + restart persistence check, F datapacks/logo/TAB toggle, G report template.
- **Deploy:** STOP; upload the 10 new jars MAVOTpa/MAVOLocks/MAVOTimber/MAVOCrafting/MAVOEnchants/MAVOCouples/MAVOMiniboss/MAVOSpawners/MAVODuels/MAVOGuilds (all `-1.0.0.jar`); keep all configs (each self-creates its folder; data.yml starts empty). One-time: /duelarena; /miniboss reload (config already defaults); /guild create test; /crafting list. Boot log must show all 10 "enabled".
- **Next:** user big test run with the previous 9 jars + these 10 using COMPLETE-CHECKLIST.md; then Discord restructure (version logs stand out, short Features + per-feature threads, Current Work tick lists) — AFTER jars delivered.

## 2026-09-07 HOTFIX 32 — boot-log fixes: 4 invalid plugin.yml + datapack pack.mcmeta schema
- **User boot log:** 4 plugins failed `Invalid plugin.yml` (SnakeYAML "mapping values are not allowed here") - unquoted `description:` values containing `: ` (colon+space). Affected: MAVOCrafting (`List custom recipes (admin: reload)`), MAVOEnchants (`Enchants: list / inspect; admin: gem give`), MAVOMiniboss (`Miniboss status (admin: spawn all, reload)`), and **MAVOPets 2.0.0** (pre-existing from CW#3 delivery: `...abilities: carry a stack...`). Fixed by wrapping those 4 strings in double quotes in `sources/src-*/.../plugin.yml`; automated scan now checks every plugin.yml for unquoted colon-space values (all clean).
- **CI:** run 34076093656 SUCCESS -> 5eb6e9c (jars rebuilt: MAVOCrafting/MAVOEnchants/MAVOMiniboss-1.0.0 + MAVOPets-2.0.0, root + jars/; plugin.yml re-verified inside each jar).
- **Datapacks warning** (`Error reading pack metadata, attempting fallback type` x2, packs still loaded): MC 1.21.9+ requires new pack.mcmeta schema - `min_format` + `max_format` required, `supported_formats` removed, `pack_format` only needed if supporting <82 (data packs). Server 26.2 = data format 107.1. Both MAVOcraft-75/95 datapacks rebuilt with `{"pack": {"min_format": 82, "max_format": 107, "description": ...}}`; gen_logo.py template updated - future regens stay compatible. Zip SHAs: 75 = d7c317bf..., 95 = f118b363....
- **Deploy (this hotfix):** STOP; replace MAVOCrafting-1.0.0.jar + MAVOEnchants-1.0.0.jar + MAVOMiniboss-1.0.0.jar + MAVOPets-2.0.0.jar AND (optional but recommended) world/datapacks/MAVOcraft-75-datapack.zip + MAVOcraft-95-datapack.zip. Keep all configs. Boot log: no "Invalid plugin.yml", no "Error reading pack metadata"; MAVOCrafting/MAVOEnchants/MAVOMiniboss/MAVOPets all "enabled".

## 2026-09-07 HOTFIX 33 — datapack /datapack enable ID tip (packs were already loaded)
- **User boot log 2 (02:30):** all fixes deployed - 58 plugins initialized, no Invalid plugin.yml, NO pack metadata errors; MAVOCrafting (7 recipes), MAVOEnchants (4), MAVOMiniboss (5 bosses), MAVOPets (6 types) all "enabled".
- **User issue:** `/datapack enable MAVOcraft-95-datapack` → `Unknown data pack 'MAVOcraft-95-datapack'`; `"file/MAVOcraft-95-datapack"` also failed. Cause: (a) missing `file/` prefix, and (b) zip pack IDs KEEP the `.zip` extension: **`/datapack enable "file/MAVOcraft-95-datapack.zip"`**. Real ID confirmed in first boot log: `Found new data pack file/MAVOcraft-95-datapack.zip`.
- **Key point:** packs in `world/datapacks/` are AUTO-ENABLED on first boot (02:19 log) and just load silently afterwards (02:30 log: no "Found new" lines = already in level.dat enabled list). `/datapack enable` is NOT needed; verify with `/datapack list enabled` and run `/function mavocraft95:logo_test` → `logo_frame` → `logo_build` directly.
- Docs updated: mavocraft-logo/README.md install section + COMPLETE-CHECKLIST.md Part F with the exact `.zip` ID syntax.

## 2026-09-07 HOTFIX 34 — logo v95 180° flip (in-game orientation bug)
- **In-game test (user, 03:44):** `/function mavocraft95:logo_build` renders the full v95 logo but UPSIDE DOWN from the spawn viewpoint (screenshot 2026-09-07_03.44.44.png). Design/look approved — only orientation wrong.
- **Root cause:** the hotfix-29 F3 note had the looking-up view 180° wrong. Correct view (confirmed by re-rendering the deployed fill commands into ASCII with both candidate mappings): standing at spawn facing WEST and looking straight up → **screen-right = SOUTH (+Z), screen-top = WEST (−X)** (NOT north/east). With the old note the letters came out rotated 180°.
- **Fix (v95 only):** `gen_logo.py` adds `flip=True` to the "95" variant — letter tops now point WEST and the word runs north→south (same 24×95 block footprint, same margins/gap/materials). v75 keeps `flip` unset (frozen v4, 180° rotated from spawn — intentional, design untouched).
- **Refactors to keep both paths honest:** shared `_stroke(cfg, ch)` drives `face_blocks` + the lime-A recolor (can't diverge); `geometry()` resolves `xTop`/`zStart` per orientation; preview PNG now renders the TRUE in-game view (`setb(z-Z0, x-X0)`) for BOTH variants — v95 preview reads upright, v75 preview shows the frozen layout as it really appears.
- **Verified:** v95 flipped ASCII reads MAVOCRAFT upright left→right; v75 datapack zip content byte-IDENTICAL to previous commit (diff -r of extracted tree empty; zip SHA unchanged d7c317bf...) — frozen v4 preserved; canvas 24×95 / clear still the full canvas (fill -2591 249 -1732 → -2568 249 -1638 air). NEW v95 zip SHA = 255ae59d4cfdaacb28f041f55f1e1dd134bad9691ad4f76ca762fd00d25dc5f6.
- **Deploy (no restart, players online):** overwrite `world/datapacks/MAVOcraft-95-datapack.zip`, run `/reload`, then `/function mavocraft95:logo_clear` and `/function mavocraft95:logo_build` (console or OP). v75 zip unchanged. Then confirm from spawn.

## 2026-09-07 HOTFIX 35 — test-round fixes: Timber exploit, /craft for everyone, gem shop + ore drops, /hunt + boss broadcasts (Discord pack restructured)
- **User test feedback (A-D + D1-D12 done):** D13 Timber exploit (dark forest gives 10-150 logs + XP; logs -> sticks -> villager/shop exploit; leaves don't fall); D14 /craft is the Essentials op-only workbench (no permission for survivors) and /crafting only lists custom recipes; D15 OK but wants a gem shop (1M/2M/4M/8M per tier) + rare gems from mining ores (1% / 0.5% / 0.25%, halving); D17 needs /hunt to leave spawn + boss location broadcast every 5 min; D16/D18-20 later.
- **MAVOTimber (anti-exploit):** new config keys `max-logs: 10` (hard cap per tree, shorter trees still fall fully), `xp-per-log: 0.5`, `xp-cap-per-tree: 10` (max profession XP per tree), `leaves-fall: true`, `leaves-max: 750`. Crown leaves now decay ~10 ticks after the trunk falls (5% sapling / 2% stick, correct sapling per tree type). BFS collects logs up to the cap and walks the crown separately (max-blocks stays a walk safety cap). /timber status shows the caps. Maven config: `getConfig().options().copyDefaults(true); saveConfig()` adds the new keys to an existing server config.
- **MAVOProfessions (harder):** lumberjack `xp-base` 350 -> 600, `xp-growth` 1.035 -> 1.04 (~170 trees per early level with the 10-log cap). Other professions untouched (Gambler/Sleeper rebalances stay). **Disk migration added** (same pattern as the 3.15.4 Gambler migration): if the live config still has xp-base < 600 or growth < 1.04, Professions rewrites them to 600/1.04 and logs "HOTFIX 35: Lumberjack rebalanced" - no manual config edit needed.
- **MAVOCrafting (/craft for everyone):** `/craft` was Essentials' workbench alias (permission `essentials.workbench`, op-only). The plugin now declares `craft` AND hijacks the plain label at enable: Paper refuses to override existing commands, so it removes `craft` from the command map (via reflection on `SimplePluginManager.commandMap` -> `SimpleCommandMap.getKnownCommands()`) and registers a custom `Command` subclass (PluginCommand's ctor is package-private - a subclass is required). `/workbench` + `/e craft` keep working. `/craft` = paginated GUI (54 slots, 45 recipes/page) of the **50 beginner recipes** from `beginner-recipes` in config.yml (click = consumes ingredients + gives the item, 300ms debounce); `/craft <id>` jumps to that recipe's page. `/crafting` keeps `list`/`reload` (custom recipes still craft in a normal table).
- **MAVOEnchants (gems economy):** tiers extended to IV (`max-tier` migrated 3 -> 4 + logged); `/gemshop` (all players, Vault) shows 4 gem types x tiers I-IV with `shop-prices` 1M / 2M / 4M / 8M coins (configurable, any number of tiers); click a gem to buy (insufficient funds message, balance item shown). Mining ores: `mine-gem-enabled`, `mine-gem-base-chance: 1.0`% for tier I, `mine-gem-levels: 4` - chance halves per tier (0.5%, 0.25%, 0.125%), random type, tier never exceeds max-tier. pom got VaultAPI 1.7.1 (provided). `/maenchant shop` opens the same GUI; `/maenchant gem <p> <type> [tier]` clamps to max-tier.
- **MAVOMiniboss (/hunt + broadcast):** new `/hunt` for everyone - 30s per-player cooldown (`hunt-cooldown-seconds`); with a live boss -> teleports within `hunt-boss-radius: 60` blocks (surface, no water/lava); no boss -> random surface spot `hunt-bare-min..max: 500..2000` from world spawn (`hunt-enabled`). Boss locations broadcast every `broadcast-interval-minutes: 5` as `around x, z (±100 blocks) - /hunt to go hunting!`; `/miniboss broadcast` forces one now; `/miniboss status` shows hunt + broadcast state. Spawn broadcast now points to /hunt.
- **Config migration for all four plugins:** `saveDefaultConfig()` + `getConfig().options().copyDefaults(true)` + `saveConfig()` in onEnable - existing server configs get the new keys WITHOUT losing old values.
- **Docs:** COMPLETE-CHECKLIST.md D-13/D-14/D-15/D-17 rows updated (new tests), MAVOCRAFT-COMMANDS.md gained MAVOTimber/MAVOCrafting/MAVOEnchants/MAVOMiniboss tables.
- **Discord restructure done (user direction):** new pack `DISCORD-PACK-2026-09-07.md` - (1) version log = one titled heading + block per version so versions stand out, v1-v22 (v22 = Hotfix 35 fixes), (2) Features = ONE short line per feature + `📌 more in #thread-name`, with a thread-name list to create in ✨features (one per feature), (3) Current Work = one block per item with `━━━━` separators + tick list (`✓ CURRENT WORK 1 ... SHIPPED` etc.; current work 4 shows D13-D17 status). Old pack kept as `DISCORD-PACK-2026-09-05.md`.
- **Not done:** D16 (needs 2+ players), D18/D19/D20 - user will test later.

## 2026-09-07 HOTFIX 36 — Timber fells GROWN TREES only (no shipwrecks/village houses/player builds)
- **User feedback:** axe was felling everything made of logs - houses, shipwrecks, village log walls. Must apply only to grown trees.
- **Detection (natural-trees-only: true):** the connected component is only felled when BOTH hold: (1) it contains LEAVES (a real tree crown - structures/houses have no natural crown), (2) it contains NO player-placed log/leaf (tracked by BlockPlaceEvent; grown trees from saplings are NOT "placed"). Fails → the clicked log breaks vanilla + message "That's not a grown tree - Timber only fells natural trees (no builds/shipwrecks)." / "Timber skips player-built wood - plant saplings for real trees."
- **Placed tracker:** in-memory Set<String> of "world,x,y,z" + persisted in data.yml under "placed" (loaded on enable, dirty-flagged + 100-tick debounced save, saved on disable). Keys removed when placed blocks break / get felled / decay, so a tree that later grows at the same coords is not wrongly excluded.
- **Config:** new `natural-trees-only: true` (copyDefaults adds it to existing configs); /timber status shows "grown trees only".
- **Files:** Timber.java (BlockPlaceEvent listener, grown-tree gate in tree(), messages), config.yml, COMPLETE-CHECKLIST D-13 rows, MAVOCRAFT-COMMANDS note, DISCORD-PACK-2026-09-07 v23 + Timber feature line.

## 2026-09-07 HOTFIX 37 — fix round 2: empty /craft menu, tall trees refused, broadcast didn't spawn, gem charges/cooldowns L1-10
- **User in-game (screenshots):** /craft opened "Craft - basics (page 1/1)" with NO recipes; Timber refused tall trees as "not a grown tree" while small trees fell; /miniboss broadcast said nothing because no boss was alive; gems need real per-level power via charges/cooldowns.
- **Root cause 1 (empty /craft):** boot log showed `MAVOCrafting enabled - 7 custom recipe(s), 0 beginner recipe(s)`. `getConfig().options().copyDefaults(true) + saveConfig()` does NOT write missing NESTED sections into an already-existing config.yml, so the live server never got `beginner-recipes`. FIXED with a real disk merge: load `config.yml` into a YamlConfiguration, merge every key/section missing vs the bundled resource (recursive `mergeMissing`), save, `reloadConfig()` — then `loadBeginner()` sees all 50. Same helper added to MAVOEnchants (new `gem-charges` + `shop-prices` 5-10 get written into existing configs).
- **Root cause 2 (tall trees):** the BFS stopped expanding when `logs.size() >= maxLogs`, so the leaf crown of a tall spruce/dark-oak was never reached -> hasLeaves=false -> "not a grown tree". FIXED: logs always expand neighbours even past the fell cap (cap only limits how many are FELLED, not how far the walk goes; leaves also keep expanding past the decay cap). Tall trees now fell their capped 10 logs.
- **Miniboss:** every 5-min auto-broadcast and `/miniboss broadcast` now call `spawnOne()` FIRST if no boss is alive, then broadcast its location — hunt is never an empty callout.
- **Gems L1-10 charge system (config `gem-charges`):** L1 10 uses/10 min, L2 5/9, L3 10/8, L4 15/7, L5 20/6, L6 25/5, L7 30/4, L8 35/3, L9 50/2, L10 unlimited (uses 0 + cooldown 0). Cooldown starts on the FIRST use of a fresh pool; pool refreshes when it ends (so 9 uses remain after the first). State per player persisted in `data.yml` (tier, uses-left, cooldown-until; reloaded on enable, debounced save, saved on disable). Recharging message throttled to once per 4s. `max-tier` migrated 3->4->10 with log; `mine-gem-levels` bumped to 10 (chances still halve: 1%, 0.5%, 0.25% ...); shop prices extended 1M->512M (1,2,4,8x2^) and the 54-slot shop shows all 4 types x 10 tiers with charge lore. `/maenchant charges` shows per-type uses/cooldown. VEIN spends 1 charge per vein break; SMELT 1 per vein (via veinBreak) or per single ore; XP/LIFESTEAL 1 per kill; effects do nothing (vanilla) while recharging.
- **Docs:** COMPLETE-CHECKLIST D-13/D-14/D-15/D-17 rows, MAVOCRAFT-COMMANDS (enchants charges + broadcast spawn note), DISCORD-PACK-2026-09-07 (v24 + feature lines), this log.
- **Deploy:** replace MAVOCrafting-1.0.0.jar, MAVOTimber-1.0.0.jar, MAVOEnchants-1.0.0.jar, MAVOMiniboss-1.0.0.jar; keep configs (merge upgrades them); restart or /reload. Verify boot: "7 custom recipe(s), 50 beginner recipe(s)"; Enchants "max tier 10, charge table 10 levels"; then /craft shows recipes, tall tree fells 10, /miniboss broadcast spawns + callout, gem charges work.
- **SHIPPED 2026-09-07:** commits `20daffa` (code) + `7c4d6a7` (CI compile fix: mergeMissing params ConfigurationSection) + `b6cd395` (ci: rebuilt plugin jars, run 34091543224 success) + `45c66a3` (zip refresh, SHA a24571db21ce884540b83071c6024522bf32708b664c8fa93eb0ab36f3291064). Jar checks verified: Crafting config 7 custom + 50 beginner, Enchants gem-charges 10 / shop-prices 10 / mine-gem-levels 10 / max-tier 10. Deploy per line above, then in-game verify.

## 2026-09-07 HOTFIX 38 — shift-click broken everywhere: /craft menu swallowed every shift-click
- **User report:** shift right-click broken for ALL players - cannot move items inventory <-> furnaces/chests/etc (both directions). Suspected chest lock.
- **Root cause (NOT Locks):** Locks only cancels PlayerInteractEvent. The culprit was MAVOCrafting (since Hotfix 35): `onClick(InventoryClickEvent)` did `if (!p.getOpenInventory().getTopInventory().equals(e.getView().getTopInventory())) return; if (e.getClick().isShiftClick()) e.setCancelled(true);` - the scope check was ALWAYS true (same view), so it cancelled every shift-click in every inventory server-wide. Only isShiftClick in the whole source tree.
- **Fix:** track our /craft inventory per player (`Map<UUID, Inventory> openGuis`, set in openBeginner, removed in InventoryCloseEvent only when the closed inv matches). onClick now returns unless the open top inventory IS the recorded /craft GUI; shift-click cancel stays but only inside the menu; all other containers are untouched.
- **Files:** Crafting.java (+InventoryCloseEvent import, openGuis, onClose, onClick gate), DISCORD-PACK-2026-09-07 v25, this log.
- **Verify:** shift-click works in chests/furnaces/barrels both ways; /craft still opens, paginates, crafts, close button works; no items can be shift-dropped into the /craft menu.

## 2026-09-07 HOTFIX 39 — Timber leaves the rest of tall trees standing (floating trunk)
- **User in-game:** a tall dark forest tree was cut, giving ~9 logs + the crown fell, but the REST of the trunk logs stayed floating. (Hotfix 37 had re-enabled tall trees but only collected/BROKE max-logs = 10 logs; the surplus trunk was never removed.)
- **Root cause:** `tree()` collected logs into the fell set only up to `maxLogs`; the walk passed the cap (crown check) but breaking was capped, so only the first ~10 BFS logs (the base chunk) were removed - the rest of the trunk remained.
- **Fix:** collect EVERY log of the connected component (walk safety unchanged) and break them ALL - the whole tree falls, nothing floats. The anti-exploit cap now applies to the HARVEST only: drops capped at max-logs (incl. the clicked one), axe durability capped at the collected count, Lumberjack XP based on collected logs, message "whole tree - 10 collected, cap 10/tree". Shorter trees still give their natural amount. Natural-trees-only guard unchanged (shipwrecks/village/player builds still break vanilla).
- **Files:** Timber.java (whole-component collection + capped drops/durability/XP + message), config.yml comments, COMPLETE-CHECKLIST D-13 rows, DISCORD-PACK-2026-09-07 v26, this log.
- **Verify:** chop tall dark oak / giant spruce -> ENTIRE tree falls (no floating logs), 10 logs collected, leaves decay; small tree -> natural amount; houses/shipwrecks still safe.

## 2026-09-07 HOTFIX 40 — /craft is a GUIDE (no auto-craft, 100 recipes) + gemshop hover lore + restart note
- **User feedback:** /reload only reloads recipes/advancements - correct, jar changes need a FULL server restart (plugins are loaded when the JVM starts; /reload cannot swap jars). Tell users: replace jar(s) -> stop -> start. (Hotfix 38/39 are NOT live unless the server was restarted after the jars were swapped.)
- **/craft GUIDE (was auto-craft on click):** clicking a recipe no longer consumes ingredients/grants items. It now opens a display-only 3x3 preview GUI showing the REAL recipe (fetched from the server's recipe registry - ShapedRecipe grid / ShapelessRecipe list + result count + hint text) and unlocks the recipe in the player's vanilla recipe book (discoverRecipe - press E). Back button returns to the list page. Paper has NO "open recipe book" API, so this is the closest possible (E unlocks + preview grid). Preview clicks are all cancelled; old craft()/count()/remove() helpers removed (lastCraft debounce kept for clicks).
- **100 beginner recipes:** config beginner-recipes extended 50 -> 100 (planks variants, iron armor/doors/bars, compass/clock/map, painting/item frame, stone/sandstone/bricks, lantern, barrel, composter, lectern, grindstone, stonecutter, smoker, blast furnace, brewing stand, hopper, dispenser, note block, jukebox, ender chest...). GUI: 45/page -> 3 pages. Text "50 basics" -> "100 basics" (command desc, /crafting list tip).
- **Gemshop hover:** gem lore now shows "Effect: ..." for THAT tier (HOTFIX 40): VEIN = up to veinPerTier*tier ore blocks/charge; XP = +xpPct*tier% XP; LIFESTEAL = heal lifestealHearts*tier hearts; SMELT gained REAL tier scaling (new config smelt-bonus-per-tier: 10% per tier, T10 = 100% chance of a bonus smelted item) so higher levels have a describable benefit. makeGem() made an instance method (was public static, only used internally) so lore can use config values.
- **Files:** Crafting.java + config.yml, Enchants.java + config.yml (smelt-bonus-per-tier - mergeMissingDefaults adds it to existing configs), COMPLETE-CHECKLIST D-14/D-15 rows, DISCORD-PACK-2026-09-07 v27, this log.
- **Verify:** /craft shows 100 recipes; click = preview grid only (NOT crafted, nothing consumed); recipe appears in recipe book on E; Back/Close work; gemshop hover shows tier-specific effect + charges + price; SMELT T1 ~10% bonus item, T10 always bonus.

## 2026-09-07 HOTFIX 41 — Discord pack: feature threads now carry DETAILED content + 41/41 name match
- **User feedback:** the pack told them to create feature threads but included only short one-liners ("more in #thread") with NO detailed info to paste into the threads; also the thread-name list did not match the feature list.
- **Fix (DISCORD-PACK-2026-09-07.md):** each of the 41 features now has TWO paste-ready blocks: (1) short announcement post for `✨┃features`, (2) DETAILED first-message block for the thread (commands, costs, rules, rewards). Thread list rebuilt to exactly 41 names (added `⛏ professions` - was missing), verified programmatically: zero thread names without a detail block and zero detail blocks without a thread name.
- **Details sourced/verified from:** DISCORD-PACK-2026-09-05.md (old long blocks) + MAVOCRAFT-COMMANDS.md + COMPLETE-CHECKLIST.md + context hotfix entries. Content updated to current state: /craft 100-recipe GUIDE, Timber whole-tree/10 collected, gems L1-10 charges + per-tier effect hover (VEIN 6xT / SMELT +10%/tier / XP +50%/tier / LIFESTEAL T hearts), miniboss spawn-first broadcast, MobFarm 2.7.7 (36 bays, order/enable), Pets 2.0, AH 1.1, OS sleep 07:00, vault 1.7.1 door menu etc.
- This is a docs-only change - no jars, no CI run, no zip refresh needed.

## 2026-09-07 HOTFIX 42 — crates 2.0 (GUI + % drop rates, key drops, holos) + boss drop announce + hunt 200
- **User (stream):** created the 3 crates; wants proper rebalanced drop pools; crates should open a GUI showing each item's drop rate; keys should drop from farming/mining/fishing at Common 1% / Rare 0.05% / Mythic 0.01%; holos for all 3 crates; broadcast showed `&a&l` raw + a "?" before every event (untranslated display + ✨ glyph not in MC font); /hunt should land ~200 blocks from the boss (1000 = too much walking); bosses should announce their drop pool in % before the difficulty/HP.
- **MAVOCrates:** right-click now opens a 54-slot GUI (drop rates, exact % computed from weights) + OPEN button (uses 1 key, rolls, closes). Pools rebalanced to 10 rewards each summing 100% (common: 2.5k coins 30%/coal 15/iron 15/gold 10/diamond 8/emerald 6/Lucky 5/logs 5/netherite 3/10k coins 3 ...; rare: 15k 25/...; mythic: 100k 25/.../elytra 3). Keys drop on BlockBreak (ores = mining, mature Ageable crops = farming) and CAUGHT_FISH at 1%/0.05%/0.01% per action (key-drops config, survival only). Floating TextDisplay holo (PDC-tagged) above every crate block, rebuilt on enable/reload/`/crate resholo`, removed on unset/clear. Broadcast/private messages now cc() the display (was raw & codes); `✨` replaced with `»` (not in MC font). Config migration `pool-version: 2` rewrites crates+key-drops on live configs (data.yml blocks untouched). GUI clicks gated by per-player tracked inventory (hotfix 38 pattern) + InventoryDragEvent cancel.
- **MAVOMiniboss:** spawn broadcast now shows `Drops: 100% coins · luckyChance% Lucky · crateChance% Crate Key` + HP before /hunt (dropsLine()); kill rolls lucky/crate chances independently and broadcasts what actually dropped; per-boss config `lucky-chance`/`crate-chance` (defaults 50, bundled: 60/50, 50/60, 55/60, 50/65, 65/40); `/hunt` boss radius default 60 -> 200 + migration clamps ANY config >200 back to 200 with a log (live was 1000); `⚔` -> `»` in messages.
- **Files:** Crates.java + config.yml, MiniBoss.java + config.yml, MAVOCRAFT-COMMANDS (crates section + miniboss notes), COMPLETE-CHECKLIST D-17 rows, DISCORD-PACK v28 + crate/miniboss threads, this log.
- **Deploy (after CI):** replace MAVOCrates-1.0.0.jar + MAVOMiniboss-1.0.0.jar, full restart (NOT /reload). Keep data.yml (crate blocks survive); config.yml auto-upgraded (pool-version 2). Verify: right-click crate = GUI with % + OPEN; break ore/crop/fish -> key messages at 1%/0.05%/0.01%; holos above all 3 crates; boss spawn shows % drop pool + HP; /hunt lands ~200 blocks from boss; broadcasts colored (no &a&l, no ?).
## 2026-09-08 HOTFIX 43 — gem drops fixed (exact per-action odds) + admin key cleanup + colored armor gems
- **User (stream):** a player found 4 gems in an hour, one worth 4M. Wants the drop rate "properly working": only tiers 1-3 drop, tier 1 = 0.1%, tier 2 = 0.05%, tier 3 = 0.01%, and every action is its own independent 1-in-N roll (no hidden counter/session guarantee, no halving per attempt). Also: can admins look at another player's inventory to clean up keys dropped during the exploit? Double-check the crate key rates too. And add different colored gems for helm/armor/legs/boots enchants if special enchants exist.
- **MAVOEnchants - mining drops (exact odds):** old logic was 1% base halving per level (1% + 0.5% + 0.25%... per ore) - that is why 4 gems/hour happened. Replaced with config `mine-gem-max-tier: 3` + `mine-gem-chances: 1:0.1 / 2:0.05 / 3:0.01` (% per ore). Per ore mined we roll each tier ONCE independently (survival only, one gem max per ore): tier 1 = 1 in 1,000, tier 2 = 1 in 2,000, tier 3 = 1 in 10,000 → overall ~0.16% per ore (about 1 gem per ~625 ores). No counters, no guaranteed drops, no halving. Migration key `drop-version: 2`: live configs with old `mine-gem-base-chance`/`mine-gem-levels` get the new section written and the old keys removed (log "Hotfix 43: gem drops -> 0.1% / 0.05% / 0.01%"). Shop still sells all tiers 1-10 (1M..512M), so the 4M gem is buyable - only MINING is capped to 1-3.
- **MAVOEnchants - 4 armor gems (colored, own slot + charge pool):** AQUA (Diving, helmet, LAPIS_LAZULI gem, cancels DROWNING damage + refills air, 1 charge per trigger), AEGIS (chestplate, DIAMOND gem, tier*4% capped 50% chance to halve incoming MELEE/projectile/magic damage, 1 charge per proc - environmental ticks like fire/suffocation never drain it), MIGHT (leggings, AMETHYST_SHARD gem, tier*2% capped 25% chance melee hits deal +50%, 1 charge per proc), FEATHER (Featherfall, boots, QUARTZ gem, cancels FALL damage, 1 charge per trigger). All use the same per-player charge/cooldown pools (`useCharge`, persisted data.yml) and spend only when the effect procs; while recharging the gem does nothing (same rule as tool gems). Apply = gem in main hand + the armor piece in offhand, right-click (slot-validated); up to 3 gems per item still applies.
- **Gemshop is now 2 pages:** page 1 = tool gems (VEIN/SMELT/XP/LIFESTEAL, emerald), page 2 = armor gems (AQUA/AEGIS/MIGHT/FEATHER) with a Next/Prev arrow; close balanced item keeps working; GUI clicks are gated by the per-player tracked inventory (hotfix 38 pattern, page switch close-order safe). `/maenchant list` splits TOOL vs ARMOR with slot info + prints the exact mining odds (1 in 1000 / 1 in 2000 / 1 in 10000). Note: 0.05% is 1 in 2,000 (1-in-5,000 would be 0.02%) - the config values 0.1/0.05/0.01 are the exact spec.
- **MAVOCrates - admin inspect + key cleanup:** `/crate inspect <player>` (mavocrate.admin, online only, tab-complete) opens a read-only inventory viewer: storage + armor + offhand copied, keys visually marked [KEY] + crate id on hover. Buttons: TAKE ALL (barrier), take COMMON / RARE / MYTHIC (per-tier, key materials), refresh (live re-read), close. Removal scans the player's 41 inventory slots + ender chest and deletes ONLY MAVOCrate key items (PDC `mavocrate:key`, tracked crate types); already-spent keys and enchanted/gemmed items are NEVER touched ("gift"). Summary line shows counts found (incl. ender chest).
- **Key drop rates DOUBLE-CHECKED (user ask):** `key-drops` in Crates config still `common: 1.0 / rare: 0.05 / mythic: 0.01` (% per action), `pool-version: 2`, and `tryKeyDrops` still rolls all three independently per farming/mining/fishing action (no code regression). No config or code change needed for keys.
- **Files:** Enchants.java + config.yml (mine-gem-max-tier/chances + armor gems + 2-page shop), Crates.java + plugin.yml (inspect cmd + permission text), MAVOCRAFT-COMMANDS (enchants + crates), COMPLETE-CHECKLIST D-15 rows, DISCORD-PACK v29, this log.
- **Deploy (after CI):** replace MAVOEnchants-1.0.0.jar + MAVOCrates-1.0.0.jar, FULL server restart (not /reload). Keep configs - Enchants config auto-upgrades (drop-version 2). Verify: mining ~1/1000 tier1 odds feel (no 4 gems/hour); shop page 2 shows 4 colored armor gems; apply AQUA to helmet then drown (no damage, charge used); AEGIS/MIGHT procs spend a charge; /crate inspect <player> shows keys + take buttons remove keys only; keys still drop 1%/0.05%/0.01%.
- **SHIPPED 2026-09-08:** commits `531e104` (code+docs+configs) -> `4412f27` (CI failure log - compile fix needed) -> `feccccc` (fix: actionItem lore List.of) -> `f4a236c` (plugin.yml description) -> `b948020`/`1af86e1`/`59beb65`/`8ecffa2` (CI hardening: upload-rebuilt-jars artifact + non-fatal push + retries; GitHub intermittently rejects the bot push with `remote: fatal error in commit_refs` - retried runs / probes confirmed it is server-side flakiness, jars push eventually lands; artifact + 4x20s retry are the safety net) -> `92532c6` + `0ff4ed0` (ci: rebuilt plugin jars, SUCCESS - verified inside jar: drop-version 2 / mine-gem-max-tier 3 / chances 0.1-0.05-0.01, armor gem strings, Crates "TAKE ALL KEYS" + "[KEY]") -> this zip refresh.
- **Zip:** MAVOcraft-backup.zip refreshed (505 files, jars/ + sources/ + docs + uploads + logo), SHA `00a56600b62e8d36e1099047b4ffc4ca7967dd2945faddfffd72dac4bcb77878` (also in MAVOcraft-backup.sha256). `MAVOEnchants-1.0.0.jar` (27,282 B) + `MAVOCrates-1.0.0.jar` (29,262 B) updated in root, jars/ and the zip.
- **MAVOGuide 2.8.4 (v20) - Guide + Tutorials + Updates (user mandate: every delivery):** the guide had NOT been touched since v19 (Sleeper, hotfix 21) so it was missing Timber, the /craft GUIDE, gem shop, crates 2.0 and minibosses entirely. Fixed now: version 19 -> 20 (auto-popup for every player), What's New v20 entry (fair gem odds + armor gems + crates 2.0 + Timber/craft), NEW feature pages `timber` (whole tree/grown only/10-log cap), `craftguide` (100-recipe /craft preview + recipe-book unlock), `enchantgems` (8 gems, 2-page shop, charges, exact mining odds), `crates` (GUI with %, key drop rates, holos), `miniboss` (/hunt, spawn announce drops % + HP, broadcast); NEW tutorial chapter CH16 "Gems, Crates & Hunting". Plugin/pom version 2.8.3 -> 2.8.4 (MAVOGuide-2.8.4.jar delivered alongside MAVOEnchants/MAVOCrates). Checklist D-1 updated for 2.8.4, COMMANDS guide section updated with the every-hotfix rule, Discord pack v29 + #guide thread updated.
- **SHIPPED (guide follow-up):** `eda7dc6` (Guide 2.8.4 v20 source + docs) -> `6d0ee0e` (ci: rebuilt plugin jars, SUCCESS, verified: plugin.yml 2.8.4 / config v20 / 32 feature pages incl. timber, craftguide, enchantgems, crates, miniboss / tutorial CH16 / whatsnew v20) -> this finalize (MAVOGuide-2.8.3.jar dropped from root + jars/, zip refresh).
- **Zip (final):** MAVOcraft-backup.zip refreshed with Guide 2.8.4, SHA `b9e4989e5a0f0f1670e17c5def706f0bbc1f9d77376beef6c92efa3aa910cab9` (also in MAVOcraft-backup.sha256). MAVOGuide-2.8.4.jar (28,416 B) in root, jars/ and the zip.

## 2026-09-09 HOTFIX 44 — miniboss expansion: 15 boss types at 2 fixed arenas (Guide 2.8.5 v21)
- **User:** gave the production PebbleHost boot log and asked to "check for errors then add 10 more bosses and have them appear on 2 locations now."
- **Boot-log check (no fatal errors):** Paper 26.2-119, Java 25, 58 plugins, all MAVO plugins enabled (MAVOMiniboss 1.0.0 "5 boss type(s)"), Done in 22.4s. Non-fatal only: Essentials "unsupported server version", Vault "2 builds behind", JOML Unsafe deprecation, BlueMap manual-save note. REAL FINDING: live NETHER OVERLORD announce showed 2000 HP / 150,000 coins / 6x Lucky Coin @50% / 3x Mythic Crate Key @50% while the repo config said 1400/100k/5x@65%/1x@40% - server config drift; production values preserved and merged into the repo instead of overwriting.
- **MAVOMiniboss - 2 spawn arenas (was random wild):** new config `spawn-locations` list (name/world/x/z/jitter, default NORTHERN ARENA -2700,560 + SOUTHERN ARENA 340,1470, jitter 120) - bosses spawn at a random arena, random offset inside the jitter (surface only, y>=50, 40 tries then arena-centre fallback), so callouts always match reality. Spawn broadcast + 5-min broadcast + `/miniboss status|locate` now NAME the arena; `pickSpot()` replaced `findSpot()` (random 1k-5k wild). Old spawn-min/spawn-max keys removed from resource (unused). Arena list is reloadable via `/miniboss reload` (OP). `getMapList` parse with per-entry try/catch.
- **MAVOMiniboss - 15 boss types:** 10 NEW defs added (spider_queen CAVE_SPIDER 900HP/55k, plains_titan RAVAGER 1600/90k, skeleton_king SKELETON 1300/75k, ocean_tide_king DROWNED 1500/85k, undead_warlord WITHER_SKELETON 1200/70k, dark_forest_stalker VINDICATOR 1350/80k, mountain_giant GIANT 1900/100k, end_crusader ENDERMAN 1700/95k, crimson_behemoth ZOGLIN 1600/90k, ancient_golem IRON_GOLEM 2400/130k) with rare/mythic key ladders; nether_overlord updated to the LIVE values (2000 HP / 150k / 6x@50% / mythic 3x@50%, head -> PLAYER_HEAD since PIGLIN_HEAD is not a Material).
- **Config migration for existing live configs:** `bosses` already exists on live with 5 ids, so copyDefaults can NOT add the 10 new defs inside it - new `mergeBossRoster()` on enable loads disk + bundled config and ADDS every missing boss id (live-tuned values untouched), logs "Hotfix 44: N new boss type(s) added", then reloadConfig(). `spawn-locations` is a new top-level key so copyDefaults(true)+saveConfig() writes it.
- **Jar/version:** MAVOMiniboss stays 1.0.0 (hotfix release); MAVOGuide 2.8.4 -> 2.8.5, config version 20 -> 21 (auto-popup), What's New v21 (15 bosses/2 arenas/+Overlord boost), miniboss feature page rewritten (15 types, arenas, coords), tutorial CH16 step 3 updated (2 arenas + 15 types).
- **Files:** MiniBoss.java + config.yml, src-guide config.yml + pom/plugin.yml (2.8.5), MAVOCRAFT-COMMANDS (miniboss section), COMPLETE-CHECKLIST D-1 (2.8.5 v21) + D-17 rows rewritten (arena/reload/15 types rows), DISCORD-PACK-2026-09-07 v30 + #miniboss thread, this log.
- **Verify:** boot log "MAVOMiniboss v1.0.0 enabled - 15 boss type(s), 2 arena(s)"; live config gets 10 new ids + spawn-locations after restart; spawn broadcast names the arena; bosses ONLY at the two arenas (no random 1k-5k spots); /hunt lands ~200 blocks from the boss; Overlord announces 2000 HP/150k/6x@50%/3x Mythic @50%.
- **SHIPPED 2026-09-09:** `7edc553` (code + configs + Guide v21 + docs) -> CI `34289380019` SUCCESS -> `30c30e3` (ci: rebuilt plugin jars; verified: MAVOMiniboss 1.0.0 contains 15 boss defs + spawn-locations + "Hotfix 44" strings, MAVOGuide 2.8.5 = config v21 + plugin.yml 2.8.5) -> this finalize (MAVOGuide-2.8.4.jar dropped from root + jars/, zip refresh).
- **Zip (final):** MAVOcraft-backup.zip refreshed, SHA `ba4d78778e58534f0715b3267d72ed89ccc9248e675588198e33e0c72c0118cc` (also in MAVOcraft-backup.sha256). MAVOGuide-2.8.5.jar (29,097 B) + MAVOMiniboss-1.0.0.jar (20,641 B) in root, jars/ and the zip. Deploy: replace both jars, FULL restart (not /reload) - config auto-adds the 10 boss ids + spawn-locations on first boot.

## 2026-09-09 v3.0.0 — CHEST HUNT plugin + all plugins 3.0.0 + live config repair
- **User:** gave a fresh production boot log ("check for errors"), asked for a new plugin: Chest Hunt (every Minecraft day at 12:00 announce a new chest within a 100 block radius; ender chest with useful items + really small chance of expensive items; chest disappears if nobody collects it), then "give all JAR files version 3.0.0", then a NEW standing rule: **any edit increases the version** so jar file names show what got updated.
- **Boot-log ERROR CHECK — two REAL bugs found (both from the Hotfix-37 recursive config merge, confirmed live):** (1) `MAVOEnchants ... mining drop off (tiers 1-3), charge table 0 levels` — the recursive merge built wrong path prefixes so `gem-charges`/`shop-prices`/`mine-gem-chances` were created EMPTY on live; `useCharge()` sees `c == null` -> unlimited -> all gems were free-unlimited; `mine-gem-chances` empty -> no ore drops. copyDefaults also wrote `drop-version: 2` BEFORE the Hotfix-43 migration, so the migration never rewrote the section. (2) `MAVOCrafting ... 50 beginner recipe(s)` — the Hotfix-40 100-recipe upgrade never reached live (merge only ADDS missing keys; the 50-recipe list already existed). Non-fatal, not chased: Essentials unsupported version, Vault 2 builds behind, JOML Unsafe, BlueMap manual-save. Cosmetics (do not chase): Tavern "price 100 ?" / PersonalVault "? packs" log glyphs (console-only unicode). Everything else clean; Hotfix 44 confirmed live (MAVOMiniboss "15 boss type(s), 2 arena(s)", MAVOGuide 2.8.5).
- **MAVOEnchants v3.0.0 fix:** `mergeMissing` rewritten flat (def.getKeys(true), leaves only) + boot REPAIR: any empty/missing `gem-charges`/`shop-prices`/`mine-gem-chances` section is replaced from the bundled config; drop-version migration now also runs when the section is empty; logs "v3.0: config repaired - gem charges, shop prices and mining gem odds restored." Expected live boot after deploy: "max tier 10, mining drop 1=0.1%/2=0.05%/3=0.01% (tiers 1-3), charge table 10 levels".
- **MAVOCrafting v3.0.0 fix:** same flat merge + `recipes-version: 3` in the resource; boot migration replaces `beginner-recipes` wholesale when recipes-version < 3 (logs "v3.0: beginner recipes upgraded to 100"). Expected live boot: "100 beginner recipe(s)".
- **NEW MAVOChestHunt 3.0.0** (src-chesthunt): every Minecraft day at noon (full-time tick 6000, once per day, window 400 ticks with retry) an ENDER_CHEST spawns within `radius: 100` blocks of world spawn (surface, y>=50, random angle/dist, 50 tries, no water/lava), with a floating "CHEST HUNT" TextDisplay holo. Right-click (HAND only) opens a shared 27-slot loot GUI: `loot-slots: 12` stacks rolled from a weighted config pool (20 types: iron/gold/diamonds/emerald/XP/food/pick/sword/shield/golden apple/arrows/bow/obsidian + rare DIAMOND_BLOCK w2, NETHERITE_INGOT/NETHERITE_SWORD/ELYTRA/ENCHANTED_GOLDEN_APPLE w1 — about 0.7% per slot, ~8% per chest). Shared loot: taking removes it for everyone (other open views refresh); drag cancelled; per-player GUI scope guard (Hotfix 38 pattern). `lifetime-minutes: 20` (= one MC day): if NOT opened within the lifetime the chest broadcasts "vanished - nobody collected it" and despawns; opened chests stay until emptied or replaced at the next noon. Chest block protected (BlockBreak cancel + message; EntityExplodeEvent removes it from the blast list). Persisted in `chest.yml` (world/x/y/z/spawn-time/opened/day) — respawns within lifetime on restart (loot regenerates). Commands: `/chesthunt` distance hint + sound, `status` (coords/items left/expiry), `reload`, `spawn` (OP `chesthunt.admin`; force spawn does NOT duplicate the noon one). Announcement/broadcasts use `»` and cc() (no raw & codes, no unsupported glyphs).
- **ALL plugins 3.0.0:** 32 maintained sources + the new Chest Hunt = 33 plugins bumped in pom `<version>` + `<finalName>MAVO*-3.0.0</finalName>` + plugin.yml (all verified). The 13 jars WITHOUT sources in this repo (Tavern, Vault, PersonalVault, Spawn, Homes, Quests, HUD, PortalRoom, Trades, Streaks, CommunityGoals, ChunkBorders, ChunkPrices) stay at their original versions — nothing changed so nothing to bump; if sources are supplied later they get bumped too (documented in MAVOCRAFT-COMMANDS).
- **Guide v22 (jar 3.0.0):** config version 21 -> 22 (auto-popup), What's New v22 (chest hunt + v3.0.0 + fixes), NEW feature page `chesthunt` (33 feature pages), tutorial CH16 step 4 (CHEST HUNT). Guide pom/plugin.yml 2.8.5 -> 3.0.0 in the batch bump.
- **STANDING (user): every edit bumps the version** — next edits -> 3.0.1, 3.0.2 ... Jar filename = the version to compare when copying; recorded in MAVOCRAFT-COMMANDS top note + deploy doc.
- **Files:** Enchants.java, Crafting.java + config.yml, NEW src-chesthunt (pom/plugin/config/ChestHunt.java), all 33 poms + plugin.ymls, guide config.yml, MAVOCRAFT-COMMANDS, COMPLETE-CHECKLIST (D-1 v22, D-14/D-15 boot rows, NEW D-21, PART D summary), DISCORD-PACK v31 + #chesthunt thread + current-work, MAVOCRAFT-DEPLOY (v3.0.0 section), this log.
- **Verify:** boot log shows 33 plugins at 3.0.0 incl. "MAVOChestHunt v3.0.0 enabled - chest every day at noon, radius 100 blocks, pool 20 item type(s)"; Enchants charge table 10 levels + mining drop on; Crafting 100 beginner recipes; `/chesthunt spawn` -> broadcast + chest + holo within 100 blocks; open GUI, take item (gone for everyone), leave it -> vanishes after 20 min.
- **SHIPPED 2026-09-09:** `7e1219a` (code + 3.0.0 batch + docs) -> CI `34294468255` FAILURE (Paper 26.2 API: InventoryClickEvent `getCurrent()`/`setCurrent()` removed -> fix `f5713e6` uses `getCurrentItem()` + `setItem(slot,null)`) -> CI `34294828677` SUCCESS -> `c911b19` (ci: rebuilt plugin jars; verified: all 33 jars plugin.yml 3.0.0, ChestHunt class + 20-type loot config, Guide config v22, Enchants "config repaired" + Crafting "upgraded to 100" strings) -> this finalize: stale/old-version jars deleted (root 83 -> 46 = 33x3.0.0 + 13 legacy w/o sources; jars/ 66 -> 34 = 33x3.0.0 + PortalRoom-1.2.0), MAVOcraft-backup.zip refreshed.
- **Zip (final):** MAVOcraft-backup.zip 31,854,627 B, SHA `87e061afe44521dd6c1455220815d7233e76a0418fe51a3bffef8552daefb3a9` (also in MAVOcraft-backup.sha256) - 33x MAVO*-3.0.0.jar in root + jars/ (66 entries) + legacy PortalRoom-1.2.0 (both) + 12 legacy root-only jars (ChunkBorders, ChunkPrices, CommunityGoals-1.2.1, Homes, Hud-2.2.0, PersonalVault, Quests, Spawn, Streaks, Tavern-1.4.0, Trades, Vault - no sources in repo, unchanged).
- **Deploy:** stop -> replace ALL old MAVO jars in plugins/ with the 33 v3.0.0 jars (list in MAVOCRAFT-DEPLOY.md) -> start (configs auto-repair: Enchants log "v3.0: config repaired...", Crafting "v3.0: beginner recipes upgraded to 100") -> verify boot: 33 MAVO plugins at 3.0.0, MAVOChestHunt "enabled - chest every day at noon, radius 100 blocks"; `/chesthunt spawn` -> broadcast + chest + holo; leave it -> vanishes after 20 min; Guide v22 popup.

## 2026-09-09 v3.0.0 PLUGIN PACK (user uploaded whole plugins folder as Parts 1-7)
- **User:** uploaded Plugin_Part_1..7 (folders + jars) to origin/main; asked: create a `3.0.0` folder in the arena branch and move contents inside; check folders & jars one by one to completely map everything (incl. the plugins we had no source for); check every jar against our versions and replace where ours is better; guarantee that wiping plugins/ jars and copying ours works 100%; prefix jars with 01_ 02_ 03_ (not just versions) so they stay in order; order = main jar, third party, MAVO; push jars one by one with a copy signal; make everything that was edited land at 3.0.0.
- **UPLOADS (origin/main):** Plugins_Part_1.tar.gz (third-party config folders: BlueMap/ClaimChunk/CoreProtect/EconomyShopGUI/Essentials/Geyser-Spigot/LuckPerms/TAB/PlaceholderAPI/Vault/spark), Part_2 (LuckPerms libs+db etc.), Part_3 (ALL 44 MAVO plugin folders - config.yml/data.yml/homes.yml/vaults.yml; MAVOTrades folder NOT uploaded), Part_4-6 (01-12_ third-party jars), Part_7 (13_BlueMap + 22_MAVO-Quests/23_ChunkPrices/29_Trades/30_Streaks/37_Spawn numbered + ALL other live MAVO jars incl. unnumbered).
- **FULL MAP (verified by opening every jar's plugin.yml + sha256):** 58 live jars = 13 third-party (01-12 + BlueMap 5.23, all match the 23:46 boot log exactly) + 45 MAVO (33 we have sources for; 13 with NO source: ChunkBorders 1.2.0, ChunkPrices 1.0.1, CommunityGoals 1.2.1, Homes 1.2.0, Hud 2.2.0, PersonalVault 1.0.0, PortalRoom 1.2.0, Quests 1.4.0, Spawn 1.0.0, Streaks 1.0.0, Tavern 1.4.0, Trades 1.0.0, Vault 1.7.1). The 5 numbered live jars are sha256-IDENTICAL to the plain MAVO jars we already keep (they are just renamed in the user's folder). No "better" third-party available - kept as-is.
- **LIVE CONFIG FINDINGS (from the uploaded folders):** MAVOEnchants `gem-charges: {}` EMPTY (gems unlimited - fixed v3.0.0 repair), MAVOCrafting 50 beginner recipes + no recipes-version (fixed v3.0.0), MAVOGuide config v19 while jar 2.8.5 = v21 content (new pages never showed - FIXED in Guide 3.0.0 with syncConfig(): bundled version newer -> regenerate config.yml at boot, data.yml kept; log "Guide config vN -> vM"), MAVOCrates pool-version 2 OK, MAVOMiniboss already 15 bosses + 2 arenas (hf44 live), Tavern/Vault/Homes/Hud/Quests/Spawn/Streaks folders present (Trades folder missing), EconomyShopGUI/Geyser/Essentials configs match boot log.
- **Guide.java v3.0.0 change:** syncConfig() on enable - reads disk + bundled config versions and regenerates config.yml if bundled is newer (copyDefaults NEVER replaces existing config - root cause of v19-forever). CI 34299237483 SUCCESS -> 4d6c285 -> jar refreshed into root + jars/ + pack (sha 771977168825a8cd..., Config strings verified inside class).
- **3.0.0/ DELIVERABLE (arena branch):** `3.0.0/plugins/` = 59 numbered jars (01-13 third-party incl. BlueMap 5.23, 14-59 MAVO alphabetical: 33 rebuilt 3.0.0 + 13 live-identical kept + NEW 18_MAVO-ChestHunt-3.0.0), `MANIFEST.md` (per-jar #/file/plugin/version/source/sha256 - name+version verified from each jar's plugin.yml), `SHA256SUMS`, `README.md` (copy protocol: stop -> delete ONLY jars, keep plugin folders+data -> copy 59 -> start -> boot lines to expect; main jar paper-26.2.jar stays in server root, NOT in plugins/), `live-configs/` (44 MAVO plugin folders' config.yml/data.yml etc + BlueMap/ClaimChunk/CoreProtect/TAB/PlaceholderAPI/Vault/spark configs = compact snapshot of the upload for mapping/compat checks).
- **Per-jar pushes:** each of the 59 jars was committed + pushed individually (3.0.0 pack: NN_*.jar), then a docs commit (manifest/sha/configs). Local = remote = (after finalize). Pack does NOT trigger CI (only sources/** watched).
- **Bump rule note:** the 13 no-source plugins are NOT 3.0.0 and will not be - no edit was made (byte-identical to the live jar = same behavior). Everything MAVOcraft-built this release IS 3.0.0.

## 2026-09-09 v3.0.0 ALL-JARS (user: "Change ALL jar file version to 3.0.0 - we need ALL the same so any edit is visible")
- **Decision:** every MAVO jar now 3.0.0 (filename + plugin.yml). The 13 no-source plugins (ChunkBorders, ChunkPrices, CommunityGoals, Homes, Hud, PersonalVault, PortalRoom, Quests, Spawn, Streaks, Tavern, Trades, Vault) were **metadata-version-patched**: plugin.yml version -> 3.0.0, re-zipped keeping every other entry byte-identical (verified per jar: code entries identical, only plugin.yml differs). Behaviour cannot change - the visible version is just consistent now (user's requirement).
- **Third-party 01-13 NOT renamed** (real upstream versions kept: LuckPerms 5.5.78, Vault 1.7.3-b131, PAPI 2.12.3, TAB 6.1.2, EssentialsX 2.22.0, Geyser 2.11.2, Floodgate 2.2.5, ClaimChunk 0.0.25-FIX3, EconomyShopGUI 7.2.1, CoreProtect 24.0, BlueMap 5.23): we never edit them so they cannot hide an edit; faking 3.0.0 would break /plugins truth + update checks. Explained in README/MANIFEST; if the user insists they can be renamed afterwards.
- **Deliverable state:** 3.0.0/plugins = 59 jars (46 MAVO all 3.0.0 incl. 13 patched + 1 new ChestHunt; 13 third-party real versions). Root + jars/ now contain MAVO*-3.0.0.jar for all 46 (old-version jars removed). MANIFEST.md source column marks rebuilt vs metadata-patched; SHA256SUMS 59 lines; README updated (all-MAVO-3.0.0 wording, copy protocol, version rule).
- **Per-jar push signals:** 13 commits+pushes `3.0.0 pack: NN_MAVO-Name-3.0.0.jar - COPY READY (version X -> 3.0.0)` (78c00eb..bccf538). Note: the first attempt accidentally bundled all 13 into one commit (10eddd7) - rewrote history with `git push --force-with-lease=arena/...:<old tip>` (remote-tracking ref was missing, plain --force-with-lease reported stale info; explicit lease + fetched ref fixed it), then split into 13 commits. No other branch affected; remote arena tip now the split chain.
- **Verify commands** used: unzip -p plugin.yml grep version for all 46 (all 3.0.0), zipfile entry-level sha comparison old vs patched jar (code identical), sha256sum -c MAVOcraft-backup.sha256 after zip refresh.
