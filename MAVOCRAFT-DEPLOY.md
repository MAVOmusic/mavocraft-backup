# MAVOcraft — UPDATE GUIDE (2026-09-04: MobFarm 2.7.0 — per-mob builds)

Plain steps. Only replace/delete files named here. Never delete plugin folders.

## 1. STOP the server (full stop, no /reload)

## 2. DELETE this file from plugins/ (jar only)

    MAVOMobFarm-2.6.6.jar        <- replace it with 2.7.0
    (2.6.5 / 2.6.4 / older MobFarm jars if still there)

AuctionHouse 1.0.3 stays as it is - no re-upload needed (keeper dedupe + terrace already in).

## 3. UPLOAD this jar into plugins/

    MAVOMobFarm-2.7.0.jar    (per-mob builds: /mobfarm build zombie, build husk, ... each mob
                              gets a DIFFERENT structure + its own datapack)

MAVOProfessions-3.14.2.jar stays too.

## 4. KEEP all configs / data

- plugins/MAVOMobFarm/config.yml + data.yml: KEEP.
- plugins/MAVOAuctionHouse/data.yml: KEEP.

## 5. START the server

## 6. First full build (one time)

    /updates reload
    /mobfarm purge
    /mobfarm clear
    /mobfarm setcenter        (x2 - once per range, as before)
    /mobfarm build            (builds hub + all 36 bays AND writes the 36 datapacks)

Boot log must show "MAVOMobFarm 2.7.0 enabled".

## 7. Iterate ONE bay at a time (the new way)

    /mobfarm build zombie       <- only the zombie bay: deletes its blocks (zombie-datapack.zip
                                   clear), waits 5s, reloads the pack, applies the new build
    /mobfarm build husk         <- same for husk (or cave_spider, wither_skeleton, iron_golem,
                                   glow_squid ... - tab-complete shows all 36 ids)
    /mobfarm rebuild <mob>      <- alias of build <mob>

After each: check that mob's bay; the other 35 bays are NOT touched.

Optional checks:
- world/datapacks/ now holds 36 zips: zombie-datapack.zip, husk-datapack.zip, ...
  (each has clear + build functions; you can also reload one manually with
   /datapack enable "file/zombie-datapack" after editing it)
- If a build ever fails to apply, the bay is still built (Java fallback) - just rerun
  /mobfarm build <mob>

## 8. Check in game

- Loot: stand in the trench, the double chest is right in front of you at floor level -
  click it directly (LOOT sign above it, text faces you).
- Mobs stay inside: 2-high window (bars/fence/pane/glass per bay) - hit through it.
- No more mystery ladder at bay corners.
- Structures differ: pyramid (husk), dome/igloo (stray), tower (skeleton), cage (pillager/
  spider), vault (creeper), obelisk (enderman), round basin (slime), caldera (magma cube),
  tank (squid/glow squid), fortress/bastion (hoglin/piglin), unique pens (all 14 animals).

## 9. NEVER delete (standing rule)

plugins/EconomyShopGUI/, plugins/LuckPerms/, plugins/TAB/, any MAVO plugin's data.yml /
homes.yml / config.yml not named above, world/datapacks/*-datapack.zip.
