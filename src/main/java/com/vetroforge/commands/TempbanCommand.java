// src/main/java/com/vetroforge/commands/TempbanCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.StaffLabelUtil;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class TempbanCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    public TempbanCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vetroforge.staff.ban")) { s.sendMessage(ChatColor.RED+"Keine Rechte."); return true; }
        if (a.length < 2) { s.sendMessage(ChatColor.YELLOW+"/"+l+" <Spieler> <Minuten> [Grund…] [-s]"); return true; }

        String target = a[0];
        Integer minutes = tryInt(a[1]);
        if (minutes == null || minutes <= 0) { s.sendMessage(ChatColor.RED+"Ungültige Minuten."); return true; }
        boolean silent = hasFlag(a, "-s");

        List<String> tail = new ArrayList<>(Arrays.asList(a).subList(2, a.length));
        tail.remove("-s");
        String reason = tail.isEmpty() ? "Tempban" : String.join(" ", tail);

        OfflinePlayer off = Bukkit.getOfflinePlayer(target);
        if (plugin.getStaffProtectionService().blockIfProtected(s, off, "Tempban")) {
            s.sendMessage(ChatColor.RED + "Du darfst keine Teammitglieder bannen.");
            return true;
        }
        Date until = new Date(System.currentTimeMillis() + minutes * 60_000L);
        Bukkit.getBanList(BanList.Type.NAME).addBan(target, reason, until, StaffLabelUtil.labelPlain(s));

        Player online = Bukkit.getPlayerExact(target);
        if (online != null) try { online.kickPlayer(ChatColor.RED+"Du wurdest zeitweise gebannt!\n"
                + ChatColor.GRAY + "Von: " + StaffLabelUtil.labelColored(s) + "\n"
                + ChatColor.GRAY + "Grund: " + ChatColor.WHITE + reason + "\n"
                + ChatColor.GRAY + "Dauer: " + ChatColor.WHITE + minutes + "m"); } catch (Throwable ignored) {}

        var lines = Arrays.asList("&7Spieler: &f"+target, "&7Dauer: &f"+minutes+"m", "&7Grund: &f"+reason, "&7Von: &f"+StaffLabelUtil.labelPlain(s));
        if (silent) plugin.getPunishmentManager().announce("Ban", lines, true);
        else        plugin.getPunishmentManager().broadcastBanner("Ban", lines);

        // optional: History (als Ban)
        try {
            var hist = plugin.getClass().getMethod("getPunishmentHistory").invoke(plugin);
            if (hist != null) hist.getClass().getMethod("logBan", UUID.class, String.class, UUID.class, Long.TYPE)
                    .invoke(hist, off.getUniqueId(), reason+" (temp "+minutes+"m)", (s instanceof Player)?((Player)s).getUniqueId():null, System.currentTimeMillis());
        } catch (Throwable ignored) {}

        s.sendMessage(ChatColor.GREEN+"Tempban gesetzt für "+ChatColor.WHITE+target+ChatColor.GRAY+" ("+minutes+"m)");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) {
            String pfx = a[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pfx)) out.add(p.getName());
            return out;
        }
        if (a.length == 2) return Arrays.asList("30","60","120","240");
        if (a.length >= 3) return Arrays.asList("-s");
        return Collections.emptyList();
    }

    private Integer tryInt(String s){ try { return Integer.parseInt(s); } catch (Exception e){ return null; } }
    private boolean hasFlag(String[] a, String flag){ for (String t : a) if (flag.equalsIgnoreCase(t)) return true; return false; }
}
