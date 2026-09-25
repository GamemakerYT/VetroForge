// src/main/java/com/vetroforge/raid/RaidTargetService.java
package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.factions.FactionManager;

import java.util.*;

public class RaidTargetService {

    private final FactionsPlugin plugin;
    private final FactionManager fm;

    public RaidTargetService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.fm = plugin.getFactionManager();
    }

    public List<String> getAvailableTargets(String attacker) {
        List<String> out = new ArrayList<>();

        for (String f : fm.getAllFactionNames()) {
            if (f.equalsIgnoreCase(attacker)) continue;

            // ❌ keine Allies
            if (fm.getAllies(attacker).contains(f)) continue;

            // ❌ kein Raid aktiv
            if (plugin.getRaidManager().isFactionInRaid(f)) continue;

            out.add(f);
        }

        return out;
    }

    public String getRandomTarget(String attacker) {
        List<String> list = getAvailableTargets(attacker);
        if (list.isEmpty()) return null;
        return list.get(new Random().nextInt(list.size()));
    }
}