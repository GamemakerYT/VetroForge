package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.CosmeticsManager;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class CosmeticsCommand implements CommandExecutor {
    private final FactionsPlugin plugin;
    public CosmeticsCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage("Nur ingame."); return true; }
        CosmeticsManager cm = plugin.getCosmeticsManager();
        if (cm == null) { p.sendMessage("§cCosmetics nicht aktiv."); return true; }
        if (args.length >= 2 && args[0].equalsIgnoreCase("preview")) {
            if (!cm.startPreview(p, args[1], 60)) {
                p.sendMessage("§cCosmetic-Preview nicht gefunden oder nicht verfügbar.");
            }
            return true;
        }
        cm.openMenu(p);
        return true;
    }
}
