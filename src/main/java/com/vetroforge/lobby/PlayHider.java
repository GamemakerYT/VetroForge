// src/main/java/com/vetroforge/lobby/PlayHider.java
package com.vetroforge.lobby;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.commands.PlayHiderCommand.Mode;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PlayHider {

    private final FactionsPlugin plugin;
    private final Map<UUID, Mode> modes = new ConcurrentHashMap<>();

    public PlayHider(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void setMode(Player viewer, Mode mode) {
        if (viewer == null || mode == null) return;
        modes.put(viewer.getUniqueId(), mode);
        refresh(viewer);
    }

    public Mode getMode(Player viewer) {
        if (viewer == null) return Mode.ALL;
        return modes.getOrDefault(viewer.getUniqueId(), Mode.ALL);
    }

    public void refresh(Player viewer) {
        if (viewer == null || !viewer.isOnline()) return;

        Mode mode = getMode(viewer);

        for (Player other : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(other)) continue;

            boolean show = shouldShow(viewer, other, mode);
            if (show) {
                viewer.showPlayer(plugin, other);
            } else {
                viewer.hidePlayer(plugin, other);
            }
        }
    }

    public void refreshAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            refresh(player);
        }
    }

    public void clear(Player viewer) {
        if (viewer == null) return;
        modes.remove(viewer.getUniqueId());
    }

    private boolean shouldShow(Player viewer, Player other, Mode mode) {
        return switch (mode) {
            case ALL -> true;
            case NONE -> false;
            case STAFF -> isStaff(other);
            case FRIENDS -> isSameFaction(viewer, other);
        };
    }

    private boolean isStaff(Player player) {
        return player.hasPermission("vetroforge.staff")
                || player.hasPermission("vetroforge.rank.staff")
                || player.hasPermission("vetroforge.rank.mod")
                || player.hasPermission("vetroforge.rank.admin")
                || player.hasPermission("vetroforge.rank.support")
                || player.hasPermission("vetroforge.admin");
    }

    private boolean isSameFaction(Player a, Player b) {
        if (plugin.getFactionManager() == null) return false;

        String fa = plugin.getFactionManager().getFactionOf(a.getUniqueId());
        String fb = plugin.getFactionManager().getFactionOf(b.getUniqueId());

        return fa != null && fb != null && fa.equalsIgnoreCase(fb);
    }
}