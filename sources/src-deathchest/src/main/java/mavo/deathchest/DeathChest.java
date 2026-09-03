package mavo.deathchest;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class DeathChest extends JavaPlugin implements Listener {

    private NamespacedKey ownerKey, expireKey, idKey;

    private Economy econ;
    private Object lucky;                 // MAVOLuckyCoins
    private Method luckyCount, luckyTake, luckyCoinItem;

    private final Map<UUID, Pending> pending = new HashMap<>();

    private static final class Pending {
        BukkitTask task;
        Location target;
        String mode;      // coins | lucky
        double cost;
        Location start;
    }

    private static final class Holder implements InventoryHolder {
        String view = "list";             // list | confirm
        int page = 0;
        String graveKey = null;
        @Override public Inventory getInventory() { return null; }
    }

    @Override
    public void onEnable() {
        ownerKey = new NamespacedKey(this, "dcowner");
        expireKey = new NamespacedKey(this, "dcexpire");
        idKey = new NamespacedKey(this, "dcid");
        getConfig().addDefault("expire-minutes", 30);
        getConfig().addDefault("teleport-cost-coins", 5000);
        getConfig().addDefault("teleport-cost-lucky", 100);
        getConfig().addDefault("teleport-seconds", 3);
        getConfig().addDefault("monster-radius", 12);
        getConfig().options().copyDefaults(true);
        // 1.1.1 migration: old defaults 1000/10 -> 5000/100 (late-game cost; totems stay valuable)
        if (getConfig().getInt("teleport-cost-coins", -1) == 1000 && getConfig().getInt("teleport-cost-lucky", -1) == 10) {
            getConfig().set("teleport-cost-coins", 5000);
            getConfig().set("teleport-cost-lucky", 100);
            saveConfig();
            getLogger().info("Migrated grave teleport costs -> 5000 coins / 100 lucky coins.");
        }
        saveConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        try {
            var pl = Bukkit.getPluginManager().getPlugin("MAVOLuckyCoins");
            if (pl != null) {
                lucky = pl;
                luckyCount = pl.getClass().getMethod("countCoins", Player.class);
                luckyTake = pl.getClass().getMethod("takeCoins", Player.class, int.class);
                luckyCoinItem = pl.getClass().getMethod("createCoinItem", int.class);
            }
        } catch (Exception ignored) {}
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::sweep, 600L, 600L);
        getLogger().info("MAVODeathChest enabled - your loot is safe(ish). LC=" + (lucky != null));
    }

    private String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    private ItemStack item(Material m, String name, List<String> lore, String id) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(cc(name));
        if (lore != null) {
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(cc(s));
            meta.setLore(l);
        }
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        if (id != null) meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++)
            if (inv.getItem(i) == null)
                inv.setItem(i, item(Material.GRAY_STAINED_GLASS_PANE, " ", null, null));
    }

    private List<String> chests() { return getConfig().getStringList("chests"); }
    private void setChests(List<String> l) { getConfig().set("chests", l); saveConfig(); }

    private String key(Location l) { return l.getWorld().getName() + ";" + l.getBlockX() + ";" + l.getBlockY() + ";" + l.getBlockZ(); }

    private Location parse(String s) {
        try {
            String[] p = s.split(";");
            var w = Bukkit.getWorld(p[0]);
            if (w == null) return null;
            return new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3]));
        } catch (Exception ex) { return null; }
    }

    // ---------------- death ----------------
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        if (e.getKeepInventory()) return;
        List<ItemStack> drops = new ArrayList<>(e.getDrops());
        drops.removeIf(i -> i == null || i.getType() == Material.AIR);
        if (drops.isEmpty()) return;

        Player p = e.getEntity();
        Location loc = p.getLocation().clone();
        if (loc.getBlockY() < loc.getWorld().getMinHeight() + 1) loc.setY(loc.getWorld().getMinHeight() + 1);
        if (loc.getBlockY() > loc.getWorld().getMaxHeight() - 2) loc.setY(loc.getWorld().getMaxHeight() - 2);
        Block b = findSpot(loc.getBlock());
        if (b == null) return; // no safe spot: vanilla drops

        b.setType(Material.CHEST);
        if (!(b.getState() instanceof Chest chest)) { b.setType(Material.AIR); return; }
        long expireAt = System.currentTimeMillis() + getConfig().getInt("expire-minutes", 30) * 60000L;
        chest.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, p.getUniqueId().toString());
        chest.getPersistentDataContainer().set(expireKey, PersistentDataType.LONG, expireAt);
        chest.setCustomName(ChatColor.RED + p.getName() + "'s grave");
        chest.update();

        Iterator<ItemStack> it = drops.iterator();
        int slot = 0;
        List<ItemStack> overflow = new ArrayList<>();
        while (it.hasNext()) {
            ItemStack s = it.next();
            if (slot < 27) chest.getBlockInventory().setItem(slot++, s);
            else overflow.add(s);
        }
        e.getDrops().clear();
        e.getDrops().addAll(overflow); // rare: >27 stacks, remainder drops normally

        List<String> l = new ArrayList<>(chests());
        l.add(key(b.getLocation()) + ";" + p.getUniqueId());
        setChests(l);

        String where = b.getX() + ", " + b.getY() + ", " + b.getZ();
        int mins = getConfig().getInt("expire-minutes", 30);
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (p.isOnline())
                p.sendMessage(ChatColor.RED + "\u26B0 " + ChatColor.GOLD + "Your items are in a grave at "
                        + ChatColor.WHITE + where + ChatColor.GOLD + " (" + b.getWorld().getName() + ")"
                        + ChatColor.GRAY + " - only you can open it. /grave to travel. Bursts open in " + mins + " min!");
        }, 20L);
    }

    private Block findSpot(Block start) {
        if (replaceable(start)) return start;
        for (int dy = 0; dy <= 3; dy++)
            for (int dx = -1; dx <= 1; dx++)
                for (int dz = -1; dz <= 1; dz++) {
                    Block b = start.getRelative(dx, dy, dz);
                    if (replaceable(b)) return b;
                }
        return null;
    }

    private boolean replaceable(Block b) {
        Material m = b.getType();
        return m.isAir() || m == Material.WATER || m == Material.LAVA || m == Material.SHORT_GRASS
                || m == Material.TALL_GRASS || m == Material.SNOW || m == Material.FERN;
    }

    // ---------------- protection ----------------
    private String ownerOf(Block b) {
        if (b.getType() != Material.CHEST || !(b.getState() instanceof Chest c)) return null;
        return c.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getInventory().getHolder() instanceof Chest c)) return;
        String owner = c.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        if (owner == null) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!p.getUniqueId().toString().equals(owner) && !p.hasPermission("mavodc.admin")) {
            e.setCancelled(true);
            p.sendMessage(ChatColor.RED + "That grave isn't yours.");
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Chest c)) return;
        if (!c.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING)) return;
        if (c.getBlockInventory().isEmpty()) {
            Location loc = c.getBlock().getLocation();
            c.getBlock().setType(Material.AIR);
            List<String> l = new ArrayList<>(chests());
            String pref = key(loc);
            l.removeIf(x -> x.startsWith(pref + ";") || x.equals(pref));
            setChests(l);
            if (e.getPlayer() instanceof Player p)
                p.playSound(loc, Sound.BLOCK_WOOD_BREAK, 1f, 0.8f);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onBreak(BlockBreakEvent e) {
        String owner = ownerOf(e.getBlock());
        if (owner == null) return;
        if (!e.getPlayer().getUniqueId().toString().equals(owner) && !e.getPlayer().hasPermission("mavodc.admin")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "That grave isn't yours.");
            return;
        }
        List<String> l = new ArrayList<>(chests());
        String pref = key(e.getBlock().getLocation());
        l.removeIf(x -> x.startsWith(pref + ";") || x.equals(pref));
        setChests(l);
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) { e.blockList().removeIf(b -> ownerOf(b) != null); }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) { e.blockList().removeIf(b -> ownerOf(b) != null); }

    @EventHandler
    public void onHopper(InventoryMoveItemEvent e) {
        if (e.getSource().getHolder() instanceof Chest c
                && c.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING))
            e.setCancelled(true);
    }

    // ---------------- expiry ----------------
    private void sweep() {
        List<String> l = new ArrayList<>(chests());
        boolean changed = false;
        Iterator<String> it = l.iterator();
        while (it.hasNext()) {
            String s = it.next();
            Location loc = parse(s);
            if (loc == null) { it.remove(); changed = true; continue; }
            if (!loc.getWorld().isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) continue;
            Block b = loc.getBlock();
            if (b.getType() != Material.CHEST || !(b.getState() instanceof Chest c)
                    || !c.getPersistentDataContainer().has(ownerKey, PersistentDataType.STRING)) {
                it.remove(); changed = true; continue;
            }
            Long exp = c.getPersistentDataContainer().get(expireKey, PersistentDataType.LONG);
            if (exp != null && System.currentTimeMillis() > exp) {
                for (ItemStack item : c.getBlockInventory().getContents())
                    if (item != null && item.getType() != Material.AIR)
                        loc.getWorld().dropItemNaturally(loc.clone().add(0.5, 0.5, 0.5), item);
                b.setType(Material.AIR);
                it.remove(); changed = true;
            }
        }
        if (changed) setChests(l);
    }

    // ---------------- /grave GUI ----------------
    private List<String> myGraves(Player p) {
        List<String> out = new ArrayList<>();
        for (String k : chests()) {
            String[] parts = k.split(";");
            if (parts.length < 5) continue;
            if (!parts[4].equals(p.getUniqueId().toString())) continue;
            Location loc = parse(k);
            if (loc == null) continue;
            out.add(k);
        }
        return out;
    }

    private String[] graveInfo(String k) {
        String[] parts = k.split(";");
        Location loc = parse(k);
        if (loc == null) return null;
        long left = 0;
        Block b = loc.getBlock();
        if (b.getType() == Material.CHEST && b.getState() instanceof Chest c) {
            Long exp = c.getPersistentDataContainer().get(expireKey, PersistentDataType.LONG);
            if (exp != null) left = Math.max(0, (exp - System.currentTimeMillis()) / 60000L);
        }
        return new String[]{parts[0], String.valueOf(loc.getBlockX()), String.valueOf(loc.getBlockY()),
                String.valueOf(loc.getBlockZ()), String.valueOf(left)};
    }

    private void openList(Player p, int page) {
        Holder h = new Holder(); h.view = "list"; h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, cc("&4Your Graves"));
        List<String> g = myGraves(p);
        int per = 28;
        int pages = Math.max(1, (g.size() + per - 1) / per);
        int start = page * per;
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34, 37,38,39,40,41,42,43};
        int s = 0;
        for (int i = start; i < Math.min(g.size(), start + per); i++, s++) {
            String k = g.get(i);
            String[] info = graveInfo(k);
            if (info == null) continue;
            List<String> lore = new ArrayList<>();
            lore.add("&7World: &f" + info[0]);
            lore.add("&7X: &f" + info[1] + "  &7Y: &f" + info[2] + "  &7Z: &f" + info[3]);
            lore.add("&7Opens in: &f~" + info[4] + " min");
            lore.add("");
            lore.add("&e\u25B6 Click to travel");
            inv.setItem(slots[s], item(Material.CHEST, "&4" + p.getName() + "'s grave", lore, "g:" + k));
        }
        inv.setItem(45, page > 0 ? item(Material.LIME_DYE, "&a\u25C0 Newer graves", null, "__prev") : null);
        inv.setItem(49, page < pages - 1 ? item(Material.ORANGE_DYE, "&6Older graves \u25B6", null, "__next") : null);
        inv.setItem(53, item(Material.BARRIER, "&cClose", null, "__close"));
        fill(inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.7f, 1f);
    }

    private void openConfirm(Player p, String k) {
        String[] info = graveInfo(k);
        if (info == null) { p.sendMessage(ChatColor.RED + "That grave is gone."); openList(p, 0); return; }
        Holder h = new Holder(); h.view = "confirm"; h.graveKey = k;
        Inventory inv = Bukkit.createInventory(h, 27, cc("&4Travel to grave?"));
        List<String> lore = new ArrayList<>();
        lore.add("&7World: &f" + info[0] + "  |  X &f" + info[1] + " Y &f" + info[2] + " Z &f" + info[3]);
        lore.add("&7Opens in ~&f" + info[4] + " min");
        inv.setItem(13, item(Material.CHEST, "&4" + p.getName() + "'s grave", lore, null));

        double coinCost = getConfig().getDouble("teleport-cost-coins", 5000);
        boolean canCoins = econ != null && econ.getBalance(p) >= coinCost;
        inv.setItem(11, item(canCoins ? Material.GOLD_INGOT : Material.GRAY_DYE,
                "&6" + (int) coinCost + " coins", List.of("&7Instant trip to your grave",
                        "&73s countdown - moving cancels, no refund",
                        canCoins ? "" : "&cNot enough coins!", "&e\u25B6 Click to travel"),
                "pay:coins"));

        int lcCost = getConfig().getInt("teleport-cost-lucky", 100);
        boolean canLucky = canLucky(p, lcCost);
        ItemStack lcIcon = luckyCoinItem != null ? luckyItem(lcCost) : new ItemStack(Material.GOLD_NUGGET);
        inv.setItem(15, item(lcIcon.getType(), "&6" + lcCost + " Lucky Coins",
                List.of("&7Instant trip to your grave",
                        "&73s countdown - moving cancels, no refund",
                        canLucky ? "" : "&cNot enough Lucky Coins!", "&e\u25B6 Click to travel"),
                "pay:lucky"));
        inv.setItem(18, item(Material.ARROW, "&e\u25C0 Back", null, "__back"));
        inv.setItem(26, item(Material.BARRIER, "&cCancel", null, "__close"));
        fill(inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.1f);
    }

    private ItemStack luckyItem(int n) {
        try { return (ItemStack) luckyCoinItem.invoke(lucky, n); }
        catch (Exception ex) { return new ItemStack(Material.GOLD_NUGGET); }
    }

    private boolean canLucky(Player p, int n) {
        if (luckyCount == null || lucky == null) return false;
        try { return (int) luckyCount.invoke(lucky, p) >= n; }
        catch (Exception ex) { return false; }
    }

    private boolean takeLucky(Player p, int n) {
        if (luckyTake == null || lucky == null) return false;
        try { return (boolean) luckyTake.invoke(lucky, p, n); }
        catch (Exception ex) { return false; }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (id == null) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        if (id.equals("__close")) { p.closeInventory(); return; }
        if (id.equals("__back")) { openList(p, h.page); return; }
        if (id.equals("__prev")) { if (h.page > 0) openList(p, h.page - 1); return; }
        if (id.equals("__next")) { openList(p, h.page + 1); return; }
        if (id.startsWith("g:")) { openConfirm(p, id.substring(2)); return; }
        if (id.startsWith("pay:")) {
            double coins = getConfig().getDouble("teleport-cost-coins", 1000);
            int lc = getConfig().getInt("teleport-cost-lucky", 10);
            double cost = id.endsWith("coins") ? coins : lc;
            String mode = id.endsWith("coins") ? "coins" : "lucky";
            if ("coins".equals(mode) && (econ == null || econ.getBalance(p) < cost)) {
                p.sendMessage(ChatColor.RED + "Not enough coins."); return;
            }
            if ("lucky".equals(mode) && !canLucky(p, lc)) {
                p.sendMessage(ChatColor.RED + "Not enough Lucky Coins."); return;
            }
            startTeleport(p, h.graveKey, mode, cost);
            p.closeInventory();
        }
    }

    private void startTeleport(Player p, String k, String mode, double cost) {
        Location target = parse(k);
        if (target == null) return;
        UUID u = p.getUniqueId();
        Pending old = pending.remove(u);
        if (old != null && old.task != null) old.task.cancel();
        if (monstersNear(p)) {
            p.sendMessage(ChatColor.RED + "\u2694 Monsters nearby - can't travel! No escaping a fight.");
            return;
        }
        int secs = Math.max(1, getConfig().getInt("teleport-seconds", 3));
        p.sendMessage(ChatColor.RED + "\u26B0 " + ChatColor.GRAY + "Teleporting to your grave in "
                + ChatColor.GOLD + secs + "s" + ChatColor.GRAY + "... don't move!");
        Pending pend = new Pending();
        pend.target = target.clone(); pend.mode = mode; pend.cost = cost;
        pend.start = p.getLocation().clone();
        pend.task = Bukkit.getScheduler().runTaskLater(this, () -> {
            pending.remove(u);
            if (!p.isOnline()) return;
            if (p.getLocation().distanceSquared(pend.start) > 0.6) {
                p.sendMessage(ChatColor.RED + "\u26B0 Teleport cancelled - you moved.");
                return;
            }
            if (monstersNear(p)) {
                p.sendMessage(ChatColor.RED + "\u2694 Monsters nearby - teleport cancelled!");
                return;
            }
            if ("coins".equals(pend.mode)) {
                if (econ == null || !econ.has(p, pend.cost)) {
                    p.sendMessage(ChatColor.RED + "Not enough coins."); return;
                }
                econ.withdrawPlayer(p, pend.cost);
            } else {
                if (!takeLucky(p, (int) pend.cost)) {
                    p.sendMessage(ChatColor.RED + "Not enough Lucky Coins."); return;
                }
            }
            p.teleportAsync(pend.target).thenAccept(ok -> {
                if (ok) {
                    p.playSound(pend.target, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
                    p.sendMessage(ChatColor.GOLD + "\u26B0 Here's your grave. Grab it fast!");
                }
            });
        }, secs * 20L);
        pending.put(u, pend);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Pending pend = pending.get(e.getPlayer().getUniqueId());
        if (pend == null) return;
        if (e.getPlayer().getLocation().distanceSquared(pend.start) > 0.6) {
            pend.task.cancel();
            pending.remove(e.getPlayer().getUniqueId());
            e.getPlayer().sendMessage(ChatColor.RED + "\u26B0 Teleport cancelled - you moved.");
        }
    }

    private boolean monstersNear(Player p) {
        int r = getConfig().getInt("monster-radius", 12);
        return p.getWorld().getNearbyEntities(p.getLocation(), r, r, r).stream()
                .anyMatch(en -> en instanceof org.bukkit.entity.Monster);
    }

    // ---------------- command ----------------
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        openList(p, 0);
        return true;
    }
}
