package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;

public final class FactionBannerManager {

    private final File file;
    private final YamlConfiguration yaml;

    public FactionBannerManager(FactionsPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "data/faction-banners.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public Material getBanner(String faction) {
        if (faction == null) return Material.GRAY_BANNER;
        Material material = Material.matchMaterial(yaml.getString("factions." + faction, "RED_BANNER"));
        return material != null && material.name().endsWith("_BANNER") ? material : Material.RED_BANNER;
    }

    public void setBanner(String faction, Material material) {
        if (faction == null || material == null || !material.name().endsWith("_BANNER")) return;
        yaml.set("factions." + faction, material.name());
        save();
    }

    public String getSymbol(String faction) {
        return faction == null ? "" : yaml.getString("symbols." + faction, "");
    }

    public boolean setSymbol(String faction, String symbol) {
        if (faction == null) return false;
        String normalized = symbol == null || symbol.equalsIgnoreCase("none") ? "" : symbol.trim().toUpperCase();
        if (!normalized.isEmpty() && !normalized.matches("[A-Z0-9]")) return false;
        yaml.set("symbols." + faction, normalized);
        save();
        return true;
    }

    private void save() {
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }
}
