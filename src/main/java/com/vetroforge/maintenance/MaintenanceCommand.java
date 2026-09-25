// src/main/java/com/vetroforge/commands/MaintenanceCommand.java
package com.vetroforge.maintenance;

 // ===============================================
// File: src/main/java/com/vetroforge/maintenance/MaintenanceCommand.java
// ===============================================
import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.util.StringUtil;

import java.util.*;

public class MaintenanceCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final MaintenanceManager mm;

    public MaintenanceCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.mm = plugin.getMaintenanceManager();
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vetroforge.maintenance.admin")) {
            s.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (a.length == 0) {
            sendHelp(s);
            return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);
        if (sub.equals("on") || sub.equals("enable")) {
            int secs = plugin.getConfig().getInt("maintenance.countdown.start-seconds", 60);
            String rsn = null;
            if (a.length >= 2) {
                try { secs = Math.max(0, Integer.parseInt(a[1])); } catch (NumberFormatException ignored) {}
                if (a.length >= 3) {
                    rsn = joinFrom(a, 2);
                }
            }
            mm.enable(rsn, secs);
            s.sendMessage(ChatColor.GREEN + "Maintenance aktiviert. Countdown: " + secs + "s" + (rsn!=null?" Grund: "+rsn:""));
            return true;
        }
        if (sub.equals("off") || sub.equals("disable")) {
            mm.disable();
            s.sendMessage(ChatColor.GREEN + "Maintenance deaktiviert.");
            return true;
        }
        if (sub.equals("add") && a.length >= 2) {
            mm.addWhitelist(a[1]);
            s.sendMessage(ChatColor.GREEN + "Whitelisted: " + a[1]);
            return true;
        }
        if (sub.equals("remove") && a.length >= 2) {
            mm.removeWhitelist(a[1]);
            s.sendMessage(ChatColor.GREEN + "Entfernt: " + a[1]);
            return true;
        }
        if (sub.equals("list")) {
            s.sendMessage(ChatColor.YELLOW + "Whitelist: " + mm.getWhitelistSnapshot());
            return true;
        }
        sendHelp(s);
        return true;
    }

    private void sendHelp(CommandSender s) {
        s.sendMessage(ChatColor.DARK_RED + "—————— " + ChatColor.RED + "VetroForge" + ChatColor.WHITE + " Maintenance " + ChatColor.DARK_RED + "——————");
        s.sendMessage(ChatColor.YELLOW + "/maintenance on [sekunden] [grund]");
        s.sendMessage(ChatColor.YELLOW + "/maintenance off");
        s.sendMessage(ChatColor.YELLOW + "/maintenance add <spieler|uuid>");
        s.sendMessage(ChatColor.YELLOW + "/maintenance remove <spieler|uuid>");
        s.sendMessage(ChatColor.YELLOW + "/maintenance list");
        s.sendMessage(ChatColor.DARK_RED + "——————————————————————————————");
    }

    private String joinFrom(String[] arr, int idx) {
        StringBuilder sb = new StringBuilder();
        for (int i=idx;i<arr.length;i++) {
            if (i>idx) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vetroforge.maintenance.admin")) return java.util.Collections.<String>emptyList();
        if (a.length == 1) {
            return StringUtil.copyPartialMatches(a[0],
                    java.util.Arrays.asList("on","off","add","remove","list"),
                    new java.util.ArrayList<String>());
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("on")) {
            return StringUtil.copyPartialMatches(a[1],
                    java.util.Arrays.asList("60","30","10","0"),
                    new java.util.ArrayList<String>());
        }
        return java.util.Collections.<String>emptyList();
    }
}
