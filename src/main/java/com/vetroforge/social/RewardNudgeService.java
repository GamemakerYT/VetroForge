package com.vetroforge.social;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public final class RewardNudgeService implements Listener {
    private final FactionsPlugin plugin;
    private final Set<UUID> nudgedThisSession = new HashSet<>();

    public RewardNudgeService(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        long delayTicks = Math.max(20L, plugin.getConfig().getLong("rewards.nudge.delayTicks", 20L * 60L * 10L));
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || nudgedThisSession.contains(player.getUniqueId())) return;
            nudgedThisSession.add(player.getUniqueId());
            player.sendActionBar(ChatColor.RED + "Rewards " + ChatColor.DARK_GRAY + "» "
                    + ChatColor.GRAY + "Du hast 2 Rewards offen " + ChatColor.YELLOW + "(/reward)");
        }, delayTicks);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        nudgedThisSession.remove(event.getPlayer().getUniqueId());
    }
}
