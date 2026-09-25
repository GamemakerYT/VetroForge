package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.gui.TopRaidersGui;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class TopRaidersCommand implements CommandExecutor {
    private final TopRaidersGui gui;
    public TopRaidersCommand(FactionsPlugin plugin){ this.gui = new TopRaidersGui(plugin); }
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        gui.open(p);
        return true;
    }
}