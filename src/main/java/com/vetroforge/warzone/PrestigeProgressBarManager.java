// src/main/java/com/vetroforge/warzone/PrestigeProgressBarManager.java
package com.vetroforge.warzone;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;

import java.util.*;

public class PrestigeProgressBarManager {

    private final FactionsPlugin plugin;
    private final WarzoneRegionManager warzone;

    // Pro Spieler: BossBar + Auto-Hide Task + letzter Anzeigezeitpunkt (Cooldown)
    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, Integer> hideTasks = new HashMap<>();
    private final Map<UUID, Long> lastShown = new HashMap<>();

    public PrestigeProgressBarManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.warzone = plugin.getWarzoneRegionManager();
    }

    public void start() { /* kein Loop nötig */ }

    public void stop() {
        // Bars entfernen & Tasks stoppen
        for (BossBar b : bars.values()) { try { b.removeAll(); } catch (Throwable ignored) {} }
        bars.clear();
        for (Integer id : hideTasks.values()) { try { Bukkit.getScheduler().cancelTask(id); } catch (Throwable ignored) {} }
        hideTasks.clear();
        lastShown.clear();
    }

    /** Von außen rufen, z.B. aus PrestigeManager.addXp(...). */
    public void notifyGain(Player p, int gainedXp) {
        if (!enabled()) return;
        if (p == null || !p.isOnline()) return;
        if (!isInWarzone(p)) return;                  // nur in Warzone zeigen
        long cd = cooldownMillis();
        long now = System.currentTimeMillis();
        Long last = lastShown.get(p.getUniqueId());
        if (last != null && now - last < cd) {
            // innerhalb Cooldown: nur bestehende Bar updaten und Timer verlängern
            updateBar(p);
            extendHide(p);
            return;
        }
        lastShown.put(p.getUniqueId(), now);

        // Bar erstellen/aktualisieren
        updateBar(p);
        showFor(p, durationSeconds());
    }

    /* ---------------- intern ---------------- */

    private void updateBar(Player p) {
        BossBar bar = bars.computeIfAbsent(p.getUniqueId(), id -> createBar());

        // Fortschritt (cur/need) direkt aus PrestigeManager ermitteln
        int level = plugin.getPrestigeManager().getLevel(p.getUniqueId());
        int need  = plugin.getPrestigeManager().xpNeededFor(level);  // statt nextNeeded(...)
        int cur   = plugin.getPrestigeManager().getXp(p.getUniqueId());
        int pct   = (need <= 0 ? 0 : Math.min(100, Math.max(0, (int)Math.round(100.0 * cur / (double) need * 100.0))));

        String title = titleRaw()
                .replace("{CUR}", String.valueOf(cur))
                .replace("{NEED}", String.valueOf(need))
                .replace("{PCT}", String.valueOf(pct));

        bar.setTitle(color(title));
        bar.setColor(readColor());
        bar.setStyle(readStyle());
        syncFlags(bar);

        double progress = (need <= 0 ? 1.0 : Math.max(0.0, Math.min(1.0, cur / (double) need)));
        bar.setProgress(progress);

        if (!bar.getPlayers().contains(p)) bar.addPlayer(p);
        bar.setVisible(true);
    }

    private void showFor(Player p, int seconds) {
        // alten Hide-Task ersetzen
        Integer old = hideTasks.remove(p.getUniqueId());
        if (old != null) { try { Bukkit.getScheduler().cancelTask(old); } catch (Throwable ignored) {} }
        int id = Bukkit.getScheduler().runTaskLater(plugin, () -> hide(p), Math.max(1, seconds) * 20L).getTaskId();
        hideTasks.put(p.getUniqueId(), id);
    }

    private void extendHide(Player p) {
        // einfach erneut showFor(...) mit gleicher Dauer aufrufen
        showFor(p, durationSeconds());
    }

    private void hide(Player p) {
        BossBar bar = bars.get(p.getUniqueId());
        if (bar != null) bar.setVisible(false);
        Integer old = hideTasks.remove(p.getUniqueId());
        if (old != null) { try { Bukkit.getScheduler().cancelTask(old); } catch (Throwable ignored) {} }
    }

    private BossBar createBar() {
        BossBar b = Bukkit.createBossBar(color(titleRaw()), readColor(), readStyle(), new BarFlag[0]);
        syncFlags(b);
        b.setProgress(0.0);
        return b;
    }

    private void syncFlags(BossBar b) {
        for (BarFlag f : BarFlag.values()) b.removeFlag(f);
        for (BarFlag f : readFlags()) b.addFlag(f);
    }

    private boolean isInWarzone(Player p) {
        try { return warzone != null && warzone.isWarzone(p.getLocation()); }
        catch (Throwable t) { return false; }
    }

    /* ---------------- config ---------------- */

    private boolean enabled() {
        return plugin.getConfig().getBoolean("warzone.prestigeBar.enabled", true);
    }
    private int durationSeconds() {
        return Math.max(1, plugin.getConfig().getInt("warzone.prestigeBar.durationSeconds", 10)); // 10s default
    }
    private long cooldownMillis() {
        return Math.max(0, plugin.getConfig().getLong("warzone.prestigeBar.cooldownMillis", 1500L));
    }
    private String titleRaw() {
        // Platzhalter: {CUR}, {NEED}, {PCT}
        return plugin.getConfig().getString("warzone.prestigeBar.title",
                "&6Prestige &8» &e{CUR}&7/&f{NEED} &8(&e{PCT}%&8)");
    }
    private BarColor readColor() {
        String n = plugin.getConfig().getString("warzone.prestigeBar.color", "YELLOW").toUpperCase(Locale.ROOT);
        try { return BarColor.valueOf(n); } catch (Throwable ignored) { return BarColor.YELLOW; }
    }
    private BarStyle readStyle() {
        String n = plugin.getConfig().getString("warzone.prestigeBar.style", "SEGMENTED_10").toUpperCase(Locale.ROOT);
        try { return BarStyle.valueOf(n); } catch (Throwable ignored) { return BarStyle.SEGMENTED_10; }
    }
    private List<BarFlag> readFlags() {
        List<String> raw = plugin.getConfig().getStringList("warzone.prestigeBar.flags");
        if (raw == null || raw.isEmpty()) return Collections.emptyList();
        List<BarFlag> out = new ArrayList<>();
        for (String s : raw) try { out.add(BarFlag.valueOf(s.trim().toUpperCase(Locale.ROOT))); } catch (Throwable ignored) {}
        return out;
    }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
}
