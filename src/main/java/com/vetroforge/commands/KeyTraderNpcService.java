package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.SQLException;
import java.util.UUID;

public final class KeyTraderNpcService implements Listener {

    private static final String TAG = "vetro_key_trader_npc";
    private static final String PATH = "keytrader.npc";

    private final FactionsPlugin plugin;
    private final KeyTraderCommand command;
    private final NamespacedKey tagKey;

    public KeyTraderNpcService(FactionsPlugin plugin, KeyTraderCommand command) {
        this.plugin = plugin;
        this.command = command;
        this.tagKey = new NamespacedKey(plugin, TAG);
        if (useMysql()) {
            initTable();
        }
    }

    public void spawnConfigured() {
        Location location = getLocation();
        if (location == null) return;
        UUID uuid = getUuid();
        if (uuid != null && entity(uuid) != null) return;
        // Chunk nur entladen (statt NPC wirklich verloren)? Dann nicht erneut spawnen.
        if (uuid != null && location.getWorld() != null
                && !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        spawnAt(location);
    }

    public boolean set(Location location) {
        if (location == null || location.getWorld() == null) return false;
        remove();
        Villager villager = spawnAt(location);
        return villager != null;
    }

    public boolean remove() {
        boolean removed = false;
        UUID uuid = getUuid();
        if (uuid != null) {
            Entity entity = entity(uuid);
            if (entity != null) {
                entity.remove();
                removed = true;
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isNpc(entity)) {
                    entity.remove();
                    removed = true;
                }
            }
        }
        deleteMysql();
        plugin.getConfig().set(PATH, null);
        plugin.saveConfig();
        return removed;
    }

    public String info() {
        Location location = getLocation();
        if (location == null || location.getWorld() == null) return ChatColor.GRAY + "Kein Key-Händler gesetzt.";
        return ChatColor.AQUA + "Key-Händler " + ChatColor.DARK_GRAY + "» "
                + ChatColor.WHITE + location.getWorld().getName()
                + ChatColor.GRAY + " x=" + ChatColor.WHITE + location.getBlockX()
                + ChatColor.GRAY + " y=" + ChatColor.WHITE + location.getBlockY()
                + ChatColor.GRAY + " z=" + ChatColor.WHITE + location.getBlockZ();
    }

    public boolean isNpc(Entity entity) {
        if (entity == null) return false;
        if (entity.getScoreboardTags().contains(TAG)) return true;
        Byte value = entity.getPersistentDataContainer().get(tagKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractAtEntityEvent event) {
        handle(event.getRightClicked(), event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClickCompat(PlayerInteractEntityEvent event) {
        handle(event.getRightClicked(), event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (isNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (isNpc(entity)) {
                try { entity.setCustomNameVisible(true); } catch (Throwable ignored) {}
            }
        }
    }

    private void handle(Entity entity, Player player, Cancellable event) {
        if (!isNpc(entity)) return;
        event.setCancelled(true);
        command.open(player);
    }

    private Villager spawnAt(Location raw) {
        Location location = center(raw);
        Villager villager = location.getWorld().spawn(location, Villager.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setCanPickupItems(false);
            entity.setCollidable(false);
            try { entity.setGravity(false); } catch (Throwable ignored) {}
            entity.setRemoveWhenFarAway(false);
            try { entity.setProfession(Villager.Profession.CLERIC); } catch (Throwable ignored) {}
            try { entity.setVillagerType(Villager.Type.SNOW); } catch (Throwable ignored) {}
            try { entity.setVillagerLevel(5); } catch (Throwable ignored) {}
            if (entity instanceof LivingEntity living) living.setCustomNameVisible(true);
            entity.setCustomName(ChatColor.AQUA + "" + ChatColor.BOLD + "Key Händler");
            tag(entity);
        });

        save(villager.getUniqueId(), raw);
        return villager;
    }

    private void tag(Entity entity) {
        entity.addScoreboardTag(TAG);
        entity.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void save(UUID uuid, Location location) {
        saveMysql(uuid, location);
        FileConfiguration config = plugin.getConfig();
        config.set(PATH + ".uuid", uuid.toString());
        config.set(PATH + ".world", location.getWorld().getName());
        config.set(PATH + ".x", location.getX());
        config.set(PATH + ".y", location.getY());
        config.set(PATH + ".z", location.getZ());
        config.set(PATH + ".yaw", location.getYaw());
        config.set(PATH + ".pitch", location.getPitch());
        plugin.saveConfig();
    }

    private Location getLocation() {
        Location mysql = getMysqlLocation();
        if (mysql != null) return mysql;

        FileConfiguration config = plugin.getConfig();
        if (!config.isSet(PATH + ".world")) return null;
        World world = Bukkit.getWorld(config.getString(PATH + ".world", "world"));
        if (world == null) return null;
        return new Location(
                world,
                config.getDouble(PATH + ".x"),
                config.getDouble(PATH + ".y"),
                config.getDouble(PATH + ".z"),
                (float) config.getDouble(PATH + ".yaw"),
                (float) config.getDouble(PATH + ".pitch")
        );
    }

    private UUID getUuid() {
        UUID mysql = getMysqlUuid();
        if (mysql != null) return mysql;

        String raw = plugin.getConfig().getString(PATH + ".uuid");
        if (raw == null || raw.isBlank()) return null;
        try {
            return UUID.fromString(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    private Entity entity(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null && !entity.isDead()) return entity;
        }
        return null;
    }

    private Location center(Location location) {
        return new Location(
                location.getWorld(),
                Math.floor(location.getX()) + 0.5,
                location.getY(),
                Math.floor(location.getZ()) + 0.5,
                location.getYaw(),
                location.getPitch()
        );
    }

    private void initTable() {
        if (!useMysql()) return;
        try (Connection connection = openConnection();
             Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS key_trader_npc (
                        id VARCHAR(32) PRIMARY KEY,
                        entity_uuid VARCHAR(36),
                        world VARCHAR(64) NOT NULL,
                        x DOUBLE NOT NULL,
                        y DOUBLE NOT NULL,
                        z DOUBLE NOT NULL,
                        yaw FLOAT NOT NULL DEFAULT 0,
                        pitch FLOAT NOT NULL DEFAULT 0,
                        updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
        } catch (SQLException ex) {
            plugin.getLogger().warning("KeyTrader-NPC MySQL konnte nicht initialisiert werden: " + ex.getMessage());
        }
    }

    private void saveMysql(UUID uuid, Location location) {
        if (!useMysql()) return;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     REPLACE INTO key_trader_npc(id, entity_uuid, world, x, y, z, yaw, pitch)
                     VALUES('main', ?, ?, ?, ?, ?, ?, ?)
                     """)) {
            statement.setString(1, uuid.toString());
            statement.setString(2, location.getWorld().getName());
            statement.setDouble(3, location.getX());
            statement.setDouble(4, location.getY());
            statement.setDouble(5, location.getZ());
            statement.setFloat(6, location.getYaw());
            statement.setFloat(7, location.getPitch());
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().warning("KeyTrader-NPC konnte nicht in MySQL gespeichert werden: " + ex.getMessage());
        }
    }

    private void deleteMysql() {
        if (!useMysql()) return;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("DELETE FROM key_trader_npc WHERE id='main'")) {
            statement.executeUpdate();
        } catch (SQLException ex) {
            plugin.getLogger().warning("KeyTrader-NPC konnte nicht aus MySQL entfernt werden: " + ex.getMessage());
        }
    }

    private Location getMysqlLocation() {
        if (!useMysql()) return null;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT world, x, y, z, yaw, pitch
                     FROM key_trader_npc
                     WHERE id='main'
                     """);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) return null;
            World world = Bukkit.getWorld(result.getString("world"));
            if (world == null) return null;
            return new Location(
                    world,
                    result.getDouble("x"),
                    result.getDouble("y"),
                    result.getDouble("z"),
                    result.getFloat("yaw"),
                    result.getFloat("pitch")
            );
        } catch (SQLException ex) {
            plugin.getLogger().warning("KeyTrader-NPC MySQL-Position konnte nicht geladen werden: " + ex.getMessage());
            return null;
        }
    }

    private UUID getMysqlUuid() {
        if (!useMysql()) return null;
        try (Connection connection = openConnection();
             PreparedStatement statement = connection.prepareStatement("""
                     SELECT entity_uuid
                     FROM key_trader_npc
                     WHERE id='main'
                     """);
             ResultSet result = statement.executeQuery()) {
            if (!result.next()) return null;
            String raw = result.getString("entity_uuid");
            if (raw == null || raw.isBlank()) return null;
            return UUID.fromString(raw);
        } catch (Exception ex) {
            return null;
        }
    }

    private Connection openConnection() throws SQLException {
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("mysql.host", "127.0.0.1");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "VF");
        String user = config.getString("mysql.user", "VF");
        String password = config.getString("mysql.password", "");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&autoReconnect=true&allowPublicKeyRetrieval=true";
        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }

    private boolean useMysql() {
        return plugin.getConfig().getBoolean("mysql.enabled", false)
                && plugin.getConfig().getBoolean("keytrader.npc.mysql.enabled", true);
    }
}
