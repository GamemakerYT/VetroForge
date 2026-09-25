package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.prestige.PrestigeManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;

import java.lang.reflect.Method;
import java.util.*;

public class PrestigeDebugCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public PrestigeDebugCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    private String color(String s){ return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String lbl, String[] args) {
        if (!sender.hasPermission("vetroforge.admin")) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        PrestigeManager pm = plugin.getPrestigeManager();
        if (pm == null) { sender.sendMessage(ChatColor.RED + "PrestigeManager fehlt."); return true; }

        if (args.length == 0) return help(sender, lbl);

        String sub = args[0].toLowerCase(Locale.ROOT);
        Object kl = lookupKillListener(); // optional

        switch (sub) {
            case "caps": {
                if (!(sender instanceof org.bukkit.entity.Player)) { sender.sendMessage(color("&cNur ingame.")); return true; }
                UUID u = ((org.bukkit.entity.Player) sender).getUniqueId();

                int today = invokeInt(kl, "getDailyGain", new Class[]{UUID.class}, new Object[]{u}, 0);
                int cap   = invokeInt(kl, "getDailyCap",  null,               null,               0);

                if (kl == null) sender.sendMessage(color("&eHinweis: KillListener nicht gefunden – zeige nur Defaults."));
                sender.sendMessage(color("&6Daily Cap: &f"+cap+" &7| &6Heute: &f"+today));
                return true;
            }
            case "capsreset": {
                if (args.length == 1 && sender instanceof org.bukkit.entity.Player) {
                    UUID u = ((org.bukkit.entity.Player) sender).getUniqueId();
                    invokeVoid(kl, "resetDaily", new Class[]{UUID.class}, new Object[]{u});
                    sender.sendMessage(color("&aDein Daily-Prestige wurde zurückgesetzt."));
                    return true;
                }
                if (args.length >= 2) {
                    if ("*".equals(args[1])) {
                        invokeVoid(kl, "resetAllDaily", null, null);
                        sender.sendMessage(color("&aAlle Daily-Prestige-Zähler zurückgesetzt."));
                        return true;
                    }
                    OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                    if (op == null || op.getUniqueId() == null) { sender.sendMessage(color("&cSpieler nicht gefunden.")); return true; }
                    invokeVoid(kl, "resetDaily", new Class[]{UUID.class}, new Object[]{op.getUniqueId()});
                    sender.sendMessage(color("&aZurückgesetzt: &f"+(op.getName()!=null?op.getName():op.getUniqueId())));
                    return true;
                }
                return usage(sender, lbl, "capsreset [player|*]");
            }
            case "repeat": {
                if (args.length < 3) return usage(sender, lbl, "repeat <killer> <victim>");
                OfflinePlayer ok = Bukkit.getOfflinePlayer(args[1]);
                OfflinePlayer ov = Bukkit.getOfflinePlayer(args[2]);
                if (ok == null || ov == null || ok.getUniqueId() == null || ov.getUniqueId() == null) {
                    sender.sendMessage(color("&cSpieler ungültig.")); return true;
                }
                if (kl == null) { sender.sendMessage(color("&cKillListener nicht aktiv.")); return true; }

                Long ts = invokeLongObj(kl, "getLastKillTs", new Class[]{UUID.class, UUID.class}, new Object[]{ok.getUniqueId(), ov.getUniqueId()}, null);
                long ageSec = (ts == null) ? -1 : (System.currentTimeMillis() - ts) / 1000L;

                int window = invokeInt(kl, "getRepeatWindowSec", null, null, 0);
                double factor = invokeDouble(kl, "getRepeatFactor", null, null, 1.0);

                sender.sendMessage(color("&6LastKill ("+(ok.getName()!=null?ok.getName():ok.getUniqueId())+" → "+(ov.getName()!=null?ov.getName():ov.getUniqueId())+"): &f"+(ageSec<0?"nie":ageSec+"s ago")));
                sender.sendMessage(color("&6Window: &f"+window+"s &7| &6RepeatFactor: &f"+factor));
                return true;
            }
            case "addxp": {
                if (args.length < 3) return usage(sender, lbl, "addxp <player> <amount>");
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                Integer val = tryInt(args[2]);
                if (op == null || op.getUniqueId() == null || val == null) { sender.sendMessage(color("&cUngültige Eingabe.")); return true; }
                pm.addXp(op.getUniqueId(), val);
                sender.sendMessage(color("&aXP hinzugefügt: &f"+val+" &7→ &f"+pm.progressText(op.getUniqueId())+" &7("+pm.getProgressPct(op.getUniqueId())+"%)"));
                return true;
            }
            case "addlvls":
            case "addlevels": {
                if (args.length < 3) return usage(sender, lbl, "addlvls <player> <levels>");
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                Integer lv = tryInt(args[2]);
                if (op == null || op.getUniqueId() == null || lv == null) { sender.sendMessage(color("&cUngültige Eingabe.")); return true; }
                pm.addLevels(op.getUniqueId(), lv);
                sender.sendMessage(color("&aLevel erhöht um &f"+lv+" &7→ &fLevel "+pm.getLevel(op.getUniqueId())));
                return true;
            }
            case "setlvl":
            case "setlevel": {
                if (args.length < 3) return usage(sender, lbl, "setlvl <player> <level>");
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                Integer lv = tryInt(args[2]);
                if (op == null || op.getUniqueId() == null || lv == null) { sender.sendMessage(color("&cUngültige Eingabe.")); return true; }
                pm.setLevel(op.getUniqueId(), lv);
                sender.sendMessage(color("&aLevel gesetzt auf &f"+pm.getLevel(op.getUniqueId())));
                return true;
            }
            case "setxp": {
                if (args.length < 3) return usage(sender, lbl, "setxp <player> <xp>");
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                Integer x = tryInt(args[2]);
                if (op == null || op.getUniqueId() == null || x == null) { sender.sendMessage(color("&cUngültige Eingabe.")); return true; }
                pm.setXp(op.getUniqueId(), x);
                sender.sendMessage(color("&aXP gesetzt: &f"+pm.getXp(op.getUniqueId())+" &7/ &f"+pm.nextNeeded(op.getUniqueId())));
                return true;
            }
            case "show": {
                if (args.length < 2 && sender instanceof org.bukkit.entity.Player) {
                    UUID u = ((org.bukkit.entity.Player) sender).getUniqueId();
                    showStatus(sender, pm, u, sender.getName());
                    return true;
                }
                if (args.length < 2) return usage(sender, lbl, "show <player>");
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                if (op == null || op.getUniqueId() == null) { sender.sendMessage(color("&cSpieler nicht gefunden.")); return true; }
                showStatus(sender, pm, op.getUniqueId(), op.getName()!=null?op.getName():op.getUniqueId().toString());
                return true;
            }
            default:
                return help(sender, lbl);
        }
    }

    private void showStatus(CommandSender s, PrestigeManager pm, UUID id, String name) {
        int lvl = pm.getLevel(id);
        int xp  = pm.getXp(id);
        int need = pm.nextNeeded(id);
        int toNext = pm.getXpToNext(id);
        int pct = pm.getProgressPct(id);
        s.sendMessage(color("&6Prestige » &f"+name));
        s.sendMessage(color("&7Level: &f"+lvl));
        s.sendMessage(color("&7XP: &f"+xp+" &7/ &f"+need+" &8("+pct+"%)"));
        s.sendMessage(color("&7Fehlend bis LevelUp: &f"+toNext));
        s.sendMessage(color("&7Progress: "+pm.progressBar(id)));
    }

    private boolean help(CommandSender s, String lbl) {
        s.sendMessage(color("&e/"+lbl+" show [player]"));
        s.sendMessage(color("&e/"+lbl+" addxp <player> <amount>"));
        s.sendMessage(color("&e/"+lbl+" addlvls <player> <levels>"));
        s.sendMessage(color("&e/"+lbl+" setlvl <player> <level>"));
        s.sendMessage(color("&e/"+lbl+" setxp <player> <xp>"));
        s.sendMessage(color("&e/"+lbl+" caps &7– zeigt deinen Daily-Stand"));
        s.sendMessage(color("&e/"+lbl+" capsreset [player|*]"));
        s.sendMessage(color("&e/"+lbl+" repeat <killer> <victim>"));
        return true;
    }

    private boolean usage(CommandSender s, String lbl, String u) {
        s.sendMessage(color("&cNutze: /"+lbl+" "+u));
        return true;
    }

    /* ================= Reflection Helpers ================= */

    private Object lookupKillListener() {
        try {
            Method m = plugin.getClass().getMethod("getPvpKillListener");
            return m.invoke(plugin);
        } catch (Throwable ignored) {}
        return null;
    }

    private Object invokeAny(Object target, String name, Class<?>[] sig, Object[] args) throws Exception {
        Method m = (sig == null) ? target.getClass().getMethod(name) : target.getClass().getMethod(name, sig);
        return (args == null) ? m.invoke(target) : m.invoke(target, args);
    }

    private int invokeInt(Object target, String name, Class<?>[] sig, Object[] args, int def) {
        if (target == null) return def;
        try {
            Object r = invokeAny(target, name, sig, args);
            return (r instanceof Number) ? ((Number) r).intValue() : def;
        } catch (Throwable ignored) { return def; }
    }

    private double invokeDouble(Object target, String name, Class<?>[] sig, Object[] args, double def) {
        if (target == null) return def;
        try {
            Object r = invokeAny(target, name, sig, args);
            return (r instanceof Number) ? ((Number) r).doubleValue() : def;
        } catch (Throwable ignored) { return def; }
    }

    private Long invokeLongObj(Object target, String name, Class<?>[] sig, Object[] args, Long def) {
        if (target == null) return def;
        try {
            Object r = invokeAny(target, name, sig, args);
            return (r instanceof Number) ? ((Number) r).longValue() : def;
        } catch (Throwable ignored) { return def; }
    }

    private void invokeVoid(Object target, String name, Class<?>[] sig, Object[] args) {
        if (target == null) return;
        try { invokeAny(target, name, sig, args); } catch (Throwable ignored) {}
    }

    private Integer tryInt(String s){ try { return Integer.parseInt(s); } catch (Exception e){ return null; } }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] A) {
        List<String> out = new ArrayList<>();
        if (A.length == 1) {
            for (String k : new String[]{"show","addxp","addlvls","setlvl","setxp","caps","capsreset","repeat"})
                if (k.startsWith(A[0].toLowerCase(Locale.ROOT))) out.add(k);
        } else if (A.length >= 2 && !A[0].equalsIgnoreCase("caps")) {
            for (org.bukkit.entity.Player p : Bukkit.getOnlinePlayers()) {
                String n = p.getName();
                if (n.toLowerCase(Locale.ROOT).startsWith(A[A.length-1].toLowerCase(Locale.ROOT))) out.add(n);
            }
            if (A[0].equalsIgnoreCase("capsreset") && "*".startsWith(A[A.length-1])) out.add("*");
        }
        return out;
    }
}