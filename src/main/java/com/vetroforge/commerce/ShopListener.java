// src/main/java/com/vetroforge/commerce/ShopListener.java
package com.vetroforge.commerce;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.events.progress.impl.CactusSoldProgress;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public class ShopListener implements Listener {

    private final FactionsPlugin plugin;
    private final NamespacedKey KEY_TAG;

    public ShopListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.KEY_TAG = new NamespacedKey(plugin, "shop_tag"); // muss zu ShopGui.KEY_TAG(...) passen
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player)) return;
        Player p = (Player) e.getWhoClicked();

        String title = e.getView().getTitle();
        if (title == null) return;

        boolean inMain = ShopGui.titleMain().equals(title);
        boolean inBuy  = title.startsWith(ShopGui.titleBuy());
        boolean inSell = title.startsWith(ShopGui.titleSell());
        boolean inCategories = title.startsWith(ShopGui.titleCategories());
        if (!inMain && !inBuy && !inSell && !inCategories) return;

        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || it.getType() == Material.AIR) return;

        // --- Navigation aus dem Hauptmenü ---
        if (inMain) {
            String nav = getTag(it);
            if ("nav:categories:buy".equals(nav)) { ShopGui.openCategories(plugin, p, false); return; }
            if ("nav:categories:sell".equals(nav)) { ShopGui.openCategories(plugin, p, true); return; }
            if (nav != null && nav.startsWith("nav:open:buy"))  { ShopGui.openBuy(plugin, p);  return; }
            if (nav != null && nav.startsWith("nav:open:sell")) { ShopGui.openSell(plugin, p); return; }
            if (nav != null && nav.startsWith("nav:category:buy:")) {
                ShopGui.openBuy(plugin, p, 1, nav.substring("nav:category:buy:".length()));
                return;
            }
            if (nav != null && nav.startsWith("nav:category:sell:")) {
                ShopGui.openSell(plugin, p, 1, nav.substring("nav:category:sell:".length()));
                return;
            }
            switch (it.getType()) {
                case CHEST:  ShopGui.openBuy(plugin, p);  return;
                case BARREL: ShopGui.openSell(plugin, p); return;
                default: return;
            }
        }

        // ID ermitteln: PDC-Tag oder Lore "ID: <id>"
        String tag = getTag(it);
        if ("nav:main".equals(tag)) {
            ShopGui.openMain(plugin, p);
            return;
        }
        if ("nav:close".equals(tag)) {
            p.closeInventory();
            return;
        }
        if ("nav:categories:buy".equals(tag)) {
            ShopGui.openCategories(plugin, p, false);
            return;
        }
        if ("nav:categories:sell".equals(tag)) {
            ShopGui.openCategories(plugin, p, true);
            return;
        }
        if (inCategories && tag != null && tag.startsWith("nav:category:buy:")) {
            ShopGui.openBuy(plugin, p, 1, tag.substring("nav:category:buy:".length()));
            return;
        }
        if (inCategories && tag != null && tag.startsWith("nav:category:sell:")) {
            ShopGui.openSell(plugin, p, 1, tag.substring("nav:category:sell:".length()));
            return;
        }
        if (inSell && tag != null && tag.startsWith("nav:sell:")) {
            switch (tag) {
                case "nav:sell:cactus" -> sellMaterial(p, "cactus", Material.CACTUS, true);
                case "nav:sell:hand" -> sellHand(p);
                case "nav:sell:inventory" -> sellInventory(p);
                default -> {}
            }
            ShopGui.openSell(plugin, p);
            return;
        }
        if (tag != null && tag.startsWith("nav:page:")) {
            String[] parts = tag.split(":");
            if (parts.length >= 4) {
                int page;
                try { page = Integer.parseInt(parts[3]); } catch (NumberFormatException ignored) { return; }
                String category = parts.length >= 5 ? parts[4] : null;
                if ("all".equalsIgnoreCase(category)) category = null;
                if ("buy".equals(parts[2])) ShopGui.openBuy(plugin, p, page, category);
                else if ("sell".equals(parts[2])) ShopGui.openSell(plugin, p, page, category);
            }
            return;
        }
        String id = (tag != null && (tag.startsWith("buy:") || tag.startsWith("sell:")))
                ? tag.substring(tag.indexOf(':') + 1)
                : extractIdFromLore(it);
        if (id == null || id.isBlank()) return;

        AICommerceManager ai = plugin.getAiCommerceManager();
        AICommerceManager.Entry entry = ai != null ? ai.get(id) : null;

        // ===== KAUFEN =====
        if (inBuy) {
            double priceEach = (entry != null)
                    ? dynBuyOf(entry, plugin.getConfig().getDouble("shop.prices."+id+".buy", 10d))
                    : plugin.getConfig().getDouble("shop.prices."+id+".buy", 10d);

            ItemStack base = materialFromConfig(id);

            if (base.getType() == Material.AIR) base.setType(Material.STONE);

            int baseAmount = Math.max(1, base.getAmount());
            int amount = switch (e.getClick()) {
                case RIGHT -> 64;
                case SHIFT_LEFT, SHIFT_RIGHT -> 64 * 5;
                default -> baseAmount;
            };

            int total = coins(priceEach * amount);

            if (plugin.getCoinsManager() == null || plugin.getCoinsManager().getCoins(p.getUniqueId()) < total) {
                p.sendMessage(ChatColor.RED + "Nicht genug Coins.");
                return;
            }
            plugin.getCoinsManager().removeCoins(p.getUniqueId(), total);

            ItemStack give = base.clone();
            int maxStack = Math.max(1, give.getMaxStackSize());
            give.setAmount(Math.min(maxStack, amount));
            int remaining = amount;
            while (remaining > 0) {
                ItemStack stack = give.clone();
                stack.setAmount(Math.min(maxStack, remaining));
                p.getInventory().addItem(stack).values().forEach(left ->
                        p.getWorld().dropItemNaturally(p.getLocation(), left));
                remaining -= stack.getAmount();
            }

            // >>> EINMALIGER Impuls für die Dynamik (Menge berücksichtigt)
            try { if (ai != null) ai.buy(id, amount); } catch (Throwable ignored) {}
            try { if (ai != null) ai.saveRuntime(); } catch (Throwable ignored) {}
            // Logging (mit Menge, falls vorhanden)
            if (plugin.getGeneralShopManager() != null) plugin.getGeneralShopManager().logBuy(id, amount);

            p.sendMessage(ChatColor.GREEN + "Gekauft: " + ChatColor.WHITE + amount + "x " + give.getType().name()
                    + ChatColor.GRAY + " für " + ChatColor.GOLD + total + " Coins");
            ShopGui.openBuy(plugin, p);
            return;
        }

        // ===== VERKAUFEN =====
        if (inSell) {
            double priceEach = (entry != null)
                    ? dynSellOf(entry, plugin.getConfig().getDouble("shop.prices."+id+".sell", 1d))
                    : plugin.getConfig().getDouble("shop.prices."+id+".sell", 1d);

            Material mat = materialFromConfig(id).getType();
            if (mat == null || mat == Material.AIR) mat = Material.STONE;

            int sold = (e.getClick() == ClickType.RIGHT || e.getClick() == ClickType.SHIFT_RIGHT)
                    ? removeAllOf(p.getInventory(), mat)
                    : removeOneStack(p.getInventory(), mat);

            if (sold <= 0) { p.sendMessage(ChatColor.RED + "Du hast davon nichts im Inventar."); return; }

            int payout = payoutSell(p, id, mat, priceEach, sold);

            p.sendMessage(ChatColor.GREEN + "Verkauft: " + ChatColor.WHITE + sold + "x " + mat.name()
                    + ChatColor.GRAY + " für " + ChatColor.GOLD + payout + " Coins");

            // Refresh
            ShopGui.openSell(plugin, p);
        }
    }

    /* ===== Helpers ===== */

    private void sellHand(Player player) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            player.sendMessage(ChatColor.RED + "Du hast kein Item in der Hand.");
            return;
        }
        String id = idForMaterial(hand.getType());
        if (id == null) {
            player.sendMessage(ChatColor.RED + "Dieses Item kauft der Shop nicht an.");
            return;
        }
        int amount = hand.getAmount();
        double price = sellPrice(id);
        player.getInventory().setItemInMainHand(null);
        int payout = payoutSell(player, id, hand.getType(), price, amount);
        player.sendMessage(ChatColor.GREEN + "Hand verkauft: " + ChatColor.WHITE + amount + "x " + hand.getType().name()
                + ChatColor.GRAY + " für " + ChatColor.GOLD + payout + " Coins");
    }

    private void sellInventory(Player player) {
        AICommerceManager ai = plugin.getAiCommerceManager();
        if (ai == null || ai.all().isEmpty()) {
            player.sendMessage(ChatColor.RED + "Der Shop hat aktuell keine Ankauf-Liste.");
            return;
        }
        int totalSold = 0;
        int totalCoins = 0;
        Set<Material> handled = new HashSet<>();
        for (AICommerceManager.Entry entry : ai.all()) {
            Material mat = materialFromConfig(entry.id).getType();
            if (mat == Material.AIR || !handled.add(mat)) continue;
            int sold = removeAllOf(player.getInventory(), mat);
            if (sold <= 0) continue;
            totalSold += sold;
            totalCoins += payoutSell(player, entry.id, mat, sellPrice(entry.id), sold);
        }
        if (totalSold <= 0) {
            player.sendMessage(ChatColor.RED + "Keine verkaufbaren Items im Inventar gefunden.");
            return;
        }
        player.sendMessage(ChatColor.GREEN + "Inventar verkauft: " + ChatColor.WHITE + totalSold
                + " Items" + ChatColor.GRAY + " für " + ChatColor.GOLD + totalCoins + " Coins");
    }

    private void sellMaterial(Player player, String preferredId, Material material, boolean all) {
        String id = idForMaterial(material);
        if (id == null) id = preferredId;
        int sold = all ? removeAllOf(player.getInventory(), material) : removeOneStack(player.getInventory(), material);
        if (sold <= 0) {
            player.sendMessage(ChatColor.RED + "Du hast davon nichts im Inventar.");
            return;
        }
        int payout = payoutSell(player, id, material, sellPrice(id), sold);
        player.sendMessage(ChatColor.GREEN + "Verkauft: " + ChatColor.WHITE + sold + "x " + material.name()
                + ChatColor.GRAY + " für " + ChatColor.GOLD + payout + " Coins");
    }

    private int payoutSell(Player player, String id, Material mat, double priceEach, int sold) {
        int payout = coins(priceEach * sold);
        if (plugin.getCoinsManager() != null) plugin.getCoinsManager().addCoins(player.getUniqueId(), payout);

        AICommerceManager ai = plugin.getAiCommerceManager();
        try { if (ai != null) ai.sell(id, sold); } catch (Throwable ignored) {}
        try { if (ai != null) ai.saveRuntime(); } catch (Throwable ignored) {}
        if (plugin.getGeneralShopManager() != null) plugin.getGeneralShopManager().logSell(id, sold);
        if (mat == Material.CACTUS || id.equalsIgnoreCase("cactus") || id.equalsIgnoreCase("kaktus")) {
            CactusSoldProgress.record(sold);
            try {
                if (plugin.getEventManager() != null) {
                    plugin.getEventManager().addProgressIfActive("cactus_grind", sold);
                }
            } catch (Throwable ignored) {}
        }
        return payout;
    }

    private double sellPrice(String id) {
        AICommerceManager ai = plugin.getAiCommerceManager();
        AICommerceManager.Entry entry = ai == null ? null : ai.get(id);
        double fallback = plugin.getConfig().getDouble("shop.prices." + id + ".sell", 1d);
        return entry == null ? fallback : dynSellOf(entry, fallback);
    }

    private String idForMaterial(Material material) {
        if (material == null || material == Material.AIR) return null;
        AICommerceManager ai = plugin.getAiCommerceManager();
        if (ai != null) {
            for (AICommerceManager.Entry entry : ai.all()) {
                if (materialFromConfig(entry.id).getType() == material) return entry.id;
            }
        }
        var section = plugin.getConfig().getConfigurationSection("shop.general.items");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (materialFromConfig(id).getType() == material) return id;
            }
        }
        return null;
    }

    private ItemStack materialFromConfig(String id) {
        String base = "shop.general.items."+id+".";
        String matName = plugin.getConfig().getString(base+"material","STONE");
        Material mat = Material.matchMaterial(matName);
        if (mat == null) {
            try { mat = Material.valueOf(matName.toUpperCase(Locale.ROOT)); } catch (Exception ignored) {}
        }
        if (mat == null) mat = Material.STONE;
        int amount = Math.max(1, plugin.getConfig().getInt(base+"amount", 1));
        return new ItemStack(mat, amount);
    }

    private int coins(double value) {
        return Math.max(1, (int) Math.ceil(value));
    }

    private String getTag(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m == null) return null;
        PersistentDataContainer pdc = m.getPersistentDataContainer();
        return pdc.get(KEY_TAG, PersistentDataType.STRING);
    }

    private String extractIdFromLore(ItemStack it) {
        ItemMeta m = it.getItemMeta();
        if (m == null || m.getLore() == null) return null;
        for (String line : m.getLore()) {
            String plain = ChatColor.stripColor(line);
            if (plain != null && plain.startsWith("ID: ")) return plain.substring(4).trim();
        }
        return null;
    }

    private int removeOneStack(PlayerInventory inv, Material mat) {
        ItemStack[] cont = inv.getContents();
        for (int i = 0; i < cont.length; i++) {
            ItemStack s = cont[i];
            if (s != null && s.getType() == mat) {
                int amt = s.getAmount();
                cont[i] = null;
                inv.setContents(cont);
                return amt;
            }
        }
        return 0;
    }

    private int removeAllOf(PlayerInventory inv, Material mat) {
        int total = 0;
        ItemStack[] cont = inv.getContents();
        for (int i = 0; i < cont.length; i++) {
            ItemStack s = cont[i];
            if (s != null && s.getType() == mat) {
                total += s.getAmount();
                cont[i] = null;
            }
        }
        inv.setContents(cont);
        return total;
    }

    private double dynBuyOf(AICommerceManager.Entry entry, double fallback){
        return entry.dynBuy > 0 ? entry.dynBuy : fallback;
    }

    private double dynSellOf(AICommerceManager.Entry entry, double fallback){
        return entry.dynSell > 0 ? entry.dynSell : fallback;
    }
}
