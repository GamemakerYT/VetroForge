// src/main/java/com/vetroforge/world/RaidBuildProtectionListener.java
package com.vetroforge.world;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.worldgen.RaidWorldLayout;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.entity.Player;
import org.bukkit.Material;
import com.vetroforge.raid.RaidText;

public class RaidBuildProtectionListener implements Listener {

    private final FactionsPlugin plugin;
    private final int yLevel;

    public RaidBuildProtectionListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.yLevel = plugin.getConfig().getInt("factions.world.yLevel", 64);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e) {
        World world = e.getBlock().getWorld();

        if (!isFactionWorld(world)) return;

        Location loc = e.getBlock().getLocation();

        if (e.getBlock().getY() <= yLevel) {
            e.setCancelled(true);
            RaidText.send(plugin, e.getPlayer(), "message.underground_protected");
            return;
        }

        if (e.getBlock().getType() == Material.OBSIDIAN && isActiveRaider(e.getPlayer())) {
            return;
        }

        if (!canBuildHere(e.getPlayer(), loc)) {
            e.setCancelled(true);
            RaidText.send(plugin, e.getPlayer(), "message.cannot_break_here");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent e) {
        World world = e.getBlockPlaced().getWorld();

        if (!isFactionWorld(world)) return;

        Location loc = e.getBlockPlaced().getLocation();

        if (e.getBlockPlaced().getY() <= yLevel) {
            e.setCancelled(true);
            RaidText.send(plugin, e.getPlayer(), "message.underground_protected");
            return;
        }

        if (!canBuildHere(e.getPlayer(), loc)) {
            e.setCancelled(true);
            RaidText.send(plugin, e.getPlayer(), "message.cannot_build_here");
        }
    }

    private boolean canBuildHere(Player player, Location loc) {
        if (player == null || loc == null || loc.getWorld() == null) return false;

        if (com.vetroforge.services.BuildModeService.hasAccess(plugin, player, com.vetroforge.services.BuildModeService.Area.RAID)) {
            return true;
        }

        int x = loc.getBlockX();
        int z = loc.getBlockZ();

        String worldFaction = extractFaction(loc.getWorld().getName());
        String playerFaction = getPlayerFaction(player);

        if (RaidWorldLayout.inBuild(x, z)) {
            return worldFaction != null && worldFaction.equalsIgnoreCase(playerFaction);
        }

        if (RaidWorldLayout.inBufferRaiders(x, z)) {
            return isActiveRaider(player);
        }

        return false;
    }

    private boolean isActiveRaider(Player player) {
        try {
            return plugin.getRaidSessionService() != null
                    && plugin.getRaidSessionService().isInRaid(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String getPlayerFaction(Player player) {
        try {
            if (plugin.getFactionManager() == null) return null;
            return plugin.getFactionManager().getFactionOf(player.getUniqueId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isFactionWorld(World world) {
        return world != null && world.getName().startsWith("faction_");
    }

    private String extractFaction(String worldName) {
        if (worldName == null || !worldName.startsWith("faction_")) return null;
        return worldName.substring("faction_".length());
    }

}
