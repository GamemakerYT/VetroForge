// src/main/java/com/vetroforge/language/LanguageJoinListener.java
package com.vetroforge.language;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.function.Consumer;

public class LanguageJoinListener implements Listener {
    private final FactionsPlugin plugin;
    private final Consumer<Player> opener; // statt LanguageGui

    public LanguageJoinListener(FactionsPlugin plugin, Consumer<Player> opener) {
        this.plugin = plugin;
        this.opener = opener;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        if (!hasStoredLanguage(p)) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (p.isOnline()) opener.accept(p);
            }, 20L * 2);
        }
    }

    private boolean hasStoredLanguage(Player p) {
        return plugin.getI18n() != null && plugin.getI18n().hasLanguage(p);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent e) {
        if (plugin.getI18n() != null) plugin.getI18n().clearCache(e.getPlayer().getUniqueId());
    }
}
