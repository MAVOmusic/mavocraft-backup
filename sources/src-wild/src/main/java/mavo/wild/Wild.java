package mavo.wild;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class Wild extends JavaPlugin implements Listener {

    private final Random rng = new Random();
    private final Map<UUID, Long> cooldown = new HashMap<>();
    private final java.util.Set<UUID> locked = new java.util.HashSet<>();
    private final Map<UUID, Long> lastNag = new HashMap<>();
    private final Map<UUID, Long> lastPortalMsg = new HashMap<>();
    private final Map<UUID, int[]> pendingCorner = new HashMap<>();

    // portal countdowns + command warmups
    private final Map<UUID, BukkitTask> counting = new HashMap<>();
    private final Map<UUID, Box> countingBox = new HashMap<>();
    private final Map<UUID, BukkitTask> warmups = new HashMap<>();

    private NamespacedKey holoKey;
    private long tick = 0;

    private static final class Box {
        boolean on;
        String world = "world";
        int x1, y1, z1, x2, y2, z2;
    }

    private final Box wildBox = new Box();
    private final Box homeBox = new Box();

    @Override
    public void onEnable() {
        holoKey = new NamespacedKey(this, "portalholo");
        saveDefaultCfg();
        // migrate stale short-range configs from older jars
        int maxR = getConfig().getInt("max-radius", 400000);
        if (maxR > 0 && maxR <= 5000) {
            getConfig().set("max-radius", 400000);
            if (!getConfig().isSet("blacklist-radius")) getConfig().set("blacklist-radius", 200);
            if (!getConfig().isSet("blacklist-size")) getConfig().set("blacklist-size", 50);
            saveConfig();
            getLogger().info("Migrated max-radius " + maxR + " -> 400000");
        }
        // old 2k minimum -> 5k (matches live portal range)
        if (getConfig().getInt("min-radius", 5000) <= 2000) {
            getConfig().set("min-radius", 5000);
            saveConfig();
            getLogger().info("Migrated min-radius -> 5000");
        }
        loadPortals();
        getServer().getPluginManager().registerEvents(this, this);
        startPortalFx();
        // always rewrite portal holos on boot so jar text changes stick (kills 2k-5k orphans)
        Bukkit.getScheduler().runTaskLater(this, this::refreshPortalHolos, 60L);
        getLogger().info("MAVOWild enabled (/wild, /rtp)"
                + (wildBox.on ? " + Wild Portal" : "") + (homeBox.on ? " + Home Portal" : "") + ".");
    }

    /** Respawn wild/home portal holos from current config + live text builders. */
    private void refreshPortalHolos() {
        loadPortals();
        if (wildBox.on) spawnHolo(wildBox, "portal", wildHoloText());
        if (homeBox.on) spawnHolo(homeBox, "homeportal", homeHoloText());
    }

    private void saveDefaultCfg() {
        getConfig().addDefault("min-radius", 5000);
        getConfig().addDefault("max-radius", 400000);
        getConfig().addDefault("blacklist-radius", 200);
        getConfig().addDefault("blacklist-size", 50);
        getConfig().addDefault("cooldown-seconds", 300);
        getConfig().addDefault("lock-new-players", false); // portals at spawn cover onboarding now
        getConfig().addDefault("portal-countdown-seconds", 3);
        getConfig().addDefault("command-warmup-seconds", 3);
        getConfig().addDefault("monster-radius", 12);
        getConfig().options().copyDefaults(true);
        saveConfig();
    }

    private void loadBox(Box b, String prefix) {
        b.on = getConfig().getBoolean(prefix + ".enabled", false) && getConfig().contains(prefix + ".x1");
        b.world = getConfig().getString(prefix + ".world", "world");
        b.x1 = Math.min(getConfig().getInt(prefix + ".x1"), getConfig().getInt(prefix + ".x2"));
        b.x2 = Math.max(getConfig().getInt(prefix + ".x1"), getConfig().getInt(prefix + ".x2"));
        b.y1 = Math.min(getConfig().getInt(prefix + ".y1"), getConfig().getInt(prefix + ".y2"));
        b.y2 = Math.max(getConfig().getInt(prefix + ".y1"), getConfig().getInt(prefix + ".y2"));
        b.z1 = Math.min(getConfig().getInt(prefix + ".z1"), getConfig().getInt(prefix + ".z2"));
        b.z2 = Math.max(getConfig().getInt(prefix + ".z1"), getConfig().getInt(prefix + ".z2"));
    }

    private void loadPortals() {
        loadBox(wildBox, "portal");
        loadBox(homeBox, "homeportal");
    }

    private boolean inBox(Box b, Location l) {
        if (!b.on || l.getWorld() == null || !l.getWorld().getName().equals(b.world)) return false;
        return l.getBlockX() >= b.x1 && l.getBlockX() <= b.x2
                && l.getBlockY() >= b.y1 && l.getBlockY() <= b.y2
                && l.getBlockZ() >= b.z1 && l.getBlockZ() <= b.z2;
    }

    private boolean monstersNear(Player pl) {
        if (pl.getGameMode() != GameMode.SURVIVAL) return false;
        int r = getConfig().getInt("monster-radius", 12);
        for (Entity en : pl.getNearbyEntities(r, Math.max(6, r / 2.0), r))
            if (en instanceof Enemy && !en.isDead()) return true;
        return false;
    }

    /* ================= PORTAL FX: dense animated curtain ================= */

    private void startPortalFx() {
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            tick += 2;
            renderPortal(wildBox, Color.fromRGB(255, 40, 40), Color.fromRGB(50, 110, 255), 0.85f);
            renderPortal(homeBox, Color.fromRGB(40, 220, 90), Color.fromRGB(255, 200, 40), 1.25f);
        }, 40L, 2L);
    }

    private void renderPortal(Box b, Color cA, Color cB, float soundPitch) {
        if (!b.on) return;
        World w = Bukkit.getWorld(b.world);
        if (w == null) return;
        double cxd = (b.x1 + b.x2 + 1) / 2.0, cyd = (b.y1 + b.y2 + 1) / 2.0, czd = (b.z1 + b.z2 + 1) / 2.0;
        boolean near = false;
        for (Player p : w.getPlayers()) {
            Location l = p.getLocation();
            if (Math.abs(l.getX() - cxd) < 44 && Math.abs(l.getZ() - czd) < 44) { near = true; break; }
        }
        if (!near) return;

        boolean thinZ = (b.z2 - b.z1) <= (b.x2 - b.x1); // portal plane faces north/south if depth(z) is thin
        double wSpan = thinZ ? (b.x2 - b.x1 + 1) : (b.z2 - b.z1 + 1);
        double hSpan = (b.y2 - b.y1 + 1);

        // 1) LIQUID GLASS SHEET - fine dust grid, small particles packed tight, two
        //    slightly offset layers with a slow wave rolling through = molten glass look
        if (tick % 4 == 0) {
            double spacing = Math.max(0.28, Math.sqrt((wSpan * hSpan) / 420.0));
            for (double u = 0.15; u <= wSpan - 0.15; u += spacing) {
                for (double v = 0.15; v <= hSpan - 0.15; v += spacing) {
                    double wave = Math.sin(u * 1.2 + v * 0.6 + tick * 0.09);
                    double depth = wave * 0.14;
                    double px, py = b.y1 + v, pz;
                    if (thinZ) { px = b.x1 + u; pz = czd + depth; }
                    else { px = cxd + depth; pz = b.z1 + u; }
                    // color flows diagonally across the sheet like currents in liquid
                    double mix = 0.5 + 0.5 * Math.sin(u * 0.55 - v * 0.4 + tick * 0.05);
                    Color from = blend(cA, cB, mix);
                    Color to = blend(cA, cB, 1.0 - mix);
                    w.spawnParticle(Particle.DUST_COLOR_TRANSITION, px, py, pz, 1, 0.02, 0.02, 0.02, 0,
                            new Particle.DustTransition(from, to, 0.95f));
                }
            }
        }

        // 2) FLUID SUCTION - nautilus streams constantly flowing INTO the surface
        //    (this is what sells "liquid"; they visibly get pulled in)
        w.spawnParticle(Particle.NAUTILUS, cxd, cyd, czd, 6,
                thinZ ? wSpan / 3.0 : 0.2, hSpan / 3.0, thinZ ? 0.2 : wSpan / 3.0, 0.6);

        // 3) slow glowing vortex arms
        double maxR = Math.min(wSpan, hSpan) / 2.0 - 0.3;
        for (int arm = 0; arm < 2; arm++) {
            for (double r = 0.4; r < maxR; r += 0.75) {
                double ang = tick * 0.11 + arm * Math.PI + r * 0.9;
                double du = Math.cos(ang) * r, dv = Math.sin(ang) * r;
                double px, py = cyd + dv, pz;
                if (thinZ) { px = cxd + du; pz = czd; } else { px = cxd; pz = czd + du; }
                w.spawnParticle(Particle.END_ROD, px, py, pz, 1, 0, 0, 0, 0.001);
            }
        }

        // 4) shimmer + floor sparks
        if (tick % 6 == 0) {
            for (int i = 0; i < 4; i++) {
                double u = rng.nextDouble() * wSpan, v = rng.nextDouble() * hSpan;
                double px, py = b.y1 + v, pz;
                if (thinZ) { px = b.x1 + u; pz = czd; } else { px = cxd; pz = b.z1 + u; }
                w.spawnParticle(Particle.ELECTRIC_SPARK, px, py, pz, 1, 0.05, 0.05, 0.05, 0.02);
            }
        }

        // 5) ambient hum
        if (tick % 90 == 0) {
            Location c = new Location(w, cxd, cyd, czd);
            for (Player p : w.getPlayers())
                if (p.getLocation().distanceSquared(c) < 625)
                    p.playSound(c, Sound.BLOCK_PORTAL_AMBIENT, 0.55f, soundPitch);
        }
    }

    private Color blend(Color a, Color b, double t) {
        t = Math.max(0, Math.min(1, t));
        return Color.fromRGB(
                (int) (a.getRed() + (b.getRed() - a.getRed()) * t),
                (int) (a.getGreen() + (b.getGreen() - a.getGreen()) * t),
                (int) (a.getBlue() + (b.getBlue() - a.getBlue()) * t));
    }

    /* ================= holograms ================= */

    private void removeHolo(String prefix) {
        String id = getConfig().getString(prefix + ".holo", null);
        if (id == null) return;
        try {
            Entity ent = Bukkit.getEntity(UUID.fromString(id));
            if (ent != null) ent.remove();
        } catch (IllegalArgumentException ignored) {}
        getConfig().set(prefix + ".holo", null);
    }

    private void spawnHolo(Box b, String prefix, String text) {
        removeHolo(prefix);
        World w = Bukkit.getWorld(b.world);
        if (w == null) return;
        Location loc = new Location(w, (b.x1 + b.x2) / 2.0 + 0.5, b.y2 + 2.2, (b.z1 + b.z2) / 2.0 + 0.5);
        loc.getChunk().load();
        // sweep tagged + legacy untagged portal signs (old jars / lost UUIDs / 2k-5k text)
        for (Entity ent : w.getNearbyEntities(loc, 12, 12, 12))
            if (ent instanceof TextDisplay td0 && isStalePortalHolo(td0))
                td0.remove();
        TextDisplay td = w.spawn(loc, TextDisplay.class, d -> {
            d.setText(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(true);
            try { d.setDefaultBackground(false); d.setBackgroundColor(Color.fromARGB(200, 10, 10, 20)); } catch (Throwable ignored) {}
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(280);
            var tr = d.getTransformation();
            tr.getScale().set(2.2f);
            d.setTransformation(tr);
            d.setViewRange(1.4f);
            try { d.setBrightness(new Display.Brightness(15, 15)); } catch (Throwable ignored) {}
            d.setPersistent(true);
            d.getPersistentDataContainer().set(holoKey, PersistentDataType.BYTE, (byte) 1);
        });
        getConfig().set(prefix + ".holo", td.getUniqueId().toString());
        saveConfig();
    }

    private String wildHoloText() {
        int cd = getConfig().getInt("portal-countdown-seconds", 3);
        int min = Math.max(1, getConfig().getInt("min-radius", 5000));
        int max = Math.max(min, getConfig().getInt("max-radius", 400000));
        String range = String.format(Locale.ROOT, "%,d\u2013%,d", min, max);
        return ChatColor.RED + "" + ChatColor.BOLD + "\u25C6 WILD " + ChatColor.BLUE + "" + ChatColor.BOLD + "PORTAL \u25C6"
                + "\n" + ChatColor.WHITE + "Random teleport " + ChatColor.AQUA + range + ChatColor.WHITE + " blocks out"
                + "\n" + ChatColor.YELLOW + "Stand inside " + cd + "s" + ChatColor.GRAY + " \u00B7 step out = cancel"
                + "\n" + ChatColor.RED + "" + ChatColor.BOLD + "NO WAY BACK!";
    }

    private String homeHoloText() {
        int cd = getConfig().getInt("portal-countdown-seconds", 3);
        return ChatColor.GREEN + "" + ChatColor.BOLD + "\u2302 HOME " + ChatColor.GOLD + "" + ChatColor.BOLD + "PORTAL \u2302"
                + "\n" + ChatColor.WHITE + "Travel to your " + ChatColor.GOLD + "first home"
                + "\n" + ChatColor.YELLOW + "Stand inside " + cd + "s" + ChatColor.GRAY + " \u00B7 step out = cancel"
                + "\n" + ChatColor.GRAY + "No home? Bed + right-click in your chunk";
    }

    /** Tagged mavowild holos OR legacy untagged portal text (2k-5k era). */
    private boolean isStalePortalHolo(TextDisplay td) {
        if (td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE)) return true;
        String raw = td.getText();
        if (raw == null) return false;
        String s = ChatColor.stripColor(raw).toLowerCase(Locale.ROOT);
        if (s.contains("wild") && s.contains("portal")) return true;
        if (s.contains("home") && s.contains("portal")) return true;
        if (s.contains("2,000") && s.contains("5,000")) return true;
        if (s.contains("2000") && s.contains("5000")) return true;
        if (s.contains("2,000") && s.contains("400,000")) return true; // refresh even current text
        if (s.contains("no way back")) return true;
        return false;
    }

    /**
     * Admin: wipe known MAVO floating signs in a radius, then fully restore them.
     * Default radius 60. Always restores Wild/Home, Vault portals, PortalRoom jumps,
     * Tavern, Wishing Well, and ShopNPC signs (Tutorial/Louie/Curator/shopkeepers).
     */
    /**
     * @param full when false (default): only Wild/Home portal holos — safe.
     *             when true (/holoreset all): full plaza wipe+restore including ShopNPC etc.
     */
    private int holoresetAll(Player pl, double radius, boolean full) {
        World w = pl.getWorld();
        Location c = pl.getLocation();
        int cr = Math.max(2, (int) Math.ceil(radius / 16.0) + 1);
        int cx = c.getBlockX() >> 4, cz = c.getBlockZ() >> 4;
        for (int dx = -cr; dx <= cr; dx++)
            for (int dz = -cr; dz <= cr; dz++)
                w.getChunkAt(cx + dx, cz + dz).load();

        int removed = 0;
        for (Entity ent : w.getNearbyEntities(c, radius, radius, radius)) {
            if (!(ent instanceof TextDisplay td)) continue;
            boolean kill = full ? isResettableHolo(td) : isStalePortalHolo(td);
            if (kill) {
                td.remove();
                removed++;
            }
        }

        try { refreshPortalHolos(); } catch (Throwable ex) {
            getLogger().warning("holoreset wild holos: " + ex.getMessage());
        }

        if (full) {
            Bukkit.getScheduler().runTask(this, () -> restorePeerHolos(pl, radius));
        }
        return removed;
    }

    private void restorePeerHolos(Player pl, double radius) {
        Location c = pl.getLocation();
        World w = c.getWorld();

        // PortalRoom jump holos
        try {
            var pr = Bukkit.getPluginManager().getPlugin("MAVOPortalRoom");
            if (pr != null && pr.isEnabled())
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "portalroom reload");
        } catch (Throwable ex) {
            getLogger().warning("holoreset portalroom: " + ex.getMessage());
        }

        // Vault gate/return + chest holos
        try {
            var v = Bukkit.getPluginManager().getPlugin("MAVOVault");
            if (v != null && v.isEnabled())
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "vaultroom reload");
        } catch (Throwable ex) {
            getLogger().warning("holoreset vault: " + ex.getMessage());
        }

        // Tavern bed holo
        try {
            var ta = Bukkit.getPluginManager().getPlugin("MAVOTavern");
            if (ta != null && ta.isEnabled())
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "tavern reload");
        } catch (Throwable ex) {
            getLogger().warning("holoreset tavern: " + ex.getMessage());
        }

        // ShopNPC floating signs (Tutorial, Louie, Curator, shopkeepers…)
        try {
            var sn = Bukkit.getPluginManager().getPlugin("MAVOShopNPC");
            if (sn != null && sn.isEnabled())
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "shopnpc resholo");
        } catch (Throwable ex) {
            getLogger().warning("holoreset shopnpc: " + ex.getMessage());
        }

        // Wishing well
        try {
            var lc = Bukkit.getPluginManager().getPlugin("MAVOLuckyCoins");
            if (lc != null && lc.isEnabled() && lc instanceof org.bukkit.plugin.java.JavaPlugin jp) {
                var cfg = jp.getConfig();
                if (cfg.getBoolean("wishing-well.enabled", false)
                        && w != null
                        && w.getName().equals(cfg.getString("wishing-well.world", "world"))) {
                    int wx = cfg.getInt("wishing-well.x"), wy = cfg.getInt("wishing-well.y"), wz = cfg.getInt("wishing-well.z");
                    if (c.distanceSquared(new Location(w, wx + 0.5, wy, wz + 0.5)) <= radius * radius) {
                        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "wish well");
                    }
                }
            }
        } catch (Throwable ex) {
            getLogger().warning("holoreset well: " + ex.getMessage());
        }
    }

    /**
     * Only remove holos we can restore. Prefer PDC tags; text fingerprints only for
     * known portal/well/jump patterns — NOT generic shop labels we might miss restoring
     * on older ShopNPC jars without resholo.
     */
    private boolean isResettableHolo(TextDisplay td) {
        var pdc = td.getPersistentDataContainer();
        if (pdc.has(holoKey, PersistentDataType.BYTE)) return true;
        for (var key : pdc.getKeys()) {
            String k = key.getKey().toLowerCase(Locale.ROOT);
            String ns = key.getNamespace().toLowerCase(Locale.ROOT);
            // tagged MAVO plugin holos (incl. shopnpcholo once 1.3.2 is live)
            if (ns.contains("mavo") && (k.contains("holo") || k.contains("portal") || k.contains("jump")
                    || k.contains("vault") || k.contains("tavern") || k.contains("well") || k.contains("shop")))
                return true;
            if (k.equals("portalholo") || k.equals("vaultholo") || k.equals("jumpholo")
                    || k.equals("tavernholo") || k.equals("shopnpcholo") || k.startsWith("jumpholo_")
                    || k.startsWith("portalholo_"))
                return true;
        }
        String raw = td.getText();
        if (raw == null) return false;
        String s = ChatColor.stripColor(raw).toLowerCase(Locale.ROOT);
        // portal / jump / well / tavern fingerprints (always restorable by peer reload)
        if (s.contains("no way back")) return true;
        if (s.contains("wild") && s.contains("portal")) return true;
        if (s.contains("home") && s.contains("portal")) return true;
        if (s.contains("2,000") || s.contains("2000")) return true; // wild range text
        if (s.contains("the vault") || s.contains("portal room")) return true;
        if (s.contains("per jump")) return true;
        if (s.contains("wishing well") || s.contains("tavern bed")) return true;
        if (s.contains("returning to spawn") || s.contains("back to spawn")) return true;
        // plaza NPC holos — only if ShopNPC is loaded (we can restore via resholo / defaults)
        if (Bukkit.getPluginManager().getPlugin("MAVOShopNPC") != null) {
            if (s.contains("tutorial") || s.contains("lucky louie") || s.contains("the museum")
                    || s.contains("curator") || s.contains("achievements") || s.contains("professions")
                    || s.contains("updates") || s.contains("casino") || s.contains("museum"))
                return true;
            // generic "right-click" shopkeeper style above villagers near spawn
            if (s.contains("right-click") || s.contains("right click")) return true;
        }
        return false;
    }



    /* ================= portal countdown ================= */

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        Player pl = e.getPlayer();
        if (e.getTo() != null) {
            // INSTANT cancel the moment they leave the portal region
            Box active = countingBox.get(pl.getUniqueId());
            if (active != null && !inBox(active, e.getTo())) {
                cancelCountdown(pl.getUniqueId());
                pl.sendTitle(" ", "", 0, 1, 0);
                pl.sendMessage(ChatColor.GRAY + "Portal cancelled - you stepped out.");
            } else if (active == null && !counting.containsKey(pl.getUniqueId())) {
                if (inBox(wildBox, e.getTo()) && !inBox(wildBox, e.getFrom())) tryStartCountdown(pl, true);
                else if (inBox(homeBox, e.getTo()) && !inBox(homeBox, e.getFrom())) tryStartCountdown(pl, false);
            }
        }
        if (!locked.contains(pl.getUniqueId())) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        e.setTo(e.getFrom());
        nag(pl, false);
    }

    private void tryStartCountdown(Player pl, boolean wild) {
        long now = System.currentTimeMillis();
        if (wild) {
            int cd = getConfig().getInt("cooldown-seconds", 300);
            Long last = cooldown.get(pl.getUniqueId());
            if (last != null && !pl.hasPermission("mavowild.nocooldown") && now - last < cd * 1000L) {
                Long msg = lastPortalMsg.get(pl.getUniqueId());
                if (msg == null || now - msg > 3000) {
                    lastPortalMsg.put(pl.getUniqueId(), now);
                    long left = (cd * 1000L - (now - last)) / 1000L;
                    pl.sendMessage(ChatColor.RED + "The portal hums... but refuses. " + ChatColor.GRAY + "(" + left + "s cooldown)");
                }
                return;
            }
        }
        if (monstersNear(pl)) {
            pl.sendMessage(ChatColor.RED + "\u2694 Monsters nearby - the portal won't open! Deal with them first.");
            return;
        }
        final Box box = wild ? wildBox : homeBox;
        final int total = Math.max(1, getConfig().getInt("portal-countdown-seconds", 3));
        final ChatColor col = wild ? ChatColor.RED : ChatColor.GREEN;
        pl.sendTitle(col + "" + ChatColor.BOLD + total, ChatColor.GRAY + "Hold still... step out to cancel", 0, 25, 5);
        pl.playSound(pl.getLocation(), wild ? Sound.BLOCK_PORTAL_TRIGGER : Sound.BLOCK_RESPAWN_ANCHOR_SET_SPAWN, 0.4f, wild ? 1.4f : 1.0f);
        final int[] ticksLeft = {total * 20};
        countingBox.put(pl.getUniqueId(), box);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!pl.isOnline() || !inBox(box, pl.getLocation())) {
                cancelCountdown(pl.getUniqueId());
                if (pl.isOnline()) {
                    pl.sendTitle(" ", "", 0, 1, 0);
                    pl.sendMessage(ChatColor.GRAY + "Portal cancelled - you stepped out.");
                }
                return;
            }
            if (monstersNear(pl)) {
                cancelCountdown(pl.getUniqueId());
                pl.sendTitle(" ", "", 0, 1, 0);
                pl.sendMessage(ChatColor.RED + "\u2694 Monsters nearby - portal cancelled! No escaping a fight.");
                return;
            }
            ticksLeft[0] -= 5;
            if (ticksLeft[0] > 0) {
                if (ticksLeft[0] % 20 == 0) {
                    int sec = ticksLeft[0] / 20;
                    pl.sendTitle(col + "" + ChatColor.BOLD + sec, ChatColor.GRAY + "Hold still... step out to cancel", 0, 25, 5);
                    pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f + (total - sec) * 0.3f);
                }
                return;
            }
            cancelCountdown(pl.getUniqueId());
            pl.sendTitle(" ", "", 0, 1, 0);
            pl.playSound(pl.getLocation(), Sound.BLOCK_PORTAL_TRAVEL, 0.35f, wild ? 1.2f : 1.6f);
            if (wild) {
                pl.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "\u27A4 " + ChatColor.BLUE + "The Wild Portal takes hold of you...");
                doWildTeleport(pl);
            } else {
                doHomeTeleport(pl);
            }
        }, 5L, 5L);
        counting.put(pl.getUniqueId(), task);
    }

    private void cancelCountdown(UUID id) {
        BukkitTask t = counting.remove(id);
        if (t != null) t.cancel();
        countingBox.remove(id);
    }

    /* ================= command warmup (3s, move/monster cancel) ================= */

    private void startWarmup(Player pl, ChatColor col, String label, Runnable action) {
        if (monstersNear(pl)) {
            pl.sendMessage(ChatColor.RED + "\u2694 Monsters nearby - can't teleport! Fight or run first.");
            return;
        }
        BukkitTask old = warmups.remove(pl.getUniqueId());
        if (old != null) old.cancel();
        final int total = Math.max(1, getConfig().getInt("command-warmup-seconds", 3));
        final Location start = pl.getLocation().clone();
        pl.sendTitle(col + "" + ChatColor.BOLD + total, ChatColor.GRAY + "Stand still - teleporting to " + label, 0, 25, 5);
        pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f);
        final int[] ticksLeft = {total * 20};
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (!pl.isOnline()) { BukkitTask t = warmups.remove(pl.getUniqueId()); if (t != null) t.cancel(); return; }
            if (!pl.getWorld().equals(start.getWorld()) || pl.getLocation().distanceSquared(start) > 0.5) {
                BukkitTask t = warmups.remove(pl.getUniqueId()); if (t != null) t.cancel();
                pl.sendTitle(" ", "", 0, 1, 0);
                pl.sendMessage(ChatColor.RED + "Teleport cancelled - you moved.");
                return;
            }
            if (monstersNear(pl)) {
                BukkitTask t = warmups.remove(pl.getUniqueId()); if (t != null) t.cancel();
                pl.sendTitle(" ", "", 0, 1, 0);
                pl.sendMessage(ChatColor.RED + "\u2694 Monsters nearby - teleport cancelled! No escaping a fight.");
                return;
            }
            ticksLeft[0] -= 5;
            if (ticksLeft[0] > 0) {
                if (ticksLeft[0] % 20 == 0) {
                    int sec = ticksLeft[0] / 20;
                    pl.sendTitle(col + "" + ChatColor.BOLD + sec, ChatColor.GRAY + "Stand still - teleporting to " + label, 0, 25, 5);
                    pl.playSound(pl.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 1f, 1f + (total - sec) * 0.3f);
                }
                return;
            }
            BukkitTask t = warmups.remove(pl.getUniqueId()); if (t != null) t.cancel();
            pl.sendTitle(" ", "", 0, 1, 0);
            action.run();
        }, 5L, 5L);
        warmups.put(pl.getUniqueId(), task);
    }

    // /spawn gets the same treatment - intercept, warm up, then hand to Essentials
    private final java.util.Set<UUID> spawnBypass = new java.util.HashSet<>();

    @EventHandler(ignoreCancelled = true)
    public void onSpawnCmd(PlayerCommandPreprocessEvent e) {
        String low = e.getMessage().trim().toLowerCase(Locale.ROOT);
        if (!low.equals("/spawn") && !low.equals("/espawn") && !low.equals("/essentials:spawn") && !low.equals("/essentialsspawn:spawn")) return;
        Player pl = e.getPlayer();
        if (spawnBypass.remove(pl.getUniqueId())) return;                 // warmup already served - let it through
        if (locked.contains(pl.getUniqueId())) return;                    // locked handler deals with them
        if (pl.getGameMode() != GameMode.SURVIVAL) return;                // creative admins teleport instantly
        e.setCancelled(true);
        startWarmup(pl, ChatColor.GOLD, "spawn", () -> {
            spawnBypass.add(pl.getUniqueId());
            pl.performCommand("spawn");
            spawnBypass.remove(pl.getUniqueId());
        });
    }

    /* ================= home teleport (reads MAVOHomes data) ================= */

    private void doHomeTeleport(Player pl) {
        var mh = Bukkit.getPluginManager().getPlugin("MAVOHomes");
        if (mh == null) { pl.sendMessage(ChatColor.RED + "Homes aren't available right now."); return; }
        File f = new File(mh.getDataFolder(), "homes.yml");
        YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
        ConfigurationSection sec = y.getConfigurationSection("players." + pl.getUniqueId());
        if (sec == null || sec.getKeys(false).isEmpty()) {
            pl.sendMessage(ChatColor.RED + "You don't have a home yet! " + ChatColor.GRAY
                    + "Claim a chunk (" + ChatColor.GREEN + "/chunk claim" + ChatColor.GRAY + "), place a "
                    + ChatColor.GREEN + "bed" + ChatColor.GRAY + " in it and " + ChatColor.GREEN + "right-click" + ChatColor.GRAY + " it.");
            return;
        }
        String first = sec.getKeys(false).iterator().next();
        ConfigurationSection hs = sec.getConfigurationSection(first);
        if (hs == null) { pl.sendMessage(ChatColor.RED + "Home data error - try /home instead."); return; }
        World w = Bukkit.getWorld(hs.getString("world", "world"));
        if (w == null) { pl.sendMessage(ChatColor.RED + "Home world isn't loaded."); return; }
        String name = hs.getString("name", "home");
        Location t = new Location(w, hs.getDouble("x"), hs.getDouble("y"), hs.getDouble("z"),
                (float) hs.getDouble("yaw"), (float) hs.getDouble("pitch"));
        pl.sendMessage(ChatColor.GREEN + "" + ChatColor.BOLD + "\u2302 " + ChatColor.GOLD + "The Home Portal carries you to " + ChatColor.GREEN + name + ChatColor.GOLD + "...");
        pl.teleportAsync(t).thenAccept(ok -> {
            if (ok) pl.playSound(t, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1.2f);
        });
    }

    /* ================= new-player lock ================= */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player pl = e.getPlayer();
        if (!getConfig().getBoolean("lock-new-players", true)) return;
        if (pl.hasPlayedBefore()) return;
        locked.add(pl.getUniqueId());
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (pl.isOnline() && locked.contains(pl.getUniqueId()))
                nag(pl, true);
        }, 80L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        UUID id = e.getPlayer().getUniqueId();
        locked.remove(id);
        lastNag.remove(id);
        lastPortalMsg.remove(id);
        cancelCountdown(id);
        BukkitTask t = warmups.remove(id);
        if (t != null) t.cancel();
    }

    private void nag(Player pl, boolean force) {
        long now = System.currentTimeMillis();
        Long last = lastNag.get(pl.getUniqueId());
        if (!force && last != null && now - last < 3000) return;
        lastNag.put(pl.getUniqueId(), now);
        pl.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "\u27A4 " + ChatColor.GOLD
                + "Welcome! Type " + ChatColor.YELLOW + "/wild" + ChatColor.GOLD
                + " (or " + ChatColor.YELLOW + "/rtp" + ChatColor.GOLD + ") to start your adventure!");
        pl.sendMessage(ChatColor.GRAY + "You can't move until you teleport into the wild.");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedBreak(BlockBreakEvent e) {
        if (locked.contains(e.getPlayer().getUniqueId())) { e.setCancelled(true); nag(e.getPlayer(), false); }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLockedPlace(BlockPlaceEvent e) {
        if (locked.contains(e.getPlayer().getUniqueId())) { e.setCancelled(true); nag(e.getPlayer(), false); }
    }

    @EventHandler
    public void onCmd(PlayerCommandPreprocessEvent e) {
        if (!locked.contains(e.getPlayer().getUniqueId())) return;
        String c = e.getMessage().toLowerCase(Locale.ROOT).split(" ")[0];
        if (c.equals("/wild") || c.equals("/rtp") || c.startsWith("/msg") || c.startsWith("/r")
                || c.startsWith("/tell") || c.startsWith("/help") || c.startsWith("/updates")
                || c.startsWith("/guide")) return;
        e.setCancelled(true);
        nag(e.getPlayer(), false);
    }

    /* ================= commands ================= */

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player pl)) { sender.sendMessage("In-game only."); return true; }

        // standalone /holoreset [radius]
        if (command.getName().equalsIgnoreCase("holoreset")) {
            if (!pl.hasPermission("mavowild.admin")) { pl.sendMessage(ChatColor.RED + "No permission."); return true; }
            double rad = 60;
            boolean full = false;
            for (String a : args) {
                if (a.equalsIgnoreCase("all") || a.equalsIgnoreCase("full") || a.equalsIgnoreCase("plaza")) full = true;
                else try { rad = Math.max(8, Math.min(128, Double.parseDouble(a))); } catch (NumberFormatException ignored) {}
            }
            if (full) {
                pl.sendMessage(ChatColor.YELLOW + "Full plaza holoreset (radius " + (int) rad + ")...");
            } else {
                pl.sendMessage(ChatColor.GRAY + "Wild/Home portal holos only (radius " + (int) rad
                        + "). " + ChatColor.DARK_GRAY + "Add 'all' for full plaza.");
            }
            int n = holoresetAll(pl, rad, full);
            pl.sendMessage(ChatColor.GREEN + "\u2714 Removed " + n + " sign(s), Wild/Home holos rewritten"
                    + (full ? ChatColor.GRAY + " + peer restores queued." : ChatColor.GRAY + "."));
            pl.playSound(pl.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.2f);
            return true;
        }

        // /wild holoreset [radius]  OR  /holoreset [radius]
        if (args.length >= 1 && (args[0].equalsIgnoreCase("holoreset") || args[0].equalsIgnoreCase("resetholo")
                || args[0].equalsIgnoreCase("holo") && args.length >= 2 && args[1].equalsIgnoreCase("reset"))) {
            if (!pl.hasPermission("mavowild.admin")) { pl.sendMessage(ChatColor.RED + "No permission."); return true; }
            double rad = 60;
            boolean full = false;
            int start = args[0].equalsIgnoreCase("holo") ? 2 : 1;
            for (int i = start; i < args.length; i++) {
                String a = args[i];
                if (a.equalsIgnoreCase("all") || a.equalsIgnoreCase("full") || a.equalsIgnoreCase("plaza")) full = true;
                else try { rad = Math.max(8, Math.min(128, Double.parseDouble(a))); } catch (NumberFormatException ignored) {}
            }
            if (full) pl.sendMessage(ChatColor.YELLOW + "Full plaza holoreset (radius " + (int) rad + ")...");
            else pl.sendMessage(ChatColor.GRAY + "Wild/Home portal holos only (radius " + (int) rad
                    + "). " + ChatColor.DARK_GRAY + "Add 'all' for full plaza.");
            int n = holoresetAll(pl, rad, full);
            pl.sendMessage(ChatColor.GREEN + "\u2714 Removed " + n + " sign(s), Wild/Home holos rewritten"
                    + (full ? ChatColor.GRAY + " + peer restores queued." : ChatColor.GRAY + "."));
            pl.playSound(pl.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.2f);
            return true;
        }

        if (args.length >= 1 && (args[0].equalsIgnoreCase("portal") || args[0].equalsIgnoreCase("homeportal"))) {
            if (!pl.hasPermission("mavowild.admin")) { pl.sendMessage(ChatColor.RED + "No permission."); return true; }
            boolean wild = args[0].equalsIgnoreCase("portal");
            String prefix = wild ? "portal" : "homeportal";
            String sub = args.length >= 2 ? args[1].toLowerCase(Locale.ROOT) : "";
            switch (sub) {
                case "pos1" -> {
                    pendingCorner.put(pl.getUniqueId(), new int[]{pl.getLocation().getBlockX(), pl.getLocation().getBlockY(), pl.getLocation().getBlockZ()});
                    pl.sendMessage((wild ? ChatColor.BLUE : ChatColor.GREEN) + "Corner 1 set (BOTTOM corner of the opening). "
                            + ChatColor.GRAY + "Now FLY to the opposite TOP corner and run " + ChatColor.YELLOW + "/wild " + prefix + " pos2");
                }
                case "pos2" -> {
                    int[] c1 = pendingCorner.remove(pl.getUniqueId());
                    if (c1 == null) { pl.sendMessage(ChatColor.RED + "Run /wild " + prefix + " pos1 first!"); return true; }
                    int y2 = pl.getLocation().getBlockY();
                    if (Math.abs(y2 - c1[1]) < 2) y2 = c1[1] + 2; // same height given? assume 3-tall opening
                    getConfig().set(prefix + ".enabled", true);
                    getConfig().set(prefix + ".world", pl.getWorld().getName());
                    getConfig().set(prefix + ".x1", c1[0]); getConfig().set(prefix + ".y1", c1[1]); getConfig().set(prefix + ".z1", c1[2]);
                    getConfig().set(prefix + ".x2", pl.getLocation().getBlockX());
                    getConfig().set(prefix + ".y2", y2);
                    getConfig().set(prefix + ".z2", pl.getLocation().getBlockZ());
                    saveConfig();
                    loadPortals();
                    spawnHolo(wild ? wildBox : homeBox, prefix, wild ? wildHoloText() : homeHoloText());
                    Box bb = wild ? wildBox : homeBox;
                    pl.sendMessage((wild ? ChatColor.BLUE : ChatColor.GREEN) + "\u27A4 " + (wild ? "Wild" : "Home") + " Portal ACTIVE! "
                            + ChatColor.GRAY + "Region " + (bb.x2 - bb.x1 + 1) + "\u00D7" + (bb.y2 - bb.y1 + 1) + "\u00D7" + (bb.z2 - bb.z1 + 1)
                            + " \u00B7 sign floats above \u00B7 step in to test.");
                }
                case "off" -> {
                    getConfig().set(prefix + ".enabled", false);
                    removeHolo(prefix);
                    saveConfig();
                    loadPortals();
                    pl.sendMessage(ChatColor.GRAY + (wild ? "Wild" : "Home") + " Portal disabled, sign removed.");
                }
                default -> pl.sendMessage(ChatColor.GRAY + "/wild " + prefix + " pos1 \u00B7 pos2 \u00B7 off");
            }
            return true;
        }

        // /wild as a chat command is retired - the Wild Portal at spawn is the only way out.
        // (admins keep it for testing; locked new players keep it as their escape hatch)
        boolean isLocked = locked.contains(pl.getUniqueId());
        if (!isLocked && !pl.hasPermission("mavowild.admin")) {
            pl.sendMessage(ChatColor.RED + "\u25C6 " + ChatColor.WHITE + "Wild teleports happen at the "
                    + ChatColor.RED + "" + ChatColor.BOLD + "WILD PORTAL" + ChatColor.WHITE + " at spawn now!");
            pl.sendMessage(ChatColor.GRAY + "Head back with " + ChatColor.YELLOW + "/spawn"
                    + ChatColor.GRAY + " - maybe visit the traders while you're there...");
            return true;
        }

        int cd = getConfig().getInt("cooldown-seconds", 300);
        long now = System.currentTimeMillis();
        Long last = cooldown.get(pl.getUniqueId());
        if (!isLocked && last != null && !pl.hasPermission("mavowild.nocooldown") && now - last < cd * 1000L) {
            long left = (cd * 1000L - (now - last)) / 1000L;
            pl.sendMessage(ChatColor.RED + "Wild teleport on cooldown - " + left + "s left.");
            return true;
        }
        // locked new players + creative go instantly; survival players get the warmup
        if (isLocked || pl.getGameMode() != GameMode.SURVIVAL) {
            doWildTeleport(pl);
        } else {
            startWarmup(pl, ChatColor.BLUE, "the wild", () -> doWildTeleport(pl));
        }
        return true;
    }

    private void doWildTeleport(Player pl) {
        long now = System.currentTimeMillis();
        World w = pl.getWorld();
        if (w.getEnvironment() != World.Environment.NORMAL) {
            pl.sendMessage(ChatColor.RED + "Only works in the overworld!");
            return;
        }
        pl.sendMessage(ChatColor.GRAY + "Searching for a safe wild spot...");
        int minR = getConfig().getInt("min-radius", 5000);
        int maxR = getConfig().getInt("max-radius", 400000);
        if (maxR < minR) maxR = minR;
        int blRadius = getConfig().getInt("blacklist-radius", 200);
        int blSize = getConfig().getInt("blacklist-size", 50);
        java.util.List<String> zones = getConfig().getStringList("wild-zones");
        if (zones == null) zones = new java.util.ArrayList<>();
        final int blR2 = blRadius * blRadius;

        Location target = null;
        for (int attempt = 0; attempt < 48; attempt++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            double dist = minR + rng.nextDouble() * (maxR - minR);
            int x = (int) Math.round(w.getSpawnLocation().getX() + Math.cos(ang) * dist);
            int z = (int) Math.round(w.getSpawnLocation().getZ() + Math.sin(ang) * dist);
            if (isBlacklisted(x, z, zones, blR2)) continue;
            Block top = w.getHighestBlockAt(x, z);
            Material m = top.getType();
            if (m == Material.WATER || m == Material.LAVA || m == Material.CACTUS
                    || m == Material.MAGMA_BLOCK || m == Material.POWDER_SNOW || !m.isSolid()) continue;
            target = top.getLocation().add(0.5, 1.0, 0.5);
            break;
        }
        if (target == null) {
            for (int attempt = 0; attempt < 64 && target == null; attempt++) {
                double ang = rng.nextDouble() * Math.PI * 2;
                double dist = minR + rng.nextDouble() * (maxR - minR);
                int x = (int) Math.round(w.getSpawnLocation().getX() + Math.cos(ang) * dist);
                int z = (int) Math.round(w.getSpawnLocation().getZ() + Math.sin(ang) * dist);
                if (isBlacklisted(x, z, zones, blR2)) continue;
                Block top = w.getHighestBlockAt(x, z);
                if (top.getType() == Material.LAVA || top.getType() == Material.MAGMA_BLOCK) continue;
                target = top.getLocation().add(0.5, 1.0, 0.5);
            }
        }
        if (target == null) {
            pl.sendMessage(ChatColor.RED + "Couldn't find a safe spot - try again!");
            return;
        }
        // record zone (rolling last N)
        String tag = target.getBlockX() + "," + target.getBlockZ();
        zones = new java.util.ArrayList<>(zones);
        zones.add(tag);
        while (zones.size() > blSize) zones.remove(0);
        getConfig().set("wild-zones", zones);
        saveConfig();

        cooldown.put(pl.getUniqueId(), now);
        locked.remove(pl.getUniqueId());
        final Location tloc = target;
        pl.teleportAsync(tloc).thenRun(() -> {
            pl.playSound(tloc, Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            pl.sendMessage(ChatColor.GREEN + "\u27A4 Teleported to the wild! " + ChatColor.GRAY
                    + "(" + tloc.getBlockX() + ", " + tloc.getBlockZ() + ") - good luck out there.");
        });
    }

    /** true if (x,z) is within blacklist-radius of any recent wild zone */
    private boolean isBlacklisted(int x, int z, java.util.List<String> zones, int r2) {
        if (zones == null || zones.isEmpty()) return false;
        for (String s : zones) {
            int c = s.indexOf(',');
            if (c < 0) continue;
            try {
                int zx = Integer.parseInt(s.substring(0, c).trim());
                int zz = Integer.parseInt(s.substring(c + 1).trim());
                long dx = (long) x - zx, dz = (long) z - zz;
                if (dx * dx + dz * dz <= (long) r2) return true;
            } catch (NumberFormatException ignored) {}
        }
        return false;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        if (!s.hasPermission("mavowild.admin")) return java.util.List.of();
        if (a.length == 1) {
            String pfx = a[0].toLowerCase(Locale.ROOT);
            return java.util.List.of("portal", "homeportal", "holoreset", "resetholo", "holo").stream()
                    .filter(x -> x.startsWith(pfx)).toList();
        }
        if (a.length == 2 && (a[0].equalsIgnoreCase("portal") || a[0].equalsIgnoreCase("homeportal")))
            return java.util.List.of("pos1", "pos2", "off");
        if (a.length == 2 && (a[0].equalsIgnoreCase("holoreset") || a[0].equalsIgnoreCase("resetholo")))
            return java.util.List.of("60", "32", "80", "100");
        if (a.length == 2 && a[0].equalsIgnoreCase("holo"))
            return java.util.List.of("reset");
        if (a.length == 3 && a[0].equalsIgnoreCase("holo") && a[1].equalsIgnoreCase("reset"))
            return java.util.List.of("60", "32", "80", "100");
        return java.util.List.of();
    }
}
