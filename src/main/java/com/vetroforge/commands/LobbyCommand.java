package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class LobbyCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    public LobbyCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    private boolean noPerm(CommandSender s) {
        if (s.hasPermission("vetroforge.admin") || s.isOp()) return false;
        s.sendMessage(UiDecor.noPermission());
        return true;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {

        if (a.length == 0 || a[0].equalsIgnoreCase("spawn")) {
            if (!(sender instanceof Player p)) { sender.sendMessage(UiDecor.playersOnly()); return true; }
            plugin.getLobbyManager().teleportToLobby(p);
            return true;
        }

        if (a[0].equalsIgnoreCase("cancel")) {
            if (!(sender instanceof Player p)) { sender.sendMessage(UiDecor.playersOnly()); return true; }
            plugin.getLobbyManager().cancelWarmup(p, true);
            return true;
        }

        // /lobby fly [on|off|toggle]
        if (a[0].equalsIgnoreCase("fly")) {
            if (!(sender instanceof Player p)) { sender.sendMessage(UiDecor.playersOnly()); return true; }
            boolean enabled;
            if (a.length >= 2) {
                String v = a[1].toLowerCase();
                if (v.equals("on")) enabled = true;
                else if (v.equals("off")) enabled = false;
                else enabled = !p.getAllowFlight(); // toggle
            } else {
                enabled = !p.getAllowFlight(); // toggle
            }
            plugin.getLobbyManager().setFlyMode(p, enabled);
            return true;
        }

        // /lobby setspawn
        if (a[0].equalsIgnoreCase("setspawn")) {
            if (!(sender instanceof Player p)) { sender.sendMessage(UiDecor.playersOnly()); return true; }
            if (noPerm(sender)) return true;
            Location l = p.getLocation();
            plugin.getConfig().set("lobby.world", l.getWorld().getName());
            plugin.getConfig().set("lobby.x", l.getX());
            plugin.getConfig().set("lobby.y", l.getY());
            plugin.getConfig().set("lobby.z", l.getZ());
            plugin.getConfig().set("lobby.yaw", l.getYaw());
            plugin.getConfig().set("lobby.pitch", l.getPitch());
            plugin.saveConfig();
            plugin.getLobbyManager().setSpawn(p.getLocation());
            sender.sendMessage("§6Lobby §8» §aSpawn gesetzt.");
            return true;
        }

        // /lobby reload (nur Admin)
        if (a[0].equalsIgnoreCase("reload")) {
            if (noPerm(sender)) return true;
            plugin.reloadConfig();
            plugin.getLobbyManager().reload();
            sender.sendMessage("§aLobby-Konfiguration neu geladen.");
            return true;
        }

        sender.sendMessage("§e/" + label + " spawn §8| §e/" + label + " cancel §8| §e/" + label + " fly [on|off|toggle] §8| §e/" + label + " setspawn §8| §e/" + label + " reload");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] A) {
        List<String> out = new ArrayList<>();
        if (A.length == 1) {
            String pref = A[0].toLowerCase();
            for (String opt : new String[]{"spawn","cancel","fly","setspawn","reload"})
                if (opt.startsWith(pref)) out.add(opt);
        } else if (A.length == 2 && A[0].equalsIgnoreCase("fly")) {
            String pref = A[1].toLowerCase();
            for (String opt : new String[]{"on","off","toggle"})
                if (opt.startsWith(pref)) out.add(opt);
        }
        return out;
    }
}