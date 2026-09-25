// src/main/java/com/vetroforge/items/cmd/GiveCustomCommand.java
package com.vetroforge.items.cmd;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.crates.CrateKeyType;
import com.vetroforge.crates.CrateKeyUtil;
import com.vetroforge.items.CustomEnchant;
import com.vetroforge.items.CustomItemCatalog;
import com.vetroforge.items.CustomItemUtil;
import com.vetroforge.items.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public class GiveCustomCommand implements CommandExecutor, TabCompleter {

    private static final String PREFIX = "§6Custom Items §8» §f";

    private final FactionsPlugin plugin;

    public GiveCustomCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("vetroforge.givecustom")) {
            sender.sendMessage(PREFIX + "§cKeine Berechtigung.");
            return true;
        }

        if (args.length < 2) {
            sendHelp(sender, label);
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[0]);
        if (target == null) {
            sender.sendMessage(PREFIX + "§cSpieler ist nicht online.");
            return true;
        }

        String sub = args[1].toLowerCase(Locale.ROOT);

        if (sub.equals("key")) {
            return handleKey(sender, target, label, args);
        }

        if (sub.equals("enchant")) {
            return handleEnchant(sender, target, label, args);
        }

        return handleCatalogItem(sender, target, sub, args);
    }

    private boolean handleCatalogItem(CommandSender sender, Player target, String itemId, String[] args) {
        CustomItemCatalog.Entry entry = findEntry(itemId);

        if (entry == null) {
            sender.sendMessage(PREFIX + "§cUnbekanntes Item: §f" + itemId);
            return true;
        }

        Rarity rarity = Rarity.RARE;
        int amount = 1;

        if (entry.hasRarity && args.length >= 3) {
            rarity = parseRarity(args[2], Rarity.RARE);
        }

        if (entry.hasRarity && args.length >= 4) {
            amount = parseInt(args[3], 1);
        } else if (!entry.hasRarity && args.length >= 3) {
            amount = parseInt(args[2], 1);
        }

        amount = Math.max(1, amount);

        for (int i = 0; i < amount; i++) {
            ItemStack item = entry.factory.make(plugin, entry.hasRarity ? rarity : null);
            CustomItemUtil.setSource(plugin, item, "admin_give");
            target.getInventory().addItem(item);
            if (plugin.getItemProgressionService() != null) {
                plugin.getItemProgressionService().discover(target, item);
                plugin.getItemProgressionService().audit("ADMIN_GIVE", target, item, "sender=" + sender.getName());
            }
        }

        String suffix = entry.hasRarity ? " §7(" + rarity.color + rarity.display + "§7)" : "";
        sender.sendMessage(PREFIX + "§a" + amount + "x §f" + entry.display + suffix + " §aan §f" + target.getName() + " §agegeben.");
        target.sendMessage(PREFIX + "§aDu hast §f" + amount + "x §a" + entry.display + suffix + " §aerhalten.");
        playSuccess(target);

        return true;
    }

    private boolean handleKey(CommandSender sender, Player target, String label, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(PREFIX + "§cNutze: §f/" + label + " <spieler> key <common|rare|epic|legendary|mystic> <anzahl>");
            return true;
        }

        CrateKeyType type = parseKey(args[2]);
        int amount = Math.max(1, parseInt(args[3], 1));

        target.getInventory().addItem(CrateKeyUtil.make(plugin, type, amount));

        sender.sendMessage(PREFIX + "§a" + amount + "x §f" + prettyKey(type) + " §aan §f" + target.getName() + " §agegeben.");
        target.sendMessage(PREFIX + "§aDu hast §f" + amount + "x §a" + prettyKey(type) + " §aerhalten.");
        playSuccess(target);

        return true;
    }

    private boolean handleEnchant(CommandSender sender, Player target, String label, String[] args) {
        if (args.length < 4) {
            sender.sendMessage(PREFIX + "§cNutze: §f/" + label + " <spieler> enchant <lifesteal|bleed|rage|shatter|guard> <level>");
            return true;
        }

        CustomEnchant enchant = parseEnchant(args[2]);
        int level = Math.max(1, parseInt(args[3], 1));

        ItemStack inHand = target.getInventory().getItemInMainHand();
        if (inHand == null || inHand.getType().isAir() || CustomItemUtil.baseId(plugin, inHand) == null) {
            sender.sendMessage(PREFIX + "§c" + target.getName() + " §chat kein passendes Custom-Item in der Main-Hand.");
            return true;
        }

        CustomItemUtil.setEnchant(plugin, inHand, enchant, level);
        CustomItemUtil.refreshLore(plugin, inHand);

        sender.sendMessage(PREFIX + "§aEnchant §f" + prettyEnchant(enchant) + " " + roman(level) + " §aauf §f" + target.getName() + " §agesetzt.");
        target.sendMessage(PREFIX + "§aDein Item wurde mit §f" + prettyEnchant(enchant) + " " + roman(level) + " §averzaubert.");
        playSuccess(target);

        return true;
    }

    private void sendHelp(CommandSender sender, String label) {
        sender.sendMessage("§8§m------------------------------------------------");
        sender.sendMessage("§6Custom Items §8» §fBefehle");
        sender.sendMessage("§7/" + label + " <spieler> <itemId> [rarity] [anzahl]");
        sender.sendMessage("§7/" + label + " <spieler> key <type> <anzahl>");
        sender.sendMessage("§7/" + label + " <spieler> enchant <type> <level>");
        sender.sendMessage("§8§m------------------------------------------------");
    }

    private CustomItemCatalog.Entry findEntry(String id) {
        for (CustomItemCatalog.Entry entry : CustomItemCatalog.all()) {
            if (entry.id.equalsIgnoreCase(id)) {
                return entry;
            }
        }
        return null;
    }

    private void playSuccess(Player player) {
        try {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1f, 1.15f);
        } catch (Throwable ignored) {
        }
    }

    private Rarity parseRarity(String raw, Rarity fallback) {
        try {
            return Rarity.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private CrateKeyType parseKey(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "rare" -> CrateKeyType.RARE;
            case "epic" -> CrateKeyType.EPIC;
            case "legendary" -> CrateKeyType.LEGENDARY;
            case "mystic" -> CrateKeyType.MYSTIC;
            default -> CrateKeyType.COMMON;
        };
    }

    private CustomEnchant parseEnchant(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "bleed" -> CustomEnchant.BLEED;
            case "rage" -> CustomEnchant.RAGE;
            case "shatter" -> CustomEnchant.SHATTER;
            case "guard" -> CustomEnchant.GUARD;
            default -> CustomEnchant.LIFESTEAL;
        };
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private String prettyKey(CrateKeyType type) {
        return switch (type) {
            case COMMON -> "Common Key";
            case RARE -> "Rare Key";
            case EPIC -> "Epic Key";
            case LEGENDARY -> "Legendary Key";
            case MYSTIC -> "Mystic Key";
        };
    }

    private String prettyEnchant(CustomEnchant enchant) {
        return switch (enchant) {
            case LIFESTEAL -> "Lifesteal";
            case BLEED -> "Bleed";
            case RAGE -> "Rage";
            case SHATTER -> "Shatter";
            case GUARD -> "Guard";
        };
    }

    private String roman(int n) {
        return switch (Math.max(1, Math.min(10, n))) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            default -> "X";
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command cmd, String alias, String[] args) {
        List<String> out = new ArrayList<>();

        if (!sender.hasPermission("vetroforge.givecustom")) {
            return Collections.emptyList();
        }

        if (args.length == 1) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                out.add(player.getName());
            }
            return StringUtil.copyPartialMatches(args[0], out, new ArrayList<>());
        }

        if (args.length == 2) {
            for (CustomItemCatalog.Entry entry : CustomItemCatalog.all()) {
                out.add(entry.id);
            }
            out.add("key");
            out.add("enchant");
            return StringUtil.copyPartialMatches(args[1], out, new ArrayList<>());
        }

        if (args.length == 3) {
            if ("key".equalsIgnoreCase(args[1])) {
                out = Arrays.asList("common", "rare", "epic", "legendary", "mystic");
                return StringUtil.copyPartialMatches(args[2], out, new ArrayList<>());
            }

            if ("enchant".equalsIgnoreCase(args[1])) {
                out = Arrays.asList("lifesteal", "bleed", "rage", "shatter", "guard");
                return StringUtil.copyPartialMatches(args[2], out, new ArrayList<>());
            }

            CustomItemCatalog.Entry entry = findEntry(args[1]);
            if (entry != null && entry.hasRarity) {
                out = Arrays.asList("common", "rare", "epic", "legendary", "mystic");
                return StringUtil.copyPartialMatches(args[2], out, new ArrayList<>());
            }

            out = Arrays.asList("1", "2", "3", "5", "10");
            return StringUtil.copyPartialMatches(args[2], out, new ArrayList<>());
        }

        if (args.length == 4) {
            if ("key".equalsIgnoreCase(args[1])) {
                out = Arrays.asList("1", "2", "3", "5", "10", "16", "32", "64");
                return StringUtil.copyPartialMatches(args[3], out, new ArrayList<>());
            }

            if ("enchant".equalsIgnoreCase(args[1])) {
                out = Arrays.asList("1", "2", "3", "4", "5");
                return StringUtil.copyPartialMatches(args[3], out, new ArrayList<>());
            }

            CustomItemCatalog.Entry entry = findEntry(args[1]);
            if (entry != null && entry.hasRarity) {
                out = Arrays.asList("1", "2", "3", "5", "10");
                return StringUtil.copyPartialMatches(args[3], out, new ArrayList<>());
            }
        }

        return Collections.emptyList();
    }
}
