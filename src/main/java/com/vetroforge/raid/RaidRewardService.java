package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.raid.runes.RuneType;
import com.vetroforge.raid.runes.RuneUtil;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class RaidRewardService {

    private final FactionsPlugin plugin;

    public RaidRewardService(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void rewardWin(String attacker, String target) {
        rewardProgress(attacker, target, 1, 1);
    }

    public void rewardProgress(String attacker, String target, int obsidianBroken, int obsidianGoal) {
        double progress = Math.min(1.0D, Math.max(0.0D, obsidianBroken / (double) Math.max(1, obsidianGoal)));

        // Anti-Stall: wer lange keinen PvP-Treffer in dieser Raid-Welt mehr gelandet hat
        // (Raider oder Verteidiger), bekommt reduzierte Belohnungen -- verhindert
        // absichtliches Aussitzen eines Raids ohne echten Kampf.
        double stall = 1.0D;
        if (plugin.getAntiStallService() != null && plugin.getWorldManager() != null) {
            String raidId = plugin.getWorldManager().worldNameForFaction(target);
            stall = plugin.getAntiStallService().stallPenalty(raidId);
        }

        int factionCrystals = Math.max(1, (int) Math.round(5 * progress * stall));
        int networkCoins = Math.max(10, (int) Math.round(100 * progress * stall));
        int factionCoins = Math.max(5, (int) Math.round(50 * progress * stall));
        int factionElo = Math.max(5, (int) Math.round(100 * progress * stall));

        boolean useFactionBank = plugin.getRaidCrystalManager().useFactionSource();

        if (useFactionBank) {
            plugin.getRaidCrystalManager().addForFaction(attacker, factionCrystals);
        }
        plugin.getCoinsManager().addCoinsToFaction(attacker, networkCoins);
        plugin.getFactionCoinManager().addCoins(attacker, factionCoins);
        if (plugin.getFactionUpgradeManager() != null) {
            plugin.getFactionUpgradeManager().addElo(attacker, factionElo);
        }
        if (progress >= 1.0D && plugin.getEventManager() != null) {
            plugin.getEventManager().addProgressIfActive("raid_weekend", 1);
        }

        int playerCrystals = Math.max(1, (int) Math.round(3 * progress * stall));

        for (Player p : Bukkit.getOnlinePlayers()) {

            String f = plugin.getFactionManager().getFactionOf(p.getUniqueId());

            if (!attacker.equalsIgnoreCase(f)) continue;

            // Nur in eine der beiden Crystal-Töpfe schreiben -- welcher tatsächlich
            // gelesen wird, entscheidet raid.crystals.useFactionBank (RaidCrystalManager).
            int personalCrystals = playerCrystals;
            if (!useFactionBank) {
                if (RuneUtil.hasRune(plugin, p.getInventory().getItemInMainHand(), RuneType.CRYSTAL_GAIN)) {
                    double mul = Math.max(1.0D, plugin.getConfig().getDouble("runes.bonus.crystalGain", 1.05));
                    personalCrystals = (int) Math.round(personalCrystals * mul);
                }
                personalCrystals = plugin.getBoosterManager().applyRaidXp(p, personalCrystals);
                plugin.getRaidCrystalManager().addForPlayer(p.getUniqueId(), personalCrystals);
            }

            RaidText.send(plugin, p, "message.reward_progress",
                    "crystals", useFactionBank ? factionCrystals : personalCrystals,
                    "broken", obsidianBroken,
                    "goal", Math.max(1, obsidianGoal));
            p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.2f);
        }
    }
}
