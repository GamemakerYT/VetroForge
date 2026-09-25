// src/main/java/com/vetroforge/manager/HologramManager.java
package com.vetroforge.manager;

import org.bukkit.*;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;

public class HologramManager {
    private final JavaPlugin plugin;
    private final Map<UUID, List<ArmorStand>> holos = new HashMap<>();

    public HologramManager(JavaPlugin plugin){ this.plugin = plugin; }

    public UUID create(Location base, List<String> lines){
        UUID id = UUID.randomUUID();
        List<ArmorStand> list = new ArrayList<>();
        double y = base.getY();
        for (String line : lines) {
            Location l = new Location(base.getWorld(), base.getX()+0.5, y, base.getZ()+0.5);
            ArmorStand as = (ArmorStand) base.getWorld().spawnEntity(l, EntityType.ARMOR_STAND);
            as.setInvisible(true);
            as.setMarker(true);
            as.setGravity(false);
            as.setCustomNameVisible(true);
            as.setCustomName(ChatColor.translateAlternateColorCodes('&', line));
            list.add(as);
            y -= 0.27;
        }
        holos.put(id, list);
        return id;
    }

    public void remove(UUID id){
        List<ArmorStand> list = holos.remove(id);
        if (list != null) for (ArmorStand as : list) if (as != null && !as.isDead()) as.remove();
    }
}