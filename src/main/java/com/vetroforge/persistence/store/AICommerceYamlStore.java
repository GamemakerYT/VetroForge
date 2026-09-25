// src/main/java/com/vetroforge/persistence/store/AICommerceYamlStore.java
package com.vetroforge.persistence.store;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.commerce.AICommerceManager;
import com.vetroforge.persistence.YamlIO;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

public class AICommerceYamlStore {

    private final File file;

    public AICommerceYamlStore(FactionsPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "data/aicommerce.yml");
    }

    public void loadInto(AICommerceManager ai) {
        FileConfiguration cfg = YamlIO.load(file);
        if (!cfg.isConfigurationSection("items")) return;

        for (String id : cfg.getConfigurationSection("items").getKeys(false)) {
            double dynBuy  = cfg.getDouble("items."+id+".dynBuy",  0.0);
            double dynSell = cfg.getDouble("items."+id+".dynSell", 0.0);
            for (AICommerceManager.Entry e : ai.all()) {
                if (id.equalsIgnoreCase(e.id)) {
                    if (dynBuy  > 0.0) e.dynBuy  = dynBuy;
                    if (dynSell > 0.0) e.dynSell = dynSell;
                    break;
                }
            }
        }
    }

    public void saveFrom(AICommerceManager ai) {
        FileConfiguration cfg = YamlIO.load(file);
        cfg.set("items", null);
        for (AICommerceManager.Entry e : ai.all()) {
            String base = "items."+e.id+".";
            cfg.set(base+"dynBuy", e.dynBuy);
            cfg.set(base+"dynSell", e.dynSell);
        }
        YamlIO.save(cfg, file);
    }
}