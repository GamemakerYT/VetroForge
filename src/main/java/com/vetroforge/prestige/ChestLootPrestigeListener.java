// src/main/java/com/vetroforge/listeners/ChestLootPrestigeListener.java
package com.vetroforge.prestige;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.warzone.WarzoneRegionManager;
import org.bukkit.Location;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryOpenEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Vergibt Prestige-XP, wenn in der Warzone eine Chest geöffnet wird.
 * Anti-Spam: pro Spieler nur alle N Sekunden (cooldownSecs).
 */
public class ChestLootPrestigeListener implements Listener {

    private final FactionsPlugin plugin;
    private final Map<UUID, Long> lastAward = new HashMap<UUID, Long>();
    private final int cooldownSecs = 5;

    public ChestLootPrestigeListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent e) {
        if (!(e.getPlayer() instanceof Player)) return;
        Player p = (Player) e.getPlayer();

        // Nur Block-Inventare haben eine Location (z.B. Chests)
        Location loc = e.getInventory().getLocation();
        if (loc == null) return;

        // In Warzone?
        WarzoneRegionManager wz = plugin.getWarzoneRegionManager();
        if (wz == null || !wz.isWarzone(loc)) return;

        // Cooldown check
        long now = System.currentTimeMillis();
        Long last = lastAward.get(p.getUniqueId());
        if (last != null && (now - last) < cooldownSecs * 1000L) return;
        lastAward.put(p.getUniqueId(), now);

        int xp = plugin.getConfig().getInt("prestige.xp.onWarzoneChestOpen", 4);
        if (xp <= 0) return;

        plugin.getPrestigeManager().addXp(p.getUniqueId(), xp);
        // Optional: kleine ActionBar
        p.sendActionBar("§a+" + xp + "§7 Prestige §8(Chest)");
    }
}
