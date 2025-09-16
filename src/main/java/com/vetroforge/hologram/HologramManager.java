package com.vetroforge.hologram;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.event.Listener;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

public class HologramManager implements Listener {

    private final FactionsPlugin plugin;
    private final Map<HologramType, ManagedHologram> holograms = new EnumMap<>(HologramType.class);
    private final Map<HologramType, Location> storedLocations = new EnumMap<>(HologramType.class);
    private BukkitTask refreshTask;

    public HologramManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        for (HologramType type : HologramType.values()) {
            holograms.put(type, new ManagedHologram(type));
        }
    }

    public void loadAndSpawnAll() {
        storedLocations.clear();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("holo.positions");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                HologramType type = HologramType.fromString(key);
                if (type == null) {
                    continue;
                }
                Location location = readLocation(section.getConfigurationSection(key));
                if (location != null) {
                    storedLocations.put(type, location);
                    spawn(type, location);
                }
            }
        }
        scheduleRefreshTask();
    }

    private void scheduleRefreshTask() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        long ticks = Math.max(40L, plugin.getConfig().getLong("holo.refreshSeconds", 20L) * 20L);
        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                refreshDynamicHolograms();
            }
        }.runTaskTimer(plugin, ticks, ticks);
    }

    private void refreshDynamicHolograms() {
        ManagedHologram warzone = holograms.get(HologramType.WARZONE);
        Location location = storedLocations.get(HologramType.WARZONE);
        if (warzone != null && location != null) {
            warzone.updateLines(getLines(HologramType.WARZONE));
        }
    }

    public boolean setLocation(HologramType type, Location location) {
        if (type == null || location == null) {
            return false;
        }
        storedLocations.put(type, location.clone());
        writeLocation(type, location);
        spawn(type, location);
        return true;
    }

    public boolean removeLocation(HologramType type) {
        if (type == null) {
            return false;
        }
        if (!storedLocations.containsKey(type)) {
            return false;
        }
        storedLocations.remove(type);
        plugin.getConfig().set("holo.positions." + type.getConfigKey(), null);
        plugin.saveConfig();
        ManagedHologram hologram = holograms.get(type);
        if (hologram != null) {
            hologram.despawn();
        }
        return true;
    }

    public void respawn(HologramType type) {
        Location location = storedLocations.get(type);
        if (location == null) {
            return;
        }
        spawn(type, location);
    }

    public void respawnAll() {
        for (Map.Entry<HologramType, Location> entry : storedLocations.entrySet()) {
            spawn(entry.getKey(), entry.getValue());
        }
    }

    private void spawn(HologramType type, Location location) {
        ManagedHologram hologram = holograms.get(type);
        if (hologram == null) {
            return;
        }
        Bukkit.getScheduler().runTask(plugin, () -> hologram.spawn(location, getLines(type)));
    }

    public List<String> getLines(HologramType type) {
        List<String> lines = new ArrayList<>();
        if (type == HologramType.WARZONE) {
            String title = plugin.getConfig().getString("holo.warzoneTitle", "&bWarzone Live");
            lines.add(title);
            List<String> leaderboard = new ArrayList<>(plugin.getConfig().getStringList("holo.warzoneLines"));
            if (!leaderboard.isEmpty()) {
                String first = leaderboard.get(0);
                leaderboard.set(0, "&l" + first.replace("&l", ""));
            }
            lines.addAll(leaderboard);
            return lines;
        }
        List<String> configured = plugin.getConfig().getStringList("holo." + type.getConfigKey() + "Lines");
        if (!configured.isEmpty()) {
            lines.addAll(configured);
        } else {
            lines.add("&b" + type.getDisplayName());
            lines.add("&7Coming soon");
        }
        return lines;
    }

    private Location readLocation(ConfigurationSection section) {
        if (section == null) {
            return null;
        }
        String worldName = section.getString("world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            plugin.getPrefixedLogger().warning("Cannot spawn hologram, world not found: " + worldName);
            return null;
        }
        double x = section.getDouble("x");
        double y = section.getDouble("y");
        double z = section.getDouble("z");
        float yaw = (float) section.getDouble("yaw");
        float pitch = (float) section.getDouble("pitch");
        return new Location(world, x, y, z, yaw, pitch);
    }

    private void writeLocation(HologramType type, Location location) {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("holo.positions");
        if (section == null) {
            section = plugin.getConfig().createSection("holo.positions");
        }
        section.set(type.getConfigKey(), null);
        ConfigurationSection holoSection = section.createSection(type.getConfigKey());
        holoSection.set("world", location.getWorld().getName());
        holoSection.set("x", location.getX());
        holoSection.set("y", location.getY());
        holoSection.set("z", location.getZ());
        holoSection.set("yaw", location.getYaw());
        holoSection.set("pitch", location.getPitch());
        plugin.saveConfig();
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (ManagedHologram hologram : holograms.values()) {
            hologram.despawn();
        }
    }

    public List<String> getStoredTypes() {
        List<String> list = new ArrayList<>();
        for (HologramType type : HologramType.values()) {
            if (storedLocations.containsKey(type)) {
                list.add(type.name());
            }
        }
        return list;
    }
}
