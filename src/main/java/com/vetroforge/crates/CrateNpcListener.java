// src/main/java/com/vetroforge/crates/CrateNpcListener.java
package com.vetroforge.crates;

import com.vetroforge.FactionsPlugin;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class CrateNpcListener implements Listener {
    private final FactionsPlugin plugin;
    private final CrateNpcManager mgr;
    private final Map<UUID, Long> lastClick = new HashMap<>();

    public CrateNpcListener(FactionsPlugin plugin, CrateNpcManager mgr) {
        this.plugin = plugin;
        this.mgr = mgr;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractAtEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        handleOpen(e.getRightClicked(), e.getPlayer(), e);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClickCompat(PlayerInteractEntityEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        handleOpen(e.getRightClicked(), e.getPlayer(), e);
    }

    private void handleOpen(Entity clicked, Player p, Cancellable ev) {
        if (!mgr.isCrateNpc(clicked)) return;
        ev.setCancelled(true); // verhindert Villager-Trades

        long now = System.currentTimeMillis();
        if (now - lastClick.getOrDefault(p.getUniqueId(), 0L) < 900L) return;
        lastClick.put(p.getUniqueId(), now);
        plugin.getKeyCrateManager().redeemHeldKey(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        if (mgr.isCrateNpc(e.getEntity())) e.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage2(EntityDamageByEntityEvent e) {
        if (mgr.isCrateNpc(e.getEntity())) e.setCancelled(true);
    }

    // Quality of life: nach Chunk-Load sicherstellen, dass Name sichtbar bleibt
    @EventHandler
    public void onChunkLoad(ChunkLoadEvent e) {
        for (Entity en : e.getChunk().getEntities()) {
            if (mgr.isCrateNpc(en)) {
                try { en.setCustomNameVisible(true); } catch (Throwable ignored) {}
            }
        }
    }
}
