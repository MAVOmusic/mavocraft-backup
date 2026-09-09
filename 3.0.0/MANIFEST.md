# MAVOcraft 3.0.0 PLUGIN PACK - manifest (verified)

Every jar below was opened and its `plugin.yml` name + version matched against the file name.
**46 MAVO plugins: 41 at 3.0.0, 2 at 3.0.2 (MAVOChestHunt, MAVOLuckyCoins) and 3 at
3.0.3 (MAVOCrafting, MAVOGuide, MAVOMiniboss).**
3.0.2 = config-heal round (plugins rename legacy item/material names in OLD configs at boot).
3.0.3 = balance round (minibosses HP x2 + attack x1.5, drops scaled to event-fair;
/craft shows 100 VERIFIED vanilla recipes with exact 3x3 grids; Guide v25).
**33 were rebuilt from source by MAVOcraft CI (5 of them at 3.0.2/3.0.3); the 13 without source
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
| 14 | 14_MAVO-Achievements-3.0.0.jar | MAVOAchievements | 3.0.0 | rebuilt from source | `c8b6ed35c310d422…` |
| 15 | 15_MAVO-AuctionHouse-3.0.0.jar | MAVOAuctionHouse | 3.0.0 | rebuilt from source | `bcb4b1559d295389…` |
| 16 | 16_MAVO-BossRaid-3.0.0.jar | MAVOBossRaid | 3.0.0 | rebuilt from source | `654228488bcda2b3…` |
| 17 | 17_MAVO-Casino-3.0.0.jar | MAVOCasino | 3.0.0 | rebuilt from source | `43657e552041c10c…` |
| 18 | 18_MAVO-ChestHunt-3.0.2.jar | MAVOChestHunt | 3.0.2 | 3.0.2 config-heal (EXP bottle + 2 recipe names + well pool 26.2 names) | `5be97efc91da40f1…` |
| 19 | 19_MAVO-ChestShops-3.0.0.jar | MAVOChestShops | 3.0.0 | rebuilt from source | `67ae83a7a8dcb5d9…` |
| 20 | 20_MAVO-ChunkBorders-3.0.0.jar | MAVOChunkBorders | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `9c24bc3b2fdc82fa…` |
| 21 | 21_MAVO-ChunkPrices-3.0.0.jar | MAVOChunkPrices | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `6e1ae6d72b0f3770…` |
| 22 | 22_MAVO-CommunityGoals-3.0.0.jar | MAVOCommunityGoals | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `2949117d074d4f90…` |
| 23 | 23_MAVO-Couples-3.0.0.jar | MAVOCouples | 3.0.0 | rebuilt from source | `6b3c67f57ee14ea0…` |
| 24 | 24_MAVO-Crafting-3.0.3.jar | MAVOCrafting | 3.0.3 | 3.0.3 rebalance (hard bosses + real /craft recipes) | `1527b36a3132f333…` |
| 25 | 25_MAVO-Crates-3.0.0.jar | MAVOCrates | 3.0.0 | rebuilt from source | `ad82436c8b517aca…` |
| 26 | 26_MAVO-Curator-3.0.0.jar | MAVOCurator | 3.0.0 | rebuilt from source | `26ab0a763be66607…` |
| 27 | 27_MAVO-DeathChest-3.0.0.jar | MAVODeathChest | 3.0.0 | rebuilt from source | `8d0c62fb19f8f71a…` |
| 28 | 28_MAVO-DoubleXp-3.0.0.jar | MAVODoubleXp | 3.0.0 | rebuilt from source | `1efac15249302d5c…` |
| 29 | 29_MAVO-Duels-3.0.0.jar | MAVODuels | 3.0.0 | rebuilt from source | `db1673e2570a1572…` |
| 30 | 30_MAVO-Enchants-3.0.0.jar | MAVOEnchants | 3.0.0 | rebuilt from source | `21570577d27062c7…` |
| 31 | 31_MAVO-Events-3.0.0.jar | MAVOEvents | 3.0.0 | rebuilt from source | `cde95b658133542a…` |
| 32 | 32_MAVO-FishComp-3.0.0.jar | MAVOFishComp | 3.0.0 | rebuilt from source | `1cea7261de825227…` |
| 33 | 33_MAVO-Guide-3.0.3.jar | MAVOGuide | 3.0.3 | 3.0.3 rebalance (hard bosses + real /craft recipes) | `e987dfa3e7c48f50…` |
| 34 | 34_MAVO-Guilds-3.0.0.jar | MAVOGuilds | 3.0.0 | rebuilt from source | `18f4a13e68416938…` |
| 35 | 35_MAVO-Homes-3.0.0.jar | MAVOHomes | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `e50461bda67841e0…` |
| 36 | 36_MAVO-Hud-3.0.0.jar | MAVOHud | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `95627604ab20c97d…` |
| 37 | 37_MAVO-Locks-3.0.0.jar | MAVOLocks | 3.0.0 | rebuilt from source | `ffc649806a1f45b3…` |
| 38 | 38_MAVO-LuckyCoins-3.0.2.jar | MAVOLuckyCoins | 3.0.2 | 3.0.2 config-heal (EXP bottle + 2 recipe names + well pool 26.2 names) | `9c8b88a49b118217…` |
| 39 | 39_MAVO-Mail-3.0.0.jar | MAVOMail | 3.0.0 | rebuilt from source | `7f91f07c6c13a23d…` |
| 40 | 40_MAVO-Miniboss-3.0.3.jar | MAVOMiniboss | 3.0.3 | 3.0.3 rebalance (hard bosses + real /craft recipes) | `f68c076cf043d0f2…` |
| 41 | 41_MAVO-MobFarm-3.0.0.jar | MAVOMobFarm | 3.0.0 | rebuilt from source | `f576bc9c95838f9c…` |
| 42 | 42_MAVO-PersonalVault-3.0.0.jar | MAVOPersonalVault | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `262fc9e9e373f75e…` |
| 43 | 43_MAVO-Pets-3.0.0.jar | MAVOPets | 3.0.0 | rebuilt from source | `ab527696619146e3…` |
| 44 | 44_MAVO-PortalRoom-3.0.0.jar | MAVOPortalRoom | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `e0dfbba6c5cb1fe4…` |
| 45 | 45_MAVO-Professions-3.0.0.jar | MAVOProfessions | 3.0.0 | rebuilt from source | `a4985e56b9a90ab5…` |
| 46 | 46_MAVO-Quests-3.0.0.jar | MAVOQuests | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `610f4df0f7e182fc…` |
| 47 | 47_MAVO-Seasonal-3.0.0.jar | MAVOSeasonal | 3.0.0 | rebuilt from source | `cbf8f94abd1e3e84…` |
| 48 | 48_MAVO-ShopNPC-3.0.0.jar | MAVOShopNPC | 3.0.0 | rebuilt from source | `f5f498bf02580133…` |
| 49 | 49_MAVO-Spawn-3.0.0.jar | MAVOSpawn | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `505bec8d02b0b29f…` |
| 50 | 50_MAVO-Spawners-3.0.0.jar | MAVOSpawners | 3.0.0 | rebuilt from source | `f9aa10ce8709e02b…` |
| 51 | 51_MAVO-Streaks-3.0.0.jar | MAVOStreaks | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `d6feb1ebdb366e83…` |
| 52 | 52_MAVO-Tavern-3.0.0.jar | MAVOTavern | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `0183a64854a48aea…` |
| 53 | 53_MAVO-Timber-3.0.0.jar | MAVOTimber | 3.0.0 | rebuilt from source | `075a2703d0293f18…` |
| 54 | 54_MAVO-Tpa-3.0.0.jar | MAVOTpa | 3.0.0 | rebuilt from source | `66f9b7d21f9b1fef…` |
| 55 | 55_MAVO-Trades-3.0.0.jar | MAVOTrades | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `db6825e024f55311…` |
| 56 | 56_MAVO-Vault-3.0.0.jar | MAVOVault | 3.0.0 | metadata-patched to 3.0.0 (code = live jar, no source) | `f0c27c00182802c8…` |
| 57 | 57_MAVO-Wanderer-3.0.0.jar | MAVOWanderer | 3.0.0 | rebuilt from source | `1f4ef9e85c8ebe73…` |
| 58 | 58_MAVO-Warps-3.0.0.jar | MAVOWarps | 3.0.0 | rebuilt from source | `2396b671792c3b4d…` |
| 59 | 59_MAVO-Wild-3.0.0.jar | MAVOWild | 3.0.0 | rebuilt from source | `b86f4b9508cc20fd…` |

**Copy protocol (see README.md):** stop server → delete ONLY the .jar files in plugins/
(keep every plugin folder + data) → copy all 59 jars → start → check the boot lines in README.
Order 01-13 third-party first, 14-59 MAVO alphabetical. Verify with `sha256sum -c 3.0.0/SHA256SUMS`.
