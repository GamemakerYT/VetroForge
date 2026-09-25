package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class SpeedCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public SpeedCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur Spieler.");
            return true;
        }
        if (!player.hasPermission("vetroforge.speed") && !player.hasPermission("vetroforge.admin")) {
            player.sendMessage("§cKeine Rechte.");
            return true;
        }
        if (args.length == 0) {
            player.sendMessage("§6Speed §8» §7Nutze §e/speed <1-10> §8oder §e/speed reset");
            return true;
        }

        String value = args[0].toLowerCase(Locale.ROOT);
        if (value.equals("reset") || value.equals("0")) {
            player.setWalkSpeed(0.2F);
            player.setFlySpeed(0.1F);
            player.sendMessage("§6Speed §8» §aZurückgesetzt.");
            return true;
        }

        int level;
        try {
            level = Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            player.sendMessage("§cBitte eine Zahl von 1 bis 10 nutzen.");
            return true;
        }
        level = Math.max(1, Math.min(10, level));
        float speed = Math.max(0.1F, Math.min(1.0F, level / 10.0F));

        if (player.isFlying() || player.getAllowFlight()) {
            player.setFlySpeed(speed);
            player.sendMessage("§6Speed §8» §fFly-Speed §a" + level + "§7/10");
        } else {
            player.setWalkSpeed(speed);
            player.sendMessage("§6Speed §8» §fWalk-Speed §a" + level + "§7/10");
        }
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("1", "2", "3", "4", "5", "6", "7", "8", "9", "10", "reset")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(option);
            }
        }
        return out;
    }
}
