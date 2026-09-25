// src/main/java/com/vetroforge/listeners/ChatListener.java
package com.vetroforge.listeners;

import com.vetroforge.FactionsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerChatEvent;


import java.util.HashSet;
import java.util.Set;

public class ChatListener implements Listener {

    private final FactionsPlugin plugin;
    private final Set<String> filterWords;

    public ChatListener(FactionsPlugin plugin){
        this.plugin = plugin;
        this.filterWords = new HashSet<>(plugin.getConfig().getStringList("chat.filter.words"));
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent e){
        Player p = e.getPlayer();
        String rank = resolveRank(p);
        String name = p.getName();

        // Filter (msgnotify on/off könntest du per Spieler-Flag speichern – hier immer an)
        String msg = e.getMessage();
        if (containsBanned(msg)) {
            e.setCancelled(true);
            p.sendMessage("§cBitte respektvoll bleiben.");
            return;
        }

        // Format: "Admin • Spieler » Message"
        String format = rankColor(rank) + rank + " §7• §f" + name + " §8» §7%2$s";
        e.setFormat(format);
    }

    private boolean containsBanned(String msg){
        String m = msg.toLowerCase();
        for (String w : filterWords) {
            if (w != null && !w.isBlank() && m.contains(w.toLowerCase())) return true;
        }
        return false;
    }

    private String resolveRank(Player p){
        if (p.hasPermission("vetroforge.rank.admin")) return "Admin";
        if (p.hasPermission("vetroforge.rank.coadmin")) return "Co-Admin";
        if (p.hasPermission("vetroforge.rank.mod")) return "Moderator";
        if (p.hasPermission("vetroforge.rank.builder")) return "Builder";
        if (p.hasPermission("vetroforge.rank.supporter")) return "Supporter";
        return "Member";
    }
    private String rankColor(String r){
        if ("Admin".equals(r)) return "§4";
        if ("Co-Admin".equals(r)) return "§c";
        if ("Moderator".equals(r)) return "§b";
        if ("Builder".equals(r)) return "§2";
        if ("Supporter".equals(r)) return "§3";
        return "§7"; // Member grau
    }
}