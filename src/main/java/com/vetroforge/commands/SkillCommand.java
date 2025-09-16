package com.vetroforge.commands;

import com.vetroforge.skills.SkillManager;
import com.vetroforge.skills.SkillProfile;
import com.vetroforge.skills.SkillProgress;
import com.vetroforge.skills.SkillType;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class SkillCommand implements CommandExecutor, TabCompleter {

    private final SkillManager skillManager;

    public SkillCommand(SkillManager skillManager) {
        this.skillManager = skillManager;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.command.skill")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to use this command.");
            return true;
        }
        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Console must specify a player.");
                return true;
            }
            showProfile(player, player);
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(ChatColor.RED + "Player not found.");
            return true;
        }
        if (sender instanceof Player player && !player.equals(target) && !sender.hasPermission("vetroforge.command.skill.other")) {
            sender.sendMessage(ChatColor.RED + "You do not have permission to inspect other players.");
            return true;
        }
        showProfile(sender, target);
        return true;
    }

    private void showProfile(CommandSender viewer, Player target) {
        SkillProfile profile = skillManager.getProfile(target);
        viewer.sendMessage(ChatColor.AQUA + "Skills for " + target.getName() + ":");
        for (SkillType type : SkillType.values()) {
            SkillProgress progress = profile.getProgress(type);
            double required = Math.round(skillManager.getRequiredXp(progress.getLevel()));
            viewer.sendMessage(ChatColor.GRAY + " - " + ChatColor.WHITE + type.displayName() + ChatColor.GRAY + ": Level " + ChatColor.AQUA + progress.getLevel()
                    + ChatColor.GRAY + " | XP " + ChatColor.WHITE + Math.round(progress.getXp()) + "/" + Math.round(required)
                    + ChatColor.GRAY + " | Points " + ChatColor.WHITE + progress.getPoints());
            if (!progress.getUnlockedStages().isEmpty()) {
                viewer.sendMessage(ChatColor.DARK_GRAY + "   Stages: " + ChatColor.WHITE + prettify(progress.getUnlockedStages()));
            }
            if (!progress.getUnlockedAbilities().isEmpty()) {
                viewer.sendMessage(ChatColor.DARK_GRAY + "   Abilities: " + ChatColor.WHITE + prettify(progress.getUnlockedAbilities()));
            }
        }
    }

    private String prettify(Iterable<String> values) {
        List<String> formatted = new ArrayList<>();
        for (String value : values) {
            if (value == null || value.isEmpty()) {
                continue;
            }
            String normalized = value.replace('_', ' ');
            formatted.add(Character.toUpperCase(normalized.charAt(0)) + (normalized.length() > 1 ? normalized.substring(1) : ""));
        }
        return String.join(", ", formatted);
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            List<String> names = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                names.add(player.getName());
            }
            return StringUtil.copyPartialMatches(args[0], names, new ArrayList<>());
        }
        return Collections.emptyList();
    }
}
