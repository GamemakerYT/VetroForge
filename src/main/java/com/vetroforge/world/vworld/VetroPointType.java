// src/main/java/com/vetroforge/world/vworld/VetroPointType.java
package com.vetroforge.world.vworld;

import java.util.Locale;

public enum VetroPointType {

    SPAWN,
    RAIDSPAWN,
    SCOUTSPAWN,
    PASTEORIGIN,

    WARZONEGATE,
    WARZONESPAWN,
    BOSS,

    LOOTDROP1,
    LOOTDROP2,
    LOOTDROP3,
    LOOTDROP4;

    public static VetroPointType from(String input) {
        if (input == null || input.isBlank()) return null;

        String normalized = input
                .replace("_", "")
                .replace("-", "")
                .toUpperCase(Locale.ROOT);

        try {
            return VetroPointType.valueOf(normalized);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public String key() {
        return name().toLowerCase(Locale.ROOT);
    }
}