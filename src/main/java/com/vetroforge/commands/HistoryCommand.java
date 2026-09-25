// src/main/java/com/vetroforge/commands/HistoryCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import java.util.*;

public class HistoryCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    public HistoryCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vetroforge.staff.history")) {
            s.sendMessage(ChatColor.RED + "Keine Rechte."); return true;
        }
        if (a.length < 1) {
            s.sendMessage(ChatColor.YELLOW + "/" + l + " <Spieler> [Seite]");
            return true;
        }

        OfflinePlayer off = Bukkit.getOfflinePlayer(a[0]);
        int page = (a.length >= 2) ? safeInt(a[1], 1) : 1;

        Object hist = getHistoryManager();
        if (hist == null) {
            s.sendMessage(ChatColor.RED + "Kein History-Manager geladen.");
            return true;
        }

        // Bevorzugt: sendHistory(CommandSender, UUID, int)
        try {
            hist.getClass()
                    .getMethod("sendHistory", CommandSender.class, UUID.class, int.class)
                    .invoke(hist, s, off.getUniqueId(), page);
            return true;
        } catch (NoSuchMethodException ignore) {
            // Fallback unten
        } catch (Throwable t) {
            s.sendMessage(ChatColor.RED + "Fehler beim Laden der History."); return true;
        }

        // Fallback: getHistoryLines(UUID, int page, int pageSize) -> List<String>
        try {
            @SuppressWarnings("unchecked")
            List<String> lines = (List<String>) hist.getClass()
                    .getMethod("getHistoryLines", UUID.class, int.class, int.class)
                    .invoke(hist, off.getUniqueId(), page, 10);

            s.sendMessage(ChatColor.DARK_RED + "—— " + ChatColor.RED + "History " +
                    ChatColor.GRAY + "(" + (off.getName() != null ? off.getName() : off.getUniqueId()) + ")" +
                    ChatColor.DARK_RED + " ——");

            if (lines == null || lines.isEmpty()) {
                s.sendMessage(ChatColor.GRAY + "(keine Einträge)");
            } else {
                for (String line : lines) {
                    s.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
                }
            }
            return true;
        } catch (Throwable t) {
            s.sendMessage(ChatColor.RED + "History kann nicht angezeigt werden.");
            return true;
        }
    }

    private Object getHistoryManager() {
        try {
            // gängige Getter-Namen probieren
            for (String m : new String[]{
                    "getPunishmentHistory", "getPunishmentHistoryManager",
                    "punishmentHistory", "getHistoryManager"}) {
                try {
                    Object o = plugin.getClass().getMethod(m).invoke(plugin);
                    if (o != null) return o;
                } catch (Throwable ignored) {}
            }
            // Felder durchsuchen
            for (var f : plugin.getClass().getDeclaredFields()) {
                if (f.getName().toLowerCase(Locale.ROOT).contains("history")) {
                    f.setAccessible(true);
                    Object v = f.get(plugin);
                    if (v != null) return v;
                }
            }
        } catch (Throwable ignored) {}
        return null;
    }

    private int safeInt(String s, int def) { try { return Integer.parseInt(s.trim()); } catch (Exception e) { return def; } }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        if (args.length == 1) {
            String pfx = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pfx)) out.add(p.getName());
            }
            return out;
        } else if (args.length == 2) {
            return Arrays.asList("1","2","3","4","5");
        }
        return Collections.emptyList();
    }
}