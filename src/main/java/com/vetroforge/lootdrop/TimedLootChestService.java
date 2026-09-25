// src/main/java/com/vetroforge/lootdrop/TimedLootChestService.java
package com.vetroforge.lootdrop;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.Rarity;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitTask;

import java.util.concurrent.ThreadLocalRandom;

public class TimedLootChestService {

    private static final String META = "vetro_lootdrop";

    private final FactionsPlugin plugin;
    private final LootChestUX ux;
    private BukkitTask task;

    public TimedLootChestService(FactionsPlugin plugin){
        this.plugin = plugin;
        this.ux = new LootChestUX(plugin); // registriert Listener intern
    }

    public void start(){
        stop();
        int sec = Math.max(60, plugin.getConfig().getInt("lootdrop.timed.intervalSeconds", 900));
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::spawnOne, 40L, 20L * sec);
        plugin.getLogger().info("[TimedLootChest] running every " + sec + "s");
    }

    public void stop(){
        if (task != null) {
            try { task.cancel(); } catch (Throwable ignore) {}
            task = null;
        }
    }

    public void spawnNow(){ spawnOne(); }

    private void spawnOne(){
        if (Bukkit.getOnlinePlayers().isEmpty()) return;

        Location loc = randomRegionLocation();
        if (loc == null) {
            World w = pickWorld();
            if (w == null) return;

            Location near = w.getSpawnLocation().clone();
            int radius = Math.max(16, plugin.getConfig().getInt("lootdrop.timed.radius", 64));

            for (int attempt = 0; attempt < 24; attempt++) {
                double a = ThreadLocalRandom.current().nextDouble(Math.PI * 2);
                double r = ThreadLocalRandom.current().nextDouble(radius * .5, radius);
                Location candidate = near.clone().add(Math.cos(a) * r, 0, Math.sin(a) * r);
                candidate = groundTop(candidate);
                if (isAllowed(candidate)) {
                    loc = candidate;
                    break;
                }
            }
            if (loc == null) {
                plugin.getLogger().warning("[TimedLootChest] Kein gültiger Warzone-Spawnpunkt gefunden. Setze /adminlootdrop pos1/pos2 größer oder prüfe /setspawn pos1/pos2.");
                return;
            }
        }

        // exakt auf Boden setzen
        loc = groundTop(loc);
        if (!isAllowed(loc)) return;

        try {
            // 1) Kiste setzen & labeln & metadaten (UX macht Setzen+Name+SFX)
            ux.spawnLootChest(loc, Rarity.COMMON);
            loc.getBlock().setMetadata(META, new FixedMetadataValue(plugin, Rarity.COMMON.name()));

            // 2) Kiste nur FÜLLEN (ohne erneut zu platzieren)
            LootDropChestUtil.fillAtExact(
                    new LootDropChestUtil.Context(
                            plugin,
                            loc,
                            null,
                            Math.max(1, plugin.getConfig().getInt("lootdrop.timed.slots", 6))
                    )
            );

            // 3) dezente Umgebungseffekte
            try {
                World w = loc.getWorld();
                if (w == null) return;
                w.playSound(loc, Sound.BLOCK_BELL_RESONATE, 1f, 1.15f);
                w.spawnParticle(Particle.ENCHANT, loc.clone().add(.5, 1, .5), 20, .4, .5, .4, .02);
            } catch (Throwable ignore) {}

        } catch (Throwable t) {
            plugin.getLogger().warning("[TimedLootChest] failed: " + t.getMessage());
        }
    }

    private World pickWorld(){
        String worldName = plugin.getConfig().getString("lootdrop.timed.region.world",
                plugin.getConfig().getString("lootdrop.timed.world", null));
        if (worldName != null) {
            World w = Bukkit.getWorld(worldName);
            if (w != null) return w;
        }
        return Bukkit.getWorlds().isEmpty() ? null : Bukkit.getWorlds().get(0);
    }

    private Location randomRegionLocation() {
        ConfigurationSection region = plugin.getConfig().getConfigurationSection("lootdrop.timed.region");
        if (region == null || !region.getBoolean("enabled", false)) return null;

        World world = Bukkit.getWorld(region.getString("world", ""));
        if (world == null) {
            plugin.getLogger().warning("[TimedLootChest] lootdrop.timed.region world ist nicht geladen.");
            return null;
        }

        if (!region.isConfigurationSection("pos1") || !region.isConfigurationSection("pos2")) return null;

        int x1 = region.getInt("pos1.x");
        int z1 = region.getInt("pos1.z");
        int x2 = region.getInt("pos2.x");
        int z2 = region.getInt("pos2.z");

        int minX = Math.min(x1, x2);
        int maxX = Math.max(x1, x2);
        int minZ = Math.min(z1, z2);
        int maxZ = Math.max(z1, z2);
        if (minX == maxX && minZ == maxZ) return null;

        for (int attempt = 0; attempt < 32; attempt++) {
            int x = ThreadLocalRandom.current().nextInt(minX, maxX + 1);
            int z = ThreadLocalRandom.current().nextInt(minZ, maxZ + 1);
            Location location = new Location(world, x + 0.5, world.getHighestBlockYAt(x, z) + 1, z + 0.5);
            if (isAllowed(location)) return location;
        }
        return null;
    }

    private Location groundTop(Location near){
        World w = near.getWorld(); if (w == null) return near;
        int y = w.getHighestBlockYAt(near);
        return new Location(w, near.getX(), y + 1, near.getZ());
    }

    private boolean isAllowed(Location location) {
        if (location == null || location.getWorld() == null) return false;
        try {
            var region = plugin.getWarzoneRegionManager();
            return region == null || region.isWarzone(location);
        } catch (Throwable ignored) {
            return true;
        }
    }
}
