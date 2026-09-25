package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.raid.scout.ScoutingService;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class ScoutCommand implements CommandExecutor {

    private final FactionsPlugin plugin;
    private final ScoutingService service;

    public ScoutCommand(FactionsPlugin plugin, ScoutingService service) {
        this.plugin = plugin; this.service = service;
    }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }
        if (!p.hasPermission("vetroforge.scout")) { p.sendMessage(ChatColor.RED+"Keine Rechte."); return true; }
        if (a.length < 1) { p.sendMessage(ChatColor.YELLOW+"/"+l+" <FactionID> [Sekunden]"); return true; }

        String fid = a[0];
        int sec = (a.length>=2) ? Math.max(5, Math.min(120, tryInt(a[1], 30))) : 30;

        Location target = null;
        var fm = plugin.getFactionManager();
        if (fm != null) {
            target = fm.getHome(fid);
        }

        if (target == null) { p.sendMessage(ChatColor.RED+"Keine Ziel-Location bekannt."); return true; }

        service.start(p, target, sec);
        return true;
    }

    private int tryInt(String s, int def){ try { return Integer.parseInt(s); } catch (Exception e){ return def; } }
}