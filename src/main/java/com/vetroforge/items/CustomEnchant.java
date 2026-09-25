// src/main/java/com/vetroforge/items/CustomEnchant.java
package com.vetroforge.items;

public enum CustomEnchant {
    LIFESTEAL("Lebensraub"),   // heilt % des verursachten Schadens
    BLEED("Blutung"),         // zusätzlicher True-DMG über Zeit
    RAGE("Raserei"),          // kurzer DMG-Boost nach Treffer
    SHATTER("Zerschmetterer"),// +% Schaden (skalierend)
    GUARD("Wächter");         // -% eingehender Schaden

    /** Anzeigename, einheitlich mit dem Raid-Runen-System ("Rune: <display>"). */
    public final String display;

    CustomEnchant(String display) {
        this.display = display;
    }
}