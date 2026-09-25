// src/main/java/com/vetroforge/commerce/ShopGui.java
package com.vetroforge.commerce;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class ShopGui {

    /* ---------- Titel ---------- */
    private static final String TITLE_MAIN = "§8✦ §b§lVetroForge §8• §fServer-Shop";
    private static final String TITLE_BUY  = "§8✦ §a§lShop §8• §fKaufen";
    private static final String TITLE_SELL = "§8✦ §c§lShop §8• §fVerkaufen";
    private static final String TITLE_CATEGORIES = "§8✦ §b§lShop §8• §fKategorien";

    /* ---------- PDC-Key (einheitlich mit Listener) ---------- */
    public static NamespacedKey PDC_TAG(FactionsPlugin p){ return new NamespacedKey(p, "shop_tag"); }

    /* ---------- Layout ---------- */
    private static final int[] CONTENT_SLOTS = {
            10,11,12,13,14,15,16,
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    /* ---------- Öffnen ---------- */

    public static void openMain(FactionsPlugin plugin, Player p) {
        Inventory inv = Bukkit.createInventory(null, 45, TITLE_MAIN);
        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE, " "));

        inv.setItem(4, info("§b§lVetroForge §8• §fServer-Shop",
                List.of("§7Kaufen, verkaufen und Cactus-Events",
                        "§7laufen hier über denselben Economy-Flow.",
                        "",
                        "§8Tipp: §f/shop debug cactus §7prüft SellShop + Event.")));

        inv.setItem(20, tagged(plugin, new ItemStack(Material.CHEST),
                "§a§lItems kaufen", List.of(
                        "§7Wähle zuerst eine Kategorie.",
                        "§7Danach siehst du alle Kaufpreise.",
                        "",
                        "§eKlick §8» §fKategorien öffnen"), "nav:categories:buy"));
        inv.setItem(24, tagged(plugin, new ItemStack(Material.HOPPER),
                "§c§lItems verkaufen", List.of(
                        "§7Sell Hand, Sell Inventory und",
                        "§7Kategorie-Verkauf sauber getrennt.",
                        "",
                        "§eKlick §8» §fSellShop-Kategorien"), "nav:categories:sell"));

        addCategoryPreview(plugin, inv);
        p.openInventory(inv);
    }

    public static void openBuy(FactionsPlugin plugin, Player p) { openBuy(plugin, p, 1, null); }
    public static void openSell(FactionsPlugin plugin, Player p) { openSell(plugin, p, 1, null); }

    public static void openCategories(FactionsPlugin plugin, Player p, boolean sell) {
        Inventory inv = Bukkit.createInventory(null, 54, TITLE_CATEGORIES + (sell ? " §8• §cVerkaufen" : " §8• §aKaufen"));
        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE, " "));

        inv.setItem(4, info((sell ? "§c§lSellShop" : "§a§lServer-Shop") + " §8• §fKategorien",
                List.of("§7Wähle eine Kategorie als eigene Seite.",
                        sell ? "§7Danach kannst du Items gezielt verkaufen." : "§7Danach kannst du Items gezielt kaufen.",
                        "",
                        "§8Modus: " + (sell ? "§cVerkaufen" : "§aKaufen"))));

        inv.setItem(10, categoryCard(plugin, Material.CHEST, "§f§lAlle Items", "all", sell,
                "§7Zeigt alle Shop-Items ohne Filter."));

        var section = plugin.getConfig().getConfigurationSection("shop.categories");
        int[] slots = {
                12, 13, 14, 15, 16,
                21, 22, 23, 24, 25,
                30, 31, 32, 33, 34,
                39, 40, 41, 42, 43
        };
        int index = 0;
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (index >= slots.length) break;
                Material icon = Material.matchMaterial(plugin.getConfig().getString("shop.categories." + id + ".icon", sell ? "HOPPER" : "CHEST"));
                if (icon == null || icon == Material.AIR) icon = sell ? Material.HOPPER : Material.CHEST;
                String rawName = ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("shop.categories." + id + ".name", humanize(id)));
                int itemCount = plugin.getConfig().getStringList("shop.categories." + id + ".items").size();
                inv.setItem(slots[index++], categoryCard(plugin, icon, "§f§l" + ChatColor.stripColor(rawName), id, sell,
                        "§7Items: §f" + itemCount,
                        sell ? "§7Öffnet die Verkaufsliste." : "§7Öffnet die Kaufliste."));
            }
        }

        inv.setItem(45, tagged(plugin, new ItemStack(Material.ARROW), ChatColor.YELLOW + "Zurück",
                List.of(ChatColor.GRAY + "Zum Shop-Hauptmenü"), "nav:main"));
        inv.setItem(53, tagged(plugin, new ItemStack(Material.BARRIER), ChatColor.RED + "Schließen",
                List.of(ChatColor.GRAY + "Schließt das Menü"), "nav:close"));
        p.openInventory(inv);
    }

    public static void openBuy(FactionsPlugin plugin, Player p, int page) {
        openBuy(plugin, p, page, null);
    }

    public static void openBuy(FactionsPlugin plugin, Player p, int page, String category) {
        page = Math.max(1, page);
        AICommerceManager ai = plugin.getAiCommerceManager();

        List<AICommerceManager.Entry> entries = filteredEntries(plugin, ai, category);
        entries.sort(Comparator.comparing(e -> e.id));

        int perPage = CONTENT_SLOTS.length;
        int maxPage = Math.max(1, (int)Math.ceil(entries.size() / (double) perPage));
        page = Math.min(page, maxPage);

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_BUY + titleCategorySuffix(plugin, category) + " §8(" + page + "/" + maxPage + ")");
        decorateGrid(inv, plugin, true, page, maxPage, category);

        int from = (page - 1) * perPage;
        int to   = Math.min(from + perPage, entries.size());
        int slotIdx = 0;
        for (int i = from; i < to; i++) {
            AICommerceManager.Entry e = entries.get(i);
            ItemStack it = displayItem(plugin, e.id, ChatColor.GRAY + "Preis: " + ChatColor.GOLD + money(e.dynBuy) + " Coins",
                    List.of(
                            priceAnchor("Vorher", e.baseBuy, e.dynBuy),
                            ChatColor.GRAY + "Ø " + avgDailyBuys(plugin, e.id) + " Käufe/Tag",
                            ChatColor.GRAY + "Kategorie: " + ChatColor.WHITE + categoryName(plugin, e.id),
                            ChatColor.GRAY + "Links: 1x  | Rechts: 1 Stack | Shift: 5 Stacks"
                    ));
            inv.setItem(CONTENT_SLOTS[slotIdx++], tag(plugin, it, "buy:"+e.id));
        }

        p.openInventory(inv);
    }

    public static void openSell(FactionsPlugin plugin, Player p, int page) {
        openSell(plugin, p, page, null);
    }

    public static void openSell(FactionsPlugin plugin, Player p, int page, String category) {
        page = Math.max(1, page);
        AICommerceManager ai = plugin.getAiCommerceManager();

        List<AICommerceManager.Entry> entries = filteredEntries(plugin, ai, category);
        entries.sort(Comparator.comparing(e -> e.id));

        int perPage = CONTENT_SLOTS.length;
        int maxPage = Math.max(1, (int)Math.ceil(entries.size() / (double) perPage));
        page = Math.min(page, maxPage);

        Inventory inv = Bukkit.createInventory(null, 54, TITLE_SELL + titleCategorySuffix(plugin, category) + " §8(" + page + "/" + maxPage + ")");
        decorateGrid(inv, plugin, false, page, maxPage, category);

        int from = (page - 1) * perPage;
        int to   = Math.min(from + perPage, entries.size());
        int slotIdx = 0;
        for (int i = from; i < to; i++) {
            AICommerceManager.Entry e = entries.get(i);
            ItemStack it = displayItem(plugin, e.id, ChatColor.GRAY + "Auszahlung: " + ChatColor.GOLD + money(e.dynSell) + " Coins",
                    List.of(
                            priceAnchor("Vorher", e.baseSell, e.dynSell),
                            ChatColor.GRAY + "Ø " + avgDailySells(plugin, e.id) + " Verkäufe/Tag",
                            ChatColor.GRAY + "Kategorie: " + ChatColor.WHITE + categoryName(plugin, e.id),
                            ChatColor.GRAY + "Links: 1 Stack  | Rechts: Alle"
                    ));
            inv.setItem(CONTENT_SLOTS[slotIdx++], tag(plugin, it, "sell:"+e.id));
        }

        p.openInventory(inv);
    }

    /* ---------- Dekor ---------- */

    private static void decorateGrid(Inventory inv, FactionsPlugin plugin, boolean buy, int page, int maxPage, String category) {
        // Rahmen
        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE, " "));

        // Fensterslots freigeben
        for (int s : CONTENT_SLOTS) inv.setItem(s, null);

        // Kopfzeile Info + Balance + Zurück
        inv.setItem(0, tagged(plugin, new ItemStack(Material.ARROW), ChatColor.YELLOW+"Zurück",
                List.of(ChatColor.GRAY+"Zum Hauptmenü"), "nav:main"));
        if (buy) {
            inv.setItem(4, info(ChatColor.GOLD + "Server-Shop",
                    List.of(ChatColor.GRAY + "Kategorie: " + ChatColor.WHITE + displayCategory(plugin, category),
                            ChatColor.GRAY + "Wird beim Kauf live geprüft")));
            inv.setItem(7, tagged(plugin, new ItemStack(Material.COMPASS), ChatColor.AQUA + "Kategorie wechseln",
                    List.of(ChatColor.GRAY + "Zur großen Kategorieauswahl"), "nav:categories:buy"));
        } else {
            inv.setItem(2, tagged(plugin, new ItemStack(Material.CACTUS), ChatColor.GREEN + "Cactus verkaufen",
                    List.of(ChatColor.GRAY + "Verkauft alle Cactus im Inventar",
                            ChatColor.DARK_GRAY + "Perfekt für Cactus-Events"), "nav:sell:cactus"));
            inv.setItem(4, tagged(plugin, new ItemStack(Material.CHEST_MINECART), ChatColor.GREEN + "Inventar verkaufen",
                    List.of(ChatColor.GRAY + "Verkauft alle Items, die der Shop ankauft",
                            ChatColor.GRAY + "Unverkaufbares bleibt im Inventar"), "nav:sell:inventory"));
            inv.setItem(6, tagged(plugin, new ItemStack(Material.HOPPER), ChatColor.GREEN + "Hand verkaufen",
                    List.of(ChatColor.GRAY + "Verkauft nur das Item in deiner Hand"), "nav:sell:hand"));
            inv.setItem(7, tagged(plugin, new ItemStack(Material.COMPASS), ChatColor.AQUA + "Kategorie wechseln",
                    List.of(ChatColor.GRAY + "Zur großen SellShop-Kategorieauswahl"), "nav:categories:sell"));
        }
        inv.setItem(8, tagged(plugin, new ItemStack(Material.BARRIER), ChatColor.RED+"Schließen",
                List.of(ChatColor.GRAY+"Schließt das Menü"), "nav:close"));

        // Seiten-Navi
        int previousSlot = buy ? 18 : 36;
        int nextSlot = buy ? 26 : 44;
        if (page > 1) {
            inv.setItem(previousSlot, tagged(plugin, new ItemStack(Material.PAPER), ChatColor.YELLOW+"← Zurück",
                    List.of(ChatColor.GRAY+"Seite " + (page-1)), "nav:page:"+(buy?"buy":"sell")+":"+(page-1)+":" + encodeCategory(category)));
        }
        if (page < maxPage) {
            inv.setItem(nextSlot, tagged(plugin, new ItemStack(Material.PAPER), ChatColor.YELLOW+"Weiter →",
                    List.of(ChatColor.GRAY+"Seite " + (page+1)), "nav:page:"+(buy?"buy":"sell")+":"+(page+1)+":" + encodeCategory(category)));
        }
    }

    private static ItemStack displayItem(FactionsPlugin plugin, String id, String line1, List<String> extraLore) {
        ItemStack it = stackFor(plugin, id);
        ItemMeta m = it.getItemMeta();
        List<String> lore = new ArrayList<>();
        lore.add(line1);
        if (extraLore != null) lore.addAll(extraLore);
        m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    /* ---------- Helpers ---------- */

    private static ItemStack stackFor(FactionsPlugin plugin, String id) {
        String base = "shop.general.items." + id + ".";
        String matName = plugin.getConfig().getString(base + "material", "STONE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            try { mat = Material.valueOf(matName.toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}
        }
        if (mat == null || mat == Material.AIR) mat = Material.STONE;

        int amount = Math.max(1, plugin.getConfig().getInt(base + "amount", 1));
        String name = ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString(base + "name", "&f" + humanize(mat.name())));

        ItemStack it = new ItemStack(mat, amount);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        it.setItemMeta(meta);
        return it;
    }

    /** MATERIAL_NAME -> "Material Name", for shop items without a configured display name. */
    private static String humanize(String materialName) {
        String[] words = materialName.toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private static ItemStack info(String name, List<String> lore) {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta m = it.getItemMeta();
        m.setDisplayName(name);
        if (lore != null) m.setLore(lore);
        it.setItemMeta(m);
        return it;
    }

    private static ItemStack pane(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }

    private static void fill(Inventory inv, ItemStack f) { for (int i=0;i<inv.getSize();i++) if (inv.getItem(i)==null) inv.setItem(i,f); }

    private static void addCategoryPreview(FactionsPlugin plugin, Inventory inv) {
        var section = plugin.getConfig().getConfigurationSection("shop.categories");
        if (section == null) return;
        int[] slots = {29, 30, 31, 32, 33};
        int index = 0;
        for (String id : section.getKeys(false)) {
            if (index >= slots.length) break;
            Material icon = Material.matchMaterial(plugin.getConfig().getString("shop.categories." + id + ".icon", "CHEST"));
            if (icon == null || icon == Material.AIR) icon = Material.CHEST;
            String name = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("shop.categories." + id + ".name", humanize(id)));
            int itemCount = plugin.getConfig().getStringList("shop.categories." + id + ".items").size();
            inv.setItem(slots[index++], tagged(plugin, new ItemStack(icon),
                    "§f§l" + ChatColor.stripColor(name),
                    List.of("§7Items: §f" + itemCount,
                            "",
                            "§eKlick §8» §fKategorie öffnen"), "nav:category:buy:" + id));
        }
    }

    private static ItemStack categoryCard(FactionsPlugin plugin, Material icon, String name, String category, boolean sell, String... details) {
        List<String> lore = new ArrayList<>();
        if (details != null) lore.addAll(List.of(details));
        lore.add("");
        lore.add("§eKlick §8» §f" + (sell ? "Verkaufsliste öffnen" : "Kaufliste öffnen"));
        return tagged(plugin, new ItemStack(icon), name, lore, "nav:category:" + (sell ? "sell:" : "buy:") + category);
    }

    private static void addSellCategoryBar(FactionsPlugin plugin, Inventory inv) {
        var section = plugin.getConfig().getConfigurationSection("shop.categories");
        if (section == null) return;
        int[] slots = {45, 46, 47, 48, 49, 50};
        int index = 0;
        inv.setItem(slots[index++], tagged(plugin, new ItemStack(Material.CHEST),
                "§f§lAlle", List.of("§7Zeigt alle ankaufbaren Items.", "", "§eKlick §8» §fÖffnen"), "nav:category:sell:all"));
        for (String id : section.getKeys(false)) {
            if (index >= slots.length) break;
            Material icon = Material.matchMaterial(plugin.getConfig().getString("shop.categories." + id + ".icon", "HOPPER"));
            if (icon == null || icon == Material.AIR) icon = Material.HOPPER;
            String name = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("shop.categories." + id + ".name", humanize(id)));
            int itemCount = plugin.getConfig().getStringList("shop.categories." + id + ".items").size();
            inv.setItem(slots[index++], tagged(plugin, new ItemStack(icon),
                    "§f§l" + ChatColor.stripColor(name),
                    List.of("§7SellShop-Kategorie",
                            "§7Items: §f" + itemCount,
                            "",
                            "§eKlick §8» §fVerkaufsliste öffnen"), "nav:category:sell:" + id));
        }
    }

    private static List<AICommerceManager.Entry> filteredEntries(FactionsPlugin plugin, AICommerceManager ai, String category) {
        List<AICommerceManager.Entry> entries = new ArrayList<>(ai.all());
        if (category == null || category.isBlank() || category.equalsIgnoreCase("all")) return entries;
        List<String> ids = plugin.getConfig().getStringList("shop.categories." + category + ".items");
        if (ids.isEmpty()) return entries;
        entries.removeIf(entry -> !ids.contains(entry.id));
        return entries;
    }

    private static String titleCategorySuffix(FactionsPlugin plugin, String category) {
        if (category == null || category.isBlank() || category.equalsIgnoreCase("all")) return "";
        return " §8• §f" + displayCategory(plugin, category);
    }

    private static String displayCategory(FactionsPlugin plugin, String category) {
        if (category == null || category.isBlank() || category.equalsIgnoreCase("all")) return "Alle";
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString("shop.categories." + category + ".name", category)));
    }

    private static String encodeCategory(String category) {
        return category == null || category.isBlank() ? "all" : category;
    }

    private static String money(double v) { return String.format(Locale.US, "%,.0f", v); }

    private static String priceAnchor(String label, double base, double current) {
        if (Math.round(base) == Math.round(current)) {
            return ChatColor.GRAY + label + ": " + ChatColor.DARK_GRAY + money(base) + " Coins";
        }
        ChatColor color = current <= base ? ChatColor.GREEN : ChatColor.RED;
        return ChatColor.GRAY + label + ": " + ChatColor.DARK_GRAY + ChatColor.STRIKETHROUGH + money(base)
                + ChatColor.GRAY + "  Jetzt: " + color + money(current) + " Coins";
    }

    private static String avgDailyBuys(FactionsPlugin plugin, String id) {
        return String.format(Locale.US, "%.1f", plugin.getConfig().getDouble("shop.stats." + id + ".avgDailyBuys", 1.3D));
    }

    private static String avgDailySells(FactionsPlugin plugin, String id) {
        return String.format(Locale.US, "%.1f", plugin.getConfig().getDouble("shop.stats." + id + ".avgDailySells", 0.8D));
    }

    private static String categoryName(FactionsPlugin plugin, String itemId) {
        var section = plugin.getConfig().getConfigurationSection("shop.categories");
        if (section == null) return "Sonstiges";
        for (String id : section.getKeys(false)) {
            if (plugin.getConfig().getStringList("shop.categories." + id + ".items").contains(itemId)) {
                return ChatColor.translateAlternateColorCodes('&',
                        plugin.getConfig().getString("shop.categories." + id + ".name", humanize(id)));
            }
        }
        return "Sonstiges";
    }

    private static ItemStack tagged(FactionsPlugin plugin, ItemStack base, String name, List<String> lore, String tagVal){
        ItemStack it = base.clone();
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        if (lore != null) meta.setLore(lore);
        meta.getPersistentDataContainer().set(PDC_TAG(plugin), PersistentDataType.STRING, tagVal);
        it.setItemMeta(meta);
        return it;
    }

    private static ItemStack tag(FactionsPlugin plugin, ItemStack it, String tagVal){
        ItemMeta meta = it.getItemMeta();
        meta.getPersistentDataContainer().set(PDC_TAG(plugin), PersistentDataType.STRING, tagVal);
        it.setItemMeta(meta);
        return it;
    }

    /* Exporte der Titel (Listener nutzt startsWith wegen Seitenanzeige) */
    public static String titleMain(){ return TITLE_MAIN; }
    public static String titleBuy(){ return TITLE_BUY; }
    public static String titleSell(){ return TITLE_SELL; }
    public static String titleCategories(){ return TITLE_CATEGORIES; }
}
