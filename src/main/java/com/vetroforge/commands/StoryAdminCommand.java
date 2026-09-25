// src/main/java/com/vetroforge/commands/StoryAdminCommand.java
package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.*;

public class StoryAdminCommand implements CommandExecutor {

    private final FactionsPlugin plugin;
    public StoryAdminCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.isOp() && !s.hasPermission("vetroforge.admin") && !s.hasPermission("vetroforge.story.admin")) { s.sendMessage(UiDecor.noPermission()); return true; }
        if (!(s instanceof org.bukkit.entity.Player p)) { s.sendMessage("Players only."); return true; }

        if (a.length == 0) {
            s.sendMessage("§8/§e"+l+" reload §8| §ecomplete");
            return true;
        }
        if (a[0].equalsIgnoreCase("reload")) {
            plugin.getStoryManager().reload();
            s.sendMessage("§aStories neu geladen.");
            return true;
        }
        if (a[0].equalsIgnoreCase("complete")) {
            plugin.getStoryManager().adminComplete(p);
            s.sendMessage("§aAktive Story abgeschlossen.");
            return true;
        }
        s.sendMessage("§cUnbekannter Subcommand.");
        return true;
    }
}