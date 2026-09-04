package mavo.auction;

import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * MAVOAuctionHouse 1.0.0 - community auction house.
 *
 * - fancy bedrock-box auction house with keeper villagers (same GUI)
 * - /ah /auctionhouse /auction + /inbox
 * - /ah add hand <amount> <price> <duration>, /ah add <material> <amount> <price> <duration>
 * - slots: 1 free, unlock up to 20 (50k/100 Lucky, 100k/200, 150k/300 ...)
 * - 20% tax when posted via command, 5% when posted from the AH villagers
 * - buy -> item to buyer inbox "auction" (bound: cannot be re-listed); seller paid net
 * - expire -> seller inbox "auction expired" (no tax); cancel -> inbox, no cost, no cooldown
 * - successful sale locks ONLY that slot for 10 Minecraft days; expiry/cancel do not
 * - min price = 110% of the EconomyShopGUI shop SELL price (runtime scan) - anti-exploit
 */
public class AuctionHouse extends org.bukkit.plugin.java.JavaPlugin implements Listener {

    private static final int MAX_SLOTS = 20;
    private static final int INBOX_MAX = 100;
    private static final long MC_DAY_MS = 1_200_000L;          // 20 real minutes
    private static final long COOLDOWN_MS = 10 * MC_DAY_MS;    // 10 Minecraft days
    private static final double COMMAND_TAX = 0.20;
    private static final double KEEPER_TAX = 0.05;
    private static final long KEEPER_TOUCH_MS = 120_000L;      // "posted at the AH" window

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;
    private final NamespacedKey boundKey = new NamespacedKey("mavoauction", "bound");
    private final NamespacedKey keeperKey = new NamespacedKey("mavoauction", "keeper");
    private final NamespacedKey luckyKey = new NamespacedKey("mavoluckycoins", "luckycoin");
    private final NamespacedKey profLockKey = new NamespacedKey("mavoprofessions", "proflock");

    private Location regionCenter;
    private int regionHalf = 7;

    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final Map<Material, Long> shopSell = new HashMap<>();

    private record Listing(String id, UUID seller, int slot, ItemStack item, long price,
                           long priceMoved, long end, String tier, long posted) {}

    /* ------------------------------------------------------------------ lifecycle */

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        if (econ == null) getLogger().warning("No Vault economy found - coins won't work!");
        loadSettings();
        loadAll();
        loadShopPrices();
        getServer().getPluginManager().registerEvents(this, this);
        respawnKeepers();
        startExpiryTask();
        getLogger().info("MAVOAuctionHouse 1.0.0 enabled. shopSell=" + shopSell.size()
                + " listings=" + listings.size() + " region=" + (regionCenter != null));
    }

    @Override public void onDisable() {
        saveData();
    }

    private void loadSettings() {
        ConfigurationSection c = getConfig().getConfigurationSection("center");
        if (c != null) {
            World w = Bukkit.getWorld(c.getString("world", "world"));
            if (w != null) regionCenter = new Location(w, c.getInt("x"), c.getInt("y"), c.getInt("z"));
        }
        regionHalf = Math.max(1, getConfig().getInt("half", 7));
    }

    private void loadAll() {
        listings.clear();
        ConfigurationSection ls = data.getConfigurationSection("listings");
        if (ls != null) for (String id : ls.getKeys(false)) {
            ConfigurationSection e = ls.getConfigurationSection(id);
            if (e == null) continue;
            try {
                UUID seller = UUID.fromString(e.getString("seller", ""));
                int slot = e.getInt("slot", 1);
                long price = e.getLong("price", 0);
                long end = e.getLong("end", 0);
                String tier = e.getString("tier", "command");
                long posted = e.getLong("posted", 0);
                ItemStack it = loadItem(e.getConfigurationSection("item"));
                if (it == null) continue;
                listings.put(id, new Listing(id, seller, slot, it, price, 0, end, tier, posted));
            } catch (Throwable t) { getLogger().warning("Skip listing " + id + ": " + t.getMessage()); }
        }
    }

    private void saveData() {
        try { data.save(dataFile); } catch (Throwable ignored) {}
    }

    private static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
    private static String fmt(long n) { return String.format("%,d", n); }

    private void msg(Player p, String s) { p.sendMessage(color("&8[&6AH&8] " + s)); }

    /* ------------------------------------------------------------------ items */

    private void saveItem(ConfigurationSection sec, ItemStack it) {
        for (String k : sec.getKeys(false)) sec.set(k, null);
        for (Map.Entry<String, Object> en : it.serialize().entrySet()) sec.set(en.getKey(), en.getValue());
    }

    @SuppressWarnings("unchecked")
    private ItemStack loadItem(ConfigurationSection sec) {
        if (sec == null) return null;
        try {
            Map<String, Object> m = new HashMap<>(sec.getValues(true));
            return ItemStack.deserialize(m);
        } catch (Throwable t) { return null; }
    }

    private boolean isBound(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(boundKey, PersistentDataType.STRING);
    }
    private boolean isProfLocked(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(profLockKey, PersistentDataType.BYTE);
    }
    private ItemStack bindItem(ItemStack it, UUID owner) {
        ItemStack out = it.clone();
        ItemMeta m = out.getItemMeta();
        if (m == null) return out;
        m.getPersistentDataContainer().set(boundKey, PersistentDataType.STRING, owner.toString());
        List<String> lore = m.getLore() == null ? new ArrayList<>() : new ArrayList<>(m.getLore());
        lore.add(ChatColor.DARK_GRAY + "⛓ " + ChatColor.GRAY + "Auction-bound (cannot be re-listed)");
        m.setLore(lore);
        out.setItemMeta(m);
        return out;
    }

    private long countLucky(Player p) {
        long n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (isLucky(it)) n += it.getAmount();
        return n;
    }
    private boolean isLucky(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(luckyKey, PersistentDataType.BYTE);
    }
    private boolean chargeLucky(Player p, long need) {
        if (countLucky(p) < need) return false;
        long left = need;
        ItemStack[] inv = p.getInventory().getContents();
        for (int i = 0; i < inv.length && left > 0; i++) {
            ItemStack it = inv[i];
            if (!isLucky(it)) continue;
            int take = (int) Math.min(left, it.getAmount());
            it.setAmount(it.getAmount() - take);
            if (it.getAmount() <= 0) p.getInventory().setItem(i, null);
            left -= take;
        }
        return true;
    }

    /* ------------------------------------------------------------------ shop sell prices (anti-exploit floor) */

    private void loadShopPrices() {
        shopSell.clear();
        File shops = new File(getDataFolder().getParentFile(), "EconomyShopGUI/shops");
        if (!shops.isDirectory()) shops = new File(getDataFolder().getParentFile(), "EconomyShopGUI");
        scanYml(shops);
        ConfigurationSection over = getConfig().getConfigurationSection("min-sell-prices");
        if (over != null) for (String k : over.getKeys(false)) {
            Material m = Material.matchMaterial(k);
            if (m != null && over.getLong(k, 0) > 0) shopSell.put(m, over.getLong(k));
        }
        if (!shopSell.isEmpty())
            getLogger().info("AuctionHouse shop sell prices loaded: " + shopSell.size()
                    + " items (netherite ingot " + shopSell.getOrDefault(Material.NETHERITE_INGOT, 0L) + ").");
    }

    private void scanYml(File dir) {
        File[] files = dir.listFiles();
        if (files == null) return;
        for (File f : files) {
            if (f.isDirectory()) { scanYml(f); continue; }
            if (!f.getName().toLowerCase(Locale.ROOT).endsWith(".yml")) continue;
            try {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                walk(y.getRoot(), "");
            } catch (Throwable ignored) {}
        }
    }

    @SuppressWarnings("unchecked")
    private void walk(Map<String, Object> node, String path) {
        for (Map.Entry<String, Object> en : node.entrySet()) {
            Object v = en.getValue();
            if (v instanceof Map) { walk((Map<String, Object>) v, path + en.getKey() + "."); continue; }
            String key = en.getKey().toLowerCase(Locale.ROOT).replace("_", "-").replace(" ", "");
            if (!(key.equals("sell-price") || key.equals("sellprice") || key.equals("sell"))) continue;
            long price = parseNum(v);
            if (price <= 0) continue;
            Material m = Material.matchMaterial(lastMaterialKey(path));
            if (m != null && price > shopSell.getOrDefault(m, 0L)) shopSell.put(m, price);
        }
    }

    private static String lastMaterialKey(String path) {
        String[] parts = path.split("\\.");
        for (int i = parts.length - 1; i >= 0; i--) {
            String s = parts[i].trim();
            if (s.isEmpty()) continue;
            if (Material.matchMaterial(s) != null) return s;
        }
        return null;
    }

    private static long parseNum(Object v) {
        try {
            if (v instanceof Number n) return n.longValue();
            return Long.parseLong(v.toString().replace(",", "").replace("$", "").trim());
        } catch (Throwable t) { return -1; }
    }

    private long minPrice(ItemStack it) {
        Long sale = shopSell.get(it.getType());
        if (sale == null || sale <= 0) return 0;
        double factor = Math.max(1.01, getConfig().getDouble("min-price-factor", 1.10));
        return (long) Math.ceil(sale * factor);
    }

    /* ------------------------------------------------------------------ slots / cooldown / listings */

    private int slotsOf(UUID u) {
        return Math.max(1, Math.min(MAX_SLOTS, data.getInt("players." + u + ".slots", 1)));
    }
    private void setSlots(UUID u, int n) {
        data.set("players." + u + ".slots", Math.max(1, Math.min(MAX_SLOTS, n)));
        saveData();
    }
    private long unlockCostCoins(int current) { return 50_000L * current; }          // slot current+1
    private long unlockCostLucky(int current) { return 100L * current; }

    private List<Listing> activeOf(UUID u) {
        List<Listing> out = new ArrayList<>();
        for (Listing l : listings.values()) if (l.seller().equals(u)) out.add(l);
        return out;
    }
    private long cooldownUntil(UUID u, int slot) {
        return data.getLong("players." + u + ".cooldowns." + slot, 0L);
    }
    private boolean slotFree(UUID u, int slot) {
        if (System.currentTimeMillis() < cooldownUntil(u, slot)) return false;
        for (Listing l : listings.values())
            if (l.seller().equals(u) && l.slot() == slot) return false;
        return true;
    }
    private int freeSlot(UUID u) {
        int n = slotsOf(u);
        for (int s = 1; s <= n; s++) if (slotFree(u, s)) return s;
        return -1;
    }
    private void lockSlot(UUID u, int slot) {
        data.set("players." + u + ".cooldowns." + slot, System.currentTimeMillis() + COOLDOWN_MS);
        saveData();
    }

    private String newId() {
        long t = System.currentTimeMillis();
        String r = Long.toString(t, 36).toUpperCase(Locale.ROOT)
                + Integer.toHexString(ThreadLocalRandom.current().nextInt(0xFFFF)).toUpperCase(Locale.ROOT);
        String id = "A" + r;
        while (listings.containsKey(id)) id = "A" + r + ThreadLocalRandom.current().nextInt(10);
        return id;
    }

    /* ------------------------------------------------------------------ inbox */

    private List<Map<String, Object>> inboxOf(UUID u) {
        List<Map<String, Object>> out = new ArrayList<>();
        ConfigurationSection s = data.getConfigurationSection("players." + u + ".inbox");
        if (s == null) return out;
        for (String k : s.getKeys(false)) {
            ConfigurationSection e = s.getConfigurationSection(k);
            if (e == null) continue;
            Map<String, Object> m = new HashMap<>();
            m.put("item", e.getConfigurationSection("item"));
            m.put("tag", e.getString("tag", "auction"));
            m.put("time", e.getLong("time", 0));
            out.add(m);
        }
        return out;
    }

    private int inboxCount(UUID u) {
        ConfigurationSection s = data.getConfigurationSection("players." + u + ".inbox");
        return s == null ? 0 : s.getKeys(false).size();
    }

    private boolean inboxAdd(UUID u, ItemStack it, String tag) {
        if (inboxCount(u) >= INBOX_MAX) return false;
        String k = "e" + System.currentTimeMillis() + "_" + ThreadLocalRandom.current().nextInt(1000);
        ConfigurationSection e = data.createSection("players." + u + ".inbox." + k);
        saveItem(e.createSection("item"), it);
        e.set("tag", tag);
        e.set("time", System.currentTimeMillis());
        saveData();
        return true;
    }

    private void inboxRemove(UUID u, String key) {
        data.set("players." + u + ".inbox." + key, null);
        saveData();
    }

    /* ------------------------------------------------------------------ posts */

    private String tierFor(Player p, boolean touchedKeeperNow) {
        long touch = data.getLong("players." + p.getUniqueId() + ".keeper", 0L);
        boolean touched = touchedKeeperNow || (System.currentTimeMillis() - touch) < KEEPER_TOUCH_MS;
        if (touched && inAH(p.getLocation())) return "keeper";
        return "command";
    }

    private void commandPost(Player p, String what, int amount, long price, String dur) {
        int hours = parseDur(dur);
        if (hours <= 0) { msg(p, "&cDuration from 1h to 48h (e.g. 12h, 90m, 2d)."); return; }
        ItemStack held = p.getInventory().getItemInMainHand();
        ItemStack src = "hand".equalsIgnoreCase(what) ? held :
                matchMaterial(p.getInventory().getContents(), what);
        if (src == null || src.getType() == Material.AIR) { msg(p, "&cHeld item not found (or use a material name)."); return; }
        if (amount <= 0) amount = src.getAmount();
        if (amount > src.getAmount()) { msg(p, "&cYou only have &e" + src.getAmount() + "&c of that."); return; }
        if (amount > 64) { msg(p, "&cA listing holds at most 64 items."); return; }
        ItemStack listingItem = src.clone();
        listingItem.setAmount(amount);
        String err = validate(p, listingItem, price, hours, true);
        if (err != null) { msg(p, err); return; }
        int slot = freeSlot(p.getUniqueId());
        if (slot < 0) { msg(p, "&cNo free slot (buy more in /ah slots or wait out a cooldown)."); return; }
        // remove from inventory, then store
        ItemStack rm = src.clone(); rm.setAmount(amount);
        p.getInventory().removeItem(rm);
        String tier = tierFor(p, false);
        finishPost(p, listingItem, price, hours, slot, tier);
    }

    private ItemStack matchMaterial(ItemStack[] inv, String what) {
        String w = what == null ? "" : what.replace("minecraft:", "").toUpperCase(Locale.ROOT);
        for (ItemStack it : inv) if (it != null && it.getType() != Material.AIR)
            if (it.getType().name().equalsIgnoreCase(w)) return it;
        return null;
    }

    private String validate(Player p, ItemStack it, long price, int hours, boolean fromCommand) {
        if (it.getType() == Material.AIR) return "&cCannot auction air.";
        if (isBound(it)) return "&cAuction-bound items cannot be re-listed.";
        if (isProfLocked(it)) return "&cProfession-bound items cannot be auctioned.";
        if (hours < 1 || hours > 48) return "&cDuration must be 1h - 48h.";
        long max = Math.max(1, getConfig().getLong("max-price", 1_000_000_000L));
        if (price < 1) return "&cPrice must be at least 1 coin.";
        if (price > max) return "&cPrice above the " + fmt(max) + " cap.";
        long min = minPrice(it);
        if (min > 0 && price < min)
            return "&cToo cheap - shop sells " + it.getType().name().toLowerCase(Locale.ROOT)
                    + " for &e" + fmt(shopSell.getOrDefault(it.getType(), 0L))
                    + "&c, minimum posting &e" + fmt(min) + "&c (shop sell x 110%).";
        return null;
    }

    private void finishPost(Player p, ItemStack it, long price, int hours, int slot, String tier) {
        String id = newId();
        long end = System.currentTimeMillis() + hours * 3_600_000L;
        listings.put(id, new Listing(id, p.getUniqueId(), slot, it, price, 0, end, tier, System.currentTimeMillis()));
        ConfigurationSection e = data.createSection("listings." + id);
        e.set("seller", p.getUniqueId().toString());
        e.set("slot", slot);
        e.set("price", price);
        e.set("end", end);
        e.set("tier", tier);
        e.set("posted", System.currentTimeMillis());
        saveItem(e.createSection("item"), it);
        saveData();
        double tax = "keeper".equals(tier) ? KEEPER_TAX : COMMAND_TAX;
        msg(p, "&aPosted &e" + it.getAmount() + "x " + it.getType().name().toLowerCase(Locale.ROOT)
                + " &afor &e" + fmt(price) + " coins &a(" + hours + "h)."
                + ("keeper".equals(tier) ? " &a5% tax (posted at the AH)." : " &c20% tax (posted by command)."));
        p.closeInventory();
        openMain(p, 0);
    }

    private void purchase(Player buyer, Listing l) {
        UUID u = buyer.getUniqueId();
        if (l.seller().equals(u)) { msg(buyer, "&cYou can't buy your own auction."); return; }
        if (inboxCount(u) >= INBOX_MAX) { msg(buyer, "&cYour inbox is full - collect items first."); return; }
        double price = l.price();
        EconomyResponse r = econ == null ? null : econ.withdrawPlayer(buyer, price);
        if (r == null || !r.transactionSuccess()) { msg(buyer, "&cYou need &e" + fmt(l.price()) + " coins&c."); return; }
        double tax = "keeper".equals(l.tier()) ? KEEPER_TAX : COMMAND_TAX;
        long net = (long) Math.round(price * (1 - tax));
        ItemStack bound = bindItem(l.item(), u);
        if (!inboxAdd(u, bound, "auction")) {
            paySeller(buyer.getUniqueId(), (long) price);  // refund, listing untouched
            msg(buyer, "&cYour inbox is full - nothing was bought.");
            return;
        }
        paySeller(l.seller(), net);
        String id = l.id();
        listings.remove(id);
        data.set("listings." + id, null);
        lockSlot(l.seller(), l.slot());
        msg(buyer, "&aBought &e" + l.item().getAmount() + "x " + l.item().getType().name().toLowerCase(Locale.ROOT)
                + " &afor &e" + fmt(l.price()) + " coins. &7Item is in your &e/inbox&7 (tag: auction).");
        saveData();
        openMain(buyer, 0);
    }

    private void paySeller(UUID seller, long amount) {
        if (amount <= 0) return;
        OfflinePlayer op = Bukkit.getOfflinePlayer(seller);
        if (econ != null && econ.hasAccount(op) && econ.depositPlayer(op, amount).transactionSuccess()) return;
        data.set("players." + seller + ".pending", data.getDouble("players." + seller + ".pending", 0) + amount);
        saveData();
        Player lp = Bukkit.getPlayer(seller);
        if (lp != null && lp.isOnline()) msg(lp, "&aYour auction payout &e" + fmt(amount) + " coins &ais waiting (server restarts payout it).");
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        String p = "players." + e.getPlayer().getUniqueId() + ".pending";
        double pending = data.getDouble(p, 0);
        if (pending > 0 && econ != null) {
            if (econ.depositPlayer(e.getPlayer(), pending).transactionSuccess()) {
                msg(e.getPlayer(), "&aPaid you &e" + fmt((long) pending) + " coins &afrom past auctions.");
                data.set(p, null); saveData();
            }
        }
    }

    private void cancelListing(Player p, String id) {
        Listing l = listings.get(id);
        if (l == null || !l.seller().equals(p.getUniqueId())) { msg(p, "&cNo such listing of yours."); return; }
        listings.remove(id);
        data.set("listings." + id, null);
        boolean ok = inboxAdd(l.seller(), l.item(), "auction cancelled");
        msg(p, ok ? "&aCancelled &7(ID &e" + id + "&7)&a - item moved to your &e/inbox&a (tag: auction cancelled)."
                : "&cInbox full - contact an admin (item stays in the listing data).");
        saveData();
        if (p.isOnline()) openMain(p, 0);
    }

    private void expireAll() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        for (Listing l : new ArrayList<>(listings.values())) {
            if (l.end() <= now) {
                listings.remove(l.id());
                data.set("listings." + l.id(), null);
                boolean ok = inboxAdd(l.seller(), l.item(), "auction expired");
                if (ok) { changed = true;
                    Player sp = Bukkit.getPlayer(l.seller());
                    if (sp != null && sp.isOnline())
                        msg(sp, "&eYour auction &7(" + l.id() + ") &eexpired - item moved to &e/inbox&7 (tag: auction expired).&8 No tax.");
                }
            }
        }
        if (changed) saveData();
    }

    private void startExpiryTask() {
        new BukkitRunnable() { @Override public void run() { expireAll(); } }.runTaskTimer(this, 400L, 600L);
    }

    /* ------------------------------------------------------------------ duration helpers */

    private int parseDur(String s) {
        if (s == null || s.isEmpty()) return 0;
        try {
            s = s.trim().toLowerCase(Locale.ROOT);
            double v;
            if (s.endsWith("h")) v = Double.parseDouble(s.substring(0, s.length() - 1));
            else if (s.endsWith("m")) v = Double.parseDouble(s.substring(0, s.length() - 1)) / 60.0;
            else if (s.endsWith("d")) v = Double.parseDouble(s.substring(0, s.length() - 1)) * 24.0;
            else v = Double.parseDouble(s);
            return (int) Math.round(v);
        } catch (Throwable t) { return 0; }
    }

    private static String durText(long ms) {
        long min = Math.max(0, ms / 60_000L);
        long h = min / 60, m = min % 60;
        if (h >= 48) return (h / 24) + "d " + (h % 24) + "h";
        return h + "h " + String.format("%02dm", m);
    }

    /* ------------------------------------------------------------------ AH region / protection */

    private boolean inAH(Location l) {
        if (l == null || regionCenter == null) return false;
        if (!l.getWorld().equals(regionCenter.getWorld())) return false;
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return Math.abs(x - regionCenter.getBlockX()) <= regionHalf
                && Math.abs(z - regionCenter.getBlockZ()) <= regionHalf
                && y >= regionCenter.getBlockY() - 1 && y <= regionCenter.getBlockY() + regionHalf + 2;
    }

    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (inAH(e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "The auction house is protected.");
        }
    }
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (inAH(e.getBlockPlaced().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "The auction house is protected.");
        }
    }
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> inAH(b.getLocation()));
    }
    @EventHandler(priority = EventPriority.LOW, ignoreCancelled = true)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (e.getEntity() instanceof LivingEntity && inAH(e.getBlock().getLocation())) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (e.getEntity() instanceof Villager v
                && v.getPersistentDataContainer().has(keeperKey, PersistentDataType.BYTE)) {
            e.setCancelled(true);
        }
    }
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onPvP(EntityDamageByEntityEvent e) {
        if (!inAH(e.getEntity().getLocation())) return;
        if (e.getEntity() instanceof Player || e.getEntity() instanceof Villager) e.setCancelled(true);
    }

    /* ------------------------------------------------------------------ villager keepers */

    @EventHandler
    public void onInteractKeeper(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof Villager v)) return;
        if (!v.getPersistentDataContainer().has(keeperKey, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
        data.set("players." + e.getPlayer().getUniqueId() + ".keeper", System.currentTimeMillis());
        saveData();
        openMain(e.getPlayer(), 0);
    }

    private void respawnKeepers() {
        if (regionCenter == null) return;
        ConfigurationSection ks = data.getConfigurationSection("keepers");
        if (ks == null) return;
        for (String k : ks.getKeys(false)) {
            ConfigurationSection e = ks.getConfigurationSection(k);
            if (e == null) continue;
            World w = regionCenter.getWorld();
            Location loc = new Location(w, e.getInt("x"), e.getInt("y"), e.getInt("z"));
            boolean found = false;
            for (Entity en : w.getNearbyEntities(loc, 1, 2, 1))
                if (en instanceof Villager v && v.getPersistentDataContainer().has(keeperKey, PersistentDataType.BYTE)
                        && k.equals(v.getPersistentDataContainer().get(keeperKey, PersistentDataType.STRING))) {
                    found = true; break;
                }
            if (found) continue;
            spawnKeeper(k, loc, e.getString("name", "&6Auction Keeper"), e.getString("profession", "LIBRARIAN"));
        }
    }

    private void spawnKeeper(String key, Location loc, String name, String prof) {
        Villager v = (Villager) loc.getWorld().spawnEntity(loc, EntityType.VILLAGER);
        v.setCustomName(color(name));
        v.setCustomNameVisible(true);
        v.setAI(false);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistent(true);
        v.setCollidable(false);
        try { v.setProfession(Villager.Profession.valueOf(prof.trim().toUpperCase(Locale.ROOT))); } catch (Throwable ignored) {}
        v.getPersistentDataContainer().set(keeperKey, PersistentDataType.STRING, key);
    }

    /* ------------------------------------------------------------------ building the house */

    private void buildHouse(Player admin) {
        World w = regionCenter.getWorld();
        int cx = regionCenter.getBlockX(), cy = regionCenter.getBlockY(), cz = regionCenter.getBlockZ();
        int h = regionHalf;
        // clear
        for (int x = -h - 2; x <= h + 2; x++)
            for (int z = -h - 2; z <= h + 2; z++)
                for (int y = -2; y <= h + 3; y++)
                    w.getBlockAt(cx + x, cy + y, cz + z).setType(Material.AIR, false);
        // bedrock box: floor, walls, ceiling (entrance = south wall opening)
        for (int x = -h; x <= h; x++)
            for (int z = -h; z <= h; z++)
                w.getBlockAt(cx + x, cy - 1, cz + z).setType(Material.BEDROCK, false);
        for (int y = 0; y <= h; y++) {
            for (int x = -h; x <= h; x++) {
                w.getBlockAt(cx + x, cy + y, cz - h).setType(Material.BEDROCK, false);
                w.getBlockAt(cx + x, cy + y, cz + h).setType(Material.BEDROCK, false);
            }
            for (int z = -h + 1; z <= h - 1; z++) {
                w.getBlockAt(cx - h, cy + y, cz + z).setType(Material.BEDROCK, false);
                w.getBlockAt(cx + h, cy + y, cz + z).setType(Material.BEDROCK, false);
            }
            // entrance opening (south, 3 wide x 3 high)
            if (y <= 2) for (int x = -1; x <= 1; x++)
                w.getBlockAt(cx + x, cy + y, cz + h).setType(Material.AIR, false);
        }
        for (int x = -h; x <= h; x++)
            for (int z = -h; z <= h; z++)
                w.getBlockAt(cx + x, cy + h, cz + z).setType(Material.BEDROCK, false);
        // fancy interior
        for (int x = -h + 1; x <= h - 1; x++)
            for (int z = -h + 1; z <= h - 1; z++) {
                Block b = w.getBlockAt(cx + x, cy, cz + z);
                b.setType(Material.POLISHED_BLACKSTONE, false);
                if (((x + z) & 1) == 0) b.setType(Material.POLISHED_BLACKSTONE_BRICKS, false);
            }
        // red carpet runner from entrance to podium
        for (int z = 1; z <= 6; z++) {
            w.getBlockAt(cx, cy + 1, cz + z).setType(Material.RED_CARPET, false);
            if (z == 6) { w.getBlockAt(cx - 1, cy + 1, cz + z).setType(Material.RED_CARPET, false);
                          w.getBlockAt(cx + 1, cy + 1, cz + z).setType(Material.RED_CARPET, false); }
        }
        // corner quartz pillars + gold accents + ceiling lanterns
        for (int[] d : new int[][]{{-h + 1, -h + 1}, {-h + 1, h - 1}, {h - 1, -h + 1}, {h - 1, h - 1}}) {
            for (int y = 0; y <= h - 1; y++)
                w.getBlockAt(cx + d[0], cy + y, cz + d[1]).setType(
                        y % 3 == 2 ? Material.GOLD_BLOCK : Material.QUARTZ_PILLAR, false);
        }
        for (int x = -2; x <= 2; x += 4)
            for (int z = -2; z <= 2; z += 4)
                w.getBlockAt(cx + x, cy + h - 1, cz + z).setType(Material.LANTERN, false);
        // centre podium + info board
        w.getBlockAt(cx, cy + 1, cz).setType(Material.GOLD_BLOCK, false);
        w.getBlockAt(cx, cy + 2, cz).setType(Material.GOLD_BLOCK, false);
        Block sign = w.getBlockAt(cx, cy + 3, cz);
        sign.setType(Material.OAK_SIGN, false);
        if (sign.getState() instanceof Sign s) {
            try {
                s.getSide(org.bukkit.block.sign.Side.FRONT).setLine(0, ChatColor.GOLD + "AUCTION");
                s.getSide(org.bukkit.block.sign.Side.FRONT).setLine(1, ChatColor.WHITE + "HOUSE");
                s.getSide(org.bukkit.block.sign.Side.FRONT).setLine(2, ChatColor.GRAY + "post here = 5% tax");
                s.getSide(org.bukkit.block.sign.Side.FRONT).setLine(3, ChatColor.GRAY + "command = 20%");
                s.update(true, false);
            } catch (Throwable ignored) {}
        }
        // remove old keepers then place new ones from config
        for (Entity en : new ArrayList<>(w.getEntities()))
            if (en instanceof Villager v && v.getPersistentDataContainer().has(keeperKey, PersistentDataType.BYTE))
                en.remove();
        data.set("keepers", null);
        ConfigurationSection ks = getConfig().getConfigurationSection("keepers");
        if (ks != null) for (String k : ks.getKeys(false)) {
            ConfigurationSection c = ks.getConfigurationSection(k);
            if (c == null) continue;
            int kx = cx + c.getInt("x", 0), kz = cz + c.getInt("z", 0), ky = cy + c.getInt("y", 0);
            // pedestal
            w.getBlockAt(kx, ky - 1, kz).setType(Material.QUARTZ_PILLAR, false);
            w.getBlockAt(kx, ky, kz).setType(Material.AIR, false);
            String key = k;
            spawnKeeper(key, new Location(w, kx + 0.5, ky, kz + 0.5),
                    c.getString("name", "&6Auction Keeper"), c.getString("profession", "LIBRARIAN"));
            ConfigurationSection de = data.createSection("keepers." + key);
            de.set("x", kx + 0.5); de.set("y", ky); de.set("z", kz + 0.5);
            de.set("name", c.getString("name", "&6Auction Keeper"));
            de.set("profession", c.getString("profession", "LIBRARIAN"));
        }
        saveData();
        msg(admin, "&aAuction house built at " + cx + " " + cy + " " + cz
                + " (bedrock box, " + (2 * h + 1) + " wide) with keepers " + (data.getConfigurationSection("keepers") != null
                ? data.getConfigurationSection("keepers").getKeys(false).size() : 0) + ".");
    }

    /* ------------------------------------------------------------------ GUIs */

    private static class Holder implements InventoryHolder {
        final String kind;
        final int page;
        final String listingId;
        long price;
        int hours;
        ItemStack postItem;
        final boolean viaKeeper;
        Inventory inv;
        Holder(String kind, int page, String listingId, long price, int hours, ItemStack postItem, boolean viaKeeper) {
            this.kind = kind; this.page = page; this.listingId = listingId;
            this.price = price; this.hours = hours; this.postItem = postItem; this.viaKeeper = viaKeeper;
        }
        @Override public Inventory getInventory() { return inv; }
    }

    private ItemStack gui(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(color(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(color(s));
        meta.setLore(l);
        it.setItemMeta(meta);
        return it;
    }

    private void openMain(Player p, int page) { openPage(p, "main", page); }
    private void openMine(Player p, int page) { openPage(p, "mine", page); }

    private void openPage(Player p, String kind, int page) {
        List<Listing> all = new ArrayList<>(listings.values());
        if ("mine".equals(kind)) {
            all = activeOf(p.getUniqueId());
        }
        all.sort((a, b) -> Long.compare(a.end(), b.end()));
        int per = 45, pages = Math.max(1, (all.size() + per - 1) / per);
        page = Math.max(0, Math.min(pages - 1, page));
        Holder h = new Holder(kind, page, null, 0, 0, null, false);
        h.inv = Bukkit.createInventory(h, 54, color("&8&l" + ("mine".equals(kind) ? "My Listings" : "Auction House")));
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            if (idx >= all.size()) break;
            Listing l = all.get(idx);
            ItemStack it = l.item().clone();
            ItemMeta meta = it.getItemMeta();
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            boolean own = l.seller().equals(p.getUniqueId());
            lore.add("");
            lore.add(ChatColor.GRAY + "Seller: " + ChatColor.WHITE + uname(l.seller())
                    + (own ? ChatColor.YELLOW + " (you)" : ""));
            lore.add(ChatColor.GRAY + "Price: " + ChatColor.GOLD + fmt(l.price()) + " coins");
            lore.add(ChatColor.GRAY + "Time left: " + ChatColor.AQUA + durText(l.end() - System.currentTimeMillis()));
            lore.add(ChatColor.GRAY + "Poster tax: " + ("keeper".equals(l.tier()) ? ChatColor.GREEN + "5%" : ChatColor.RED + "20%"));
            lore.add(ChatColor.DARK_GRAY + "ID: " + l.id());
            lore.add("");
            lore.add(own ? ChatColor.YELLOW + "Click to cancel (no cost)" : ChatColor.GREEN + "Click to buy");
            meta.setLore(lore);
            it.setItemMeta(meta);
            h.inv.setItem(i, it);
        }
        h.inv.setItem(45, gui(Material.ARROW, page > 0 ? "&a◀ Previous" : "&8◀ Previous"));
        h.inv.setItem(53, gui(Material.ARROW, page + 1 < pages ? "&aNext ▶" : "&8Next ▶"));
        h.inv.setItem(46, gui(Material.BOOK, "&eMy Listings", "&7Your active auctions (cancel here).",
                "&7Count: &f" + activeOf(p.getUniqueId()).size()));
        h.inv.setItem(47, gui(Material.CHEST, "&eInbox", "&7Items you bought, cancelled or expired.",
                "&7Count: &f" + inboxCount(p.getUniqueId()) + "&7/&f" + INBOX_MAX, "&7Also: &f/inbox"));
        h.inv.setItem(48, gui(Material.GOLD_INGOT, "&eMy Slots", "&7You own &f" + slotsOf(p.getUniqueId())
                + "&7/&f" + MAX_SLOTS + " slots.", "&7Unlock more here."));
        h.inv.setItem(49, gui(Material.ANVIL, "&ePost an item", "&7Hold the item, then click here.",
                "&7Posting at the AH = &a5% tax", "&7Posting by command = &c20% tax"));
        h.inv.setItem(50, gui(Material.CLOCK, "&eHow it works",
                "&7/ah add hand 34 340 12h posts 34 for 340 coins.",
                "&7Successful sale: 5%/20% tax, slot locks 10 MC days.",
                "&7Expired/cancelled: no tax, no cooldown.",
                "&7Min price = &e110% &7of the shop sell price."));
        for (int i = 45; i <= 53; i++) {
            ItemStack cur = h.inv.getItem(i);
            if (cur == null) h.inv.setItem(i, gui(Material.GRAY_STAINED_GLASS_PANE, " "));
        }
        p.openInventory(h.inv);
    }

    private void openPost(Player p, int page) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (held == null || held.getType() == Material.AIR) {
            msg(p, "&cHold the item you want to post.");
            return;
        }
        if (held.getAmount() > 64) { msg(p, "&cA listing holds at most 64 items."); return; }
        if (isBound(held)) { msg(p, "&cAuction-bound items cannot be re-listed."); return; }
        if (isProfLocked(held)) { msg(p, "&cProfession-bound items cannot be auctioned."); return; }
        ItemStack it = held.clone();
        // take it now; restored if the editor is closed without confirming
        p.getInventory().setItemInMainHand(null);
        long min = minPrice(it);
        Holder h = new Holder("post", page, null, min > 0 ? min : 100, 12, it, tierFor(p, true).equals("keeper"));
        h.inv = Bukkit.createInventory(h, 54, color("&8&lPost an item"));
        h.inv.setItem(13, it);
        h.inv.setItem(20, gui(Material.PAPER, "&ePrice: &6" + fmt(h.price) + " coins",
                "&7Click the buttons to change it.",
                "&7Min allowed: &e" + (min > 0 ? fmt(min) + " (110% of shop sell)" : "none")));
        h.inv.setItem(28, gui(Material.RED_STAINED_GLASS_PANE, "&c-1,000", ""));
        h.inv.setItem(29, gui(Material.RED_STAINED_GLASS_PANE, "&c-100", ""));
        h.inv.setItem(30, gui(Material.RED_STAINED_GLASS_PANE, "&c-10", ""));
        h.inv.setItem(31, gui(Material.LIME_STAINED_GLASS_PANE, "&a+10", ""));
        h.inv.setItem(32, gui(Material.LIME_STAINED_GLASS_PANE, "&a+100", ""));
        h.inv.setItem(33, gui(Material.LIME_STAINED_GLASS_PANE, "&a+1,000", ""));
        h.inv.setItem(15, gui(Material.CLOCK, hoursBtn(1, h.hours)));
        h.inv.setItem(16, gui(Material.CLOCK, hoursBtn(6, h.hours)));
        h.inv.setItem(17, gui(Material.CLOCK, hoursBtn(12, h.hours)));
        h.inv.setItem(23, gui(Material.CLOCK, hoursBtn(24, h.hours)));
        h.inv.setItem(24, gui(Material.CLOCK, hoursBtn(48, h.hours)));
        h.inv.setItem(40, gui(Material.LIME_DYE, "&a✔ Confirm post",
                "&7Item: &f" + it.getAmount() + "x " + it.getType().name().toLowerCase(Locale.ROOT),
                "&7Price: &e" + fmt(h.price) + " coins &7for the whole listing.",
                "&7Duration: &e" + h.hours + "h",
                "&7Your tax: " + (h.viaKeeper ? ChatColor.GREEN + "5% (posted at the AH)" : ChatColor.RED + "20% (you are not in the AH)")));
        h.inv.setItem(49, gui(Material.BARRIER, "&cCancel (item returns to you)"));
        h.inv.setItem(52, gui(Material.NAME_TAG, "&eTip", "&7You can also post instantly:",
                "&f/ah add hand " + it.getAmount() + " <price> <duration>"));
        p.openInventory(h.inv);
    }

    private String hoursBtn(int h, int cur) {
        return (cur == h ? "&a▶ " : "&8") + h + "h" + (cur == h ? " &7(selected)" : "");
    }

    private void openSlots(Player p) {
        Holder h = new Holder("slots", 0, null, 0, 0, null, false);
        h.inv = Bukkit.createInventory(h, 54, color("&8&lMy Auction Slots"));
        int owned = slotsOf(p.getUniqueId());
        for (int s = 1; s <= MAX_SLOTS; s++) {
            if (s <= owned) {
                boolean cool = System.currentTimeMillis() < cooldownUntil(p.getUniqueId(), s);
                Listing use = null;
                for (Listing l : listings.values())
                    if (l.seller().equals(p.getUniqueId()) && l.slot() == s) use = l;
                ItemStack icon;
                if (use != null) icon = gui(Material.CHEST, "&eSlot " + s + " - in use",
                        "&7ID: &f" + use.id(), "&7Price: &e" + fmt(use.price()) + " coins",
                        "&7Ends in: &a" + durText(use.end() - System.currentTimeMillis()),
                        "&7Cancel in /ah (My Listings).");
                else if (cool) icon = gui(Material.CLOCK, "&cSlot " + s + " - cooldown",
                        "&7Locked for &e" + durText(cooldownUntil(p.getUniqueId(), s) - System.currentTimeMillis()),
                        "&7(10 Minecraft days after a successful sale)");
                else icon = gui(Material.LIME_STAINED_GLASS_PANE, "&aSlot " + s + " - free", "&7Ready to post.");
                h.inv.setItem(s - 1 + 9, icon);
            } else {
                h.inv.setItem(s - 1 + 9, gui(Material.GRAY_STAINED_GLASS_PANE, "&8Slot " + s + " - locked"));
            }
        }
        h.inv.setItem(4, gui(Material.GOLD_INGOT, "&eSlots: &f" + owned + "&7/&f" + MAX_SLOTS,
                "&7Every sale locks only that slot. Expired/cancelled never lock."));
        if (owned < MAX_SLOTS) {
            h.inv.setItem(49, gui(Material.GOLD_BLOCK, "&6Unlock slot " + (owned + 1),
                    "&e" + fmt(unlockCostCoins(owned)) + " coins &7or &e" + unlockCostLucky(owned) + " Lucky Coins",
                    "&7You have &f" + fmt((long) (econ != null ? econ.getBalance(p) : 0)) + " coins",
                    "&7and &f" + countLucky(p) + " Lucky Coins."));
            h.inv.setItem(50, gui(Material.EMERALD, "&aPay coins", "&e" + fmt(unlockCostCoins(owned)) + " coins"));
            h.inv.setItem(51, gui(Material.SUNFLOWER, "&aPay Lucky", "&e" + unlockCostLucky(owned) + " Lucky Coins"));
        } else {
            h.inv.setItem(49, gui(Material.BARRIER, "&cMax slots reached", "&7" + MAX_SLOTS + "/" + MAX_SLOTS));
        }
        h.inv.setItem(45, gui(Material.ARROW, "&a◀ Back to /ah"));
        p.openInventory(h.inv);
    }

    private void openInbox(Player p, int page) {
        List<Map<String, Object>> inbox = inboxOf(p.getUniqueId());
        int per = 36, pages = Math.max(1, (inbox.size() + per - 1) / per);
        page = Math.max(0, Math.min(pages - 1, page));
        Holder h = new Holder("inbox", page, null, 0, 0, null, false);
        h.inv = Bukkit.createInventory(h, 54, color("&8&lInbox (auction)"));
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            if (idx >= inbox.size()) break;
            Map<String, Object> m = inbox.get(idx);
            ConfigurationSection sec = (ConfigurationSection) m.get("item");
            ItemStack it = loadItem(sec);
            if (it == null) continue;
            ItemMeta meta = it.getItemMeta();
            List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
            lore.add("");
            lore.add(ChatColor.GRAY + "Tag: " + tagColor((String) m.get("tag")) + m.get("tag"));
            lore.add(ChatColor.GRAY + "Click to collect into your inventory.");
            meta.setLore(lore);
            it.setItemMeta(meta);
            h.inv.setItem(i, it);
        }
        h.inv.setItem(45, gui(Material.ARROW, page > 0 ? "&a◀ Previous" : "&8◀ Previous"));
        h.inv.setItem(53, gui(Material.ARROW, page + 1 < pages ? "&aNext ▶" : "&8Next ▶"));
        h.inv.setItem(49, gui(Material.CHEST, "&eCollect all", "&7Items go to your inventory (keep space)."));
        h.inv.setItem(50, gui(Material.HOPPER, "&eInbox: &f" + inbox.size() + "&7/&f" + INBOX_MAX,
                "&7" + (inbox.size() >= INBOX_MAX ? "&cFULL - collect now!" : "&aSpace available.")));
        h.inv.setItem(46, gui(Material.ARROW, "&a◀ Back to /ah"));
        p.openInventory(h.inv);
    }

    private String tagColor(String tag) {
        if ("auction".equals(tag)) return ChatColor.GREEN + "";
        if ("auction expired".equals(tag)) return ChatColor.GOLD + "";
        return ChatColor.GRAY + "";
    }

    private void openConfirmBuy(Player p, Listing l) {
        Holder h = new Holder("buy", 0, l.id(), 0, 0, null, false);
        h.inv = Bukkit.createInventory(h, 27, color("&8&lConfirm purchase"));
        ItemStack it = l.item().clone();
        ItemMeta meta = it.getItemMeta();
        List<String> lore = meta.getLore() == null ? new ArrayList<>() : new ArrayList<>(meta.getLore());
        lore.add("");
        lore.add(ChatColor.GRAY + "Price: " + ChatColor.GOLD + fmt(l.price()) + " coins");
        lore.add(ChatColor.GRAY + "Seller gets: " + ChatColor.YELLOW + fmt((long) Math.round(l.price()
                * (1 - ("keeper".equals(l.tier()) ? KEEPER_TAX : COMMAND_TAX)))) + " coins");
        lore.add(ChatColor.GRAY + "You get it in your &e/inbox&7 (tag: auction, bound).");
        meta.setLore(lore);
        it.setItemMeta(meta);
        h.inv.setItem(13, it);
        h.inv.setItem(11, gui(Material.LIME_DYE, "&a✔ Confirm buy", "&7" + fmt(l.price()) + " coins"));
        h.inv.setItem(15, gui(Material.BARRIER, "&cCancel"));
        p.openInventory(h.inv);
    }

    private void openConfirmCancel(Player p, String id) {
        Holder h = new Holder("cancel", 0, id, 0, 0, null, false);
        h.inv = Bukkit.createInventory(h, 27, color("&8&lCancel listing " + id));
        h.inv.setItem(13, gui(Material.BARRIER, "&cCancel this listing?",
                "&7Item goes to your &e/inbox&7 (tag: auction cancelled).",
                "&7No cost, no slot cooldown."));
        h.inv.setItem(11, gui(Material.LIME_DYE, "&a✔ Yes, cancel"));
        h.inv.setItem(15, gui(Material.RED_DYE, "&cNo, keep it"));
        p.openInventory(h.inv);
    }

    private void openConfirmUnlock(Player p, int current, boolean lucky) {
        Holder h = new Holder("unlock", 0, null, 0, 0, null, false);
        h.inv = Bukkit.createInventory(h, 27, color("&8&lUnlock slot " + (current + 1)));
        long coins = unlockCostCoins(current), luck = unlockCostLucky(current);
        h.inv.setItem(13, gui(Material.GOLD_BLOCK, "&6Unlock slot " + (current + 1) + "?",
                "&e" + fmt(coins) + " coins &7or &e" + luck + " Lucky Coins",
                "&7You have &f" + fmt((long) (econ != null ? econ.getBalance(p) : 0)) + " coins",
                "&7and &f" + countLucky(p) + " Lucky Coins."));
        h.inv.setItem(11, gui(Material.EMERALD, lucky ? "&aPay " + fmt(coins) + " coins" : "&aPay " + fmt(coins) + " coins"));
        h.inv.setItem(14, gui(Material.SUNFLOWER, lucky ? "&aPay " + luck + " Lucky Coins" : "&aPay " + luck + " Lucky Coins"));
        h.inv.setItem(15, gui(Material.BARRIER, "&cCancel"));
        p.openInventory(h.inv);
    }

    private static boolean isNumber(String s) {
        try { Integer.parseInt(s); return true; } catch (Throwable t) { return false; }
    }

    private String uname(UUID u) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(u);
        String n = op.getName();
        return n != null ? n : u.toString().substring(0, 8);
    }

    /* ------------------------------------------------------------------ GUI clicks */

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) return;
        if (!e.getClickedInventory().equals(e.getView().getTopInventory())) return; // bottom inv stays playable
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        int slot = e.getRawSlot();
        switch (h.kind) {
            case "main", "mine" -> {
                if (slot >= 0 && slot < 45 && e.getCurrentItem() != null && e.getCurrentItem().getType() != Material.AIR) {
                    List<Listing> all = new ArrayList<>("mine".equals(h.kind) ? activeOf(p.getUniqueId()) : listings.values());
                    all.sort((a, b) -> Long.compare(a.end(), b.end()));
                    int idx = h.page * 45 + slot;
                    if (idx < all.size()) {
                        Listing l = all.get(idx);
                        if (l.seller().equals(p.getUniqueId())) openConfirmCancel(p, l.id());
                        else openConfirmBuy(p, l);
                    }
                    return;
                }
                if (slot == 45) openPage(p, h.kind, h.page - 1);
                else if (slot == 53) openPage(p, h.kind, h.page + 1);
                else if (slot == 46) openMine(p, 0);
                else if (slot == 47) openInbox(p, 0);
                else if (slot == 48) openSlots(p);
                else if (slot == 49) openPost(p, 0);
                else if (slot == 50) { /* info */ }
            }
            case "post" -> {
                if (slot == 28) h.price = Math.max(1, h.price - 1000);
                else if (slot == 29) h.price = Math.max(1, h.price - 100);
                else if (slot == 30) h.price = Math.max(1, h.price - 10);
                else if (slot == 31) h.price += 10;
                else if (slot == 32) h.price += 100;
                else if (slot == 33) h.price += 1000;
                else if (slot == 15) h.hours = 1;
                else if (slot == 16) h.hours = 6;
                else if (slot == 17) h.hours = 12;
                else if (slot == 23) h.hours = 24;
                else if (slot == 24) h.hours = 48;
                else if (slot == 40) { confirmPost(p, h); return; }
                else if (slot == 49) {
                    giveBack(p, h.postItem); h.postItem = null; p.closeInventory(); return;
                }
                else return;
                if (h.price > Math.max(1, getConfig().getLong("max-price", 1_000_000_000L)))
                    h.price = Math.max(1, getConfig().getLong("max-price", 1_000_000_000L));
                refreshPost(p, h);
            }
            case "slots" -> {
                if (slot == 45) openMain(p, 0);
                else if (slot == 50) openConfirmUnlock(p, slotsOf(p.getUniqueId()), false);
                else if (slot == 51) openConfirmUnlock(p, slotsOf(p.getUniqueId()), true);
            }
            case "inbox" -> {
                if (slot == 45) openInbox(p, h.page - 1);
                else if (slot == 53) openInbox(p, h.page + 1);
                else if (slot == 46) openMain(p, 0);
                else if (slot == 49) claimAll(p);
                else if (slot >= 0 && slot < 36 && e.getCurrentItem() != null
                        && e.getCurrentItem().getType() != Material.AIR) {
                    claimAt(p, h.page * 36 + slot);
                }
            }
            case "buy" -> {
                if (slot == 11) {
                    Listing l = listings.get(h.listingId);
                    if (l == null) msg(p, "&cThat auction ended.");
                    else purchase(p, l);
                } else if (slot == 15) openMain(p, 0);
            }
            case "cancel" -> {
                if (slot == 11) cancelListing(p, h.listingId);
                else if (slot == 15) openMain(p, 0);
            }
            case "unlock" -> {
                if (slot == 11) tryUnlock(p, false);
                else if (slot == 14) tryUnlock(p, true);
                else if (slot == 15) openSlots(p);
            }
        }
    }

    private void refreshPost(Player p, Holder h) {
        ItemStack it = h.inv.getItem(13);
        h.inv.setItem(20, gui(Material.PAPER, "&ePrice: &6" + fmt(h.price) + " coins",
                "&7Click the buttons to change it."));
        h.inv.setItem(15, gui(Material.CLOCK, hoursBtn(1, h.hours)));
        h.inv.setItem(16, gui(Material.CLOCK, hoursBtn(6, h.hours)));
        h.inv.setItem(17, gui(Material.CLOCK, hoursBtn(12, h.hours)));
        h.inv.setItem(23, gui(Material.CLOCK, hoursBtn(24, h.hours)));
        h.inv.setItem(24, gui(Material.CLOCK, hoursBtn(48, h.hours)));
        h.inv.setItem(40, gui(Material.LIME_DYE, "&a✔ Confirm post",
                "&7Item: &f" + h.postItem.getAmount() + "x " + h.postItem.getType().name().toLowerCase(Locale.ROOT),
                "&7Price: &e" + fmt(h.price) + " coins &7for the whole listing.",
                "&7Duration: &e" + h.hours + "h",
                "&7Your tax: " + (h.viaKeeper ? ChatColor.GREEN + "5% (posted at the AH)" : ChatColor.RED + "20% (you are not in the AH)")));
    }

    private void confirmPost(Player p, Holder h) {
        String err = validate(p, h.postItem, h.price, h.hours, false);
        if (err != null) {
            msg(p, err); giveBack(p, h.postItem); h.postItem = null; p.closeInventory(); return;
        }
        int slot = freeSlot(p.getUniqueId());
        if (slot < 0) {
            msg(p, "&cNo free slot (buy more in /ah slots or wait out a cooldown).");
            giveBack(p, h.postItem); h.postItem = null; p.closeInventory(); return;
        }
        String tier = tierFor(p, h.viaKeeper);
        finishPost(p, h.postItem, h.price, h.hours, slot, tier);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if ("post".equals(h.kind) && h.postItem != null) {
            // only restore if a listing was NOT created (finishPost closes the GUI too)
            boolean listed = false;
            for (Listing l : listings.values())
                if (l.item() != null && l.seller().equals(p.getUniqueId())
                        && l.item().getType() == h.postItem.getType()
                        && l.item().getAmount() == h.postItem.getAmount()
                        && System.currentTimeMillis() - l.posted() < 5000) { listed = true; break; }
            if (!listed) giveBack(p, h.postItem);
        }
    }

    private void giveBack(Player p, ItemStack it) {
        if (it == null) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(it.clone());
        if (left.isEmpty()) return;
        p.getWorld().dropItemNaturally(p.getLocation(), left.get(0));
    }

    private void claimAt(Player p, int idx) {
        List<Map<String, Object>> inbox = inboxOf(p.getUniqueId());
        if (idx < 0 || idx >= inbox.size()) return;
        ConfigurationSection sec = (ConfigurationSection) inbox.get(idx).get("item");
        ItemStack it = loadItem(sec);
        if (it == null) return;
        Map<Integer, ItemStack> left = p.getInventory().addItem(it);
        if (!left.isEmpty()) { msg(p, "&cInventory full - collect later."); return; }
        // remove that entry (entries are keyed by absolute path)
        String key = inboxKeyAt(p.getUniqueId(), idx);
        if (key != null) inboxRemove(p.getUniqueId(), key);
        openInbox(p, 0);
    }

    private String inboxKeyAt(UUID u, int idx) {
        ConfigurationSection s = data.getConfigurationSection("players." + u + ".inbox");
        if (s == null) return null;
        List<String> keys = new ArrayList<>(s.getKeys(false));
        return idx < keys.size() ? keys.get(idx) : null;
    }

    private void claimAll(Player p) {
        ConfigurationSection s = data.getConfigurationSection("players." + p.getUniqueId() + ".inbox");
        if (s == null) { msg(p, "&7Inbox is empty."); return; }
        for (String k : new ArrayList<>(s.getKeys(false))) {
            ConfigurationSection e = s.getConfigurationSection(k);
            if (e == null) continue;
            ItemStack it = loadItem(e.getConfigurationSection("item"));
            if (it == null) continue;
            Map<Integer, ItemStack> left = p.getInventory().addItem(it);
            if (!left.isEmpty()) { msg(p, "&cInventory full - " + (s.getKeys(false).size()) + " items left."); return; }
            inboxRemove(p.getUniqueId(), k);
        }
        msg(p, "&aInbox emptied into your inventory.");
    }

    private void tryUnlock(Player p, boolean lucky) {
        int cur = slotsOf(p.getUniqueId());
        if (cur >= MAX_SLOTS) { msg(p, "&cMax slots already."); return; }
        long coins = unlockCostCoins(cur), luck = unlockCostLucky(cur);
        if (lucky) {
            if (!chargeLucky(p, luck)) { msg(p, "&cYou need &e" + luck + " &cLucky Coins (look for &e⛁ &cin your inventory)."); return; }
        } else {
            if (econ == null || !econ.has(p, coins)) { msg(p, "&cYou need &e" + fmt(coins) + " coins &c(or " + luck + " Lucky Coins)."); return; }
            EconomyResponse r = econ.withdrawPlayer(p, coins);
            if (!r.transactionSuccess()) { msg(p, "&cPayment failed."); return; }
        }
        setSlots(p.getUniqueId(), cur + 1);
        msg(p, "&aUnlocked slot &e" + (cur + 1) + "&a! (now " + (cur + 1) + "/" + MAX_SLOTS + ")");
        openSlots(p);
    }

    /* ------------------------------------------------------------------ commands */

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Player command only.");
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("inbox")) { openInbox(p, 0); return true; }
        if (args.length == 0) { openMain(p, 0); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "help" -> {
                p.sendMessage(color("&6Auction House:"));
                p.sendMessage(color("&e/ah &7- open the auction house"));
                p.sendMessage(color("&e/ah add hand <amount> <price> <duration> &7- post what you hold"));
                p.sendMessage(color("&e/ah add <material> <amount> <price> <duration> &7- post from inventory"));
                p.sendMessage(color("&e/ah inbox &7| &e/inbox &7- collect bought/expired/cancelled items"));
                p.sendMessage(color("&e/ah slots &7- unlock slots (50k/100 Lucky, 100k/200 ... up to 20)"));
                p.sendMessage(color("&e/ah cancel <id> &7- withdraw a listing (no cost)"));
                p.sendMessage(color("&7Tax: &c20% &7via command, &a5% &7posted at the AH villagers. Min price = &e110% &7of shop sell."));
            }
            case "add" -> {
                if (args.length < 5) {
                    p.sendMessage(color("&cUsage: /ah add hand <amount> <price> <duration>   or   /ah add <material> <amount> <price> <duration>"));
                    return true;
                }
                String what = args[1];
                int ai = 2, pi = 3, di = 4;
                // "/ah add hand carrot 34 340 12h" - material after "hand"
                if ("hand".equalsIgnoreCase(what) && args.length >= 6 && !isNumber(args[2])) {
                    what = args[2]; ai = 3; pi = 4; di = 5;
                }
                long price;
                try { price = Long.parseLong(args[pi].replace(",", "")); } catch (Throwable t) {
                    p.sendMessage(color("&cPrice must be a number.")); return true; }
                int amount;
                try { amount = Integer.parseInt(args[ai]); } catch (Throwable t) {
                    p.sendMessage(color("&cAmount must be a number.")); return true; }
                if (di >= args.length) {
                    p.sendMessage(color("&cUsage: /ah add hand <amount> <price> <duration>   or   /ah add <material> <amount> <price> <duration>"));
                    return true;
                }
                commandPost(p, what, amount, price, args[di]);
            }
            case "inbox" -> openInbox(p, 0);
            case "slots", "slot" -> openSlots(p);
            case "cancel" -> {
                if (args.length < 2) { p.sendMessage(color("&cUsage: /ah cancel <listing id>")); return true; }
                cancelListing(p, args[1].toUpperCase(Locale.ROOT));
            }
            case "setcenter" -> {
                if (!p.hasPermission("mavoauction.admin")) { p.sendMessage(ChatColor.RED + "OP only."); return true; }
                getConfig().set("center.world", p.getWorld().getName());
                getConfig().set("center.x", p.getLocation().getBlockX());
                getConfig().set("center.y", p.getLocation().getBlockY());
                getConfig().set("center.z", p.getLocation().getBlockZ());
                saveConfig();
                regionCenter = p.getLocation().clone();
                msg(p, "&aAuction house center set to your feet. Run &e/ah build&a to build the box.");
            }
            case "build" -> {
                if (!p.hasPermission("mavoauction.admin")) { p.sendMessage(ChatColor.RED + "OP only."); return true; }
                if (regionCenter == null) { msg(p, "&cRun /ah setcenter first."); return true; }
                buildHouse(p);
            }
            case "reload" -> {
                if (!p.hasPermission("mavoauction.admin")) { p.sendMessage(ChatColor.RED + "OP only."); return true; }
                reloadConfig(); loadSettings(); loadShopPrices();
                msg(p, "&aReloaded. shopSell=" + shopSell.size());
            }
            default -> openMain(p, 0);
        }
        return true;
    }
}
