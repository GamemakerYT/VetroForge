// src/main/java/com/vetroforge/commands/WikiCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class WikiCommand implements CommandExecutor {

    private final FactionsPlugin plugin;
    public WikiCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur ingame.");
            return true;
        }
        if (plugin.getWikiManager() != null) {
            plugin.getWikiManager().open(p);
        } else {
            p.sendMessage("§cWiki nicht verfügbar.");
        }
        return true;
    }
}