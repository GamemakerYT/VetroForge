// ===============================================
// File: src/main/java/com/vetroforge/persistence/PersistenceManager.java
// ===============================================
package com.vetroforge.persistence;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.factions.FactionManager;
import com.vetroforge.persistence.store.AICommerceYamlStore;
import com.vetroforge.persistence.store.CrateYamlStore;
import com.vetroforge.persistence.store.EconomyYamlStore;
import com.vetroforge.persistence.store.FactionYamlStore;
import com.vetroforge.persistence.store.StatsYamlStore;
import org.bukkit.Bukkit;
import org.bukkit.scheduler.BukkitTask;

import java.util.logging.Level;

/**
 * Zentraler Orchestrator für Persistenz (YAML).
 * - loadAll(): Lädt YAML -> füllt Manager.
 * - saveAll(): Holt Snapshots aus Managern -> YAML.
 * - startAutosave(): periodisches Speichern (Standard 300s, konfigurierbar in config.yml: persistence.autosave-seconds).
 */
public class PersistenceManager {

    private final FactionsPlugin plugin;

    // Stores
    private final FactionYamlStore factionStore;
    private final EconomyYamlStore economyStore;
    private final StatsYamlStore statsStore;
    private final AICommerceYamlStore commerceStore;
    private final CrateYamlStore crateStore;

    private BukkitTask autosave;

    public PersistenceManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.factionStore  = new FactionYamlStore(plugin);
        this.economyStore  = new EconomyYamlStore(plugin);
        this.statsStore    = new StatsYamlStore(plugin);
        this.commerceStore = new AICommerceYamlStore(plugin);
        this.crateStore    = new CrateYamlStore(plugin);
    }

    public void loadAll() {
        try {
            FactionManager.Snapshot snap = factionStore.load();
            if (snap != null) plugin.getFactionManager().importSnapshot(snap);
        } catch (Throwable t) {
            plugin.getLogger().log(Level.WARNING, "Fehler beim Laden der Fraktionen: " + t.getMessage(), t);
        }

        try { economyStore.loadInto(plugin.getEconomyManager()); }
        catch (Throwable t) { plugin.getLogger().log(Level.WARNING, "Fehler beim Laden der Economy: " + t.getMessage(), t); }

        try { statsStore.loadInto(plugin.getStatsManager()); }
        catch (Throwable t) { plugin.getLogger().log(Level.WARNING, "Fehler beim Laden der Stats: " + t.getMessage(), t); }

        try { commerceStore.loadInto(plugin.getAiCommerceManager()); }
        catch (Throwable t) { plugin.getLogger().log(Level.WARNING, "Fehler beim Laden von AI-Commerce: " + t.getMessage(), t); }

        try { crateStore.loadInto(plugin); }
        catch (Throwable t) { plugin.getLogger().log(Level.WARNING, "Fehler beim Laden der Crate-NPCs: " + t.getMessage(), t); }
    }

    public void saveAll() {
        try { factionStore.save(plugin.getFactionManager().exportSnapshot()); }
        catch (Throwable t) { plugin.getLogger().log(Level.WARNING, "Fehler beim Speichern der Fraktionen: " + t.getMessage(), t); }

        try { economyStore.saveFrom(plugin.getEconomyManager()); }
        catch (Throwable t) { plugin.getLogger().log(Level.WARNING, "Fehler beim Speichern der Economy: " + t.getMessage(), t); }

        try { statsStore.saveFrom(plugin.getStatsManager()); }
        catch (Throwable t) { plugin.getLogger().log(Level.WARNING, "Fehler beim Speichern der Stats: " + t.getMessage(), t); }

        try { commerceStore.saveFrom(plugin.getAiCommerceManager()); }
        catch (Throwable t) { plugin.getLogger().log(Level.WARNING, "Fehler beim Speichern von AI-Commerce: " + t.getMessage(), t); }

        try { crateStore.saveFrom(plugin); }
        catch (Throwable t) { plugin.getLogger().log(Level.WARNING, "Fehler beim Speichern der Crate-NPCs: " + t.getMessage(), t); }
    }

    public void startAutosave() {
        stopAutosave();
        int seconds = 300;
        try { seconds = Math.max(60, plugin.getConfig().getInt("persistence.autosave-seconds", 300)); }
        catch (Throwable ignored) {}
        final long period = seconds * 20L;
        autosave = Bukkit.getScheduler().runTaskTimerAsynchronously(plugin, new Runnable() {
            @Override public void run() {
                try { saveAll(); } catch (Throwable t) {
                    plugin.getLogger().log(Level.WARNING, "Auto-Save Fehler: " + t.getMessage(), t);
                }
            }
        }, period, period);
        plugin.getLogger().info("[Persistence] Autosave gestartet: alle " + seconds + "s");
    }

    public void stopAutosave() {
        if (autosave != null) {
            try { autosave.cancel(); } catch (Throwable ignored) {}
            autosave = null;
        }
    }
}