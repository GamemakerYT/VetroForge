package com.vetroforge.commands;

import com.vetroforge.notify.NotifyManager;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class NotifyCommand implements CommandExecutor, TabCompleter {

    private final NotifyManager notify;

    public NotifyCommand(NotifyManager mgr){ this.notify = mgr; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player)) { s.sendMessage("Ingame."); return true; }
        Player p = (Player) s;

        if (a.length == 0) {
            // toggle
            boolean on = !notify.isEnabled(p.getUniqueId());
            notify.setEnabled(p.getUniqueId(), on);
            p.sendMessage(on ? cc("&aNotify AN") : cc("&eNotify AUS"));
            return true;
        }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "on":
            case "enable":
                notify.setEnabled(p.getUniqueId(), true);
                p.sendMessage(cc("&aNotify aktiviert."));
                return true;

            case "off":
            case "disable":
                notify.setEnabled(p.getUniqueId(), false);
                p.sendMessage(cc("&eNotify deaktiviert."));
                return true;

            case "status": {
                var s0 = notify.getSettings(p.getUniqueId());
                String ch = notify.getKnownChannels().stream()
                        .map(k -> (notify.isChannelEnabled(p.getUniqueId(), k) ? ChatColor.GREEN : ChatColor.RED) + k)
                        .collect(Collectors.joining(ChatColor.GRAY + ", "));
                p.sendMessage(cc("&6—— Notify Status ——"));
                p.sendMessage(cc("&7Global: " + (s0.enabled ? "&aAN" : "&eAUS")
                        + " &8| &7Sound: " + (s0.soundEnabled ? "&aAN" : "&eAUS")
                        + " &8| &7ActionBar: " + (s0.actionbar ? "&aAN" : "&eAUS")
                        + " &8| &7AutoJoin: " + (s0.autoOnJoin ? "&aAN" : "&eAUS")));
                long left = s0.snoozeUntil - System.currentTimeMillis();
                if (left > 0) p.sendMessage(cc("&7Snooze: &e" + fmtDuration(left) + " &8(rest)"));
                p.sendMessage(cc("&7Channels: &f" + ch));
                return true;
            }

            case "channel": {
                if (a.length < 2) { p.sendMessage(cc("&eVerwendung: /"+l+" channel <name> [on|off|toggle]")); return true; }
                String ch = a[1].toLowerCase(Locale.ROOT);
                if (a.length >= 3) {
                    String v = a[2].toLowerCase(Locale.ROOT);
                    boolean on = v.equals("on") || v.equals("true") || v.equals("1") || v.equals("enable");
                    if (v.equals("toggle")) on = !notify.isChannelEnabled(p.getUniqueId(), ch);
                    notify.setChannel(p.getUniqueId(), ch, on);
                    p.sendMessage(cc("&7Channel &f"+ch+"&7: " + (on?"&aAN":"&eAUS")));
                } else {
                    // toggle
                    boolean on = !notify.isChannelEnabled(p.getUniqueId(), ch);
                    notify.setChannel(p.getUniqueId(), ch, on);
                    p.sendMessage(cc("&7Channel &f"+ch+"&7: " + (on?"&aAN":"&eAUS")));
                }
                return true;
            }

            case "sound": {
                if (a.length == 1) {
                    boolean on = !notify.getSettings(p.getUniqueId()).soundEnabled;
                    notify.setSoundEnabled(p.getUniqueId(), on);
                    p.sendMessage(cc("&7Sound: " + (on ? "&aAN" : "&eAUS")));
                    return true;
                }
                if ("on".equalsIgnoreCase(a[1]) || "enable".equalsIgnoreCase(a[1])) {
                    notify.setSoundEnabled(p.getUniqueId(), true);
                    p.sendMessage(cc("&7Sound: &aAN"));
                    return true;
                }
                if ("off".equalsIgnoreCase(a[1]) || "disable".equalsIgnoreCase(a[1])) {
                    notify.setSoundEnabled(p.getUniqueId(), false);
                    p.sendMessage(cc("&7Sound: &eAUS"));
                    return true;
                }
                // sound name setzen
                String name = a[1].toUpperCase(Locale.ROOT);
                try {
                    Sound.valueOf(name); // Validierung
                    notify.setSoundName(p.getUniqueId(), name);
                    p.sendMessage(cc("&7Sound gesetzt: &f" + name));
                } catch (IllegalArgumentException ex) {
                    p.sendMessage(cc("&cUnbekannter Sound. Beispiel: &fBLOCK_NOTE_BLOCK_PLING"));
                }
                return true;
            }

            case "actionbar": {
                boolean on = (a.length >= 2)
                        ? ("on".equalsIgnoreCase(a[1]) || "enable".equalsIgnoreCase(a[1]))
                        : !notify.getSettings(p.getUniqueId()).actionbar;
                notify.setActionbar(p.getUniqueId(), on);
                p.sendMessage(cc("&7ActionBar: " + (on ? "&aAN" : "&eAUS")));
                return true;
            }

            case "snooze": {
                if (a.length == 1) {
                    p.sendMessage(cc("&eVerwendung: /"+l+" snooze <Dauer z.B. 10m | off>"));
                    return true;
                }
                if ("off".equalsIgnoreCase(a[1])) {
                    notify.snooze(p.getUniqueId(), 0);
                    p.sendMessage(cc("&7Snooze &eaus."));
                    return true;
                }
                long millis = parseDuration(a[1]);
                if (millis <= 0) { p.sendMessage(cc("&cUngültige Dauer. Beispiele: &f30s &7| &f5m &7| &f1h")); return true; }
                notify.snooze(p.getUniqueId(), millis);
                p.sendMessage(cc("&7Snooze: &e" + fmtDuration(millis)));
                return true;
            }

            case "test": {
                // Schnelltest: sendet eine Test-Nachricht an dich selbst (um Styles zu sehen)
                notify.broadcast("reports", "&fTest-Report &8• &7Hello world!", Sound.BLOCK_NOTE_BLOCK_PLING, null);
                p.sendMessage(cc("&7Test versendet (Channel &freports&7)."));
                return true;
            }
        }

        p.sendMessage(cc("&eVerwendung: /"+l+" [on|off|status|channel|sound|actionbar|snooze|test]"));
        return true;
    }

    /* ===== TabComplete ===== */

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        List<String> out = new ArrayList<>();
        if (a.length == 1) {
            suggest(out, a[0], "on","off","status","channel","sound","actionbar","snooze","test");
        } else if (a.length == 2 && "channel".equalsIgnoreCase(a[0])) {
            if (s instanceof Player) {
                out.addAll(notify.getKnownChannels());
            }
            suggest(out, a[1], out.toArray(new String[0]));
        } else if (a.length == 3 && "channel".equalsIgnoreCase(a[0])) {
            suggest(out, a[2], "on","off","toggle");
        } else if (a.length == 2 && "sound".equalsIgnoreCase(a[0])) {
            suggest(out, a[1], "on","off","BLOCK_NOTE_BLOCK_PLING","ENTITY_EXPERIENCE_ORB_PICKUP","UI_BUTTON_CLICK");
        } else if (a.length == 2 && "snooze".equalsIgnoreCase(a[0])) {
            suggest(out, a[1], "30s","5m","15m","1h","off");
        }
        return out;
    }

    /* ===== Helpers ===== */

    private static void suggest(List<String> out, String cur, String... opts) {
        String low = cur == null ? "" : cur.toLowerCase(Locale.ROOT);
        for (String o : opts) if (o.toLowerCase(Locale.ROOT).startsWith(low)) out.add(o);
    }

    private static String cc(String s){ return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }

    private static long parseDuration(String token) {
        try {
            String t = token.trim().toLowerCase(Locale.ROOT);
            long mul = 1000L;
            if (t.endsWith("ms")) { mul = 1L; t = t.substring(0, t.length()-2); }
            else if (t.endsWith("s")) { mul = 1000L; t = t.substring(0, t.length()-1); }
            else if (t.endsWith("m")) { mul = 60_000L; t = t.substring(0, t.length()-1); }
            else if (t.endsWith("h")) { mul = 3_600_000L; t = t.substring(0, t.length()-1); }
            else if (t.endsWith("d")) { mul = 86_400_000L; t = t.substring(0, t.length()-1); }
            double v = Double.parseDouble(t);
            return (long) Math.max(0, v * mul);
        } catch (Exception e) { return 0L; }
    }

    private static String fmtDuration(long ms) {
        long s = Math.max(0, ms / 1000L);
        if (s < 60) return s + "s";
        long m = s / 60; s %= 60;
        if (m < 60) return m + "m " + s + "s";
        long h = m / 60; m %= 60;
        return h + "h " + m + "m";
    }
}
