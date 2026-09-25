package com.vetroforge.web;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public final class WebPlayerCacheListener implements Listener {
    private final FactionsPlugin plugin;

    public WebPlayerCacheListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            if (plugin.getWebApiManager() != null) {
                plugin.getWebApiManager().cachePlayerSnapshot(uuid, name);
            }
        });
    }
}
