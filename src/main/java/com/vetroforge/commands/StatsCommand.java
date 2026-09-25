// src/main/java/com/vetroforge/commands/StatsCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.gui.StatsGui;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.*;

public class StatsCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final StatsGui gui;

    public StatsCommand(FactionsPlugin plugin) { this.plugin = plugin; this.gui = new StatsGui(plugin); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length >= 2 && a[0].equalsIgnoreCase("reset")) {
            return reset(s, a[1]);
        }

        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        Player target = (a.length >= 1) ? Bukkit.getPlayerExact(a[0]) : p;
        if (target == null) { p.sendMessage("§cSpieler ist nicht online."); return true; }

        gui.open(p, target);
        return true;
    }

    /** Konsolenfähig (Tebex/Webshop): setzt Kills/Deaths/Streak/Elo eines -- auch offline -- Spielers zurück. */
    private boolean reset(CommandSender s, String name) {
        if (!s.hasPermission("vetroforge.stats.reset")) {
            s.sendMessage("§cKeine Rechte.");
            return true;
        }
        var target = Bukkit.getOfflinePlayer(name);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            s.sendMessage("§cSpieler wurde noch nie auf diesem Server gesehen.");
            return true;
        }
        plugin.getStatsManager().resetCombatStats(target.getUniqueId());
        s.sendMessage("§aStats zurückgesetzt: §e" + (target.getName() == null ? name : target.getName()));
        Player online = target.getPlayer();
        if (online != null) online.sendMessage("§6Stats §8» §aDeine Kill/Death-Statistiken wurden zurückgesetzt.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] A) {
        List<String> out = new ArrayList<>();
        if (A.length == 1) {
            String pref = A[0].toLowerCase(Locale.ROOT);
            if ("reset".startsWith(pref)) out.add("reset");
            for (Player pl : Bukkit.getOnlinePlayers())
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(pref)) out.add(pl.getName());
        } else if (A.length == 2 && A[0].equalsIgnoreCase("reset")) {
            String pref = A[1].toLowerCase(Locale.ROOT);
            for (Player pl : Bukkit.getOnlinePlayers())
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(pref)) out.add(pl.getName());
        }
        return out;
    }
}
