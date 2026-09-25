// src/main/java/com/vetroforge/stats/StatsManager.java
package com.vetroforge.stats;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Dateibasierter Stats-Manager: <dataFolder>/playerdata/<uuid>.yml
 *
 * Keys:
 *  - prestige (int)
 *  - prestigeXp (int)
 *  - stats.kills (int)
 *  - stats.deaths (int)
 *  - stats.streak (int)
 *  - online.seconds (long)
 */
public class StatsManager {

    private final JavaPlugin plugin;
    public StatsManager(JavaPlugin plugin) { this.plugin = plugin; }

    /* ===================== File & YAML ===================== */

    private File playerFile(UUID id) {
        File dir = new File(plugin.getDataFolder(), "playerdata");
        if (!dir.exists()) //noinspection ResultOfMethodCallIgnored
            dir.mkdirs();
        return new File(dir, id.toString() + ".yml");
    }

    private YamlConfiguration yaml(UUID id) {
        return YamlConfiguration.loadConfiguration(playerFile(id));
    }

    private void save(UUID id, YamlConfiguration y) {
        try { y.save(playerFile(id)); } catch (IOException ignored) {}
    }

    /* ===================== Known Players ===================== */

    /** Alle UUIDs, für die es bereits playerdata gibt. */
    public Set<UUID> getKnownPlayers() {
        File dir = new File(plugin.getDataFolder(), "playerdata");
        if (!dir.exists() || !dir.isDirectory()) return Collections.emptySet();
        File[] files = dir.listFiles((d, n) -> n.endsWith(".yml"));
        if (files == null || files.length == 0) return Collections.emptySet();
        Set<UUID> out = new HashSet<>();
        for (File f : files) {
            try {
                String raw = f.getName().substring(0, f.getName().length() - 4);
                out.add(UUID.fromString(raw));
            } catch (Throwable ignored) {}
        }
        return out;
    }

    /* ===================== Kills / Deaths / Streak ===================== */

    public int getKills(UUID id) { return yaml(id).getInt("stats.kills", 0); }
    public void setKills(UUID id, int value) {
        YamlConfiguration y = yaml(id);
        y.set("stats.kills", Math.max(0, value));
        save(id, y);
    }
    public void addKills(UUID id, int delta) { setKills(id, getKills(id) + Math.max(0, delta)); }

    public int getDeaths(UUID id) { return yaml(id).getInt("stats.deaths", 0); }
    public void setDeaths(UUID id, int value) {
        YamlConfiguration y = yaml(id);
        y.set("stats.deaths", Math.max(0, value));
        save(id, y);
    }
    public void addDeaths(UUID id, int delta) { setDeaths(id, getDeaths(id) + Math.max(0, delta)); }

    public int getStreak(UUID id) { return yaml(id).getInt("stats.streak", 0); }
    public void setStreak(UUID id, int value) {
        YamlConfiguration y = yaml(id);
        y.set("stats.streak", Math.max(0, value));
        save(id, y);
    }
    public void resetStreak(UUID id) { setStreak(id, 0); }

    public int getElo(UUID id) { return yaml(id).getInt("stats.elo", 1000); }
    public void setElo(UUID id, int value) {
        YamlConfiguration y = yaml(id);
        y.set("stats.elo", Math.max(0, value));
        save(id, y);
    }
    public int getElo(Player player) { return getElo(player.getUniqueId()); }

    /** Setzt Kills, Deaths, Streak und Elo auf den Ausgangswert zurück (Prestige/Level bleiben unangetastet). */
    public void resetCombatStats(UUID id) {
        YamlConfiguration y = yaml(id);
        y.set("stats.kills", 0);
        y.set("stats.deaths", 0);
        y.set("stats.streak", 0);
        y.set("stats.elo", 1000);
        save(id, y);
    }

    /** K/D als double (Aliase: getKD/getKDR). */
    public double getKDR(UUID id) {
        int k = getKills(id), d = getDeaths(id);
        return d == 0 ? (k == 0 ? 0.0 : k) : (k / (double) d);
    }

    /* ===================== Prestige (Level) ===================== */

    public int getPrestige(UUID id) { return yaml(id).getInt("prestige", 0); }
    public void setPrestige(UUID id, int value) {
        YamlConfiguration y = yaml(id);
        y.set("prestige", Math.max(0, value));
        save(id, y);
    }

    // Aliase
    public int getPrestigeLevel(UUID id) { return getPrestige(id); }
    public void setPrestigeLevel(UUID id, int level) { setPrestige(id, level); }

    /* ===================== Prestige XP ===================== */

    public int getPrestigeXp(UUID id) { return yaml(id).getInt("prestigeXp", 0); }
    public void setPrestigeXp(UUID id, int value) {
        YamlConfiguration y = yaml(id);
        y.set("prestigeXp", Math.max(0, value));
        save(id, y);
    }
    public void addPrestigeXp(UUID id, int delta) {
        setPrestigeXp(id, getPrestigeXp(id) + Math.max(0, delta));
    }

    /* ===================== Online-Zeit ===================== */

    public long getOnlineSeconds(UUID id) { return yaml(id).getLong("online.seconds", 0L); }
    public void setOnlineSeconds(UUID id, long seconds) {
        YamlConfiguration y = yaml(id);
        y.set("online.seconds", Math.max(0L, seconds));
        save(id, y);
    }
    public void addOnlineSeconds(UUID id, long sec) {
        if (sec <= 0L) return;
        YamlConfiguration y = yaml(id);
        long cur = y.getLong("online.seconds", 0L);
        y.set("online.seconds", Math.max(0L, cur + sec));
        save(id, y);
    }

    /* ===================== Kill-Hooks ===================== */

    // Anti-Farm: killer:victim -> letzter gewerteter Kill (Epoch-Sekunden)
    private final Map<String, Long> recentKills = new ConcurrentHashMap<>();

    /**
     * Prüft, ob ein Kill von killer an victim aktuell auf Cooldown ist (wiederholtes
     * gegenseitiges Farmen), und merkt sich bei "nicht auf Cooldown" gleich den
     * jetzigen Zeitpunkt für die nächste Prüfung. Nur für die Stats-Gutschrift
     * (Kills/Streak/Elo) in StatsListener gedacht -- PvpKillListener führt für die
     * Prestige-XP-Vergabe eine eigene, unabhängige Cooldown-Prüfung durch (sonst
     * würde die erste Prüfung hier den Zeitstempel schon "verbrauchen" und die
     * zweite, unmittelbar folgende Prüfung fälschlich als Cooldown werten).
     */
    public boolean isKillOnCooldown(UUID killer, UUID victim) {
        int cd = Math.max(0, plugin.getConfig().getInt("stats.killCooldownSeconds", 120));
        if (cd <= 0) return false;
        String key = killer + ":" + victim;
        long now = System.currentTimeMillis() / 1000L;
        Long last = recentKills.put(key, now);
        return last != null && (now - last) < cd;
    }

    /** Call on player kill (killer != victim). */
    public void onKill(UUID killer, UUID victim) {
        if (killer != null) {
            addKills(killer, 1);
            setStat(killer, "daily_kills", getStat(killer, "daily_kills") + 1);
            setStreak(killer, getStreak(killer) + 1);
            setElo(killer, getElo(killer) + 15);
            // Prestige wird ausschließlich über PvpKillListener -> PrestigeManager.addXp()
            // vergeben (echte XP-Kurve inkl. Cap/Rewards). Ein direkter Level-Bump hier
            // lief unkoordiniert parallel dazu und konnte Level/XP-Anzeige auseinanderlaufen
            // lassen sowie das Level-Cap umgehen.
        }
        if (victim != null) {
            addDeaths(victim, 1);
            resetStreak(victim);
            setElo(victim, getElo(victim) - 10);
        }
    }
    public void onPlayerKill(UUID killer) { onKill(killer, null); }
    public void onPlayerDeath(UUID victim) { onKill(null, victim); }

    /* ===================== Player-Overloads ===================== */

    public int getKills(Player p)   { return getKills(p.getUniqueId()); }
    public int getDeaths(Player p)  { return getDeaths(p.getUniqueId()); }
    public int getStreak(Player p)  { return getStreak(p.getUniqueId()); }
    public void resetStreak(Player p){ resetStreak(p.getUniqueId()); }

    public double getKDR(Player p)  { return getKDR(p.getUniqueId()); }

    public int getPrestige(Player p) { return getPrestige(p.getUniqueId()); }
    public void setPrestige(Player p, int value) { setPrestige(p.getUniqueId(), value); }

    public int getPrestigeLevel(Player p) { return getPrestigeLevel(p.getUniqueId()); }
    public void setPrestigeLevel(Player p, int level) { setPrestigeLevel(p.getUniqueId(), level); }

    public int getPrestigeXp(Player p) { return getPrestigeXp(p.getUniqueId()); }
    public void setPrestigeXp(Player p, int value) { setPrestigeXp(p.getUniqueId(), value); }
    public void addPrestigeXp(Player p, int delta) { addPrestigeXp(p.getUniqueId(), delta); }

    /* ===================== Level Progression ===================== */

    public int getLevel(UUID id) {
        YamlConfiguration y = yaml(id);
        if (y.isSet("level")) return Math.max(1, Math.min(10, y.getInt("level", 1)));
        return Math.max(1, Math.min(10, y.getInt("prestige", 1)));
    }

    public void setLevel(UUID id, int value) {
        YamlConfiguration y = yaml(id);
        y.set("level", Math.max(1, Math.min(10, value)));
        save(id, y);
        refreshLevelDisplay(id);
    }

    public int getLevelXp(UUID id) {
        YamlConfiguration y = yaml(id);
        if (y.isSet("levelXp")) return Math.max(0, y.getInt("levelXp", 0));
        return Math.max(0, y.getInt("prestigeXp", 0));
    }

    public void setLevelXp(UUID id, int value) {
        YamlConfiguration y = yaml(id);
        y.set("levelXp", Math.max(0, value));
        save(id, y);
    }

    private void refreshLevelDisplay(UUID id) {
        Player player = Bukkit.getPlayer(id);
        if (player == null || !player.isOnline()) return;
        if (!(plugin instanceof FactionsPlugin factions)) return;

        Bukkit.getScheduler().runTask(factions, () -> {
            if (!player.isOnline()) return;
            if (factions.getNametagTabManager() != null) {
                factions.getNametagTabManager().updateTargetEverywhere(player);
                factions.getNametagTabManager().applyForAll();
            }
            if (factions.getTablistManager() != null) {
                factions.getTablistManager().updatePlayer(player);
            }
        });
    }

    public int getLevel(Player p) { return getLevel(p.getUniqueId()); }
    public void setLevel(Player p, int value) { setLevel(p.getUniqueId(), value); }
    public int getLevelXp(Player p) { return getLevelXp(p.getUniqueId()); }
    public void setLevelXp(Player p, int value) { setLevelXp(p.getUniqueId(), value); }

    public long getOnlineSeconds(Player p) { return getOnlineSeconds(p.getUniqueId()); }
    public void addOnlineSeconds(Player p, long sec) { addOnlineSeconds(p.getUniqueId(), sec); }

    public void onKill(Player killer, Player victim) {
        onKill(killer == null ? null : killer.getUniqueId(),
                victim == null ? null : victim.getUniqueId());
    }

    /* ===================== Join/Quit-Tracking ===================== */

    private final ConcurrentHashMap<UUID, Long> sessionLogin = new ConcurrentHashMap<>();

    public void handleJoin(Player p) {
        if (p == null) return;
        sessionLogin.put(p.getUniqueId(), System.currentTimeMillis());
    }

    public void handleQuit(Player p) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        Long start = sessionLogin.remove(id);
        if (start != null) {
            long secs = Math.max(0L, (System.currentTimeMillis() - start) / 1000L);
            addOnlineSeconds(id, secs);
        }
    }

    /* ===================== Rankings ===================== */

    /** Top-Spieler nach Kills (absteigend). */
    public List<UUID> topByKills(int limit) {
        return getKnownPlayers().stream()
                .sorted(Comparator.comparingInt((UUID u) -> getKills(u)).reversed())
                .limit(Math.max(1, limit))
                .collect(Collectors.toList());
    }

    /* ===================== Legacy-/Kompat-Aliase ===================== */
    // Diese Aliase decken alte Aufrufe/Schreibweisen ab, damit "rote" Methoden verschwinden.

    // getKills-Varianten, die oft verwendet werden:
    public int getKills(OfflinePlayer off) {
        if (off == null || off.getUniqueId() == null) return 0;
        return getKills(off.getUniqueId());
    }
    public int getKills(String name) {
        if (name == null || name.isBlank()) return 0;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return getKills(online);
        try {
            OfflinePlayer off = Bukkit.getOfflinePlayer(name);
            return (off != null && off.getUniqueId() != null) ? getKills(off.getUniqueId()) : 0;
        } catch (Throwable ignored) {
            return 0;
        }
    }
    // ===== Generic custom stats (keyed under "stats.<key>") =====
    public int getStat(java.util.UUID id, String key) {
        if (id == null || key == null || key.isBlank()) return 0;
        return yaml(id).getInt("stats." + key, 0);
    }

    public void setStat(java.util.UUID id, String key, int value) {
        if (id == null || key == null || key.isBlank()) return;
        org.bukkit.configuration.file.YamlConfiguration y = yaml(id);
        y.set("stats." + key, Math.max(0, value));
        save(id, y);
    }

    // Bequeme Overloads für Player:
    public int getStat(org.bukkit.entity.Player p, String key) {
        return (p == null) ? 0 : getStat(p.getUniqueId(), key);
    }

    public void setStat(org.bukkit.entity.Player p, String key, int value) {
        if (p != null) setStat(p.getUniqueId(), key, value);
    }

    // Schreibweisen & Tippfehler aus älterem Code:
    public double getKD(UUID id) { return getKDR(id); }
    public double getKD(Player p) { return getKDR(p); }

    public void setPrestigeXP(UUID id, int value) { setPrestigeXp(id, value); }
    public void setPrestigeXP(Player p, int value) { setPrestigeXp(p, value); }
    public void addPrestigeXP(UUID id, int delta) { addPrestigeXp(id, delta); }
    public void addPrestigeXP(Player p, int delta) { addPrestigeXp(p, delta); }

    public void addonlineseconds(UUID id, long sec) { addOnlineSeconds(id, sec); }
    public void addonlineseconds(Player p, long sec) { addOnlineSeconds(p, sec); }

    public void reserStreak(UUID id) { resetStreak(id); }
    public void reserStreak(Player p) { resetStreak(p); }

    public List<UUID> topbykills(int limit) { return topByKills(limit); }

    public int getPrestigeOfName(String name) {
        if (name == null || name.isBlank()) return 0;
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return getPrestige(online);
        try {
            OfflinePlayer off = Bukkit.getOfflinePlayer(name);
            UUID id = off != null ? off.getUniqueId() : null;
            return id != null ? getPrestige(id) : 0;
        } catch (Throwable ignored) { return 0; }
    }
    /** Tippfehler-Variante für Altcalls. */
    public int getPrestigeofName(String name) { return getPrestigeOfName(name); }

    public String getPrestigeTierName(int level) {
        try {
            return com.vetroforge.util.PrestigeUtil.titleFor(Math.max(0, level));
        } catch (Throwable ignored) {
            return "Rookie";
        }
    }
    // Prestige bequem adden
    public void addPrestige(java.util.UUID id, int delta) {
        if (id == null || delta <= 0) return;
        setPrestige(id, getPrestige(id) + delta);
    }

    public void addPrestige(org.bukkit.entity.Player p, int delta) {
        if (p != null) addPrestige(p.getUniqueId(), delta);
    }
}
