// src/main/java/com/vetroforge/lobby/JumpPadListener.java
package com.vetroforge.lobby;

import com.vetroforge.FactionsPlugin;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.util.Vector;

import java.util.*;

public class JumpPadListener implements Listener {

    private final FactionsPlugin plugin;
    private final Set<Material> pads = EnumSet.noneOf(Material.class);
    private final Map<UUID, Long> cd = new HashMap<>();
    private long cooldownMs;
    private double vertical;
    private double horizontal;

    public JumpPadListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        var c = plugin.getConfig();
        cooldownMs = Math.max(0L, (long) (c.getDouble("lobby.jumpPads.cooldownSec", 0.7) * 1000));
        vertical = Math.max(0.1, c.getDouble("lobby.jumpPads.vertical", 1.0));
        horizontal = Math.max(0.0, c.getDouble("lobby.jumpPads.horizontal", 1.2));

        pads.clear();
        List<String> list = c.getStringList("lobby.jumpPads.blocks");
        if (list == null || list.isEmpty()) {
            list = Arrays.asList("LIGHT_WEIGHTED_PRESSURE_PLATE", "SLIME_BLOCK");
        }

        for (String s : list) {
            try {
                pads.add(Material.valueOf(s.toUpperCase(Locale.ROOT)));
            } catch (Exception ignored) {}
        }
    }

    private boolean isLobbyWorld(World w) {
        String name = plugin.getConfig().getString("lobby.world", "world");
        return w != null && w.getName().equalsIgnoreCase(name);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null) return;

        Player p = e.getPlayer();
        if (!isLobbyWorld(p.getWorld())) return;

        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        Material under = e.getTo().clone().subtract(0, 1, 0).getBlock().getType();
        if (!pads.contains(under)) return;

        long now = System.currentTimeMillis();
        long last = cd.getOrDefault(p.getUniqueId(), 0L);
        if (now - last < cooldownMs) return;
        cd.put(p.getUniqueId(), now);

        Vector dir = p.getLocation().getDirection().setY(0);
        if (dir.lengthSquared() > 0.0001) dir = dir.normalize().multiply(horizontal);
        else dir = new Vector(0, 0, 0);

        Vector vel = dir.add(new Vector(0, vertical, 0));
        p.setVelocity(vel);

        try {
            p.playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 0.8f, 1.2f);
        } catch (Throwable ignore) {}

        try {
            p.getWorld().spawnParticle(Particle.CLOUD, p.getLocation(), 16, 0.2, 0.1, 0.2, 0.01);
        } catch (Throwable ignore) {}
    }
}