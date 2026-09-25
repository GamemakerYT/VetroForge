// src/main/java/com/vetroforge/raid/AntiStallService.java
package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

import java.util.*;

public class AntiStallService implements Listener {
    private final FactionsPlugin plugin;
    private final Map<String, Long> lastCombat = new HashMap<>(); // key: raidInstanceId

    public AntiStallService(FactionsPlugin p){
        this.plugin = p;
        p.getServer().getPluginManager().registerEvents(this,p);
    }

    public double stallPenalty(String raidId){
        long last = lastCombat.getOrDefault(raidId, System.currentTimeMillis());
        long secs = (System.currentTimeMillis() - last)/1000;
        if (secs>=240) return 0.90; // -10%
        if (secs>=120) return 0.95; // -5%
        return 1.00;
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onHit(EntityDamageByEntityEvent e){
        String raidId = resolveRaidIdAt(e.getEntity().getLocation());
        if (raidId != null) lastCombat.put(raidId, System.currentTimeMillis());
    }

    /**
     * Raids finden jeweils in der Welt der verteidigenden Faction ("faction_&lt;name&gt;")
     * statt, siehe WorldManager.createOrLoadFactionWorld(...). Der Weltname ist damit
     * ein stabiler, echter Bezeichner für "welcher Raid" -- anders als der vorherige
     * Versuch, ihn per Reflection aus RaidInstanceService zu erraten (dessen Methoden
     * es dort gar nicht gibt).
     */
    private String resolveRaidIdAt(org.bukkit.Location loc){
        if (loc == null || loc.getWorld() == null) return null;
        String world = loc.getWorld().getName();
        return world.startsWith("faction_") ? world : null;
    }
}