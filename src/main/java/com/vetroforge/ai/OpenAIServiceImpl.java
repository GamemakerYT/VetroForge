package com.vetroforge.ai;

import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Minimaler OpenAI-Client über Java HttpClient (keine Extra-Dependencies).
 * Nutzt Chat Completions + Moderation-API.
 *
 * config.yml: ai.key: "<API_KEY>"
 */
public class OpenAIServiceImpl implements AIService {

    private static final String CHAT_URL = "https://api.openai.com/v1/chat/completions";
    private static final String MOD_URL  = "https://api.openai.com/v1/moderations";
    private static final String CHAT_MODEL = "gpt-4o-mini";           // klein & günstig
    private static final String MOD_MODEL  = "omni-moderation-latest";

    private final String apiKey;
    private final HttpClient http;

    public OpenAIServiceImpl(String apiKey) {
        this.apiKey = Objects.requireNonNull(apiKey, "apiKey");
        this.http = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(15))
                .build();
    }

    @Override
    public String prompt(String system, String user, int maxTokens) {
        if (apiKey.isEmpty()) return "[AI key missing]";
        try {
            String body = buildChatBody(system, user, maxTokens);
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(CHAT_URL))
                    .timeout(Duration.ofSeconds(45))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) return "[AI error " + res.statusCode() + "]";
            return extractMessageContent(res.body());
        } catch (Exception ex) {
            return "[AI error: " + ex.getMessage() + "]";
        }
    }

    @Override
    public boolean isToxic(String text) {
        if (apiKey.isEmpty()) return false;
        try {
            String body = "{\"model\":\""+ MOD_MODEL +"\",\"input\":"+ jsonString(text) +"}";
            HttpRequest req = HttpRequest.newBuilder()
                    .uri(URI.create(MOD_URL))
                    .timeout(Duration.ofSeconds(20))
                    .header("Authorization", "Bearer " + apiKey)
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> res = http.send(req, HttpResponse.BodyHandlers.ofString());
            if (res.statusCode() / 100 != 2) return false;
            // sehr simple Auswertung: "flagged": true?
            return res.body().contains("\"flagged\":true");
        } catch (Exception ex) {
            return false;
        }
    }

    @Override
    public String generateWikiForItem(java.util.Locale locale, String itemName, Map<String, String> meta) {
        String sys = "You are a concise wiki writer for a Minecraft Factions server. " +
                "Write short, helpful, non-repetitive Markdown: Title (bold), bullet points (max 5), and a 1-line tip.";
        StringBuilder sb = new StringBuilder();
        sb.append("Language: ").append(locale != null ? locale.getLanguage() : "en").append("\n");
        sb.append("Item: ").append(itemName).append("\n");
        if (meta != null && !meta.isEmpty()) {
            sb.append("Meta:\n");
            for (Map.Entry<String,String> e : meta.entrySet()) {
                sb.append("- ").append(e.getKey()).append(": ").append(e.getValue()).append("\n");
            }
        }
        return prompt(sys, sb.toString(), 280);
    }

    @Override
    public Map<String, Double> suggestPrices(Map<String, Double> baseBuyPrices, Map<String, Integer> soldWindow,
                                             Map<String, Integer> boughtWindow, double sensitivity) {
        // Einfache deterministische Formel (robust, offline) – “AI-hybrid”:
        // price = base * (1 + sens*(boughtIndex - 1)) , boughtIndex ~ Käufe/10
        Map<String, Double> out = new HashMap<String, Double>();
        for (Map.Entry<String, Double> e : baseBuyPrices.entrySet()) {
            String id = e.getKey();
            double base = e.getValue();
            int bought = boughtWindow != null && boughtWindow.containsKey(id) ? boughtWindow.get(id) : 0;
            double idx = 1.0 + (bought / 10.0);
            double price = Math.max(1.0, base * (1.0 + sensitivity * (idx - 1.0)));
            out.put(id, price);
        }
        return out;
    }

    /* ------------- Helpers ------------- */

    private String buildChatBody(String system, String user, int maxTokens) {
        StringBuilder sb = new StringBuilder();
        sb.append("{\"model\":\"").append(CHAT_MODEL).append("\",");
        if (maxTokens > 0) sb.append("\"max_tokens\":").append(maxTokens).append(',');
        sb.append("\"messages\":[");
        if (system != null && !system.isEmpty()) {
            sb.append("{\"role\":\"system\",\"content\":").append(jsonString(system)).append("},");
        }
        sb.append("{\"role\":\"user\",\"content\":").append(jsonString(user!=null?user:"")).append("}");
        sb.append("]}");
        return sb.toString();
    }

    /** winzige JSON-Utility (nur String escaping) */
    private String jsonString(String s) {
        if (s == null) return "null";
        StringBuilder out = new StringBuilder("\"");
        for (int i=0;i<s.length();i++) {
            char c = s.charAt(i);
            switch (c) {
                case '\\': out.append("\\\\"); break;
                case '"':  out.append("\\\""); break;
                case '\n': out.append("\\n");  break;
                case '\r': out.append("\\r");  break;
                case '\t': out.append("\\t");  break;
                default:
                    if (c < 0x20) out.append(String.format("\\u%04x", (int)c));
                    else out.append(c);
            }
        }
        out.append('"');
        return out.toString();
    }

    /** sehr simple Extraktion der ersten message.content */
    private String extractMessageContent(String json) {
        if (json == null) return "";
        // Suche nach ..."message":{"role":"assistant","content":"..."}
        Pattern p = Pattern.compile("\"content\"\\s*:\\s*\"(.*?)\"", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        if (m.find()) {
            String raw = m.group(1);
            return raw.replace("\\n", "\n").replace("\\r","").replace("\\t","\t").replace("\\\"","\"");
        }
        return "";
    }
}