// src/main/java/com/vetroforge/raid/energy/RaidSessionService.java
package com.vetroforge.raid.energy;

import com.vetroforge.FactionsPlugin;
import net.kyori.adventure.text.Component;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import com.vetroforge.worldgen.RaidWorldLayout;
import com.vetroforge.raid.RaidText;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class RaidSessionService {

    private static final long SCOUTING_DURATION_MS = 120_000L;

    public static final String ACTION_NEXT = "next";
    public static final String ACTION_START = "start";
    public static final String ACTION_CANCEL = "cancel";

    private enum ReturnType {
        SPAWN,
        OWN_FACTION
    }

    private static final class ReturnData {
        private final ReturnType type;
        private final String ownFactionId;

        private ReturnData(ReturnType type, String ownFactionId) {
            this.type = type;
            this.ownFactionId = ownFactionId;
        }
    }

    private static final class InventorySnapshot {
        private final ItemStack[] storage;
        private final ItemStack[] armor;
        private final ItemStack[] extra;
        private final int heldSlot;

        private InventorySnapshot(Player player) {
            PlayerInventory inv = player.getInventory();

            this.storage = cloneArray(inv.getStorageContents());
            this.armor = cloneArray(inv.getArmorContents());
            this.extra = cloneArray(inv.getExtraContents());
            this.heldSlot = inv.getHeldItemSlot();
        }

        private void restore(Player player) {
            PlayerInventory inv = player.getInventory();

            inv.clear();
            inv.setStorageContents(cloneArray(storage));
            inv.setArmorContents(cloneArray(armor));
            inv.setExtraContents(cloneArray(extra));

            if (heldSlot >= 0 && heldSlot <= 8) {
                inv.setHeldItemSlot(heldSlot);
            }

            player.updateInventory();
        }

        private static ItemStack[] cloneArray(ItemStack[] input) {
            if (input == null) return new ItemStack[0];

            ItemStack[] copy = new ItemStack[input.length];

            for (int i = 0; i < input.length; i++) {
                copy[i] = input[i] == null ? null : input[i].clone();
            }

            return copy;
        }
    }

    private final FactionsPlugin plugin;
    private final NamespacedKey sessionItemKey;

    private final Set<UUID> scouting = ConcurrentHashMap.newKeySet();
    private final Set<UUID> raidPlayers = ConcurrentHashMap.newKeySet();

    private final Map<UUID, String> scoutingTargets = new ConcurrentHashMap<>();
    private final Map<UUID, Long> scoutingStartedAt = new ConcurrentHashMap<>();
    private final Map<UUID, Long> scoutingEndsAt = new ConcurrentHashMap<>();
    private final Map<UUID, ReturnData> returnData = new ConcurrentHashMap<>();
    private final Map<UUID, InventorySnapshot> inventorySnapshots = new ConcurrentHashMap<>();
    private final Map<UUID, String> joinedRaidAttackers = new ConcurrentHashMap<>();

    private final Map<String, String> activeFactionRaids = new ConcurrentHashMap<>();
    private final Map<UUID, String> joinedRaidTargets = new ConcurrentHashMap<>();

    public RaidSessionService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.sessionItemKey = new NamespacedKey(plugin, "raid_session_item");
    }

    private void teleportToRaidJoin(Player player, String targetFactionId) {
        if (player == null) return;

        Location raidSpawn = getRaidZoneSpawn(targetFactionId);

        if (raidSpawn == null) {
            RaidText.send(plugin, player, "message.zone_missing");
            teleportToSpawn(player);
            return;
        }

        player.teleport(raidSpawn);
    }
    public boolean startScouting(Player player, String targetFactionId) {
        if (player == null) return false;

        if (targetFactionId == null || targetFactionId.isBlank()) {
            RaidText.send(plugin, player, "message.invalid_target");
            return false;
        }

        Location scoutLocation = getFactionSpawn(targetFactionId);

        if (scoutLocation == null) {
            RaidText.send(plugin, player, "message.target_without_spawn");
            return false;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        inventorySnapshots.put(uuid, new InventorySnapshot(player));

        scouting.add(uuid);
        scoutingTargets.put(uuid, targetFactionId);
        scoutingStartedAt.put(uuid, now);
        scoutingEndsAt.put(uuid, now + SCOUTING_DURATION_MS);
        returnData.put(uuid, createReturnData(player));

        prepareScoutingInventory(player);

        player.teleport(scoutLocation.clone().add(0, 15, 0));

        RaidText.send(plugin, player, "message.scouting_started", "target", targetFactionId);
        return true;
    }

    public Location getRaidZoneSpawn(String targetFactionId) {
        if (targetFactionId == null || targetFactionId.isBlank()) return null;

        Location factionSpawn = getFactionSpawn(targetFactionId);
        if (factionSpawn == null || factionSpawn.getWorld() == null) return null;

        World world = factionSpawn.getWorld();

        int y = plugin.getConfig().getInt("factions.world.yLevel", 64);

        /*
         * Raid-Zone liegt außerhalb der roten Linie:
         * BUILD_HALF = 100
         * Rot = x/z ±100
         * Buffer/Raid-Zone = 101 bis GREEN_EDGE
         *
         * Wir spawnen sauber 8 Blöcke hinter der roten Linie.
         * Also bei X = 108, Z = 0.
         */
        double x = RaidWorldLayout.BUILD_HALF + 8.5;
        double z = 0.5;

        Location loc = new Location(world, x, y + 1.0, z, -90.0f, 0.0f);

        /*
         * Falls dort aus irgendeinem Grund ein Block ist, sauber nach oben korrigieren.
         * Weil Minecraft natürlich lieber jemanden in Gras erstickt als logisch zu sein.
         */
        int highest = world.getHighestBlockYAt(loc);

        if (highest > y) {
            loc.setY(highest + 1.0);
        }

        return loc;
    }
    public boolean startRaidFromScouting(Player player) {
        if (player == null) return false;

        if (!isScouting(player)) {
            RaidText.send(plugin, player, "message.not_scouting");
            return false;
        }

        String ownFaction = getOwnFaction(player);
        String targetFaction = getScoutingTargetFaction(player);

        if (ownFaction == null || ownFaction.isBlank()) {
            RaidText.send(plugin, player, "message.no_faction");
            return false;
        }

        if (targetFaction == null || targetFaction.isBlank()) {
            RaidText.send(plugin, player, "message.no_scouting_target");
            return false;
        }

        int durationSeconds = plugin.getConfig().getInt("raid.attack.durationSeconds", 30 * 60);
        int obsidianGoal = plugin.getConfig().getInt("raid.attack.obsidianGoal", 25);
        if (plugin.getRaidManager() == null
                || !plugin.getRaidManager().startRaid(ownFaction, targetFaction, durationSeconds, obsidianGoal)) {
            RaidText.send(plugin, player, "message.start_failed");
            return false;
        }

        UUID uuid = player.getUniqueId();

        scouting.remove(uuid);
        scoutingTargets.remove(uuid);
        scoutingStartedAt.remove(uuid);
        scoutingEndsAt.remove(uuid);

        restoreInventory(player);
        teleportBack(player);

        returnData.remove(uuid);

        player.sendActionBar(Component.empty());
        RaidText.send(plugin, player, "message.prepared");
        RaidText.send(plugin, player, "message.join_instruction", "seconds", 60);

        return true;
    }

    public void stopScouting(Player player) {
        stopScouting(player, true, "message.scouting_stopped");
    }

    public void stopScoutingExpired(Player player) {
        stopScouting(player, true, "message.scouting_expired");
    }

    private void stopScouting(Player player, boolean teleportBack, String messageKey) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();

        scouting.remove(uuid);
        scoutingTargets.remove(uuid);
        scoutingStartedAt.remove(uuid);
        scoutingEndsAt.remove(uuid);

        restoreInventory(player);

        if (teleportBack) {
            teleportBack(player);
        }

        returnData.remove(uuid);

        player.sendActionBar(Component.empty());

        if (messageKey != null && !messageKey.isBlank()) {
            RaidText.send(plugin, player, messageKey);
        }
    }

    public void finishRaidOrScouting(Player player) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();

        scouting.remove(uuid);
        scoutingTargets.remove(uuid);
        scoutingStartedAt.remove(uuid);
        scoutingEndsAt.remove(uuid);
        raidPlayers.remove(uuid);
        joinedRaidTargets.remove(uuid);

        restoreInventory(player);
        teleportBack(player);

        returnData.remove(uuid);

        player.sendActionBar(Component.empty());
        RaidText.send(plugin, player, "message.reset");
    }

    public boolean isScouting(Player player) {
        if (player == null) return false;

        UUID uuid = player.getUniqueId();

        if (!scouting.contains(uuid)) return false;

        Long endsAt = scoutingEndsAt.get(uuid);
        if (endsAt == null) return true;

        return System.currentTimeMillis() < endsAt;
    }

    public boolean isScoutingExpired(Player player) {
        if (player == null) return false;

        UUID uuid = player.getUniqueId();

        if (!scouting.contains(uuid)) return false;

        Long endsAt = scoutingEndsAt.get(uuid);
        return endsAt != null && System.currentTimeMillis() >= endsAt;
    }

    public boolean isInRaid(Player player) {
        if (player == null) return false;
        return raidPlayers.contains(player.getUniqueId());
    }

    public void markPlayerInRaid(Player player, String targetFactionId) {
        if (player == null) return;

        String ownFaction = getOwnFaction(player);

        raidPlayers.add(player.getUniqueId());

        if (targetFactionId != null && !targetFactionId.isBlank()) {
            joinedRaidTargets.put(player.getUniqueId(), targetFactionId);
        }

        if (ownFaction != null && !ownFaction.isBlank()) {
            joinedRaidAttackers.put(player.getUniqueId(), ownFaction);
        }

        player.sendActionBar(Component.empty());
    }

    public void leaveRaid(Player player) {
        if (player == null) return;

        raidPlayers.remove(player.getUniqueId());
        joinedRaidTargets.remove(player.getUniqueId());
        joinedRaidAttackers.remove(player.getUniqueId());
        if (plugin.getRaidManager() != null) {
            plugin.getRaidManager().removeRaider(player.getUniqueId());
        }

        teleportRaidPlayerBack(player);
        player.sendActionBar(Component.empty());
    }

    public String getActiveRaidTargetForFaction(String ownFactionId) {
        if (ownFactionId == null || ownFactionId.isBlank()) return null;
        return activeFactionRaids.get(ownFactionId);
    }

    public void registerActiveRaid(String attackerFaction, String targetFaction) {
        if (attackerFaction == null || attackerFaction.isBlank()) return;
        if (targetFaction == null || targetFaction.isBlank()) return;

        activeFactionRaids.put(attackerFaction, targetFaction);

        if (plugin.getRaidJoinService() != null) {
            plugin.getRaidJoinService().startJoinPhase(attackerFaction, targetFaction);
        }
    }

    public boolean hasActiveRaidForFaction(String ownFactionId) {
        return getActiveRaidTargetForFaction(ownFactionId) != null;
    }

    public long getScoutingRemainingMillis(Player player) {
        if (player == null) return 0L;

        Long endsAt = scoutingEndsAt.get(player.getUniqueId());
        if (endsAt == null) return 0L;

        return Math.max(0L, endsAt - System.currentTimeMillis());
    }

    public int getScoutingRemainingSeconds(Player player) {
        return (int) Math.ceil(getScoutingRemainingMillis(player) / 1000.0);
    }

    public String getTarget(Player player) {
        if (player == null) return null;
        return scoutingTargets.get(player.getUniqueId());
    }

    public String getScoutingTargetFaction(Player player) {
        if (player == null) return null;
        return scoutingTargets.get(player.getUniqueId());
    }

    public void setScoutingTargetFaction(Player player, String targetFactionId) {
        if (player == null) return;

        UUID uuid = player.getUniqueId();

        if (targetFactionId == null || targetFactionId.isBlank()) {
            scoutingTargets.remove(uuid);
            return;
        }

        scoutingTargets.put(uuid, targetFactionId);
    }

    public void clearScoutingTargetFaction(Player player) {
        if (player == null) return;
        scoutingTargets.remove(player.getUniqueId());
    }

    public void nextTarget(Player player) {
        if (player == null) return;

        if (plugin.getFactionManager() == null) {
            RaidText.send(plugin, player, "message.faction_manager_unavailable");
            return;
        }

        if (plugin.getRaidManager() == null) {
            RaidText.send(plugin, player, "message.raid_manager_unavailable");
            return;
        }

        String ownFaction = plugin.getFactionManager().getFactionOf(player.getUniqueId());

        List<String> allTargets = new ArrayList<>(plugin.getFactionManager().getAllFactionNames());

        allTargets.removeIf(faction ->
                faction == null ||
                        faction.isBlank() ||
                        faction.equalsIgnoreCase(ownFaction) ||
                        plugin.getRaidManager().isFactionInRaid(faction)
        );

        if (allTargets.isEmpty()) {
            RaidText.send(plugin, player, "message.no_targets");
            return;
        }

        String nextTarget = allTargets.get(new Random().nextInt(allTargets.size()));

        Location loc = getFactionSpawn(nextTarget);

        if (loc == null) {
            RaidText.send(plugin, player, "message.target_without_spawn");
            return;
        }

        UUID uuid = player.getUniqueId();
        long now = System.currentTimeMillis();

        if (!scouting.contains(uuid)) {
            inventorySnapshots.put(uuid, new InventorySnapshot(player));
            returnData.put(uuid, createReturnData(player));
            scoutingStartedAt.put(uuid, now);
            scoutingEndsAt.put(uuid, now + SCOUTING_DURATION_MS);
        }

        scouting.add(uuid);
        scoutingTargets.put(uuid, nextTarget);

        ensureScoutingInventory(player);

        player.teleport(loc.clone().add(0, 15, 0));

        RaidText.send(plugin, player, "message.new_scouting_target", "target", nextTarget);
    }

    public void ensureScoutingInventory(Player player) {
        if (player == null || !player.isOnline()) return;
        if (!isScouting(player)) return;

        PlayerInventory inv = player.getInventory();

        if (isSessionItem(inv.getItem(3), ACTION_NEXT)
                && isSessionItem(inv.getItem(4), ACTION_START)
                && isSessionItem(inv.getItem(5), ACTION_CANCEL)) {
            return;
        }

        prepareScoutingInventory(player);
    }

    private void prepareScoutingInventory(Player player) {
        PlayerInventory inv = player.getInventory();

        inv.clear();
        inv.setArmorContents(new ItemStack[4]);
        inv.setExtraContents(new ItemStack[1]);

        inv.setItem(3, createSessionItem(player, Material.ENDER_EYE, "session.next", ACTION_NEXT));
        inv.setItem(4, createSessionItem(player, Material.NETHERITE_SWORD, "session.start", ACTION_START));
        inv.setItem(5, createSessionItem(player, Material.BARRIER, "session.cancel", ACTION_CANCEL));

        inv.setHeldItemSlot(4);
        player.updateInventory();
    }

    private ItemStack createSessionItem(Player player, Material material, String nameKey, String action) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();

        if (meta != null) {
            meta.setDisplayName(RaidText.tr(plugin, player, nameKey));
            meta.setLore(List.of(
                    RaidText.tr(plugin, player, "session.lore_title"),
                    RaidText.tr(plugin, player, "session.lore_locked")
            ));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
            meta.getPersistentDataContainer().set(sessionItemKey, PersistentDataType.STRING, action);
            item.setItemMeta(meta);
        }

        return item;
    }

    public boolean isRaidSessionItem(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        return meta.getPersistentDataContainer().has(sessionItemKey, PersistentDataType.STRING);
    }

    public boolean isSessionItem(ItemStack item, String action) {
        if (item == null || item.getType() == Material.AIR) return false;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return false;

        String value = meta.getPersistentDataContainer().get(sessionItemKey, PersistentDataType.STRING);
        return action.equalsIgnoreCase(String.valueOf(value));
    }

    public String getSessionItemAction(ItemStack item) {
        if (item == null || item.getType() == Material.AIR) return null;

        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;

        return meta.getPersistentDataContainer().get(sessionItemKey, PersistentDataType.STRING);
    }

    private void restoreInventory(Player player) {
        InventorySnapshot snapshot = inventorySnapshots.remove(player.getUniqueId());

        if (snapshot == null) return;

        snapshot.restore(player);
    }

    private ReturnData createReturnData(Player player) {
        String ownFaction = getOwnFaction(player);

        boolean inWarzone = isInWarzone(player);
        boolean inOwnFaction = ownFaction != null && !ownFaction.isBlank() && isInOwnFactionArea(player, ownFaction);

        if (inWarzone) {
            return new ReturnData(ReturnType.SPAWN, ownFaction);
        }

        if (inOwnFaction) {
            return new ReturnData(ReturnType.OWN_FACTION, ownFaction);
        }

        return new ReturnData(ReturnType.SPAWN, ownFaction);
    }

    private void teleportBack(Player player) {
        if (player == null) return;

        ReturnData data = returnData.get(player.getUniqueId());

        if (data == null || data.type == ReturnType.SPAWN) {
            teleportToSpawn(player);
            return;
        }

        if (data.type == ReturnType.OWN_FACTION && data.ownFactionId != null && !data.ownFactionId.isBlank()) {
            Location factionSpawn = getFactionSpawn(data.ownFactionId);

            if (factionSpawn != null) {
                player.teleport(factionSpawn);
                return;
            }
        }

        teleportToSpawn(player);
    }

    private Location getFactionSpawn(String factionId) {
        if (factionId == null || factionId.isBlank()) return null;

        try {
            if (plugin.getWorldManager() == null) return null;
            return plugin.getWorldManager().getFactionSpawn(factionId);
        } catch (Throwable ignored) {
            return null;
        }
    }
    private void teleportRaidPlayerBack(Player player) {
        if (player == null) return;

        String ownFaction = getOwnFaction(player);

        if (ownFaction != null && !ownFaction.isBlank()) {
            Location factionSpawn = getFactionSpawn(ownFaction);

            if (factionSpawn != null) {
                player.teleport(factionSpawn);
                return;
            }
        }

        teleportToSpawn(player);
    }
    private void teleportToSpawn(Player player) {
        Location spawn = getServerSpawn();

        if (spawn == null) {
            RaidText.send(plugin, player, "message.spawn_missing");
            return;
        }

        player.teleport(spawn);
    }

    private Location getServerSpawn() {
        try {
            var lobbyManager = plugin.getLobbyManager();
            if (lobbyManager != null) {
                Location spawn = lobbyManager.getSpawn();
                if (spawn != null) return spawn;
            }
        } catch (Throwable ignored) {
        }

        try {
            if (Bukkit.getWorld("world") != null) {
                return Bukkit.getWorld("world").getSpawnLocation().clone().add(0.5, 0.0, 0.5);
            }
        } catch (Throwable ignored) {
        }

        try {
            if (!Bukkit.getWorlds().isEmpty()) {
                return Bukkit.getWorlds().get(0).getSpawnLocation().clone().add(0.5, 0.0, 0.5);
            }
        } catch (Throwable ignored) {
        }

        return null;
    }

    private boolean isInWarzone(Player player) {
        try {
            return plugin.getWarzoneRegionManager() != null
                    && plugin.getWarzoneRegionManager().isWarzone(player.getLocation());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isInOwnFactionArea(Player player, String ownFactionId) {
        if (player == null || ownFactionId == null || ownFactionId.isBlank()) return false;

        var factionManager = plugin.getFactionManager();
        if (factionManager == null) return false;

        String factionAt = factionManager.getFactionAt(player.getLocation());
        if (factionAt != null) return ownFactionId.equalsIgnoreCase(factionAt);

        return !isInWarzone(player);
    }

    private String getOwnFaction(Player player) {
        try {
            if (plugin.getFactionManager() == null) return null;
            return plugin.getFactionManager().getFactionOf(player.getUniqueId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    public boolean hasActiveRaid() {
        try {
            if (!activeFactionRaids.isEmpty()) return true;

            return plugin.getRaidManager() != null
                    && plugin.getRaidManager().getActiveTargets() != null
                    && !plugin.getRaidManager().getActiveTargets().isEmpty();
        } catch (Throwable ignored) {
            return false;
        }
    }

    public Set<UUID> getScoutingPlayers() {
        return new HashSet<>(scouting);
    }

    public Set<UUID> getRaidPlayers() {
        return new HashSet<>(raidPlayers);
    }

    public String getJoinedRaidTarget(Player player) {
        if (player == null) return null;
        return joinedRaidTargets.get(player.getUniqueId());
    }

    public String getJoinedRaidAttackerFaction(Player player) {
        if (player == null) return null;
        return joinedRaidAttackers.get(player.getUniqueId());
    }

    public int getRaidPlayerCountForFaction(String attackerFaction) {
        if (attackerFaction == null || attackerFaction.isBlank()) return 0;

        int count = 0;

        for (String faction : joinedRaidAttackers.values()) {
            if (attackerFaction.equalsIgnoreCase(faction)) {
                count++;
            }
        }

        return count;
    }

    public void clearActiveRaidForFaction(String attackerFaction) {
        if (attackerFaction == null || attackerFaction.isBlank()) return;
        activeFactionRaids.remove(attackerFaction);
    }

    public boolean canScoutTarget(String targetFactionId) {
        return getFactionSpawn(targetFactionId) != null;
    }

    public void endRaidForFaction(String attackerFaction) {
        if (attackerFaction == null || attackerFaction.isBlank()) return;

        String target = activeFactionRaids.remove(attackerFaction);

        for (UUID uuid : new HashSet<>(raidPlayers)) {
            String playerFaction = joinedRaidAttackers.get(uuid);

            if (!attackerFaction.equalsIgnoreCase(playerFaction)) continue;

            Player player = Bukkit.getPlayer(uuid);

            raidPlayers.remove(uuid);
            joinedRaidTargets.remove(uuid);
            joinedRaidAttackers.remove(uuid);
            if (plugin.getRaidManager() != null) {
                plugin.getRaidManager().removeRaider(uuid);
            }

            if (player != null && player.isOnline()) {
                teleportToSpawn(player);
                player.sendActionBar(Component.empty());
                RaidText.send(plugin, player, "message.ended");
            }
        }

        if (plugin.getRaidJoinService() != null) {
            plugin.getRaidJoinService().stopJoinPhase(attackerFaction);
        }
    }

    public void endAllRaids() {
        for (String attackerFaction : new HashSet<>(activeFactionRaids.keySet())) {
            endRaidForFaction(attackerFaction);
        }
    }

    public void shutdown() {
        for (UUID uuid : new HashSet<>(scouting)) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                restoreInventory(player);
                teleportBack(player);
                player.sendActionBar(Component.empty());
                RaidText.send(plugin, player, "message.scouting_restart");
            }

            scouting.remove(uuid);
            scoutingTargets.remove(uuid);
            scoutingStartedAt.remove(uuid);
            scoutingEndsAt.remove(uuid);
            returnData.remove(uuid);
            inventorySnapshots.remove(uuid);
        }

        for (UUID uuid : new HashSet<>(raidPlayers)) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                teleportRaidPlayerBack(player);
                player.sendActionBar(Component.empty());
                RaidText.send(plugin, player, "message.raid_restart");
            }

            raidPlayers.remove(uuid);
            joinedRaidTargets.remove(uuid);
            joinedRaidAttackers.remove(uuid);
        }

        activeFactionRaids.clear();
    }
}
