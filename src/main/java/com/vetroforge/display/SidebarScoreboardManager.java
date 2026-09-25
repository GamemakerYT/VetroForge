// src/main/java/com/vetroforge/display/SidebarScoreboardManager.java
package com.vetroforge.display;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.factions.FactionManager;
import com.vetroforge.scoreboard.ScoreboardCrystalHelper;
import com.vetroforge.util.PrestigeUtil;
import com.vetroforge.util.VetroForgeUtil;
import com.vetroforge.warzone.WarzoneRegionManager;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.World;
import org.bukkit.entity.Player;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;

import java.util.*;

/**
 * Einheitlicher Sidebar-Scoreboard-Manager für VetroForge.
 *
 * Boards:
 * - Lobby / Spawn
 * - Warzone
 * - Raid
 *
 * Wichtig:
 * - Nutzt Cache pro Spieler
 * - Rebuildet nicht jedes Mal komplett neu
 * - LootDrop-Status bleibt bewusst aus der Sidebar
 */
public class SidebarScoreboardManager {

    /* =======================================================
       Raid Context
       ======================================================= */

    public static final class RaidContext {

        public final String target;
        public final int obsidianBroken;
        public final int obsidianGoal;
        public final int secondsLeft;
        public final Set<UUID> raiders;

        public RaidContext(String target, int broken, int goal, int secs) {
            this(target, broken, goal, secs, new HashSet<>());
        }

        public RaidContext(String target, int broken, int goal, int secs, Set<UUID> raiders) {
            this.target = target;
            this.obsidianBroken = broken;
            this.obsidianGoal = goal;
            this.secondsLeft = secs;
            this.raiders = raiders == null ? new HashSet<>() : raiders;
        }
    }

    /* =======================================================
       Styling
       ======================================================= */

    private static final String SEP = "§8§m────────────";
    private static final String BRAND_TITLE = "§c§lVetroForge";

    private static final String ICON_RANK     = "✦";
    private static final String ICON_FACTION  = "⚔";
    private static final String ICON_CRYSTAL  = "❖";
    private static final String ICON_MONEY    = "⛃";
    private static final String ICON_PRESTIGE = "◆";
    private static final String ICON_KD       = "⚖";
    private static final String ICON_WZ_PLAY  = "👥";
    private static final String ICON_COINS    = "●";
    private static final String ICON_ELO      = "♜";

    private final FactionsPlugin plugin;
    private final Map<UUID, org.bukkit.scoreboard.Scoreboard> cache = new HashMap<>();
    private final Map<UUID, String> rendered = new HashMap<>();

    private final Map<UUID, org.bukkit.scoreboard.Scoreboard> overlayPrev = new HashMap<>();
    private final Map<UUID, Integer> overlayTask = new HashMap<>();

    public SidebarScoreboardManager(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    /* =======================================================
       Public API
       ======================================================= */

    public void showLobbyBoard(Player p) {
        if (p == null || !p.isOnline()) return;

        String factionName = getFactionName(p);
        String title = BRAND_TITLE;

        List<String> lines = new ArrayList<>();
        ScoreboardCrystalHelper crystals = new ScoreboardCrystalHelper(plugin);

        addBlank(lines);
        addIconPair(lines, ICON_RANK, tr(p, "scoreboard.rank"), getRankName(p));
        addBlank(lines);
        addIconPair(lines, ICON_FACTION, tr(p, "scoreboard.faction"), "§c" + factionName);
        addBlank(lines);
        addIconPair(lines, ICON_ELO, tr(p, "scoreboard.elo"), getElo(p));
        addBlank(lines);
        addIconPair(lines, ICON_COINS, tr(p, "scoreboard.coins"), getCoins(p));
        addBlank(lines);
        addIconPair(lines, ICON_CRYSTAL, tr(p, plugin.getRaidCrystalManager() != null
                && !plugin.getRaidCrystalManager().useFactionSource()
                ? "scoreboard.player_crystals"
                : "scoreboard.faction_crystals"), crystals.factionCrystalsLine(p));
        addSpacer(lines);
        lines.add("§cvetroforge.net");

        applyBoard(p, title, lines);
    }
// In SidebarScoreboardManager.java ergänzen:

    public void showScoutingBoard(
            Player p,
            String ownFaction,
            String targetFaction,
            int ownFactionOnline,
            int targetFactionOnline,
            int ownFactionScouting
    ) {
        if (p == null || !p.isOnline()) return;

        String title = BRAND_TITLE;

        List<String> lines = new ArrayList<>();
        addSpacer(lines);
        addPair(lines, tr(p, "scoreboard.status"), tr(p, "scoreboard.scouting_active"));
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.own_faction"), ownFaction);
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.target_faction"), targetFaction);
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.own_online"), String.valueOf(ownFactionOnline));
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.target_online"), String.valueOf(targetFactionOnline));
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.scouts"), String.valueOf(ownFactionScouting));
        addSpacer(lines);
        lines.add("§cvetroforge.net");

        applyBoard(p, title, lines);
    }
    public void showJoinedRaidBoard(Player p, String targetFaction, int raiders, int joinSecondsLeft) {
        if (p == null || !p.isOnline()) return;

        String title = BRAND_TITLE;

        List<String> lines = new ArrayList<>();
        addSpacer(lines);
        addPair(lines, tr(p, "scoreboard.status"), tr(p, "scoreboard.raid_active"));
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.target"), targetFaction);
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.raiders"), String.valueOf(raiders));
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.join_time"), joinSecondsLeft + "s");
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.building"), tr(p, "scoreboard.before_red_line"));
        addSpacer(lines);
        lines.add("§cvetroforge.net");

        applyBoard(p, title, lines);
    }
    public void showWarzoneBoard(Player p) {
        if (p == null || !p.isOnline()) return;

        String factionName = getFactionName(p);
        String title = BRAND_TITLE;

        List<String> lines = new ArrayList<>();
        ScoreboardCrystalHelper crystals = new ScoreboardCrystalHelper(plugin);

        addSpacer(lines);
        addPair(lines, tr(p, "scoreboard.faction"), factionName);
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.coins"), getCoins(p));
        addBlank(lines);
        addPair(lines, tr(p, plugin.getRaidCrystalManager() != null
                && !plugin.getRaidCrystalManager().useFactionSource()
                ? "scoreboard.player_crystals"
                : "scoreboard.faction_crystals"), crystals.factionCrystalsLine(p));
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.warzone_online"), String.valueOf(countWarzonePlayers()));
        addSpacer(lines);
        lines.add("§cvetroforge.net");

        applyBoard(p, title, lines);
    }

    public void showRaidBoard(Player p, RaidContext ctx) {
        if (p == null || !p.isOnline()) return;

        String title = BRAND_TITLE;

        List<String> lines = new ArrayList<>();
        addSpacer(lines);

        String target = safe(ctx != null ? ctx.target : "-");
        String obsidian = (ctx != null ? ctx.obsidianBroken : 0) + "§7/§f" + (ctx != null ? ctx.obsidianGoal : 0);
        String time = formatTime(ctx != null ? ctx.secondsLeft : 0);
        String raiders = String.valueOf(ctx != null && ctx.raiders != null ? ctx.raiders.size() : 0);

        addPair(lines, tr(p, "scoreboard.target"), target);
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.obsidian"), obsidian);
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.time"), time);
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.attackers"), raiders);
        addBlank(lines);
        addPair(lines, tr(p, "scoreboard.energy"), new ScoreboardCrystalHelper(plugin).energyLine(p));
        addSpacer(lines);
        lines.add("§cvetroforge.net");

        applyBoard(p, title, lines);
    }

    public void showRaidBoard(Player p, String target, int broken, int goal, int secondsLeft, int raidersCount) {
        Set<UUID> fake = new HashSet<>();

        for (int i = 0; i < Math.max(0, raidersCount); i++) {
            fake.add(new UUID(0L, i + 1));
        }

        showRaidBoard(p, new RaidContext(target, broken, goal, secondsLeft, fake));
    }

    public void refreshWarzoneLineIfInZone(Player p) {
        try {
            if (p == null || !p.isOnline()) return;

            WarzoneRegionManager rm = plugin.getWarzoneRegionManager();
            if (rm == null) return;

            if (!rm.isWarzone(p.getLocation())) return;

            showWarzoneBoard(p);
        } catch (Throwable ignored) {}
    }

    public void refreshWarzoneBoardForWorld(World world) {
        if (world == null) return;

        for (Player p : world.getPlayers()) {
            refreshWarzoneLineIfInZone(p);
        }
    }

    public void refreshWarzoneBoardForAll() {
        for (Player p : Bukkit.getOnlinePlayers()) {
            refreshWarzoneLineIfInZone(p);
        }
    }

    @Deprecated
    public void refreshWarzoneLootLineForWorld(World world) {
        refreshWarzoneBoardForWorld(world);
    }

    @Deprecated
    public void refreshWarzoneLootLineForAll() {
        refreshWarzoneBoardForAll();
    }

    public void remove(Player p) {
        if (p == null) return;

        try {
            UUID uuid = p.getUniqueId();

            Integer overlay = overlayTask.remove(uuid);
            if (overlay != null) {
                Bukkit.getScheduler().cancelTask(overlay);
            }

            overlayPrev.remove(uuid);
            cache.remove(uuid);
            rendered.remove(uuid);

            var sm = Bukkit.getScoreboardManager();
            if (sm != null) {
                p.setScoreboard(sm.getMainScoreboard());
            }
        } catch (Throwable ignored) {}
    }

    public void clearCache() {
        cache.clear();
        rendered.clear();
        overlayPrev.clear();

        for (Integer task : overlayTask.values()) {
            try {
                Bukkit.getScheduler().cancelTask(task);
            } catch (Throwable ignored) {}
        }

        overlayTask.clear();
    }

    /* =======================================================
       Rendering
       ======================================================= */

    private void applyBoard(Player p, String legacyTitle, List<String> lines) {
        var mgr = Bukkit.getScoreboardManager();
        if (mgr == null) return;

        var board = cache.computeIfAbsent(p.getUniqueId(), uuid -> mgr.getNewScoreboard());
        String signature = legacyTitle + "\n" + String.join("\n", lines);
        if (signature.equals(rendered.get(p.getUniqueId())) && p.getScoreboard() == board) {
            return;
        }

        Objective obj = board.getObjective("vetro");

        if (obj == null) {
            obj = board.registerNewObjective(
                    "vetro",
                    Criteria.DUMMY,
                    LegacyComponentSerializer.legacySection().deserialize(legacyTitle)
            );
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        } else {
            obj.displayName(LegacyComponentSerializer.legacySection().deserialize(legacyTitle));
            obj.setDisplaySlot(DisplaySlot.SIDEBAR);
        }

        for (String entry : new HashSet<>(board.getEntries())) {
            board.resetScores(entry);
        }

        int score = lines.size();

        for (String line : lines) {
            String unique = makeUnique(line, score);
            obj.getScore(unique).setScore(score);
            score--;
        }

        if (p.getScoreboard() != board) {
            p.setScoreboard(board);
        }
        if (plugin.getNametagTabManager() != null) {
            plugin.getNametagTabManager().attachBoard(p, board);
        }
        rendered.put(p.getUniqueId(), signature);
    }

    private String makeUnique(String line, int score) {
        String base = line == null ? "" : line;

        ChatColor color = ChatColor.values()[Math.abs(score) % ChatColor.values().length];

        String unique = base + color;

        if (unique.length() > 120) {
            unique = unique.substring(0, 119) + color;
        }

        return unique;
    }

    /** Icon+Label auf einer Zeile, farbiger Wert auf der naechsten -- Referenz-Sidebar-Layout. */
    private void addIconPair(List<String> lines, String icon, String label, String coloredValue) {
        lines.add("§7" + icon + " " + label);
        lines.add(safe(coloredValue));
    }

    private void addPair(List<String> lines, String label, String value) {
        lines.add("§8┃ §7" + safe(label));
        lines.add("§c┃ §f" + safe(value));
    }

    private void addBlank(List<String> lines) {
        lines.add("");
    }

    private void addSpacer(List<String> lines) {
        lines.add(SEP);
    }

    private String tr(Player player, String key) {
        return VetroForgeUtil.tr(plugin, player, key);
    }

    /* =======================================================
       Temporary Overlay API
       ======================================================= */

    public void pushTemporary(Player p, String legacyTitle, List<String> lines, int durationTicks) {
        if (p == null || !p.isOnline()) return;

        UUID uuid = p.getUniqueId();

        overlayPrev.putIfAbsent(uuid, p.getScoreboard());

        applyBoard(p, legacyTitle, lines);

        Integer oldTask = overlayTask.remove(uuid);
        if (oldTask != null) {
            try {
                Bukkit.getScheduler().cancelTask(oldTask);
            } catch (Throwable ignored) {}
        }

        int taskId = Bukkit.getScheduler().runTaskLater(plugin, () -> {
            try {
                org.bukkit.scoreboard.Scoreboard previous = overlayPrev.remove(uuid);

                if (previous != null && p.isOnline()) {
                    p.setScoreboard(previous);
                }
            } finally {
                overlayTask.remove(uuid);
            }
        }, Math.max(1L, durationTicks)).getTaskId();

        overlayTask.put(uuid, taskId);
    }

    /* =======================================================
       Data Helper
       ======================================================= */

    private String getFactionName(Player p) {
        try {
            FactionManager fm = plugin.getFactionManager();
            if (fm == null) return "-";

            String factionId = fm.getFactionOf(p.getUniqueId());
            if (factionId == null) return "-";

            return safe(fm.getDisplayName(factionId));
        } catch (Throwable ignored) {
            return "-";
        }
    }

    private boolean isSpawn(Player p) {
        try {
            return plugin.getWarzoneRegionManager() != null
                    && plugin.getWarzoneRegionManager().isLobbySpawnArea(p.getLocation());
        } catch (Throwable ignored) {
            return false;
        }
    }

    private String getMoney(Player p) {
        try {
            double balance = plugin.getEconomyManager() == null
                    ? 0.0
                    : plugin.getEconomyManager().getBalance(p.getUniqueId());

            return String.format(Locale.US, "%,.0f", balance);
        } catch (Throwable ignored) {
            return "0";
        }
    }

    private String getCoins(Player p) {
        try {
            int coins = plugin.getCoinsManager() == null ? 0 : plugin.getCoinsManager().getCoins(p.getUniqueId());
            return coins < 0 ? "§7Lädt..." : "§e" + String.format(Locale.US, "%,d", coins);
        } catch (Throwable ignored) {
            return "§e0";
        }
    }

    private String getElo(Player p) {
        try {
            String faction = plugin.getFactionManager() == null ? null : plugin.getFactionManager().getFactionOf(p.getUniqueId());
            return "§b" + (plugin.getFactionUpgradeManager() == null ? 0 : plugin.getFactionUpgradeManager().getElo(faction));
        } catch (Throwable ignored) {
            return "§b0";
        }
    }

    private String coloredKD(Player p) {
        double kd;

        try {
            kd = plugin.getStatsManager() == null ? 0.0 : plugin.getStatsManager().getKDR(p);
        } catch (Throwable ignored) {
            kd = 0.0;
        }

        String value = String.format(Locale.US, "%.2f", kd);

        if (kd >= 2.0) return "§a" + value;
        if (kd >= 1.0) return "§e" + value;

        return "§c" + value;
    }

    private String getPrestigeTier(Player p) {
        int points = 0;

        try {
            points = plugin.getStatsManager() == null ? 0 : plugin.getStatsManager().getPrestige(p);
        } catch (Throwable ignored) {}

        try {
            return PrestigeUtil.titleFor(points);
        } catch (Throwable ignored) {
            return "§7Prestige 0";
        }
    }

    private int countWarzonePlayers() {
        try {
            WarzoneRegionManager rm = plugin.getWarzoneRegionManager();
            if (rm == null) return 0;

            int count = 0;

            for (Player p : Bukkit.getOnlinePlayers()) {
                if (rm.isWarzone(p.getLocation())) {
                    count++;
                }
            }

            return count;
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private String getRankName(Player p) {
        try {
            return plugin.getVetroPermsManager().getColor(p) + plugin.getVetroPermsManager().getRank(p);
        } catch (Throwable ignored) {
            return ChatColor.GRAY + "Player";
        }
    }

    /* =======================================================
       Format Helper
       ======================================================= */

    private String safe(String s) {
        return s == null || s.isBlank() ? "-" : s;
    }

    private String formatTime(int sec) {
        int s = Math.max(0, sec);
        int m = s / 60;
        int r = s % 60;

        return String.format(Locale.ROOT, "%d:%02d", m, r);
    }

    private String gradient(String text, String startHex, String endHex) {
        int[] start = hexToRgb(startHex);
        int[] end = hexToRgb(endHex);

        StringBuilder out = new StringBuilder();

        int length = Math.max(1, text.length() - 1);

        for (int i = 0; i < text.length(); i++) {
            double ratio = (double) i / length;

            int r = (int) (start[0] + (end[0] - start[0]) * ratio);
            int g = (int) (start[1] + (end[1] - start[1]) * ratio);
            int b = (int) (start[2] + (end[2] - start[2]) * ratio);

            out.append(toLegacyHex(r, g, b)).append(text.charAt(i));
        }

        return out.toString();
    }

    private int[] hexToRgb(String hex) {
        String clean = hex.replace("#", "");

        return new int[] {
                Integer.parseInt(clean.substring(0, 2), 16),
                Integer.parseInt(clean.substring(2, 4), 16),
                Integer.parseInt(clean.substring(4, 6), 16)
        };
    }

    private String toLegacyHex(int r, int g, int b) {
        String hex = String.format("%06X", (r << 16) | (g << 8) | b);

        return "§x"
                + "§" + hex.charAt(0)
                + "§" + hex.charAt(1)
                + "§" + hex.charAt(2)
                + "§" + hex.charAt(3)
                + "§" + hex.charAt(4)
                + "§" + hex.charAt(5);
    }
}
