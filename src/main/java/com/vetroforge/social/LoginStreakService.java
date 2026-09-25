// src/main/java/com/vetroforge/social/LoginStreakService.java
package com.vetroforge.social;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;

import java.time.*;
import java.util.*;

public class LoginStreakService implements Listener {
    private final FactionsPlugin plugin;
    private final Map<UUID, LocalDate> last = new HashMap<>();
    private final Map<UUID, Integer> streak = new HashMap<>();

    public LoginStreakService(FactionsPlugin p){ this.plugin=p; Bukkit.getPluginManager().registerEvents(this,p); }

    @EventHandler public void onJoin(PlayerJoinEvent e){
        UUID id = e.getPlayer().getUniqueId();
        LocalDate today = LocalDate.now();
        LocalDate prev  = last.get(id);

        if (prev==null || prev.isBefore(today.minusDays(1))) streak.put(id, 1);
        else if (prev.equals(today.minusDays(1))) streak.put(id, streak.getOrDefault(id,0)+1);
        last.put(id, today);

        int s = streak.getOrDefault(id,1);
        try {
            var m = plugin.getClass().getMethod("getEnergyManager");
            Object em = m.invoke(plugin);
            if (em != null) {
                var g = em.getClass().getMethod("grantFastNextTick", java.util.UUID.class);
                g.invoke(em, id);
            }
        } catch (Throwable ignored) {}
        }
    }
