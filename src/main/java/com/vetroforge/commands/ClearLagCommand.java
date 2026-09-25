package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class ClearLagCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;

    public ClearLagCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.clearlag")) {
            sender.sendMessage("§cKeine Rechte.");
            return true;
        }
        if (plugin.getClearLagService() == null) {
            sender.sendMessage("§cClearLag-Service ist nicht geladen.");
            return true;
        }
        String sub = args.length == 0 ? "now" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("now")) {
            plugin.getClearLagService().runNow(true);
            sender.sendMessage("§aClearLag ausgeführt.");
            return true;
        }
        if (sub.equals("countdown")) {
            int seconds = args.length >= 2 ? parse(args[1]) : 60;
            plugin.getClearLagService().beginCountdown(seconds);
            sender.sendMessage("§aClearLag-Countdown gestartet.");
            return true;
        }
        sender.sendMessage("§7/" + label + " now §8| §7/" + label + " countdown [seconds]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vetroforge.clearlag")) return List.of();
        if (args.length == 1) return List.of("now", "countdown");
        return List.of();
    }

    private int parse(String raw) {
        try {
            return Math.max(5, Math.min(300, Integer.parseInt(raw)));
        } catch (NumberFormatException ignored) {
            return 60;
        }
    }
}
