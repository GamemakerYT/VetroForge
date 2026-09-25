// src/main/java/com/vetroforge/commands/FlyCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.UiDecor;
import com.vetroforge.warzone.WarzoneRegionManager;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;

public class FlyCommand implements CommandExecutor, TabCompleter {

    private static final String PERM_FLY = "vetroforge.lobby.fly";
    private static final String PERM_FACTION_FLY = "vetroforge.fly.faction";
    private static final String PERM_ADMIN = "vetroforge.admin";

    private final FactionsPlugin plugin;

    public FlyCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage(UiDecor.err() + "Nur Spieler.");
            return true;
        }

        boolean isAdmin = p.isOp() || p.hasPermission(PERM_ADMIN);
        boolean hasFly = p.hasPermission(PERM_FLY)
                || p.hasPermission("vetro.fly")
                || p.hasPermission("vetro.lobby.fly")
                || p.hasPermission(PERM_FACTION_FLY)
                || isAdmin;

        if (a.length >= 1 && a[0].equalsIgnoreCase("info")) {
            WarzoneRegionManager region = plugin.getWarzoneRegionManager();
            String flag = region == null ? "UNKNOWN" : region.flagAt(p.getLocation());
            p.sendMessage(UiDecor.pfx() + "§6Fly-Check");
            p.sendMessage("§8• §7Permission: " + (hasFly ? "§aJa" : "§cNein"));
            p.sendMessage("§8• §7In eigener Fraktion flugbereit: " + (plugin.canFlyInOwnFaction(p) ? "§aJa" : "§cNein"));
            p.sendMessage("§8• §7Flag: §f" + flag);
            p.sendMessage("§8• §7AllowFlight: §f" + p.getAllowFlight());
            p.sendMessage("§8• §7Flying: §f" + p.isFlying());
            return true;
        }

        if (!hasFly) {
            p.sendMessage(UiDecor.err() + "Keine Rechte.");
            return true;
        }

        boolean inOwnFaction = plugin.canFlyInOwnFaction(p);
        if (!isInLobbySpawn(p) && !inOwnFaction) {
            WarzoneRegionManager region = plugin.getWarzoneRegionManager();
            String flag = region == null ? "UNKNOWN" : region.flagAt(p.getLocation());
            p.sendMessage(UiDecor.err() + "Fly ist nur im Spawn-Bereich oder in deiner eigenen Fraktion erlaubt. §7Aktuelle Flag: §f" + flag);
            return true;
        }

        boolean enabled;
        if (a.length >= 1) {
            String v = a[0].toLowerCase();
            if (v.equals("on") || v.equals("true")) enabled = true;
            else if (v.equals("off") || v.equals("false")) enabled = false;
            else if (v.equals("toggle")) enabled = !p.isFlying();
            else enabled = !p.isFlying();
        } else {
            enabled = !p.isFlying();
        }

        plugin.getLobbyManager().setFlyMode(p, enabled);
        return true;
    }

    private boolean isInLobbyWorld(Location l) {
        if (l == null || l.getWorld() == null) return false;
        String lobbyWorld = plugin.getConfig().getString("lobby.world", "world");
        return l.getWorld().getName().equalsIgnoreCase(lobbyWorld);
    }

    private boolean isInLobbySpawn(Player player) {
        WarzoneRegionManager region = plugin.getWarzoneRegionManager();
        if (region != null) return region.isLobbySpawnArea(player.getLocation());
        return isInLobbyWorld(player.getLocation());
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            for (String opt : new String[]{"on", "off", "toggle", "info"}) {
                if (opt.startsWith(a[0].toLowerCase())) out.add(opt);
            }
        }
        return out;
    }
}
