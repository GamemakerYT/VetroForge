package com.vetroforge.listeners;

import com.vetroforge.services.RankKitService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class StarterKitJoinListener implements Listener {
    private final RankKitService rankKitService;

    public StarterKitJoinListener(RankKitService rankKitService) {
        this.rankKitService = rankKitService;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        rankKitService.remindStarter(event.getPlayer());
    }
}
