# 3.0.7 — Enchants gem overflow hotfix

## Report and scope
A player reported a lost Diving I mining gem with a full inventory. Previous code
already dropped addItem leftovers, but at the ore block corner. The exact loss
cause is unconfirmed; the new delivery avoids that spawn location and random scatter.

- Inventory first; only returned leftovers drop (no second copy).
- Overflow drops at the player's feet + 0.1 blocks, zero initial velocity.
- Glowing and invulnerable, configured before spawn; 10-tick pickup delay.
- Explicit inventory-full message tells the recipient where to collect it.
- Applies to mining, gemshop and admin gem delivery, preserving gem metadata.
- No mail integration, cap bypass or unlimited storage. Normal item despawn,
  void loss and third-party item removal still apply. Collect promptly.
- Mining odds, gem tiers/prices and the 3.0.5 payment-failure guard are unchanged.

## Deployment
Stop the server. Replace only MAVOEnchants-3.0.5.jar with MAVOEnchants-3.0.7.jar;
do not keep both versions or delete configs/data. Start and check its enable line.
The 3.0.6 pack was confirmed live by the user's 08:09 boot log, including Guide
migration v26 -> v28; gameplay tests remain pending.

## Live checks (pending — do off-stream)
1. Empty slot: `/maenchant gem MAVOmusicYT AQUA 1` gives exactly one Diving I
   directly to inventory, with no ground duplicate or full-inventory warning.
2. Completely full inventory (no matching partial gem stack): same command gives
   exactly one glowing gem at the recipient's feet plus the warning. Free a slot,
   pick it up and verify its tier/lore and application to a helmet.
3. Full inventory with a matching partially filled gem stack: gem stacks in the
   inventory; no ground duplicate. Non-matching metadata must not merge.
4. Repeat #2 in the narrow mining tunnel with a wall in front; gem must not spawn
   inside that wall. Check fire/lava protection in a controlled test area.
5. Full inventory `/gemshop` purchase: exactly one charge and one delivered gem.
   Insufficient funds/failed withdrawal: no gem, unchanged balance on failure.
6. On the next genuine ore gem drop with full inventory, confirm the same delivery
   and warning. Do not increase production drop odds just to test.
7. Bedrock: verify visibility/pickup (glowing outline rendering may differ).

## Replacement for the reported loss (run ONCE, after freeing a slot)
`/maenchant gem MAVOmusicYT AQUA 1`
This is an operator replacement, not automatic reimbursement.

## Build/package verification
- GitHub Actions run 34591518653: successful JDK 25 build.
- Source checks: all three grant paths use the shared helper; only leftovers drop;
  purchase delivery remains behind the successful-payment guard.
- Built Enchants class contains the helper/protection/warning; plugin.yml is 3.0.7.
- Pack: 59 jars; 26 MAVO @3.0.0, 2 @3.0.2, 11 @3.0.5, 6 @3.0.6, 1 @3.0.7.
- Build is compilation verification, not a gameplay test; live checks above pending.
