// src/main/java/com/vetroforge/commands/TopCoinsCommand.java
package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Comparator;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class TopCoinsCommand implements CommandExecutor {

    private final FactionsPlugin plugin;

    public TopCoinsCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage(UiDecor.playersOnly());
            return true;
        }

        EconomyManager eco = plugin.getEconomyManager();
        if (eco == null) {
            p.sendMessage("§cEconomy ist nicht verfügbar.");
            return true;
        }

        p.sendMessage("§6§lTop 10 Coins:");

        final int[] rank = {1};
        eco.getAllBalances().entrySet().stream()
                .sorted(Map.Entry.<UUID, Double>comparingByValue(Comparator.reverseOrder()))
                .limit(10)
                .forEach(entry -> {
                    OfflinePlayer op = Bukkit.getOfflinePlayer(entry.getKey());
                    String name = op.getName() != null ? op.getName() : entry.getKey().toString();
                    String amount = format(entry.getValue());
                    p.sendMessage("§e#" + rank[0] + " §7" + name + " §8» §6" + amount + " Coins");
                    rank[0]++;
                });

        return true;
    }

    private String format(double value) {
        if (Math.floor(value) == value) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.US, "%.2f", value);
    }
}