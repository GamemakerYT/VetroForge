// src/main/java/com/vetroforge/world/vworld/VetroRegionSelectionService.java
package com.vetroforge.world.vworld;

import org.bukkit.Location;
import org.bukkit.entity.Player;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public final class VetroRegionSelectionService {

    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();

    public void setPos1(Player player, Location location) {
        if (player == null || location == null) return;
        pos1.put(player.getUniqueId(), location.clone());
    }

    public void setPos2(Player player, Location location) {
        if (player == null || location == null) return;
        pos2.put(player.getUniqueId(), location.clone());
    }

    public Location getPos1(Player player) {
        if (player == null) return null;
        Location location = pos1.get(player.getUniqueId());
        return location == null ? null : location.clone();
    }

    public Location getPos2(Player player) {
        if (player == null) return null;
        Location location = pos2.get(player.getUniqueId());
        return location == null ? null : location.clone();
    }

    public boolean hasSelection(Player player) {
        return getPos1(player) != null && getPos2(player) != null;
    }

    public void clear(Player player) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();
        pos1.remove(uuid);
        pos2.remove(uuid);
    }
}