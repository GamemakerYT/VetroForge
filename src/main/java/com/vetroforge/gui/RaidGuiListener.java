// src/main/java/com/vetroforge/gui/RaidGuiListener.java
package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

import com.vetroforge.raid.RaidText;

public class RaidGuiListener implements Listener {

    private final FactionsPlugin plugin;

    public RaidGuiListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getInventory().getHolder() instanceof RaidGui.Holder)) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        int slot = e.getRawSlot();

        switch (slot) {

            case 10 -> {
                p.closeInventory();
                runPrimaryAction(p);
            }

            case 13 -> {
                p.closeInventory();
                cancelRaidOrScouting(p);
            }

            case 16 -> {
                p.closeInventory();
                p.performCommand("raidshop");
            }
        }
    }

    private void runPrimaryAction(Player p) {
        if (plugin.getRaidSessionService() == null) {
            RaidText.send(plugin, p, "message.session_unavailable");
            return;
        }

        if (plugin.getRaidSessionService().isScouting(p)) {
            if (plugin.getRaidSessionService().startRaidFromScouting(p)) {
                p.sendTitle(RaidText.tr(plugin, p, "title.prepared"), RaidText.tr(plugin, p, "title.join"), 5, 50, 10);
                p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f);
            } else {
                fail(p);
            }
            return;
        }

        if (plugin.getRaidSessionService().isInRaid(p)) {
            if (plugin.getRaidManager() != null) {
                plugin.getRaidManager().sendStats(p);
            }
            return;
        }

        String faction = getFaction(p);
        String target = faction == null ? null : plugin.getRaidSessionService().getActiveRaidTargetForFaction(faction);
        if (target != null && !target.isBlank() && !plugin.getRaidSessionService().isInRaid(p)) {
            if (plugin.getRaidJoinService() == null) {
                RaidText.send(plugin, p, "message.join_unavailable");
                fail(p);
                return;
            }
            plugin.getRaidJoinService().joinRaid(p, target);
            return;
        }

        startRaidScouting(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof RaidGui.Holder) {
            e.setCancelled(true);
        }
    }

    private void startRaidScouting(Player p) {
        if (plugin.getRaidSessionService() == null) {
            RaidText.send(plugin, p, "message.session_unavailable");
            return;
        }

        if (plugin.getRaidSessionService().isScouting(p)) {
            RaidText.send(plugin, p, "message.already_scouting");
            fail(p);
            return;
        }

        if (plugin.getRaidSessionService().isInRaid(p)) {
            RaidText.send(plugin, p, "message.already_raiding");
            fail(p);
            return;
        }

        String faction = getFaction(p);

        if (faction == null || faction.isBlank()) {
            RaidText.send(plugin, p, "message.no_faction");
            fail(p);
            return;
        }

        int energy = getEnergy(p);

        if (energy < 3) {
            RaidText.send(plugin, p, "message.not_enough_energy", "amount", 3);
            fail(p);
            return;
        }

        String target = getRandomTarget(faction);

        if (target == null || target.isBlank()) {
            RaidText.send(plugin, p, "message.no_targets");
            fail(p);
            return;
        }

        if (!plugin.getRaidSessionService().canScoutTarget(target)) {
            RaidText.send(plugin, p, "message.target_without_spawn");
            fail(p);
            return;
        }

        if (!consumeEnergy(p, 3)) {
            RaidText.send(plugin, p, "message.energy_charge_failed");
            fail(p);
            return;
        }

        if (!consumeCrystals(p, 3)) {
            refundEnergy(p, 3);

            RaidText.send(plugin, p, "message.not_enough_crystals", "amount", 3);
            fail(p);
            return;
        }

        boolean started = plugin.getRaidSessionService().startScouting(p, target);

        if (!started) {
            refundEnergy(p, 3);
            refundCrystals(p, 3);

            RaidText.send(plugin, p, "message.scouting_failed_refunded");
            fail(p);
            return;
        }

        p.sendTitle(RaidText.tr(plugin, p, "title.scouting"), RaidText.tr(plugin, p, "title.target", "target", target), 5, 40, 5);
        p.playSound(p.getLocation(), Sound.ENTITY_ENDER_EYE_LAUNCH, 1.0f, 1.0f);
    }

    private void cancelRaidOrScouting(Player p) {
        if (plugin.getRaidSessionService() == null) {
            RaidText.send(plugin, p, "message.session_unavailable");
            return;
        }

        if (plugin.getRaidSessionService().isScouting(p)) {
            plugin.getRaidSessionService().stopScouting(p);
            p.playSound(p.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.4f, 1.4f);
            return;
        }

        if (plugin.getRaidSessionService().isInRaid(p)) {
            p.performCommand("raid stop");
            return;
        }

        RaidText.send(plugin, p, "message.nothing_active");
        fail(p);
    }

    private String getFaction(Player p) {
        try {
            if (plugin.getFactionManager() == null) return null;
            return plugin.getFactionManager().getFactionOf(p.getUniqueId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String getRandomTarget(String faction) {
        try {
            if (plugin.getRaidTargetService() != null) {
                return plugin.getRaidTargetService().getRandomTarget(faction);
            }
        } catch (Throwable ignored) {}

        try {
            if (plugin.getRaidManager() != null) {
                return plugin.getRaidManager().getRandomTarget(faction);
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private int getEnergy(Player p) {
        var manager = plugin.getEnergyManager();
        return manager == null ? 0 : manager.getEnergy(p);
    }

    private boolean consumeEnergy(Player p, int amount) {
        if (p == null || amount <= 0) return false;

        var manager = plugin.getEnergyManager();
        if (manager == null) return false;

        return manager.removeEnergy(p, amount);
    }

    private void refundEnergy(Player p, int amount) {
        if (p == null || amount <= 0) return;

        var manager = plugin.getEnergyManager();
        if (manager == null) return;

        manager.add(p.getUniqueId(), amount);
    }

    private boolean consumeCrystals(Player player, int amount) {
        return plugin.getRaidCrystalManager() != null
                && plugin.getRaidCrystalManager().consume(player, amount);
    }

    private void refundCrystals(Player player, int amount) {
        if (plugin.getRaidCrystalManager() != null) {
            plugin.getRaidCrystalManager().refund(player, amount);
        }
    }

    private void fail(Player p) {
        try {
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
        } catch (Throwable ignored) {}
    }
}
