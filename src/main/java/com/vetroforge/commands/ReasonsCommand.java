// src/main/java/com/vetroforge/commands/ReasonsCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.punish.PunishmentManager;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class ReasonsCommand implements CommandExecutor {
    private final FactionsPlugin plugin;
    public ReasonsCommand(FactionsPlugin plugin){ this.plugin=plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vetroforge.staff.reasons")) { s.sendMessage(ChatColor.RED+"Keine Rechte."); return true; }
        if (a.length < 1) {
            s.sendMessage(ChatColor.YELLOW+"/"+l+" <ban|kick|mute> [lang]"); return true;
        }
        String which = a[0].toLowerCase();
        PunishmentManager.Type type;
        switch (which) {
            case "ban": type = PunishmentManager.Type.BAN; break;
            case "kick": type = PunishmentManager.Type.KICK; break;
            case "mute": type = PunishmentManager.Type.MUTE; break;
            default: s.sendMessage(ChatColor.RED+"Unbekannter Typ. Nutze: ban|kick|mute"); return true;
        }
        String lang = (a.length >= 2) ? a[1] : ((s instanceof Player sp) ? plugin.getLanguageManager().getPlayerLangKey(sp) : "de");
        plugin.getPunishmentManager().sendReasonsBanner(s, type, lang);
        return true;
    }
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String t : new String[]{"ban","kick","mute"})
                if (t.startsWith(args[0].toLowerCase())) out.add(t);
        } else if (args.length == 2) {
            for (String lang : new String[]{"de","en","fr","nl"})
                if (lang.startsWith(args[1].toLowerCase())) out.add(lang);
        }
        return out;
    }

}