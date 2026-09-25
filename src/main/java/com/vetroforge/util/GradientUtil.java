// src/main/java/com/vetroforge/util/GradientUtil.java
package com.vetroforge.util;

import org.bukkit.ChatColor;

public final class GradientUtil {
    private GradientUtil(){}

    // ========= Preset-Farben =========
    public static final String COL_NPC_START = "#55FF55"; // Grün
    public static final String COL_NPC_END   = "#FFFFFF"; // Weiß

    public static final String COL_WZ_START  = "#FF0000"; // Rot
    public static final String COL_WZ_END    = "#FFA500"; // Orange

    public static final String COL_BRAND_R1  = "#FF0000"; // Rot -> Rot (einfarbig/leicht)
    public static final String COL_BRAND_R2  = "#FF0000";

    public static final String COL_RAID_DARK = "#8B0000"; // Dunkelrot
    public static final String COL_RAID_RED  = "#FF0000"; // Rot

    // ========= Bequeme Preset-Wrapper =========
    public static String npc(String text)               { return gradientBold(text, COL_NPC_START, COL_NPC_END); }
    public static String warzoneTitle(String text)      { return gradientBold(text, COL_WZ_START, COL_WZ_END); }
    public static String brandRed(String text)          { return gradientBold(text, COL_BRAND_R1, COL_BRAND_R2); }
    public static String raidTitle(String text)         { return gradientBold(text, COL_RAID_DARK, COL_RAID_RED); }

    /** Kompatibilitäts-Alias (falls irgendwo noch "gardient" aufgerufen wird). */
    public static String gardient(String text, String startHex, String endHex) {
        return gradient(text, startHex, endHex);
    }

    /** Linearer Verlauf (HEX §x-Codes). */
    public static String gradient(String text, String startHex, String endHex) {
        if (text == null || text.isEmpty()) return "";
        int[] a = parseHex(startHex);
        int[] b = parseHex(endHex);
        int n = text.length();
        if (n == 1) return toLegacyHex(b[0], b[1], b[2]) + text;

        StringBuilder out = new StringBuilder(n * 16);
        for (int i = 0; i < n; i++) {
            double t = (double) i / (double) (n - 1);
            int r = (int) Math.round(lerp(a[0], b[0], t));
            int g = (int) Math.round(lerp(a[1], b[1], t));
            int bl = (int) Math.round(lerp(a[2], b[2], t));
            out.append(toLegacyHex(r, g, bl)).append(text.charAt(i));
        }
        return out.toString();
    }

    /** Verlauf + **bold** (setzt §l nach jedem Farbcode → robust bei §x-Hex). */
    public static String gradientBold(String text, String startHex, String endHex) {
        if (text == null || text.isEmpty()) return "";
        int[] a = parseHex(startHex);
        int[] b = parseHex(endHex);
        int n = text.length();

        StringBuilder out = new StringBuilder(n * 18);
        for (int i = 0; i < n; i++) {
            double t = (n <= 1) ? 0 : (double) i / (double) (n - 1);
            int r = (int) Math.round(lerp(a[0], b[0], t));
            int g = (int) Math.round(lerp(a[1], b[1], t));
            int bl = (int) Math.round(lerp(a[2], b[2], t));
            out.append(toLegacyHex(r, g, bl)).append('§').append('l').append(text.charAt(i));
        }
        return out.toString();
    }

    public static String header(String title, String accent) {
        String bar = "§8——————————————";
        String name = "§cVetro§6Forge";
        String mid  = " §7• " + (title != null && !title.isBlank() ? title : accent) + " §7• ";
        return bar + " " + name + mid + bar;
    }

    public static String color(String s){
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    private static double lerp(int x, int y, double t) { return x + (y - x) * t; }

    private static String toLegacyHex(int r, int g, int b) {
        String hex = String.format("%02X%02X%02X", r, g, b);
        StringBuilder sb = new StringBuilder("§x");
        for (char c : hex.toCharArray()) sb.append('§').append(c);
        return sb.toString();
    }

    private static int[] parseHex(String hex) {
        if (hex == null) hex = "FFFFFF";
        hex = hex.trim();
        if (hex.startsWith("#")) hex = hex.substring(1);
        if (hex.length() != 6)  return new int[]{255,255,255};
        try {
            int val = Integer.parseInt(hex, 16);
            return new int[]{ (val >> 16) & 0xFF, (val >> 8) & 0xFF, val & 0xFF };
        } catch (Exception e) { return new int[]{255,255,255}; }
    }
}