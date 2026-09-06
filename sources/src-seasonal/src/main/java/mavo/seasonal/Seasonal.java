package mavo.seasonal;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.MonthDay;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.java.JavaPlugin;

/**
 * MAVOSeasonal 1.0.0 - seasonal / themed events calendar (Discord CW#3 idea 10).
 * - yearly windows from config (Spooky 10-25..11-02, Festive 12-20..01-05, Anniversary 09-01..09-08)
 * - /season status; manual admin toggle to test a season early
 * - mob-candy: mobs drop candy during the season (chance per kill)
 * - xp-multiplier: forces MAVODoubleXp while the season runs (reflection)
 */
public final class Seasonal extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private File dataFile;
    private YamlConfiguration data;
    private ZoneId zone = ZoneId.of("Europe/London");
    private final Map<String, Season> seasons = new HashMap<>();
    private String current = "";          // season id active right now

    private record Season(String display, MonthDay start, MonthDay end,
                          boolean candy, Material candyMat, double candyChance, double xpMult) { }

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        loadSeasons();
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::check, 60L, 1200L); // every minute
        check();
        getLogger().info("MAVOSeasonal v" + getDescription().getVersion() + " enabled - " + seasons.size()
                + " season(s) defined, active: " + (current.isEmpty() ? "none" : current));
    }

    @Override public void onDisable() {
        // let the XP boost end with the season unless another season is active
        if (!current.isEmpty()) forceOff();
        saveData();
    }

    private void loadSeasons() {
        seasons.clear();
        ConfigurationSection cs = getConfig().getConfigurationSection("seasons");
        if (cs == null) return;
        for (String id : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(id);
            if (c == null) continue;
            MonthDay st = parseMd(c.getString("start", ""));
            MonthDay en = parseMd(c.getString("end", ""));
            if (st == null || en == null) continue;
            Material m = Material.matchMaterial(c.getString("candy-material", "SUGAR"));
            if (m == null) m = Material.SUGAR;
            seasons.put(id.toLowerCase(Locale.ROOT), new Season(
                    c.getString("display", "&e" + id), st, en,
                    c.getBoolean("mob-candy", true), m,
                    Math.max(0, Math.min(1.0, c.getDouble("candy-chance", 0.05))),
                    Math.max(0, c.getDouble("xp-multiplier", 0))));
        }
    }
    private static MonthDay parseMd(String s) {
        try { return MonthDay.parse(s.trim()); } catch (Throwable t) { return null; }
    }

    // ---------------- active detection ----------------
    private String activeAt(LocalDate day) {
        List<String> active = new ArrayList<>();
        for (Map.Entry<String, Season> en : seasons.entrySet()) {
            String id = en.getKey();
            Season s = en.getValue();
            boolean manual = data.getBoolean("manual." + id, false);
            if (manual) { active.add(id); continue; }
            if (inWindow(day, s)) active.add(id);
        }
        if (active.size() != 1) return "";
        return active.get(0);
    }
    private static boolean inWindow(LocalDate day, Season s) {
        LocalDate st = toDate(day.getYear(), s.start);
        LocalDate en = toDate(day.getYear(), s.end);
        if (en.isBefore(st)) en = en.plusYears(1); // festive wraps the year
        return !day.isBefore(st) && !day.isAfter(en);
    }
    private static LocalDate toDate(int year, MonthDay md) {
        LocalDate d = md.atYear(year);
        // Feb 29 guard
        if (d.getMonthValue() != md.getMonthValue()) d = d.withDayOfMonth(d.lengthOfMonth());
        return d;
    }

    private void check() {
        LocalDate day = LocalDate.now(zone);
        String now = activeAt(day);
        if (!now.equals(current)) {
            String old = current;
            current = now;
            if (!old.isEmpty()) {
                Season s = seasons.get(old);
                Bukkit.broadcastMessage(C + "7\u2744 " + cc(s.display) + C + "7 has ended!");
                forceOff();
            }
            if (!current.isEmpty()) {
                Season s = seasons.get(current);
                Bukkit.broadcastMessage(C + "d\u2744 " + C + "l" + cc(s.display) + C + "d has started!"
                        + (s.candy ? C + "7 - killed mobs drop " + C + "e" + niceMat(s.candyMat) + C + "7 candy!"
                        : ""));
                for (Player p : Bukkit.getOnlinePlayers())
                    p.playSound(p.getLocation(), Sound.BLOCK_BELL_RESONATE, 0.9f, 1.3f);
                forceOn(s);
            }
            saveData();
        }
    }

    private void forceOn(Season s) {
        if (s.xpMult <= 1.0) return;
        try {
            Class<?> c = Class.forName("mavo.doublexp.DoubleXp");
            ZonedDateTime end = ZonedDateTime.of(
                    toDate(LocalDate.now(zone).getYear(), s.end),
                    LocalTime.of(23, 59), zone);
            c.getMethod("force", long.class, double.class).invoke(null,
                    end.toInstant().toEpochMilli(), s.xpMult);
            Bukkit.broadcastMessage(C + "d\u2744 Season XP: " + C + "e" + tidy(s.xpMult) + "x"
                    + C + "d until " + C + "e" + end.toLocalDate() + C + "d!");
        } catch (Throwable ignored) { }
    }
    private void forceOff() {
        try {
            Class<?> c = Class.forName("mavo.doublexp.DoubleXp");
            c.getMethod("clearForce").invoke(null);
        } catch (Throwable ignored) { }
    }

    // ---------------- candy drops ----------------
    @EventHandler
    public void onKill(EntityDeathEvent e) {
        if (current.isEmpty()) return;
        Season s = seasons.get(current);
        if (s == null || !s.candy || s.candyChance <= 0) return;
        if (e.getEntity().getKiller() == null) return;
        if (e.getEntity() instanceof org.bukkit.entity.Player) return;
        if (Math.random() >= s.candyChance) return;
        ItemStack candy = new ItemStack(s.candyMat, 1 + (int) (Math.random() * 2));
        e.getDrops().add(candy);
    }

    // ---------------- commands ----------------
    @Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("status"));
            if (sender.hasPermission("mavoseason.admin")) out.addAll(List.of("toggle", "reload"));
            return out;
        }
        if (args.length == 2 && sender.hasPermission("mavoseason.admin")) return new ArrayList<>(seasons.keySet());
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        String sub = args.length == 0 ? "status" : args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "status" -> {
                sender.sendMessage(C + "d\u2744 " + C + "lSEASONAL CALENDAR");
                LocalDate today = LocalDate.now(zone);
                for (Map.Entry<String, Season> en : seasons.entrySet()) {
                    Season s = en.getValue();
                    LocalDate st = toDate(today.getYear(), s.start);
                    LocalDate en2 = toDate(today.getYear(), s.end);
                    if (en2.isBefore(st)) en2 = en2.plusYears(1);
                    boolean on = en.getKey().equals(current);
                    String when;
                    if (on) when = C + "aACTIVE NOW";
                    else if (en2.isBefore(today)) when = C + "7next: " + st.plusYears(1);
                    else if (st.isAfter(today)) when = C + "7starts " + st;
                    else when = C + "7ends " + en2;
                    sender.sendMessage(C + (on ? "a" : "7") + " - " + cc(s.display) + C + "7 ("
                            + s.start + " -> " + s.end + ") " + when
                            + (s.xpMult > 1.0 ? C + "7, xp " + tidy(s.xpMult) : ""));
                }
                if (current.isEmpty()) sender.sendMessage(C + "8No season active right now.");
            }
            case "toggle" -> {
                if (!sender.hasPermission("mavoseason.admin")) { sender.sendMessage("OP only."); return true; }
                if (args.length < 2 || !seasons.containsKey(args[1].toLowerCase(Locale.ROOT))) {
                    sender.sendMessage(C + "cUsage: /season toggle <id>");
                    return true;
                }
                String id = args[1].toLowerCase(Locale.ROOT);
                boolean now = !data.getBoolean("manual." + id, false);
                data.set("manual." + id, now);
                saveData();
                Season s = seasons.get(id);
                sender.sendMessage(C + (now ? "a" : "7") + id + (now ? " forced ON (candy + xp active)." : " back to calendar."));
                check();
            }
            case "reload" -> {
                if (!sender.hasPermission("mavoseason.admin")) { sender.sendMessage("OP only."); return true; }
                reloadConfig(); loadSeasons(); check();
                sender.sendMessage(C + "aCalendar reloaded.");
            }
            default -> sender.sendMessage(C + "7/season status (admin: toggle <id>, reload)");
        }
        return true;
    }

    private void saveData() { try { data.save(dataFile); } catch (Throwable ignored) { } }
    private static String niceMat(Material m) {
        return m.name().toLowerCase(Locale.ROOT).replace('_', ' ');
    }
    private static String tidy(double d) {
        return d == Math.floor(d) ? String.valueOf((long) d) : String.valueOf(d);
    }
    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
