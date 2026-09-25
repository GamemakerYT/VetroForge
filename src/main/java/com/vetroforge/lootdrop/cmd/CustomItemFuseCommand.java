// src/main/java/com/vetroforge/items/cmd/CustomItemFuseCommand.java
package com.vetroforge.lootdrop.cmd;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomItemUtil;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class CustomItemFuseCommand implements CommandExecutor {
    private final FactionsPlugin plugin;
    public CustomItemFuseCommand(FactionsPlugin p){ this.plugin=p; }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a){
        if (!(s instanceof Player p)) { s.sendMessage("Ingame."); return true; }
        ItemStack base = p.getInventory().getItemInMainHand();
        if (CustomItemUtil.baseId(plugin, base)==null){ p.sendMessage("§cHalte das Basis-Item in der Hand."); return true; }

        // finde identisches Item im Inventar
        String bid = CustomItemUtil.baseId(plugin, base);
        for (ItemStack it : p.getInventory().getContents()){
            if (it==null || it==base) continue;
            if (bid.equals(CustomItemUtil.baseId(plugin, it))
                    && CustomItemUtil.rarityOf(plugin, it)==CustomItemUtil.rarityOf(plugin, base)){
                CustomItemUtil.SacrificeResult result = CustomItemUtil.sacrificeOnto(plugin, p, base);
                switch (result) {
                    case MAX_LEVEL -> p.sendMessage("§cItem hat bereits das Max-Level erreicht.");
                    case INSUFFICIENT_COINS -> p.sendMessage("§cNicht genug Coins fuer das Level-Up.");
                    case PROGRESS, LEVEL_UP -> {
                        it.setAmount(it.getAmount() - 1);
                        p.sendMessage("§aFusion +1! Neues Level: §f" + CustomItemUtil.levelOf(plugin, base));
                    }
                }
                return true;
            }
        }
        p.sendMessage("§eKein passendes Duplikat gefunden.");
        return true;
    }
}