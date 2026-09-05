# MAVOcraft — UPDATE GUIDE (2026-09-05: MobFarm 2.7.4 — saves are NEVER overwritten)

Plain steps. Only replace/delete files named here. Never delete plugin folders.

## 0. READ THIS FIRST (your footpath is safe - but ONLY after savehub)

- The jar does NOT contain your footpath. It only builds the bare hub platform and the
  36 pristine bay layouts. Your hub walls + footpaths exist ONLY in the world until you
  run `/mobfarm savehub` -> world/datapacks/hub-datapack.zip (then also copy it to your PC).
- `/mobfarm buildhub` WITHOUT a saved hub zip does nothing (no file = nothing to restore).
- Since you already built + saved the zombie bay and the path: DO NOT run `/mobfarm purge`
  or `/mobfarm clear` unless you have saved first. Steps 5-7 below are the safe order.

## 1. STOP the server (full stop, no /reload)

## 2. DELETE this file from plugins/ (jar only)

    MAVOMobFarm-2.7.3.jar        <- replace it with 2.7.4
    (2.6.x / older MobFarm jars if still there)

AuctionHouse 1.0.3 stays as it is.

## 3. UPLOAD this jar into plugins/

    MAVOMobFarm-2.7.4.jar    (2.7.3 fixes +: /mobfarm build KEEPS your saved zips,
                              /mobfarm build force = regenerate all, savehub covers the
                              whole protected area (AABB + 50), protection + current + hub
                              save/restore as in 2.7.2)

MAVOProfessions-3.14.2.jar stays too.

## 4. KEEP all configs / data

- plugins/MAVOMobFarm/config.yml + data.yml: KEEP.
- world/datapacks/*-datapack.zip (zombie etc.) and anything you copied to your PC: KEEP.

## 5. START the server

Boot log: "MAVOMobFarm 2.7.4 enabled" and NO loadState warnings.
Your world still contains the hub, footpath and zombie bay exactly as they are.

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
