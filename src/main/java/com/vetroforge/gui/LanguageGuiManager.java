// src/main/java/com/vetroforge/gui/LanguageGuiManager.java
package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.profile.PlayerProfile;
import org.bukkit.profile.PlayerTextures;
import com.vetroforge.i18n.LanguageManager;

import java.net.URI;
import java.net.URL;
import java.util.*;

public class LanguageGuiManager implements Listener {

    private final FactionsPlugin plugin;
    private final NamespacedKey KEY_TAG;

    public LanguageGuiManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.KEY_TAG = new NamespacedKey(plugin, "lang_gui_tag");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** GUI öffnen */
    public void openSelector(Player p) {
        Inventory inv = Bukkit.createInventory(new Holder(), 27,
                cc(tr(p, "language.gui.title"))); // "&fSprache wählen"
        fill(inv, pane());

        // Reihenfolge & Labels
        addLangItem(inv, 10, p, "de", "language.gui.item.de");
        addLangItem(inv, 12, p, "en", "language.gui.item.en");
        addLangItem(inv, 14, p, "fr", "language.gui.item.fr");
        addLangItem(inv, 16, p, "nl", "language.gui.item.nl");

        // Info
        inv.setItem(22, info(Material.PAPER, cc(tr(p, "language.gui.pick"))));

        p.openInventory(inv);
    }

    private void addLangItem(Inventory inv, int slot, Player p, String code, String keyName) {
        ItemStack icon = flagItem(p, code, keyName);
        ItemMeta im = icon.getItemMeta();
        setTag(im, "lang:" + code);
        icon.setItemMeta(im);
        inv.setItem(slot, icon);
    }

    /** Versucht Kopf mit Skin-URL, sonst fällt auf Wolle zurück. */
    private ItemStack flagItem(Player p, String code, String keyName) {
        boolean useHeads = plugin.getConfig().getBoolean("i18n.flags.useHeads", false);
        String url = plugin.getConfig().getString("i18n.flags.textures."+code, "");
        String name = cc(tr(p, keyName)); // "&fDeutsch" etc.

        if (useHeads && url != null && !url.isBlank()) {
            try {
                ItemStack head = new ItemStack(Material.PLAYER_HEAD);
                SkullMeta sm = (SkullMeta) head.getItemMeta();

                PlayerProfile profile = Bukkit.createPlayerProfile(UUID.randomUUID(), null);
                PlayerTextures tx = profile.getTextures();

                boolean set = false;
                try {
                    // Paper: setSkin(URI)
                    URI uri = new URI(url);
                    PlayerTextures.class.getMethod("setSkin", URI.class).invoke(tx, uri);
                    set = true;
                } catch (Throwable ignored) {
                    // Manche Builds: setSkin(URL)
                    try {
                        URL u = new URL(url);
                        PlayerTextures.class.getMethod("setSkin", URL.class).invoke(tx, u);
                        set = true;
                    } catch (Throwable ignored2) {}
                }

                if (set) {
                    profile.setTextures(tx);
                    sm.setOwnerProfile(profile);
                    sm.setDisplayName(name);
                    head.setItemMeta(sm);
                    return head;
                }
            } catch (Throwable ignored) {
                // Fallback unten
            }
        }

        // Fallback: farbige Wolle
        Material fallback = switch (code) {
            case "de" -> Material.RED_WOOL;
            case "en" -> Material.BLUE_WOOL;
            case "fr" -> Material.LAPIS_BLOCK;
            case "nl" -> Material.ORANGE_WOOL;
            default -> Material.WHITE_WOOL;
        };
        ItemStack wool = new ItemStack(fallback);
        ItemMeta im = wool.getItemMeta();
        im.setDisplayName(name);
        wool.setItemMeta(im);
        return wool;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String tag = getTag(it);
        if (tag == null || !tag.startsWith("lang:")) return;

        String code = tag.substring(5);

        // Sprache setzen – robust
        setLanguage(p, code);

        p.closeInventory();
        p.sendMessage(cc(tr(p, "language.set.ok", Map.of("lang", code))));
    }

    /* ===================== Language/I18n Wrappers ===================== */

    private void setLanguage(Player p, String lang) {
        // 1) LanguageManager bevorzugen, mehrere Signaturen testen
        Object lm = plugin.getLanguageManager();
        if (lm != null) {
            Class<?> c = lm.getClass();
            try { c.getMethod("setLanguage", Player.class, String.class, boolean.class).invoke(lm, p, lang, true); return; } catch (Throwable ignored) {}
            try { c.getMethod("setLanguage", Player.class, String.class).invoke(lm, p, lang); return; } catch (Throwable ignored) {}
            try { c.getMethod("setLanguageFor", Player.class, String.class, boolean.class).invoke(lm, p, lang, true); return; } catch (Throwable ignored) {}
            try { c.getMethod("setPlayerLanguage", Player.class, String.class).invoke(lm, p, lang); return; } catch (Throwable ignored) {}
            try { c.getMethod("setPlayerLangKey", Player.class, String.class).invoke(lm, p, lang); return; } catch (Throwable ignored) {}
            try { c.getMethod("setLanguage", UUID.class, String.class).invoke(lm, p.getUniqueId(), lang); return; } catch (Throwable ignored) {}
        }

        // 2) Fallback über I18n-Alias
        Object i18n = plugin.getI18n();
        if (i18n != null) {
            Class<?> c = i18n.getClass();
            try { c.getMethod("setLanguage", Player.class, String.class).invoke(i18n, p, lang); return; } catch (Throwable ignored) {}
            try { c.getMethod("setLanguage", UUID.class, String.class).invoke(i18n, p.getUniqueId(), lang); return; } catch (Throwable ignored) {}
            try { c.getMethod("setPlayerLanguage", Player.class, String.class).invoke(i18n, p, lang); return; } catch (Throwable ignored) {}
            try { c.getMethod("setPlayerLangKey", Player.class, String.class).invoke(i18n, p, lang); return; } catch (Throwable ignored) {}
        }
    }

    private String tr(Player p, String key) {
        Object i18n = plugin.getI18n();
        if (i18n != null) {
            Class<?> c = i18n.getClass();
            try { Object r = c.getMethod("tr", Player.class, String.class).invoke(i18n, p, key); if (r != null) return String.valueOf(r); } catch (Throwable ignored) {}
            try { Object r = c.getMethod("tr", String.class).invoke(i18n, key); if (r != null) return String.valueOf(r); } catch (Throwable ignored) {}
        }
        return key; // Fallback: key anzeigen
    }

    private String tr(Player p, String key, Map<String, Object> vars) {
        Object i18n = plugin.getI18n();
        if (i18n != null) {
            Class<?> c = i18n.getClass();
            try { Object r = c.getMethod("tr", Player.class, String.class, Map.class).invoke(i18n, p, key, vars); if (r != null) return String.valueOf(r); } catch (Throwable ignored) {}
            try { Object r = c.getMethod("tr", String.class, Map.class).invoke(i18n, key, vars); if (r != null) return String.valueOf(r); } catch (Throwable ignored) {}
        }
        // Fallback: sehr simpler Replace
        String out = key;
        for (var e : vars.entrySet()) out = out.replace("{"+e.getKey()+"}", String.valueOf(e.getValue()));
        return out;
    }

    /* ===================== PDC Tag Helpers ===================== */

    private void setTag(ItemMeta m, String value) {
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        pdc.set(KEY_TAG, PersistentDataType.STRING, value);
    }

    private String getTag(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        String v = m.getPersistentDataContainer().get(KEY_TAG, PersistentDataType.STRING);
        return (v == null || v.isBlank()) ? null : v;
    }

    /* ===================== UI Utils ===================== */

    private static String cc(String s){ return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }
    private static void fill(Inventory inv, ItemStack f){ for (int i=0;i<inv.getSize();i++) inv.setItem(i,f); }
    private static ItemStack pane(){ ItemStack it=new ItemStack(Material.GRAY_STAINED_GLASS_PANE); ItemMeta im=it.getItemMeta(); im.setDisplayName(" "); it.setItemMeta(im); return it; }
    private static ItemStack info(Material m, String name){ ItemStack it=new ItemStack(m); ItemMeta im=it.getItemMeta(); im.setDisplayName(name); it.setItemMeta(im); return it; }

    private static final class Holder implements InventoryHolder { @Override public Inventory getInventory(){ return null; } }
}
