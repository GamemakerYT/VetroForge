package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

import java.util.List;

public class ChangelogCommand implements CommandExecutor {
    private final FactionsPlugin plugin;
    public ChangelogCommand(FactionsPlugin plugin){ this.plugin=plugin; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a){
        if (!(s instanceof Player p)){ s.sendMessage("Ingame."); return true; }
        var list = plugin.getConfig().getStringList("changelog.lines");
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.GOLD+"Season Changelog");
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var pm = pane.getItemMeta(); pm.setDisplayName(" "); pane.setItemMeta(pm);
        for (int i=0;i<54;i++) inv.setItem(i, pane);

        int idx = 10;
        for (String line : list){
            ItemStack it = new ItemStack(Material.PAPER);
            var im = it.getItemMeta(); im.setDisplayName("§f"+line); it.setItemMeta(im);
            inv.setItem(idx, it);
            idx += (idx%9==7) ? 3 : 1; // hübsche Spalten
            if (idx>=53) break;
        }
        p.openInventory(inv);
        return true;
    }
}