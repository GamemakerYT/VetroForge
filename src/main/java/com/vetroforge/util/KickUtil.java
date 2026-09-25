// src/main/java/com/vetroforge/util/KickUtil.java
package com.vetroforge.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

public final class KickUtil {
    private KickUtil(){}

    /** Kickt mit Legacy-Farbcodes (§/&) – zeigt Farben auch auf Paper korrekt an. */
    public static void kick(OfflinePlayer off, String legacyMessage) {
        if (off == null) return;
        Player p = off.getPlayer();
        if (p == null) return;

        String msg = legacyMessage == null ? "" : legacyMessage;
        try {
            // & → § umwandeln, dann als Adventure-Komponente setzen
            String withSection = ChatColor.translateAlternateColorCodes('&', msg);
            Component comp = LegacyComponentSerializer.legacySection().deserialize(withSection);
            p.kick(comp);
        } catch (Throwable ignore) {
            try {
                // Spigot-Fallback
                //noinspection deprecation
                p.kickPlayer(ChatColor.stripColor(msg));
            } catch (Throwable ignored2) {}
        }
    }
}