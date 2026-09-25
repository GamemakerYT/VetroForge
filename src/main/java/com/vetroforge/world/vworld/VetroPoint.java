// src/main/java/com/vetroforge/world/vworld/VetroPoint.java
package com.vetroforge.world.vworld;

import org.bukkit.Location;
import org.bukkit.World;

public final class VetroPoint {

    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;

    public VetroPoint(double x, double y, double z, float yaw, float pitch) {
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
    }

    public static VetroPoint from(Location location) {
        return new VetroPoint(
                location.getX(),
                location.getY(),
                location.getZ(),
                location.getYaw(),
                location.getPitch()
        );
    }

    public Location toLocation(World world) {
        return new Location(world, x, y, z, yaw, pitch);
    }

    public double x() {
        return x;
    }

    public double y() {
        return y;
    }

    public double z() {
        return z;
    }

    public float yaw() {
        return yaw;
    }

    public float pitch() {
        return pitch;
    }
}