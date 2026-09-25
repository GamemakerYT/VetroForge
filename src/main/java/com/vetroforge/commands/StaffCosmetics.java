package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.CosmeticsManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class StaffCosmetics implements CommandExecutor {
    private final FactionsPlugin plugin;
    public StaffCosmetics(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur ingame."); return true; }
        CosmeticsManager cm = plugin.getCosmeticsManager();
        if (cm == null) { p.sendMessage("§cCosmetics nicht aktiv."); return true; }
        // VetroPerms-Check passiert in openStaffMenu()
        cm.openStaffMenu(p);
        return true;
    }
}