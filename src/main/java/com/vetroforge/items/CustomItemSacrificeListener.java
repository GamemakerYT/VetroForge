// src/main/java/com/vetroforge/items/CustomItemSacrificeListener.java
package com.vetroforge.items;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.ItemStack;

/**
 * Direktes Sacrifice-Leveling: ein Duplikat eines Custom-Items wird im eigenen
 * Inventar auf das Ziel-Item gezogen (kein GUI, kein Command -- analog zum
 * bestehenden CustomEnchantBookApplyListener fuer Enchant-Buecher). Jedes
 * Duplikat erhoeht den Fortschritt; sobald genug Sacrifices zusammen sind,
 * kostet der Level-Up zusaetzlich Coins (siehe CustomItemUtil.sacrificeOnto).
 */
public final class CustomItemSacrificeListener implements Listener {

    private final FactionsPlugin plugin;

    public CustomItemSacrificeListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || !event.getClickedInventory().equals(player.getInventory())) return;

        ItemStack cursor = event.getCursor();
        ItemStack target = event.getCurrentItem();
        if (cursor == null || cursor.getType().isAir() || target == null || target.getType().isAir()) return;
        if (cursor.getAmount() != 1) return;

        String cursorBase = CustomItemUtil.baseId(plugin, cursor);
        String targetBase = CustomItemUtil.baseId(plugin, target);
        if (cursorBase == null || !cursorBase.equals(targetBase)) return;
        if (CustomItemUtil.rarityOf(plugin, cursor) != CustomItemUtil.rarityOf(plugin, target)) return;

        event.setCancelled(true);

        ItemStack workingTarget = target.clone();
        CustomItemUtil.SacrificeResult result = CustomItemUtil.sacrificeOnto(plugin, player, workingTarget);

        switch (result) {
            case MAX_LEVEL -> player.sendMessage("§cDieses Item hat bereits das Max-Level erreicht.");
            case INSUFFICIENT_COINS -> {
                int lvl = CustomItemUtil.levelOf(plugin, target);
                int cost = CustomItemUtil.levelUpCost(plugin, lvl);
                player.sendMessage("§cNicht genug Coins fuer das Level-Up. Benoetigt: §6" + cost + " Coins§c.");
            }
            case PROGRESS -> {
                event.setCurrentItem(workingTarget);
                consumeOneFromCursor(event, cursor);
            }
            case LEVEL_UP -> {
                event.setCurrentItem(workingTarget);
                consumeOneFromCursor(event, cursor);
                try { player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1f); } catch (Throwable ignored) {}
                player.sendMessage("§6Forge §8» §aLevel-Up! Neues Level: §f" + CustomItemUtil.levelOf(plugin, workingTarget));
            }
        }
    }

    private void consumeOneFromCursor(InventoryClickEvent event, ItemStack cursor) {
        if (cursor.getAmount() <= 1) {
            event.setCursor(null);
        } else {
            cursor.setAmount(cursor.getAmount() - 1);
            event.setCursor(cursor);
        }
    }
}
