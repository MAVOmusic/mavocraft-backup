package mavo.enchants;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** MAVOEnchants 1.0.0 - custom enchant gems (Discord CW#4 idea 15, inspired by EcoEnchants).
 *  Types: VEIN (break ores in a vein), SMELT (ores drop smelted), XP (bonus XP orbs
 *  from kills), LIFESTEAL (heal on kill). Gems are items with a persistent tag; apply
 *  by right-clicking while holding the gem in main hand and the tool in the offhand.
 *  Hotfix 35: /gemshop + rare ore drops. HOTFIX 37: gem levels go to 10 and every gem
 *  has a CHARGE pool + cooldown per the config table (L1: 10 uses / 10 min ->
 *  L9: 50 / 2 min, L10: unlimited). Cooldown starts on the FIRST use and the pool
 *  refreshes when it ends; charges persist per player in data.yml. */
public final class Enchants extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';
    private final NamespacedKey gemKey = new NamespacedKey("mavoenchants", "gem");
    private final NamespacedKey toolKey = new NamespacedKey("mavoenchants", "enchants");
    private final NamespacedKey buyKey = new NamespacedKey("mavoenchants", "buy");

    private static final Map<String, String> DESCR = new LinkedHashMap<>();
    static {
        DESCR.put("VEIN", "Vein Miner - breaks a vein of the same ore");
        DESCR.put("SMELT", "Auto Smelt - ores drop smelted");
        DESCR.put("XP", "XP Boost - extra XP orbs from kills");
        DESCR.put("LIFESTEAL", "Lifesteal - heals you on kill");
    }

    private int maxGems = 3, maxTier = 10, veinPerTier = 6, lifestealHearts = 1, xpPct = 50, smeltBonus = 10;
    private Economy econ;
    private final Random rnd = new Random();
    private boolean mineEnabled = true;
    private double mineBase = 1.0;              // % chance for tier I
    private int mineLevels = 10;
    private boolean shopEnabled = true;
    private final Map<Integer, Double> shopPrices = new LinkedHashMap<>();
    private final Map<Integer, int[]> chargesCfg = new LinkedHashMap<>();   // tier -> {uses, cooldownMinutes}

    // per-player charge state: type -> pool (tier, uses left, cooldown until)
    private static final class Charge { int tier; int uses; long cdUntil; }
    private final Map<UUID, Map<String, Charge>> charges = new HashMap<>();
    private final Map<UUID, Long> lastMsg = new HashMap<>();
    private File dataFile;
    private YamlConfiguration data;
    private boolean dataDirty = false;

    @Override public void onEnable() {
        saveDefaultConfig();
        mergeMissingDefaults();          // HOTFIX 37: writes gem-charges + shop-prices 5..10 into existing configs
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        maxGems = Math.max(1, getConfig().getInt("max-gems-per-tool", 3));
        int cfgTier = getConfig().getInt("max-tier", 4);
        if (cfgTier < 10) {              // migration: gem levels now go to 10 (charge/cooldown system)
            getLogger().info("max-tier " + cfgTier + " -> 10 (Hotfix 37: gem levels 1-10 with charges).");
            getConfig().set("max-tier", 10);
            saveConfig();
            cfgTier = 10;
        }
        maxTier = cfgTier;
        veinPerTier = Math.max(1, getConfig().getInt("vein-blocks-per-tier", 6));
        lifestealHearts = Math.max(1, getConfig().getInt("lifesteal-hearts", 1));
        xpPct = Math.max(10, getConfig().getInt("xpboost-percent-per-tier", 50));
        smeltBonus = Math.max(0, getConfig().getInt("smelt-bonus-per-tier", 10));   // HOTFIX 40: tier scaling + lore
        mineEnabled = getConfig().getBoolean("mine-gem-enabled", true);
        mineBase = Math.max(0.0, getConfig().getDouble("mine-gem-base-chance", 1.0));
        mineLevels = Math.max(1, getConfig().getInt("mine-gem-levels", 10));
        if (mineLevels < 10) {
            getConfig().set("mine-gem-levels", 10);
            saveConfig();
            mineLevels = 10;
        }
        shopEnabled = getConfig().getBoolean("shop-enabled", true);
        shopPrices.clear();
        ConfigurationSection sp = getConfig().getConfigurationSection("shop-prices");
        if (sp != null) for (String k : sp.getKeys(false))
            try { shopPrices.put(Integer.parseInt(k), Math.max(1, getConfig().getDouble("shop-prices." + k))); }
            catch (Throwable ignored) { }
        chargesCfg.clear();
        ConfigurationSection cc = getConfig().getConfigurationSection("gem-charges");
        if (cc != null) for (String k : cc.getKeys(false)) {
            try {
                int tier = Integer.parseInt(k);
                ConfigurationSection c = cc.getConfigurationSection(k);
                if (c == null) continue;
                chargesCfg.put(tier, new int[]{Math.max(0, c.getInt("uses", 0)),
                        Math.max(0, c.getInt("cooldown-minutes", 0))});
            } catch (Throwable ignored) { }
        }
        loadData();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOEnchants v" + getDescription().getVersion() + " enabled - " + DESCR.size()
                + " enchants, max tier " + maxTier + ", gem shop " + (shopEnabled && econ != null ? "ON" : "off")
                + ", mining drop " + (mineEnabled ? mineBase + "% base" : "off") + ", charge table "
                + chargesCfg.size() + " levels.");
    }

    // ---------------- config merge (writes missing sections into existing config.yml) ----------------
    private void mergeMissingDefaults() {
        File f = new File(getDataFolder(), "config.yml");
        try {
            YamlConfiguration disk = YamlConfiguration.loadConfiguration(f);
            InputStream in = getResource("config.yml");
            if (in == null) return;
            YamlConfiguration def = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            if (mergeMissing(disk, def, "")) {
                try { disk.save(f); }
                catch (Exception ex) { getLogger().warning("could not save config.yml: " + ex.getMessage()); }
            }
            reloadConfig();
        } catch (Throwable t) {
            getLogger().warning("config merge failed: " + t.getMessage());
        }
    }

    private boolean mergeMissing(ConfigurationSection disk, ConfigurationSection def, String prefix) {
        boolean changed = false;
        for (String key : def.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object dv = def.get(path);
            if (dv instanceof ConfigurationSection) {
                if (!disk.isConfigurationSection(path)) {
                    disk.createSection(path);
                    changed = true;
                }
                changed |= mergeMissing(disk, ((ConfigurationSection) dv), path);
            } else if (disk.get(path) == null) {
                disk.set(path, dv);
                changed = true;
            }
        }
        return changed;
    }

    // ---------------- charge data (persisted per player) ----------------
    private void loadData() {
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        charges.clear();
        ConfigurationSection pc = data.getConfigurationSection("charges");
        if (pc == null) return;
        for (String u : pc.getKeys(false)) {
            UUID id;
            try { id = UUID.fromString(u); } catch (Exception ex) { continue; }
            ConfigurationSection tc = pc.getConfigurationSection(u);
            if (tc == null) continue;
            Map<String, Charge> m = new HashMap<>();
            for (String type : tc.getKeys(false)) {
                ConfigurationSection c = tc.getConfigurationSection(type);
                if (c == null) continue;
                Charge ch = new Charge();
                ch.tier = c.getInt("tier", 1);
                ch.uses = c.getInt("uses", 0);
                ch.cdUntil = c.getLong("cooldown", 0);
                m.put(type, ch);
            }
            charges.put(id, m);
        }
    }

    private void saveData() {
        try { data.save(dataFile); } catch (Throwable ignored) { }
    }

    private void queueSave() {
        if (dataDirty) return;
        dataDirty = true;
        Bukkit.getScheduler().runTaskLater(this, () -> { dataDirty = false; saveData(); }, 100L);
    }

    /** Spend one charge of the gem pool. Cooldown starts on the FIRST use of a fresh
     *  pool and the pool refreshes when it ends. Level 10 (uses=0, cd=0) = unlimited. */
    private boolean useCharge(Player p, String type, int tier) {
        int[] c = chargesCfg.get(tier);
        if (c == null || (c[0] == 0 && c[1] == 0)) return true;   // unlimited
        long now = System.currentTimeMillis();
        Map<String, Charge> m = charges.computeIfAbsent(p.getUniqueId(), k -> new HashMap<>());
        Charge ch = m.get(type);
        boolean fresh = false;
        if (ch == null || ch.tier != tier || (ch.cdUntil > 0 && now >= ch.cdUntil)) {
            ch = new Charge();
            ch.tier = tier;
            ch.uses = c[0];
            ch.cdUntil = 0;
            m.put(type, ch);
            fresh = true;
        }
        if (ch.uses <= 0) {
            long left = Math.max(0, ch.cdUntil - now);
            Long last = lastMsg.get(p.getUniqueId());
            if (last == null || now - last > 4000) {   // throttle "recharging" spam
                lastMsg.put(p.getUniqueId(), now);
                p.sendMessage(C + "c" + friendly(type) + " " + roman(tier) + " is recharging"
                        + (left > 0 ? " - " + fmt(left / 1000) + " left" : "") + ".");
            }
            return false;
        }
        if (fresh && c[1] > 0) ch.cdUntil = now + c[1] * 60000L;   // cooldown starts on first use
        ch.uses--;
        data.set("charges." + p.getUniqueId() + "." + type + ".tier", ch.tier);
        data.set("charges." + p.getUniqueId() + "." + type + ".uses", ch.uses);
        data.set("charges." + p.getUniqueId() + "." + type + ".cooldown", ch.cdUntil);
        queueSave();
        return true;
    }

    private String chargesLine(int tier) {
        int[] c = chargesCfg.get(tier);
        if (c == null || (c[0] == 0 && c[1] == 0)) return "Unlimited uses";
        return c[0] + " uses / " + (c[1] > 0 ? c[1] + " min cooldown" : "no cooldown");
    }

    private static String fmt(long s) { return String.format("%d:%02d", s / 60, s % 60); }

    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    // ---------------- gem items ----------------
    private ItemStack makeGem(String type, int tier) {
        ItemStack it = new ItemStack(Material.EMERALD);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(cc("&bGem: " + friendly(type) + " " + roman(tier)));
        List<String> lore = new ArrayList<>();
        lore.add(cc("&7" + DESCR.getOrDefault(type, "?") + "."));
        lore.add(cc("&fEffect: &b" + effectLine(type, tier) + "."));   // HOTFIX 40: what THIS level gives
        lore.add(cc("&7Charges: " + chargesLine(tier) + "."));
        lore.add(cc("&7Right-click with the TOOL in your offhand."));
        m.setLore(lore);
        m.getPersistentDataContainer().set(new NamespacedKey("mavoenchants", "gem"), PersistentDataType.STRING,
                type + ":" + tier);
        it.setItemMeta(m);
        return it;
    }

    /** HOTFIX 40: what the gem ACTUALLY does at this tier - shown on hover in the
     *  shop (and on the gem itself) so higher levels are easy to compare. */
    private String effectLine(String type, int tier) {
        return switch (type.toUpperCase(Locale.ROOT)) {
            case "VEIN" -> "Mining: up to " + (veinPerTier * tier) + " ore blocks per charge";
            case "SMELT" -> "Mining: ores drop smelted with " + (smeltBonus * tier) + "% bonus item chance";
            case "XP" -> "Kills: +" + (xpPct * tier) + "% extra XP orbs per charge";
            case "LIFESTEAL" -> "Kills: heal " + (lifestealHearts * tier) + " heart" + (lifestealHearts * tier == 1 ? "" : "s") + " per charge";
            default -> DESCR.getOrDefault(type, "?");
        };
    }

    private ItemStack shopGem(String type, int tier, double price) {
        ItemStack it = makeGem(type, tier);
        ItemMeta m = it.getItemMeta();
        List<String> lore = new ArrayList<>(m.getLore());
        lore.add("");
        lore.add(cc("&6Price: " + String.format("%,.0f", price) + " coins"));
        lore.add(cc("&7Click to buy."));
        m.setLore(lore);
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        m.getPersistentDataContainer().set(buyKey, PersistentDataType.STRING, type + ":" + tier);
        it.setItemMeta(m);
        return it;
    }

    private static String[] gemOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        String s = it.getItemMeta().getPersistentDataContainer().get(new NamespacedKey("mavoenchants", "gem"), PersistentDataType.STRING);
        return s == null ? null : s.split(":");
    }

    private String enchantsOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return "";
        String s = it.getItemMeta().getPersistentDataContainer().get(toolKey, PersistentDataType.STRING);
        return s == null ? "" : s;
    }

    private void setEnchants(ItemStack it, String val) {
        ItemMeta m = it.getItemMeta();
        if (m == null) return;
        if (val == null || val.isEmpty()) m.getPersistentDataContainer().remove(toolKey);
        else m.getPersistentDataContainer().set(toolKey, PersistentDataType.STRING, val);
        it.setItemMeta(m);
    }

    private int tierOf(String ench, ItemStack tool) {
        for (String part : enchantsOf(tool).split(","))
            if (!part.isEmpty() && part.startsWith(ench + ":")) return Integer.parseInt(part.split(":")[1]);
        return 0;
    }

    @Override public void onDisable() { saveData(); }

    // ---------------- apply ----------------
    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        Player p = e.getPlayer();
        String[] gem = gemOf(p.getInventory().getItemInMainHand());
        if (gem == null) return;
        ItemStack tool = p.getInventory().getItemInOffHand();
        if (tool == null || tool.getType() == Material.AIR || !isTool(tool.getType())) {
            p.sendMessage(C + "cPut the TOOL in your offhand, then right-click with the gem.");
            return;
        }
        e.setCancelled(true);
        String type = gem[0].toUpperCase(Locale.ROOT);
        int tier;
        try { tier = Math.max(1, Math.min(maxTier, Integer.parseInt(gem[1]))); }
        catch (Throwable ex) { p.sendMessage(C + "cThat gem is corrupted."); return; }
        if (!DESCR.containsKey(type)) { p.sendMessage(C + "cThat gem is corrupted."); return; }
        int cur = tierOf(type, tool);
        if (cur >= tier) { p.sendMessage(C + "cThat tool already has " + friendly(type) + " " + roman(cur) + "."); return; }
        int count = enchantsOf(tool).isEmpty() ? 0 : enchantsOf(tool).split(",").length;
        if (count >= maxGems) { p.sendMessage(C + "cTool gem limit reached (" + maxGems + ")."); return; }
        // consume one gem
        ItemStack gemItem = p.getInventory().getItemInMainHand();
        if (gemItem.getAmount() > 1) gemItem.setAmount(gemItem.getAmount() - 1);
        else p.getInventory().setItemInMainHand(null);
        // write enchant
        StringBuilder sb = new StringBuilder();
        for (String part : enchantsOf(tool).split(",")) {
            if (part.isEmpty() || part.startsWith(type + ":")) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(part);
        }
        if (sb.length() > 0) sb.append(',');
        sb.append(type).append(':').append(tier);
        setEnchants(tool, sb.toString());
        p.sendMessage(C + "aApplied " + CC2(type) + " " + roman(tier) + C + "a to your "
                + tool.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + "! ("
                + chargesLine(tier) + ")");
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
    }

    private static boolean isTool(Material m) {
        String n = m.name();
        return n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE") || n.endsWith("_SWORD") || n == "SHEARS";
    }

    // ---------------- effects ----------------
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

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        int vein = tierOf("VEIN", tool);
        int sm = tierOf("SMELT", tool);
        Material mat = e.getBlock().getType();
        if (vein > 0 && isOre(mat) && useCharge(p, "VEIN", vein)) {
            veinBreak(e.getBlock(), p, mat, vein);   // one SMELT charge covers the whole vein
        } else if (sm > 0 && isOre(mat) && useCharge(p, "SMELT", sm)) {
            e.setDropItems(false);
            Block b = e.getBlock();
            Material smelted = smeltedOf(mat);
            if (smelted != null) {
                int amount = 1;
                String n = mat.name();
                if (n.endsWith("_ORE") && (n.contains("DIAMOND") || n.contains("EMERALD")
                        || n.contains("REDSTONE") || n.contains("LAPIS"))) amount = 2;
                if (smeltBonus > 0 && rnd.nextInt(100) < smeltBonus * sm) amount++;   // HOTFIX 40: tier bonus
                b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(smelted, amount));
            }
        }
        tryMineGem(p, e.getBlock());
    }

    /** Mining ores: 1% random gem (tier I), 0.5% tier II, 0.25% tier III, half per level after. */
    private void tryMineGem(Player p, Block b) {
        if (!mineEnabled || !isOre(b.getType())) return;
        double ch = mineBase;
        for (int level = 1; level <= mineLevels; level++) {
            if (rnd.nextDouble() * 100.0 < ch) {
                int tier = Math.min(level, maxTier);
                String type = new ArrayList<>(DESCR.keySet()).get(rnd.nextInt(DESCR.size()));
                ItemStack gem = makeGem(type, tier);
                var left = p.getInventory().addItem(gem);
                for (ItemStack it : left.values()) p.getWorld().dropItemNaturally(b.getLocation(), it);
                p.sendMessage(C + "b\u2726 A " + friendly(type) + " " + roman(tier) + " gem dropped from the ore!");
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
                return;
            }
            ch /= 2.0;
        }
    }

    private static Material smeltedOf(Material m) {
        return switch (m) {
            case IRON_ORE, DEEPSLATE_IRON_ORE, RAW_IRON -> Material.IRON_INGOT;
            case GOLD_ORE, DEEPSLATE_GOLD_ORE, NETHER_GOLD_ORE, RAW_GOLD -> Material.GOLD_INGOT;
            case COPPER_ORE, DEEPSLATE_COPPER_ORE, RAW_COPPER -> Material.COPPER_INGOT;
            case ANCIENT_DEBRIS -> Material.NETHERITE_SCRAP;
            default -> null; // diamonds/emeralds/etc drop themselves
        };
    }

    private void veinBreak(Block start, Player p, Material mat, int tier) {
        Set<Block> visited = new HashSet<>();
        List<Block> queue = new ArrayList<>();
        queue.add(start);
        int limit = veinPerTier * tier;
        int broke = 0;
        boolean smelt = tierOf("SMELT", p.getInventory().getItemInMainHand()) > 0 && useCharge(p, "SMELT", tierOf("SMELT", p.getInventory().getItemInMainHand()));
        for (int i = 0; i < queue.size() && broke < limit; i++) {
            Block b = queue.get(i);
            if (b.getType() != mat || !visited.add(b)) continue;
            if (!b.equals(start)) {
                if (smelt && smeltedOf(mat) != null) {
                    b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(smeltedOf(mat)));
                } else {
                    for (ItemStack it : b.getDrops()) b.getWorld().dropItemNaturally(b.getLocation(), it);
                }
                b.setType(Material.AIR, false);
                broke++;
            }
            for (int dx = -1; dx <= 1; dx++)
                for (int dy = -1; dy <= 1; dy++)
                    for (int dz = -1; dz <= 1; dz++)
                        if (dx != 0 || dy != 0 || dz != 0) {
                            Block nb = b.getRelative(dx, dy, dz);
                            if (nb.getType() == mat) queue.add(nb);
                        }
        }
        if (broke > 0) p.sendMessage(C + "aVein mined " + C + "e" + broke + C + "a blocks.");
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (!(e.getEntity().getKiller() instanceof Player p)) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        int xp = tierOf("XP", tool);
        if (xp > 0 && useCharge(p, "XP", xp)) {
            int bonus = (int) Math.max(1, Math.ceil(e.getDroppedExp() * (xpPct * xp / 100.0)));
            Location l = e.getEntity().getLocation();
            e.getEntity().getWorld().spawn(l, org.bukkit.entity.ExperienceOrb.class, orb -> orb.setExperience(bonus));
        }
        int ls = tierOf("LIFESTEAL", tool);
        if (ls > 0 && useCharge(p, "LIFESTEAL", ls)) {
            var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double max = attr != null ? attr.getValue() : 20.0;
            p.setHealth(Math.min(max, p.getHealth() + lifestealHearts * ls * 2));
            p.sendMessage(C + "c\u2764 " + C + "7Lifesteal +" + (lifestealHearts * ls) + " hearts.");
        }
    }

    // ---------------- gem shop ----------------
    private void openShop(Player p) {
        if (!shopEnabled) { p.sendMessage(C + "cThe gem shop is disabled."); return; }
        if (econ == null) { p.sendMessage(C + "cVault economy not available - the gem shop needs it."); return; }
        Inventory inv = Bukkit.createInventory(null, 54, C + "1\u2726 Gem Shop");
        int slot = 0;
        for (int tier = 1; tier <= maxTier; tier++) {
            double price = shopPrices.getOrDefault(tier, -1.0);
            if (price < 0) continue;
            for (String type : DESCR.keySet()) {
                if (slot >= 44) break;
                inv.setItem(slot++, shopGem(type, tier, price));
            }
        }
        while (slot < 44) inv.setItem(slot++, new ItemStack(Material.GRAY_STAINED_GLASS_PANE));
        ItemStack close = new ItemStack(Material.BOOK);
        ItemMeta cm = close.getItemMeta();
        cm.setDisplayName(C + "eClose");
        close.setItemMeta(cm);
        inv.setItem(45, close);
        ItemStack bal = new ItemStack(Material.GOLD_NUGGET);
        ItemMeta m = bal.getItemMeta();
        m.setDisplayName(C + "6Your balance: " + String.format("%,.0f", econ.getBalance(p)) + " coins");
        bal.setItemMeta(m);
        inv.setItem(49, bal);
        p.openInventory(inv);
    }

    @EventHandler(ignoreCancelled = true)
    public void onShopClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView().getTopInventory().getHolder() != null) return; // only our server-less GUI
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String buy = it.getItemMeta().getPersistentDataContainer().get(buyKey, PersistentDataType.STRING);
        if (buy == null) {
            String nm = it.getItemMeta().getDisplayName();
            if (nm != null && nm.contains("Close")) e.setCancelled(true);
            return;
        }
        e.setCancelled(true);
        String[] parts = buy.split(":");
        if (parts.length < 2 || econ == null) return;
        int tier;
        try { tier = Integer.parseInt(parts[1]); } catch (Throwable ex) { return; }
        double price = shopPrices.getOrDefault(tier, -1.0);
        if (price < 0) return;
        if (!econ.has(p, price)) {
            p.sendMessage(C + "cYou need " + String.format("%,.0f", price) + " coins for that gem.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
            return;
        }
        econ.withdrawPlayer(p, price);
        ItemStack gem = makeGem(parts[0], tier);
        var left = p.getInventory().addItem(gem);
        for (ItemStack l : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), l);
        p.sendMessage(C + "aBought a " + friendly(parts[0]) + " " + roman(tier) + " gem for "
                + String.format("%,.0f", price) + " coins (" + chargesLine(tier) + ").");
        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.1f);
    }

    // ---------------- commands ----------------
    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("list", "gem", "shop", "charges");
        if (a.length == 3 && a[0].equalsIgnoreCase("gem")) return new ArrayList<>(DESCR.keySet());
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("gemshop")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
            openShop(p);
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                sender.sendMessage(C + "b\u2726 Custom enchants:");
                for (Map.Entry<String, String> en : DESCR.entrySet())
                    sender.sendMessage(C + "7 - " + CC2(en.getKey()) + C + "7: " + en.getValue());
                sender.sendMessage(C + "8Hold gem in main hand + tool in offhand, right-click to apply.");
                sender.sendMessage(C + "8Gems: /gemshop (coins) or mine ores (rare drops).");
                sender.sendMessage(C + "8Charges L1-10: 10/5min -> 10/5/10/15/20/25/30/35/50/∞ uses, "
                        + "10->2 min cooldowns (L10 unlimited); see /maenchant charges.");
            }
            case "shop" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
                openShop(p);
            }
            case "charges" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
                Map<String, Charge> m = charges.get(p.getUniqueId());
                boolean any = false;
                for (String type : DESCR.keySet()) {
                    Charge ch = m == null ? null : m.get(type);
                    if (ch == null) continue;
                    any = true;
                    int[] c = chargesCfg.getOrDefault(ch.tier, new int[]{0, 0});
                    long now = System.currentTimeMillis();
                    String state;
                    if (ch.cdUntil > 0 && now < ch.cdUntil)
                        state = C + "c" + ch.uses + "/" + (c[0] > 0 ? c[0] : "\u221e") + " uses - recharging "
                                + fmt((ch.cdUntil - now) / 1000);
                    else
                        state = C + "a" + ch.uses + "/" + (c[0] > 0 ? c[0] : "\u221e") + " uses";
                    sender.sendMessage(C + "7 - " + CC2(type) + " " + roman(ch.tier) + C + "7: " + state);
                }
                if (!any) sender.sendMessage(C + "7No charges used yet - use a gem to start the charge/cooldown.");
            }
            case "gem" -> {
                if (!sender.hasPermission("mavoenchants.admin")) { sender.sendMessage("OP only."); return true; }
                if (args.length < 3) { sender.sendMessage(C + "cUsage: /maenchant gem <player> <type> [tier 1-" + maxTier + "]"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage(C + "cPlayer offline."); return true; }
                String type = args[2].toUpperCase(Locale.ROOT);
                if (!DESCR.containsKey(type)) { sender.sendMessage(C + "cUnknown enchant: " + args[2]); return true; }
                int tier = args.length >= 4 ? Math.max(1, Math.min(maxTier, Integer.parseInt(args[3]))) : 1;
                ItemStack gem = makeGem(type, tier);
                var left = t.getInventory().addItem(gem);
                for (ItemStack it : left.values()) t.getWorld().dropItemNaturally(t.getLocation(), it);
                sender.sendMessage(C + "aGiven " + t.getName() + " a " + friendly(type) + " " + roman(tier)
                        + " gem (" + chargesLine(tier) + ").");
            }
            default -> sender.sendMessage(C + "7/maenchant list | gem <player> <type> [tier] | shop | charges");
        }
        return true;
    }

    private static String friendly(String t) {
        return switch (t.toUpperCase(Locale.ROOT)) {
            case "VEIN" -> "Vein Miner";
            case "SMELT" -> "Auto Smelt";
            case "XP" -> "XP Boost";
            case "LIFESTEAL" -> "Lifesteal";
            default -> t;
        };
    }
    private static String CC2(String t) {
        return switch (t) {
            case "VEIN" -> C + "bVein Miner";
            case "SMELT" -> C + "6Auto Smelt";
            case "XP" -> C + "aXP Boost";
            case "LIFESTEAL" -> C + "cLifesteal";
            default -> C + "e" + t;
        };
    }
    private static String roman(int n) {
        return switch (n) {
            case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V";
            case 6 -> "VI"; case 7 -> "VII"; case 8 -> "VIII"; case 9 -> "IX"; case 10 -> "X";
            default -> String.valueOf(n);
        };
    }
}
