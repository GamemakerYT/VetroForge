package com.vetroforge.raid.obsidian;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class ObsidianHealthManager {

    private final FactionsPlugin plugin;
    private final Map<String, Integer> hp = new HashMap<>();
    private final File store;

    public ObsidianHealthManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.store = new File(plugin.getDataFolder(), "obsidian_hp.yml");
        load();
    }

    public int defaultHp() {
        return Math.max(1, plugin.getConfig().getInt("raids.obsidian.hp", 1000));
    }
    public int defaultHp(String faction) {
        int base = defaultHp();
        return plugin.getFactionUpgradeManager() == null
                ? base
                : plugin.getFactionUpgradeManager().applyObsidianHealth(faction, base);
    }
    public int tntDamage() {
        return Math.max(1, plugin.getConfig().getInt("raids.obsidian.tntDamage", 120));
    }
    public int tntDamage(String attackerFaction) {
        int base = tntDamage();
        return plugin.getFactionUpgradeManager() == null
                ? base
                : plugin.getFactionUpgradeManager().applyTntDamage(attackerFaction, base);
    }

    private String key(Location l) {
        return l.getWorld().getName()+":"+l.getBlockX()+":"+l.getBlockY()+":"+l.getBlockZ();
    }

    public int getHp(Block b) {
        if (b == null || b.getType() != Material.OBSIDIAN) return 0;
        return hp.getOrDefault(key(b.getLocation()), defaultHp(factionAt(b)));
    }

    public void setHp(Block b, int value) {
        if (b == null || b.getType() != Material.OBSIDIAN) return;
        int v = Math.max(0, value);
        if (v == defaultHp(factionAt(b))) hp.remove(key(b.getLocation()));
        else hp.put(key(b.getLocation()), v);
    }

    /** wendet Schaden an; gibt neue HP zurück */
    public int damage(Block b, int amount) {
        if (b == null || b.getType() != Material.OBSIDIAN) return 0;
        int cur = getHp(b);
        int now = Math.max(0, cur - Math.max(1, amount));
        setHp(b, now);
        if (now == 0) {
            // zerstören
            b.setType(Material.AIR);
            // optional: Partikel/Sound hier (visuelles Feedback)
            try { b.getWorld().createExplosion(b.getLocation().add(0.5,0.5,0.5), 0F, false, false); } catch (Throwable ignored) {}
        }
        return now;
    }

    public String format(Block b) {
        int cur = getHp(b);
        int max = defaultHp(factionAt(b));
        return cur + "§7/§f" + max + " §7HP";
    }

    private String factionAt(Block block) {
        return block == null || plugin.getFactionManager() == null
                ? null
                : plugin.getFactionManager().getFactionAt(block.getLocation());
    }

    /* ===== persist ===== */

    public void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Map.Entry<String,Integer> e : hp.entrySet()) y.set(e.getKey(), e.getValue());
        try { y.save(store); } catch (IOException ignored) {}
    }

    public void load() {
        hp.clear();
        if (!store.exists()) return;
        YamlConfiguration y = YamlConfiguration.loadConfiguration(store);
        for (String k : y.getKeys(false)) hp.put(k, y.getInt(k, defaultHp()));
    }

    public void startAutosave() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::save, 20L*60, 20L*300);
    }
}
