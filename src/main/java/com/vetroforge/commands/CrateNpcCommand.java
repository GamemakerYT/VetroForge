// src/main/java/com/vetroforge/commands/CrateNpcCommand.java
package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.crates.CrateNpcManager;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class CrateNpcCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    private final CrateNpcManager mgr;

    public CrateNpcCommand(FactionsPlugin plugin, CrateNpcManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) { sender.sendMessage(UiDecor.playersOnly()); return true; }
        if (!hasAccess(p)) { p.sendMessage(UiDecor.noPermission()); return true; }

        if (args.length == 0) {
            sendHelp(p, label);
            return true;
        }

        switch (args[0].toLowerCase(Locale.ROOT)) {
            case "add": {
                String name = (args.length >= 2) ? joinFrom(args, 1) : "Crate Redeemer";
                Location loc = p.getLocation();
                var info = mgr.addNpc(loc, name);
                if (info == null) { p.sendMessage("§cKonnte NPC nicht erstellen."); return true; }
                p.sendMessage("§aNPC erstellt @ §f" + loc.getWorld().getName() + " §7(" +
                        loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ() + ")");
                return true;
            }
            case "remove": {
                Entity target = p.getTargetEntity(6);
                if (target == null) { p.sendMessage("§cBitte schaue einen Crate-NPC an (max 6 Blöcke)."); return true; }
                boolean ok = mgr.removeNpc(target.getUniqueId());
                p.sendMessage(ok ? "§aNPC entfernt." : "§cDas ist kein Crate-NPC.");
                return true;
            }
            case "list": {
                var list = mgr.listInfos();
                if (list.isEmpty()) { p.sendMessage("§7Keine Crate-NPCs gespeichert."); return true; }
                p.sendMessage("§6Crate-NPCs:");
                for (var i : list) {
                    p.sendMessage(" §8- §f" + i.name() + " §7@" + i.world() +
                            " §7(" + i.x() + "," + i.y() + "," + i.z() + ") §8[" + i.uuid() + "]"
                            + (i.glow() ? " §e(glow)" : ""));
                }
                return true;
            }
            case "respawn": {
                mgr.spawnAll();
                p.sendMessage("§aAlle Crate-NPCs neu gespawnt.");
                return true;
            }
            case "rename": {
                Entity target = p.getTargetEntity(6);
                if (target == null) { p.sendMessage("§cBitte schaue den NPC an (max 6 Blöcke)."); return true; }
                if (args.length < 2) { p.sendMessage("§eVerwendung: /" + label + " rename <Neuer Name>"); return true; }
                String newName = joinFrom(args, 1);
                boolean ok = mgr.rename(target.getUniqueId(), newName);
                p.sendMessage(ok ? "§aName aktualisiert." : "§cKonnte Namen nicht setzen.");
                return true;
            }
            case "glow": {
                Entity target = p.getTargetEntity(6);
                if (target == null) { p.sendMessage("§cBitte schaue den NPC an (max 6 Blöcke)."); return true; }
                boolean val;
                if (args.length >= 2) {
                    if ("on".equalsIgnoreCase(args[1])) val = true;
                    else if ("off".equalsIgnoreCase(args[1])) val = false;
                    else { p.sendMessage("§eVerwendung: /" + label + " glow <on|off> (ohne Angabe = toggle)"); return true; }
                } else {
                    val = !target.isGlowing(); // toggle
                }
                boolean ok = mgr.setGlow(target.getUniqueId(), val);
                p.sendMessage(ok ? ("§aGlow " + (val ? "an" : "aus") + ".") : "§cKonnte Glow nicht setzen.");
                return true;
            }
            default: {
                sendHelp(p, label);
                return true;
            }
        }
    }

    private void sendHelp(Player p, String label) {
        p.sendMessage("§e/" + label + " add [Name] §7– NPC hier erstellen");
        p.sendMessage("§e/" + label + " remove §7– anvisierten NPC löschen");
        p.sendMessage("§e/" + label + " list §7– alle NPCs auflisten");
        p.sendMessage("§e/" + label + " respawn §7– alle neu spawnen");
        p.sendMessage("§e/" + label + " rename <Neuer Name> §7– anvisierten NPC umbenennen");
        p.sendMessage("§e/" + label + " glow <on|off> §7– anvisierten NPC glühen lassen (ohne Angabe = toggle)");
    }

    private String joinFrom(String[] arr, int startIdx) {
        StringBuilder sb = new StringBuilder();
        for (int i = startIdx; i < arr.length; i++) {
            if (i > startIdx) sb.append(' ');
            sb.append(arr[i]);
        }
        return sb.toString();
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] args) {
        List<String> out = new ArrayList<>();
        if (s instanceof Player player && !hasAccess(player)) return out;
        if (args.length == 1) {
            String pfx = args[0].toLowerCase(Locale.ROOT);
            for (String k : new String[]{"add","remove","list","respawn","rename","glow"}) {
                if (k.startsWith(pfx)) out.add(k);
            }
            return out;
        }
        if (args.length == 2 && "glow".equalsIgnoreCase(args[0])) {
            if ("on".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("on");
            if ("off".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("off");
            return out;
        }
        return out;
    }

    private boolean hasAccess(Player player) {
        return player.isOp()
                || player.hasPermission("vetroforge.admin")
                || player.hasPermission("vetroforge.crates.admin");
    }
}
