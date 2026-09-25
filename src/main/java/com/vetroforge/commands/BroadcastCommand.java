// src/main/java/com/vetroforge/commands/BroadcastCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.BroadcastManager;
import net.kyori.adventure.text.Component;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class BroadcastCommand implements CommandExecutor {
    private final BroadcastManager bm;

    public BroadcastCommand(FactionsPlugin plugin) {
        this.bm = plugin.getBroadcastManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.staff.broadcast")) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "Verwendung: /" + label + " <Nachricht>");
            return true;
        }
        String colored = ChatColor.translateAlternateColorCodes('&', String.join(" ", args));

        if (bm != null) {
            bm.broadcastPlain(colored);
        } else {
            Bukkit.getServer().broadcastMessage(colored);
            for (Player p : Bukkit.getOnlinePlayers()) {
                p.sendActionBar(Component.text(ChatColor.stripColor(colored)));
            }
        }
        return true;
    }
}