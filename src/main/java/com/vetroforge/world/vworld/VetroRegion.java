// src/main/java/com/vetroforge/world/vworld/VetroRegion.java
package com.vetroforge.world.vworld;

import org.bukkit.Location;

public final class VetroRegion {

    private final VetroRegionType type;

    private final int minX;
    private final int minY;
    private final int minZ;

    private final int maxX;
    private final int maxY;
    private final int maxZ;

    public VetroRegion(VetroRegionType type, Location pos1, Location pos2) {
        this(
                type,
                Math.min(pos1.getBlockX(), pos2.getBlockX()),
                Math.min(pos1.getBlockY(), pos2.getBlockY()),
                Math.min(pos1.getBlockZ(), pos2.getBlockZ()),
                Math.max(pos1.getBlockX(), pos2.getBlockX()),
                Math.max(pos1.getBlockY(), pos2.getBlockY()),
                Math.max(pos1.getBlockZ(), pos2.getBlockZ())
        );
    }

    public VetroRegion(VetroRegionType type, int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        this.type = type;
        this.minX = Math.min(minX, maxX);
        this.minY = Math.min(minY, maxY);
        this.minZ = Math.min(minZ, maxZ);
        this.maxX = Math.max(minX, maxX);
        this.maxY = Math.max(minY, maxY);
        this.maxZ = Math.max(minZ, maxZ);
    }

    public boolean contains(Location location) {
        if (location == null) return false;

        int x = location.getBlockX();
        int y = location.getBlockY();
        int z = location.getBlockZ();

        return x >= minX && x <= maxX
                && y >= minY && y <= maxY
                && z >= minZ && z <= maxZ;
    }

    public VetroRegionType type() {
        return type;
    }

    public int minX() {
        return minX;
    }

    public int minY() {
        return minY;
    }

    public int minZ() {
        return minZ;
    }

    public int maxX() {
        return maxX;
    }

    public int maxY() {
        return maxY;
    }

    public int maxZ() {
        return maxZ;
    }
}