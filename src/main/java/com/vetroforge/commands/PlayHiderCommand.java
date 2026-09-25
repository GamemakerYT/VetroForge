// src/main/java/com/vetroforge/commands/PlayHiderCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.commands.BaseCommand;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class PlayHiderCommand extends BaseCommand implements CommandExecutor, TabCompleter {

    public enum Mode { STAFF, ALL, NONE, FRIENDS }

    private final FactionsPlugin plugin;
    private final Map<UUID, Mode> prefs = new HashMap<UUID, Mode>();

    public PlayHiderCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player)) { s.sendMessage("Ingame."); return true; }
        Player p = (Player) s;
        if (a.length == 0) {
            sendFramed(p, "PlayHider",
                    "&7Modi: &eALL&7, &eNONE&7, &eSTAFF&7, &eFRIENDS",
                    "&7Beispiel: &e/playhider STAFF");
            return true;
        }
        Mode m;
        try { m = Mode.valueOf(a[0].toUpperCase(Locale.ROOT)); }
        catch (Exception ex){ p.sendMessage(ChatColor.RED+"Unbekannter Modus."); return true; }
        prefs.put(p.getUniqueId(), m);
        apply(p, m);
        sendFramed(p, "PlayHider", "&7Aktiv: &e" + m.name());
        return true;
    }

    private void apply(Player viewer, Mode m) {
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (viewer.equals(other)) continue;
            boolean show = shouldShow(viewer, other, m);
            if (show) viewer.showPlayer(plugin, other);
            else viewer.hidePlayer(plugin, other);
        }
    }

    private boolean shouldShow(Player viewer, Player other, Mode m) {
        switch (m) {
            case ALL: return true;
            case NONE: return false;
            case STAFF:
                return other.hasPermission("vetroforge.rank.staff")
                        || other.hasPermission("vetroforge.rank.mod")
                        || other.hasPermission("vetroforge.rank.admin")
                        || other.hasPermission("vetroforge.rank.support");
            case FRIENDS:
                // TODO: echte Freundesliste; vorerst gleiche Faction = „Freund“
                String fv = plugin.getFactionManager().getFactionOf(viewer.getUniqueId());
                String fo = plugin.getFactionManager().getFactionOf(other.getUniqueId());
                return fv != null && fv.equalsIgnoreCase(fo);
        }
        return true;
    }

    @Override public java.util.List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length==1) return org.bukkit.util.StringUtil.copyPartialMatches(a[0],
                java.util.Arrays.asList("ALL","NONE","STAFF","FRIENDS"), new java.util.ArrayList<String>());
        return java.util.Collections.<String>emptyList();
    }
}