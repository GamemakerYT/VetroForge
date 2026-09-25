// src/main/java/com/vetroforge/raid/ping/PingListener.java
package com.vetroforge.raid.ping;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.raid.RaidText;
import com.vetroforge.raid.obsidian.ObsidianHealthManager;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;

public class PingListener implements Listener {

    private final FactionsPlugin plugin;
    private final ObsidianHealthManager hp;

    public PingListener(FactionsPlugin plugin, ObsidianHealthManager hp) {
        this.plugin = plugin;
        this.hp = hp;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPing(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getItem() == null || e.getItem().getType() != Material.STICK) return; // Stick = Ping-Tool

        Block b = e.getClickedBlock();
        if (b == null) return;

        Player p = e.getPlayer();

        // Team/Faction Broadcast (vereinfachter Broadcast an Online-Faction-Mitglieder)
        String fac = plugin.getFactionManager() != null
                ? plugin.getFactionManager().getFactionOf(p.getUniqueId()) : null;

        String where = b.getType().name();
        String extra = "";
        if (b.getType() == Material.OBSIDIAN) {
            extra = " §8» §7" + hp.format(b);
        }

        for (Player on : Bukkit.getOnlinePlayers()) {
            String msg = RaidText.tr(plugin, on, "message.ping",
                    "block", where,
                    "hp", extra,
                    "x", b.getX(),
                    "y", b.getY(),
                    "z", b.getZ());
            if (fac == null || plugin.getFactionManager() == null) {
                if (on.equals(p)) on.sendMessage(msg);
            } else {
                String of = plugin.getFactionManager().getFactionOf(on.getUniqueId());
                if (fac.equalsIgnoreCase(of)) on.sendMessage(msg);
            }
        }

        // Versionssichere Partikel (verschiedene Enum-Namen zwischen MC-Versionen)
        Particle happy = resolveParticle("VILLAGER_HAPPY", "HAPPY_VILLAGER", "HEART");
        b.getWorld().spawnParticle(happy, b.getLocation().add(.5,1.2,.5), 20, .3,.3,.3, 0.01);
        b.getWorld().playSound(b.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.7f, 1.6f);
    }

    private Particle resolveParticle(String... candidates) {
        for (String name : candidates) {
            try { return Particle.valueOf(name); } catch (IllegalArgumentException ignored) {}
        }
        return Particle.CRIT; // letzte Rettung
    }
}
