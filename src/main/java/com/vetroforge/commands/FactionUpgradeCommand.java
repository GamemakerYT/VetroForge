package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class FactionUpgradeCommand implements CommandExecutor {
    private final FactionsPlugin plugin;

    public FactionUpgradeCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur ingame.");
            return true;
        }
        if (plugin.getFactionUpgradeManager() == null) {
            player.sendMessage("§cFraktionsmacht-Shop ist gerade nicht geladen.");
            return true;
        }
        plugin.getFactionUpgradeManager().open(player);
        return true;
    }
}
