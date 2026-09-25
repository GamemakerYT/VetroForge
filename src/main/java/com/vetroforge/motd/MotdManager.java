// src/main/java/com/vetroforge/motd/MotdManager.java
package com.vetroforge.motd;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.GradientUtil;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;

import java.time.Clock;
import java.util.*;
import java.util.function.IntSupplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MotdManager {

    private final FactionsPlugin plugin;
    private final Clock clock = Clock.systemDefaultZone();

    private final Map<String, MotdProfile> profiles = new LinkedHashMap<>();
    private List<MotdRule> rules = new ArrayList<>();

    private String activeProfileId = "default";
    private boolean manualMode = false;
    private boolean proMode = false;

    // Lieferant für „Spieler in der Warzone“
    private IntSupplier warzoneCounter = () -> 0;

    // Rendering-Optionen
    private boolean centerLines = true;
    private int centerWidth = 48;           // grobe Breite zum Zentrieren (Serverliste ist nicht monospace – reicht als Annäherung)
    private int warzoneBarWidth = 16;       // Länge der WZ-Progressbar

    public MotdManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        reload();
    }

    /* ================= Public wiring ================= */

    /** Setzt den Lieferanten für die aktuelle Warzone-Onlinezahl. */
    public void setWarzoneCounter(IntSupplier supplier) {
        this.warzoneCounter = (supplier != null) ? supplier : (() -> 0);
    }

    /* ================= Load / State ================= */

    public void reload() {
        profiles.clear();
        rules = new ArrayList<>();

        // Rendering-Flags
        centerLines    = plugin.getConfig().getBoolean("motd.center.enabled", true);
        centerWidth    = Math.max(20, plugin.getConfig().getInt("motd.center.width", 48));
        warzoneBarWidth= Math.max(6,  plugin.getConfig().getInt("motd.warzoneBar.width", 16));

        // Profile
        ConfigurationSection pSec = plugin.getConfig().getConfigurationSection("motd.profiles");
        if (pSec != null) {
            for (String id : pSec.getKeys(false)) {
                List<String> lines = pSec.getStringList(id + ".lines");
                if (lines == null || lines.isEmpty()) {
                    String single = pSec.getString(id + ".line", "&6VetroForge &8• &7Welcome");
                    lines = new ArrayList<>(); lines.add(single);
                }
                profiles.put(id, new MotdProfile(id, lines));
            }
        }

        // Fallback-Profile
        profiles.putIfAbsent("default", new MotdProfile("default",
                Arrays.asList(
                        "{brand}", // hübscher Brand via GradientUtil
                        "&7Online: &f{online}&7/&f{max} &8| &7WZ: &f{warzone} &8({warzone_pct}%) {warzone_bar}"
                )));

        profiles.putIfAbsent("pro", new MotdProfile("pro",
                Arrays.asList(
                        "{grad:#ff2a2a:#ffcc00|VetroForge • PRO}",
                        "&7Warzone: &f{warzone} &8| &7Spawn: &f{spawn} &8| &7Online: &f{online}&7/&f{max}"
                )));

        // Flags
        manualMode = plugin.getConfig().getBoolean("motd.manualMode", false);
        proMode    = plugin.getConfig().getBoolean("motd.proMode.enabled", false);
        String cfgActive = plugin.getConfig().getString("motd.activeProfile", "default");
        activeProfileId = profiles.containsKey(cfgActive) ? cfgActive : "default";

        // Rules (Auto-Umschaltung)
        rules = MotdRule.fromConfig(plugin.getConfig().getConfigurationSection("motd.rules"));
    }

    public void setManualProfile(String id) {
        if (!profiles.containsKey(id)) throw new IllegalArgumentException("Unknown profile: " + id);
        manualMode = true;
        activeProfileId = id;
        plugin.getConfig().set("motd.manualMode", true);
        plugin.getConfig().set("motd.activeProfile", id);
        plugin.saveConfig();
    }

    public void useAutoMode() {
        manualMode = false;
        plugin.getConfig().set("motd.manualMode", false);
        plugin.saveConfig();
    }

    public void setProMode(boolean enabled) {
        proMode = enabled;
        plugin.getConfig().set("motd.proMode.enabled", enabled);
        plugin.saveConfig();
    }

    public boolean isProMode() { return proMode; }
    public boolean isManualMode() { return manualMode; }
    public String getActiveProfileId() { return activeProfileId; }
    public Collection<MotdProfile> getProfiles() { return Collections.unmodifiableCollection(profiles.values()); }

    /* ================= Selection ================= */

    /** Wähle automatisch ein Profil anhand Rules/Pro-Flag. */
    public void selectAuto(int online) {
        if (manualMode) return;

        // Pro überschreibt alles, wenn Profil existiert
        if (proMode && profiles.containsKey("pro")) {
            activeProfileId = "pro";
            return;
        }

        // Regel-basierte Auswahl: erste passende Regel gewinnt (Top-Down)
        for (MotdRule r : rules) {
            if (!profiles.containsKey(r.targetProfile)) continue;
            if (r.matchesNow(online, clock)) {
                activeProfileId = r.targetProfile;
                return;
            }
        }

        activeProfileId = profiles.containsKey("default") ? "default" : activeProfileId;
    }

    /* ================= Render ================= */

    public String renderMotd(int online, int defaultMax) {
        MotdProfile p = profiles.getOrDefault(activeProfileId, profiles.get("default"));
        List<String> L = (p != null && p.lines != null && !p.lines.isEmpty())
                ? p.lines
                : Arrays.asList("&6VetroForge", "&7Welcome");

        int shownMax = computeShownMax(online, defaultMax);

        String l1 = replaceAll(L.get(0), online, shownMax);
        String l2 = replaceAll(L.size() >= 2 ? L.get(1) : "", online, shownMax);

        // zentrieren (nach Color-Translation, damit sichtbare Länge stimmt)
        if (centerLines) {
            l1 = center(l1, centerWidth);
            l2 = center(l2, centerWidth);
        }

        return l1 + "\n" + l2;
    }

    /* ---- Platzhalter & Styling ---- */

    private String replaceAll(String raw, int online, int shownMax) {
        if (raw == null) raw = "";

        // Zahlen ermitteln
        int wz = 0;
        try { wz = Math.max(0, Math.min(online, warzoneCounter.getAsInt())); } catch (Throwable ignored) {}
        int spawn = Math.max(0, online - wz);
        int pct = (online <= 0) ? 0 : (int)Math.round((wz * 100.0) / Math.max(1, online));

        // Standard-Platzhalter
        String s = raw
                .replace("{online}", String.valueOf(online))
                .replace("{max}", String.valueOf(shownMax))
                .replace("{mode}", proMode ? "PRO" : "NORMAL")
                .replace("{profile}", String.valueOf(activeProfileId))
                .replace("{arrow}", "»")
                .replace("{warzone}", String.valueOf(wz))
                .replace("{wz}", String.valueOf(wz))
                .replace("{spawn}", String.valueOf(spawn))
                .replace("{warzone_pct}", String.valueOf(pct));

        // Komfort-Tags
        s = s.replace("{brand}", GradientUtil.brandRed(" VetroForge "));           // hübscher Marken-Titel
        s = s.replace("{warzone_title}", GradientUtil.warzoneTitle(" WARZONE "));  // Warzone-Gradient-Title

        // Progressbar für Warzone-Anteil
        s = s.replace("{warzone_bar}", makeBar(pct, warzoneBarWidth));

        // Inline-Gradienten: {grad:#RRGGBB:#RRGGBB|Text}
        s = applyInlineGradients(s);

        // & → § Farbcodes
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private String makeBar(int pct, int width) {
        int filled = (int) Math.round((pct / 100.0) * width);
        filled = Math.max(0, Math.min(width, filled));
        StringBuilder sb = new StringBuilder(width * 2);
        // gefüllt (grün/gelb/rot je nach Wert)
        ChatColor col = (pct >= 66) ? ChatColor.GREEN : (pct >= 33) ? ChatColor.YELLOW : ChatColor.RED;
        for (int i = 0; i < filled; i++) sb.append(col).append('█');
        for (int i = filled; i < width; i++) sb.append(ChatColor.DARK_GRAY).append('█');
        return sb.toString();
    }

    private static final Pattern GRAD_TAG =
            Pattern.compile("\\{grad:#[0-9a-fA-F]{6}:#?[0-9a-fA-F]{6}\\|[^}]+}");

    /** {grad:#ff0000:#ffaa00|TEXT} → farbverlauf */
    private String applyInlineGradients(String s) {
        Matcher m = GRAD_TAG.matcher(s);
        StringBuffer out = new StringBuffer();
        while (m.find()) {
            String tag = m.group();
            // zerlegen
            // Format: {grad:#start:#end|TEXT}
            try {
                int first = tag.indexOf(':');           // {grad:
                int second = tag.indexOf(':', first+1); // #start:
                int pipe = tag.indexOf('|', second+1);  // |TEXT
                int close= tag.lastIndexOf('}');

                String start = tag.substring(first+1, second);
                String end   = tag.substring(second+1, pipe);
                String text  = tag.substring(pipe+1, close);

                String grad = GradientUtil.gradientBold(text, start, end);
                m.appendReplacement(out, Matcher.quoteReplacement(grad));
            } catch (Exception ex) {
                // bei Fehler: Tag unverändert lassen
                m.appendReplacement(out, Matcher.quoteReplacement(tag));
            }
        }
        m.appendTail(out);
        return out.toString();
    }

    private String center(String line, int width) {
        // sichtbare Länge ohne Farbcodes
        String stripped = ChatColor.stripColor(line);
        if (stripped == null) stripped = "";
        int pad = Math.max(0, (width - stripped.length()) / 2);
        if (pad == 0) return line;
        StringBuilder sb = new StringBuilder(pad + line.length());
        for (int i = 0; i < pad; i++) sb.append(' ');
        sb.append(line);
        return sb.toString();
    }

    /* ================= Slot Override ================= */

    public int computeShownMax(int online, int defaultMax) {
        var c = plugin.getConfig();
        if (!c.getBoolean("motd.slotOverride.enabled", false)) return defaultMax;

        String mode = String.valueOf(c.getString("motd.slotOverride.mode", "fixed")).toLowerCase(Locale.ROOT);
        int min = Math.max(1, c.getInt("motd.slotOverride.min", defaultMax));
        int max = Math.max(min, c.getInt("motd.slotOverride.max", defaultMax));
        int out = defaultMax;

        switch (mode) {
            case "fixed":
                out = c.getInt("motd.slotOverride.fixed", defaultMax);
                break;
            case "add":
                out = defaultMax + c.getInt("motd.slotOverride.add", 0);
                break;
            case "grow":
                double f = Math.max(1.0, c.getDouble("motd.slotOverride.factor", 1.10));
                out = (int) Math.ceil(online * f);
                out = Math.max(out, defaultMax);
                break;
            case "reserve":
                out = defaultMax;
                if (c.getBoolean("motd.slotOverride.reserve.enabled", true)
                        && c.getBoolean("motd.slotOverride.reserve.showInMax", true)) {
                    out = defaultMax + Math.max(0, c.getInt("motd.slotOverride.reserve.count", 0));
                }
                break;
            default:
                out = defaultMax;
        }

        if (c.getBoolean("motd.slotOverride.reserve.enabled", false)
                && "grow".equals(mode) && c.getBoolean("motd.slotOverride.reserve.showInMax", true)) {
            out += Math.max(0, c.getInt("motd.slotOverride.reserve.count", 0));
        }

        return Math.max(min, Math.min(max, out));
    }
}