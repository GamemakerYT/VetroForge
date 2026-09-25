package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
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

public final class GameModeShortcutCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public GameModeShortcutCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.gamemode") && !sender.hasPermission("vetroforge.admin")) {
            sender.sendMessage("§cKeine Rechte.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage("§6Gamemode §8» §7Nutze §e/gm <1|2|3|4> [Spieler]");
            return true;
        }

        GameMode mode = parse(args[0]);
        if (mode == null) {
            sender.sendMessage("§cUnbekannter Gamemode. Nutze 1, 2, 3 oder 4.");
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
            if (target == null) {
                sender.sendMessage("§cSpieler nicht online.");
                return true;
            }
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage("§cKonsole: /gm <1|2|3|4> <Spieler>");
            return true;
        }

        target.setGameMode(mode);
        String labelMode = switch (mode) {
            case CREATIVE -> "Creative";
            case ADVENTURE -> "Adventure";
            case SPECTATOR -> "Spectator";
            default -> "Survival";
        };
        target.sendMessage("§6Gamemode §8» §a" + labelMode);
        if (!target.equals(sender)) sender.sendMessage("§6Gamemode §8» §f" + target.getName() + " §7ist jetzt §a" + labelMode);
        return true;
    }

    private GameMode parse(String raw) {
        String value = raw.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "1", "creative", "c" -> GameMode.CREATIVE;
            case "2", "adventure", "a" -> GameMode.ADVENTURE;
            case "3", "spectator", "spec" -> GameMode.SPECTATOR;
            case "4", "0", "survival", "s" -> GameMode.SURVIVAL;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("1", "2", "3", "4")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(option);
            }
        } else if (args.length == 2) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(player.getName());
            }
        }
        return out;
    }
}
