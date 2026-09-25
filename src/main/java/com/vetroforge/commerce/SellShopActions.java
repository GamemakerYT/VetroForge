package com.vetroforge.commerce;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.Branding;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

// NOTE (adapted from factions-repair): every public method here originally started with a
// FeatureSwitchCommand.allow(plugin, player, FeatureSwitches.Feature.SHOP_SELL) kill-switch
// check. That feature-flag subsystem (com.vetroforge.features.*) does not exist anywhere in the
// current codebase and is not part of this integration's file list, so the gate calls were
// dropped rather than guessed at - matching the current codebase's status quo of having no such
// gate on selling at all. If that subsystem gets ported later, re-add the guard here.
public final class SellShopActions {
    private SellShopActions() {
    }

    public static Result sellHand(FactionsPlugin plugin, Player player, boolean message) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType() == Material.AIR) {
            fail(player, "Du hast kein Item in der Hand.");
            return Result.empty();
        }
        String id = idForMaterial(plugin, hand.getType());
        if (com.vetroforge.items.ItemSafety.deny(player, hand)) return Result.empty();
        if (id == null) {
            fail(player, "Dieses Item ist nicht im SellShop.");
            return Result.empty();
        }
        int amount = hand.getAmount();
        player.getInventory().setItemInMainHand(null);
        return sellMaterial(plugin, player, id, hand.getType(), amount, message);
    }

    public static Result sellCactus(FactionsPlugin plugin, Player player, boolean message) {
        String id = idForMaterial(plugin, Material.CACTUS);
        int amount = removeAllOf(player.getInventory(), Material.CACTUS);
        return sellMaterial(plugin, player, id == null ? "cactus" : id, Material.CACTUS, amount, message);
    }

    public static Result sellInventory(FactionsPlugin plugin, Player player, boolean message) {
        Map<String, SellBatch> batches = collectSellableInventory(plugin, player);
        if (batches.isEmpty()) {
            fail(player, "Keine verkaufbaren Items im Inventar gefunden.");
            return Result.empty();
        }

        double total = 0.0D;
        int items = 0;
        int cactus = 0;
        boolean eventProgressWritten = false;
        for (SellBatch batch : batches.values()) {
            double payout = payoutFor(plugin, batch.id(), batch.amount());
            total += payout;
            items += batch.amount();
            if (batch.material() == Material.CACTUS) cactus += batch.amount();
            if (applySellSideEffects(plugin, player, batch.id(), batch.material(), batch.amount())) {
                eventProgressWritten = true;
            }
        }
        plugin.getEconomyManager().deposit(player.getUniqueId(), total);
        success(player, 1.25F);
        if (message) {
            player.sendMessage(Branding.PREFIX + "§aInventar verkauft: §f" + items
                    + " Items §8• §6$" + String.format(Locale.US, "%.0f", total));
            player.sendActionBar("§aSell Inventory §8• §f" + items + " Items §8• §6$" + String.format(Locale.US, "%.0f", total));
        }
        return new Result(items, total, cactus, eventProgressWritten);
    }

    public static Result sellMaterial(FactionsPlugin plugin, Player player, String id, Material material, int amount, boolean message) {
        if (amount <= 0) {
            fail(player, "Du hast davon nichts im Inventar.");
            return Result.empty();
        }
        double payout = payoutFor(plugin, id, amount);
        plugin.getEconomyManager().deposit(player.getUniqueId(), payout);
        boolean eventProgressWritten = applySellSideEffects(plugin, player, id, material, amount);
        success(player, 1.15F);
        if (message) {
            player.sendMessage(Branding.PREFIX + "§aVerkauft: §f" + amount + "x " + material.name()
                    + " §8• §6$" + String.format(Locale.US, "%.0f", payout));
            player.sendActionBar("§aVerkauft §8• §f" + amount + "x " + material.name());
        }
        return new Result(amount, payout, material == Material.CACTUS ? amount : 0, eventProgressWritten);
    }

    public static double payoutFor(FactionsPlugin plugin, String id, int amount) {
        AICommerceManager ai = plugin.getAiCommerceManager();
        AICommerceManager.Entry entry = ai != null ? ai.get(id) : null;
        double priceEach = entry != null
                ? Math.max(0.0D, entry.dynSell)
                : plugin.getConfig().getDouble("shop.prices." + id + ".sell", 1.0D);
        return priceEach * Math.max(0, amount);
    }

    public static String idForMaterial(FactionsPlugin plugin, Material material) {
        if (material == null || material == Material.AIR || plugin.getAiCommerceManager() == null) return null;
        for (AICommerceManager.Entry entry : plugin.getAiCommerceManager().all()) {
            if (materialFromConfig(plugin, entry.id).getType() == material) return entry.id;
        }
        return null;
    }

    public static boolean applySellSideEffects(FactionsPlugin plugin, Player player, String id, Material material, int amount) {
        AICommerceManager ai = plugin.getAiCommerceManager();
        try { if (ai != null) ai.sell(id, amount); } catch (Throwable ignored) {}
        try { plugin.getGeneralShopManager().getClass().getMethod("logSell", String.class, int.class).invoke(plugin.getGeneralShopManager(), id, amount); }
        catch (Throwable ignored) { try { plugin.getGeneralShopManager().logSell(id); } catch (Throwable ignored2) {} }
        try {
            if (material == Material.CACTUS && plugin.getEventManager() != null) {
                plugin.getEventManager().addProgressIfActive("cactus_sold", amount);
                return true;
            }
        } catch (Throwable ignored) {}
        return false;
    }

    public static ItemStack materialFromConfig(FactionsPlugin plugin, String id) {
        String base = "shop.general.items." + id + ".";
        String matName = plugin.getConfig().getString(base + "material", "STONE");
        Material material = Material.matchMaterial(matName);
        if (material == null) {
            try { material = Material.valueOf(matName.toUpperCase(Locale.ROOT)); } catch (Throwable ignored) {}
        }
        if (material == null) material = Material.STONE;
        int amount = Math.max(1, plugin.getConfig().getInt(base + "amount", 1));
        return new ItemStack(material, amount);
    }

    private static Map<String, SellBatch> collectSellableInventory(FactionsPlugin plugin, Player player) {
        Map<String, SellBatch> batches = new LinkedHashMap<>();
        ItemStack[] contents = player.getInventory().getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item == null || item.getType() == Material.AIR || com.vetroforge.items.ItemSafety.locked(item)) continue;
            String id = idForMaterial(plugin, item.getType());
            if (id == null) continue;
            int amount = item.getAmount();
            contents[i] = null;
            SellBatch current = batches.get(id);
            batches.put(id, new SellBatch(id, item.getType(), (current == null ? 0 : current.amount()) + amount));
        }
        player.getInventory().setContents(contents);
        return batches;
    }

    private static int removeAllOf(PlayerInventory inventory, Material material) {
        int total = 0;
        ItemStack[] contents = inventory.getContents();
        for (int i = 0; i < contents.length; i++) {
            ItemStack item = contents[i];
            if (item != null && item.getType() == material && !com.vetroforge.items.ItemSafety.locked(item)) {
                total += item.getAmount();
                contents[i] = null;
            }
        }
        inventory.setContents(contents);
        return total;
    }

    private static void fail(Player player, String message) {
        player.sendMessage(Branding.PREFIX + "§c" + message);
        player.sendActionBar("§cSellShop §8• §7" + message);
        try { player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.7F, 0.7F); } catch (Throwable ignored) {}
    }

    private static void success(Player player, float pitch) {
        try { player.playSound(player.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 0.65F, pitch); } catch (Throwable ignored) {}
    }

    private record SellBatch(String id, Material material, int amount) {
    }

    public record Result(int amount, double payout, int cactusAmount, boolean eventProgressWritten) {
        public static Result empty() {
            return new Result(0, 0.0D, 0, false);
        }

        public boolean soldAnything() {
            return amount > 0;
        }
    }
}
