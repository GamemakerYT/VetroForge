// src/main/java/com/vetroforge/lootdrop/NoDropService.java
package com.vetroforge.lootdrop;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.warzone.WarzoneRegionManager;
import org.bukkit.Location;

import java.util.HashSet;
import java.util.Set;

public class NoDropService {
    private final FactionsPlugin plugin;
    private final Set<String> worldBlacklist = new HashSet<>();

    public NoDropService(FactionsPlugin plugin){
        this.plugin = plugin;
        worldBlacklist.addAll(plugin.getConfig().getStringList("lootdrop.noDropWorlds")); // z. B. ["lobby","spawn"]
    }

    public boolean isAllowedDrop(Location l){
        if (l == null || l.getWorld() == null) return false;
        if (worldBlacklist.contains(l.getWorld().getName())) return false;

        try {
            WarzoneRegionManager wz = plugin.getWarzoneRegionManager();
            if (wz != null && wz.isSpawnSafeMode()) {
                return wz.isWarzone(l);
            }
        } catch (Throwable ignored) {}

        return true;
    }
}
