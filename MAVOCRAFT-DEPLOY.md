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
