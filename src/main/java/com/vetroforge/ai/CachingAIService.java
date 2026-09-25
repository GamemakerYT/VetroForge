package com.vetroforge.ai;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Dünner LRU-Cache vor einem “realen” AIService. */
public class CachingAIService implements AIService {

    private final AIService delegate;
    private final int maxEntries;
    private final Map<String, String> cache = Collections.synchronizedMap(
            new LinkedHashMap<String, String>(128, 0.75f, true) {
                @Override protected boolean removeEldestEntry(Map.Entry<String, String> eldest) {
                    return size() > maxEntries;
                }
            });

    public CachingAIService(AIService delegate) { this(delegate, 256); }
    public CachingAIService(AIService delegate, int maxEntries) {
        this.delegate = delegate; this.maxEntries = Math.max(64, maxEntries);
    }

    private String key(String sys, String user, int max) {
        return (sys==null?"":sys)+"\n<<>>\n"+(user==null?"":user)+"\n#"+max;
    }

    @Override
    public String prompt(String system, String user, int maxTokens) {
        String k = key(system, user, maxTokens);
        String hit = cache.get(k);
        if (hit != null) return hit;
        String out = delegate.prompt(system, user, maxTokens);
        if (out != null) cache.put(k, out);
        return out;
    }

    @Override public boolean isToxic(String text) { return delegate.isToxic(text); }

    @Override
    public String generateWikiForItem(java.util.Locale locale, String itemName, Map<String, String> meta) {
        String sys = "You create short, structured wiki blurbs for Minecraft items. Output Markdown only.";
        StringBuilder sb = new StringBuilder();
        sb.append("Locale: ").append(locale!=null?locale:"en").append("\n");
        sb.append("Item: ").append(itemName).append("\n");
        if (meta != null && !meta.isEmpty()) {
            sb.append("Meta:\n");
            for (Map.Entry<String,String> e : meta.entrySet()) {
                sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
        }
        return prompt(sys, sb.toString(), 300);
    }

    @Override
    public Map<String, Double> suggestPrices(Map<String, Double> baseBuyPrices, Map<String, Integer> soldWindow,
                                             Map<String, Integer> boughtWindow, double sensitivity) {
        return delegate.suggestPrices(baseBuyPrices, soldWindow, boughtWindow, sensitivity);
    }
}