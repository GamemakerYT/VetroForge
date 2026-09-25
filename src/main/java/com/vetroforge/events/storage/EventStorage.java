package com.vetroforge.events.storage;

import com.vetroforge.events.EventTemplate;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class EventStorage {

    private final Plugin plugin;
    private File file;
    private YamlConfiguration yaml;

    // Persistenz-Datei für aktives Event
    private File activeFile;

    public EventStorage(Plugin plugin) {
        this.plugin = plugin;
        ensureFiles();
        load();
    }

    private void ensureFiles() {
        file = new File(plugin.getDataFolder(), "events.yml");
        if (!file.exists()) plugin.saveResource("events.yml", false);
        activeFile = new File(plugin.getDataFolder(), "active_event.yml");
    }

    public void load() {
        yaml = YamlConfiguration.loadConfiguration(file);
    }

    /* ========== Templates & Messages ========== */

    public List<EventTemplate> readTemplates() {
        List<EventTemplate> out = new ArrayList<>();
        ConfigurationSection sec = yaml.getConfigurationSection("templates");
        if (sec == null) return out;

        for (String id : sec.getKeys(false)) {
            String p = "templates." + id + ".";
            String name = yaml.getString(p + "name", id);
            String description = yaml.getString(p + "description", "");
            String category = yaml.getString(p + "category", "Community");
            String icon = yaml.getString(p + "icon", "PAPER");
            long goal = yaml.getLong(p + "goal", 0);
            String durStr = yaml.getString(p + "duration", null);
            String source = yaml.getString(p + "progress_source", null);
            List<String> rewards = yaml.getStringList(p + "rewards");
            Duration dur = null;
            if (durStr != null && !durStr.isBlank()) dur = parseDuration(durStr.trim());
            out.add(new EventTemplate(id, name, description, category, icon, goal, dur, source, rewards));
        }
        return out;
    }

    public EventMessages readMessages() {
        return EventMessages.fromConfig(yaml.getConfigurationSection("messages"));
    }

    /** Dauer-Parser "1h30m", "45m", "2h". */
    private Duration parseDuration(String s) {
        long h = 0, m = 0;
        s = s.toLowerCase();
        String buf = "";
        for (char c : s.toCharArray()) {
            if (Character.isDigit(c)) buf += c;
            else if (c == 'h') { if (!buf.isEmpty()) { h += Long.parseLong(buf); buf = ""; } }
            else if (c == 'm') { if (!buf.isEmpty()) { m += Long.parseLong(buf); buf = ""; } }
        }
        return Duration.ofHours(h).plusMinutes(m);
    }

    /* ========== Persistenz für aktives Event ========== */

    public void saveActive(String id, String name, long current, long goal, Instant startedAt, Instant endsAt, boolean paused, String sourceKey, long sourceBase) {
        try {
            YamlConfiguration y = new YamlConfiguration();
            y.set("id", id);
            y.set("name", name);
            y.set("current", current);
            y.set("goal", goal);
            y.set("startedAt", startedAt == null ? null : startedAt.toEpochMilli());
            y.set("endsAt", endsAt == null ? null : endsAt.toEpochMilli());
            y.set("paused", paused);
            y.set("source", sourceKey);
            y.set("sourceBase", sourceBase);
            y.save(activeFile);
        } catch (IOException ignored) {}
    }

    public ActiveSnapshot loadActive() {
        if (!activeFile.exists()) return null;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(activeFile);
        String id = y.getString("id", null);
        if (id == null) return null;
        String name = y.getString("name", id);
        long current = y.getLong("current", 0);
        long goal = y.getLong("goal", 0);
        Long startMs = y.isLong("startedAt") ? y.getLong("startedAt") : null;
        Long endMs = y.isLong("endsAt") ? y.getLong("endsAt") : null;
        boolean paused = y.getBoolean("paused", false);
        String source = y.getString("source", null);
        long sourceBase = y.getLong("sourceBase", Long.MIN_VALUE);
        return new ActiveSnapshot(id, name, current, goal,
                startMs == null ? null : Instant.ofEpochMilli(startMs),
                endMs == null ? null : Instant.ofEpochMilli(endMs),
                paused, source, sourceBase);
    }

    public void clearActive() {
        if (activeFile.exists()) activeFile.delete();
    }

    /* Snapshot-DTO */
    public static final class ActiveSnapshot {
        public final String id, name, source;
        public final long current, goal;
        public final long sourceBase;
        public final Instant startedAt, endsAt;
        public final boolean paused;

        public ActiveSnapshot(String id, String name, long current, long goal,
                              Instant startedAt, Instant endsAt, boolean paused, String source, long sourceBase) {
            this.id = id; this.name = name; this.current = current; this.goal = goal;
            this.startedAt = startedAt; this.endsAt = endsAt; this.paused = paused; this.source = source; this.sourceBase = sourceBase;
        }
    }
}
