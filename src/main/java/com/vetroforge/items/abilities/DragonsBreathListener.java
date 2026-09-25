// src/main/java/com/vetroforge/items/abilities/DragonsBreathListener.java
package com.vetroforge.items.abilities;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Fireball;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityChangeBlockEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.util.Vector;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import com.vetroforge.util.VetroForgeUtil;

public class DragonsBreathListener implements Listener {

    private static final String ITEM_ID = "dragons_breath";
    private static final String META_DB = "vetro_dragons_breath";
    private static final String META_OWNER = "vetro_dragons_breath_owner";
    private static final String META_LEVEL = "vetro_dragons_breath_level";

    private final FactionsPlugin plugin;
    private final long cooldownMs;

    public DragonsBreathListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.cooldownMs = (long) (plugin.getConfig().getDouble("loot.dragons_breath.cooldownSec", 6.0D) * 1000L);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        if (e.getItem() == null) return;
        if (!e.getAction().name().contains("RIGHT")) return;

        ItemStack item = e.getItem();
        if (!ITEM_ID.equalsIgnoreCase(CustomItemUtil.baseId(plugin, item))) return;

        e.setCancelled(true);

        Player player = e.getPlayer();
        long remaining = plugin.getItemCooldownService().remainingMs(player, ITEM_ID);
        if (remaining > 0L) {
            long left = Math.max(1L, (remaining + 999L) / 1000L);
            player.sendMessage(VetroForgeUtil.tr(plugin, player, "items.dragons_breath.cooldown",
                    Map.of("seconds", String.valueOf(left))));
            return;
        }

        long adjustedCooldown = plugin.getItemCooldownService().use(player, item, ITEM_ID, cooldownMs);
        player.setCooldown(item.getType(), Math.max(1, (int) (adjustedCooldown / 50L)));

        spawnFireball(player, CustomItemUtil.dragonsBreathLevel(plugin, item));
    }

    private void spawnFireball(Player player, int level) {
        Location eye = player.getEyeLocation();
        Vector dir = eye.getDirection().normalize();
        World world = player.getWorld();

        Location spawn = eye.clone().add(dir.clone().multiply(1.15));

        Fireball fireball = (Fireball) world.spawnEntity(spawn, EntityType.FIREBALL);
        fireball.setShooter(player);
        fireball.setYield(0F);
        fireball.setIsIncendiary(false);
        fireball.setGravity(false);
        fireball.setVelocity(dir.multiply(1.25));

        fireball.setMetadata(META_DB, new FixedMetadataValue(plugin, true));
        fireball.setMetadata(META_OWNER, new FixedMetadataValue(plugin, player.getUniqueId().toString()));
        fireball.setMetadata(META_LEVEL, new FixedMetadataValue(plugin, level));

        try {
            world.playSound(player.getLocation(), Sound.ENTITY_BLAZE_SHOOT, 1f, 1f);
        } catch (Throwable ignored) {
        }

        try {
            world.spawnParticle(Particle.FLAME, player.getLocation().add(0, 1.2, 0), 24, 0.25, 0.25, 0.25, 0.01);
            world.spawnParticle(Particle.SMOKE, player.getLocation().add(0, 1.2, 0), 12, 0.20, 0.20, 0.20, 0.01);
        } catch (Throwable ignored) {
        }

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {
            if (!fireball.isValid() || fireball.isDead()) {
                task.cancel();
                return;
            }

            Location loc = fireball.getLocation();
            try {
                world.spawnParticle(Particle.FLAME, loc, 6, 0.08, 0.08, 0.08, 0.005);
                world.spawnParticle(Particle.SMOKE, loc, 3, 0.05, 0.05, 0.05, 0.003);
            } catch (Throwable ignored) {
            }
        }, 1L, 1L);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onPrime(ExplosionPrimeEvent e) {
        if (!isDragonsBreathProjectile(e.getEntity())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onExplode(EntityExplodeEvent e) {
        if (!isDragonsBreathProjectile(e.getEntity())) return;
        e.blockList().clear();
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onChangeBlock(EntityChangeBlockEvent e) {
        if (!isDragonsBreathProjectile(e.getEntity())) return;
        e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onHit(ProjectileHitEvent e) {
        if (!(e.getEntity() instanceof Fireball fireball)) return;
        if (!isDragonsBreathProjectile(fireball)) return;

        Player shooter = getShooter(fireball);
        if (shooter == null) {
            fireball.remove();
            return;
        }

        int level = getLevel(fireball);
        Location impact = fireball.getLocation().clone();
        World world = impact.getWorld();
        if (world == null) {
            fireball.remove();
            return;
        }

        fireball.remove();

        try {
            world.spawnParticle(Particle.FLAME, impact, 45, 0.22, 0.22, 0.22, 0.02);
            world.spawnParticle(Particle.LARGE_SMOKE, impact, 18, 0.18, 0.18, 0.18, 0.01);
            world.spawnParticle(Particle.LAVA, impact, 6, 0.10, 0.10, 0.10, 0.0);
            world.playSound(impact, Sound.ENTITY_GENERIC_EXPLODE, 0.85f, 1.35f);
        } catch (Throwable ignored) {
        }

        double radius = CustomItemUtil.dbRadius(level);
        double damage = CustomItemUtil.dbDamage(level);
        int fireTicks = CustomItemUtil.dbFireSeconds(level) * 20;

        for (Entity nearby : world.getNearbyEntities(impact, radius, radius, radius)) {
            if (!(nearby instanceof LivingEntity target)) continue;
            if (target.equals(shooter)) continue;

            if (target instanceof Player tp && isSameFaction(shooter, tp)) continue;

            double dist = target.getLocation().distance(impact);
            if (dist > radius) continue;

            double scaled = damage * Math.max(0.35D, 1.0D - (dist / Math.max(0.1D, radius)));
            target.damage(scaled, shooter);
            target.setFireTicks(Math.max(target.getFireTicks(), fireTicks));
        }

        if (level >= 5) {
            spawnFireField(shooter, impact, 2.75D, 100L);
        }
    }

    private void spawnFireField(Player shooter, Location center, double radius, long durationTicks) {
        World world = center.getWorld();
        if (world == null) return;

        final long start = System.currentTimeMillis();

        Bukkit.getScheduler().runTaskTimer(plugin, task -> {

            // ⏳ STOP nach Zeit
            long lived = System.currentTimeMillis() - start;
            if (lived >= (durationTicks * 50L)) {
                task.cancel();
                return;
            }

            // ❌ wenn Spieler weg → abbrechen
            if (!shooter.isOnline()) {
                task.cancel();
                return;
            }

            // 🔥 VISUAL RING
            for (int i = 0; i < 20; i++) {
                double angle = (Math.PI * 2D) * (i / 20.0D);
                double x = center.getX() + Math.cos(angle) * radius;
                double z = center.getZ() + Math.sin(angle) * radius;

                Location l = new Location(world, x, center.getY() + 0.05D, z);

                try {
                    world.spawnParticle(Particle.FLAME, l, 2, 0.05, 0.02, 0.05, 0.001);
                    world.spawnParticle(Particle.SMOKE, l, 1, 0.03, 0.02, 0.03, 0.001);
                } catch (Throwable ignored) {}
            }

            // 💀 DAMAGE (JETZT FUNKTIONIERT ES AUCH)
            for (Entity nearby : world.getNearbyEntities(center, radius, 1.5D, radius)) {

                if (!(nearby instanceof LivingEntity target)) continue;
                if (target.equals(shooter)) continue;

                if (target instanceof Player tp && isSameFaction(shooter, tp)) continue;

                double dist = target.getLocation().distance(center);
                if (dist > radius) continue;

                target.damage(0.5D, shooter); // ✅ dein gewünschter DOT
                target.setFireTicks(Math.max(target.getFireTicks(), 40));
            }

        }, 0L, 20L); // ⏱ jede Sekunde sauberer Tick
    }

    private boolean isDragonsBreathProjectile(Entity entity) {
        return entity != null && entity.hasMetadata(META_DB);
    }

    private Player getShooter(Fireball fireball) {
        if (fireball.getShooter() instanceof Player p) return p;
        return null;
    }

    private int getLevel(Fireball fireball) {
        if (!fireball.hasMetadata(META_LEVEL)) return 1;
        try {
            return Math.max(1, fireball.getMetadata(META_LEVEL).get(0).asInt());
        } catch (Throwable ignored) {
            return 1;
        }
    }

    private boolean isSameFaction(Player a, Player b) {
        String fa = plugin.getFactionManager().getFactionOf(a.getUniqueId());
        String fb = plugin.getFactionManager().getFactionOf(b.getUniqueId());
        return fa != null && fb != null && fa.equalsIgnoreCase(fb);
    }
}
