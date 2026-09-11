# 3.0.8 — Crates: one key per roll + 30-second cooldowns

## Confirmed cause / fix
`takeKey` cleared the entire first matching inventory stack and returned its size;
`roll` used that result only as a yes/no check and issued one reward. Now it removes
exactly one matching key, cloning and decrementing a multi-key stack to preserve
all item metadata. It stops after the first matching slot, including offhand.

Cooldown remains per player, per crate type; it is checked BEFORE consuming a key.
Repeat attempts during cooldown spend nothing. Common/Rare/Mythic defaults are all
30 seconds. A separate `cooldown-version: 1` migration updates existing crate sections
(including custom ones) once, without replacing rewards, key odds, or placed blocks.
Pool-version stays 2: no new reward-pool reset. Cooldown messages round up rather
than saying `0s` while still blocked. Offhand interaction does not reopen the preview.

## Deploy
Stop the server; replace only MAVOCrates-3.0.6.jar with MAVOCrates-3.0.8.jar
(pack filename `25_MAVO-Crates-3.0.8.jar`). Keep all plugin config/data folders.
Start; verify the 3.0.8 enable line and, for existing configs, the one-time
`all crate cooldowns upgraded to 30 seconds` log. Enchants remains 3.0.7.

## Automated regression checks
CrateRulesTest covers 2-key and 64-key stacks, singleton removal, wrong-tier skip,
offhand, no-match, metadata-preserving clone use, all-tier/custom config migration,
unrelated config preservation, migration persistence/idempotency, bundled defaults.
CI now runs Maven tests instead of skipping them. These mocks/config tests do not
replace live server inventory checks below.

## Live checks — pending, off-stream
1. For each of common/rare/mythic: give 2 keys, open once, expect 1 reward + 1 key
   remaining. `/crate info <type>` must show 30s.
2. Try again immediately (including double-click): no reward and no further key
   consumed. At 30 seconds, opening works and consumes the remaining single key.
3. Repeat with 64 keys (63 left), separate stacks (only one loses 1), and keys in
   offhand (2 -> 1). Verify retained keys still work and have original lore/name.
4. Wrong-tier key / ordinary tripwire hook / no key: no roll, no item removed.
5. Open/close preview without pressing OPEN: no key spent, no cooldown started.
6. Restart: no repeated cooldown migration; crate positions, rewards and key-drop
   odds unchanged; remaining cooldown survives restart. `/crate reload` also keeps
   30s and does not re-run migration on an already-upgraded config.
7. Check Java and Bedrock clients. New per-hand guard should open one preview.

## Optional replacement for the reported extra key (run once)
`/crate givekey MAVOmusicYT common 1`
One extra common key was reported consumed. No automatic reimbursement is made.
