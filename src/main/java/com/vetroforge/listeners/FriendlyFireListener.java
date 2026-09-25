// src/main/java/com/vetroforge/listeners/FriendlyFireListener.java
package com.vetroforge.listeners;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.factions.FactionRelationsManager;
import com.vetroforge.warzone.WarzoneRegionManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.projectiles.ProjectileSource;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Zonenlogik:
 * - Warzone: Friendly-Fire erlaubt (kein Cancel).
 * - Außerhalb Warzone (z.B. Spawn/Lobby/Rest): Friendly-Fire blockiert.
 *
 * Admin-Bypass: vetroforge.pvp.friendlyfire.bypass
 *
 * Konfigurierbar (optional):
 * pvp.friendlyFire.allowInWarzone: true
 * pvp.friendlyFire.blockOutsideWarzone: true
 * pvp.friendlyFire.notify: true
 * pvp.friendlyFire.notifyCooldownMs: 2000
 */
public class FriendlyFireListener implements Listener {

    private final FactionsPlugin plugin;

    // Anti-Spam Cooldown für Nachrichten/Sounds
    private final Map<UUID, Long> lastWarn = new HashMap<>();

    public FriendlyFireListener(FactionsPlugin plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPvP(EntityDamageByEntityEvent e) {
        if (!(e.getEntity() instanceof Player victim)) return;

        Player damager = resolveDamager(e);
        if (damager == null || damager.equals(victim)) return;

        // Admin/Staff dürfen (optional)
        if (damager.hasPermission("vetroforge.pvp.friendlyfire.bypass")) return;

        FactionRelationsManager rel = plugin.getFactionRelationsManager();
        if (rel == null) return;

        // Nur wenn befreundet (OWN/ALLY/TRUCE o.ä.) greifen wir ein
        UUID a = damager.getUniqueId();
        UUID b = victim.getUniqueId();
        if (!rel.areFriendly(a, b)) return;

        // Zonen-Regeln
        WarzoneRegionManager wz = plugin.getWarzoneRegionManager();
        boolean victimInWarzone = wz != null && wz.isWarzone(victim.getLocation());
        boolean damagerInWarzone = wz != null && wz.isWarzone(damager.getLocation());
        boolean inWarzone = victimInWarzone || damagerInWarzone;

        // Config-Flags (mit Defaults passend zu deinem Wunsch)
        boolean allowInWarzone     = plugin.getConfig().getBoolean("pvp.friendlyFire.allowInWarzone", true);
        boolean blockOutsideWarzone= plugin.getConfig().getBoolean("pvp.friendlyFire.blockOutsideWarzone", true);

        if (inWarzone && allowInWarzone) {
            // Warzone → friendly fire erlaubt
            return;
        }

        if (!inWarzone && blockOutsideWarzone) {
            // Außerhalb Warzone → blockieren
            e.setCancelled(true);
            notifyBlocked(damager, victim);
        }
    }

    /* ================= Helpers ================= */

    private Player resolveDamager(EntityDamageByEntityEvent e) {
        if (e.getDamager() instanceof Player p) return p;
        if (e.getDamager() instanceof org.bukkit.entity.Projectile proj) {
            ProjectileSource src = proj.getShooter();
            if (src instanceof Player p) return p;
        }
        return null;
        // Optional: hier könntest du noch Tamed-Wolves, Tridents usw. einbauen.
    }

    private void notifyBlocked(Player damager, Player victim) {
        // Ruhiges, kurzes Feedback – mit Cooldown
        if (!plugin.getConfig().getBoolean("pvp.friendlyFire.notify", true)) return;

        long now = System.currentTimeMillis();
        long last = lastWarn.getOrDefault(damager.getUniqueId(), 0L);
        long cdMs = Math.max(250L, plugin.getConfig().getLong("pvp.friendlyFire.notifyCooldownMs", 2000L));
        if (now - last < cdMs) return;
        lastWarn.put(damager.getUniqueId(), now);

        String name = ChatColor.WHITE + victim.getName() + ChatColor.GRAY;
        damager.sendActionBar(ChatColor.RED + "Friendly-Fire ist hier deaktiviert " + ChatColor.DARK_GRAY + "• " + ChatColor.GRAY + "Ziel: " + name);
        try { damager.playSound(damager.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.8f); } catch (Throwable ignored) {}
    }
}
