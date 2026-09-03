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

    /** Content lines shown per reader page (keeps tooltips on-screen). */
    private static final int PAGE_LINES = 12;
    /** Version entries shown on the first What's New page (newest 3). */
    private static final int WN_FIRST_PAGE = 3;
    /** Version entries per later What's New page. */
    private static final int WN_PAGE = 6;

    private static final class Holder implements InventoryHolder {
        String view = "main";          // main | tutorial | reader | whatsnew
        String backView = "main";      // where the reader's Back button goes
        int backPage = 0;              // whatsnew page to return to
        int page = 0;                  // reader page / whatsnew page
        List<String> lines = List.of();
        String readerTitle = "";
        Material readerIcon = Material.PAPER;
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

    private String nameOr(Map<?, ?> f, String fallback) {
        Object n = f.get("name");
        return n == null ? fallback : String.valueOf(n);
    }

    /** Safe getOrDefault for wildcard maps (Java can't infer the default type). */
    private Object mapOr(Map<?, ?> m, String key, String def) {
        Object v = m.get(key);
        return v == null ? def : v;
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
            if (slot >= 48) break;
            if ("guide".equals(String.valueOf(f.get("id")))) continue; // pinned at bottom
            if ("map".equals(String.valueOf(f.get("id")))) continue;   // pinned top
            Material icon;
            try { icon = Material.valueOf(String.valueOf(f.get("icon"))); }
            catch (Exception ex) { icon = Material.PAPER; }
            Object shrt = mapOr(f, "short", "");
            inv.setItem(slot++, item(icon, nameOr(f, "?"),
                    List.of("&7" + shrt, "", "&e\u25B6 Click to read the full page"),
                    "feat:" + f.get("id")));
        }

        // pinned: "This Guide" reminder in the bottom-middle box
        inv.setItem(49, item(Material.BOOK, "&f&lThis Guide",
                List.of("&7Reopen this guide any time:", "&e/updates &7or &e/tutorial", "", "&e\u25B6 Click to read"),
                "feat:guide"));
        inv.setItem(53, item(Material.BARRIER, "&cClose &7- reopen with &e/updates", null, "__close"));
        fillPanes(inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 0.7f, 1.4f);
    }

    // ---------------- tutorial menu (up to 21 chapters) ----------------
    private void openTutorial(Player p) {
        Holder h = new Holder(); h.view = "tutorial";
        Inventory inv = Bukkit.createInventory(h, 54, cc("&a&l\u270E Tutorial &8- pick a chapter"));
        List<Map<?, ?>> chapters = getConfig().getMapList("tutorial");
        int[] slots = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        int i = 0;
        for (Map<?, ?> c : chapters) {
            if (i >= slots.length) break;
            Material icon;
            try { icon = Material.valueOf(String.valueOf(c.get("icon"))); }
            catch (Exception ex) { icon = Material.BOOK; }
            String nm = String.valueOf(c.get("name"));
            String shortNm = nm.length() > 26 ? nm.substring(0, 25) + "\u2026" : nm;
            inv.setItem(slots[i], item(icon, "&f&lCH" + i + " &8- " + shortNm,
                    List.of("", "&e\u25B6 Click to read"),
                    "tut:" + i));
            i++;
        }
        inv.setItem(45, item(Material.ARROW, "&e\u25C0 Back &7- guide menu", null, "__back"));
        inv.setItem(53, item(Material.BARRIER, "&cClose", null, "__close"));
        fillPanes(inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    // ---------------- paged reader (never overflows the screen) ----------------
    private void openReader(Player p, Material icon, String title, String subtitle,
                            List<String> lines, String backView, int backPage, int page) {
        Holder h = new Holder(); h.view = "reader"; h.backView = backView; h.backPage = backPage;
        h.page = Math.max(0, page); h.lines = lines; h.readerTitle = title; h.readerIcon = icon;
        Inventory inv = Bukkit.createInventory(h, 27, cc(title.length() > 30 ? title.substring(0, 29) + "\u2026" : title));
        renderReader(inv, h, subtitle);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.2f);
    }

    private void renderReader(Inventory inv, Holder h, String subtitle) {
        int per = PAGE_LINES;
        int pages = Math.max(1, (h.lines.size() + per - 1) / per);
        int page = Math.min(h.page, pages - 1);
        h.page = page;
        List<String> lore = new ArrayList<>();
        lore.add("&7Page " + (page + 1) + "/" + pages);
        if (page == 0 && subtitle != null && !subtitle.isBlank()) { lore.add("&7" + subtitle); lore.add(""); }
        int start = page * per;
        int end = Math.min(h.lines.size(), start + per);
        for (int i = start; i < end; i++) lore.add(String.valueOf(h.lines.get(i)));
        inv.setItem(13, item(h.readerIcon, h.readerTitle, lore, null));
        inv.setItem(16, item(Material.ARROW, "&e\u25C0 Back", null, "__back"));
        inv.setItem(18, page > 0 ? item(Material.LIME_DYE, "&a\u25C0 Previous", null, "__rprev") : null);
        inv.setItem(22, page < pages - 1 ? item(Material.ORANGE_DYE, "&6Next \u25B6", null, "__rnext") : null);
        inv.setItem(26, item(Material.BARRIER, "&cClose", null, "__close"));
        fillPanes(inv);
    }

    private void openFeature(Player p, String id) {
        for (Map<?, ?> f : getConfig().getMapList("features")) {
            if (!id.equals(String.valueOf(f.get("id")))) continue;
            Material icon;
            try { icon = Material.valueOf(String.valueOf(f.get("icon"))); }
            catch (Exception ex) { icon = Material.PAPER; }
            String title = nameOr(f, "?");
            List<String> lines = new ArrayList<>();
            Object info = f.get("info") != null ? f.get("info") : f.get("pages");
            if (info instanceof List<?> raw) for (Object line : raw) lines.add(String.valueOf(line));
            Object shrt = mapOr(f, "short", "");
            openReader(p, icon, title, String.valueOf(shrt), lines, "main", 0, 0);
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
        openReader(p, icon, "&f&lCH" + idx + " &8- " + c.get("name"), null, lines, "tutorial", 0, 0);
    }

    // ---------------- What's New: version list menu + pager ----------------
    private List<Map<?, ?>> wnEntries() {
        List<Map<?, ?>> list = getConfig().getMapList("whatsnew");
        if (list.isEmpty()) {
            // legacy single-shot changelog -> one entry
            List<String> lines = new ArrayList<>(getConfig().getStringList("changelog"));
            Map<String, Object> one = new java.util.LinkedHashMap<>();
            one.put("title", "&c&l\u2605 What's New");
            one.put("icon", "NETHER_STAR");
            one.put("lines", lines);
            list = List.of(one);
        }
        return list;
    }

    private void openWhatsNew(Player p, int page) {
        int version = getConfig().getInt("version", 1);
        Holder h = new Holder(); h.view = "whatsnew"; h.page = page;
        Inventory inv = Bukkit.createInventory(h, 27, cc("&c&l\u2605 What's New &7(v" + version + ")"));
        renderWhatsNew(inv, h);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.1f);
    }

    private void renderWhatsNew(Inventory inv, Holder h) {
        int page = Math.max(0, h.page);
        List<Map<?, ?>> entries = wnEntries();
        int per = page == 0 ? WN_FIRST_PAGE : WN_PAGE;
        int start = page == 0 ? 0 : WN_FIRST_PAGE + (page - 1) * WN_PAGE;
        int end = Math.min(entries.size(), start + per);
        int[] slots = page == 0 ? new int[]{11, 13, 15} : new int[]{10, 11, 12, 13, 14, 15};
        int s = 0;
        for (int i = start; i < end && s < slots.length; i++, s++) {
            Map<?, ?> e = entries.get(i);
            Material icon;
            try { icon = Material.valueOf(String.valueOf(mapOr(e, "icon", "PAPER"))); }
            catch (Exception ex) { icon = Material.PAPER; }
            String title = String.valueOf(e.get("title"));
            List<String> lore = new ArrayList<>();
            Object ln = e.get("lines");
            if (ln instanceof List<?> raw && !raw.isEmpty())
                lore.add("&7" + raw.get(0));
            lore.add("");
            lore.add("&e\u25B6 Click to read the full notes");
            inv.setItem(slots[s], item(icon, title, lore, "wn:" + i));
        }
        inv.setItem(4, item(Material.ARROW, "&e\u25C0 Back &7- guide menu", null, "__back"));
        inv.setItem(18, page > 0 ? item(Material.LIME_DYE, "&a\u25C0 Newer updates", null, "__wnprev") : null);
        inv.setItem(22, end < entries.size() ? item(Material.ORANGE_DYE, "&6Older updates \u25B6", null, "__wnnext") : null);
        inv.setItem(26, item(Material.BARRIER, "&cClose", null, "__close"));
        fillPanes(inv);
    }

    private void openWhatsNewEntry(Player p, int idx, int page) {
        List<Map<?, ?>> entries = wnEntries();
        if (idx < 0 || idx >= entries.size()) return;
        Map<?, ?> e = entries.get(idx);
        Material icon;
        try { icon = Material.valueOf(String.valueOf(mapOr(e, "icon", "PAPER"))); }
        catch (Exception ex) { icon = Material.PAPER; }
        List<String> lines = new ArrayList<>();
        Object ln = e.get("lines");
        if (ln instanceof List<?> raw) for (Object line : raw) lines.add(String.valueOf(line));
        openReader(p, icon, String.valueOf(e.get("title")), null, lines, "whatsnew", page, 0);
    }

    private void openChangelog(Player p) {
        openWhatsNew(p, 0);
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
            case "__changelog" -> openWhatsNew(p, 0);
            case "__back" -> {
                if (h.view.equals("reader") && h.backView.equals("tutorial")) openTutorial(p);
                else if (h.view.equals("reader") && h.backView.equals("whatsnew")) openWhatsNew(p, h.backPage);
                else openMain(p);
            }
            case "__rprev" -> {
                if (h.view.equals("reader") && h.page > 0) {
                    h.page--;
                    renderReader(e.getInventory(), h, null);
                }
            }
            case "__rnext" -> {
                if (h.view.equals("reader")) {
                    int pages = Math.max(1, (h.lines.size() + PAGE_LINES - 1) / PAGE_LINES);
                    if (h.page < pages - 1) {
                        h.page++;
                        renderReader(e.getInventory(), h, null);
                    }
                }
            }
            case "__wnprev" -> {
                if (h.view.equals("whatsnew") && h.page > 0) {
                    h.page--;
                    renderWhatsNew(e.getInventory(), h);
                }
            }
            case "__wnnext" -> {
                if (h.view.equals("whatsnew")) {
                    int entries = wnEntries().size();
                    int start = WN_FIRST_PAGE + h.page * WN_PAGE;
                    if (start < entries) { h.page++; renderWhatsNew(e.getInventory(), h); }
                }
            }
            default -> {
                if (id.startsWith("feat:")) openFeature(p, id.substring(5));
                else if (id.startsWith("tut:")) {
                    try { openChapter(p, Integer.parseInt(id.substring(4))); } catch (NumberFormatException ignored) {}
                } else if (id.startsWith("wn:")) {
                    try { openWhatsNewEntry(p, Integer.parseInt(id.substring(3)), h.view.equals("whatsnew") ? h.page : 0); }
                    catch (NumberFormatException ignored) {}
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
