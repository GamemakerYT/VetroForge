package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.UiDecor;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class AdminCoinsCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public AdminCoinsCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!sender.hasPermission("vetro.admin.coins")) {
            sender.sendMessage(UiDecor.noPermission());
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage("§7/admincoins <set|give> <spieler> <betrag>");
            return true;
        }

        String action = args[0].toLowerCase(Locale.ROOT);
        if (!action.equals("set") && !action.equals("give")) {
            sender.sendMessage("§7/admincoins <set|give> <spieler> <betrag>");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            sender.sendMessage("§cSpieler wurde noch nie auf diesem Server gesehen.");
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (NumberFormatException e) {
            sender.sendMessage("§cUngültige Zahl.");
            return true;
        }

        UUID uuid = target.getUniqueId();
        if (action.equals("set")) {
            plugin.getCoinsManager().setCoins(uuid, amount);
        } else {
            plugin.getCoinsManager().addCoins(uuid, amount);
        }

        int now = plugin.getCoinsManager().getCoins(uuid);
        String name = target.getName() == null ? args[1] : target.getName();
        sender.sendMessage("§aCoins " + (action.equals("set") ? "gesetzt" : "gegeben") + ". Neuer Stand von §e" + name + "§a: §e" + now);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String opt : new String[]{"set", "give"}) {
                if (opt.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(opt);
            }
            return out;
        }
        if (args.length == 2) {
            String pref = args[1].toLowerCase(Locale.ROOT);
            for (var p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pref)) out.add(p.getName());
            }
            return out;
        }
        return out;
    }
}
