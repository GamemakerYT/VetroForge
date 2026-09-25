package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class StaffProtectCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;

    public StaffProtectCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.staff.protect")) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            List<String> entries = entries();
            sender.sendMessage(ChatColor.GOLD + "StaffProtect " + ChatColor.DARK_GRAY + "» "
                    + ChatColor.GRAY + "Geschuetzte Eintraege: " + ChatColor.WHITE + entries.size());
            if (entries.isEmpty()) {
                sender.sendMessage(ChatColor.DARK_GRAY + "-");
            } else {
                for (String entry : entries) sender.sendMessage(ChatColor.DARK_GRAY + "• " + ChatColor.WHITE + entry);
            }
            return true;
        }

        if (args.length < 2 || (!sub.equals("add") && !sub.equals("remove"))) {
            help(sender, label);
            return true;
        }

        String value = normalize(args[1]);
        List<String> entries = new ArrayList<>(entries());
        if (sub.equals("add")) {
            if (contains(entries, value)) {
                sender.sendMessage(ChatColor.YELLOW + "Ist bereits geschuetzt: " + ChatColor.WHITE + value);
                return true;
            }
            entries.add(value);
            save(entries);
            sender.sendMessage(ChatColor.GREEN + "Staff-Schutz hinzugefuegt: " + ChatColor.WHITE + value);
            return true;
        }

        boolean removed = entries.removeIf(entry -> entry.equalsIgnoreCase(value));
        save(entries);
        sender.sendMessage(removed
                ? ChatColor.GREEN + "Staff-Schutz entfernt: " + ChatColor.WHITE + value
                : ChatColor.YELLOW + "Eintrag nicht gefunden: " + ChatColor.WHITE + value);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("add", "remove", "list").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            return entries().stream()
                    .filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("add")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(player.getName());
            }
            return out;
        }
        return List.of();
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "StaffProtect");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " list" + ChatColor.GRAY + " - Schutzliste anzeigen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " add <name|uuid>" + ChatColor.GRAY + " - Eintrag schuetzen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " remove <name|uuid>" + ChatColor.GRAY + " - Eintrag entfernen");
    }

    private String normalize(String raw) {
        String value = raw == null ? "" : raw.trim();
        Player online = Bukkit.getPlayerExact(value);
        if (online != null) return online.getName();
        try {
            OfflinePlayer offline = Bukkit.getOfflinePlayer(value);
            if (offline != null && offline.getName() != null && !offline.getName().isBlank()) return offline.getName();
        } catch (Throwable ignored) {}
        return value;
    }

    private List<String> entries() {
        return plugin.getConfig().getStringList("punishments.protectedStaff");
    }

    private boolean contains(List<String> entries, String value) {
        for (String entry : entries) {
            if (entry.equalsIgnoreCase(value)) return true;
        }
        return false;
    }

    private void save(List<String> entries) {
        plugin.getConfig().set("punishments.protectedStaff", entries);
        plugin.saveConfig();
    }
}
