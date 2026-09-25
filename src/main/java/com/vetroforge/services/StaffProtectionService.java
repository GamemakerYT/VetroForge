package com.vetroforge.services;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class StaffProtectionService {
    private final FactionsPlugin plugin;

    public StaffProtectionService(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean hasAdminOverride(CommandSender sender) {
        if (sender == null) return false;
        try {
            return sender.isOp()
                    || sender.hasPermission("vetroforge.admin")
                    || sender.hasPermission("vetroforge.*")
                    || sender.hasPermission("vetroforge.rank.admin")
                    || sender.hasPermission("vetroforge.rank.coadmin");
        } catch (Throwable ignored) {
            return false;
        }
    }

    public boolean isProtected(Player target) {
        return isStaff(target);
    }

    public boolean isProtected(OfflinePlayer target) {
        if (target == null) return false;
        try {
            if (target.isOp()) return true;
        } catch (Throwable ignored) {}

        Player online = target.getPlayer();
        if (online != null) return isStaff(online);

        UUID id = null;
        String name = null;
        try { id = target.getUniqueId(); } catch (Throwable ignored) {}
        try { name = target.getName(); } catch (Throwable ignored) {}
        return isConfiguredProtected(id, name) || isStaffRank(configuredRank(id));
    }

    public boolean isProtected(UUID targetId, String targetName) {
        OfflinePlayer target = resolve(targetId, targetName);
        if (target != null && isProtected(target)) return true;
        return isConfiguredProtected(targetId, targetName) || isStaffRank(configuredRank(targetId));
    }

    public boolean isStaff(Player player) {
        if (player == null) return false;
        try {
            if (player.isOp()
                    || player.hasPermission("vetroforge.admin")
                    || player.hasPermission("vetroforge.*")
                    || player.hasPermission("vetroforge.staff")
                    || player.hasPermission("vetroforge.staff.*")
                    || player.hasPermission("vetroforge.staff.notify")
                    || player.hasPermission("vetroforge.staff.aiban")
                    || player.hasPermission("vetroforge.staff.ban")
                    || player.hasPermission("vetroforge.staff.kick")
                    || player.hasPermission("vetroforge.staff.mute")
                    || player.hasPermission("vetroforge.rank.admin")
                    || player.hasPermission("vetroforge.rank.coadmin")
                    || player.hasPermission("vetroforge.rank.mod")
                    || player.hasPermission("vetroforge.rank.moderator")
                    || player.hasPermission("vetroforge.rank.builder")
                    || player.hasPermission("vetroforge.rank.support")
                    || player.hasPermission("vetroforge.rank.supporter")) {
                return true;
            }
        } catch (Throwable ignored) {}

        try {
            var perms = plugin.getVetroPermsManager();
            if (perms != null && isStaffRank(perms.getRank(player))) return true;
        } catch (Throwable ignored) {}

        return isConfiguredProtected(player.getUniqueId(), player.getName());
    }

    public boolean mayPunish(CommandSender actor, OfflinePlayer target) {
        return !isProtected(target) || hasAdminOverride(actor);
    }

    public boolean mayPunish(CommandSender actor, Player target) {
        return !isStaff(target) || hasAdminOverride(actor);
    }

    public boolean blockIfProtected(CommandSender actor, OfflinePlayer target, String action) {
        if (mayPunish(actor, target)) return false;
        auditBlocked(actor, targetName(target), action);
        return true;
    }

    public boolean blockIfProtected(CommandSender actor, Player target, String action) {
        if (mayPunish(actor, target)) return false;
        auditBlocked(actor, target == null ? "-" : target.getName(), action);
        return true;
    }

    public void auditBlocked(CommandSender actor, String targetName, String action) {
        String actorName = actor == null ? "AI/Auto" : actor.getName();
        String target = targetName == null || targetName.isBlank() ? "-" : targetName;
        String act = action == null || action.isBlank() ? "Punishment" : action;
        String message = "§cStaffProtection §8» §7Blockiert: §f" + act
                + " §7gegen §e" + target + " §7von §f" + actorName;
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (hasAdminOverride(player) || player.hasPermission("vetroforge.staff.audit")) {
                player.sendMessage(message);
            }
        }
        Bukkit.getConsoleSender().sendMessage(message);
        appendAudit(ChatColor.stripColor(message));
    }

    private OfflinePlayer resolve(UUID targetId, String targetName) {
        try {
            if (targetName != null && !targetName.isBlank()) {
                Player online = Bukkit.getPlayerExact(targetName);
                if (online != null) return online;
            }
            if (targetId != null) {
                Player online = Bukkit.getPlayer(targetId);
                if (online != null) return online;
                return Bukkit.getOfflinePlayer(targetId);
            }
            if (targetName != null && !targetName.isBlank()) return Bukkit.getOfflinePlayer(targetName);
        } catch (Throwable ignored) {}
        return null;
    }

    private boolean isConfiguredProtected(UUID id, String name) {
        try {
            List<String> protectedStaff = plugin.getConfig().getStringList("punishments.protectedStaff");
            String idText = id == null ? "" : id.toString();
            String nameText = name == null ? "" : name;
            for (String raw : protectedStaff) {
                if (raw == null || raw.isBlank()) continue;
                String entry = raw.trim();
                if (!idText.isBlank() && entry.equalsIgnoreCase(idText)) return true;
                if (!nameText.isBlank() && entry.equalsIgnoreCase(nameText)) return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    private String targetName(OfflinePlayer target) {
        if (target == null) return "-";
        try {
            if (target.getName() != null && !target.getName().isBlank()) return target.getName();
        } catch (Throwable ignored) {}
        try {
            if (target.getUniqueId() != null) return target.getUniqueId().toString();
        } catch (Throwable ignored) {}
        return "-";
    }

    private void appendAudit(String line) {
        File file = new File(plugin.getDataFolder(), "data/staff-protection-audit.log");
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();
        try (FileWriter writer = new FileWriter(file, true)) {
            writer.write("[" + Instant.now() + "] " + (line == null ? "" : line) + System.lineSeparator());
        } catch (IOException ignored) {}
    }

    private String configuredRank(UUID id) {
        if (id == null) return null;
        try {
            return plugin.getConfig().getString("perms.users." + id + ".rank");
        } catch (Throwable ignored) {
            return null;
        }
    }

    private boolean isStaffRank(String rank) {
        if (rank == null) return false;
        String normalized = rank.toLowerCase(Locale.ROOT).replace("-", "").replace("_", "").replace(" ", "");
        return normalized.equals("admin")
                || normalized.equals("coadmin")
                || normalized.equals("owner")
                || normalized.equals("mod")
                || normalized.equals("moderator")
                || normalized.equals("builder")
                || normalized.equals("support")
                || normalized.equals("supporter");
    }
}
