// src/main/java/com/vetroforge/anticheat/MovementAntiCheatListener.java
package com.vetroforge.anticheat;

import com.vetroforge.FactionsPlugin;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleExitEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase-1-Movement-/Reach-Anticheat: Speed, Fly, Reach. Nur Logging/Staff-Alert (siehe
 * MovementAntiCheatService) -- kein Auto-Punish. Bekannte, bewusst in Kauf genommene
 * Einschraenkung: Dragon's Breath' Feuerfeld-Tick-Schaden (DragonsBreathListener,
 * target.damage(amount, shooter)) kann in seltenen Faellen als ueberhoehte Reach geloggt
 * werden, da Bukkit programmatischen und echten Nahkampfschaden beide als ENTITY_ATTACK
 * meldet und beide denselben Event-Typ nutzen -- da diese Funktion NICHT bestraft,
 * sondern nur markiert, ist der Preis dafuer ein gelegentlicher, fuer Staff leicht
 * erkennbarer Log-Eintrag, keine echte Fehlfunktion.
 */
public final class MovementAntiCheatListener implements Listener {

    private final FactionsPlugin plugin;
    private final MovementAntiCheatService service;

    private final Map<UUID, PositionSample> lastPosition = new ConcurrentHashMap<>();
    private final Map<UUID, Long> graceUntil = new ConcurrentHashMap<>();

    private record PositionSample(long timestampMs, double x, double z) {}

    public MovementAntiCheatListener(FactionsPlugin plugin, MovementAntiCheatService service) {
        this.plugin = plugin;
        this.service = service;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onMove(PlayerMoveEvent event) {
        if (!service.enabled()) return;

        Player player = event.getPlayer();
        Location from = event.getFrom();
        Location to = event.getTo();
        if (to == null) return;
        if (from.getX() == to.getX() && from.getY() == to.getY() && from.getZ() == to.getZ()) return; // reine Blickrichtung

        if (isExempt(player)) return;

        checkFly(player);
        checkSpeed(player, to);
    }

    private boolean isExempt(Player player) {
        if (player.getGameMode() == GameMode.CREATIVE || player.getGameMode() == GameMode.SPECTATOR) return true;
        if (player.isInsideVehicle() || player.isGliding()) return true;
        Long until = graceUntil.get(player.getUniqueId());
        return until != null && System.currentTimeMillis() < until;
    }

    private void checkFly(Player player) {
        // isFlying() spiegelt, was der CLIENT dem Server meldet -- ohne allowFlight ist das
        // NIE legitim (jede echte Flug-Faehigkeit dieses Plugins setzt vorher setAllowFlight(true),
        // siehe DoubleJumpListener/FlyCommand/RaidScoutingRestrictionListener), unabhaengig davon
        // WARUM der Spieler gerade fliegen darf.
        if (player.isFlying() && !player.getAllowFlight()) {
            service.alertStaff(player, "Fly", "isFlying ohne allowFlight");
        }
    }

    private void checkSpeed(Player player, Location to) {
        UUID id = player.getUniqueId();
        long now = System.currentTimeMillis();

        PositionSample previous = lastPosition.put(id, new PositionSample(now, to.getX(), to.getZ()));
        if (previous == null) return;

        long elapsedMs = now - previous.timestampMs();
        if (elapsedMs <= 0L || elapsedMs > 2000L) return; // Lag-Spike/erste Bewegung nach Pause -- nicht verlaesslich messbar

        double dx = to.getX() - previous.x();
        double dz = to.getZ() - previous.z();
        double horizontalDistance = Math.sqrt(dx * dx + dz * dz);
        double observedBlocksPerSecond = horizontalDistance / (elapsedMs / 1000.0);

        double expected = expectedMaxSpeed(player);
        if (observedBlocksPerSecond <= expected * service.speedToleranceMultiplier()) return;

        boolean flagged = service.recordAndCheckThreshold(
                "speed", id, service.speedViolationsToFlag(), service.speedWindowMillis());
        if (flagged) {
            service.alertStaff(player, "Speed", String.format(
                    java.util.Locale.ROOT, "%.1f Bloecke/s (erwartet max ~%.1f)", observedBlocksPerSecond, expected));
        }
    }

    /** Grobe Vanilla-Basiswerte (Bloecke/Sekunde), respektiert /speed-Command und aktive SPEED-Effekte. */
    private double expectedMaxSpeed(Player player) {
        double base = player.isSprinting() ? 5.7 : 4.4;

        double walkSpeedRatio = player.getWalkSpeed() / 0.2D;
        double speed = base * Math.max(0.1D, walkSpeedRatio);

        PotionEffect speedEffect = player.getPotionEffect(PotionEffectType.SPEED);
        if (speedEffect != null) {
            int level = speedEffect.getAmplifier() + 1;
            speed *= 1.0D + 0.2D * level;
        }
        return speed;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onReach(EntityDamageByEntityEvent event) {
        if (!service.enabled()) return;
        if (event.getCause() != EntityDamageEvent.DamageCause.ENTITY_ATTACK) return;
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (!(event.getEntity() instanceof LivingEntity victim)) return;
        if (attacker.getGameMode() == GameMode.CREATIVE || attacker.getGameMode() == GameMode.SPECTATOR) return;

        double distance = attacker.getEyeLocation().distance(victim.getLocation());
        if (distance <= service.reachMaxDistance()) return;

        boolean flagged = service.recordAndCheckThreshold(
                "reach", attacker.getUniqueId(), service.reachViolationsToFlag(), service.reachWindowMillis());
        if (flagged) {
            service.alertStaff(attacker, "Reach", String.format(
                    java.util.Locale.ROOT, "%.2f Bloecke (max %.2f) gegen %s",
                    distance, service.reachMaxDistance(), victim.getName()));
        }
    }

    /* ================= Grace-Perioden (verhindern False-Positives) ================= */

    @EventHandler(ignoreCancelled = true)
    public void onDamaged(EntityDamageEvent event) {
        if (event.getEntity() instanceof Player player) {
            grace(player.getUniqueId(), service.graceMillisAfterDamage());
        }
    }

    @EventHandler
    public void onTeleport(PlayerTeleportEvent event) {
        grace(event.getPlayer().getUniqueId(), service.graceMillisAfterTeleport());
        lastPosition.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent event) {
        grace(event.getPlayer().getUniqueId(), service.graceMillisAfterTeleport());
        lastPosition.remove(event.getPlayer().getUniqueId());
    }

    @EventHandler
    public void onVehicleExit(VehicleExitEvent event) {
        if (event.getExited() instanceof Player player) {
            grace(player.getUniqueId(), service.graceMillisAfterTeleport());
            lastPosition.remove(player.getUniqueId());
        }
    }

    private void grace(UUID uuid, long millis) {
        graceUntil.put(uuid, System.currentTimeMillis() + millis);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        lastPosition.remove(uuid);
        graceUntil.remove(uuid);
        service.forgetPlayer(uuid);
    }
}
