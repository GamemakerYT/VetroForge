package com.vetroforge.raid.shop;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Einfacher Persistenz-Manager für Energy-Crystals (pro Spieler). */
public class EnergyCrystalManager {

    private final FactionsPlugin plugin;
    private final File file;
    private FileConfiguration cfg;

    public EnergyCrystalManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "energy_crystals.yml");
        this.cfg  = YamlConfiguration.loadConfiguration(file);
    }

    private String key(UUID id) { return "players." + id.toString() + ".balance"; }

    public int get(UUID player) {
        Player online = Bukkit.getPlayer(player);
        if (online != null && plugin.getRaidCrystalManager() != null) {
            return plugin.getRaidCrystalManager().getAvailable(online);
        }
        return cfg.getInt(key(player), 0);
    }

    public int getMax() {
        return Math.max(1, plugin.getConfig().getInt("raid.shop.maxCrystals",
                plugin.getConfig().getInt("raid.crystals.max", 100)));
    }

    public void set(UUID player, int amount) {
        cfg.set(key(player), Math.max(0, Math.min(amount, getMax())));
        save();
    }

    public void add(UUID player, int delta) {
        Player online = Bukkit.getPlayer(player);
        if (online != null && plugin.getRaidCrystalManager() != null) {
            plugin.getRaidCrystalManager().refund(online, delta);
            return;
        }
        set(player, get(player) + delta);
    }

    /** Versucht zu bezahlen; liefert true, wenn erfolgreich. */
    public boolean spend(UUID player, int cost) {
        Player online = Bukkit.getPlayer(player);
        if (online != null && plugin.getRaidCrystalManager() != null) {
            return plugin.getRaidCrystalManager().consume(online, cost);
        }
        int have = get(player);
        if (have < cost) return false;
        set(player, have - cost);
        return true;
    }

    private void save() {
        try { cfg.save(file); } catch (IOException e) {
            plugin.getLogger().warning("Konnte energy_crystals.yml nicht speichern: " + e.getMessage());
        }
    }

    /* Optional: Admin-Komfort */
    public void setByName(String name, int amount) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(name);
        if (op != null && op.getUniqueId() != null) set(op.getUniqueId(), amount);
    }
}
