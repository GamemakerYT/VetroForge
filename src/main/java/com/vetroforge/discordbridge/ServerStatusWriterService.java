// Für Factions:
// src/main/java/com/vetroforge/discordbridge/ServerStatusWriterService.java
package com.vetroforge.discordbridge;

import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.util.Locale;

public final class ServerStatusWriterService {

    private final JavaPlugin plugin;
    private final String serverName;

    private int taskId = -1;

    public ServerStatusWriterService(JavaPlugin plugin, String serverName) {
        this.plugin = plugin;
        this.serverName = normalize(serverName);
    }

    public void start() {
        stop();

        createTable();
        updateNow(true);

        taskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                () -> updateNow(true),
                20L * 5L,
                20L * 10L
        ).getTaskId();

        plugin.getLogger().info("[ServerStatus] Writer started for " + serverName);
    }

    public void stop() {
        if (taskId != -1) {
            Bukkit.getScheduler().cancelTask(taskId);
            taskId = -1;
        }

        updateNow(false);
    }

    public void updateNow(boolean online) {
        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        INSERT INTO server_status
                        (server_name, online, players, max_players, tps, maintenance)
                        VALUES (?, ?, ?, ?, ?, COALESCE((SELECT s.maintenance FROM server_status s WHERE s.server_name = ?), 0))
                        ON DUPLICATE KEY UPDATE
                            online = VALUES(online),
                            players = VALUES(players),
                            max_players = VALUES(max_players),
                            tps = VALUES(tps),
                            updated_at = CURRENT_TIMESTAMP
                        """)
        ) {
            statement.setString(1, serverName);
            statement.setBoolean(2, online);
            statement.setInt(3, online ? Bukkit.getOnlinePlayers().size() : 0);
            statement.setInt(4, Bukkit.getMaxPlayers());
            statement.setDouble(5, online ? currentTps() : 0.0D);
            statement.setString(6, serverName);

            statement.executeUpdate();
        } catch (Exception exception) {
            plugin.getLogger().warning("[ServerStatus] Update failed: " + exception.getMessage());
        }
    }

    private void createTable() {
        try (
                Connection connection = openConnection();
                PreparedStatement statement = connection.prepareStatement("""
                        CREATE TABLE IF NOT EXISTS server_status (
                            server_name VARCHAR(64) PRIMARY KEY,
                            online TINYINT(1) NOT NULL DEFAULT 0,
                            players INT NOT NULL DEFAULT 0,
                            max_players INT NOT NULL DEFAULT 100,
                            tps DOUBLE NOT NULL DEFAULT 20.0,
                            maintenance TINYINT(1) NOT NULL DEFAULT 0,
                            updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """)
        ) {
            statement.executeUpdate();
        } catch (Exception exception) {
            plugin.getLogger().warning("[ServerStatus] Table init failed: " + exception.getMessage());
        }
    }

    private double currentTps() {
        try {
            double[] tps = Bukkit.getTPS();

            if (tps.length == 0) {
                return 20.0D;
            }

            return Math.min(20.0D, Math.max(0.0D, tps[0]));
        } catch (Throwable ignored) {
            return 20.0D;
        }
    }

    private Connection openConnection() throws Exception {
        FileConfiguration config = plugin.getConfig();

        String host = first(config,
                "mysql.host",
                "database.host",
                "discord-bridge.mysql.host",
                "discord.mysql.host"
        );

        int port = firstInt(config, 3306,
                "mysql.port",
                "database.port",
                "discord-bridge.mysql.port",
                "discord.mysql.port"
        );

        String database = first(config,
                "mysql.database",
                "database.name",
                "database.database",
                "discord-bridge.mysql.database",
                "discord.mysql.database"
        );

        String user = first(config,
                "mysql.user",
                "database.user",
                "discord-bridge.mysql.user",
                "discord.mysql.user"
        );

        String password = first(config,
                "mysql.password",
                "database.password",
                "discord-bridge.mysql.password",
                "discord.mysql.password"
        );
        if (password == null || password.isBlank()) {
            password = System.getenv("VF_MYSQL_PASSWORD");
        }

        if (host == null || database == null || user == null) {
            throw new IllegalStateException("MySQL config missing.");
        }

        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false"
                + "&allowPublicKeyRetrieval=true"
                + "&serverTimezone=UTC"
                + "&characterEncoding=utf8"
                + "&useUnicode=true";

        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }

    private String first(FileConfiguration config, String... paths) {
        for (String path : paths) {
            if (!config.contains(path)) {
                continue;
            }

            String value = config.getString(path);

            if (value != null && !value.isBlank()) {
                return value;
            }
        }

        return null;
    }

    private int firstInt(FileConfiguration config, int fallback, String... paths) {
        for (String path : paths) {
            if (config.contains(path)) {
                return config.getInt(path, fallback);
            }
        }

        return fallback;
    }

    private String normalize(String input) {
        if (input == null || input.isBlank()) {
            return "unknown";
        }

        return input.toLowerCase(Locale.ROOT);
    }
}
