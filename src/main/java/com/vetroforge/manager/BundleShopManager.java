// src/main/java/com/vetroforge/manager/BundleShopManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

public class BundleShopManager {

    private final FactionsPlugin plugin;

    public BundleShopManager(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void buyBundle(Player p, String id) {

        String base = "shop.bundles." + id + ".";
        int price = plugin.getConfig().getInt(base + "price", 100);

        int coins = plugin.getCoinsManager().getCoins(p.getUniqueId());

        if (coins < price) {
            p.sendMessage("§cNicht genug Coins.");
            return;
        }

        plugin.getCoinsManager().removeCoins(p.getUniqueId(), price);

        for (String item : plugin.getConfig().getStringList(base + "items")) {
            Material mat = Material.matchMaterial(
                    plugin.getConfig().getString("shop.general.items." + item + ".material", "STONE")
            );

            p.getInventory().addItem(new ItemStack(mat == null ? Material.STONE : mat));
        }

        p.sendMessage("§aBundle gekauft: §f" + id);
    }
}