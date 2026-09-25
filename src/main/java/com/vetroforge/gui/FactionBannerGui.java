package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public final class FactionBannerGui implements Listener {

    private static final List<Material> BANNERS = List.of(
            Material.RED_BANNER, Material.ORANGE_BANNER, Material.YELLOW_BANNER,
            Material.LIME_BANNER, Material.CYAN_BANNER, Material.BLUE_BANNER,
            Material.PURPLE_BANNER, Material.WHITE_BANNER, Material.BLACK_BANNER
    );

    private final FactionsPlugin plugin;
    private final NamespacedKey bannerKey;

    public FactionBannerGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.bannerKey = new NamespacedKey(plugin, "faction_banner");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        String faction = factionOf(player);
        if (faction == null) {
            player.sendMessage(tr(player, "no_faction"));
            return;
        }

        Inventory inventory = Bukkit.createInventory(new Holder(), 27, tr(player, "title"));
        VetroForgeUtil.fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        for (int index = 0; index < BANNERS.size(); index++) {
            inventory.setItem(9 + index, banner(player, BANNERS.get(index)));
        }
        String symbol = plugin.getFactionBannerManager().getSymbol(faction);
        inventory.setItem(22, VetroForgeUtil.item(Material.NAME_TAG, "§e§lBanner-Zeichen",
                "§7Aktuell: §f" + (symbol.isBlank() ? "-" : symbol),
                "§7Setzen: §f/f banner symbol <A-Z|0-9>",
                "§7Entfernen: §f/f banner symbol none"));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String raw = clicked.getItemMeta().getPersistentDataContainer().get(bannerKey, PersistentDataType.STRING);
        if (raw == null) return;

        String faction = factionOf(player);
        if (faction == null || !plugin.getFactionManager().isLeader(player.getUniqueId())) {
            player.sendMessage(tr(player, "leader_only"));
            return;
        }

        int cost = Math.max(0, plugin.getConfig().getInt("factions.banner.cost", 250));
        int coins = plugin.getCoinsManager().getCoins(player.getUniqueId());
        if (coins < cost) {
            player.sendMessage(tr(player, "not_enough", "cost", cost));
            return;
        }

        Material material = Material.matchMaterial(raw);
        if (material == null) return;
        plugin.getCoinsManager().removeCoins(player.getUniqueId(), cost);
        plugin.getFactionBannerManager().setBanner(faction, material);
        player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.1f);
        player.sendMessage(tr(player, "purchased", "cost", cost));
        plugin.getFactionMenuManager().openMain(player);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private ItemStack banner(Player player, Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(tr(player, "option", "color", pretty(material)));
        meta.setLore(List.of(tr(player, "price", "cost", Math.max(0, plugin.getConfig().getInt("factions.banner.cost", 250)))));
        meta.getPersistentDataContainer().set(bannerKey, PersistentDataType.STRING, material.name());
        item.setItemMeta(meta);
        return item;
    }

    private String factionOf(Player player) {
        return plugin.getFactionManager().getFactionOf(player.getUniqueId());
    }

    private String pretty(Material material) {
        return material.name().replace("_BANNER", "").replace('_', ' ');
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, "faction.banner." + key, VetroForgeUtil.vars(values));
    }

    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
