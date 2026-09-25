// src/main/java/com/vetroforge/display/TabHeaderFooterTask.java
package com.vetroforge.display;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.GradientUtil;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

public class TabHeaderFooterTask {

    private static final LocalDate SEASON_START = LocalDate.of(2026, 5, 10);
    private static final int SEASON_WEEKS = 12;

    private final FactionsPlugin plugin;
    private BukkitTask task;

    public TabHeaderFooterTask(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void start() {
        stop();

        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int online = Bukkit.getOnlinePlayers().size();
            int max = Bukkit.getMaxPlayers();
            int week = getSeasonWeek();
            String phase = getSeasonPhaseName(week);

            String mainTitle = GradientUtil.gradientBold("VetroForge", "#7A0000", "#FF2A2A");
            String seasonTitle = GradientUtil.gradientBold("The Frost Cycle", "#E9FDFF", "#55CFFF");
            String bottomGradient = GradientUtil.gradient("Factions • Raids • Warzone", "#E9FDFF", "#55CFFF");

            for (Player p : Bukkit.getOnlinePlayers()) {
                String header =
                        "\n" +
                                mainTitle + "\n" +
                                seasonTitle + "\n" +
                                "§7Season 1 §8• §bWeek " + week + "§7/" + SEASON_WEEKS + " §8• §b" + phase + "\n";

                String footer =
                        "\n" +
                                "§6" + tr(p, "tablist.label.online") + " §8» §f" + online + "§8/§f" + max +
                                "  §8•  §bFaction Member §8» §f" + factionOnlineLine(p) +
                                "  §8•  §bVetroForge.net" +
                                "  §8•  §bdc.vetroforge.net" + "\n" +
                                bottomGradient + "\n";

                try {
                    p.setPlayerListHeaderFooter(header, footer);
                } catch (Throwable ignored) {}
            }
        }, 20L, 80L);
    }

    private String tr(Player player, String key) {
        return VetroForgeUtil.tr(plugin, player, key);
    }

    private String factionOnlineLine(Player player) {
        try {
            if (plugin.getFactionManager() == null) return "-";
            String faction = plugin.getFactionManager().getFactionOf(player.getUniqueId());
            if (faction == null || faction.isBlank()) return "-";
            int onlineMembers = 0;
            for (java.util.UUID uuid : plugin.getFactionManager().getMembers(faction)) {
                Player member = Bukkit.getPlayer(uuid);
                if (member != null && member.isOnline()) onlineMembers++;
            }
            int maxMembers = Math.max(1, plugin.getConfig().getInt("factions.maxMembers", 10));
            return onlineMembers + "§8/§f" + maxMembers;
        } catch (Throwable ignored) {
            return "-";
        }
    }

    public void stop() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Throwable ignored) {}
        }

        task = null;
    }

    private int getSeasonWeek() {
        long days = ChronoUnit.DAYS.between(SEASON_START, LocalDate.now());
        int week = (int) (Math.max(0, days) / 7) + 1;
        return Math.max(1, Math.min(SEASON_WEEKS, week));
    }

    private String getSeasonPhaseName(int week) {
        if (week <= 3) return "Calm Frost";
        if (week <= 6) return "Rising Wind";
        if (week <= 9) return "Blizzard Warning";
        return "Collapse";
    }

}
