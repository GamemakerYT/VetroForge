// src/main/java/com/vetroforge/lootdrop/listeners/DropInteractListener.java
package com.vetroforge.lootdrop.listeners;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.lootdrop.LootDropManager;
import com.vetroforge.lootdrop.LockpickGui;
import com.vetroforge.lootdrop.model.ActiveDrop;
import org.bukkit.ChatColor;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

import java.util.UUID;
import com.vetroforge.util.VetroForgeUtil;

public class DropInteractListener implements Listener {

    private final FactionsPlugin plugin;
    private final LootDropManager manager;

    public DropInteractListener(FactionsPlugin plugin, LootDropManager manager){
        this.plugin = plugin;
        this.manager = manager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onInteract(PlayerInteractAtEntityEvent e){
        if (!(e.getRightClicked() instanceof ArmorStand as)) return;

        // Finde zugehörigen Drop
        ActiveDrop found = manager.getActive().stream()
                .filter(d -> d.location().getWorld().equals(as.getWorld())
                        && d.location().distanceSquared(as.getLocation()) < 0.1)
                .findFirst().orElse(null);
        if (found == null) return;

        e.setCancelled(true);
        openDrop(e.getPlayer(), found);
    }

    /**
     * Verhindert, dass die Lootdrop-Kiste als normaler Container geöffnet wird.
     * Ohne diesen Handler kann jeder Spieler die Kiste direkt anklicken und damit
     * Dietrich-Minigame, Fraktions-Exklusivität, Diminishing Returns und den
     * Anti-Doppelclaim-Schutz komplett umgehen (und zusätzlich per ArmorStand-Klick
     * ein zweites Mal Loot bekommen).
     */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onChestInteract(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (e.getHand() != EquipmentSlot.HAND) return;
        var block = e.getClickedBlock();
        if (block == null) return;

        ActiveDrop found = manager.findByChest(block);
        if (found == null) return;

        e.setCancelled(true);
        e.setUseInteractedBlock(Event.Result.DENY);
        e.setUseItemInHand(Event.Result.DENY);
        openDrop(e.getPlayer(), found);
    }

    private void openDrop(Player p, ActiveDrop found) {
        if (found.consumed()) {
            p.sendMessage(tr(p, "lootdrop.already_looted"));
            return;
        }
        if (!found.unlocked()) {
            p.sendMessage(tr(p, "lootdrop.cooling", "seconds", found.unlockSecondsLeft()));
            return;
        }

        // Lockpick-Minigame (bypass per Permission)
        if (p.hasPermission("vetroforge.loot.lockpick.bypass")) {
            manager.tryOpen(p, found.id());
        } else {
            new LockpickGui(plugin, result -> {
                if (result) manager.tryOpen(p, found.id());
                else p.sendMessage(tr(p, "lootdrop.lockpick_failed"));
            }).open(p);
        }
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, key, VetroForgeUtil.vars(values));
    }
}
