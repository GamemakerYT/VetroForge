package com.vetroforge.listeners;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.scoreboard.ScoreboardService;
import org.bukkit.ChatColor;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;

public class ChatFormatListener implements Listener {

    private final FactionsPlugin plugin;
    private final ScoreboardService scoreboardService;
    private final boolean useAdventure;
    private boolean adventureWarned;

    public ChatFormatListener(FactionsPlugin plugin, ScoreboardService scoreboardService) {
        this.plugin = plugin;
        this.scoreboardService = scoreboardService;
        this.useAdventure = plugin.getConfig().getBoolean("chat.useAdventure", false);
    }

    @EventHandler(priority = EventPriority.HIGH)
    public void onChat(AsyncPlayerChatEvent event) {
        if (scoreboardService == null) {
            return;
        }
        String rank = scoreboardService.getRankLabel(event.getPlayer());
        String prefix = rank + ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + event.getPlayer().getName();
        if (useAdventure && !adventureWarned) {
            adventureWarned = true;
            plugin.getPrefixedLogger().fine("Adventure chat formatting requested but renderer not configured; falling back to legacy format.");
        }
        event.setFormat(prefix + ChatColor.GRAY + ": " + ChatColor.WHITE + "%2$s");
    }
}
