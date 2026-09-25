// src/main/java/com/vetroforge/items/cmd/CustomItemsCommand.java
package com.vetroforge.items.cmd;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomItemCatalog;
import com.vetroforge.items.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class CustomItemsCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String PERM_VIEW = "vetroforge.customitems";
    private static final String PERM_GIVE = "vetroforge.givecustom";
    private static final String TITLE = ChatColor.DARK_PURPLE + "Custom Items";

    private final FactionsPlugin plugin;
    private final NamespacedKey KEY_ID;
    private final NamespacedKey KEY_PREVIEW_RAR;

    public CustomItemsCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.KEY_ID = new NamespacedKey(plugin, "ci_preview_id");
        this.KEY_PREVIEW_RAR = new NamespacedKey(plugin, "ci_preview_rarity");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("§cNur ingame.");
            return true;
        }

        if (!p.hasPermission(PERM_VIEW)) {
            p.sendMessage("§cKeine Rechte.");
            return true;
        }

        openList(p);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] a) {
        return Collections.emptyList();
    }

    private void openList(Player p) {
        Inventory inv = Bukkit.createInventory(p, 54, TITLE);
        fill(inv);

        int[] slots = {
                1, 2, 3, 4, 5, 6, 7,
                10, 11, 12, 13, 14, 15, 16,
                19, 20, 21, 22, 23, 24, 25,
                28, 29, 30, 31, 32, 33, 34,
                37, 38, 39, 40, 41, 42, 43,
                46, 47, 48, 49, 50, 51, 52,
                0, 8
        };
        int i = 0;

        for (CustomItemCatalog.Entry entry : CustomItemCatalog.all()) {
            if (i >= slots.length) break;

            Rarity preview = entry.hasRarity ? Rarity.RARE : null;
            ItemStack previewItem = buildPreview(entry, preview);
            inv.setItem(slots[i++], action(previewItem, entry.id, preview));
        }

        p.openInventory(inv);
        try {
            p.playSound(p.getLocation(), Sound.BLOCK_ENCHANTMENT_TABLE_USE, 1f, 1.1f);
        } catch (Throwable ignored) {
        }
    }

    private ItemStack buildPreview(CustomItemCatalog.Entry def, Rarity previewRarity) {
        ItemStack it = def.factory.make(plugin, def.hasRarity ? (previewRarity == null ? Rarity.RARE : previewRarity) : null);
        ItemMeta im = it.getItemMeta();

        List<String> lore = (im != null && im.hasLore() && im.getLore() != null)
                ? new ArrayList<>(im.getLore())
                : new ArrayList<>();

        lore.add(" ");
        lore.add("§8» §7ID: §f" + def.id);

        if (def.hasRarity) {
            lore.add("§8» §7Rarity: " + (previewRarity == null ? Rarity.RARE.color + Rarity.RARE.display : previewRarity.color + previewRarity.display));
            lore.add("§8» §7Rechtsklick §f= §7Rarity wechseln");
        }

        lore.add("§8» §7Linksklick §f= §7Preview erhalten");

        if (im != null) {
            im.setLore(lore);
            it.setItemMeta(im);
        }

        return it;
    }

    private ItemStack action(ItemStack item, String id) {
        return action(item, id, null);
    }

    private ItemStack action(ItemStack item, String id, Rarity rarity) {
        if (item == null) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return item;

        meta.getPersistentDataContainer().set(KEY_ID, PersistentDataType.STRING, id);

        if (rarity != null) {
            meta.getPersistentDataContainer().set(KEY_PREVIEW_RAR, PersistentDataType.STRING, rarity.name());
        } else {
            meta.getPersistentDataContainer().remove(KEY_PREVIEW_RAR);
        }

        item.setItemMeta(meta);
        return item;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!TITLE.equals(e.getView().getTitle())) return;
        if (e.getRawSlot() >= e.getInventory().getSize()) return;

        e.setCancelled(true);

        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;

        ItemMeta im = clicked.getItemMeta();
        String id = im.getPersistentDataContainer().get(KEY_ID, PersistentDataType.STRING);
        if (id == null) return;

        CustomItemCatalog.Entry entry = CustomItemCatalog.all().stream()
                .filter(en -> en.id.equalsIgnoreCase(id))
                .findFirst()
                .orElse(null);

        if (entry == null) return;

        Rarity current = null;
        if (entry.hasRarity) {
            try {
                String raw = im.getPersistentDataContainer().get(KEY_PREVIEW_RAR, PersistentDataType.STRING);
                current = raw == null ? Rarity.RARE : Rarity.valueOf(raw.toUpperCase(Locale.ROOT));
            } catch (Exception ignored) {
                current = Rarity.RARE;
            }
        }

        if (e.getClick() == ClickType.RIGHT && entry.hasRarity) {
            Rarity next = nextRarity(current);
            ItemStack updated = action(buildPreview(entry, next), entry.id, next);
            e.getInventory().setItem(e.getRawSlot(), updated);

            try {
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
            } catch (Throwable ignored) {
            }
            return;
        }

        if (e.getClick() == ClickType.LEFT) {
            if (!p.hasPermission(PERM_GIVE)) {
                p.sendMessage("§cKeine Rechte zum Geben.");
                try {
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.8f);
                } catch (Throwable ignored) {
                }
                return;
            }

            ItemStack give = entry.factory.make(plugin, entry.hasRarity ? (current == null ? Rarity.RARE : current) : null);
            p.getInventory().addItem(give);

            try {
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.2f);
            } catch (Throwable ignored) {
            }

            p.sendMessage("§aDu hast §f1x §a" + entry.display
                    + (entry.hasRarity ? " §8(§f" + (current == null ? Rarity.RARE.display : current.display) + "§8)" : "")
                    + " §aerhalten.");
        }
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            pane.setItemMeta(meta);
        }

        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    private Rarity nextRarity(Rarity current) {
        if (current == null) return Rarity.RARE;

        return switch (current) {
            case COMMON -> Rarity.RARE;
            case RARE -> Rarity.EPIC;
            case EPIC -> Rarity.LEGENDARY;
            case LEGENDARY -> Rarity.MYSTIC;
            case MYSTIC -> Rarity.COMMON;
        };
    }
}
