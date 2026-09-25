package com.vetroforge.worldgen;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import java.util.List;
import java.util.Random;

import static com.vetroforge.worldgen.RaidWorldLayout.*;

public class CleanFantasyArenaGenerator extends ChunkGenerator {

    private final int yLevel;

    public CleanFantasyArenaGenerator(int yLevel) {
        this.yLevel = yLevel;
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        ChunkData data = createChunkData(world);

        int minX = chunkX << 4;
        int minZ = chunkZ << 4;

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {

                int x = minX + dx;
                int z = minZ + dz;

                if (!insideWorldSquare(x, z)) continue;

                // Base
                data.setBlock(dx, yLevel - 1, dz, Material.BEDROCK);

                // ===== CORE =====
                if (inBuild(x, z)) {
                    data.setBlock(dx, yLevel, dz, Material.GRASS_BLOCK);
                    continue;
                }

                // ===== RED LINE =====
                if (atRedLine(x, z)) {
                    data.setBlock(dx, yLevel, dz, Material.RED_TERRACOTTA);
                    continue;
                }

                // ===== BUFFER =====
                if (inBufferRaiders(x, z)) {
                    data.setBlock(dx, yLevel, dz, Material.GRASS_BLOCK);
                    continue;
                }

                // ===== GREEN LINE =====
                if (atGreenLine(x, z)) {
                    data.setBlock(dx, yLevel, dz, Material.GREEN_TERRACOTTA);
                    continue;
                }

                // ===== BERGE (CLEAN + GEFÜLLT) =====
                if (inMountains(x, z)) {

                    int height = mountainHeightFlat(x, z);

                    // komplett gefüllt (kein hohler Müll)
                    for (int y = yLevel; y <= yLevel + height; y++) {

                        if (y < yLevel + height - 2) {
                            data.setBlock(dx, y, dz, Material.STONE);
                        } else if (y < yLevel + height) {
                            data.setBlock(dx, y, dz, Material.DIRT);
                        } else {
                            data.setBlock(dx, y, dz, Material.GRASS_BLOCK);
                        }
                    }

                    biome.setBiome(dx, dz, Biome.CHERRY_GROVE);
                    continue;
                }

                data.setBlock(dx, yLevel, dz, Material.GRASS_BLOCK);
            }
        }

        return data;
    }

    // ⭐ DAS IST DER KEY FIX → keine wilden spikes mehr
    private int mountainHeightFlat(int x, int z) {

        double t = mountainT(x, z); // 0..1

        // sanfter Verlauf wie im Bild
        double smooth = Math.sin(t * Math.PI);

        // leichte Variation (aber KEINE Peaks)
        double noise = (hash(x, z) - 0.5) * 0.3;

        double h = (smooth * 10.0) + (noise * 4.0);

        return Math.max(6, (int) Math.round(h));
    }

    private double hash(int x, int z) {
        long h = x * 341873128712L ^ z * 132897987541L;
        h ^= (h >> 13);
        return ((h & 0xFFFF) / (double) 0xFFFF);
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        return List.of(new CherryFantasyPopulator(GREEN_EDGE + GREEN_THICK, WORLD_HALF, yLevel));
    }
}