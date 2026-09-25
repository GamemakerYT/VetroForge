package com.vetroforge.commands;

import com.vetroforge.services.PrivateVaultService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public final class PrivateVaultCommand implements CommandExecutor, TabCompleter {
    private final PrivateVaultService service;

    public PrivateVaultCommand(PrivateVaultService service) {
        this.service = service;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        int number = 1;
        if (args.length > 0) {
            try { number = Integer.parseInt(args[0]); }
            catch (NumberFormatException ignored) {
                player.sendMessage(ChatColor.RED + "Nutze /" + label + " <nummer>.");
                return true;
            }
        }
        service.open(player, number);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!(sender instanceof Player player) || args.length != 1) return List.of();
        int max = service.access(player).maxVaults();
        List<String> out = new ArrayList<>();
        for (int i = 1; i <= max; i++) out.add(String.valueOf(i));
        return out;
    }
}
