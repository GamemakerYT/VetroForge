// src/main/java/com/vetroforge/manager/SeasonManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.*;

public class SeasonManager {

    private final FactionsPlugin plugin;
    private File file; private YamlConfiguration yaml;

    public SeasonManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        load();
    }

    public void load() {
        try {
            file = new File(plugin.getDataFolder(), "seasons.yml");
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            if (!file.exists()) file.createNewFile();
            yaml = YamlConfiguration.loadConfiguration(file);

            if (!yaml.isSet("current.name")) {
                yaml.set("current.name", "Season 1");
                yaml.set("current.startedAtEpoch", Instant.now().getEpochSecond());
                saveYaml();
            }
        } catch (IOException e) {
            plugin.getLogger().warning("Could not load seasons.yml: " + e.getMessage());
        }
    }

    public String getCurrentName() { return yaml.getString("current.name","Season 1"); }
    public long getCurrentStartEpoch() { return yaml.getLong("current.startedAtEpoch", Instant.now().getEpochSecond()); }

    public void setCurrentName(String name) {
        yaml.set("current.name", name);
        saveYaml();
    }

    public void archiveSnapshot() {
        try {
            String season = getCurrentName();
            File dir = new File(plugin.getDataFolder(), "playerdata");
            if (!dir.exists() || !dir.isDirectory()) return;

            List<Map<String,Object>> lb = LeaderboardUtil.buildLeaderboards(dir);
            File outDir = new File(plugin.getDataFolder(), "seasons");
            if (!outDir.exists()) outDir.mkdirs();
            File out = new File(outDir, season + "-leaderboards.yml");
            YamlConfiguration y = new YamlConfiguration();
            y.set("season", season);
            y.set("exportedAtEpoch", Instant.now().getEpochSecond());
            y.set("leaderboards", lb);
            y.save(out);

            // in archive-liste schreiben
            List<String> list = yaml.getStringList("archive");
            list.add(season + "@" + Instant.now().getEpochSecond());
            yaml.set("archive", list);
            saveYaml();
        } catch (IOException e) {
            plugin.getLogger().warning("Failed to archive snapshot: " + e.getMessage());
        }
    }

    public void resetSeason(boolean hard) {
        // 1) archivieren
        archiveSnapshot();

        // 2) Spielerdateien zurücksetzen
        File dir = new File(plugin.getDataFolder(), "playerdata");
        if (!dir.exists() || !dir.isDirectory()) return;

        File[] files = dir.listFiles((d,n) -> n.endsWith(".yml"));
        if (files == null) return;

        for (File f : files) {
            try {
                YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
                // stats
                y.set("stats.kills", 0);
                y.set("stats.deaths", 0);
                y.set("stats.streak", 0);
                // online
                y.set("online.seconds", 0L);
                if (hard) {
                    y.set("prestige", 0);
                }
                y.save(f);
            } catch (IOException ignored) {}
        }

        // 3) Season hochzählen + startdatum neu
        String old = getCurrentName();
        String next = nextSeasonName(old);
        yaml.set("current.name", next);
        yaml.set("current.startedAtEpoch", Instant.now().getEpochSecond());
        saveYaml();
    }

    private String nextSeasonName(String current) {
        // "Season X" -> "Season X+1"
        try {
            String[] p = current.trim().split("\\s+");
            int n = Integer.parseInt(p[p.length-1]);
            return current.substring(0, current.lastIndexOf(' ')) + " " + (n+1);
        } catch (Exception e) {
            return current + " *";
        }
    }

    private void saveYaml() {
        try { yaml.save(file); } catch (IOException ignored) {}
    }

    /** Hilfs-Klasse zum Sammeln von Leaderboards als strukturierte Map-Liste */
    public static final class LeaderboardUtil {
        public static List<Map<String,Object>> buildLeaderboards(File playerDataDir) {
            List<Map<String,Object>> list = new ArrayList<>();
            List<Snapshot> snaps = readAll(playerDataDir);

            // Kills
            snaps.sort(Comparator.comparingInt((Snapshot s) -> s.kills).reversed());
            list.add(export("kills", snaps));

            // KDR
            snaps.sort(Comparator.comparingDouble((Snapshot s) -> s.kdr).reversed());
            list.add(export("kdr", snaps));

            // Prestige
            snaps.sort(Comparator.comparingInt((Snapshot s) -> s.prestige).reversed());
            list.add(export("prestige", snaps));

            // Online
            snaps.sort(Comparator.comparingLong((Snapshot s) -> s.online).reversed());
            list.add(export("online", snaps));

            return list;
        }

        private static Map<String,Object> export(String key, List<Snapshot> snaps) {
            Map<String,Object> map = new LinkedHashMap<>();
            map.put("category", key);
            List<Map<String,Object>> entries = new ArrayList<>();
            int limit = Math.min(100, snaps.size());
            for (int i=0;i<limit;i++) {
                Snapshot s = snaps.get(i);
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("pos", i+1);
                row.put("uuid", s.uuid.toString());
                row.put("name", s.name);
                if ("kdr".equals(key)) row.put("value", s.kdr);
                else if ("online".equals(key)) row.put("value", s.online);
                else if ("prestige".equals(key)) row.put("value", s.prestige);
                else row.put("value", s.kills);
                entries.add(row);
            }
            map.put("entries", entries);
            return map;
        }

        private static List<Snapshot> readAll(File dir) {
            List<Snapshot> out = new ArrayList<>();
            File[] files = dir.listFiles((d,n) -> n.endsWith(".yml"));
            if (files == null) return out;
            for (File f : files) {
                try {
                    String raw = f.getName().substring(0, f.getName().length()-4);
                    java.util.UUID uuid = java.util.UUID.fromString(raw);
                    org.bukkit.OfflinePlayer off = org.bukkit.Bukkit.getOfflinePlayer(uuid);
                    String name = (off.getName() == null || off.getName().isBlank()) ? raw.substring(0,8) : off.getName();

                    org.bukkit.configuration.file.YamlConfiguration y = org.bukkit.configuration.file.YamlConfiguration.loadConfiguration(f);
                    int kills = y.getInt("stats.kills",0);
                    int deaths = y.getInt("stats.deaths",0);
                    int prestige = y.getInt("prestige",0);
                    long online = y.getLong("online.seconds",0L);
                    double kdr = deaths == 0 ? (kills == 0 ? 0.0 : (double) kills) : (kills / (double) deaths);
                    out.add(new Snapshot(uuid, name, kills, deaths, kdr, prestige, online));
                } catch (Throwable ignored) {}
            }
            return out;
        }

        private static final class Snapshot {
            final java.util.UUID uuid; final String name;
            final int kills; final int deaths; final double kdr; final int prestige; final long online;
            Snapshot(java.util.UUID u, String n, int k, int d, double r, int p, long o) { uuid=u; name=n; kills=k; deaths=d; kdr=r; prestige=p; online=o; }
        }
    }
}