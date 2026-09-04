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

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * MAVOMobFarm 2.7.0 - every one of the 36 mobs gets a HAND-BUILT layout with a different
 * structure (sunken crypt, pyramid, igloo, tower, cage, vault, obelisk, courtyard, basin,
 * caldera, maze, court, dome, tank, fortress, bastion + 14 unique animal pens).
 *
 * The KILL METHOD stays identical everywhere: hopper floor feeding ONE double loot chest
 * that stands ON the trench floor (front face flush - clickable, never covered), a 2-high
 * see-through barrier window you hit through, and teleport containment as backup.
 *
 * Every mob also owns its own datapack: world/datapacks/&lt;id&gt;-datapack.zip with
 * clear (fill bay box air) + build (exact block states). /mobfarm build &lt;mob&gt; runs
 * clear, waits 5s, re-writes + reloads the pack, then applies the build - so one bay can
 * be redesigned and rebuilt without touching the other 35.
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
     * 2-high barrier window above the chest, wall above that, sunken trench + walkway.
     */
    private static void pit(MobFarm f, World w, int cx, int cy, int cz,
                            int depth, int halfW, int winW, Material barrier,
                            Material wallM, boolean water, int waterLevel, int top, boolean openTop) {
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
        // front wall at z=+1: winW-wide 2-high window (see-through barrier), wall elsewhere
        for (int x = -halfW; x <= halfW; x++) {
            boolean win = Math.abs(x) <= winW;
            for (int y = 0; y <= top; y++)
                b(w, cx + x, cy + y, cz + 1, win && y <= 1 ? barrier : wallM);
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
    private static void pen(MobFarm f, World w, int cx, int cy, int cz, int half, int fenceH,
                            Material floorM, Material fenceM, int roofMode, boolean pond) {
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
        m.cell = new double[]{hx, hz, minY, maxY};
        m.stand = new Location(w, cx + 0.5, cy + 1, cz + 6.5);
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
        // zone plate + hit pad (on the walkway)
        Block name = w.getBlockAt(cx, cy + 1, cz + 7);
        name.setType(Material.OAK_SIGN, false);
        f.faceSign(name, BlockFace.NORTH);
        f.writeSign(name, ChatColor.GOLD + "ZONE", ChatColor.stripColor(m.display),
                ChatColor.GRAY + m.style + "/" + m.theme, ChatColor.AQUA + "HIT ▶");
        Block hitpad = w.getBlockAt(cx, cy, cz + 5);
        hitpad.setType(Material.LIME_WOOL, false);
        Block hitSgn = w.getBlockAt(cx, cy + 1, cz + 5);
        hitSgn.setType(Material.OAK_SIGN, false);
        f.faceSign(hitSgn, BlockFace.NORTH);
        f.writeSign(hitSgn, ChatColor.GREEN + "HIT", ChatColor.WHITE + "HERE",
                ChatColor.GRAY + "stand ↓", ChatColor.YELLOW + "/mobfarm pick");
        // LOOT standing sign ON the chest face, readable from the trench
        Block lootSign = w.getBlockAt(cx, cy, cz + 1);
        lootSign.setType(Material.OAK_SIGN, false);
        f.faceSign(lootSign, BlockFace.SOUTH);
        f.writeSign(lootSign, ChatColor.GOLD + "LOOT", ChatColor.WHITE + "↓ chest",
                ChatColor.GRAY + "hopper fed", ChatColor.YELLOW + "collect ↓");
        // community pedestal sign for barns uses the same markers
    }

    /** re-apply things a setblock snapshot cannot carry: chest pairing, spawner, sign text. */
    private static void patch(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        f.placeDoubleChest(w, cx - 1, cy - 1, cz + 1, BlockFace.SOUTH);
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
        Block hitSgn = w.getBlockAt(cx, cy + 1, cz + 5);
        f.writeSign(hitSgn, ChatColor.GREEN + "HIT", ChatColor.WHITE + "HERE",
                ChatColor.GRAY + "stand ↓", ChatColor.YELLOW + "/mobfarm pick");
        Block lootSign = w.getBlockAt(cx, cy, cz + 1);
        f.writeSign(lootSign, ChatColor.GOLD + "LOOT", ChatColor.WHITE + "↓ chest",
                ChatColor.GRAY + "hopper fed", ChatColor.YELLOW + "collect ↓");
    }

    /* ------------------------------------------------ per-mob datapacks (2.7.0) */

    private static File packFile(World w, String id) {
        File d = new File(w.getWorldFolder(), "datapacks");
        if (!d.exists()) d.mkdirs();
        return new File(d, id + "-datapack.zip");
    }

    private static void runConsole(MobFarm f, String cmd) {
        try { f.getServer().dispatchCommand(f.getServer().getConsoleSender(), cmd); }
        catch (Throwable ignored) {}
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

    private static void writePack(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        try {
            File zf = packFile(w, m.id);
            File tmp = new File(zf.getParentFile(), zf.getName() + ".tmp");
            try (ZipOutputStream zo = new ZipOutputStream(new FileOutputStream(tmp))) {
                zo.putNextEntry(new ZipEntry("pack.mcmeta"));
                zo.write(("{\"pack\":{\"pack_format\":48,\"supported_formats\":{\"min_format\":48,"
                        + "\"max_format\":999},\"description\":\"MAVOMobFarm " + m.id + " bay builder\"}}")
                        .getBytes(StandardCharsets.UTF_8));
                zo.closeEntry();
                zo.putNextEntry(new ZipEntry("data/mavomobfarm/function/" + m.id + "/clear.mcfunction"));
                zo.write(("fill " + (cx - 12) + " " + (cy - 10) + " " + (cz - 12) + " "
                        + (cx + 12) + " " + (cy + 16) + " " + (cz + 10) + " air").getBytes(StandardCharsets.UTF_8));
                zo.closeEntry();
                zo.putNextEntry(new ZipEntry("data/mavomobfarm/function/" + m.id + "/build.mcfunction"));
                for (String line : snapshot(f, w, cx, cy, cz)) {
                    zo.write(line.getBytes(StandardCharsets.UTF_8));
                    zo.write('\n');
                }
                zo.closeEntry();
            }
            if (zf.exists()) zf.delete();
            tmp.renameTo(zf);
        } catch (Throwable t) {
            f.getLogger().warning("writePack " + m.id + " failed: " + t);
        }
    }

    /** True when the mob's datapack already exists (it was built before). */
    static boolean wasBuilt(MobFarm f, MobDef m) {
        if (f.center == null) return false;
        return packFile(f.center.getWorld(), m.id).exists();
    }

    /** Java build + write pack + (re)enable pack + apply via its build function + patch. */
    private static void applyBuild(MobFarm f, MobDef m) {
        buildMobSync(f, m);
        f.getLogger().info("MAVOMobFarm: applied " + m.id + " build via " + m.id + "-datapack.zip");
    }

    /** synchronous per-mob build (used by /mobfarm build all + the reload path). */
    static void buildMobSync(MobFarm f, MobDef m) {
        if (f.center == null) return;
        World w = f.center.getWorld();
        Location o = f.center.clone().add(m.ox, m.oy, m.oz);
        int cx = o.getBlockX(), cy = o.getBlockY(), cz = o.getBlockZ();
        // clear volume so a redesign fully replaces the old shell
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 10; z++)
                for (int y = -10; y <= 16; y++)
                    air(w, cx + x, cy + y, cz + z);
        build(f, m);
        writePack(f, m, w, cx, cy, cz);
        runConsole(f, "datapack disable \"file/" + m.id + "-datapack\"");
        runConsole(f, "datapack enable \"file/" + m.id + "-datapack\"");
        loadBayChunks(w, cx, cy, cz);
        runConsole(f, "function mavomobfarm:" + m.id + "/build");
        patch(f, m, w, cx, cy, cz);
    }

    /** /mobfarm build <mob>: clear what the old pack built, wait 5s, reload + apply. */
    static void buildMob(MobFarm f, MobDef m, PlayerRef user) {
        if (f.center == null) { user.msg(ChatColor.RED + "Set center first."); return; }
        World w = f.center.getWorld();
        Location o = f.center.clone().add(m.ox, m.oy, m.oz);
        boolean had = wasBuilt(f, m);
        if (had) {
            // 1) delete every block the old pack built (its clear function)
            loadBayChunks(w, o.getBlockX(), o.getBlockY(), o.getBlockZ());
            runConsole(f, "function mavomobfarm:" + m.id + "/clear");
            user.msg(ChatColor.YELLOW + "Cleared " + m.id + " bay (old datapack). Waiting 5s, then reloading "
                    + m.id + "-datapack.zip...");
            Bukkit.getScheduler().runTaskLater(f, () -> {
                applyBuild(f, m);
                user.msg(ChatColor.GREEN + "Rebuilt " + ChatColor.stripColor(m.display) + " bay via "
                        + m.id + "-datapack.zip.");
            }, 100L);
        } else {
            applyBuild(f, m);
            user.msg(ChatColor.GREEN + "Built " + ChatColor.stripColor(m.display) + " bay + "
                    + m.id + "-datapack.zip.");
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
        pit(f, w, cx, cy, cz, 3, 1, 1, Material.IRON_BARS, wallm, false, 0, 4, false);
        ringC(w, cx, cy, cz, 3, 0, 1, wallm, -4, 1);
        ringC(w, cx, cy, cz, 3, 2, 3, Material.MOSS_BLOCK, -4, -1);
        for (int y = 0; y <= 1; y++)
            b(w, cx, cy + y, cz + 1, Material.IRON_BARS);
        for (int y = 0; y <= 3; y++) b(w, cx - 1, cy + y, cz - 3, Material.MOSSY_STONE_BRICKS);
        b(w, cx, cy + 3, cz - 2, Material.SPAWNER);
        w.getBlockAt(cx, cy + 3, cz - 2).setType(Material.AIR, false);
        finish(f, m, w, cx, cy, cz, 3.4, 4.6, cy - 1.6, cy + 3.8, -0.9);
    }

    /** HUSK - DESERT PYRAMID: 3-tier sandstone pyramid with a shaft down to the kill pit. */
    private static void bHusk(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        Material wallm = Material.SANDSTONE;
        fill(w, cx, cy, cz, -3, 3, -1, -1, -3, 0, Material.SAND);
        pit(f, w, cx, cy, cz, 3, 1, 1, Material.IRON_BARS, wallm, false, 0, 2, true);
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
        pit(f, w, cx, cy, cz, 3, 1, 1, Material.GLASS, wallm, true, 2, 4, false);
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
        pit(f, w, cx, cy, cz, 3, 1, 1, Material.OAK_FENCE, wallm, false, 0, 3, false);
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
        pit(f, w, cx, cy, cz, 4, 1, 1, Material.IRON_BARS, wallm, false, 0, 4, false);
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
        pit(f, w, cx, cy, cz, 3, 1, 2, Material.GLASS, Material.PACKED_ICE, false, 0, 3, false);
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
        pit(f, w, cx, cy, cz, 4, 1, 0, Material.PURPLE_STAINED_GLASS_PANE, wallm, false, 0, 5, false);
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
        pit(f, w, cx, cy, cz, 4, 1, 1, Material.DARK_OAK_FENCE, wallm, false, 0, 4, false);
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
        pit(f, w, cx, cy, cz, 3, 2, 3, Material.OAK_FENCE, wallm, false, 0, 5, false);
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
        pit(f, w, cx, cy, cz, 5, 1, 0, Material.IRON_BARS, wallm, false, 0, 5, false);
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
        pit(f, w, cx, cy, cz, 5, 1, 0, Material.OAK_TRAPDOOR, wallm, false, 0, 5, false);
        fill(w, cx, cy, cz, -3, -2, 0, 6, -7, 1, Material.OBSIDIAN);
        fill(w, cx, cy, cz, 2, 3, 0, 6, -7, 1, Material.OBSIDIAN);
        fill(w, cx, cy, cz, -3, 3, 0, 6, -8, -7, Material.OBSIDIAN);
        fill(w, cx, cy, cz, -3, 3, 6, 7, -7, 0, Material.OBSIDIAN);
        for (int x = -2; x <= 2; x++) {
            b(w, cx + x, cy + 1, cz + 1, Material.IRON_BARS);
            b(w, cx + x, cy + 2, cz + 1, Material.IRON_BARS);
        }
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
        pit(f, w, cx, cy, cz, 3, 1, 0, Material.PURPLE_STAINED_GLASS_PANE, wallm, false, 0, 5, false);
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
        pit(f, w, cx, cy, cz, 3, 1, 2, Material.IRON_BARS, wallm, false, 0, 2, true);
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
        pit(f, w, cx, cy, cz, 3, 1, 2, Material.GLASS, wallm, true, 1, 3, false);
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
        pit(f, w, cx, cy, cz, 3, 1, 2, Material.IRON_BARS, wallm, false, 0, 3, true);
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
        pit(f, w, cx, cy, cz, 3, 1, 1, Material.IRON_BARS, wallm, false, 0, 3, false);
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
        pit(f, w, cx, cy, cz, 3, 2, 3, Material.IRON_BARS, wallm, false, 0, 8, true);
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
        pit(f, w, cx, cy, cz, 3, 1, 1, Material.GLASS, wallm, true, 2, 4, false);
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
        pit(f, w, cx, cy, cz, 3, 1, 1, Material.GLASS, wallm, true, 2, 4, false);
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
        pit(f, w, cx, cy, cz, 3, 1, 1, Material.GLASS, wallm, true, 2, 4, false);
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
        pit(f, w, cx, cy, cz, 4, 1, 2, Material.CRIMSON_FENCE, wallm, false, 0, 4, false);
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
        pit(f, w, cx, cy, cz, 4, 1, 2, Material.IRON_BARS, wallm, false, 0, 4, false);
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
        pen(f, w, cx, cy, cz, 2, 1, Material.GRASS_BLOCK, Material.OAK_FENCE, 0, false);
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
        pen(f, w, cx, cy, cz, 1, 2, Material.OAK_PLANKS, Material.OAK_FENCE, 4, false);
        b(w, cx, cy + 2, cz, Material.MUD);
        b(w, cx, cy + 3, cz - 1, Material.OAK_FENCE);
        b(w, cx - 1, cy + 2, cz + 1, Material.OAK_TRAPDOOR);
        slab(w, cx, cy + 6, cz, Material.OAK_SLAB, false);
        finish(f, m, w, cx, cy, cz, 3.0, 4.0, cy - 1.4, cy + 6.6, 3.0);
    }

    /** CHICKEN - COOP: 3x3 raised coop, full roof + roosting bar + hay nest. */
    private static void bChicken(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, w, cx, cy, cz, 1, 2, Material.OAK_PLANKS, Material.OAK_FENCE, 1, false);
        b(w, cx, cy + 5, cz, Material.OAK_FENCE);          // roost bar
        b(w, cx, cy + 3, cz, Material.HAY_BLOCK);
        b(w, cx, cy + 3, cz + 1, Material.HAY_BLOCK);
        slab(w, cx, cy + 6, cz, Material.BIRCH_SLAB, false);
        finish(f, m, w, cx, cy, cz, 3.0, 4.0, cy - 1.4, cy + 6.6, 3.0);
    }

    /** SHEEP - WOOL PEN: white wool walls (solid ring), 5x5, no roof. */
    private static void bSheep(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, w, cx, cy, cz, 2, 1, Material.GRASS_BLOCK, Material.WHITE_WOOL, 0, false);
        b(w, cx - 1, cy + 3, cz, Material.PINK_WOOL);
        slab(w, cx, cy + 3, cz - 2, Material.WHITE_WOOL, false);
        finish(f, m, w, cx, cy, cz, 3.6, 4.2, cy - 1.4, cy + 4.0, 3.0);
    }

    /** RABBIT - WARREN: sunken burrow with a dirt mound + carrot garden. */
    private static void bRabbit(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, w, cx, cy, cz, 1, 2, Material.DIRT, Material.OAK_FENCE, 2, false);
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
        pen(f, w, cx, cy, cz, 2, 2, Material.GRASS_BLOCK, Material.STONE_BRICKS, 3, false);
        fill(w, cx, cy, cz, -1, 1, 3, 3, -3, -3, Material.OAK_PLANKS);
        fill(w, cx, cy, cz, -1, 1, 4, 4, -3, -3, Material.OAK_SLAB);
        fill(w, cx, cy, cz, -2, -2, 3, 3, -1, 1, Material.OAK_PLANKS);
        fill(w, cx, cy, cz, -2, -2, 4, 4, -1, 1, Material.OAK_SLAB);
        b(w, cx, cy + 3, cz, Material.CAULDRON);
        finish(f, m, w, cx, cy, cz, 3.6, 4.6, cy - 1.4, cy + 5.2, 3.0);
    }

    /** IRON GOLEM - GOLEM COURT: 2-high iron-block pillars, rose garden, 5x5. */
    private static void bIronGolem(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, w, cx, cy, cz, 2, 2, Material.POLISHED_ANDESITE, Material.IRON_BLOCK, 0, false);
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
        pen(f, w, cx, cy, cz, 1, 2, Material.GRASS_BLOCK, Material.OAK_FENCE, 1, false);
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
        pen(f, w, cx, cy, cz, 2, 2, Material.MOSS_BLOCK, Material.OAK_FENCE, 2, false);
        for (int x = -1; x <= 1; x++)
            b(w, cx + x, cy + 4, cz - 2, Material.MOSS_BLOCK);
        b(w, cx - 1, cy + 3, cz, Material.SWEET_BERRY_BUSH);
        b(w, cx + 1, cy + 3, cz, Material.SWEET_BERRY_BUSH);
        b(w, cx, cy + 3, cz + 1, Material.SWEET_BERRY_BUSH);
        finish(f, m, w, cx, cy, cz, 3.6, 4.4, cy - 1.4, cy + 5.0, 3.0);
    }

    /** GOAT - ICE PEAK: stepped packed-ice mountain, 3-high blue-ice fence. */
    private static void bGoat(MobFarm f, MobDef m, World w, int cx, int cy, int cz) {
        pen(f, w, cx, cy, cz, 2, 3, Material.SNOW_BLOCK, Material.BLUE_ICE, 0, false);
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
        pen(f, w, cx, cy, cz, 2, 1, Material.COARSE_DIRT, Material.OAK_FENCE, 0, false);
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
        pen(f, w, cx, cy, cz, 2, 2, Material.GRASS_BLOCK, Material.OAK_FENCE, 0, false);
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
        pen(f, w, cx, cy, cz, 2, 1, Material.MOSS_BLOCK, Material.OAK_FENCE, 0, true);
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
        pen(f, w, cx, cy, cz, 2, 1, Material.DIRT, Material.OAK_FENCE, 0, false);
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
