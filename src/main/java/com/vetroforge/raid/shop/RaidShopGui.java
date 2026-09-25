// src/main/java/com/vetroforge/raid/shop/RaidShopGui.java
package com.vetroforge.raid.shop;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.raid.breach.BreachChargeListener;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.PotionMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.*;

public class RaidShopGui implements Listener {

    private final FactionsPlugin plugin;
    private final EnergyCrystalManager crystals;
    private final NamespacedKey offerKey;

    public RaidShopGui(FactionsPlugin plugin, EnergyCrystalManager crystals) {
        this.plugin = plugin;
        this.crystals = crystals;
        this.offerKey = new NamespacedKey(plugin, "raid_shop_offer");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        openOverview(p, false);
    }

    public void openAdmin(Player p) {
        openOverview(p, true);
    }

    private void openOverview(Player p, boolean admin) {
        Inventory inv = Bukkit.createInventory(new Holder(null, admin), 54, admin ? "§9§lVetroForge §8• §cRaidShop Admin" : tr(p, "title"));
        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE));
        for (int slot : new int[]{10,11,12,13,14,15,16,19,25,28,34,37,38,39,40,41,42,43}) {
            inv.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE));
        }

        int balance = bankBalance(p);
        int max = bankMax(p);
        inv.setItem(4, info(Material.NETHER_STAR,
                admin ? "§9§lVetroForge §8• §cRaidShop Admin" : "§9§lVetroForge §8• §fRaid-Shop",
                Arrays.asList(
                        admin ? "§7Bearbeite Preise direkt im GUI." : "§7Waehle eine Kategorie.",
                        "§7Jede Kategorie hat eine eigene Seite.",
                        "",
                        "§8Quelle: §f" + bankSource(p),
                        "§8Bank: §d" + balance + "§8/§d" + max + " Shards"
                )));
        inv.setItem(22, bankItem(p, balance, max));

        inv.setItem(20, categoryItem(ShopCategory.ATTACK, null, admin));
        inv.setItem(21, categoryItem(ShopCategory.DEFENSE, null, admin));
        inv.setItem(23, categoryItem(ShopCategory.MOBILITY, null, admin));
        inv.setItem(24, categoryItem(ShopCategory.UTILITY, null, admin));
        inv.setItem(49, closeItem());

        p.openInventory(inv);
        startBankAnimation(p);
        try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.1f); } catch (Throwable ignored) {}
    }

    private void open(Player p, ShopCategory category, boolean admin) {
        Inventory inv = Bukkit.createInventory(new Holder(category, admin), 54,
                admin ? "§9§lVetroForge §8• §c" + category.label : "§9§lVetroForge §8• §f" + category.label);
        fill(inv, pane(Material.BLACK_STAINED_GLASS_PANE));
        for (int slot : new int[]{10,11,12,13,14,15,16,19,25,28,34,37,38,39,40,41,42,43}) {
            inv.setItem(slot, pane(Material.GRAY_STAINED_GLASS_PANE));
        }

        int balance = bankBalance(p);
        int max = bankMax(p);
        inv.setItem(4, info(Material.NETHER_STAR,
                admin ? "§9§lVetroForge §8• §cAdmin Preise" : "§9§lVetroForge §8• §fRaid-Shop",
                Arrays.asList(
                        admin ? "§7Klicke Items, um Preise zu aendern." : category.description,
                        admin ? "§8Links +1 §7| §8Rechts -1" : "§7Diese Kategorie ist eine eigene Seite.",
                        admin ? "§8Shift = 10er Schritte" : "",
                        "",
                        "§8Quelle: §f" + bankSource(p),
                        "§8Bank: §d" + balance + "§8/§d" + max + " Shards"
                )));
        inv.setItem(22, bankItem(p, balance, max));

        inv.setItem(10, categoryItem(ShopCategory.ATTACK, category, admin));
        inv.setItem(12, categoryItem(ShopCategory.DEFENSE, category, admin));
        inv.setItem(14, categoryItem(ShopCategory.MOBILITY, category, admin));
        inv.setItem(16, categoryItem(ShopCategory.UTILITY, category, admin));
        renderOffers(inv, p, category, admin);
        inv.setItem(45, backItem());
        inv.setItem(49, closeItem());

        p.openInventory(inv);
        startBankAnimation(p);
        try { p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.1f); } catch (Throwable ignored) {}
    }

    private void renderOffers(Inventory inv, Player player, ShopCategory category, boolean admin) {
        int[] slots = {29, 30, 31, 32, 33, 38, 39, 40, 41, 42};
        int index = 0;
        for (ShopOffer offer : offers(category, admin)) {
            if (index >= slots.length) break;
            inv.setItem(slots[index++], offer(player, offer, admin));
        }
    }

    private int cost(String path) {
        // Standardpreise; "breach2" default 8 Crystals wie gewünscht
        int def = switch (path) {
            case "breach2" -> 8;
            case "breach4" -> 14;
            case "siege_bundle" -> 10;
            case "cannon_bundle" -> 12;
            case "tank_bundle" -> 9;
            case "water_kit" -> 5;
            case "mobility_bundle" -> 7;
            case "slow_falling" -> 4;
            case "raid_energy_1" -> 12;
            case "faction_elo_50" -> 10;
            case "scout_bundle" -> 8;
            case "obsidian_64" -> 3;
            case "pearls_16" -> 3;
            case "speed2" -> 3;
            case "strength2" -> 3;
            case "creeper_eggs_2" -> 3;
            case "tnt_64" -> 4;
            case "fire_resistance" -> 4;
            case "gapples_2" -> 5;
            default -> 3;
        };
        return Math.max(1, plugin.getConfig().getInt("raid.shop.costs." + path, def));
    }

    private ItemStack offer(Player player, ShopOffer offer, boolean admin) {
        int price = cost(offer.id());
        boolean enabled = enabled(offer.id());
        ItemStack it = new ItemStack(enabled ? offer.material() : Material.GRAY_DYE);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName((enabled ? "" : "§8§m") + offerName(player, offer.id()));
        im.setLore(admin
                ? Arrays.asList(
                        enabled ? "§aAktiv im Spieler-Shop" : "§cDeaktiviert",
                        "§8Preis: §d" + price + " Shards",
                        "§8Menge: §f" + offer.amount(),
                        "§8Reward: §f" + offer.rewardType() + (offer.rewardValue().isBlank() ? "" : " §7(" + offer.rewardValue() + ")"),
                        "",
                        "§aLinksklick §8» §f+1",
                        "§cRechtsklick §8» §f-1",
                        "§eShift-Linksklick §8» §f+10",
                        enabled
                                ? "§4Shift-Rechtsklick §8» §fAngebot entfernen"
                                : "§aKlick §8» §fAngebot hinzufügen",
                        "",
                        "§7Wird direkt gespeichert.",
                        "§8Quelle: Config, spaeter MySQL/Webpanel."
                )
                : Arrays.asList(
                        tr(player, "price", "price", price),
                        "§7Reward: §f" + rewardHint(offer),
                        tr(player, "click_buy")
                ));
        im.getPersistentDataContainer().set(offerKey, PersistentDataType.STRING, offer.id());
        it.setItemMeta(im);
        it.setAmount(Math.min(Math.max(1, offer.amount()), it.getMaxStackSize()));
        return it;
    }

    private ItemStack pane(Material material) {
        ItemStack it = new ItemStack(material);
        ItemMeta im = it.getItemMeta(); im.setDisplayName(" "); it.setItemMeta(im);
        return it;
    }

    private void fill(Inventory inv, ItemStack filler) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, filler);
    }

    private ItemStack info(Material mat, String name, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta(); im.setDisplayName(name); im.setLore(lore); it.setItemMeta(im);
        return it;
    }

    private ItemStack closeItem() {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName("§cSchließen");
        im.getPersistentDataContainer().set(offerKey, PersistentDataType.STRING, "close");
        it.setItemMeta(im);
        return it;
    }

    private ItemStack backItem() {
        ItemStack it = new ItemStack(Material.ARROW);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName("§eZurück");
        im.setLore(List.of("§7Zur Kategorie-Übersicht."));
        im.getPersistentDataContainer().set(offerKey, PersistentDataType.STRING, "back");
        it.setItemMeta(im);
        return it;
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder holder)) return;

        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String id = it.getItemMeta().getPersistentDataContainer().get(offerKey, PersistentDataType.STRING);
        if (id == null) return;
        if ("close".equals(id)) {
            p.closeInventory();
            return;
        }
        if ("back".equals(id)) {
            openOverview(p, holder.admin());
            return;
        }
        if (id.startsWith("cat:")) {
            open(p, ShopCategory.from(id.substring("cat:".length())), holder.admin());
            return;
        }
        if (holder.admin()) {
            editOffer(p, holder.category(), id, e.getClick());
            return;
        }
        ShopOffer offer = offerById(id);
        if (offer == null) return;
        int price = cost(id);
        if (requiresFaction(offer) && factionOf(p) == null) {
            p.closeInventory();
            p.sendMessage("§cDafür brauchst du eine Faction.");
            return;
        }
        if (!enabled(id)) {
            p.closeInventory();
            p.sendMessage("§cDieses RaidShop-Angebot ist aktuell deaktiviert.");
            return;
        }

        if (!crystals.spend(p.getUniqueId(), price)) {
            p.closeInventory();
            p.sendMessage(tr(p, "not_enough"));
            return;
        }

        // Rewards
        switch (id) {
            case "breach2" -> {
                ItemStack charges = BreachChargeListener.makeCharge(plugin, 2);
                p.getInventory().addItem(charges);
            }
            case "obsidian_64" -> p.getInventory().addItem(new ItemStack(Material.OBSIDIAN, 64));
            case "pearls_16" -> p.getInventory().addItem(new ItemStack(Material.ENDER_PEARL, 16));
            case "speed2" -> givePotion(p, "speed2_long");
            case "strength2" -> givePotion(p, "strength2_short");
            case "creeper_eggs_2" -> p.getInventory().addItem(new ItemStack(Material.CREEPER_SPAWN_EGG, 2));
            case "tnt_64" -> p.getInventory().addItem(new ItemStack(Material.TNT, 64));
            case "fire_resistance" -> givePotion(p, "fire_resistance");
            case "gapples_2" -> p.getInventory().addItem(new ItemStack(Material.GOLDEN_APPLE, 2));
            default -> giveCustomReward(p, offer);
        }

        auditPurchase(p, id, price, bankBalance(p));
        notifyFactionInbox(p, id, price);
        p.sendMessage(tr(p, "purchased", "balance", bankBalance(p)));
        try { p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.2f); } catch (Throwable ignored) {}
    }

    private void giveCustomReward(Player player, ShopOffer offer) {
        String type = offer.rewardType() == null ? "ITEM" : offer.rewardType().toUpperCase(Locale.ROOT);
        String value = offer.rewardValue() == null ? "" : offer.rewardValue().trim();
        switch (type) {
            case "POTION" -> {
                givePotion(player, value.isBlank() ? offer.id() : value);
            }
            case "BREACH_CHARGE" -> {
                player.getInventory().addItem(BreachChargeListener.makeCharge(plugin, Math.max(1, offer.amount())));
            }
            case "BUNDLE" -> {
                giveBundle(player, value);
            }
            case "ENERGY" -> {
                int amount = Math.max(1, parseInt(value, offer.amount()));
                if (plugin.getEnergyManager() != null) plugin.getEnergyManager().add(player.getUniqueId(), amount);
                player.sendActionBar("§bRaid Energy §8• §a+" + amount);
            }
            case "FACTION_ELO" -> {
                int amount = Math.max(1, parseInt(value, offer.amount()));
                String faction = factionOf(player);
                if (faction != null && plugin.getFactionUpgradeManager() != null) {
                    plugin.getFactionUpgradeManager().addElo(faction, amount);
                    player.sendActionBar("§bFraktionsmacht §8• §a+" + amount);
                }
            }
            case "COMMAND" -> {
                runRewardCommand(player, value);
            }
            case "KIT" -> {
                if (value.contains(" ") || value.startsWith("/")) runRewardCommand(player, value);
                else runRewardCommand(player, "kit give {player} " + value);
            }
            default -> {
                giveMaterial(player, offer.material(), offer.amount());
            }
        }
    }

    private String rewardHint(ShopOffer offer) {
        String type = offer.rewardType() == null ? "ITEM" : offer.rewardType().toUpperCase(Locale.ROOT);
        String value = offer.rewardValue() == null ? "" : offer.rewardValue().trim();
        return switch (type) {
            case "BUNDLE" -> "Raid-Bundle";
            case "ENERGY" -> "+" + Math.max(1, parseInt(value, offer.amount())) + " Raid Energy";
            case "FACTION_ELO" -> "+" + Math.max(1, parseInt(value, offer.amount())) + " Fraktionsmacht";
            case "POTION" -> "Potion";
            case "BREACH_CHARGE" -> "Breach Charge x" + Math.max(1, offer.amount());
            case "COMMAND" -> "Spezial-Reward";
            case "KIT" -> "Kit";
            default -> humanizeMaterial(offer.material()) + " x" + Math.max(1, offer.amount());
        };
    }

    /** ENDER_PEARL -> "Ender Pearl", for reward lore that would otherwise show the raw enum name. */
    private String humanizeMaterial(Material material) {
        String[] words = material.name().toLowerCase(Locale.ROOT).split("_");
        StringBuilder out = new StringBuilder();
        for (String word : words) {
            if (word.isEmpty()) continue;
            if (!out.isEmpty()) out.append(' ');
            out.append(Character.toUpperCase(word.charAt(0))).append(word.substring(1));
        }
        return out.toString();
    }

    private boolean requiresFaction(ShopOffer offer) {
        String type = offer.rewardType() == null ? "" : offer.rewardType().toUpperCase(Locale.ROOT);
        return "FACTION_ELO".equals(type);
    }

    private void giveBundle(Player player, String value) {
        if (value == null || value.isBlank()) return;
        for (String part : value.split(",")) {
            String[] split = part.trim().split(":", 2);
            if (split.length == 0 || split[0].isBlank()) continue;
            Material material = Material.matchMaterial(split[0].trim().toUpperCase(Locale.ROOT));
            if (material == null || material.isAir()) continue;
            int amount = split.length > 1 ? Math.max(1, parseInt(split[1].trim(), 1)) : 1;
            giveMaterial(player, material, amount);
        }
    }

    private void giveMaterial(Player player, Material material, int amount) {
        int remaining = Math.max(1, amount);
        int stack = Math.max(1, material.getMaxStackSize());
        while (remaining > 0) {
            int give = Math.min(stack, remaining);
            player.getInventory().addItem(new ItemStack(material, give));
            remaining -= give;
        }
    }

    private int parseInt(String raw, int fallback) {
        try { return Integer.parseInt(raw); } catch (Throwable ignored) { return fallback; }
    }

    private void runRewardCommand(Player player, String raw) {
        if (raw == null || raw.isBlank()) return;
        String command = raw.startsWith("/") ? raw.substring(1) : raw;
        command = command
                .replace("{player}", player.getName())
                .replace("{uuid}", player.getUniqueId().toString());
        Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
    }

    private void changeCost(Player player, ShopCategory category, String id, boolean decrease, boolean bigStep) {
        if (!player.hasPermission("vetroforge.raidshop.admin")) {
            player.sendMessage("§cDazu hast du keine Rechte.");
            return;
        }
        int step = bigStep ? 10 : 1;
        int next = Math.max(1, cost(id) + (decrease ? -step : step));
        plugin.getConfig().set("raid.shop.costs." + id, next);
        plugin.saveConfig();
        player.sendMessage("§9VetroForge §8• §fRaidShop Preis §d" + id + " §8= §d" + next + " Shards");
        try { player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 1f, decrease ? 0.8f : 1.25f); } catch (Throwable ignored) {}
        if (category == null) openOverview(player, true);
        else open(player, category, true);
    }

    private void editOffer(Player player, ShopCategory category, String id, ClickType click) {
        if (!enabled(id)) {
            setEnabled(id, true);
            player.sendMessage("§9VetroForge §8• §fRaidShop Angebot §d" + id + " §aaktiviert");
            try { player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1f, 1.45f); } catch (Throwable ignored) {}
            if (category == null) openOverview(player, true);
            else open(player, category, true);
            return;
        }
        if (click == ClickType.SHIFT_RIGHT) {
            setEnabled(id, false);
            player.sendMessage("§9VetroForge §8• §fRaidShop Angebot §d" + id + " §cdeaktiviert");
            try { player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 1f, 0.75f); } catch (Throwable ignored) {}
            if (category == null) openOverview(player, true);
            else open(player, category, true);
            return;
        }
        changeCost(player, category, id, click.isRightClick(), click.isShiftClick());
    }

    private void setEnabled(String id, boolean enabled) {
        plugin.getConfig().set("raid.shop.offers." + id + ".enabled", enabled);
        plugin.saveConfig();
    }

    /* -------- Versionssicherer Effekt-Resolver (fix für INCREASE_DAMAGE/STRENGTH) -------- */
    private PotionEffectType resolveEffect(String... names) {
        for (String n : names) {
            // Versuche direkten Namen (egal ob upper/lower)
            PotionEffectType t = PotionEffectType.getByName(n);
            if (t != null) return t;
            t = PotionEffectType.getByName(n.toUpperCase(Locale.ROOT));
            if (t != null) return t;
            t = PotionEffectType.getByName(n.toLowerCase(Locale.ROOT));
            if (t != null) return t;
        }
        // Letzter Versuch: grob vergleichen
        try {
            for (PotionEffectType t : PotionEffectType.values()) {
                if (t == null) continue;
                String nm = t.getName();
                if (nm == null) continue;
                String norm = nm.replace("_","").toLowerCase(Locale.ROOT);
                for (String ask : names) {
                    String an = ask.replace("_","").replace("minecraft:","").toLowerCase(Locale.ROOT);
                    if (norm.equals(an)) return t;
                }
            }
        } catch (Throwable ignored) {}
        return PotionEffectType.SPEED; // sicherer Fallback
    }

    private void givePotion(Player p, String preset) {
        ItemStack pot = new ItemStack(Material.POTION, 1);
        PotionMeta meta = (PotionMeta) pot.getItemMeta();

        switch (preset) {
            case "speed2_long" -> {
                meta.setDisplayName(tr(p, "potion.speed2"));
                meta.setColor(Color.AQUA);
                PotionEffectType speed = resolveEffect("SPEED", "minecraft:speed");
                meta.addCustomEffect(new PotionEffect(speed, 8 * 60 * 20, 1), true);
            }
            case "strength2_short" -> {
                meta.setDisplayName(tr(p, "potion.strength2"));
                meta.setColor(Color.RED);
                PotionEffectType strength = resolveEffect("INCREASE_DAMAGE", "STRENGTH", "minecraft:strength");
                meta.addCustomEffect(new PotionEffect(strength, 90 * 20, 1), true);
            }
            case "fire_resistance" -> {
                meta.setDisplayName("§6Feuerresistenz §8(6:00)");
                meta.setColor(Color.ORANGE);
                PotionEffectType fireResistance = resolveEffect("FIRE_RESISTANCE", "minecraft:fire_resistance");
                meta.addCustomEffect(new PotionEffect(fireResistance, 6 * 60 * 20, 0), true);
            }
            case "slow_falling" -> {
                meta.setDisplayName("§fSlow Falling §8(2:30)");
                meta.setColor(Color.WHITE);
                PotionEffectType slowFalling = resolveEffect("SLOW_FALLING", "minecraft:slow_falling");
                meta.addCustomEffect(new PotionEffect(slowFalling, 150 * 20, 0), true);
            }
            default -> { return; }
        }

        pot.setItemMeta(meta);
        p.getInventory().addItem(pot);
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, "raid.shop." + key, VetroForgeUtil.vars(values));
    }

    private String offerName(Player player, String id) {
        String translated = tr(player, "offer." + id);
        if (translated != null && !translated.contains("raid.shop.offer.")) return translated;
        return switch (id) {
            case "breach2" -> "§aBreach Charge x2";
            case "breach4" -> "§aBreach Charge x4";
            case "siege_bundle" -> "§cSiege Bundle";
            case "cannon_bundle" -> "§cCannon Bundle";
            case "tank_bundle" -> "§6Tank Bundle";
            case "water_kit" -> "§bDefense Kit";
            case "mobility_bundle" -> "§bMobility Bundle";
            case "slow_falling" -> "§fSlow Falling §8(2:30)";
            case "raid_energy_1" -> "§bRaid Energy +1";
            case "faction_elo_50" -> "§bFraktionsmacht +50";
            case "scout_bundle" -> "§eScout Bundle";
            case "obsidian_64" -> "§aObsidian x64";
            case "pearls_16" -> "§aEnderperlen x16";
            case "creeper_eggs_2" -> "§aCreeper-Eier x2";
            case "tnt_64" -> "§cTNT x64";
            case "speed2" -> "§bSpeed II §8(8:00)";
            case "fire_resistance" -> "§6Feuerresistenz §8(6:00)";
            case "strength2" -> "§cStrength II §8(1:30)";
            case "gapples_2" -> "§eGoldene Aepfel x2";
            default -> "§f" + id;
        };
    }

    private List<ShopOffer> offers(ShopCategory category, boolean admin) {
        List<ShopOffer> out = new ArrayList<>();
        for (ShopOffer offer : defaultOffers()) {
            if (offer.category() != category) continue;
            if (!admin && !enabled(offer.id())) continue;
            out.add(offer);
        }
        return out;
    }

    private boolean enabled(String id) {
        return plugin.getConfig().getBoolean("raid.shop.offers." + id + ".enabled", true);
    }

    private List<ShopOffer> defaultOffers() {
        List<ShopOffer> out = new ArrayList<>(Arrays.asList(
                new ShopOffer("breach2", ShopCategory.ATTACK, Material.TNT, 2),
                new ShopOffer("breach4", ShopCategory.ATTACK, Material.TNT, 4, "BREACH_CHARGE", ""),
                new ShopOffer("tnt_64", ShopCategory.ATTACK, Material.TNT, 64),
                new ShopOffer("cannon_bundle", ShopCategory.ATTACK, Material.DISPENSER, 1, "BUNDLE", "TNT:64,FIRE_CHARGE:8,REDSTONE:32,REPEATER:16,DISPENSER:4"),
                new ShopOffer("siege_bundle", ShopCategory.ATTACK, Material.TNT_MINECART, 1, "BUNDLE", "TNT:64,OBSIDIAN:32,ENDER_PEARL:8"),
                new ShopOffer("strength2", ShopCategory.ATTACK, Material.BLAZE_POWDER, 1),
                new ShopOffer("creeper_eggs_2", ShopCategory.ATTACK, Material.CREEPER_SPAWN_EGG, 2),
                new ShopOffer("obsidian_64", ShopCategory.DEFENSE, Material.OBSIDIAN, 64),
                new ShopOffer("tank_bundle", ShopCategory.DEFENSE, Material.SHIELD, 1, "BUNDLE", "OBSIDIAN:64,GOLDEN_APPLE:4,COBWEB:16"),
                new ShopOffer("water_kit", ShopCategory.DEFENSE, Material.WATER_BUCKET, 1, "BUNDLE", "WATER_BUCKET:2,LAVA_BUCKET:1,OBSIDIAN:32"),
                new ShopOffer("gapples_2", ShopCategory.DEFENSE, Material.GOLDEN_APPLE, 2),
                new ShopOffer("fire_resistance", ShopCategory.DEFENSE, Material.MAGMA_CREAM, 1),
                new ShopOffer("pearls_16", ShopCategory.MOBILITY, Material.ENDER_PEARL, 16),
                new ShopOffer("mobility_bundle", ShopCategory.MOBILITY, Material.ENDER_PEARL, 1, "BUNDLE", "ENDER_PEARL:16,COOKED_BEEF:16"),
                new ShopOffer("speed2", ShopCategory.MOBILITY, Material.SUGAR, 1),
                new ShopOffer("slow_falling", ShopCategory.MOBILITY, Material.FEATHER, 1, "POTION", "slow_falling"),
                new ShopOffer("obsidian_64", ShopCategory.UTILITY, Material.OBSIDIAN, 64),
                new ShopOffer("raid_energy_1", ShopCategory.UTILITY, Material.EXPERIENCE_BOTTLE, 1, "ENERGY", "1"),
                new ShopOffer("faction_elo_50", ShopCategory.UTILITY, Material.EMERALD, 50, "FACTION_ELO", "50"),
                new ShopOffer("scout_bundle", ShopCategory.UTILITY, Material.SPYGLASS, 1, "BUNDLE", "SPYGLASS:1,ENDER_PEARL:8,COOKED_BEEF:16"),
                new ShopOffer("fire_resistance", ShopCategory.UTILITY, Material.MAGMA_CREAM, 1),
                new ShopOffer("gapples_2", ShopCategory.UTILITY, Material.GOLDEN_APPLE, 2)
        ));
        org.bukkit.configuration.ConfigurationSection custom = plugin.getConfig().getConfigurationSection("raid.shop.customOffers");
        if (custom != null) {
            for (String id : custom.getKeys(false)) {
                Material material = Material.matchMaterial(custom.getString(id + ".material", "STONE"));
                if (material == null || material.isAir()) material = Material.STONE;
                out.add(new ShopOffer(
                        id,
                        ShopCategory.from(custom.getString(id + ".category", "UTILITY")),
                        material,
                        Math.max(1, custom.getInt(id + ".amount", 1)),
                        custom.getString(id + ".rewardType", "ITEM"),
                        custom.getString(id + ".rewardValue", "")
                ));
            }
        }
        return out;
    }

    private ShopOffer offerById(String id) {
        for (ShopOffer offer : defaultOffers()) {
            if (offer.id().equals(id)) return offer;
        }
        return null;
    }

    private void auditPurchase(Player player, String id, int price, int balanceAfter) {
        try {
            File dir = new File(plugin.getDataFolder(), "data");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, "raidshop-purchases.log");
            try (FileWriter writer = new FileWriter(file, true)) {
                writer.write(System.currentTimeMillis()
                        + "," + player.getUniqueId()
                        + "," + player.getName()
                        + "," + id
                        + "," + price
                        + "," + balanceAfter
                        + System.lineSeparator());
            }
        } catch (IOException exception) {
            plugin.getLogger().warning("[RaidShop] Audit log failed: " + exception.getMessage());
        }
    }

    private void notifyFactionInbox(Player player, String id, int price) {
        try {
            if (plugin.getFactionInboxService() == null || plugin.getFactionManager() == null) return;
            String faction = plugin.getFactionManager().getFactionOf(player.getUniqueId());
            if (faction == null || faction.isBlank()) return;
            plugin.getFactionInboxService().add(
                    faction,
                    "raidshop_purchase",
                    player.getName(),
                    id + " (" + price + " Shards)"
            );
        } catch (Throwable ignored) {
        }
    }

    private ItemStack bankItem(Player player, int current, int max) {
        int safeMax = Math.max(1, max);
        int percent = (int) Math.round(Math.min(1.0D, Math.max(0.0D, current / (double) safeMax)) * 100.0D);
        Material material = percent >= 90 ? Material.AMETHYST_CLUSTER : percent >= 50 ? Material.AMETHYST_SHARD : Material.ECHO_SHARD;
        return info(material,
                "§d§lFaction Bank §8• §f" + current + "§8/§f" + safeMax,
                Arrays.asList(
                        "§7Raid-Shards sind mit deiner",
                        "§7Faction geteilt.",
                        "",
                        progressBar(current, safeMax),
                        "§8Fortschritt: §f" + percent + "%",
                        "",
                        "§7Rechtsklick auf Kriegsbeute-Splitter",
                        "§7zahlt sie in diese Bank ein."
                ));
    }

    private ItemStack categoryItem(ShopCategory category, ShopCategory selected, boolean admin) {
        Material material = category == selected ? category.selectedMaterial : category.material;
        ItemStack item = info(material,
                (category == selected ? "§b§l" : "§7§l") + category.label,
                Arrays.asList(
                        category.description,
                        "",
                        category == selected ? "§aAktive Seite" : "§eKlick §8» §fKategorie öffnen",
                        admin ? "§cAdmin §8» §fPreise bearbeiten" : "§8Eigene Seite"
                ));
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(offerKey, PersistentDataType.STRING, "cat:" + category.name());
        item.setItemMeta(meta);
        return item;
    }

    private String progressBar(int current, int max) {
        int width = 20;
        int safeMax = Math.max(1, max);
        int filled = (int) Math.round(Math.min(1.0D, Math.max(0.0D, current / (double) safeMax)) * width);
        int pulse = (int) ((System.currentTimeMillis() / 350L) % width);
        StringBuilder out = new StringBuilder("§8[");
        for (int i = 0; i < width; i++) {
            if (i < filled) out.append(i == pulse ? "§f|" : "§d|");
            else out.append(i == pulse ? "§8|" : "§7|");
        }
        out.append("§8]");
        return out.toString();
    }

    private void startBankAnimation(Player player) {
        new BukkitRunnable() {
            private int ticks;

            @Override
            public void run() {
                if (!player.isOnline()
                        || !(player.getOpenInventory().getTopInventory().getHolder() instanceof Holder)) {
                    cancel();
                    return;
                }
                player.getOpenInventory().getTopInventory().setItem(22, bankItem(player, bankBalance(player), bankMax(player)));
                ticks += 10;
                if (ticks >= 20 * 60) cancel();
            }
        }.runTaskTimer(plugin, 10L, 10L);
    }

    private int bankBalance(Player player) {
        if (player != null && plugin.getRaidCrystalManager() != null) {
            return plugin.getRaidCrystalManager().getAvailable(player);
        }
        return player == null ? 0 : crystals.get(player.getUniqueId());
    }

    private int bankMax(Player player) {
        if (player != null && plugin.getRaidCrystalManager() != null) {
            return plugin.getRaidCrystalManager().getAvailableCap(player);
        }
        return crystals.getMax();
    }

    private String bankSource(Player player) {
        try {
            if (plugin.getRaidCrystalManager() != null && plugin.getRaidCrystalManager().useFactionSource()) {
                String faction = factionOf(player);
                if (faction == null || faction.isBlank()) return "Keine Faction";
                return "Faction " + plugin.getFactionManager().getDisplayName(faction);
            }
        } catch (Throwable ignored) {
        }
        return "Spieler-Bank";
    }

    private String factionOf(Player player) {
        return player == null || plugin.getFactionManager() == null ? null
                : plugin.getFactionManager().getFactionOf(player.getUniqueId());
    }

    private enum ShopCategory {
        ATTACK("Angriff", "§7Breach, TNT und Damage.", Material.IRON_SWORD, Material.NETHERITE_SWORD),
        DEFENSE("Defense", "§7Obsidian, Heilung und Schutz.", Material.SHIELD, Material.NETHERITE_CHESTPLATE),
        MOBILITY("Mobility", "§7Pearls, Speed und Bewegung.", Material.ENDER_PEARL, Material.FEATHER),
        UTILITY("Utility", "§7Flex-Tools fuer lange Raids.", Material.CHEST, Material.ENDER_CHEST);

        private final String label;
        private final String description;
        private final Material material;
        private final Material selectedMaterial;

        ShopCategory(String label, String description, Material material, Material selectedMaterial) {
            this.label = label;
            this.description = description;
            this.material = material;
            this.selectedMaterial = selectedMaterial;
        }

        private static ShopCategory from(String raw) {
            try {
                return ShopCategory.valueOf(raw == null ? "" : raw.toUpperCase(Locale.ROOT));
            } catch (Throwable ignored) {
                return ATTACK;
            }
        }
    }

    private record Holder(ShopCategory category, boolean admin) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record ShopOffer(String id, ShopCategory category, Material material, int amount, String rewardType, String rewardValue) {
        private ShopOffer(String id, ShopCategory category, Material material, int amount) {
            this(id, category, material, amount, "ITEM", "");
        }
    }
}
