// src/main/java/com/vetroforge/capture/CaptureZoneBuffService.java
package com.vetroforge.capture;

import com.vetroforge.FactionsPlugin;
import java.util.*;

public class CaptureZoneBuffService {
    private final FactionsPlugin plugin;
    private final Map<String,Integer> stacks = new HashMap<>(); // factionId -> stack count

    public CaptureZoneBuffService(FactionsPlugin p){ this.plugin=p; }

    public void onZoneCaptured(String factionId){ stacks.put(factionId, Math.min(3, stacks.getOrDefault(factionId,0)+1)); }
    public void onZoneLost(String factionId){
        int next = stacks.getOrDefault(factionId, 0) - 1;
        if (next <= 0) stacks.remove(factionId);
        else stacks.put(factionId, next);
    }

    /** Bonus auf Loot-Tier-Weight & Raid-Rewards (z.B. +3% je Stack, max 9%) */
    public double weightMultiplier(String factionId){
        int s = stacks.getOrDefault(factionId,0);
        return 1.0 + 0.03*s;
    }

    /** True, wenn die Faction aktuell mindestens eine Capture-Zone kontrolliert. */
    public boolean hasZoneControl(String factionId){
        return stacks.getOrDefault(factionId, 0) > 0;
    }
}
