package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

/**
 * Kümmert sich um das Erstellen von Tickets und sendet sie per Discord Webhook.
 * Nutzt den zentralen WebhookManager (kein direkter OkHttp-Import hier).
 */
public class TicketManager {

    private final FactionsPlugin plugin;
    private final WebhookManager webhook;
    private final String webhookUrl;

    public TicketManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.webhook = plugin.getWebhookManager(); // zentraler Client
        this.webhookUrl = plugin.getConfig().getString("tickets.webhookUrl", "");
    }

    /**
     * Öffentliche API, die von /ticket verwendet wird.
     *
     * @param author  Spielername
     * @param message Inhalt des Tickets (einzeilig/mehrzeilig möglich)
     * @return true, wenn ein Versand versucht wurde; false bei fehlender URL/Konfig
     */
    public boolean createTicket(String author, String message) {
        if (webhookUrl == null || webhookUrl.isBlank()) {
            plugin.getLogger().warning("[TicketManager] tickets.webhookUrl fehlt/leer – Ticket wird nicht gesendet.");
            return false;
        }
        if (author == null || author.isBlank()) author = "Unknown";
        if (message == null || message.isBlank()) message = "(no content)";

        String time = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                .withZone(ZoneId.systemDefault())
                .format(Instant.now());

        // Discord „content“ – hier simpel gehalten. Du kannst auch Embeds senden.
        String content = "**New ticket**\n"
                + "**Player:** " + author + "\n"
                + "**Time:** " + time + "\n"
                + "**Server:** " + Bukkit.getServer().getName() + "\n"
                + "```\n" + message + "\n```";

        // Minimal-JSON ohne externe Lib
        String json = "{\"content\":\"" + escapeJson(content) + "\"}";

        webhook.postJson(webhookUrl, json);
        return true;
    }

    private String escapeJson(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\r", "\\r")
                .replace("\n", "\\n");
    }
}