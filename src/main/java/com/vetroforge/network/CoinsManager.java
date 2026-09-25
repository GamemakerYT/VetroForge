package com.vetroforge.network;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.persistence.sql.NetworkDao;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class CoinsManager implements Listener {

    /** Sicherheitsgrenze weit über jedem realistischen Kontostand -- verhindert int-Overflow bei addCoins. */
    private static final int MAX_BALANCE = 2_000_000_000;

    private final FactionsPlugin plugin;
    private final NetworkBridge network;
    private final NetworkDao networkDao;

    private final Map<UUID, Integer> coins = new ConcurrentHashMap<>();
    private final Map<UUID, Boolean> loading = new ConcurrentHashMap<>();

    public CoinsManager(FactionsPlugin plugin, NetworkBridge network, NetworkDao networkDao) {
        this.plugin = plugin;
        this.network = network;
        this.networkDao = networkDao;
    }

    // ===========================
    // JOIN → ASYNC LOAD
    // ===========================

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        UUID uuid = e.getPlayer().getUniqueId();
        if (networkDao == null) {
            coins.putIfAbsent(uuid, 0);
            return;
        }
        loading.put(uuid, true);

        networkDao.getCoins(uuid).thenAccept(dbCoins -> {
            coins.put(uuid, dbCoins);
            loading.remove(uuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage("§7Coins geladen: §e" + dbCoins);
                    refreshScoreboard(uuid);
                }
            });
        });
    }

    // ===========================
    // GET
    // ===========================

    public int getCoins(UUID uuid) {
        if (loading.containsKey(uuid)) return -1;
        return coins.getOrDefault(uuid, 0);
    }

    // ===========================
    // MODIFY
    // ===========================

    public void setCoins(UUID uuid, int amount) {
        coins.put(uuid, Math.max(0, amount));
        if (networkDao != null) networkDao.setCoins(uuid, Math.max(0, amount));
        refreshScoreboard(uuid);
    }

    public void addCoins(UUID uuid, int amount) {
        if (amount <= 0) return;
        coins.merge(uuid, amount, (cur, add) -> (int) Math.min((long) cur + add, MAX_BALANCE));
        if (networkDao != null) networkDao.addCoins(uuid, amount);
        if (network != null) network.publish("coins:add", uuid + ":" + amount);
        refreshScoreboard(uuid);
    }

    public void removeCoins(UUID uuid, int amount) {
        if (amount <= 0) return;
        coins.compute(uuid, (k, v) -> Math.max(0, (v == null ? 0 : v) - amount));
        if (networkDao != null) networkDao.removeCoins(uuid, amount);
        if (network != null) network.publish("coins:remove", uuid + ":" + amount);
        refreshScoreboard(uuid);
    }
    public void addCoinsToFaction(String faction, int amount) {
        if (faction == null) return;

        var members = plugin.getFactionManager().getMembers(faction);
        if (members == null || members.isEmpty()) return;

        for (UUID uuid : members) {
            addCoins(uuid, amount);
        }
    }

    public void loadCoins(UUID uuid) {
        System.out.println("DEBUG: Lade Coins für " + uuid);
        if (networkDao == null) {
            coins.putIfAbsent(uuid, 0);
            loading.remove(uuid);
            return;
        }

        networkDao.getCoins(uuid).whenComplete((dbCoins, err) -> {

            if (err != null) {
                System.out.println("DEBUG ERROR: " + err.getMessage());
                err.printStackTrace();
                loading.remove(uuid);
                return;
            }

            System.out.println("DEBUG SUCCESS: Coins = " + dbCoins);

            coins.put(uuid, dbCoins);
            loading.remove(uuid);

            Bukkit.getScheduler().runTask(plugin, () -> {
                Player p = Bukkit.getPlayer(uuid);
                if (p != null) {
                    p.sendMessage("§aCoins geladen: " + dbCoins);
                    refreshScoreboard(uuid);
                }
            });
        });
    }
    public void addCoinsLocal(UUID uuid, int amount) {
        if (amount <= 0) return;
        coins.merge(uuid, amount, Integer::sum);
        refreshScoreboard(uuid);
    }

    public void removeCoinsLocal(UUID uuid, int amount) {
        if (amount <= 0) return;
        coins.compute(uuid, (k, v) -> Math.max(0, (v == null ? 0 : v) - amount));
        refreshScoreboard(uuid);
    }

    private void refreshScoreboard(UUID uuid) {
        if (uuid == null) return;
        Runnable task = () -> {
            Player player = Bukkit.getPlayer(uuid);
            if (player == null || !player.isOnline() || plugin.getSidebarScoreboardManager() == null) return;

            var raid = plugin.getRaidManager() == null ? null : plugin.getRaidManager().getRaidForPlayer(uuid);
            if (raid != null) {
                plugin.getSidebarScoreboardManager().showRaidBoard(
                        player,
                        raid.targetFaction,
                        raid.obsidianBroken,
                        raid.obsidianGoal,
                        raid.secondsLeft,
                        raid.raiders.size()
                );
                return;
            }

            if (plugin.getWarzoneRegionManager() != null && plugin.getWarzoneRegionManager().isWarzone(player.getLocation())) {
                plugin.getSidebarScoreboardManager().showWarzoneBoard(player);
            } else {
                plugin.getSidebarScoreboardManager().showLobbyBoard(player);
            }
        };

        if (Bukkit.isPrimaryThread()) {
            task.run();
        } else {
            Bukkit.getScheduler().runTask(plugin, task);
        }
    }
}
