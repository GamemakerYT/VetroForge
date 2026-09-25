package com.vetroforge.lootdrop;

import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class AdminLootDropCommand implements CommandExecutor, TabCompleter {

    private static final String PERMISSION = "vetroforge.admin";

    private final FactionsPlugin plugin;

    public AdminLootDropCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission(PERMISSION)) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }

        if (args.length == 0) {
            help(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "pos1", "pos2" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage(ChatColor.RED + "Nur ingame.");
                    return true;
                }
                savePosition(sub, player.getLocation());
                sender.sendMessage(ChatColor.GREEN + "Lootdrop-" + sub + " gesetzt: "
                        + ChatColor.WHITE + positionText(sub));
                sender.sendMessage(ChatColor.GRAY + "Timed Lootdrops spawnen jetzt nur noch innerhalb der gesetzten Box.");
                return true;
            }
            case "info", "list" -> {
                sender.sendMessage(ChatColor.GOLD + "Lootdrop Spawn-Region");
                sender.sendMessage(ChatColor.GRAY + "Pos1: " + ChatColor.WHITE + positionText("pos1"));
                sender.sendMessage(ChatColor.GRAY + "Pos2: " + ChatColor.WHITE + positionText("pos2"));
                sender.sendMessage(ChatColor.GRAY + "Aktiv: " + (isComplete() ? ChatColor.GREEN + "ja" : ChatColor.RED + "nein"));
                return true;
            }
            case "clear", "reset" -> {
                plugin.getConfig().set("lootdrop.timed.region", null);
                plugin.saveConfig();
                sender.sendMessage(ChatColor.YELLOW + "Lootdrop-Region entfernt. Timer nutzt wieder den Radius-Fallback.");
                return true;
            }
            default -> {
                help(sender, label);
                return true;
            }
        }
    }

    private void savePosition(String id, Location location) {
        String base = "lootdrop.timed.region";
        plugin.getConfig().set(base + ".enabled", true);
        plugin.getConfig().set(base + ".world", location.getWorld().getName());
        plugin.getConfig().set(base + "." + id + ".x", location.getBlockX());
        plugin.getConfig().set(base + "." + id + ".y", location.getBlockY());
        plugin.getConfig().set(base + "." + id + ".z", location.getBlockZ());
        plugin.saveConfig();
    }

    private boolean isComplete() {
        String base = "lootdrop.timed.region";
        return plugin.getConfig().getBoolean(base + ".enabled", false)
                && plugin.getConfig().isConfigurationSection(base + ".pos1")
                && plugin.getConfig().isConfigurationSection(base + ".pos2");
    }

    private String positionText(String id) {
        String base = "lootdrop.timed.region";
        String world = plugin.getConfig().getString(base + ".world", "-");
        if (!plugin.getConfig().isConfigurationSection(base + "." + id)) return "nicht gesetzt";
        return world + " "
                + plugin.getConfig().getInt(base + "." + id + ".x") + " "
                + plugin.getConfig().getInt(base + "." + id + ".y") + " "
                + plugin.getConfig().getInt(base + "." + id + ".z");
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " pos1" + ChatColor.GRAY + " - erste Ecke setzen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " pos2" + ChatColor.GRAY + " - zweite Ecke setzen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " info" + ChatColor.GRAY + " - Region anzeigen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " clear" + ChatColor.GRAY + " - Region entfernen");
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission(PERMISSION)) return List.of();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("pos1", "pos2", "info", "list", "clear", "reset").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }
}
