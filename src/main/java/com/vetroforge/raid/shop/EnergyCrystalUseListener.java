package com.vetroforge.raid.shop;

import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public class EnergyCrystalUseListener implements Listener {

    private final FactionsPlugin plugin;
    private final EnergyCrystalItems items;
    private final EnergyCrystalBridge bridge;

    public EnergyCrystalUseListener(FactionsPlugin plugin, EnergyCrystalItems items, EnergyCrystalBridge bridge) {
        this.plugin = plugin;
        this.items = items;
        this.bridge = bridge;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onUse(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND) return;
        ItemStack it = e.getItem();
        if (!items.isCrystal(it)) return;

        Player p = e.getPlayer();
        int per = items.valueOf(it);
        int stack = it.getAmount();

        if (depositToRaidBank(p, it, per, stack)) {
            e.setCancelled(true);
            return;
        }

        EnergyCrystalBridge.Target def = targetFromCfg(plugin.getConfig().getString("raid.crystal.deposit.defaultTarget", "PLAYER"));
        EnergyCrystalBridge.Target sne = targetFromCfg(plugin.getConfig().getString("raid.crystal.deposit.sneakTarget", "FACTION"));
        EnergyCrystalBridge.Target target = p.isSneaking() ? sne : def;

        int depositCount = (p.isSneaking() && plugin.getConfig().getBoolean("raid.crystal.deposit.sneakAll", true)) ? stack : 1;
        int total = per * depositCount;

        boolean ok;
        String msg;
        if (target == EnergyCrystalBridge.Target.FACTION) {
            String fac = plugin.getFactionManager().getFactionOf(p.getUniqueId());
            if (fac == null) {
                // Fallback auf Spieler
                ok = bridge.depositToPlayer(p.getUniqueId(), total);
                Integer bal = bridge.getPlayerBalance(p.getUniqueId());
                msg = ChatColor.GREEN + "Eingezahlt (Spieler): " + ChatColor.WHITE + total + " ✦"
                        + ChatColor.GRAY + (bal != null ? "  (Kontostand: " + ChatColor.WHITE + bal + " ✦" + ChatColor.GRAY + ")" : "");
            } else {
                ok = bridge.depositToFaction(fac, total);
                Integer bal = bridge.getFactionBalance(fac);
                msg = ChatColor.GREEN + "Eingezahlt (Faction " + fac + "): " + ChatColor.WHITE + total + " ✦"
                        + ChatColor.GRAY + (bal != null ? "  (Kontostand: " + ChatColor.WHITE + bal + " ✦" + ChatColor.GRAY + ")" : "");
            }
        } else {
            ok = bridge.depositToPlayer(p.getUniqueId(), total);
            Integer bal = bridge.getPlayerBalance(p.getUniqueId());
            msg = ChatColor.GREEN + "Eingezahlt: " + ChatColor.WHITE + total + " ✦"
                    + ChatColor.GRAY + (bal != null ? "  (Kontostand: " + ChatColor.WHITE + bal + " ✦" + ChatColor.GRAY + ")" : "");
        }

        if (ok) {
            // Items verbrauchen
            if (depositCount >= stack) {
                p.getInventory().setItemInMainHand(null);
            } else {
                it.setAmount(stack - depositCount);
            }
            p.sendMessage(msg);
            try { p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f); } catch (Throwable ignored) {}
        } else {
            p.sendMessage(ChatColor.RED + "Einzahlung fehlgeschlagen – kein kompatibler Manager gefunden?");
        }
        e.setCancelled(true);
    }

    private boolean depositToRaidBank(Player player, ItemStack item, int perItem, int stackAmount) {
        if (player == null || item == null || plugin.getRaidCrystalManager() == null) return false;
        if (!plugin.getRaidCrystalManager().useFactionSource()) return false;
        if (plugin.getFactionManager() == null) return false;

        String faction = plugin.getFactionManager().getFactionOf(player.getUniqueId());
        if (faction == null || faction.isBlank()) {
            player.sendActionBar("§cKeine Faction §8• §7Shard nicht eingezahlt");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
            return true;
        }

        int before = plugin.getRaidCrystalManager().getCrystals(faction);
        int cap = plugin.getRaidCrystalManager().getCrystalCap(faction);
        int free = Math.max(0, cap - before);
        int requestedItems = player.isSneaking()
                && plugin.getConfig().getBoolean("raid.crystal.deposit.sneakAll", true)
                ? stackAmount
                : 1;
        int acceptedItems = Math.min(requestedItems, free / Math.max(1, perItem));
        if (acceptedItems <= 0) {
            player.sendActionBar("§dRaid Bank §8• §cVoll §8(" + before + "/" + cap + "§8)");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.7f);
            return true;
        }

        int total = acceptedItems * Math.max(1, perItem);
        plugin.getRaidCrystalManager().addForFaction(faction, total);
        if (acceptedItems >= stackAmount) {
            player.getInventory().setItemInMainHand(null);
        } else {
            item.setAmount(stackAmount - acceptedItems);
        }

        int after = Math.min(cap, before + total);
        player.sendActionBar("§d+" + total + " Shards §8• §fBank §d" + after + "§8/§d" + cap);
        player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.25f);
        return true;
    }

    private EnergyCrystalBridge.Target targetFromCfg(String s) {
        if (s == null) return EnergyCrystalBridge.Target.PLAYER;
        s = s.trim().toUpperCase();
        return s.contains("FACTION") ? EnergyCrystalBridge.Target.FACTION : EnergyCrystalBridge.Target.PLAYER;
    }
}
