// src/main/java/com/vetroforge/commands/BalanceTuningCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.BalanceTuningManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class BalanceTuningCommand implements CommandExecutor {

    private final BalanceTuningManager tuning;

    public BalanceTuningCommand(FactionsPlugin plugin) {
        this.tuning = plugin.getBalanceTuningManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.balancetune")) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        sender.sendMessage(ChatColor.YELLOW + "Starte Economy-Tuning …");
        try {
            tuning.runTuning();
            sender.sendMessage(ChatColor.GREEN + "Fertig. Details in der Konsole/Logs.");
        } catch (Throwable t) {
            sender.sendMessage(ChatColor.RED + "Fehler beim Tuning: " + t.getMessage());
        }
        return true;
    }
}