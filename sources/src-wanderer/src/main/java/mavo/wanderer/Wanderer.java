package mavo.wanderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.entity.TraderLlama;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.MerchantRecipe;
import org.bukkit.plugin.java.JavaPlugin;

public final class Wanderer extends JavaPlugin implements Listener {

    private final Random rng = new Random();
    private long nextSpawnAt = 0;
    private int spawnTask = -1;

    // entry: material, amount, emerald cost (scaled by usefulness/rarity)
    private record Offer(Material mat, int amount, int emeralds) {}

    private final List<Offer> pool = new ArrayList<>();

    @Override
    public void onEnable() {
        getConfig().addDefault("spawn-minutes", 30);
        getConfig().addDefault("spawn-max-minutes", 60);
        getConfig().addDefault("despawn-minutes", 10);
        getConfig().options().copyDefaults(true);
        saveConfig();
        buildPool();
        getServer().getPluginManager().registerEvents(this, this);
        scheduleSpawn();
        getLogger().info("MAVOWanderer enabled - traders now sell " + pool.size() + " useful offers, zero junk.");
    }

    @Override
    public void onDisable() {
        if (spawnTask != -1) { Bukkit.getScheduler().cancelTask(spawnTask); spawnTask = -1; }
    }

    private void scheduleSpawn() {
        if (spawnTask != -1) { Bukkit.getScheduler().cancelTask(spawnTask); spawnTask = -1; }
        int min = getConfig().getInt("spawn-minutes", 30);
        int max = Math.max(min + 1, getConfig().getInt("spawn-max-minutes", 60));
        int wait = min + rng.nextInt(max - min);
        nextSpawnAt = System.currentTimeMillis() + wait * 60000L;
        spawnTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            spawnNearRandom(true);
            scheduleSpawn();
        }, wait * 60L * 20L).getTaskId();
    }

    /** Spawn a trader near a random online survival player. Returns true if spawned. */
    private boolean spawnNearRandom(boolean announce) {
        var online = Bukkit.getOnlinePlayers().stream()
                .filter(p -> p.getGameMode() == org.bukkit.GameMode.SURVIVAL).toList();
        if (online.isEmpty()) return false;
        Player target = online.get(rng.nextInt(online.size()));
        Location loc = findSpot(target.getWorld(), target.getLocation());
        if (loc == null) return false;
        spawnTrader(loc, announce, target.getName());
        return true;
    }

    private Location findSpot(World w, Location center) {
        for (int tries = 0; tries < 12; tries++) {
            double ang = rng.nextDouble() * Math.PI * 2;
            int d = 10 + rng.nextInt(10);
            int x = center.getBlockX() + (int) (Math.cos(ang) * d);
            int z = center.getBlockZ() + (int) (Math.sin(ang) * d);
            Location loc = new Location(w, x + 0.5, 0, z + 0.5);
            int y = w.getHighestBlockYAt(x, z);
            loc.setY(y + 1);
            if (loc.getBlock().getType().isAir() && loc.getBlock().getRelative(0, -1, 0).getType().isSolid())
                return loc;
        }
        return null;
    }

    private void spawnTrader(Location loc, boolean announce, String nearName) {
        WanderingTrader wt = (WanderingTrader) loc.getWorld().spawnEntity(loc, org.bukkit.entity.EntityType.WANDERING_TRADER);
        wt.setDespawnDelay(getConfig().getInt("despawn-minutes", 10) * 60 * 20);
        // two trader llamas, leashed look
        for (int i = 0; i < 2; i++) {
            Location l = loc.clone().add(i == 0 ? 1 : -1, 0, 0);
            TraderLlama llama = (TraderLlama) loc.getWorld().spawnEntity(l, org.bukkit.entity.EntityType.TRADER_LLAMA);
            llama.setLeashHolder(wt);
        }
        if (announce)
            Bukkit.broadcastMessage(ChatColor.GREEN + "\uD83D\uDD14 A Wandering Trader " + ChatColor.GRAY
                    + "appeared near " + ChatColor.WHITE + nearName + ChatColor.GRAY
                    + " - useful stock only, 10 min!");
        for (Player p : Bukkit.getOnlinePlayers())
            if (p.getWorld().equals(loc.getWorld()) && p.getLocation().distanceSquared(loc) < 2500)
                p.playSound(p.getLocation(), Sound.BLOCK_BELL_USE, 0.7f, 1.4f);
    }

    private void add(Material m, int amount, int emeralds) {
        if (m != null) pool.add(new Offer(m, amount, emeralds));
    }

    private void buildPool() {
        // --- utility & redstone (the good stuff) ---
        add(Material.WATER_BUCKET, 1, 3);
        add(Material.LAVA_BUCKET, 1, 6);
        add(Material.MILK_BUCKET, 1, 4);
        add(Material.HOPPER, 1, 10);
        add(Material.HOPPER, 4, 36);
        add(Material.OBSERVER, 2, 8);
        add(Material.PISTON, 4, 10);
        add(Material.STICKY_PISTON, 2, 10);
        add(Material.REDSTONE_BLOCK, 4, 8);
        add(Material.RAIL, 32, 10);
        add(Material.POWERED_RAIL, 8, 12);
        add(Material.CHEST_MINECART, 1, 5);
        add(Material.SHULKER_SHELL, 1, 24);
        add(Material.ENDER_CHEST, 1, 16);
        add(Material.ANVIL, 1, 14);
        add(Material.SCAFFOLDING, 32, 6);
        add(Material.LEAD, 2, 4);
        add(Material.NAME_TAG, 1, 8);
        add(Material.SADDLE, 1, 10);
        add(Material.CAMPFIRE, 2, 4);
        add(Material.LANTERN, 8, 6);
        add(Material.SOUL_LANTERN, 8, 8);
        add(Material.BELL, 1, 12);
        // --- building blocks (bulk, useful) ---
        add(Material.GLASS, 32, 6);
        add(Material.QUARTZ_BLOCK, 16, 12);
        add(Material.PRISMARINE, 16, 10);
        add(Material.DARK_PRISMARINE, 16, 12);
        add(Material.SEA_LANTERN, 8, 12);
        add(Material.GLOWSTONE, 16, 10);
        add(Material.OBSIDIAN, 8, 10);
        add(Material.CRYING_OBSIDIAN, 4, 10);
        add(Material.TERRACOTTA, 32, 8);
        add(Material.SMOOTH_STONE, 32, 6);
        add(Material.DEEPSLATE_BRICKS, 32, 8);
        add(Material.MUD_BRICKS, 32, 6);
        add(Material.SANDSTONE, 32, 5);
        add(Material.RED_SANDSTONE, 32, 6);
        add(Material.BONE_BLOCK, 16, 10);
        add(Material.COPPER_BLOCK, 8, 8);
        add(Material.MOSS_BLOCK, 16, 6);
        add(Material.CALCITE, 16, 6);
        add(Material.TUFF, 32, 4);
        add(Material.AMETHYST_BLOCK, 8, 10);
        // --- nature & farming ---
        add(Material.SPORE_BLOSSOM, 1, 8);
        add(Material.BIG_DRIPLEAF, 4, 4);
        add(Material.GLOW_BERRIES, 8, 4);
        add(Material.SWEET_BERRIES, 16, 3);
        add(Material.HONEY_BOTTLE, 4, 5);
        add(Material.HONEYCOMB, 8, 6);
        add(Material.MYCELIUM, 8, 8);
        add(Material.PODZOL, 16, 6);
        add(Material.MANGROVE_PROPAGULE, 4, 4);
        add(Material.CHERRY_SAPLING, 2, 6);
        add(Material.BAMBOO, 16, 3);
        add(Material.CACTUS, 8, 3);
        add(Material.SUGAR_CANE, 16, 4);
        add(Material.PUMPKIN_SEEDS, 8, 3);
        add(Material.MELON_SEEDS, 8, 3);
        add(Material.KELP, 16, 3);
        add(Material.SEAGRASS, 16, 3);
        add(Material.LILY_PAD, 8, 4);
        // --- brewing & misc useful ---
        add(Material.BLAZE_ROD, 2, 12);
        add(Material.NETHER_WART, 8, 10);
        add(Material.GHAST_TEAR, 1, 12);
        add(Material.MAGMA_CREAM, 4, 8);
        add(Material.SLIME_BALL, 4, 8);
        add(Material.INK_SAC, 8, 4);
        add(Material.GLOW_INK_SAC, 4, 6);
        add(Material.TURTLE_SCUTE, 1, 14);
        add(Material.ARMADILLO_SCUTE, 2, 10);
        add(Material.ECHO_SHARD, 1, 20);
        add(Material.HEART_OF_THE_SEA, 1, 32);
        add(Material.NAUTILUS_SHELL, 1, 12);
        add(Material.SPONGE, 2, 14);
        add(Material.BOOKSHELF, 8, 10);
        add(Material.BOOK, 16, 8);
        add(Material.EXPERIENCE_BOTTLE, 8, 14);
        add(Material.END_ROD, 8, 8);
        add(Material.CHORUS_FRUIT, 8, 8);
        add(Material.SHROOMLIGHT, 8, 8);
        add(Material.OCHRE_FROGLIGHT, 8, 10);
        add(Material.VERDANT_FROGLIGHT, 8, 10);
        add(Material.PEARLESCENT_FROGLIGHT, 8, 10);
    }

    @EventHandler(ignoreCancelled = true)
    public void onSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof WanderingTrader wt)) return;
        List<Offer> picks = new ArrayList<>(pool);
        java.util.Collections.shuffle(picks, rng);
        List<MerchantRecipe> recipes = new ArrayList<>();
        int offers = 7 + rng.nextInt(3); // 7-9 offers per trader
        for (int i = 0; i < Math.min(offers, picks.size()); i++) {
            Offer o = picks.get(i);
            MerchantRecipe r = new MerchantRecipe(new ItemStack(o.mat(), o.amount()), 0, 3 + rng.nextInt(4), false);
            r.addIngredient(new ItemStack(Material.EMERALD, Math.min(64, o.emeralds())));
            recipes.add(r);
        }
        wt.setRecipes(recipes);
    }

    // ---------------- command ----------------
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("spawn")) {
            if (!s.hasPermission("mavowanderer.admin")) { s.sendMessage(ChatColor.RED + "No permission."); return true; }
            Player target = null;
            if (a.length > 1) {
                target = Bukkit.getPlayerExact(a[1]);
                if (target == null) { s.sendMessage(ChatColor.RED + "Player not found."); return true; }
            } else if (s instanceof Player pl) target = pl;
            if (target == null) { s.sendMessage("Players only."); return true; }
            Location loc = findSpot(target.getWorld(), target.getLocation());
            if (loc == null) { s.sendMessage(ChatColor.RED + "No safe spot found near " + target.getName() + "."); return true; }
            spawnTrader(loc, true, target.getName());
            s.sendMessage(ChatColor.GREEN + "Trader spawned.");
            return true;
        }
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        long left = Math.max(0, (nextSpawnAt - System.currentTimeMillis()) / 60000L);
        p.sendMessage(ChatColor.GREEN + "\uD83D\uDD14 Next Wandering Trader in ~" + ChatColor.YELLOW + left
                + ChatColor.GREEN + " min" + ChatColor.GRAY + " (stock is always useful!).");
        p.sendMessage(ChatColor.GRAY + "It spawns near a random online player - keep playing & listen for the bell!");
        return true;
    }
}
