package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.crates.CrateHistoryService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.UUID;

public final class CrateHistoryCommand implements CommandExecutor {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm")
            .withZone(ZoneId.systemDefault());

    private final FactionsPlugin plugin;

    public CrateHistoryCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.crates.history")) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " <spieler> [anzahl]");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        UUID uuid = target.getUniqueId();
        int limit = args.length >= 2 ? parse(args[1], 10) : 10;
        CrateHistoryService history = plugin.getKeyCrateManager().getHistory();

        sender.sendMessage(ChatColor.DARK_GRAY + "—— " + ChatColor.GOLD + "Crate History: "
                + ChatColor.WHITE + (target.getName() == null ? uuid : target.getName()) + ChatColor.DARK_GRAY + " ——");
        var entries = history.entries(uuid, Math.max(1, Math.min(40, limit)));
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Keine Crate-Gewinne gefunden.");
            return true;
        }
        for (var entry : entries) {
            sender.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + TIME.format(Instant.ofEpochMilli(entry.time()))
                    + ChatColor.DARK_GRAY + " | " + ChatColor.GOLD + entry.crateId()
                    + ChatColor.DARK_GRAY + " -> " + ChatColor.WHITE + entry.amount() + "x " + entry.itemName());
        }
        return true;
    }

    private int parse(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }
}
