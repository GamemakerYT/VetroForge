// src/main/java/com/vetroforge/commands/BanCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.punish.PunishmentManager;
import com.vetroforge.util.StaffLabelUtil;
import org.bukkit.BanList;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class BanCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    public BanCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!has(s, "vetroforge.staff.ban")) { s.sendMessage(ChatColor.RED+"Keine Rechte."); return true; }
        if (a.length < 1) {
            s.sendMessage(ChatColor.YELLOW + "/" + l + " <Spieler> [GrundIndex|Grund…] [-t <Min>] [-s]");
            s.sendMessage(ChatColor.GRAY + "Tipp: " + ChatColor.YELLOW + "/reasons ban");
            return true;
        }

        String targetName = a[0];
        boolean silent = hasFlag(a, "-s");
        Integer tempMinutes = readAfterFlagInt(a, "-t"); // null → perm

        OfflinePlayer off = Bukkit.getOfflinePlayer(targetName);
        Player onlineTarget = Bukkit.getPlayerExact(targetName);

        if (plugin.getStaffProtectionService().blockIfProtected(s, off, "Ban")) {
            s.sendMessage(ChatColor.RED + "Du darfst keine Teammitglieder bannen."); return true;
        }
        // Self-ban protection
        if (s instanceof Player sp && sp.getName().equalsIgnoreCase(targetName)) {
            s.sendMessage(ChatColor.RED + "Du kannst dich nicht selbst bannen."); return true;
        }

        String lang = (s instanceof Player sp) ? safeLang(plugin, sp) : "de";
        String reason = parseReasonOrIndex(plugin, PunishmentManager.Type.BAN, lang, Arrays.copyOfRange(a, 1, a.length), "Ban");
        if (reason == null) {
            s.sendMessage(ChatColor.RED + "Ungueltiger Grund-Index. Nutze " + ChatColor.YELLOW + "/reasons ban" + ChatColor.RED + ".");
            return true;
        }

        // Bukkit-Ban
        String source = StaffLabelUtil.labelPlain(s);
        BanList bl = Bukkit.getBanList(BanList.Type.NAME);
        java.util.Date expires = (tempMinutes != null && tempMinutes > 0)
                ? new java.util.Date(System.currentTimeMillis() + tempMinutes * 60_000L)
                : null;
        try { bl.addBan(targetName, reason, expires, source); } catch (Throwable ignored) {}

        // Nur bei PERM zusätzlich internen Flag setzen (Join-Banner via PunishmentManager)
        if (tempMinutes == null) {
            try { plugin.getPunishmentManager().setBanned(off.getUniqueId(), true); } catch (Throwable ignored) {}
        }

        // Sofort kicken falls online
        if (onlineTarget != null) {
            String kick = ChatColor.RED + "Du wurdest gebannt!\n"
                    + ChatColor.GRAY + "Von: " + StaffLabelUtil.labelColored(s) + "\n"
                    + ChatColor.GRAY + "Grund: " + ChatColor.WHITE + reason
                    + (tempMinutes!=null ? ChatColor.GRAY + "\nDauer: " + ChatColor.WHITE + tempMinutes + "m" : "");
            try { onlineTarget.kickPlayer(kick); } catch (Throwable ignored) {}
        }

        // Banner
        var lines = new ArrayList<String>();
        lines.add("&7Spieler: &f" + targetName);
        lines.add("&7Grund: &f" + reason);
        if (tempMinutes != null) lines.add("&7Dauer: &f" + tempMinutes + "m");
        lines.add("&7Von: &f" + StaffLabelUtil.labelPlain(s));
        try {
            if (silent) plugin.getPunishmentManager().announce("Ban", lines, true);
            else        plugin.getPunishmentManager().broadcastBanner("Ban", lines);
        } catch (Throwable ignored) {}

        // History loggen (BAN / kein Dauerfeld in History nötig — optional)
        tryLogBan(off.getUniqueId(), reason, (s instanceof Player)?((Player)s).getUniqueId():null);

        s.sendMessage(ChatColor.GREEN + "Gebannt: " + ChatColor.WHITE + targetName
                + (tempMinutes!=null ? ChatColor.GRAY+" ("+tempMinutes+"m)" : "")
                + ChatColor.GRAY + " – " + ChatColor.WHITE + reason
                + (silent? ChatColor.GRAY+" [silent]" : ""));
        return true;
    }

    /* ---------- Tab-Complete ---------- */
    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pfx = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pfx)) out.add(p.getName());
        } else if (args.length >= 2) {
            String last = args[args.length-1].toLowerCase(Locale.ROOT);
            if ("-s".startsWith(last)) out.add("-s");
            if ("-t".startsWith(last)) out.add("-t");
            // Gründe (Index)
            String lang = (sender instanceof Player) ? safeLang(plugin, (Player) sender) : "de";
            var list = plugin.getPunishmentManager().getReasons(PunishmentManager.Type.BAN, lang);
            for (int i=0;i<list.size();i++) {
                String idx = String.valueOf(i+1);
                if (idx.startsWith(last)) out.add(idx);
            }
        }
        return out;
    }

    /* ---------- helpers ---------- */
    private boolean has(CommandSender s, String node){ try { return s.hasPermission(node); } catch (Throwable t){ return false; } }
    private String safeLang(FactionsPlugin pl, Player p){
        try { return pl.getLanguageManager().getPlayerLangKey(p); } catch (Throwable ignored) { return "de"; }
    }
    private boolean hasFlag(String[] a, String flag){ for (String s : a) if (flag.equalsIgnoreCase(s)) return true; return false; }
    private Integer readAfterFlagInt(String[] a, String flag){
        for (int i=0;i<a.length-1;i++) if (flag.equalsIgnoreCase(a[i])) {
            try { return Integer.parseInt(a[i+1]); } catch (Exception ignored){}
        }
        return null;
    }
    private Integer tryInt(String s){ try { return Integer.parseInt(s); } catch (Exception e){ return null; } }

    private String parseReasonOrIndex(FactionsPlugin pl, PunishmentManager.Type type, String lang, String[] tail, String def){
        // entferne flags/-t <m>
        List<String> toks = new ArrayList<>();
        for (int i=0;i<tail.length;i++){
            String t = tail[i];
            if ("-s".equalsIgnoreCase(t)) continue;
            if ("-t".equalsIgnoreCase(t)) { i++; continue; }
            toks.add(t);
        }
        if (toks.isEmpty()) return def;
        Integer idx = tryInt(toks.get(0));
        if (idx != null) {
            String picked = pl.getPunishmentManager().getReasonByIndex(type, lang, idx);
            return picked;
        }
        return String.join(" ", toks);
    }

    private void tryLogBan(UUID target, String reason, UUID staff){
        try {
            var hist = plugin.getClass().getMethod("getPunishmentHistory").invoke(plugin);
            if (hist != null) {
                hist.getClass().getMethod("logBan", UUID.class, String.class, UUID.class, Long.TYPE)
                        .invoke(hist, target, reason, staff, System.currentTimeMillis());
            }
        } catch (Throwable ignored) {}
    }
}
