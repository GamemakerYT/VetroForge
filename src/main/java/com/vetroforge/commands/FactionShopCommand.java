// src/main/java/com/vetroforge/commands/FactionShopCommand.java
package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class FactionShopCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;

    public FactionShopCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!(sender instanceof Player p)) { sender.sendMessage(UiDecor.playersOnly()); return true; }
        if (a.length == 0) {
            plugin.getFactionShopManager().open(p);
            return true;
        }
        if (a[0].equalsIgnoreCase("list")) {
            plugin.getFactionShopManager().listItems(p);
            return true;
        }
        if (a[0].equalsIgnoreCase("buy")) {
            if (a.length < 2) { p.sendMessage(tr(p, "usage.buy", "label", label)); return true; }
            plugin.getFactionShopManager().buy(p, a[1]);
            return true;
        }
        p.sendMessage(tr(p, "usage.main", "label", label));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String a, String[] A) {
        List<String> out = new ArrayList<>();
        if (A.length == 1) {
            String p = A[0].toLowerCase(Locale.ROOT);
            for (String k : new String[]{"list","buy"}) if (k.startsWith(p)) out.add(k);
            return out;
        }
        if (A.length == 2 && A[0].equalsIgnoreCase("buy")) {
            String p = A[1].toLowerCase(Locale.ROOT);
            for (String id : plugin.getFactionShopManager().getItemIds()) if (id.startsWith(p)) out.add(id);
            return out;
        }
        return out;
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, "faction.shop." + key, VetroForgeUtil.vars(values));
    }
}
