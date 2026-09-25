// src/main/java/com/vetroforge/events/commands/EventAdminCommand.java
package com.vetroforge.events.commands;

import com.vetroforge.events.EventService;
import com.vetroforge.events.EventManager;
import com.vetroforge.events.gui.EventAdminGui;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class EventAdminCommand implements CommandExecutor, TabCompleter {

    private static final String PERM = "vetroforge.event.admin";
    private final EventService service;
    private final EventAdminGui gui;

    public EventAdminCommand(EventService service) { this(service, null); }
    public EventAdminCommand(EventService service, EventAdminGui gui) {
        this.service = service;
        this.gui = gui;
    }

    private String ok(){ return ChatColor.GREEN + "✔ " + ChatColor.GRAY; }
    private String er(){ return ChatColor.RED   + "✖ " + ChatColor.GRAY; }
    private String pfx(){ return ChatColor.DARK_GRAY + "• " + ChatColor.GRAY; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String lbl, String[] a) {
        if (!s.hasPermission(PERM)) { s.sendMessage(er()+"Keine Rechte."); return true; }
        if (a.length == 0 || a[0].equalsIgnoreCase("gui")) {
            if (gui != null && s instanceof Player p) {
                gui.open(p);
                return true;
            }
            return help(s, lbl);
        }

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "list":
            case "ls": {
                s.sendMessage(pfx() + "Templates:");
                service.getTemplates().forEach(t -> s.sendMessage(ChatColor.DARK_GRAY + "  - " + ChatColor.WHITE + t.id
                        + ChatColor.GRAY + " » " + t.name + ChatColor.DARK_GRAY + " (Goal " + ChatColor.GRAY + t.goal + ChatColor.DARK_GRAY + ")"));
                return true;
            }
            case "start": {
                if (a.length < 2) { s.sendMessage(er()+"Nutze: /"+lbl+" start <templateId>"); return true; }
                try { service.start(a[1]); s.sendMessage(ok()+"Event gestartet: "+ChatColor.WHITE+a[1]); }
                catch (IllegalArgumentException ex){ s.sendMessage(er()+"Unbekanntes Template."); }
                return true;
            }
            case "stop": {
                service.stop(false); s.sendMessage(ok()+"Event gestoppt."); return true;
            }
            case "pause": service.pause(); s.sendMessage(ok()+"Event pausiert."); return true;
            case "resume": service.resume(); s.sendMessage(ok()+"Event fortgesetzt."); return true;
            case "set": {
                if (a.length < 2) { s.sendMessage(er()+"Nutze: /"+lbl+" set <zahl>"); return true; }
                try { service.setProgress(Long.parseLong(a[1])); s.sendMessage(ok()+"Progress gesetzt."); }
                catch (NumberFormatException ex){ s.sendMessage(er()+"Ungültige Zahl."); }
                return true;
            }
            case "add": {
                if (a.length < 2) { s.sendMessage(er()+"Nutze: /"+lbl+" add <zahl>"); return true; }
                try { service.addProgress(Long.parseLong(a[1])); s.sendMessage(ok()+"Progress erhöht."); }
                catch (NumberFormatException ex){ s.sendMessage(er()+"Ungültige Zahl."); }
                return true;
            }
            case "reload":
            case "rl": {
                service.reloadTemplates(); s.sendMessage(ok()+"Event-Templates neu geladen."); return true;
            }
            case "progress":
            case "debug": {
                if (gui != null && s instanceof Player p) {
                    gui.openProgress(p);
                    return true;
                }
                if (service instanceof EventManager manager) {
                    var snap = manager.debugProgressSnapshot();
                    if (snap == null) { s.sendMessage(pfx()+"Kein aktives Event."); return true; }
                    s.sendMessage(pfx()+"Event: " + ChatColor.WHITE + snap.name() + ChatColor.GRAY + " (" + snap.id() + ")");
                    s.sendMessage(pfx()+"Current: " + ChatColor.WHITE + snap.current() + ChatColor.GRAY + " / " + ChatColor.WHITE + snap.goal() + ChatColor.GRAY + " (" + snap.pct() + "%)");
                    s.sendMessage(pfx()+"Source: " + ChatColor.WHITE + (snap.sourceKey() == null ? "-" : snap.sourceKey())
                            + ChatColor.GRAY + " raw=" + ChatColor.WHITE + (snap.sourceRaw() < 0L ? "-" : snap.sourceRaw())
                            + ChatColor.GRAY + " base=" + ChatColor.WHITE + snap.sourceBase()
                            + ChatColor.GRAY + " delta=" + ChatColor.WHITE + snap.sourceDelta());
                    return true;
                }
                s.sendMessage(er()+"Progress-Debug ist für diesen EventService nicht verfügbar.");
                return true;
            }
            case "info": {
                var ev = service.getActiveEvent();
                if (ev.isEmpty()) { s.sendMessage(pfx()+"Kein aktives Event."); return true; }
                var e = ev.get();
                s.sendMessage(pfx()+"Aktiv: " + ChatColor.WHITE + e.name + ChatColor.GRAY + " (" + e.id + ")");
                s.sendMessage(pfx()+"Progress: " + ChatColor.WHITE + e.current + ChatColor.GRAY + " / " + ChatColor.WHITE + e.goal + ChatColor.GRAY + " (" + e.pct() + "%)");
                if (e.endsAt != null) s.sendMessage(pfx()+"Ende: " + ChatColor.WHITE + e.endsAt);
                s.sendMessage(pfx()+"Status: " + (e.paused ? ChatColor.YELLOW + "PAUSED" : ChatColor.GREEN + "RUNNING"));
                return true;
            }
            default: return help(s, lbl);
        }
    }

    private boolean help(CommandSender s, String lbl) {
        s.sendMessage(pfx()+ChatColor.YELLOW+"/"+lbl+" "+ChatColor.DARK_GRAY+"–"+ChatColor.GRAY+" Admin-GUI öffnen");
        s.sendMessage(pfx()+ChatColor.YELLOW+"/"+lbl+" list "+ChatColor.DARK_GRAY+"–"+ChatColor.GRAY+" Templates auflisten");
        s.sendMessage(pfx()+ChatColor.YELLOW+"/"+lbl+" start <id> "+ChatColor.DARK_GRAY+"–"+ChatColor.GRAY+" Event starten");
        s.sendMessage(pfx()+ChatColor.YELLOW+"/"+lbl+" stop "+ChatColor.DARK_GRAY+"–"+ChatColor.GRAY+" Event stoppen");
        s.sendMessage(pfx()+ChatColor.YELLOW+"/"+lbl+" pause|resume "+ChatColor.DARK_GRAY+"–"+ChatColor.GRAY+" pausieren/fortsetzen");
        s.sendMessage(pfx()+ChatColor.YELLOW+"/"+lbl+" set <n> / add <n> "+ChatColor.DARK_GRAY+"–"+ChatColor.GRAY+" Fortschritt setzen/erhöhen");
        s.sendMessage(pfx()+ChatColor.YELLOW+"/"+lbl+" info "+ChatColor.DARK_GRAY+"–"+ChatColor.GRAY+" Aktuelles Event");
        s.sendMessage(pfx()+ChatColor.YELLOW+"/"+lbl+" progress "+ChatColor.DARK_GRAY+"–"+ChatColor.GRAY+" Source/Base/Current Diagnose");
        s.sendMessage(pfx()+ChatColor.YELLOW+"/"+lbl+" reload "+ChatColor.DARK_GRAY+"–"+ChatColor.GRAY+" Templates neu laden");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] A) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission(PERM)) return out;

        if (A.length == 1) {
            String p = A[0].toLowerCase(Locale.ROOT);
            for (String k : new String[]{"gui","list","ls","start","stop","pause","resume","set","add","info","progress","debug","reload","rl"})
                if (k.startsWith(p)) out.add(k);
        } else if (A.length == 2 && A[0].equalsIgnoreCase("start")) {
            String p = A[1].toLowerCase(Locale.ROOT);
            service.getTemplates().forEach(t -> { if (t.id.toLowerCase(Locale.ROOT).startsWith(p)) out.add(t.id); });
        }
        return out;
    }
}
