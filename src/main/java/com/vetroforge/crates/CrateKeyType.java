// src/main/java/com/vetroforge/crates/CrateKeyType.java
package com.vetroforge.crates;

public enum CrateKeyType {
    COMMON("§7§lCommon Crystal"),
    RARE("§9§lRare Crystal"),
    LEGENDARY("§6§lLegendary Crystal"),
    EPIC("§d§lEpic Crystal"),
    MYSTIC("§5§lMystic Crystal"); // nur bei Mystic Storm

    public final String display;
    CrateKeyType(String d){ this.display = d; }
}
