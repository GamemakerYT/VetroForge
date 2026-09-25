// src/main/java/com/vetroforge/raid/RaidManager.java
package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.scheduler.BukkitTask;

import java.time.Duration;
import java.util.*;

public class RaidManager implements Listener {

    /** Fuer /f rewind gespeicherter Vorher-Zustand eines waehrend des Raids zerstoerten Blocks. */
    public record BlockSnapshot(String world, int x, int y, int z, Material material) {
        public void restore() {
            World w = Bukkit.getWorld(world);
            if (w == null) return;
            Block block = w.getBlockAt(x, y, z);
            if (block.getType() == Material.AIR) block.setType(material, false);
        }
    }

    /** Ergebnis eines Raids, solange das Rewind-Fenster (raid.rewind.windowHours) noch offen ist. */
    private record RewindOffer(List<BlockSnapshot> blocks, long expiresAtMs) {}

    public static class Session {
        public final String attackerFaction;
        public final String targetFaction;

        public int secondsLeft;
        public int obsidianGoal;
        public int obsidianBroken = 0;
        public int phase = 1;
        public boolean fightEnabled = false;
        public final Set<UUID> raiders = new HashSet<>();
        public final Map<UUID, Integer> deaths = new HashMap<>();
        public final Set<UUID> eliminated = new HashSet<>();
        /** Verbuendete Faction-Mitglieder, die sich per /raid defend aktiv der Verteidigung
         *  angeschlossen haben. Die Mitglieder der angegriffenen Faction selbst sind NICHT
         *  hier drin -- die werden schon ueber ihre eigene Faction-Zugehoerigkeit erkannt. */
        public final Set<UUID> allyDefenders = new HashSet<>();
        /** Fuer /f rewind: jeder waehrend des Raids zerstoerte Obsidian-Block, damit die
         *  verteidigende Faction die Mauer gegen Bezahlung gezielt wiederherstellen kann. */
        public final List<BlockSnapshot> destroyedBlocks = new ArrayList<>();

        public Session(String attacker, String target, int sec, int goal) {
            this.attackerFaction = attacker;
            this.targetFaction = target;
            this.secondsLeft = sec;
            this.obsidianGoal = Math.max(1, goal);
        }
    }

    private final FactionsPlugin plugin;
    private final Map<String, Session> activeByTarget = new HashMap<>();
    private final Map<String, Session> activeByAttacker = new HashMap<>();
    private final Map<String, BossBar> bars = new HashMap<>();
    private final Map<String, RewindOffer> pendingRewinds = new HashMap<>();
    private final NamespacedKey revengeKey;
    private BukkitTask tickerTask;

    public RaidManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.revengeKey = new NamespacedKey(plugin, "revenge_target");
        startTicker();
    }

    private Duration revengeWindow() {
        return Duration.ofHours(Math.max(1, plugin.getConfig().getInt("raid.revenge.windowHours", 24)));
    }

    public boolean isFactionInRaid(String f) {
        return f != null && (activeByTarget.containsKey(f) || activeByAttacker.containsKey(f));
    }

    public Session getSessionByTarget(String f) {
        return activeByTarget.get(f);
    }

    public Session getSessionByAttacker(String f) {
        return activeByAttacker.get(f);
    }

    public List<String> getActiveTargets() {
        return new ArrayList<>(activeByTarget.keySet());
    }
    public Session getRaidForPlayer(UUID uuid) {
        for (Session s : activeByTarget.values()) {
            if (s.raiders.contains(uuid)) return s;
        }
        return null;
    }

    /** Session in der ein Spieler entweder als Angreifer (raiders-Set) ODER als Verteidiger
     *  (Mitglied der angegriffenen Faction) mitkaempft -- fuer das Leben-System, das beide Seiten zaehlt. */
    public Session getSessionForParticipant(Player player) {
        Session asRaider = getRaidForPlayer(player.getUniqueId());
        if (asRaider != null) return asRaider;

        for (Session s : activeByTarget.values()) {
            if (s.allyDefenders.contains(player.getUniqueId())) return s;
        }

        if (plugin.getFactionManager() == null) return null;
        String faction = plugin.getFactionManager().getFactionOf(player.getUniqueId());
        if (faction == null) return null;
        return activeByTarget.get(faction);
    }

    /** Faction-Allianz eines Verteidigers, der der angegriffenen Faction per /raid defend beisteht. */
    public boolean joinAsAllyDefender(String targetFaction, UUID uuid) {
        Session s = activeByTarget.get(targetFaction);
        if (s == null) return false;
        s.allyDefenders.add(uuid);
        return true;
    }

    public boolean isAllyDefender(Session session, UUID uuid) {
        return session != null && session.allyDefenders.contains(uuid);
    }

    /** Sucht eine Faction, die aktuell raidet UND mit ownFaction verbuendet ist -- fuer /raid defend. */
    public String findAlliedRaidTarget(String ownFaction) {
        if (ownFaction == null || plugin.getFactionManager() == null) return null;
        var allies = plugin.getFactionManager().getAllies(ownFaction);
        for (String target : activeByTarget.keySet()) {
            if (allies.contains(target)) return target;
        }
        return null;
    }

    public boolean isRaider(Session session, UUID uuid) {
        return session != null && session.raiders.contains(uuid);
    }

    /** Zaehlt einen Tod fuer das Leben-System. Gibt true zurueck, wenn der Spieler dadurch gerade eliminiert wurde. */
    public boolean recordDeath(Player player) {
        Session session = getSessionForParticipant(player);
        if (session == null) return false;

        int max = Math.max(1, plugin.getConfig().getInt("raid.lives.max", 3));
        int now = session.deaths.merge(player.getUniqueId(), 1, Integer::sum);
        if (now >= max && session.eliminated.add(player.getUniqueId())) {
            return true;
        }
        return false;
    }

    public int livesRemaining(Player player) {
        Session session = getSessionForParticipant(player);
        if (session == null) return -1;
        int max = Math.max(1, plugin.getConfig().getInt("raid.lives.max", 3));
        int used = session.deaths.getOrDefault(player.getUniqueId(), 0);
        return Math.max(0, max - used);
    }

    public boolean isEliminated(Player player) {
        Session session = getSessionForParticipant(player);
        return session != null && session.eliminated.contains(player.getUniqueId());
    }

    public String getRandomTarget(String attackerFaction) {
        List<String> all = new ArrayList<>(plugin.getFactionManager().getAllFactionNames());
        all.removeIf(f -> f.equalsIgnoreCase(attackerFaction));
        all.removeIf(this::isFactionInRaid);
        return all.isEmpty() ? null : all.get(new Random().nextInt(all.size()));
    }

    /** Zeigt Factions, die die eigene Faction in den letzten 24h angegriffen haben; Klick startet Scouting gegen sie. */
    public void openRevengeMenu(Player p) {
        String faction = plugin.getFactionManager() == null ? null : plugin.getFactionManager().getFactionOf(p.getUniqueId());
        Duration window = revengeWindow();
        List<com.vetroforge.factions.FactionInboxService.AttackEntry> attacks =
                (faction == null || plugin.getFactionInboxService() == null)
                        ? List.of()
                        : plugin.getFactionInboxService().recentAttacks(faction, window);

        if (attacks.isEmpty()) {
            RaidText.send(plugin, p, "message.no_revenge_targets");
            return;
        }

        Inventory inv = Bukkit.createInventory(new RevengeHolder(), 27, RaidText.tr(plugin, p, "gui.revenge_title"));
        for (var attack : attacks.subList(0, Math.min(attacks.size(), inv.getSize()))) {
            ItemStack it = new ItemStack(Material.PLAYER_HEAD);
            ItemMeta meta = it.getItemMeta();
            if (meta != null) {
                long remainingMs = window.toMillis() - Duration.between(attack.time(), java.time.Instant.now()).toMillis();
                meta.setDisplayName("§c§l" + attack.attacker());
                meta.setLore(List.of(
                        "§7Angriff §fvor " + agoText(attack.time()),
                        "§7Läuft ab in §f" + durationText(Math.max(0, remainingMs)),
                        "",
                        "§eKlick §8» §fScouting starten"
                ));
                meta.getPersistentDataContainer().set(revengeKey, PersistentDataType.STRING, attack.attacker());
                it.setItemMeta(meta);
            }
            inv.addItem(it);
        }

        p.openInventory(inv);
    }

    /** Marker statt Title-String-Vergleich -- ein Sprachwechsel während das Menü offen ist, darf die Erkennung nicht brechen. */
    private static final class RevengeHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    @EventHandler
    public void onRevengeMenuClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof RevengeHolder)) return;
        event.setCancelled(true);

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String attacker = clicked.getItemMeta().getPersistentDataContainer().get(revengeKey, PersistentDataType.STRING);
        if (attacker == null) return;

        player.closeInventory();
        if (plugin.getRaidSessionService() == null) {
            RaidText.send(plugin, player, "message.system_unavailable");
            return;
        }
        plugin.getRaidSessionService().startScouting(player, attacker);
    }

    private String agoText(java.time.Instant time) {
        long minutes = Math.max(0, Duration.between(time, java.time.Instant.now()).toMinutes());
        if (minutes < 1) return "<1m";
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    private String durationText(long millis) {
        long minutes = Math.max(0, millis / 60_000L);
        if (minutes < 60) return minutes + "m";
        return (minutes / 60) + "h " + (minutes % 60) + "m";
    }

    /** Title, Broadcast-Zeile und Sound -- ausschließlich an die Online-Mitglieder der angegriffenen Faction. */
    private void notifyDefenders(String target, String attacker) {
        if (plugin.getFactionManager() == null) return;
        for (UUID uuid : plugin.getFactionManager().getMembers(target)) {
            Player member = Bukkit.getPlayer(uuid);
            if (member == null || !member.isOnline()) continue;
            RaidText.send(plugin, member, "broadcast.started", "attacker", attacker, "target", target);
            member.sendTitle(
                    RaidText.tr(plugin, member, "title.under_attack", "attacker", attacker),
                    RaidText.tr(plugin, member, "title.under_attack_subtitle"),
                    10, 60, 20
            );
            RaidText.send(plugin, member, "message.under_attack", "attacker", attacker);
            try { member.playSound(member.getLocation(), Sound.EVENT_RAID_HORN, 1f, 1f); } catch (Throwable ignored) {}
        }
    }

    public boolean startRaid(String attacker, String target, int sec, int goal) {

        if (attacker == null || target == null) return false;
        if (attacker.equalsIgnoreCase(target)) return false;
        if (isFactionInRaid(attacker) || isFactionInRaid(target)) return false;

        Session s = new Session(attacker, target, sec, goal);

        activeByTarget.put(target, s);
        activeByAttacker.put(attacker, s);
        if (plugin.getFactionInboxService() != null) {
            plugin.getFactionInboxService().add(target, "attacked", attacker, target);
        }
        if (plugin.getRaidSessionService() != null) {
            plugin.getRaidSessionService().registerActiveRaid(attacker, target);
        }

        BossBar bar = Bukkit.createBossBar("§cRaid", BarColor.RED, BarStyle.SEGMENTED_10);
        bars.put(target, bar);

        notifyDefenders(target, attacker);

        try {
            if (plugin.discordBridge() != null) {
                plugin.discordBridge().insertAlert(
                        "raid_started",
                        "Raid Started",
                        attacker + " has started a raid against " + target + ".",
                        attacker,
                        null
                );
            }
        } catch (Throwable ignored) {}

        try {
            if (plugin.factionDiscordSync() != null) {
                plugin.factionDiscordSync().syncFaction(attacker);
                plugin.factionDiscordSync().syncFaction(target);
            }
        } catch (Throwable ignored) {}

        return true;
    }

    public void addRaider(String target, UUID uuid) {
        Session s = activeByTarget.get(target);
        if (s == null) return;
        s.raiders.add(uuid);
        Player player = Bukkit.getPlayer(uuid);
        BossBar bar = bars.get(target);
        if (player != null && bar != null) bar.addPlayer(player);
    }

    public void removeRaider(UUID uuid) {
        if (uuid == null) return;
        for (Session session : activeByTarget.values()) {
            session.raiders.remove(uuid);
        }
        for (BossBar bar : bars.values()) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null) bar.removePlayer(player);
        }
    }

    public void addSpectator(Player p, String target) {
        Session s = activeByTarget.get(target);
        if (s == null) return;

        s.raiders.add(p.getUniqueId());

        p.setGameMode(GameMode.SPECTATOR);
        RaidText.send(plugin, p, "message.spectating");
    }

    public void enableFighting(Player p) {
        p.setGameMode(GameMode.SURVIVAL);

        p.sendTitle(
                RaidText.tr(plugin, p, "title.fight"),
                RaidText.tr(plugin, p, "title.fight_subtitle"),
                4, 24, 6
        );
    }
    /** Vom ExplosionListener aufgerufen, nachdem das HP-System einen Block auf AIR gesetzt hat --
     *  das Material wird explizit uebergeben, weil der Block zu diesem Zeitpunkt schon leer ist. */
    public void recordDestroyedBlock(String target, Block block, Material original) {
        Session s = activeByTarget.get(target);
        if (s == null) return;
        s.destroyedBlocks.add(new BlockSnapshot(block.getWorld().getName(),
                block.getX(), block.getY(), block.getZ(), original));
    }

    public void addObsidianBreak(String target, UUID breaker) {

        Session s = activeByTarget.get(target);
        if (s == null) return;

        s.obsidianBroken++;

        if (s.phase == 1 && s.obsidianBroken >= s.obsidianGoal / 3) {
            s.phase = 2;
            s.fightEnabled = true;
            Bukkit.getOnlinePlayers().forEach(player -> RaidText.send(plugin, player, "broadcast.fight"));
        }

        if (s.obsidianBroken >= s.obsidianGoal) {
            stopRaid(target);
        }
    }

    public void stopRaid(String target) {
        stopRaid(target, false, "completed");
    }

    public void stopRaid(String target, boolean timeUp, String reason) {

        Session s = activeByTarget.remove(target);
        if (s == null) return;

        activeByAttacker.remove(s.attackerFaction);

        BossBar bar = bars.remove(target);
        if (bar != null) bar.removeAll();

        if (!s.destroyedBlocks.isEmpty()) {
            long windowMs = Math.max(1, plugin.getConfig().getInt("raid.rewind.windowHours", 24)) * 3_600_000L;
            pendingRewinds.put(target, new RewindOffer(new ArrayList<>(s.destroyedBlocks), System.currentTimeMillis() + windowMs));
        }

        Bukkit.getOnlinePlayers().forEach(player ->
                RaidText.send(plugin, player, "broadcast.ended", "reason",
                        RaidText.tr(plugin, player, "reason." + reason)));

        String winner = timeUp ? s.targetFaction : s.attackerFaction;
        broadcastStats(s, winner, reason);
        if (plugin.getRaidSessionService() != null) {
            plugin.getRaidSessionService().endRaidForFaction(s.attackerFaction);
        }
        if (s.obsidianBroken > 0 && plugin.getRaidRewardService() != null) {
            plugin.getRaidRewardService().rewardProgress(
                    s.attackerFaction, s.targetFaction, s.obsidianBroken, s.obsidianGoal
            );
        }

        try {
            if (plugin.discordBridge() != null) {
                plugin.discordBridge().insertAlert(
                        "raid_ended",
                        "Raid Ended",
                        "The raid between " + s.attackerFaction + " and " + s.targetFaction
                                + " has ended. Winner: " + winner + ". Reason: " + reason + ".",
                        winner,
                        null
                );
            }
        } catch (Throwable ignored) {}

        try {
            if (plugin.factionDiscordSync() != null) {
                plugin.factionDiscordSync().syncFaction(s.attackerFaction);
                plugin.factionDiscordSync().syncFaction(s.targetFaction);
            }
        } catch (Throwable ignored) {}
    }

    public boolean stopRaidForAttacker(String attackerFaction, String reason) {
        Session session = getSessionByAttacker(attackerFaction);
        if (session == null) return false;
        stopRaid(session.targetFaction, false, reason);
        return true;
    }

    /* ===== Faction-Rewind: bezahlte Wiederherstellung der waehrend des Raids zerstoerten Mauer ===== */

    private RewindOffer freshRewindOffer(String faction) {
        RewindOffer offer = pendingRewinds.get(faction);
        if (offer == null) return null;
        if (offer.expiresAtMs() < System.currentTimeMillis()) {
            pendingRewinds.remove(faction);
            return null;
        }
        return offer;
    }

    public boolean hasRewindOffer(String faction) {
        return faction != null && freshRewindOffer(faction) != null;
    }

    public int rewindBlockCount(String faction) {
        RewindOffer offer = faction == null ? null : freshRewindOffer(faction);
        return offer == null ? 0 : offer.blocks().size();
    }

    public int rewindCost(String faction) {
        int perBlock = Math.max(0, plugin.getConfig().getInt("raid.rewind.costPerBlock", 25));
        return rewindBlockCount(faction) * perBlock;
    }

    /** Stellt nur die zerstoerten Bloecke wieder her (Struktur), keine Truheninhalte --
     *  die bleiben, wie in der Spec gewollt, beim Angreifer. */
    public boolean performRewind(String faction) {
        RewindOffer offer = faction == null ? null : freshRewindOffer(faction);
        if (offer == null) return false;
        for (BlockSnapshot snapshot : offer.blocks()) {
            snapshot.restore();
        }
        pendingRewinds.remove(faction);
        return true;
    }

    public void sendStats(Player player) {
        if (player == null) return;
        Session session = getRaidForPlayer(player.getUniqueId());
        if (session == null) {
            RaidText.send(plugin, player, "message.no_active_raid");
            return;
        }
        sendStats(player, session, "-");
    }

    private void broadcastStats(Session session, String winner, String reason) {
        if (plugin.getFactionManager() == null) return;
        for (UUID uuid : plugin.getFactionManager().getMembers(session.attackerFaction)) {
            Player player = Bukkit.getPlayer(uuid);
            if (player != null && player.isOnline()) {
                sendStats(player, session, winner);
                RaidText.send(plugin, player, "message.stats_reason",
                        "reason", RaidText.tr(plugin, player, "reason." + reason));
            }
        }
    }

    private void sendStats(Player player, Session session, String winner) {
        RaidText.send(plugin, player, "message.stats_title");
        RaidText.send(plugin, player, "message.stats_target", "target", session.targetFaction);
        RaidText.send(plugin, player, "message.stats_obsidian",
                "broken", session.obsidianBroken, "goal", session.obsidianGoal);
        RaidText.send(plugin, player, "message.stats_raiders", "raiders", session.raiders.size());
        RaidText.send(plugin, player, "message.stats_winner", "winner", winner);
    }

    private void startTicker() {

        tickerTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {

            for (Session s : new ArrayList<>(activeByTarget.values())) {

                s.secondsLeft--;

                BossBar bar = bars.get(s.targetFaction);
                if (bar != null) {
                    bar.setProgress((double) s.obsidianBroken / s.obsidianGoal);
                    bar.setTitle("§c" + s.obsidianBroken + "/" + s.obsidianGoal + " §8| §e" + s.secondsLeft + "s");
                }
                for (UUID uuid : new HashSet<>(s.raiders)) {
                    Player player = Bukkit.getPlayer(uuid);
                    if (player != null && player.isOnline() && plugin.getSidebarScoreboardManager() != null) {
                        plugin.getSidebarScoreboardManager().showRaidBoard(
                                player, s.targetFaction, s.obsidianBroken, s.obsidianGoal, s.secondsLeft, s.raiders.size()
                        );
                    }
                }

                if (s.secondsLeft <= 0) {
                    stopRaid(s.targetFaction, true, "timeout");
                }
            }

        }, 20L, 20L);
    }

    public void shutdown() {
        if (tickerTask != null) tickerTask.cancel();
        tickerTask = null;
        for (BossBar bar : bars.values()) bar.removeAll();
        bars.clear();
        activeByTarget.clear();
        activeByAttacker.clear();
    }
}
