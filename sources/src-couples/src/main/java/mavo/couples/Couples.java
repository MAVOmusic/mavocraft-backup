package mavo.couples;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;

/** MAVOCouples 1.0.0 - social marriage (Discord CW#4 idea 16, inspired by MarriageMaster).
 *  /marry <player> -> /accept; /couple sethome + /couplehome; /couple tp; heart particles
 *  while both online; data safe across restarts. No combat buffs - pure social. */
public final class Couples extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private File dataFile;
    private YamlConfiguration data;
    private final Map<UUID, UUID> proposals = new HashMap<>();   // target -> suitor
    private int expireSec = 120;
    private boolean particles = true;

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        expireSec = Math.max(10, getConfig().getInt("proposal-expire-seconds", 120));
        particles = getConfig().getBoolean("particles", true);
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, this::tickParticles, 40L, 20L);
        getLogger().info("MAVOCouples v" + getDescription().getVersion() + " enabled - " + marriages() + " marriage(s).");
    }

    @Override public void onDisable() { saveData(); }
    private void saveData() { try { data.save(dataFile); } catch (Throwable ignored) { } }
    private int marriages() {
        var s = data.getConfigurationSection("marriages");
        return s == null ? 0 : s.getKeys(false).size();
    }

    private UUID spouse(UUID u) {
        for (String k : keys()) {
            UUID a = UUID.fromString(data.getString("marriages." + k + ".a"));
            UUID b = UUID.fromString(data.getString("marriages." + k + ".b"));
            if (a.equals(u)) return b;
            if (b.equals(u)) return a;
        }
        return null;
    }
    private List<String> keys() {
        var s = data.getConfigurationSection("marriages");
        return s == null ? List.of() : new java.util.ArrayList<>(s.getKeys(false));
    }
    private void marry(UUID a, UUID b) {
        String k = a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
        data.set("marriages." + k + ".a", a.toString());
        data.set("marriages." + k + ".b", b.toString());
        data.set("marriages." + k + ".since", System.currentTimeMillis());
        saveData();
    }
    private void divorce(UUID a) {
        UUID b = spouse(a);
        if (b == null) return;
        String k = a.toString().compareTo(b.toString()) < 0 ? a + "_" + b : b + "_" + a;
        data.set("marriages." + k, null);
        data.set("homes." + a, null);
        data.set("homes." + b, null);
        saveData();
    }

    private String nm(UUID u) { return Bukkit.getOfflinePlayer(u).getName() == null ? "?" : Bukkit.getOfflinePlayer(u).getName(); }

    @EventHandler public void onQuit(PlayerQuitEvent e) { proposals.remove(e.getPlayer().getUniqueId()); }

    private void tickParticles() {
        if (!particles) return;
        for (String k : keys()) {
            UUID a = UUID.fromString(data.getString("marriages." + k + ".a"));
            UUID b = UUID.fromString(data.getString("marriages." + k + ".b"));
            Player pa = Bukkit.getPlayer(a), pb = Bukkit.getPlayer(b);
            if (pa == null || pb == null || !pa.isOnline() || !pb.isOnline()) continue;
            if (pa.getWorld().equals(pb.getWorld()) && pa.getLocation().distanceSquared(pb.getLocation()) < 36) {
                Location mid = pa.getLocation().midpoint(pb.getLocation()).add(0, 2, 0);
                pa.getWorld().spawnParticle(Particle.HEART, mid, 2, 0.4, 0.3, 0.4);
            }
        }
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (c.getName().equalsIgnoreCase("marry") && a.length == 1)
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (c.getName().equalsIgnoreCase("couple") && a.length == 1)
            return List.of("info", "spouse", "home", "sethome", "tp");
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
        String n = cmd.getName().toLowerCase(java.util.Locale.ROOT);
        UUID u = p.getUniqueId();
        switch (n) {
            case "marry" -> {
                if (args.length < 1) { p.sendMessage(C + "cUsage: /marry <player>"); return true; }
                if (spouse(u) != null) { p.sendMessage(C + "cYou are already married."); return true; }
                Player t = Bukkit.getPlayerExact(args[0]);
                if (t == null) { p.sendMessage(C + "cPlayer offline."); return true; }
                if (t.equals(p)) { p.sendMessage(C + "cYou can't marry yourself."); return true; }
                if (spouse(t.getUniqueId()) != null) { p.sendMessage(C + "cThey are already married."); return true; }
                proposals.put(t.getUniqueId(), u);
                t.sendMessage(C + "d\u2764 " + C + "e" + p.getName() + C + "d proposes marriage! "
                        + C + "a/accept" + C + "d or " + C + "c/deny" + C + "d. (expires in " + expireSec + "s)");
                t.playSound(t.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.6f);
                p.sendMessage(C + "aProposal sent. Waiting for " + t.getName() + ".");
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (t.isOnline() && proposals.get(t.getUniqueId()) == u)
                        proposals.remove(t.getUniqueId());
                }, (expireSec + 5) * 20L);
            }
            case "accept" -> {
                UUID suitor = proposals.remove(u);
                if (suitor == null) { p.sendMessage(C + "cNo proposal pending."); return true; }
                Player s2 = Bukkit.getPlayer(suitor);
                if (s2 == null || !s2.isOnline()) { p.sendMessage(C + "cThey left."); return true; }
                if (spouse(u) != null || spouse(suitor) != null) { p.sendMessage(C + "cSomeone is already married."); return true; }
                marry(u, suitor);
                p.sendMessage(C + "d\u2764 You are married to " + C + "e" + nm(suitor) + C + "d!");
                s2.sendMessage(C + "d\u2764 " + C + "e" + nm(u) + C + "d accepted! You are married!");
                Bukkit.broadcastMessage(C + "d\u2764 " + C + "a" + nm(u) + C + "d & " + C + "a" + nm(suitor)
                        + C + "d are married! (you can /couple sethome)");
            }
            case "deny" -> {
                UUID suitor = proposals.remove(u);
                p.sendMessage(suitor == null ? C + "cNo proposal pending." : C + "cProposal denied.");
                if (suitor != null) {
                    Player s2 = Bukkit.getPlayer(suitor);
                    if (s2 != null) s2.sendMessage(C + "c" + p.getName() + " declined your proposal.");
                }
            }
            case "divorce" -> {
                if (!p.hasPermission("mavocouple.admin") && spouse(u) == null) { p.sendMessage(C + "cYou are not married."); return true; }
                UUID other = spouse(u);
                divorce(u);
                p.sendMessage(C + "cYou are divorced.");
                if (other != null) {
                    Player o = Bukkit.getPlayer(other);
                    if (o != null) o.sendMessage(C + "cYour marriage has ended.");
                }
            }
            case "couple" -> {
                UUID sp = spouse(u);
                String sub = args.length == 0 ? "info" : args[0].toLowerCase(java.util.Locale.ROOT);
                switch (sub) {
                    case "info", "spouse" -> {
                        if (sp == null) p.sendMessage(C + "7You are not married. Try " + C + "e/marry <player>" + C + "7.");
                        else p.sendMessage(C + "d\u2764 Your spouse: " + C + "e" + nm(sp) + C + "d. ("
                                + C + "7/couple tp, sethome, home" + C + "d)");
                    }
                    case "sethome" -> {
                        if (sp == null) { p.sendMessage(C + "cYou are not married."); return true; }
                        String home = locKey(p.getLocation());
                        data.set("homes." + u, home);
                        data.set("homes." + sp, home);   // one shared couple home
                        saveData();
                        p.sendMessage(C + "aCouple home set at " + p.getLocation().getBlockX() + " "
                                + p.getLocation().getBlockY() + " " + p.getLocation().getBlockZ() + C + "a.");
                        Player o = Bukkit.getPlayer(sp);
                        if (o != null) o.sendMessage(C + "dYour partner set the couple home!");
                    }
                    case "home" -> {
                        String h = data.getString("homes." + u);
                        if (h == null && sp != null) h = data.getString("homes." + sp);
                        if (h == null) { p.sendMessage(C + "cNo couple home set - " + C + "e/couple sethome" + C + "c."); return true; }
                        teleport(p, parseLoc(h));
                    }
                    case "tp" -> {
                        if (sp == null) { p.sendMessage(C + "cYou are not married."); return true; }
                        Player o = Bukkit.getPlayer(sp);
                        if (o == null || !o.isOnline()) { p.sendMessage(C + "cYour partner is offline."); return true; }
                        p.teleport(o.getLocation());
                        p.sendMessage(C + "aTeleported to " + o.getName() + ".");
                    }
                    default -> p.sendMessage(C + "7/couple info | sethome | home | tp");
                }
            }
            default -> p.sendMessage(C + "7/marry <player> | /accept | /deny | /divorce | /couple");
        }
        return true;
    }

    private void teleport(Player p, Location l) {
        if (l == null) { p.sendMessage(C + "cCouple home is broken - set it again."); return; }
        p.teleport(l);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
    }

    private static String locKey(Location l) {
        return l.getWorld().getName() + "," + l.getBlockX() + "," + l.getBlockY() + "," + l.getBlockZ();
    }
    private static Location parseLoc(String s) {
        try {
            String[] p = s.split(",");
            World w = Bukkit.getWorld(p[0]);
            if (w == null) return null;
            return new Location(w, Double.parseDouble(p[1]) + 0.5, Double.parseDouble(p[2]), Double.parseDouble(p[3]) + 0.5);
        } catch (Throwable t) { return null; }
    }
}
