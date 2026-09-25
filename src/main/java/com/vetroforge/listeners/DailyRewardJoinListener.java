package com.vetroforge.listeners;

import com.vetroforge.services.DailyRewardService;
import com.vetroforge.util.Branding;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.util.UUID;

public final class DailyRewardJoinListener implements Listener {
    private final DailyRewardService service;

    public DailyRewardJoinListener(DailyRewardService service) {
        this.service = service;
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        Bukkit.getScheduler().runTaskLater(service.plugin(), () ->
                Bukkit.getScheduler().runTaskAsynchronously(service.plugin(), () -> {
                    DailyRewardService.Status status = service.status(uuid);
                    if (!status.canClaim()) return;

                    Bukkit.getScheduler().runTask(service.plugin(), () -> {
                        Player player = Bukkit.getPlayer(uuid);
                        if (player == null || !player.isOnline()) return;

                        player.sendMessage(Branding.PREFIX + "Dein Daily Reward ist bereit. §f/rewards");
                    });
                }), 80L);
    }
}
