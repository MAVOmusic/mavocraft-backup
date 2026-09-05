package mavo.events;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;

public final class Events extends JavaPlugin implements Listener {

    private Economy econ;
    private final Random rng = new Random();
    private String active = null;
    private int taskId = -1, rainTaskId = -1, siegeTaskId = -1, giftTaskId = -1;
    private long endsAt = 0;
    private double savedLuckyChance = -1;
    private NamespacedKey placedKey; // MAVOLuckyCoins' player-placed guard

    private static final String[] EVENTS = {"luckyhour", "coinrain", "mobhunt", "fishingfrenzy", "minersrush",
            "harvestbonus", "buildbonus", "zombiesiege", "giftdrop", "farmfrenzy"};

    private static final Material[] GIFT_POOL = {
            Material.BREAD, Material.COOKED_BEEF, Material.BAKED_POTATO, Material.IRON_INGOT,
            Material.GOLD_NUGGET, Material.LAPIS_LAZULI, Material.EMERALD
    };

    @Override
    public void onEnable() {
        getConfig().addDefault("auto-events", true);
        getConfig().addDefault("min-minutes-between", 45);
        getConfig().addDefault("max-minutes-between", 90);
        getConfig().addDefault("event-minutes", 15);
        getConfig().options().copyDefaults(true);
        saveConfig();
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        try { placedKey = new NamespacedKey("mavoluckycoins", "lcplaced"); } catch (Exception ignored) {}
        getServer().getPluginManager().registerEvents(this, this);
        scheduleNext();
        getLogger().info("MAVOEvents enabled - chaos scheduled.");
    }

    @Override
    public void onDisable() { stopEvent(false); }

    // ---------------- scheduling ----------------
    private World eventWorld() {
        World w = Bukkit.getWorld("world");
        return w != null ? w : (Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0));
    }

    /** Zombie Siege night window: 18:30-06:00 (ticks 12500 -> 6000), same as the sleep vote. */
    private boolean isNightNow() {
        World w = eventWorld();
        if (w == null) return true; // no world clock = don't block the event
        long t = w.getTime() % 24000L;
        return t >= 12500L || t < 6000L;
    }

    private String pickEvent() {
        String ev = EVENTS[rng.nextInt(EVENTS.length)];
        if (ev.equals("zombiesiege") && !isNightNow()) {
            // daytime: never auto-start the siege before sunset (18:30) - roll another
            String alt;
            do { alt = EVENTS[rng.nextInt(EVENTS.length)]; } while (alt.equals("zombiesiege"));
            ev = alt;
        }
        return ev;
    }

    private void scheduleNext() {
        if (!getConfig().getBoolean("auto-events", true)) return;
        int min = getConfig().getInt("min-minutes-between", 45);
        int max = Math.max(min + 1, getConfig().getInt("max-minutes-between", 90));
        long delayTicks = (min + rng.nextInt(max - min)) * 60L * 20L;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (active == null && Bukkit.getOnlinePlayers().size() >= 1)
                startEvent(pickEvent());
            scheduleNext();
        }, delayTicks);
    }

    private void pay(Player p, double amt) {
        if (p.getGameMode() != org.bukkit.GameMode.SURVIVAL) return; // creative never earns
        if (econ != null) econ.depositPlayer(p, amt);
    }

    private boolean playerPlaced(org.bukkit.block.Block b) {
        if (placedKey == null) return false;
        long k = (((long) (b.getY() + 512)) << 8) | ((b.getX() & 15) << 4) | (b.getZ() & 15);
        long[] arr = b.getChunk().getPersistentDataContainer().get(placedKey, PersistentDataType.LONG_ARRAY);
        if (arr != null) for (long v : arr) if (v == k) return true;
        return false;
    }

    private boolean isHarvest(Material m) {
        return switch (m) {
            case WHEAT, CARROTS, POTATOES, BEETROOTS, NETHER_WART, SWEET_BERRY_BUSH, COCOA,
                 MELON, PUMPKIN, CHORUS_FLOWER, PITCHER_PLANT, TORCHFLOWER_CROP, PITCHER_CROP -> true;
            default -> false;
        };
    }

    private String pretty(String ev) {
        return switch (ev) {
            case "luckyhour" -> ChatColor.GOLD + "" + ChatColor.BOLD + "\u26C0 LUCKY HOUR";
            case "coinrain" -> ChatColor.YELLOW + "" + ChatColor.BOLD + "\u26C3 COIN RAIN";
            case "mobhunt" -> ChatColor.RED + "" + ChatColor.BOLD + "\u2694 MOB HUNT";
            case "fishingfrenzy" -> ChatColor.BLUE + "" + ChatColor.BOLD + "\uD83C\uDFA3 FISHING FRENZY";
            case "minersrush" -> ChatColor.AQUA + "" + ChatColor.BOLD + "\u26CF MINER'S RUSH";
            case "harvestbonus" -> ChatColor.DARK_GREEN + "" + ChatColor.BOLD + "\u2E3A HARVEST BONUS";
            case "buildbonus" -> ChatColor.DARK_BLUE + "" + ChatColor.BOLD + "\u2E3B BUILDATHON";
            case "zombiesiege" -> ChatColor.DARK_RED + "" + ChatColor.BOLD + "\u2620 ZOMBIE SIEGE";
            case "giftdrop" -> ChatColor.GOLD + "" + ChatColor.BOLD + "\uD83C\uDF81 GIFT DROP";
            case "farmfrenzy" -> ChatColor.DARK_AQUA + "" + ChatColor.BOLD + "\u2691 FARM FRENZY";
            default -> ev;
        };
    }

    private String describe(String ev) {
        return switch (ev) {
            case "luckyhour" -> "Lucky Coin drop chance is 5x for the next %d minutes!";
            case "coinrain" -> "Free coins rain on random online players for %d minutes!";
            case "mobhunt" -> "Hostile mob kills pay bonus coins for %d minutes!";
            case "fishingfrenzy" -> "Every catch pays bonus coins for %d minutes!";
            case "minersrush" -> "Mining ores pays bonus coins for %d minutes!";
            case "harvestbonus" -> "Every crop harvested pays bonus coins for %d minutes!";
            case "buildbonus" -> "Every block placed pays bonus coins for %d minutes!";
            case "zombiesiege" -> "Zombie waves hunt survivors - kills pay bonus for %d minutes!";
            case "giftdrop" -> "Random gift items fall from the sky for %d minutes!";
            case "farmfrenzy" -> "Animal kills pay bonus coins for %d minutes!";
            default -> "Event running for %d minutes!";
        };
    }

    private void startEvent(String ev) {
        stopEvent(false);
        active = ev;
        int mins = getConfig().getInt("event-minutes", 15);
        endsAt = System.currentTimeMillis() + mins * 60000L;
        Bukkit.broadcastMessage("");
        Bukkit.broadcastMessage(pretty(ev) + ChatColor.RESET + " " + ChatColor.GRAY + String.format(describe(ev), mins));
        Bukkit.broadcastMessage("");
        for (Player p : Bukkit.getOnlinePlayers())
            p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.4f, 1.6f);

        if (ev.equals("luckyhour")) {
            Plugin lucky = Bukkit.getPluginManager().getPlugin("MAVOLuckyCoins");
            if (lucky != null) {
                savedLuckyChance = lucky.getConfig().getDouble("drop-chance", 0.001);
                lucky.getConfig().set("drop-chance", Math.min(0.25, savedLuckyChance * 5));
            }
        }
        if (ev.equals("coinrain")) {
            rainTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
                var online = Bukkit.getOnlinePlayers().stream()
                        .filter(pl -> pl.getGameMode() == org.bukkit.GameMode.SURVIVAL).toList();
                if (online.isEmpty()) return;
                Player p = online.get(rng.nextInt(online.size()));
                int amt = 5 + rng.nextInt(16);
                pay(p, amt);
                p.sendMessage(ChatColor.YELLOW + "\u26C3 +" + amt + ChatColor.GRAY + " coin rain!");
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.6f, 2f);
            }, 100L, 600L).getTaskId(); // every 30s
        }
        if (ev.equals("zombiesiege")) {
            siegeTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
                // sunrise check: the siege ends at 06:00 (tick 6000) - no daylight zombies
                if (!isNightNow()) {
                    stopEvent(false);
                    Bukkit.broadcastMessage(pretty("zombiesiege") + ChatColor.RESET + " "
                            + ChatColor.GRAY + "has ended - the sun rose (06:00).");
                    return;
                }
                var online = Bukkit.getOnlinePlayers().stream()
                        .filter(pl -> pl.getGameMode() == org.bukkit.GameMode.SURVIVAL).toList();
                if (online.isEmpty()) return;
                Player p = online.get(rng.nextInt(online.size()));
                var w = p.getWorld();
                for (int i = 0; i < 3; i++) {
                    var loc = p.getLocation().clone().add(rng.nextInt(9) - 4, 0, rng.nextInt(9) - 4);
                    loc.setY(w.getHighestBlockYAt(loc) + 1);
                    w.spawnEntity(loc, org.bukkit.entity.EntityType.ZOMBIE);
                }
                p.sendMessage(ChatColor.DARK_RED + "\u2620 Zombies are hunting you! DEFEND!");
                p.playSound(p.getLocation(), Sound.ENTITY_ZOMBIE_AMBIENT, 0.7f, 1f);
            }, 200L, 400L).getTaskId(); // every 20s
        }
        if (ev.equals("giftdrop")) {
            giftTaskId = Bukkit.getScheduler().runTaskTimer(this, () -> {
                var online = Bukkit.getOnlinePlayers().stream()
                        .filter(pl -> pl.getGameMode() == org.bukkit.GameMode.SURVIVAL).toList();
                if (online.isEmpty()) return;
                Player p = online.get(rng.nextInt(online.size()));
                Material m = GIFT_POOL[rng.nextInt(GIFT_POOL.length)];
                int amt = 1 + rng.nextInt(Math.min(8, m.getMaxStackSize()));
                ItemStack gift = new ItemStack(m, amt);
                var left = p.getInventory().addItem(gift);
                if (!left.isEmpty()) p.getWorld().dropItemNaturally(p.getLocation(), gift);
                p.sendMessage(ChatColor.GOLD + "\uD83C\uDF81 " + ChatColor.WHITE + "Gift: " + m.name().toLowerCase(Locale.ROOT).replace('_', ' '));
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.7f, 1.6f);
            }, 300L, 800L).getTaskId(); // every 40s
        }
        taskId = Bukkit.getScheduler().runTaskLater(this, () -> stopEvent(true), mins * 60L * 20L).getTaskId();
    }

    private void stopEvent(boolean announce) {
        if (active == null) return;
        String was = active;
        active = null;
        if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
        if (rainTaskId != -1) { Bukkit.getScheduler().cancelTask(rainTaskId); rainTaskId = -1; }
        if (siegeTaskId != -1) { Bukkit.getScheduler().cancelTask(siegeTaskId); siegeTaskId = -1; }
        if (giftTaskId != -1) { Bukkit.getScheduler().cancelTask(giftTaskId); giftTaskId = -1; }
        if (was.equals("luckyhour") && savedLuckyChance >= 0) {
            Plugin lucky = Bukkit.getPluginManager().getPlugin("MAVOLuckyCoins");
            if (lucky != null) lucky.getConfig().set("drop-chance", savedLuckyChance);
            savedLuckyChance = -1;
        }
        if (announce) Bukkit.broadcastMessage(pretty(was) + ChatColor.RESET + " " + ChatColor.GRAY + "has ended. Back to the grind!");
    }

    // ---------------- event effects ----------------
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k == null || k.getGameMode() != org.bukkit.GameMode.SURVIVAL) return;
        boolean hostileEvent = "mobhunt".equals(active) || "zombiesiege".equals(active);
        if (hostileEvent && e.getEntity() instanceof Monster) {
            int amt = 2 + rng.nextInt(3);
            pay(k, amt);
            k.sendMessage(ChatColor.RED + "\u2694 +" + amt + ChatColor.GRAY + " mob hunt bonus");
        } else if ("farmfrenzy".equals(active) && !(e.getEntity() instanceof Monster) && !(e.getEntity() instanceof Player)) {
            int amt = 2 + rng.nextInt(3);
            pay(k, amt);
            k.sendMessage(ChatColor.DARK_AQUA + "\u2691 +" + amt + ChatColor.GRAY + " farm frenzy bonus");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (!"fishingfrenzy".equals(active) || e.getState() != PlayerFishEvent.State.CAUGHT_FISH) return;
        if (e.getPlayer().getGameMode() != org.bukkit.GameMode.SURVIVAL) return;
        int amt = 3 + rng.nextInt(4);
        pay(e.getPlayer(), amt);
        e.getPlayer().sendMessage(ChatColor.BLUE + "\uD83C\uDFA3 +" + amt + ChatColor.GRAY + " frenzy bonus");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMine(BlockBreakEvent e) {
        if (e.getPlayer().getGameMode() != org.bukkit.GameMode.SURVIVAL) return;
        if ("harvestbonus".equals(active) && isHarvest(e.getBlock().getType()) && !playerPlaced(e.getBlock())) {
            int amt = 1 + rng.nextInt(3);
            pay(e.getPlayer(), amt);
            e.getPlayer().sendMessage(ChatColor.DARK_GREEN + "\u2E3A +" + amt + ChatColor.GRAY + " harvest bonus");
            return;
        }
        if (!"minersrush".equals(active)) return;
        Material m = e.getBlock().getType();
        if (!m.name().endsWith("_ORE") && m != Material.ANCIENT_DEBRIS) return;
        if (playerPlaced(e.getBlock())) return;
        int amt = 1 + rng.nextInt(3);
        pay(e.getPlayer(), amt);
        e.getPlayer().sendMessage(ChatColor.AQUA + "\u26CF +" + amt + ChatColor.GRAY + " miner's bonus");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        if (!"buildbonus".equals(active)) return;
        if (e.getPlayer().getGameMode() != org.bukkit.GameMode.SURVIVAL) return;
        int amt = 1 + rng.nextInt(2);
        pay(e.getPlayer(), amt);
    }

    // ---------------- command ----------------
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 0) {
            if (active == null) s.sendMessage(ChatColor.GRAY + "No event running right now. They start randomly - stay online!");
            else {
                long left = Math.max(0, (endsAt - System.currentTimeMillis()) / 60000L);
                s.sendMessage(pretty(active) + ChatColor.RESET + ChatColor.GRAY + " is LIVE - about " + left + " min left.");
            }
            return true;
        }
        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "list" -> {
                s.sendMessage(ChatColor.RED + "" + ChatColor.BOLD + "Server Events (10):");
                for (String ev : EVENTS) s.sendMessage(ChatColor.GRAY + "- " + pretty(ev));
            }
            case "start" -> {
                if (!s.hasPermission("mavoevents.admin")) { s.sendMessage(ChatColor.RED + "No permission."); return true; }
                if (a.length < 2 || !Arrays.asList(EVENTS).contains(a[1].toLowerCase(Locale.ROOT))) {
                    s.sendMessage(ChatColor.RED + "Events: " + String.join(", ", EVENTS)); return true;
                }
                if (a[1].equalsIgnoreCase("zombiesiege") && !isNightNow()) {
                    s.sendMessage(ChatColor.RED + "Zombie Siege only runs at night (18:30-06:00).");
                    return true;
                }
                startEvent(a[1].toLowerCase(Locale.ROOT));
            }
            case "stop" -> {
                if (!s.hasPermission("mavoevents.admin")) { s.sendMessage(ChatColor.RED + "No permission."); return true; }
                if (active == null) s.sendMessage(ChatColor.GRAY + "Nothing to stop.");
                else stopEvent(true);
            }
            default -> s.sendMessage(ChatColor.RED + "/event [start <name>|stop|list]");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return s.hasPermission("mavoevents.admin") ? Arrays.asList("list", "start", "stop") : List.of("list");
        if (a.length == 2 && a[0].equalsIgnoreCase("start")) return Arrays.asList(EVENTS);
        return List.of();
    }
}
