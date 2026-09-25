package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.services.BoosterManager;
import com.vetroforge.util.Branding;
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
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.scheduler.BukkitTask;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class BoosterCommand implements CommandExecutor, TabCompleter, Listener {

    private final FactionsPlugin plugin;
    private final Map<UUID, Inventory> openInventories = new HashMap<>();
    private BukkitTask refresher;

    public BoosterCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
        this.refresher = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshOpenGuis, 20L, 20L);
    }

    @Override
    public boolean onCommand(@NotNull CommandSender sender, @NotNull Command command, @NotNull String label, String[] args) {
        BoosterManager boosters = plugin.getBoosterManager();
        if (boosters == null) {
            sender.sendMessage(prefix() + "§cBooster-System ist nicht geladen.");
            return true;
        }

        if (args.length == 0) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§7/" + label + " globalcoin <spieler|Server>");
                return true;
            }
            openGui(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("activate")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cNur Spieler können persönliche Booster aktivieren.");
                return true;
            }
            if (args.length < 2) {
                sender.sendMessage(prefix() + "§7Nutze: §f/" + label + " activate <coin|raid>");
                return true;
            }
            BoosterManager.Type type = BoosterManager.Type.parse(args[1]);
            if (type == null || type == BoosterManager.Type.GLOBAL_COIN) {
                sender.sendMessage(prefix() + "§cUnbekannter Booster. Nutze coin oder raid.");
                return true;
            }
            boosters.activatePersonalFromVoucher(player, type);
            return true;
        }

        if (sub.equals("give")) {
            if (!sender.hasPermission("vetroforge.booster.admin")) {
                sender.sendMessage(prefix() + "§cKeine Rechte.");
                return true;
            }
            if (args.length < 3) {
                sender.sendMessage(prefix() + "§7Nutze: §f/" + label + " give <spieler> <coin|raid> [anzahl]");
                return true;
            }
            Player target = Bukkit.getPlayerExact(args[1]);
            BoosterManager.Type type = BoosterManager.Type.parse(args[2]);
            int amount = args.length >= 4 ? parseAmount(args[3]) : 1;
            if (target == null || type == null || type == BoosterManager.Type.GLOBAL_COIN) {
                sender.sendMessage(prefix() + "§cSpieler oder Booster ungültig.");
                return true;
            }
            boosters.giveVoucher(target, type, amount);
            sender.sendMessage(prefix() + "§aBooster-Voucher gegeben.");
            return true;
        }

        if (sub.equals("buy")) {
            if (!(sender instanceof Player player)) {
                sender.sendMessage("§cNur Spieler können Booster kaufen.");
                return true;
            }
            if (args.length < 2 || BoosterManager.Type.parse(args[1]) != BoosterManager.Type.GLOBAL_COIN) {
                sender.sendMessage(prefix() + "§7Nutze: §f/" + label + " buy globalcoin");
                return true;
            }
            boosters.buyGlobalCoin(player);
            return true;
        }

        if (sub.equals("globalcoin")) {
            if (!sender.hasPermission("vetroforge.booster.admin")) {
                sender.sendMessage(prefix() + "§cKeine Rechte.");
                return true;
            }
            String activator = args.length >= 2 ? args[1] : sender.getName();
            Player player = Bukkit.getPlayerExact(activator);
            boosters.activateGlobalCoin(player == null ? activator : player.getName());
            sender.sendMessage(prefix() + "§aGlobaler Coin Booster gestartet.");
            return true;
        }

        sender.sendMessage(prefix() + "§7Nutze: §f/" + label + "§7, §f/" + label + " activate <coin|raid>§7, §f/" + label + " buy globalcoin");
        return true;
    }

    private String prefix() {
        return "§b§lVetroForge §8» ";
    }

    @Override
    public @Nullable List<String> onTabComplete(@NotNull CommandSender sender, @NotNull Command command, @NotNull String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("activate", "buy", "give", "globalcoin")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(option);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("activate")) {
            for (String option : List.of("coin", "raid")) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(option);
            }
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (String option : List.of("coin", "raid")) {
                if (option.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(option);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("buy")) {
            if ("globalcoin".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("globalcoin");
        }
        return out;
    }

    private void openGui(Player player) {
        Inventory inv = Bukkit.createInventory(new BoosterHolder(), 45, Branding.GUI_TITLE + " §8» §fBooster");
        buildGui(player, inv);
        openInventories.put(player.getUniqueId(), inv);
        player.openInventory(inv);
    }

    private void buildGui(Player player, Inventory inv) {
        BoosterManager boosters = plugin.getBoosterManager();
        if (boosters == null) return;
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass(frameSlot(i, inv.getSize()) ? Material.CYAN_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE));
        }
        inv.setItem(4, item(Material.NETHER_STAR, "§b§lVetroForge §8» §fBooster",
                "§7Aktiviere persönliche Booster",
                "§7oder starte einen globalen Coin-Boost.",
                "",
                "§6Coin§7: " + boosters.remaining(player.getUniqueId(), BoosterManager.Type.COIN),
                "§dRaid§7: " + boosters.remaining(player.getUniqueId(), BoosterManager.Type.RAID),
                "§eGlobal§7: " + boosters.remaining(player.getUniqueId(), BoosterManager.Type.GLOBAL_COIN)));
        inv.setItem(20, boosterItem(boosters, player, BoosterManager.Type.COIN, Material.GOLD_INGOT, "§6§lCoin Booster",
                "§7Dauer: §f2 Stunden",
                "§7Effekt: §e+" + percent("boosters.personalCoin.multiplier") + "% Coins",
                "§7Offen: " + boosters.availableText(player, BoosterManager.Type.COIN),
                "§7Status: " + boosters.remaining(player.getUniqueId(), BoosterManager.Type.COIN),
                "",
                "§bKlicken §8» §fAktivieren"));
        inv.setItem(22, boosterItem(boosters, player, BoosterManager.Type.RAID, Material.NETHERITE_SWORD, "§d§lRaid Booster",
                "§7Dauer: §f2 Stunden",
                "§7Effekt: §d+" + percent("boosters.raid.multiplier") + "% Raid-XP",
                "§7Offen: " + boosters.availableText(player, BoosterManager.Type.RAID),
                "§7Status: " + boosters.remaining(player.getUniqueId(), BoosterManager.Type.RAID),
                "",
                "§bKlicken §8» §fAktivieren"));
        inv.setItem(24, boosterItem(boosters, player, BoosterManager.Type.GLOBAL_COIN, Material.BEACON, "§e§lGlobaler Coin Booster",
                "§7Dauer: §f1 Stunde",
                "§7Effekt: §e+" + percent("boosters.globalCoin.multiplier") + "% Coins für alle",
                "§7Kosten: §640.000 Coins",
                "§7Status: " + boosters.remaining(player.getUniqueId(), BoosterManager.Type.GLOBAL_COIN),
                "§7Aktiviert von: §f" + boosters.globalActivator(),
                "",
                "§bKlicken §8» §fKaufen"));
        if (player.hasPermission("vetroforge.booster.admin")) {
            inv.setItem(40, item(Material.PAPER, "§f§lAdmin §8• §7Tebex-Commands",
                    "§f/booster give {username} coin 1",
                    "§f/booster give {username} raid 1",
                    "§f/booster globalcoin {username}"));
        }
    }

    private void refreshOpenGuis() {
        openInventories.entrySet().removeIf(entry -> {
            Player player = Bukkit.getPlayer(entry.getKey());
            Inventory inv = entry.getValue();
            if (player == null || !player.isOnline()
                    || player.getOpenInventory() == null
                    || player.getOpenInventory().getTopInventory() != inv
                    || !(inv.getHolder() instanceof BoosterHolder)) {
                return true;
            }
            buildGui(player, inv);
            return false;
        });
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getInventory().getHolder() instanceof BoosterHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        BoosterManager boosters = plugin.getBoosterManager();
        if (boosters == null) return;
        switch (event.getRawSlot()) {
            case 20 -> boosters.activatePersonalFromVoucher(player, BoosterManager.Type.COIN);
            case 22 -> boosters.activatePersonalFromVoucher(player, BoosterManager.Type.RAID);
            case 24 -> boosters.buyGlobalCoin(player);
            default -> {
                return;
            }
        }
        Bukkit.getScheduler().runTask(plugin, () -> openGui(player));
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (event.getInventory().getHolder() instanceof BoosterHolder) {
            openInventories.remove(event.getPlayer().getUniqueId());
        }
    }

    private int percent(String path) {
        double multiplier = plugin.getConfig().getDouble(path, 1.5D);
        return Math.max(0, (int) Math.round((multiplier - 1.0D) * 100.0D));
    }

    private int parseAmount(String raw) {
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(raw)));
        } catch (NumberFormatException ignored) {
            return 1;
        }
    }

    private ItemStack glass(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private boolean frameSlot(int slot, int size) {
        int last = size - 1;
        return slot < 9 || slot > last - 9 || slot % 9 == 0 || slot % 9 == 8;
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack boosterItem(BoosterManager boosters, Player player, BoosterManager.Type type,
                                  Material material, String name, String... lore) {
        ItemStack item = item(material, name, lore);
        if (!boosters.isActive(player.getUniqueId(), type)) return item;
        ItemMeta meta = item.getItemMeta();
        List<String> updatedLore = new ArrayList<>(meta.getLore() == null ? List.of() : meta.getLore());
        updatedLore.add(0, "§a§lAKTIV §8• §7Timer läuft live");
        meta.setLore(updatedLore);
        meta.addEnchant(Enchantment.UNBREAKING, 1, true);
        meta.addItemFlags(ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ATTRIBUTES);
        item.setItemMeta(meta);
        return item;
    }

    private static final class BoosterHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
