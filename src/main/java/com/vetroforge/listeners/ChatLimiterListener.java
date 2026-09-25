// src/main/java/com/vetroforge/listeners/ChatLimiterListener.java
package com.vetroforge.listeners;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.chat.ChatLimiter;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;

@SuppressWarnings("deprecation") // Spigot-Event ist markiert, funktioniert aber in 1.21 weiterhin
public class ChatLimiterListener implements Listener {
    private final FactionsPlugin plugin;
    private final ChatLimiter limiter;

    public ChatLimiterListener(FactionsPlugin plugin, ChatLimiter limiter) {
        this.plugin = plugin;
        this.limiter = limiter;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e) {
        Player p = e.getPlayer();

        // Bypass-Rechte
        if (p.hasPermission("vetroforge.chat.slow.bypass") || p.hasPermission("vetroforge.staff.slowchat")) return;

        if (!limiter.canSend(p)) {
            long rem = (limiter.getRemainingMs(p) + 999) / 1000;
            p.sendMessage("§cBitte warte §e" + rem + "s §cbevor du erneut schreibst.");
            e.setCancelled(true);
            return;
        }
        limiter.recordSend(p);
    }
}