package mavo.timber;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** MAVOTimber 1.0.0 - tree felling (Discord CW#4 idea 13, inspired by UltimateTimber).
 *  Break the bottom log of a GROWN tree and up to `max-logs` connected logs break at
 *  once (default 10 - anti-exploit: no more 150-log dark forest hauls); drops land at
 *  the trunk; one axe durability per extra log; Lumberjack XP capped at
 *  `xp-cap-per-tree` per tree; the tree's leaves decay a tick later.
 *  NATURAL-TREES-ONLY (HOTFIX 36): a log cluster is only felled when its connected
 *  component contains LEAVES (a real tree crown) and NO player-placed logs/leaves.
 *  Shipwrecks, village houses and player log builds have no natural crown and are
 *  never felled (placed blocks are tracked from BlockPlaceEvent). */
public final class Timber extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private boolean enabled = true;
    private int maxLogs = 10;
    private int maxBlocks = 200;      // BFS walk safety cap (crown search, not the fell cap)
    private boolean axeDamage = true;
    private boolean dropAll = true;
    private double xpPerLog = 0.5;
    private double xpCap = 10.0;
    private boolean leavesFall = true;
    private int leavesMax = 750;
    private boolean naturalOnly = true;   // HOTFIX 36: fell grown trees only
    private final Set<String> placed = new HashSet<>();   // "world,x,y,z" of player-placed logs/leaves
    private final Map<UUID, Boolean> toggles = new HashMap<>();
    private final Random rnd = new Random();
    private boolean placedDirty = false;
    private File dataFile;
    private YamlConfiguration data;

    private static boolean isLog(Material m) { return Tag.LOGS.isTagged(m); }
    private static boolean isLeaves(Material m) { return Tag.LEAVES.isTagged(m); }
    private static boolean isAxe(Material m) {
        return m == Material.WOODEN_AXE || m == Material.STONE_AXE || m == Material.IRON_AXE
                || m == Material.GOLDEN_AXE || m == Material.DIAMOND_AXE || m == Material.NETHERITE_AXE;
    }

    private static String key(Block b) {
        return b.getWorld().getName() + "," + b.getX() + "," + b.getY() + "," + b.getZ();
    }
    private static String key(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();                       // adds new keys (max-logs, xp caps, leaves, natural-only) to an existing config
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        enabled = getConfig().getBoolean("enabled", true);
        maxLogs = Math.max(2, getConfig().getInt("max-logs", 10));   // anti-exploit hard cap
        maxBlocks = Math.max(maxLogs, getConfig().getInt("max-blocks", 200));
        axeDamage = getConfig().getBoolean("axe-damage", true);
        dropAll = getConfig().getBoolean("drop-all", true);
        xpPerLog = Math.max(0.0, getConfig().getDouble("xp-per-log", 0.5));
        xpCap = Math.max(0.0, getConfig().getDouble("xp-cap-per-tree", 10.0));
        leavesFall = getConfig().getBoolean("leaves-fall", true);
        leavesMax = Math.max(50, getConfig().getInt("leaves-max", 750));
        naturalOnly = getConfig().getBoolean("natural-trees-only", true);
        ConfigurationSection t = data.getConfigurationSection("toggles");
        if (t != null) for (String k : t.getKeys(false)) toggles.put(UUID.fromString(k), t.getBoolean(k));
        ConfigurationSection pl = data.getConfigurationSection("placed");
        if (pl != null) placed.addAll(pl.getKeys(false));
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOTimber v" + getDescription().getVersion() + " enabled - max "
                + maxLogs + " logs/tree, XP cap " + xpCap + " per tree, leaves "
                + (leavesFall ? "decay" : "stay") + ", " + (naturalOnly ? "GROWN TREES ONLY" : "any logs")
                + ".");
    }

    @Override public void onDisable() { saveData(); }
    private void saveData() {
        // persist the placed-block tracker so the grown-tree guard survives restarts
        if (placedDirty) {
            data.set("placed", null);
            for (String k : placed) data.set("placed." + k, true);
            placedDirty = false;
        }
        try { data.save(dataFile); } catch (Throwable ignored) { }
    }
    private void placedChanged() {
        placedDirty = true;
        Bukkit.getScheduler().runTaskLater(this, this::saveData, 100L);   // debounce disk writes
    }

    private boolean on(UUID u) { return toggles.getOrDefault(u, true); }

    // ---------------- HOTFIX 36: track player-placed logs/leaves ----------------
    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Material m = e.getBlockPlaced().getType();
        if ((isLog(m) || isLeaves(m)) && placed.add(key(e.getBlockPlaced()))) placedChanged();
    }

    private void tree(Block b, Player p) {
        // Walk the component: collect up to maxLogs logs (hard cap) + the connected
        // crown leaves (so they can decay). maxBlocks is only a walk safety cap.
        Set<Location> logs = new LinkedHashSet<>();
        Set<Location> leaves = new LinkedHashSet<>();
        Set<Location> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(b);
        int steps = 0;
        boolean natural = true;      // becomes false if the component contains a placed block
        boolean hasLeaves = false;   // a grown tree must have a crown
        while (!queue.isEmpty() && steps < Math.max(200, maxBlocks + leavesMax)) {
            Block cur = queue.poll();
            Location loc = cur.getLocation();
            if (!visited.add(loc)) continue;
            steps++;
            if (naturalOnly && placed.contains(key(cur))) { natural = false; continue; }
            if (isLeaves(cur.getType())) {
                hasLeaves = true;
                if (leavesFall && leaves.size() < leavesMax) leaves.add(loc);
                // keep walking from leaves even past the decay cap so the crown stays discoverable
            } else if (!isLog(cur.getType())) {
                continue;
            } else {
                // ALWAYS expand from logs even once the FELL cap is reached - otherwise the
                // leaf crown of a tall tree is never seen and it gets called "unnatural".
                if (logs.size() < maxLogs) logs.add(loc);
            }
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int dy = -1; dy <= 1; dy++) {
                        if (dx == 0 && dy == 0 && dz == 0) continue;
                        Block nb = cur.getRelative(dx, dy, dz);
                        if (isLog(nb.getType()) || isLeaves(nb.getType())) queue.add(nb);
                    }
        }
        if (logs.size() <= 1) return; // single log = no tree
        // HOTFIX 36: only fell GROWN trees - a natural tree always has a crown and
        // no player-placed blocks. Shipwrecks/village houses/player log builds fail
        // one of these checks and just break like vanilla (single log).
        if (naturalOnly && (!natural || !hasLeaves)) {
            if (!hasLeaves) p.sendMessage(C + "8That's not a grown tree - Timber only fells natural trees (no builds/shipwrecks).");
            else p.sendMessage(C + "8Timber skips player-built wood - plant saplings for real trees.");
            return;
        }
        // break the other logs (the clicked one broke via the event flow)
        List<ItemStack> drops = new ArrayList<>();
        Location dropSpot = b.getLocation().clone().add(0.5, 0.4, 0.5);
        for (Location l : logs) {
            Block log = l.getBlock();
            if (log.equals(b)) continue;
            drops.addAll(log.getDrops());
            if (placed.remove(key(log))) placedChanged();
            log.setType(Material.AIR, false);
        }
        if (dropAll || logs.size() > 2) {
            for (ItemStack it : drops) b.getWorld().dropItemNaturally(dropSpot, it);
        }
        // durability: one point per extra log
        if (axeDamage) {
            ItemStack axe = p.getInventory().getItemInMainHand();
            if (isAxe(axe.getType()) && axe.getItemMeta() instanceof org.bukkit.inventory.meta.Damageable d) {
                d.setDamage(d.getDamage() + Math.max(0, logs.size() - 1));
                axe.setItemMeta(d);
            }
        }
        // Lumberjack XP (best-effort reflection into MAVOProfessions) - capped per tree
        if (xpCap > 0 && xpPerLog > 0 && logs.size() > 1) {
            try {
                org.bukkit.plugin.Plugin prof = Bukkit.getPluginManager().getPlugin("MAVOProfessions");
                if (prof != null) prof.getClass().getMethod("externalXp", Player.class, String.class, double.class)
                        .invoke(prof, p, "lumberjack", Math.min(xpCap, (logs.size() - 1) * xpPerLog));
            } catch (Throwable ignored) { }
        }
        // leaves decay shortly after the trunk falls (drops saplings/sticks)
        if (leavesFall && !leaves.isEmpty()) decay(leaves, b.getWorld());
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_WOOD_BREAK, 1f, 1.1f);
        p.sendMessage(C + "aTree felled - " + C + "e" + (logs.size() - 1) + C + "a logs"
                + (logs.size() >= maxLogs ? C + "8 (tree max " + maxLogs + ")" : "") + ".");
    }

    /** Remove crown leaves a moment after the fall, dropping sticks/saplings. */
    private void decay(Set<Location> leaves, org.bukkit.World world) {
        List<Location> list = new ArrayList<>(leaves);
        final int[] i = {0};
        final org.bukkit.scheduler.BukkitTask[] task = new org.bukkit.scheduler.BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(this, () -> {
            int done = 0;
            while (i[0] < list.size() && done < 60) {
                Location l = list.get(i[0]++);
                if (l.getBlock().getType() != Material.AIR && isLeaves(l.getBlock().getType())) {
                    Material mat = l.getBlock().getType();
                    Location drop = l.clone().add(0.5, 0.3, 0.5);
                    if (rnd.nextDouble() < 0.05) {
                        Material sap = saplingOf(mat);
                        if (sap != null) world.dropItemNaturally(drop, new ItemStack(sap));
                    } else if (rnd.nextDouble() < 0.02) {
                        world.dropItemNaturally(drop, new ItemStack(Material.STICK));
                    }
                    if (placed.remove(key(l))) placedChanged();
                    l.getBlock().setType(Material.AIR, false);
                }
                done++;
            }
            if (i[0] >= list.size() && task[0] != null) task[0].cancel();
        }, 10L, 1L);
    }

    private static Material saplingOf(Material leaves) {
        return switch (leaves) {
            case OAK_LEAVES -> Material.OAK_SAPLING;
            case SPRUCE_LEAVES -> Material.SPRUCE_SAPLING;
            case BIRCH_LEAVES -> Material.BIRCH_SAPLING;
            case JUNGLE_LEAVES -> Material.JUNGLE_SAPLING;
            case ACACIA_LEAVES -> Material.ACACIA_SAPLING;
            case DARK_OAK_LEAVES -> Material.DARK_OAK_SAPLING;
            case CHERRY_LEAVES -> Material.CHERRY_SAPLING;
            case MANGROVE_LEAVES -> Material.MANGROVE_PROPAGULE;
            case AZALEA_LEAVES, FLOWERING_AZALEA_LEAVES -> null;
            default -> null;
        };
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        // keep the placed-block tracker in sync however the block disappears
        if ((isLog(e.getBlock().getType()) || isLeaves(e.getBlock().getType()))
                && placed.remove(key(e.getBlock()))) placedChanged();
        if (!enabled || !on(e.getPlayer().getUniqueId())) return;
        if (e.getPlayer().getGameMode() != org.bukkit.GameMode.SURVIVAL) return;
        if (!isLog(e.getBlock().getType())) return;
        if (!isAxe(e.getPlayer().getInventory().getItemInMainHand().getType())) return;
        tree(e.getBlock(), e.getPlayer());
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        return a.length == 1 ? List.of("status", "toggle") : List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> p.sendMessage(C + "7Tree felling: " + (on(p.getUniqueId()) ? C + "aON" : C + "cOFF")
                    + C + "8 | max " + maxLogs + " logs/tree | XP cap " + xpCap + " per tree | "
                    + (naturalOnly ? "grown trees only" : "any logs")
                    + C + "8 | leaves " + (leavesFall ? "fall" : "stay") + ".");
            case "toggle" -> {
                boolean now = !on(p.getUniqueId());
                toggles.put(p.getUniqueId(), now);
                data.set("toggles." + p.getUniqueId(), now);
                saveData();
                p.sendMessage(C + "aTree felling " + (now ? C + "aON" : C + "cOFF") + C + "a.");
            }
            default -> p.sendMessage(C + "7/timber status | toggle");
        }
        return true;
    }
}
