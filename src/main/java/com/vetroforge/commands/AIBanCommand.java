// src/main/java/com/vetroforge/commands/AIBanCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.ai.AIBanManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;

public class AIBanCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final AIBanManager ai;

    public AIBanCommand(FactionsPlugin plugin){
        this.plugin = plugin;
        this.ai = plugin.getAIBanManager() != null ? plugin.getAIBanManager() : new AIBanManager(plugin);
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vetroforge.staff.aiban")) {
            s.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (a.length < 1) {
            s.sendMessage(ChatColor.YELLOW + "Verwendung: /aiban <Spieler> [--apply] [--silent]");
            return true;
        }

        String name = a[0];
        boolean apply  = hasFlag(a, "--apply");
        boolean silent = hasFlag(a, "--silent") || hasFlag(a, "-s");

        UUID target = resolveUuid(name);
        if (target == null) { s.sendMessage(ChatColor.RED + "Unbekannter Spieler."); return true; }

        String localeKey  = "de";
        if (s instanceof Player) {
            String lk = safeGetLangKey(plugin, (Player) s);
            if (lk != null && !lk.isBlank()) localeKey = lk;
        }

        // ===== Entscheidung anfordern =====
        AIBanManager.Decision d = ai.evaluateAuto(target, name, localeKey);

        s.sendMessage(ChatColor.DARK_RED + "—————— " + ChatColor.RED + "VetroForge • AI Decision" + ChatColor.DARK_RED + " ——————");
        s.sendMessage(ChatColor.YELLOW + "Spieler: " + ChatColor.WHITE + name);
        s.sendMessage(ChatColor.YELLOW + "Empfehlung: " + ChatColor.WHITE + d.action + (d.minutes > 0 ? (" " + d.minutes + "m") : ""));
        s.sendMessage(ChatColor.YELLOW + "Grund: " + ChatColor.WHITE + d.reason);
        s.sendMessage(ChatColor.YELLOW + "Confidence: " + ChatColor.WHITE + String.format(java.util.Locale.US,"%.2f", d.confidence));
        s.sendMessage(ChatColor.YELLOW + "Quelle: " + ChatColor.WHITE + d.source);
        if (d.diagReports != null) s.sendMessage(ChatColor.YELLOW + "Reports: " + ChatColor.WHITE + d.diagReports);
        if (d.diagAcSeverity != null) s.sendMessage(ChatColor.YELLOW + "AC-Severity: " + ChatColor.WHITE + d.diagAcSeverity + "/5");
        if (d.diagChatPreview != null && !d.diagChatPreview.isBlank()) {
            s.sendMessage(ChatColor.YELLOW + "Chat-Auszug:");
            for (String line : d.diagChatPreview.split("\\r?\\n")) {
                if (!line.isBlank()) s.sendMessage(ChatColor.DARK_GRAY + "| " + ChatColor.GRAY + line);
            }
        }
        s.sendMessage(ChatColor.DARK_RED + "——————————————————————————————");

        if (apply) {
            UUID staff = (s instanceof Player) ? ((Player)s).getUniqueId() : null;
            ai.applyDecision(d, name, silent, staff);
            s.sendMessage(ChatColor.GREEN + "AI-Empfehlung angewendet" + (silent ? " (silent)" : "") + ".");
        } else {
            s.sendMessage(ChatColor.GRAY + "Hinweis: Mit " + ChatColor.YELLOW + "/aiban " + name + " --apply" + ChatColor.GRAY + " anwenden.");
        }
        return true;
    }

    /* ================= Reflection-Helper ================= */

    private String safeGetRecentChat(FactionsPlugin plugin, String playerName, int lines) {
        try {
            Object svc = callNoArg(plugin, "getChatLogService");
            if (svc == null) return "";
            // Bevorzugter Signatur-Versuch: getRecentLines(String,int)
            Object res = call(svc, "getRecentLines", new Class[]{String.class, int.class}, new Object[]{playerName, lines});
            if (res instanceof String) return (String) res;
            // Fallbacks: getLastLines / getChat / getLog
            for (String m : new String[]{"getLastLines","getChat","getLog"}) {
                res = call(svc, m, new Class[]{String.class, int.class}, new Object[]{playerName, lines});
                if (res instanceof String) return (String) res;
            }
        } catch (Throwable ignored) {}
        return "";
    }

    private int safeGetReportCount(FactionsPlugin plugin, String playerName, int minutes) {
        try {
            Object svc = callNoArg(plugin, "getReportService");
            if (svc == null) return 0;
            Object res = call(svc, "getRecentReportsCount",
                    new Class[]{String.class, int.class}, new Object[]{playerName, minutes});
            if (res instanceof Number) return ((Number) res).intValue();
            // Fallbacks
            for (String m : new String[]{"countRecent","getReports","getCount"}) {
                res = call(svc, m, new Class[]{String.class, int.class}, new Object[]{playerName, minutes});
                if (res instanceof Number) return ((Number) res).intValue();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private int safeGetAcSeverity(FactionsPlugin plugin, String playerName) {
        try {
            Object svc = callNoArg(plugin, "getAntiCheatBridge");
            if (svc == null) svc = callNoArg(plugin, "getAnticheatBridge");
            if (svc == null) return 0;
            Object res = call(svc, "getSeverity", new Class[]{String.class}, new Object[]{playerName});
            if (res instanceof Number) return ((Number) res).intValue();
            // Fallbacks
            for (String m : new String[]{"severityFor","getVL","getViolationLevel"}) {
                res = call(svc, m, new Class[]{String.class}, new Object[]{playerName});
                if (res instanceof Number) return ((Number) res).intValue();
            }
        } catch (Throwable ignored) {}
        return 0;
    }

    private String safeGetLangKey(FactionsPlugin plugin, Player p) {
        try {
            Object lm = callNoArg(plugin, "getLanguageManager");
            if (lm == null) return null;
            Object res = call(lm, "getPlayerLangKey", new Class[]{Player.class}, new Object[]{p});
            if (res instanceof String) return (String) res;
            // Fallbacks
            for (String m : new String[]{"getLang","getLanguage","getLocale"}) {
                res = call(lm, m, new Class[]{Player.class}, new Object[]{p});
                if (res instanceof String) return (String) res;
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private Object callNoArg(Object obj, String method) {
        try {
            Method m = obj.getClass().getMethod(method);
            return m.invoke(obj);
        } catch (Throwable ignored) { return null; }
    }
    private Object call(Object obj, String method, Class<?>[] sig, Object[] args) {
        try {
            Method m = obj.getClass().getMethod(method, sig);
            return m.invoke(obj, args);
        } catch (Throwable ignored) { return null; }
    }

    private UUID resolveUuid(String name) {
        Player online = Bukkit.getPlayerExact(name);
        if (online != null) return online.getUniqueId();
        try {
            OfflinePlayer off = Bukkit.getOfflinePlayer(name);
            if (off != null) return off.getUniqueId();
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean hasFlag(String[] arr, String flag){
        for (String s : arr) if (flag.equalsIgnoreCase(s)) return true;
        return false;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length==1) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return org.bukkit.util.StringUtil.copyPartialMatches(a[0], names, new ArrayList<>());
        }
        if (a.length>=2) {
            return org.bukkit.util.StringUtil.copyPartialMatches(
                    a[a.length-1],
                    Arrays.asList("--apply","--silent","-s"),
                    new ArrayList<>()
            );
        }
        return Collections.emptyList();
    }
}
