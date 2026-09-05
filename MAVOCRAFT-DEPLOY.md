# MAVOcraft — UPDATE GUIDE (2026-09-05: SLEEP PACK — Professions 3.15.0 / DeathChest 1.2.0 / Guide 2.8.1)

Plain steps. Only replace/delete files named here. Never delete plugin folders.

## 0. READ THIS FIRST (mobfarm saves + sleep pack setup)

- MobFarm 2.7.4 stays as-is. The MobFarm jar NEVER contains your footpath: `/mobfarm savehub`
  MUST have been run before any `/mobfarm purge`/`clear`, and hub + bay zips copied to your PC.
- The Sleep pack adds the 10th profession (Sleeper), the 18:30 night vote and level-scaled
  grave costs. It needs ONE admin setup command after boot (`/sleeper tavernset`) and the
  LuckPerms group `sleeper` (only if you want the L100 rank group; command below).

## 1. STOP the server (full stop, no /reload)

## 2. DELETE these files from plugins/ (jars only)

    MAVOProfessions-3.14.2.jar    <- replace with 3.15.0
    MAVODeathChest-1.1.1.jar      <- replace with 1.2.0
    MAVOGuide-2.8.0.jar           <- replace with 2.8.1
    MAVOMobFarm-2.7.3.jar         <- replace with 2.7.4 (if not already done)
    (2.6.x / older MobFarm jars if still there)

AuctionHouse 1.0.3 / Mobile plug-ins unchanged.

## 3. UPLOAD these jars into plugins/

    MAVOProfessions-3.15.0.jar  (Sleeper profession + night vote + bound bed + rest bonuses)
    MAVODeathChest-1.2.0.jar    (/grave costs scale with Player Level: 1000/10 at Lv1, x2 per level)
    MAVOGuide-2.8.1.jar         (professions/tavern/deathchest pages updated, new Sleep page)
    MAVOMobFarm-2.7.4.jar       (if not already done - keeps saved zips, protects farm +50)

## 4. KEEP all configs / data

- plugins/MAVOProfessions/config.yml + data.yml: KEEP (Sleeper + sleep section are added by
  the new jar with defaults; your 9 professions + levels are untouched).
- plugins/MAVODeathChest/config.yml + data.yml: KEEP (costs are migrated to level-scaled
  1000/10 bases automatically; your graves are untouched).
- plugins/MAVOGuide/config.yml: KEEP. plugins/MAVOMobFarm/config.yml + data.yml: KEEP.
- world/datapacks/*-datapack.zip (zombie etc.) and anything you copied to your PC: KEEP.

## 5. START the server

Boot log: "MAVOProfessions v3.15 enabled: 10 professions..." and NO warnings.
Your world still contains the hub, footpath and zombie bay exactly as they are.

## 5b. SLEEP PACK - one-time admin setup

    /sleeper tavernset        <- look at the plaza Tavern bed and run this (2 blocks or less)
                                 so Tavern rests award +1 profession point to active professions
    lp creategroup sleeper    <- optional: creates the group for the L100 SLEEPER RANK
                                 (per-profession config commands run at L100 automatically;
                                 default "lp user %player% parent add sleeper")

Then a quick test at night (or /time set night 18000 on a test account):
- 5+ players online at 18:30 -> "Sleep time!" broadcast; type `!sleep yes` / `!sleep no`.
- /sleeper bind whilst looking at a bed -> right-click it (sleep only, no respawn/home).
- /grave -> costs should show 1000 coins / 10 LC at Player Level 1.

## 6. SAVE YOUR WORK FIRST (most important step)

    /mobfarm savehub                    <- captures hub + walls + EVERY footpath you built
                                           (not the 36 bay boxes) into hub-datapack.zip;
                                           prints the full path - copy it to your PC
    /mobfarm zombie save                <- already saved; run again if you edited the bay
                                           since the last save

Check the file panel: world/datapacks/ must show hub-datapack.zip + zombie-datapack.zip
(and the others as you build/save them). Download both to your PC now.

## 7. Optional: full reset, ONLY after step 6 (zips are safe on disk + your PC)

    /restart                            <- restart so the hub zip is discovered
    /mobfarm purge
    /mobfarm clear                      <- wipes hub + paths + bays in the WORLD
    /mobfarm setcenter                  (x2 - once per range, as before)
    /restart
    /mobfarm buildhub                   <- restores hub + walls + footpaths from the zip
    /mobfarm build zombie               <- restores the zombie bay from its zip
    /mobfarm build husk                 <- then each other bay you have completed

Do NOT run /mobfarm build (no arg) here: it only writes MISSING packs (existing ones are
kept now), but it also does not restore bays - build <mob> does that. If you really want
the pristine plugin layouts again: /mobfarm build force (this OVERWRITES saved bay edits).

## 8. Build / reapply one bay (unchanged from 2.7.x)

    /mobfarm build zombie       <- only the zombie bay (clear -> 5s -> reload -> apply -> VERIFY)
    /mobfarm rebuild <mob>      <- alias of build <mob>
    /mobfarm zombie save        <- save YOUR current bay again after edits

## 9. Hub + footpath (as often as you finish a new path)

    /mobfarm savehub            <- re-save hub+paths (bay boxes excluded). It covers the whole
                                   farm area + 50 blocks around, so paths past the farm edge
                                   are captured too. Copy the printed path to your PC.
    /mobfarm buildhub           <- restore hub+paths from the zip (after a restart any time)

## 10. Go back to your mob zone

    /mobfarm current            <- teleports to the mob zone you bought this session
                                   (no re-pay). /mobfarm enter first, /mobfarm pick to unlock.

## 11. Protection

Whole farm area (hub + bays + paths + 50 blocks around) is protected. Normal players can
kill mobs, open chests, deposit/remove items - NOT break/place/bucket/ignite. Owner group
still bypasses (so you can build paths). Use a second account to test as a normal player.

## 12. NEVER delete (standing rule)

plugins/EconomyShopGUI/, plugins/LuckPerms/, plugins/TAB/, any MAVO plugin's data.yml /
homes.yml / config.yml not named above, world/datapacks/*-datapack.zip (incl. hub zip).

## 13. Sleep pack rules of play (3.15)

- NIGHT VOTE: opens 18:30 (chat "Sleep time!") when MORE than 4 players are online,
  closes 19:30. Type `!sleep yes` / `!sleep no` (or `/sleeper vote yes|no`). To skip to
  06:00 next day: more YES than NO **and** >=75% of online players voted. Vote skips give
  NO Sleeper XP. Bed quorum still works without a vote: 1->1, 2->2, 3-4->2, 5-7->3,
  8-9->4, 10+->5 players in bed (no beds needed for the vote).
- TAVERN REST (100 coins, unchanged): +1 profession point to EVERY active profession
  (and counts as 1 Sleeper sleep). Tavern paid skip and the free vote coexist.
- SLEEPER (10th profession, no tools): +1 XP per successful bed sleep (once per day);
  need = 50 rests to L2, +50 more each level; L10/L20/... = +1 extra heart (2 HP each)
  and 100 Lucky Coins (reflected); L100 = SLEEPER RANK (LuckPerms commands in config).
- BOUND BED: `/sleeper bind` looking at a bed -> right-click = sleep only (NO respawn
  point, NO home creation; MAVOHomes may still see cancelled events - keep the bound bed
  outside your claim if you want zero home interaction). Bound-bed rest = +2 profession
  points to your other active professions (instead of the tavern +1).
- /grave costs: Player Level 1 = 1000 coins / 10 Lucky Coins; each Player Level doubles
  both (L2 = 2000/20, L3 = 4000/40 ... capped at 2^30). Costs shown in the confirm GUI.
