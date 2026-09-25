package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.capture.CaptureZoneManager;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

public final class AdminCaptureCommand implements CommandExecutor, TabCompleter {

    private final CaptureZoneManager manager;

    public AdminCaptureCommand(CaptureZoneManager manager) {
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.capture.admin")) {
            sender.sendMessage(UiDecor.noPermission());
            return true;
        }
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(java.util.Locale.ROOT);
        if (sub.equals("list")) {
            sender.sendMessage("§6Capture Flags §8» §7" + manager.listFlags().size() + " Punkte");
            for (var flag : manager.listFlags()) sender.sendMessage(line(flag));
            return true;
        }
        if (sub.equals("set") && sender instanceof Player player) {
            String name = name(args);
            manager.setFlag(name, player.getLocation());
            sender.sendMessage("§6Capture §8» §a" + name + " gesetzt.");
            return true;
        }
        if (sub.equals("remove")) {
            String name = name(args);
            sender.sendMessage(manager.removeFlag(name)
                    ? "§6Capture §8» §c" + name + " entfernt."
                    : "§6Capture §8» §cFlagge nicht gefunden.");
            return true;
        }
        if (sub.equals("info")) {
            var flag = manager.getFlag(name(args));
            sender.sendMessage(flag == null ? "§6Capture §8» §cFlagge nicht gefunden." : line(flag));
            return true;
        }
        if (sub.equals("season") || sub.equals("top")) {
            sender.sendMessage("§c§lCTF Season §8» §7Top Factions");
            int rank = 1;
            for (var entry : manager.seasonLeaderboard(10)) {
                sender.sendMessage("§8#" + rank + " §f" + entry.faction() + " §8- §c" + entry.captures() + " Captures");
                rank++;
            }
            if (rank == 1) sender.sendMessage("§7Noch keine Captures in dieser Season.");
            return true;
        }
        sender.sendMessage("§6Capture §8» §f/" + label + " <set|list|info|remove|season> [Flag Name]");
        return true;
    }

    private String name(String[] args) {
        return args.length < 2 ? "Flag 1" : String.join(" ", Arrays.copyOfRange(args, 1, args.length));
    }

    private String line(CaptureZoneManager.FlagSnapshot flag) {
        Location at = flag.location();
        return "§8• §f" + flag.name() + " §8» §7"
                + at.getWorld().getName() + " §f" + at.getBlockX() + " " + at.getBlockY() + " " + at.getBlockZ()
                + " §8• §7Faction: §f" + (flag.ownerFaction() == null ? "-" : flag.ownerFaction())
                + " §8• §7Capture: §e" + flag.progressPercent() + "%";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vetroforge.capture.admin")) return List.of();
        if (args.length == 1) return List.of("set", "list", "info", "remove", "season", "top").stream()
                .filter(value -> value.startsWith(args[0].toLowerCase(java.util.Locale.ROOT))).toList();
        return List.of();
    }
}
