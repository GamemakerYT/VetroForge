package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.commerce.CommerceNpcService;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;

public final class AdminAhCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    private final CommerceNpcService npcService;

    public AdminAhCommand(FactionsPlugin plugin, CommerceNpcService npcService) {
        this.plugin = plugin;
        this.npcService = npcService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.auction.admin")) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("set")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Nur ingame nutzbar.");
                return true;
            }
            String name = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "§6§lAuktionshaus";
            boolean ok = npcService.set(player.getLocation(), name);
            sender.sendMessage(ok ? ChatColor.GREEN + "Auktionshaus-NPC gesetzt." : ChatColor.RED + "Auktionshaus-NPC konnte nicht gesetzt werden.");
            return true;
        }
        if (sub.equals("remove")) {
            sender.sendMessage(npcService.remove() ? ChatColor.GREEN + "Auktionshaus-NPC entfernt." : ChatColor.YELLOW + "Kein Auktionshaus-NPC gefunden.");
            return true;
        }
        if (sub.equals("spawn")) {
            npcService.spawnConfigured();
            sender.sendMessage(ChatColor.GREEN + "Auktionshaus-NPC Spawn versucht.");
            return true;
        }
        if (sub.equals("info")) {
            sender.sendMessage(npcService.info());
            return true;
        }
        if (sub.equals("open") || sub.equals("gui")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Nur ingame nutzbar.");
                return true;
            }
            if (plugin.getAuctionHouseManager() != null) {
                if (sub.equals("gui")) plugin.getAuctionHouseManager().openAdmin(player);
                else plugin.getAuctionHouseManager().open(player);
            }
            else player.sendMessage(ChatColor.RED + "Auktionshaus ist nicht geladen.");
            return true;
        }

        help(sender, label);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("set", "remove", "spawn", "info", "open", "gui").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        return List.of();
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "AdminAH");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " set [Name]" + ChatColor.GRAY + " - AH-NPC setzen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " remove" + ChatColor.GRAY + " - AH-NPC entfernen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " spawn" + ChatColor.GRAY + " - gespeicherten NPC neu spawnen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " info" + ChatColor.GRAY + " - NPC-Position anzeigen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " open" + ChatColor.GRAY + " - AH testen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " gui" + ChatColor.GRAY + " - Admin-GUI öffnen");
        sender.sendMessage(ChatColor.DARK_GRAY + "Spieler nutzen /ah oder den NPC.");
    }
}
