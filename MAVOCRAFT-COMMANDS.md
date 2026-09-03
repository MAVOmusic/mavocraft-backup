# MAVOcraft Commands

Every MAVO jar and its commands. **Players** = anyone online · **OP** = server admins / LuckyPerms group with the listed permission.

---

## MAVOGuide 2.7.3
| Command | Who | What it does |
|---|---|---|
| `/updates` | Players | Open the guide main menu (aliases: `/guide`, `/mods`, `/features`) |
| `/tutorial` | Players | Open the tutorial chapter list — CH0 newbie basics → CH15 Mob Farm (aliases: `/howto`, `/help2`) |
| `/whatsnew` | Players | Open the What's New version list (newest first, paged) |
| `/updates reload` | OP (`mavoguide.admin`) | Reload config; players who haven't seen the new version get the popup on next join |

Notes: Guide auto-opens once per new version. "This Guide" book is pinned bottom-middle of the menu.

---

## MAVOEvents 1.2.0
| Command | Who | What it does |
|---|---|---|
| `/event` | Players | Show what's live right now (aliases: `/events`) |
| `/event list` | Players | List all 10 events |
| `/event start <name>` | OP (`mavoevents.admin`) | Force-start an event |
| `/event stop` | OP (`mavoevents.admin`) | Stop the current event |

10 events: `luckyhour, coinrain, mobhunt, fishingfrenzy, minersrush, harvestbonus, buildbonus, zombiesiege, giftdrop, farmfrenzy` (auto ~1 every 45–90 min while players are online).

---

## MAVOLuckyCoins 1.5.5
| Command | Who | What it does |
|---|---|---|
| `/wish` | Players | Wishing Well info (wishes happen at the well with `Q`) |
| `/ccollect` | Players | Claim your free Lucky Coin (every 10 MC days) |
| `/wish well` | OP (`mavolucky.admin`) | Set the Wishing Well to the block you look at |
| `/ccollect give [amount]` | OP (`mavolucky.admin`) | Give Lucky Coins to yourself |

Drop rate 0.1% (1 in 1,000 actions) with a 20s cadence between drops — mining/killing/fishing only, never player-placed blocks.

---

## MAVODeathChest 1.1.0
| Command | Who | What it does |
|---|---|---|
| `/grave` | Players | Open the **/grave GUI**: every grave with world + X/Y/Z coords + time left (aliases: `/deathchest`, `/graves`) |
| `/grave` (click grave) | Players | Confirm screen: travel for **1,000 coins OR 10 Lucky Coins** (3s countdown, moving/monsters cancels) |
| click grave chest | OP (`mavodc.admin`) | Open any death chest |

Graves hold your loot 30 min, then burst open.

---

## MAVOAchievements 1.7.2
| Command | Who | What it does |
|---|---|---|
| `/ach` | Players | Open the achievements menu (aliases: `/achievements`, `/achieve`) |
| `/ach reload` | OP (`mavoach.admin`) | Reload categories (52 total, 38 mob kill categories — every Mob Farm mob has its own) |

Every achievement starts at level 1, not 0.

---

## MAVOProfessions 3.14.1
| Command | Who | What it does |
|---|---|---|
| `/profession` / `/prof` | Players | Open the professions menu |
| `/profession check` | Players | Your XP/level per profession |
| `/profession top <prof>` | Players | Leaderboard for a profession |
| `/profession almost <prof>` | Players | Who's about to level up |
| `/profession addxp <prof> <n>` | OP (`mavoprof.admin`) | Add XP to a player |
| `/profession reload` | OP (`mavoprof.admin`) | Reload config |

Tools: Stone → Iron **L10** → Diamond **L25** → Netherite **L50**. Enchant upgrades run to L999. Everyone starts at level 1.

---

## MAVOWanderer 1.1.0
| Command | Who | What it does |
|---|---|---|
| `/wanderer` | Players | When the next trader visit happens (plus trader info) |
| `/wanderer spawn [player]` | OP (`mavowanderer.admin`) | Force a usable trader to spawn (default: near you; with player: near them) |

Auto-spawns a wandering trader near a random online survival player every **30–60 min**; despawns after 10 min with 2 trader llamas and a bell.

---

## MAVOCurator 1.0.5
| Command | Who | What it does |
|---|---|---|
| `/museum` | Players | Open your collection book (aliases: `/curator`, `/collection`) |
| `/museum extras` | Players | Per-player **buy-only** list of items you still miss (normal shop prices, no selling) |
| `/museum reload` | OP (`mavocurator.admin`) | Reload 103 sections / 1,413 items |
| `/museum shopsgen` | OP (`mavocurator.admin`) | Re-index buy prices from the normal EconomyShopGUI shops + `/sreload`. Stale `MAVOMuseum.yml` files are now also **auto-removed on server boot**. |

---

## MAVOWild 1.7.4
| Command | Who | What it does |
|---|---|---|
| `/wild portal` | OP (`mavowild.admin`) | Re-set the Wild Portal at the block you look at |
| `/wild homeportal` | OP (`mavowild.admin`) | Re-set the Home Portal |
| `/holoreset [radius]` | OP (`mavowild.admin`) | Wipe & respawn all MAVO floating holos in radius (default 60) — aliases: `/resetholo`, `/mavoholo` |

Wild range: **5,000–400,000** blocks (1,000s comma format on the portal sign), 5-min cooldown, 3s warmup.

---

## MAVOShopNPC 1.3.3
| Command | Who | What it does |
|---|---|---|
| `/shopnpc spawn <name> [cmd]` | OP (`mavoshopnpc.admin`) | Spawn an NPC at your feet |
| `/shopnpc remove <name>` | OP | Remove an NPC |
| `/shopnpc list` | OP | List NPCs |
| `/shopnpc setcmd <name> <cmd>` | OP | Set the command an NPC runs |
| `/shopnpc adopt <name> [cmd]` | OP | Take over a village villager as an NPC |
| `/shopnpc holo|holoremove <name>` | OP | Add/remove the floating text |
| `/shopnpc resholo` (or `/holoreset` from MAVOWild) | OP | Rebuild all floating texts (needed to apply the new smaller holo size) |

---

## Mob Farm 2.5.0
| Command | Who | What it does |
|---|---|---|
| `/mobfarm info` | Players | Entry cost, session length, XP scale, community coins, center, protect radius |
| `/mobfarm prices` | Players | Spawner prices (10k/20k/40k…), unlock cost |
| `/mobfarm hub` | Players | Teleport to the Mob Farm hub |
| `/mobfarm status` | Players | Your session status, time left, loot/spawner counts |
| `/mobfarm enter` | Players | 5,000 coins, 10s don't-move, then 30 min session |
| `/mobfarm leave` | Players | Leave early (session timer keeps running) |
| `/mobfarm buy` | Players | Buy extra spawners (10k/20k/40k…) |
| `/mobfarm pick` | Players | Choose your bay/loot pick |
| `/mobfarm tp` | OP (`mavomobfarm.admin`) | Teleport to the hub (admins) |
| `/mobfarm setcenter` | OP | Move the farm center to where you stand |
| `/mobfarm build` | OP | Build the farm complex |
| `/mobfarm rebuild` | OP | Clear + rebuild the farm |
| `/mobfarm clear` | OP | Clear the whole farm complex |
| `/mobfarm clearhere [r]` | OP | Clear old structures around you (default r=80) |
| `/mobfarm purge [r]` | OP | Remove spawned mobs only (default r=150) |
| `/mobfarm resholo` | OP | Rebuild hub + bay floating texts |
| `/mobfarm reload` | OP | Reload config (mobs=36) |

---

## Casino 1.2.3 (unchanged, for reference)
| Command | Who | What it does |
|---|---|---|
| `/casino` | Players | Open Louie's menu (also right-click the villager) |

10 games; 10 coin attempts + 10 Lucky Coin attempts per 10 MC days; bets 100–5,000 coins or 1–10 Lucky Coins.
