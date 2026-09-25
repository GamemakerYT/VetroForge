// =======================================================
// File: src/main/java/com/vetroforge/raid/RaidAnnouncements.java
// =======================================================
package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;

public class RaidAnnouncements {

    private final FactionsPlugin plugin;

    public RaidAnnouncements(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void announceStart(String attacker, String target) {
        String msg = color(plugin.getConfig().getString("raids.broadcast.start",
                "&c&lRAID START &8» &7%attacker% &fvs &7%target%"));
        msg = msg.replace("%attacker%", attacker).replace("%target%", target);
        broadcast(msg);
        titleAll(color("&cRAID START"), color("&7"+attacker+" &fvs &7"+target));
    }

    public void announceEnd(String attacker, String target, String reason) {
        String msg = color(plugin.getConfig().getString("raids.broadcast.end",
                "&c&lRAID ENDE &8» &7%attacker% &fvs &7%target% &8(%reason%)"));
        msg = msg.replace("%attacker%", attacker).replace("%target%", target).replace("%reason%", reason==null?"-":reason);
        broadcast(msg);
        titleAll(color("&aRAID ENDE"), color("&7"+attacker+" &fvs &7"+target));
    }

    public void announceTimeUp(String attacker, String target) {
        String msg = color(plugin.getConfig().getString("raids.broadcast.timeup",
                "&eZeit abgelaufen &8» &7%attacker% &fvs &7%target%"));
        msg = msg.replace("%attacker%", attacker).replace("%target%", target);
        broadcast(msg);
        titleAll(color("&eZeit abgelaufen"), color("&7"+attacker+" &fvs &7"+target));
    }

    private void broadcast(String s) { Bukkit.getServer().broadcastMessage(s); }

    private void titleAll(String title, String sub) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            try { p.sendTitle(title, sub, 10, 50, 10); } catch (Throwable ignored) {}
        }
    }

    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }
}