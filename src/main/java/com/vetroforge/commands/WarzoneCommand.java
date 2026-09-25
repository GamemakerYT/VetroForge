// src/main/java/com/vetroforge/commands/WarzoneCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.warzone.WarzoneRegionManager;
import com.vetroforge.warzone.WarzoneRegionManager.Shape;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;

public class WarzoneCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public WarzoneCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage(color("&cNur Spieler.")); return true; }
        if (!p.hasPermission("vetroforge.admin")) { p.sendMessage(color("&cNo permission.")); return true; }

        WarzoneRegionManager wz = plugin.getWarzoneRegionManager();

        if (a.length == 0) {
            p.sendMessage(color("&8/") + l + color(" &7shape &f<circle|cuboid>"));
            p.sendMessage(color("&8/") + l + color(" &7center &8(aktuellen Ort setzen)"));
            p.sendMessage(color("&8/") + l + color(" &7radius &f<zahl>"));
            p.sendMessage(color("&8/") + l + color(" &7pos1 &8| &7pos2 &8(cuboid)"));
            p.sendMessage(color("&8/") + l + color(" &7commit &8(cuboid speichern)"));
            p.sendMessage(color("&8/") + l + color(" &7save &8| &7info"));
            p.sendMessage(color("&8/") + l + color(" &7combattag &f<sekunden> &8(Combat-Tag-Dauer)"));
            return true;
        }

        if (a[0].equalsIgnoreCase("shape") && a.length >= 2) {
            try {
                Shape sshape = Shape.valueOf(a[1].toUpperCase(Locale.ROOT));
                wz.setShape(sshape);
                p.sendMessage(color("&aShape gesetzt: &f" + sshape));
            } catch (Exception ex) {
                p.sendMessage(color("&cUnbekannt: &f" + a[1]));
            }
            return true;
        }

        if (a[0].equalsIgnoreCase("center")) {
            safeSetCircleCenter(wz, p);
            p.sendMessage(color("&aCenter gesetzt auf deine Position."));
            return true;
        }

        if (a[0].equalsIgnoreCase("radius") && a.length >= 2) {
            try {
                double r = Double.parseDouble(a[1]);
                wz.setCircleRadius(r);
                p.sendMessage(color("&aRadius gesetzt: &f" + r));
            } catch (Exception ex) {
                p.sendMessage(color("&cZahl erwartet."));
            }
            return true;
        }

        if (a[0].equalsIgnoreCase("pos1")) {
            safeSetPos(wz, "setPos1", p);
            p.sendMessage(color("&aPos1 gesetzt."));
            return true;
        }

        if (a[0].equalsIgnoreCase("pos2")) {
            safeSetPos(wz, "setPos2", p);
            p.sendMessage(color("&aPos2 gesetzt."));
            return true;
        }

        if (a[0].equalsIgnoreCase("commit")) {
            boolean ok = tryCommitCuboid(wz, p);
            if (ok) p.sendMessage(color("&aCuboid gespeichert."));
            else     p.sendMessage(color("&cpos1/pos2 fehlen oder unterschiedliche Welten."));
            return true;
        }

        if (a[0].equalsIgnoreCase("save")) {
            wz.saveToConfig();
            p.sendMessage(color("&aWarzone gespeichert (config.yml)."));
            return true;
        }

        if (a[0].equalsIgnoreCase("info")) {
            if (!wz.isConfigured()) { p.sendMessage(color("&cKeine Warzone konfiguriert.")); return true; }
            p.sendMessage(color("&7Shape: &f" + wz.getShape()));
            p.sendMessage(color("&7World: &f" + wz.getWorldName()));
            if (wz.getShape() == Shape.CIRCLE) {
                p.sendMessage(color("&7Center: &f" + fmt(wz.getCenter()) + " &7Radius: &f" + wz.getRadius()));
            } else {
                p.sendMessage(color("&7Cuboid: &f" + String.valueOf(wz.getCuboid())));
            }
            return true;
        }

        if (a[0].equalsIgnoreCase("combattag") && a.length >= 2) {
            try {
                int seconds = Integer.parseInt(a[1]);
                if (seconds < 1) throw new NumberFormatException();
                plugin.getConfig().set("warzone.combatTag.durationSeconds", seconds);
                plugin.saveConfig();
                p.sendMessage(color("&aCombat-Tag-Dauer gesetzt: &f" + seconds + "s"));
            } catch (NumberFormatException ex) {
                p.sendMessage(color("&cGanze Zahl >= 1 erwartet."));
            }
            return true;
        }

        p.sendMessage(color("&cUnbekannter Subcommand."));
        return true;
    }

    /* ---------- helpers ---------- */

    private boolean tryCommitCuboid(WarzoneRegionManager wz, Player p) {
        try {
            Method m = wz.getClass().getMethod("commitCuboid", Player.class);
            if (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class) {
                Object res = m.invoke(wz, p);
                return Boolean.TRUE.equals(res);
            } else {
                m.invoke(wz, p);
                // wenn Methode void ist, prüfen wir den Zustand danach
                return wz.isConfigured() && wz.getShape() == Shape.CUBOID && wz.getCuboid() != null;
            }
        } catch (NoSuchMethodException nsme) {
            // Alternative Signatur: commitCuboid() ohne Parameter
            try {
                Method m = wz.getClass().getMethod("commitCuboid");
                if (m.getReturnType() == boolean.class || m.getReturnType() == Boolean.class) {
                    Object res = m.invoke(wz);
                    return Boolean.TRUE.equals(res);
                } else {
                    m.invoke(wz);
                    return wz.isConfigured() && wz.getShape() == Shape.CUBOID && wz.getCuboid() != null;
                }
            } catch (Throwable ignored) { return false; }
        } catch (Throwable t) { return false; }
    }

    private void safeSetCircleCenter(WarzoneRegionManager wz, Player p) {
        try {
            // bevorzugt setCircleCenter(Player)
            Method m = wz.getClass().getMethod("setCircleCenter", Player.class);
            m.invoke(wz, p);
            return;
        } catch (Throwable ignored) {}
        try {
            // fallback setCircleCenter(Location)
            Method m = wz.getClass().getMethod("setCircleCenter", Location.class);
            m.invoke(wz, p.getLocation());
        } catch (Throwable ignored) {}
    }

    private void safeSetPos(WarzoneRegionManager wz, String methodName, Player p) {
        try {
            Method m = wz.getClass().getMethod(methodName, Player.class);
            m.invoke(wz, p);
            return;
        } catch (Throwable ignored) {}
        try {
            Method m = wz.getClass().getMethod(methodName, Location.class);
            m.invoke(wz, p.getLocation());
        } catch (Throwable ignored) {}
    }

    private String color(String s){ return ChatColor.translateAlternateColorCodes('&', s); }
    private String fmt(Location l){ return l==null?"-":(l.getBlockX()+","+l.getBlockY()+","+l.getBlockZ()); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String k : new String[]{"shape","center","radius","pos1","pos2","commit","save","info","combattag"}) {
                if (k.startsWith(a[0].toLowerCase(Locale.ROOT))) out.add(k);
            }
            return out;
        }
        if (a.length == 2 && "shape".equalsIgnoreCase(a[0])) {
            for (String k : new String[]{"circle","cuboid"}) if (k.startsWith(a[1].toLowerCase(Locale.ROOT))) out.add(k);
            return out;
        }
        return out;
    }
}