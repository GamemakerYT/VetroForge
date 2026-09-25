package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.*;

public class CoinsCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public CoinsCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        // =====================
        // ADMIN (konsolenfähig für Tebex/Webshop)
        // =====================
        if (args.length >= 1 && isAdminSub(args[0])) {
            if (!sender.hasPermission("vetro.admin.coins")) {
                sender.sendMessage(UiDecor.noPermission());
                return true;
            }

            if (args.length < 3) {
                sender.sendMessage("§7/coins <add|remove|set> <spieler> <betrag>");
                return true;
            }

            String action = args[0].toLowerCase();
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (!target.hasPlayedBefore() && !target.isOnline()) {
                sender.sendMessage("§cSpieler wurde noch nie auf diesem Server gesehen.");
                return true;
            }

            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (Exception e) {
                sender.sendMessage("§cUngültige Zahl.");
                return true;
            }

            UUID uuid = target.getUniqueId();

            switch (action) {
                case "add" -> plugin.getCoinsManager().addCoins(uuid, amount);
                case "remove" -> plugin.getCoinsManager().removeCoins(uuid, amount);
                case "set" -> plugin.getCoinsManager().setCoins(uuid, amount);
                default -> {
                    sender.sendMessage("§7/coins <add|remove|set> <spieler> <betrag>");
                    return true;
                }
            }

            int now = plugin.getCoinsManager().getCoins(uuid);
            sender.sendMessage("§aCoins gesetzt. Neuer Stand von §e" + (target.getName() == null ? args[1] : target.getName()) + "§a: §e" + now);
            return true;
        }

        if (!(sender instanceof Player p)) {
            sender.sendMessage(UiDecor.playersOnly());
            return true;
        }

        // =====================
        // /coins
        // =====================
        if (args.length == 0) {
            int coins = plugin.getCoinsManager().getCoins(p.getUniqueId());

            if (coins == -1) {
                p.sendMessage("§7Dein Kontostand wird geladen...");
                return true;
            }

            p.sendMessage("§7Dein Kontostand: §e" + coins + " Coins");
            return true;
        }

        // =====================
        // /coins <spieler>
        // =====================
        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);

        int coins = plugin.getCoinsManager().getCoins(target.getUniqueId());

        if (coins == -1) {
            p.sendMessage("§7Coins von §e" + target.getName() + "§7: §eLädt...");
            return true;
        }

        p.sendMessage("§7Coins von §e" + target.getName() + "§7: §e" + coins);
        return true;
    }

    private boolean isAdminSub(String s) {
        return s.equalsIgnoreCase("add") ||
                s.equalsIgnoreCase("remove") ||
                s.equalsIgnoreCase("set");
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, @NotNull String @NotNull [] args) {
        return List.of();
    }
}