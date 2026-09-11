package mavo.pets;

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
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.ExperienceOrb;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

/**
 * MAVOPets 2.0.0 - companions with passive abilities (Discord CW#3 idea 7).
 * - one active pet per player (buy in /pets shop, coins)
 * - follows you, cannot be damaged, levels up while active (+1 XP/min)
 * - abilities: xorb (pulls XP orbs to you), carry (one bonus slot in /pet menu)
 */
public final class Pets extends JavaPlugin implements Listener {

    private static final char C = '\u00a7';

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;
    private final NamespacedKey petKey = new NamespacedKey("mavopet", "owner");

    private int xpPerLevel = 100, maxLevel = 100, followRange = 10, xorbRadius = 8;
    private final Map<String, PetDef> defs = new HashMap<>();
    /** uuid -> the player's live pet entity */
    private final Map<UUID, LivingEntity> active = new HashMap<>();
    private BukkitTask followTask, xpTask, xorbTask;

    private record PetDef(String display, EntityType type, long price,
                          boolean xorb, boolean carry, List<String> lore) { }

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        loadDefs();
        xpPerLevel = Math.max(1, getConfig().getInt("xp-per-level", 100));
        maxLevel = Math.max(1, getConfig().getInt("max-level", 100));
        followRange = Math.max(2, getConfig().getInt("follow-range", 10));
        xorbRadius = Math.max(2, getConfig().getInt("xorb-radius", 8));
        getServer().getPluginManager().registerEvents(this, this);
        followTask = Bukkit.getScheduler().runTaskTimer(this, this::followTick, 60L, 20L);
        xpTask = Bukkit.getScheduler().runTaskTimer(this, this::xpTick, 1200L, 1200L);
        xorbTask = Bukkit.getScheduler().runTaskTimer(this, this::xorbTick, 40L, 40L);
        for (Player p : Bukkit.getOnlinePlayers()) spawnActive(p);
        getLogger().info("MAVOPets v" + getDescription().getVersion() + " enabled - " + defs.size()
                + " pet types, " + ownedCount() + " owned pet(s) total.");
    }

    @Override public void onDisable() {
        if (followTask != null) followTask.cancel();
        if (xpTask != null) xpTask.cancel();
        if (xorbTask != null) xorbTask.cancel();
        for (LivingEntity e : active.values()) e.remove();
        active.clear();
        saveData();
    }

    private void loadDefs() {
        defs.clear();
        ConfigurationSection cs = getConfig().getConfigurationSection("pets");
        if (cs == null) return;
        for (String id : cs.getKeys(false)) {
            ConfigurationSection c = cs.getConfigurationSection(id);
            if (c == null) continue;
            EntityType t;
            try { t = EntityType.valueOf(c.getString("entity", "CAT").toUpperCase(Locale.ROOT)); }
            catch (Throwable ex) { continue; }
            defs.put(id.toLowerCase(Locale.ROOT), new PetDef(
                    c.getString("display", "&e" + id), t, Math.max(0, c.getLong("price", 0)),
                    c.getBoolean("abilities.xorb", true), c.getBoolean("abilities.carry", true),
                    c.getStringList("lore")));
        }
    }
    private int ownedCount() { int n = 0; ConfigurationSection s = data.getConfigurationSection("pets2"); if (s != null) for (String k : s.getKeys(false)) { ConfigurationSection p = s.getConfigurationSection(k + ".owned"); if (p != null) n += p.getKeys(false).size(); } return n; }
    private void saveData() { try { data.save(dataFile); } catch (Throwable ignored) { } }

    // ---------------- per-player state ----------------
    private List<String> owned(UUID u) {
        List<String> out = new ArrayList<>();
        ConfigurationSection s = data.getConfigurationSection("pets2." + u + ".owned");
        if (s != null) out.addAll(s.getKeys(false));
        return out;
    }
    private boolean owns(UUID u, String id) { return owned(u).contains(id); }
    private void addPet(UUID u, String id) { data.set("pets2." + u + ".owned." + id, true); saveData(); }
    private void setActive(UUID u, String id) { data.set("pets2." + u + ".active", id); saveData(); }
    private String activeId(UUID u) { return data.getString("pets2." + u + ".active", ""); }
    private int petLevel(UUID u, String id) {
        int xp = data.getInt("pets2." + u + "." + id + ".xp", 0);
        return Math.max(1, Math.min(maxLevel, xp / xpPerLevel + 1));
    }
    private int petXp(UUID u, String id) { return data.getInt("pets2." + u + "." + id + ".xp", 0); }
    private void addPetXp(UUID u, String id, int n) {
        int before = petLevel(u, id);
        int xp = Math.min(petXp(u, id) + n, maxLevel * xpPerLevel);
        data.set("pets2." + u + "." + id + ".xp", xp);
        saveData();
        int after = petLevel(u, id);
        if (after > before) {
            Player p = Bukkit.getPlayer(u);
            if (p != null && p.isOnline()) {
                p.sendMessage(C + "d" + cc(defs.get(id).display) + C + "d reached level "
                        + C + "e" + after + C + "d!");
                LivingEntity e = active.get(u);
                if (e != null) rename(e, u, id);
            }
        }
    }
    private boolean abilityOn(UUID u, String id, String ab) {
        return data.getBoolean("pets2." + u + "." + id + "." + ab, true);
    }
    private void setAbility(UUID u, String id, String ab, boolean on) {
        data.set("pets2." + u + "." + id + "." + ab, on); saveData();
    }

    private void saveCarry(UUID u, String id, ItemStack it) {
        ConfigurationSection sec = data.createSection("pets2." + u + "." + id + ".carry");
        if (it == null || it.getType() == Material.AIR) { data.set("pets2." + u + "." + id + ".carry", null); saveData(); return; }
        for (String k : sec.getKeys(false)) sec.set(k, null);
        for (Map.Entry<String, Object> en : it.serialize().entrySet()) sec.set(en.getKey(), en.getValue());
        saveData();
    }
    private ItemStack loadCarry(UUID u, String id) {
        ConfigurationSection sec = data.getConfigurationSection("pets2." + u + "." + id + ".carry");
        if (sec == null) return null;
        try { return ItemStack.deserialize(new HashMap<>(sec.getValues(true))); }
        catch (Throwable t) { return null; }
    }

    // ---------------- spawning / following ----------------
    private void spawnActive(Player p) {
        removePet(p.getUniqueId());
        String id = activeId(p.getUniqueId());
        if (id.isEmpty() || !defs.containsKey(id)) return;
        PetDef d = defs.get(id);
        World w = p.getWorld();
        Location loc = p.getLocation().add(p.getLocation().getDirection().multiply(-1.2)).add(0, 0, 0);
        LivingEntity e = (LivingEntity) w.spawnEntity(loc, d.type);
        e.setCustomName(colorName(p.getUniqueId(), id));
        e.setCustomNameVisible(true);
        // 3.0.6: AI ON so pets look alive (wander, idle, pathfind) - they stay
        // invulnerable + damage-cancelled + non-collidable, so still untouchable.
        e.setAI(true);
        e.setInvulnerable(true);
        e.setSilent(true);
        e.setPersistent(true);
        e.setCanPickupItems(false);
        e.setCollidable(false);
        e.setGravity(true);
        e.getPersistentDataContainer().set(petKey, PersistentDataType.STRING, p.getUniqueId().toString());
        active.put(p.getUniqueId(), e);
    }
    private void removePet(UUID u) {
        LivingEntity e = active.remove(u);
        if (e != null) e.remove();
    }
    private void rename(LivingEntity e, UUID u, String id) {
        e.setCustomName(colorName(u, id));
    }
    private String colorName(UUID u, String id) {
        PetDef d = defs.get(id);
        return C + "7[Lv " + petLevel(u, id) + "] " + cc(d == null ? id : d.display);
    }
    private void followTick() {
        for (Map.Entry<UUID, LivingEntity> en : new ArrayList<>(active.entrySet())) {
            Player p = Bukkit.getPlayer(en.getKey());
            LivingEntity e = en.getValue();
            if (p == null || !p.isOnline() || e == null || e.isDead()
                    || !e.getWorld().equals(p.getWorld())) {
                if (e != null) e.remove();
                active.remove(en.getKey());
                if (p != null && p.isOnline()) spawnActive(p);
                continue;
            }
            double dist = e.getLocation().distanceSquared(p.getLocation());
            if (dist > followRange * (double) followRange) {
                Location to = p.getLocation().add(p.getLocation().getDirection().multiply(-1.2));
                e.teleport(to);
            } else if (dist > 2.5) {
                e.setVelocity(p.getLocation().toVector().subtract(e.getLocation().toVector())
                        .normalize().multiply(0.35));
            }
        }
    }
    private void xpTick() {
        for (UUID u : new ArrayList<>(active.keySet())) {
            Player p = Bukkit.getPlayer(u);
            String id = activeId(u);
            if (p != null && p.isOnline() && !id.isEmpty() && defs.containsKey(id)
                    && p.getGameMode() == org.bukkit.GameMode.SURVIVAL)
                addPetXp(u, id, 1);
        }
    }
    private void xorbTick() {
        for (Map.Entry<UUID, LivingEntity> en : active.entrySet()) {
            Player p = Bukkit.getPlayer(en.getKey());
            if (p == null || !p.isOnline()) continue;
            String id = activeId(p.getUniqueId());
            if (id.isEmpty() || !defs.containsKey(id) || !abilityOn(p.getUniqueId(), id, "xorb")) continue;
            for (Entity en2 : p.getNearbyEntities(xorbRadius, 4, xorbRadius)) {
                if (en2 instanceof ExperienceOrb orb) {
                    World w = orb.getWorld();
                    if (!w.equals(p.getWorld())) continue;
                    Location target = p.getLocation();
                    w.spawnParticle(org.bukkit.Particle.HAPPY_VILLAGER, orb.getLocation(), 3, 0.1, 0.1, 0.1, 0.01);
                    orb.setVelocity(target.toVector().subtract(orb.getLocation().toVector())
                            .normalize().multiply(0.55));
                }
            }
        }
    }

    // ---------------- safety ----------------
    @EventHandler public void onHurt(EntityDamageEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(petKey, PersistentDataType.STRING)) e.setCancelled(true);
    }
    @EventHandler public void onDeath(EntityDeathEvent e) {
        if (e.getEntity().getPersistentDataContainer().has(petKey, PersistentDataType.STRING)) {
            e.setDroppedExp(0); e.getDrops().clear();
        }
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) { spawnActive(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { removePet(e.getPlayer().getUniqueId()); }

    // ---------------- pet menu ----------------
    private static class Holder implements InventoryHolder {
        final String kind; final String id; Inventory inv;
        Holder(String kind, String id) { this.kind = kind; this.id = id; }
        @Override public Inventory getInventory() { return inv; }
    }

    private void openMenu(Player p) {
        UUID u = p.getUniqueId();
        String id = activeId(u);
        if (id.isEmpty() || !owns(u, id)) { p.sendMessage(C + "cPick an active pet first - " + C + "e/pets"); return; }
        PetDef d = defs.get(id);
        Holder h = new Holder("menu", id);
        h.inv = Bukkit.createInventory(h, 27, cc(d.display) + C + "8 menu");
        ItemStack carry = loadCarry(u, id);
        h.inv.setItem(10, carry != null ? carry : gui(Material.CHEST_MINECART, "&eCarry slot (empty)",
                "&7Click an item here to store it.", "&7Shift+click it to take it back.", "&7One stack, one slot."));
        h.inv.setItem(12, gui(Material.EXPERIENCE_BOTTLE, "&bPet XP",
                "&7Level: &e" + petLevel(u, id) + "&7/" + maxLevel,
                "&7XP: &e" + petXp(u, id) + "&7/" + (petLevel(u, id) * xpPerLevel),
                "&7+1 XP per active minute (survival)."));
        h.inv.setItem(14, gui(Material.DIAMOND, "&dAbilities",
                "&7xorb: " + (abilityOn(u, id, "xorb") ? "&aON" : "&cOFF") + "&7 - pulls XP orbs to you",
                "&7carry: " + (abilityOn(u, id, "carry") ? "&aON" : "&cOFF") + "&7 - this menu's bag",
                "&7(click &dDiamond&7 to toggle xorb, &dGold Ingot&7 for carry)"));
        h.inv.setItem(16, gui(Material.COMPASS, "&aRecall", "&7Teleport your pet back to you."));
        h.inv.setItem(22, gui(Material.CHEST, "&e/ p e t s", "&7Switch pets or buy more."));
        h.inv.setItem(24, gui(Material.RED_BED, "&cRest", "&7Send your pet away.", "&7Pick it again in /pets anytime."));
        p.openInventory(h.inv);
    }

    private void openShop(Player p) {
        UUID u = p.getUniqueId();
        Holder h = new Holder("shop", null);
        Inventory inv = Bukkit.createInventory(h, 54, C + "8" + C + "lPet Shop");
        int slot = 0;
        for (String id : defs.keySet()) {
            if (slot >= 45) break;
            PetDef d = defs.get(id);
            ItemStack it = new ItemStack(iconOf(d.type));
            ItemMeta m = it.getItemMeta();
            m.setDisplayName(cc(d.display));
            List<String> lore = new ArrayList<>();
            for (String s : d.lore) lore.add(cc(s));
            lore.add("");
            if (owns(u, id)) {
                boolean act = id.equals(activeId(u));
                lore.add(act ? C + "a\u2714 ACTIVE" : C + "eClick to activate");
                lore.add(C + "7Lv " + petLevel(u, id) + " \u00b7 " + petXp(u, id) + " XP");
            } else {
                lore.add(C + "6Price: " + C + "e" + String.format("%,d", d.price) + " coins"
                        + (d.price > 0 && econ != null && econ.has(p, d.price) ? C + "a (you can afford it)" : ""));
                lore.add(C + "aClick to buy & activate");
            }
            lore.add("");
            lore.add(C + "7Abilities: " + (d.xorb ? "&axorb " : "&8xorb ")
                    + (d.carry ? "&acarries 1 stack" : "&8no carry"));
            m.setLore(lore);
            it.setItemMeta(m);
            inv.setItem(slot++, it);
        }
        inv.setItem(49, gui(Material.HOPPER, "&eActive pet: &f" + cc(defs.get(activeId(u)).display),
                "&7/&epet&7 opens its menu (carry slot + toggles)."));
        p.openInventory(inv);
    }

    private static Material iconOf(EntityType t) {
        // rough icon fallback; real pets spawn correctly regardless
        try { return switch (t) {
            case CAT, OCELOT -> Material.ORANGE_DYE;
            case WOLF -> Material.BONE;
            case FOX -> Material.SWEET_BERRIES;
            case PARROT -> Material.FEATHER;
            case AXOLOTL -> Material.TROPICAL_FISH;
            case TURTLE -> Material.TURTLE_SCUTE;
            default -> Material.BONE;
        }; } catch (Throwable ex) { return Material.BONE; }
    }

    @EventHandler
    public void onClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof Holder h) || !"menu".equals(h.kind)) return;
        e.setCancelled(true);
        UUID u = p.getUniqueId();
        String id = h.id;
        int slot = e.getRawSlot();
        if (slot == 10) {
            ItemStack cur = e.getCursor();
            ItemStack stored = loadCarry(u, id);
            if (stored != null && (cur == null || cur.getType() == Material.AIR)) {
                saveCarry(u, id, null);
                p.setItemOnCursor(stored);
                openMenu(p);
            } else if (cur != null && cur.getType() != Material.AIR) {
                int amt = cur.getAmount();
                ItemStack keep = cur.clone(); keep.setAmount(amt - 1);
                ItemStack one = cur.clone(); one.setAmount(1);
                if (stored != null) { p.sendMessage(C + "cCarry slot is full."); return; }
                p.setItemOnCursor(keep.getAmount() > 0 ? keep : null);
                saveCarry(u, id, one);
                openMenu(p);
            }
        } else if (slot == 14) {
            boolean on = !abilityOn(u, id, "xorb");
            setAbility(u, id, "xorb", on);
            p.sendMessage(C + "dXorb " + (on ? C + "aON" : C + "cOFF"));
            openMenu(p);
        } else if (slot == 16) {
            LivingEntity pet = active.get(u);
            if (pet != null) pet.teleport(p.getLocation());
            p.sendMessage(C + "aPet recalled.");
        } else if (slot == 24) {
            // 3.0.6: Rest = deactivate (re-pick in /pets anytime)
            setActive(u, "");
            removePet(u);
            p.closeInventory();
            p.sendMessage(C + "7Your pet rests. Pick it again in " + C + "e/pets" + C + "7 anytime.");
        } else p.closeInventory();
    }

    @EventHandler
    public void onRightClickPet(PlayerInteractEntityEvent e) {
        if (!(e.getRightClicked() instanceof LivingEntity pet)) return;
        if (!pet.getPersistentDataContainer().has(petKey, PersistentDataType.STRING)) return;
        e.setCancelled(true);
        String owner = pet.getPersistentDataContainer().get(petKey, PersistentDataType.STRING);
        if (owner != null && owner.equals(e.getPlayer().getUniqueId().toString())) openMenu(e.getPlayer());
    }

    // ---------------- commands ----------------
    @Override public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("pet") && args.length == 1)
            return List.of("menu", "recall", "xp", "off");
        if (cmd.getName().equalsIgnoreCase("pet") && args.length == 2 && args[0].equalsIgnoreCase("give"))
            return new ArrayList<>(defs.keySet());
        return List.of();
    }

    @Override public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (cmd.getName().equalsIgnoreCase("pets")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            openShop(p);
            return true;
        }
        if (cmd.getName().equalsIgnoreCase("pet")) {
            if (!(sender instanceof Player p)) { sender.sendMessage("Players only."); return true; }
            String sub = args.length == 0 ? "menu" : args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "menu" -> openMenu(p);
                case "recall" -> {
                    LivingEntity pet = active.get(p.getUniqueId());
                    if (pet == null) p.sendMessage(C + "cNo active pet.");
                    else { pet.teleport(p.getLocation()); p.sendMessage(C + "aPet recalled."); }
                }
                case "xp" -> {
                    String id = activeId(p.getUniqueId());
                    if (id.isEmpty()) p.sendMessage(C + "cNo active pet.");
                    else p.sendMessage(C + "d" + cc(defs.get(id).display) + C + "d is level "
                            + C + "e" + petLevel(p.getUniqueId(), id) + C + "d (" + petXp(p.getUniqueId(), id)
                            + " XP" + C + "d). +1 per active minute.");
                }
                case "give" -> {
                    if (!sender.hasPermission("mavopet.admin")) { p.sendMessage("OP only."); return true; }
                    if (args.length < 3) { p.sendMessage(C + "cUsage: /pet give <player> <pet>"); return true; }
                    Player target = Bukkit.getPlayerExact(args[1]);
                    String id = args[2].toLowerCase(Locale.ROOT);
                    if (target == null) { p.sendMessage(C + "cPlayer offline."); return true; }
                    if (!defs.containsKey(id)) { p.sendMessage(C + "cNo such pet: " + args[2]); return true; }
                    addPet(target.getUniqueId(), id);
                    setActive(target.getUniqueId(), id);
                    spawnActive(target);
                    p.sendMessage(C + "aGave " + target.getName() + " a " + cc(defs.get(id).display) + C + "a.");
                }
                case "off", "rest" -> {
                    if (activeId(p.getUniqueId()).isEmpty())
                        p.sendMessage(C + "7No active pet.");
                    else {
                        setActive(p.getUniqueId(), "");
                        removePet(p.getUniqueId());
                        p.sendMessage(C + "7Your pet rests. Pick it again in " + C + "e/pets" + C + "7 anytime.");
                    }
                }
                default -> p.sendMessage(C + "7/pet menu | recall | xp | off");
            }
            return true;
        }
        return false;
    }

    // ---------------- shop clicks ----------------
    @EventHandler
    public void onShopClick(org.bukkit.event.inventory.InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof Holder h) || !"shop".equals(h.kind)) return;
        if (e.getClickedInventory() == null || !e.getClickedInventory().equals(e.getView().getTopInventory())) return;
        if (e.getCurrentItem() == null || e.getCurrentItem().getType() == Material.AIR) return;
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot >= 45) { p.closeInventory(); return; }
        List<String> ids = new ArrayList<>(defs.keySet());
        if (slot >= ids.size()) return;
        String id = ids.get(slot);
        UUID u = p.getUniqueId();
        if (owns(u, id)) {
            setActive(u, id);
            spawnActive(p);
            p.sendMessage(C + "a" + cc(defs.get(id).display) + C + "a is now your active pet.");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.4f);
        } else {
            PetDef d = defs.get(id);
            if (d.price > 0 && (econ == null || !econ.has(p, d.price))) {
                p.sendMessage(C + "cYou need " + C + "e" + String.format("%,d", d.price) + " coins" + C + "c.");
                return;
            }
            if (d.price > 0 && econ != null) econ.withdrawPlayer(p, d.price);
            addPet(u, id);
            setActive(u, id);
            spawnActive(p);
            p.sendMessage(C + "aBought " + cc(d.display) + C + "a for " + C + "e" + String.format("%,d", d.price)
                    + " coins" + C + "a - now your active pet!");
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.4f);
        }
        openShop(p);
    }

    private static ItemStack gui(Material m, String name, String... lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(cc(name));
        List<String> l = new ArrayList<>();
        for (String s : lore) l.add(cc(s));
        meta.setLore(l);
        it.setItemMeta(meta);
        return it;
    }
    private static String cc(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
