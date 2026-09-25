// ===============================================
// File: src/main/java/com/vetroforge/persistence/store/CrateYamlStore.java
// ===============================================
package com.vetroforge.persistence.store;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.persistence.YamlIO;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

/**
 * Hook-Speicher für Crate-NPCs / Positionen.
 * Da die konkrete NPC-Implementierung variieren kann (ArmorStand/Citizens),
 * ist dieser Store als Platzhalter ausgelegt – erweitere nach Bedarf.
 */
public class CrateYamlStore {

    private final FactionsPlugin plugin;
    private final File file;

    public CrateYamlStore(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/crates.yml");
    }

    public void loadInto(FactionsPlugin plugin) {
        FileConfiguration cfg = YamlIO.load(file);
        // Beispiel: java.util.List<Map<String,Object>> list = cfg.getMapList("npcs");
        // Dann per eigenem Manager spawnen lassen:
        // plugin.getCrateNpcManager().spawnAll(list);
    }

    public void saveFrom(FactionsPlugin plugin) {
        FileConfiguration cfg = YamlIO.load(file);
        // Beispiel: cfg.set("npcs", plugin.getCrateNpcManager().exportAll());
        YamlIO.save(cfg, file);
    }
}