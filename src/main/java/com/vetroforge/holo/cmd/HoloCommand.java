package com.vetroforge.holo.cmd;

import com.vetroforge.holo.HoloLeaderboardService;
import com.vetroforge.holo.HoloLeaderboardService.Board;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

/**
 * /holo set <Type>
 * /holo remove <id|nearest>
 * /holo list
 * /holo tp <id>
 * /holo refresh
 * /holo clearall
 */
public class HoloCommand implements CommandExecutor, TabCompleter {

    private final HoloLeaderboardService svc;

    public HoloCommand(HoloLeaderboardService svc) { this.svc = svc; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("vetroforge.admin")) { s.sendMessage("§cKeine Rechte."); return true; }

        if (a.length == 0) { help(s, label); return true; }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "set" -> {
                if (!(s instanceof Player p)) { s.sendMessage("§cNur ingame."); return true; }
                if (a.length < 2) { s.sendMessage("§cType fehlt. z. B. §e/holo set TopFactions"); return true; }
                Board b = Board.parse(a[1]);
                if (b == null) { s.sendMessage("§cUnbekannter Type: §e" + a[1]); return true; }
                Location base = p.getLocation().clone().add(0, 2.2, 0);
                UUID id = svc.set(base, b);
                s.sendMessage("§aHolo gesetzt: §f" + b + " §7(ID: " + id + ")");
            }
            case "remove" -> {
                if (a.length < 2) { s.sendMessage("§cBitte ID oder §enearest§c angeben."); return true; }
                if ("nearest".equalsIgnoreCase(a[1])) {
                    if (!(s instanceof Player p)) { s.sendMessage("§cNur ingame."); return true; }
                    UUID id = svc.removeNearest(p.getLocation(), 8.0);
                    s.sendMessage(id == null ? "§7Kein Holo in der Nähe." : "§aEntfernt §7ID: " + id);
                } else {
                    try {
                        UUID id = UUID.fromString(a[1]);
                        boolean ok = svc.remove(id);
                        s.sendMessage(ok ? "§aEntfernt." : "§7ID nicht gefunden.");
                    } catch (IllegalArgumentException e) {
                        s.sendMessage("§cUngültige ID.");
                    }
                }
            }
            case "list" -> {
                for (String line : svc.list()) s.sendMessage("§7- " + line);
            }
            case "tp" -> {
                if (!(s instanceof Player p)) { s.sendMessage("§cNur ingame."); return true; }
                if (a.length < 2) { s.sendMessage("§cBitte ID."); return true; }
                try {
                    UUID id = UUID.fromString(a[1]);
                    Location loc = svc.getLocation(id);
                    if (loc == null) { s.sendMessage("§7ID nicht gefunden."); return true; }
                    p.teleport(loc.clone().add(0, -1.6, 0)); // vor die Base-Linie
                    s.sendMessage("§aTeleportiert.");
                } catch (IllegalArgumentException e) {
                    s.sendMessage("§cUngültige ID.");
                }
            }
            case "refresh" -> {
                svc.refreshNow();
                s.sendMessage("§aAlle Holos aktualisiert.");
            }
            case "clearall" -> {
                svc.clearAll();
                s.sendMessage("§aAlle Holos entfernt.");
            }
            default -> help(s, label);
        }
        return true;
    }

    private void help(CommandSender s, String label){
        s.sendMessage("§e/" + label + " set <TopFactions|TopCrystals|Warzone|TopKills|TopPrestige>");
        s.sendMessage("§e/" + label + " remove <id|nearest>");
        s.sendMessage("§e/" + label + " list");
        s.sendMessage("§e/" + label + " tp <id>");
        s.sendMessage("§e/" + label + " refresh");
        s.sendMessage("§e/" + label + " clearall");
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        if (a.length == 1) {
            return Arrays.asList("set","remove","list","tp","refresh","clearall")
                    .stream().filter(x -> x.startsWith(a[0].toLowerCase(Locale.ROOT))).toList();
        }
        if (a.length == 2 && "set".equalsIgnoreCase(a[0])) {
            return Arrays.asList("TopFactions","TopCrystals","Warzone","TopKills","TopPrestige")
                    .stream().filter(x -> x.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT))).toList();
        }
        if (a.length == 2 && ("remove".equalsIgnoreCase(a[0]) || "tp".equalsIgnoreCase(a[0]))) {
            List<String> ids = svc.list().stream()
                    .map(line -> line.split(" ", 2)[0]) // UUID am Anfang
                    .filter(uuid -> uuid.toLowerCase(Locale.ROOT).startsWith(a[1].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
            if ("remove".equalsIgnoreCase(a[0])) ids.add("nearest");
            return ids;
        }
        return Collections.emptyList();
    }
}