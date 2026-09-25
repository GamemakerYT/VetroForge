package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.PrestigeUtil;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Locale;

public class PrestigeCommand implements CommandExecutor {
    private static final String ARROW = "§8» §f";
    private final FactionsPlugin plugin;
    public PrestigeCommand(FactionsPlugin plugin){ this.plugin=plugin; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        plugin.getPrestigeGui().open(p);
        return true;
    }
}
