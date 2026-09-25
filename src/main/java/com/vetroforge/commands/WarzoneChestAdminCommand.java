package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.ChestSpawnManager;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class WarzoneChestAdminCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;

    public WarzoneChestAdminCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    private String cc(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private int parse(String s, int def) {
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return def;
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!sender.hasPermission("vetroforge.admin")) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        ChestSpawnManager m = plugin.getChestSpawnManager();
        if (m == null) {
            sender.sendMessage(ChatColor.RED + "ChestSpawnManager nicht aktiv.");
            return true;
        }

        if (a.length == 0) {
            sender.sendMessage(cc("&e/" + label + " spawn [anzahl] &7- spawnt sofort Kisten"));
            sender.sendMessage(cc("&e/" + label + " start &7- Spawn-Timer starten"));
            sender.sendMessage(cc("&e/" + label + " stop &7- Spawn-Timer stoppen"));
            sender.sendMessage(cc("&e/" + label + " restart &7- Timer neu starten"));
            sender.sendMessage(cc("&e/" + label + " clear &7- alle aktiven Kisten entfernen"));
            sender.sendMessage(cc("&e/" + label + " reload &7- Config/Spawnpunkte neu laden & Timer neu starten"));
            sender.sendMessage(cc("&7Spawnpunkte setzt du mit &f/chest set <id>&7."));
            return true;
        }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "spawn" -> {
                int n = (a.length >= 2) ? Math.max(1, parse(a[1], 1)) : 1;
                int spawned = m.forceSpawn(n);
                sender.sendMessage(spawned > 0
                        ? cc("&aGespawnt: &f" + spawned + " &7Kiste(n).")
                        : cc("&cKeine Kiste gespawnt (alle Plätze belegt oder keine Spawnpunkte)."));
                return true;
            }
            case "start" -> {
                m.start();
                sender.sendMessage(cc("&aSpawn-Timer gestartet."));
                return true;
            }
            case "stop" -> {
                m.stop();
                sender.sendMessage(cc("&eSpawn-Timer gestoppt."));
                return true;
            }
            case "restart" -> {
                m.restart();
                sender.sendMessage(cc("&aSpawn-Timer neu gestartet."));
                return true;
            }
            case "clear" -> {
                int removed = m.clearActive();
                sender.sendMessage(cc("&eEntfernt: &f" + removed + " &7aktive Kiste(n)."));
                return true;
            }
            case "reload" -> {
                m.reload();
                m.restart();
                sender.sendMessage(cc("&aKonfiguration & Spawnpunkte neu geladen, Timer neu gestartet."));
                return true;
            }
            case "status" -> {
                sender.sendMessage(cc("&6Warzone-Chests &8» &7Spawnpunkte: &f" + m.list().size()
                        + " &8| &7aktiv: &f" + m.activeCount()));
                sender.sendMessage(cc("&7Spawnpunkte: &f/chest list"));
                return true;
            }
            default -> {
                sender.sendMessage(cc("&cUnbekannter Unterbefehl. Nutze &e/" + label));
                return true;
            }
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] A) {
        List<String> out = new ArrayList<>();
        if (A.length == 1) {
            String p = A[0].toLowerCase(Locale.ROOT);
            for (String k : new String[]{"spawn", "start", "stop", "restart", "clear", "reload", "status"})
                if (k.startsWith(p)) out.add(k);
        } else if (A.length == 2 && A[0].equalsIgnoreCase("spawn")) {
            out.add("1");
            out.add("3");
            out.add("5");
        }
        return out;
    }
}
