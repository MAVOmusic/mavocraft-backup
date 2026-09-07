package mavo.spawners;

import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

/** MAVOSpawners 1.0.0 - mineable spawners (Discord CW#4 idea 18, inspired by SilkSpawners).
 *  Breaking a spawner with a silk-touch pickaxe drops a "Spawner (mob)" item that keeps
 *  the mob type; placing it builds a real spawner of that type. /spawner info reads any
 *  spawner you look at. */
public final class Spawners extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';
    private final NamespacedKey mobKey = new NamespacedKey("mavospawner", "mob");

    private boolean silkOnly = true, placeable = true;

    @Override public void onEnable() {
        saveDefaultConfig();
        silkOnly = getConfig().getBoolean("silk-touch-only", true);
        placeable = getConfig().getBoolean("placeable", true);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOSpawners v" + getDescription().getVersion() + " enabled.");
    }

    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private static String nice(EntityType t) { return t.name().toLowerCase(Locale.ROOT).replace('_', ' '); }

    private boolean hasSilk(ItemStack it) {
        return it != null && it.getEnchantments().keySet().stream()
                .anyMatch(e -> e.getKey().getKey().equals("silk_touch"));
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.SPAWNER) return;
        if (e.getBlock().getState() instanceof CreatureSpawner cs) {
            EntityType type = cs.getSpawnedType();
            ItemStack tool = e.getPlayer().getInventory().getItemInMainHand();
            if (silkOnly && !hasSilk(tool)) return; // vanilla drop
            e.setDropItems(false);
            ItemStack item = new ItemStack(Material.SPAWNER);
            applyMob(item, type);
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), item);
            e.getPlayer().sendMessage(C + "aSilk-touched spawner (" + C + "e" + nice(type) + C + "a).");
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        ItemStack item = e.getItemInHand();
        if (item.getType() != Material.SPAWNER) return;
        String t = mobOf(item);
        if (t == null || !placeable) return;
        if (e.getBlockPlaced().getState() instanceof CreatureSpawner cs) {
            EntityType type = EntityType.valueOf(t);
            cs.setSpawnedType(type);
            cs.update(true, false);
            e.getPlayer().sendMessage(C + "aSpawner set to " + C + "e" + nice(type) + C + "a.");
        }
    }

    private void applyMob(ItemStack it, EntityType type) {
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(cc("&eSpawner (") + cc("&a" + nice(type)) + cc("&e)"));
        m.setLore(List.of(cc("&7Place it to spawn " + nice(type) + "s."), cc("&7Get it from a boss or silk-touch.")));
        m.getPersistentDataContainer().set(mobKey, PersistentDataType.STRING, type.name());
        it.setItemMeta(m);
    }

    private String mobOf(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(mobKey, PersistentDataType.STRING);
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
        Block b = p.getTargetBlockExact(6);
        if (b == null || b.getType() != Material.SPAWNER || !(b.getState() instanceof CreatureSpawner cs)) {
            p.sendMessage(C + "cLook at a spawner."); return true;
        }
        p.sendMessage(C + "e\uD83D\uDC0E Spawner info:");
        p.sendMessage(C + "7  Mob: " + C + "a" + nice(cs.getSpawnedType()));
        p.sendMessage(C + "7  Delay: " + C + "e" + cs.getDelay() + "t" + C + "7 (resets to min/max when near)");
        p.sendMessage(C + "7  Max nearby: " + C + "e" + cs.getMaxNearbyEntities() + C + "7 | player range: "
                + C + "e" + cs.getRequiredPlayerRange() + C + "7 | spawn range: " + C + "e" + cs.getSpawnRange());
        p.sendMessage(C + "8Tip: silk-touch lets you move it.");
        return true;
    }
}
