# MAVOcraft 3.0.0 — complete plugin pack (verified)

This folder is a **fully mapped, cross-checked copy of your live plugins folder**.
It contains **59 jars** in `plugins/`, numbered so they always load in a safe order,
plus `live-configs/` (the config/data snapshot running on the server) and
`MANIFEST.md` (every jar checked: file ↔ `plugin.yml` name ↔ version ↔ sha256).

**Current state: 41 MAVO jars at 3.0.0, 2 MAVO jars at 3.0.2
(MAVOChestHunt, MAVOLuckyCoins — config-heal round), 1 MAVO jar at 3.0.3
(MAVOCrafting — balance round) and 2 MAVO jars at 3.0.4
(MAVOGuide, MAVOMiniboss — boss-table upgrade fix).
The version bump is the signal: any MAVO file that is NOT at the version below is
old and must be replaced.

## Versions — how
- **46 MAVO jars: 41 @ 3.0.0 + 2 @ 3.0.2 + 1 @ 3.0.3 + 2 @ 3.0.4** (file name + internal `plugin.yml` —
  verified, see MANIFEST):
  - **33 rebuilt from source** by MAVOcraft CI (all hotfixes 35–44, Chest Hunt, gem fix,
    100 recipes, guide v22 + config auto-fix, 15 minibosses at 2 arenas …). 5 of them
    are at **3.0.2/3.0.3/3.0.4** — they force-upgrade their own old configs at boot, so no
    config editing/delete is needed:
    - ChestHunt: renames `EXP_BOTTLE`→`EXPERIENCE_BOTTLE` in your `config.yml`
      -> loot pool really loads **20/20 item types** (was 19).
    - Crafting: renames `EMPTY_MAP`→`MAP` + `TERRA_COTTA`→`TERRACOTTA` in `config.yml`
      -> /craft really shows **100/100 beginner recipes** (was 98).
    - LuckyCoins: well-pool.txt updated for Paper 26.2 (4 old item names replaced)
      -> **1268 sellable items** (1 hard-banned by design).
    - Guide: v24 What's New entry describing the above (config v23 -> v24 auto-upgrade).
    - **3.0.3:** Crafting (100 VERIFIED vanilla recipes: correct amounts, output
      counts + exact 3x3 grids - white bed = wool+planks, no dye recipes),
      Miniboss (bosses-version 2: HP x2, attack x1.5, drops ~1/10 - event-fair),
      Guide (v25 What's New).
    - **3.0.4:** Miniboss (table upgrade fix: the 3.0.3 check ran AFTER
      copyDefaults seeded `bosses-version` into old configs, so the old
      90k/45% table survived while claiming v2 - merge now runs first + table
      gen 3 re-forces it), Guide (v26 What's New).
  - **13 had no source code in this repo** (never uploaded before part 1–7:
    ChunkBorders, ChunkPrices, CommunityGoals, Homes, Hud, PersonalVault, PortalRoom,
    Quests, Spawn, Streaks, Tavern, Trades, Vault). Their `plugin.yml` version was
    patched to **3.0.0**; the code inside is **byte-identical** to the jar running on
    your server (sha-verified, every entry compared) — behaviour cannot change, only
    the visible version did, so you can always see at a glance if anything is updated.
- **13 third-party jars keep their REAL upstream versions** (01–13: LuckPerms 5.5.78,
  Vault 1.7.3-b131, PAPI 2.12.3, TAB 6.1.2, EssentialsX 2.22.0, Geyser 2.11.2, Floodgate
  2.2.5, ClaimChunk 0.0.25-FIX3, EconomyShopGUI 7.2.1, CoreProtect 24.0, BlueMap 5.23).
  We never edit those — they cannot hide an edit. A fake "3.0.0" there would show wrong
  versions in `/plugins` and break their update checks, so they stay honest. If they
  ever change on your server, they are a third-party update, not a MAVO edit.

## HOW TO APPLY (in this order)
1. **Stop the server** (full stop — `/reload` cannot swap jars).
2. In `plugins/` **delete ONLY the .jar files — never the plugin folders or data**
   (LuckPerms data, EconomyShopGUI shops, Essentials userdata, every MAVO data.yml,
   claim data, BlueMap configs … all stay). Existing configs are kept on purpose:
   MAVOCrafting / MAVOEnchants / MAVOGuide auto-upgrade themselves at boot.
3. Copy **all 59 jars** from `3.0.0/plugins/` into `plugins/` (numbers handle order:
   01–13 third-party first, 14–59 MAVO alphabetical).
4. Start and check:
   - `MAVOChestHunt 3.0.2: repaired 1 legacy item name(s) ... EXP_BOTTLE -> EXPERIENCE_BOTTLE` + `MAVOChestHunt v3.0.2 enabled ... pool 20 item type(s)` (no "not a valid item" warn)
   - `MAVOEnchants v3.0.0 ... mining drop 1=0.1%/2=0.05%/3=0.01% (tiers 1-3), charge table 10 levels`
   - `MAVOCrafting 3.0.3: beginner recipes re-verified - 100 real vanilla basics (correct amounts + 3x3 grid).` + `MAVOCrafting v3.0.3 ... 7 custom recipe(s), 100 beginner recipe(s)`
   - `MAVOMiniboss 3.0.4: boss table replaced - hard hunts (HP x2, damage x1.5), coins/key drops scaled down to event-fair values.` + `MAVOMiniboss v3.0.4 ... 15 boss type(s), 2 arena(s)`
   - `MAVOLuckyCoins Wishing well pool loaded: 1268 sellable items (1 skipped)` + `MAVOLuckyCoins v3.0.2 enabled`
   - `MAVOGuide v3.0.4 ...` + `Guide config v24 -> v26 (new pages/notes delivered...)`
   - `MAVOMiniboss v3.0.0 ... 15 boss type(s), 2 arena(s)`
   - `/plugins` shows the 41 MAVO plugins as v3.0.0, 2 as v3.0.2, 1 as v3.0.3, 2 as v3.0.4.
5. Verify checksums: `sha256sum -c 3.0.0/SHA256SUMS` (run inside the folder holding the
   copied jars) — all 59 must say OK.

## Version rule (yours, now absolute)
Any future MAVO edit bumps the version: 3.0.0 → 3.0.1 → 3.0.2 … Every rebuilt jar is
replaced in this pack + pushed here, so when you copy, any file still reading an older
version, or any file not matching `SHA256SUMS`, is instantly visible.

**Zip note:** `MAVOcraft-backup.zip` extracts every file writable (`-rw-rw-rw-`), so
jars/configs can be uploaded or overwritten from the panel without any chmod.

## Files in this folder
| Path | What |
|---|---|
| `plugins/` | the 59 numbered jars to copy (41 MAVO @3.0.0 + 2 MAVO @3.0.2 + 1 MAVO @3.0.3 + 2 MAVO @3.0.4 + 13 third-party) |
| `MANIFEST.md` | per-jar table: #, filename, plugin, version, source, sha256 |
| `SHA256SUMS` | official checksums (same order as MANIFEST) |
| `live-configs/` | snapshot of configs/data currently on the server (reference + mapping) |
| `README.md` | this file |

## About "main jar files"
The server's **main jar (paper-26.2.jar) is NOT in plugins/** — it stays in the server
root and is managed by PebbleHost (checksum-verified by the loader). This pack covers
only `plugins/`: 01–13 third-party, 14–59 MAVO.
