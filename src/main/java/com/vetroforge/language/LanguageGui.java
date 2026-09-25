// src/main/java/com/vetroforge/language/LanguageGui.java
package com.vetroforge.language;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class LanguageGui implements Listener {
    private final FactionsPlugin plugin;
    private final NamespacedKey KEY_TAG;

    public LanguageGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.KEY_TAG = new NamespacedKey(plugin, "lang_gui_tag");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        String title = safeTr(p, "language.gui.title");
        Inventory inv = Bukkit.createInventory(new LangHolder(), 27, title);

        // Hintergrund
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta(); pm.setDisplayName(" "); pane.setItemMeta(pm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);

        // Sprachen
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

        // Close
        String closeName = safeTr(p, "language.gui.close");
        List<String> closeLore = new ArrayList<>();
        String closeLoreLine = safeTr(p, "language.gui.close_lore");
        if (closeLoreLine != null && !closeLoreLine.isBlank()) closeLore.add(closeLoreLine);
        inv.setItem(22, action(Material.BARRIER, closeName, "close", closeLore));

        p.openInventory(inv);
    }

    private ItemStack langIcon(String lang) {
        String name = safeTr(lang, "language.item." + lang + ".name");

        List<String> lore = new ArrayList<>();
        String loreLine = safeTr(lang, "language.item." + lang + ".lore");
        if (loreLine != null && !loreLine.isBlank()) lore.add(loreLine);

        ItemStack icon = tryHead(lang, name, lore);
        ItemMeta m = icon.getItemMeta();
        setTag(m, "lang:" + lang);
        icon.setItemMeta(m);
        return icon;
    }

    // versucht com.vetroforge.util.HeadUtil.skullFromUrl(...), fällt auf Banner zurück
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
        // Fallback: Banner
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
        setTag(m, "action:" + tag);
        it.setItemMeta(m);
        return it;
    }

    private void setTag(ItemMeta m, String value) {
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(KEY_TAG, PersistentDataType.STRING, value);
        // Versuch, zusätzlich LocalizedName zu setzen (falls existiert)
        try { m.getClass().getMethod("setLocalizedName", String.class).invoke(m, value); } catch (Throwable ignored) {}
    }

    private String getTag(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        ItemMeta m = it.getItemMeta();
        String v = m.getPersistentDataContainer().get(KEY_TAG, PersistentDataType.STRING);
        if (v != null) return v;
        try {
            Object ret = m.getClass().getMethod("getLocalizedName").invoke(m);
            return ret == null ? null : String.valueOf(ret);
        } catch (Throwable ignored) {}
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        Inventory top = e.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof LangHolder)) return;

        ItemStack cur = e.getCurrentItem();
        if (cur == null || !cur.hasItemMeta()) return;

        e.setCancelled(true);

        String tag = getTag(cur);
        if (tag == null) return;

        if (tag.startsWith("lang:")) {
            String lang = tag.substring(5);

            // Sprache setzen (WikiManager bevorzugt, sonst I18n)
            var wiki = plugin.getWikiManager();
            if (wiki != null) {
                try { wiki.setLanguageFor(p, lang, true); } catch (Throwable ignored) {}
            } else {
                trySetLangFallback(p, lang);
            }

            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.05f);

            // Java-8-sicher: ohne Map.of – einfacher Platzhalter
            String msg = safeTr(p, "language.set");
            msg = msg.replace("{lang}", lang.toUpperCase(Locale.ROOT));
            p.sendMessage(msg);

            p.closeInventory();

            if (wiki != null) {
                try { wiki.openWiki(p); } catch (Throwable ignored) {}
            }
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

    private static final class LangHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private void trySetLangFallback(Player p, String lang) {
        Object i18n = plugin.getI18n();
        if (i18n == null) return;
        Class<?> c = i18n.getClass();
        try { c.getMethod("setLanguage", Player.class, String.class).invoke(i18n, p, lang); return; } catch (Throwable ignored) {}
        try { c.getMethod("setLanguage", java.util.UUID.class, String.class).invoke(i18n, p.getUniqueId(), lang); return; } catch (Throwable ignored) {}
        try { c.getMethod("setPlayerLanguage", Player.class, String.class).invoke(i18n, p, lang); return; } catch (Throwable ignored) {}
        try { c.getMethod("setPlayerLangKey", Player.class, String.class).invoke(i18n, p, lang); } catch (Throwable ignored) {}
    }
}
