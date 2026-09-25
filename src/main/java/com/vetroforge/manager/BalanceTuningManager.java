// src/main/java/com/vetroforge/manager/BalanceTuningManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Collections;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.logging.Logger;

/**
 * Balance-/Economy-Tuning.
 * - clampDamage(...) klemmt Schadenswerte basierend auf config
 * - runTuning() passt Shop-Preise basierend auf Kauf-/Verkauf-Logs an
 */
public class BalanceTuningManager {

    private final FactionsPlugin plugin;

    public BalanceTuningManager(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    /** Klemmt einen Schaden zwischen balance.damage.min und balance.damage.max */
    public int clampDamage(int base) {
        int min = Math.max(1, plugin.getConfig().getInt("balance.damage.min", 1));
        int max = Math.max(min, plugin.getConfig().getInt("balance.damage.max", 20));
        return Math.min(max, Math.max(min, base));
    }

    /**
     * Führt das Preis-Tuning aus:
     * - Erhöht Kaufpreise leicht bei hoher Nachfrage (buys)
     * - Erhöht Auszahlpreise leicht bei hohem Angebot (sells)
     * - Erzwingt sell < buy
     * - Speichert Änderungen in config.yml
     */
    @SuppressWarnings("unchecked")
    public void runTuning() {
        Logger log = plugin.getLogger();

        Map<String,Integer> recentBuys  = Collections.emptyMap();
        Map<String,Integer> recentSells = Collections.emptyMap();

        // Versuche Logs aus GeneralShopManager zu ziehen
        try {
            Object gsm = plugin.getGeneralShopManager();
            if (gsm instanceof com.vetroforge.manager.GeneralShopManager) {
                com.vetroforge.manager.GeneralShopManager g =
                        (com.vetroforge.manager.GeneralShopManager) gsm;
                recentSells = g.drainRecent(true);   // true = sellPrices
                recentBuys  = g.drainRecent(false);  // false = buyPrices
            } else if (gsm != null) {
                java.lang.reflect.Method m = gsm.getClass().getMethod("drainRecent", boolean.class);
                recentSells = (Map<String,Integer>) m.invoke(gsm, true);
                recentBuys  = (Map<String,Integer>) m.invoke(gsm, false);
            }
        } catch (Throwable t) {
            log.warning("[BalanceTuning] Konnte Kauf-/Verkauf-Logs nicht lesen: " + t.getMessage());
        }

        if (recentBuys.isEmpty() && recentSells.isEmpty()) {
            log.info("[BalanceTuning] Keine neuen Transaktionsdaten – keine Änderungen.");
            return;
        }

        FileConfiguration cfg = plugin.getConfig();
        Set<String> ids = new HashSet<>();
        ids.addAll(recentBuys.keySet());
        ids.addAll(recentSells.keySet());

        for (String id : ids) {
            int buys  = recentBuys.getOrDefault(id, 0);
            int sells = recentSells.getOrDefault(id, 0);

            String base = "shop.prices."+id+".";
            int buy  = cfg.getInt(base + "buy", 10);
            int sell = cfg.getInt(base + "sell", Math.max(1, buy / 2));

            // Simple Heuristik
            int newBuy  = Math.max(1, buy  + (buys  / 10) - (sells / 20));
            int newSell = Math.max(1, sell + (sells / 10) - (buys  / 20));

            // Sicherstellen: sell < buy
            if (newSell >= newBuy) newSell = Math.max(1, newBuy - 1);

            // Deckel
            newBuy  = Math.min(newBuy,  1_000_000);
            newSell = Math.min(newSell, 1_000_000);

            cfg.set(base + "buy",  newBuy);
            cfg.set(base + "sell", newSell);

            log.info(String.format(Locale.ROOT,
                    "[BalanceTuning] %s: buy %d -> %d | sell %d -> %d (buys=%d, sells=%d)",
                    id, buy, newBuy, sell, newSell, buys, sells));
        }

        plugin.saveConfig();
        log.info("[BalanceTuning] Fertig. Preise gespeichert.");
    }
}