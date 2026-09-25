// src/main/java/com/vetroforge/manager/UpdateCheckerManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.URL;
import java.net.URLConnection;

public class UpdateCheckerManager {

    private final FactionsPlugin plugin;
    private final String checkUrl;
    private final boolean notifyOps;

    public UpdateCheckerManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.checkUrl = plugin.getConfig().getString("updater.checkUrl", ""); // z.B. Textdatei mit neuester Version
        this.notifyOps = plugin.getConfig().getBoolean("updater.notifyOps", true);
    }

    /** Startet den Update-Check asynchron und loggt/benachrichtigt. */
    public void check() { check(null); }

    /** Wie check(), aber mit optionalem Feedback an einen Sender. */
    public void check(CommandSender feedback) {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                if (checkUrl == null || checkUrl.isBlank()) {
                    log("No updater.checkUrl configured.");
                    if (feedback != null) reply(feedback, "§7No updater.checkUrl configured.");
                    return;
                }
                URLConnection conn = new URL(checkUrl).openConnection();
                conn.setConnectTimeout(5000);
                conn.setReadTimeout(5000);

                String remote;
                try (BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream()))) {
                    remote = br.readLine();
                }
                if (remote == null) {
                    log("Update check: empty response");
                    if (feedback != null) reply(feedback, "§7Update check: empty response");
                    return;
                }
                remote = remote.trim();
                String local = plugin.getDescription().getVersion().trim();

                int cmp = compareVersions(local, remote);
                if (cmp < 0) {
                    String msg = "A new version is available: " + remote + " (current " + local + ")";
                    log(msg);
                    if (notifyOps) {
                        Bukkit.getScheduler().runTask(plugin, () ->
                                Bukkit.getOnlinePlayers().stream().filter(p -> p.isOp())
                                        .forEach(p -> p.sendMessage("§e[VetroForge] §a" + msg))
                        );
                    }
                    if (feedback != null) reply(feedback, "§e[VetroForge] §a" + msg);
                } else {
                    String msg = "You are up to date (" + local + ").";
                    log(msg);
                    if (feedback != null) reply(feedback, "§e[VetroForge] §7" + msg);
                }
            } catch (Exception ex) {
                log("Update check failed: " + ex.getMessage());
                if (feedback != null) reply(feedback, "§cUpdate check failed.");
            }
        });
    }

    private void reply(CommandSender s, String msg) {
        Bukkit.getScheduler().runTask(plugin, () -> s.sendMessage(msg));
    }
    private void log(String s){ plugin.getLogger().info("[Updater] " + s); }

    /** Sehr robuste, simple Versions-Vergleichsfunktion. */
    static int compareVersions(String a, String b) {
        String[] as = a.split("[^0-9a-zA-Z]+");
        String[] bs = b.split("[^0-9a-zA-Z]+");
        int n = Math.max(as.length, bs.length);
        for (int i=0;i<n;i++){
            String ai = i<as.length?as[i]:"0";
            String bi = i<bs.length?bs[i]:"0";
            int ci = cmpToken(ai, bi);
            if (ci != 0) return ci;
        }
        return 0;
    }
    private static int cmpToken(String x, String y) {
        boolean xn = x.matches("\\d+");
        boolean yn = y.matches("\\d+");
        if (xn && yn) {
            long vx = Long.parseLong(x);
            long vy = Long.parseLong(y);
            return Long.compare(vx, vy);
        }
        return x.compareToIgnoreCase(y);
    }
}