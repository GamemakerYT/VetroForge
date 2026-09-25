package com.vetroforge.warzone.heatmap;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.warzone.WarzoneRegionManager;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class WarzoneActivityService {

    public static final String TYPE_POSITION = "position";
    public static final String TYPE_KILL = "kill";
    public static final String TYPE_DEATH = "death";
    public static final String TYPE_CHEST_OPEN = "chest_open";
    public static final String TYPE_LOOTDROP_SPAWN = "lootdrop_spawn";
    public static final String TYPE_LOOTDROP_CLAIM = "lootdrop_claim";
    public static final String TYPE_LOOTDROP_EXPIRE = "lootdrop_expire";

    private final FactionsPlugin plugin;
    private final Map<UUID, Long> lastPositionLog = new ConcurrentHashMap<>();
    private volatile boolean schemaReady;
    private BukkitTask cleanupTask;

    public WarzoneActivityService(FactionsPlugin plugin) {
        this.plugin = plugin;
        ensureSchemaAsync();
        startCleanupTask();
    }

    public void shutdown() {
        if (cleanupTask != null) {
            cleanupTask.cancel();
            cleanupTask = null;
        }
        lastPositionLog.clear();
    }

    public void logPosition(Player player) {
        if (player == null || !player.isOnline()) return;
        Location location = player.getLocation();
        if (!isWarzone(location)) return;
        long now = System.currentTimeMillis();
        long everyMs = Math.max(1000L, plugin.getConfig().getLong("warzone.heatmap.positionSampleSeconds", 5L) * 1000L);
        Long previous = lastPositionLog.get(player.getUniqueId());
        if (previous != null && now - previous < everyMs) return;
        lastPositionLog.put(player.getUniqueId(), now);
        log(TYPE_POSITION, location, player, null, player.getUniqueId(), player.getName(), null);
    }

    public void logKill(Player killer, Player victim) {
        if (killer != null && isWarzone(killer.getLocation())) {
            log(TYPE_KILL, killer.getLocation(), killer, victim,
                    killer.getUniqueId(), killer.getName(), victim == null ? null : victim.getName());
        }
        if (victim != null && isWarzone(victim.getLocation())) {
            log(TYPE_DEATH, victim.getLocation(), victim, killer,
                    victim.getUniqueId(), victim.getName(), killer == null ? null : killer.getName());
        }
    }

    public void logChestOpen(Player opener, Location location) {
        log(TYPE_CHEST_OPEN, location, opener, null,
                opener == null ? null : opener.getUniqueId(),
                opener == null ? null : opener.getName(),
                null);
    }

    public void logLootdropSpawn(Location location) {
        log(TYPE_LOOTDROP_SPAWN, location, null, null, null, null, null);
    }

    public void logLootdropClaim(Player player, Location location) {
        log(TYPE_LOOTDROP_CLAIM, location, player, null,
                player == null ? null : player.getUniqueId(),
                player == null ? null : player.getName(),
                null);
    }

    public void logLootdropExpire(Location location) {
        log(TYPE_LOOTDROP_EXPIRE, location, null, null, null, null, null);
    }

    public String heatmapJson(int minutes, int grid, int limit) {
        ensureSchemaSyncQuietly();
        int safeMinutes = Math.max(1, Math.min(60 * 24 * 30, minutes));
        int safeGrid = Math.max(8, Math.min(128, grid));
        int safeLimit = Math.max(10, Math.min(2000, limit));
        long since = System.currentTimeMillis() - safeMinutes * 60_000L;
        Bounds bounds = bounds();

        try (Connection connection = connection()) {
            List<Map<String, Object>> points = loadPoints(connection, since, safeLimit);
            List<Map<String, Object>> cells = loadCells(connection, since, safeGrid, safeLimit);
            List<Map<String, Object>> replay = loadReplay(connection, Math.max(System.currentTimeMillis() - 10 * 60_000L, since), 600);
            List<Map<String, Object>> factionControl = loadFactionControl(connection, since, safeGrid, 250);
            Map<String, Object> hotspot = loadHotspot(connection, since, safeGrid);
            List<Map<String, Object>> deadzones = computeDeadzones(bounds, cells, safeGrid);
            List<Map<String, Object>> fairness = loadLootdropFairness(connection, since, safeGrid);

            Map<String, Object> root = new LinkedHashMap<>();
            root.put("ok", true);
            root.put("generatedAt", System.currentTimeMillis());
            root.put("minutes", safeMinutes);
            root.put("grid", safeGrid);
            root.put("bounds", bounds.toMap());
            root.put("points", points);
            root.put("cells", cells);
            root.put("hotspot", hotspot);
            root.put("deadzones", deadzones);
            root.put("lootdropFairness", fairness);
            root.put("factionControl", factionControl);
            root.put("replay", replay);
            return toJson(root);
        } catch (Throwable throwable) {
            return "{\"ok\":false,\"err\":\"" + esc(throwable.getMessage()) + "\"}";
        }
    }

    private void log(String type, Location location, Player actor, Player target, UUID uuid, String name, String targetName) {
        if (location == null || location.getWorld() == null) return;
        if (!isWarzone(location) && !TYPE_LOOTDROP_EXPIRE.equals(type)) return;
        if (!plugin.getConfig().getBoolean("warzone.heatmap.enabled", true)) return;
        String normalized = normalizeType(type);
        String world = location.getWorld().getName();
        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();
        String faction = actor == null ? null : readFaction(actor.getUniqueId());
        String targetUuid = target == null ? null : target.getUniqueId().toString();
        String finalName = name;
        String finalTargetName = targetName;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            ensureSchemaSyncQuietly();
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO warzone_activity_log
                         (type, world, x, y, z, actor_uuid, actor_name, actor_faction, target_uuid, target_name, created_at)
                         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                         """)) {
                statement.setString(1, normalized);
                statement.setString(2, world);
                statement.setInt(3, x);
                statement.setInt(4, y);
                statement.setInt(5, z);
                statement.setString(6, uuid == null ? null : uuid.toString());
                statement.setString(7, finalName);
                statement.setString(8, faction);
                statement.setString(9, targetUuid);
                statement.setString(10, finalTargetName);
                statement.setLong(11, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (Throwable throwable) {
                plugin.getLogger().fine("[WarzoneHeatmap] log skipped: " + throwable.getMessage());
            }
        });
    }

    private List<Map<String, Object>> loadPoints(Connection connection, long since, int limit) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT type, world, x, y, z, actor_name, actor_faction, target_name, created_at
                FROM warzone_activity_log
                WHERE created_at >= ?
                ORDER BY created_at DESC
                LIMIT ?
                """)) {
            statement.setLong(1, since);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) out.add(row(result));
            }
        }
        return out;
    }

    private List<Map<String, Object>> loadCells(Connection connection, long since, int grid, int limit) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT type,
                       FLOOR(x / ?) AS cell_x,
                       FLOOR(z / ?) AS cell_z,
                       COUNT(*) AS count
                FROM warzone_activity_log
                WHERE created_at >= ?
                GROUP BY type, cell_x, cell_z
                ORDER BY count DESC
                LIMIT ?
                """)) {
            statement.setInt(1, grid);
            statement.setInt(2, grid);
            statement.setLong(3, since);
            statement.setInt(4, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("type", result.getString("type"));
                    row.put("cellX", result.getInt("cell_x"));
                    row.put("cellZ", result.getInt("cell_z"));
                    row.put("x", result.getInt("cell_x") * grid + grid / 2);
                    row.put("z", result.getInt("cell_z") * grid + grid / 2);
                    row.put("count", result.getLong("count"));
                    out.add(row);
                }
            }
        }
        return out;
    }

    private Map<String, Object> loadHotspot(Connection connection, long since, int grid) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT FLOOR(x / ?) AS cell_x,
                       FLOOR(z / ?) AS cell_z,
                       COUNT(*) AS count
                FROM warzone_activity_log
                WHERE created_at >= ? AND type IN ('kill','death')
                GROUP BY cell_x, cell_z
                ORDER BY count DESC
                LIMIT 1
                """)) {
            statement.setInt(1, grid);
            statement.setInt(2, grid);
            statement.setLong(3, since);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("cellX", result.getInt("cell_x"));
                    row.put("cellZ", result.getInt("cell_z"));
                    row.put("x", result.getInt("cell_x") * grid + grid / 2);
                    row.put("z", result.getInt("cell_z") * grid + grid / 2);
                    row.put("count", result.getLong("count"));
                    return row;
                }
            }
        }
        return Map.of();
    }

    private List<Map<String, Object>> loadReplay(Connection connection, long since, int limit) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT type, world, x, y, z, actor_name, actor_faction, target_name, created_at
                FROM warzone_activity_log
                WHERE created_at >= ? AND type IN ('position','kill','death','lootdrop_claim','chest_open')
                ORDER BY created_at ASC
                LIMIT ?
                """)) {
            statement.setLong(1, since);
            statement.setInt(2, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) out.add(row(result));
            }
        }
        return out;
    }

    private List<Map<String, Object>> loadFactionControl(Connection connection, long since, int grid, int limit) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT actor_faction,
                       FLOOR(x / ?) AS cell_x,
                       FLOOR(z / ?) AS cell_z,
                       COUNT(*) AS count
                FROM warzone_activity_log
                WHERE created_at >= ? AND actor_faction IS NOT NULL AND actor_faction <> ''
                  AND type IN ('kill','chest_open','lootdrop_claim')
                GROUP BY actor_faction, cell_x, cell_z
                ORDER BY count DESC
                LIMIT ?
                """)) {
            statement.setInt(1, grid);
            statement.setInt(2, grid);
            statement.setLong(3, since);
            statement.setInt(4, limit);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("faction", result.getString("actor_faction"));
                    row.put("cellX", result.getInt("cell_x"));
                    row.put("cellZ", result.getInt("cell_z"));
                    row.put("x", result.getInt("cell_x") * grid + grid / 2);
                    row.put("z", result.getInt("cell_z") * grid + grid / 2);
                    row.put("count", result.getLong("count"));
                    out.add(row);
                }
            }
        }
        return out;
    }

    private List<Map<String, Object>> loadLootdropFairness(Connection connection, long since, int grid) throws Exception {
        List<Map<String, Object>> out = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT FLOOR(x / ?) AS cell_x,
                       FLOOR(z / ?) AS cell_z,
                       COUNT(*) AS count
                FROM warzone_activity_log
                WHERE created_at >= ? AND type IN ('lootdrop_spawn','lootdrop_claim')
                GROUP BY cell_x, cell_z
                ORDER BY count DESC
                LIMIT 25
                """)) {
            statement.setInt(1, grid);
            statement.setInt(2, grid);
            statement.setLong(3, since);
            try (ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    Map<String, Object> row = new LinkedHashMap<>();
                    row.put("cellX", result.getInt("cell_x"));
                    row.put("cellZ", result.getInt("cell_z"));
                    row.put("x", result.getInt("cell_x") * grid + grid / 2);
                    row.put("z", result.getInt("cell_z") * grid + grid / 2);
                    row.put("count", result.getLong("count"));
                    out.add(row);
                }
            }
        }
        return out;
    }

    private List<Map<String, Object>> computeDeadzones(Bounds bounds, List<Map<String, Object>> cells, int grid) {
        List<Map<String, Object>> out = new ArrayList<>();
        if (!bounds.valid) return out;
        Map<String, Boolean> active = new HashMap<>();
        for (Map<String, Object> cell : cells) {
            active.put(cell.get("cellX") + ":" + cell.get("cellZ"), true);
        }
        int minCellX = (int) Math.floor((double) bounds.minX / grid);
        int maxCellX = (int) Math.floor((double) bounds.maxX / grid);
        int minCellZ = (int) Math.floor((double) bounds.minZ / grid);
        int maxCellZ = (int) Math.floor((double) bounds.maxZ / grid);
        for (int cx = minCellX; cx <= maxCellX && out.size() < 20; cx++) {
            for (int cz = minCellZ; cz <= maxCellZ && out.size() < 20; cz++) {
                if (active.containsKey(cx + ":" + cz)) continue;
                int x = cx * grid + grid / 2;
                int z = cz * grid + grid / 2;
                Location check = bounds.world == null ? null : new Location(bounds.world, x, bounds.world.getSpawnLocation().getY(), z);
                WarzoneRegionManager warzone = plugin.getWarzoneRegionManager();
                if (warzone != null && check != null && !warzone.isWarzone(check)) continue;
                Map<String, Object> row = new LinkedHashMap<>();
                row.put("cellX", cx);
                row.put("cellZ", cz);
                row.put("x", x);
                row.put("z", z);
                out.add(row);
            }
        }
        return out;
    }

    private Map<String, Object> row(ResultSet result) throws Exception {
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("type", result.getString("type"));
        row.put("world", result.getString("world"));
        row.put("x", result.getInt("x"));
        row.put("y", result.getInt("y"));
        row.put("z", result.getInt("z"));
        row.put("actor", result.getString("actor_name"));
        row.put("faction", result.getString("actor_faction"));
        row.put("target", result.getString("target_name"));
        row.put("createdAt", result.getLong("created_at"));
        return row;
    }

    private Bounds bounds() {
        WarzoneRegionManager warzone = plugin.getWarzoneRegionManager();
        if (warzone == null || !warzone.isConfigured()) return Bounds.invalid();
        if (warzone.getShape() == WarzoneRegionManager.Shape.CIRCLE && warzone.getCenter() != null) {
            Location center = warzone.getCenter();
            int radius = (int) Math.ceil(warzone.getRadius());
            return new Bounds(center.getWorld(), center.getBlockX() - radius, center.getBlockZ() - radius,
                    center.getBlockX() + radius, center.getBlockZ() + radius);
        }
        WarzoneRegionManager.Cuboid cuboid = warzone.getCuboid();
        if (cuboid != null) {
            return new Bounds(cuboid.min.getWorld(), cuboid.min.getBlockX(), cuboid.min.getBlockZ(),
                    cuboid.max.getBlockX(), cuboid.max.getBlockZ());
        }
        return Bounds.invalid();
    }

    private boolean isWarzone(Location location) {
        try {
            WarzoneRegionManager warzone = plugin.getWarzoneRegionManager();
            return warzone != null && warzone.isWarzone(location);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String readFaction(UUID uuid) {
        try {
            if (uuid == null || plugin.getFactionManager() == null) return null;
            return plugin.getFactionManager().getFactionOf(uuid);
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void ensureSchemaAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::ensureSchemaSyncQuietly);
    }

    private void ensureSchemaSyncQuietly() {
        if (schemaReady) return;
        try (Connection connection = connection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS warzone_activity_log (
                        id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
                        type VARCHAR(32) NOT NULL,
                        world VARCHAR(64) NOT NULL,
                        x INT NOT NULL,
                        y INT NOT NULL,
                        z INT NOT NULL,
                        actor_uuid VARCHAR(36) NULL,
                        actor_name VARCHAR(32) NULL,
                        actor_faction VARCHAR(64) NULL,
                        target_uuid VARCHAR(36) NULL,
                        target_name VARCHAR(32) NULL,
                        created_at BIGINT NOT NULL,
                        INDEX idx_warzone_activity_time (created_at),
                        INDEX idx_warzone_activity_type_time (type, created_at),
                        INDEX idx_warzone_activity_world_time (world, created_at),
                        INDEX idx_warzone_activity_faction_time (actor_faction, created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            schemaReady = true;
        } catch (Throwable throwable) {
            plugin.getLogger().fine("[WarzoneHeatmap] schema not ready: " + throwable.getMessage());
        }
    }

    private void startCleanupTask() {
        long periodTicks = 20L * 60L * 30L;
        cleanupTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, () -> {
            int days = Math.max(1, plugin.getConfig().getInt("warzone.heatmap.retentionDays", 14));
            long cutoff = System.currentTimeMillis() - days * 86_400_000L;
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("DELETE FROM warzone_activity_log WHERE created_at < ?")) {
                statement.setLong(1, cutoff);
                statement.executeUpdate();
            } catch (Throwable ignored) {
            }
        }, periodTicks, periodTicks);
    }

    private Connection connection() throws Exception {
        String host = plugin.getConfig().getString("mysql.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String database = plugin.getConfig().getString("mysql.database", "VF");
        String user = plugin.getConfig().getString("mysql.user", "VF");
        String password = plugin.getConfig().getString("mysql.password", "");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&autoReconnect=true&serverTimezone=UTC";
        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }

    private static String normalizeType(String type) {
        if (type == null || type.isBlank()) return "unknown";
        return type.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");
    }

    private static String toJson(Object value) {
        if (value == null) return "null";
        if (value instanceof Number || value instanceof Boolean) return String.valueOf(value);
        if (value instanceof Map<?, ?> map) {
            StringBuilder builder = new StringBuilder("{");
            int index = 0;
            for (Map.Entry<?, ?> entry : map.entrySet()) {
                if (index++ > 0) builder.append(',');
                builder.append('"').append(esc(String.valueOf(entry.getKey()))).append('"').append(':')
                        .append(toJson(entry.getValue()));
            }
            return builder.append('}').toString();
        }
        if (value instanceof Iterable<?> iterable) {
            StringBuilder builder = new StringBuilder("[");
            int index = 0;
            for (Object entry : iterable) {
                if (index++ > 0) builder.append(',');
                builder.append(toJson(entry));
            }
            return builder.append(']').toString();
        }
        return '"' + esc(String.valueOf(value)) + '"';
    }

    private static String esc(String value) {
        if (value == null) return "";
        StringBuilder builder = new StringBuilder(value.length() + 16);
        for (char c : value.toCharArray()) {
            switch (c) {
                case '\\' -> builder.append("\\\\");
                case '"' -> builder.append("\\\"");
                case '\n' -> builder.append("\\n");
                case '\r' -> builder.append("\\r");
                case '\t' -> builder.append("\\t");
                default -> builder.append(c);
            }
        }
        return builder.toString();
    }

    private static final class Bounds {
        private final org.bukkit.World world;
        private final int minX;
        private final int minZ;
        private final int maxX;
        private final int maxZ;
        private final boolean valid;

        private Bounds(org.bukkit.World world, int minX, int minZ, int maxX, int maxZ) {
            this.world = world;
            this.minX = Math.min(minX, maxX);
            this.minZ = Math.min(minZ, maxZ);
            this.maxX = Math.max(minX, maxX);
            this.maxZ = Math.max(minZ, maxZ);
            this.valid = world != null;
        }

        private static Bounds invalid() {
            return new Bounds(null, 0, 0, 0, 0);
        }

        private Map<String, Object> toMap() {
            Map<String, Object> out = new LinkedHashMap<>();
            out.put("valid", valid);
            out.put("world", world == null ? "" : world.getName());
            out.put("minX", minX);
            out.put("minZ", minZ);
            out.put("maxX", maxX);
            out.put("maxZ", maxZ);
            return out;
        }
    }
}
