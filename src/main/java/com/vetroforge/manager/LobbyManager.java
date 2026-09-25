package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.ActionBarUtil;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class LobbyManager implements Listener {

    private final FactionsPlugin plugin;
    private Location spawn;

    private int warmupSec;
    private int cooldownSec;
    private boolean cancelOnMove;
    private boolean cancelOnDamage;
    private boolean playParticles;
    private boolean soundEnabled;
    private boolean autoFlyInLobby;

    private final Map<UUID, PendingTp> pending = new HashMap<>();
    private final Map<UUID, Long> lastTp = new HashMap<>();

    private static final String PREFIX = "§6Lobby §8» §f";
    
    private static final class PendingTp {
        final Location startBlockLoc;
        final long endAtMs;
        final BukkitTask task;

        PendingTp(Location startBlockLoc, long endAtMs, BukkitTask task) {
            this.startBlockLoc = startBlockLoc;
            this.endAtMs = endAtMs;
            this.task = task;
        }
    }

    public LobbyManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        reload();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void reload() {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("lobby");
        if (sec == null) {
            spawn = null;
            warmupSec = 3;
            cooldownSec = 5;
            cancelOnMove = true;
            cancelOnDamage = true;
            playParticles = true;
            soundEnabled = true;
            autoFlyInLobby = false;
            return;
        }

        World w = Bukkit.getWorld(sec.getString("world", "world"));
        double x = sec.getDouble("x", 0.5);
        double y = sec.getDouble("y", 65.0);
        double z = sec.getDouble("z", 0.5);
        float yaw = (float) sec.getDouble("yaw", 0.0);
        float pitch = (float) sec.getDouble("pitch", 0.0);

        spawn = (w == null) ? null : new Location(w, x, y, z, yaw, pitch);

        warmupSec = Math.max(0, sec.getInt("warmupSec", 3));
        cooldownSec = Math.max(0, sec.getInt("cooldownSec", 5));
        cancelOnMove = sec.getBoolean("cancelOnMove", true);
        cancelOnDamage = sec.getBoolean("cancelOnDamage", true);
        playParticles = sec.getBoolean("particles", true);
        soundEnabled = sec.getBoolean("sound", true);
        autoFlyInLobby = sec.getBoolean("autoFly", false);
    }

    public boolean hasSpawn() {
        return spawn != null && spawn.getWorld() != null;
    }

    public Location getSpawn() {
        return hasSpawn() ? spawn.clone() : null;
    }

    public void setSpawn(Location location) {
        if (location == null || location.getWorld() == null) {
            return;
        }

        this.spawn = location.clone();

        plugin.getConfig().set("lobby.world", location.getWorld().getName());
        plugin.getConfig().set("lobby.x", location.getX());
        plugin.getConfig().set("lobby.y", location.getY());
        plugin.getConfig().set("lobby.z", location.getZ());
        plugin.getConfig().set("lobby.yaw", location.getYaw());
        plugin.getConfig().set("lobby.pitch", location.getPitch());

        if (!plugin.getConfig().isSet("lobby.warmupSec")) plugin.getConfig().set("lobby.warmupSec", 3);
        if (!plugin.getConfig().isSet("lobby.cooldownSec")) plugin.getConfig().set("lobby.cooldownSec", 5);
        if (!plugin.getConfig().isSet("lobby.cancelOnMove")) plugin.getConfig().set("lobby.cancelOnMove", true);
        if (!plugin.getConfig().isSet("lobby.cancelOnDamage")) plugin.getConfig().set("lobby.cancelOnDamage", true);
        if (!plugin.getConfig().isSet("lobby.particles")) plugin.getConfig().set("lobby.particles", true);
        if (!plugin.getConfig().isSet("lobby.sound")) plugin.getConfig().set("lobby.sound", true);
        if (!plugin.getConfig().isSet("lobby.autoFly")) plugin.getConfig().set("lobby.autoFly", false);
        if (!plugin.getConfig().isSet("lobby.spawnRadius")) plugin.getConfig().set("lobby.spawnRadius", 48.0D);

        plugin.saveConfig();
        reload();
    }

    public void teleportToLobby(Player player) {
        if (!hasSpawn()) {
            player.sendMessage(PREFIX + "Lobby-Spawn ist nicht gesetzt oder die Welt ist nicht geladen.");
            return;
        }

        long now = System.currentTimeMillis();
        long last = lastTp.getOrDefault(player.getUniqueId(), 0L);
        long leftMs = (last + cooldownSec * 1000L) - now;

        if (leftMs > 0) {
            long s = (long) Math.ceil(leftMs / 1000.0);
            player.sendMessage("§cBitte warte §f" + s + "§cs, bevor du wieder §e/lobby §cverwendest.");
            return;
        }

        if (pending.containsKey(player.getUniqueId())) {
            player.sendMessage(PREFIX + "Teleport läuft bereits…");
            return;
        }

        if (warmupSec <= 0) {
            doTeleport(player);
            return;
        }

        Location startBlock = blockLoc(player.getLocation());
        long endAt = now + warmupSec * 1000L;

        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long remain = endAt - System.currentTimeMillis();
            if (remain <= 0) {
                cancelWarmup(player, false);
                doTeleport(player);
                return;
            }

            int sec = (int) Math.ceil(remain / 1000.0);
            sendActionBar(player, "§eTeleportiere in §f" + sec + "§es… §8(§7nicht bewegen§8)");

            if (soundEnabled) {
                try {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_HAT, 0.6f, 1.6f);
                } catch (Throwable ignored) {
                }
            }
        }, 0L, 10L);

        pending.put(player.getUniqueId(), new PendingTp(startBlock, endAt, task));
        player.sendMessage(PREFIX + "Teleport wird vorbereitet…");
    }

    public void setFlyMode(Player player, boolean enabled) {
        if (!enabled) {
            player.setFlying(false);
            player.setAllowFlight(false);
            player.sendMessage(PREFIX + "Fly §cdeaktiviert");
        } else if (!canUseLobbyFly(player)) {
            player.setFlying(false);
            player.setAllowFlight(false);
            player.sendMessage(PREFIX + "§cFly ist nur im Spawn für berechtigte Spieler erlaubt.");
        } else {
            player.setAllowFlight(true);
            player.setFlying(true);
            player.sendMessage(PREFIX + "Fly §aaktiviert");
        }

        if (soundEnabled) {
            try {
                player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.7f, enabled ? 1.4f : 0.8f);
            } catch (Throwable ignored) {
            }
        }
    }

    private boolean canUseLobbyFly(Player player) {
        boolean permission = player.isOp()
                || player.hasPermission("vetro.fly")
                || player.hasPermission("vetro.lobby.fly")
                || player.hasPermission("vetroforge.lobby.fly")
                || player.hasPermission("vetroforge.admin");
        if (!permission) return false;
        if (plugin.getWarzoneRegionManager() != null) {
            return plugin.getWarzoneRegionManager().isLobbySpawnArea(player.getLocation());
        }
        return player.getWorld() != null
                && player.getWorld().getName().equalsIgnoreCase(plugin.getConfig().getString("lobby.world", "world"));
    }

    public void cancelWarmup(Player player, boolean notify) {
        PendingTp p = pending.remove(player.getUniqueId());
        if (p != null && p.task != null) {
            try {
                p.task.cancel();
            } catch (Throwable ignored) {
            }
        }

        if (notify) {
            sendActionBar(player, "§cTeleport abgebrochen.");
            if (soundEnabled) {
                try {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7f, 0.8f);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    public boolean isInWarmup(Player player) {
        return pending.containsKey(player.getUniqueId());
    }

    private void doTeleport(Player p) {
        if (!hasSpawn()) {
            return;
        }

        if (playParticles) {
            try {
                p.getWorld().spawnParticle(Particle.PORTAL, p.getLocation().add(0, 1, 0), 40, 0.4, 0.6, 0.4, 0.01);
            } catch (Throwable ignored) {
            }
        }

        if (soundEnabled) {
            try {
                p.playSound(p.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 0.7f, 1.2f);
            } catch (Throwable ignored) {
            }
        }

        p.teleport(spawn);

        if (playParticles) {
            try {
                p.getWorld().spawnParticle(Particle.END_ROD, p.getLocation().add(0, 1, 0), 30, 0.3, 0.5, 0.3, 0.02);
            } catch (Throwable ignored) {
            }
        }

        if (soundEnabled) {
            try {
                p.playSound(p.getLocation(), Sound.UI_TOAST_IN, 0.7f, 1.3f);
            } catch (Throwable ignored) {
            }
        }

        if (autoFlyInLobby && canUseLobbyFly(p)) {
            setFlyQuiet(p, true);
        }

        try {
            p.sendTitle("§6Lobby", "§7Willkommen zurück", 5, 25, 5);
        } catch (Throwable ignored) {
        }

        sendActionBar(p, "§aTeleport abgeschlossen.");
        lastTp.put(p.getUniqueId(), System.currentTimeMillis());
    }

    private void setFlyQuiet(Player p, boolean enabled) {
        p.setAllowFlight(enabled);
        p.setFlying(enabled);
    }

    private Location blockLoc(Location l) {
        return new Location(l.getWorld(), l.getBlockX(), l.getBlockY(), l.getBlockZ());
    }

    private void sendActionBar(Player p, String msg) {
        try {
            ActionBarUtil.send(p, msg);
        } catch (Throwable t) {
            try {
                p.sendActionBar(net.kyori.adventure.text.Component.text(ChatColor.stripColor(msg)));
            } catch (Throwable ignored) {
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onMove(PlayerMoveEvent e) {
        if (!cancelOnMove) return;

        Player p = e.getPlayer();
        if (!pending.containsKey(p.getUniqueId())) return;
        if (e.getTo() == null) return;

        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) {
            return;
        }

        cancelWarmup(p, true);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDamage(EntityDamageEvent e) {
        if (!cancelOnDamage) return;
        if (!(e.getEntity() instanceof Player p)) return;
        if (!pending.containsKey(p.getUniqueId())) return;

        cancelWarmup(p, true);
    }
}
