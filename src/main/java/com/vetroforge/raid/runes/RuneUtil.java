package com.vetroforge.raid.runes;

import org.bukkit.NamespacedKey;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;

public final class RuneUtil {
    private RuneUtil(){}

    public static final String PDC_RUNE = "runes";

    public static boolean hasRune(org.bukkit.plugin.Plugin plugin, ItemStack it, RuneType type) {
        if (it == null || !it.hasItemMeta()) return false;
        ItemMeta im = it.getItemMeta();
        var pdc = im.getPersistentDataContainer();
        String data = pdc.get(new NamespacedKey(plugin, PDC_RUNE), PersistentDataType.STRING);
        if (data == null || data.isEmpty()) return false;
        for (String t : data.split(",")) if (t.equalsIgnoreCase(type.name())) return true;
        return false;
    }

    public static void addRune(org.bukkit.plugin.Plugin plugin, ItemStack it, RuneType type) {
        if (it == null) return;
        ItemMeta im = it.getItemMeta();
        var pdc = im.getPersistentDataContainer();

        NamespacedKey key = new NamespacedKey(plugin, PDC_RUNE);
        String data = pdc.get(key, PersistentDataType.STRING);
        List<String> list = new ArrayList<>();
        if (data != null && !data.isEmpty()) for (String s : data.split(",")) list.add(s);
        if (!list.contains(type.name())) list.add(type.name());
        pdc.set(key, PersistentDataType.STRING, String.join(",", list));

        List<String> lore = im.hasLore() ? new ArrayList<>(im.getLore()) : new ArrayList<>();
        lore.add(type.display());
        im.setLore(lore);
        it.setItemMeta(im);
    }
}