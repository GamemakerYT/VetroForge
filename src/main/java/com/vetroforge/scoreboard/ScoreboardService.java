package com.vetroforge.scoreboard;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.ScoreboardManager;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class ScoreboardService implements Listener {

    private final FactionsPlugin plugin;
    private final Map<UUID, ScoreboardContext> contexts = new HashMap<>();
    private final long refreshTicks;
    private BukkitTask refreshTask;

    public ScoreboardService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.refreshTicks = Math.max(40L, plugin.getConfig().getLong("scoreboard.refreshTicks", 100L));
    }

    public void start() {
        if (refreshTask != null) {
            refreshTask.cancel();
        }
        refreshTask = new BukkitRunnable() {
            @Override
            public void run() {
                updateAll();
            }
        }.runTaskTimer(plugin, 60L, refreshTicks);
    }

    public void shutdown() {
        if (refreshTask != null) {
            refreshTask.cancel();
            refreshTask = null;
        }
        for (ScoreboardContext context : contexts.values()) {
            context.clear();
        }
        contexts.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        setup(event.getPlayer());
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onRespawn(PlayerRespawnEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, () -> setup(event.getPlayer()), 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        remove(event.getPlayer().getUniqueId());
    }

    public void setup(Player player) {
        ScoreboardContext context = contexts.computeIfAbsent(player.getUniqueId(), id -> createContext());
        apply(player, context);
    }

    private ScoreboardContext createContext() {
        ScoreboardManager manager = Bukkit.getScoreboardManager();
        Scoreboard scoreboard = manager != null ? manager.getNewScoreboard() : Bukkit.getScoreboardManager().getMainScoreboard();
        Objective objective = scoreboard.getObjective("vf-main");
        if (objective != null) {
            objective.unregister();
        }
        objective = scoreboard.registerNewObjective("vf-main", "dummy", translate(plugin.getConfig().getString("scoreboard.title", "&bVetroForge")));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        Team team = scoreboard.getTeam("nametag");
        if (team == null) {
            team = scoreboard.registerNewTeam("nametag");
            team.setColor(ChatColor.WHITE);
        }
        return new ScoreboardContext(scoreboard, objective, team);
    }

    private void apply(Player player, ScoreboardContext context) {
        context.objective.setDisplayName(translate(plugin.getConfig().getString("scoreboard.title", "&bVetroForge")));
        List<String> lines = plugin.getConfig().getStringList("scoreboard.lines");
        List<String> processed = new ArrayList<>();
        for (String line : lines) {
            processed.add(translate(replacePlaceholders(line, player)));
        }
        if (processed.size() > 15) {
            processed = processed.subList(0, 15);
        }
        for (String entry : new ArrayList<>(context.scoreboard.getEntries())) {
            context.scoreboard.resetScores(entry);
        }
        int score = processed.size();
        int index = 0;
        for (String line : processed) {
            ChatColor suffix = ChatColor.values()[index % ChatColor.values().length];
            String entry = (line.length() > 32 ? line.substring(0, 32) : line) + suffix;
            context.objective.getScore(entry).setScore(score--);
            index++;
        }
        player.setScoreboard(context.scoreboard);
        applyNametag(player, context.team);
        context.lastUpdate = System.currentTimeMillis();
    }

    private void updateAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            ScoreboardContext context = contexts.computeIfAbsent(player.getUniqueId(), id -> createContext());
            long now = System.currentTimeMillis();
            if (now - context.lastUpdate < refreshTicks * 50L) {
                continue;
            }
            apply(player, context);
        }
    }

    private void applyNametag(Player player, Team team) {
        String rank = getRankLabel(player);
        String prefix = rank + ChatColor.DARK_GRAY + " » " + ChatColor.WHITE;
        team.addEntry(player.getName());
        team.setPrefix(cut(prefix, 16));
        team.setSuffix("");
        player.setDisplayName(prefix + player.getName());
        player.setPlayerListName(prefix + player.getName());
    }

    public String getRankLabel(Player player) {
        if (player.hasPermission("vetroforge.rank.admin")) {
            return ChatColor.RED + "Admin";
        }
        if (player.hasPermission("vetroforge.rank.mod")) {
            return ChatColor.LIGHT_PURPLE + "Mod";
        }
        if (player.hasPermission("vetroforge.rank.helper")) {
            return ChatColor.AQUA + "Helper";
        }
        return ChatColor.GRAY + "Member";
    }

    private String replacePlaceholders(String input, Player player) {
        if (input == null) {
            return "";
        }
        return input
                .replace("%player%", player.getName())
                .replace("%players_online%", String.valueOf(Bukkit.getOnlinePlayers().size()))
                .replace("%world%", player.getWorld().getName());
    }

    private String translate(String input) {
        return ChatColor.translateAlternateColorCodes('&', input == null ? "" : input);
    }

    private String cut(String input, int max) {
        if (input.length() <= max) {
            return input;
        }
        return input.substring(0, max);
    }

    public void remove(UUID uuid) {
        ScoreboardContext context = contexts.remove(uuid);
        if (context != null) {
            context.clear();
        }
    }

    private static class ScoreboardContext {
        private final Scoreboard scoreboard;
        private final Objective objective;
        private final Team team;
        private long lastUpdate;

        private ScoreboardContext(Scoreboard scoreboard, Objective objective, Team team) {
            this.scoreboard = scoreboard;
            this.objective = objective;
            this.team = team;
            this.lastUpdate = 0L;
        }

        private void clear() {
            try {
                team.unregister();
            } catch (IllegalStateException ignored) {
            }
            try {
                objective.unregister();
            } catch (IllegalStateException ignored) {
            }
        }
    }
}
