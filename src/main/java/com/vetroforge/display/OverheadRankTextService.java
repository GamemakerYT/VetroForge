// src/main/java/com/vetroforge/display/OverheadRankTextService.java
package com.vetroforge.display;

import com.vetroforge.FactionsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.scheduler.BukkitTask;

import java.util.HashMap;
import java.util.Map;
import java.util.Locale;
import java.util.UUID;

public class OverheadRankTextService implements Listener {
    private final FactionsPlugin plugin;
    private final Map<UUID, TextDisplay> displays = new HashMap<>();
    private BukkitTask followTask;

    public OverheadRankTextService(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        if (followTask != null) return;
        followTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {
                TextDisplay td = displays.get(p.getUniqueId());
                if (td == null || td.isDead() || !td.isValid()) continue;
                // Position „klebt“ am Kopf
                td.teleport(p.getLocation().add(0, p.getHeight() + 0.35, 0));
            }
        }, 10L, 10L); // alle 0.5s
    }

    public void stop() {
        if (followTask != null) { followTask.cancel(); followTask = null; }
        // Alles entfernen → nichts bleibt gespeichert
        for (TextDisplay td : displays.values()) try { td.remove(); } catch (Throwable ignore) {}
        displays.clear();
    }

    @EventHandler public void onJoin(PlayerJoinEvent e) { showFor(e.getPlayer()); }
    @EventHandler public void onQuit(PlayerQuitEvent e) { removeFor(e.getPlayer()); }

    public void showFor(Player p) {
        removeFor(p); // neu
        TextDisplay td = p.getWorld().spawn(p.getLocation().add(0, p.getHeight() + 0.35, 0), TextDisplay.class, d -> {
            d.setBillboard(TextDisplay.Billboard.CENTER);
            d.setShadowed(true);
            d.setSeeThrough(true);
            d.setDefaultBackground(false);
            d.setLineWidth(200);
            d.setPersistent(false);     // wir entfernen selbst; soll nicht „ewig“ leben
            d.setInvulnerable(true);
            d.text(rankTag(p));
        });
        displays.put(p.getUniqueId(), td);
    }

    public void removeFor(Player p) {
        TextDisplay td = displays.remove(p.getUniqueId());
        if (td != null) try { td.remove(); } catch (Throwable ignore) {}
    }

    private Component rankTag(Player p) {
        String rank = managedRankFor(p);
        NamedTextColor col = switch (rank) {
            case "ADMIN"   -> NamedTextColor.DARK_RED;
            case "COADMIN" -> NamedTextColor.RED;
            case "MOD"     -> NamedTextColor.GOLD;
            case "BUILDER" -> NamedTextColor.GREEN;
            case "SUPPORT" -> NamedTextColor.AQUA;
            default        -> NamedTextColor.GRAY;
        };
        String label = switch (rank) {
            case "ADMIN" -> "Admin";
            case "COADMIN" -> "Co-Admin";
            case "MOD" -> "Mod";
            case "BUILDER" -> "Builder";
            case "SUPPORT" -> "Support";
            default -> "Spieler";
        };
        return Component.text(label).color(col).decorate(TextDecoration.BOLD);
        // Wenn du „Rang » Name“ als zweite Zeile willst:
        // return Component.text(label).color(col).decorate(TextDecoration.BOLD)
        //       .append(Component.text(" » ").color(NamedTextColor.DARK_GRAY))
        //       .append(Component.text(p.getName(), NamedTextColor.WHITE));
    }

    public static String primaryRankFor(Player p) {
        if (p == null) return "MEMBER";
        if (p.hasPermission("vetroforge.rank.admin"))   return "ADMIN";
        if (p.hasPermission("vetroforge.rank.coadmin")) return "COADMIN";
        if (p.hasPermission("vetroforge.rank.mod"))     return "MOD";
        if (p.hasPermission("vetroforge.rank.builder")) return "BUILDER";
        if (p.hasPermission("vetroforge.rank.support")) return "SUPPORT";
        return "MEMBER";
    }

    private String managedRankFor(Player player) {
        try {
            if (plugin.getVetroPermsManager() != null) {
                String rank = plugin.getVetroPermsManager().getRank(player);
                if (rank != null && !rank.isBlank()) {
                    return switch (rank.trim().toLowerCase(Locale.ROOT)) {
                        case "admin", "owner" -> "ADMIN";
                        case "coadmin", "co-admin" -> "COADMIN";
                        case "mod", "moderator" -> "MOD";
                        case "builder" -> "BUILDER";
                        case "support", "supporter" -> "SUPPORT";
                        default -> "MEMBER";
                    };
                }
            }
        } catch (Throwable ignored) {
        }
        return primaryRankFor(player);
    }
}
