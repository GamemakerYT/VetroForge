// src/main/java/com/vetroforge/persistence/sql/NetworkDao.java
package com.vetroforge.persistence.sql;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.*;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class NetworkDao {

    private final HikariDataSource ds;

    public NetworkDao(String host, int port, String db, String user, String pass) {
        HikariConfig cfg = new HikariConfig();
        cfg.setJdbcUrl("jdbc:mysql://" + host + ":" + port + "/" + db + "?useSSL=false&autoReconnect=true");
        cfg.setUsername(user);
        cfg.setPassword(pass);
        cfg.setMaximumPoolSize(10);
        cfg.setMinimumIdle(2);
        cfg.setPoolName("VetroForge-MySQL");

        this.ds = new HikariDataSource(cfg);

        init();
    }

    private void init() {
        try (Connection c = ds.getConnection();
             Statement st = c.createStatement()) {

            st.executeUpdate("""
                CREATE TABLE IF NOT EXISTS coins (
                    uuid VARCHAR(36) PRIMARY KEY,
                    coins INT NOT NULL DEFAULT 0
                )
            """);

        } catch (SQLException e) {
            e.printStackTrace();
        }
    }

    public CompletableFuture<Integer> getCoins(UUID uuid) {
        return CompletableFuture.supplyAsync(() -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(
                         "SELECT coins FROM coins WHERE uuid=?")) {

                ps.setString(1, uuid.toString());
                ResultSet rs = ps.executeQuery();

                if (rs.next()) return rs.getInt("coins");

                // neu anlegen
                try (PreparedStatement insert = c.prepareStatement(
                        "INSERT INTO coins(uuid, coins) VALUES(?,0)")) {
                    insert.setString(1, uuid.toString());
                    insert.executeUpdate();
                }

                return 0;

            } catch (SQLException e) {
                e.printStackTrace();
                return 0;
            }
        });
    }

    public void setCoins(UUID uuid, int amount) {
        execute("REPLACE INTO coins(uuid, coins) VALUES(?,?)", uuid, amount);
    }

    public void addCoins(UUID uuid, int amount) {
        execute("INSERT INTO coins(uuid, coins) VALUES(?,?) ON DUPLICATE KEY UPDATE coins = coins + ?", uuid, amount, amount);
    }

    public void removeCoins(UUID uuid, int amount) {
        execute("UPDATE coins SET coins = GREATEST(0, coins - ?) WHERE uuid=?", amount, uuid);
    }

    private void execute(String sql, Object... params) {
        CompletableFuture.runAsync(() -> {
            try (Connection c = ds.getConnection();
                 PreparedStatement ps = c.prepareStatement(sql)) {

                for (int i = 0; i < params.length; i++) {
                    Object o = params[i];
                    if (o instanceof UUID u) ps.setString(i + 1, u.toString());
                    else if (o instanceof Integer n) ps.setInt(i + 1, n);
                    else ps.setObject(i + 1, o);
                }

                ps.executeUpdate();

            } catch (SQLException e) {
                e.printStackTrace();
            }
        });
    }
}