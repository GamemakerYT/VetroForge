// src/main/java/com/vetroforge/commands/FactionWorldCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.factions.FactionManager;
import com.vetroforge.world.WorldManager;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FactionWorldCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    private final FactionManager fm;

    public FactionWorldCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.fm = plugin.getFactionManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler.");
            return true;
        }
        if (fm == null) {
            p.sendMessage(ChatColor.RED + "Faction-System ist nicht verfügbar.");
            return true;
        }

        // /fworld  oder  /fworld spawn
        if (args.length == 0 || args[0].equalsIgnoreCase("spawn")) {
            String ownName = fm.getFactionOf(p.getUniqueId());
            if (ownName == null || ownName.isBlank()) {
                p.sendMessage(ChatColor.RED + "Du bist in keiner Faction.");
                return true;
            }
            World w = plugin.getWorldManager().createOrLoadFactionWorld(ownName);
            if (w == null) {
                p.sendMessage(ChatColor.RED + "Factionswelt konnte nicht erstellt/geladen werden.");
                return true;
            }
            p.teleport(w.getSpawnLocation());
            p.sendMessage(ChatColor.GREEN + "Teleport zu " + ChatColor.YELLOW + ownName + ChatColor.GREEN + " (Spawn).");
            return true;
        }

        // /fworld tp <FactionName>  (alias: warp)
        if (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("warp")) {
            if (args.length < 2) {
                p.sendMessage(ChatColor.YELLOW + "Verwendung: /" + lbl + " tp <FactionName>");
                return true;
            }
            String targetFaction = args[1];

            if (!p.isOp() && !p.hasPermission("vetroforge.admin")) {
                String own = fm.getFactionOf(p.getUniqueId());
                if (own == null || own.isBlank()) {
                    p.sendMessage(ChatColor.RED + "Du bist in keiner Faction.");
                    return true;
                }
                if (!own.equalsIgnoreCase(targetFaction) && !fm.getAllies(own).contains(targetFaction)) {
                    p.sendMessage(ChatColor.RED + "Du kannst nur in die Welt deiner eigenen Faction oder von Verbündeten reisen.");
                    return true;
                }
            }

            World w = plugin.getWorldManager().createOrLoadFactionWorld(targetFaction);
            if (w == null) {
                p.sendMessage(ChatColor.RED + "Factionswelt konnte nicht geladen/erstellt werden.");
                return true;
            }
            p.teleport(w.getSpawnLocation());
            p.sendMessage(ChatColor.GREEN + "Teleport zu " + ChatColor.YELLOW + targetFaction + ChatColor.GREEN + ".");
            return true;
        }

        // /fworld list
        if (args[0].equalsIgnoreCase("list")) {
            List<String> names = new ArrayList<>(fm.getAllFactionNames());
            if (names.isEmpty()) {
                p.sendMessage(ChatColor.GRAY + "Es gibt noch keine Factions.");
            } else {
                p.sendMessage(ChatColor.DARK_GRAY + "—— " + ChatColor.WHITE + "Factions (" + names.size() + ") " + ChatColor.DARK_GRAY + "——");
                p.sendMessage(ChatColor.GRAY + String.join(ChatColor.DARK_GRAY + ", " + ChatColor.GRAY, names));
            }
            return true;
        }

        p.sendMessage(ChatColor.YELLOW + "Verwendung: /" + lbl + " [spawn|tp <FactionName>|list]");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (!(sender instanceof Player)) return out;

        if (args.length == 1) {
            String pfx = args[0].toLowerCase(Locale.ROOT);
            for (String s : new String[]{"spawn","tp","warp","list"})
                if (s.startsWith(pfx)) out.add(s);
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("warp"))) {
            String pfxF = args[1].toLowerCase(Locale.ROOT);
            for (String name : fm.getAllFactionNames()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(pfxF)) out.add(name);
            }
            return out;
        }
        return out;
    }
}