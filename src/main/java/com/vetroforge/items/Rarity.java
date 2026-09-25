// src/main/java/com/vetroforge/items/Rarity.java
package com.vetroforge.items;

public enum Rarity {
    COMMON   ("Common",    "§7"),
    RARE     ("Rare",      "§9"),
    EPIC     ("Epic",      "§d"),
    LEGENDARY("Legendary", "§6"),
    MYSTIC   ("Mythic",    "§5");

    public final String display;
    public final String color;
    Rarity(String display, String color){ this.display = display; this.color = color; }

    public String colored() {
        return color + display;
    }
}
