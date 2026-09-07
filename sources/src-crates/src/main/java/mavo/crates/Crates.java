package mavo.crates;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

/**
 * MAVOCrates 1.0.0 - custom reward crates + keys (Discord CW#3 idea 4).
 * - crate blocks placed with /crate set <name>
 * - right-click opens a GUI that shows EVERY drop with its exact % chance + an OPEN button
 * - per-player cooldown per crate, firework + broadcast on good rolls
 * - keys drop from farming (mature crops), mining (ores) and fishing (key-drops config)
 * - floating hologram above every crate block
 * - public static giveKey(Player, crate, n) for BossRaid / Quests / Goals
 */
public final class Crates extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';
    private static Crates inst;

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;
    private final NamespacedKey keyTag = new NamespacedKey("mavocrate", "key");
    private final NamespacedKey guiTag = new NamespacedKey("mavocrate", "gui");
    private final NamespacedKey holoTag = new NamespacedKey("mavocrate", "holo");

    /** parsed pools: crate id -> (display, holo, key material, key name, key lore, cooldown, rewards) */
    private final Map<String, CrateDef> defs = new HashMap<>();
    /** crate blocks: world,x,y,z -> crate id */
    private final Map<Location, String> blocks = new HashMap<>();
    /** open crate GUIs (Hotfix 42): only these clicks are handled - no server-wide click theft */
    private final Map<UUID, GuiState> openGuis = new HashMap<>();

    private boolean keyDrops = true;
    private double keyCommon = 1.0, keyRare = 0.05, keyMythic = 0.01;

    private record Reward(String type, Material mat, int amount, long weight) { }
    private record CrateDef(String display, List<String> holo, Material keyMat, String keyName,
                            List<String> keyLore, long cooldown, List<Reward> rewards) { }
    private record GuiState(String id, Location at, Inventory inv) { }

    @Override public void onEnable() {
        inst = this;
        saveDefaultConfig();
        mergeMissingDefaults();              // HOTFIX 42: rebalanced pools reach existing configs
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        loadPools();
        loadBlocks();
        spawnHolos();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOCrates v" + getDescription().getVersion() + " enabled - " + defs.size()
                + " crate type(s), " + blocks.size() + " block(s), key drops "
                + (keyDrops ? "on (" + keyCommon + "%/" + keyRare + "%/" + keyMythic + "%)" : "off") + ".");
    }

    @Override public void onDisable() { saveData(); }

    // ---------------- config ----------------
    /** HOTFIX 42: a migrated config replaces the crate pools + key drops with the
     *  bundled rebalanced defaults (data.yml blocks are untouched). Runs once. */
    private void mergeMissingDefaults() {
        File f = new File(getDataFolder(), "config.yml");
        try {
            YamlConfiguration disk = YamlConfiguration.loadConfiguration(f);
            int ver = disk.getInt("pool-version", 0);
            if (ver >= 2) return;
            InputStream in = getResource("config.yml");
            if (in == null) return;
            YamlConfiguration def = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            disk.set("pool-version", 2);
            disk.set("crates", def.get("crates"));
            disk.set("key-drops", def.get("key-drops"));
            disk.save(f);
            getLogger().info("Hotfix 42: crate pools rebalanced to percentage drops (config upgraded).");
            reloadConfig();
        } catch (Throwable t) {
            getLogger().warning("crate pool upgrade failed: " + t.getMessage());
        }
    }

    private void loadPools() {
        defs.clear();
        ConfigurationSection cs = getConfig().getConfigurationSection("crates");
        if (cs == null) return;
        for (String id : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(id);
            if (c == null) continue;
            Material km = Material.matchMaterial(c.getString("key-material", "TRIPWIRE_HOOK"));
            if (km == null) km = Material.TRIPWIRE_HOOK;
            List<Reward> rewards = new ArrayList<>();
            for (String line : c.getStringList("rewards")) {
                String[] p = line.split(":");
                if (p.length < 3) continue;
                String type = p[0].trim().toLowerCase(Locale.ROOT);
                int amount = parseInt(p[1], 1);
                long weight = Math.max(1, parseLong(p[2], 1));
                if ("coins".equals(type) || "lucky".equals(type))
                    rewards.add(new Reward(type, null, amount, weight));
                else {
                    Material m = Material.matchMaterial(p[0].trim());
                    if (m != null && m != Material.AIR) rewards.add(new Reward("item", m, amount, weight));
                }
            }
            if (rewards.isEmpty()) continue;
            defs.put(id.toLowerCase(Locale.ROOT), new CrateDef(
                    c.getString("display", "&e" + id + " Crate"),
                    c.getStringList("holo"), km,
                    c.getString("key-name", "&e" + id + " Key"),
                    c.getStringList("key-lore"), Math.max(0, c.getLong("cooldown-seconds", 0)), rewards));
        }
        ConfigurationSection kd = getConfig().getConfigurationSection("key-drops");
        keyDrops = kd == null || kd.getBoolean("enabled", true);
        keyCommon = Math.max(0, kd == null ? 1.0 : kd.getDouble("common", 1.0));
        keyRare = Math.max(0, kd == null ? 0.05 : kd.getDouble("rare", 0.05));
        keyMythic = Math.max(0, kd == null ? 0.01 : kd.getDouble("mythic", 0.01));
    }

    private void loadBlocks() {
        blocks.clear();
        ConfigurationSection s = data.getConfigurationSection("blocks");
        if (s == null) return;
        for (String key : s.getKeys(false)) {
            String id = s.getString(key, "").toLowerCase(Locale.ROOT);
            if (!defs.containsKey(id)) continue;
            String[] p = key.split(",");
            if (p.length < 4) continue;
            World w = Bukkit.getWorld(p[0]);
            if (w == null) continue;
            try {
                blocks.put(new Location(w, Integer.parseInt(p[1]), Integer.parseInt(p[2]), Integer.parseInt(p[3])), id);
            } catch (Throwable ignored) { }
        }
    }

    private void saveData() { try { data.save(dataFile); } catch (Throwable ignored) { } }
    private void saveBlock(String id, Location l) {
        String key = locKey(l);
        data.set("blocks." + key, id);
        blocks.put(l, id);
        saveData();
    }
    private void removeBlock(Location l) {
        data.set("blocks." + locKey(l), null);
        blocks.remove(l);
        removeHolo(l);
        saveData();
    }
    private void clearBlocks(String id) {
        for (Map.Entry<Location, String> e : new ArrayList<>(blocks.entrySet()))
            if (e.getValue().equals(id)) {
                data.set("blocks." + locKey(e.getKey()), null);
                removeHolo(e.getKey());
                blocks.remove(e.getKey());
            }
        saveData();
    }
    private static String locKey(Location l) { return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ(); }

    // ---------------- holograms (Hotfix 42) ----------------
    private void spawnHolos() {
        for (Map.Entry<Location, String> e : blocks.entrySet()) {
            CrateDef d = defs.get(e.getValue());
            if (d == null) continue;
            Location l = e.getKey();
            // clear any old marked holo for this block (plugin reload safe)
            for (Entity en : l.getWorld().getNearbyEntities(l.clone().add(0.5, 1.6, 0.5), 1.5, 3.5, 1.5))
                if (en instanceof TextDisplay && en.getPersistentDataContainer().has(holoTag, PersistentDataType.BYTE))
                    en.remove();
            spawnHolo(l, d);
        }
    }

    private void spawnHolo(Location l, CrateDef d) {
        if (d == null) return;
        try {
            TextDisplay td = l.getWorld().spawn(l.clone().add(0.5, 1.6, 0.5), TextDisplay.class);
            List<String> lines = d.holo == null || d.holo.isEmpty()
                    ? List.of(d.display, "&7Right-click to open (1 key)") : d.holo;
            StringBuilder sb = new StringBuilder();
            for (String s : lines) { if (sb.length() > 0) sb.append('\n'); sb.append(cc(s)); }
            td.setText(sb.toString());   // legacy § codes render natively
            td.setBillboard(TextDisplay.Billboard.CENTER);
            td.setDefaultBackground(false);
            td.setShadowed(true);
            td.setSeeThrough(false);
            td.setAlignment(TextDisplay.TextAlignment.CENTER);
            td.setTransformation(new Transformation(new Vector3f(0, 0, 0), new Quaternionf(),
                    new Vector3f(0.55f, 0.55f, 0.55f), new Quaternionf()));
            td.getPersistentDataContainer().set(holoTag, PersistentDataType.BYTE, (byte) 1);
        } catch (Throwable t) {
            getLogger().warning("could not spawn crate holo: " + t.getMessage());
        }
    }

    private void removeHolo(Location l) {
        try {
            for (Entity en : l.getWorld().getNearbyEntities(l.clone().add(0.5, 1.6, 0.5), 1.5, 3.5, 1.5))
                if (en instanceof TextDisplay && en.getPersistentDataContainer().has(holoTag, PersistentDataType.BYTE))
                    en.remove();
        } catch (Throwable ignored) { }
    }

    // ---------------- keys ----------------
    public static void giveKey(Player p, String crate, int n) {
        if (inst == null) return;
        for (String id : inst.defs.keySet())
            if (id.equalsIgnoreCase(crate)) { inst.give(p, id, Math.max(1, n)); return; }
        p.sendMessage(C + "cNo such crate type: " + C + "e" + crate);
    }

    private void give(Player p, String id, int n) {
        CrateDef d = defs.get(id);
        if (d == null) return;
        ItemStack key = makeKey(d, id);
        key.setAmount(n);
        Map<Integer, ItemStack> left = p.getInventory().addItem(key);
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
        p.sendMessage(C + "aYou received " + C + "e" + n + "x " + cc(d.keyName) + C + "a.");
    }

    private ItemStack makeKey(CrateDef d, String id) {
        ItemStack it = new ItemStack(d.keyMat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(cc(d.keyName));
        List<String> lore = new ArrayList<>();
        if (d.keyLore != null) for (String s : d.keyLore) lore.add(cc(s));
        lore.add(C + "7Crate: " + cc(d.display));
        m.setLore(lore);
        m.getPersistentDataContainer().set(keyTag, PersistentDataType.STRING, id);
        it.setItemMeta(m);
        return it;
    }

    private boolean isKey(ItemStack it, String id) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(keyTag, PersistentDataType.STRING)
                && id.equals(it.getItemMeta().getPersistentDataContainer().get(keyTag, PersistentDataType.STRING));
    }

    private int takeKey(Player p, String id) {
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length; i++)
            if (isKey(inv[i], id)) {
                ItemStack it = inv[i];
                int got = it.getAmount();
                p.getInventory().setItem(i, null);
                return got;
            }
        return 0;
    }

    // ---------------- key drops from actions (Hotfix 42) ----------------
    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        String id = blocks.get(e.getBlock().getLocation());
        if (id != null) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C + "cThis is a crate block - use " + C + "e/crate unset" + C + "c (OP).");
            return;
        }
        if (!keyDrops) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;
        Block b = e.getBlock();
        if (isOre(b.getType())) { tryKeyDrops(p, b.getLocation()); return; }
        if (b.getBlockData() instanceof Ageable a && a.getAge() >= a.getMaximumAge())
            tryKeyDrops(p, b.getLocation());   // mature crop harvest = farming action
    }

    @EventHandler(ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH || !keyDrops) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;
        tryKeyDrops(p, p.getLocation());
    }

    private void tryKeyDrops(Player p, Location at) {
        boolean got = false;
        if (rollPct(keyCommon) && giveKeyQuiet(p, "common")) got = true;
        if (rollPct(keyRare) && giveKeyQuiet(p, "rare")) got = true;
        if (rollPct(keyMythic) && giveKeyQuiet(p, "mythic")) got = true;
        if (got) {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.3f);
        }
    }

    /** true if a key was handed out; false if the crate type is missing */
    private boolean giveKeyQuiet(Player p, String id) {
        CrateDef d = defs.get(id);
        if (d == null) return false;
        ItemStack key = makeKey(d, id);
        Map<Integer, ItemStack> left = p.getInventory().addItem(key);
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
        p.sendMessage(C + "7You found a " + cc(d.keyName) + C + "7!");
        return true;
    }

    private static boolean rollPct(double pct) { return pct > 0 && Math.random() * 100.0 < pct; }

    private static boolean isOre(Material m) {
        return switch (m) {
            case COAL_ORE, DEEPSLATE_COAL_ORE, IRON_ORE, DEEPSLATE_IRON_ORE, COPPER_ORE,
                 DEEPSLATE_COPPER_ORE, GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE,
                 REDSTONE_ORE, DEEPSLATE_REDSTONE_ORE, LAPIS_ORE, DEEPSLATE_LAPIS_ORE,
                 DIAMOND_ORE, DEEPSLATE_DIAMOND_ORE, EMERALD_ORE, DEEPSLATE_EMERALD_ORE,
                 NETHER_QUARTZ_ORE, ANCIENT_DEBRIS -> true;
            default -> false;
        };
    }

    // ---------------- GUI (Hotfix 42) ----------------
    private void openGui(Player p, String id, Location at) {
        CrateDef d = defs.get(id);
        if (d == null) return;
        Inventory inv = Bukkit.createInventory(null, 54, C + "1" + cc(d.display) + C + "8 - drop rates");
        long total = 0;
        for (Reward r : d.rewards) total += r.weight;
        int slot = 0;
        for (Reward r : d.rewards) {
            if (slot >= 45) break;
            ItemStack it = new ItemStack("coins".equals(r.type) ? Material.GOLD_NUGGET
                    : "lucky".equals(r.type) ? Material.SUNFLOWER : r.mat);
            it.setAmount(Math.min(64, Math.max(1, r.amount)));
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(C + "e" + nice(r));
            List<String> lore = new ArrayList<>();
            lore.add(C + "7Drop chance: " + C + "f" + pct(total, r.weight));
            lore.add(C + "8Every roll is random - this is the exact chance.");
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(slot++, it);
        }
        ItemStack open = new ItemStack(Material.CHEST);
        ItemMeta om = open.getItemMeta();
        om.setDisplayName(C + "a\u276f OPEN CRATE");
        om.setLore(List.of(C + "7Uses " + C + "e1x " + cc(d.keyName) + C + "7, rolls once,",
                C + "7then shows what you won."));
        om.getPersistentDataContainer().set(guiTag, PersistentDataType.STRING, "open");
        open.setItemMeta(om);
        inv.setItem(49, open);
        ItemStack close = new ItemStack(Material.BOOK);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName(C + "eClose");
        close.setItemMeta(cm);
        inv.setItem(53, close);
        inv.setItem(48, glass());
        inv.setItem(50, glass());
        openGuis.put(p.getUniqueId(), new GuiState(id, at, inv));
        p.openInventory(inv);
    }

    private ItemStack glass() {
        ItemStack it = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(" ");
        it.setItemMeta(m);
        return it;
    }

    private static String pct(long total, long weight) {
        double p = 100.0 * weight / Math.max(1, total);
        return p == Math.floor(p) ? String.format(Locale.ROOT, "%.0f%%", p)
                : String.format(Locale.ROOT, "%.1f%%", p);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getPlayer() instanceof Player p) openGuis.remove(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // HOTFIX 38 lesson: only touch clicks while OUR crate GUI is open - the
        // per-player tracked inventory makes the guard exact, so chests/furnaces/
        // player inventories are never affected.
        GuiState g = openGuis.get(p.getUniqueId());
        if (g == null || !g.inv().equals(e.getView().getTopInventory())) return;
        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String act = it.getItemMeta().getPersistentDataContainer().get(guiTag, PersistentDataType.STRING);
        if (act == null) return;
        if (act.equals("open")) {
            p.closeInventory();
            roll(p, g.id(), g.at());
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (openGuis.containsKey(p.getUniqueId())) e.setCancelled(true);
    }

    // ---------------- rolling ----------------
    private void roll(Player p, String id, Location at) {
        CrateDef d = defs.get(id);
        if (d == null) return;
        long cd = d.cooldown * 1000L;
        if (cd > 0) {
            long last = data.getLong("players." + p.getUniqueId() + "." + id, 0);
            long left = last + cd - System.currentTimeMillis();
            if (left > 0) {
                p.sendMessage(C + "cThis crate is recharging - wait " + C + "e" + (left / 1000) + "s" + C + "c.");
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                return;
            }
        }
        if (takeKey(p, id) == 0) {
            p.sendMessage(C + "cYou need a " + cc(d.keyName) + C + "c to open this crate.");
            return;
        }
        data.set("players." + p.getUniqueId() + "." + id, System.currentTimeMillis());
        saveData();
        Reward r = pick(d.rewards);
        giveReward(p, r, id);
        firework(at);
        Bukkit.broadcastMessage(C + "d\u00bb " + p.getName() + C + "d opened a " + cc(d.display)
                + C + "d and got " + C + "e" + nice(r) + C + "d!");
        p.sendMessage(C + "aYou opened a " + cc(d.display) + C + "a and got " + C + "e" + nice(r) + C + "a.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
    }

    private Reward pick(List<Reward> rewards) {
        long total = 0;
        for (Reward r : rewards) total += r.weight;
        long roll = (long) (Math.random() * total);
        long acc = 0;
        for (Reward r : rewards) {
            acc += r.weight;
            if (roll < acc) return r;
        }
        return rewards.get(0);
    }

    private void giveReward(Player p, Reward r, String crateId) {
        if ("coins".equals(r.type)) {
            if (econ != null && econ.depositPlayer(p, r.amount).transactionSuccess()) return;
            p.sendMessage(C + "cCould not pay you - tell an admin.");
            return;
        }
        if ("lucky".equals(r.type)) {
            try {
                org.bukkit.plugin.Plugin luck = Bukkit.getPluginManager().getPlugin("MAVOLuckyCoins");
                if (luck != null) {
                    luck.getClass().getMethod("giveCoins", Player.class, int.class).invoke(luck, p, r.amount);
                    return;
                }
            } catch (Throwable ignored) { }
            p.sendMessage(C + "cLucky Coins plugin missing - nothing given.");
            return;
        }
        ItemStack it = new ItemStack(r.mat, r.amount);
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    private void firework(Location at) {
        try {
            Firework fw = (Firework) at.getWorld().spawn(at.clone().add(0.5, 3, 0.5), Firework.class);
            FireworkMeta meta = fw.getFireworkMeta();
            meta.setPower(1);
            meta.addEffect(FireworkEffect.builder().with(FireworkEffect.Type.BURST)
                    .withColor(Color.RED, Color.YELLOW, Color.AQUA, Color.PURPLE).withFlicker().build());
            fw.setFireworkMeta(meta);
            Bukkit.getScheduler().runTaskLater(this, fw::detonate, 25L);
        } catch (Throwable ignored) { }
    }

    private static String nice(Reward r) {
        if ("coins".equals(r.type)) return String.format("%,d", r.amount) + " coins";
        if ("lucky".equals(r.type)) return r.amount + "x Lucky Coin" + (r.amount > 1 ? "s" : "");
        return r.amount + "x " + r.mat.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    // ---------------- events ----------------
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) return;
        String id = blocks.get(e.getClickedBlock().getLocation());
        if (id == null) return;
        e.setCancelled(true);
        openGui(e.getPlayer(), id, e.getClickedBlock().getLocation());   // HOTFIX 42: GUI first
    }

    // ---------------- commands ----------------
    @Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("list", "info"));
            if (sender.hasPermission("mavocrate.admin")) out.addAll(List.of("set", "unset", "clear", "givekey", "resholo", "reload"));
            return out;
        }
        if (args.length == 2 && sender.hasPermission("mavocrate.admin")) return new ArrayList<>(defs.keySet());
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                sender.sendMessage(C + "d" + C + "lCrate types:");
                for (String id : defs.keySet()) {
                    CrateDef d = defs.get(id);
                    sender.sendMessage(C + "7 - " + cc(d.display) + C + "7 (" + id + ", " + d.rewards.size()
                            + " rewards, blocks: " + blocksCount(id) + ")");
                }
                sender.sendMessage(C + "8Keys drop while farming/mining/fishing: " + keyCommon + "% / "
                        + keyRare + "% / " + keyMythic + "% (common/rare/mythic).");
            }
            case "info" -> {
                if (args.length < 2) { sender.sendMessage(C + "cUsage: /crate info <name>"); return true; }
                CrateDef d = defs.get(args[1].toLowerCase(Locale.ROOT));
                if (d == null) { sender.sendMessage(C + "cNo such crate: " + args[1]); return true; }
                long total = 0;
                for (Reward r : d.rewards) total += r.weight;
                sender.sendMessage(C + "d" + cc(d.display) + C + "7 - key: " + cc(d.keyName) + C + "7 ("
                        + d.keyMat.name() + "), cooldown " + d.cooldown + "s, blocks: " + blocksCount(args[1]));
                for (Reward r : d.rewards)
                    sender.sendMessage(C + "7   " + nice(r) + C + "8 (" + pct(total, r.weight) + ")");
            }
            case "set" -> {
                if (!sender.hasPermission("mavocrate.admin")) { sender.sendMessage("OP only."); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                if (args.length < 2 || !defs.containsKey(args[1].toLowerCase(Locale.ROOT))) {
                    sender.sendMessage(C + "cUsage: /crate set <name>  (look at the block)"); return true;
                }
                Block b = p.getTargetBlockExact(6);
                if (b == null || b.getType() == Material.AIR) { p.sendMessage(C + "cLook at a block within 6 blocks."); return true; }
                saveBlock(args[1].toLowerCase(Locale.ROOT), b.getLocation());
                spawnHolo(b.getLocation(), defs.get(args[1].toLowerCase(Locale.ROOT)));
                p.sendMessage(C + "aCrate set at " + b.getX() + " " + b.getY() + " " + b.getZ()
                        + " (" + cc(defs.get(args[1].toLowerCase(Locale.ROOT)).display) + C + "a).");
            }
            case "unset" -> {
                if (!sender.hasPermission("mavocrate.admin")) { sender.sendMessage("OP only."); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                Block b = p.getTargetBlockExact(6);
                if (b == null || !blocks.containsKey(b.getLocation())) { p.sendMessage(C + "cThat block is not a crate."); return true; }
                removeBlock(b.getLocation());
                p.sendMessage(C + "aCrate block removed (holo cleaned up).");
            }
            case "clear" -> {
                if (!sender.hasPermission("mavocrate.admin")) { sender.sendMessage("OP only."); return true; }
                if (args.length < 2 || !defs.containsKey(args[1].toLowerCase(Locale.ROOT))) {
                    sender.sendMessage(C + "cUsage: /crate clear <name>"); return true;
                }
                clearBlocks(args[1].toLowerCase(Locale.ROOT));
                sender.sendMessage(C + "aAll blocks for " + args[1] + " removed.");
            }
            case "givekey" -> {
                if (!sender.hasPermission("mavocrate.admin")) { sender.sendMessage("OP only."); return true; }
                if (args.length < 3) { sender.sendMessage(C + "cUsage: /crate givekey <player> <name> [n]"); return true; }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) { sender.sendMessage(C + "cPlayer offline."); return true; }
                giveKey(target, args[2], args.length >= 4 ? parseInt(args[3], 1) : 1);
                sender.sendMessage(C + "aDone.");
            }
            case "resholo" -> {
                if (!sender.hasPermission("mavocrate.admin")) { sender.sendMessage("OP only."); return true; }
                spawnHolos();
                sender.sendMessage(C + "aCrate holograms rebuilt (" + blocks.size() + ").");
            }
            case "reload" -> {
                if (!sender.hasPermission("mavocrate.admin")) { sender.sendMessage("OP only."); return true; }
                reloadConfig(); loadPools();
                spawnHolos();
                sender.sendMessage(C + "aCrate pools reloaded (" + defs.size() + " types).");
            }
            default -> sender.sendMessage(C + "7/crate list | info <name> (OP: set, unset, clear, givekey, resholo, reload)");
        }
        return true;
    }

    private long blocksCount(String id) {
        return blocks.values().stream().filter(v -> v.equals(id)).count();
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Throwable t) { return def; }
    }
    private static long parseLong(String s, long def) {
        try { return Long.parseLong(s); } catch (Throwable t) { return def; }
    }
    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
