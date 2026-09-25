// src/main/java/com/vetroforge/ai/NopAIService.java
package com.vetroforge.ai;

import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

/** Fallback: keine externen Calls – liefert sichere Defaults. */
public class NopAIService implements AIService {

    @Override
    public String prompt(String system, String user, int maxTokens) {
        return "[AI disabled] " + (user == null ? "" : user);
    }

    @Override
    public boolean isToxic(String text) {
        return false; // nichts blocken, wenn AI aus
    }

    @Override
    public String generateWikiForItem(Locale locale, String itemName, Map<String, String> meta) {
        String lang = (locale != null ? locale.getLanguage() : "en");
        if ("de".equalsIgnoreCase(lang)) {
            return "- **Name:** " + itemName + "\n- **Beschreibung:** Platzhalter (AI deaktiviert).";
        }
        return "- **Name:** " + itemName + "\n- **Description:** Placeholder (AI disabled).";
    }

    @Override
    public Map<String, Double> suggestPrices(Map<String, Double> baseBuyPrices,
                                             Map<String, Integer> soldWindow,
                                             Map<String, Integer> boughtWindow,
                                             double sensitivity) {
        // Keine Anpassung
        return new HashMap<>(baseBuyPrices);
    }
}