// src/main/java/com/vetroforge/listeners/StoryListener.java
package com.vetroforge.listeners;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerPickupItemEvent;

@SuppressWarnings("deprecation")
public class StoryListener implements Listener {

    private final FactionsPlugin plugin;

    public StoryListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { /* optional */ }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        plugin.getStoryManager().recordBlockBreak(e.getPlayer(), e.getBlock().getType());
    }

    @EventHandler(ignoreCancelled = true)
    public void onPickup(PlayerPickupItemEvent e) {
        plugin.getStoryManager().recordItemCollect(e.getPlayer(), e.getItem().getItemStack().getType());
    }

    @EventHandler(ignoreCancelled = true)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null || killer.equals(victim)) return;

        boolean inWarzone = true;
        try {
            var enterListener = plugin.getWarzoneEnterListener(); // **richtiger Getter**
            if (enterListener != null) {
                var m = enterListener.getClass().getMethod("isInWarzone", Player.class);
                Object ret = m.invoke(enterListener, killer);
                if (ret instanceof Boolean b) inWarzone = b;
            }
        } catch (Throwable ignored) {}

        plugin.getStoryManager().recordKill(killer, inWarzone);
    }
}