// src/main/java/com/vetroforge/prestige/PrestigeManager.java
package com.vetroforge.prestige;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PrestigeManager {

    private final FactionsPlugin plugin;

    // Nur XP werden hier gehalten; Level liegt im StatsManager
    private final Map<UUID, Integer> xpCache = new HashMap<>();
    private final File xpFile;
    private BukkitTask autosaveTask;

    public PrestigeManager(FactionsPlugin plugin){
        this.plugin = plugin;
        this.xpFile = new File(plugin.getDataFolder(), "prestige.yml");
        loadXp();               // beim Start vorhandene XP laden
        startAutosave();        // und Autosave einschalten (falls konfiguriert)
    }

    /* ==================== Autosave ==================== */
    public void startAutosave() {
        stopAutosave();
        int sec = Math.max(0, plugin.getConfig().getInt("prestige.autosaveSeconds", 120));
        if (sec <= 0) return; // deaktiviert
        autosaveTask = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin, this::saveXp, sec * 20L, sec * 20L
        );
    }
    public void stopAutosave() {
        if (autosaveTask != null) {
            try { autosaveTask.cancel(); } catch (Throwable ignored) {}
            autosaveTask = null;
        }
        try { saveXp(); } catch (Throwable ignored) {}
    }

    /* ==================== Public API ==================== */

    /** Mindestlevel (per Config steuerbar; Default 1). */
    private int minLevel() {
        return Math.max(0, plugin.getConfig().getInt("prestige.minLevel", 1));
    }

    /** Aktuelles Prestige-Level (aus StatsManager, mind. minLevel). */
    public int getLevel(UUID id) {
        int lv = 0;
        try { lv = plugin.getStatsManager().getPrestige(id); }
        catch (Throwable ignored) {}
        return Math.max(minLevel(), lv);
    }
    public int getLevel(Player p){ return getLevel(p.getUniqueId()); }

    /** Aktuelle XP (lokaler Cache). */
    public int getXp(UUID id){ return xpCache.getOrDefault(id, 0); }

    /** XP hinzufügen (inkl. Booster, Warzone-Bonus, Levelups, Rewards). */
    public void addXp(UUID id, int amount){
        if (amount <= 0) return;

        // Booster (global + Warzone)
        double boost = plugin.getConfig().getDouble("prestige.boosters.global", 1.0);
        Player online = Bukkit.getPlayer(id);
        if (online != null) {
            try {
                if (plugin.getWarzoneRegionManager() != null
                        && plugin.getWarzoneRegionManager().isWarzone(online.getLocation())) {
                    boost *= plugin.getConfig().getDouble("prestige.boosters.warzone", 1.0);
                }
            } catch (Throwable ignored) {}
        }
        int gain = Math.max(1, (int)Math.round(amount * boost));

        if (online != null) {
            Bukkit.getPluginManager().callEvent(new PrestigeXpGainEvent(online, gain));
        }

        // Fortschritt updaten
        int curLevel = getLevel(id);
        int curXp    = getXp(id) + gain;

        // Levelups
        int need = xpNeededFor(curLevel);
        while (curXp >= need) {
            curXp -= need;
            int from = curLevel;
            curLevel++;

            // Cap / Rebirth
            int cap = plugin.getConfig().getInt("prestige.cap", 0);
            boolean rebirth = plugin.getConfig().getBoolean("prestige.rebirth.enabled", false);
            boolean resetXp = plugin.getConfig().getBoolean("prestige.rebirth.resetXp", true);
            if (cap > 0 && curLevel > cap && rebirth) {
                curLevel = minLevel();       // zurücksetzen
                if (resetXp) curXp = 0;
            }

            // Persistiere Level
            try { plugin.getStatsManager().setPrestige(id, curLevel); } catch (Throwable ignored) {}

            // Rewards
            applyRewards(id, curLevel);

            // Titel/Sound/Broadcast/Scoreboard-Refresh übernimmt PrestigeListener.
            Player p = Bukkit.getPlayer(id);
            Bukkit.getPluginManager().callEvent(
                    new com.vetroforge.prestige.PrestigeLevelUpEvent(p, from, curLevel));

            need = xpNeededFor(curLevel);
        }

        // XP zurück schreiben (StatsManager als Spiegel mitführen, s. setXp()/loadXp())
        xpCache.put(id, Math.max(0, curXp));
        try { plugin.getStatsManager().setPrestigeXp(id, Math.max(0, curXp)); } catch (Throwable ignored) {}

        // (Optional) Fortschritts-Bossbar informieren, falls vorhanden
        try {
            var bar = plugin.getPrestigeProgressBar();
            if (bar != null && online != null) bar.notifyGain(online, gain);
        } catch (Throwable ignored) {}
    }

    public void addXp(Player p, int amount){ addXp(p.getUniqueId(), amount); }

    /** Benötigte XP für ein bestimmtes Level (geometrische/lineare Kurve per Config). */
    public int xpNeededFor(int level){
        var c = plugin.getConfig();
        int base  = Math.max(1, c.getInt("prestige.curve.base", 100));
        int per   = Math.max(0, c.getInt("prestige.curve.perLevel", 25));
        double mul= Math.max(0.01, c.getDouble("prestige.curve.multiplier", 1.0));
        return (int) Math.round((base + per * level) * mul);
    }

    /** Bedarf für die nächste Stufe dieses Spielers. */
    public int nextNeeded(UUID id) { return xpNeededFor(getLevel(id)); }

    /** Fortschritt in % auf die nächste Stufe. */
    public int getProgressPct(UUID id) {
        int need = nextNeeded(id);
        if (need <= 0) return 0;
        int cur = getXp(id);
        int pct = (int) Math.round(100.0 * cur / need);
        return Math.min(100, Math.max(0, pct));
    }

    /** Wieviel XP fehlen bis zum LevelUp. */
    public int getXpToNext(UUID id) { return Math.max(0, nextNeeded(id) - getXp(id)); }

    /** XP direkt setzen (Cache + StatsManager-Spiegel). */
    public void setXp(UUID id, int xp) {
        int v = Math.max(0, xp);
        xpCache.put(id, v);
        try { plugin.getStatsManager().setPrestigeXp(id, v); } catch (Throwable ignored) {}
    }

    /** Level direkt setzen (Persistenz via StatsManager). */
    public void setLevel(UUID id, int level) {
        int newLv = Math.max(minLevel(), level);
        try { plugin.getStatsManager().setPrestige(id, newLv); } catch (Throwable ignored) {}
    }

    /** Level um N erhöhen (mit Rewards). */
    public void addLevels(UUID id, int levels) {
        if (levels <= 0) return;
        int cur = getLevel(id);
        int target = cur + levels;
        for (int lv = cur + 1; lv <= target; lv++) {
            try { plugin.getStatsManager().setPrestige(id, lv); } catch (Throwable ignored) {}
            applyRewards(id, lv);
            Player p = Bukkit.getPlayer(id);
            Bukkit.getPluginManager().callEvent(new PrestigeLevelUpEvent(p, lv - 1, lv));
        }
    }

    /** Hübscher Text-Balken (konfigurierbar). */
    public String progressBar(UUID id) {
        int len   = Math.max(5, plugin.getConfig().getInt("prestige.progress.length", 20));
        String full  = color(plugin.getConfig().getString("prestige.progress.colorFull", "&6"))
                + plugin.getConfig().getString("prestige.progress.full", "■");
        String empty = color(plugin.getConfig().getString("prestige.progress.colorEmpty", "&7"))
                + plugin.getConfig().getString("prestige.progress.empty", "□");

        int need = nextNeeded(id);
        int cur  = getXp(id);
        double frac = (need <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, cur / (double) need)));
        int filled = (int) Math.round(frac * len);

        StringBuilder sb = new StringBuilder(len * 2 + 16);
        sb.append(color(plugin.getConfig().getString("prestige.progress.bracketL", "&8[")));
        for (int i=0;i<len;i++) sb.append(i < filled ? full : empty);
        sb.append(color(plugin.getConfig().getString("prestige.progress.bracketR", "&8]")));
        sb.append(" ").append(ChatColor.YELLOW).append((int)Math.round(frac*100)).append("%");
        return sb.toString();
    }

    public String progressText(UUID id) {
        return getXp(id) + "/" + nextNeeded(id);
    }

    // (optional) Bequemlichkeit für Online-Player:
    public String progressText(org.bukkit.entity.Player p) {
        return progressText(p.getUniqueId());
    }

    /* ==================== Rewards (Commands) ==================== */
    private void applyRewards(UUID id, int newLv){
        var sec = plugin.getConfig().getConfigurationSection("prestige.rewards");
        if (sec == null) return;
        List<String> cmds = sec.getStringList(String.valueOf(newLv));
        if (cmds == null || cmds.isEmpty()) return;

        String name = Optional.ofNullable(Bukkit.getOfflinePlayer(id)).map(OfflinePlayer::getName).orElse(id.toString());
        for (String raw : cmds) {
            String cmd = raw.replace("{PLAYER}", name);
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), cmd);
        }
    }

    /* ==================== Persistenz (nur XP) ==================== */
    public void loadXp(){
        xpCache.clear();
        if (!xpFile.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(xpFile);
        for (String k : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                int xp = y.getInt(k, 0);
                xpCache.put(id, xp);
                // StatsManager (gelesen von /stats, StatsYamlStore) auf den echten Stand bringen --
                // sonst bleibt es beim naechsten Levelup-Sync auf einem veralteten Wert haengen.
                try { plugin.getStatsManager().setPrestigeXp(id, xp); } catch (Throwable ignored) {}
            } catch (Exception ignored) {}
        }
    }
    public void saveXp(){
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<UUID,Integer> e : xpCache.entrySet()) y.set(e.getKey().toString(), e.getValue());
        try { y.save(xpFile); } catch (IOException ignored) {}
    }

    /* ==================== Utils ==================== */
    private String color(String s){
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
