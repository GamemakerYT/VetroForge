package com.vetroforge.physics;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.event.*;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.block.Block;

import java.util.*;

public class ExplosionPhysicsListener implements Listener {

    private final FactionsPlugin plugin;

    public ExplosionPhysicsListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent e) {
        if (!plugin.getConfig().getBoolean("raid.physics.enabled", true)) return;

        double radius  = plugin.getConfig().getDouble("raid.physics.radius", 5.0);
        boolean cyl    = plugin.getConfig().getBoolean("raid.physics.cylindrical", true);
        boolean ignoreLiquids = plugin.getConfig().getBoolean("raid.physics.ignoreLiquids", true);
        Set<String> blacklist = new HashSet<>(plugin.getConfig().getStringList("raid.physics.blockBlacklist"));

        Location center = e.getLocation();
        World w = center.getWorld();
        if (w == null) return;

        // Erzeuge deterministische, zylindrische (oder kugelige) Explosionsform → vorherige Liste verwerfen
        e.blockList().clear();

        int r = (int)Math.ceil(radius);
        int cx = center.getBlockX(), cy = center.getBlockY(), cz = center.getBlockZ();

        for (int x = cx - r; x <= cx + r; x++) {
            for (int z = cz - r; z <= cz + r; z++) {
                for (int y = cy - r; y <= cy + r; y++) {
                    double dx = x - cx, dy = y - cy, dz = z - cz;
                    double dist2 = cyl ? (dx*dx + dz*dz) : (dx*dx + dy*dy + dz*dz);
                    if (dist2 > radius*radius) continue;

                    Block b = w.getBlockAt(x, y, z);
                    if (b.isEmpty()) continue;

                    if (ignoreLiquids && (b.getType() == Material.WATER || b.getType() == Material.LAVA)) continue;
                    if (blacklist.contains(b.getType().name())) continue;

                    e.blockList().add(b);
                }
            }
        }

        // Optional: konstantere Drops
        e.setYield((float) plugin.getConfig().getDouble("raid.physics.dropYield", 0.0));
    }
}
