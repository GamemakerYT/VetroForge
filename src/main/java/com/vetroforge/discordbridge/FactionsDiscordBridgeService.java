package com.vetroforge.discordbridge;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.*;
import java.util.UUID;

public final class FactionsDiscordBridgeService {

    private final JavaPlugin plugin;

    private final String host;
    private final int port;
    private final String database;
    private final String user;
    private final String password;

    private final String serverName;

    public FactionsDiscordBridgeService(JavaPlugin plugin) {
        this.plugin = plugin;

        this.host = firstString("discord-bridge.mysql.host", "mysql.host", "127.0.0.1");
        this.port = firstInt(3306, "discord-bridge.mysql.port", "mysql.port");
        this.database = firstString("discord-bridge.mysql.database", "mysql.database", "VF");
        this.user = firstString("discord-bridge.mysql.user", "mysql.user", "VF");
        String configuredPassword = firstString("discord-bridge.mysql.password", "mysql.password", "");
        this.password = configuredPassword == null || configuredPassword.isBlank()
                ? System.getenv().getOrDefault("VF_MYSQL_PASSWORD", "")
                : configuredPassword;

        this.serverName = plugin.getConfig().getString("discord-bridge.server-name", "Factions");
    }

    public void initTables() {
        runAsync(() -> {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS server_status (
                        server_name VARCHAR(64) PRIMARY KEY,
                        online TINYINT(1) NOT NULL DEFAULT 0,
                        players_online INT NOT NULL DEFAULT 0,
                        players_max INT NOT NULL DEFAULT 0,
                        tps DOUBLE NOT NULL DEFAULT 20.0,
                        maintenance TINYINT(1) NOT NULL DEFAULT 0,
                        status_text VARCHAR(255) DEFAULT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS bot_alerts (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        alert_type VARCHAR(32) NOT NULL,
                        title VARCHAR(128) NOT NULL,
                        message TEXT NOT NULL,
                        server_name VARCHAR(64) DEFAULT NULL,
                        faction_name VARCHAR(64) DEFAULT NULL,
                        x INT DEFAULT NULL,
                        y INT DEFAULT NULL,
                        z INT DEFAULT NULL,
                        handled TINYINT(1) NOT NULL DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_profiles (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        minecraft_uuid VARCHAR(36) NOT NULL UNIQUE,
                        minecraft_name VARCHAR(32) NOT NULL,
                        rank_name VARCHAR(32) NOT NULL DEFAULT 'member',
                        faction_name VARCHAR(64) DEFAULT NULL,
                        faction_role VARCHAR(32) DEFAULT NULL,
                        kills INT NOT NULL DEFAULT 0,
                        deaths INT NOT NULL DEFAULT 0,
                        raid_wins INT NOT NULL DEFAULT 0,
                        raid_losses INT NOT NULL DEFAULT 0,
                        crystals INT NOT NULL DEFAULT 0,
                        vetros BIGINT NOT NULL DEFAULT 0,
                        playtime_minutes BIGINT NOT NULL DEFAULT 0,
                        last_seen TIMESTAMP NULL DEFAULT NULL,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);

                statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS factions (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        faction_name VARCHAR(64) NOT NULL UNIQUE,
                        leader_uuid VARCHAR(36) DEFAULT NULL,
                        leader_name VARCHAR(32) DEFAULT NULL,
                        elo INT NOT NULL DEFAULT 0,
                        crystals INT NOT NULL DEFAULT 0,
                        raid_wins INT NOT NULL DEFAULT 0,
                        raid_losses INT NOT NULL DEFAULT 0,
                        kills INT NOT NULL DEFAULT 0,
                        deaths INT NOT NULL DEFAULT 0,
                        created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;
                """);
            } catch (SQLException exception) {
                plugin.getLogger().severe("[DiscordBridge] Could not initialize tables: " + exception.getMessage());
            }
        });
    }

    public void updateServerStatus(boolean online, int playersOnline, int playersMax, double tps, boolean maintenance, String statusText) {
        runAsync(() -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO server_status
                    (server_name, online, players_online, players_max, tps, maintenance, status_text)
                    VALUES (?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        online = VALUES(online),
                        players_online = VALUES(players_online),
                        players_max = VALUES(players_max),
                        tps = VALUES(tps),
                        maintenance = VALUES(maintenance),
                        status_text = VALUES(status_text),
                        updated_at = CURRENT_TIMESTAMP
                """)) {

                statement.setString(1, serverName);
                statement.setBoolean(2, online);
                statement.setInt(3, playersOnline);
                statement.setInt(4, playersMax);
                statement.setDouble(5, tps);
                statement.setBoolean(6, maintenance);
                statement.setString(7, statusText);
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().warning("[DiscordBridge] Status update failed: " + exception.getMessage());
            }
        });
    }

    public void sendWarzoneLootdropAlert(Location location) {
        insertAlert(
                "warzone_lootdrop",
                "Lootdrop Spawned",
                "A rare lootdrop has spawned in the Warzone.",
                null,
                location
        );
    }

    public void sendWarzoneBossAlert(Location location) {
        insertAlert(
                "warzone_boss",
                "Warzone Boss Spawned",
                "A powerful boss has entered the Warzone.",
                null,
                location
        );
    }

    public void sendRaidStartedAlert(String factionName) {
        insertAlert(
                "raid_started",
                "Raid Started",
                factionName + " has started a raid.",
                factionName,
                null
        );
    }

    public void sendRaidEndedAlert(String factionName) {
        insertAlert(
                "raid_ended",
                "Raid Ended",
                factionName + " has ended a raid.",
                factionName,
                null
        );
    }

    public void insertAlert(String alertType, String title, String message, String factionName, Location location) {
        runAsync(() -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO bot_alerts
                    (alert_type, title, message, server_name, faction_name, x, y, z, handled)
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, 0)
                """)) {

                statement.setString(1, alertType);
                statement.setString(2, title);
                statement.setString(3, message);
                statement.setString(4, serverName);
                statement.setString(5, factionName);

                if (location != null) {
                    statement.setInt(6, location.getBlockX());
                    statement.setInt(7, location.getBlockY());
                    statement.setInt(8, location.getBlockZ());
                } else {
                    statement.setNull(6, Types.INTEGER);
                    statement.setNull(7, Types.INTEGER);
                    statement.setNull(8, Types.INTEGER);
                }

                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().warning("[DiscordBridge] Alert insert failed: " + exception.getMessage());
            }
        });
    }

    public void upsertPlayerProfile(PlayerProfileData data) {
        runAsync(() -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO player_profiles
                    (
                        minecraft_uuid,
                        minecraft_name,
                        rank_name,
                        faction_name,
                        faction_role,
                        kills,
                        deaths,
                        raid_wins,
                        raid_losses,
                        crystals,
                        vetros,
                        playtime_minutes,
                        last_seen
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, CURRENT_TIMESTAMP)
                    ON DUPLICATE KEY UPDATE
                        minecraft_name = VALUES(minecraft_name),
                        rank_name = VALUES(rank_name),
                        faction_name = VALUES(faction_name),
                        faction_role = VALUES(faction_role),
                        kills = VALUES(kills),
                        deaths = VALUES(deaths),
                        raid_wins = VALUES(raid_wins),
                        raid_losses = VALUES(raid_losses),
                        crystals = VALUES(crystals),
                        vetros = VALUES(vetros),
                        playtime_minutes = VALUES(playtime_minutes),
                        last_seen = CURRENT_TIMESTAMP
                """)) {

                statement.setString(1, data.minecraftUuid().toString());
                statement.setString(2, data.minecraftName());
                statement.setString(3, data.rankName());
                statement.setString(4, data.factionName());
                statement.setString(5, data.factionRole());
                statement.setInt(6, data.kills());
                statement.setInt(7, data.deaths());
                statement.setInt(8, data.raidWins());
                statement.setInt(9, data.raidLosses());
                statement.setInt(10, data.crystals());
                statement.setLong(11, data.vetros());
                statement.setLong(12, data.playtimeMinutes());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().warning("[DiscordBridge] Profile upsert failed: " + exception.getMessage());
            }
        });
    }

    public void upsertFaction(FactionData data) {
        runAsync(() -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO factions
                    (
                        faction_name,
                        leader_uuid,
                        leader_name,
                        elo,
                        crystals,
                        raid_wins,
                        raid_losses,
                        kills,
                        deaths
                    )
                    VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?)
                    ON DUPLICATE KEY UPDATE
                        leader_uuid = VALUES(leader_uuid),
                        leader_name = VALUES(leader_name),
                        elo = VALUES(elo),
                        crystals = VALUES(crystals),
                        raid_wins = VALUES(raid_wins),
                        raid_losses = VALUES(raid_losses),
                        kills = VALUES(kills),
                        deaths = VALUES(deaths)
                """)) {

                statement.setString(1, data.factionName());
                statement.setString(2, data.leaderUuid() == null ? null : data.leaderUuid().toString());
                statement.setString(3, data.leaderName());
                statement.setInt(4, data.elo());
                statement.setInt(5, data.crystals());
                statement.setInt(6, data.raidWins());
                statement.setInt(7, data.raidLosses());
                statement.setInt(8, data.kills());
                statement.setInt(9, data.deaths());
                statement.executeUpdate();
            } catch (SQLException exception) {
                plugin.getLogger().warning("[DiscordBridge] Faction upsert failed: " + exception.getMessage());
            }
        });
    }

    public void shutdown() {
        updateServerStatusDirect(
                false,
                0,
                plugin.getConfig().getInt("discord-bridge.status.max-players", 500),
                0.0,
                false,
                "Factions offline"
        );
    }

    private void updateServerStatusDirect(boolean online, int playersOnline, int playersMax, double tps, boolean maintenance, String statusText) {
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("""
            INSERT INTO server_status
            (server_name, online, players_online, players_max, tps, maintenance, status_text)
            VALUES (?, ?, ?, ?, ?, ?, ?)
            ON DUPLICATE KEY UPDATE
                online = VALUES(online),
                players_online = VALUES(players_online),
                players_max = VALUES(players_max),
                tps = VALUES(tps),
                maintenance = VALUES(maintenance),
                status_text = VALUES(status_text),
                updated_at = CURRENT_TIMESTAMP
        """)) {

            statement.setString(1, serverName);
            statement.setBoolean(2, online);
            statement.setInt(3, playersOnline);
            statement.setInt(4, playersMax);
            statement.setDouble(5, tps);
            statement.setBoolean(6, maintenance);
            statement.setString(7, statusText);
            statement.executeUpdate();
        } catch (SQLException exception) {
            plugin.getLogger().warning("[DiscordBridge] Direct shutdown status update failed: " + exception.getMessage());
        }
    }

    public String serverName() {
        return serverName;
    }

    private Connection connection() throws SQLException {
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useUnicode=true&characterEncoding=utf8"
                + "&useSSL=false"
                + "&serverTimezone=UTC"
                + "&allowPublicKeyRetrieval=true";

        return DriverManager.getConnection(url, user, password);
    }

    private String firstString(String primary, String fallback, String def) {
        String value = plugin.getConfig().getString(primary);
        if (value != null && !value.isBlank()) return value;
        value = plugin.getConfig().getString(fallback);
        return value == null || value.isBlank() ? def : value;
    }

    private int firstInt(int def, String primary, String fallback) {
        if (plugin.getConfig().contains(primary)) return plugin.getConfig().getInt(primary, def);
        return plugin.getConfig().getInt(fallback, def);
    }

    private void runAsync(Runnable runnable) {
        if (!plugin.isEnabled()) {
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, runnable);
    }

    public record PlayerProfileData(
            UUID minecraftUuid,
            String minecraftName,
            String rankName,
            String factionName,
            String factionRole,
            int kills,
            int deaths,
            int raidWins,
            int raidLosses,
            int crystals,
            long vetros,
            long playtimeMinutes
    ) {
    }

    public record FactionData(
            String factionName,
            UUID leaderUuid,
            String leaderName,
            int elo,
            int crystals,
            int raidWins,
            int raidLosses,
            int kills,
            int deaths
    ) {
    }
}
