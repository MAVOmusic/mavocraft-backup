package mavo.duels;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

/** MAVODuels 1.0.0 - ranked 1v1 duels (moreinfo on Discord CW#4 idea 19).
 *  /duel <player> [bet] -> /daccept. Both teleport into the configured arena (or stay
 *  where they are if no arena). First death, leaving the arena or logging out = loss.
 *  Winner takes the pot minus the house cut; wins/losses kept in data.yml. */
public final class Duels extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;
    private Location arena;
    private int radius = 20;
    private long defaultBet = 0;
    private long maxBet = 1_000_000;
    private double houseCut = 0.10;

    private record Fight(UUID a, UUID b, long bet, Location aLoc, Location bLoc) {}
    private final Map<UUID, UUID> challenges = new HashMap<>();   // target -> challenger
    private final Map<UUID, Long> challengeBet = new HashMap<>(); // target -> bet
    private final Map<UUID, Fight> fights = new HashMap<>();      // player -> fight

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        loadCfg();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVODuels v" + getDescription().getVersion() + " enabled - arena="
                + (arena != null ? arena.getBlockX() + "," + arena.getBlockZ() : "NOT SET")
                + (econ == null ? " - Vault NOT found (bets disabled)" : ""));
    }

    @Override public void onDisable() { saveData(); }
    private void saveData() { try { data.save(dataFile); } catch (Throwable ignored) { } }

    private void loadCfg() {
        ConfigurationSection a = getConfig().getConfigurationSection("arena");
        World w = a != null ? Bukkit.getWorld(a.getString("world", "world")) : null;
        arena = w != null && a != null ? new Location(w, a.getInt("x") + 0.5, a.getInt("y"), a.getInt("z") + 0.5) : null;
        radius = Math.max(4, getConfig().getInt("radius", 20));
        defaultBet = Math.max(0, getConfig().getLong("default-bet", 0));
        maxBet = Math.max(0, getConfig().getLong("max-bet", 1_000_000));
        houseCut = Math.max(0, Math.min(0.9, getConfig().getDouble("house-cut", 0.10)));
    }

    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    public static String nm(UUID u) {
        if (u == null) return "?";
        var p = Bukkit.getOfflinePlayer(u);
        return p.getName() == null ? "?" : p.getName();
    }

    private static String coins(long n) { return String.format("%,d", n); }

    private void addStat(UUID u, boolean won) {
        data.set("stats." + u + ".wins", data.getInt("stats." + u + ".wins", 0) + (won ? 1 : 0));
        data.set("stats." + u + ".losses", data.getInt("stats." + u + ".losses", 0) + (won ? 0 : 1));
        saveData();
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        challenges.remove(e.getPlayer().getUniqueId());
        challenges.entrySet().removeIf(en -> en.getValue().equals(e.getPlayer().getUniqueId()));
        challengeBet.remove(e.getPlayer().getUniqueId());
        Fight f = fights.remove(e.getPlayer().getUniqueId());
        if (f != null) {
            UUID winner = e.getPlayer().getUniqueId().equals(f.a()) ? f.b() : f.a();
            finishFight(f, winner);
        }
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Fight f = fights.get(e.getPlayer().getUniqueId());
        if (f == null || e.getTo() == null || e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        Location center = arena != null ? arena : mid(f.aLoc(), f.bLoc());
        if (e.getTo().distanceSquared(center) > radius * (double) radius) {
            UUID winner = e.getPlayer().getUniqueId().equals(f.a()) ? f.b() : f.a();
            e.getPlayer().sendMessage(C + "cYou left the arena - forfeit!");
            finishFight(f, winner);
        }
    }

    @EventHandler
    public void onDeath(PlayerDeathEvent e) {
        Fight f = fights.get(e.getEntity().getUniqueId());
        if (f == null) return;
        e.setDeathMessage(null);
        UUID winner = e.getEntity().getUniqueId().equals(f.a()) ? f.b() : f.a();
        Player loser = e.getEntity();
        Player loserReturn = loser;
        finishFight(f, winner);
        // let the loser respawn right where they started
        Location back = loser.getUniqueId().equals(f.a()) ? f.aLoc() : f.bLoc();
        Bukkit.getScheduler().runTask(this, () -> {
            if (loserReturn.isOnline()) {
                loserReturn.spigot().respawn();
                loserReturn.setHealth(loserReturn.getMaxHealth());
                loserReturn.setFoodLevel(20);
                loserReturn.teleport(back);
                loserReturn.sendMessage(C + "7You respawned at the duel entrance.");
            }
        });
    }

    @EventHandler
    public void onPvP(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player d && e.getEntity() instanceof Player v) {
            Fight fd = fights.get(d.getUniqueId());
            Fight fv = fights.get(v.getUniqueId());
            // one fighter vs an outsider = block, neither = normal PvP, both = fight
            if ((fd == null) ^ (fv == null)) e.setCancelled(true);
        }
    }

    private static Location mid(Location a, Location b) {
        return new Location(a.getWorld(), (a.getX() + b.getX()) / 2, a.getY(), (a.getZ() + b.getZ()) / 2);
    }

    private void finishFight(Fight f, UUID winnerId) {
        fights.remove(f.a());
        fights.remove(f.b());
        UUID loserId = winnerId.equals(f.a()) ? f.b() : f.a();
        addStat(winnerId, true);
        addStat(loserId, false);
        if (f.bet() > 0 && econ != null) {
            long pot = (long) Math.floor(f.bet() * 2 * (1 - houseCut));
            if (econ.depositPlayer(Bukkit.getOfflinePlayer(winnerId), pot).transactionSuccess())
                msg(winnerId, C + "aPot: +" + C + "e" + coins(pot) + C + "a coins (10% house cut).");
        } else if (f.bet() > 0) {
            msg(winnerId, C + "cVault not found - " + coins(f.bet() * 2) + " coins were not paid.");
        }
        Player w = Bukkit.getPlayer(winnerId);
        if (w != null && w.isOnline()) {
            w.setHealth(w.getMaxHealth());
            w.setFoodLevel(20);
            w.sendMessage(C + "a\u2694 DUEL WON (+1 win)");
            w.playSound(w.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
        }
        Bukkit.broadcastMessage(cc("&6\u2694 &e" + nm(winnerId) + " &6defeated " + nm(loserId)
                + " &6in a duel" + (f.bet() > 0 ? " &7(pot " + coins(f.bet() * 2) + ")" : "") + "!"));
    }

    private static void msg(UUID u, String s) {
        Player p = Bukkit.getPlayer(u);
        if (p != null && p.isOnline()) p.sendMessage(s);
    }

    private void startFight(Player a, Player b2, long bet) {
        if (arena != null && !inArena(a.getLocation()) && !inArena(b2.getLocation())) {
            Location aLoc = arena.clone().add(-(radius / 2.0), 1, 0);
            Location bLoc = arena.clone().add(radius / 2.0, 1, 0);
            a.teleport(aLoc);
            b2.teleport(bLoc);
        }
        Location aLoc = a.getLocation().clone();
        Location bLoc = b2.getLocation().clone();
        Fight f = new Fight(a.getUniqueId(), b2.getUniqueId(), bet, aLoc, bLoc);
        fights.put(a.getUniqueId(), f);
        fights.put(b2.getUniqueId(), f);
        a.sendMessage(C + "6\u2694 DUEL START against " + nm(b2.getUniqueId()) + "! First to die loses.");
        b2.sendMessage(C + "6\u2694 DUEL START against " + nm(a.getUniqueId()) + "! First to die loses.");
        a.playSound(a.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
        b2.playSound(b2.getLocation(), Sound.ENTITY_WITHER_SPAWN, 1f, 1f);
    }

    private boolean inArena(Location l) {
        return arena != null && l.getWorld().equals(arena.getWorld())
                && l.distanceSquared(arena) < radius * (double) radius;
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (c.getName().equalsIgnoreCase("duel") && a.length == 1)
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        if (c.getName().equalsIgnoreCase("dstats") && a.length == 1) return List.of("top");
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
        UUID u = p.getUniqueId();
        switch (cmd.getName().toLowerCase(Locale.ROOT)) {
            case "duel" -> {
                if (args.length < 1) { p.sendMessage(C + "cUsage: /duel <player> [bet]"); return true; }
                Player t = Bukkit.getPlayerExact(args[0]);
                if (t == null) { p.sendMessage(C + "cPlayer offline."); return true; }
                if (t.equals(p)) { p.sendMessage(C + "cYou can't duel yourself."); return true; }
                if (fights.containsKey(u)) { p.sendMessage(C + "cYou are already in a duel."); return true; }
                if (fights.containsKey(t.getUniqueId())) { p.sendMessage(C + "cThat player is duelling."); return true; }
                long bet = defaultBet;
                if (args.length >= 2) {
                    try { bet = Long.parseLong(args[1].replace(",", "")); }
                    catch (Throwable ex) { p.sendMessage(C + "cBet must be a number."); return true; }
                }
                if (bet < 0 || bet > maxBet) { p.sendMessage(C + "cBet must be 0-" + coins(maxBet) + "."); return true; }
                if (bet > 0 && (econ == null || !econ.has(p, bet))) { p.sendMessage(C + "cYou need " + coins(bet) + " coins (Vault)."); return true; }
                challenges.put(t.getUniqueId(), u);
                challengeBet.put(t.getUniqueId(), bet);
                t.sendMessage(C + "6\u2694 " + p.getName() + C + "6 challenges you to a duel"
                        + (bet > 0 ? C + "7 (bet " + coins(bet) + ")" : "") + "! " + C + "a/daccept" + C + "6 or " + C + "c/ddeny" + C + "6.");
                p.sendMessage(C + "aChallenge sent. Wait for " + t.getName() + ".");
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (t.isOnline() && u.equals(challenges.get(t.getUniqueId()))) {
                        challenges.remove(t.getUniqueId());
                        challengeBet.remove(t.getUniqueId());
                    }
                }, 2400L);
            }
            case "daccept" -> {
                UUID ch = challenges.remove(u);
                Long betL = challengeBet.remove(u);
                long bet = betL == null ? 0 : betL;
                if (ch == null) { p.sendMessage(C + "cNo duel challenge."); return true; }
                Player c = Bukkit.getPlayer(ch);
                if (c == null || !c.isOnline()) { p.sendMessage(C + "cThey left."); return true; }
                if (fights.containsKey(u) || fights.containsKey(ch)) { p.sendMessage(C + "cSomeone is already duelling."); return true; }
                if (bet > 0) {
                    if (econ == null || !econ.has(p, bet) || !econ.has(c, bet)) {
                        p.sendMessage(C + "cSomeone can't afford the " + coins(bet) + " bet."); return true;
                    }
                    // 3.0.5: if the second withdraw fails, the first is refunded (never half-charged)
                    boolean w1 = econ.withdrawPlayer(p, bet).transactionSuccess();
                    boolean w2 = w1 && econ.withdrawPlayer(c, bet).transactionSuccess();
                    if (!w2) {
                        if (w1) econ.depositPlayer(p, bet);
                        p.sendMessage(C + "cPayment failed."); return true;
                    }
                }
                startFight(c, p, bet);
            }
            case "ddeny" -> {
                UUID ch = challenges.remove(u);
                challengeBet.remove(u);
                p.sendMessage(C + "cChallenge denied.");
                if (ch != null) msg(ch, C + "c" + p.getName() + " declined the duel.");
            }
            case "dstats" -> {
                if (args.length >= 1 && args[0].equalsIgnoreCase("top")) {
                    sender.sendMessage(C + "6\u2694 Duel leaderboard:");
                    ConfigurationSection s = data.getConfigurationSection("stats");
                    if (s == null || s.getKeys(false).isEmpty()) { sender.sendMessage(C + "7No duels yet."); return true; }
                    List<String> rows = new ArrayList<>();
                    for (String k : s.getKeys(false))
                        rows.add(k + "|" + s.getInt(k + ".wins", 0) + "|" + s.getInt(k + ".losses", 0));
                    rows.sort((x, y) -> {
                        String[] a1 = x.split("\\|"), b1 = y.split("\\|");
                        int w = Integer.compare(Integer.parseInt(b1[1]), Integer.parseInt(a1[1]));
                        return w != 0 ? w : Integer.compare(Integer.parseInt(a1[2]), Integer.parseInt(b1[2]));
                    });
                    for (int i = 0; i < Math.min(10, rows.size()); i++) {
                        String[] e = rows.get(i).split("\\|");
                        sender.sendMessage(C + "7#" + (i + 1) + " " + C + "e" + nm(UUID.fromString(e[0]))
                                + C + "7 - " + C + "a" + e[1] + "W " + C + "c" + e[2] + "L");
                    }
                } else {
                    int w = data.getInt("stats." + u + ".wins", 0), l = data.getInt("stats." + u + ".losses", 0);
                    p.sendMessage(C + "6\u2694 Your record: " + C + "a" + w + "W " + C + "c" + l + "L"
                            + C + "7 (" + (w + l == 0 ? "no duels yet" : Math.round(w * 100.0 / (w + l)) + "% win rate") + ").");
                }
            }
            case "duelarena" -> {
                if (!p.hasPermission("mavoduel.admin")) { p.sendMessage("OP only."); return true; }
                getConfig().set("arena.world", p.getWorld().getName());
                getConfig().set("arena.x", p.getLocation().getBlockX());
                getConfig().set("arena.y", p.getLocation().getBlockY());
                getConfig().set("arena.z", p.getLocation().getBlockZ());
                saveConfig(); loadCfg();
                p.sendMessage(C + "aDuel arena set at " + arena.getBlockX() + " " + arena.getBlockZ()
                        + " (radius " + radius + ").");
            }
            default -> p.sendMessage(C + "7/duel <player> [bet] | /daccept | /ddeny | /dstats [top] | /duelarena (OP)");
        }
        return true;
    }
}
