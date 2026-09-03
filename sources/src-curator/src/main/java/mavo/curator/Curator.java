package mavo.curator;

import java.io.File;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
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
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.RegisteredServiceProvider;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;

/**
 * MAVOCurator - The Museum.
 * Donate ONE of every survival-obtainable item. Grey = missing, green = exhibited.
 * Complete a section: coins = items-in-section x section-coin-per-item.
 * Global completion milestones (25/50/75/100%) fire LP rank commands.
 */
public final class Curator extends JavaPlugin implements Listener {

    private Economy econ;
    private File dataFile;
    private YamlConfiguration data;
    private NamespacedKey profLockKey, luckyKey; // never swallow bound tools / lucky coins

    /** ordered category list */
    private final LinkedHashMap<String, Cat> cats = new LinkedHashMap<>();
    /** material -> category id */
    private final Map<Material, String> matCat = new HashMap<>();
    /** buy prices copied from the normal EconomyShopGUI shops (per-player extras list) */
    private Map<String, double[]> shopPrices = new LinkedHashMap<>();
    private int totalItems = 0;

    /** cache: player -> collected material names */
    private final Map<UUID, Set<String>> collected = new HashMap<>();

    static final class Cat {
        String id, name;
        Material icon;
        List<Material> items = new ArrayList<>();
    }

    static final class Holder implements InventoryHolder {
        String view = "main";     // main | cat | vault | extras
        String catId = null;
        int page = 0;
        Inventory inv;
        @Override public @NotNull Inventory getInventory() { return inv; }
    }

    /* =================================================================
     * Category rules. First match wins. Format per row:
     *   name(&colors), ICON:MATERIAL, then patterns:
     *   EQ:NAME  END:_SUFFIX  START:PREFIX_  CONT:PART
     * ================================================================= */
    private static final String[][] RULES = {
        // ---- wearables ----
        {"&fHelmets","ICON:IRON_HELMET","END:_HELMET"},
        {"&fChestplates","ICON:IRON_CHESTPLATE","END:_CHESTPLATE"},
        {"&fLeggings","ICON:IRON_LEGGINGS","END:_LEGGINGS"},
        {"&fBoots","ICON:IRON_BOOTS","END:_BOOTS"},
        {"&6Animal Armor","ICON:DIAMOND_HORSE_ARMOR","END:_HORSE_ARMOR","EQ:WOLF_ARMOR","EQ:SADDLE"},
        {"&dGhast Harnesses","ICON:WHITE_HARNESS","END:_HARNESS"},
        {"&bShields & Wings","ICON:SHIELD","EQ:SHIELD","EQ:ELYTRA"},
        {"&eCarved Wearables","ICON:CARVED_PUMPKIN","EQ:CARVED_PUMPKIN"},
        // ---- tools & combat ----
        {"&cSwords","ICON:IRON_SWORD","END:_SWORD"},
        {"&7Pickaxes","ICON:IRON_PICKAXE","END:_PICKAXE"},
        {"&2Axes","ICON:IRON_AXE","END:_AXE"},
        {"&6Shovels","ICON:IRON_SHOVEL","END:_SHOVEL"},
        {"&eHoes","ICON:IRON_HOE","END:_HOE"},
        {"&cHeavy & Ranged Arms","ICON:BOW","EQ:BOW","EQ:CROSSBOW","EQ:TRIDENT","EQ:MACE"},
        {"&fArrows","ICON:ARROW","EQ:ARROW","EQ:SPECTRAL_ARROW","EQ:TIPPED_ARROW"},
        {"&bAdventuring Gadgets","ICON:SPYGLASS","EQ:FISHING_ROD","EQ:FLINT_AND_STEEL","EQ:SHEARS","EQ:BRUSH","EQ:SPYGLASS","EQ:COMPASS","EQ:RECOVERY_COMPASS","EQ:CLOCK","EQ:LEAD","EQ:NAME_TAG","EQ:GOAT_HORN"},
        {"&fThrowables","ICON:SNOWBALL","EQ:SNOWBALL","EQ:ENDER_PEARL","EQ:ENDER_EYE","EQ:WIND_CHARGE","EQ:FIRE_CHARGE","EQ:FIREWORK_ROCKET","EQ:FIREWORK_STAR"},
        {"&fBuckets","ICON:BUCKET","CONT:BUCKET"},
        // ---- alchemy / magic ----
        {"&dPotions","ICON:POTION","EQ:POTION","EQ:SPLASH_POTION","EQ:LINGERING_POTION"},
        {"&dBrewing Reagents","ICON:BLAZE_POWDER","EQ:NETHER_WART","EQ:BLAZE_POWDER","EQ:BLAZE_ROD","EQ:MAGMA_CREAM","EQ:FERMENTED_SPIDER_EYE","EQ:GLISTERING_MELON_SLICE","EQ:GHAST_TEAR","EQ:RABBIT_FOOT","EQ:PHANTOM_MEMBRANE","EQ:DRAGON_BREATH","EQ:BREWING_STAND","EQ:CAULDRON","EQ:BREEZE_ROD"},
        {"&dBottles","ICON:GLASS_BOTTLE","EQ:GLASS_BOTTLE","EQ:EXPERIENCE_BOTTLE","EQ:OMINOUS_BOTTLE","EQ:HONEY_BOTTLE"},
        {"&5Enchanting & Lore","ICON:ENCHANTING_TABLE","EQ:BOOK","EQ:ENCHANTED_BOOK","EQ:WRITABLE_BOOK","EQ:WRITTEN_BOOK","EQ:BOOKSHELF","EQ:CHISELED_BOOKSHELF","EQ:LECTERN","EQ:ENCHANTING_TABLE","EQ:END_CRYSTAL"},
        {"&fScribe's Supplies","ICON:MAP","EQ:PAPER","EQ:MAP","EQ:FILLED_MAP","EQ:INK_SAC","EQ:GLOW_INK_SAC","EQ:FEATHER"},
        // ---- music & art ----
        {"&aMusic Discs","ICON:MUSIC_DISC_CAT","CONT:MUSIC_DISC","EQ:DISC_FRAGMENT_5"},
        {"&aMusic Makers","ICON:JUKEBOX","EQ:JUKEBOX","EQ:NOTE_BLOCK","EQ:BELL"},
        {"&ePaintings & Frames","ICON:PAINTING","EQ:PAINTING","EQ:ITEM_FRAME","EQ:GLOW_ITEM_FRAME","EQ:ARMOR_STAND","EQ:FLOWER_POT"},
        // ---- archaeology / rare ----
        {"&6Smithing Templates","ICON:NETHERITE_UPGRADE_SMITHING_TEMPLATE","CONT:SMITHING_TEMPLATE"},
        {"&6Pottery Sherds","ICON:DECORATED_POT","CONT:_SHERD","EQ:DECORATED_POT"},
        {"&6Banner Patterns","ICON:CREEPER_BANNER_PATTERN","END:_BANNER_PATTERN"},
        {"&5Mob Heads","ICON:ZOMBIE_HEAD","END:_HEAD","END:_SKULL"},
        {"&6&lGrand Trophies","ICON:NETHER_STAR","EQ:NETHER_STAR","EQ:DRAGON_EGG","EQ:HEAVY_CORE","EQ:TOTEM_OF_UNDYING","EQ:ENCHANTED_GOLDEN_APPLE","EQ:CONDUIT","EQ:BEACON","EQ:CREAKING_HEART"},
        {"&3Precious Eggs","ICON:SNIFFER_EGG","EQ:SNIFFER_EGG","EQ:TURTLE_EGG"},
        // ---- food ----
        {"&6Golden Foods","ICON:GOLDEN_APPLE","EQ:GOLDEN_APPLE","EQ:GOLDEN_CARROT"},
        {"&cCooked Meals","ICON:COOKED_BEEF","START:COOKED_","EQ:BAKED_POTATO"},
        {"&cRaw Meats","ICON:BEEF","EQ:BEEF","EQ:PORKCHOP","EQ:MUTTON","EQ:CHICKEN","EQ:RABBIT","EQ:ROTTEN_FLESH"},
        {"&bFresh Fish","ICON:COD","EQ:COD","EQ:SALMON","EQ:TROPICAL_FISH","EQ:PUFFERFISH"},
        {"&eBakery & Sweets","ICON:CAKE","EQ:BREAD","EQ:CAKE","EQ:COOKIE","EQ:PUMPKIN_PIE","EQ:HONEYCOMB","EQ:SUGAR"},
        {"&6Stews & Soups","ICON:RABBIT_STEW","END:_STEW","END:_SOUP"},
        {"&aFruit & Veg","ICON:APPLE","EQ:APPLE","EQ:CARROT","EQ:POTATO","EQ:POISONOUS_POTATO","EQ:BEETROOT","EQ:MELON_SLICE","EQ:SWEET_BERRIES","EQ:GLOW_BERRIES","EQ:CHORUS_FRUIT","EQ:POPPED_CHORUS_FRUIT","EQ:DRIED_KELP"},
        {"&aSeeds & Starters","ICON:WHEAT_SEEDS","END:_SEEDS","EQ:WHEAT","EQ:COCOA_BEANS","EQ:SUGAR_CANE","EQ:EGG","EQ:BLUE_EGG","EQ:BROWN_EGG","EQ:NETHER_SPROUTS"},
        // ---- mob drops ----
        {"&2Monster Parts","ICON:BONE","EQ:BONE","EQ:STRING","EQ:SPIDER_EYE","EQ:GUNPOWDER","EQ:SLIME_BALL","EQ:SHULKER_SHELL","EQ:PRISMARINE_SHARD","EQ:PRISMARINE_CRYSTALS","EQ:NAUTILUS_SHELL","EQ:HEART_OF_THE_SEA","EQ:SCUTE","EQ:TURTLE_SCUTE","EQ:ARMADILLO_SCUTE","EQ:RESIN_CLUMP","EQ:LEATHER","EQ:RABBIT_HIDE","EQ:BONE_MEAL","EQ:BONE_BLOCK"},
        // ---- redstone / transport ----
        {"&cRedstone Components","ICON:REDSTONE","EQ:REDSTONE","EQ:REDSTONE_BLOCK","EQ:REDSTONE_TORCH","EQ:REPEATER","EQ:COMPARATOR","EQ:OBSERVER","EQ:PISTON","EQ:STICKY_PISTON","EQ:DISPENSER","EQ:DROPPER","EQ:HOPPER","EQ:CRAFTER","EQ:LEVER","EQ:TRIPWIRE_HOOK","EQ:DAYLIGHT_DETECTOR","EQ:REDSTONE_LAMP","EQ:TARGET","EQ:SLIME_BLOCK","EQ:HONEY_BLOCK","EQ:TNT","EQ:CALIBRATED_SCULK_SENSOR"},
        {"&7Rails & Minecarts","ICON:MINECART","CONT:MINECART","CONT:RAIL"},
        {"&6Boats & Rafts","ICON:OAK_BOAT","END:_BOAT","END:_RAFT"},
        {"&eButtons & Plates","ICON:STONE_BUTTON","END:_BUTTON","END:_PRESSURE_PLATE"},
        // ---- wooden functional ----
        {"&6Doors","ICON:OAK_DOOR","END:_DOOR"},
        {"&6Trapdoors","ICON:OAK_TRAPDOOR","END:_TRAPDOOR"},
        {"&6Fences & Gates","ICON:OAK_FENCE","END:_FENCE","END:_FENCE_GATE"},
        {"&7Walls","ICON:COBBLESTONE_WALL","END:_WALL"},
        {"&7Slabs","ICON:OAK_SLAB","END:_SLAB"},
        {"&7Stairs","ICON:OAK_STAIRS","END:_STAIRS"},
        {"&6Signs","ICON:OAK_SIGN","END:_SIGN"},
        {"&5Beds","ICON:RED_BED","END:_BED"},
        // ---- colored families ----
        {"&fWool","ICON:WHITE_WOOL","END:_WOOL"},
        {"&fCarpets","ICON:RED_CARPET","END:_CARPET"},
        {"&eBanners","ICON:RED_BANNER","END:_BANNER"},
        {"&7Concrete Powder","ICON:LIME_CONCRETE_POWDER","END:_CONCRETE_POWDER"},
        {"&7Concrete","ICON:LIME_CONCRETE","END:_CONCRETE"},
        {"&6Glazed Terracotta","ICON:ORANGE_GLAZED_TERRACOTTA","END:_GLAZED_TERRACOTTA"},
        {"&6Terracotta","ICON:TERRACOTTA","END:_TERRACOTTA","EQ:TERRACOTTA"},
        {"&bStained Glass Panes","ICON:LIGHT_BLUE_STAINED_GLASS_PANE","END:_STAINED_GLASS_PANE"},
        {"&bStained Glass","ICON:LIGHT_BLUE_STAINED_GLASS","END:_STAINED_GLASS"},
        {"&bPlain Glass","ICON:GLASS","EQ:GLASS","EQ:GLASS_PANE","EQ:TINTED_GLASS"},
        {"&eCandles","ICON:CANDLE","END:_CANDLE","EQ:CANDLE"},
        {"&dShulker Boxes","ICON:SHULKER_BOX","CONT:SHULKER_BOX"},
        {"&aDyes","ICON:LIME_DYE","END:_DYE"},
        // ---- lighting ----
        {"&eTorches & Lanterns","ICON:LANTERN","CONT:TORCH","EQ:LANTERN","EQ:SOUL_LANTERN","EQ:SEA_LANTERN","EQ:CAMPFIRE","EQ:SOUL_CAMPFIRE","EQ:GLOWSTONE","EQ:GLOWSTONE_DUST","EQ:SHROOMLIGHT","EQ:END_ROD","CONT:FROGLIGHT"},
        // ---- workstations & storage ----
        {"&6Workstations","ICON:CRAFTING_TABLE","EQ:CRAFTING_TABLE","EQ:FURNACE","EQ:BLAST_FURNACE","EQ:SMOKER","CONT:ANVIL","EQ:GRINDSTONE","EQ:SMITHING_TABLE","EQ:FLETCHING_TABLE","EQ:CARTOGRAPHY_TABLE","EQ:LOOM","EQ:STONECUTTER","EQ:COMPOSTER","EQ:SCAFFOLDING"},
        {"&6Storage","ICON:CHEST","EQ:CHEST","EQ:TRAPPED_CHEST","EQ:BARREL","EQ:ENDER_CHEST"},
        {"&aFarm & Ranch","ICON:HAY_BLOCK","EQ:HAY_BLOCK","EQ:BEEHIVE","EQ:BEE_NEST","EQ:HONEYCOMB_BLOCK","EQ:DRIED_KELP_BLOCK","EQ:LILY_PAD","EQ:PUMPKIN","EQ:MELON","EQ:JACK_O_LANTERN","EQ:COBWEB"},
        // ---- ores & minerals ----
        {"&7Ores","ICON:IRON_ORE","CONT:_ORE","EQ:ANCIENT_DEBRIS","EQ:GILDED_BLACKSTONE"},
        {"&6Raw Metals","ICON:RAW_IRON","START:RAW_"},
        {"&fIngots & Metal","ICON:IRON_INGOT","END:_INGOT","EQ:NETHERITE_SCRAP"},
        {"&eNuggets","ICON:GOLD_NUGGET","END:_NUGGET"},
        {"&bGems & Shards","ICON:DIAMOND","EQ:DIAMOND","EQ:EMERALD","EQ:LAPIS_LAZULI","EQ:QUARTZ","EQ:AMETHYST_SHARD","EQ:ECHO_SHARD","EQ:COAL","EQ:CHARCOAL","EQ:FLINT"},
        {"&bMineral Blocks","ICON:DIAMOND_BLOCK","EQ:IRON_BLOCK","EQ:GOLD_BLOCK","EQ:DIAMOND_BLOCK","EQ:EMERALD_BLOCK","EQ:NETHERITE_BLOCK","EQ:LAPIS_BLOCK","EQ:COAL_BLOCK"},
        {"&dAmethyst","ICON:AMETHYST_CLUSTER","CONT:AMETHYST"},
        {"&6Copperworks","ICON:COPPER_BLOCK","CONT:COPPER"},
        // ---- world stone families ----
        {"&3Sculk","ICON:SCULK","CONT:SCULK"},
        {"&8Deepslate","ICON:DEEPSLATE","CONT:DEEPSLATE"},
        {"&fQuartz Blocks","ICON:QUARTZ_BLOCK","CONT:QUARTZ"},
        {"&bPrismarine","ICON:PRISMARINE","CONT:PRISMARINE","EQ:DARK_PRISMARINE"},
        {"&eSandstone","ICON:SANDSTONE","CONT:SANDSTONE"},
        {"&eSands & Soils","ICON:SAND","EQ:SAND","EQ:RED_SAND","EQ:GRAVEL","EQ:CLAY","EQ:CLAY_BALL","EQ:DIRT","EQ:COARSE_DIRT","EQ:ROOTED_DIRT","EQ:PODZOL","EQ:MYCELIUM","EQ:GRASS_BLOCK","EQ:MUD","EQ:PACKED_MUD","EQ:BRICK"},
        {"&cNether Stones","ICON:NETHERRACK","CONT:NETHERRACK","CONT:NETHER_BRICK","CONT:BLACKSTONE","CONT:BASALT","EQ:MAGMA_BLOCK","EQ:SOUL_SAND","EQ:SOUL_SOIL","EQ:CRYING_OBSIDIAN","EQ:OBSIDIAN"},
        {"&dEnd Materials","ICON:END_STONE","CONT:END_STONE","CONT:PURPUR","EQ:CHORUS_FLOWER"},
        {"&7Igneous Rocks","ICON:GRANITE","CONT:GRANITE","CONT:DIORITE","CONT:ANDESITE","CONT:TUFF","EQ:CALCITE","EQ:DRIPSTONE_BLOCK","EQ:POINTED_DRIPSTONE"},
        {"&7Bricks & Masonry","ICON:BRICKS","CONT:BRICK"},
        {"&bFrozen Finds","ICON:ICE","EQ:ICE","EQ:PACKED_ICE","EQ:BLUE_ICE","EQ:SNOW","EQ:SNOW_BLOCK","EQ:POWDER_SNOW_BUCKET"},
        {"&7Stones","ICON:STONE","EQ:STONE","EQ:COBBLESTONE","EQ:MOSSY_COBBLESTONE","EQ:SMOOTH_STONE","EQ:BEDROCK"},
        // ---- flora ----
        {"&cFlowers","ICON:POPPY","EQ:POPPY","EQ:DANDELION","EQ:BLUE_ORCHID","EQ:ALLIUM","EQ:AZURE_BLUET","CONT:TULIP","EQ:OXEYE_DAISY","EQ:CORNFLOWER","EQ:LILY_OF_THE_VALLEY","EQ:WITHER_ROSE","EQ:SUNFLOWER","EQ:LILAC","EQ:ROSE_BUSH","EQ:PEONY","EQ:TORCHFLOWER","EQ:PITCHER_PLANT","EQ:PINK_PETALS","EQ:WILDFLOWERS","EQ:SPORE_BLOSSOM","EQ:CACTUS_FLOWER","EQ:EYEBLOSSOM","EQ:OPEN_EYEBLOSSOM","EQ:CLOSED_EYEBLOSSOM"},
        {"&2Saplings","ICON:OAK_SAPLING","END:_SAPLING","EQ:MANGROVE_PROPAGULE"},
        {"&2Leaves","ICON:OAK_LEAVES","END:_LEAVES"},
        {"&6Logs & Timber","ICON:OAK_LOG","END:_LOG","END:_WOOD","CONT:STRIPPED_","END:_HYPHAE","EQ:CRIMSON_STEM","EQ:WARPED_STEM","EQ:BAMBOO_BLOCK"},
        {"&6Planks","ICON:OAK_PLANKS","END:_PLANKS","EQ:BAMBOO_MOSAIC"},
        {"&dMushrooms & Fungi","ICON:RED_MUSHROOM","CONT:MUSHROOM","CONT:FUNGUS","CONT:NYLIUM","EQ:CRIMSON_ROOTS","EQ:WARPED_ROOTS","EQ:WEEPING_VINES","EQ:TWISTING_VINES"},
        {"&aMosses & Vines","ICON:MOSS_BLOCK","CONT:MOSS","EQ:VINE","EQ:GLOW_LICHEN","CONT:AZALEA","EQ:HANGING_ROOTS","EQ:BIG_DRIPLEAF","EQ:SMALL_DRIPLEAF","CONT:PALE_","EQ:BUSH","EQ:FIREFLY_BUSH","EQ:LEAF_LITTER"},
        {"&bAquatic Flora","ICON:KELP","EQ:KELP","EQ:SEAGRASS","EQ:SEA_PICKLE","EQ:BAMBOO","EQ:SPONGE","EQ:WET_SPONGE"},
        {"&bCorals","ICON:BRAIN_CORAL","CONT:CORAL"},
        {"&aGrasses & Shrubs","ICON:SHORT_GRASS","EQ:SHORT_GRASS","EQ:TALL_GRASS","EQ:FERN","EQ:LARGE_FERN","EQ:DEAD_BUSH","EQ:SHORT_DRY_GRASS","EQ:TALL_DRY_GRASS","EQ:CACTUS","EQ:SUGAR_CANE"},
        // ---- crafting bits that remain ----
        {"&fCrafting Materials","ICON:STICK","EQ:STICK","EQ:BOWL","EQ:CHAIN","EQ:IRON_BARS","EQ:LADDER","EQ:ARMADILLO_SCUTE","EQ:BRUSH","EQ:HEAVY_WEIGHTED_PRESSURE_PLATE"},
    };

    /* ---------------- lifecycle ---------------- */

    @Override
    public void onEnable() {
        saveDefaultConfig();
        profLockKey = new NamespacedKey("mavoprofessions", "proflock");
        luckyKey = new NamespacedKey("mavoluckycoins", "luckycoin");
        dataFile = new File(getDataFolder(), "data.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        RegisteredServiceProvider<Economy> rsp = getServer().getServicesManager().getRegistration(Economy.class);
        if (rsp != null) econ = rsp.getProvider();
        buildRegistry();
        refreshShopPrices();
        getServer().getPluginManager().registerEvents(this, this);
        getLogger().info("MAVOCurator enabled: " + cats.size() + " exhibit sections, " + totalItems + " collectable items.");
    }

    @Override
    public void onDisable() { save(); }

    private void save() { try { data.save(dataFile); } catch (Exception ignored) {} }

    /* ---------------- registry ---------------- */

    private boolean blacklisted(String n) {
        if (n.contains("SPAWN_EGG") || n.contains("COMMAND") || n.contains("STRUCTURE") || n.startsWith("INFESTED_")) return true;
        switch (n) {
            case "JIGSAW": case "BARRIER": case "LIGHT": case "DEBUG_STICK": case "KNOWLEDGE_BOOK":
            case "SPAWNER": case "TRIAL_SPAWNER": case "VAULT": case "END_PORTAL_FRAME":
            case "REINFORCED_DEEPSLATE": case "BUDDING_AMETHYST": case "SUSPICIOUS_SAND": case "SUSPICIOUS_GRAVEL":
            case "FARMLAND": case "DIRT_PATH": case "FROGSPAWN": case "PETRIFIED_OAK_SLAB":
            case "PLAYER_HEAD": case "CHORUS_PLANT": case "BEDROCK": case "TEST_BLOCK": case "TEST_INSTANCE_BLOCK":
                return true;
        }
        return false;
    }

    private void buildRegistry() {
        cats.clear(); matCat.clear(); totalItems = 0;
        // instantiate categories from rules
        List<Cat> order = new ArrayList<>();
        List<List<String[]>> pats = new ArrayList<>(); // parsed patterns per cat
        for (String[] row : RULES) {
            Cat c = new Cat();
            c.name = ChatColor.translateAlternateColorCodes('&', row[0]);
            c.id = ChatColor.stripColor(c.name).toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
            List<String[]> pp = new ArrayList<>();
            for (int i = 1; i < row.length; i++) {
                String[] kv = row[i].split(":", 2);
                if (kv.length != 2) continue;
                if (kv[0].equals("ICON")) { c.icon = Material.matchMaterial(kv[1]); continue; }
                pp.add(kv);
            }
            order.add(c); pats.add(pp);
        }
        Cat oddBlocks = new Cat(); oddBlocks.name = ChatColor.GRAY + "Curious Blocks"; oddBlocks.id = "curious_blocks"; oddBlocks.icon = Material.LODESTONE;
        Cat oddItems = new Cat(); oddItems.name = ChatColor.GRAY + "Curiosities"; oddItems.id = "curiosities"; oddItems.icon = Material.LODESTONE;

        for (Material m : Material.values()) {
            if (!m.isItem() || m.isAir() || m.isLegacy()) continue;
            String n = m.name();
            if (blacklisted(n)) continue;
            Cat target = null;
            outer:
            for (int i = 0; i < order.size(); i++) {
                for (String[] kv : pats.get(i)) {
                    boolean hit = switch (kv[0]) {
                        case "EQ" -> n.equals(kv[1]);
                        case "END" -> n.endsWith(kv[1]);
                        case "START" -> n.startsWith(kv[1]);
                        case "CONT" -> n.contains(kv[1]);
                        default -> false;
                    };
                    if (hit) { target = order.get(i); break outer; }
                }
            }
            if (target == null) target = m.isBlock() ? oddBlocks : oddItems;
            target.items.add(m);
            matCat.put(m, target.id);
        }
        order.add(oddBlocks); order.add(oddItems);
        for (Cat c : order) {
            if (c.items.isEmpty()) continue;
            if (c.icon == null) c.icon = c.items.get(0);
            cats.put(c.id, c);
            totalItems += c.items.size();
        }
        // catch-all needs matCat ids fixed for dropped empties (they can't be hit anyway)
    }

    /* ---------------- player data ---------------- */

    private Set<String> got(UUID u) {
        return collected.computeIfAbsent(u, k ->
                new HashSet<>(data.getStringList("players." + u + ".items")));
    }

    private boolean has(UUID u, Material m) { return got(u).contains(m.name()); }

    private int catCount(UUID u, Cat c) {
        Set<String> g = got(u);
        int n = 0;
        for (Material m : c.items) if (g.contains(m.name())) n++;
        return n;
    }

    /* ---------------- donation core ---------------- */

    /** returns true if registered as new */
    private boolean register(Player p, Material m) {
        UUID u = p.getUniqueId();
        Set<String> g = got(u);
        if (g.contains(m.name())) return false;
        g.add(m.name());
        data.set("players." + u + ".items", new ArrayList<>(g));
        save();
        achProgress(p, 1);
        checkSection(p, cats.get(matCat.get(m)));
        checkMilestones(p);
        return true;
    }

    private void checkSection(Player p, Cat c) {
        if (c == null) return;
        UUID u = p.getUniqueId();
        List<String> done = data.getStringList("players." + u + ".sections");
        if (done.contains(c.id)) return;
        if (catCount(u, c) < c.items.size()) return;
        done.add(c.id);
        data.set("players." + u + ".sections", done);
        long coins = (long) c.items.size() * getConfig().getLong("section-coin-per-item", 1000);
        if (econ != null) econ.depositPlayer(p, coins);
        save();
        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f);
        Bukkit.broadcastMessage(ChatColor.GOLD + "\u2726 " + ChatColor.AQUA + p.getName() + ChatColor.GRAY
                + " completed the " + c.name + ChatColor.GRAY + " exhibit (" + c.items.size() + " items)! "
                + ChatColor.YELLOW + "+" + String.format(Locale.UK, "%,d", coins) + " \u26C3");
    }

    private void checkMilestones(Player p) {
        UUID u = p.getUniqueId();
        int n = got(u).size();
        double pct = totalItems == 0 ? 0 : 100.0 * n / totalItems;
        List<Integer> fired = data.getIntegerList("players." + u + ".milestones");
        ConfigurationSection rw = getConfig().getConfigurationSection("completion-rewards");
        if (rw == null) return;
        boolean changed = false;
        for (String k : rw.getKeys(false)) {
            int th;
            try { th = Integer.parseInt(k); } catch (NumberFormatException ex) { continue; }
            if (pct + 1e-9 < th || fired.contains(th)) continue;
            fired.add(th); changed = true;
            for (String cmd : rw.getStringList(k))
                Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd.replace("%player%", p.getName()));
            Bukkit.broadcastMessage(ChatColor.DARK_RED + "\u2726\u2726\u2726 " + ChatColor.AQUA + p.getName()
                    + ChatColor.GOLD + " has catalogued " + ChatColor.BOLD + th + "%" + ChatColor.GOLD
                    + " of the entire Museum! " + ChatColor.DARK_RED + "\u2726\u2726\u2726");
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 0.7f);
        }
        if (changed) { data.set("players." + u + ".milestones", fired); save(); }
    }

    private void achProgress(Player p, long amount) {
        org.bukkit.plugin.Plugin pl = Bukkit.getPluginManager().getPlugin("MAVOAchievements");
        if (pl == null || !pl.isEnabled()) return;
        try {
            pl.getClass().getMethod("externalProgress", Player.class, String.class, long.class)
                    .invoke(pl, p, "museum", amount);
        } catch (Exception ignored) {}
    }

    /** true if this stack must never be donated (bound tools, lucky coins, renamed specials) */
    private boolean isProtected(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        ItemMeta m = it.getItemMeta();
        if (m.hasDisplayName()) return true; // renamed/custom items stay out of the museum
        var pdc = m.getPersistentDataContainer();
        return pdc.has(profLockKey, PersistentDataType.BYTE) || pdc.has(luckyKey, PersistentDataType.BYTE);
    }

    /** Take exactly one plain item of the material from the player's inventory. */
    private boolean takeOne(Player p, Material m) {
        ItemStack[] cont = p.getInventory().getContents();
        for (int i = 0; i < cont.length; i++) {
            ItemStack s = cont[i];
            if (s == null || s.getType() != m || isProtected(s)) continue;
            if (s.getAmount() <= 1) p.getInventory().setItem(i, null);
            else s.setAmount(s.getAmount() - 1);
            return true;
        }
        return false;
    }

    /* ---------------- GUI helpers ---------------- */

    private ItemStack item(Material m, String name, List<String> lore, boolean glow) {
        ItemStack it = new ItemStack(m);
        ItemMeta meta = it.getItemMeta();
        if (meta == null) return it;
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        if (lore != null) {
            List<String> l = new ArrayList<>();
            for (String s : lore) l.add(ChatColor.translateAlternateColorCodes('&', s));
            meta.setLore(l);
        }
        if (glow) meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(org.bukkit.inventory.ItemFlag.values());
        it.setItemMeta(meta);
        return it;
    }

    private void fill(Inventory inv) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", null, false);
        for (int i = 0; i < inv.getSize(); i++) if (inv.getItem(i) == null) inv.setItem(i, pane);
    }

    private String bar(int cur, int max) {
        int filled = max == 0 ? 20 : (int) Math.round(20.0 * cur / max);
        StringBuilder b = new StringBuilder(ChatColor.DARK_GRAY + "[" + ChatColor.GREEN);
        for (int i = 0; i < filled; i++) b.append("|");
        b.append(ChatColor.GRAY);
        for (int i = filled; i < 20; i++) b.append("|");
        return b.append(ChatColor.DARK_GRAY).append("]").toString();
    }

    private String fmt(long n) { return String.format(Locale.UK, "%,d", n); }

    /* ---------------- main menu ---------------- */

    private void openMain(Player p, int page) {
        Holder h = new Holder(); h.view = "main"; h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, ChatColor.DARK_RED + "" + ChatColor.BOLD + "\u2726 The Museum \u2726");
        h.inv = inv;
        UUID u = p.getUniqueId();
        int n = got(u).size();
        double pct = totalItems == 0 ? 0 : 100.0 * n / totalItems;
        String nextMile = "-";
        for (int th : new int[]{25, 50, 75, 100})
            if (pct < th) { nextMile = th + "% (" + fmt((long) Math.ceil(totalItems * th / 100.0) - n) + " more items)"; break; }

        inv.setItem(4, item(Material.WRITABLE_BOOK, "&6&l\u2726 Your Collection",
                List.of("&fExhibited: &a" + fmt(n) + " &7/ &f" + fmt(totalItems) + " &7(" + String.format(Locale.UK, "%.1f", pct) + "%)",
                        bar(n, totalItems),
                        "",
                        "&7Donate ONE of every item in the game.",
                        "&7Complete a section: &e1,000 \u26C3 &7per item in it!",
                        "&7Museum ranks at &f25% &7/ &f50% &7/ &f75% &7/ &6100%",
                        "&7Next rank: &e" + nextMile,
                        "",
                        "&7Green &a\u2714&7 = exhibited \u00B7 grey = still missing"), false));
        inv.setItem(8, item(Material.CHEST, "&a&l\u25A0 Deposit Crate",
                List.of("&7Open a crate, toss in ANYTHING,",
                        "&7close it - every NEW item is",
                        "&7registered, the rest comes back.",
                        "", "&e\u25B6 Click to open"), false));
        inv.setItem(52, item(Material.EMERALD, "&d&l\u2726 Museum Extras",
                List.of("&7Everything you are STILL missing,",
                        "&7per player - buy it here with coins.",
                        "&7(Buy only - no selling.)",
                        "",
                        "&e\u25B6 Click to browse your missing items"), false));

        List<Cat> list = new ArrayList<>(cats.values());
        int per = 36, from = page * per, to = Math.min(from + per, list.size());
        for (int i = from; i < to; i++) {
            Cat c = list.get(i);
            int cc = catCount(u, c), max = c.items.size();
            boolean done = cc >= max;
            String name = (done ? "&a&l\u2714 " : cc > 0 ? "&e" : "&7") + ChatColor.stripColor(c.name);
            if (done) {
                // completed section -> GREEN BOX, no longer clickable
                inv.setItem(9 + (i - from), item(Material.GREEN_STAINED_GLASS_PANE, "&a&l\u2714 " + ChatColor.stripColor(c.name),
                        List.of("&8Exhibit COMPLETE",
                                "&7Reward paid: &e" + fmt((long) max * getConfig().getLong("section-coin-per-item", 1000)) + " \u26C3",
                                "",
                                "&7(Completed sections are locked.)"), false));
                continue;
            }
            List<String> lore = new ArrayList<>();
            lore.add("&fCollected: " + (cc > 0 ? "&e" : "&7") + cc + " &7/ &f" + max);
            lore.add(bar(cc, max));
            lore.add("&7Complete reward: &e" + fmt((long) max * getConfig().getLong("section-coin-per-item", 1000)) + " \u26C3");
            lore.add("");
            lore.add("&e\u25B6 Click to browse this exhibit");
            inv.setItem(9 + (i - from), item(c.icon, name, lore, false));
        }
        int pages = (list.size() + per - 1) / per;
        if (page > 0) inv.setItem(45, item(Material.ARROW, "&e\u25C0 Previous page", null, false));
        inv.setItem(49, item(Material.BARRIER, "&cClose", List.of("&7Sections page &f" + (page + 1) + "&7/&f" + pages), false));
        if (page < pages - 1) inv.setItem(53, item(Material.ARROW, "&eNext page \u25B6", null, false));
        fill(inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    /* ---------------- category view ---------------- */

    private void openCat(Player p, String catId, int page) {
        Cat c = cats.get(catId);
        if (c == null) { openMain(p, 0); return; }
        Holder h = new Holder(); h.view = "cat"; h.catId = catId; h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, ChatColor.DARK_RED + "" + ChatColor.BOLD + "Museum \u25B8 "
                + ChatColor.RESET + ChatColor.stripColor(c.name));
        h.inv = inv;
        UUID u = p.getUniqueId();
        int cc = catCount(u, c), max = c.items.size();
        boolean done = cc >= max;

        inv.setItem(4, item(c.icon, c.name + " &7- " + (done ? "&a&lCOMPLETE" : "&e" + cc + "&7/&f" + max),
                List.of(bar(cc, max),
                        done ? "&6\u2605 Exhibit complete - reward paid!"
                             : "&7Complete ALL &f" + max + " &7for &e" + fmt((long) max * getConfig().getLong("section-coin-per-item", 1000)) + " \u26C3",
                        "",
                        "&7Click a grey item to donate 1 from",
                        "&7your inventory. Or use the crate \u2192"), done));
        inv.setItem(8, item(Material.CHEST, "&a&l\u25A0 Deposit Crate",
                List.of("&7Toss in anything, close, done.", "", "&e\u25B6 Click to open"), false));

        int per = 36, from = page * per, to = Math.min(from + per, max);
        for (int i = from; i < to; i++) {
            Material m = c.items.get(i);
            boolean have = has(u, m);
            String nice = niceName(m);
            ItemStack it = item(m, have ? "&a\u2714 " + nice : "&7" + nice,
                    have ? List.of("&a\u2714 Already donated to the museum!", "&7Do NOT add this to the crate.")
                         : List.of("&7\u2716 Not donated yet.", "", "&e\u25B6 Click to donate 1 from your inventory"),
                    have);
            inv.setItem(9 + (i - from), it);
        }
        int pages = (max + per - 1) / per;
        if (page > 0) inv.setItem(45, item(Material.ARROW, "&e\u25C0 Previous page", null, false));
        inv.setItem(48, item(Material.OAK_DOOR, "&e\u25C0 Back &7- all exhibits", null, false));
        inv.setItem(50, item(Material.BARRIER, "&cClose", List.of("&7Item page &f" + (page + 1) + "&7/&f" + Math.max(1, pages)), false));
        if (page < pages - 1) inv.setItem(53, item(Material.ARROW, "&eNext page \u25B6", null, false));
        fill(inv);
        p.openInventory(inv);
    }

    private String niceName(Material m) {
        String[] parts = m.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder b = new StringBuilder();
        for (String s : parts) {
            if (b.length() > 0) b.append(' ');
            b.append(Character.toUpperCase(s.charAt(0))).append(s.substring(1));
        }
        return b.toString();
    }

    /* ---------------- museum extras (per player, buy-only) ---------------- */

    /** Reload buy prices from the normal ESGUI shops. Matched count. */
    private int refreshShopPrices() {
        Plugin es = Bukkit.getPluginManager().getPlugin("EconomyShopGUI");
        File base = es != null ? es.getDataFolder() : new File(getDataFolder().getParentFile(), "EconomyShopGUI");
        shopPrices = loadShopPrices(new File(base, "shops"));
        return shopPrices == null ? 0 : shopPrices.size();
    }

    private double buyPrice(Material m) {
        double[] pr = shopPrices == null ? null : shopPrices.get(m.name());
        if (pr != null) return pr[0];
        return priceOf(m);
    }

    private List<Material> missingFor(UUID u) {
        List<Material> out = new ArrayList<>();
        for (Cat c : cats.values())
            for (Material m : c.items)
                if (!has(u, m)) out.add(m);
        return out;
    }

    private void openExtras(Player p, int page) {
        Holder h = new Holder(); h.view = "extras"; h.page = page;
        Inventory inv = Bukkit.createInventory(h, 54, ChatColor.DARK_RED + "" + ChatColor.BOLD + "\u2726 Museum Extras \u2726");
        h.inv = inv;
        UUID u = p.getUniqueId();
        List<Material> miss = missingFor(u);
        int per = 36, from = page * per, to = Math.min(from + per, miss.size());
        int pages = Math.max(1, (miss.size() + per - 1) / per);
        String b = p.getName();
        inv.setItem(4, item(Material.EMERALD, "&d&l\u2726 What YOU still need",
                List.of("&7Only items you have NOT donated yet.",
                        "&7Missing: &f" + miss.size() + " &7of &f" + totalItems,
                        "",
                        "&7Click an item to &ebuy 1 &7at the normal",
                        "&7shop price. Donate it afterwards \u2192",
                        "",
                        "&7Prices: " + (shopPrices == null || shopPrices.isEmpty() ? "fallback" : "real shop")), false));
        inv.setItem(8, item(Material.CHEST, "&a&l\u25A0 Deposit Crate",
                List.of("&7Toss in anything, close, done.", "", "&e\u25B6 Click to open"), false));
        for (int i = from; i < to; i++) {
            Material m = miss.get(i);
            double price = buyPrice(m);
            inv.setItem(9 + (i - from), item(m, "&7" + niceName(m),
                    List.of("&7\u2716 Missing from your collection",
                            "&eBuy: &f" + fmt(price) + " \u26C3",
                            "",
                            "&e\u25B6 Click to buy 1 and donate it"), false));
        }
        if (page > 0) inv.setItem(45, item(Material.ARROW, "&e\u25C0 Previous page", null, false));
        inv.setItem(48, item(Material.OAK_DOOR, "&e\u25C0 Back &7- museum menu", null, false));
        inv.setItem(49, item(Material.BARRIER, "&cClose", List.of("&7Missing items page &f" + (page + 1) + "&7/&f" + pages), false));
        if (page < pages - 1) inv.setItem(53, item(Material.ARROW, "&eNext page \u25B6", null, false));
        fill(inv);
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.ITEM_BOOK_PAGE_TURN, 0.8f, 1.0f);
    }

    private void buyExtras(Player p, Holder h, int slot) {
        int idx = h.page * 36 + (slot - 9);
        List<Material> miss = missingFor(p.getUniqueId());
        if (idx < 0 || idx >= miss.size()) return;
        Material m = miss.get(idx);
        if (has(p.getUniqueId(), m)) { openExtras(p, h.page); return; } // already donated: refresh
        if (econ == null) { p.sendMessage(ChatColor.RED + "Economy not available - cannot buy."); return; }
        double price = buyPrice(m);
        if (!econ.has(p, price)) {
            p.sendMessage(ChatColor.RED + "You need " + ChatColor.YELLOW + fmt(price) + " \u26C3" + ChatColor.RED + " - you have " + ChatColor.WHITE + fmt((long) econ.getBalance(p)) + " \u26C3");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.9f);
            return;
        }
        econ.withdrawPlayer(p, price);
        var left = p.getInventory().addItem(new ItemStack(m));
        if (!left.isEmpty()) {
            econ.depositPlayer(p, price);
            p.sendMessage(ChatColor.RED + "Inventory full - purchase refunded.");
            p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.9f);
            return;
        }
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.4f);
        p.sendMessage(ChatColor.GOLD + "\u2726 " + ChatColor.GREEN + "Bought " + ChatColor.WHITE + niceName(m)
                + ChatColor.GREEN + " for " + ChatColor.YELLOW + fmt(price) + " \u26C3"
                + ChatColor.GRAY + " - donate it to complete your collection!");
        openExtras(p, h.page); // refresh: nothing changed yet, but keeps view consistent
    }

    /** Removes the old static ESGUI Museum Extras (it cannot be per-player). Returns files removed. */
    private int removeEsguiMuseum(Plugin es) {
        int n = 0;
        File base = es != null ? es.getDataFolder() : new File(getDataFolder().getParentFile(), "EconomyShopGUI");
        for (String rel : new String[]{"sections/MAVOMuseum.yml", "shops/MAVOMuseum.yml"}) {
            File f = new File(base, rel);
            if (f.exists() && f.delete()) n++;
        }
        return n;
    }

    /* ---------------- deposit crate ---------------- */

    private void openVault(Player p, Holder prev) {
        Holder h = new Holder(); h.view = "vault";
        h.catId = prev != null ? prev.catId : null;
        Inventory inv = Bukkit.createInventory(h, 27, ChatColor.DARK_GREEN + "" + ChatColor.BOLD
                + "Deposit Crate " + ChatColor.RESET + ChatColor.GRAY + "- close to donate");
        h.inv = inv;
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.BLOCK_CHEST_OPEN, 0.8f, 1.1f);
    }

    private void processVault(Player p, Holder h) {
        List<String> registered = new ArrayList<>();
        List<String> dupes = new ArrayList<>();
        List<String> refused = new ArrayList<>();
        boolean survival = p.getGameMode() == GameMode.SURVIVAL;
        for (ItemStack s : h.inv.getContents()) {
            if (s == null || s.getType().isAir()) continue;
            ItemStack back = s.clone();
            if (!survival) { giveBack(p, back); continue; }
            Material m = s.getType();
            if (isProtected(s)) { refused.add(niceName(m)); giveBack(p, back); continue; }
            if (!matCat.containsKey(m)) { refused.add(niceName(m)); giveBack(p, back); continue; }
            if (has(p.getUniqueId(), m)) { dupes.add(niceName(m)); giveBack(p, back); continue; }
            // take exactly one, return the rest
            register(p, m);
            registered.add(niceName(m));
            if (back.getAmount() > 1) { back.setAmount(back.getAmount() - 1); giveBack(p, back); }
        }
        h.inv.clear();
        if (!survival) {
            p.sendMessage(ChatColor.RED + "The Curator only accepts donations from SURVIVAL players. Items returned.");
            return;
        }
        if (!registered.isEmpty()) {
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.9f, 1.5f);
            p.sendMessage(ChatColor.GOLD + "\u2726 " + ChatColor.GREEN + "Registered " + registered.size()
                    + " new exhibit(s): " + ChatColor.WHITE + String.join(ChatColor.GRAY + ", " + ChatColor.WHITE, registered));
        }
        if (!dupes.isEmpty())
            p.sendMessage(ChatColor.RED + "\u2718 Already in the museum (returned): " + ChatColor.GRAY
                    + String.join(", ", dupes));
        if (!refused.isEmpty())
            p.sendMessage(ChatColor.RED + "\u2718 The Curator refused (returned): " + ChatColor.GRAY
                    + String.join(", ", refused));
        if (registered.isEmpty() && dupes.isEmpty() && refused.isEmpty())
            p.sendMessage(ChatColor.GRAY + "Nothing donated - the crate was empty.");
    }

    private void giveBack(Player p, ItemStack s) {
        var left = p.getInventory().addItem(s);
        for (ItemStack rest : left.values()) p.getWorld().dropItemNaturally(p.getLocation(), rest);
    }

    /* ---------------- events ---------------- */

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if (!(e.getWhoClicked() instanceof Player p)) return;

        if (h.view.equals("vault")) return; // free interaction inside the crate

        e.setCancelled(true);
        if (e.getRawSlot() >= e.getInventory().getSize()) return;
        int slot = e.getRawSlot();

        if (h.view.equals("main")) {
            if (slot == 8) { openVault(p, h); return; }
            if (slot == 52) { openExtras(p, 0); return; }
            if (slot == 45) { openMain(p, Math.max(0, h.page - 1)); return; }
            if (slot == 53) { openMain(p, h.page + 1); return; }
            if (slot == 49) { p.closeInventory(); return; }
            if (slot >= 9 && slot <= 44) {
                int idx = h.page * 36 + (slot - 9);
                List<Cat> list = new ArrayList<>(cats.values());
                if (idx < list.size()) {
                    Cat c = list.get(idx);
                    if (catCount(p.getUniqueId(), c) >= c.items.size()) {
                        p.sendMessage(ChatColor.GRAY + "\u2714 " + ChatColor.stripColor(c.name)
                                + ChatColor.GRAY + " is already complete - nothing left to donate.");
                        p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
                        return;
                    }
                    openCat(p, c.id, 0);
                }
            }
            return;
        }

        if (h.view.equals("extras")) {
            if (slot == 8) { openVault(p, h); return; }
            if (slot == 45) { openExtras(p, Math.max(0, h.page - 1)); return; }
            if (slot == 53) { openExtras(p, h.page + 1); return; }
            if (slot == 48) { openMain(p, 0); return; }
            if (slot == 49) { p.closeInventory(); return; }
            if (slot >= 9 && slot <= 44) { buyExtras(p, h, slot); return; }
            return;
        }

        if (h.view.equals("cat")) {
            Cat c = cats.get(h.catId);
            if (c == null) { openMain(p, 0); return; }
            if (slot == 8) { openVault(p, h); return; }
            if (slot == 45) { openCat(p, h.catId, Math.max(0, h.page - 1)); return; }
            if (slot == 53) { openCat(p, h.catId, h.page + 1); return; }
            if (slot == 48) { openMain(p, 0); return; }
            if (slot == 50) { p.closeInventory(); return; }
            if (slot >= 9 && slot <= 44) {
                int idx = h.page * 36 + (slot - 9);
                if (idx >= c.items.size()) return;
                Material m = c.items.get(idx);
                if (has(p.getUniqueId(), m)) {
                    p.sendMessage(ChatColor.RED + "\u2718 " + niceName(m) + " is already exhibited in the museum!");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 1f);
                    return;
                }
                if (p.getGameMode() != GameMode.SURVIVAL) {
                    p.sendMessage(ChatColor.RED + "The Curator only accepts donations from SURVIVAL players.");
                    return;
                }
                if (!takeOne(p, m)) {
                    p.sendMessage(ChatColor.RED + "You don't have a plain " + ChatColor.WHITE + niceName(m)
                            + ChatColor.RED + " in your inventory.");
                    p.playSound(p.getLocation(), Sound.ENTITY_VILLAGER_NO, 1f, 0.8f);
                    return;
                }
                register(p, m);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.6f);
                p.sendMessage(ChatColor.GOLD + "\u2726 " + ChatColor.GREEN + niceName(m)
                        + " added to the museum! " + ChatColor.GRAY + "(" + got(p.getUniqueId()).size()
                        + "/" + totalItems + ")");
                openCat(p, h.catId, h.page); // refresh
            }
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Holder h)) return;
        if (!(e.getPlayer() instanceof Player p)) return;
        if (!h.view.equals("vault")) return;
        processVault(p, h);
        // reopen where they came from, next tick
        String backCat = h.catId;
        Bukkit.getScheduler().runTask(this, () -> {
            if (!p.isOnline()) return;
            if (backCat != null) openCat(p, backCat, 0);
            else if (h.view.equals("extras")) openExtras(p, h.page);
            else openMain(p, 0);
        });
    }

    /* ---------------- command ---------------- */

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length > 0 && a[0].equalsIgnoreCase("reload")) {
            if (!s.hasPermission("mavocurator.admin")) { s.sendMessage(ChatColor.RED + "No permission."); return true; }
            reloadConfig();
            buildRegistry();
            s.sendMessage(ChatColor.GREEN + "MAVOCurator reloaded: " + cats.size() + " sections, " + totalItems + " items.");
            return true;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("shopsgen")) {
            if (!s.hasPermission("mavocurator.admin")) { s.sendMessage(ChatColor.RED + "No permission."); return true; }
            Plugin es = Bukkit.getPluginManager().getPlugin("EconomyShopGUI");
            int matched = refreshShopPrices();
            int removed = removeEsguiMuseum(es);
            if (es != null) Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "sreload");
            int secs = es != null ? countYml(new File(es.getDataFolder(), "sections")) : 0;
            int shops = es != null ? countYml(new File(es.getDataFolder(), "shops")) : 0;
            s.sendMessage(ChatColor.GREEN + "Museum Extras is now PER-PLAYER and buy-only - open it with &e/museum extras&7.");
            s.sendMessage(ChatColor.GRAY + "Removed old EconomyShopGUI museum files: " + removed + "."
                    + (es != null ? " EconomyShopGUI reloaded (" + secs + " section / " + shops + " shop configs)." : ""));
            s.sendMessage(ChatColor.YELLOW + "Prices indexed from the normal shops: " + matched + " materials (museum-only items use fallback).");
            return true;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("extras")) {
            if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
            openExtras(p, 0);
            return true;
        }
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        openMain(p, 0);
        return true;
    }

    private int countYml(File dir) {
        if (dir == null || !dir.isDirectory()) return 0;
        try (java.util.stream.Stream<java.nio.file.Path> st = java.nio.file.Files.walk(dir.toPath())) {
            return (int) st.filter(java.nio.file.Files::isRegularFile)
                    .filter(x -> x.toString().toLowerCase(Locale.ROOT).endsWith(".yml")
                            || x.toString().toLowerCase(Locale.ROOT).endsWith(".yaml")).count();
        } catch (Exception ex) { return 0; }
    }

    /**
     * Reads EVERY normal EconomyShopGUI shop file (recursive, skips MAVOMuseum.yml)
     * and returns MATERIAL -> {buy, sell}. If an item is listed in several shops with
     * different prices, the CHEAPEST buy (and its sell) wins - that way the museum can
     * never be a cheaper source than the normal shop (no arbitrage either direction).
     */
    private Map<String, double[]> loadShopPrices(File shopsDir) {
        Map<String, double[]> out = new LinkedHashMap<>();
        if (shopsDir == null || !shopsDir.isDirectory()) return out;
        try (java.util.stream.Stream<java.nio.file.Path> st = java.nio.file.Files.walk(shopsDir.toPath())) {
            st.filter(java.nio.file.Files::isRegularFile)
              .filter(x -> x.toString().toLowerCase(Locale.ROOT).endsWith(".yml")
                      || x.toString().toLowerCase(Locale.ROOT).endsWith(".yaml"))
              .filter(x -> !x.getFileName().toString().equalsIgnoreCase("MAVOMuseum.yml"))
              .forEach(x -> {
                  try {
                      YamlConfiguration cfg = YamlConfiguration.loadConfiguration(x.toFile());
                      for (Map.Entry<String, Object> e : cfg.getValues(true).entrySet()) {
                          String k = e.getKey();
                          if (!k.endsWith(".material")) continue;
                          if (!(e.getValue() instanceof String mat)) continue;
                          String id = k.substring(0, k.length() - ".material".length());
                          Object bv = cfg.get(id + ".buy");
                          Object sv = cfg.get(id + ".sell");
                          if (!(bv instanceof Number buyN) || !(sv instanceof Number sellN)) continue;
                          double buy = buyN.doubleValue();
                          double sell = sellN.doubleValue();
                          double[] cur = out.get(mat);
                          if (cur == null) out.put(mat, new double[]{buy, sell});
                          else { cur[0] = Math.min(cur[0], buy); cur[1] = Math.min(cur[1], sell); }
                      }
                  } catch (Exception ignored) {}
              });
        } catch (Exception ex) { return out; }
        return out;
    }

    private static String fmt(double v) {
        if (v == Math.rint(v) && !Double.isInfinite(v) && Math.abs(v) < 1e15) return Long.toString((long) v);
        return java.math.BigDecimal.valueOf(v).stripTrailingZeros().toPlainString();
    }

    /** Rough villager-first price: common cheap, rares scaled up; sell stays 20%. */
    private int priceOf(Material m) {
        String n = m.name();
        int tier = 15;
        if (n.contains("NETHERITE") || n.equals("DRAGON_EGG") || n.equals("BEACON") || n.contains("SMITHING_TEMPLATE")
                || n.equals("NETHER_STAR") || n.equals("ELYTRA") || n.contains("TOTEM_OF_UNDYING")
                || n.equals("HEAVY_CORE") || n.equals("CREAKING_HEART"))
            tier = 4000;
        else if (n.contains("DIAMOND") || n.equals("EMERALD") || n.equals("HEART_OF_THE_SEA")
                || n.contains("_HEAD") || n.contains("_SKULL"))
            tier = 800;
        else if (n.contains("GOLD") || n.contains("ANCIENT_DEBRIS") || n.contains("SCUTE")
                || n.contains("SPONGE") || n.contains("CONDUIT") || n.contains("FROGLIGHT") || n.equals("ECHO_SHARD"))
            tier = 300;
        else if (n.contains("IRON") || n.contains("REDSTONE") || n.contains("LAPIS") || n.contains("QUARTZ")
                || n.contains("AMETHYST") || n.contains("COPPER") || n.contains("BLAZE") || n.contains("SLIME")
                || n.contains("SHULKER") || n.contains("PRISMARINE") || n.contains("EXPERIENCE"))
            tier = 80;
        else if (n.contains("GLOWSTONE") || n.contains("MAGMA") || n.contains("OBSIDIAN") || n.contains("END_")
                || n.contains("_SHERD") || n.contains("_BANNER_PATTERN"))
            tier = 40;
        return Math.max(5, tier);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1 && s.hasPermission("mavocurator.admin")) {
            List<String> out = new ArrayList<>();
            if ("reload".startsWith(a[0].toLowerCase(Locale.ROOT))) out.add("reload");
            if ("shopsgen".startsWith(a[0].toLowerCase(Locale.ROOT))) out.add("shopsgen");
            return out;
        }
        return List.of();
    }
}
