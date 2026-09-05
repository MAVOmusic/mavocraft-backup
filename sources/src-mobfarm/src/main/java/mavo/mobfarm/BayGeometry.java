package mavo.mobfarm;

import mavo.mobfarm.MobFarm.MobDef;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.data.type.Slab;
import org.bukkit.entity.EntityType;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * MAVOMobFarm 2.7.5 - every one of the 36 mobs gets a HAND-BUILT layout with a different
 * structure (sunken crypt, pyramid, igloo, tower, cage, vault, obelisk, courtyard, basin,
 * caldera, maze, court, dome, tank, fortress, bastion + 14 unique animal pens).
 *
 * The KILL METHOD stays identical everywhere: hopper floor feeding ONE double loot chest
 * that stands ON the trench floor (front face flush - clickable, never covered). Land bays
 * get a SOLID sill + 1-high OPEN slit: you melee straight through it, but no 1.95-block
 * mob fits it and none can jump it (their feet stay below the sill line), so the bay is
 * sealed. Water bays keep the full see-through barrier so the water stays in. A tight
 * containment AABB (pit interior ONLY, set by pit()/pen()) teleports any escapee back to
 * the kill pad as a hard safety net.
 *
 * Every mob also owns its own datapack: world/datapacks/&lt;id&gt;-datapack.zip with
 * clear (fill bay box air) + build (exact block states).
 *   /mobfarm build          -> generates all 36 packs (Java build, snapshot to zip, then
 *                              clears the bay) + hub/HUD; packs load on server START.
 *   /mobfarm build &lt;mob&gt;   -> clears the old pack build, waits 5s, disables/enables the
 *                              pack, runs its build function, patches, and VERIFIES the
 *                              result before reporting success.
 *   /mobfarm &lt;mob&gt; save     -> snapshots the bay's CURRENT in-world blocks (your edits,
 *                              spawner/hopper positions untouched) into its zip - the
 *                              backup/version mechanism; build <mob> then loads it.
 */
final class BayGeometry {

    private BayGeometry() {}

    /* ------------------------------------------------ block helpers */

    private static void b(World w, int x, int y, int z, Material m) {
        if (m != null && m.isBlock())
            try { w.getBlockAt(x, y, z).setType(m, false); } catch (Throwable ignored) {}
    }

    private static void air(World w, int x, int y, int z) {
        try { w.getBlockAt(x, y, z).setType(Material.AIR, false); } catch (Throwable ignored) {}
    }

    /** fill relative to bay center. */
    private static void fill(World w, int cx, int cy, int cz, int x1, int x2, int y1, int y2, int z1, int z2, Material m) {
        for (int x = x1; x <= x2; x++)
            for (int y = y1; y <= y2; y++)
                for (int z = z1; z <= z2; z++)
                    b(w, cx + x, cy + y, cz + z, m);
    }

    private static void slab(World w, int x, int y, int z, Material m, boolean top) {
        Block bl = w.getBlockAt(x, y, z);
        bl.setType(m, false);
        try {
            Slab s = (Slab) bl.getBlockData();
            s.setType(top ? Slab.Type.TOP : Slab.Type.BOTTOM);
            bl.setBlockData(s, false);
        } catch (Throwable ignored) {}
    }

    private static void hopper(World w, int x, int y, int z, BlockFace face) {
        Block bl = w.getBlockAt(x, y, z);
        bl.setType(Material.HOPPER, false);
        try {
            org.bukkit.block.data.type.Hopper h = (org.bukkit.block.data.type.Hopper) bl.getBlockData();
            h.setFacing(face);
            bl.setBlockData(h, false);
        } catch (Throwable ignored) {}
    }

    /** circular disc (solid) around bay center. */
    private static void disc(World w, int cx, int cy, int cz, int r, int y1, int y2, Material m) {
        for (int x = -r; x <= r; x++)
            for (int z = -r; z <= r; z++)
                if (x * x + z * z <= r * r)
                    for (int y = y1; y <= y2; y++)
                        b(w, cx + x, cy + y, cz + z, m);
    }

    /** square ring wall (thickness 1) at radius r. */
    private static void ring(World w, int cx, int cy, int cz, int r, int y1, int y2, Material m,
                             int zMin, int zMax) {
        for (int x = -r; x <= r; x++)
            for (int z = zMin; z <= zMax; z++)
                if (Math.abs(x) == r || Math.abs(z) == r)
                    for (int y = y1; y <= y2; y++)
                        b(w, cx + x, cy + y, cz + z, m);
    }

    /** circular shell (thickness ~1) at radius r, restricted to zMin..zMax. */
    private static void ringC(World w, int cx, int cy, int cz, int r, int y1, int y2, Material m,
                              int zMin, int zMax) {
        for (int x = -r; x <= r; x++)
            for (int z = zMin; z <= zMax; z++) {
                int d2 = x * x + z * z;
                if (d2 > (r - 1) * (r - 1) && d2 <= r * r)
                    for (int y = y1; y <= y2; y++)
                        b(w, cx + x, cy + y, cz + z, m);
            }
    }

    private static Material wall(MobDef m) {
        return switch (m.theme) {
            case "nether" -> Material.NETHER_BRICKS;
            case "end" -> Material.OBSIDIAN;
            case "ocean" -> Material.PRISMARINE_BRICKS;
            case "ice" -> Material.PACKED_ICE;
            case "desert" -> Material.SANDSTONE;
            case "swamp" -> Material.MOSSY_COBBLESTONE;
            case "mine" -> Material.DEEPSLATE_BRICKS;
            case "raid" -> Material.DARK_OAK_LOG;
            case "village" -> Material.OAK_LOG;
            case "animal" -> Material.OAK_PLANKS;
            default -> Material.STONE_BRICKS;
        };
    }

    private static Material floor(MobDef m) {
        return switch (m.theme) {
            case "nether" -> Material.NETHERRACK;
            case "end" -> Material.END_STONE;
            case "ocean" -> Material.PRISMARINE;
            case "ice" -> Material.SNOW_BLOCK;
            case "desert" -> Material.SAND;
            case "swamp" -> Material.MUD;
            case "animal", "village" -> Material.GRASS_BLOCK;
            default -> Material.DEEPSLATE_TILES;
        };
    }

    private static Material ped(MobDef m) {
        return switch (m.style) {
            case "forge" -> Material.NETHER_BRICKS;
            case "barn", "pen", "village" -> Material.OAK_PLANKS;
            case "aqua", "water" -> Material.PRISMARINE;
            case "arena" -> Material.DEEPSLATE_BRICKS;
            case "web", "spider" -> Material.OAK_LOG;
            case "cells", "slime" -> Material.IRON_BLOCK;
            case "brutal" -> Material.POLISHED_BLACKSTONE;
            case "gallery" -> Material.QUARTZ_BLOCK;
            case "totem", "enderman", "bunker" -> Material.OBSIDIAN;
            default -> Material.MOSSY_STONE_BRICKS;
        };
    }

    /* ------------------------------------------------ the KILL METHOD (same for all) */

    /**
     * Hopper floor (z=-depth..0) -> ONE double loot chest resting ON the trench floor
     * at z=+1 (its front face is flush with the player's footing - always clickable),
     * solid sill + 1-high open slit + wall above (land bays) or full barrier window
     * (water bays), sunken trench + walkway.
     */
    private static void pit(MobFarm f, MobDef m, World w, int cx, int cy, int cz,
                            int depth, int halfW, int winW, Material barrier,
                            Material wallM, boolean water, int waterLevel, int top, boolean openTop) {
        // tight containment box = pit interior ONLY: mobs may never reach the trench/walkway
        m.cell = new double[]{cx - halfW + 0.3, cx + halfW - 0.3,
                cz - depth + 0.2, cz + 0.35,
                cy - 1.3, cy + top + (openTop ? 2.6 : -0.4)};
        m.pit = true;
        // hopper floor: outer columns feed the centre chain, centre feeds the chest halves
        for (int x = -halfW; x <= halfW; x++)
            for (int z = -depth; z <= 0; z++)
                hopper(w, cx + x, cy - 1, cz + z,
                        x < 0 ? BlockFace.EAST : x > 0 ? BlockFace.WEST : BlockFace.SOUTH);
        // trench floor FIRST (the chest replaces it at x=cx-1..cx, placed after so the
        // fill can never overwrite it) - chest face is flush with the player's footing
        fill(w, cx, cy, cz, -3, 3, -1, -1, 1, 3, Material.POLISHED_DEEPSLATE);
        f.placeDoubleChest(w, cx - 1, cy - 1, cz + 1, BlockFace.SOUTH);
        // pit walls (sides + back), from floor level up
        for (int y = -1; y <= top; y++) {
            for (int z = -depth - 1; z <= 1; z++) {
                b(w, cx - halfW - 1, cy + y, cz + z, wallM);
                b(w, cx + halfW + 1, cy + y, cz + z, wallM);
            }
            for (int x = -halfW - 1; x <= halfW + 1; x++)
                b(w, cx + x, cy + y, cz - depth - 1, wallM);
        }
        // front wall: solid sill at y=0, then a 1-high OPEN slit at y=1 for land bays -
        // you hit straight through it, but no mob fits it or can jump it (feet stay below
        // the sill line), so the bay stays sealed. Water bays keep the full 2-high
        // see-through barrier (glass/bars) so the water stays inside.
        for (int x = -halfW; x <= halfW; x++) {
            boolean win = Math.abs(x) <= winW;
            for (int y = 0; y <= top; y++) {
                boolean waterWindow = win && y <= 1 && water; // full barrier window
                boolean slit = win && y == 1 && !water;       // land bays: open slit
                if (slit) continue;
                b(w, cx + x, cy + y, cz + 1, waterWindow ? barrier : wallM);
            }
        }
        // water level inside aquatic bays (contained by full glass window)
        if (water && waterLevel > 0)
            for (int x = -halfW; x <= halfW; x++)
                for (int z = -depth; z <= 0; z++)
                    for (int y = 1; y <= waterLevel; y++)
                        b(w, cx + x, cy + y, cz + z, Material.WATER);
        // light-tight ceiling unless open-top
        if (!openTop)
            fill(w, cx, cy, cz, -halfW - 1, halfW + 1, top, top, -depth - 1, 0, wallM);
        // trench side walls
        for (int y = 0; y <= 2; y++) {
            b(w, cx - 3, cy + y, cz + 1, wallM);
            b(w, cx + 3, cy + y, cz + 1, wallM);
            b(w, cx - 3, cy + y, cz + 3, wallM);
            b(w, cx + 3, cy + y, cz + 3, wallM);
        }
        // walkway (sea-lantern checker) - players stand at feet cy+1
        for (int x = -3; x <= 3; x++)
            for (int z = 4; z <= 7; z++)
                b(w, cx + x, cy, cz + z, ((x + z) & 1) == 0 ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE);
    }

    /* ------------------------------------------------ animal pen (the barn KILL METHOD) */

    /**
     * Animal pen: the animal walks on an iron-bars grate; drops fall through onto the
     * hopper field below and feed ONE double chest at the front. Per-animal SHAPE comes
     * from half/fence height/material/roof mode which every builder sets differently.
     * roofMode: 0 none, 1 full, 2 back half, 3 corner pillars, 4 full + ridge line.
     */
    private static void pen(MobFarm f, MobDef m, World w, int cx, int cy, int cz, int half, int fenceH,
                            Material floorM, Material fenceM, int roofMode, boolean pond) {
        // tight containment box = pen interior only (animals never leave the grate area)
        m.cell = new double[]{cx - half + 0.3, cx + half - 0.3,
                cz - half + 0.3, cz + half - 0.3,
                cy + (pond ? 1.2 : 2.3), cy + 2 + fenceH + 1.8};
        m.pit = false;
        int penFloorY = cy + 2;   // grate level - animals walk here
        int edgeTop = penFloorY + fenceH;
        for (int x = -half; x <= half; x++)
            for (int z = -half; z <= half; z++) {
                b(w, cx + x, cy - 1, cz + z, floorM);
                air(w, cx + x, cy, cz + z);
                boolean open = Math.abs(x) < half && Math.abs(z) < half;
                if (pond) {
                    b(w, cx + x, penFloorY, cz + z, open ? Material.WATER : floorM);
                    if (open) b(w, cx + x, penFloorY - 1, cz + z, Material.IRON_BARS);
                } else {
                    b(w, cx + x, penFloorY, cz + z, open ? Material.IRON_BARS : floorM);
                }
                boolean edge = Math.abs(x) == half || Math.abs(z) == half;
                boolean corner = Math.abs(x) == half && Math.abs(z) == half;
                if (edge) {
                    for (int y = 0; y < fenceH; y++)
                        b(w, cx + x, penFloorY + 1 + y, cz + z, fenceM);
                    if (roofMode == 1 || (roofMode == 3 && corner) || roofMode == 4)
                        b(w, cx + x, edgeTop, cz + z, floorM);
                } else {
                    air(w, cx + x, penFloorY + 1, cz + z);
                    air(w, cx + x, penFloorY + 2, cz + z);
                    boolean back = z <= -1;
                    if (roofMode == 1) b(w, cx + x, edgeTop, cz + z, floorM);
                    else if (roofMode == 2 && back) b(w, cx + x, edgeTop, cz + z, floorM);
                    else if (roofMode == 4 && back) b(w, cx + x, edgeTop, cz + z, floorM);
                }
            }
        if (roofMode == 4) { // ridge line
            for (int x = -half + 1; x <= half - 1; x++)
                slab(w, cx + x, penFloorY + fenceH + 2, cz, floorM, false);
        }
        // hopper field under the grate feeding the ONE double chest
        for (int x = -half + 1; x <= half - 1; x++)
            for (int z = -half + 1; z <= half - 1; z++)
                hopper(w, cx + x, cy + 1, cz + z,
                        x > 0 ? BlockFace.WEST : x < 0 ? BlockFace.EAST : BlockFace.SOUTH);
        f.placeDoubleChest(w, cx - 1, cy + 1, cz + 3, BlockFace.SOUTH);
        for (int x = -1; x <= 0; x++)
            air(w, cx + x, cy + 2, cz + 3);
    }

    /* ------------------------------------------------ per-bay finish (same for all) */

    private static void finish(MobFarm f, MobDef m, World w, int cx, int cy, int cz,
                               double hx, double hz, double minY, double maxY, double padY) {
        if (m.cell == null) m.cell = new double[]{
                cx + 0.5 - hx, cx + 0.5 + hx, cz - 1 - hz, cz - 1 + hz, minY, maxY};
        // player stands in the TRENCH (z=+2.5) right at the slit: mobs are ~2 blocks away
        // (kill pad inside the pit) - in reach, out of the mobs' reach, sign in view.
        m.stand = new Location(w, cx + 0.5, cy, cz + 2.5);
        if (!m.pit) m.stand = new Location(w, cx + 0.5, cy + 1, cz + 6.5);
        m.killPad = new Location(w, cx + 0.5, cy + padY, cz - 1);
        m.lootChest = new Location(w, cx + 0.5, cy - 1, cz + 1.2);
        // stack spawner display east side with its own floor pad
        b(w, cx + 6, cy - 1, cz - 2, wall(m));
        b(w, cx + 6, cy, cz - 2, ped(m));
        b(w, cx + 6, cy + 1, cz - 2, Material.SPAWNER);
        m.stackBlock = new Location(w, cx + 6, cy + 1, cz - 2);
        Block sp = w.getBlockAt(cx + 6, cy + 1, cz - 2);
        if (sp.getState() instanceof CreatureSpawner cs) {
            try {
                cs.setSpawnedType(m.entity);
                cs.setDelay(Integer.MAX_VALUE / 4);
                try { cs.setSpawnCount(0); cs.setMinSpawnDelay(99999); cs.setMaxSpawnDelay(99999); } catch (Throwable ignored) {}
                cs.update(true, false);
            } catch (Throwable ignored) {}
        }
        // community chest west of the walkway entry, on its own pad, faces NORTH
        fill(w, cx, cy, cz, -6, -3, -1, -1, 5, 8, floor(m));
        f.placeDoubleChest(w, cx - 4, cy, cz + 6, BlockFace.NORTH);
        m.communityChest = new Location(w, cx - 4, cy, cz + 6);
        Block baySign = w.getBlockAt(cx - 4, cy + 1, cz + 6);
        baySign.setType(Material.OAK_SIGN, false);
        f.faceSign(baySign, BlockFace.NORTH);
        f.writeSign(baySign, ChatColor.GOLD + "COMMUNITY", ChatColor.stripColor(m.display),
                ChatColor.WHITE + "Donate loot", ChatColor.GRAY + "→ stack");
        // zone plate on the walkway edge (arrival marker), hit pad in the trench
        Block name = w.getBlockAt(cx, cy + 1, cz + 7);
        name.setType(Material.OAK_SIGN, false);
        f.faceSign(name, BlockFace.NORTH);
        f.writeSign(name, ChatColor.GOLD + "ZONE", ChatColor.stripColor(m.display),
                ChatColor.GRAY + m.style + "/" + m.theme, ChatColor.AQUA + "HIT ▶");
        // kill pad on the trench floor in front of the slit, flanked by LOOT + HIT signs
        Block hitpad = w.getBlockAt(cx, cy - 1, cz + 2);
        hitpad.setType(Material.LIME_WOOL, false);
        Block hitSgn = w.getBlockAt(cx + 2, cy, cz + 2);
        hitSgn.setType(Material.OAK_SIGN, false);
        f.faceSign(hitSgn, BlockFace.WEST);
        f.writeSign(hitSgn, ChatColor.GREEN + "HIT", ChatColor.WHITE + "HERE",
                ChatColor.GRAY + "stand ↑", ChatColor.YELLOW + "/mobfarm pick");
        Block lootSign = w.getBlockAt(cx - 2, cy, cz + 2);
        lootSign.setType(Material.OAK_SIGN, false);
        f.faceSign(lootSign, BlockFace.EAST);
        f.writeSign(lootSign, ChatColor.GOLD + "LOOT", ChatColor.WHITE + "↓ chest",
                ChatColor.GRAY + "hopper fed", ChatColor.YELLOW + "collect ↓");
    }

    /** re-apply things a setblock snapshot cannot carry: chest pairing, spawner, sign text. */
    private static void patch(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        if (m.pit) f.placeDoubleChest(w, cx - 1, cy - 1, cz + 1, BlockFace.SOUTH);
        else f.placeDoubleChest(w, cx - 1, cy + 1, cz + 3, BlockFace.SOUTH);
        f.placeDoubleChest(w, cx - 4, cy, cz + 6, BlockFace.NORTH);
        Block sp = w.getBlockAt(cx + 6, cy + 1, cz - 2);
        if (sp.getState() instanceof CreatureSpawner cs) {
            try {
                cs.setSpawnedType(m.entity);
                cs.setDelay(Integer.MAX_VALUE / 4);
                try { cs.setSpawnCount(0); cs.setMinSpawnDelay(99999); cs.setMaxSpawnDelay(99999); } catch (Throwable ignored) {}
                cs.update(true, false);
            } catch (Throwable ignored) {}
        }
        Block baySign = w.getBlockAt(cx - 4, cy + 1, cz + 6);
        f.writeSign(baySign, ChatColor.GOLD + "COMMUNITY", ChatColor.stripColor(m.display),
                ChatColor.WHITE + "Donate loot", ChatColor.GRAY + "→ stack");
        Block name = w.getBlockAt(cx, cy + 1, cz + 7);
        f.writeSign(name, ChatColor.GOLD + "ZONE", ChatColor.stripColor(m.display),
                ChatColor.GRAY + m.style + "/" + m.theme, ChatColor.AQUA + "HIT ▶");
        w.getBlockAt(cx, cy - 1, cz + 2).setType(Material.LIME_WOOL, false);
        Block hitSgn = w.getBlockAt(cx + 2, cy, cz + 2);
        f.writeSign(hitSgn, ChatColor.GREEN + "HIT", ChatColor.WHITE + "HERE",
                ChatColor.GRAY + "stand ↑", ChatColor.YELLOW + "/mobfarm pick");
        Block lootSign = w.getBlockAt(cx - 2, cy, cz + 2);
        f.writeSign(lootSign, ChatColor.GOLD + "LOOT", ChatColor.WHITE + "↓ chest",
                ChatColor.GRAY + "hopper fed", ChatColor.YELLOW + "collect ↓");
    }

    /* ------------------------------------------------ per-mob datapacks (2.7.0) */

    /** world/datapacks/ (the REAL world folder; the absolute path is always printed). */
    static File packDir(World w) {
        File d = new File(w.getWorldFolder(), "datapacks");
        if (!d.exists()) d.mkdirs();
        return d;
    }

    private static File packFile(World w, String id) {
        return new File(packDir(w), id + "-datapack.zip");
    }

    /**
     * Applies a setblock-only pack function DIRECTLY in Java (no live datapack
     * registration needed). Pack files placed while the server is running are only
     * scanned at STARTUP, so the old datapack-command path could fail with
     * "Unknown data pack" even though the zip sits in the datapacks folder - exactly
     * what happened to /mobfarm buildhub on Paper 26.2. Both the hub zip and every
     * bay zip carry pure "setblock x y z <state>" lines, so applying them here is
     * byte-identical to running the function. Returns blocks applied, or -1 on error.
     */
    private static int applyPackFunction(MobFarm f, World w, File zf, String entry) {
        if (zf == null || !zf.isFile()) return -1;
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zf)) {
            ZipEntry e = z.getEntry(entry);
            if (e == null) {
                f.getLogger().warning("MAVOMobFarm: " + zf.getName() + " has no " + entry);
                return -1;
            }
            int applied = 0;
            try (BufferedReader r = new BufferedReader(new java.io.InputStreamReader(
                    z.getInputStream(e), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    line = line.trim();
                    if (line.isEmpty() || line.startsWith("#")) continue;
                    String[] p = line.split(" ");
                    if (p.length < 5 || !"setblock".equals(p[0])) continue;
                    int x, y, zz;
                    try {
                        x = Integer.parseInt(p[1]); y = Integer.parseInt(p[2]); zz = Integer.parseInt(p[3]);
                    } catch (NumberFormatException ignore) { continue; }
                    StringBuilder state = new StringBuilder(p[4]);
                    for (int i = 5; i < p.length; i++) state.append(' ').append(p[i]);
                    try {
                        w.getBlockAt(x, y, zz).setBlockData(
                                Bukkit.createBlockData(state.toString()), false);
                        applied++;
                    } catch (Exception ignore) { }
                }
            }
            return applied;
        } catch (Throwable t) {
            f.getLogger().warning("MAVOMobFarm: apply " + entry + " FAILED: " + t);
            return -1;
        }
    }

    /** Same fill the pack's clear function does (bay box -> air), via Java. */
    private static void clearBayBox(World w, int cx, int cy, int cz) {
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 10; z++)
                for (int y = -10; y <= 16; y++)
                    air(w, cx + x, cy + y, cz + z);
    }

    private static void loadBayChunks(World w, int cx, int cy, int cz) {
        for (int x = cx - 12; x <= cx + 12; x += 16)
            for (int z = cz - 12; z <= cz + 10; z += 16) {
                try { w.getChunkAt(x >> 4, z >> 4).load(); } catch (Throwable ignored) {}
            }
    }

    private static List<String> snapshot(MobFarm f, World w, int cx, int cy, int cz) {
        loadBayChunks(w, cx, cy, cz);
        List<String> out = new ArrayList<>();
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 10; z++)
                for (int y = -10; y <= 16; y++) {
                    Block bl = w.getBlockAt(cx + x, cy + y, cz + z);
                    Material t = bl.getType();
                    if (t == Material.AIR || t == Material.CAVE_AIR || t == Material.VOID_AIR) continue;
                    out.add("setblock " + bl.getX() + " " + bl.getY() + " " + bl.getZ()
                            + " " + bl.getBlockData().getAsString());
                }
        return out;
    }

    private static String nums(double... v) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < v.length; i++) {
            if (i > 0) sb.append(',');
            sb.append((float) v[i]);
        }
        return sb.toString();
    }

    /** Writes the bay's CURRENT blocks (world state, user edits included) into its zip
     *  plus state.txt (pit flag, stand/kill-pad/cell relative to the bay origin), so the
     *  pack is fully self-describing. Never silently fails: null + log on error. */
    private static File writePack(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        try {
            List<String> snap = snapshot(f, w, cx, cy, cz);
            File zf = packFile(w, m.id);
            File tmp = new File(zf.getParentFile(), zf.getName() + ".tmp");
            try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(tmp))) {
                zo.putNextEntry(new ZipEntry("pack.mcmeta"));
                // Paper 26.2 rejects ranges and old formats: pack_format 81, exact, like the
                // known-good MAVOcraft-builder-datapack.zip (ranges error on this server).
                zo.write(("{\"pack\":{\"pack_format\":81,\"description\":\"MAVOMobFarm " + m.id + " bay builder\"}}")
                        .getBytes(StandardCharsets.UTF_8));
                zo.closeEntry();
                // self-describing geometry state (relative to the bay origin cx,cy,cz)
                StringBuilder st = new StringBuilder("pit=" + m.pit + "\n");
                if (m.stand != null) st.append("stand=").append(nums(m.stand.getX() - cx, m.stand.getY() - cy, m.stand.getZ() - cz)).append('\n');
                if (m.killPad != null) st.append("killpad=").append(nums(m.killPad.getX() - cx, m.killPad.getY() - cy, m.killPad.getZ() - cz)).append('\n');
                if (m.stackBlock != null) st.append("stack=").append(nums(m.stackBlock.getX() - cx, m.stackBlock.getY() - cy, m.stackBlock.getZ() - cz)).append('\n');
                if (m.lootChest != null) st.append("loot=").append(nums(m.lootChest.getX() - cx, m.lootChest.getY() - cy, m.lootChest.getZ() - cz)).append('\n');
                if (m.communityChest != null) st.append("community=").append(nums(m.communityChest.getX() - cx, m.communityChest.getY() - cy, m.communityChest.getZ() - cz)).append('\n');
                if (m.cell != null) st.append("cell=").append(nums(
                        m.cell[0] - cx, m.cell[1] - cx, m.cell[2] - cz, m.cell[3] - cz, m.cell[4] - cy, m.cell[5] - cy)).append('\n');
                zo.putNextEntry(new ZipEntry("state.txt"));
                zo.write(st.toString().getBytes(StandardCharsets.UTF_8));
                zo.closeEntry();
                zo.putNextEntry(new ZipEntry("data/mavomobfarm/function/" + m.id + "/clear.mcfunction"));
                zo.write(("fill " + (cx - 12) + " " + (cy - 10) + " " + (cz - 12) + " "
                        + (cx + 12) + " " + (cy + 16) + " " + (cz + 10) + " air").getBytes(StandardCharsets.UTF_8));
                zo.closeEntry();
                zo.putNextEntry(new ZipEntry("data/mavomobfarm/function/" + m.id + "/build.mcfunction"));
                for (String line : snap) {
                    zo.write(line.getBytes(StandardCharsets.UTF_8));
                    zo.write('\n');
                }
                zo.closeEntry();
            }
            if (!zf.getParentFile().isDirectory() && !zf.getParentFile().mkdirs())
                throw new java.io.IOException("cannot create " + zf.getParentFile().getAbsolutePath());
            if (zf.exists() && !zf.delete())
                throw new java.io.IOException("cannot replace " + zf.getAbsolutePath());
            if (!tmp.renameTo(zf))
                throw new java.io.IOException("cannot rename to " + zf.getAbsolutePath());
            if (!zf.isFile() || zf.length() == 0)
                throw new java.io.IOException("zip missing/empty after write: " + zf.getAbsolutePath());
            f.getLogger().info("MAVOMobFarm: wrote " + zf.getAbsolutePath() + " (" + snap.size() + " blocks)");
            return zf;
        } catch (Throwable t) {
            f.getLogger().warning("MAVOMobFarm: writePack " + m.id + " FAILED: " + t);
            return null;
        }
    }

    /** Restores m.pit/stand/killPad/cell/... from the pack's state.txt (relative -> absolute). */
    static boolean loadState(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        File zf = packFile(w, m.id);
        try (java.util.zip.ZipFile z = new java.util.zip.ZipFile(zf)) {
            java.util.zip.ZipEntry e = z.getEntry("state.txt");
            if (e == null) return false;
            try (BufferedReader r = new BufferedReader(new java.io.InputStreamReader(
                    z.getInputStream(e), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) {
                    int eq = line.indexOf('=');
                    if (eq < 0) continue;
                    String k = line.substring(0, eq);
                    String val = line.substring(eq + 1);
                    try {
                        if (k.equals("pit")) { // boolean, NOT a number (2.7.2 bug: parsed as double)
                            m.pit = Boolean.parseBoolean(val.trim());
                            continue;
                        }
                        String[] v = val.split(",");
                        double[] d = new double[v.length];
                        for (int i = 0; i < v.length; i++) d[i] = Double.parseDouble(v[i]);
                        switch (k) {
                            case "stand" -> m.stand = new Location(w, cx + d[0], cy + d[1], cz + d[2]);
                            case "killpad" -> m.killPad = new Location(w, cx + d[0], cy + d[1], cz + d[2]);
                            case "stack" -> m.stackBlock = new Location(w, cx + d[0], cy + d[1], cz + d[2]);
                            case "loot" -> m.lootChest = new Location(w, cx + d[0], cy + d[1], cz + d[2]);
                            case "community" -> m.communityChest = new Location(w, cx + d[0], cy + d[1], cz + d[2]);
                            case "cell" -> m.cell = new double[]{cx + d[0], cx + d[1], cz + d[2], cz + d[3], cy + d[4], cy + d[5]};
                        }
                    } catch (Throwable t) {
                        f.getLogger().warning("MAVOMobFarm: loadState " + m.id + ": skipped bad line '"
                                + line + "' (" + t.getMessage() + ")");
                    }
                }
            }
            return m.cell != null;
        } catch (Throwable t) {
            f.getLogger().warning("MAVOMobFarm: loadState " + m.id + " failed: " + t);
            return false;
        }
    }

    /** True when the mob's datapack already exists on disk. */
    static boolean wasBuilt(MobFarm f, MobDef m) {
        if (f.center == null) return false;
        File zf = packFile(f.center.getWorld(), m.id);
        return zf.isFile() && zf.length() > 0;
    }

    /** The bay is REALLY applied only when its signature blocks came back. */
    private static boolean verifyBuilt(World w, int cx, int cy, int cz, MobDef m) {
        boolean sp = w.getBlockAt(cx + 6, cy + 1, cz - 2).getType() == Material.SPAWNER;
        if (m.pit)
            return sp && w.getBlockAt(cx, cy - 1, cz).getType() == Material.HOPPER
                    && w.getBlockAt(cx, cy - 1, cz + 2).getType() == Material.LIME_WOOL;
        return sp && w.getBlockAt(cx, cy + 1, cz).getType() == Material.HOPPER
                && w.getBlockAt(cx - 1, cy + 1, cz + 3).getType() == Material.CHEST;
    }

    /** Replaces the bay in the world by the pack's build lines + patch, then VERIFIES.
     *  Geometry state (pit/cell/stand...) is restored from the pack's state.txt; the
     *  FINAL blocks are whatever the pack's build lines set (user saves win). The
     *  build lines are applied in Java - no live datapack registration required. */
    private static boolean applyPack(MobFarm f, MobDef m) {
        if (f.center == null) return false;
        World w = f.center.getWorld();
        Location o = f.center.clone().add(m.ox, m.oy, m.oz);
        int cx = o.getBlockX(), cy = o.getBlockY(), cz = o.getBlockZ();
        if (!loadState(f, m, w, cx, cy, cz)) {
            // pre-2.7.1 pack without state.txt: derive state via one Java build (result is
            // still fully overwritten by the pack build lines below; leaves state correct)
            build(f, m);
        }
        clearBayBox(w, cx, cy, cz);
        loadBayChunks(w, cx, cy, cz);
        int applied = applyPackFunction(f, w, packFile(w, m.id),
                "data/mavomobfarm/function/" + m.id + "/build.mcfunction");
        if (applied < 0) return false;
        patch(f, m, w, cx, cy, cz);
        return verifyBuilt(w, cx, cy, cz, m);
    }

    /** Java-builds the bay, snapshots it into its zip, then clears the bay again:
     *  /mobfarm build (no arg) ONLY generates all packs + hub - nothing is applied here. */
    static boolean generatePack(MobFarm f, MobDef m) {
        if (f.center == null) return false;
        World w = f.center.getWorld();
        Location o = f.center.clone().add(m.ox, m.oy, m.oz);
        int cx = o.getBlockX(), cy = o.getBlockY(), cz = o.getBlockZ();
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 10; z++)
                for (int y = -10; y <= 16; y++)
                    air(w, cx + x, cy + y, cz + z);
        build(f, m);
        File zf = writePack(f, m, w, cx, cy, cz);
        // leave NO per-bay build behind: only the packs + hub exist after /mobfarm build
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 10; z++)
                for (int y = -10; y <= 16; y++)
                    air(w, cx + x, cy + y, cz + z);
        return zf != null;
    }

    /** /mobfarm build <mob>: clear the old pack build, wait 5s, reload + apply the zip.
     *  If no zip exists yet, it is generated first. Success is reported only after the
     *  build function really ran (verified by signature blocks). */
    static void buildMob(MobFarm f, MobDef m, PlayerRef user) {
        if (f.center == null) { user.msg(ChatColor.RED + "Set center first."); return; }
        World w = f.center.getWorld();
        Location o = f.center.clone().add(m.ox, m.oy, m.oz);
        int cx = o.getBlockX(), cy = o.getBlockY(), cz = o.getBlockZ();
        if (!wasBuilt(f, m)) {
            if (!generatePack(f, m)) {
                user.msg(ChatColor.RED + "FAILED to write " + m.id + "-datapack.zip (see server log).");
                return;
            }
            File zf = packFile(w, m.id);
            if (applyPack(f, m)) {
                f.markBuilt();
                user.msg(ChatColor.GREEN + "Built " + ChatColor.stripColor(m.display) + " bay from "
                        + zf.getAbsolutePath() + ".");
            } else {
                user.msg(ChatColor.RED + "Zip written: " + zf.getAbsolutePath()
                        + " but applying it FAILED (pack read/verify error - see server log).");
            }
            return;
        }
        // pack exists on disk: rebuild from the zip directly (Java apply - no datapack
        // reload, no restart, old build is cleared inside applyPack)
        user.msg(ChatColor.YELLOW + "Clearing " + m.id + " bay and applying "
                + m.id + "-datapack.zip...");
        if (applyPack(f, m)) {
            f.markBuilt();
            user.msg(ChatColor.GREEN + "Rebuilt " + ChatColor.stripColor(m.display) + " bay via "
                    + m.id + "-datapack.zip.");
        } else {
            user.msg(ChatColor.RED + "Apply FAILED for " + m.id + " - pack read/verify error "
                    + "(see server log).");
        }
    }

    /** /mobfarm <mob> save: snapshot the bay EXACTLY as it stands in the world - the
     *  player's own edits included, spawner/hopper positions untouched - into its zip.
     *  That zip is the backup/version: copy it to your PC, /mobfarm build <mob> loads it. */
    static void saveBay(MobFarm f, MobDef m, PlayerRef user) {
        if (f.center == null) { user.msg(ChatColor.RED + "Set center first."); return; }
        World w = f.center.getWorld();
        Location o = f.center.clone().add(m.ox, m.oy, m.oz);
        int cx = o.getBlockX(), cy = o.getBlockY(), cz = o.getBlockZ();
        // if this session never built the bay, refresh state from the existing zip first
        if (m.cell == null && wasBuilt(f, m)) loadState(f, m, w, cx, cy, cz);
        File zf = writePack(f, m, w, cx, cy, cz);
        if (zf == null) {
            user.msg(ChatColor.RED + "Save FAILED for " + m.id + " (see server log).");
            return;
        }
        user.msg(ChatColor.GREEN + "Saved " + ChatColor.stripColor(m.display) + " bay -> "
                + zf.getAbsolutePath());
        user.msg(ChatColor.GRAY + "Copy that zip to your PC to keep this version. "
                + "/mobfarm build " + m.id + " loads it back into the world.");
    }

    /* ------------------------------------------------ hub + footpath datapack (2.7.5) */

    static File hubPackFile(World w) {
        return new File(packDir(w), "hub-datapack.zip");
    }

    static boolean hasHubPack(World w) {
        File f = hubPackFile(w);
        return f.isFile() && f.length() > 0;
    }

    /** True when (x,z) falls inside any bay's clear box (those blocks belong to the
     *  per-mob zips; the hub pack must never duplicate or clear them). */
    private static boolean inBayBox(List<int[]> bays, int x, int z) {
        for (int[] b : bays)
            if (x >= b[0] && x <= b[1] && z >= b[2] && z <= b[3]) return true;
        return false;
    }

    /** /mobfarm savehub: snapshots the ENTIRE farm footprint (hub platform + every
     *  footpath you built) MINUS the 36 bay boxes into world/datapacks/hub-datapack.zip.
     *  build.mcfunction = setblock lines ONLY (no clear - it must never wipe a bay). */
    static boolean writeHubPack(MobFarm f, World w, int minX, int maxX, int minZ, int maxZ,
                                int minY, int maxY, List<int[]> bays) {
        try {
            for (int x = minX; x <= maxX; x += 16)
                for (int z = minZ; z <= maxZ; z += 16) {
                    try { w.getChunkAt(x >> 4, z >> 4).load(); } catch (Throwable ignored) {}
                }
            List<String> out = new ArrayList<>();
            for (int x = minX; x <= maxX; x++) {
                for (int z = minZ; z <= maxZ; z++) {
                    if (inBayBox(bays, x, z)) continue;
                    for (int y = minY; y <= maxY; y++) {
                        Block bl = w.getBlockAt(x, y, z);
                        Material t = bl.getType();
                        if (t == Material.AIR || t == Material.CAVE_AIR || t == Material.VOID_AIR) continue;
                        out.add("setblock " + bl.getX() + " " + bl.getY() + " " + bl.getZ()
                                + " " + bl.getBlockData().getAsString());
                    }
                }
            }
            File zf = hubPackFile(w);
            File tmp = new File(zf.getParentFile(), zf.getName() + ".tmp");
            try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(tmp))) {
                zo.putNextEntry(new ZipEntry("pack.mcmeta"));
                zo.write(("{\"pack\":{\"pack_format\":81,\"description\":\"MAVOMobFarm hub + footpaths\"}}")
                        .getBytes(StandardCharsets.UTF_8));
                zo.closeEntry();
                zo.putNextEntry(new ZipEntry("data/mavomobfarm/function/hub/build.mcfunction"));
                for (String line : out) {
                    zo.write(line.getBytes(StandardCharsets.UTF_8));
                    zo.write('\n');
                }
                zo.closeEntry();
            }
            if (zf.exists() && !zf.delete())
                throw new java.io.IOException("cannot replace " + zf.getAbsolutePath());
            if (!tmp.renameTo(zf))
                throw new java.io.IOException("cannot rename to " + zf.getAbsolutePath());
            if (!zf.isFile() || zf.length() == 0)
                throw new java.io.IOException("hub zip missing/empty after write");
            f.getLogger().info("MAVOMobFarm: wrote " + zf.getAbsolutePath() + " (" + out.size() + " blocks)");
            return true;
        } catch (Throwable t) {
            f.getLogger().warning("MAVOMobFarm: writeHubPack FAILED: " + t);
            return false;
        }
    }

    /** /mobfarm buildhub: applies the saved hub+paths zip DIRECTLY in Java (no live
     *  datapack registration, no restart needed after savehub - the server only scans
     *  the datapacks folder at STARTUP, which is why the old function path failed with
     *  "Unknown data pack 'file/hub-datapack'"), re-pairs the hub chest/sign, verifies. */
    static void buildHub(MobFarm f, PlayerRef user) {
        if (f.center == null) { user.msg(ChatColor.RED + "Set center first."); return; }
        World w = f.center.getWorld();
        if (!hasHubPack(w)) {
            user.msg(ChatColor.RED + "No hub-datapack.zip yet. /mobfarm savehub writes it first.");
            return;
        }
        int hx = f.center.getBlockX(), hy = f.center.getBlockY(), hz = f.center.getBlockZ();
        // the hub pack covers the WHOLE farm footprint: load every chunk in the AABB
        f.loadFarmChunks(w);
        int applied = applyPackFunction(f, w, hubPackFile(w),
                "data/mavomobfarm/function/hub/build.mcfunction");
        if (applied < 0) {
            user.msg(ChatColor.RED + "Apply FAILED: hub-datapack.zip could not be read "
                    + "(see server log).");
            return;
        }
        f.patchHub(w, hx, hy, hz);
        boolean ok = w.getBlockAt(hx, hy, hz + 4).getType() == Material.CHEST
                && w.getBlockAt(hx + 1, hy, hz + 4).getType() == Material.CHEST
                && w.getBlockAt(hx, hy - 1, hz).getType() == Material.SEA_LANTERN;
        if (ok) {
            f.markBuilt();
            user.msg(ChatColor.GREEN + "Hub + footpaths restored from "
                    + hubPackFile(w).getAbsolutePath() + " (" + applied + " blocks).");
        } else {
            user.msg(ChatColor.RED + "Apply FAILED: hub verify failed - signature blocks missing "
                    + "(restore the hub pack from an older save or re-run /mobfarm savehub).");
        }
    }

    /** Simple sender wrapper so BayGeometry can message players / console without a Bukkit dep. */
    interface PlayerRef {
        void msg(String s);
    }

    /** main entry: build one bay (Java). */
    static void build(MobFarm f, MobDef m) {
        if (f.center == null) return;
        World w = f.center.getWorld();
        Location o = f.center.clone().add(m.ox, m.oy, m.oz);
        int cx = o.getBlockX(), cy = o.getBlockY(), cz = o.getBlockZ();
        switch (m.entity) {
            // ---------------- HOSTILES: 22 different structures ----------------
            case ZOMBIE -> bZombie(f, m, w, cx, cy, cz);
            case HUSK -> bHusk(f, m, w, cx, cy, cz);
            case DROWNED -> bDrowned(f, m, w, cx, cy, cz);
            case WITCH -> bWitch(f, m, w, cx, cy, cz);
            case SKELETON -> bSkeleton(f, m, w, cx, cy, cz);
            case STRAY -> bStray(f, m, w, cx, cy, cz);
            case WITHER_SKELETON -> bWitherSkeleton(f, m, w, cx, cy, cz);
            case PILLAGER -> bPillager(f, m, w, cx, cy, cz);
            case SPIDER -> bSpider(f, m, w, cx, cy, cz);
            case CAVE_SPIDER -> bCaveSpider(f, m, w, cx, cy, cz);
            case CREEPER -> bCreeper(f, m, w, cx, cy, cz);
            case ENDERMAN -> bEnderman(f, m, w, cx, cy, cz);
            case BLAZE -> bBlaze(f, m, w, cx, cy, cz);
            case SLIME -> bSlime(f, m, w, cx, cy, cz);
            case MAGMA_CUBE -> bMagmaCube(f, m, w, cx, cy, cz);
            case SILVERFISH -> bSilverfish(f, m, w, cx, cy, cz);
            case PHANTOM -> bPhantom(f, m, w, cx, cy, cz);
            case GUARDIAN -> bGuardian(f, m, w, cx, cy, cz);
            case SQUID -> bSquid(f, m, w, cx, cy, cz);
            case GLOW_SQUID -> bGlowSquid(f, m, w, cx, cy, cz);
            case HOGLIN -> bHoglin(f, m, w, cx, cy, cz);
            case PIGLIN -> bPiglin(f, m, w, cx, cy, cz);
            // ---------------- ANIMALS: 14 unique pens ----------------
            case COW -> bCow(f, m, w, cx, cy, cz);
            case PIG -> bPig(f, m, w, cx, cy, cz);
            case CHICKEN -> bChicken(f, m, w, cx, cy, cz);
            case SHEEP -> bSheep(f, m, w, cx, cy, cz);
            case RABBIT -> bRabbit(f, m, w, cx, cy, cz);
            case VILLAGER -> bVillager(f, m, w, cx, cy, cz);
            case IRON_GOLEM -> bIronGolem(f, m, w, cx, cy, cz);
            case BEE -> bBee(f, m, w, cx, cy, cz);
            case FOX -> bFox(f, m, w, cx, cy, cz);
            case GOAT -> bGoat(f, m, w, cx, cy, cz);
            case LLAMA -> bLlama(f, m, w, cx, cy, cz);
            case PANDA -> bPanda(f, m, w, cx, cy, cz);
            case FROG -> bFrog(f, m, w, cx, cy, cz);
            case SNIFFER -> bSniffer(f, m, w, cx, cy, cz);
            default -> bZombie(f, m, w, cx, cy, cz);
        }
    }

    /* ================= 22 HOSTILE LAYOUTS - each a different structure ================= */

    /** ZOMBIE - SUNKEN CRYPT: octagonal mossy pit, arch front, 1-wide window. */
    private static void bZombie(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.MOSSY_COBBLESTONE;
        disc(w, cx, cy, cz, 3, -1, -1, Material.DEEPSLATE_TILES);
        pit(f, m, w, cx, cy, cz, 3, 1, 1, Material.IRON_BARS, wallm, false, 0, 4, false);
        ringC(w, cx, cy, cz, 3, 0, 1, wallm, -4, 1);
        ringC(w, cx, cy, cz, 3, 2, 3, Material.MOSS_BLOCK, -4, -1);
        for (int y = 0; y <= 3; y++) b(w, cx - 1, cy + y, cz - 3, Material.MOSSY_STONE_BRICKS);
        b(w, cx, cy + 3, cz - 2, Material.SPAWNER);
        w.getBlockAt(cx, cy + 3, cz - 2).setType(Material.AIR, false);
        finish(f, m, w, cx, cy, cz, 3.4, 4.6, cy - 1.6, cy + 3.8, -0.9);
    }

    /** HUSK - DESERT PYRAMID: 3-tier sandstone pyramid with a shaft down to the kill pit. */
    private static void bHusk(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.SANDSTONE;
        fill(w, cx, cy, cz, -3, 3, -1, -1, -3, 0, Material.SAND);
        pit(f, m, w, cx, cy, cz, 3, 1, 1, Material.IRON_BARS, wallm, false, 0, 2, true);
        // solid tiers around the 1-wide x 4-deep shaft (never inside the pit)
        for (int y = 0; y <= 1; y++)
            for (int x = -3; x <= 3; x++)
                for (int z = -4; z <= 0; z++) {
                    boolean shaft = Math.abs(x) <= 1 && z >= -3;
                    if (!shaft) b(w, cx + x, cy + y, cz + z, Material.SANDSTONE);
                }
        for (int x = -2; x <= 2; x++)
            for (int z = -3; z <= -1; z++)
                if (!(Math.abs(x) <= 1 && z >= -3))
                    b(w, cx + x, cy + 2, cz + z, Material.SANDSTONE_SLAB);
        b(w, cx, cy + 3, cz - 1, Material.GOLD_BLOCK);
        b(w, cx - 3, cy + 1, cz - 1, Material.CACTUS);
        b(w, cx + 3, cy + 1, cz - 1, Material.CACTUS);
        b(w, cx - 2, cy + 1, cz + 1, Material.CUT_SANDSTONE);
        b(w, cx + 2, cy + 1, cz + 1, Material.CUT_SANDSTONE);
        finish(f, m, w, cx, cy, cz, 3.6, 4.6, cy - 1.6, cy + 3.8, -0.9);
    }

    /** DROWNED - FLOODED SHRINE: sunken prismarine shrine, water 2 deep, glass window. */
    private static void bDrowned(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.PRISMARINE_BRICKS;
        disc(w, cx, cy, cz, 4, -1, -1, Material.PRISMARINE);
        pit(f, m, w, cx, cy, cz, 3, 1, 1, Material.GLASS, wallm, true, 2, 4, false);
        ring(w, cx, cy, cz, 2, 0, 4, wallm, -4, 0);
        for (int x = -2; x <= 2; x++) b(w, cx + x, cy + 4, cz - 4, wallm);
        for (int x = -2; x <= 2; x++) b(w, cx + x, cy + 5, cz - 4, Material.SEA_LANTERN);
        for (int x = -2; x <= 2; x++) b(w, cx + x, cy + 4, cz - 2, Material.PRISMARINE);
        b(w, cx - 2, cy + 1, cz + 1, Material.GLASS);
        b(w, cx + 2, cy + 1, cz + 1, Material.GLASS);
        b(w, cx - 1, cy + 1, cz - 2, Material.SEAGRASS);
        b(w, cx + 1, cy + 1, cz - 3, Material.SEAGRASS);
        finish(f, m, w, cx, cy, cz, 3.4, 4.7, cy - 1.6, cy + 5.6, 0.3);
    }

    /** WITCH - SWAMP HUT: raised dark-oak hut on mushroom stems looming over the pit. */
    private static void bWitch(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.MOSSY_COBBLESTONE;
        disc(w, cx, cy, cz, 3, -1, -1, Material.MUD);
        pit(f, m, w, cx, cy, cz, 3, 1, 1, Material.OAK_FENCE, wallm, false, 0, 3, false);
        disc(w, cx, cy, cz, 3, 0, 0, Material.PODZOL);
        for (int[] d : new int[][]{{-2, -3}, {2, -3}, {-2, 1}, {2, 1}})
            for (int y = 0; y <= 4; y++)
                b(w, cx + d[0], cy + y, cz + d[1], Material.MUSHROOM_STEM);
        fill(w, cx, cy, cz, -2, 2, 5, 5, -4, 0, Material.DARK_OAK_PLANKS);
        fill(w, cx, cy, cz, -2, 2, 6, 6, -4, 0, Material.DARK_OAK_SLAB);
        for (int x = -2; x <= 2; x += 4) b(w, cx + x, cy + 7, cz - 2, Material.DARK_OAK_SLAB);
        for (int x = -1; x <= 1; x++) b(w, cx + x, cy + 6, cz + 1, Material.DARK_OAK_PLANKS);
        fill(w, cx, cy, cz, -2, 2, 4, 4, -4, -3, Material.DARK_OAK_LOG);
        finish(f, m, w, cx, cy, cz, 3.4, 4.7, cy - 1.6, cy + 7.5, -0.9);
    }

    /** SKELETON - GALLERY TOWER: 2-storey quartz colonnade, arrow-slit windows. */
    private static void bSkeleton(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.SMOOTH_QUARTZ;
        disc(w, cx, cy, cz, 3, -1, -1, Material.STONE_BRICKS);
        pit(f, m, w, cx, cy, cz, 4, 1, 1, Material.IRON_BARS, wallm, false, 0, 4, false);
        ring(w, cx, cy, cz, 2, 0, 5, wallm, -5, 0);
        b(w, cx - 2, cy + 4, cz - 2, Material.GLASS);
        b(w, cx + 2, cy + 4, cz - 2, Material.GLASS);
        b(w, cx - 2, cy + 5, cz - 1, Material.BONE_BLOCK);
        b(w, cx + 2, cy + 5, cz - 1, Material.BONE_BLOCK);
        b(w, cx - 2, cy + 6, cz - 3, Material.BONE_BLOCK);
        b(w, cx + 2, cy + 6, cz - 3, Material.BONE_BLOCK);
        for (int x = -2; x <= 2; x++) b(w, cx + x, cy + 6, cz - 5, Material.QUARTZ_PILLAR);
        finish(f, m, w, cx, cy, cz, 3.4, 5.0, cy - 1.6, cy + 5.6, -0.9);
    }

    /** STRAY - ICE IGLOO: full packed-ice dome with a glass window. */
    private static void bStray(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        disc(w, cx, cy, cz, 4, -1, -1, Material.SNOW_BLOCK);
        pit(f, m, w, cx, cy, cz, 3, 1, 2, Material.GLASS, Material.PACKED_ICE, false, 0, 3, false);
        for (int x = -4; x <= 4; x++)
            for (int z = -5; z <= 2; z++)
                for (int y = 0; y <= 4; y++) {
                    double d2 = x * x + (y - 1.0) * (y - 1.0) + (z + 1.0) * (z + 1.0);
                    if (d2 > 16 && d2 <= 24) {
                        Material mt = Material.PACKED_ICE;
                        if (x == 0 && z == 1 && (y == 0 || y == 1)) mt = Material.GLASS;
                        if (z >= 2 && y <= 1) mt = Material.AIR;
                        if (mt != Material.AIR) b(w, cx + x, cy + y, cz + z, mt);
                    }
                }
        b(w, cx, cy + 4, cz - 1, Material.BLUE_ICE);
        b(w, cx, cy + 2, cz - 3, Material.SNOW_BLOCK);
        finish(f, m, w, cx, cy, cz, 3.6, 4.8, cy - 1.6, cy + 4.6, -0.9);
    }

    /** WITHER SKELETON - SOUL SPIRE: tapering blackstone spire, 1-wide purple pane slit. */
    private static void bWitherSkeleton(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.NETHER_BRICKS;
        disc(w, cx, cy, cz, 3, -1, -1, Material.SOUL_SAND);
        pit(f, m, w, cx, cy, cz, 4, 1, 0, Material.PURPLE_STAINED_GLASS_PANE, wallm, false, 0, 5, false);
        ring(w, cx, cy, cz, 2, 0, 3, wallm, -5, 0);
        ring(w, cx, cy, cz, 1, 0, 5, wallm, -3, 0);
        b(w, cx, cy + 6, cz, Material.NETHER_BRICK_STAIRS);
        b(w, cx - 1, cy + 6, cz - 2, Material.NETHER_BRICK_SLAB);
        b(w, cx + 1, cy + 6, cz - 2, Material.NETHER_BRICK_SLAB);
        b(w, cx, cy + 4, cz - 4, Material.SOUL_LANTERN);
        for (int y = 0; y <= 5; y++) b(w, cx - 2, cy + y, cz - 5, y % 2 == 0 ? wallm : Material.NETHER_BRICK_FENCE);
        for (int y = 0; y <= 5; y++) b(w, cx + 2, cy + y, cz - 5, y % 2 == 0 ? wallm : Material.NETHER_BRICK_FENCE);
        finish(f, m, w, cx, cy, cz, 3.4, 5.2, cy - 1.6, cy + 6.8, -0.9);
    }

    /** PILLAGER - RAIDER CAGE: dark-oak cage on posts, wool banner, fence window. */
    private static void bPillager(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.DARK_OAK_LOG;
        disc(w, cx, cy, cz, 3, -1, -1, Material.COARSE_DIRT);
        pit(f, m, w, cx, cy, cz, 4, 1, 1, Material.DARK_OAK_FENCE, wallm, false, 0, 4, false);
        for (int[] d : new int[][]{{-2, -4}, {2, -4}, {-2, 0}, {2, 0}})
            for (int y = 0; y <= 5; y++)
                b(w, cx + d[0], cy + y, cz + d[1], Material.DARK_OAK_LOG);
        for (int y = 0; y <= 5; y++)
            for (int x = -2; x <= 2; x++)
                b(w, cx + x, cy + y, cz - 4, Material.DARK_OAK_FENCE);
        fill(w, cx, cy, cz, -2, 2, 5, 5, -4, 0, Material.DARK_OAK_TRAPDOOR);
        fill(w, cx, cy, cz, -2, 2, 6, 6, -3, -1, Material.DARK_OAK_SLAB);
        b(w, cx - 2, cy + 3, cz + 1, Material.WHITE_BANNER);
        b(w, cx + 2, cy + 3, cz + 1, Material.GRAY_BANNER);
        b(w, cx, cy + 2, cz - 2, Material.LANTERN);
        finish(f, m, w, cx, cy, cz, 3.4, 5.0, cy - 1.6, cy + 6.6, -0.9);
    }

    /** SPIDER - WEB CAGE: wide fence cage, cobwebs, corner posts. */
    private static void bSpider(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.OAK_LOG;
        disc(w, cx, cy, cz, 4, -1, -1, Material.STONE_BRICKS);
        pit(f, m, w, cx, cy, cz, 3, 2, 3, Material.OAK_FENCE, wallm, false, 0, 5, false);
        for (int[] d : new int[][]{{-3, -3}, {3, -3}, {-3, 1}, {3, 1}})
            for (int y = 0; y <= 5; y++)
                b(w, cx + d[0], cy + y, cz + d[1], Material.OAK_LOG);
        for (int y = 0; y <= 5; y++)
            for (int x = -3; x <= 3; x++)
                b(w, cx + x, cy + y, cz - 3, Material.OAK_FENCE);
        for (int y = 0; y <= 5; y++)
            for (int z = -3; z <= 1; z++) {
                b(w, cx - 3, cy + y, cz + z, Material.OAK_FENCE);
                b(w, cx + 3, cy + y, cz + z, Material.OAK_FENCE);
            }
        for (int y = 2; y <= 4; y++)
            for (int x = -2; x <= 2; x++)
                for (int z = -2; z <= 0; z++)
                    if ((x + z + y) % 3 == 0) b(w, cx + x, cy + y, cz + z, Material.COBWEB);
        b(w, cx, cy + 5, cz - 1, Material.COBWEB);
        finish(f, m, w, cx, cy, cz, 3.9, 5.2, cy - 1.6, cy + 5.8, -0.9);
    }

    /** CAVE SPIDER - BURROW: deep mine tunnel, stepped slab roof, 1-wide window. */
    private static void bCaveSpider(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.DEEPSLATE_BRICKS;
        disc(w, cx, cy, cz, 3, -1, -1, Material.DEEPSLATE_TILES);
        pit(f, m, w, cx, cy, cz, 5, 1, 0, Material.IRON_BARS, wallm, false, 0, 5, false);
        for (int y = 2; y <= 5; y++)
            for (int z = Math.max(-3, -y); z <= 0; z++)
                for (int x = -1; x <= 1; x++)
                    b(w, cx + x, cy + y, cz + z, y == 2 && z == 0 ? Material.COBBLED_DEEPSLATE_SLAB : Material.COBBLED_DEEPSLATE);
        for (int x = -1; x <= 1; x++) b(w, cx + x, cy + 5, cz - 4, Material.DEEPSLATE_TILES);
        b(w, cx, cy + 3, cz - 2, Material.INFESTED_DEEPSLATE);
        b(w, cx - 1, cy + 2, cz - 3, Material.INFESTED_STONE);
        b(w, cx + 1, cy + 2, cz - 3, Material.INFESTED_STONE);
        b(w, cx, cy + 1, cz - 2, Material.AMETHYST_BLOCK);
        finish(f, m, w, cx, cy, cz, 3.4, 5.4, cy - 1.6, cy + 5.8, -0.9);
    }

    /** CREEPER - BLAST VAULT: thick obsidian monolith, single trapdoor slit, blast doors. */
    private static void bCreeper(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.OBSIDIAN;
        disc(w, cx, cy, cz, 4, -1, -1, Material.DEEPSLATE_BRICKS);
        pit(f, m, w, cx, cy, cz, 5, 1, 0, Material.OAK_TRAPDOOR, wallm, false, 0, 5, false);
        fill(w, cx, cy, cz, -3, -2, 0, 6, -7, 1, Material.OBSIDIAN);
        fill(w, cx, cy, cz, 2, 3, 0, 6, -7, 1, Material.OBSIDIAN);
        fill(w, cx, cy, cz, -3, 3, 0, 6, -8, -7, Material.OBSIDIAN);
        fill(w, cx, cy, cz, -3, 3, 6, 7, -7, 0, Material.OBSIDIAN);
        // blast-door bars ABOVE the kill slit (decor; the slit at y=1 stays open)
        for (int x = -1; x <= 1; x++) b(w, cx + x, cy + 2, cz + 1, Material.IRON_BARS);
        for (int x = -2; x <= 2; x++) b(w, cx + x, cy + 5, cz - 2, Material.OBSIDIAN);
        b(w, cx, cy + 5, cz - 5, Material.DEEPSLATE_BRICK_SLAB);
        b(w, cx - 2, cy + 4, cz - 3, Material.DEEPSLATE_BRICK_SLAB);
        b(w, cx + 2, cy + 4, cz - 3, Material.DEEPSLATE_BRICK_SLAB);
        finish(f, m, w, cx, cy, cz, 3.8, 5.6, cy - 1.6, cy + 7.8, -0.9);
    }

    /** ENDERMAN - OBELISK HALL: twin purpur pillars + tall centre obelisk, 1-wide pane slit. */
    private static void bEnderman(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.OBSIDIAN;
        disc(w, cx, cy, cz, 3, -1, -1, Material.END_STONE);
        pit(f, m, w, cx, cy, cz, 3, 1, 0, Material.PURPLE_STAINED_GLASS_PANE, wallm, false, 0, 5, false);
        for (int y = 0; y <= 6; y++) {
            b(w, cx - 2, cy + y, cz - 1, Material.PURPUR_PILLAR);
            b(w, cx + 2, cy + y, cz - 1, Material.PURPUR_PILLAR);
            b(w, cx, cy + y, cz - 3, Material.PURPUR_PILLAR);
        }
        b(w, cx - 2, cy + 7, cz - 1, Material.END_ROD);
        b(w, cx + 2, cy + 7, cz - 1, Material.END_ROD);
        b(w, cx, cy + 7, cz - 3, Material.END_CRYSTAL);
        b(w, cx, cy + 2, cz - 2, Material.ENDER_CHEST);
        b(w, cx, cy + 4, cz - 1, Material.SEA_LANTERN);
        for (int x = -1; x <= 1; x++) b(w, cx + x, cy + 1, cz - 4, Material.PURPUR_SLAB);
        finish(f, m, w, cx, cy, cz, 3.4, 5.0, cy - 1.6, cy + 7.9, -0.9);
    }

    /** BLAZE - FORGE COURT: open-sky nether courtyard, pillars, magma ring. */
    private static void bBlaze(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.NETHER_BRICKS;
        disc(w, cx, cy, cz, 4, -1, -1, Material.NETHERRACK);
        pit(f, m, w, cx, cy, cz, 3, 1, 2, Material.IRON_BARS, wallm, false, 0, 2, true);
        disc(w, cx, cy, cz, 4, 0, 0, Material.MAGMA_BLOCK);
        disc(w, cx, cy, cz, 2, 0, 0, Material.NETHERRACK);
        for (int[] d : new int[][]{{-3, -3}, {3, -3}, {-3, 1}, {3, 1}})
            for (int y = 0; y <= 4; y++)
                b(w, cx + d[0], cy + y, cz + d[1], Material.NETHER_BRICK_FENCE);
        for (int[] d : new int[][]{{-3, -3}, {3, -3}, {-3, 1}, {3, 1}})
            b(w, cx + d[0], cy + 5, cz + d[1], Material.SOUL_LANTERN);
        for (int x = -1; x <= 1; x++) b(w, cx + x, cy + 3, cz - 1, Material.NETHER_WART_BLOCK);
        b(w, cx, cy + 1, cz - 3, Material.GOLD_BLOCK);
        finish(f, m, w, cx, cy, cz, 4.0, 4.6, cy - 1.6, cy + 5.6, -0.9);
    }

    /** SLIME - SWAMP BASIN: round mossy basin with shallow water, glass window. */
    private static void bSlime(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.MOSSY_COBBLESTONE;
        disc(w, cx, cy, cz, 4, -1, -1, Material.MUD);
        pit(f, m, w, cx, cy, cz, 3, 1, 2, Material.GLASS, wallm, true, 1, 3, false);
        ringC(w, cx, cy, cz, 3, 0, 1, Material.MOSS_BLOCK, -4, 1);
        b(w, cx - 1, cy + 1, cz - 2, Material.LILY_PAD);
        b(w, cx + 1, cy + 1, cz - 3, Material.LILY_PAD);
        b(w, cx, cy + 1, cz - 4, Material.BIG_DRIPLEAF);
        b(w, cx - 2, cy + 2, cz - 2, Material.MOSS_CARPET);
        b(w, cx + 2, cy + 2, cz - 2, Material.MOSS_CARPET);
        finish(f, m, w, cx, cy, cz, 3.8, 4.6, cy - 1.6, cy + 3.8, -0.5);
    }

    /** MAGMA CUBE - MAGMA CALDERA: stepped round crater, glow ring, 3-wide window. */
    private static void bMagmaCube(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.POLISHED_BLACKSTONE_BRICKS;
        disc(w, cx, cy, cz, 4, -1, -1, Material.MAGMA_BLOCK);
        pit(f, m, w, cx, cy, cz, 3, 1, 2, Material.IRON_BARS, wallm, false, 0, 3, true);
        ringC(w, cx, cy, cz, 2, 0, 1, Material.POLISHED_BLACKSTONE, -4, 1);
        disc(w, cx, cy, cz, 3, 0, 0, Material.MAGMA_BLOCK);
        ringC(w, cx, cy, cz, 3, 0, 0, Material.GLOWSTONE, -4, -1);
        for (int x = -2; x <= 2; x++) b(w, cx + x, cy + 2, cz - 4, Material.POLISHED_BLACKSTONE_BRICK_SLAB);
        b(w, cx, cy + 1, cz - 4, Material.GOLD_BLOCK);
        finish(f, m, w, cx, cy, cz, 3.8, 4.6, cy - 1.6, cy + 3.8, -0.9);
    }

    /** SILVERFISH - STONE MAZE: infested-stone maze corridors around the pit. */
    private static void bSilverfish(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.STONE_BRICKS;
        disc(w, cx, cy, cz, 4, -1, -1, Material.STONE);
        pit(f, m, w, cx, cy, cz, 3, 1, 1, Material.IRON_BARS, wallm, false, 0, 3, false);
        for (int y = 0; y <= 1; y++) {
            b(w, cx - 2, cy + y, cz - 2, Material.STONE_BRICKS);
            b(w, cx + 2, cy + y, cz - 2, Material.STONE_BRICKS);
            b(w, cx - 2, cy + y, cz - 4, Material.STONE_BRICKS);
            b(w, cx + 2, cy + y, cz - 4, Material.STONE_BRICKS);
        }
        b(w, cx - 2, cy + 2, cz - 3, Material.INFESTED_STONE_BRICKS);
        b(w, cx + 2, cy + 2, cz - 3, Material.INFESTED_STONE_BRICKS);
        b(w, cx, cy + 2, cz - 4, Material.STONE_BRICK_SLAB);
        b(w, cx - 1, cy + 2, cz - 1, Material.STONE_BRICK_SLAB);
        b(w, cx + 1, cy + 2, cz - 1, Material.STONE_BRICK_SLAB);
        b(w, cx, cy + 2, cz - 2, Material.LANTERN);
        finish(f, m, w, cx, cy, cz, 3.8, 4.8, cy - 1.6, cy + 3.8, -0.9);
    }

    /** PHANTOM - SKY COURT: tall octagonal open court, glow accents. */
    private static void bPhantom(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.DEEPSLATE_BRICKS;
        disc(w, cx, cy, cz, 4, -1, -1, Material.DEEPSLATE_TILES);
        pit(f, m, w, cx, cy, cz, 3, 2, 3, Material.IRON_BARS, wallm, false, 0, 8, true);
        ringC(w, cx, cy, cz, 4, 0, 8, wallm, -5, 0);
        ringC(w, cx, cy, cz, 4, 0, 2, wallm, 1, 1);
        b(w, cx - 4, cy + 4, cz - 2, Material.SEA_LANTERN);
        b(w, cx + 4, cy + 4, cz - 2, Material.SEA_LANTERN);
        b(w, cx - 4, cy + 7, cz - 4, Material.DEEPSLATE_BRICK_SLAB);
        b(w, cx + 4, cy + 7, cz - 4, Material.DEEPSLATE_BRICK_SLAB);
        b(w, cx, cy + 8, cz - 3, Material.SEA_LANTERN);
        for (int x = -2; x <= 2; x++) b(w, cx + x, cy + 8, cz + 1, Material.DEEPSLATE_BRICKS);
        finish(f, m, w, cx, cy, cz, 4.4, 5.6, cy - 1.6, cy + 8.8, -0.9);
    }

    /** GUARDIAN - PRISMARINE DOME: aquarium dome, full glass window, water + kelp. */
    private static void bGuardian(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.PRISMARINE_BRICKS;
        disc(w, cx, cy, cz, 4, -1, -1, Material.PRISMARINE);
        pit(f, m, w, cx, cy, cz, 3, 1, 1, Material.GLASS, wallm, true, 2, 4, false);
        for (int x = -4; x <= 4; x++)
            for (int z = -5; z <= 2; z++)
                for (int y = 0; y <= 4; y++) {
                    double d2 = x * x + (y - 1.0) * (y - 1.0) + (z + 1.0) * (z + 1.0);
                    if (d2 > 14 && d2 <= 22) {
                        Material mt = Material.PRISMARINE_BRICKS;
                        if (x == 0 && z == 1 && (y == 0 || y == 1)) mt = Material.GLASS;
                        if (z >= 2 && y <= 1) mt = Material.AIR;
                        if (mt != Material.AIR) b(w, cx + x, cy + y, cz + z, mt);
                    }
                }
        b(w, cx - 1, cy + 3, cz - 1, Material.SEA_LANTERN);
        b(w, cx + 1, cy + 3, cz - 1, Material.SEA_LANTERN);
        b(w, cx, cy + 4, cz - 2, Material.SEA_LANTERN);
        b(w, cx - 1, cy + 1, cz - 3, Material.KELP);
        b(w, cx + 1, cy + 1, cz - 2, Material.KELP);
        finish(f, m, w, cx, cy, cz, 3.8, 4.8, cy - 1.6, cy + 4.8, 0.3);
    }

    /** SQUID - ROUND TANK: glass cylinder with prismarine pillars. */
    private static void bSquid(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.PRISMARINE;
        disc(w, cx, cy, cz, 4, -1, -1, Material.PRISMARINE);
        pit(f, m, w, cx, cy, cz, 3, 1, 1, Material.GLASS, wallm, true, 2, 4, false);
        ringC(w, cx, cy, cz, 3, 0, 4, Material.GLASS, -4, 1);
        for (int y = 0; y <= 4; y++) {
            b(w, cx - 3, cy + y, cz - 1, Material.PRISMARINE_BRICKS);
            b(w, cx + 3, cy + y, cz - 1, Material.PRISMARINE_BRICKS);
            b(w, cx, cy + y, cz - 4, Material.PRISMARINE_BRICKS);
        }
        ringC(w, cx, cy, cz, 3, 4, 4, Material.PRISMARINE_BRICKS, -4, 1);
        b(w, cx - 1, cy + 1, cz - 3, Material.SEAGRASS);
        b(w, cx + 1, cy + 1, cz - 2, Material.SEAGRASS);
        b(w, cx, cy + 2, cz - 4, Material.SEA_LANTERN);
        finish(f, m, w, cx, cy, cz, 3.8, 4.8, cy - 1.6, cy + 4.8, 0.3);
    }

    /** GLOW SQUID - GLOW TANK: dark-prismarine cylinder, sea-lantern pillars, glow floor. */
    private static void bGlowSquid(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.DARK_PRISMARINE;
        disc(w, cx, cy, cz, 4, -1, -1, Material.DARK_PRISMARINE);
        pit(f, m, w, cx, cy, cz, 3, 1, 1, Material.GLASS, wallm, true, 2, 4, false);
        ringC(w, cx, cy, cz, 3, 0, 4, Material.GLASS, -4, 1);
        for (int y = 0; y <= 4; y++) {
            b(w, cx - 3, cy + y, cz - 1, Material.DARK_PRISMARINE);
            b(w, cx + 3, cy + y, cz - 1, Material.DARK_PRISMARINE);
            b(w, cx, cy + y, cz - 4, Material.DARK_PRISMARINE);
        }
        ringC(w, cx, cy, cz, 3, 4, 4, Material.DARK_PRISMARINE, -4, 1);
        b(w, cx - 1, cy + 1, cz - 3, Material.SEA_LANTERN);
        b(w, cx + 1, cy + 1, cz - 2, Material.SEA_LANTERN);
        b(w, cx, cy + 2, cz - 3, Material.GLOWSTONE);
        b(w, cx, cy + 3, cz - 4, Material.SEA_LANTERN);
        finish(f, m, w, cx, cy, cz, 3.8, 4.8, cy - 1.6, cy + 4.8, 0.3);
    }

    /** HOGLIN - CRIMSON FORTRESS: red nether-brick walls, crenellations, gates. */
    private static void bHoglin(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.RED_NETHER_BRICKS;
        disc(w, cx, cy, cz, 4, -1, -1, Material.NETHERRACK);
        pit(f, m, w, cx, cy, cz, 4, 1, 2, Material.CRIMSON_FENCE, wallm, false, 0, 4, false);
        fill(w, cx, cy, cz, -3, -2, 0, 4, -6, 1, wallm);
        fill(w, cx, cy, cz, 2, 3, 0, 4, -6, 1, wallm);
        fill(w, cx, cy, cz, -3, 3, 0, 4, -7, -6, wallm);
        for (int y = 0; y <= 4; y++) {
            b(w, cx - 3, cy + y, cz + 1, wallm);
            b(w, cx + 3, cy + y, cz + 1, wallm);
        }
        for (int x = -3; x <= 3; x += 2) b(w, cx + x, cy + 5, cz - 6, wallm);
        for (int x = -3; x <= 3; x += 2) b(w, cx + x, cy + 5, cz - 3, wallm);
        for (int x = -3; x <= 3; x += 2) b(w, cx + x, cy + 5, cz + 1, wallm);
        b(w, cx - 3, cy + 5, cz - 1, Material.WARPED_FENCE);
        b(w, cx + 3, cy + 5, cz - 1, Material.WARPED_FENCE);
        b(w, cx - 2, cy + 1, cz - 3, Material.CRIMSON_FUNGUS);
        b(w, cx + 2, cy + 1, cz - 3, Material.CRIMSON_FUNGUS);
        b(w, cx, cy + 2, cz - 5, Material.CRIMSON_ROOTS);
        finish(f, m, w, cx, cy, cz, 4.0, 5.4, cy - 1.6, cy + 5.8, -0.9);
    }

    /** PIGLIN - GOLD BASTION: blackstone bastion with gold trim + corner towers. */
    private static void bPiglin(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.POLISHED_BLACKSTONE_BRICKS;
        disc(w, cx, cy, cz, 4, -1, -1, Material.BLACKSTONE);
        pit(f, m, w, cx, cy, cz, 4, 1, 2, Material.IRON_BARS, wallm, false, 0, 4, false);
        fill(w, cx, cy, cz, -3, -2, 0, 4, -6, 1, wallm);
        fill(w, cx, cy, cz, 2, 3, 0, 4, -6, 1, wallm);
        fill(w, cx, cy, cz, -3, 3, 0, 4, -7, -6, wallm);
        fill(w, cx, cy, cz, -3, -2, 2, 2, -6, 1, Material.GOLD_BLOCK);
        fill(w, cx, cy, cz, 2, 3, 2, 2, -6, 1, Material.GOLD_BLOCK);
        fill(w, cx, cy, cz, -3, -2, 5, 5, -6, -1, Material.GOLD_BLOCK);
        fill(w, cx, cy, cz, 2, 3, 5, 5, -6, -1, Material.GOLD_BLOCK);
        for (int[] d : new int[][]{{-3, -5}, {3, -5}})
            for (int y = 0; y <= 5; y++) {
                b(w, cx + d[0], cy + y, cz + d[1], wallm);
                b(w, cx + d[0] + (d[0] < 0 ? 1 : -1), cy + y, cz + d[1], wallm);
            }
        b(w, cx - 3, cy + 6, cz - 5, Material.GOLD_BLOCK);
        b(w, cx + 3, cy + 6, cz - 5, Material.GOLD_BLOCK);
        b(w, cx - 2, cy + 1, cz - 3, Material.GILDED_BLACKSTONE);
        b(w, cx + 2, cy + 1, cz - 3, Material.GILDED_BLACKSTONE);
        b(w, cx, cy + 2, cz - 4, Material.GOLD_BLOCK);
        finish(f, m, w, cx, cy, cz, 4.0, 5.4, cy - 1.6, cy + 6.4, -0.9);
    }
    /* ================= 14 ANIMAL PENS - each a unique pasture/coop/den ================= */

    /** COW - OPEN PASTURE: 5x5 meadow, 1-high oak fence, trough + tree shade. */
    private static void bCow(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 2, 1, Material.GRASS_BLOCK, Material.OAK_FENCE, 0, false);
        b(w, cx - 2, cy + 2, cz + 2, Material.WATER); // trough
        b(w, cx - 2, cy + 3, cz - 2, Material.OAK_LOG);
        b(w, cx - 3, cy + 4, cz - 3, Material.OAK_LEAVES);
        b(w, cx - 2, cy + 4, cz - 3, Material.OAK_LEAVES);
        b(w, cx - 3, cy + 4, cz - 2, Material.OAK_LEAVES);
        b(w, cx - 1, cy + 4, cz - 2, Material.OAK_LEAVES);
        finish(f, m, w, cx, cy, cz, 3.6, 4.6, cy - 1.4, cy + 4.6, 3.0);
    }

    /** PIG - MUD STY: 3x3 roofed hut with a slab ridge, mud floor patch. */
    private static void bPig(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 1, 2, Material.OAK_PLANKS, Material.OAK_FENCE, 4, false);
        b(w, cx, cy + 2, cz, Material.MUD);
        b(w, cx, cy + 3, cz - 1, Material.OAK_FENCE);
        b(w, cx - 1, cy + 2, cz + 1, Material.OAK_TRAPDOOR);
        slab(w, cx, cy + 6, cz, Material.OAK_SLAB, false);
        finish(f, m, w, cx, cy, cz, 3.0, 4.0, cy - 1.4, cy + 6.6, 3.0);
    }

    /** CHICKEN - COOP: 3x3 raised coop, full roof + roosting bar + hay nest. */
    private static void bChicken(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 1, 2, Material.OAK_PLANKS, Material.OAK_FENCE, 1, false);
        b(w, cx, cy + 5, cz, Material.OAK_FENCE);          // roost bar
        b(w, cx, cy + 3, cz, Material.HAY_BLOCK);
        b(w, cx, cy + 3, cz + 1, Material.HAY_BLOCK);
        slab(w, cx, cy + 6, cz, Material.BIRCH_SLAB, false);
        finish(f, m, w, cx, cy, cz, 3.0, 4.0, cy - 1.4, cy + 6.6, 3.0);
    }

    /** SHEEP - WOOL PEN: white wool walls (solid ring), 5x5, no roof. */
    private static void bSheep(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 2, 1, Material.GRASS_BLOCK, Material.WHITE_WOOL, 0, false);
        b(w, cx - 1, cy + 3, cz, Material.PINK_WOOL);
        slab(w, cx, cy + 3, cz - 2, Material.WHITE_WOOL, false);
        finish(f, m, w, cx, cy, cz, 3.6, 4.2, cy - 1.4, cy + 4.0, 3.0);
    }

    /** RABBIT - WARREN: sunken burrow with a dirt mound + carrot garden. */
    private static void bRabbit(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 1, 2, Material.DIRT, Material.OAK_FENCE, 2, false);
        for (int x = -2; x <= 2; x++) {
            b(w, cx + x, cy + 4, cz - 2, x == 0 ? Material.DIRT_PATH : Material.DIRT);
        }
        b(w, cx, cy + 3, cz - 1, Material.CARROTS);
        b(w, cx, cy + 3, cz, Material.CARROTS);
        b(w, cx - 1, cy + 3, cz - 1, Material.CARROTS);
        b(w, cx + 1, cy + 3, cz - 1, Material.CARROTS);
        finish(f, m, w, cx, cy, cz, 3.2, 4.2, cy - 1.4, cy + 5.0, 3.0);
    }

    /** VILLAGER - VILLAGE: plaza with a well + two tiny oak houses. */
    private static void bVillager(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 2, 2, Material.GRASS_BLOCK, Material.STONE_BRICKS, 3, false);
        fill(w, cx, cy, cz, -1, 1, 3, 3, -3, -3, Material.OAK_PLANKS);
        fill(w, cx, cy, cz, -1, 1, 4, 4, -3, -3, Material.OAK_SLAB);
        fill(w, cx, cy, cz, -2, -2, 3, 3, -1, 1, Material.OAK_PLANKS);
        fill(w, cx, cy, cz, -2, -2, 4, 4, -1, 1, Material.OAK_SLAB);
        b(w, cx, cy + 3, cz, Material.CAULDRON);
        finish(f, m, w, cx, cy, cz, 3.6, 4.6, cy - 1.4, cy + 5.2, 3.0);
    }

    /** IRON GOLEM - GOLEM COURT: 2-high iron-block pillars, rose garden, 5x5. */
    private static void bIronGolem(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 2, 2, Material.POLISHED_ANDESITE, Material.IRON_BLOCK, 0, false);
        for (int[] d : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}})
            for (int y = 0; y < 2; y++)
                b(w, cx + d[0], cy + 3 + y, cz + d[1], Material.IRON_BLOCK);
        b(w, cx, cy + 3, cz, Material.POPPY);
        b(w, cx - 1, cy + 3, cz, Material.POPPY);
        b(w, cx + 1, cy + 3, cz, Material.POPPY);
        finish(f, m, w, cx, cy, cz, 3.6, 4.2, cy - 1.4, cy + 4.8, 3.0);
    }

    /** BEE - APIARY: 3x3 hive garden, full roof, beehives + flowers. */
    private static void bBee(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 1, 2, Material.GRASS_BLOCK, Material.OAK_FENCE, 1, false);
        b(w, cx - 1, cy + 3, cz - 1, Material.BEEHIVE);
        b(w, cx + 1, cy + 3, cz - 1, Material.BEEHIVE);
        b(w, cx, cy + 3, cz - 1, Material.BEEHIVE);
        b(w, cx - 1, cy + 3, cz + 1, Material.DANDELION);
        b(w, cx + 1, cy + 3, cz + 1, Material.POPPY);
        b(w, cx, cy + 3, cz + 1, Material.CORNFLOWER);
        finish(f, m, w, cx, cy, cz, 3.0, 4.0, cy - 1.4, cy + 5.4, 3.0);
    }

    /** FOX - DEN: mossy berm + berry bushes, half roof. */
    private static void bFox(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 2, 2, Material.MOSS_BLOCK, Material.OAK_FENCE, 2, false);
        for (int x = -1; x <= 1; x++)
            b(w, cx + x, cy + 4, cz - 2, Material.MOSS_BLOCK);
        b(w, cx - 1, cy + 3, cz, Material.SWEET_BERRY_BUSH);
        b(w, cx + 1, cy + 3, cz, Material.SWEET_BERRY_BUSH);
        b(w, cx, cy + 3, cz + 1, Material.SWEET_BERRY_BUSH);
        finish(f, m, w, cx, cy, cz, 3.6, 4.4, cy - 1.4, cy + 5.0, 3.0);
    }

    /** GOAT - ICE PEAK: stepped packed-ice mountain, 3-high blue-ice fence. */
    private static void bGoat(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 2, 3, Material.SNOW_BLOCK, Material.BLUE_ICE, 0, false);
        for (int x = -2; x <= 2; x++)
            for (int z = -2; z <= 1; z++)
                if (z <= -1) b(w, cx + x, cy + 4, cz + z, Material.PACKED_ICE);
        for (int x = -2; x <= 2; x++)
            b(w, cx + x, cy + 5, cz - 2, Material.PACKED_ICE);
        b(w, cx, cy + 6, cz - 1, Material.PACKED_ICE);
        finish(f, m, w, cx, cy, cz, 3.6, 4.4, cy - 1.4, cy + 6.2, 3.0);
    }

    /** LLAMA - CARAVAN: two wool tents, carpets, open 5x5 pen. */
    private static void bLlama(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 2, 1, Material.COARSE_DIRT, Material.OAK_FENCE, 0, false);
        for (int[] d : new int[][]{{-1, -2}, {1, -2}}) {
            for (int y = 0; y < 2; y++)
                b(w, cx + d[0], cy + 3 + y, cz + d[1], Material.RED_WOOL);
            slab(w, cx + d[0], cy + 5, cz + d[1], Material.WHITE_WOOL, false);
        }
        b(w, cx, cy + 3, cz, Material.MAGENTA_CARPET);
        b(w, cx, cy + 3, cz + 1, Material.LIGHT_BLUE_CARPET);
        b(w, cx, cy + 3, cz + 2, Material.YELLOW_CARPET);
        finish(f, m, w, cx, cy, cz, 3.6, 4.4, cy - 1.4, cy + 5.0, 3.0);
    }

    /** PANDA - BAMBOO GROVE: bamboo stalks + big leaf canopy, 5x5 pen. */
    private static void bPanda(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 2, 2, Material.GRASS_BLOCK, Material.OAK_FENCE, 0, false);
        for (int[] d : new int[][]{{-2, -1}, {-1, -2}, {1, 0}, {2, -2}}) {
            for (int y = 0; y < 3; y++)
                b(w, cx + d[0], cy + 3 + y, cz + d[1], Material.BAMBOO);
        }
        for (int x = -2; x <= 2; x++)
            for (int z = -3; z <= 0; z++)
                if ((x + z) % 2 == 0) b(w, cx + x, cy + 6, cz + z, Material.OAK_LEAVES);
        b(w, cx - 2, cy + 6, cz - 1, Material.BAMBOO);
        b(w, cx + 2, cy + 6, cz - 1, Material.BAMBOO);
        finish(f, m, w, cx, cy, cz, 3.6, 4.6, cy - 1.4, cy + 6.8, 3.0);
    }

    /** FROG - LILY POND: round moss pond with a fountain + lily pads. */
    private static void bFrog(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 2, 1, Material.MOSS_BLOCK, Material.OAK_FENCE, 0, true);
        b(w, cx, cy + 4, cz, Material.SEA_LANTERN);
        b(w, cx, cy + 3, cz + 1, Material.LILY_PAD);
        b(w, cx - 1, cy + 3, cz - 1, Material.LILY_PAD);
        b(w, cx + 1, cy + 3, cz - 1, Material.LILY_PAD);
        b(w, cx - 2, cy + 3, cz + 2, Material.BIG_DRIPLEAF);
        b(w, cx + 2, cy + 3, cz + 2, Material.BIG_DRIPLEAF);
        finish(f, m, w, cx, cy, cz, 3.4, 4.0, cy - 1.4, cy + 4.4, 2.6);
    }

    /** SNIFFER - DIG SITE: dirt crater with torchflower garden + sand patches. */
    private static void bSniffer(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, m, w, cx, cy, cz, 2, 1, Material.DIRT, Material.OAK_FENCE, 0, false);
        for (int[] d : new int[][]{{-2, -2}, {2, -2}, {-2, 2}, {2, 2}})
            b(w, cx + d[0], cy + 2, cz + d[1], Material.DIRT_PATH);
        b(w, cx, cy + 3, cz, Material.TORCHFLOWER);
        b(w, cx - 1, cy + 3, cz, Material.TORCHFLOWER);
        b(w, cx + 1, cy + 3, cz, Material.TORCHFLOWER);
        b(w, cx, cy + 3, cz - 1, Material.SAND);
        b(w, cx, cy + 3, cz + 1, Material.SAND);
        finish(f, m, w, cx, cy, cz, 3.6, 4.2, cy - 1.4, cy + 4.0, 3.0);
    }
}
