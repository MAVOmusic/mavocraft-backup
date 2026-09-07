package mavo.tpa;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** MAVOTpa 1.0.0 - teleport requests (Discord CW#4 idea 11, inspired by EssentialsX).
 *  /tpa <player>, /tpahere <player> -> target accepts/denies; 3s stand-still warmup with
 *  standard safety (moving cancels, monsters block), personal send cooldown. */
public final class Tpa extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private record Req(UUID from, boolean here, long time) {}

    private final Map<UUID, Req> pending = new LinkedHashMap<>();       // target -> request
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final Map<UUID, BukkitTask> warmups = new HashMap<>();
    private final Map<UUID, Location> warmLoc = new HashMap<>();

    private int cooldownSec = 30, expireSec = 120, warmupSec = 3, monsterRadius = 12;

    @Override public void onEnable() {
        saveDefaultConfig();
        cooldownSec = Math.max(1, getConfig().getInt("cooldown-seconds", 30));
        expireSec = Math.max(5, getConfig().getInt("expire-seconds", 120));
        warmupSec = Math.max(0, getConfig().getInt("warmup-seconds", 3));
        monsterRadius = Math.max(0, getConfig().getInt("monster-radius", 12));
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOTpa v" + getDescription().getVersion() + " enabled.");
    }

    @Override public void onDisable() {
        for (BukkitTask t : warmups.values()) t.cancel();
        warmups.clear();
    }

    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private static String name(UUID u) {
        Player p = Bukkit.getPlayer(u);
        return p != null ? p.getName() : u.toString().substring(0, 8);
    }

    private boolean monstersNear(Player pl) {
        if (monsterRadius <= 0) return false;
        for (org.bukkit.entity.Entity en : pl.getNearbyEntities(monsterRadius, monsterRadius, monsterRadius))
            if (en instanceof Enemy && !en.isDead()) return true;
        return false;
    }

    private void request(Player from, Player to, boolean here) {
        if (from.equals(to)) { from.sendMessage(C + "cYou can't teleport to yourself."); return; }
        UUID f = from.getUniqueId(), t = to.getUniqueId();
        long now = System.currentTimeMillis();
        long cd = cooldown.getOrDefault(f, 0L);
        if (now < cd && !from.hasPermission("mavotpa.admin")) {
            from.sendMessage(C + "cCooldown - wait " + C + "e" + ((cd - now) / 1000) + "s" + C + "c.");
            return;
        }
        pending.put(t, new Req(f, here, now));
        cooldown.put(f, now + cooldownSec * 1000L);
        String verb = here ? "wants YOU to teleport to them" : "wants to teleport to you";
        to.sendMessage(C + "e\u2708 " + C + "a" + name(f) + C + "e " + verb + ". "
                + C + "a/tpaccept" + C + "e or " + C + "c/tpdeny" + C + "e. (expires in "
                + expireSec + "s)");
        to.playSound(to.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        from.sendMessage(C + "aRequest sent to " + C + "e" + name(t) + C + "a. Wait for them to accept.");
        Bukkit.getScheduler().runTaskLater(this, () -> {
            Req r = pending.get(t);
            if (r != null && r.from().equals(f) && System.currentTimeMillis() - r.time() > expireSec * 1000L) {
                pending.remove(t);
                Player p = Bukkit.getPlayer(t);
                if (p != null) p.sendMessage(C + "7Teleport request from " + name(f) + " expired.");
            }
        }, (expireSec + 5) * 20L);
    }

    private void accept(Player target, Player fromOpt) {
        Req r = pending.get(target.getUniqueId());
        if (r == null) { target.sendMessage(C + "cYou have no pending teleport requests."); return; }
        if (fromOpt != null && !r.from().equals(fromOpt.getUniqueId())) {
            target.sendMessage(C + "cNo pending request from " + fromOpt.getName() + "."); return;
        }
        pending.remove(target.getUniqueId());
        Player from = Bukkit.getPlayer(r.from());
        if (from == null || !from.isOnline()) { target.sendMessage(C + "cThat player left."); return; }
        target.sendMessage(C + "aRequest accepted.");
        if (r.here()) {
            // "come here": the TARGET teleports to the requester
            teleportWithWarmup(target, from.getLocation(), from.getLocation().getYaw(), from.getLocation().getPitch());
        } else {
            // "can I come": the REQUESTER teleports to the target
            teleportWithWarmup(from, target.getLocation(), target.getLocation().getYaw(), target.getLocation().getPitch());
        }
    }

    private void teleportWithWarmup(Player p, Location dest, float yaw, float pitch) {
        p.sendMessage(C + "eTeleporting in " + C + "a" + warmupSec + "s" + C + "e - stand still.");
        if (warmups.containsKey(p.getUniqueId())) warmups.remove(p.getUniqueId()).cancel();
        warmLoc.put(p.getUniqueId(), p.getLocation());
        if (warmupSec <= 0) { doTeleport(p, dest, yaw, pitch); return; }
        BukkitTask task = Bukkit.getScheduler().runTaskLater(this, () -> {
            warmups.remove(p.getUniqueId()); warmLoc.remove(p.getUniqueId());
            if (!p.isOnline()) return;
            if (monstersNear(p)) { p.sendMessage(C + "cTeleport cancelled - monsters nearby!"); return; }
            doTeleport(p, dest, yaw, pitch);
        }, warmupSec * 20L);
        warmups.put(p.getUniqueId(), task);
    }

    private void doTeleport(Player p, Location dest, float yaw, float pitch) {
        Location to = dest.clone();
        to.setYaw(yaw); to.setPitch(pitch);
        p.teleport(to);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
        p.sendMessage(C + "aTeleported!");
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
        BukkitTask t = warmups.get(e.getPlayer().getUniqueId());
        if (t == null) return;
        Location wl = warmLoc.get(e.getPlayer().getUniqueId());
        if (wl == null) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX() || e.getFrom().getBlockZ() != e.getTo().getBlockZ()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()) {
            t.cancel(); warmups.remove(e.getPlayer().getUniqueId()); warmLoc.remove(e.getPlayer().getUniqueId());
            e.getPlayer().sendMessage(C + "cTeleport cancelled - you moved!");
        }
    }

    @EventHandler public void onQuit(PlayerQuitEvent e) {
        pending.remove(e.getPlayer().getUniqueId());
        pending.entrySet().removeIf(en -> en.getValue().from().equals(e.getPlayer().getUniqueId()));
    }

    @Override public List<String> onTabComplete(CommandSender sender, Command c, String l, String[] a) {
        if (a.length == 1 && (c.getName().equalsIgnoreCase("tpa") || c.getName().equalsIgnoreCase("tpahere")))
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
        String n = cmd.getName().toLowerCase(java.util.Locale.ROOT);
        switch (n) {
            case "tpa" -> {
                if (args.length < 1) { p.sendMessage(C + "cUsage: /tpa <player>"); return true; }
                Player to = Bukkit.getPlayerExact(args[0]);
                if (to == null) { p.sendMessage(C + "cPlayer offline."); return true; }
                request(p, to, false);
            }
            case "tpahere" -> {
                if (args.length < 1) { p.sendMessage(C + "cUsage: /tpahere <player>"); return true; }
                Player to = Bukkit.getPlayerExact(args[0]);
                if (to == null) { p.sendMessage(C + "cPlayer offline."); return true; }
                request(p, to, true);
            }
            case "tpaccept" -> accept(p, args.length >= 1 ? Bukkit.getPlayerExact(args[0]) : null);
            case "tpdeny" -> {
                Req r = pending.remove(p.getUniqueId());
                if (r == null) p.sendMessage(C + "cNo pending requests.");
                else {
                    p.sendMessage(C + "cRequest denied.");
                    Player f = Bukkit.getPlayer(r.from());
                    if (f != null) f.sendMessage(C + "c" + p.getName() + " denied your teleport request.");
                }
            }
            case "tpacancel" -> {
                boolean removed = pending.entrySet().removeIf(en -> en.getValue().from().equals(p.getUniqueId()));
                p.sendMessage(removed ? C + "aAll your pending requests were cancelled." : C + "7Nothing to cancel.");
            }
            default -> p.sendMessage(C + "7/tpa <player> | /tpahere <player> | /tpaccept [player] | /tpdeny | /tpacancel");
        }
        return true;
    }
}
