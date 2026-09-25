package com.vetroforge.items;

import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class ItemSafety {
    public static final NamespacedKey FAVORITE = new NamespacedKey("vetroforge", "favorite");
    public static final String LORE = "§8» §bLieblingsitem geschützt";
    public static boolean readOnly(org.bukkit.inventory.Inventory inventory) {
        return inventory != null && inventory.getHolder() instanceof ReadOnlyItemMenu;
    }
    public static boolean locked(ItemStack item) {
        return item != null && item.hasItemMeta()
                && item.getItemMeta().getPersistentDataContainer().has(FAVORITE, PersistentDataType.BYTE);
    }
    public static boolean deny(Player player, ItemStack... items) {
        for (ItemStack item : items) if (locked(item)) {
            player.sendMessage("§9§lVetroForge §8» §cLieblingsitem geschützt. Erst mit /itemlock off entsperren.");
            return true;
        }
        return false;
    }
    private ItemSafety() {}
}
