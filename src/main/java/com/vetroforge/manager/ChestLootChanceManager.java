// src/main/java/com/vetroforge/manager/ChestLootChanceManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Random;

public class ChestLootChanceManager {
    private final FactionsPlugin plugin;
    private final Random rnd = new Random();

    public static record Loot(Material mat, int min, int max, double chance) {}

    public ChestLootChanceManager(FactionsPlugin plugin) { this.plugin = plugin; }

    /** Füllt ein Inventar anhand von chests.loot.<context> */
    public void applyLoot(Inventory inv, String context) {
        if (inv == null || context == null || context.isBlank()) return;

        // optional: Anzahl der Rolls aus der Config, sonst 2..4
        int rollsMin = plugin.getConfig().getInt("chests.loot."+context+".rollsMin",
                plugin.getConfig().getInt("chests.loot.rollsMin", 2));
        int rollsMax = plugin.getConfig().getInt("chests.loot."+context+".rollsMax",
                plugin.getConfig().getInt("chests.loot.rollsMax", 4));
        if (rollsMax < rollsMin) rollsMax = rollsMin;
        int rolls = rollsMin + (rollsMax > rollsMin ? rnd.nextInt(rollsMax - rollsMin + 1) : 0);

        List<Loot> pool = getLootPool(context);
        if (pool.isEmpty()) return;

        for (int i = 0; i < rolls; i++) {
            Loot l = rollOneFrom(pool);
            if (l == null || l.mat() == null) continue;
            int amt = randInt(l.min(), l.max());
            if (amt <= 0) amt = 1;
            inv.addItem(new ItemStack(l.mat(), amt));
        }
    }

    /** Liest alle Einträge unter chests.loot.<context>.* */
    public List<Loot> getLootPool(String context) {
        List<Loot> list = new ArrayList<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("chests.loot." + context);
        if (sec == null) return list;

        for (String key : sec.getKeys(false)) {
            // keys wie "rollsMin/rollsMax" überspringen
            if ("rollsMin".equalsIgnoreCase(key) || "rollsMax".equalsIgnoreCase(key)) continue;

            String base = "chests.loot." + context + "." + key;
            // Support: entweder explizit material/min/max/chance … oder einfacher Materialname direkt als key
            Material m = Material.matchMaterial(plugin.getConfig().getString(base + ".material", key.toUpperCase(Locale.ROOT)));
            int min = plugin.getConfig().getInt(base + ".min", plugin.getConfig().getInt(base + ".amount", 1));
            int max = plugin.getConfig().getInt(base + ".max", Math.max(min, plugin.getConfig().getInt(base + ".amount", 1)));
            double chance = plugin.getConfig().getDouble(base + ".chance", 1.0);
            if (m != null && chance > 0) list.add(new Loot(m, Math.max(1,min), Math.max(1,max), chance));
        }
        return list;
    }

    /** Weighted Pick aus Pool (Chance-Feld) */
    private Loot rollOneFrom(List<Loot> pool) {
        if (pool == null || pool.isEmpty()) return null;
        double total = 0.0;
        for (Loot l : pool) total += Math.max(0.0, l.chance());
        if (total <= 0.0) return pool.get(rnd.nextInt(pool.size()));

        double r = rnd.nextDouble() * total;
        double run = 0.0;
        for (Loot l : pool) {
            run += Math.max(0.0, l.chance());
            if (r <= run) return l;
        }
        return pool.get(pool.size() - 1);
    }

    private int randInt(int a, int b) {
        int lo = Math.min(a, b), hi = Math.max(a, b);
        return lo + (hi > lo ? rnd.nextInt(hi - lo + 1) : 0);
    }
}