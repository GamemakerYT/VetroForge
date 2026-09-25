package com.vetroforge.holo;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

public class TopCornerManager {
    private final FactionsPlugin plugin;
    private final HoloLeaderboardService svc;
    private final Map<String, java.util.UUID> placed = new HashMap<>();
    private BukkitTask task;

    public TopCornerManager(FactionsPlugin plugin, HoloLeaderboardService svc){
        this.plugin = plugin;
        this.svc = svc;
    }

    public void start(){
        stop();
        refresh();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::refresh, 20L*30, 20L*30);
    }

    public void stop(){
        if (task!=null) task.cancel();
        task=null;
        // Holos verbleiben (Service verwaltet sie); hier löschen wir nichts
    }

    private void refresh(){
        Location base = readLoc();
        if (base==null) return;

        // Drei Holos nebeneinander (x-Versatz)
        ensure("kills",     base.clone().add(-1.2, 0, 0), HoloLeaderboardService.Board.TopKills);
        ensure("prestige",  base.clone().add( 0.0, 0, 0), HoloLeaderboardService.Board.TopPrestige);
        ensure("crystals",  base.clone().add( 1.2, 0, 0), HoloLeaderboardService.Board.TopCrystals);
        svc.refreshNow();
    }

    private void ensure(String key, Location at, HoloLeaderboardService.Board type){
        if (!placed.containsKey(key)) {
            java.util.UUID id = svc.set(at, type);
            placed.put(key, id);
        }
    }

    private Location readLoc(){
        var c = plugin.getConfig().getConfigurationSection("lobby.topCorner");
        if (c==null) return null;
        var w = Bukkit.getWorld(c.getString("world","world"));
        if (w==null) return null;
        return new Location(w, c.getDouble("x"), c.getDouble("y"), c.getDouble("z"));
    }
}