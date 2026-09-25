package com.vetroforge.warzone;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.services.BuildModeService;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.TNTPrimeEvent;

/**
 * TNT darf im Spawn und in der Warzone nicht gezuendet werden -- Faction-Welten (Raid-TNT-Kanonen)
 * sind bewusst ausgenommen, das ist dort Kernmechanik.
 */
public final class SpawnWarzoneTntListener implements Listener {

    private final FactionsPlugin plugin;

    public SpawnWarzoneTntListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPrime(TNTPrimeEvent event) {
        WarzoneRegionManager region = plugin.getWarzoneRegionManager();
        if (region == null) return;

        var loc = event.getBlock().getLocation();
        boolean restricted = region.isLobbySpawnArea(loc) || region.isWarzone(loc);
        if (!restricted) return;

        if (event.getPrimingEntity() instanceof Player player
                && BuildModeService.hasAccess(plugin, player, BuildModeService.Area.GLOBAL)) {
            return;
        }

        event.setCancelled(true);
    }
}
