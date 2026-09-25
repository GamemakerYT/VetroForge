// src/main/java/com/vetroforge/items/enchants/CustomEnchantListener.java
package com.vetroforge.items.enchants;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomEnchant;
import com.vetroforge.items.CustomItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class CustomEnchantListener implements Listener {
    private final FactionsPlugin plugin;
    public CustomEnchantListener(FactionsPlugin plugin){
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof Player p)) return;
        ItemStack mh = p.getInventory().getItemInMainHand();
        if (CustomItemUtil.baseId(plugin, mh) == null) return;
        e.setDamage(e.getDamage() * (1.0 + CustomItemUtil.damageRollOf(plugin, mh)));

        int rg = CustomItemUtil.getEnchant(plugin, mh, CustomEnchant.RAGE);
        if (rg > 0) {
            e.setDamage(e.getDamage() * (1.0 + 0.03 * rg));
            try { p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SHOOT, .7f, 1.8f);} catch (Throwable ignore) {}
        }

        int sh = CustomItemUtil.getEnchant(plugin, mh, CustomEnchant.SHATTER);
        if (sh > 0 && e.getEntity() instanceof LivingEntity) {
            e.setDamage(e.getDamage() * (1.0 + 0.04 * sh));
        }

        if (e.getEntity() instanceof Player target) {
            int guard = highestEnchant(target, CustomEnchant.GUARD);
            if (guard > 0) {
                e.setDamage(Math.max(0, e.getDamage() * (1.0 - 0.03 * guard)));
            }
        }

        int ls = CustomItemUtil.getEnchant(plugin, mh, CustomEnchant.LIFESTEAL);
        if (ls > 0) {
            heal(p, e.getFinalDamage() * 0.05 * ls);
        }

        int bleed = CustomItemUtil.getEnchant(plugin, mh, CustomEnchant.BLEED);
        if (bleed > 0 && e.getEntity() instanceof LivingEntity target) {
            applyBleed(target, bleed);
        }
    }

    private void applyBleed(LivingEntity target, int level) {
        int ticks = Math.min(4, Math.max(1, level));
        double damage = 0.5 + 0.25 * level;

        for (int tick = 1; tick <= ticks; tick++) {
            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                if (!target.isValid() || target.isDead()) return;
                target.damage(damage);
                try { target.getWorld().playSound(target.getLocation(), Sound.ENTITY_PLAYER_HURT, 0.35f, 0.8f); } catch (Throwable ignored) {}
            }, tick * 20L);
        }
    }

    private void heal(Player player, double amount) {
        double maxHealth = safeMaxHealth(player);
        try {
            AttributeInstance inst = player.getAttribute(Attribute.valueOf("GENERIC_MAX_HEALTH"));
            if (inst != null) maxHealth = inst.getValue();
        } catch (Throwable ignored) {
        }

        player.setHealth(Math.min(maxHealth, player.getHealth() + amount));
        try { player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.8f, 1.4f); } catch (Throwable ignored) {}
    }

    private int highestEnchant(Player player, CustomEnchant enchant) {
        int highest = CustomItemUtil.getEnchant(plugin, player.getInventory().getItemInMainHand(), enchant);
        highest = Math.max(highest, CustomItemUtil.getEnchant(plugin, player.getInventory().getItemInOffHand(), enchant));
        for (ItemStack armor : player.getInventory().getArmorContents()) {
            highest = Math.max(highest, CustomItemUtil.getEnchant(plugin, armor, enchant));
        }
        return highest;
    }

    private double safeMaxHealth(LivingEntity le){
        try { return le.getMaxHealth(); } catch (Throwable t) { return 20.0; }
    }
}
