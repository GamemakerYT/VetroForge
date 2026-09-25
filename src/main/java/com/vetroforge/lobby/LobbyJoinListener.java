// src/main/java/com/vetroforge/lobby/LobbyJoinListener.java
package com.vetroforge.lobby;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.warzone.WarzoneRegionManager;
import org.bukkit.GameMode;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public class LobbyJoinListener implements Listener {

    private final FactionsPlugin plugin;

    public LobbyJoinListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        var p = e.getPlayer();

        boolean autoFly = plugin.getConfig().getBoolean("lobby.autoFly", false);
        boolean inSpawn = isSpawnLobbyArea(p);
        boolean mayFly = p.isOp()
                || p.hasPermission("vetro.fly")
                || p.hasPermission("vetro.lobby.fly")
                || p.hasPermission("vetroforge.lobby.fly")
                || p.hasPermission("vetroforge.admin")
                || p.getGameMode() == GameMode.CREATIVE
                || p.getGameMode() == GameMode.SPECTATOR;

        if (inSpawn && mayFly) {
            p.setAllowFlight(true);
            if (autoFly && p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) {
                p.setFlying(true);
            }
        } else if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
            p.setFlying(false);
            p.setAllowFlight(false);
        }

        float speed = (float) plugin.getConfig().getDouble("lobby.walkSpeed", 0.2D);
        if (speed < 0.0f) speed = 0.0f;
        if (speed > 1.0f) speed = 1.0f;
        p.setWalkSpeed(speed);

        if (plugin.getPlayerVisibilityManager() != null) {
            plugin.getPlayerVisibilityManager().refreshFor(p);
        }
    }

    private boolean isSpawnLobbyArea(org.bukkit.entity.Player player) {
        WarzoneRegionManager region = plugin.getWarzoneRegionManager();
        if (region != null) return region.isLobbySpawnArea(player.getLocation());
        return player.getWorld() != null
                && player.getWorld().getName().equalsIgnoreCase(plugin.getConfig().getString("lobby.world", "world"));
    }
}
