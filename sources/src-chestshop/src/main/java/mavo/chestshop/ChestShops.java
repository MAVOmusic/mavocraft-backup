package mavo.chestshop;

import java.io.File;
import java.util.*;
import net.milkbowl.vault.economy.Economy;
import net.milkbowl.vault.economy.EconomyResponse;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockState;
import org.bukkit.block.Chest;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** MAVOChestShops 1.0.0 - player-run chest shops (Discord CW#3 idea 2).
 *  Chest + price + item = shop. Right-click buys via GUI (1 or 64). Chests are
 *  explosion-proof, owner-only storage, optional market district + rentable stalls. */
public final class ChestShops extends JavaPlugin implements Listener {

    private static final ChatColor G = ChatColor.GREEN, R = ChatColor.RED, Y = ChatColor.YELLOW,
            GOLD = ChatColor.GOLD, GRAY = ChatColor.GRAY, AQ = ChatColor.AQUA, D = ChatColor.DARK_GRAY;

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;
    private final Map<String, Shop> shops = new LinkedHashMap<>();      // id -> shop
    private final Map<String, Shop> byBlock = new HashMap<>();          // "world,x,y,z" -> shop
    private long nextId = 1;

    static final class Shop {
        String id, world, item; UUID owner; int x, y, z; long price;
        String stallId; long created;
    }

    static final class Stall {
        String id, world; int x, y, z; long price;
    }

    // ---------------- lifecycle ----------------
    @Override
    public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        loadShops();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOChestShops v" + getDescription().getVersion() + " enabled - "
                + shops.size() + " shop(s), stalls=" + stalls().size() + ".");
    }

    @Override
    public void onDisable() { saveData(); }

    private void loadShops() {
        shops.clear(); byBlock.clear();
        ConfigurationSection s = data.getConfigurationSection("shops");
        if (s != null) for (String id : s.getKeys(false)) {
            Shop sh = new Shop();
            sh.id = id;
            sh.owner = UUID.fromString(s.getString(id + ".owner", ""));
            sh.world = s.getString(id + ".world", "");
            sh.x = s.getInt(id + ".x"); sh.y = s.getInt(id + ".y"); sh.z = s.getInt(id + ".z");
            sh.price = s.getLong(id + ".price");
            sh.item = s.getString(id + ".item", "AIR");
            sh.stallId = s.getString(id + ".stall", null);
            sh.created = s.getLong(id + ".created", 0);
            shops.put(id, sh);
            byBlock.put(key(sh.world, sh.x, sh.y, sh.z), sh);
        }
        ConfigurationSection n = data.getConfigurationSection("meta");
        nextId = n == null ? 1 : Math.max(1, n.getLong("next-id", 1));
        for (Shop sh : shops.values()) {
            long idx = parseId(sh.id);
            if (idx >= nextId) nextId = idx + 1;
        }
    }

    private long parseId(String id) {
        try { return Long.parseLong(id.replaceAll("\\D", "")); } catch (Exception e) { return 0; }
    }

    private void saveData() {
        data.set("meta.next-id", nextId);
        try { data.save(dataFile); } catch (Exception ignored) {}
    }

    private static String key(String w, int x, int y, int z) { return w + "," + x + "," + y + "," + z; }
    private static String key(Block b) { return key(b.getWorld().getName(), b.getX(), b.getY(), b.getZ()); }
    private static String fmt(long n) { return String.format("%,d", n); }
    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private static String pn(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ') + " ("
                + m.name().replace('_', ' ').toLowerCase(Locale.ROOT) + ")";
    }

    private Shop shopAt(Block b) {
        if (b == null) return null;
        return byBlock.get(key(b));
    }

    /** the other half of a double chest (or null). */
    private Block otherHalf(Block b) {
        if (!(b.getState() instanceof Chest c)) return null;
        InventoryHolder holder = c.getInventory().getHolder();
        if (holder instanceof org.bukkit.block.DoubleChest dc) {
            for (InventoryHolder side : new InventoryHolder[]{dc.getLeftSide(), dc.getRightSide()})
                if (side instanceof BlockState bs && !bs.getBlock().equals(b)) return bs.getBlock();
        }
        return null;
    }

    private void indexBoth(Block b, Shop sh) {
        byBlock.put(key(b), sh);
        Block o = otherHalf(b);
        if (o != null) byBlock.put(key(o), sh);
    }

    private int shopCount(UUID u) {
        int n = 0;
        for (Shop sh : shops.values()) if (sh.owner.equals(u)) n++;
        return n;
    }

    private List<Stall> stalls() {
        List<Stall> out = new ArrayList<>();
        for (Map<?, ?> m : getConfig().getMapList("stalls")) {
            Stall st = new Stall();
            try {
                st.id = String.valueOf(m.get("id"));
                st.world = String.valueOf(m.get("world"));
                @SuppressWarnings("unchecked")
                List<Number> c = (List<Number>) m.get("chest");
                st.x = c.get(0).intValue(); st.y = c.get(1).intValue(); st.z = c.get(2).intValue();
                st.price = ((Number) m.get("price")).longValue();
                out.add(st);
            } catch (Exception ignored) {}
        }
        return out;
    }

    private Stall stallAt(Block b) {
        String k = key(b);
        for (Stall st : stalls()) if (key(st.world, st.x, st.y, st.z).equals(k)) return st;
        return null;
    }

    private boolean stallRented(Stall st) {
        for (Shop sh : shops.values())
            if (st.id.equals(sh.stallId)) return true;
        return false;
    }

    private boolean inMarket(Location l) {
        List<Map<?, ?>> regs = getConfig().getMapList("market-regions");
        if (regs == null || regs.isEmpty()) return true;
        for (Map<?, ?> r : regs) {
            try {
                if (!String.valueOf(r.get("world")).equals(l.getWorld().getName())) continue;
                int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
                if (x >= pos(r.get("minX")) && x <= pos(r.get("maxX"))
                        && y >= pos(r.get("minY")) && y <= pos(r.get("maxY"))
                        && z >= pos(r.get("minZ")) && z <= pos(r.get("maxZ"))) return true;
            } catch (Exception ignored) {}
        }
        return false;
    }

    private static int pos(Object o) { return o instanceof Number ? ((Number) o).intValue() : 0; }

    private Material chestMaterial(Material m) {
        return m == Material.TRAPPED_CHEST ? m : Material.CHEST;
    }

    private ItemStack firstItem(Block b) {
        if (!(b.getState() instanceof Chest c)) return null;
        for (ItemStack it : c.getInventory().getContents())
            if (it != null && !it.getType().isAir()) return it.clone();
        return null;
    }

    private int stock(Block b, Material mat) {
        if (!(b.getState() instanceof Chest c)) return 0;
        int n = 0;
        for (ItemStack it : c.getInventory().getContents())
            if (it != null && it.getType() == mat) n += it.getAmount();
        return n;
    }

    private void renameChest(Block b, Shop sh) {
        String name = ChatColor.GOLD + "" + ChatColor.BOLD + "[SHOP] " + ChatColor.YELLOW + nice(sh.item)
                + " " + ChatColor.GRAY + fmt(sh.price) + "c " + ChatColor.DARK_GRAY + ownerName(sh.owner);
        Block[] parts = {b, otherHalf(b)};
        for (Block part : parts) {
            if (part == null || !(part.getState() instanceof Chest c)) continue;
            c.setCustomName(name);
            c.update();
        }
    }

    private static String nice(String item) {
        Material m = Material.matchMaterial(item);
        return m == null ? item : m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }

    private String ownerName(UUID u) {
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n == null ? u.toString().substring(0, 8) : n;
    }

    // ---------------- commands ----------------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("tp")) {
                Shop sh = shops.get(args[1]);
                sender.sendMessage(sh == null ? R + "No such shop." : G + args[1] + " -> " + sh.world
                        + " " + sh.x + " " + sh.y + " " + sh.z + " (" + ownerName(sh.owner) + ")");
                return true;
            }
            sender.sendMessage(R + "In-game only.");
            return true;
        }
        if (args.length == 0) { help(p); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "create" -> createShop(p, args);
            case "rent" -> rentStall(p, args);
            case "price" -> changePrice(p, args);
            case "remove" -> removeShop(p, null);
            case "list" -> openList(p, 0);
            case "info" -> info(p, args);
            case "tp" -> {
                if (!p.hasPermission("mavochestshop.admin")) { p.sendMessage(R + "No permission."); return true; }
                if (args.length < 2) { p.sendMessage(R + "/cshop tp <id>"); return true; }
                Shop sh = shops.get(args[1]);
                if (sh == null) { p.sendMessage(R + "No shop '" + args[1] + "'."); return true; }
                p.teleport(new Location(Bukkit.getWorld(sh.world), sh.x + 0.5, sh.y, sh.z + 0.5));
                p.sendMessage(G + "Teleported to shop " + sh.id + ".");
            }
            case "delete" -> {
                if (!p.hasPermission("mavochestshop.admin")) { p.sendMessage(R + "No permission."); return true; }
                if (args.length < 2) { p.sendMessage(R + "/cshop delete <id>"); return true; }
                Shop sh = shops.get(args[1]);
                if (sh == null) { p.sendMessage(R + "No shop '" + args[1] + "'."); return true; }
                removeShopBy(sh);
                p.sendMessage(G + "Shop " + sh.id + " deleted.");
            }
            case "reload" -> {
                if (!p.hasPermission("mavochestshop.admin")) { p.sendMessage(R + "No permission."); return true; }
                reloadConfig();
                p.sendMessage(G + "Config reloaded (stalls " + stalls().size() + ").");
            }
            default -> help(p);
        }
        return true;
    }

    private void help(Player p) {
        p.sendMessage(GOLD + "" + ChatColor.BOLD + "=== CHEST SHOPS ===");
        p.sendMessage(GRAY + "Put items in a chest, then:");
        p.sendMessage(AQ + "/cshop create <price> " + GRAY + "- make this chest a shop (look at it)");
        p.sendMessage(AQ + "/cshop price <coins> " + GRAY + "- change your shop's price");
        p.sendMessage(AQ + "/cshop remove " + GRAY + "- close your shop (chest + items stay)");
        p.sendMessage(AQ + "/cshop list " + GRAY + "- all shops | " + AQ + "/cshop info " + GRAY + "- this");
        p.sendMessage(AQ + "/cshop rent <price> " + GRAY + "- rent a market stall (if built)");
        p.sendMessage(GRAY + "Buyers: right-click the chest -> buy 1 / shift = buy 64.");
        p.sendMessage(GRAY + "Owner storage: sneak + right-click your chest.");
        int max = getConfig().getInt("max-shops-per-player", 3);
        p.sendMessage(GRAY + "Your shops: " + Y + shopCount(p.getUniqueId()) + "/" + max + ".");
    }

    private void createShop(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(R + "/cshop create <price> [item]"); return; }
        long price = parsePrice(args[1]);
        if (price <= 0) { p.sendMessage(R + "Price must be 1+ coins."); return; }
        if (econ == null) { p.sendMessage(R + "Economy missing."); return; }
        Block b = p.getTargetBlockExact(5);
        if (b == null || !isChest(b.getType())) { p.sendMessage(R + "Look at a chest within 5 blocks."); return; }
        if (shopAt(b) != null) { p.sendMessage(R + "That chest is already a shop."); return; }
        if (stallAt(b) != null) { p.sendMessage(R + "That's a market stall - use /cshop rent <price>."); return; }
        if (getConfig().getBoolean("require-market", false) && !inMarket(b.getLocation())) {
            p.sendMessage(R + "Shops only inside the market district right now.");
            return;
        }
        int max = getConfig().getInt("max-shops-per-player", 3);
        if (shopCount(p.getUniqueId()) >= max) { p.sendMessage(R + "Max " + max + " shops per player."); return; }
        ItemStack first = firstItem(b);
        if (first == null && args.length < 3) { p.sendMessage(R + "Put the item you want to sell in the chest first (or /cshop create <price> <item>)."); return; }
        Material mat = args.length >= 3 ? Material.matchMaterial(args[2]) : first.getType();
        if (mat == null || mat.isAir()) { p.sendMessage(R + "Unknown item."); return; }
        Shop sh = new Shop();
        sh.id = "S" + nextId++;
        sh.owner = p.getUniqueId(); sh.world = b.getWorld().getName();
        sh.x = b.getX(); sh.y = b.getY(); sh.z = b.getZ();
        sh.price = price; sh.item = mat.name(); sh.created = System.currentTimeMillis();
        shops.put(sh.id, sh);
        indexBoth(b, sh);
        saveRegister(sh.id);
        renameChest(b, sh);
        p.sendMessage(G + "Shop " + Y + sh.id + G + " created: " + AQ + nice(sh.item)
                + " " + Y + fmt(price) + "c" + G + " per item. Right-click to buy!");
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 1f, 1.4f);
    }

    private void rentStall(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(R + "/cshop rent <item-price>"); return; }
        long itemPrice = parsePrice(args[1]);
        if (itemPrice <= 0) { p.sendMessage(R + "Item price must be 1+ coins."); return; }
        Block b = p.getTargetBlockExact(5);
        if (b == null || !isChest(b.getType())) { p.sendMessage(R + "Look at the stall chest within 5 blocks."); return; }
        Stall st = stallAt(b);
        if (st == null) { p.sendMessage(R + "That's not a configured market stall."); return; }
        if (stallRented(st)) { p.sendMessage(R + "Stall " + st.id + " is already taken."); return; }
        if (shopAt(b) != null) { p.sendMessage(R + "That chest is already a shop."); return; }
        if (econ == null || !econ.has(p, st.price)) { p.sendMessage(R + "Need " + fmt(st.price) + " coins to rent this stall."); return; }
        ItemStack first = firstItem(b);
        if (first == null) { p.sendMessage(R + "Put the item you want to sell in the stall chest first."); return; }
        econ.withdrawPlayer(p, st.price);
        Shop sh = new Shop();
        sh.id = "S" + nextId++;
        sh.owner = p.getUniqueId(); sh.world = b.getWorld().getName();
        sh.x = b.getX(); sh.y = b.getY(); sh.z = b.getZ();
        sh.price = itemPrice; sh.item = first.getType().name();
        sh.stallId = st.id; sh.created = System.currentTimeMillis();
        shops.put(sh.id, sh);
        indexBoth(b, sh);
        saveRegister(sh.id);
        renameChest(b, sh);
        p.sendMessage(G + "Stall " + Y + st.id + G + " rented (" + fmt(st.price)
                + "c). Shop " + Y + sh.id + G + " live: " + AQ + nice(sh.item) + " " + fmt(itemPrice) + "c.");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.8f);
    }

    private void changePrice(Player p, String[] args) {
        if (args.length < 2) { p.sendMessage(R + "/cshop price <coins>"); return; }
        long price = parsePrice(args[1]);
        if (price <= 0) { p.sendMessage(R + "Price must be 1+ coins."); return; }
        Block b = p.getTargetBlockExact(5);
        Shop sh = shopAt(b);
        if (sh == null) { p.sendMessage(R + "No shop here - look at a shop chest."); return; }
        if (!sh.owner.equals(p.getUniqueId()) && !p.hasPermission("mavochestshop.admin")) {
            p.sendMessage(R + "Only the owner can change the price."); return;
        }
        sh.price = price;
        data.set("shops." + sh.id + ".price", price);
        saveData();
        renameChest(b, sh);
        p.sendMessage(G + "Shop " + sh.id + " price -> " + fmt(price) + "c per item.");
    }

    private void removeShop(Player p, String id) {
        Block b = id == null ? p.getTargetBlockExact(5) : null;
        Shop sh = id != null ? shops.get(id) : shopAt(b);
        if (sh == null) { p.sendMessage(R + "No shop here."); return; }
        if (!sh.owner.equals(p.getUniqueId()) && !p.hasPermission("mavochestshop.admin")) {
            p.sendMessage(R + "Only the owner can remove this shop."); return;
        }
        removeShopBy(sh);
        p.sendMessage(Y + "Shop " + sh.id + " closed. Chest + items kept"
                + (sh.stallId != null ? " - stall " + sh.stallId + " is free again." : "."));
    }

    private void removeShopBy(Shop sh) {
        shops.remove(sh.id);
        byBlock.remove(key(sh.world, sh.x, sh.y, sh.z));
        World w = Bukkit.getWorld(sh.world);
        if (w != null) {
            Block b = w.getBlockAt(sh.x, sh.y, sh.z);
            byBlock.remove(key(b));
            Block o = otherHalf(b);
            if (o != null) byBlock.remove(key(o));
            for (Block part : new Block[]{b, o}) {
                if (part != null && part.getState() instanceof Chest c) { c.setCustomName(null); c.update(); }
            }
        }
        data.set("shops." + sh.id, null);
        saveData();
    }

    private void saveRegister(String id) {
        Shop sh = shops.get(id);
        data.set("shops." + id + ".owner", sh.owner.toString());
        data.set("shops." + id + ".world", sh.world);
        data.set("shops." + id + ".x", sh.x);
        data.set("shops." + id + ".y", sh.y);
        data.set("shops." + id + ".z", sh.z);
        data.set("shops." + id + ".price", sh.price);
        data.set("shops." + id + ".item", sh.item);
        if (sh.stallId != null) data.set("shops." + id + ".stall", sh.stallId);
        data.set("shops." + id + ".created", sh.created);
        saveData();
    }

    private void info(Player p, String[] args) {
        String id = args.length >= 2 ? args[1] : null;
        Shop sh = id != null ? shops.get(id) : shopAt(p.getTargetBlockExact(5));
        if (sh == null) { p.sendMessage(R + "Usage: /cshop info <id> (or look at a shop chest)."); return; }
        p.sendMessage(GOLD + "" + ChatColor.BOLD + sh.id + " (" + ownerName(sh.owner) + ")");
        p.sendMessage(GRAY + nice(sh.item) + " @ " + Y + fmt(sh.price) + "c" + GRAY
                + " - " + sh.world + " " + sh.x + " " + sh.y + " " + sh.z
                + (sh.stallId != null ? " · stall " + sh.stallId : ""));
    }

    private void openList(Player p, int page) {
        List<Shop> all = new ArrayList<>(shops.values());
        int per = 45, pages = Math.max(1, (all.size() + per - 1) / per);
        page = Math.max(0, Math.min(page, pages - 1));
        Inventory inv = Bukkit.createInventory(null, 54,
                GOLD + "" + ChatColor.BOLD + "Chest Shops — " + (page + 1) + "/" + pages);
        int slot = 0;
        for (int i = page * per; i < Math.min(all.size(), page * per + per); i++) {
            Shop sh = all.get(i);
            Material m = Material.matchMaterial(sh.item);
            ItemStack it = new ItemStack(m == null ? Material.CHEST : m);
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(GOLD + "" + ChatColor.BOLD + sh.id + " · " + nice(sh.item));
            Block b = Bukkit.getWorld(sh.world).getBlockAt(sh.x, sh.y, sh.z);
            meta.setLore(List.of(GRAY + "Price: " + Y + fmt(sh.price) + "c" + GRAY + " per item",
                    GRAY + "Stock: " + stock(b, m == null ? Material.CHEST : m),
                    GRAY + "Owner: " + ownerName(sh.owner),
                    GRAY + sh.world + " " + sh.x + " " + sh.y + " " + sh.z));
            it.setItemMeta(meta);
            inv.setItem(slot++, it);
        }
        if (all.isEmpty()) {
            ItemStack b = new ItemStack(Material.BARRIER);
            ItemMeta bm = b.getItemMeta();
            bm.setDisplayName(R + "No chest shops yet");
            bm.setLore(List.of(GRAY + "Put items in a chest, then /cshop create <price>"));
            b.setItemMeta(bm);
            inv.setItem(22, b);
        }
        if (page > 0) { ItemStack b = new ItemStack(Material.ARROW); ItemMeta bm = b.getItemMeta(); bm.setDisplayName(GOLD + "◀ Previous"); b.setItemMeta(bm); inv.setItem(48, b); }
        if (page < pages - 1) { ItemStack b = new ItemStack(Material.ARROW); ItemMeta bm = b.getItemMeta(); bm.setDisplayName(GOLD + "Next ▶"); b.setItemMeta(bm); inv.setItem(50, b); }
        p.openInventory(inv);
    }

    // ---------------- events ----------------
    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Block b = e.getClickedBlock();
        if (b == null || !isChest(b.getType())) return;
        Shop sh = shopAt(b);
        if (sh == null) return;
        e.setCancelled(true);
        Player p = e.getPlayer();
        if (sh.owner.equals(p.getUniqueId()) && p.isSneaking()) {
            // owner storage: open the actual chest
            if (b.getState() instanceof Chest c) p.openInventory(c.getInventory());
            return;
        }
        openBuy(p, sh, b);
    }

    private void openBuy(Player p, Shop sh, Block b) {
        Material m = Material.matchMaterial(sh.item);
        int st = stock(b, m == null ? Material.CHEST : m);
        Inventory inv = Bukkit.createInventory(null, 27,
                GOLD + "" + ChatColor.BOLD + "Chest Shop — " + sh.id);
        ItemStack it = new ItemStack(m == null ? Material.BARRIER : m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(AQ + "" + ChatColor.BOLD + nice(sh.item));
        meta.setLore(List.of(Y + "Price: " + fmt(sh.price) + "c" + GRAY + " each",
                GRAY + "In stock: " + (m == null ? 0 : st),
                GRAY + "Seller: " + ownerName(sh.owner)));
        it.setItemMeta(meta);
        inv.setItem(13, it);
        ItemStack b1 = new ItemStack(Material.LIME_DYE);
        ItemMeta m1 = b1.getItemMeta(); m1.setDisplayName(G + "Buy 1 (" + fmt(sh.price) + "c)"); b1.setItemMeta(m1);
        inv.setItem(11, b1);
        ItemStack b64 = new ItemStack(Material.LIME_DYE);
        ItemMeta m2 = b64.getItemMeta(); m2.setDisplayName(G + "Buy 64" + GRAY + " (up to stock)"); b64.setItemMeta(m2);
        inv.setItem(15, b64);
        if (st == 0) {
            ItemStack out = new ItemStack(Material.BARRIER);
            ItemMeta om = out.getItemMeta(); om.setDisplayName(R + "OUT OF STOCK"); out.setItemMeta(om);
            inv.setItem(22, out);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onBuyClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String t = e.getView().getTitle();
        if (t == null || !t.contains("Chest Shop —")) return;
        e.setCancelled(true);
        if (e.getCurrentItem() == null || e.getCurrentItem().getType().isAir()) return;
        ItemMeta meta = e.getCurrentItem().getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return;
        String name = ChatColor.stripColor(meta.getDisplayName());
        int want = name.startsWith("Buy 64") ? 64 : 1;
        if (name.startsWith("Buy 1") || name.startsWith("Buy 64") || !name.equals("OUT OF STOCK")) {
            String id = t.substring(t.indexOf("—") + 2).trim();
            Shop sh = shops.get(id);
            if (sh == null) { p.closeInventory(); return; }
            Block b = Bukkit.getWorld(sh.world).getBlockAt(sh.x, sh.y, sh.z);
            buy(p, sh, b, want);
        }
    }

    private void buy(Player p, Shop sh, Block b, int want) {
        Material m = Material.matchMaterial(sh.item);
        if (m == null) { p.sendMessage(R + "Shop item is invalid - tell an admin."); p.closeInventory(); return; }
        int st = stock(b, m);
        if (st <= 0) { p.sendMessage(R + "Out of stock!"); openBuy(p, sh, b); return; }
        int amount = Math.min(want, st);
        long cost = sh.price * amount;
        boolean survival = p.getGameMode() == org.bukkit.GameMode.SURVIVAL;
        if (survival) {
            if (econ == null || !econ.has(p, cost)) {
                p.sendMessage(R + "You need " + fmt(cost) + " coins for " + amount + "x "
                        + nice(sh.item) + ".");
                p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            EconomyResponse er = econ.withdrawPlayer(p, cost);
            if (!er.transactionSuccess()) { p.sendMessage(R + "Payment failed: " + er.errorMessage); return; }
        }
        // take item(s) from the chest
        if (b.getState() instanceof Chest c) c.getInventory().removeItem(new ItemStack(m, amount));
        // pay the seller (tax applies to survival sales only)
        if (survival) {
            int tax = Math.max(0, Math.min(100, getConfig().getInt("tax-percent", 0)));
            long pay = tax > 0 ? (long) Math.floor(cost * (100 - tax) / 100.0) : cost;
            econ.depositPlayer(Bukkit.getOfflinePlayer(sh.owner), pay);
        }
        p.sendMessage(G + "Bought " + Y + amount + "x " + AQ + nice(sh.item) + G + " for "
                + Y + fmt(cost) + "c" + G + " from " + AQ + sh.id + G + " (" + ownerName(sh.owner) + ").");
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f);
        openBuy(p, sh, b);
    }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        Shop sh = shopAt(e.getBlock());
        if (sh == null) return;
        if (!sh.owner.equals(e.getPlayer().getUniqueId()) && !e.getPlayer().hasPermission("mavochestshop.admin")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(R + "That's a shop (" + sh.id + " by " + ownerName(sh.owner) + ") - break it as its owner.");
            return;
        }
        removeShopBy(sh);
        e.getPlayer().sendMessage(Y + "Shop " + sh.id + " removed with the chest.");
    }

    @EventHandler
    public void onExplode(BlockExplodeEvent e) { e.blockList().removeIf(b -> shopAt(b) != null); }

    @EventHandler
    public void onExplodeEntity(EntityExplodeEvent e) { e.blockList().removeIf(b -> shopAt(b) != null); }

    @EventHandler
    public void onPistonExtend(BlockPistonExtendEvent e) {
        for (Block b : e.getBlocks()) if (shopAt(b) != null) { e.setCancelled(true); return; }
    }

    @EventHandler
    public void onPistonRetract(BlockPistonRetractEvent e) {
        for (Block b : e.getBlocks()) if (shopAt(b) != null) { e.setCancelled(true); return; }
    }

    private static boolean isChest(Material m) { return m == Material.CHEST || m == Material.TRAPPED_CHEST; }

    private long parsePrice(String s) {
        try { return Long.parseLong(s.replaceAll("[^0-9]", "")); } catch (Exception e) { return -1; }
    }
}
