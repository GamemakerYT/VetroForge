// ===============================================
// File: src/main/java/com/vetroforge/maintenance/MaintenanceManager.java
// ===============================================
package com.vetroforge.maintenance;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Steuert Wartungsmodus inkl. Countdown, Broadcast, ActionBar und Whitelist.
 * Gesteuert über /maintenance on|off|add|remove|list
 */
public class MaintenanceManager {

    private final FactionsPlugin plugin;

    private volatile boolean enabled = false;
    private String reason = null;

    // Whitelist kann Namen oder UUIDs enthalten (wir tolerieren beides)
    private final Set<String> whitelist = ConcurrentHashMap.newKeySet();

    // Hintergrund-Tasks
    private BukkitTask countdownTask;
    private BukkitTask actionbarTask;

    public MaintenanceManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        // aus config laden
        java.util.List<String> wl = plugin.getConfig().getStringList("maintenance.whitelist");
        if (wl != null) whitelist.addAll(wl);
        this.enabled = plugin.getConfig().getBoolean("maintenance.enabled", false);
        if (enabled) startActionbar();
    }

    public boolean isEnabled() { return enabled; }
    public String  getReason() { return reason; }

    public boolean isAllowed(Player p) {
        if (!enabled) return true;
        if (p.isOp()) return true;
        // erlaubnis via permission
        if (p.hasPermission("vetroforge.maintenance.bypass")) return true;

        // check whitelist (name oder uuid)
        if (whitelist.contains(p.getName())) return true;
        if (whitelist.contains(p.getUniqueId().toString())) return true;

        return false;
    }

    public void addWhitelist(String token) {
        if (token == null || token.isEmpty()) return;
        whitelist.add(token);
        persistWhitelist();
    }
    public void removeWhitelist(String token) {
        if (token == null || token.isEmpty()) return;
        whitelist.remove(token);
        persistWhitelist();
    }
    public Set<String> getWhitelistSnapshot() {
        return new HashSet<String>(whitelist);
    }

    /** Aktiviert Maintenance mit optionalem Grund und Countdown in Sekunden. */
    public void enable(String reason, int countdownSeconds) {
        if (enabled) {
            // bereits aktiv → ggf. Countdown neu starten
            cancelCountdown();
            if (countdownSeconds > 0) startCountdown(countdownSeconds, reason);
            this.reason = reason;
            return;
        }
        this.enabled = true;
        this.reason = reason;

        // Actionbar Loop starten
        startActionbar();

        // Broadcast „Maintenance in Xs“ + Countdown (optional)
        if (countdownSeconds > 0) {
            startCountdown(countdownSeconds, reason);
        } else {
            broadcastEnabledNow(reason);
        }

        // in config merken
        plugin.getConfig().set("maintenance.enabled", true);
        plugin.saveConfig();
    }

    /** Deaktiviert Maintenance und stoppt alle Tasks. */
    public void disable() {
        if (!enabled) return;
        enabled = false;
        cancelCountdown();
        stopActionbar();
        broadcastDisabled();

        // config
        plugin.getConfig().set("maintenance.enabled", false);
        plugin.saveConfig();
    }

    /* ========================= intern ========================= */

    private void persistWhitelist() {
        plugin.getConfig().set("maintenance.whitelist", new java.util.ArrayList<String>(whitelist));
        plugin.saveConfig();
    }

    private void startActionbar() {
        stopActionbar();
        if (!plugin.getConfig().getBoolean("maintenance.actionbar.enabled", true)) return;

        final String text = color(plugin.getConfig().getString("maintenance.actionbar.text", "&cMaintenance aktiv"));
        actionbarTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                if (!enabled) return;
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try {
                        p.sendActionBar(text);
                    } catch (Throwable t) {
                        // Fallback für sehr alte Paper/Spigot:
                        try { p.spigot().sendMessage(net.md_5.bungee.api.ChatMessageType.ACTION_BAR,
                                new net.md_5.bungee.api.chat.TextComponent(ChatColor.stripColor(text))); } catch (Throwable ignored) {}
                    }
                }
            }
        }, 0L, 40L); // alle 2s
    }

    private void stopActionbar() {
        if (actionbarTask != null) try { actionbarTask.cancel(); } catch (Throwable ignored) {}
        actionbarTask = null;
    }

    private void startCountdown(final int seconds, final String rsn) {
        cancelCountdown();

        final java.util.List<Integer> announces = new java.util.ArrayList<Integer>();
        java.util.List<Integer> cfg = plugin.getConfig().getIntegerList("maintenance.countdown.announce-at");
        if (cfg == null || cfg.isEmpty()) {
            announces.add(60); announces.add(30); announces.add(10);
        } else {
            announces.addAll(cfg);
        }

        final long[] left = new long[]{ seconds };
        announceCountdown(left[0], rsn, announces);

        countdownTask = Bukkit.getScheduler().runTaskTimer(plugin, new Runnable() {
            @Override public void run() {
                left[0]--;
                if (left[0] <= 0) {
                    // Kick alle Nicht-Whitelist
                    kickNonWhitelisted(rsn);
                    broadcastEnabledNow(rsn);
                    cancelCountdown();
                    return;
                }
                // bei 10 → pro Sekunde anzeigen
                if (left[0] <= 10) {
                    announceCountdown(left[0], rsn, null);
                } else if (announces.contains((int) left[0])) {
                    announceCountdown(left[0], rsn, announces);
                }
            }
        }, 20L, 20L);
    }

    private void cancelCountdown() {
        if (countdownTask != null) try { countdownTask.cancel(); } catch (Throwable ignored) {}
        countdownTask = null;
    }

    private void announceCountdown(long seconds, String rsn, java.util.List<Integer> configured) {
        boolean hasReason = rsn != null && !rsn.isEmpty();
        java.util.List<String> lines = hasReason
                ? plugin.getConfig().getStringList("maintenance.broadcast.message-enabled")
                : plugin.getConfig().getStringList("maintenance.broadcast.message-enabled-no-reason");
        if (lines == null || lines.isEmpty()) {
            String base = (hasReason
                    ? "—————— &c&lVetro&f&lForge ——————\n&cMaintenance&7 in &f%seconds%s\n&7Grund: &f%reason%\n&7Alle Spieler werden gekickt.\n——————————————"
                    : "—————— &c&lVetro&f&lForge ——————\n&cMaintenance&7 in &f%seconds%s\n&7Alle Spieler werden gekickt.\n——————————————");
            lines = java.util.Arrays.asList(base.split("\n"));
        }
        for (String l : lines) {
            String msg = l.replace("%seconds%", String.valueOf(seconds));
            if (hasReason) msg = msg.replace("%reason%", rsn);
            Bukkit.broadcastMessage(color(msg));
        }
    }

    private void broadcastEnabledNow(String rsn) {
        // „aktiv“ Meldung (kannst du bei Bedarf besondere Nachricht ergänzen)
        boolean hasReason = rsn != null && !rsn.isEmpty();
        java.util.List<String> lines = hasReason
                ? plugin.getConfig().getStringList("maintenance.broadcast.message-enabled")
                : plugin.getConfig().getStringList("maintenance.broadcast.message-enabled-no-reason");

        if (lines == null || lines.isEmpty()) return;
        for (String l : lines) {
            String msg = l.replace("%seconds%", "0");
            if (hasReason) msg = msg.replace("%reason%", rsn);
            Bukkit.broadcastMessage(color(msg));
        }
    }

    private void broadcastDisabled() {
        java.util.List<String> lines = plugin.getConfig().getStringList("maintenance.broadcast.message-disabled");
        if (lines == null || lines.isEmpty()) {
            lines = java.util.Arrays.asList(
                    "—————— &a&lVetro&f&lForge ——————",
                    "&aMaintenance deaktiviert.",
                    "——————————————"
            );
        }
        for (String l : lines) Bukkit.broadcastMessage(color(l));
    }

    private void kickNonWhitelisted(String rsn) {
        final String kickMsg = color("&cServer in Wartung" + (rsn!=null && !rsn.isEmpty() ? " &8- &7"+rsn : ""));
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isAllowed(p)) {
                try { p.kickPlayer(kickMsg); } catch (Throwable ignored) {}
            }
        }
    }

    private String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}