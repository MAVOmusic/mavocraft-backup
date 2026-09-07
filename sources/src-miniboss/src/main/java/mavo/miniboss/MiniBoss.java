package mavo.miniboss;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** MAVOMiniboss 1.0.0 - ambient biome bosses (Discord CW#4 idea 17, inspired by MythicMobs/Elitemobs).
 *  Every 45 min a random boss spawns in the wild (surface, 1-5k from spawn, up to 3 alive).
 *  Bosses are tagged, hit harder (config HP), and drop coins + Lucky Coins + crate keys +
 *  a trophy head to the killer (Vault / reflection into MAVOLuckyCoins + MAVOCrates). */
public final class MiniBoss extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';
    private final NamespacedKey tag = new NamespacedKey("mavominiboss", "boss");
    private final NamespacedKey bossType = new NamespacedKey("mavominiboss", "type");

    private Economy econ;
    private int intervalMin = 45, maxAlive = 3, spawnMin = 1000, spawnMax = 5000;
    private boolean surfaceOnly = true;
    private final Map<String, BossDef> defs = new HashMap<>();
    private final Map<UUID, String> alive = new HashMap<>();
    private final Random rnd = new Random();

    private record BossDef(String name, EntityType type, double hp, long coins,
                           int lucky, String crate, int crateKeys, Material head) {}

    @Override public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        load();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 200L, 1200L);
        tick();
        getLogger().info("MAVOMiniboss v" + getDescription().getVersion() + " enabled - " + defs.size()
                + " boss type(s).");
    }

    @Override public void onDisable() {
        for (UUID id : alive.keySet()) {
            var en = Bukkit.getEntity(id);
            if (en != null) en.remove();
        }
        alive.clear();
    }

    private void load() {
        defs.clear();
        intervalMin = Math.max(5, getConfig().getInt("spawn-interval-minutes", 45));
        maxAlive = Math.max(1, getConfig().getInt("max-alive", 3));
        spawnMin = Math.max(100, getConfig().getInt("spawn-min-distance", 1000));
        spawnMax = Math.max(spawnMin, getConfig().getInt("spawn-max-distance", 5000));
        surfaceOnly = getConfig().getBoolean("spawn-surface", true);
        ConfigurationSection cs = getConfig().getConfigurationSection("bosses");
        if (cs == null) return;
        for (String id : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(id);
            if (c == null) continue;
            EntityType t;
            try { t = EntityType.valueOf(c.getString("entity", "WITCH").toUpperCase(Locale.ROOT)); }
            catch (Throwable ex) { continue; }
            Material h = Material.matchMaterial(c.getString("head", "PLAYER_HEAD"));
            defs.put(id, new BossDef(c.getString("name", "&cBOSS"), t, Math.max(100, c.getDouble("hp", 800)),
                    Math.max(0, c.getLong("coins", 0)), Math.max(0, c.getInt("lucky-coins", 0)),
                    c.getString("crate-key", ""), Math.max(0, c.getInt("crate-keys", 0)),
                    h == null ? Material.PLAYER_HEAD : h));
        }
    }

    private void tick() {
        if (alive.size() >= maxAlive) return;
        if (rnd.nextInt(intervalMin) != 0) return; // ~once per interval
        spawnOne();
    }

    private void spawnOne() {
        if (defs.isEmpty()) return;
        World w = Bukkit.getWorlds().get(0);
        Location l = findSpot(w);
        if (l == null) return;
        String key = new ArrayList<>(defs.keySet()).get(rnd.nextInt(defs.size()));
        BossDef d = defs.get(key);
        LivingEntity e = (LivingEntity) w.spawnEntity(l, d.type());
        e.setCustomName(cc(d.name()));
        e.setCustomNameVisible(true);
        e.setPersistent(true);
        e.setRemoveWhenFarAway(false);
        try {
            e.getAttribute(Attribute.MAX_HEALTH).setBaseValue(d.hp());
            e.setHealth(d.hp());
            e.getAttribute(Attribute.MOVEMENT_SPEED).setBaseValue(
                    e.getAttribute(Attribute.MOVEMENT_SPEED).getBaseValue() * 1.15);
        } catch (Throwable ignored) { }
        e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        e.getPersistentDataContainer().set(bossType, PersistentDataType.STRING, key);
        alive.put(e.getUniqueId(), key);
        Bukkit.broadcastMessage(C + "5\u2694 A " + cc(d.name()) + C + "5 has appeared in the wild ("
                + C + "e" + (int) l.getX() + ", " + (int) l.getZ() + C + "5)! Go hunt it!");
    }

    private Location findSpot(World w) {
        int sx = w.getSpawnLocation().getBlockX(), sz = w.getSpawnLocation().getBlockZ();
        for (int tries = 0; tries < 50; tries++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            int dist = spawnMin + rnd.nextInt(spawnMax - spawnMin);
            int x = sx + (int) (Math.cos(ang) * dist);
            int z = sz + (int) (Math.sin(ang) * dist);
            int y = w.getHighestBlockYAt(x, z);
            if (surfaceOnly && y < 50) continue;
            return new Location(w, x + 0.5, y + 1, z + 0.5);
        }
        return null;
    }

    @EventHandler
    public void onKill(EntityDeathEvent e) {
        LivingEntity en = e.getEntity();
        if (!en.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) return;
        alive.remove(en.getUniqueId());
        String key = en.getPersistentDataContainer().get(bossType, PersistentDataType.STRING);
        BossDef d = defs.get(key);
        if (d == null) return;
        e.setDroppedExp(0);
        e.getDrops().clear();
        Player killer = en.getKiller();
        String killerName = killer != null ? killer.getName() : "?";
        Bukkit.broadcastMessage(C + "5\u2694 " + cc(d.name()) + C + "5 was slain by " + C + "a" + killerName
                + C + "5! Loot: " + C + "e" + String.format("%,d", d.coins()) + " coins"
                + (d.crateKeys() > 0 ? " + " + d.crateKeys() + "x " + d.crate() + " key" : "") + ".");
        if (killer == null) return;
        if (econ != null && d.coins() > 0) econ.depositPlayer(killer, d.coins());
        try {
            org.bukkit.plugin.Plugin luck = Bukkit.getPluginManager().getPlugin("MAVOLuckyCoins");
            if (luck != null && d.lucky() > 0)
                luck.getClass().getMethod("giveCoins", Player.class, int.class).invoke(luck, killer, d.lucky());
        } catch (Throwable ignored) { }
        try {
            if (d.crateKeys() > 0 && !d.crate().isEmpty())
                Class.forName("mavo.crates.Crates").getMethod("giveKey", Player.class, String.class, int.class)
                        .invoke(null, killer, d.crate(), d.crateKeys());
        } catch (Throwable ignored) { }
        ItemStack head = new ItemStack(d.head());
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setDisplayName(cc(d.name()) + C + " Trophy");
        sm.setLore(List.of(C + "7Slain by the MAVOcraft community.", C + "7A rare collectible."));
        head.setItemMeta(sm);
        var left = killer.getInventory().addItem(head);
        for (ItemStack it : left.values()) killer.getWorld().dropItemNaturally(killer.getLocation(), it);
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        killer.sendMessage(C + "aYour prize: " + C + "e" + String.format("%,d", d.coins()) + " coins"
                + (d.lucky() > 0 ? " + " + d.lucky() + "x Lucky Coin" : "") + " + " + d.head().name().toLowerCase(Locale.ROOT).replace('_', ' ') + "!");
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                sender.sendMessage(C + "5\u2694 Minibosses: " + C + "e" + alive.size() + "/" + maxAlive
                        + C + "5 alive, " + C + "e" + defs.size() + C + "5 types, every " + intervalMin + " min.");
                for (UUID id : alive.keySet()) {
                    var en = Bukkit.getEntity(id);
                    if (en != null) sender.sendMessage(C + "7 - " + cc(getDef(en).name()) + C + "7 at "
                            + en.getLocation().getBlockX() + "," + en.getLocation().getBlockZ());
                }
            }
            case "locate" -> {
                if (alive.isEmpty()) { sender.sendMessage(C + "7No miniboss alive right now."); return true; }
                for (UUID id : alive.keySet()) {
                    var en = Bukkit.getEntity(id);
                    if (en != null) sender.sendMessage(C + "7 - " + cc(getDef(en).name()) + C + "7 at "
                            + en.getLocation().getBlockX() + " " + en.getLocation().getBlockZ());
                }
            }
            case "reload" -> {
                if (!sender.hasPermission("mavominiboss.admin")) { sender.sendMessage("OP only."); return true; }
                reloadConfig(); load();
                sender.sendMessage(C + "aReloaded " + defs.size() + " boss types.");
            }
            default -> sender.sendMessage(C + "7/miniboss status | locate (OP: reload)");
        }
        return true;
    }

    private BossDef getDef(LivingEntity en) {
        String k = en.getPersistentDataContainer().get(bossType, PersistentDataType.STRING);
        return defs.getOrDefault(k, new BossDef("&cBOSS", EntityType.WITCH, 100, 0, 0, "", 0, Material.PLAYER_HEAD));
    }

    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
