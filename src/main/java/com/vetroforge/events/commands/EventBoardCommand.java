// src/main/java/com/vetroforge/events/commands/EventBoardCommand.java
package com.vetroforge.events.commands;

import com.vetroforge.events.gui.EventsGui;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class EventBoardCommand implements CommandExecutor {

    private final EventsGui gui;

    public EventBoardCommand(EventsGui gui) { this.gui = gui; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String lbl, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("§cNur Ingame."); return true; }
        gui.open(p);
        return true;
    }
}