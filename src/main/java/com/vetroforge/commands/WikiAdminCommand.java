// src/main/java/com/vetroforge/commands/WikiAdminCommand.java
package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.*;

import java.util.*;

public class WikiAdminCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    public WikiAdminCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    private boolean admin(CommandSender s){ return s.hasPermission("vetroforge.admin") || s.isOp(); }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!admin(s)) { s.sendMessage(UiDecor.noPermission()); return true; }
        if (a.length == 0) {
            s.sendMessage("§8/§e"+l+" add <category> <itemId>");
            s.sendMessage("§8/§e"+l+" remove <category> <itemId>");
            s.sendMessage("§8/§e"+l+" setdesc <itemId> <de|en|fr|nl> <Text...>");
            s.sendMessage("§8/§e"+l+" reload");
            return true;
        }

        if (a[0].equalsIgnoreCase("add") && a.length >= 3) {
            String cat = a[1]; String id = a[2];
            java.util.List<String> list = plugin.getConfig().getStringList("wiki.categories."+cat+".items");
            if (!list.contains(id)) list.add(id);
            plugin.getConfig().set("wiki.categories."+cat+".items", list);
            plugin.saveConfig();
            s.sendMessage("§aHinzugefügt: §f"+id+" §7→ §f"+cat);
            return true;
        }

        if (a[0].equalsIgnoreCase("remove") && a.length >= 3) {
            String cat = a[1]; String id = a[2];
            java.util.List<String> list = plugin.getConfig().getStringList("wiki.categories."+cat+".items");
            list.removeIf(x -> x.equalsIgnoreCase(id));
            plugin.getConfig().set("wiki.categories."+cat+".items", list);
            plugin.saveConfig();
            s.sendMessage("§aEntfernt: §f"+id+" §7aus §f"+cat);
            return true;
        }

        if (a[0].equalsIgnoreCase("setdesc") && a.length >= 4) {
            String id = a[1]; String lang = a[2].toLowerCase(Locale.ROOT);
            java.util.Set<String> ok = new java.util.HashSet<>(java.util.Arrays.asList("de","en","fr","nl"));
            if (!ok.contains(lang)) { s.sendMessage("§cSprache: de/en/fr/nl"); return true; }
            String text = String.join(" ", java.util.Arrays.copyOfRange(a, 3, a.length));
            plugin.getConfig().set("wiki.items."+id+".desc."+lang, text);
            plugin.saveConfig();
            s.sendMessage("§aBeschreibung gesetzt: §f"+id+" §7["+lang+"]");
            return true;
        }

        if (a[0].equalsIgnoreCase("reload")) {
            plugin.reloadConfig();
            s.sendMessage("§aConfig neu geladen.");
            return true;
        }

        s.sendMessage("§cUnbekannter Subcommand.");
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender s, Command c, String a, String[] A) {
        java.util.List<String> out = new java.util.ArrayList<>();
        if (!admin(s)) return out;

        if (A.length == 1) {
            for (String k : new String[]{"add","remove","setdesc","reload"}) if (k.startsWith(A[0].toLowerCase())) out.add(k);
            return out;
        }
        if (A.length == 2 && (A[0].equalsIgnoreCase("add") || A[0].equalsIgnoreCase("remove"))) {
            if (plugin.getConfig().isConfigurationSection("wiki.categories")) {
                for (String cat : plugin.getConfig().getConfigurationSection("wiki.categories").getKeys(false)) {
                    if (cat.toLowerCase(Locale.ROOT).startsWith(A[1].toLowerCase(Locale.ROOT))) out.add(cat);
                }
            }
            return out;
        }
        if (A.length == 3 && (A[0].equalsIgnoreCase("add") || A[0].equalsIgnoreCase("remove"))) {
            // Item IDs aus shop.general.items.*
            if (plugin.getConfig().isConfigurationSection("shop.general.items")) {
                for (String id : plugin.getConfig().getConfigurationSection("shop.general.items").getKeys(false)) {
                    if (id.toLowerCase(Locale.ROOT).startsWith(A[2].toLowerCase(Locale.ROOT))) out.add(id);
                }
            }
            return out;
        }
        if (A.length == 3 && A[0].equalsIgnoreCase("setdesc")) {
            // Item IDs
            if (plugin.getConfig().isConfigurationSection("shop.general.items")) {
                for (String id : plugin.getConfig().getConfigurationSection("shop.general.items").getKeys(false)) {
                    if (id.toLowerCase(Locale.ROOT).startsWith(A[2].toLowerCase(Locale.ROOT))) out.add(id);
                }
            }
            return out;
        }
        if (A.length == 4 && A[0].equalsIgnoreCase("setdesc")) {
            for (String lang : new String[]{"de","en","fr","nl"}) {
                if (lang.startsWith(A[3].toLowerCase(Locale.ROOT))) out.add(lang);
            }
            return out;
        }
        return out;
    }
}