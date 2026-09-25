package com.vetroforge.ai;

import com.vetroforge.FactionsPlugin;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Sehr einfache, lokale Implementation von AIService:
 * - prompt(): echo-ähnliche Kurzantwort (keine externen APIs)
 * - isToxic(): naive Keyword-Prüfung
 * - generateWikiForItem(): kompaktes Markdown mit Meta-Daten
 * - suggestPrices(): simple dynamische Preis-Anpassung basierend auf Nachfrage
 */
public class SimpleAIService implements AIService {

    private final FactionsPlugin plugin;

    // sehr grobe Keyword-Liste (nur Demo)
    private static final Set<String> TOXIC_WORDS = new HashSet<>(Arrays.asList(
            "hate","kill","suicide","nazi","racist","slur","terror","bomb","ddos","dox"
    ));

    public SimpleAIService(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public String prompt(String system, String user, int maxTokens) {
        if (user == null) return "";
        String base = user.trim();
        if (base.isEmpty()) return "Okay!";
        // “maxTokens” hier als einfache Längenbegrenzung interpretiert
        int limit = Math.max(32, Math.min(512, maxTokens));
        if (base.length() > limit) {
            base = base.substring(0, limit).trim();
            if (!base.endsWith("…")) base += "…";
        }
        // Mini-Helferantwort (ohne externe KI)
        return "Kurzantwort: " + base;
    }

    @Override
    public boolean isToxic(String text) {
        if (text == null || text.isBlank()) return false;
        String l = text.toLowerCase(Locale.ROOT);
        for (String w : TOXIC_WORDS) {
            if (l.contains(w)) return true;
        }
        return false;
    }

    @Override
    public String generateWikiForItem(Locale locale, String itemName, Map<String, String> meta) {
        String lang = (locale == null ? "en" : locale.getLanguage());
        String title = (itemName == null || itemName.isBlank()) ? "Unknown Item" : itemName;

        StringBuilder md = new StringBuilder();
        md.append("# ").append(title).append("\n\n");

        if ("de".equalsIgnoreCase(lang)) {
            md.append("Kurzübersicht zum Item für deinen Server.\n\n");
            md.append("## Eigenschaften\n");
        } else {
            md.append("Quick overview for your server.\n\n");
            md.append("## Properties\n");
        }

        if (meta != null && !meta.isEmpty()) {
            for (Map.Entry<String,String> e : meta.entrySet()) {
                md.append("- **").append(e.getKey()).append("**: ").append(e.getValue()).append("\n");
            }
        } else {
            md.append("- *(none)*\n");
        }

        md.append("\n");
        if ("de".equalsIgnoreCase(lang)) {
            md.append("## Hinweise\n- Werte sind Beispielangaben und sollten serverseitig geprüft werden.\n");
        } else {
            md.append("## Notes\n- Values are examples and should be validated server-side.\n");
        }
        return md.toString();
    }

    @Override
    public Map<String, Double> suggestPrices(Map<String, Double> baseBuyPrices,
                                             Map<String, Integer> soldWindow,
                                             Map<String, Integer> boughtWindow,
                                             double sensitivity) {
        Map<String, Double> out = new HashMap<>();
        if (baseBuyPrices == null || baseBuyPrices.isEmpty()) return out;

        // Klemme Sensitivität in einen sinnvollen Bereich (0.05 .. 3.0)
        double s = Math.max(0.05, Math.min(3.0, sensitivity));

        for (Map.Entry<String, Double> e : baseBuyPrices.entrySet()) {
            String id = e.getKey();
            double base = Math.max(0.01, e.getValue() == null ? 0.01 : e.getValue());

            int sold   = soldWindow   != null ? soldWindow.getOrDefault(id, 0)   : 0;
            int bought = boughtWindow != null ? boughtWindow.getOrDefault(id, 0) : 0;

            // Nachfrage ≈ mehr Verkäufe (sold) als Einkäufe (bought) → Preis hoch
            int demand = sold - bought;

            // Sanfte Begrenzung via tanh; *0.2 als Grundamplitude; *s für Sensitivität
            double factor = 1.0 + Math.tanh(demand / 10.0) * 0.2 * s;

            double newPrice = base * factor;
            // runde auf 2 Nachkommastellen
            newPrice = Math.round(newPrice * 100.0) / 100.0;
            if (newPrice < 0.01) newPrice = 0.01;

            out.put(id, newPrice);
        }
        return out;
    }
}