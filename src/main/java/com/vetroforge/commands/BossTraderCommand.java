package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomItemUtil;
import com.vetroforge.items.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class BossTraderCommand implements CommandExecutor, TabCompleter, Listener {
    private static final String TITLE = ChatColor.DARK_RED + "" + ChatColor.BOLD + "VetroForge " + ChatColor.DARK_GRAY + "• " + ChatColor.RED + "Boss Trader";
    private static final String FRAGMENT_ID = "disparity_core_fragment";

    private final FactionsPlugin plugin;
    private final NamespacedKey fragmentKey;
    private final NamespacedKey actionKey;
    private BossTraderNpcService npcService;

    public BossTraderCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.fragmentKey = new NamespacedKey(plugin, "boss_fragment");
        this.actionKey = new NamespacedKey(plugin, "boss_trader_action");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void setNpcService(BossTraderNpcService npcService) {
        this.npcService = npcService;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length >= 1 && isAdminSub(args[0])) {
            if (!sender.hasPermission("vetroforge.boss.admin")) {
                sender.sendMessage(ChatColor.RED + "Keine Rechte.");
                return true;
            }
            String sub = args[0].toLowerCase(Locale.ROOT);
            if (sub.equals("give")) {
                Player target = args.length >= 2 ? Bukkit.getPlayerExact(args[1]) : sender instanceof Player p ? p : null;
                int amount = args.length >= 3 ? parseAmount(args[2]) : 1;
                if (target == null) {
                    sender.sendMessage(ChatColor.RED + "Spieler nicht online.");
                    return true;
                }
                target.getInventory().addItem(createFragment(plugin, amount));
                sender.sendMessage(ChatColor.GREEN + "Boss-Fragmente gegeben: " + amount + " an " + target.getName());
                return true;
            }
            if (!(sender instanceof Player player)) {
                sender.sendMessage(ChatColor.RED + "Dieser Boss-Trader Befehl ist nur ingame.");
                return true;
            }
            if (npcService == null) {
                sender.sendMessage(ChatColor.RED + "Boss-Trader NPC-Service ist nicht bereit.");
                return true;
            }
            switch (sub) {
                case "set" -> {
                    String name = args.length >= 2 ? join(args, 1) : "&4&lDisparity Trader";
                    sender.sendMessage(npcService.set(player.getLocation(), name)
                            ? ChatColor.GREEN + "Boss-Trader gesetzt."
                            : ChatColor.RED + "Boss-Trader konnte nicht gesetzt werden.");
                }
                case "skin" -> {
                    String skin = args.length >= 2 ? join(args, 1) : "WITHER_SKELETON_SKULL";
                    npcService.setSkin(skin);
                    sender.sendMessage(ChatColor.GREEN + "Boss-Trader Skin gesetzt: " + ChatColor.WHITE + skin);
                }
                case "remove" -> sender.sendMessage(npcService.remove()
                        ? ChatColor.GREEN + "Boss-Trader entfernt."
                        : ChatColor.YELLOW + "Kein Boss-Trader vorhanden.");
                case "list", "info" -> sender.sendMessage(npcService.info());
                default -> {
                }
            }
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur ingame.");
            return true;
        }
        open(player);
        return true;
    }

    public void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 27, TITLE);
        ItemStack filler = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), null);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, filler);

        int owned = countFragments(player);
        inventory.setItem(4, item(Material.NETHER_STAR, "§c§lDisparity Boss Trader", List.of(
                "§7Sammle Boss-Fragmente aus",
                "§7Warzone-Bossen und tausche sie",
                "§7gegen starke Rewards.",
                "",
                "§7Deine Fragmente: §f" + owned
        ), null));

        inventory.setItem(11, offerButton(Offer.DRAGONS_BREATH, owned));
        inventory.setItem(13, offerButton(Offer.STORMCALLER, owned));
        inventory.setItem(15, offerButton(Offer.BOSS_CHESTPLATE, owned));
        inventory.setItem(22, createFragment(plugin, Math.max(1, Math.min(64, owned))));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (event.getClickedInventory() == null || event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        Offer offer = Offer.from(action);
        if (offer == null) return;

        int cost = offer.cost(plugin);
        if (countFragments(player) < cost) {
            player.sendMessage(ChatColor.RED + "Du brauchst " + cost + " Disparity Core Fragments.");
            player.playSound(player.getLocation(), org.bukkit.Sound.BLOCK_NOTE_BLOCK_BASS, 0.8f, 0.7f);
            open(player);
            return;
        }
        removeFragments(player, cost);
        ItemStack reward = offer.createReward(plugin);
        if (plugin.getItemInboxService() != null) {
            plugin.getItemInboxService().giveOrStore(player, reward, "boss-trader:" + offer.id);
        } else {
            player.getInventory().addItem(reward).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        player.sendMessage(ChatColor.DARK_RED + "Boss Trader " + ChatColor.DARK_GRAY + "» " + ChatColor.GREEN + offer.display + " gekauft.");
        player.playSound(player.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.9f, 1.0f);
        open(player);
    }

    public static ItemStack createFragment(FactionsPlugin plugin, int amount) {
        NamespacedKey key = new NamespacedKey(plugin, "boss_fragment");
        ItemStack item = new ItemStack(Material.NETHER_STAR, Math.max(1, amount));
        item.editMeta(meta -> {
            meta.setDisplayName("§c§lDisparity Core Fragment");
            meta.setLore(List.of(
                    "§7Boss-Waehrung aus der Warzone.",
                    "§7Sammle §f30§7 Stueck fuer",
                    "§7den §cBoss Trader§7.",
                    "",
                    "§8ID: " + FRAGMENT_ID
            ));
            meta.addItemFlags(ItemFlag.values());
            meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, FRAGMENT_ID);
        });
        return item;
    }

    private ItemStack item(Material material, String name, List<String> lore, String action) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        });
        return item;
    }

    private int countFragments(Player player) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isFragment(item)) total += item.getAmount();
        }
        return total;
    }

    private boolean isFragment(ItemStack item) {
        if (item == null || item.getType() == Material.AIR || !item.hasItemMeta()) return false;
        String value = item.getItemMeta().getPersistentDataContainer().get(fragmentKey, PersistentDataType.STRING);
        return FRAGMENT_ID.equals(value);
    }

    private void removeFragments(Player player, int amount) {
        int remaining = amount;
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length && remaining > 0; i++) {
            ItemStack item = contents[i];
            if (!isFragment(item)) continue;
            int take = Math.min(remaining, item.getAmount());
            item.setAmount(item.getAmount() - take);
            remaining -= take;
            if (item.getAmount() <= 0) contents[i] = null;
        }
        player.getInventory().setContents(contents);
    }

    private ItemStack offerButton(Offer offer, int owned) {
        int cost = offer.cost(plugin);
        List<String> lore = new ArrayList<>();
        lore.add("§7Kosten: §c" + cost + " Disparity Core Fragments");
        lore.addAll(offer.lore);
        lore.add("");
        lore.add(owned >= cost ? "§aKlicke zum Kaufen." : "§cDir fehlen noch " + (cost - owned) + " Fragmente.");
        return item(offer.material, offer.name, lore, offer.id);
    }

    private int parseAmount(String raw) {
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(raw)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1 && sender.hasPermission("vetroforge.boss.admin")) {
            String prefix = args[0].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String option : List.of("give", "set", "skin", "remove", "list", "info")) {
                if (option.startsWith(prefix)) out.add(option);
            }
            return out;
        }
        if (args.length == 2 && sender.hasPermission("vetroforge.boss.admin")
                && args[0].equalsIgnoreCase("skin")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (String option : List.of("WITHER_SKELETON_SKULL", "DRAGON_HEAD", "SKELETON_SKULL", "ZOMBIE_HEAD", "GamemakerYT")) {
                if (option.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(option);
            }
            return out;
        }
        if (args.length == 2 && sender.hasPermission("vetroforge.boss.admin")) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            List<String> out = new ArrayList<>();
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(player.getName());
            }
            return out;
        }
        return List.of();
    }

    private boolean isAdminSub(String raw) {
        if (raw == null) return false;
        String value = raw.toLowerCase(Locale.ROOT);
        return value.equals("give")
                || value.equals("set")
                || value.equals("skin")
                || value.equals("remove")
                || value.equals("list")
                || value.equals("info");
    }

    private String join(String[] args, int start) {
        StringBuilder builder = new StringBuilder();
        for (int i = start; i < args.length; i++) {
            if (i > start) builder.append(' ');
            builder.append(args[i]);
        }
        return builder.toString();
    }

    private enum Offer {
        DRAGONS_BREATH(
                "buy_dragons_breath",
                "Dragon's Breath",
                Material.DRAGON_BREATH,
                "§5§lDragon's Breath",
                "warzone.boss.trader.dragonsBreathCost",
                30,
                List.of("§7Feueratem-Artefakt fuer", "§7starke Warzone-Fights.")
        ),
        STORMCALLER(
                "buy_stormcaller",
                "Stormcaller",
                Material.NETHERITE_AXE,
                "§5§lStormcaller",
                "warzone.boss.trader.stormcallerCost",
                45,
                List.of("§7Mystic Boss-Waffe.", "§7Selten und teuer.")
        ),
        BOSS_CHESTPLATE(
                "buy_boss_chestplate",
                "Titan Chestplate",
                Material.NETHERITE_CHESTPLATE,
                "§6§lTitan Chestplate",
                "warzone.boss.trader.titanChestplateCost",
                24,
                List.of("§7Legendary Armor-Piece", "§7aus Boss-Fragmenten.")
        );

        private final String id;
        private final String display;
        private final Material material;
        private final String name;
        private final String configPath;
        private final int defaultCost;
        private final List<String> lore;

        Offer(String id, String display, Material material, String name, String configPath, int defaultCost, List<String> lore) {
            this.id = id;
            this.display = display;
            this.material = material;
            this.name = name;
            this.configPath = configPath;
            this.defaultCost = defaultCost;
            this.lore = lore;
        }

        private int cost(FactionsPlugin plugin) {
            return Math.max(1, plugin.getConfig().getInt(configPath, defaultCost));
        }

        private ItemStack createReward(FactionsPlugin plugin) {
            return switch (this) {
                case DRAGONS_BREATH -> CustomItemUtil.makeDragonsBreath(plugin);
                case STORMCALLER -> CustomItemUtil.makeStormcaller(plugin);
                case BOSS_CHESTPLATE -> CustomItemUtil.makeArmorPiece(plugin, "titan", Material.NETHERITE_CHESTPLATE, Rarity.LEGENDARY);
            };
        }

        private static Offer from(String raw) {
            if (raw == null) return null;
            for (Offer offer : values()) {
                if (offer.id.equalsIgnoreCase(raw)) return offer;
            }
            return null;
        }
    }
}
