package mavo.timber;

import java.io.File;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
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
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/** MAVOTimber 1.0.0 - tree felling (Discord CW#4 idea 13, inspired by UltimateTimber).
 *  Break the bottom log of a tree and every connected log (up to `max-blocks`) breaks
 *  at once; drops land at the trunk; one axe durability per log; feeds Lumberjack XP.
 *  Per-player /timber toggle (on by default). */
public final class Timber extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private boolean enabled = true;
    private int maxBlocks = 200;
    private boolean axeDamage = true;
    private boolean dropAll = true;
    private final Map<UUID, Boolean> toggles = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;

    private static boolean isLog(Material m) { return Tag.LOGS.isTagged(m); }
    private static boolean isLeaves(Material m) { return Tag.LEAVES.isTagged(m); }
    private static boolean isAxe(Material m) {
        return m == Material.WOODEN_AXE || m == Material.STONE_AXE || m == Material.IRON_AXE
                || m == Material.GOLDEN_AXE || m == Material.DIAMOND_AXE || m == Material.NETHERITE_AXE;
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        enabled = getConfig().getBoolean("enabled", true);
        maxBlocks = Math.max(10, getConfig().getInt("max-blocks", 200));
        axeDamage = getConfig().getBoolean("axe-damage", true);
        dropAll = getConfig().getBoolean("drop-all", true);
        ConfigurationSection t = data.getConfigurationSection("toggles");
        if (t != null) for (String k : t.getKeys(false)) toggles.put(UUID.fromString(k), t.getBoolean(k));
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOTimber v" + getDescription().getVersion() + " enabled - max tree " + maxBlocks + " logs.");
    }

    @Override public void onDisable() { saveData(); }
    private void saveData() { try { data.save(dataFile); } catch (Throwable ignored) { } }

    private boolean on(UUID u) { return toggles.getOrDefault(u, true); }

    private void tree(Block b, Player p) {
        // collect connected logs BFS (ignores leaves but passes through them for the crown)
        Set<Location> logs = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(b);
        while (!queue.isEmpty() && logs.size() < maxBlocks) {
            Block cur = queue.poll();
            if (!isLog(cur.getType()) || !logs.add(cur.getLocation())) continue;
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++)
                    for (int dy = -1; dy <= 1; dy++) {
                        Block nb = cur.getRelative(dx, dy, dz);
                        if (isLog(nb.getType()) || isLeaves(nb.getType())) queue.add(nb);
                    }
        }
        if (logs.size() <= 1) return; // single log = no tree
        // break all remaining logs (the clicked one already broke via the event flow)
        List<ItemStack> drops = new ArrayList<>();
        Location dropSpot = b.getLocation().clone().add(0.5, 0.4, 0.5);
        for (Location l : logs) {
            Block log = l.getBlock();
            if (log.equals(b)) continue;
            drops.addAll(log.getDrops());
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
        // Lumberjack XP (best-effort reflection into MAVOProfessions)
        try {
            org.bukkit.plugin.Plugin prof = Bukkit.getPluginManager().getPlugin("MAVOProfessions");
            if (prof != null) prof.getClass().getMethod("externalXp", Player.class, String.class, double.class)
                    .invoke(prof, p, "lumberjack", logs.size() * 0.5);
        } catch (Throwable ignored) { }
        p.playSound(p.getLocation(), org.bukkit.Sound.BLOCK_WOOD_BREAK, 1f, 1.1f);
        p.sendMessage(C + "aTree felled - " + C + "e" + (logs.size() - 1) + C + "a logs.");
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
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
            case "status" -> p.sendMessage(C + "7Tree felling: " + (on(p.getUniqueId()) ? C + "aON" : C + "cOFF"));
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
