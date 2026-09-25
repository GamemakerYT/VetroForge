package com.vetroforge.raid.listener;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.display.SidebarScoreboardManager;
import com.vetroforge.factions.FactionManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerGameModeChangeEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import com.vetroforge.raid.RaidText;

public class RaidScoutingRestrictionListener implements Listener {

    private final FactionsPlugin plugin;
    private final SidebarScoreboardManager scoreboard;
    private final RaidStartItemBridge raidStartItemBridge;

    private final Set<UUID> lastScouting = new HashSet<>();
    private final Map<UUID, Long> notifyCooldown = new HashMap<>();
    private final Map<UUID, BoardState> lastBoardState = new HashMap<>();
    private final Set<UUID> boardDirty = new HashSet<>();

    private BukkitTask task;

    public RaidScoutingRestrictionListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.scoreboard = plugin.getSidebarScoreboardManager();
        this.raidStartItemBridge = new RaidStartItemBridge(plugin);
    }

    public void startTask() {
        stopTask();

        this.task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (plugin.getRaidSessionService() == null) return;

            Set<UUID> nowScouting = new HashSet<>();

            for (Player player : Bukkit.getOnlinePlayers()) {
                if (plugin.getRaidSessionService().isScoutingExpired(player)) {
                    plugin.getRaidSessionService().stopScoutingExpired(player);
                    continue;
                }

                if (!isScouting(player)) continue;

                nowScouting.add(player.getUniqueId());

                String ownFaction = getOwnFactionId(player);
                String targetFaction = getTargetFactionId(player);

                enforceScoutingState(player);
                plugin.getRaidSessionService().ensureScoutingInventory(player);
                if (!isInRaid(player)) {
                    sendScoutingActionBar(player);
                }

                int ownOnline = countFactionOnline(ownFaction);
                int targetOnline = countFactionOnline(targetFaction);
                int ownScouting = countFactionScouting(ownFaction);

                BoardState current = new BoardState(
                        ownFaction,
                        targetFaction,
                        ownOnline,
                        targetOnline,
                        ownScouting
                );

                UUID uuid = player.getUniqueId();

                boolean firstScoutingTick = !lastScouting.contains(uuid);
                boolean changed = !current.equals(lastBoardState.get(uuid));
                boolean dirty = boardDirty.remove(uuid);

                if (firstScoutingTick || changed || dirty) {
                    scoreboard.showScoutingBoard(
                            player,
                            getFactionDisplayName(ownFaction),
                            getFactionDisplayName(targetFaction),
                            ownOnline,
                            targetOnline,
                            ownScouting
                    );

                    lastBoardState.put(uuid, current);
                }

                if (firstScoutingTick) {
                    notifyTargetFaction(player, targetFaction);
                }
            }

            for (UUID old : new HashSet<>(lastScouting)) {
                if (nowScouting.contains(old)) continue;

                Player player = Bukkit.getPlayer(old);
                if (player != null && player.isOnline()) {
                    removeScoutingState(player);
                }

                lastBoardState.remove(old);
                boardDirty.remove(old);
            }

            lastScouting.clear();
            lastScouting.addAll(nowScouting);

        }, 20L, 20L);
    }

    public void stopTask() {
        if (task != null) {
            try {
                task.cancel();
            } catch (Throwable ignored) {}
        }

        task = null;

        for (UUID uuid : new HashSet<>(lastScouting)) {
            Player player = Bukkit.getPlayer(uuid);

            if (player != null && player.isOnline()) {
                removeScoutingState(player);
            }
        }

        lastScouting.clear();
        notifyCooldown.clear();
        lastBoardState.clear();
        boardDirty.clear();
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent event) {
        Bukkit.getScheduler().runTaskLater(plugin, this::markAllScoutingBoardsDirty, 5L);
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onQuit(PlayerQuitEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();

        lastScouting.remove(uuid);
        lastBoardState.remove(uuid);
        boardDirty.remove(uuid);
        notifyCooldown.remove(uuid);

        Bukkit.getScheduler().runTaskLater(plugin, this::markAllScoutingBoardsDirty, 5L);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onToggleFlight(PlayerToggleFlightEvent event) {
        Player player = event.getPlayer();

        if (!isScouting(player)) return;

        event.setCancelled(false);
        enforceScoutingState(player);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onGameModeChange(PlayerGameModeChangeEvent event) {
        Player player = event.getPlayer();

        if (!isScouting(player)) return;

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline()) return;
            if (!isScouting(player)) return;

            enforceScoutingState(player);
        }, 2L);
    }

    private void markAllScoutingBoardsDirty() {
        if (plugin.getRaidSessionService() == null) return;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (isScouting(player)) {
                boardDirty.add(player.getUniqueId());
            }
        }
    }

    private boolean isScouting(Player player) {
        try {
            return plugin.getRaidSessionService() != null
                    && plugin.getRaidSessionService().isScouting(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private boolean isInRaid(Player player) {
        if (player == null) return false;
        if (plugin.getRaidManager() == null || plugin.getFactionManager() == null) return false;

        String faction = plugin.getFactionManager().getFactionOf(player.getUniqueId());
        return faction != null && plugin.getRaidManager().isFactionInRaid(faction);
    }

    private void sendScoutingActionBar(Player player) {
        int seconds = plugin.getRaidSessionService().getScoutingRemainingSeconds(player);

        int min = Math.max(0, seconds) / 60;
        int sec = Math.max(0, seconds) % 60;

        String time = min + ":" + (sec < 10 ? "0" : "") + sec;

        String msg = com.vetroforge.raid.RaidText.tr(plugin, player, "message.scouting_actionbar", "remaining", time);

        player.sendActionBar(LegacyComponentSerializer.legacySection().deserialize(msg));
    }

    private void enforceScoutingState(Player player) {
        if (player == null || !player.isOnline()) return;

        if (player.getGameMode() == GameMode.SPECTATOR) {
            player.setGameMode(GameMode.SURVIVAL);
        }

        if (!player.getAllowFlight()) {
            player.setAllowFlight(true);
        }

        player.addPotionEffect(new PotionEffect(
                PotionEffectType.INVISIBILITY,
                60,
                0,
                false,
                false,
                false
        ));
    }

    private void removeScoutingState(Player player) {
        if (player == null || !player.isOnline()) return;

        if (player.getGameMode() != GameMode.CREATIVE && player.getGameMode() != GameMode.SPECTATOR) {
            player.setFlying(false);
            player.setAllowFlight(false);
        }

        player.removePotionEffect(PotionEffectType.INVISIBILITY);
        raidStartItemBridge.remove(player);

        scoreboard.refreshWarzoneLineIfInZone(player);
    }

    private void notifyTargetFaction(Player scout, String targetFactionId) {
        if (targetFactionId == null || targetFactionId.isBlank() || targetFactionId.equals("-")) return;

        long now = System.currentTimeMillis();
        long last = notifyCooldown.getOrDefault(scout.getUniqueId(), 0L);

        if (now - last < 10_000L) return;

        notifyCooldown.put(scout.getUniqueId(), now);

        String ownFaction = getFactionDisplayName(getOwnFactionId(scout));
        String targetFaction = getFactionDisplayName(targetFactionId);

        for (Player target : Bukkit.getOnlinePlayers()) {
            String faction = getOwnFactionId(target);

            if (!targetFactionId.equalsIgnoreCase(faction)) continue;

            RaidText.send(plugin, target, "message.faction_scouted");
            RaidText.send(plugin, target, "message.attacker_faction", "faction", ownFaction);
            target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 1.0f, 0.7f);
        }

        RaidText.send(plugin, scout, "message.scouting_target", "target", targetFaction);
        scout.playSound(scout.getLocation(), Sound.UI_BUTTON_CLICK, 1.0f, 1.4f);
    }

    private String getOwnFactionId(Player player) {
        try {
            FactionManager fm = plugin.getFactionManager();
            if (fm == null) return "-";

            String faction = fm.getFactionOf(player.getUniqueId());
            return faction == null || faction.isBlank() ? "-" : faction;
        } catch (Throwable ignored) {
            return "-";
        }
    }

    private String getTargetFactionId(Player player) {
        try {
            if (plugin.getRaidSessionService() == null) return "-";

            String target = plugin.getRaidSessionService().getScoutingTargetFaction(player);

            if (target == null || target.isBlank()) {
                return "-";
            }

            return target;
        } catch (Throwable ignored) {
            return "-";
        }
    }

    private String getFactionDisplayName(String factionId) {
        if (factionId == null || factionId.isBlank() || factionId.equals("-")) return "-";

        try {
            FactionManager fm = plugin.getFactionManager();
            if (fm == null) return factionId;

            String display = fm.getDisplayName(factionId);
            return display == null || display.isBlank() ? factionId : display;
        } catch (Throwable ignored) {
            return factionId;
        }
    }

    private int countFactionOnline(String factionId) {
        if (factionId == null || factionId.isBlank() || factionId.equals("-")) return 0;

        int count = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            String playerFaction = getOwnFactionId(player);

            if (factionId.equalsIgnoreCase(playerFaction)) {
                count++;
            }
        }

        return count;
    }

    private int countFactionScouting(String factionId) {
        if (factionId == null || factionId.isBlank() || factionId.equals("-")) return 0;

        int count = 0;

        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!isScouting(player)) continue;

            String playerFaction = getOwnFactionId(player);

            if (factionId.equalsIgnoreCase(playerFaction)) {
                count++;
            }
        }

        return count;
    }

    private static final class BoardState {

        private final String ownFaction;
        private final String targetFaction;
        private final int ownOnline;
        private final int targetOnline;
        private final int ownScouting;

        private BoardState(String ownFaction, String targetFaction, int ownOnline, int targetOnline, int ownScouting) {
            this.ownFaction = ownFaction;
            this.targetFaction = targetFaction;
            this.ownOnline = ownOnline;
            this.targetOnline = targetOnline;
            this.ownScouting = ownScouting;
        }

        @Override
        public boolean equals(Object o) {
            if (!(o instanceof BoardState other)) return false;

            return ownOnline == other.ownOnline
                    && targetOnline == other.targetOnline
                    && ownScouting == other.ownScouting
                    && Objects.equals(ownFaction, other.ownFaction)
                    && Objects.equals(targetFaction, other.targetFaction);
        }

        @Override
        public int hashCode() {
            return Objects.hash(ownFaction, targetFaction, ownOnline, targetOnline, ownScouting);
        }
    }

    private static final class RaidStartItemBridge {

        private final FactionsPlugin plugin;
        private final NamespacedKey fallbackKey;
        private ItemStack cachedTemplate;

        private RaidStartItemBridge(FactionsPlugin plugin) {
            this.plugin = plugin;
            this.fallbackKey = new NamespacedKey(plugin, "raid_start_item");
        }

        private void giveIfMissing(Player player) {
            if (player == null || !player.isOnline()) return;

            PlayerInventory inv = player.getInventory();

            for (ItemStack content : inv.getContents()) {
                if (isRaidStartItem(content)) {
                    return;
                }
            }

            ItemStack item = createRaidStartItem();
            if (item == null) return;

            int preferredSlot = 4;

            if (inv.getItem(preferredSlot) == null || inv.getItem(preferredSlot).getType() == Material.AIR) {
                inv.setItem(preferredSlot, item);
            } else {
                inv.addItem(item);
            }
        }

        private void remove(Player player) {
            if (player == null || !player.isOnline()) return;

            PlayerInventory inv = player.getInventory();
            ItemStack[] contents = inv.getContents();

            for (int i = 0; i < contents.length; i++) {
                ItemStack item = contents[i];

                if (isRaidStartItem(item)) {
                    contents[i] = null;
                }
            }

            inv.setContents(contents);
        }

        private boolean isRaidStartItem(ItemStack item) {
            if (item == null || item.getType() == Material.AIR) return false;

            try {
                ItemMeta meta = item.getItemMeta();

                if (meta != null && meta.getPersistentDataContainer().has(fallbackKey, PersistentDataType.BYTE)) {
                    return true;
                }
            } catch (Throwable ignored) {}

            try {
                ItemStack template = createRaidStartItem();

                return template != null && item.isSimilar(template);
            } catch (Throwable ignored) {
                return false;
            }
        }

        private ItemStack createRaidStartItem() {
            if (cachedTemplate != null) {
                return cachedTemplate.clone();
            }

            ItemStack reflected = tryCreateFromExistingRaidStartItemClass();

            if (reflected != null) {
                cachedTemplate = reflected.clone();
                return reflected.clone();
            }

            ItemStack fallback = new ItemStack(Material.NETHER_STAR);
            ItemMeta meta = fallback.getItemMeta();

            if (meta != null) {
                meta.setDisplayName(com.vetroforge.util.VetroForgeUtil.color(
                        plugin.getI18n().tr("de", "raid.session.legacy_start")
                ));
                meta.getPersistentDataContainer().set(fallbackKey, PersistentDataType.BYTE, (byte) 1);
                fallback.setItemMeta(meta);
            }

            cachedTemplate = fallback.clone();
            return fallback;
        }

        private ItemStack tryCreateFromExistingRaidStartItemClass() {
            String[] classNames = {
                    "com.vetroforge.raid.item.RaidStartItem",
                    "com.vetroforge.raid.items.RaidStartItem",
                    "com.vetroforge.items.RaidStartItem",
                    "com.vetroforge.raid.RaidStartItem"
            };

            String[] staticMethods = {
                    "create",
                    "createItem",
                    "getItem",
                    "build",
                    "item"
            };

            for (String className : classNames) {
                try {
                    Class<?> clazz = Class.forName(className);

                    for (String methodName : staticMethods) {
                        try {
                            Method method = clazz.getMethod(methodName, FactionsPlugin.class);

                            if (!Modifier.isStatic(method.getModifiers())) continue;

                            Object value = method.invoke(null, plugin);

                            if (value instanceof ItemStack item) {
                                return item;
                            }
                        } catch (Throwable ignored) {}

                        try {
                            Method method = clazz.getMethod(methodName);

                            if (!Modifier.isStatic(method.getModifiers())) continue;

                            Object value = method.invoke(null);

                            if (value instanceof ItemStack item) {
                                return item;
                            }
                        } catch (Throwable ignored) {}
                    }
                } catch (Throwable ignored) {}
            }

            return null;
        }
    }
}
