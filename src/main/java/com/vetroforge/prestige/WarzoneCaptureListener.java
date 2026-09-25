// src/main/java/com/vetroforge/listeners/WarzoneCaptureListener.java
package com.vetroforge.prestige;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.warzone.WarzoneCaptureEvent;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;

public class WarzoneCaptureListener implements Listener {

    private final FactionsPlugin plugin;

    public WarzoneCaptureListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onCapture(WarzoneCaptureEvent e) {
        Player p = e.getPlayer();
        if (p == null) return;

        int xp = plugin.getConfig().getInt("prestige.xp.onFlagCapture", 20);
        if (xp > 0) {
            plugin.getPrestigeManager().addXp(p.getUniqueId(), xp);
            p.sendTitle(ChatColor.RED + "FLAG CAPTURED",
                    ChatColor.GOLD + "+" + xp + " Prestige",
                    10, 30, 10);
        }

        String flag = (e.getPointId() == null ? "-" : e.getPointId());
        plugin.getServer().broadcastMessage(
                "§cWarzone§8: §f" + p.getName() + " §7hat §e" + flag + " §7erobert!");
    }
}