// src/main/java/com/vetroforge/commands/MuteCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.punish.PunishmentManager;
import com.vetroforge.util.StaffLabelUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class MuteCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;
    public MuteCommand(FactionsPlugin plugin){ this.plugin=plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!has(s,"vetroforge.staff.mute")) { s.sendMessage(ChatColor.RED+"Keine Rechte."); return true; }
        if (a.length < 1) {
            s.sendMessage(ChatColor.YELLOW+"/"+l+" <Spieler> [Minuten] [GrundIndex|Grund…] [-s]");
            s.sendMessage(ChatColor.GRAY + "Tipp: " + ChatColor.YELLOW + "/reasons mute");
            return true;
        }

        boolean silent = hasFlag(a, "-s");
        OfflinePlayer off = Bukkit.getOfflinePlayer(a[0]);
        if (off == null || off.getUniqueId()==null) { s.sendMessage(ChatColor.RED+"Unbekannter Spieler."); return true; }

        if (plugin.getStaffProtectionService().blockIfProtected(s, off, "Mute")) { s.sendMessage(ChatColor.RED + "Du darfst keine Teammitglieder muten."); return true; }
        if (s instanceof Player sp && sp.getUniqueId().equals(off.getUniqueId())) {
            s.sendMessage(ChatColor.RED + "Du kannst dich nicht selbst muten."); return true;
        }

        int minutes = 30;
        String lang = (s instanceof Player sp) ? safeLang(plugin, sp) : "de";
        String reason = "Mute";

        List<String> tail = new ArrayList<>(Arrays.asList(a).subList(1, a.length));
        tail.remove("-s");
        if (!tail.isEmpty()) {
            Integer maybeMin = tryInt(tail.get(0));
            if (maybeMin != null) {
                minutes = Math.max(1, maybeMin);
                if (tail.size() >= 2) {
                    Integer idx = tryInt(tail.get(1));
                    if (idx != null) {
                        String picked = plugin.getPunishmentManager().getReasonByIndex(PunishmentManager.Type.MUTE, lang, idx);
                        if (picked == null) { s.sendMessage(ChatColor.RED+"Ungültiger Grund-Index. /reasons mute"); return true; }
                        reason = picked;
                    } else {
                        reason = String.join(" ", tail.subList(1, tail.size()));
                    }
                }
            } else {
                Integer idx = tryInt(tail.get(0));
                if (idx != null) {
                    String picked = plugin.getPunishmentManager().getReasonByIndex(PunishmentManager.Type.MUTE, lang, idx);
                    if (picked == null) { s.sendMessage(ChatColor.RED+"Ungültiger Grund-Index. /reasons mute"); return true; }
                    reason = picked;
                } else {
                    reason = String.join(" ", tail);
                }
            }
        }

        long durMs = minutes * 60_000L;
        plugin.getPunishmentManager().mute(off.getUniqueId(), reason,
                (s instanceof Player) ? ((Player) s).getUniqueId() : null,
                durMs);

        var lines = Arrays.asList(
                "&7Von: &f" + StaffLabelUtil.labelPlain(s) + " &8» &7Spieler: &f" + (off.getName()!=null?off.getName():off.getUniqueId()),
                "&7Dauer: &f" + minutes + "m",
                "&7Grund: &f" + reason
        );
        try {
            if (silent) plugin.getPunishmentManager().announce("Mute", lines, true);
            else        plugin.getPunishmentManager().broadcastBanner("Mute", lines);
        } catch (Throwable ignored) {}

        tryLogMute(off.getUniqueId(), reason, (s instanceof Player)?((Player)s).getUniqueId():null, minutes);
        s.sendMessage(ChatColor.GREEN+"Gemutet: "+ChatColor.WHITE+(off.getName()!=null?off.getName():off.getUniqueId())
                + ChatColor.GRAY + " ("+minutes+"m, "+reason+")" + (silent? ChatColor.GRAY+" [silent]":""));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            String pfx = args[0].toLowerCase(Locale.ROOT);
            for (Player p : Bukkit.getOnlinePlayers())
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(pfx)) out.add(p.getName());
        } else if (args.length == 2) {
            for (String m : new String[]{"5","10","15","30","60","120","240"})
                if (m.startsWith(args[1])) out.add(m);
        } else {
            String last = args[args.length-1].toLowerCase(Locale.ROOT);
            if ("-s".startsWith(last)) out.add("-s");
            String lang = (sender instanceof Player) ? safeLang(plugin, (Player) sender) : "de";
            var list = plugin.getPunishmentManager().getReasons(PunishmentManager.Type.MUTE, lang);
            for (int i=0;i<list.size();i++) {
                String idx = String.valueOf(i+1);
                if (idx.startsWith(last)) out.add(idx);
            }
        }
        return out;
    }

    /* helpers */
    private boolean has(CommandSender s, String node){ try { return s.hasPermission(node); } catch (Throwable t){ return false; } }
    private String safeLang(FactionsPlugin pl, Player p){ try { return pl.getLanguageManager().getPlayerLangKey(p); } catch (Throwable ignored) { return "de"; } }
    private boolean hasFlag(String[] a, String flag){ for (String s : a) if (flag.equalsIgnoreCase(s)) return true; return false; }
    private Integer tryInt(String s){ try { return Integer.parseInt(s); } catch (Exception e){ return null; } }

    private void tryLogMute(UUID target, String reason, UUID staff, int minutes){
        try {
            var hist = plugin.getClass().getMethod("getPunishmentHistory").invoke(plugin);
            if (hist != null) {
                try {
                    hist.getClass().getMethod("logMute", UUID.class, String.class, UUID.class, Long.TYPE, Long.TYPE)
                            .invoke(hist, target, reason, staff, System.currentTimeMillis(), minutes * 60_000L);
                    return;
                } catch (NoSuchMethodException ignore) {}
                hist.getClass().getMethod("logMute", UUID.class, String.class, UUID.class, Long.TYPE)
                        .invoke(hist, target, reason, staff, System.currentTimeMillis());
            }
        } catch (Throwable ignored) {}
    }
}
