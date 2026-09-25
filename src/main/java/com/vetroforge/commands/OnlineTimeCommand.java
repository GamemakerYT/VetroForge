// src/main/java/com/vetroforge/commands/OnlineTimeCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class OnlineTimeCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public OnlineTimeCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }

        if (a.length == 0) {
            long sec = plugin.getStatsManager().getOnlineSeconds(p);
            p.sendMessage(plugin.getI18n().tr(p, "onlinetime.self",
                    Map.of("time", format(sec))));
            return true;
        }

        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) {
            p.sendMessage(plugin.getI18n().tr(p, "common.error"));
            p.sendMessage("§cSpieler ist nicht online.");
            return true;
        }

        long sec = plugin.getStatsManager().getOnlineSeconds(t);
        p.sendMessage(plugin.getI18n().tr(p, "onlinetime.other",
                Map.of("player", t.getName(), "time", format(sec))));
        return true;
    }

    private String format(long seconds) {
        long s = Math.max(0, seconds);
        long h = s / 3600;
        long m = (s % 3600) / 60;
        long sc = s % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", h, m, sc);
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] A) {
        List<String> out = new ArrayList<>();
        if (A.length == 1) {
            String pref = A[0].toLowerCase(Locale.ROOT);
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(pref)) out.add(pl.getName());
            }
        }
        return out;
    }
}