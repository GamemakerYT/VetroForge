package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class LootDropCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public LootDropCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.command.lootdrop")) {
            sender.sendMessage("§cYou do not have permission to use this command.");
            return true;
        }
        if (args.length == 0 || !args[0].equalsIgnoreCase("at")) {
            sender.sendMessage("§cUsage: /" + label + " at <x> <y> <z> [world]");
            return true;
        }
        if (args.length < 4 && !(sender instanceof Player)) {
            sender.sendMessage("§cYou must provide coordinates and world when running from console.");
            return true;
        }
        Location location;
        if (args.length >= 4) {
            try {
                double x = Double.parseDouble(args[1]);
                double y = Double.parseDouble(args[2]);
                double z = Double.parseDouble(args[3]);
                World world;
                if (args.length >= 5) {
                    world = Bukkit.getWorld(args[4]);
                    if (world == null) {
                        sender.sendMessage("§cUnknown world: " + args[4]);
                        return true;
                    }
                } else if (sender instanceof Player player) {
                    world = player.getWorld();
                } else {
                    sender.sendMessage("§cPlease specify a world.");
                    return true;
                }
                location = new Location(world, x, y, z);
            } catch (NumberFormatException ex) {
                sender.sendMessage("§cCoordinates must be numeric.");
                return true;
            }
        } else {
            Player player = (Player) sender;
            location = player.getLocation();
        }
        spawnLootDrop(sender, location);
        return true;
    }

    private void spawnLootDrop(CommandSender sender, Location location) {
        // Placeholder implementation; integrate with loot drop manager if available
        sender.sendMessage("§aScheduled loot drop at §f" + String.format(Locale.US, "%.1f %.1f %.1f", location.getX(), location.getY(), location.getZ())
                + " §ain world §f" + location.getWorld().getName());
        plugin.getPrefixedLogger().info(sender.getName() + " scheduled loot drop at " + location);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vetroforge.command.lootdrop")) {
            return Collections.emptyList();
        }
        if (!(sender instanceof Player player)) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Collections.singletonList("at"), new ArrayList<>());
        }
        if (!args[0].equalsIgnoreCase("at")) {
            return Collections.emptyList();
        }
        if (args.length == 2) {
            return Collections.singletonList(String.valueOf(player.getLocation().getBlockX()));
        }
        if (args.length == 3) {
            return Collections.singletonList(String.valueOf(player.getLocation().getBlockY()));
        }
        if (args.length == 4) {
            return Collections.singletonList(String.valueOf(player.getLocation().getBlockZ()));
        }
        if (args.length == 5) {
            return Collections.singletonList(player.getWorld().getName());
        }
        return Collections.emptyList();
    }
}
