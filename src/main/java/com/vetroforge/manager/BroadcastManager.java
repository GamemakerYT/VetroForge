// src/main/java/com/vetroforge/manager/BroadcastManager.java
package com.vetroforge.manager;

import com.vetroforge.util.GradientUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class BroadcastManager {

    /** Wartungs-Broadcast im Block-Stil mit Header */
    public void broadcastMaintenance(Integer time, String reason) {
        String header = GradientUtil.header("§cMaintenance", "");
        Bukkit.broadcastMessage(header);
        if (time != null) Bukkit.broadcastMessage("§fIn " + time + " Sekunden");
        if (reason != null && !reason.isBlank()) Bukkit.broadcastMessage("§fGrund: " + reason);
        Bukkit.broadcastMessage("§cAlle Spieler werden gekickt.");
        Bukkit.broadcastMessage("§8——————————————");
    }

    /** Schlichter Broadcast + ActionBar an alle */
    public void broadcastPlain(String legacyColoredMessage) {
        String msg = "§8[§cVF§8] §r" + GradientUtil.color(legacyColoredMessage);
        Bukkit.broadcastMessage(msg);
        sendActionBarAll(legacyColoredMessage);
    }

    /** ActionBar an einen Spieler (Legacy-§ Farben unterstützt) */
    public void sendActionBar(Player p, String legacyMessage) {
        var comp = LegacyComponentSerializer.legacySection().deserialize(legacyMessage);
        p.sendActionBar(comp);
    }

    /** ActionBar an alle Spieler */
    public void sendActionBarAll(String legacyMessage) {
        var comp = LegacyComponentSerializer.legacySection().deserialize(legacyMessage);
        for (Player p : Bukkit.getOnlinePlayers()) p.sendActionBar(comp);
    }

    public void broadcast(String legacyColoredMessage) {
        broadcastPlain(legacyColoredMessage);
    }
}