package com.vetroforge.services;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.util.io.BukkitObjectInputStream;
import org.bukkit.util.io.BukkitObjectOutputStream;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Base64;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class PrivateVaultService implements Listener {
    private static final String TITLE_PREFIX = ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Vault " + ChatColor.DARK_GRAY + "» " + ChatColor.WHITE + "#";
    private static final int EMBER_RESERVED_SLOT = 8;

    private final FactionsPlugin plugin;
    private final Map<Inventory, OpenVault> openVaults = new HashMap<>();

    public PrivateVaultService(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        initTables();
    }

    public void open(Player player, int vaultNumber) {
        VaultAccess access = access(player);
        if (access.maxVaults <= 0) {
            player.sendMessage(ChatColor.RED + "Private Vaults sind ab Rang Ember verfügbar.");
            return;
        }
        if (vaultNumber < 1 || vaultNumber > access.maxVaults) {
            player.sendMessage(ChatColor.RED + "Du kannst /pv 1" + (access.maxVaults > 1 ? "-" + access.maxVaults : "") + " nutzen.");
            return;
        }

        Inventory inventory = Bukkit.createInventory(null, access.inventorySize,
                TITLE_PREFIX + vaultNumber + ChatColor.DARK_GRAY + " • " + access.rankDisplay);
        load(player.getUniqueId(), vaultNumber, inventory);
        if (access.usableSlots < access.inventorySize) {
            inventory.setItem(EMBER_RESERVED_SLOT, lockedSlot());
        }
        openVaults.put(inventory, new OpenVault(player.getUniqueId(), vaultNumber, access.usableSlots));
        player.openInventory(inventory);
    }

    public VaultAccess access(Player player) {
        String rank = rankName(player).toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
        if (player.hasPermission("vetroforge.rank.vetroplus") || rank.equals("vetro+") || rank.equals("vetroplus")) {
            return new VaultAccess("Vetro+", 5, 54, 54);
        }
        if (player.hasPermission("vetroforge.rank.apex") || player.hasPermission("vetroforge.rank.mythic")
                || rank.equals("apex") || rank.equals("mythic")) {
            return new VaultAccess("Apex", 4, 36, 36);
        }
        if (player.hasPermission("vetroforge.rank.obsidian") || rank.equals("obsidian")) {
            return new VaultAccess("Obsidian", 3, 27, 27);
        }
        if (player.hasPermission("vetroforge.rank.vanguard") || rank.equals("vanguard")) {
            return new VaultAccess("Vanguard", 2, 18, 18);
        }
        if (player.hasPermission("vetroforge.rank.ember") || rank.equals("ember")) {
            return new VaultAccess("Ember", 1, 9, 8);
        }
        return new VaultAccess("Player", 0, 0, 0);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onClick(InventoryClickEvent event) {
        OpenVault open = openVaults.get(event.getView().getTopInventory());
        if (open == null) return;
        if (open.usableSlots < event.getView().getTopInventory().getSize()
                && event.getRawSlot() == EMBER_RESERVED_SLOT) {
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onDrag(InventoryDragEvent event) {
        OpenVault open = openVaults.get(event.getView().getTopInventory());
        if (open == null || open.usableSlots >= event.getView().getTopInventory().getSize()) return;
        if (event.getRawSlots().contains(EMBER_RESERVED_SLOT)) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        OpenVault open = openVaults.remove(event.getInventory());
        if (open == null) return;
        save(open.owner, open.number, event.getInventory(), open.usableSlots);
    }

    private void initTables() {
        if (!mysqlEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS private_vaults (
                          uuid VARCHAR(36) NOT NULL,
                          vault_no INT NOT NULL,
                          contents MEDIUMTEXT NOT NULL,
                          updated_at BIGINT NOT NULL,
                          PRIMARY KEY (uuid, vault_no)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[PrivateVault] MySQL init fehlgeschlagen: " + throwable.getMessage());
            }
        });
    }

    private void load(UUID uuid, int number, Inventory inventory) {
        if (!mysqlEnabled()) return;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT contents FROM private_vaults WHERE uuid=? AND vault_no=?")) {
            statement.setString(1, uuid.toString());
            statement.setInt(2, number);
            try (ResultSet result = statement.executeQuery()) {
                if (result.next()) restore(inventory, result.getString("contents"));
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[PrivateVault] Laden fehlgeschlagen: " + throwable.getMessage());
        }
    }

    private void save(UUID uuid, int number, Inventory inventory, int usableSlots) {
        if (!mysqlEnabled()) return;
        ItemStack[] contents = new ItemStack[inventory.getSize()];
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            if (slot >= usableSlots) continue;
            contents[slot] = inventory.getItem(slot);
        }
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO private_vaults (uuid, vault_no, contents, updated_at)
                         VALUES (?, ?, ?, ?)
                         ON DUPLICATE KEY UPDATE contents=VALUES(contents), updated_at=VALUES(updated_at)
                         """)) {
                statement.setString(1, uuid.toString());
                statement.setInt(2, number);
                statement.setString(3, serialize(contents));
                statement.setLong(4, System.currentTimeMillis());
                statement.executeUpdate();
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[PrivateVault] Speichern fehlgeschlagen: " + throwable.getMessage());
            }
        });
    }

    private String serialize(ItemStack[] contents) throws Exception {
        ByteArrayOutputStream byteStream = new ByteArrayOutputStream();
        try (BukkitObjectOutputStream out = new BukkitObjectOutputStream(byteStream)) {
            out.writeInt(contents.length);
            for (ItemStack item : contents) out.writeObject(item);
        }
        return Base64.getEncoder().encodeToString(byteStream.toByteArray());
    }

    private void restore(Inventory inventory, String encoded) throws Exception {
        if (encoded == null || encoded.isBlank()) return;
        byte[] data = Base64.getDecoder().decode(encoded);
        try (BukkitObjectInputStream in = new BukkitObjectInputStream(new ByteArrayInputStream(data))) {
            int length = Math.min(in.readInt(), inventory.getSize());
            for (int slot = 0; slot < length; slot++) {
                Object value = in.readObject();
                if (value instanceof ItemStack item) inventory.setItem(slot, item);
            }
        }
    }

    private ItemStack lockedSlot() {
        ItemStack item = new ItemStack(Material.BARRIER);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(ChatColor.RED + "Slot gesperrt");
            meta.setLore(java.util.List.of(ChatColor.GRAY + "Ember hat 8 nutzbare Vault-Slots."));
            item.setItemMeta(meta);
        }
        return item;
    }

    private String rankName(Player player) {
        try {
            return plugin.getVetroPermsManager() == null ? "Player" : plugin.getVetroPermsManager().getRank(player);
        } catch (Throwable ignored) {
            return "Player";
        }
    }

    private boolean mysqlEnabled() {
        return plugin.getConfig().getBoolean("mysql.enabled", false);
    }

    private Connection connection() throws Exception {
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("mysql.host", "127.0.0.1");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "VF");
        String user = config.getString("mysql.user", "VF");
        String password = config.getString("mysql.password", "");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&autoReconnect=true&serverTimezone=UTC";
        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }

    public record VaultAccess(String rankDisplay, int maxVaults, int inventorySize, int usableSlots) {}
    private record OpenVault(UUID owner, int number, int usableSlots) {}
}
