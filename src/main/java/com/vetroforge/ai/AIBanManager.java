// src/main/java/com/vetroforge/ai/AIBanManager.java
package com.vetroforge.ai;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.punish.PunishmentManager;
import com.vetroforge.util.KickUtil;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;

import java.util.*;

public class AIBanManager {

    public enum Action { NONE, WARN, MUTE, TEMP_BAN, PERM_BAN }

    public static final class Decision {
        public final Action action;
        public final int minutes;       // für MUTE/TEMP_BAN
        public final String reason;
        public final double confidence; // 0..1
        public final String source;     // "ai" oder "rule"
        // Diagnose (optional)
        public final Integer diagReports;
        public final Integer diagAcSeverity;
        public final String  diagChatPreview;

        public Decision(Action a, int m, String r, double c, String src) {
            this(a, m, r, c, src, null, null, null);
        }
        public Decision(Action a, int m, String r, double c, String src,
                        Integer diagReports, Integer diagAcSeverity, String diagChatPreview) {
            this.action = a;
            this.minutes = m;
            this.reason = r;
            this.confidence = c;
            this.source = src;
            this.diagReports = diagReports;
            this.diagAcSeverity = diagAcSeverity;
            this.diagChatPreview = diagChatPreview;
        }
        public Decision withDiag(Integer rep, Integer ac, String chat) {
            return new Decision(action, minutes, reason, confidence, source, rep, ac, chat);
        }
    }

    private final FactionsPlugin plugin;

    public AIBanManager(FactionsPlugin plugin) { this.plugin = plugin; }

    /* ================= High-level (Auto) ================= */

    /** Holt Report/Chat/AC automatisch und gibt die KI-Entscheidung zurück. */
    public Decision evaluateAuto(UUID target, String playerName, String localeKey) {
        OfflinePlayer off = resolveTarget(target, playerName);
        if (plugin.getStaffProtectionService().isProtected(off)) {
            plugin.getStaffProtectionService().auditBlocked(null, playerName, "AI Review");
            return new Decision(Action.NONE, 0, "Staff-Schutz: AI-Review darf Teammitglieder nicht bestrafen", 1.0, "rule");
        }

        var cfg = plugin.getConfig();
        int reportMins = Math.max(1, cfg.getInt("punishments.integrations.reports.minutes", 60));
        int chatLines  = Math.max(1, Math.min(40, cfg.getInt("punishments.integrations.chat.lines", 6)));

        int recentReports = safeReports(playerName, reportMins);
        String recentChat = safeChat(playerName, chatLines);
        int acSeverity    = safeAc(playerName);

        Decision d = evaluate(target, recentChat, recentReports, acSeverity, localeKey);
        return (d == null) ? null : d.withDiag(recentReports, acSeverity, clip(recentChat, 200));
    }

    /** Komplett-Orchestrierung: evaluiert automatisch und führt die Entscheidung aus. */
    public void evaluateAndApplyAuto(String playerName, String localeKey, boolean silent, UUID staff) {
        if (playerName == null || playerName.isBlank()) return;
        OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
        if (plugin.getStaffProtectionService().isProtected(off)) {
            plugin.getStaffProtectionService().auditBlocked(null, playerName, "AI Auto-Review");
            plugin.getLogger().warning("[AIBan] Auto-Review fuer Staff-Mitglied blockiert: " + playerName);
            return;
        }
        Decision d = evaluateAuto(off.getUniqueId(), playerName, localeKey);
        applyDecision(d, playerName, silent, staff);
    }

    /* ================= Core Evaluate ================= */

    public Decision evaluate(UUID target, String recentChat, int recentReports, int anticheatSeverity, String localeKey) {
        if ((recentChat == null || recentChat.isBlank()) && recentReports <= 0 && anticheatSeverity <= 0) {
            return new Decision(Action.NONE, 0, "Keine verwertbaren Daten fuer AI-Review", 0.95, "rule");
        }

        // 1) KI befragen
        try {
            String system = "You are a moderation assistant for a Minecraft Factions server. "
                    + "Return a single line ONLY in the format: ACTION|minutes|reason|confidence. "
                    + "Allowed ACTION: NONE, WARN, MUTE, TEMP_BAN, PERM_BAN. "
                    + "For MUTE/TEMP_BAN set minutes>0, else 0. Confidence is 0..1. "
                    + "Be conservative. If uncertain, prefer MUTE over BAN.";

            String lang = (localeKey == null || localeKey.isEmpty()) ? "de" : localeKey.toLowerCase(Locale.ROOT);
            String user = ""
                    + "language=" + lang + "\n"
                    + "playerId=" + target + "\n"
                    + "recentReports=" + recentReports + "\n"
                    + "anticheatSeverity=" + anticheatSeverity + "\n"
                    + "recentChat=" + trimSafe(recentChat, 800) + "\n";

            String rec = plugin.getAIService().prompt(system, user, 128);
            Decision d = parseModelReply(rec);
            if (d != null) {
                if (d.confidence < 0.65 && (d.action == Action.TEMP_BAN || d.action == Action.PERM_BAN)) {
                    return new Decision(Action.MUTE, Math.max(d.minutes, 30), d.reason, d.confidence, "ai");
                }
                return new Decision(d.action, d.minutes, d.reason, clamp(d.confidence, 0, 1), "ai");
            }
        } catch (Throwable ignored) {}

        // 2) Fallback-Heuristik
        if (anticheatSeverity >= 4) {
            return new Decision(Action.TEMP_BAN, 1440, "Anticheat-Schweregrad " + anticheatSeverity, 0.8, "rule");
        }
        if (anticheatSeverity >= 3 && recentReports >= 3) {
            return new Decision(Action.TEMP_BAN, 720, "Mehrfache Reports + AC:" + anticheatSeverity, 0.7, "rule");
        }
        if (recentReports >= 5) {
            return new Decision(Action.MUTE, 60, "Viele Reports (Review erforderlich)", 0.6, "rule");
        }
        try {
            if (recentChat != null && plugin.getAIService().isToxic(recentChat)) {
                return new Decision(Action.MUTE, 30, "Toxic Chat", 0.65, "rule");
            }
        } catch (Throwable ignored) {}

        return new Decision(Action.NONE, 0, "Kein Eingriff nötig", 0.5, "rule");
    }

    /* ================= Apply Decision ================= */

    public void applyDecision(Decision d, String playerName, boolean silent, UUID staff) {
        if (d == null || d.action == Action.NONE) return;

        OfflinePlayer off = Bukkit.getOfflinePlayer(playerName);
        PunishmentManager pm = plugin.getPunishmentManager();
        if (plugin.getStaffProtectionService().isProtected(off)) {
            plugin.getStaffProtectionService().auditBlocked(null, playerName, "AI " + d.action);
            List<String> protectedLines = new ArrayList<>();
            protectedLines.add("&7Spieler: &f" + playerName);
            protectedLines.add("&7Aktion: &f" + d.action);
            protectedLines.add("&cBlockiert: &fAI-Review darf Staff nicht muten/bannen.");
            if (d.reason != null && !d.reason.isBlank()) protectedLines.add("&7Grund der AI: &f" + d.reason);
            try { pm.announce("AI-Schutz", protectedLines, true); } catch (Throwable ignored) {}
            plugin.getLogger().warning("[AIBan] Blocked " + d.action + " for protected staff target " + playerName);
            return;
        }

        // Banner-Zeilen
        List<String> lines = new ArrayList<>();
        lines.add("&7Spieler: &f" + playerName);
        lines.add("&7Quelle: &f" + d.source + " &8(conf=" + String.format(Locale.US, "%.2f", d.confidence) + ")");
        if (d.reason != null && !d.reason.isEmpty()) lines.add("&7Grund: &f" + d.reason);
        if (d.diagReports != null) lines.add("&7Reports (60m): &f" + d.diagReports);
        if (d.diagAcSeverity != null) lines.add("&7AC-Severity: " + severityBar(d.diagAcSeverity) + " &8(&f"+d.diagAcSeverity+"&8/5)");
        if (d.diagChatPreview != null && !d.diagChatPreview.isBlank()) {
            lines.add("&7Chat-Auszug:");
            for (String ln : d.diagChatPreview.split("\\r?\\n")) {
                if (ln != null && !ln.isBlank()) lines.add("&8| &7" + clip(ln, 96));
            }
        }

        switch (d.action) {
            case WARN: {
                pm.announce("Warn", lines, true);
                break;
            }
            case MUTE: {
                pm.mute(off.getUniqueId(), d.reason, staff, d.minutes * 60_000L);
                logHistory("logMute", off.getUniqueId(), d.reason, staff, (long) d.minutes * 60_000L);
                lines.add("&7Dauer: &f" + d.minutes + "m");
                pm.announce("Mute", lines, silent);
                break;
            }
            case TEMP_BAN: {
                // Vanilla-Ban mit Ablaufzeit
                Date expires = new Date(System.currentTimeMillis() + (long) d.minutes * 60_000L);
                try { Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, safe(d.reason,"Temporärer Bann"), expires, staffName(staff)); } catch (Throwable ignored) {}
                logHistory("logBan", off.getUniqueId(), d.reason, staff, 0L);
                KickUtil.kick(off, "§cDu wurdest gebannt!\n§7Grund: §f" + safe(d.reason,"-") + "\n§7Dauer: §f" + d.minutes + "m");
                lines.add("&7Dauer: &f" + d.minutes + "m (temp)");
                pm.announce("Ban", lines, silent);
                break;
            }
            case PERM_BAN: {
                try { Bukkit.getBanList(BanList.Type.NAME).addBan(playerName, safe(d.reason,"Permanenter Bann"), null, staffName(staff)); } catch (Throwable ignored) {}
                try { pm.setBanned(off.getUniqueId(), true); } catch (Throwable ignored) {}
                logHistory("logBan", off.getUniqueId(), d.reason, staff, 0L);
                KickUtil.kick(off, "§cDu wurdest gebannt!\n§7Grund: §f" + safe(d.reason,"-") + "\n§7Dauer: §fpermanent");
                lines.add("&7Dauer: &fpermanent");
                pm.announce("Ban", lines, silent);
                break;
            }
            default: break;
        }
    }

    private String staffName(UUID staff){
        if (staff == null) return "AI/Auto";
        Player p = Bukkit.getPlayer(staff);
        return (p != null) ? p.getName() : "AI/Auto";
    }

    private void logHistory(String method, UUID target, String reason, UUID staff, long durationMs) {
        var hist = plugin.getPunishmentHistory();
        if (hist == null || target == null) return;
        if ("logMute".equals(method)) {
            hist.logMute(target, reason, staff, System.currentTimeMillis(), durationMs);
            return;
        }
        hist.logBan(target, reason, staff, System.currentTimeMillis());
    }

    private OfflinePlayer resolveTarget(UUID target, String playerName) {
        try {
            if (playerName != null && !playerName.isBlank()) {
                Player online = Bukkit.getPlayerExact(playerName);
                if (online != null) return online;
            }
            if (target != null) {
                Player online = Bukkit.getPlayer(target);
                if (online != null) return online;
                return Bukkit.getOfflinePlayer(target);
            }
            if (playerName != null && !playerName.isBlank()) return Bukkit.getOfflinePlayer(playerName);
        } catch (Throwable ignored) {}
        return null;
    }

    /* ================= Parsing ================= */

    private Decision parseModelReply(String rec) {
        if (rec == null) return null;
        String s = rec.trim();
        if (s.isEmpty()) return null;

        // JSON
        if (s.startsWith("{") && s.endsWith("}")) {
            Map<String, String> m = parseTinyJson(s);
            Action a = parseAction(m.get("action"));
            int minutes = safeInt(m.get("minutes"), (a == Action.TEMP_BAN ? 1440 : (a == Action.MUTE ? 60 : 0)));
            String reason = safeStr(m.get("reason"), "AI recommendation");
            double conf = safeDouble(m.get("confidence"), 0.75);
            return new Decision(a, minutes, reason, conf, "ai");
        }

        // Pipe
        String[] parts = s.split("\\|", 4);
        Action a = parseAction(parts.length > 0 ? parts[0] : null);
        int minutes = safeInt(parts.length > 1 ? parts[1] : null, (a == Action.TEMP_BAN ? 1440 : (a == Action.MUTE ? 60 : 0)));
        String reason = parts.length > 2 ? parts[2] : "AI recommendation";
        double conf = safeDouble(parts.length > 3 ? parts[3] : null, 0.75);
        return new Decision(a, minutes, reason, conf, "ai");
    }

    private Map<String, String> parseTinyJson(String json){
        Map<String, String> out = new HashMap<>();
        String body = json.substring(1, json.length() - 1).trim(); // ohne {}
        List<String> pairs = new ArrayList<>();
        int depth = 0; StringBuilder cur = new StringBuilder();
        for (int i = 0; i < body.length(); i++) {
            char ch = body.charAt(i);
            if (ch == '"') depth ^= 1;
            if (ch == ',' && depth == 0) { pairs.add(cur.toString()); cur.setLength(0); }
            else cur.append(ch);
        }
        if (cur.length() > 0) pairs.add(cur.toString());
        for (String pair : pairs) {
            String[] kv = pair.split(":", 2);
            if (kv.length != 2) continue;
            String k = stripQuotes(kv[0].trim());
            String v = stripQuotes(kv[1].trim());
            out.put(k, v);
        }
        return out;
    }

    private String stripQuotes(String s){
        if (s == null) return null;
        s = s.trim();
        if (s.startsWith("\"") && s.endsWith("\"") && s.length() >= 2) return s.substring(1, s.length() - 1);
        return s;
    }

    /* ================= Services (optional) ================= */

    private com.vetroforge.services.ReportService reportService() {
        try {
            for (String m : new String[]{"getReportService","reportService"}) {
                try {
                    Object o = plugin.getClass().getMethod(m).invoke(plugin);
                    if (o instanceof com.vetroforge.services.ReportService rs) return rs;
                } catch (Throwable ignored) {}
            }
            for (var f : plugin.getClass().getDeclaredFields()) {
                if (com.vetroforge.services.ReportService.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object v = f.get(plugin);
                    if (v instanceof com.vetroforge.services.ReportService rs) return rs;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private com.vetroforge.services.ChatLogService chatLogService() {
        try {
            for (String m : new String[]{"getChatLogService","chatLogService"}) {
                try {
                    Object o = plugin.getClass().getMethod(m).invoke(plugin);
                    if (o instanceof com.vetroforge.services.ChatLogService cs) return cs;
                } catch (Throwable ignored) {}
            }
            for (var f : plugin.getClass().getDeclaredFields()) {
                if (com.vetroforge.services.ChatLogService.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object v = f.get(plugin);
                    if (v instanceof com.vetroforge.services.ChatLogService cs) return cs;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private com.vetroforge.services.AntiCheatBridge antiCheat() {
        try {
            for (String m : new String[]{"getAntiCheatBridge","antiCheatBridge","getAntiCheat","antiCheat"}) {
                try {
                    Object o = plugin.getClass().getMethod(m).invoke(plugin);
                    if (o instanceof com.vetroforge.services.AntiCheatBridge ac) return ac;
                } catch (Throwable ignored) {}
            }
            for (var f : plugin.getClass().getDeclaredFields()) {
                if (com.vetroforge.services.AntiCheatBridge.class.isAssignableFrom(f.getType())) {
                    f.setAccessible(true);
                    Object v = f.get(plugin);
                    if (v instanceof com.vetroforge.services.AntiCheatBridge ac) return ac;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private int safeReports(String playerName, int minutes) {
        try {
            var rs = reportService();
            if (rs == null) return 0;
            return Math.max(0, rs.getRecentReportsCount(playerName, minutes));
        } catch (Throwable ignored) { return 0; }
    }
    private String safeChat(String playerName, int maxLines) {
        try {
            var cs = chatLogService();
            if (cs == null) return "";
            return nonNull(cs.getRecentLines(playerName, maxLines));
        } catch (Throwable ignored) { return ""; }
    }
    private int safeAc(String playerName) {
        try {
            var ac = antiCheat();
            if (ac == null) return 0;
            int s = ac.getSeverity(playerName);
            return Math.max(0, Math.min(5, s));
        } catch (Throwable ignored) { return 0; }
    }

    /* ================= Utils ================= */

    private static String trimSafe(String s, int max){
        if (s == null) return "";
        String t = s;
        if (t.length() > max) t = t.substring(0, max);
        return t;
    }
    // Alias, damit safe(...) Aufrufe funktionieren
    private static String safe(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }
    private static String clip(String s, int max){ return (s==null) ? "" : (s.length()>max ? s.substring(0, max-3)+"..." : s); }
    private static String safeStr(String s, String def){ return (s == null || s.isEmpty()) ? def : s; }
    private static int safeInt(String s, int def){ try { return Integer.parseInt(String.valueOf(s).trim()); } catch (Exception e) { return def; } }
    private static double safeDouble(String s, double def){ try { return Double.parseDouble(String.valueOf(s).trim()); } catch (Exception e) { return def; } }
    private static double clamp(double v, double a, double b){ return Math.max(a, Math.min(b, v)); }
    private static Action parseAction(String s){
        if (s == null) return Action.NONE;
        try { return Action.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception e){ return Action.NONE; }
    }
    private static String nonNull(String s){ return s == null ? "" : s; }

    private String severityBar(int sev0to5) {
        int s = Math.max(0, Math.min(5, sev0to5));
        StringBuilder sb = new StringBuilder(10);
        for (int i=0;i<s;i++) sb.append("§c■");
        for (int i=s;i<5;i++) sb.append("§7□");
        return sb.toString();
    }
}
