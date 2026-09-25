// =======================================================
// File: src/main/java/com/vetroforge/commands/ChestCommand.java
// =======================================================
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.ChestSpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

public class ChestCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final ChestSpawnManager manager;

    public ChestCommand(FactionsPlugin plugin, ChestSpawnManager manager) {
        this.plugin = plugin;
        this.manager = manager;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player)) { s.sendMessage("Ingame nutzen."); return true; }
        Player p = (Player) s;
        if (!p.hasPermission("vetroforge.chest.admin")) { p.sendMessage(ChatColor.RED + "Keine Rechte."); return true; }

        if (a.length == 0) {
            sendHelp(p); return true;
        }
        String sub = a[0].toLowerCase(Locale.ROOT);

        if (sub.equals("set")) {
            if (a.length < 2) {
                p.sendMessage(ChatColor.RED + "Nutze /chest set <id> [warzone|flag_capture]");
                return true;
            }
            ChestSpawnManager.Category cat = resolveCategory(a, 2);
            Location lct = p.getLocation().getBlock().getLocation();
            if (!requireWarzone(p, lct)) return true;

            manager.setSpawn(a[1], lct, cat);
            p.sendMessage(ChatColor.GREEN + "Chest-Spawnpunkt " + ChatColor.WHITE + a[1]
                    + ChatColor.GREEN + " gesetzt (" + cat + "). "
                    + ChatColor.GRAY + "Respawn: 2 Minuten nach Freiwerden.");
            return true;
        }

        if (sub.equals("add")) {
            ChestSpawnManager.Category cat = resolveCategory(a, 1);
            Location lct = p.getLocation().getBlock().getLocation();
            if (!requireWarzone(p, lct)) return true;

            boolean ok = manager.addSpawn(lct, cat);
            p.sendMessage(ok ? ChatColor.GREEN + "Spawnpunkt gesetzt ("+cat+")"
                    : ChatColor.RED + "Konnte Spawnpunkt nicht setzen.");
            return true;
        }

        if (sub.equals("remove")) {
            if (a.length >= 2 && !isNumber(a[1])) {
                boolean ok = manager.removeById(a[1]);
                p.sendMessage(ok ? ChatColor.GREEN + "Spawnpunkt " + ChatColor.WHITE + a[1] + ChatColor.GREEN + " entfernt."
                        : ChatColor.RED + "Kein Spawnpunkt mit ID '" + a[1] + "' gefunden.");
                return true;
            }

            double radius = 5.0;
            if (a.length >= 2) {
                try { radius = Math.max(1.0, Double.parseDouble(a[1])); } catch (Throwable ignored) {}
            }
            boolean ok = manager.removeNearest(p.getLocation(), radius);
            p.sendMessage(ok ? ChatColor.GREEN + "Nächsten Spawnpunkt entfernt."
                    : ChatColor.RED + "Kein Spawnpunkt in Reichweite.");
            return true;
        }

        if (sub.equals("tp") || sub.equals("teleport")) {
            if (a.length < 2) {
                p.sendMessage(ChatColor.RED + "Nutze /chest tp <id>");
                return true;
            }
            ChestSpawnManager.SpawnPoint point = manager.findSpawn(a[1]);
            Location location = point == null ? null : point.toLocation();
            if (location == null) {
                p.sendMessage(ChatColor.RED + "Spawnpunkt nicht gefunden oder Welt nicht geladen.");
                return true;
            }
            p.teleport(location.clone().add(0.5, 0.2, 0.5));
            p.sendMessage(ChatColor.GREEN + "Teleportiert zu Chest-Punkt " + ChatColor.WHITE + point.id + ChatColor.GREEN + ".");
            return true;
        }

        if (sub.equals("repair")) {
            if (a.length < 2) {
                p.sendMessage(ChatColor.RED + "Nutze /chest repair <id>");
                return true;
            }
            boolean ok = manager.repairSpawn(a[1]);
            p.sendMessage(ok ? ChatColor.GREEN + "Chest-Punkt repariert und einen sicheren Block höher gesetzt."
                    : ChatColor.YELLOW + "Nichts repariert. Punkt ist entweder OK, nicht in der Warzone oder nicht reparierbar.");
            return true;
        }

        if (sub.equals("doctor")) {
            boolean fix = a.length >= 2 && (a[1].equalsIgnoreCase("fix") || a[1].equalsIgnoreCase("repair"));
            if (fix) {
                int fixed = manager.repairAllSpawnPoints();
                p.sendMessage(ChatColor.GREEN + "Chest-Doctor Fix abgeschlossen. Repariert: " + fixed);
            }
            sendDoctor(p);
            return true;
        }

        if (sub.equals("show")) {
            showPoints(p);
            return true;
        }

        if (sub.equals("list")) {
            java.util.List<ChestSpawnManager.SpawnPoint> all = manager.list();
            p.sendMessage(net.kyori.adventure.text.Component.text(" "));
            p.sendMessage(com.vetroforge.util.ChatBoxUtil.boxTop());
            p.sendMessage(com.vetroforge.util.ChatBoxUtil.center(legacy("§6§lChest-Spawnpunkte  §7" + all.size())));
            p.sendMessage(com.vetroforge.util.ChatBoxUtil.divider());
            if (all.isEmpty()) {
                p.sendMessage(com.vetroforge.util.ChatBoxUtil.center(legacy("§8Keine gesetzt.")));
            } else {
                for (ChestSpawnManager.SpawnPoint sp : all) {
                    p.sendMessage(com.vetroforge.util.ChatBoxUtil.center(legacy(ChatColor.GRAY + "- " + ChatColor.WHITE + sp.id + ChatColor.GRAY
                            + ": " + sp.world + " @ " + sp.x + "," + sp.y + "," + sp.z + " [" + sp.cat + "]")));
                }
            }
            p.sendMessage(com.vetroforge.util.ChatBoxUtil.boxBottom());
            return true;
        }

        if (sub.equals("force")) {
            int count = 1;
            if (a.length >= 2) {
                try { count = Math.max(1, Integer.parseInt(a[1])); } catch (Throwable ignored) {}
            }
            int spawned = manager.forceSpawn(count);
            p.sendMessage(ChatColor.GREEN + "Forciert gespawnt: " + spawned);
            return true;
        }

        sendHelp(p);
        return true;
    }

    private void sendHelp(Player p) {
        p.sendMessage(net.kyori.adventure.text.Component.text(" "));
        p.sendMessage(com.vetroforge.util.ChatBoxUtil.boxTop());
        p.sendMessage(com.vetroforge.util.ChatBoxUtil.center(legacy("§6§lChests")));
        p.sendMessage(com.vetroforge.util.ChatBoxUtil.divider());
        for (String line : new String[]{
                "/chest set <id> [warzone|flag_capture]",
                "/chest add [warzone|flag_capture]",
                "/chest remove <id|radius>",
                "/chest tp <id>",
                "/chest repair <id>",
                "/chest doctor [fix]",
                "/chest show",
                "/chest list",
                "/chest force [anzahl]"
        }) {
            p.sendMessage(com.vetroforge.util.ChatBoxUtil.center(legacy(ChatColor.YELLOW + line)));
        }
        p.sendMessage(com.vetroforge.util.ChatBoxUtil.boxBottom());
    }

    private net.kyori.adventure.text.Component legacy(String text) {
        return net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer.legacySection().deserialize(text);
    }

    private void sendDoctor(Player p) {
        java.util.List<ChestSpawnManager.DoctorEntry> entries = manager.doctor();
        long ok = entries.stream().filter(ChestSpawnManager.DoctorEntry::ok).count();
        p.sendMessage(ChatColor.DARK_AQUA + "VetroForge " + ChatColor.WHITE + "• Chest Doctor "
                + ChatColor.GRAY + "(" + ok + "/" + entries.size() + " OK)");
        if (entries.isEmpty()) {
            p.sendMessage(ChatColor.YELLOW + "Keine Chest-Punkte gesetzt. Nutze /chest add warzone.");
            return;
        }
        for (ChestSpawnManager.DoctorEntry entry : entries) {
            ChatColor color = entry.ok() ? ChatColor.GREEN : ChatColor.RED;
            ChestSpawnManager.SpawnPoint sp = entry.point;
            p.sendMessage(color + "● " + ChatColor.WHITE + sp.id + ChatColor.GRAY
                    + " " + sp.world + " " + sp.x + " " + sp.y + " " + sp.z
                    + " " + ChatColor.DARK_GRAY + "[" + sp.cat + "] "
                    + (entry.ok() ? ChatColor.GREEN + "OK" : ChatColor.RED + entry.issue));
        }
        p.sendMessage(ChatColor.GRAY + "Fix: " + ChatColor.YELLOW + "/chest doctor fix"
                + ChatColor.GRAY + " oder einzeln " + ChatColor.YELLOW + "/chest repair <id>");
    }

    private void showPoints(Player p) {
        int shown = 0;
        for (ChestSpawnManager.SpawnPoint point : manager.list()) {
            Location location = point.toLocation();
            if (location == null || location.getWorld() == null || !location.getWorld().equals(p.getWorld())) continue;
            Location center = location.clone().add(0.5, 0.1, 0.5);
            for (int i = 0; i < 24; i++) {
                double angle = (Math.PI * 2 * i) / 24.0;
                location.getWorld().spawnParticle(
                        org.bukkit.Particle.DUST,
                        center.clone().add(Math.cos(angle) * 0.65, 0, Math.sin(angle) * 0.65),
                        1,
                        0, 0, 0, 0,
                        new org.bukkit.Particle.DustOptions(org.bukkit.Color.fromRGB(54, 168, 255), 1.25f)
                );
            }
            location.getWorld().spawnParticle(org.bukkit.Particle.END_ROD, center.clone().add(0, 1.0, 0), 5, 0.2, 0.35, 0.2, 0.02);
            shown++;
        }
        p.sendMessage(ChatColor.AQUA + "Chest-Punkte sichtbar gemacht: " + shown + ChatColor.GRAY + " (nur geladene Punkte in deiner Welt).");
    }

    private ChestSpawnManager.Category resolveCategory(String[] a, int idx) {
        if (a.length <= idx) return ChestSpawnManager.Category.WARZONE;
        try { return ChestSpawnManager.Category.valueOf(a[idx].toUpperCase(Locale.ROOT)); }
        catch (Throwable ignored) { return ChestSpawnManager.Category.WARZONE; }
    }

    private boolean requireWarzone(Player p, Location lct) {
        if (plugin.getWarzoneRegionManager() != null && !plugin.getWarzoneRegionManager().isWarzone(lct)) {
            p.sendMessage(ChatColor.RED + "Dieser Chest-Spawnpunkt muss außerhalb des Spawn-Bereichs in der Warzone liegen.");
            return false;
        }
        return true;
    }

    private boolean isNumber(String value) {
        try {
            Double.parseDouble(value);
            return true;
        } catch (Throwable ignored) {
            return false;
        }
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player) || !s.hasPermission("vetroforge.chest.admin")) return java.util.Collections.<String>emptyList();
        if (a.length == 1) {
            return StringUtil.copyPartialMatches(a[0],
                    java.util.Arrays.asList("set","add","remove","tp","repair","doctor","show","list","force"),
                    new java.util.ArrayList<String>());
        }
        if (a.length == 2 && (a[0].equalsIgnoreCase("remove") || a[0].equalsIgnoreCase("tp") || a[0].equalsIgnoreCase("repair"))) {
            java.util.List<String> ids = manager.list().stream().map(point -> point.id).toList();
            return StringUtil.copyPartialMatches(a[1], ids, new java.util.ArrayList<String>());
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("doctor")) {
            return StringUtil.copyPartialMatches(a[1],
                    java.util.Arrays.asList("fix"),
                    new java.util.ArrayList<String>());
        }
        if (a.length == 2 && a[0].equalsIgnoreCase("add")) {
            return StringUtil.copyPartialMatches(a[1],
                    java.util.Arrays.asList("warzone","flag_capture"),
                    new java.util.ArrayList<String>());
        }
        if (a.length == 3 && a[0].equalsIgnoreCase("set")) {
            return StringUtil.copyPartialMatches(a[2],
                    java.util.Arrays.asList("warzone","flag_capture"),
                    new java.util.ArrayList<String>());
        }
        return java.util.Collections.<String>emptyList();
    }
}
