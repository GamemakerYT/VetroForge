// src/main/java/com/vetroforge/commands/FMenuCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class FMenuCommand implements CommandExecutor {
    private final FactionsPlugin plugin;

    public FMenuCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (plugin.getFactionMenuManager() == null) {
            p.sendMessage(com.vetroforge.util.VetroForgeUtil.tr(plugin, p, "fmenu.message.unavailable"));
            return true;
        }
        plugin.getFactionMenuManager().openMain(p);
        return true;
    }
}
