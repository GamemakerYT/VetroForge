// src/main/java/com/vetroforge/raid/RaidInteractListener.java

package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import org.bukkit.GameMode;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;

public class RaidInteractListener implements Listener {

    private final FactionsPlugin plugin;

    public RaidInteractListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler
    public void onInteract(PlayerInteractEvent e) {

        Player p = e.getPlayer();
        RaidManager rm = plugin.getRaidManager();

        RaidManager.Session session = rm.getRaidForPlayer(p.getUniqueId());
        if (session == null) return;

        if (!session.fightEnabled) {
            p.sendTitle(
                    RaidText.tr(plugin, p, "title.fight_locked"),
                    RaidText.tr(plugin, p, "title.wait_for_fight"),
                    5, 30, 5
            );
            return;
        }

        rm.enableFighting(p);
    }
}
