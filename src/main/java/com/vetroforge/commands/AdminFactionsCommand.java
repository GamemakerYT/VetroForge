package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.List;
import java.util.Locale;

public final class AdminFactionsCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;

    public AdminFactionsCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.factions.admin")) return message(sender, UiDecor.noPermission());
        if (args.length == 0 || args[0].equalsIgnoreCase("list")) {
            sender.sendMessage("§6Factions Admin §8» §7" + plugin.getFactionManager().getAllFactionNames().size() + " Factions");
            plugin.getFactionManager().getAllFactionNames().stream().sorted().forEach(name ->
                    sender.sendMessage("§8• §f" + name + " §8» §7Member: §f" + plugin.getFactionManager().getMembers(name).size()
                            + " §8• §7Fraktionsmacht: §6" + plugin.getFactionUpgradeManager().getElo(name)));
            return true;
        }
        if (args[0].equalsIgnoreCase("delete") && args.length >= 2) {
            boolean removed = plugin.getFactionManager().disband(args[1]);
            if (removed) {
                save();
                plugin.getWorldManager().archiveAndDeleteFactionWorld(args[1]);
            }
            return message(sender, removed ? "§aFaction gelöscht." : "§cFaction nicht gefunden.");
        }
        if (args[0].equalsIgnoreCase("remove") && args.length >= 2) {
            boolean removed = plugin.getFactionManager().removeMember(Bukkit.getOfflinePlayer(args[1]).getUniqueId());
            save();
            return message(sender, removed ? "§aMitglied entfernt." : "§cSpieler ist in keiner Faction.");
        }
        if (args[0].equalsIgnoreCase("elo") && args.length >= 4) {
            try {
                int amount = Integer.parseInt(args[3]);
                if (args[1].equalsIgnoreCase("add")) plugin.getFactionUpgradeManager().addElo(args[2], amount);
                else if (args[1].equalsIgnoreCase("set")) plugin.getFactionUpgradeManager().setElo(args[2], amount);
                else return help(sender, label);
                return message(sender, "§aFraktionsmacht aktualisiert: §f" + args[2] + " §8» §6" + plugin.getFactionUpgradeManager().getElo(args[2]));
            } catch (NumberFormatException ignored) { return message(sender, "§cUngültige Zahl."); }
        }
        return help(sender, label);
    }

    private boolean help(CommandSender sender, String label) {
        return message(sender, "§7/" + label + " <list|delete <faction>|remove <player>|elo <add|set> <faction> <amount>>");
    }

    private boolean message(CommandSender sender, String text) { sender.sendMessage("§6Factions Admin §8» " + text); return true; }
    private void save() { if (plugin.getPersistenceManager() != null) plugin.getPersistenceManager().saveAll(); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vetroforge.factions.admin")) return List.of();
        if (args.length == 1) return filter(List.of("list", "delete", "remove", "elo"), args[0]);
        if (args.length == 2 && args[0].equalsIgnoreCase("elo")) return filter(List.of("add", "set"), args[1]);
        if ((args.length == 2 && args[0].equalsIgnoreCase("delete")) || (args.length == 3 && args[0].equalsIgnoreCase("elo")))
            return filter(plugin.getFactionManager().getAllFactionNames(), args[args.length - 1]);
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
