package com.vetroforge.hologram;

import java.util.Locale;

public enum HologramType {
    TOP_FACTIONS("Top Factions"),
    TOP_CRYSTALS("Top Crystals"),
    TOP_KILLS("Top Kills"),
    TOP_PRESTIGE("Top Prestige"),
    WARZONE("Warzone Live");

    private final String displayName;

    HologramType(String displayName) {
        this.displayName = displayName;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getConfigKey() {
        return name().toLowerCase(Locale.ROOT).replace('_', '-');
    }

    public static HologramType fromString(String input) {
        if (input == null) {
            return null;
        }
        String normalized = input.trim().toUpperCase(Locale.ROOT).replace('-', '_');
        try {
            return valueOf(normalized);
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
