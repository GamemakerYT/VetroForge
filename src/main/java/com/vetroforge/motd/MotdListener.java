// src/main/java/com/vetroforge/motd/MotdListener.java
package com.vetroforge.motd;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.server.ServerListPingEvent;

public class MotdListener implements Listener {

    private final MotdManager motd;

    public MotdListener(FactionsPlugin plugin, MotdManager motd) {
        this.motd = motd;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onPing(ServerListPingEvent e) {
        int online   = Bukkit.getOnlinePlayers().size();
        int shownMax = motd.computeShownMax(online, e.getMaxPlayers());

        motd.selectAuto(online);
        e.setMotd(motd.renderMotd(online, shownMax));
        try { e.setMaxPlayers(shownMax); } catch (Throwable ignored) {}
    }
}