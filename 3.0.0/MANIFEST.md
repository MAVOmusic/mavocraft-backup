# MAVOcraft 3.0.0 PLUGIN PACK - manifest (verified)

Every jar below was opened and its `plugin.yml` name + version matched against the file name.
**46 MAVO plugins: 42 at 3.0.0, and 4 at 3.0.2 (MAVOChestHunt, MAVOCrafting, MAVOLuckyCoins,
MAVOGuide - the 3.0.2 config-heal round: the plugins rename legacy item/material names in
your OLD config files at boot, so the chest pool is 20/20, /craft is 100/100 and the
wishing-well pool uses 26.2 item names; Guide v24 documents it).**
33 were rebuilt from source by MAVOcraft CI (4 of them now 3.0.2); the 13 without source code in
this repo had their plugin.yml version patched to 3.0.0 - code inside is byte-identical to the
jar running on the server (sha-verified), so behaviour is unchanged.**
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
| 14 | 14_MAVO-Achievements-3.0.0.jar | MAVOAchievements | 3.0.0 | rebuilt from source | `b29a0d00815b337d…` |
| 15 | 15_MAVO-AuctionHouse-3.0.0.jar | MAVOAuctionHouse | 3.0.0 | rebuilt from source | `e3c539dcc6609365…` |
| 16 | 16_MAVO-BossRaid-3.0.0.jar | MAVOBossRaid | 3.0.0 | rebuilt from source | `f3d97bde626b89f8…` |
| 17 | 17_MAVO-Casino-3.0.0.jar | MAVOCasino | 3.0.0 | rebuilt from source | `9d0896d74088f705…` |
| 18 | 18_MAVO-ChestHunt-3.0.2.jar | MAVOChestHunt | 3.0.2 | rebuilt from source - 3.0.2 (heals old configs: EXP bottle + 2 recipe names + well pool 26.2 names) | `4098cac4c305cccd…` |
| 19 | 19_MAVO-ChestShops-3.0.0.jar | MAVOChestShops | 3.0.0 | rebuilt from source | `0fffafe6d34074d5…` |
| 20 | 20_MAVO-ChunkBorders-3.0.0.jar | MAVOChunkBorders | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `9c24bc3b2fdc82fa…` |
| 21 | 21_MAVO-ChunkPrices-3.0.0.jar | MAVOChunkPrices | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `6e1ae6d72b0f3770…` |
| 22 | 22_MAVO-CommunityGoals-3.0.0.jar | MAVOCommunityGoals | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `2949117d074d4f90…` |
| 23 | 23_MAVO-Couples-3.0.0.jar | MAVOCouples | 3.0.0 | rebuilt from source | `13dbdbe5ba408102…` |
| 24 | 24_MAVO-Crafting-3.0.2.jar | MAVOCrafting | 3.0.2 | rebuilt from source - 3.0.2 (heals old configs: EXP bottle + 2 recipe names + well pool 26.2 names) | `188cd1515869ce7f…` |
| 25 | 25_MAVO-Crates-3.0.0.jar | MAVOCrates | 3.0.0 | rebuilt from source | `5ed76203a5c8f05d…` |
| 26 | 26_MAVO-Curator-3.0.0.jar | MAVOCurator | 3.0.0 | rebuilt from source | `5630c1f2cc055560…` |
| 27 | 27_MAVO-DeathChest-3.0.0.jar | MAVODeathChest | 3.0.0 | rebuilt from source | `e600d19bbfaf557a…` |
| 28 | 28_MAVO-DoubleXp-3.0.0.jar | MAVODoubleXp | 3.0.0 | rebuilt from source | `d728af4898a48134…` |
| 29 | 29_MAVO-Duels-3.0.0.jar | MAVODuels | 3.0.0 | rebuilt from source | `933cee32d838add7…` |
| 30 | 30_MAVO-Enchants-3.0.0.jar | MAVOEnchants | 3.0.0 | rebuilt from source | `e32dbc9366362817…` |
| 31 | 31_MAVO-Events-3.0.0.jar | MAVOEvents | 3.0.0 | rebuilt from source | `e575fa3304d4d5f0…` |
| 32 | 32_MAVO-FishComp-3.0.0.jar | MAVOFishComp | 3.0.0 | rebuilt from source | `3f86b2381f76df52…` |
| 33 | 33_MAVO-Guide-3.0.2.jar | MAVOGuide | 3.0.2 | rebuilt from source - 3.0.2 (heals old configs: EXP bottle + 2 recipe names + well pool 26.2 names) | `e9533f4b201a27d7…` |
| 34 | 34_MAVO-Guilds-3.0.0.jar | MAVOGuilds | 3.0.0 | rebuilt from source | `7ae8a7cd44830aa8…` |
| 35 | 35_MAVO-Homes-3.0.0.jar | MAVOHomes | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `e50461bda67841e0…` |
| 36 | 36_MAVO-Hud-3.0.0.jar | MAVOHud | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `95627604ab20c97d…` |
| 37 | 37_MAVO-Locks-3.0.0.jar | MAVOLocks | 3.0.0 | rebuilt from source | `332e58ded46d392a…` |
| 38 | 38_MAVO-LuckyCoins-3.0.2.jar | MAVOLuckyCoins | 3.0.2 | rebuilt from source - 3.0.2 (heals old configs: EXP bottle + 2 recipe names + well pool 26.2 names) | `c25c6a8d1c546ad7…` |
| 39 | 39_MAVO-Mail-3.0.0.jar | MAVOMail | 3.0.0 | rebuilt from source | `6ada070fde28b9f3…` |
| 40 | 40_MAVO-Miniboss-3.0.0.jar | MAVOMiniboss | 3.0.0 | rebuilt from source | `1d581e55414aec81…` |
| 41 | 41_MAVO-MobFarm-3.0.0.jar | MAVOMobFarm | 3.0.0 | rebuilt from source | `d1c90fabebfa14b9…` |
| 42 | 42_MAVO-PersonalVault-3.0.0.jar | MAVOPersonalVault | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `262fc9e9e373f75e…` |
| 43 | 43_MAVO-Pets-3.0.0.jar | MAVOPets | 3.0.0 | rebuilt from source | `4d0aadfec94edfcd…` |
| 44 | 44_MAVO-PortalRoom-3.0.0.jar | MAVOPortalRoom | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `e0dfbba6c5cb1fe4…` |
| 45 | 45_MAVO-Professions-3.0.0.jar | MAVOProfessions | 3.0.0 | rebuilt from source | `749e0007a0d9a89a…` |
| 46 | 46_MAVO-Quests-3.0.0.jar | MAVOQuests | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `610f4df0f7e182fc…` |
| 47 | 47_MAVO-Seasonal-3.0.0.jar | MAVOSeasonal | 3.0.0 | rebuilt from source | `9699c0e7b7109780…` |
| 48 | 48_MAVO-ShopNPC-3.0.0.jar | MAVOShopNPC | 3.0.0 | rebuilt from source | `04a77588e66eb9fa…` |
| 49 | 49_MAVO-Spawn-3.0.0.jar | MAVOSpawn | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `505bec8d02b0b29f…` |
| 50 | 50_MAVO-Spawners-3.0.0.jar | MAVOSpawners | 3.0.0 | rebuilt from source | `0ae8773abecb2e03…` |
| 51 | 51_MAVO-Streaks-3.0.0.jar | MAVOStreaks | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `d6feb1ebdb366e83…` |
| 52 | 52_MAVO-Tavern-3.0.0.jar | MAVOTavern | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `0183a64854a48aea…` |
| 53 | 53_MAVO-Timber-3.0.0.jar | MAVOTimber | 3.0.0 | rebuilt from source | `ef073188ce756cf0…` |
| 54 | 54_MAVO-Tpa-3.0.0.jar | MAVOTpa | 3.0.0 | rebuilt from source | `54b012315fc0e68c…` |
| 55 | 55_MAVO-Trades-3.0.0.jar | MAVOTrades | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `db6825e024f55311…` |
| 56 | 56_MAVO-Vault-3.0.0.jar | MAVOVault | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `f0c27c00182802c8…` |
| 57 | 57_MAVO-Wanderer-3.0.0.jar | MAVOWanderer | 3.0.0 | rebuilt from source | `5387f2e08f9d3c06…` |
| 58 | 58_MAVO-Warps-3.0.0.jar | MAVOWarps | 3.0.0 | rebuilt from source | `e3aa5903079a4364…` |
| 59 | 59_MAVO-Wild-3.0.0.jar | MAVOWild | 3.0.0 | rebuilt from source | `ea5daad97672cdfd…` |

**Copy protocol (see README.md):** stop server → delete ONLY the .jar files in plugins/
(keep every plugin folder + data) → copy all 59 jars → start → check the boot lines in README.
Order 01-13 third-party first, 14-59 MAVO alphabetical. Verify with `sha256sum -c 3.0.0/SHA256SUMS`.
