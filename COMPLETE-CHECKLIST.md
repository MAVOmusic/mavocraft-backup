# MAVOcraft — Complete Testing Checklist

**Server:** Paper 26.2 build 119 · Java 25 · IP `185.206.148.185:25567` (Java + Geyser/Bedrock UDP 19132)
**This file covers EVERYTHING:** vanilla gameplay, third-party plugins and all MAVOcraft plugins/mods.
**Run it twice:** (1) as **OP** (console can help), then (2) as a **normal non-OP player**.
Tick ✅ / ❌ next to every line. ⭐ = brand-new plugin (the CW4 "next 10" — test these first).

---

## PART A — Server boot & health (OP)

| # | Test | Command / How | Expected |
|---|---|---|---|
| A1 | Plugins enabled | `/plugins` | No red "disabled" plugins; all MAVO* present |
| A2 | Boot log | console | No stack traces; MAVOTpa/Locks/Timber/Crafting/Enchants/Couples/Miniboss/Spawners/Duels/Guilds all "enabled" |
| A3 | Vault hook | console | MAVODuels: "Vault NOT found" must NOT appear; MAVOMiniboss: coins reward works |
| A4 | Reload gate | `/updates reload` | Guide re-indexes; no errors |
| A5 | Shop index | `/sreload` | EconomyShopGUI reloads shops |
| A6 | TAB | `/tab reload` | No errors; sidebar = right overlay |

**Before you start:** keep a spare normal account logged in — half of this checklist needs a non-OP player.

---

## PART B — Vanilla tests (no plugins involved)

| # | Test | How | Expected |
|---|---|---|---|
| B1 | Day/night | `/time set day` (OP) | Sun cycle normal |
| B2 | Weather | `/weather rain` then `/weather clear` (OP) | Rain particles + puddles, then clear |
| B3 | Spawn protection | try to break/place near spawn (non-OP) | Spawn plaza ±60 blocks is protected (OP+creative only) |
| B4 | Mob spawning | travel >200 blocks from spawn (non-OP) | Hostiles spawn naturally |
| B5 | Death/graves | die once (non-OP) | MAVODeathChest grave appears; loot intact |
| B6 | Sleep vote | all players sleep at night | Night skips only when enough players sleep (Sleeper pack) |
| B7 | Beds | right-click bed (non-OP) | Bed bound to player; can use again |
| B8 | Chunk loading | `/chunk info` (ClaimChunk) | Chunk owner shows correctly |
| B9 | Enchanting table | enchant an item (non-OP) | Normal vanilla enchant flow + XP cost |
| B10 | Trade with villager | trade one item | Normal trades; EconomyShopGUI overrides prices where set |

---

## PART C — Third-party plugins/mod tests

### C1 TAB
| Who | Test | Expected |
|---|---|---|
| OP | `/tab reload` | No errors |
| OP+Player | `/tab scoreboard toggle` | Right-side sidebar (HUD scores) hides / shows |
| Player | `/tab scoreboard toggle` | Permission `tab.scoreboard.toggle` works for players |
| Player | look at TAB list | NameTags + scoreboard match server state |

### C2 LuckPerms
| Who | Test | Expected |
|---|---|---|
| OP | `/lp user <name> parent list` | Correct group(s) |
| OP | `/lp group default permission info` | Default group has expected permissions |
| OP | `/lp editor` (briefly) | Web editor opens |
| Player | `/lp ...` should NOT work | No permission |

### C3 Vault (economy)
| Who | Test | Expected |
|---|---|---|
| OP | `/balance` | Your balance shows |
| OP | `/eco give <name> 1000` | Balance +1000 |
| Player | `/balance` | Shows balance (EssentialsX `/bal`) |

### C4 EconomyShopGUI
| Who | Test | Expected |
|---|---|---|
| OP | `/sreload` | Reloads shops |
| Player | visit shop sign / shop villager | Can buy & sell; prices match configured shops |
| Player | sell a known item (`/shop sell`) | Money received; item removed |

### C5 ClaimChunk
| Who | Test | Expected |
|---|---|---|
| OP | `/chunk claim` | Chunk claimed (config `economy.useEconomy=false` — no charge) |
| Player | `/chunk info` | Claims show owner; unclaimed = wild |
| Player | `/chunk trust <name>` | Trusted player can build in your claim |

### C6 CoreProtect
| Who | Test | Expected |
|---|---|---|
| OP | `/co i` then break+place a block | Inspector shows block history |
| OP | `/co lookup u:<name> t:<time>` | Rollback/lookup results appear |
| OP | `/co undo` | Last action restored |

### C7 EssentialsX
| Who | Test | Expected |
|---|---|---|
| Player | `/balance` | Balance works |
| Player | `/kit` or `/spawn` | Essentials commands respond |
| ⚠️ IMPORTANT | Player | `/tpa` must be **MAVOTpa's** version (see D-11) — if EssentialsX's /tpa still answers, that is a conflict to report |

### C8 BlueMap (web map)
| Who | Test | Expected |
|---|---|---|
| OP | browser `mavocraft.my.pebble.host:8156` | Map renders; marker for the Mob Farm |
| OP | `/bluemap` | BlueMap status |

### C9 Geyser (Bedrock)
| Who | Test | Expected |
|---|---|---|
| OP | Bedrock device | Can join via `185.206.148.185:19132` |
| Bedrock player | `/warp hub` | Bedrock works normally |

---

## PART D — MAVOcraft plugins (command reference is in `MAVOCRAFT-COMMANDS.md`)

> Each block = **OP test row** + **Player test row**. Run the Player rows on the non-OP account.

### D-1 MAVOGuide 2.8.3
| Who | Test | Expected |
|---|---|---|
| OP | `/updates reload` | Reload ok |
| Player | `/updates`, `/tutorial`, `/whatsnew` | Menus open; What's New lists versions |

### D-2 MAVOEvents 1.2.1
| Who | Test | Expected |
|---|---|---|
| OP | `/event list` | 10 events listed |
| OP | `/event start zombiesiege` then `/event stop` | Event starts/stops with announcement |
| Player | `/event` | Current/live event info |

### D-3 MAVOLuckyCoins 1.5.6
| Who | Test | Expected |
|---|---|---|
| OP | `/ccollect give 5` | 5 Lucky Coins given |
| Player | `/wish` | Well info opens |
| Player | `/destroy hand` on junk | Unselleable junk deleted; sellable items kept |
| Player | `/destroyall` | Inventory cleared (hotbar/lucky coins/tools kept) |

### D-4 MAVODeathChest 1.2.0
| Who | Test | Expected |
|---|---|---|
| Player | die → `/grave` | Grave listed with coords + time left |
| Player | `/grave` click grave | 1,000 coins or 10 Lucky Coins travel (3s countdown) |
| OP | OP clicks a grave chest | Admin can open any death chest (mavodc.admin) |

### D-5 MAVOAchievements 1.7.2
| Who | Test | Expected |
|---|---|---|
| Player | `/ach` | Menu opens (52 categories) |
| Player | kill a zombie | Achievement progress/pop-up appears |
| OP | `/ach reload` | Reload ok |

### D-6 MAVOProfessions 3.15.5
| Who | Test | Expected |
|---|---|---|
| Player | `/profession` / `/profession check` | Menu + XP/level per profession |
| Player | harvest a crops / ore with a tool | XP goes to the right profession |
| Player | `/profession top lumberjack` | Leaderboard |
| OP | `/profession addxp <p> farming 100` then `/profession reload` | XP added; reload ok |

### D-7 Sleep pack (in Professions)
| Who | Test | Expected |
|---|---|---|
| Player | `/profession sleeper` | Sleeper info |
| Player | sleep at night with a bed | Night vote + rest bonuses work |
| Player | right-click at night | Bound-bed behaviour (no teleport through walls) |

### D-8 MAVOWild 1.7.4
| Who | Test | Expected |
|---|---|---|
| Player | `/wild` | 5s warmup then teleport to the wild (5k–400k blocks) |
| Player | `/wild` twice quickly | 5-min cooldown message |
| OP | `/wild portal` then `/wild homeportal` | Sets portal positions |
| OP | `/holoreset 60` | Floating holos rebuilt |

### D-9 MAVOShopNPC 1.3.3
| Who | Test | Expected |
|---|---|---|
| OP | `/shopnpc list` | NPCs listed |
| OP | `/shopnpc resholo` | Holos rebuilt at new size |
| Player | right-click an NPC | Runs its command / shop |

### D-10 MAVOWanderer 1.1.0
| Who | Test | Expected |
|---|---|---|
| Player | `/wanderer` | Next trader visit time |
| OP | `/wanderer spawn` | Trader spawns nearby with llamas |

### D-11 ⭐ MAVOTpa 1.0.0
| Who | Test | Expected |
|---|---|---|
| Player A | `/tpa PlayerB` | B gets a request; A gets "request sent" |
| Player B | `/tpaccept` | A teleports to B (3s stand-still warmup) |
| Player A | `/tpahere PlayerB` | B teleports to A |
| Player B | `/tpdeny` / Player A `/tpacancel` | Denies / cancels pending request |
| Player | move during warmup | "Teleport cancelled - you moved!" |
| Player | stand near monsters | "Teleport cancelled - monsters nearby!" |
| Player | spam `/tpa` | 30s cooldown message |
| OP | `/tpa` as admin | Cooldown bypassed (admin perm) |
| ⚠️ COMPAT | any | Ensure `/tpa` output says "+ request" (ours) not EssentialsX |

### D-12 ⭐ MAVOLocks 1.0.0
| Who | Test | Expected |
|---|---|---|
| Player | look at chest → `/lock` | Locked; other players can't open/break |
| Player | `/trust PlayerB` (looking at your lock) | B can open it |
| Player | `/untrust PlayerB` | B blocked again |
| Player | look at chest → `/unlock` | Lock removed |
| Player | lock a door | BOTH door parts useable only by owner (linked) |
| Player | lock a double chest | BOTH halves locked together |
| OP | blast a locked chest (TNT) | Explosion does NOT break locked blocks (`protect-from-explosion`) |
| Player | `/lock` more than 50 | "Lock limit reached" |

### D-13 ⭐ MAVOTimber 1.0.0 (HOTFIX 35 + 39)
| Who | Test | Expected |
|---|---|---|
| Player | break bottom log with axe | Whole tree falls; **max 10 logs collected** ("whole tree - 10 collected, cap 10/tree" if capped) |
| Player | chop a DARK FOREST tall dark oak / giant spruce (>10 logs) | **WHOLE tree falls** - no floating trunk stub; **10 logs collected** (capped) |
| Player | chop a small tree (<10 logs) | Falls fully with its natural amount |
| Player | chop a SHIPWRECK / VILLAGE HOUSE log wall | **NOT felled** - only the clicked log breaks ("not a grown tree") |
| Player | chop a player-built log house | **NOT felled** - only the clicked log breaks |
| Player | place logs/leaves then chop them | Never felled (player-placed wood is tracked; plant saplings instead) |
| Player | watch after the fall | **Leaves decay** ~0.5s later, drop sticks/saplings |
| Player | `/timber status` | ON/OFF + "max 10 logs/tree, XP cap 10 per tree, grown trees only" |
| Player | break with hand | No tree fall |
| Player | `/timber toggle` then break | Felling off; toggle persists after relog |

### D-14 ⭐ MAVOCrafting 1.0.0 (HOTFIX 35)
| Who | Test | Expected |
|---|---|---|
| Player | `/craft` (NON-op survival) | **Opens the beginner recipe list with ALL 50 recipes** (existing configs auto-upgraded on enable) |
| Player | `/craft` | 50 basic recipes, 45 per page, click = consumes ingredients + crafts |
| Player | `/craft stone_pickaxe` | Opens the page with Stone Pickaxe |
| Player | click a recipe in /craft | **GUIDE only - nothing is crafted/consumed**: opens a 3x3 preview of the REAL recipe (+result), recipe also unlocked in the vanilla recipe book (press E) |
| Player | `/craft` (all pages) | **100 beginner recipes** (3 pages of 45), each shows ingredients on hover |
| Player | `/crafting list` | 7 custom recipes listed |
| Player | craft Name Tag / Saddle / Lead / Chainmail (normal table) | Custom recipes still work |
| OP | `/crafting reload` | Reloads; recipes still craftable |
| OP | `/workbench` (or `/e craft`) | Essentials workbench still works for ops |

### D-15 ⭐ MAVOEnchants 1.0.0 (HOTFIX 35)
| Who | Test | Expected |
|---|---|---|
| OP | `/maenchant gem <p> VEIN 2` | Gem given to player |
| Player | `/gemshop` | GUI: 4 gem types × tiers I-X; prices 1M→512M; hovering shows **effect for THAT tier** (e.g. Vein V = 30 blocks), charges/cooldown + price |
| Player | click a gem in `/gemshop` with enough coins | Gem bought, coins deducted (Vault) |
| Player | use a gem | Charge consumed; L1 = 10 uses/10 min → L10 = unlimited (cooldown starts on first use, pool refreshes when it ends) |
| Player | run out of charges | "recharging - M:SS left"; effect stops until cooldown ends |
| Player | `/maenchant charges` | Shows uses left + cooldown per type |
| Player | `/gemshop` with < price | "You need X coins" |
| Player | mine ~50 ores | ~1% chance random gem (tier I), 0.5% II, 0.25% III... |
| Player | hold gem in main hand + pickaxe in OFFHAND, right-click | Enchant applied, gem consumed |
| Player | break an ore vein with VEIN pickaxe | Whole vein breaks |
| Player | mine ores with SMELT pick | Ores drop as ingots |
| Player | kill mobs with XP pick / LIFESTEAL sword | Bonus XP orbs / heal message |
| Player | apply 4th gem | "Tool gem limit reached" |

### D-16 ⭐ MAVOCouples 1.0.0
| Who | Test | Expected |
|---|---|---|
| Player A | `/marry PlayerB` | B gets proposal (120s expiry) |
| Player B | `/accept` | Married; broadcast message |
| Player | `/couple info` | Spouse name |
| Player | `/couple sethome` then `/couple home` | Shared couple home works for BOTH partners |
| Player | `/couple tp` | Teleports to partner |
| Player | stand near partner | Heart particles |
| Player | `/divorce` | Un-married; home cleared |
| Player | propose a married player | "They are already married" |

### D-17 ⭐ MAVOMiniboss 1.0.0 (HOTFIX 35)
| Who | Test | Expected |
|---|---|---|
| Player | `/hunt` (no boss alive) | Teleported OUTSIDE spawn to wild (500-2000 blocks, surface, 30s cooldown) |
| Player | `/hunt` (boss alive) | Teleported within 60 blocks of a live boss |
| Player | wait | Boss location broadcast **every 5 min** (a boss is SPAWNED first if none is alive) |
| OP | `/miniboss broadcast` | Spawns a boss if none is alive, then broadcasts its location |
| OP | `/miniboss status` | 0/3 alive + 5 types + 45 min + /hunt ON + broadcast 5 min |
| OP | `/miniboss locate` | Lists alive bosses (or "none") |
| Player | wait / hunt | Broadcast "A … appeared in the wild"; boss spawns 1k–5k from spawn, surface |
| Player | kill a boss | Coins + Lucky Coins + crate key + trophy head; broadcast |
| OP | edit config → `/miniboss reload` | Boss list reloads |

### D-18 ⭐ MAVOSpawners 1.0.0
| Who | Test | Expected |
|---|---|---|
| Player | silk-touch pickaxe → break spawner | "Silk-touched spawner (mob)" item |
| Player | break spawner WITHOUT silk | Vanilla behaviour (no item) |
| Player | place silk-touched spawner | Real spawner set to that mob |
| Player | `/spawner info` looking at spawner | Mob, delay, max nearby, player range, spawn range |

### D-19 ⭐ MAVODuels 1.0.0
| Who | Test | Expected |
|---|---|---|
| OP | stand where arena is → `/duelarena` | "Duel arena set" |
| Player A | `/duel PlayerB [bet]` | B gets challenge with bet |
| Player B | `/daccept` | Both teleported into arena; "DUEL START" |
| Player | fight | First death loses; broadcast + stats |
| Player | quit arena (walk out) | "You left the arena - forfeit!" → other wins |
| Player | `/dstats` | W/L + win rate |
| Player | `/dstats top` | Leaderboard top 10 |
| Player | `/duel 999999999` | "Bet must be 0-1000000" |
| Player | duel while already duelling | "already in a duel" |

### D-20 ⭐ MAVOGuilds 1.0.0
| Who | Test | Expected |
|---|---|---|
| Player A | `/guild create Ravens` | Guild created (cost 500 coins via Vault, 0 if no currency) |
| Player A | `/guild invite PlayerB` | B gets invite |
| Player B | `/guild accept` | Joined; chat works via `/g <msg>` |
| Player A | `/guild claim` in a chunk | 1st chunk claimed; later claims must touch it |
| Player B | try to break A's claimed chunk | Blocked — "claimed by an enemy guild" |
| Player B | interact with A's chest in claim | Blocked |
| Player B | damage A's animals in claim | Blocked |
| Player | `/guild map` | ASCII map (A=yours, X=others) |
| Player A | `/guild sethome` then `/guild home` | Teleports to guild home |
| Player A | `/guild promote PlayerB` | B becomes officer (can invite/claim) |
| Player | `/guild leave` then `/guild disband` (as leader) | Member can leave; leader must disband (or promote first) |
| OP | `/guild reload` | Reloads config |

---

## PART E — ⭐ THE 10 NEW FILES: BIG TEST RUN ORDER

Deploy **all 10 jars** at once (plus everything from any previous test run), then boot and test in this order — fastest signal first:

1. **Boot check** → `/plugins` shows 10 new MAVO* plugins enabled (D-11 … D-20).
2. **MAVOTpa** — 2 players: /tpa → /tpaccept (D-11) ✅
3. **MAVOLocks** — lock chest + double chest + door (D-12) ✅
4. **MAVOTimber** — one oak tree (D-13) ✅
5. **MAVOCrafting** — craft 1 name tag + 1 saddle (D-14) ✅
6. **MAVOEnchants** — OP gives gem, apply, vein one ore cluster (D-15) ✅
7. **MAVOCouples** — marry + /couple sethome + /couple home (D-16) ✅
8. **MAVOMiniboss** — `/miniboss status`, wait for spawn or edit config spawn-interval-minutes: 1 + `/miniboss reload` (D-17) ✅
9. **MAVOSpawners** — silk-touch one spawner (D-18) ✅
10. **MAVODuels** — `/duelarena`, duel with 0 bet then with 100 coins (D-19) ✅
11. **MAVOGuilds** — create + claim + enemy-can't-build (D-20) ✅
12. **Persistence check** — restart server and confirm: locks, marriage, duels stats, guilds + claims, miniboss config survive.

---

## PART F — Datapacks / logo / HUD

| Who | Test | Expected |
|---|---|---|
| OP | `/datapack list enabled` | `file/MAVOcraft-75-datapack.zip` + `file/MAVOcraft-95-datapack.zip` shown (auto-enabled; if `available` use `/datapack enable "file/MAVOcraft-95-datapack.zip"` — the `.zip` is part of the ID) |
| OP | `/function mavocraft95:logo_test` | Logo frame + letters appear at Y=249 (read at spawn, FACE WEST, look up) |
| OP | `/function mavocraft95:logo_build` | Full 24×95 logo — **reads `MAVOCRAFT` upright left→right** (sea lantern letters, glowstone borders, lime A's) |
| OP | `/function mavocraft75:logo_test` (frozen v4) | Same layout as v4 (appears 180° rotated from spawn on purpose — frozen design) |
| OP | `/function mavocraft95:logo_clear` | **Whole 24×95 canvas removed** (incl. frame / black bg / glowstone borders; Y=249 only) |
| OP | `/tab scoreboard toggle` | Sidebar hides for ceiling checks (remember to toggle back) |
| OP | `/hud` | HUD on/off + config works |

---

## PART G — Report template (paste back with your results)

```
VERSIONS TESTED: [jars list]
PART A (boot): A1✅ A2✅ A3✅ A4✅ A5✅ A6❌ — ...
PART B (vanilla): ...
PART C (third-party): ...
PART D (new plugins): D-11 … D-20 (each ✅/❌ + notes)
PART E (persistence): pass/fail
PART F (datapacks): pass/fail
ANY ERRORS IN CONSOLE: [paste]
STUCK / NEEDS JAR-FIX: [plugin + command + what happened]
```
