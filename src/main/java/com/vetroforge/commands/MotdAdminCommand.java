// src/main/java/com/vetroforge/commands/MotdAdminCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.motd.MotdManager;
import com.vetroforge.motd.MotdProfile;
import org.bukkit.ChatColor;
import org.bukkit.command.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class MotdAdminCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final MotdManager motd;

    public MotdAdminCommand(FactionsPlugin plugin, MotdManager motd) {
        this.plugin = plugin;
        this.motd = motd;
    }

    private String cc(String s){ return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String lbl, String[] a) {
        if (!s.hasPermission("vetroforge.motd.admin")) {
            s.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (a.length == 0) return help(s, lbl);

        switch (a[0].toLowerCase(Locale.ROOT)) {
            case "list": {
                s.sendMessage(cc("&6MOTD-Profile &8(aktiv: &f"+motd.getActiveProfileId()+"&8, Modus: &f"+(motd.isManualMode()?"MANUAL":"AUTO")+" &8/ &f"+(motd.isProMode()?"PRO":"NORMAL")+"&8)"));
                for (MotdProfile p : motd.getProfiles()) s.sendMessage(" §8- §f" + p.id);
                return true;
            }
            case "set": {
                if (a.length < 2) { s.sendMessage(cc("&cNutze: /"+lbl+" set <profile>")); return true; }
                try { motd.setManualProfile(a[1]); s.sendMessage(cc("&aProfil gesetzt: &f"+a[1]+" &7(Manuell)")); }
                catch (IllegalArgumentException ex) { s.sendMessage(cc("&cUnbekanntes Profil.")); }
                return true;
            }
            case "auto": {
                motd.useAutoMode();
                s.sendMessage(cc("&aAutomatik aktiviert."));
                return true;
            }
            case "pro": {
                if (a.length < 2) { s.sendMessage(cc("&cNutze: /"+lbl+" pro <on|off>")); return true; }
                boolean on = a[1].equalsIgnoreCase("on") || a[1].equalsIgnoreCase("true");
                motd.setProMode(on);
                s.sendMessage(cc("&aPro-Gamemode: &f"+(on?"AN":"AUS")));
                return true;
            }
            case "info": {
                s.sendMessage(cc("&6Aktives Profil: &f"+motd.getActiveProfileId()));
                s.sendMessage(cc("&6Modus: &f"+(motd.isManualMode()?"MANUAL":"AUTO")));
                s.sendMessage(cc("&6Pro: &f"+(motd.isProMode()?"AN":"AUS")));
                return true;
            }
            case "reload": {
                plugin.reloadConfig();
                motd.reload();
                s.sendMessage(cc("&aMOTD neu geladen."));
                return true;
            }
            default:
                return help(s, lbl);
        }
    }

    private boolean help(CommandSender s, String lbl) {
        s.sendMessage(cc("&e/"+lbl+" list &7– Profile auflisten"));
        s.sendMessage(cc("&e/"+lbl+" set <profile> &7– Profil fixieren (manuell)"));
        s.sendMessage(cc("&e/"+lbl+" auto &7– zurück in Auto-Modus"));
        s.sendMessage(cc("&e/"+lbl+" pro <on|off> &7– Pro Gamemode umschalten"));
        s.sendMessage(cc("&e/"+lbl+" info &7– Status"));
        s.sendMessage(cc("&e/"+lbl+" reload &7– Config & Profile neu laden"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] A) {
        List<String> out = new ArrayList<>();
        if (!s.hasPermission("vetroforge.motd.admin")) return out;

        if (A.length == 1) {
            for (String k : new String[]{"list","set","auto","pro","info","reload"})
                if (k.startsWith(A[0].toLowerCase(Locale.ROOT))) out.add(k);
        } else if (A.length == 2 && A[0].equalsIgnoreCase("set")) {
            for (MotdProfile p : motd.getProfiles())
                if (p.id.startsWith(A[1].toLowerCase(Locale.ROOT))) out.add(p.id);
        } else if (A.length == 2 && A[0].equalsIgnoreCase("pro")) {
            for (String k : new String[]{"on","off"})
                if (k.startsWith(A[1].toLowerCase(Locale.ROOT))) out.add(k);
        }
        return out;
    }
}