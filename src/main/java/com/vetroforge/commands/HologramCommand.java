package com.vetroforge.commands;

import com.vetroforge.hologram.HologramManager;
import com.vetroforge.hologram.HologramType;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class HologramCommand implements CommandExecutor, TabCompleter {

    private final HologramManager hologramManager;

    public HologramCommand(HologramManager hologramManager) {
        this.hologramManager = hologramManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.command.holo")) {
            sender.sendMessage("§cYou do not have permission to manage holograms.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage("§cUsage: /" + label + " <set|remove|respawn> <type>");
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "set" -> handleSet(sender, args, label);
            case "remove" -> handleRemove(sender, args, label);
            case "respawn" -> handleRespawn(sender, args);
            default -> sender.sendMessage("§cUnknown sub command. Use set, remove or respawn.");
        }
        return true;
    }

    private void handleSet(CommandSender sender, String[] args, String label) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cOnly players can set hologram positions.");
            return;
        }
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /" + label + " set <type>");
            return;
        }
        HologramType type = HologramType.fromString(args[1]);
        if (type == null) {
            sender.sendMessage("§cUnknown hologram type.");
            return;
        }
        Location location = player.getLocation();
        if (hologramManager.setLocation(type, location)) {
            sender.sendMessage("§aStored hologram position for §f" + type.getDisplayName() + "§a.");
        } else {
            sender.sendMessage("§cFailed to store hologram location.");
        }
    }

    private void handleRemove(CommandSender sender, String[] args, String label) {
        if (args.length < 2) {
            sender.sendMessage("§cUsage: /" + label + " remove <type>");
            return;
        }
        HologramType type = HologramType.fromString(args[1]);
        if (type == null) {
            sender.sendMessage("§cUnknown hologram type.");
            return;
        }
        if (hologramManager.removeLocation(type)) {
            sender.sendMessage("§aRemoved hologram §f" + type.getDisplayName() + "§a.");
        } else {
            sender.sendMessage("§cNothing to remove for that hologram.");
        }
    }

    private void handleRespawn(CommandSender sender, String[] args) {
        if (args.length < 2) {
            hologramManager.respawnAll();
            sender.sendMessage("§aRespawned all holograms.");
            return;
        }
        HologramType type = HologramType.fromString(args[1]);
        if (type == null) {
            sender.sendMessage("§cUnknown hologram type.");
            return;
        }
        hologramManager.respawn(type);
        sender.sendMessage("§aRespawned hologram §f" + type.getDisplayName() + "§a.");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vetroforge.command.holo")) {
            return Collections.emptyList();
        }
        if (args.length == 1) {
            return StringUtil.copyPartialMatches(args[0], Arrays.asList("set", "remove", "respawn"), new ArrayList<>());
        }
        if (args.length == 2) {
            List<String> types = new ArrayList<>();
            for (HologramType type : HologramType.values()) {
                types.add(type.name().toLowerCase(Locale.ROOT));
            }
            return StringUtil.copyPartialMatches(args[1], types, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
