package com.vetroforge.crates.cmd;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.Locale;

public class GiveKeyCommand implements CommandExecutor {

    private final FactionsPlugin plugin;
    public GiveKeyCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!s.hasPermission("vetroforge.admin")) { s.sendMessage("§cNo permission."); return true; }
        if (a.length < 3) { s.sendMessage("§e/"+label+" <player> <common|rare|epic|legendary|mystic> <amount>"); return true; }

        Player t = Bukkit.getPlayerExact(a[0]);
        if (t == null) { s.sendMessage("§cSpieler nicht online."); return true; }

        Rarity r = parseRarity(a[1]);
        int amt;
        try { amt = Math.max(1, Integer.parseInt(a[2])); } catch (Exception ex){ s.sendMessage("§cAmount?"); return true; }

        plugin.getKeyCrateManager().giveKey(t, r, amt);
        s.sendMessage("§aGave §f" + amt + "× " + r.display + " §aCrystal to §f" + t.getName());
        return true;
    }

    private Rarity parseRarity(String s){
        try { return Rarity.valueOf(s.toUpperCase(Locale.ROOT)); } catch (Exception e){ return Rarity.COMMON; }
    }
}
