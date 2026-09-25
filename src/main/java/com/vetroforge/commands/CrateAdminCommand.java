package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class CrateAdminCommand implements CommandExecutor {

    private final FactionsPlugin plugin;

    public CrateAdminCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler koennen /" + label + " nutzen.");
            return true;
        }
        if (!player.hasPermission("vetroforge.crates.admin") && !player.hasPermission("vetroforge.admin")) {
            player.sendMessage(UiDecor.noPermission());
            return true;
        }
        plugin.getCrateAdminGui().openCrates(player);
        return true;
    }
}
