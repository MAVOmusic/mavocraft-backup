# MAVOcraft — NEXT IDEAS (21-30) + PLUGIN UPGRADES

*Proposals for review. Nothing here is built yet — tick the ones you want and they go into the build queue.
Effort: 🟢 easy (small plugin / config) · 🟡 medium · 🔴 large (new system, multi-part).*

---

## PART 1 — 10 NEW IDEAS (21-30)

### 21. `/bounty` — Bounty Board 🟡
- Put coins on a player's head (min 5k, no self-bounty, 1 active per target).
- Kill them → bounty paid instantly from the pot + death message says who claimed it.
- `/bounty` GUI: active bounties, top bounty hunters, your own listed bounties.
- Anti-abuse: no bounty on offline >30 days, no alt farming (kill credit needs 3+ hits, 10 min gap between same-pair claims).
- *Why:* player-driven economy sink + gives PvP a purpose without griefing.

### 22. `/party` — Parties (up to 5) 🟢
- `/party create/invite/join/leave` — party chat (`/p <msg>`), `/p home` (teleport to leader), party XP shown in HUD.
- Boss kills credit the whole party; party members share kill-reward chat line.
- Config: max size, invite expiry, teleport cooldown.
- *Why:* bosses and hunts are social — makes groups work as one team; zero conflict with couples/guilds.

### 23. `/dungeon` — Dungeon Runs 🔴
- 4 escalating dungeon rooms (arena-style instanced rooms, 1-4 players): waves of mobs + a boss per room.
- Difficulty ladder: Copper → Iron → Gold → Diamond (unlock = clear previous room).
- Loot: coins + dungeon tokens (trade in `dungeon-shop` for gear/crate keys); 1 run/player/day, leaderboard for fastest clear.
- Built on the same spawn/arena tech as Miniboss (proven), so risk is contained.
- *Why:* the biggest gap right now is a repeatable PvE endgame that isn't open-world.

### 24. `/stats` — Career Stats & Leaderboards 🟢
- Per-player record: playtime, kills/deaths, professions levels, bosses killed, fishing best catch, marriages, duels won, bounties claimed.
- `/stats <player>` + `/stats top <stat>` (top 10 boards in a GUI).
- *Why:* players love progress proof; gives every other plugin a "scoreboard moment".

### 25. `/trade` — Safe Player-to-Player Trade 🟡
- 2-slot each side + coin field; both must accept (no drop-on-ground item swaps).
- Anti-scam: both see a confirmation summary; cancel = everything back.
- Config: max items per trade, trade cooldown, coin limit.
- *Why:* chest shops cover selling; item-for-item trades are still done by dropping stuff on the ground.

### 26. `/parkour` — Timed Parkour Courses 🟡
- 3 official courses (easy/medium/hard) with checkpoints; timer + best time per player.
- Medals: gold/silver/bronze per course; first completion rewards coins; `/parkour board` = fastest times.
- *Why:* cheap to build, huge player engagement, pure skill content with no economic risk.

### 27. `/dig` — Treasure Maps 🟢
- Buy a map (or win one from crates) → a treasure chest is buried 500–2000 blocks from spawn.
- Dig with any shovel when you're within 5 blocks; loot = coins + random items scaled to map rarity.
- Anti-exploit: buried chest isn't breakable/findable by other means; map is consumed; 1 active map/player.
- *Why:* turns exploration into a mini-game; pairs well with `/wild` and the existing Chest Hunt.

### 28. `/fame` — Build Showcase & Weekly Votes 🟡
- `/fame submit <name>` marks your build; other players vote once/day via `/fame board`.
- Weekly top 3 get coins + a sign/plaque at spawn; total fame recorded per player.
- Anti-abuse: votes need 5+ min playtime and aren't from same IP/session twice; submit resets annually.
- *Why:* celebrates builders (museum is about items; nothing rewards builds) — best social glue for a community server.

### 29. `/bank` — Player Bank with Interest 🟢
- `/bank deposit/withdraw` (coins are Vault money, so no risk) + `/bank balance`.
- Weekly interest (config: 0.5–2%/wk) capped at a balance; bank tier upgrades (Copper→Iron→Gold→Diamond, paid in coins) raise interest + cap.
- *Why:* gives a reason to save, a slow money faucet to balance, and a NEW coin sink (tier upgrades).

### 30. `/brew` — Alchemy 🟡
- Craft custom potions: base potion + 2-3 ingredients, choose effects from unlocked recipes (Masterwork Potions: speed+regen 10s, etc.).
- Rare "boss essences" drop from minibosses (gate the best brews behind bossing — ties the boss loop in).
- `/brew` GUI shows recipes + what you own; brews have duration/potency limits so nothing breaks balance.
- *Why:* a crafting/progression layer with a real endgame recipe hunt; deepens the boss reward loop.

---

## PART 2 — IMPROVE WHAT'S ALREADY LIVE

Picked by: what players would feel immediately (value) vs what's cheap (effort). Nothing here replaces behaviour — all additive/config-gated.

| Plugin | Improvement idea | Effort |
|---|---|---|
| MAVOMiniboss | **Boss trophies**: each of the 15 bosses drops a unique trophy; `/trophy` hall at spawn shows your collection (15/15 = title). | 🟡 |
| MAVOMiniboss | **Boss Rush week**: kill all 15 types in one MC week → bundle of 3 crate keys + coins; `/miniboss rush` progress. | 🟡 |
| MAVOCrafting | **Search + favorites** in `/craft` (favorites kept per player); recipes you can craft right now get a ✅ marker. | 🟢 |
| MAVOChestHunt | **Weekly Golden Chest** (1/wk, better odds, always ≥1 rare); **streak bonus**: 3 days in a row = +1 extra roll. | 🟢 |
| MAVOCouples | **Shared bed/home** (`/couples home`) + **anniversary rewards** (year 1 = heart chest); `/couples top` longest marriages. | 🟢 |
| MAVOGuilds | **Guild bank** (shared stash, member tiers) + **weekly guild quests** (group objective → everyone's small reward). | 🟡 |
| MAVODuels | **Ranked ELO** + `/duel top`; **bet lobby** (stake coins, winner takes pot); spectator mode. | 🟡 |
| MAVOSpawners | **Spawner upgrades** (rate ×1.5/×2 via paid upgrades) + silk-touch trade item to move a spawner. | 🟡 |
| MAVOEnchants | **Gem fusion**: 3 same-tier gems → 1 next tier; `/gemshop` shows your gem count; socket limit per item. | 🟢 |
| MAVOProfessions | **Profession of the week** (rotating 1.5× XP on one profession) + level titles (Lv30+ = title shown in chat). | 🟢 |
| MAVOTavern | **Group rest**: whole party sleeps at once when the leader uses the tavern bed; **daily special** (cheap rest 1 day/wk). | 🟢 |
| MAVOLuckyCoins | **Coin exchange shop**: spend lucky coins on small boosts (double XP hour, /wild cooldown reset) — gives coins a sink. | 🟢 |
| MAVOCurator | **Visitor rewards**: +coins when players view/vote your exhibit; **Exhibit of the week** spotlight at spawn. | 🟡 |
| MAVOTimber | **Axe durability bonus during fell** (no dura loss on tree logs) + **combo XP** (5+ logs same tree = bonus). | 🟢 |
| MAVOFishComp | **Personal best + weekly leaderboard** (`/fishcomp top`), weekend double-points event. | 🟢 |
| MAVOCasino | **Loyalty points** (every spin earns points → free spin at 100) + **progressive jackpot** (5% of each loss grows the pot). | 🟡 |
| MAVOHud | **Widgets on/off** (time, coins, xp, bounty, party) + show boss-rush progress when a boss is alive. | 🟢 |
| MAVOCommunityGoals | **Live progress broadcast** at 25/50/75/100% + per-goal reward tiers (not one flat reward). | 🟢 |
| MAVOQuests | **Quest tiers** (easy/medium/hard board, harder = better rewards) + `/quests reroll` once a day. | 🟢 |
| MAVOPets | **Pet 3.0**: pets give a small bonus (pocket XP boost or auto-pickup of drops) + pet names/hats. | 🟡 |
| MAVOWild | **Squad wild**: party members teleport together (`/wild` anyone = all party joins). | 🟢 |
| MAVODeathChest | **Auto-loot on walk-over** (configurable) with a 30s hold, + chest expiry timer broadcast. | 🟢 |
| MAVOAchievements | **Milestone broadcast** (top 10% players / first-time server records) + `/ach top`. | 🟢 |
| MAVOPortalRoom | **Portal cooldown + `/portal list`** in the guide; cheaper "practice jumps" at spawn. | 🟢 |
| MAVOBossRaid | **Raid loot tiers**: participation even without the kill gets a small consolation crate. | 🟢 |

---

## Suggested build order (if you approve everything)

1. **Quick wins (🟢 first, 8-10 files):** 22 `/party` · 24 `/stats` · 27 `/dig` · 29 `/bank` + the 🟢 upgrades (crafting search, chest streak, couples home, gemshop count, profession-of-week, coin shop, HUD widgets, quest tiers, wild squad, death chest auto-loot, raid consolation).
2. **Medium (next):** 21 `/bounty` · 25 `/trade` · 26 `/parkour` · 28 `/fame` · 30 `/brew` + 🟡 upgrades (trophies, guild bank, duel ranked, spawner upgrades, casino points, curator visits).
3. **Large (last):** 23 `/dungeon` + Miniboss Boss Rush (they share tech and can ship together).

*Every one follows the house rules: config-driven, bundled default configs with copyDefaults, version bump = signal, Guide page + What's New entry, full checklist with OP + player checks.*
