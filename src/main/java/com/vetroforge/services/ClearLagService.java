package com.vetroforge.services;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Item;
import org.bukkit.scheduler.BukkitTask;

public final class ClearLagService {
    private final FactionsPlugin plugin;
    private BukkitTask cycleTask;
    private BukkitTask warningTask;
    private int secondsLeft;

    public ClearLagService(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();
        if (!plugin.getConfig().getBoolean("clearlag.enabled", true)) return;
        int intervalSeconds = Math.max(120, plugin.getConfig().getInt("clearlag.intervalSeconds", 300));
        int warningSeconds = Math.max(5, Math.min(300, plugin.getConfig().getInt("clearlag.warningSeconds", 60)));
        cycleTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> beginCountdown(warningSeconds),
                20L * intervalSeconds, 20L * intervalSeconds);
    }

    public void stop() {
        if (cycleTask != null) cycleTask.cancel();
        if (warningTask != null) warningTask.cancel();
        cycleTask = null;
        warningTask = null;
    }

    public void runNow(boolean broadcast) {
        int removed = clearGroundItems();
        if (broadcast) {
            Bukkit.broadcastMessage(prefix() + "§aAlle Boden-Items wurden gelöscht. §8(§f" + removed + "§8)");
        }
    }

    public void beginCountdown(int warningSeconds) {
        if (warningTask != null) warningTask.cancel();
        secondsLeft = Math.max(5, Math.min(300, warningSeconds));
        warningTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (secondsLeft == 60 || secondsLeft == 30 || secondsLeft == 20 || secondsLeft == 10 || secondsLeft == 5) {
                Bukkit.broadcastMessage(prefix() + "§7Boden-Items werden in §c" + secondsLeft + "s §7gelöscht.");
            }
            if (secondsLeft <= 0) {
                if (warningTask != null) warningTask.cancel();
                warningTask = null;
                runNow(true);
                return;
            }
            secondsLeft--;
        }, 0L, 20L);
    }

    private int clearGroundItems() {
        int removed = 0;
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (!(entity instanceof Item item)) continue;
                if (item.isDead() || !item.isValid()) continue;
                item.remove();
                removed++;
            }
        }
        return removed;
    }

    private String prefix() {
        return "§b§lVetroForge §8» ";
    }
}
