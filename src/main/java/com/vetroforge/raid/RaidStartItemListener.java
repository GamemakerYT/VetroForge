// src/main/java/com/vetroforge/raid/RaidStartItemListener.java
package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.raid.energy.RaidSessionService;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public class RaidStartItemListener implements Listener {

    private final FactionsPlugin plugin;

    public RaidStartItemListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = false)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;

        Action action = e.getAction();

        if (action != Action.RIGHT_CLICK_AIR && action != Action.RIGHT_CLICK_BLOCK) {
            return;
        }

        Player p = e.getPlayer();

        ItemStack item = e.getItem();

        if (item == null || item.getType() == Material.AIR) {
            item = p.getInventory().getItemInMainHand();
        }

        if (item == null || item.getType() == Material.AIR) return;

        if (plugin.getRaidSessionService() == null) {
            RaidText.send(plugin, p, "message.session_unavailable");
            return;
        }

        RaidSessionService session = plugin.getRaidSessionService();
        String sessionAction = session.getSessionItemAction(item);

        if (session.isScouting(p) && sessionAction != null) {
            e.setCancelled(true);

            switch (sessionAction.toLowerCase()) {
                case RaidSessionService.ACTION_NEXT -> {
                    session.nextTarget(p);
                    p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.4f);
                }

                case RaidSessionService.ACTION_START -> {
                    boolean started = session.startRaidFromScouting(p);

                    if (started) {
                        p.sendTitle(RaidText.tr(plugin, p, "title.prepared"), RaidText.tr(plugin, p, "title.join"), 5, 50, 10);
                        p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
                    }
                }

                case RaidSessionService.ACTION_CANCEL -> {
                    session.stopScouting(p);
                    p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.4f);
                }
            }

            return;
        }

        if (!isLegacyRaidStartItem(item)) return;

        e.setCancelled(true);

        if (session.isScouting(p)) {
            RaidText.send(plugin, p, "message.already_scouting");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 0.7f);
            return;
        }

        if (plugin.getFactionManager() == null) {
            RaidText.send(plugin, p, "message.faction_manager_unavailable");
            return;
        }

        if (plugin.getEnergyManager() == null) {
            RaidText.send(plugin, p, "message.energy_manager_unavailable");
            return;
        }

        if (plugin.getRaidTargetService() == null) {
            RaidText.send(plugin, p, "message.target_service_unavailable");
            return;
        }

        if (plugin.getRaidCrystalManager() == null) {
            RaidText.send(plugin, p, "message.crystal_manager_unavailable");
            return;
        }

        String faction = plugin.getFactionManager().getFactionOf(p.getUniqueId());

        if (faction == null || faction.isBlank()) {
            RaidText.send(plugin, p, "message.no_faction");
            return;
        }

        int energy = getEnergy(p);

        if (energy < 3) {
            RaidText.send(plugin, p, "message.not_enough_energy", "amount", 3);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 0.7f);
            return;
        }

        String target = plugin.getRaidTargetService().getRandomTarget(faction);

        if (target == null || target.isBlank()) {
            RaidText.send(plugin, p, "message.no_targets");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 0.7f);
            return;
        }

        if (!session.canScoutTarget(target)) {
            RaidText.send(plugin, p, "message.target_without_spawn");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 0.7f);
            return;
        }

        if (!consumeEnergy(p, 3)) {
            RaidText.send(plugin, p, "message.energy_charge_failed");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 0.7f);
            return;
        }

        if (!plugin.getRaidCrystalManager().consume(p, 3)) {
            refundEnergy(p, 3);

            RaidText.send(plugin, p, "message.not_enough_crystals", "amount", 3);
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 0.7f);
            return;
        }

        boolean started = session.startScouting(p, target);

        if (!started) {
            refundEnergy(p, 3);
            plugin.getRaidCrystalManager().refund(p, 3);

            RaidText.send(plugin, p, "message.scouting_failed_refunded");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1, 0.7f);
            return;
        }

        p.sendTitle(RaidText.tr(plugin, p, "title.scouting"), RaidText.tr(plugin, p, "title.target", "target", target), 5, 40, 5);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_EYE_LAUNCH, 1, 1);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrop(PlayerDropItemEvent e) {
        if (plugin.getRaidSessionService() == null) return;

        ItemStack item = e.getItemDrop().getItemStack();

        if (plugin.getRaidSessionService().isRaidSessionItem(item) || isLegacyRaidStartItem(item)) {
            e.setCancelled(true);
            RaidText.send(plugin, e.getPlayer(), "message.item_no_drop");
            e.getPlayer().playSound(e.getPlayer().getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.8f);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSwap(PlayerSwapHandItemsEvent e) {
        if (plugin.getRaidSessionService() == null) return;

        if (plugin.getRaidSessionService().isRaidSessionItem(e.getMainHandItem())
                || plugin.getRaidSessionService().isRaidSessionItem(e.getOffHandItem())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (plugin.getRaidSessionService() == null) return;

        ItemStack current = e.getCurrentItem();
        ItemStack cursor = e.getCursor();

        if (plugin.getRaidSessionService().isRaidSessionItem(current)
                || plugin.getRaidSessionService().isRaidSessionItem(cursor)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onInventoryDrag(InventoryDragEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        if (plugin.getRaidSessionService() == null) return;

        ItemStack cursor = e.getOldCursor();

        if (plugin.getRaidSessionService().isRaidSessionItem(cursor)) {
            e.setCancelled(true);
        }
    }

    private boolean isLegacyRaidStartItem(ItemStack item) {
        if (item == null || item.getType() != Material.NETHERITE_SWORD) return false;
        if (!item.hasItemMeta()) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null || !meta.hasDisplayName()) return false;

        String name = meta.getDisplayName();
        return name != null && name.contains("Raid starten");
    }

    private int getEnergy(Player player) {
        var manager = plugin.getEnergyManager();
        return manager == null ? 0 : manager.getEnergy(player);
    }

    private boolean consumeEnergy(Player player, int amount) {
        if (player == null || amount <= 0) return false;

        var manager = plugin.getEnergyManager();
        if (manager == null) return false;

        return manager.removeEnergy(player, amount);
    }

    private void refundEnergy(Player player, int amount) {
        if (player == null || amount <= 0) return;

        var manager = plugin.getEnergyManager();
        if (manager == null) return;

        manager.add(player.getUniqueId(), amount);
    }

}
