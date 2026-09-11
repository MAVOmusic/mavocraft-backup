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
    private final Map<String, ShapedRecipe> customRecipes = new LinkedHashMap<>(); // 3.0.6: /craft customs browser
    private final Map<String, RecipeDef> beginners = new LinkedHashMap<>();
    private final NamespacedKey recipeKey = new NamespacedKey("mavocrafting", "recipe");
    private final NamespacedKey navKey = new NamespacedKey("mavocrafting", "nav");
    private final Map<UUID, Long> lastCraft = new ConcurrentHashMap<>();
    private final Map<UUID, Inventory> openGuis = new ConcurrentHashMap<>();   // HOTFIX 38: scope clicks to OUR /craft menu only

    /** Paper renamed these; the 3.0.0 upgrade wrote the old names into live configs
     *  (EMPTY_MAP, TERRA_COTTA), so /craft loaded 98 instead of 100. 3.0.2 heals them. */
    private static final java.util.Map<String,String> LEGACY = java.util.Map.of(
            "EMPTY_MAP", "MAP",
            "TERRA_COTTA", "TERRACOTTA",
            "EXP_BOTTLE", "EXPERIENCE_BOTTLE");

    private record Ingredient(Material mat, int amount) {}
    private record RecipeDef(String id, Material result, int count, List<Ingredient> ingredients, List<String> layout) {}

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
            if (mergeMissing(disk, def)) {
                try { disk.save(f); }
                catch (Exception ex) { getLogger().warning("could not save config.yml: " + ex.getMessage()); }
            }
            // v3.0 REPAIR (live bug): old configs carry the 50-recipe list and the
            // merge only ADDS missing keys, so the other 50 never appeared
            // ("50 beginner recipe(s)" on live). Replace the whole section once.
            if (disk.getInt("recipes-version", 0) < 4) {
                Object list = def.get("beginner-recipes");
                if (list != null) {
                    disk.set("recipes-version", 4);
                    disk.set("beginner-recipes", list);
                    try { disk.save(f); }
                    catch (Exception ex) { getLogger().warning("could not save recipe upgrade: " + ex.getMessage()); }
                    getLogger().info("v3.0.3: beginner recipes re-verified - 100 real vanilla basics (correct amounts + 3x3 grid).");
                }
            }
            // 3.0.2: the 3.0.0 upgrade wrote two names Paper no longer knows
            // (EMPTY_MAP, TERRA_COTTA) into the live config -> 98 recipes silently.
            repairLegacyNames(disk, f);
            reloadConfig();
        } catch (Throwable t) {
            getLogger().warning("config merge failed: " + t.getMessage());
        }
    }

    /** v3.0: flat key merge - the old recursive version built wrong path prefixes
     *  (nested sections stayed empty). Every missing leaf path from the bundled
     *  config is written once. */
    /** 3.0.2: replace legacy material names in every string leaf of the disk config
     *  (results AND ingredients, beginner + custom recipes + anything else) and save,
     *  so the live config heals itself and all 100 recipes really load. */
    private int repairLegacyNames(YamlConfiguration disk, File f) {
        int fixed = 0;
        StringBuilder detail = new StringBuilder();
        java.util.regex.Pattern p = java.util.regex.Pattern.compile(
                "(?<![A-Z0-9_])(" + String.join("|", LEGACY.keySet()) + ")(?![A-Z0-9_])",
                java.util.regex.Pattern.CASE_INSENSITIVE);
        for (String path : disk.getKeys(true)) {
            Object v = disk.get(path);
            if (!(v instanceof String s)) continue;
            java.util.regex.Matcher m = p.matcher(s);
            if (!m.find()) continue;
            StringBuffer sb = new StringBuffer();
            do {
                m.appendReplacement(sb, LEGACY.get(m.group(1).toUpperCase(Locale.ROOT)));
                fixed++;
                if (detail.indexOf(m.group(1).toUpperCase(Locale.ROOT)) < 0)
                    detail.append(m.group(1).toUpperCase(Locale.ROOT)).append("->")
                          .append(LEGACY.get(m.group(1).toUpperCase(Locale.ROOT))).append(' ');
            } while (m.find());
            m.appendTail(sb);
            disk.set(path, sb.toString());
        }
        if (fixed > 0) {
            try {
                disk.save(f);
                getLogger().info("3.0.2: repaired " + fixed + " legacy material name(s) in config.yml: " + detail);
            } catch (Exception ex) {
                getLogger().warning("could not save repaired recipes: " + ex.getMessage());
            }
        }
        return fixed;
    }

    private boolean mergeMissing(ConfigurationSection disk, ConfigurationSection def) {
        boolean changed = false;
        for (String path : def.getKeys(true)) {
            Object v = def.get(path);
            if (v == null || v instanceof ConfigurationSection) continue;   // leaves only
            if (disk.get(path) == null) {
                disk.set(path, v);
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
        customRecipes.clear();
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
                customRecipes.put(id, r);
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
                List<String> layout = null;
                if (c.contains("layout")) {   // 3.0.3: verified vanilla 3x3 grid, row by row
                    List<String> l = c.getStringList("layout");
                    if (l.size() >= 9) layout = new ArrayList<>(l.subList(0, 9));
                }
                beginners.put(id.toLowerCase(Locale.ROOT), new RecipeDef(id, result, count, ings, layout));
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
        if (!customRecipes.isEmpty()) inv.setItem(47, nav(Material.CRAFTING_TABLE, "customs",
                "Custom recipes (" + customRecipes.size() + ")"));
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
            else if (nav.equals("customs")) openCustoms(p);
            else if (nav.equals("close")) p.closeInventory();
            return;
        }
        // HOTFIX 40: the recipe preview is display-only - block clicks on its icons
        if (e.getView().getTitle().contains(" - how to craft")) { e.setCancelled(true); return; }
        String id = recipeOf(it);
        if (id == null) return;
        e.setCancelled(true);
        // 3.0.6: custom recipes open their exact registered shape
        if (id.startsWith("custom:")) {
            Long lastC = lastCraft.get(p.getUniqueId());
            long nowC = System.currentTimeMillis();
            if (lastC != null && nowC - lastC < 300) return;
            lastCraft.put(p.getUniqueId(), nowC);
            openCustomRecipe(p, id.substring(7));
            p.playSound(p.getLocation(), org.bukkit.Sound.ITEM_BOOK_PAGE_TURN, 1f, 1.2f);
            return;
        }
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

    /** 3.0.3: GUIDE mode. Clicking a recipe in /craft opens a 3x3 preview of
     *  the REAL recipe and unlocks it in the player's recipe book (press E).
     *  The grid comes from the verified vanilla layout stored in the config -
     *  the server registry can return ALT recipes for the same result (e.g.
     *  a white bed from an orange bed + white dye), which is why the registry
     *  lookup is only a fallback now. Nothing is crafted and nothing is consumed. */
    private void openRecipe(Player p, RecipeDef r, int fromPage) {
        Inventory inv = Bukkit.createInventory(null, 45, C + "1\u2692 " + pretty(r.id()) + " - how to craft");
        ItemStack[] grid = new ItemStack[9];
        Recipe found = null;
        if (r.layout() != null) {                                  // verified vanilla grid
            for (int i = 0; i < 9; i++) {
                String cell = r.layout().get(i);
                if (cell == null || cell.equals("AIR")) continue;
                Material m = Material.matchMaterial(cell);
                if (m != null) grid[i] = new ItemStack(m);
            }
        } else {
            try {                                                  // fallback: registry
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
        }
        if (found != null) {
            try {   // unlock in the vanilla recipe book (press E to see it)
                for (Recipe rc : Bukkit.getRecipesFor(new ItemStack(r.result())))
                    if (rc instanceof org.bukkit.Keyed k) p.discoverRecipe(k.getKey());
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
        if (r.layout() != null || found instanceof ShapedRecipe)
            rl.add(C + "7Place the ingredients in this exact pattern in a crafting table.");
        else if (found instanceof ShapelessRecipe)
            rl.add(C + "7Throw the ingredients together in a crafting table (any order).");
        else {
            for (Ingredient ing : r.ingredients()) rl.add(C + "7  " + ing.amount() + "x " + pretty2(ing.mat()));
            rl.add(C + "7Craft in a crafting table.");
        }
        rl.add(C + "8Unlocked in your recipe book too - press E to see it.");
        rm.setLore(rl);
        result.setItemMeta(rm);
        inv.setItem(26, result);
        inv.setItem(40, nav(Material.ARROW, "back:" + fromPage, "Back to recipes"));
        inv.setItem(44, nav(Material.BOOK, "close", "Close"));
        openGuis.put(p.getUniqueId(), inv);
        p.openInventory(inv);
    }

    // ---------------- 3.0.6: customs browser + direct /craft <name> ----------------
    /** "/craft has options to select from" - the 7 customs finally open from the GUI. */
    private void openCustoms(Player p) {
        List<String> ids = new ArrayList<>(customRecipes.keySet());
        Inventory inv = Bukkit.createInventory(null, 27, C + "1\u2692 Craft - customs (" + ids.size() + ")");
        for (int idx = 0; idx < ids.size() && idx < 21; idx++) {
            String id = ids.get(idx);
            ShapedRecipe sr = customRecipes.get(id);
            if (sr == null) continue;
            ItemStack it = sr.getResult().clone();
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(C + "a" + pretty(id));
            List<String> lore = new ArrayList<>();
            for (Map.Entry<Material, Integer> en : customAmounts(sr).entrySet())
                lore.add(C + "7  " + en.getValue() + "x " + pretty2(en.getKey()));
            lore.add(C + "eClick to see how to craft it (guide - no auto craft)");
            m.setLore(lore);
            m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
            m.getPersistentDataContainer().set(recipeKey, PersistentDataType.STRING, "custom:" + id);
            it.setItemMeta(m);
            inv.setItem(idx, it);
        }
        inv.setItem(22, nav(Material.ARROW, "back:0", "Back to basics"));
        inv.setItem(26, nav(Material.BOOK, "close", "Close"));
        openGuis.put(p.getUniqueId(), inv);
        p.openInventory(inv);
    }

    private static Map<Material, Integer> customAmounts(ShapedRecipe sr) {
        Map<Material, Integer> out = new LinkedHashMap<>();
        Map<Character, ItemStack> map = sr.getIngredientMap();
        for (String row : sr.getShape())
            for (int k = 0; k < row.length(); k++) {
                ItemStack ing = map.get(row.charAt(k));
                if (ing != null) out.merge(ing.getType(), 1, Integer::sum);
            }
        return out;
    }

    /** The EXACT registered custom shape (not the registry-first match - a custom
     *  lead must show string+slime, never the vanilla slime-ball recipe). */
    private void openCustomRecipe(Player p, String id) {
        ShapedRecipe sr = customRecipes.get(id);
        if (sr == null) { openCustoms(p); return; }
        Inventory inv = Bukkit.createInventory(null, 45, C + "1\u2692 " + pretty(id) + " - how to craft");
        int[] slots = {10, 11, 12, 19, 20, 21, 28, 29, 30};
        String[] shape = sr.getShape();
        Map<Character, ItemStack> map = sr.getIngredientMap();
        for (int row = 0; row < shape.length && row < 3; row++) {
            String s = shape[row];
            for (int col = 0; col < s.length() && col < 3; col++) {
                ItemStack ing = map.get(s.charAt(col));
                if (ing == null) continue;
                ItemStack show = ing.clone();
                ItemMeta m = show.getItemMeta();
                m.setLore(List.of(C + "7" + pretty2(show.getType())));
                show.setItemMeta(m);
                inv.setItem(slots[row * 3 + col], show);
            }
        }
        ItemStack arrow = new ItemStack(Material.ARROW);
        ItemMeta am = arrow.getItemMeta();
        am.setDisplayName(C + "7Craft in a crafting table");
        arrow.setItemMeta(am);
        inv.setItem(24, arrow);
        ItemStack result = sr.getResult().clone();
        ItemMeta rm = result.getItemMeta();
        rm.setDisplayName(C + "a" + pretty(id) + (result.getAmount() > 1 ? " x" + result.getAmount() : ""));
        rm.setLore(List.of(C + "7Custom MAVO recipe - works in any crafting table."));
        result.setItemMeta(rm);
        inv.setItem(26, result);
        try {
            NamespacedKey k = keys.get(id);
            if (k != null) p.discoverRecipe(k);
        } catch (Throwable ignored) { }
        inv.setItem(40, nav(Material.ARROW, "customs", "Back to customs"));
        inv.setItem(44, nav(Material.BOOK, "close", "Close"));
        openGuis.put(p.getUniqueId(), inv);
        p.openInventory(inv);
    }

    private RecipeDef findRecipe(String q) {
        if (beginners.containsKey(q)) return beginners.get(q);
        for (Map.Entry<String, RecipeDef> en : beginners.entrySet()) {
            String id = en.getKey();
            if (id.contains(q) || pretty(id).toLowerCase(Locale.ROOT).contains(q)) return en.getValue();
        }
        return null;
    }

    private String findCustom(String q) {
        if (customRecipes.containsKey(q)) return q;
        for (String id : customRecipes.keySet())
            if (id.contains(q) || pretty(id).toLowerCase(Locale.ROOT).contains(q)) return id;
        return null;
    }

    private int pageOfRecipe(RecipeDef r) {
        List<RecipeDef> all = new ArrayList<>(beginners.values());
        for (int idx = 0; idx < all.size(); idx++)
            if (all.get(idx).id().equalsIgnoreCase(r.id())) return idx / 45;
        return 0;
    }

    // ---------------- commands ----------------
    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (c.getName().equalsIgnoreCase("craft") && a.length == 1) {
            List<String> out = new ArrayList<>(beginners.keySet());
            out.addAll(customRecipes.keySet());   // 3.0.6: customs complete too
            out.sort(String::compareTo);
            return out;
        }
        return List.of("list", "reload");
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("craft")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
            // 3.0.6: /craft <name> opens the recipe directly (exact, then
            // contains-match on id or display name, beginners + customs).
            if (args.length > 0) {
                String q = args[0].toLowerCase(Locale.ROOT);
                RecipeDef direct = findRecipe(q);
                if (direct != null) { openRecipe(p, direct, pageOfRecipe(direct)); return true; }
                String custom = findCustom(q);
                if (custom != null) { openCustomRecipe(p, custom); return true; }
                p.sendMessage(C + "cNo recipe matches '" + args[0] + "' - browse /craft.");
            }
            openBeginner(p, 0);
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
