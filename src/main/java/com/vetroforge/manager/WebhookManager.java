package com.vetroforge.manager;

import okhttp3.*;

import java.io.IOException;
import java.time.Duration;

public class WebhookManager {

    private static final MediaType JSON = MediaType.get("application/json; charset=utf-8");
    private final OkHttpClient client;
    private final String userAgent;

    public WebhookManager() {
        this("VetroForge/1.0 (+okhttp)");
    }

    public WebhookManager(String userAgent) {
        this.userAgent = (userAgent == null || userAgent.isBlank()) ? "VetroForge/1.0 (+okhttp)" : userAgent;
        this.client = new OkHttpClient.Builder()
                .callTimeout(Duration.ofSeconds(15))
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    /** Asynchron, feuert in OkHttp-Threadpool. */
    public void postJson(String url, String jsonBody) {
        if (url == null || url.isBlank() || jsonBody == null) return;

        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        client.newCall(req).enqueue(new Callback() {
            @Override public void onFailure(Call call, IOException e) {
                // hier ggf. Plugin-Logger nutzen – absichtlich still
            }
            @Override public void onResponse(Call call, Response response) throws IOException {
                try (response) { /* Body schließen */ }
            }
        });
    }

    /** Synchron (blocking); true wenn 2xx. Praktisch für Tests/Retry-Logik. */
    public boolean postJsonSync(String url, String jsonBody) {
        if (url == null || url.isBlank() || jsonBody == null) return false;

        Request req = new Request.Builder()
                .url(url)
                .header("User-Agent", userAgent)
                .post(RequestBody.create(jsonBody, JSON))
                .build();

        try (Response resp = client.newCall(req).execute()) {
            return resp.isSuccessful();
        } catch (IOException ignored) {
            return false;
        }
    }

    /** Convenience für Discord: baut {"content": "..."} */
    public void postDiscord(String url, String content) {
        String json = "{\"content\":" + escapeJson(content) + "}";
        postJson(url, json);
    }

    private static String escapeJson(String s) {
        if (s == null) return "\"\"";
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            switch (ch) {
                case '\\': sb.append("\\\\"); break;
                case '"' : sb.append("\\\""); break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (ch < 0x20) sb.append(String.format("\\u%04x", (int) ch));
                    else sb.append(ch);
            }
        }
        sb.append('"');
        return sb.toString();
    }

    public OkHttpClient getClient() { return client; }
}