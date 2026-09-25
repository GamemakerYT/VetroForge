package com.vetroforge.raid.runes;

import com.vetroforge.FactionsPlugin;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.inventory.ItemStack;

public class RuneDamageListener implements Listener {

    private final FactionsPlugin plugin;
    public RuneDamageListener(FactionsPlugin plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onHit(EntityDamageByEntityEvent e) {
        if (!(e.getDamager() instanceof org.bukkit.entity.Player p)) return;
        ItemStack hand = p.getInventory().getItemInMainHand();
        if (RuneUtil.hasRune(plugin, hand, RuneType.RAID_DAMAGE)) {
            double mul = Math.max(1.0, plugin.getConfig().getDouble("runes.bonus.raidDamage", 1.10));
            e.setDamage(e.getDamage() * mul);
        }
    }
}