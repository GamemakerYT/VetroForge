// src/main/java/com/vetroforge/raid/RaidFightListener.java

package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;

public class RaidFightListener implements Listener {

    private final FactionsPlugin plugin;

    public RaidFightListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {

        if (!(e.getDamager() instanceof Player p)) return;

        var rm = plugin.getRaidManager();
        var session = rm.getRaidForPlayer(p.getUniqueId());

        if (session == null) return;

        // 🔥 Wenn Spectator → wird Fighter
        if (p.getGameMode() == GameMode.SPECTATOR) {
            p.setGameMode(GameMode.SURVIVAL);
            RaidText.send(plugin, p, "message.attack_active");
        }
    }
}
