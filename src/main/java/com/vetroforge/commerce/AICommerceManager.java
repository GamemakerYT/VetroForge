// src/main/java/com/vetroforge/commerce/AICommerceManager.java
package com.vetroforge.commerce;

import com.vetroforge.FactionsPlugin;
import org.bukkit.configuration.ConfigurationSection;

import java.util.*;

public class AICommerceManager {

    public static final class Entry {
        public final String id;
        public final double baseBuy, baseSell;  // Basispreise aus der Config
        public double dynBuy, dynSell;          // dynamische Preise (verändern sich)

        public Entry(String id, double baseBuy, double baseSell) {
            this.id = id;
            this.baseBuy = Math.max(0.01, baseBuy);
            this.baseSell = Math.max(0.01, baseSell);
            this.dynBuy = this.baseBuy;
            this.dynSell = this.baseSell;
        }
    }

    private final FactionsPlugin plugin;
    private final Map<String, Entry> map = new LinkedHashMap<>();

    // Schalter & Parameter (konfigurierbar)
    private final boolean enabled;
    private final double minBuy, maxBuy, minSell, maxSell;
    private final double stepBuyPct, stepSellPct;

    public AICommerceManager(FactionsPlugin plugin) {
        this.plugin = plugin;

        // toggles + parameter
        enabled    = plugin.getConfig().getBoolean("shop.dynamic.enabled", true);
        minBuy     = plugin.getConfig().getDouble("shop.dynamic.minBuy", 1.0);
        maxBuy     = plugin.getConfig().getDouble("shop.dynamic.maxBuy", 1_000_000.0);
        minSell    = plugin.getConfig().getDouble("shop.dynamic.minSell", 1.0);
        maxSell    = plugin.getConfig().getDouble("shop.dynamic.maxSell", 1_000_000.0);
        stepBuyPct = plugin.getConfig().getDouble("shop.dynamic.stepBuyPct", 0.02);  // +2% pro Kauf
        stepSellPct= plugin.getConfig().getDouble("shop.dynamic.stepSellPct",0.02);  // -2% pro Verkauf

        loadFromConfig();   // Basispreise
        loadRuntime();      // laufende dynBuy/dynSell aus YAML (falls vorhanden)
    }

    /** Lädt Einträge aus:
     *  - shop.general.items.<id>.* (nur zur ID-Ermittlung)
     *  - shop.prices.<id>.buy / sell (Preise) */
    private void loadFromConfig() {
        ConfigurationSection itemsSec  = plugin.getConfig().getConfigurationSection("shop.general.items");
        ConfigurationSection pricesSec = plugin.getConfig().getConfigurationSection("shop.prices");

        Set<String> ids = new LinkedHashSet<>();
        if (itemsSec  != null) ids.addAll(itemsSec.getKeys(false));
        if (pricesSec != null) ids.addAll(pricesSec.getKeys(false));

        for (String id : ids) {
            double buy  = plugin.getConfig().getDouble("shop.prices."+id+".buy",
                    plugin.getConfig().getDouble("shop."+id+".buy", 10.0));
            double sell = plugin.getConfig().getDouble("shop.prices."+id+".sell",
                    plugin.getConfig().getDouble("shop."+id+".sell", Math.max(1.0, buy * 0.5)));
            map.put(id, new Entry(id, buy, sell));
        }
    }

    /** runtime dynBuy/dynSell laden (persistente Dynamik) */
    public void loadRuntime() {
        ConfigurationSection rt = plugin.getConfig().getConfigurationSection("shop.dynamic.runtime");
        if (rt == null) return;
        for (String id : rt.getKeys(false)) {
            Entry e = get(id);
            if (e == null) continue;
            e.dynBuy  = clamp(rt.getDouble(id + ".buy",  e.baseBuy),  minBuy,  maxBuy);
            e.dynSell = clamp(rt.getDouble(id + ".sell", e.baseSell), minSell, maxSell);
        }
    }

    /** runtime dynBuy/dynSell speichern */
    public void saveRuntime() {
        String root = "shop.dynamic.runtime.";
        // löschen & neu schreiben
        plugin.getConfig().set("shop.dynamic.runtime", null);
        for (Entry e : map.values()) {
            plugin.getConfig().set(root + e.id + ".buy",  round2(e.dynBuy));
            plugin.getConfig().set(root + e.id + ".sell", round2(e.dynSell));
        }
        plugin.saveConfig();
    }

    /** Alle bekannten Einträge (GUI baut damit die Liste). */
    public Collection<Entry> all() {
        return Collections.unmodifiableCollection(map.values());
    }

    /** Holt (oder legt an) einen Eintrag. */
    public Entry get(String id) {
        if (id == null || id.isBlank()) return null;
        return map.computeIfAbsent(id, k -> new Entry(k, 10.0, 5.0));
    }

    /** Spieler hat gekauft → Nachfrage hoch → dynBuy steigt leicht. */
    public void buy(String id, int amount) {
        if (!enabled) return;
        Entry e = get(id);
        if (e == null) return;

        int qty = Math.max(1, amount);
        // proportional zur Menge
        double step = Math.max(0.01, e.dynBuy * stepBuyPct) * qty;
        e.dynBuy  = clamp(e.dynBuy + step,  minBuy,  maxBuy);
        // Optional: dynSell leicht mitziehen (Angebot knapper)
        e.dynSell = clamp(e.dynSell + step * 0.25, minSell, maxSell);
    }

    /** Spieler hat verkauft → Angebot hoch → dynSell sinkt leicht. */
    public void sell(String id, int amount) {
        if (!enabled) return;
        Entry e = get(id);
        if (e == null) return;

        int qty = Math.max(1, amount);
        double step = Math.max(0.01, e.dynSell * stepSellPct) * qty;
        e.dynSell = clamp(e.dynSell - step, minSell, maxSell);
        // Optional: dynBuy leicht gegensteuern (Markt gesättigt)
        e.dynBuy  = clamp(e.dynBuy - step * 0.10, minBuy, maxBuy);
    }

    /* ================== Admin-API (unchanged) ================== */

    public Map<String, Double> proposePricesMap(Map<String, Integer> recent, boolean sell) {
        Map<String, Double> out = new LinkedHashMap<>();
        if (recent == null || recent.isEmpty()) return out;

        for (Map.Entry<String, Integer> e : recent.entrySet()) {
            String id = e.getKey();
            int volume = Math.max(0, e.getValue());
            Entry entry = get(id);
            if (entry == null) continue;

            double current = sell ? entry.dynSell : entry.dynBuy;

            // Skalierung 0..1 (bis 100 Transaktionen), max ±25% Änderung
            double scale = Math.min(1.0, volume / 100.0);
            double factor = sell ? (1.0 - 0.25 * scale) : (1.0 + 0.25 * scale);

            double proposed = current * factor;
            proposed = sell
                    ? clamp(proposed, minSell, maxSell)
                    : clamp(proposed, minBuy,  maxBuy);

            out.put(id, round2(proposed));
        }
        return out;
    }

    public void applyPriceProposals(Map<String, Double> proposals, boolean sell) {
        if (proposals == null || proposals.isEmpty()) return;

        for (Map.Entry<String, Double> e : proposals.entrySet()) {
            String id = e.getKey();
            double price = Math.max(0.01, e.getValue());

            // In Config schreiben
            String path = "shop.prices." + id + "." + (sell ? "sell" : "buy");
            plugin.getConfig().set(path, round2(price));

            // In-Memory anpassen
            Entry entry = get(id);
            if (entry != null) {
                if (sell) entry.dynSell = price; else entry.dynBuy = price;
            }
        }
        plugin.saveConfig();

        // Optional: GeneralShopManager über Reload informieren (falls vorhanden)
        try {
            Object gsm = plugin.getGeneralShopManager();
            if (gsm != null) {
                try { gsm.getClass().getMethod("reloadPrices").invoke(gsm); }
                catch (NoSuchMethodException ignored) { /* kein Reload vorhanden */ }
            }
        } catch (Throwable ignored) { /* best effort */ }
    }

    /* ===== Utils ===== */

    private static double clamp(double v, double lo, double hi) {
        return Math.max(lo, Math.min(hi, v));
    }

    private static double round2(double v) { return Math.round(v * 100.0) / 100.0; }
}