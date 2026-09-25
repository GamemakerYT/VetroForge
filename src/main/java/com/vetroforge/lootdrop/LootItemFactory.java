// src/main/java/com/vetroforge/lootdrop/LootItemFactory.java
package com.vetroforge.lootdrop;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomEnchant;
import com.vetroforge.items.CustomItemUtil;
import com.vetroforge.items.Rarity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Zentrale Loot-Erzeugung für Crates & Lootdrops – ohne harte Abhängigkeit auf dein Crates-Package.
 * - Keys via optionalem Bridge-Objekt (plugin.getCrateBridge()) per Reflection.
 * - Fallback: hübsches Crystal-Item ohne externe API.
 * - Mystic-Items nur aus MYSTIC vorgesehen.
 * - Capture-Zone-Buff skaliert HIGH-Drops.
 */
public final class LootItemFactory {
    private LootItemFactory() {}

    /* ----------------------------------------------------------
     * Public Factories
     * ---------------------------------------------------------- */

    /** Shadowblade in gewünschter Rarity (nutzt dein CustomItemUtil). */
    public static ItemStack shadowblade(FactionsPlugin plugin, Rarity r) {
        return CustomItemUtil.makeShadowblade(plugin, r);
    }

    public static ItemStack warPickaxe(FactionsPlugin plugin, Rarity r) {
        return CustomItemUtil.makeWarPickaxe(plugin, r);
    }

    public static ItemStack battleHoe(FactionsPlugin plugin, Rarity r) {
        return CustomItemUtil.makeBattleHoe(plugin, r);
    }

    /** Mystic-Item (Blazerute mit Ability). */
    public static ItemStack dragonsBreath(FactionsPlugin plugin) {
        return CustomItemUtil.makeDragonsBreath(plugin);
    }

    public static ItemStack armorPiece(FactionsPlugin plugin, String set, Rarity rarity) {
        Material[] pieces = switch (set.toLowerCase(Locale.ROOT)) {
            case "scout" -> new Material[]{Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE, Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS};
            case "ranger" -> new Material[]{Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE, Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS};
            case "fortune" -> new Material[]{Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE, Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS};
            case "vanguard" -> new Material[]{Material.IRON_HELMET, Material.IRON_CHESTPLATE, Material.IRON_LEGGINGS, Material.IRON_BOOTS};
            case "ember", "guardian", "prospector", "ender" ->
                    new Material[]{Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE, Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS};
            default -> new Material[]{Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE, Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS};
        };
        return CustomItemUtil.makeArmorPiece(plugin, set, pieces[ThreadLocalRandom.current().nextInt(pieces.length)], rarity);
    }

    public static ItemStack randomArmorPiece(FactionsPlugin plugin, Rarity rarity) {
        List<String> sets = new ArrayList<>(List.of(
                "scout", "ranger", "fortune", "vanguard", "ender",
                "ember", "shadow", "titan", "void", "raider", "guardian", "berserker", "prospector"
        ));
        if (plugin.getConfig().getBoolean("customitems.seasonOneDropsEnabled", true)) sets.add("season_one");
        return armorPiece(plugin, sets.get(ThreadLocalRandom.current().nextInt(sets.size())), rarity);
    }

    public static ItemStack eventItem(FactionsPlugin plugin) {
        DayOfWeek day = LocalDate.now().getDayOfWeek();
        boolean weekend = day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
        List<String> active = weekend
                ? plugin.getConfig().getStringList("customitems.eventDrops.weekendActive")
                : plugin.getConfig().getStringList("customitems.eventDrops.active");
        if (active == null || active.isEmpty()) active = List.of("halloween_reaper", "winter_star");
        return CustomItemUtil.makeEventItem(plugin, active.get(ThreadLocalRandom.current().nextInt(active.size())));
    }

    /** Crate-Key rollen; desiredTier == null → kleine Pyramide (Common→Legendary, kein Mystic). */
    public static ItemStack rollKey(FactionsPlugin plugin, Rarity desiredTier, Random rnd) {
        if (desiredTier == null) {
            int r = (rnd == null ? ThreadLocalRandom.current().nextInt(100) : rnd.nextInt(100));
            if (r < 55) desiredTier = Rarity.COMMON;
            else if (r < 80) desiredTier = Rarity.RARE;
            else if (r < 95) desiredTier = Rarity.EPIC;
            else desiredTier = Rarity.LEGENDARY;
        }
        // Bridge nutzen, wenn vorhanden
        Object bridge = crateBridge(plugin);
        if (bridge != null) {
            String crateId = crateIdFor(bridge, desiredTier);
            if (crateId != null) {
                ItemStack key = makeKeyItem(bridge, desiredTier, crateId, 1);
                if (key != null && !key.getType().isAir()) return key;
            }
        }
        if (plugin.getKeyCrateManager() != null) {
            ItemStack key = plugin.getKeyCrateManager().createKeyItem(desiredTier, 1);
            if (key != null && !key.getType().isAir()) return key;
        }
        // Letzter Fallback: generischer Crystal, falls der Key-Manager noch nicht initialisiert ist.
        return fallbackKey(desiredTier, 1);
    }

    /** Garantiert ein sichtbares Crystal-/Key-Item, auch wenn Bridge oder Config gerade nichts liefern. */
    public static ItemStack guaranteedCrystal(FactionsPlugin plugin, Rarity rarity, int amount) {
        Rarity safe = rarity == null ? Rarity.RARE : rarity;
        int safeAmount = Math.max(1, Math.min(64, amount));

        try {
            ItemStack key = rollKey(plugin, safe, null);
            if (key != null && !key.getType().isAir()) {
                key.setAmount(Math.min(safeAmount, key.getMaxStackSize()));
                return key;
            }
        } catch (Throwable ignored) {}

        return fallbackKey(safe, safeAmount);
    }

    /** Kriegsbeute-Voucher: einlösbar per Rechtsklick (EnergyCrystalUseListener), deponiert bei RaidCrystalManager. */
    public static ItemStack crystalShard(FactionsPlugin plugin, int amount) {
        return new com.vetroforge.raid.shop.EnergyCrystalItems(plugin).createCrystalItem(Math.max(1, amount), 1);
    }

    public static ItemStack customEnchantBook(FactionsPlugin plugin, Rarity rarity) {
        CustomEnchant[] values = CustomEnchant.values();
        CustomEnchant enchant = values[ThreadLocalRandom.current().nextInt(values.length)];
        int level = switch (rarity) {
            case COMMON -> 1;
            case RARE -> ThreadLocalRandom.current().nextInt(1, 3);
            case EPIC -> ThreadLocalRandom.current().nextInt(2, 4);
            case LEGENDARY -> ThreadLocalRandom.current().nextInt(3, 5);
            case MYSTIC -> ThreadLocalRandom.current().nextInt(4, 6);
        };

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName(colorFor(rarity) + "Rune: " + enchant.display + " " + roman(level));
        meta.setLore(List.of(
                ChatColor.GRAY + "Rune-Buch",
                ChatColor.DARK_GRAY + "Rarity: " + colorFor(rarity) + rarity.display,
                ChatColor.GRAY + "Im Forge auf ein Item fusionieren."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "custom_enchant_book"), PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "custom_enchant"), PersistentDataType.STRING, enchant.name());
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "custom_enchant_level"), PersistentDataType.INTEGER, level);
        meta.getPersistentDataContainer().set(new NamespacedKey(plugin, "custom_enchant_rarity"), PersistentDataType.STRING, rarity.name());
        book.setItemMeta(meta);
        return book;
    }

    /* ----------------------------------------------------------
     * Crate-Roller (Tier = Rarity)
     * ---------------------------------------------------------- */

    /** Backwards-compatible: ohne Player-Kontext (kein Capture-Bonus). */
    public static ItemStack rollFromCrate(FactionsPlugin plugin, Rarity tier) {
        return rollFromCrate(plugin, tier, null);
    }

    /** Bevorzugt: mit Player-Kontext (Capture-Zone-Bonus auf bessere Drops). */
    public static ItemStack rollFromCrate(FactionsPlugin plugin, Rarity tier, Player ctx) {
        double weightMulHigh = 1.0;
        if (ctx != null && plugin.getFactionManager() != null && plugin.getCaptureZoneBuffService() != null) {
            String fId = plugin.getFactionManager().getFactionOf(ctx.getUniqueId());
            if (fId != null) weightMulHigh = plugin.getCaptureZoneBuffService().weightMultiplier(fId);
        }

        WeightedPicker picker = new WeightedPicker();

        if (tier == null) tier = Rarity.COMMON;

        switch (tier) {
            case COMMON -> {
                picker.add(() -> shadowblade(plugin, Rarity.COMMON),      35, Quality.LOW);
                picker.add(() -> battleHoe(plugin, Rarity.COMMON),        20, Quality.LOW);
                picker.add(() -> randomArmorPiece(plugin, Rarity.COMMON), 35, Quality.LOW);
                picker.add(() -> customEnchantBook(plugin, Rarity.COMMON), 15, Quality.LOW);
                picker.add(() -> rollKey(plugin, Rarity.COMMON, null),    10, Quality.LOW);
                picker.add(() -> crystalShard(plugin, 3),                   5, Quality.LOW);
            }
            case RARE -> {
                picker.add(() -> shadowblade(plugin, Rarity.RARE),       35, Quality.MID);
                picker.add(() -> battleHoe(plugin, Rarity.RARE),         20, Quality.MID);
                picker.add(() -> randomArmorPiece(plugin, Rarity.RARE),   35, Quality.MID);
                picker.add(() -> customEnchantBook(plugin, Rarity.RARE),  15, Quality.MID);
                picker.add(() -> rollKey(plugin, Rarity.RARE, null),      10, Quality.MID);
                picker.add(() -> crystalShard(plugin, 6),                   5, Quality.MID);
            }
            case EPIC -> {
                picker.add(() -> shadowblade(plugin, Rarity.EPIC),       30, Quality.HIGH);
                picker.add(() -> warPickaxe(plugin, Rarity.EPIC),        18, Quality.HIGH);
                picker.add(() -> battleHoe(plugin, Rarity.EPIC),         18, Quality.HIGH);
                picker.add(() -> randomArmorPiece(plugin, Rarity.EPIC),   40, Quality.HIGH);
                picker.add(() -> customEnchantBook(plugin, Rarity.EPIC),  15, Quality.HIGH);
                picker.add(() -> rollKey(plugin, Rarity.EPIC, null),      10, Quality.HIGH);
                picker.add(() -> crystalShard(plugin, 10),                  5, Quality.MID);
            }
            case LEGENDARY -> {
                picker.add(() -> shadowblade(plugin, Rarity.LEGENDARY),          30, Quality.HIGH);
                picker.add(() -> warPickaxe(plugin, Rarity.LEGENDARY),           24, Quality.HIGH);
                picker.add(() -> randomArmorPiece(plugin, Rarity.LEGENDARY),     50, Quality.HIGH);
                picker.add(() -> customEnchantBook(plugin, Rarity.LEGENDARY),    10, Quality.HIGH);
                picker.add(() -> rollKey(plugin, Rarity.LEGENDARY, null),        15, Quality.HIGH);
                picker.add(() -> crystalShard(plugin, 16),                         5, Quality.MID);
            }
            case MYSTIC -> {
                picker.add(() -> dragonsBreath(plugin),                       20, Quality.HIGH);
                picker.add(() -> shadowblade(plugin, Rarity.MYSTIC),          20, Quality.HIGH);
                picker.add(() -> warPickaxe(plugin, Rarity.MYSTIC),           18, Quality.HIGH);
                picker.add(() -> randomArmorPiece(plugin, Rarity.MYSTIC),     35, Quality.HIGH);
                picker.add(() -> customEnchantBook(plugin, Rarity.MYSTIC),    10, Quality.HIGH);
                if (plugin.getConfig().getBoolean("customitems.eventDrops.enabled", true)) {
                    picker.add(() -> eventItem(plugin),                       10, Quality.HIGH);
                }
                picker.add(() -> rollKey(plugin, Rarity.MYSTIC, null),        15, Quality.HIGH);
            }
        }

        return picker.pick(weightMulHigh);
    }

    /* ----------------------------------------------------------
     * Intern: Weighting mit "Qualität" (LOW/MID/HIGH)
     * ---------------------------------------------------------- */
    private enum Quality { LOW, MID, HIGH }
    private interface SupplierEx { ItemStack get(); }

    private static final class Entry {
        final SupplierEx sup; final double base; final Quality q;
        Entry(SupplierEx s, double w, Quality q){ this.sup=s; this.base=w; this.q=q; }
    }
    private static final class WeightedPicker {
        private final List<Entry> entries = new ArrayList<>();
        void add(SupplierEx sup, double weight, Quality q){ if (weight>0) entries.add(new Entry(sup, weight, q)); }
        ItemStack pick(double highMul){
            if (entries.isEmpty()) return new ItemStack(Material.NETHER_STAR);
            double lowDiv = Math.sqrt(Math.max(1.0, highMul));
            double sum = 0.0;
            double[] eff = new double[entries.size()];
            for (int i=0;i<entries.size();i++){
                Entry e = entries.get(i);
                double w = e.base;
                if (e.q == Quality.HIGH) w *= highMul;
                else if (e.q == Quality.LOW) w /= lowDiv;
                eff[i] = Math.max(0.0001, w);
                sum += eff[i];
            }
            double r = ThreadLocalRandom.current().nextDouble(sum);
            for (int i=0;i<entries.size();i++){
                if (r < eff[i]) {
                    ItemStack picked = safeGet(entries.get(i));
                    if (picked != null && !picked.getType().isAir()) return picked;
                    break;
                }
                r -= eff[i];
            }
            for (Entry entry : entries) {
                ItemStack picked = safeGet(entry);
                if (picked != null && !picked.getType().isAir()) return picked;
            }
            return fallbackKey(Rarity.RARE, 1);
        }

        private ItemStack safeGet(Entry entry) {
            try {
                return entry.sup.get();
            } catch (Throwable ignored) {
                return null;
            }
        }
    }

    /* ----------------------------------------------------------
     * Bridge + Fallback für Keys (Reflection, keine harte Dep.)
     * ---------------------------------------------------------- */

    private static Object crateBridge(FactionsPlugin plugin){
        try { return plugin.getClass().getMethod("getCrateBridge").invoke(plugin); }
        catch (Throwable ignored) { return null; }
    }
    private static String crateIdFor(Object bridge, Rarity r){
        try {
            var m = bridge.getClass().getMethod("crateIdFor", Rarity.class);
            Object v = m.invoke(bridge, r);
            return (v == null) ? null : String.valueOf(v);
        } catch (Throwable ignored) { return null; }
    }
    private static ItemStack makeKeyItem(Object bridge, Rarity r, String crateId, int amount){
        try {
            var m = bridge.getClass().getMethod("makeKeyItem", Rarity.class, String.class, int.class);
            Object v = m.invoke(bridge, r, crateId, amount);
            return (v instanceof ItemStack is) ? is : null;
        } catch (Throwable ignored) { return null; }
    }
    private static ItemStack fallbackKey(Rarity r, int amount){
        ItemStack it = new ItemStack(Material.NETHER_STAR, Math.max(1, amount));
        ItemMeta im = it.getItemMeta();
        String display = r == Rarity.MYSTIC ? "Mystic" : r.display;
        im.setDisplayName(colorFor(r) + ChatColor.BOLD + display + " Crystal");
        im.setLore(List.of(
                ChatColor.GRAY + "Dieser " + display + " Crystal kann",
                ChatColor.GRAY + "beim Crystal NPC eingelöst werden."
        ));
        it.setItemMeta(im);
        return it;
    }
    private static String colorFor(Rarity r){
        return switch (r){
            case COMMON -> ChatColor.GRAY.toString();
            case RARE -> ChatColor.BLUE.toString();
            case EPIC -> ChatColor.LIGHT_PURPLE.toString();
            case LEGENDARY -> ChatColor.GOLD.toString();
            case MYSTIC -> ChatColor.LIGHT_PURPLE.toString();
        };
    }

    private static String roman(int level) {
        return switch (Math.max(1, Math.min(5, level))) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            default -> "V";
        };
    }
}
