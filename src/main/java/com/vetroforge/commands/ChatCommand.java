package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.chat.ChatChannelManager.Channel;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.List;

public class ChatCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    public ChatCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a){
        if (!(s instanceof Player p)){ s.sendMessage("Ingame."); return true; }
        if (a.length==0){
            s.sendMessage("§e/"+l+" global §8| §e"+"/"+l+" lobby §8| §e"+"/"+l+" faction");
            return true;
        }
        String sub = a[0].toLowerCase();
        switch (sub){
            case "g":
            case "global":  plugin.getChatChannelManager().set(p, Channel.GLOBAL);  p.sendMessage("§7Kanal: §fGLOBAL");  break;
            case "l":
            case "lobby":   plugin.getChatChannelManager().set(p, Channel.LOBBY);   p.sendMessage("§7Kanal: §fLOBBY");   break;
            case "f":
            case "faction": plugin.getChatChannelManager().set(p, Channel.FACTION); p.sendMessage("§7Kanal: §fFACTION"); break;
            default: s.sendMessage(ChatColor.RED+"Unbekannter Kanal."); break;
        }
        return true;
    }

    @Override public List<String> onTabComplete(CommandSender s, Command c, String a, String[] A){
        if (A.length==1) return java.util.Arrays.asList("global","lobby","faction");
        return java.util.Collections.emptyList();
    }
}