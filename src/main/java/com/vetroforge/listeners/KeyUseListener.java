// src/main/java/com/vetroforge/crates/KeyUseListener.java
package com.vetroforge.listeners;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.block.Action;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class KeyUseListener implements Listener {

    private final FactionsPlugin plugin;

    public KeyUseListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onUse(PlayerInteractEvent e) {
        // nur Mainhand + Rechtsklick
        if (e.getHand() != EquipmentSlot.HAND) return;
        Action a = e.getAction();
        if (a != Action.RIGHT_CLICK_AIR && a != Action.RIGHT_CLICK_BLOCK) return;

        if (plugin.getKeyCrateManager() == null) return;
        ItemStack item = e.getItem();
        if (!plugin.getKeyCrateManager().isKeyItem(item)) return;
        e.setCancelled(true);
        e.getPlayer().sendMessage(VetroForgeUtil.tr(plugin, e.getPlayer(), "crates.npc_only"));
    }
}
