package mavo.guide;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
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
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

public final class Guide extends JavaPlugin implements Listener {

    private NamespacedKey idKey;
    private File dataFile;
    private YamlConfiguration data;

    private static final class Holder implements InventoryHolder {
        String view = "main";          // main | tutorial | reader
        String backView = "main";      // where the reader's Back button goes
        @Override public Inventory getInventory() { return null; }
    }

    @Override
    public void onEnable() {
        idKey = new NamespacedKey(this, "guideid");
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOGuide enabled - welcome aboard.");
    }

    @Override
    public void onDisable() { try { data.save(dataFile); } catch (Exception ignored) {} }

    private String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s); }

    private ItemStack item(Material m, String name, List<String> lore, String id) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(cc(name));
        if (lore != null) {
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(cc(s));
            meta.setLore(l);
        }
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        if (id != null) meta.getPersistentDataContainer().set(idKey, PersistentDataType.STRING, id);
        it.setItemMeta(meta);
        return it;
    }

    private void fillPanes(Inventory inv) {
        ItemStack pane = item(Material.BLUE_STAINED_GLASS_PANE, " ", null, null);
        ItemStack paneR = item(Material.RED_STAINED_GLASS_PANE, " ", null, null);
        for (int i = 0; i < inv.getSize(); i++)
            if (inv.getItem(i) == null) inv.setItem(i, (i % 2 == 0) ? paneR : pane);
    }

    // ---------------- main menu ----------------
    private void openMain(Player p) {
        int version = getConfig().getInt("version", 1);
        Holder h = new Holder(); h.view = "main";
        Inventory inv = Bukkit.createInventory(h, 54,
                cc("&c&lMAVO&9&lcraft &8Guide &7- &fv" + version));

        inv.setItem(2, item(Material.WRITABLE_BOOK, "&a&l\u270E Start Here - Tutorial",
                List.of("&7New to the server? Step-by-step", "&7chapters from first login to", "&7your own claimed base.", "", "&e\u25B6 Click to open"),
                "__tutorial"));
        inv.setItem(4, item(Material.NETHER_STAR, "&c&l\u2605 What's New &7(v" + version + ")",
                List.of("&7The latest update notes.", "", "&e\u25B6 Click to read"),
                "__changelog"));
        inv.setItem(6, item(Material.FILLED_MAP, "&3&l\u2693 Live Web Map",
                List.of("&7See the world in 3D in your browser:", "&fmavocraft.my.pebble.host:8156"),
                null));

        List<Map<?, ?>> feats = getConfig().getMapList("features");
        int slot = 9;
        for (Map<?, ?> f : feats) {
            if (slot >= 53) break;
            Material icon;
            try { icon = Material.valueOf(String.valueOf(f.get("icon"))); }
            catch (Exception ex) { icon = Material.PAPER; }
            inv.setItem(slot++, item(icon, String.valueOf(f.get("name")),
                    List.of("&7" + f.get("short"), "", "&e\u25B6 Click to read the full page"),
                    "feat:" + f.get("id")));
        }

        inv.setItem(53, item(Material.BARRIER, "&cClose &7- reopen with &e/updates", null, "__close"));
        fillPanes(inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.4f);
    }

    // ---------------- tutorial menu ----------------
    private void openTutorial(Player p) {
        Holder h = new Holder(); h.view = "tutorial";
        Inventory inv = Bukkit.createInventory(h, 27, cc("&a&l\u270E Tutorial &8- pick a chapter"));
        List<Map<?, ?>> chapters = getConfig().getMapList("tutorial");
        int[] slots = {10, 11, 12, 13, 14, 15, 16, 3, 4, 5, 21, 22, 23};
        int i = 0;
        for (Map<?, ?> c : chapters) {
            if (i >= slots.length) break;
            Material icon;
            try { icon = Material.valueOf(String.valueOf(c.get("icon"))); }
            catch (Exception ex) { icon = Material.BOOK; }
            inv.setItem(slots[i], item(icon, "&f&lChapter " + (i + 1) + " &8- " + c.get("name"),
                    List.of("", "&e\u25B6 Click to read"),
                    "tut:" + i));
            i++;
        }
        inv.setItem(18, item(Material.ARROW, "&e\u25C0 Back &7- guide menu", null, "__back"));
        inv.setItem(26, item(Material.BARRIER, "&cClose", null, "__close"));
        fillPanes(inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    // ---------------- reader page (a "book page" GUI) ----------------
    private void openReader(Player p, Material icon, String title, String subtitle, List<String> lines, String backView) {
        Holder h = new Holder(); h.view = "reader"; h.backView = backView;
        Inventory inv = Bukkit.createInventory(h, 27, cc(title));
        List<String> lore = new ArrayList<>();
        if (subtitle != null && !subtitle.isBlank()) { lore.add("&7" + subtitle); lore.add(""); }
        for (String line : lines) lore.add(String.valueOf(line));
        inv.setItem(13, item(icon, title, lore, null));
        inv.setItem(18, item(Material.ARROW, "&e\u25C0 Back", null, "__back"));
        inv.setItem(26, item(Material.BARRIER, "&cClose", null, "__close"));
        fillPanes(inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    private void openFeature(Player p, String id) {
        for (Map<?, ?> f : getConfig().getMapList("features")) {
            if (!id.equals(String.valueOf(f.get("id")))) continue;
            Material icon;
            try { icon = Material.valueOf(String.valueOf(f.get("icon"))); }
            catch (Exception ex) { icon = Material.PAPER; }
            List<String> lines = new ArrayList<>();
            Object info = f.get("info");
            if (info instanceof List<?> raw) for (Object line : raw) lines.add(String.valueOf(line));
            openReader(p, icon, String.valueOf(f.get("name")), String.valueOf(f.get("short")), lines, "main");
            return;
        }
    }

    private void openChapter(Player p, int idx) {
        List<Map<?, ?>> chapters = getConfig().getMapList("tutorial");
        if (idx < 0 || idx >= chapters.size()) return;
        Map<?, ?> c = chapters.get(idx);
        Material icon;
        try { icon = Material.valueOf(String.valueOf(c.get("icon"))); }
        catch (Exception ex) { icon = Material.BOOK; }
        List<String> lines = new ArrayList<>();
        Object info = c.get("lines");
        if (info instanceof List<?> raw) for (Object line : raw) lines.add(String.valueOf(line));
        openReader(p, icon, "&f&lChapter " + (idx + 1) + " &8- " + c.get("name"), null, lines, "tutorial");
    }

    private void openChangelog(Player p) {
        int version = getConfig().getInt("version", 1);
        List<String> lines = new ArrayList<>(getConfig().getStringList("changelog"));
        openReader(p, Material.NETHER_STAR, "&c&l\u2605 What's New &7(v" + version + ")", null, lines, "main");
    }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String id = it.getItemMeta().getPersistentDataContainer().get(idKey, PersistentDataType.STRING);
        if (id == null) return;
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.5f, 1.2f);
        switch (id) {
            case "__close" -> p.closeInventory();
            case "__tutorial" -> openTutorial(p);
            case "__changelog" -> openChangelog(p);
            case "__back" -> {
                if (h.view.equals("reader") && h.backView.equals("tutorial")) openTutorial(p);
                else openMain(p);
            }
            default -> {
                if (id.startsWith("feat:")) openFeature(p, id.substring(5));
                else if (id.startsWith("tut:")) {
                    try { openChapter(p, Integer.parseInt(id.substring(4))); } catch (NumberFormatException ignored) {}
                }
            }
        }
    }

    // ---------------- auto-open on join ----------------
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!getConfig().getBoolean("auto-open", true)) return;
        Player p = e.getPlayer();
        int version = getConfig().getInt("version", 1);
        int seen = data.getInt("seen." + p.getUniqueId(), 0);
        if (seen >= version) return;
        data.set("seen." + p.getUniqueId(), version);
        try { data.save(dataFile); } catch (Exception ignored) {}
        boolean firstTime = seen == 0;
        Bukkit.getScheduler().runTaskLater(this, () -> {
            if (!p.isOnline()) return;
            if (firstTime)
                p.sendMessage(cc("&c&lWelcome to MAVO&9&lcraft&f! &7Here's everything you can do:"));
            else
                p.sendMessage(cc("&c&l\u2605 &fThe server was updated to &ev" + version + "&f! Here's what's new:"));
            openMain(p);
        }, 60L);
    }

    // ---------------- commands ----------------
    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        String name = c.getName().toLowerCase(java.util.Locale.ROOT);
        if (name.equals("updates") && a.length > 0 && a[0].equalsIgnoreCase("reload")) {
            if (!s.hasPermission("mavoguide.admin")) { s.sendMessage(ChatColor.RED + "No permission."); return true; }
            reloadConfig();
            s.sendMessage(ChatColor.GREEN + "MAVOGuide reloaded. Version is now " + getConfig().getInt("version", 1)
                    + ". Players who haven't seen this version get the popup on next join.");
            return true;
        }
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        switch (name) {
            case "tutorial" -> openTutorial(p);
            case "whatsnew" -> openChangelog(p);
            default -> openMain(p);
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (c.getName().equalsIgnoreCase("updates") && a.length == 1 && s.hasPermission("mavoguide.admin"))
            return List.of("reload");
        return List.of();
    }
}
