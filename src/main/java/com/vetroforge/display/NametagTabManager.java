package com.vetroforge.display;

import com.vetroforge.FactionsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scoreboard.*;

import java.util.*;

/**
 * Zeigt über dem Kopf UND in der Tab-Liste: "Rang » Name".
 * - Kein ArmorStand, keine Entities, nichts bleibt in der Welt.
 * - Overhead via Scoreboard-Team-Prefix (pro Viewer eigenes Board für saubere Sortierung).
 * - Tab-Name via Adventure (Legacy-Fallback).
 */
public class NametagTabManager implements Listener {

    private static final Component ARROW = Component.text(" » ").color(NamedTextColor.DARK_GRAY);

    private final FactionsPlugin plugin;

    /** pro Viewer ein eigenes Board (Sortierung/Prefix je Sicht) */
    private final Map<UUID, Scoreboard> viewerBoards = new HashMap<>();

    // Teams (A..Z – steuert Tab-Order und Prefix)
    private static final String TEAM_ADMIN   = "zA_ADMIN";
    private static final String TEAM_COADMIN = "zB_COADMIN";
    private static final String TEAM_MOD     = "zC_MOD";
    private static final String TEAM_BUILDER = "zD_BUILDER";
    private static final String TEAM_SUPPORT = "zE_SUPPORT";
    private static final String TEAM_MEMBER  = "zF_MEMBER";

    public NametagTabManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        // Listener registrieren; danach genügt new NametagTabManager(this) in onEnable()
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        // Bereits online? Gleich initial anwenden.
        Bukkit.getScheduler().runTask(plugin, this::applyForAll);
    }

    /* =================== Öffentliche Helfer =================== */

    /** Für einen Viewer alle Nametags rendern. */
    public void applyFor(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;

        Scoreboard board = viewerBoards.computeIfAbsent(viewer.getUniqueId(), id -> {
            ScoreboardManager sm = Bukkit.getScoreboardManager();
            if (sm == null) return null;
            Scoreboard current = viewer.getScoreboard();
            return current != null && current != sm.getMainScoreboard() ? current : sm.getNewScoreboard();
        });
        if (board == null) return;

        decorateBoard(board);

        viewer.setScoreboard(board);
    }

    public void decorateBoard(Scoreboard board) {
        if (board == null) return;
        ensureTeams(board);
        for (Player target : Bukkit.getOnlinePlayers()) {
            if (target.isOnline()) renderOneTargetOnBoard(board, target);
        }
    }

    public void attachBoard(Player viewer, Scoreboard board) {
        if (viewer == null || board == null) return;
        viewerBoards.put(viewer.getUniqueId(), board);
        decorateBoard(board);
    }

    /** Für alle Viewer aktualisieren. */
    public void applyForAll() {
        for (Player v : Bukkit.getOnlinePlayers()) applyFor(v);
    }

    public void removeViewer(Player viewer) {
        if (viewer == null) return;
        viewerBoards.remove(viewer.getUniqueId());
        try {
            org.bukkit.scoreboard.Scoreboard main = org.bukkit.Bukkit.getScoreboardManager().getMainScoreboard();
            viewer.setScoreboard(main);
        } catch (Throwable ignored) {}
    }
    /** Nur die Darstellung eines konkreten Ziels für alle Viewer aktualisieren (z. B. nach Rank-Change). */
    public void updateTargetEverywhere(Player target) {
        if (target == null || !target.isOnline()) return;
        for (Map.Entry<UUID, Scoreboard> e : viewerBoards.entrySet()) {
            Player viewer = Bukkit.getPlayer(e.getKey());
            Scoreboard board = e.getValue();
            if (viewer == null || !viewer.isOnline() || board == null) continue;
            renderOneTargetOnBoard(board, target);
            viewer.setScoreboard(board);
        }
        // Tab-Eintrag des Targets separat setzen
        applyTabName(target);
    }

    /** Beim Plugin-Stopp aufrufen. */
    public void shutdown() {
        // Tab-Namen zurücksetzen (optional)
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { p.playerListName(null); } catch (Throwable ignored) {
                try { p.setPlayerListName(null); } catch (Throwable ignored2) {}
            }
        }
        viewerBoards.clear();
    }

    /* =================== Events (Auto-Refresh) =================== */

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        // 1) Alle vorhandenen Viewer bekommen den Neuen gerendert
        for (Map.Entry<UUID, Scoreboard> en : viewerBoards.entrySet()) {
            Player viewer = Bukkit.getPlayer(en.getKey());
            Scoreboard board = en.getValue();
            if (viewer == null || !viewer.isOnline() || board == null) continue;
            renderOneTargetOnBoard(board, p);
            viewer.setScoreboard(board);
        }
        // 2) Der Neue bekommt sein eigenes Board mit allen
        applyFor(p);
        // 3) Tab-Name für den Neuen
        applyTabName(p);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        // aus allen Boards entfernen + Tab zurücksetzen
        for (Scoreboard board : viewerBoards.values()) {
            if (board == null) continue;
            removeFromAllTeams(board, p.getName());
        }
        try { p.playerListName(null); } catch (Throwable ignored) {
            try { p.setPlayerListName(null); } catch (Throwable ignored2) {}
        }
        viewerBoards.remove(p.getUniqueId());
    }

    @EventHandler
    public void onRespawn(PlayerRespawnEvent e) {
        // kurz warten, bis Bukkit Nametag refreshen kann
        Bukkit.getScheduler().runTask(plugin, () -> updateTargetEverywhere(e.getPlayer()));
    }

    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent e) {
        updateTargetEverywhere(e.getPlayer());
    }

    /* =================== Rendering intern =================== */

    private void renderOneTargetOnBoard(Scoreboard board, Player target) {
        if (board == null || target == null || !target.isOnline()) return;

        String teamKey = resolveTeamKeyFor(target);
        Team team = board.getTeam(teamKey);
        if (team == null) return;

        removeFromAllTeams(board, target.getName());
        updateTeamStyle(team, target);

        // Overhead-Prefix: Rank »  (Name kommt als Entry automatisch danach)
        Component prefix = plugin.getVetroPermsManager().getNameTagPrefix(target);

        try { team.prefix(prefix); } catch (Throwable ignored) {
            team.setPrefix(LegacyComponentSerializer.legacySection().serialize(prefix));
        }
        Component suffix = fullSuffix(target);
        try { team.suffix(suffix); } catch (Throwable ignored) {
            try { team.setSuffix(LegacyComponentSerializer.legacySection().serialize(suffix)); } catch (Throwable ignored2) {}
        }

        try { team.addEntry(target.getName()); } catch (IllegalStateException ignored) {}

        // Tab separat setzen
        applyTabName(target);
    }

    private void applyTabName(Player target) {
        if (target == null || !target.isOnline()) return;
        Component tab = plugin.getVetroPermsManager().getTabName(target).append(tabFactionSuffix(target));
        try { target.playerListName(tab); } catch (Throwable ignored) {
            try { target.setPlayerListName(LegacyComponentSerializer.legacySection().serialize(tab)); } catch (Throwable ignored2) {}
        }
    }

    /** Level + Prestige + Fraktion, rechts neben dem Namen (Overhead-Nametag). */
    private Component fullSuffix(Player target) {
        Component suffix = Component.empty();
        try { suffix = suffix.append(plugin.getVetroPermsManager().getLevelSuffix(target)); } catch (Throwable ignored) {}
        suffix = suffix.append(prestigeSuffix(target));
        suffix = suffix.append(factionSuffix(target));
        return suffix;
    }

    private Component prestigeSuffix(Player target) {
        try {
            if (plugin.getStatsManager() == null) return Component.empty();
            int points = plugin.getStatsManager().getPrestige(target);
            if (points <= 0) return Component.empty();
            String title = com.vetroforge.util.PrestigeUtil.titleFor(points);
            return Component.text(" [").color(NamedTextColor.DARK_GRAY)
                    .append(LegacyComponentSerializer.legacySection().deserialize(title))
                    .append(Component.text("]", NamedTextColor.DARK_GRAY));
        } catch (Throwable ignored) {
            return Component.empty();
        }
    }

    private Component factionSuffix(Player target) {
        String faction = factionName(target);
        if (faction == null || faction.isBlank()) return Component.empty();
        return Component.text(" ").color(NamedTextColor.DARK_GRAY)
                .append(Component.text(faction, NamedTextColor.DARK_GRAY));
    }

    private Component tabFactionSuffix(Player target) {
        String faction = factionName(target);
        if (faction == null || faction.isBlank()) return Component.empty();
        return Component.text(" • ", NamedTextColor.DARK_GRAY)
                .append(Component.text(faction, NamedTextColor.GRAY));
    }

    private String factionName(Player target) {
        try {
            if (plugin.getFactionManager() == null) return "";
            String faction = plugin.getFactionManager().getFactionOf(target.getUniqueId());
            if (faction == null || faction.isBlank()) return "";
            String display = plugin.getFactionManager().getDisplayName(faction);
            return display == null || display.isBlank() ? faction : display;
        } catch (Throwable ignored) {
            return "";
        }
    }


    private void ensureTeams(Scoreboard board) {
        ensureTeam(board, TEAM_ADMIN);
        ensureTeam(board, TEAM_COADMIN);
        ensureTeam(board, TEAM_MOD);
        ensureTeam(board, TEAM_BUILDER);
        ensureTeam(board, TEAM_SUPPORT);
        ensureTeam(board, TEAM_MEMBER);
    }
    private void ensureTeam(Scoreboard board, String name) {
        if (board.getTeam(name) == null) board.registerNewTeam(name);
    }
    private void removeFromAllTeams(Scoreboard board, String entry) {
        for (Team t : board.getTeams()) {
            if (t.hasEntry(entry)) {
                try { t.removeEntry(entry); } catch (Throwable ignored) {}
            }
        }
    }
    private String resolveTeamKeyFor(Player target) {
        switch (managedRankFor(target)) {
            case "ADMIN":   return TEAM_ADMIN;
            case "COADMIN": return TEAM_COADMIN;
            case "MOD":     return TEAM_MOD;
            case "BUILDER": return TEAM_BUILDER;
            case "SUPPORT": return TEAM_SUPPORT;
            default:        return TEAM_MEMBER;
        }
    }
    private void updateTeamStyle(Team t, Player target) {
        try { t.color(rankColor(target)); } catch (Throwable ignored) {}
        t.setCanSeeFriendlyInvisibles(true);
        t.setAllowFriendlyFire(false);
    }

    /* =================== Rank-Utils =================== */

    public static String primaryRankFor(Player p) {
        if (p == null) return "MEMBER";
        if (p.hasPermission("vetroforge.rank.admin"))   return "ADMIN";
        if (p.hasPermission("vetroforge.rank.coadmin")) return "COADMIN";
        if (p.hasPermission("vetroforge.rank.mod"))     return "MOD";
        if (p.hasPermission("vetroforge.rank.builder")) return "BUILDER";
        if (p.hasPermission("vetroforge.rank.support")) return "SUPPORT";
        return "MEMBER";
    }
    private String shortRank(Player p) {
        switch (managedRankFor(p)) {
            case "ADMIN":   return "Admin";
            case "COADMIN": return "Co-Admin";
            case "MOD":     return "Mod";
            case "BUILDER": return "Builder";
            case "SUPPORT": return "Support";
            default:        return "Spieler";
        }
    }
    private NamedTextColor rankColor(Player p) {
        switch (managedRankFor(p)) {
            case "ADMIN":   return NamedTextColor.DARK_RED;
            case "COADMIN": return NamedTextColor.RED;
            case "MOD":     return NamedTextColor.GOLD;
            case "BUILDER": return NamedTextColor.GREEN;
            case "SUPPORT": return NamedTextColor.AQUA;
            default:        return NamedTextColor.GRAY;
        }
    }

    private String managedRankFor(Player player) {
        try {
            if (plugin.getVetroPermsManager() != null) {
                String rank = plugin.getVetroPermsManager().getRank(player);
                if (rank != null && !rank.isBlank()) return normalizeRank(rank);
            }
        } catch (Throwable ignored) {
        }
        return primaryRankFor(player);
    }

    public static String normalizeRank(String rank) {
        if (rank == null) return "MEMBER";
        return switch (rank.trim().toLowerCase(Locale.ROOT)) {
            case "admin", "owner" -> "ADMIN";
            case "coadmin", "co-admin" -> "COADMIN";
            case "mod", "moderator" -> "MOD";
            case "builder" -> "BUILDER";
            case "support", "supporter" -> "SUPPORT";
            default -> "MEMBER";
        };
    }
}
