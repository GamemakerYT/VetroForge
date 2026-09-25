package com.vetroforge.doctor;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.world.ChunkLoadEvent;

import java.util.Locale;

public final class LegacyEntityCleaner implements Listener {
    private static final String[] LEGACY_TAGS = {
            "vetro_crate_npc",
            "vetro_key_trader_npc",
            "vetro_commerce_npc"
    };

    private final FactionsPlugin plugin;

    public record SweepResult(int scanned, int pluginVillagers, int spawnVillagers,
                              int crowdedVillagers, int removed) {
        public int found() {
            return pluginVillagers + spawnVillagers + crowdedVillagers;
        }
    }

    public LegacyEntityCleaner(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        if (!enabled()) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            SweepResult result = sweepLoaded(true);
            if (result.removed() > 0 || logCleanSweeps()) {
                plugin.getLogger().info("[LegacyCleaner] Scan: " + result.scanned()
                        + " Entities, alt=" + result.found()
                        + ", entfernt=" + result.removed());
            }
        }, Math.max(1L, plugin.getConfig().getLong("doctor.legacyEntityCleaner.startDelayTicks", 40L)));
    }

    public SweepResult sweepLoaded(boolean remove) {
        MutableResult result = new MutableResult();
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                inspectEntity(entity, remove, result);
            }
        }
        return result.toResult();
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        if (!enabled()) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            MutableResult result = new MutableResult();
            inspectChunk(event.getChunk(), true, result);
            if (result.removed > 0 && logCleanSweeps()) {
                plugin.getLogger().warning("[LegacyCleaner] " + result.removed
                        + " alte Villager/NPCs in Chunk "
                        + event.getWorld().getName() + " ["
                        + event.getChunk().getX() + "," + event.getChunk().getZ()
                        + "] entfernt.");
            }
        });
    }

    private void inspectChunk(Chunk chunk, boolean remove, MutableResult result) {
        for (Entity entity : chunk.getEntities()) {
            inspectEntity(entity, remove, result);
        }
        removeCrowdedVillagers(chunk, remove, result);
    }

    private void inspectEntity(Entity entity, boolean remove, MutableResult result) {
        result.scanned++;
        if (entity.getType() != EntityType.VILLAGER) return;

        if (looksLikeOldPluginNpc(entity)) {
            result.pluginVillagers++;
            remove(entity, remove, result);
            return;
        }

        if (removeSpawnVillagers() && isInSpawnArea(entity.getLocation())) {
            result.spawnVillagers++;
            remove(entity, remove, result);
        }
    }

    private void removeCrowdedVillagers(Chunk chunk, boolean remove, MutableResult result) {
        int max = Math.max(0, plugin.getConfig().getInt("doctor.legacyEntityCleaner.maxVillagersPerChunk", 8));
        if (max <= 0) return;

        int villagers = 0;
        for (Entity entity : chunk.getEntities()) {
            if (entity.getType() == EntityType.VILLAGER) villagers++;
        }
        if (villagers <= max) return;

        int toRemove = villagers - max;
        for (Entity entity : chunk.getEntities()) {
            if (toRemove <= 0) break;
            if (entity.getType() != EntityType.VILLAGER) continue;
            if (looksLikeOldPluginNpc(entity) || isInSpawnArea(entity.getLocation())) continue;
            if (entity.getCustomName() != null && !entity.getCustomName().isBlank()) continue;
            result.crowdedVillagers++;
            remove(entity, remove, result);
            toRemove--;
        }
    }

    private void remove(Entity entity, boolean remove, MutableResult result) {
        if (!remove) return;
        try {
            entity.remove();
            result.removed++;
        } catch (Throwable ignored) {
        }
    }

    private boolean looksLikeOldPluginNpc(Entity entity) {
        for (String tag : LEGACY_TAGS) {
            if (entity.getScoreboardTags().contains(tag)) return true;
        }
        String name = entity.getCustomName();
        if (name == null || name.isBlank()) return false;
        String plain = org.bukkit.ChatColor.stripColor(name).toLowerCase(Locale.ROOT);
        return plain.contains("crate")
                || plain.contains("kiste")
                || plain.contains("key händler")
                || plain.contains("key handler")
                || plain.contains("key trader")
                || plain.contains("shop")
                || plain.contains("auktionshaus")
                || plain.contains("auction");
    }

    private boolean isInSpawnArea(Location location) {
        if (location == null || location.getWorld() == null) return false;
        if (plugin.getConfig().getBoolean("spawn.region.enabled", false)
                && plugin.getConfig().isConfigurationSection("spawn.region.pos1")
                && plugin.getConfig().isConfigurationSection("spawn.region.pos2")) {
            String world = plugin.getConfig().getString("spawn.region.world", "");
            if (!location.getWorld().getName().equalsIgnoreCase(world)) return false;
            int x1 = plugin.getConfig().getInt("spawn.region.pos1.x");
            int z1 = plugin.getConfig().getInt("spawn.region.pos1.z");
            int x2 = plugin.getConfig().getInt("spawn.region.pos2.x");
            int z2 = plugin.getConfig().getInt("spawn.region.pos2.z");
            int minX = Math.min(x1, x2);
            int maxX = Math.max(x1, x2);
            int minZ = Math.min(z1, z2);
            int maxZ = Math.max(z1, z2);
            return location.getBlockX() >= minX && location.getBlockX() <= maxX
                    && location.getBlockZ() >= minZ && location.getBlockZ() <= maxZ;
        }

        int radius = Math.max(0, plugin.getConfig().getInt("doctor.legacyEntityCleaner.spawnRadiusFallback", 96));
        if (radius <= 0) return false;
        String worldName = plugin.getConfig().getString("spawn.world", location.getWorld().getName());
        if (!location.getWorld().getName().equalsIgnoreCase(worldName)) return false;
        double x = plugin.getConfig().getDouble("spawn.x", location.getWorld().getSpawnLocation().getX());
        double z = plugin.getConfig().getDouble("spawn.z", location.getWorld().getSpawnLocation().getZ());
        return Math.abs(location.getX() - x) <= radius && Math.abs(location.getZ() - z) <= radius;
    }

    private boolean enabled() {
        return plugin.getConfig().getBoolean("doctor.legacyEntityCleaner.enabled", true);
    }

    private boolean removeSpawnVillagers() {
        return plugin.getConfig().getBoolean("doctor.legacyEntityCleaner.removeSpawnVillagers", true);
    }

    private boolean logCleanSweeps() {
        return plugin.getConfig().getBoolean("doctor.legacyEntityCleaner.log", true);
    }

    private static final class MutableResult {
        private int scanned;
        private int pluginVillagers;
        private int spawnVillagers;
        private int crowdedVillagers;
        private int removed;

        private SweepResult toResult() {
            return new SweepResult(scanned, pluginVillagers, spawnVillagers, crowdedVillagers, removed);
        }
    }
}
