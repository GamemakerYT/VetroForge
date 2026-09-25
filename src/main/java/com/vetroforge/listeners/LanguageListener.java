// src/main/java/com/vetroforge/listeners/LanguageListener.java
package com.vetroforge.listeners;

import com.vetroforge.FactionsPlugin;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class LanguageListener implements Listener {

    private final FactionsPlugin plugin;

    public LanguageListener(FactionsPlugin plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e){
        var lm = plugin.getLanguageManager();
        boolean has = false;
        try {
            has = (boolean) lm.getClass()
                    .getMethod("hasLanguage", org.bukkit.entity.Player.class)
                    .invoke(lm, e.getPlayer());
        } catch (Throwable ignored) {}
        if (!has) {
            com.vetroforge.i18n.LanguageMenu.open(plugin, e.getPlayer());
        }
    }
    }