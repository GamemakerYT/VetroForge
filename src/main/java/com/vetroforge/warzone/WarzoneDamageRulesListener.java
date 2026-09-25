package com.vetroforge.warzone;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;

public final class WarzoneDamageRulesListener implements Listener {

    private final FactionsPlugin plugin;

    public WarzoneDamageRulesListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPlayerDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        boolean inHazardZone = isPvpEnabled(player.getLocation());
        if (event.getCause() == EntityDamageEvent.DamageCause.FALL) {
            event.setCancelled(!inHazardZone);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onPvp(EntityDamageByEntityEvent event) {
        if (!(event.getEntity() instanceof Player victim)) return;
        Player attacker = attacker(event.getDamager());
        if (attacker == null || attacker.getUniqueId().equals(victim.getUniqueId())) return;

        boolean victimOk = isPvpEnabled(victim.getLocation());
        boolean attackerOk = isPvpEnabled(attacker.getLocation());
        if (!(victimOk && attackerOk)) {
            event.setCancelled(true);
            return;
        }

        if (!friendlyFireAllowed(attacker, victim)) {
            event.setCancelled(true);
            return;
        }

        if (!prestigeTierAllows(attacker, victim)) {
            event.setCancelled(true);
            return;
        }

        if (isEliminatedFromRaid(attacker) || isEliminatedFromRaid(victim)) {
            event.setCancelled(true);
        }
    }

    /**
     * Level-Schutz: ein Spieler mit niedrigerer Prestige-Stufe ist in einer Disparity-Zone
     * geschuetzt, solange die Zonen-Stufe unter der des Angreifers liegt (z.B. Prestige-2-
     * Spieler kann einen Prestige-1-Spieler in Disparity 1 nicht angreifen). Begibt sich der
     * niedrigerstufige Spieler freiwillig in eine Zone, deren Disparity-Level mindestens dem
     * Angreifer entspricht, verliert er dort den Schutz. Ein gleich- oder niedrigerstufiger
     * Angreifer ist nie eingeschraenkt (Prestige 1 darf in Disparity 1 jeden angreifen).
     */
    private boolean prestigeTierAllows(Player attacker, Player victim) {
        WarzoneRegionManager warzone = plugin.getWarzoneRegionManager();
        if (warzone == null) return true;

        int attackerTier = prestigeTier(attacker);
        int victimTier = prestigeTier(victim);
        if (attackerTier <= victimTier) return true;

        int disparityAtVictim = warzone.disparityLevel(victim.getLocation());
        return disparityAtVictim >= attackerTier;
    }

    private int prestigeTier(Player p) {
        try {
            return plugin.getStatsManager() == null ? 1 : Math.max(1, plugin.getStatsManager().getPrestige(p));
        } catch (Throwable ignored) {
            return 1;
        }
    }

    /** Leben-System: ein Spieler, der fuer seinen aktuellen Raid eliminiert wurde, darf
     *  darin weder austeilen noch einstecken, auch wenn er nach dem Respawn zurücklaeuft. */
    private boolean isEliminatedFromRaid(Player player) {
        return plugin.getRaidManager() != null && plugin.getRaidManager().isEliminated(player);
    }

    /** Team-Kill/Allianz-Regeln, vom Faction-Leader im /f-GUI gesetzt (FactionMenuManager). */
    private boolean friendlyFireAllowed(Player attacker, Player victim) {
        var factions = plugin.getFactionManager();
        if (factions == null) return true;

        String attackerFaction = factions.getFactionOf(attacker.getUniqueId());
        String victimFaction = factions.getFactionOf(victim.getUniqueId());
        if (attackerFaction == null || victimFaction == null) return true;

        if (attackerFaction.equalsIgnoreCase(victimFaction)) {
            return plugin.getConfig().getBoolean(
                    com.vetroforge.gui.FactionMenuManager.combatSettingPath(attackerFaction, "friendlyFire"), false);
        }

        if (factions.getAllies(attackerFaction).contains(victimFaction)) {
            return plugin.getConfig().getBoolean(
                    com.vetroforge.gui.FactionMenuManager.combatSettingPath(attackerFaction, "allyFire"), false);
        }

        return true;
    }

    private boolean isPvpEnabled(Location loc) {
        WarzoneRegionManager warzone = plugin.getWarzoneRegionManager();
        return warzone != null && warzone.isPvpZone(loc);
    }

    private Player attacker(Entity damager) {
        if (damager instanceof Player player) return player;
        if (damager instanceof org.bukkit.entity.Projectile projectile
                && projectile.getShooter() instanceof Player player) {
            return player;
        }
        return null;
    }
}
