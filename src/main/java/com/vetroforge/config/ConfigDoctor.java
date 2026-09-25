package com.vetroforge.config;

import com.vetroforge.FactionsPlugin;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public final class ConfigDoctor {
    private ConfigDoctor() {
    }

    public static Result mergeDefaults(FactionsPlugin plugin, boolean logNoop) {
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) {
                plugin.getLogger().warning("[ConfigDoctor] Default config.yml ist nicht im Plugin-Jar enthalten.");
                return new Result(0, false);
            }

            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            FileConfiguration live = plugin.getConfig();
            int before = live.getKeys(true).size();
            int added = copyMissing(defaults, live, "");

            if (added > 0) {
                plugin.saveConfig();
                plugin.reloadConfig();
                plugin.getLogger().warning("[ConfigDoctor] " + added
                        + " fehlende Config-Werte aus Default ergänzt. Bestehende Live-Werte wurden nicht überschrieben.");
            } else if (logNoop) {
                plugin.getLogger().info("[ConfigDoctor] Config vollständig, keine Defaults ergänzt.");
            }

            if (before < 20) {
                plugin.getLogger().warning("[ConfigDoctor] config.yml wirkt verdächtig kurz (" + before
                        + " Keys). Defaults wurden geprüft/ergänzt.");
            }

            return new Result(added, true);
        } catch (Throwable t) {
            plugin.getLogger().warning("[ConfigDoctor] Config-Merge fehlgeschlagen: " + t.getMessage());
            return new Result(0, false);
        }
    }

    public static CompactResult compact(FactionsPlugin plugin) {
        File config = new File(plugin.getDataFolder(), "config.yml");
        if (!config.isFile()) {
            plugin.getLogger().warning("[ConfigDoctor] config.yml existiert nicht.");
            return new CompactResult(false, "", 0L, 0L);
        }

        try {
            long before = config.length();
            String stamp = DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(LocalDateTime.now());
            File backupDir = new File(plugin.getDataFolder(), "backups");
            if (!backupDir.exists()) backupDir.mkdirs();
            File backup = new File(backupDir, "config.yml.bak-compact-" + stamp);
            Files.copy(config.toPath(), backup.toPath(), StandardCopyOption.REPLACE_EXISTING);

            YamlConfiguration live = YamlConfiguration.loadConfiguration(config);
            YamlConfiguration compacted = defaultBackedLiveConfig(plugin, live);
            File temp = new File(config.getParentFile(), "config.yml.compact-" + stamp + ".tmp");
            compacted.save(temp);
            YamlConfiguration validate = YamlConfiguration.loadConfiguration(temp);
            if (validate.getKeys(true).isEmpty()) {
                temp.delete();
                plugin.getLogger().warning("[ConfigDoctor] Compact abgebrochen: Ergebnis wirkt leer.");
                return new CompactResult(false, backup.getName(), before, before);
            }

            Files.move(temp.toPath(), config.toPath(), StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            plugin.reloadConfig();
            long after = config.length();
            plugin.getLogger().warning("[ConfigDoctor] config.yml kompakt gespeichert. Backup: "
                    + backup.getName() + " | " + before + " -> " + after + " Bytes");
            return new CompactResult(true, backup.getName(), before, after);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[ConfigDoctor] Compact fehlgeschlagen: " + throwable.getMessage());
            return new CompactResult(false, "", config.length(), config.length());
        }
    }

    private static YamlConfiguration defaultBackedLiveConfig(FactionsPlugin plugin, YamlConfiguration live) throws Exception {
        try (InputStream stream = plugin.getResource("config.yml")) {
            if (stream == null) throw new IllegalStateException("Default config.yml fehlt im Plugin-Jar");
            YamlConfiguration compacted = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(stream, StandardCharsets.UTF_8)
            );
            overlayKnownValues(compacted, live, "");
            return compacted;
        }
    }

    private static void overlayKnownValues(ConfigurationSection compacted, ConfigurationSection live, String prefix) {
        for (String key : compacted.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = compacted.get(key);
            if (value instanceof ConfigurationSection section) {
                overlayKnownValues(section, live, path);
                continue;
            }
            if (live.contains(path)) compacted.set(key, live.get(path));
        }
    }

    private static int copyMissing(ConfigurationSection defaults, FileConfiguration live, String prefix) {
        int added = 0;
        for (String key : defaults.getKeys(false)) {
            String path = prefix.isEmpty() ? key : prefix + "." + key;
            Object value = defaults.get(key);
            if (value instanceof ConfigurationSection section) {
                added += copyMissing(section, live, path);
                continue;
            }
            if (!live.contains(path)) {
                live.set(path, value);
                added++;
            }
        }
        return added;
    }

    public record Result(int addedKeys, boolean ok) {
    }

    public record CompactResult(boolean ok, String backupFile, long beforeBytes, long afterBytes) {
    }
}
