package mavo.professions;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.TreeMap;
import java.util.UUID;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.milkbowl.vault.economy.Economy;
import org.jetbrains.annotations.NotNull;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.block.data.type.Bed;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.block.Block;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.PrepareItemEnchantEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.PrepareAnvilEvent;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.player.PlayerBedEnterEvent;
import org.bukkit.event.player.PlayerBedLeaveEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;

public final class Professions extends JavaPlugin implements Listener, TabCompleter {

    private Economy econ;
    final Map<String, Prof> profs = new LinkedHashMap<>();
    private int overVanillaLevel;
    private double coinChance;
    private double enchantChance;
    private final TreeMap<Integer, Double> chanceTable = new TreeMap<>();
    private int capStep;
    private int xpFlattenLevel;
    private final Map<Integer, List<String>> rankCommands = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;
    private boolean dirty = false;
    private NamespacedKey lockKey, toolKey, ownerKey, arrowKey, placedKey, hitKey, countedKey;
    private final Map<UUID, Map<String, BossBar>> bars = new HashMap<>();
    private final Map<UUID, Map<String, Long>> barHide = new HashMap<>();
    private final Random rng = new Random();
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();
    private static final List<Material> CROPS = Arrays.asList(Material.WHEAT, Material.CARROTS, Material.POTATOES,
            Material.BEETROOTS, Material.NETHER_WART, Material.MELON, Material.PUMPKIN, Material.SUGAR_CANE,
            Material.CACTUS, Material.COCOA, Material.SWEET_BERRY_BUSH, Material.BAMBOO);

    // ------------------ sleep pack 3.15 ------------------
    private static final String SLEEPER = "sleeper";
    private static final char C = '\u00a7';
    private int voteOpenTick, voteCloseTick, voteMinOnline, sleepSkipTick;
    private double voteTurnout;
    private String sleepWorldName;
    private Location tavernBed;
    private boolean tavernSleeperXp, restBonusIncludesSleeper;
    private Object lucky;
    private Method luckyGive;
    /** players currently in a bed in the sleep world (uuid -> bed location) */
    private final Map<UUID, Location> sleepers = new HashMap<>();
    /** last day a player earned sleeper XP (world fullTime/24000 before the skip) */
    private final Map<UUID, Long> lastSleepXpDay = new HashMap<>();
    /** last day a player earned the tavern rest bonus */
    private final Map<UUID, Long> lastTavernDay = new HashMap<>();
    /** tavern bed right-clicks awaiting the paid night skip (player -> millis) */
    private final Map<UUID, Long> pendingTavern = new HashMap<>();
    /** vote state */
    private final Map<UUID, Boolean> votes = new java.util.concurrent.ConcurrentHashMap<>();
    private volatile boolean voteOpen = false;
    private long voteDay = -1;
    /** reentrancy guard while our own Player#sleep() API call is running */
    private final Set<UUID> programSleep = new HashSet<>();
    /** fullTime days we already skipped via our own logic (bed quorum or vote) */
    private final Set<Long> ourSkipDays = new HashSet<>();
    private long lastFullSeen = Long.MIN_VALUE;

    @Override
    public void onEnable() {
        saveDefaultConfig();
        lockKey = new NamespacedKey(this, "proflock");
        toolKey = new NamespacedKey(this, "proftool");
        ownerKey = new NamespacedKey(this, "profowner");
        arrowKey = new NamespacedKey(this, "profarrow");
        placedKey = new NamespacedKey(this, "placed");
        hitKey = new NamespacedKey(this, "arrowhit");
        countedKey = new NamespacedKey(this, "killcounted");
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        // Lucky Coins bridge for Sleeper level rewards (reflection, like MAVODeathChest)
        Plugin lc = getServer().getPluginManager().getPlugin("MAVOLuckyCoins");
        if (lc != null) {
            lucky = lc;
            try { luckyGive = lc.getClass().getMethod("giveCoins", Player.class, int.class); }
            catch (Exception ex) { getLogger().warning("MAVOLuckyCoins API not found: " + ex.getMessage()); }
        }
        loadCfg();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("profession") != null) getCommand("profession").setTabCompleter(this);
        if (getCommand("sleeper") != null) getCommand("sleeper").setTabCompleter(this);
        new BukkitRunnable() {
            public void run() {
                if (dirty) { save(); dirty = false; }
                tickBars();
            }
        }.runTaskTimer(this, 100L, 40L);
        new BukkitRunnable() {
            public void run() { sleepClock(); }
        }.runTaskTimer(this, 60L, 20L);
        if (getServer().getPluginManager().getPlugin("PlaceholderAPI") != null) {
            new ProfExpansion(this).register();
            getLogger().info("PlaceholderAPI expansion registered (%mavoprof_...%).");
        }
        getLogger().info("MAVOProfessions v3.15 enabled: " + profs.size() + " professions"
                + (tavernBed != null ? ", tavern rest hooked at " + tavernBed.getWorld().getName()
                + " " + tavernBed.getBlockX() + " " + tavernBed.getBlockY() + " " + tavernBed.getBlockZ() : ""));
    }

    @Override
    public void onDisable() {
        save();
        for (Map<String, BossBar> m : bars.values())
            for (BossBar b : m.values()) b.removeAll();
    }

    private Enchantment ench(String key) {
        return Registry.ENCHANTMENT.get(NamespacedKey.minecraft(key.toLowerCase(Locale.ROOT)));
    }

    private void loadCfg() {
        profs.clear();
        reloadConfig();
        overVanillaLevel = getConfig().getInt("over-vanilla-level", 50);
        coinChance = getConfig().getDouble("coin-bonus-chance", 0.1);
        enchantChance = getConfig().getDouble("enchant-chance", 0.40);
        capStep = Math.max(1, getConfig().getInt("cap-step", 25));
        xpFlattenLevel = Math.max(1, getConfig().getInt("xp-flatten-level", 250));
        ConfigurationSection slp = getConfig().getConfigurationSection("sleep");
        if (slp != null) {
            voteOpenTick = slp.getInt("vote-open-tick", 12500);       // 18:30
            voteCloseTick = slp.getInt("vote-close-tick", 13500);     // 19:30
            voteMinOnline = Math.max(1, slp.getInt("vote-min-online", 5));
            voteTurnout = Math.max(0.1, Math.min(1.0, slp.getDouble("vote-turnout", 0.75)));
            sleepSkipTick = Math.max(0, Math.min(23999, slp.getInt("skip-to-tick", 6000)));
            sleepWorldName = slp.getString("world", "world");
            tavernSleeperXp = slp.getBoolean("tavern-rest-sleeper-xp", true);
            restBonusIncludesSleeper = slp.getBoolean("rest-bonus-includes-sleeper", false);
            String tb = slp.getString("tavern-bed", "");
            try {
                String[] p = tb.split(",");
                World w = p.length >= 4 && Bukkit.getWorld(p[0]) != null ? Bukkit.getWorld(p[0]) : null;
                tavernBed = w != null ? new Location(w, Double.parseDouble(p[1]), Double.parseDouble(p[2]), Double.parseDouble(p[3])) : null;
            } catch (Exception ex) { tavernBed = null; }
        } else {
            voteOpenTick = 12500; voteCloseTick = 13500; voteMinOnline = 5;
            voteTurnout = 0.75; sleepSkipTick = 6000; sleepWorldName = "world";
            tavernBed = null; tavernSleeperXp = true; restBonusIncludesSleeper = false;
        }
        rankCommands.clear();
        ConfigurationSection rcs = getConfig().getConfigurationSection("rank-commands");
        if (rcs != null) for (String k : rcs.getKeys(false))
            rankCommands.put(Integer.parseInt(k), rcs.getStringList(k));
        chanceTable.clear();
        ConfigurationSection ct = getConfig().getConfigurationSection("enchant-chances");
        if (ct != null) for (String k : ct.getKeys(false))
            chanceTable.put(Integer.parseInt(k), ct.getDouble(k));
        ConfigurationSection sec = getConfig().getConfigurationSection("professions");
        if (sec == null) return;
        for (String id : sec.getKeys(false)) {
            ConfigurationSection c = sec.getConfigurationSection(id);
            if (c == null) continue;
            Prof p = new Prof();
            p.id = id.toLowerCase(Locale.ROOT);
            p.display = ChatColor.translateAlternateColorCodes('&', c.getString("display", id));
            p.action = c.getString("action", "actions");
            p.icon = Material.matchMaterial(c.getString("icon", "CHEST"));
            if (p.icon == null) p.icon = Material.CHEST;
            p.xpBase = Math.max(1.0, c.getDouble("xp-base", 10.0));
            p.xpGrowth = Math.max(1.0, c.getDouble("xp-growth", 1.02));
            p.maxLevel = Math.max(1, c.getInt("max-level", 250));
            p.coinBonus = Math.max(0.0, c.getDouble("coin-bonus", 0.1));
            p.noTool = c.getBoolean("no-tool", false);
            p.sleeper = c.getBoolean("sleeper", false);
            p.perLevelXp = Math.max(0, c.getInt("xp-per-level", 0));
            ConfigurationSection prc = c.getConfigurationSection("rank-commands");
            if (prc != null) for (String k : prc.getKeys(false))
                p.rankCommands.put(Integer.parseInt(k), prc.getStringList(k));
            for (String en : c.getStringList("enchant-pool")) {
                Enchantment e = ench(en);
                if (e != null) p.pool.add(e);
                else getLogger().warning(id + ": unknown enchant " + en);
            }
            ConfigurationSection ts = c.getConfigurationSection("tiers");
            if (ts != null) {
                for (String lv : ts.getKeys(false)) {
                    ConfigurationSection tc = ts.getConfigurationSection(lv);
                    if (tc == null) continue;
                    Tier tier = new Tier();
                    tier.tool = Material.matchMaterial(tc.getString("tool", "STONE_AXE"));
                    if (tier.tool == null) tier.tool = Material.STONE_AXE;
                    tier.note = tc.getString("note", null);
                    tier.name = tc.getString("name", null);
                    for (String es : tc.getStringList("enchants")) {
                        String[] parts = es.split(":");
                        Enchantment e = ench(parts[0]);
                        if (e != null) tier.base.put(e, parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                    }
                    p.tiers.put(Integer.parseInt(lv), tier);
                }
            }
            ConfigurationSection bs = c.getConfigurationSection("branches");
            if (bs != null) {
                for (String bid : bs.getKeys(false)) {
                    ConfigurationSection bc = bs.getConfigurationSection(bid);
                    if (bc == null) continue;
                    Branch b = new Branch();
                    b.id = bid.toLowerCase(Locale.ROOT);
                    b.name = bc.getString("name", bid);
                    String to = bc.getString("tool", null);
                    if (to != null) b.toolOverride = Material.matchMaterial(to);
                    ConfigurationSection tt = bc.getConfigurationSection("tools");
                    if (tt != null) for (String lv : tt.getKeys(false)) {
                        Material tm = Material.matchMaterial(tt.getString(lv, ""));
                        if (tm != null) b.tierTools.put(Integer.parseInt(lv), tm);
                    }
                    for (String ex : bc.getStringList("exclude")) b.exclude.add(ex.toLowerCase(Locale.ROOT));
                    ConfigurationSection bo = bc.getConfigurationSection("bonus");
                    if (bo != null) {
                        for (String lv : bo.getKeys(false)) {
                            Map<Enchantment, Integer> m = new LinkedHashMap<>();
                            for (String es : bo.getStringList(lv)) {
                                String[] parts = es.split(":");
                                Enchantment e = ench(parts[0]);
                                if (e != null) m.put(e, parts.length > 1 ? Integer.parseInt(parts[1]) : 1);
                            }
                            b.bonus.put(Integer.parseInt(lv), m);
                        }
                    }
                    p.branches.put(b.id, b);
                }
            }
            if (p.branches.isEmpty()) {
                Branch main = new Branch();
                main.id = "main";
                main.name = null;
                p.branches.put("main", main);
            }
            profs.put(p.id, p);
        }
    }

    private synchronized void save() {
        try { data.save(dataFile); }
        catch (Exception e) { getLogger().warning("save failed: " + e.getMessage()); }
    }

    // ---------------- data ----------------
    private String path(UUID u, String prof) { return "p." + u + "." + prof; }
    public int level(UUID u, String prof) { return Math.max(1, data.getInt(path(u, prof) + ".level", 1)); }
    double xp(UUID u, String prof) { return data.getDouble(path(u, prof) + ".xp", 0.0); }
    boolean pendingClaim(UUID u, String prof) { return data.getBoolean(path(u, prof) + ".pending", false); }
    boolean started(UUID u, String prof) { return data.getBoolean(path(u, prof) + ".started", false); }
    private void set(UUID u, String prof, String key, Object val) {
        data.set(path(u, prof) + "." + key, val);
        dirty = true;
    }

    private Map<Enchantment, Integer> enchState(UUID u, String prof, String branch) {
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        ConfigurationSection s = data.getConfigurationSection(path(u, prof) + ".ench." + branch);
        if (s != null) for (String k : s.getKeys(false)) {
            Enchantment e = ench(k);
            if (e != null) out.put(e, s.getInt(k));
        }
        return out;
    }

    private void saveEnchState(UUID u, String prof, String branch, Map<Enchantment, Integer> m) {
        data.set(path(u, prof) + ".ench." + branch, null);
        for (Map.Entry<Enchantment, Integer> e : m.entrySet())
            data.set(path(u, prof) + ".ench." + branch + "." + e.getKey().getKey().getKey(), e.getValue());
        dirty = true;
    }

    double xpNeeded(Prof p, int curLevel) {
        // Sleeper-style professions: need = xp-per-level * current level
        // (L1->2 = 50, L2->3 = 100, L3->4 = 150 ...)
        if (p.perLevelXp > 0) return p.perLevelXp * (long) curLevel;
        return Math.ceil(p.xpBase * Math.pow(p.xpGrowth, Math.min(curLevel, xpFlattenLevel)));
    }

    // ---------------- tiers ----------------
    private int tierStart(Prof p, int level) {
        Integer k = p.tiers.floorKey(level);
        return k == null ? p.tiers.firstKey() : k;
    }
    private Tier tierOf(Prof p, int level) {
        Map.Entry<Integer, Tier> e = p.tiers.floorEntry(level);
        return e == null ? p.tiers.firstEntry().getValue() : e.getValue();
    }
    private boolean isTopTier(Prof p, int level) { return p.tiers.higherKey(level) == null; }
    private boolean isThirdTierPlus(Prof p, int level) {
        int idx = 0;
        for (Integer k : p.tiers.keySet()) { if (k <= level) idx++; }
        return idx >= 3;
    }

    private Material toolMat(Prof p, Branch b, int level) {
        Map.Entry<Integer, Material> te = b.tierTools.floorEntry(level);
        if (te != null) return te.getValue();
        return b.toolOverride != null ? b.toolOverride : tierOf(p, level).tool;
    }

    private int enchCap(Prof p, Enchantment en, int level) {
        int vanilla = en.getMaxLevel();
        if (vanilla <= 1) return vanilla;              // mending/silk/infinity etc stay binary
        if (!isThirdTierPlus(p, level)) return vanilla; // stone/iron never exceed vanilla
        if (!isTopTier(p, level)) return vanilla + 1;   // diamond: vanilla+1
        // netherite/top tier: vanilla+2, +1 more per 50 levels past over-vanilla-level
        return vanilla + 2 + Math.max(0, level - overVanillaLevel) / capStep;
    }

    private boolean conflicts(Enchantment cand, Map<Enchantment, Integer> current) {
        for (Enchantment ex : current.keySet()) {
            if (ex.equals(cand)) continue;
            if (cand.conflictsWith(ex) || ex.conflictsWith(cand)) return true;
        }
        return false;
    }

    // tier baseline for a branch at a tier level: tier enchants (minus excluded) + branch bonus
    private Map<Enchantment, Integer> tierBaseline(Prof p, Branch b, int level) {
        Map<Enchantment, Integer> out = new LinkedHashMap<>();
        Tier t = p.tiers.get(level) != null ? p.tiers.get(level) : tierOf(p, level);
        for (Map.Entry<Enchantment, Integer> e : t.base.entrySet())
            if (!b.exclude.contains(e.getKey().getKey().getKey())) out.put(e.getKey(), e.getValue());
        Map<Enchantment, Integer> bonus = b.bonus.get(level);
        if (bonus != null) for (Map.Entry<Enchantment, Integer> e : bonus.entrySet())
            out.merge(e.getKey(), e.getValue(), Math::max);
        // strip anything that conflicts within the baseline itself (config safety)
        List<Enchantment> keys = new ArrayList<>(out.keySet());
        for (Enchantment k : keys) {
            Map<Enchantment, Integer> others = new LinkedHashMap<>(out);
            others.remove(k);
            if (conflicts(k, others) && out.containsKey(k) && others.size() >= out.size() - 1) {
                // keep first occurrence, drop later conflicting ones
            }
        }
        return out;
    }

    double chanceFor(int level) {
        Map.Entry<Integer, Double> e = chanceTable.ceilingEntry(level);
        if (e != null) return e.getValue();
        return chanceTable.isEmpty() ? enchantChance : chanceTable.lastEntry().getValue();
    }

    // splitmix64 finalizer - fixes Java Random's correlated first draws from adjacent seeds
    private static long mix(long z) {
        z ^= z >>> 33; z *= 0xff51afd7ed558ccdL;
        z ^= z >>> 33; z *= 0xc4ceb9fe1a85ec53L;
        z ^= z >>> 33; return z;
    }

    private Map<Enchantment, Integer> ultimateEnchants(Prof p, Branch b, Map<Enchantment, Integer> current) {
        Map<Enchantment, Integer> out = new LinkedHashMap<>(current);
        for (Enchantment en : p.pool) {
            if (b.exclude.contains(en.getKey().getKey())) continue;
            if (out.getOrDefault(en, 0) == 0 && conflicts(en, out)) continue;
            out.put(en, en.getMaxLevel() <= 1 ? 1 : 255);
        }
        Enchantment unb = ench("unbreaking");
        if (unb != null && !b.exclude.contains("unbreaking")) out.put(unb, 255);
        Enchantment mend = ench("mending");
        if (mend != null && !b.exclude.contains("mending") && !conflicts(mend, out)) out.put(mend, 1);
        return out;
    }

    private UpgradeResult simulateUpgrade(UUID u, Prof p, Branch b, int newLevel, Map<Enchantment, Integer> current) {
        UpgradeResult res = new UpgradeResult();
        res.material = toolMat(p, b, newLevel);
        if (newLevel >= p.maxLevel) {
            res.tierChange = true;
            res.ench = ultimateEnchants(p, b, current);
            return res;
        }
        if (newLevel == 250 || newLevel == 420 || newLevel == 666) {
            // prestige set: fill every rollable enchant to its cap at this level
            res.tierChange = true;
            Map<Enchantment, Integer> outm = new LinkedHashMap<>(current);
            for (Enchantment en : p.pool) {
                if (b.exclude.contains(en.getKey().getKey())) continue;
                if (outm.getOrDefault(en, 0) == 0 && conflicts(en, outm)) continue;
                outm.put(en, Math.max(outm.getOrDefault(en, 0), enchCap(p, en, newLevel)));
            }
            res.ench = outm;
            return res;
        }
        if (p.tiers.containsKey(newLevel)) {
            res.tierChange = true;
            Map<Enchantment, Integer> newBase = tierBaseline(p, b, newLevel);
            Integer prevKey = p.tiers.lowerKey(newLevel);
            Map<Enchantment, Integer> prevBase = prevKey == null ? new LinkedHashMap<>() : tierBaseline(p, b, prevKey);
            Map<Enchantment, Integer> merged = new LinkedHashMap<>();
            for (Map.Entry<Enchantment, Integer> e : current.entrySet()) {
                if (b.exclude.contains(e.getKey().getKey().getKey())) continue;
                if (!newBase.containsKey(e.getKey()) && conflicts(e.getKey(), newBase)) continue;
                merged.put(e.getKey(), e.getValue());
            }
            for (Map.Entry<Enchantment, Integer> e : newBase.entrySet()) {
                int delta = Math.max(0, e.getValue() - prevBase.getOrDefault(e.getKey(), 0));
                int cur = merged.getOrDefault(e.getKey(), 0);
                merged.put(e.getKey(), Math.max(e.getValue(), cur + delta));
            }
            res.ench = merged;
            return res;
        }
        res.ench = new LinkedHashMap<>(current);
        Random r = new Random(mix((((long) u.hashCode() * 31L + p.id.hashCode()) * 131L + newLevel) * 17L + b.id.hashCode()));
        if (r.nextDouble() >= chanceFor(newLevel)) return res; // tiered chance: see enchant-chances
        List<Enchantment> order = new ArrayList<>(p.pool);
        java.util.Collections.shuffle(order, r);
        ItemStack probe = new ItemStack(res.material);
        for (Enchantment en : order) {
            if (b.exclude.contains(en.getKey().getKey())) continue;
            if (!en.canEnchantItem(probe) && res.ench.getOrDefault(en, 0) == 0) continue;
            int cur = res.ench.getOrDefault(en, 0);
            if (cur == 0 && conflicts(en, res.ench)) continue; // never roll a conflicting enchant
            int cap = enchCap(p, en, newLevel);
            if (cur < cap) {
                res.ench.put(en, cur + 1);
                res.gained = en;
                res.gainedLevel = cur + 1;
                return res;
            }
        }
        return res; // everything maxed or conflicting
    }

    // ---------------- tool building ----------------
    private boolean isOverVanilla(Map<Enchantment, Integer> ench) {
        for (Map.Entry<Enchantment, Integer> e : ench.entrySet())
            if (e.getValue() > e.getKey().getMaxLevel()) return true;
        return false;
    }

    private ItemStack buildTool(Player pl, Prof p, Branch b, int level, Map<Enchantment, Integer> ench) {
        Material mat = toolMat(p, b, level);
        ItemStack it = new ItemStack(mat);
        ItemMeta meta = it.getItemMeta();
        for (Map.Entry<Enchantment, Integer> e : ench.entrySet())
            meta.addEnchant(e.getKey(), e.getValue(), true);
        boolean over = isOverVanilla(ench);
        boolean ultimate = level >= p.maxLevel;
        int ts = tierStart(p, level);
        Tier tier = tierOf(p, level);
        String tierColor = ts >= 50 ? "&4" : ts >= 25 ? "&b" : ts >= 10 ? "&f" : "&7";
        String tierName = tier != null ? tier.name : null;
        String label = tierName != null ? tierName : b.name != null ? b.name : stripColor(p.display) + " Tool";
        String base = tierColor + stripColor(p.display).split(" ")[0] + " " + label + " &7[Lv " + level + "]";
        if (b.name == null && tierName == null) base = tierColor + stripColor(p.display) + " Tool &7[Lv " + level + "]";
        String name;
        String plainLabel = label;
        if (ultimate) name = cycle("GOD SET", new String[]{"&f", "&e"}) + ChatColor.RESET + " "
                + ChatColor.WHITE + plainLabel + ChatColor.GRAY + " [Lv " + level + "]";
        else if (level >= 666) name = cycle("SATAN SET", new String[]{"&4", "&8"}) + ChatColor.RESET + " "
                + ChatColor.DARK_RED + plainLabel + ChatColor.GRAY + " [Lv " + level + "]";
        else if (level >= 420) name = cycle("YE MAN SET", new String[]{"&a", "&e", "&c"}) + ChatColor.RESET + " "
                + ChatColor.GREEN + plainLabel + ChatColor.GRAY + " [Lv " + level + "]";
        else if (level >= 250) name = ChatColor.translateAlternateColorCodes('&',
                "&6&l\u269C FLEXER SET \u269C &r&6" + plainLabel + " &7[Lv " + level + "]");
        else name = over ? ChatColor.translateAlternateColorCodes('&', "&c\u2605 Masterwork &r" + base)
                : ChatColor.translateAlternateColorCodes('&', base);
        meta.setDisplayName(name);
        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "MAVOcraft Profession Tool");
        lore.add(ChatColor.GRAY + "Bound to " + ChatColor.AQUA + pl.getName());
        lore.add(ChatColor.GRAY + "Only bound tools earn " + p.display + ChatColor.GRAY + " XP");
        lore.add(ChatColor.DARK_GRAY + "Roll caps at this tier:");
        for (Enchantment en : p.pool) {
            if (b.exclude.contains(en.getKey().getKey())) continue;
            int cap = enchCap(p, en, level);
            int cur = ench.getOrDefault(en, 0);
            boolean maxed = cur >= cap;
            lore.add((maxed ? ChatColor.GOLD : ChatColor.DARK_GRAY) + "  " + en.getKey().getKey().replace('_', ' ')
                    + " " + (maxed ? ChatColor.GOLD : ChatColor.GRAY) + cur + ChatColor.DARK_GRAY + "/" + cap
                    + (maxed ? ChatColor.GOLD + " \u2713" : ""));
        }
        if (tier != null && tier.note != null)
            lore.add(ChatColor.translateAlternateColorCodes('&', tier.note));
        if (ultimate) lore.add(ChatColor.GREEN + "\u2738 One with the craft - Level " + p.maxLevel);
        else if (over) lore.add(ChatColor.RED + "\u2605 Beyond vanilla limits");
        lore.add(ChatColor.DARK_RED + "\ud83d\udd12 Too attuned to be enchanted further");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(lockKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, p.id + ":" + b.id);
        meta.getPersistentDataContainer().set(ownerKey, PersistentDataType.STRING, pl.getUniqueId().toString());
        it.setItemMeta(meta);
        return it;
    }

    private boolean isBoundBranch(ItemStack it, String profId, String branchId, UUID owner) {
        if (it == null || !it.hasItemMeta()) return false;
        ItemMeta m = it.getItemMeta();
        String t = m.getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        String o = m.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return (profId + ":" + branchId).equals(t) && owner.toString().equals(o);
    }

    private boolean isBoundAny(ItemStack it, String profId, UUID owner) {
        if (it == null || !it.hasItemMeta()) return false;
        ItemMeta m = it.getItemMeta();
        String t = m.getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        String o = m.getPersistentDataContainer().get(ownerKey, PersistentDataType.STRING);
        return t != null && t.startsWith(profId + ":") && owner.toString().equals(o);
    }

    private boolean isLocked(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(lockKey, PersistentDataType.BYTE);
    }

    private int findBoundSlot(Player pl, String profId, String branchId) {
        PlayerInventory inv = pl.getInventory();
        for (int i = 0; i < inv.getSize(); i++)
            if (isBoundBranch(inv.getItem(i), profId, branchId, pl.getUniqueId())) return i;
        return -1;
    }

    // ---------------- anti-farm: player-placed block tracking ----------------
    private long blockKey(Block b) {
        return ((long) (b.getY() + 512) << 8) | ((long) (b.getX() & 15) << 4) | (b.getZ() & 15);
    }

    private boolean isPlayerPlaced(Block b) {
        long[] arr = b.getChunk().getPersistentDataContainer().get(placedKey, PersistentDataType.LONG_ARRAY);
        if (arr == null) return false;
        long k = blockKey(b);
        for (long v : arr) if (v == k) return true;
        return false;
    }

    private void markPlaced(Block b) {
        var pdc = b.getChunk().getPersistentDataContainer();
        long[] arr = pdc.get(placedKey, PersistentDataType.LONG_ARRAY);
        long k = blockKey(b);
        if (arr == null) { pdc.set(placedKey, PersistentDataType.LONG_ARRAY, new long[]{k}); return; }
        for (long v : arr) if (v == k) return;
        long[] out = java.util.Arrays.copyOf(arr, arr.length + 1);
        out[arr.length] = k;
        pdc.set(placedKey, PersistentDataType.LONG_ARRAY, out);
    }

    private void unmarkPlaced(Block b) {
        var pdc = b.getChunk().getPersistentDataContainer();
        long[] arr = pdc.get(placedKey, PersistentDataType.LONG_ARRAY);
        if (arr == null) return;
        long k = blockKey(b);
        int idx = -1;
        for (int i = 0; i < arr.length; i++) if (arr[i] == k) { idx = i; break; }
        if (idx < 0) return;
        if (arr.length == 1) { pdc.remove(placedKey); return; }
        long[] out = new long[arr.length - 1];
        System.arraycopy(arr, 0, out, 0, idx);
        System.arraycopy(arr, idx + 1, out, idx, arr.length - 1 - idx);
        pdc.set(placedKey, PersistentDataType.LONG_ARRAY, out);
    }

    // never allow placing a bound BLOCK item as a block (gambler sticks are bamboo/rods etc!).
    // Tool USES that change blocks (tilling dirt -> farmland, shovel paths, axe stripping) are
    // NOT placements - a bound hoe must keep tilling next to water (3.14.2 bugfix).
    @EventHandler(priority = EventPriority.LOWEST)
    public void onPlaceBound(BlockPlaceEvent e) {
        if (!isLocked(e.getItemInHand())) return;
        Material hand = e.getItemInHand().getType();
        if (!hand.isBlock()) return; // tools/items can never be "placed" - the event is a use
        e.setCancelled(true);
        e.getPlayer().sendMessage(ChatColor.RED + "That's a bound profession item - you can't place it!");
    }

    // tilling soil with a bound farmer hoe counts toward the Farmer profession
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTill(org.bukkit.event.player.PlayerInteractEvent e) {
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null) return;
        Player pl = e.getPlayer();
        Material bm = e.getClickedBlock().getType();
        boolean soil = bm == Material.DIRT || bm == Material.GRASS_BLOCK
                || bm == Material.COARSE_DIRT || bm == Material.ROOTED_DIRT || bm == Material.PODZOL;
        if (!soil) return;
        if (!isBoundAny(pl.getInventory().getItemInMainHand(), "farmer", pl.getUniqueId())) return;
        if (e.getClickedBlock().getRelative(0, 1, 0).getType() != Material.AIR
                && e.getClickedBlock().getRelative(0, 1, 0).getType() != Material.WATER) return;
        // wait a tick so the farmland actually exists, then credit a little
        getServer().getScheduler().runTask(this, () -> {
            if (e.getClickedBlock().getChunk().isLoaded()
                    && e.getClickedBlock().getType() == Material.FARMLAND)
                addXp(pl, "farmer", 0.5);
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        Material pm = e.getBlockPlaced().getType();
        if (Tag.SAPLINGS.isTagged(pm) || CROPS.contains(pm)) return;
        markPlaced(e.getBlockPlaced());
    }

    // growth creates NEW natural blocks - clear any stale marks at those positions
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onStructureGrow(org.bukkit.event.world.StructureGrowEvent e) {
        for (org.bukkit.block.BlockState st : e.getBlocks()) unmarkPlaced(st.getBlock());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockGrow(org.bukkit.event.block.BlockGrowEvent e) {
        unmarkPlaced(e.getBlock());
    }

    // ---------------- XP ----------------
    /**
     * Public bridge for other MAVO plugins (e.g. MAVOCasino awards gambler XP).
     * Same rules as internal XP: survival only, profession must be started,
     * pending claims block further XP.
     */
    
    /** MobFarm XP scale: when player in active farm session, multiply earned XP. */
    private double farmScale(Player pl) {
        try {
            org.bukkit.plugin.Plugin mf = getServer().getPluginManager().getPlugin("MAVOMobFarm");
            if (mf == null) return 1.0;
            java.lang.reflect.Method is = mf.getClass().getMethod("isFarmKill", Player.class);
            java.lang.reflect.Method sc = mf.getClass().getMethod("farmXpScale");
            Object ok = is.invoke(mf, pl);
            if (ok instanceof Boolean && (Boolean) ok) {
                Object s = sc.invoke(mf);
                if (s instanceof Number) return Math.max(0.0, ((Number) s).doubleValue());
            }
        } catch (Exception ignored) {}
        return 1.0;
    }

    public void externalXp(Player pl, String profId, double amount) {
        if (amount <= 0) return;
        addXp(pl, profId, amount);
    }

    private void addXp(Player pl, String profId, double amount) {
        if (pl.getGameMode() != org.bukkit.GameMode.SURVIVAL) return; // creative never counts
        // MobFarm sessions: scale combat XP (and any XP while in farm)
        if ("hunter".equals(profId) || "archer".equals(profId)) {
            amount = amount * farmScale(pl);
            if (amount <= 0) return;
        }
        Prof p = profs.get(profId);
        if (p == null) return;
        UUID u = pl.getUniqueId();
        if (!started(u, profId)) return;
        int lv = level(u, profId);
        if (lv >= p.maxLevel) return;
        if (pendingClaim(u, profId)) {
            showBar(pl, p, 1.0, ChatColor.GOLD + "" + ChatColor.BOLD + "CLAIM UPGRADE in /profession to continue!");
            return;
        }
        double cur = xp(u, profId) + amount;
        double need = xpNeeded(p, lv);
        if (econ != null && p.coinBonus > 0 && rng.nextDouble() < coinChance)
            econ.depositPlayer(pl, p.coinBonus);
        if (cur >= need) {
            set(u, profId, "xp", need);
            set(u, profId, "pending", true);
            showBar(pl, p, 1.0, p.display + ChatColor.GOLD + " LEVEL " + (lv + 1) + " ready! "
                    + ChatColor.YELLOW + "/profession" + ChatColor.GOLD + " to upgrade your tools");
            pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.2f);
            pl.sendMessage(p.display + ChatColor.GREEN + " level " + (lv + 1) + " complete! "
                    + ChatColor.YELLOW + "Upgrade in /profession to keep earning XP.");
        } else {
            set(u, profId, "xp", cur);
            showBar(pl, p, cur / need, p.display + ChatColor.WHITE + " Lv " + lv + "  "
                    + ChatColor.AQUA + (int) cur + ChatColor.GRAY + "/" + (int) need + " " + p.action);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Material m = e.getBlock().getType();
        Player pl = e.getPlayer();
        if (isPlayerPlaced(e.getBlock())) { unmarkPlaced(e.getBlock()); return; }
        String prof;
        if (Tag.LOGS.isTagged(m)) prof = "lumberjack";
        else if (CROPS.contains(m)) prof = "farmer";
        else if (Tag.MINEABLE_SHOVEL.isTagged(m)) prof = "excavator";
        else if (m != Material.AIR) prof = "miner";
        else return;
        if (isBoundAny(pl.getInventory().getItemInMainHand(), prof, pl.getUniqueId()))
            addXp(pl, prof, 1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onShoot(EntityShootBowEvent e) {
        if (!(e.getEntity() instanceof Player pl)) return;
        if (e.getBow() == null || e.getProjectile() == null) return;
        if (isBoundAny(e.getBow(), "archer", pl.getUniqueId()))
            e.getProjectile().getPersistentDataContainer().set(arrowKey, PersistentDataType.STRING, pl.getUniqueId().toString());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onArrowHit(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player) return;
        if (!(e.getDamager() instanceof Projectile proj)) return;
        String tag = proj.getPersistentDataContainer().get(arrowKey, PersistentDataType.STRING);
        if (tag != null)
            e.getEntity().getPersistentDataContainer().set(hitKey, PersistentDataType.STRING, tag);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        if (e.getEntity() instanceof Player) return;
        var pdc = e.getEntity().getPersistentDataContainer();
        if (pdc.has(countedKey, PersistentDataType.BYTE)) return; // double-count guard
        pdc.set(countedKey, PersistentDataType.BYTE, (byte) 1);
        // archer: ANY tagged arrow hit during the fight counts, regardless of the killing blow
        String tag = pdc.get(hitKey, PersistentDataType.STRING);
        if (tag != null) {
            Player shooter = Bukkit.getPlayer(UUID.fromString(tag));
            if (shooter != null) addXp(shooter, "archer", 1.0);
        }
        // hunter: melee kill holding any bound hunter weapon (sword OR axe)
        Player k = e.getEntity().getKiller();
        if (k != null && isBoundAny(k.getInventory().getItemInMainHand(), "hunter", k.getUniqueId()))
            addXp(k, "hunter", 1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player pl = e.getPlayer();
        if (isBoundAny(pl.getInventory().getItemInMainHand(), "fisherman", pl.getUniqueId())
                || isBoundAny(pl.getInventory().getItemInOffHand(), "fisherman", pl.getUniqueId()))
            addXp(pl, "fisherman", 1.0);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlocked(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player pl)) return;
        if (!pl.isBlocking()) return;
        if (isBoundAny(pl.getInventory().getItemInOffHand(), "guardian", pl.getUniqueId())
                || isBoundAny(pl.getInventory().getItemInMainHand(), "guardian", pl.getUniqueId()))
            addXp(pl, "guardian", 1.0);
    }

    // ---------------- enchant lock ----------------
    @EventHandler
    public void onEnchantPrepare(PrepareItemEnchantEvent e) {
        if (isLocked(e.getItem())) e.setCancelled(true);
    }

    @EventHandler
    public void onAnvil(PrepareAnvilEvent e) {
        if (isLocked(e.getInventory().getItem(0)) || isLocked(e.getInventory().getItem(1)))
            e.setResult(null);
    }

    // ---------------- bossbar / action bar ----------------
    private void showBar(Player pl, Prof p, double progress, String title) {
        UUID u = pl.getUniqueId();
        double prog = Math.max(0, Math.min(1, progress));
        BarColor color = progress >= 1.0 ? BarColor.YELLOW : BarColor.RED;
        BossBar bar = bars.computeIfAbsent(u, k -> new HashMap<>()).get(p.id);
        if (bar == null) {
            bar = Bukkit.createBossBar(title, color, BarStyle.SEGMENTED_10);
            bars.get(u).put(p.id, bar);
        }
        bar.setTitle(title);
        bar.setProgress(prog);
        bar.setColor(color);
        if (!bar.getPlayers().contains(pl)) bar.addPlayer(pl);
        bar.setVisible(true);
        barHide.computeIfAbsent(u, k -> new HashMap<>()).put(p.id, System.currentTimeMillis() + 4000L);
    }

    private void tickBars() {
        long now = System.currentTimeMillis();
        for (Map.Entry<UUID, Map<String, Long>> e : barHide.entrySet()) {
            Map<String, BossBar> pb = bars.get(e.getKey());
            if (pb == null) continue;
            for (Map.Entry<String, Long> t : e.getValue().entrySet()) {
                if (now <= t.getValue()) continue;
                BossBar b = pb.get(t.getKey());
                if (b == null || pendingClaim(e.getKey(), t.getKey())) continue;
                b.setVisible(false);
            }
        }
    }

    // ---------------- menu ----------------
    private void openMenu(Player pl) {
        MenuHolder holder = new MenuHolder();
        Inventory inv = Bukkit.createInventory(holder, 27, ChatColor.DARK_RED + "" + ChatColor.BOLD + "Professions");
        holder.inv = inv;
        ItemStack fill = named(new ItemStack(Material.GRAY_STAINED_GLASS_PANE), " ", null);
        for (int i = 0; i < 27; i++) inv.setItem(i, fill);
        int[] slots = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
        int i = 0;
        UUID u = pl.getUniqueId();
        for (Prof p : profs.values()) {
            int lv = level(u, p.id);
            boolean pending = pendingClaim(u, p.id);
            boolean isStarted = started(u, p.id);
            double cur = xp(u, p.id);
            double need = xpNeeded(p, lv);
            ItemStack it;
            List<String> lore = new ArrayList<>();
            if (!isStarted) {
                it = new ItemStack(p.noTool ? p.icon : toolMat(p, p.branches.values().iterator().next(), 0));
                lore.add(ChatColor.GRAY + "Not started yet. XP from: " + ChatColor.WHITE + p.action);
                lore.add("");
                if (p.noTool) {
                    lore.add(ChatColor.GRAY + "No tools - earn XP by sleeping in a bed at night.");
                    lore.add(ChatColor.GRAY + "Vote skips do NOT count as a successful sleep.");
                    if (p.sleeper) {
                        lore.add("");
                        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "THE ROAD AHEAD");
                        lore.add(ChatColor.YELLOW + " L10 " + ChatColor.WHITE + "\u2764 +1 heart " + ChatColor.DARK_GRAY + "\u2022 " + ChatColor.YELLOW + "+100 \u2b50 Lucky Coins");
                        lore.add(ChatColor.YELLOW + " L20 " + ChatColor.WHITE + "\u2764 +1 heart " + ChatColor.DARK_GRAY + "\u2022 " + ChatColor.YELLOW + "+100 \u2b50 Lucky Coins");
                        lore.add(ChatColor.YELLOW + " ... " + ChatColor.GRAY + "every 10 levels!");
                        lore.add(ChatColor.GOLD + " L100 " + ChatColor.WHITE + "\u2728 SLEEPER RANK \u2728");
                        lore.add("");
                        lore.add(ChatColor.GRAY + "XP per sleep: " + ChatColor.YELLOW + "50" + ChatColor.GRAY + " at L1, then +50 more per level.");
                        lore.add(ChatColor.GRAY + "Tip: " + ChatColor.AQUA + "/sleeper bind " + ChatColor.GRAY + "your own bed for a +2 rested bonus.");
                    }
                } else {
                    if (p.branches.size() > 1) {
                        lore.add(ChatColor.GRAY + "You get " + ChatColor.WHITE + p.branches.size() + " bound tools" + ChatColor.GRAY + ":");
                        for (Branch b : p.branches.values())
                            lore.add(ChatColor.GRAY + " \u2022 " + ChatColor.WHITE + (b.name != null ? b.name
                                    : toolMat(p, b, 0).name().toLowerCase(Locale.ROOT).replace('_', ' ')));
                    } else {
                        lore.add(ChatColor.GRAY + "Free bound tool: " + ChatColor.WHITE
                                + toolMat(p, p.branches.values().iterator().next(), 0).name().toLowerCase(Locale.ROOT).replace('_', ' '));
                    }
                    lore.add(ChatColor.GRAY + "Only bound tools earn " + p.display + ChatColor.GRAY + " XP.");
                    // ---- deep dive: tier road map ----
                    lore.add("");
                    lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "THE ROAD AHEAD");
                    for (Map.Entry<Integer, Tier> te : p.tiers.entrySet()) {
                        StringBuilder ens = new StringBuilder();
                        for (Map.Entry<Enchantment, Integer> en : te.getValue().base.entrySet()) {
                            if (ens.length() > 0) ens.append(ChatColor.DARK_GRAY + ", ");
                            ens.append(ChatColor.AQUA).append(en.getKey().getKey().getKey().replace('_', ' '))
                               .append(" ").append(en.getValue());
                        }
                        lore.add(ChatColor.YELLOW + " L" + te.getKey() + " " + ChatColor.WHITE
                                + te.getValue().tool.name().toLowerCase(Locale.ROOT).replace('_', ' ')
                                + (ens.length() > 0 ? ChatColor.DARK_GRAY + " + " + ens : ""));
                    }
                    lore.add(ChatColor.YELLOW + " L31+" + ChatColor.GRAY + " enchant roll every level-up:");
                    lore.add(ChatColor.GRAY + "   40% to L30, then 35/30/25/20/15%");
                    lore.add(ChatColor.YELLOW + " L50+" + ChatColor.GRAY + " enchants past vanilla caps");
                    lore.add(ChatColor.GRAY + "   (+1 per " + capStep + " levels, e.g. Eff 6, 7, 8...)");
                    lore.add(ChatColor.YELLOW + " L100 " + ChatColor.GRAY + "\u2248 Eff/Sharp +2 over vanilla, Unb V+");
                    lore.add("");
                    lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "PRESTIGE RANKS");
                    lore.add(ChatColor.WHITE + " 250 FLEXER " + ChatColor.DARK_GRAY + "\u2022 " + ChatColor.GREEN + "420 YE MAN");
                    lore.add(ChatColor.RED + " 666 SATAN " + ChatColor.DARK_GRAY + "\u2022 " + ChatColor.GOLD + "999 GOD (255 enchants)");
                    lore.add(ChatColor.GRAY + " Each fills your enchants to cap + LP rank!");
                    lore.add("");
                    lore.add(ChatColor.GRAY + "Coin bonus per action: " + ChatColor.YELLOW + (int) Math.round(p.coinBonus * 100) + "%" + ChatColor.GRAY + " chance");
                }
                lore.add(ChatColor.GRAY + "Max level: " + ChatColor.YELLOW + p.maxLevel);
                lore.add("");
                lore.add(ChatColor.GREEN + "" + ChatColor.BOLD + "CLICK TO START!");
            } else {
                it = new ItemStack(pending ? Material.CHEST : p.icon);
                lore.add(ChatColor.GRAY + "Level: " + ChatColor.YELLOW + lv + ChatColor.GRAY + " / " + p.maxLevel);
                if (lv >= p.maxLevel) {
                    lore.add(ChatColor.GOLD + "\u2605 MAX LEVEL!");
                } else if (pending) {
                    int newLevel = lv + 1;
                    if (p.noTool) {
                        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "SLEEPER LEVEL " + newLevel + " READY!");
                        if (p.sleeper && newLevel % 10 == 0)
                            lore.add(ChatColor.LIGHT_PURPLE + "\u2764 +1 heart  +100 \u2b50 Lucky Coins!");
                        if (p.sleeper && newLevel >= p.maxLevel)
                            lore.add(ChatColor.GOLD + "\u2728 SLEEPER RANK unlocked!");
                        lore.add("");
                        lore.add(ChatColor.GREEN + "CLICK TO CLAIM!");
                        lore.add(ChatColor.GRAY + "(XP is paused until you do)");
                    } else {
                        lore.add(ChatColor.GOLD + "" + ChatColor.BOLD + "TOOL UPGRADE READY - Level " + newLevel);
                        for (Branch b : p.branches.values()) {
                            UpgradeResult sim = simulateUpgrade(u, p, b, newLevel, enchState(u, p.id, b.id));
                            String label = b.name != null ? b.name : "Tool";
                            if (sim.tierChange) {
                                lore.add(ChatColor.AQUA + "\u2B06 " + label + ": NEW TIER "
                                        + ChatColor.WHITE + sim.material.name().toLowerCase(Locale.ROOT).replace('_', ' '));
                                for (Map.Entry<Enchantment, Integer> en : sim.ench.entrySet())
                                    lore.add(ChatColor.AQUA + "    " + en.getKey().getKey().getKey().replace('_', ' ') + " " + en.getValue());
                            } else if (sim.gained != null) {
                                lore.add(ChatColor.GREEN + "\u2728 " + label + ": " + ChatColor.AQUA
                                        + sim.gained.getKey().getKey().replace('_', ' ') + " " + sim.gainedLevel);
                            } else {
                                lore.add(ChatColor.GRAY + "\u2727 " + label + ": no new enchant" + ChatColor.DARK_GRAY + " (" + (int) (chanceFor(newLevel) * 100) + "%)");
                            }
                        }
                        lore.add("");
                        lore.add(ChatColor.GREEN + "CLICK TO UPGRADE! " + ChatColor.GRAY + "(tools repaired too)");
                        lore.add(ChatColor.GRAY + "(XP is paused until you do)");
                    }
                } else {
                    int filled = (int) Math.min(20, cur * 20 / Math.max(1, need));
                    StringBuilder bar = new StringBuilder(ChatColor.DARK_GRAY + "[" + ChatColor.RED);
                    for (int b = 0; b < filled; b++) bar.append("|");
                    bar.append(ChatColor.GRAY);
                    for (int b = filled; b < 20; b++) bar.append("|");
                    bar.append(ChatColor.DARK_GRAY).append("]");
                    lore.add(bar.toString());
                    lore.add(ChatColor.GRAY + "" + (int) cur + " / " + (int) need + " " + p.action);
                    if (p.sleeper) {
                        int next = (lv / 10 + 1) * 10;
                        if (lv < p.maxLevel)
                            lore.add(ChatColor.LIGHT_PURPLE + "L" + next + ": +1 heart, +100 \u2b50");
                        if (p.maxLevel == 100 && lv < p.maxLevel)
                            lore.add(ChatColor.GOLD + "L100: \u2728 SLEEPER RANK");
                    } else {
                        Integer nextTier = p.tiers.higherKey(lv);
                        if (nextTier != null)
                            lore.add(ChatColor.GRAY + "Next tier at level " + ChatColor.YELLOW + nextTier);
                        boolean missing = false;
                        for (Branch b : p.branches.values())
                            if (findBoundSlot(pl, p.id, b.id) == -1) missing = true;
                        if (missing)
                            lore.add(ChatColor.RED + "\u26A0 Tool missing! Click for a replacement.");
                    }
                }
            }
            ItemMeta meta = it.getItemMeta();
            meta.setDisplayName(p.display + (pending ? ChatColor.GOLD + " \ud83d\udce6" : ""));
            meta.setLore(lore);
            it.setItemMeta(meta);
            if (i < slots.length) {
                inv.setItem(slots[i], it);
                holder.profSlots.put(slots[i], p.id);
            }
            i++;
        }
        inv.setItem(22, named(new ItemStack(Material.PLAYER_HEAD), ChatColor.AQUA + pl.getName(),
                Arrays.asList(ChatColor.GRAY + "Leaderboards: " + ChatColor.YELLOW + "/prof top <profession>")));
        pl.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof MenuHolder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player pl)) return;
        String profId = h.profSlots.get(e.getRawSlot());
        if (profId == null) return;
        UUID u = pl.getUniqueId();
        Prof p = profs.get(profId);
        if (p == null) return;

        // -------- start profession: hand out all bound starter tools --------
        if (!started(u, profId)) {
            if (!p.noTool) {
                int free = 0;
                for (ItemStack s : pl.getInventory().getStorageContents()) if (s == null) free++;
                if (free < p.branches.size()) {
                    pl.sendMessage(ChatColor.RED + "Free up " + p.branches.size() + " inventory slot(s) for your starter tools!");
                    pl.playSound(pl.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                for (Branch b : p.branches.values()) {
                    Map<Enchantment, Integer> base = tierBaseline(p, b, p.tiers.firstKey());
                    pl.getInventory().addItem(buildTool(pl, p, b, 0, base));
                    saveEnchState(u, profId, b.id, base);
                }
            }
            set(u, profId, "started", true);
            set(u, profId, "level", 1);
            set(u, profId, "xp", 0);
            set(u, profId, "pending", false);
            pl.playSound(pl.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
            if (p.noTool) {
                pl.sendMessage(p.display + ChatColor.GREEN + " started! " + ChatColor.GRAY
                        + "Sleep in a bed at night to earn XP - vote skips don't count.");
                applySleeperHealth(pl);
            } else {
                pl.sendMessage(p.display + ChatColor.GREEN + " started! " + ChatColor.GRAY
                        + "Use your bound tool" + (p.branches.size() > 1 ? "s" : "") + " to earn XP.");
            }
            openMenu(pl);
            return;
        }

        // -------- lost tool replacement --------
        if (!pendingClaim(u, profId)) {
            boolean gave = false;
            for (Branch b : p.branches.values()) {
                if (findBoundSlot(pl, profId, b.id) != -1) continue;
                if (pl.getInventory().firstEmpty() == -1) {
                    pl.sendMessage(ChatColor.RED + "Free up an inventory slot first!");
                    return;
                }
                pl.getInventory().addItem(buildTool(pl, p, b, level(u, profId), enchState(u, profId, b.id)));
                gave = true;
            }
            if (gave) {
                pl.sendMessage(p.display + ChatColor.YELLOW + " tool(s) replaced. Don't lose them!");
                pl.playSound(pl.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
                openMenu(pl);
            }
            return;
        }

        doClaim(pl, p, profId, true);
    }


    private void doClaim(Player pl, Prof p, String profId, boolean reopen) {
        UUID u = pl.getUniqueId();
        int newLevel = level(u, profId) + 1;
        boolean anyTier = false, anyOver = false;
        List<String> gains = new ArrayList<>();
        if (!p.noTool) {
            // pre-check: every missing tool needs a free slot
            int missing = 0;
            for (Branch b : p.branches.values())
                if (findBoundSlot(pl, profId, b.id) == -1) missing++;
            int free = 0;
            for (ItemStack s : pl.getInventory().getStorageContents()) if (s == null) free++;
            if (missing > free) {
                pl.sendMessage(ChatColor.RED + "Free up " + missing + " inventory slot(s) - some bound tools are missing!");
                pl.playSound(pl.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                return;
            }
            for (Branch b : p.branches.values()) {
                UpgradeResult sim = simulateUpgrade(u, p, b, newLevel, enchState(u, profId, b.id));
                ItemStack upgraded = buildTool(pl, p, b, newLevel, sim.ench);
                int slot = findBoundSlot(pl, profId, b.id);
                if (slot >= 0) pl.getInventory().setItem(slot, upgraded);
                else pl.getInventory().addItem(upgraded);
                saveEnchState(u, profId, b.id, sim.ench);
                String label = b.name != null ? b.name : "tool";
                if (sim.tierChange) anyTier = true;
                if (upgraded.getItemMeta().getDisplayName().contains("Masterwork")) anyOver = true;
                if (sim.gained != null)
                    gains.add(label + " +" + sim.gained.getKey().getKey().replace('_', ' ') + " " + sim.gainedLevel);
            }
        }
        set(u, profId, "level", newLevel);
        set(u, profId, "xp", 0);
        set(u, profId, "pending", false);
        pl.playSound(pl.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
        if (p.noTool) {
            StringBuilder msg = new StringBuilder(p.display + ChatColor.GREEN + " Level " + newLevel);
            if (p.sleeper) {
                if (newLevel % 10 == 0 && newLevel < p.maxLevel)
                    msg.append(ChatColor.LIGHT_PURPLE + "  \u2764 +1 heart, +100 \u2b50 Lucky Coins!");
                if (newLevel >= p.maxLevel)
                    msg.append(ChatColor.GOLD + "  \u2728 SLEEPER RANK!");
            }
            pl.sendMessage(msg.toString());
            if (p.sleeper) sleeperClaimBonus(pl, newLevel);
        } else {
            if (anyTier)
                pl.sendMessage(p.display + ChatColor.GREEN + " Level " + newLevel + ChatColor.GOLD + " NEW TIER unlocked!");
            else if (!gains.isEmpty())
                pl.sendMessage(p.display + ChatColor.GREEN + " Level " + newLevel + " - " + ChatColor.AQUA + String.join(ChatColor.GRAY + ", " + ChatColor.AQUA, gains));
            else
                pl.sendMessage(p.display + ChatColor.GREEN + " Level " + newLevel + ChatColor.GRAY + " - no new enchants this time, tools repaired.");
        }
        if (p.noTool && p.sleeper && newLevel >= p.maxLevel) {
            Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "\u2738 " + ChatColor.AQUA + pl.getName()
                    + ChatColor.GOLD + " unlocked " + ChatColor.BOLD + "SLEEPER RANK" + ChatColor.GOLD + " (100 nights of rest)! \u2738");
        } else if (!p.noTool) {
            String title = newLevel >= p.maxLevel ? "GOD" : newLevel == 666 ? "SATAN SET"
                    : newLevel == 420 ? "YE MAN SET" : newLevel == 250 ? "FLEXER SET" : null;
            if (title != null)
                Bukkit.broadcastMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "\u2738 " + ChatColor.AQUA + pl.getName()
                        + ChatColor.GOLD + " unlocked " + ChatColor.BOLD + title + ChatColor.GOLD + " in "
                        + stripColor(p.display) + ChatColor.GOLD + "! \u2738");
            else if (anyTier || anyOver)
                Bukkit.broadcastMessage(ChatColor.AQUA + pl.getName() + ChatColor.GRAY + " reached "
                        + stripColor(p.display) + ChatColor.GRAY + " Lv " + newLevel
                        + (anyOver ? ChatColor.RED + " \u2605 Masterwork!" : ChatColor.GRAY + " - new tier!"));
        }
        List<String> rc = rankCommands.get(newLevel);
        if (rc != null) for (String c : rc)
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", pl.getName()));
        List<String> prc = p.rankCommands.get(newLevel);
        if (prc != null) for (String c : prc)
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c.replace("%player%", pl.getName()));
        if (reopen) openMenu(pl);
    }

    // ================= SLEEP PACK 3.15: vote, Sleeper, bound bed, rest bonus =================

    private Prof sleeperProf() { return profs.get(SLEEPER); }

    private int onlineCount() { return Bukkit.getOnlinePlayers().size(); }

    /** Bed quorum table: 1->1, 2->2, 3-4->2, 5-7->3, 8-9->4, 10+->5. */
    private int sleepersNeeded(int online) {
        if (online <= 1) return 1;
        if (online == 2) return 2;
        if (online <= 4) return 2;
        if (online <= 7) return 3;
        if (online <= 9) return 4;
        return 5;
    }

    private boolean canSleepNow(World w) {
        int tick = (int) (w.getFullTime() % 24000L);
        return tick >= 12000 || w.isThundering(); // night or thunderstorm
    }

    // ---------- vote clock ----------
    private void sleepClock() {
        World w = Bukkit.getWorld(sleepWorldName);
        if (w == null) return;
        long full = w.getFullTime();
        long day = full / 24000L;
        int tick = (int) (full % 24000L);
        if (lastFullSeen != Long.MIN_VALUE) {
            long diff = full - lastFullSeen;
            // a night skip we didn't perform = MAVOTavern paid rest -> award pending taverner
            if (diff >= 24000L && diff <= 30000L && !ourSkipDays.contains(day) && !ourSkipDays.contains(day - 1)
                    && !pendingTavern.isEmpty()) {
                long now = System.currentTimeMillis();
                for (Map.Entry<UUID, Long> e : new HashMap<>(pendingTavern).entrySet()) {
                    if (now - e.getValue() > 30000L) continue;
                    Player pl = Bukkit.getPlayer(e.getKey());
                    if (pl != null && pl.isOnline()) tavernRestAward(pl, day - 1);
                    pendingTavern.remove(e.getKey());
                }
            }
            if (diff > 0) ourSkipDays.remove(day - 1); // prune old markers
        }
        lastFullSeen = full;
        // reopen every night; close at 19:30
        if (!voteOpen && day != voteDay && tick >= voteOpenTick && tick < voteCloseTick
                && onlineCount() >= voteMinOnline) {
            voteOpen = true;
            voteDay = day;
            votes.clear();
            int online = onlineCount();
            Bukkit.broadcastMessage(C + "6" + C + "l" + C + "a" + " Sleep time! " + C + "7It's " + C + "e18:30"
                    + C + "7 - vote " + C + "a" + C + "l" + C + "e!sleep yes" + C + "7 or " + C + "c" + C + "l!sleep no"
                    + C + "7 to skip to morning. Closes " + C + "e19:30" + C + "7. ("
                    + C + "b" + online + C + "7 online, " + C + "e75%" + C + "7 turnout + more yes wins)");
            for (Player pl : Bukkit.getOnlinePlayers())
                pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.1f);
        } else if (voteOpen && (tick >= voteCloseTick || day != voteDay)) {
            tallyVote(w, day);
        }
        // clear stale pending tavern clicks
        if (!pendingTavern.isEmpty()) {
            long now = System.currentTimeMillis();
            pendingTavern.entrySet().removeIf(e -> now - e.getValue() > 60000L);
        }
    }

    private void tallyVote(World w, long day) {
        voteOpen = false;
        votes.clear();
        int eligible = onlineCount();
        int yes = 0, no = 0, voted = 0;
        for (Boolean v : votesCache()) { voted++; if (v) yes++; else no++; }
        int needed = (int) Math.ceil(eligible * voteTurnout);
        boolean turnout = voted >= needed;
        boolean yesWins = yes > no;
        if (voted == 0) {
            Bukkit.broadcastMessage(C + "8" + C + "lSleep vote closed " + C + "7- no one voted, the night continues.");
            return;
        }
        if (yesWins && turnout) {
            skipNight(w, day, true);
            return;
        }
        Bukkit.broadcastMessage(C + "c" + C + "lSleep vote failed " + C + "7- " + C + "a" + yes + " yes"
                + C + "7 / " + C + "c" + no + " no" + C + "7 (" + voted + "/" + eligible + " voted, "
                + needed + " needed). " + C + "7The night continues - " + C + "6" + sleepersNeeded(eligible)
                + C + "7 players in bed still skips it!");
    }

    /** snapshot votes safely (chat events may be async) */
    private List<Boolean> votesCache() {
        return new ArrayList<>(votes.values());
    }

    // ---------- night skip ----------
    private void skipNight(World w, long day, boolean viaVote) {
        long target = (day + 1) * 24000L + sleepSkipTick;
        w.setFullTime(target);
        ourSkipDays.add(day);
        // any skip ends tonight's vote
        voteOpen = false;
        votes.clear();
        if (!viaVote) {
            long xpDays = day;
            for (Map.Entry<UUID, Location> e : new HashMap<>(sleepers).entrySet()) {
                if (!e.getValue().getWorld().equals(w)) continue;
                Player pl = Bukkit.getPlayer(e.getKey());
                if (pl == null || !pl.isOnline()) continue;
                sleepXp(pl, xpDays);
                if (isOwnBoundBed(pl, e.getValue().getBlock())) {
                    // bound-bed rest: +2 profession points to the other active professions
                    restBonus(pl, 2);
                    pl.sendMessage(C + "d" + "Sleeper bed rest: " + C + "a+2 profession points " + C + "7(rested).");
                }
            }
        }
        for (Map.Entry<UUID, Location> e : new HashMap<>(sleepers).entrySet()) {
            Player pl = Bukkit.getPlayer(e.getKey());
            if (pl != null && pl.isOnline() && pl.getSleepTicks() > 0) {
                try { pl.wakeup(false); } catch (Exception ignore) { }
            }
        }
        sleepers.clear();
        if (!viaVote) {
            Bukkit.broadcastMessage(C + "6" + C + "l\u2600 " + C + "aMorning! " + C + "7Night skipped - bedtime worked.");
        } else {
            Bukkit.broadcastMessage(C + "6" + C + "l\u2600 " + C + "aMorning! " + C + "7The " + C + "e!sleep"
                    + C + "7 vote passed and skipped the night.");
        }
    }

    /** one successful sleep counts toward the Sleeper profession (once per day). */
    private void sleepXp(Player pl, long day) {
        UUID u = pl.getUniqueId();
        if (lastSleepXpDay.getOrDefault(u, -1L) == day) return;
        Prof sp = sleeperProf();
        if (sp == null || !started(u, SLEEPER)) return;
        lastSleepXpDay.put(u, day);
        addXp(pl, SLEEPER, 1);
        pl.playSound(pl.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.6f);
    }

    /** tavern rest or bound-bed rest: +points XP to every OTHER active profession. */
    private void restBonus(Player pl, int points) {
        for (Map.Entry<String, Prof> e : profs.entrySet()) {
            Prof other = e.getValue();
            if (other.noTool && !restBonusIncludesSleeper) continue; // Sleeper has its own sleep XP
            if (!started(pl.getUniqueId(), other.id) || level(pl.getUniqueId(), other.id) >= other.maxLevel) continue;
            addXp(pl, other.id, points);
        }
    }

    private void tavernRestAward(Player pl, long day) {
        UUID u = pl.getUniqueId();
        if (lastTavernDay.getOrDefault(u, -1L) == day) return;
        lastTavernDay.put(u, day);
        restBonus(pl, 1);
        pl.sendMessage(C + "6Tavern rest: " + C + "a+1 profession point " + C + "7to every active profession (rested).");
        if (tavernSleeperXp) sleepXp(pl, day);
        pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.7f);
    }

    // ---------- bed events ----------
    @EventHandler(priority = EventPriority.LOWEST)
    public void onBedEnterLow(PlayerBedEnterEvent e) {
        Player pl = e.getPlayer();
        UUID u = pl.getUniqueId();
        if (programSleep.contains(u)) return; // our own API sleep() re-fire guard
        if (!e.getBed().getWorld().getName().equals(sleepWorldName)) return;
        if (!isOwnBoundBed(pl, e.getBed())) return;
        e.setCancelled(true); // sleep only - no respawn point, no home creation
        World w = e.getBed().getWorld();
        if (!canSleepNow(w)) {
            pl.sendMessage(C + "7You can only rest at night (or in a storm).");
            return;
        }
        programSleep.add(u);
        boolean ok = false;
        try {
            ok = pl.sleep(e.getBed().getLocation(), false);
            if (!ok) pl.sendMessage(C + "cCouldn't get into your bound bed.");
        } catch (Exception ex) {
            pl.sendMessage(C + "cCouldn't get into your bound bed: " + ex.getMessage());
        } finally {
            programSleep.remove(u);
        }
        if (!ok) return;
        sleepers.put(u, e.getBed().getLocation());
        pl.sendMessage(C + "d" + "Sleeper bed: " + C + "7resting - no respawn/home set here.");
        if (onlineCount() >= voteMinOnline) {
            if (voteOpen) {
                votes.put(pl.getUniqueId(), true);
                pl.sendMessage(C + "aYour vote counts as YES (" + C + "e!sleep yes" + C + "a).");
            } else {
                pl.sendMessage(C + "7" + onlineCount() + " players online - a " + C + "e!sleep"
                        + C + "7 vote opens at " + C + "e18:30" + C + "7.");
            }
        }
        checkAutoSkip(w);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onBedEnter(PlayerBedEnterEvent e) {
        if (e.isCancelled()) return;
        if (!e.getBed().getWorld().getName().equals(sleepWorldName)) return;
        sleepers.put(e.getPlayer().getUniqueId(), e.getBed().getLocation());
        checkAutoSkip(e.getBed().getWorld());
    }

    @EventHandler
    public void onBedLeave(PlayerBedLeaveEvent e) {
        sleepers.remove(e.getPlayer().getUniqueId());
    }

    private void checkAutoSkip(World w) {
        if (!canSleepNow(w)) return;
        int online = onlineCount();
        int inBed = 0;
        for (Location l : sleepers.values()) if (l.getWorld().equals(w)) inBed++;
        if (inBed >= sleepersNeeded(online)) {
            skipNight(w, w.getFullTime() / 24000L, false);
        }
    }

    // ---------- bound bed ----------
    private Location boundBed(UUID u) {
        try {
            String w = data.getString("bed." + u + ".world", null);
            if (w == null) return null;
            return new Location(Bukkit.getWorld(w), data.getDouble("bed." + u + ".x"),
                    data.getDouble("bed." + u + ".y"), data.getDouble("bed." + u + ".z"));
        } catch (Exception ex) { return null; }
    }

    private void setBoundBed(UUID u, Location loc) {
        data.set("bed." + u + ".world", loc.getWorld().getName());
        data.set("bed." + u + ".x", loc.getX());
        data.set("bed." + u + ".y", loc.getY());
        data.set("bed." + u + ".z", loc.getZ());
        dirty = true;
    }

    private void clearBoundBed(UUID u) {
        data.set("bed." + u, null);
        dirty = true;
    }

    /** resolve both halves of a bed block. */
    private List<Block> bedParts(Block b) {
        List<Block> out = new ArrayList<>();
        out.add(b);
        if (b.getBlockData() instanceof Bed bed) {
            if (bed.getPart() == Bed.Part.HEAD) out.add(b.getRelative(bed.getFacing().getOppositeFace()));
            else out.add(b.getRelative(bed.getFacing()));
        }
        return out;
    }

    private boolean isOwnBoundBed(Player pl, Block bed) {
        if (pl == null || bed == null) return false;
        Location bound = boundBed(pl.getUniqueId());
        if (bound == null) return false;
        for (Block part : bedParts(bed))
            if (part.getLocation().equals(bound)) return true;
        return false;
    }

    @EventHandler
    public void onBreakBoundBed(BlockBreakEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        Location bound = boundBed(u);
        if (bound == null) return;
        for (Block part : bedParts(e.getBlock()))
            if (part.getLocation().equals(bound)) {
                clearBoundBed(u);
                e.getPlayer().sendMessage(C + "7Your Sleeper bed was broken - bind a new one with " + C + "e/sleeper bind" + C + "7.");
            }
    }

    // ---------- tavern rest ----------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = false)
    public void onTavernBedUse(PlayerInteractEvent e) {
        if (tavernBed == null) return;
        if (e.getAction() != org.bukkit.event.block.Action.RIGHT_CLICK_BLOCK) return;
        if (e.getClickedBlock() == null || !e.getClickedBlock().getType().name().contains("BED")) return;
        for (Block part : bedParts(e.getClickedBlock()))
            if (part.getLocation().equals(tavernBed)) {
                // award only if MAVOTavern actually skips the night (detected by the clock)
                pendingTavern.put(e.getPlayer().getUniqueId(), System.currentTimeMillis());
                return;
            }
    }

    // ---------- chat vote ----------
    @EventHandler(ignoreCancelled = true)
    public void onChatVote(AsyncPlayerChatEvent e) {
        String msg = e.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!msg.startsWith("!sleep")) return;
        UUID u = e.getPlayer().getUniqueId();
        Boolean v = null;
        if (msg.equals("!sleep yes") || msg.endsWith(" yes")) v = true;
        else if (msg.equals("!sleep no") || msg.endsWith(" no")) v = false;
        if (v == null) return; // plain !sleep = info only
        if (!voteOpen) {
            Bukkit.getScheduler().runTask(this, () -> e.getPlayer().sendMessage(
                    C + "7No sleep vote is open right now (votes run 18:30-19:30 with " + voteMinOnline + "+ online)."));
            return;
        }
        votes.put(u, v);
        int yes = 0;
        for (Boolean b : votesCache()) if (b) yes++;
        final int fy = yes;
        final boolean fv = v;
        Bukkit.getScheduler().runTask(this, () -> e.getPlayer().sendMessage(
                C + "aVote recorded " + (fv ? C + "aYES" : C + "cNO") + C + "7 - " + C + "a" + fy + " yes"
                        + C + "7 so far. Winning needs " + C + "e" + (int) Math.ceil(onlineCount() * voteTurnout)
                        + C + "7 votes + more yes than no."));
    }

    // ---------- Sleeper rewards ----------
    private void sleeperClaimBonus(Player pl, int newLevel) {
        applySleeperHealth(pl);
        if (newLevel % 10 == 0) giveLucky(pl, 100); // L10, L20, ... L100
    }

    private void applySleeperHealth(Player pl) {
        UUID u = pl.getUniqueId();
        if (!started(u, SLEEPER)) return;
        int lv = level(u, SLEEPER);
        int hearts = lv / 10; // +1 extra heart per 10 levels
        AttributeInstance ai = pl.getAttribute(Attribute.MAX_HEALTH);
        if (ai != null) ai.setBaseValue(20.0 + hearts * 2.0);
    }

    private void giveLucky(Player pl, int amount) {
        if (lucky == null || luckyGive == null) return;
        try { luckyGive.invoke(lucky, pl, amount); }
        catch (Exception ex) { getLogger().warning("giveCoins failed: " + ex.getMessage()); }
    }

    @EventHandler
    public void onJoinSleeper(PlayerJoinEvent e) {
        applySleeperHealth(e.getPlayer());
    }

    @EventHandler
    public void onQuitSleepState(PlayerQuitEvent e) {
        UUID u = e.getPlayer().getUniqueId();
        sleepers.remove(u);
        votes.remove(u);
        pendingTavern.remove(u);
    }

    // ---------- /sleeper ----------
    private void sleeperStatus(Player pl) {
        Prof sp = sleeperProf();
        if (sp == null) { pl.sendMessage(C + "cSleeper profession is not configured."); return; }
        UUID u = pl.getUniqueId();
        int lv = level(u, SLEEPER);
        double cur = xp(u, SLEEPER);
        double need = xpNeeded(sp, lv);
        boolean started = started(u, SLEEPER);
        Location bed = boundBed(u);
        pl.sendMessage(C + "d" + C + "lSleeper " + C + "7- " + (started ? C + "aLv " + lv + C + "7 (" + (int) cur + "/" + (int) need + " rests"
                + (pendingClaim(u, SLEEPER) ? C + "6, CLAIM READY" : "") + ")" : C + "cnot started") + ".");
        if (started) {
            pl.sendMessage(C + "7Hearts: " + C + "a" + (10 + lv / 10) + C + "7 / " + C + "aLv " + (lv / 10 * 10 + 10)
                    + C + "7 next +1 heart +100 \u2b50");
            if (lv < 100) pl.sendMessage(C + "7Next level: " + C + "y" + (int) need + C + "7 rests from L" + lv + ".");
            else pl.sendMessage(C + "6" + C + "l\u2728 SLEEPER RANK \u2728");
        }
        if (bed == null) pl.sendMessage(C + "7Bound bed: " + C + "cnone" + C + "7 - " + C + "e/sleeper bind " + C + "7looks at a bed.");
        else pl.sendMessage(C + "7Bound bed: " + C + "a" + bed.getBlockX() + ", " + bed.getBlockY() + ", " + bed.getBlockZ()
                + C + "7 - right-click to sleep only (+2 rested).");
    }

    private boolean bindTargetBed(Player pl, boolean bind) {
        if (!started(pl.getUniqueId(), SLEEPER)) {
            pl.sendMessage(C + "cStart the Sleeper profession first in " + C + "e/profession" + C + "c.");
            return true;
        }
        Block target = pl.getTargetBlockExact(5);
        if (target == null || !target.getType().name().contains("BED")) {
            pl.sendMessage(C + "cLook at a bed within 5 blocks.");
            return true;
        }
        if (!canSleepNow(target.getWorld())) {
            pl.sendMessage(C + "7Bind works day or night, but you can only rest at night.");
        }
        if (bind) {
            setBoundBed(pl.getUniqueId(), target.getLocation());
            pl.sendMessage(C + "aBound Sleeper bed! " + C + "7Right-click it to rest - no respawn/home here. "
                    + C + "e/sleeper unbind" + C + "7 to change it.");
            pl.playSound(pl.getLocation(), Sound.BLOCK_ANVIL_USE, 1f, 1.2f);
        } else {
            clearBoundBed(pl.getUniqueId());
            pl.sendMessage(C + "7Sleeper bed unbound.");
        }
        return true;
    }

    private boolean tavernBedSet(Player pl) {
        Block target = pl.getTargetBlockExact(5);
        if (target == null || !target.getType().name().contains("BED")) {
            pl.sendMessage(C + "cLook at the Tavern bed within 5 blocks.");
            return true;
        }
        getConfig().set("sleep.tavern-bed", target.getWorld().getName() + "," + target.getX() + "," + target.getY() + "," + target.getZ());
        saveConfig();
        loadCfg();
        pl.sendMessage(C + "aTavern bed set at " + target.getWorld().getName() + " "
                + target.getX() + " " + target.getY() + " " + target.getZ() + C + "7 - "
                + "Tavern rests will grant +1 profession point.");
        return true;
    }

    // ---------------- commands ----------------
    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("sleeper")) {
            if (!(sender instanceof Player pl)) { sender.sendMessage("In-game only."); return true; }
            if (args.length == 0) { sleeperStatus(pl); return true; }
            switch (args[0].toLowerCase(Locale.ROOT)) {
                case "bind":
                    return bindTargetBed(pl, true);
                case "unbind":
                    return bindTargetBed(pl, false);
                case "status":
                    sleeperStatus(pl);
                    return true;
                case "vote": {
                    if (args.length < 2) { pl.sendMessage(ChatColor.RED + "Usage: /sleeper vote <yes|no>"); return true; }
                    boolean y = args[1].equalsIgnoreCase("yes");
                    if (!voteOpen) {
                        pl.sendMessage(ChatColor.GRAY + "No sleep vote is open right now (votes run 18:30-19:30 with " + voteMinOnline + "+ online).");
                        return true;
                    }
                    votes.put(pl.getUniqueId(), y);
                    pl.sendMessage(ChatColor.GREEN + "Vote recorded " + (y ? ChatColor.GREEN + "YES" : ChatColor.RED + "NO")
                            + ChatColor.GRAY + " - winning needs " + ChatColor.YELLOW
                            + (int) Math.ceil(onlineCount() * voteTurnout) + ChatColor.GRAY + " votes + more yes than no.");
                    return true;
                }
                case "tavernset":
                    if (!pl.hasPermission("mavoprof.admin")) { pl.sendMessage(ChatColor.RED + "No permission."); return true; }
                    return tavernBedSet(pl);
                default:
                    pl.sendMessage(ChatColor.GRAY + "Usage: /sleeper <bind|unbind|status|vote yes|no|tavernset>");
                    return true;
            }
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("reload")) {
            if (!sender.hasPermission("mavoprof.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            save();
            loadCfg();
            sender.sendMessage(ChatColor.GREEN + "MAVOProfessions reloaded.");
            return true;
        }
        if (args.length >= 2 && (args[0].equalsIgnoreCase("almost") || args[0].equalsIgnoreCase("addxp") || args[0].equalsIgnoreCase("maxxp") || args[0].equalsIgnoreCase("claim"))) {
            if (!sender.hasPermission("mavoprof.admin")) {
                sender.sendMessage(ChatColor.RED + "No permission.");
                return true;
            }
            if (!(sender instanceof Player pl)) { sender.sendMessage("In-game only."); return true; }
            Prof p = profs.get(args[1].toLowerCase(Locale.ROOT));
            if (p == null) {
                sender.sendMessage(ChatColor.RED + "Unknown profession. " + String.join(", ", profs.keySet()));
                return true;
            }
            UUID u = pl.getUniqueId();
            if (!started(u, p.id)) { sender.sendMessage(ChatColor.RED + "Start it first in /profession."); return true; }
            if (args[0].equalsIgnoreCase("claim")) {
                if (!pendingClaim(u, p.id)) { sender.sendMessage(ChatColor.RED + "Nothing pending - level up first."); return true; }
                doClaim(pl, p, p.id, false);
                return true;
            }
            if (pendingClaim(u, p.id)) { sender.sendMessage(ChatColor.RED + "Claim the pending level first (/prof claim " + p.id + ")."); return true; }
            int lv = level(u, p.id);
            if (lv >= p.maxLevel) { sender.sendMessage(ChatColor.RED + "Already max level."); return true; }
            double need = xpNeeded(p, lv);
            if (args[0].equalsIgnoreCase("maxxp")) {
                set(u, p.id, "xp", need);
                set(u, p.id, "pending", true);
                sender.sendMessage(ChatColor.GREEN + "Level " + (lv + 1) + " READY - claim with " + ChatColor.YELLOW + "/prof claim " + p.id);
                return true;
            }
            if (args[0].equalsIgnoreCase("almost")) {
                set(u, p.id, "xp", need - 1);
                sender.sendMessage(ChatColor.GREEN + "Set " + p.display + ChatColor.GREEN + " XP to "
                        + ChatColor.YELLOW + (long) (need - 1) + "/" + (long) need
                        + ChatColor.GREEN + " - one action to level " + (lv + 1) + "!");
            } else {
                double amt;
                try { amt = args.length >= 3 ? Double.parseDouble(args[2]) : 1; }
                catch (NumberFormatException ex) { sender.sendMessage(ChatColor.RED + "Bad number."); return true; }
                addXp(pl, p.id, amt);
                sender.sendMessage(ChatColor.GREEN + "Added " + (long) amt + " XP to " + p.display);
            }
            return true;
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("top")) {
            Prof p = profs.get(args[1].toLowerCase(Locale.ROOT));
            if (p == null) {
                sender.sendMessage(ChatColor.RED + "Unknown profession. " + String.join(", ", profs.keySet()));
                return true;
            }
            ConfigurationSection ps = data.getConfigurationSection("p");
            List<Map.Entry<String, Integer>> board = new ArrayList<>();
            if (ps != null) for (String uid : ps.getKeys(false)) {
                int lv = data.getInt("p." + uid + "." + p.id + ".level", 0);
                if (lv > 0) board.add(Map.entry(uid, lv));
            }
            board.sort((a, b) -> b.getValue() - a.getValue());
            sender.sendMessage(ChatColor.DARK_RED + "" + ChatColor.BOLD + "Top " + ChatColor.RESET + p.display);
            int rank = 1;
            for (Map.Entry<String, Integer> en : board.subList(0, Math.min(10, board.size()))) {
                String name = Bukkit.getOfflinePlayer(UUID.fromString(en.getKey())).getName();
                sender.sendMessage(ChatColor.GRAY + "" + rank + ". " + ChatColor.AQUA + (name == null ? "?" : name)
                        + ChatColor.GRAY + " - Lv " + ChatColor.YELLOW + en.getValue());
                rank++;
            }
            if (board.isEmpty()) sender.sendMessage(ChatColor.GRAY + "Nobody yet - get grinding!");
            return true;
        }
        if (!(sender instanceof Player)) {
            sender.sendMessage("Professions: " + String.join(", ", profs.keySet()));
            return true;
        }
        openMenu((Player) sender);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("sleeper")) {
            List<String> out = new ArrayList<>();
            List<String> subs = args.length == 2 && args[0].equalsIgnoreCase("vote")
                    ? Arrays.asList("yes", "no")
                    : sender.hasPermission("mavoprof.admin")
                    ? Arrays.asList("bind", "unbind", "status", "vote", "tavernset")
                    : Arrays.asList("bind", "unbind", "status", "vote");
            String last = args.length == 0 ? "" : args[args.length - 1].toLowerCase(Locale.ROOT);
            for (String s : subs)
                if (s.startsWith(last)) out.add(s);
            return out;
        }
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String s : sender.hasPermission("mavoprof.admin") ? Arrays.asList("check", "top", "almost", "maxxp", "addxp", "claim", "reload") : Arrays.asList("check", "top"))
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("top") || args[0].equalsIgnoreCase("almost") || args[0].equalsIgnoreCase("addxp") || args[0].equalsIgnoreCase("maxxp") || args[0].equalsIgnoreCase("claim"))) {
            List<String> out = new ArrayList<>();
            for (String s : profs.keySet())
                if (s.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(s);
            return out;
        }
        return new ArrayList<>();
    }

    private ItemStack named(ItemStack it, String name, List<String> lore) {
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        if (lore != null) m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private String stripColor(String s) { return ChatColor.stripColor(s); }

    private String cycle(String text, String[] colors) {
        StringBuilder sb = new StringBuilder();
        int ci = 0;
        for (char c : text.toCharArray()) {
            if (c == ' ') { sb.append(' '); continue; }
            sb.append(ChatColor.translateAlternateColorCodes('&', colors[ci % colors.length] + "&l")).append(c);
            ci++;
        }
        return sb.toString();
    }

    // ---------------- classes ----------------
    static final class Prof {
        String id, display, action;
        Material icon;
        double xpBase, xpGrowth, coinBonus;
        int maxLevel;
        boolean noTool;       // Sleeper-style: no bound tools, XP from sleeping
        boolean sleeper;      // gets hearts + Lucky Coins every 10 levels
        int perLevelXp;       // 0 = normal curve; >0 = need = perLevelXp * level
        TreeMap<Integer, Tier> tiers = new TreeMap<>();
        List<Enchantment> pool = new ArrayList<>();
        Map<String, Branch> branches = new LinkedHashMap<>();
        Map<Integer, List<String>> rankCommands = new HashMap<>(); // per-profession, fired on top of global ones
    }

    static final class Tier {
        Material tool;
        String note; // optional flavour line shown on the tool lore (e.g. casino luck %)
        String name; // optional per-tier tool name override (e.g. "Lucky Twig")
        Map<Enchantment, Integer> base = new LinkedHashMap<>();
    }

    static final class Branch {
        String id;
        String name;
        Material toolOverride = null;
        TreeMap<Integer, Material> tierTools = new TreeMap<>();
        Set<String> exclude = new HashSet<>();
        Map<Integer, Map<Enchantment, Integer>> bonus = new HashMap<>();
    }

    static final class UpgradeResult {
        Material material;
        Map<Enchantment, Integer> ench;
        boolean tierChange = false;
        Enchantment gained = null;
        int gainedLevel = 0;
    }

    static final class MenuHolder implements InventoryHolder {
        final Map<Integer, String> profSlots = new HashMap<>();
        Inventory inv;
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    // ---------------- PlaceholderAPI ----------------
    public static class ProfExpansion extends me.clip.placeholderapi.expansion.PlaceholderExpansion {
        private final Professions plugin;
        public ProfExpansion(Professions plugin) { this.plugin = plugin; }
        @Override public @NotNull String getIdentifier() { return "mavoprof"; }
        @Override public @NotNull String getAuthor() { return "MAVO"; }
        @Override public @NotNull String getVersion() { return "3.0.0"; }
        @Override public boolean persist() { return true; }
        @Override
        public String onRequest(OfflinePlayer op, @NotNull String params) {
            if (op == null) return "";
            UUID u = op.getUniqueId();
            String key = params.toLowerCase(Locale.ROOT);
            String profId = key;
            String field = "line";
            int us = key.indexOf('_');
            if (us > 0) { profId = key.substring(0, us); field = key.substring(us + 1); }
            Prof p = plugin.profs.get(profId);
            if (p == null) return null;
            int lv = plugin.level(u, profId);
            double cur = plugin.xp(u, profId);
            double need = plugin.xpNeeded(p, lv);
            boolean pending = plugin.pendingClaim(u, profId);
            boolean started = plugin.started(u, profId);
            switch (field) {
                case "level": return String.valueOf(lv);
                case "xp": return String.valueOf((int) cur);
                case "need": return String.valueOf((int) need);
                case "icon": return p.display;
                default:
                    if (!started) return ChatColor.DARK_GRAY + "not started";
                    if (lv >= p.maxLevel) return ChatColor.GOLD + "Lv " + lv + " \u2605MAX";
                    if (pending) return ChatColor.GOLD + "" + ChatColor.BOLD + "UPGRADE Lv " + (lv + 1) + "!";
                    return ChatColor.WHITE + "Lv " + lv + " " + ChatColor.AQUA + (int) cur
                            + ChatColor.DARK_GRAY + "/" + ChatColor.GRAY + (int) need;
            }
        }
    }
}
