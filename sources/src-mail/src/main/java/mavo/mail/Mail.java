package mavo.mail;

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
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * MAVOMail 1.0.0 - player mail (Discord CW#3 idea 9).
 * - /mail send <player> [coins] [message...] (attaches held item when configured)
 * - /mail GUI: click to claim items, coins auto-banked, Collect All
 * - 7-day expiry, max 100 per player, offline delivery safe in data.yml
 */
public final class Mail extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;
    private int maxMail = 100;
    private long expiryMs = 7L * 24 * 3600_000L;
    private boolean attachHand = true;
    private long sendFee = 0;
    private BukkitTask cleaner;

    private static class Holder implements InventoryHolder {
        final int page; Inventory inv;
        Holder(int page) { this.page = page; }
        @Override public Inventory getInventory() { return inv; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        maxMail = Math.max(10, getConfig().getInt("max-mail", 100));
        expiryMs = Math.max(1, getConfig().getLong("expiry-days", 7)) * 24L * 3600_000L;
        attachHand = getConfig().getBoolean("attach-hand", true);
        sendFee = Math.max(0, getConfig().getLong("send-fee", 0));
        getServer().getPluginManager().registerEvents(this, this);
        cleaner = Bukkit.getScheduler().runTaskTimer(this, this::purgeExpired, 1200L, 1200L);
        getLogger().info("MAVOMail v" + getDescription().getVersion() + " enabled - max " + maxMail
                + " per player, " + (expiryMs / 86_400_000L) + " day expiry.");
    }

    @Override public void onDisable() {
        if (cleaner != null) cleaner.cancel();
        saveData();
    }
    private void saveData() { try { data.save(dataFile); } catch (Throwable ignored) { } }

    // ---------------- storage ----------------
    private List<String> keys(UUID u) {
        List<String> out = new ArrayList<>();
        ConfigurationSection s = data.getConfigurationSection("mail." + u);
        if (s != null) out.addAll(s.getKeys(false));
        return out;
    }
    private int count(UUID u) { return keys(u).size(); }
    private int unread(UUID u) {
        int n = 0;
        for (String k : keys(u)) if (!data.getBoolean("mail." + u + "." + k + ".read", false)) n++;
        return n;
    }

    private void send(Player from, UUID to, long coins, ItemStack item, String message) {
        String k = "m" + System.currentTimeMillis() + "_" + String.format("%04d", (int) (Math.random() * 10000));
        String path = "mail." + to + "." + k;
        data.set(path + ".from", from.getUniqueId().toString());
        data.set(path + ".from-name", from.getName());
        data.set(path + ".time", System.currentTimeMillis());
        data.set(path + ".coins", coins);
        data.set(path + ".message", message == null ? "" : message);
        if (item != null && item.getType() != Material.AIR) {
            ConfigurationSection sec = data.createSection(path + ".item");
            for (Map.Entry<String, Object> en : item.serialize().entrySet()) sec.set(en.getKey(), en.getValue());
        }
        data.set(path + ".read", false);
        // keep under the cap: drop the oldest
        List<String> ks = keys(to);
        while (ks.size() > maxMail) {
            data.set("mail." + to + "." + ks.remove(0), null);
        }
        saveData();
        Player online = Bukkit.getPlayer(to);
        if (online != null && online.isOnline()) {
            online.sendMessage(C + "e\u2709 " + C + "7New mail from " + C + "a" + from.getName()
                    + C + "7! (\\mail - " + unread(online.getUniqueId()) + " unread)");
            online.playSound(online.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f);
        }
    }

    private void purgeExpired() {
        long now = System.currentTimeMillis();
        boolean changed = false;
        ConfigurationSection all = data.getConfigurationSection("mail");
        if (all == null) return;
        for (String u : all.getKeys(false)) {
            for (String k : keys(UUID.fromString(u))) {
                long t = data.getLong("mail." + u + "." + k + ".time", 0);
                if (t > 0 && now - t > expiryMs) {
                    data.set("mail." + u + "." + k, null);
                    changed = true;
                }
            }
        }
        if (changed) saveData();
    }

    // ---------------- GUI ----------------
    private void open(Player p, int page) {
        UUID u = p.getUniqueId();
        List<String> ks = keys(u);
        int per = 36, pages = Math.max(1, (ks.size() + per - 1) / per);
        page = Math.max(0, Math.min(pages - 1, page));
        Holder h = new Holder(page);
        h.inv = Bukkit.createInventory(h, 54, C + "8" + C + "lMail");
        for (int i = 0; i < per; i++) {
            int idx = page * per + i;
            if (idx >= ks.size()) break;
            String k = ks.get(idx);
            String path = "mail." + u + "." + k;
            long coins = data.getLong(path + ".coins", 0);
            ItemStack item = loadItem(data.getConfigurationSection(path + ".item"));
            ItemStack icon;
            String who = data.getString(path + ".from-name", "?");
            if (item != null) icon = item.clone();
            else if (coins > 0) icon = new ItemStack(Material.GOLD_INGOT,
                    (int) Math.min(64, Math.max(1, coins / 1000)));
            else icon = new ItemStack(Material.PAPER);
            ItemMeta m = icon.getItemMeta();
            List<String> lore = new ArrayList<>();
            lore.add(C + "7From: " + C + "e" + who);
            if (coins > 0) lore.add(C + "7Coins: " + C + "a" + String.format("%,d", coins));
            if (item != null) lore.add(C + "7Item: " + C + "e" + item.getAmount() + "x "
                    + item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' '));
            String msg = data.getString(path + ".message", "");
            if (!msg.isEmpty()) lore.add(C + "8\u201c" + msg + "\u201d");
            lore.add(C + "8" + new java.text.SimpleDateFormat("dd MMM HH:mm", Locale.ENGLISH)
                    .format(new java.util.Date(data.getLong(path + ".time", 0))));
            lore.add("");
            lore.add(data.getBoolean(path + ".read", false) ? C + "8click to claim"
                    : C + "aclick to claim (NEW)");
            m.setLore(lore);
            icon.setItemMeta(m);
            h.inv.setItem(i, icon);
            data.set(path + ".read", true);
        }
        h.inv.setItem(45, gui(Material.ARROW, page > 0 ? "&a\u25c0 Previous" : "&8\u25c0 Previous"));
        h.inv.setItem(53, gui(Material.ARROW, page + 1 < pages ? "&aNext \u25b6" : "&8Next \u25b6"));
        h.inv.setItem(49, gui(Material.CHEST, "&eCollect all", "&7Items to inventory, coins banked.",
                "&7(" + count(u) + " mail, " + unread(u) + " unread)"));
        p.openInventory(h.inv);
        saveData();
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot == 45) { open(p, h.page - 1); return; }
        if (slot == 53) { open(p, h.page + 1); return; }
        if (slot == 49) { collectAll(p); return; }
        if (slot >= 36 || e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
        claimAt(p, h.page * 36 + slot);
        open(p, h.page);
    }

    private void claimAt(Player p, int idx) {
        UUID u = p.getUniqueId();
        List<String> ks = keys(u);
        if (idx < 0 || idx >= ks.size()) return;
        String k = ks.get(idx);
        String path = "mail." + u + "." + k;
        long coins = data.getLong(path + ".coins", 0);
        ItemStack item = loadItem(data.getConfigurationSection(path + ".item"));
        if (coins > 0) {
            if (econ != null && econ.depositPlayer(p, coins).transactionSuccess())
                p.sendMessage(C + "aBanked " + C + "e" + String.format("%,d", coins) + " coins" + C + "a from mail.");
        }
        if (item != null) {
            Map<Integer, ItemStack> left = p.getInventory().addItem(item);
            if (left.isEmpty()) p.sendMessage(C + "aClaimed " + C + "e" + item.getAmount() + "x "
                    + item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + C + "a from mail.");
            else {
                p.sendMessage(C + "cInventory full - item left in the mail (coins were banked).");
                return;
            }
        }
        data.set(path, null);
        saveData();
    }

    private void collectAll(Player p) {
        UUID u = p.getUniqueId();
        for (String k : new ArrayList<>(keys(u))) {
            String path = "mail." + u + "." + k;
            long coins = data.getLong(path + ".coins", 0);
            if (coins > 0 && econ != null && econ.depositPlayer(p, coins).transactionSuccess()) coins = 0;
            ItemStack item = loadItem(data.getConfigurationSection(path + ".item"));
            if (item != null) {
                Map<Integer, ItemStack> left = p.getInventory().addItem(item);
                if (!left.isEmpty()) {
                    p.sendMessage(C + "cInventory full - stopped (coins collected, items kept).");
                    saveData();
                    open(p, 0);
                    return;
                }
            }
            data.set(path, null);
        }
        saveData();
        p.sendMessage(C + "aMailbox emptied (coins banked, items in your inventory).");
        open(p, 0);
    }

    private ItemStack loadItem(ConfigurationSection sec) {
        if (sec == null) return null;
        try { return ItemStack.deserialize(new HashMap<>(sec.getValues(true))); }
        catch (Throwable t) { return null; }
    }

    // ---------------- join notification ----------------
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        int n = unread(e.getPlayer().getUniqueId());
        if (n > 0)
            e.getPlayer().sendMessage(C + "e\u2709 " + C + "7You have " + C + "a" + n + C + "7 unread mail - "
                    + C + "e\\mail" + C + "7.");
    }

    // ---------------- commands ----------------
    @Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>(List.of("send"));
            if (sender.hasPermission("mavomail.admin")) out.add("purge");
            return out;
        }
        if (args[0].equalsIgnoreCase("send") && args.length == 2)
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            if (args.length >= 2 && args[0].equalsIgnoreCase("purge") && sender.hasPermission("mavomail.admin")) {
                purge(sender, args[1]);
                return true;
            }
            sender.sendMessage("Player command only.");
            return true;
        }
        if (args.length == 0) { open(p, 0); return true; }
        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "send" -> {
                if (args.length < 2) {
                    p.sendMessage(C + "cUsage: /mail send <player> [coins] [message...]");
                    return true;
                }
                OfflinePlayer to = Bukkit.getOfflinePlayerIfCached(args[1]);
                if (to == null) to = Bukkit.getOfflinePlayer(args[1]);
                UUID tu = to.getUniqueId();
                if (tu == null || tu.equals(p.getUniqueId())) { p.sendMessage(C + "cThat player can't receive mail."); return true; }
                // 3.0.5: full mailbox = REJECT before anything is taken. 100 unclaimed mails
                // is hoarding, not a vault - and the old code silently DELETED the target's
                // oldest mail (items + coins) to make room, which was griefable.
                if (keys(tu).size() >= maxMail) {
                    p.sendMessage(C + "c" + (to.getName() == null ? args[1] : to.getName())
                            + " has " + maxMail + " unclaimed mails - they must claim some first. Nothing was taken.");
                    return true;
                }
                int idx = 2;
                long coins = 0;
                if (args.length > 2) {
                    try { coins = Long.parseLong(args[2].replace(",", "")); idx = 3; }
                    catch (Throwable ignored) { coins = 0; }
                }
                if (coins < 0) coins = 0;
                String message = String.join(" ", java.util.Arrays.copyOfRange(args, idx, args.length));
                long total = coins + sendFee;
                if (coins > 0 || sendFee > 0) {
                    if (econ == null || !econ.has(p, total)) {
                        p.sendMessage(C + "cYou need " + C + "e" + String.format("%,d", total) + " coins" + C + "c"
                                + (sendFee > 0 ? " (includes " + sendFee + " fee)" : "") + C + "c.");
                        return true;
                    }
                    if (!econ.withdrawPlayer(p, total).transactionSuccess()) {
                        p.sendMessage(C + "cPayment failed."); return true;
                    }
                }
                ItemStack hand = attachHand ? p.getInventory().getItemInMainHand() : null;
                if (hand != null && hand.getType() == Material.AIR) hand = null;
                if (hand != null) p.getInventory().setItemInMainHand(null);
                send(p, tu, coins, hand, message);
                p.sendMessage(C + "aMail sent to " + C + "e" + (to.getName() == null ? args[1] : to.getName())
                        + C + "a" + (coins > 0 ? " (" + C + "e" + String.format("%,d", coins) + " coins" + C + "a)" : "")
                        + (hand != null ? " (+ " + hand.getAmount() + "x "
                        + hand.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ') + ")" : "")
                        + C + "7." + (message.isEmpty() ? "" : " \u201c" + message + "\u201d"));
                p.playSound(p.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.3f);
            }
            case "purge" -> {
                if (!p.hasPermission("mavomail.admin")) { p.sendMessage("OP only."); return true; }
                if (args.length < 2) { p.sendMessage(C + "cUsage: /mail purge <player>"); return true; }
                purge(p, args[1]);
            }
            default -> open(p, 0);
        }
        return true;
    }

    private void purge(CommandSender sender, String name) {
        OfflinePlayer t = Bukkit.getOfflinePlayerIfCached(name);
        if (t == null) t = Bukkit.getOfflinePlayer(name);
        int n = keys(t.getUniqueId()).size();
        data.set("mail." + t.getUniqueId(), null);
        saveData();
        sender.sendMessage(C + "aPurged " + n + " mail item(s) for " + name + ".");
    }

    private static ItemStack gui(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(ChatColor.translateAlternateColorCodes('&', s));
        meta.setLore(l);
        it.setItemMeta(meta);
        return it;
    }
}
