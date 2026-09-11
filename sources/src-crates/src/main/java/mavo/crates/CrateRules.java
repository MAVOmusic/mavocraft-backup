package mavo.crates;

import java.util.function.Predicate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

/** Small, testable inventory/config rules shared by the live crate paths. */
final class CrateRules {
    static final long COOLDOWN_SECONDS = 30;

    private CrateRules() { }

    /** Consumes exactly one matching key, including an offhand key, never a whole stack. */
    static boolean takeOneKey(PlayerInventory inventory, Predicate<ItemStack> matches) {
        ItemStack[] contents = inventory.getContents();
        for (int slot = 0; slot < contents.length; slot++) {
            ItemStack item = contents[slot];
            if (item == null || item.getAmount() <= 0 || !matches.test(item)) continue;
            if (item.getAmount() == 1) {
                inventory.setItem(slot, null);
            } else {
                ItemStack remainder = item.clone(); // keep name, lore, PDC and all other metadata
                remainder.setAmount(item.getAmount() - 1);
                inventory.setItem(slot, remainder);
            }
            return true; // do not consume from any subsequent matching slot
        }
        return false;
    }

    /** One-time, surgical migration: no reward pools, key odds or player data touched. */
    static boolean upgradeCooldowns(ConfigurationSection disk) {
        if (disk.getInt("cooldown-version", 0) >= 1) return false;
        ConfigurationSection crates = disk.getConfigurationSection("crates");
        if (crates != null) {
            for (String id : crates.getKeys(false)) {
                ConfigurationSection crate = crates.getConfigurationSection(id);
                if (crate != null) crate.set("cooldown-seconds", COOLDOWN_SECONDS);
            }
        }
        disk.set("cooldown-version", 1);
        return true;
    }
}
