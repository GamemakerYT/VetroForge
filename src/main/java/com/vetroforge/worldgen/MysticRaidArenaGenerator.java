// src/main/java/com/vetroforge/worldgen/MysticRaidArenaGenerator.java
package com.vetroforge.worldgen;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import java.util.List;
import java.util.Random;

import static com.vetroforge.worldgen.RaidWorldLayout.*;

public class MysticRaidArenaGenerator extends ChunkGenerator {

    private final int yLevel;

    public MysticRaidArenaGenerator(int yLevel) {
        this.yLevel = yLevel;
    }

    @Override public boolean shouldGenerateSurface() { return false; }
    @Override public boolean shouldGenerateCaves() { return false; }
    @Override public boolean shouldGenerateMobs() { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateStructures() { return false; }
    @Override public boolean shouldGenerateNoise() { return false; }
    @Override public boolean shouldGenerateBedrock() { return false; }

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomes) {
        ChunkData data = createChunkData(world);

        int minX = chunkX << 4;
        int minZ = chunkZ << 4;

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;

                if (!insideWorldSquare(x, z)) {
                    continue;
                }

                boolean mountainZone = inMountains(x, z);
                setBiomeSafe(biomes, dx, dz, mountainZone ? Biome.TAIGA : Biome.PLAINS);

                data.setBlock(dx, yLevel - 6, dz, Material.BEDROCK);

                if (inBuild(x, z)) {
                    buildFlatColumn(data, dx, dz, Material.GRASS_BLOCK);

                    if (atRedLine(x, z)) {
                        buildFlatColumn(data, dx, dz, Material.RED_STAINED_GLASS);
                    }

                    continue;
                }

                if (inBufferRaiders(x, z)) {
                    buildFlatColumn(data, dx, dz, Material.GRASS_BLOCK);
                    continue;
                }

                if (atGreenLine(x, z)) {
                    buildFlatColumn(data, dx, dz, Material.LIME_STAINED_GLASS);
                    continue;
                }

                if (mountainZone) {
                    buildSmoothMountainColumn(data, dx, dz, x, z);
                    continue;
                }

                buildFlatColumn(data, dx, dz, Material.GRASS_BLOCK);
            }
        }

        if (chunkX == 0 && chunkZ == 0) {
            data.setBlock(0, yLevel, 0, Material.GOLD_BLOCK);
        }

        return data;
    }

    private void buildFlatColumn(ChunkData data, int x, int z, Material top) {
        for (int y = yLevel - 5; y <= yLevel - 2; y++) {
            data.setBlock(x, y, z, Material.STONE);
        }

        data.setBlock(x, yLevel - 1, z, Material.DIRT);
        data.setBlock(x, yLevel, z, top);
    }

    private void buildSmoothMountainColumn(ChunkData data, int dx, int dz, int worldX, int worldZ) {
        int height = getSoftTerracedHeight(worldX, worldZ);
        int topY = yLevel + height;

        for (int y = yLevel - 5; y <= topY; y++) {
            if (shouldCarveSmallCave(worldX, y, worldZ, topY)) {
                data.setBlock(dx, y, dz, Material.CAVE_AIR);
                continue;
            }

            Material material;
            int depth = topY - y;

            if (depth == 0) {
                material = Material.GRASS_BLOCK;
            } else if (depth <= 2) {
                material = chooseSoil(worldX, worldZ);
            } else if (depth <= 6) {
                material = chooseRock(worldX, y, worldZ, true);
            } else {
                material = chooseRock(worldX, y, worldZ, false);
            }

            data.setBlock(dx, y, dz, material);
        }

        decorateSurface(data, dx, dz, worldX, worldZ, topY);
    }

    /*
     * Fix gegen diese ekelhafte Wand:
     * - direkt nach der grünen Linie bleibt es fast flach
     * - danach steigt es langsam 1 Block pro ca. 3 Blöcke Abstand
     * - Noise ist weich und niedrig
     * - keine harten Blob-Wände mehr
     */
    private int getSoftTerracedHeight(int x, int z) {
        int inner = GREEN_EDGE + GREEN_THICK;
        int distance = Math.max(Math.abs(x), Math.abs(z));
        int d = Math.max(0, distance - inner);

        if (d <= 4) {
            return 0;
        }

        double t = d / (double) MTN_WIDTH;

        /*
         * Grundrampe:
         * 1 Block Höhe alle 3 Blöcke Abstand.
         */
        double base = Math.max(0, (d - 4) / 3.0);

        /*
         * Weiche Höhenvariation.
         * Keine harte Wand, nur leichte Geländeform.
         */
        double large = noise(x * 0.018, z * 0.018);
        double medium = noise(x * 0.040 + 120, z * 0.040 - 120);
        double valley = noise(x * 0.020 - 700, z * 0.020 + 700);

        double noiseBoost = large * 4.0 + medium * 2.0;

        /*
         * Täler schneiden weich rein.
         */
        if (valley < 0.34) {
            noiseBoost *= 0.35;
            base *= 0.70;
        }

        /*
         * Außen nicht endlos hoch.
         */
        double outerFalloff = 1.0 - smoothStep((t - 0.86) / 0.14);
        outerFalloff = Math.max(0.45, outerFalloff);

        double height = (base + noiseBoost) * outerFalloff;

        /*
         * Terrassen:
         * Höhe bleibt 2 bis 3 Blöcke horizontal ähnlich,
         * dadurch sieht es wie sauber geterraformt aus.
         */
        int raw = (int) Math.round(height);
        int terraced = raw - (raw % 1);

        if (d < 18) {
            terraced = Math.min(terraced, 4);
        }

        if (d < 32) {
            terraced = Math.min(terraced, 8);
        }

        return Math.max(0, Math.min(26, terraced));
    }

    private boolean shouldCarveSmallCave(int x, int y, int z, int topY) {
        if (y < yLevel + 2) return false;
        if (y > topY - 4) return false;

        int inner = GREEN_EDGE + GREEN_THICK;
        int distance = Math.max(Math.abs(x), Math.abs(z));
        int d = distance - inner;

        /*
         * Keine Caves direkt vorne,
         * sonst sieht die Kante wieder kaputt aus.
         */
        if (d < 42 || d > MTN_WIDTH - 12) return false;

        double cave = noise3(x * 0.055, y * 0.105, z * 0.055);
        double tunnel = Math.abs(Math.sin((x * 0.050) + (z * 0.040)));

        boolean pocket = cave > 0.785 && tunnel < 0.34;
        boolean entrance = isSmallEntrance(x, y, z, d);

        return pocket || entrance;
    }

    private boolean isSmallEntrance(int x, int y, int z, int d) {
        if (d < 45 || d > 82) return false;
        if (y < yLevel + 1 || y > yLevel + 6) return false;

        int side = Math.max(Math.abs(x), Math.abs(z));
        boolean face = Math.abs(x) == side || Math.abs(z) == side;

        if (!face) return false;

        int pattern = Math.abs((x * 31 + z * 17) % 83);
        if (pattern > 3) return false;

        return smoothHash(x / 4, z / 4) > 0.60;
    }

    private Material chooseSoil(int x, int z) {
        double n = smoothHash(x + 11, z - 19);

        if (n > 0.80) return Material.COARSE_DIRT;
        if (n > 0.64) return Material.ROOTED_DIRT;

        return Material.DIRT;
    }

    private Material chooseRock(int x, int y, int z, boolean light) {
        double n = noise3(x * 0.090 + 10, y * 0.120, z * 0.090 - 10);

        if (light) {
            if (n > 0.76) return Material.ANDESITE;
            if (n > 0.59) return Material.COBBLESTONE;
            if (n > 0.44) return Material.STONE;
            return Material.TUFF;
        }

        if (n > 0.76) return Material.COBBLESTONE;
        if (n > 0.58) return Material.STONE;
        if (n > 0.46) return Material.ANDESITE;
        return Material.STONE;
    }

    private void decorateSurface(ChunkData data, int dx, int dz, int worldX, int worldZ, int topY) {
        double n = smoothHash(worldX * 2 + 7, worldZ * 2 - 9);

        if (n > 0.991) {
            data.setBlock(dx, topY + 1, dz, Material.FERN);
        } else if (n > 0.976) {
            data.setBlock(dx, topY + 1, dz, Material.SHORT_GRASS);
        } else if (n > 0.964) {
            data.setBlock(dx, topY + 1, dz, Material.MOSS_CARPET);
        } else if (n > 0.954) {
            data.setBlock(dx, topY + 1, dz, Material.DANDELION);
        } else if (n > 0.946) {
            data.setBlock(dx, topY + 1, dz, Material.POPPY);
        }
    }

    private double noise(double x, double z) {
        int x0 = fastFloor(x);
        int z0 = fastFloor(z);
        int x1 = x0 + 1;
        int z1 = z0 + 1;

        double sx = smoothStep(x - x0);
        double sz = smoothStep(z - z0);

        double n00 = smoothHash(x0, z0);
        double n10 = smoothHash(x1, z0);
        double n01 = smoothHash(x0, z1);
        double n11 = smoothHash(x1, z1);

        double ix0 = lerp(n00, n10, sx);
        double ix1 = lerp(n01, n11, sx);

        return lerp(ix0, ix1, sz);
    }

    private double noise3(double x, double y, double z) {
        int x0 = fastFloor(x);
        int y0 = fastFloor(y);
        int z0 = fastFloor(z);

        int x1 = x0 + 1;
        int y1 = y0 + 1;
        int z1 = z0 + 1;

        double sx = smoothStep(x - x0);
        double sy = smoothStep(y - y0);
        double sz = smoothStep(z - z0);

        double n000 = smoothHash3(x0, y0, z0);
        double n100 = smoothHash3(x1, y0, z0);
        double n010 = smoothHash3(x0, y1, z0);
        double n110 = smoothHash3(x1, y1, z0);
        double n001 = smoothHash3(x0, y0, z1);
        double n101 = smoothHash3(x1, y0, z1);
        double n011 = smoothHash3(x0, y1, z1);
        double n111 = smoothHash3(x1, y1, z1);

        double x00 = lerp(n000, n100, sx);
        double x10 = lerp(n010, n110, sx);
        double x01 = lerp(n001, n101, sx);
        double x11 = lerp(n011, n111, sx);

        double y0v = lerp(x00, x10, sy);
        double y1v = lerp(x01, x11, sy);

        return lerp(y0v, y1v, sz);
    }

    private int fastFloor(double value) {
        int i = (int) value;
        return value < i ? i - 1 : i;
    }

    private double smoothStep(double value) {
        value = clamp(value);
        return value * value * (3.0 - 2.0 * value);
    }

    private double lerp(double a, double b, double t) {
        return a + (b - a) * t;
    }

    private double clamp(double value) {
        return Math.max(0.0, Math.min(1.0, value));
    }

    private double smoothHash(int x, int z) {
        long h = x * 341873128712L ^ z * 132897987541L ^ 0x9E3779B97F4A7C15L;
        h ^= (h >>> 23);
        h *= 0x2127599bf4325c37L;
        h ^= (h >>> 47);

        return (h >>> 11) / (double) (1L << 53);
    }

    private double smoothHash3(int x, int y, int z) {
        long h = x * 341873128712L
                ^ y * 42317861L
                ^ z * 132897987541L
                ^ 0x9E3779B97F4A7C15L;

        h ^= (h >>> 23);
        h *= 0x2127599bf4325c37L;
        h ^= (h >>> 47);

        return (h >>> 11) / (double) (1L << 53);
    }

    private void setBiomeSafe(BiomeGrid biomes, int x, int z, Biome biome) {
        try {
            biomes.setBiome(x, z, biome);
        } catch (Throwable ignored) {
            biomes.setBiome(x, z, Biome.PLAINS);
        }
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(
                new SoftSpruceForestPopulator(
                        GREEN_EDGE + GREEN_THICK + 26,
                        WORLD_HALF - 5,
                        yLevel,
                        38
                )
        );
    }

    public static final class SoftSpruceForestPopulator extends BlockPopulator {

        private final int inner;
        private final int outer;
        private final int yLevel;
        private final int chance;

        public SoftSpruceForestPopulator(int inner, int outer, int yLevel, int chancePercent) {
            this.inner = inner;
            this.outer = outer;
            this.yLevel = yLevel;
            this.chance = Math.max(1, Math.min(100, chancePercent));
        }

        @Override
        public void populate(World world, Random random, Chunk chunk) {
            if (random.nextInt(100) >= chance) return;

            int amount = 1 + random.nextInt(2);

            for (int i = 0; i < amount; i++) {
                int x = (chunk.getX() << 4) + 2 + random.nextInt(12);
                int z = (chunk.getZ() << 4) + 2 + random.nextInt(12);

                int distance = Math.max(Math.abs(x), Math.abs(z));

                if (distance < inner || distance > outer) {
                    continue;
                }

                int y = getTopSolid(world, x, z);

                if (y < yLevel) {
                    continue;
                }

                Material floor = world.getBlockAt(x, y, z).getType();

                if (floor != Material.GRASS_BLOCK
                        && floor != Material.DIRT
                        && floor != Material.COARSE_DIRT
                        && floor != Material.ROOTED_DIRT
                        && floor != Material.STONE
                        && floor != Material.COBBLESTONE
                        && floor != Material.ANDESITE
                        && floor != Material.TUFF) {
                    continue;
                }

                if (!hasSpace(world, x, y + 1, z)) {
                    continue;
                }

                buildSpruce(world, x, y + 1, z, random);
            }
        }

        private int getTopSolid(World world, int x, int z) {
            int max = Math.min(world.getMaxHeight() - 3, yLevel + MAX_MTN_H + 32);

            for (int y = max; y >= world.getMinHeight() + 2; y--) {
                if (world.getBlockAt(x, y, z).getType().isSolid()) {
                    return y;
                }
            }

            return -1;
        }

        private boolean hasSpace(World world, int x, int y, int z) {
            for (int dy = 0; dy <= 16; dy++) {
                if (!world.getBlockAt(x, y + dy, z).getType().isAir()) {
                    return false;
                }
            }

            return true;
        }

        private void buildSpruce(World world, int x, int y, int z, Random random) {
            int height = 9 + random.nextInt(10);

            for (int i = 0; i < height; i++) {
                world.getBlockAt(x, y + i, z).setType(Material.SPRUCE_LOG, false);
            }

            int top = y + height;

            for (int layer = 0; layer < height - 2; layer++) {
                int cy = top - layer;
                int radius = Math.max(1, Math.min(4, layer / 2 + 1));

                if (layer % 2 == 0 && radius > 1) {
                    radius--;
                }

                if (layer < 3) {
                    radius = 1;
                }

                for (int dx = -radius; dx <= radius; dx++) {
                    for (int dz = -radius; dz <= radius; dz++) {
                        int dist = Math.abs(dx) + Math.abs(dz);

                        if (dist > radius + 1) continue;
                        if (dx == 0 && dz == 0) continue;

                        Block block = world.getBlockAt(x + dx, cy, z + dz);

                        if (block.getType().isAir()) {
                            block.setType(Material.SPRUCE_LEAVES, false);
                        }
                    }
                }
            }

            world.getBlockAt(x, top + 1, z).setType(Material.SPRUCE_LEAVES, false);
        }
    }
}