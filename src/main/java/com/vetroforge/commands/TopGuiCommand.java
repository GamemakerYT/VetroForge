// src/main/java/com/vetroforge/commands/TopGuiCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.lang.reflect.Method;
import java.util.*;

public class TopGuiCommand implements CommandExecutor, TabCompleter {

    private static final String ARROW = "§8» §f";
    private final FactionsPlugin plugin;

    // Merkt sich die letzte Seite pro Spieler für "next"/"prev"
    private final Map<UUID, Integer> lastPage = new HashMap<>();

    public TopGuiCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }

        if (plugin.getTopGuiManager() == null) {
            p.sendMessage(tr(p, "top.players.unavailable"));
            return true;
        }

        // subcommands: help | next | prev | <page>
        if (a.length >= 1 && a[0].equalsIgnoreCase("help")) {
            sendHelp(p, l);
            return true;
        }

        int targetPage;
        if (a.length >= 1 && a[0].equalsIgnoreCase("next")) {
            int cur = lastPage.getOrDefault(p.getUniqueId(), 1);
            targetPage = cur + 1;
        } else if (a.length >= 1 && a[0].equalsIgnoreCase("prev")) {
            int cur = lastPage.getOrDefault(p.getUniqueId(), 1);
            targetPage = Math.max(1, cur - 1);
        } else {
            targetPage = (a.length >= 1) ? parsePage(a[0], 1) : 1;
        }

        // optional: auf bekannte Max-Seite clampen (wenn der Manager sowas anbietet)
        Integer max = tryGetMaxPages(plugin.getTopGuiManager());
        if (max != null) targetPage = Math.max(1, Math.min(targetPage, Math.max(1, max)));

        // Öffnen
        plugin.getTopGuiManager().open(p, targetPage);
        lastPage.put(p.getUniqueId(), targetPage);

        // nettes Feedback
        p.sendActionBar(tr(p, "top.players.opened", VetroForgeUtil.vars("page", targetPage)));
        try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f); } catch (Throwable ignored) {}

        return true;
    }

    private void sendHelp(Player p, String label) {
        p.sendMessage(tr(p, "top.players.help.title"));
        p.sendMessage(ARROW + tr(p, "top.players.help.open", VetroForgeUtil.vars("label", label)));
        p.sendMessage(ARROW + tr(p, "top.players.help.page", VetroForgeUtil.vars("label", label)));
        p.sendMessage(ARROW + tr(p, "top.players.help.next", VetroForgeUtil.vars("label", label)));
        p.sendMessage(ARROW + tr(p, "top.players.help.previous", VetroForgeUtil.vars("label", label)));
    }

    private int parsePage(String token, int def) {
        try { return Math.max(1, Integer.parseInt(token)); }
        catch (Exception ignored) { return def; }
    }

    /** Versucht gängige Methoden zur Seitenermittlung: getTotalPages(), getMaxPage(), getPageCount() */
    private Integer tryGetMaxPages(Object mgr) {
        if (mgr == null) return null;
        for (String m : new String[]{"getTotalPages", "getMaxPage", "getPageCount"}) {
            try {
                Method mm = mgr.getClass().getMethod(m);
                Object r = mm.invoke(mgr);
                if (r instanceof Number) return ((Number) r).intValue();
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private String tr(Player player, String key) {
        return VetroForgeUtil.tr(plugin, player, key);
    }

    private String tr(Player player, String key, Map<String, String> vars) {
        return VetroForgeUtil.tr(plugin, player, key, vars);
    }

    /* -------- Tab-Completion -------- */
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] A) {
        if (!(s instanceof Player)) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        if (A.length == 1) {
            for (String opt : new String[]{"help","prev","next","1","2","3","4","5","6","7","8","9","10"}) {
                if (opt.toLowerCase(Locale.ROOT).startsWith(A[0].toLowerCase(Locale.ROOT))) out.add(opt);
            }
            // Falls der Manager eine Max-Seite kennt, hänge die Zahlen bis dahin an
            Integer max = tryGetMaxPages(plugin.getTopGuiManager());
            if (max != null && max > 10) {
                for (int i = 11; i <= Math.min(max, 20); i++) {
                    String sNum = String.valueOf(i);
                    if (sNum.startsWith(A[0])) out.add(sNum);
                }
            }
        }
        return out;
    }
}
