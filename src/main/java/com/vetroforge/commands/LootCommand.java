package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.ChestLootChanceManager;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

public class LootCommand implements CommandExecutor {
    private final ChestLootChanceManager lm;

    public LootCommand(FactionsPlugin plugin) {
        this.lm = plugin.getChestLootChanceManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd,
                             String label, String[] args) {
        if (!sender.hasPermission("vetroforge.admin") && !sender.isOp()) {
            sender.sendMessage("§cKeine Rechte.");
            return true;
        }
        if (sender instanceof Player p && args.length == 1) {
            lm.applyLoot(p.getOpenInventory().getTopInventory(), args[0]);
            p.sendMessage("§aLoot angewendet: " + args[0]);
        }
        return true;
    }
}