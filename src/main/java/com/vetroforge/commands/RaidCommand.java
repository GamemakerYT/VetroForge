// src/main/java/com/vetroforge/commands/RaidCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import com.vetroforge.raid.RaidText;

public class RaidCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final Map<UUID, Long> stopConfirmations = new ConcurrentHashMap<>();

    public RaidCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!(sender instanceof Player p)) {
            sender.sendMessage("Players only.");
            return true;
        }

        if (plugin.getRaidSessionService() == null) {
            RaidText.send(plugin, p, "message.system_unavailable");
            p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
            return true;
        }

        if (args.length == 0 || args[0].equalsIgnoreCase("gui")) {
            new com.vetroforge.gui.RaidGui(plugin).open(p);
            return true;
        }

        switch (args[0].toLowerCase()) {

            case "join" -> {
                if (plugin.getRaidJoinService() == null) {
                    RaidText.send(plugin, p, "message.join_unavailable");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                    return true;
                }

                if (!plugin.getRaidSessionService().hasActiveRaid()) {
                    RaidText.send(plugin, p, "message.no_active_raid");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                    return true;
                }

                String target = args.length >= 2 ? args[1] : null;
                plugin.getRaidJoinService().joinRaid(p, target);
            }

            case "stop" -> {
                boolean confirmed = args.length >= 2 && args[1].equalsIgnoreCase("confirm");
                long now = System.currentTimeMillis();
                if (!confirmed && stopConfirmations.getOrDefault(p.getUniqueId(), 0L) < now) {
                    stopConfirmations.put(p.getUniqueId(), now + 10_000L);
                    RaidText.send(plugin, p, "message.stop_confirm");
                    return true;
                }
                stopConfirmations.remove(p.getUniqueId());
                if (plugin.getRaidSessionService().isScouting(p)) {
                    plugin.getRaidSessionService().stopScouting(p);
                } else {
                    String faction = plugin.getFactionManager() == null ? null
                            : plugin.getFactionManager().getFactionOf(p.getUniqueId());
                    if (plugin.getRaidManager() == null || !plugin.getRaidManager().stopRaidForAttacker(faction, "cancelled")) {
                        RaidText.send(plugin, p, "message.nothing_active");
                        return true;
                    }
                }
                p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
            }

            case "next" -> {
                plugin.getRaidSessionService().nextTarget(p);
                p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.2f);
            }

            case "defend" -> {
                if (plugin.getRaidManager() == null || plugin.getFactionManager() == null) {
                    RaidText.send(plugin, p, "message.system_unavailable");
                    return true;
                }
                String ownFaction = plugin.getFactionManager().getFactionOf(p.getUniqueId());
                if (ownFaction == null || ownFaction.isBlank()) {
                    RaidText.send(plugin, p, "message.no_faction");
                    return true;
                }
                String target = plugin.getRaidManager().findAlliedRaidTarget(ownFaction);
                if (target == null) {
                    RaidText.send(plugin, p, "message.no_ally_raid");
                    p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1.0f, 0.7f);
                    return true;
                }
                if (plugin.getRaidManager().isEliminated(p)) {
                    RaidText.send(plugin, p, "message.eliminated_cannot_rejoin");
                    return true;
                }
                var spawn = plugin.getWorldManager() == null ? null : plugin.getWorldManager().getFactionSpawn(target);
                if (spawn == null) {
                    RaidText.send(plugin, p, "message.zone_missing");
                    return true;
                }
                plugin.getRaidManager().joinAsAllyDefender(target, p.getUniqueId());
                p.teleport(spawn);
                RaidText.send(plugin, p, "message.ally_defend_joined", "target", target);
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 0.6f, 1.3f);
            }

            case "revenge" -> {
                if (plugin.getRaidManager() == null) {
                    RaidText.send(plugin, p, "message.system_unavailable");
                    return true;
                }
                plugin.getRaidManager().openRevengeMenu(p);
            }

            default -> {
                RaidText.send(plugin, p, "message.unknown_command");
                p.sendMessage("§8» §f/raid");
                p.sendMessage("§8» §f/raid join");
                p.sendMessage("§8» §f/raid defend");
                p.sendMessage("§8» §f/raid stop");
                p.sendMessage("§8» §f/raid next");
                p.sendMessage("§8» §f/raid revenge");
            }
        }

        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {

        if (args.length == 1) {
            List<String> out = new ArrayList<>();
            String input = args[0].toLowerCase();

            for (String option : List.of("gui", "join", "defend", "stop", "next", "revenge")) {
                if (option.startsWith(input)) {
                    out.add(option);
                }
            }

            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("stop")) {
            return "confirm".startsWith(args[1].toLowerCase()) ? List.of("confirm") : Collections.emptyList();
        }

        return Collections.emptyList();
    }
}
