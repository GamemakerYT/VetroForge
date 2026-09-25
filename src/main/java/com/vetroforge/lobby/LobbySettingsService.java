// src/main/java/com/vetroforge/lobby/LobbySettingsService.java
package com.vetroforge.lobby;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.commands.PlayHiderCommand.Mode;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class LobbySettingsService {

    private final FactionsPlugin plugin;
    private final File file;
    private final YamlConfiguration cfg;
    private final ConcurrentHashMap<UUID, Mode> visibilityCache = new ConcurrentHashMap<>();

    public LobbySettingsService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "player_settings.yml");
        this.cfg = YamlConfiguration.loadConfiguration(file);
        loadAll();
    }
    private void saveAsync() {
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                cfg.save(file);
            } catch (IOException e) {
                plugin.getLogger().warning("Fehler beim Speichern von player_settings.yml: " + e.getMessage());
            }
        });
    }


    /* ----------------------- Sichtbarkeit ----------------------- */
    public Mode getVisibility(Player p) {
        return visibilityCache.getOrDefault(p.getUniqueId(),
                Mode.ALL);
    }

    public void setVisibility(Player p, Mode m) {
        visibilityCache.put(p.getUniqueId(), m);
        cfg.set(p.getUniqueId() + ".visibility", m.name());
        saveAsync();
    }

    /* ----------------------- Fly ----------------------- */
    public boolean canFly(Player p) {
        return cfg.getBoolean(p.getUniqueId() + ".fly", false);
    }

    public void setFly(Player p, boolean enabled) {
        boolean allowedHere = plugin.getWarzoneRegionManager() != null
                && plugin.getWarzoneRegionManager().isLobbySpawnArea(p.getLocation());
        boolean active = enabled && allowedHere;
        cfg.set(p.getUniqueId() + ".fly", enabled);
        if (!active) {
            p.setFlying(false);
            p.setAllowFlight(false);
        } else {
            p.setAllowFlight(true);
            p.setFlying(true);
        }
        saveAsync();
    }

    /* ----------------------- DoubleJump ----------------------- */
    public boolean hasDoubleJump(Player p) {
        return cfg.getBoolean(p.getUniqueId() + ".doublejump", true);
    }

    public void setDoubleJump(Player p, boolean enabled) {
        cfg.set(p.getUniqueId() + ".doublejump", enabled);
        saveAsync();
    }

    /* ----------------------- Speed ----------------------- */
    public float getSpeed(Player p) {
        return (float) cfg.getDouble(p.getUniqueId() + ".speed", 0.2);
    }

    public void setSpeed(Player p, float speed) {
        cfg.set(p.getUniqueId() + ".speed", speed);
        p.setWalkSpeed(speed);
        saveAsync();
    }
    public void load(Player p) {
        // optional: lade spezifische Settings aus Datei
    }

    public void save(Player p) {
        saveAsync();
    }

    public void unload(Player p) {
        // optional: Cache leeren
        visibilityCache.remove(p.getUniqueId());
    }

    /* ----------------------- Persistenz ----------------------- */
    private void loadAll() {
        for (String key : cfg.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(key);
                String vis = cfg.getString(key + ".visibility", "ALL");
                visibilityCache.put(uuid, Mode.valueOf(vis.toUpperCase()));
            } catch (Exception ignored) {
            }
        }
    }
}
