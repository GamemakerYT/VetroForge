// src/main/java/com/vetroforge/commands/UnbanCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class UnbanCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    public UnbanCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vetroforge.staff.ban")) { s.sendMessage(ChatColor.RED+"Keine Rechte."); return true; }
        if (a.length < 1) { s.sendMessage(ChatColor.YELLOW+"/"+l+" <Spieler>"); return true; }

        String name = a[0];
        OfflinePlayer off = Bukkit.getOfflinePlayer(name);

        // Bukkit-Banliste
        try { Bukkit.getBanList(BanList.Type.NAME).pardon(name); } catch (Throwable ignored) {}
        // interner Flag
        try { plugin.getPunishmentManager().setBanned(off.getUniqueId(), false); } catch (Throwable ignored) {}

        // History (optional, via Reflection)
        try {
            Object hist = plugin.getClass().getMethod("getPunishmentHistory").invoke(plugin);
            if (hist != null) {
                hist.getClass().getMethod("logUnban", UUID.class, String.class, UUID.class, long.class)
                        .invoke(hist, off.getUniqueId(), "manual", (s instanceof Player)?((Player)s).getUniqueId():null, System.currentTimeMillis());
            }
        } catch (Throwable ignored) {}

        s.sendMessage(ChatColor.GREEN+"Unban: "+ChatColor.WHITE+name);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length != 1) return Collections.emptyList();
        String pfx = a[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        try {
            for (org.bukkit.BanEntry be : Bukkit.getBanList(BanList.Type.NAME).getBanEntries()) {
                String t = be.getTarget();
                if (t != null && t.toLowerCase(Locale.ROOT).startsWith(pfx)) out.add(t);
            }
        } catch (Throwable ignored) {}
        return out;
    }
}