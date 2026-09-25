// ===================================================================
// File: src/main/java/com/vetroforge/factions/RelationColor.java
// ===================================================================
package com.vetroforge.factions;

public final class RelationColor {
    private RelationColor(){}

    /** Chat/Tab/Prefix-Farbe pro Relation */
    public static String colorCode(RelationType r){
        switch (r){
            case OWN:     return "§a"; // grün
            case ALLY:    return "§b"; // aqua
            case TRUCE:   return "§3"; // dark aqua
            case NEUTRAL: return "§7"; // grau
            case ENEMY:   return "§c"; // rot
            case WAR:     return "§4"; // dunkelrot
            default:      return "§7";
        }
    }
}