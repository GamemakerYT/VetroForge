package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class TeamChatCommand implements CommandExecutor, Listener {
    private final FactionsPlugin plugin;
    private final Set<UUID> toggled = new HashSet<>();

    public TeamChatCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.staff.teamchat")) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (args.length == 0 && sender instanceof Player player) {
            boolean now = toggled.add(player.getUniqueId());
            if (!now) toggled.remove(player.getUniqueId());
            player.sendMessage(now ? ChatColor.GREEN + "Teamchat aktiviert." : ChatColor.YELLOW + "Teamchat deaktiviert.");
            return true;
        }
        if (args.length == 0) {
            sender.sendMessage(ChatColor.YELLOW + "/" + label + " <Nachricht>");
            return true;
        }
        send(sender, String.join(" ", args));
        return true;
    }

    public boolean isToggled(Player player) {
        return player != null && toggled.contains(player.getUniqueId());
    }

    @EventHandler
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        if (!isToggled(player)) return;
        if (!player.hasPermission("vetroforge.staff.teamchat")) {
            toggled.remove(player.getUniqueId());
            return;
        }
        event.setCancelled(true);
        Bukkit.getScheduler().runTask(plugin, () -> send(player, event.getMessage()));
    }

    public void send(CommandSender sender, String message) {
        String name = sender instanceof Player player ? player.getName() : "Console";
        String formatted = ChatColor.DARK_GRAY + "[" + ChatColor.RED + "Teamchat" + ChatColor.DARK_GRAY + "] "
                + ChatColor.GRAY + name + ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + message;
        for (Player online : Bukkit.getOnlinePlayers()) {
            if (!online.hasPermission("vetroforge.staff.teamchat")) continue;
            online.sendMessage(formatted);
            try { online.playSound(online.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.35f, 1.4f); } catch (Throwable ignored) {}
        }
        Bukkit.getConsoleSender().sendMessage(formatted);
        try {
            if (plugin.getNotifyManager() != null) {
                plugin.getNotifyManager().broadcast("staffchat", "&7" + name + " &8» &f" + message,
                        Sound.BLOCK_NOTE_BLOCK_PLING, "vetroforge.staff.teamchat");
            }
        } catch (Throwable ignored) {}
    }
}
