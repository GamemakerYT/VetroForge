// src/main/java/com/vetroforge/manager/OnlineTimeManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.stats.StatsManager; // ✅ richtiges Paket
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class OnlineTimeManager {

    private final FactionsPlugin plugin;
    private final StatsManager stats;
    private BukkitTask task;

    public OnlineTimeManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.stats  = plugin.getStatsManager(); // ✅ nicht "manager.StatsManager"
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                stats.addOnlineSeconds(p.getUniqueId(), 1); // tickweise++
            }
        }, 20L, 20L);
    }

    public void stop() {
        if (task != null) { try { task.cancel(); } catch (Throwable ignored) {} }
        task = null;
    }
}