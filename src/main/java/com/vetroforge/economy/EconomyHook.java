package com.vetroforge.economy;

import org.bukkit.plugin.java.JavaPlugin;

// Beispiel: EconomyHook
public final class EconomyHook {
    private final JavaPlugin plugin;
    private net.milkbowl.vault.economy.Economy econ;

    public EconomyHook(JavaPlugin plugin){ this.plugin = plugin; }

    public void hookWithRetry() {
        if (tryHook()) return;
        plugin.getServer().getScheduler().runTaskLater(plugin, this::tryHook, 20L); // retry nach 1s
    }

    private boolean tryHook() {
        if (plugin.getServer().getPluginManager().getPlugin("Vault") == null) return false;
        var reg = plugin.getServer().getServicesManager()
                .getRegistration(net.milkbowl.vault.economy.Economy.class);
        if (reg == null) {
            plugin.getLogger().warning("[FactionShop] Vault gefunden, aber kein Economy-Provider registriert.");
            return false;
        }
        econ = reg.getProvider();
        plugin.getLogger().info("[FactionShop] Vault Economy: " + econ.getName());
        return true;
    }

    public net.milkbowl.vault.economy.Economy getEconomy(){ return econ; }
}