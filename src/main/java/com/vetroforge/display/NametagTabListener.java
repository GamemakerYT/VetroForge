// src/main/java/com/vetroforge/display/NametagTabListener.java
package com.vetroforge.display;

import com.vetroforge.FactionsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class NametagTabListener implements Listener {
    private final FactionsPlugin plugin;
    public NametagTabListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }
    @EventHandler public void onJoin(PlayerJoinEvent e) {
        try { plugin.getNametagTabManager().applyFor(e.getPlayer()); } catch (Throwable ignored) {}
        try { plugin.getNametagTabManager().applyForAll(); } catch (Throwable ignored) {}
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) {
        try { plugin.getNametagTabManager().removeViewer(e.getPlayer()); } catch (Throwable ignored) {}
    }
}