package mavo.mobfarm;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.block.CreatureSpawner;
import org.bukkit.block.Sign;
import org.bukkit.block.data.Bisected;
import org.bukkit.block.data.type.Stairs;
import org.bukkit.block.sign.Side;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.SpawnerSpawnEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.util.Vector;

/** MAVOMobFarm 2.3.0 — AI toggle, session HUD, sun-safe pad, base stack 2, bay community. */
public final class MobFarm extends JavaPlugin implements Listener, TabCompleter {

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;
    private Location center;
    private final Map<String, RoomDef> rooms = new LinkedHashMap<>();
    private final Map<String, MobDef> mobs = new LinkedHashMap<>();
    private final Map<UUID, Session> sessions = new HashMap<>();
    private final Map<UUID, PendingEnter> pending = new HashMap<>();
    private NamespacedKey holoKey, farmMobKey, stackHoloKey;
    private long communityCoins, communityTarget;
    private int communityStack;

    static final class RoomDef {
        String id, display; int ox, oy, oz;
        Location balcony, killPad, lootChest, door, stackBlock, communityChest;
    }
    static final class MobDef {
        String id, display, theme; EntityType entity; Material icon;
    }
    static final class Session {
        UUID owner; String roomId; String mobId = "zombie"; long endsAtMs;
        int extraSpawners; Location returnLoc, stackLoc; boolean active = true;
        BukkitTask spawnTask; BukkitTask hudTask; BossBar hud; TextDisplay stackHolo;
    }
    static final class PendingEnter { int secondsLeft; BukkitTask task; long cost; }

    @Override public void onEnable() {
        holoKey = new NamespacedKey(this, "mfholo");
        farmMobKey = new NamespacedKey(this, "farmmob");
        stackHoloKey = new NamespacedKey(this, "stackholo");
        saveDefaultConfig();
        ensureDefaults();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        loadAll();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("mobfarm") != null) getCommand("mobfarm").setTabCompleter(this);
        new BukkitRunnable() {
            @Override public void run() {
                long now = System.currentTimeMillis();
                List<UUID> end = new ArrayList<>();
                for (var e : sessions.entrySet()) if (now >= e.getValue().endsAtMs) end.add(e.getKey());
                for (UUID u : end) endSession(u, true);
            }
        }.runTaskTimer(this, 40L, 40L);
        getLogger().info("MAVOMobFarm 2.3.0 enabled. Rooms=" + rooms.size()
                + " mobs=" + mobs.size() + " stackBase=" + communityStack
                + " ai=" + getConfig().getBoolean("mob-ai", true));
    }

    @Override public void onDisable() {
        for (UUID u : new ArrayList<>(sessions.keySet())) endSession(u, false);
        saveData();
    }

    private void ensureDefaults() {
        if (getConfig().getConfigurationSection("mobs") == null
                || getConfig().getConfigurationSection("mobs").getKeys(false).isEmpty()) {
            getLogger().warning("Empty mobs — writing jar defaults.");
            try { saveResource("config.yml", true); reloadConfig(); }
            catch (Throwable t) { writeBuiltinMobs(); saveConfig(); reloadConfig(); }
        } else {
            // merge any missing builtin mobs without wiping custom entries
            boolean added = false;
            for (String[] d : builtinMobTable()) {
                if (!getConfig().isConfigurationSection("mobs." + d[0])) {
                    getConfig().set("mobs." + d[0] + ".display", d[1]);
                    getConfig().set("mobs." + d[0] + ".entity", d[2]);
                    getConfig().set("mobs." + d[0] + ".theme", d[3]);
                    getConfig().set("mobs." + d[0] + ".icon", d[4]);
                    added = true;
                }
            }
            if (!getConfig().isSet("mob-ai")) getConfig().set("mob-ai", true);
            if (!getConfig().isSet("sun-safe-killpad")) getConfig().set("sun-safe-killpad", true);
            if (!getConfig().isSet("credit-last-damager")) getConfig().set("credit-last-damager", true);
            if (!getConfig().isSet("session-hud")) getConfig().set("session-hud", true);
            if (!getConfig().isSet("base-spawners")) getConfig().set("base-spawners", 2);
            if (!getConfig().isSet("normal-spawner-price")) getConfig().set("normal-spawner-price", 10000);
            if (!getConfig().isSet("extra-price-multiplier")) getConfig().set("extra-price-multiplier", 4);
            if (!getConfig().isSet("community.stack-start")) getConfig().set("community.stack-start", 2);
            if (added || !getConfig().isSet("mob-ai")) {
                try { saveConfig(); } catch (Throwable ignored) {}
            }
        }
    }

    private void writeBuiltinMobs() {
        for (String[] d : builtinMobTable()) {
            getConfig().set("mobs." + d[0] + ".display", d[1]);
            getConfig().set("mobs." + d[0] + ".entity", d[2]);
            getConfig().set("mobs." + d[0] + ".theme", d[3]);
            getConfig().set("mobs." + d[0] + ".icon", d[4]);
        }
    }

    private static String[][] builtinMobTable() {
        return new String[][] {
            {"zombie", "&2Zombie", "ZOMBIE", "dark", "ROTTEN_FLESH"},
            {"husk", "&6Husk", "HUSK", "dark", "SAND"},
            {"skeleton", "&fSkeleton", "SKELETON", "dark", "BONE"},
            {"stray", "&bStray", "STRAY", "dark", "ARROW"},
            {"spider", "&8Spider", "SPIDER", "dark", "SPIDER_EYE"},
            {"cave_spider", "&2Cave Spider", "CAVE_SPIDER", "dark", "FERMENTED_SPIDER_EYE"},
            {"creeper", "&aCreeper", "CREEPER", "dark", "GUNPOWDER"},
            {"enderman", "&5Enderman", "ENDERMAN", "end", "ENDER_PEARL"},
            {"blaze", "&6Blaze", "BLAZE", "nether", "BLAZE_ROD"},
            {"magma_cube", "&6Magma Cube", "MAGMA_CUBE", "nether", "MAGMA_CREAM"},
            {"wither_skeleton", "&8Wither Skeleton", "WITHER_SKELETON", "nether", "COAL"},
            {"drowned", "&3Drowned", "DROWNED", "ocean", "COPPER_INGOT"},
            {"guardian", "&3Guardian", "GUARDIAN", "ocean", "PRISMARINE_SHARD"},
            {"cow", "&fCow", "COW", "animal", "BEEF"},
            {"pig", "&dPig", "PIG", "animal", "PORKCHOP"},
            {"chicken", "&eChicken", "CHICKEN", "animal", "CHICKEN"},
            {"sheep", "&7Sheep", "SHEEP", "animal", "WHITE_WOOL"},
            {"rabbit", "&eRabbit", "RABBIT", "animal", "RABBIT"},
            {"slime", "&aSlime", "SLIME", "dark", "SLIME_BALL"},
        };
    }

    private void loadAll() {
        rooms.clear(); mobs.clear();
        String wn = getConfig().getString("center.world", "world");
        World w = Bukkit.getWorld(wn);
        if (w == null && !Bukkit.getWorlds().isEmpty()) w = Bukkit.getWorlds().get(0);
        if (w != null) center = new Location(w, getConfig().getDouble("center.x"),
                getConfig().getDouble("center.y"), getConfig().getDouble("center.z"));
        ConfigurationSection rs = getConfig().getConfigurationSection("rooms");
        if (rs != null) {
            List<String> ids = new ArrayList<>(rs.getKeys(false));
            ids.sort(String::compareTo);
            for (String id : ids) {
                ConfigurationSection c = rs.getConfigurationSection(id);
                if (c == null) continue;
                RoomDef r = new RoomDef();
                r.id = id; r.display = color(c.getString("display", id));
                r.ox = c.getInt("offset.x", 0); r.oy = c.getInt("offset.y", 0); r.oz = c.getInt("offset.z", -30);
                rooms.put(id, r);
            }
        }
        if (rooms.isEmpty()) {
            int[] xs = {-36, -18, 0, 18, 36};
            String[] names = {"&aFarm Cell 1", "&bFarm Cell 2", "&eFarm Cell 3", "&6Farm Cell 4", "&cFarm Cell 5"};
            for (int i = 0; i < 5; i++) {
                RoomDef r = new RoomDef();
                r.id = "room" + (i + 1); r.display = color(names[i]);
                r.ox = xs[i]; r.oy = 0; r.oz = -30; rooms.put(r.id, r);
            }
        }
        ConfigurationSection ms = getConfig().getConfigurationSection("mobs");
        if (ms != null) {
            for (String id : ms.getKeys(false)) {
                ConfigurationSection c = ms.getConfigurationSection(id);
                if (c == null) continue;
                addMob(id, c.getString("display", id), c.getString("entity", "ZOMBIE"),
                        c.getString("theme", "dark"), c.getString("icon", "ROTTEN_FLESH"));
            }
        }
        if (mobs.isEmpty()) for (String[] d : builtinMobTable()) addMob(d[0], d[1], d[2], d[3], d[4]);
        communityCoins = data.getLong("community.coins", 0L);
        communityTarget = data.getLong("community.target", getConfig().getLong("community.start-target", 1_000_000L));
        communityStack = data.getInt("community.stack", getConfig().getInt("community.stack-start", getConfig().getInt("base-spawners", 2)));
        if (communityStack < 1) communityStack = getConfig().getInt("community.stack-start", getConfig().getInt("base-spawners", 2));
        if (data.getBoolean("built", false) && center != null)
            for (RoomDef r : rooms.values()) computeGeom(r);
    }

    private void addMob(String id, String display, String entity, String theme, String icon) {
        MobDef m = new MobDef();
        m.id = id; m.display = color(display); m.theme = theme != null ? theme : "dark";
        try { m.entity = EntityType.valueOf(entity.toUpperCase(Locale.ROOT)); }
        catch (Exception ex) { m.entity = EntityType.ZOMBIE; }
        Material ic = Material.matchMaterial(icon != null ? icon : "ROTTEN_FLESH");
        m.icon = ic != null ? ic : Material.ROTTEN_FLESH;
        mobs.put(id, m);
    }
    private static String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private void saveData() {
        data.set("community.coins", communityCoins);
        data.set("community.target", communityTarget);
        data.set("community.stack", communityStack);
        try { data.save(dataFile); } catch (Exception ignored) {}
    }
    private int baseSpawners() {
        int cfg = getConfig().getInt("base-spawners", 2);
        int start = getConfig().getInt("community.stack-start", cfg);
        int stack = communityStack > 0 ? communityStack : start;
        return Math.max(1, stack);
    }
    private int stackCount(Session s) {
        return Math.min(getConfig().getInt("max-spawners", 25), baseSpawners() + s.extraSpawners);
    }
    /** First extra = normal-spawner-price * extra-price-multiplier; each further buy doubles. */
    private long extraCost(int extrasAlreadyBought) {
        long normal = getConfig().getLong("normal-spawner-price",
                getConfig().getLong("extra-spawner-base-cost", 10_000L));
        long mult = Math.max(1L, getConfig().getLong("extra-price-multiplier", 4L));
        long first = normal * mult;
        int n = Math.max(0, Math.min(20, extrasAlreadyBought));
        return first * (1L << n);
    }
    private boolean mobAiEnabled() { return getConfig().getBoolean("mob-ai", true); }
    private boolean sunSafe() { return getConfig().getBoolean("sun-safe-killpad", true); }
    private boolean creditLastDamager() { return getConfig().getBoolean("credit-last-damager", true); }
    private boolean sessionHud() { return getConfig().getBoolean("session-hud", true); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage(ChatColor.GOLD + "/mobfarm enter|leave|hub|status|buy|pick|info"
                    + (sender.hasPermission("mavomobfarm.admin")
                    ? "|setcenter|build|rebuild|clear|clearhere|purge|reload|resholo" : ""));
            return true;
        }
        String a = args[0].toLowerCase(Locale.ROOT);
        if (a.equals("info")) {
            sender.sendMessage(ChatColor.GOLD + "MobFarm 2.3.0 " + ChatColor.GRAY + "entry "
                    + ChatColor.YELLOW + getConfig().getInt("entry-cost")
                    + ChatColor.GRAY + " · " + getConfig().getInt("session-minutes") + "m"
                    + ChatColor.GRAY + " · XP×" + ChatColor.AQUA + getConfig().getDouble("profession-xp-scale"));
            sender.sendMessage(ChatColor.GRAY + "Community " + ChatColor.YELLOW + communityCoins + "/" + communityTarget
                    + ChatColor.GRAY + " · base stack " + ChatColor.GREEN + "x" + baseSpawners()
                    + ChatColor.GRAY + " · mobs " + mobs.size());
            sender.sendMessage(ChatColor.DARK_GRAY + "Stacks: ONE block ×N. Extra #1 = 4× spawner price, then doubles.");
            sender.sendMessage(ChatColor.DARK_GRAY + "mob-ai=" + mobAiEnabled() + " sun-safe=" + sunSafe()
                    + " — /mobfarm reload after config edit.");
            return true;
        }
        if (a.equals("reload")) {
            if (!sender.hasPermission("mavomobfarm.admin")) { sender.sendMessage(ChatColor.RED + "No."); return true; }
            reloadConfig(); ensureDefaults(); loadAll();
            sender.sendMessage(ChatColor.GREEN + "Reloaded. mobs=" + mobs.size()
                    + " ai=" + mobAiEnabled() + " base=" + baseSpawners()
                    + " hud=" + sessionHud());
            for (Session s : sessions.values()) {
                if (s.hud == null && sessionHud()) startHud(s); else updateHud(s);
            }
            return true;
        }
        if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
        switch (a) {
            case "setcenter" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                center = p.getLocation().getBlock().getLocation().add(0.5, 0, 0.5);
                getConfig().set("center.world", center.getWorld().getName());
                getConfig().set("center.x", center.getX());
                getConfig().set("center.y", center.getY());
                getConfig().set("center.z", center.getZ());
                saveConfig();
                p.sendMessage(ChatColor.GREEN + "Center set → /mobfarm build");
            }
            case "build", "rebuild" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                if (center == null) { p.sendMessage(ChatColor.RED + "/mobfarm setcenter first"); return true; }
                clearComplex(p); buildComplex(p);
            }
            case "clear" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                clearComplex(p);
            }
            case "clearhere" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                int rad = 120;
                if (args.length >= 2) try { rad = Integer.parseInt(args[1]); } catch (Exception ignored) {}
                clearHere(p, rad);
            }
            case "purge" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                int rad = 150;
                if (args.length >= 2) try { rad = Integer.parseInt(args[1]); } catch (Exception ignored) {}
                purgeOnly(p, rad);
            }
            case "resholo" -> {
                if (!p.hasPermission("mavomobfarm.admin")) { p.sendMessage(ChatColor.RED + "No."); return true; }
                spawnHubHolo(); refreshAllBayHolos(); p.sendMessage(ChatColor.GREEN + "Holo ok.");
            }
            case "enter" -> beginEnter(p);
            case "leave" -> leaveFarm(p);
            case "hub" -> goHub(p);
            case "status" -> status(p);
            case "buy" -> buySpawner(p);
            case "pick" -> openPick(p);
            default -> p.sendMessage(ChatColor.RED + "Unknown. /mobfarm info");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length != 1) return List.of();
        List<String> o = new ArrayList<>(List.of("enter", "leave", "hub", "status", "buy", "pick", "info"));
        if (s.hasPermission("mavomobfarm.admin"))
            o.addAll(List.of("setcenter", "build", "rebuild", "clear", "clearhere", "purge", "reload", "resholo"));
        return o.stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
    }

    private void status(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.endsAtMs < System.currentTimeMillis()) {
            p.sendMessage(ChatColor.GRAY + "No active session."); return;
        }
        long left = Math.max(0, (s.endsAtMs - System.currentTimeMillis()) / 1000L);
        MobDef m = mobs.get(s.mobId);
        p.sendMessage(ChatColor.GOLD + "Cell " + s.roomId
                + ChatColor.GRAY + " · " + (m != null ? ChatColor.stripColor(m.display) : s.mobId)
                + ChatColor.GRAY + " · " + (left / 60) + "m" + (left % 60) + "s"
                + ChatColor.GRAY + " · stack " + ChatColor.YELLOW + "x" + stackCount(s));
    }

    private void beginEnter(Player p) {
        if (center == null || !data.getBoolean("built", false)) {
            p.sendMessage(ChatColor.RED + "Farm not built. Admin: setcenter + build"); return;
        }
        Session existing = sessions.get(p.getUniqueId());
        if (existing != null && existing.endsAtMs > System.currentTimeMillis()) {
            existing.active = true;
            if (existing.spawnTask == null) startSpawnTask(existing);
            if (existing.hud == null) startHud(existing); else updateHud(existing);
            teleportToRoom(p, existing);
            p.sendMessage(ChatColor.GREEN + "Welcome back — same session/stack. /mobfarm pick"); return;
        }
        if (pending.containsKey(p.getUniqueId())) { p.sendMessage(ChatColor.YELLOW + "Already entering…"); return; }
        long cost = getConfig().getLong("entry-cost", 5000);
        if (econ == null || !econ.has(p, cost)) { p.sendMessage(ChatColor.RED + "Need " + cost + " coins."); return; }
        int secs = getConfig().getInt("entry-cancel-seconds", 10);
        PendingEnter pe = new PendingEnter(); pe.secondsLeft = secs; pe.cost = cost;
        pe.task = new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) { pending.remove(p.getUniqueId()); cancel(); return; }
                pe.secondsLeft--;
                if (pe.secondsLeft > 0) {
                    p.sendActionBar(ChatColor.GOLD + "Mob farm in " + pe.secondsLeft + "s… don't move!");
                    return;
                }
                pending.remove(p.getUniqueId()); cancel();
                if (!econ.has(p, pe.cost)) { p.sendMessage(ChatColor.RED + "Not enough coins."); return; }
                econ.withdrawPlayer(p, pe.cost); startSession(p);
            }
        }.runTaskTimer(this, 20L, 20L);
        pending.put(p.getUniqueId(), pe);
        p.sendMessage(ChatColor.GOLD + "Entering… " + secs + "s (move = cancel). Cost " + cost);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        PendingEnter pe = pending.get(e.getPlayer().getUniqueId());
        if (pe == null || e.getTo() == null) return;
        if (e.getFrom().getBlockX() != e.getTo().getBlockX()
                || e.getFrom().getBlockY() != e.getTo().getBlockY()
                || e.getFrom().getBlockZ() != e.getTo().getBlockZ()) {
            pe.task.cancel(); pending.remove(e.getPlayer().getUniqueId());
            e.getPlayer().sendMessage(ChatColor.RED + "Entry cancelled.");
        }
    }

    private void startSession(Player p) {
        String roomId = freeRoom();
        if (roomId == null) {
            p.sendMessage(ChatColor.RED + "All cells busy.");
            if (econ != null) econ.depositPlayer(p, getConfig().getLong("entry-cost", 5000));
            return;
        }
        Session s = new Session();
        s.owner = p.getUniqueId(); s.roomId = roomId;
        s.mobId = data.getString("rooms." + roomId + ".last-mob", "zombie");
        if (!mobs.containsKey(s.mobId)) s.mobId = mobs.keySet().iterator().next();
        s.endsAtMs = System.currentTimeMillis() + getConfig().getInt("session-minutes", 30) * 60_000L;
        s.returnLoc = p.getLocation().clone(); s.extraSpawners = 0;
        sessions.put(p.getUniqueId(), s);
        data.set("locks." + roomId, p.getUniqueId().toString()); saveData();
        setupCellStack(s); startSpawnTask(s); startHud(s); teleportToRoom(p, s);
        p.sendMessage(ChatColor.GREEN + "Cell unlocked: " + rooms.get(roomId).display
                + ChatColor.GRAY + " · stack " + ChatColor.YELLOW + "x" + stackCount(s));
        p.sendMessage(ChatColor.GRAY + "Stand on balcony, hit DOWN the hole. "
                + ChatColor.YELLOW + "/mobfarm pick · buy" + ChatColor.GRAY + " stacks SAME block.");
        openPick(p);
    }

    private String freeRoom() {
        List<String> ids = new ArrayList<>(rooms.keySet()); ids.sort(String::compareTo);
        for (String id : ids) {
            boolean taken = false;
            for (Session s : sessions.values()) {
                if (s.active && s.endsAtMs > System.currentTimeMillis() && id.equals(s.roomId)) { taken = true; break; }
            }
            String lock = data.getString("locks." + id);
            if (lock != null && !taken) {
                try {
                    UUID u = UUID.fromString(lock); Session ss = sessions.get(u);
                    if (ss == null || ss.endsAtMs < System.currentTimeMillis()) data.set("locks." + id, null);
                    else taken = true;
                } catch (Exception ex) { data.set("locks." + id, null); }
            }
            if (!taken) return id;
        }
        return null;
    }

    private void leaveFarm(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null) { p.sendMessage(ChatColor.GRAY + "No session."); return; }
        if (s.returnLoc != null) p.teleport(s.returnLoc); else if (center != null) p.teleport(center);
        p.sendMessage(ChatColor.YELLOW + "Left farm zone. Session timer still runs (HUD hides until you return). "
                + ChatColor.GRAY + "/mobfarm enter free while session live — stack kept.");
    }
    private void goHub(Player p) {
        if (center == null) { p.sendMessage(ChatColor.RED + "No hub."); return; }
        p.teleport(center.clone().add(0, 1, 0)); p.sendMessage(ChatColor.AQUA + "Hub.");
    }

    private void endSession(UUID u, boolean announce) {
        Session s = sessions.remove(u); if (s == null) return;
        if (s.spawnTask != null) s.spawnTask.cancel();
        stopHud(s);
        clearRoomMobs(s); removeStackHolo(s);
        if (s.stackLoc != null && s.stackLoc.getBlock().getType() == Material.SPAWNER) {
            if (s.stackLoc.getBlock().getState() instanceof CreatureSpawner cs) {
                try { cs.setSpawnedType(EntityType.PIG); cs.setDelay(9999); cs.update(true, false); } catch (Throwable ignored) {}
            }
        }
        data.set("locks." + s.roomId, null);
        data.set("rooms." + s.roomId + ".last-mob", s.mobId);
        Player p = Bukkit.getPlayer(u);
        if (p != null && p.isOnline()) {
            if (s.returnLoc != null) p.teleport(s.returnLoc); else if (center != null) p.teleport(center);
            if (announce) p.sendMessage(ChatColor.RED + "Session ended. Bought stacks cleared.");
        }
        saveData();
    }

    private void buySpawner(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.endsAtMs < System.currentTimeMillis()) { p.sendMessage(ChatColor.RED + "No session."); return; }
        int max = getConfig().getInt("max-spawners", 25);
        if (stackCount(s) >= max) { p.sendMessage(ChatColor.RED + "Max stack " + max); return; }
        long cost = extraCost(s.extraSpawners);
        if (econ == null || !econ.has(p, cost)) {
            p.sendMessage(ChatColor.RED + "Need " + cost + " coins for next stack (4× spawner, doubles each).");
            return;
        }
        econ.withdrawPlayer(p, cost); s.extraSpawners++; setupCellStack(s);
        updateHud(s);
        long next = extraCost(s.extraSpawners);
        p.sendMessage(ChatColor.GREEN + "Stack now " + ChatColor.YELLOW + "x" + stackCount(s)
                + ChatColor.GREEN + " same block! Paid " + cost + ". Next: " + next);
    }

    private void openPick(Player p) {
        Session s = sessions.get(p.getUniqueId());
        if (s == null || s.endsAtMs < System.currentTimeMillis()) { p.sendMessage(ChatColor.RED + "/mobfarm enter first"); return; }
        if (mobs.isEmpty()) for (String[] d : builtinMobTable()) addMob(d[0], d[1], d[2], d[3], d[4]);
        Inventory inv = Bukkit.createInventory(null, 27, ChatColor.DARK_RED + "Pick Farm Mob");
        int slot = 0;
        for (MobDef m : mobs.values()) {
            if (slot >= 27) break;
            ItemStack it = new ItemStack(m.icon);
            ItemMeta meta = it.getItemMeta(); meta.setDisplayName(m.display);
            List<String> lore = new ArrayList<>();
            lore.add(ChatColor.GRAY + "Theme: " + m.theme);
            lore.add(ChatColor.YELLOW + "Click to set cell");
            if (m.id.equals(s.mobId)) lore.add(ChatColor.GREEN + "✓ CURRENT");
            lore.add(ChatColor.DARK_GRAY + "Safe top-down kill hole");
            meta.setLore(lore); it.setItemMeta(meta); inv.setItem(slot++, it);
        }
        p.openInventory(inv);
    }

    @EventHandler
    public void onPickClick(InventoryClickEvent e) {
        String title; try { title = ChatColor.stripColor(e.getView().getTitle()); } catch (Throwable t) { return; }
        if (title == null || !title.equals("Pick Farm Mob")) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack cur = e.getCurrentItem();
        if (cur == null || !cur.hasItemMeta() || !cur.getItemMeta().hasDisplayName()) return;
        String name = ChatColor.stripColor(cur.getItemMeta().getDisplayName());
        Session s = sessions.get(p.getUniqueId());
        if (s == null) { p.closeInventory(); return; }
        String chosen = null;
        for (MobDef m : mobs.values()) if (ChatColor.stripColor(m.display).equals(name)) { chosen = m.id; break; }
        if (chosen == null) return;
        s.mobId = chosen; clearRoomMobs(s); applyTheme(s); setupCellStack(s);
        p.closeInventory();
        p.sendMessage(ChatColor.GREEN + "Cell → " + mobs.get(chosen).display
                + ChatColor.GRAY + " · hit DOWN the hole.");
    }

    private Location origin(RoomDef r) { return center.clone().add(r.ox, r.oy, r.oz); }

    private void computeGeom(RoomDef r) {
        Location o = origin(r); World w = o.getWorld();
        int x = o.getBlockX(), y = o.getBlockY(), z = o.getBlockZ();
        r.balcony = new Location(w, x + 0.5, y, z + 5.5);
        r.killPad = new Location(w, x + 0.5, y - 3, z + 0.5);
        r.lootChest = new Location(w, x + 4, y - 3, z + 5);
        r.door = new Location(w, x + 0.5, y, z + 10.5);
        r.stackBlock = new Location(w, x + 3, y + 1, z - 6);
    }

    private void buildComplex(Player admin) {
        World w = center.getWorld();
        int hx = center.getBlockX(), hy = center.getBlockY(), hz = center.getBlockZ();
        int minX = hx - 10, maxX = hx + 10, minZ = hz - 10, maxZ = hz + 10;
        for (RoomDef r : rooms.values()) {
            Location o = origin(r);
            minX = Math.min(minX, o.getBlockX() - 8); maxX = Math.max(maxX, o.getBlockX() + 10);
            minZ = Math.min(minZ, o.getBlockZ() - 12); maxZ = Math.max(maxZ, o.getBlockZ() + 14);
        }
        wipeBox(w, minX, hy - 8, minZ, maxX, hy + 8, maxZ);
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++) {
                Material fl = ((x + z) & 1) == 0 ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE;
                w.getBlockAt(x, hy - 1, z).setType(fl, false);
            }
        for (int x = -6; x <= 6; x++)
            for (int z = -6; z <= 6; z++) {
                Material fl = ((x + z) & 1) == 0 ? Material.SEA_LANTERN : Material.POLISHED_DEEPSLATE;
                w.getBlockAt(hx + x, hy - 1, hz + z).setType(fl, false);
                setAir(w, hx + x, hy, hz + z); setAir(w, hx + x, hy + 1, hz + z);
            }
        for (int[] c : new int[][]{{-6, -6}, {-6, 6}, {6, -6}, {6, 6}}) {
            w.getBlockAt(hx + c[0], hy, hz + c[1]).setType(Material.GLOWSTONE, false);
            w.getBlockAt(hx + c[0], hy + 1, hz + c[1]).setType(Material.GLOWSTONE, false);
        }
        placeDoubleChest(w, hx, hy, hz + 5, BlockFace.NORTH);
        Block sign = w.getBlockAt(hx, hy + 1, hz + 5);
        sign.setType(Material.OAK_SIGN, false);
        writeSign(sign, ChatColor.GOLD + "COMMUNITY", ChatColor.YELLOW + "FARM CHEST",
                ChatColor.WHITE + "Donate loot", ChatColor.GRAY + "→ stack goal");
        spawnHubHolo();
        for (int z = hz - 28; z <= hz - 6; z++) {
            for (int x = minX + 2; x <= maxX - 2; x++) {
                Material fl = ((x + z) & 1) == 0 ? Material.SEA_LANTERN : Material.DEEPSLATE_TILES;
                w.getBlockAt(x, hy - 1, z).setType(fl, false);
                setAir(w, x, hy, z); setAir(w, x, hy + 1, z); setAir(w, x, hy + 2, z);
                w.getBlockAt(x, hy + 3, z).setType(Material.DEEPSLATE_BRICKS, false);
            }
            for (int y = 0; y <= 3; y++) {
                w.getBlockAt(minX + 1, hy + y, z).setType(Material.DEEPSLATE_BRICKS, false);
                w.getBlockAt(maxX - 1, hy + y, z).setType(Material.DEEPSLATE_BRICKS, false);
            }
        }
        // hallway lights
        for (int z = hz - 28; z <= hz - 6; z += 3) {
            w.getBlockAt(hx, hy + 2, z).setType(Material.SEA_LANTERN, false);
            w.getBlockAt(minX + 4, hy + 2, z).setType(Material.SEA_LANTERN, false);
            w.getBlockAt(maxX - 4, hy + 2, z).setType(Material.SEA_LANTERN, false);
        }
        for (RoomDef r : rooms.values()) { computeGeom(r); buildRoom(r); }
        refreshAllBayHolos();
        data.set("built", true); saveData();
        admin.sendMessage(ChatColor.GREEN + "MobFarm 2.3.0 sealed grinders (" + rooms.size()
                + " cells). True stacks ready.");
        admin.sendMessage(ChatColor.GRAY + "Hit DOWN the balcony hole. /mobfarm buy stacks SAME block.");
    }

    private void setAir(World w, int x, int y, int z) { w.getBlockAt(x, y, z).setType(Material.AIR, false); }

    private void buildRoom(RoomDef r) {
        World w = center.getWorld(); computeGeom(r);
        int cx = origin(r).getBlockX(), cy = origin(r).getBlockY(), cz = origin(r).getBlockZ();
        for (int x = -7; x <= 9; x++)
            for (int z = -12; z <= 13; z++)
                for (int y = -7; y <= 7; y++) setAir(w, cx + x, cy + y, cz + z);

        /* ============================================================
         * CLASSIC DROP GRINDER
         * Spawn box (dark, sealed) with WATER TRENCH on floor center:
         *   sources at north → flow south into 2×2 drop (adults fit)
         * Spawner STACK sits EAST of trench (never blocks water).
         * Kill cell under balcony: carpet on hoppers, hit from ABOVE.
         * Loot: enclosed room + ladder on solid wall + real double chest.
         * ============================================================ */

        // ---- SPAWN BOX shell z=-10..-2 ----
        for (int x = -5; x <= 5; x++) {
            for (int z = -10; z <= -2; z++) {
                w.getBlockAt(cx + x, cy - 1, cz + z).setType(Material.DEEPSLATE_TILES, false);
                w.getBlockAt(cx + x, cy + 5, cz + z).setType(Material.DEEPSLATE_TILES, false);
                boolean edge = Math.abs(x) == 5 || z == -10;
                if (edge) {
                    for (int y = 0; y <= 5; y++)
                        w.getBlockAt(cx + x, cy + y, cz + z).setType(Material.DEEPSLATE_BRICKS, false);
                }
            }
        }
        // south wall of spawn box with 2×2 drop opening (x=-1..0, y=0..1) so adults pass; babies filtered in plugin
        for (int x = -5; x <= 5; x++) {
            for (int y = 0; y <= 5; y++) {
                boolean hole = (x == -1 || x == 0) && y <= 1;
                if (hole) setAir(w, cx + x, cy + y, cz - 2);
                else w.getBlockAt(cx + x, cy + y, cz - 2).setType(Material.DEEPSLATE_BRICKS, false);
            }
        }

        // ---- WATER TRENCH: 2-wide, glass sides, clear path to drop ----
        // trench x=-1..0, z=-9..-3, floor y=cy-1, water at y=cy
        for (int z = -9; z <= -3; z++) {
            // glass walls contain water (x=-2 and x=+1)
            w.getBlockAt(cx - 2, cy - 1, cz + z).setType(Material.PACKED_ICE, false); // faster flow
            w.getBlockAt(cx + 1, cy - 1, cz + z).setType(Material.PACKED_ICE, false);
            for (int y = 0; y <= 2; y++) {
                w.getBlockAt(cx - 2, cy + y, cz + z).setType(Material.GLASS, false);
                w.getBlockAt(cx + 1, cy + y, cz + z).setType(Material.GLASS, false);
            }
            // trench floor + air column
            for (int x = -1; x <= 0; x++) {
                w.getBlockAt(cx + x, cy - 1, cz + z).setType(Material.DEEPSLATE_TILES, false);
                setAir(w, cx + x, cy, cz + z);
                setAir(w, cx + x, cy + 1, cz + z);
                setAir(w, cx + x, cy + 2, cz + z);
            }
        }
        // open trench into drop (ensure z=-2 hole floor open)
        for (int x = -1; x <= 0; x++) {
            setAir(w, cx + x, cy - 1, cz - 2); // so water can fall
            setAir(w, cx + x, cy, cz - 2);
            setAir(w, cx + x, cy + 1, cz - 2);
        }
        // WATER: packed-ice floor, ONLY source blocks at the NORTH end so vanilla flows south.
        // (Multiple mid-canal sources = still water with no current — that was the bug.)
        for (int z = -9; z <= -3; z++) {
            for (int x = -1; x <= 0; x++) {
                w.getBlockAt(cx + x, cy - 1, cz + z).setType(Material.PACKED_ICE, false);
                setAir(w, cx + x, cy, cz + z);
                setAir(w, cx + x, cy + 1, cz + z);
            }
        }
        // single pair of sources at back only
        w.getBlockAt(cx - 1, cy, cz - 9).setType(Material.WATER, true);
        w.getBlockAt(cx, cy, cz - 9).setType(Material.WATER, true);

        // ---- STACKED SPAWNER east of trench (NOT in water) ----
        // platform at x=3
        for (int z = -8; z <= -4; z++) {
            w.getBlockAt(cx + 3, cy - 1, cz + z).setType(Material.OBSIDIAN, false);
            w.getBlockAt(cx + 2, cy - 1, cz + z).setType(Material.DEEPSLATE_TILES, false);
            w.getBlockAt(cx + 4, cy - 1, cz + z).setType(Material.DEEPSLATE_TILES, false);
        }
        w.getBlockAt(cx + 3, cy, cz - 6).setType(Material.OBSIDIAN, false);
        w.getBlockAt(cx + 3, cy + 1, cz - 6).setType(Material.SPAWNER, false);
        r.stackBlock = new Location(w, cx + 3, cy + 1, cz - 6);

        // glass floor viewing? no — keep dark. Sea lanterns ONLY outside box.

        // ---- DROP SHAFT z=-1..0, x=-1..0 down to kill ----
        for (int x = -1; x <= 0; x++) {
            for (int z = -1; z <= 0; z++) {
                for (int y = -4; y <= 1; y++) setAir(w, cx + x, cy + y, cz + z);
            }
        }
        // shaft walls
        for (int z = -1; z <= 0; z++) {
            for (int y = -4; y <= 3; y++) {
                w.getBlockAt(cx - 2, cy + y, cz + z).setType(Material.DEEPSLATE_BRICKS, false);
                w.getBlockAt(cx + 1, cy + y, cz + z).setType(Material.DEEPSLATE_BRICKS, false);
            }
        }

        // ---- KILL CELL x=-2..1, z=-1..2, floor hoppers at y=-4, carpet y=-3 ----
        for (int x = -2; x <= 1; x++) {
            for (int z = -1; z <= 2; z++) {
                w.getBlockAt(cx + x, cy - 4, cz + z).setType(Material.HOPPER, false);
                try {
                    org.bukkit.block.data.type.Hopper h =
                            (org.bukkit.block.data.type.Hopper) w.getBlockAt(cx + x, cy - 4, cz + z).getBlockData();
                    // drain toward east then south to loot
                    if (x < 1) h.setFacing(BlockFace.EAST);
                    else if (z < 2) h.setFacing(BlockFace.SOUTH);
                    else h.setFacing(BlockFace.EAST);
                    w.getBlockAt(cx + x, cy - 4, cz + z).setBlockData(h, false);
                } catch (Throwable ignored) {}
                w.getBlockAt(cx + x, cy - 3, cz + z).setType(Material.GRAY_CARPET, false);
                setAir(w, cx + x, cy - 2, cz + z);
                setAir(w, cx + x, cy - 1, cz + z);
            }
        }
        // sealed kill walls
        for (int x = -3; x <= 2; x++) {
            for (int z = -2; z <= 3; z++) {
                boolean wall = x == -3 || x == 2 || z == -2 || z == 3;
                if (!wall) continue;
                for (int y = -4; y <= 0; y++)
                    w.getBlockAt(cx + x, cy + y, cz + z).setType(Material.DEEPSLATE_BRICKS, false);
            }
        }
        // kill ceiling = balcony underside; 2×2 hit hole
        for (int x = -3; x <= 2; x++)
            for (int z = -1; z <= 2; z++)
                w.getBlockAt(cx + x, cy - 1, cz + z).setType(Material.DEEPSLATE_TILES, false);
        for (int x = -1; x <= 0; x++)
            for (int z = 0; z <= 1; z++)
                setAir(w, cx + x, cy - 1, cz + z);

        // sun-proof roof over kill + hole (undead won't burn in daylight)
        if (getConfig().getBoolean("sun-safe-killpad", true)) {
            for (int x = -3; x <= 2; x++)
                for (int z = -2; z <= 3; z++)
                    w.getBlockAt(cx + x, cy + 5, cz + z).setType(Material.DEEPSLATE_TILES, false);
            for (int x = -3; x <= 2; x++)
                for (int z = -1; z <= 2; z++) {
                    if (w.getBlockAt(cx + x, cy + 3, cz + z).getType() == Material.AIR)
                        w.getBlockAt(cx + x, cy + 3, cz + z).setType(Material.DEEPSLATE_TILES, false);
                }
        }

        // ---- BALCONY lit z=3..9 ----
        for (int x = -5; x <= 5; x++) {
            for (int z = 3; z <= 9; z++) {
                Material fl = ((x + z) & 1) == 0 ? Material.SEA_LANTERN : Material.DEEPSLATE_TILES;
                w.getBlockAt(cx + x, cy - 1, cz + z).setType(fl, false);
                setAir(w, cx + x, cy, cz + z);
                setAir(w, cx + x, cy + 1, cz + z);
                setAir(w, cx + x, cy + 2, cz + z);
                w.getBlockAt(cx + x, cy + 3, cz + z).setType(Material.DEEPSLATE_BRICKS, false);
            }
        }
        // extra lights on balcony roof underside
        for (int x = -4; x <= 4; x += 2)
            for (int z = 4; z <= 8; z += 2)
                w.getBlockAt(cx + x, cy + 2, cz + z).setType(Material.SEA_LANTERN, false);
        for (int z = 3; z <= 9; z++)
            for (int y = 0; y <= 2; y++) {
                w.getBlockAt(cx - 5, cy + y, cz + z).setType(Material.GLASS, false);
                w.getBlockAt(cx + 5, cy + y, cz + z).setType(Material.GLASS, false);
            }
        // iron bars around 2×2 hole
        for (int x = -2; x <= 1; x++) {
            w.getBlockAt(cx + x, cy, cz - 1).setType(Material.IRON_BARS, false);
            w.getBlockAt(cx + x, cy, cz + 2).setType(Material.IRON_BARS, false);
        }
        for (int z = 0; z <= 1; z++) {
            w.getBlockAt(cx - 2, cy, cz + z).setType(Material.IRON_BARS, false);
            w.getBlockAt(cx + 1, cy, cz + z).setType(Material.IRON_BARS, false);
        }
        // clear bar corners over hole air
        for (int x = -1; x <= 0; x++)
            for (int z = 0; z <= 1; z++)
                setAir(w, cx + x, cy, cz + z);

        // door frame + sign
        for (int y = 0; y <= 2; y++) {
            w.getBlockAt(cx - 2, cy + y, cz + 10).setType(Material.DEEPSLATE_BRICKS, false);
            w.getBlockAt(cx + 2, cy + y, cz + 10).setType(Material.DEEPSLATE_BRICKS, false);
        }
        w.getBlockAt(cx, cy + 3, cz + 10).setType(Material.DEEPSLATE_BRICKS, false);
        Block doorSign = w.getBlockAt(cx, cy + 2, cz + 11);
        doorSign.setType(Material.OAK_WALL_SIGN, false);
        try {
            org.bukkit.block.data.type.WallSign ws = (org.bukkit.block.data.type.WallSign) doorSign.getBlockData();
            ws.setFacing(BlockFace.SOUTH); doorSign.setBlockData(ws, false);
        } catch (Throwable ignored) {}
        writeSign(doorSign, ChatColor.GOLD + "MOBFARM", ChatColor.stripColor(r.display),
                ChatColor.GRAY + "Hit DOWN hole", ChatColor.YELLOW + "/mobfarm pick");
        for (int z = 10; z <= 12; z++)
            for (int x = -3; x <= 3; x++) {
                Material fl = ((x + z) & 1) == 0 ? Material.SEA_LANTERN : Material.DEEPSLATE_TILES;
                w.getBlockAt(cx + x, cy - 1, cz + z).setType(fl, false);
            }

        // ---- PER-BAY COMMUNITY CHEST (donate without walking to hub) ----
        placeDoubleChest(w, cx - 4, cy, cz + 8, BlockFace.SOUTH);
        r.communityChest = new Location(w, cx - 4, cy, cz + 8);
        Block baySign = w.getBlockAt(cx - 4, cy + 1, cz + 8);
        baySign.setType(Material.OAK_SIGN, false);
        writeSign(baySign, ChatColor.GOLD + "COMMUNITY", ChatColor.YELLOW + ChatColor.stripColor(r.display),
                ChatColor.WHITE + "Donate loot", ChatColor.GRAY + "→ stack goal");
        spawnBayHolo(r);

        // ---- LOOT ROOM east: FULL box, solid floor, ladder, double chest ----
        // room bounds x=3..8, z=2..8, floor y=-5, walk y=-4
        for (int x = 3; x <= 8; x++) {
            for (int z = 2; z <= 8; z++) {
                w.getBlockAt(cx + x, cy - 5, cz + z).setType(Material.DEEPSLATE_TILES, false); // FLOOR
                setAir(w, cx + x, cy - 4, cz + z);
                setAir(w, cx + x, cy - 3, cz + z);
                setAir(w, cx + x, cy - 2, cz + z);
                w.getBlockAt(cx + x, cy - 1, cz + z).setType(Material.DEEPSLATE_TILES, false); // ceiling
            }
        }
        // walls
        for (int z = 2; z <= 8; z++) {
            for (int y = -5; y <= -1; y++) {
                w.getBlockAt(cx + 3, cy + y, cz + z).setType(Material.DEEPSLATE_BRICKS, false);
                w.getBlockAt(cx + 8, cy + y, cz + z).setType(Material.DEEPSLATE_BRICKS, false);
            }
        }
        for (int x = 3; x <= 8; x++) {
            for (int y = -5; y <= -1; y++) {
                w.getBlockAt(cx + x, cy + y, cz + 2).setType(Material.DEEPSLATE_BRICKS, false);
                w.getBlockAt(cx + x, cy + y, cz + 8).setType(Material.DEEPSLATE_BRICKS, false);
            }
        }
        // lights in loot — NOT on hopper path (avoid blocking)
        w.getBlockAt(cx + 7, cy - 3, cz + 7).setType(Material.SEA_LANTERN, false);
        w.getBlockAt(cx + 4, cy - 3, cz + 7).setType(Material.SEA_LANTERN, false);
        w.getBlockAt(cx + 6, cy - 3, cz + 3).setType(Material.SEA_LANTERN, false);

        // Punch hopper tunnel through west wall (x=3) at y=-4, z=2
        for (int y = -5; y <= -3; y++) {
            setAir(w, cx + 3, cy + y, cz + 2);
        }
        w.getBlockAt(cx + 3, cy - 5, cz + 2).setType(Material.DEEPSLATE_TILES, false);

        // HOPPER LINE (no gaps, no bricks between, last faces INTO chest):
        // kill cell (1,-4,2) → east to (5,-4,2) → south to (5,-4,4) → east into chest (6,-4,4)
        int[][] hopPath = {
                // x, z, face: 0=E 1=S 2=W 3=N 4=DOWN
                {1, 2, 0}, {2, 2, 0}, {3, 2, 0}, {4, 2, 0}, {5, 2, 1},
                {5, 3, 1}, {5, 4, 0} // last faces EAST into chest at (6,-4,4)
        };
        for (int[] hp : hopPath) {
            int hx = cx + hp[0], hz = cz + hp[1];
            w.getBlockAt(hx, cy - 5, hz).setType(Material.DEEPSLATE_TILES, false); // solid under
            setAir(w, hx, cy - 3, hz);
            w.getBlockAt(hx, cy - 4, hz).setType(Material.HOPPER, false);
            try {
                org.bukkit.block.data.type.Hopper h =
                        (org.bukkit.block.data.type.Hopper) w.getBlockAt(hx, cy - 4, hz).getBlockData();
                BlockFace bf = switch (hp[2]) {
                    case 1 -> BlockFace.SOUTH;
                    case 2 -> BlockFace.WEST;
                    case 3 -> BlockFace.NORTH;
                    case 4 -> BlockFace.DOWN;
                    default -> BlockFace.EAST;
                };
                h.setFacing(bf);
                w.getBlockAt(hx, cy - 4, hz).setBlockData(h, false);
            } catch (Throwable ignored) {}
        }

        // Double chest directly east of last hopper — clear and place with physics
        setAir(w, cx + 6, cy - 4, cz + 4);
        setAir(w, cx + 7, cy - 4, cz + 4);
        setAir(w, cx + 6, cy - 3, cz + 4);
        setAir(w, cx + 7, cy - 3, cz + 4);
        w.getBlockAt(cx + 6, cy - 5, cz + 4).setType(Material.DEEPSLATE_TILES, false);
        w.getBlockAt(cx + 7, cy - 5, cz + 4).setType(Material.DEEPSLATE_TILES, false);
        placeDoubleChest(w, cx + 6, cy - 4, cz + 4, BlockFace.NORTH);
        r.lootChest = new Location(w, cx + 6, cy - 4, cz + 4);

        // Ensure last hopper still faces EAST after chest place
        w.getBlockAt(cx + 5, cy - 4, cz + 4).setType(Material.HOPPER, false);
        try {
            org.bukkit.block.data.type.Hopper h =
                    (org.bukkit.block.data.type.Hopper) w.getBlockAt(cx + 5, cy - 4, cz + 4).getBlockData();
            h.setFacing(BlockFace.EAST);
            w.getBlockAt(cx + 5, cy - 4, cz + 4).setBlockData(h, false);
        } catch (Throwable ignored) {}

        // Kill-cell hoppers must drain toward bridge entry (1,-4,2)
        for (int x = -2; x <= 1; x++) {
            for (int z = -1; z <= 2; z++) {
                Block hb = w.getBlockAt(cx + x, cy - 4, cz + z);
                if (hb.getType() != Material.HOPPER) continue;
                try {
                    org.bukkit.block.data.type.Hopper h = (org.bukkit.block.data.type.Hopper) hb.getBlockData();
                    if (z < 2) h.setFacing(BlockFace.SOUTH);
                    else if (x < 1) h.setFacing(BlockFace.EAST);
                    else h.setFacing(BlockFace.EAST); // (1,2) into bridge
                    hb.setBlockData(h, false);
                } catch (Throwable ignored) {}
            }
        }

        // ---- WALKWAY + LADDER (no jump required) ----
        // Solid balcony path to ladder hatch at (6..7, z=7)
        for (int x = 4; x <= 7; x++) {
            for (int z = 6; z <= 8; z++) {
                w.getBlockAt(cx + x, cy - 1, cz + z).setType(Material.DEEPSLATE_TILES, false);
                setAir(w, cx + x, cy, cz + z);
                setAir(w, cx + x, cy + 1, cz + z);
            }
        }
        // hatch 2x2 in balcony floor with solid rim
        setAir(w, cx + 6, cy - 1, cz + 7);
        setAir(w, cx + 7, cy - 1, cz + 7);
        setAir(w, cx + 6, cy - 1, cz + 8);
        setAir(w, cx + 7, cy - 1, cz + 8);
        // ladder shaft fully open with wall behind
        for (int y = -4; y <= 0; y++) {
            setAir(w, cx + 6, cy + y, cz + 7);
            setAir(w, cx + 7, cy + y, cz + 7);
            setAir(w, cx + 6, cy + y, cz + 8);
            setAir(w, cx + 7, cy + y, cz + 8);
            w.getBlockAt(cx + 8, cy + y, cz + 7).setType(Material.DEEPSLATE_BRICKS, false);
            w.getBlockAt(cx + 8, cy + y, cz + 8).setType(Material.DEEPSLATE_BRICKS, false);
            placeLadder(w.getBlockAt(cx + 7, cy + y, cz + 7), BlockFace.WEST);
            placeLadder(w.getBlockAt(cx + 7, cy + y, cz + 8), BlockFace.WEST);
        }
        // floor under ladder landing already solid; open loot room to ladder
        for (int z = 6; z <= 8; z++) {
            setAir(w, cx + 5, cy - 4, cz + z);
            setAir(w, cx + 5, cy - 3, cz + z);
            w.getBlockAt(cx + 5, cy - 5, cz + z).setType(Material.DEEPSLATE_TILES, false);
        }
        // sea lantern markers at hatch (on rim, not hole)
        w.getBlockAt(cx + 5, cy - 1, cz + 7).setType(Material.SEA_LANTERN, false);
        w.getBlockAt(cx + 5, cy - 1, cz + 6).setType(Material.SEA_LANTERN, false);
        w.getBlockAt(cx + 6, cy - 4, cz + 6).setType(Material.SEA_LANTERN, false);

        Block cs = w.getBlockAt(cx + 6, cy - 3, cz + 5);
        cs.setType(Material.OAK_WALL_SIGN, false);
        try {
            org.bukkit.block.data.type.WallSign ws = (org.bukkit.block.data.type.WallSign) cs.getBlockData();
            ws.setFacing(BlockFace.SOUTH); cs.setBlockData(ws, false);
        } catch (Throwable ignored) {}
        writeSign(cs, ChatColor.GOLD + "BAY LOOT", ChatColor.WHITE + "Double chest",
                ChatColor.GRAY + "Hopper fed", ChatColor.YELLOW + "Ladder ↑");

        // final: ONLY north sources (do not refill mid-canal)
        w.getBlockAt(cx - 1, cy, cz - 9).setType(Material.WATER, true);
        w.getBlockAt(cx, cy, cz - 9).setType(Material.WATER, true);
    }

    private void placeLadder(Block b, BlockFace facing) {
        b.setType(Material.LADDER, false);
        try {
            org.bukkit.block.data.type.Ladder lad = (org.bukkit.block.data.type.Ladder) b.getBlockData();
            lad.setFacing(facing);
            b.setBlockData(lad, false);
        } catch (Throwable ignored) {}
    }

    private void placeStair(Block b, BlockFace facing, boolean upsideDown) {
        b.setType(Material.DEEPSLATE_BRICK_STAIRS, false);
        try {
            Stairs st = (Stairs) b.getBlockData();
            st.setFacing(facing);
            st.setHalf(upsideDown ? Bisected.Half.TOP : Bisected.Half.BOTTOM);
            b.setBlockData(st, false);
        } catch (Throwable ignored) {}
    }

    /** Place a working double chest along +X. applyPhysics=true so vanilla links them. */
    private void placeDoubleChest(World w, int x, int y, int z, BlockFace facing) {
        Block a = w.getBlockAt(x, y, z);
        Block b = w.getBlockAt(x + 1, y, z);
        // clear neighbors that block chest pairing
        a.setType(Material.AIR, false);
        b.setType(Material.AIR, false);
        // place with physics so game forms double chest
        a.setType(Material.CHEST, true);
        b.setType(Material.CHEST, true);
        try {
            org.bukkit.block.data.type.Chest ca = (org.bukkit.block.data.type.Chest) a.getBlockData();
            org.bukkit.block.data.type.Chest cb = (org.bukkit.block.data.type.Chest) b.getBlockData();
            ca.setFacing(facing);
            cb.setFacing(facing);
            a.setBlockData(ca, true);
            b.setBlockData(cb, true);
            // force pair types (try both orientations)
            ca = (org.bukkit.block.data.type.Chest) a.getBlockData();
            cb = (org.bukkit.block.data.type.Chest) b.getBlockData();
            ca.setFacing(facing);
            cb.setFacing(facing);
            ca.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
            cb.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
            a.setBlockData(ca, true);
            b.setBlockData(cb, true);
            int size = 0;
            if (a.getState() instanceof Chest ch) size = ch.getInventory().getSize();
            if (size < 54) {
                ca = (org.bukkit.block.data.type.Chest) a.getBlockData();
                cb = (org.bukkit.block.data.type.Chest) b.getBlockData();
                ca.setFacing(facing);
                cb.setFacing(facing);
                ca.setType(org.bukkit.block.data.type.Chest.Type.RIGHT);
                cb.setType(org.bukkit.block.data.type.Chest.Type.LEFT);
                a.setBlockData(ca, true);
                b.setBlockData(cb, true);
            }
            if (a.getState() instanceof Chest ch2) {
                getLogger().info("Double chest at " + x + "," + y + "," + z + " size=" + ch2.getInventory().getSize());
            }
        } catch (Throwable t) {
            getLogger().warning("Double chest: " + t.getMessage());
        }
    }

    private void writeSign(Block b, String l0, String l1, String l2, String l3) {
        if (!(b.getState() instanceof Sign si)) return;
        try {
            si.getSide(Side.FRONT).setLine(0, nz(l0)); si.getSide(Side.FRONT).setLine(1, nz(l1));
            si.getSide(Side.FRONT).setLine(2, nz(l2)); si.getSide(Side.FRONT).setLine(3, nz(l3));
        } catch (Throwable t) {
            try { si.setLine(0, nz(l0)); si.setLine(1, nz(l1)); si.setLine(2, nz(l2)); si.setLine(3, nz(l3)); }
            catch (Throwable ignored) {}
        }
        si.update(true, false);
    }
    private static String nz(String s) { return s == null ? "" : s; }

    private void applyTheme(Session s) {
        RoomDef r = rooms.get(s.roomId); MobDef m = mobs.get(s.mobId);
        if (r == null || m == null || center == null) return;
        World w = center.getWorld();
        int cx = origin(r).getBlockX(), cy = origin(r).getBlockY(), cz = origin(r).getBlockZ();
        Material floor = switch (m.theme) {
            case "nether" -> Material.NETHERRACK;
            case "animal" -> Material.GRASS_BLOCK;
            case "ocean" -> Material.PRISMARINE;
            case "end" -> Material.END_STONE;
            default -> Material.DEEPSLATE_TILES;
        };
        for (int x = -3; x <= 3; x++)
            for (int z = -9; z <= -4; z++) {
                if (Math.abs(x) == 2) continue;
                Block fl = w.getBlockAt(cx + x, cy - 1, cz + z);
                if (fl.getType() != Material.GLASS && fl.getType() != Material.SPAWNER
                        && fl.getType() != Material.OBSIDIAN) fl.setType(floor, false);
            }
        Block ceil = w.getBlockAt(cx, cy + 4, cz - 6);
        switch (m.theme) {
            case "nether" -> ceil.setType(Material.SHROOMLIGHT, false);
            case "animal", "ocean" -> ceil.setType(Material.SEA_LANTERN, false);
            case "end" -> ceil.setType(Material.PURPLE_STAINED_GLASS, false);
            default -> ceil.setType(Material.DEEPSLATE_TILES, false);
        }
        // sun-safe solid cover over kill pad + hole (undead won't burn)
        if (sunSafe()) {
            for (int x = -3; x <= 2; x++)
                for (int z = -2; z <= 3; z++) {
                    Block roof = w.getBlockAt(cx + x, cy + 4, cz + z);
                    if (roof.getType() == Material.AIR || roof.getType().name().contains("GLASS"))
                        roof.setType(Material.DEEPSLATE_TILES, false);
                    Block high = w.getBlockAt(cx + x, cy + 5, cz + z);
                    if (high.getType() == Material.AIR)
                        high.setType(Material.DEEPSLATE_TILES, false);
                }
        }
        w.getBlockAt(cx - 1, cy, cz - 9).setType(Material.WATER, true);
        w.getBlockAt(cx, cy, cz - 9).setType(Material.WATER, true);
    }

    private void setupCellStack(Session s) {
        RoomDef r = rooms.get(s.roomId); MobDef m = mobs.get(s.mobId);
        if (r == null || m == null || center == null) return;
        if (r.stackBlock == null) computeGeom(r);
        World w = center.getWorld();
        int cx = origin(r).getBlockX(), cy = origin(r).getBlockY(), cz = origin(r).getBlockZ();
        // east of water trench — never blocks flow
        Block b = w.getBlockAt(cx + 3, cy + 1, cz - 6);
        w.getBlockAt(cx + 3, cy, cz - 6).setType(Material.OBSIDIAN, false);
        b.setType(Material.SPAWNER, false); s.stackLoc = b.getLocation().clone();
        r.stackBlock = s.stackLoc.clone();
        if (b.getState() instanceof CreatureSpawner cs) {
            try {
                cs.setSpawnedType(m.entity);
                cs.setDelay(Integer.MAX_VALUE / 4);
                try {
                    cs.setMinSpawnDelay(99999); cs.setMaxSpawnDelay(99999);
                    cs.setSpawnCount(0); cs.setRequiredPlayerRange(32);
                } catch (Throwable ignored) {}
                cs.update(true, false);
            } catch (Throwable t) { getLogger().warning("Spawner display: " + t.getMessage()); }
        }
        applyTheme(s); spawnStackHolo(s);
    }

    private void spawnStackHolo(Session s) {
        removeStackHolo(s); if (s.stackLoc == null) return;
        // wipe ANY leftover stack holos in the whole cell
        RoomDef rr = rooms.get(s.roomId);
        if (rr != null) {
            Location o = origin(rr);
            for (Entity e : o.getWorld().getNearbyEntities(o, 20, 12, 20)) {
                if (e instanceof TextDisplay td
                        && td.getPersistentDataContainer().has(stackHoloKey, PersistentDataType.BYTE))
                    td.remove();
            }
        }
        Location loc = s.stackLoc.clone().add(0.5, 1.4, 0.5);
        int n = stackCount(s); MobDef m = mobs.get(s.mobId);
        String name = m != null ? ChatColor.stripColor(m.display) : s.mobId;
        String text = ChatColor.GOLD + "" + ChatColor.BOLD + "STACK x" + n
                + "\n" + ChatColor.WHITE + name + "\n" + ChatColor.GRAY + "/mobfarm buy to add";
        s.stackHolo = loc.getWorld().spawn(loc, TextDisplay.class, d -> {
            d.setText(text); d.setBillboard(Display.Billboard.CENTER); d.setShadowed(true);
            try { d.setDefaultBackground(false); d.setBackgroundColor(org.bukkit.Color.fromARGB(160, 10, 10, 30)); }
            catch (Throwable ignored) {}
            d.setLineWidth(120);
            var tr = d.getTransformation(); tr.getScale().set(0.9f); d.setTransformation(tr);
            d.setPersistent(false);
            d.getPersistentDataContainer().set(stackHoloKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void removeStackHolo(Session s) {
        if (s.stackHolo != null && !s.stackHolo.isDead()) s.stackHolo.remove();
        s.stackHolo = null;
        if (s.stackLoc != null) {
            for (Entity e : s.stackLoc.getWorld().getNearbyEntities(s.stackLoc.clone().add(0.5, 1.4, 0.5), 2, 2, 2)) {
                if (e instanceof TextDisplay td
                        && td.getPersistentDataContainer().has(stackHoloKey, PersistentDataType.BYTE)) td.remove();
            }
        }
    }

    private void startSpawnTask(Session s) {
        if (s.spawnTask != null) s.spawnTask.cancel();
        RoomDef r = rooms.get(s.roomId);
        if (r == null) return;
        final Location o = origin(r);
        final long spawnEvery = Math.max(20L, getConfig().getLong("stack-spawn-interval-ticks", 40L));
        final int maxMobs = getConfig().getInt("max-mobs-in-cell", 16);

        s.spawnTask = new BukkitRunnable() {
            long age = 0;
            @Override public void run() {
                age += 5;
                if (!data.getBoolean("built", false)) { cancel(); return; }
                Session live = sessions.get(s.owner);
                if (live == null || live != s || s.endsAtMs < System.currentTimeMillis()) { cancel(); return; }
                if (s.spawnTask == null) { cancel(); return; }
                MobDef m = mobs.get(s.mobId); if (m == null) return;
                Player owner = Bukkit.getPlayer(s.owner);
                if (owner == null || !owner.isOnline()) return;
                if (owner.getWorld() != o.getWorld() || owner.getLocation().distanceSquared(o) > 48 * 48) return;

                World w = o.getWorld();
                int bx = o.getBlockX(), by = o.getBlockY(), bz = o.getBlockZ();
                boolean ai = mobAiEnabled();
                Location pad = new Location(w, bx - 0.5, by - 2.0, bz + 0.5);

                // north water sources only (cosmetic when AI on; unused when frozen)
                Block wa = w.getBlockAt(bx - 1, by, bz - 9);
                Block wb = w.getBlockAt(bx, by, bz - 9);
                if (wa.getType() != Material.WATER) wa.setType(Material.WATER, true);
                if (wb.getType() != Material.WATER) wb.setType(Material.WATER, true);

                int alive = 0;
                for (LivingEntity le : w.getLivingEntities()) {
                    if (le instanceof Player) continue;
                    if (!le.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) continue;
                    Location loc = le.getLocation();
                    if (loc.getWorld() != w || loc.distanceSquared(o) > 22 * 22) continue;
                    alive++;
                    if (loc.getY() > by + 6 || loc.getY() < by - 8) { le.remove(); continue; }

                    try { le.setAI(ai); } catch (Throwable ignored) {}
                    try { le.setCollidable(true); } catch (Throwable ignored) {}
                    try { le.setGravity(true); } catch (Throwable ignored) {}
                    if (sunSafe()) {
                        try { le.setFireTicks(0); } catch (Throwable ignored) {}
                        try { le.setVisualFire(false); } catch (Throwable ignored) {}
                    }

                    if (!ai) {
                        // freeze-mode: keep on pad under hole
                        double relY = loc.getY() - by, relZ = loc.getZ() - bz, relX = loc.getX() - bx;
                        boolean onPad = relY < -1.2 && relY > -3.6 && relZ > -1.5 && relZ < 3.0 && relX > -2.5 && relX < 2.0;
                        if (!onPad) {
                            Location dest = pad.clone();
                            dest.setX(bx - 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8);
                            dest.setZ(bz + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.8);
                            dest.setY(by - 2.0);
                            le.teleport(dest);
                            le.setVelocity(new Vector(0, -0.2, 0));
                        } else {
                            double pullX = (bx - 0.5) - loc.getX();
                            double pullZ = (bz + 0.5) - loc.getZ();
                            le.setVelocity(new Vector(pullX * 0.12, Math.min(-0.05, le.getVelocity().getY()), pullZ * 0.12));
                        }
                    } else {
                        // AI on: gentle pull toward pad so they gather under the hole
                        double dist = loc.distanceSquared(pad);
                        if (dist > 9 && dist < 400) {
                            Vector v = pad.toVector().subtract(loc.toVector());
                            if (v.lengthSquared() > 0.01) {
                                v.setY(Math.max(-0.1, Math.min(0.15, v.getY())));
                                v.normalize().multiply(0.18);
                                le.setVelocity(le.getVelocity().multiply(0.4).add(v));
                            }
                        }
                    }
                }

                if (age % spawnEvery != 0) return;
                if (alive >= maxMobs) return;
                int stack = stackCount(s);
                int per = Math.max(1, getConfig().getInt("spawn-per-stack", 1));
                int want = Math.min(Math.min(stack * per, maxMobs - alive), Math.max(1, Math.min(3, stack)));

                for (int i = 0; i < want; i++) {
                    try {
                        Location at = new Location(w,
                                bx - 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 1.0,
                                by - 2.0,
                                bz + 0.5 + (ThreadLocalRandom.current().nextDouble() - 0.5) * 0.9);
                        if (at.getBlock().getType().isSolid()) at.setY(by - 1.9);
                        Entity ent = w.spawnEntity(at, m.entity);
                        if (ent instanceof LivingEntity le) {
                            try {
                                if (le instanceof org.bukkit.entity.Ageable ag && !ag.isAdult()) ag.setAdult();
                                if (le.getVehicle() != null) le.getVehicle().remove();
                                le.eject();
                                for (Entity pass : new ArrayList<>(le.getPassengers())) pass.remove();
                            } catch (Throwable ignored) {}
                            le.getPersistentDataContainer().set(farmMobKey, PersistentDataType.BYTE, (byte) 1);
                            le.setRemoveWhenFarAway(true);
                            le.setCanPickupItems(false);
                            try { le.setAI(ai); } catch (Throwable ignored) {}
                            if (sunSafe()) try { le.setFireTicks(0); } catch (Throwable ignored) {}
                            if (owner != null) {
                                le.getPersistentDataContainer().set(
                                        new NamespacedKey(MobFarm.this, "lasthit"),
                                        PersistentDataType.STRING, owner.getUniqueId().toString());
                            }
                            try {
                                if (le.getAttribute(Attribute.FOLLOW_RANGE) != null)
                                    le.getAttribute(Attribute.FOLLOW_RANGE).setBaseValue(ai ? 16 : 2);
                            } catch (Throwable ignored) {}
                            le.setVelocity(new Vector(0, -0.15, 0));
                        }
                    } catch (Throwable t) {
                        getLogger().warning("Spawn " + m.entity + ": " + t.getMessage());
                        break;
                    }
                }
            }
        }.runTaskTimer(this, 15L, 5L);
    }


    private void startHud(Session s) {
        stopHud(s);
        if (!sessionHud()) return;
        Player p = Bukkit.getPlayer(s.owner);
        String title = color(getConfig().getString("session-hud-title",
                "&6MobFarm &7| &e{mm}:{ss} &7left &8| &fstack &ex{stack}"));
        s.hud = Bukkit.createBossBar(formatHud(title, s), BarColor.YELLOW, BarStyle.SEGMENTED_10);
        s.hud.setProgress(1.0);
        s.hud.setVisible(true);
        if (p != null && p.isOnline()) s.hud.addPlayer(p);
        s.hudTask = new BukkitRunnable() {
            @Override public void run() {
                Session live = sessions.get(s.owner);
                if (live == null || live != s) { cancel(); return; }
                long left = s.endsAtMs - System.currentTimeMillis();
                if (left <= 0) { cancel(); return; }
                updateHud(s);
            }
        }.runTaskTimer(this, 10L, 20L);
    }

    private void updateHud(Session s) {
        if (s.hud == null) return;
        long leftMs = Math.max(0, s.endsAtMs - System.currentTimeMillis());
        long total = Math.max(1L, getConfig().getInt("session-minutes", 30) * 60_000L);
        double prog = Math.max(0.0, Math.min(1.0, leftMs / (double) total));
        s.hud.setProgress(prog);
        String title = color(getConfig().getString("session-hud-title",
                "&6MobFarm &7| &e{mm}:{ss} &7left &8| &fstack &ex{stack}"));
        s.hud.setTitle(formatHud(title, s));
        Player p = Bukkit.getPlayer(s.owner);
        if (p == null || !p.isOnline()) {
            s.hud.removeAll();
            return;
        }
        // show bar only when near farm; countdown continues silently when away
        boolean near = center != null && p.getWorld() == center.getWorld()
                && p.getLocation().distanceSquared(center) < 90 * 90;
        if (near) {
            if (!s.hud.getPlayers().contains(p)) s.hud.addPlayer(p);
            s.hud.setVisible(true);
        } else {
            s.hud.removePlayer(p);
        }
    }

    private String formatHud(String tmpl, Session s) {
        long left = Math.max(0, (s.endsAtMs - System.currentTimeMillis()) / 1000L);
        long mm = left / 60, ss = left % 60;
        return tmpl.replace("{mm}", String.format("%02d", mm))
                .replace("{ss}", String.format("%02d", ss))
                .replace("{stack}", String.valueOf(stackCount(s)))
                .replace("{room}", s.roomId)
                .replace("{mob}", s.mobId);
    }

    private void stopHud(Session s) {
        if (s.hudTask != null) { try { s.hudTask.cancel(); } catch (Throwable ignored) {} s.hudTask = null; }
        if (s.hud != null) {
            try { s.hud.setVisible(false); s.hud.removeAll(); } catch (Throwable ignored) {}
            s.hud = null;
        }
    }

    private void clearRoomMobs(Session s) {
        RoomDef r = rooms.get(s.roomId); if (r == null || center == null) return;
        Location o = origin(r);
        for (LivingEntity le : o.getWorld().getLivingEntities()) {
            if (le instanceof Player) continue;
            if (le.getLocation().distanceSquared(o) < 20 * 20) le.remove();
        }
    }

    private void teleportToRoom(Player p, Session s) {
        RoomDef r = rooms.get(s.roomId); if (r == null) return;
        if (r.balcony == null) computeGeom(r);
        Location dest = r.balcony.clone(); dest.setYaw(180f); dest.setPitch(45f);
        p.teleport(dest);
    }

    private void spawnHubHolo() {
        if (center == null) return;
        World w = center.getWorld(); Location loc = center.clone().add(0, 3.2, 5);
        for (var e : w.getNearbyEntities(loc, 16, 10, 16)) {
            if (e instanceof TextDisplay td && td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE))
                td.remove();
        }
        String line = ChatColor.GOLD + "" + ChatColor.BOLD + "COMMUNITY FARM CHEST"
                + "\n" + ChatColor.WHITE + "Put farm loot here"
                + "\n" + ChatColor.GRAY + "Funds more base stack"
                + "\n" + ChatColor.YELLOW + communityCoins + "/" + communityTarget
                + "\n" + ChatColor.AQUA + "/mobfarm enter";
        w.spawn(loc, TextDisplay.class, d -> {
            d.setText(line); d.setBillboard(Display.Billboard.CENTER); d.setShadowed(true);
            try { d.setDefaultBackground(false); d.setBackgroundColor(org.bukkit.Color.fromARGB(180, 20, 40, 20)); }
            catch (Throwable ignored) {}
            d.setLineWidth(200);
            var tr = d.getTransformation(); tr.getScale().set(1.25f); d.setTransformation(tr);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(holoKey, PersistentDataType.BYTE, (byte) 1);
        });
    }


    private void spawnBayHolo(RoomDef r) {
        if (r.communityChest == null || center == null) return;
        Location loc = r.communityChest.clone().add(0.5, 2.4, 0.5);
        World w = loc.getWorld();
        String roomName = ChatColor.stripColor(r.display);
        for (var e : w.getNearbyEntities(loc, 3, 4, 3)) {
            if (e instanceof TextDisplay td && td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE)) {
                String t = td.getText();
                if (t != null && (t.contains(roomName) || t.contains("COMMUNITY"))) td.remove();
            }
        }
        String line = ChatColor.GOLD + "" + ChatColor.BOLD + "COMMUNITY"
                + "\n" + r.display
                + "\n" + ChatColor.WHITE + "Donate farm loot"
                + "\n" + ChatColor.YELLOW + communityCoins + "/" + communityTarget
                + "\n" + ChatColor.GRAY + "→ base stack x" + baseSpawners();
        w.spawn(loc, TextDisplay.class, d -> {
            d.setText(line);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            try {
                d.setDefaultBackground(false);
                d.setBackgroundColor(org.bukkit.Color.fromARGB(170, 20, 40, 20));
            } catch (Throwable ignored) {}
            d.setLineWidth(140);
            var tr = d.getTransformation(); tr.getScale().set(1.0f); d.setTransformation(tr);
            d.setPersistent(true);
            d.getPersistentDataContainer().set(holoKey, PersistentDataType.BYTE, (byte) 1);
        });
    }

    private void refreshAllBayHolos() {
        for (RoomDef r : rooms.values()) if (r.communityChest != null) spawnBayHolo(r);
    }

    /** Stop every farm session (cancels spawn tasks), kill farm mobs, remove holos. */
    private void stopAllFarmActivity(String reason) {
        for (UUID u : new ArrayList<>(sessions.keySet())) {
            Session s = sessions.get(u);
            if (s != null && s.spawnTask != null) {
                try { s.spawnTask.cancel(); } catch (Throwable ignored) {}
                s.spawnTask = null;
            }
            endSession(u, false);
        }
        sessions.clear();
        // cancel pending enters
        for (UUID u : new ArrayList<>(pending.keySet())) {
            PendingEnter pe = pending.remove(u);
            if (pe != null && pe.task != null) try { pe.task.cancel(); } catch (Throwable ignored) {}
        }
        // clear room locks so bays free
        if (data.getConfigurationSection("locks") != null) {
            for (String k : new ArrayList<>(data.getConfigurationSection("locks").getKeys(false)))
                data.set("locks." + k, null);
        }
        getLogger().info("Stopped all farm activity (" + reason + ").");
    }

    private int purgeFarmEntities(World w, Location around, double radius) {
        int mobs = 0, holos = 0;
        for (Entity e : w.getNearbyEntities(around, radius, radius, radius)) {
            if (e instanceof Player) continue;
            if (e instanceof TextDisplay td) {
                boolean ours = td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE)
                        || td.getPersistentDataContainer().has(stackHoloKey, PersistentDataType.BYTE);
                String t = null;
                try { t = td.getText(); } catch (Throwable ignored) {}
                if (ours || (t != null && (t.contains("STACK") || t.contains("COMMUNITY FARM")
                        || t.contains("mobfarm buy") || t.contains("/mobfarm")))) {
                    td.remove(); holos++;
                }
                continue;
            }
            if (e instanceof LivingEntity le) {
                boolean tagged = le.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE);
                // also remove strays near farm that look like grind leftovers
                if (tagged || (around.distanceSquared(le.getLocation()) < radius * radius
                        && isFarmMobType(le.getType()))) {
                    le.remove(); mobs++;
                }
            }
        }
        getLogger().info("Purged farm entities: mobs=" + mobs + " holos=" + holos + " r=" + (int) radius);
        return mobs + holos;
    }

    private boolean isFarmMobType(EntityType t) {
        if (t == null) return false;
        for (MobDef m : mobs.values()) if (m.entity == t) return true;
        return t == EntityType.ZOMBIE || t == EntityType.SKELETON || t == EntityType.CREEPER
                || t == EntityType.SPIDER || t == EntityType.BLAZE || t == EntityType.DROWNED
                || t == EntityType.HUSK || t == EntityType.STRAY || t == EntityType.CAVE_SPIDER
                || t == EntityType.ENDERMAN || t == EntityType.MAGMA_CUBE || t == EntityType.WITHER_SKELETON
                || t == EntityType.GUARDIAN || t == EntityType.SLIME || t == EntityType.RABBIT
                || t == EntityType.CHICKEN; // jockey leftovers
    }

    private void clearComplex(Player admin) {
        if (center == null) { admin.sendMessage(ChatColor.RED + "No center."); return; }
        stopAllFarmActivity("clear");
        World w = center.getWorld();
        int minX = center.getBlockX() - 16, maxX = center.getBlockX() + 16;
        int minZ = center.getBlockZ() - 16, maxZ = center.getBlockZ() + 16;
        int minY = center.getBlockY() - 12, maxY = center.getBlockY() + 12;
        for (RoomDef r : rooms.values()) {
            Location o = origin(r);
            minX = Math.min(minX, o.getBlockX() - 12); maxX = Math.max(maxX, o.getBlockX() + 14);
            minZ = Math.min(minZ, o.getBlockZ() - 14); maxZ = Math.max(maxZ, o.getBlockZ() + 16);
            minY = Math.min(minY, o.getBlockY() - 10); maxY = Math.max(maxY, o.getBlockY() + 10);
        }
        int ents = purgeFarmEntities(w, center.clone(), 100);
        int n = wipeBox(w, minX, minY, minZ, maxX, maxY, maxZ);
        // second pass holos after wipe (floating leftovers)
        ents += purgeFarmEntities(w, center.clone(), 100);
        data.set("built", false); saveData();
        admin.sendMessage(ChatColor.YELLOW + "Cleared " + n + " blocks + " + ents
                + " mobs/holos. Sessions stopped.");
    }

    private void clearHere(Player admin, int radius) {
        radius = Math.max(8, Math.min(150, radius));
        Location c = admin.getLocation();
        World w = c.getWorld();
        // ALWAYS stop sessions first — otherwise spawn tasks keep making mobs in the sky
        stopAllFarmActivity("clearhere");
        int ents = purgeFarmEntities(w, c, radius);
        int n = wipeBox(w, c.getBlockX() - radius, c.getBlockY() - radius, c.getBlockZ() - radius,
                c.getBlockX() + radius, c.getBlockY() + radius, c.getBlockZ() + radius);
        ents += purgeFarmEntities(w, c, radius);
        data.set("built", false); saveData();
        admin.sendMessage(ChatColor.YELLOW + "clearhere r=" + radius + " → " + n + " blocks, "
                + ents + " mobs/holos removed. All sessions STOPPED.");
        admin.sendMessage(ChatColor.GRAY + "Rebuild with /mobfarm setcenter then /mobfarm build");
    }

    /** Admin: kill orphan farm mobs + holos without wiping blocks. */
    private void purgeOnly(Player admin, int radius) {
        radius = Math.max(8, Math.min(200, radius));
        stopAllFarmActivity("purge");
        int ents = purgeFarmEntities(admin.getWorld(), admin.getLocation(), radius);
        admin.sendMessage(ChatColor.YELLOW + "Purge r=" + radius + ": removed " + ents
                + " mobs/holos, sessions stopped.");
    }

    private int wipeBox(World w, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int n = 0;
        for (int x = minX; x <= maxX; x++)
            for (int z = minZ; z <= maxZ; z++)
                for (int y = minY; y <= maxY; y++) {
                    Block b = w.getBlockAt(x, y, z); Material t = b.getType();
                    if (t != Material.AIR && t != Material.CAVE_AIR && t != Material.VOID_AIR) {
                        b.setType(Material.AIR, false); n++;
                    }
                }
        return n;
    }

    @EventHandler(ignoreCancelled = true)
    public void onPvp(EntityDamageByEntityEvent e) {
        if (getConfig().getBoolean("pvp", false)) return;
        if (!(e.getEntity() instanceof Player) || !(e.getDamager() instanceof Player)) return;
        if (center != null && (nearFarm(e.getEntity().getLocation()) || nearFarm(e.getDamager().getLocation())))
            e.setCancelled(true);
    }
    private boolean nearFarm(Location l) {
        if (center == null || l.getWorld() != center.getWorld()) return false;
        return l.distanceSquared(center) < 100 * 100;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        if (center == null) return;
        Location l = e.getLocation();
        if (l.getWorld() != center.getWorld() || l.distanceSquared(center) > 100 * 100) return;
        // strip chicken jockeys / unwanted mounts in farm radius
        LivingEntity ent = e.getEntity();
        if (ent.getVehicle() != null) {
            Entity v = ent.getVehicle();
            if (v.getType() == EntityType.CHICKEN) { e.setCancelled(true); return; }
        }
        for (Entity pass : new ArrayList<>(ent.getPassengers())) {
            if (pass.getType() == EntityType.CHICKEN || pass.getType() == EntityType.ZOMBIE) pass.remove();
        }
        try {
            if (ent instanceof org.bukkit.entity.Ageable age && !age.isAdult()) age.setAdult();
        } catch (Throwable ignored) {}
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawnerSpawn(SpawnerSpawnEvent e) {
        if (center == null) return;
        Location l = e.getSpawner().getLocation();
        if (l.getWorld() == center.getWorld() && l.distanceSquared(center) < 100 * 100) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (center != null && nearFarm(e.getEntity().getLocation())) { e.blockList().clear(); e.setYield(0f); }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFarmDamage(EntityDamageByEntityEvent e) {
        if (!creditLastDamager()) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!le.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) return;
        Player p = null;
        if (e.getDamager() instanceof Player pl) p = pl;
        else if (e.getDamager() instanceof Projectile proj && proj.getShooter() instanceof Player pl) p = pl;
        if (p == null) return;
        le.getPersistentDataContainer().set(new NamespacedKey(this, "lasthit"),
                PersistentDataType.STRING, p.getUniqueId().toString());
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onFarmCombust(EntityDamageEvent e) {
        if (!sunSafe()) return;
        if (!(e.getEntity() instanceof LivingEntity le)) return;
        if (!le.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) return;
        EntityDamageEvent.DamageCause c = e.getCause();
        if (c == EntityDamageEvent.DamageCause.FIRE
                || c == EntityDamageEvent.DamageCause.FIRE_TICK
                || c == EntityDamageEvent.DamageCause.LAVA
                || c == EntityDamageEvent.DamageCause.HOT_FLOOR) {
            e.setCancelled(true);
            try { le.setFireTicks(0); } catch (Throwable ignored) {}
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        LivingEntity victim = e.getEntity();
        if (!victim.getPersistentDataContainer().has(farmMobKey, PersistentDataType.BYTE)) return;
        // credit last damager so achievements see getKiller() even if fire somehow ticks
        if (victim.getKiller() == null && creditLastDamager()) {
            String id = victim.getPersistentDataContainer().get(
                    new NamespacedKey(this, "lasthit"), PersistentDataType.STRING);
            if (id != null) {
                try {
                    Player p = Bukkit.getPlayer(UUID.fromString(id));
                    if (p != null && p.isOnline()) {
                        try { victim.setKiller(p); } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }
        }
        // drops still go hoppers under carpet
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        PendingEnter pe = pending.remove(e.getPlayer().getUniqueId());
        if (pe != null && pe.task != null) pe.task.cancel();
    }

    @EventHandler
    public void onCommunityClose(InventoryCloseEvent e) {
        if (!(e.getPlayer() instanceof Player p) || center == null) return;
        Inventory top = e.getInventory();
        Location il;
        try { il = top.getLocation(); } catch (Throwable t) { return; }
        if (il == null || il.getWorld() != center.getWorld()) return;
        boolean isCommunity = false;
        // hub chest
        if (Math.abs(il.getBlockX() - center.getBlockX()) <= 1
                && il.getBlockY() == center.getBlockY()
                && il.getBlockZ() == center.getBlockZ() + 5) isCommunity = true;
        // per-bay chests
        if (!isCommunity) {
            for (RoomDef r : rooms.values()) {
                if (r.communityChest == null) continue;
                if (il.getBlockY() != r.communityChest.getBlockY()) continue;
                if (il.getBlockZ() != r.communityChest.getBlockZ()) continue;
                if (Math.abs(il.getBlockX() - r.communityChest.getBlockX()) <= 1) {
                    isCommunity = true; break;
                }
            }
        }
        if (!isCommunity) return;
        long value = 0; ItemStack[] contents = top.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack it = contents[i]; if (it == null || it.getType().isAir()) continue;
            value += communityUnitValue(it.getType()) * it.getAmount(); contents[i] = null;
        }
        if (value <= 0) return;
        top.setContents(contents); addCommunityCoins(value, p); spawnHubHolo();
        refreshAllBayHolos();
    }

    private long communityUnitValue(Material mat) {
        return switch (mat) {
            case ROTTEN_FLESH, BONE, STRING, GUNPOWDER, SPIDER_EYE, ARROW, SAND -> 2L;
            case ENDER_PEARL, BLAZE_ROD, GHAST_TEAR, MAGMA_CREAM, SLIME_BALL, PRISMARINE_SHARD -> 25L;
            case BEEF, PORKCHOP, CHICKEN, MUTTON, LEATHER, WHITE_WOOL, RABBIT,
                 COOKED_BEEF, COOKED_PORKCHOP, COOKED_CHICKEN, COOKED_MUTTON, COOKED_RABBIT -> 5L;
            case IRON_INGOT, GOLD_INGOT, COPPER_INGOT, COAL -> 50L;
            case DIAMOND, EMERALD -> 200L;
            case NETHERITE_INGOT -> 2000L;
            case SPAWNER -> 5000L;
            default -> 1L;
        };
    }

    public boolean isFarmKill(Player p) {
        Session s = sessions.get(p.getUniqueId());
        return s != null && s.active && s.endsAtMs >= System.currentTimeMillis();
    }
    public double farmXpScale() { return getConfig().getDouble("profession-xp-scale", 0.30); }

    public void addCommunityCoins(long amount, Player who) {
        if (amount <= 0) return;
        communityCoins += amount;
        while (communityCoins >= communityTarget) {
            communityCoins -= communityTarget; communityStack++;
            communityTarget = Math.max(communityTarget * 2, communityTarget + 1);
            Bukkit.broadcastMessage(ChatColor.GOLD + "MobFarm community goal! Base stack now "
                    + ChatColor.YELLOW + "x" + communityStack + ChatColor.GOLD + ". Next: " + communityTarget);
            spawnHubHolo();
            refreshAllBayHolos();
        }
        saveData();
        if (who != null) who.sendMessage(ChatColor.GREEN + "Community +" + amount
                + " (" + communityCoins + "/" + communityTarget + ")");
    }
}
