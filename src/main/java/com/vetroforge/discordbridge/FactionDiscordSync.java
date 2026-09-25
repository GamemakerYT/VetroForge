// src/main/java/com/vetroforge/discordbridge/FactionDiscordSync.java
package com.vetroforge.discordbridge;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.factions.FactionManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;

import java.util.List;
import java.util.UUID;

public final class FactionDiscordSync {

    private final FactionsPlugin plugin;

    public FactionDiscordSync(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public void syncFaction(String factionName) {
        if (factionName == null || factionName.isBlank()) {
            return;
        }

        if (plugin.discordBridge() == null) {
            return;
        }

        FactionManager factionManager = plugin.getFactionManager();

        if (factionManager == null) {
            return;
        }

        List<UUID> members = factionManager.getMembers(factionName);

        UUID leaderUuid = null;
        String leaderName = null;

        for (UUID uuid : members) {
            if (factionManager.isLeader(uuid)) {
                leaderUuid = uuid;

                OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
                leaderName = offlinePlayer.getName() != null
                        ? offlinePlayer.getName()
                        : uuid.toString();

                break;
            }
        }

        plugin.discordBridge().upsertFaction(
                new FactionsDiscordBridgeService.FactionData(
                        factionName,
                        leaderUuid,
                        leaderName,
                        0,
                        0,
                        0,
                        0,
                        0,
                        0
                )
        );
    }

    public void syncPlayer(UUID uuid) {
        if (uuid == null) {
            return;
        }

        if (plugin.discordBridge() == null) {
            return;
        }

        FactionManager factionManager = plugin.getFactionManager();

        if (factionManager == null) {
            return;
        }

        OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

        String name = offlinePlayer.getName() != null
                ? offlinePlayer.getName()
                : uuid.toString();

        String factionName = factionManager.getFactionOf(uuid);
        FactionManager.Role role = factionManager.getRole(uuid);

        plugin.discordBridge().upsertPlayerProfile(
                new FactionsDiscordBridgeService.PlayerProfileData(
                        uuid,
                        name,
                        "member",
                        factionName,
                        role.name(),
                        0,
                        0,
                        0,
                        0,
                        0,
                        0L,
                        0L
                )
        );
    }

    public void syncFactionAndMembers(String factionName) {
        if (factionName == null || factionName.isBlank()) {
            return;
        }

        FactionManager factionManager = plugin.getFactionManager();

        if (factionManager == null) {
            return;
        }

        syncFaction(factionName);

        for (UUID uuid : factionManager.getMembers(factionName)) {
            syncPlayer(uuid);
        }
    }
}