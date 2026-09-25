package com.vetroforge.leaderboard;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.stats.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LeaderboardService {

    public enum Board {
        WEEKLY_FACTIONS("weekly-factions", "§6§lWeekly Top Factions", true, "Wochenpunkte"),
        WEEKLY_PLAYERS("weekly-players", "§e§lWeekly Top Spieler", false, "Aktivitäts-Score"),
        SEASON_FACTIONS("season-factions", "§c§lSeason Top Factions", true, "Fraktionsmacht"),
        SEASON_PLAYERS("season-players", "§4§lSeason Top Spieler", false, "PvP-Elo");

        public final String id;
        public final String title;
        public final boolean faction;
        public final String valueLabel;

        Board(String id, String title, boolean faction, String valueLabel) {
            this.id = id;
            this.title = title;
            this.faction = faction;
            this.valueLabel = valueLabel;
        }

        public static Board parse(String raw) {
            if (raw == null || raw.isBlank()) return SEASON_PLAYERS;
            String normalized = raw.toLowerCase(Locale.ROOT).replace("_", "-");
            for (Board board : values()) {
                if (board.id.equals(normalized) || board.name().toLowerCase(Locale.ROOT).replace("_", "-").equals(normalized)) {
                    return board;
                }
            }
            return switch (normalized) {
                case "weekly", "week", "wplayers", "weeklyplayers" -> WEEKLY_PLAYERS;
                case "wfactions", "weeklyfactions" -> WEEKLY_FACTIONS;
                case "season", "players", "top", "seasonplayers", "splayers" -> SEASON_PLAYERS;
                case "factions", "seasonfactions", "sfactions" -> SEASON_FACTIONS;
                default -> SEASON_PLAYERS;
            };
        }
    }

    public record Entry(String id, String name, int value, UUID playerUuid, boolean faction) {}

    private final FactionsPlugin plugin;

    public LeaderboardService(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public List<Entry> top(Board board, int limit) {
        return switch (board) {
            case WEEKLY_FACTIONS -> weeklyFactions(limit);
            case WEEKLY_PLAYERS -> weeklyPlayers(limit);
            case SEASON_FACTIONS -> seasonFactions(limit);
            case SEASON_PLAYERS -> seasonPlayers(limit);
        };
    }

    // NOTE (adapted from factions-repair): the original implementation sourced weekly-faction
    // progress from FactionWeeklyQuestService.topFactionsByWeeklyProgress(limit). The current
    // codebase's FactionWeeklyQuestService does not expose that method (API drift between the
    // two codebases), so this falls back to "no weekly-faction data" rather than guessing at a
    // replacement aggregation. Season/weekly-player boards below are unaffected.
    private List<Entry> weeklyFactions(int limit) {
        return List.of();
    }

    private List<Entry> seasonFactions(int limit) {
        if (plugin.getFactionManager() == null) return List.of();
        List<Entry> entries = new ArrayList<>();
        for (String faction : plugin.getFactionManager().getAllFactionNames()) {
            int elo = plugin.getFactionUpgradeManager() == null ? 0 : plugin.getFactionUpgradeManager().getElo(faction);
            entries.add(new Entry(faction, faction, elo, null, true));
        }
        entries.sort(Comparator.comparingInt(Entry::value).reversed().thenComparing(Entry::name));
        return entries.stream().limit(Math.max(1, limit)).toList();
    }

    private List<Entry> seasonPlayers(int limit) {
        StatsManager stats = plugin.getStatsManager();
        if (stats == null) return List.of();
        return stats.getKnownPlayers().stream()
                .map(uuid -> new Entry(uuid.toString(), playerName(uuid), stats.getElo(uuid), uuid, false))
                .sorted(Comparator.comparingInt(Entry::value).reversed().thenComparing(Entry::name))
                .limit(Math.max(1, limit))
                .toList();
    }

    // NOTE (adapted from factions-repair): the original tried FactionWeeklyQuestService.
    // topPlayersByWeeklyProgress(limit) first and only fell back to StatsManager-derived scoring
    // when that came back empty. That method doesn't exist on the current codebase's
    // FactionWeeklyQuestService (same API drift as weeklyFactions above), so this goes straight
    // to the StatsManager-derived fallback, which is otherwise identical to the original.
    private List<Entry> weeklyPlayers(int limit) {
        StatsManager stats = plugin.getStatsManager();
        if (stats == null) return List.of();
        return stats.getKnownPlayers().stream()
                .map(uuid -> {
                    int score = stats.getStat(uuid, "weeklyScore");
                    if (score <= 0) {
                        long onlineMinutes = Math.max(0L, stats.getOnlineSeconds(uuid) / 60L);
                        score = (int) Math.min(Integer.MAX_VALUE,
                                stats.getKills(uuid) * 12L + stats.getStreak(uuid) * 8L + onlineMinutes);
                    }
                    return new Entry(uuid.toString(), playerName(uuid), score, uuid, false);
                })
                .sorted(Comparator.comparingInt(Entry::value).reversed().thenComparing(Entry::name))
                .limit(Math.max(1, limit))
                .toList();
    }

    private String playerName(UUID uuid) {
        OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
        return player.getName() == null ? uuid.toString().substring(0, 8) : player.getName();
    }
}
