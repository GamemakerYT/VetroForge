// src/main/java/com/vetroforge/commands/SellCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.commerce.ShopGui;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class SellCommand implements CommandExecutor {

    private final FactionsPlugin plugin;
    public SellCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("§cDiesen Befehl kannst du nur ingame nutzen.");
            return true;
        }
        try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.3f); } catch (Throwable ignored) {}
        ShopGui.openSell(plugin, p);
        return true;
    }
}
