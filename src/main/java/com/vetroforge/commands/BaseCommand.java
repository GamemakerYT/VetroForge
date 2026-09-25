// src/main/java/com/vetroforge/command/BaseCommand.java
package com.vetroforge.commands;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;

public abstract class BaseCommand {
    protected String header(String section){
        return ChatColor.DARK_RED + "—————— " + ChatColor.RED + "VetroForge" + ChatColor.WHITE + " • " + section + ChatColor.DARK_RED + " ——————";
    }
    protected String footer(){
        return ChatColor.DARK_RED + "——————————————————————————————";
    }
    protected void sendFramed(CommandSender s, String section, String... lines){
        s.sendMessage(header(section));
        if (lines != null) for (String l : lines)
            s.sendMessage(ChatColor.translateAlternateColorCodes('&', l));
        s.sendMessage(footer());
    }
}