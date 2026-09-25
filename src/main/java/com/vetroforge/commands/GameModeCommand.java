package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class GameModeCommand implements CommandExecutor, TabCompleter {

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.gamemode")) {
            sender.sendMessage(UiDecor.noPermission());
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§7/" + label + " <0|1|2|3|survival|creative|adventure|spectator> [spieler]");
            return true;
        }

        GameMode mode = parse(args[0]);
        if (mode == null) {
            sender.sendMessage("§cUnbekannter Gamemode.");
            return true;
        }

        Player target;
        if (args.length >= 2) {
            if (!sender.hasPermission("vetroforge.gamemode.others")) {
                sender.sendMessage("§cKeine Rechte für andere Spieler.");
                return true;
            }
            target = Bukkit.getPlayerExact(args[1]);
        } else {
            target = sender instanceof Player player ? player : null;
        }

        if (target == null) {
            sender.sendMessage("§cSpieler nicht gefunden.");
            return true;
        }

        target.setGameMode(mode);
        target.sendMessage("§6Gamemode §8» §7Du bist jetzt in §f" + mode.name().toLowerCase(Locale.ROOT) + "§7.");
        if (!target.equals(sender)) {
            sender.sendMessage("§6Gamemode §8» §f" + target.getName() + " §7ist jetzt in §f" + mode.name().toLowerCase(Locale.ROOT) + "§7.");
        }
        return true;
    }

    private GameMode parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "0", "s", "survival" -> GameMode.SURVIVAL;
            case "1", "c", "creative" -> GameMode.CREATIVE;
            case "2", "a", "adventure" -> GameMode.ADVENTURE;
            case "3", "sp", "spectator" -> GameMode.SPECTATOR;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vetroforge.gamemode")) return List.of();
        if (args.length == 1) {
            return List.of("0", "1", "2", "3", "survival", "creative", "adventure", "spectator").stream()
                    .filter(value -> value.startsWith(args[0].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        if (args.length == 2 && sender.hasPermission("vetroforge.gamemode.others")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(player.getName());
            }
            return out;
        }
        return List.of();
    }
}
