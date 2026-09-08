# MAVOcraft Commands

Every MAVO jar and its commands. **Players** = anyone online · **OP** = server admins / LuckyPerms group with the listed permission.

---

## MAVOGuide 2.8.4
| Command | Who | What it does |
|---|---|---|
| `/updates` | Players | Open the guide main menu (aliases: `/guide`, `/mods`, `/features`) |
| `/tutorial` | Players | Open the tutorial chapter list — CH0 newbie basics → CH16 Gems, Crates & Hunting (aliases: `/howto`, `/help2`) |
| `/whatsnew` | Players | Open the What's New version list (newest first, paged) — currently v20 (Hotfix 43) |
| `/updates reload` | OP (`mavoguide.admin`) | Reload config; players who haven't seen the new version get the popup on next join |

Notes: Guide auto-opens once per new version (auto-popup + chat banner). "This Guide" book is pinned bottom-middle of the menu. **The Guide is updated with EVERY hotfix** (What's New entry + feature pages + tutorial chapters, rebuilt jar delivered alongside the feature jar) — e.g. v20 added the Timber / Crafting Guide / Enchant Gems / Crates & Keys / Mini Bosses pages + CH16.

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

## MAVOLuckyCoins 1.5.6
| Command | Who | What it does |
|---|---|---|
| `/wish` | Players | Wishing Well info (wishes happen at the well with `Q`) |
| `/ccollect` | Players | Claim your free Lucky Coin (every 10 MC days) |
| `/wish well` | OP (`mavolucky.admin`) | Set the Wishing Well to the block you look at |
| `/ccollect give [amount]` | OP (`mavolucky.admin`) | Give Lucky Coins to yourself |
| `/destroy` / `/destroy hand` | Players | Destroy **unsellable** junk (hand = main hand only). NEVER touches shop-sellable items, lucky coins, profession tools, renamed/enchanted items, or museum items you have not donated yet. |
| `/destroyall` | Players | Clear the main inventory (slots 9–35). Hotbar + offhand shield + lucky coins + profession tools are kept. |

Drop rate 0.1% (1 in 1,000 actions) with a 20s cadence between drops — mining/killing/fishing only, never player-placed blocks.

---

## MAVODeathChest 1.1.1
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

## MAVOCurator 1.0.6
| Command | Who | What it does |
|---|---|---|
| `/museum` | Players | Open your collection book (aliases: `/curator`, `/collection`) |
| `/museum extras` | Players | Per-player **buy-only** list of items you still miss (normal shop prices). **Clicking buys instantly and adds it to the museum — GUI stays open.** |
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

## Mob Farm 2.6.1
| Command | Who | What it does |
|---|---|---|
| `/mobfarm info` | Players | Entry cost, session, pick prices, extend cost, XP scale, community, center |
| `/mobfarm prices` | Players | Price GUI: egg icons per mob (REAL shop price / 16, doubles per pick) + stack extras |
| `/mobfarm hub` | Players | Teleport to the Mob Farm hub |
| `/mobfarm extend` | Players | 25,000 coins → +15 min on the same session timer (HUD countdown updates) |
| `/mobfarm status` | Players | Your session status, time left, loot/spawner counts |
| `/mobfarm enter` | Players | 10,000 coins, 10s don't-move, then **15 min** session |
| `/mobfarm leave` | Players | Leave early (session timer keeps running) |
| `/mobfarm buy` | Players | Extra spawners on the SAME block (mob shop price /8, /4, /2, ×1, then ×2…) |
| `/mobfarm pick` | Players | 2-page pick GUI (hostile \| farm animals, alphabetic, egg icons) — pick #1 = shop price/16, each extra pick doubles |
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

## Casino 1.2.4
| Command | Who | What it does |
|---|---|---|
| `/casino` | Players | Open Louie's menu (also right-click the villager) |

10 games; 10 coin attempts + 10 Lucky Coin attempts per 10 MC days; bets 100–5,000 coins or 1–10 Lucky Coins.

---

## MAVOTimber 1.0.0
| Command | Who | What it does |
|---|---|---|
| `/timber status` | Players | Tree felling ON/OFF + max logs/tree + XP cap |
| `/timber toggle` | Players | Turn felling off/on (persists per player) |

Felling: axe + break bottom log → up to **10 logs** per tree (anti-exploit; shorter trees fall fully), 1 axe durability per log, **max 10 Lumberjack XP per tree**, crown leaves decay ~0.5s later (saplings/sticks).

**Grown trees only** (Hotfix 36): felling only triggers when the log cluster is a natural tree — it must have a **leaf crown** and contain **no player-placed blocks**. Shipwrecks, village houses and player-built log houses just break like vanilla (message: "That's not a grown tree"). Plant saplings to grow real trees. **Tall trees (dark forest / giant spruce) are recognised as natural and fell their capped 10 logs** (Hotfix 37 — the crown is always checked, even past the 10-log fell cap).

---

## MAVOCrafting 1.0.0
| Command | Who | What it does |
|---|---|---|
| `/craft` | **Everyone** | Open the **beginner recipe GUIDE** (100 basics, 45 per page). Click a recipe = **display-only 3×3 preview** of the real recipe + unlocks it in your vanilla recipe book (press E) — nothing is auto-crafted or consumed. Replaces the op-only Essentials workbench for this label. |
| `/craft <recipe>` | Everyone | Jump to that recipe's page (e.g. `/craft stone_pickaxe`) |
| `/crafting list` | Everyone | Lists the 7 custom recipes (name tag, saddle, lead, chainmail set) |
| `/crafting reload` | OP (`mavocrafting.admin`) | Re-register custom + beginner recipes |

Custom recipes craft in a normal crafting table. `/workbench` + `/e craft` still exist for ops.

---

## MAVOEnchants 1.0.0
| Command | Who | What it does |
|---|---|---|
| `/gemshop` | Players | Buy enchant gems with coins — **page 1 = tool gems, page 2 = armor gems** (Next/Prev arrows). Tiers I–X = 1M / 2M / 4M / 8M / 16M / 32M / 64M / 128M / 256M / 512M |
| `/maenchant list` | Players | List all 8 gem enchants (4 tool + 4 armor/slot) with exact mining drop odds + charge table |
| `/maenchant shop` | Players | Opens the same gem shop |
| `/maenchant charges` | Players | Your uses left + cooldown per gem type |
| `/maenchant gem <p> <type> [tier 1-10]` | OP (`mavoenchants.admin`) | Give a gem |

**Touch gems** (emerald): VEIN Vein Miner · SMELT Auto Smelt · XP XP Boost · LIFESTEAL Lifesteal.
**Armor gems** (colored, own slot — Hotfix 43): **AQUA** Diving (helmet, lapis — no drowning damage) · **AEGIS** (chestplate, diamond — chance to halve damage; tier×4%, cap 50%) · **MIGHT** (leggings, amethyst — chance melee deals +50%; tier×2%, cap 25%) · **FEATHER** Featherfall (boots, quartz — no fall damage). Armor effects only trigger while recharging-free (1 charge per trigger/proc); fire/suffocation ticks never drain AEGIS.

**Charges & cooldown per level** (config `gem-charges`): L1 10 uses/10 min · L2 5/9 · L3 10/8 · L4 15/7 · L5 20/6 · L6 25/5 · L7 30/4 · L8 35/3 · L9 50/2 · **L10 unlimited**. The cooldown starts with the FIRST use and the pool refreshes when it ends. State persists per player (`data.yml`).

Gems also drop from mining ores — **exact per-action rolls** (Hotfix 43, tiers 1–3 only, no hidden counters): tier I **0.1% (1 in 1,000)**, tier II **0.05% (1 in 2,000)**, tier III **0.01% (1 in 10,000)** per ore mined. Shop still sells all tiers I–X. Apply: gem in main hand + the item (tool or armor piece) in offhand, right-click (max 3 gems/item, slot-validated).

---

## MAVOMiniboss 1.0.0
| Command | Who | What it does |
|---|---|---|
| `/hunt` | Players | Teleport **outside spawn**; ~200 blocks from a live boss if one is up (30s cooldown) |
| `/miniboss status` | Players | Bosses alive, types, interval, /hunt + broadcast state |
| `/miniboss locate` | Players | Exact coords of live bosses |
| `/miniboss broadcast` | Players | Force a boss location broadcast now (**spawns a boss first if none is alive**) |
| `/miniboss reload` | OP (`mavominiboss.admin`) | Reload boss config |

Boss locations are also **broadcast every 5 min** as "around x, z (±100 blocks)".
On spawn, chat announces the **drop pool in %** (100% coins · lucky-chance % Lucky Coins ·
crate-chance % Crate Key) plus the boss **HP**. Those chances are rolled independently on kill
(config: `lucky-chance`, `crate-chance` per boss).

## MAVOCrates 1.0.0
| Command | Who | What it does |
|---|---|---|
| `/crate list` | Players | Crate types + key drop chances (farming/mining/fishing) |
| `/crate info <name>` | Players | Crate key, cooldown, every drop with its exact % |
| `/crate` (right-click a crate block) | Players | **GUI**: every drop + % chance, then an OPEN button (uses 1 key) |
| `/crate set <name>` | OP (`mavocrate.admin`) | Turn the block you look at into that crate (adds a holo) |
| `/crate unset` | OP | Remove the crate block you look at (holo cleaned) |
| `/crate clear <name>` | OP | Remove all blocks of that crate type |
| `/crate givekey <p> <name> [n]` | OP | Give crate keys |
| `/crate inspect <player>` | OP (`mavocrate.admin`) | **Read-only inventory view of an online player** with every MAVOCrate key marked; buttons take all keys or one tier (common/rare/mythic) out of their inventory + ender chest — only keys are ever removed, enchanted/spent items stay untouched |
| `/crate resholo` | OP | Rebuild all crate holograms |
| `/crate reload` | OP | Reload pools/key-drop chances |

Every crate block has a floating hologram (name + "right-click to open"). Keys drop from
**farming (mature crops), mining (ores) and fishing**: Common 1% / Rare 0.05% / Mythic
0.01% per action. Open GUI shows each reward's exact % (pools sum to 100).
