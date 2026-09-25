package com.vetroforge.listeners;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

public final class BuildVersionJoinListener implements Listener {
    private final FactionsPlugin plugin;

    public BuildVersionJoinListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent event) {
        if (!event.getPlayer().hasPermission("vetroforge.admin")) return;
        if (!plugin.getConfig().getBoolean("doctor.versionJoinHint.enabled", true)) return;

        long maxAgeHours = Math.max(1L, plugin.getConfig().getLong("doctor.versionJoinHint.maxAgeHours", 48L));
        Path jar = pluginJarPath();
        if (jar == null) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                Instant modified = Files.getLastModifiedTime(jar).toInstant();
                long ageHours = Math.max(0L, Duration.between(modified, Instant.now()).toHours());
                if (ageHours < maxAgeHours || !event.getPlayer().isOnline()) return;

                event.getPlayer().sendMessage("§b§lVetroForge §8» §eDie geladene JAR ist ca. §f"
                        + ageHours + "h §ealt. Prüfe mit §f/vf version§e, ob der richtige Stand live ist.");
            } catch (Throwable ignored) {
            }
        }, 60L);
    }

    private Path pluginJarPath() {
        try {
            return Path.of(plugin.getClass()
                    .getProtectionDomain()
                    .getCodeSource()
                    .getLocation()
                    .toURI());
        } catch (Throwable ignored) {
            return null;
        }
    }
}
