// src/main/java/com/vetroforge/items/CustomItemCatalog.java
package com.vetroforge.items;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;

public final class CustomItemCatalog {

    private CustomItemCatalog() {}

    @FunctionalInterface
    public interface Factory {
        ItemStack make(FactionsPlugin plugin, Rarity rarity);
    }

    public static final class Entry {
        public final String id;         // für Befehle: z.B. "shadowblade"
        public final String display;    // schöner Name in der Liste
        public final boolean hasRarity; // ob das Item Rarity-Varianten hat
        public final Factory factory;

        public Entry(String id, String display, boolean hasRarity, Factory factory) {
            this.id = id; this.display = display; this.hasRarity = hasRarity; this.factory = factory;
        }
    }

    /** Hier ALLE Custom-Items eintragen. */
    public static List<Entry> all() {
        List<Entry> list = new ArrayList<>();

        // Shadowblade (mit Rarity)
        list.add(new Entry(
                "shadowblade",
                "Shadowblade",
                true,
                (plugin, rarity) -> CustomItemUtil.makeShadowblade(plugin, rarity == null ? Rarity.RARE : rarity)
        ));

        list.add(new Entry(
                "war_pickaxe",
                "Breach Pick",
                true,
                (plugin, rarity) -> CustomItemUtil.makeWarPickaxe(plugin, rarity == null ? Rarity.EPIC : rarity)
        ));

        list.add(new Entry(
                "battle_hoe",
                "Battle Hoe",
                true,
                (plugin, rarity) -> CustomItemUtil.makeBattleHoe(plugin, rarity == null ? Rarity.RARE : rarity)
        ));

        list.add(new Entry(
                "reaper_scythe",
                "Reaper Scythe",
                true,
                (plugin, rarity) -> CustomItemUtil.makeReaperScythe(plugin, rarity == null ? Rarity.RARE : rarity)
        ));

        list.add(new Entry(
                "titanbreaker",
                "Titanbreaker",
                true,
                (plugin, rarity) -> CustomItemUtil.makeTitanbreaker(plugin, rarity == null ? Rarity.RARE : rarity)
        ));

        list.add(new Entry(
                "frostfang",
                "Frostfang",
                true,
                (plugin, rarity) -> CustomItemUtil.makeFrostfang(plugin, rarity == null ? Rarity.RARE : rarity)
        ));

        // Mystic-only artifacts without a rarity selector.
        list.add(new Entry(
                "dragons_breath",
                "Dragon's Breath",
                false,
                (plugin, rarity) -> CustomItemUtil.makeDragonsBreath(plugin)
        ));

        list.add(new Entry(
                "stormcaller",
                "Stormcaller",
                false,
                (plugin, rarity) -> CustomItemUtil.makeStormcaller(plugin)
        ));

        addArmorSet(list, "scout", "Scout", Material.LEATHER_HELMET, Material.LEATHER_CHESTPLATE,
                Material.LEATHER_LEGGINGS, Material.LEATHER_BOOTS);
        addArmorSet(list, "ranger", "Ranger", Material.CHAINMAIL_HELMET, Material.CHAINMAIL_CHESTPLATE,
                Material.CHAINMAIL_LEGGINGS, Material.CHAINMAIL_BOOTS);
        addArmorSet(list, "fortune", "Fortune", Material.GOLDEN_HELMET, Material.GOLDEN_CHESTPLATE,
                Material.GOLDEN_LEGGINGS, Material.GOLDEN_BOOTS);
        addArmorSet(list, "vanguard", "Vanguard", Material.IRON_HELMET, Material.IRON_CHESTPLATE,
                Material.IRON_LEGGINGS, Material.IRON_BOOTS);
        addArmorSet(list, "ender", "Ender", Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS);
        addArmorSet(list, "shadow", "Shadow", Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);
        addArmorSet(list, "titan", "Titan", Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);
        addArmorSet(list, "ember", "Ember", Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS);
        addArmorSet(list, "void", "Void", Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);
        addArmorSet(list, "raider", "Raider", Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);
        addArmorSet(list, "guardian", "Guardian", Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS);
        addArmorSet(list, "berserker", "Berserker", Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);
        addArmorSet(list, "prospector", "Prospector", Material.DIAMOND_HELMET, Material.DIAMOND_CHESTPLATE,
                Material.DIAMOND_LEGGINGS, Material.DIAMOND_BOOTS);
        addArmorSet(list, "season_one", "Season I", Material.NETHERITE_HELMET, Material.NETHERITE_CHESTPLATE,
                Material.NETHERITE_LEGGINGS, Material.NETHERITE_BOOTS);

        list.add(new Entry(
                "halloween_reaper",
                "Halloween Reaper",
                false,
                (plugin, rarity) -> CustomItemUtil.makeEventItem(plugin, "halloween_reaper")
        ));
        list.add(new Entry(
                "winter_star",
                "Winter Star",
                false,
                (plugin, rarity) -> CustomItemUtil.makeEventItem(plugin, "winter_star")
        ));

        return list;
    }

    private static void addArmorSet(
            List<Entry> list,
            String id,
            String display,
            Material helmet,
            Material chestplate,
            Material leggings,
            Material boots
    ) {
        list.add(armor(id, display, "helmet", helmet));
        list.add(armor(id, display, "chestplate", chestplate));
        list.add(armor(id, display, "leggings", leggings));
        list.add(armor(id, display, "boots", boots));
    }

    private static Entry armor(String id, String display, String piece, Material material) {
        return new Entry(
                id + "_" + piece,
                display + " " + Character.toUpperCase(piece.charAt(0)) + piece.substring(1),
                true,
                (plugin, rarity) -> CustomItemUtil.makeArmorPiece(plugin, id, material, rarity == null ? Rarity.RARE : rarity)
        );
    }
}
