package com.vetroforge.placeholders;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.economy.EconomyManager;
import me.clip.placeholderapi.expansion.PlaceholderExpansion;
import org.bukkit.entity.Player;

public class CoinsPlaceholder extends PlaceholderExpansion {

    private final FactionsPlugin plugin;

    public CoinsPlaceholder(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override public String getIdentifier() { return "vetro"; }
    @Override public String getAuthor() { return "VetroForge"; }
    @Override public String getVersion() { return "1.0.0"; }

    @Override
    public String onPlaceholderRequest(Player p, String id) {
        if (p == null) return "";
        if (id.equalsIgnoreCase("coins")) {
            double coins = plugin.getEconomyManager().getBalance(p.getUniqueId());
            return EconomyManager.formatAmount(coins); // schöner formatiert, z. B. 1,234.56
        }
        return null;
    }
}
