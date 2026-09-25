package com.vetroforge.services.buildbot;

import org.bukkit.Material;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Generates the Frostal Ice landmark relative to its southern entrance. */
public final class FrostalCoreGenerator {

    private static final int CENTER_Z = 42;
    private static final int CRYSTAL_Z = 50;
    private static final int RADIUS_X = 44;
    private static final int RADIUS_Z = 42;
    private static final int CENTER_Y = 23;
    private static final int RADIUS_Y = 25;

    private FrostalCoreGenerator() {}

    public static List<Block> generate() {
        Builder out = new Builder();
        buildFloor(out);
        buildCavern(out);
        buildArena(out);
        buildEntrances(out);
        buildPillars(out);
        buildControlPylons(out);
        buildCrystal(out);
        buildIceClusters(out);
        buildIcicles(out);
        return out.blocks();
    }

    private static void buildFloor(Builder out) {
        for (int x = -RADIUS_X; x <= RADIUS_X; x++) {
            for (int z = 0; z <= CENTER_Z + RADIUS_Z; z++) {
                double ellipse = square(x / (double) RADIUS_X)
                        + square((z - CENTER_Z) / (double) RADIUS_Z);
                if (ellipse > 1.0D) continue;
                out.set(x, -4, z, Material.OBSIDIAN);
                out.set(x, -3, z, Material.DEEPSLATE);
                out.set(x, -2, z, floorMaterial(x, z, false));
                out.set(x, -1, z, floorMaterial(x, z, true));
            }
        }
    }

    private static void buildCavern(Builder out) {
        for (int x = -RADIUS_X; x <= RADIUS_X; x++) {
            for (int y = 0; y <= CENTER_Y + RADIUS_Y; y++) {
                for (int z = 0; z <= CENTER_Z + RADIUS_Z; z++) {
                    double norm = square(x / (double) RADIUS_X)
                            + square((z - CENTER_Z) / (double) RADIUS_Z)
                            + square((y - CENTER_Y) / (double) RADIUS_Y);
                    if (norm < 0.80D || norm > 1.06D || isEntrance(x, y, z)) continue;
                    out.set(x, y, z, cavernMaterial(x, y, z));
                }
            }
        }
    }

    private static void buildArena(Builder out) {
        int cz = CRYSTAL_Z;
        for (int x = -24; x <= 24; x++) {
            for (int z = cz - 24; z <= cz + 24; z++) {
                int dx = x;
                int dz = z - cz;
                int distance = dx * dx + dz * dz;
                if (distance > 24 * 24) continue;
                Material top;
                if (distance >= 21 * 21) {
                    top = Material.CRYING_OBSIDIAN;
                } else if (Math.abs(dx) <= 1 || Math.abs(dz) <= 1) {
                    top = Material.BLUE_ICE;
                } else if ((Math.abs(dx) + Math.abs(dz)) % 11 == 0) {
                    top = Material.PACKED_ICE;
                } else {
                    top = ((dx + dz) & 1) == 0
                            ? Material.POLISHED_DEEPSLATE
                            : Material.POLISHED_BLACKSTONE_BRICKS;
                }
                out.set(x, -2, z, Material.DEEPSLATE);
                out.set(x, -1, z, top);
            }
        }

        for (int radius = 8; radius >= 5; radius--) {
            int y = 8 - radius;
            fillCircle(out, 0, cz, radius, y, radius <= 6
                    ? Material.CRYING_OBSIDIAN
                    : Material.POLISHED_BLACKSTONE_BRICKS);
        }

        for (int z = 1; z <= cz - 24; z++) {
            int halfWidth = z < 8 ? 6 : 4;
            for (int x = -halfWidth; x <= halfWidth; x++) {
                out.set(x, -1, z, Math.abs(x) == halfWidth
                        ? Material.PACKED_ICE
                        : Material.POLISHED_DEEPSLATE);
            }
            if (z % 7 == 0) {
                out.set(-halfWidth - 1, 0, z, Material.SOUL_LANTERN);
                out.set(halfWidth + 1, 0, z, Material.SOUL_LANTERN);
            }
        }
    }

    private static void buildEntrances(Builder out) {
        buildArch(out, 0, 2, false);
        buildArch(out, 0, CENTER_Z * 2 - 2, false);
        buildArch(out, -RADIUS_X + 2, CENTER_Z, true);
        buildArch(out, RADIUS_X - 2, CENTER_Z, true);
    }

    private static void buildArch(Builder out, int cx, int cz, boolean alongX) {
        for (int y = 0; y <= 13; y++) {
            int half = y < 9 ? 7 : Math.max(1, 7 - (y - 8) * 2);
            for (int offset = -half; offset <= half; offset++) {
                if (Math.abs(offset) < half && y < 9) continue;
                int x = alongX ? cx : cx + offset;
                int z = alongX ? cz + offset : cz;
                out.set(x, y, z, y % 4 == 0 ? Material.PACKED_ICE : Material.POLISHED_BLACKSTONE_BRICKS);
            }
        }
    }

    private static void buildPillars(Builder out) {
        int[][] positions = {
                {-28, 25, 17}, {28, 25, 17}, {-31, 66, 20}, {31, 66, 20}
        };
        for (int[] pos : positions) {
            int x = pos[0];
            int z = pos[1];
            int height = pos[2];
            for (int y = 0; y <= height; y++) {
                for (int dx = -2; dx <= 2; dx++) {
                    for (int dz = -2; dz <= 2; dz++) {
                        boolean edge = Math.abs(dx) == 2 || Math.abs(dz) == 2;
                        if (!edge && y > 2 && y < height - 2) continue;
                        Material material = y % 6 == 0
                                ? Material.CRYING_OBSIDIAN
                                : (edge ? Material.POLISHED_BLACKSTONE_BRICKS : Material.DEEPSLATE_TILES);
                        out.set(x + dx, y, z + dz, material);
                    }
                }
            }
            out.set(x, height + 1, z, Material.SEA_LANTERN);
            line(out, x, height + 2, z, 0, 24, CRYSTAL_Z, Material.CHAIN);
        }
    }

    private static void buildControlPylons(Builder out) {
        int[][] positions = {{0, 31}, {-17, 58}, {17, 58}};
        for (int[] pos : positions) {
            int x = pos[0];
            int z = pos[1];
            fillCircle(out, x, z, 4, 0, Material.POLISHED_BLACKSTONE_BRICKS);
            fillCircle(out, x, z, 3, 1, Material.CRYING_OBSIDIAN);
            out.set(x, 2, z, Material.LODESTONE);
            out.set(x, 3, z, Material.SEA_LANTERN);
            out.set(x, 4, z, Material.END_ROD);
            out.set(x, 5, z, Material.AMETHYST_CLUSTER);
        }
    }

    private static void buildCrystal(Builder out) {
        int[][] radii = {
                {0, 2}, {1, 3}, {2, 4}, {3, 5}, {4, 6}, {5, 7}, {6, 7}, {7, 8},
                {8, 8}, {9, 7}, {10, 7}, {11, 6}, {12, 6}, {13, 5}, {14, 5},
                {15, 4}, {16, 4}, {17, 3}, {18, 3}, {19, 2}, {20, 2}, {21, 1}, {22, 1}
        };
        for (int[] layer : radii) {
            int y = 3 + layer[0];
            int radius = layer[1];
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    int manhattan = Math.abs(x) + Math.abs(z);
                    if (manhattan > radius + 1) continue;
                    boolean core = Math.abs(x) <= 1 && Math.abs(z) <= 1;
                    Material material = core
                            ? Material.SEA_LANTERN
                            : ((x + z + y) % 5 == 0 ? Material.BLUE_STAINED_GLASS : Material.BLUE_ICE);
                    out.set(x, y, CRYSTAL_Z + z, material);
                }
            }
        }
        out.set(0, 26, CRYSTAL_Z, Material.END_ROD);
        out.set(0, 27, CRYSTAL_Z, Material.AMETHYST_CLUSTER);
    }

    private static void buildIceClusters(Builder out) {
        int[][] clusters = {
                {-35, 15, 8}, {34, 17, 11}, {-37, 46, 10}, {38, 43, 8},
                {-30, 74, 12}, {29, 76, 9}, {-18, 12, 6}, {19, 11, 7}
        };
        for (int[] cluster : clusters) {
            spike(out, cluster[0], cluster[1], cluster[2], 3);
            spike(out, cluster[0] - 4, cluster[1] + 2, Math.max(3, cluster[2] - 4), 2);
            spike(out, cluster[0] + 4, cluster[1] + 1, Math.max(4, cluster[2] - 3), 2);
        }
    }

    private static void buildIcicles(Builder out) {
        for (int x = -36; x <= 36; x += 6) {
            for (int z = 8; z <= 76; z += 7) {
                int hash = positiveHash(x, z, 91);
                if (hash % 4 == 0) continue;
                double horizontal = square(x / (double) RADIUS_X)
                        + square((z - CENTER_Z) / (double) RADIUS_Z);
                if (horizontal >= 0.75D) continue;
                int roof = CENTER_Y + (int) Math.floor(RADIUS_Y * Math.sqrt(1.0D - horizontal));
                int length = 2 + hash % 7;
                for (int y = roof - 3; y >= roof - 3 - length; y--) {
                    out.set(x, y, z, y == roof - 3 - length ? Material.BLUE_ICE : Material.PACKED_ICE);
                }
            }
        }
    }

    private static void spike(Builder out, int cx, int cz, int height, int baseRadius) {
        for (int y = 0; y < height; y++) {
            int radius = Math.max(0, baseRadius - (y * baseRadius / Math.max(1, height - 1)));
            for (int x = -radius; x <= radius; x++) {
                for (int z = -radius; z <= radius; z++) {
                    if (Math.abs(x) + Math.abs(z) > radius + 1) continue;
                    out.set(cx + x, y, cz + z, y % 5 == 0 ? Material.CALCITE : Material.PACKED_ICE);
                }
            }
        }
        out.set(cx, height, cz, Material.BLUE_ICE);
    }

    private static void fillCircle(Builder out, int cx, int cz, int radius, int y, Material material) {
        int squared = radius * radius;
        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                if (x * x + z * z <= squared) out.set(cx + x, y, cz + z, material);
            }
        }
    }

    private static void line(Builder out, int x1, int y1, int z1, int x2, int y2, int z2, Material material) {
        int steps = Math.max(Math.abs(x2 - x1), Math.max(Math.abs(y2 - y1), Math.abs(z2 - z1)));
        for (int step = 0; step <= steps; step++) {
            double progress = steps == 0 ? 0.0D : step / (double) steps;
            out.set(
                    (int) Math.round(x1 + (x2 - x1) * progress),
                    (int) Math.round(y1 + (y2 - y1) * progress),
                    (int) Math.round(z1 + (z2 - z1) * progress),
                    material
            );
        }
    }

    private static boolean isEntrance(int x, int y, int z) {
        boolean south = z <= 8 && Math.abs(x) <= 7 && y <= 13;
        boolean north = z >= CENTER_Z * 2 - 8 && Math.abs(x) <= 7 && y <= 13;
        boolean west = x <= -RADIUS_X + 8 && Math.abs(z - CENTER_Z) <= 6 && y <= 11;
        boolean east = x >= RADIUS_X - 8 && Math.abs(z - CENTER_Z) <= 6 && y <= 11;
        return south || north || west || east;
    }

    private static Material floorMaterial(int x, int z, boolean top) {
        int value = positiveHash(x, z, top ? 17 : 31) % 100;
        if (top && value < 9) return Material.BLUE_ICE;
        if (top && value < 25) return Material.PACKED_ICE;
        if (value < 51) return Material.POLISHED_DEEPSLATE;
        if (value < 78) return Material.DEEPSLATE_TILES;
        return Material.POLISHED_BLACKSTONE_BRICKS;
    }

    private static Material cavernMaterial(int x, int y, int z) {
        int value = positiveHash(x, y, z) % 100;
        if (value < 8) return Material.BLUE_ICE;
        if (value < 21) return Material.PACKED_ICE;
        if (value < 27) return Material.CALCITE;
        if (value < 55) return Material.DEEPSLATE_TILES;
        if (value < 78) return Material.POLISHED_BLACKSTONE_BRICKS;
        return Material.OBSIDIAN;
    }

    private static int positiveHash(int a, int b, int c) {
        int value = a * 73428767 ^ b * 912931 ^ c * 438289;
        value ^= value >>> 16;
        return value & Integer.MAX_VALUE;
    }

    private static double square(double value) {
        return value * value;
    }

    public record Block(int x, int y, int z, Material material) {}

    private record Position(int x, int y, int z) {}

    private static final class Builder {
        private final Map<Position, Material> blocks = new LinkedHashMap<>();

        void set(int x, int y, int z, Material material) {
            blocks.put(new Position(x, y, z), material);
        }

        List<Block> blocks() {
            List<Block> result = new ArrayList<>(blocks.size());
            blocks.forEach((position, material) -> result.add(
                    new Block(position.x, position.y, position.z, material)
            ));
            return List.copyOf(result);
        }
    }
}
