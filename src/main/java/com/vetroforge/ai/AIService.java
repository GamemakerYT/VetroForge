// src/main/java/com/vetroforge/ai/AIService.java
package com.vetroforge.ai;

import java.util.Locale;
import java.util.Map;

/** Neutrales Interface für KI-Funktionen im Plugin. */
public interface AIService {

    /** Freiform-Prompt. Gib eine kurze Antwort. */
    String prompt(String system, String user, int maxTokens);

    /** Kurzantwort (nur User-Prompt). */
    default String quick(String userPrompt) {
        return prompt("You are a helpful assistant for a Minecraft Factions server.", userPrompt, 512);
    }

    /** Moderation: true = Text ist problematisch. */
    boolean isToxic(String text);

    /** Wiki-Text (Markdown, kurz), lokalisiert. */
    String generateWikiForItem(Locale locale, String itemName, Map<String, String> meta);

    /** Preisvorschläge: id->neuer Preis, sensitivitätsbasiert. */
    Map<String, Double> suggestPrices(Map<String, Double> baseBuyPrices,
                                      Map<String, Integer> soldWindow,
                                      Map<String, Integer> boughtWindow,
                                      double sensitivity);
}