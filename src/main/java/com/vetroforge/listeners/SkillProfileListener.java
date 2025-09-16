package com.vetroforge.listeners;

import com.vetroforge.skills.SkillManager;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class SkillProfileListener implements Listener {

    private final SkillManager skillManager;

    public SkillProfileListener(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        skillManager.load(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        Player player = event.getPlayer();
        skillManager.save(player);
        skillManager.removeProfile(player.getUniqueId());
    }
}
