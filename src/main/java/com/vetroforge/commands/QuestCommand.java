// src/main/java/com/vetroforge/commands/QuestCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Map;

public class QuestCommand implements CommandExecutor {

    private final FactionsPlugin plugin;

    public QuestCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }
        if (a.length > 0 && a[0].equalsIgnoreCase("faction")) {
            if (a.length > 1 && a[1].equalsIgnoreCase("claim")) {
                plugin.getFactionWeeklyQuestService().claim(p);
            } else {
                plugin.getFactionWeeklyQuestService().show(p);
            }
            return true;
        }

        // Daily-kills lesen
        int kills  = plugin.getStatsManager().getStat(p, "daily_kills");
        int target = plugin.getConfig().getInt("quests.daily.kills", 20);

        p.sendMessage(plugin.getI18n().tr(p, "quest.daily",
                Map.of("kills", String.valueOf(kills),
                        "target", String.valueOf(target))));

        if (kills >= target) {
            int reward = plugin.getConfig().getInt("quests.daily.rewardMoney", 50);
            int rewardCoins = plugin.getConfig().getInt("quests.daily.rewardCoins", 25);
            int totalCoins = reward + rewardCoins;

            plugin.getCoinsManager().addCoins(p.getUniqueId(), totalCoins);

            plugin.getStatsManager().setStat(p, "daily_kills", 0);
            p.sendMessage(plugin.getI18n().tr(p, "quest.completed",
                    Map.of("reward", totalCoins + " Coins")));
        }
        return true;
    }
}
