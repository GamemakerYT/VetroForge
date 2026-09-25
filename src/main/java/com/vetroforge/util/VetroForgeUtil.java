package com.vetroforge.util;

import com.vetroforge.FactionsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Map;

public final class VetroForgeUtil {

    private VetroForgeUtil() {
    }

    public static Component prefix() {
        return Component.text("VetroForge", NamedTextColor.AQUA)
                .decorate(TextDecoration.BOLD)
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY));
    }

    public static String legacyPrefix() {
        return "§b§lVetroForge §8» §7";
    }

    public static String color(String text) {
        return ChatColor.translateAlternateColorCodes('&', text == null ? "" : text);
    }

    public static String tr(FactionsPlugin plugin, Player player, String key) {
        return color(plugin.getI18n().tr(player, key));
    }

    public static String tr(FactionsPlugin plugin, Player player, String key, Map<String, String> vars) {
        return color(plugin.getI18n().tr(player, key, vars));
    }

    public static Map<String, String> vars(Object... values) {
        Map<String, String> vars = new LinkedHashMap<>();
        for (int i = 0; i + 1 < values.length; i += 2) {
            vars.put(String.valueOf(values[i]), String.valueOf(values[i + 1]));
        }
        return vars;
    }

    public static ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(Arrays.asList(lore));
        item.setItemMeta(meta);
        return item;
    }

    public static void fill(Inventory inventory, Material material) {
        ItemStack filler = item(material, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            inventory.setItem(slot, filler);
        }
    }
}
