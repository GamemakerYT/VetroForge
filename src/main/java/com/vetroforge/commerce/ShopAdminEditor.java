package com.vetroforge.commerce;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.HashMap;

public final class ShopAdminEditor implements Listener {
    private static final String TITLE = "§cAdminShop Editor";
    private static final String TITLE_PREFIX = TITLE;
    private static final String CATEGORY_TITLE = TITLE + " §8Kategorien";
    private static final int ITEMS_PER_PAGE = 45;
    private static final int PREV_SLOT = 45;
    private static final int NEXT_SLOT = 46;
    private static final int CATEGORY_SLOT = 47;
    private static final int SEARCH_SLOT = 48;
    private static final int SAVE_SLOT = 53;
    private static final int INFO_SLOT = 49;
    private static final int BULK_DOWN_SLOT = 50;
    private static final int BULK_UP_SLOT = 51;
    private static final int PREVIEW_SLOT = 52;

    private final FactionsPlugin plugin;
    private final NamespacedKey controlKey;
    private final NamespacedKey itemIdKey;
    private final Map<UUID, String> categoryFilter = new HashMap<>();
    private final Map<UUID, String> searchFilter = new HashMap<>();
    private final Set<UUID> awaitingSearch = new LinkedHashSet<>();

    public ShopAdminEditor(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.controlKey = new NamespacedKey(plugin, "adminshop_control");
        this.itemIdKey = new NamespacedKey(plugin, "adminshop_item_id");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        open(player, 0);
    }

    private void openCategoryMenu(Player player) {
        Inventory inv = Bukkit.createInventory(null, 54, CATEGORY_TITLE);
        inv.setItem(0, categoryButton("all", "§fAlle Items", Material.CHEST,
                "§7Zeigt den kompletten Server-Shop.",
                "§7Gut für Suche und schnelle Übersicht."));

        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop.categories");
        int slot = 10;
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (slot >= 44) break;
                String name = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("shop.categories." + id + ".name", id));
                Material icon = Material.matchMaterial(plugin.getConfig().getString("shop.categories." + id + ".icon", "CHEST"));
                if (icon == null || icon == Material.AIR) icon = Material.CHEST;
                int count = plugin.getConfig().getStringList("shop.categories." + id + ".items").size();
                inv.setItem(slot++, categoryButton(id, name, icon,
                        "§7Items: §f" + count,
                        "§7Klick: Kategorie bearbeiten.",
                        "§8Speichern schreibt in diese Kategorie."));
                if (slot % 9 == 8) slot += 2;
            }
        }

        inv.setItem(49, control(Material.BARRIER, "§eZurück", "back", "§7Zurück zum Editor."));
        player.openInventory(inv);
    }

    private void open(Player player, int page) {
        List<String> ids = visibleIds(player);
        int maxPage = Math.max(0, (ids.size() - 1) / ITEMS_PER_PAGE);
        int safePage = Math.max(0, Math.min(page, maxPage));
        Inventory inv = Bukkit.createInventory(null, 54, TITLE + " §8S." + (safePage + 1));
        int start = safePage * ITEMS_PER_PAGE;
        int end = Math.min(ids.size(), start + ITEMS_PER_PAGE);
        int slot = 0;
        for (int i = start; i < end; i++) {
            inv.setItem(slot++, itemFor(ids.get(i)));
        }
        inv.setItem(PREV_SLOT, control(Material.ARROW, "§eVorherige Seite", "page:" + (safePage - 1),
                "§7Seite §f" + (safePage + 1) + "§8/§f" + (maxPage + 1)));
        inv.setItem(NEXT_SLOT, control(Material.ARROW, "§eNächste Seite", "page:" + (safePage + 1),
                "§7Seite §f" + (safePage + 1) + "§8/§f" + (maxPage + 1)));
        inv.setItem(CATEGORY_SLOT, control(Material.COMPASS, "§6Kategorie: §f" + categoryLabel(activeCategory(player)), "category",
                "§7Klick öffnet das Kategorie-Menü.",
                "§7Aktiv: §f" + activeCategory(player)));
        inv.setItem(SEARCH_SLOT, control(Material.SPYGLASS, "§eSuche", "search",
                activeSearch(player).isBlank() ? "§7Klick und Suchtext in Chat schreiben." : "§7Aktiv: §f" + activeSearch(player),
                "§7Schreibe clear zum Zurücksetzen."));
        inv.setItem(INFO_SLOT, control(Material.BOOK, "§6So funktioniert es",
                "info",
                "§7Lege Items in die freien Slots.",
                "§7Klick auf Shop-Item: Preis bearbeiten.",
                "§7Links: Buy +10, Rechts: Sell +10.",
                "§7Drop/Q: Buy -10, Middle: Sell -10.",
                "§7Shift: 100er Schritte.",
                "§7Speichern betrifft nur diese Seite.",
                "§7Klicke Speichern unten rechts."));
        inv.setItem(SAVE_SLOT, control(Material.LIME_CONCRETE, "§aShop speichern",
                "save",
                "§7Speichert die sichtbare Seite.",
                "§7Preise bleiben erhalten, neue Items bekommen Defaults."));
        inv.setItem(BULK_DOWN_SLOT, control(Material.REDSTONE, "§cBulk senken", "bulk:-0.10",
                "§7Klick: Buy/Sell -10%.",
                "§7Shift-Klick: Buy/Sell -100 Coins."));
        inv.setItem(BULK_UP_SLOT, control(Material.EMERALD, "§aBulk erhöhen", "bulk:0.10",
                "§7Klick: Buy/Sell +10%.",
                "§7Shift-Klick: Buy/Sell +100 Coins."));
        inv.setItem(PREVIEW_SLOT, control(Material.PLAYER_HEAD, "§bSpieler-Preview", "preview",
                "§7Öffnet /shop aus Spieler-Sicht."));
        player.openInventory(inv);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent event) {
        if (!event.getView().getTitle().startsWith(TITLE_PREFIX)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!player.hasPermission("vetroforge.shop.admin")) {
            event.setCancelled(true);
            return;
        }

        if (event.getView().getTitle().equals(CATEGORY_TITLE)) {
            event.setCancelled(true);
            String tag = controlTag(event.getCurrentItem());
            if (tag == null) return;
            if (tag.equals("back")) {
                open(player, 0);
                return;
            }
            if (tag.startsWith("cat:")) {
                categoryFilter.put(player.getUniqueId(), tag.substring(4));
                searchFilter.remove(player.getUniqueId());
                open(player, 0);
            }
            return;
        }

        if (event.getRawSlot() == SAVE_SLOT) {
            event.setCancelled(true);
            save(player, event.getInventory(), pageFromTitle(event.getView().getTitle()));
            return;
        }
        if (event.getRawSlot() == INFO_SLOT) {
            event.setCancelled(true);
            return;
        }
        if (event.getRawSlot() == PREV_SLOT || event.getRawSlot() == NEXT_SLOT) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            String tag = controlTag(current);
            if (tag != null && tag.startsWith("page:")) {
                open(player, parseInt(tag.substring(5), 0));
            }
            return;
        }
        if (event.getRawSlot() == CATEGORY_SLOT) {
            event.setCancelled(true);
            openCategoryMenu(player);
            return;
        }
        if (event.getRawSlot() == SEARCH_SLOT) {
            event.setCancelled(true);
            awaitingSearch.add(player.getUniqueId());
            player.closeInventory();
            player.sendMessage(ChatColor.GOLD + "AdminShop §8» §7Schreibe jetzt deinen Suchtext in den Chat. §8(clear = zurücksetzen)");
            return;
        }
        if (event.getRawSlot() == BULK_DOWN_SLOT || event.getRawSlot() == BULK_UP_SLOT) {
            event.setCancelled(true);
            ItemStack current = event.getCurrentItem();
            String tag = controlTag(current);
            if (tag != null && tag.startsWith("bulk:")) {
                double value = parseDouble(tag.substring(5), 0.0);
                if (event.isShiftClick()) bulkFlatAdjust(player, value < 0 ? -100.0 : 100.0);
                else bulkAdjust(player, value);
            }
            open(player, pageFromTitle(event.getView().getTitle()));
            return;
        }
        if (event.getRawSlot() == PREVIEW_SLOT) {
            event.setCancelled(true);
            ShopGui.openBuy(plugin, player);
            return;
        }
        if (event.getRawSlot() >= 0 && event.getRawSlot() < event.getInventory().getSize()) {
            ItemStack current = event.getCurrentItem();
            if (isControl(current)) event.setCancelled(true);
            if (current != null && current.getType() != Material.AIR && event.getCursor().getType() == Material.AIR) {
                String id = itemId(current);
                if (id != null) {
                    event.setCancelled(true);
                    adjustPrice(player, event.getInventory(), event.getRawSlot(), id, event.getClick());
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onSearchChat(AsyncPlayerChatEvent event) {
        UUID uuid = event.getPlayer().getUniqueId();
        if (!awaitingSearch.remove(uuid)) return;
        event.setCancelled(true);
        String message = event.getMessage() == null ? "" : event.getMessage().trim();
        if (message.equalsIgnoreCase("clear") || message.equalsIgnoreCase("reset")) searchFilter.remove(uuid);
        else searchFilter.put(uuid, message);
        Bukkit.getScheduler().runTask(plugin, () -> open(event.getPlayer(), 0));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDrag(InventoryDragEvent event) {
        if (!event.getView().getTitle().startsWith(TITLE_PREFIX)) return;
        for (int slot : event.getRawSlots()) {
            if (slot >= ITEMS_PER_PAGE && slot < 54) {
                event.setCancelled(true);
                return;
            }
        }
    }

    private void save(Player player, Inventory inv, int page) {
        List<String> oldIds = visibleIds(player);
        List<String> allIds = currentIds();
        int start = Math.max(0, page) * ITEMS_PER_PAGE;
        int end = Math.min(oldIds.size(), start + ITEMS_PER_PAGE);
        List<String> merged = new ArrayList<>();
        if (start > 0) merged.addAll(oldIds.subList(0, Math.min(start, oldIds.size())));

        Set<String> used = new LinkedHashSet<>(merged);

        for (int slot = 0; slot < ITEMS_PER_PAGE; slot++) {
            ItemStack item = inv.getItem(slot);
            if (item == null || item.getType() == Material.AIR || isControl(item)) continue;
            String existing = itemId(item);
            String id = existing != null && oldIds.contains(existing) && !used.contains(existing)
                    ? existing
                    : uniqueId(baseId(item), used);
            used.add(id);
            merged.add(id);
            String base = "shop.general.items." + id + ".";
            plugin.getConfig().set(base + "material", item.getType().name());
            plugin.getConfig().set(base + "amount", Math.max(1, item.getAmount()));
            plugin.getConfig().set(base + "name", displayName(item, id));
            if (!plugin.getConfig().isSet("shop.prices." + id + ".buy")) {
                plugin.getConfig().set("shop.prices." + id + ".buy", defaultBuy(item));
            }
            if (!plugin.getConfig().isSet("shop.prices." + id + ".sell")) {
                plugin.getConfig().set("shop.prices." + id + ".sell", Math.max(1, defaultBuy(item) * 0.5));
            }
            try { if (plugin.getAiCommerceManager() != null) plugin.getAiCommerceManager().get(id); } catch (Throwable ignored) {}
        }
        if (end < oldIds.size()) {
            for (String id : oldIds.subList(end, oldIds.size())) {
                if (used.add(id)) merged.add(id);
            }
        }
        for (int i = start; i < end; i++) {
            String oldId = oldIds.get(i);
            if (!merged.contains(oldId)) plugin.getConfig().set("shop.general.items." + oldId, null);
        }
        String targetCategory = activeCategory(player);
        if (targetCategory.equals("all")) targetCategory = "admin";
        if (!plugin.getConfig().isConfigurationSection("shop.categories." + targetCategory)) {
            plugin.getConfig().set("shop.categories." + targetCategory + ".name", "&f" + targetCategory);
            plugin.getConfig().set("shop.categories." + targetCategory + ".icon", "CHEST");
        }
        if (targetCategory.equals("admin")) {
            plugin.getConfig().set("shop.categories.admin.name", "&cAdmin-Shop");
            plugin.getConfig().set("shop.categories.admin.icon", "NETHER_STAR");
            plugin.getConfig().set("shop.categories.admin.items", mergeCategoryAdmin(allIds, oldIds, merged));
        } else {
            plugin.getConfig().set("shop.categories." + targetCategory + ".items", merged);
        }
        plugin.saveConfig();
        try { if (plugin.getAiCommerceManager() != null) plugin.getAiCommerceManager().saveRuntime(); } catch (Throwable ignored) {}
        player.sendMessage(ChatColor.GREEN + "AdminShop Seite gespeichert: " + ChatColor.WHITE + merged.size() + ChatColor.GRAY + " Items gesamt.");
        open(player, page);
    }

    private ItemStack itemFor(String id) {
        String base = "shop.general.items." + id + ".";
        Material material = Material.matchMaterial(plugin.getConfig().getString(base + "material", "STONE"));
        if (material == null || material == Material.AIR) material = Material.STONE;
        int amount = Math.max(1, plugin.getConfig().getInt(base + "amount", 1));
        ItemStack item = new ItemStack(material, Math.min(material.getMaxStackSize(), amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString(base + "name", "&f" + id)));
        List<String> lore = new ArrayList<>();
        lore.add("§8ID: " + id);
        lore.add("§7Buy: §6" + plugin.getConfig().getDouble("shop.prices." + id + ".buy", 10.0));
        lore.add("§7Sell: §6" + plugin.getConfig().getDouble("shop.prices." + id + ".sell", 5.0));
        lore.add(" ");
        lore.add("§eLinks §7Buy +10 §8| §eShift-Links §7Buy +100");
        lore.add("§eRechts §7Sell +10 §8| §eShift-Rechts §7Sell +100");
        lore.add("§cDrop/Q §7Buy -10 §8| §cShift-Drop §7Buy -100");
        lore.add("§cMiddle §7Sell -10 §8| §cF-Taste §7Sell -100");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(itemIdKey, PersistentDataType.STRING, id);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack control(Material material, String name, String tag, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        meta.getPersistentDataContainer().set(controlKey, PersistentDataType.STRING, tag);
        item.setItemMeta(meta);
        return item;
    }

    private boolean isControl(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return false;
        return item.getItemMeta().getPersistentDataContainer().has(controlKey, PersistentDataType.STRING);
    }

    private String controlTag(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(controlKey, PersistentDataType.STRING);
    }

    private String itemId(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(itemIdKey, PersistentDataType.STRING);
    }

    private void adjustPrice(Player player, Inventory inv, int slot, String id, ClickType click) {
        boolean sell = click == ClickType.RIGHT || click == ClickType.SHIFT_RIGHT
                || click == ClickType.MIDDLE || click == ClickType.SWAP_OFFHAND;
        boolean buy = click == ClickType.LEFT || click == ClickType.SHIFT_LEFT
                || click == ClickType.DROP || click == ClickType.CONTROL_DROP;
        if (!buy && !sell) return;
        boolean large = click == ClickType.SHIFT_LEFT || click == ClickType.SHIFT_RIGHT
                || click == ClickType.CONTROL_DROP || click == ClickType.SWAP_OFFHAND;
        boolean negative = click == ClickType.DROP || click == ClickType.CONTROL_DROP
                || click == ClickType.MIDDLE || click == ClickType.SWAP_OFFHAND;
        double delta = (large ? 100.0 : 10.0) * (negative ? -1.0 : 1.0);

        String buyPath = "shop.prices." + id + ".buy";
        String sellPath = "shop.prices." + id + ".sell";
        double currentBuy = plugin.getConfig().getDouble(buyPath, 10.0);
        double currentSell = plugin.getConfig().getDouble(sellPath, Math.max(1.0, currentBuy * 0.5));
        double nextBuy = buy ? Math.max(1.0, currentBuy + delta) : currentBuy;
        double nextSell = sell ? Math.max(1.0, currentSell + delta) : currentSell;
        if (buy) plugin.getConfig().set(buyPath, nextBuy);
        if (sell) plugin.getConfig().set(sellPath, nextSell);
        plugin.saveConfig();
        try {
            if (plugin.getAiCommerceManager() != null) {
                if (buy) plugin.getAiCommerceManager().applyPriceProposals(java.util.Map.of(id, nextBuy), false);
                if (sell) plugin.getAiCommerceManager().applyPriceProposals(java.util.Map.of(id, nextSell), true);
            }
        } catch (Throwable ignored) {}
        inv.setItem(slot, itemFor(id));
        player.sendMessage(ChatColor.GOLD + "AdminShop §8» §f" + id
                + ChatColor.GRAY + " geändert: §6" + (buy ? "Buy" : "Sell")
                + ChatColor.GRAY + " Buy: §6" + money(nextBuy)
                + ChatColor.GRAY + " Sell: §6" + money(nextSell));
    }

    private String baseId(ItemStack item) {
        String plain = ChatColor.stripColor(displayName(item, item.getType().name()));
        if (plain == null || plain.isBlank()) plain = item.getType().name();
        String id = plain.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]+", "_").replaceAll("^_+|_+$", "");
        if (id.isBlank()) id = item.getType().name().toLowerCase(Locale.ROOT);
        return id;
    }

    private String uniqueId(String base, Set<String> used) {
        String id = base;
        int index = 2;
        while (used.contains(id)) id = base + "_" + index++;
        return id;
    }

    private List<String> currentIds() {
        List<String> ids = new ArrayList<>();
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("shop.general.items");
        if (section != null) ids.addAll(section.getKeys(false));
        return ids;
    }

    private List<String> visibleIds(Player player) {
        String category = activeCategory(player);
        String search = activeSearch(player).toLowerCase(Locale.ROOT);
        List<String> ids = new ArrayList<>(currentIds());
        if (!category.equals("all")) {
            ids.removeIf(id -> !categoryOf(id).equals(category));
        }
        if (!search.isBlank()) {
            ids.removeIf(id -> {
                String base = "shop.general.items." + id + ".";
                String haystack = (id + " " + plugin.getConfig().getString(base + "name", "") + " "
                        + plugin.getConfig().getString(base + "material", "")).toLowerCase(Locale.ROOT);
                return !haystack.contains(search);
            });
        }
        return ids;
    }

    private String activeCategory(Player player) {
        return categoryFilter.getOrDefault(player.getUniqueId(), "all");
    }

    private String activeSearch(Player player) {
        return searchFilter.getOrDefault(player.getUniqueId(), "");
    }

    private String categoryOf(String id) {
        String material = plugin.getConfig().getString("shop.general.items." + id + ".material", "STONE").toLowerCase(Locale.ROOT);
        var categories = plugin.getConfig().getConfigurationSection("shop.categories");
        if (categories != null) {
            for (String category : categories.getKeys(false)) {
                if (plugin.getConfig().getStringList("shop.categories." + category + ".items").contains(id)) {
                    return category;
                }
            }
        }
        if (plugin.getConfig().getStringList("shop.categories.admin.items").contains(id)) return "admin";
        if (id.toLowerCase(Locale.ROOT).contains("key") || material.contains("key") || material.contains("crystal")) return "keys";
        if (material.contains("sword") || material.contains("axe") || material.contains("bow") || material.contains("tnt")) return "combat";
        if (material.contains("bread") || material.contains("apple") || material.contains("beef") || material.contains("pork") || material.contains("potato") || material.contains("carrot")) return "food";
        if (id.toLowerCase(Locale.ROOT).contains("custom") || id.toLowerCase(Locale.ROOT).contains("mystic")) return "custom";
        Material mat = Material.matchMaterial(material.toUpperCase(Locale.ROOT));
        return mat != null && mat.isBlock() ? "blocks" : "custom";
    }

    private ItemStack categoryButton(String id, String name, Material icon, String... lore) {
        ItemStack item = new ItemStack(icon);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        List<String> lines = new ArrayList<>(List.of(lore));
        lines.add(" ");
        lines.add("§8Kategorie-ID: " + id);
        meta.setLore(lines);
        meta.getPersistentDataContainer().set(controlKey, PersistentDataType.STRING, "cat:" + id);
        item.setItemMeta(meta);
        return item;
    }

    private String categoryLabel(String id) {
        if (id == null || id.isBlank() || id.equals("all")) return "Alle Items";
        return ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("shop.categories." + id + ".name", id));
    }

    private void bulkAdjust(Player player, double pct) {
        List<String> ids = visibleIds(player);
        for (String id : ids) {
            double buy = plugin.getConfig().getDouble("shop.prices." + id + ".buy", 10.0);
            double sell = plugin.getConfig().getDouble("shop.prices." + id + ".sell", 5.0);
            double nextBuy = Math.max(1.0, buy + buy * pct);
            double nextSell = Math.max(1.0, sell + sell * pct);
            plugin.getConfig().set("shop.prices." + id + ".buy", nextBuy);
            plugin.getConfig().set("shop.prices." + id + ".sell", nextSell);
            try {
                if (plugin.getAiCommerceManager() != null) {
                    plugin.getAiCommerceManager().applyPriceProposals(java.util.Map.of(id, nextBuy), false);
                    plugin.getAiCommerceManager().applyPriceProposals(java.util.Map.of(id, nextSell), true);
                }
            } catch (Throwable ignored) {}
        }
        plugin.saveConfig();
        player.sendMessage(ChatColor.GOLD + "AdminShop §8» §7Bulk angepasst: §f" + ids.size() + " Items");
    }

    private void bulkFlatAdjust(Player player, double delta) {
        List<String> ids = visibleIds(player);
        for (String id : ids) {
            double buy = plugin.getConfig().getDouble("shop.prices." + id + ".buy", 10.0);
            double sell = plugin.getConfig().getDouble("shop.prices." + id + ".sell", 5.0);
            double nextBuy = Math.max(1.0, buy + delta);
            double nextSell = Math.max(1.0, sell + delta);
            plugin.getConfig().set("shop.prices." + id + ".buy", nextBuy);
            plugin.getConfig().set("shop.prices." + id + ".sell", nextSell);
            try {
                if (plugin.getAiCommerceManager() != null) {
                    plugin.getAiCommerceManager().applyPriceProposals(java.util.Map.of(id, nextBuy), false);
                    plugin.getAiCommerceManager().applyPriceProposals(java.util.Map.of(id, nextSell), true);
                }
            } catch (Throwable ignored) {}
        }
        plugin.saveConfig();
        player.sendMessage(ChatColor.GOLD + "AdminShop §8» §7Bulk Coins angepasst: §f" + ids.size() + " Items");
    }

    private List<String> mergeCategoryAdmin(List<String> allIds, List<String> oldVisible, List<String> mergedVisible) {
        List<String> out = new ArrayList<>();
        for (String id : allIds) {
            if (!oldVisible.contains(id) && !out.contains(id)) out.add(id);
        }
        for (String id : mergedVisible) {
            if (!out.contains(id)) out.add(id);
        }
        return out;
    }

    private int pageFromTitle(String title) {
        int marker = title.lastIndexOf("S.");
        if (marker < 0) return 0;
        return Math.max(0, parseInt(title.substring(marker + 2).replaceAll("[^0-9]", ""), 1) - 1);
    }

    private int parseInt(String raw, int fallback) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private double parseDouble(String raw, double fallback) {
        try {
            return Double.parseDouble(raw);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private String displayName(ItemStack item, String fallback) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return meta.getDisplayName();
        return "&f" + fallback;
    }

    private double defaultBuy(ItemStack item) {
        return Math.max(1.0, plugin.getConfig().getDouble("shop.defaults.buy", item.getType().isBlock() ? 16.0 : 64.0));
    }

    private String money(double value) {
        return String.format(Locale.US, "%.0f", value);
    }
}
