package mavo.fishcomp;

import java.io.File;
import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * MAVOFishComp 1.0.0 - weekly fishing tournament (Discord CW#3 idea 8).
 * - one random 2h window per week (seeded by ISO week so it is stable)
 * - any fish caught during the window counts (points per fish, rares worth more)
 * - top 3 coins + participation payout; /fishcomp leaderboard
 */
public final class FishComp extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;
    private ZoneId zone = ZoneId.of("Europe/London");
    private List<DayOfWeek> days = List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
    private int startMin = 16, startMax = 20, durationMin = 120;
    private final Map<Integer, Long> prizes = new HashMap<>();
    private long participationPay = 500;
    private int minCatches = 3;
    private final Map<Material, Integer> bonus = new HashMap<>();

    private long windowStart = 0, windowEnd = 0;
    private final Map<UUID, Integer> scores = new LinkedHashMap<>();
    private boolean counting = false;
    private BossBar bar;
    private BukkitTask timer;

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        loadCfg();
        restoreWindow();
        bar = Bukkit.createBossBar(C + "b\uD83C\uDFA3 FISHING TOURNAMENT", BarColor.BLUE, BarStyle.SOLID);
        bar.setVisible(false);
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        timer = Bukkit.getScheduler().runTaskTimer(this, this::tick, 40L, 400L);
        tick();
        getLogger().info("MAVOFishComp v" + getDescription().getVersion() + " enabled - current window "
                + (counting ? "ACTIVE (" + fmtAgo() + " left)" : windowText()));
    }

    @Override public void onDisable() {
        if (timer != null) timer.cancel();
        if (bar != null) bar.removeAll();
        saveData();
    }

    private void loadCfg() {
        try { zone = ZoneId.of(getConfig().getString("timezone", "Europe/London")); }
        catch (Throwable t) { zone = ZoneId.of("Europe/London"); }
        days = new ArrayList<>();
        for (String d : getConfig().getStringList("days"))
            try { days.add(DayOfWeek.valueOf(d.toUpperCase(Locale.ROOT))); } catch (Throwable ignored) { }
        if (days.isEmpty()) days = List.of(DayOfWeek.SATURDAY, DayOfWeek.SUNDAY);
        startMin = Math.max(6, Math.min(23, getConfig().getInt("window-start-hour-min", 16)));
        startMax = Math.max(startMin, Math.min(23, getConfig().getInt("window-start-hour-max", 20)));
        durationMin = Math.max(30, getConfig().getInt("duration-minutes", 120));
        prizes.clear();
        ConfigurationSection ps = getConfig().getConfigurationSection("prizes");
        if (ps != null)
            for (String k : ps.getKeys(false)) prizes.put(Integer.parseInt(k), ps.getLong(k, 0));
        participationPay = Math.max(0, getConfig().getLong("participation", 500));
        minCatches = Math.max(1, getConfig().getInt("min-catches", 3));
        bonus.clear();
        ConfigurationSection bs = getConfig().getConfigurationSection("points");
        if (bs != null)
            for (String k : bs.getKeys(false)) {
                Material m = Material.matchMaterial(k);
                if (m != null) bonus.put(m, Math.max(1, bs.getInt(k, 1)));
            }
    }

    // ---------------- weekly window math (deterministic per ISO week) ----------------
    private ZonedDateTime thisWeekWindow(ZonedDateTime now) {
        int week = now.get(WeekFields.ISO.weekOfWeekBasedYear());
        int year = now.get(WeekFields.ISO.weekBasedYear());
        Random rnd = new Random(year * 1000L + week);
        DayOfWeek day = days.get(rnd.nextInt(days.size()));
        int hour = startMin + rnd.nextInt(startMax - startMin + 1);
        ZonedDateTime day0 = now.with(java.time.temporal.TemporalAdjusters.previousOrSame(day));
        ZonedDateTime start = day0.withHour(hour).withMinute(0).withSecond(0).withNano(0);
        if (start.isBefore(now.minusDays(1))) start = start.plusDays(7);
        return start;
    }

    private void restoreWindow() {
        long start = data.getLong("window.start", 0), end = data.getLong("window.end", 0);
        if (start > 0 && end > start && System.currentTimeMillis() < end && System.currentTimeMillis() >= start - 30 * 60_000L) {
            windowStart = start; windowEnd = end; counting = true;
            scores.clear();
            ConfigurationSection s = data.getConfigurationSection("window.scores");
            if (s != null) for (String k : s.getKeys(false))
                try { scores.put(UUID.fromString(k), s.getInt(k)); } catch (Throwable ignored) { }
            getLogger().info("MAVOFishComp: restored live tournament window (" + scores.size() + " score(s)).");
        } else {
            ZonedDateTime w = thisWeekWindow(ZonedDateTime.now(zone));
            windowStart = w.toInstant().toEpochMilli();
            windowEnd = windowStart + durationMin * 60_000L;
            counting = false;
        }
    }

    private void tick() {
        long now = System.currentTimeMillis();
        if (!counting && now >= windowStart && now < windowEnd) start();
        if (counting && now >= windowEnd) finish();
        if (counting) {
            long remain = windowEnd - now;
            bar.setTitle(C + "b\uD83C\uDFA3 FISHING TOURNAMENT " + C + "7- " + C + "e" + fmtDur(remain)
                    + C + "7 left \u00b7 " + C + "a" + scores.size() + " angler(s)");
            bar.setProgress(Math.max(0.02, Math.min(1.0, remain / (double) (durationMin * 60_000L))));
            bar.setVisible(true);
        } else bar.setVisible(false);
    }

    private void start() {
        counting = true;
        scores.clear();
        data.set("window.start", windowStart);
        data.set("window.end", windowEnd);
        data.set("window.scores", null);
        saveData();
        Bukkit.broadcastMessage(C + "b\uD83C\uDFA3 " + C + "lFISHING TOURNAMENT STARTED"
                + C + "7! " + C + "aFish anywhere" + C + "7 - every catch = 1 point, rares worth more."
                + " Ends in " + C + "e" + fmtDur(windowEnd - System.currentTimeMillis())
                + C + "7. Top 3 win coins, everyone with " + minCatches + "+ catches gets paid!");
        for (Player p : Bukkit.getOnlinePlayers()) p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.2f);
    }

    private void finish() {
        counting = false;
        bar.setVisible(false);
        List<Map.Entry<UUID, Integer>> top = new ArrayList<>(scores.entrySet());
        top.sort((a, b) -> b.getValue() - a.getValue());
        List<Map.Entry<UUID, Integer>> podium = top.subList(0, Math.min(3, top.size()));
        List<String> lines = new ArrayList<>();
        lines.add(C + "b\uD83C\uDF3A " + C + "lFISHING TOURNAMENT RESULTS");
        int place = 1;
        for (Map.Entry<UUID, Integer> e : podium) {
            String name = uname(e.getKey());
            long pay = prizes.getOrDefault(place, 0L);
            lines.add(C + "7#" + place + " " + C + "e" + name + C + "7 - " + C + "a" + e.getValue()
                    + " points" + (pay > 0 ? C + "7 - " + C + "e" + String.format("%,d", pay) + " coins" : ""));
            payOut(e.getKey(), pay);
            place++;
        }
        int paid = 0;
        for (Map.Entry<UUID, Integer> e : scores.entrySet()) {
            if (e.getValue() >= minCatches && !isPodium(e.getKey(), podium)) {
                payOut(e.getKey(), participationPay);
                paid++;
            }
            data.set("alltime." + e.getKey(), data.getInt("alltime." + e.getKey(), 0) + e.getValue());
        }
        saveLatest(top);
        Bukkit.broadcastMessage(String.join("\n", lines)
                + C + "7\u00b7 " + paid + " participant(s) got " + C + "e"
                + String.format("%,d", participationPay) + C + "7 each. /fishcomp top");
        data.set("window", null);
        saveData();
        ZonedDateTime next = thisWeekWindow(ZonedDateTime.now(zone).plusDays(7));
        windowStart = next.toInstant().toEpochMilli();
        windowEnd = windowStart + durationMin * 60_000L;
    }

    private boolean isPodium(UUID u, List<Map.Entry<UUID, Integer>> podium) {
        for (Map.Entry<UUID, Integer> e : podium) if (e.getKey().equals(u)) return true;
        return false;
    }

    private void payOut(UUID u, long coins) {
        if (coins <= 0 || econ == null) return;
        Player p = Bukkit.getPlayer(u);
        if (p != null && p.isOnline()) {
            if (econ.depositPlayer(p, coins).transactionSuccess())
                p.sendMessage(C + "aYou earned " + C + "e" + String.format("%,d", coins) + " coins" + C + "a from the tournament!");
        } else {
            double pending = data.getDouble("pending." + u, 0);
            data.set("pending." + u, pending + coins);
        }
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) {
        bar.addPlayer(e.getPlayer());
        double pending = data.getDouble("pending." + e.getPlayer().getUniqueId(), 0);
        if (pending > 0 && econ != null && econ.depositPlayer(e.getPlayer(), pending).transactionSuccess()) {
            data.set("pending." + e.getPlayer().getUniqueId(), null);
            saveData();
            e.getPlayer().sendMessage(C + "aPaid you " + C + "e" + String.format("%,d", (long) pending)
                    + " coins" + C + "a from a past tournament.");
        }
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { bar.removePlayer(e.getPlayer()); }

    @EventHandler
    public void onFish(PlayerFishEvent e) {
        if (!counting || e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        Player p = e.getPlayer();
        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;
        int pts = 1;
        if (e.getCaught() instanceof org.bukkit.entity.Item it
                && it.getItemStack() != null && it.getItemStack().getType() != Material.AIR)
            pts = 1 + bonus.getOrDefault(it.getItemStack().getType(), 0);
        scores.merge(p.getUniqueId(), pts, Integer::sum);
        boolean milestone = scores.getOrDefault(p.getUniqueId(), 0) % 10 == 0;
        if (milestone && scores.getOrDefault(p.getUniqueId(), 0) <= 50)
            p.sendMessage(C + "b\uD83C\uDFA3 " + C + "7You have " + C + "a" + scores.get(p.getUniqueId())
                    + C + "7 points! (" + C + "e/fishcomp" + C + "7)");
    }

    // ---------------- leaderboard ----------------
    private void saveLatest(List<Map.Entry<UUID, Integer>> top) {
        data.set("latest", null);
        int i = 1;
        for (Map.Entry<UUID, Integer> e : top) {
            if (i > 10) break;
            data.set("latest." + i + ".uuid", e.getKey().toString());
            data.set("latest." + i + ".points", e.getValue());
            i++;
        }
        saveData();
    }

    private String windowText() {
        ZonedDateTime s = Instant.ofEpochMilli(windowStart).atZone(zone);
        return s.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH)
                + " " + s.getHour() + ":" + String.format("%02d", s.getMinute());
    }
    private String fmtAgo() {
        long min = Math.max(1, (windowEnd - System.currentTimeMillis()) / 60_000L);
        return min / 60 + "h " + String.format("%02dm", min % 60);
    }
    private String uname(UUID u) {
        String n = Bukkit.getOfflinePlayer(u).getName();
        return n != null ? n : u.toString().substring(0, 8);
    }

    // ---------------- commands ----------------
    @Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("status", "top"));
            if (sender.hasPermission("mavofish.admin")) out.addAll(List.of("start", "end", "reload"));
            return out;
        }
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                if (counting) sender.sendMessage(C + "b\uD83C\uDFA3 TOURNAMENT LIVE - ends in "
                        + C + "e" + fmtDur(windowEnd - System.currentTimeMillis()) + C + "7 ("
                        + C + "a" + scores.size() + C + "7 angler(s), current top: "
                        + C + "e" + (scores.isEmpty() ? "nobody yet" : uname(scores.entrySet().stream()
                        .max(Comparator.comparingInt(Map.Entry::getValue)).get().getKey())
                        + " " + scores.values().stream().max(Integer::compare).orElse(0)) + C + "7).");
                else sender.sendMessage(C + "7Next tournament window: " + C + "e" + windowText()
                        + C + "7 (random 2h window, announced at start).");
            }
            case "top" -> {
                sender.sendMessage(C + "b\uD83C\uDF3A " + C + "lAll-time fishing points");
                ConfigurationSection s = data.getConfigurationSection("alltime");
                if (s == null || s.getKeys(false).isEmpty()) { sender.sendMessage(C + "7No data yet."); return true; }
                List<Map.Entry<String, Integer>> all = new ArrayList<>();
                for (String k : s.getKeys(false)) all.add(Map.entry(k, s.getInt(k)));
                all.sort((a, b) -> b.getValue() - a.getValue());
                for (int i = 0; i < Math.min(10, all.size()); i++)
                    sender.sendMessage(C + "7#" + (i + 1) + " " + C + "e" + uname(UUID.fromString(all.get(i).getKey()))
                            + C + "7 - " + C + "a" + String.format("%,d", all.get(i).getValue()) + " points");
            }
            case "start" -> {
                if (!sender.hasPermission("mavofish.admin")) { sender.sendMessage("OP only."); return true; }
                int min = 60;
                try { if (args.length >= 2) min = Math.max(10, Integer.parseInt(args[1])); } catch (Throwable ignored) { }
                windowStart = System.currentTimeMillis();
                windowEnd = windowStart + min * 60_000L;
                start();
                sender.sendMessage(C + "aManual tournament started (" + min + " min).");
            }
            case "end" -> {
                if (!sender.hasPermission("mavofish.admin")) { sender.sendMessage("OP only."); return true; }
                if (!counting) { sender.sendMessage(C + "7No tournament running."); return true; }
                windowEnd = System.currentTimeMillis();
                finish();
            }
            case "reload" -> {
                if (!sender.hasPermission("mavofish.admin")) { sender.sendMessage("OP only."); return true; }
                reloadConfig(); loadCfg();
                sender.sendMessage(C + "aConfig reloaded.");
            }
            default -> sender.sendMessage(C + "7/fishcomp status | top");
        }
        return true;
    }

    private static String fmtDur(long ms) {
        long min = Math.max(1, ms / 60_000L);
        return (min / 60) + "h " + String.format("%02dm", min % 60);
    }
    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
