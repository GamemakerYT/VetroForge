// ===================================================================
// File: src/main/java/com/vetroforge/factions/RelationType.java
// ===================================================================
package com.vetroforge.factions;

/** Beziehung zwischen zwei Factions. */
public enum RelationType {
    OWN,       // gleiche Faction
    ALLY,      // Verbündet
    TRUCE,     // Waffenstillstand
    NEUTRAL,   // Standard
    ENEMY,     // Feind
    WAR;       // formaler Krieg (Raid aktiv)

    public boolean isFriendly() { return this == OWN || this == ALLY || this == TRUCE; }
    public boolean isHostile()  { return this == ENEMY || this == WAR; }
}
