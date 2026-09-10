package mavo.miniboss;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
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
import org.bukkit.configuration.file.YamlConfiguration;
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

/** MAVOMiniboss 1.0.0 - arena bosses (Discord CW#4 idea 17, inspired by MythicMobs/Elitemobs).
 *  Every 45 min a random boss spawns at one of the config "spawn-locations" arenas
 *  (Hotfix 44: 2 fixed locations instead of random wild spots, up to 3 alive).
 *  Bosses are tagged, hit harder (config HP), and drop coins + Lucky Coins + crate keys +
 *  a trophy head to the killer (Vault / reflection into MAVOLuckyCoins + MAVOCrates).
 *  Hotfix 35: /hunt teleports you out of spawn (next to a live boss if one is up - 30s
 *  cooldown) and boss locations are broadcast to chat every 5 minutes. */
public final class MiniBoss extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';
    private final NamespacedKey tag = new NamespacedKey("mavominiboss", "boss");
    private final NamespacedKey bossType = new NamespacedKey("mavominiboss", "type");

    private Economy econ;
    private int intervalMin = 45, maxAlive = 3;
    private boolean surfaceOnly = true;
    private final List<SpawnArena> arenas = new ArrayList<>();
    private boolean huntEnabled = true;
    private int huntCooldown = 30;              // seconds
    private int huntBareMin = 500, huntBareMax = 2000;
    private int huntBossRadius = 200;           // HOTFIX 42: land ~200 blocks from a live boss
    private boolean broadcastLocations = true;
    private int broadcastInterval = 5;          // minutes
    private int broadcastRadius = 100;
    private int bcCounter = 0;                  // minutes since last location broadcast
    private final Map<UUID, Long> huntCooldowns = new HashMap<>();
    // 3.0.5 schedule state (persisted in config.yml schedule-state, survives restarts)
    private int spawnTick = 4000;                 // MC 10:00 - fresh bosses, both sides
    private int despawnTick = 1000;               // MC 7:00 - all bosses vanish, no loot
    private boolean scheduleOnlyOnline = true;    // no spawns on an empty server
    private int broadcastCmdCooldown = 60;        // anti-spam on /miniboss broadcast
    private final Map<String, Long> sideKillDay = new HashMap<>(); // arena -> MC day of last kill
    private long lastDespawnDay = -1;
    private final Map<UUID, Long> broadcastCooldowns = new HashMap<>();
    private final Map<String, BossDef> defs = new HashMap<>();
    private final Map<UUID, String> alive = new HashMap<>();
    private final Map<UUID, String> arenaOf = new HashMap<>();  // HOTFIX 44: arena a boss spawned in
    private final Random rnd = new Random();

    // HOTFIX 42: drop chances (percent) per boss - announced on spawn, rolled on kill
    private record BossDef(String name, EntityType type, double hp, long coins,
                           int lucky, int luckyChance, String crate, int crateKeys,
                           int crateChance, Material head) {}

    // HOTFIX 44: named spawn arena from config (center + optional jitter in blocks)
    private record SpawnArena(String name, String world, int x, int z, int jitter) {}
    private record SpawnSpot(Location loc, SpawnArena arena) {}

    @Override public void onEnable() {
        saveDefaultConfig();
        mergeBossRoster();                     // MUST run before copyDefaults(): the bundled
                                               // bosses-version key would otherwise seed into
                                               // old configs and fake "already upgraded"
        getConfig().options().copyDefaults(true);
        saveConfig();                          // adds /hunt + broadcast keys to existing configs
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        load();
        loadScheduleState();
        cleanOrphanBosses();   // 3.0.5: crash leftovers never linger (schedule respawns)
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 200L, 1200L);   // once a minute
        tick();
        getLogger().info("MAVOMiniboss v" + getDescription().getVersion() + " enabled - " + defs.size()
                + " boss type(s), " + arenas.size() + " arena(s), schedule 10:00 spawn / 7:00 despawn,"
                + " 1 per side, /hunt " + (huntEnabled ? "on" : "off")
                + ", location broadcast every " + broadcastInterval + " min.");
    }

    @Override public void onDisable() {
        for (UUID id : alive.keySet()) {
            var en = Bukkit.getEntity(id);
            if (en != null) en.remove();
        }
        alive.clear();
        arenaOf.clear();
    }

    /** HOTFIX 44: an existing config.yml already has a "bosses" section with only
     *  the 5 old defs - copyDefaults() cannot add ids inside it. Merge every
     *  bundled boss id the disk file is missing.
     *  3.0.3 REBALANCE: old live-tuned stats (90k coins, 45% mythic keys, low HP)
     *  were far too rich - bosses-version < 2 forces the WHOLE new table.
     *  3.0.4 FIX: in 3.0.3 the check ran AFTER copyDefaults(), so the bundled
     *  "bosses-version: 2" key was seeded into old configs first and the merge
     *  skipped the real replacement (old 90k values survived while claiming v2).
     *  Gen 3 > every config a 3.0.3 boot could have touched. */
    private void mergeBossRoster() {
        File f = new File(getDataFolder(), "config.yml");
        try {
            YamlConfiguration disk = YamlConfiguration.loadConfiguration(f);
            InputStream in = getResource("config.yml");
            if (in == null) return;
            YamlConfiguration def = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            ConfigurationSection dcs = def.getConfigurationSection("bosses");
            if (dcs == null) return;
            int added = 0;
            for (String id : dcs.getKeys(false)) {
                if (!disk.isConfigurationSection("bosses." + id)) {
                    disk.set("bosses." + id, def.get("bosses." + id));
                    added++;
                }
            }
            if (disk.getInt("bosses-version", 0) < 3) {
                disk.set("bosses-version", 3);
                disk.set("bosses", def.get("bosses"));
                disk.save(f);
                getLogger().info("3.0.4: boss table replaced - hard hunts (HP x2, damage x1.5), "
                        + "coins/key drops scaled down to event-fair values.");
            } else if (added > 0) {
                disk.save(f);
                getLogger().info("Hotfix 44: " + added + " new boss type(s) added to config.yml.");
            }
            reloadConfig();
        } catch (Throwable t) {
            getLogger().warning("boss roster merge failed: " + t.getMessage());
        }
    }

    /** 3.0.3: boss attack damage multiplier from config (default 1.5). */
    private double damageMult() {
        return Math.max(1.0, getConfig().getDouble("boss-damage-multiplier", 1.5));
    }

    private void load() {
        defs.clear();
        arenas.clear();
        intervalMin = Math.max(5, getConfig().getInt("spawn-interval-minutes", 45));
        maxAlive = Math.max(1, getConfig().getInt("max-alive", 3));
        surfaceOnly = getConfig().getBoolean("spawn-surface", true);
        // HOTFIX 44: bosses spawn at these fixed arenas (config-driven, /miniboss reload).
        for (var sec : getConfig().getMapList("spawn-locations")) {
            try {
                @SuppressWarnings("unchecked")
                Map<String, Object> m = (Map<String, Object>) sec;
                String w = String.valueOf(m.getOrDefault("world", "world"));
                String nm = String.valueOf(m.getOrDefault("name", "&cARENA"));
                double dx = m.containsKey("x") ? ((Number) m.get("x")).doubleValue() : 0;
                double dz = m.containsKey("z") ? ((Number) m.get("z")).doubleValue() : 0;
                int jit = Math.max(0, (int) (m.containsKey("jitter") ? ((Number) m.get("jitter")).doubleValue() : 0));
                arenas.add(new SpawnArena(nm, w, (int) dx, (int) dz, jit));
            } catch (Throwable t) {
                getLogger().warning("bad spawn-location entry skipped: " + t.getMessage());
            }
        }
        if (arenas.isEmpty())
            getLogger().warning("spawn-locations is empty - bosses cannot spawn (check config.yml).");
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
        scheduleCheck();
        // boss location broadcast every N minutes (3.0.5: never spawns - schedule owns that)
        if (broadcastLocations) {
            if (++bcCounter >= broadcastInterval) {
                bcCounter = 0;
                if (!alive.isEmpty()) broadcastLocations();
            }
        }
    }

    /** 3.0.5 schedule: despawn at 7:00, spawn at 10:00 (1 per side, players online). */
    private void scheduleCheck() {
        World w = scheduleWorld();
        if (w == null || defs.isEmpty() || arenas.isEmpty()) return;
        long day = w.getFullTime() / 24000L;
        int t = (int) (w.getTime() % 24000L);
        // 7:00 - every boss vanishes (no loot, no kill cooldown: they were not slain)
        if (t >= despawnTick && lastDespawnDay != day) {
            lastDespawnDay = day;
            despawnAll();
            saveScheduleState();
        }
        // 10:00 - fresh boss per side (skipped while the server is empty)
        if (t < spawnTick) return;
        if (scheduleOnlyOnline && Bukkit.getOnlinePlayers().isEmpty()) return;
        for (SpawnArena a : arenas) {
            if (aliveIn(a.name()) > 0) continue;                 // 1 per side max
            if (sideKillDay.getOrDefault(a.name(), -1L) >= day) continue; // killed today: wait for next 10:00
            spawnForArena(a);
        }
    }

    private World scheduleWorld() {
        for (SpawnArena a : arenas) {
            World w = Bukkit.getWorld(a.world());
            if (w != null) return w;
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    private int aliveIn(String arenaName) {
        int n = 0;
        for (String a : arenaOf.values()) if (arenaName.equals(a)) n++;
        return n;
    }

    private void despawnAll() {
        if (alive.isEmpty()) return;
        for (UUID id : new ArrayList<>(alive.keySet())) {
            var en = Bukkit.getEntity(id);
            if (en != null) en.remove();   // remove() fires no death event: no loot, no kill credit
        }
        alive.clear();
        arenaOf.clear();
        Bukkit.broadcastMessage(C + "7\u00bb The minibosses have vanished with the dawn. "
                + C + "eFresh hunts at 10:00" + C + "7!");
    }

    /** 3.0.5: entities tagged by a previous run (crash: onDisable never ran) are removed. */
    private void cleanOrphanBosses() {
        int n = 0;
        for (World w : Bukkit.getWorlds()) {
            for (LivingEntity e : w.getLivingEntities()) {
                if (e.getPersistentDataContainer().has(tag, PersistentDataType.BYTE)) {
                    e.remove();
                    n++;
                }
            }
        }
        if (n > 0) getLogger().info("3.0.5: removed " + n + " leftover boss entit(y/ies) from a previous run.");
    }

    private void loadScheduleState() {
        lastDespawnDay = getConfig().getLong("schedule-state.last-despawn-day", -1L);
        ConfigurationSection s = getConfig().getConfigurationSection("schedule-state.side-kill-day");
        if (s != null) for (String k : s.getKeys(false)) sideKillDay.put(k, s.getLong(k, -1L));
    }

    private void saveScheduleState() {
        getConfig().set("schedule-state.last-despawn-day", lastDespawnDay);
        for (Map.Entry<String, Long> e : sideKillDay.entrySet())
            getConfig().set("schedule-state.side-kill-day." + e.getKey(), e.getValue());
        saveConfig();
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        // a join right after 10:00 on an empty server should not wait a full minute
        Bukkit.getScheduler().runTaskLater(this, this::scheduleCheck, 100L);
    }

    private void broadcastLocations() {
        for (UUID id : alive.keySet()) {
            var en = Bukkit.getEntity(id);
            if (en == null) continue;
            Location l = en.getLocation();
            String arena = arenaOf.containsKey(id) ? cc(arenaOf.get(id)) : "";
            Bukkit.broadcastMessage(C + "5\u00bb " + cc(getDef(en).name()) + C + "8 is at "
                    + (arena.isEmpty() ? "" : "the " + arena + C + "8 ")
                    + C + "e" + l.getBlockX() + " , " + l.getBlockZ()
                    + C + "8 (\u00b1" + broadcastRadius + " blocks) - " + C + "b/hunt" + C + "8 to go hunting!");
        }
    }

    /** 3.0.5: spawn one random boss at a GIVEN arena (schedule calls this per side). */
    private void spawnForArena(SpawnArena arena) {
        if (defs.isEmpty()) return;
        SpawnSpot spot = pickSpot(arena);
        if (spot == null) return;
        World w = spot.loc().getWorld();
        Location l = spot.loc();
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
            // 3.0.3: bosses hit harder (default x1.5) - "hard hunt", not free money
            org.bukkit.attribute.AttributeInstance atk =
                    e.getAttribute(Attribute.ATTACK_DAMAGE);
            if (atk != null)
                atk.setBaseValue(atk.getBaseValue() * damageMult());
        } catch (Throwable ignored) { }
        e.getPersistentDataContainer().set(tag, PersistentDataType.BYTE, (byte) 1);
        e.getPersistentDataContainer().set(bossType, PersistentDataType.STRING, key);
        alive.put(e.getUniqueId(), key);
        arenaOf.put(e.getUniqueId(), spot.arena().name());
        // HOTFIX 42 + 44: announce the arena + drop pool (percentages) + boss HP
        Bukkit.broadcastMessage(C + "5\u00bb A " + cc(d.name()) + C + "5 has appeared at the "
                + cc(spot.arena().name()) + C + "5 (" + C + "e" + (int) l.getX() + ", " + (int) l.getZ()
                + C + "5)!" + C + "8 " + dropsLine(d) + C + "8. HP " + C + "e" + (long) d.hp()
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

    /** HOTFIX 44: pick a spawn point at one of the config arenas (random arena,
     *  random offset inside its jitter radius, surface only). Falls back to the
     *  arena centre so a boss always reaches its announced arena. */
    private SpawnSpot pickSpot() {
        if (arenas.isEmpty()) return null;
        World fallback = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        for (int round = 0; round < 5; round++) {
            SpawnArena a = arenas.get(rnd.nextInt(arenas.size()));
            World w = Bukkit.getWorld(a.world());
            if (w == null) w = fallback;
            if (w == null) return null;
            int ax = a.x(), az = a.z();
            for (int tries = 0; tries < 40; tries++) {
                int j = a.jitter();
                int x = ax + (j > 0 ? rnd.nextInt(j * 2 + 1) - j : 0);
                int z = az + (j > 0 ? rnd.nextInt(j * 2 + 1) - j : 0);
                int y = w.getHighestBlockYAt(x, z);
                if (surfaceOnly && y < 50) continue;
                return new SpawnSpot(new Location(w, x + 0.5, y + 1, z + 0.5), a);
            }
            int y = w.getHighestBlockYAt(ax, az);
            if (y >= 0)
                return new SpawnSpot(new Location(w, ax + 0.5, Math.max(50, y) + 1, az + 0.5), a);
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
        arenaOf.remove(en.getUniqueId());
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
                sender.sendMessage(C + "5\u00bb Minibosses: " + C + "e" + alive.size()
                        + C + "5 alive (1 per side), " + C + "e" + defs.size() + C + "5 types."
                        + C + "7 Spawn 10:00 both sides, vanish 7:00, killed side rests till next 10:00.");
                sender.sendMessage(C + "5/hunt: " + (huntEnabled ? C + "aON" : C + "cOFF")
                        + C + "5, locations broadcast every " + broadcastInterval + " min.");
                World sw = scheduleWorld();
                long day = sw == null ? -1 : sw.getFullTime() / 24000L;
                for (SpawnArena a : arenas) {
                    String state;
                    if (aliveIn(a.name()) > 0) state = C + "aHUNTABLE NOW";
                    else if (sideKillDay.getOrDefault(a.name(), -1L) >= day && day >= 0)
                        state = C + "cslain - back next 10:00";
                    else state = C + "espawns 10:00";
                    sender.sendMessage(C + "7 - " + cc(a.name()) + C + "7 (" + a.x() + "," + a.z()
                            + ") " + state);
                }
                for (UUID id : alive.keySet()) {
                    var en = Bukkit.getEntity(id);
                    if (en != null) sender.sendMessage(C + "7 - " + cc(getDef(en).name()) + C + "7 at "
                            + (arenaOf.containsKey(id) ? "the " + cc(arenaOf.get(id)) + C + "7 " : "")
                            + en.getLocation().getBlockX() + "," + en.getLocation().getBlockZ());
                }
            }
            case "locate" -> {
                if (alive.isEmpty()) { sender.sendMessage(C + "7No miniboss alive right now."); return true; }
                for (UUID id : alive.keySet()) {
                    var en = Bukkit.getEntity(id);
                    if (en != null) sender.sendMessage(C + "7 - " + cc(getDef(en).name()) + C + "7 at "
                            + (arenaOf.containsKey(id) ? "the " + cc(arenaOf.get(id)) + C + "7 " : "")
                            + en.getLocation().getBlockX() + " " + en.getLocation().getBlockZ());
                }
            }
            case "broadcast" -> {
                // 3.0.5: broadcast-only (never spawns - that was an unlimited-farm hole),
                // short per-player cooldown so chat can't be spammed.
                if (sender instanceof Player bp && broadcastCmdCooldown > 0) {
                    long now = System.currentTimeMillis();
                    Long last = broadcastCooldowns.get(bp.getUniqueId());
                    if (last != null && now - last < broadcastCmdCooldown * 1000L) {
                        long left = (broadcastCmdCooldown * 1000L - (now - last)) / 1000L + 1;
                        bp.sendMessage(C + "cWait " + left + "s - broadcast cooldown.");
                        return true;
                    }
                    broadcastCooldowns.put(bp.getUniqueId(), now);
                }
                if (alive.isEmpty()) {
                    sender.sendMessage(C + "7No miniboss alive right now - fresh hunts spawn at "
                            + C + "e10:00" + C + "7 on both sides.");
                    return true;
                }
                broadcastLocations();
            }
            case "reload" -> {
                if (!sender.hasPermission("mavominiboss.admin")) { sender.sendMessage("OP only."); return true; }
                reloadConfig(); load();
                sender.sendMessage(C + "aReloaded " + defs.size() + " boss types, " + arenas.size() + " arenas.");
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
