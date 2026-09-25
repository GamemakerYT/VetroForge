// src/main/java/com/vetroforge/manager/TablistManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.HashMap;
import java.util.Map;

public class TablistManager {
    private static final LegacyComponentSerializer LEGACY = LegacyComponentSerializer.legacySection();

    private final VetroPermsManager perms;
    private final Scoreboard board;
    private final Map<String, Team> teams = new HashMap<>();

    public TablistManager(FactionsPlugin plugin) {
        this.perms = plugin.getVetroPermsManager();
        this.board = Bukkit.getScoreboardManager().getNewScoreboard();
        setupTeams();
    }

    private void setupTeams() {
        for (Map.Entry<String, VetroPermsManager.Rank> e : perms.getRanks().entrySet()) {
            String rankName = e.getKey();
            VetroPermsManager.Rank r = e.getValue();

            Team team = board.registerNewTeam(rankName);

            // Farbe per Team setzen (Adventure), Fallback auf alte API
            trySetTeamColor(team, r.color);

            // Prefix als Component setzen (Adventure), Fallback auf String-API
            if ("Member".equalsIgnoreCase(rankName)) {
                // Nur Farbe – kein „Role • “-Prefix
                setTeamPrefix(team, r.color.toString());
            } else {
                String legacy = r.color + rankName + " §8• " + r.color;
                setTeamPrefix(team, legacy);
            }

            teams.put(rankName, team);
        }
    }

    /** Aktualisiert Tab-Eintrag für einen Spieler. */
    public void updatePlayer(Player p) {
        // Nur den sichtbaren TAB-Namen aktualisieren. Das Scoreboard selbst gehört
        // dem Sidebar/Nametag-System, sonst verschwindet die Sidebar nach Level-Ups.
        setPlayerListName(p, perms.getTabName(p));
    }

    /* ================= Helpers: Adventure zuerst, dann Fallback ================= */

    private void setTeamPrefix(Team team, String legacyPrefix) {
        Component comp = LEGACY.deserialize(legacyPrefix);
        // Versuche neue API: prefix(Component)
        try {
            Team.class.getMethod("prefix", Component.class).invoke(team, comp);
            return;
        } catch (Throwable ignored) { }
        // Fallback: alte API setPrefix(String)
        try { team.setPrefix(legacyPrefix); } catch (Throwable ignored) { }
    }

    private void trySetTeamColor(Team team, ChatColor chatColor) {
        // Neue API: color(NamedTextColor)
        try {
            NamedTextColor nc = map(chatColor);
            if (nc != null) {
                Team.class.getMethod("color", NamedTextColor.class).invoke(team, nc);
                return;
            }
        } catch (Throwable ignored) { }
        // Fallback: alte API setColor(ChatColor)
        try { team.setColor(chatColor); } catch (Throwable ignored) { }
    }

    private void setPlayerListName(Player p, Component comp) {
        // Neue Paper-API: playerListName(Component)
        try {
            Player.class.getMethod("playerListName", Component.class).invoke(p, comp);
            return;
        } catch (Throwable ignored) { }
        // Fallback: alte API setPlayerListName(String)
        try {
            String legacy = LEGACY.serialize(comp);
            p.setPlayerListName(legacy);
        } catch (Throwable ignored) { }
    }

    private NamedTextColor map(ChatColor c) {
        if (c == null) return null;
        switch (c) {
            case BLACK: return NamedTextColor.BLACK;
            case DARK_BLUE: return NamedTextColor.DARK_BLUE;
            case DARK_GREEN: return NamedTextColor.DARK_GREEN;
            case DARK_AQUA: return NamedTextColor.DARK_AQUA;
            case DARK_RED: return NamedTextColor.DARK_RED;
            case DARK_PURPLE: return NamedTextColor.DARK_PURPLE;
            case GOLD: return NamedTextColor.GOLD;
            case GRAY: return NamedTextColor.GRAY;
            case DARK_GRAY: return NamedTextColor.DARK_GRAY;
            case BLUE: return NamedTextColor.BLUE;
            case GREEN: return NamedTextColor.GREEN;
            case AQUA: return NamedTextColor.AQUA;
            case RED: return NamedTextColor.RED;
            case LIGHT_PURPLE: return NamedTextColor.LIGHT_PURPLE;
            case YELLOW: return NamedTextColor.YELLOW;
            case WHITE: return NamedTextColor.WHITE;
            default: return null;
        }
    }
}
