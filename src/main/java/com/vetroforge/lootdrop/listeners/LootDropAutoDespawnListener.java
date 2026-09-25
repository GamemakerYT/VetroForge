// src/main/java/com/vetroforge/lootdrop/listeners/LootDropAutoDespawnListener.java
package com.vetroforge.lootdrop.listeners;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.lootdrop.LootDropChestUtil;
import com.vetroforge.lootdrop.model.Tier;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.inventory.InventoryMoveItemEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;

public class LootDropAutoDespawnListener implements Listener {

    private static final String META = "vetro_lootdrop";
    private final FactionsPlugin plugin;

    public LootDropAutoDespawnListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    private boolean isLootdropChest(Inventory inv) {
        if (inv == null) return false;
        InventoryHolder h = inv.getHolder();
        if (!(h instanceof Chest chest)) return false;
        return chest.getBlock().getType() == Material.CHEST && chest.getBlock().hasMetadata(META);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onOpen(InventoryOpenEvent e) {
        if (!isLootdropChest(e.getView().getTopInventory())) return;
        Chest chest = (Chest) e.getView().getTopInventory().getHolder();
        ensureLoot(chest);
        LootDropChestUtil.ensureInventoryFilled(
                plugin,
                e.getView().getTopInventory(),
                tierFromMetadata(chest),
                Math.max(5, plugin.getConfig().getInt("lootdrop.open-refill-slots", 7))
        );
        Bukkit.getScheduler().runTask(plugin, () -> {
            ensureLoot(chest);
            LootDropChestUtil.ensureInventoryFilled(
                    plugin,
                    e.getView().getTopInventory(),
                    tierFromMetadata(chest),
                    Math.max(5, plugin.getConfig().getInt("lootdrop.open-refill-slots", 7))
            );
        });
    }

    // sofort prüfen beim Schließen
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClose(InventoryCloseEvent e) {
        if (!isLootdropChest(e.getView().getTopInventory())) return;
        Chest chest = (Chest) e.getView().getTopInventory().getHolder();
        ensureLoot(chest);
        plugin.getLootDropManager().despawnIfEmpty(chest);
    }

    // nach Click einen Tick warten → echten Inventarstand prüfen
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!isLootdropChest(e.getView().getTopInventory())) return;
        Chest chest = (Chest) e.getView().getTopInventory().getHolder();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ensureLoot(chest);
            plugin.getLootDropManager().despawnIfEmpty(chest);
        });
    }

    // nach Drag einen Tick warten
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (!isLootdropChest(e.getView().getTopInventory())) return;
        Chest chest = (Chest) e.getView().getTopInventory().getHolder();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ensureLoot(chest);
            plugin.getLootDropManager().despawnIfEmpty(chest);
        });
    }

    // Hopper zieht Items raus → nach Tick prüfen
    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(InventoryMoveItemEvent e) {
        Inventory src = e.getSource();
        if (!isLootdropChest(src)) return;
        Chest chest = (Chest) src.getHolder();
        Bukkit.getScheduler().runTask(plugin, () -> {
            ensureLoot(chest);
            plugin.getLootDropManager().despawnIfEmpty(chest);
        });
    }

    private void ensureLoot(Chest chest) {
        if (chest == null || chest.getBlock().getType() != Material.CHEST) return;
        if (!chest.getBlock().hasMetadata(META)) return;
        LootDropChestUtil.ensureFilled(new LootDropChestUtil.Context(
                plugin,
                chest.getBlock().getLocation(),
                null,
                Math.max(5, plugin.getConfig().getInt("lootdrop.open-refill-slots", 7)),
                tierFromMetadata(chest)
        ));
    }

    private Tier tierFromMetadata(Chest chest) {
        try {
            String raw = chest.getBlock().getMetadata(META).isEmpty()
                    ? ""
                    : chest.getBlock().getMetadata(META).get(0).asString();
            if (raw == null) return Tier.GOLD;
            raw = raw.trim().toUpperCase(java.util.Locale.ROOT);
            if (raw.equals("COMMON") || raw.equals("RARE")) return Tier.BRONZE;
            if (raw.equals("EPIC")) return Tier.SILVER;
            if (raw.equals("LEGENDARY")) return Tier.GOLD;
            if (raw.equals("MYSTIC") || raw.equals("MYTHIC")) return Tier.MYTHIC;
            return Tier.valueOf(raw);
        } catch (Throwable ignored) {
            return Tier.GOLD;
        }
    }
}
