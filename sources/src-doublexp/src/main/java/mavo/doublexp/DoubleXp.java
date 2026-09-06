package mavo.doublexp;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MAVODoubleXp 1.0.0 - scheduled DOUBLE XP weekends (Discord CW#3 idea 5).
 * - weekly window from config (default Fri 18:00 -> Sun 18:00, Europe/London)
 * - MAVOProfessions calls DoubleXp.boost() on every XP grant (reflection)
 * - /xpboost status | now <minutes> [mult] | off | reload (admin)
 * - bossbar countdown + start/stop broadcasts
 */
public final class DoubleXp extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private static volatile double mult = 1.0;         // what Professions reads
    private static volatile long forcedUntilMs = 0L;  // manual /xpboost now
    private static volatile long forcedDurationMs = 0L;
    private static volatile double forcedMult = 1.0;   // multiplier used while forced
    private static volatile ZonedDateTime activeEnd = null; // display only

    private ZoneId zone = ZoneId.of("Europe/London");
    private double baseMult = 2.0;
    private boolean announce = true;
    private boolean wkEnabled = true;
    private DayOfWeek wkStartDay = DayOfWeek.FRIDAY, wkEndDay = DayOfWeek.SUNDAY;
    private LocalTime wkStart = LocalTime.of(18, 0), wkEnd = LocalTime.of(18, 0);

    private BossBar bar;
    private boolean wasActive = false;

    // ---------------- lifecycle ----------------
    @Override public void onEnable() {
        saveDefaultConfig();
        loadCfg();
        bar = Bukkit.createBossBar(C + "b" + C + "l\u26a1 " + C + "d" + C + "lDOUBLE XP WEEKEND",
                BarColor.PURPLE, BarStyle.SOLID);
        bar.setVisible(false);
        Bukkit.getPluginManager().registerEvents(this, this);
        for (Player p : Bukkit.getOnlinePlayers()) bar.addPlayer(p);
        Bukkit.getScheduler().runTaskTimer(this, this::tick, 20L, 600L); // every 30s
        tick();
        getLogger().info("MAVODoubleXp v" + getDescription().getVersion() + " enabled - weekly "
                + (wkEnabled
                ? wkStartDay.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + wkStart
                + " -> " + wkEndDay.getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " " + wkEnd
                : "OFF") + " x" + baseMult + ".");
    }

    @Override public void onDisable() {
        if (bar != null) bar.removeAll();
    }

    private void loadCfg() {
        try { zone = ZoneId.of(getConfig().getString("timezone", "Europe/London")); }
        catch (Throwable t) { zone = ZoneId.of("Europe/London"); }
        baseMult = Math.max(1.01, getConfig().getDouble("multiplier", 2.0));
        announce = getConfig().getBoolean("announce", true);
        wkEnabled = getConfig().getBoolean("weekend.enabled", true);
        wkStartDay = parseDay(getConfig().getString("weekend.start-day", "FRIDAY"), DayOfWeek.FRIDAY);
        wkEndDay = parseDay(getConfig().getString("weekend.end-day", "SUNDAY"), DayOfWeek.SUNDAY);
        wkStart = parseTime(getConfig().getString("weekend.start", "18:00"), LocalTime.of(18, 0));
        wkEnd = parseTime(getConfig().getString("weekend.end", "18:00"), LocalTime.of(18, 0));
    }

    // ---------------- public API (called by MAVOProfessions via reflection) ----------------
    /** Current XP multiplier: 1.0 off, 2.0+ during a boost. */
    public static double boost() { return mult; }

    /** True while a boost (scheduled or forced) is running. */
    public static boolean active() { return mult > 1.0; }

    /** External force (used by MAVOSeasonal): run multiplier m until endMs. */
    public static void force(long endMs, double m) {
        if (endMs > System.currentTimeMillis() && m > 1.0) {
            forcedUntilMs = endMs;
            forcedMult = Math.max(1.01, m);
            forcedDurationMs = endMs - System.currentTimeMillis();
        }
    }
    /** External: remove a forced boost (seasonal override cleared). */
    public static void clearForce() { forcedUntilMs = 0; forcedMult = 1.0; }

    // ---------------- schedule math ----------------
    private static DayOfWeek parseDay(String s, DayOfWeek def) {
        try { return DayOfWeek.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (Throwable t) { return def; }
    }
    private static LocalTime parseTime(String s, LocalTime def) {
        try { return LocalTime.parse(s.trim()); }
        catch (Throwable t) { return def; }
    }

    private ZonedDateTime windowStartOnOrBefore(ZonedDateTime t) {
        for (int d = 7; d >= 0; d--) {
            ZonedDateTime s = onDay(t.toLocalDate().minusDays(d));
            if (!s.isAfter(t)) return s;
        }
        return null;
    }
    private ZonedDateTime onDay(LocalDate day) {
        long add = (wkStartDay.getValue() - day.getDayOfWeek().getValue() + 7) % 7;
        return ZonedDateTime.of(day.plusDays(add), wkStart, zone);
    }
    private ZonedDateTime windowEnd(ZonedDateTime start) {
        long add = (wkEndDay.getValue() - wkStartDay.getValue() + 7) % 7;
        ZonedDateTime e = start.plusDays(add).with(wkEnd);
        if (!e.isAfter(start)) e = e.plusDays(7);
        return e;
    }
    private ZonedDateTime nextStart(ZonedDateTime t) {
        ZonedDateTime s = onDay(t.toLocalDate());
        if (!s.isAfter(t)) s = s.plusDays(7);
        return s;
    }
    private static String fmtDur(long ms) {
        long min = Math.max(1, ms / 60_000L);
        long h = min / 60, m = min % 60;
        return h > 0 ? h + "h " + String.format("%02dm", m) : m + "m";
    }
    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private static String fmtTime(ZonedDateTime t) {
        return t.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH) + " "
                + t.getHour() + ":" + String.format("%02d", t.getMinute());
    }

    // ---------------- the 30s tick ----------------
    private void tick() {
        ZonedDateTime now = ZonedDateTime.now(zone);
        ZonedDateTime end = null;
        double newMult = 1.0;
        long totalMs = 0;
        if (forcedUntilMs > System.currentTimeMillis()) {
            newMult = forcedMult > 1.0 ? forcedMult : baseMult;
            end = Instant.ofEpochMilli(forcedUntilMs).atZone(zone);
            totalMs = forcedDurationMs;
        } else {
            forcedUntilMs = 0;
            if (wkEnabled) {
                ZonedDateTime s = windowStartOnOrBefore(now);
                if (s != null) {
                    ZonedDateTime e = windowEnd(s);
                    if (!now.isBefore(s) && now.isBefore(e)) {
                        newMult = baseMult;
                        end = e;
                        totalMs = Duration.between(s, e).toMillis();
                    }
                }
            }
        }
        mult = newMult;
        activeEnd = end;
        updateBar(end, totalMs);
        if (announce && newMult > 1.0 && !wasActive) {
            Bukkit.broadcastMessage(C + "d" + C + "l\u26a1 " + C + "bDOUBLE XP WEEKEND " + C + "dSTARTED"
                    + C + "7 - all profession XP x" + tidy(baseMult) + " until " + C + "e" + fmtTime(end) + C + "7!");
        } else if (announce && newMult <= 1.0 && wasActive) {
            Bukkit.broadcastMessage(C + "d" + C + "l\u26a1 " + C + "7Double XP weekend has ended - back to normal XP.");
        }
        wasActive = newMult > 1.0;
    }

    private void updateBar(ZonedDateTime end, long totalMs) {
        if (end == null || totalMs <= 0) { bar.setVisible(false); return; }
        long remain = Math.max(0, Duration.between(ZonedDateTime.now(zone), end).toMillis());
        bar.setTitle(C + "b" + C + "l\u26a1 " + C + "d" + C + "lDOUBLE XP WEEKEND " + C + "b( x" + tidy(baseMult)
                + " )" + C + "7 - ends in " + C + "e" + fmtDur(remain));
        bar.setProgress(Math.max(0.02, Math.min(1.0, remain / (double) totalMs)));
        bar.setVisible(true);
    }

    private static String tidy(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }

    // ---------------- events (bossbar follows players) ----------------
    @EventHandler public void onJoin(PlayerJoinEvent e) { bar.addPlayer(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { bar.removePlayer(e.getPlayer()); }

    // ---------------- commands ----------------
    @Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("status"));
            if (sender.hasPermission("mavodoublexp.admin")) out.addAll(List.of("now", "off", "reload"));
            return out;
        }
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) { status(sender); return true; }
        if (args[0].equalsIgnoreCase("reload") && sender.hasPermission("mavodoublexp.admin")) {
            reloadConfig(); loadCfg(); tick();
            sender.sendMessage(C + "aDoubleXP config reloaded.");
            return true;
        }
        if (args[0].equalsIgnoreCase("now") && sender.hasPermission("mavodoublexp.admin")) {
            if (args.length < 2) { sender.sendMessage(C + "cUsage: /xpboost now <minutes> [multiplier]"); return true; }
            try {
                int min = Math.max(1, Integer.parseInt(args[1]));
                double m = args.length >= 3 ? Double.parseDouble(args[2]) : baseMult;
                if (m < 1.01) m = baseMult;
                baseMult = Math.max(1.01, m);
                forcedDurationMs = min * 60_000L;
                forcedUntilMs = System.currentTimeMillis() + forcedDurationMs;
                tick();
                Bukkit.broadcastMessage(C + "d" + C + "l\u26a1 " + C + "bADMIN BOOST - " + C + "d" + C + "lALL XP x"
                        + tidy(m) + C + "7 for " + C + "e" + min + " minutes" + C + "7!");
            } catch (Throwable t) {
                sender.sendMessage(C + "cUsage: /xpboost now <minutes> [multiplier]");
            }
            return true;
        }
        if (args[0].equalsIgnoreCase("off") && sender.hasPermission("mavodoublexp.admin")) {
            forcedUntilMs = 0;
            tick();
            sender.sendMessage(C + "aForced boost cleared - back to the weekly schedule.");
            return true;
        }
        status(sender);
        return true;
    }

    private void status(CommandSender sender) {
        ZonedDateTime now = ZonedDateTime.now(zone);
        sender.sendMessage(C + "d" + C + "l\u26a1 DOUBLE XP WEEKEND");
        if (mult > 1.0) {
            sender.sendMessage(C + "aACTIVE: " + C + "e" + tidy(baseMult) + C + "a until "
                    + C + "e" + fmtTime(activeEnd) + C + "a (" + fmtDur(Duration.between(now, activeEnd).toMillis()) + " left).");
        } else if (wkEnabled) {
            sender.sendMessage(C + "7Off. Weekly window: " + C + "e" + fmtTime(nextStart(now))
                    + C + "7 -> " + C + "e" + fmtTime(windowEnd(nextStart(now))) + C + "7 (" + C + "d..."
                    + C + "7).");
        } else {
            sender.sendMessage(C + "7Off. Weekly schedule disabled; an admin can start one with "
                    + C + "e/xpboost now <minutes>");
        }
        sender.sendMessage(C + "7Multiplier applies to &eall profession XP&7 (MAVOProfessions).");
    }
}
