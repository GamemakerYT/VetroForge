package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class LinkWebCommand implements CommandExecutor {
    private final FactionsPlugin plugin;

    public LinkWebCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler koennen ihren Web-Account verknuepfen.");
            return true;
        }
        if (args.length < 1) {
            player.sendMessage("§cWeb §8» §7Nutze §f/linkweb <code>§7.");
            player.sendMessage("§7Den Code bekommst du im Web-Dashboard.");
            return true;
        }
        if (plugin.getWebApiManager() == null) {
            player.sendMessage("§cWeb §8» §7Die WebAPI ist gerade nicht aktiv.");
            return true;
        }
        if (plugin.getWebApiManager().claimLinkCode(player, args[0])) {
            player.sendMessage("§aWeb §8» §7Dein Minecraft-Account wurde mit dem Web-Dashboard verknuepft.");
        } else {
            player.sendMessage("§cWeb §8» §7Code ungueltig oder abgelaufen.");
        }
        return true;
    }
}
