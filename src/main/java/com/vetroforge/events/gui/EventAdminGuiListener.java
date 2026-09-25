// src/main/java/com/vetroforge/events/gui/EventAdminGuiListener.java
package com.vetroforge.events.gui;

import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.*;

public final class EventAdminGuiListener implements Listener {

    private final EventAdminGui gui;

    public EventAdminGuiListener(EventAdminGui gui) { this.gui = gui; }

    @EventHandler
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof EventAdminGui.AdminHolder)
                && !(e.getView().getTopInventory().getHolder() instanceof EventAdminGui.ProgressHolder)) return;

        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot < 0 || slot >= e.getView().getTopInventory().getSize()) return;

        gui.handleClick(p, slot);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof EventAdminGui.AdminHolder
                || e.getView().getTopInventory().getHolder() instanceof EventAdminGui.ProgressHolder) {
            e.setCancelled(true);
        }
    }
}
