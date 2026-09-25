// src/main/java/com/vetroforge/commands/UpdaterCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.UpdateCheckerManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

public class UpdaterCommand implements CommandExecutor {
    private final UpdateCheckerManager uc;

    public UpdaterCommand(FactionsPlugin plugin) {
        this.uc = plugin.getUpdateCheckerManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {
        if (uc == null) {
            sender.sendMessage("§cUpdater nicht verfügbar.");
            return true;
        }
        sender.sendMessage("§eUpdate-Check gestartet …");
        uc.check(sender); // liefert Ergebnisnachricht zurück
        return true;
    }
}