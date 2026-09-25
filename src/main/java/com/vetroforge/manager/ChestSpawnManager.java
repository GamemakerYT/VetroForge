// =======================================================
// File: src/main/java/com/vetroforge/manager/ChestSpawnManager.java
// =======================================================
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.lootdrop.LootItemFactory;
import com.vetroforge.skills.SkillType;
import com.vetroforge.warzone.WarzoneRegionManager;
import com.vetroforge.stats.StatsManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.potion.PotionType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Steuert Warzone/FlagCapture-Kisten:
 * - Admin setzt Spawnpunkte (/chest add/remove/list)
 * - Spawnt periodisch Chests mit Loot nach gewichteten Chancen (COMMON/RARE/EPIC)
 * - Despawn, wenn leer, danach Respawn pro Spawnpunkt
 * - Level-XP beim ersten Öffnen
 */
public class ChestSpawnManager {

    public enum Category { WARZONE, FLAG_CAPTURE }

    // === internes Rarity-Tier für die Kisten (leichtgewichtig) ===
    private enum Rarity { COMMON, RARE, EPIC }

    /** aktiver Kisten-Eintrag */
    private static final class ActiveChest {
        final String spawnId;
        final Location loc;
        final Category cat;
        long firstOpenAt = 0L; // 0 = ungeöffnet
        ActiveChest(String spawnId, Location loc, Category cat){ this.spawnId = spawnId; this.loc = loc; this.cat = cat; }
    }

    // Ein Loot-Eintrag für Tier-Drops
    private static final class LootEntry {
        final ItemStack proto; final int min; final int max; final double weight;
        LootEntry(Material mat, int min, int max, double weight){ this(new ItemStack(mat), min, max, weight); }
        LootEntry(ItemStack proto, int min, int max, double weight){
            this.proto = proto; this.min = min; this.max = max; this.weight = weight;
        }
        ItemStack roll(){
            ItemStack it = proto.clone();
            int amount = (min == max) ? min : ThreadLocalRandom.current().nextInt(min, max+1);
            it.setAmount(Math.max(1, Math.min(amount, it.getMaxStackSize())));
            return it;
        }
    }

    private final FactionsPlugin plugin;
    private final WarzoneRegionManager warzone;
    private final StatsManager stats;

    private final Set<SpawnPoint> spawnPoints = new HashSet<>();
    private final Map<Location, ActiveChest> active = new HashMap<>();
    private final Map<String, Long> nextSpawnAt = new HashMap<>();

    // Tiered Pools
    private final List<LootEntry> commonPool = new ArrayList<>();
    private final List<LootEntry> rarePool   = new ArrayList<>();
    private final List<LootEntry> epicPool   = new ArrayList<>();

    private BukkitTask loop;
    private int spawnEverySeconds = 120;
    private int maxActiveChests   = 8;
    private int openDespawnSeconds= 0;
    private int rollsPerChest     = 6; // Obergrenze; echte Kisten rollen zufällig darunter
    private int minRollsPerChest  = 2;

    private final File dataFile;

    /* ====== Modell SpawnPoint ====== */
    public static final class SpawnPoint {
        public final String id;
        public final String world;
        public final int x, y, z;
        public final Category cat;

        public SpawnPoint(String id, String world, int x, int y, int z, Category cat) {
            this.id = (id == null || id.isBlank()) ? "spot" : id.toLowerCase(Locale.ROOT);
            this.world = world; this.x = x; this.y = y; this.z = z; this.cat = cat;
        }

        public SpawnPoint(String world, int x, int y, int z, Category cat) {
            this(null, world, x, y, z, cat);
        }
        public Location toLocation() {
            World w = Bukkit.getWorld(world);
            if (w == null) return null;
            return new Location(w, x, y, z);
        }

        // Damit HashSet.contains/equals funktioniert (removeNearest etc.)
        @Override public boolean equals(Object o){
            if (!(o instanceof SpawnPoint sp)) return false;
            return Objects.equals(id, sp.id);
        }
        @Override public int hashCode(){ return Objects.hash(id); }
    }

    public static final class DoctorEntry {
        public final SpawnPoint point;
        public final boolean worldLoaded;
        public final boolean chunkLoaded;
        public final boolean inWarzone;
        public final boolean safeBlock;
        public final String issue;

        DoctorEntry(SpawnPoint point, boolean worldLoaded, boolean chunkLoaded, boolean inWarzone, boolean safeBlock, String issue) {
            this.point = point;
            this.worldLoaded = worldLoaded;
            this.chunkLoaded = chunkLoaded;
            this.inWarzone = inWarzone;
            this.safeBlock = safeBlock;
            this.issue = issue;
        }

        public boolean ok() {
            return worldLoaded && chunkLoaded && inWarzone && safeBlock;
        }
    }

    public ChestSpawnManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.warzone = plugin.getWarzoneRegionManager();
        this.stats = plugin.getStatsManager();
        this.dataFile = new File(plugin.getDataFolder(), "data/chest-spawns.yml");

        loadConfig();
        seedTieredLoot();       // ← NEU: Common/Rare/Epic Pools
        loadSpawnPoints();
        start();
    }

    /* ================= Lifecycle ================= */

    public void start() {
        stop();
        loop = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            try { tick(); } catch (Throwable ignored) {}
        }, 40L, 20L * 10L);
    }

    public void stop() {
        if (loop != null) try { loop.cancel(); } catch (Throwable ignored) {}
        loop = null;
    }

    public void restart() {
        stop();
        start();
    }

    public void reload() {
        loadConfig();
        loadSpawnPoints();
    }

    public int clearActive() {
        List<Location> locations = new ArrayList<>(active.keySet());
        for (Location location : locations) {
            despawn(location, false);
        }
        return locations.size();
    }

    public int activeCount() {
        return active.size();
    }

    public void save() {
        org.bukkit.configuration.file.FileConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
        java.util.List<Map<String, Object>> list = new ArrayList<>();
        for (SpawnPoint sp : spawnPoints) {
            Map<String,Object> m = new HashMap<>();
            m.put("id", sp.id);
            m.put("world", sp.world);
            m.put("x", sp.x); m.put("y", sp.y); m.put("z", sp.z);
            m.put("cat", sp.cat.toString());
            list.add(m);
        }
        cfg.set("spawns", list);
        try { cfg.save(dataFile); } catch (Exception ignored) {}
    }

    /* ================= Admin API ================= */

    public boolean addSpawn(Location loc, Category cat) {
        return setSpawn(String.valueOf(spawnPoints.size() + 1), loc, cat);
    }

    public boolean setSpawn(String id, Location loc, Category cat) {
        if (loc == null || loc.getWorld() == null) return false;
        if (warzone != null && !warzone.isWarzone(loc)) return false;
        SpawnPoint sp = new SpawnPoint(id, loc.getWorld().getName(), loc.getBlockX(), loc.getBlockY(), loc.getBlockZ(), cat);
        spawnPoints.remove(sp);
        spawnPoints.add(sp);
        nextSpawnAt.put(sp.id, System.currentTimeMillis() + spawnEverySeconds * 1000L);
        save();
        return true;
    }

    public boolean removeNearest(Location loc, double radius) {
        SpawnPoint best = null;
        double bestD2 = radius * radius;
        for (SpawnPoint sp : spawnPoints) {
            if (!sp.world.equals(loc.getWorld().getName())) continue;
            double dx = (sp.x + 0.5) - loc.getX();
            double dy = (sp.y + 0.5) - loc.getY();
            double dz = (sp.z + 0.5) - loc.getZ();
            double d2 = dx*dx + dy*dy + dz*dz;
            if (d2 <= bestD2) { bestD2 = d2; best = sp; }
        }
        if (best != null) {
            spawnPoints.remove(best);
            save();
            return true;
        }
        return false;
    }

    public boolean removeById(String id) {
        SpawnPoint point = findSpawn(id);
        if (point == null) return false;
        spawnPoints.remove(point);
        nextSpawnAt.remove(point.id);
        Location location = point.toLocation();
        if (location != null && active.containsKey(location)) {
            despawn(location, false);
        }
        save();
        return true;
    }

    public SpawnPoint findSpawn(String id) {
        if (id == null || id.isBlank()) return null;
        String clean = id.toLowerCase(Locale.ROOT);
        for (SpawnPoint point : spawnPoints) {
            if (point.id.equalsIgnoreCase(clean)) return point;
        }
        return null;
    }

    public List<SpawnPoint> list() { return new ArrayList<>(spawnPoints); }

    public int forceSpawn(int count) {
        int spawned = 0;
        for (int i=0;i<count;i++) {
            if (spawnOne(true)) spawned++; else break;
        }
        return spawned;
    }

    public List<DoctorEntry> doctor() {
        List<DoctorEntry> entries = new ArrayList<>();
        for (SpawnPoint point : list()) {
            World world = Bukkit.getWorld(point.world);
            boolean worldLoaded = world != null;
            Location location = point.toLocation();
            boolean chunkLoaded = location != null && location.getWorld() != null && location.getChunk().isLoaded();
            boolean inWarzone = location != null && (warzone == null || warzone.isWarzone(location));
            boolean safeBlock = location != null && isSafeSpawnBlock(location.getBlock());
            String issue = "OK";
            if (!worldLoaded) issue = "Welt nicht geladen";
            else if (!chunkLoaded) issue = "Chunk nicht geladen";
            else if (!inWarzone) issue = "Nicht in der Warzone / im Spawn-Cutout";
            else if (!safeBlock) issue = "Block ist belegt: " + location.getBlock().getType().name();
            entries.add(new DoctorEntry(point, worldLoaded, chunkLoaded, inWarzone, safeBlock, issue));
        }
        entries.sort(Comparator.comparing(entry -> entry.point.id, String.CASE_INSENSITIVE_ORDER));
        return entries;
    }

    public int repairAllSpawnPoints() {
        int fixed = 0;
        for (SpawnPoint point : new ArrayList<>(spawnPoints)) {
            if (repairSpawn(point.id)) fixed++;
        }
        return fixed;
    }

    public boolean repairSpawn(String id) {
        SpawnPoint point = findSpawn(id);
        if (point == null) return false;
        Location current = point.toLocation();
        if (current == null || current.getWorld() == null) return false;
        if (warzone != null && !warzone.isWarzone(current)) return false;
        if (isSafeSpawnBlock(current.getBlock())) return false;

        for (int dy = 1; dy <= 8; dy++) {
            Location candidate = current.clone().add(0, dy, 0);
            if (warzone != null && !warzone.isWarzone(candidate)) continue;
            if (!isSafeSpawnBlock(candidate.getBlock())) continue;
            spawnPoints.remove(point);
            SpawnPoint repaired = new SpawnPoint(point.id, candidate.getWorld().getName(), candidate.getBlockX(), candidate.getBlockY(), candidate.getBlockZ(), point.cat);
            spawnPoints.add(repaired);
            nextSpawnAt.put(repaired.id, System.currentTimeMillis() + spawnEverySeconds * 1000L);
            save();
            return true;
        }
        return false;
    }

    private boolean isSafeSpawnBlock(Block block) {
        if (block == null) return false;
        Material type = block.getType();
        return type == Material.AIR || type == Material.CAVE_AIR || type == Material.VOID_AIR || type == Material.CHEST;
    }

    /* ================= Game Loop ================= */

    private void tick() {
        // Despawn geöffnete (Timeout) oder leere Kisten
        if (!active.isEmpty()) {
            long now = System.currentTimeMillis();
            List<Location> toRemove = new ArrayList<>();
            for (Map.Entry<Location, ActiveChest> e : active.entrySet()) {
                ActiveChest ac = e.getValue();
                if (openDespawnSeconds > 0 && ac.firstOpenAt > 0 && now - ac.firstOpenAt >= openDespawnSeconds * 1000L) {
                    toRemove.add(e.getKey());
                } else {
                    Chest chest = getChestAt(e.getKey());
                    if (chest != null) {
                        if (isEmpty(chest.getBlockInventory())) toRemove.add(e.getKey());
                    } else {
                        toRemove.add(e.getKey()); // Block existiert nicht/anderer Typ
                    }
                }
            }
            for (Location l : toRemove) despawn(l, false);
        }

        // Spawnen, wenn Kapazität
        int need = Math.max(0, maxActiveChests - active.size());
        for (int i=0;i<need;i++) spawnOne();
    }

    private boolean spawnOne() {
        return spawnOne(false);
    }

    private boolean spawnOne(boolean ignoreCooldown) {
        // zufälliger freier Spawn
        long now = System.currentTimeMillis();
        List<SpawnPoint> candidates = new ArrayList<>();
        for (SpawnPoint sp : spawnPoints) {
            Location l = sp.toLocation();
            if (l == null) continue;
            if (l.getWorld() == null || !l.getChunk().isLoaded()) continue;
            if (active.containsKey(l)) continue;
            if (!ignoreCooldown && now < nextSpawnAt.getOrDefault(sp.id, 0L)) continue;
            if (warzone != null && !warzone.isWarzone(l)) continue;
            candidates.add(sp);
        }
        if (candidates.isEmpty()) return false;

        SpawnPoint chosen = candidates.get(ThreadLocalRandom.current().nextInt(candidates.size()));
        Location l = chosen.toLocation();
        if (l == null) return false;

        // Block setzen
        Block b = l.getBlock();
        Chest chest = createSafeChest(b);
        if (chest == null) return false;

        chest.setCustomName("Warzone Chest");
        chest.update(true, false);

        // Befüllen (tiered)
        fillChestTiered(chest.getBlockInventory(), chosen.cat, l);

        active.put(l, new ActiveChest(chosen.id, l, chosen.cat));
        nextSpawnAt.remove(chosen.id);

        // dezentes FX
        l.getWorld().playSound(l, Sound.BLOCK_CHEST_OPEN, 0.6f, 1.2f);
        l.getWorld().spawnParticle(Particle.CRIT, l.clone().add(0.5, 1.2, 0.5), 10, 0.2,0.2,0.2, 0.01);
        return true;
    }

    /**
     * Markiert „erstmals geöffnet“ + vergibt Level-XP einmalig.
     */
    public void markOpened(Location chestLoc, UUID opener) {
        ActiveChest ac = active.get(chestLoc);
        if (ac == null) return;
        if (ac.firstOpenAt == 0L) {
            ac.firstOpenAt = System.currentTimeMillis();

            Player player = opener == null ? null : Bukkit.getPlayer(opener);
            if (plugin.getEventManager() != null) {
                plugin.getEventManager().addProgressIfActive("chest_rush", 1);
            }
            if (player != null && plugin.getLevelProgressionService() != null) {
                int xp = Math.max(1, plugin.getConfig().getInt("level.chestXp", 12));
                plugin.getLevelProgressionService().addXp(player, xp, "Warzone-Kiste");
            }
            if (player != null && plugin.getSkillManager() != null) {
                int skillXp = Math.max(5, plugin.getConfig().getInt("skills.sources.warzoneChestRaiderXp", 12));
                plugin.getSkillManager().addXp(player, SkillType.RAIDER, skillXp);
            }
            if (player != null && plugin.getCustomArmorSetService() != null
                    && plugin.getCustomArmorSetService().hasSet(player, "prospector", 4)
                    && plugin.getCoinsManager() != null) {
                int bonus = plugin.getBoosterManager().applyCoinBoost(opener, 25);
                plugin.getCoinsManager().addCoins(opener, bonus);
                player.sendMessage("§eProspector Set §8» §6+" + bonus + " Coins §7aus der Warzone-Kiste");
            }
        }
    }

    public boolean isManagedChest(Location loc) { return active.containsKey(loc); }

    public void despawn(Location l, boolean dropLeftovers) {
        ActiveChest ac = active.remove(l);
        if (ac != null) {
            nextSpawnAt.put(ac.spawnId, System.currentTimeMillis() + spawnEverySeconds * 1000L);
        }
        Block b = l.getBlock();
        if (b.getType() == Material.CHEST && b.getState() instanceof Chest) {
            Chest c = (Chest) b.getState();
            if (!dropLeftovers) c.getBlockInventory().clear();
        }
        b.setType(Material.AIR, false);
    }

    /* ================= Loot (Tiered) ================= */

    private void seedTieredLoot() {
        // COMMON: nützliches Basiszeug
        commonPool.add(new LootEntry(Material.COOKED_BEEF,      3,16, 1.0));
        commonPool.add(new LootEntry(Material.BAKED_POTATO,     3,16, 1.0));
        commonPool.add(new LootEntry(Material.TNT,              6,16, 0.55));
        commonPool.add(new LootEntry(Material.OBSIDIAN,        16,16, 0.7));
        commonPool.add(new LootEntry(Material.OBSIDIAN,        30,30, 0.25));
        commonPool.add(new LootEntry(Material.ENDER_PEARL,      3, 6, 0.6));
        commonPool.add(new LootEntry(potion(Material.POTION, PotionType.HEALING, "§cInstant Heal", null), 1, 2, 0.35));
        commonPool.add(new LootEntry(potion(Material.POTION, PotionType.SWIFTNESS, "§bSpeed Potion", new PotionEffect(PotionEffectType.SPEED, 30 * 20, 0)), 1, 1, 0.25));
        commonPool.add(new LootEntry(Material.NETHER_STAR, 1, 1, 0.22));

        // RARE: wertiger
        rarePool.add(new LootEntry(Material.TNT,               16,30, 0.85));
        rarePool.add(new LootEntry(Material.OBSIDIAN,           16,16, 0.35));
        rarePool.add(new LootEntry(Material.OBSIDIAN,           30,30, 0.55));
        rarePool.add(new LootEntry(Material.EXPERIENCE_BOTTLE, 12,24, 0.7));
        rarePool.add(new LootEntry(potion(Material.POTION, PotionType.STRENGTH, "§cStrength II §7(30s)", new PotionEffect(PotionEffectType.STRENGTH, 30 * 20, 1)), 1, 1, 0.35));
        rarePool.add(new LootEntry(potion(Material.POTION, PotionType.REGENERATION, "§dRegeneration I §7(30s)", new PotionEffect(PotionEffectType.REGENERATION, 30 * 20, 0)), 1, 1, 0.25));
        rarePool.add(new LootEntry(potion(Material.SPLASH_POTION, PotionType.WEAKNESS, "§8Splash Weakness I §7(30s)", new PotionEffect(PotionEffectType.WEAKNESS, 30 * 20, 0)), 1, 1, 0.2));
        rarePool.add(new LootEntry(Material.NETHER_STAR, 1, 1, 0.34));

        // EPIC: selten
        epicPool.add(new LootEntry(Material.TNT,                30,64, 0.7));
        epicPool.add(new LootEntry(Material.OBSIDIAN,           64,64, 0.75));
        epicPool.add(new LootEntry(Material.ENDER_PEARL,        3, 6, 0.35));
        epicPool.add(new LootEntry(Material.ENDER_PEARL,        8,16, 0.2));
        epicPool.add(new LootEntry(potion(Material.SPLASH_POTION, PotionType.HEALING, "§cSplash Instant Heal", null), 1, 2, 0.3));
        epicPool.add(new LootEntry(potion(Material.SPLASH_POTION, PotionType.POISON, "§2Splash Poison I §7(30s)", new PotionEffect(PotionEffectType.POISON, 30 * 20, 0)), 1, 1, 0.3));
        epicPool.add(new LootEntry(Material.NETHER_STAR, 1, 1, 0.55));
    }

    private ItemStack potion(Material material, PotionType type, String name, PotionEffect effect) {
        ItemStack potion = new ItemStack(material == null ? Material.POTION : material);
        if (potion.getItemMeta() instanceof PotionMeta meta) {
            meta.setBasePotionType(type);
            if (name != null && !name.isBlank()) meta.setDisplayName(name);
            if (effect != null) meta.addCustomEffect(effect, true);
            potion.setItemMeta(meta);
        }
        return potion;
    }

    private void fillChestTiered(Inventory inv, Category cat, Location location) {
        inv.clear();

        // Tier-Gewichte je Kategorie
        // WARZONE etwas „normaler“, FLAG_CAPTURE etwas hochwertiger
        int disparity = warzone == null ? 1 : Math.max(1, warzone.disparityLevel(location));
        int wCommon, wRare, wEpic;
        if (cat == Category.FLAG_CAPTURE) {
            wCommon = 45; wRare = 40; wEpic = 15;
        } else if (disparity >= 25) {
            wCommon = 35; wRare = 40; wEpic = 25;
        } else if (disparity >= 16) {
            wCommon = 40; wRare = 40; wEpic = 20;
        } else if (disparity >= 8) {
            wCommon = 50; wRare = 35; wEpic = 15;
        } else {
            wCommon = 62; wRare = 30; wEpic = 8;
        }

        int extraByDepth = Math.min(2, disparity / 20);
        int maxPulls = Math.min(Math.max(3, rollsPerChest + extraByDepth), inv.getSize());
        int minPulls = Math.max(1, Math.min(minRollsPerChest + (disparity >= 25 ? 1 : 0), maxPulls));
        int pulls = ThreadLocalRandom.current().nextInt(minPulls, maxPulls + 1);
        for (int i = 0; i < pulls; i++) {
            Rarity r = pickRarity(wCommon, wRare, wEpic);
            LootEntry le = pickFromPool(r);
            if (le == null) continue;

            int free = findFreeSlot(inv);
            if (free < 0) break;
            ItemStack item = le.roll();
            if (isForbiddenWarzoneChestMaterial(item.getType())) continue;
            item = decorateChestItem(item, r, location);
            inv.setItem(free, item);
        }

        ensureCrateCrystal(inv, cat, location);
        if (isEmpty(inv)) {
            addSpread(inv, new ItemStack(Material.COOKED_BEEF, 16));
            addSpread(inv, new ItemStack(Material.OBSIDIAN, 24));
            ensureCrateCrystal(inv, cat, location);
        }
    }

    private boolean isForbiddenWarzoneChestMaterial(Material material) {
        if (material == null) return true;
        if (material == Material.GOLDEN_APPLE || material == Material.ENCHANTED_GOLDEN_APPLE) return true;
        String name = material.name();
        return name.endsWith("_SWORD")
                || name.endsWith("_AXE")
                || name.endsWith("_HELMET")
                || name.endsWith("_CHESTPLATE")
                || name.endsWith("_LEGGINGS")
                || name.endsWith("_BOOTS");
    }

    private ItemStack decorateChestItem(ItemStack item, Rarity rarity, Location location) {
        if (item == null || (item.getType() != Material.END_CRYSTAL && item.getType() != Material.NETHER_STAR)) {
            return item;
        }

        int disparity = plugin.getWarzoneRegionManager() == null ? 1 : plugin.getWarzoneRegionManager().disparityLevel(location);
        com.vetroforge.items.Rarity crateRarity = pickCrystalRarity(disparity, rarity);
        return LootItemFactory.guaranteedCrystal(plugin, crateRarity, item.getAmount());
    }

    private void ensureCrateCrystal(Inventory inv, Category cat, Location location) {
        if (containsCrateCrystal(inv)) return;
        int disparity = plugin.getWarzoneRegionManager() == null ? 1 : plugin.getWarzoneRegionManager().disparityLevel(location);
        com.vetroforge.items.Rarity rarity = cat == Category.FLAG_CAPTURE
                ? pickCrystalRarity(Math.max(disparity, 16), Rarity.EPIC)
                : pickCrystalRarity(disparity, disparity >= 16 ? Rarity.EPIC : Rarity.RARE);
        addSpread(inv, LootItemFactory.guaranteedCrystal(plugin, rarity, 1));
    }

    private boolean containsCrateCrystal(Inventory inv) {
        for (ItemStack item : inv.getContents()) {
            if (item == null || item.getType().isAir()) continue;
            if (item.getType() == Material.NETHER_STAR || item.getType() == Material.END_CRYSTAL || item.getType() == Material.AMETHYST_SHARD) return true;
            if (plugin.getKeyCrateManager() != null && plugin.getKeyCrateManager().isKeyItem(item)) return true;
        }
        return false;
    }

    private void addSpread(Inventory inv, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        int free = findFreeSlot(inv);
        if (free >= 0) inv.setItem(free, item);
        else inv.addItem(item);
    }

    private com.vetroforge.items.Rarity pickCrystalRarity(int disparity, Rarity rolledPool) {
        int legendary = 0;
        int epic;
        int rare;
        int common;

        if (disparity >= 80) {
            legendary = 55; epic = 35; rare = 10; common = 0;
        } else if (disparity >= 50) {
            legendary = 32; epic = 43; rare = 22; common = 3;
        } else if (disparity >= 25) {
            legendary = 14; epic = 38; rare = 38; common = 10;
        } else if (disparity >= 10) {
            legendary = 3; epic = 20; rare = 55; common = 22;
        } else if (rolledPool == Rarity.EPIC) {
            epic = 8; rare = 37; common = 55;
        } else if (rolledPool == Rarity.RARE) {
            epic = 0; rare = 35; common = 65;
        } else {
            epic = 0; rare = 20; common = 80;
        }

        int total = common + rare + epic + legendary;
        int roll = ThreadLocalRandom.current().nextInt(Math.max(1, total));
        if (roll < common) return com.vetroforge.items.Rarity.COMMON;
        roll -= common;
        if (roll < rare) return com.vetroforge.items.Rarity.RARE;
        roll -= rare;
        if (roll < epic) return com.vetroforge.items.Rarity.EPIC;
        return com.vetroforge.items.Rarity.LEGENDARY;
    }

    private Rarity pickRarity(int wCommon, int wRare, int wEpic) {
        int total = wCommon + wRare + wEpic;
        int r = ThreadLocalRandom.current().nextInt(total);
        if (r < wCommon) return Rarity.COMMON;
        r -= wCommon;
        if (r < wRare)   return Rarity.RARE;
        return Rarity.EPIC;
    }

    private LootEntry pickFromPool(Rarity r) {
        List<LootEntry> pool = switch (r) {
            case COMMON -> commonPool;
            case RARE   -> rarePool;
            case EPIC   -> epicPool;
        };
        if (pool.isEmpty()) return null;

        // gewichtete Auswahl
        double sum = 0;
        for (LootEntry e : pool) sum += Math.max(0.0, e.weight);
        double roll = ThreadLocalRandom.current().nextDouble(sum);
        for (LootEntry e : pool) {
            roll -= Math.max(0.0, e.weight);
            if (roll <= 0) return e;
        }
        return pool.get(pool.size() - 1);
    }

    private int findFreeSlot(Inventory inv) {
        List<Integer> free = new ArrayList<>();
        for (int i=0;i<inv.getSize();i++) {
            ItemStack it = inv.getItem(i);
            if (it == null || it.getType() == Material.AIR) free.add(i);
        }
        if (free.isEmpty()) return -1;
        return free.get(ThreadLocalRandom.current().nextInt(free.size()));
    }

    private boolean isEmpty(Inventory inv) {
        for (ItemStack it : inv.getContents()) {
            if (it != null && it.getType() != Material.AIR) return false;
        }
        return true;
    }

    private Chest getChestAt(Location l) {
        if (l == null) return null;
        if (l.getWorld() == null || !l.getChunk().isLoaded()) return null;
        Block b = l.getBlock();
        if (b.getType() != Material.CHEST) return null;
        try {
            if (!(b.getState() instanceof Chest)) return null;
            return (Chest) b.getState();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[WarzoneChest] Defekter Chest-BlockState bei "
                    + formatLocation(l) + " übersprungen: " + throwable.getMessage());
            return null;
        }
    }

    private Chest createSafeChest(Block block) {
        if (block == null || block.getWorld() == null) return null;
        Location location = block.getLocation();
        if (location.getWorld() == null || !location.getChunk().isLoaded()) return null;

        try {
            if (block.getType() != Material.AIR && block.getType() != Material.CHEST) {
                block.setType(Material.AIR, false);
            }
            block.setType(Material.CHEST, false);
            if (block.getState() instanceof Chest chest) return chest;

            block.setType(Material.CHEST, true);
            if (block.getState() instanceof Chest chest) return chest;
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[WarzoneChest] Konnte Chest bei "
                    + formatLocation(location) + " nicht sicher erstellen: " + throwable.getMessage());
        }

        return null;
    }

    private String formatLocation(Location location) {
        if (location == null || location.getWorld() == null) return "unknown";
        return location.getWorld().getName() + " "
                + location.getBlockX() + " "
                + location.getBlockY() + " "
                + location.getBlockZ();
    }

    /* ================= Config/Spawns ================= */

    private void loadConfig() {
        try {
            this.spawnEverySeconds = Math.max(30, plugin.getConfig().getInt(
                    "warzone.chests.spawn-every-seconds",
                    plugin.getConfig().getInt("warzone.chests.spawn.everySeconds", 120)
            ));
            this.maxActiveChests = Math.max(1, plugin.getConfig().getInt(
                    "warzone.chests.max-active",
                    plugin.getConfig().getInt("warzone.chests.spawn.maxActive", 8)
            ));
            this.openDespawnSeconds = Math.max(0, plugin.getConfig().getInt(
                    "warzone.chests.despawn-after-open-seconds",
                    plugin.getConfig().getInt("warzone.chests.spawn.despawnAfterOpenSeconds", 300)
            ));
            this.rollsPerChest = Math.max(1, plugin.getConfig().getInt(
                    "warzone.chests.rolls-per-chest",
                    plugin.getConfig().getInt("warzone.chests.spawn.rollsPerChest", 6)
            ));
            this.minRollsPerChest = Math.max(1, plugin.getConfig().getInt(
                    "warzone.chests.min-rolls-per-chest",
                    plugin.getConfig().getInt("warzone.chests.spawn.minRollsPerChest", 2)
            ));
            if (this.minRollsPerChest > this.rollsPerChest) {
                this.minRollsPerChest = this.rollsPerChest;
            }
        } catch (Throwable ignored) {}
    }

    private void loadSpawnPoints() {
        org.bukkit.configuration.file.FileConfiguration cfg =
                org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(dataFile);
        spawnPoints.clear();
        nextSpawnAt.clear();
        long initialSpawnAt = System.currentTimeMillis() + spawnEverySeconds * 1000L;
        if (cfg.isList("spawns")) {
            for (Object o : cfg.getList("spawns")) {
                if (!(o instanceof Map)) continue;
                Map<?,?> m = (Map<?,?>) o;
                String world = String.valueOf(m.get("world"));
                Object rawId = m.get("id");
                String id = rawId == null ? String.valueOf(spawnPoints.size() + 1) : String.valueOf(rawId);
                int x = toInt(m.get("x")), y = toInt(m.get("y")), z = toInt(m.get("z"));
                Category cat = Category.WARZONE;
                try { cat = Category.valueOf(String.valueOf(m.get("cat"))); } catch (Throwable ignored) {}
                SpawnPoint spawnPoint = new SpawnPoint(id, world, x, y, z, cat);
                spawnPoints.add(spawnPoint);
                nextSpawnAt.put(spawnPoint.id, initialSpawnAt);
            }
        }
    }

    private int toInt(Object v) {
        if (v instanceof Number) return ((Number) v).intValue();
        try { return Integer.parseInt(String.valueOf(v)); } catch (Exception e) { return 0; }
    }
}
