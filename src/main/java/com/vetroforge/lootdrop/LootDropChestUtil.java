// src/main/java/com/vetroforge/lootdrop/LootDropChestUtil.java
package com.vetroforge.lootdrop;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomItemUtil;
import com.vetroforge.items.Rarity;
import com.vetroforge.lootdrop.model.Tier;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

public final class LootDropChestUtil {
    private LootDropChestUtil(){}

    /* ================= Context ================= */

    public static final class Context {
        public final FactionsPlugin plugin;
        public final Location loc;
        public final Player openerContext;
        public final int slots;
        public final Tier forcedTier;
        public Context(FactionsPlugin plugin, Location loc, Player ctx, int slots) {
            this(plugin, loc, ctx, slots, null);
        }
        public Context(FactionsPlugin plugin, Location loc, Player ctx, int slots, Tier forcedTier) {
            this.plugin = plugin;
            this.loc = loc;
            this.openerContext = ctx;
            this.slots = Math.max(1, slots);
            this.forcedTier = forcedTier;
        }
    }

    /* ================= Public API ================= */

    /** Spawnt (falls nötig) eine Chest am Boden unterhalb von cx.loc und füllt sie. */
    public static Inventory spawnAndFill(Context cx){
        Location place = groundTop(cx.loc);
        Block b = place.getBlock();
        Chest chest = asChestOrNull(b);
        if (chest == null) {
            cx.plugin.getLogger().warning("[LootDrop] Kiste konnte nicht initialisiert werden @ " + place);
            return Bukkit.createInventory(null, 27);
        }
        return doFill(cx.plugin, place, chest.getBlockInventory(), cx.openerContext, cx.slots, cx.forcedTier);
    }

    /** Füllt die Chest exakt an cx.loc (setzt zur Not den Block auf CHEST). */
    public static Inventory fillAtExact(Context cx){
        Block b = cx.loc.getBlock();
        if (b.getType() != Material.CHEST) b.setType(Material.CHEST, false);
        Chest chest = asChestOrNull(b);
        if (chest == null) {
            cx.plugin.getLogger().warning("[LootDrop] Chest-Block ungültig @ " + cx.loc);
            return Bukkit.createInventory(null, 27);
        }
        Inventory inv = doFill(cx.plugin, cx.loc, chest.getBlockInventory(), cx.openerContext, cx.slots, cx.forcedTier);
        try {
            chest.update(true, false);
        } catch (Throwable ignored) {}
        return inv;
    }

    /** Garantiert, dass die Kiste nach Server-/Chunk-Ticks nicht leer bleibt. */
    public static void ensureFilled(Context cx) {
        Block b = cx.loc.getBlock();
        Chest chest = asChestOrNull(b);
        if (chest == null) {
            cx.plugin.getLogger().warning("[LootDrop] ensureFilled: Chest fehlt @ " + cx.loc);
            return;
        }
        Inventory inv = chest.getBlockInventory();
        if (countItems(inv) > 0) return;

        cx.plugin.getLogger().warning("[LootDrop] ensureFilled: Kiste war leer, fülle direkt mit Fallback @ " + cx.loc);
        inv.clear();
        placeSequential(inv, fallbackLoot(cx.forcedTier));
        placeSequential(inv, guaranteedCrystals(cx.plugin, cx.forcedTier));
        if (countItems(inv) == 0) {
            inv.setItem(13, named(Material.GOLDEN_APPLE, 3, "§6Lootdrop Supply"));
        }
        try {
            chest.update(true, false);
        } catch (Throwable ignored) {}
    }

    /** Füllt direkt das Inventar, das der Spieler gerade sieht. */
    public static void ensureInventoryFilled(FactionsPlugin plugin, Inventory inv, Tier tier, int slots) {
        if (plugin == null || inv == null) return;
        if (countItems(inv) > 0) return;

        Tier safeTier = tier == null ? Tier.GOLD : tier;
        plugin.getLogger().warning("[LootDrop] Open-Inventory war leer, fülle direkt mit Notfall-Loot.");
        placeSequential(inv, fallbackLoot(safeTier));
        placeSequential(inv, guaranteedCrystals(plugin, safeTier));
        int minItems = Math.max(1, Math.min(slots, inv.getSize()));
        for (int i = countItems(inv); i < minItems; i++) {
            inv.addItem(fallbackSingle(safeTier, i));
        }
        if (countItems(inv) == 0) {
            inv.setItem(Math.min(13, inv.getSize() - 1), named(Material.GOLDEN_APPLE, 3, "§6Lootdrop Supply"));
        }
    }

    /* ================= Internals ================= */

    private static Inventory doFill(FactionsPlugin plugin, Location where, Inventory inv, Player openerCtx, int slots, Tier forcedTier){
        if (inv == null) inv = Bukkit.createInventory(null, 27);
        inv.clear(); // niemals „refillen“, wenn später despawn geprüft wird

        List<ItemStack> rolled = new ArrayList<>();
        boolean allowMystic = forcedTier == Tier.MYTHIC || computeMysticAllowed(plugin, where.getWorld(), where);
        Tier rewardTier = forcedTier != null ? forcedTier : (allowMystic ? Tier.MYTHIC : Tier.GOLD);
        List<ItemStack> guaranteed;
        try {
            guaranteed = new LootTables(plugin).roll(rewardTier, 0.0D, false);
        } catch (Throwable t) {
            plugin.getLogger().warning("[LootDrop] LootTable-Roll fehlgeschlagen, nutze Fallback: " + t.getMessage());
            guaranteed = fallbackLoot(rewardTier);
        }
        for (ItemStack item : guaranteed) {
            if (item != null && item.getType() != Material.AIR) rolled.add(item);
        }
        rolled.addAll(guaranteedCrystals(plugin, rewardTier));

        WeightedRarity wt = weightsFor(plugin, allowMystic);
        int extraSlots = Math.max(0, slots - guaranteed.size());
        for (int i = 0; i < extraSlots; i++){
            Rarity tier = wt.pick();
            ItemStack item;
            try {
                item = LootItemFactory.rollFromCrate(plugin, tier, openerCtx);
                applyBadgesIfNeeded(plugin, item, tier);
            } catch (Throwable t) {
                plugin.getLogger().warning("[LootDrop] Bonus-Roll fehlgeschlagen, setze Fallback-Item: " + t.getMessage());
                item = fallbackSingle(rewardTier, i);
            }
            if (item != null && item.getType() != Material.AIR) rolled.add(item);
        }
        if (rolled.isEmpty()) {
            plugin.getLogger().warning("[LootDrop] LootTable fuer " + rewardTier + " war leer, nutze Fallback-Loot.");
            rolled.addAll(fallbackLoot(rewardTier));
        }
        scatter(inv, rolled);
        if (!containsCrystal(inv)) {
            placeSequential(inv, guaranteedCrystals(plugin, rewardTier));
        }
        if (countItems(inv) == 0) {
            plugin.getLogger().warning("[LootDrop] Kiste blieb nach Scatter leer, setze Fallback-Loot direkt.");
            placeSequential(inv, fallbackLoot(rewardTier));
            placeSequential(inv, guaranteedCrystals(plugin, rewardTier));
        }

        // SFX dezent
        try {
            where.getWorld().playSound(where, Sound.BLOCK_CHEST_OPEN, 0.7f, 1.1f);
            where.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, where.clone().add(0.5,1.2,0.5), 6, .2, .2, .2, 0.01);
        } catch (Throwable ignored){}
        return inv;
    }

    private static int countItems(Inventory inv) {
        if (inv == null) return 0;
        int count = 0;
        for (ItemStack item : inv.getContents()) {
            if (item != null && item.getType() != Material.AIR) count++;
        }
        return count;
    }

    private static boolean containsCrystal(Inventory inv) {
        if (inv == null) return false;
        for (ItemStack item : inv.getContents()) {
            if (isCrateKey(item)) return true;
        }
        return false;
    }

    public static List<ItemStack> fallbackLoot(Tier tier) {
        Tier safeTier = tier == null ? Tier.GOLD : tier;
        List<ItemStack> items = new ArrayList<>();
        switch (safeTier) {
            case BRONZE -> {
                items.add(named(Material.ENDER_PEARL, 12, "§6Bronze Supply"));
                items.add(named(Material.EXPERIENCE_BOTTLE, 16, "§aXP-Flasche +150"));
                items.add(named(Material.OBSIDIAN, 24, "§5Obsidian Supply"));
            }
            case SILVER -> {
                items.add(named(Material.ENDER_PEARL, 20, "§7Silver Supply"));
                items.add(named(Material.EXPERIENCE_BOTTLE, 24, "§aXP-Flasche +225"));
                items.add(named(Material.TNT, 32, "§cRaid Supply"));
            }
            case GOLD -> {
                items.add(named(Material.ENDER_PEARL, 30, "§eGold Supply"));
                items.add(named(Material.EXPERIENCE_BOTTLE, 32, "§aXP-Flasche +300"));
                items.add(named(Material.OBSIDIAN, 64, "§5Obsidian x64"));
                items.add(named(Material.TNT, 64, "§cTNT x64"));
                items.add(named(Material.GOLDEN_APPLE, 3, "§6Goldener Apfel"));
            }
            case MYTHIC -> {
                items.add(named(Material.ENDER_PEARL, 32, "§dMythic Supply"));
                items.add(named(Material.EXPERIENCE_BOTTLE, 48, "§aXP-Flasche +500"));
                items.add(named(Material.OBSIDIAN, 64, "§5Obsidian x64"));
                items.add(named(Material.TNT, 64, "§cTNT x64"));
                items.add(named(Material.ENCHANTED_GOLDEN_APPLE, 1, "§5Mystic Apple"));
            }
        }
        return items;
    }

    private static List<ItemStack> guaranteedCrystals(FactionsPlugin plugin, Tier tier) {
        Tier safeTier = tier == null ? Tier.GOLD : tier;
        List<ItemStack> crystals = new ArrayList<>();
        switch (safeTier) {
            case BRONZE -> crystals.add(crystal(plugin, Rarity.RARE, 1));
            case SILVER -> crystals.add(crystal(plugin, Rarity.EPIC, 1));
            case GOLD -> {
                crystals.add(crystal(plugin, Rarity.EPIC, 1));
                crystals.add(crystal(plugin, Rarity.LEGENDARY, 1));
            }
            case MYTHIC -> {
                crystals.add(crystal(plugin, Rarity.LEGENDARY, 1));
                crystals.add(crystal(plugin, Rarity.MYSTIC, 1));
            }
        }
        crystals.removeIf(item -> item == null || item.getType() == Material.AIR);
        return crystals;
    }

    private static ItemStack crystal(FactionsPlugin plugin, Rarity rarity, int amount) {
        return LootItemFactory.guaranteedCrystal(plugin, rarity, amount);
    }

    private static ItemStack fallbackSingle(Tier tier, int index) {
        List<ItemStack> items = fallbackLoot(tier);
        if (items.isEmpty()) return named(Material.GOLDEN_APPLE, 1, "§6Lootdrop Supply");
        return items.get(Math.floorMod(index, items.size()));
    }

    private static ItemStack named(Material material, int amount, String name) {
        ItemStack item = new ItemStack(material, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private static void scatter(Inventory inv, List<ItemStack> items) {
        if (inv == null || items == null || items.isEmpty()) return;
        List<Integer> slots = new ArrayList<>();
        for (int i = 0; i < inv.getSize(); i++) slots.add(i);
        Collections.shuffle(slots, ThreadLocalRandom.current());

        int slotIndex = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            if (slotIndex < slots.size()) {
                inv.setItem(slots.get(slotIndex++), item);
            } else {
                inv.addItem(item);
            }
        }
    }

    private static void placeSequential(Inventory inv, List<ItemStack> items) {
        if (inv == null || items == null || items.isEmpty()) return;
        int slot = 0;
        for (ItemStack item : items) {
            if (item == null || item.getType() == Material.AIR) continue;
            while (slot < inv.getSize() && inv.getItem(slot) != null) slot++;
            if (slot >= inv.getSize()) {
                inv.addItem(item);
            } else {
                inv.setItem(slot++, item);
            }
        }
    }

    /** Findet Bodenposition (Block über erster festen Fläche). */
    private static Location groundTop(Location near){
        Block b = near.getBlock();
        for (int i = 0; i < 8; i++){
            if (b.getType().isSolid()) break;
            b = b.getRelative(BlockFace.DOWN);
        }
        return b.getLocation().add(0,1,0);
    }

    /** Mystic erlaubt, wenn MysticStorm aktiv ODER die Faction an dieser Stelle eine Capture-Zone kontrolliert. */
    private static boolean computeMysticAllowed(FactionsPlugin plugin, World w, Location at){
        if (plugin.getMysticStormService() != null && plugin.getMysticStormService().isActive(w)) {
            return true;
        }

        if (plugin.getCaptureZoneBuffService() == null || plugin.getFactionManager() == null) return false;
        String faction = plugin.getFactionManager().getFactionAt(at);
        return faction != null && plugin.getCaptureZoneBuffService().hasZoneControl(faction);
    }

    /** Für Keys/Shard etc. (die keine CI-Metadaten haben) Badge ergänzen. */
    private static void applyBadgesIfNeeded(FactionsPlugin plugin, ItemStack it, Rarity tier){
        if (it == null || it.getType() == Material.AIR) return;

        // CI-Items haben eigene Lore
        String base = CustomItemUtil.baseId(plugin, it);
        if (base != null) return;

        ItemMeta im = it.getItemMeta();
        List<String> lore = (im != null && im.hasLore())
                ? new ArrayList<>(Objects.requireNonNull(im.getLore()))
                : new ArrayList<>();

        if (isCrateKey(it)) {
            lore.add(0, "§8Tier: " + colorTier(tier) + tier.name());
        } else if (it.getType() == Material.AMETHYST_SHARD) {
            lore.add(0, "§8Loot: " + ChatColor.LIGHT_PURPLE + "Kriegsbeute");
        } else {
            Rarity r = CustomItemUtil.rarityOf(plugin, it);
            lore.add(0, "§8Rarity: " + r.color + r.display);
        }

        if (im != null) {
            im.setLore(lore);
            it.setItemMeta(im);
        }
    }

    private static boolean isCrateKey(ItemStack it){
        if (it == null || !it.hasItemMeta()) return false;
        String name = it.getItemMeta().getDisplayName();
        if (name == null) return false;
        String n = ChatColor.stripColor(name).toLowerCase(Locale.ROOT);
        return it.getType() == Material.NETHER_STAR
                || it.getType() == Material.END_CRYSTAL
                || n.contains("key")
                || n.contains("crystal")
                || n.contains("kiste")
                || n.contains("crate");
    }

    private static String colorTier(Rarity t){
        return switch (t){
            case COMMON    -> "§7";
            case RARE      -> "§9";
            case EPIC      -> "§d";
            case LEGENDARY -> "§6";
            case MYSTIC    -> "§5";
        };
    }

    /* ---------- Gewichte ---------- */

    private static WeightedRarity weightsFor(FactionsPlugin plugin, boolean allowMystic){
        WeightedRarity w = new WeightedRarity();
        w.add(Rarity.LEGENDARY, allowMystic ? 82 : 100);
        if (allowMystic) {
            w.add(Rarity.MYSTIC, plugin.getConfig().getDouble("lootdrop.mysticWeight", 3.0D));
        }
        return w;
    }

    private static final class WeightedRarity {
        private final List<Rarity> tiers = new ArrayList<>();
        private final List<Double> wts   = new ArrayList<>();
        private double sum = 0;
        void add(Rarity t, double w){ if (w>0){ tiers.add(t); wts.add(w); sum+=w; } }
        Rarity pick(){
            if (tiers.isEmpty()) return Rarity.LEGENDARY;
            double r = ThreadLocalRandom.current().nextDouble(sum);
            for (int i=0;i<tiers.size();i++){
                if (r < wts.get(i)) return tiers.get(i);
                r -= wts.get(i);
            }
            return tiers.get(0);
        }
    }

    /** Block → Chest-State (mit Update-Fallback). */
    private static Chest asChestOrNull(Block b) {
        if (b.getType() != Material.CHEST) {
            b.setType(Material.CHEST, false);
        }
        var state = b.getState();
        if (state instanceof Chest c) return c;

        b.setType(Material.CHEST, true);
        state = b.getState();
        return (state instanceof Chest c2) ? c2 : null;
    }
}
