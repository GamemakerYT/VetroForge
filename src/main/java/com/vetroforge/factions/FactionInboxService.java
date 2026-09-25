package com.vetroforge.factions;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;

public final class FactionInboxService implements Listener {

    private final FactionsPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;

    public FactionInboxService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/faction-inbox.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public synchronized void add(String faction, String type, String actor, String target) {
        if (faction == null || faction.isBlank()) return;
        String path = "factions." + faction;
        List<Map<?, ?>> entries = new ArrayList<>(yaml.getMapList(path));
        entries.add(Map.of(
                "type", type == null ? "info" : type,
                "actor", actor == null ? "-" : actor,
                "target", target == null ? "-" : target,
                "time", Instant.now().toString()
        ));
        yaml.set(path, trim(entries, 30));
        save();
    }

    /**
     * Trimmt auf maxEntries, behält dabei aber "attacked"-Einträge, die noch innerhalb des
     * Revenge-Fensters liegen -- /raid revenge muss sich 24h lang auf diese Einträge verlassen
     * können, egal wie viel andere Faction-Aktivität (left/kicked/...) dazwischen passiert.
     */
    private List<Map<?, ?>> trim(List<Map<?, ?>> entries, int maxEntries) {
        if (entries.size() <= maxEntries) return entries;
        Instant cutoff = Instant.now().minus(Duration.ofHours(Math.max(1, plugin.getConfig().getInt("raid.revenge.windowHours", 24))));
        List<Map<?, ?>> protectedAttacks = new ArrayList<>();
        List<Map<?, ?>> rest = new ArrayList<>();
        for (Map<?, ?> entry : entries) {
            if ("attacked".equals(value(entry, "type")) && isAfter(entry, cutoff)) protectedAttacks.add(entry);
            else rest.add(entry);
        }
        int restBudget = Math.max(0, maxEntries - protectedAttacks.size());
        if (rest.size() > restBudget) rest = new ArrayList<>(rest.subList(rest.size() - restBudget, rest.size()));
        List<Map<?, ?>> merged = new ArrayList<>(rest);
        merged.addAll(protectedAttacks);
        return merged;
    }

    private boolean isAfter(Map<?, ?> entry, Instant cutoff) {
        try {
            return Instant.parse(value(entry, "time")).isAfter(cutoff);
        } catch (Exception ignored) {
            return false;
        }
    }

    public synchronized void show(Player player, boolean clear) {
        String faction = faction(player);
        if (faction == null) {
            player.sendMessage(tr(player, "no_faction"));
            return;
        }
        String path = "factions." + faction;
        List<Map<?, ?>> entries = yaml.getMapList(path);
        if (entries.isEmpty()) {
            player.sendMessage(tr(player, "empty"));
            return;
        }
        player.sendMessage(tr(player, "header", "count", entries.size()));
        for (Map<?, ?> entry : entries) {
            String type = value(entry, "type");
            player.sendMessage(tr(player, "entry." + type,
                    "actor", value(entry, "actor"),
                    "target", value(entry, "target"),
                    "time", value(entry, "time")));
        }
        if (clear) {
            yaml.set(path, null);
            save();
            player.sendMessage(tr(player, "cleared"));
        } else {
            player.sendMessage(tr(player, "hint"));
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        String faction = faction(event.getPlayer());
        if (faction == null) return;
        int count = yaml.getMapList("factions." + faction).size();
        if (count > 0) event.getPlayer().sendMessage(tr(event.getPlayer(), "join_hint", "count", count));

        List<AttackEntry> attacks = recentAttacks(faction, Duration.ofHours(Math.max(1, plugin.getConfig().getInt("raid.revenge.windowHours", 24))));
        if (!attacks.isEmpty()) {
            AttackEntry latest = attacks.get(0);
            event.getPlayer().sendMessage(tr(event.getPlayer(), "attacked_hint",
                    "attacker", latest.attacker(),
                    "time", agoText(latest.time()),
                    "count", attacks.size()));
        }
    }

    public record AttackEntry(String attacker, Instant time) {}

    /** "attacked"-Einträge dieser Faction innerhalb des Zeitfensters, neueste zuerst (Basis für /raid revenge). */
    public synchronized List<AttackEntry> recentAttacks(String faction, Duration window) {
        if (faction == null) return List.of();
        Instant cutoff = Instant.now().minus(window);
        List<AttackEntry> out = new ArrayList<>();
        for (Map<?, ?> entry : yaml.getMapList("factions." + faction)) {
            if (!"attacked".equals(value(entry, "type"))) continue;
            Instant time;
            try {
                time = Instant.parse(value(entry, "time"));
            } catch (Exception ignored) {
                continue;
            }
            if (time.isBefore(cutoff)) continue;
            out.add(new AttackEntry(value(entry, "actor"), time));
        }
        out.sort(Comparator.comparing(AttackEntry::time).reversed());
        return out;
    }

    private String agoText(Instant time) {
        long minutes = Math.max(0, Duration.between(time, Instant.now()).toMinutes());
        if (minutes < 1) return "gerade eben";
        if (minutes < 60) return "vor " + minutes + "m";
        return "vor " + (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private String faction(Player player) {
        return plugin.getFactionManager() == null ? null
                : plugin.getFactionManager().getFactionOf(player.getUniqueId());
    }

    private String value(Map<?, ?> entry, String key) {
        Object value = entry.get(key);
        return value == null ? "-" : String.valueOf(value);
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, "faction.inbox." + key, VetroForgeUtil.vars(values));
    }

    private void save() {
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException ignored) {}
    }
}
