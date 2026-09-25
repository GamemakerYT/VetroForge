package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.List;
import java.util.Locale;

public final class AuctionHouseCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public AuctionHouseCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(UiDecor.playersOnly());
            return true;
        }
        if (plugin.getAuctionHouseManager() == null) {
            player.sendMessage("§cAuktionshaus ist nicht geladen.");
            return true;
        }
        if (args.length == 0) {
            plugin.getAuctionHouseManager().open(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("sell")) {
            if (args.length < 2) {
                player.sendMessage("§e/" + label + " sell <preis>");
                return true;
            }
            int price;
            try {
                price = Integer.parseInt(args[1]);
            } catch (NumberFormatException ignored) {
                player.sendMessage("§cUngültiger Preis.");
                return true;
            }
            plugin.getAuctionHouseManager().sell(player, price);
            return true;
        }
        if (sub.equals("cancel")) {
            if (args.length < 2) {
                player.sendMessage("§e/" + label + " cancel <id>");
                return true;
            }
            plugin.getAuctionHouseManager().cancel(player, args[1]);
            return true;
        }
        if (sub.equals("auction")) {
            if (args.length < 3) {
                player.sendMessage("§e/" + label + " auction <startpreis> <minuten>");
                return true;
            }
            int startPrice;
            int minutes;
            try {
                startPrice = Integer.parseInt(args[1]);
                minutes = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                player.sendMessage("§cUngültige Zahl.");
                return true;
            }
            plugin.getAuctionHouseManager().sellBid(player, startPrice, minutes);
            return true;
        }
        if (sub.equals("bid")) {
            if (args.length < 3) {
                player.sendMessage("§e/" + label + " bid <id> <betrag>");
                return true;
            }
            int amount;
            try {
                amount = Integer.parseInt(args[2]);
            } catch (NumberFormatException ignored) {
                player.sendMessage("§cUngültiger Betrag.");
                return true;
            }
            plugin.getAuctionHouseManager().bid(player, args[1], amount);
            return true;
        }
        if (sub.equals("search")) {
            if (args.length < 2) {
                player.sendMessage("§e/" + label + " search <item>");
                return true;
            }
            plugin.getAuctionHouseManager().search(player, String.join(" ", java.util.Arrays.copyOfRange(args, 1, args.length)));
            return true;
        }
        if (sub.equals("category") || sub.equals("cat")) {
            if (args.length < 2) {
                player.sendMessage("§e/" + label + " category <blocks|combat|gear|keys|mystic>");
                return true;
            }
            plugin.getAuctionHouseManager().category(player, args[1]);
            return true;
        }

        player.sendMessage("§e/" + label + " §7- Auktionshaus öffnen");
        player.sendMessage("§e/" + label + " sell <preis> §7- Item in Hand als Sofortkauf anbieten");
        player.sendMessage("§e/" + label + " auction <startpreis> <minuten> §7- Item in Hand als Bid-Auktion anbieten");
        player.sendMessage("§e/" + label + " bid <id> <betrag> §7- auf eine Bid-Auktion bieten");
        player.sendMessage("§e/" + label + " search <item> §7- Auktionen suchen");
        player.sendMessage("§e/" + label + " category <name> §7- Kategorie öffnen");
        player.sendMessage("§e/" + label + " cancel <id> §7- eigene Auktion abbrechen");
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            return List.of("sell", "auction", "bid", "search", "category", "cancel").stream().filter(value -> value.startsWith(prefix)).toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("sell")) {
            return List.of("100", "500", "1000", "5000", "10000").stream()
                    .filter(value -> value.startsWith(args[1]))
                    .toList();
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("auction")) {
            return List.of("100", "500", "1000", "5000", "10000").stream()
                    .filter(value -> value.startsWith(args[1]))
                    .toList();
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("auction")) {
            return List.of("10", "30", "60", "360", "1440").stream()
                    .filter(value -> value.startsWith(args[2]))
                    .toList();
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("category") || args[0].equalsIgnoreCase("cat"))) {
            return List.of("blocks", "combat", "gear", "keys", "mystic").stream()
                    .filter(value -> value.startsWith(args[1].toLowerCase(Locale.ROOT)))
                    .toList();
        }
        return List.of();
    }
}
