// =======================================================
// File: src/main/java/com/vetroforge/chests/ChestListener.java
// =======================================================
package com.vetroforge.listeners;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.ChestSpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public class ChestListener implements Listener {

    private final FactionsPlugin plugin;
    private final ChestSpawnManager manager;

    public ChestListener(FactionsPlugin plugin, ChestSpawnManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getClickedBlock() == null) return;
        Block b = e.getClickedBlock();
        if (b.getType() != Material.CHEST) return;
        if (!manager.isManagedChest(b.getLocation())) return;

        // Markiere "geöffnet" & XP vergeben
        Player p = e.getPlayer();
        manager.markOpened(b.getLocation(), p.getUniqueId());
    }

    @EventHandler(ignoreCancelled = true)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Chest)) return;
        Chest c = (Chest) e.getInventory().getHolder();
        if (c == null) return;
        if (!manager.isManagedChest(c.getLocation())) return;

        // Wenn leer -> sofort despawn
        boolean empty = true;
        for (org.bukkit.inventory.ItemStack it : e.getInventory().getContents()) {
            if (it != null && it.getType() != Material.AIR) { empty = false; break; }
        }
        if (empty) {
            manager.despawn(c.getLocation(), false);
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.CHEST) return;
        if (!manager.isManagedChest(e.getBlock().getLocation())) return;
        // Unsere Chests dürfen nicht abgebaut werden
        e.setCancelled(true);
        e.getPlayer().sendMessage(ChatColor.RED + "Diese Kiste verschwindet automatisch.");
    }
}
