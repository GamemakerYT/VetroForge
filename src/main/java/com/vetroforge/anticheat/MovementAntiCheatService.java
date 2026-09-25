// src/main/java/com/vetroforge/anticheat/MovementAntiCheatService.java
package com.vetroforge.anticheat;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Phase 1, bewusst NUR Logging/Staff-Alert -- keine automatische Strafe. Movement-/Reach-
 * Anticheats haben ein deutlich hoeheres False-Positive-Risiko als die Verbindungs-/Chat-
 * Checks in VetroCore (Lag, Custom-Item-Boosts, Server-Ability-Effekte) und brauchen
 * erfahrungsgemaess wochenlanges Live-Tuning, bevor Auto-Punish vertretbar ist.
 *
 * Wird BEWUSST NICHT in AntiCheatBridge.getSeverity() eingespeist: dieser Stub wird von
 * AIBanManager gelesen und kann bei Wert &gt;=4 automatisch einen Temp-Ban ausloesen, sobald
 * Staff /aiban --apply nutzt -- das wuerde die "kein Auto-Punish"-Entscheidung indirekt
 * unterlaufen. Eigener, unabhaengiger Score hier.
 */
public final class MovementAntiCheatService {

    private final FactionsPlugin plugin;

    /** Je Check-Typ + Spieler: Zeitpunkte der letzten Verstoesse (fuer "N Verstoesse in Fenster"). */
    private final Map<String, Map<UUID, Deque<Long>>> violations = new ConcurrentHashMap<>();
    /** Cooldown, damit derselbe Verstoss nicht bei jedem einzelnen Tick erneut alarmiert. */
    private final Map<UUID, Long> lastAlertAt = new ConcurrentHashMap<>();

    public MovementAntiCheatService(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("anticheat.enabled", true);
    }

    public double speedToleranceMultiplier() {
        return Math.max(1.1, plugin.getConfig().getDouble("anticheat.speed.toleranceMultiplier", 1.5));
    }

    public int speedViolationsToFlag() {
        return Math.max(1, plugin.getConfig().getInt("anticheat.speed.violationsToFlag", 4));
    }

    public long speedWindowMillis() {
        return Math.max(1000L, plugin.getConfig().getLong("anticheat.speed.windowMillis", 4000L));
    }

    public double reachMaxDistance() {
        return Math.max(3.0, plugin.getConfig().getDouble("anticheat.reach.maxDistance", 4.2));
    }

    public int reachViolationsToFlag() {
        return Math.max(1, plugin.getConfig().getInt("anticheat.reach.violationsToFlag", 3));
    }

    public long reachWindowMillis() {
        return Math.max(1000L, plugin.getConfig().getLong("anticheat.reach.windowMillis", 5000L));
    }

    public long graceMillisAfterDamage() {
        return Math.max(0L, plugin.getConfig().getLong("anticheat.graceMillisAfterDamage", 600L));
    }

    public long graceMillisAfterTeleport() {
        return Math.max(0L, plugin.getConfig().getLong("anticheat.graceMillisAfterTeleport", 1000L));
    }

    private long alertCooldownMillis() {
        return Math.max(0L, plugin.getConfig().getLong("anticheat.alertCooldownMillis", 15000L));
    }

    /**
     * Traegt einen Verstoss ein und meldet zurueck, ob damit die Schwelle (N Verstoesse
     * im Fenster) erreicht wurde -- der Aufrufer alarmiert dann Staff.
     */
    public boolean recordAndCheckThreshold(String checkType, UUID uuid, int threshold, long windowMillis) {
        long now = System.currentTimeMillis();
        Deque<Long> times = violations
                .computeIfAbsent(checkType, k -> new ConcurrentHashMap<>())
                .computeIfAbsent(uuid, u -> new ArrayDeque<>());

        synchronized (times) {
            times.addLast(now);
            while (!times.isEmpty() && now - times.peekFirst() > windowMillis) {
                times.removeFirst();
            }
            return times.size() >= threshold;
        }
    }

    /** Raeumt den Zustand eines Spielers auf, damit die Maps nicht unbegrenzt wachsen (Quit). */
    public void forgetPlayer(UUID uuid) {
        for (Map<UUID, Deque<Long>> byPlayer : violations.values()) {
            byPlayer.remove(uuid);
        }
        lastAlertAt.remove(uuid);
    }

    /** Alarmiert Staff ueber den bestehenden "cheat"-Notify-Kanal, mit Cooldown pro Spieler. */
    public void alertStaff(Player player, String checkType, String detail) {
        if (!plugin.getConfig().getBoolean("anticheat.alertStaff", true)) return;

        long now = System.currentTimeMillis();
        Long last = lastAlertAt.get(player.getUniqueId());
        if (last != null && now - last < alertCooldownMillis()) return;
        lastAlertAt.put(player.getUniqueId(), now);

        if (plugin.getNotifyManager() == null) return;
        plugin.getNotifyManager().broadcast(
                "cheat",
                "&c[AC] &f" + player.getName() + " &7" + checkType + " &8» &f" + detail
                        + " &8(nur markiert, keine automatische Strafe)",
                Sound.BLOCK_NOTE_BLOCK_PLING,
                "vetroforge.anticheat.alerts"
        );
    }
}
