// ===================================================================
// File: src/main/java/com/vetroforge/commands/FRelationCommand.java
// ===================================================================
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.factions.FactionRelationsManager;
import com.vetroforge.factions.RelationType;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

public class FRelationCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public FRelationCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player)) { s.sendMessage("Ingame verwenden."); return true; }
        if (!s.hasPermission("vetroforge.faction.relation")) {
            s.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        Player p = (Player) s;
        String myFaction = plugin.getFactionManager().getFactionOf(p.getUniqueId());
        FactionRelationsManager rm = plugin.getFactionRelationsManager();

        if (a.length == 0) {
            s.sendMessage(header("Relations"));
            if (myFaction == null) {
                s.sendMessage(ChatColor.GRAY + "Du bist in keiner Faction.");
            } else {
                Map<String, RelationType> all = rm.getAllFor(myFaction);
                if (all.isEmpty()) {
                    s.sendMessage(ChatColor.GRAY + "Keine speziellen Beziehungen gesetzt (alles NEUTRAL).");
                } else {
                    for (Map.Entry<String, RelationType> e : all.entrySet()) {
                        s.sendMessage(ChatColor.YELLOW + e.getKey() + ChatColor.GRAY + " → " + ChatColor.WHITE + e.getValue().name());
                    }
                }
            }
            s.sendMessage(footer());
            s.sendMessage(ChatColor.YELLOW + "Verwendung: /frelation <faction> <ally|truce|neutral|enemy|war>");
            return true;
        }

        if (a.length < 2) {
            s.sendMessage(ChatColor.YELLOW + "Verwendung: /frelation <faction> <ally|truce|neutral|enemy|war>");
            return true;
        }

        if (myFaction == null) {
            s.sendMessage(ChatColor.RED + "Du bist in keiner Faction.");
            return true;
        }

        String other = a[0];
        RelationType t = parse(a[1]);
        if (t == null) {
            s.sendMessage(ChatColor.RED + "Unbekannter Relationstyp: " + a[1]);
            return true;
        }
        if (other.equalsIgnoreCase(myFaction)) {
            s.sendMessage(ChatColor.RED + "Eigene Faction kann nicht gesetzt werden.");
            return true;
        }

        rm.setRelation(myFaction, other, t);
        s.sendMessage(ChatColor.GREEN + "Relation zu " + other + " ist nun " + t.name() + ".");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) {
            // Set -> List umwandeln, damit copyPartialMatches sauber arbeitet
            List<String> names = new ArrayList<>(plugin.getFactionManager().getAllFactionNames());
            return StringUtil.copyPartialMatches(a[0], names, new ArrayList<>());
        }
        if (a.length == 2) {
            List<String> types = Arrays.asList("ally","truce","neutral","enemy","war");
            return StringUtil.copyPartialMatches(a[1], types, new ArrayList<>());
        }
        return Collections.emptyList();
    }

    private RelationType parse(String s){
        String x = s.toLowerCase(java.util.Locale.ROOT);
        if (x.equals("ally")) return RelationType.ALLY;
        if (x.equals("truce")) return RelationType.TRUCE;
        if (x.equals("neutral")) return RelationType.NEUTRAL;
        if (x.equals("enemy")) return RelationType.ENEMY;
        if (x.equals("war")) return RelationType.WAR;
        return null;
    }

    private String header(String section){
        return ChatColor.DARK_RED + "—————— " + ChatColor.RED + "VetroForge" + ChatColor.WHITE + " • " + section + ChatColor.DARK_RED + " ——————";
    }
    private String footer(){ return ChatColor.DARK_RED + "——————————————————————————————"; }
}