# MAVOcraft — Jar Update Guide (REPLACE / DELETE / KEEP)

**One rule to remember:** after any jar update you normally only **REPLACE jar files**, **DELETE old-version jars** of the same plugin, and — for THIS update (2026-09-03, fix batch v18) — **DELETE exactly 1 config file** (`plugins/MAVOGuide/config.yml`). **Everything else stays.** Never delete a whole plugin folder, and never delete the folders listed in §7.

---

## 1. Do it in this order

1. **FULL-STOP the server** (no `/reload` for jar swaps).
2. **DELETE the old-version jars** of each plugin from `plugins/` (§2) — otherwise two jars of the same plugin get loaded.
3. **DELETE exactly this 1 config file** (§3).
4. **UPLOAD the 7 new jars** (§4) into `plugins/`.
5. **FULL-START the server.**
6. Run the post-start commands (§6).

---

## 2. DELETE — old-version jars (only jar files, nothing else)

In `plugins/`, remove every jar except the new one. Exact superseded files:

| Plugin | DELETE from plugins/ | Keep (new) |
|---|---|---|
| MAVOGuide | `MAVOGuide-2.5.*.jar`, `MAVOGuide-2.6.0.jar`, `MAVOGuide-2.7.*.jar` | `MAVOGuide-2.8.0.jar` |
| MAVOLuckyCoins | `MAVOLuckyCoins-1.5.*.jar` | `MAVOLuckyCoins-1.5.6.jar` |
| MAVODeathChest | `MAVODeathChest-1.0.*.jar`, `MAVODeathChest-1.1.0.jar` | `MAVODeathChest-1.1.1.jar` |
| MAVOCurator | `MAVOCurator-1.0.*.jar` (incl. 1.0.5) | `MAVOCurator-1.0.6.jar` |
| MAVOCasino | `MAVOCasino-1.2.*.jar` (incl. 1.2.3) | `MAVOCasino-1.2.4.jar` |
| MAVOMobFarm | `MAVOMobFarm-2.[0-4].*.jar`, `MAVOMobFarm-2.5.0.jar` | `MAVOMobFarm-2.6.0.jar` |
| MAVOPortalRoom | `MAVOPortalRoom-1.1.0.jar` | `MAVOPortalRoom-1.2.0.jar` |

Simple check: **`plugins/` must contain exactly ONE jar per plugin name** before starting.

---

## 3. DELETE — only this 1 config file (this update)

| File to delete | Why (verified in source) |
|---|---|
| `plugins/MAVOGuide/config.yml` | Guide uses `saveDefaultConfig()` — an existing config is NEVER overwritten. Old file = **v17** (old "1,000 coins or 10 Lucky Coins", old Casino/Portal/MobFarm text). New file inside the jar = **v18** (5,000/100 grave, 1–1M Casino bets, 25k–5M danger dives, 10k/15min MobFarm, Previous/Next pager). Delete only `config.yml`. |

**Do NOT delete alongside it:**
- `plugins/MAVOGuide/data.yml` — only stores "which version each player has seen". Keep it; players still get the v18 popup (they've seen v17). *(Optional: delete if you want the popup even for players who already saw v17.)*

---

## 4. UPLOAD — the 7 new jars

`MAVOGuide-2.8.0.jar` · `MAVOLuckyCoins-1.5.6.jar` · `MAVODeathChest-1.1.1.jar` · `MAVOCurator-1.0.6.jar` · `MAVOCasino-1.2.4.jar` · `MAVOMobFarm-2.6.0.jar` · `MAVOPortalRoom-1.2.0.jar`

---

## 5. KEEP — never delete (verified per plugin)

| Plugin folder (`plugins/…`) | KEEP | Why |
|---|---|---|
| `MAVOGuide/` | `data.yml` | Seen-version per player (keep → popup still shows; see §3) |
| `MAVOLuckyCoins/` | `config.yml` **+** `data.yml` | Config auto-migrates drop-chance 0.01 → 0.001 on first start (safe); data = free-coin timers |
| `MAVODeathChest/` | `config.yml` + `data.yml`(if any) | **Live graves are stored in config.yml (`chests` list)** — deleting loses active graves. New 5,000/100 costs merge automatically (`copyDefaults` + 1.1.1 migration when old 1,000/10 present). |
| `MAVOCurator/` | `config.yml` **+** `data.yml` | **`data.yml` = every player's museum donations/progress — CRITICAL, keep.** `materials.txt` is auto-generated on boot (used by `/destroy` protection). |
| `MAVOShopNPC/` | `config.yml` **+** NPC entities | config = every shopkeeper NPC — deleting makes NPCs vanish |
| `MAVOMobFarm/` | `config.yml` **+** `data.yml` | **1.1.0→2.6.0 auto-migrates on boot:** entry 5000→10,000, session 30→15 min, adds `shop-buy` (36 real spawner prices) + `extend-cost`/`extend-minutes`. data = sessions/memberships. |
| `MAVOCasino/` | `config.yml` **+** `data.yml` | data = attempt refill timers |
| `MAVOWild/` | `config.yml` **+** `data.yml`(if any) **+** `homes.yml` | Portal positions + blacklist |
| `MAVOPortalRoom/` | `config.yml` (none shipped — all prices baked into the jar) | no data files |

---

## 6. After the full start (console/OP)

```text
/updates reload        # triggers the v18 "what's new" popup
/museum shopsgen       # writes plugins/EconomyShopGUI/sections/MAVOMuseum.yml + shops/MAVOMuseum.yml
/shopnpc resholo       # rebuilds floating texts (unchanged this batch, safe to run)
/mobfarm resholo       # rebuilds Mob Farm hub + bay holos (hub holo now shows new prices)
```

Check the console on boot for:
- `MAVOGuide 2.8.0` / `MAVOCurator 1.0.6` / `MAVOMobFarm 2.6.0` / `MAVOPortalRoom 1.2.0` in the plugin list
- `MAVOCurator enabled: 103 exhibit sections, 1413 collectable items.`
- `Migrated MobFarm economy -> 10k entry / 15 min / shop-priced picks.` (first boot with old config)
- `Migrated grave teleport costs -> 5000 coins / 100 lucky coins.` (first boot with old DeathChest config)
- `MAVOLuckyCoins enabled… Museum protection loaded: 1413 collectables.` (after Curator writes materials.txt)

Gameplay checks:
- `/museum` tab completes with `extras` now.
- `/museum extras`: click an item → coins deducted, item added to museum, GUI stays open, item gone from list.
- Deposit crate: dropping a duplicate **bounces back the moment you place it** (chat: "Already in the museum (returned): …").
- `/grave` menu shows 5,000 coins / 100 Lucky Coins.
- Casino: bets start at **1 coin**, **+ doubles** to 1,000,000; Lucky 1–50 **+1/click**.
- `/mobfarm enter` 10,000 · 15 min; `/mobfarm pick` 2 pages (Hostile/Farm animals); `/mobfarm extend` 25,000/+15 min (HUD updates).
- Portal Room SOUTH wall: wormhole prices now 25,000 → 5,000,000 (Stronghold 500,000 · Trial 1,000,000 · Deep Dark 5,000,000).
- `/destroy`, `/destroy hand`, `/destroyall` (unsellable junk only; never lucky coins / profession tools / museum items you still need).
- `/mobfarm prices` opens the egg-icon GUI; hub hologram shows entry/pick/extend prices.

---

## 7. Folders you must NEVER delete (standing rule)

`plugins/EconomyShopGUI/` (and its `sections/`, `shops/` subfolders — the two `MAVOMuseum.yml` files are regenerated by `/museum shopsgen`, not hand-deleted) · `plugins/LuckPerms/` · `plugins/TAB/` · `plugins/MAVOWild/` (only its jar gets replaced) · every non-MAVO plugin folder (Vault, WorldGuard, etc.) · any MAVO plugin's `data.yml`/`homes.yml` not listed in §3.

**If in doubt: replace/delete only files, never folders — and only files named in §2 or §3.**

## HISTORY
- **2026-09-03 v18 fix batch (this doc):** curator 1.0.6, guide 2.8.0 v18, deathchest 1.1.1, casino 1.2.4, lucky 1.5.6, mobfarm 2.6.0, portalroom 1.2.0.
- **2026-09-03 HOTFIX 5:** curator 1.0.5 + guide 2.7.3 v17 (stale museum shop auto-removed on boot).
- **2026-09-03 HOTFIX 4:** curator 1.0.4 + guide 2.7.2 v16 (per-player museum extras).
- **2026-09-03 HOTFIX 3:** curator 1.0.3 (museum prices = normal shop prices).
- **2026-09-03 HOTFIX 2:** curator 1.0.2 + guide 2.7.1 v15.
- **2026-09-03 MAVOWanderer 1.1.0 rebuilt** (invalid plugin.yml description). REPLACE jar only, keep `plugins/MAVOWanderer/config.yml`.
