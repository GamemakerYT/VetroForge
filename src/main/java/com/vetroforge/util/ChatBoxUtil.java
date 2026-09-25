// src/main/java/com/vetroforge/util/ChatBoxUtil.java
package com.vetroforge.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/**
 * Gemeinsame "Box"-Chatoptik, vorher unabhaengig in CrystalsCommand und
 * EnergyCommand dupliziert (mit leicht unterschiedlicher Breite). Zentral hier,
 * damit /help, /adminboss, /chest und Hoverchat dieselbe visuelle Sprache nutzen.
 */
public final class ChatBoxUtil {

    private ChatBoxUtil() {}

    public static final int WIDTH = 34;

    public static Component boxTop() {
        return Component.text("╔" + "═".repeat(WIDTH) + "╗", NamedTextColor.DARK_GRAY);
    }

    public static Component boxBottom() {
        return Component.text("╚" + "═".repeat(WIDTH) + "╝", NamedTextColor.DARK_GRAY);
    }

    /** Chat ist nicht monospaced -- optisches "Centering" via ein paar grauen Spaces links. */
    public static Component center(Component c) {
        return Component.text("  ", NamedTextColor.DARK_GRAY).append(c);
    }

    public static Component divider() {
        return Component.text("§8" + "─".repeat(WIDTH));
    }

    /** Farbiger Balken mit gegebener Segmentanzahl, gefuellte Segmente in rot→gelb→gruen-Verlauf. */
    public static Component progressBar(double fraction, int len) {
        double t = Math.max(0.0, Math.min(1.0, fraction));
        int full = (int) Math.round(len * t);

        Component left = Component.text("▕", NamedTextColor.DARK_GRAY);
        Component right = Component.text("▏", NamedTextColor.DARK_GRAY);
        Component bar = Component.empty();

        for (int i = 0; i < len; i++) {
            boolean filled = i < full;
            double k = (len == 1) ? 1.0 : (i / (double) (len - 1));
            TextColor color = filled ? gradient(k) : NamedTextColor.DARK_GRAY;
            bar = bar.append(Component.text("█", color));
        }
        return left.append(bar).append(right);
    }

    /** Linearer Verlauf: 0..0.5 rot→gelb, 0.5..1 gelb→gruen. */
    public static TextColor gradient(double k) {
        k = Math.max(0.0, Math.min(1.0, k));
        int r1 = 0xFF, g1 = 0x55, b1 = 0x55;
        int r2 = 0xFF, g2 = 0xFF, b2 = 0x55;
        int r3 = 0x55, g3 = 0xFF, b3 = 0x55;

        if (k <= 0.5) {
            double t = k / 0.5;
            return TextColor.color(lerp(r1, r2, t), lerp(g1, g2, t), lerp(b1, b2, t));
        } else {
            double t = (k - 0.5) / 0.5;
            return TextColor.color(lerp(r2, r3, t), lerp(g2, g3, t), lerp(b2, b3, t));
        }
    }

    private static int lerp(int a, int b, double t) {
        return (int) Math.round(a + (b - a) * t);
    }
}
