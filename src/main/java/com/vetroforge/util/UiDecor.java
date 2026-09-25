package com.vetroforge.util;

import org.bukkit.ChatColor;

/** Kleine Helfer für hübsche Chat-/Titel-Zierleisten und Standard-Prefixe */
public final class UiDecor {
    private UiDecor(){}

    public static final String ARROW = " §8» §7";

    /** Titelzeile: ■──── TITLE ────■ */
    public static String boxTitle(String title) {
        String mid = title == null ? "" : title.trim();
        return ChatColor.DARK_GRAY + "■" + ChatColor.DARK_GRAY + ChatColor.STRIKETHROUGH + "──────────"
                + ChatColor.RESET + "  " + mid + "  "
                + ChatColor.DARK_GRAY + ChatColor.STRIKETHROUGH + "──────────" + ChatColor.DARK_GRAY + "■" + ChatColor.RESET;
    }

    /** Dünne Linie */
    public static String line() {
        return ChatColor.DARK_GRAY + "" + ChatColor.STRIKETHROUGH + "────────────────────────────────" + ChatColor.RESET;
    }

    /** Kleine Standardmarker */
    public static String ok()  { return ChatColor.GREEN + "✔ " + ChatColor.GRAY; }
    public static String err() { return ChatColor.RED   + "✖ " + ChatColor.GRAY; }
    public static String pfx() { return ChatColor.DARK_GRAY + "• " + ChatColor.GRAY; }

    /** Allgemeiner Prefix-Builder */
    public static String prefix(String labelColor, String label, String msg) {
        return color(labelColor) + label + ARROW + ChatColor.translateAlternateColorCodes('&', msg == null ? "" : msg);
    }

    public static String faction(String msg) {
        return ChatColor.GOLD + "Faction" + ARROW + ChatColor.translateAlternateColorCodes('&', msg == null ? "" : msg);
    }

    public static String raid(String msg) {
        return ChatColor.RED + "Raid" + ARROW + ChatColor.translateAlternateColorCodes('&', msg == null ? "" : msg);
    }

    public static String crystals(String msg) {
        return ChatColor.LIGHT_PURPLE + "Crystals" + ARROW + ChatColor.translateAlternateColorCodes('&', msg == null ? "" : msg);
    }

    public static String energy(String msg) {
        return ChatColor.YELLOW + "Energy" + ARROW + ChatColor.translateAlternateColorCodes('&', msg == null ? "" : msg);
    }

    public static String shop(String msg) {
        return ChatColor.GREEN + "Shop" + ARROW + ChatColor.translateAlternateColorCodes('&', msg == null ? "" : msg);
    }

    public static String error(String msg) {
        return ChatColor.RED + "Fehler" + ARROW + ChatColor.translateAlternateColorCodes('&', msg == null ? "" : msg);
    }

    /** Einheitliche "keine Berechtigung"-Meldung für alle Commands. */
    public static String noPermission() {
        return ChatColor.RED + "Keine Rechte.";
    }

    /** Einheitliche "nur ingame"-Meldung für alle Commands. */
    public static String playersOnly() {
        return ChatColor.RED + "Nur Spieler.";
    }

    private static ChatColor color(String input) {
        if (input == null) return ChatColor.WHITE;
        try {
            return ChatColor.valueOf(input.toUpperCase());
        } catch (Exception ignored) {
            return ChatColor.WHITE;
        }
    }
}