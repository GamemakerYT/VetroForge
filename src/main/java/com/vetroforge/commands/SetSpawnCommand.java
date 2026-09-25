package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.UiDecor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public class SetSpawnCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public SetSpawnCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!s.hasPermission("vetroforge.spawn.set")) {
            s.sendMessage(UiDecor.err() + "Keine Rechte.");
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage(UiDecor.err() + "Nur Spieler.");
            return true;
        }

        if (args.length > 0) {
            String sub = args[0].toLowerCase(Locale.ROOT);
            switch (sub) {
                case "pos1" -> {
                    plugin.getWarzoneRegionManager().setSpawnSafePos1(p);
                    p.sendMessage(UiDecor.pfx() + "§aSpawn-Region Pos1 gesetzt. §7Alles außerhalb der Box ist Warzone.");
                    return true;
                }
                case "pos2" -> {
                    plugin.getWarzoneRegionManager().setSpawnSafePos2(p);
                    p.sendMessage(UiDecor.pfx() + "§aSpawn-Region Pos2 gesetzt. §7Alles außerhalb der Box ist Warzone.");
                    return true;
                }
                case "info" -> {
                    var safe = plugin.getWarzoneRegionManager().getSpawnSafeCuboid();
                    var fallback = plugin.getLobbyManager().getSpawn();
                    p.sendMessage(UiDecor.pfx() + "§7Aktuelle Flag: §f"
                            + plugin.getWarzoneRegionManager().flagAt(p.getLocation()));
                    if (safe == null) {
                        p.sendMessage(UiDecor.err() + "Spawn-Box ist nicht vollständig gesetzt.");
                        if (fallback != null && fallback.getWorld() != null) {
                            double radius = plugin.getConfig().getDouble("lobby.spawnRadius", 48.0D);
                            p.sendMessage(UiDecor.pfx() + "§eFallback aktiv: §f" + fallback.getWorld().getName()
                                    + " §7@ §f" + fallback.getBlockX() + " " + fallback.getBlockY() + " " + fallback.getBlockZ()
                                    + " §8(§7Radius §f" + (int) radius + "§8)");
                            p.sendMessage("§8• §7Für exakte Warzone/Spawn-Trennung nutze §e/setspawn pos1 §7und §e/setspawn pos2§7.");
                        }
                    } else {
                        p.sendMessage(UiDecor.pfx() + "§6Spawn-Region:");
                        p.sendMessage("§8• §7Welt: §f" + safe.min.getWorld().getName());
                        p.sendMessage("§8• §7Min: §f" + safe.min.getBlockX() + " " + safe.min.getBlockY() + " " + safe.min.getBlockZ());
                        p.sendMessage("§8• §7Max: §f" + safe.max.getBlockX() + " " + safe.max.getBlockY() + " " + safe.max.getBlockZ());
                        p.sendMessage("§8• §7Alles außerhalb dieser Box ist §6Warzone§7.");
                    }
                    return true;
                }
                case "clear" -> {
                    plugin.getWarzoneRegionManager().clearSpawnSafe();
                    p.sendMessage(UiDecor.pfx() + "§eSpawn-Region entfernt. Warzone nutzt wieder die normale Warzone-Konfiguration.");
                    return true;
                }
                default -> {
                    p.sendMessage(UiDecor.err() + "Nutze /setspawn, /setspawn pos1, /setspawn pos2, /setspawn info oder /setspawn clear.");
                    return true;
                }
            }
        }

        plugin.getLobbyManager().setSpawn(p.getLocation());
        p.sendMessage(UiDecor.pfx() + "§aSpawn gesetzt!");
        p.sendMessage("§8• §7Fallback-Spawnradius: §f" + (int) plugin.getConfig().getDouble("lobby.spawnRadius", 48.0D)
                + " Blöcke§7. Für saubere Flags: §e/setspawn pos1 §7und §e/setspawn pos2§7.");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vetroforge.spawn.set")) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("pos1", "pos2", "info", "clear").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
