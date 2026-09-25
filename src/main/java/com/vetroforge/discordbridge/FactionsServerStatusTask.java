package com.vetroforge.discordbridge;

import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

public final class FactionsServerStatusTask {

    private final JavaPlugin plugin;
    private final FactionsDiscordBridgeService bridge;

    private BukkitTask task;

    public FactionsServerStatusTask(JavaPlugin plugin, FactionsDiscordBridgeService bridge) {
        this.plugin = plugin;
        this.bridge = bridge;
    }

    public void start() {
        boolean enabled = plugin.getConfig().getBoolean("discord-bridge.status.enabled", true);

        if (!enabled) {
            return;
        }

        long seconds = plugin.getConfig().getLong("discord-bridge.status.update-seconds", 15);
        long ticks = Math.max(20L, seconds * 20L);

        task = Bukkit.getScheduler().runTaskTimer(plugin, this::update, 40L, ticks);
    }

    public void stop() {
        if (task != null) {
            task.cancel();
            task = null;
        }

        bridge.shutdown();
    }

    private void update() {
        int online = Bukkit.getOnlinePlayers().size();
        int max = plugin.getConfig().getInt("discord-bridge.status.max-players", Bukkit.getMaxPlayers());

        double tps = 20.0;

        try {
            tps = Bukkit.getTPS()[0];
        } catch (Throwable ignored) {
        }

        bridge.updateServerStatus(
                true,
                online,
                max,
                tps,
                false,
                bridge.serverName() + " online"
        );
    }
}