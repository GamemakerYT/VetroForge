// src/main/java/com/vetroforge/commands/ScoreboardCommand.java
package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.display.SidebarScoreboardManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class ScoreboardCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public ScoreboardCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage(UiDecor.playersOnly()); return true; }

        SidebarScoreboardManager sb = plugin.getSidebarScoreboardManager();

        if (args.length == 0 || args[0].equalsIgnoreCase("lobby")) {
            sb.showLobbyBoard(p); return true;
        }
        if (args[0].equalsIgnoreCase("warzone")) {
            sb.showWarzoneBoard(p); return true;
        }
        if (args[0].equalsIgnoreCase("faction")) {
            sb.showLobbyBoard(p);
            p.sendMessage("§7(Tipp: Detaillierte Faction-Infos per §e/f info§7.)");
            return true;
        }
        if (args[0].equalsIgnoreCase("raid")) {
            if (args.length >= 5) {
                try {
                    String target  = args[1];
                    int broken     = Integer.parseInt(args[2]);
                    int goal       = Integer.parseInt(args[3]);
                    int seconds    = Integer.parseInt(args[4]);
                    int raidersCnt = (args.length >= 6) ? Integer.parseInt(args[5]) : 0;

                    // Neuer Overload (nur Anzahl der Raider nötig):
                    sb.showRaidBoard(p, target, broken, goal, seconds, raidersCnt);

                } catch (NumberFormatException ex) {
                    p.sendMessage("§cZahlen erwartet: /scoreboard raid <target> <broken> <goal> <seconds> [raiders]");
                }
            } else {
                p.sendMessage("§7Nutze: §e/scoreboard raid <target> <broken> <goal> <seconds> [raiders]");
            }
            return true;
        }

        p.sendMessage("§eVerwendung: /scoreboard [lobby|faction|warzone|raid]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            return Arrays.asList("lobby","faction","warzone","raid").stream()
                    .filter(s -> s.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("raid")) {
            try {
                return plugin.getFactionManager().getAllFactionNames().stream()
                        .filter(n -> n.toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT)))
                        .limit(10)
                        .collect(Collectors.toList());
            } catch (Throwable ignored) {}
        }
        return Collections.emptyList();
    }
}