// src/main/java/com/vetroforge/world/MysticStormService.java
package com.vetroforge.world;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;

public class MysticStormService {
    private final FactionsPlugin plugin;
    private volatile boolean manual = false;

    public MysticStormService(FactionsPlugin plugin){ this.plugin = plugin; }

    /** Aktiv, wenn Nacht (13k–23k) ODER manuell getoggled. */
    public boolean isActive(World w){
        if (manual) return true;
        if (w == null) w = Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
        if (w == null) return false;
        long t = w.getTime(); // 0..23999
        return t >= 13000 && t <= 23000;
    }

    public void setManual(boolean on){ this.manual = on; }
}