# MAVOcraft — UPDATE GUIDE (2026-09-05: MobFarm 2.7.3 — protection, hub/path save, /mobfarm current)

Plain steps. Only replace/delete files named here. Never delete plugin folders.

## 1. STOP the server (full stop, no /reload)

## 2. DELETE this file from plugins/ (jar only)

    MAVOMobFarm-2.7.2.jar        <- replace it with 2.7.3
    (2.6.x / older MobFarm jars if still there)

AuctionHouse 1.0.3 stays as it is.

## 3. UPLOAD this jar into plugins/

    MAVOMobFarm-2.7.3.jar    (whole-area protection r=50, /mobfarm savehub + buildhub,
                              /mobfarm current, packs-first per-mob flow as in 2.7.1)

MAVOProfessions-3.14.2.jar stays too.

## 4. KEEP all configs / data

- plugins/MAVOMobFarm/config.yml + data.yml: KEEP.
- world/datapacks/*-datapack.zip (zombie + hub etc.) and any you copied to your PC: KEEP.

## 5. START the server

## 6. Protect / regenerate (one pass, optional if already done)

    /updates reload
    /mobfarm purge
    /mobfarm clear            <- also wipes hub + footpaths (restore right after with buildhub)
    /mobfarm setcenter        (x2 - once per range, as before)
    /mobfarm build            (writes all 36 <mob>-datapack.zip; builds the hub platform ONLY
                               if no hub-datapack.zip exists - your saved hub/paths are never touched)

Boot log must show "MAVOMobFarm 2.7.3 enabled".

## 7. RESTART the server (once) - packs are scanned at STARTUP only

After it: /mobfarm build <mob> and /mobfarm buildhub can apply the packs.

## 8. Build each bay from its zip (one at a time)

    /mobfarm build zombie       <- only the zombie bay (clear -> 5s -> reload -> apply -> VERIFY)
    /mobfarm build husk         <- etc. (tab-complete shows all 36 ids)
    /mobfarm rebuild <mob>      <- alias of build <mob>
    /mobfarm zombie save        <- save YOUR current bay (walls/paths edits included) to its zip;
                                   copy the printed path to your PC as the version

## 9. Save the hub + footpaths (so you never redo them)

    /mobfarm savehub            <- snapshots the ENTIRE hub platform + every footpath you built
                                   (all 36 bay boxes are EXCLUDED - each bay has its own zip)
                                   into world/datapacks/hub-datapack.zip and prints the path.
                                   Re-run it whenever you finish a new path. Copy to your PC.
    /mobfarm buildhub           <- restores hub + paths from that zip (after a restart any time)
                                   - e.g. right after /mobfarm clear

## 10. Go back to your mob zone

    /mobfarm current            <- teleports you to the mob zone you bought this session
                                   (no second pick / no re-pay). /mobfarm enter first.

## 11. Protection

The whole farm area (hub + all 36 bays + the paths between them, plus 50 blocks around)
is now ALWAYS protected once the center is set - even right after /mobfarm build.
Normal players can: kill the farm mobs, open chests, deposit/remove items. They cannot
break/place blocks, fill buckets, or ignite. OP / owner accounts still bypass it so YOU
can keep building paths. Note: /deop does NOT remove LuckPerms group permissions - to
test protection as a normal player, remove mavomobfarm.admin + mavomobfarm.bypass.protect
from the OWNER group (or test with a second account).

## 12. Check in game

- Loot: stand in the trench, double chest in front at floor level, LOOT sign beside the pad.
- Kill pad: LIME_WOOL pad in the trench at z=+2 (HIT sign right, LOOT sign left).
- Mobs stay inside: 1-high slit + sill (land bays), glass window (water bays), tight
  containment AABB = pit/pen interior.
- /mobfarm current works during an active session.

## 13. NEVER delete (standing rule)

plugins/EconomyShopGUI/, plugins/LuckPerms/, plugins/TAB/, any MAVO plugin's data.yml /
homes.yml / config.yml not named above, world/datapacks/*-datapack.zip (incl. hub zip).
