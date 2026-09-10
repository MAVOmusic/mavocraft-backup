# MAVOcraft — Security & Exploit Audit (2026-09-10)

Source-code audit of all 33 source plugins + black-box test plan for the 13 no-source jars.
Severity: 🔴 critical (fix before players use it) · 🟠 high · 🟡 medium · 🔵 low · ⚪ info/design.

---

## 🔴 CRITICAL

### AUDIT-CRIT-01 — ChestShops: buyers pay and receive NOTHING
- **File:** `sources/src-chestshop/.../ChestShops.java`, `buy()` (~L529-560). `grep addItem` = zero hits in the file.
- **Flow:** stock check → `withdrawPlayer(buyer, cost)` → `removeItem` from chest → `depositPlayer(seller, pay)` → "Bought …" message. **No `addItem` to the buyer anywhere.**
- **Impact:** every purchase destroys the item and moves coins buyer→seller. Buyers lose money for nothing; with `tax-percent: 0` self-buys delete stock for free.
- **Secondary (same function):** stock counted and removed by `Material` only — enchanted/renamed stock is consumed at base price (owner-side loss).
- **Also:** `onBuyClick` buys on ANY named item click, not just the Buy buttons (info-item clicks buy too).
- **Fix:** add the item to the buyer (`addItem` + overflow drop) between payment and message; match stock by full meta or restrict shops to plain items; gate clicks to the two Buy buttons only.
- **Test rows:** E-OP-001, C-PL-155/156.

## 🟠 HIGH

### AUDIT-HIGH-02 — `/miniboss broadcast`: any player can summon bosses on demand
- **File:** `sources/src-miniboss/.../MiniBoss.java`, `onCommand` `case "broadcast"`: `if (alive.isEmpty()) spawnOne();` — **no permission check, no cooldown**.
- **Impact:** kill boss → `/miniboss broadcast` → next boss instantly. The 45-minute interval is bypassable; unlimited boss farming (coins + Lucky Coins + mythic keys).
- **Fix (pick one):** (a) gate to `mavominiboss.admin`; or (b) keep public broadcast but add 30-min per-player cooldown and NEVER spawn (broadcast-only). Recommend (b) — keeps the social feature, kills the farm.
- **Test rows:** E-OP-002, C-PL-098.

## 🟡 MEDIUM

### AUDIT-MED-03 — AuctionHouse: listing item LOST when seller inbox is full
- **File:** `sources/src-auction/.../AuctionHouse.java` — `cancelListing()` removes the listing from memory AND `data` BEFORE `inboxAdd()`; on full inbox it prints "contact an admin (item stays in the listing data)" but the data was already nulled → item gone. Same pattern in `expireAll()` for the seller return (no message at all there).
- **Fix:** `inboxAdd` first; only remove the listing when it succeeds; else leave listing + tell seller to clear inbox.
- **Test rows:** E-OP-003, C-PL-151.

### AUDIT-MED-04 — AuctionHouse: escrow refunds can vanish
- **File:** same, `bidOn()` / `purchaseAt()` / `cancelListing()`: online-bidder refund is `if (depositPlayer(old, bid).transactionSuccess()) msg(...)` — if the deposit fails there is NO pending fallback (offline path has one). Money destroyed.
- **Fix:** else-branch → add to `players.<uuid>.pending` like the offline path.
- **Test row:** C-PL-150 (hard to trigger live; fix by inspection).

### AUDIT-MED-05 — Mail: sending to a full mailbox DELETES the victim's oldest mail
- **File:** `sources/src-mail/.../Mail.java`, `send()`: `while (ks.size() > maxMail) data.set(... oldest ..., null)`.
- **Impact:** anyone can spam 100 junk mails at a victim and permanently destroy their stored items/coins. Grief + wealth destruction.
- **Fix:** reject the send ("their mailbox is full") BEFORE withdraw/take-hand; refund path if already taken.
- **Test rows:** E-OP-004, C-PL-190.

### AUDIT-MED-06 — Locks: hoppers drain + pistons push locked blocks
- **File:** `sources/src-locks/.../Locks.java` — no `InventoryMoveItemEvent`, no piston handlers at all.
- **Impact:** hopper under a locked chest steals everything; pistons can move locked blocks (door/chest dupes possible via push tricks).
- **Fix:** cancel hopper pull/push involving locked inventories; cancel piston extend/retract moving locked blocks.
- **Test rows:** E-OP-005/006, C-PL-126/127.

### AUDIT-MED-07 — Instant combat-escape teleports (no warmup / monster check / cooldown)
- **Files:** `src-couples` (`teleport()` L206 = bare `p.teleport`, used by `/couple home` + `/couple tp`); `src-guilds` (`/guild home` L508 = bare teleport).
- **Impact:** free escape from any danger (bosses, duels-adjacent PvP, lava, raids), unlimited, no accept needed for `/couple tp`. Compare: /tpa, /home, /warp, grave travel ALL have warmup + monster radius.
- **Fix:** same standard as /tpa: 3s stand-still + 12-block monster radius (+ 30s cooldown on `/couple tp`).
- **Test rows:** E-OP-007, C-PL-132/133/244.

## 🔵 LOW (fix opportunistically)

- **AUDIT-LOW-08 — Duels:** at `/daccept`, if the SECOND bet withdraw fails, the FIRST player's bet is not refunded (`Duels.java` accept handler). Rare (both passed `has()`), but free money loss. Fix: refund first on second failure.
- **AUDIT-LOW-09 — FishComp:** `payOut()` to an online player with a failed deposit has no pending fallback (offline path has one). Fix: pending fallback.
- **AUDIT-LOW-10 — Unchecked withdraws:** Enchants gemshop (`econ.withdrawPlayer` result ignored), Curator extras, Warps create. Single-threaded so the `has()`→withdraw race is near-impossible, but check `transactionSuccess()` and abort the give on failure.
- **AUDIT-LOW-11 — Guild/AH data races:** none found (all main-thread). No action.
- **AUDIT-LOW-12 — Casino close-to-settle:** `onClose` forfeits most games but settles mines/hiLo/crash at current multiplier; `settled` flag prevents double-pay. Verified by code; confirm live (C-PL-060/061).

## ⚪ INFO / DESIGN (no bug, but decide)

- **AUDIT-INFO-13 — AH tax is sale-only:** posting is FREE (20%/5% taken from proceeds). Slot limits (1 free, up to 20) cap spam, but zero-risk price manipulation is possible. Consider a small listing fee.
- **AUDIT-INFO-14 — DeathChest cost curve:** `1000 × 2^(level-1)` coins (cap 2^30) → L5 = 16k, L10 = 512k, L15 = 16M. Verify this brutality is intended; consider a softer curve or lower cap.
- **AUDIT-INFO-15 — Casino RTP unverified:** 10 games with individual multipliers; needs a math pass or long-run test to confirm the house edge (~95% claim). Test-heavy; schedule separately.
- **AUDIT-INFO-16 — `/tpa` conflict:** MAVOTpa must win over EssentialsX. Covered in checklists (C-OP-016, C-PL-118) — verify every deploy.
- **AUDIT-INFO-17 — No-source jars can't be code-fixed:** Tavern, Vault, Homes, Spawn, Quests, HUD, PortalRoom, Trades, Streaks, CommunityGoals, ChunkBorders, ChunkPrices, PersonalVault. All admin-subcommand gates for these are TEST-ONLY rows (D-OP-088/096/105/107/115/117/119/121). If a hole is found, the fix is LuckPerms negations or a replacement build.

## SUSPECTED (needs in-game confirmation)

| ID | Hypothesis | Test rows |
|---|---|---|
| SUS-01 | Duel escape via /home, /tpa, pearl, chorus: no teleport listener → no forfeit, fight stuck, no timeout | E-OP-008, C-PL-144/145 |
| SUS-02 | TPA warmup vs relog/damage edge cases | C-PL-115/116 |
| SUS-03 | AH post-GUI item lost on crash/restart (postItem lives in memory Holder) | C-PL-149 (`/ah` post then restart before confirm — use junk!) |
| SUS-04 | Curator deposit-crate items lost on crash mid-open | C-PL-242 |
| SUS-05 | Pet carry-slot spam/close/relog dupe | C-PL-185 |
| SUS-06 | Crate OPEN double-click race consumes 2 keys / pays twice | C-PL-103 |
| SUS-07 | ChestHunt simultaneous-take dupe (2 players same tick) | C-PL-109 |
| SUS-08 | Guild claim inside someone's ClaimChunk chunk (grief/protection clash) | C-PL-245 |
| SUS-09 | ChestShop double-chest half-break desync | C-PL-159/160 |
| SUS-10 | MobFarm / spawner-farm profit flood (entry 10k vs loot value) | C-PL-054, C-PL-140 |
| SUS-11 | Wanderer / master-trader arbitrage vs shop sell prices | C-PL-042, C-PL-230 |
| SUS-12 | Quest mystery stacks (1-64 incl. name tag/saddle) bypass shop economy | C-PL-210 |
| SUS-13 | Streak/event/seasonal/casino faucet sizes need numbers from live play | C-PL-166/168/227/229, E-OP-010 |
| SUS-14 | CommunityGoals shop-bonus does nothing ("applied via data flag for shop plugin / docs") | C-PL-228 |
| SUS-15 | Shill bidding via alt accounts (seller can't bid own, alts can) — policy, not code | — |
| SUS-16 | Bedrock GUI usability (AH/pets/casino click paths) | B-PL-012 |

##verified clean (spot-checked, no action)

GUI click-cancel discipline (all menus cancel + holder/track + raw-slot gating; Hotfix-38 pattern in gemshop/crates/chesthunt/casino) · Duels bet escrow (withdraw both at accept, 10% house cut, logout= forfeit, leaver=forfeit) · Casino bet charged at game start, attempts 10+10/10 MC days · DeathChest grave ownership + 30-min expiry · TPA/Locks/Warps/Homes warmup+monster+cooldown standards · Timber survival-gated + grown-trees-only + 10-log cap · Professions/Casino XP survival-gated · AH min-price (shop sell ×110%), bound-no-relist, buy-own blocked, bid escrow + auto-extend · Mail 7-day expiry + offline-safe · FishComp/BossRaid offline payouts queued · All source-plugin admin gates present (`mav*admin`).

## Fix order (proposed)

1. CRIT-01 ChestShops buyer give (+ NBT stock match + button-only clicks) — blocks ALL shop use until fixed.
2. HIGH-02 Miniboss broadcast cooldown/no-spawn.
3. MED-05 Mail full-mailbox reject (griefable NOW).
4. MED-06 Locks hopper/piston.
5. MED-07 warmups on /couple home|tp + /guild home.
6. MED-03/04 AH inbox/escrow ordering.
7. LOW-08/09/10 one-line hardening.
8. SUS confirmations from your test run → fixes round 2.
