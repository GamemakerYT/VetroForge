// src/main/java/com/vetroforge/commands/SeasonCommand.java
package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.*;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class SeasonCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss").withLocale(Locale.ROOT).withZone(ZoneId.systemDefault());

    public SeasonCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    private boolean admin(CommandSender s){
        return s.hasPermission("vetroforge.admin.season") || s.isOp();
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!admin(s)) { s.sendMessage(UiDecor.noPermission()); return true; }

        if (a.length == 0 || a[0].equalsIgnoreCase("info")) {
            String name = plugin.getSeasonManager().getCurrentName();
            long started = plugin.getSeasonManager().getCurrentStartEpoch();
            s.sendMessage("§8—————— §cVetro§6Forge §7• §fSeason §8——————");
            s.sendMessage("§7Name: §f" + name);
            s.sendMessage("§7Start: §f" + FMT.format(Instant.ofEpochSecond(started)));
            s.sendMessage("§8——————————————————————————————");
            return true;
        }

        if (a[0].equalsIgnoreCase("setname")) {
            if (a.length < 2) { s.sendMessage("§e/" + l + " setname <Season Name>"); return true; }
            String name = String.join(" ", java.util.Arrays.copyOfRange(a, 1, a.length));
            plugin.getSeasonManager().setCurrentName(name);
            s.sendMessage("§aSeason-Name gesetzt: §f" + name);
            return true;
        }

        if (a[0].equalsIgnoreCase("reset")) {
            if (a.length < 2) { s.sendMessage("§e/" + l + " reset <soft|hard> confirm"); return true; }
            boolean hard = a[1].equalsIgnoreCase("hard");
            if (a.length < 3 || !a[2].equalsIgnoreCase("confirm")) {
                s.sendMessage("§cAchtung: Dies setzt Spielerdaten zurück und archiviert die Leaderboards.");
                s.sendMessage("§e/" + l + " reset " + (hard ? "hard" : "soft") + " confirm");
                return true;
            }
            plugin.getSeasonManager().resetSeason(hard);
            s.sendMessage(hard ? "§aHard-Reset durchgeführt." : "§aSoft-Reset durchgeführt.");
            return true;
        }

        s.sendMessage("§8/§e" + l + " info §8| §esetname <Name> §8| §ereset <soft|hard> confirm");
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender s, Command c, String a, String[] A) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (A.length == 1) {
            for (String k : new String[]{"info","setname","reset"}) if (k.startsWith(A[0].toLowerCase(Locale.ROOT))) out.add(k);
            return out;
        }
        if (A.length == 2 && A[0].equalsIgnoreCase("reset")) {
            for (String k : new String[]{"soft","hard"}) if (k.startsWith(A[1].toLowerCase(Locale.ROOT))) out.add(k);
            return out;
        }
        if (A.length == 3 && A[0].equalsIgnoreCase("reset")) {
            if ("confirm".startsWith(A[2].toLowerCase(Locale.ROOT))) out.add("confirm");
        }
        return out;
    }
}