// src/main/java/com/vetroforge/lootdrop/model/Tier.java
package com.vetroforge.lootdrop.model;

import org.bukkit.ChatColor;

public enum Tier {
    BRONZE(ChatColor.GOLD + "Bronze"),
    SILVER(ChatColor.GRAY + "Silver"),
    GOLD(ChatColor.YELLOW + "" + ChatColor.BOLD + "Gold"),
    MYTHIC(ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Mythic");

    private final String display;

    Tier(String d) {
        this.display = d;
    }

    public String displayName() {
        return display;
    }

    /** Leichte Aufwertung (für Double-Drop-Boost). */
    public Tier upBias() {
        switch (this) {
            case BRONZE: return SILVER;
            case SILVER: return GOLD;
            case GOLD:   return MYTHIC;
            default:     return MYTHIC;
        }
    }
}