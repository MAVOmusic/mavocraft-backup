/*
 * Decompiled with CFR 0.152.
 * 
 * Could not load the following classes:
 *  net.milkbowl.vault.economy.Economy
 *  org.bukkit.Bukkit
 *  org.bukkit.ChatColor
 *  org.bukkit.Material
 *  org.bukkit.OfflinePlayer
 *  org.bukkit.Sound
 *  org.bukkit.command.Command
 *  org.bukkit.command.CommandSender
 *  org.bukkit.configuration.ConfigurationSection
 *  org.bukkit.configuration.file.YamlConfiguration
 *  org.bukkit.entity.Player
 *  org.bukkit.event.EventHandler
 *  org.bukkit.event.EventPriority
 *  org.bukkit.event.Listener
 *  org.bukkit.event.block.BlockBreakEvent
 *  org.bukkit.event.block.BlockPlaceEvent
 *  org.bukkit.event.entity.EntityDeathEvent
 *  org.bukkit.event.inventory.InventoryClickEvent
 *  org.bukkit.event.player.PlayerFishEvent
 *  org.bukkit.event.player.PlayerFishEvent$State
 *  org.bukkit.event.player.PlayerMoveEvent
 *  org.bukkit.inventory.Inventory
 *  org.bukkit.inventory.InventoryHolder
 *  org.bukkit.inventory.ItemStack
 *  org.bukkit.inventory.meta.ItemMeta
 *  org.bukkit.plugin.Plugin
 *  org.bukkit.plugin.RegisteredServiceProvider
 *  org.bukkit.plugin.java.JavaPlugin
 *  org.bukkit.scheduler.BukkitRunnable
 */
package mavo.achievements;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.entity.Monster;
import org.bukkit.entity.EntityType;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class Achievements
extends JavaPlugin
implements Listener {
    private Economy econ;
    private final Map<String, Category> categories = new LinkedHashMap<String, Category>();
    private int rewardEvery;
    private File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, Map<String, Long>> counts = new HashMap<UUID, Map<String, Long>>();
    private final Map<UUID, Map<String, Integer>> levels = new HashMap<UUID, Map<String, Integer>>();
    private boolean dirty = false;
    private static final List<Material> CROPS = Arrays.asList(Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS, Material.NETHER_WART, Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE, Material.CACTUS, Material.COCOA, Material.SWEET_BERRY_BUSH, Material.BAMBOO);
    private final Map<UUID, Double> moveBuffer = new HashMap<UUID, Double>();

    public void onEnable() {
        this.saveDefaultConfig();
        this.dataFile = new File(this.getDataFolder(), "data.yml");
        this.data = YamlConfiguration.loadConfiguration((File)this.dataFile);
        RegisteredServiceProvider rsp = this.getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) {
            this.econ = (Economy)rsp.getProvider();
        }
        this.loadCfg();
        this.getServer().getPluginManager().registerEvents((Listener)this, (Plugin)this);
        new BukkitRunnable(){

            public void run() {
                if (Achievements.this.dirty) {
                    Achievements.this.flush();
                    Achievements.this.dirty = false;
                }
            }
        }.runTaskTimerAsynchronously((Plugin)this, 600L, 600L);
        this.getLogger().info("MAVOAchievements enabled: " + this.categories.size() + " categories, milestone every " + this.rewardEvery + " levels.");
    }

    public void onDisable() {
        this.flush();
    }

    private void loadCfg() {
        this.categories.clear();
        this.reloadConfig();
        this.rewardEvery = Math.max(1, this.getConfig().getInt("reward-every", 5));
        ConfigurationSection sec = this.getConfig().getConfigurationSection("categories");
        if (sec == null) {
            return;
        }
        for (String id : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(id);
            if (c == null) continue;
            Category cat = new Category();
            cat.id = id.toLowerCase(Locale.ROOT);
            cat.display = ChatColor.translateAlternateColorCodes((char)'&', (String)c.getString("display", id));
            cat.what = c.getString("what", "things");
            cat.icon = Material.matchMaterial((String)c.getString("icon", "CHEST"));
            if (cat.icon == null) {
                cat.icon = Material.CHEST;
            }
            cat.base = Math.max(1L, c.getLong("base", 1L));
            cat.growth = Math.max(1.1, c.getDouble("growth", 2.0));
            cat.maxLevel = Math.max(1, c.getInt("max-level", 20));
            cat.milestoneCoins = Math.max(0, c.getInt("milestone-coins", 0));
            cat.milestoneCommands = c.getStringList("milestone-commands");
            this.categories.put(cat.id, cat);
        }
    }

    private synchronized void flush() {
        for (Map.Entry<UUID, Map<String, Long>> entry : this.counts.entrySet()) {
            for (Map.Entry<String, Long> entry2 : entry.getValue().entrySet()) {
                this.data.set("players." + String.valueOf(entry.getKey()) + "." + entry2.getKey() + ".count", (Object)entry2.getValue());
            }
        }
        for (Map.Entry<UUID, Map<String, Integer>> entry : this.levels.entrySet()) {
            for (Map.Entry<String, Integer> entry3 : entry.getValue().entrySet()) {
                this.data.set("players." + String.valueOf(entry.getKey()) + "." + entry3.getKey() + ".level", (Object)entry3.getValue());
            }
        }
        try {
            this.data.save(this.dataFile);
        }
        catch (Exception ex) {
            this.getLogger().warning("save failed: " + ex.getMessage());
        }
    }

    private long getCount(UUID u, String cat) {
        return this.counts.computeIfAbsent(u, k -> new HashMap<>()).computeIfAbsent(cat, k -> Long.valueOf(this.data.getLong("players." + String.valueOf(u) + "." + cat + ".count", 0L)));
    }

    private int getLevel(UUID u, String cat) {
        return Math.max(1, this.levels.computeIfAbsent(u, k -> new HashMap<>()).computeIfAbsent(cat, k -> Integer.valueOf(this.data.getInt("players." + String.valueOf(u) + "." + cat + ".level", 1))));
    }

    private long threshold(Category c, int level) {
        double t = (double)c.base * Math.pow(c.growth, level - 1);
        return (long)Math.ceil(t);
    }

    /** Public bridge for other MAVO plugins (e.g. MAVOCasino wager/winnings tracking). */
    public void externalProgress(Player p, String catId, long amount) {
        if (amount > 0) this.progress(p, catId, amount);
    }

    /** Highwater categories (record balance / biggest hoard): only ever counts UP to value. */
    public void externalHighwater(Player p, String catId, long value) {
        if (this.categories.get(catId) == null) return;
        long cur = this.getCount(p.getUniqueId(), catId);
        if (value > cur) this.progress(p, catId, value - cur);
    }

    private void progress(Player p, String catId, long amount) {
        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL) {
            return; // creative/spectator work never counts
        }
        Category c = this.categories.get(catId);
        if (c == null) {
            return;
        }
        UUID u = p.getUniqueId();
        long count = this.getCount(u, catId) + amount;
        this.counts.get(u).put(catId, count);
        this.dirty = true;
        int level = this.getLevel(u, catId);
        boolean leveled = false;
        while (level < c.maxLevel && count >= this.threshold(c, level + 1)) {
            leveled = true;
            this.levels.get(u).put(catId, ++level);
            if (level % this.rewardEvery != 0) continue;
            this.milestone(p, c, level);
        }
        if (leveled) {
            int lv = level;
            String next = lv >= c.maxLevel ? String.valueOf(ChatColor.GOLD) + " MAX!" : String.valueOf(ChatColor.GRAY) + " (next: " + this.fmt(this.threshold(c, lv + 1)) + " " + c.what + ")";
            p.sendMessage(c.display + String.valueOf(ChatColor.GREEN) + " Level " + lv + "!" + next);
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.4f);
        }
    }

        private void milestone(Player p, Category c, int level) {
        long coins;
        if (c.id != null && c.id.startsWith("kill_")) {
            coins = 5000L; // free mobfarm entry key every 5 levels
        } else {
            coins = (long)c.milestoneCoins * (long)level;
        }
        if (coins > 0L && this.econ != null) {
            this.econ.depositPlayer((OfflinePlayer)p, (double)coins);
        }
        for (String cmd : c.milestoneCommands) {
            Bukkit.dispatchCommand((CommandSender)Bukkit.getConsoleSender(), (String)cmd.replace("%player%", p.getName()).replace("%level%", String.valueOf(level)).replace("%category%", c.id));
        }
        Bukkit.broadcastMessage((String)(String.valueOf(ChatColor.AQUA) + p.getName() + String.valueOf(ChatColor.GRAY) + " reached " + c.display + String.valueOf(ChatColor.GOLD) + " Level " + level + String.valueOf(ChatColor.GRAY) + " milestone!" + (String)(coins > 0L ? String.valueOf(ChatColor.YELLOW) + " +" + this.fmt(coins) + " coins" : "")));
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.0f);
    }

    private String fmt(long n) {
        return String.format(Locale.UK, "%,d", n);
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onBreak(BlockBreakEvent e) {
        Material m = e.getBlock().getType();
        if (CROPS.contains(m)) {
            this.progress(e.getPlayer(), "farming", 1L);
        } else if (Tag.LOGS.isTagged(m)) {
            this.progress(e.getPlayer(), "woodcutting", 1L);
        } else {
            this.progress(e.getPlayer(), "mining", 1L);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onPlace(BlockPlaceEvent e) {
        this.progress(e.getPlayer(), "building", 1L);
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player killer = e.getEntity().getKiller();
        if (killer != null && !(e.getEntity() instanceof Player)) {
            this.progress(killer, "combat", 1L);
            // per-mob mastery (kill_zombie, kill_skeleton, …) — was never ticked before
            String key = "kill_" + e.getEntityType().name().toLowerCase(Locale.ROOT);
            if (this.categories.containsKey(key)) {
                this.progress(killer, key, 1L);
            } else {
                // fallback alias only when dedicated category missing
                String alias = switch (e.getEntityType()) {
                    case HUSK, DROWNED, ZOMBIE_VILLAGER -> "kill_zombie";
                    case STRAY, BOGGED, WITHER_SKELETON -> "kill_skeleton";
                    case CAVE_SPIDER -> "kill_spider";
                    case ELDER_GUARDIAN -> "kill_guardian";
                    case MAGMA_CUBE -> "kill_slime";
                    default -> null;
                };
                if (alias != null && this.categories.containsKey(alias)) {
                    this.progress(killer, alias, 1L);
                }
            }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            this.progress(e.getPlayer(), "fishing", 1L);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || !e.getFrom().getWorld().equals((Object)e.getTo().getWorld())) {
            return;
        }
        double d = e.getFrom().toVector().setY(0).distance(e.getTo().toVector().setY(0));
        if (d <= 0.0 || d > 15.0) {
            return;
        }
        UUID u = e.getPlayer().getUniqueId();
        double buf = this.moveBuffer.getOrDefault(u, 0.0) + d;
        if (buf >= 10.0) {
            this.progress(e.getPlayer(), "travel", (long)buf);
            buf -= (double)((long)buf);
        }
        this.moveBuffer.put(u, buf);
    }

    private void openGui(Player p) {
        openGui(p, 0);
    }

    private void openGui(Player p, int page) {
        MenuHolder holder = new MenuHolder(this);
        List<Category> list = new ArrayList<>(this.categories.values());
        final int perPage = 28; // 4 rows x 7 usable in a 6-row chest with chrome
        int pages = Math.max(1, (list.size() + perPage - 1) / perPage);
        if (page < 0) page = 0;
        if (page >= pages) page = pages - 1;
        holder.page = page;
        holder.view = "main";

        Inventory inv = Bukkit.createInventory(holder, 54,
                ChatColor.DARK_RED + "" + ChatColor.BOLD + "Achievements"
                        + ChatColor.DARK_GRAY + " (" + (page + 1) + "/" + pages + ")");
        holder.inv = inv;
        ItemStack fill = this.named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ", null);
        for (int i = 0; i < inv.getSize(); ++i) inv.setItem(i, fill);

        // content slots: rows 1-4 (slots 10-16, 19-25, 28-34, 37-43) = 28
        int[] contentSlots = {
                10,11,12,13,14,15,16,
                19,20,21,22,23,24,25,
                28,29,30,31,32,33,34,
                37,38,39,40,41,42,43
        };

        int totalLevels = 0;
        int maxTotal = 0;
        for (Category c : list) {
            totalLevels += this.getLevel(p.getUniqueId(), c.id);
            maxTotal += c.maxLevel;
        }

        int from = page * perPage;
        int to = Math.min(list.size(), from + perPage);
        int slotIdx = 0;
        for (int idx = from; idx < to; idx++) {
            Category c = list.get(idx);
            UUID u = p.getUniqueId();
            long count = this.getCount(u, c.id);
            int level = this.getLevel(u, c.id);
            ItemStack it = new ItemStack(c.icon != null ? c.icon : Material.PAPER);
            ArrayList<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Level: " + ChatColor.YELLOW + level + ChatColor.GRAY + " / " + c.maxLevel);
            lore.add(ChatColor.GRAY + "Total: " + ChatColor.YELLOW + this.fmt(count) + " " + c.what);
            lore.add("");
            if (level >= c.maxLevel) {
                lore.add(ChatColor.GOLD + "★ MAXED OUT!");
            } else {
                long need = this.threshold(c, level + 1);
                long prev = level == 0 ? 0L : this.threshold(c, level);
                long into = Math.max(0L, count - prev);
                long span = Math.max(1L, need - prev);
                int filled = (int) Math.min(20L, into * 20L / span);
                StringBuilder bar = new StringBuilder(ChatColor.DARK_GRAY + "[" + ChatColor.GREEN);
                for (int b = 0; b < filled; ++b) bar.append("|");
                bar.append(ChatColor.GRAY);
                for (int b = filled; b < 20; ++b) bar.append("|");
                bar.append(ChatColor.DARK_GRAY).append("]");
                lore.add(bar.toString());
                lore.add(ChatColor.GRAY + "Next level at " + ChatColor.YELLOW + this.fmt(need) + ChatColor.GRAY + " " + c.what);
                int nextMile = (level / this.rewardEvery + 1) * this.rewardEvery;
                if (nextMile <= c.maxLevel) {
                    long coins = (c.id != null && c.id.startsWith("kill_")) ? 5000L : (long) c.milestoneCoins * (long) nextMile;
                    lore.add(ChatColor.GRAY + "Milestone L" + nextMile + ": " + ChatColor.YELLOW + this.fmt(coins) + " coins");
                }
            }
            lore.add("");
            lore.add(ChatColor.YELLOW + "▶ Click for the full level breakdown");
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                meta.setDisplayName(c.display);
                meta.setLore(lore);
                it.setItemMeta(meta);
            }
            int slot = contentSlots[slotIdx++];
            inv.setItem(slot, it);
            holder.catSlots.put(slot, c.id);
        }

        // footer
        inv.setItem(45, this.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "◀ Prev page",
                Arrays.asList(ChatColor.GRAY + "Page " + (page + 1) + " / " + pages)));
        inv.setItem(49, this.named(new ItemStack(Material.PLAYER_HEAD), ChatColor.AQUA + p.getName(),
                Arrays.asList(ChatColor.GRAY + "Total levels: " + ChatColor.YELLOW + totalLevels + ChatColor.GRAY + " / " + maxTotal,
                        ChatColor.GRAY + "Categories: " + ChatColor.YELLOW + list.size(),
                        ChatColor.GRAY + "Milestone every " + ChatColor.YELLOW + this.rewardEvery + ChatColor.GRAY + " levels")));
        inv.setItem(53, this.named(new ItemStack(Material.ARROW), ChatColor.YELLOW + "Next page ▶",
                Arrays.asList(ChatColor.GRAY + "Page " + (page + 1) + " / " + pages)));

        p.openInventory(inv);
    }

    private ItemStack named(ItemStack it, String name, List<String> lore) {
        if (it == null) it = new ItemStack(Material.PAPER);
        ItemMeta m = it.getItemMeta();
        if (m == null) return it;
        m.setDisplayName(name);
        if (lore != null) m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private void openDetail(Player p, String catId) {
        Category c = this.categories.get(catId);
        if (c == null) return;
        MenuHolder holder = new MenuHolder(this);
        holder.view = "detail";
        Inventory inv = Bukkit.createInventory((InventoryHolder)holder, 27,
                String.valueOf(ChatColor.DARK_RED) + String.valueOf(ChatColor.BOLD) + "Achievements" + String.valueOf(ChatColor.DARK_GRAY) + " \u25B8 " + ChatColor.stripColor(c.display));
        holder.inv = inv;
        ItemStack fill = this.named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ", null);
        for (int i = 0; i < inv.getSize(); ++i) inv.setItem(i, fill);

        UUID u = p.getUniqueId();
        long count = this.getCount(u, c.id);
        int level = this.getLevel(u, c.id);

        // center: the big breakdown page
        ItemStack page = new ItemStack(c.icon != null ? c.icon : Material.PAPER);
        ItemMeta meta = page.getItemMeta();
        if (meta == null) return;
        meta.setDisplayName(c.display);
        ArrayList<String> lore = new ArrayList<String>();
        lore.add(String.valueOf(ChatColor.GRAY) + "Lifetime total: " + String.valueOf(ChatColor.YELLOW) + this.fmt(count) + " " + c.what);
        lore.add(String.valueOf(ChatColor.GRAY) + "Level: " + String.valueOf(ChatColor.YELLOW) + level + String.valueOf(ChatColor.GRAY) + " / " + c.maxLevel);
        lore.add("");
        lore.add(String.valueOf(ChatColor.GOLD) + String.valueOf(ChatColor.BOLD) + "LEVEL ROAD MAP");
        int shown = 0;
        int start = Math.max(1, level - 1);
        for (int lv = start; lv <= c.maxLevel && shown < 10; ++lv, ++shown) {
            long need = this.threshold(c, lv);
            boolean done = level >= lv;
            boolean mile = lv % this.rewardEvery == 0;
            String mark = done ? String.valueOf(ChatColor.GREEN) + "\u2714 " : String.valueOf(ChatColor.GRAY) + "\u2022 ";
            long mileCoins = (c.id != null && c.id.startsWith("kill_")) ? 5000L : (long)c.milestoneCoins * (long)lv;
            String coin = mile ? String.valueOf(ChatColor.YELLOW) + "  +" + this.fmt(mileCoins) + " coins" : "";
            lore.add(mark + (done ? ChatColor.GREEN : ChatColor.WHITE) + "L" + lv + " "
                    + String.valueOf(ChatColor.GRAY) + this.fmt(need) + " " + c.what + coin);
        }
        if (level < c.maxLevel) {
            long need = this.threshold(c, level + 1);
            long prev = level == 0 ? 0L : this.threshold(c, level);
            lore.add("");
            lore.add(String.valueOf(ChatColor.GRAY) + "To next level: " + String.valueOf(ChatColor.YELLOW)
                    + this.fmt(Math.max(0L, need - count)) + String.valueOf(ChatColor.GRAY) + " more " + c.what);
        } else {
            lore.add("");
            lore.add(String.valueOf(ChatColor.GOLD) + "\u2605 MAXED OUT! Absolute legend.");
        }
        lore.add("");
        lore.add(String.valueOf(ChatColor.GRAY) + "Milestone every " + String.valueOf(ChatColor.YELLOW) + this.rewardEvery
                + String.valueOf(ChatColor.GRAY) + " levels pays " + String.valueOf(ChatColor.YELLOW) + c.milestoneCoins + " coins \u00D7 level");
        lore.add(String.valueOf(ChatColor.GRAY) + "Counts in SURVIVAL only.");
        meta.setLore(lore);
        page.setItemMeta(meta);
        inv.setItem(13, page);

        inv.setItem(18, this.named(new ItemStack(Material.ARROW), String.valueOf(ChatColor.YELLOW) + "\u25C0 Back", null));
        inv.setItem(26, this.named(new ItemStack(Material.BARRIER), String.valueOf(ChatColor.RED) + "Close", null));
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        int slot = e.getRawSlot();
        if ("main".equals(h.view)) {
            if (slot == 45) { this.openGui(p, Math.max(0, h.page - 1)); return; }
            if (slot == 53) { this.openGui(p, h.page + 1); return; }
            String cat = h.catSlots.get(slot);
            if (cat != null) this.openDetail(p, cat);
        } else if ("detail".equals(h.view)) {
            if (slot == 18) this.openGui(p, h.page);
            else if (slot == 26) p.closeInventory();
        }
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mavoach.admin")) {
                sender.sendMessage(String.valueOf(ChatColor.RED) + "No permission.");
                return true;
            }
            this.flush();
            this.loadCfg();
            sender.sendMessage(String.valueOf(ChatColor.GREEN) + "MAVOAchievements reloaded (" + this.categories.size() + " categories).");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Categories: " + String.join((CharSequence)", ", this.categories.keySet()));
            return true;
        }
        this.openGui((Player)sender);
        return true;
    }

    static final class Category {
        String id;
        String display;
        String what;
        Material icon;
        long base;
        double growth;
        int maxLevel;
        int milestoneCoins;
        List<String> milestoneCommands = new ArrayList<String>();

        Category() {
        }
    }

    static final class MenuHolder implements InventoryHolder {
        Inventory inv;
        String view = "main";
        int page = 0;
        final Map<Integer, String> catSlots = new HashMap<>();
        final Achievements plugin;

        MenuHolder(Achievements plugin) {
            this.plugin = plugin;
        }

        @Override
        public Inventory getInventory() {
            return this.inv;
        }
    }
}


