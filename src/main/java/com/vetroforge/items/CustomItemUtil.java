// src/main/java/com/vetroforge/items/CustomItemUtil.java
package com.vetroforge.items;

import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class CustomItemUtil {
    private CustomItemUtil() {}

    public static NamespacedKey KEY_BASE(FactionsPlugin p) { return new NamespacedKey(p, "ci_base"); }
    public static NamespacedKey KEY_RARITY(FactionsPlugin p) { return new NamespacedKey(p, "ci_rarity"); }
    public static NamespacedKey KEY_LEVEL(FactionsPlugin p) { return new NamespacedKey(p, "ci_level"); }
    public static NamespacedKey KEY_FP(FactionsPlugin p) { return new NamespacedKey(p, "ci_fusion_points"); }
    public static NamespacedKey KEY_UID(FactionsPlugin p) { return new NamespacedKey(p, "ci_unique"); }
    public static NamespacedKey KEY_ARMOR_SET(FactionsPlugin p) { return new NamespacedKey(p, "ci_armor_set"); }
    public static NamespacedKey KEY_EVENT(FactionsPlugin p) { return new NamespacedKey(p, "ci_event"); }
    public static NamespacedKey KEY_ROLL_DAMAGE(FactionsPlugin p) { return new NamespacedKey(p, "ci_roll_damage"); }
    public static NamespacedKey KEY_ROLL_COINS(FactionsPlugin p) { return new NamespacedKey(p, "ci_roll_coins"); }
    public static NamespacedKey KEY_ROLL_COOLDOWN(FactionsPlugin p) { return new NamespacedKey(p, "ci_roll_cooldown"); }
    public static NamespacedKey KEY_SOULBOUND(FactionsPlugin p) { return new NamespacedKey(p, "ci_soulbound"); }
    public static NamespacedKey KEY_OWNER(FactionsPlugin p) { return new NamespacedKey(p, "ci_owner"); }
    public static NamespacedKey KEY_SOURCE(FactionsPlugin p) { return new NamespacedKey(p, "ci_source"); }
    public static NamespacedKey KEY_TRADE_LOCK_UNTIL(FactionsPlugin p) { return new NamespacedKey(p, "ci_trade_lock_until"); }
    public static NamespacedKey KEY_UPGRADE_PROTECTION(FactionsPlugin p) { return new NamespacedKey(p, "ci_upgrade_protection"); }
    public static NamespacedKey KEY_EVENT_TOKEN(FactionsPlugin p) { return new NamespacedKey(p, "ci_event_token"); }
    public static NamespacedKey KEY_ENCHANT_BOOK(FactionsPlugin p) { return new NamespacedKey(p, "ci_enchant_book"); }
    public static NamespacedKey KEY_ENCHANT_BOOK_LEVEL(FactionsPlugin p) { return new NamespacedKey(p, "ci_enchant_book_level"); }
    public static NamespacedKey KEY_RARITY_FUSION_FROM(FactionsPlugin p) { return new NamespacedKey(p, "ci_rarity_fusion_from"); }
    public static NamespacedKey KEY_RARITY_FUSION_TO(FactionsPlugin p) { return new NamespacedKey(p, "ci_rarity_fusion_to"); }

    public static NamespacedKey KEY_ENC_LIFESTEAL(FactionsPlugin p) { return new NamespacedKey(p, "ci_lifesteal"); }
    public static NamespacedKey KEY_ENC_BLEED(FactionsPlugin p) { return new NamespacedKey(p, "ci_bleed"); }
    public static NamespacedKey KEY_ENC_RAGE(FactionsPlugin p) { return new NamespacedKey(p, "ci_rage"); }
    public static NamespacedKey KEY_ENC_SHATTER(FactionsPlugin p) { return new NamespacedKey(p, "ci_shatter"); }
    public static NamespacedKey KEY_ENC_GUARD(FactionsPlugin p) { return new NamespacedKey(p, "ci_guard"); }

    public static String baseId(FactionsPlugin p, ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(KEY_BASE(p), PersistentDataType.STRING);
    }

    public static String armorSetOf(FactionsPlugin p, ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(KEY_ARMOR_SET(p), PersistentDataType.STRING);
    }

    public static double damageRollOf(FactionsPlugin p, ItemStack item) { return doubleMeta(p, item, KEY_ROLL_DAMAGE(p)); }
    public static double coinRollOf(FactionsPlugin p, ItemStack item) { return doubleMeta(p, item, KEY_ROLL_COINS(p)); }
    public static double cooldownRollOf(FactionsPlugin p, ItemStack item) { return doubleMeta(p, item, KEY_ROLL_COOLDOWN(p)); }
    public static boolean isSoulbound(FactionsPlugin p, ItemStack item) { return intMeta(p, item, KEY_SOULBOUND(p)) > 0; }
    public static boolean hasUpgradeProtection(FactionsPlugin p, ItemStack item) { return intMeta(p, item, KEY_UPGRADE_PROTECTION(p)) > 0; }
    public static String sourceOf(FactionsPlugin p, ItemStack item) { return stringMeta(p, item, KEY_SOURCE(p)); }
    public static String ownerOf(FactionsPlugin p, ItemStack item) { return stringMeta(p, item, KEY_OWNER(p)); }
    public static long tradeLockUntil(FactionsPlugin p, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0L;
        Long value = item.getItemMeta().getPersistentDataContainer().get(KEY_TRADE_LOCK_UNTIL(p), PersistentDataType.LONG);
        return value == null ? 0L : value;
    }
    public static boolean isTradeLocked(FactionsPlugin p, ItemStack item) { return tradeLockUntil(p, item) > System.currentTimeMillis(); }

    public static void setSource(FactionsPlugin p, ItemStack item, String source) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY_SOURCE(p), PersistentDataType.STRING, source == null ? "unknown" : source);
        item.setItemMeta(meta);
    }

    public static void bindSoulbound(FactionsPlugin p, ItemStack item, UUID owner) {
        if (item == null || !item.hasItemMeta() || owner == null) return;
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(KEY_SOULBOUND(p), PersistentDataType.INTEGER, 1);
        meta.getPersistentDataContainer().set(KEY_OWNER(p), PersistentDataType.STRING, owner.toString());
        item.setItemMeta(meta);
        refreshLore(p, item);
    }

    public static void setUpgradeProtection(FactionsPlugin p, ItemStack item, boolean active) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        if (active) meta.getPersistentDataContainer().set(KEY_UPGRADE_PROTECTION(p), PersistentDataType.INTEGER, 1);
        else meta.getPersistentDataContainer().remove(KEY_UPGRADE_PROTECTION(p));
        item.setItemMeta(meta);
        refreshLore(p, item);
    }

    public static void reroll(FactionsPlugin p, ItemStack item) {
        if (item == null || baseId(p, item) == null) return;
        applyRandomRolls(p, item, rarityOf(p, item));
        refreshLore(p, item);
    }

    public static Rarity rarityOf(FactionsPlugin p, ItemStack it) {
        if (it == null || !it.hasItemMeta()) return Rarity.COMMON;
        String r = it.getItemMeta().getPersistentDataContainer().get(KEY_RARITY(p), PersistentDataType.STRING);
        try {
            return r == null ? Rarity.COMMON : Rarity.valueOf(r);
        } catch (IllegalArgumentException e) {
            return Rarity.COMMON;
        }
    }

    public static int levelOf(FactionsPlugin p, ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Integer v = it.getItemMeta().getPersistentDataContainer().get(KEY_LEVEL(p), PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public static int fusionPointsOf(FactionsPlugin p, ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;
        Integer v = it.getItemMeta().getPersistentDataContainer().get(KEY_FP(p), PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public static int getEnchant(FactionsPlugin p, ItemStack it, CustomEnchant ce) {
        if (it == null || !it.hasItemMeta() || ce == null) return 0;
        PersistentDataContainer c = it.getItemMeta().getPersistentDataContainer();
        NamespacedKey k;
        switch (ce) {
            case LIFESTEAL -> k = KEY_ENC_LIFESTEAL(p);
            case BLEED -> k = KEY_ENC_BLEED(p);
            case RAGE -> k = KEY_ENC_RAGE(p);
            case SHATTER -> k = KEY_ENC_SHATTER(p);
            case GUARD -> k = KEY_ENC_GUARD(p);
            default -> { return 0; }
        }
        Integer v = c.get(k, PersistentDataType.INTEGER);
        return v == null ? 0 : v;
    }

    public static void setMeta(FactionsPlugin p, ItemStack it, String baseId, Rarity r, int level, int fp) {
        if (it == null) return;
        ItemMeta im = it.getItemMeta();
        PersistentDataContainer c = im.getPersistentDataContainer();

        c.set(KEY_BASE(p), PersistentDataType.STRING, baseId);
        c.set(KEY_RARITY(p), PersistentDataType.STRING, r.name());
        c.set(KEY_LEVEL(p), PersistentDataType.INTEGER, level);
        c.set(KEY_FP(p), PersistentDataType.INTEGER, fp);

        if (!c.has(KEY_UID(p), PersistentDataType.STRING)) {
            c.set(KEY_UID(p), PersistentDataType.STRING, UUID.randomUUID().toString());
        }
        if (!c.has(KEY_SOURCE(p), PersistentDataType.STRING)) {
            c.set(KEY_SOURCE(p), PersistentDataType.STRING, "crafted");
        }
        if (!c.has(KEY_ROLL_DAMAGE(p), PersistentDataType.DOUBLE)) {
            applyRandomRolls(c, p, r);
        }

        it.setItemMeta(im);
        refreshLore(p, it);
    }

    public static void setEnchant(FactionsPlugin p, ItemStack it, CustomEnchant ce, int lvl) {
        if (it == null || ce == null) return;
        ItemMeta im = it.getItemMeta();
        NamespacedKey k;
        switch (ce) {
            case LIFESTEAL -> k = KEY_ENC_LIFESTEAL(p);
            case BLEED -> k = KEY_ENC_BLEED(p);
            case RAGE -> k = KEY_ENC_RAGE(p);
            case SHATTER -> k = KEY_ENC_SHATTER(p);
            case GUARD -> k = KEY_ENC_GUARD(p);
            default -> { return; }
        }
        if (lvl <= 0) im.getPersistentDataContainer().remove(k);
        else im.getPersistentDataContainer().set(k, PersistentDataType.INTEGER, lvl);
        it.setItemMeta(im);
        refreshLore(p, it);
    }

    public static void setRarity(FactionsPlugin p, ItemStack it, Rarity rarity, boolean reroll) {
        if (it == null || rarity == null || baseId(p, it) == null) return;
        ItemMeta im = it.getItemMeta();
        PersistentDataContainer c = im.getPersistentDataContainer();
        c.set(KEY_RARITY(p), PersistentDataType.STRING, rarity.name());
        if (reroll) {
            c.remove(KEY_ROLL_DAMAGE(p));
            c.remove(KEY_ROLL_COINS(p));
            c.remove(KEY_ROLL_COOLDOWN(p));
            applyRandomRolls(c, p, rarity);
        }
        it.setItemMeta(im);
        refreshLore(p, it);
    }

    public static ItemStack makeEnchantBook(FactionsPlugin p, CustomEnchant enchant, int level) {
        int resolvedLevel = Math.max(1, Math.min(5, level));
        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName("§d§l" + prettyEnchant(enchant) + " " + roman(resolvedLevel) + " §8• §fApply Book");
        meta.setLore(List.of(
                "§7Ziehe dieses Buch auf ein passendes Item.",
                "§8» §fPreview: §d" + prettyEnchant(enchant) + " " + roman(resolvedLevel),
                "§8» §7Waffen: Lifesteal, Bleed, Rage, Shatter",
                "§8» §7Tools: Shatter",
                "§8» §7Rüstung: Guard",
                "",
                "§cWird erst beim erfolgreichen Anwenden verbraucht."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(KEY_ENCHANT_BOOK(p), PersistentDataType.STRING, enchant.name());
        meta.getPersistentDataContainer().set(KEY_ENCHANT_BOOK_LEVEL(p), PersistentDataType.INTEGER, resolvedLevel);
        book.setItemMeta(meta);
        return book;
    }

    public static ItemStack makeRarityFusionBook(FactionsPlugin p, Rarity from, Rarity to) {
        Rarity resolvedFrom = from == null ? Rarity.COMMON : from;
        Rarity resolvedTo = to == null ? nextRarity(resolvedFrom) : to;
        if (resolvedTo == null || resolvedTo.ordinal() <= resolvedFrom.ordinal()) {
            resolvedTo = nextRarity(resolvedFrom);
        }
        if (resolvedTo == null) {
            resolvedFrom = Rarity.LEGENDARY;
            resolvedTo = Rarity.MYSTIC;
        }

        ItemStack book = new ItemStack(Material.ENCHANTED_BOOK, 1);
        ItemMeta meta = book.getItemMeta();
        meta.setDisplayName(resolvedTo.color + "§lFusion Book §8• " + resolvedFrom.colored() + " §8→ " + resolvedTo.colored());
        meta.setLore(List.of(
                "§7Ziehe dieses Buch auf ein Custom-Item.",
                "§8» §7Benötigt: " + resolvedFrom.colored(),
                "§8» §7Upgrade: " + resolvedFrom.colored() + " §8→ " + resolvedTo.colored(),
                "§8» §aSofort-Upgrade beim Auflegen.",
                "§8» §7Level und Fusion-Fortschritt bleiben erhalten.",
                "§8» §7Rolls werden beim Upgrade neu ausgewürfelt.",
                "",
                "§cWird erst beim erfolgreichen Anwenden verbraucht."
        ));
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        meta.getPersistentDataContainer().set(KEY_RARITY_FUSION_FROM(p), PersistentDataType.STRING, resolvedFrom.name());
        meta.getPersistentDataContainer().set(KEY_RARITY_FUSION_TO(p), PersistentDataType.STRING, resolvedTo.name());
        book.setItemMeta(meta);
        return book;
    }

    public static CustomEnchant enchantFromBook(FactionsPlugin p, ItemStack item) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(KEY_ENCHANT_BOOK(p), PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return CustomEnchant.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static Rarity rarityFusionFrom(FactionsPlugin p, ItemStack item) {
        return rarityMeta(p, item, KEY_RARITY_FUSION_FROM(p));
    }

    public static Rarity rarityFusionTo(FactionsPlugin p, ItemStack item) {
        return rarityMeta(p, item, KEY_RARITY_FUSION_TO(p));
    }

    public static Rarity nextRarity(Rarity current) {
        if (current == null) return Rarity.RARE;
        return switch (current) {
            case COMMON -> Rarity.RARE;
            case RARE -> Rarity.EPIC;
            case EPIC -> Rarity.LEGENDARY;
            case LEGENDARY -> Rarity.MYSTIC;
            case MYSTIC -> null;
        };
    }

    public static int enchantBookLevel(FactionsPlugin p, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer value = item.getItemMeta().getPersistentDataContainer().get(KEY_ENCHANT_BOOK_LEVEL(p), PersistentDataType.INTEGER);
        return value == null ? 0 : Math.max(1, Math.min(5, value));
    }

    /** Anzahl Sacrifices, die vom aktuellen Level zum naechsten noetig sind (Level 0->1, 1->2, ...). */
    private static final int[] LEVEL_UP_REQUIRED_DEFAULT = {2, 4, 7, 10, 15};
    /** Coins-Kosten fuer den jeweiligen Level-Up, sobald die Sacrifice-Anzahl erreicht ist. */
    private static final int[] LEVEL_UP_COST_DEFAULT = {400, 1000, 2000, 6000, 20000};

    public static int requiredForNext(int level) {
        return requiredForNext(null, level);
    }

    public static int requiredForNext(FactionsPlugin p, int level) {
        if (level < 0 || level >= LEVEL_UP_REQUIRED_DEFAULT.length) return 0;
        if (p == null) return LEVEL_UP_REQUIRED_DEFAULT[level];
        List<Integer> configured = p.getConfig().getIntegerList("customitems.levelUpRequired");
        if (configured.size() == LEVEL_UP_REQUIRED_DEFAULT.length) return configured.get(level);
        return LEVEL_UP_REQUIRED_DEFAULT[level];
    }

    public static int levelUpCost(FactionsPlugin p, int level) {
        if (level < 0 || level >= LEVEL_UP_COST_DEFAULT.length) return 0;
        List<Integer> configured = p.getConfig().getIntegerList("customitems.levelUpCost");
        if (configured.size() == LEVEL_UP_COST_DEFAULT.length) return configured.get(level);
        return LEVEL_UP_COST_DEFAULT[level];
    }

    public enum SacrificeResult { PROGRESS, LEVEL_UP, MAX_LEVEL, INSUFFICIENT_COINS }

    /**
     * Opfert ein Duplikat auf ein Item: erhoeht den Fortschritt um 1, und sobald genug Sacrifices
     * zusammen sind, wird das Level-Up gegen Coins (levelUpCost) abgeschlossen. Reicht das Geld
     * nicht, bleibt der Fortschritt unveraendert (INSUFFICIENT_COINS) -- der Aufrufer darf das
     * Sacrifice-Item in diesem Fall NICHT verbrauchen, damit dem Spieler nichts verloren geht.
     */
    public static SacrificeResult sacrificeOnto(FactionsPlugin p, Player player, ItemStack base) {
        if (base == null) return SacrificeResult.MAX_LEVEL;

        String id = baseId(p, base);
        if ("dragons_breath".equalsIgnoreCase(id)) {
            return upgradeDragonsBreathOnce(p, base) ? SacrificeResult.LEVEL_UP : SacrificeResult.MAX_LEVEL;
        }

        int lvl = levelOf(p, base);
        int max = maxLevelOf(p, base);
        if (max > 0 && lvl >= max) return SacrificeResult.MAX_LEVEL;

        int need = requiredForNext(p, lvl);
        int fp = fusionPointsOf(p, base) + 1;

        if (need <= 0 || fp < need) {
            setMeta(p, base, id, rarityOf(p, base), lvl, fp);
            return SacrificeResult.PROGRESS;
        }

        int cost = levelUpCost(p, lvl);
        if (p.getCoinsManager() == null || p.getCoinsManager().getCoins(player.getUniqueId()) < cost) {
            return SacrificeResult.INSUFFICIENT_COINS;
        }
        p.getCoinsManager().removeCoins(player.getUniqueId(), cost);
        setMeta(p, base, id, rarityOf(p, base), lvl + 1, 0);
        return SacrificeResult.LEVEL_UP;
    }

    public static boolean isDragonsBreath(FactionsPlugin p, ItemStack it) {
        return "dragons_breath".equalsIgnoreCase(baseId(p, it));
    }

    public static int dragonsBreathMaxLevel() {
        return 5;
    }

    public static int dragonsBreathLevel(FactionsPlugin p, ItemStack it) {
        int lvl = levelOf(p, it);
        return Math.max(1, Math.min(dragonsBreathMaxLevel(), lvl <= 0 ? 1 : lvl));
    }

    public static int dragonsBreathPoints(FactionsPlugin p, ItemStack it) {
        return Math.max(0, fusionPointsOf(p, it));
    }

    public static int dragonsBreathNeedForNext(int level) {
        return switch (Math.max(1, level)) {
            case 1 -> 1;
            case 2 -> 2;
            case 3 -> 3;
            case 4 -> 4;
            default -> 0;
        };
    }

    public static boolean isDragonsBreathMax(FactionsPlugin p, ItemStack it) {
        int lvl = dragonsBreathLevel(p, it);
        return lvl >= dragonsBreathMaxLevel();
    }

    public static int maxLevelOf(FactionsPlugin p, ItemStack it) {
        if (it == null || !it.hasItemMeta()) return 0;

        String id = baseId(p, it);
        if (id == null) return 0;

        if ("dragons_breath".equalsIgnoreCase(id)) {
            return dragonsBreathMaxLevel();
        }

        return Math.max(1, p.getConfig().getInt("customitems.maxLevel", 5));
    }

    public static boolean isMaxLevel(FactionsPlugin p, ItemStack it) {
        if (it == null || !it.hasItemMeta()) return false;

        String id = baseId(p, it);
        if (id == null) return false;

        if ("dragons_breath".equalsIgnoreCase(id)) {
            return isDragonsBreathMax(p, it);
        }

        return levelOf(p, it) >= maxLevelOf(p, it);
    }

    public static boolean upgradeDragonsBreathOnce(FactionsPlugin p, ItemStack item) {
        if (!isDragonsBreath(p, item)) return false;

        int level = dragonsBreathLevel(p, item);
        int points = dragonsBreathPoints(p, item);

        if (level >= dragonsBreathMaxLevel()) {
            setMeta(p, item, "dragons_breath", Rarity.LEGENDARY, dragonsBreathMaxLevel(), 0);
            return false;
        }

        points++;
        int need = dragonsBreathNeedForNext(level);

        while (need > 0 && points >= need && level < dragonsBreathMaxLevel()) {
            points -= need;
            level++;
            need = dragonsBreathNeedForNext(level);
        }

        if (level >= dragonsBreathMaxLevel()) {
            points = 0;
        }

        setMeta(p, item, "dragons_breath", Rarity.LEGENDARY, level, points);
        ensureUniqueInstance(p, item);
        return true;
    }

    public static void ensureUniqueInstance(FactionsPlugin p, ItemStack item) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        PersistentDataContainer c = meta.getPersistentDataContainer();
        if (!c.has(KEY_UID(p), PersistentDataType.STRING)) {
            c.set(KEY_UID(p), PersistentDataType.STRING, UUID.randomUUID().toString());
            item.setItemMeta(meta);
        }
    }

    public static void refreshLore(FactionsPlugin p, ItemStack it) {
        if (it == null) return;
        String base = baseId(p, it);

        if ("dragons_breath".equalsIgnoreCase(base)) {
            refreshDragonsBreathLore(p, it);
            return;
        }

        Rarity r = rarityOf(p, it);
        int lvl = levelOf(p, it);
        int fp = fusionPointsOf(p, it);
        int need = requiredForNext(p, lvl);
        int max = maxLevelOf(p, it);
        boolean maxed = max > 0 && lvl >= max;

        ItemMeta im = it.getItemMeta();
        List<String> lore = new ArrayList<>();

        lore.add(ChatColor.DARK_GRAY + "Rarity: " + r.color + r.display);
        lore.add("§7Level: §f" + lvl + (max > 0 ? "§8/§f" + max : ""));
        if (maxed) {
            lore.add(progressBar(1.0, 18, true));
        } else {
            lore.add("§7Sacrifices: §f" + fp + "§8/§f" + need + " §8(§6" + levelUpCost(p, lvl) + " Coins§8)");
            lore.add(progressBar(need <= 0 ? 1.0 : Math.min(1.0, fp / (double) need), 18, false));
        }

        int ls = getEnchant(p, it, CustomEnchant.LIFESTEAL);
        int bd = getEnchant(p, it, CustomEnchant.BLEED);
        int rg = getEnchant(p, it, CustomEnchant.RAGE);
        int sh = getEnchant(p, it, CustomEnchant.SHATTER);
        int gd = getEnchant(p, it, CustomEnchant.GUARD);

        if (ls > 0) lore.add("§dRune: §5" + CustomEnchant.LIFESTEAL.display + " " + roman(ls));
        if (bd > 0) lore.add("§dRune: §5" + CustomEnchant.BLEED.display + " " + roman(bd));
        if (rg > 0) lore.add("§dRune: §5" + CustomEnchant.RAGE.display + " " + roman(rg));
        if (sh > 0) lore.add("§dRune: §5" + CustomEnchant.SHATTER.display + " " + roman(sh));
        if (gd > 0) lore.add("§dRune: §5" + CustomEnchant.GUARD.display + " " + roman(gd));
        appendItemDetails(p, it, lore);

        im.setLore(lore);
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(im);
    }

    private static void appendItemDetails(FactionsPlugin p, ItemStack item, List<String> lore) {
        double damage = damageRollOf(p, item);
        double coins = coinRollOf(p, item);
        double cooldown = cooldownRollOf(p, item);
        lore.add("");
        lore.add("§7Power Score: §f" + powerScore(p, item));
        if (damage > 0) lore.add("§8» §c+" + pct(damage) + " Damage");
        if (coins > 0) lore.add("§8» §e+" + pct(coins) + " Coins");
        if (cooldown > 0) lore.add("§8» §b-" + pct(cooldown) + " Cooldown");
        if (isSoulbound(p, item)) lore.add("§8» §dSoulbound");
        if (hasUpgradeProtection(p, item)) lore.add("§8» §aUpgrade Protection");
        long lock = tradeLockUntil(p, item);
        if (lock > System.currentTimeMillis()) lore.add("§8» §cTrade-Lock: §f" + Math.max(1L, (lock - System.currentTimeMillis()) / 60000L) + "m");
    }

    public static int powerScore(FactionsPlugin p, ItemStack item) {
        if (item == null || baseId(p, item) == null) return 0;
        int enchant = 0;
        for (CustomEnchant ce : CustomEnchant.values()) enchant += getEnchant(p, item, ce) * 10;
        return rarityOf(p, item).ordinal() * 100 + levelOf(p, item) * 15 + enchant
                + (int) Math.round((damageRollOf(p, item) + coinRollOf(p, item) + cooldownRollOf(p, item)) * 100);
    }

    private static void applyRandomRolls(FactionsPlugin p, ItemStack item, Rarity rarity) {
        if (item == null || !item.hasItemMeta()) return;
        ItemMeta meta = item.getItemMeta();
        applyRandomRolls(meta.getPersistentDataContainer(), p, rarity);
        item.setItemMeta(meta);
    }

    private static void applyRandomRolls(PersistentDataContainer c, FactionsPlugin p, Rarity rarity) {
        double scale = 1.0 + (rarity == null ? 0 : rarity.ordinal() * 0.35);
        c.set(KEY_ROLL_DAMAGE(p), PersistentDataType.DOUBLE, round(ThreadLocalRandom.current().nextDouble(0.01, 0.035) * scale));
        c.set(KEY_ROLL_COINS(p), PersistentDataType.DOUBLE, round(ThreadLocalRandom.current().nextDouble(0.02, 0.06) * scale));
        c.set(KEY_ROLL_COOLDOWN(p), PersistentDataType.DOUBLE, round(ThreadLocalRandom.current().nextDouble(0.01, 0.045) * scale));
    }

    private static double round(double value) { return Math.round(value * 1000.0) / 1000.0; }
    private static String pct(double value) { return String.format(Locale.US, "%.1f%%", value * 100.0); }
    private static String stringMeta(FactionsPlugin p, ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return null;
        return item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
    }
    private static int intMeta(FactionsPlugin p, ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return 0;
        Integer value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.INTEGER);
        return value == null ? 0 : value;
    }
    private static double doubleMeta(FactionsPlugin p, ItemStack item, NamespacedKey key) {
        if (item == null || !item.hasItemMeta()) return 0.0;
        Double value = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.DOUBLE);
        return value == null ? 0.0 : value;
    }
    private static Rarity rarityMeta(FactionsPlugin p, ItemStack item, NamespacedKey key) {
        if (item == null || item.getType() != Material.ENCHANTED_BOOK || !item.hasItemMeta()) return null;
        String raw = item.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (raw == null) return null;
        try {
            return Rarity.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public static void refreshDragonsBreathLore(FactionsPlugin p, ItemStack it) {
        if (it == null) return;

        int level = dragonsBreathLevel(p, it);
        int max = dragonsBreathMaxLevel();
        int fp = dragonsBreathPoints(p, it);
        int need = dragonsBreathNeedForNext(level);
        boolean maxed = level >= max;

        ItemMeta im = it.getItemMeta();
        im.setDisplayName("§6Dragon's Breath");

        List<String> lore = new ArrayList<>();
        lore.add("§8Legendary Ability");
        lore.add("");
        lore.add("§7Rechtsklick: §fFeuerball entfesseln");
        lore.add("§7Level: §e" + level + "§7/§e" + max);
        lore.add("§7Schaden: §c" + formatOne(dbDamage(level)) + " Herzen");
        lore.add("§7Splash-Radius: §6" + formatOne(dbRadius(level)));
        lore.add("§7Feuer: §e" + dbFireSeconds(level) + "s");

        if (level >= 5) lore.add("§7Special Attack: §cFeuerfeld §e(5s)");
        else lore.add("§7Special Attack: §8Ab Level 5");

        lore.add("");
        lore.add(maxed ? "§aMAX LEVEL" : "§6UPGRADE");
        lore.add(progressBar(maxed ? 1.0 : Math.min(1.0, fp / (double) Math.max(1, need)), 15, maxed));
        if (!maxed) {
            lore.add("§8" + fp + "/" + need + " Fusion");
            lore.add("§7Ziehe ein §6Dragon's Breath §7auf dieses Item.");
        }
        lore.add("");
        lore.add("§6LEGENDARY");
        appendItemDetails(p, it, lore);

        im.setLore(lore);
        im.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        it.setItemMeta(im);
    }

    public static double dbDamage(int level) {
        return switch (Math.max(1, Math.min(5, level))) {
            case 1 -> 7.0;
            case 2 -> 10.0;
            case 3 -> 13.0;
            case 4 -> 16.0;
            default -> 19.0;
        };
    }

    public static double dbRadius(int level) {
        return switch (Math.max(1, Math.min(5, level))) {
            case 1 -> 2.5;
            case 2 -> 3.0;
            case 3 -> 3.5;
            case 4 -> 4.0;
            default -> 4.5;
        };
    }

    public static int dbFireSeconds(int level) {
        return switch (Math.max(1, Math.min(5, level))) {
            case 1 -> 3;
            case 2 -> 4;
            case 3 -> 5;
            case 4 -> 6;
            default -> 7;
        };
    }

    private static String formatOne(double value) {
        return String.format(Locale.US, "%.1f", value);
    }

    private static String pctPct(double p) {
        return String.format(Locale.US, "%.0f%%", p * 100.0);
    }

    public static String progressBar(double t, int len, boolean maxed) {
        t = Math.max(0, Math.min(1, t));
        int full = (int) Math.round(len * t);
        StringBuilder sb = new StringBuilder(len * 4);

        for (int i = 0; i < len; i++) {
            if (i < full) sb.append(maxed ? "§a" : "§6");
            else sb.append("§8");
            sb.append('■');
        }
        return sb.toString();
    }

    private static String roman(int n) {
        return switch (Math.max(1, Math.min(10, n))) {
            case 1 -> "I";
            case 2 -> "II";
            case 3 -> "III";
            case 4 -> "IV";
            case 5 -> "V";
            case 6 -> "VI";
            case 7 -> "VII";
            case 8 -> "VIII";
            case 9 -> "IX";
            default -> "X";
        };
    }

    private static String prettyEnchant(CustomEnchant enchant) {
        return switch (enchant) {
            case LIFESTEAL -> "Lifesteal";
            case BLEED -> "Bleed";
            case RAGE -> "Rage";
            case SHATTER -> "Shatter";
            case GUARD -> "Guard";
        };
    }

    public static ItemStack makeShadowblade(FactionsPlugin p, Rarity r) {
        return makeCombatItem(p, "shadowblade", "Shadowblade", Material.NETHERITE_SWORD, r,
                tier(r, 0, 1, 1, 2, 3),
                tier(r, 0, 0, 1, 1, 2),
                tier(r, 0, 0, 0, 1, 2),
                0,
                0);
    }

    public static ItemStack makeWarPickaxe(FactionsPlugin p, Rarity r) {
        Rarity resolved = minRarity(r, Rarity.EPIC);
        Material material = switch (resolved) {
            case MYSTIC -> Material.NETHERITE_PICKAXE;
            case LEGENDARY -> Material.DIAMOND_PICKAXE;
            default -> Material.IRON_PICKAXE;
        };
        ItemStack item = makeCombatItem(p, "war_pickaxe", "Breach Pick", material, resolved,
                tier(resolved, 0, 0, 1, 1, 2),
                0,
                tier(resolved, 0, 0, 1, 2, 3),
                tier(resolved, 0, 0, 1, 2, 3),
                0);
        try { item.addUnsafeEnchantment(Enchantment.EFFICIENCY, tier(resolved, 0, 0, 2, 3, 4)); } catch (Throwable ignored) {}
        refreshLore(p, item);
        return item;
    }

    public static ItemStack makeBattleHoe(FactionsPlugin p, Rarity r) {
        Rarity resolved = maxRarity(r, Rarity.EPIC);
        Material material = switch (resolved) {
            case EPIC -> Material.DIAMOND_HOE;
            case RARE -> Material.IRON_HOE;
            default -> Material.STONE_HOE;
        };
        ItemStack item = makeCombatItem(p, "battle_hoe", "Battle Hoe", material, resolved,
                tier(resolved, 0, 0, 1, 0, 0),
                tier(resolved, 1, 2, 3, 0, 0),
                tier(resolved, 0, 1, 2, 0, 0),
                tier(resolved, 0, 0, 1, 0, 0),
                0);
        try { item.addUnsafeEnchantment(Enchantment.SHARPNESS, tier(resolved, 1, 2, 3, 0, 0)); } catch (Throwable ignored) {}
        refreshLore(p, item);
        return item;
    }

    public static ItemStack makeWarHoe(FactionsPlugin p, Rarity r) {
        Rarity resolved = r == null ? Rarity.COMMON : r;
        Material material = switch (resolved) {
            case MYSTIC, LEGENDARY -> Material.NETHERITE_HOE;
            case EPIC -> Material.DIAMOND_HOE;
            case RARE -> Material.IRON_HOE;
            default -> Material.STONE_HOE;
        };
        return makeCombatItem(p, "war_hoe", "War Hoe", material, resolved,
                tier(resolved, 0, 1, 1, 1, 1),
                tier(resolved, 1, 2, 3, 3, 3),
                tier(resolved, 0, 0, 1, 1, 1),
                0,
                0);
    }

    public static ItemStack makeReaperScythe(FactionsPlugin p, Rarity r) {
        return makeCombatItem(p, "reaper_scythe", "Reaper Scythe", Material.NETHERITE_HOE, r,
                tier(r, 0, 1, 1, 2, 3),
                tier(r, 0, 1, 2, 3, 4),
                0,
                tier(r, 0, 0, 0, 1, 2),
                0);
    }

    public static ItemStack makeTitanbreaker(FactionsPlugin p, Rarity r) {
        return makeCombatItem(p, "titanbreaker", "Titanbreaker", Material.NETHERITE_AXE, r,
                0,
                0,
                tier(r, 0, 1, 2, 3, 4),
                tier(r, 0, 1, 2, 3, 4),
                tier(r, 0, 0, 1, 2, 3));
    }

    public static ItemStack makeFrostfang(FactionsPlugin p, Rarity r) {
        return makeCombatItem(p, "frostfang", "Frostfang", Material.DIAMOND_SWORD, r,
                tier(r, 0, 0, 1, 1, 2),
                tier(r, 0, 1, 1, 2, 3),
                0,
                tier(r, 0, 0, 1, 2, 3),
                tier(r, 0, 0, 0, 1, 2));
    }

    public static ItemStack makeStormcaller(FactionsPlugin p) {
        return makeCombatItem(p, "stormcaller", "Stormcaller", Material.NETHERITE_AXE, Rarity.MYSTIC,
                2, 2, 3, 3, 2);
    }

    public static ItemStack makeArmorPiece(FactionsPlugin p, String setId, Material material, Rarity rarity) {
        Rarity resolved = rarity == null ? Rarity.RARE : rarity;
        String display = switch (setId.toLowerCase(Locale.ROOT)) {
            case "shadow" -> "Shadow";
            case "titan" -> "Titan";
            case "ember" -> "Ember";
            case "void" -> "Void";
            case "raider" -> "Raider";
            case "guardian" -> "Guardian";
            case "berserker" -> "Berserker";
            case "prospector" -> "Prospector";
            case "season_one" -> "Season I";
            default -> Character.toUpperCase(setId.charAt(0)) + setId.substring(1);
        };
        String piece = switch (material) {
            case NETHERITE_HELMET, DIAMOND_HELMET -> "Helmet";
            case NETHERITE_CHESTPLATE, DIAMOND_CHESTPLATE -> "Chestplate";
            case NETHERITE_LEGGINGS, DIAMOND_LEGGINGS -> "Leggings";
            case NETHERITE_BOOTS, DIAMOND_BOOTS -> "Boots";
            default -> "Armor";
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(resolved.color + display + " " + piece);
        meta.getPersistentDataContainer().set(KEY_ARMOR_SET(p), PersistentDataType.STRING, setId.toLowerCase(Locale.ROOT));
        item.setItemMeta(meta);
        setMeta(p, item, setId.toLowerCase(Locale.ROOT) + "_" + piece.toLowerCase(Locale.ROOT), resolved, 0, 0);
        refreshArmorLore(p, item, display);
        return item;
    }

    public static ItemStack makeEventItem(FactionsPlugin p, String eventId) {
        String normalized = eventId.toLowerCase(Locale.ROOT);
        Material material = switch (normalized) {
            case "halloween_reaper" -> Material.NETHERITE_HOE;
            case "winter_star" -> Material.NETHER_STAR;
            case "lunar_relic" -> Material.ENDER_EYE;
            case "anniversary_core" -> Material.HEART_OF_THE_SEA;
            default -> Material.ECHO_SHARD;
        };
        String name = switch (normalized) {
            case "halloween_reaper" -> "Halloween Reaper";
            case "winter_star" -> "Winter Star";
            case "lunar_relic" -> "Lunar Relic";
            case "anniversary_core" -> "Anniversary Core";
            default -> "Event Relic";
        };

        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§d" + name);
        meta.getPersistentDataContainer().set(KEY_EVENT(p), PersistentDataType.STRING, normalized);
        meta.getPersistentDataContainer().set(KEY_TRADE_LOCK_UNTIL(p), PersistentDataType.LONG,
                System.currentTimeMillis() + p.getConfig().getLong("customitems.eventDrops.tradeLockHours", 6L) * 3_600_000L);
        item.setItemMeta(meta);
        setMeta(p, item, normalized, Rarity.MYSTIC, 0, 0);
        setEnchantIfPresent(p, item, CustomEnchant.LIFESTEAL, normalized.equals("halloween_reaper") ? 3 : 1);
        setEnchantIfPresent(p, item, CustomEnchant.BLEED, normalized.equals("halloween_reaper") ? 4 : 1);
        setEnchantIfPresent(p, item, CustomEnchant.RAGE, normalized.equals("winter_star") ? 3 : 2);
        setEnchantIfPresent(p, item, CustomEnchant.GUARD, normalized.equals("winter_star") ? 3 : 1);
        refreshLore(p, item);
        ItemMeta refreshed = item.getItemMeta();
        List<String> lore = refreshed.hasLore() && refreshed.getLore() != null
                ? new ArrayList<>(refreshed.getLore())
                : new ArrayList<>();
        lore.add(0, "§dLimited Event Item");
        refreshed.setLore(lore);
        item.setItemMeta(refreshed);
        return item;
    }

    public static ItemStack makeEventToken(FactionsPlugin p, int amount) {
        ItemStack item = new ItemStack(Material.SUNFLOWER, Math.max(1, amount));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§6Event Token");
        meta.setLore(List.of("§7Währung für den §f/eventshop", "§8Erhältlich aus Event-Meteoren."));
        meta.getPersistentDataContainer().set(KEY_EVENT_TOKEN(p), PersistentDataType.INTEGER, 1);
        item.setItemMeta(meta);
        return item;
    }

    public static boolean isEventToken(FactionsPlugin p, ItemStack item) {
        return intMeta(p, item, KEY_EVENT_TOKEN(p)) > 0;
    }

    private static void refreshArmorLore(FactionsPlugin p, ItemStack item, String display) {
        ItemMeta meta = item.getItemMeta();
        Rarity rarity = rarityOf(p, item);
        List<String> lore = new ArrayList<>();
        lore.add("§8Rarity: " + rarity.color + rarity.display);
        lore.add("§7Set: §f" + display + " Armor");
        lore.add("");
        lore.add("§72/4 Set-Bonus §8• §fGrundeffekt");
        lore.add("§74/4 Set-Bonus:");
        switch (armorSetOf(p, item)) {
            case "shadow" -> lore.add("§8» §dSpeed §7+ §cStrength");
            case "titan" -> lore.add("§8» §bResistance §7+ §6Absorption");
            case "ember" -> lore.add("§8» §6Fire Resistance §7+ §eHaste");
            case "void" -> lore.add("§8» §5Pearl Cooldown §7+ §dBlink");
            case "raider" -> lore.add("§8» §cRaid Damage §7+ §6Breach Power");
            case "guardian" -> lore.add("§8» §bClaim Defense §7+ §aRegeneration");
            case "berserker" -> lore.add("§8» §4Low Health Damage §7+ §cSpeed");
            case "prospector" -> lore.add("§8» §eExtra Chest Resources §7+ §6Coins");
            case "season_one" -> lore.add("§8» §6Season I Legacy Aura");
            default -> lore.add("§8» §7Unknown");
        }
        lore.add("§7Einmalige Full-Set-Belohnung: §eCoins");
        appendItemDetails(p, item, lore);
        meta.setLore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS);
        item.setItemMeta(meta);
    }

    private static ItemStack makeCombatItem(
            FactionsPlugin p,
            String id,
            String name,
            Material material,
            Rarity rarity,
            int lifesteal,
            int bleed,
            int rage,
            int shatter,
            int guard
    ) {
        Rarity resolved = rarity == null ? Rarity.RARE : rarity;
        ItemStack item = new ItemStack(material, 1);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(resolved.color + name);
        item.setItemMeta(meta);

        setMeta(p, item, id, resolved, 0, 0);
        setEnchantIfPresent(p, item, CustomEnchant.LIFESTEAL, lifesteal);
        setEnchantIfPresent(p, item, CustomEnchant.BLEED, bleed);
        setEnchantIfPresent(p, item, CustomEnchant.RAGE, rage);
        setEnchantIfPresent(p, item, CustomEnchant.SHATTER, shatter);
        setEnchantIfPresent(p, item, CustomEnchant.GUARD, guard);
        refreshLore(p, item);
        return item;
    }

    private static void setEnchantIfPresent(FactionsPlugin p, ItemStack item, CustomEnchant enchant, int level) {
        if (level > 0) setEnchant(p, item, enchant, level);
    }

    private static int tier(Rarity rarity, int common, int rare, int epic, int legendary, int mystic) {
        return switch (rarity == null ? Rarity.RARE : rarity) {
            case COMMON -> common;
            case RARE -> rare;
            case EPIC -> epic;
            case LEGENDARY -> legendary;
            case MYSTIC -> mystic;
        };
    }

    private static Rarity minRarity(Rarity rarity, Rarity minimum) {
        Rarity safe = rarity == null ? minimum : rarity;
        return safe.ordinal() < minimum.ordinal() ? minimum : safe;
    }

    private static Rarity maxRarity(Rarity rarity, Rarity maximum) {
        Rarity safe = rarity == null ? maximum : rarity;
        return safe.ordinal() > maximum.ordinal() ? maximum : safe;
    }

    public static ItemStack makeDragonsBreath(FactionsPlugin p) {
        ItemStack it = new ItemStack(Material.BLAZE_ROD, 1);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName("§6Dragon's Breath");
        im.getPersistentDataContainer().set(KEY_UID(p), PersistentDataType.STRING, UUID.randomUUID().toString());
        it.setItemMeta(im);

        setMeta(p, it, "dragons_breath", Rarity.LEGENDARY, 1, 0);
        refreshDragonsBreathLore(p, it);
        return it;
    }
}
