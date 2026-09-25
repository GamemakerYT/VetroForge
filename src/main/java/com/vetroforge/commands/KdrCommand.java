// src/main/java/com/vetroforge/commands/KdrCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.text.DecimalFormat;
import java.util.*;
import java.util.stream.Collectors;

public class KdrCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private static final DecimalFormat DF = new DecimalFormat("0.00");

    private static final String ARROW = " §8» §f";
    private static final String BAR   = "§8——————————————";

    public KdrCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) {
            s.sendMessage("Players only.");
            return true;
        }

        // Self
        if (a.length == 0) {
            showKdrCard(p, p, p.getName());
            return true;
        }

        // Target (online, permissiv suchen)
        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) t = searchOnline(a[0]);
        if (t == null) {
            p.sendMessage(BAR);
            p.sendMessage("§c" + tr(p, "kdr.notfound", "Spieler nicht gefunden oder offline."));
            p.sendMessage("§7" + tr(p, "kdr.usage", "Nutze") + ARROW + "§e/" + l + " [Spieler]");
            p.sendMessage(BAR);
            return true;
        }

        showKdrCard(p, t, t.getName());
        return true;
    }

    private void showKdrCard(Player viewer, Player target, String displayName) {
        int kills  = safe(() -> plugin.getStatsManager().getKills(target), 0);
        int deaths = safe(() -> plugin.getStatsManager().getDeaths(target), 0);
        double kdr = safe(() -> plugin.getStatsManager().getKDR(target), 0.0);

        String title = tr(viewer, "kdr.title", "K/D");
        String lk    = tr(viewer, "kdr.kills", "Kills");
        String ld    = tr(viewer, "kdr.deaths", "Tode");
        String lr    = tr(viewer, "kdr.ratio", "K/D");

        String kdrColored = colorKdr(kdr) + DF.format(kdr);

        viewer.sendMessage(BAR);
        viewer.sendMessage("§6" + title + " §8• §e" + displayName);
        viewer.sendMessage(" ");
        viewer.sendMessage("§7" + lk + ARROW + "§f" + kills);
        viewer.sendMessage("§7" + ld + ARROW + "§f" + deaths);
        viewer.sendMessage("§7" + lr + ARROW + kdrColored);
        viewer.sendMessage(BAR);
    }

    private String colorKdr(double r) {
        if (r >= 2.0) return "§a";
        if (r >= 1.0) return "§e";
        return "§c";
    }

    private Player searchOnline(String prefix) {
        String pfx = prefix.toLowerCase(Locale.ROOT);
        List<Player> matches = Bukkit.getOnlinePlayers().stream()
                .filter(pl -> pl.getName().toLowerCase(Locale.ROOT).startsWith(pfx))
                .collect(Collectors.toList());
        return matches.isEmpty() ? null : matches.get(0);
    }

    private <T> T safe(SupplierX<T> s, T def) {
        try { return s.get(); } catch (Throwable ignored) { return def; }
    }

    private String tr(Player p, String key, String fallback) {
        try {
            String v = plugin.getI18n().tr(p, key);
            // Fallback benutzen, wenn Key nicht existiert (I18nManager gibt dann key zurück)
            if (v == null || v.equals(key)) return fallback;
            return ChatColor.translateAlternateColorCodes('&', v);
        } catch (Throwable ignored) {
            return fallback;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] args) {
        if (args.length == 1) {
            String pref = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player pl : Bukkit.getOnlinePlayers()) {
                if (pl.getName().toLowerCase(Locale.ROOT).startsWith(pref)) out.add(pl.getName());
            }
            return out;
        }
        return Collections.emptyList();
    }

    /* ---------- tiny functional helper ---------- */
    private interface SupplierX<T> { T get() throws Exception; }
}