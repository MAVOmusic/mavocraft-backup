package mavo.shopnpc;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.Villager;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityCombustEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityPortalEvent;
import org.bukkit.event.entity.EntityTargetEvent;
import org.bukkit.event.entity.EntityTransformEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class ShopNPC extends JavaPlugin implements Listener, TabCompleter {

    private NamespacedKey npcKey;
    private NamespacedKey holoKey;
    private final Map<UUID, Long> clickCooldown = new HashMap<>();

    /** Built-in holo text for known plaza NPCs (used when config has no holo-text yet). */
    private static final Map<String, String> DEFAULT_HOLOS = Map.ofEntries(
            Map.entry("Tutorial_Guide", "&a&l\u270E TUTORIAL|&fNew here? Right-click me!"),
            Map.entry("Lucky_Louie", "&c&l\u2663 LOUIS \u2660|&f10 games - coins & lucky coins"),
            Map.entry("The_Curator", "&d&l\u2726 MUSEUM \u2726|&fBring me one of everything"),
            Map.entry("Update_Crier", "&b&l\u2605 UPDATES|&fSee what's new"),
            Map.entry("Achievement_Keeper", "&6&l\u2655 ACHIEVEMENTS|&fLifetime goals & coins"),
            Map.entry("Profession_Master", "&a&l\u2692 PROFESSIONS|&fJobs, tools & mastery")
    );

    @Override
    public void onEnable() {
        npcKey = new NamespacedKey(this, "shopnpc");
        holoKey = new NamespacedKey(this, "shopnpcholo");
        saveDefaultConfigIfMissing();
        getServer().getPluginManager().registerEvents(this, this);
        if (getCommand("shopnpc") != null) getCommand("shopnpc").setTabCompleter(this);
        Bukkit.getScheduler().runTaskLater(this, this::reapplyAll, 60L);
        // restore floating signs after chunk load (survives /holoreset + restarts)
        Bukkit.getScheduler().runTaskLater(this, this::restoreAllHolos, 100L);
        getLogger().info("MAVOShopNPC enabled.");
    }

    private void saveDefaultConfigIfMissing() {
        if (!new java.io.File(getDataFolder(), "config.yml").exists()) {
            getConfig().set("npcs", new HashMap<String, Object>());
            saveConfig();
        }
    }

    private void reapplyAll() {
        ConfigurationSection s = getConfig().getConfigurationSection("npcs");
        if (s == null) return;
        int found = 0;
        for (String name : s.getKeys(false)) {
            String uid = s.getString(name + ".uuid");
            if (uid == null) continue;
            try {
                Entity e = Bukkit.getEntity(UUID.fromString(uid));
                if (e instanceof Villager v) { protect(v); found++; }
            } catch (Exception ignored) {}
        }
        getLogger().info("Re-applied protection to " + found + " shop NPC(s).");
    }

    /** Respawn every NPC floating sign from config (holo-text or built-in defaults). */
    public int restoreAllHolos() {
        ConfigurationSection s = getConfig().getConfigurationSection("npcs");
        if (s == null) return 0;
        int n = 0;
        for (String name : s.getKeys(false)) {
            if (restoreHolo(name, false)) n++;
        }
        if (n > 0) getLogger().info("Restored " + n + " shop NPC holo(s).");
        return n;
    }

    /**
     * @param force if true, always rewrite even when existing tagged holo is alive
     * @return true if a holo was spawned/updated
     */
    private boolean restoreHolo(String name, boolean force) {
        String uid = getConfig().getString("npcs." + name + ".uuid");
        if (uid == null) return false;
        Entity ent;
        try { ent = Bukkit.getEntity(UUID.fromString(uid)); }
        catch (Exception e) { return false; }
        if (ent == null) {
            // try load chunk from saved coords
            String wn = getConfig().getString("npcs." + name + ".world");
            if (wn != null) {
                World w = Bukkit.getWorld(wn);
                if (w != null) {
                    double x = getConfig().getDouble("npcs." + name + ".x");
                    double y = getConfig().getDouble("npcs." + name + ".y");
                    double z = getConfig().getDouble("npcs." + name + ".z");
                    w.getChunkAt((int) Math.floor(x) >> 4, (int) Math.floor(z) >> 4).load();
                    try { ent = Bukkit.getEntity(UUID.fromString(uid)); } catch (Exception ignored) {}
                }
            }
        }
        if (ent == null) return false;

        String raw = getConfig().getString("npcs." + name + ".holo-text", null);
        // 1.3.3: replace pre-shrink texts so smaller holos actually appear on live
        boolean refresh = false;
        if (raw != null && (raw.contains("LUCKY LOUIE") || raw.contains("THE MUSEUM")
                || raw.contains("One of everything") || raw.contains("Right-click to play")
                || raw.contains("Lifetime goals") || raw.contains("What's new on MAVOcraft"))) {
            raw = null;
            getConfig().set("npcs." + name + ".holo-text", null);
            saveConfig();
            refresh = true; // force respawn so the new small holo replaces the old one
        }
        if (raw == null || raw.isBlank()) {
            raw = DEFAULT_HOLOS.get(name);
            // also try case-insensitive default keys
            if (raw == null) {
                for (var e : DEFAULT_HOLOS.entrySet())
                    if (e.getKey().equalsIgnoreCase(name)) { raw = e.getValue(); break; }
            }
        }
        // still nothing and no prior holo-uuid → skip (shopkeeper without a sign)
        if (raw == null || raw.isBlank()) {
            String old = getConfig().getString("npcs." + name + ".holo-uuid");
            if (old == null) return false;
            // had a holo but lost text — leave a generic label from villager name
            String cn = ent.getCustomName();
            raw = cn != null ? cn : name;
        }

        // if existing holo still alive and not forcing, keep it (but re-tag if needed)
        if (!force && !refresh) {
            String oldHolo = getConfig().getString("npcs." + name + ".holo-uuid");
            if (oldHolo != null) {
                try {
                    Entity oh = Bukkit.getEntity(UUID.fromString(oldHolo));
                    if (oh instanceof TextDisplay td && !td.isDead()) {
                        if (!td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE))
                            td.getPersistentDataContainer().set(holoKey, PersistentDataType.BYTE, (byte) 1);
                        // persist text if missing
                        if (getConfig().getString("npcs." + name + ".holo-text") == null) {
                            getConfig().set("npcs." + name + ".holo-text", raw);
                            saveConfig();
                        }
                        return false;
                    }
                } catch (Exception ignored) {}
            }
        }

        spawnHoloAbove(name, ent, raw);
        return true;
    }

    private void spawnHoloAbove(String name, Entity ent, String raw) {
        // remove old recorded + nearby tagged orphans
        String oldHolo = getConfig().getString("npcs." + name + ".holo-uuid");
        if (oldHolo != null) {
            try {
                Entity oh = Bukkit.getEntity(UUID.fromString(oldHolo));
                if (oh != null) oh.remove();
            } catch (Exception ignored) {}
        }
        Location hl = ent.getLocation().clone().add(0, 2.65, 0);
        hl.getChunk().load();
        for (Entity e : ent.getWorld().getNearbyEntities(hl, 1.5, 2.0, 1.5)) {
            if (e instanceof TextDisplay td
                    && td.getPersistentDataContainer().has(holoKey, PersistentDataType.BYTE))
                td.remove();
        }
        String text = ChatColor.translateAlternateColorCodes('&', raw.replace("|", "\n"));
        TextDisplay td = ent.getWorld().spawn(hl, TextDisplay.class, d -> {
            d.setText(text);
            d.setBillboard(Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(true);
            try {
                d.setDefaultBackground(false);
                d.setBackgroundColor(Color.fromARGB(190, 10, 10, 20));
            } catch (Throwable ignored) {}
            d.setAlignment(TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(140);
            var tr = d.getTransformation();
            tr.getScale().set(0.55f);
            d.setTransformation(tr);
            d.setViewRange(0.8f);
            try { d.setBrightness(new Display.Brightness(15, 15)); } catch (Throwable ignored) {}
            d.setPersistent(true);
            d.getPersistentDataContainer().set(holoKey, PersistentDataType.BYTE, (byte) 1);
        });
        getConfig().set("npcs." + name + ".holo-uuid", td.getUniqueId().toString());
        getConfig().set("npcs." + name + ".holo-text", raw);
        saveConfig();
    }

    private void protect(Villager v) {
        v.setAI(false);
        v.setInvulnerable(true);
        v.setSilent(true);
        v.setPersistent(true);
        v.setRemoveWhenFarAway(false);
        v.setCollidable(false);
        v.setCanPickupItems(false);
        v.setBreed(false);
        v.setAgeLock(true);
        v.setAdult();
        v.setCustomNameVisible(true);
    }

    private boolean isShopNpc(Entity e) {
        return e != null && e.getPersistentDataContainer().has(npcKey, PersistentDataType.STRING);
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onInteract(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (!isShopNpc(e.getRightClicked())) return;
        e.setCancelled(true);
        Player pl = e.getPlayer();
        long now = System.currentTimeMillis();
        Long last = clickCooldown.get(pl.getUniqueId());
        if (last != null && now - last < 600) return;
        clickCooldown.put(pl.getUniqueId(), now);
        String cmd = e.getRightClicked().getPersistentDataContainer().get(npcKey, PersistentDataType.STRING);
        if (cmd == null || cmd.isEmpty()) cmd = "shop";
        while (cmd.startsWith("/")) cmd = cmd.substring(1);
        pl.playSound(pl.getLocation(), Sound.ENTITY_VILLAGER_TRADE, 1f, 1f);
        pl.performCommand(cmd);
    }

    @EventHandler
    public void onDamage(EntityDamageEvent e) {
        if (isShopNpc(e.getEntity())) e.setCancelled(true);
    }
    @EventHandler
    public void onCombust(EntityCombustEvent e) {
        if (isShopNpc(e.getEntity())) e.setCancelled(true);
    }
    @EventHandler
    public void onTransform(EntityTransformEvent e) {
        if (isShopNpc(e.getEntity())) e.setCancelled(true);
    }
    @EventHandler
    public void onPortal(EntityPortalEvent e) {
        if (isShopNpc(e.getEntity())) e.setCancelled(true);
    }
    @EventHandler
    public void onTarget(EntityTargetEvent e) {
        if (isShopNpc(e.getEntity()) || isShopNpc(e.getTarget())) e.setCancelled(true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player pl)) {
            // console: allow resholo / list
            if (args.length >= 1 && (args[0].equalsIgnoreCase("resholo") || args[0].equalsIgnoreCase("holoreset"))) {
                int n = restoreAllHolos();
                sender.sendMessage("Restored " + n + " shop NPC holo(s).");
                return true;
            }
            sender.sendMessage("In-game only for most shopnpc commands. Console: /shopnpc resholo");
            return true;
        }
        if (!pl.hasPermission("mavoshopnpc.admin")) {
            pl.sendMessage(ChatColor.RED + "No permission.");
            return true;
        }
        if (args.length == 0) {
            pl.sendMessage(ChatColor.RED + "/shopnpc spawn <name> [command]  " + ChatColor.GRAY + "(spawns at your feet)");
            pl.sendMessage(ChatColor.GRAY + "/shopnpc remove|setcmd|list|holo|holoremove|adopt|resholo");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("resholo") || sub.equals("holoreset") || sub.equals("restoreholo")) {
            int n = 0;
            ConfigurationSection sec = getConfig().getConfigurationSection("npcs");
            if (sec != null) for (String name : sec.getKeys(false))
                if (restoreHolo(name, true)) n++;
            pl.sendMessage(ChatColor.GREEN + "\u2714 Restored " + n + " shop NPC floating sign(s).");
            pl.playSound(pl.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.6f, 1.3f);
            return true;
        }

        if (sub.equals("spawn")) {
            if (args.length < 2) { pl.sendMessage(ChatColor.RED + "Name it! /shopnpc spawn <name> [command]"); return true; }
            String name = args[1];
            String cmd = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "shop";
            while (cmd.startsWith("/")) cmd = cmd.substring(1);
            Location loc = pl.getLocation();
            Villager v = pl.getWorld().spawn(loc, Villager.class);
            v.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, cmd);
            v.setCustomName(ChatColor.AQUA + name.replace('_', ' '));
            protect(v);
            getConfig().set("npcs." + name + ".uuid", v.getUniqueId().toString());
            getConfig().set("npcs." + name + ".command", cmd);
            getConfig().set("npcs." + name + ".world", loc.getWorld().getName());
            getConfig().set("npcs." + name + ".x", loc.getX());
            getConfig().set("npcs." + name + ".y", loc.getY());
            getConfig().set("npcs." + name + ".z", loc.getZ());
            saveConfig();
            // auto holo if we have a default
            if (DEFAULT_HOLOS.containsKey(name) || DEFAULT_HOLOS.keySet().stream().anyMatch(k -> k.equalsIgnoreCase(name)))
                restoreHolo(name, true);
            pl.sendMessage(ChatColor.GREEN + "" + ChatColor.AQUA + name + ChatColor.GREEN
                    + " spawned. Right-click runs: " + ChatColor.YELLOW + "/" + cmd);
            return true;
        }

        if (sub.equals("remove")) {
            if (args.length < 2) { pl.sendMessage(ChatColor.RED + "/shopnpc remove <name>"); return true; }
            String name = args[1];
            String uid = getConfig().getString("npcs." + name + ".uuid");
            if (uid == null) { pl.sendMessage(ChatColor.RED + "No NPC named " + name); return true; }
            try {
                Entity e = Bukkit.getEntity(UUID.fromString(uid));
                if (e != null) e.remove();
            } catch (Exception ignored) {}
            String oldHolo = getConfig().getString("npcs." + name + ".holo-uuid");
            if (oldHolo != null) {
                try { Entity oh = Bukkit.getEntity(UUID.fromString(oldHolo)); if (oh != null) oh.remove(); } catch (Exception ignored) {}
            }
            getConfig().set("npcs." + name, null);
            saveConfig();
            pl.sendMessage(ChatColor.GREEN + "Removed " + name);
            return true;
        }

        if (sub.equals("setcmd")) {
            if (args.length < 3) { pl.sendMessage(ChatColor.RED + "/shopnpc setcmd <name> <command>"); return true; }
            String name = args[1];
            String uid = getConfig().getString("npcs." + name + ".uuid");
            if (uid == null) { pl.sendMessage(ChatColor.RED + "No NPC named " + name); return true; }
            String cmd = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            while (cmd.startsWith("/")) cmd = cmd.substring(1);
            try {
                Entity e = Bukkit.getEntity(UUID.fromString(uid));
                if (e != null) e.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, cmd);
            } catch (Exception ignored) {}
            getConfig().set("npcs." + name + ".command", cmd);
            saveConfig();
            pl.sendMessage(ChatColor.GREEN + name + " now runs /" + cmd);
            return true;
        }

        if (sub.equals("list")) {
            ConfigurationSection s = getConfig().getConfigurationSection("npcs");
            if (s == null || s.getKeys(false).isEmpty()) {
                pl.sendMessage(ChatColor.GRAY + "No shop NPCs.");
                return true;
            }
            pl.sendMessage(ChatColor.GOLD + "== Shop NPCs ==");
            for (String name : s.getKeys(false)) {
                String cmd = s.getString(name + ".command", "?");
                boolean holo = s.getString(name + ".holo-uuid") != null || s.getString(name + ".holo-text") != null
                        || DEFAULT_HOLOS.containsKey(name);
                pl.sendMessage(ChatColor.AQUA + name + ChatColor.GRAY + " → /" + cmd
                        + (holo ? ChatColor.GREEN + " [holo]" : ""));
            }
            return true;
        }

        if (sub.equals("adopt")) {
            if (args.length < 2) { pl.sendMessage(ChatColor.RED + "/shopnpc adopt <name> [command]"); return true; }
            String name = args[1];
            String cmd = args.length >= 3 ? String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length)) : "shop";
            while (cmd.startsWith("/")) cmd = cmd.substring(1);
            Villager nearest = null;
            double best = 8;
            for (Entity e : pl.getNearbyEntities(8, 8, 8)) {
                if (!(e instanceof Villager v)) continue;
                double d = e.getLocation().distanceSquared(pl.getLocation());
                if (d < best * best) { best = Math.sqrt(d); nearest = v; }
            }
            if (nearest == null) { pl.sendMessage(ChatColor.RED + "No villager within 8 blocks."); return true; }
            nearest.getPersistentDataContainer().set(npcKey, PersistentDataType.STRING, cmd);
            nearest.setCustomName(ChatColor.AQUA + name.replace('_', ' '));
            protect(nearest);
            Location loc = nearest.getLocation();
            getConfig().set("npcs." + name + ".uuid", nearest.getUniqueId().toString());
            getConfig().set("npcs." + name + ".command", cmd);
            getConfig().set("npcs." + name + ".world", loc.getWorld().getName());
            getConfig().set("npcs." + name + ".x", loc.getX());
            getConfig().set("npcs." + name + ".y", loc.getY());
            getConfig().set("npcs." + name + ".z", loc.getZ());
            saveConfig();
            pl.sendMessage(ChatColor.GREEN + "Adopted nearest NPC as " + ChatColor.AQUA + name + ChatColor.GREEN
                    + ". Right-click runs: " + ChatColor.YELLOW + "/" + cmd);
            return true;
        }

        if (sub.equals("holo")) {
            if (args.length < 3) {
                pl.sendMessage(ChatColor.RED + "/shopnpc holo <name> <text - use | for a new line>");
                pl.sendMessage(ChatColor.GRAY + "Example: /shopnpc holo Tutorial_Guide &a&l\u270E TUTORIAL|&fNew here? Right-click me!");
                return true;
            }
            String name = args[1];
            String uid = getConfig().getString("npcs." + name + ".uuid");
            if (uid == null) { pl.sendMessage(ChatColor.RED + "No NPC named " + name); return true; }
            Entity ent = Bukkit.getEntity(UUID.fromString(uid));
            if (ent == null) { pl.sendMessage(ChatColor.RED + "NPC entity not loaded - go near it and retry."); return true; }
            String raw = String.join(" ", java.util.Arrays.copyOfRange(args, 2, args.length));
            spawnHoloAbove(name, ent, raw);
            pl.sendMessage(ChatColor.GREEN + "Floating sign set above " + ChatColor.AQUA + name);
            return true;
        }

        if (sub.equals("holoremove")) {
            if (args.length < 2) { pl.sendMessage(ChatColor.RED + "/shopnpc holoremove <name>"); return true; }
            String name = args[1];
            String oldHolo = getConfig().getString("npcs." + name + ".holo-uuid");
            if (oldHolo == null) { pl.sendMessage(ChatColor.RED + "No floating sign recorded for " + name); return true; }
            try { Entity oh = Bukkit.getEntity(UUID.fromString(oldHolo)); if (oh != null) oh.remove(); } catch (Exception ignored) {}
            getConfig().set("npcs." + name + ".holo-uuid", null);
            getConfig().set("npcs." + name + ".holo-text", null);
            saveConfig();
            pl.sendMessage(ChatColor.GREEN + "Floating sign removed from " + name);
            return true;
        }

        pl.sendMessage(ChatColor.RED + "Unknown subcommand.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String s : List.of("spawn", "remove", "setcmd", "list", "holo", "holoremove", "adopt", "resholo"))
                if (s.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(s);
        } else if (args.length == 2 && (args[0].equalsIgnoreCase("remove") || args[0].equalsIgnoreCase("setcmd")
                || args[0].equalsIgnoreCase("holo") || args[0].equalsIgnoreCase("holoremove"))) {
            ConfigurationSection s = getConfig().getConfigurationSection("npcs");
            if (s != null) for (String n : s.getKeys(false))
                if (n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(n);
        }
        return out;
    }
}
