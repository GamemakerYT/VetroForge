// src/main/java/com/vetroforge/stats/StatsListener.java
package com.vetroforge.stats;

import com.vetroforge.FactionsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class StatsListener implements Listener {
    private final FactionsPlugin plugin;

    public StatsListener(FactionsPlugin plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e){
        plugin.getStatsManager().handleJoin(e.getPlayer());
    }

    @EventHandler public void onQuit(PlayerQuitEvent e){
        plugin.getStatsManager().handleQuit(e.getPlayer());
    }

    @EventHandler public void onDeath(PlayerDeathEvent e){
        Player victim = e.getEntity();
        Player killer = victim.getKiller();

        // Der Tod des Opfers (Deaths-Zähler, Streak-Reset) ist real und zählt immer.
        plugin.getStatsManager().onPlayerDeath(victim.getUniqueId());

        if (killer == null || killer == victim) return;

        // Anti-Farm: wiederholte Kills desselben Spielers innerhalb des Cooldowns
        // geben dem Killer keine Stats-Gutschrift (Kills/Streak/Elo) mehr.
        if (plugin.getStatsManager().isKillOnCooldown(killer.getUniqueId(), victim.getUniqueId())) {
            killer.sendMessage("§7Kill zählt nicht (Cooldown gegen §f" + victim.getName() + "§7).");
            return;
        }

        plugin.getStatsManager().onPlayerKill(killer.getUniqueId());

        int streak = plugin.getStatsManager().getStreak(killer);
        if (streak == 5 || streak == 10 || streak % 25 == 0) {
            org.bukkit.Bukkit.broadcastMessage("§a" + killer.getName() + " §7hat eine Kill-Streak von §a" + streak + "§7!");
        }
    }
}