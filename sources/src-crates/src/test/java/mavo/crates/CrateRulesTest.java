package mavo.crates;

import java.util.List;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.junit.Test;
import static org.junit.Assert.*;
import static org.mockito.Mockito.*;

public class CrateRulesTest {
    private ItemStack stack(int amount) {
        ItemStack item = mock(ItemStack.class);
        when(item.getAmount()).thenReturn(amount);
        return item;
    }

    @Test public void twoKeysConsumeOneAndKeepTheClonedRemainder() {
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack two = stack(2), remainder = stack(1), otherStack = stack(12);
        when(two.clone()).thenReturn(remainder);
        when(inv.getContents()).thenReturn(new ItemStack[]{null, two, otherStack});
        assertTrue(CrateRules.takeOneKey(inv, item -> true));
        verify(remainder).setAmount(1);
        verify(inv).setItem(1, remainder);
        verify(inv, times(1)).setItem(anyInt(), nullable(ItemStack.class));
        verify(two, never()).setAmount(anyInt());
        verify(otherStack, never()).clone();
    }

    @Test public void fullStackLosesOnlyOne() {
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack full = stack(64), remainder = stack(63);
        when(full.clone()).thenReturn(remainder);
        when(inv.getContents()).thenReturn(new ItemStack[]{full});
        assertTrue(CrateRules.takeOneKey(inv, item -> true));
        verify(remainder).setAmount(63);
        verify(inv).setItem(0, remainder);
    }

    @Test public void singleKeyClearsOnlyItsSlotAndSkipsWrongTier() {
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack rare = stack(2), common = stack(1), more = stack(1);
        when(inv.getContents()).thenReturn(new ItemStack[]{rare, common, more});
        assertTrue(CrateRules.takeOneKey(inv, item -> item != rare));
        verify(inv).setItem(1, null);
        verify(inv, times(1)).setItem(anyInt(), nullable(ItemStack.class));
    }

    @Test public void offhandStackAlsoLosesOnlyOne() {
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack[] slots = new ItemStack[41];
        ItemStack keys = stack(2), remainder = stack(1);
        slots[40] = keys;
        when(keys.clone()).thenReturn(remainder);
        when(inv.getContents()).thenReturn(slots);
        assertTrue(CrateRules.takeOneKey(inv, item -> true));
        verify(inv).setItem(40, remainder);
        verify(remainder).setAmount(1);
    }

    @Test public void noMatchingKeyChangesNothing() {
        PlayerInventory inv = mock(PlayerInventory.class);
        ItemStack wrongTier = stack(3), empty = stack(0);
        when(inv.getContents()).thenReturn(new ItemStack[]{null, wrongTier, empty});
        assertFalse(CrateRules.takeOneKey(inv, item -> false));
        verify(inv, never()).setItem(anyInt(), nullable(ItemStack.class));
    }

    @Test public void migrationChangesAllCooldownsAndPreservesOtherData() throws Exception {
        YamlConfiguration disk = new YamlConfiguration();
        disk.set("pool-version", 2);
        disk.set("key-drops.common", 1.0);
        disk.set("players.example.common", 123456L);
        disk.set("blocks.world,1,2,3", "common");
        for (String id : List.of("common", "rare", "mythic", "custom")) {
            disk.set("crates." + id + ".cooldown-seconds", 600);
            disk.set("crates." + id + ".rewards", List.of("coins:1234:100"));
            disk.set("crates." + id + ".key-name", "custom name");
        }
        String before = disk.saveToString();
        assertTrue(CrateRules.upgradeCooldowns(disk));
        assertEquals(1, disk.getInt("cooldown-version"));
        for (String id : List.of("common", "rare", "mythic", "custom")) {
            assertEquals(30, disk.getLong("crates." + id + ".cooldown-seconds"));
            disk.set("crates." + id + ".cooldown-seconds", 600);
        }
        disk.set("cooldown-version", null);
        assertEquals(before, disk.saveToString()); // absolutely no unrelated keys changed
    }

    @Test public void migrationIsPersistentAndDoesNotRepeat() throws Exception {
        YamlConfiguration disk = new YamlConfiguration();
        disk.set("crates.common.display", "Common"); // cooldown missing: still receives 30
        assertTrue(CrateRules.upgradeCooldowns(disk));
        assertEquals(30, disk.getLong("crates.common.cooldown-seconds"));
        YamlConfiguration reloaded = new YamlConfiguration();
        reloaded.loadFromString(disk.saveToString());
        String before = reloaded.saveToString();
        assertFalse(CrateRules.upgradeCooldowns(reloaded));
        assertEquals(before, reloaded.saveToString());
    }

    @Test public void freshDefaultsUseThirtySecondsForEveryTier() throws Exception {
        YamlConfiguration bundled = new YamlConfiguration();
        try (var in = getClass().getResourceAsStream("/config.yml")) {
            assertNotNull(in);
            bundled.load(new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
        }
        for (String id : List.of("common", "rare", "mythic"))
            assertEquals(30, bundled.getLong("crates." + id + ".cooldown-seconds"));
        assertFalse(CrateRules.upgradeCooldowns(bundled));
    }
}
