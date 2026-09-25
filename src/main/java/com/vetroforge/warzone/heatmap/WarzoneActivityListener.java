package com.vetroforge.warzone.heatmap;

import com.vetroforge.FactionsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerMoveEvent;

public final class WarzoneActivityListener implements Listener {

    private final FactionsPlugin plugin;

    public WarzoneActivityListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) return;
        if (plugin.getWarzoneActivityService() != null) {
            plugin.getWarzoneActivityService().logPosition(event.getPlayer());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player victim = event.getEntity();
        Player killer = victim.getKiller();
        if (plugin.getWarzoneActivityService() != null) {
            plugin.getWarzoneActivityService().logKill(killer, victim);
        }
        // NOTE: factions-repair also awarded bonus war/weekly score here via
        // SeasonManager.addWarScorePlayer/addWarScoreFaction and StatsManager.addStat(...).
        // Neither method exists on the current codebase's SeasonManager/StatsManager (their
        // APIs evolved differently - current SeasonManager has no live score-accumulation
        // concept), so that bonus-scoring side effect was dropped here. The core heatmap
        // activity logging above (logKill) is unaffected and preserved as-is.
    }
}
