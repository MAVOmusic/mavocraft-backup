package mavo.chesthunt;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/** MAVOChestHunt 3.0.0 - daily loot chest next to spawn (Discord idea).
 *  Every Minecraft day at noon (tick 6000) an ENDER_CHEST appears within
 *  'radius' blocks of world spawn, filled with useful loot + a really small
 *  chance of expensive items. Right-click opens a shared loot GUI - whoever
 *  takes an item removes it for everyone. If nobody opens the chest within
 *  'lifetime-minutes' it disappears (next one at noon). A floating holo marks
 *  the chest, /chesthunt shows the distance, admin can reload/force spawn. */
public final class ChestHunt extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private record LootEntry(Material mat, int amount, int weight) {}

    private boolean enabled = true;
    private String worldName = "world";
    private int spawnTick = 6000;
    private int radius = 100;
    private int lifetimeMin = 20;
    private int lootSlots = 12;
    private final List<LootEntry> pool = new ArrayList<>();
    private int totalWeight = 0;

    private Location chestLoc;                    // block location of the live chest
    private long spawnTime = 0;                   // epoch millis when it appeared
    private boolean opened = false;               // somebody collected at least once
    private long lastDay = -1;                    // Minecraft day already served
    private final List<ItemStack> loot = new ArrayList<>();   // remaining shared loot
    private TextDisplay holo;
    private final Map<UUID, Inventory> openGuis = new HashMap<>();
    private final Random rnd = new Random();
    private File dataFile;
    private YamlConfiguration data;

    @Override public void onEnable() {
        saveDefaultConfig();
        load();
        dataFile = new File(getDataFolder(), "chest.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadState();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 20L);
        getLogger().info("MAVOChestHunt v" + getDescription().getVersion() + " enabled - "
                + (enabled ? "chest every day at noon, radius " + radius + " blocks" : "disabled")
                + ", pool " + pool.size() + " item type(s).");
    }

    @Override public void onDisable() {
        saveState();
        if (holo != null) holo.remove();
        holo = null;
        openGuis.clear();
    }

    private void load() {
        pool.clear();
        totalWeight = 0;
        enabled = getConfig().getBoolean("enabled", true);
        worldName = getConfig().getString("world", "world");
        spawnTick = Math.max(0, getConfig().getInt("spawn-daytime-tick", 6000));
        radius = Math.max(10, getConfig().getInt("radius", 100));
        lifetimeMin = Math.max(1, getConfig().getInt("lifetime-minutes", 20));
        lootSlots = Math.max(1, Math.min(27, getConfig().getInt("loot-slots", 12)));
        ConfigurationSection cs = getConfig().getConfigurationSection("loot");
        if (cs != null) for (String k : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(k);
            if (c == null) continue;
            Material m = Material.matchMaterial(k.toUpperCase(Locale.ROOT));
            if (m == null || !m.isItem()) {
                getLogger().warning("loot entry " + k + " is not a valid item - skipped.");
                continue;
            }
            int amount = Math.max(1, c.getInt("amount", 1));
            int weight = Math.max(1, c.getInt("weight", 1));
            pool.add(new LootEntry(m, amount, weight));
            totalWeight += weight;
        }
        if (pool.isEmpty()) {
            pool.add(new LootEntry(Material.GOLD_INGOT, 4, 1));
            totalWeight = 1;
        }
    }

    // ---------------- persistence ----------------
    private void loadState() {
        chestLoc = null;
        if (data == null || !data.contains("spawn-time")) return;
        long st = data.getLong("spawn-time", 0);
        if (st <= 0 || System.currentTimeMillis() - st > lifetimeMin * 60000L) return;
        World w = Bukkit.getWorld(data.getString("world", worldName));
        if (w == null) w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (w == null) return;
        chestLoc = new Location(w, data.getInt("x", 0), data.getInt("y", 0), data.getInt("z", 0));
        spawnTime = st;
        opened = data.getBoolean("opened", false);
        lastDay = data.getLong("day", w.getFullTime() / 24000L);
        placeChest(chestLoc);
    }

    private void saveState() {
        if (data == null) return;
        data.set("world", chestLoc == null ? worldName : chestLoc.getWorld().getName());
        data.set("x", chestLoc == null ? 0 : chestLoc.getBlockX());
        data.set("y", chestLoc == null ? 0 : chestLoc.getBlockY());
        data.set("z", chestLoc == null ? 0 : chestLoc.getBlockZ());
        data.set("spawn-time", chestLoc == null ? 0 : spawnTime);
        data.set("opened", chestLoc != null && opened);
        data.set("day", lastDay);
        try { data.save(dataFile); } catch (Exception ex) { getLogger().warning("could not save chest.yml: " + ex.getMessage()); }
    }

    // ---------------- daily schedule ----------------
    private void tick() {
        if (!enabled) return;
        World w = chestLoc != null ? chestLoc.getWorld() : Bukkit.getWorld(worldName);
        if (w == null) w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (w == null) return;

        long full = w.getFullTime();
        long day = full / 24000L;
        long time = full % 24000L;

        // noon: announce + spawn a new chest once per day
        if (time >= spawnTick && time < spawnTick + 400L && day != lastDay) {
            if (spawnChest(w)) {
                lastDay = day;
                saveState();
            }
        }

        // integrity: chest block removed by someone/something -> clean state silently
        if (chestLoc != null && chestLoc.getBlock().getType() != Material.ENDER_CHEST) {
            despawn(false);
            return;
        }

        // nobody collected it within its lifetime -> it disappears
        if (chestLoc != null && !opened && System.currentTimeMillis() - spawnTime > lifetimeMin * 60000L) {
            Bukkit.broadcastMessage(C + "5\u00bb " + C + "dThe Chest Hunt chest " + C + "7vanished - nobody collected it! "
                    + C + "5The next one appears at noon.");
            despawn(false);
        }
    }

    private boolean spawnChest(World w) {
        if (chestLoc != null) despawn(false);   // replace the old chest for the new day
        Location l = findSpot(w);
        if (l == null) {
            getLogger().warning("no safe chest spot found within " + radius + " blocks - retrying.");
            return false;
        }
        placeChest(l);
        spawnTime = System.currentTimeMillis();
        opened = false;
        for (int i = 0; i < lootSlots; i++) {
            LootEntry e = roll();
            if (e != null) loot.add(new ItemStack(e.mat(), e.amount()));
        }
        if (loot.isEmpty()) loot.add(new ItemStack(Material.GOLD_INGOT, 4));
        Bukkit.broadcastMessage(C + "5\u00bb " + C + "dA Chest Hunt chest " + C + "5has appeared within "
                + C + "e" + radius + C + "5 blocks of spawn (" + C + "e" + l.getBlockX() + ", " + l.getBlockZ()
                + C + "5)! = useful loot + a REALLY small chance of a BIG prize - "
                + C + "b/chesthunt" + C + "5 to find it!");
        return true;
    }

    private LootEntry roll() {
        if (totalWeight <= 0) return null;
        int r = rnd.nextInt(totalWeight);
        for (LootEntry e : pool) {
            r -= e.weight();
            if (r < 0) return e;
        }
        return pool.get(pool.size() - 1);
    }

    private Location findSpot(World w) {
        Location s = w.getSpawnLocation();
        for (int tries = 0; tries < 50; tries++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            int dist = 1 + rnd.nextInt(radius);
            int x = s.getBlockX() + (int) (Math.cos(ang) * dist);
            int z = s.getBlockZ() + (int) (Math.sin(ang) * dist);
            int y = w.getHighestBlockYAt(x, z);
            if (y < 50) continue;
            Material top = w.getBlockAt(x, y, z).getType();
            if (top == Material.WATER || top == Material.LAVA) continue;
            return new Location(w, x, y + 1, z);
        }
        return null;
    }

    private void placeChest(Location l) {
        l.getBlock().setType(Material.ENDER_CHEST);
        chestLoc = new Location(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
        // floating holo marker
        Location hl = chestLoc.clone().add(0.5, 1.5, 0.5);
        TextDisplay d = (TextDisplay) chestLoc.getWorld().spawnEntity(hl, EntityType.TEXT_DISPLAY);
        d.setText(cc("&d&lCHEST HUNT"));
        d.setBillboard(Display.Billboard.CENTER);
        d.setDefaultBackground(false);
        d.setShadowed(true);
        d.setPersistent(true);
        d.setTransformation(new Transformation(
                new Vector3f(0f, 0.35f, 0f), new Quaternionf(),
                new Vector3f(0.9f), new Quaternionf()));
        holo = d;
    }

    private void despawn(boolean emptied) {
        if (chestLoc != null && chestLoc.getBlock().getType() == Material.ENDER_CHEST)
            chestLoc.getBlock().setType(Material.AIR);
        if (holo != null) holo.remove();
        holo = null;
        chestLoc = null;
        loot.clear();
        // copy the key set - closing inventories fires InventoryCloseEvent which
        // removes entries from openGuis while we iterate
        for (UUID id : new ArrayList<>(openGuis.keySet())) {
            Player p = Bukkit.getPlayer(id);
            if (p != null && openGuis.containsKey(id)
                    && p.getOpenInventory().getTopInventory().equals(openGuis.get(id)))
                p.closeInventory();
        }
        openGuis.clear();
        saveState();
        if (emptied)
            Bukkit.broadcastMessage(C + "5\u00bb " + C + "dThe Chest Hunt chest " + C + "5is empty - the hunt is over for today!");
    }

    // ---------------- looting ----------------
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!enabled || e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || b.getType() != Material.ENDER_CHEST) return;
        if (chestLoc == null || !b.getLocation().equals(chestLoc)) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        Inventory inv = Bukkit.createInventory(null, 27, cc("&d&lChest Hunt"));
        fill(inv);
        openGuis.put(p.getUniqueId(), inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 1f, 1f);
        opened = true;
        saveState();
    }

    private void fill(Inventory inv) {
        inv.clear();
        int slot = 0;
        for (ItemStack it : loot) {
            if (slot >= 27) break;
            inv.setItem(slot++, it.clone());
        }
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory gui = openGuis.get(p.getUniqueId());
        if (gui == null || !gui.equals(e.getView().getTopInventory())) return;
        e.setCancelled(true);
        if (e.getClickedInventory() == null || e.getClickedInventory() != gui) return;
        ItemStack it = e.getCurrent();
        if (it == null || it.getType().isAir()) return;
        // remove one matching stack from the SHARED loot
        ItemStack found = null;
        for (ItemStack s : loot) {
            if (s.isSimilar(it)) { found = s; break; }
        }
        if (found == null) return;
        loot.remove(found);
        Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone());
        for (ItemStack drop : left.values())
            p.getWorld().dropItemNaturally(p.getLocation(), drop);
        e.setCurrent(null);
        // refresh the other open viewers so nobody double-takes
        for (Map.Entry<UUID, Inventory> en : openGuis.entrySet()) {
            if (en.getKey().equals(p.getUniqueId())) continue;
            Player o = Bukkit.getPlayer(en.getKey());
            if (o != null && o.getOpenInventory().getTopInventory().equals(en.getValue())) fill(en.getValue());
        }
        p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 1f, 1.2f);
        if (loot.isEmpty()) despawn(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory gui = openGuis.get(p.getUniqueId());
        if (gui != null && gui.equals(e.getView().getTopInventory())) e.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory gui = openGuis.get(p.getUniqueId());
        if (gui != null && gui.equals(e.getInventory())) openGuis.remove(p.getUniqueId());
    }

    // ---------------- protection ----------------
    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (chestLoc != null && e.getBlock().getType() == Material.ENDER_CHEST
                && e.getBlock().getLocation().equals(chestLoc)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C + "cThe hunt chest is protected - collect its loot instead!");
        }
    }

    @EventHandler
    public void onExplode(EntityExplodeEvent e) {
        if (chestLoc == null) return;
        List<Block> keep = new ArrayList<>();
        for (Block b : e.blockList())
            if (!(b.getType() == Material.ENDER_CHEST && b.getLocation().equals(chestLoc))) keep.add(b);
        e.blockList().clear();
        e.blockList().addAll(keep);
    }

    // ---------------- commands ----------------
    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!cmd.getName().equalsIgnoreCase("chesthunt")) return true;
        String sub = args.length == 0 ? "" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("status")) {
            if (chestLoc == null) {
                sender.sendMessage(C + "5\u00bb No Chest Hunt chest right now - the next one appears at noon.");
            } else {
                long left = Math.max(1, (lifetimeMin * 60000L - (System.currentTimeMillis() - spawnTime)) / 60000L);
                sender.sendMessage(C + "5\u00bb Chest Hunt chest at " + C + "e" + chestLoc.getBlockX() + ", "
                        + chestLoc.getBlockZ() + C + "5 - " + C + "e" + loot.size() + C + "5 items left, expires in "
                        + C + "e" + left + C + "5 min.");
            }
            return true;
        }
        if (sub.equals("reload")) {
            if (!sender.hasPermission("chesthunt.admin")) { sender.sendMessage("OP only."); return true; }
            reloadConfig(); load();
            sender.sendMessage(C + "aReloaded Chest Hunt config (" + pool.size() + " item types).");
            return true;
        }
        if (sub.equals("spawn")) {
            if (!sender.hasPermission("chesthunt.admin")) { sender.sendMessage("OP only."); return true; }
            World w = Bukkit.getWorld(worldName);
            if (w == null) w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
            if (w == null) { sender.sendMessage(C + "cNo world loaded."); return true; }
            lastDay = w.getFullTime() / 24000L;   // don't double-spawn on the next tick
            spawnChest(w);
            saveState();
            return true;
        }
        // default: hint
        if (!(sender instanceof Player p)) {
            sender.sendMessage(C + "7/chesthunt | status | reload|spawn (OP)");
            return true;
        }
        if (chestLoc == null) {
            p.sendMessage(C + "5\u00bb No Chest Hunt chest right now - the next one appears at noon.");
        } else {
            int dist = (int) Math.hypot(p.getLocation().getBlockX() - chestLoc.getBlockX(),
                    p.getLocation().getBlockZ() - chestLoc.getBlockZ());
            p.sendMessage(C + "5\u00bb The Chest Hunt chest is " + C + "e" + dist
                    + C + "5 blocks away" + (dist <= 8 ? " - right there!" : " - go get it!"));
            p.playSound(p.getLocation(), Sound.BLOCK_ENDER_CHEST_OPEN, 0.8f, 1.4f);
        }
        return true;
    }

    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
