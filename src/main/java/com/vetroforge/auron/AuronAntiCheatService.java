package com.vetroforge.auron;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerAnimationType;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.player.PlayerVelocityEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.BoundingBox;
import org.bukkit.util.Vector;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conservative server-side AURON checks. This first stage only records and
 * alerts; it never cancels gameplay or applies punishments automatically.
 *
 * <p>AURON is deliberately self-sufficient: it keeps its own in-memory signal
 * store instead of writing into {@link com.vetroforge.services.AntiCheatBridge}.
 * That shared bridge is read by {@code AIBanManager} to feed the {@code /aiban}
 * ban recommendation, and {@link com.vetroforge.anticheat.MovementAntiCheatService}
 * already documents why movement/combat heuristics are kept out of it on purpose:
 * they have a meaningfully higher false-positive rate than the connection/chat
 * checks that bridge was designed for, and feeding it could indirectly undermine
 * the "no auto-punish" decision once staff use {@code /aiban --apply}. AURON's
 * signals follow the same reasoning and never touch that bridge.</p>
 */
public final class AuronAntiCheatService implements Listener {

    public static final String CHECK_SPEED = "SPEED";
    public static final String CHECK_VERTICAL = "VERTICAL";
    public static final String CHECK_REACH = "REACH";
    public static final String CHECK_AUTOCLICKER = "AUTOCLICKER";
    public static final String CHECK_KILLAURA = "KILLAURA";

    private static final long SIGNAL_WINDOW_MILLIS = 15L * 60L * 1000L;
    private static final int MAX_FLAGS_PER_PLAYER = 40;

    /** One recorded AURON signal. {@code simulated} marks results of {@code /auron test}. */
    public record FlagRecord(UUID playerId, String playerName, String check, double score,
                              String evidence, long createdAt, boolean simulated) {}

    /** Aggregated view of a player's recent AURON signals, used by the {@code /auron} GUI. */
    public record PlayerSnapshot(UUID playerId, String playerName, int severity, double score,
                                  List<FlagRecord> flags) {}

    private final FactionsPlugin plugin;
    private final boolean enabled;
    private final double speedGround;
    private final double speedAir;
    private final double verticalRise;
    private final double reachDistance;
    private final double autoClickerMinCps;
    private final int autoClickerMinSamples;
    private final double autoClickerMaxVariation;
    private final double killAuraMaxAngle;
    private final long killAuraSwitchMillis;
    private final int bufferNeeded;
    private final long alertCooldownMillis;
    private final Set<String> excludedWorlds;

    private final Map<UUID, Long> exemptUntil = new ConcurrentHashMap<>();
    private final Map<SignalKey, Double> buffers = new HashMap<>();
    private final Map<SignalKey, Long> lastAlert = new HashMap<>();
    private final Map<UUID, Deque<Long>> combatSwings = new HashMap<>();
    private final Map<UUID, Long> combatUntil = new HashMap<>();
    private final Map<UUID, Long> lastClickSample = new HashMap<>();
    private final Map<UUID, AttackState> lastAttack = new HashMap<>();
    private final Map<SignalKey, TrackingSession> trackingSessions = new HashMap<>();
    private final Set<UUID> alertsDisabled = ConcurrentHashMap.newKeySet();
    private final Map<UUID, Deque<FlagRecord>> flagsByPlayer = new ConcurrentHashMap<>();

    public AuronAntiCheatService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.enabled = plugin.getConfig().getBoolean("auron.anticheat.enabled", true);
        this.speedGround = plugin.getConfig().getDouble("auron.anticheat.checks.speed.max-ground-blocks-per-tick", 1.05D);
        this.speedAir = plugin.getConfig().getDouble("auron.anticheat.checks.speed.max-air-blocks-per-tick", 1.30D);
        this.verticalRise = plugin.getConfig().getDouble("auron.anticheat.checks.vertical.max-rise-blocks-per-tick", 1.65D);
        this.reachDistance = plugin.getConfig().getDouble("auron.anticheat.checks.reach.max-distance", 4.25D);
        this.autoClickerMinCps = plugin.getConfig().getDouble("auron.anticheat.checks.autoclicker.min-cps", 17.0D);
        this.autoClickerMinSamples = Math.max(8,
                plugin.getConfig().getInt("auron.anticheat.checks.autoclicker.min-samples", 12));
        this.autoClickerMaxVariation = plugin.getConfig().getDouble(
                "auron.anticheat.checks.autoclicker.max-interval-variation", 0.38D);
        this.killAuraMaxAngle = plugin.getConfig().getDouble("auron.anticheat.checks.killaura.max-angle", 100.0D);
        this.killAuraSwitchMillis = Math.max(100L,
                plugin.getConfig().getLong("auron.anticheat.checks.killaura.rapid-switch-millis", 280L));
        this.bufferNeeded = Math.max(2, plugin.getConfig().getInt("auron.anticheat.buffer-needed", 3));
        this.alertCooldownMillis = Math.max(1_000L,
                plugin.getConfig().getLong("auron.anticheat.alert-cooldown-seconds", 8L) * 1_000L);
        this.excludedWorlds = new HashSet<>();
        for (String world : plugin.getConfig().getStringList("auron.anticheat.excluded-worlds")) {
            if (world != null && !world.isBlank()) excludedWorlds.add(world.toLowerCase(Locale.ROOT));
        }
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        plugin.getLogger().info("[AURON] AntiCheat " + (enabled ? "aktiv" : "deaktiviert")
                + " (Beobachten + Staff-Warnungen, keine Auto-Bans).");
    }

    public boolean isEnabled() {
        return enabled;
    }

    public boolean isPausedByMaintenance() {
        return plugin.getMaintenanceManager() != null && plugin.getMaintenanceManager().isEnabled();
    }

    public boolean toggleAlerts(Player player) {
        UUID playerId = player.getUniqueId();
        if (!alertsDisabled.add(playerId)) {
            alertsDisabled.remove(playerId);
            return true;
        }
        return false;
    }

    public boolean alertsEnabled(Player player) {
        return !alertsDisabled.contains(player.getUniqueId());
    }

    public void exempt(Player player, long millis) {
        if (player == null) return;
        exemptUntil.merge(player.getUniqueId(), System.currentTimeMillis() + Math.max(0L, millis), Math::max);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        exempt(event.getPlayer(), 5_000L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        exempt(event.getPlayer(), 4_000L);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        exempt(event.getPlayer(), 3_000L);
        resetBuffers(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onVelocity(PlayerVelocityEvent event) {
        exempt(event.getPlayer(), 2_500L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onWorldChange(PlayerChangedWorldEvent event) {
        exempt(event.getPlayer(), 4_000L);
        resetBuffers(event.getPlayer().getUniqueId());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID playerId = event.getPlayer().getUniqueId();
        exemptUntil.remove(playerId);
        alertsDisabled.remove(playerId);
        combatSwings.remove(playerId);
        combatUntil.remove(playerId);
        lastClickSample.remove(playerId);
        lastAttack.remove(playerId);
        trackingSessions.keySet().removeIf(key -> key.playerId().equals(playerId));
        resetBuffers(playerId);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAnimation(PlayerAnimationEvent event) {
        if (event.getAnimationType() != PlayerAnimationType.ARM_SWING) return;
        Player player = event.getPlayer();
        boolean normal = activeFor(player);
        boolean tracked = isTracking(player, CHECK_AUTOCLICKER);
        if (!normal && !tracked) return;

        long now = System.currentTimeMillis();
        if (!tracked && combatUntil.getOrDefault(player.getUniqueId(), 0L) < now) return;
        Deque<Long> swings = combatSwings.computeIfAbsent(player.getUniqueId(), ignored -> new ArrayDeque<>());
        swings.addLast(now);
        while (!swings.isEmpty() && now - swings.peekFirst() > 1_000L) swings.removeFirst();
        if (swings.size() < autoClickerMinSamples) return;
        if (now - lastClickSample.getOrDefault(player.getUniqueId(), 0L) < 200L) return;
        lastClickSample.put(player.getUniqueId(), now);

        long first = swings.peekFirst();
        long last = swings.peekLast();
        long span = Math.max(250L, last - first);
        double cps = (swings.size() - 1) * 1_000.0D / span;
        double variation = intervalVariation(swings);
        String evidence = String.format(Locale.ROOT, "%.1f CPS, Intervallstreuung %.2f", cps, variation);
        trackSample(player, CHECK_AUTOCLICKER, cps, cps >= autoClickerMinCps && variation <= autoClickerMaxVariation, evidence);
        if (!normal) return;
        if (cps >= autoClickerMinCps && variation <= autoClickerMaxVariation) {
            double amount = Math.min(1.25D, 0.55D + (cps - autoClickerMinCps) * 0.08D);
            buffer(player, CHECK_AUTOCLICKER, amount, evidence);
        } else {
            decay(player.getUniqueId(), CHECK_AUTOCLICKER, 0.25D);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent event) {
        if (event.getTo() == null) return;
        Player player = event.getPlayer();
        boolean normal = activeFor(player);
        boolean tracked = isTracking(player, CHECK_SPEED) || isTracking(player, CHECK_VERTICAL);
        if (!normal && !tracked) return;
        Location from = event.getFrom();
        Location to = event.getTo();
        if (from.getWorld() != to.getWorld()) return;

        double dx = to.getX() - from.getX();
        double dz = to.getZ() - from.getZ();
        double horizontal = Math.sqrt(dx * dx + dz * dz);
        double vertical = to.getY() - from.getY();
        if (horizontal > 12.0D || Math.abs(vertical) > 8.0D) {
            if (normal) exempt(player, 2_000L);
            return;
        }

        double horizontalLimit = player.isOnGround() ? speedGround : speedAir;
        horizontalLimit += movementAllowance(player, to);
        trackSample(player, CHECK_SPEED, horizontal, horizontal > horizontalLimit,
                String.format(Locale.ROOT, "%.2f Blöcke/Tick (Limit %.2f)", horizontal, horizontalLimit));
        double verticalLimit = verticalRise + verticalAllowance(player);
        trackSample(player, CHECK_VERTICAL, vertical, vertical > verticalLimit,
                String.format(Locale.ROOT, "+%.2f Y/Tick (Limit %.2f)", vertical, verticalLimit));
        if (!normal) return;
        if (horizontal > horizontalLimit) {
            double ratio = horizontal / Math.max(0.01D, horizontalLimit);
            buffer(player, CHECK_SPEED, Math.min(2.0D, ratio - 0.65D),
                    String.format(Locale.ROOT, "%.2f Blöcke/Tick (Limit %.2f)", horizontal, horizontalLimit));
        } else {
            decay(player.getUniqueId(), CHECK_SPEED, 0.35D);
        }

        if (vertical > verticalLimit) {
            buffer(player, CHECK_VERTICAL, 1.0D,
                    String.format(Locale.ROOT, "+%.2f Y/Tick (Limit %.2f)", vertical, verticalLimit));
        } else {
            decay(player.getUniqueId(), CHECK_VERTICAL, 0.45D);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMelee(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        boolean normal = activeFor(attacker);
        boolean tracked = isTracking(attacker, CHECK_REACH) || isTracking(attacker, CHECK_KILLAURA);
        if (!normal && !tracked) return;
        Entity target = event.getEntity();
        if (target == attacker || !target.isValid()) return;
        combatUntil.put(attacker.getUniqueId(), System.currentTimeMillis() + 4_000L);

        Location eye = attacker.getEyeLocation();
        BoundingBox box = target.getBoundingBox().expand(0.10D);
        Vector point = eye.toVector();
        double closestX = clamp(point.getX(), box.getMinX(), box.getMaxX());
        double closestY = clamp(point.getY(), box.getMinY(), box.getMaxY());
        double closestZ = clamp(point.getZ(), box.getMinZ(), box.getMaxZ());
        double distance = point.distance(new Vector(closestX, closestY, closestZ));
        double pingAllowance = Math.min(0.35D, Math.max(0, attacker.getPing()) / 1_000.0D);
        double limit = reachDistance + pingAllowance;
        String reachEvidence = String.format(Locale.ROOT, "%.2f Blöcke Reichweite (Limit %.2f, Ping %dms)",
                distance, limit, attacker.getPing());
        trackSample(attacker, CHECK_REACH, distance, distance > limit, reachEvidence);
        if (normal && distance > limit) {
            buffer(attacker, CHECK_REACH, 1.35D,
                    reachEvidence);
        } else if (normal) {
            decay(attacker.getUniqueId(), CHECK_REACH, 0.30D);
        }
        if (target instanceof Player targetPlayer) analyzeKillAura(attacker, targetPlayer, box, distance, normal);
    }

    public String normalizeCheck(String value) {
        if (value == null) return null;
        return switch (value.trim().toLowerCase(Locale.ROOT).replace("-", "")) {
            case "speed" -> CHECK_SPEED;
            case "vertical", "fly" -> CHECK_VERTICAL;
            case "reach" -> CHECK_REACH;
            case "autoclicker", "clicker", "cps" -> CHECK_AUTOCLICKER;
            case "killaura", "aura" -> CHECK_KILLAURA;
            default -> null;
        };
    }

    public FlagRecord emitTestSignal(Player target, String requestedCheck, String actorName) {
        String check = normalizeCheck(requestedCheck);
        if (target == null || check == null) return null;
        String evidence = "TEST durch " + (actorName == null || actorName.isBlank() ? "Konsole" : actorName)
                + " - keine Gefahrenstufe";
        FlagRecord flag = record(target.getUniqueId(), target.getName(), check, 0.0D, evidence, true);
        notifyStaff(flag, getSeverity(target.getName()));
        return flag;
    }

    public boolean startTracking(Player target, String requestedCheck, long durationMillis, Player actor) {
        String check = normalizeCheck(requestedCheck);
        if (target == null || check == null || actor == null) return false;
        long duration = Math.max(10_000L, Math.min(600_000L, durationMillis));
        SignalKey key = new SignalKey(target.getUniqueId(), check);
        TrackingSession session = new TrackingSession(actor.getUniqueId(), actor.getName(), System.currentTimeMillis(),
                System.currentTimeMillis() + duration);
        trackingSessions.put(key, session);
        Bukkit.getScheduler().runTaskLater(plugin, () -> finishTracking(key), Math.max(1L, duration / 50L));
        return true;
    }

    private boolean isTracking(Player player, String check) {
        TrackingSession session = trackingSessions.get(new SignalKey(player.getUniqueId(), check));
        return session != null && session.endsAt >= System.currentTimeMillis();
    }

    private void trackSample(Player player, String check, double value, boolean suspicious, String evidence) {
        TrackingSession session = trackingSessions.get(new SignalKey(player.getUniqueId(), check));
        if (session == null || session.endsAt < System.currentTimeMillis()) return;
        session.samples++;
        if (suspicious) session.suspicious++;
        if (Math.abs(value) >= Math.abs(session.peak)) {
            session.peak = value;
            session.evidence = evidence;
        }
    }

    private void finishTracking(SignalKey key) {
        TrackingSession session = trackingSessions.remove(key);
        if (session == null) return;
        Player target = Bukkit.getPlayer(key.playerId());
        String targetName = target == null ? Bukkit.getOfflinePlayer(key.playerId()).getName() : target.getName();
        if (targetName == null) targetName = key.playerId().toString();
        long seconds = Math.max(1L, (session.endsAt - session.startedAt) / 1_000L);
        String evidence = "Live-Test " + seconds + "s durch " + session.actorName + ": " + session.samples
                + " Messungen, " + session.suspicious + " auffällig"
                + (session.evidence.isBlank() ? "" : ", Spitze: " + session.evidence);
        FlagRecord flag = record(key.playerId(), targetName, key.check(), 0.0D, evidence, true);
        Player actor = Bukkit.getPlayer(session.actorId);
        if (actor != null) {
            actor.sendMessage("§9§lAURON §8» §aMessung abgeschlossen: §f" + targetName + " §8• §b" + key.check());
            actor.sendMessage("§7Messungen: §f" + session.samples + " §8• §7Auffällig: §f" + session.suspicious);
            if (!session.evidence.isBlank()) actor.sendMessage("§7Spitzenwert: §f" + session.evidence);
        }
    }

    private boolean activeFor(Player player) {
        if (!enabled || isPausedByMaintenance() || player == null || !player.isOnline() || player.isDead()) return false;
        if (player.hasPermission("vetroforge.auron.bypass")) return false;
        if (excludedWorlds.contains(player.getWorld().getName().toLowerCase(Locale.ROOT))) return false;
        if (System.currentTimeMillis() < exemptUntil.getOrDefault(player.getUniqueId(), 0L)) return false;
        GameMode mode = player.getGameMode();
        if (mode == GameMode.CREATIVE || mode == GameMode.SPECTATOR) return false;
        return !player.getAllowFlight() && !player.isFlying() && !player.isGliding()
                && !player.isSwimming() && !player.isRiptiding() && !player.isInsideVehicle();
    }

    private double movementAllowance(Player player, Location location) {
        double allowance = Math.max(0.0D, (player.getWalkSpeed() - 0.2D) * 2.5D);
        PotionEffect speed = player.getPotionEffect(PotionEffectType.SPEED);
        if (speed != null) allowance += (speed.getAmplifier() + 1) * 0.20D;
        Material below = location.clone().subtract(0.0D, 1.0D, 0.0D).getBlock().getType();
        if (below == Material.ICE || below == Material.PACKED_ICE || below == Material.BLUE_ICE
                || below == Material.FROSTED_ICE || below == Material.SLIME_BLOCK
                || below == Material.HONEY_BLOCK || below == Material.SOUL_SAND) {
            allowance += 0.70D;
        }
        Vector velocity = player.getVelocity();
        allowance += Math.min(1.2D, Math.hypot(velocity.getX(), velocity.getZ()) * 0.75D);
        return allowance;
    }

    private double verticalAllowance(Player player) {
        PotionEffect jump = player.getPotionEffect(PotionEffectType.JUMP_BOOST);
        double allowance = jump == null ? 0.0D : (jump.getAmplifier() + 1) * 0.22D;
        if (player.getVelocity().getY() > 0.0D) allowance += Math.min(1.5D, player.getVelocity().getY());
        return allowance;
    }

    private void analyzeKillAura(Player attacker, LivingEntity target, BoundingBox targetBox, double distance, boolean normal) {
        long now = System.currentTimeMillis();
        Vector eye = attacker.getEyeLocation().toVector();
        Vector direction = targetBox.getCenter().subtract(eye);
        if (direction.lengthSquared() < 0.0001D) return;
        double angle = Math.toDegrees(attacker.getEyeLocation().getDirection().angle(direction));
        boolean blockedSight = !attacker.hasLineOfSight(target) && distance > 2.6D;
        AttackState previous = lastAttack.put(attacker.getUniqueId(),
                new AttackState(target.getUniqueId(), now));
        boolean rapidSwitch = previous != null
                && !previous.targetId().equals(target.getUniqueId())
                && now - previous.createdAt() <= killAuraSwitchMillis;

        double suspicion = 0.0D;
        List<String> reasons = new ArrayList<>();
        if (angle > killAuraMaxAngle) {
            suspicion += angle > killAuraMaxAngle + 25.0D ? 1.45D : 0.85D;
            reasons.add(String.format(Locale.ROOT, "Blickwinkel %.0f Grad", angle));
        }
        if (blockedSight) {
            suspicion += 0.85D;
            reasons.add("keine klare Sichtlinie");
        }
        if (rapidSwitch) {
            suspicion += 1.10D;
            reasons.add("Zielwechsel < " + killAuraSwitchMillis + "ms");
        }

        String evidence = reasons.isEmpty() ? String.format(Locale.ROOT, "Blickwinkel %.0f Grad", angle) : String.join(", ", reasons);
        trackSample(attacker, CHECK_KILLAURA, suspicion, suspicion >= 1.65D, evidence);
        if (!normal) return;
        if (suspicion >= 1.65D) {
            buffer(attacker, CHECK_KILLAURA, Math.min(1.75D, suspicion), String.join(", ", reasons));
        } else {
            decay(attacker.getUniqueId(), CHECK_KILLAURA, 0.35D);
        }
    }

    private double intervalVariation(Deque<Long> samples) {
        if (samples.size() < 3) return 1.0D;
        long previous = -1L;
        double total = 0.0D;
        int intervals = 0;
        for (long sample : samples) {
            if (previous >= 0L) {
                total += Math.max(1L, sample - previous);
                intervals++;
            }
            previous = sample;
        }
        if (intervals == 0) return 1.0D;
        double mean = total / intervals;
        previous = -1L;
        double variance = 0.0D;
        for (long sample : samples) {
            if (previous >= 0L) {
                double delta = Math.max(1L, sample - previous) - mean;
                variance += delta * delta;
            }
            previous = sample;
        }
        return Math.sqrt(variance / intervals) / Math.max(1.0D, mean);
    }

    private void buffer(Player player, String check, double amount, String evidence) {
        SignalKey key = new SignalKey(player.getUniqueId(), check);
        double value = Math.min(bufferNeeded + 2.0D, buffers.getOrDefault(key, 0.0D) + Math.max(0.25D, amount));
        buffers.put(key, value);
        if (value < bufferNeeded) return;

        long now = System.currentTimeMillis();
        long previous = lastAlert.getOrDefault(key, 0L);
        if (now - previous < alertCooldownMillis) return;
        lastAlert.put(key, now);
        buffers.put(key, Math.max(0.0D, value - bufferNeeded + 0.5D));

        FlagRecord flag = record(player.getUniqueId(), player.getName(), check,
                check.equals(CHECK_REACH) ? 2.0D : 1.25D, evidence);
        int severity = getSeverity(player.getName());
        plugin.getLogger().warning("[AURON] " + player.getName() + " flagged " + check
                + " | " + evidence + " | Stufe " + severity);
        notifyStaff(flag, severity);
    }

    private void notifyStaff(FlagRecord flag, int severity) {
        for (Player staff : Bukkit.getOnlinePlayers()) {
            if (!staff.hasPermission("vetroforge.auron.alerts") || !alertsEnabled(staff)) continue;
            staff.sendMessage("§8§m------------------------------------------------");
            staff.sendMessage(flag.simulated()
                    ? "§9§lAURON §8• §bTestsignal"
                    : "§9§lAURON §8• §cVerdächtige Aktivität");
            staff.sendMessage("§7Spieler: §f" + flag.playerName() + " §8• §7Check: §b" + flag.check());
            staff.sendMessage(flag.simulated()
                    ? "§7Simulation: §fkeine Gefahrenstufe §8• §7Signal: §f" + flag.evidence()
                    : "§7Stufe: §f" + severity + "/5 §8• §7Signal: §f" + flag.evidence());
            staff.sendMessage("§8Nur Hinweis, keine automatische Strafe. §7/auron player " + flag.playerName());
            staff.sendMessage("§8§m------------------------------------------------");
        }
    }

    private void decay(UUID playerId, String check, double amount) {
        SignalKey key = new SignalKey(playerId, check);
        double next = Math.max(0.0D, buffers.getOrDefault(key, 0.0D) - amount);
        if (next == 0.0D) buffers.remove(key);
        else buffers.put(key, next);
    }

    private void resetBuffers(UUID playerId) {
        buffers.keySet().removeIf(key -> key.playerId().equals(playerId));
        lastAlert.keySet().removeIf(key -> key.playerId().equals(playerId));
    }

    private double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }

    // ==================== Self-contained AURON signal store ====================
    // Kept inside this service on purpose instead of com.vetroforge.services.AntiCheatBridge --
    // see the class javadoc above for why.

    /**
     * Records one AURON signal for a player and returns it. {@code simulated} flags
     * results produced by {@code /auron test}, which never carry a danger score.
     */
    public FlagRecord record(UUID playerId, String playerName, String check, double score, String evidence, boolean simulated) {
        FlagRecord flag = new FlagRecord(playerId, playerName == null || playerName.isBlank() ? "Unbekannt" : playerName,
                check, score, evidence == null ? "" : evidence, System.currentTimeMillis(), simulated);
        Deque<FlagRecord> deque = flagsByPlayer.computeIfAbsent(playerId, ignored -> new ArrayDeque<>());
        synchronized (deque) {
            deque.addLast(flag);
            while (deque.size() > MAX_FLAGS_PER_PLAYER) deque.removeFirst();
        }
        return flag;
    }

    /** Convenience overload for real (non-simulated) signals. */
    public FlagRecord record(UUID playerId, String playerName, String check, double score, String evidence) {
        return record(playerId, playerName, check, score, evidence, false);
    }

    /** Signals still inside the 15-minute AURON window, newest first. */
    public List<FlagRecord> recentFlags(UUID playerId) {
        Deque<FlagRecord> deque = flagsByPlayer.get(playerId);
        if (deque == null) return List.of();
        long cutoff = System.currentTimeMillis() - SIGNAL_WINDOW_MILLIS;
        List<FlagRecord> result = new ArrayList<>();
        synchronized (deque) {
            for (FlagRecord flag : deque) {
                if (flag.createdAt() >= cutoff) result.add(flag);
            }
        }
        result.sort(Comparator.comparingLong(FlagRecord::createdAt).reversed());
        return result;
    }

    /** Aggregated snapshot for one player, always non-null even without any signals. */
    public PlayerSnapshot snapshot(UUID playerId) {
        List<FlagRecord> flags = recentFlags(playerId);
        String name = null;
        for (FlagRecord flag : flags) {
            if (flag.playerName() != null && !flag.playerName().isBlank() && !"Unbekannt".equals(flag.playerName())) {
                name = flag.playerName();
                break;
            }
        }
        if (name == null) {
            var offline = Bukkit.getOfflinePlayer(playerId);
            name = offline.getName() != null ? offline.getName() : "Unbekannt";
        }
        double score = realScore(flags);
        return new PlayerSnapshot(playerId, name, severityFor(score), score, flags);
    }

    /** Every player with at least one AURON signal still inside the window, most severe first. */
    public List<PlayerSnapshot> flaggedPlayers() {
        List<PlayerSnapshot> result = new ArrayList<>();
        for (UUID playerId : flagsByPlayer.keySet()) {
            PlayerSnapshot snap = snapshot(playerId);
            if (!snap.flags().isEmpty()) result.add(snap);
        }
        result.sort(Comparator.comparingInt(PlayerSnapshot::severity).reversed()
                .thenComparing(Comparator.comparingDouble(PlayerSnapshot::score).reversed()));
        return result;
    }

    /** Drops all stored AURON signals for a player (does not touch punishments). */
    public void clear(UUID playerId) {
        flagsByPlayer.remove(playerId);
    }

    /** 0..5 AURON severity for a player, derived only from this service's own signals. */
    public int getSeverity(String playerName) {
        if (playerName == null || playerName.isBlank()) return 0;
        for (UUID playerId : flagsByPlayer.keySet()) {
            PlayerSnapshot snap = snapshot(playerId);
            if (playerName.equalsIgnoreCase(snap.playerName())) return snap.severity();
        }
        return 0;
    }

    private double realScore(List<FlagRecord> flags) {
        double total = 0.0D;
        for (FlagRecord flag : flags) {
            if (!flag.simulated()) total += flag.score();
        }
        return total;
    }

    private int severityFor(double score) {
        if (score <= 0.0D) return 0;
        return (int) Math.max(0L, Math.min(5L, Math.round(Math.ceil(score / 2.5D))));
    }

    private record SignalKey(UUID playerId, String check) {}
    private record AttackState(UUID targetId, long createdAt) {}
    private static final class TrackingSession {
        private final UUID actorId;
        private final String actorName;
        private final long startedAt;
        private final long endsAt;
        private int samples;
        private int suspicious;
        private double peak;
        private String evidence = "";

        private TrackingSession(UUID actorId, String actorName, long startedAt, long endsAt) {
            this.actorId = actorId;
            this.actorName = actorName;
            this.startedAt = startedAt;
            this.endsAt = endsAt;
        }
    }
}
