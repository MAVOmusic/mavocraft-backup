package mavo.lucky;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class LuckyCoins extends JavaPlugin implements Listener {

    private NamespacedKey coinKey, placedKey;
    private final Random rng = new Random();
    private File dataFile;
    private YamlConfiguration data;
    private boolean dirty = false;
    private boolean wellMode;
    private String wellWorld;
    private int wellX, wellY, wellZ, wellRadius;

    @Override
    public void onEnable() {
        coinKey = new NamespacedKey(this, "luckycoin");
        placedKey = new NamespacedKey(this, "lcplaced");
        dataFile = new File(getDataFolder(), "data.yml");
        if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
        data = YamlConfiguration.loadConfiguration(dataFile);
        getConfig().addDefault("drop-chance", 0.001);
        getConfig().addDefault("drop-cadence-seconds", 20);
        getConfig().addDefault("free-coin-every-days", 10);
        getConfig().addDefault("survival-only", true);
        getConfig().addDefault("wishing-well.enabled", false);
        getConfig().addDefault("wishing-well.world", "world");
        getConfig().addDefault("wishing-well.x", 0);
        getConfig().addDefault("wishing-well.y", 64);
        getConfig().addDefault("wishing-well.z", 0);
        getConfig().addDefault("wishing-well.radius", 2);
        // 1.5.5 migration: older configs shipped drop-chance 0.01 (1%) - too generous.
        // Only touch it when the new cadence key is missing (i.e. first run of this build).
        if (!getConfig().contains("drop-cadence-seconds")) {
            getConfig().set("drop-chance", 0.001);
        }
        getConfig().options().copyDefaults(true);
        saveConfig();
        loadWell();
        loadWellPool();
        getServer().getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            if (dirty) { try { data.save(dataFile); } catch (Exception ignored) {} dirty = false; }
        }, 200L, 200L);
        getLogger().info("MAVOLuckyCoins enabled - may fortune find you.");
    }

    @Override
    public void onDisable() {
        try { data.save(dataFile); } catch (Exception ignored) {}
    }

    private void loadWell() {
        wellMode = getConfig().getBoolean("wishing-well.enabled", false);
        wellWorld = getConfig().getString("wishing-well.world", "world");
        wellX = getConfig().getInt("wishing-well.x", 0);
        wellY = getConfig().getInt("wishing-well.y", 64);
        wellZ = getConfig().getInt("wishing-well.z", 0);
        wellRadius = getConfig().getInt("wishing-well.radius", 2);
    }

    private boolean survivalOnly(Player pl) {
        return getConfig().getBoolean("survival-only", true)
                && pl.getGameMode() != org.bukkit.GameMode.SURVIVAL;
    }

    // ---------------- the coin item ----------------
    private ItemStack makeCoin(int amount) {
        ItemStack it = new ItemStack(Material.SUNFLOWER, amount);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&l\u26C0 Lucky Coin"));
        m.setLore(Arrays.asList(
                ChatColor.GRAY + "Toss it into the " + ChatColor.AQUA + "Wishing Well" + ChatColor.GRAY + " at spawn",
                ChatColor.GRAY + "to trade it for... something.",
                ChatColor.DARK_GRAY + "MAVOcraft fortune token"));
        m.addEnchant(Enchantment.UNBREAKING, 1, true);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        m.getPersistentDataContainer().set(coinKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isCoin(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(coinKey, PersistentDataType.BYTE);
    }

    private void giveCoin(Player pl, String reason) {
        ItemStack coin = makeCoin(1);
        var left = pl.getInventory().addItem(coin);
        if (!left.isEmpty()) pl.getWorld().dropItemNaturally(pl.getLocation(), coin);
        pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.8f);
        pl.sendMessage(ChatColor.YELLOW + "\u26C0 " + ChatColor.GOLD + "A Lucky Coin! " + ChatColor.GRAY + reason);
    }

    // ---------------- 1% drops from grinding ----------------
    private void tryDrop(Player pl) {
        if (survivalOnly(pl)) return;
        int cadence = Math.max(1, getConfig().getInt("drop-cadence-seconds", 20));
        long last = data.getLong("drop." + pl.getUniqueId() + ".last", 0L);
        long now = System.currentTimeMillis();
        if (now - last < cadence * 1000L) return;
        if (rng.nextDouble() < getConfig().getDouble("drop-chance", 0.001)) {
            data.set("drop." + pl.getUniqueId() + ".last", now);
            giveCoin(pl, "(lucky drop!)");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        e.getBlock().getChunk().getPersistentDataContainer(); // ensure chunk pdc exists
        long k = (((long) (e.getBlock().getY() + 512)) << 8) | ((e.getBlock().getX() & 15) << 4) | (e.getBlock().getZ() & 15);
        var pdc = e.getBlock().getChunk().getPersistentDataContainer();
        long[] arr = pdc.get(placedKey, PersistentDataType.LONG_ARRAY);
        if (arr == null) pdc.set(placedKey, PersistentDataType.LONG_ARRAY, new long[]{k});
        else {
            for (long v : arr) if (v == k) return;
            long[] out = Arrays.copyOf(arr, arr.length + 1);
            out[arr.length] = k;
            pdc.set(placedKey, PersistentDataType.LONG_ARRAY, out);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        long k = (((long) (e.getBlock().getY() + 512)) << 8) | ((e.getBlock().getX() & 15) << 4) | (e.getBlock().getZ() & 15);
        long[] arr = e.getBlock().getChunk().getPersistentDataContainer().get(placedKey, PersistentDataType.LONG_ARRAY);
        if (arr != null) for (long v : arr) if (v == k) return; // player-placed: no luck
        tryDrop(e.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onKill(EntityDeathEvent e) {
        Player k = e.getEntity().getKiller();
        if (k != null && !(e.getEntity() instanceof Player)) tryDrop(k);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onFish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) tryDrop(e.getPlayer());
    }

    // ---------------- /ccollect: free coin every 10 MC days ----------------
    private boolean handleCollect(Player pl) {
        int everyDays = getConfig().getInt("free-coin-every-days", 10);
        long day = pl.getWorld().getFullTime() / 24000L;
        long window = day / everyDays; // current 10-day window index
        long lastWindow = data.getLong("collect." + pl.getUniqueId(), -1);
        if (lastWindow == window) {
            long nextDay = (window + 1) * everyDays;
            pl.sendMessage(ChatColor.RED + "Already collected this window! Next free coin on MC day " + nextDay
                    + ChatColor.GRAY + " (now day " + day + "). Uncollected coins don't stack!");
            return true;
        }
        data.set("collect." + pl.getUniqueId(), window);
        dirty = true;
        giveCoin(pl, "(free collection - next in " + everyDays + " MC days)");
        return true;
    }

    // ---------------- /wish ----------------
    // EVERY wish prize is drawn from the EconomyShopGUI sellable catalogue
    // (well-pool.txt bundled in the jar). Nothing unsellable can drop.
    // Format per line: MATERIAL:maxAmount:weight
    private record WishEntry(Material mat, int maxAmt, int weight) {}
    private final List<WishEntry> poolItems = new ArrayList<>();
    private final List<WishEntry> poolGear = new ArrayList<>();
    private int totalItemWeight = 0;
    private int totalGearWeight = 0;
    private boolean poolLoaded = false;

    private static boolean isGearMaterial(Material m) {
        if (m == null) return false;
        String n = m.name();
        if (n.equals("BOW") || n.equals("CROSSBOW") || n.equals("FISHING_ROD")
                || n.equals("SHIELD") || n.equals("TRIDENT") || n.equals("ELYTRA")
                || n.equals("TURTLE_HELMET") || n.equals("MACE")) return true;
        return n.endsWith("_HELMET") || n.endsWith("_CHESTPLATE") || n.endsWith("_LEGGINGS")
                || n.endsWith("_BOOTS") || n.endsWith("_SWORD") || n.endsWith("_AXE")
                || n.endsWith("_PICKAXE") || n.endsWith("_SHOVEL") || n.endsWith("_HOE")
                || n.endsWith("_SPEAR");
    }

    private static boolean isHardBanned(Material m) {
        if (m == null || !m.isItem() || m.isAir() || m.isLegacy()) return true;
        String n = m.name();
        if (n.equals("NETHERITE_BLOCK") || n.equals("NETHERITE_INGOT")) return true;
        if (n.contains("COMMAND") || n.contains("STRUCTURE") || n.contains("JIGSAW")
                || n.contains("BARRIER") || n.contains("DEBUG") || n.contains("SPAWN_EGG")
                || n.equals("BEDROCK") || n.contains("REINFORCED_DEEPSLATE")
                || n.equals("LIGHT") || n.contains("KNOWLEDGE_BOOK")
                || n.equals("DRAGON_EGG") || n.contains("SPAWNER")
                || n.equals("END_PORTAL_FRAME") || n.equals("BUDDING_AMETHYST")
                || n.contains("INFESTED") || n.equals("PLAYER_HEAD")) return true;
        return false;
    }

    private void loadWellPool() {
        poolItems.clear(); poolGear.clear();
        totalItemWeight = 0; totalGearWeight = 0;
        java.io.InputStream in = getResource("well-pool.txt");
        if (in == null) {
            getLogger().warning("well-pool.txt missing from jar - using tiny iron fallback");
            addPoolEntry(Material.IRON_INGOT, 8, 10, false);
            addPoolEntry(Material.COBBLESTONE, 32, 20, false);
            addPoolEntry(Material.BREAD, 16, 10, false);
            poolLoaded = true;
            return;
        }
        int ok = 0, skip = 0;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(in))) {
            String line;
            while ((line = br.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty() || line.startsWith("#")) continue;
                String[] p = line.split(":");
                if (p.length < 3) { skip++; continue; }
                Material m = Material.matchMaterial(p[0].trim());
                if (m == null || isHardBanned(m)) { skip++; continue; }
                int maxAmt, weight;
                try {
                    maxAmt = Integer.parseInt(p[1].trim());
                    weight = Integer.parseInt(p[2].trim());
                } catch (NumberFormatException ex) { skip++; continue; }
                maxAmt = Math.max(1, Math.min(maxAmt, m.getMaxStackSize()));
                weight = Math.max(1, weight);
                // netherite gear kept very rare
                if (m.name().contains("NETHERITE")) weight = 1;
                boolean gear = isGearMaterial(m);
                addPoolEntry(m, maxAmt, weight, gear);
                ok++;
            }
        } catch (Exception ex) {
            getLogger().warning("Failed reading well-pool.txt: " + ex.getMessage());
        }
        if (poolItems.isEmpty()) {
            addPoolEntry(Material.IRON_INGOT, 8, 10, false);
            addPoolEntry(Material.COBBLESTONE, 32, 20, false);
        }
        poolLoaded = true;
        getLogger().info("Wishing well pool loaded: " + ok + " sellable items (" + skip + " skipped), "
                + "itemW=" + totalItemWeight + " gearW=" + totalGearWeight);
    }

    private void addPoolEntry(Material m, int maxAmt, int weight, boolean gear) {
        WishEntry e = new WishEntry(m, maxAmt, weight);
        if (gear) { poolGear.add(e); totalGearWeight += weight; }
        else { poolItems.add(e); totalItemWeight += weight; }
    }

    private WishEntry pickWeighted(List<WishEntry> list, int total) {
        if (list.isEmpty() || total <= 0) return new WishEntry(Material.IRON_INGOT, 4, 1);
        int r = rng.nextInt(total);
        for (WishEntry e : list) {
            r -= e.weight();
            if (r < 0) return e;
        }
        return list.get(0);
    }

    private ItemStack rollItemPrize() {
        if (!poolLoaded) loadWellPool();
        WishEntry e = pickWeighted(poolItems, totalItemWeight);
        int amt = 1;
        if (e.maxAmt() > 1) {
            // triangular bias toward low amounts
            int cap = e.maxAmt();
            amt = 1 + (int) Math.floor(rng.nextDouble() * rng.nextDouble() * cap);
            if (amt < 1) amt = 1;
            if (amt > cap) amt = cap;
        }
        // hard 1x for trophies / top-tier mineral blocks
        String n = e.mat().name();
        if (n.contains("NETHERITE") || n.equals("ELYTRA") || n.equals("BEACON")
                || n.endsWith("_HEAD") || n.endsWith("_SKULL") || n.equals("TOTEM_OF_UNDYING")
                || n.equals("ENCHANTED_GOLDEN_APPLE") || n.equals("NETHERITE_UPGRADE_SMITHING_TEMPLATE")
                || n.equals("DIAMOND_BLOCK") || n.equals("EMERALD_BLOCK")
                || n.equals("HEART_OF_THE_SEA") || n.equals("CONDUIT") || n.equals("DRAGON_HEAD"))
            amt = 1;
        return new ItemStack(e.mat(), amt);
    }

    private ItemStack rollGearPrize() {
        if (!poolLoaded) loadWellPool();
        List<WishEntry> gear = poolGear.isEmpty() ? poolItems : poolGear;
        int tw = poolGear.isEmpty() ? totalItemWeight : totalGearWeight;
        WishEntry e = pickWeighted(gear, tw);
        ItemStack prize = new ItemStack(e.mat(), 1);
        ItemMeta meta = prize.getItemMeta();
        if (meta == null) return prize;
        List<Enchantment> pool = new ArrayList<>();
        for (Enchantment en : Registry.ENCHANTMENT)
            if (en.canEnchantItem(prize) && !en.isCursed()) pool.add(en);
        boolean nether = e.mat().name().contains("NETHERITE");
        int count = nether ? (rng.nextDouble() < 0.30 ? 1 : 0)
                : rng.nextInt(3) + (rng.nextDouble() < 0.10 ? 1 : 0);
        java.util.Collections.shuffle(pool, rng);
        int added = 0;
        for (Enchantment en : pool) {
            if (added >= count) break;
            boolean conflict = false;
            for (Enchantment have : meta.getEnchants().keySet())
                if (en.conflictsWith(have)) { conflict = true; break; }
            if (conflict) continue;
            int lvl = nether ? 1 : 1 + rng.nextInt(Math.max(1, en.getMaxLevel()));
            meta.addEnchant(en, lvl, true);
            added++;
        }
        prize.setItemMeta(meta);
        return prize;
    }

    private boolean handleWish(Player pl) {
        if (wellMode) {
            pl.sendMessage(ChatColor.RED + "Wishes are made at the " + ChatColor.AQUA + "Wishing Well" + ChatColor.RED + " now!");
            pl.sendMessage(ChatColor.GRAY + "Visit spawn and " + ChatColor.YELLOW + "toss (Q) a \\u26C0 Lucky Coin" + ChatColor.GRAY + " into the well...");
            return true;
        }
        ItemStack hand = pl.getInventory().getItemInMainHand();
        if (!isCoin(hand)) {
            pl.sendMessage(ChatColor.RED + "Hold a " + ChatColor.YELLOW + "\\u26C0 Lucky Coin" + ChatColor.RED + " in your main hand to /wish!");
            return true;
        }
        hand.setAmount(hand.getAmount() - 1);
        grantWish(pl);
        return true;
    }

    private void grantWish(Player pl) {
        if (!poolLoaded) loadWellPool();
        // 18% enchanted gear (from sellable gear only), 82% weighted shop items
        ItemStack prize = rng.nextDouble() < 0.18 ? rollGearPrize() : rollItemPrize();
        // final safety: never banned / never zero
        if (isHardBanned(prize.getType())) {
            prize = new ItemStack(Material.IRON_INGOT, 1 + rng.nextInt(4));
        }
        if (prize.getAmount() < 1) prize.setAmount(1);
        if (prize.getAmount() > prize.getMaxStackSize()) prize.setAmount(prize.getMaxStackSize());

        var left = pl.getInventory().addItem(prize);
        for (ItemStack rest : left.values()) pl.getWorld().dropItemNaturally(pl.getLocation(), rest);
        String pname = prize.getItemMeta() != null && prize.getItemMeta().hasDisplayName()
                ? prize.getItemMeta().getDisplayName()
                : prize.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 0.6f);
        pl.playSound(pl.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1.2f);
        pl.sendMessage(ChatColor.GOLD + "\\u26C0 The coin spins... " + ChatColor.YELLOW + "you got "
                + ChatColor.AQUA + prize.getAmount() + "x " + pname + ChatColor.YELLOW + "!");
        Bukkit.broadcastMessage(ChatColor.GRAY + pl.getName() + " wished on a \\u26C0 Lucky Coin and got "
                + ChatColor.AQUA + prize.getAmount() + "x " + pname);
    }

    // ---------------- wishing well: toss a coin in ----------------
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onCoinToss(org.bukkit.event.player.PlayerDropItemEvent e) {
        if (!wellMode) return;
        org.bukkit.entity.Item drop = e.getItemDrop();
        ItemStack stack = drop.getItemStack();
        if (!isCoin(stack)) return;
        Player pl = e.getPlayer();
        org.bukkit.World w = Bukkit.getWorld(wellWorld);
        if (w == null || pl.getWorld() == null || !pl.getWorld().getName().equals(wellWorld)) return;
        org.bukkit.Location l = pl.getLocation();
        org.bukkit.Location dropLoc = drop.getLocation();
        boolean nearPlayer = Math.abs(l.getBlockX() - wellX) <= wellRadius + 4
                && Math.abs(l.getBlockZ() - wellZ) <= wellRadius + 4
                && Math.abs(l.getBlockY() - wellY) <= 10;
        boolean nearDrop = Math.abs(dropLoc.getBlockX() - wellX) <= wellRadius + 5
                && Math.abs(dropLoc.getBlockZ() - wellZ) <= wellRadius + 5
                && Math.abs(dropLoc.getBlockY() - wellY) <= 12;
        if (!nearPlayer && !nearDrop) return;

        final int count = Math.max(1, Math.min(16, stack.getAmount()));
        // Cancel so the entity does not stay in the world. Paper restores cancelled
        // drops into the inventory AFTER this handler — so we consume next tick.
        e.setCancelled(true);
        try { drop.remove(); } catch (Throwable ignored) {}

        Bukkit.getScheduler().runTask(this, () -> {
            if (!pl.isOnline()) return;
            int before = countCoins(pl);
            if (before < 1) {
                pl.sendMessage(ChatColor.RED + "No Lucky Coin to offer the well.");
                return;
            }
            int spend = Math.min(count, before);
            if (!takeCoins(pl, spend)) {
                pl.sendMessage(ChatColor.RED + "Could not consume Lucky Coin — wish cancelled.");
                return;
            }
            // use actual spent count for wishes
            final int wishes = spend;

            org.bukkit.Location well = new org.bukkit.Location(w, wellX + 0.5, wellY + 0.5, wellZ + 0.5);
            try {
                w.spawnParticle(org.bukkit.Particle.SPLASH, well, 30, 0.35, 0.25, 0.35, 0.05);
            } catch (Throwable t) {
                try { w.spawnParticle(org.bukkit.Particle.BUBBLE, well, 20, 0.3, 0.2, 0.3, 0.02); } catch (Throwable ignored) {}
            }
            try { w.spawnParticle(org.bukkit.Particle.END_ROD, well, 18, 0.25, 0.5, 0.25, 0.03); } catch (Throwable ignored) {}
            try {
                w.playSound(well, Sound.ENTITY_GENERIC_SPLASH, 1f, 1.1f);
                w.playSound(well, Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 0.8f);
            } catch (Throwable ignored) {}
            pl.sendMessage(ChatColor.AQUA + "✦ The well accepts your coin" + (wishes > 1 ? "s" : "") + "...");
            for (int i = 0; i < wishes; i++) {
                final int delay = i * 15;
                Bukkit.getScheduler().runTaskLater(this, () -> {
                    if (!pl.isOnline()) return;
                    try { grantWish(pl); }
                    catch (Throwable t) {
                        getLogger().warning("Wish failed: " + t.getMessage());
                        pl.sendMessage(ChatColor.RED + "Wish fizzled — coin refunded.");
                        giveCoins(pl, 1);
                    }
                }, 10L + delay);
            }
        });
    }

    private void forceTakeFromHands(Player pl, int amount) {
        int left = amount;
        left = stripHand(pl, true, left);
        if (left > 0) stripHand(pl, false, left);
    }

    private int stripHand(Player pl, boolean main, int amount) {
        if (amount <= 0) return 0;
        ItemStack hand = main ? pl.getInventory().getItemInMainHand() : pl.getInventory().getItemInOffHand();
        if (!isCoin(hand)) return amount;
        int a = hand.getAmount();
        if (a <= amount) {
            if (main) pl.getInventory().setItemInMainHand(null);
            else pl.getInventory().setItemInOffHand(null);
            return amount - a;
        }
        hand.setAmount(a - amount);
        return 0;
    }

    private boolean handleWellSet(Player pl) {
        getConfig().set("wishing-well.enabled", true);
        getConfig().set("wishing-well.world", pl.getWorld().getName());
        getConfig().set("wishing-well.x", pl.getLocation().getBlockX());
        getConfig().set("wishing-well.y", pl.getLocation().getBlockY());
        getConfig().set("wishing-well.z", pl.getLocation().getBlockZ());
        saveConfig();
        loadWell();
        spawnWellHolo();
        pl.sendMessage(ChatColor.AQUA + "Wishing Well set here! " + ChatColor.GRAY + "("
                + wellX + ", " + wellY + ", " + wellZ + ") - /wish is now disabled; players toss coins in."
                + ChatColor.AQUA + " Floating sign placed.");
        return true;
    }

    // ---------------- floating sign over the well ----------------
    private void spawnWellHolo() {
        org.bukkit.World w = Bukkit.getWorld(wellWorld);
        if (w == null) return;
        // remove old holo if recorded
        String old = getConfig().getString("wishing-well.holo-uuid");
        if (old != null) {
            try {
                org.bukkit.entity.Entity e = Bukkit.getEntity(UUID.fromString(old));
                if (e != null) e.remove();
            } catch (Exception ignored) {}
        }
        org.bukkit.Location loc = new org.bukkit.Location(w, wellX + 0.5, wellY + 4.6, wellZ + 0.5);
        loc.getChunk().load();
        org.bukkit.entity.TextDisplay td = w.spawn(loc, org.bukkit.entity.TextDisplay.class, d -> {
            d.setText(ChatColor.AQUA + "" + ChatColor.BOLD + "\u2728 WISHING WELL \u2728"
                    + "\n" + ChatColor.WHITE + "Toss " + ChatColor.YELLOW + "(Q)" + ChatColor.WHITE + " a " + ChatColor.GOLD + "\u26C0 Lucky Coin" + ChatColor.WHITE + " in"
                    + "\n" + ChatColor.GRAY + "Every prize is shop-sellable"
                    + "\n" + ChatColor.AQUA + "Fortune favours the bold!");
            d.setBillboard(org.bukkit.entity.Display.Billboard.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(true);
            try { d.setDefaultBackground(false); d.setBackgroundColor(org.bukkit.Color.fromARGB(200, 5, 15, 25)); } catch (Throwable ignored) {}
            d.setAlignment(org.bukkit.entity.TextDisplay.TextAlignment.CENTER);
            d.setLineWidth(260);
            var tr = d.getTransformation();
            tr.getScale().set(1.9f);
            d.setTransformation(tr);
            d.setViewRange(1.3f);
            try { d.setBrightness(new org.bukkit.entity.Display.Brightness(15, 15)); } catch (Throwable ignored) {}
            d.setPersistent(true);
        });
        getConfig().set("wishing-well.holo-uuid", td.getUniqueId().toString());
        saveConfig();
    }


    // ---------------- public API for other MAVO plugins (Personal Vault etc.) ----------------
    /** Count genuine lucky coins in the player's inventory (not ender/armor). */
    public int countCoins(Player pl) {
        if (pl == null) return 0;
        int n = 0;
        for (ItemStack it : pl.getInventory().getContents()) {
            if (isCoin(it)) n += it.getAmount();
        }
        return n;
    }

    /** True if player has at least {@code amount} lucky coins in inventory. */
    public boolean hasCoins(Player pl, int amount) {
        return amount <= 0 || countCoins(pl) >= amount;
    }

    /**
     * Remove up to {@code amount} lucky coins from inventory.
     * @return true if fully paid
     */
    public boolean takeCoins(Player pl, int amount) {
        if (pl == null || amount <= 0) return true;
        if (countCoins(pl) < amount) return false;
        int left = amount;
        ItemStack[] contents = pl.getInventory().getContents();
        for (int i = 0; i < contents.length && left > 0; i++) {
            ItemStack it = contents[i];
            if (!isCoin(it)) continue;
            int a = it.getAmount();
            if (a <= left) {
                left -= a;
                contents[i] = null;
            } else {
                it.setAmount(a - left);
                left = 0;
            }
        }
        pl.getInventory().setContents(contents);
        return left == 0;
    }

    /** Give genuine lucky coin items (admin/shop/refunds). */
    public void giveCoins(Player pl, int amount) {
        if (pl == null || amount <= 0) return;
        amount = Math.min(640, amount);
        ItemStack coins = makeCoin(amount);
        var left = pl.getInventory().addItem(coins);
        for (ItemStack rest : left.values())
            pl.getWorld().dropItemNaturally(pl.getLocation(), rest);
    }

    public ItemStack createCoinItem(int amount) {
        return makeCoin(Math.max(1, Math.min(64, amount)));
    }

    public boolean isLuckyCoin(ItemStack it) { return isCoin(it); }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player pl)) { sender.sendMessage("In-game only."); return true; }
        if (command.getName().equalsIgnoreCase("wish")) {
            if (args.length == 1 && args[0].equalsIgnoreCase("well") && pl.hasPermission("mavolucky.admin"))
                return handleWellSet(pl);
            return handleWish(pl);
        }
        if (command.getName().equalsIgnoreCase("ccollect")) {
            // admin: /ccollect give [amount] - conjure genuine lucky coins for testing
            if (args.length >= 1 && args[0].equalsIgnoreCase("give") && pl.hasPermission("mavolucky.admin")) {
                int n = 1;
                if (args.length >= 2) try { n = Integer.parseInt(args[1]); } catch (NumberFormatException ignored) {}
                n = Math.max(1, Math.min(64, n));
                ItemStack coins = makeCoin(n);
                var left = pl.getInventory().addItem(coins);
                for (ItemStack rest : left.values()) pl.getWorld().dropItemNaturally(pl.getLocation(), rest);
                pl.playSound(pl.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.8f);
                pl.sendMessage(ChatColor.YELLOW + "\u26C0 " + ChatColor.GOLD + "Conjured " + n + " Lucky Coin(s). " + ChatColor.GRAY + "(admin)");
                return true;
            }
            return handleCollect(pl);
        }
        return true;
    }
}
