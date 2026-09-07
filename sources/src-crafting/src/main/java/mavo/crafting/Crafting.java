package mavo.crafting;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Keyed;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.Recipe;
import org.bukkit.inventory.ShapedRecipe;
import org.bukkit.plugin.java.JavaPlugin;

/** MAVOCrafting 1.0.0 - custom crafting (Discord CW#4 idea 14, inspired by Slimefun-lite).
 *  Config-driven shaped recipes for otherwise uncraftable items. Recipes register on
 *  enable and re-register on /crafting reload (old keys removed first). */
public final class Crafting extends JavaPlugin {

    private static final char C = '\u00a7';
    private final Map<String, NamespacedKey> keys = new LinkedHashMap<>();

    @Override public void onEnable() {
        saveDefaultConfig();
        loadRecipes();
        getLogger().info("MAVOCrafting v" + getDescription().getVersion() + " enabled - "
                + keys.size() + " recipe(s).");
    }

    private void loadRecipes() {
        // remove previously registered custom recipes
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

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "list" -> {
                sender.sendMessage(C + "e\u2696 Custom crafting recipes (" + keys.size() + "):");
                ConfigurationSection cs = getConfig().getConfigurationSection("recipes");
                if (cs != null) for (String id : cs.getKeys(false))
                    sender.sendMessage(C + "7 - " + C + "f" + id + C + "7 -> " + C + "a" + cs.getString(id + ".result", "?"));
            }
            case "reload" -> {
                if (!sender.hasPermission("mavocrafting.admin")) { sender.sendMessage("OP only."); return true; }
                reloadConfig();
                loadRecipes();
                sender.sendMessage(C + "aRecipes reloaded (" + keys.size() + ").");
            }
            default -> sender.sendMessage(C + "7/crafting list | reload (OP)");
        }
        return true;
    }
}
