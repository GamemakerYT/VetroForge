package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.TicketManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class TicketCommand implements CommandExecutor {
    private final TicketManager tm;

    public TicketCommand(FactionsPlugin plugin) {
        this.tm = plugin.getTicketManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {
        if (args.length == 0) {
            sender.sendMessage("§eUsage: /ticket <Nachricht>");
            return true;
        }
        tm.createTicket(sender.getName(), String.join(" ", args));
        sender.sendMessage("§aTicket gesendet.");
        return true;
    }
}