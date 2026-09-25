package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerChangedWorldEvent;

/**
 * Leben-System: jeder Teilnehmer (Angreifer im raiders-Set oder Verteidiger aus der
 * angegriffenen Faction) hat pro Raid begrenzte Leben (raid.lives.max, Standard 3).
 * Beim letzten Tod wird der Spieler aus der Faction-Welt des Raids teleportiert und
 * kann fuer diesen Raid nicht mehr beitreten/mitkaempfen (siehe RaidJoinService).
 */
public final class RaidLivesListener implements Listener {

    private final FactionsPlugin plugin;

    public RaidLivesListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent event) {
        Player player = event.getEntity();
        if (plugin.getRaidManager() == null) return;

        boolean eliminated = plugin.getRaidManager().recordDeath(player);
        if (eliminated) {
            eject(player);
            return;
        }

        int lives = plugin.getRaidManager().livesRemaining(player);
        if (lives >= 0) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (player.isOnline()) RaidText.send(plugin, player, "message.lives_remaining", "lives", lives);
            }, 20L);
        }
    }

    /** Verhindert, dass ein eliminierter Spieler nach dem Respawn einfach zurueckrennt. */
    @EventHandler
    public void onWorldChange(PlayerChangedWorldEvent event) {
        Player player = event.getPlayer();
        if (plugin.getRaidManager() == null || !plugin.getRaidManager().isEliminated(player)) return;
        eject(player);
    }

    /** leaveRaid() teleports the player back to their own faction spawn/home -- reused as-is
     *  instead of resolving a destination here, but deferred a tick since teleporting a still-dead
     *  entity (PlayerDeathEvent fires before respawn) is unreliable in Bukkit. */
    private void eject(Player player) {
        RaidText.send(plugin, player, "message.eliminated_cannot_rejoin");
        try { player.playSound(player.getLocation(), Sound.ENTITY_WITHER_DEATH, 0.5f, 1.6f); } catch (Throwable ignored) {}

        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!player.isOnline()) return;
            player.sendTitle(RaidText.tr(plugin, player, "message.eliminated_title"),
                    RaidText.tr(plugin, player, "message.eliminated_subtitle"), 10, 60, 20);
            if (plugin.getRaidSessionService() != null) {
                try { plugin.getRaidSessionService().leaveRaid(player); } catch (Throwable ignored) {}
            } else if (plugin.getRaidManager() != null) {
                plugin.getRaidManager().removeRaider(player.getUniqueId());
            }
        });
    }
}
