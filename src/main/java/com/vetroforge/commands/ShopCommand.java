// src/main/java/com/vetroforge/commands/ShopCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.commerce.AICommerceManager;
import com.vetroforge.commerce.ShopGui;
import com.vetroforge.economy.EconomyManager;
import com.vetroforge.events.progress.impl.CactusSoldProgress;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;

public class ShopCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    public ShopCommand(FactionsPlugin plugin){ this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (a.length >= 2 && a[0].equalsIgnoreCase("debug") && a[1].equalsIgnoreCase("cactus")) {
            if (a.length >= 3 && a[2].equalsIgnoreCase("fix")) {
                fixCactus(s);
            }
            debugCactus(s);
            return true;
        }
        if (!(s instanceof Player p)) {
            s.sendMessage("§cDiesen Befehl kannst du nur ingame nutzen.");
            return true;
        }
        try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.6f, 1.4f); } catch (Throwable ignored) {}
        ShopGui.openMain(plugin, p);
        return true;
    }

    @Override
    public java.util.List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) return List.of("debug");
        if (a.length == 2 && a[0].equalsIgnoreCase("debug")) return List.of("cactus");
        if (a.length == 3 && a[0].equalsIgnoreCase("debug") && a[1].equalsIgnoreCase("cactus")) return List.of("fix");
        return java.util.Collections.emptyList();
    }

    private void debugCactus(CommandSender sender) {
        String id = idFor(Material.CACTUS);
        AICommerceManager ai = plugin.getAiCommerceManager();
        AICommerceManager.Entry entry = id == null || ai == null ? null : ai.get(id);
        double cfgSell = id == null ? 0.0D : plugin.getConfig().getDouble("shop.prices." + id + ".sell", 0.0D);
        double dynSell = entry == null ? cfgSell : entry.dynSell;
        int invCactus = sender instanceof Player player ? count(player, Material.CACTUS) : -1;
        boolean cactusInCategory = isInAnyCategory(id == null ? "cactus" : id);
        boolean sellPermission = !(sender instanceof Player player) || player.hasPermission("vetroforge.shop.sell");
        boolean eventActive = plugin.getEventManager() != null && plugin.getEventManager().isActiveEvent("cactus_grind");

        sender.sendMessage("§8§m--------------------------------");
        sender.sendMessage("§b§lVetroForge §8• §fShop Debug §8(Cactus)");
        sender.sendMessage("§7Shop-ID: " + (id == null ? "§cNicht gefunden" : "§a" + id));
        sender.sendMessage("§7Material: §f" + Material.CACTUS.name());
        sender.sendMessage("§7In Kategorie: " + (cactusInCategory ? "§aJa" : "§eNein §8(§7Button funktioniert trotzdem§8)"));
        sender.sendMessage("§7Config Sell: §6" + EconomyManager.formatAmount(cfgSell) + " Coins");
        sender.sendMessage("§7Dynamic Sell: §6" + EconomyManager.formatAmount(dynSell) + " Coins");
        sender.sendMessage("§7Sell-Permission: " + (sellPermission ? "§aOK" : "§cFehlt vetroforge.shop.sell"));
        sender.sendMessage("§7CoinsManager: " + (plugin.getCoinsManager() == null ? "§cfehlt" : "§ageladen"));
        sender.sendMessage("§7EventManager: " + (plugin.getEventManager() == null ? "§cfehlt" : "§ageladen"));
        sender.sendMessage("§7Event aktiv: " + (eventActive ? "§aCactus Grind" : "§eNein"));
        sender.sendMessage("§7Event-Progress Source: §fcactus_sold §8(§7Delta seit Eventstart§8)");
        sender.sendMessage("§7Runtime Cactus verkauft: §f" + CactusSoldProgress.current());
        if (plugin.getEventManager() != null) {
            sender.sendMessage("§7Event Debug: §f" + plugin.getEventManager().debugActiveEvent());
            Optional<com.vetroforge.events.EventService.RunningEvent> active = plugin.getEventManager().getActiveEvent();
            active.ifPresentOrElse(
                    event -> sender.sendMessage("§7Aktives Event: §f" + event.name
                            + " §8(" + event.current + "/" + event.goal + ", " + event.pct() + "%)"),
                    () -> sender.sendMessage("§7Aktives Event: §cKeins")
            );
        }
        if (invCactus >= 0) {
            int payout = Math.max(1, (int) Math.ceil(dynSell * invCactus));
            sender.sendMessage("§7Cactus im Inventar: §f" + invCactus + " §8→ §6" + payout + " Coins");
        }
        sender.sendMessage(debugVerdict(id, cfgSell, dynSell, sellPermission));
        sender.sendMessage("§8§m--------------------------------");
    }

    private String debugVerdict(String id, double cfgSell, double dynSell, boolean sellPermission) {
        if (id == null) return "§cFix: Cactus in shop.general.items oder AI-Shop eintragen.";
        if (!sellPermission) return "§cFix: Spieler braucht vetroforge.shop.sell.";
        if (cfgSell <= 0.0D && dynSell <= 0.0D) return "§cFix: shop.prices." + id + ".sell ist 0 oder fehlt.";
        if (plugin.getCoinsManager() == null) return "§cFix: CoinsManager ist nicht geladen.";
        if (plugin.getEventManager() == null) return "§eShop ok, aber EventManager fehlt.";
        return "§aCactus-Verkauf ist bereit. SellShop-Cactus-Button zählt ins aktive Cactus-Event.";
    }

    private void fixCactus(CommandSender sender) {
        if (!sender.hasPermission("vetroforge.shop.admin")) {
            sender.sendMessage("§cFix benötigt vetroforge.shop.admin.");
            return;
        }

        boolean changed = false;
        String id = idFor(Material.CACTUS);
        if (id == null || id.isBlank()) {
            id = "cactus";
            plugin.getConfig().set("shop.general.items." + id + ".material", Material.CACTUS.name());
            plugin.getConfig().set("shop.general.items." + id + ".display", "&aCactus");
            plugin.getConfig().set("shop.general.items." + id + ".amount", 1);
            changed = true;
        }

        String sellPath = "shop.prices." + id + ".sell";
        if (plugin.getConfig().getDouble(sellPath, 0.0D) <= 0.0D) {
            plugin.getConfig().set(sellPath, 2.0D);
            changed = true;
        }

        String buyPath = "shop.prices." + id + ".buy";
        if (!plugin.getConfig().contains(buyPath)) {
            plugin.getConfig().set(buyPath, 8.0D);
            changed = true;
        }

        String categoryPath = "shop.categories.farming.items";
        List<String> farmingItems = new ArrayList<>(plugin.getConfig().getStringList(categoryPath));
        String cactusId = id;
        boolean listed = farmingItems.stream().anyMatch(item -> cactusId.equalsIgnoreCase(item));
        if (!listed) {
            farmingItems.add(id);
            plugin.getConfig().set(categoryPath, farmingItems);
            changed = true;
        }
        if (!plugin.getConfig().contains("shop.categories.farming.name")) {
            plugin.getConfig().set("shop.categories.farming.name", "&aFarming");
            changed = true;
        }
        if (!plugin.getConfig().contains("shop.categories.farming.icon")) {
            plugin.getConfig().set("shop.categories.farming.icon", "CACTUS");
            changed = true;
        }

        if (changed) {
            plugin.saveConfig();
            sender.sendMessage("§aCactus-Shop Config ergänzt. §7ID: §f" + id);
        } else {
            sender.sendMessage("§aCactus-Shop Config war bereits okay. §7ID: §f" + id);
        }
    }

    private String idFor(Material material) {
        AICommerceManager ai = plugin.getAiCommerceManager();
        if (ai != null) {
            for (AICommerceManager.Entry entry : ai.all()) {
                if (materialFor(entry.id) == material) return entry.id;
            }
        }
        var section = plugin.getConfig().getConfigurationSection("shop.general.items");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                if (materialFor(id) == material) return id;
            }
        }
        return null;
    }

    private Material materialFor(String id) {
        String raw = plugin.getConfig().getString("shop.general.items." + id + ".material", "STONE");
        Material material = Material.matchMaterial(raw);
        if (material == null) {
            try { material = Material.valueOf(raw.toUpperCase(Locale.ROOT)); } catch (Throwable ignored) {}
        }
        return material == null ? Material.STONE : material;
    }

    private int count(Player player, Material material) {
        int total = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (item != null && item.getType() == material) total += item.getAmount();
        }
        return total;
    }

    private boolean isInAnyCategory(String id) {
        if (id == null || id.isBlank()) return false;
        var section = plugin.getConfig().getConfigurationSection("shop.categories");
        if (section == null) return false;
        for (String category : section.getKeys(false)) {
            for (String itemId : plugin.getConfig().getStringList("shop.categories." + category + ".items")) {
                if (id.equalsIgnoreCase(itemId)) return true;
            }
        }
        return false;
    }

}
