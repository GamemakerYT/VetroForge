// src/main/java/com/vetroforge/world/vworld/VWorldWand.java
package com.vetroforge.world.vworld;

import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.List;

public final class VWorldWand {

    private static final String KEY = "vworld_wand";

    private VWorldWand() {
    }

    public static ItemStack create(JavaPlugin plugin) {
        ItemStack item = new ItemStack(Material.BLAZE_ROD);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName("§6§lVWorld Wand");
            meta.setLore(List.of(
                    "§8VetroForge Tool",
                    "§7Left Click §8» §fSet Pos1",
                    "§7Right Click §8» §fSet Pos2",
                    "",
                    "§e/vworld region set <type>"
            ));

            meta.getPersistentDataContainer().set(
                    key(plugin),
                    PersistentDataType.STRING,
                    "true"
            );

            item.setItemMeta(meta);
        }

        return item;
    }

    public static boolean isWand(JavaPlugin plugin, ItemStack item) {
        if (item == null || item.getType() != Material.BLAZE_ROD) {
            return false;
        }

        ItemMeta meta = item.getItemMeta();

        if (meta == null) {
            return false;
        }

        String value = meta.getPersistentDataContainer().get(
                key(plugin),
                PersistentDataType.STRING
        );

        return "true".equalsIgnoreCase(value);
    }

    private static NamespacedKey key(JavaPlugin plugin) {
        return new NamespacedKey(plugin, KEY);
    }
}