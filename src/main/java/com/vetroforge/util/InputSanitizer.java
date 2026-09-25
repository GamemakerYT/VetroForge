// src/main/java/com/vetroforge/util/InputSanitizer.java
package com.vetroforge.util;

public final class InputSanitizer {
    private InputSanitizer(){}

    /** Entfernt Farben, trimmt, begrenzt Länge, verbietet Steuerzeichen. */
    public static String sanitize(String raw, int maxLen) {
        if (raw == null) return "";
        String s = raw.replaceAll("(?i)§[0-9A-FK-OR]", "");
        s = s.replaceAll("\\p{Cntrl}", "");
        s = s.trim();
        if (s.length() > Math.max(1, maxLen)) s = s.substring(0, maxLen);
        return s;
    }

    /** Nur a-zA-Z0-9_ - nützlich für Faction-Namen o.ä. */
    public static String alnumUnderscore(String raw, int maxLen) {
        return sanitize(raw, maxLen).replaceAll("[^a-zA-Z0-9_]", "");
    }
}