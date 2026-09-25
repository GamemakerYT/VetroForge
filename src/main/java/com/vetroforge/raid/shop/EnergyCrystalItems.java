package com.vetroforge.raid.shop;

import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.Arrays;
import java.util.Locale;

public final class EnergyCrystalItems {
    private final FactionsPlugin plugin;
    private final NamespacedKey KEY_IS;
    private final NamespacedKey KEY_VAL;

    public EnergyCrystalItems(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.KEY_IS  = new NamespacedKey(plugin, "energy_crystal");
        this.KEY_VAL = new NamespacedKey(plugin, "ec_value");
    }

    public ItemStack createCrystalItem(int value, int amount) {
        Material mat = matFromCfg();
        ItemStack it = new ItemStack(mat, Math.max(1, Math.min(amount, mat.getMaxStackSize())));
        ItemMeta im = it.getItemMeta();

        String name = plugin.getConfig().getString("raid.crystal.item.name", "&dKriegsbeute-Splitter");
        im.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        im.setLore(Arrays.asList(
                ChatColor.GRAY + "Wert: " + ChatColor.WHITE + value + " ✦",
                ChatColor.DARK_GRAY + "Rechtsklick: einzahlen",
                ChatColor.DARK_GRAY + "Sneak + Rechtsklick: an Faction (Raid)"
        ));

        im.getPersistentDataContainer().set(KEY_IS,  PersistentDataType.BYTE, (byte)1);
        im.getPersistentDataContainer().set(KEY_VAL, PersistentDataType.INTEGER, Math.max(1, value));
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(im);
        return it;
    }

    public boolean isCrystal(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;
        Byte b = it.getItemMeta().getPersistentDataContainer().get(KEY_IS, PersistentDataType.BYTE);
        return b != null && b == (byte)1;
    }

    public int valueOf(ItemStack it) {
        if (!isCrystal(it)) return 0;
        Integer v = it.getItemMeta().getPersistentDataContainer().get(KEY_VAL, PersistentDataType.INTEGER);
        return (v == null || v <= 0) ? 1 : v;
    }

    private Material matFromCfg() {
        String m = plugin.getConfig().getString("raid.crystal.item.material", "AMETHYST_SHARD");
        Material mat = Material.matchMaterial(m == null ? "" : m.toUpperCase(Locale.ROOT));
        return mat != null ? mat : Material.AMETHYST_SHARD;
    }
}