// src/main/java/com/vetroforge/scoreboard/SidebarService.java
package com.vetroforge.scoreboard;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.economy.EconomyManager;
import com.vetroforge.factions.FactionManager;
import com.vetroforge.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class SidebarService {

    public org.bukkit.scoreboard.Scoreboard getOrCreateBoardFor(org.bukkit.entity.Player p) {
        org.bukkit.scoreboard.Scoreboard sb = p.getScoreboard();
        org.bukkit.scoreboard.ScoreboardManager sm = org.bukkit.Bukkit.getScoreboardManager();
        if (sm == null) return sb;
        if (sb == null || sb == sm.getMainScoreboard()) sb = sm.getNewScoreboard();
        return sb;
    }

    /** Kontext-Info fürs Raid-Board. */
    public static final class RaidContext {
        public final String target;
        public final int obsidianBroken;
        public final int obsidianGoal;
        public final int secondsLeft;
        public final java.util.Set<java.util.UUID> raiders;
        public RaidContext(String target, int broken, int goal, int secs) { this(target, broken, goal, secs, new java.util.HashSet<>()); }
        public RaidContext(String target, int broken, int goal, int secs, java.util.Set<java.util.UUID> raiders) {
            this.target = target; this.obsidianBroken = broken; this.obsidianGoal = goal;
            this.secondsLeft = secs; this.raiders = (raiders == null ? new java.util.HashSet<>() : raiders);
        }
    }

    private final FactionsPlugin plugin;

    public SidebarService(FactionsPlugin plugin) { this.plugin = plugin; }

    public void showLobbyBoard(Player p) {
        String title = color(plugin.getConfig().getString("scoreboard.lobby.title", "&cVetroForge &l• &aSpawn"));
        java.util.List<String> lines = getCfgLines("scoreboard.lobby.lines");
        java.util.List<String> rendered = renderPlaceholders(p, lines);
        applyBoard(p, title, rendered);
    }

    public void showWarzoneBoard(Player p) {
        String title = color(plugin.getConfig().getString("scoreboard.warzone.title", "&6&lWarzone"));
        java.util.List<String> lines = getCfgLines("scoreboard.warzone.lines");
        java.util.List<String> rendered = renderPlaceholders(p, lines);
        applyBoard(p, title, rendered);
    }

    public void showRaidBoard(Player p, RaidContext ctx) {
        String title = color(plugin.getConfig().getString("scoreboard.raid.title", "&c&lRaid"));
        java.util.List<String> lines = getCfgLines("scoreboard.raid.lines");
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String ln : lines) {
            String rep = ln;
            rep = rep.replace("%raid_target%", safe(ctx != null ? ctx.target : "-"));
            rep = rep.replace("%raid_broken%", String.valueOf(ctx != null ? ctx.obsidianBroken : 0));
            rep = rep.replace("%raid_goal%", String.valueOf(ctx != null ? ctx.obsidianGoal : 0));
            rep = rep.replace("%raid_time%", String.valueOf(ctx != null ? ctx.secondsLeft : 0));
            rep = rep.replace("%raid_raiders%", String.valueOf(ctx != null && ctx.raiders != null ? ctx.raiders.size() : 0));
            out.add(color(rep));
        }
        applyBoard(p, title, out);
    }

    /** Reset beim Quit etc. */
    public void remove(Player p) {
        try {
            org.bukkit.scoreboard.ScoreboardManager sm = Bukkit.getScoreboardManager();
            if (sm != null) p.setScoreboard(sm.getMainScoreboard());
        } catch (Throwable ignored) {}
    }

    /* ======================= intern ======================= */

    private java.util.List<String> getCfgLines(String path) {
        java.util.List<String> raw = plugin.getConfig().getStringList(path);
        if (raw == null || raw.isEmpty()) {
            java.util.List<String> def = new java.util.ArrayList<>();
            def.add("&7no lines");
            return def;
        }
        return raw;
    }

    private java.util.List<String> renderPlaceholders(Player p, java.util.List<String> lines) {
        FactionManager fm = plugin.getFactionManager();
        EconomyManager eco = plugin.getEconomyManager();
        StatsManager sm    = plugin.getStatsManager();

        String rank = getRankName(p);
        String faction = "-";
        try {
            String fId = fm.getFactionOf(p.getUniqueId());
            if (fId != null) faction = fm.getDisplayName(fId);
        } catch (Throwable ignored) {}

        String prestige = "0";
        try { prestige = String.valueOf(sm.getPrestige(p)); } catch (Throwable ignored) {}

        String kd = "0.00";
        try { kd = String.format(java.util.Locale.US, "%.2f", sm.getKDR(p)); } catch (Throwable ignored) {}

        String money = "0";
        try { money = String.format(java.util.Locale.US, "%.0f", eco.getBalance(p.getUniqueId())); } catch (Throwable ignored) {}

        java.util.List<String> out = new java.util.ArrayList<>();
        for (String ln : lines) {
            String rep = ln;
            rep = rep.replace("%rank%", rank);
            rep = rep.replace("%faction%", faction);
            rep = rep.replace("%prestige%", prestige);
            rep = rep.replace("%kd%", kd);
            rep = rep.replace("%money%", money);
            out.add(color(rep));
        }
        return out;
    }

    private void applyBoard(Player p, String title, java.util.List<String> lines) {
        org.bukkit.scoreboard.ScoreboardManager mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;

        org.bukkit.scoreboard.Scoreboard sb = mgr.getNewScoreboard();
        org.bukkit.scoreboard.Objective obj = sb.registerNewObjective("vetro", "dummy", title);
        obj.setDisplaySlot(org.bukkit.scoreboard.DisplaySlot.SIDEBAR);

        int score = lines.size();
        for (String ln : lines) {
            String text = ln.length() > 40 ? ln.substring(0, 40) : ln;
            obj.getScore(text).setScore(score--);
        }
        p.setScoreboard(sb);
    }

    private String getRankName(Player p) {
        if (p.hasPermission("vetroforge.rank.admin"))   return ChatColor.DARK_RED + "Admin";
        if (p.hasPermission("vetroforge.rank.coadmin")) return ChatColor.RED + "Co-Admin";
        if (p.hasPermission("vetroforge.rank.mod"))     return ChatColor.GOLD + "Moderator";
        if (p.hasPermission("vetroforge.rank.builder")) return ChatColor.YELLOW + "Builder";
        if (p.hasPermission("vetroforge.rank.support")) return ChatColor.AQUA + "Supporter";
        return ChatColor.GRAY + "Spieler";
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }
    private String safe(String s)  { return (s == null || s.isEmpty()) ? "-" : s; }
}
