package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;

public class ObsidianBreakListener implements Listener {

    private final FactionsPlugin plugin;

    public ObsidianBreakListener(FactionsPlugin plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e){
        if (e.getBlock().getType() != Material.OBSIDIAN) return;

        boolean allowPick = plugin.getConfig().getBoolean("raids.obsidian.allowPickaxeBreak", false);
        if (allowPick) return;

        if (!e.getPlayer().isOp() && !e.getPlayer().hasPermission("vetroforge.raid.obsidian.bypass")) {
            e.setCancelled(true);
            e.getPlayer().sendMessage(ChatColor.RED + "Obsidian kann nur mit TNT geknackt werden.");
        }
    }

}
