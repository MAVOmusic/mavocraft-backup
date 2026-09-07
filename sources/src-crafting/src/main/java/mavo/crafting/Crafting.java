package mavo.crafting;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.inventory.ShapelessRecipe;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** MAVOCrafting 1.0.0 - custom crafting + NEW-PLAYER RECIPE LIST (Hotfix 35).
 *  Config-driven shaped recipes for otherwise uncraftable items register on enable
 *  (run via a normal crafting table; /crafting list names them).
 *  /craft (hijacked from Essentials workbench) is open to EVERYONE and shows a
 *  paginated list of 100 basic recipes a new character needs - it is a GUIDE only
 *  (HOTFIX 40): clicking a recipe opens a 3x3 preview of the REAL recipe and unlocks
 *  it in the vanilla recipe book (press E); it NEVER crafts or consumes items. */
public final class Crafting extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';
    private final Map<String, NamespacedKey> keys = new LinkedHashMap<>();
    private final Map<String, RecipeDef> beginners = new LinkedHashMap<>();
    private final NamespacedKey recipeKey = new NamespacedKey("mavocrafting", "recipe");
    private final NamespacedKey navKey = new NamespacedKey("mavocrafting", "nav");
    private final Map<UUID, Long> lastCraft = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> openGuis = new ConcurrentHashMap<>();   // HOTFIX 38: scope clicks to OUR /craft menu only

    private record Ingredient(Material mat, int amount) {}
    private record RecipeDef(String id, Material result, int count, List<Ingredient> ingredients) {}

    @Override public void onEnable() {
        saveDefaultConfig();
        mergeMissingDefaults();          // HOTFIX 37: really writes beginner-recipes into existing configs
        loadRecipes();
        loadBeginner();
        getServer().getPluginManager().registerEvents(this, this);
        stealCraftCommand();
        getLogger().info("MAVOCrafting v" + getDescription().getVersion() + " enabled - "
                + keys.size() + " custom recipe(s), " + beginners.size() + " beginner recipe(s).");
    }

    /** copyDefaults(true)+saveConfig() does NOT write missing nested sections into an
     *  already-existing config.yml (that's why /craft showed an empty menu on live).
     *  Merge every key that the bundled resource has but the disk file lacks. */
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

    /** EssentialsX (or another plugin) owns /craft as the op-only workbench alias.
     *  Paper refuses to override an existing command, so: remove the plain "craft"
     *  label from the command map, then register ours - open to everyone, while
     *  /workbench and /e craft keep working for Essentials. */
    private final class CraftCommand extends Command {
        CraftCommand() {
            super("craft");
            setDescription("Open the MAVOcraft beginner recipe list (100 basics)");
            setUsage("/craft [recipe]");
            setPermission(null);
            setPermissionMessage(null);
        }
        @Override public boolean execute(CommandSender sender, String label, String[] args) {
            return onCommand(sender, this, label, args);
        }
        @Override public List<String> tabComplete(CommandSender sender, String alias, String[] args) {
            return Crafting.this.onTabComplete(sender, this, alias, args);
        }
    }

    private void stealCraftCommand() {
        try {
            if (getCommand("craft") != null) return;          // we own it already
            Command pc = new CraftCommand();
            Object pm = getServer().getPluginManager();
            java.lang.reflect.Field f = pm.getClass().getDeclaredField("commandMap");
            f.setAccessible(true);
            if (f.get(pm) instanceof org.bukkit.command.SimpleCommandMap scm) {
                scm.getKnownCommands().remove("craft");
                scm.getKnownCommands().remove("mavocrafting:craft");
                scm.register("mavocrafting", pc);
                getLogger().info("/craft registered (beginner recipe list - replaces the op-only workbench).");
            }
        } catch (Throwable t) {
            getLogger().warning("could not register /craft: " + t.getMessage()
                    + " - use /crafting instead.");
        }
    }

    // ---------------- custom recipes (vanilla-shaped, crafting table) ----------------
    private void loadRecipes() {
        for (NamespacedKey k : keys.values()) Bukkit.removeRecipe(k);
        keys.clear();
        ConfigurationSection cs = getConfig().getConfigurationSection("recipes");
        if (cs == null) return;
        for (String id : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(id);
            if (c == null) continue;
            try {
                List<String> shapeList = c.getStringList("shape");
                if (shapeList.isEmpty() || shapeList.size() > 3) {
                    getLogger().warning("recipe " + id + ": shape must have 1-3 rows - skipped.");
                    continue;
                }
                String[] shape = new String[shapeList.size()];
                for (int i = 0; i < shapeList.size(); i++) {
                    String row = shapeList.get(i);
                    if (row.length() > 3) { getLogger().warning("recipe " + id + ": row too long - skipped."); shape = null; break; }
                    shape[i] = row;
                }
                if (shape == null) continue;
                Material result = Material.matchMaterial(c.getString("result", ""));
                if (result == null || result == Material.AIR) {
                    getLogger().warning("recipe " + id + ": bad result - skipped."); continue;
                }
                int count = Math.max(1, c.getInt("result-count", 1));
                NamespacedKey key = new NamespacedKey(this, id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_"));
                ShapedRecipe r = new ShapedRecipe(key, new ItemStack(result, count));
                r.shape(shape);
                ConfigurationSection ing = c.getConfigurationSection("ingredients");
                if (ing == null) {
                    getLogger().warning("recipe " + id + ": no ingredients - skipped."); continue;
                }
                boolean ok = true;
                for (String ch : ing.getKeys(false)) {
                    Material m = Material.matchMaterial(ing.getString(ch, ""));
                    if (ch.length() != 1 || m == null) { getLogger().warning("recipe " + id + ": bad ingredient " + ch); ok = false; break; }
                    r.setIngredient(ch.charAt(0), m);
                }
                if (!ok) continue;
                Bukkit.addRecipe(r);
                keys.put(id, key);
            } catch (Throwable t) {
                getLogger().warning("recipe " + id + ": " + t.getMessage() + " - skipped.");
            }
        }
    }

    // ---------------- beginner recipe list (the /craft menu) ----------------
    private void loadBeginner() {
        beginners.clear();
        ConfigurationSection cs = getConfig().getConfigurationSection("beginner-recipes");
        if (cs == null) return;
        for (String id : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(id);
            if (c == null) continue;
            try {
                Material result = Material.matchMaterial(c.getString("result", ""));
                if (result == null || result == Material.AIR) continue;
                int count = Math.max(1, c.getInt("count", 1));
                List<Ingredient> ings = new ArrayList<>();
                for (String s : c.getStringList("ingredients")) {
                    String[] parts = s.split(":");
                    Material m = Material.matchMaterial(parts[0].trim());
                    if (m == null) continue;
                    ings.add(new Ingredient(m, Math.max(1, parts.length > 1 ? Integer.parseInt(parts[1].trim()) : 1)));
                }
                if (ings.isEmpty()) continue;
                beginners.put(id.toLowerCase(Locale.ROOT), new RecipeDef(id, result, count, ings));
            } catch (Throwable t) {
                getLogger().warning("beginner recipe " + id + ": " + t.getMessage() + " - skipped.");
            }
        }
    }

    private String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String w : id.split("[_ ]")) {
            if (w.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(w.charAt(0))).append(w.substring(1).toLowerCase(Locale.ROOT));
        }
        return sb.toString();
    }

    private String recipeOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(recipeKey, PersistentDataType.STRING);
    }
    private String navOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(navKey, PersistentDataType.STRING);
    }

    private void openBeginner(Player p, int page) {
        List<RecipeDef> all = new ArrayList<>(beginners.values());
        int perPage = 45, pages = Math.max(1, (all.size() + perPage - 1) / perPage);
        page = Math.max(0, Math.min(pages - 1, page));
        Inventory inv = Bukkit.createInventory(null, 54, C + "1\u2692 Craft - basics (page " + (page + 1) + "/" + pages + ")");

        for (int i = 0; i < perPage; i++) {
            int idx = page * perPage + i;
            if (idx >= all.size()) break;
            RecipeDef r = all.get(idx);
            ItemStack it = new ItemStack(r.result(), r.count());
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(C + "a" + pretty(r.id()));
            List<String> lore = new ArrayList<>();
            for (Ingredient ing : r.ingredients())
                lore.add(C + "7  " + ing.amount() + "x " + pretty2(ing.mat()));
            lore.add(C + "eClick to see how to craft it (guide - no auto craft)");
            m.setLore(lore);
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            m.getPersistentDataContainer().set(recipeKey, PersistentDataType.STRING, r.id().toLowerCase(Locale.ROOT));
            it.setItemMeta(m);
            inv.setItem(i, it);
        }
        if (page > 0) inv.setItem(45, nav(Material.ARROW, "prev", "Previous page"));
        inv.setItem(49, nav(Material.BOOK, "close", "Close"));
        if (page < pages - 1) inv.setItem(53, nav(Material.ARROW, "next", "Next page"));
        openGuis.put(p.getUniqueId(), inv);   // HOTFIX 38: remember which inventory is ours
        p.openInventory(inv);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p)) return;
        Inventory gui = openGuis.get(p.getUniqueId());
        if (gui != null && gui.equals(e.getInventory())) openGuis.remove(p.getUniqueId());
    }

    private ItemStack nav(Material mat, String nav, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(C + "e" + name);
        m.getPersistentDataContainer().set(navKey, PersistentDataType.STRING, nav);
        it.setItemMeta(m);
        return it;
    }

    private static String pretty2(Material m) {
        String n = m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return String.valueOf(Character.toUpperCase(n.charAt(0))) + n.substring(1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        // HOTFIX 38: only touch clicks while OUR /craft beginner menu is the open
        // inventory. The old check was always true (same view), so the shift-click
        // cancel below was killing shift transfers in EVERY container (chests,
        // furnaces, barrels ...) server-wide. Chests/furnaces are untouched now.
        Inventory gui = openGuis.get(p.getUniqueId());
        if (gui == null || !gui.equals(e.getView().getTopInventory())) return;
        if (e.getClick().isShiftClick()) e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String nav = navOf(it);
        if (nav != null) {
            e.setCancelled(true);
            if (nav.startsWith("back:")) openBeginner(p, Integer.parseInt(nav.substring(5)));
            else if (nav.equals("prev")) openBeginner(p, pageOf(e.getView().getTitle()) - 2);
            else if (nav.equals("next")) openBeginner(p, pageOf(e.getView().getTitle()));
            else if (nav.equals("close")) p.closeInventory();
            return;
        }
        // HOTFIX 40: the recipe preview is display-only - block clicks on its icons
        if (e.getView().getTitle().contains(" - how to craft")) { e.setCancelled(true); return; }
        String id = recipeOf(it);
        if (id == null) return;
        e.setCancelled(true);
        RecipeDef r = beginners.get(id);
        if (r == null) return;
        Long last = lastCraft.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < 300) return;   // debounce double clicks
        lastCraft.put(p.getUniqueId(), now);
        openRecipe(p, r, pageOf(e.getView().getTitle()));   // HOTFIX 40: GUIDE, no auto-craft
        p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
    }

    private static int pageOf(String title) {
        try { return Integer.parseInt(title.replaceAll(".*page (\\d+)/.*", "$1")); }
        catch (Throwable t) { return 0; }
    }

    /** HOTFIX 40: GUIDE mode. Clicking a recipe in /craft opens a 3x3 preview of
     *  the REAL recipe (looked up from the server's recipe registry - same layout
     *  as the vanilla crafting grid) and unlocks it in the player's recipe book
     *  (press E). Nothing is crafted and nothing is consumed. */
    private void openRecipe(Player p, RecipeDef r, int fromPage) {
        Inventory inv = Bukkit.createInventory(null, 45, C + "1\u2692 " + pretty(r.id()) + " - how to craft");
        ItemStack[] grid = new ItemStack[9];
        Recipe found = null;
        try {
            for (Recipe rc : Bukkit.getRecipesFor(new ItemStack(r.result()))) {
                if (rc instanceof ShapedRecipe sr) {          // exact 3x3 pattern
                    found = rc;
                    String[] shape = sr.getShape();
                    for (int row = 0; row < shape.length && row < 3; row++) {
                        String s = shape[row];
                        for (int col = 0; col < s.length() && col < 3; col++) {
                            char ch = s.charAt(col);
                            if (ch == ' ') continue;
                            ItemStack ing = sr.getIngredientMap().get(ch);
                            if (ing != null) grid[row * 3 + col] = ing.clone();
                        }
                    }
                    break;
                } else if (rc instanceof ShapelessRecipe sl) { // any order
                    found = rc;
                    int i = 0;
                    for (ItemStack ing : sl.getIngredientList())
                        if (i < 9) grid[i++] = ing.clone();
                    break;
                }
            }
        } catch (Throwable ignored) { }
        if (found != null) {
            try {   // unlock in the vanilla recipe book (press E to see it)
                for (Recipe rc : Bukkit.getRecipesFor(new ItemStack(r.result())))
                    p.discoverRecipe(rc.getKey());
            } catch (Throwable ignored) { }
        }
        int[] slots = {10, 11, 12, 19, 20, 21, 28, 29, 30};   // 3x3 grid
        for (int i = 0; i < 9; i++) {
            ItemStack ing = grid[i];
            if (ing == null) continue;
            ItemMeta m = ing.getItemMeta();
            m.setLore(List.of(C + "7" + ing.getAmount() + "x " + pretty2(ing.getType())));
            ing.setItemMeta(m);
            inv.setItem(slots[i], ing);
        }
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta am = arrow.getItemMeta();
        am.setDisplayName(C + "7Craft in a crafting table");
        arrow.setItemMeta(am);
        inv.setItem(24, arrow);
        ItemStack result = new ItemStack(r.result(), r.count());
        ItemMeta rm = result.getItemMeta();
        rm.setDisplayName(C + "a" + pretty(r.id()) + " x" + r.count());
        List<String> rl = new ArrayList<>();
        if (found instanceof ShapedRecipe)
            rl.add(C + "7Place the ingredients in this exact pattern in a crafting table.");
        else if (found instanceof ShapelessRecipe)
            rl.add(C + "7Throw the ingredients together in a crafting table (any order).");
        else {
            for (Ingredient ing : r.ingredients()) rl.add(C + "7  " + ing.amount() + "x " + pretty2(ing.mat()));
            rl.add(C + "7Craft in a crafting table.");
        }
        if (found != null) rl.add(C + "8Unlocked in your recipe book too - press E to see it.");
        rm.setLore(rl);
        result.setItemMeta(rm);
        inv.setItem(26, result);
        inv.setItem(40, nav(Material.ARROW, "back:" + fromPage, "Back to recipes"));
        inv.setItem(44, nav(Material.BOOK, "close", "Close"));
        openGuis.put(p.getUniqueId(), inv);
        p.openInventory(inv);
    }

    // ---------------- commands ----------------
    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (c.getName().equalsIgnoreCase("craft") && a.length == 1) return new ArrayList<>(beginners.keySet());
        return List.of("list", "reload");
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("craft")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
            String q = args.length > 0 ? args[0].toLowerCase(Locale.ROOT) : null;
            int page = 0;
            if (q != null && beginners.containsKey(q)) {
                List<RecipeDef> all = new ArrayList<>(beginners.values());
                int idx = 0;
                for (int i = 0; i < all.size(); i++) if (all.get(i).id().equalsIgnoreCase(q)) { idx = i; break; }
                page = idx / 45;
            }
            openBeginner(p, page);
            return true;
        }
        // /crafting
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                sender.sendMessage(C + "e\u2692 Custom crafting recipes (" + keys.size() + "):");
                ConfigurationSection cs = getConfig().getConfigurationSection("recipes");
                if (cs != null) for (String id : cs.getKeys(false))
                    sender.sendMessage(C + "7 - " + C + "f" + id + C + "7 -> " + C + "a" + cs.getString(id + ".result", "?"));
                sender.sendMessage(C + "8Tip: /craft opens the beginner recipe list (100 basics, guide only) for everyone.");
            }
            case "reload" -> {
                if (!sender.hasPermission("mavocrafting.admin")) { sender.sendMessage("OP only."); return true; }
                reloadConfig();
                loadRecipes();
                loadBeginner();
                sender.sendMessage(C + "aRecipes reloaded (" + keys.size() + " custom, " + beginners.size() + " beginner).");
            }
            default -> sender.sendMessage(C + "7/crafting list | reload (OP)");
        }
        return true;
    }
}
