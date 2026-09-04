package mavo.casino;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import net.milkbowl.vault.economy.Economy;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;

/**
 * MAVOCasino - Lucky Louie's gambling den.
 * 10 games (5 classic + 5 new), coin & lucky attempt pools, random GUI order.
 * House edge ~4-8% on every game: fun, not a money printer.
 */
public final class Casino extends JavaPlugin implements Listener {

    private Economy econ;
    private final Random rng = new Random();
    private NamespacedKey luckyKey;     // mavoluckycoins:luckycoin - genuine coin tag
    private NamespacedKey profToolKey, profOwnerKey; // mavoprofessions tool tags (gambler stick)
    private File dataFile;
    private YamlConfiguration data;

    // 1.2.4: coins start at 1 and DOUBLE per + click (1,2,4,...,524288, then cap 1,000,000)
    private static final int[] COIN_BETS = {1, 2, 4, 8, 16, 32, 64, 128, 256, 512, 1024, 2048,
            4096, 8192, 16384, 32768, 65536, 131072, 262144, 524288, 1_000_000};
    // lucky coins: +1 per click, 1 - 50
    private static final int[] LUCKY_BETS = java.util.stream.IntStream.rangeClosed(1, 50).toArray();

    // ---- Gambler profession lucky sticks: material -> win-luck bonus ----
    // Must match the tier tools in MAVOProfessions config (gambler profession).
    private static final Material[] STICK_MATS = {Material.STICK, Material.BAMBOO, Material.BONE,
            Material.SUGAR_CANE, Material.POINTED_DRIPSTONE, Material.BREEZE_ROD,
            Material.END_ROD, Material.LIGHTNING_ROD, Material.BLAZE_ROD};
    private static final double[] STICK_LUCK = {0.01, 0.02, 0.035, 0.05, 0.065, 0.08, 0.10, 0.12, 0.15};
    private static final double[] MINE_MULT = {1.2, 1.6, 2.2, 3.4, 5.5, 11, 32}; // after k safe reveals
    private static final double[] WHEEL_MULT = {0, 0.5, 1, 2, 3, 10};
    private static final int[] WHEEL_WEIGHT = {34, 25, 15, 16, 9, 1};
    private static final Material[] WHEEL_ICON = {Material.BARRIER, Material.COAL, Material.IRON_INGOT,
            Material.GOLD_INGOT, Material.EMERALD, Material.NETHER_STAR};

    private static final class Holder implements InventoryHolder {
        String view = "main";           // main | cups | flip | dice | wheel | mines
        boolean lucky = false;          // currency
        int betIdx = 0;
        long bet = 0;                   // active game's locked bet
        double luck = 0;                // gambler stick luck bonus locked in at bet time
        boolean busy = false;           // animation running
        boolean settled = true;         // game finished/paid (mines: false while running)
        boolean paid = false;           // result screen shown & payout done
        BukkitTask closeTask;           // pending auto-close of result screen
        java.util.List<String> gameOrder = java.util.List.of();
        int hiLoCard; double hiLoMult;
        double crashMult; boolean crashLive;
        int bjPlayer, bjDealer;
        // mines state
        boolean[] tnt = new boolean[9];
        int revealed = 0;
        Inventory inv;
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    @Override
    public void onEnable() {
        saveDefaultConfig();
        luckyKey = new NamespacedKey("mavoluckycoins", "luckycoin");
        profToolKey = new NamespacedKey("mavoprofessions", "proftool");
        profOwnerKey = new NamespacedKey("mavoprofessions", "profowner");
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOCasino 1.2.4 enabled - the house always wins (about 95% of the time).");
    }

    @Override
    public void onDisable() { save(); }

    private void save() { try { data.save(dataFile); } catch (Exception ignored) {} }

    /* ---------------- attempts: 10 coin + 10 lucky per window ---------------- */

    private long currentDay() { return Bukkit.getWorlds().get(0).getFullTime() / 24000L; }

    private int attemptsLeft(UUID u, boolean lucky) {
        long day = currentDay();
        String key = lucky ? "lucky" : "coin";
        long start = data.getLong("players." + u + "." + key + "window", -100);
        int perWindow = lucky
                ? getConfig().getInt("lucky-attempts", getConfig().getInt("attempts", 10))
                : getConfig().getInt("coin-attempts", getConfig().getInt("attempts", 10));
        int windowDays = getConfig().getInt("window-days", 10);
        if (day - start >= windowDays || start > day) {
            data.set("players." + u + "." + key + "window", day);
            data.set("players." + u + "." + key + "left", perWindow);
            return perWindow;
        }
        return data.getInt("players." + u + "." + key + "left", perWindow);
    }

    private void useAttempt(UUID u, boolean lucky) {
        String key = lucky ? "lucky" : "coin";
        data.set("players." + u + "." + key + "left", Math.max(0, attemptsLeft(u, lucky) - 1));
        save();
    }

    private long nextRefillIn(UUID u, boolean lucky) {
        String key = lucky ? "lucky" : "coin";
        long start = data.getLong("players." + u + "." + key + "window", currentDay());
        return Math.max(0, getConfig().getInt("window-days", 10) - (currentDay() - start));
    }

    /* ---------------- money helpers ---------------- */

    private ItemStack makeLucky(int amount) {
        ItemStack it = new ItemStack(Material.SUNFLOWER, amount);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(ChatColor.translateAlternateColorCodes('&', "&e&l\u26C0 Lucky Coin"));
        m.setLore(java.util.Arrays.asList(
                ChatColor.GRAY + "Toss it into the " + ChatColor.AQUA + "Wishing Well" + ChatColor.GRAY + " at spawn",
                ChatColor.GRAY + "to trade it for... something.",
                ChatColor.DARK_GRAY + "MAVOcraft fortune token"));
        m.addEnchant(Enchantment.UNBREAKING, 1, true);
        m.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
        m.getPersistentDataContainer().set(luckyKey, PersistentDataType.BYTE, (byte) 1);
        it.setItemMeta(m);
        return it;
    }

    private boolean isLucky(ItemStack it) {
        return it != null && it.hasItemMeta()
                && it.getItemMeta().getPersistentDataContainer().has(luckyKey, PersistentDataType.BYTE);
    }

    private int countLucky(Player p) {
        int n = 0;
        for (ItemStack it : p.getInventory().getContents()) if (isLucky(it)) n += it.getAmount();
        return n;
    }

    private boolean takeLucky(Player p, int n) {
        if (n <= 0) return true;
        if (countLucky(p) < n) return false;
        ItemStack[] inv = p.getInventory().getContents();
        int left = n;
        for (int i = 0; i < inv.length && left > 0; i++) {
            if (!isLucky(inv[i])) continue;
            int have = inv[i].getAmount();
            int take = Math.min(left, have);
            if (have <= take) inv[i] = null;
            else inv[i].setAmount(have - take);
            left -= take;
        }
        p.getInventory().setContents(inv);
        return left == 0;
    }

    private void giveLucky(Player p, int n) {
        while (n > 0) {
            int stack = Math.min(64, n);
            var left = p.getInventory().addItem(makeLucky(stack));
            for (ItemStack drop : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), drop);
            n -= stack;
        }
    }

    private boolean chargeBet(Player p, Holder h) {
        long bet = h.lucky ? LUCKY_BETS[h.betIdx] : COIN_BETS[h.betIdx];
        if (h.lucky) {
            if (!takeLucky(p, (int) bet)) {
                p.sendMessage(ChatColor.RED + "You need " + bet + " \u26C0 Lucky Coins in your inventory!");
                return false;
            }
        } else {
            if (econ == null || econ.getBalance(p) < bet) {
                p.sendMessage(ChatColor.RED + "You need \u26C3 " + bet + " coins!");
                return false;
            }
            econ.withdrawPlayer(p, bet);
        }
        h.bet = bet;
        // gambler stick: lock in luck + award profession XP for the wager
        double luck = stickLuck(p);
        h.luck = Math.max(0, luck);
        if (luck >= 0) profXp(p, h.lucky ? bet * 5.0 : bet / 100.0);
        // achievements: coins/lucky coins wagered
        achProgress(p, h.lucky ? "luckybets" : "betting", bet);
        return true;
    }

    /* ---------------- gambler profession bridge (MAVOProfessions) ---------------- */

    /** Luck bonus of the gambler stick the player is holding; -1 if none held. */
    private double stickLuck(Player p) {
        double best = -1;
        ItemStack[] hands = {p.getInventory().getItemInMainHand(), p.getInventory().getItemInOffHand()};
        for (ItemStack it : hands) {
            if (it == null || !it.hasItemMeta()) continue;
            var pdc = it.getItemMeta().getPersistentDataContainer();
            String t = pdc.get(profToolKey, PersistentDataType.STRING);
            String o = pdc.get(profOwnerKey, PersistentDataType.STRING);
            if (t == null || !t.startsWith("gambler:")) continue;
            if (!p.getUniqueId().toString().equals(o)) continue;
            for (int i = 0; i < STICK_MATS.length; i++)
                if (STICK_MATS[i] == it.getType()) best = Math.max(best, STICK_LUCK[i]);
        }
        return best;
    }

    private void profXp(Player p, double amount) {
        org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("MAVOProfessions");
        if (pl == null || !pl.isEnabled()) return;
        try {
            pl.getClass().getMethod("externalXp", Player.class, String.class, double.class)
                    .invoke(pl, p, "gambler", amount);
        } catch (Exception ignored) {}
    }

    private void achProgress(Player p, String category, long amount) {
        org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("MAVOAchievements");
        if (pl == null || !pl.isEnabled()) return;
        try {
            pl.getClass().getMethod("externalProgress", Player.class, String.class, long.class)
                    .invoke(pl, p, category, amount);
        } catch (Exception ignored) {}
    }

    private void payout(Player p, Holder h, double mult) {
        long win = h.lucky ? Math.round(h.bet * mult) : (long) Math.floor(h.bet * mult);
        if (win <= 0) {
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            p.sendMessage(ChatColor.RED + "\u2620 House wins. " + ChatColor.GRAY + "Better luck next time...");
            return;
        }
        if (h.lucky) giveLucky(p, (int) win);
        else if (econ != null) econ.depositPlayer(p, win);
        achProgress(p, h.lucky ? "luckywins" : "winnings", win);
        recordBalances(p);
        String cur = h.lucky ? " \u26C0 Lucky Coins" : " \u26C3 coins";
        if (mult >= 2) {
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.2f);
            p.sendMessage(ChatColor.GOLD + "" + ChatColor.BOLD + "\u2605 WINNER! " + ChatColor.GREEN + "+" + win + cur
                    + ChatColor.GRAY + " (" + mult + "x)");
            if (p.hasPermission("mavocasino.pokercards")) pokerEmote(p);
        } else {
            p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f);
            p.sendMessage(ChatColor.GREEN + "You get back " + win + cur + ChatColor.GRAY + " (" + mult + "x)");
        }
    }

    /** Balance achievements: highwater-mark of coin balance + lucky coins carried. */
    private void recordBalances(Player p) {
        org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("MAVOAchievements");
        if (pl == null || !pl.isEnabled()) return;
        try {
            var m = pl.getClass().getMethod("externalHighwater", Player.class, String.class, long.class);
            if (econ != null) m.invoke(pl, p, "fortune", (long) econ.getBalance(p));
            m.invoke(pl, p, "luckyhoard", (long) countLucky(p));
        } catch (Exception ignored) {}
    }

    /** L1000 ultimate gambler flex: a burst of spinning card-suit particles + jingle. */
    private void pokerEmote(Player p) {
        org.bukkit.Location base = p.getLocation().add(0, 1.2, 0);
        p.getWorld().playSound(base, Sound.ENTITY_PLAYER_LEVELUP, 1f, 2f);
        p.getWorld().playSound(base, Sound.BLOCK_NOTE_BLOCK_CHIME, 1f, 1.6f);
        final int[] tick = {0};
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline() || tick[0]++ >= 20) { cancel(); return; }
                double a = tick[0] * 0.55;
                for (int card = 0; card < 4; card++) {
                    double ang = a + card * Math.PI / 2;
                    double r = 0.9 + 0.25 * Math.sin(tick[0] * 0.4);
                    org.bukkit.Location l = p.getLocation().add(Math.cos(ang) * r, 1.1 + 0.5 * Math.sin(a + card), Math.sin(ang) * r);
                    // red suits = flame/red dust, black suits = smoke/white sparkle
                    if (card % 2 == 0)
                        p.getWorld().spawnParticle(org.bukkit.Particle.DUST, l, 3, 0.05, 0.05, 0.05,
                                new org.bukkit.Particle.DustOptions(org.bukkit.Color.RED, 1.4f));
                    else
                        p.getWorld().spawnParticle(org.bukkit.Particle.DUST, l, 3, 0.05, 0.05, 0.05,
                                new org.bukkit.Particle.DustOptions(org.bukkit.Color.WHITE, 1.4f));
                }
                if (tick[0] % 5 == 0)
                    p.getWorld().spawnParticle(org.bukkit.Particle.FIREWORK, base, 8, 0.4, 0.5, 0.4, 0.05);
            }
        }.runTaskTimer(this, 0L, 2L);
        p.getWorld().getPlayers().forEach(o -> {
            if (o.getLocation().distanceSquared(p.getLocation()) < 400 && !o.equals(p))
                o.sendMessage(ChatColor.RED + "\u2660\u2665 " + ChatColor.GOLD + p.getName()
                        + ChatColor.YELLOW + " fans out their cards - the ultimate gambler wins again! "
                        + ChatColor.RED + "\u2666\u2663");
        });
    }

    /* ---------------- GUI building ---------------- */

    private String trimPct(double luck) {
        double pct = luck * 100;
        return pct == Math.floor(pct) ? String.valueOf((int) pct) : String.valueOf(pct);
    }

    private ItemStack item(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (lore != null) {
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(ChatColor.translateAlternateColorCodes('&', s));
            meta.setLore(l);
        }
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", null);
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, pane);
    }

    private void openMain(Player p, Holder prev) {
        Holder h = new Holder();
        h.settled = true;
        h.busy = false;
        if (prev != null) { h.lucky = prev.lucky; h.betIdx = prev.betIdx; }
        Inventory inv = Bukkit.createInventory(h, 36, ChatColor.DARK_RED + "" + ChatColor.BOLD + "\u2663 Lucky Louie's Casino \u2660");
        h.inv = inv;

        int coinLeft = attemptsLeft(p.getUniqueId(), false);
        int luckyLeft = attemptsLeft(p.getUniqueId(), true);
        inv.setItem(4, item(Material.CLOCK, "&6&l\u2666 Your Luck Allowance",
                List.of("&eCoin attempts: " + (coinLeft > 0 ? "&a" : "&c") + coinLeft + "&7 / " + getConfig().getInt("coin-attempts", 10),
                        "&6Lucky attempts: " + (luckyLeft > 0 ? "&a" : "&c") + luckyLeft + "&7 / " + getConfig().getInt("lucky-attempts", 10),
                        "&7Refill coin in &e" + nextRefillIn(p.getUniqueId(), false) + " &7/ lucky &e" + nextRefillIn(p.getUniqueId(), true) + " &7MC days",
                        "", "&7Each game costs &f1 attempt &7of the currency you chose.",
                        "&7Louie shuffles the board every visit.")));

        // 10 games in random order across slots
        int[] gameSlots = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
        java.util.List<String> games = new java.util.ArrayList<>(java.util.List.of(
                "cups", "flip", "dice", "wheel", "mines",
                "hiLo", "crash", "slots", "rps", "blackjack"));
        if (getConfig().getBoolean("random-order", true)) java.util.Collections.shuffle(games, rng);
        h.gameOrder = games; // store mapping for click
        for (int i = 0; i < games.size() && i < gameSlots.length; i++) {
            inv.setItem(gameSlots[i], gameIcon(games.get(i)));
        }

        long bet = h.lucky ? LUCKY_BETS[h.betIdx] : COIN_BETS[h.betIdx];
        inv.setItem(28, item(h.lucky ? Material.SUNFLOWER : Material.GOLD_NUGGET,
                "&e&lCurrency: " + (h.lucky ? "&6\u26C0 Lucky Coins" : "&e\u26C3 Coins"),
                List.of("&7Click to switch", "&7You have: &f" + (h.lucky ? countLucky(p) : (econ == null ? 0 : (long) econ.getBalance(p))))));
        inv.setItem(30, item(Material.RED_STAINED_GLASS_PANE, "&c- Lower bet", null));
        inv.setItem(31, item(Material.PAPER, "&f&lBet: &e" + bet + (h.lucky ? " \u26C0" : " \u26C3"),
                List.of("&7Coins: &f1 - 1,000,000 &7(doubles on +)",
                        "&7Lucky Coins: &f1 - 50 &7(+1 each)")));
        inv.setItem(32, item(Material.LIME_STAINED_GLASS_PANE, "&a+ Raise bet", null));
        double luck = stickLuck(p);
        if (luck >= 0)
            inv.setItem(34, item(Material.BLAZE_ROD, "&6&l\u2660 Lucky Stick held!",
                    List.of("&7Gambler profession bonus:", "&a+" + trimPct(luck) + "% win luck &7on every game",
                            "", "&7Betting also earns &6\u2660 Gambler XP&7.")));
        else
            inv.setItem(34, item(Material.STICK, "&7\u2660 No lucky stick...",
                    List.of("&7Start the &6Gambler &7profession in &e/prof",
                            "&7and HOLD your bound lucky stick here:",
                            "&7- bets earn &6Gambler XP",
                            "&7- the stick adds &awin luck &7(up to +15%)")));
        inv.setItem(35, item(Material.BARRIER, "&cLeave", null));
        fill(inv);
        h.view = "main";
        h.inv = inv;
        h.settled = true;
        h.busy = false;
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BANJO, 0.8f, 1.1f);
    }

    private Inventory gameInv(Holder h, String view, String title) {
        h.view = view;
        Inventory inv = Bukkit.createInventory(h, 27, ChatColor.translateAlternateColorCodes('&', title));
        h.inv = inv;
        h.settled = false;
        h.busy = false;
        h.paid = false;
        return inv;
    }

    /** Open game GUI and re-assert unsettled after Paper fires InventoryClose on the previous menu. */
    private void showGame(Player p, Holder h, Inventory inv) {
        h.inv = inv;
        h.settled = false;
        h.busy = false;
        p.openInventory(inv);
        // onClose may run synchronously during openInventory — force state after
        h.settled = false;
        h.busy = false;
        Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline()) return;
            if (h.inv == inv && h.view != null && !h.view.equals("main")) {
                h.settled = false;
            }
        });
    }


    private ItemStack gameIcon(String id) {
        return switch (id) {
            case "cups" -> item(Material.DECORATED_POT, "&e&l\u26B1 Pick a Cup",
                    List.of("&7Pearl under 3 cups.", "", "&aWin: &f2.7x", "", "&e\u25B6 Play"));
            case "flip" -> item(Material.SUNFLOWER, "&6&l\u26C0 Coin Flip",
                    List.of("&7Heads or tails.", "", "&aWin: &f1.9x", "", "&e\u25B6 Play"));
            case "dice" -> item(Material.BONE_BLOCK, "&f&l\u2680 Dice Duel",
                    List.of("&7Beat Louie's d6.", "", "&aWin: &f2.3x", "", "&e\u25B6 Play"));
            case "wheel" -> item(Material.AMETHYST_CLUSTER, "&d&l\u2740 Crystal Wheel",
                    List.of("&7Spin 0x-10x.", "", "&e\u25B6 Spin"));
            case "mines" -> item(Material.TNT, "&c&l\u2620 TNT Tiles",
                    List.of("&7Avoid TNT, cash out.", "", "&e\u25B6 Play"));
            case "hiLo" -> item(Material.COMPARATOR, "&b&l\u2195 Higher or Lower",
                    List.of("&7Guess next card higher/lower.", "&aStreak multiplies!", "", "&e\u25B6 Play"));
            case "crash" -> item(Material.FIREWORK_ROCKET, "&c&lRocket Crash",
                    List.of("&7Multiplier climbs... cash before boom!", "", "&e\u25B6 Ride"));
            case "slots" -> item(Material.GOLD_INGOT, "&6&lLucky Slots",
                    List.of("&7Match 3 symbols.", "&aJackpot 12x!", "", "&e\u25B6 Spin"));
            case "rps" -> item(Material.COBBLESTONE, "&f&lRock Paper Scissors",
                    List.of("&7Best of nerves vs Louie.", "&aWin 1.9x", "", "&e\u25B6 Play"));
            case "blackjack" -> item(Material.MAP, "&2&lBlackjack 21",
                    List.of("&7Hit or stand. Beat 21?", "&aBlackjack pays 2.5x", "", "&e\u25B6 Deal"));
            default -> item(Material.BARRIER, "&c?", null);
        };
    }

    /* ---------------- games ---------------- */

    private void openCups(Player p, Holder h) {
        h.settled = false;
        h.busy = false;
        Inventory inv = gameInv(h, "cups", "&e&l\u26B1 Pick a Cup &8- bet " + h.bet);
        for (int s : new int[]{11, 13, 15})
            inv.setItem(s, item(Material.DECORATED_POT, "&e&lCup", List.of("&7The pearl is under ONE of these...", "&e\u25B6 Pick me!")));
        inv.setItem(4, item(Material.ENDER_PEARL, "&d&lWhere's the pearl?", List.of("&7One cup pays &a2.7x&7. Choose wisely.")));
        fill(inv);
        showGame(p, h, inv);
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_OPEN, 1f, 1.2f);
    }

    private void resolveCups(Player p, Holder h, int clickedSlot) {
        h.busy = true;
        int[] slots = {11, 13, 15};
        boolean won = rng.nextDouble() < (1.0 / 3.0 + h.luck);
        int winSlot;
        if (won) winSlot = clickedSlot;
        else { do { winSlot = slots[rng.nextInt(3)]; } while (winSlot == clickedSlot); }
        for (int s : slots) {
            if (s == winSlot) h.inv.setItem(s, item(Material.ENDER_PEARL, "&d&lThe pearl!", null));
            else h.inv.setItem(s, item(Material.BARRIER, "&8Empty", null));
        }
        p.playSound(p.getLocation(), Sound.BLOCK_BARREL_CLOSE, 1f, 0.9f);
        finishLater(p, h, won ? 2.7 : 0, 25);
    }

    private void openFlip(Player p, Holder h) {
        h.settled = false;
        h.busy = false;
        Inventory inv = gameInv(h, "flip", "&6&l\u26C0 Coin Flip &8- bet " + h.bet);
        inv.setItem(11, item(Material.REDSTONE_BLOCK, "&c&lRED side", List.of("&e\u25B6 Call red!")));
        inv.setItem(15, item(Material.LAPIS_BLOCK, "&9&lBLUE side", List.of("&e\u25B6 Call blue!")));
        inv.setItem(4, item(Material.SUNFLOWER, "&6&lCall the flip", List.of("&7Right call pays &a2x")));
        fill(inv);
        showGame(p, h, inv);
    }

    private void resolveFlip(Player p, Holder h, boolean calledRed) {
        h.busy = true;
        boolean won = rng.nextDouble() < 0.475 + h.luck;
        boolean landsRed = won == calledRed;
        final int[] tick = {0};
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                tick[0]++;
                if (tick[0] < 8) {
                    boolean red = tick[0] % 2 == 0;
                    h.inv.setItem(13, item(red ? Material.REDSTONE_BLOCK : Material.LAPIS_BLOCK,
                            red ? "&c&l\u26C0 spinning..." : "&9&l\u26C0 spinning...", null));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1f + tick[0] * 0.1f);
                    return;
                }
                cancel();
                h.inv.setItem(13, item(landsRed ? Material.REDSTONE_BLOCK : Material.LAPIS_BLOCK,
                        landsRed ? "&c&lIt's RED!" : "&9&lIt's BLUE!", null));
                finishNow(p, h, won ? 2.0 : 0);
            }
        }.runTaskTimer(this, 5L, 4L);
    }

    private void openDice(Player p, Holder h) {
        h.settled = false;
        h.busy = false;
        Inventory inv = gameInv(h, "dice", "&f&l\u2680 Dice Duel &8- bet " + h.bet);
        inv.setItem(13, item(Material.BONE_BLOCK, "&f&lROLL THE DICE", List.of("&7Beat Louie's roll to win &a2.3x", "&cTies go to Louie!", "", "&e\u25B6 Click to roll")));
        fill(inv);
        showGame(p, h, inv);
    }

    private void resolveDice(Player p, Holder h) {
        h.busy = true;
        boolean won = rng.nextDouble() < 15.0 / 36.0 + h.luck; // base 41.7% + stick luck
        int mine, louie;
        do { mine = 1 + rng.nextInt(6); louie = 1 + rng.nextInt(6); }
        while (won != (mine > louie));
        h.inv.setItem(11, item(Material.LIME_CONCRETE, "&a&lYou rolled: " + mine, null));
        h.inv.setItem(15, item(Material.RED_CONCRETE, "&c&lLouie rolled: " + louie, null));
        p.playSound(p.getLocation(), Sound.BLOCK_STONE_HIT, 1f, 0.8f);
        showResult(p, h, won ? 2.3 : 0, null, null, null);
    }

    private void openWheel(Player p, Holder h) {
        h.settled = false;
        h.busy = false;
        Inventory inv = gameInv(h, "wheel", "&d&l\u2740 Crystal Wheel &8- bet " + h.bet);
        inv.setItem(13, item(Material.AMETHYST_CLUSTER, "&d&lSPIN", List.of("&80x &7\u00B7 &80.5x &7\u00B7 &f1x &7\u00B7 &e2x &7\u00B7 &a3x &7\u00B7 &b10x", "", "&e\u25B6 Click to spin")));
        fill(inv);
        showGame(p, h, inv);
    }

    private int pickWheel() {
        int total = 0; for (int w : WHEEL_WEIGHT) total += w;
        int r = rng.nextInt(total);
        for (int i = 0; i < WHEEL_WEIGHT.length; i++) { r -= WHEEL_WEIGHT[i]; if (r < 0) return i; }
        return 0;
    }

    private void resolveWheel(Player p, Holder h) {
        h.busy = true;
        int firstSpin = pickWheel();
        // gambler stick luck: chance to spin twice and keep the better result
        if (h.luck > 0 && rng.nextDouble() < h.luck * 2) firstSpin = Math.max(firstSpin, pickWheel());
        final int result = firstSpin;
        final int[] tick = {0};
        new BukkitRunnable() {
            @Override public void run() {
                if (!p.isOnline()) { cancel(); return; }
                tick[0]++;
                if (tick[0] < 12) {
                    int show = rng.nextInt(WHEEL_MULT.length);
                    h.inv.setItem(13, item(WHEEL_ICON[show], "&d&l" + WHEEL_MULT[show] + "x ...", null));
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_XYLOPHONE, 0.6f, 0.8f + tick[0] * 0.1f);
                    return;
                }
                cancel();
                double mult = WHEEL_MULT[result];
                h.inv.setItem(13, item(WHEEL_ICON[result],
                        (mult >= 2 ? "&a&l" : mult >= 1 ? "&e&l" : "&c&l") + "Landed on " + mult + "x!", null));
                finishNow(p, h, mult);
            }
        }.runTaskTimer(this, 5L, 3L);
    }

    private void openMines(Player p, Holder h) {
        Inventory inv = gameInv(h, "mines", "&c&l\u2620 TNT Tiles &8- bet " + h.bet);
        h.settled = false;
        h.revealed = 0;
        java.util.Arrays.fill(h.tnt, false);
        int placed = 0;
        while (placed < 2) {
            int t = rng.nextInt(9);
            if (!h.tnt[t]) { h.tnt[t] = true; placed++; }
        }
        int[] grid = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        for (int g : grid) inv.setItem(g, item(Material.GRAY_WOOL, "&7? Tile", List.of("&e\u25B6 Reveal (2 TNT hiding!)")));
        inv.setItem(17, item(Material.GOLD_INGOT, "&6&lCASH OUT",
                List.of("&7Current: &f1.0x &7(your bet back)", "&7Next safe tile: &a1.2x")));
        fill(inv);
        showGame(p, h, inv);
        p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.7f);
    }

    private int mineIndex(int slot) {
        int[] grid = {3, 4, 5, 12, 13, 14, 21, 22, 23};
        for (int i = 0; i < grid.length; i++) if (grid[i] == slot) return i;
        return -1;
    }

    private void resolveMineClick(Player p, Holder h, int slot) {
        int idx = mineIndex(slot);
        if (idx >= 0) {
            ItemStack cur = h.inv.getItem(slot);
            if (cur == null || cur.getType() != Material.GRAY_WOOL) return; // already revealed
            // gambler stick luck: chance the TNT "fizzles" and slides to another hidden tile
            if (h.tnt[idx] && h.luck > 0 && rng.nextDouble() < h.luck) {
                int[] grid = {3, 4, 5, 12, 13, 14, 21, 22, 23};
                List<Integer> spots = new ArrayList<>();
                for (int i = 0; i < 9; i++) {
                    if (i == idx || h.tnt[i]) continue;
                    ItemStack tile = h.inv.getItem(grid[i]);
                    if (tile != null && tile.getType() == Material.GRAY_WOOL) spots.add(i);
                }
                if (!spots.isEmpty()) {
                    h.tnt[idx] = false;
                    h.tnt[spots.get(rng.nextInt(spots.size()))] = true;
                    p.playSound(p.getLocation(), Sound.BLOCK_FIRE_EXTINGUISH, 1f, 1.4f);
                    p.sendMessage(ChatColor.GOLD + "\u2660 " + ChatColor.YELLOW + "Your lucky stick trembles... the TNT fizzles out!");
                }
            }
            if (h.tnt[idx]) {
                // boom - reveal all
                int[] grid = {3, 4, 5, 12, 13, 14, 21, 22, 23};
                for (int i = 0; i < 9; i++)
                    h.inv.setItem(grid[i], item(h.tnt[i] ? Material.TNT : Material.LIME_WOOL,
                            h.tnt[i] ? "&c&lTNT!" : "&aSafe", null));
                h.settled = true;
                h.busy = true;
                p.playSound(p.getLocation(), Sound.ENTITY_GENERIC_EXPLODE, 1f, 1f);
                finishLater(p, h, 0, 30);
                return;
            }
            h.revealed++;
            double mult = MINE_MULT[h.revealed - 1];
            h.inv.setItem(slot, item(Material.LIME_WOOL, "&a&lSafe! &f" + mult + "x", null));
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 0.8f + h.revealed * 0.15f);
            if (h.revealed >= 7) { // all safe tiles found
                h.settled = true;
                h.busy = true;
                p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
                finishLater(p, h, MINE_MULT[6], 30);
                return;
            }
            double next = MINE_MULT[h.revealed];
            h.inv.setItem(17, item(Material.GOLD_INGOT, "&6&lCASH OUT",
                    List.of("&7Current: &a" + mult + "x", "&7Next safe tile: &a" + next + "x",
                            "&7TNT left: &c2 &7in &f" + (9 - h.revealed - 0) + " tiles... wait, still 2!")));
            return;
        }
        if (slot == 17) { // cash out
            double mult = h.revealed == 0 ? 1.0 : MINE_MULT[h.revealed - 1];
            h.settled = true;
            h.busy = true;
            finishNow(p, h, mult);
        }
    }

    /* ---------------- finish helpers ---------------- */


    private void openHiLo(Player p, Holder h) {
        h.settled = false;
        h.busy = false;
        h.hiLoCard = 2 + rng.nextInt(12); // 2..13
        h.hiLoMult = 1.0;
        Inventory inv = gameInv(h, "hiLo", "&b&lHigher/Lower &8- bet " + h.bet);
        inv.setItem(13, item(Material.PAPER, "&f&lCard: &e" + h.hiLoCard, List.of("&7Multiplier: &a" + trim(h.hiLoMult) + "x")));
        inv.setItem(11, item(Material.LIME_WOOL, "&a&lHIGHER", List.of("&e\u25B6 Guess higher")));
        inv.setItem(15, item(Material.RED_WOOL, "&c&lLOWER", List.of("&e\u25B6 Guess lower")));
        inv.setItem(22, item(Material.GOLD_INGOT, "&6&lCASH OUT", List.of("&7Take " + trim(h.hiLoMult) + "x now")));
        fill(inv); showGame(p, h, inv);
    }
    private void resolveHiLo(Player p, Holder h, String guess) {
        if (h.settled) return;
        if ("cash".equals(guess)) {
            h.inv.setItem(13, item(Material.PAPER, "&f&lCard: &e" + h.hiLoCard, List.of("&7Multiplier: &a" + trim(h.hiLoMult) + "x", "&7Cashed out!")));
            showResult(p, h, h.hiLoMult, null, null, "&e&lCASHED OUT &f" + (h.lucky ? Math.round(h.bet * h.hiLoMult) : (long) Math.floor(h.bet * h.hiLoMult))
                    + (h.lucky ? " \u26C0" : " \u26C3") + " &7(" + trim(h.hiLoMult) + "x)");
            return;
        }
        int next = 2 + rng.nextInt(12);
        boolean higher = next > h.hiLoCard;
        boolean lower = next < h.hiLoCard;
        boolean win = ("higher".equals(guess) && higher) || ("lower".equals(guess) && lower);
        // ties lose (house)
        if (!win || next == h.hiLoCard) {
            h.inv.setItem(11, item(Material.PAPER, "&f&lCard was: &e" + h.hiLoCard, null));
            showResult(p, h, 0, null, null, "&c&l" + next + " - BUST! &7Louie wins");
            return;
        }
        h.hiLoCard = next;
        h.hiLoMult *= 1.45;
        double luck = stickLuck(p);
        if (luck > 0 && rng.nextDouble() < luck / 200.0) h.hiLoMult *= 1.1;
        h.inv.setItem(13, item(Material.PAPER, "&f&lCard: &e" + h.hiLoCard, List.of("&7Multiplier: &a" + trim(h.hiLoMult) + "x", "&aCorrect!")));
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, 1.4f);
    }

    private void openCrash(Player p, Holder h) {
        h.settled = false;
        h.busy = false;
        h.crashMult = 1.0;
        h.crashLive = true;
        Inventory inv = gameInv(h, "crash", "&c&lRocket Crash &8- bet " + h.bet);
        inv.setItem(13, item(Material.FIREWORK_ROCKET, "&e&l" + trim(h.crashMult) + "x", List.of("&7Climbing...")));
        inv.setItem(22, item(Material.GOLD_INGOT, "&6&lCASH OUT", null));
        fill(inv); showGame(p, h, inv);
        runCrashTick(p, h);
    }
    private void runCrashTick(Player p, Holder h) {
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline() || !h.crashLive || h.settled) return;
            // crash chance rises
            double crashChance = 0.06 + (h.crashMult - 1.0) * 0.04;
            if (rng.nextDouble() < crashChance) {
                h.crashLive = false;
                h.inv.setItem(13, item(Material.TNT, "&c&lBOOM @ " + trim(h.crashMult) + "x", null));
                finishLater(p, h, 0, 20);
                return;
            }
            h.crashMult += 0.15 + rng.nextDouble() * 0.2;
            h.inv.setItem(13, item(Material.FIREWORK_ROCKET, "&e&l" + trim(h.crashMult) + "x", List.of("&7Still flying...")));
            runCrashTick(p, h);
        }, 12L);
    }

    private void openSlots(Player p, Holder h) {
        h.settled = false;
        h.busy = false;
        Inventory inv = gameInv(h, "slots", "&6&lLucky Slots &8- bet " + h.bet);
        inv.setItem(13, item(Material.LEVER, "&e&lPULL", List.of("&7Click to spin")));
        fill(inv); showGame(p, h, inv);
    }
    private void resolveSlots(Player p, Holder h) {
        if (h.settled) return;
        Material[] pool = {Material.GOLD_NUGGET, Material.DIAMOND, Material.EMERALD, Material.COAL, Material.APPLE};
        Material a = pool[rng.nextInt(pool.length)];
        Material b = pool[rng.nextInt(pool.length)];
        Material c = pool[rng.nextInt(pool.length)];
        // slight luck bias
        double luck = stickLuck(p);
        if (luck > 0 && rng.nextDouble() < luck / 100.0) { b = a; c = a; }
        h.inv.setItem(11, item(a, "&f", null));
        h.inv.setItem(13, item(b, "&f", null));
        h.inv.setItem(15, item(c, "&f", null));
        double mult = 0;
        if (a == b && b == c) mult = (a == Material.DIAMOND) ? 12.0 : (a == Material.EMERALD) ? 8.0 : 5.0;
        else if (a == b || b == c || a == c) mult = 1.5;
        finishLater(p, h, mult, 30);
    }

    private void openRps(Player p, Holder h) {
        h.settled = false;
        h.busy = false;
        Inventory inv = gameInv(h, "rps", "&f&lRPS &8- bet " + h.bet);
        inv.setItem(11, item(Material.COBBLESTONE, "&7&lROCK", null));
        inv.setItem(13, item(Material.PAPER, "&f&lPAPER", null));
        inv.setItem(15, item(Material.SHEARS, "&c&lSCISSORS", null));
        fill(inv); showGame(p, h, inv);
    }
    private void resolveRps(Player p, Holder h, int choice) {
        if (h.settled) return;
        int louie = rng.nextInt(3); // 0 rock 1 paper 2 scissors
        // house edge: 5% force Louie win
        if (rng.nextDouble() < 0.05) louie = (choice + 1) % 3;
        double luck = stickLuck(p);
        if (luck > 0 && rng.nextDouble() < luck / 150.0) louie = (choice + 2) % 3; // player wins
        int diff = (choice - louie + 3) % 3;
        double mult = (diff == 0) ? 0 : (diff == 1) ? 1.9 : 0; // 1=win, 2=lose
        String[] names = {"ROCK", "PAPER", "SCISSORS"};
        Material[] icons = {Material.COBBLESTONE, Material.PAPER, Material.SHEARS};
        showResult(p, h, mult,
                item(icons[choice], "&7&lYou: &f" + names[choice], null),
                item(icons[louie], "&e&lLouie: &f" + names[louie], null),
                null);
    }

    private void openBlackjack(Player p, Holder h) {
        h.settled = false;
        h.busy = false;
        h.bjPlayer = 2 + rng.nextInt(10) + 2 + rng.nextInt(10); // two cards sum rough
        if (h.bjPlayer > 21) h.bjPlayer = 12 + rng.nextInt(9);
        h.bjDealer = 2 + rng.nextInt(11);
        Inventory inv = gameInv(h, "blackjack", "&2&lBlackjack &8- bet " + h.bet);
        inv.setItem(11, item(Material.MAP, "&a&lYou: &f" + h.bjPlayer, null));
        inv.setItem(15, item(Material.MAP, "&c&lLouie shows: &f" + h.bjDealer, null));
        inv.setItem(20, item(Material.LIME_CONCRETE, "&a&lHIT", null));
        inv.setItem(24, item(Material.YELLOW_CONCRETE, "&e&lSTAND", null));
        fill(inv); showGame(p, h, inv);
    }
    private void resolveBj(Player p, Holder h, boolean hit) {
        if (h.settled) return;
        if (hit) {
            h.bjPlayer += 2 + rng.nextInt(10);
            h.inv.setItem(11, item(Material.MAP, "&a&lYou: &f" + h.bjPlayer, null));
            if (h.bjPlayer > 21) {
                showResult(p, h, 0, null, null, "&c&lBUST! &fYou had " + h.bjPlayer + " - Louie wins");
                return;
            }
            return;
        }
        // dealer draws to 17
        while (h.bjDealer < 17) h.bjDealer += 2 + rng.nextInt(10);
        h.inv.setItem(15, item(Material.MAP, "&c&lLouie: &f" + h.bjDealer, null));
        double mult;
        if (h.bjPlayer == 21 && h.bjDealer != 21) mult = 2.5;
        else if (h.bjDealer > 21 || h.bjPlayer > h.bjDealer) mult = 2.0;
        else if (h.bjPlayer == h.bjDealer) mult = 1.0; // push
        else mult = 0;
        String label;
        if (mult >= 2.5) label = "&a&lBLACKJACK! &fYou beat Louie's " + h.bjDealer + " (" + trim(mult) + "x)";
        else if (mult >= 2.0) label = "&a&lYOU WIN! &f" + h.bjPlayer + " beats " + h.bjDealer + " (" + trim(mult) + "x)";
        else if (mult >= 1.0) label = "&e&lPUSH &f" + h.bjPlayer + " = " + h.bjDealer + " - bet back (" + trim(mult) + "x)";
        else label = "&c&lLOUIE WINS &f" + h.bjDealer + " beats " + h.bjPlayer;
        showResult(p, h, mult, null, null, label);
    }

    private String trim(double d) { return String.format(java.util.Locale.US, "%.2f", d); }

    private int resultSeconds() { return Math.max(1, getConfig().getInt("result-seconds", 5)); }

    /** Result banner for slot 13: win/lose text with amount. */
    private ItemStack resultHeader(Holder h, double mult, String custom) {
        long win = h.lucky ? Math.round(h.bet * mult) : (long) Math.floor(h.bet * mult);
        String cur = h.lucky ? " \u26C0" : " \u26C3";
        if (custom != null && !custom.isEmpty())
            return item(Material.GOLD_BLOCK, custom,
                    List.of("&7Click &c&lCLOSE &7to return to the casino."));
        if (win > 0)
            return item(Material.GOLD_BLOCK, "&a&lYOU WIN &f+" + win + cur + " &7(" + trim(mult) + "x)",
                    List.of("&7Click &c&lCLOSE &7to return to the casino."));
        return item(Material.COAL_BLOCK, "&c&lLOUIE WINS &7(0x)",
                List.of("&7Your " + h.bet + cur + " stays with the house.",
                        "&7Click &c&lCLOSE &7to try again."));
    }

    /**
     * Result screen: pays out immediately, marks the game settled, shows the outcome
     * and a CLOSE button, then auto-returns after result-seconds (5 by default).
     */
    private void showResult(Player p, Holder h, double mult, ItemStack left, ItemStack right, String custom) {
        if (h.paid && h.settled) return; // already displayed
        h.settled = true;
        h.busy = false;
        if (!h.paid) { payout(p, h, mult); h.paid = true; }
        if (left != null) h.inv.setItem(11, left);
        if (right != null) h.inv.setItem(15, right);
        h.inv.setItem(13, resultHeader(h, mult, custom));
        h.inv.setItem(26, item(Material.RED_STAINED_GLASS_PANE, "&c&l\u2726 CLOSE",
                List.of("&7Back to Lucky Louie's casino", "&7Auto-closes in &f" + resultSeconds() + "s")));
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 0.8f);
        if (h.closeTask != null) { try { h.closeTask.cancel(); } catch (Throwable ignored) {} }
        h.closeTask = Bukkit.getScheduler().runTaskLater(this, () -> {
            h.closeTask = null;
            if (!p.isOnline()) return;
            Inventory top = p.getOpenInventory().getTopInventory();
            if (top != null && top.getHolder() == h) openMain(p, h);
        }, (long) resultSeconds() * 20L);
    }

    private void closeResultWindow(Player p, Holder h) {
        if (h.closeTask != null) { try { h.closeTask.cancel(); } catch (Throwable ignored) {} h.closeTask = null; }
        openMain(p, h);
    }

    private void finishNow(Player p, Holder h, double mult) {
        h.settled = true;
        payout(p, h, mult);
        Bukkit.getScheduler().runTaskLater(this, () -> { if (p.isOnline()) openMain(p, h); }, 30L);
    }

    private void finishLater(Player p, Holder h, double mult, long delayTicks) {
        h.settled = true;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            payout(p, h, mult);
            openMain(p, h);
        }, delayTicks);
    }

    /* ---------------- events ---------------- */

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        // Paper: getView().getTopInventory() is the casino chest
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() == null) return;
        // only handle clicks in the top casino inventory
        if (e.getClickedInventory() != top) return;
        if (e.getRawSlot() < 0 || e.getRawSlot() >= top.getSize()) return;
        if (h.busy) return;
        // stale holder from a previous openMain after reopen
        if (h.inv != null && h.inv != top) return;
        int slot = e.getRawSlot();

        if (h.view.equals("main")) {
            switch (slot) {
                case 28 -> { h.lucky = !h.lucky; h.betIdx = 0; openMain(p, h); }
                case 30 -> { h.betIdx = Math.max(0, h.betIdx - 1); openMain(p, h); }
                case 32 -> { h.betIdx = Math.min((h.lucky ? LUCKY_BETS : COIN_BETS).length - 1, h.betIdx + 1); openMain(p, h); }
                case 35 -> p.closeInventory();
                default -> {
                    int[] gameSlots = {9, 10, 11, 12, 13, 14, 15, 16, 17, 18};
                    for (int i = 0; i < gameSlots.length; i++) {
                        if (slot == gameSlots[i] && h.gameOrder != null && i < h.gameOrder.size()) {
                            startGameById(p, h, h.gameOrder.get(i));
                            break;
                        }
                    }
                }
            }
            return;
        }
        // result screen shown? only CLOSE is clickable
        if (h.paid) {
            if (slot == 26) closeResultWindow(p, h);
            return;
        }
        // in-game clicks
        switch (h.view) {
            case "cups" -> { if (slot == 11 || slot == 13 || slot == 15) resolveCups(p, h, slot); }
            case "flip" -> { if (slot == 11) resolveFlip(p, h, true); else if (slot == 15) resolveFlip(p, h, false); }
            case "dice" -> { if (slot == 13) resolveDice(p, h); }
            case "wheel" -> { if (slot == 13) resolveWheel(p, h); }
            case "mines" -> resolveMineClick(p, h, slot);
            case "hiLo" -> {
                if (slot == 11) resolveHiLo(p, h, "higher");
                else if (slot == 15) resolveHiLo(p, h, "lower");
                else if (slot == 22) resolveHiLo(p, h, "cash");
            }
            case "crash" -> { if (slot == 22 && h.crashLive) { h.crashLive = false; finishNow(p, h, h.crashMult); } }
            case "slots" -> { if (slot == 13) resolveSlots(p, h); }
            case "rps" -> {
                if (slot == 11) resolveRps(p, h, 0);
                else if (slot == 13) resolveRps(p, h, 1);
                else if (slot == 15) resolveRps(p, h, 2);
            }
            case "blackjack" -> {
                if (slot == 20) resolveBj(p, h, true);
                else if (slot == 24) resolveBj(p, h, false);
            }
        }
    }

    private void startGameById(Player p, Holder h, String id) {
        if (p.getGameMode() != GameMode.SURVIVAL) {
            p.sendMessage(ChatColor.RED + "Louie only takes bets from SURVIVAL players.");
            return;
        }
        if (attemptsLeft(p.getUniqueId(), h.lucky) <= 0) {
            p.sendMessage(ChatColor.RED + "\u2620 You're out of " + (h.lucky ? "lucky " : "") + "luck! " + ChatColor.GRAY + "Refills in "
                    + nextRefillIn(p.getUniqueId(), h.lucky) + " MC days.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
            return;
        }
        if (!chargeBet(p, h)) return;
        useAttempt(p.getUniqueId(), h.lucky);
        // every game must start unsettled or resolve* no-ops immediately
        h.settled = false;
        h.busy = false;
        h.paid = false;
        h.closeTask = null;
        h.crashLive = false;
        h.revealed = 0;
        h.bet = h.lucky ? LUCKY_BETS[h.betIdx] : COIN_BETS[h.betIdx];
        h.luck = Math.max(0, stickLuck(p));
        switch (id) {
            case "cups" -> openCups(p, h);
            case "flip" -> openFlip(p, h);
            case "dice" -> openDice(p, h);
            case "wheel" -> openWheel(p, h);
            case "mines" -> openMines(p, h);
            case "hiLo" -> openHiLo(p, h);
            case "crash" -> openCrash(p, h);
            case "slots" -> openSlots(p, h);
            case "rps" -> openRps(p, h);
            case "blackjack" -> openBlackjack(p, h);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        // CRITICAL: opening a game GUI closes the main menu first. Both share the same
        // Holder; gameInv() already pointed h.inv at the NEW chest. Ignoring closes
        // that are not the active inventory stops settled=true from killing hiLo/etc.
        if (h.inv == null || e.getInventory() != h.inv) return;
        if (h.view == null || h.view.equals("main")) return;
        if (h.settled) return;

        double mult = 0;
        if (h.view.equals("mines")) {
            mult = h.revealed == 0 ? 1.0 : MINE_MULT[Math.min(Math.max(0, h.revealed - 1), MINE_MULT.length - 1)];
        } else if (h.view.equals("hiLo")) {
            mult = h.hiLoMult > 1.0 ? h.hiLoMult : 0;
        } else if (h.view.equals("crash") && h.crashLive) {
            mult = h.crashMult;
            h.crashLive = false;
        }
        // blackjack / slots / rps / cups... mid-leave = forfeit (mult 0)
        h.settled = true;
        h.busy = false;
        h.crashLive = false;
        if (mult > 0) {
            long win = h.lucky ? Math.round(h.bet * mult) : (long) Math.floor(h.bet * mult);
            if (win > 0) {
                if (h.lucky) giveLucky(p, (int) win);
                else if (econ != null) econ.depositPlayer(p, win);
                achProgress(p, h.lucky ? "luckywins" : "winnings", win);
                recordBalances(p);
                p.sendMessage(ChatColor.YELLOW + "Cashed out " + win + (h.lucky ? " \u26C0" : " \u26C3")
                        + ChatColor.GRAY + " (" + trim(mult) + "x) - walked away.");
            }
        } else if (h.bet > 0) {
            // silent forfeit — bet already taken
            p.sendMessage(ChatColor.GRAY + "Left the table. Bet stays with Louie.");
        }
    }

    /* ---------------- command ---------------- */

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        openMain(p, null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) { return List.of(); }
}
