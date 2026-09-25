// src/main/java/com/vetroforge/lootdrop/cmd/LootTestCommand.java
package com.vetroforge.lootdrop.cmd;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.lootdrop.LootTables;
import com.vetroforge.lootdrop.model.Tier;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

public class LootTestCommand implements CommandExecutor {
    private final FactionsPlugin plugin;

    public LootTestCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Nur ingame."); return true; }

        // Kiste setzen
        var loc = p.getLocation().getBlock().getLocation().add(0, 1, 0);
        loc.getBlock().setType(Material.CHEST);
        org.bukkit.block.Chest chest = (org.bukkit.block.Chest) loc.getBlock().getState();
        var inv = chest.getBlockInventory();

        boolean mystic = a.length > 0 && a[0].equalsIgnoreCase("mystic");
        for (ItemStack roll : new LootTables(plugin).roll(mystic ? Tier.MYTHIC : Tier.GOLD, 0.0D, false)) {
            if (roll != null) inv.addItem(roll);
        }

        s.sendMessage("§aLoot-Testkiste gespawnt. MysticActive=" + mystic);
        return true;
    }
}
