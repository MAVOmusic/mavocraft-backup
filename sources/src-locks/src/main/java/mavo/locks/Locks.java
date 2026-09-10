package mavo.locks;

import java.io.File;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** MAVOLocks 1.0.0 - chest/door locks (Discord CW#4 idea 12, inspired by LWC/ChestLock).
 *  /lock a container or door, /trust a friend, /unlock to free it. Locked blocks are
 *  only usable by owner + trusted; breaking blocked; explosions protected. */
public final class Locks extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private File dataFile;
    private YamlConfiguration data;
    private int maxLocks = 50;
    private boolean protectExplosion = true;
    private boolean protectHopperPiston = true;   // 3.0.5: hoppers can't drain, pistons can't push locked blocks

    private static final List<Material> LOCKABLE = List.of(
            Material.CHEST, Material.TRAPPED_CHEST, Material.BARREL, Material.SHULKER_BOX,
            Material.ENDER_CHEST, Material.FURNACE, Material.BLAST_FURNACE, Material.SMOKER,
            Material.HOPPER, Material.DROPPER, Material.DISPENSER,
            Material.OAK_DOOR, Material.SPRUCE_DOOR, Material.BIRCH_DOOR, Material.JUNGLE_DOOR,
            Material.ACACIA_DOOR, Material.DARK_OAK_DOOR, Material.CHERRY_DOOR,
            Material.OAK_TRAPDOOR, Material.SPRUCE_TRAPDOOR, Material.BIRCH_TRAPDOOR,
            Material.OAK_FENCE_GATE, Material.SPRUCE_FENCE_GATE, Material.BIRCH_FENCE_GATE);

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        maxLocks = Math.max(1, getConfig().getInt("max-locks-per-player", 50));
        protectExplosion = getConfig().getBoolean("protect-from-explosion", true);
        protectHopperPiston = getConfig().getBoolean("protect-hopper-piston", true);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOLocks v" + getDescription().getVersion() + " enabled - " + lockCount()
                + " lock(s) loaded.");
    }

    @Override public void onDisable() { saveData(); }
    private void saveData() { try { data.save(dataFile); } catch (Throwable ignored) { } }
    private int lockCount() {
        ConfigurationSection s = data.getConfigurationSection("locks");
        return s == null ? 0 : s.getKeys(false).size();
    }

    private static String key(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    private boolean lockable(Material m) { return LOCKABLE.contains(m); }

    private UUID ownerOf(Location l) {
        if (l == null) return null;
        String o = data.getString("locks." + key(l) + ".owner", "");
        try { return o.isEmpty() ? null : UUID.fromString(o); }
        catch (Throwable t) { return null; }
    }
    private Set<UUID> trustedOf(Location l) {
        Set<UUID> out = new LinkedHashSet<>();
        ConfigurationSection s = data.getConfigurationSection("locks." + key(l) + ".trusted");
        if (s != null) for (String k : s.getKeys(false))
            try { out.add(UUID.fromString(k)); } catch (Throwable ignored) { }
        return out;
    }
    private boolean canUse(Player p, Location l) {
        if (p.hasPermission("mavolock.admin")) return true;
        UUID o = ownerOf(l);
        if (o == null) return true;
        if (o.equals(p.getUniqueId())) return true;
        return trustedOf(l).contains(p.getUniqueId());
    }

    private int locksBy(UUID u) {
        ConfigurationSection s = data.getConfigurationSection("locks");
        if (s == null) return 0;
        int n = 0;
        for (String k : s.getKeys(false))
            if (u.toString().equals(s.getString(k + ".owner", ""))) n++;
        return n;
    }

    /** Blocks that share one lock: both halves of a double chest and both parts of a door. */
    private List<Block> linked(Block b) {
        List<Block> out = new ArrayList<>();
        out.add(b);
        Material t = b.getType();
        boolean chest = t == Material.CHEST || t == Material.TRAPPED_CHEST;
        boolean door = t.name().endsWith("_DOOR");
        boolean trapdoor = t.name().endsWith("_TRAPDOOR");
        if (chest) {
            for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
                if (dx == 0 && dz == 0) continue;
                Block nb = b.getRelative(dx, 0, dz);
                if (nb.getType() == t && !out.contains(nb)) out.add(nb);
            }
        } else if (door || trapdoor) {
            Block above = b.getRelative(0, 1, 0), below = b.getRelative(0, -1, 0);
            if (above.getType() == t && !out.contains(above)) out.add(above);
            if (below.getType() == t && !out.contains(below)) out.add(below);
        }
        return out;
    }

    /** Locks of every linked block with the same owner (e.g. both chest halves). */
    private List<Location> sameLock(Block b) {
        List<Location> out = new ArrayList<>();
        List<Block> blocks = linked(b);
        if (blocks.size() > 1) {
            boolean allIn = true;
            for (Block nb : blocks) if (ownerOf(nb.getLocation()) == null) { allIn = false; break; }
            if (allIn) return blocks.stream().map(x -> x.getLocation()).toList();
        }
        out.add(b.getLocation());
        return out;
    }

    private void toggleLock(Player p, Block b, boolean lock) {
        if (!lockable(b.getType())) {
            p.sendMessage(C + "cThat block can't be locked (" + b.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ").");
            return;
        }
        List<Block> blocks = linked(b);
        if (lock) {
            UUID o = ownerOf(b.getLocation());
            if (o != null) {
                if (o.equals(p.getUniqueId()) || p.hasPermission("mavolock.admin"))
                    p.sendMessage(C + "7Already locked (by you).");
                else p.sendMessage(C + "cThat block is already locked by someone else.");
                return;
            }
            // a linked half locked by someone else? abort
            for (Block nb : blocks) {
                UUID no = ownerOf(nb.getLocation());
                if (no != null && !no.equals(p.getUniqueId()) && !p.hasPermission("mavolock.admin")) {
                    p.sendMessage(C + "cOne half of that is locked by someone else.");
                    return;
                }
            }
            if (!p.hasPermission("mavolock.admin") && locksBy(p.getUniqueId()) + (blocks.size() - 1) > maxLocks) {
                p.sendMessage(C + "cLock limit reached (" + maxLocks + "). /unlock something first.");
                return;
            }
            for (Block nb : blocks) data.set("locks." + key(nb.getLocation()) + ".owner", p.getUniqueId().toString());
            saveData();
            p.sendMessage(C + "a\uD83D\uDD12 Locked!" + (blocks.size() > 1 ? C + "7 (both halves)" : "")
                    + C + "7 You, /trust <player> and admins can use it.");
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_LOCKED, 1f, 1f);
        } else {
            List<Location> shares = sameLock(b);
            UUID o = ownerOf(b.getLocation());
            if (o == null) { p.sendMessage(C + "7Not locked."); return; }
            if (!o.equals(p.getUniqueId()) && !p.hasPermission("mavolock.admin")) {
                p.sendMessage(C + "cOnly the owner can unlock this."); return;
            }
            for (Location l : shares) data.set("locks." + key(l), null);
            saveData();
            p.sendMessage(C + "a\uD83D\uDD13 Unlocked" + (shares.size() > 1 ? " (both halves)" : "") + ".");
            p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1f);
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() == Action.PHYSICAL) return;
        Block b = e.getClickedBlock();
        if (b == null || !lockable(b.getType())) return;
        UUID o = ownerOf(b.getLocation());
        if (o == null) return;
        if (canUse(e.getPlayer(), b.getLocation())) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(C + "c\uD83D\uDD12 Locked by " + C + "e"
                + niceName(Bukkit.getOfflinePlayer(o).getName()) + C + "c.");
        e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.9f);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        UUID o = ownerOf(e.getBlock().getLocation());
        if (o == null) return;
        if (!e.getPlayer().getUniqueId().equals(o) && !e.getPlayer().hasPermission("mavolock.admin")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C + "c\uD83D\uDD12 You can't break this - it is locked.");
        } else {
            // owner breaks it: free the whole lock (both halves / both door parts)
            for (Location l : sameLock(e.getBlock())) data.set("locks." + key(l), null);
            saveData();
        }
    }

    @EventHandler public void onExplode(BlockExplodeEvent e) {
        if (protectExplosion) e.blockList().removeIf(b -> ownerOf(b.getLocation()) != null);
    }

    /** 3.0.5: hoppers (and any inventory mover) can't pull from or push into a locked block. */
    @EventHandler
    public void onMoveItem(InventoryMoveItemEvent e) {
        if (!protectHopperPiston) return;
        try {
            if (ownerOf(e.getSource().getLocation()) != null) { e.setCancelled(true); return; }
        } catch (Throwable ignored) { }
        try {
            if (ownerOf(e.getDestination().getLocation()) != null) e.setCancelled(true);
        } catch (Throwable ignored) { }
    }

    /** 3.0.5: pistons can't push or pull locked blocks (no push-dupe tricks). */
    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        if (!protectHopperPiston) return;
        for (Block b : e.getBlocks())
            if (ownerOf(b.getLocation()) != null) { e.setCancelled(true); return; }
    }
    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        if (!protectHopperPiston) return;
        for (Block b : e.getBlocks())
            if (ownerOf(b.getLocation()) != null) { e.setCancelled(true); return; }
    }
    @EventHandler public void onExplode(EntityExplodeEvent e) {
        if (protectExplosion) e.blockList().removeIf(b -> ownerOf(b.getLocation()) != null);
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command c, String l, String[] a) {
        if (a.length == 1 && (c.getName().equalsIgnoreCase("trust") || c.getName().equalsIgnoreCase("untrust")))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
        String n = cmd.getName().toLowerCase(Locale.ROOT);
        switch (n) {
            case "lock", "unlock" -> {
                Block b = p.getTargetBlockExact(6);
                if (b == null) { p.sendMessage(C + "cLook at a chest, door or furnace within 6 blocks."); return true; }
                toggleLock(p, b, "lock".equals(n));
            }
            case "trust", "untrust" -> {
                if (args.length < 1) { p.sendMessage(C + "cUsage: /" + n + " <player>"); return true; }
                Block b = p.getTargetBlockExact(6);
                if (b == null) { p.sendMessage(C + "cLook at your locked block first, then /" + n + " <player>."); return true; }
                UUID o = ownerOf(b.getLocation());
                if (o == null || !o.equals(p.getUniqueId()) && !p.hasPermission("mavolock.admin")) {
                    p.sendMessage(C + "cLook at YOUR locked block first, then /" + n + " <player>.");
                    return true;
                }
                UUID t = Bukkit.getOfflinePlayerIfCached(args[0]) != null ? Bukkit.getOfflinePlayerIfCached(args[0]).getUniqueId() : Bukkit.getOfflinePlayer(args[0]).getUniqueId();
                String k = key(b.getLocation());
                if ("trust".equals(n)) {
                    data.set("locks." + k + ".trusted." + t, true);
                    p.sendMessage(C + "a" + args[0] + " can now use this block.");
                } else {
                    data.set("locks." + k + ".trusted." + t, null);
                    p.sendMessage(C + "a" + args[0] + " was untrusted.");
                }
                saveData();
            }
            default -> p.sendMessage(C + "7/lock | /unlock | /trust <player> | /untrust <player>");
        }
        return true;
    }

    private static String niceName(String s) { return s == null ? "?" : s; }
}
