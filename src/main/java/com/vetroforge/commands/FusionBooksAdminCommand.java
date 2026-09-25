package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomItemUtil;
import com.vetroforge.items.Rarity;
import com.vetroforge.util.Branding;
import org.bukkit.Bukkit;
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
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.NamespacedKey;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class FusionBooksAdminCommand implements CommandExecutor, TabCompleter, Listener {

    private final FactionsPlugin plugin;
    private final NamespacedKey actionKey;

    public FusionBooksAdminCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "fusionbooks_action");
        Bukkit.getPluginManager().registerEvents(this, plugin);
        ensureDefaults();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (args.length > 0 && args[0].equalsIgnoreCase("simulate")) {
            return simulateCommand(sender, label, args);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("test")) {
            return testCommand(sender, label, args);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("giveall")) {
            return giveAllCommand(sender, label, args);
        }

        if (args.length > 0 && args[0].equalsIgnoreCase("give")) {
            return giveCommand(sender, label, args);
        }

        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("vetroforge.admin")) {
            player.sendMessage(Branding.PREFIX + "§cKeine Rechte.");
            return true;
        }
        open(player);
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vetroforge.admin")) return Collections.emptyList();
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            if ("give".startsWith(args[0].toLowerCase(Locale.ROOT))) out.add("give");
            if ("giveall".startsWith(args[0].toLowerCase(Locale.ROOT))) out.add("giveall");
            if ("test".startsWith(args[0].toLowerCase(Locale.ROOT))) out.add("test");
            if ("simulate".startsWith(args[0].toLowerCase(Locale.ROOT))) out.add("simulate");
            return out;
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("give")
                || args[0].equalsIgnoreCase("giveall")
                || args[0].equalsIgnoreCase("test")
                || args[0].equalsIgnoreCase("simulate"))) {
            for (Player player : Bukkit.getOnlinePlayers()) {
                if (player.getName().toLowerCase(Locale.ROOT).startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(player.getName());
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("giveall")) {
            for (String amount : List.of("1", "2", "3", "5", "10")) {
                if (amount.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(amount);
            }
            return out;
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            for (String rarity : List.of("common", "rare", "epic", "legendary")) {
                if (rarity.startsWith(args[2].toLowerCase(Locale.ROOT))) out.add(rarity);
            }
            return out;
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("give")) {
            for (String option : List.of("rare", "epic", "legendary", "mystic", "1", "2", "3", "5", "10")) {
                if (option.startsWith(args[3].toLowerCase(Locale.ROOT))) out.add(option);
            }
            return out;
        }
        if (args.length == 5 && args[0].equalsIgnoreCase("give")) {
            for (String amount : List.of("1", "2", "3", "5", "10", "16", "32")) {
                if (amount.startsWith(args[4].toLowerCase(Locale.ROOT))) out.add(amount);
            }
        }
        return out;
    }

    private void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), 45, Branding.GUI_TITLE + " §8» §fFusion Books");
        fill(inv);

        inv.setItem(4, item(Material.ENCHANTED_BOOK, "§b§lFusion Books §8» §fAdmin",
                List.of(
                        "§7Steuere Drop-Chancen und Regeln direkt ingame.",
                        "",
                        "§8» §fLinksklick §7= +1%",
                        "§8» §fRechtsklick §7= -1%",
                        "§8» §fShift §7= 5%-Schritte"
                ), null));

        inv.setItem(10, toggle(Material.CHEST, "§6Crates", "crates.utilityFusionBooks.enabled",
                "§7Fusion Books in Crates aktivieren."));
        inv.setItem(11, toggle(Material.ENDER_CHEST, "§9Lootdrops", "lootdrop.fusionBooks.enabled",
                "§7Fusion Books in Lootdrops aktivieren."));
        inv.setItem(12, chance(Material.PAPER, "§9Lootdrop Basis-Chance", "lootdrop.fusionBooks.chance",
                "§7Chance für normale Lootdrops."));
        inv.setItem(13, chance(Material.GOLD_BLOCK, "§6Gold Lootdrop Chance", "lootdrop.fusionBooks.goldChance",
                "§7Chance für Gold Lootdrops."));
        inv.setItem(14, chance(Material.NETHER_STAR, "§5Mythic Lootdrop Chance", "lootdrop.fusionBooks.mythicChance",
                "§7Chance für Mythic Lootdrops."));

        inv.setItem(20, toggle(Material.DRAGON_EGG, "§5Legendary -> Mythic in Legendary Crates",
                "customitems.rarityFusion.allowLegendaryToMysticInLegendaryCrates",
                "§7Aus lassen, wenn Mythic selten bleiben soll."));
        inv.setItem(21, toggle(Material.AMETHYST_SHARD, "§dReroll beim Upgrade",
                "customitems.rarityFusion.rerollOnUpgrade",
                "§7Wuerfelt Rolls beim Rarity-Upgrade neu."));

        inv.setItem(23, toggle(Material.LIME_DYE, "§aEvents vorbereitet", "events.rewards.fusionBooks.enabled",
                "§7Config-Schalter für Event-Rewards."));
        inv.setItem(24, chance(Material.MAP, "§aEvent Chance", "events.rewards.fusionBooks.chance",
                "§7Vorbereitete Chance für Event-Rewards."));
        inv.setItem(25, toggle(Material.EMERALD, "§aShop vorbereitet", "shop.fusionBooks.enabled",
                "§7Config-Schalter für Shop-Angebote."));
        inv.setItem(26, chance(Material.GOLD_INGOT, "§aShop Angebot-Gewichtung", "shop.fusionBooks.weight",
                "§7Vorbereitete Gewichtung für Shop-Angebote."));

        inv.setItem(28, giveItem(Rarity.COMMON));
        inv.setItem(29, giveItem(Rarity.RARE));
        inv.setItem(30, giveItem(Rarity.EPIC));
        inv.setItem(31, giveItem(Rarity.LEGENDARY));
        inv.setItem(33, item(Material.BOOKSHELF, "§b§lTest-Set geben", List.of(
                "§7Gibt dir jedes Fusion Book einmal.",
                "",
                "§aKlick §8» §71 Set",
                "§eShift-Klick §8» §75 Sets"
        ), "giveall:self"));
        inv.setItem(34, item(Material.STONE_HOE, "§a§lTest-Kette geben", List.of(
                "§7Gibt dir ein Common-Testitem",
                "§7plus alle Fusion Books.",
                "",
                "§aKlick §8» §7Test-Kette erhalten"
        ), "test:self"));
        inv.setItem(35, item(Material.COMPARATOR, "§b§lSimulation laufen lassen", List.of(
                "§7Prüft Common bis Mythic automatisch.",
                "§7Kontrolliert Rarity, Name und Lore.",
                "",
                "§aKlick §8» §7Self-Test starten"
        ), "simulate:self"));

        inv.setItem(40, item(Material.BARRIER, "§cSchließen", List.of("§7GUI schließen."), "close"));
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        if (action.equals("close")) {
            player.closeInventory();
            return;
        }

        if (action.startsWith("give:")) {
            Rarity from = parseRarity(action.substring("give:".length()), null);
            Rarity to = CustomItemUtil.nextRarity(from);
            if (from == null || to == null) return;
            int amount = event.isShiftClick() ? 5 : 1;
            giveBooks(player, player, from, to, amount);
            player.playSound(player.getLocation(), Sound.ENTITY_ITEM_PICKUP, 0.8f, 1.35f);
            return;
        }

        if (action.equals("giveall:self")) {
            int sets = event.isShiftClick() ? 5 : 1;
            giveAllBooks(player, player, sets);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.65f, 1.5f);
            return;
        }

        if (action.equals("test:self")) {
            giveTestKit(player, player);
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.75f, 1.65f);
            return;
        }

        if (action.equals("simulate:self")) {
            runSimulation(player, player);
            player.playSound(player.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.65f, 1.35f);
            return;
        }

        if (action.startsWith("toggle:")) {
            String path = action.substring("toggle:".length());
            boolean next = !plugin.getConfig().getBoolean(path, false);
            plugin.getConfig().set(path, next);
            plugin.saveConfig();
            player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, next ? 1.45f : 0.75f);
            open(player);
            return;
        }

        if (action.startsWith("chance:")) {
            String path = action.substring("chance:".length());
            double current = plugin.getConfig().getDouble(path, 0.0D);
            double step = event.isShiftClick() ? 0.05D : 0.01D;
            double next = event.isRightClick() ? current - step : current + step;
            next = Math.max(0.0D, Math.min(1.0D, next));
            plugin.getConfig().set(path, Math.round(next * 10000.0D) / 10000.0D);
            plugin.saveConfig();
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.5f, event.isRightClick() ? 0.85f : 1.35f);
            open(player);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private ItemStack toggle(Material material, String name, String path, String description) {
        boolean enabled = plugin.getConfig().getBoolean(path, false);
        return item(material,
                (enabled ? "§a§lAN §8» " : "§c§lAUS §8» ") + name,
                List.of(
                        description,
                        "",
                        "§7Status: " + (enabled ? "§aaktiv" : "§cinaktiv"),
                        "§8" + path,
                        "",
                        "§eKlick §8» §7umschalten"
                ),
                "toggle:" + path);
    }

    private ItemStack chance(Material material, String name, String path, String description) {
        double value = plugin.getConfig().getDouble(path, 0.0D);
        return item(material,
                "§b" + name + " §8» §f" + percent(value),
                List.of(
                        description,
                        "",
                        "§7Aktuell: §f" + percent(value),
                        "§8" + path,
                        "",
                        "§aLinksklick §8» §7+1%",
                        "§cRechtsklick §8» §7-1%",
                        "§eShift §8» §75%-Schritte"
                ),
                "chance:" + path);
    }

    private ItemStack giveItem(Rarity from) {
        Rarity to = CustomItemUtil.nextRarity(from);
        return item(Material.ENCHANTED_BOOK,
                from.color + "§lGeben §8» " + from.colored() + " §8→ " + (to == null ? "§7-" : to.colored()),
                List.of(
                        "§7Gibt dir direkt ein Fusion Book.",
                        "",
                        "§aKlick §8» §71x geben",
                        "§eShift-Klick §8» §75x geben"
                ),
                "give:" + from.name());
    }

    private ItemStack item(Material material, String name, List<String> lore, String action) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        if (action != null) meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        stack.setItemMeta(meta);
        return stack;
    }

    private void fill(Inventory inv) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of(), null);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private String percent(double value) {
        return String.format(Locale.US, "%.1f%%", value * 100.0D);
    }

    private boolean giveCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.admin")) {
            sender.sendMessage(Branding.PREFIX + "§cKeine Rechte.");
            return true;
        }
        if (args.length < 3) {
            sender.sendMessage(Branding.PREFIX + "§7Nutze: §f/" + label + " give <spieler> <common|rare|epic|legendary> [toRarity] [anzahl]");
            return true;
        }

        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Branding.PREFIX + "§cSpieler ist nicht online.");
            return true;
        }

        Rarity from = parseRarity(args[2], null);
        if (from == null || from == Rarity.MYSTIC) {
            sender.sendMessage(Branding.PREFIX + "§cUngültige Ausgangs-Rarity.");
            return true;
        }

        Rarity to = CustomItemUtil.nextRarity(from);
        int amount = 1;
        if (args.length >= 5) {
            Rarity requestedTo = parseRarity(args[3], null);
            if (requestedTo != null) {
                to = requestedTo;
                amount = parseAmount(args[4]);
            } else {
                amount = parseAmount(args[3]);
            }
        } else if (args.length >= 4) {
            Rarity requestedTo = parseRarity(args[3], null);
            if (requestedTo != null) to = requestedTo;
            else amount = parseAmount(args[3]);
        }

        if (to == null || to.ordinal() <= from.ordinal()) {
            sender.sendMessage(Branding.PREFIX + "§cUngültiger Upgrade-Pfad.");
            return true;
        }

        giveBooks(sender, target, from, to, amount);
        return true;
    }

    private boolean giveAllCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.admin")) {
            sender.sendMessage(Branding.PREFIX + "§cKeine Rechte.");
            return true;
        }
        if (args.length < 2) {
            sender.sendMessage(Branding.PREFIX + "§7Nutze: §f/" + label + " giveall <spieler> [sets]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) {
            sender.sendMessage(Branding.PREFIX + "§cSpieler ist nicht online.");
            return true;
        }
        int sets = args.length >= 3 ? parseAmount(args[2]) : 1;
        giveAllBooks(sender, target, sets);
        return true;
    }

    private boolean testCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.admin")) {
            sender.sendMessage(Branding.PREFIX + "§cKeine Rechte.");
            return true;
        }

        Player target;
        if (args.length >= 2) {
            target = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            target = player;
        } else {
            sender.sendMessage(Branding.PREFIX + "§7Nutze: §f/" + label + " test <spieler>");
            return true;
        }

        if (target == null) {
            sender.sendMessage(Branding.PREFIX + "§cSpieler ist nicht online.");
            return true;
        }

        giveTestKit(sender, target);
        return true;
    }

    private boolean simulateCommand(CommandSender sender, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.admin")) {
            sender.sendMessage(Branding.PREFIX + "§cKeine Rechte.");
            return true;
        }

        Player viewer;
        if (args.length >= 2) {
            viewer = Bukkit.getPlayerExact(args[1]);
        } else if (sender instanceof Player player) {
            viewer = player;
        } else {
            sender.sendMessage(Branding.PREFIX + "§7Nutze: §f/" + label + " simulate <spieler>");
            return true;
        }

        if (viewer == null) {
            sender.sendMessage(Branding.PREFIX + "§cSpieler ist nicht online.");
            return true;
        }

        runSimulation(sender, viewer);
        return true;
    }

    private void giveBooks(CommandSender sender, Player target, Rarity from, Rarity to, int amount) {
        int safeAmount = Math.max(1, Math.min(64, amount));
        for (int i = 0; i < safeAmount; i++) {
            target.getInventory().addItem(CustomItemUtil.makeRarityFusionBook(plugin, from, to));
        }
        sender.sendMessage(Branding.PREFIX + "§a" + safeAmount + "x Fusion Book §7("
                + from.colored() + " §8→ " + to.colored() + "§7) §aan §f" + target.getName() + " §agegeben.");
        if (!sender.equals(target)) {
            target.sendMessage(Branding.PREFIX + "§aDu hast §f" + safeAmount + "x Fusion Book §7("
                    + from.colored() + " §8→ " + to.colored() + "§7) §aerhalten.");
        }
    }

    private void giveAllBooks(CommandSender sender, Player target, int sets) {
        int safeSets = Math.max(1, Math.min(16, sets));
        for (int i = 0; i < safeSets; i++) {
            for (Rarity from : List.of(Rarity.COMMON, Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY)) {
                Rarity to = CustomItemUtil.nextRarity(from);
                if (to != null) {
                    target.getInventory().addItem(CustomItemUtil.makeRarityFusionBook(plugin, from, to));
                }
            }
        }
        sender.sendMessage(Branding.PREFIX + "§a" + safeSets + "x Fusion-Book-Testset §aan §f"
                + target.getName() + " §agegeben.");
        if (!sender.equals(target)) {
            target.sendMessage(Branding.PREFIX + "§aDu hast §f" + safeSets + "x Fusion-Book-Testset §aerhalten.");
        }
    }

    private void giveTestKit(CommandSender sender, Player target) {
        ItemStack testItem = CustomItemUtil.makeWarHoe(plugin, Rarity.COMMON);
        CustomItemUtil.setSource(plugin, testItem, "fusionbooks_test");
        target.getInventory().addItem(testItem);
        giveAllBooks(sender, target, 1);
        sender.sendMessage(Branding.PREFIX + "§aFusion-Test-Kette an §f" + target.getName() + " §agegeben.");
        if (!sender.equals(target)) {
            target.sendMessage(Branding.PREFIX + "§aDu hast eine Fusion-Test-Kette erhalten.");
        }
    }

    private void runSimulation(CommandSender sender, Player viewer) {
        ItemStack item = CustomItemUtil.makeWarHoe(plugin, Rarity.COMMON);
        CustomItemUtil.setSource(plugin, item, "fusionbooks_simulate");

        List<String> lines = new ArrayList<>();
        boolean ok = assertState(lines, item, Rarity.COMMON, "Start");

        for (Rarity from : List.of(Rarity.COMMON, Rarity.RARE, Rarity.EPIC, Rarity.LEGENDARY)) {
            Rarity to = CustomItemUtil.nextRarity(from);
            if (to == null) {
                ok = false;
                lines.add("§c✖ " + from.name() + " hat keine nächste Rarity.");
                continue;
            }

            Rarity current = CustomItemUtil.rarityOf(plugin, item);
            if (current != from) {
                ok = false;
                lines.add("§c✖ Erwartet " + from.colored() + "§c, gefunden " + current.colored());
                continue;
            }

            CustomItemUtil.setRarity(plugin, item, to, true);
            ok &= assertState(lines, item, to, from.display + " -> " + to.display);
        }

        viewer.sendMessage("§8§m--------------------------------");
        viewer.sendMessage("§b§lFusionBooks Simulation §8» " + (ok ? "§aBESTANDEN" : "§cFEHLER"));
        for (String line : lines) viewer.sendMessage(line);
        viewer.sendMessage("§8§m--------------------------------");
        if (!sender.equals(viewer)) {
            sender.sendMessage(Branding.PREFIX + "§7Simulation für §f" + viewer.getName() + " §7ausgeführt: "
                    + (ok ? "§aBESTANDEN" : "§cFEHLER"));
        }
        if (ok) {
            viewer.playSound(viewer.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.65f, 1.7f);
        } else {
            viewer.playSound(viewer.getLocation(), Sound.BLOCK_ANVIL_LAND, 0.55f, 0.8f);
        }
    }

    private boolean assertState(List<String> lines, ItemStack item, Rarity expected, String step) {
        Rarity actual = CustomItemUtil.rarityOf(plugin, item);
        ItemMeta meta = item.hasItemMeta() ? item.getItemMeta() : null;
        String name = meta != null && meta.hasDisplayName() ? meta.getDisplayName() : "";
        List<String> lore = meta != null && meta.hasLore() && meta.getLore() != null ? meta.getLore() : List.of();

        boolean rarityOk = actual == expected;
        boolean nameOk = name.contains(expected.color) || name.toLowerCase(Locale.ROOT).contains(expected.display.toLowerCase(Locale.ROOT));
        boolean loreOk = lore.stream().anyMatch(line -> line.contains(expected.name()) || line.contains(expected.display) || line.contains(expected.color));

        boolean ok = rarityOk && nameOk && loreOk;
        lines.add((ok ? "§a✔ " : "§c✖ ") + "§7" + step + " §8» "
                + expected.colored()
                + " §8| §7Rarity " + (rarityOk ? "§aOK" : "§cFAIL")
                + " §8| §7Name " + (nameOk ? "§aOK" : "§cFAIL")
                + " §8| §7Lore " + (loreOk ? "§aOK" : "§cFAIL"));
        if (!ok) {
            lines.add("§8  Name: §7" + (name.isBlank() ? "<leer>" : name));
            lines.add("§8  Lore-Zeilen: §7" + lore.size());
        }
        return ok;
    }

    private Rarity parseRarity(String raw, Rarity fallback) {
        if (raw == null) return fallback;
        try {
            return Rarity.valueOf(raw.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return fallback;
        }
    }

    private int parseAmount(String raw) {
        try {
            return Math.max(1, Math.min(64, Integer.parseInt(raw)));
        } catch (Exception ignored) {
            return 1;
        }
    }

    private void ensureDefaults() {
        addDefault("crates.utilityFusionBooks.enabled", true);
        addDefault("lootdrop.fusionBooks.enabled", true);
        addDefault("lootdrop.fusionBooks.chance", 0.02D);
        addDefault("lootdrop.fusionBooks.goldChance", 0.02D);
        addDefault("lootdrop.fusionBooks.mythicChance", 0.02D);
        addDefault("customitems.rarityFusion.allowLegendaryToMysticInLegendaryCrates", false);
        addDefault("customitems.rarityFusion.rerollOnUpgrade", true);
        addDefault("events.rewards.fusionBooks.enabled", false);
        addDefault("events.rewards.fusionBooks.chance", 0.05D);
        addDefault("shop.fusionBooks.enabled", false);
        addDefault("shop.fusionBooks.weight", 0.05D);
        plugin.saveConfig();
    }

    private void addDefault(String path, Object value) {
        if (!plugin.getConfig().contains(path)) plugin.getConfig().set(path, value);
    }

    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
