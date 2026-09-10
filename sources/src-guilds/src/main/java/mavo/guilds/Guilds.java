package mavo.guilds;

import java.io.File;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/** MAVOGuilds 1.0.0 - guilds + claimed territory (Discord CW#4 idea 20, inspired by
 *  Factions/Kingdoms). /guild create|invite|accept|claim|map|home|sethome|promote|...
 *  Claims are chunk-based, must touch an existing claim (require-adjacent), are protected
 *  from non-members (build/interact/pvp/animals/explosions per config) and persist in
 *  data.yml. Optional Vault costs for create/claim. */
public final class Guilds extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;

    private long createCost = 500, claimCost = 100;
    private int maxGuilds = 50, maxMembers = 12, maxClaims = 8, maxName = 16;
    private boolean pvpInClaims, protectBlocks, protectInteract, protectExplosions, protectAnimals, requireAdjacent = true;
    // 3.0.5: /guild home is no longer instant (combat-escape hole): 5s stand-still + 12-block monsters.
    private int warmupSec = 5, monsterRadius = 12;
    private final Map<UUID, BukkitTask> warmups = new HashMap<>();
    private final Map<UUID, Location> warmLoc = new HashMap<>();
    private Set<String> worlds = new HashSet<>();

    private static final class Guild {
        String id, name;
        UUID leader;
        final Set<UUID> officers = new HashSet<>();
        final Set<UUID> members = new HashSet<>();
        final Set<String> claims = new HashSet<>();
        Location home;
        String desc = "";
        Guild(String id, String name, UUID leader) {
            this.id = id; this.name = name; this.leader = leader;
            members.add(leader);
        }
        boolean isMember(UUID u) { return members.contains(u); }
        boolean isOfficer(UUID u) { return officers.contains(u) || leader.equals(u); }
    }

    private final Map<String, Guild> guilds = new LinkedHashMap<>();          // id -> guild
    private final Map<UUID, String> playerGuild = new HashMap<>();            // uuid -> id
    private final Map<UUID, Set<String>> invites = new HashMap<>();           // target -> guild ids
    private final Map<UUID, Long> inviteTime = new HashMap<>();               // target -> ms
    private static final long INVITE_TTL = 120_000;

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        loadCfg();
        loadAll();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOGuilds v" + getDescription().getVersion() + " enabled - "
                + guilds.size() + " guild(s), " + countClaims() + " claim(s)"
                + (econ == null ? " - Vault NOT found (costs disabled)" : ""));
    }

    @Override public void onDisable() {
        for (BukkitTask task : warmups.values()) task.cancel();
        warmups.clear();
        saveAll();
    }

    /* ---------------- config / storage ---------------- */

    private void loadCfg() {
        createCost = Math.max(0, getConfig().getLong("create-cost", 500));
        claimCost = Math.max(0, getConfig().getLong("claim-cost", 100));
        maxGuilds = Math.max(1, getConfig().getInt("max-guilds", 50));
        maxMembers = Math.max(2, getConfig().getInt("max-members", 12));
        maxClaims = Math.max(1, getConfig().getInt("max-claims", 8));
        maxName = Math.max(3, getConfig().getInt("max-name-length", 16));
        pvpInClaims = getConfig().getBoolean("pvp-in-claims", false);
        protectBlocks = getConfig().getBoolean("protect-blocks", true);
        protectInteract = getConfig().getBoolean("protect-interact", true);
        protectExplosions = getConfig().getBoolean("protect-explosions", true);
        protectAnimals = getConfig().getBoolean("protect-animals", true);
        requireAdjacent = getConfig().getBoolean("require-adjacent", true);
        warmupSec = Math.max(0, getConfig().getInt("warmup-seconds", 5));
        monsterRadius = Math.max(0, getConfig().getInt("monster-radius", 12));
        worlds = new HashSet<>(getConfig().getStringList("worlds"));
    }

    private void loadAll() {
        guilds.clear(); playerGuild.clear();
        ConfigurationSection gs = data.getConfigurationSection("guilds");
        if (gs == null) return;
        for (String id : gs.getKeys(false)) {
            ConfigurationSection s = gs.getConfigurationSection(id);
            if (s == null) continue;
            String name = s.getString("name", id);
            UUID leader;
            try { leader = UUID.fromString(s.getString("leader", "")); } catch (Throwable t) { continue; }
            Guild g = new Guild(id, name, leader);
            for (String u : s.getStringList("officers")) add(g.officers, u);
            for (String u : s.getStringList("members")) add(g.members, u);
            g.members.add(leader);
            ConfigurationSection h = s.getConfigurationSection("home");
            if (h != null) {
                World w = Bukkit.getWorld(h.getString("world", ""));
                if (w != null) g.home = new Location(w, h.getDouble("x"), h.getDouble("y"), h.getDouble("z"),
                        (float) h.getDouble("yaw"), (float) h.getDouble("pitch"));
            }
            g.claims.addAll(s.getStringList("claims"));
            g.desc = s.getString("desc", "");
            guilds.put(id, g);
            for (UUID u : g.members) playerGuild.put(u, id);
        }
    }

    private static void add(Set<UUID> set, String raw) {
        try { set.add(UUID.fromString(raw)); } catch (Throwable ignored) { }
    }

    private void saveAll() {
        data.set("guilds", null);
        for (Guild g : guilds.values()) {
            String p = "guilds." + g.id + ".";
            data.set(p + "name", g.name);
            data.set(p + "leader", g.leader.toString());
            data.set(p + "officers", g.officers.stream().map(UUID::toString).toList());
            data.set(p + "members", g.members.stream().map(UUID::toString).toList());
            if (g.home != null) {
                data.set(p + "home.world", g.home.getWorld().getName());
                data.set(p + "home.x", g.home.getX());
                data.set(p + "home.y", g.home.getY());
                data.set(p + "home.z", g.home.getZ());
                data.set(p + "home.yaw", g.home.getYaw());
                data.set(p + "home.pitch", g.home.getPitch());
            } else data.set(p + "home", null);
            data.set(p + "claims", new ArrayList<>(g.claims));
            data.set(p + "desc", g.desc);
        }
        try { data.save(dataFile); } catch (Throwable ignored) { }
    }

    private int countClaims() { return guilds.values().stream().mapToInt(g -> g.claims.size()).sum(); }

    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private static String nm(UUID u) {
        var p = Bukkit.getOfflinePlayer(u);
        return p.getName() == null ? u.toString().substring(0, 8) : p.getName();
    }

    /* ---------------- helpers ---------------- */

    private Guild guildOf(Player p) { return guilds.get(playerGuild.get(p.getUniqueId())); }
    private static String key(Chunk c) { return c.getWorld().getName() + "," + c.getX() + "," + c.getZ(); }
    private static String key(Block b) { return key(b.getChunk()); }
    private static String key(Location l) { return key(l.getChunk()); }

    private Guild claimingGuild(Location l) {
        String k = key(l);
        for (Guild g : guilds.values()) if (g.claims.contains(k)) return g;
        return null;
    }

    private boolean bypass(Player p) { return p.hasPermission("mavoguilds.admin"); }

    private boolean canBuild(Player p, Block b) {
        if (bypass(p)) return true;
        if (!worlds.contains(b.getWorld().getName())) return true;
        Guild g = claimingGuild(b.getLocation());
        return g == null || g.isMember(p.getUniqueId());
    }

    private void payOrKill(Player p, long cost, Runnable onDone) {
        if (cost <= 0) { onDone.run(); return; }
        if (econ == null) { p.sendMessage(C + "cVault not found - cost cannot be charged."); return; }
        if (!econ.has(p, cost)) { p.sendMessage(C + "cYou need " + cost + " coins."); return; }
        if (!econ.withdrawPlayer(p, cost).transactionSuccess()) { p.sendMessage(C + "cPayment failed."); return; }
        onDone.run();
    }

    private boolean adjacentToClaim(Guild g, String k) {
        String[] parts = k.split(",");
        World w = Bukkit.getWorld(parts[0]);
        int x = Integer.parseInt(parts[1]), z = Integer.parseInt(parts[2]);
        for (int dx = -1; dx <= 1; dx++) for (int dz = -1; dz <= 1; dz++) {
            if (dx == 0 && dz == 0) continue;
            if (g.claims.contains(w.getName() + "," + (x + dx) + "," + (z + dz))) return true;
        }
        return false;
    }

    /* ---------------- territory protection ---------------- */

    private boolean protectedWorld(World w) { return worlds.contains(w.getName()); }

    @EventHandler
    public void onBreak(BlockBreakEvent e) {
        if (protectedWorld(e.getBlock().getWorld()) && !canBuild(e.getPlayer(), e.getBlock())
                && protectBlocks) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C + "cThat land is claimed by an enemy guild. /guild map to see.");
        }
    }

    @EventHandler
    public void onPlace(BlockPlaceEvent e) {
        if (protectedWorld(e.getBlock().getWorld()) && !canBuild(e.getPlayer(), e.getBlock())
                && protectBlocks) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C + "cYou cannot build on claimed land.");
        }
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {
        if (!protectInteract || e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (!protectedWorld(b.getWorld())) return;
        if (bypass(e.getPlayer())) return;
        Guild g = claimingGuild(b.getLocation());
        if (g == null || g.isMember(e.getPlayer().getUniqueId())) return;
        Material type = b.getType();
        boolean locked = switch (type) {
            case CHEST, TRAPPED_CHEST, BARREL, FURNACE, BLAST_FURNACE, SMOKER, SHULKER_BOX,
                 WHITE_SHULKER_BOX, ORANGE_SHULKER_BOX, MAGENTA_SHULKER_BOX, LIGHT_BLUE_SHULKER_BOX,
                 LIME_SHULKER_BOX, PINK_SHULKER_BOX, GRAY_SHULKER_BOX, LIGHT_GRAY_SHULKER_BOX,
                 CYAN_SHULKER_BOX, PURPLE_SHULKER_BOX, BLUE_SHULKER_BOX, BROWN_SHULKER_BOX,
                 GREEN_SHULKER_BOX, RED_SHULKER_BOX, BLACK_SHULKER_BOX,
                 OAK_DOOR, SPRUCE_DOOR, BIRCH_DOOR, JUNGLE_DOOR, ACACIA_DOOR, DARK_OAK_DOOR,
                 MANGROVE_DOOR, CHERRY_DOOR, CRIMSON_DOOR, WARPED_DOOR,
                 OAK_FENCE_GATE, SPRUCE_FENCE_GATE, BIRCH_FENCE_GATE, JUNGLE_FENCE_GATE,
                 ACACIA_FENCE_GATE, DARK_OAK_FENCE_GATE, MANGROVE_FENCE_GATE,
                 CHERRY_FENCE_GATE, CRIMSON_FENCE_GATE, WARPED_FENCE_GATE,
                 LEVER, REPEATER, COMPARATOR, STONE_BUTTON, OAK_BUTTON, SPRUCE_BUTTON,
                 BIRCH_BUTTON, JUNGLE_BUTTON, ACACIA_BUTTON, DARK_OAK_BUTTON, MANGROVE_BUTTON,
                 CHERRY_BUTTON, CRIMSON_BUTTON, WARPED_BUTTON, BELL, CAKE, ANVIL,
                 CHIPPED_ANVIL, DAMAGED_ANVIL, CANDLE_CAKE, BREWING_STAND, ENCHANTING_TABLE,
                 LOOM, STONECUTTER, GRINDSTONE, CARTOGRAPHY_TABLE, COMPOSTER, NOTE_BLOCK -> true;
            default -> false;
        };
        if (locked) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(C + "cThis belongs to guild " + C + "e" + g.name + C + "c.");
        }
    }

    @EventHandler
    public void onDamage(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player d)) return;
        if (bypass(d)) return;
        if (e.getEntity() instanceof Animals && protectAnimals) {
            Guild g = claimingGuild(e.getEntity().getLocation());
            if (g != null && !g.isMember(d.getUniqueId())) {
                e.setCancelled(true);
                d.sendMessage(C + "cThese animals belong to guild " + C + "e" + g.name + C + "c.");
            }
        }
        if (e.getEntity() instanceof Player v && !pvpInClaims) {
            Guild g = claimingGuild(v.getLocation());
            if (g != null && !g.isMember(d.getUniqueId())) {
                e.setCancelled(true);
                d.sendMessage(C + "cPvP is disabled in " + g.name + " territory.");
            }
        }
    }

    private void handleExplosion(List<Block> blocks) {
        if (!protectExplosions) return;
        blocks.removeIf(b -> {
            Guild g = claimingGuild(b.getLocation());
            return g != null;
        });
    }

    @EventHandler
    public void onBlockExplode(BlockExplodeEvent e) {
        if (!protectedWorld(e.getBlock().getWorld())) return;
        handleExplosion(e.blockList());
    }

    @EventHandler
    public void onEntityExplode(EntityExplodeEvent e) {
        if (e.getLocation().getWorld() == null || !protectedWorld(e.getLocation().getWorld())) return;
        handleExplosion(e.blockList());
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        invites.remove(e.getPlayer().getUniqueId());
        cancelWarmup(e.getPlayer().getUniqueId());
    }

    private boolean monstersNear(Player pl) {
        if (monsterRadius <= 0) return false;
        for (org.bukkit.entity.Entity en : pl.getNearbyEntities(monsterRadius, monsterRadius, monsterRadius))
            if (en instanceof org.bukkit.entity.Enemy && !en.isDead()) return true;
        return false;
    }

    /** 3.0.5: warmup teleport for /guild home. */
    private void teleportHomeWithWarmup(Player p, Location dest, String guildName) {
        if (monstersNear(p)) { p.sendMessage(C + "cMonsters nearby - you can't teleport right now!"); return; }
        cancelWarmup(p.getUniqueId());
        warmLoc.put(p.getUniqueId(), p.getLocation());
        p.sendMessage(C + "eTeleporting in " + C + "a" + warmupSec + "s" + C + "e - stand still.");
        if (warmupSec <= 0) {
            p.teleport(dest);
            p.sendMessage(C + "aTeleported to " + guildName + "'s home.");
            return;
        }
        warmups.put(p.getUniqueId(), Bukkit.getScheduler().runTaskLater(this, () -> {
            warmups.remove(p.getUniqueId());
            warmLoc.remove(p.getUniqueId());
            if (!p.isOnline()) return;
            if (monstersNear(p)) { p.sendMessage(C + "cTeleport cancelled - monsters nearby!"); return; }
            p.teleport(dest);
            p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1f, 1f);
            p.sendMessage(C + "aTeleported to " + guildName + "'s home.");
        }, warmupSec * 20L));
    }

    private void cancelWarmup(UUID u) {
        BukkitTask old = warmups.remove(u);
        if (old != null) old.cancel();
        warmLoc.remove(u);
    }

    @EventHandler public void onMove(PlayerMoveEvent e) {
        BukkitTask task = warmups.get(e.getPlayer().getUniqueId());
        if (task == null || e.getTo() == null) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()) {
            cancelWarmup(e.getPlayer().getUniqueId());
            e.getPlayer().sendMessage(C + "cTeleport cancelled - you moved!");
        }
    }

    /* ---------------- commands ---------------- */

    private void help(Player p) {
        p.sendMessage(C + "6\u2694 MAVOGuilds:");
        p.sendMessage(C + "7/guild create <name> | invite <p> | accept | decline");
        p.sendMessage(C + "7/guild leave | disband | list | info [guild] | desc <text>");
        p.sendMessage(C + "7/guild promote <p> | demote <p> | kick <p> | chat <msg>");
        p.sendMessage(C + "7/guild claim | unclaim | unclaimall | map [r] | sethome | home");
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length != 1) return List.of();
        if (c.getName().equalsIgnoreCase("guild"))
            return List.of("create", "invite", "accept", "decline", "leave", "disband", "list", "info",
                    "promote", "demote", "kick", "chat", "claim", "unclaim", "unclaimall", "map",
                    "sethome", "home", "desc", "reload");
        if (c.getName().equalsIgnoreCase("gchat")) return List.of();
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Player command only."); return true; }
        UUID u = p.getUniqueId();

        // /g <message> = guild chat shorthand
        if (label.equalsIgnoreCase("g") && args.length > 0 && isChat(args[0])) {
            guildChat(p, String.join(" ", args));
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("gchat")) {
            if (args.length == 0) { p.sendMessage(C + "cUsage: /gchat <message>"); return true; }
            guildChat(p, String.join(" ", args));
            return true;
        }

        if (label.equalsIgnoreCase("g") && args.length == 0) { help(p); return true; }
        if (args.length == 0) { help(p); return true; }
        String sub = args[0].toLowerCase(Locale.ROOT);
        String[] rest = new String[args.length - 1];
        System.arraycopy(args, 1, rest, 0, rest.length);

        switch (sub) {
            case "create" -> {
                if (rest.length == 0) { p.sendMessage(C + "cUsage: /guild create <name>"); return true; }
                String want = rest[0].replace(" ", "");
                if (want.length() < 3 || want.length() > maxName) { p.sendMessage(C + "cName must be 3-" + maxName + " chars."); return true; }
                String id = want.toLowerCase(Locale.ROOT);
                if (guilds.size() >= maxGuilds) { p.sendMessage(C + "cToo many guilds on the server (" + maxGuilds + ")."); return true; }
                if (guilds.containsKey(id)) { p.sendMessage(C + "cA guild named " + want + " already exists."); return true; }
                if (playerGuild.containsKey(u)) { p.sendMessage(C + "cLeave your current guild first."); return true; }
                payOrKill(p, createCost, () -> {
                    Guild g = new Guild(id, want, u);
                    guilds.put(id, g);
                    playerGuild.put(u, id);
                    saveAll();
                    p.sendMessage(C + "a\u2694 Guild " + C + "e" + want + C + "a created! Claim land with /guild claim.");
                    p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.4f);
                });
            }
            case "invite" -> {
                if (rest.length == 0) { p.sendMessage(C + "cUsage: /guild invite <player>"); return true; }
                Guild g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return true; }
                if (!g.isOfficer(u)) { p.sendMessage(C + "cOnly leaders/officers can invite."); return true; }
                if (g.members.size() >= maxMembers) { p.sendMessage(C + "cGuild is full (" + maxMembers + ")."); return true; }
                Player t = Bukkit.getPlayerExact(rest[0]);
                if (t == null) { p.sendMessage(C + "cPlayer offline."); return true; }
                if (g.isMember(t.getUniqueId())) { p.sendMessage(C + "cAlready a member."); return true; }
                if (playerGuild.containsKey(t.getUniqueId())) { p.sendMessage(C + "cThey are already in a guild."); return true; }
                invites.computeIfAbsent(t.getUniqueId(), k -> new HashSet<>()).add(g.id);
                inviteTime.put(t.getUniqueId(), System.currentTimeMillis());
                t.sendMessage(C + "6\u2694 " + p.getName() + C + "6 invited you to " + C + "e" + g.name
                        + C + "6! /guild accept or /guild decline");
                p.sendMessage(C + "aInvite sent to " + t.getName() + ".");
            }
            case "accept" -> {
                Set<String> ids = invites.get(u);
                if (ids == null || ids.isEmpty() || System.currentTimeMillis() - inviteTime.getOrDefault(u, 0L) > INVITE_TTL)
                    { p.sendMessage(C + "cNo pending invites (invites expire after 2 min)."); return true; }
                Guild g = guilds.get(ids.iterator().next());
                if (g == null) { p.sendMessage(C + "cThat guild no longer exists."); return true; }
                if (g.members.size() >= maxMembers) { p.sendMessage(C + "cGuild is full."); return true; }
                g.members.add(u);
                playerGuild.put(u, g.id);
                invites.remove(u); inviteTime.remove(u);
                saveAll();
                gcast(g, C + "a" + p.getName() + " joined " + g.name + "!");
                p.sendMessage(C + "aWelcome to " + C + "e" + g.name + C + "a! Use /guild map to see land, /guild home to teleport.");
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
            }
            case "decline" -> {
                if (invites.remove(u) == null) { p.sendMessage(C + "cNo pending invites."); return true; }
                inviteTime.remove(u);
                p.sendMessage(C + "cInvite declined.");
            }
            case "leave" -> {
                Guild g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return true; }
                if (g.leader.equals(u)) { p.sendMessage(C + "cLeaders must use /guild disband (or promote someone first)."); return true; }
                g.members.remove(u); g.officers.remove(u);
                playerGuild.remove(u);
                saveAll();
                gcast(g, C + "7" + p.getName() + " left " + g.name + ".");
            }
            case "disband" -> {
                Guild g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return true; }
                if (!g.leader.equals(u)) { p.sendMessage(C + "cOnly the leader can disband."); return true; }
                gcast(g, C + "c\u2694 " + g.name + " has been disbanded by " + p.getName() + ".");
                for (UUID m : new HashSet<>(g.members)) playerGuild.remove(m);
                guilds.remove(g.id);
                saveAll();
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1f, 1f);
            }
            case "list" -> {
                if (guilds.isEmpty()) { p.sendMessage(C + "7No guilds yet. Create one with /guild create <name>."); return true; }
                p.sendMessage(C + "6\u2694 Guilds (" + guilds.size() + "/" + maxGuilds + "):");
                guilds.values().stream().sorted(Comparator.comparing(g -> g.name)).forEach(g ->
                        p.sendMessage(C + "7- " + C + "e" + g.name + C + "7 [" + g.members.size() + "/" + maxMembers
                                + " members, " + g.claims.size() + "/" + maxClaims + " claims]"));
            }
            case "info" -> {
                Guild g;
                if (rest.length > 0) g = guilds.get(rest[0].toLowerCase(Locale.ROOT));
                else g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cGuild not found."); return true; }
                p.sendMessage(C + "6\u2694 " + C + "e" + g.name);
                if (!g.desc.isEmpty()) p.sendMessage(C + "7" + g.desc);
                p.sendMessage(C + "7Leader: " + C + "f" + nm(g.leader));
                if (!g.officers.isEmpty())
                    p.sendMessage(C + "7Officers: " + C + "f" + g.officers.stream().map(Guilds::nm).reduce((a, b) -> a + ", " + b).orElse(""));
                p.sendMessage(C + "7Members (" + g.members.size() + "/" + maxMembers + "): " + C + "f"
                        + g.members.stream().map(Guilds::nm).reduce((a, b) -> a + ", " + b).orElse(""));
                p.sendMessage(C + "7Claims: " + C + "f" + g.claims.size() + "/" + maxClaims + " chunks");
                if (bypass(p)) p.sendMessage(C + "7ID: " + g.id);
            }
            case "desc" -> {
                Guild g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return true; }
                if (!g.isOfficer(u)) { p.sendMessage(C + "cOnly leaders/officers can set the description."); return true; }
                g.desc = rest.length > 0 ? String.join(" ", rest) : "";
                saveAll();
                p.sendMessage(C + "aDescription updated.");
            }
            case "promote" -> {
                Guild g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return true; }
                if (!g.leader.equals(u)) { p.sendMessage(C + "cOnly the leader can promote."); return true; }
                UUID t = findMember(g, rest.length > 0 ? rest[0] : "");
                if (t == null) { p.sendMessage(C + "cMember not found."); return true; }
                if (g.officers.add(t)) { saveAll(); gcast(g, C + "a" + nm(t) + " is now an officer!"); }
                else p.sendMessage(C + "cAlready an officer.");
            }
            case "demote" -> {
                Guild g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return true; }
                if (!g.leader.equals(u)) { p.sendMessage(C + "cOnly the leader can demote."); return true; }
                UUID t = findMember(g, rest.length > 0 ? rest[0] : "");
                if (t == null) { p.sendMessage(C + "cMember not found."); return true; }
                if (g.officers.remove(t)) { saveAll(); gcast(g, C + "7" + nm(t) + " was demoted to member."); }
                else p.sendMessage(C + "cThey are not an officer.");
            }
            case "kick" -> {
                Guild g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return true; }
                if (!g.isOfficer(u)) { p.sendMessage(C + "cOnly leaders/officers can kick."); return true; }
                UUID t = findMember(g, rest.length > 0 ? rest[0] : "");
                if (t == null) { p.sendMessage(C + "cMember not found."); return true; }
                if (g.leader.equals(t)) { p.sendMessage(C + "cYou cannot kick the leader."); return true; }
                g.members.remove(t); g.officers.remove(t); playerGuild.remove(t);
                saveAll();
                gcast(g, C + "c" + nm(t) + " was kicked from " + g.name + ".");
                msg(t, C + "cYou were kicked from " + g.name + ".");
            }
            case "chat" -> {
                if (rest.length == 0) { p.sendMessage(C + "cUsage: /guild chat <message>"); return true; }
                guildChat(p, String.join(" ", rest));
            }
            case "sethome" -> {
                Guild g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return true; }
                if (!g.isOfficer(u)) { p.sendMessage(C + "cOnly leaders/officers can set the guild home."); return true; }
                g.home = p.getLocation().clone();
                saveAll();
                p.sendMessage(C + "aGuild home set!");
            }
            case "home" -> {
                Guild g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return true; }
                if (g.home == null) { p.sendMessage(C + "cNo guild home set yet (/guild sethome)."); return true; }
                teleportHomeWithWarmup(p, g.home.clone(), g.name);
            }
            case "claim" -> {
                Guild g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return true; }
                if (!g.isOfficer(u)) { p.sendMessage(C + "cOnly leaders/officers can claim."); return true; }
                if (g.claims.size() >= maxClaims) { p.sendMessage(C + "cGuild has the max of " + maxClaims + " claims."); return true; }
                Chunk c = p.getLocation().getChunk();
                String k = key(c);
                if (claimingGuild(p.getLocation()) != null) { p.sendMessage(C + "cThat chunk is already claimed."); return true; }
                if (requireAdjacent && !g.claims.isEmpty() && !adjacentToClaim(g, k)) {
                    p.sendMessage(C + "cNew claims must touch your existing territory.");
                    return true;
                }
                payOrKill(p, claimCost, () -> {
                    g.claims.add(k);
                    saveAll();
                    p.sendMessage(C + "a\u2694 Chunk claimed by " + C + "e" + g.name
                            + C + "a (" + g.claims.size() + "/" + maxClaims + ").");
                    p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1f, 1.5f);
                });
            }
            case "unclaim" -> {
                Guild g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return true; }
                if (!g.isOfficer(u)) { p.sendMessage(C + "cOnly leaders/officers can unclaim."); return true; }
                String k = key(p.getLocation());
                if (!g.claims.remove(k)) { p.sendMessage(C + "cThis chunk is not your claim."); return true; }
                saveAll();
                p.sendMessage(C + "aClaim released. (" + g.claims.size() + " left)");
            }
            case "unclaimall" -> {
                Guild g = guildOf(p);
                if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return true; }
                if (!g.leader.equals(u)) { p.sendMessage(C + "cOnly the leader can unclaim all."); return true; }
                int n = g.claims.size();
                g.claims.clear();
                saveAll();
                p.sendMessage(C + "a" + n + " claims released.");
            }
            case "map" -> {
                int r = 6;
                if (rest.length > 0) { try { r = Integer.parseInt(rest[0]); } catch (Throwable ignored) { }
                    r = Math.max(2, Math.min(10, r)); }
                map(p, r);
            }
            case "reload" -> {
                if (!bypass(p)) { p.sendMessage("OP only."); return true; }
                reloadConfig();
                loadCfg();
                p.sendMessage(C + "aConfig reloaded.");
            }
            default -> help(p);
        }
        return true;
    }

    private static boolean isChat(String s) {
        return !List.of("create", "invite", "accept", "decline", "leave", "disband", "list", "info",
                "promote", "demote", "kick", "chat", "claim", "unclaim", "unclaimall", "map",
                "sethome", "home", "desc", "reload").contains(s.toLowerCase(Locale.ROOT));
    }

    private static UUID findMember(Guild g, String name) {
        if (name.isEmpty()) return null;
        var it = g.members.stream().filter(x -> nm(x).equalsIgnoreCase(name)).iterator();
        return it.hasNext() ? it.next() : null;
    }

    private void guildChat(Player p, String msg) {
        Guild g = guildOf(p);
        if (g == null) { p.sendMessage(C + "cYou are not in a guild."); return; }
        for (UUID m : g.members)
            msg(m, C + "e[G] " + C + "7" + p.getName() + C + "f: " + msg);
    }

    private static void msg(UUID u, String s) {
        Player p = Bukkit.getPlayer(u);
        if (p != null && p.isOnline()) p.sendMessage(s);
    }

    private void gcast(Guild g, String s) { for (UUID m : g.members) msg(m, s); }

    private void map(Player p, int r) {
        Chunk center = p.getLocation().getChunk();
        World w = p.getWorld();
        p.sendMessage(C + "6\u2694 Territory map (radius " + r + "):");
        Guild own = guildOf(p);
        for (int dz = -r; dz <= r; dz++) {
            StringBuilder sb = new StringBuilder();
            for (int dx = -r; dx <= r; dx++) {
                if (dx == 0 && dz == 0) { sb.append(C).append("a+ "); continue; }
                Chunk c = w.getChunkAt(center.getX() + dx, center.getZ() + dz);
                String k = key(c);
                if (own != null && own.claims.contains(k)) sb.append(C).append("a").append(shortId(own)).append(" ");
                else {
                    Guild other = null;
                    for (Guild g : guilds.values()) if (g.claims.contains(k)) { other = g; break; }
                    if (other != null) sb.append(C).append("c").append(shortId(other)).append(" ");
                    else sb.append(C).append("7+ ");
                }
            }
            p.sendMessage(sb.toString());
        }
        p.sendMessage(C + "7" + C + "a[A]" + C + "7 yours  " + C + "c[X]" + C + "7 other guild  " + C + "7[+]" + C + "7 free");
    }

    private static String shortId(Guild g) {
        String s = g.name.substring(0, 1).toUpperCase(Locale.ROOT);
        return s;
    }
}
