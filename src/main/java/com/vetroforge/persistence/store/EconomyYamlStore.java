// ===============================================
// File: src/main/java/com/vetroforge/persistence/store/EconomyYamlStore.java
// ===============================================
package com.vetroforge.persistence.store;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.economy.EconomyManager;
import com.vetroforge.persistence.YamlIO;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

/**
 * Speichert/liest Spielerguthaben.
 * Erwartete API in EconomyManager:
 *  - void setBalance(UUID, double)
 *  - Map<UUID, Double> getAllBalances()
 */
public class EconomyYamlStore {

    private final File file;

    public EconomyYamlStore(FactionsPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "data/economy.yml");
    }

    public void loadInto(EconomyManager eco) {
        FileConfiguration cfg = YamlIO.load(file);
        if (cfg.isConfigurationSection("balances")) {
            for (String key : cfg.getConfigurationSection("balances").getKeys(false)) {
                try {
                    java.util.UUID id = java.util.UUID.fromString(key);
                    double val = cfg.getDouble("balances."+key, 0D);
                    eco.setBalance(id, val);
                } catch (Throwable ignored) {}
            }
        }
    }

    public void saveFrom(EconomyManager eco) {
        FileConfiguration cfg = YamlIO.load(file);
        cfg.set("balances", null);
        for (java.util.Map.Entry<java.util.UUID, java.lang.Double> e : eco.getAllBalances().entrySet()) {
            cfg.set("balances."+e.getKey().toString(), e.getValue().doubleValue());
        }
        YamlIO.save(cfg, file);
    }
}