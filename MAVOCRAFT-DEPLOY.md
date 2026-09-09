# MAVOcraft — UPDATE GUIDE (2026-09-06: THE BIG BUILD v21 — Boss Raid, Crates, Double XP, Pets 2.0, Fishing Tourney, Mail, Seasonal, AH 1.1.0, Professions 3.15.5 bedtime 7:00)

Plain steps. Only replace/delete files named here. Never delete plugin folders.

## 0. READ THIS FIRST

- MAVOProfessions **3.15.5** changes one thing: sleeping in a bed now wakes you at
  **07:00 (tick 1000)** instead of 12:00 (tick 6000). The old value on your server
  gets migrated automatically (only if it is still the default 6000; a custom
  wake value is left alone).
- MAVODoubleXp hooks into Professions by reflection — install BOTH in the same stop.
- MAVOCrates must load BEFORE MAVOBossRaid if you want boss crate keys (softdepend
  handles the order; missing crate key = boss still pays coins/LC, no key).
- AuctionHouse 1.1.0 keeps the same data.yml — listings/cooldowns/inbox are
  compatible. Old listings just have no bid/buy-now fields until re-posted.

## 1. STOP the server (full stop, no /reload)

## 2. DELETE these files from plugins/ (jars only)

    MAVOProfessions-3.15.4.jar    <- replace with 3.15.5 (3.15.3/older too if still there)
    MAVOAuctionHouse-1.0.3.jar    <- replace with 1.1.0 (1.0.x/older too if still there)
    MAVOPets-1.0.0.jar            <- replace with 2.0.0
    (nothing else - all other jars stay)

## 3. UPLOAD these jars into plugins/  (9 files)

    MAVOProfessions-3.15.5.jar  (bedtime wake-up 12:00 -> 07:00; + DoubleXp hook: every
                                 profession XP grant is multiplied while a boost runs)
    MAVOAuctionHouse-1.1.0.jar  (QUICK-BUY + BID TIMERS: /ah add ... [buy-now]; listing
                                 shows Buy Now + current bid; click listing -> buy / Buy Now /
                                 bid +10%/+25% /custom /ah bid <id> [amount]; bids in the last
                                 60s auto-extend +5 min; auctions with bids settle to the top
                                 bidder at expiry (their coins were escrowed); use /ah buy <id>)
    MAVOBossRaid-1.0.0.jar      (NEW - weekly boss: /boss join opens 10 min before Sat 20:00;
                                 scaled HP (2,000 + 100/raider), coins + damage bonus +
                                 Lucky Coins + trophy + crate key; admin: /boss setarena|now|cancel)
    MAVOCrates-1.0.0.jar        (NEW - custom crates + keys: /crate set <name> on a block,
                                 /crate givekey <player> <common|rare|mythic>; right-click crate
                                 with matching key = weighted roll; blocks unbreakable by players)
    MAVODoubleXp-1.0.0.jar      (NEW - DOUBLE XP weekends: Fri 18:00 -> Sun 18:00 (Europe/London,
                                 config); bossbar countdown; /xpboost status; admin /xpboost now
                                 <minutes> [mult] | off | reload)
    MAVOPets-2.0.0.jar          (NEW abilities: /pets = shop (cat/dog/fox/parrot/axolotl/turtle),
                                 /pet menu = carry slot + xorb toggle + recall; pet levels +1 XP
                                 per active minute (max 100); pets invulnerable, follow you)
    MAVOFishComp-1.0.0.jar      (NEW - weekly fishing tournament: one random 2h window
                                 Sat/Sun; any catch = 1 pt, rares worth more; top 3 coins,
                                 participation 500 for 3+ catches; /fishcomp status|top)
    MAVOMail-1.0.0.jar          (NEW - /mail send <player> [coins] [message] (+ held item);
                                 /mail GUI to claim; 7-day expiry, max 100, offline safe)
    MAVOSeasonal-1.0.0.jar      (NEW - season calendar: Spooky 25 Oct-2 Nov, Festive 20 Dec-5 Jan,
                                 Anniversary 1-8 Sep; mobs drop candy; Anniversary = x2 XP;
                                 /season status; admin /season toggle <id> to test early)

## 4. KEEP all configs / data — BUT note

- plugins/MAVOProfessions/config.yml + data.yml: KEEP. 3.15.5 migrates
  `sleep.skip-to-tick` 6000 -> 1000 on boot when it is still the old default.
- plugins/MAVOAuctionHouse/config.yml + data.yml: KEEP (same storage).
- plugins/MAVOPets/data.yml: KEEP (2.0.0 reads the same save; old 1.0.0 "pets" key is
  ignored, purchases in the new format live under pets2 - no conflict).
- EconomyShopGUI: no change this time.
- New plugins create their own folders on first boot (no config to pre-place).

## 5. START the server

Expected boot lines (order depends on jar loading):
    MAVOProfessions v3.15.5 enabled: 10 professions [.., sleeper]
    3.15.5: bedtime wake-up 12:00 -> 7:00 (if the old default was migrated)
    MAVOAuctionHouse 1.1.0 enabled. shopSell=.. listings=..
    MAVOBossRaid v1.0.0 enabled - next raid Sat 20:00 arena=NOT SET (/boss setarena)
    MAVOCrates v1.0.0 enabled - 3 crate type(s), 0 block(s).
    MAVODoubleXp v1.0.0 enabled - weekly FRI 18:00 -> SUN 18:00 x2.0.
    MAVOPets v2.0.0 enabled - 6 pet types, N owned pet(s) total.
    MAVOFishComp v1.0.0 enabled - current window ...
    MAVOMail v1.0.0 enabled - max 100 per player, 7 day expiry.
    MAVOSeasonal v1.0.0 enabled - 3 season(s) defined, active: none|anniversary

## 6. ONE-TIME ADMIN SETUP (after boot)

1. /boss setarena          - stand in your boss arena, then /boss now to test
2. /crate set common|rare|mythic  - look at each crate block you build
   /crate givekey <player> <name> [n]  - hand out keys (boss/quests can too)
3. /xpboost status         - shows the weekly window (default already on)
4. /season status          - calendar; /season toggle anniversary to test
5. /fishcomp status        - next weekend window
6. /pets /pet menu         - player-facing; pet prices in config if you want them lower
7. /mail                   - tell players: /mail send <name> 1000 "nice build!"

## 7. MAVOCRAFT-CONTEXT + DISCORD

- MAVOCRAFT-CONTEXT-REVIVAL.md: HOTFIX 26 entry appended.
- DISCORD-PACK-2026-09-05.md: version-log v20/v21 blocks + FEATURES blocks for all
  seven new systems + CW3 done list + CW4 "next 10" proposals — copy from there.

---

## 2026-09-09 — v3.0.0 BATCH + CHEST HUNT (all maintained plugins -> 3.0.0)

**VERSION RULE (user, standing):** every edit bumps the plugin version (3.0.0 -> 3.0.1 -> ...).
Jar file names show the version - if the file you copy is not the newest version, it was not
updated. No edits = version stays.

**What changed:** NEW MAVOChestHunt-3.0.0 (daily noon chest near spawn, 100-block radius,
useful + rare loot, vanishes if uncollected); ALL 46 MAVO plugins ship at **3.0.0**
(33 rebuilt from source + 13 version-patched — code = the live jar, byte-identical);
MAVOEnchants + MAVOCrafting config REPAIRS (gem charge table / mining gem drops / 100
beginner recipes - old configs auto-upgrade at boot); MAVOGuide 3.0.0 (v22).

**All 46 MAVO jars are 3.0.0** (2.0.0+ not present anymore). The 13 no-source plugins
(Tavern, Vault, PersonalVault, Spawn, Homes, Quests, HUD, PortalRoom, Trades, Streaks,
CommunityGoals, ChunkBorders, ChunkPrices) are 3.0.0 too — version metadata patched,
code identical to the jar you were running. Third-party 01–13 keep real upstream versions.

### 1. STOP the server (full stop, no /reload)

### 2. In plugins/ DELETE the old MAVO jars you are replacing (every jar whose name is NOT 3.0.0
and exists in this batch list) - each plugin has exactly one jar in plugins/:
MAVOAchievements, AuctionHouse, BossRaid, Casino, ChestShops, Couples, Crafting, Crates,
Curator, DeathChest, DoubleXp, Duels, Enchants, Events, FishComp, Guide, Guilds, Locks,
LuckyCoins, Mail, Miniboss, MobFarm, Pets, Professions, Seasonal, ShopNPC, Spawners, Timber,
Tpa, Wanderer, Warps, Wild. DELETE MAVOChestHunt* if present (should not be).

### 3. UPLOAD all 33 jars below into plugins/ (versions now 3.0.0; MAVOGuide = 3.0.0 v22):
MAVOAchievements-3.0.0.jar  MAVOAuctionHouse-3.0.0.jar  MAVOBossRaid-3.0.0.jar
MAVOCasino-3.0.0.jar  MAVOChestShops-3.0.0.jar  MAVOChestHunt-3.0.0.jar  MAVOCouples-3.0.0.jar
MAVOCrafting-3.0.0.jar  MAVOCrates-3.0.0.jar  MAVOCurator-3.0.0.jar  MAVODeathChest-3.0.0.jar
MAVODoubleXp-3.0.0.jar  MAVODuels-3.0.0.jar  MAVOEnchants-3.0.0.jar  MAVOEvents-3.0.0.jar
MAVOFishComp-3.0.0.jar  MAVOGuide-3.0.0.jar  MAVOGuilds-3.0.0.jar  MAVOLocks-3.0.0.jar
MAVOLuckyCoins-3.0.0.jar  MAVOMail-3.0.0.jar  MAVOMiniboss-3.0.0.jar  MAVOMobFarm-3.0.0.jar
MAVOPets-3.0.0.jar  MAVOProfessions-3.0.0.jar  MAVOSeasonal-3.0.0.jar
MAVOShopNPC-3.0.0.jar  MAVOSpawners-3.0.0.jar  MAVOTimber-3.0.0.jar  MAVOTpa-3.0.0.jar
MAVOWanderer-3.0.0.jar  MAVOWarps-3.0.0.jar  MAVOWild-3.0.0.jar

(The plugins WITHOUT sources above - Tavern/Vault/etc - keep their existing jars untouched.)

### 4. START the server. Configs auto-upgrade (no config edits needed):
- MAVOEnchants: repairs gem-charges/shop-prices/mine-gem-chances (log: "v3.0: config repaired ...").
- MAVOCrafting: replaces the 50-recipe list with 100 (log: "v3.0: beginner recipes upgraded to 100").
- MAVOChestHunt: creates config.yml on first boot (loot pool, radius 100, lifetime 20 min).

### 5. VERIFY (boot log + in game):
- `MAVOEnchants v3.0.0 enabled - ... mining drop 1=0.1%/2=0.05%/3=0.01% (tiers 1-3), charge table 10 levels`
- `MAVOCrafting v3.0.0 enabled - 7 custom recipe(s), 100 beginner recipe(s)`
- `MAVOChestHunt v3.0.0 enabled - chest every day at noon, radius 100 blocks, pool 20 item type(s)`
- `/chesthunt spawn` (OP) -> broadcast + chest + holo within 100 blocks of spawn; right-click -> GUI; take item; leave it -> vanish after 20 min (or next noon).
- Guide v22 popup; /whatsnew v22 top.

---

## 2026-09-09 — v3.0.0 PLUGIN PACK (`3.0.0/plugins/`, 59 numbered jars)

**THE SAFE WAY (user-approved protocol):**
1. **STOP** the server (full stop).
2. In `plugins/` **delete ONLY the .jar files**. NEVER delete plugin folders / data
   (LuckPerms, Essentials userdata, EconomyShopGUI shops+transactions, ClaimChunk data,
   BlueMap configs, all MAVO data.yml / homes.yml / vaults.yml - they must stay).
3. Copy **all 59 jars** from `3.0.0/plugins/` (01-13 third-party, 14-59 MAVO) into plugins/.
4. **START** and check:
   - `MAVOChestHunt v3.0.0 enabled - chest every day at noon, radius 100 blocks, pool 20 item type(s)`
   - `MAVOEnchants v3.0.0 ... mining drop 1=0.1%/2=0.05%/3=0.01% (tiers 1-3), charge table 10 levels`
   - `MAVOCrafting v3.0.0 ... 100 beginner recipe(s)`
   - `MAVOGuide ...` + `Guide config v19 -> v22` (auto-fix, was stuck at v19)
   - `MAVOMiniboss v3.0.0 ... 15 boss type(s), 2 arena(s)`

**Never rename the 13 no-source jars (20/21/22/35/36/42/44/46/49/51/52/55/56) to 3.0.0** -
they are the exact live jars (sha256-identical), no edit was made. If you ever upload
their plugin folders/sources, they get rebuilt + bumped too.

**Full mapping:** `3.0.0/MANIFEST.md` (per jar: name/version/source/sha256), `3.0.0/SHA256SUMS`
(verify with `sha256sum -c`), `3.0.0/live-configs/` (server configs snapshot).

---

## 2026-09-09 — 3.0.1 round (3 files changed - the bump is the signal)
Replace in plugins/: `18_MAVO-ChestHunt-3.0.1.jar`, `24_MAVO-Crafting-3.0.1.jar`,
`33_MAVO-Guide-3.0.1.jar` (delete the 3.0.0 versions of these three). Restart.
Boot must now show: MAVOCrafting "100 beginner recipe(s)", MAVOChestHunt "pool 20 item
type(s)", MAVOGuide "Guide config v22 -> v23". All other files stay at 3.0.0.

---

## 2026-09-09 — 3.0.2 round (4 files changed - the bump is the signal)
Replace in plugins/: `18_MAVO-ChestHunt-3.0.2.jar`, `24_MAVO-Crafting-3.0.2.jar`,
`33_MAVO-Guide-3.0.2.jar`, `38_MAVO-LuckyCoins-3.0.2.jar` (delete the 3.0.1/3.0.0
versions of these four). Restart. **The plugins heal your existing configs themselves**
(ChestHunt renames EXP_BOTTLE->EXPERIENCE_BOTTLE, Crafting renames
EMPTY_MAP->MAP + TERRA_COTTA->TERRACOTTA) - do NOT delete the configs. Boot must show:
"repaired 1 legacy item name(s)" + ChestHunt "pool 20 item type(s)";
"repaired 2 legacy material name(s)" + Crafting "100 beginner recipe(s)";
LuckyCoins "1268 sellable items (1 skipped)";
Guide "config v23 -> v24". All other files stay at 3.0.0.
Full OP+player checklist: MAVOCRAFT-CHECKLIST-3.0.2.md.

---

## 2026-09-09 — 3.0.3 round (3 files changed - the bump is the signal)
Replace: `24_MAVO-Crafting-3.0.3.jar`, `33_MAVO-Guide-3.0.3.jar`,
`40_MAVO-Miniboss-3.0.3.jar` (delete the 3.0.2/3.0.0 versions of these three).
Restart. The plugins force-upgrade their own configs:
Crafting "v3.0.3: beginner recipes re-verified - 100 real vanilla basics (correct
amounts + 3x3 grid)"; Miniboss "3.0.3: boss table replaced - hard hunts (HP x2,
damage x1.5), coins/key drops scaled down to event-fair values)." Boot must show:
"MAVOCrafting v3.0.3 ... 100 beginner recipe(s)", "MAVOMiniboss v3.0.3 ... 15 boss
type(s), 2 arena(s)", "Guide config v24 -> v25". Next boss spawn shows e.g. "Drops:
100% 9,000 coins - 30% 1x Lucky Coin - 11% 1x Mythic Crate Key. HP 3200".
All other files stay 3.0.0/3.0.2. Pack + `MAVOcraft-backup.zip` already rebuilt: all 59 jars verified in `3.0.0/` (MANIFEST + SHA256SUMS), zip extracts 100% writable (0666). Full checklist: `MAVOCRAFT-CHECKLIST-3.0.3.md`.
