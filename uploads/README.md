# uploads/ — user screenshots & issue tracking

Drop user screenshots here (e.g. `01-whatsnew-overflow.png`). Each issue + image + status is tracked below so any chat can catch up.

| # | Screenshot (uploads/) | Issue | Status |
|---|---|---|---|
| 1 | `01-whatsnew-overflow.png` | What's New showed EVERY version in one menu → flowed off screen; only last 3 wanted | RESOLVED (guide 2.7.0: v14 list is paged — 3 newest on page 1, older versions on later pages, pager to v2) |
| 2 | `02-tutorial-ch2-wild.png` | CH2 said Wild Portal 2,000–400,000 (wrong) | RESOLVED (guide says 5,000–400,000; MAVOWild 1.7.4 defaults min-radius 5000 + holo reads config) |
| 3 | `03-tutorial-ch3-survival.png` | CH3 "Only SURVIVAL mode counts" | RESOLVED (removed from all guide text) |
| 4 | `04-tutorial-ch5-trader.png` | Wandering Trader never seen | RESOLVED (MAVOWanderer 1.1.0 auto-spawns every 30–60 min near a random online player, 10-min despawn, bell) |
| 5 | `05-tutorial-ch8-lucky.png` | ~20 Lucky Coins in 10 min — claimed 1% chance | RESOLVED (MAVOLuckyCoins 1.5.5: 0.1% + 20s cadence + migration; guide says 1 in 1,000, max 1 per 20s) |
| 6 | `06-tutorial-ch9-grave.png` | /grave spammed coords in chat | RESOLVED (MAVODeathChest 1.1.0 /grave GUI; v18: travel now 5,000 coins or 100 Lucky Coins) |
| 7 | `07-tutorial-ch10-casino.png` | Casino holo too big / title too long / said 5 games | RESOLVED (MAVOShopNPC 1.3.3 holo scale 0.9→0.55, width 140, short texts; guide CH10 lists all 10 games; v18 casino 1.2.4 bets 1–1,000,000 coins doubling / 1–50 Lucky +1) |
| 8 | `08-tutorial-ch11-museum.png` | Museum counts + unsellable items | RESOLVED (103 sections / 1,413 items; `/museum extras` per-player buy-only at normal shop prices; v18: click = buy instantly into museum + GUI stays open; dupes bounce the moment they are placed; Curator writes materials.txt so /destroy protects not-yet-donated museum items) |
| 9 | `09-tutorial-ch12-events.png` | Events chapter said 3, only 5 existed | RESOLVED (MAVOEvents 1.2.0 = 10 events; guide CH12 lists all 10; CH13 = Community Goals incl. Mob Farm chest) |
| 10 | `10-guide-null-name.png` | 3 guide items said "null" (mobfarm / playerlevel / tavernbar) | RESOLVED (all 26 features have names + Java null-guard; "This Guide" pinned bottom-middle slot 49) |
| 11 | `11-professions-tier-holo.png` | Profession tiers "10-25-50" vs "5-15-30" mismatch + holo too big | RESOLVED (config comment 5/15/30→10/25/50; guide consistent iron@10/diamond@25/netherite@50; holos shrunk via MAVOShopNPC 1.3.3) |
| 13 | — | Fix batch v18: Extras buy behavior, grave costs, Casino bets, MobFarm economy, PortalRoom danger prices, /destroy commands | IMPLEMENTED (curator 1.0.6 · guide 2.8.0 v18 · deathchest 1.1.1 · casino 1.2.4 · lucky 1.5.6 · mobfarm 2.6.0 · portalroom 1.2.0) — awaiting user deploy + confirm |
| 12 | `12-mobfarm-test.png` | User testing Mob Farm while fixes run | RESOLVED in v18 (mobfarm 2.6.0: 10k entry, 15 min, pick = shop price/16 doubling per pick, extend 25k/+15m, 2-page egg GUIs, prices GUI + hub holo). Generation fine-tune still OPEN. |

Deploy notes: `/shopnpc resholo` (or `/holoreset`) after installing MAVOShopNPC 1.3.3 to rebuild all floating texts with the new small size.
