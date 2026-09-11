package mavo.bossraid;

import java.io.File;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * MAVOBossRaid 1.0.0 - weekly server-wide boss raid (Discord CW#3 idea 3).
 * - /boss join queue opens 10 min before the scheduled raid (default Sat 20:00)
 * - teleports the queue to the arena, spawns ONE scaled boss (hp base + per player)
 * - rewards: coins per participant + damage bonus, Lucky Coins, a trophy, a crate key
 */
public final class BossRaid extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private Economy econ;
    private ZoneId zone = ZoneId.of("Europe/London");
    private Location arena;
    private int spawnRadius = 12;
    private boolean scheduleOn = true;
    private DayOfWeek raidDay = DayOfWeek.SATURDAY;
    private LocalTime raidTime = LocalTime.of(20, 0);
    private int joinOpenMin = 10;
    private boolean requireQueue = true;
    private EntityType bossType = EntityType.ZOMBIE;
    private String bossName = "MAVO WARDEN OF THE WILDS";
    private double hpBase = 2000, hpPerPlayer = 100;
    private boolean noAi = false;
    private long coinsPerPlayer = 2500, coinsPer1k = 50;
    private int luckyCoins = 2;
    private Material trophyMat = Material.GOLD_BLOCK;
    private String trophyName = "MAVO BOSS TROPHY";
    private String crateKey = "mythic";
    private int crateKeys = 1;

    private final Set<UUID> queue = new LinkedHashSet<>();
    private final Map<UUID, Double> damage = new HashMap<>();
    private LivingEntity boss;
    private BossBar bar;
    private long raidEndAt = 0;          // when the current raid started + safety window
    private boolean joinOpen = false;
    private long nextRaidAt = 0;         // epoch ms of the next scheduled raid
    private BukkitTask timer;

    @Override public void onEnable() {
        saveDefaultConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        loadCfg();
        getServer().getPluginManager().registerEvents(this, this);
        timer = Bukkit.getScheduler().runTaskTimer(this, this::tick, 40L, 400L); // every 20s
        tick();
        getLogger().info("MAVOBossRaid v" + getDescription().getVersion() + " enabled - next raid "
                + nextRaidText() + " arena=" + (arena == null ? "NOT SET (/boss setarena)" : arena.getBlockX()
                + "," + arena.getBlockY() + "," + arena.getBlockZ()));
    }

    @Override public void onDisable() {
        if (timer != null) timer.cancel();
        endRaid(true);
    }

    private void loadCfg() {
        try { zone = ZoneId.of(getConfig().getString("timezone", "Europe/London")); }
        catch (Throwable t) { zone = ZoneId.of("Europe/London"); }
        World w = Bukkit.getWorld(getConfig().getString("world", "world"));
        ConfigurationSection a = getConfig().getConfigurationSection("arena");
        arena = w != null && a != null
                ? new Location(w, a.getInt("x") + 0.5, a.getInt("y"), a.getInt("z") + 0.5) : null;
        spawnRadius = Math.max(3, getConfig().getInt("spawn-radius", 12));
        scheduleOn = getConfig().getBoolean("schedule.enabled", true);
        try { raidDay = DayOfWeek.valueOf(getConfig().getString("schedule.day-of-week", "SATURDAY").toUpperCase(Locale.ROOT)); }
        catch (Throwable t) { raidDay = DayOfWeek.SATURDAY; }
        try { raidTime = LocalTime.parse(getConfig().getString("schedule.hour", "20:00")); }
        catch (Throwable t) { raidTime = LocalTime.of(20, 0); }
        joinOpenMin = Math.max(1, getConfig().getInt("schedule.join-open-minutes", 10));
        requireQueue = getConfig().getBoolean("schedule.require-queue", true);
        try { bossType = EntityType.valueOf(getConfig().getString("boss.type", "ZOMBIE").toUpperCase(Locale.ROOT)); }
        catch (Throwable t) { bossType = EntityType.ZOMBIE; }
        bossName = getConfig().getString("boss.name", "MAVO WARDEN OF THE WILDS");
        hpBase = Math.max(1, getConfig().getDouble("boss.hp-base", 2000));
        hpPerPlayer = Math.max(0, getConfig().getDouble("boss.hp-per-player", 100));
        noAi = getConfig().getBoolean("boss.no-ai", false);
        coinsPerPlayer = Math.max(0, getConfig().getLong("rewards.coins-per-player", 2500));
        coinsPer1k = Math.max(0, getConfig().getLong("rewards.coins-per-1000-damage", 50));
        luckyCoins = Math.max(0, getConfig().getInt("rewards.lucky-coins", 2));
        trophyMat = Material.matchMaterial(getConfig().getString("rewards.trophy", "GOLD_BLOCK"));
        if (trophyMat == null) trophyMat = Material.GOLD_BLOCK;
        trophyName = getConfig().getString("rewards.trophy-name", "MAVO BOSS TROPHY");
        crateKey = getConfig().getString("rewards.crate-key", "mythic");
        crateKeys = Math.max(0, getConfig().getInt("rewards.crate-keys", 1));
    }

    // ---------------- schedule ----------------
    private ZonedDateTime nextRaid(ZonedDateTime now) {
        ZonedDateTime t = ZonedDateTime.of(now.toLocalDate(), raidTime, zone);
        long add = (raidDay.getValue() - t.getDayOfWeek().getValue() + 7) % 7;
        t = t.plusDays(add);
        if (!t.isAfter(now) || (t.isEqual(now) && !isRunning())) t = t.plusDays(7);
        return t;
    }

    private String nextRaidText() {
        if (isRunning()) return "ACTIVE NOW";
        if (!scheduleOn || arena == null) return "disabled";
        ZonedDateTime n = nextRaid(ZonedDateTime.now(zone));
        return n.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + n.getHour()
                + ":" + String.format("%02d", n.getMinute());
    }

    private boolean isRunning() { return boss != null && !boss.isDead(); }

    private void tick() {
        if (!isRunning() && scheduleOn && arena != null) {
            ZonedDateTime now = ZonedDateTime.now(zone);
            ZonedDateTime next = nextRaid(now);
            ZonedDateTime joinAt = next.minusMinutes(joinOpenMin);
            if (!joinOpen && now.isAfter(joinAt) && now.isBefore(next)) {
                joinOpen = true;
                queue.clear();
                Bukkit.broadcastMessage(C + "c" + C + "l\u2694 BOSS RAID " + C + "e" + C + "lJOIN NOW "
                        + C + "7- " + C + "a/boss join" + C + "7, starts in " + C + "e"
                        + Duration.between(now, next).toMinutes() + C + "7 minutes!");
                for (Player p : Bukkit.getOnlinePlayers())
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_ENDER_DRAGON_GROWL, 0.8f, 1f);
            }
            if (now.isAfter(next) && !now.isAfter(next.plusSeconds(90))) {
                startRaid(false);
            }
        }
        // safety: a raid cannot run forever (boss boxed / no players)
        if (isRunning() && raidEndAt > 0 && System.currentTimeMillis() > raidEndAt) {
            Bukkit.broadcastMessage(C + "cThe raid timed out after 45 minutes - ending it.");
            endRaid(false);
        }
        // 3.0.6 self-heal: a bar with no running raid is a leak (boss lost
        // without its death event) - clear it instead of haunting screens.
        if (bar != null && !isRunning()) { bar.removeAll(); bar = null; }
        if (isRunning() && bar != null && boss != null) {
            double frac = Math.max(0, boss.getHealth() / boss.getMaxHealth());
            bar.setProgress((float) Math.min(1.0, frac));
            bar.setTitle(C + "c" + C + "l\u2694 " + cc(bossName) + C + "c" + C + "l  "
                    + (int) boss.getHealth() + " / " + (int) boss.getMaxHealth() + " HP"
                    + C + "7 (" + queue.size() + " raiders)");
        }
    }

    private void startRaid(boolean adminNow) {
        if (arena == null) { Bukkit.broadcastMessage(C + "cBoss raid arena is not set - /boss setarena!"); return; }
        List<Player> raiders = new ArrayList<>();
        for (UUID u : queue) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) raiders.add(p);
        }
        if (raiders.isEmpty()) {
            if (requireQueue && !adminNow) {
                Bukkit.broadcastMessage(C + "c\u2694 Boss raid skipped - nobody joined the queue. Next one: "
                        + C + "e" + nextRaidText() + C + "7.");
                joinOpen = false;
                return;
            }
            if (adminNow) raiders.addAll(Bukkit.getOnlinePlayers());
            else for (Player p : Bukkit.getOnlinePlayers()) raiders.add(p);
        }
        World w = arena.getWorld();
        int n = raiders.size();
        double hp = hpBase + hpPerPlayer * Math.max(0, n);
        // teleport raiders in a circle facing the boss
        for (int i = 0; i < n; i++) {
            Player p = raiders.get(i);
            double ang = 2 * Math.PI * i / Math.max(1, n);
            Location to = arena.clone().add(Math.cos(ang) * spawnRadius, 1, Math.sin(ang) * spawnRadius);
            to.setYaw((float) Math.toDegrees(Math.atan2(arena.getX() - to.getX(), arena.getZ() - to.getZ())));
            to.setPitch(4f);
            p.teleport(to);
            p.sendMessage(C + "c" + C + "l\u2694 " + C + "7Boss raid started - the "
                    + C + "e" + cc(bossName) + C + "7 has " + C + "c" + (long) hp + " HP&7. Fight together!");
            p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
        }
        LivingEntity b = (LivingEntity) w.spawnEntity(arena, bossType);
        b.setCustomName(cc(bossName));
        b.setCustomNameVisible(true);
        b.setPersistent(true);
        b.setRemoveWhenFarAway(false);
        if (noAi) b.setAI(false);
        try { b.getAttribute(Attribute.MAX_HEALTH).setBaseValue(hp); b.setHealth(hp); } catch (Throwable t) { }
        // 3.0.6: a previous bar/boss left over (boss lost without death event)
        // used to be orphaned here - the old bar stayed on screens forever.
        if (bar != null) { bar.removeAll(); bar = null; }
        if (boss != null && boss != b) { try { boss.remove(); } catch (Throwable ignored) { } }
        boss = b;
        damage.clear();
        raidEndAt = System.currentTimeMillis() + 45L * 60_000L;
        bar = Bukkit.createBossBar(cc(bossName), BarColor.RED, BarStyle.SOLID);
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        Bukkit.broadcastMessage(C + "c" + C + "l\u2694 " + C + "eBOSS RAID STARTED"
                + C + "7 - " + n + " raider(s), " + C + "c" + (long) hp + " HP" + C + "7. Type "
                + C + "a/boss return" + C + "7 if you die!");
        joinOpen = false;
        queue.clear();
        queue: for (Player p : raiders) queue.add(p.getUniqueId());
    }

    private void finishRaid() {
        List<UUID> raiders = new ArrayList<>(queue);
        // payout: every participant + damage bonus
        double totalDmg = 0;
        for (double d : damage.values()) totalDmg += d;
        UUID top = null; double topDmg = -1;
        for (Map.Entry<UUID, Double> en : damage.entrySet())
            if (en.getValue() > topDmg) { topDmg = en.getValue(); top = en.getKey(); }
        for (UUID u : raiders) {
            Player p = Bukkit.getPlayer(u);
            if (p == null || !p.isOnline()) continue;
            long pay = coinsPerPlayer;
            if (coinsPer1k > 0) pay += (long) (damage.getOrDefault(u, 0.0) / 1000.0 * coinsPer1k);
            if (pay > 0 && econ != null && econ.depositPlayer(p, pay).transactionSuccess())
                p.sendMessage(C + "aBOSS down! You earned " + C + "e" + String.format("%,d", pay) + " coins"
                        + C + "a.");
            if (luckyCoins > 0) giveLucky(p, luckyCoins);
            if (trophyMat != null && u.equals(top) && topDmg > 0)
                p.getInventory().addItem(trophy());
            if (!crateKey.isEmpty() && crateKeys > 0) giveCrateKey(p, crateKey, crateKeys);
        }
        if (top != null) {
            String topName = Bukkit.getOfflinePlayer(top).getName();
            Bukkit.broadcastMessage(C + "c" + C + "l\u2694 " + C + "dBOSS RAID COMPLETE! "
                    + C + "7MVP: " + C + "e" + (topName == null ? "?" : topName) + C + "7 ("
                    + C + "e" + (long) topDmg + " damage" + C + "7). Loot sent to all raiders.");
        }
        endRaid(false);
    }

    private ItemStack trophy() {
        ItemStack it = new ItemStack(trophyMat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(cc(trophyName));
        m.setLore(List.of(C + "7Earned in a MAVO boss raid.", C + "7Wear it with pride."));
        it.setItemMeta(m);
        return it;
    }

    private void giveLucky(Player p, int n) {
        try {
            org.bukkit.plugin.Plugin luck = Bukkit.getPluginManager().getPlugin("MAVOLuckyCoins");
            if (luck == null) return;
            luck.getClass().getMethod("giveCoins", Player.class, int.class).invoke(luck, p, n);
        } catch (Throwable ignored) { }
    }
    private void giveCrateKey(Player p, String crate, int n) {
        try {
            Class<?> c = Class.forName("mavo.crates.Crates");
            c.getMethod("giveKey", Player.class, String.class, int.class).invoke(null, p, crate, n);
        } catch (Throwable ignored) { }
    }

    private void endRaid(boolean quiet) {
        if (bar != null) bar.removeAll();
        bar = null;
        if (boss != null) { try { if (!boss.isDead()) boss.remove(); } catch (Throwable ignored) { } boss = null; }
        raidEndAt = 0;
        damage.clear();
        if (!quiet) queue.clear();
    }

    // ---------------- events ----------------
    @EventHandler
    public void onHurt(EntityDamageByEntityEvent e) {
        if (!isRunning() || e.getEntity() != boss) return;
        if (!(e.getDamager() instanceof Player p)) return;
        damage.merge(p.getUniqueId(), Math.min(e.getFinalDamage(), boss.getHealth()), Double::sum);
    }

    @EventHandler
    public void onBossDeath(EntityDeathEvent e) {
        if (!isRunning() || e.getEntity() != boss) return;
        e.setDroppedExp(0);
        e.getDrops().clear();
        finishRaid();
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { if (bar != null) bar.addPlayer(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { if (bar != null) bar.removePlayer(e.getPlayer()); }

    // ---------------- commands ----------------
    @Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("join", "leave", "return", "status"));
            if (sender.hasPermission("mavoboss.admin")) out.addAll(List.of("setarena", "now", "cancel", "reload"));
            return out;
        }
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                if (isRunning()) {
                    sender.sendMessage(C + "c\u2694 Raid ACTIVE - " + cc(bossName) + " ("
                            + (int) boss.getHealth() + "/" + (int) boss.getMaxHealth() + " HP), "
                            + queue.size() + " raiders. " + C + "a/boss return" + C + "7 to rejoin the arena.");
                } else if (joinOpen) {
                    sender.sendMessage(C + "e\u2694 Join window OPEN! " + C + "a/boss join" + C + "7 - "
                            + nextRaidText() + ".");
                } else {
                    sender.sendMessage(C + "7\u2694 Next boss raid: " + C + "e" + nextRaidText()
                            + C + "7. Join window opens " + C + "e" + joinOpenMin + " min before ("
                            + C + "a/boss join" + C + "7). Arena: "
                            + (arena == null ? "NOT SET" : arena.getBlockX() + "," + arena.getBlockY() + "," + arena.getBlockZ()));
                }
            }
            case "join" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                if (isRunning()) { p.sendMessage(C + "cThe raid is already running - use " + C + "e/boss return" + C + "c."); return true; }
                if (!joinOpen && scheduleOn) { p.sendMessage(C + "cNot open yet - next raid " + C + "e" + nextRaidText() + C + "c."); return true; }
                if (queue.add(p.getUniqueId())) {
                    p.sendMessage(C + "aAdded to the raid queue (" + queue.size() + " raider(s)). See you at "
                            + C + "e" + nextRaidText() + C + "a!");
                    p.playSound(p.getLocation(), org.bukkit.Sound.ENTITY_PLAYER_LEVELUP, 0.8f, 1.4f);
                } else p.sendMessage(C + "7You are already queued.");
            }
            case "leave" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                if (queue.remove(p.getUniqueId())) p.sendMessage(C + "7Left the raid queue.");
                else p.sendMessage(C + "7You are not in the queue.");
            }
            case "return" -> {
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                if (!isRunning() || arena == null) { p.sendMessage(C + "7No raid running."); return true; }
                p.teleport(arena.clone().add(0, 1, 0));
                p.sendMessage(C + "aBack in the arena - good luck!");
            }
            case "setarena" -> {
                if (!sender.hasPermission("mavoboss.admin")) { sender.sendMessage("OP only."); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                getConfig().set("world", p.getWorld().getName());
                getConfig().set("arena.x", p.getLocation().getBlockX());
                getConfig().set("arena.y", p.getLocation().getBlockY());
                getConfig().set("arena.z", p.getLocation().getBlockZ());
                saveConfig();
                loadCfg();
                p.sendMessage(C + "aBoss arena set at " + arena.getBlockX() + " " + arena.getBlockY()
                        + " " + arena.getBlockZ() + ". Next raid " + C + "e" + nextRaidText() + C + "a.");
            }
            case "now" -> {
                if (!sender.hasPermission("mavoboss.admin")) { sender.sendMessage("OP only."); return true; }
                if (isRunning()) { sender.sendMessage(C + "cA raid is already running."); return true; }
                if (arena == null) { sender.sendMessage(C + "cSet the arena first: /boss setarena"); return true; }
                if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
                queue.add(p.getUniqueId());
                startRaid(true);
            }
            case "cancel" -> {
                if (!sender.hasPermission("mavoboss.admin")) { sender.sendMessage("OP only."); return true; }
                endRaid(false);
                queue.clear(); joinOpen = false;
                sender.sendMessage(C + "aRaid cancelled.");
            }
            case "reload" -> {
                if (!sender.hasPermission("mavoboss.admin")) { sender.sendMessage("OP only."); return true; }
                reloadConfig(); loadCfg();
                sender.sendMessage(C + "aBossRaid config reloaded - next raid " + C + "e" + nextRaidText() + C + "a.");
            }
            default -> { sender.sendMessage(C + "7/boss join | leave | return | status"); }
        }
        return true;
    }

    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
