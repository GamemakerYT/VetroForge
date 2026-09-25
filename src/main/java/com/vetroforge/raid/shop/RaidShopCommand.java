package com.vetroforge.raid.shop;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class RaidShopCommand implements CommandExecutor {

    private final FactionsPlugin plugin;
    private final RaidShopGui gui;

    public RaidShopCommand(FactionsPlugin plugin, RaidShopGui gui) {
        this.plugin = plugin;
        this.gui = gui;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (a.length > 0 && a[0].equalsIgnoreCase("admin")) {
            if (!p.hasPermission("vetroforge.raidshop.admin")) {
                p.sendMessage("§cDazu hast du keine Rechte.");
                return true;
            }
            gui.openAdmin(p);
            return true;
        }
        gui.open(p);
        return true;
    }
}
