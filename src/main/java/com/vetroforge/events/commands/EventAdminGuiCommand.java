package com.vetroforge.events.commands;

import com.vetroforge.events.gui.EventAdminGui;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class EventAdminGuiCommand implements CommandExecutor {

    private static final String PERM = "vetroforge.event.admin";
    private final EventAdminGui gui;

    public EventAdminGuiCommand(EventAdminGui gui) { this.gui = gui; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String lbl, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("§cNur Ingame."); return true; }
        if (!p.hasPermission(PERM)) { p.sendMessage("§cKeine Rechte."); return true; }
        gui.open(p);
        return true;
    }
}
