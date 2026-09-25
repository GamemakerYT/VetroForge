// src/main/java/com/vetroforge/commands/GeyserIniCommand.java
package com.vetroforge.commands;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;

public class GeyserIniCommand implements CommandExecutor {
    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        boolean geyser = Bukkit.getPluginManager().getPlugin("Geyser-Spigot") != null;
        boolean flood  = Bukkit.getPluginManager().getPlugin("floodgate") != null;
        s.sendMessage((geyser?ChatColor.GREEN:ChatColor.RED)+"Geyser: "+geyser
                + ChatColor.GRAY+" | " + (flood?ChatColor.GREEN:ChatColor.RED)+"Floodgate: "+flood);
        return true;
    }
}