# MAVOcraft 3.0.0 — complete plugin pack (verified)

This folder is a **fully mapped, cross-checked copy of your live plugins folder**,
updated so **every MAVO jar is 3.0.0**. It contains **59 jars** in `plugins/`, numbered
so they always load in a safe order, plus `live-configs/` (the config/data snapshot
running on the server) and `MANIFEST.md` (every jar checked: file ↔ `plugin.yml`
name ↔ version ↔ sha256).

## Every jar at 3.0.0 — how
- **46 MAVO jars, ALL 3.0.0** (file name + internal `plugin.yml` — verified, see MANIFEST):
  - **33 rebuilt from source** by MAVOcraft CI (all hotfixes 35–44, Chest Hunt, gem fix,
    100 recipes, guide v22 + config auto-fix, 15 minibosses at 2 arenas …).
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
   - `MAVOChestHunt v3.0.0 enabled - chest every day at noon, radius 100 blocks, pool 20 item type(s)`
   - `MAVOEnchants v3.0.0 ... mining drop 1=0.1%/2=0.05%/3=0.01% (tiers 1-3), charge table 10 levels`
   - `MAVOCrafting v3.0.0 ... 100 beginner recipe(s)`
   - `MAVOGuide ...` + `Guide config v19 -> v22 (new pages/notes delivered...)`
   - `MAVOMiniboss v3.0.0 ... 15 boss type(s), 2 arena(s)`
   - `/plugins` shows every MAVO* plugin as v3.0.0.
5. Verify checksums: `sha256sum -c 3.0.0/SHA256SUMS` (run inside the folder holding the
   copied jars) — all 59 must say OK.

## Version rule (yours, now absolute)
Any future MAVO edit bumps the version: 3.0.0 → 3.0.1 → 3.0.2 … Every rebuilt jar is
replaced in this pack + pushed here, so when you copy, any file still reading `3.0.0`
with a different SHA, or any file not at 3.0.0, is instantly visible.

## Files in this folder
| Path | What |
|---|---|
| `plugins/` | the 59 numbered jars to copy (46 MAVO @3.0.0 + 13 third-party) |
| `MANIFEST.md` | per-jar table: #, filename, plugin, version, source, sha256 |
| `SHA256SUMS` | official checksums (same order as MANIFEST) |
| `live-configs/` | snapshot of configs/data currently on the server (reference + mapping) |
| `README.md` | this file |

## About "main jar files"
The server's **main jar (paper-26.2.jar) is NOT in plugins/** — it stays in the server
root and is managed by PebbleHost (checksum-verified by the loader). This pack covers
only `plugins/`: 01–13 third-party, 14–59 MAVO.
