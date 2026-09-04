# MAVOcraft — UPDATE GUIDE (2026-09-04: MobFarm 2.7.1 — packs first, per-bay apply)

Plain steps. Only replace/delete files named here. Never delete plugin folders.

## 1. STOP the server (full stop, no /reload)

## 2. DELETE this file from plugins/ (jar only)

    MAVOMobFarm-2.7.0.jar        <- replace it with 2.7.1
    (2.6.x / older MobFarm jars if still there)

AuctionHouse 1.0.3 stays as it is - no re-upload needed (keeper dedupe + terrace already in).

## 3. UPLOAD this jar into plugins/

    MAVOMobFarm-2.7.1.jar    (per-mob datapack flow: /mobfarm build (all packs + hub),
                              /mobfarm build <mob> (apply one bay from its zip),
                              /mobfarm <mob> save (backup/version the bay's current blocks))

MAVOProfessions-3.14.2.jar stays too.

## 4. KEEP all configs / data

- plugins/MAVOMobFarm/config.yml + data.yml: KEEP.
- plugins/MAVOAuctionHouse/data.yml: KEEP.
- world/datapacks/*-datapack.zip from any previous run: KEEP (2.7.0 zips are regenerated
  by step 6 anyway; any you copied off-server are your versions).

## 5. START the server

## 6. Generate the packs (one time)

    /updates reload
    /mobfarm purge
    /mobfarm clear
    /mobfarm setcenter        (x2 - once per range, as before)
    /mobfarm build            (writes all 36 <mob>-datapack.zip into world/datapacks/
                               AND builds the hub/HUD platform ONLY - no bay is built yet)

Boot log must show "MAVOMobFarm 2.7.1 enabled". The command prints the absolute
datapack folder path, e.g. /home/container/world/datapacks - the 36 zips MUST appear
there (check the file panel) before continuing.

## 7. RESTART the server (IMPORTANT, once)

The server only scans datapacks at STARTUP. After this restart the 36 packs are loaded
and /mobfarm build <mob> can use them. (Without the restart the build command will tell
you exactly this and nothing is falsely reported as built.)

## 8. Build each bay from its zip (one at a time)

    /mobfarm build zombie       <- ONLY the zombie bay: its zip's clear function wipes the
                                   old bay, waits 5s, disables/enables the pack, runs its
                                   build function, patches, and VERIFIES the result - the
                                   green "Rebuilt ... via zombie-datapack.zip" only appears
                                   when it really worked
    /mobfarm build husk         <- same for husk (or cave_spider, wither_skeleton, iron_golem,
                                   glow_squid ... - tab-complete shows all 36 ids)
    /mobfarm rebuild <mob>      <- alias of build <mob>

After each: check that mob's bay; the other 35 bays are NOT touched. If it says "Apply
FAILED ... RESTART once", the pack is not loaded (you skipped step 7).

## 9. Save your work (your versions / backups)

    /mobfarm zombie save        <- snapshots the zombie bay EXACTLY as it stands (YOUR
                                   edits included, spawner/hopper positions untouched) into
                                   world/datapacks/zombie-datapack.zip and prints the full
                                   path - copy that zip to your PC to keep the version.
                                   Next /mobfarm build zombie loads it back.

Do this after every redesign. One zip per mob = your version history. (Note: /mobfarm
build with NO argument regenerates ALL packs from the plugin's pristine layouts, so any
saved edits are replaced - save AFTER building, not before.)

## 10. Check in game

- Loot: stand in the trench, the double chest is right in front of you at floor level -
  click it directly (LOOT sign beside the pad, text faces you).
- Kill pad: LIME_WOOL pad in the TRENCH at z=+2 (HIT sign to its right, LOOT sign to its
  left, both visible). Land bays have a solid sill + 1-high slit: you hit mobs straight
  through it; no mob fits it or can jump it. Water bays keep their glass window (loot
  still flows to the chest - hopper-harvest bays).
- Mobs stay inside: tight containment box = pit/pen interior only; any escapee is
  teleported back to the kill pad.
- Structures differ: pyramid (husk), dome/igloo (stray), tower (skeleton), cage (pillager/
  spider), vault (creeper), obelisk (enderman), round basin (slime), caldera (magma cube),
  tank (squid/glow squid), fortress/bastion (hoglin/piglin), unique pens (all 14 animals).

## 11. NEVER delete (standing rule)

plugins/EconomyShopGUI/, plugins/LuckPerms/, plugins/TAB/, any MAVO plugin's data.yml /
homes.yml / config.yml not named above, world/datapacks/*-datapack.zip.
