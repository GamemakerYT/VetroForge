// src/main/java/com/vetroforge/raid/spectator/RaidSpectatorService.java
package com.vetroforge.raid.spectator;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.display.SidebarScoreboardManager;
import com.vetroforge.factions.FactionManager;
import com.vetroforge.raid.RaidCrystalManager;
import com.vetroforge.raid.RaidManager;
import com.vetroforge.raid.RaidText;
import com.vetroforge.instance.RaidInstanceService;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

import java.util.*;

public class RaidSpectatorService {

    private final FactionsPlugin plugin;
    private final RaidManager raidManager;
    private final FactionManager factionManager;
    private final RaidInstanceService instanceService;
    private final RaidCrystalManager crystalManager;

    private final Map<UUID, String> targetFaction = new HashMap<>();
    private final Map<UUID, Location> returnLocations = new HashMap<>();
    private final Map<UUID, GameMode> returnGameModes = new HashMap<>();

    public RaidSpectatorService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.raidManager = plugin.getRaidManager();
        this.factionManager = plugin.getFactionManager();
        this.instanceService = plugin.getRaidInstanceService();
        this.crystalManager = plugin.getRaidCrystalManager();
    }

    /* ================= BASIC ================= */

    public boolean isSpectating(Player p){
        return p != null && targetFaction.containsKey(p.getUniqueId());
    }

    public String getTarget(Player p){
        return p == null ? null : targetFaction.get(p.getUniqueId());
    }

    public void enterSpectator(Player p, String target){
        if (p == null || target == null) return;

        if (raidManager.getSessionByTarget(target) == null) {
            RaidText.send(plugin, p, "message.no_active_raid");
            return;
        }

        UUID uuid = p.getUniqueId();
        if (!isSpectating(p)) {
            returnLocations.put(uuid, p.getLocation().clone());
            returnGameModes.put(uuid, p.getGameMode());
        }

        targetFaction.put(uuid, target);
        p.setGameMode(GameMode.SPECTATOR);

        teleport(p, target);
        RaidText.send(plugin, p, "message.spectating");
    }

    public void exitSpectator(Player p){
        if (!isSpectating(p)) return;

        UUID uuid = p.getUniqueId();
        targetFaction.remove(uuid);

        p.setGameMode(returnGameModes.getOrDefault(uuid, GameMode.SURVIVAL));
        returnGameModes.remove(uuid);

        Location location = returnLocations.remove(uuid);
        if (location != null && location.getWorld() != null) {
            p.teleport(location);
        }

        RaidText.send(plugin, p, "message.spectating_left");
    }

    /* ================= FIXED PART ================= */

    public void nextTarget(Player p) {
        if (!isSpectating(p)) return;

        List<String> targets = raidManager.getActiveTargets();

        if (targets.isEmpty()) {
            RaidText.send(plugin, p, "message.no_active_raid");
            return;
        }

        String current = targetFaction.get(p.getUniqueId());
        int index = Math.max(0, targets.indexOf(current));

        String next = targets.get((index + 1) % targets.size());

        targetFaction.put(p.getUniqueId(), next);

        teleport(p, next);
        RaidText.send(plugin, p, "message.spectating_target", "target", next);
    }

    /* ================= UTIL ================= */

    private void teleport(Player p, String faction) {

        World w = instanceService.ensureInstance(faction);
        if (w == null) return;

        Location loc = instanceService.entrySpawn(w);
        if (loc != null) p.teleport(loc);
    }
}
