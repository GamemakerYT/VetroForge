package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public final class AutoNotifyCommand implements CommandExecutor {
    private final FactionsPlugin plugin;

    public AutoNotifyCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Ingame.");
            return true;
        }
        if (!player.hasPermission("vetroforge.staff.notify")) {
            player.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        boolean enabled = !plugin.getNotifyManager().isAutoOnJoin(player.getUniqueId());
        plugin.getNotifyManager().setAutoOnJoin(player.getUniqueId(), enabled);
        if (enabled) plugin.getNotifyManager().setEnabled(player.getUniqueId(), true);
        player.sendMessage(enabled
                ? ChatColor.GREEN + "AutoNotify aktiviert. Notify ist ab Join automatisch an."
                : ChatColor.YELLOW + "AutoNotify deaktiviert.");
        return true;
    }
}
