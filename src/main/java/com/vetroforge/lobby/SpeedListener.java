// src/main/java/com/vetroforge/lobby/SpeedListener.java
package com.vetroforge.lobby;

import com.vetroforge.FactionsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;

public class SpeedListener implements Listener {

    private final FactionsPlugin plugin;

    public SpeedListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        apply(e.getPlayer());
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        apply(e.getPlayer());
    }

    private void apply(Player p) {
        float speed = (float) plugin.getConfig().getDouble("lobby.speed.walk", 0.2D);

        if (speed < -1.0f) speed = -1.0f;
        if (speed > 1.0f) speed = 1.0f;

        try {
            p.setWalkSpeed(speed);
        } catch (IllegalArgumentException ignored) {
            p.setWalkSpeed(0.2f);
        }
    }
}