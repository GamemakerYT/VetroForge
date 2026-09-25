package com.vetroforge.commands;

import com.vetroforge.services.RankKitService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class RankKitCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String TITLE = "§4§lVetroForge §8• §cKits";

    private final RankKitService service;
    private final RankKitService.KitType fixedType;

    public RankKitCommand(RankKitService service, RankKitService.KitType fixedType) {
        this.service = service;
        this.fixedType = fixedType;
        if (fixedType == null) {
            service.plugin().getServer().getPluginManager().registerEvents(this, service.plugin());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        RankKitService.KitType type = fixedType;
        if (type == null) {
            if (args.length == 0) {
                open(player);
                return true;
            }
            type = parse(args[0]);
            if (type == null) {
                player.sendMessage(ChatColor.RED + "Unbekanntes Kit. Nutze starter, daily, weekly oder monthly.");
                return true;
            }
        }
        service.claim(player, type);
        return true;
    }

    private void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
        decorate(inventory);

        RankKitService.RankTier tier = service.tier(player);
        inventory.setItem(4, item(Material.NETHER_STAR, "§c§lRank Kits", List.of(
                "§7Dein Rang: §f" + tier.display,
                "§8Daily, Weekly und Monthly skalieren mit deinem Rang.",
                "",
                "§cPremium §8• §7cleaner, schneller, uebersichtlicher"
        )));
        inventory.setItem(20, kitItem(player, Material.CHAINMAIL_CHESTPLATE, "§f§lStarter Kit", RankKitService.KitType.STARTER,
                "§7Einmalig fuer neue Spieler",
                "§8Divine Armor, Stone Club, Food, Crystals"));
        inventory.setItem(22, kitItem(player, Material.COOKED_BEEF, "§6§lDaily Kit", RankKitService.KitType.DAILY,
                "§7Ab §6Ember",
                "§8Food, XP und kleine Daily-Boni"));
        inventory.setItem(24, kitItem(player, Material.OBSIDIAN, "§5§lWeekly Kit", RankKitService.KitType.WEEKLY,
                "§7Ab §bVanguard",
                "§8Raid-Materialien und Crystals"));
        inventory.setItem(31, kitItem(player, Material.DRAGON_EGG, "§d§lMonthly Kit", RankKitService.KitType.MONTHLY,
                "§7Ab §dApex",
                "§8Premium Crystals und hoehere Bundles"));

        inventory.setItem(40, item(Material.PLAYER_HEAD, "§fDein Progress", List.of(
                "§7Rang: §c" + tier.display,
                "§7Vaults und Kits werden ueber VetroPerms vergeben.",
                "§8Ember -> Vanguard -> Obsidian -> Apex -> Vetro+"
        )));
        player.openInventory(inventory);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.35f);
    }

    private void decorate(Inventory inventory) {
        ItemStack black = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        ItemStack red = item(Material.RED_STAINED_GLASS_PANE, " ", List.of());
        ItemStack gray = item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, black);
        for (int slot : List.of(0, 1, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53)) inventory.setItem(slot, red);
        for (int slot : List.of(2, 3, 5, 6, 18, 26, 27, 35, 47, 48, 50, 51)) inventory.setItem(slot, gray);
    }

    private ItemStack kitItem(Player player, Material material, String name, RankKitService.KitType type, String line1, String line2) {
        return item(material, name, List.of(
                line1,
                line2,
                "",
                service.statusLine(player, type),
                service.canClaim(player, type) ? "§aLinksklick §8» §fKit abholen" : "§8Aktuell nicht claimbar",
                "§8ID: " + type.key
        ));
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>(lore));
            meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_UNBREAKABLE);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler
    public void onKitClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType().isAir() || !clicked.hasItemMeta()) return;
        ItemMeta meta = clicked.getItemMeta();
        if (meta == null || meta.getLore() == null) return;
        for (String line : meta.getLore()) {
            if (!ChatColor.stripColor(line).startsWith("ID: ")) continue;
            RankKitService.KitType type = parse(ChatColor.stripColor(line).substring(4).trim());
            if (type != null) {
                boolean animated = service.canClaim(player, type);
                player.closeInventory();
                service.claim(player, type);
                if (animated) {
                    player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.45f);
                    player.sendTitle("§c§lKIT CLAIMED", "§7" + type.display + " Kit wurde abgeholt.", 5, 35, 10);
                } else {
                    player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5f, 0.75f);
                }
            }
            return;
        }
    }

    @EventHandler
    public void onKitDrag(InventoryDragEvent event) {
        if (TITLE.equals(event.getView().getTitle())) event.setCancelled(true);
    }

    private RankKitService.KitType parse(String raw) {
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "starter", "start", "firstjoin", "anfang" -> RankKitService.KitType.STARTER;
            case "daily", "day", "tag" -> RankKitService.KitType.DAILY;
            case "weekly", "week", "woche" -> RankKitService.KitType.WEEKLY;
            case "monthly", "month", "monat" -> RankKitService.KitType.MONTHLY;
            default -> null;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (fixedType != null || args.length != 1) return List.of();
        return List.of("starter", "daily", "weekly", "monthly");
    }
}
