package com.vetroforge.services;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.ActionBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.FileWriter;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BuildModeService implements Listener, CommandExecutor, TabCompleter {

    public enum Area { SPAWN, WARZONE, RAID, GLOBAL }

    private static BuildModeService instance;

    private final FactionsPlugin plugin;
    private final Map<UUID, State> active = new HashMap<>();
    private final File auditFile;
    private BukkitTask task;

    public BuildModeService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.auditFile = new File(plugin.getDataFolder(), "logs/build-audit.log");
        instance = this;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startTask();
    }

    public static boolean hasAccess(FactionsPlugin plugin, Player player, Area area) {
        if (player == null) return false;
        if (player.hasPermission("vetroforge.admin") || player.hasPermission("vetroforge.build")) return true;
        if (area == Area.SPAWN && player.hasPermission("vetroforge.build.spawn")) return true;
        if (area == Area.WARZONE && player.hasPermission("vetroforge.build.warzone")) return true;
        if (area == Area.RAID && player.hasPermission("vetroforge.build.raid")) return true;
        BuildModeService service = instance;
        return service != null && service.isActive(player)
                && (player.hasPermission("vetroforge.build.toggle") || player.hasPermission("vetroforge.admin"));
    }

    public boolean isActive(Player player) {
        return player != null && active.containsKey(player.getUniqueId());
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.build.toggle") && !sender.hasPermission("vetroforge.admin")) {
            sender.sendMessage("§cKeine Rechte.");
            return true;
        }
        if (args.length > 0 && args[0].equalsIgnoreCase("list")) {
            sendList(sender);
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cKonsole: /build list");
            return true;
        }

        boolean enable = !isActive(player);
        if (args.length > 0) {
            String raw = args[0].toLowerCase(Locale.ROOT);
            if (raw.equals("on")) enable = true;
            if (raw.equals("off")) enable = false;
        }
        setActive(player, enable, "command");
        return true;
    }

    public void setActive(Player player, boolean enable, String reason) {
        if (enable) {
            active.put(player.getUniqueId(), new State(player.getName(), System.currentTimeMillis(), System.currentTimeMillis()));
            player.sendMessage("§6Build §8» §aBuildmodus aktiviert.");
            ActionBarUtil.send(player, "§aBuildmodus aktiv §8• §7Blöcke werden nicht geschützt");
            audit("MODE_ON", player, player.getLocation(), reason);
        } else {
            active.remove(player.getUniqueId());
            player.sendMessage("§6Build §8» §cBuildmodus deaktiviert.");
            ActionBarUtil.send(player, "§cBuildmodus aus");
            audit("MODE_OFF", player, player.getLocation(), reason);
        }
    }

    private void sendList(CommandSender sender) {
        sender.sendMessage("§8§m------------------------------");
        sender.sendMessage("§6Build §8» §fAktive Builder: §e" + active.size());
        long now = System.currentTimeMillis();
        for (State state : active.values()) {
            sender.sendMessage("§8• §f" + state.name + " §7idle §e" + ((now - state.lastActivity) / 1000) + "s");
        }
        sender.sendMessage("§8§m------------------------------");
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        touch(event.getPlayer());
        if (isActive(event.getPlayer())) {
            audit("BREAK " + event.getBlock().getType(), event.getPlayer(), event.getBlock().getLocation(), "");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onPlace(BlockPlaceEvent event) {
        touch(event.getPlayer());
        if (isActive(event.getPlayer())) {
            audit("PLACE " + event.getBlockPlaced().getType(), event.getPlayer(), event.getBlockPlaced().getLocation(), "");
        }
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        if (active.remove(event.getPlayer().getUniqueId()) != null) {
            audit("MODE_OFF", event.getPlayer(), event.getPlayer().getLocation(), "quit");
        }
    }

    private void touch(Player player) {
        State state = active.get(player.getUniqueId());
        if (state != null) state.lastActivity = System.currentTimeMillis();
    }

    private void startTask() {
        long idleMillis = Math.max(60_000L, plugin.getConfig().getLong("build.autoOffSeconds", 600L) * 1000L);
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            long now = System.currentTimeMillis();
            for (UUID id : new ArrayList<>(active.keySet())) {
                Player player = Bukkit.getPlayer(id);
                State state = active.get(id);
                if (player == null || state == null) {
                    active.remove(id);
                    continue;
                }
                boolean inKnownBuildArea = isSpawn(player) || isWarzone(player);
                if (!inKnownBuildArea || now - state.lastActivity > idleMillis) {
                    setActive(player, false, !inKnownBuildArea ? "left-area" : "idle");
                    continue;
                }
                ActionBarUtil.send(player, "§aBuildmodus aktiv §8• §7Auto-Off bei Inaktivität");
            }
        }, 40L, 40L);
    }

    private boolean isSpawn(Player player) {
        return plugin.getWarzoneRegionManager() != null && plugin.getWarzoneRegionManager().isSpawn(player.getLocation());
    }

    private boolean isWarzone(Player player) {
        return plugin.getWarzoneRegionManager() != null && plugin.getWarzoneRegionManager().isWarzone(player.getLocation());
    }

    private void audit(String action, Player player, Location loc, String reason) {
        try {
            auditFile.getParentFile().mkdirs();
            try (FileWriter writer = new FileWriter(auditFile, true)) {
                writer.write(Instant.now() + " | " + action + " | " + player.getName()
                        + " | " + player.getUniqueId() + " | " + shortLoc(loc)
                        + (reason == null || reason.isBlank() ? "" : " | " + reason) + "\n");
            }
        } catch (Throwable ignored) {}
    }

    private String shortLoc(Location loc) {
        if (loc == null || loc.getWorld() == null) return "-";
        return loc.getWorld().getName() + " " + loc.getBlockX() + "/" + loc.getBlockY() + "/" + loc.getBlockZ();
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            for (String option : List.of("on", "off", "toggle", "list")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(option);
            }
            return out;
        }
        return List.of();
    }

    private static final class State {
        final String name;
        final long enabledAt;
        long lastActivity;

        State(String name, long enabledAt, long lastActivity) {
            this.name = name;
            this.enabledAt = enabledAt;
            this.lastActivity = lastActivity;
        }
    }
}
