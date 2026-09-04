package mavo.mobfarm;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.*;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.*;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Slab;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.sign.Side;
import org.bukkit.boss.*;
import org.bukkit.command.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.*;
import org.bukkit.event.*;
import org.bukkit.event.block.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.inventory.*;
import org.bukkit.event.player.*;
import org.bukkit.event.world.PortalCreateEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** MAVOMobFarm 2.7.1 — every mob has its own hand-built structure (crypt/pyramid/igloo/tower/cage/vault/obelisk/court/basin/caldera/tank/fortress/bastion + 14 unique pens), its own datapack, and its own /mobfarm build <mob>. Loot chest exposed on the trench floor; kill method + positions shared. */
public final class MobFarm extends JavaPlugin implements Listener, TabCompleter {

    /**
     * REAL EconomyShopGUI spawner buy prices (shops/Mobs/spawners.yml) — authoritative.
     * config.yml shop-buy only OVERRIDES; if it is missing/stale the table below is used,
     * so pick prices can never silently fall back to the old flat P/16 (625).
     */
    private static final Map<String, Long> SHOP_BUY = new LinkedHashMap<>();
    static {
        SHOP_BUY.put("ZOMBIE", 1200000L); SHOP_BUY.put("HUSK", 1250000L);
        SHOP_BUY.put("SKELETON", 1250000L); SHOP_BUY.put("STRAY", 1250000L);
        SHOP_BUY.put("SPIDER", 450000L); SHOP_BUY.put("CAVE_SPIDER", 500000L);
        SHOP_BUY.put("CREEPER", 1500000L); SHOP_BUY.put("ENDERMAN", 1750000L);
        SHOP_BUY.put("BLAZE", 1250000L); SHOP_BUY.put("MAGMA_CUBE", 5000000L);
        SHOP_BUY.put("SLIME", 5500000L); SHOP_BUY.put("WITHER_SKELETON", 2500000L);
        SHOP_BUY.put("DROWNED", 1200000L); SHOP_BUY.put("GUARDIAN", 2500000L);
        SHOP_BUY.put("WITCH", 5000000L); SHOP_BUY.put("PILLAGER", 1750000L);
        SHOP_BUY.put("HOGLIN", 1200000L); SHOP_BUY.put("PIGLIN", 1200000L);
        SHOP_BUY.put("SILVERFISH", 500000L); SHOP_BUY.put("PHANTOM", 2500000L);
        SHOP_BUY.put("COW", 1000000L); SHOP_BUY.put("PIG", 1000000L);
        SHOP_BUY.put("CHICKEN", 150000L); SHOP_BUY.put("SHEEP", 450000L);
        SHOP_BUY.put("RABBIT", 450000L); SHOP_BUY.put("VILLAGER", 1000000L);
        SHOP_BUY.put("IRON_GOLEM", 10000000L); SHOP_BUY.put("SQUID", 1000000L);
        SHOP_BUY.put("GLOW_SQUID", 1000000L); SHOP_BUY.put("BEE", 150000L);
        SHOP_BUY.put("FOX", 250000L); SHOP_BUY.put("GOAT", 1200000L);
        SHOP_BUY.put("LLAMA", 450000L); SHOP_BUY.put("PANDA", 500000L);
        SHOP_BUY.put("FROG", 3950000L); SHOP_BUY.put("SNIFFER", 450000L);
    }

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;
    Location center;
    private final Map<String, MobDef> mobs = new LinkedHashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, PendingEnter> pending = new HashMap<>();
    private NamespacedKey holoKey, farmMobKey, stackHoloKey, lastHitKey;
    private long communityCoins, communityTarget;
    private int communityStack;
    private int minX, minY, minZ, maxX, maxY, maxZ; // farm AABB (built bays)

    static final class MobDef {
        String id, display, wing, style, theme; EntityType entity; Material icon;
        int ox, oy, oz;
        Location stand, killPad, lootChest, stackBlock, communityChest;
        double[] cell;   // containment AABB {minX, maxX, minZ, maxZ, minY, maxY}, pit interior only
        boolean pit;     // true = sunken pit bay (slit window), false = animal pen
        boolean built;
    }
    static final class Session {
        UUID owner; String mobId; long endsAtMs, totalMs;
        int extraSpawners; Location returnLoc, stackLoc; boolean active = true;
        boolean unlocked; // paid pick for this session's mob
        int picks;        // paid picks THIS session -> cost doubles per pick
        BukkitTask spawnTask; BukkitTask hudTask; BossBar hud; TextDisplay stackHolo;
    }
    static final class PendingEnter { int secondsLeft; BukkitTask task; long cost; }
    static final class Region {
        String world; int minX, minY, minZ, maxX, maxY, maxZ;
        boolean contains(Location l) {
            if (l.getWorld() == null || !l.getWorld().getName().equals(world)) return false;
            int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
            return x >= minX && x <= maxX && y >= minY && y <= maxY && z >= minZ && z <= maxZ;
        }
    }
    private final List<Region> blacklist = new ArrayList<>();

    @Override public void onEnable() {
        holoKey = new NamespacedKey(this, "mfholo");
        farmMobKey = new NamespacedKey(this, "farmmob");
        stackHoloKey = new NamespacedKey(this, "stackholo");
        lastHitKey = new NamespacedKey(this, "lasthit");
        saveDefaultConfig();
        // 2.6 migration: old economy (5,000 entry / 30 min / no shop-priced picks)
        // -> new (10,000 entry / 15 min / real shop spawner price/16, 25k extends).
        boolean migrated = false;
        if (getConfig().getInt("entry-cost", -1) == 5000) { getConfig().set("entry-cost", 10000); migrated = true; }
        if (getConfig().getInt("session-minutes", -1) == 30) { getConfig().set("session-minutes", 15); migrated = true; }
        getConfig().options().copyDefaults(true); // adds extend-* keys if missing
        // 2.6.1: write the REAL spawner prices into config if it is missing or stale
        // (older builds fell back to flat 10,000/16 = 625 when shop-buy was absent).
        java.util.Set<String> have = getConfig().getConfigurationSection("shop-buy") == null
                ? new java.util.HashSet<>()
                : getConfig().getConfigurationSection("shop-buy").getKeys(false);
        int written = 0;
        for (var e : SHOP_BUY.entrySet()) {
            if (getConfig().getLong("shop-buy." + e.getKey(), -1L) <= 0) {
                getConfig().set("shop-buy." + e.getKey(), e.getValue());
                written++;
            }
        }
        if (written > 0) migrated = true;
        saveConfig();
        if (migrated) getLogger().info("Migrated MobFarm economy -> 10k entry / 15 min / real shop-priced picks.");
        if (written > 0) getLogger().info("MobFarm shop prices: wrote " + written + " real spawner price(s) into config.yml.");
        getLogger().info("MobFarm shop prices embedded: " + SHOP_BUY.size() + " mobs (zombie pick "
                + (SHOP_BUY.get("ZOMBIE") / Math.max(1, getConfig().getInt("unlock-divisor", 16))) + ").");
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        loadAll();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("mobfarm") != null) getCommand("mobfarm").setTabCompleter(this);
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                List<UUID> end = new ArrayList<>();
                for (var e : sessions.entrySet()) if (now >= e.getValue().endsAtMs) end.add(e.getKey());
                for (UUID u : end) endSession(u, true);
            }
        }.runTaskTimer(this, 40L, 40L);
        getLogger().info("MAVOMobFarm 2.7.1 enabled. mobs=" + mobs.size()
                + " center=" + (center == null ? "?" : center.getBlockX() + "," + center.getBlockZ())
                + " ai=" + mobAiEnabled());
    }

    @Override public void onDisable() {
        for (UUID u : new ArrayList<>(sessions.keySet())) endSession(u, false);
        saveData();
    }

    private void loadAll() {
        mobs.clear(); blacklist.clear();
        String wn = getConfig().getString("center.world", "world");
        World w = Bukkit.getWorld(wn);
        if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
        if (w != null) center = new Location(w, getConfig().getDouble("center.x", -15000),
                getConfig().getDouble("center.y", 200), getConfig().getDouble("center.z", -2000));
        ConfigurationSection ms = getConfig().getConfigurationSection("mobs");
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection c = ms.getConfigurationSection(id);
                if (c == null) continue;
                MobDef m = new MobDef();
                m.id = id;
                m.display = color(c.getString("display", id));
                m.wing = c.getString("wing", "hostile");
                m.style = c.getString("style", "pad");
                m.theme = c.getString("theme", "dark");
                m.ox = c.getInt("offset.x", 0); m.oy = c.getInt("offset.y", 0); m.oz = c.getInt("offset.z", -40);
                try { m.entity = EntityType.valueOf(c.getString("entity", "ZOMBIE").toUpperCase(Locale.ROOT)); }
                catch (Exception ex) { m.entity = EntityType.ZOMBIE; }
                Material ic = Material.matchMaterial(c.getString("icon", "ROTTEN_FLESH"));
                m.icon = ic != null ? ic : Material.ROTTEN_FLESH;
                mobs.put(id, m);
            }
        }
        communityCoins = data.getLong("community.coins", 0L);
        communityTarget = data.getLong("community.target", getConfig().getLong("community.start-target", 1_000_000L));
        communityStack = data.getInt("community.stack", getConfig().getInt("community.stack-start", 2));
        if (communityStack < 1) communityStack = getConfig().getInt("community.stack-start", 2);
        loadBlacklist();
        if (data.getBoolean("built", false) && center != null) {
            recomputeAABB();
            for (MobDef m : mobs.values()) computeGeom(m);
        }
    }

    private void loadBlacklist() {
        blacklist.clear();
        if (!getConfig().getBoolean("blacklist.enabled", true)) return;
        ConfigurationSection rs = getConfig().getConfigurationSection("blacklist.regions");
        if (rs == null) return;
        for (String id : rs.getKeys(false)) {
            ConfigurationSection c = rs.getConfigurationSection(id);
            if (c == null) continue;
            Region r = new Region();
            r.world = c.getString("world", "world");
            r.minX = c.getInt("min.x"); r.minY = c.getInt("min.y"); r.minZ = c.getInt("min.z");
            r.maxX = c.getInt("max.x"); r.maxY = c.getInt("max.y"); r.maxZ = c.getInt("max.z");
            blacklist.add(r);
        }
    }

    private void saveData() {
        data.set("community.coins", communityCoins);
        data.set("community.target", communityTarget);
        data.set("community.stack", communityStack);
        try { data.save(dataFile); } catch (Exception ignored) {}
    }

    private static String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private static String fmt(long n) { return String.format("%,d", n); }

    private int baseSpawners() {
        int start = getConfig().getInt("community.stack-start", getConfig().getInt("base-spawners", 2));
        int stack = communityStack > 0 ? communityStack : start;
        return Math.max(1, stack);
    }
    private int stackCount(Session s) {
        return Math.min(getConfig().getInt("max-spawners", 25), baseSpawners() + s.extraSpawners);
    }
    private long normalPrice() { return getConfig().getLong("normal-spawner-price", 10_000L); }
    /** Real shop spawner buy price for a mob (mirrors EconomyShopGUI shops/Mobs/spawners.yml). */
    private long shopBuy(MobDef m) {
        String k = m.entity.name().toUpperCase(Locale.ROOT);
        long v = getConfig().getLong("shop-buy." + k, -1L);
        if (v > 0) return v;                       // config override (kept in sync)
        Long emb = SHOP_BUY.get(k);                // authoritative embedded price
        return emb != null ? emb : Math.max(1L, normalPrice());
    }
    /** Base pick price = that mob's shop spawner buy price / 16 (zombie 1.2M -> 75,000). */
    private long basePickCost(MobDef m) {
        long d = Math.max(1L, getConfig().getLong("unlock-divisor", 16L));
        return Math.max(1L, shopBuy(m) / d);
    }
    /** Pick price: base doubles for EVERY additional paid pick in the same session. */
    private long pickCost(MobDef m, Session s) {
        long c = basePickCost(m);
        for (int i = 0; i < s.picks; i++) c = Math.min(c * 2L, Long.MAX_VALUE / 8);
        return c;
    }
    private long minPickCost() {
        long best = Long.MAX_VALUE;
        for (MobDef m : mobs.values()) best = Math.min(best, basePickCost(m));
        return best == Long.MAX_VALUE ? 1L : best;
    }
    private long extendCost() { return Math.max(1L, getConfig().getLong("extend-cost", 25_000L)); }
    private long extendMs() { return Math.max(1L, getConfig().getLong("extend-minutes", 15)) * 60_000L; }
    /** Extra spawner STACK on the same block: mob price /8, /4, /2, x1, then double. */
    private long stackCost(MobDef m, int extrasAlreadyBought) {
        long p = shopBuy(m);
        List<Integer> divs = getConfig().getIntegerList("extra-divisors");
        if (divs == null || divs.isEmpty()) divs = List.of(8, 4, 2, 1);
        if (extrasAlreadyBought < divs.size()) {
            int d = Math.max(1, divs.get(extrasAlreadyBought));
            return Math.max(1L, p / d);
        }
        long last = Math.max(1L, p / Math.max(1, divs.get(divs.size() - 1)));
        int over = extrasAlreadyBought - (divs.size() - 1);
        long cost = last;
        for (int i = 0; i < over; i++) cost = Math.min(cost * 2L, Long.MAX_VALUE / 4);
        return cost;
    }
    private List<MobDef> wingMobs(String wing) {
        List<MobDef> out = new ArrayList<>();
        for (MobDef m : mobs.values()) if (m.wing.equalsIgnoreCase(wing)) out.add(m);
        out.sort((a, b) -> ChatColor.stripColor(a.display).toLowerCase(Locale.ROOT)
                .compareTo(ChatColor.stripColor(b.display).toLowerCase(Locale.ROOT)));
        return out;
    }
    private Material eggIcon(MobDef m) {
        Material egg = Material.matchMaterial(m.entity.name() + "_SPAWN_EGG");
        return egg != null ? egg : m.icon;
    }
    private boolean mobAiEnabled() { return getConfig().getBoolean("mob-ai", true); }
    private boolean sunSafe() { return getConfig().getBoolean("sun-safe-killpad", true); }
    private boolean creditLastDamager() { return getConfig().getBoolean("credit-last-damager", true); }
    private boolean sessionHud() { return getConfig().getBoolean("session-hud", true); }
    private int protectRadius() { return Math.max(0, getConfig().getInt("protect-radius", 50)); }

    private Location origin(MobDef m) {
        return center.clone().add(m.ox, m.oy, m.oz);
    }

    private void computeGeom(MobDef m) {
        if (center == null) return;
        Location o = origin(m);
        World w = o.getWorld();
        int x = o.getBlockX(), y = o.getBlockY(), z = o.getBlockZ();
        m.stand = new Location(w, x + 0.5, y + 1, z + 6.5);
        m.killPad = new Location(w, x + 0.5, y - 0.9, z - 1);
        m.stackBlock = new Location(w, x + 6, y + 1, z - 2);
        m.lootChest = new Location(w, x + 0.5, y - 1, z + 1.2);
        m.communityChest = new Location(w, x - 4, y, z + 6);
    }

    private void recomputeAABB() {
        if (center == null) return;
        minX = center.getBlockX() - 20; maxX = center.getBlockX() + 20;
        minY = center.getBlockY() - 12; maxY = center.getBlockY() + 16;
        minZ = center.getBlockZ() - 20; maxZ = center.getBlockZ() + 20;
        for (MobDef m : mobs.values()) {
            Location o = origin(m);
            minX = Math.min(minX, o.getBlockX() - 14); maxX = Math.max(maxX, o.getBlockX() + 14);
            minY = Math.min(minY, o.getBlockY() - 10); maxY = Math.max(maxY, o.getBlockY() + 12);
            minZ = Math.min(minZ, o.getBlockZ() - 14); maxZ = Math.max(maxZ, o.getBlockZ() + 16);
        }
    }

    private boolean inFarmProtect(Location l) {
        if (center == null || l.getWorld() != center.getWorld()) return false;
        if (!data.getBoolean("built", false)) {
            // only hub ball until built
            return l.distanceSquared(center) <= (double) protectRadius() * protectRadius();
        }
        int r = protectRadius();
        int x = l.getBlockX(), y = l.getBlockY(), z = l.getBlockZ();
        return x >= minX - r && x <= maxX + r && y >= minY - r && y <= maxY + r && z >= minZ - r && z <= maxZ + r;
    }

    private boolean inBlacklist(Location l) {
        for (Region r : blacklist) if (r.contains(l)) return true;
        // also mobfarm protect volume as blacklist for wild dumps
        return inFarmProtect(l);
    }

    // ---------------- commands ----------------
    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "/mobfarm enter|leave|hub|status|buy|extend|pick|prices|info"
                    + (sender.hasPermission("mavomobfarm.admin")
                    ? "|tp|setcenter|build|rebuild|clear|clearhere|purge|reload|resholo" : ""));
            return true;
        }
        String a = args[0].toLowerCase(Locale.ROOT);
        if (a.equals("info")) {
            sender.sendMessage(ChatColor.GOLD + "MobFarm 2.7.1 " + ChatColor.GRAY + "entry "
                    + ChatColor.YELLOW + getConfig().getInt("entry-cost")
                    + ChatColor.GRAY + " · " + getConfig().getInt("session-minutes") + "m"
                    + ChatColor.GRAY + " · pick from " + ChatColor.GREEN + minPickCost()
                    + ChatColor.GRAY + " (spawner price/16, doubles per pick)"
                    + ChatColor.GRAY + " · extend " + ChatColor.YELLOW + extendCost()
                    + ChatColor.GRAY + "/+" + getConfig().getInt("extend-minutes", 15) + "m"
                    + ChatColor.GRAY + " · XP×" + ChatColor.AQUA + getConfig().getDouble("profession-xp-scale"));
            sender.sendMessage(ChatColor.GRAY + "Community " + ChatColor.YELLOW + communityCoins + "/" + communityTarget
                    + ChatColor.GRAY + " · base stack " + ChatColor.GREEN + "x" + baseSpawners()
                    + ChatColor.GRAY + " · mobs " + mobs.size());
            sender.sendMessage(ChatColor.DARK_GRAY + "Hub @ " + (center == null ? "?" :
                    center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ())
                    + " · protect r=" + protectRadius());
            sender.sendMessage(ChatColor.DARK_GRAY + "mob-ai=" + mobAiEnabled() + " · /mobfarm prices for costs");
            return true;
        }
        if (a.equals("prices")) {
            showPrices(sender);
            return true;
        }
        if (a.equals("reload")) {
            if (!sender.hasPermission("mavomobfarm.admin")) { sender.sendMessage(ChatColor.RED + "No."); return true; }
            reloadConfig(); loadAll();
            sender.sendMessage(ChatColor.GREEN + "Reloaded. mobs=" + mobs.size()
                    + " ai=" + mobAiEnabled() + " first-pick=" + minPickCost());
            for (Session s : sessions.values()) {
                if (s.hud == null && sessionHud()) startHud(s); else updateHud(s);
            }
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        if (args.length == 2 && args[1].equalsIgnoreCase("save")) {
            if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
            MobDef ms = findMob(args[0]);
            if (ms == null) {
                p.sendMessage(ChatColor.RED + "Unknown mob '" + args[0] + "'. /mobfarm <mob> save");
                return true;
            }
            BayGeometry.saveBay(this, ms, p::sendMessage);
            return true;
        }
        switch (a) {
            case "setcenter" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                center = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
                getConfig().set("center.world", center.getWorld().getName());
                getConfig().set("center.x", center.getX());
                getConfig().set("center.y", center.getY());
                getConfig().set("center.z", center.getZ());
                saveConfig();
                p.sendMessage(ChatColor.GREEN + "Center set " + center.getBlockX() + " " + center.getBlockY() + " " + center.getBlockZ());
            }
            case "tp", "hub" -> {
                if (a.equals("tp") && !p.hasPermission("mavomobfarm.admin")) {
                    // players use hub
                }
                goHub(p);
            }
            case "build" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                MobDef mb = args.length > 1 ? findMob(args[1]) : null;
                if (args.length > 1 && mb == null) {
                    p.sendMessage(ChatColor.RED + "Unknown mob '" + args[1] + "'. /mobfarm build <mob>");
                    return true;
                }
                if (mb != null) {
                    BayGeometry.buildMob(this, mb, p::sendMessage);
                } else {
                    buildComplex(p);
                }
            }
            case "rebuild" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                MobDef mr = args.length > 1 ? findMob(args[1]) : null;
                if (args.length > 1 && mr == null) {
                    p.sendMessage(ChatColor.RED + "Unknown mob '" + args[1] + "'. /mobfarm rebuild <mob>");
                    return true;
                }
                if (mr != null) {
                    BayGeometry.buildMob(this, mr, p::sendMessage);
                } else {
                    clearComplex(p); buildComplex(p);
                }
            }
            case "clear" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                clearComplex(p);
            }
            case "clearhere" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                int r = args.length > 1 ? parseInt(args[1], 80) : 80;
                clearHere(p, r);
            }
            case "purge" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                int r = args.length > 1 ? parseInt(args[1], 150) : 150;
                purgeOnly(p, r);
            }
            case "resholo" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                spawnHubHolo(); refreshBayHolos(); p.sendMessage(ChatColor.GREEN + "Holo ok.");
            }
            case "enter" -> beginEnter(p);
            case "leave" -> leaveFarm(p);
            case "status" -> showStatus(p);
            case "buy" -> buySpawner(p);
            case "extend" -> extendSession(p);
            case "pick" -> openPick(p);
            default -> p.sendMessage(ChatColor.RED + "Unknown. /mobfarm");
        }
        return true;
    }

    private MobDef findMob(String id) {
        String q = id.toLowerCase(Locale.ROOT);
        if (mobs.containsKey(q)) return mobs.get(q);
        for (MobDef m : mobs.values())
            if (m.entity.name().equalsIgnoreCase(q) || ChatColor.stripColor(m.display).equalsIgnoreCase(id))
                return m;
        return null;
    }

    private static int parseInt(String s, int def) {
        try { return Integer.parseInt(s); } catch (Exception e) { return def; }
    }

    private void showPrices(CommandSender sender) {
        if (sender instanceof Player pl) { openPrices(pl, 0); return; }
        sender.sendMessage(ChatColor.GOLD + "=== MobFarm prices ===");
        sender.sendMessage(ChatColor.YELLOW + "Session entry: " + ChatColor.WHITE + getConfig().getInt("entry-cost")
                + ChatColor.GRAY + " (" + getConfig().getInt("session-minutes") + "m) · extend "
                + ChatColor.YELLOW + extendCost() + ChatColor.GRAY + "/+" + getConfig().getInt("extend-minutes", 15) + "m");
        sender.sendMessage(ChatColor.AQUA + "Pick = real shop spawner price / 16 (doubles per extra pick):");
        for (MobDef m : mobs.values()) {
            sender.sendMessage(ChatColor.GRAY + "  " + ChatColor.stripColor(m.display) + ChatColor.WHITE
                    + " " + basePickCost(m) + ChatColor.GRAY + " → " + (basePickCost(m) * 2) + " → " + (basePickCost(m) * 4) + "…");
        }
    }

    // ---------------- pick + prices GUIs (2.6: 2 pages: hostile / farm animals) ----------------
    private void openPick(Player p, int wingIdx) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.endsAtMs < System.currentTimeMillis()) { p.sendMessage(ChatColor.RED + "/mobfarm enter first"); return; }
        String wing = wingIdx == 0 ? "hostile" : "animal";
        String label = wingIdx == 0 ? "Hostile (1/2)" : "Farm animals (2/2)";
        Inventory inv = Bukkit.createInventory(null, 54,
                ChatColor.DARK_RED + "MobFarm Pick — " + label);
        int slot = 0;
        for (MobDef m : wingMobs(wing)) {
            ItemStack it = new ItemStack(eggIcon(m));
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(m.display);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Wing: " + m.wing + " · " + m.style);
            long now = pickCost(m, s);
            lore.add(ChatColor.YELLOW + "Pick #" + (s.picks + 1) + " cost: " + fmt(now) + " coins");
            lore.add(ChatColor.DARK_GRAY + "Next pick (this session): " + fmt(Math.min(now * 2L, Long.MAX_VALUE / 8)));
            if (m.id.equals(s.mobId) && s.unlocked) lore.add(ChatColor.GREEN + "✓ ACTIVE");
            else if (m.id.equals(s.mobId)) lore.add(ChatColor.GOLD + "Selected — pay to unlock");
            lore.add(ChatColor.AQUA + "Click to pay & teleport to zone");
            meta.setLore(lore);
            it.setItemMeta(meta);
            inv.setItem(slot++, it);
        }
        // nav + info
        ItemStack nav = new ItemStack(Material.ARROW);
        ItemMeta nm = nav.getItemMeta();
        nm.setDisplayName(wingIdx == 0 ? ChatColor.GREEN + "▶ Farm animals" : ChatColor.GOLD + "◀ Hostile");
        nav.setItemMeta(nm);
        inv.setItem(49, nav);
        ItemStack info = new ItemStack(Material.CLOCK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.GOLD + "Session info");
        im.setLore(List.of(ChatColor.YELLOW + "Entry " + fmt(getConfig().getInt("entry-cost")) + " · "
                        + getConfig().getInt("session-minutes") + "m",
                ChatColor.YELLOW + "Extend " + fmt(extendCost()) + "/+" + getConfig().getInt("extend-minutes", 15) + "m",
                ChatColor.GRAY + "picks so far this session: " + s.picks,
                ChatColor.GRAY + "balance: " + fmt((long) (econ == null ? 0 : econ.getBalance(p)))));
        info.setItemMeta(im);
        inv.setItem(50, info);
        p.openInventory(inv);
    }

    private void openPrices(Player p, int wingIdx) {
        // read-only version of the pick GUI (no payment) — /mobfarm prices from console stays chat
        String wing = wingIdx == 0 ? "hostile" : "animal";
        String label = wingIdx == 0 ? "Hostile (1/2)" : "Farm animals (2/2)";
        Inventory inv = Bukkit.createInventory(null, 54,
                ChatColor.DARK_RED + "MobFarm Prices — " + label);
        int slot = 0;
        for (MobDef m : wingMobs(wing)) {
            ItemStack it = new ItemStack(eggIcon(m));
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(m.display);
            long b = basePickCost(m);
            meta.setLore(List.of(
                    ChatColor.GRAY + "Shop spawner price: " + ChatColor.YELLOW + fmt(shopBuy(m)) + " coins",
                    ChatColor.YELLOW + "Pick #1: " + fmt(b),
                    ChatColor.YELLOW + "Pick #2: " + fmt(Math.min(b * 2L, Long.MAX_VALUE / 8)),
                    ChatColor.YELLOW + "Pick #3: " + fmt(Math.min(b * 4L, Long.MAX_VALUE / 8)),
                    ChatColor.DARK_GRAY + "Stack extras: " + fmt(stackCost(m, 0)) + " → "
                            + fmt(stackCost(m, 1)) + " → " + fmt(stackCost(m, 2)),
                    ChatColor.GRAY + "= shop buy price / 16, then doubles per pick"));
            it.setItemMeta(meta);
            inv.setItem(slot++, it);
        }
        ItemStack nav = new ItemStack(Material.ARROW);
        ItemMeta nm = nav.getItemMeta();
        nm.setDisplayName(wingIdx == 0 ? ChatColor.GREEN + "▶ Farm animals" : ChatColor.GOLD + "◀ Hostile");
        nav.setItemMeta(nm);
        inv.setItem(49, nav);
        ItemStack info = new ItemStack(Material.CLOCK);
        ItemMeta im = info.getItemMeta();
        im.setDisplayName(ChatColor.GOLD + "Session info");
        im.setLore(List.of(ChatColor.YELLOW + "Entry " + fmt(getConfig().getInt("entry-cost")) + " · "
                        + getConfig().getInt("session-minutes") + "m",
                ChatColor.YELLOW + "Extend " + fmt(extendCost()) + "/+" + getConfig().getInt("extend-minutes", 15) + "m",
                ChatColor.GRAY + "Hub hologram shows the same info"));
        info.setItemMeta(im);
        inv.setItem(50, info);
        p.openInventory(inv);
    }

    private void showStatus(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.endsAtMs < System.currentTimeMillis()) {
            p.sendMessage(ChatColor.GRAY + "No active session. /mobfarm enter");
            return;
        }
        long left = Math.max(0, (s.endsAtMs - System.currentTimeMillis()) / 1000L);
        p.sendMessage(ChatColor.GOLD + "Session " + ChatColor.YELLOW + (left / 60) + "m " + (left % 60) + "s"
                + ChatColor.GRAY + " · mob " + ChatColor.WHITE + s.mobId
                + ChatColor.GRAY + " · stack x" + stackCount(s)
                + ChatColor.GRAY + " · unlocked=" + s.unlocked);
    }

    private void beginEnter(Player p) {
        if (center == null) { p.sendMessage(ChatColor.RED + "Farm not set. Admin: /mobfarm setcenter"); return; }
        Session existing = sessions.get(p.getUniqueId());
        if (existing != null && existing.endsAtMs > System.currentTimeMillis()) {
            existing.active = true;
            if (existing.spawnTask == null && existing.unlocked) startSpawnTask(existing);
            if (existing.hud == null) startHud(existing); else updateHud(existing);
            teleportToSession(p, existing);
            p.sendMessage(ChatColor.GREEN + "Welcome back — same session/stack. /mobfarm pick");
            return;
        }
        if (pending.containsKey(p.getUniqueId())) { p.sendMessage(ChatColor.RED + "Already entering."); return; }
        long cost = getConfig().getLong("entry-cost", 10_000L);
        if (econ == null) { p.sendMessage(ChatColor.RED + "Economy missing."); return; }
        if (!econ.has(p, cost)) { p.sendMessage(ChatColor.RED + "Need " + fmt(cost) + " coins to enter."); return; }
        long unlock = minPickCost();
        p.sendActionBar(ChatColor.RED + "" + ChatColor.BOLD + "MAKE SURE TO HAVE ≥ " + fmt(unlock)
                + " COINS TO UNLOCK THE CHEAPEST SPAWNER");
        p.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "⚠ After entry you must PAY " + fmt(unlock)
                + "+ coins in /mobfarm pick to unlock a mob zone (no free spawner).");
        int secs = Math.max(3, getConfig().getInt("entry-cancel-seconds", 10));
        PendingEnter pe = new PendingEnter(); pe.secondsLeft = secs; pe.cost = cost;
        pe.task = new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) { pending.remove(p.getUniqueId()); cancel(); return; }
                pe.secondsLeft--;
                if (pe.secondsLeft > 0) {
                    p.sendActionBar(ChatColor.GOLD + "Mob farm in " + pe.secondsLeft + "s… don't move! "
                            + ChatColor.RED + "Need ≥" + fmt(unlock) + " after entry to unlock");
                    return;
                }
                pending.remove(p.getUniqueId()); cancel();
                if (!econ.has(p, pe.cost)) { p.sendMessage(ChatColor.RED + "Not enough coins."); return; }
                econ.withdrawPlayer(p, pe.cost); startSession(p);
            }
        }.runTaskTimer(this, 20L, 20L);
        pending.put(p.getUniqueId(), pe);
        p.sendMessage(ChatColor.GOLD + "Entering… " + secs + "s (move = cancel). Cost " + fmt(cost)
                + ChatColor.GRAY + " · " + getConfig().getInt("session-minutes") + "m session");
    }

    @EventHandler
    public void onMoveCancel(PlayerMoveEvent e) {
        PendingEnter pe = pending.get(e.getPlayer().getUniqueId());
        if (pe == null || e.getTo() == null) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            pe.task.cancel(); pending.remove(e.getPlayer().getUniqueId());
            e.getPlayer().sendMessage(ChatColor.RED + "Entry cancelled.");
        }
    }

    private void startSession(Player p) {
        Session s = new Session();
        s.owner = p.getUniqueId();
        s.mobId = data.getString("players." + p.getUniqueId() + ".last-mob", "zombie");
        if (!mobs.containsKey(s.mobId)) s.mobId = mobs.isEmpty() ? "zombie" : mobs.keySet().iterator().next();
        long sessMs = Math.max(1L, getConfig().getInt("session-minutes", 15)) * 60_000L;
        s.totalMs = sessMs;
        s.endsAtMs = System.currentTimeMillis() + sessMs;
        s.returnLoc = p.getLocation().clone(); s.extraSpawners = 0; s.unlocked = false; s.picks = 0;
        sessions.put(p.getUniqueId(), s);
        startHud(s);
        goHub(p);
        p.sendMessage(ChatColor.GREEN + "Session started (" + getConfig().getInt("session-minutes", 15)
                + "m). " + ChatColor.YELLOW + "Pay "
                + fmt(minPickCost()) + "+" + ChatColor.GREEN + " in " + ChatColor.AQUA + "/mobfarm pick"
                + ChatColor.GREEN + " to unlock a zone (teleports you there).");
        p.sendMessage(ChatColor.RED + "No free spawner — unlock required. /mobfarm prices");
        openPick(p);
    }

    /**
     * 2.6: /mobfarm extend — 25,000 coins for +15 min.
     * Adds to the SAME session timer (endsAtMs) so the HUD countdown shows the new time.
     */
    private void extendSession(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.endsAtMs < System.currentTimeMillis()) { p.sendMessage(ChatColor.RED + "No active session."); return; }
        long cost = extendCost();
        if (econ == null) { p.sendMessage(ChatColor.RED + "Economy missing."); return; }
        if (!econ.has(p, cost)) { p.sendMessage(ChatColor.RED + "Need " + fmt(cost) + " coins to extend."); return; }
        econ.withdrawPlayer(p, cost);
        long add = extendMs();
        s.totalMs += add;
        s.endsAtMs += add;
        updateHud(s);
        long left = Math.max(0, (s.endsAtMs - System.currentTimeMillis()) / 1000L);
        p.sendMessage(ChatColor.GREEN + "Extended +" + getConfig().getInt("extend-minutes", 15) + "m (paid "
                + fmt(cost) + "). " + ChatColor.YELLOW + left / 60 + "m " + left % 60 + "s left"
                + ChatColor.GRAY + " — next extend " + fmt(cost) + ".");
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
    }

    private void leaveFarm(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) { p.sendMessage(ChatColor.GRAY + "No session."); return; }
        if (s.returnLoc != null) p.teleport(s.returnLoc); else if (center != null) p.teleport(center);
        p.sendMessage(ChatColor.YELLOW + "Left farm zone. Timer still runs (HUD hides). "
                + ChatColor.GRAY + "/mobfarm enter free while session live — stack kept.");
    }

    private void goHub(Player p) {
        if (center == null) { p.sendMessage(ChatColor.RED + "No hub."); return; }
        p.teleport(center.clone().add(0.5, 1, 0.5));
        p.sendMessage(ChatColor.AQUA + "MobFarm hub.");
    }

    private void endSession(UUID u, boolean announce) {
        Session s = sessions.remove(u); if (s == null) return;
        if (s.spawnTask != null) s.spawnTask.cancel();
        stopHud(s);
        clearSessionMobs(s); removeStackHolo(s);
        if (s.stackLoc != null && s.stackLoc.getBlock().getType() == Material.SPAWNER) {
            if (s.stackLoc.getBlock().getState() instanceof CreatureSpawner cs) {
                try { cs.setSpawnedType(EntityType.PIG); cs.setDelay(9999); cs.update(true, false); } catch (Throwable ignored) {}
            }
        }
        data.set("players." + u + ".last-mob", s.mobId);
        Player p = Bukkit.getPlayer(u);
        if (p != null && p.isOnline()) {
            if (s.returnLoc != null) p.teleport(s.returnLoc); else if (center != null) p.teleport(center);
            if (announce) p.sendMessage(ChatColor.RED + "Session ended. Bought stacks cleared.");
        }
        saveData();
    }

    private void buySpawner(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.endsAtMs < System.currentTimeMillis()) { p.sendMessage(ChatColor.RED + "No session."); return; }
        MobDef sm = mobs.get(s.mobId);
        long pick = sm == null ? minPickCost() : basePickCost(sm);
        if (!s.unlocked) { p.sendMessage(ChatColor.RED + "Unlock a mob first: /mobfarm pick (" + fmt(pick) + " coins)"); return; }
        int max = getConfig().getInt("max-spawners", 25);
        if (stackCount(s) >= max) { p.sendMessage(ChatColor.RED + "Max stack " + max); return; }
        long cost = sm == null ? 1L : stackCost(sm, s.extraSpawners);
        if (econ == null || !econ.has(p, cost)) {
            p.sendMessage(ChatColor.RED + "Need " + cost + " coins for next stack.");
            return;
        }
        econ.withdrawPlayer(p, cost); s.extraSpawners++; setupCellStack(s);
        updateHud(s);
        p.sendMessage(ChatColor.GREEN + "Stack now " + ChatColor.YELLOW + "x" + stackCount(s)
                + ChatColor.GREEN + " Paid " + fmt(cost) + ". Next: "
                + fmt(sm == null ? 1L : stackCost(sm, s.extraSpawners)));
    }

    private void openPick(Player p) { openPick(p, 0); }

    @EventHandler
    public void onPickClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        String title = e.getView().getTitle();
        if (title == null) return;
        if (title.contains("MobFarm Pick")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType().isAir()) return;
            ItemMeta meta = e.getCurrentItem().getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;
            String name = ChatColor.stripColor(meta.getDisplayName());
            if (name.contains("Farm animals")) { openPick(p, 1); return; }
            if (name.contains("Hostile")) { openPick(p, 0); return; }
            if (name.contains("Session info")) return;
            Session s = sessions.get(p.getUniqueId());
            if (s == null || s.endsAtMs < System.currentTimeMillis()) { p.closeInventory(); return; }
            MobDef chosen = null;
            for (MobDef m : mobs.values()) {
                if (ChatColor.stripColor(m.display).equalsIgnoreCase(name)) { chosen = m; break; }
            }
            if (chosen == null) return;
            long cost = pickCost(chosen, s);
            // switching mob mid-session still requires payment; each paid pick doubles
            boolean needPay = !s.unlocked || !chosen.id.equals(s.mobId);
            if (needPay) {
                if (econ == null || !econ.has(p, cost)) {
                    p.sendMessage(ChatColor.RED + "Need " + fmt(cost) + " coins to unlock " + ChatColor.stripColor(chosen.display));
                    return;
                }
                econ.withdrawPlayer(p, cost);
                s.picks++;
                p.sendMessage(ChatColor.GREEN + "Paid " + fmt(cost) + " — unlocked " + chosen.display
                        + ChatColor.GRAY + " (next pick this session: "
                        + fmt(Math.min(cost * 2L, Long.MAX_VALUE / 8)) + ")");
            }
            if (s.spawnTask != null) { s.spawnTask.cancel(); s.spawnTask = null; }
            clearSessionMobs(s); removeStackHolo(s);
            s.mobId = chosen.id;
            s.unlocked = true;
            if (needPay) s.extraSpawners = 0;
            setupCellStack(s);
            startSpawnTask(s);
            updateHud(s);
            p.closeInventory();
            teleportToSession(p, s);
            p.sendMessage(ChatColor.AQUA + "Zone ready: " + chosen.display + ChatColor.GRAY
                    + " · stack x" + stackCount(s) + " · hit from the safe window");
            return;
        }
        if (title.contains("MobFarm Prices")) {
            e.setCancelled(true);
            if (e.getCurrentItem() == null || e.getCurrentItem().getType().isAir()) return;
            ItemMeta meta = e.getCurrentItem().getItemMeta();
            if (meta == null || !meta.hasDisplayName()) return;
            String name = ChatColor.stripColor(meta.getDisplayName());
            if (name.contains("Farm animals")) openPrices(p, 1);
            else if (name.contains("Hostile")) openPrices(p, 0);
        }
    }

    private void teleportToSession(Player p, Session s) {
        MobDef m = mobs.get(s.mobId);
        if (m == null || center == null) { goHub(p); return; }
        if (m.stand == null) computeGeom(m);
        Location dest = m.stand.clone();
        dest.setYaw(180f); dest.setPitch(20f);
        p.teleport(dest);
    }

    // ---------------- build (2.7.1: packs + hub only; per-bay apply is separate) ----------------
    /** /mobfarm build (no arg): generate all 36 <id>-datapack.zip into world/datapacks/
     *  AND build the hub/HUD platform ONLY. No per-bay build is applied (the packs are
     *  picked up at the next server START, then /mobfarm build <mob> applies one bay). */
    private void buildComplex(Player admin) {
        if (center == null) { admin.sendMessage(ChatColor.RED + "Set center first."); return; }
        stopAllFarmActivity("build");
        World w = center.getWorld();
        // hub platform
        int hx = center.getBlockX(), hy = center.getBlockY(), hz = center.getBlockZ();
        for (int x = -12; x <= 12; x++)
            for (int z = -12; z <= 12; z++) {
                Material fl = ((x + z) & 1) == 0 ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE;
                w.getBlockAt(hx + x, hy - 1, hz + z).setType(fl, false);
                for (int y = 0; y <= 4; y++) setAir(w, hx + x, hy + y, hz + z);
            }
        // hub beacon pillars
        for (int[] d : new int[][]{{-10, -10}, {-10, 10}, {10, -10}, {10, 10}}) {
            for (int y = 0; y <= 6; y++)
                w.getBlockAt(hx + d[0], hy + y, hz + d[1]).setType(Material.CRYING_OBSIDIAN, false);
            w.getBlockAt(hx + d[0], hy + 7, hz + d[1]).setType(Material.SEA_LANTERN, false);
        }
        // community chest hub
        placeDoubleChest(w, hx, hy, hz + 4, BlockFace.NORTH);
        Block sign = w.getBlockAt(hx, hy + 1, hz + 4);
        sign.setType(Material.OAK_SIGN, false);
        writeSign(sign, ChatColor.GOLD + "COMMUNITY", ChatColor.YELLOW + "FARM CHEST",
                ChatColor.WHITE + "Donate loot", ChatColor.GRAY + "→ stack goal");
        // wing signs
        Block hs = w.getBlockAt(hx - 6, hy + 1, hz);
        hs.setType(Material.OAK_SIGN, false);
        writeSign(hs, ChatColor.RED + "HOSTILE", ChatColor.WHITE + "WING ← WEST",
                ChatColor.GRAY + "/mobfarm pick", ChatColor.DARK_GRAY + "pay unlock");
        Block as = w.getBlockAt(hx + 6, hy + 1, hz);
        as.setType(Material.OAK_SIGN, false);
        writeSign(as, ChatColor.GREEN + "ANIMAL", ChatColor.WHITE + "WING → EAST",
                ChatColor.GRAY + "/mobfarm pick", ChatColor.DARK_GRAY + "pay unlock");

        // generate every mob's datapack from its pristine Java layout (bays cleared again;
        // only the packs + hub remain - /mobfarm build <mob> applies packs from here on)
        int n = 0;
        List<String> bad = new ArrayList<>();
        for (MobDef m : mobs.values()) {
            computeGeom(m);
            try {
                if (BayGeometry.generatePack(this, m)) { m.built = true; n++; }
                else bad.add(m.id);
            } catch (Throwable t) {
                getLogger().warning("generatePack " + m.id + " failed: " + t);
                bad.add(m.id);
            }
        }
        recomputeAABB();
        // nothing is built in the world yet: sessions stay closed until /mobfarm build <mob>
        data.set("built", false); saveData();
        spawnHubHolo(); refreshBayHolos();
        String dir = BayGeometry.packDir(w).getAbsolutePath();
        admin.sendMessage(ChatColor.GREEN + "Hub built + " + n + "/" + mobs.size()
                + " datapacks generated -> " + dir);
        admin.sendMessage(ChatColor.YELLOW + "The server scans datapacks at STARTUP only: "
                + "restart once, then /mobfarm build <mob> builds each bay from its zip.");
        admin.sendMessage(ChatColor.GRAY + "TP: /tp @s " + hx + " " + (hy + 1) + " " + hz
                + " — stand on the marked HIT pads in the trench; loot chests face the walkway.");
        if (!bad.isEmpty())
            admin.sendMessage(ChatColor.RED + "Pack generation FAILED for: " + String.join(" ", bad)
                    + " (check the server log for 'writePack ... FAILED').");
    }

    void markBuilt() { data.set("built", true); saveData(); }

    /** 2.7.0: every mob has its own hand-built layout (BayGeometry). */
    private void buildBay(MobDef m) {
        if (center == null) return;
        BayGeometry.build(this, m);
    }

    private Material pedestal(MobDef m) {
        return switch (m.style) {
            case "forge" -> Material.NETHER_BRICKS;
            case "barn", "pen", "village" -> Material.OAK_PLANKS;
            case "aqua", "water" -> Material.PRISMARINE;
            case "arena" -> Material.DEEPSLATE_BRICKS;
            case "web", "spider" -> Material.OAK_LOG;
            case "cells", "slime" -> Material.IRON_BLOCK;
            case "brutal" -> Material.POLISHED_BLACKSTONE;
            case "gallery" -> Material.QUARTZ_BLOCK;
            case "totem", "enderman", "bunker" -> Material.OBSIDIAN;
            default -> Material.MOSSY_STONE_BRICKS;
        };
    }

    /**
     * Places a proper DOUBLE chest pair along +X (facing-aware LEFT/RIGHT).
     * Bukkit LEFT/RIGHT are relative to the chest itself (opposite to the player's view):
     *   face NORTH -> west block (x) = LEFT, east block (x+1) = RIGHT
     *   face SOUTH -> east block (x+1) = LEFT, west block (x) = RIGHT
     * 2.6.3: the pair is written with NO intermediate SINGLE state (both halves
     * get their final LEFT/RIGHT at once, physics off, then a physics re-apply),
     * so the server never has a moment to "un-merge" them; a next-tick check
     * re-applies if the game still reports a single.
     */
    void placeDoubleChest(World w, int x, int y, int z, BlockFace facing) {
        Block b1 = w.getBlockAt(x, y, z);
        Block b2 = w.getBlockAt(x + 1, y, z);
        b1.setType(Material.CHEST, false);
        b2.setType(Material.CHEST, false);
        applyChestPair(b1, b2, facing, false);
        applyChestPair(b1, b2, facing, true);
        // some servers resolve chest pairing asynchronously - verify next tick
        Bukkit.getScheduler().runTask(this, () -> {
            if (!b1.getChunk().isLoaded() || !b2.getChunk().isLoaded()) return;
            try {
                org.bukkit.block.data.type.Chest c1 = (org.bukkit.block.data.type.Chest) b1.getBlockData();
                org.bukkit.block.data.type.Chest c2 = (org.bukkit.block.data.type.Chest) b2.getBlockData();
                if (c1.getType() == org.bukkit.block.data.type.Chest.Type.SINGLE
                        || c2.getType() == org.bukkit.block.data.type.Chest.Type.SINGLE) {
                    applyChestPair(b1, b2, facing, true);
                }
            } catch (Throwable ignored) {}
        });
    }

    private void applyChestPair(Block b1, Block b2, BlockFace facing, boolean physics) {
        try {
            org.bukkit.block.data.type.Chest c1 = (org.bukkit.block.data.type.Chest) b1.getBlockData();
            org.bukkit.block.data.type.Chest c2 = (org.bukkit.block.data.type.Chest) b2.getBlockData();
            c1.setFacing(facing); c2.setFacing(facing);
            if (facing == BlockFace.NORTH) {          // west half (x) = LEFT
                c1.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
                c2.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
            } else {                                   // south: east half (x+1) = LEFT
                c1.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
                c2.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
            }
            b1.setBlockData(c1, physics);
            b2.setBlockData(c2, physics);
        } catch (Throwable ignored) {}
    }

    void setTrapdoor(World w, int x, int y, int z, BlockFace facing) {
        Block b = w.getBlockAt(x, y, z);
        b.setType(Material.OAK_TRAPDOOR, false);
        try {
            org.bukkit.block.data.BlockData d = b.getBlockData();
            ((org.bukkit.block.data.Directional) d).setFacing(facing);
            try { ((org.bukkit.block.data.Bisected) d).setHalf(org.bukkit.block.data.Bisected.Half.BOTTOM); } catch (Throwable ignored) {}
            try { ((org.bukkit.block.data.Openable) d).setOpen(false); } catch (Throwable ignored) {}
            b.setBlockData(d, false);
        } catch (Throwable ignored) {}
    }

    /** Rotate a sign block so its writing faces `facing` (works on standing + wall signs). */
    static void faceSign(Block b, BlockFace facing) {
        try {
            org.bukkit.block.data.BlockData d = b.getBlockData();
            if (d instanceof org.bukkit.block.data.Rotatable r) r.setRotation(facing);
            else if (d instanceof org.bukkit.block.data.Directional dir) dir.setFacing(facing);
            b.setBlockData(d, false);
        } catch (Throwable ignored) {}
    }

    void writeSign(Block b, String l0, String l1, String l2, String l3) {
        if (!(b.getState() instanceof Sign si)) return;
        try {
            si.getSide(Side.FRONT).setLine(0, nz(l0));
            si.getSide(Side.FRONT).setLine(1, nz(l1));
            si.getSide(Side.FRONT).setLine(2, nz(l2));
            si.getSide(Side.FRONT).setLine(3, nz(l3));
        } catch (Throwable t) {
            try {
                si.setLine(0, nz(l0)); si.setLine(1, nz(l1)); si.setLine(2, nz(l2)); si.setLine(3, nz(l3));
            } catch (Throwable ignored) {}
        }
        si.update(true, false);
    }
    private static String nz(String s) { return s == null ? "" : s; }
    private static void setAir(World w, int x, int y, int z) {
        w.getBlockAt(x, y, z).setType(Material.AIR, false);
    }

    private void setupCellStack(Session s) {
        MobDef m = mobs.get(s.mobId);
        if (m == null || center == null) return;
        if (m.stackBlock == null) computeGeom(m);
        World w = center.getWorld();
        Block b = m.stackBlock.getBlock();
        b.getRelative(0, -1, 0).setType(pedestal(m), false);
        b.setType(Material.SPAWNER, false);
        s.stackLoc = b.getLocation().clone();
        if (b.getState() instanceof CreatureSpawner cs) {
            try {
                cs.setSpawnedType(m.entity);
                cs.setDelay(Integer.MAX_VALUE / 4);
                try { cs.setSpawnCount(0); cs.setMinSpawnDelay(99999); cs.setMaxSpawnDelay(99999); } catch (Throwable ignored) {}
                cs.update(true, false);
            } catch (Throwable ignored) {}
        }
        spawnStackHolo(s);
    }

    private void spawnStackHolo(Session s) {
        removeStackHolo(s);
        if (s.stackLoc == null) return;
        Location loc = s.stackLoc.clone().add(0.5, 1.4, 0.5);
        int n = stackCount(s);
        MobDef m = mobs.get(s.mobId);
        String name = m != null ? ChatColor.stripColor(m.display) : s.mobId;
        String text = ChatColor.GOLD + "" + ChatColor.BOLD + "STACK x" + n
                + "\n" + ChatColor.WHITE + name
                + "\n" + ChatColor.GRAY + "/mobfarm buy";
        s.stackHolo = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setText(text); d.setBillboard(Display.Billboard.CENTER); d.setShadowed(true);
            try { d.setDefaultBackground(false); d.setBackgroundColor(Color.fromARGB(160, 10, 10, 30)); } catch (Throwable ignored) {}
            d.setLineWidth(120);
            var tr = d.getTransformation(); tr.getScale().set(0.9f); d.setTransformation(tr);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(stackHoloKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void removeStackHolo(Session s) {
        if (s.stackHolo != null && !s.stackHolo.isDead()) s.stackHolo.remove();
        s.stackHolo = null;
        if (s.stackLoc != null) {
            for (Entity e : s.stackLoc.getWorld().getNearbyEntities(s.stackLoc.clone().add(0.5, 1.4, 0.5), 3, 3, 3)) {
                if (e instanceof TextDisplay td
                        && td.getPersistentDataContainer().has(stackHoloKey, PersistentDataType.BYTE)) td.remove();
            }
        }
    }

    private void startSpawnTask(Session s) {
        if (s.spawnTask != null) s.spawnTask.cancel();
        if (!s.unlocked) return;
        MobDef m = mobs.get(s.mobId);
        if (m == null) return;
        final Location o = origin(m);
        final long spawnEvery = Math.max(20L, getConfig().getLong("stack-spawn-interval-ticks", 40L));
        final int maxMobs = getConfig().getInt("max-mobs-in-cell", 12);
        s.spawnTask = new BukkitRunnable() {
            long age = 0;
            @Override public void run() {
                age += 5;
                if (!data.getBoolean("built", false)) { cancel(); return; }
                Session live = sessions.get(s.owner);
                if (live == null || live != s || s.endsAtMs < System.currentTimeMillis()) { cancel(); return; }
                MobDef md = mobs.get(s.mobId); if (md == null) return;
                Player owner = Bukkit.getPlayer(s.owner);
                if (owner == null || !owner.isOnline()) return;
                // only spawn when player near THIS bay
                if (owner.getWorld() != o.getWorld() || owner.getLocation().distanceSquared(o) > 40 * 40) return;

                World w = o.getWorld();
                boolean ai = mobAiEnabled();
                if ("arena".equals(md.style)) ai = false; // phantoms float: keep them low, no dives
                Location pad = md.killPad != null ? md.killPad.clone() : o.clone().add(0.5, -1, 0.5);

                int alive = 0;
                for (LivingEntity le : w.getLivingEntities()) {
                    if (le instanceof Player) continue;
                    if (!le.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) continue;
                    Location loc = le.getLocation();
                    if (loc.getWorld() != w || loc.distanceSquared(o) > 18 * 18) continue;
                    alive++;
                    try { le.setAI(ai); } catch (Throwable ignored) {}
                    if (sunSafe()) {
                        try { le.setFireTicks(0); } catch (Throwable ignored) {}
                    }
                    // pin / contain
                    containMob(le, md, pad, ai);
                }

                if (age % spawnEvery != 0) return;
                if (alive >= maxMobs) return;
                int stack = stackCount(s);
                int per = Math.max(1, getConfig().getInt("spawn-per-stack", 1));
                int want = Math.min(Math.min(stack * per, maxMobs - alive), Math.max(1, Math.min(3, stack)));
                for (int i = 0; i < want; i++) {
                    try {
                        Location at = pad.clone().add(
                                (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8,
                                0.1,
                                (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8);
                        if (at.getBlock().getType().isSolid()) at.add(0, 0.5, 0);
                        Entity ent = w.spawnEntity(at, md.entity);
                        if (ent instanceof LivingEntity le) {
                            tagFarmMob(le, owner, ai, md);
                        }
                    } catch (Throwable t) {
                        getLogger().warning("Spawn " + md.entity + ": " + t.getMessage());
                        break;
                    }
                }
            }
        }.runTaskTimer(this, 15L, 5L);
    }

    private void tagFarmMob(LivingEntity le, Player owner, boolean ai, MobDef md) {
        try {
            if (le instanceof Ageable ag && !ag.isAdult()) ag.setAdult();
            if (le.getVehicle() != null) le.getVehicle().remove();
            le.eject();
            for (Entity pass : new ArrayList<>(le.getPassengers())) pass.remove();
        } catch (Throwable ignored) {}
        // force small slimes / magma
        try {
            if (le instanceof org.bukkit.entity.Slime slime) slime.setSize(1);
        } catch (Throwable ignored) {}
        // piglins not zombified hostility dampen
        try {
            if (le instanceof org.bukkit.entity.Piglin piglin) {
                piglin.setImmuneToZombification(true);
                piglin.setBaby(false);
            }
        } catch (Throwable ignored) {}
        le.getPersistentDataContainer().set(farmMobKey, PersistentDataType.BYTE, (byte) 1);
        le.setRemoveWhenFarAway(true);
        le.setCanPickupItems(false);
        try { le.setAI(ai); } catch (Throwable ignored) {}
        if (sunSafe()) try { le.setFireTicks(0); } catch (Throwable ignored) {}
        if (owner != null) {
            le.getPersistentDataContainer().set(lastHitKey, PersistentDataType.STRING, owner.getUniqueId().toString());
        }
        try {
            if (le.getAttribute(Attribute.FOLLOW_RANGE) != null)
                le.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(ai ? 12 : 2);
        } catch (Throwable ignored) {}
        // enderman: no teleport — keep AI but cancel teleport event separately; reduce movement
        if (md.style.equals("enderman") || md.style.equals("totem")) {
            try { le.setAI(true); } catch (Throwable ignored) {}
        }
        // creeper charged false
        if (le instanceof org.bukkit.entity.Creeper cr) {
            try { cr.setPowered(false); cr.setMaxFuseTicks(80); } catch (Throwable ignored) {}
        }
        le.setVelocity(new Vector(0, -0.1, 0));
    }

    /** Per-bay containment AABB {minX, maxX, minZ, maxZ, minY, maxY} - set by the
     *  builders to the pit/pen INTERIOR, so mobs can never reach the trench/walkway. */
    private double[] cellBox(MobDef m, Location pad) {
        if (m.cell != null) return m.cell;
        return new double[]{pad.getX() - 3.4, pad.getX() + 3.4, pad.getZ() - 4.6, pad.getZ() + 3.6,
                pad.getY() - 1.6, pad.getY() + 3.8};
    }

    /** Safety net: any farm mob outside its cell box is teleported back to the kill pad. */
    private void containMob(LivingEntity le, MobDef md, Location pad, boolean ai) {
        Location loc = le.getLocation();
        double[] b = cellBox(md, pad);
        boolean out = loc.getX() < b[0] || loc.getX() > b[1]
                || loc.getZ() < b[2] || loc.getZ() > b[3]
                || loc.getY() < b[4] || loc.getY() > b[5];
        if (out) {
            le.teleport(pad.clone().add(
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.5,
                    0,
                    (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.5));
            return;
        }
        double dist = loc.distanceSquared(pad);
        if (!ai) {
            double pullX = pad.getX() - loc.getX();
            double pullZ = pad.getZ() - loc.getZ();
            le.setVelocity(new Vector(pullX * 0.15, Math.min(-0.02, le.getVelocity().getY()), pullZ * 0.15));
        } else if (dist > 4) {
            Vector v = pad.toVector().subtract(loc.toVector());
            if (v.lengthSquared() > 0.01) {
                v.setY(Math.max(-0.05, Math.min(0.05, v.getY())));
                v.normalize().multiply(0.15);
                le.setVelocity(le.getVelocity().multiply(0.5).add(v));
            }
        }
        // spiders: cancel climb by pushing down
        if ((md.style.equals("spider") || md.style.equals("web")) && le.getVelocity().getY() > 0.1) {
            le.setVelocity(le.getVelocity().setY(-0.1));
        }
    }

    private void clearSessionMobs(Session s) {
        MobDef m = mobs.get(s.mobId);
        if (m == null || center == null) return;
        Location o = origin(m);
        for (LivingEntity le : o.getWorld().getLivingEntities()) {
            if (le instanceof Player) continue;
            if (le.getLocation().distanceSquared(o) < 20 * 20
                    && le.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE))
                le.remove();
        }
    }

    // ---------------- HUD ----------------
    private void startHud(Session s) {
        stopHud(s);
        if (!sessionHud()) return;
        Player p = Bukkit.getPlayer(s.owner);
        String title = color(getConfig().getString("session-hud-title",
                "&6MobFarm &7| &e{mm}:{ss} &7left &8| &f{mob} &8| &ex{stack}"));
        s.hud = Bukkit.createBossBar(formatHud(title, s), BarColor.YELLOW, BarStyle.SEGMENTED_10);
        s.hud.setProgress(1.0);
        s.hud.setVisible(true);
        if (p != null && p.isOnline()) s.hud.addPlayer(p);
        s.hudTask = new BukkitRunnable() {
            @Override public void run() {
                Session live = sessions.get(s.owner);
                if (live == null || live != s) { cancel(); return; }
                if (s.endsAtMs - System.currentTimeMillis() <= 0) { cancel(); return; }
                updateHud(s);
            }
        }.runTaskTimer(this, 10L, 20L);
    }

    private void updateHud(Session s) {
        if (s.hud == null) return;
        long leftMs = Math.max(0, s.endsAtMs - System.currentTimeMillis());
        long total = Math.max(1L, s.totalMs > 0 ? s.totalMs
                : getConfig().getInt("session-minutes", 15) * 60_000L);
        s.hud.setProgress(Math.max(0, Math.min(1, leftMs / (double) total)));
        String title = color(getConfig().getString("session-hud-title",
                "&6MobFarm &7| &e{mm}:{ss} &7left &8| &f{mob} &8| &ex{stack}"));
        s.hud.setTitle(formatHud(title, s));
        Player p = Bukkit.getPlayer(s.owner);
        if (p == null || !p.isOnline()) { s.hud.removeAll(); return; }
        boolean near = inFarmProtect(p.getLocation()) || (center != null
                && p.getWorld() == center.getWorld() && p.getLocation().distanceSquared(center) < 100 * 100);
        if (near) {
            if (!s.hud.getPlayers().contains(p)) s.hud.addPlayer(p);
            s.hud.setVisible(true);
        } else s.hud.removePlayer(p);
    }

    private String formatHud(String tmpl, Session s) {
        long left = Math.max(0, (s.endsAtMs - System.currentTimeMillis()) / 1000L);
        return tmpl.replace("{mm}", String.format("%02d", left / 60))
                .replace("{ss}", String.format("%02d", left % 60))
                .replace("{stack}", String.valueOf(stackCount(s)))
                .replace("{mob}", s.unlocked ? s.mobId : "locked");
    }

    private void stopHud(Session s) {
        if (s.hudTask != null) { try { s.hudTask.cancel(); } catch (Throwable ignored) {} s.hudTask = null; }
        if (s.hud != null) {
            try { s.hud.setVisible(false); s.hud.removeAll(); } catch (Throwable ignored) {}
            s.hud = null;
        }
    }

    private void spawnHubHolo() {
        if (center == null) return;
        World w = center.getWorld();
        Location loc = center.clone().add(0, 3.2, 4);
        for (var e : w.getNearbyEntities(loc, 16, 10, 16)) {
            if (e instanceof TextDisplay td && td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE))
                td.remove();
        }
        String line = ChatColor.GOLD + "" + ChatColor.BOLD + "MOBFARM HUB"
                + "\n" + ChatColor.WHITE + "Community chest ↓"
                + "\n" + ChatColor.YELLOW + communityCoins + "/" + communityTarget
                + "\n" + ChatColor.AQUA + "Entry " + fmt(getConfig().getInt("entry-cost"))
                + " · " + getConfig().getInt("session-minutes") + "m"
                + "\n" + ChatColor.GREEN + "Pick from " + fmt(minPickCost())
                + ChatColor.GRAY + " (shop price/16, doubles)"
                + "\n" + ChatColor.YELLOW + "Extend " + fmt(extendCost()) + "/+"
                + getConfig().getInt("extend-minutes", 15) + "m"
                + "\n" + ChatColor.AQUA + "/mobfarm enter · pick · prices"
                + "\n" + ChatColor.GRAY + "base stack x" + baseSpawners();
        w.spawn(loc, TextDisplay.class, d -> {
            d.setText(line); d.setBillboard(Display.Billboard.CENTER); d.setShadowed(true);
            try { d.setDefaultBackground(false); d.setBackgroundColor(Color.fromARGB(180, 20, 40, 20)); } catch (Throwable ignored) {}
            d.setLineWidth(200);
            var tr = d.getTransformation(); tr.getScale().set(1.2f); d.setTransformation(tr);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(holoKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void refreshBayHolos() {
        for (MobDef m : mobs.values()) spawnBayHolo(m);
    }

    private void spawnBayHolo(MobDef m) {
        if (m.communityChest == null || center == null) return;
        Location loc = m.communityChest.clone().add(0.5, 2.2, 0.5);
        World w = loc.getWorld();
        String name = ChatColor.stripColor(m.display);
        for (var e : w.getNearbyEntities(loc, 3, 4, 3)) {
            if (e instanceof TextDisplay td && td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE)) {
                String t = td.getText();
                if (t != null && t.contains(name)) td.remove();
            }
        }
        String line = ChatColor.GOLD + "" + ChatColor.BOLD + "COMMUNITY"
                + "\n" + m.display
                + "\n" + ChatColor.YELLOW + communityCoins + "/" + communityTarget;
        w.spawn(loc, TextDisplay.class, d -> {
            d.setText(line); d.setBillboard(Display.Billboard.CENTER); d.setShadowed(true);
            try { d.setDefaultBackground(false); d.setBackgroundColor(Color.fromARGB(170, 20, 40, 20)); } catch (Throwable ignored) {}
            d.setLineWidth(140);
            var tr = d.getTransformation(); tr.getScale().set(1.0f); d.setTransformation(tr);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(holoKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    // ---------------- clear / purge ----------------
    private void stopAllFarmActivity(String reason) {
        for (UUID u : new ArrayList<>(sessions.keySet())) endSession(u, false);
        getLogger().info("stopAllFarmActivity: " + reason);
    }

    private int purgeFarmEntities(World w, Location around, double radius) {
        int mobsN = 0, holos = 0;
        for (Entity e : w.getEntities()) {
            if (e.getLocation().distanceSquared(around) > radius * radius) continue;
            if (e instanceof TextDisplay td) {
                boolean ours = td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE)
                        || td.getPersistentDataContainer().has(stackHoloKey, PersistentDataType.BYTE);
                String t = null; try { t = td.getText(); } catch (Throwable ignored) {}
                if (ours || (t != null && (t.contains("STACK") || t.contains("MOBFARM") || t.contains("COMMUNITY")))) {
                    td.remove(); holos++;
                }
                continue;
            }
            if (e instanceof LivingEntity le && !(e instanceof Player)) {
                if (le.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) {
                    le.remove(); mobsN++;
                }
            }
        }
        return mobsN + holos;
    }

    private void clearComplex(Player admin) {
        if (center == null) { admin.sendMessage(ChatColor.RED + "No center."); return; }
        stopAllFarmActivity("clear");
        World w = center.getWorld();
        recomputeAABB();
        int ents = purgeFarmEntities(w, center.clone(), 250);
        int n = wipeBox(w, minX - 5, minY - 5, minZ - 5, maxX + 5, maxY + 5, maxZ + 5);
        ents += purgeFarmEntities(w, center.clone(), 250);
        data.set("built", false); saveData();
        admin.sendMessage(ChatColor.YELLOW + "Cleared " + n + " blocks + " + ents + " entities.");
    }

    private void clearHere(Player admin, int radius) {
        radius = Math.max(8, Math.min(200, radius));
        stopAllFarmActivity("clearhere");
        Location c = admin.getLocation();
        int ents = purgeFarmEntities(c.getWorld(), c, radius);
        int n = wipeBox(c.getWorld(), c.getBlockX() - radius, c.getBlockY() - radius, c.getBlockZ() - radius,
                c.getBlockX() + radius, c.getBlockY() + radius, c.getBlockZ() + radius);
        ents += purgeFarmEntities(c.getWorld(), c, radius);
        data.set("built", false); saveData();
        admin.sendMessage(ChatColor.YELLOW + "clearhere r=" + radius + " → " + n + " blocks, " + ents + " ents.");
    }

    private void purgeOnly(Player admin, int radius) {
        radius = Math.max(8, Math.min(250, radius));
        stopAllFarmActivity("purge");
        int ents = purgeFarmEntities(admin.getWorld(), admin.getLocation(), radius);
        admin.sendMessage(ChatColor.YELLOW + "Purge r=" + radius + ": " + ents);
    }

    private int wipeBox(World w, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int n = 0;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                for (int y = minY; y <= maxY; y++) {
                    Block b = w.getBlockAt(x, y, z);
                    Material t = b.getType();
                    if (t != Material.AIR && t != Material.CAVE_AIR && t != Material.VOID_AIR) {
                        b.setType(Material.AIR, false); n++;
                    }
                }
        return n;
    }

    // ---------------- protection (r=50 around farm) ----------------
    private boolean canBypassProtect(Player p) {
        return p != null && (p.hasPermission("mavomobfarm.bypass.protect")
                || p.hasPermission("mavomobfarm.admin")
                || p.getGameMode() == GameMode.CREATIVE);
    }

    private boolean protectCancel(Player p, Location loc) {
        if (p != null && canBypassProtect(p)) return false;
        return inFarmProtect(loc);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (protectCancel(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "MobFarm is protected.");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPlace(BlockPlaceEvent e) {
        if (protectCancel(e.getPlayer(), e.getBlock().getLocation())) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "MobFarm is protected.");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketEmpty(PlayerBucketEmptyEvent e) {
        if (protectCancel(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBucketFill(PlayerBucketFillEvent e) {
        if (protectCancel(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onIgnite(BlockIgniteEvent e) {
        if (e.getPlayer() != null && protectCancel(e.getPlayer(), e.getBlock().getLocation())) e.setCancelled(true);
        else if (e.getPlayer() == null && inFarmProtect(e.getBlock().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (center != null && inFarmProtect(e.getEntity().getLocation())) {
            e.blockList().clear();
            e.setYield(0f);
        }
        // also strip farm blocks from any explosion list
        e.blockList().removeIf(b -> inFarmProtect(b.getLocation()));
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onInteractProtect(PlayerInteractEvent e) {
        if (e.getClickedBlock() == null) return;
        Material t = e.getClickedBlock().getType();
        // allow chests, barrels, ender chests, signs, buttons used for farm
        if (t == Material.CHEST || t == Material.TRAPPED_CHEST || t == Material.BARREL
                || t.name().contains("SIGN") || t == Material.HOPPER) return;
        if (e.getAction() == org.bukkit.event.block.Action.PHYSICAL) return;
        if (protectCancel(e.getPlayer(), e.getClickedBlock().getLocation())
                && e.getAction().isRightClick()
                && e.getClickedBlock().getType().isInteractable()) {
            // allow nothing else that changes blocks; doors ok? keep locked — only chests
            if (t != Material.CHEST && t != Material.TRAPPED_CHEST && t != Material.BARREL) {
                // still allow opening nothing destructive — cancel lever/door break style
            }
        }
    }

    // ---------------- blacklist wild/portal dumps ----------------
    private boolean canBypassBlacklist(Player p) {
        return p != null && (p.hasPermission("mavomobfarm.bypass.blacklist")
                || p.hasPermission("mavomobfarm.admin"));
    }

    private boolean isFarmTeleportAllowed(Player p) {
        // active enter / session teleport
        return sessions.containsKey(p.getUniqueId()) || pending.containsKey(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTeleportBlacklist(PlayerTeleportEvent e) {
        if (!getConfig().getBoolean("blacklist.enabled", true)) return;
        if (canBypassBlacklist(e.getPlayer())) return;
        Location to = e.getTo();
        if (to == null || !inBlacklist(to)) return;
        PlayerTeleportEvent.TeleportCause c = e.getCause();
        // allow plugin/commands for farm enter, and UNKNOWN from our plugin teleports
        if (c == PlayerTeleportEvent.TeleportCause.PLUGIN
                || c == PlayerTeleportEvent.TeleportCause.COMMAND
                || c == PlayerTeleportEvent.TeleportCause.UNKNOWN) {
            // still block NETHER/END portal style if flagged as PLUGIN from portal — portals use NETHER_PORTAL
            return;
        }
        if (c == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL
                || c == PlayerTeleportEvent.TeleportCause.END_PORTAL
                || c == PlayerTeleportEvent.TeleportCause.END_GATEWAY
                || c == PlayerTeleportEvent.TeleportCause.SPECTATE
                || c == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
                || c == PlayerTeleportEvent.TeleportCause.ENDER_PEARL) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Protected area — walk or /spawn. No portal/pearl dumps.");
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onPortal(PlayerPortalEvent e) {
        if (!getConfig().getBoolean("blacklist.enabled", true)) return;
        if (canBypassBlacklist(e.getPlayer())) return;
        Location to = e.getTo();
        if (to != null && inBlacklist(to)) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Portals cannot drop you into protected MAVOcraft zones.");
        }
        // also if FROM is outside and would create link into blacklist
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent e) {
        // if bed/anchor in blacklist weirdness — leave vanilla; /spawn OK
    }

    // ---------------- combat / farm events ----------------
    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (getConfig().getBoolean("pvp", false)) return;
        if (!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Player)) return;
        if (inFarmProtect(e.getEntity().getLocation()) || inFarmProtect(e.getDamager().getLocation()))
            e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (center == null) return;
        if (!inFarmProtect(e.getLocation())) return;
        LivingEntity ent = e.getEntity();
        // strip jockeys
        try {
            if (ent.getVehicle() != null && ent.getVehicle().getType() == EntityType.CHICKEN) {
                e.setCancelled(true); return;
            }
            if (ent instanceof Ageable age && !age.isAdult()) age.setAdult();
        } catch (Throwable ignored) {}
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent e) {
        if (center != null && inFarmProtect(e.getSpawner().getLocation())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onEnderTeleport(EntityTeleportEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!le.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) return;
        // farm endermen cannot teleport out
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onTarget(EntityTargetLivingEntityEvent e) {
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!le.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) return;
        // passive styles: pad animals shouldn't fight hard — still allow if player hits
        MobDef style = null;
        // keep target only if player is session owner near
        if (e.getTarget() instanceof Player p) {
            Session s = sessions.get(p.getUniqueId());
            if (s == null || !s.unlocked) {
                e.setCancelled(true);
            }
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFarmDamage(EntityDamageByEntityEvent e) {
        if (!creditLastDamager()) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!le.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) return;
        Player p = null;
        if (e.getDamager() instanceof Player pl) p = pl;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player pl) p = pl;
        if (p == null) return;
        le.getPersistentDataContainer().set(lastHitKey, PersistentDataType.STRING, p.getUniqueId().toString());
        // cancel creeper explosion damage to player if blocked
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmCombust(EntityDamageEvent e) {
        if (!sunSafe()) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!le.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.FIRE
                || c == EntityDamageEvent.DamageCause.FIRE_TICK
                || c == EntityDamageEvent.DamageCause.LAVA
                || c == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            e.setCancelled(true);
            try { le.setFireTicks(0); } catch (Throwable ignored) {}
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onCreeperExplode(ExplosionPrimeEvent e) {
        if (e.getEntity() instanceof LivingEntity le
                && le.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) {
            // prevent farm creepers from exploding — still killable by player
            e.setCancelled(true);
            // soften: deal no blast
        }
    }

    /** Safety (2.6.3): farm mobs (and their projectiles) can NEVER hurt a player. */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmAttack(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player)) return;
        LivingEntity mob = null;
        if (e.getDamager() instanceof LivingEntity le) mob = le;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof LivingEntity sh) mob = sh;
        if (mob == null) return;
        if (!mob.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) return;
        e.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (!victim.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) return;
        Player killer = victim.getKiller();
        if (killer == null && creditLastDamager()) {
            String id = victim.getPersistentDataContainer().get(lastHitKey, PersistentDataType.STRING);
            if (id != null) {
                try {
                    Player p = Bukkit.getPlayer(UUID.fromString(id));
                    if (p != null && p.isOnline()) {
                        try { victim.setKiller(p); } catch (Throwable ignored) {}
                        killer = p;
                    }
                } catch (Throwable ignored) {}
            }
        }
        // Achievements listens to getKiller — setKiller above is enough for 1.7.0
        // Soft-call externalProgress if needed
        if (killer != null) {
            tryReflectAchievement(killer, victim.getType());
        }
    }

    private void tryReflectAchievement(Player killer, EntityType type) {
        try {
            org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("MAVOAchievements");
            if (pl == null) return;
            // onKill in achievements handles combat + kill_* when killer set
            // also push external for safety
            String key = "kill_" + type.name().toLowerCase(Locale.ROOT);
            try {
                pl.getClass().getMethod("externalProgress", Player.class, String.class, long.class)
                        .invoke(pl, killer, key, 1L);
            } catch (Throwable ignored) {}
        } catch (Throwable ignored) {}
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PendingEnter pe = pending.remove(e.getPlayer().getUniqueId());
        if (pe != null && pe.task != null) pe.task.cancel();
    }

    /** True when the closed inventory belongs to the hub or a bay community chest. */
    private boolean isCommunityLocation(Location il) {
        if (il == null || center == null || il.getWorld() != center.getWorld()) return false;
        if (Math.abs(il.getBlockX() - center.getBlockX()) <= 1
                && il.getBlockY() == center.getBlockY()
                && Math.abs(il.getBlockZ() - (center.getBlockZ() + 4)) <= 1) return true;
        for (MobDef m : mobs.values()) {
            if (m.communityChest == null) continue;
            if (il.getBlockY() != m.communityChest.getBlockY()) continue;
            if (Math.abs(il.getBlockZ() - m.communityChest.getBlockZ()) > 1) continue;
            if (Math.abs(il.getBlockX() - m.communityChest.getBlockX()) <= 1) return true;
        }
        return false;
    }

    @EventHandler
    public void onCommunityClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p) || center == null) return;
        Inventory top = e.getInventory();
        boolean isCommunity = false;
        Location il = null;
        try { il = top.getLocation(); } catch (Throwable t) { il = null; }
        if (isCommunityLocation(il)) isCommunity = true;
        if (!isCommunity) {
            // double chests: Inventory.getLocation() is unreliable, so resolve the
            // closed inventory from the DoubleChest holder + each half in turn
            try {
                if (top.getHolder() instanceof org.bukkit.block.DoubleChest dc) {
                    try { if (isCommunityLocation(dc.getLocation())) isCommunity = true; } catch (Throwable t) {}
                    if (!isCommunity) {
                        org.bukkit.inventory.InventoryHolder[] sides =
                                { dc.getLeftSide(), dc.getRightSide() };
                        for (org.bukkit.inventory.InventoryHolder h : sides) {
                            if (h == null) continue;
                            try {
                                if (isCommunityLocation(h.getInventory().getLocation())) {
                                    isCommunity = true; break;
                                }
                            } catch (Throwable t) {}
                        }
                    }
                }
            } catch (Throwable t) { /* fall through */ }
        }
        if (!isCommunity) return;
        long value = 0;
        ItemStack[] contents = top.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i];
            if (it == null || it.getType().isAir()) continue;
            value += communityUnitValue(it.getType()) * it.getAmount();
            contents[i] = null;
        }
        if (value <= 0) return;
        top.setContents(contents);
        addCommunityCoins(value, p);
        spawnHubHolo();
        refreshBayHolos();
    }

    private long communityUnitValue(Material mat) {
        return switch (mat) {
            case ROTTEN_FLESH, BONE, STRING, GUNPOWDER, SPIDER_EYE, ARROW, SAND, INK_SAC -> 2L;
            case ENDER_PEARL, BLAZE_ROD, GHAST_TEAR, MAGMA_CREAM, SLIME_BALL, PRISMARINE_SHARD, PHANTOM_MEMBRANE -> 25L;
            case BEEF, PORKCHOP, CHICKEN, MUTTON, LEATHER, WHITE_WOOL, RABBIT, HONEYCOMB,
                 COOKED_BEEF, COOKED_PORKCHOP, COOKED_CHICKEN, COOKED_MUTTON, COOKED_RABBIT -> 5L;
            case IRON_INGOT, GOLD_INGOT, COPPER_INGOT, COAL, EMERALD -> 50L;
            case DIAMOND -> 200L;
            case NETHERITE_INGOT -> 2000L;
            case SPAWNER -> 5000L;
            default -> 1L;
        };
    }

    public boolean isFarmKill(Player p) {
        Session s = sessions.get(p.getUniqueId());
        return s != null && s.active && s.endsAtMs >= System.currentTimeMillis() && s.unlocked;
    }
    public double farmXpScale() { return getConfig().getDouble("profession-xp-scale", 0.30); }

    public void addCommunityCoins(long amount, Player who) {
        if (amount <= 0) return;
        communityCoins += amount;
        while (communityCoins >= communityTarget) {
            communityCoins -= communityTarget;
            communityStack++;
            communityTarget = Math.max(communityTarget * 2, communityTarget + 1);
            Bukkit.broadcastMessage(ChatColor.GOLD + "MobFarm community goal! Base stack now "
                    + ChatColor.YELLOW + "x" + communityStack + ChatColor.GOLD + ". Next: " + communityTarget);
            spawnHubHolo();
            refreshBayHolos();
        }
        saveData();
        if (who != null) who.sendMessage(ChatColor.GREEN + "Community +" + amount
                + " (" + communityCoins + "/" + communityTarget + ")");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> base = new ArrayList<>(List.of("enter", "leave", "hub", "status", "buy", "extend", "pick", "prices", "info"));
            if (sender.hasPermission("mavomobfarm.admin"))
                base.addAll(List.of("tp", "setcenter", "build", "rebuild", "clear", "clearhere", "purge", "reload", "resholo"));
            String pfx = args[0].toLowerCase(Locale.ROOT);
            base.removeIf(s -> !s.startsWith(pfx));
            // /mobfarm <mob> save: offer mob ids too
            if (sender.hasPermission("mavomobfarm.admin"))
                for (String id : mobs.keySet())
                    if (id.startsWith(pfx)) base.add(id);
            return base;
        }
        if (args.length == 2 && sender.hasPermission("mavomobfarm.admin")) {
            if (args[0].equalsIgnoreCase("build") || args[0].equalsIgnoreCase("rebuild")) {
                List<String> ids = new ArrayList<>(mobs.keySet());
                String pfx = args[1].toLowerCase(Locale.ROOT);
                ids.removeIf(s -> !s.startsWith(pfx));
                return ids;
            }
            if (findMob(args[0]) != null && "save".startsWith(args[1].toLowerCase(Locale.ROOT)))
                return List.of("save");
        }
        return List.of();
    }
}
