package com.vetroforge.quests;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.PlayerDeathEvent;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.temporal.WeekFields;
import java.util.Locale;

public final class FactionWeeklyQuestService implements Listener {

    private final FactionsPlugin plugin;
    private final File file;
    private final YamlConfiguration data;

    public FactionWeeklyQuestService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/faction-weekly-quests.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || killer.equals(event.getEntity())) return;
        String faction = plugin.getFactionManager().getFactionOf(killer.getUniqueId());
        if (faction == null) return;
        String root = root(faction);
        data.set(root + ".kills", data.getInt(root + ".kills", 0) + 1);
        save();
    }

    public void show(Player player) {
        String faction = plugin.getFactionManager().getFactionOf(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§6Faction Quest §8» §cDu bist in keiner Faction.");
            return;
        }
        String root = root(faction);
        int kills = data.getInt(root + ".kills", 0);
        int target = Math.max(1, plugin.getConfig().getInt("quests.weeklyFaction.kills", 100));
        int reward = Math.max(0, plugin.getConfig().getInt("quests.weeklyFaction.rewardFactionCoins", 250));
        boolean claimed = data.getBoolean(root + ".claimed", false);
        player.sendMessage("§6Faction Quest §8» §fWöchentliche PvP-Kills: §e" + kills + "§7/§e" + target);
        player.sendMessage("§6Faction Quest §8» §7Belohnung: §e" + reward + " Faction Coins");
        if (claimed) {
            player.sendMessage("§6Faction Quest §8» §aDiese Woche bereits eingesammelt.");
        } else if (kills >= target) {
            player.sendMessage("§6Faction Quest §8» §eNutze §f/quest faction claim§e.");
        }
    }

    public void claim(Player player) {
        String faction = plugin.getFactionManager().getFactionOf(player.getUniqueId());
        if (faction == null) {
            player.sendMessage("§6Faction Quest §8» §cDu bist in keiner Faction.");
            return;
        }
        String root = root(faction);
        int target = Math.max(1, plugin.getConfig().getInt("quests.weeklyFaction.kills", 100));
        if (data.getBoolean(root + ".claimed", false)) {
            player.sendMessage("§6Faction Quest §8» §cBereits eingesammelt.");
            return;
        }
        if (data.getInt(root + ".kills", 0) < target) {
            player.sendMessage("§6Faction Quest §8» §cZiel noch nicht erreicht.");
            return;
        }
        int reward = Math.max(0, plugin.getConfig().getInt("quests.weeklyFaction.rewardFactionCoins", 250));
        plugin.getFactionCoinManager().addCoins(faction, reward);
        data.set(root + ".claimed", true);
        save();
        Bukkit.broadcastMessage("§6Faction Quest §8» §e" + faction + " §7hat das Wochenziel abgeschlossen. §e+" + reward + " Faction Coins");
    }

    private String root(String faction) {
        return "weeks." + week() + "." + faction.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
    }

    private String week() {
        LocalDate now = LocalDate.now();
        WeekFields fields = WeekFields.ISO;
        return now.getYear() + "-" + now.get(fields.weekOfWeekBasedYear());
    }

    private void save() {
        try {
            File parent = file.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[FactionQuest] Speichern fehlgeschlagen: " + e.getMessage());
        }
    }
}
