// src/main/java/com/vetroforge/punish/BanScreenListener.java
package com.vetroforge.punish;

import com.vetroforge.FactionsPlugin;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.event.*;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;

import java.text.SimpleDateFormat;
import java.util.Date;

public class BanScreenListener implements Listener {

    private final FactionsPlugin plugin;
    private final SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm");

    public BanScreenListener(FactionsPlugin plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        String name = e.getName();
        BanList bl = Bukkit.getBanList(BanList.Type.NAME);
        BanEntry be = bl.getBanEntry(name);

        // zusätzlich interner Flag (falls genutzt)
        boolean flagged = false;
        try { flagged = plugin.getPunishmentManager().isBanned(e.getUniqueId()); } catch (Throwable ignored) {}

        if (be == null && !flagged) return;

        String reason = (be != null && be.getReason()!=null) ? be.getReason() : "Ban";
        String src    = (be != null && be.getSource()!=null) ? be.getSource() : "Staff";
        String until  = (be != null && be.getExpiration()!=null) ? fmt.format(new Date(be.getExpiration().getTime())) : "Permanent";

        String msg = ChatColor.RED + "" + ChatColor.BOLD + "Du bist gebannt!\n"
                + ChatColor.GRAY + "Grund: " + ChatColor.WHITE + reason + "\n"
                + ChatColor.GRAY + "Von: "   + ChatColor.WHITE + src + "\n"
                + ChatColor.GRAY + "Bis: "   + ChatColor.WHITE + until + "\n"
                + ChatColor.DARK_GRAY + "—\n"
                + ChatColor.GRAY + "Entbannungsantrag: " + ChatColor.WHITE + "vetroforge.net";

        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, msg);
    }
}
