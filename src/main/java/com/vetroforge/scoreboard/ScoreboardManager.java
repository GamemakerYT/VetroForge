// =======================================================
// File: src/main/java/com/vetroforge/scoreboard/ScoreboardManager.java
// =======================================================
/*package com.vetroforge.scoreboard;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.raid.RaidManager;
import com.vetroforge.warzone.WarzoneRegionManager;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

public class ScoreboardManager

    public org.bukkit.scoreboard.Scoreboard getOrCreateBoardFor(org.bukkit.entity.Player p) {
        org.bukkit.scoreboard.Scoreboard sb = p.getScoreboard();
        org.bukkit.scoreboard.ScoreboardManager sm = org.bukkit.Bukkit.getScoreboardManager();
        if (sm == null) return sb;
        if (sb == null || sb == sm.getMainScoreboard()) sb = sm.getNewScoreboard();
        return sb;
    }

    private final FactionsPlugin plugin;
    private BukkitTask task;

    public ScoreboardManager(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    tickPlayer(p);
                }
            }
        }, 20L, 20L); // jede Sekunde
    }

    public void stop() {
        if (task != null) try { task.cancel(); } catch (Throwable ignored) {}
        task = null;
    }

    private void tickPlayer(Player p) {
        RaidManager rm = plugin.getRaidManager();
        WarzoneRegionManager wm = plugin.getWarzoneRegionManager();

        String raidTarget = rm.getTargetFactionForPlayer(p.getUniqueId());
        if (raidTarget != null) {
            // RAID-Board
            RaidManager.Session s = rm.getSessionByTarget(raidTarget);
            int broken = s != null ? s.obsidianBroken : 0;
            int goal   = s != null ? s.obsidianGoal   : 0;
            int secs   = s != null ? s.secondsLeft    : 0;
            java.util.Set<java.util.UUID> raiders = s != null ? new java.util.HashSet<java.util.UUID>(s.raiders) : new java.util.HashSet<java.util.UUID>();
            plugin.getSidebarService().showRaidBoard(p, new SidebarService.RaidContext(raidTarget, broken, goal, secs, raiders));
            return;
        }

        if (wm != null && wm.isInside(p.getLocation())) {
            plugin.getSidebarService().showWarzoneBoard(p);
        } else {
            plugin.getSidebarService().showLobbyBoard(p);
        }
    }
*/