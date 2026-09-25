package com.vetroforge.worldgen;

import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.generator.ChunkGenerator;

import org.bukkit.util.noise.SimplexOctaveGenerator;
import java.util.List;
import java.util.Random;

import static com.vetroforge.worldgen.RaidWorldLayout.*;

public class RaidWorldGenerator extends ChunkGenerator {

    private final int yLevel;

    public RaidWorldGenerator(int yLevel) { this.yLevel = yLevel; }

    @Override
    public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biome) {
        ChunkData data = createChunkData(world);

        // smoother Berge via Simplex
        SimplexOctaveGenerator noise = new SimplexOctaveGenerator(world.getSeed(), 3);
        noise.setScale(1.0 / 96.0);

        int minX = (chunkX << 4);
        int minZ = (chunkZ << 4);

        for (int dx = 0; dx < 16; dx++) {
            for (int dz = 0; dz < 16; dz++) {
                int x = minX + dx;
                int z = minZ + dz;

                if (!insideWorldSquare(x, z)) continue;

                // Untergrund-Sicherung
                data.setBlock(dx, yLevel - 1, dz, Material.BEDROCK);

                if (inBuild(x, z)) {
                    // Moderner Plot: glatte Platte + Grid + Achsen
                    data.setBlock(dx, yLevel, dz, coreTopBlock(x, z));
                } else if (atRedLine(x, z)) {
                    data.setBlock(dx, yLevel, dz, Material.RED_TERRACOTTA);
                } else if (atGreenLine(x, z)) {
                    data.setBlock(dx, yLevel, dz, Material.GREEN_TERRACOTTA);
                } else if (inBufferRaiders(x, z)) {
                    // Puffer/“Grün”-Fläche
                    data.setBlock(dx, yLevel, dz, Material.GRASS_BLOCK);
                } else if (inMountains(x, z)) {
                    int h = mountainHeight(x, z, noise);
                    // Stone-Sockel
                    for (int y = yLevel; y < y; y++) { /* no-op guard */ }
                    for (int y = yLevel; y < yLevel + h - 2; y++) data.setBlock(dx, y, dz, Material.STONE);
                    // Dirt-Schicht
                    data.setBlock(dx, yLevel + h - 2, dz, Material.DIRT);
                    data.setBlock(dx, yLevel + h - 1, dz, Material.DIRT);
                    // Oberseite
                    data.setBlock(dx, yLevel + h, dz, Material.GRASS_BLOCK);
                } else {
                    // äußerster Rest (sollte nicht oft vorkommen)
                    data.setBlock(dx, yLevel, dz, Material.GRASS_BLOCK);
                }
            }
        }

        // Goldblock exakt im Zentrum
        if (chunkX == 0 && chunkZ == 0) data.setBlock(0, yLevel, 0, Material.GOLD_BLOCK);

        return data;
    }

    private Material coreTopBlock(int x, int z) {
        // Sanfte, helle Oberfläche
        // Grid: alle 16 Blöcke (Chunk-Gitter) -> LIGHT_GRAY_CONCRETE
        if ((Math.floorMod(x, 16) == 0) || (Math.floorMod(z, 16) == 0)) {
            // Achsen (X oder Z == 0) -> GOLD_BLOCK
            if (x == 0 || z == 0) return Material.GOLD_BLOCK;
            return Material.LIGHT_GRAY_CONCRETE;
        }
        // sonst smooth stone
        return Material.SMOOTH_STONE;
    }

    private int mountainHeight(int x, int z, SimplexOctaveGenerator noise) {
        double t = mountainT(x, z);
        if (t <= 0) return 0;

        // glockenförmiges Profil (0..1..0), oben geglättet
        double bell = Math.sin(Math.PI * Math.min(1.0, t)); // 0..1..0
        // weiches Simplex-Rauschen
        double n = (noise.noise(x, z, 0.6, 0.5, true) + 1.0) * 0.5; // 0..1
        double h = (0.65 * bell + 0.35 * n * bell) * MAX_MTN_H;

        return Math.max(3, (int)Math.round(h)); // mind. 3 hoch
    }

    @Override
    public List<BlockPopulator> getDefaultPopulators(World world) {
        // Mystische Bäume nur im Berggürtel
        return List.of(new MagicForestPopulator(GREEN_EDGE + GREEN_THICK, WORLD_HALF, yLevel, 35));
    }
}