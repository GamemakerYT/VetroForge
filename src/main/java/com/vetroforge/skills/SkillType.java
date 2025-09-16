package com.vetroforge.skills;

import java.util.Locale;

public enum SkillType {
    ASSASSIN,
    HEALER,
    WARRIOR,
    RAIDER;

    public static SkillType fromString(String input) {
        if (input == null) {
            return null;
        }
        try {
            return valueOf(input.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    public String displayName() {
        String lower = name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
