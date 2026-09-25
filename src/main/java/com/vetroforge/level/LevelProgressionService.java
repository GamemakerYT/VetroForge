package com.vetroforge.level;

import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LevelProgressionService {

    private final FactionsPlugin plugin;
    private final File historyFile;

    public LevelProgressionService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.historyFile = new File(plugin.getDataFolder(), "data/level-history.yml");
    }

    public int addXp(Player player, int amount, String reason) {
        if (player == null || plugin.getStatsManager() == null || amount <= 0) return 0;
        UUID uuid = player.getUniqueId();
        int maxLevel = maxLevel();
        int level = Math.min(maxLevel, plugin.getStatsManager().getLevel(uuid));
        int xp = plugin.getStatsManager().getLevelXp(uuid) + amount;
        int gainedLevels = 0;

        while (level < maxLevel) {
            int needed = xpNeeded(level);
            if (xp < needed) break;
            xp -= needed;
            level++;
            gainedLevels++;
        }

        if (level >= maxLevel) {
            level = maxLevel;
            xp = Math.min(xp, xpNeeded(level));
        }

        plugin.getStatsManager().setLevel(uuid, level);
        plugin.getStatsManager().setLevelXp(uuid, xp);
        record(uuid, amount, reason, level, xp);

        player.sendMessage(ChatColor.AQUA + "+" + amount + " Level-XP"
                + ChatColor.DARK_GRAY + " (" + ChatColor.GRAY + reason + ChatColor.DARK_GRAY + ")");
        if (gainedLevels > 0) {
            player.sendMessage(ChatColor.GOLD + "Level Up! " + ChatColor.YELLOW + "Du bist jetzt Level " + level + ".");
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.25f);
            refreshDisplay(player);
        }
        return gainedLevels;
    }

    private void refreshDisplay(Player player) {
        refreshDisplayNow(player);
        for (long delay : new long[]{1L, 5L, 20L, 60L}) {
            plugin.getServer().getScheduler().runTaskLater(plugin, () -> refreshDisplayNow(player), delay);
        }
    }

    private void refreshDisplayNow(Player player) {
        if (player == null || !player.isOnline()) return;
        if (plugin.getNametagTabManager() != null) {
            plugin.getNametagTabManager().updateTargetEverywhere(player);
            plugin.getNametagTabManager().applyForAll();
        }
        if (plugin.getTablistManager() != null) {
            plugin.getTablistManager().updatePlayer(player);
        }
    }

    public int xpNeeded(int currentLevel) {
        int base = plugin.getConfig().getInt("level.baseXp", 100);
        int perLevel = plugin.getConfig().getInt("level.perLevel", 35);
        return Math.max(25, base + Math.max(0, currentLevel) * perLevel);
    }

    public int maxLevel() {
        return Math.max(1, Math.min(10, plugin.getConfig().getInt("level.maxLevel", 10)));
    }

    public List<String> history(UUID uuid, int limit) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(historyFile);
        List<String> raw = yaml.getStringList("players." + uuid);
        int from = Math.max(0, raw.size() - Math.max(1, limit));
        return new ArrayList<>(raw.subList(from, raw.size()));
    }

    private void record(UUID uuid, int amount, String reason, int level, int xp) {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(historyFile);
        String path = "players." + uuid;
        List<String> entries = yaml.getStringList(path);
        String time = new SimpleDateFormat("dd.MM HH:mm", Locale.ROOT).format(new Date());
        entries.add(time + " +" + amount + " XP | " + reason + " | Level " + level + " (" + xp + " XP)");
        while (entries.size() > 30) entries.remove(0);
        yaml.set(path, entries);
        try {
            if (historyFile.getParentFile() != null) historyFile.getParentFile().mkdirs();
            yaml.save(historyFile);
        } catch (IOException ignored) {}
    }
}
