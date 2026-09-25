package com.vetroforge.events.storage;

import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.util.HashMap;
import java.util.Map;

public final class EventMessages {
    public final String start;     // beim Start
    public final String stop;      // manuelles Stoppen
    public final String complete;  // Ziel erreicht
    public final String timeout;   // Zeit abgelaufen
    public final String pause;     // pausiert
    public final String resume;    // fortgesetzt
    public final String progress;  // optional: Zwischenstände / Meilensteine
    public final String milestone;

    public EventMessages(String start, String stop, String complete, String timeout,
                         String pause, String resume, String progress, String milestone) {
        this.start = start; this.stop = stop; this.complete = complete; this.timeout = timeout;
        this.pause = pause; this.resume = resume; this.progress = progress;
        this.milestone = milestone;
    }

    public static EventMessages fromConfig(ConfigurationSection sec) {
        if (sec == null) sec = new org.bukkit.configuration.MemoryConfiguration();

        String pre = "§8[§dEvent§8] §r";
        return new EventMessages(
                sec.getString("start",    pre + "§d{event_name} §7gestartet. {ends_in}"),
                sec.getString("stop",     pre + "§c{event_name} §7gestoppt."),
                sec.getString("complete", pre + "§aAbgeschlossen: §f{event_name} §8(§f{current}§7/§f{goal}§8, §a{pct}%§8)"),
                sec.getString("timeout",  pre + "§eZeit abgelaufen: §f{event_name} §8(§f{current}§7/§f{goal}§8)"),
                sec.getString("pause",    pre + "§ePausiert: §f{event_name}"),
                sec.getString("resume",   pre + "§aFortgesetzt: §f{event_name}"),
                sec.getString("progress", pre + "§bFortschritt: §f{event_name} §8→ §f{current}§7/§f{goal} §8(§b{pct}%§8)"),
                sec.getString("milestone", pre + "§d{event_name} §7hat §f{pct}% §7erreicht!")
        );
    }

    public String format(String template, Map<String, String> ph) {
        String out = template == null ? "" : template;
        for (var e : ph.entrySet()) out = out.replace("{" + e.getKey() + "}", e.getValue());
        return ChatColor.translateAlternateColorCodes('&', out);
    }

    public static Map<String, String> basePlaceholders(String id, String name, long current, long goal, int pct, String endsIn) {
        Map<String, String> m = new HashMap<>();
        m.put("event_id", id == null ? "" : id);
        m.put("event_name", name == null ? "" : name);
        m.put("current", String.valueOf(current));
        m.put("goal", String.valueOf(goal));
        m.put("pct", String.valueOf(pct));
        m.put("ends_in", endsIn == null ? "" : endsIn);
        return m;
    }
}
