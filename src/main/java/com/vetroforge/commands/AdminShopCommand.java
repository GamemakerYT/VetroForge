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

public final class AdminShopCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    private final CommerceNpcService npcService;
    private final ShopAdminCommand tuningCommand;

    public AdminShopCommand(FactionsPlugin plugin, CommerceNpcService npcService) {
        this.plugin = plugin;
        this.npcService = npcService;
        this.tuningCommand = new ShopAdminCommand(plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.shop.admin")) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (args.length == 0) {
            help(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("tune")) {
            return tuningCommand.onCommand(sender, command, label, args);
        }
        if (sub.equals("gui")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Nur ingame nutzbar.");
                return true;
            }
            if (plugin.getShopAdminEditor() != null) plugin.getShopAdminEditor().open(player);
            else player.sendMessage(ChatColor.RED + "Shop-Editor ist nicht geladen.");
            return true;
        }
        if (sub.equals("set")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Nur ingame nutzbar.");
                return true;
            }
            String name = args.length >= 2 ? String.join(" ", Arrays.copyOfRange(args, 1, args.length)) : "§c§lVetroForge §fShop";
            boolean ok = npcService.set(player.getLocation(), name);
            sender.sendMessage(ok ? ChatColor.GREEN + "Shop-NPC gesetzt." : ChatColor.RED + "Shop-NPC konnte nicht gesetzt werden.");
            return true;
        }
        if (sub.equals("remove")) {
            sender.sendMessage(npcService.remove() ? ChatColor.GREEN + "Shop-NPC entfernt." : ChatColor.YELLOW + "Kein Shop-NPC gefunden.");
            return true;
        }
        if (sub.equals("spawn")) {
            npcService.spawnConfigured();
            sender.sendMessage(ChatColor.GREEN + "Shop-NPC Spawn versucht.");
            return true;
        }
        if (sub.equals("info")) {
            sender.sendMessage(npcService.info());
            return true;
        }

        help(sender, label);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("set", "remove", "spawn", "info", "tune", "gui").stream()
                    .filter(value -> value.startsWith(prefix))
                    .toList();
        }
        if (args.length >= 2 && args[0].equalsIgnoreCase("tune")) {
            return tuningCommand.onTabComplete(sender, command, alias, args);
        }
        return List.of();
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage(ChatColor.GOLD + "AdminShop");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " set [Name]" + ChatColor.GRAY + " - Shop-NPC setzen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " remove" + ChatColor.GRAY + " - Shop-NPC entfernen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " spawn" + ChatColor.GRAY + " - gespeicherten NPC neu spawnen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " info" + ChatColor.GRAY + " - NPC-Position anzeigen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " tune <buy|sell> [--apply]" + ChatColor.GRAY + " - AI-Preise tunen");
        sender.sendMessage(ChatColor.YELLOW + "/" + label + " gui" + ChatColor.GRAY + " - Shop-Items per Drag-and-drop bearbeiten");
        sender.sendMessage(ChatColor.DARK_GRAY + "Spieler nutzen nur /shop.");
    }
}
