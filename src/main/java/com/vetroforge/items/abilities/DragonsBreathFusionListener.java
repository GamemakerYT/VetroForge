// src/main/java/com/vetroforge/items/abilities/DragonsBreathFusionListener.java
package com.vetroforge.items.abilities;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

public class DragonsBreathFusionListener implements Listener {

    private final FactionsPlugin plugin;

    public DragonsBreathFusionListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player player)) return;

        ItemStack cursor = e.getCursor();
        ItemStack current = e.getCurrentItem();

        if (!CustomItemUtil.isDragonsBreath(plugin, cursor)) return;
        if (!CustomItemUtil.isDragonsBreath(plugin, current)) return;

        e.setCancelled(true);

        if (CustomItemUtil.isDragonsBreathMax(plugin, current)) {
            player.sendMessage("§6Dragon's Breath §8» §cDieses Item ist bereits auf Max-Level.");
            try {
                player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
            } catch (Throwable ignored) {
            }
            return;
        }

        boolean upgraded = CustomItemUtil.upgradeDragonsBreathOnce(plugin, current);
        if (!upgraded) {
            player.sendMessage("§6Dragon's Breath §8» §cUpgrade nicht möglich.");
            return;
        }

        if (cursor.getAmount() <= 1) {
            e.setCursor(null);
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            e.setCursor(cursor);
        }

        e.setCurrentItem(current);

        int newLevel = CustomItemUtil.dragonsBreathLevel(plugin, current);
        int max = CustomItemUtil.dragonsBreathMaxLevel();

        player.sendMessage("§6Dragon's Breath §8» §aFusion erfolgreich. §7Level: §e" + newLevel + "§7/§e" + max);
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
        } catch (Throwable ignored) {
        }

        player.updateInventory();
    }
}