package com.vetroforge.level;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

public final class LevelXpBottleListener implements Listener {

    private final FactionsPlugin plugin;
    private final NamespacedKey xpBottleKey;

    public LevelXpBottleListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.xpBottleKey = new NamespacedKey(plugin, "level_xp_bottle");
    }

    @EventHandler
    public void onUse(PlayerInteractEvent event) {
        Action action = event.getAction();
        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null || item.getType() != Material.EXPERIENCE_BOTTLE) return;

        Player player = event.getPlayer();
        event.setCancelled(true);
        item.setAmount(item.getAmount() - 1);

        int xp = plugin.getConfig().getInt("level.xpBottle", 15);
        if (item.hasItemMeta()) {
            Integer customXp = item.getItemMeta().getPersistentDataContainer().get(xpBottleKey, PersistentDataType.INTEGER);
            if (customXp != null && customXp > 0) xp = customXp;
        }
        plugin.getLevelProgressionService().addXp(player, xp, "XP-Flasche");
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.35f);
        player.spawnParticle(Particle.ENCHANT, player.getLocation().add(0, 1.1, 0), 28, 0.45, 0.6, 0.45, 0.02);
    }
}
