package mavo.warps;

import java.io.File;
import java.util.*;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** MAVOWarps 1.0.0 - public player warps (Discord CW#3 idea 1).
 *  Buy a warp slot (coins), register your base, the whole server can /warp there
 *  with the standard 3s stand-still safety (no escaping fights). */
public final class Warps extends JavaPlugin implements Listener {

    private static final ChatColor D = ChatColor.DARK_AQUA, G = ChatColor.GREEN,
            R = ChatColor.RED, Y = ChatColor.YELLOW, GRAY = ChatColor.GRAY, GOLD = ChatColor.GOLD;

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, BukkitTask> warmups = new HashMap<>();
    private final Map<UUID, Long> cooldown = new HashMap<>();

    // ---------------- lifecycle ----------------
    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOWarps v" + getDescription().getVersion() + " enabled - " + warpCount()
                + " warp(s) loaded.");
    }

    @Override
    public void onDisable() {
        for (BukkitTask t : warmups.values()) t.cancel();
        warmups.clear();
        saveData();
    }

    private void saveData() { try { data.save(dataFile); } catch (Exception ignored) {} }

    private int warpCount() {
        ConfigurationSection s = data.getConfigurationSection("warps");
        return s == null ? 0 : s.getKeys(false).size();
    }

    // ---------------- helpers ----------------
    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private static String fmt(long n) { return String.format("%,d", n); }

    private List<String> warpIds() {
        List<String> out = new ArrayList<>();
        ConfigurationSection s = data.getConfigurationSection("warps");
        if (s != null) out.addAll(s.getKeys(false));
        out.sort(String::compareTo);
        return out;
    }

    private Location warpLoc(String id) {
        try {
            World w = Bukkit.getWorld(data.getString("warps." + id + ".world", ""));
            if (w == null) return null;
            return new Location(w, data.getDouble("warps." + id + ".x"), data.getDouble("warps." + id + ".y"),
                    data.getDouble("warps." + id + ".z"));
        } catch (Exception ex) { return null; }
    }

    private String ownerName(String id) {
        try {
            UUID o = UUID.fromString(data.getString("warps." + id + ".owner", ""));
            String n = Bukkit.getOfflinePlayer(o).getName();
            return n == null ? o.toString().substring(0, 8) : n;
        } catch (Exception ex) { return "?"; }
    }

    private boolean monstersNear(Player pl) {
        if (pl.getGameMode() != org.bukkit.GameMode.SURVIVAL) return false;
        int r = getConfig().getInt("monster-radius", 12);
        for (Entity en : pl.getNearbyEntities(r, Math.max(6, r / 2.0), r))
            if (en instanceof Enemy && !en.isDead()) return true;
        return false;
    }

    private int slotsUsed(UUID u) {
        int n = 0;
        for (String id : warpIds())
            if (data.getString("warps." + id + ".owner", "").equalsIgnoreCase(u.toString())) n++;
        return n;
    }

    private long slotCost(int slotIndex) {
        if (slotIndex == 0) return Math.max(1L, getConfig().getLong("cost-create", 25_000L));
        List<Integer> extra = getConfig().getIntegerList("extra-warp-costs");
        if (extra == null || extra.isEmpty()) return -1;
        int i = slotIndex - 1;
        return i < extra.size() ? extra.get(i) : -1;
    }

    private Location safeTeleportLoc(Location loc) {
        Location l = loc.clone();
        World w = l.getWorld();
        if (w == null) return null;
        l.getChunk().load();
        for (int up = 0; up < 4; up++) {
            Location c = l.clone().add(0, up, 0);
            if (!c.getBlock().getType().isSolid()) return c;
        }
        // landing on top of whatever is there
        return w.getHighestBlockAt(l).getLocation().add(0.5, 1, 0.5);
    }

    private void startWarmup(Player pl, String label, Runnable action) {
        if (monstersNear(pl)) {
            pl.sendMessage(R + "⚔ Monsters nearby - can't teleport! Fight or run first.");
            return;
        }
        BukkitTask old = warmups.remove(pl.getUniqueId());
        if (old != null) old.cancel();
        final int total = Math.max(1, getConfig().getInt("warmup-seconds", 3));
        final Location start = pl.getLocation().clone();
        pl.sendTitle(D + "" + ChatColor.BOLD + total, GRAY + "Stand still - teleporting to " + label, 0, 25, 5);
        pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
        final int[] ticksLeft = {total * 20};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!pl.isOnline()) { BukkitTask t = warmups.remove(pl.getUniqueId()); if (t != null) t.cancel(); return; }
            if (!pl.getWorld().equals(start.getWorld()) || pl.getLocation().distanceSquared(start) > 0.5) {
                BukkitTask t = warmups.remove(pl.getUniqueId()); if (t != null) t.cancel();
                pl.sendTitle(" ", "", 0, 1, 0);
                pl.sendMessage(R + "Teleport cancelled - you moved.");
                return;
            }
            if (monstersNear(pl)) {
                BukkitTask t = warmups.remove(pl.getUniqueId()); if (t != null) t.cancel();
                pl.sendTitle(" ", "", 0, 1, 0);
                pl.sendMessage(R + "⚔ Monsters nearby - teleport cancelled! No escaping a fight.");
                return;
            }
            ticksLeft[0] -= 5;
            if (ticksLeft[0] > 0) {
                if (ticksLeft[0] % 20 == 0) {
                    int sec = ticksLeft[0] / 20;
                    pl.sendTitle(D + "" + ChatColor.BOLD + sec, GRAY + "Stand still - teleporting to " + label, 0, 25, 5);
                    pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f + (total - sec) * 0.3f);
                }
                return;
            }
            BukkitTask t = warmups.remove(pl.getUniqueId()); if (t != null) t.cancel();
            pl.sendTitle(" ", "", 0, 1, 0);
            action.run();
        }, 5L, 5L);
        warmups.put(pl.getUniqueId(), task);
    }

    // ---------------- commands ----------------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("tp") && args[1].length() > 0) {
                Location l = warpLoc(args[1].toLowerCase(Locale.ROOT));
                sender.sendMessage(l == null ? R + "No such warp." : G + args[1] + " -> " + l.getWorld().getName()
                        + " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ());
                return true;
            }
            sender.sendMessage(R + "In-game only.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("warps")) { openList(p, 0); return true; }
        if (args.length == 0) {
            p.sendMessage(GOLD + "" + ChatColor.BOLD + "=== WARPS ===");
            p.sendMessage(GRAY + "/warp <name> - teleport (3s, monsters block it)");
            p.sendMessage(GRAY + "/warps - public list");
            p.sendMessage(GRAY + "/warp create <name> - buy a warp point here (" + fmt(slotCost(slotsUsed(p.getUniqueId()))) + " coins)");
            p.sendMessage(GRAY + "/warp remove <name> | rename <old> <new> - owner only");
            p.sendMessage(GRAY + "Slots used: " + Y + slotsUsed(p.getUniqueId()) + GRAY + " (next: "
                    + (slotCost(slotsUsed(p.getUniqueId())) > 0 ? fmt(slotCost(slotsUsed(p.getUniqueId()))) : "no more") + ")");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> createWarp(p, args);
            case "remove" -> removeWarp(p, args);
            case "rename" -> renameWarp(p, args);
            case "info" -> infoWarp(p, args);
            case "delete" -> {
                if (!p.hasPermission("mavowarps.admin")) { p.sendMessage(R + "No permission."); return true; }
                if (args.length < 2) { p.sendMessage(R + "/warp delete <name>"); return true; }
                String id = args[1].toLowerCase(Locale.ROOT);
                if (!data.contains("warps." + id)) { p.sendMessage(R + "No warp named " + id + "."); return true; }
                data.set("warps." + id, null); saveData();
                p.sendMessage(G + "Warp " + id + " deleted.");
            }
            default -> teleportWarp(p, args[0].toLowerCase(Locale.ROOT));
        }
        return true;
    }

    private void createWarp(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(R + "/warp create <name>"); return; }
        String id = args[1].toLowerCase(Locale.ROOT).replace(" ", "_");
        if (!id.matches("[a-z0-9_\\-]{3,20}")) {
            p.sendMessage(R + "Name: 3-20 chars, letters/numbers/_/- only.");
            return;
        }
        if (data.contains("warps." + id)) { p.sendMessage(R + "A warp named " + id + " already exists."); return; }
        int slot = slotsUsed(p.getUniqueId());
        long cost = slotCost(slot);
        if (cost < 0) { p.sendMessage(R + "No warp slots left for you."); return; }
        if (econ != null && p.getGameMode() == org.bukkit.GameMode.SURVIVAL) {
            if (!econ.has(p, cost)) { p.sendMessage(R + "Need " + fmt(cost) + " coins for warp slot " + (slot + 1) + "."); return; }
            econ.withdrawPlayer(p, cost);
        }
        Location l = p.getLocation();
        data.set("warps." + id + ".owner", p.getUniqueId().toString());
        data.set("warps." + id + ".world", l.getWorld().getName());
        data.set("warps." + id + ".x", l.getBlockX() + 0.5);
        data.set("warps." + id + ".y", l.getBlockY());
        data.set("warps." + id + ".z", l.getBlockZ() + 0.5);
        data.set("warps." + id + ".created", System.currentTimeMillis());
        data.set("warps." + id + ".visits", 0);
        saveData();
        p.sendMessage(D + "Warp " + G + id + D + " created" + (cost > 0 ? " (paid " + fmt(cost) + ")" : "")
                + GRAY + " - everyone can /warp " + id + " now.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
    }

    private void removeWarp(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(R + "/warp remove <name>"); return; }
        String id = args[1].toLowerCase(Locale.ROOT);
        if (!data.contains("warps." + id)) { p.sendMessage(R + "No warp named " + id + "."); return; }
        if (!owner(id, p.getUniqueId()) && !p.hasPermission("mavowarps.admin")) {
            p.sendMessage(R + "Only the owner can remove " + id + "."); return;
        }
        data.set("warps." + id, null); saveData();
        p.sendMessage(Y + "Warp " + id + " removed (no refund).");
    }

    private void renameWarp(Player p, String[] args) {
        if (args.length < 3) { p.sendMessage(R + "/warp rename <old> <new>"); return; }
        String oldId = args[1].toLowerCase(Locale.ROOT), newId = args[2].toLowerCase(Locale.ROOT).replace(" ", "_");
        if (!newId.matches("[a-z0-9_\\-]{3,20}")) { p.sendMessage(R + "New name: 3-20 chars, letters/numbers/_/- only."); return; }
        if (!data.contains("warps." + oldId)) { p.sendMessage(R + "No warp named " + oldId + "."); return; }
        if (data.contains("warps." + newId)) { p.sendMessage(R + newId + " already exists."); return; }
        if (!owner(oldId, p.getUniqueId()) && !p.hasPermission("mavowarps.admin")) {
            p.sendMessage(R + "Only the owner can rename " + oldId + "."); return;
        }
        for (String k : data.getConfigurationSection("warps." + oldId).getKeys(true))
            data.set("warps." + newId + "." + k, data.get("warps." + oldId + "." + k));
        data.set("warps." + oldId, null); saveData();
        p.sendMessage(G + "Warp renamed: " + oldId + " -> " + newId + ".");
    }

    private void infoWarp(Player p, String[] args) {
        String id = args.length < 2 ? null : args[1].toLowerCase(Locale.ROOT);
        if (id == null || !data.contains("warps." + id)) { p.sendMessage(R + "Usage: /warp info <name>"); return; }
        Location l = warpLoc(id);
        p.sendMessage(GOLD + "" + ChatColor.BOLD + id + " (" + ownerName(id) + ")");
        p.sendMessage(GRAY + "at " + (l == null ? "?" : l.getWorld().getName())
                + (l == null ? "" : " " + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ())
                + " - visits " + data.getInt("warps." + id + ".visits", 0));
    }

    private void teleportWarp(Player p, String id) {
        if (!data.contains("warps." + id)) { p.sendMessage(R + "No warp named " + id + ". /warps"); return; }
        long now = System.currentTimeMillis();
        long cd = getConfig().getLong("cooldown-seconds", 30) * 1000L;
        Long last = cooldown.get(p.getUniqueId());
        if (last != null && now - last < cd) {
            p.sendMessage(GRAY + "Warp cooldown: " + Y + ((cd - (now - last)) / 1000L) + "s");
            return;
        }
        startWarmup(p, id, () -> {
            Location l = safeTeleportLoc(warpLoc(id));
            if (l == null) { p.sendMessage(R + "Warp is in an unloaded world."); return; }
            cooldown.put(p.getUniqueId(), System.currentTimeMillis());
            data.set("warps." + id + ".visits", data.getInt("warps." + id + ".visits", 0) + 1);
            saveData();
            p.teleport(l);
            p.sendMessage(D + "Warped to " + G + id + D + " (" + ownerName(id) + ").");
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.5f, 1.4f);
        });
    }

    private boolean owner(String id, UUID u) {
        return data.getString("warps." + id + ".owner", "").equalsIgnoreCase(u.toString());
    }

    // ---------------- /warps list GUI ----------------
    private void openList(Player p, int page) {
        List<String> ids = warpIds();
        int per = Math.min(45, Math.max(9, getConfig().getInt("max-per-page", 45)));
        int pages = Math.max(1, (ids.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(null, 54,
                D + "" + ChatColor.BOLD + "Warps — page " + (page + 1) + "/" + pages);
        int slot = 0;
        for (int i = page * per; i < Math.min(ids.size(), page * per + per); i++) {
            String id = ids.get(i);
            Location l = warpLoc(id);
            ItemStack it = new ItemStack(Material.OAK_SIGN);
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(GOLD + "" + ChatColor.BOLD + id);
            List<String> lore = new ArrayList<>();
            lore.add(GRAY + "Owner: " + ownerName(id));
            lore.add(GRAY + (l == null ? "?" : l.getWorld().getName() + " "
                    + l.getBlockX() + " " + l.getBlockY() + " " + l.getBlockZ()));
            lore.add(GRAY + "Visits: " + data.getInt("warps." + id + ".visits", 0));
            lore.add(D + "Click to teleport (3s, monsters block it)");
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(slot++, it);
        }
        if (page > 0) {
            ItemStack b = new ItemStack(Material.ARROW);
            ItemMeta bm = b.getItemMeta(); bm.setDisplayName(GOLD + "◀ Previous"); b.setItemMeta(bm);
            inv.setItem(48, b);
        }
        if (page < pages - 1) {
            ItemStack b = new ItemStack(Material.ARROW);
            ItemMeta bm = b.getItemMeta(); bm.setDisplayName(GOLD + "Next ▶"); b.setItemMeta(bm);
            inv.setItem(50, b);
        }
        if (ids.isEmpty()) {
            ItemStack b = new ItemStack(Material.BARRIER);
            ItemMeta bm = b.getItemMeta(); bm.setDisplayName(R + "No public warps yet");
            bm.setLore(List.of(GRAY + "Buy one: /warp create <name> (25,000 coins)"));
            b.setItemMeta(bm);
            inv.setItem(22, b);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onListClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String t = e.getView().getTitle();
        if (t == null || !t.contains("Warps")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType().isAir()) return;
        ItemMeta m = e.getCurrentItem().getItemMeta();
        if (m == null || !m.hasDisplayName()) return;
        String name = ChatColor.stripColor(m.getDisplayName());
        if (name.contains("Previous")) { p.closeInventory(); openList(p, pageOf(t) - 1); return; }
        if (name.contains("Next")) { p.closeInventory(); openList(p, pageOf(t) + 1); return; }
        if (name.startsWith("No public")) return;
        p.closeInventory();
        teleportWarp(p, name.toLowerCase(Locale.ROOT));
    }

    private int pageOf(String title) {
        try {
            String[] parts = title.split("page ");
            if (parts.length < 2) return 0;
            return Integer.parseInt(parts[1].split("/")[0]) - 1;
        } catch (Exception ex) { return 0; }
    }

}

