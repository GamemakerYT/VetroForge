// src/main/java/com/vetroforge/commands/WikiSearchCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class WikiSearchCommand implements CommandExecutor {
    private final FactionsPlugin plugin;
    public WikiSearchCommand(FactionsPlugin p){ this.plugin=p; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length < 1) { s.sendMessage(ChatColor.YELLOW + "/" + l + " <query>"); return true; }
        String q = String.join(" ", a);

        if (s instanceof Player p) {
            if (plugin.getWikiManager() != null) {
                plugin.getWikiManager().openSearch(p, q, 1);
            } else {
                p.sendMessage("§cWiki nicht verfügbar.");
            }
        } else {
            // Konsole: einfacher Text-Search über config (Kurzfassung)
            s.sendMessage("§8—— §fWiki-Suche: §7" + q + " §8——");
            if (plugin.getConfig().isConfigurationSection("shop.general.items")) {
                for (String id : plugin.getConfig().getConfigurationSection("shop.general.items").getKeys(false)) {
                    String name = ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                            plugin.getConfig().getString("shop.general.items."+id+".name", id)));
                    if (id.toLowerCase().contains(q.toLowerCase()) || name.toLowerCase().contains(q.toLowerCase())) {
                        s.sendMessage(" - " + id + " (" + name + ")");
                    }
                }
            } else {
                s.sendMessage("Keine Items definiert.");
            }
        }
        return true;
    }
}