package com.vetroforge.services;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.ActionBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class RegionDebugService {

    private static RegionDebugService instance;

    private final FactionsPlugin plugin;
    private final Set<UUID> enabled = new HashSet<>();

    public RegionDebugService(FactionsPlugin plugin) {
        this.plugin = plugin;
        instance = this;
        Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 20L, 20L);
    }

    public static boolean toggle(Player player) {
        if (instance == null || player == null) return false;
        if (instance.enabled.remove(player.getUniqueId())) {
            ActionBarUtil.send(player, "§cRegion-Debug aus");
            return false;
        }
        instance.enabled.add(player.getUniqueId());
        ActionBarUtil.send(player, "§aRegion-Debug aktiv");
        return true;
    }

    private void tick() {
        for (UUID id : new HashSet<>(enabled)) {
            Player player = Bukkit.getPlayer(id);
            if (player == null || !player.isOnline()) {
                enabled.remove(id);
                continue;
            }
            String zone = "§7Außerhalb";
            if (plugin.getWarzoneRegionManager() != null) {
                if (plugin.getWarzoneRegionManager().isSpawn(player.getLocation())) zone = "§aSpawn Safezone";
                else if (plugin.getWarzoneRegionManager().isWarzone(player.getLocation())) zone = "§cWarzone PvP";
                else if (plugin.getWarzoneRegionManager().isInside(player.getLocation())) zone = "§eWarzone Cutout";
            }
            ActionBarUtil.send(player, "§6Region §8» " + zone + " §8• §7" + shortLoc(player));
        }
    }

    private String shortLoc(Player player) {
        return player.getWorld().getName() + " "
                + player.getLocation().getBlockX() + "/"
                + player.getLocation().getBlockY() + "/"
                + player.getLocation().getBlockZ();
    }
}
