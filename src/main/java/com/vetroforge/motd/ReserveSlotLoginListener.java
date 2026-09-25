// src/main/java/com/vetroforge/motd/ReserveSlotLoginListener.java
package com.vetroforge.motd;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerLoginEvent;

public class ReserveSlotLoginListener implements Listener {

    private final FactionsPlugin plugin;

    public ReserveSlotLoginListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent e) {
        if (!plugin.getConfig().getBoolean("motd.slotOverride.reserve.enabled", false)) return;

        int online = Bukkit.getOnlinePlayers().size();
        int max    = Bukkit.getMaxPlayers(); // echter physischer Cap
        String perm = plugin.getConfig().getString("motd.slotOverride.reserve.permission", "vetroforge.slot.reserve");

        boolean full = online >= max;
        if (!full) return;

        if (e.getPlayer().hasPermission(perm)) {
            try { e.allow(); } catch (Throwable t) { e.setResult(PlayerLoginEvent.Result.ALLOWED); }
            return;
        }

        int reserve = Math.max(0, plugin.getConfig().getInt("motd.slotOverride.reserve.count", 0));
        String msg = ChatColor.RED + "Server voll. " + ChatColor.GRAY + "Reserve-Slots: " + reserve
                + ChatColor.DARK_GRAY + " (Staff/Donor)";
        e.disallow(PlayerLoginEvent.Result.KICK_FULL, msg);
    }
}