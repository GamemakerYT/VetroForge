package com.vetroforge.hologram;

import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class ManagedHologram {

    private final HologramType type;
    private Location location;
    private final List<ArmorStand> armorStands = new ArrayList<>();
    private List<String> lines = Collections.emptyList();

    public ManagedHologram(HologramType type) {
        this.type = type;
    }

    public HologramType getType() {
        return type;
    }

    public Location getLocation() {
        return location;
    }

    public void spawn(Location location, List<String> lines) {
        despawn();
        if (location == null || location.getWorld() == null) {
            return;
        }
        this.location = location.clone();
        this.lines = new ArrayList<>(lines);
        World world = location.getWorld();
        if (!location.getChunk().isLoaded()) {
            location.getChunk().load();
        }
        double spacing = 0.28;
        for (int i = 0; i < lines.size(); i++) {
            String line = ChatColor.translateAlternateColorCodes('&', lines.get(i));
            Location spawnLoc = location.clone().add(0, (lines.size() - i - 1) * spacing, 0);
            ArmorStand stand = world.spawn(spawnLoc, ArmorStand.class, armorStand -> {
                armorStand.setGravity(false);
                armorStand.setSmall(true);
                armorStand.setMarker(true);
                armorStand.setVisible(false);
                armorStand.setCustomNameVisible(true);
                armorStand.setCustomName(line);
                armorStand.setPersistent(false);
            });
            armorStands.add(stand);
        }
    }

    public void updateLines(List<String> newLines) {
        if (location == null || armorStands.isEmpty()) {
            spawn(location, newLines);
            return;
        }
        if (newLines.size() != armorStands.size()) {
            spawn(location, newLines);
            return;
        }
        this.lines = new ArrayList<>(newLines);
        for (int i = 0; i < armorStands.size(); i++) {
            ArmorStand stand = armorStands.get(i);
            if (stand == null || stand.isDead()) {
                spawn(location, newLines);
                return;
            }
            stand.setCustomName(ChatColor.translateAlternateColorCodes('&', newLines.get(i)));
        }
    }

    public void despawn() {
        for (ArmorStand stand : armorStands) {
            if (stand != null && !stand.isDead()) {
                stand.remove();
            }
        }
        armorStands.clear();
        lines = Collections.emptyList();
    }

    public List<String> getLines() {
        return lines;
    }
}
