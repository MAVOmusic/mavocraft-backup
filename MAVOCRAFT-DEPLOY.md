# MAVOcraft — Jar Update Guide (REPLACE / DELETE / KEEP)

**One rule to remember:** after any jar update you normally only **REPLACE jar files**, **DELETE old-version jars** of the same plugin, and — for THIS update (2026-09-03, 12 jars) — **DELETE exactly 2 config files** (`MAVOGuide/config.yml` and `MAVOAchievements/config.yml`). **Everything else stays.** Never delete a whole plugin folder, and never delete the folders listed in §E.

---

## 1. Do it in this order

1. **FULL-STOP the server** (no `/reload` for jar swaps).
2. **DELETE the old-version jars** of each plugin from `plugins/` (§2) — otherwise two jars of the same plugin get loaded.
3. **DELETE exactly these 2 config files** (§3).
4. **UPLOAD the 12 new jars** (§4) into `plugins/`.
5. **FULL-START the server.**
6. Run the post-start commands (§6).

---

## 2. DELETE — old-version jars (only jar files, nothing else)

In `plugins/`, remove every jar except the new one. Exact superseded files:

| Plugin | DELETE from plugins/ | Keep (new) |
|---|---|---|
| MAVOGuide | `MAVOGuide-2.5.*.jar`, `MAVOGuide-2.6.0.jar`, `MAVOGuide-2.7.0.jar`, `MAVOGuide-2.7.1.jar`, `MAVOGuide-2.7.2.jar` | `MAVOGuide-2.7.3.jar` |
| MAVOEvents | `MAVOEvents-1.1.0.jar` | `MAVOEvents-1.2.0.jar` |
| MAVOLuckyCoins | `MAVOLuckyCoins-1.5.1.jar` … `1.5.4.jar` | `MAVOLuckyCoins-1.5.5.jar` |
| MAVODeathChest | `MAVODeathChest-1.0.0.jar` | `MAVODeathChest-1.1.0.jar` |
| MAVOAchievements | `MAVOAchievements-1.6.0.jar`, `1.7.0.jar`, `1.7.1.jar` | `MAVOAchievements-1.7.2.jar` |
| MAVOProfessions | `MAVOProfessions-3.14.0.jar` (and 3.13.x if present) | `MAVOProfessions-3.14.1.jar` |
| MAVOWanderer | `MAVOWanderer-1.0.0.jar` | `MAVOWanderer-1.1.0.jar` |
| MAVOCurator | `MAVOCurator-1.0.0.jar`, `MAVOCurator-1.0.1.jar`, `MAVOCurator-1.0.2.jar`, `MAVOCurator-1.0.3.jar`, `MAVOCurator-1.0.4.jar` | `MAVOCurator-1.0.5.jar` |
| MAVOWild | `MAVOWild-1.7.3.jar` | `MAVOWild-1.7.4.jar` |
| MAVOShopNPC | `MAVOShopNPC-1.3.2.jar` | `MAVOShopNPC-1.3.3.jar` |
| MAVOCasino | `MAVOCasino-1.2.0.jar`, `1.2.1.jar`, `1.2.2.jar` | `MAVOCasino-1.2.3.jar` |
| MAVOMobFarm | any `MAVOMobFarm-*.jar` older than 2.5.0 | `MAVOMobFarm-2.5.0.jar` |

Simple check: **`plugins/` must contain exactly ONE jar per plugin name** before starting.

---

## 3. DELETE — only these 2 config files (this update)

| File to delete | Why (verified in source) |
|---|---|
| `plugins/MAVOGuide/config.yml` | Guide uses `saveDefaultConfig()` — an existing config is NEVER overwritten. Old file = **v13/v14** (12 or 16 chapter builds, old text). New file inside the jar = **v15** (16 chapters CH0–CH15, paged What's New, 5,000–400,000 wild, 10/25/50 professions, v15 Museum shopsgen auto-reload). Delete only `config.yml`. |
| `plugins/MAVOAchievements/config.yml` | Achievements reads its categories **from this file** and uses `saveDefaultConfig()`. Old file = 43 categories; new = **52** (9 new Mob Farm kill categories: bee, fox, goat, llama, panda, frog, sniffer, squid, glow_squid + aliases). Keeping the old file means the new categories never appear. Delete only `config.yml`. |

**Do NOT delete alongside them:**
- `plugins/MAVOGuide/data.yml` — only stores "which version each player has seen". Keep it; players still get the v14 popup (they've seen v13). *(Optional: delete it if you want the popup to appear even for players who already saw v14.)*
- `plugins/MAVOAchievements/data.yml` — **player kill counts / levels / progress. KEEP.**

---

## 4. UPLOAD — the 12 new jars

`MAVOGuide-2.7.3.jar` · `MAVOEvents-1.2.0.jar` · `MAVOLuckyCoins-1.5.5.jar` · `MAVODeathChest-1.1.0.jar` · `MAVOAchievements-1.7.2.jar` · `MAVOProfessions-3.14.1.jar` · `MAVOWanderer-1.1.0.jar` · `MAVOCurator-1.0.5.jar` · `MAVOWild-1.7.4.jar` · `MAVOShopNPC-1.3.3.jar` · `MAVOCasino-1.2.3.jar` · `MAVOMobFarm-2.5.0.jar`

---

## 5. KEEP — never delete (verified per plugin)

| Plugin folder (`plugins/…`) | KEEP | Why |
|---|---|---|
| `MAVOGuide/` | `data.yml` | Seen-version per player (keep → popup still shows; see §3) |
| `MAVOLuckyCoins/` | `config.yml` **+** `data.yml` | Config auto-migrates drop-chance 0.01 → 0.001 on first start (safe); data = free-coin timers |
| `MAVOWild/` | `config.yml` **+** `data.yml`(if any) **+** `homes.yml` | Config holds the Portal + Home Portal block positions and recent-landing blacklist — deleting it would force you to re-place portals. min-radius auto-migrates 2,000 → 5,000 on start. |
| `MAVODeathChest/` | `config.yml` + `data.yml`(if any) | **Live graves are stored in config.yml (`chests` list)** — deleting loses active graves. New cost keys merge automatically (`copyDefaults`). |
| `MAVOAchievements/` | `data.yml` | Player progress (see §3 — config.yml is one of the 2 files to delete) |
| `MAVOProfessions/` | `config.yml` **+** `data.yml` | Config diff vs 3.14.0 is only a comment (5/15/30 → 10/25/50); data = all player XP |
| `MAVOWanderer/` | `config.yml` | Defaults merge automatically |
| `MAVOCurator/` | `config.yml` **+** `data.yml` | **`data.yml` = every player's museum donations/progress — CRITICAL, keep.** Categories are built in code (103 sections / 1,413 items) |
| `MAVOShopNPC/` | `config.yml` **+** NPC entities | **`config.yml` = every shopkeeper NPC (uuid, position, holo text) — deleting makes all NPCs vanish.** 1.3.3 auto-replaces old oversized holo texts on boot; no manual edit needed |
| `MAVOEvents/` | `config.yml` | All 10 events are defined in code; missing config keys merge automatically |
| `MAVOMobFarm/` | `config.yml` **+** `data.yml` | Config = farm center, mob list, prices; data = sessions/memberships |
| `MAVOCasino/` | `config.yml` **+** `data.yml` | data = attempt refill timers; `result-seconds` falls back to 5 if the key is missing (no delete needed) |

---

## 6. After the full start (console/OP)

```text
/updates reload        # Guide v14 — triggers the "what's new" popups
/museum shopsgen       # writes plugins/EconomyShopGUI/sections/MAVOMuseum.yml + shops/MAVOMuseum.yml
/shop                  # open the shop -> Museum Extras (slot 43)
/shopnpc resholo       # rebuilds floating texts at the new small size (auto-applied at boot too)
/mobfarm resholo       # rebuilds Mob Farm hub + bay holos
```

Check the console on boot for:
- `MAVOCurator enabled: 103 exhibit sections, 1413 collectable items.`
- `MAVOAchievements enabled: 52 categories…`
- `MAVOGuide enabled…` (reads v14 config — if deleted, first start writes the new `config.yml`)
- `MAVOLuckyCoins enabled…` (+ `Migrated …` style line from Wild for min-radius if applicable)

---

## 7. Folders you must NEVER delete (standing rule)

`plugins/EconomyShopGUI/` (and its `sections/`, `shops/` subfolders — the two `MAVOMuseum.yml` files are regenerated by `/museum shopsgen`, not hand-deleted) · `plugins/LuckPerms/` · `plugins/TAB/` · `plugins/MAVOWild/` (only its jar gets replaced) · every non-MAVO plugin folder (Vault, WorldGuard, etc.) · any MAVO plugin's `data.yml`/`homes.yml` not listed in §3.

**If in doubt: replace/delete only files, never folders — and only files named in §2 or §3.**

## HOTFIX 2026-09-03 — MAVOWanderer
- Same version 1.1.0 jar rebuilt (invalid plugin.yml description). REPLACE jar only, keep plugins/MAVOWanderer/config.yml.
