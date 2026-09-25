package com.vetroforge.network;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;
import redis.clients.jedis.Jedis;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public final class PresenceService implements Listener {

    private static final String PLAYER_PREFIX = "presence:player:";
    private static final String SERVER_PREFIX = "presence:server:";
    private static final String ONLINE_SET = "presence:online";
    private static final String EVENT_CHANNEL = "presence:events";

    private final FactionsPlugin plugin;
    private final NetworkBridge networkBridge;
    private final String serverName;
    private final int ttlSeconds;
    private final int heartbeatSeconds;

    private BukkitTask heartbeatTask;
    private BukkitTask sanitizerTask;

    public PresenceService(FactionsPlugin plugin, NetworkBridge networkBridge) {
        this.plugin = plugin;
        this.networkBridge = networkBridge;
        this.serverName = normalize(plugin.getConfig().getString("presence.server-name",
                plugin.getConfig().getString("discord-bridge.server-name", "Factions")));
        this.ttlSeconds = Math.max(15, plugin.getConfig().getInt("presence.ttl-seconds", 30));
        this.heartbeatSeconds = Math.max(5, plugin.getConfig().getInt("presence.heartbeat-seconds", 10));
    }

    public void start() {
        Bukkit.getPluginManager().registerEvents(this, plugin);

        for (Player player : Bukkit.getOnlinePlayers()) {
            touch(player, "heartbeat");
        }

        heartbeatTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                touch(player, "heartbeat");
            }
        }, 20L, heartbeatSeconds * 20L);

        sanitizerTask = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin,
                this::sanitizeServerSet,
                20L * 60L,
                20L * 60L);

        plugin.getLogger().info("[Presence] Redis presence started for " + serverName
                + " (heartbeat=" + heartbeatSeconds + "s, ttl=" + ttlSeconds + "s)");
    }

    public void stop() {
        if (heartbeatTask != null) {
            heartbeatTask.cancel();
            heartbeatTask = null;
        }
        if (sanitizerTask != null) {
            sanitizerTask.cancel();
            sanitizerTask = null;
        }

        Set<UUID> online = new HashSet<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            online.add(player.getUniqueId());
        }
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = networkBridge.connect()) {
                for (UUID uuid : online) {
                    remove(jedis, uuid, "shutdown");
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[Presence] Shutdown cleanup failed: " + message(t));
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        touch(event.getPlayer(), "join");
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        String name = event.getPlayer().getName();
        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = networkBridge.connect()) {
                remove(jedis, uuid, "leave");
                publish(jedis, "leave", uuid, name);
            } catch (Throwable t) {
                plugin.getLogger().warning("[Presence] Leave failed for " + name + ": " + message(t));
            }
        });
    }

    private void touch(Player player, String reason) {
        if (player == null) return;
        UUID uuid = player.getUniqueId();
        String name = player.getName();

        CompletableFuture.runAsync(() -> {
            try (Jedis jedis = networkBridge.connect()) {
                jedis.setex(PLAYER_PREFIX + uuid, ttlSeconds, serverName);
                jedis.sadd(SERVER_PREFIX + serverName, uuid.toString());
                jedis.sadd(ONLINE_SET, uuid.toString());
                if ("join".equals(reason)) {
                    publish(jedis, "join", uuid, name);
                }
            } catch (Throwable t) {
                plugin.getLogger().warning("[Presence] Heartbeat failed for " + name + ": " + message(t));
            }
        });
    }

    private void sanitizeServerSet() {
        try (Jedis jedis = networkBridge.connect()) {
            String serverKey = SERVER_PREFIX + serverName;
            Set<String> members = jedis.smembers(serverKey);
            for (String rawUuid : members) {
                String current = jedis.get(PLAYER_PREFIX + rawUuid);
                if (!serverName.equalsIgnoreCase(current)) {
                    jedis.srem(serverKey, rawUuid);
                    boolean stillOnlineSomewhere = false;
                    for (String serverSet : jedis.keys(SERVER_PREFIX + "*")) {
                        if (jedis.sismember(serverSet, rawUuid)) {
                            stillOnlineSomewhere = true;
                            break;
                        }
                    }
                    if (!stillOnlineSomewhere) {
                        jedis.srem(ONLINE_SET, rawUuid);
                    }
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Presence] Sanitizer failed: " + message(t));
        }
    }

    private void remove(Jedis jedis, UUID uuid, String reason) {
        String raw = uuid.toString();
        jedis.del(PLAYER_PREFIX + raw);
        jedis.srem(SERVER_PREFIX + serverName, raw);
        jedis.srem(ONLINE_SET, raw);
        if ("shutdown".equals(reason)) {
            publish(jedis, "leave", uuid, "");
        }
    }

    private void publish(Jedis jedis, String type, UUID uuid, String name) {
        long now = System.currentTimeMillis();
        jedis.publish(EVENT_CHANNEL, "{"
                + "\"type\":\"" + esc(type) + "\","
                + "\"uuid\":\"" + esc(uuid.toString()) + "\","
                + "\"name\":\"" + esc(name == null ? "" : name) + "\","
                + "\"server\":\"" + esc(serverName) + "\","
                + "\"ts\":" + now
                + "}");
    }

    private String normalize(String input) {
        if (input == null || input.isBlank()) return "factions";
        return input.trim().toLowerCase(Locale.ROOT);
    }

    private String esc(String value) {
        return value.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    private String message(Throwable throwable) {
        return throwable.getMessage() == null ? throwable.getClass().getSimpleName() : throwable.getMessage();
    }
}
