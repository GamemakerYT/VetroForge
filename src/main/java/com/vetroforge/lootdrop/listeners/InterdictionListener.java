// src/main/java/com/vetroforge/lootdrop/listeners/InterdictionListener.java
package com.vetroforge.lootdrop.listeners;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.lootdrop.model.ActiveDrop;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.event.player.PlayerInteractEvent;

public class InterdictionListener implements Listener {

    private final FactionsPlugin plugin;

    public InterdictionListener(FactionsPlugin plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    private boolean nearDrop(Player p){
        for (var d : plugin.getLootDropManager().getActive()){
            if (d.location().getWorld().equals(p.getWorld())
                    && d.location().distance(p.getLocation()) <= d.interdictionRadius()) return true;
        }
        return false;
    }

    // Enderperlen blockieren im Interdiction-Radius
    @EventHandler(ignoreCancelled = true)
    public void onPearl(ProjectileLaunchEvent e){
        if (!(e.getEntity().getShooter() instanceof Player p)) return;
        if (!e.getEntityType().name().equals("ENDER_PEARL")) return;
        if (nearDrop(p)) e.setCancelled(true);
    }

    // Raketen-Boost für Elytra blockieren (optional)
    @EventHandler(ignoreCancelled = true)
    public void onRocket(PlayerInteractEvent e){
        if (!(e.getPlayer() instanceof Player p)) return;
        if (e.getItem() == null || e.getItem().getType() != Material.FIREWORK_ROCKET) return;
        if (!p.isGliding()) return;
        if (nearDrop(p)) e.setCancelled(true);
    }
}