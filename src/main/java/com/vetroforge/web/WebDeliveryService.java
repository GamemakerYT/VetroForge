package com.vetroforge.web;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.GameMode;
import org.bukkit.persistence.PersistentDataType;
import com.vetroforge.factions.vault.VaultPlayerReceipt;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import java.util.List;
import java.util.ArrayList;
import java.util.Set;

public final class WebDeliveryService implements Listener {
    private final FactionsPlugin plugin;
    private final NamespacedKey receiptKey;
    public record Delivery(String purchaseId, String itemKey, String material, int amount, int price, String status, long createdAt) {}
    public record AdminDelivery(String purchaseId, UUID player, String type, String itemKey, String material,
                                int amount, int price, String status, String error, boolean handoffStarted, long createdAt) {}

    public WebDeliveryService(FactionsPlugin plugin) {
        this.plugin = plugin;
        receiptKey = new NamespacedKey(plugin, "delivery_receipt");
        ensureSchema();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) processPending(player);
        }, 60L);
    }

    public Map<String, Object> submitShopPurchase(String purchaseId, UUID uuid, String itemKey,
                                                   Material material, int amount, int price) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Purchase requires server thread");
        if (purchaseId == null || purchaseId.isBlank() || purchaseId.length() > 64 || purchaseId.regionMatches(true,0,"convoy-",0,7) || uuid == null
                || itemKey == null || itemKey.isBlank() || itemKey.length() > 128 || material == null
                || !material.isItem() || material.isAir() || amount < 1 || amount > 2304 || price < 1)
            return result(false, "FAILED", purchaseId, "invalid purchase");
        try (Connection connection = connection()) {
            connection.setAutoCommit(false);
            Map<String, Object> existing = status(connection, purchaseId, uuid);
            if (existing != null) {
                connection.rollback();
                return existing;
            }

            try (PreparedStatement insert = connection.prepareStatement("""
                    INSERT INTO web_deliveries
                    (purchase_id, player_uuid, delivery_type, item_key, material, amount, price, status, created_at, updated_at)
                    VALUES (?, ?, 'SHOP', ?, ?, ?, ?, 'PENDING', ?, ?)
                    """)) {
                long now = System.currentTimeMillis();
                insert.setString(1, purchaseId);
                insert.setString(2, uuid.toString());
                insert.setString(3, itemKey);
                insert.setString(4, material.name());
                insert.setInt(5, amount);
                insert.setInt(6, price);
                insert.setLong(7, now);
                insert.setLong(8, now);
                insert.executeUpdate();
            }

            try (PreparedStatement ensure = connection.prepareStatement(
                    "INSERT IGNORE INTO coins(uuid, coins) VALUES(?, 0)")) {
                ensure.setString(1, uuid.toString());
                ensure.executeUpdate();
            }
            int changed;
            try (PreparedStatement debit = connection.prepareStatement(
                    "UPDATE coins SET coins=coins-? WHERE uuid=? AND coins>=?")) {
                debit.setInt(1, price);
                debit.setString(2, uuid.toString());
                debit.setInt(3, price);
                changed = debit.executeUpdate();
            }
            if (changed != 1) {
                updateStatus(connection, purchaseId, "FAILED", "not enough coins");
                connection.commit();
                return result(false, "FAILED", purchaseId, "not enough coins");
            }
            connection.commit();
            if (plugin.getCoinsManager() != null) plugin.getCoinsManager().removeCoinsLocal(uuid, price);

            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                return deliver(player, purchaseId);
            }
            return result(true, "PENDING", purchaseId, null);
        } catch (Exception exception) {
            plugin.getLogger().warning("[WebDelivery] Queue fehlgeschlagen: " + exception.getMessage());
            // A lost commit acknowledgement is not evidence that the payment failed.
            return status(purchaseId, uuid);
        }
    }

    public List<Delivery> list(UUID uuid, int page) throws Exception {
        if (page < 0 || page > 10000) throw new IllegalArgumentException("Invalid page");
        List<Delivery> deliveries = new ArrayList<>();
        try (Connection connection = connection(); PreparedStatement query = connection.prepareStatement(
                "SELECT purchase_id,item_key,material,amount,price,status,created_at FROM web_deliveries WHERE player_uuid=? ORDER BY id DESC LIMIT 22 OFFSET ?")) {
            query.setString(1, uuid.toString()); query.setInt(2, page * 21);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) deliveries.add(new Delivery(rows.getString(1), rows.getString(2), rows.getString(3), rows.getInt(4), rows.getInt(5), rows.getString(6), rows.getLong(7)));
            }
        }
        return List.copyOf(deliveries);
    }

    public List<AdminDelivery> listAttention(int page) throws Exception {
        if (page < 0 || page > 10_000) throw new IllegalArgumentException("Invalid page");
        List<AdminDelivery> deliveries = new ArrayList<>();
        try (Connection database = connection(); PreparedStatement query = database.prepareStatement("""
                SELECT purchase_id,player_uuid,delivery_type,item_key,material,amount,price,status,error_message,
                       before_inventory IS NOT NULL OR after_inventory IS NOT NULL AS handoff_started,created_at
                FROM web_deliveries
                WHERE status IN ('PENDING','DELIVERING','REVIEW')
                ORDER BY CASE status WHEN 'DELIVERING' THEN 0 WHEN 'REVIEW' THEN 1 ELSE 2 END,id DESC
                LIMIT 22 OFFSET ?
                """)) {
            query.setInt(1, page * 21);
            try (ResultSet rows = query.executeQuery()) {
                while (rows.next()) deliveries.add(new AdminDelivery(rows.getString(1), UUID.fromString(rows.getString(2)),
                        rows.getString(3), rows.getString(4), rows.getString(5), rows.getInt(6), rows.getInt(7),
                        rows.getString(8), rows.getString(9), rows.getBoolean(10), rows.getLong(11)));
            }
        }
        return List.copyOf(deliveries);
    }

    /** Only actions with provable pre-handoff state are automated. */
    public String adminResolve(String purchaseId, UUID actor, String actorName, String action) throws Exception {
        if (purchaseId == null || purchaseId.isBlank() || actor == null || actorName == null) throw new IllegalArgumentException("Invalid admin action");
        try (Connection database = connection()) {
            database.setAutoCommit(false);
            try {
                AdminDelivery row;
                try (PreparedStatement query = database.prepareStatement("""
                        SELECT purchase_id,player_uuid,delivery_type,item_key,material,amount,price,status,error_message,
                               before_inventory IS NOT NULL OR after_inventory IS NOT NULL AS handoff_started,created_at
                        FROM web_deliveries WHERE purchase_id=? FOR UPDATE
                        """)) {
                    query.setString(1, purchaseId);
                    try (ResultSet result = query.executeQuery()) {
                        if (!result.next()) throw new IllegalStateException("Lieferung nicht gefunden.");
                        row = new AdminDelivery(result.getString(1), UUID.fromString(result.getString(2)), result.getString(3),
                                result.getString(4), result.getString(5), result.getInt(6), result.getInt(7), result.getString(8),
                                result.getString(9), result.getBoolean(10), result.getLong(11));
                    }
                }
                if (row.handoffStarted()) throw new IllegalStateException("Inventar-Handoff hat begonnen. Automatische Korrektur ist gesperrt.");
                long now = System.currentTimeMillis();
                int balance = -1;
                if ("retry".equals(action)) {
                    if (!"REVIEW".equals(row.status())) throw new IllegalStateException("Nur ungeklärte Lieferungen können erneut versucht werden.");
                    Material material = Material.matchMaterial(row.material());
                    if (material == null || material.isAir() || !material.isItem() || row.amount() < 1)
                        throw new IllegalStateException("Lieferdaten sind ungültig und können nicht erneut versucht werden.");
                    try (PreparedStatement update = database.prepareStatement("UPDATE web_deliveries SET status='PENDING',error_message=NULL,updated_at=? WHERE purchase_id=? AND status='REVIEW'")) {
                        update.setLong(1, now); update.setString(2, purchaseId);
                        if (update.executeUpdate() != 1) throw new IllegalStateException("Lieferstatus hat sich geändert.");
                    }
                } else if ("refund".equals(action)) {
                    if (!"SHOP".equals(row.type()) || !Set.of("PENDING", "REVIEW").contains(row.status()) || row.price() < 1)
                        throw new IllegalStateException("Diese Lieferung darf nicht automatisch erstattet werden.");
                    try (PreparedStatement ensure = database.prepareStatement("INSERT IGNORE INTO coins(uuid,coins) VALUES(?,0)")) {
                        ensure.setString(1, row.player().toString()); ensure.executeUpdate();
                    }
                    try (PreparedStatement credit = database.prepareStatement("UPDATE coins SET coins=coins+? WHERE uuid=?")) {
                        credit.setInt(1, row.price()); credit.setString(2, row.player().toString()); credit.executeUpdate();
                    }
                    try (PreparedStatement update = database.prepareStatement("UPDATE web_deliveries SET status='REFUNDED',error_message='staff refund',updated_at=? WHERE purchase_id=? AND status=?")) {
                        update.setLong(1, now); update.setString(2, purchaseId); update.setString(3, row.status());
                        if (update.executeUpdate() != 1) throw new IllegalStateException("Lieferstatus hat sich geändert.");
                    }
                    try (PreparedStatement current = database.prepareStatement("SELECT coins FROM coins WHERE uuid=?")) {
                        current.setString(1, row.player().toString());
                        try (ResultSet result = current.executeQuery()) { if (result.next()) balance = result.getInt(1); }
                    }
                } else throw new IllegalArgumentException("Unbekannte Teamaktion.");

                try (PreparedStatement audit = database.prepareStatement("""
                        INSERT INTO web_delivery_audit(purchase_id,player_uuid,actor_uuid,actor_name,action,before_status,after_status,created_at)
                        VALUES(?,?,?,?,?,?,?,?)
                        """)) {
                    audit.setString(1, purchaseId); audit.setString(2, row.player().toString()); audit.setString(3, actor.toString());
                    audit.setString(4, actorName); audit.setString(5, action); audit.setString(6, row.status());
                    audit.setString(7, "retry".equals(action) ? "PENDING" : "REFUNDED"); audit.setLong(8, now); audit.executeUpdate();
                }
                database.commit();
                // NOTE (adapted from factions-repair): the original called
                // CoinsManager.syncCommittedBalance(row.player(), balance) to push the freshly
                // committed absolute DB balance into the local cache. The current codebase's
                // CoinsManager has no such method (API drift); it does have addCoinsLocal(uuid,
                // delta), so the equivalent delta (the refunded price) is applied instead. This
                // reaches the same end state for the local cache without needing a new method
                // on a class outside this integration's scope.
                if (balance >= 0 && plugin.getCoinsManager() != null) plugin.getCoinsManager().addCoinsLocal(row.player(), row.price());
                return "retry".equals(action) ? "Lieferung steht wieder zur Abholung bereit." : row.price() + " Coins wurden erstattet.";
            } catch (Exception failure) {
                try { database.rollback(); } catch (Exception rollback) { failure.addSuppressed(rollback); }
                throw failure;
            }
        }
    }

    /** Internal event rewards only; unique event/player identity makes retries safe after lost acknowledgements. */
    public boolean isQueueAvailable() {
        try (Connection db=connection(); PreparedStatement check=db.prepareStatement("SELECT purchase_id,delivery_type,status FROM web_deliveries LIMIT 0")) {
            check.executeQuery().close(); return true;
        } catch (Exception error) { return false; }
    }

    public boolean enqueueConvoyReward(UUID event, UUID player, Material material, int amount) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Server thread required");
        if (event==null || player==null || material==null || !material.isItem() || material.isAir() || amount<1 || amount>64) return false;
        String id="convoy-"+UUID.nameUUIDFromBytes((event+":"+player).getBytes(java.nio.charset.StandardCharsets.UTF_8));
        try(Connection db=connection()) {
            try(PreparedStatement insert=db.prepareStatement("INSERT INTO web_deliveries (purchase_id,player_uuid,delivery_type,item_key,material,amount,price,status,created_at,updated_at) VALUES(?,?,'CONVOY',?,?,?,0,'PENDING',?,?) ON DUPLICATE KEY UPDATE purchase_id=purchase_id")) {
                insert.setString(1,id);insert.setString(2,player.toString());insert.setString(3,event.toString());insert.setString(4,material.name());
                insert.setInt(5,amount);insert.setLong(6,System.currentTimeMillis());insert.setLong(7,System.currentTimeMillis());insert.executeUpdate();
            }
            try(PreparedStatement check=db.prepareStatement("SELECT 1 FROM web_deliveries WHERE purchase_id=? AND player_uuid=? AND delivery_type='CONVOY' AND item_key=? AND material=? AND amount=? AND price=0")) {
                check.setString(1,id);check.setString(2,player.toString());check.setString(3,event.toString());check.setString(4,material.name());check.setInt(5,amount);
                try(ResultSet row=check.executeQuery()){return row.next();}
            }
        } catch(Exception failure) { plugin.getLogger().warning("[Convoy] Zustellung bleibt offen: "+failure.getClass().getSimpleName());return false; }
    }

    public Map<String, Object> collect(Player player, String purchaseId) { return deliver(player, purchaseId); }

    public Map<String, Object> status(String purchaseId, UUID uuid) {
        try (Connection connection = connection()) {
            Map<String, Object> status = status(connection, purchaseId, uuid);
            return status == null ? result(false, "UNKNOWN", purchaseId, "delivery not found") : status;
        } catch (Exception exception) {
            return result(false, "UNKNOWN", purchaseId, "delivery unavailable");
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> processPending(event.getPlayer()), 60L);
    }

    private void processPending(Player player) {
        if (!player.isOnline()) return;
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT purchase_id FROM web_deliveries WHERE player_uuid=? AND status='PENDING' ORDER BY id ASC LIMIT 25")) {
            statement.setString(1, player.getUniqueId().toString());
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) deliver(player, rows.getString("purchase_id"));
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("[WebDelivery] Join-Verarbeitung fehlgeschlagen: " + exception.getMessage());
        }
    }

    private Map<String, Object> deliver(Player player, String purchaseId) {
        if (!Bukkit.isPrimaryThread()) throw new IllegalStateException("Delivery requires server thread");
        if (!player.isOnline() || player.isDead() || player.getGameMode() != GameMode.SURVIVAL)
            return result(true, "PENDING", purchaseId, "delivery requires survival");
        var raids = plugin.getRaidSessionService();
        if (raids != null && (raids.isInRaid(player) || raids.isScouting(player))
                || plugin.getRaidManager() != null && plugin.getRaidManager().getRaidForPlayer(player.getUniqueId()) != null)
            return result(true, "PENDING", purchaseId, "delivery waits until after raid");
        try (Connection connection = connection(); PreparedStatement statement = connection.prepareStatement(
                "SELECT material,amount,price,status,delivery_type FROM web_deliveries WHERE purchase_id=? AND player_uuid=? LIMIT 1")) {
            statement.setString(1, purchaseId);
            statement.setString(2, player.getUniqueId().toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return result(false, "UNKNOWN", purchaseId, "delivery not found");
                String current = row.getString("status");
                if (!"PENDING".equals(current)) return result("DELIVERED".equals(current), current, purchaseId, null);
                Material material = Material.matchMaterial(row.getString("material"));
                int amount = row.getInt("amount");
                int price = row.getInt("price");
                boolean eventReward="CONVOY".equals(row.getString("delivery_type"));
                if (material == null || !material.isItem() || material.isAir() || amount < 1 || amount > (eventReward?64:2304) || (eventReward?price!=0:price<1)) {
                    try (PreparedStatement invalid = connection.prepareStatement("UPDATE web_deliveries SET status='REVIEW',error_message='invalid delivery requires review',updated_at=? WHERE purchase_id=? AND player_uuid=? AND status='PENDING'")) {
                        invalid.setLong(1, System.currentTimeMillis()); invalid.setString(2, purchaseId); invalid.setString(3, player.getUniqueId().toString()); invalid.executeUpdate();
                    }
                    return result(false, "REVIEW", purchaseId, "invalid delivery requires review");
                }
                // Keep the last durable player receipt available while an earlier handoff is unresolved.
                try (PreparedStatement uncertain = connection.prepareStatement("SELECT 1 FROM web_deliveries WHERE player_uuid=? AND status='DELIVERING' LIMIT 1")) {
                    uncertain.setString(1, player.getUniqueId().toString());
                    try (ResultSet pending = uncertain.executeQuery()) {
                        if (pending.next()) return result(false, "REVIEW", purchaseId, "earlier delivery requires review");
                    }
                }
                ItemStack[] before = player.getInventory().getStorageContents();
                ItemStack[] after = DeliveryInventory.plan(before, new ItemStack(material), amount);
                if (after == null) {
                    player.sendMessage("§9VetroForge §8• §eDeine Lieferung wartet. Schaffe Platz und nutze /deliveries.");
                    return result(true, "PENDING", purchaseId, null);
                }
                player.saveData();
                try (PreparedStatement claim = connection.prepareStatement("UPDATE web_deliveries SET status='DELIVERING',before_inventory=?,after_inventory=?,updated_at=? WHERE purchase_id=? AND player_uuid=? AND status='PENDING'")) {
                    claim.setString(1, DeliveryInventory.encode(before)); claim.setString(2, DeliveryInventory.encode(after));
                    claim.setLong(3, System.currentTimeMillis()); claim.setString(4, purchaseId); claim.setString(5, player.getUniqueId().toString());
                    if (claim.executeUpdate() != 1) return result(false, "REVIEW", purchaseId, "delivery state changed");
                }
                player.getInventory().setStorageContents(after);
                player.getPersistentDataContainer().set(receiptKey, PersistentDataType.STRING, purchaseId);
                player.saveData();
                VaultPlayerReceipt.verify(Bukkit.getWorlds().getFirst().getWorldFolder().toPath().resolve("playerdata")
                        .resolve(player.getUniqueId() + ".dat"), receiptKey.toString(), purchaseId);
                try (PreparedStatement finish = connection.prepareStatement("UPDATE web_deliveries SET status='DELIVERED',error_message=NULL,updated_at=? WHERE purchase_id=? AND player_uuid=? AND status='DELIVERING'")) {
                    finish.setLong(1, System.currentTimeMillis()); finish.setString(2, purchaseId); finish.setString(3, player.getUniqueId().toString());
                    if (finish.executeUpdate() != 1) return result(false, "REVIEW", purchaseId, "delivery requires review");
                }
                player.sendMessage("§9VetroForge §8• §a"+(eventReward?"Konvoi-Belohnung":"Web-Kauf")+" zugestellt: §f" + amount + "x " + material.name());
                return result(true, "DELIVERED", purchaseId, null);
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("[WebDelivery] Zustellung fehlgeschlagen: " + exception.getMessage());
            return result(false, "REVIEW", purchaseId, "delivery requires review");
        }
    }

    private void ensureSchema() {
        try (Connection connection = connection(); Statement statement = connection.createStatement()) {
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS web_deliveries (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        purchase_id VARCHAR(64) NOT NULL UNIQUE,
                        player_uuid VARCHAR(36) NOT NULL,
                        delivery_type VARCHAR(24) NOT NULL,
                        item_key VARCHAR(128) NOT NULL,
                        material VARCHAR(64) NOT NULL,
                        amount INT NOT NULL,
                        price INT NOT NULL,
                        status VARCHAR(24) NOT NULL DEFAULT 'PENDING',
                        error_message VARCHAR(255) DEFAULT NULL,
                        before_inventory MEDIUMTEXT DEFAULT NULL,
                        after_inventory MEDIUMTEXT DEFAULT NULL,
                        created_at BIGINT NOT NULL,
                        updated_at BIGINT NOT NULL,
                        INDEX idx_web_delivery_player (player_uuid, status, created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            statement.executeUpdate("""
                    CREATE TABLE IF NOT EXISTS web_delivery_audit (
                        id BIGINT AUTO_INCREMENT PRIMARY KEY,
                        purchase_id VARCHAR(64) NOT NULL,
                        player_uuid VARCHAR(36) NOT NULL,
                        actor_uuid VARCHAR(36) NOT NULL,
                        actor_name VARCHAR(32) NOT NULL,
                        action VARCHAR(24) NOT NULL,
                        before_status VARCHAR(24) NOT NULL,
                        after_status VARCHAR(24) NOT NULL,
                        created_at BIGINT NOT NULL,
                        INDEX idx_delivery_audit_purchase (purchase_id,created_at),
                        INDEX idx_delivery_audit_actor (actor_uuid,created_at)
                    ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                    """);
            for (String column : List.of("before_inventory", "after_inventory")) {
                try (ResultSet columns = connection.getMetaData().getColumns(connection.getCatalog(), null, "web_deliveries", column)) {
                    if (!columns.next()) {
                        // Legacy PENDING rows have no durable proof that inventory was untouched.
                        statement.executeUpdate("UPDATE web_deliveries SET status='REVIEW',error_message='legacy delivery requires review' WHERE status='PENDING'");
                        statement.executeUpdate("ALTER TABLE web_deliveries ADD COLUMN " + column + " MEDIUMTEXT DEFAULT NULL");
                    }
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("[WebDelivery] Schema konnte nicht erstellt werden: " + exception.getMessage());
        }
    }

    private Connection connection() throws Exception {
        String host = plugin.getConfig().getString("mysql.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String database = plugin.getConfig().getString("mysql.database", "VF");
        String user = plugin.getConfig().getString("mysql.user", plugin.getConfig().getString("mysql.username", "VF"));
        String password = System.getenv("VF_MYSQL_PASSWORD");
        if (password == null || password.isBlank()) password = plugin.getConfig().getString("mysql.password", "");
        return DriverManager.getConnection("jdbc:mysql://" + host + ":" + port + "/" + database + "?useSSL=false&connectTimeout=3000&socketTimeout=3000", user, password);
    }

    private Map<String, Object> status(Connection connection, String purchaseId, UUID uuid) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "SELECT status,error_message,price,amount,item_key FROM web_deliveries WHERE purchase_id=? AND player_uuid=? LIMIT 1")) {
            statement.setString(1, purchaseId);
            statement.setString(2, uuid.toString());
            try (ResultSet row = statement.executeQuery()) {
                if (!row.next()) return null;
                String deliveryStatus = row.getString("status");
                Map<String, Object> result = result("DELIVERED".equals(deliveryStatus) || "PENDING".equals(deliveryStatus), deliveryStatus, purchaseId, row.getString("error_message"));
                result.put("price", row.getInt("price"));
                result.put("amount", row.getInt("amount"));
                result.put("id", row.getString("item_key"));
                return result;
            }
        }
    }

    private void updateStatus(Connection connection, String purchaseId, String status, String error) throws Exception {
        try (PreparedStatement statement = connection.prepareStatement(
                "UPDATE web_deliveries SET status=?,error_message=?,updated_at=? WHERE purchase_id=?")) {
            statement.setString(1, status);
            statement.setString(2, error);
            statement.setLong(3, System.currentTimeMillis());
            statement.setString(4, purchaseId);
            statement.executeUpdate();
        }
    }

    private Map<String, Object> result(boolean ok, String status, String purchaseId, String error) {
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("ok", ok);
        result.put("status", status);
        result.put("purchaseId", purchaseId);
        if (error != null) result.put("err", error);
        return result;
    }
}
