// src/main/java/com/vetroforge/web/WebApiManager.java
package com.vetroforge.web;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.factions.FactionManager;
import com.vetroforge.raid.RaidManager;
import com.vetroforge.raid.RaidCrystalManager;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import redis.clients.jedis.Jedis;

import java.io.*;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.sql.*;
import java.util.*;
import java.util.concurrent.*;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class WebApiManager {

    private final FactionsPlugin plugin;
    private HttpServer server;
    private ExecutorService serverExecutor;
    private String bind;
    private int port;
    private String apiKey;                 // NEW
    private boolean authEnabled;           // NEW
    private boolean webMysqlReady;
    private String webMysqlError = "";
    private volatile long lastStatusAlertAt;
    private volatile String lastStatusAlertKey = "";
    private int statusAlertTaskId = -1;
    private volatile long apiStartedAtMillis;

    public WebApiManager(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    /* ================= Lifecycle ================= */

    public boolean start() {
        try {
            boolean enabled = plugin.getConfig().getBoolean("webapi.enabled", true);
            if (!enabled) { plugin.getLogger().info("[WebAPI] disabled via config."); return false; }

            this.bind = plugin.getConfig().getString("webapi.bind", "45.146.252.240");
            this.port = plugin.getConfig().getInt("webapi.port", 8500);
            this.apiKey = plugin.getConfig().getString("webapi.apiKey", "5475698").trim();
            this.authEnabled = !apiKey.isEmpty();
            initWebMysql();

            server = HttpServer.create(new InetSocketAddress(bind, port), 0);

            // Public GET
            server.createContext("/",                    this::handleRoot);
            server.createContext("/api/health",          this::handleHealth);
            server.createContext("/api/web/status",      this::handleWebStatus);
            server.createContext("/api/player/hud",      this::handlePlayerHud);
            server.createContext("/api/leaderboards",    this::handleLeaderboards);
            server.createContext("/api/faction",         this::handleFaction);
            server.createContext("/api/profile",         this::handleProfile);
            server.createContext("/api/faction/profile", this::handleFactionProfile);
            server.createContext("/api/auth/register",   this::handleAuthRegister);
            server.createContext("/api/auth/login",      this::handleAuthLogin);
            server.createContext("/api/auth/me",         this::handleAuthMe);
            server.createContext("/api/auth/link-code",  this::handleAuthLinkCode);
            server.createContext("/api/auth/reset/request", this::handleAuthResetRequest);
            server.createContext("/api/auth/reset/confirm", this::handleAuthResetConfirm);
            server.createContext("/api/events/next",     this::handleEventsNext);
            server.createContext("/api/warzone/live",    this::handleWarzoneLive);
            server.createContext("/api/raid/status",     this::handleRaidStatus);
            server.createContext("/api/lootdrop/next",   this::handleLootdropNext);
            server.createContext("/api/contracts/active",this::handleContractsActive);
            server.createContext("/api/market/overview", this::handleMarketOverview);
            server.createContext("/api/admin/auction/risk", this::handleAdminAuctionRisk);
            server.createContext("/api/admin/auction/quarantine", this::handleAdminAuctionQuarantine);
            server.createContext("/api/admin/auction/action", this::handleAdminAuctionAction);
            server.createContext("/api/admin/auction/seller", this::handleAdminAuctionSeller);
            server.createContext("/api/admin/shop/editor", this::handleAdminShopEditor);
            server.createContext("/api/admin/shop/action", this::handleAdminShopAction);
            server.createContext("/api/admin/raidshop/editor", this::handleAdminRaidShopEditor);
            server.createContext("/api/admin/raidshop/audit", this::handleAdminRaidShopAudit);
            server.createContext("/api/admin/raidshop/action", this::handleAdminRaidShopAction);
            server.createContext("/api/admin/dashboard/summary", this::handleAdminDashboardSummary);
            server.createContext("/api/admin/users", this::handleAdminUsers);
            server.createContext("/api/admin/players/recent", this::handleAdminPlayersRecent);
            server.createContext("/api/admin/players/rank", this::handleAdminPlayerRank);
            server.createContext("/api/admin/punishments/history", this::handleAdminPunishmentHistory);
            server.createContext("/api/appeals/submit",  this::handleAppealSubmit);
            server.createContext("/api/appeals/mine",    this::handleAppealsMine);
            server.createContext("/api/appeals/queue",   this::handleAppealsQueue);
            server.createContext("/api/appeals/action",  this::handleAppealAction);
            server.createContext("/api/mysticstorm",     this::handleMysticStorm);

            // SSE stream (GET, aber sinnvollerweise auth-geschützt)
            server.createContext("/api/stream/warzone",  this::handleStreamWarzone);

            // Admin (POST, Auth required)
            server.createContext("/api/admin/broadcast", this::handleAdminBroadcast);
            server.createContext("/api/admin/lootdrop/flare", this::handleAdminFlare);

            serverExecutor = Executors.newCachedThreadPool();
            server.setExecutor(serverExecutor);
            server.start();
            apiStartedAtMillis = System.currentTimeMillis();
            startStatusAlertMonitor();

            plugin.getLogger().info("[VetroForge] WebAPI running at http://" + bind + ":" + port);
            if (authEnabled) plugin.getLogger().info("[WebAPI] API-Key auth enabled");
            else plugin.getLogger().warning("[WebAPI] No API key set (auth disabled) — set webapi.apiKey !");
            return true;
        } catch (Throwable t) {
            plugin.getLogger().warning("[WebAPI] start failed: " + t.getMessage());
            return false;
        }
    }

    public void stop() {
        if (statusAlertTaskId != -1) {
            Bukkit.getScheduler().cancelTask(statusAlertTaskId);
            statusAlertTaskId = -1;
        }
        try { if (server != null) server.stop(0); } catch (Throwable ignored) {}
        server = null;
        try { if (serverExecutor != null) serverExecutor.shutdownNow(); } catch (Throwable ignored) {}
        serverExecutor = null;
    }

    /* ================= Helpers ================= */

    private static Map<String, String> query(HttpExchange ex) {
        Map<String, String> out = new HashMap<>();
        String q = ex.getRequestURI().getRawQuery();
        if (q == null || q.isEmpty()) return out;
        for (String part : q.split("&")) {
            int i = part.indexOf('=');
            if (i < 0) out.put(urld(part), "");
            else out.put(urld(part.substring(0, i)), urld(part.substring(1 + i)));
        }
        return out;
    }

    private static String urld(String s) {
        try { return java.net.URLDecoder.decode(s, StandardCharsets.UTF_8); }
        catch (Exception e) { return s; }
    }

    private void addCors(HttpExchange ex) {
        ex.getResponseHeaders().add("Access-Control-Allow-Origin", "*");
        ex.getResponseHeaders().add("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
        ex.getResponseHeaders().add("Access-Control-Allow-Headers", "Content-Type, X-API-Key, X-Session-Token, Authorization");
        ex.getResponseHeaders().add("Content-Type", "application/json; charset=utf-8");
        ex.getResponseHeaders().add("Cache-Control", "no-store");
    }

    private static void send(HttpExchange ex, int code, String body) {
        try (OutputStream os = ex.getResponseBody()) {
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            ex.sendResponseHeaders(code, bytes.length);
            os.write(bytes);
        } catch (Throwable ignored) {}
    }

    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (char c : s.toCharArray()) {
            switch (c) {
                case '\\' -> b.append("\\\\");
                case '"'  -> b.append("\\\"");
                case '\n' -> b.append("\\n");
                case '\r' -> b.append("\\r");
                case '\t' -> b.append("\\t");
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    private static int asInt(Map<String,String> q, String key, int def) {
        try { return Integer.parseInt(q.getOrDefault(key, String.valueOf(def))); }
        catch (Exception e){ return def; }
    }

    private boolean checkAuth(HttpExchange ex) {
        if (!authEnabled) return true;
        String got = ex.getRequestHeaders().getFirst("X-API-Key");
        return apiKey.equals(got);
    }

    private static String readBody(HttpExchange ex) {
        try (InputStream in = ex.getRequestBody()) {
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) { return ""; }
    }

    private static String jsonGet(String body, String key) {
        // ultralight parser: sucht "key":"value" oder "key":value
        try {
            String k = "\"" + key + "\"";
            int i = body.indexOf(k);
            if (i < 0) return null;
            int c = body.indexOf(':', i + k.length());
            if (c < 0) return null;
            int j = c + 1;
            while (j < body.length() && Character.isWhitespace(body.charAt(j))) j++;
            if (j >= body.length()) return null;
            char ch = body.charAt(j);
            if (ch == '"') {
                int end = body.indexOf('"', j + 1);
                if (end > j) return body.substring(j + 1, end);
            } else {
                int end = j;
                while (end < body.length() && ",}] \n\r\t".indexOf(body.charAt(end)) < 0) end++;
                return body.substring(j, end);
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private static String value(Map<String,String> q, String body, String key) {
        String fromQuery = q.get(key);
        return fromQuery != null ? fromQuery : jsonGet(body, key);
    }

    private String safeUser(String raw) {
        if (raw == null) return "";
        return raw.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-.]", "");
    }

    private YamlConfiguration webUsers() {
        File file = new File(plugin.getDataFolder(), "data/web-users.yml");
        return YamlConfiguration.loadConfiguration(file);
    }

    private void saveWebUsers(YamlConfiguration users) {
        try {
            File file = new File(plugin.getDataFolder(), "data/web-users.yml");
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            users.save(file);
        } catch (IOException ignored) {}
    }

    private void initWebMysql() {
        webMysqlReady = false;
        webMysqlError = "";
        if (!plugin.getConfig().getBoolean("mysql.enabled", false)) {
            webMysqlError = "mysql.enabled=false";
            return;
        }
        try (Connection connection = webConnection(); Statement st = connection.createStatement()) {
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS web_users (
                        username VARCHAR(64) PRIMARY KEY,
                        password_hash CHAR(64) NOT NULL,
                        salt VARCHAR(64) NOT NULL,
                        minecraft VARCHAR(32) NOT NULL DEFAULT '',
                        uuid VARCHAR(36) NOT NULL DEFAULT '',
                        role VARCHAR(16) NOT NULL DEFAULT 'player',
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS account_link_codes (
                        code VARCHAR(16) PRIMARY KEY,
                        username VARCHAR(64) NOT NULL,
                        expires_at BIGINT NOT NULL,
                        created_at BIGINT NOT NULL,
                        INDEX idx_account_link_username(username)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS account_reset_codes (
                        code VARCHAR(64) PRIMARY KEY,
                        username VARCHAR(64) NOT NULL,
                        expires_at BIGINT NOT NULL,
                        used TINYINT(1) NOT NULL DEFAULT 0,
                        created_at BIGINT NOT NULL,
                        INDEX idx_account_reset_username(username)
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS player_cache (
                        uuid VARCHAR(36) PRIMARY KEY,
                        name VARCHAR(32) NOT NULL DEFAULT '',
                        faction VARCHAR(64) NOT NULL DEFAULT '',
                        role VARCHAR(16) NOT NULL DEFAULT 'player',
                        coins INT NOT NULL DEFAULT 0,
                        last_seen BIGINT NOT NULL
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS raid_shop_offers (
                        id VARCHAR(64) PRIMARY KEY,
                        category VARCHAR(32) NOT NULL DEFAULT '',
                        material VARCHAR(64) NOT NULL DEFAULT '',
                        amount INT NOT NULL DEFAULT 1,
                        reward_type VARCHAR(32) NOT NULL DEFAULT 'ITEM',
                        reward_value TEXT NULL,
                        price INT NOT NULL DEFAULT 1,
                        enabled TINYINT(1) NOT NULL DEFAULT 1,
                        updated_at BIGINT NOT NULL
                    )
                    """);
            st.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS ban_appeals (
                        id INT AUTO_INCREMENT PRIMARY KEY,
                        punishment_id INT NOT NULL,
                        uuid VARCHAR(36) NOT NULL,
                        username VARCHAR(64) NOT NULL,
                        message TEXT NOT NULL,
                        status VARCHAR(16) NOT NULL DEFAULT 'open',
                        created_at BIGINT NOT NULL,
                        reviewed_by VARCHAR(64) NULL,
                        reviewed_at BIGINT NULL,
                        review_note TEXT NULL,
                        INDEX idx_ban_appeals_status (status),
                        INDEX idx_ban_appeals_uuid (uuid)
                    )
                    """);
            migrateLegacyWebTables(st);
            webMysqlReady = true;
            plugin.getLogger().info("[WebAPI] MySQL Web tables ready: web_users, account_link_codes, account_reset_codes, player_cache, raid_shop_offers, ban_appeals");
        } catch (Throwable t) {
            webMysqlError = t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
            plugin.getLogger().warning("[WebAPI] MySQL Web tables unavailable, YAML fallback active: " + webMysqlError);
        }
    }

    private void migrateLegacyWebTables(Statement st) {
        trySql(st, "ALTER TABLE web_users ADD COLUMN username VARCHAR(64) NULL");
        trySql(st, "ALTER TABLE web_users ADD COLUMN salt VARCHAR(64) NOT NULL DEFAULT ''");
        trySql(st, "ALTER TABLE web_users ADD COLUMN minecraft VARCHAR(32) NOT NULL DEFAULT ''");
        trySql(st, "ALTER TABLE web_users ADD COLUMN uuid VARCHAR(36) NOT NULL DEFAULT ''");
        trySql(st, "ALTER TABLE web_users ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'player'");
        trySql(st, "ALTER TABLE web_users ADD COLUMN created_at BIGINT NOT NULL DEFAULT 0");
        trySql(st, "ALTER TABLE web_users ADD COLUMN updated_at BIGINT NOT NULL DEFAULT 0");
        trySql(st, "ALTER TABLE web_users MODIFY player_uuid VARCHAR(36) NULL");
        trySql(st, "ALTER TABLE web_users MODIFY player_name VARCHAR(32) NULL");
        trySql(st, "CREATE UNIQUE INDEX idx_web_users_username ON web_users(username)");

        trySql(st, "ALTER TABLE account_link_codes ADD COLUMN username VARCHAR(64) NOT NULL DEFAULT ''");
        trySql(st, "ALTER TABLE account_link_codes ADD COLUMN expires_at BIGINT NOT NULL DEFAULT 0");
        trySql(st, "ALTER TABLE account_link_codes ADD COLUMN created_at BIGINT NOT NULL DEFAULT 0");
        trySql(st, "ALTER TABLE account_link_codes MODIFY target VARCHAR(32) NULL");
        trySql(st, "ALTER TABLE account_link_codes MODIFY used_at BIGINT NULL");
        trySql(st, "ALTER TABLE account_link_codes MODIFY player_uuid VARCHAR(36) NULL");
        trySql(st, "ALTER TABLE account_link_codes MODIFY player_name VARCHAR(32) NULL");

        trySql(st, "ALTER TABLE player_cache ADD COLUMN name VARCHAR(32) NOT NULL DEFAULT ''");
        trySql(st, "ALTER TABLE player_cache ADD COLUMN faction VARCHAR(64) NOT NULL DEFAULT ''");
        trySql(st, "ALTER TABLE player_cache ADD COLUMN role VARCHAR(16) NOT NULL DEFAULT 'player'");
        trySql(st, "ALTER TABLE player_cache ADD COLUMN coins INT NOT NULL DEFAULT 0");
        trySql(st, "ALTER TABLE player_cache ADD COLUMN last_seen BIGINT NOT NULL DEFAULT 0");

        trySql(st, "ALTER TABLE raid_shop_offers ADD COLUMN reward_type VARCHAR(32) NOT NULL DEFAULT 'ITEM'");
        trySql(st, "ALTER TABLE raid_shop_offers ADD COLUMN reward_value TEXT NULL");
    }

    private void trySql(Statement st, String sql) {
        try { st.executeUpdate(sql); } catch (Throwable ignored) {}
    }

    private Connection webConnection() throws SQLException {
        String host = configOrEnv("mysql.host", "VF_MYSQL_HOST", "127.0.0.1");
        int port = plugin.getConfig().getInt("mysql.port", parseInt(System.getenv("VF_MYSQL_PORT"), 3306));
        String database = configOrEnv("mysql.database", "VF_MYSQL_DATABASE", "VF");
        String user = configOrEnv("mysql.user", "VF_MYSQL_USER", "VF");
        String password = configOrEnv("mysql.password", "VF_MYSQL_PASSWORD", "");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC";
        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }

    /** Batch UUID-&gt;name lookup from player_cache, for callers whose Bukkit.getOfflinePlayer()
     *  cache misses (e.g. leaderboards showing offline-mode or long-unseen players). Blocking;
     *  call off the main thread. */
    public Map<UUID, String> lookupCachedNames(Collection<UUID> ids) {
        Map<UUID, String> out = new HashMap<>();
        if (!webMysqlReady || ids == null || ids.isEmpty()) return out;
        String placeholders = String.join(",", Collections.nCopies(ids.size(), "?"));
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement(
                     "SELECT uuid, name FROM player_cache WHERE uuid IN (" + placeholders + ") AND name <> ''")) {
            int i = 1;
            for (UUID id : ids) ps.setString(i++, id.toString());
            try (ResultSet rows = ps.executeQuery()) {
                while (rows.next()) {
                    try { out.put(UUID.fromString(rows.getString(1)), rows.getString(2)); } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}
        return out;
    }

    private String configOrEnv(String path, String env, String fallback) {
        String configured = plugin.getConfig().getString(path, "");
        if (configured != null && !configured.isBlank()) return configured;
        String value = System.getenv(env);
        return value == null || value.isBlank() ? fallback : value;
    }

    private int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw); } catch (Throwable ignored) { return fallback; }
    }

    private long parseLong(String raw, long fallback) {
        try { return Long.parseLong(raw); } catch (Throwable ignored) { return fallback; }
    }

    private String orDefault(String raw, String fallback) {
        return raw == null || raw.isBlank() ? fallback : raw;
    }

    private record WebUser(String username, String passwordHash, String salt, String minecraft, String uuid, String role, long createdAt) {}

    private Optional<WebUser> mysqlUser(String username) {
        if (!webMysqlReady) return Optional.empty();
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT username,password_hash,salt,minecraft,uuid,role,created_at
                     FROM web_users WHERE username=?
                     """)) {
            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new WebUser(
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("salt"),
                        rs.getString("minecraft"),
                        rs.getString("uuid"),
                        rs.getString("role"),
                        rs.getLong("created_at")
                ));
            }
        } catch (Throwable t) {
            webMysqlReady = false;
            webMysqlError = t.getMessage();
            return Optional.empty();
        }
    }

    private Optional<WebUser> mysqlUserByUuid(UUID uuid) {
        if (!webMysqlReady || uuid == null) return Optional.empty();
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT username,password_hash,salt,minecraft,uuid,role,created_at
                     FROM web_users WHERE uuid=?
                     """)) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return Optional.empty();
                return Optional.of(new WebUser(
                        rs.getString("username"),
                        rs.getString("password_hash"),
                        rs.getString("salt"),
                        rs.getString("minecraft"),
                        rs.getString("uuid"),
                        rs.getString("role"),
                        rs.getLong("created_at")
                ));
            }
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
            return Optional.empty();
        }
    }

    private boolean mysqlCreateUser(String username, String salt, String hash, String minecraft) {
        if (!webMysqlReady) return false;
        long now = System.currentTimeMillis();
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO web_users(username,password_hash,salt,minecraft,uuid,role,created_at,updated_at)
                     VALUES(?,?,?,?,?,?,?,?)
                     """)) {
            ps.setString(1, username);
            ps.setString(2, hash);
            ps.setString(3, salt);
            ps.setString(4, minecraft == null ? "" : minecraft);
            ps.setString(5, "");
            ps.setString(6, "player");
            ps.setLong(7, now);
            ps.setLong(8, now);
            ps.executeUpdate();
            return true;
        } catch (SQLIntegrityConstraintViolationException duplicate) {
            return false;
        } catch (Throwable t) {
            webMysqlReady = false;
            webMysqlError = t.getMessage();
            return false;
        }
    }

    private boolean mysqlCreateLinkCode(String username, String code, long expiresAt) {
        if (!webMysqlReady) return false;
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     REPLACE INTO account_link_codes(code,username,expires_at,created_at)
                     VALUES(?,?,?,?)
                     """)) {
            ps.setString(1, code);
            ps.setString(2, username);
            ps.setLong(3, expiresAt);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (Throwable t) {
            webMysqlReady = false;
            webMysqlError = t.getMessage();
            return false;
        }
    }

    private boolean mysqlCreateResetCode(String username, String code, long expiresAt) {
        if (!webMysqlReady) return false;
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     REPLACE INTO account_reset_codes(code,username,expires_at,used,created_at)
                     VALUES(?,?,?,?,?)
                     """)) {
            ps.setString(1, code);
            ps.setString(2, username);
            ps.setLong(3, expiresAt);
            ps.setBoolean(4, false);
            ps.setLong(5, System.currentTimeMillis());
            ps.executeUpdate();
            return true;
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
            return false;
        }
    }

    private boolean mysqlConfirmReset(String username, String code, String salt, String hash) {
        if (!webMysqlReady) return false;
        try (Connection connection = webConnection()) {
            connection.setAutoCommit(false);
            long expires = 0L;
            boolean used = true;
            try (PreparedStatement ps = connection.prepareStatement("""
                    SELECT expires_at,used FROM account_reset_codes WHERE code=? AND username=?
                    """)) {
                ps.setString(1, code);
                ps.setString(2, username);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        expires = rs.getLong("expires_at");
                        used = rs.getBoolean("used");
                    }
                }
            }
            if (used || expires < System.currentTimeMillis()) {
                connection.rollback();
                return false;
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE web_users SET salt=?, password_hash=?, updated_at=? WHERE username=?
                    """)) {
                ps.setString(1, salt);
                ps.setString(2, hash);
                ps.setLong(3, System.currentTimeMillis());
                ps.setString(4, username);
                if (ps.executeUpdate() <= 0) {
                    connection.rollback();
                    return false;
                }
            }
            try (PreparedStatement ps = connection.prepareStatement("""
                    UPDATE account_reset_codes SET used=1 WHERE code=? AND username=?
                    """)) {
                ps.setString(1, code);
                ps.setString(2, username);
                ps.executeUpdate();
            }
            connection.commit();
            return true;
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
            return false;
        }
    }

    private boolean mysqlSetRole(String username, String role) {
        if (!webMysqlReady) return false;
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("UPDATE web_users SET role=?, updated_at=? WHERE username=?")) {
            ps.setString(1, role);
            ps.setLong(2, System.currentTimeMillis());
            ps.setString(3, username);
            return ps.executeUpdate() > 0;
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
            return false;
        }
    }

    private List<Map<String,Object>> mysqlUsers() {
        List<Map<String,Object>> out = new ArrayList<>();
        if (!webMysqlReady) return out;
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT username,minecraft,uuid,role,created_at,updated_at
                     FROM web_users ORDER BY updated_at DESC, username ASC LIMIT 250
                     """);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                Map<String,Object> row = new LinkedHashMap<>();
                row.put("username", rs.getString("username"));
                row.put("minecraft", rs.getString("minecraft"));
                row.put("uuid", rs.getString("uuid"));
                row.put("role", rs.getString("role"));
                row.put("createdAt", rs.getLong("created_at"));
                row.put("updatedAt", rs.getLong("updated_at"));
                row.put("store", "mysql");
                out.add(row);
            }
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
        }
        return out;
    }

    private boolean mysqlClaimLinkCode(Player player, String code) {
        if (!webMysqlReady) return false;
        try (Connection connection = webConnection()) {
            connection.setAutoCommit(false);
            String username = null;
            long expires = 0L;
            try (PreparedStatement find = connection.prepareStatement("SELECT username,expires_at FROM account_link_codes WHERE code=?")) {
                find.setString(1, code.trim());
                try (ResultSet rs = find.executeQuery()) {
                    if (rs.next()) {
                        username = rs.getString("username");
                        expires = rs.getLong("expires_at");
                    }
                }
            }
            if (username == null || expires < System.currentTimeMillis()) {
                connection.rollback();
                return false;
            }
            try (PreparedStatement update = connection.prepareStatement("""
                    UPDATE web_users SET uuid=?, minecraft=?, updated_at=? WHERE username=?
                    """)) {
                update.setString(1, player.getUniqueId().toString());
                update.setString(2, player.getName());
                update.setLong(3, System.currentTimeMillis());
                update.setString(4, username);
                update.executeUpdate();
            }
            try (PreparedStatement delete = connection.prepareStatement("DELETE FROM account_link_codes WHERE code=?")) {
                delete.setString(1, code.trim());
                delete.executeUpdate();
            }
            connection.commit();
            cachePlayerSnapshot(player.getUniqueId(), player.getName());
            return true;
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
            return false;
        }
    }

    private void cachePlayer(UUID uuid, String name, String username, String role) {
        if (!webMysqlReady || uuid == null) return;
        String faction = "";
        int coins = 0;
        try { if (plugin.getFactionManager() != null) faction = plugin.getFactionManager().getFactionOf(uuid); } catch (Throwable ignored) {}
        try { if (plugin.getCoinsManager() != null) coins = Math.max(0, plugin.getCoinsManager().getCoins(uuid)); } catch (Throwable ignored) {}
        if (coins <= 0) coins = cachedCoins(uuid);
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO player_cache(uuid,name,faction,role,coins,last_seen)
                     VALUES(?,?,?,?,?,?)
                     ON DUPLICATE KEY UPDATE name=VALUES(name), faction=VALUES(faction), role=VALUES(role), coins=VALUES(coins), last_seen=VALUES(last_seen)
                     """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name == null ? "" : name);
            ps.setString(3, faction == null ? "" : faction);
            ps.setString(4, role == null || role.isBlank() ? "player" : role);
            ps.setInt(5, coins);
            ps.setLong(6, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
        }
    }

    public void cachePlayerSnapshot(UUID uuid, String name) {
        if (!webMysqlReady || uuid == null) return;
        Optional<WebUser> linked = mysqlUserByUuid(uuid);
        Player online = Bukkit.getPlayer(uuid);
        cachePlayer(
                uuid,
                name,
                linked.map(WebUser::username).orElse(""),
                rankFor(uuid, online)
        );
    }

    private String cleanRole(String role) {
        String normalized = role == null ? "" : role.trim().toLowerCase(Locale.ROOT);
        if (normalized.equals("admin") || normalized.equals("staff") || normalized.equals("player")) return normalized;
        return "player";
    }

    private boolean checkAdminAccess(HttpExchange ex) {
        if (checkAuth(ex) && ex.getRequestHeaders().getFirst("X-API-Key") != null) return true;
        String username = currentSessionUser(ex);
        return "admin".equals(roleFor(username, webUsers(), ex));
    }

    private boolean checkStaffAccess(HttpExchange ex) {
        if (checkAuth(ex) && ex.getRequestHeaders().getFirst("X-API-Key") != null) return true;
        String username = currentSessionUser(ex);
        String role = roleFor(username, webUsers(), ex);
        return "admin".equals(role) || "staff".equals(role);
    }

    private String randomToken(int bytes) {
        byte[] data = new byte[Math.max(8, bytes)];
        new SecureRandom().nextBytes(data);
        StringBuilder out = new StringBuilder(data.length * 2);
        for (byte b : data) out.append(String.format("%02x", b));
        return out.toString();
    }

    private String sha256(String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] data = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder(data.length * 2);
            for (byte b : data) out.append(String.format("%02x", b));
            return out.toString();
        } catch (Throwable ignored) {
            return "";
        }
    }

    private String jwtSecret() {
        String configured = plugin.getConfig().getString("webapi.jwtSecret", "").trim();
        if (!configured.isBlank()) return configured;
        String fallback = apiKey == null || apiKey.isBlank() ? "vetroforge-dev-secret-change-me" : apiKey;
        return fallback + ":" + plugin.getDataFolder().getAbsolutePath();
    }

    private String b64Url(byte[] data) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(data);
    }

    private byte[] hmac(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (Throwable ignored) {
            return new byte[0];
        }
    }

    private String sessionToken(String username) {
        long now = System.currentTimeMillis();
        long exp = now + TimeUnit.DAYS.toMillis(plugin.getConfig().getInt("webapi.sessionDays", 7));
        String header = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";
        String payload = "{\"sub\":\"" + esc(username) + "\",\"iat\":" + now + ",\"exp\":" + exp + ",\"nonce\":\"" + randomToken(8) + "\"}";
        String unsigned = b64Url(header.getBytes(StandardCharsets.UTF_8)) + "." + b64Url(payload.getBytes(StandardCharsets.UTF_8));
        return unsigned + "." + b64Url(hmac(unsigned));
    }

    private String currentSessionUser(HttpExchange ex) {
        String token = ex.getRequestHeaders().getFirst("X-Session-Token");
        String auth = ex.getRequestHeaders().getFirst("Authorization");
        if ((token == null || token.isBlank()) && auth != null && auth.startsWith("Bearer ")) token = auth.substring("Bearer ".length()).trim();
        if (token == null || token.isBlank()) return null;
        String[] parts = token.split("\\.");
        if (parts.length != 3) return null;
        String unsigned = parts[0] + "." + parts[1];
        if (!MessageDigest.isEqual(b64Url(hmac(unsigned)).getBytes(StandardCharsets.UTF_8), parts[2].getBytes(StandardCharsets.UTF_8))) return null;
        String payload;
        try {
            payload = new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
        } catch (Throwable ignored) {
            return null;
        }
        long exp = 0L;
        try {
            String raw = jsonGet(payload, "exp");
            if (raw != null) exp = Long.parseLong(raw);
        } catch (Throwable ignored) {}
        if (exp > 0L && exp < System.currentTimeMillis()) return null;
        return safeUser(jsonGet(payload, "sub"));
    }

    private String roleFor(String username, YamlConfiguration users, HttpExchange ex) {
        if (checkAuth(ex) && ex.getRequestHeaders().getFirst("X-API-Key") != null) return "admin";
        if (username == null || username.isBlank()) return "public";
        Optional<WebUser> mysql = mysqlUser(username);
        if (mysql.isPresent()) {
            String role = mysql.get().role() == null ? "" : mysql.get().role().toLowerCase(Locale.ROOT);
            if (role.equals("admin") || role.equals("staff") || role.equals("player")) return role;
        }
        String configured = users.getString("users." + username + ".role", "player").toLowerCase(Locale.ROOT);
        String uuidRaw = users.getString("users." + username + ".uuid", "");
        if (uuidRaw != null && !uuidRaw.isBlank()) {
            try {
                Player player = Bukkit.getPlayer(UUID.fromString(uuidRaw));
                if (player != null) {
                    if (player.hasPermission("vetroforge.web.admin")
                            || player.hasPermission("vetroforge.shop.admin")
                            || player.hasPermission("vetroforge.admin")) return "admin";
                    if (player.hasPermission("vetroforge.web.staff")
                            || player.hasPermission("vetroforge.auction.admin")
                            || player.hasPermission("vetroforge.staff.notify")
                            || player.hasPermission("vetroforge.staff.ban")) return "staff";
                }
            } catch (Throwable ignored) {}
        }
        if (configured.equals("admin") || configured.equals("staff") || configured.equals("player")) return configured;
        return "player";
    }

    public String createLinkCode(Player player) {
        if (player == null) return "";
        String code = String.valueOf(100000 + new SecureRandom().nextInt(900000));
        YamlConfiguration users = webUsers();
        users.set("minecraftCodes." + code + ".uuid", player.getUniqueId().toString());
        users.set("minecraftCodes." + code + ".name", player.getName());
        users.set("minecraftCodes." + code + ".expires", System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10));
        saveWebUsers(users);
        return code;
    }

    public boolean claimLinkCode(Player player, String code) {
        if (player == null || code == null || code.isBlank()) return false;
        if (mysqlClaimLinkCode(player, code)) return true;
        YamlConfiguration users = webUsers();
        String path = "linkCodes." + code.trim();
        String username = safeUser(users.getString(path + ".username", ""));
        long expires = users.getLong(path + ".expires", 0L);
        if (username.isBlank() || expires < System.currentTimeMillis()) return false;
        users.set("users." + username + ".uuid", player.getUniqueId().toString());
        users.set("users." + username + ".minecraft", player.getName());
        users.set(path, null);
        saveWebUsers(users);
        return true;
    }

    /* ================= Root & Basics ================= */

    private void handleRoot(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            String j = """
                    {"name":"VetroForge WebAPI","ok":true,"endpoints":["/api/health","/api/player/hud?uuid=...","/api/leaderboards","/api/faction?name=...","/api/events/next","/api/warzone/live","/api/raid/status","/api/lootdrop/next","/api/contracts/active","/api/market/overview","/api/mysticstorm","/api/stream/warzone","/api/admin/broadcast","/api/admin/lootdrop/flare"]}""";
            send(ex, 200, j);
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleHealth(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            Runtime runtime = Runtime.getRuntime();
            long usedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
            long maxMb = runtime.maxMemory() / 1024L / 1024L;
            double[] tps = Bukkit.getTPS();
            long uptimeSeconds = Math.max(0L, (System.currentTimeMillis() - apiStartedAtMillis) / 1000L);
            String j = "{\"ok\":true,\"online\":" + plugin.getServer().getOnlinePlayers().size() +
                    ",\"max\":" + plugin.getServer().getMaxPlayers() +
                    ",\"maxPlayers\":" + plugin.getServer().getMaxPlayers() +
                    ",\"tps\":" + formatTps(tps, 0) +
                    ",\"ramUsedMb\":" + usedMb +
                    ",\"ramMaxMb\":" + maxMb +
                    ",\"uptimeSeconds\":" + uptimeSeconds + "}";
            send(ex, 200, j);
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleWebStatus(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            String external = publicStatusUrl();
            boolean webUsers = tableExists("web_users");
            boolean linkCodes = tableExists("account_link_codes");
            boolean resetCodes = tableExists("account_reset_codes");
            boolean playerCache = tableExists("player_cache");
            StatusService lobby = serviceLobby();
            StatusService factions = serviceFactions();
            StatusService mysql = serviceMysql(webUsers && linkCodes && resetCodes && playerCache);
            StatusService redis = serviceRedis();
            StatusService webApi = serviceWebApi();
            String overall = overallStatus(List.of(lobby, factions, mysql, redis, webApi));
            int online = plugin.getServer().getOnlinePlayers().size();
            int maxPlayers = plugin.getServer().getMaxPlayers();
            Runtime runtime = Runtime.getRuntime();
            long ramUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
            long ramMaxMb = runtime.maxMemory() / 1024L / 1024L;
            double[] tps = Bukkit.getTPS();
            long uptimeSeconds = Math.max(0L, (System.currentTimeMillis() - apiStartedAtMillis) / 1000L);
            Map<String, ServerStatusRow> serverRows = serverStatusRows();
            ServerStatusRow lobbyRow = serverRows.getOrDefault("lobby", new ServerStatusRow(false, 0, 0, 0.0D, 0L));
            ServerStatusRow factionsRow = serverRows.getOrDefault("factions", new ServerStatusRow(true, online, maxPlayers, safeTps(tps, 0), System.currentTimeMillis()));
            int proxyOnline = Math.max(online, serverRows.values().stream().filter(ServerStatusRow::online).mapToInt(ServerStatusRow::players).sum());
            int proxyMaxPlayers = Math.max(maxPlayers, serverRows.values().stream().filter(ServerStatusRow::online).mapToInt(ServerStatusRow::maxPlayers).sum());
            maybeSendStatusAlert(overall, List.of(lobby, factions, mysql, redis, webApi), external);
            send(ex, 200, "{"
                    + "\"ok\":true"
                    + ",\"overall\":\"" + esc(overall) + "\""
                    + ",\"online\":" + online
                    + ",\"maxPlayers\":" + maxPlayers
                    + ",\"tps\":" + formatTps(tps, 0)
                    + ",\"tps5m\":" + formatTps(tps, 1)
                    + ",\"tps15m\":" + formatTps(tps, 2)
                    + ",\"ramUsedMb\":" + ramUsedMb
                    + ",\"ramMaxMb\":" + ramMaxMb
                    + ",\"uptimeSeconds\":" + uptimeSeconds
                    + ",\"proxyOnline\":" + proxyOnline
                    + ",\"proxyMaxPlayers\":" + proxyMaxPlayers
                    + ",\"lobbyOnline\":" + lobbyRow.players()
                    + ",\"lobbyMaxPlayers\":" + lobbyRow.maxPlayers()
                    + ",\"lobbyStatus\":\"" + esc(lobbyRow.online() ? "ok" : "unknown") + "\""
                    + ",\"factionsOnline\":" + factionsRow.players()
                    + ",\"factionsMaxPlayers\":" + factionsRow.maxPlayers()
                    + ",\"apiUrl\":\"" + esc(external) + "\""
                    + ",\"mysqlReady\":" + webMysqlReady
                    + ",\"mysqlError\":\"" + esc(webMysqlError) + "\""
                    + ",\"services\":{"
                    + "\"lobby\":" + serviceJson(lobby) + ','
                    + "\"factions\":" + serviceJson(factions) + ','
                    + "\"mysql\":" + serviceJson(mysql) + ','
                    + "\"redis\":" + serviceJson(redis) + ','
                    + "\"webapi\":" + serviceJson(webApi)
                    + "}"
                    + ",\"tables\":{"
                    + "\"web_users\":" + webUsers + ','
                    + "\"account_link_codes\":" + linkCodes + ','
                    + "\"account_reset_codes\":" + resetCodes + ','
                    + "\"player_cache\":" + playerCache
                    + "}"
                    + "}");
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private String formatTps(double[] tps, int index) {
        return String.format(Locale.US, "%.2f", safeTps(tps, index));
    }

    private double safeTps(double[] tps, int index) {
        if (tps != null && index >= 0 && index < tps.length && Double.isFinite(tps[index])) {
            return Math.min(20.0, Math.max(0.0, tps[index]));
        }
        return 20.0D;
    }

    private record ServerStatusRow(boolean online, int players, int maxPlayers, double tps, long updatedAt) {}

    private Map<String, ServerStatusRow> serverStatusRows() {
        Map<String, ServerStatusRow> rows = new LinkedHashMap<>();
        if (!webMysqlReady || !tableExists("server_status")) return rows;
        try (Connection connection = webConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT server_name, online, players, max_players, tps, UNIX_TIMESTAMP(updated_at)
                     FROM server_status
                     """);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                String name = rs.getString(1);
                if (name == null || name.isBlank()) continue;
                rows.put(name.toLowerCase(Locale.ROOT), new ServerStatusRow(
                        rs.getBoolean(2),
                        Math.max(0, rs.getInt(3)),
                        Math.max(0, rs.getInt(4)),
                        Math.max(0.0D, Math.min(20.0D, rs.getDouble(5))),
                        Math.max(0L, rs.getLong(6))
                ));
            }
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
        }
        return rows;
    }

    private record StatusService(String id, String label, String status, String detail) {}

    private StatusService serviceLobby() {
        String lobby = plugin.getConfig().getString("proxy.lobby-server", "Lobby");
        boolean configured = lobby != null && !lobby.isBlank();
        return new StatusService("lobby", "Lobby", configured ? "ok" : "warn",
                configured ? "Proxy target: " + lobby : "proxy.lobby-server fehlt");
    }

    private StatusService serviceFactions() {
        int online = plugin.getServer().getOnlinePlayers().size();
        int max = plugin.getServer().getMaxPlayers();
        String status = plugin.isEnabled() ? "ok" : "error";
        return new StatusService("factions", "Factions", status, online + "/" + max + " Spieler online");
    }

    private StatusService serviceMysql(boolean tablesReady) {
        if (!plugin.getConfig().getBoolean("mysql.enabled", false)) {
            return new StatusService("mysql", "MySQL", "warn", "mysql.enabled=false");
        }
        if (!webMysqlReady) {
            return new StatusService("mysql", "MySQL", "error", webMysqlError == null || webMysqlError.isBlank() ? "nicht verbunden" : webMysqlError);
        }
        return new StatusService("mysql", "MySQL", tablesReady ? "ok" : "warn",
                tablesReady ? "verbunden, Tabellen bereit" : "verbunden, aber Web-Tabellen fehlen");
    }

    private StatusService serviceRedis() {
        if (!plugin.getConfig().getBoolean("redis.enabled", false)) {
            return new StatusService("redis", "Redis", "warn", "redis.enabled=false");
        }
        String host = plugin.getConfig().getString("redis.host", "127.0.0.1");
        int redisPort = plugin.getConfig().getInt("redis.port", 6379);
        String password = plugin.getConfig().getString("redis.password", "");
        try (Jedis jedis = new Jedis(host, redisPort)) {
            if (password != null && !password.isBlank()) jedis.auth(password);
            String pong = jedis.ping();
            boolean ok = "PONG".equalsIgnoreCase(pong);
            return new StatusService("redis", "Redis", ok ? "ok" : "error", ok ? "verbunden" : "Ping fehlgeschlagen");
        } catch (Throwable t) {
            return new StatusService("redis", "Redis", "error", t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage());
        }
    }

    private StatusService serviceWebApi() {
        return new StatusService("webapi", "WebAPI", server == null ? "error" : "ok", server == null ? "nicht aktiv" : "aktiv");
    }

    private String overallStatus(List<StatusService> services) {
        boolean error = false;
        boolean warn = false;
        for (StatusService service : services) {
            if ("error".equals(service.status())) error = true;
            if ("warn".equals(service.status())) warn = true;
        }
        if (error) return "error";
        if (warn) return "warn";
        return "ok";
    }

    private String serviceJson(StatusService service) {
        return "{"
                + "\"id\":\"" + esc(service.id()) + "\""
                + ",\"label\":\"" + esc(service.label()) + "\""
                + ",\"status\":\"" + esc(service.status()) + "\""
                + ",\"detail\":\"" + esc(service.detail()) + "\""
                + "}";
    }

    private void maybeSendStatusAlert(String overall, List<StatusService> services, String publicUrl) {
        if (!"error".equals(overall)) return;
        if (!plugin.getConfig().getBoolean("status.alerts.enabled", false)) return;
        String webhook = plugin.getConfig().getString("status.alerts.webhookUrl", "");
        if (webhook == null || webhook.isBlank()) webhook = System.getenv("VF_STATUS_WEBHOOK_URL");
        if (webhook == null || webhook.isBlank()) return;

        StringBuilder key = new StringBuilder();
        StringBuilder broken = new StringBuilder();
        for (StatusService service : services) {
            if (!"error".equals(service.status())) continue;
            if (!key.isEmpty()) key.append(',');
            if (!broken.isEmpty()) broken.append("\n");
            key.append(service.id());
            broken.append("**").append(service.label()).append("**: ").append(service.detail());
        }
        long now = System.currentTimeMillis();
        long cooldown = Math.max(60_000L, plugin.getConfig().getLong("status.alerts.cooldown-seconds", 300L) * 1000L);
        String currentKey = key.toString();
        if (currentKey.equals(lastStatusAlertKey) && now - lastStatusAlertAt < cooldown) return;
        lastStatusAlertKey = currentKey;
        lastStatusAlertAt = now;

        String json = "{"
                + "\"username\":\"VetroForge Status\","
                + "\"embeds\":[{"
                + "\"title\":\"VetroForge Status ROT\","
                + "\"description\":\"" + esc(broken.toString()) + "\","
                + "\"color\":16724079,"
                + "\"fields\":[{\"name\":\"Statusseite\",\"value\":\"" + esc(publicUrl == null || publicUrl.isBlank() ? "nicht gesetzt" : publicUrl) + "\"}],"
                + "\"footer\":{\"text\":\"Cooldown aktiv, keine Spam-Alerts\"}"
                + "}]"
                + "}";
        try {
            plugin.getWebhookManager().postJson(webhook, json);
        } catch (Throwable t) {
            plugin.getLogger().warning("[StatusAlert] Webhook failed: " + t.getMessage());
        }
    }

    private void startStatusAlertMonitor() {
        if (!plugin.getConfig().getBoolean("status.alerts.enabled", false)) return;
        long seconds = Math.max(30L, plugin.getConfig().getLong("status.update-seconds", 60L));
        statusAlertTaskId = Bukkit.getScheduler().runTaskTimerAsynchronously(
                plugin,
                this::checkStatusAlertNow,
                20L * 30L,
                20L * seconds
        ).getTaskId();
    }

    private void checkStatusAlertNow() {
        try {
            boolean webUsers = tableExists("web_users");
            boolean linkCodes = tableExists("account_link_codes");
            boolean resetCodes = tableExists("account_reset_codes");
            boolean playerCache = tableExists("player_cache");
            List<StatusService> services = List.of(
                    serviceLobby(),
                    serviceFactions(),
                    serviceMysql(webUsers && linkCodes && resetCodes && playerCache),
                    serviceRedis(),
                    serviceWebApi()
            );
            String external = publicStatusUrl();
            maybeSendStatusAlert(overallStatus(services), services, external);
        } catch (Throwable t) {
            plugin.getLogger().warning("[StatusAlert] Check failed: " + t.getMessage());
        }
    }

    private String publicStatusUrl() {
        String external = System.getenv("VF_FACTIONS_API_URL");
        if (external == null || external.isBlank()) external = plugin.getConfig().getString("webapi.publicUrl", "");
        if (external == null || external.isBlank()) external = plugin.getConfig().getString("status.public-url", "");
        if (external == null || external.isBlank()) external = "http://" + bind + ":" + port;
        if (external.contains("status.vetroforge.net")) return "https://vetroforge.net/status/";
        return external;
    }

    private boolean tableExists(String table) {
        if (!webMysqlReady) return false;
        try (Connection connection = webConnection()) {
            DatabaseMetaData meta = connection.getMetaData();
            try (ResultSet rs = meta.getTables(connection.getCatalog(), null, table, new String[]{"TABLE"})) {
                return rs.next();
            }
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
            return false;
        }
    }

    private void handlePlayerHud(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }

            Map<String,String> q = query(ex);
            String rawUuid = q.get("uuid");
            if (rawUuid == null || rawUuid.isBlank()) {
                send(ex, 400, "{\"ok\":false,\"err\":\"uuid missing\"}");
                return;
            }

            UUID uuid;
            try {
                uuid = UUID.fromString(rawUuid);
            } catch (IllegalArgumentException exception) {
                send(ex, 400, "{\"ok\":false,\"err\":\"invalid uuid\"}");
                return;
            }

            Player player = Bukkit.getPlayer(uuid);
            String faction = null;
            try {
                if (plugin.getFactionManager() != null) faction = plugin.getFactionManager().getFactionOf(uuid);
            } catch (Throwable ignored) {}

            int coins = 0;
            try {
                if (plugin.getCoinsManager() != null) coins = plugin.getCoinsManager().getCoins(uuid);
            } catch (Throwable ignored) {}

            int energy = 0;
            int maxEnergy = plugin.getConfig().getInt("energy.cap", 10);
            long nextEnergyAt = 0L;
            try {
                if (plugin.getEnergyManager() != null) {
                    energy = plugin.getEnergyManager().get(uuid);
                    maxEnergy = plugin.getEnergyManager().getMax(uuid);
                    if (energy < maxEnergy) {
                        nextEnergyAt = System.currentTimeMillis() + plugin.getEnergyManager().millisToNext(uuid);
                    }
                }
            } catch (Throwable ignored) {}

            int crystals = 0;
            try {
                RaidCrystalManager rcm = plugin.getRaidCrystalManager();
                if (rcm != null) crystals = faction == null || faction.isBlank()
                        ? rcm.getPlayerCrystals(uuid)
                        : rcm.getForFaction(faction);
            } catch (Throwable ignored) {}

            int level = 0;
            try {
                if (plugin.getStatsManager() != null) level = plugin.getStatsManager().getLevel(uuid);
            } catch (Throwable ignored) {}

            int disparity = 0;
            try {
                if (player != null && plugin.getWarzoneRegionManager() != null
                        && plugin.getWarzoneRegionManager().isWarzone(player.getLocation())) {
                    disparity = plugin.getWarzoneRegionManager().disparityLevel(player.getLocation());
                }
            } catch (Throwable ignored) {}

            Object timedLoot = plugin.getTimedLootChestService();
            Long nextLootdrop = tryLong(timedLoot, "getNextSpawnEpochMs", "getNextEpoch", "nextSpawnAt");
            long now = System.currentTimeMillis();
            long lootdropEta = nextLootdrop != null && nextLootdrop > now ? (nextLootdrop - now) / 1000L : 0L;
            String lootdropStatus = lootdropEta > 0 ? "in " + lootdropEta + "s" : "ready";

            String bossStatus = "none";
            int bossCount = 0;
            try {
                if (plugin.getWarzoneMobService() != null) {
                    bossCount = plugin.getWarzoneMobService().activeBosses().size();
                    bossStatus = bossCount <= 0 ? "none" : bossCount + " active";
                }
            } catch (Throwable ignored) {}

            RaidSnapshot raid = raidSnapshot(player, faction);
            String rank = rankFor(uuid, player);
            int prestige = prestigeLevel(uuid);
            int prestigeXp = prestigeXp(uuid);
            int xpToNext = prestigeXpToNext(uuid);

            String j = "{"
                    + "\"ok\":true"
                    + ",\"uuid\":\"" + esc(uuid.toString()) + "\""
                    + ",\"name\":\"" + esc(player == null ? "" : player.getName()) + "\""
                    + ",\"rank\":\"" + esc(rank) + "\""
                    + ",\"faction\":\"" + esc(faction == null ? "" : faction) + "\""
                    + ",\"coins\":" + coins
                    + ",\"energy\":" + energy
                    + ",\"maxEnergy\":" + maxEnergy
                    + ",\"nextEnergyAtEpochMillis\":" + nextEnergyAt
                    + ",\"crystals\":" + crystals
                    + ",\"level\":" + level
                    + ",\"prestige\":" + prestige
                    + ",\"prestigeXp\":" + prestigeXp
                    + ",\"nextPrestige\":\"" + esc("Prestige " + (prestige + 1)) + "\""
                    + ",\"xpToNext\":" + xpToNext
                    + ",\"disparity\":" + disparity
                    + ",\"lootdropStatus\":\"" + esc(lootdropStatus) + "\""
                    + ",\"lootdropEtaSeconds\":" + lootdropEta
                    + ",\"bossStatus\":\"" + esc(bossStatus) + "\""
                    + ",\"bossCount\":" + bossCount
                    + ",\"raidStatus\":\"" + esc(raid.status) + "\""
                    + ",\"raidEndsAtEpochMillis\":" + raid.endsAtEpochMillis
                    + ",\"obsidianBroken\":" + raid.obsidianBroken
                    + ",\"obsidianGoal\":" + raid.obsidianGoal
                    + ",\"teamOnline\":" + raid.teamOnline
                    + ",\"links\":{"
                    + "\"discord\":\"" + esc(link("discord", "https://discord.gg/vetroforge")) + "\""
                    + ",\"status\":\"" + esc(link("status", "https://vetroforge.net/status/")) + "\""
                    + ",\"rules\":\"" + esc(link("rules", "https://vetroforge.net/rules")) + "\""
                    + "}"
                    + "}";
            send(ex, 200, j);
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private RaidSnapshot raidSnapshot(Player player, String faction) {
        int defaultGoal = plugin.getConfig().getInt("raid.attack.obsidianGoal", 25);
        if (player == null && (faction == null || faction.isBlank())) {
            return new RaidSnapshot("idle", 0L, 0, defaultGoal, 0);
        }

        try {
            if (player != null && plugin.getRaidSessionService() != null && plugin.getRaidSessionService().isScouting(player)) {
                return new RaidSnapshot(
                        "scouting",
                        System.currentTimeMillis() + plugin.getRaidSessionService().getScoutingRemainingMillis(player),
                        0,
                        defaultGoal,
                        1
                );
            }
        } catch (Throwable ignored) {}

        RaidManager.Session session = null;
        try {
            RaidManager rm = plugin.getRaidManager();
            if (rm != null) {
                if (player != null) session = rm.getRaidForPlayer(player.getUniqueId());
                if (session == null && faction != null && !faction.isBlank()) session = rm.getSessionByAttacker(faction);
                if (session == null && faction != null && !faction.isBlank()) session = rm.getSessionByTarget(faction);
            }
        } catch (Throwable ignored) {}

        if (session == null) {
            return new RaidSnapshot("idle", 0L, 0, defaultGoal, 0);
        }

        String status = faction != null && faction.equalsIgnoreCase(session.targetFaction) ? "defending" : "attacking";
        if (player != null) {
            try {
                if (plugin.getRaidSessionService() != null && plugin.getRaidSessionService().isInRaid(player)) {
                    status = "raiding";
                }
            } catch (Throwable ignored) {}
        }

        int online = 0;
        for (UUID raider : session.raiders) {
            Player current = Bukkit.getPlayer(raider);
            if (current != null && current.isOnline()) online++;
        }

        return new RaidSnapshot(
                status,
                System.currentTimeMillis() + Math.max(0, session.secondsLeft) * 1000L,
                session.obsidianBroken,
                session.obsidianGoal,
                online
        );
    }

    private String link(String key, String fallback) {
        String value = plugin.getConfig().getString("links." + key, "");
        if (value == null || value.isBlank()) value = plugin.getConfig().getString("webapi.links." + key, "");
        return value == null || value.isBlank() ? fallback : value;
    }

    private record RaidSnapshot(String status, long endsAtEpochMillis, int obsidianBroken, int obsidianGoal, int teamOnline) {}

    /* ================= Leaderboards & Faction (wie zuvor) ================= */

    private void handleLeaderboards(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            Map<String,String> q = query(ex);
            int limit = Math.max(1, Math.min(50, asInt(q, "limit", 10)));

            List<String> kills    = tryFetchList(plugin.getStatsManager(), "topKills", limit);
            List<String> prestige = tryFetchList(plugin.getStatsManager(), "topPrestige", limit);

            List<String> crystals = new ArrayList<>();
            try {
                RaidCrystalManager rcm = plugin.getRaidCrystalManager();
                if (rcm != null) {
                    var top = rcm.topFactions(limit);
                    for (var e : top) crystals.add(e.getKey() + ":" + e.getValue());
                }
            } catch (Throwable ignored) {}

            List<String> factions = new ArrayList<>();
            try {
                FactionManager fm = plugin.getFactionManager();
                if (fm != null) {
                    List<String> all = new ArrayList<>(fm.getAllFactionNames());
                    all.sort((a,b) -> Integer.compare(fm.getMembers(b).size(), fm.getMembers(a).size()));
                    for (int i=0;i<Math.min(limit, all.size()); i++) {
                        String n = all.get(i);
                        factions.add(n + ":" + fm.getMembers(n).size());
                    }
                }
            } catch (Throwable ignored){}

            String j = "{"
                    + "\"ok\":true"
                    + ",\"kills\":"     + toJsonArray(kills)
                    + ",\"prestige\":"  + toJsonArray(prestige)
                    + ",\"crystals\":"  + toJsonObjectArray(crystals, "faction","crystals")
                    + ",\"factions\":"  + toJsonObjectArray(factions, "name","members")
                    + "}";
            send(ex, 200, j);
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleFaction(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }

            String path = ex.getRequestURI().getPath();
            String name = null;
            if (path != null) {
                String[] parts = path.split("/");
                if (parts.length >= 4) name = urld(parts[3]);
            }
            if (name == null || name.isEmpty()) name = query(ex).get("name");
            if (name == null || name.isEmpty()) { send(ex, 400, "{\"ok\":false,\"err\":\"name missing\"}"); return; }

            FactionManager fm = plugin.getFactionManager();
            if (fm == null) { send(ex, 500, "{\"ok\":false,\"err\":\"no faction manager\"}"); return; }
            if (!fm.getAllFactionNames().contains(name)) { send(ex, 404, "{\"ok\":false,\"err\":\"faction not found\"}"); return; }

            List<UUID> memberIds = fm.getMembers(name);
            int members = memberIds.size();
            Set<String> allies = fm.getAllies(name);
            String leader = "-";
            List<String> topPlayers = new ArrayList<>();
            List<Map<String,Object>> memberRows = new ArrayList<>();
            int factionElo = 0;

            try {
                memberIds.sort((a, b) -> Integer.compare(
                        plugin.getStatsManager() == null ? 0 : plugin.getStatsManager().getKills(b),
                        plugin.getStatsManager() == null ? 0 : plugin.getStatsManager().getKills(a)
                ));
                for (UUID uuid : memberIds) {
                    String playerName = Bukkit.getOfflinePlayer(uuid).getName();
                    if (playerName == null || playerName.isBlank()) playerName = uuid.toString();
                    if (fm.getRole(uuid) == FactionManager.Role.LEADER) leader = playerName;
                    if (topPlayers.size() < 5) topPlayers.add(playerName);
                    int kills = plugin.getStatsManager() == null ? 0 : plugin.getStatsManager().getKills(uuid);
                    int elo = plugin.getStatsManager() == null ? 0 : plugin.getStatsManager().getElo(uuid);
                    if (plugin.getStatsManager() != null) factionElo += elo;
                    Map<String,Object> row = new LinkedHashMap<>();
                    row.put("uuid", uuid.toString());
                    row.put("name", playerName);
                    row.put("role", fm.getRole(uuid).name());
                    row.put("online", Bukkit.getPlayer(uuid) != null);
                    row.put("kills", kills);
                    row.put("elo", elo);
                    memberRows.add(row);
                }
            } catch (Throwable ignored) {}

            long createdAt = 0L;
            try {
                FactionManager.Snapshot snapshot = fm.exportSnapshot();
                FactionManager.FactionDTO dto = snapshot.factions.get(name);
                if (dto != null && dto.createdAt != null) createdAt = dto.createdAt;
            } catch (Throwable ignored) {}

            int coins = 0;
            try {
                if (plugin.getFactionCoinManager() != null) coins = plugin.getFactionCoinManager().getCoins(name);
            } catch (Throwable ignored) {}

            int crystals = 0;
            try {
                RaidCrystalManager rcm = plugin.getRaidCrystalManager();
                if (rcm != null) crystals = rcm.getForFaction(name);
            } catch (Throwable ignored){}
            int ctfCaptures = 0;
            List<Map<String,Object>> ctfTop = new ArrayList<>();
            try {
                if (plugin.getCaptureZoneManager() != null) {
                    for (var entry : plugin.getCaptureZoneManager().seasonLeaderboard(10)) {
                        if (entry.faction().equalsIgnoreCase(name)) ctfCaptures = entry.captures();
                        Map<String,Object> row = new LinkedHashMap<>();
                        row.put("faction", entry.faction());
                        row.put("captures", entry.captures());
                        ctfTop.add(row);
                    }
                }
            } catch (Throwable ignored) {}

            String j = "{"
                    + "\"ok\":true"
                    + ",\"name\":\"" + esc(name) + "\""
                    + ",\"display\":\"" + esc(fm.getDisplayName(name)) + "\""
                    + ",\"leader\":\"" + esc(leader) + "\""
                    + ",\"members\":" + members
                    + ",\"topPlayers\":" + toJsonArray(topPlayers)
                    + ",\"coins\":" + coins
                    + ",\"sales\":" + 0
                    + ",\"raidsStarted\":" + 0
                    + ",\"raidWins\":" + 0
                    + ",\"elo\":" + factionElo
                    + ",\"membersList\":" + toJsonObjectList(memberRows)
                    + ",\"createdAtEpochMillis\":" + createdAt
                    + ",\"allies\":" + toJsonArray(new ArrayList<>(allies))
                    + ",\"crystals\":" + crystals
                    + ",\"ctfCaptures\":" + ctfCaptures
                    + ",\"ctfSeasonTop\":" + toJsonObjectList(ctfTop)
                    + ",\"eloHistory\":" + factionHistoryJson("elo", name)
                    + ",\"raidHistory\":" + factionHistoryJson("raid", name)
                    + "}";
            send(ex, 200, j);
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    /* ================= Events/MysticStorm/Lootdrop/Raid (GET) ================= */

    private void handleEventsNext(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }

            List<String> worlds = new ArrayList<>();
            List<String> active = new ArrayList<>();
            try {
                var ms = plugin.getMysticStormService();
                for (World w : Bukkit.getWorlds()) {
                    worlds.add(w.getName());
                    boolean a = (ms != null && ms.isActive(w));
                    active.add(w.getName() + ":" + (a ? "true" : "false"));
                }
            } catch (Throwable ignored){}

            String j = "{"
                    + "\"ok\":true"
                    + ",\"worlds\":" + toJsonArray(worlds)
                    + ",\"mysticStorm\":" + toJsonObjectArray(active, "world","active")
                    + "}";
            send(ex, 200, j);
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {}
        }
    }

    private void handleWarzoneLive(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            var rm = plugin.getWarzoneRegionManager();
            List<String> players = new ArrayList<>();
            if (rm != null) {
                for (Player p : Bukkit.getOnlinePlayers()) {
                    try { if (rm.isWarzone(p.getLocation())) players.add(p.getName()); } catch (Throwable ignored) {}
                }
            }
            String j = "{\"ok\":true,\"count\":" + players.size() + ",\"players\":" + toJsonArray(players) + "}";
            send(ex, 200, j);
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleAuthRegister(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            String body = readBody(ex);
            String username = safeUser(jsonGet(body, "username"));
            String password = jsonGet(body, "password");
            String minecraft = jsonGet(body, "minecraft");
            if (username.isBlank() || password == null || password.length() < 6) {
                send(ex, 400, "{\"ok\":false,\"err\":\"username/password invalid\"}");
                return;
            }
            YamlConfiguration users = webUsers();
            if (webMysqlReady) {
                if (mysqlUser(username).isPresent()) {
                    send(ex, 409, "{\"ok\":false,\"err\":\"exists\"}");
                    return;
                }
                String salt = randomToken(16);
                String hash = sha256(salt + ":" + password);
                if (mysqlCreateUser(username, salt, hash, minecraft)) {
                    send(ex, 200, "{\"ok\":true,\"token\":\"" + esc(sessionToken(username)) + "\",\"role\":\"player\",\"store\":\"mysql\"}");
                    return;
                }
            }
            if (users.isConfigurationSection("users." + username)) {
                send(ex, 409, "{\"ok\":false,\"err\":\"exists\"}");
                return;
            }
            String salt = randomToken(16);
            users.set("users." + username + ".salt", salt);
            users.set("users." + username + ".password", sha256(salt + ":" + password));
            users.set("users." + username + ".minecraft", minecraft == null ? "" : minecraft);
            users.set("users." + username + ".uuid", "");
            users.set("users." + username + ".role", "player");
            users.set("users." + username + ".created", System.currentTimeMillis());
            saveWebUsers(users);
            send(ex, 200, "{\"ok\":true,\"token\":\"" + esc(sessionToken(username)) + "\",\"role\":\"player\"}");
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleAuthLogin(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            String body = readBody(ex);
            String username = safeUser(jsonGet(body, "username"));
            String password = jsonGet(body, "password");
            YamlConfiguration users = webUsers();
            Optional<WebUser> mysql = mysqlUser(username);
            if (mysql.isPresent()) {
                WebUser user = mysql.get();
                if (password == null || !user.passwordHash().equals(sha256(user.salt() + ":" + password))) {
                    send(ex, 401, "{\"ok\":false,\"err\":\"login\"}");
                    return;
                }
                send(ex, 200, "{\"ok\":true,\"token\":\"" + esc(sessionToken(username)) + "\",\"role\":\""
                        + esc(roleFor(username, users, ex)) + "\",\"store\":\"mysql\"}");
                return;
            }
            String salt = users.getString("users." + username + ".salt", "");
            String hash = users.getString("users." + username + ".password", "");
            if (username.isBlank() || password == null || !hash.equals(sha256(salt + ":" + password))) {
                send(ex, 401, "{\"ok\":false,\"err\":\"login\"}");
                return;
            }
            send(ex, 200, "{\"ok\":true,\"token\":\"" + esc(sessionToken(username)) + "\",\"role\":\""
                    + esc(roleFor(username, users, ex)) + "\"}");
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleAuthMe(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            String username = currentSessionUser(ex);
            YamlConfiguration users = webUsers();
            if ((username == null || username.isBlank()) && checkAuth(ex) && ex.getRequestHeaders().getFirst("X-API-Key") != null) {
                send(ex, 200, "{\"ok\":true,\"username\":\"api-key\",\"role\":\"admin\",\"linked\":false}");
                return;
            }
            if (username == null || username.isBlank() || !users.isConfigurationSection("users." + username)) {
                Optional<WebUser> mysql = mysqlUser(username);
                if (mysql.isEmpty()) {
                    send(ex, 200, "{\"ok\":true,\"username\":\"\",\"role\":\"public\",\"linked\":false,\"store\":\"none\"}");
                    return;
                }
            }
            Optional<WebUser> mysql = mysqlUser(username);
            String uuid = mysql.map(WebUser::uuid).orElseGet(() -> users.getString("users." + username + ".uuid", ""));
            String minecraft = mysql.map(WebUser::minecraft).orElseGet(() -> users.getString("users." + username + ".minecraft", ""));
            String role = roleFor(username, users, ex);
            send(ex, 200, "{"
                    + "\"ok\":true"
                    + ",\"username\":\"" + esc(username) + "\""
                    + ",\"role\":\"" + esc(role) + "\""
                    + ",\"minecraft\":\"" + esc(minecraft) + "\""
                    + ",\"uuid\":\"" + esc(uuid) + "\""
                    + ",\"linked\":" + (uuid != null && !uuid.isBlank())
                    + ",\"store\":\"" + (mysql.isPresent() ? "mysql" : "yaml") + "\""
                    + "}");
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleAuthLinkCode(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            String username = currentSessionUser(ex);
            if (username == null || username.isBlank()) { send(ex, 401, "{\"ok\":false,\"err\":\"session\"}"); return; }
            YamlConfiguration users = webUsers();
            if (!webMysqlReady && !users.isConfigurationSection("users." + username)) { send(ex, 404, "{\"ok\":false,\"err\":\"user\"}"); return; }
            if (webMysqlReady && mysqlUser(username).isEmpty()) { send(ex, 404, "{\"ok\":false,\"err\":\"user\"}"); return; }
            String code = String.valueOf(100000 + new SecureRandom().nextInt(900000));
            long expires = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(10);
            if (mysqlCreateLinkCode(username, code, expires)) {
                send(ex, 200, "{\"ok\":true,\"code\":\"" + code + "\",\"command\":\"/linkweb " + code + "\",\"expiresInSeconds\":600,\"store\":\"mysql\"}");
                return;
            }
            users.set("linkCodes." + code + ".username", username);
            users.set("linkCodes." + code + ".expires", expires);
            saveWebUsers(users);
            send(ex, 200, "{\"ok\":true,\"code\":\"" + code + "\",\"command\":\"/linkweb " + code + "\",\"expiresInSeconds\":600,\"store\":\"yaml\"}");
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleAuthResetRequest(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            String body = readBody(ex);
            String username = safeUser(jsonGet(body, "username"));
            if (username.isBlank()) {
                send(ex, 400, "{\"ok\":false,\"err\":\"username\"}");
                return;
            }

            String code = randomToken(12);
            long expires = System.currentTimeMillis() + TimeUnit.MINUTES.toMillis(15);
            if (webMysqlReady && mysqlUser(username).isPresent() && mysqlCreateResetCode(username, code, expires)) {
                send(ex, 200, "{\"ok\":true,\"code\":\"" + esc(code) + "\",\"expiresInSeconds\":900,\"store\":\"mysql\"}");
                return;
            }

            YamlConfiguration users = webUsers();
            if (users.isConfigurationSection("users." + username)) {
                users.set("resetCodes." + code + ".username", username);
                users.set("resetCodes." + code + ".expires", expires);
                users.set("resetCodes." + code + ".used", false);
                saveWebUsers(users);
                send(ex, 200, "{\"ok\":true,\"code\":\"" + esc(code) + "\",\"expiresInSeconds\":900,\"store\":\"yaml\"}");
                return;
            }

            // Kein User-Leak: bei unbekannten Accounts trotzdem ok antworten, nur ohne nutzbaren Code.
            send(ex, 200, "{\"ok\":true,\"code\":\"\",\"expiresInSeconds\":900,\"store\":\"none\"}");
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleAuthResetConfirm(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            String body = readBody(ex);
            String username = safeUser(jsonGet(body, "username"));
            String code = jsonGet(body, "code");
            String password = jsonGet(body, "password");
            if (username.isBlank() || code == null || code.isBlank() || password == null || password.length() < 6) {
                send(ex, 400, "{\"ok\":false,\"err\":\"invalid\"}");
                return;
            }
            String salt = randomToken(16);
            String hash = sha256(salt + ":" + password);
            if (mysqlConfirmReset(username, code.trim(), salt, hash)) {
                send(ex, 200, "{\"ok\":true,\"token\":\"" + esc(sessionToken(username)) + "\",\"role\":\""
                        + esc(roleFor(username, webUsers(), ex)) + "\",\"store\":\"mysql\"}");
                return;
            }

            YamlConfiguration users = webUsers();
            String path = "resetCodes." + code.trim();
            String owner = safeUser(users.getString(path + ".username", ""));
            long expires = users.getLong(path + ".expires", 0L);
            boolean used = users.getBoolean(path + ".used", true);
            if (!owner.equals(username) || used || expires < System.currentTimeMillis()
                    || !users.isConfigurationSection("users." + username)) {
                send(ex, 401, "{\"ok\":false,\"err\":\"reset\"}");
                return;
            }
            users.set("users." + username + ".salt", salt);
            users.set("users." + username + ".password", hash);
            users.set(path + ".used", true);
            saveWebUsers(users);
            send(ex, 200, "{\"ok\":true,\"token\":\"" + esc(sessionToken(username)) + "\",\"role\":\""
                    + esc(roleFor(username, users, ex)) + "\",\"store\":\"yaml\"}");
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleAdminUsers(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAdminAccess(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"admin\"}"); return; }

            if ("GET".equalsIgnoreCase(ex.getRequestMethod())) {
                List<Map<String,Object>> users = mysqlUsers();
                if (users.isEmpty()) {
                    YamlConfiguration yaml = webUsers();
                    var section = yaml.getConfigurationSection("users");
                    if (section != null) {
                        for (String username : section.getKeys(false)) {
                            Map<String,Object> row = new LinkedHashMap<>();
                            row.put("username", username);
                            row.put("minecraft", yaml.getString("users." + username + ".minecraft", ""));
                            row.put("uuid", yaml.getString("users." + username + ".uuid", ""));
                            row.put("role", cleanRole(yaml.getString("users." + username + ".role", "player")));
                            row.put("createdAt", yaml.getLong("users." + username + ".created", 0L));
                            row.put("updatedAt", 0L);
                            row.put("store", "yaml");
                            users.add(row);
                        }
                    }
                }
                send(ex, 200, "{\"ok\":true,\"users\":" + toJsonObjectList(users) + ",\"store\":\""
                        + (webMysqlReady ? "mysql" : "yaml") + "\"}");
                return;
            }

            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            String body = readBody(ex);
            Map<String,String> q = query(ex);
            String username = safeUser(value(q, body, "username"));
            String role = cleanRole(value(q, body, "role"));
            if (username.isBlank()) { send(ex, 400, "{\"ok\":false,\"err\":\"username\"}"); return; }
            if (mysqlSetRole(username, role)) {
                send(ex, 200, "{\"ok\":true,\"role\":\"" + esc(role) + "\",\"store\":\"mysql\"}");
                return;
            }
            YamlConfiguration yaml = webUsers();
            if (!yaml.isConfigurationSection("users." + username)) {
                send(ex, 404, "{\"ok\":false,\"err\":\"user\"}");
                return;
            }
            yaml.set("users." + username + ".role", role);
            saveWebUsers(yaml);
            send(ex, 200, "{\"ok\":true,\"role\":\"" + esc(role) + "\",\"store\":\"yaml\"}");
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleAdminDashboardSummary(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkStaffAccess(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"staff\"}"); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }

            Runtime runtime = Runtime.getRuntime();
            long ramUsedMb = (runtime.totalMemory() - runtime.freeMemory()) / 1024L / 1024L;
            long ramMaxMb = runtime.maxMemory() / 1024L / 1024L;
            double[] tps = Bukkit.getTPS();
            int online = plugin.getServer().getOnlinePlayers().size();
            int maxPlayers = plugin.getServer().getMaxPlayers();
            long uptimeSeconds = Math.max(0L, (System.currentTimeMillis() - apiStartedAtMillis) / 1000L);

            int newReports = countRecentReports(TimeUnit.HOURS.toMillis(24));
            int activeListings = 0;
            int riskyListings = 0;
            int quarantined = 0;
            try {
                if (plugin.getAuctionHouseManager() != null) {
                    activeListings = plugin.getAuctionHouseManager().activeListingCount();
                    riskyListings = plugin.getAuctionHouseManager().riskyListingCount(40);
                    quarantined = plugin.getAuctionHouseManager().quarantineCount();
                }
            } catch (Throwable ignored) {}

            String lastErrors = toJsonArray(lastLogProblems(6));
            send(ex, 200, "{"
                    + "\"ok\":true"
                    + ",\"online\":" + online
                    + ",\"maxPlayers\":" + maxPlayers
                    + ",\"tps\":" + formatTps(tps, 0)
                    + ",\"ramUsedMb\":" + ramUsedMb
                    + ",\"ramMaxMb\":" + ramMaxMb
                    + ",\"apiUptimeSeconds\":" + uptimeSeconds
                    + ",\"reports24h\":" + newReports
                    + ",\"ahActiveListings\":" + activeListings
                    + ",\"ahRiskListings\":" + riskyListings
                    + ",\"ahQuarantined\":" + quarantined
                    + ",\"mysqlReady\":" + webMysqlReady
                    + ",\"webApiReady\":" + (server != null)
                    + ",\"lastErrors\":" + lastErrors
                    + "}");
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleAdminPlayersRecent(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkStaffAccess(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"staff\"}"); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            if (!webMysqlReady) { send(ex, 503, "{\"ok\":false,\"err\":\"mysql\"}"); return; }

            Map<String,String> q = query(ex);
            String search = q.getOrDefault("q", "").trim();
            int page = Math.max(1, asInt(q, "page", 1));
            int limit = Math.max(1, Math.min(80, asInt(q, "limit", 20)));
            int days = Math.max(0, Math.min(365, asInt(q, "days", 14)));
            long cutoff = days <= 0 ? 0L : System.currentTimeMillis() - TimeUnit.DAYS.toMillis(days);
            int offset = (page - 1) * limit;

            StringBuilder where = new StringBuilder(" WHERE last_seen>=?");
            List<Object> params = new ArrayList<>();
            params.add(cutoff);
            if (!search.isBlank()) {
                where.append(" AND (name LIKE ? OR uuid LIKE ? OR faction LIKE ? OR role LIKE ?)");
                String like = "%" + search + "%";
                params.add(like);
                params.add(like);
                params.add(like);
                params.add(like);
            }

            List<Map<String,Object>> rows = new ArrayList<>();
            int total = 0;
            try (Connection connection = webConnection()) {
                try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM player_cache" + where)) {
                    bind(count, params);
                    try (ResultSet rs = count.executeQuery()) {
                        if (rs.next()) total = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement("""
                        SELECT uuid,name,faction,role,coins,last_seen
                        FROM player_cache
                        """ + where + " ORDER BY last_seen DESC LIMIT ? OFFSET ?")) {
                    bind(ps, params);
                    ps.setInt(params.size() + 1, limit);
                    ps.setInt(params.size() + 2, offset);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            String uuidRaw = rs.getString("uuid");
                            UUID parsedUuid = safeUuid(uuidRaw);
                            boolean online = false;
                            try { online = parsedUuid != null && Bukkit.getPlayer(parsedUuid) != null; } catch (Throwable ignored) {}

                            Map<String,Object> row = new LinkedHashMap<>();
                            row.put("uuid", uuidRaw);
                            row.put("name", rs.getString("name"));
                            row.put("faction", rs.getString("faction"));
                            row.put("role", rankFor(parsedUuid, parsedUuid == null ? null : Bukkit.getPlayer(parsedUuid)));
                            row.put("coins", rs.getInt("coins"));
                            row.put("lastSeen", rs.getLong("last_seen"));
                            row.put("online", online);
                            rows.add(row);
                        }
                    }
                }
            }

            int pages = Math.max(1, (int)Math.ceil(total / (double)limit));
            send(ex, 200, "{\"ok\":true,\"players\":" + toJsonObjectList(rows)
                    + ",\"page\":" + page
                    + ",\"pages\":" + pages
                    + ",\"total\":" + total
                    + ",\"days\":" + days
                    + ",\"ranks\":" + availableRanksJson()
                    + "}");
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleAdminPlayerRank(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAdminAccess(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"admin\"}"); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            String body = readBody(ex);
            Map<String,String> q = query(ex);
            String uuidRaw = value(q, body, "uuid");
            String name = value(q, body, "name");
            String rank = value(q, body, "rank");
            UUID uuid = safeUuid(uuidRaw);
            if (uuid == null && name != null && !name.isBlank()) uuid = resolveUuidFromCache(name);
            if (uuid == null) { send(ex, 400, "{\"ok\":false,\"err\":\"uuid\"}"); return; }
            if (rank == null || rank.isBlank()) { send(ex, 400, "{\"ok\":false,\"err\":\"rank\"}"); return; }
            if (plugin.getVetroPermsManager() == null) { send(ex, 503, "{\"ok\":false,\"err\":\"vetroperms\"}"); return; }

            UUID targetUuid = uuid;
            String targetRank = rank;
            String[] result = Bukkit.getScheduler().callSyncMethod(plugin, () -> {
                String applied = plugin.getVetroPermsManager().setRank(targetUuid, targetRank);
                Player online = Bukkit.getPlayer(targetUuid);
                String playerName = online != null ? online.getName() : null;
                return new String[]{applied, playerName};
            }).get(5, TimeUnit.SECONDS);
            String applied = result[0];
            String cachedName = result[1] != null ? result[1] : nameFromCache(uuid, name == null ? "" : name);
            updatePlayerCacheRank(uuid, cachedName, applied);
            send(ex, 200, "{\"ok\":true,\"uuid\":\"" + uuid + "\",\"rank\":\"" + esc(applied) + "\",\"name\":\"" + esc(cachedName) + "\"}");
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleAdminPunishmentHistory(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkStaffAccess(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"staff\"}"); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            if (!webMysqlReady) { send(ex, 503, "{\"ok\":false,\"err\":\"mysql\"}"); return; }

            Map<String,String> q = query(ex);
            String search = q.getOrDefault("q", "").trim();
            String type = q.getOrDefault("type", "all").trim().toUpperCase(Locale.ROOT);
            int page = Math.max(1, asInt(q, "page", 1));
            int limit = Math.max(1, Math.min(50, asInt(q, "limit", 12)));
            int offset = (page - 1) * limit;

            StringBuilder where = new StringBuilder(" WHERE 1=1");
            List<Object> params = new ArrayList<>();
            if (type.equals("BAN") || type.equals("MUTE") || type.equals("KICK")) {
                where.append(" AND p.type=?");
                params.add(type);
            }
            if (!search.isBlank()) {
                where.append(" AND (p.uuid LIKE ? OR p.reason LIKE ? OR p.staff LIKE ? OR pc.name LIKE ?)");
                String like = "%" + search + "%";
                params.add(like);
                params.add(like);
                params.add(like);
                params.add(like);
            }

            List<Map<String,Object>> rows = new ArrayList<>();
            int total = 0;
            try (Connection connection = webConnection()) {
                try (PreparedStatement count = connection.prepareStatement("""
                        SELECT COUNT(*)
                        FROM punishments p
                        LEFT JOIN player_cache pc ON pc.uuid=p.uuid
                        """ + where)) {
                    bind(count, params);
                    try (ResultSet rs = count.executeQuery()) {
                        if (rs.next()) total = rs.getInt(1);
                    }
                }
                try (PreparedStatement ps = connection.prepareStatement("""
                        SELECT p.id,p.uuid,p.type,p.reason,p.staff,p.until_time,p.active,p.created_at,pc.name AS player_name
                        FROM punishments p
                        LEFT JOIN player_cache pc ON pc.uuid=p.uuid
                        """ + where + " ORDER BY id DESC LIMIT ? OFFSET ?")) {
                    bind(ps, params);
                    ps.setInt(params.size() + 1, limit);
                    ps.setInt(params.size() + 2, offset);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String,Object> row = new LinkedHashMap<>();
                            row.put("id", rs.getInt("id"));
                            row.put("uuid", rs.getString("uuid"));
                            row.put("playerName", rs.getString("player_name"));
                            row.put("type", rs.getString("type"));
                            row.put("reason", rs.getString("reason"));
                            row.put("staff", rs.getString("staff"));
                            row.put("untilTime", rs.getLong("until_time"));
                            row.put("active", rs.getBoolean("active"));
                            Timestamp created = rs.getTimestamp("created_at");
                            row.put("createdAt", created == null ? 0L : created.getTime());
                            rows.add(row);
                        }
                    }
                }
            }
            int pages = Math.max(1, (int)Math.ceil(total / (double)limit));
            send(ex, 200, "{\"ok\":true,\"items\":" + toJsonObjectList(rows)
                    + ",\"page\":" + page
                    + ",\"pages\":" + pages
                    + ",\"total\":" + total
                    + "}");
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    /** Spieler reicht einen Einspruch gegen seinen aktuell aktiven Bann ein (an den verlinkten Web-Account gebunden). */
    private void handleAppealSubmit(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            if (!webMysqlReady) { send(ex, 503, "{\"ok\":false,\"err\":\"mysql\"}"); return; }

            String username = currentSessionUser(ex);
            if (username == null || username.isBlank()) { send(ex, 401, "{\"ok\":false,\"err\":\"session\"}"); return; }
            Optional<WebUser> user = mysqlUser(username);
            UUID uuid = user.isPresent() ? safeUuid(user.get().uuid()) : null;
            if (uuid == null) { send(ex, 400, "{\"ok\":false,\"err\":\"not-linked\"}"); return; }

            String body = readBody(ex);
            String message = value(query(ex), body, "message");
            if (message == null || message.isBlank()) { send(ex, 400, "{\"ok\":false,\"err\":\"message\"}"); return; }
            message = message.trim();
            if (message.length() > 1000) message = message.substring(0, 1000);

            try (Connection connection = webConnection()) {
                int punishmentId;
                try (PreparedStatement ps = connection.prepareStatement("""
                        SELECT id FROM punishments WHERE uuid=? AND type='BAN' AND active=true ORDER BY id DESC LIMIT 1
                        """)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { send(ex, 400, "{\"ok\":false,\"err\":\"not-banned\"}"); return; }
                        punishmentId = rs.getInt("id");
                    }
                }
                try (PreparedStatement check = connection.prepareStatement(
                        "SELECT COUNT(*) FROM ban_appeals WHERE uuid=? AND status='open'")) {
                    check.setString(1, uuid.toString());
                    try (ResultSet rs = check.executeQuery()) {
                        if (rs.next() && rs.getInt(1) > 0) { send(ex, 409, "{\"ok\":false,\"err\":\"already-open\"}"); return; }
                    }
                }
                try (PreparedStatement insert = connection.prepareStatement("""
                        INSERT INTO ban_appeals (punishment_id, uuid, username, message, status, created_at)
                        VALUES (?, ?, ?, ?, 'open', ?)
                        """, Statement.RETURN_GENERATED_KEYS)) {
                    insert.setInt(1, punishmentId);
                    insert.setString(2, uuid.toString());
                    insert.setString(3, username);
                    insert.setString(4, message);
                    insert.setLong(5, System.currentTimeMillis());
                    insert.executeUpdate();
                    long id = -1L;
                    try (ResultSet keys = insert.getGeneratedKeys()) {
                        if (keys.next()) id = keys.getLong(1);
                    }
                    send(ex, 200, "{\"ok\":true,\"id\":" + id + "}");
                }
            }
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    /** Zeigt dem eingeloggten Spieler seinen aktuellen Bann-Status + den Stand seines juengsten Einspruchs. */
    private void handleAppealsMine(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            if (!webMysqlReady) { send(ex, 503, "{\"ok\":false,\"err\":\"mysql\"}"); return; }

            String username = currentSessionUser(ex);
            if (username == null || username.isBlank()) { send(ex, 401, "{\"ok\":false,\"err\":\"session\"}"); return; }
            Optional<WebUser> user = mysqlUser(username);
            UUID uuid = user.isPresent() ? safeUuid(user.get().uuid()) : null;
            if (uuid == null) { send(ex, 400, "{\"ok\":false,\"err\":\"not-linked\"}"); return; }

            try (Connection connection = webConnection()) {
                Map<String,Object> ban = null;
                try (PreparedStatement ps = connection.prepareStatement("""
                        SELECT id, reason, staff, until_time, created_at
                        FROM punishments WHERE uuid=? AND type='BAN' AND active=true ORDER BY id DESC LIMIT 1
                        """)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            ban = new LinkedHashMap<>();
                            ban.put("id", rs.getInt("id"));
                            ban.put("reason", rs.getString("reason"));
                            ban.put("staff", rs.getString("staff"));
                            ban.put("untilTime", rs.getLong("until_time"));
                            Timestamp created = rs.getTimestamp("created_at");
                            ban.put("createdAt", created == null ? 0L : created.getTime());
                        }
                    }
                }

                Map<String,Object> appeal = null;
                try (PreparedStatement ps = connection.prepareStatement("""
                        SELECT id, status, message, created_at, reviewed_by, reviewed_at, review_note
                        FROM ban_appeals WHERE uuid=? ORDER BY id DESC LIMIT 1
                        """)) {
                    ps.setString(1, uuid.toString());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            appeal = new LinkedHashMap<>();
                            appeal.put("id", rs.getInt("id"));
                            appeal.put("status", rs.getString("status"));
                            appeal.put("message", rs.getString("message"));
                            appeal.put("createdAt", rs.getLong("created_at"));
                            appeal.put("reviewedBy", rs.getString("reviewed_by"));
                            appeal.put("reviewedAt", rs.getLong("reviewed_at"));
                            appeal.put("reviewNote", rs.getString("review_note"));
                        }
                    }
                }

                send(ex, 200, "{\"ok\":true,\"banned\":" + (ban != null)
                        + ",\"ban\":" + (ban == null ? "null" : toJsonObject(ban))
                        + ",\"appeal\":" + (appeal == null ? "null" : toJsonObject(appeal))
                        + "}");
            }
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    /** Staff-Review-Queue fuer offene/entschiedene Ban-Appeals, inkl. der zugehoerigen Bann-Details. */
    private void handleAppealsQueue(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkStaffAccess(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"staff\"}"); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            if (!webMysqlReady) { send(ex, 503, "{\"ok\":false,\"err\":\"mysql\"}"); return; }

            Map<String,String> q = query(ex);
            String status = q.getOrDefault("status", "open").trim().toLowerCase(Locale.ROOT);
            if (!status.equals("open") && !status.equals("approved") && !status.equals("rejected")) status = "open";
            int page = Math.max(1, asInt(q, "page", 1));
            int limit = Math.max(1, Math.min(50, asInt(q, "limit", 20)));
            int offset = (page - 1) * limit;

            List<Map<String,Object>> rows = new ArrayList<>();
            int total = 0;
            try (Connection connection = webConnection()) {
                try (PreparedStatement count = connection.prepareStatement("SELECT COUNT(*) FROM ban_appeals WHERE status=?")) {
                    count.setString(1, status);
                    try (ResultSet rs = count.executeQuery()) { if (rs.next()) total = rs.getInt(1); }
                }
                try (PreparedStatement ps = connection.prepareStatement("""
                        SELECT a.id, a.punishment_id, a.uuid, a.username, a.message, a.status, a.created_at,
                               a.reviewed_by, a.reviewed_at, a.review_note,
                               p.reason AS ban_reason, p.staff AS ban_staff, p.until_time AS ban_until,
                               pc.name AS player_name
                        FROM ban_appeals a
                        LEFT JOIN punishments p ON p.id = a.punishment_id
                        LEFT JOIN player_cache pc ON pc.uuid = a.uuid
                        WHERE a.status = ?
                        ORDER BY a.id DESC
                        LIMIT ? OFFSET ?
                        """)) {
                    ps.setString(1, status);
                    ps.setInt(2, limit);
                    ps.setInt(3, offset);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            Map<String,Object> row = new LinkedHashMap<>();
                            row.put("id", rs.getInt("id"));
                            row.put("uuid", rs.getString("uuid"));
                            row.put("playerName", rs.getString("player_name"));
                            row.put("username", rs.getString("username"));
                            row.put("message", rs.getString("message"));
                            row.put("status", rs.getString("status"));
                            row.put("createdAt", rs.getLong("created_at"));
                            row.put("reviewedBy", rs.getString("reviewed_by"));
                            row.put("reviewedAt", rs.getLong("reviewed_at"));
                            row.put("reviewNote", rs.getString("review_note"));
                            row.put("banReason", rs.getString("ban_reason"));
                            row.put("banStaff", rs.getString("ban_staff"));
                            row.put("banUntil", rs.getLong("ban_until"));
                            rows.add(row);
                        }
                    }
                }
            }
            int pages = Math.max(1, (int)Math.ceil(total / (double)limit));
            send(ex, 200, "{\"ok\":true,\"items\":" + toJsonObjectList(rows)
                    + ",\"page\":" + page
                    + ",\"pages\":" + pages
                    + ",\"total\":" + total
                    + "}");
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    /** Staff genehmigt (-> entbannt direkt in der geteilten punishments-Tabelle) oder lehnt einen Appeal ab. */
    private void handleAppealAction(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkStaffAccess(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"staff\"}"); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            if (!webMysqlReady) { send(ex, 503, "{\"ok\":false,\"err\":\"mysql\"}"); return; }

            String body = readBody(ex);
            Map<String,String> q = query(ex);
            String action = value(q, body, "action");
            long id = parseLong(value(q, body, "id"), -1L);
            String note = value(q, body, "note");
            if (note == null) note = "";
            if (note.length() > 500) note = note.substring(0, 500);
            boolean approve = "approve".equalsIgnoreCase(action);
            boolean reject = "reject".equalsIgnoreCase(action);
            if (id <= 0 || (!approve && !reject)) { send(ex, 400, "{\"ok\":false,\"err\":\"args\"}"); return; }

            String staffName = currentSessionUser(ex);

            try (Connection connection = webConnection()) {
                int punishmentId; String uuid;
                try (PreparedStatement ps = connection.prepareStatement(
                        "SELECT punishment_id, uuid FROM ban_appeals WHERE id=? AND status='open'")) {
                    ps.setLong(1, id);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) { send(ex, 404, "{\"ok\":false,\"err\":\"not-found\"}"); return; }
                        punishmentId = rs.getInt("punishment_id");
                        uuid = rs.getString("uuid");
                    }
                }

                if (approve) {
                    try (PreparedStatement unban = connection.prepareStatement(
                            "UPDATE punishments SET active=false WHERE id=? AND uuid=? AND type='BAN' AND active=true")) {
                        unban.setInt(1, punishmentId);
                        unban.setString(2, uuid);
                        unban.executeUpdate();
                    }
                }

                try (PreparedStatement update = connection.prepareStatement("""
                        UPDATE ban_appeals SET status=?, reviewed_by=?, reviewed_at=?, review_note=?
                        WHERE id=? AND status='open'
                        """)) {
                    update.setString(1, approve ? "approved" : "rejected");
                    update.setString(2, staffName == null ? "web" : staffName);
                    update.setLong(3, System.currentTimeMillis());
                    update.setString(4, note);
                    update.setLong(5, id);
                    boolean changed = update.executeUpdate() > 0;
                    send(ex, 200, "{\"ok\":" + changed + "}");
                }
            }
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void bind(PreparedStatement statement, List<Object> params) throws SQLException {
        for (int i = 0; i < params.size(); i++) {
            Object value = params.get(i);
            if (value instanceof Number n) statement.setLong(i + 1, n.longValue());
            else statement.setString(i + 1, String.valueOf(value));
        }
    }

    private UUID safeUuid(String raw) {
        try { return raw == null || raw.isBlank() ? null : UUID.fromString(raw.trim()); }
        catch (Throwable ignored) { return null; }
    }

    private UUID resolveUuidFromCache(String name) {
        if (name == null || name.isBlank() || !webMysqlReady) return null;
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     SELECT uuid FROM player_cache WHERE LOWER(name)=LOWER(?) ORDER BY last_seen DESC LIMIT 1
                     """)) {
            ps.setString(1, name.trim());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return safeUuid(rs.getString("uuid"));
            }
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
        }
        return null;
    }

    private String nameFromCache(UUID uuid, String fallback) {
        if (uuid == null || !webMysqlReady) return fallback == null ? "" : fallback;
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT name FROM player_cache WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    String name = rs.getString("name");
                    if (name != null && !name.isBlank()) return name;
                }
            }
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
        }
        return fallback == null ? "" : fallback;
    }

    private int cachedCoins(UUID uuid) {
        if (uuid == null || !webMysqlReady) return 0;
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("SELECT coins FROM player_cache WHERE uuid=?")) {
            ps.setString(1, uuid.toString());
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) return Math.max(0, rs.getInt("coins"));
            }
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
        }
        return 0;
    }

    private void updatePlayerCacheRank(UUID uuid, String name, String rank) {
        if (uuid == null || !webMysqlReady) return;
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO player_cache(uuid,name,role,last_seen)
                     VALUES(?,?,?,?)
                     ON DUPLICATE KEY UPDATE
                         name=IF(VALUES(name)='', name, VALUES(name)),
                         role=VALUES(role),
                         last_seen=VALUES(last_seen)
                     """)) {
            ps.setString(1, uuid.toString());
            ps.setString(2, name == null ? "" : name);
            ps.setString(3, rank == null || rank.isBlank() ? "Member" : rank);
            ps.setLong(4, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Throwable t) {
            webMysqlError = t.getMessage();
        }
    }

    private String availableRanksJson() {
        try {
            if (plugin.getVetroPermsManager() != null) {
                return toJsonArray(new ArrayList<>(plugin.getVetroPermsManager().getRankIds()));
            }
        } catch (Throwable ignored) {}
        return toJsonArray(List.of("Member", "Ember", "Vanguard", "Obsidian", "Apex", "Vetro+", "Support", "Builder", "Mod", "Admin"));
    }

    private int countRecentReports(long windowMs) {
        long cutoff = System.currentTimeMillis() - Math.max(1L, windowMs);
        int total = 0;
        try {
            File file = new File(plugin.getDataFolder(), "data/punishments.yml");
            YamlConfiguration yml = YamlConfiguration.loadConfiguration(file);
            org.bukkit.configuration.ConfigurationSection players = yml.getConfigurationSection("players");
            if (players != null) {
                for (String uuid : players.getKeys(false)) {
                    List<Map<String,Object>> entries = (List<Map<String,Object>>) yml.getList("players." + uuid + ".entries", Collections.emptyList());
                    if (entries == null) continue;
                    for (Map<String,Object> entry : entries) {
                        String type = String.valueOf(entry.get("type"));
                        long ts = 0L;
                        try { ts = Long.parseLong(String.valueOf(entry.get("ts"))); } catch (Throwable ignored) {}
                        if ("REPORT".equalsIgnoreCase(type) && ts >= cutoff) total++;
                    }
                }
            }
        } catch (Throwable ignored) {}
        if (webMysqlReady && tableExists("punishments")) {
            try (Connection connection = webConnection();
                 PreparedStatement ps = connection.prepareStatement("""
                         SELECT COUNT(*) FROM punishments WHERE type='REPORT' AND created_at>=?
                         """)) {
                ps.setTimestamp(1, new Timestamp(cutoff));
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) total += rs.getInt(1);
                }
            } catch (Throwable ignored) {}
        }
        return total;
    }

    private List<String> lastLogProblems(int limit) {
        List<String> out = new ArrayList<>();
        File latest = new File(plugin.getServer().getWorldContainer(), "logs/latest.log");
        if (!latest.exists()) latest = new File("logs/latest.log");
        if (!latest.exists()) return out;
        try {
            List<String> lines = java.nio.file.Files.readAllLines(latest.toPath(), StandardCharsets.UTF_8);
            for (int i = lines.size() - 1; i >= 0 && out.size() < Math.max(1, limit); i--) {
                String line = lines.get(i);
                String lower = line.toLowerCase(Locale.ROOT);
                if (lower.contains("exception") || lower.contains("error") || lower.contains("caused by")
                        || lower.contains("[webapi]") && lower.contains("failed")) {
                    out.add(line.length() > 220 ? line.substring(0, 220) : line);
                }
            }
        } catch (Throwable ignored) {}
        Collections.reverse(out);
        return out;
    }

    private void handleProfile(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            Map<String,String> q = query(ex);
            String rawUuid = q.getOrDefault("uuid", "").trim();
            String name = q.getOrDefault("name", "").trim();
            UUID uuid = null;
            if (!rawUuid.isBlank()) {
                try { uuid = UUID.fromString(rawUuid); } catch (IllegalArgumentException ignored) {}
            }
            OfflinePlayer offline = null;
            if (uuid != null) {
                offline = Bukkit.getOfflinePlayer(uuid);
            } else if (!name.isBlank()) {
                offline = Bukkit.getOfflinePlayer(name);
                uuid = offline.getUniqueId();
            }
            if (uuid == null) {
                send(ex, 400, "{\"ok\":false,\"err\":\"name or uuid missing\"}");
                return;
            }
            Player online = Bukkit.getPlayer(uuid);
            if (name.isBlank()) {
                if (online != null) name = online.getName();
                else if (offline != null && offline.getName() != null) name = offline.getName();
            }
            String faction = "";
            try { if (plugin.getFactionManager() != null) faction = plugin.getFactionManager().getFactionOf(uuid); } catch (Throwable ignored) {}
            int coins = 0, kills = 0, deaths = 0, elo = 0, level = 0;
            double kd = 0.0D;
            try { if (plugin.getCoinsManager() != null) coins = plugin.getCoinsManager().getCoins(uuid); } catch (Throwable ignored) {}
            try {
                if (plugin.getStatsManager() != null) {
                    kills = plugin.getStatsManager().getKills(uuid);
                    deaths = plugin.getStatsManager().getDeaths(uuid);
                    elo = plugin.getStatsManager().getElo(uuid);
                    level = plugin.getStatsManager().getLevel(uuid);
                    kd = plugin.getStatsManager().getKDR(uuid);
                }
            } catch (Throwable ignored) {}
            int prestige = prestigeLevel(uuid);
            int prestigeXp = prestigeXp(uuid);
            int xpToNext = prestigeXpToNext(uuid);
            String rank = rankFor(uuid, online);
            send(ex, 200, "{\"ok\":true,\"uuid\":\"" + uuid + "\",\"name\":\"" + esc(name)
                    + "\",\"rank\":\"" + esc(rank) + "\",\"faction\":\"" + esc(faction == null ? "" : faction) + "\",\"coins\":" + coins
                    + ",\"kills\":" + kills + ",\"deaths\":" + deaths + ",\"kd\":" + String.format(Locale.ROOT, "%.4f", kd)
                    + ",\"elo\":" + elo + ",\"level\":" + level
                    + ",\"prestige\":" + prestige + ",\"prestigeXp\":" + prestigeXp
                    + ",\"nextPrestige\":\"" + esc("Prestige " + (prestige + 1)) + "\",\"xpToNext\":" + xpToNext
                    + ",\"raidWins\":0,\"warzoneChests\":0,\"bossKills\":0}");
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private String rankFor(UUID uuid, Player online) {
        if (uuid == null) return "Member";
        try {
            if (online != null && plugin.getVetroPermsManager() != null) {
                return cleanText(plugin.getVetroPermsManager().getRank(online), "Member");
            }
        } catch (Throwable ignored) {}
        try {
            if (plugin.getVetroPermsManager() != null) {
                return cleanText(plugin.getVetroPermsManager().getRank(uuid), "Member");
            }
        } catch (Throwable ignored) {}
        try {
            String configured = plugin.getConfig().getString("perms.users." + uuid + ".rank", "");
            if (configured != null && !configured.isBlank()) return cleanText(configured, "Member");
        } catch (Throwable ignored) {}
        return "Member";
    }

    private int prestigeLevel(UUID uuid) {
        try {
            if (plugin.getPrestigeManager() != null) return plugin.getPrestigeManager().getLevel(uuid);
        } catch (Throwable ignored) {}
        try {
            if (plugin.getStatsManager() != null) return plugin.getStatsManager().getPrestige(uuid);
        } catch (Throwable ignored) {}
        return 0;
    }

    private int prestigeXp(UUID uuid) {
        try {
            if (plugin.getPrestigeManager() != null) return plugin.getPrestigeManager().getXp(uuid);
        } catch (Throwable ignored) {}
        try {
            if (plugin.getStatsManager() != null) return plugin.getStatsManager().getPrestigeXp(uuid);
        } catch (Throwable ignored) {}
        return 0;
    }

    private int prestigeXpToNext(UUID uuid) {
        try {
            if (plugin.getPrestigeManager() != null) return plugin.getPrestigeManager().getXpToNext(uuid);
        } catch (Throwable ignored) {}
        return 0;
    }

    private String cleanText(String value, String fallback) {
        String cleaned = ChatColor.stripColor(value == null ? "" : value).trim();
        return cleaned.isBlank() ? fallback : cleaned;
    }

    private void handleFactionProfile(HttpExchange ex) {
        handleFaction(ex);
    }

    private void handleRaidStatus(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }

            List<Map<String,Object>> raids = new ArrayList<>();
            raids.addAll(tryCollectRaidMaps(plugin.getRaidInstanceService()));
            raids.addAll(tryCollectRaidMaps(plugin.getRaidManager()));
            String j = "{\"ok\":true,\"active\":" + toJsonObjectList(raids) + "}";
            send(ex, 200, j);
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private List<Map<String,Object>> tryCollectRaidMaps(Object src){
        List<Map<String,Object>> out = new ArrayList<>();
        if (src == null) return out;
        for (String m : new String[]{"getActive","listActive","getInstances"}) {
            try {
                Object r = src.getClass().getMethod(m).invoke(src);
                if (r instanceof Collection<?> c) {
                    for (Object o : c) out.add(raidToMap(o));
                    return out;
                }
            } catch (Throwable ignored){}
        }
        return out;
    }

    private Map<String,Object> raidToMap(Object o){
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("id",        tryString(o, "getId", "id"));
        m.put("attacker",  tryString(o, "getAttacker","attackerName","getAttackerName"));
        m.put("defender",  tryString(o, "getDefender","defenderName","getDefenderName"));
        m.put("state",     tryString(o, "getState","state","getPhase"));
        m.put("startedAt", tryLong(o, "getStartedAt","getStartTime","getStartEpoch"));
        return m;
    }

    private void handleLootdropNext(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            Object srv = plugin.getTimedLootChestService();
            Long next = tryLong(srv, "getNextSpawnEpochMs", "getNextEpoch", "nextSpawnAt");
            Integer intervalSec = tryInt(srv, "getIntervalSeconds", "getIntervalSec");
            boolean running = tryBool(srv, "isRunning", "isStarted");
            long now = System.currentTimeMillis();
            long eta = (next != null && next > now) ? (next - now) / 1000L : 0L;

            String j = "{"
                    + "\"ok\":true"
                    + ",\"running\":" + running
                    + ",\"intervalSeconds\":" + (intervalSec == null ? 0 : intervalSec)
                    + ",\"nextEpochMs\":" + (next == null ? 0 : next)
                    + ",\"etaSeconds\":" + eta
                    + "}";
            send(ex, 200, j);
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleContractsActive(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            Map<String,String> q = query(ex);
            int limit = Math.max(1, Math.min(50, asInt(q, "limit", 10)));

            Object cm = plugin.getContractsManager();
            List<Map<String,Object>> out = new ArrayList<>();
            if (cm != null) {
                Collection<?> coll = tryCollection(cm, "listActive", "getActive", "getContracts");
                if (coll != null) {
                    int i=0;
                    for (Object c : coll) {
                        if (i++>=limit) break;
                        Map<String,Object> row = new LinkedHashMap<>();
                        row.put("id",        tryString(c, "getId","id"));
                        row.put("title",     tryString(c, "getTitle","title","getName"));
                        row.put("assignee",  tryString(c, "getAssigneeName","getAssignee","assignee"));
                        row.put("expiresAt", tryLong(c, "getExpiresAt","getDeadlineEpoch","getExpireEpoch"));
                        out.add(row);
                    }
                }
            }
            String j = "{\"ok\":true,\"active\":" + toJsonObjectList(out) + "}";
            send(ex, 200, j);
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleMarketOverview(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            Map<String,String> q = query(ex);
            int limit = Math.max(1, Math.min(50, asInt(q, "limit", 10)));

            Object gm = plugin.getGeneralShopManager();
            List<Map<String,Object>> items = new ArrayList<>();
            if (gm != null) {
                Collection<?> coll = tryCollection(gm, "topSellers", "listItems", "getItems");
                if (coll != null) {
                    int i=0;
                    for (Object it : coll) {
                        if (i++>=limit) break;
                        Map<String,Object> row = new LinkedHashMap<>();
                        row.put("id",    tryString(it, "getId","id","getKey"));
                        row.put("name",  tryString(it, "getName","getDisplayName","toString"));
                        row.put("price", tryDouble(it, "getPrice","getSellPrice","getBuyPrice"));
                        row.put("stock", tryInt(it, "getStock","getAmount","getQty"));
                        items.add(row);
                    }
                }
            }

            String j = "{\"ok\":true,\"items\":" + toJsonObjectList(items) + "}";
            send(ex, 200, j);
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleAdminAuctionRisk(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAuth(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"auth\"}"); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            Map<String,String> q = query(ex);
            int limit = Math.max(1, Math.min(100, asInt(q, "limit", 25)));
            if (plugin.getAuctionHouseManager() == null) {
                send(ex, 503, "{\"ok\":false,\"err\":\"auction unavailable\"}");
                return;
            }
            send(ex, 200, plugin.getAuctionHouseManager().riskDashboardJson(limit));
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleAdminAuctionQuarantine(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAuth(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"auth\"}"); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            if (plugin.getAuctionHouseManager() == null) {
                send(ex, 503, "{\"ok\":false,\"err\":\"auction unavailable\"}");
                return;
            }
            send(ex, 200, plugin.getAuctionHouseManager().quarantineQueueJson(Math.max(1, Math.min(100, asInt(query(ex), "limit", 50)))));
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleAdminAuctionSeller(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAuth(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"auth\"}"); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            UUID seller = UUID.fromString(query(ex).getOrDefault("seller", ""));
            if (plugin.getAuctionHouseManager() == null) {
                send(ex, 503, "{\"ok\":false,\"err\":\"auction unavailable\"}");
                return;
            }
            send(ex, 200, plugin.getAuctionHouseManager().sellerProfileJson(seller));
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleAdminAuctionAction(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAuth(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"auth\"}"); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            if (plugin.getAuctionHouseManager() == null) {
                send(ex, 503, "{\"ok\":false,\"err\":\"auction unavailable\"}");
                return;
            }
            String body = readBody(ex);
            Map<String,String> q = query(ex);
            String action = q.getOrDefault("action", jsonGet(body, "action"));
            String actor = q.getOrDefault("actor", jsonGet(body, "actor"));
            if (actor == null || actor.isBlank()) actor = "web";
            boolean ok = false;
            int deleted = 0;
            if ("quarantine".equalsIgnoreCase(action)) {
                ok = plugin.getAuctionHouseManager().webSetQuarantine(value(q, body, "id"), true, actor);
            } else if ("unquarantine".equalsIgnoreCase(action)) {
                ok = plugin.getAuctionHouseManager().webSetQuarantine(value(q, body, "id"), false, actor);
            } else if ("delete".equalsIgnoreCase(action)) {
                ok = plugin.getAuctionHouseManager().webDeleteListing(value(q, body, "id"), actor);
            } else if ("deleteSeller".equalsIgnoreCase(action)) {
                deleted = plugin.getAuctionHouseManager().webDeleteSeller(UUID.fromString(value(q, body, "seller")), actor);
                ok = deleted > 0;
            } else if ("threshold".equalsIgnoreCase(action)) {
                ok = plugin.getAuctionHouseManager().webSetThreshold(value(q, body, "key"), value(q, body, "value"), actor);
            }
            send(ex, 200, "{\"ok\":" + ok + ",\"deleted\":" + deleted + "}");
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleAdminShopEditor(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAuth(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"auth\"}"); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            send(ex, 200, shopEditorJson());
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleAdminShopAction(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAuth(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"auth\"}"); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            String body = readBody(ex);
            Map<String,String> q = query(ex);
            String action = value(q, body, "action");
            String actor = Optional.ofNullable(value(q, body, "actor")).filter(s -> !s.isBlank()).orElse("web");
            boolean ok = false;
            if ("setPrice".equalsIgnoreCase(action)) {
                String id = value(q, body, "id");
                double buy = parseDouble(value(q, body, "buy"), plugin.getConfig().getDouble("shop.prices." + id + ".buy", 10.0));
                double sell = parseDouble(value(q, body, "sell"), plugin.getConfig().getDouble("shop.prices." + id + ".sell", 5.0));
                ok = setShopPrice(id, buy, sell, actor);
            } else if ("moveItem".equalsIgnoreCase(action)) {
                ok = moveShopItem(value(q, body, "id"), value(q, body, "category"), actor);
            } else if ("bulkPercent".equalsIgnoreCase(action)) {
                ok = bulkShopAdjust(parseDouble(value(q, body, "percent"), 0.0), 0.0, value(q, body, "category"), actor);
            } else if ("bulkFlat".equalsIgnoreCase(action)) {
                ok = bulkShopAdjust(0.0, parseDouble(value(q, body, "delta"), 0.0), value(q, body, "category"), actor);
            }
            plugin.saveConfig();
            send(ex, 200, "{\"ok\":" + ok + "}");
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleAdminRaidShopEditor(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAuth(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"auth\"}"); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            send(ex, 200, raidShopEditorJson());
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleAdminRaidShopAudit(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAuth(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"auth\"}"); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            int limit = Math.max(1, Math.min(150, parseInt(query(ex).get("limit"), 40)));
            send(ex, 200, raidShopAuditJson(limit));
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleAdminRaidShopAction(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAuth(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"auth\"}"); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }
            String body = readBody(ex);
            Map<String,String> q = query(ex);
            String action = value(q, body, "action");
            String id = value(q, body, "id");
            boolean ok = false;
            if ("setPrice".equalsIgnoreCase(action)) {
                int price = Math.max(1, parseInt(value(q, body, "price"), raidShopCost(id)));
                plugin.getConfig().set("raid.shop.costs." + id, price);
                ok = true;
            } else if ("setEnabled".equalsIgnoreCase(action)) {
                boolean enabled = Boolean.parseBoolean(value(q, body, "enabled"));
                plugin.getConfig().set("raid.shop.offers." + id + ".enabled", enabled);
                ok = true;
            } else if ("createOffer".equalsIgnoreCase(action)) {
                String safeId = safeUser(id);
                String category = orDefault(value(q, body, "category"), "UTILITY").toUpperCase(Locale.ROOT);
                String material = orDefault(value(q, body, "material"), "STONE").toUpperCase(Locale.ROOT);
                String rewardType = orDefault(value(q, body, "rewardType"), "ITEM").toUpperCase(Locale.ROOT);
                String rewardValue = orDefault(value(q, body, "rewardValue"), "");
                int amount = Math.max(1, parseInt(value(q, body, "amount"), 1));
                int price = Math.max(1, parseInt(value(q, body, "price"), 3));
                if (!safeId.isBlank()
                        && Set.of("ATTACK", "DEFENSE", "MOBILITY", "UTILITY").contains(category)
                        && Set.of("ITEM", "POTION", "BREACH_CHARGE", "BUNDLE", "ENERGY", "FACTION_ELO", "COMMAND", "KIT").contains(rewardType)
                        && org.bukkit.Material.matchMaterial(material) != null) {
                    plugin.getConfig().set("raid.shop.customOffers." + safeId + ".category", category);
                    plugin.getConfig().set("raid.shop.customOffers." + safeId + ".material", material);
                    plugin.getConfig().set("raid.shop.customOffers." + safeId + ".amount", amount);
                    plugin.getConfig().set("raid.shop.customOffers." + safeId + ".rewardType", rewardType);
                    plugin.getConfig().set("raid.shop.customOffers." + safeId + ".rewardValue", rewardValue == null ? "" : rewardValue);
                    plugin.getConfig().set("raid.shop.costs." + safeId, price);
                    plugin.getConfig().set("raid.shop.offers." + safeId + ".enabled", true);
                    id = safeId;
                    ok = true;
                }
            } else if ("deleteOffer".equalsIgnoreCase(action)) {
                String safeId = safeUser(id);
                if (!safeId.isBlank() && plugin.getConfig().isConfigurationSection("raid.shop.customOffers." + safeId)) {
                    plugin.getConfig().set("raid.shop.customOffers." + safeId, null);
                    plugin.getConfig().set("raid.shop.costs." + safeId, null);
                    plugin.getConfig().set("raid.shop.offers." + safeId, null);
                    deleteRaidShopOffer(safeId);
                    id = safeId;
                    ok = true;
                }
            }
            if (ok) {
                plugin.saveConfig();
                if (!"deleteOffer".equalsIgnoreCase(action)) mirrorRaidShopOffer(id);
            }
            send(ex, 200, "{\"ok\":" + ok + "}");
        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false,\"err\":\""+esc(String.valueOf(t))+"\"}"); } catch (Throwable ignored) {}
        }
    }

    private void handleMysticStorm(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            var svc = plugin.getMysticStormService();
            List<Map<String,Object>> worlds = new ArrayList<>();
            for (World w : Bukkit.getWorlds()) {
                Map<String,Object> row = new LinkedHashMap<>();
                boolean active = (svc != null && svc.isActive(w));
                Long ends = tryLong(svc, "getEndsAtEpochMs", "getEndEpochMs", "getNextEndEpochMs", w);
                Long starts = tryLong(svc, "getStartsAtEpochMs", "getStartEpochMs", "getNextStartEpochMs", w);
                row.put("world", w.getName());
                row.put("active", active);
                row.put("startEpochMs", starts == null ? 0 : starts);
                row.put("endEpochMs", ends == null ? 0 : ends);
                worlds.add(row);
            }
            String j = "{\"ok\":true,\"worlds\":" + toJsonObjectList(worlds) + "}";
            send(ex, 200, j);
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    /* ================= SSE: /api/stream/warzone ================= */

    private void handleStreamWarzone(HttpExchange ex) {
        try {
            addCors(ex);
            if (!checkAuth(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"auth\"}"); return; }
            if (!"GET".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }

            ex.getResponseHeaders().set("Content-Type", "text/event-stream; charset=utf-8");
            ex.getResponseHeaders().set("Cache-Control", "no-cache");
            ex.getResponseHeaders().set("Connection", "keep-alive");
            ex.sendResponseHeaders(200, 0); // chunked

            OutputStream os = ex.getResponseBody();
            final boolean[] open = {true};

            // einfacher Pusher im Hintergrund (alle 2s)
            ScheduledExecutorService ses = Executors.newSingleThreadScheduledExecutor();
            ses.scheduleAtFixedRate(() -> {
                if (!open[0]) return;
                try {
                    var rm = plugin.getWarzoneRegionManager();
                    List<String> players = new ArrayList<>();
                    if (rm != null) {
                        for (Player p : Bukkit.getOnlinePlayers()) {
                            try { if (rm.isWarzone(p.getLocation())) players.add(p.getName()); } catch (Throwable ignored) {}
                        }
                    }
                    String payload = "{\"ts\":" + System.currentTimeMillis() + ",\"count\":" + players.size()
                            + ",\"players\":" + toJsonArray(players) + "}";
                    String evt = "event: warzone\n" + "data: " + payload + "\n\n";
                    os.write(evt.getBytes(StandardCharsets.UTF_8));
                    os.flush();
                } catch (Throwable t) {
                    open[0] = false;
                    try { os.close(); } catch (IOException ignored) {}
                    ses.shutdownNow();
                }
            }, 0, 2, TimeUnit.SECONDS);

            // Timeout/cleanup nach 2 Minuten
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (open[0]) {
                    open[0] = false;
                    try { os.close(); } catch (IOException ignored) {}
                    ses.shutdownNow();
                }
            }, 20L * 120);

        } catch (Throwable t) {
            try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {}
        }
    }

    /* ================= Admin (POST) ================= */

    private void handleAdminBroadcast(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAuth(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"auth\"}"); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }

            String body = readBody(ex);
            Map<String,String> q = query(ex);
            String msg = q.getOrDefault("msg", jsonGet(body, "message"));
            if (msg == null || msg.isBlank()) { send(ex, 400, "{\"ok\":false,\"err\":\"message missing\"}"); return; }

            // Versuche BroadcastManager, sonst Bukkit.broadcast
            boolean ok = false;
            try {
                var bm = plugin.getBroadcastManager();
                if (bm != null) {
                    bm.broadcast(msg);
                    ok = true;
                }
            } catch (Throwable ignored) {}
            if (!ok) Bukkit.getServer().broadcastMessage(msg);

            send(ex, 200, "{\"ok\":true}");
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private void handleAdminFlare(HttpExchange ex) {
        try {
            addCors(ex);
            if ("OPTIONS".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 204, ""); return; }
            if (!checkAuth(ex)) { send(ex, 401, "{\"ok\":false,\"err\":\"auth\"}"); return; }
            if (!"POST".equalsIgnoreCase(ex.getRequestMethod())) { send(ex, 405, "{\"ok\":false}"); return; }

            Map<String,String> q = query(ex);
            String worldName = q.getOrDefault("world", "world");
            World w = Bukkit.getWorld(worldName);
            if (w == null) { send(ex, 400, "{\"ok\":false,\"err\":\"world not found\"}"); return; }

            double x = parseDouble(q.get("x"), w.getSpawnLocation().getX());
            double y = parseDouble(q.get("y"), w.getSpawnLocation().getY());
            double z = parseDouble(q.get("z"), w.getSpawnLocation().getZ());
            String rarity = q.getOrDefault("rarity", "RARE").toUpperCase(Locale.ROOT);

            boolean ok = false;
            Location at = new Location(w, x, y, z);

            // 1) LootDropManager API (vermutete Signaturen)
            Object ldm = plugin.getLootDropManager();
            ok = tryFlareViaLootDropManager(ldm, at, rarity);

            // 2) TimedLootChestService Fallback: sofort spawnen
            if (!ok) {
                Object tls = plugin.getTimedLootChestService();
                if (tls != null) {
                    for (String m : new String[]{"forceSpawnNowAt","forceSpawnNow","spawnNow"}) {
                        try {
                            if ("forceSpawnNowAt".equals(m)) {
                                var mm = tls.getClass().getMethod(m, Location.class);
                                mm.invoke(tls, at); ok = true; break;
                            } else {
                                var mm = tls.getClass().getMethod(m);
                                mm.invoke(tls); ok = true; break;
                            }
                        } catch (Throwable ignored){}
                    }
                }
            }

            send(ex, 200, "{\"ok\":" + ok + "}");
        } catch (Throwable t) { try { send(ex, 500, "{\"ok\":false}"); } catch (Throwable ignored) {} }
    }

    private boolean tryFlareViaLootDropManager(Object ldm, Location at, String rarityUpper){
        if (ldm == null) return false;
        try {
            // Suche enum Rarity in unserem Paket
            Class<?> rar = Class.forName("com.vetroforge.items.Rarity");
            Object rVal;
            try { rVal = Enum.valueOf((Class<? extends Enum>) rar, rarityUpper); }
            catch (Exception e){ rVal = Enum.valueOf((Class<? extends Enum>) rar, "RARE"); }

            // Versuche gängige Methoden
            for (String m : new String[]{"spawnFlare","spawnAt","spawnLootAt"}) {
                try {
                    var mm = ldm.getClass().getMethod(m, Location.class, rar);
                    mm.invoke(ldm, at, rVal);
                    return true;
                } catch (NoSuchMethodException ignored) {}
            }
            // Notfalls ohne Rarity
            for (String m : new String[]{"spawnFlare","spawnAt","spawnLootAt"}) {
                try {
                    var mm = ldm.getClass().getMethod(m, Location.class);
                    mm.invoke(ldm, at);
                    return true;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable ignored){}
        return false;
    }

    private String shopEditorJson() {
        StringBuilder json = new StringBuilder("{\"ok\":true,\"categories\":[");
        org.bukkit.configuration.ConfigurationSection categories = plugin.getConfig().getConfigurationSection("shop.categories");
        int catCount = 0;
        if (categories != null) {
            for (String category : categories.getKeys(false)) {
                if (catCount++ > 0) json.append(',');
                List<String> ids = plugin.getConfig().getStringList("shop.categories." + category + ".items");
                json.append('{')
                        .append("\"id\":\"").append(esc(category)).append("\",")
                        .append("\"name\":\"").append(esc(plugin.getConfig().getString("shop.categories." + category + ".name", category))).append("\",")
                        .append("\"icon\":\"").append(esc(plugin.getConfig().getString("shop.categories." + category + ".icon", "CHEST"))).append("\",")
                        .append("\"items\":[");
                int itemCount = 0;
                for (String id : ids) {
                    if (!plugin.getConfig().isConfigurationSection("shop.general.items." + id)) continue;
                    if (itemCount++ > 0) json.append(',');
                    json.append(shopItemJson(id));
                }
                json.append("]}");
            }
        }
        return json.append("],\"history\":").append(shopPriceHistoryJson(80)).append("}").toString();
    }

    private String shopItemJson(String id) {
        String base = "shop.general.items." + id + ".";
        double buy = plugin.getConfig().getDouble("shop.prices." + id + ".buy", 10.0);
        double sell = plugin.getConfig().getDouble("shop.prices." + id + ".sell", 5.0);
        return "{"
                + "\"id\":\"" + esc(id) + "\""
                + ",\"name\":\"" + esc(plugin.getConfig().getString(base + "name", id)) + "\""
                + ",\"material\":\"" + esc(plugin.getConfig().getString(base + "material", "STONE")) + "\""
                + ",\"amount\":" + Math.max(1, plugin.getConfig().getInt(base + "amount", 1))
                + ",\"buy\":" + buy
                + ",\"sell\":" + sell
                + "}";
    }

    private String raidShopEditorJson() {
        StringBuilder json = new StringBuilder("{\"ok\":true,\"mysql\":")
                .append(webMysqlReady)
                .append(",\"source\":\"config+mysql-mirror\",\"categories\":[");
        String[] categories = {"ATTACK", "DEFENSE", "MOBILITY", "UTILITY"};
        for (int i = 0; i < categories.length; i++) {
            if (i > 0) json.append(',');
            String category = categories[i];
            json.append("{\"id\":\"").append(category).append("\",\"items\":[");
            int count = 0;
            for (RaidShopOffer offer : raidShopDefaults()) {
                if (!offer.category().equals(category)) continue;
                if (count++ > 0) json.append(',');
                json.append(raidShopOfferJson(offer));
            }
            json.append("]}");
        }
        return json.append("]}").toString();
    }

    private String raidShopOfferJson(RaidShopOffer offer) {
        boolean custom = plugin.getConfig().isConfigurationSection("raid.shop.customOffers." + offer.id());
        return "{"
                + "\"id\":\"" + esc(offer.id()) + "\""
                + ",\"category\":\"" + esc(offer.category()) + "\""
                + ",\"material\":\"" + esc(offer.material()) + "\""
                + ",\"amount\":" + offer.amount()
                + ",\"rewardType\":\"" + esc(offer.rewardType()) + "\""
                + ",\"rewardValue\":\"" + esc(offer.rewardValue()) + "\""
                + ",\"custom\":" + custom
                + ",\"price\":" + raidShopCost(offer.id())
                + ",\"enabled\":" + raidShopEnabled(offer.id())
                + "}";
    }

    private String raidShopAuditJson(int limit) {
        File file = new File(plugin.getDataFolder(), "data/raidshop-purchases.log");
        List<String> lines = new ArrayList<>();
        if (file.isFile()) {
            try (BufferedReader reader = new BufferedReader(new FileReader(file))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (!line.isBlank()) lines.add(line);
                }
            } catch (IOException ignored) {
            }
        }
        int start = Math.max(0, lines.size() - limit);
        StringBuilder json = new StringBuilder("{\"ok\":true,\"items\":[");
        int count = 0;
        for (int i = lines.size() - 1; i >= start; i--) {
            String[] parts = lines.get(i).split(",", 6);
            if (parts.length < 6) continue;
            if (count++ > 0) json.append(',');
            json.append('{')
                    .append("\"time\":").append(parseLong(parts[0], 0L)).append(',')
                    .append("\"uuid\":\"").append(esc(parts[1])).append("\",")
                    .append("\"player\":\"").append(esc(parts[2])).append("\",")
                    .append("\"offer\":\"").append(esc(parts[3])).append("\",")
                    .append("\"price\":").append(parseInt(parts[4], 0)).append(',')
                    .append("\"balanceAfter\":").append(parseInt(parts[5], 0))
                    .append('}');
        }
        return json.append("]}").toString();
    }

    private int raidShopCost(String id) {
        int def = switch (id == null ? "" : id) {
            case "breach2" -> 8;
            case "breach4" -> 14;
            case "siege_bundle" -> 10;
            case "cannon_bundle" -> 12;
            case "tank_bundle" -> 9;
            case "water_kit" -> 5;
            case "mobility_bundle" -> 7;
            case "slow_falling" -> 4;
            case "raid_energy_1" -> 12;
            case "faction_elo_50" -> 10;
            case "scout_bundle" -> 8;
            case "obsidian_64", "pearls_16", "speed2", "strength2", "creeper_eggs_2" -> 3;
            case "tnt_64", "fire_resistance" -> 4;
            case "gapples_2" -> 5;
            default -> 3;
        };
        return Math.max(1, plugin.getConfig().getInt("raid.shop.costs." + id, def));
    }

    private boolean raidShopEnabled(String id) {
        return plugin.getConfig().getBoolean("raid.shop.offers." + id + ".enabled", true);
    }

    private List<RaidShopOffer> raidShopDefaults() {
        List<RaidShopOffer> offers = new ArrayList<>(List.of(
                new RaidShopOffer("breach2", "ATTACK", "TNT", 2, "BREACH_CHARGE", ""),
                new RaidShopOffer("breach4", "ATTACK", "TNT", 4, "BREACH_CHARGE", ""),
                new RaidShopOffer("tnt_64", "ATTACK", "TNT", 64, "ITEM", ""),
                new RaidShopOffer("cannon_bundle", "ATTACK", "DISPENSER", 1, "BUNDLE", "TNT:64,FIRE_CHARGE:8,REDSTONE:32,REPEATER:16,DISPENSER:4"),
                new RaidShopOffer("siege_bundle", "ATTACK", "TNT_MINECART", 1, "BUNDLE", "TNT:64,OBSIDIAN:32,ENDER_PEARL:8"),
                new RaidShopOffer("strength2", "ATTACK", "BLAZE_POWDER", 1, "POTION", "strength2_short"),
                new RaidShopOffer("creeper_eggs_2", "ATTACK", "CREEPER_SPAWN_EGG", 2, "ITEM", ""),
                new RaidShopOffer("obsidian_64", "DEFENSE", "OBSIDIAN", 64, "ITEM", ""),
                new RaidShopOffer("tank_bundle", "DEFENSE", "SHIELD", 1, "BUNDLE", "OBSIDIAN:64,GOLDEN_APPLE:4,COBWEB:16"),
                new RaidShopOffer("water_kit", "DEFENSE", "WATER_BUCKET", 1, "BUNDLE", "WATER_BUCKET:2,LAVA_BUCKET:1,OBSIDIAN:32"),
                new RaidShopOffer("gapples_2", "DEFENSE", "GOLDEN_APPLE", 2, "ITEM", ""),
                new RaidShopOffer("fire_resistance", "DEFENSE", "MAGMA_CREAM", 1, "POTION", "fire_resistance"),
                new RaidShopOffer("pearls_16", "MOBILITY", "ENDER_PEARL", 16, "ITEM", ""),
                new RaidShopOffer("mobility_bundle", "MOBILITY", "ENDER_PEARL", 1, "BUNDLE", "ENDER_PEARL:16,COOKED_BEEF:16"),
                new RaidShopOffer("speed2", "MOBILITY", "SUGAR", 1, "POTION", "speed2_long"),
                new RaidShopOffer("slow_falling", "MOBILITY", "FEATHER", 1, "POTION", "slow_falling"),
                new RaidShopOffer("obsidian_64", "UTILITY", "OBSIDIAN", 64, "ITEM", ""),
                new RaidShopOffer("raid_energy_1", "UTILITY", "EXPERIENCE_BOTTLE", 1, "ENERGY", "1"),
                new RaidShopOffer("faction_elo_50", "UTILITY", "EMERALD", 50, "FACTION_ELO", "50"),
                new RaidShopOffer("scout_bundle", "UTILITY", "SPYGLASS", 1, "BUNDLE", "SPYGLASS:1,ENDER_PEARL:8,COOKED_BEEF:16"),
                new RaidShopOffer("fire_resistance", "UTILITY", "MAGMA_CREAM", 1, "POTION", "fire_resistance"),
                new RaidShopOffer("gapples_2", "UTILITY", "GOLDEN_APPLE", 2, "ITEM", "")
        ));
        org.bukkit.configuration.ConfigurationSection custom = plugin.getConfig().getConfigurationSection("raid.shop.customOffers");
        if (custom != null) {
            for (String id : custom.getKeys(false)) {
                offers.add(new RaidShopOffer(
                        id,
                        plugin.getConfig().getString("raid.shop.customOffers." + id + ".category", "UTILITY"),
                        plugin.getConfig().getString("raid.shop.customOffers." + id + ".material", "STONE"),
                        Math.max(1, plugin.getConfig().getInt("raid.shop.customOffers." + id + ".amount", 1)),
                        plugin.getConfig().getString("raid.shop.customOffers." + id + ".rewardType", "ITEM"),
                        plugin.getConfig().getString("raid.shop.customOffers." + id + ".rewardValue", "")
                ));
            }
        }
        return offers;
    }

    private void mirrorRaidShopOffer(String id) {
        if (!webMysqlReady || id == null || id.isBlank()) return;
        RaidShopOffer offer = raidShopDefaults().stream()
                .filter(candidate -> candidate.id().equals(id))
                .findFirst()
                .orElse(new RaidShopOffer(id, "", "", 1, "ITEM", ""));
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("""
                     INSERT INTO raid_shop_offers(id, category, material, amount, reward_type, reward_value, price, enabled, updated_at)
                     VALUES(?,?,?,?,?,?,?,?,?)
                     ON DUPLICATE KEY UPDATE
                       category=VALUES(category),
                       material=VALUES(material),
                       amount=VALUES(amount),
                       reward_type=VALUES(reward_type),
                       reward_value=VALUES(reward_value),
                       price=VALUES(price),
                       enabled=VALUES(enabled),
                       updated_at=VALUES(updated_at)
                     """)) {
            ps.setString(1, id);
            ps.setString(2, offer.category());
            ps.setString(3, offer.material());
            ps.setInt(4, offer.amount());
            ps.setString(5, offer.rewardType());
            ps.setString(6, offer.rewardValue());
            ps.setInt(7, raidShopCost(id));
            ps.setBoolean(8, raidShopEnabled(id));
            ps.setLong(9, System.currentTimeMillis());
            ps.executeUpdate();
        } catch (Throwable ignored) {
        }
    }

    private void deleteRaidShopOffer(String id) {
        if (!webMysqlReady || id == null || id.isBlank()) return;
        try (Connection connection = webConnection();
             PreparedStatement ps = connection.prepareStatement("DELETE FROM raid_shop_offers WHERE id=?")) {
            ps.setString(1, id);
            ps.executeUpdate();
        } catch (Throwable ignored) {
        }
    }

    private boolean setShopPrice(String id, double buy, double sell, String actor) {
        if (id == null || id.isBlank() || !plugin.getConfig().isConfigurationSection("shop.general.items." + id)) return false;
        double oldBuy = plugin.getConfig().getDouble("shop.prices." + id + ".buy", 10.0);
        double oldSell = plugin.getConfig().getDouble("shop.prices." + id + ".sell", 5.0);
        plugin.getConfig().set("shop.prices." + id + ".buy", Math.max(1.0, buy));
        plugin.getConfig().set("shop.prices." + id + ".sell", Math.max(1.0, sell));
        logShopPrice(actor, id, oldBuy, oldSell, Math.max(1.0, buy), Math.max(1.0, sell), "setPrice");
        return true;
    }

    private boolean moveShopItem(String id, String category, String actor) {
        if (id == null || id.isBlank() || category == null || category.isBlank()) return false;
        if (!plugin.getConfig().isConfigurationSection("shop.general.items." + id)) return false;
        String target = safeUser(category);
        if (target.isBlank()) return false;
        if (!plugin.getConfig().isConfigurationSection("shop.categories." + target)) {
            plugin.getConfig().set("shop.categories." + target + ".name", "&f" + target);
            plugin.getConfig().set("shop.categories." + target + ".icon", "CHEST");
        }
        org.bukkit.configuration.ConfigurationSection categories = plugin.getConfig().getConfigurationSection("shop.categories");
        if (categories != null) {
            for (String existing : categories.getKeys(false)) {
                List<String> items = new ArrayList<>(plugin.getConfig().getStringList("shop.categories." + existing + ".items"));
                if (items.removeIf(item -> item.equalsIgnoreCase(id))) {
                    plugin.getConfig().set("shop.categories." + existing + ".items", items);
                }
            }
        }
        List<String> targetItems = new ArrayList<>(plugin.getConfig().getStringList("shop.categories." + target + ".items"));
        if (!targetItems.contains(id)) targetItems.add(id);
        plugin.getConfig().set("shop.categories." + target + ".items", targetItems);
        logShopAction(actor, "moveItem", id + "->" + target);
        return true;
    }

    private boolean bulkShopAdjust(double percent, double delta, String category, String actor) {
        List<String> ids = new ArrayList<>();
        if (category != null && !category.isBlank() && !"all".equalsIgnoreCase(category)) {
            ids.addAll(plugin.getConfig().getStringList("shop.categories." + safeUser(category) + ".items"));
        } else {
            org.bukkit.configuration.ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop.general.items");
            if (section != null) ids.addAll(section.getKeys(false));
        }
        for (String id : ids) {
            if (!plugin.getConfig().isConfigurationSection("shop.general.items." + id)) continue;
            double oldBuy = plugin.getConfig().getDouble("shop.prices." + id + ".buy", 10.0);
            double oldSell = plugin.getConfig().getDouble("shop.prices." + id + ".sell", 5.0);
            double nextBuy = Math.max(1.0, oldBuy + (percent == 0.0 ? delta : oldBuy * percent));
            double nextSell = Math.max(1.0, oldSell + (percent == 0.0 ? delta : oldSell * percent));
            plugin.getConfig().set("shop.prices." + id + ".buy", nextBuy);
            plugin.getConfig().set("shop.prices." + id + ".sell", nextSell);
            logShopPrice(actor, id, oldBuy, oldSell, nextBuy, nextSell, percent == 0.0 ? "bulkFlat" : "bulkPercent");
        }
        return !ids.isEmpty();
    }

    private void logShopPrice(String actor, String id, double oldBuy, double oldSell, double newBuy, double newSell, String action) {
        YamlConfiguration history = shopHistory();
        String key = String.valueOf(System.currentTimeMillis()) + "-" + randomToken(3);
        history.set("entries." + key + ".actor", actor);
        history.set("entries." + key + ".action", action);
        history.set("entries." + key + ".item", id);
        history.set("entries." + key + ".oldBuy", oldBuy);
        history.set("entries." + key + ".oldSell", oldSell);
        history.set("entries." + key + ".newBuy", newBuy);
        history.set("entries." + key + ".newSell", newSell);
        history.set("entries." + key + ".created", System.currentTimeMillis());
        saveShopHistory(history);
    }

    private void logShopAction(String actor, String action, String detail) {
        YamlConfiguration history = shopHistory();
        String key = String.valueOf(System.currentTimeMillis()) + "-" + randomToken(3);
        history.set("entries." + key + ".actor", actor);
        history.set("entries." + key + ".action", action);
        history.set("entries." + key + ".detail", detail);
        history.set("entries." + key + ".created", System.currentTimeMillis());
        saveShopHistory(history);
    }

    private YamlConfiguration shopHistory() {
        return YamlConfiguration.loadConfiguration(new File(plugin.getDataFolder(), "data/shop-price-history.yml"));
    }

    private void saveShopHistory(YamlConfiguration history) {
        try {
            File file = new File(plugin.getDataFolder(), "data/shop-price-history.yml");
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            history.save(file);
        } catch (IOException ignored) {}
    }

    private String shopPriceHistoryJson(int limit) {
        YamlConfiguration history = shopHistory();
        org.bukkit.configuration.ConfigurationSection entries = history.getConfigurationSection("entries");
        if (entries == null) return "[]";
        List<String> keys = new ArrayList<>(entries.getKeys(false));
        keys.sort(Comparator.reverseOrder());
        StringBuilder json = new StringBuilder("[");
        int count = 0;
        for (String key : keys) {
            if (count >= limit) break;
            if (count++ > 0) json.append(',');
            String root = "entries." + key + ".";
            json.append('{')
                    .append("\"id\":\"").append(esc(key)).append("\",")
                    .append("\"actor\":\"").append(esc(history.getString(root + "actor", "web"))).append("\",")
                    .append("\"action\":\"").append(esc(history.getString(root + "action", ""))).append("\",")
                    .append("\"item\":\"").append(esc(history.getString(root + "item", ""))).append("\",")
                    .append("\"detail\":\"").append(esc(history.getString(root + "detail", ""))).append("\",")
                    .append("\"oldBuy\":").append(history.getDouble(root + "oldBuy", 0.0)).append(',')
                    .append("\"oldSell\":").append(history.getDouble(root + "oldSell", 0.0)).append(',')
                    .append("\"newBuy\":").append(history.getDouble(root + "newBuy", 0.0)).append(',')
                    .append("\"newSell\":").append(history.getDouble(root + "newSell", 0.0)).append(',')
                    .append("\"created\":").append(history.getLong(root + "created", 0L))
                    .append('}');
        }
        return json.append(']').toString();
    }

    private String factionHistoryJson(String type, String faction) {
        File file = new File(plugin.getDataFolder(), "data/faction-" + type + "-history.yml");
        YamlConfiguration history = YamlConfiguration.loadConfiguration(file);
        org.bukkit.configuration.ConfigurationSection entries = history.getConfigurationSection("factions." + faction);
        if (entries == null) return "[]";
        List<String> keys = new ArrayList<>(entries.getKeys(false));
        keys.sort(Comparator.reverseOrder());
        StringBuilder json = new StringBuilder("[");
        int count = 0;
        for (String key : keys) {
            if (count++ >= 30) break;
            if (count > 1) json.append(',');
            String root = "factions." + faction + "." + key + ".";
            json.append('{')
                    .append("\"id\":\"").append(esc(key)).append("\",")
                    .append("\"type\":\"").append(esc(type)).append("\",")
                    .append("\"value\":").append(history.getInt(root + "value", history.getInt(root + "elo", 0))).append(',')
                    .append("\"detail\":\"").append(esc(history.getString(root + "detail", ""))).append("\",")
                    .append("\"created\":").append(history.getLong(root + "created", 0L))
                    .append('}');
        }
        return json.append(']').toString();
    }

    private static double parseDouble(String s, double def){
        try { return Double.parseDouble(s); } catch (Exception e){ return def; }
    }

    /* ================= JSON helpers ================= */

    private static String toJsonArray(List<String> items) {
        StringBuilder b = new StringBuilder(2 + items.size() * 16);
        b.append('[');
        for (int i = 0; i < items.size(); i++) {
            if (i > 0) b.append(',');
            b.append('"').append(esc(items.get(i))).append('"');
        }
        b.append(']');
        return b.toString();
    }

    private static String toJsonObjectArray(List<String> pairs, String leftKey, String rightKey) {
        StringBuilder b = new StringBuilder(2 + pairs.size() * 24);
        b.append('[');
        for (int i=0;i<pairs.size();i++) {
            if (i>0) b.append(',');
            String p = pairs.get(i);
            int idx = p.lastIndexOf(':');
            String left = (idx >= 0) ? p.substring(0, idx) : p;
            String right = (idx >= 0) ? p.substring(idx + 1) : "";
            boolean isBool = "true".equalsIgnoreCase(right) || "false".equalsIgnoreCase(right);
            b.append('{')
                    .append('"').append(esc(leftKey)).append('"').append(':').append('"').append(esc(left)).append('"').append(',')
                    .append('"').append(esc(rightKey)).append('"').append(':')
                    .append(isBool ? right.toLowerCase(Locale.ROOT) : tryNumber(right))
                    .append('}');
        }
        b.append(']');
        return b.toString();
    }

    private static String toJsonObject(Map<String,Object> m) {
        StringBuilder b = new StringBuilder(64);
        b.append('{');
        int j=0;
        for (var e : m.entrySet()){
            if (j++>0) b.append(',');
            b.append('"').append(esc(e.getKey())).append('"').append(':');
            Object v = e.getValue();
            if (v == null) b.append("null");
            else if (v instanceof Number || v instanceof Boolean) b.append(String.valueOf(v));
            else b.append('"').append(esc(String.valueOf(v))).append('"');
        }
        b.append('}');
        return b.toString();
    }

    private static String toJsonObjectList(List<Map<String,Object>> list){
        StringBuilder b = new StringBuilder(64);
        b.append('[');
        for (int i=0;i<list.size();i++){
            if (i>0) b.append(',');
            b.append(toJsonObject(list.get(i)));
        }
        b.append(']');
        return b.toString();
    }

    private static String tryNumber(String s) {
        try { long v = Long.parseLong(s); return String.valueOf(v); } catch (Exception ignored) {}
        try { double d = Double.parseDouble(s); if (Double.isFinite(d)) return String.valueOf(d); } catch (Exception ignored) {}
        return '"' + esc(s) + '"';
    }

    /* ================= Reflection utils ================= */

    @SuppressWarnings("unchecked")
    private static List<String> tryFetchList(Object mgr, String method, int arg) {
        if (mgr == null) return java.util.Collections.emptyList();
        try {
            var m = mgr.getClass().getMethod(method, int.class);
            Object out = m.invoke(mgr, arg);
            if (out instanceof List<?> l) {
                List<String> res = new ArrayList<>(l.size());
                for (Object o : l) res.add(String.valueOf(o));
                return res;
            }
        } catch (Throwable ignored) {}
        return java.util.Collections.emptyList();
    }

    private static Long tryLong(Object o, String... methods) {
        if (o == null) return null;
        for (String m : methods) {
            try {
                var mm = o.getClass().getMethod(m);
                Object v = mm.invoke(o);
                if (v instanceof Number n) return n.longValue();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Long tryLong(Object o, String m1, String m2, String m3, World w) {
        if (o == null) return null;
        for (String m : new String[]{m1,m2,m3}) {
            try {
                var mm = o.getClass().getMethod(m, World.class);
                Object v = mm.invoke(o, w);
                if (v instanceof Number n) return n.longValue();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Integer tryInt(Object o, String... methods) {
        if (o == null) return null;
        for (String m : methods) {
            try {
                var mm = o.getClass().getMethod(m);
                Object v = mm.invoke(o);
                if (v instanceof Number n) return n.intValue();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static Double tryDouble(Object o, String... methods) {
        if (o == null) return null;
        for (String m : methods) {
            try {
                var mm = o.getClass().getMethod(m);
                Object v = mm.invoke(o);
                if (v instanceof Number n) return n.doubleValue();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static boolean tryBool(Object o, String... methods) {
        if (o == null) return false;
        for (String m : methods) {
            try {
                var mm = o.getClass().getMethod(m);
                Object v = mm.invoke(o);
                if (v instanceof Boolean b) return b;
            } catch (Throwable ignored) {}
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    private static Collection<?> tryCollection(Object o, String... methods) {
        if (o == null) return null;
        for (String m : methods) {
            try {
                var mm = o.getClass().getMethod(m);
                Object v = mm.invoke(o);
                if (v instanceof Collection<?> c) return c;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private static String tryString(Object o, String... methods) {
        if (o == null) return null;
        for (String m : methods) {
            try {
                var mm = o.getClass().getMethod(m);
                Object v = mm.invoke(o);
                if (v != null) return String.valueOf(v);
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private record RaidShopOffer(String id, String category, String material, int amount, String rewardType, String rewardValue) {
    }
}
