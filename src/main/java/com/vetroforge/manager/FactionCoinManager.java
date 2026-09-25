// src/main/java/com/vetroforge/manager/FactionCoinManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

public class FactionCoinManager {

    private final FactionsPlugin plugin;
    private final File file;
    private YamlConfiguration yml;

    public FactionCoinManager(FactionsPlugin plugin){
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/faction-coins.yml");
        load();
    }

    private void load(){
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    private void save(){
        try { yml.save(file); } catch (IOException ignored) {}
    }

    /* ---- API by Faction name ---- */
    public int getCoins(String factionName){
        if (factionName == null) return 0;
        return Math.max(0, yml.getInt("coins."+factionName, 0));
    }

    public void setCoins(String factionName, int amount){
        if (factionName == null) return;
        yml.set("coins."+factionName, Math.max(0, amount));
        save();
    }

    public void addCoins(String factionName, int delta){
        if (factionName == null || delta <= 0) return;
        setCoins(factionName, getCoins(factionName) + delta);
    }

    public boolean takeCoins(String factionName, int amount){
        if (factionName == null || amount <= 0) return false;
        int cur = getCoins(factionName);
        if (cur < amount) return false;
        setCoins(factionName, cur - amount);
        return true;
    }

    /* ---- Convenience by player UUID ---- */
    public int getCoins(UUID playerId){
        String fac = plugin.getFactionManager().getFactionOf(playerId);
        return getCoins(fac);
    }
    public void addCoins(UUID playerId, int delta){
        String fac = plugin.getFactionManager().getFactionOf(playerId);
        addCoins(fac, delta);
    }
    public boolean takeCoins(UUID playerId, int amount){
        String fac = plugin.getFactionManager().getFactionOf(playerId);
        return takeCoins(fac, amount);
    }
}