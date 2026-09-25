// src/main/java/com/vetroforge/commands/KickCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.punish.PunishmentManager;
import com.vetroforge.util.StaffLabelUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class KickCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    public KickCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!has(s,"vetroforge.staff.kick")) { s.sendMessage(ChatColor.RED+"Keine Rechte."); return true; }
        if (a.length < 1) {
            s.sendMessage(ChatColor.YELLOW+"/"+l+" <Spieler> [GrundIndex|Grund…] [-s]");
            s.sendMessage(ChatColor.GRAY + "Tipp: " + ChatColor.YELLOW + "/reasons kick");
            return true;
        }

        boolean silent = hasFlag(a, "-s");
        Player p = Bukkit.getPlayerExact(a[0]);
        if (p == null) { s.sendMessage(ChatColor.RED+"Spieler offline."); return true; }
        if (plugin.getStaffProtectionService().blockIfProtected(s, p, "Kick")) { s.sendMessage(ChatColor.RED + "Du darfst keine Teammitglieder kicken."); return true; }
        if (s instanceof Player sp && sp.getUniqueId().equals(p.getUniqueId())) {
            s.sendMessage(ChatColor.RED + "Du kannst dich nicht selbst kicken."); return true;
        }

        String lang = (s instanceof Player sp) ? safeLang(plugin, sp) : "de";
        String reason = parseReasonOrIndex(plugin, PunishmentManager.Type.KICK, lang, Arrays.copyOfRange(a,1,a.length), "Kick");
        if (reason == null) {
            s.sendMessage(ChatColor.RED + "Ungueltiger Grund-Index. Nutze " + ChatColor.YELLOW + "/reasons kick" + ChatColor.RED + ".");
            return true;
        }

        String kickMsg = ChatColor.RED + "Du wurdest gekickt!\n"
                + ChatColor.GRAY + "Von: " + StaffLabelUtil.labelColored(s) + "\n"
                + ChatColor.GRAY + "Grund: " + ChatColor.WHITE + reason;
        try { p.kickPlayer(kickMsg); } catch (Throwable ignored) {}

        var lines = Arrays.asList(
                "&7Spieler: &f" + p.getName(),
                "&7Grund: &f" + reason,
                "&7Von: &f" + StaffLabelUtil.labelPlain(s)
        );
        try {
            if (silent) plugin.getPunishmentManager().announce("Kick", lines, true);
            else        plugin.getPunishmentManager().broadcastBanner("Kick", lines);
        } catch (Throwable ignored) {}

        tryLog("logKick", p.getUniqueId(), reason, (s instanceof Player)?((Player)s).getUniqueId():null);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pfx = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pfx)) out.add(p.getName());
        } else {
            String last = args[args.length-1].toLowerCase(Locale.ROOT);
            if ("-s".startsWith(last)) out.add("-s");
            String lang = (sender instanceof Player) ? safeLang(plugin, (Player) sender) : "de";
            var list = plugin.getPunishmentManager().getReasons(PunishmentManager.Type.KICK, lang);
            for (int i=0;i<list.size();i++){
                String idx = String.valueOf(i+1);
                if (idx.startsWith(last)) out.add(idx);
            }
        }
        return out;
    }

    /* helpers (gleich wie in BanCommand) */
    private boolean has(CommandSender s, String node){ try { return s.hasPermission(node); } catch (Throwable t){ return false; } }
    private String safeLang(FactionsPlugin pl, Player p){ try { return pl.getLanguageManager().getPlayerLangKey(p); } catch (Throwable ignored) { return "de"; } }
    private boolean hasFlag(String[] a, String flag){ for (String s : a) if (flag.equalsIgnoreCase(s)) return true; return false; }
    private Integer tryInt(String s){ try { return Integer.parseInt(s); } catch (Exception e){ return null; } }
    private String parseReasonOrIndex(FactionsPlugin pl, PunishmentManager.Type type, String lang, String[] tail, String def){
        List<String> toks = new ArrayList<>();
        for (int i=0;i<tail.length;i++){ if ("-s".equalsIgnoreCase(tail[i])) continue; toks.add(tail[i]); }
        if (toks.isEmpty()) return def;
        Integer idx = tryInt(toks.get(0));
        if (idx != null) {
            String picked = pl.getPunishmentManager().getReasonByIndex(type, lang, idx);
            return picked;
        }
        return String.join(" ", toks);
    }
    private void tryLog(String method, UUID target, String reason, UUID staff){
        try {
            var hist = plugin.getClass().getMethod("getPunishmentHistory").invoke(plugin);
            if (hist != null) {
                hist.getClass().getMethod(method, UUID.class, String.class, UUID.class, Long.TYPE)
                        .invoke(hist, target, reason, staff, System.currentTimeMillis());
            }
        } catch (Throwable ignored) {}
    }
}
