// src/main/java/com/vetroforge/raid/RaidActionBarTask.java
package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

public class RaidActionBarTask {

    private final FactionsPlugin plugin;

    public RaidActionBarTask(FactionsPlugin plugin) {
        this.plugin = plugin;
        start();
    }

    private void start() {

        Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            var rm = plugin.getRaidManager();
            if (rm == null) return;

            for (Player p : Bukkit.getOnlinePlayers()) {

                var raid = rm.getRaidForPlayer(p.getUniqueId());
                if (raid == null) continue;

                p.sendActionBar(RaidText.tr(plugin, p, "actionbar.active",
                        "target", raid.targetFaction,
                        "broken", raid.obsidianBroken,
                        "goal", raid.obsidianGoal,
                        "seconds", raid.secondsLeft));
            }

        }, 20L, 20L); // jede Sekunde ist hier okay
    }
}
