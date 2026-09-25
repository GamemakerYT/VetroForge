// src/main/java/com/vetroforge/scoreboard/ScoreboardUpdateTask.java
package com.vetroforge.scoreboard;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.display.SidebarScoreboardManager;
import com.vetroforge.raid.RaidManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class ScoreboardUpdateTask {

    private final FactionsPlugin plugin;

    public ScoreboardUpdateTask(FactionsPlugin plugin) {
        this.plugin = plugin;
        start();
    }

    private void start() {

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            SidebarScoreboardManager sb = plugin.getSidebarScoreboardManager();
            RaidManager rm = plugin.getRaidManager();
            if (sb == null || plugin.getWarzoneRegionManager() == null) return;

            for (Player p : Bukkit.getOnlinePlayers()) {

                var session = plugin.getRaidSessionService();
                if (session != null) {
                    try {
                        if (session.isScouting(p) || session.isInRaid(p)) {
                            continue;
                        }
                    } catch (Throwable ignored) {}
                }

                var raid = rm == null ? null : rm.getRaidForPlayer(p.getUniqueId());

                // 🔥 RAID BOARD PRIORITÄT
                if (raid != null) {
                    sb.showRaidBoard(
                            p,
                            raid.targetFaction,
                            raid.obsidianBroken,
                            raid.obsidianGoal,
                            raid.secondsLeft,
                            raid.raiders.size()
                    );
                    continue;
                }

                // 🔥 WARZONE ODER LOBBY
                if (plugin.getWarzoneRegionManager().isWarzone(p.getLocation())) {
                    sb.showWarzoneBoard(p);
                } else {
                    sb.showLobbyBoard(p);
                }
            }

        }, 20L, 40L); // alle 2 Sekunden (nicht jede Sekunde wie ein Irrer)
    }
}
