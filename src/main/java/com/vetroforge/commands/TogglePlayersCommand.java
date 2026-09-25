package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class TogglePlayersCommand implements CommandExecutor {
    private final FactionsPlugin plugin;
    public TogglePlayersCommand(FactionsPlugin plugin){ this.plugin=plugin; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a){
        if (!(s instanceof Player p)){ s.sendMessage("Ingame."); return true; }
        boolean newState = !plugin.getPlayerVisibilityManager().isHidden(p);
        plugin.getPlayerVisibilityManager().setHidden(p, newState);
        p.sendMessage(newState ? "§7Spieler: §cversteckt" : "§7Spieler: §aangezeigt");
        return true;
    }
}