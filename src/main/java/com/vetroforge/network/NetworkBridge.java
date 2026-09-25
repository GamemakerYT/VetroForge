package com.vetroforge.network;

import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import redis.clients.jedis.Jedis;
import redis.clients.jedis.JedisPubSub;

import java.util.UUID;
import java.util.concurrent.CompletableFuture;


public class NetworkBridge {

    private final JavaPlugin plugin;
    private final String redisHost;
    private final int redisPort;
    private final String redisPassword;

    private CoinsManager coinsManager;

    public NetworkBridge(JavaPlugin plugin, String host, int port, String password) {
        this.plugin = plugin;
        this.redisHost = host;
        this.redisPort = port;
        this.redisPassword = password;
    }

    public void attachCoinsManager(CoinsManager manager) {
        this.coinsManager = manager;
    }

    // ===========================
    // Start
    // ===========================

    public void start() {
        plugin.getLogger().info("[NetworkBridge] Verbinde mit Redis...");
        CompletableFuture.runAsync(this::subscribeChannels);
    }

    private void subscribeChannels() {
        try (Jedis jedis = connect()) {
            String bundles = jedis.get("vf:language:bundles:snapshot");
            if (bundles != null && plugin instanceof com.vetroforge.FactionsPlugin factions && factions.getI18n() != null) {
                factions.getI18n().applyRemoteBundles(bundles);
            }
            jedis.subscribe(new JedisPubSub() {

                                @Override
                                public void onMessage(String channel, String message) {
                                    handle(channel, message);
                                }
                            },
                    "coins:add", "coins:remove", "coins:set",
                    "friends:request", "friends:accept", "friends:remove",
                    "chat:broadcast", "chat:msg", "vf:language:bundles");
        } catch (Exception e) {
            plugin.getLogger().warning("[NetworkBridge] Redis Subscribe Error: " + e.getMessage());
        }
    }

    // ===========================
    // Eingehende Events
    // ===========================

    private void handle(String channel, String msg) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            try {
                switch (channel.toLowerCase()) {

                    // --- Coins ---
                    case "coins:add" -> handleCoins(msg, true);
                    case "coins:remove" -> handleCoins(msg, false);
                    case "coins:set" -> handleCoinsSet(msg);

                    // --- Friends ---
                    case "friends:request" -> handleFriendRequest(msg);
                    case "friends:accept" -> handleFriendAccept(msg);
                    case "friends:remove" -> handleFriendRemove(msg);

                    // --- Chat ---
                    case "chat:broadcast" -> Bukkit.broadcastMessage("§b§lNETZWERK §8» §7" + msg);
                    case "chat:msg" -> handlePrivateMessage(msg);
                    case "vf:language:bundles" -> {
                        if (plugin instanceof com.vetroforge.FactionsPlugin factions && factions.getI18n() != null) {
                            factions.getI18n().applyRemoteBundles(msg);
                        }
                    }
                }

            } catch (Exception e) {
                plugin.getLogger().warning("[NetworkBridge] Fehler beim Verarbeiten von " + channel + ": " + e.getMessage());
            }
        });
    }

    private void handleCoins(String msg, boolean add) {
        String[] p = msg.split(":");
        if (p.length < 2) return;

        UUID uuid = UUID.fromString(p[0]);
        int amount = Integer.parseInt(p[1]);

        if (coinsManager == null) return;

        if (add) coinsManager.addCoinsLocal(uuid, amount);
        else coinsManager.removeCoinsLocal(uuid, amount);

        Player player = Bukkit.getPlayer(uuid);
        if (player != null) {
            String action = add ? "§a+" : "§c-";
            player.sendMessage("§6" + action + amount + " Coins §7(" + coinsManager.getCoins(uuid) + " gesamt)");
        }
    }

    private void handleCoinsSet(String msg) {
        String[] p = msg.split(":");
        if (p.length < 2) return;

        UUID uuid = UUID.fromString(p[0]);
        int amount = Integer.parseInt(p[1]);

        if (coinsManager != null)
            coinsManager.setCoins(uuid, amount); // OK, KEIN publish drin

        Player player = Bukkit.getPlayer(uuid);
        if (player != null)
            player.sendMessage("§eDein Kontostand wurde auf §6" + amount + " Coins §egesetzt.");
    }

    // ===========================
    // Friends
    // ===========================

    private void handleFriendRequest(String msg) {
        String[] p = msg.split(":");
        if (p.length < 2) return;
        UUID from = UUID.fromString(p[0]);
        UUID to = UUID.fromString(p[1]);

        Player target = Bukkit.getPlayer(to);
        if (target != null)
            target.sendMessage("§b§lFREUNDE §8» §7Du hast eine Anfrage von §f" + Bukkit.getOfflinePlayer(from).getName());
    }

    private void handleFriendAccept(String msg) {
        String[] p = msg.split(":");
        if (p.length < 2) return;
        UUID from = UUID.fromString(p[0]);
        UUID to = UUID.fromString(p[1]);

        Player target = Bukkit.getPlayer(to);
        if (target != null)
            target.sendMessage("§b§lFREUNDE §8» §f" + Bukkit.getOfflinePlayer(from).getName() + " §7hat deine Anfrage akzeptiert!");
    }

    private void handleFriendRemove(String msg) {
        String[] p = msg.split(":");
        if (p.length < 2) return;
        UUID from = UUID.fromString(p[0]);
        UUID to = UUID.fromString(p[1]);

        Player target = Bukkit.getPlayer(to);
        if (target != null)
            target.sendMessage("§b§lFREUNDE §8» §f" + Bukkit.getOfflinePlayer(from).getName() + " §7hat dich entfernt.");
    }

    // ===========================
    // Private Messages
    // ===========================

    private void handlePrivateMessage(String msg) {
        String[] p = msg.split(":", 3);
        if (p.length < 3) return;
        UUID from = UUID.fromString(p[0]);
        UUID to = UUID.fromString(p[1]);
        String text = p[2];

        Player target = Bukkit.getPlayer(to);
        if (target != null)
            target.sendMessage("§8[§bMSG§8] §f" + Bukkit.getOfflinePlayer(from).getName() + " §8» §7" + text);
    }

    // ===========================
    // Publish Utility
    // ===========================

    public void publish(String channel, String message) {
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = connect()) {
                jedis.publish(channel, message);
            } catch (Exception e) {
                plugin.getLogger().warning("[NetworkBridge] Publish Error (" + channel + "): " + e.getMessage());
            }
        });
    }

    // ===========================
    // Verbindung
    // ===========================

    public Jedis connect() {
        Jedis jedis = new Jedis(redisHost, redisPort);
        if (redisPassword != null && !redisPassword.isEmpty())
            jedis.auth(redisPassword);
        return jedis;
    }
}
