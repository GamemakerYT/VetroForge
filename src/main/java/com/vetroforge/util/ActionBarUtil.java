// src/main/java/com/vetroforge/util/ActionBarUtil.java
package com.vetroforge.util;

import com.vetroforge.FactionsPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Location;
import org.bukkit.entity.Player;

public final class ActionBarUtil {
    private ActionBarUtil(){}

    // Du hast bereits send(Player,String) im Projekt? Falls nicht, hier ein sicherer Fallback:
    public static void send(Player p, String legacyMsg){
        try {
            var comp = LegacyComponentSerializer.legacySection().deserialize(legacyMsg);
            p.sendActionBar(comp);
        } catch (Throwable t) {
            // fallback: chat
            p.sendMessage(legacyMsg);
        }
    }

    /** Blendet für alle Spieler im Radius eine ActionBar ein. Die Sidebar bleibt bewusst unverändert. */
    public static void showNearby(FactionsPlugin plugin,
                                  Location center,
                                  double radius){
        if (plugin == null || center == null || center.getWorld() == null) return;
        double r2 = radius * radius;

        for (Player p : center.getWorld().getPlayers()){
            if (!p.getWorld().equals(center.getWorld())) continue;
            if (p.getLocation().distanceSquared(center) > r2) continue;

            // kleine ActionBar-Info
            try {
                int dist = (int) Math.round(p.getLocation().distance(center));
                send(p, "§dLootdrop in der Nähe §8(§f" + dist + "m§8)");
            } catch (Throwable ignore) {}
        }
    }
}
