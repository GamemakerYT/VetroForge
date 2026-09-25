package com.vetroforge.raid.spectator;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class RaidSpectatorListener implements Listener {

    private final FactionsPlugin plugin;

    public RaidSpectatorListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onClick(PlayerInteractEvent e) {
        Player p = e.getPlayer();

        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) return;

        ItemStack item = e.getItem();
        if (item == null || item.getType() != Material.COMPASS) return;

        if (plugin.getRaidSpectatorService() == null || !plugin.getRaidSpectatorService().isSpectating(p)) return;

        e.setCancelled(true);
        plugin.getRaidSpectatorService().nextTarget(p);
    }
}
