package com.vetroforge.commands;

import com.vetroforge.traveler.TravelerService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class AdminTravelerCommand implements CommandExecutor, TabCompleter {

    private final TravelerService service;

    public AdminTravelerCommand(TravelerService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur ingame.");
            return true;
        }
        if (!player.isOp() && !player.hasPermission("vetroforge.traveler.admin")) {
            player.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (args.length == 0) {
            service.spawnCurrent();
            player.sendMessage(service.activeInfo());
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Nutze /admintraveler set <id>");
                    return true;
                }
                service.setSpot(args[1], player.getLocation());
                player.sendMessage(ChatColor.GREEN + "Traveler-Spot " + ChatColor.WHITE + args[1] + ChatColor.GREEN + " gesetzt.");
            }
            case "remove" -> {
                if (args.length < 2) {
                    player.sendMessage(ChatColor.RED + "Nutze /admintraveler remove <id>");
                    return true;
                }
                boolean removed = service.removeSpot(args[1]);
                player.sendMessage(removed
                        ? ChatColor.GREEN + "Traveler-Spot entfernt."
                        : ChatColor.YELLOW + "Traveler-Spot nicht gefunden.");
            }
            case "list" -> service.spotLines().forEach(player::sendMessage);
            case "info" -> player.sendMessage(service.activeInfo());
            case "spawn" -> {
                service.spawnCurrent();
                player.sendMessage(service.activeInfo());
            }
            default -> usage(player);
        }
        return true;
    }

    private void usage(Player player) {
        player.sendMessage(ChatColor.LIGHT_PURPLE + "AdminTraveler " + ChatColor.DARK_GRAY + "» "
                + ChatColor.GRAY + "/admintraveler set <id> | remove <id> | list | info | spawn");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (sender instanceof Player player && !player.isOp() && !player.hasPermission("vetroforge.traveler.admin")) return out;
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (String option : List.of("set", "remove", "list", "info", "spawn")) {
                if (option.startsWith(prefix)) out.add(option);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (String id : service.spotIdsView()) {
                if (id.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(id);
            }
        }
        return out;
    }
}
