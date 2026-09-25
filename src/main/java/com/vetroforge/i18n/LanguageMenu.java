// src/main/java/com/vetroforge/i18n/LanguageMenu.java
package com.vetroforge.i18n;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class LanguageMenu implements Listener {

    // Singleton-Instanz, damit das Listener-Objekt nur einmal registriert wird
    private static LanguageMenu INSTANCE;

    public static void open(FactionsPlugin plugin, Player p){
        if (INSTANCE == null) {
            INSTANCE = new LanguageMenu(plugin);
            plugin.getServer().getPluginManager().registerEvents(INSTANCE, plugin);
        }
        INSTANCE.openInternal(p);
    }

    /* ===== Instanz-Teil ===== */

    private final FactionsPlugin plugin;
    private final NamespacedKey KEY_LANG;

    private LanguageMenu(FactionsPlugin plugin){
        this.plugin = plugin;
        this.KEY_LANG = new NamespacedKey(plugin, "lang");
    }

    private void openInternal(Player p){
        String title = safeTr(p, "language.gui.title"); // z. B. "&fSprache wählen"
        Inventory inv = Bukkit.createInventory(new Holder(), 27, title);

        // Hintergrund füllen
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta();
        pm.setDisplayName(" ");
        pane.setItemMeta(pm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);

        // Sprachen (aus config.language.available oder Default)
        List<String> langs = plugin.getConfig().getStringList("language.available");
        if (langs == null || langs.isEmpty()) langs = Arrays.asList("de","en","fr","nl");
        langs = langs.stream()
                .map(lang -> lang.toLowerCase(Locale.ROOT))
                .filter(plugin.getI18n().getSupported()::contains)
                .distinct()
                .toList();
        int[] slots = {10, 12, 14, 16};
        int idx = 0;
        for (String lang : langs) {
            if (idx >= slots.length) break;
            inv.setItem(slots[idx++], langIcon(lang.toLowerCase(Locale.ROOT)));
        }

        // Close-Button
        String closeName = safeTr(p, "language.gui.close");      // z. B. "&cSchließen"
        String closeLore = safeTr(p, "language.gui.close_lore"); // optionaler Lore-Text
        List<String> closeLoreList = new ArrayList<>();
        if (closeLore != null && !closeLore.isBlank()) closeLoreList.add(closeLore);
        inv.setItem(22, action(Material.BARRIER, closeName, "close", closeLoreList));

        p.openInventory(inv);
    }

    private ItemStack langIcon(String lang) {
        String name = safeTr(lang, "language.item." + lang + ".name"); // z. B. "&eDeutsch"
        List<String> lore = new ArrayList<>();
        String loreLine = safeTr(lang, "language.item." + lang + ".lore");
        if (loreLine != null && !loreLine.isBlank()) lore.add(loreLine);

        ItemStack icon = tryHead(lang, name, lore);
        ItemMeta m = icon.getItemMeta();
        // Tag: "lang:de"
        m.getPersistentDataContainer().set(KEY_LANG, PersistentDataType.STRING, "lang:" + lang);
        icon.setItemMeta(m);
        return icon;
    }

    // versucht com.vetroforge.util.HeadUtil.skullFromUrl(...), fällt auf Banner/Paper zurück
    @SuppressWarnings("unchecked")
    private ItemStack tryHead(String lang, String name, List<String> lore) {
        try {
            String url = plugin.getConfig().getString("language.items." + lang + ".headTextureUrl", "");
            Class<?> hu = Class.forName("com.vetroforge.util.HeadUtil");
            java.lang.reflect.Method m = hu.getMethod("skullFromUrl", String.class, String.class, List.class, Material.class);
            Material fallback = switch (lang) {
                case "de" -> Material.RED_BANNER;
                case "fr" -> Material.BLUE_BANNER;
                case "nl" -> Material.ORANGE_BANNER;
                default -> Material.PAPER;
            };
            Object it = m.invoke(null, url, name, lore, fallback);
            if (it instanceof ItemStack) return (ItemStack) it;
        } catch (Throwable ignored) { }
        // Fallback
        Material fallback = switch (lang) {
            case "de" -> Material.RED_BANNER;
            case "fr" -> Material.BLUE_BANNER;
            case "nl" -> Material.ORANGE_BANNER;
            default -> Material.PAPER;
        };
        ItemStack it = new ItemStack(fallback);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) im.setLore(lore);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack action(Material mat, String name, String tag, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        if (lore != null && !lore.isEmpty()) m.setLore(lore);
        m.getPersistentDataContainer().set(KEY_LANG, PersistentDataType.STRING, "action:" + tag);
        it.setItemMeta(m);
        return it;
    }

    private String getTag(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(KEY_LANG, PersistentDataType.STRING);
    }

    /* ===== Click-Handling ===== */

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e){
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder)) return; // Nur unser Menü
        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String tag = getTag(it);
        if (tag == null) return;

        if (tag.startsWith("lang:")) {
            String lang = tag.substring(5);

            // Sprache direkt über I18nManager setzen (fix für dein ursprüngliches Problem)
            plugin.getI18n().setLanguage(p, lang);

            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.05f);

            String msg = safeTr(p, "language.set"); // z. B. "&aSprache gesetzt: {lang}"
            if (msg == null || msg.equals("language.set")) {
                // Fallback, falls key fehlt
                msg = ChatColor.GREEN + "Sprache gesetzt: {lang}";
            }
            msg = msg.replace("{lang}", lang.toUpperCase(Locale.ROOT));
            p.sendMessage(ChatColor.translateAlternateColorCodes('&', msg));

            p.closeInventory();

            // Optional: Wiki öffnen, wenn vorhanden
            try { if (plugin.getWikiManager() != null) plugin.getWikiManager().open(p); } catch (Throwable ignored) {}
            return;
        }

        if ("action:close".equals(tag)) {
            p.closeInventory();
        }
    }

    private String safeTr(Player p, String key) {
        try { return plugin.getI18n().tr(p, key); } catch (Throwable ignored) { }
        return ChatColor.translateAlternateColorCodes('&', "&7" + key);
    }
    private String safeTr(String lang, String key) {
        try { return plugin.getI18n().tr(lang, key); } catch (Throwable ignored) { }
        return ChatColor.translateAlternateColorCodes('&', "&7" + key);
    }

    /* ===== Holder zum sicheren Identifizieren des Menüs ===== */
    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory(){ return null; }
    }
}
