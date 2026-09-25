package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.UiDecor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class SpawnCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public SpawnCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(UiDecor.err() + "Nur Spieler.");
            return true;
        }

        if (a.length > 0 && a[0].equalsIgnoreCase("cancel")) {
            plugin.getLobbyManager().cancelWarmup(p, true);
            return true;
        }

        if (!plugin.getLobbyManager().hasSpawn()) {
            p.sendMessage(UiDecor.err() + "Spawn ist nicht gesetzt!");
            return true;
        }

        p.sendMessage(UiDecor.boxTitle("§6Lobby-Spawn"));
        p.sendMessage(UiDecor.pfx() + "Teleport wird vorbereitet…");
        plugin.getLobbyManager().teleportToLobby(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1 && "cancel".startsWith(a[0].toLowerCase())) {
            out.add("cancel");
        }
        return out;
    }
}