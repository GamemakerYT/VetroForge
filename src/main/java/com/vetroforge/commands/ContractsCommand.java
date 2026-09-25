package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.contracts.ContractsManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;

public class ContractsCommand implements CommandExecutor {
    private final FactionsPlugin plugin;
    public ContractsCommand(FactionsPlugin plugin){ this.plugin=plugin; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a){
        if (!(s instanceof Player p)){ s.sendMessage("Ingame."); return true; }
        Inventory inv = Bukkit.createInventory(null, 54, ChatColor.GOLD+"Contracts");
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        var pm = pane.getItemMeta(); pm.setDisplayName(" "); pane.setItemMeta(pm);
        for (int i=0;i<54;i++) inv.setItem(i, pane);

        int slot = 10;
        for (ContractsManager.Contract ct : plugin.getContractsManager().daily()){
            inv.setItem(slot, card(ct, p)); slot += (slot%9==7)?3:1;
        }
        slot = 28;
        for (ContractsManager.Contract ct : plugin.getContractsManager().weekly()){
            inv.setItem(slot, card(ct, p)); slot += (slot%9==7)?3:1;
        }
        p.openInventory(inv);
        return true;
    }

    private ItemStack card(ContractsManager.Contract c, Player p){
        int prog = plugin.getContractsManager().getProgress(p.getUniqueId(), c.id);
        ItemStack it = new ItemStack(prog>=c.goal ? Material.LIME_DYE : Material.PAPER);
        var im = it.getItemMeta();
        im.setDisplayName("§e"+c.name);
        java.util.List<String> lore = new java.util.ArrayList<>();
        lore.add("§7Typ: §f"+c.type);
        lore.add("§7Ziel: §f"+c.goal);
        lore.add("§7Fortschritt: §f"+prog+"§7/§f"+c.goal);
        lore.add("§7Belohnung: §f"+c.reward);
        if (prog>=c.goal) lore.add("§aKlicke zum Abholen");
        im.setLore(lore);
        it.setItemMeta(im);
        return it;
    }
}