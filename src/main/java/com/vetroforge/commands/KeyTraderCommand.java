package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class KeyTraderCommand implements CommandExecutor, TabCompleter, Listener {

    private static final String TITLE = ChatColor.AQUA + "✦ " + ChatColor.WHITE + "Crystal Händler" + ChatColor.AQUA + " ✦";

    private final FactionsPlugin plugin;
    private final NamespacedKey rarityKey;
    private KeyTraderNpcService npcService;

    public KeyTraderCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.rarityKey = new NamespacedKey(plugin, "key_trader_rarity");
    }

    public void setNpcService(KeyTraderNpcService npcService) {
        this.npcService = npcService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur ingame.");
            return true;
        }
        if (args.length > 0) {
            return handleAdmin(player, args);
        }
        open(player);
        return true;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
        ItemStack filler = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, ChatColor.DARK_GRAY + " ", List.of(), null);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        inventory.setItem(4, item(Material.EMERALD,
                ChatColor.AQUA + "" + ChatColor.BOLD + "Crystal Händler",
                List.of(
                        ChatColor.GRAY + "Kontostand: " + ChatColor.YELLOW + coins(player) + " Coins",
                        ChatColor.GRAY + "Kaufe hier Crystals für den",
                        ChatColor.WHITE + "Crystal NPC" + ChatColor.GRAY + ".",
                        "",
                        ChatColor.DARK_PURPLE + "Mystic Crystals",
                        ChatColor.GRAY + "gibt es nur aus Lootdrops."
                ), null));

        inventory.setItem(10, keyButton(Rarity.COMMON, Material.NETHER_STAR));
        inventory.setItem(12, keyButton(Rarity.RARE, Material.NETHER_STAR));
        inventory.setItem(14, keyButton(Rarity.EPIC, Material.NETHER_STAR));
        inventory.setItem(16, keyButton(Rarity.LEGENDARY, Material.NETHER_STAR));

        player.openInventory(inventory);
    }

    private boolean handleAdmin(Player player, String[] args) {
        String sub = args[0].toLowerCase(Locale.ROOT);
        if (!sub.equals("set") && !sub.equals("remove") && !sub.equals("list")) {
            open(player);
            return true;
        }
        if (!player.hasPermission("vetroforge.keytrader.admin")) {
            player.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (npcService == null) {
            player.sendMessage(ChatColor.RED + "Crystal-Händler NPC-Service ist nicht bereit.");
            return true;
        }
        switch (sub) {
            case "set" -> {
                if (plugin.getWarzoneRegionManager() != null
                        && !plugin.getWarzoneRegionManager().isLobbySpawnArea(player.getLocation())) {
                    player.sendMessage(ChatColor.RED + "Der Crystal-Händler darf nur im Spawn/Lobby-Bereich gesetzt werden.");
                    return true;
                }
                if (npcService.set(player.getLocation())) {
                    player.sendMessage(ChatColor.GREEN + "Crystal-Händler gesetzt.");
                } else {
                    player.sendMessage(ChatColor.RED + "Crystal-Händler konnte nicht gesetzt werden.");
                }
            }
            case "remove" -> {
                boolean removed = npcService.remove();
                player.sendMessage(removed ? ChatColor.GREEN + "Crystal-Händler entfernt." : ChatColor.YELLOW + "Kein Crystal-Händler vorhanden.");
            }
            case "list" -> player.sendMessage(npcService.info());
            default -> {
            }
        }
        return true;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String rarityName = clicked.getItemMeta().getPersistentDataContainer().get(rarityKey, PersistentDataType.STRING);
        if (rarityName == null) return;

        Rarity rarity;
        try {
            rarity = Rarity.valueOf(rarityName);
        } catch (IllegalArgumentException ex) {
            return;
        }
        if (rarity == Rarity.MYSTIC) {
            player.sendMessage(ChatColor.DARK_PURPLE + "Mystic " + ChatColor.GRAY + "Crystals gibt es nur aus Lootdrops.");
            return;
        }

        int price = price(rarity);
        int current = coins(player);
        if (current < 0) {
            player.sendMessage(ChatColor.YELLOW + "Deine Coins werden noch geladen.");
            return;
        }
        if (current < price) {
            player.sendMessage(ChatColor.RED + "Nicht genug Coins. Benötigt: " + price);
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.8f);
            return;
        }

        plugin.getCoinsManager().removeCoins(player.getUniqueId(), price);
        ItemStack key = plugin.getKeyCrateManager().createKeyItem(rarity, 1);
        if (plugin.getItemInboxService() != null) {
            plugin.getItemInboxService().giveOrStore(player, key, "key-trader:" + rarity.name().toLowerCase(Locale.ROOT));
        } else {
            player.getInventory().addItem(key);
        }

        player.sendMessage(ChatColor.GREEN + "Gekauft: " + rarity.color + rarity.display
                + ChatColor.GRAY + " Crystal für " + ChatColor.YELLOW + price + " Coins");
        player.playSound(player.getLocation(), org.bukkit.Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.9f, 1.25f);
        open(player);
    }

    private ItemStack keyButton(Rarity rarity, Material material) {
        int price = price(rarity);
        return item(material,
                rarity.color + "" + ChatColor.BOLD + rarity.display + " Crystal",
                List.of(
                        ChatColor.GRAY + "Preis: " + ChatColor.YELLOW + price + " Coins",
                        ChatColor.GRAY + "Für den Crystal NPC.",
                        "",
                        ChatColor.GREEN + "Klicke zum Kaufen."
                ), rarity);
    }

    private ItemStack item(Material material, String name, List<String> lore, Rarity rarity) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            if (rarity != null) meta.getPersistentDataContainer().set(rarityKey, PersistentDataType.STRING, rarity.name());
        });
        return item;
    }

    private int price(Rarity rarity) {
        return switch (rarity) {
            case COMMON -> plugin.getConfig().getInt("keytrader.prices.common", 750);
            case RARE -> plugin.getConfig().getInt("keytrader.prices.rare", 4_000);
            case EPIC -> plugin.getConfig().getInt("keytrader.prices.epic", 12_000);
            case LEGENDARY -> plugin.getConfig().getInt("keytrader.prices.legendary", 37_000);
            case MYSTIC -> Integer.MAX_VALUE;
        };
    }

    private int coins(Player player) {
        return plugin.getCoinsManager() == null ? 0 : plugin.getCoinsManager().getCoins(player.getUniqueId());
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("vetroforge.keytrader.admin")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String option : List.of("set", "remove", "list")) {
                if (option.startsWith(prefix)) out.add(option);
            }
            return out;
        }
        return new ArrayList<>();
    }
}
