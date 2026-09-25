// src/main/java/com/vetroforge/warzone/WarzoneEnterLeaveListener.java
package com.vetroforge.warzone;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.display.SidebarScoreboardManager;
import com.vetroforge.util.ActionBarUtil;
import com.vetroforge.util.VetroForgeUtil;
import net.kyori.adventure.text.minimessage.MiniMessage;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.player.PlayerChangedWorldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerRespawnEvent;

import java.time.Duration;
import java.util.*;

public class WarzoneEnterLeaveListener implements Listener {

    private static final long  SWITCH_COOLDOWN_MS = 600L; // Anti-Flip-Flop
    private static final Title.Times TITLE_TIMES =
            Title.Times.times(Duration.ofMillis(200), Duration.ofMillis(1000), Duration.ofMillis(200));
    private static final String ARROW = " §8» §f";

    private final FactionsPlugin plugin;
    private final WarzoneRegionManager rm;
    private final SidebarScoreboardManager boards;

    // optional: hübsche Bossbar beim Betreten/Verlassen
    private final WarzoneBossBarManager bossbars;

    // Wer ist aktuell in der Warzone?
    private final Set<UUID> inside = new HashSet<>();

    // Anti-Spam für Umschalten
    private final Map<UUID, Long> lastSwitch = new HashMap<>();

    private final MiniMessage mm = MiniMessage.miniMessage();

    public WarzoneEnterLeaveListener(FactionsPlugin plugin) {
        this.plugin   = plugin;
        this.rm       = plugin.getWarzoneRegionManager();
        this.boards   = plugin.getSidebarScoreboardManager();
        this.bossbars = plugin.getWarzoneBossBar();

        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /* ===== Öffentliche Helper (z. B. für StoryListener) ===== */
    public boolean isInWarzone(Player p) {
        return p != null && inside.contains(p.getUniqueId());
    }

    /* ================= Events ================= */

    @EventHandler(priority = EventPriority.MONITOR)
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        boolean in = rm.isWarzone(p.getLocation());
        setInside(p, in);

        if (in) {
            boards.showWarzoneBoard(p);
            if (bossbars != null) bossbars.onEnter(p);
        } else {
            boards.showLobbyBoard(p);
            if (bossbars != null) bossbars.onExit(p);
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onRespawn(PlayerRespawnEvent e) {
        // Nach Respawn neu evaluieren (Spawn ist meist außerhalb)
        Bukkit.getScheduler().runTask(plugin, () -> {
            Player p = e.getPlayer();
            boolean in = rm.isWarzone(p.getLocation());
            setInside(p, in);
            if (in) {
                boards.showWarzoneBoard(p);
                if (bossbars != null) bossbars.onEnter(p);
            } else {
                boards.showLobbyBoard(p);
                if (bossbars != null) bossbars.onExit(p);
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onWorldChange(PlayerChangedWorldEvent e) {
        Player p = e.getPlayer();
        boolean in = rm.isWarzone(p.getLocation());
        // erzwinge Umschaltung (kein Cooldown hier, WorldChange ist selten)
        toggleState(p, in, !inside.contains(p.getUniqueId()), true);
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onMove(PlayerMoveEvent e) {
        if (e.getTo() == null || e.getFrom() == null) return;
        // nur reagieren, wenn sich Block-Koordinaten ändern
        if (e.getTo().getBlockX() == e.getFrom().getBlockX()
                && e.getTo().getBlockY() == e.getFrom().getBlockY()
                && e.getTo().getBlockZ() == e.getFrom().getBlockZ()) return;

        Player p = e.getPlayer();
        boolean now = rm.isWarzone(e.getTo());
        boolean was = inside.contains(p.getUniqueId());
        toggleState(p, now, was, false);
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent e) {
        Player p = e.getPlayer();
        inside.remove(p.getUniqueId());
        lastSwitch.remove(p.getUniqueId());
        if (bossbars != null) bossbars.onExit(p);
        // Scoreboard muss man beim Quit i.d.R. nicht zurücksetzen
    }

    /* ================= intern ================= */

    private void toggleState(Player p, boolean now, boolean was, boolean force) {
        if (now == was && !force) return;

        long nowMs = System.currentTimeMillis();
        long last  = lastSwitch.getOrDefault(p.getUniqueId(), 0L);
        if (!force && nowMs - last < SWITCH_COOLDOWN_MS) return;
        lastSwitch.put(p.getUniqueId(), nowMs);

        if (now && !was) {
            // ENTER
            setInside(p, true);
            boards.showWarzoneBoard(p);
            showEnterTitle(p);
            try { p.playSound(p.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.6f, 1.2f); } catch (Throwable ignored) {}
            ActionBarUtil.send(p, VetroForgeUtil.tr(plugin, p, "warzone.enter.actionbar"));
            if (bossbars != null) bossbars.onEnter(p);
        } else if (!now && was) {
            // EXIT
            setInside(p, false);
            boards.showLobbyBoard(p);
            showLeaveTitle(p);
            try { p.playSound(p.getLocation(), Sound.BLOCK_NOTE_BLOCK_BELL, 0.9f, 1.6f); } catch (Throwable ignored) {}
            ActionBarUtil.send(p, VetroForgeUtil.tr(plugin, p, "warzone.leave.actionbar"));
            if (bossbars != null) bossbars.onExit(p);
        }
    }

    private void setInside(Player p, boolean value) {
        if (value) inside.add(p.getUniqueId());
        else inside.remove(p.getUniqueId());
    }

    /* ===== Titles ===== */

    private void showEnterTitle(Player p) {
        p.showTitle(Title.title(
                mm.deserialize("<gradient:#ff2a2a:#ffcc00><bold>WARZONE</bold></gradient>"),
                LegacyComponentSerializer.legacySection().deserialize(VetroForgeUtil.tr(plugin, p, "warzone.enter.subtitle")),
                TITLE_TIMES
        ));
    }

    private void showLeaveTitle(Player p) {
        p.showTitle(Title.title(
                mm.deserialize("<gradient:#00c853:#64dd17><bold>SPAWN</bold></gradient>"),
                LegacyComponentSerializer.legacySection().deserialize(VetroForgeUtil.tr(plugin, p, "warzone.leave.subtitle")),
                TITLE_TIMES
        ));
    }
}
