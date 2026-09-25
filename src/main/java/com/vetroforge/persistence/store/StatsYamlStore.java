// ===============================================
// File: src/main/java/com/vetroforge/persistence/store/StatsYamlStore.java
// ===============================================
package com.vetroforge.persistence.store;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.persistence.YamlIO;
import com.vetroforge.stats.StatsManager;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;

/**
 * Speichert/liest Spielerstatistiken (Kills/Deaths/Prestige-Level).
 * Prestige-XP laeuft NICHT mehr hierueber -- alleinige Quelle ist prestige.yml
 * via PrestigeManager, das StatsManager.setPrestigeXp() live mitfuehrt.
 * Erwartete API in StatsManager:
 *  - void setKills(UUID,int), setDeaths(UUID,int), setPrestigeLevel(UUID,int)
 *  - int getKills(UUID), getDeaths(UUID), getPrestigeLevel(UUID)
 *  - Set<UUID> getKnownPlayers()
 */
public class StatsYamlStore {

    private final File file;

    public StatsYamlStore(FactionsPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "data/stats.yml");
    }

    public void loadInto(StatsManager sm) {
        FileConfiguration cfg = YamlIO.load(file);
        if (cfg.isConfigurationSection("players")) {
            for (String key : cfg.getConfigurationSection("players").getKeys(false)) {
                try {
                    java.util.UUID id = java.util.UUID.fromString(key);
                    int kills  = cfg.getInt("players."+key+".kills", 0);
                    int deaths = cfg.getInt("players."+key+".deaths", 0);
                    int prestigeLevel = cfg.getInt("players."+key+".prestigeLevel", 0);
                    sm.setKills(id, kills);
                    sm.setDeaths(id, deaths);
                    sm.setPrestigeLevel(id, prestigeLevel);
                    // prestigeXp bewusst NICHT mehr hier geladen: PrestigeManager.loadXp() ist die
                    // alleinige, live gepflegte Quelle (prestige.yml) und spiegelt sie direkt in
                    // StatsManager -- diese Zeile hier lief zeitlich NACH PrestigeManager's Konstruktor
                    // und ueberschrieb den frisch gespiegelten, korrekten Wert wieder mit dem
                    // eingefrorenen alten Snapshot aus data/stats.yml (Ursache des /stats <-> /prestige
                    // XP-Auseinanderlaufens).
                } catch (Throwable ignored) {}
            }
        }
    }

    public void saveFrom(StatsManager sm) {
        FileConfiguration cfg = YamlIO.load(file);
        cfg.set("players", null);
        for (java.util.UUID id : sm.getKnownPlayers()) {
            String base = "players."+id.toString()+".";
            cfg.set(base+"kills", sm.getKills(id));
            cfg.set(base+"deaths", sm.getDeaths(id));
            cfg.set(base+"prestigeLevel", sm.getPrestigeLevel(id));
            // prestigeXp wird nicht mehr gespeichert, s. loadInto().
        }
        YamlIO.save(cfg, file);
    }
}