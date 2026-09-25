package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

public final class BanListCommand implements CommandExecutor {
    private final FactionsPlugin plugin;
    private final SimpleDateFormat format = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public BanListCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.staff.banlist")) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        int page = args.length >= 1 ? safeInt(args[0], 1) : 1;
        List<BanEntry> entries = new ArrayList<>(Bukkit.getBanList(BanList.Type.NAME).getBanEntries());
        entries.sort(Comparator.comparing(BanEntry::getTarget, String.CASE_INSENSITIVE_ORDER));

        int pageSize = 8;
        int maxPage = Math.max(1, (int) Math.ceil(entries.size() / (double) pageSize));
        page = Math.max(1, Math.min(maxPage, page));
        int from = Math.min(entries.size(), (page - 1) * pageSize);
        int to = Math.min(entries.size(), from + pageSize);

        sender.sendMessage(ChatColor.DARK_RED + "———— " + ChatColor.RED + "VetroForge Banlist"
                + ChatColor.DARK_GRAY + " • " + ChatColor.GRAY + page + "/" + maxPage + ChatColor.DARK_RED + " ————");
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Keine gebannten Spieler.");
            return true;
        }
        for (BanEntry entry : entries.subList(from, to)) {
            String until = entry.getExpiration() == null ? "Permanent" : format.format(entry.getExpiration());
            sender.sendMessage(ChatColor.DARK_GRAY + "• " + ChatColor.WHITE + entry.getTarget()
                    + ChatColor.GRAY + " | Grund: " + ChatColor.RED + safe(entry.getReason(), "-")
                    + ChatColor.GRAY + " | Bis: " + ChatColor.YELLOW + until);
        }
        return true;
    }

    private int safeInt(String raw, int fallback) {
        try { return Integer.parseInt(raw); } catch (Exception ignored) { return fallback; }
    }

    private String safe(String value, String fallback) {
        return value == null || value.isBlank() ? fallback : value;
    }
}
