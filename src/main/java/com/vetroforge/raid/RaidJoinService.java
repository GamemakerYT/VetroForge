// src/main/java/com/vetroforge/raid/RaidJoinService.java
package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.display.SidebarScoreboardManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class RaidJoinService {

    private static final String DEFAULT_WORLD = "world";
    private static final double DEFAULT_X = 120.0;
    private static final double DEFAULT_Y = 65.0;
    private static final double DEFAULT_Z = 5.0;

    private static final long JOIN_PHASE_TICKS = 20L * 60L;

    private final FactionsPlugin plugin;
    private final SidebarScoreboardManager scoreboard;

    private final Map<String, String> joinPhases = new ConcurrentHashMap<>();
    private final Map<String, Long> joinPhaseEndsAt = new ConcurrentHashMap<>();
    private final Map<String, BukkitTask> joinPhaseTasks = new ConcurrentHashMap<>();

    public RaidJoinService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.scoreboard = plugin.getSidebarScoreboardManager();
    }

    public void startJoinPhase(String attackerFaction, String targetFaction) {
        if (attackerFaction == null || attackerFaction.isBlank()) return;
        if (targetFaction == null || targetFaction.isBlank()) return;

        stopJoinPhase(attackerFaction);

        joinPhases.put(attackerFaction, targetFaction);
        joinPhaseEndsAt.put(attackerFaction, System.currentTimeMillis() + 60_000L);

        BukkitTask task = Bukkit.getScheduler().runTaskLater(plugin, () -> expireJoinPhase(attackerFaction), JOIN_PHASE_TICKS);
        joinPhaseTasks.put(attackerFaction, task);

        Bukkit.getConsoleSender().sendMessage("§6Raid §8» §fJoin-Phase gestartet: §e" + attackerFaction + " §8-> §c" + targetFaction);

        for (Player player : Bukkit.getOnlinePlayers()) {
            String faction = getFaction(player);

            if (!attackerFaction.equalsIgnoreCase(faction)) continue;

            RaidText.send(plugin, player, "message.join_phase_started");
            RaidText.send(plugin, player, "message.target_faction", "target", targetFaction);
            RaidText.send(plugin, player, "message.join_instruction", "seconds", 60);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
        }
    }

    public void stopJoinPhase(String attackerFaction) {
        if (attackerFaction == null || attackerFaction.isBlank()) return;

        joinPhases.remove(attackerFaction);
        joinPhaseEndsAt.remove(attackerFaction);

        BukkitTask oldTask = joinPhaseTasks.remove(attackerFaction);

        if (oldTask != null) {
            try {
                oldTask.cancel();
            } catch (Throwable ignored) {}
        }
    }

    private void expireJoinPhase(String attackerFaction) {
        String target = joinPhases.remove(attackerFaction);
        joinPhaseEndsAt.remove(attackerFaction);
        joinPhaseTasks.remove(attackerFaction);

        if (target == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            String faction = getFaction(player);

            if (!attackerFaction.equalsIgnoreCase(faction)) continue;

            RaidText.send(plugin, player, "message.join_phase_expired");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
        }
    }

    public boolean hasJoinPhase(String attackerFaction) {
        return attackerFaction != null && joinPhases.containsKey(attackerFaction);
    }

    public String getJoinPhaseTarget(String attackerFaction) {
        if (attackerFaction == null || attackerFaction.isBlank()) return null;
        return joinPhases.get(attackerFaction);
    }

    public int getJoinPhaseRemainingSeconds(String attackerFaction) {
        Long end = joinPhaseEndsAt.get(attackerFaction);
        if (end == null) return 0;

        return (int) Math.ceil(Math.max(0L, end - System.currentTimeMillis()) / 1000.0);
    }

    public void joinRaid(Player player, String targetArg) {
        if (player == null) return;

        if (plugin.getRaidSessionService() == null) {
            RaidText.send(plugin, player, "message.session_unavailable");
            return;
        }

        if (plugin.getFactionManager() == null) {
            RaidText.send(plugin, player, "message.faction_manager_unavailable");
            return;
        }

        String ownFaction = plugin.getFactionManager().getFactionOf(player.getUniqueId());

        if (ownFaction == null || ownFaction.isBlank()) {
            RaidText.send(plugin, player, "message.no_faction");
            return;
        }

        String joinPhaseTarget = getJoinPhaseTarget(ownFaction);
        String activeTarget = plugin.getRaidSessionService().getActiveRaidTargetForFaction(ownFaction);
        String target = joinPhaseTarget != null ? joinPhaseTarget : activeTarget;

        if (target == null || target.isBlank()) {
            RaidText.send(plugin, player, "message.no_join_phase");
            return;
        }
        if (targetArg != null && !targetArg.isBlank() && !target.equalsIgnoreCase(targetArg)) {
            RaidText.send(plugin, player, "message.invalid_target");
            return;
        }
        if (plugin.getRaidManager() == null || plugin.getRaidManager().getSessionByTarget(target) == null) {
            RaidText.send(plugin, player, "message.no_active_raid");
            return;
        }
        if (plugin.getRaidManager().isEliminated(player)) {
            RaidText.send(plugin, player, "message.eliminated_cannot_rejoin");
            return;
        }

        Location join = getRaidJoinLocation(player, target);
        if (join == null || join.getWorld() == null) {
            RaidText.send(plugin, player, "message.zone_missing");
            return;
        }

        player.teleport(join);
        plugin.getRaidSessionService().markPlayerInRaid(player, target);
        if (plugin.getRaidManager() != null) {
            plugin.getRaidManager().addRaider(target, player.getUniqueId());
        }

        int raiders = plugin.getRaidSessionService().getRaidPlayerCountForFaction(ownFaction);
        int remaining = getJoinPhaseRemainingSeconds(ownFaction);

        scoreboard.showJoinedRaidBoard(player, target, raiders, remaining);

        RaidText.send(plugin, player, "message.joined", "target", target);
        RaidText.send(plugin, player, "message.red_line_building");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
    }

    public void leaveRaid(Player player) {
        if (player == null) return;

        if (plugin.getRaidSessionService() != null) {
            plugin.getRaidSessionService().leaveRaid(player);
        }

        RaidText.send(plugin, player, "message.left");
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
    }

    private Location getRaidJoinLocation(Player fallback, String targetFaction) {
        if (plugin.getRaidSessionService() != null) {
            Location raidSpawn = plugin.getRaidSessionService().getRaidZoneSpawn(targetFaction);

            if (raidSpawn != null) {
                return raidSpawn;
            }
        }

        String worldName = plugin.getConfig().getString("raid.join.world", DEFAULT_WORLD);

        double x = plugin.getConfig().getDouble("raid.join.x", DEFAULT_X);
        double y = plugin.getConfig().getDouble("raid.join.y", DEFAULT_Y);
        double z = plugin.getConfig().getDouble("raid.join.z", DEFAULT_Z);

        World world = Bukkit.getWorld(worldName);

        if (world == null && fallback != null) {
            world = fallback.getWorld();
        }

        if (world == null && !Bukkit.getWorlds().isEmpty()) {
            world = Bukkit.getWorlds().get(0);
        }

        return new Location(world, x, y, z, 0.0f, 0.0f);
    }

    private String getFaction(Player player) {
        try {
            if (plugin.getFactionManager() == null) return null;
            return plugin.getFactionManager().getFactionOf(player.getUniqueId());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
