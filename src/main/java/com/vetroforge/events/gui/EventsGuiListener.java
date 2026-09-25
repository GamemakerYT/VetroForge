// src/main/java/com/vetroforge/events/gui/EventsGuiListener.java
package com.vetroforge.events.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class EventsGuiListener implements Listener {

    private final EventsGui gui;

    public EventsGuiListener(EventsGui gui) { this.gui = gui; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EventsGui.SimpleHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EventsGui.SimpleHolder) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void onClose(InventoryCloseEvent e) {
        if (e.getInventory().getHolder() instanceof EventsGui.SimpleHolder) {
            if (e.getPlayer() instanceof Player p) gui.onClose(p);
        }
    }
}
