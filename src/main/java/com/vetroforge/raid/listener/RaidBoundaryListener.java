// src/main/java/com/vetroforge/raid/listener/RaidBoundaryListener.java
package com.vetroforge.raid.listener;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.worldgen.RaidWorldLayout;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import com.vetroforge.raid.RaidText;

public class RaidBoundaryListener implements Listener {

    private final FactionsPlugin plugin;

    private final Map<UUID, Boolean> lastEnemySide = new HashMap<>();
    private final Map<UUID, Long> warnCooldown = new HashMap<>();

    private BukkitTask particleTask;

    public RaidBoundaryListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void startTask() {
        stopTask();
        particleTask = Bukkit.getScheduler().runTaskTimer(plugin, this::drawDynamicRedLines, 20L, 20L);
    }

    public void stopTask() {
        if (particleTask != null) {
            try {
                particleTask.cancel();
            } catch (Throwable ignored) {}
        }

        particleTask = null;
        lastEnemySide.clear();
        warnCooldown.clear();
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        Player player = event.getPlayer();

        if (!isInRaid(player)) return;

        if (isEnemySide(player, event.getBlockPlaced())) {
            event.setCancelled(true);
            warn(player, "message.cannot_build_enemy");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();

        if (!isInRaid(player)) return;

        if (isEnemySide(player, event.getBlock())) {
            if (event.getBlock().getType() == org.bukkit.Material.OBSIDIAN) {
                if (!plugin.getConfig().getBoolean("raids.obsidian.allowPickaxeBreak", false)) {
                    event.setCancelled(true);
                    warn(player, "message.obsidian_tnt_only");
                    return;
                }
                String target = plugin.getRaidSessionService().getJoinedRaidTarget(player);
                if (target != null && plugin.getRaidManager() != null) {
                    plugin.getRaidManager().addObsidianBreak(target, player.getUniqueId());
                }
                return;
            }
            event.setCancelled(true);
            warn(player, "message.cannot_break_enemy");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        Player player = event.getPlayer();

        if (!isInRaid(player)) return;
        if (event.getTo() == null) return;

        if (event.getFrom().getBlockX() == event.getTo().getBlockX()
                && event.getFrom().getBlockY() == event.getTo().getBlockY()
                && event.getFrom().getBlockZ() == event.getTo().getBlockZ()) {
            return;
        }

        boolean enemySide = isEnemySide(player, player.getLocation());
        Boolean old = lastEnemySide.put(player.getUniqueId(), enemySide);

        if (old == null || old == enemySide) return;

        if (enemySide) {
            RaidText.send(plugin, player, "message.crossed_red_line");
            RaidText.send(plugin, player, "message.enemy_territory");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
        } else {
            RaidText.send(plugin, player, "message.before_red_line");
            RaidText.send(plugin, player, "message.building_enabled");
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.4f);
        }
    }

    private void drawDynamicRedLines() {
        if (plugin.getRaidSessionService() == null) return;

        HashSet<String> drawnTargets = new HashSet<>();

        for (UUID uuid : plugin.getRaidSessionService().getRaidPlayers()) {
            Player player = Bukkit.getPlayer(uuid);

            if (player == null || !player.isOnline()) continue;

            String target = plugin.getRaidSessionService().getJoinedRaidTarget(player);

            if (target == null || target.isBlank()) continue;
            if (!drawnTargets.add(target.toLowerCase())) continue;

            Location targetSpawn = getFactionSpawn(target);

            if (targetSpawn == null || targetSpawn.getWorld() == null) continue;

            World world = targetSpawn.getWorld();
            int y = plugin.getConfig().getInt("factions.world.yLevel", 64);

            Particle.DustOptions dust = new Particle.DustOptions(Color.RED, 1.5f);

            int half = RaidWorldLayout.BUILD_HALF;

            for (int i = -half; i <= half; i++) {
                world.spawnParticle(
                        Particle.DUST,
                        new Location(world, half + 0.5, y + 1.15, i + 0.5),
                        3,
                        0.04,
                        0.04,
                        0.04,
                        0.0,
                        dust
                );

                world.spawnParticle(
                        Particle.DUST,
                        new Location(world, -half + 0.5, y + 1.15, i + 0.5),
                        3,
                        0.04,
                        0.04,
                        0.04,
                        0.0,
                        dust
                );

                world.spawnParticle(
                        Particle.DUST,
                        new Location(world, i + 0.5, y + 1.15, half + 0.5),
                        3,
                        0.04,
                        0.04,
                        0.04,
                        0.0,
                        dust
                );

                world.spawnParticle(
                        Particle.DUST,
                        new Location(world, i + 0.5, y + 1.15, -half + 0.5),
                        3,
                        0.04,
                        0.04,
                        0.04,
                        0.0,
                        dust
                );
            }
        }
    }

    private boolean isEnemySide(Player player, Block block) {
        if (block == null) return false;
        return isEnemySide(player, block.getLocation());
    }

    private boolean isEnemySide(Player player, Location location) {
        if (player == null || location == null || location.getWorld() == null) return false;
        if (plugin.getRaidSessionService() == null) return false;

        String target = plugin.getRaidSessionService().getJoinedRaidTarget(player);

        if (target == null || target.isBlank()) return false;

        Location targetSpawn = getFactionSpawn(target);

        if (targetSpawn == null || targetSpawn.getWorld() == null) return false;
        if (!location.getWorld().getUID().equals(targetSpawn.getWorld().getUID())) return false;

        int x = location.getBlockX();
        int z = location.getBlockZ();

        return RaidWorldLayout.inBuild(x, z) || RaidWorldLayout.atRedLine(x, z);
    }

    private Location getFactionSpawn(String factionId) {
        try {
            if (plugin.getWorldManager() == null) return null;
            return plugin.getWorldManager().getFactionSpawn(factionId);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isInRaid(Player player) {
        try {
            return plugin.getRaidSessionService() != null
                    && plugin.getRaidSessionService().isInRaid(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private void warn(Player player, String key) {
        long now = System.currentTimeMillis();
        long last = warnCooldown.getOrDefault(player.getUniqueId(), 0L);

        if (now - last < 1200L) return;

        warnCooldown.put(player.getUniqueId(), now);

        RaidText.send(plugin, player, key);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
    }
}
