// src/main/java/com/vetroforge/manager/GeneralShopManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;

import java.util.HashMap;
import java.util.Map;

public class GeneralShopManager implements Listener {

    private final FactionsPlugin plugin;

    public GeneralShopManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }
// ===================== LOGGING =====================

    private final Map<String,Integer> recentBuys  = new HashMap<>();
    private final Map<String,Integer> recentSells = new HashMap<>();

    public void logBuy(String id) {
        if (id != null) recentBuys.merge(id, 1, Integer::sum);
    }

    public void logBuy(String id, int amount) {
        if (id != null) recentBuys.merge(id, amount, Integer::sum);
    }

    public void logSell(String id) {
        if (id != null) recentSells.merge(id, 1, Integer::sum);
    }

    public void logSell(String id, int amount) {
        if (id != null) recentSells.merge(id, amount, Integer::sum);
    }

    /**
     * @param sellPrices true = sells, false = buys
     */
    public Map<String,Integer> drainRecent(boolean sellPrices){
        Map<String,Integer> src = sellPrices ? recentSells : recentBuys;
        Map<String,Integer> out = new HashMap<>(src);
        src.clear();
        return out;
    }
    public void openSell(Player p){
        Inventory inv = Bukkit.createInventory(null, 54, "§6Verkauf");

        for (ItemStack item : p.getInventory().getContents()) {
            if (item == null) continue;
            inv.addItem(item.clone());
        }

        p.openInventory(inv);
    }
    public void buy(Player p, Material mat, int price) {

        int coins = plugin.getCoinsManager().getCoins(p.getUniqueId());

        if (coins < price) {
            p.sendMessage("§cNicht genug Coins.");
            return;
        }

        plugin.getCoinsManager().removeCoins(p.getUniqueId(), price);

        p.getInventory().addItem(new ItemStack(mat));
        p.sendMessage("§aGekauft für §e" + price + " Coins");
    }

    public void sell(Player p, Material mat, int price) {

        int amount = 0;

        for (ItemStack it : p.getInventory().getContents()) {
            if (it != null && it.getType() == mat) {
                amount += it.getAmount();
                p.getInventory().remove(it);
            }
        }

        if (amount == 0) {
            p.sendMessage("§cNichts zu verkaufen.");
            return;
        }

        int total = amount * price;
        plugin.getCoinsManager().addCoins(p.getUniqueId(), total);

        p.sendMessage("§aVerkauft: §f" + amount + " §7für §e" + total + " Coins");
    }
}