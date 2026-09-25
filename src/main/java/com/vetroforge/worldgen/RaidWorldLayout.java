// src/main/java/com/vetroforge/worldgen/RaidWorldLayout.java
package com.vetroforge.worldgen;

/**
 * Geometrie & Zonenlogik der Faction-/Raid-Welt.
 *
 * Aufbau:
 * - Mitte: 201x201 Build-Plot
 * - Rote Linie: Rand vom eigenen Plot
 * - Danach: 45 Blöcke Raid-/Buffer-Zone
 * - Grüne Linie: Außenkante der Raid-Zone
 * - Außen: Berg-/Waldgürtel
 */
public final class RaidWorldLayout {

    private RaidWorldLayout() {}

    public static final int BUILD_SIDE = 201;
    public static final int BUILD_HALF = BUILD_SIDE / 2; // 100

    public static final int RED_THICK = 1;
    public static final int BUFFER_WIDTH = 45;
    public static final int GREEN_THICK = 1;

    public static final int MTN_WIDTH = 147;
    public static final int MAX_MTN_H = 24;

    public static final int GREEN_EDGE = BUILD_HALF + RED_THICK + BUFFER_WIDTH;
    public static final int WORLD_HALF = GREEN_EDGE + GREEN_THICK + MTN_WIDTH;

    public static boolean inBuild(int x, int z) {
        return Math.abs(x) <= BUILD_HALF && Math.abs(z) <= BUILD_HALF;
    }

    public static boolean atRedLine(int x, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);

        return (ax == BUILD_HALF && az <= BUILD_HALF)
                || (az == BUILD_HALF && ax <= BUILD_HALF);
    }

    public static boolean inBufferRaiders(int x, int z) {
        int a = Math.max(Math.abs(x), Math.abs(z));
        return a > BUILD_HALF && a < GREEN_EDGE;
    }

    public static boolean atGreenLine(int x, int z) {
        int ax = Math.abs(x);
        int az = Math.abs(z);
        int a = Math.max(ax, az);

        return a == GREEN_EDGE && Math.min(ax, az) <= GREEN_EDGE;
    }

    public static boolean inMountains(int x, int z) {
        int a = Math.max(Math.abs(x), Math.abs(z));
        return a > (GREEN_EDGE + GREEN_THICK) && a <= WORLD_HALF;
    }

    public static boolean insideWorldSquare(int x, int z) {
        return Math.max(Math.abs(x), Math.abs(z)) <= WORLD_HALF;
    }

    public static double mountainT(double x, double z) {
        int a = Math.max((int) Math.abs(x), (int) Math.abs(z));
        int inner = GREEN_EDGE + GREEN_THICK;

        if (a <= inner) return 0.0;

        int d = a - inner;
        return Math.min(1.0, d / (double) MTN_WIDTH);
    }
}