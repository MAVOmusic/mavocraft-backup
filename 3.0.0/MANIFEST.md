# MAVOcraft 3.0.0 PLUGIN PACK - manifest (verified)

Every jar below was opened and its `plugin.yml` name + version matched against the file name.
**46 MAVO plugins: 30 at 3.0.0, 2 at 3.0.2 (MAVOChestHunt, MAVOLuckyCoins), 1 at 3.0.3
(MAVOCrafting) and 13 at 3.0.5 (see notes).**
3.0.2 = config-heal round (plugins rename legacy item/material names in OLD configs at boot).
3.0.3 = balance round (minibosses HP x2 + attack x1.5, drops scaled to event-fair;
/craft shows 100 VERIFIED vanilla recipes with exact 3x3 grids; Guide v25).
3.0.4 = fix round (the 3.0.3 boss table upgrade could silently skip old configs -
merge now runs first + table gen 3 re-forces it; Guide v26).
3.0.5 = exploit fix round (shop buyers receive items with exact-NBT stock match;
boss schedule 1/side 10:00 spawn / 7:00 vanish / kill cooldown; mail rejects at 100
unclaimed; locks block hoppers + pistons; couple/guild teleports get warmup +
monster check; AH explicit destroy messages + escrow fallback; duels/fishcomp/gems
hardening; Guide v27).
**33 were rebuilt from source by MAVOcraft CI (16 of them at 3.0.2/3.0.3/3.0.5); the 13 without source
code in this repo had their plugin.yml version patched to 3.0.0 - code inside is byte-identical
to the jar running on the server (sha-verified), so behaviour is unchanged.**
Third-party jars (01-13) keep their REAL upstream versions - they are never edited, so they can never
hide an edit; a fake 3.0.0 there would break plugin update checks.

| # | File (copy exactly) | Plugin name | Version | Source | SHA256 |
|---|---|---|---|---|---|
| 01 | 01_LuckPerms-5.5.78.jar | LuckPerms | 5.5.78 | third-party (upstream - never edited) | `2aa39368590acae2…` |
| 02 | 02_Vault-1.7.3-b131.jar | Vault | 1.7.3-b131 | third-party (upstream - never edited) | `a6b5ed97f43a5cf5…` |
| 03 | 03_PlaceholderAPI-2.12.3.jar | PlaceholderAPI | 2.12.3 | third-party (upstream - never edited) | `fde03259f5af6938…` |
| 04 | 04_TAB-6.1.2.jar | TAB | 6.1.2 | third-party (upstream - never edited) | `fd99633280d2367b…` |
| 05 | 05_EssentialsX-2.22.0.jar | Essentials | 2.22.0 | third-party (upstream - never edited) | `bda4685105977fca…` |
| 06 | 06_EssentialsX-Chat-2.22.0.jar | EssentialsChat | 2.22.0 | third-party (upstream - never edited) | `e5b0211f98af1eab…` |
| 07 | 07_EssentialsX-Spawn-2.22.0.jar | EssentialsSpawn | 2.22.0 | third-party (upstream - never edited) | `dd5377c4c921b9b6…` |
| 08 | 08_Geyser-2.11.2.jar | Geyser-Spigot | 2.11.2-SNAPSHOT | third-party (upstream - never edited) | `5a56d231221fbf7a…` |
| 09 | 09_Floodgate-2.2.5.jar | floodgate | 2.2.5-SNAPSHOT (b140-8780fa4) | third-party (upstream - never edited) | `9f436c42ffd8b109…` |
| 10 | 10_ClaimChunk-0.0.25-FIX3.jar | ClaimChunk | 0.0.25-FIX3 | third-party (upstream - never edited) | `8419796c8d0e8924…` |
| 11 | 11_EconomyShopGUI-7.2.1.jar | EconomyShopGUI | 7.2.1 | third-party (upstream - never edited) | `c9a06a910ea57c98…` |
| 12 | 12_CoreProtect-24.0.jar | CoreProtect | 24.0 | third-party (upstream - never edited) | `0000faa904e86b30…` |
| 13 | 13_BlueMap-5.23.jar | BlueMap | 5.23 | third-party (upstream - never edited) | `339554d75cedab35…` |
| 14 | 14_MAVO-Achievements-3.0.0.jar | MAVOAchievements | 3.0.0 | rebuilt from source | `b88eb9b14292b4b8…` |
| 15 | 15_MAVO-AuctionHouse-3.0.5.jar | MAVOAuctionHouse | 3.0.5 | 3.0.5 fix (explicit full-inbox destroy messages + escrow pending fallback) | `d886c37987aa8c12…` |
| 16 | 16_MAVO-BossRaid-3.0.0.jar | MAVOBossRaid | 3.0.0 | rebuilt from source | `bbe87f668c7dbc0c…` |
| 17 | 17_MAVO-Casino-3.0.0.jar | MAVOCasino | 3.0.0 | rebuilt from source | `0d3f059cd67b778a…` |
| 18 | 18_MAVO-ChestHunt-3.0.2.jar | MAVOChestHunt | 3.0.2 | 3.0.2 config-heal (EXP_BOTTLE rename) | `11767cd0dc4f7f0f…` |
| 19 | 19_MAVO-ChestShops-3.0.5.jar | MAVOChestShops | 3.0.5 | 3.0.5 fix (buyers RECEIVE items + exact-NBT stock match + hopper guard) | `16d03e3ea4bfcc9d…` |
| 20 | 20_MAVO-ChunkBorders-3.0.0.jar | MAVOChunkBorders | 3.0.0 | rebuilt from source | `9c24bc3b2fdc82fa…` |
| 21 | 21_MAVO-ChunkPrices-3.0.0.jar | MAVOChunkPrices | 3.0.0 | rebuilt from source | `6e1ae6d72b0f3770…` |
| 22 | 22_MAVO-CommunityGoals-3.0.0.jar | MAVOCommunityGoals | 3.0.0 | rebuilt from source | `2949117d074d4f90…` |
| 23 | 23_MAVO-Couples-3.0.5.jar | MAVOCouples | 3.0.5 | 3.0.5 fix (5s warmup + 12-block monster check on /couple home|tp) | `fd5a443560bfe39e…` |
| 24 | 24_MAVO-Crafting-3.0.3.jar | MAVOCrafting | 3.0.3 | 3.0.3 (100 verified recipes) | `d3ffd398df61d6bf…` |
| 25 | 25_MAVO-Crates-3.0.0.jar | MAVOCrates | 3.0.0 | rebuilt from source | `61433bf1fd9ee849…` |
| 26 | 26_MAVO-Curator-3.0.5.jar | MAVOCurator | 3.0.5 | 3.0.5 fix (abort-before-grant on failed gem withdraw) | `657ec66a0dd6d981…` |
| 27 | 27_MAVO-DeathChest-3.0.0.jar | MAVODeathChest | 3.0.0 | rebuilt from source | `0eba6a4392ded00f…` |
| 28 | 28_MAVO-DoubleXp-3.0.0.jar | MAVODoubleXp | 3.0.0 | rebuilt from source | `5ce5e96786e8dff5…` |
| 29 | 29_MAVO-Duels-3.0.5.jar | MAVODuels | 3.0.5 | 3.0.5 fix (refund first bet if second withdraw fails) | `1e185b0d30017f9b…` |
| 30 | 30_MAVO-Enchants-3.0.5.jar | MAVOEnchants | 3.0.5 | 3.0.5 fix (abort-before-grant on failed gem withdraw) | `673f5a1676fc9f01…` |
| 31 | 31_MAVO-Events-3.0.0.jar | MAVOEvents | 3.0.0 | rebuilt from source | `db601fb2df2c778a…` |
| 32 | 32_MAVO-FishComp-3.0.5.jar | MAVOFishComp | 3.0.5 | 3.0.5 fix (pending fallback so winnings never vanish) | `e4eda6e5bfac4412…` |
| 33 | 33_MAVO-Guide-3.0.5.jar | MAVOGuide | 3.0.5 | 3.0.5 (v27 note: exploit fix round + boss schedule) | `1f7e0690d7f3b672…` |
| 34 | 34_MAVO-Guilds-3.0.5.jar | MAVOGuilds | 3.0.5 | 3.0.5 fix (5s warmup + 12-block monster check on /guild home) | `7fe2157dc53ad50d…` |
| 35 | 35_MAVO-Homes-3.0.0.jar | MAVOHomes | 3.0.0 | rebuilt from source | `e50461bda67841e0…` |
| 36 | 36_MAVO-Hud-3.0.0.jar | MAVOHud | 3.0.0 | rebuilt from source | `95627604ab20c97d…` |
| 37 | 37_MAVO-Locks-3.0.5.jar | MAVOLocks | 3.0.5 | 3.0.5 fix (hoppers + pistons blocked on locked blocks) | `eeac232bd907876a…` |
| 38 | 38_MAVO-LuckyCoins-3.0.2.jar | MAVOLuckyCoins | 3.0.2 | 3.0.2 config-heal (well-pool names) | `872d3844652807ce…` |
| 39 | 39_MAVO-Mail-3.0.5.jar | MAVOMail | 3.0.5 | 3.0.5 fix (send REJECTED at 100 unclaimed - nothing lost) | `214df427462ced39…` |
| 40 | 40_MAVO-Miniboss-3.0.5.jar | MAVOMiniboss | 3.0.5 | 3.0.5 fix (1/side schedule: 10:00 spawn, 7:00 vanish, kill cooldown) | `6cdc7c6dda09830b…` |
| 41 | 41_MAVO-MobFarm-3.0.0.jar | MAVOMobFarm | 3.0.0 | rebuilt from source | `84301c6629da651d…` |
| 42 | 42_MAVO-PersonalVault-3.0.0.jar | MAVOPersonalVault | 3.0.0 | rebuilt from source | `262fc9e9e373f75e…` |
| 43 | 43_MAVO-Pets-3.0.0.jar | MAVOPets | 3.0.0 | rebuilt from source | `769eab8be8ccccc2…` |
| 44 | 44_MAVO-PortalRoom-3.0.0.jar | MAVOPortalRoom | 3.0.0 | rebuilt from source | `e0dfbba6c5cb1fe4…` |
| 45 | 45_MAVO-Professions-3.0.0.jar | MAVOProfessions | 3.0.0 | rebuilt from source | `d2eced1f553bc4d6…` |
| 46 | 46_MAVO-Quests-3.0.0.jar | MAVOQuests | 3.0.0 | rebuilt from source | `610f4df0f7e182fc…` |
| 47 | 47_MAVO-Seasonal-3.0.0.jar | MAVOSeasonal | 3.0.0 | rebuilt from source | `1b425685745f3fb8…` |
| 48 | 48_MAVO-ShopNPC-3.0.0.jar | MAVOShopNPC | 3.0.0 | rebuilt from source | `94173da3510abaeb…` |
| 49 | 49_MAVO-Spawn-3.0.0.jar | MAVOSpawn | 3.0.0 | rebuilt from source | `505bec8d02b0b29f…` |
| 50 | 50_MAVO-Spawners-3.0.0.jar | MAVOSpawners | 3.0.0 | rebuilt from source | `936107523e541b68…` |
| 51 | 51_MAVO-Streaks-3.0.0.jar | MAVOStreaks | 3.0.0 | rebuilt from source | `d6feb1ebdb366e83…` |
| 52 | 52_MAVO-Tavern-3.0.0.jar | MAVOTavern | 3.0.0 | rebuilt from source | `0183a64854a48aea…` |
| 53 | 53_MAVO-Timber-3.0.0.jar | MAVOTimber | 3.0.0 | rebuilt from source | `8fddf812e327a83e…` |
| 54 | 54_MAVO-Tpa-3.0.0.jar | MAVOTpa | 3.0.0 | rebuilt from source | `ffda30731026797d…` |
| 55 | 55_MAVO-Trades-3.0.0.jar | MAVOTrades | 3.0.0 | rebuilt from source | `db6825e024f55311…` |
| 56 | 56_MAVO-Vault-3.0.0.jar | MAVOVault | 3.0.0 | rebuilt from source | `f0c27c00182802c8…` |
| 57 | 57_MAVO-Wanderer-3.0.0.jar | MAVOWanderer | 3.0.0 | rebuilt from source | `ef0bc39c99518565…` |
| 58 | 58_MAVO-Warps-3.0.5.jar | MAVOWarps | 3.0.5 | 3.0.5 fix (abort-before-grant on failed gem withdraw) | `7662d00e9f96ac38…` |
| 59 | 59_MAVO-Wild-3.0.0.jar | MAVOWild | 3.0.0 | rebuilt from source | `96b259f80aada310…` |

**Copy protocol (see README.md):** stop server → delete ONLY the .jar files in plugins/
(keep every plugin folder + data) → copy all 59 jars → start → check the boot lines in README.
Order 01-13 third-party first, 14-59 MAVO alphabetical. Verify with `sha256sum -c 3.0.0/SHA256SUMS`.
