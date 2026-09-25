package com.vetroforge.events;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.events.progress.EventProgressSource;
import com.vetroforge.events.progress.ProgressRegistry;
import com.vetroforge.events.progress.impl.CactusSoldProgress;
import com.vetroforge.events.storage.EventMessages;
import com.vetroforge.events.storage.EventStorage;
import com.vetroforge.items.CustomItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicLong;
import org.bukkit.event.HandlerList;



public class EventManager implements EventService {

    private final Plugin plugin;
    private final Clock clock = Clock.systemDefaultZone();

    private final EventStorage storage;
    private final ProgressRegistry progressRegistry;

    private final Map<String, EventTemplate> templates = new LinkedHashMap<>();
    private volatile Active active; // laufendes Event
    private BukkitTask tickTask;

    // NEW: Messages
    private EventMessages messages;

    // NEW: Persistenz-Intervall (alle 60s)
    private long saveCounter = 0;
    private boolean milestonesEnabled = true;
    private int[] milestonePercents = new int[]{10,25,50,75,90};
    private int lastMilestone = 0;


    public EventManager(Plugin plugin) {
        this.plugin = plugin;
        this.storage = new EventStorage(plugin);
        this.progressRegistry = new ProgressRegistry();
        this.progressRegistry.register("cactus_sold", new CactusSoldProgress());

        reloadTemplates();       // lädt auch messages
        restoreActiveIfPresent();// versucht aktives Event wiederherzustellen
        startTick();
    }

    /* ===== lifecycle ===== */

    private void startTick() {
        if (tickTask != null) tickTask.cancel();
        tickTask = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L * 5); // alle 5s
    }

    private void tick() {
        Active a = active;
        if (a == null || a.paused) return;

        // Fortschritt aktualisieren (aus Quelle)
        if (a.source != null) {
            refreshSourceProgress(a);
        }

        maybeBroadcastMilestone(a);

        // NEW: periodisch persistieren
        if (++saveCounter % 12 == 0) { // 12 * 5s = 60s
            persistActive();
        }

        // Auto-Ende?
        if (a.endsAt != null && Instant.now(clock).isAfter(a.endsAt)) {
            complete(true);
            return;
        }

        // Ziel erreicht?
        if (a.goal > 0 && a.current.get() >= a.goal) {
            complete(false);
        }
    }

    private void complete(boolean timeUp) {
        Active a = active;
        if (a == null) return;

        // Broadcast mit Templates
        var ph = placeholders(a);
        if (timeUp) broadcast(messages.format(messages.timeout, ph));
        else        broadcast(messages.format(messages.complete, ph));

        for (Player p : Bukkit.getOnlinePlayers()) {
            try { p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f); } catch (Throwable ignored) {}
        }
        if (!timeUp) executeRewards(a);

        active = null;
        storage.clearActive();
    }

    private void broadcast(String msg) {
        Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(msg));
        Bukkit.getConsoleSender().sendMessage(msg);
    }

    private void executeRewards(Active activeEvent) {
        EventTemplate template = templates.get(activeEvent.id);
        if (template == null || template.rewards == null) return;
        for (String reward : template.rewards) {
            if (reward == null) continue;
            if (reward.startsWith("broadcast:")) {
                broadcast(org.bukkit.ChatColor.translateAlternateColorCodes('&', reward.substring("broadcast:".length())));
                continue;
            }
            if (reward.startsWith("command:") || reward.startsWith("console:")) {
                String command = reward.substring(reward.indexOf(':') + 1).trim();
                if (!command.isBlank()) {
                    command = applyRewardPlaceholders(command, activeEvent);
                    Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
                }
                continue;
            }
            if (reward.startsWith("event_tokens:") && plugin instanceof FactionsPlugin factionsPlugin) {
                try {
                    int amount = Math.max(1, Integer.parseInt(reward.substring("event_tokens:".length())));
                    for (Player player : Bukkit.getOnlinePlayers()) {
                        player.getInventory().addItem(CustomItemUtil.makeEventToken(factionsPlugin, amount)).values()
                                .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
                    }
                } catch (NumberFormatException ignored) {
                }
                continue;
            }
            if ((reward.startsWith("coins:") || reward.startsWith("money:")) && plugin instanceof FactionsPlugin factionsPlugin) {
                String raw = reward.substring(reward.indexOf(':') + 1);
                try {
                    giveCoins(factionsPlugin, Math.max(1, Integer.parseInt(raw)));
                } catch (NumberFormatException ignored) {
                }
                continue;
            }
            if (reward.startsWith("crystals:") && plugin instanceof FactionsPlugin factionsPlugin) {
                try {
                    giveCrystals(factionsPlugin, Math.max(1, Integer.parseInt(reward.substring("crystals:".length()))));
                } catch (NumberFormatException ignored) {
                }
            }
        }
    }

    private String applyRewardPlaceholders(String command, Active activeEvent) {
        return command
                .replace("{event}", activeEvent.id)
                .replace("{event_id}", activeEvent.id)
                .replace("{event_name}", activeEvent.name)
                .replace("{current}", String.valueOf(activeEvent.current.get()))
                .replace("{goal}", String.valueOf(activeEvent.goal))
                .replace("{pct}", String.valueOf(activeEvent.pct()));
    }

    private void giveCoins(FactionsPlugin factionsPlugin, int amount) {
        if (factionsPlugin.getCoinsManager() == null) return;
        for (Player player : Bukkit.getOnlinePlayers()) {
            factionsPlugin.getCoinsManager().addCoins(player.getUniqueId(), amount);
            player.sendMessage("§8[§dEvent§8] §7Du erhältst §6+" + amount + " Coins§7.");
            player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.25f);
        }
    }

    private void giveCrystals(FactionsPlugin factionsPlugin, int amount) {
        if (factionsPlugin.getRaidCrystalManager() == null) return;

        if (factionsPlugin.getRaidCrystalManager().useFactionSource()) {
            Set<String> rewardedFactions = new HashSet<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                String factionId = factionsPlugin.getFactionManager() == null
                        ? null
                        : factionsPlugin.getFactionManager().getFactionOf(player.getUniqueId());
                if (factionId == null || !rewardedFactions.add(factionId)) continue;
                factionsPlugin.getRaidCrystalManager().addForFaction(factionId, amount);
            }
            for (Player player : Bukkit.getOnlinePlayers()) {
                String factionId = factionsPlugin.getFactionManager() == null
                        ? null
                        : factionsPlugin.getFactionManager().getFactionOf(player.getUniqueId());
                if (factionId != null && rewardedFactions.contains(factionId)) {
                    player.sendMessage("§8[§dEvent§8] §7Deine Faction erhält §d+" + amount + " Crystals§7.");
                    player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.15f);
                }
            }
            return;
        }

        for (Player player : Bukkit.getOnlinePlayers()) {
            factionsPlugin.getRaidCrystalManager().addForPlayer(player.getUniqueId(), amount);
            player.sendMessage("§8[§dEvent§8] §7Du erhältst §d+" + amount + " Crystals§7.");
            player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.8f, 1.15f);
        }
    }

    /* ===== API ===== */

    @Override
    public Optional<RunningEvent> getActiveEvent() {
        Active a = active;
        if (a == null) return Optional.empty();
        return Optional.of(new RunningEvent(a.id, a.name, a.current.get(), a.goal, a.startedAt, a.endsAt, a.paused));
    }

    @Override
    public Collection<EventTemplate> getTemplates() {
        return Collections.unmodifiableCollection(templates.values());
    }

    @Override
    public void reloadTemplates() {
        storage.load();
        templates.clear();
        for (EventTemplate t : storage.readTemplates()) templates.put(t.id, t);
        messages = storage.readMessages();

        // NEW: milestones laden
        var cfg = storage; // gleiche Datei
        try {
            var y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(
                    new java.io.File(plugin.getDataFolder(), "events.yml"));
            milestonesEnabled = y.getBoolean("milestones.enabled", true);
            java.util.List<Integer> list = y.getIntegerList("milestones.percents");
            if (list != null && !list.isEmpty()) {
                milestonePercents = list.stream().mapToInt(Integer::intValue).sorted().toArray();
            }
        } catch (Exception ignored) {}
    }

    @Override
    public void start(String templateId) throws IllegalArgumentException {
        EventTemplate t = templates.get(templateId);
        if (t == null) throw new IllegalArgumentException("Unbekanntes Template: " + templateId);

        EventProgressSource src = progressRegistry.get(t.progressSource);
        Instant now = Instant.now(clock);
        Instant ends = (t.duration == null) ? null : now.plus(t.duration);
        long sourceBase = src == null ? 0L : Math.max(0L, src.readCurrent());
        active = new Active(t.id, t.name, t.goal, new AtomicLong(0L), now, ends, false, src, t.progressSource, sourceBase);

        // NEW: persist + broadcast (start)
        persistActive();
        broadcast(messages.format(messages.start, placeholders(active)));
        lastMilestone = 0;
    }

    @Override
    public void stop(boolean silent) {
        Active a = active;
        if (a == null) return;
        active = null;
        storage.clearActive();
        if (!silent) broadcast(messages.format(messages.stop, EventMessages.basePlaceholders(a.id, a.name, a.current.get(), a.goal, a.pct(), endsIn(a.endsAt))));
    }

    @Override
    public void pause() {
        if (active != null && !active.paused) {
            active.paused = true;
            persistActive();
            broadcast(messages.format(messages.pause, placeholders(active)));
        }
    }

    @Override
    public void resume() {
        if (active != null && active.paused) {
            active.paused = false;
            persistActive();
            broadcast(messages.format(messages.resume, placeholders(active)));
        }
    }

    @Override
    public void setProgress(long absolute) {
        if (active != null) {
            active.current.set(Math.max(0, absolute));
            alignSourceBase(active);
            persistActive();
            maybeBroadcastMilestone(active);
            // optional: progress broadcast (deaktivierbar, falls zu spammy)
            // broadcast(messages.format(messages.progress, placeholders(active)));
        }
    }

    @Override
    public void addProgress(long delta) {
        if (active != null) {
            active.current.set(Math.max(0L, active.current.get() + delta));
            alignSourceBase(active);
            persistActive();
            maybeBroadcastMilestone(active);
            // optional: broadcast(messages.format(messages.progress, placeholders(active)));
        }
    }

    public void addProgressIfActive(String templateId, long delta) {
        if (templateId == null || active == null || !templateId.equalsIgnoreCase(active.id)) return;
        if (active.source != null) {
            refreshSourceProgress(active);
            persistActive();
            maybeBroadcastMilestone(active);
            return;
        }
        addProgress(delta);
    }

    public void shutdown() {
        persistActive();
        if (tickTask != null) tickTask.cancel();
        tickTask = null;
    }

    /* ===== intern ===== */

    private void persistActive() {
        Active a = active;
        if (a == null) return;
        storage.saveActive(a.id, a.name, a.current.get(), a.goal, a.startedAt, a.endsAt, a.paused, a.sourceKey, a.sourceBase);
    }

    private void restoreActiveIfPresent() {
        var snap = storage.loadActive();
        if (snap == null) return;

        EventProgressSource src = progressRegistry.get(snap.source);
        long sourceBase = snap.sourceBase;
        if (sourceBase == Long.MIN_VALUE) {
            long sourceCurrent = src == null ? 0L : Math.max(0L, src.readCurrent());
            sourceBase = Math.max(0L, sourceCurrent - Math.max(0L, snap.current));
        }
        active = new Active(snap.id, snap.name, snap.goal, new AtomicLong(Math.max(0, snap.current)),
                snap.startedAt == null ? Instant.now(clock) : snap.startedAt,
                snap.endsAt, snap.paused, src, snap.source, sourceBase);
        lastMilestone = highestReachedMilestone(active.pct());

        // Beim Restore nicht nochmal „gestartet“-Broadcast spammen.
        Bukkit.getConsoleSender().sendMessage("§8[§dEvent§8] §7Restore aktives Event: §f" + snap.name);
    }

    private void maybeBroadcastMilestone(Active a) {
        if (!milestonesEnabled || a == null || a.goal <= 0) return;

        int reached = highestReachedMilestone(a.pct());
        if (reached <= lastMilestone) return;

        lastMilestone = reached;
        Map<String, String> ph = placeholders(a);
        ph.put("pct", String.valueOf(reached));
        broadcast(messages.format(messages.milestone, ph));
    }

    private int highestReachedMilestone(int pct) {
        int reached = 0;
        for (int milestone : milestonePercents) {
            if (milestone > 0 && milestone < 100 && pct >= milestone) {
                reached = milestone;
            }
        }
        return reached;
    }

    private Map<String, String> placeholders(Active a) {
        return EventMessages.basePlaceholders(
                a.id, a.name, a.current.get(), a.goal, a.pct(), endsIn(a.endsAt)
        );
    }

    public String debugActiveEvent() {
        Active a = active;
        if (a == null) return "Kein Event aktiv";
        long raw = a.source == null ? -1L : Math.max(0L, a.source.readCurrent());
        return a.name + " (" + a.id + ") "
                + a.current.get() + "/" + a.goal
                + " " + a.pct() + "%"
                + " source=" + (a.sourceKey == null ? "-" : a.sourceKey)
                + (a.source == null ? "" : " raw=" + raw + " base=" + a.sourceBase);
    }

    public ProgressDebugSnapshot debugProgressSnapshot() {
        Active a = active;
        if (a == null) return null;
        long raw = a.source == null ? -1L : Math.max(0L, a.source.readCurrent());
        long current = a.current.get();
        return new ProgressDebugSnapshot(
                a.id,
                a.name,
                current,
                a.goal,
                a.pct(),
                a.sourceKey,
                a.sourceBase,
                raw,
                raw < 0L ? current : Math.max(0L, raw - a.sourceBase),
                a.startedAt,
                a.endsAt,
                a.paused,
                endsIn(a.endsAt)
        );
    }

    public boolean isActiveEvent(String templateId) {
        Active a = active;
        return a != null && templateId != null && templateId.equalsIgnoreCase(a.id);
    }

    private void refreshSourceProgress(Active a) {
        if (a == null || a.source == null) return;
        long raw = Math.max(0L, a.source.readCurrent());
        a.current.set(Math.max(0L, raw - a.sourceBase));
    }

    private void alignSourceBase(Active a) {
        if (a == null || a.source == null) return;
        long raw = Math.max(0L, a.source.readCurrent());
        a.sourceBase = Math.max(0L, raw - Math.max(0L, a.current.get()));
    }

    private String endsIn(Instant endsAt) {
        if (endsAt == null) return "";
        Duration d = Duration.between(Instant.now(clock), endsAt);
        long secs = d.getSeconds();
        if (secs <= 0) return "§8(§7endet jetzt§8)";
        long h = secs / 3600;
        long m = (secs % 3600) / 60;
        return "§8(§7Ende in §f" + (h > 0 ? (h + "h ") : "") + m + "m§8)";
    }

    private static final class Active {
        final String id, name, sourceKey;
        final long goal;
        final AtomicLong current;
        final Instant startedAt;
        final Instant endsAt;
        volatile boolean paused;
        final EventProgressSource source;
        long sourceBase;

        Active(String id, String name, long goal, AtomicLong current,
               Instant startedAt, Instant endsAt, boolean paused,
               EventProgressSource source, String sourceKey, long sourceBase) {
            this.id = id; this.name = name; this.goal = goal; this.current = current;
            this.startedAt = startedAt; this.endsAt = endsAt; this.paused = paused;
            this.source = source; this.sourceKey = sourceKey;
            this.sourceBase = Math.max(0L, sourceBase);
        }

        int pct() {
            if (goal <= 0) return 0;
            long p = Math.round((current.get() * 100.0) / goal);
            if (p < 0) p = 0; if (p > 100) p = 100;
            return (int) p;
        }
    }

    public record ProgressDebugSnapshot(
            String id,
            String name,
            long current,
            long goal,
            int pct,
            String sourceKey,
            long sourceBase,
            long sourceRaw,
            long sourceDelta,
            Instant startedAt,
            Instant endsAt,
            boolean paused,
            String endsIn
    ) {}
}
