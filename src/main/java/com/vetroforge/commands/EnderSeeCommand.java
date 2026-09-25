package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

public final class EnderSeeCommand implements CommandExecutor, TabCompleter, Listener {

    private final Set<UUID> readOnlyViewers = new HashSet<>();

    public EnderSeeCommand(FactionsPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player viewer)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!viewer.hasPermission("vetroforge.staff.endersee")) {
            viewer.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (args.length < 1) {
            viewer.sendMessage(ChatColor.RED + "Nutze: /endersee <spieler>");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            viewer.sendMessage(ChatColor.RED + "Spieler nicht online.");
            return true;
        }
        if (!viewer.hasPermission("vetroforge.admin")) {
            readOnlyViewers.add(viewer.getUniqueId());
            viewer.sendMessage(ChatColor.YELLOW + "Nur Ansicht. Admins können Items entnehmen.");
        } else {
            readOnlyViewers.remove(viewer.getUniqueId());
            viewer.sendMessage(ChatColor.GREEN + "Admin-Modus: Enderchest kann bearbeitet werden.");
        }
        viewer.openInventory(target.getEnderChest());
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (readOnlyViewers.contains(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (readOnlyViewers.contains(event.getWhoClicked().getUniqueId())) event.setCancelled(true);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        readOnlyViewers.remove(event.getPlayer().getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        if (args.length != 1) return Collections.emptyList();
        String prefix = args[0].toLowerCase(Locale.ROOT);
        List<String> out = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(player.getName());
        }
        return out;
    }
}
