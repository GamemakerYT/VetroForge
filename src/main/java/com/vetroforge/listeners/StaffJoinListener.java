// src/main/java/com/vetroforge/listeners/StaffJoinListener.java
package com.vetroforge.listeners;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;

public class StaffJoinListener implements Listener {

    private static final String ARROW = " §8» §f";
    private final FactionsPlugin plugin;

    public StaffJoinListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();

        // 1) IMMER Vanilla-Join-Message unterdrücken → keine Standardmeldungen für Spieler
        e.setJoinMessage(null);

        // 2) Nur Staff bekommt eine schöne Join-Message
        if (plugin.getStaffProtectionService() != null && plugin.getStaffProtectionService().isProtected(p)) {
            try {
                if (plugin.getNotifyManager() != null && plugin.getNotifyManager().isAutoOnJoin(p.getUniqueId())) {
                    plugin.getNotifyManager().setEnabled(p.getUniqueId(), true);
                    p.sendMessage(ChatColor.GREEN + "AutoNotify: Notify ist aktiv.");
                }
            } catch (Throwable ignored) {}

            String rank = rankName(p); // Admin / Co-Admin / Mod ...
            ChatColor color = rankColor(p);
            String msg = ChatColor.GRAY + rank + ChatColor.WHITE + " • " + color + p.getName()
                    + ChatColor.GRAY + " ist dem Server beigetreten.";
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                if (plugin.getStaffProtectionService().isProtected(viewer)) viewer.sendMessage("§6VetroForge" + ARROW + msg);
            }
            Bukkit.getConsoleSender().sendMessage("§6VetroForge" + ARROW + msg);
            sendOnlineStaffList(p);
        }
    }

    private void sendOnlineStaffList(Player target) {
        StringBuilder online = new StringBuilder();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (plugin.getStaffProtectionService() == null || !plugin.getStaffProtectionService().isProtected(player)) continue;
            if (online.length() > 0) online.append("§8, ");
            online.append("§f").append(player.getName());
        }
        target.sendMessage("§6Staff §8» §7Online: " + (online.length() == 0 ? "§8-" : online));
    }

    private String rankName(Player p) {
        try {
            if (plugin.getVetroPermsManager() != null) {
                String rank = plugin.getVetroPermsManager().getRank(p);
                if (rank != null && !rank.isBlank()) return plugin.getVetroPermsManager().getColor(p) + rank;
            }
        } catch (Throwable ignored) {}
        if (p.hasPermission("vetroforge.rank.admin"))   return ChatColor.DARK_RED + "Admin";
        if (p.hasPermission("vetroforge.rank.coadmin")) return ChatColor.RED + "Co-Admin";
        if (p.hasPermission("vetroforge.rank.mod"))     return ChatColor.GOLD + "Moderator";
        if (p.hasPermission("vetroforge.rank.builder")) return ChatColor.YELLOW + "Builder";
        if (p.hasPermission("vetroforge.rank.support")) return ChatColor.AQUA + "Supporter";
        return ChatColor.GRAY + "Spieler";
    }

    private ChatColor rankColor(Player p) {
        try {
            if (plugin.getVetroPermsManager() != null) {
                String rank = plugin.getVetroPermsManager().getRank(p);
                if (rank != null && !rank.isBlank()) return plugin.getVetroPermsManager().getColor(p);
            }
        } catch (Throwable ignored) {}
        if (p.hasPermission("vetroforge.rank.admin"))   return ChatColor.DARK_RED;
        if (p.hasPermission("vetroforge.rank.coadmin")) return ChatColor.RED;
        if (p.hasPermission("vetroforge.rank.mod"))     return ChatColor.GOLD;
        if (p.hasPermission("vetroforge.rank.builder")) return ChatColor.YELLOW;
        if (p.hasPermission("vetroforge.rank.support")) return ChatColor.AQUA;
        return ChatColor.GRAY;
    }
}
