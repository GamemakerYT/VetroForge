// src/main/java/com/vetroforge/util/HeadUtil.java
package com.vetroforge.util;

import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.net.URL;
import java.util.List;

public class HeadUtil {

    /** Baut einen Spieler-Kopf mit Skin-URL (nur Paper). Fällt auf Banner zurück, wenn Paper-API nicht verfügbar. */
    public static ItemStack skullFromUrl(String url, String displayName, List<String> lore, Material fallbackBanner) {
        try {
            // Paper API (1.19+): org.bukkit.profile.PlayerProfile
            Class<?> profileClass = Class.forName("org.bukkit.profile.PlayerProfile");
            Class<?> texturesClass = Class.forName("org.bukkit.profile.PlayerTextures");

            Object profile = Bukkit.createProfile(java.util.UUID.randomUUID(), "flag");
            Object textures = profileClass.getMethod("getTextures").invoke(profile);
            URL u = new URL(url);
            texturesClass.getMethod("setSkin", URL.class).invoke(textures, u);

            ItemStack skull = new ItemStack(Material.PLAYER_HEAD, 1);
            ItemMeta meta = skull.getItemMeta();
            meta.setDisplayName(displayName);
            if (lore != null) meta.setLore(lore);
            // meta#setOwnerProfile (Paper)
            meta.getClass().getMethod("setOwnerProfile", profileClass).invoke(meta, profile);
            skull.setItemMeta(meta);
            return skull;
        } catch (Throwable t) {
            // Fallback
            ItemStack banner = new ItemStack(fallbackBanner == null ? Material.PAPER : fallbackBanner);
            ItemMeta m = banner.getItemMeta();
            m.setDisplayName(displayName);
            if (lore != null) m.setLore(lore);
            banner.setItemMeta(m);
            return banner;
        }
    }
}