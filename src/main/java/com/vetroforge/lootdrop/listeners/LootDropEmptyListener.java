// src/main/java/com/vetroforge/lootdrop/listeners/LootDropEmptyListener.java
package com.vetroforge.lootdrop.listeners;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;

public class LootDropEmptyListener implements Listener {

    private static final String META = "vetro_lootdrop";
    private final FactionsPlugin plugin;

    public LootDropEmptyListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onClose(InventoryCloseEvent e) {
        if (!(e.getInventory().getHolder() instanceof Chest chest)) return;
        var b = chest.getBlock();
        if (b.getType() != Material.CHEST) return;
        if (!b.hasMetadata(META)) return;

        // kurz nach dem Close prüfen (falls Items noch asynchron landen)
        Bukkit.getScheduler().runTask(plugin, () -> plugin.getLootDropManager().despawnIfEmpty(chest));
    }
}