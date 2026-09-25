// src/main/java/com/vetroforge/listeners/PvpKillListener.java
package com.vetroforge.prestige;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.warzone.WarzoneRegionManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class PvpKillListener implements Listener {

    private final FactionsPlugin plugin;

    // Anti-Farm für Prestige-XP: killer:victim -> letzter gewerteter Kill (Epoch-Sekunden).
    // Bewusst eine eigene, von StatsManager unabhängige Cooldown-Prüfung (siehe dortiger
    // Kommentar an isKillOnCooldown) statt derselben Instanz/desselben Zeitstempels.
    private final Map<String, Long> recentKills = new ConcurrentHashMap<>();

    public PvpKillListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onDeath(PlayerDeathEvent e) {
        Player victim = e.getEntity();
        Player killer = victim.getKiller();
        if (killer == null) return; // kein PVP-Kill

        if (isOnCooldown(killer.getUniqueId(), victim.getUniqueId())) {
            return; // Farmen desselben Spielers: keine Prestige-XP
        }

        int xp = plugin.getConfig().getInt("prestige.xp.onKill", 5);
        // Bonus, wenn Kill in Warzone passiert ist
        int bonus = 0;
        Location loc = victim.getLocation();
        WarzoneRegionManager wz = plugin.getWarzoneRegionManager();
        if (wz != null && loc != null && wz.isWarzone(loc)) {
            bonus = plugin.getConfig().getInt("prestige.xp.bonusWarzoneKill", 2);
        }
        int total = Math.max(0, xp + bonus);
        if (total <= 0) return;

        plugin.getPrestigeManager().addXp(killer.getUniqueId(), total);
        killer.sendActionBar("§a+" + total + "§7 Prestige §8(PvP)");
    }

    private boolean isOnCooldown(UUID killer, UUID victim) {
        int cd = Math.max(0, plugin.getConfig().getInt("stats.killCooldownSeconds", 120));
        if (cd <= 0) return false;
        String key = killer + ":" + victim;
        long now = System.currentTimeMillis() / 1000L;
        Long last = recentKills.put(key, now);
        return last != null && (now - last) < cd;
    }
}
