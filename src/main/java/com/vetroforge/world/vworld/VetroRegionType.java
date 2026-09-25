// src/main/java/com/vetroforge/world/vworld/VetroRegionType.java
package com.vetroforge.world.vworld;

import java.util.Locale;

public enum VetroRegionType {

    BUILD,
    RAID,
    BORDER,
    TERRAIN,
    NOBREAK,

    SPAWN,
    SPAWN_SAFE,
    WARZONE,
    WARZONE_GATE,
    LOOTDROP,
    BOSS,
    SCOUT;

    public static VetroRegionType from(String input) {
        if (input == null || input.isBlank()) return null;

        String normalized = input
                .replace("-", "_")
                .toUpperCase(Locale.ROOT);

        try {
            return VetroRegionType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}