// ===============================================
// File: src/main/java/com/vetroforge/maintenance/MaintenanceListener.java
// ===============================================
package com.vetroforge.maintenance;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.ChatBoxUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLoginEvent;

public class MaintenanceListener implements Listener {

    private final FactionsPlugin plugin;
    private final MaintenanceManager manager;

    public MaintenanceListener(FactionsPlugin plugin, MaintenanceManager manager) {
        this.plugin = plugin;
        this.manager = manager;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onLogin(PlayerLoginEvent e) {
        if (!manager.isEnabled()) return;
        if (!manager.isAllowed(e.getPlayer())) {
            String rsn = manager.getReason();
            String msg = "Server in Wartung" + (rsn!=null && !rsn.isEmpty() ? " - " + rsn : "");
            e.disallow(PlayerLoginEvent.Result.KICK_OTHER, msg);
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        if (!manager.isEnabled()) return;
        Player p = e.getPlayer();
        if (!manager.isAllowed(p)) return;

        String reason = manager.getReason();
        p.sendMessage(Component.text(" "));
        p.sendMessage(ChatBoxUtil.boxTop());
        p.sendMessage(ChatBoxUtil.center(legacy("§c§lVetroForge §8• §7Wartung")));
        p.sendMessage(ChatBoxUtil.divider());
        p.sendMessage(ChatBoxUtil.center(legacy("§7Der Server läuft aktuell im §cWartungsmodus§7.")));
        p.sendMessage(ChatBoxUtil.center(legacy("§7Du bist mit §aBypass-Zugriff §7beigetreten.")));
        if (reason != null && !reason.isEmpty()) {
            p.sendMessage(ChatBoxUtil.center(legacy("§7Grund: §f" + reason)));
        }
        if (p.hasPermission("vetroforge.maintenance.admin")) {
            p.sendMessage(ChatBoxUtil.center(legacy("§8/§fmaintenance off §7- Wartung beenden")));
        }
        p.sendMessage(ChatBoxUtil.boxBottom());
    }

    private Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }
}