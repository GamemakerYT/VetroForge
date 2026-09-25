// src/main/java/com/vetroforge/manager/FactionShopManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class FactionShopManager implements Listener {

    private final FactionsPlugin plugin;
    private final NamespacedKey KEY;
    public FactionShopManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "fshop_item");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        Set<String> ids = getItemIds();
        if (ids.isEmpty()) {
            p.sendMessage(tr(p, "empty"));
            return;
        }

        Inventory inv = Bukkit.createInventory(new Holder(), 27, tr(p, "title"));

        int slot = 10;
        for (String id : ids) {
            inv.setItem(slot++, buildItem(p, id));
        }

        p.openInventory(inv);
    }
    public void listItems(Player p) {
        Set<String> ids = getItemIds();

        p.sendMessage(tr(p, "list_title"));

        if (ids.isEmpty()) {
            p.sendMessage(tr(p, "no_items"));
            return;
        }

        for (String id : ids) {
            p.sendMessage("§7- §f" + id);
        }
    }
    private ItemStack buildItem(Player player, String id) {
        String base = "factions.shop.items." + id + ".";
        Material mat = Material.matchMaterial(plugin.getConfig().getString(base + "icon", "PAPER"));
        String name = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString(base + "name", id));
        int price = plugin.getConfig().getInt(base + "price", 0);

        ItemStack it = new ItemStack(mat == null ? Material.PAPER : mat);
        ItemMeta im = it.getItemMeta();

        im.setDisplayName("§e" + name);
        im.setLore(List.of(tr(player, "price", "price", price), tr(player, "click_buy")));

        im.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, id);
        it.setItemMeta(im);
        return it;
    }

    @EventHandler
    public void click(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;

        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String id = it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (id == null) return;

        buy(p, id);
    }

    public void buy(Player p, String id) {
        String base = "factions.shop.items." + id + ".";
        int price = plugin.getConfig().getInt(base + "price", 0);

        int coins = plugin.getCoinsManager().getCoins(p.getUniqueId());

        if (coins < price) {
            p.sendMessage(tr(p, "not_enough"));
            return;
        }

        plugin.getCoinsManager().removeCoins(p.getUniqueId(), price);

        for (String cmd : plugin.getConfig().getStringList(base + "commands")) {
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(),
                    cmd.replace("{PLAYER}", p.getName()));
        }

        p.sendMessage(tr(p, "purchased", "item", id, "price", price));
    }

    public Set<String> getItemIds() {
        if (!plugin.getConfig().isConfigurationSection("factions.shop.items"))
            return Collections.emptySet();
        return plugin.getConfig().getConfigurationSection("factions.shop.items").getKeys(false);
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, "faction.shop." + key, VetroForgeUtil.vars(values));
    }

    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
