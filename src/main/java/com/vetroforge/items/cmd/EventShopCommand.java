package com.vetroforge.items.cmd;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomItemUtil;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Locale;

public final class EventShopCommand implements CommandExecutor {

    private final FactionsPlugin plugin;

    public EventShopCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (args.length < 2 || !args[0].equalsIgnoreCase("buy")) {
            show(player);
            return true;
        }

        String product = args[1].toLowerCase(Locale.ROOT);
        return switch (product) {
            case "reroll" -> buy(player, product, Material.AMETHYST_SHARD, 8, "8 Reroll Shards");
            case "soulbind" -> buy(player, product, Material.ECHO_SHARD, 1, "Soulbind Shard");
            case "protection" -> buy(player, product, Material.NETHER_STAR, 1, "Upgrade Protection");
            default -> {
                show(player);
                yield true;
            }
        };
    }

    private boolean buy(Player player, String product, Material material, int amount, String display) {
        int price = plugin.getConfig().getInt("customitems.eventShop." + product, defaultPrice(product));
        if (!consumeTokens(player, price)) {
            player.sendMessage("§6Event Shop §8» §cDu brauchst §f" + price + " §cEvent Tokens.");
            return true;
        }
        player.getInventory().addItem(new ItemStack(material, amount)).values()
                .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
        player.sendMessage("§6Event Shop §8» §aGekauft: §f" + display + " §8(-" + price + " Tokens)");
        return true;
    }

    private void show(Player player) {
        player.sendMessage("§6§lEVENT SHOP §8» §f" + countTokens(player) + " Tokens");
        player.sendMessage("§e/eventshop buy reroll §8• §f" + price("reroll") + " Tokens");
        player.sendMessage("§e/eventshop buy soulbind §8• §f" + price("soulbind") + " Tokens");
        player.sendMessage("§e/eventshop buy protection §8• §f" + price("protection") + " Tokens");
    }

    private int price(String product) {
        return plugin.getConfig().getInt("customitems.eventShop." + product, defaultPrice(product));
    }

    private int defaultPrice(String product) {
        return switch (product) {
            case "reroll" -> 2;
            case "soulbind" -> 4;
            case "protection" -> 10;
            default -> 1;
        };
    }

    private int countTokens(Player player) {
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (CustomItemUtil.isEventToken(plugin, item)) count += item.getAmount();
        }
        return count;
    }

    private boolean consumeTokens(Player player, int amount) {
        if (countTokens(player) < amount) return false;
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int slot = 0; slot < contents.length && remaining > 0; slot++) {
            ItemStack item = contents[slot];
            if (!CustomItemUtil.isEventToken(plugin, item)) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            if (item.getAmount() <= 0) player.getInventory().setItem(slot, null);
            remaining -= take;
        }
        return remaining == 0;
    }
}
