// src/main/java/com/vetroforge/punish/PunishJoinListener.java
package com.vetroforge.punish;

import com.vetroforge.FactionsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerPreLoginEvent;
import org.bukkit.event.player.PlayerLoginEvent;

import java.lang.reflect.Method;
import java.util.UUID;

public class PunishJoinListener implements Listener {

    private final FactionsPlugin plugin;

    public PunishJoinListener(FactionsPlugin plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /**
     * Blockt schon im PreLogin (sicherster Zeitpunkt, bevor Plugins/Chunks geladen werden).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPreLogin(AsyncPlayerPreLoginEvent e) {
        PunishmentManager pm = plugin.getPunishmentManager();
        if (pm == null) return;

        UUID id = e.getUniqueId();
        if (!pm.isBanned(id)) return;

        // Versuche, die letzte Ban-Info zu ziehen (falls vorhanden)
        PunishmentManager.PunishEntry be = pm.latestBan(id);
        String actor  = safeStaffName(pm, be != null ? be.staff : null);
        String reason = notBlank(be != null ? be.reason : null, "Ban");

        String msg = pm.banBannerText(e.getName(), actor, reason);
        disallowPreLogin(e, msg);
    }

    /**
     * Fängt spätere Fälle ab (z.B. wenn ein Ban exakt beim Join gesetzt wurde).
     */
    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onLogin(PlayerLoginEvent e) {
        PunishmentManager pm = plugin.getPunishmentManager();
        if (pm == null) return;

        Player player = e.getPlayer();
        UUID id = player.getUniqueId();

        PunishmentManager.PunishEntry be = pm.latestBan(id);
        if (be == null) return; // nicht gebannt → reinlassen

        String actor  = safeStaffName(pm, be.staff);
        String reason = notBlank(be.reason, "Ban");
        String msg    = pm.banBannerText(player.getName(), actor, reason);

        disallowLogin(e, msg);
    }

    /* ================= helpers ================= */

    private void disallowPreLogin(AsyncPlayerPreLoginEvent e, String msg) {
        // Paper: disallow(Result, Component) – via Reflection aufrufbar
        try {
            Method m = e.getClass().getMethod("disallow", AsyncPlayerPreLoginEvent.Result.class, Component.class);
            Component comp = LegacyComponentSerializer.legacySection().deserialize(msg);
            m.invoke(e, AsyncPlayerPreLoginEvent.Result.KICK_BANNED, comp);
            return;
        } catch (Throwable ignored) {
            // Spigot: disallow(Result, String)
        }
        e.disallow(AsyncPlayerPreLoginEvent.Result.KICK_BANNED, msg.replace('&', '§'));
    }

    private void disallowLogin(PlayerLoginEvent e, String msg) {
        // Paper: disallow(Result, Component)
        try {
            Component comp = LegacyComponentSerializer.legacySection().deserialize(msg);
            e.disallow(PlayerLoginEvent.Result.KICK_BANNED, comp);
            return;
        } catch (Throwable ignored) {
            // Spigot: disallow(Result, String)
        }
        e.disallow(PlayerLoginEvent.Result.KICK_BANNED, msg.replace('&', '§'));
    }

    private String safeStaffName(PunishmentManager pm, UUID staffId) {
        // Bevorzugt PunishmentManager.staffName(UUID) (falls vorhanden)
        try {
            Method m = pm.getClass().getMethod("staffName", UUID.class);
            Object v = m.invoke(pm, staffId);
            if (v instanceof String s && !s.isBlank()) return s;
        } catch (Throwable ignored) {}

        // Fallback: Online- oder Offline-Name, sonst "Console"
        if (staffId == null) return "Console";
        try {
            Player p = Bukkit.getPlayer(staffId);
            if (p != null) return p.getName();
            OfflinePlayer off = Bukkit.getOfflinePlayer(staffId);
            String name = (off != null ? off.getName() : null);
            return (name != null && !name.isBlank()) ? name : "Console";
        } catch (Throwable ignored) {
            return "Console";
        }
    }

    private String notBlank(String s, String def) {
        return (s == null || s.isBlank()) ? def : s;
    }
}