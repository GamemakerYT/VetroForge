// src/main/java/com/vetroforge/lobby/DoubleJumpListener.java
package com.vetroforge.lobby;

import com.vetroforge.FactionsPlugin;
import org.bukkit.GameMode;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class DoubleJumpListener implements Listener {

    private final FactionsPlugin plugin;
    private final Map<UUID, Long> cooldown = new HashMap<>();

    public DoubleJumpListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onMove(PlayerMoveEvent e) {
        Player p = e.getPlayer();

        if (!isEnabledFor(p)) return;
        if (p.isFlying()) return;
        if (p.getGameMode() != GameMode.ADVENTURE && p.getGameMode() != GameMode.SURVIVAL) return;

        if (p.isOnGround()) {
            p.setAllowFlight(true);
        }
    }

    @EventHandler
    public void onToggleFlight(PlayerToggleFlightEvent e) {
        Player p = e.getPlayer();

        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) return;
        if (!isEnabledFor(p)) return;

        e.setCancelled(true);

        long now = System.currentTimeMillis();
        long cdMs = plugin.getConfig().getLong("lobby.doublejump.cooldown-ms", 2000L);
        long last = cooldown.getOrDefault(p.getUniqueId(), 0L);

        if (now - last < cdMs) return;
        cooldown.put(p.getUniqueId(), now);

        launch(p);
    }

    private boolean isEnabledFor(Player p) {
        boolean enabled = plugin.getConfig().getBoolean("lobby.doublejump.enabled", true);
        if (p.isOp()
                || p.hasPermission("vetro.fly")
                || p.hasPermission("vetro.lobby.fly")
                || p.hasPermission("vetroforge.lobby.fly")
                || p.hasPermission("vetroforge.admin")) {
            return false;
        }
        boolean inSpawn = plugin.getWarzoneRegionManager() != null
                && plugin.getWarzoneRegionManager().isLobbySpawnArea(p.getLocation());

        return enabled && inSpawn;
    }

    private void launch(Player p) {
        double horizontal = plugin.getConfig().getDouble("lobby.doublejump.horizontal", 1.2D);
        double vertical = plugin.getConfig().getDouble("lobby.doublejump.vertical", 0.9D);
        long restoreTicks = plugin.getConfig().getLong("lobby.doublejump.restore-flight-ticks", 40L);

        p.setAllowFlight(false);
        p.setFlying(false);
        p.setVelocity(p.getLocation().getDirection().multiply(horizontal).setY(vertical));

        try {
            p.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1.2f);
        } catch (Throwable ignored) {}

        try {
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 10, 0.3, 0.1, 0.3, 0.02);
        } catch (Throwable ignored) {}

        new BukkitRunnable() {
            @Override
            public void run() {
                if (!p.isOnline()) return;
                if (!isEnabledFor(p)) return;
                if (p.getGameMode() != GameMode.SURVIVAL && p.getGameMode() != GameMode.ADVENTURE) return;

                p.setAllowFlight(true);
            }
        }.runTaskLater(plugin, restoreTicks);
    }
}
