package com.vetroforge.services.buildbot;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Generates the modular VetroForge map around one shared southern anchor.
 * Modules own stable areas so they can be rebuilt without moving the map.
 */
public final class VetroMapGenerator {

    public static final String PROFILE_ID = "vetroforge";
    public static final int FROSTAL_ENTRANCE_X = 55;
    public static final int FROSTAL_ENTRANCE_Z = 245;

    private static final List<String> MODULE_ORDER = List.of(
            "corruption", "frost", "warzone", "spawn", "frostal_core"
    );

    private VetroMapGenerator() {}

    public static List<String> moduleIds() {
        return MODULE_ORDER;
    }

    public static Set<String> crystalStates() {
        return Set.of("dormant", "awakened", "broken");
    }

    /** Placement offset for a module schematic whose clipboard origin is its local module anchor. */
    public static ModuleOffset moduleOffset(String requested) {
        return switch (normalize(requested)) {
            case "warzone" -> new ModuleOffset(0, 0, 40);
            case "corruption" -> new ModuleOffset(-100, 0, 181);
            case "frost" -> new ModuleOffset(1, 0, 181);
            case "frostal", "frostal_core" -> new ModuleOffset(FROSTAL_ENTRANCE_X, 0, FROSTAL_ENTRANCE_Z);
            default -> new ModuleOffset(0, 0, 0);
        };
    }

    public static List<MapBlock> generate(String requested) {
        String id = normalize(requested);
        if (id.equals("all") || id.equals(PROFILE_ID)) {
            Builder combined = new Builder();
            for (String module : MODULE_ORDER) combined.addAll(generateModule(module));
            return combined.blocks();
        }
        return generateModule(id);
    }

    public static List<MapBlock> crystalState(String requested) {
        String state = normalize(requested);
        if (!crystalStates().contains(state)) return List.of();

        Builder out = new Builder();
        int cx = FROSTAL_ENTRANCE_X;
        int cz = FROSTAL_ENTRANCE_Z + 50;

        // Every state owns the same volume, preventing fragments from older states remaining behind.
        for (int x = -8; x <= 8; x++) {
            for (int y = 3; y <= 31; y++) {
                for (int z = -8; z <= 8; z++) out.set(cx + x, y, cz + z, Material.AIR);
            }
        }

        switch (state) {
            case "dormant" -> dormantCrystal(out, cx, cz);
            case "broken" -> brokenCrystal(out, cx, cz);
            default -> awakenedCrystal(out, cx, cz);
        }
        return out.blocks();
    }

    private static List<MapBlock> generateModule(String id) {
        Builder out = new Builder();
        switch (id) {
            case "spawn" -> buildSpawn(out);
            case "warzone" -> buildWarzone(out);
            case "corruption" -> buildCorruption(out);
            case "frost" -> buildFrost(out);
            case "frostal", "frostal_core" -> buildFrostal(out);
            default -> {
                return List.of();
            }
        }
        return out.blocks();
    }

    private static void buildSpawn(Builder out) {
        for (int x = -44; x <= 44; x++) {
            for (int z = -44; z <= 36; z++) {
                boolean frost = x >= 0;
                out.set(x, -2, z, frost ? Material.PACKED_ICE : Material.DEEPSLATE);
                out.set(x, -1, z, frost ? spawnFrostFloor(x, z) : spawnCorruptFloor(x, z));
            }
        }

        // Main split path continues into the Warzone.
        for (int z = -52; z <= 42; z++) {
            for (int x = -6; x <= 6; x++) {
                out.set(x, -1, z, x < 0 ? Material.POLISHED_DEEPSLATE : Material.QUARTZ_BRICKS);
                if (Math.abs(x) == 6 && z % 7 == 0) lamp(out, x, 0, z, x >= 0);
            }
        }

        // Fortification with open south and north gates.
        for (int y = 0; y <= 9; y++) {
            for (int x = -44; x <= 44; x++) {
                if (Math.abs(x) <= 7) continue;
                out.set(x, y, -44, wallMaterial(x, y));
                out.set(x, y, 36, wallMaterial(x, y));
            }
            for (int z = -44; z <= 36; z++) {
                out.set(-44, y, z, darkWall(y));
                out.set(44, y, z, frostWall(y));
            }
        }
        tower(out, -38, -38, 7, 18, false);
        tower(out, 38, -38, 7, 18, true);
        tower(out, -38, 30, 7, 18, false);
        tower(out, 38, 30, 7, 18, true);

        spawnHouse(out, -23, -12, false);
        spawnHouse(out, 23, -12, true);
        pavilion(out, 0, 8);
        portalArch(out, 0, 30);

        for (int z = -30; z <= 25; z += 11) {
            lamp(out, -10, 0, z, false);
            lamp(out, 10, 0, z, true);
        }
        bannerPillar(out, -31, -2, false);
        bannerPillar(out, 31, -2, true);
    }

    private static void buildWarzone(Builder out) {
        for (int x = -100; x <= 100; x++) {
            for (int z = 40; z <= 180; z++) {
                boolean frost = x >= 0;
                int noise = positiveHash(x, z, 41) % 100;
                Material under = frost ? Material.PACKED_ICE : Material.DEEPSLATE;
                Material top = frost
                        ? (noise < 16 ? Material.BLUE_ICE : noise < 48 ? Material.SNOW_BLOCK : Material.PACKED_ICE)
                        : (noise < 12 ? Material.BASALT : noise < 45 ? Material.DEEPSLATE_TILES : Material.BLACKSTONE);
                out.set(x, -2, z, under);
                out.set(x, -1, z, top);
            }
        }

        // Convoy route and flank roads.
        path(out, -4, 4, 40, 180, Material.POLISHED_DEEPSLATE, Material.QUARTZ_BRICKS);
        path(out, -74, -66, 54, 172, Material.POLISHED_BLACKSTONE, Material.POLISHED_BLACKSTONE);
        path(out, 66, 74, 54, 172, Material.PACKED_ICE, Material.PACKED_ICE);

        // A shallow river separates sight lines without blocking movement.
        for (int z = 48; z <= 176; z++) {
            int center = 28 + (int) Math.round(Math.sin(z / 15.0D) * 9.0D);
            for (int x = center - 4; x <= center + 4; x++) {
                out.set(x, -1, z, Material.BLUE_ICE);
                out.set(x, -2, z, Material.PACKED_ICE);
            }
        }
        bridge(out, 28, 74, true, true);
        bridge(out, 23, 126, true, true);
        bridge(out, 32, 164, true, true);

        capturePlaza(out, 0, 110);
        ruinedTower(out, -61, 82, false);
        ruinedTower(out, 58, 145, true);
        ruinedTower(out, -72, 157, false);
        ruinedTower(out, 72, 67, true);

        chestPlinth(out, -44, 66, false);
        chestPlinth(out, 46, 89, true);
        chestPlinth(out, -39, 148, false);
        chestPlinth(out, 43, 166, true);

        for (int z = 52; z <= 172; z += 20) {
            lowCover(out, -25, z, false);
            lowCover(out, 24, z + 8, true);
        }
    }

    private static void buildCorruption(Builder out) {
        for (int x = -100; x <= -1; x++) {
            for (int z = 181; z <= 340; z++) {
                int noise = positiveHash(x, z, 73) % 100;
                out.set(x, -2, z, Material.DEEPSLATE);
                out.set(x, -1, z, noise < 10 ? Material.MAGMA_BLOCK
                        : noise < 34 ? Material.BASALT : noise < 66 ? Material.BLACKSTONE : Material.DEEPSLATE_TILES);
            }
        }
        windingPath(out, -50, 181, 340, false);
        volcanicHill(out, -73, 286);
        darkVillage(out, -28, 232);
        ruinedTower(out, -83, 212, false);
        ruinedTower(out, -18, 315, false);
        for (int z = 196; z <= 328; z += 18) {
            corruptTree(out, -20 - positiveHash(z, 3, 11) % 65, z);
        }
    }

    private static void buildFrost(Builder out) {
        for (int x = 1; x <= 100; x++) {
            for (int z = 181; z <= 340; z++) {
                int noise = positiveHash(x, z, 97) % 100;
                // The Frostal module owns its cavern footprint.
                double cave = square((x - FROSTAL_ENTRANCE_X) / 48.0D)
                        + square((z - (FROSTAL_ENTRANCE_Z + 42)) / 47.0D);
                if (cave < 1.0D) continue;
                out.set(x, -2, z, Material.PACKED_ICE);
                out.set(x, -1, z, noise < 13 ? Material.BLUE_ICE
                        : noise < 52 ? Material.SNOW_BLOCK : Material.PACKED_ICE);
            }
        }
        windingPath(out, 50, 181, FROSTAL_ENTRANCE_Z, true);
        iceVillage(out, 28, 222);
        iceTower(out, 84, 205);
        iceTower(out, 18, 323);
        for (int z = 193; z <= 334; z += 17) {
            frostTree(out, 17 + positiveHash(z, 5, 19) % 72, z);
        }
    }

    private static void buildFrostal(Builder out) {
        for (FrostalCoreGenerator.Block block : FrostalCoreGenerator.generate()) {
            out.set(FROSTAL_ENTRANCE_X + block.x(), block.y(), FROSTAL_ENTRANCE_Z + block.z(), block.material());
        }
    }

    private static void spawnHouse(Builder out, int cx, int cz, boolean frost) {
        Material wall = frost ? Material.CALCITE : Material.DEEPSLATE_BRICKS;
        Material trim = frost ? Material.PRISMARINE_BRICKS : Material.RED_NETHER_BRICKS;
        for (int x = cx - 8; x <= cx + 8; x++) {
            for (int z = cz - 7; z <= cz + 7; z++) out.set(x, 0, z, frost ? Material.QUARTZ_BRICKS : Material.POLISHED_DEEPSLATE);
        }
        for (int y = 1; y <= 9; y++) {
            for (int x = cx - 8; x <= cx + 8; x++) {
                if (Math.abs(x - cx) <= 2 && y <= 4) continue;
                out.set(x, y, cz - 7, y % 4 == 0 ? trim : wall);
                out.set(x, y, cz + 7, y % 4 == 0 ? trim : wall);
            }
            for (int z = cz - 7; z <= cz + 7; z++) {
                out.set(cx - 8, y, z, y % 4 == 0 ? trim : wall);
                out.set(cx + 8, y, z, y % 4 == 0 ? trim : wall);
            }
        }
        for (int layer = 0; layer <= 6; layer++) {
            for (int x = cx - 9 + layer; x <= cx + 9 - layer; x++) {
                out.set(x, 10 + layer, cz - 8 + layer, trim);
                out.set(x, 10 + layer, cz + 8 - layer, trim);
            }
        }
    }

    private static void pavilion(Builder out, int cx, int cz) {
        fillCircle(out, cx, cz, 7, 0, Material.POLISHED_DEEPSLATE);
        int[][] posts = {{-5, -5}, {-5, 5}, {5, -5}, {5, 5}};
        for (int[] post : posts) {
            for (int y = 1; y <= 6; y++) out.set(cx + post[0], y, cz + post[1], Material.POLISHED_BLACKSTONE_BRICKS);
            out.set(cx + post[0], 5, cz + post[1], post[0] < 0 ? Material.LANTERN : Material.SOUL_LANTERN);
        }
        for (int radius = 7; radius >= 1; radius--) {
            int y = 7 + (7 - radius) / 2;
            ring(out, cx, cz, radius, y, radius % 2 == 0 ? Material.CYAN_TERRACOTTA : Material.ORANGE_TERRACOTTA);
        }
        out.set(cx, 1, cz, Material.CAMPFIRE);
    }

    private static void portalArch(Builder out, int cx, int cz) {
        for (int y = 0; y <= 14; y++) {
            int half = y < 10 ? 7 : Math.max(1, 7 - (y - 9) * 2);
            out.set(cx - half, y, cz, Material.POLISHED_BLACKSTONE_BRICKS);
            out.set(cx + half, y, cz, Material.QUARTZ_BRICKS);
        }
        for (int x = -5; x <= 5; x++) {
            for (int y = 2; y <= 9; y++) out.set(cx + x, y, cz, x < 0 ? Material.ORANGE_STAINED_GLASS : Material.LIGHT_BLUE_STAINED_GLASS);
        }
    }

    private static void capturePlaza(Builder out, int cx, int cz) {
        fillCircle(out, cx, cz, 17, 0, Material.POLISHED_DEEPSLATE);
        ring(out, cx, cz, 17, 0, Material.CRYING_OBSIDIAN);
        ring(out, cx, cz, 12, 0, Material.PACKED_ICE);
        pavilion(out, cx, cz);
        for (int[] pos : new int[][]{{-14, 0}, {14, 0}, {0, -14}, {0, 14}}) {
            lamp(out, cx + pos[0], 1, cz + pos[1], pos[0] >= 0);
        }
    }

    private static void tower(Builder out, int cx, int cz, int radius, int height, boolean frost) {
        Material wall = frost ? Material.CALCITE : Material.DEEPSLATE_BRICKS;
        Material trim = frost ? Material.PRISMARINE_BRICKS : Material.RED_NETHER_BRICKS;
        for (int y = 0; y <= height; y++) {
            int r = y > height - 4 ? radius + 1 : radius;
            ring(out, cx, cz, r, y, y % 5 == 0 ? trim : wall);
        }
        for (int layer = 0; layer <= 5; layer++) {
            int r = radius + 2 - layer;
            fillCircle(out, cx, cz, Math.max(1, r), height + 1 + layer, trim);
        }
    }

    private static void ruinedTower(Builder out, int cx, int cz, boolean frost) {
        Material wall = frost ? Material.CALCITE : Material.DEEPSLATE_BRICKS;
        Material trim = frost ? Material.PACKED_ICE : Material.CRYING_OBSIDIAN;
        int height = 11 + positiveHash(cx, cz, 7) % 7;
        for (int y = 0; y <= height; y++) {
            for (int x = -3; x <= 3; x++) {
                for (int z = -3; z <= 3; z++) {
                    if (Math.abs(x) != 3 && Math.abs(z) != 3) continue;
                    if (y > height - 4 && positiveHash(x, y, z) % 4 == 0) continue;
                    out.set(cx + x, y, cz + z, y % 5 == 0 ? trim : wall);
                }
            }
        }
    }

    private static void iceTower(Builder out, int cx, int cz) {
        tower(out, cx, cz, 4, 15, true);
        out.set(cx, 22, cz, Material.SEA_LANTERN);
    }

    private static void chestPlinth(Builder out, int cx, int cz, boolean frost) {
        Material base = frost ? Material.PACKED_ICE : Material.POLISHED_BLACKSTONE_BRICKS;
        fillCircle(out, cx, cz, 3, 0, base);
        fillCircle(out, cx, cz, 2, 1, frost ? Material.CALCITE : Material.DEEPSLATE_TILES);
        out.set(cx, 2, cz, Material.CHEST);
    }

    private static void lowCover(Builder out, int cx, int cz, boolean frost) {
        Material material = frost ? Material.PACKED_ICE : Material.DEEPSLATE_BRICKS;
        for (int x = -4; x <= 4; x++) {
            out.set(cx + x, 0, cz, material);
            if (Math.abs(x) <= 2) out.set(cx + x, 1, cz, material);
        }
    }

    private static void volcanicHill(Builder out, int cx, int cz) {
        for (int y = 0; y <= 28; y++) {
            int radius = Math.max(3, 24 - y);
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (x * x + z * z > radius * radius) continue;
                    if (y > 22 && Math.abs(x) <= 3 && Math.abs(z) <= 3) continue;
                    out.set(cx + x, y, cz + z, (x + z + y) % 11 == 0 ? Material.MAGMA_BLOCK : Material.BASALT);
                }
            }
        }
        for (int y = 20; y <= 31; y++) out.set(cx, y, cz, Material.LAVA);
    }

    private static void darkVillage(Builder out, int cx, int cz) {
        for (int[] offset : new int[][]{{0, 0}, {-16, 9}, {15, 12}, {-11, -14}, {18, -11}}) {
            hut(out, cx + offset[0], cz + offset[1], false);
        }
    }

    private static void iceVillage(Builder out, int cx, int cz) {
        for (int[] offset : new int[][]{{0, 0}, {-15, 11}, {16, 10}, {-10, -14}, {18, -12}}) {
            hut(out, cx + offset[0], cz + offset[1], true);
        }
    }

    private static void hut(Builder out, int cx, int cz, boolean frost) {
        Material wall = frost ? Material.CALCITE : Material.DEEPSLATE_BRICKS;
        Material roof = frost ? Material.CYAN_TERRACOTTA : Material.RED_NETHER_BRICKS;
        for (int x = -5; x <= 5; x++) for (int z = -4; z <= 4; z++) out.set(cx + x, 0, cz + z, wall);
        for (int y = 1; y <= 5; y++) {
            for (int x = -5; x <= 5; x++) {
                if (!(Math.abs(x) <= 1 && y <= 3)) out.set(cx + x, y, cz - 4, wall);
                out.set(cx + x, y, cz + 4, wall);
            }
            for (int z = -4; z <= 4; z++) {
                out.set(cx - 5, y, cz + z, wall);
                out.set(cx + 5, y, cz + z, wall);
            }
        }
        for (int layer = 0; layer <= 4; layer++) {
            for (int x = -6 + layer; x <= 6 - layer; x++) {
                out.set(cx + x, 6 + layer, cz - 5 + layer, roof);
                out.set(cx + x, 6 + layer, cz + 5 - layer, roof);
            }
        }
    }

    private static void windingPath(Builder out, int centerX, int startZ, int endZ, boolean frost) {
        for (int z = startZ; z <= endZ; z++) {
            int bend = (int) Math.round(Math.sin(z / 18.0D) * 15.0D);
            int cx = centerX + bend;
            for (int x = cx - 4; x <= cx + 4; x++) {
                out.set(x, -1, z, frost ? Material.QUARTZ_BRICKS : Material.POLISHED_DEEPSLATE);
            }
            if (z % 13 == 0) lamp(out, cx + (frost ? 7 : -7), 0, z, frost);
        }
    }

    private static void corruptTree(Builder out, int cx, int cz) {
        int height = 5 + positiveHash(cx, cz, 5) % 5;
        for (int y = 0; y <= height; y++) out.set(cx, y, cz, Material.STRIPPED_DARK_OAK_LOG);
        for (int y = height - 2; y <= height + 2; y++) {
            int radius = Math.max(1, 3 - Math.abs(height - y));
            for (int x = -radius; x <= radius; x++) for (int z = -radius; z <= radius; z++) {
                if (Math.abs(x) + Math.abs(z) <= radius + 1) out.set(cx + x, y, cz + z, Material.NETHER_WART_BLOCK);
            }
        }
    }

    private static void frostTree(Builder out, int cx, int cz) {
        int height = 7 + positiveHash(cx, cz, 8) % 5;
        for (int y = 0; y <= height; y++) out.set(cx, y, cz, Material.STRIPPED_SPRUCE_LOG);
        for (int y = 3; y <= height + 2; y += 2) {
            int radius = Math.max(1, 5 - y / 3);
            fillCircle(out, cx, cz, radius, y, Material.SPRUCE_LEAVES);
            fillCircle(out, cx, cz, Math.max(1, radius - 1), y + 1, Material.SNOW_BLOCK);
        }
    }

    private static void bridge(Builder out, int cx, int cz, boolean alongX, boolean frost) {
        Material deck = frost ? Material.QUARTZ_BRICKS : Material.POLISHED_DEEPSLATE;
        for (int axis = -9; axis <= 9; axis++) {
            for (int width = -3; width <= 3; width++) {
                int x = alongX ? cx + axis : cx + width;
                int z = alongX ? cz + width : cz + axis;
                out.set(x, 0, z, deck);
                if (Math.abs(width) == 3) out.set(x, 1, z, frost ? Material.PRISMARINE_WALL : Material.POLISHED_BLACKSTONE_BRICK_WALL);
            }
        }
    }

    private static void path(Builder out, int minX, int maxX, int minZ, int maxZ, Material left, Material right) {
        for (int x = minX; x <= maxX; x++) {
            for (int z = minZ; z <= maxZ; z++) out.set(x, -1, z, x < 0 ? left : right);
        }
    }

    private static void lamp(Builder out, int x, int y, int z, boolean frost) {
        Material post = frost ? Material.PRISMARINE_WALL : Material.POLISHED_BLACKSTONE_BRICK_WALL;
        for (int dy = 0; dy <= 3; dy++) out.set(x, y + dy, z, post);
        out.set(x, y + 4, z, frost ? Material.SOUL_LANTERN : Material.LANTERN);
    }

    private static void bannerPillar(Builder out, int x, int z, boolean frost) {
        for (int y = 0; y <= 8; y++) out.set(x, y, z, frost ? Material.CALCITE : Material.DEEPSLATE_BRICKS);
        out.set(x, 9, z, frost ? Material.SEA_LANTERN : Material.SHROOMLIGHT);
    }

    private static void awakenedCrystal(Builder out, int cx, int cz) {
        crystal(out, cx, cz, 3, 25, Material.BLUE_ICE, Material.SEA_LANTERN);
        crystal(out, cx - 5, cz + 2, 4, 15, Material.PACKED_ICE, Material.BLUE_STAINED_GLASS);
        crystal(out, cx + 5, cz + 3, 4, 17, Material.PACKED_ICE, Material.BLUE_STAINED_GLASS);
    }

    private static void dormantCrystal(Builder out, int cx, int cz) {
        crystal(out, cx, cz, 3, 15, Material.PACKED_ICE, Material.BLUE_STAINED_GLASS);
        out.set(cx, 8, cz, Material.SCULK_CATALYST);
    }

    private static void brokenCrystal(Builder out, int cx, int cz) {
        crystal(out, cx, cz, 3, 10, Material.BLUE_ICE, Material.CRYING_OBSIDIAN);
        crystal(out, cx - 5, cz - 3, 2, 7, Material.PACKED_ICE, Material.AMETHYST_BLOCK);
        crystal(out, cx + 6, cz + 2, 2, 6, Material.PACKED_ICE, Material.AMETHYST_BLOCK);
        for (int[] fragment : new int[][]{{-7, 7, 1}, {-4, 13, 5}, {6, 15, -4}, {3, 19, 7}}) {
            out.set(cx + fragment[0], fragment[1], cz + fragment[2], Material.AMETHYST_CLUSTER);
        }
    }

    private static void crystal(Builder out, int cx, int cz, int baseRadius, int height, Material shell, Material core) {
        for (int y = 0; y < height; y++) {
            double progress = y / (double) Math.max(1, height - 1);
            int radius = Math.max(1, (int) Math.round(baseRadius * (1.0D - progress * 0.82D)));
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) + Math.abs(z) > radius + 1) continue;
                    out.set(cx + x, 3 + y, cz + z, Math.abs(x) <= 1 && Math.abs(z) <= 1 ? core : shell);
                }
            }
        }
        out.set(cx, 3 + height, cz, Material.AMETHYST_CLUSTER);
    }

    private static Material spawnFrostFloor(int x, int z) {
        int value = positiveHash(x, z, 151) % 100;
        return value < 18 ? Material.BLUE_ICE : value < 55 ? Material.QUARTZ_BRICKS : Material.PACKED_ICE;
    }

    private static Material spawnCorruptFloor(int x, int z) {
        int value = positiveHash(x, z, 173) % 100;
        return value < 14 ? Material.RED_NETHER_BRICKS : value < 55 ? Material.POLISHED_DEEPSLATE : Material.DEEPSLATE_TILES;
    }

    private static Material wallMaterial(int x, int y) {
        return x < 0 ? darkWall(y) : frostWall(y);
    }

    private static Material darkWall(int y) {
        return y % 4 == 0 ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_BRICKS;
    }

    private static Material frostWall(int y) {
        return y % 4 == 0 ? Material.PRISMARINE_BRICKS : Material.CALCITE;
    }

    private static void fillCircle(Builder out, int cx, int cz, int radius, int y, Material material) {
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= radius * radius) out.set(cx + x, y, cz + z, material);
            }
        }
    }

    private static void ring(Builder out, int cx, int cz, int radius, int y, Material material) {
        int outer = radius * radius;
        int inner = Math.max(0, (radius - 1) * (radius - 1));
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int distance = x * x + z * z;
                if (distance <= outer && distance >= inner) out.set(cx + x, y, cz + z, material);
            }
        }
    }

    private static int positiveHash(int a, int b, int c) {
        int value = a * 73428767 ^ b * 912931 ^ c * 438289;
        value ^= value >>> 16;
        return value & Integer.MAX_VALUE;
    }

    private static double square(double value) {
        return value * value;
    }

    private static String normalize(String value) {
        return value == null ? "" : value.toLowerCase(Locale.ROOT).replace('-', '_');
    }

    public record MapBlock(int x, int y, int z, Material material) {}
    public record ModuleOffset(int x, int y, int z) {}

    private record Position(int x, int y, int z) {}

    private static final class Builder {
        private final Map<Position, Material> blocks = new LinkedHashMap<>();

        void set(int x, int y, int z, Material material) {
            blocks.put(new Position(x, y, z), material);
        }

        void addAll(List<MapBlock> additions) {
            for (MapBlock block : additions) set(block.x(), block.y(), block.z(), block.material());
        }

        List<MapBlock> blocks() {
            List<MapBlock> result = new ArrayList<>(blocks.size());
            blocks.forEach((position, material) -> result.add(
                    new MapBlock(position.x, position.y, position.z, material)
            ));
            return List.copyOf(result);
        }
    }
}
