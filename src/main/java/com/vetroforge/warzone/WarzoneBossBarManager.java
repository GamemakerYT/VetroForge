// src/main/java/com/vetroforge/warzone/WarzoneBossBarManager.java
package com.vetroforge.warzone;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitTask;

import java.util.*;

/**
 * Zeigt die Bossbar durchgehend an, solange der Spieler in der Warzone ist (kein
 * 15s-Flash-and-forget mehr) und aktualisiert Titel/Farbe live, wenn sich die
 * Disparity durch Bewegung aendert. Die Eskalationsstufen (low/medium/high/severe/
 * extreme/cataclysmic) teilen sich die gleichen Tier-Grenzen wie die Mob-Varianten
 * in WarzoneMobService, damit Optik und Mob-Staerke konsistent zusammen eskalieren.
 *
 * Konfiguration (optional):
 * warzone:
 *   bossbar:
 *     enabled: true
 *     title: "&4&lWarzone &8» &7PvP aktiv"
 *     color: "RED"                # Fallback-Farbe fuer extreme/cataclysmic
 *     style: "SEGMENTED_10"       # SOLID, SEGMENTED_6, SEGMENTED_10, SEGMENTED_12, SEGMENTED_20
 *     flags: [ "CREATE_FOG" ]     # DARKEN_SKY, PLAY_BOSS_MUSIC, CREATE_FOG
 *   disparity:
 *     tierLow: 16
 *     tierMedium: 41
 *     tierHigh: 71
 *     auraParticles: true
 */
public class WarzoneBossBarManager {

    private final FactionsPlugin plugin;
    private final WarzoneRegionManager warzone;

    private final Map<UUID, BossBar> bars = new HashMap<>();
    private final Map<UUID, String> lastSignature = new HashMap<>();
    private BukkitTask loop;

    public WarzoneBossBarManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.warzone = plugin.getWarzoneRegionManager();
    }

    /* ================= lifecycle ================= */

    public void start() {
        stop();
        loop = Bukkit.getScheduler().runTaskTimer(plugin, this::tickAll, 20L, 5L);
    }

    public void stop() {
        if (loop != null) { try { loop.cancel(); } catch (Throwable ignored) {} loop = null; }
        for (Map.Entry<UUID, BossBar> e : bars.entrySet()) {
            try {
                Player p = Bukkit.getPlayer(e.getKey());
                if (p != null) e.getValue().removePlayer(p);
                e.getValue().removeAll();
                e.getValue().setVisible(false);
            } catch (Throwable ignored) {}
        }
        bars.clear();
        lastSignature.clear();
    }

    /* ================= public hooks (optional) ================= */

    public void onEnter(Player p) { /* Polling erledigt's */ }
    public void onExit(Player p) { hide(p); }

    /* ================= intern ================= */

    private void tickAll() {
        if (!isEnabled() || warzone == null) {
            for (Player p : Bukkit.getOnlinePlayers()) hide(p);
            return;
        }

        for (Player p : Bukkit.getOnlinePlayers()) {
            boolean inside = warzone.isWarzone(p.getLocation());
            if (!inside) {
                hide(p);
                continue;
            }

            int level = disparityLevel(p);
            String tier = tierKey(level);

            BossBar bar = bars.get(p.getUniqueId());
            if (bar == null) {
                bar = createBar(p);
                bars.put(p.getUniqueId(), bar);
                bar.addPlayer(p);
            }
            applyConfig(bar, p, level, tier);
            bar.setProgress(1.0);
            bar.setVisible(true);

            spawnAura(p, tier);
        }
    }

    /** Blendet Bar für diesen Spieler sofort aus und räumt auf. */
    private void hide(Player p) {
        if (p == null) return;
        UUID id = p.getUniqueId();
        lastSignature.remove(id);

        BossBar bar = bars.remove(id);
        if (bar != null) {
            try { bar.removePlayer(p); } catch (Throwable ignored) {}
            try { bar.removeAll(); } catch (Throwable ignored) {}
            try { bar.setVisible(false); } catch (Throwable ignored) {}
        }
    }

    private BossBar createBar(Player player) {
        int level = disparityLevel(player);
        String tier = tierKey(level);
        String title = rawTitle(player, level, tier);
        BossBar bar = Bukkit.createBossBar(colorize(title), readColor(tier), readStyle(), new BarFlag[0]);
        for (BarFlag f : readFlags(tier)) bar.addFlag(f);
        bar.setProgress(1.0);
        return bar;
    }

    /** Konfiguration bei jedem Tick anwenden (nur bei tatsächlicher Änderung, z.B. Tier-Wechsel). */
    private void applyConfig(BossBar bar, Player player, int level, String tier) {
        if (bar == null) return;
        String title = colorize(rawTitle(player, level, tier));
        BarColor color = readColor(tier);
        BarStyle style = readStyle();
        List<BarFlag> flags = readFlags(tier);

        String signature = title + '|' + color + '|' + style + '|' + flags;
        UUID id = player.getUniqueId();
        if (signature.equals(lastSignature.get(id))) return;
        lastSignature.put(id, signature);

        bar.setTitle(title);
        bar.setColor(color);
        bar.setStyle(style);
        for (BarFlag f : BarFlag.values()) bar.removeFlag(f);
        for (BarFlag f : flags) bar.addFlag(f);
    }

    /** Leichte Partikel-Aura um den Spieler, die mit der Disparity-Stufe intensiver wird. */
    private void spawnAura(Player p, String tier) {
        if (!plugin.getConfig().getBoolean("warzone.disparity.auraParticles", true)) return;
        Particle particle = switch (tier) {
            case "high" -> Particle.CRIT;
            case "severe" -> Particle.WITCH;
            case "extreme" -> Particle.SOUL_FIRE_FLAME;
            case "cataclysmic" -> Particle.DRAGON_BREATH;
            default -> null; // low/medium: keine Aura
        };
        if (particle == null) return;
        int count = switch (tier) {
            case "high" -> 2;
            case "severe" -> 4;
            case "extreme" -> 6;
            default -> 10; // cataclysmic
        };
        World world = p.getWorld();
        Location loc = p.getLocation().add(0.0, 1.0, 0.0);
        try { world.spawnParticle(particle, loc, count, 0.4, 0.6, 0.4, 0.01); } catch (Throwable ignored) {}
    }

    /* ================= tier / config helpers ================= */

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("warzone.bossbar.enabled", true);
    }

    private String rawTitle(Player player, int level, String tier) {
        String raw = plugin.getConfig().getString("warzone.bossbar.title",
                "&6&lWarzone &8» &7Disparity: {disparity}");
        return raw.replace("{disparity}", disparityLabel(player, level, tier));
    }

    private String disparityLabel(Player player, int level, String tier) {
        return "§6" + level + " §8• " + VetroForgeUtil.tr(plugin, player, "warzone.disparity." + tier);
    }

    private int disparityLevel(Player player) {
        if (player == null || warzone == null) return 1;
        try { return warzone.disparityLevel(player.getLocation()); }
        catch (Throwable ignored) { return 1; }
    }

    /** Gemeinsame Eskalationsstufen -- teilen sich die Tier-Grenzen mit WarzoneMobService (Mob-Varianten). */
    private String tierKey(int level) {
        if (level <= 1) return "low";
        if (level <= 3) return "medium";
        if (level < tierLow()) return "high";
        if (level < tierMedium()) return "severe";
        if (level < tierHigh()) return "extreme";
        return "cataclysmic";
    }

    private int tierLow() { return Math.max(4, plugin.getConfig().getInt("warzone.disparity.tierLow", 16)); }
    private int tierMedium() { return Math.max(tierLow() + 1, plugin.getConfig().getInt("warzone.disparity.tierMedium", 41)); }
    private int tierHigh() { return Math.max(tierMedium() + 1, plugin.getConfig().getInt("warzone.disparity.tierHigh", 71)); }

    private BarColor readColor(String tier) {
        return switch (tier) {
            case "low" -> BarColor.GREEN;
            case "medium" -> BarColor.YELLOW;
            case "high" -> BarColor.PINK;
            case "severe" -> BarColor.PURPLE;
            default -> fallbackColor(); // extreme + cataclysmic
        };
    }

    private BarColor fallbackColor() {
        String name = plugin.getConfig().getString("warzone.bossbar.color", "RED").toUpperCase(Locale.ROOT);
        try { return BarColor.valueOf(name); } catch (Throwable ignored) { return BarColor.RED; }
    }

    private BarStyle readStyle() {
        String name = plugin.getConfig().getString("warzone.bossbar.style", "SEGMENTED_10").toUpperCase(Locale.ROOT);
        try { return BarStyle.valueOf(name); } catch (Throwable ignored) { return BarStyle.SOLID; }
    }

    private List<BarFlag> readFlags(String tier) {
        List<String> raw = plugin.getConfig().getStringList("warzone.bossbar.flags");
        List<BarFlag> out = new ArrayList<>();
        if (raw != null) {
            for (String s : raw) {
                try { out.add(BarFlag.valueOf(s.trim().toUpperCase(Locale.ROOT))); }
                catch (Throwable ignored) {}
            }
        }
        if ("cataclysmic".equals(tier) && !out.contains(BarFlag.DARKEN_SKY)) out.add(BarFlag.DARKEN_SKY);
        return out;
    }

    private String colorize(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }
}
