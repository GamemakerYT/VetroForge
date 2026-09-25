// src/main/java/com/vetroforge/gui/ForgeGui.java
package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

/**
 * Themed front-end for the existing book-apply mechanic (CustomEnchantBookApplyListener):
 * lege ein Item in den markierten Slot, dann ein Enchant-/Fusion-Buch aus dem Inventar
 * auf denselben Slot ziehen, um es anzuwenden. Die eigentliche Anwendungslogik (Regeln,
 * Bestaetigung, Verbrauch) bleibt unveraendert in CustomEnchantBookApplyListener -- diese
 * GUI dupliziert sie nicht, sondern laesst den Klick einfach durchlaufen.
 */
public final class ForgeGui implements Listener {

    private static final int ITEM_SLOT = 22;

    private final FactionsPlugin plugin;

    public ForgeGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new ForgeHolder(), 27, "§d§lForge §8» §7Buch anwenden");

        ItemStack border = pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean edge = row == 0 || row == 2 || col == 0 || col == 8;
            inv.setItem(slot, edge ? border : black);
        }

        inv.setItem(4, info(Material.ANVIL, "§d§lForge",
                "§7Lege ein Schwert, eine Ruestung",
                "§7oder ein Custom-Item in den Slot.",
                "",
                "§7Zieh danach ein §fEnchant-Buch§7 oder",
                "§7§fFusion Book§7 aus deinem Inventar",
                "§7auf denselben Slot.",
                "",
                "§eEin zweites Mal auflegen bestaetigt",
                "§eEnchant-Buecher. Fusion Books wirken sofort."));

        inv.setItem(ITEM_SLOT, null);

        player.openInventory(inv);
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ForgeHolder)) return;
        if (!(event.getWhoClicked() instanceof Player)) return;

        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof ForgeHolder;
        if (!clickedTop) return;

        if (event.getSlot() != ITEM_SLOT) {
            event.setCancelled(true);
        }
        // ITEM_SLOT bleibt unangetastet: normales Ablegen/Entnehmen von Items ist erlaubt,
        // und CustomEnchantBookApplyListener (Prioritaet HIGHEST) uebernimmt die Buch-Logik.
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof ForgeHolder)) return;
        ItemStack leftover = event.getInventory().getItem(ITEM_SLOT);
        if (leftover != null && !leftover.getType().isAir() && event.getPlayer() instanceof Player player) {
            player.getInventory().addItem(leftover).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack info(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static final class ForgeHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
