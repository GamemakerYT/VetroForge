package com.vetroforge.economy;

import com.vetroforge.FactionsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EconomyManager {

    private final FactionsPlugin plugin;
    private final File file;
    private final Map<UUID, Double> balances = new ConcurrentHashMap<>();
    private int autosaveTaskId = -1;

    public EconomyManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/economy.yml");
        load();
    }

    /* === Persistenz === */

    public synchronized void load() {
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            YamlConfiguration y = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            balances.clear();
            if (y.isConfigurationSection("balances")) {
                for (String k : y.getConfigurationSection("balances").getKeys(false)) {
                    try {
                        UUID id = UUID.fromString(k);
                        double v = y.getDouble("balances." + k, 0D);
                        if (v > 0) balances.put(id, v);
                    } catch (Exception ignored) {}
                }
            }
        } catch (Throwable t) {
            plugin.getLogger().warning("[Economy] Konnte Datei nicht laden: " + t.getMessage());
        }
    }

    public synchronized void save() {
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            YamlConfiguration y = new YamlConfiguration();
            for (Map.Entry<UUID, Double> e : balances.entrySet()) {
                y.set("balances." + e.getKey(), round2(e.getValue()));
            }
            y.save(file);
        } catch (IOException ex) {
            plugin.getLogger().warning("[Economy] Konnte Datei nicht speichern: " + ex.getMessage());
        }
    }

    public void startAutosave(long everyTicks) {
        stopAutosave();
        autosaveTaskId = plugin.getServer().getScheduler().scheduleSyncRepeatingTask(plugin, this::save,
                Math.max(200L, everyTicks), Math.max(200L, everyTicks));
        plugin.getLogger().info("[Economy] Autosave aktiv (alle " + everyTicks + " Ticks).");
    }

    public void stopAutosave() {
        if (autosaveTaskId != -1) {
            plugin.getServer().getScheduler().cancelTask(autosaveTaskId);
            autosaveTaskId = -1;
        }
    }

    /* === Core API (UUID-basiert) === */

    public double getBalance(UUID id) {
        return balances.getOrDefault(id, 0D);
    }

    public void setBalance(UUID id, double amount) {
        balances.put(id, Math.max(0D, round2(amount)));
    }

    public boolean deposit(UUID id, double amount) {
        if (amount <= 0D) return false;
        setBalance(id, getBalance(id) + amount);
        return true;
    }

    public boolean withdraw(UUID id, double amount) {
        if (amount <= 0D) return false;
        double cur = getBalance(id);
        if (cur + 1e-9 < amount) return false;
        setBalance(id, cur - amount);
        return true;
    }

    /** Für Persistenz-Store/Backups */
    public Map<UUID, Double> getAllBalances() {
        return Collections.unmodifiableMap(balances);
    }

    /* === Helpers === */
    public static String formatMoney(double v) {
        return "$" + formatAmount(v);
    }

    /** Formatierte Zahl ohne Währungssymbol, z.B. für Coins-Anzeigen. */
    public static String formatAmount(double v) {
        return String.format(java.util.Locale.US, "%,.2f", v);
    }
    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}