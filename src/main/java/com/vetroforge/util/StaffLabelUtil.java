// src/main/java/com/vetroforge/util/StaffLabelUtil.java
package com.vetroforge.util;

import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class StaffLabelUtil {
    private StaffLabelUtil(){}

    /** Einheitlicher Pfeil für Staff-Labels. */
    public static final String ARROW = " §8» §f";

    /** Farbige Rangbezeichnung + Pfeil + weißer Name. */
    public static String labelColored(CommandSender s) {
        return rankName(s) + ARROW + s.getName();
    }

    /** Alias – bleibt für bestehenden Code erhalten. */
    public static String labelPlain(CommandSender s) {
        return rankName(s) + ARROW + s.getName();
    }

    /** Ermittelt den farbigen Rang des Senders. */
    public static String rankName(CommandSender s) {
        if (s instanceof Player p) {
            if (p.hasPermission("vetroforge.rank.admin"))   return ChatColor.DARK_RED + "Admin";
            if (p.hasPermission("vetroforge.rank.coadmin")) return ChatColor.RED + "Co-Admin";
            if (p.hasPermission("vetroforge.rank.mod"))     return ChatColor.GOLD + "Moderator";
            if (p.hasPermission("vetroforge.rank.builder")) return ChatColor.YELLOW + "Builder";
            if (p.hasPermission("vetroforge.rank.support")) return ChatColor.AQUA + "Support";
        }
        return ChatColor.GRAY + "Team";
    }
}