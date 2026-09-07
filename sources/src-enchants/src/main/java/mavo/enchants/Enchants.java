package mavo.enchants;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.Tag;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** MAVOEnchants 1.0.0 - custom enchant gems (Discord CW#4 idea 15, inspired by EcoEnchants).
 *  Types: VEIN (break ores/logs in a vein), SMELT (ores drop smelted),
 *  XP (bonus XP orbs from kills), LIFESTEAL (heal on kill). Gems are items with a
 *  persistent tag; apply by right-clicking while holding the gem in main hand and
 *  the tool in the offhand. */
public final class Enchants extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';
    private final NamespacedKey gemKey = new NamespacedKey("mavoenchants", "gem");
    private final NamespacedKey toolKey = new NamespacedKey("mavoenchants", "enchants");

    private static final Map<String, String> DESCR = Map.of(
            "VEIN", "Vein Miner - breaks a vein of the same ore/log",
            "SMELT", "Auto Smelt - ores drop smelted",
            "XP", "XP Boost - extra XP orbs from kills",
            "LIFESTEAL", "Lifesteal - heals you on kill");

    private int maxGems = 3, maxTier = 3, veinPerTier = 6, lifestealHearts = 1, xpPct = 50;

    @Override public void onEnable() {
        saveDefaultConfig();
        maxGems = Math.max(1, getConfig().getInt("max-gems-per-tool", 3));
        maxTier = Math.max(1, getConfig().getInt("max-tier", 3));
        veinPerTier = Math.max(1, getConfig().getInt("vein-blocks-per-tier", 6));
        lifestealHearts = Math.max(1, getConfig().getInt("lifesteal-hearts", 1));
        xpPct = Math.max(10, getConfig().getInt("xpboost-percent-per-tier", 50));
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOEnchants v" + getDescription().getVersion() + " enabled - " + DESCR.size() + " enchants.");
    }

    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    // ---------------- gem items ----------------
    public static ItemStack makeGem(String type, int tier) {
        ItemStack it = new ItemStack(Material.EMERALD);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(cc("&bGem: " + friendly(type) + " " + roman(tier)));
        List<String> lore = new ArrayList<>();
        lore.add(cc("&7" + DESCR.getOrDefault(type, "?") + "."));
        lore.add(cc("&7Right-click with the TOOL in your offhand."));
        m.setLore(lore);
        m.getPersistentDataContainer().set(new NamespacedKey("mavoenchants", "gem"), PersistentDataType.STRING,
                type + ":" + tier);
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
                + tool.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + "!");
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1f);
    }

    private static boolean isTool(Material m) {
        String n = m.name();
        return n.endsWith("_PICKAXE") || n.endsWith("_AXE") || n.endsWith("_SHOVEL")
                || n.endsWith("_HOE") || n.endsWith("_SWORD") || n == "SHEARS";
    }

    // ---------------- effects ----------------
    private static boolean isOre(Material m) {
        return Tag.ORES.isTagged(m) || m == Material.ANCIENT_DEBRIS;
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;
        ItemStack tool = p.getInventory().getItemInMainHand();
        int vein = tierOf("VEIN", tool);
        Material mat = e.getBlock().getType();
        if (vein > 0 && (isOre(mat))) {
            veinBreak(e.getBlock(), p, mat, vein);
        }
        if (tierOf("SMELT", tool) > 0 && isOre(mat)) {
            e.setDropItems(false);
            Block b = e.getBlock();
            Material smelted = smeltedOf(mat);
            if (smelted != null) {
                int amount = 1;
                String n = mat.name();
                if (n.endsWith("_ORE") && (n.contains("DIAMOND") || n.contains("EMERALD")
                        || n.contains("REDSTONE") || n.contains("LAPIS"))) amount = 2;
                b.getWorld().dropItemNaturally(b.getLocation(), new ItemStack(smelted, amount));
            }
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
        boolean smelt = tierOf("SMELT", p.getInventory().getItemInMainHand()) > 0;
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
        if (xp > 0) {
            int bonus = (int) Math.max(1, Math.ceil(e.getDroppedExperience() * (xpPct * xp / 100.0)));
            Location l = e.getEntity().getLocation();
            e.getEntity().getWorld().spawn(l, org.bukkit.entity.ExperienceOrb.class, orb -> orb.setExperience(bonus));
        }
        int ls = tierOf("LIFESTEAL", tool);
        if (ls > 0) {
            var attr = p.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH);
            double max = attr != null ? attr.getValue() : 20.0;
            p.setHealth(Math.min(max, p.getHealth() + lifestealHearts * ls * 2));
            p.sendMessage(C + "c\u2764 " + C + "7Lifesteal +" + (lifestealHearts * ls) + " hearts.");
        }
    }

    // ---------------- commands ----------------
    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("list", "gem");
        if (a.length == 3 && a[0].equalsIgnoreCase("gem")) return new ArrayList<>(DESCR.keySet());
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                sender.sendMessage(C + "b\u2726 Custom enchants:");
                for (Map.Entry<String, String> en : DESCR.entrySet())
                    sender.sendMessage(C + "7 - " + CC2(en.getKey()) + C + "7: " + en.getValue());
                sender.sendMessage(C + "8Hold gem in main hand + tool in offhand, right-click to apply.");
            }
            case "gem" -> {
                if (!sender.hasPermission("mavoenchants.admin")) { sender.sendMessage("OP only."); return true; }
                if (args.length < 3) { sender.sendMessage(C + "cUsage: /maenchant gem <player> <type> [tier]"); return true; }
                Player t = Bukkit.getPlayerExact(args[1]);
                if (t == null) { sender.sendMessage(C + "cPlayer offline."); return true; }
                String type = args[2].toUpperCase(Locale.ROOT);
                if (!DESCR.containsKey(type)) { sender.sendMessage(C + "cUnknown enchant: " + args[2]); return true; }
                int tier = args.length >= 4 ? Math.max(1, Integer.parseInt(args[3])) : 1;
                ItemStack gem = makeGem(type, tier);
                var left = t.getInventory().addItem(gem);
                for (ItemStack it : left.values()) t.getWorld().dropItemNaturally(t.getLocation(), it);
                sender.sendMessage(C + "aGiven " + t.getName() + " a " + friendly(type) + " " + roman(tier) + " gem.");
            }
            default -> sender.sendMessage(C + "7/maenchant list | gem <player> <type> [tier] (OP)");
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
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; default -> String.valueOf(n); };
    }
}
