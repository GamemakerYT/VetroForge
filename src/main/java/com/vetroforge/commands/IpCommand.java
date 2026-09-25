// src/main/java/com/vetroforge/commands/IpCommand.java
package com.vetroforge.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class IpCommand implements CommandExecutor {
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vetroforge.staff.ip")) { s.sendMessage(ChatColor.RED+"Keine Rechte."); return true; }
        if (a.length<1){ s.sendMessage(ChatColor.YELLOW+"/ip <Spieler>"); return true; }
        Player p = Bukkit.getPlayerExact(a[0]);
        if (p==null){ s.sendMessage(ChatColor.RED+"Spieler offline."); return true; }
        s.sendMessage(ChatColor.GRAY+"IP von "+p.getName()+": "+ChatColor.WHITE+p.getAddress().getAddress().getHostAddress());
        return true;
    }
}