package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.StaffLabelUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public class UnmuteCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;

    public UnmuteCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.staff.unmute")) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (args.length < 1) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " <Spieler> [Grund...]");
            return true;
        }

        OfflinePlayer target = Bukkit.getOfflinePlayer(args[0]);
        if (target == null || target.getUniqueId() == null) {
            sender.sendMessage(ChatColor.RED + "Unbekannter Spieler.");
            return true;
        }

        String reason = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "Unmute";
        boolean wasMuted = plugin.getPunishmentManager() != null && plugin.getPunishmentManager().isMuted(target.getUniqueId());
        if (plugin.getPunishmentManager() != null) {
            plugin.getPunishmentManager().unmute(target.getUniqueId());
        }

        try {
            var hist = plugin.getPunishmentHistory();
            if (hist != null) {
                hist.logUnban(target.getUniqueId(), "UNMUTE: " + reason,
                        sender instanceof Player player ? player.getUniqueId() : null,
                        System.currentTimeMillis());
            }
        } catch (Throwable ignored) {}

        List<String> lines = List.of(
                "&7Von: &f" + StaffLabelUtil.labelPlain(sender),
                "&7Spieler: &f" + (target.getName() != null ? target.getName() : target.getUniqueId()),
                "&7Grund: &f" + reason,
                wasMuted ? "&aMute entfernt." : "&eSpieler war nicht gemutet."
        );
        try { plugin.getPunishmentManager().announce("Unmute", lines, true); } catch (Throwable ignored) {}
        sender.sendMessage(ChatColor.GREEN + "Unmute ausgefuehrt: " + ChatColor.WHITE
                + (target.getName() != null ? target.getName() : target.getUniqueId()));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) {
                    out.add(player.getName());
                }
            }
        }
        return out;
    }
}
