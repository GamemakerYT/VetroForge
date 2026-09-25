// src/main/java/com/vetroforge/world/vworld/VWorldWandListener.java
package com.vetroforge.world.vworld;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Location;
import org.bukkit.Sound;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public final class VWorldWandListener implements Listener {

    private final FactionsPlugin plugin;
    private final VetroRegionSelectionService selectionService;

    public VWorldWandListener(
            FactionsPlugin plugin,
            VetroRegionSelectionService selectionService
    ) {
        this.plugin = plugin;
        this.selectionService = selectionService;
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent event) {
        Player player = event.getPlayer();

        if (!player.hasPermission("vetroforge.vworld.admin")) {
            return;
        }

        if (!VWorldWand.isWand(plugin, event.getItem())) {
            return;
        }

        Block clicked = event.getClickedBlock();

        if (clicked == null) {
            return;
        }

        Action action = event.getAction();
        Location location = clicked.getLocation();

        event.setCancelled(true);

        if (action == Action.LEFT_CLICK_BLOCK) {
            selectionService.setPos1(player, location);

            player.sendMessage("§6VWorld §8» §fPos1 gesetzt: §e"
                    + location.getBlockX() + " "
                    + location.getBlockY() + " "
                    + location.getBlockZ());

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.5f);
            return;
        }

        if (action == Action.RIGHT_CLICK_BLOCK) {
            selectionService.setPos2(player, location);

            player.sendMessage("§6VWorld §8» §fPos2 gesetzt: §e"
                    + location.getBlockX() + " "
                    + location.getBlockY() + " "
                    + location.getBlockZ());

            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.8f);
        }
    }
}