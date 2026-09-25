// src/main/java/com/vetroforge/tasks/TablistUpdater.java
package com.vetroforge.tasks;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.TablistManager;
import org.bukkit.scheduler.BukkitRunnable;

public class TablistUpdater extends BukkitRunnable {
    private final FactionsPlugin plugin;
    private final TablistManager tm;

    public TablistUpdater(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.tm = plugin.getTablistManager();
    }

    @Override
    public void run() {
        plugin.getServer().getOnlinePlayers().forEach(tm::updatePlayer);
    }
}