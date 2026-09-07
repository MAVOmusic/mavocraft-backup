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

/** MAVOMiniboss 1.0.0 - ambient biome bosses (Discord CW#4 idea 17, inspired by MythicMobs/Elitemobs).
 *  Every 45 min a random boss spawns in the wild (surface, 1-5k from spawn, up to 3 alive).
 *  Bosses are tagged, hit harder (config HP), and drop coins + Lucky Coins + crate keys +
 *  a trophy head to the killer (Vault / reflection into MAVOLuckyCoins + MAVOCrates).
 *  Hotfix 35: /hunt teleports you out of spawn (next to a live boss if one is up - 30s
 *  cooldown) and boss locations are broadcast to chat every 5 minutes. */
public final class MiniBoss extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';
    private final NamespacedKey tag = new NamespacedKey("mavominiboss", "boss");
    private final NamespacedKey bossType = new NamespacedKey("mavominiboss", "type");

    private Economy econ;
    private int intervalMin = 45, maxAlive = 3, spawnMin = 1000, spawnMax = 5000;
    private boolean surfaceOnly = true;
    private boolean huntEnabled = true;
    private int huntCooldown = 30;              // seconds
    private int huntBareMin = 500, huntBareMax = 2000;
    private int huntBossRadius = 200;           // HOTFIX 42: land ~200 blocks from a live boss
    private boolean broadcastLocations = true;
    private int broadcastInterval = 5;          // minutes
    private int broadcastRadius = 100;
    private int bcCounter = 0;                  // minutes since last location broadcast
    private final Map<UUID, Long> huntCooldowns = new HashMap<>();
    private final Map<String, BossDef> defs = new HashMap<>();
    private final Map<UUID, String> alive = new HashMap<>();
    private final Random rnd = new Random();

    // HOTFIX 42: drop chances (percent) per boss - announced on spawn, rolled on kill
    private record BossDef(String name, EntityType type, double hp, long coins,
                           int lucky, int luckyChance, String crate, int crateKeys,
                           int crateChance, Material head) {}

    @Override public void onEnable() {
        saveDefaultConfig();
        getConfig().options().copyDefaults(true);
        saveConfig();                          // adds /hunt + broadcast keys to existing configs
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        load();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 200L, 1200L);   // once a minute
        tick();
        getLogger().info("MAVOMiniboss v" + getDescription().getVersion() + " enabled - " + defs.size()
                + " boss type(s), /hunt " + (huntEnabled ? "on" : "off") + ", location broadcast every "
                + broadcastInterval + " min.");
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
        huntEnabled = getConfig().getBoolean("hunt-enabled", true);
        huntCooldown = Math.max(0, getConfig().getInt("hunt-cooldown-seconds", 30));
        huntBareMin = Math.max(100, getConfig().getInt("hunt-bare-min", 500));
        huntBareMax = Math.max(huntBareMin, getConfig().getInt("hunt-bare-max", 2000));
        huntBossRadius = Math.max(10, getConfig().getInt("hunt-boss-radius", 200));
        if (huntBossRadius > 200) {   // HOTFIX 42: old configs of 1000 made players walk too far
            huntBossRadius = 200;
            getConfig().set("hunt-boss-radius", 200);
            saveConfig();
            getLogger().info("Hotfix 42: hunt-boss-radius -> 200 (was too far to walk).");
        }
        broadcastLocations = getConfig().getBoolean("broadcast-locations", true);
        broadcastInterval = Math.max(1, getConfig().getInt("broadcast-interval-minutes", 5));
        broadcastRadius = Math.max(10, getConfig().getInt("broadcast-radius", 100));
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
                    Math.max(0, Math.min(100, c.getInt("lucky-chance", 50))),   // HOTFIX 42 defaults
                    c.getString("crate-key", ""), Math.max(0, c.getInt("crate-keys", 0)),
                    Math.max(0, Math.min(100, c.getInt("crate-chance", 50))),
                    h == null ? Material.PLAYER_HEAD : h));
        }
    }

    private void tick() {
        // boss location broadcast every N minutes - make sure there IS a boss to hunt,
        // so the 5-minute callout is never an empty message (HOTFIX 37).
        if (broadcastLocations) {
            if (++bcCounter >= broadcastInterval) {
                bcCounter = 0;
                if (alive.isEmpty()) spawnOne();
                broadcastLocations();
            }
        }
        if (alive.size() >= maxAlive) return;
        if (rnd.nextInt(intervalMin) != 0) return; // ~once per interval
        spawnOne();
    }

    private void broadcastLocations() {
        for (UUID id : alive.keySet()) {
            var en = Bukkit.getEntity(id);
            if (en == null) continue;
            Location l = en.getLocation();
            Bukkit.broadcastMessage(C + "5\u00bb " + cc(getDef(en).name()) + C + "8 is around "
                    + C + "e" + l.getBlockX() + " , " + l.getBlockZ()
                    + C + "8 (\u00b1" + broadcastRadius + " blocks) - " + C + "b/hunt" + C + "8 to go hunting!");
        }
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
        // HOTFIX 42: announce the drop pool (percentages) + boss difficulty before the callout
        Bukkit.broadcastMessage(C + "5\u00bb A " + cc(d.name()) + C + "5 has appeared in the wild ("
                + C + "e" + (int) l.getX() + ", " + (int) l.getZ() + C + "5)!"
                + C + "8 " + dropsLine(d) + C + "8. HP " + C + "e" + (long) d.hp()
                + C + "8 - " + C + "b/hunt" + C + "8!");
    }

    /** "Drops: 100% 50,000 coins · 60% 3x Lucky Coins · 50% 1x Rare Crate Key" */
    private static String dropsLine(BossDef d) {
        StringBuilder sb = new StringBuilder("Drops: ");
        sb.append(C).append("a100% ").append(String.format("%,d", d.coins())).append(" coins");
        if (d.lucky() > 0 && d.luckyChance() > 0)
            sb.append(" \u00b7 ").append(C).append("a").append(d.luckyChance()).append("% ")
                    .append(d.lucky()).append("x Lucky Coin");
        if (d.crateKeys() > 0 && d.crateChance() > 0)
            sb.append(" \u00b7 ").append(C).append("a").append(d.crateChance()).append("% ")
                    .append(d.crateKeys()).append("x ").append(C + "e" + prettyCrate(d.crate()));
        return sb.toString();
    }

    private static String prettyCrate(String crate) {
        if (crate == null || crate.isEmpty()) return "Crate Key";
        return Character.toUpperCase(crate.charAt(0)) + crate.substring(1).toLowerCase(Locale.ROOT) + " Crate Key";
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

    // ---------------- /hunt ----------------
    private boolean hunt(Player p) {
        if (!huntEnabled) { p.sendMessage(C + "c/hunt is disabled."); return true; }
        Long last = huntCooldowns.get(p.getUniqueId());
        long now = System.currentTimeMillis();
        if (last != null && now - last < huntCooldown * 1000L && huntCooldown > 0) {
            long left = (huntCooldown * 1000L - (now - last)) / 1000L + 1;
            p.sendMessage(C + "cWait " + left + "s - hunt cooldown (" + huntCooldown + "s).");
            return true;
        }
        World w = Bukkit.getWorlds().get(0);
        Location dest = null;
        // a boss is alive -> teleport near it
        List<UUID> live = new ArrayList<>(alive.keySet());
        if (!live.isEmpty()) {
            UUID id = live.get(rnd.nextInt(live.size()));
            var en = Bukkit.getEntity(id);
            if (en != null) {
                Location bl = en.getLocation();
                for (int tries = 0; tries < 30 && dest == null; tries++) {
                    double ang = rnd.nextDouble() * Math.PI * 2;
                    int dist = 8 + rnd.nextInt(Math.max(1, huntBossRadius - 8));
                    int x = bl.getBlockX() + (int) (Math.cos(ang) * dist);
                    int z = bl.getBlockZ() + (int) (Math.sin(ang) * dist);
                    int y = w.getHighestBlockYAt(x, z);
                    if (y < 50) continue;
                    if (w.getBlockAt(x, y, z).getType() == Material.WATER
                            || w.getBlockAt(x, y, z).getType() == Material.LAVA) continue;
                    dest = new Location(w, x + 0.5, y + 1, z + 0.5);
                }
                if (dest != null) {
                    huntCooldowns.put(p.getUniqueId(), now);
                    p.teleport(dest);
                    p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
                    p.sendMessage(C + "5\u00bb Teleported near " + cc(getDef(en).name())
                            + C + "5 at " + C + "e" + bl.getBlockX() + " , " + bl.getBlockZ() + C + "5. Good hunting!");
                    return true;
                }
            }
        }
        // no boss -> just get out of spawn into the wild
        int sx = w.getSpawnLocation().getBlockX(), sz = w.getSpawnLocation().getBlockZ();
        for (int tries = 0; tries < 50 && dest == null; tries++) {
            double ang = rnd.nextDouble() * Math.PI * 2;
            int dist = huntBareMin + rnd.nextInt(Math.max(1, huntBareMax - huntBareMin));
            int x = sx + (int) (Math.cos(ang) * dist);
            int z = sz + (int) (Math.sin(ang) * dist);
            int y = w.getHighestBlockYAt(x, z);
            if (y < 50) continue;
            if (w.getBlockAt(x, y, z).getType() == Material.WATER
                    || w.getBlockAt(x, y, z).getType() == Material.LAVA) continue;
            dest = new Location(w, x + 0.5, y + 1, z + 0.5);
        }
        if (dest == null) { p.sendMessage(C + "cNo safe spot found - try again."); return true; }
        huntCooldowns.put(p.getUniqueId(), now);
        p.teleport(dest);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        p.sendMessage(C + "5\u00bb You are outside spawn at " + C + "e"
                + dest.getBlockX() + " , " + dest.getBlockZ()
                + C + "5 - /miniboss locate shows any live bosses.");
        return true;
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
        // HOTFIX 42: roll the announced drop pool - coins always, lucky/crate by chance
        boolean gotLucky = killer != null && d.lucky() > 0 && d.luckyChance() > 0
                && rnd.nextInt(100) < d.luckyChance();
        boolean gotCrate = killer != null && d.crateKeys() > 0 && d.crateChance() > 0
                && rnd.nextInt(100) < d.crateChance();
        StringBuilder loot = new StringBuilder(C + "e" + String.format("%,d", d.coins()) + " coins");
        if (gotLucky) loot.append(" + ").append(C).append("e").append(d.lucky()).append("x Lucky Coin");
        if (gotCrate) loot.append(" + ").append(C).append("e").append(d.crateKeys()).append("x ")
                .append(prettyCrate(d.crate()));
        Bukkit.broadcastMessage(C + "5\u00bb " + cc(d.name()) + C + "5 was slain by " + C + "a" + killerName
                + C + "5! Loot: " + loot + C + "5.");
        if (killer == null) return;
        if (econ != null && d.coins() > 0) econ.depositPlayer(killer, d.coins());
        if (gotLucky) {
            try {
                org.bukkit.plugin.Plugin luck = Bukkit.getPluginManager().getPlugin("MAVOLuckyCoins");
                if (luck != null)
                    luck.getClass().getMethod("giveCoins", Player.class, int.class).invoke(luck, killer, d.lucky());
            } catch (Throwable ignored) { }
        }
        if (gotCrate) {
            try {
                Class.forName("mavo.crates.Crates").getMethod("giveKey", Player.class, String.class, int.class)
                        .invoke(null, killer, d.crate(), d.crateKeys());
            } catch (Throwable ignored) { }
        }
        ItemStack head = new ItemStack(d.head());
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setDisplayName(cc(d.name()) + C + " Trophy");
        sm.setLore(List.of(C + "7Slain by the MAVOcraft community.", C + "7A rare collectible."));
        head.setItemMeta(sm);
        var left = killer.getInventory().addItem(head);
        for (ItemStack it : left.values()) killer.getWorld().dropItemNaturally(killer.getLocation(), it);
        killer.playSound(killer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.3f);
        StringBuilder prize = new StringBuilder(C + "e" + String.format("%,d", d.coins()) + " coins");
        if (gotLucky) prize.append(C + "a + ").append(C + "e").append(d.lucky()).append("x Lucky Coin");
        if (gotCrate) prize.append(C + "a + ").append(C + "e").append(d.crateKeys()).append("x ")
                .append(prettyCrate(d.crate()));
        killer.sendMessage(C + "aYour prize: " + prize + C + "a + " + C + "e"
                + d.head().name().toLowerCase(Locale.ROOT).replace('_', ' ') + C + "a!");
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("hunt")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
            return hunt(p);
        }
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                sender.sendMessage(C + "5\u00bb Minibosses: " + C + "e" + alive.size() + "/" + maxAlive
                        + C + "5 alive, " + C + "e" + defs.size() + C + "5 types, every " + intervalMin
                        + " min. /hunt: " + (huntEnabled ? C + "aON" : C + "cOFF")
                        + C + "5, locations broadcast every " + broadcastInterval + " min.");
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
            case "broadcast" -> {
                if (alive.isEmpty()) spawnOne();     // /miniboss broadcast summons a boss if none is up
                broadcastLocations();
            }
            case "reload" -> {
                if (!sender.hasPermission("mavominiboss.admin")) { sender.sendMessage("OP only."); return true; }
                reloadConfig(); load();
                sender.sendMessage(C + "aReloaded " + defs.size() + " boss types.");
            }
            default -> sender.sendMessage(C + "7/hunt | /miniboss status | locate | broadcast (OP: reload)");
        }
        return true;
    }

    private BossDef getDef(org.bukkit.entity.Entity en) {
        String k = en.getPersistentDataContainer().get(bossType, PersistentDataType.STRING);
        return defs.getOrDefault(k, new BossDef("&cBOSS", EntityType.WITCH, 100, 0, 0, 0, "", 0, 0, Material.PLAYER_HEAD));
    }

    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
