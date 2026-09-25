package com.vetroforge.skills.listener;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.skills.SkillType;
import com.vetroforge.skills.data.AbilityState;
import com.vetroforge.skills.data.SkillState;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TNTPrimed;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.util.Vector;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillEffectsListener implements Listener {

    private final FactionsPlugin plugin;
    private final Map<UUID, Long> recoveryCooldown = new ConcurrentHashMap<>();

    public SkillEffectsListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    // ⚔ DAMAGE BOOST
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;

        SkillState st = plugin.getSkillManager().getState(p, SkillType.WARRIOR);
        AbilityState ab = st.abilities.get("damage_boost");

        if (ab == null || ab.level <= 0) return;

        double multi = 1.0 + (ab.level * 0.05);
        e.setDamage(e.getDamage() * multi);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onBackstab(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player player)) return;
        if (!(event.getEntity() instanceof LivingEntity target)) return;

        SkillState state = plugin.getSkillManager().getState(player, SkillType.ASSASSIN);
        AbilityState ability = state.abilities.get("backstab");
        if (ability == null || ability.level <= 0) return;
        if (!isBehind(target, player)) return;

        event.setDamage(event.getDamage() * (1.0D + ability.level * 0.06D));
        player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_ATTACK_CRIT, 0.45F, 1.45F);
    }

    // 💣 TNT BOOST
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!(e.getEntity() instanceof TNTPrimed tnt)) return;
        if (!(tnt.getSource() instanceof Player p)) return;

        SkillState st = plugin.getSkillManager().getState(p, SkillType.RAIDER);
        AbilityState ab = st.abilities.get("demolition");

        if (ab == null || ab.level <= 0) return;

        float yield = 1.0f + (ab.level * 0.25f);
        e.setYield(yield);
    }

    // 🧱 OBSIDIAN BONUS
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGH)
    public void onBreak(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.OBSIDIAN) return;

        Player p = e.getPlayer();
        plugin.getSkillManager().addXp(p, SkillType.RAIDER, 10);

        SkillState st = plugin.getSkillManager().getState(p, SkillType.RAIDER);
        AbilityState ab = st.abilities.get("obsidian_master");

        if (ab == null || ab.level <= 0) return;

        e.setExpToDrop(e.getExpToDrop() + ab.level * 2);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRecovery(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof Player player)) return;

        SkillState state = plugin.getSkillManager().getState(player, SkillType.HEALER);
        AbilityState ability = state.abilities.get("recovery");
        if (ability == null || ability.level <= 0) return;

        double after = Math.max(0.0D, player.getHealth() - event.getFinalDamage());
        double max = Math.max(1.0D, player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH) == null
                ? 20.0D
                : player.getAttribute(org.bukkit.attribute.Attribute.MAX_HEALTH).getValue());
        if (after / max > 0.35D) return;

        long now = System.currentTimeMillis();
        long readyAt = recoveryCooldown.getOrDefault(player.getUniqueId(), 0L);
        if (now < readyAt) return;
        recoveryCooldown.put(player.getUniqueId(), now + 45_000L);

        int duration = Math.max(40, ability.level * 80);
        player.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, duration, 0, true, true));
        player.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, duration, 0, true, true));
        player.playSound(player.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 0.65F, 1.7F);
        plugin.getSkillManager().addXp(player, SkillType.HEALER, 5);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null) return;
        plugin.getSkillManager().addXp(killer, SkillType.WARRIOR, 20);
        plugin.getSkillManager().addXp(killer, SkillType.ASSASSIN, 10);
    }

    private boolean isBehind(LivingEntity target, Player player) {
        Vector targetDirection = target.getLocation().getDirection().setY(0).normalize();
        Vector targetToPlayer = player.getLocation().toVector().subtract(target.getLocation().toVector()).setY(0);
        if (targetToPlayer.lengthSquared() < 0.01D) return false;
        return targetDirection.dot(targetToPlayer.normalize()) < -0.45D;
    }
}
