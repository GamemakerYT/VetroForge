// src/main/java/com/vetroforge/commands/FWorldAdminCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.nio.file.Path;
import java.util.*;

public class FWorldAdminCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    public FWorldAdminCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!sender.hasPermission("vetroforge.admin")) { sender.sendMessage(cc("&cNo permission.")); return true; }

        if (args.length == 1 && args[0].equalsIgnoreCase("list")) {
            List<String> worlds = plugin.getWorldManager().listFactionWorldsOnDisk();
            if (worlds.isEmpty()) { sender.sendMessage(cc("&7Keine Faction-Welten gefunden.")); return true; }
            sender.sendMessage(cc("&7Faction-Welten:"));
            for (String wn : worlds) {
                boolean loaded = (Bukkit.getWorld(wn) != null);
                int backups = plugin.getWorldManager().listBackupsForWorld(wn).size();
                sender.sendMessage(cc(" &8- &f"+wn+" &7loaded="+(loaded?"&atrue":"&cfalse")+"&7, backups=&f"+backups));
            }
            return true;
        }

        if (args.length < 2) { sendUsage(sender, lbl); return true; }

        String action  = args[0].toLowerCase(Locale.ROOT);
        String faction = args[1];
        String worldName = plugin.getWorldManager().worldNameForFaction(faction);

        switch (action) {
            case "create": {
                boolean ok = plugin.getWorldManager().createIfMissing(worldName);
                sender.sendMessage(cc(ok ? "&aWelt bereit: &f"+worldName : "&cErstellung fehlgeschlagen."));
                return true;
            }
            case "tp": {
                if (!(sender instanceof Player p)) { sender.sendMessage(cc("&cNur Spieler.")); return true; }
                if (plugin.getWorldManager().teleportToFactionWorld(p, worldName)) p.sendMessage(cc("&aTeleportiert nach &f"+worldName));
                else p.sendMessage(cc("&cTeleport fehlgeschlagen."));
                return true;
            }
            case "info": {
                sender.sendMessage(cc("&7" + worldName + " → " + plugin.getWorldManager().infoFactionWorld(worldName)));
                return true;
            }
            case "backup": {
                Path zip = plugin.getWorldManager().backupWorld(worldName);
                sender.sendMessage(cc(zip != null ? "&aBackup erstellt: &f" + zip.getFileName() : "&cBackup fehlgeschlagen."));
                return true;
            }
            case "regen": {
                sender.sendMessage(cc("&7Regeneriere &f" + worldName + "&7 ..."));
                boolean ok = plugin.getWorldManager().regenerateFactionWorld(worldName);
                sender.sendMessage(cc(ok ? "&aErfolgreich regeneriert." : "&cFehlgeschlagen (siehe Konsole)."));
                return true;
            }
            case "delete": {
                if (args.length < 3 || !args[2].equalsIgnoreCase("confirm")) {
                    sender.sendMessage(cc("&cDieser Schritt löscht die Welt &f" + worldName + " &cendgültig."));
                    sender.sendMessage(cc("&7Bestätigen mit: &e/" + lbl + " delete " + faction + " confirm"));
                    return true;
                }
                sender.sendMessage(cc("&7Lösche &f" + worldName + "&7 ..."));
                plugin.getWorldManager().unloadWorldSafely(worldName);
                boolean ok = plugin.getWorldManager().deleteWorldFolder(worldName);
                sender.sendMessage(cc(ok ? "&aWeltordner gelöscht." : "&cLöschen fehlgeschlagen."));
                return true;
            }
            case "restore": {
                if (args.length < 3) { sender.sendMessage(cc("&cNutze: &e/"+lbl+" restore "+faction+" <backup.zip|latest> &8(confirm)")); return true; }
                String sel = args[2];
                String fileName = sel;
                if ("latest".equalsIgnoreCase(sel)) {
                    Path latest = plugin.getWorldManager().getLatestBackup(worldName);
                    if (latest == null) { sender.sendMessage(cc("&cKein Backup gefunden.")); return true; }
                    fileName = latest.getFileName().toString();
                }
                if (args.length < 4 || !args[3].equalsIgnoreCase("confirm")) {
                    sender.sendMessage(cc("&cAchtung: &7Welt &f"+worldName+"&7 wird überschrieben."));
                    sender.sendMessage(cc("&7Bestätigen mit: &e/"+lbl+" restore "+faction+" "+sel+" confirm"));
                    return true;
                }
                sender.sendMessage(cc("&7Stelle wieder her aus &f"+fileName+"&7 ..."));
                boolean ok = plugin.getWorldManager().restoreFactionWorld(worldName, fileName);
                sender.sendMessage(cc(ok ? "&aRestore OK. &7Geladen: &f"+worldName : "&cRestore fehlgeschlagen."));
                return true;
            }
            default: sendUsage(sender, lbl); return true;
        }
    }

    private void sendUsage(CommandSender s, String l) {
        s.sendMessage(cc("&8/"+l+" &7list"));
        s.sendMessage(cc("&8/"+l+" &7create &f<faction>"));
        s.sendMessage(cc("&8/"+l+" &7tp &f<faction>"));
        s.sendMessage(cc("&8/"+l+" &7info &f<faction>"));
        s.sendMessage(cc("&8/"+l+" &7backup &f<faction>"));
        s.sendMessage(cc("&8/"+l+" &7regen &f<faction>"));
        s.sendMessage(cc("&8/"+l+" &7delete &f<faction> &8(confirm)"));
        s.sendMessage(cc("&8/"+l+" &7restore &f<faction> <backup.zip|latest> &8(confirm)"));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] A) {
        List<String> out = new ArrayList<>();
        if (A.length == 1) {
            for (String k : new String[]{"list","create","tp","info","backup","regen","delete","restore"})
                if (k.startsWith(A[0].toLowerCase(Locale.ROOT))) out.add(k);
            return out;
        }
        if (A.length == 2) {
            Set<String> names = new TreeSet<>();
            for (World w : Bukkit.getWorlds())
                if (w.getName().toLowerCase(Locale.ROOT).startsWith("faction_"))
                    names.add(w.getName().substring("faction_".length()));
            for (String wn : plugin.getWorldManager().listFactionWorldsOnDisk())
                names.add(wn.substring("faction_".length()));
            for (String n : names) if (n.startsWith(A[1].toLowerCase(Locale.ROOT))) out.add(n);
            return out;
        }
        if (A.length == 3 && "restore".equalsIgnoreCase(A[0])) {
            out.add("latest");
            String worldName = plugin.getWorldManager().worldNameForFaction(A[1]);
            for (Path p : plugin.getWorldManager().listBackupsForWorld(worldName)) {
                String fn = p.getFileName().toString();
                if (fn.toLowerCase(Locale.ROOT).startsWith(A[2].toLowerCase(Locale.ROOT))) out.add(fn);
            }
            return out;
        }
        if (A.length == 4 && ("restore".equalsIgnoreCase(A[0]) || "delete".equalsIgnoreCase(A[0]))) {
            if ("confirm".startsWith(A[3].toLowerCase(Locale.ROOT))) out.add("confirm");
            return out;
        }
        if (A.length == 3 && "delete".equalsIgnoreCase(A[0])) {
            if ("confirm".startsWith(A[2].toLowerCase(Locale.ROOT))) out.add("confirm");
            return out;
        }
        return out;
    }

    private String cc(String s){ return ChatColor.translateAlternateColorCodes('&', s); }
}