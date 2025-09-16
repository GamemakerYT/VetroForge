package com.vetroforge.listeners;

import com.vetroforge.skills.SkillManager;
import com.vetroforge.skills.SkillType;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.AreaEffectCloud;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.AreaEffectCloudApplyEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.PotionSplashEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class SkillExperienceListener implements Listener {

    private final SkillManager skillManager;

    public SkillExperienceListener(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlayerDeath(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) {
            return;
        }
        if (skillManager.isWorldDisabled(killer.getWorld())) {
            return;
        }
        skillManager.reward(killer, SkillType.ASSASSIN, "kill");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker) || !(event.getEntity() instanceof Player victim)) {
            return;
        }
        if (skillManager.isWorldDisabled(attacker.getWorld())) {
            return;
        }
        if (event.getCause() == EntityDamageEvent.DamageCause.ENTITY_ATTACK) {
            if (isBackstab(attacker, victim)) {
                skillManager.reward(attacker, SkillType.ASSASSIN, "backstab");
            }
            skillManager.reward(attacker, SkillType.WARRIOR, "melee");
        }
        if (victim.isBlocking()) {
            skillManager.reward(victim, SkillType.WARRIOR, "block");
        }
    }

    private boolean isBackstab(Player attacker, Player victim) {
        Location attackerLocation = attacker.getLocation();
        Location victimLocation = victim.getLocation();
        double yawDifference = Math.abs(wrapAngle(attackerLocation.getYaw() - victimLocation.getYaw()));
        if (yawDifference > 135) {
            return true;
        }
        // fallback using vector direction
        try {
            return victimLocation.getDirection().dot(attackerLocation.toVector().subtract(victimLocation.toVector()).normalize()) < -0.5;
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private float wrapAngle(float angle) {
        angle %= 360.0F;
        if (angle >= 180.0F) {
            angle -= 360.0F;
        }
        if (angle < -180.0F) {
            angle += 360.0F;
        }
        return angle;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPotionSplash(PotionSplashEvent event) {
        if (!(event.getPotion().getShooter() instanceof Player thrower)) {
            return;
        }
        if (skillManager.isWorldDisabled(thrower.getWorld())) {
            return;
        }
        if (!containsHealingEffect(event.getPotion().getEffects())) {
            return;
        }
        boolean affectedOthers = event.getAffectedEntities().stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                .anyMatch(target -> !target.getUniqueId().equals(thrower.getUniqueId()));
        if (affectedOthers) {
            skillManager.reward(thrower, SkillType.HEALER, "splash");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onAreaCloudApply(AreaEffectCloudApplyEvent event) {
        AreaEffectCloud cloud = event.getEntity();
        Projectile sourceProjectile = cloud.getSource() instanceof Projectile ? (Projectile) cloud.getSource() : null;
        if (sourceProjectile == null) {
            return;
        }
        if (!(sourceProjectile.getShooter() instanceof Player player)) {
            return;
        }
        if (skillManager.isWorldDisabled(player.getWorld())) {
            return;
        }
        if (!containsHealingEffect(cloud.getEffects())) {
            return;
        }
        boolean healedOther = event.getAffectedEntities().stream()
                .filter(entity -> entity instanceof Player)
                .map(entity -> (Player) entity)
                .anyMatch(target -> !target.getUniqueId().equals(player.getUniqueId()));
        if (healedOther) {
            skillManager.reward(player, SkillType.HEALER, "lingering");
        }
    }

    private boolean containsHealingEffect(Iterable<PotionEffect> effects) {
        for (PotionEffect effect : effects) {
            PotionEffectType type = effect.getType();
            if (type.equals(PotionEffectType.HEAL) || type.equals(PotionEffectType.REGENERATION)) {
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBlockBreak(BlockBreakEvent event) {
        if (skillManager.isWorldDisabled(event.getBlock().getWorld())) {
            return;
        }
        Material material = event.getBlock().getType();
        if (material == Material.OBSIDIAN) {
            skillManager.reward(event.getPlayer(), SkillType.RAIDER, "obsidian");
        }
    }

    public void rewardCoreBreak(Player player) {
        skillManager.reward(player, SkillType.RAIDER, "core");
    }

    public void rewardFlagCapture(Player player) {
        skillManager.reward(player, SkillType.RAIDER, "flag");
    }
}
