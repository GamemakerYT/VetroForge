package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomItemUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;

import java.util.Collections;
import java.util.List;

public final class ApplyCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public ApplyCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler koennen /" + label + " nutzen.");
            return true;
        }

        ItemStack item = player.getInventory().getItemInMainHand();
        if (item == null || item.getType() == Material.AIR) {
            player.sendMessage("§cHalte ein anwendbares Item in der Hand.");
            return true;
        }

        if (item.getType() == Material.EXPERIENCE_BOTTLE) {
            int amount = item.getAmount();
            item.setAmount(0);
            int xp = plugin.getConfig().getInt("level.xpBottle", 15) * amount;
            if (plugin.getLevelProgressionService() != null) {
                plugin.getLevelProgressionService().addXp(player, xp, amount + "x XP-Flasche");
                player.sendActionBar("§bLevel §8• §a+" + xp + " XP");
            } else {
                player.giveExp(xp);
                player.sendActionBar("§bXP §8• §a+" + xp);
            }
            return true;
        }

        if (CustomItemUtil.baseId(plugin, item) != null || isCustomEnchantBook(item)) {
            player.sendMessage("§bVetroForge §8• §7Öffne Forge für Item-Fusion, Apply-Books und Enchants.");
            Bukkit.dispatchCommand(player, "forge");
            return true;
        }

        player.sendMessage("§cDieses Item kann nicht direkt angewendet werden.");
        player.sendMessage("§7Nutze §f/forge §7für Custom Items, Fusionen und Enchant-Books.");
        return true;
    }

    private boolean isCustomEnchantBook(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        Byte marker = item.getItemMeta().getPersistentDataContainer()
                .get(new NamespacedKey(plugin, "custom_enchant_book"), PersistentDataType.BYTE);
        return marker != null && marker == (byte) 1;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return Collections.emptyList();
    }
}
