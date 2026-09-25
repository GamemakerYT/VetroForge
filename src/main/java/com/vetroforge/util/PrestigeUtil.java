// src/main/java/com/vetroforge/util/PrestigeUtil.java
package com.vetroforge.util;

public final class PrestigeUtil {
    private PrestigeUtil(){}

    private static final String[] TITLES = {
            "Bronze I", "Bronze II", "Bronze III", "Bronze IV",
            "Silber I", "Silber II", "Silber III", "Silber IV",
            "Gold I", "Gold II", "Gold III", "Gold IV",
            "Platin I", "Platin II", "Platin III", "Platin IV",
            "Diamant I", "Diamant II", "Diamant III", "Diamant IV",
            "Mythic I", "Mythic II", "Mythic III", "Mythic IV",
            "Ascendant"
    };

    public static String titleFor(int level){
        int tier = Math.min(TITLES.length - 1, Math.max(0, level) / 10);
        return colorFor(level) + TITLES[tier];
    }

    public static String colorFor(int level) {
        return switch (Math.min(6, Math.max(0, level) / 40)) {
            case 0 -> "§7";
            case 1 -> "§f";
            case 2 -> "§6";
            case 3 -> "§b";
            case 4 -> "§9";
            case 5 -> "§d";
            default -> "§5";
        };
    }
}
