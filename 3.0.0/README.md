# MAVOcraft 3.0.0 — complete plugin pack (verified)

This folder is a **fully mapped, cross-checked copy of your live plugins folder**,
updated to MAVOcraft 3.0.0. It contains **59 jars** in `plugins/`, numbered so they
always load in a safe order, plus `live-configs/` (a snapshot of the config files
running on the server today) and `MANIFEST.md` (every jar checked: file name ↔
`plugin.yml` name ↔ version ↔ sha256).

## Guarantee — "remove all jars, copy mine, it works"
- Every jar was opened and its internal `plugin.yml` **name + version + main class**
  were matched against the file name (see `MANIFEST.md`).
- **46 MAVO jars: 33 rebuilt by MAVOcraft CI at 3.0.0** (all hotfixes incl. Chest Hunt,
  gem fixes, 100 recipes, 15 minibosses, guide v22 + the guide config auto-fix) and
  **13 kept byte-identical to live** (no source in this repo — see below).
- **13 third-party jars kept byte-identical to your upload** (they already are the
  newest available and match your boot log: LuckPerms 5.5.78, Vault 1.7.3-b131,
  PAPI 2.12.3, TAB 6.1.2, EssentialsX 2.22.0, Geyser 2.11.2-SNAPSHOT, Floodgate 2.2.5,
  ClaimChunk 0.0.25-FIX3, EconomyShopGUI 7.2.1, CoreProtect 24.0, BlueMap 5.23).

## HOW TO APPLY (in this order)
1. **Stop the server** (full stop — `/reload` cannot swap jars).
2. In `plugins/` **delete ONLY the .jar files — never the plugin folders or data**
   (LuckPerms data, EconomyShopGUI shops, Essentials userdata, MAVO data.yml files,
   claim data, BlueMap configs … all stay). Your existing configs are kept on purpose:
   MAVOCrafting/MAVOEnchants/MAVOGuide auto-upgrade themselves at boot (logs below).
3. Copy **all 59 jars** from `3.0.0/plugins/` into `plugins/` — the numbers handle load
   order (01–13 third-party first, then MAVO alphabetical).
4. Start the server and check these boot lines:
   - `MAVOChestHunt v3.0.0 enabled - chest every day at noon, radius 100 blocks, pool 20 item type(s)`
   - `MAVOEnchants v3.0.0 enabled - 8 enchants ... mining drop 1=0.1%/2=0.05%/3=0.01% (tiers 1-3), charge table 10 levels`
   - `MAVOCrafting v3.0.0 enabled - 7 custom recipe(s), 100 beginner recipe(s)`
   - `MAVOGuide enabled - welcome aboard.` **plus** `Guide config v19 -> v22 (new pages/notes delivered...)`
   - `MAVOMiniboss v3.0.0 enabled - 15 boss type(s), 2 arena(s)`
5. Verify SHA: `cd 3.0.0 && sha256sum -c SHA256SUMS` (run where the jars are; or compare
   the `SHA256SUMS` file values after copy with `sha256sum plugins/*.jar`).

## What changes on your server with 3.0.0
- **NEW Chest Hunt** — daily noon loot chest within 100 blocks of spawn (`/chesthunt`).
- **Gem fix** — charge table was empty on the server (`gem-charges: {}`); gems were
  unlimited. 3.0.0 repairs the config at boot → charges work, mining gems drop again.
- **/craft fix** — your server had the 50-recipe list; 3.0.0 upgrades it to 100.
- **Guide fix** — your server's Guide config was stuck at v19 while the jar had v21+
  (that's why new pages never appeared). 3.0.0 auto-regenerates it at boot → v22.
- **15 minibosses at 2 arenas** (already live from the hf44 deploy — still included),
  all other hotfixes 35–44.

## Files in this folder
| Path | What |
|---|---|
| `plugins/` | the 59 numbered jars to copy |
| `MANIFEST.md` | full per-jar table: #, filename, plugin, version, source, sha256 |
| `SHA256SUMS` | official checksums (same order as MANIFEST) |
| `live-configs/` | snapshot of configs/data currently on the server (reference + mapping) |
| `README.md` | this file |

## About "main jar files"
The server's **main jar (paper-26.2.jar) is NOT in plugins/** — it stays in the server
root and is managed by PebbleHost (it passes the loader checksum). This pack only covers
`plugins/`: **01–13 = third-party**, **14–59 = MAVO** (alphabetical). If you ever want a
numbered copy of the main jar too, put it in the server root as `paper.jar`/`paper-26.2.jar`
as usual — never inside a plugin account/plugins folder.

