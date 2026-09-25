package com.vetroforge.worldgen;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.Chunk;

import java.util.Random;

import static com.vetroforge.worldgen.RaidWorldLayout.*;

public class CherryFantasyPopulator extends BlockPopulator {

    private final int inner;
    private final int outer;
    private final int yLevel;

    public CherryFantasyPopulator(int inner, int outer, int yLevel) {
        this.inner = inner;
        this.outer = outer;
        this.yLevel = yLevel;
    }

    @Override
    public void populate(World world, Random random, Chunk chunk) {

        if (random.nextInt(100) > 35) return;

        int x = (chunk.getX() << 4) + random.nextInt(16);
        int z = (chunk.getZ() << 4) + random.nextInt(16);

        int a = Math.max(Math.abs(x), Math.abs(z));
        if (a <= inner || a > outer) return;

        int y = world.getHighestBlockYAt(x, z);

        buildTree(world, x, y + 1, z, random);
    }

    private void buildTree(World w, int x, int y, int z, Random r) {

        int height = 5 + r.nextInt(3);

        for (int i = 0; i < height; i++) {
            w.getBlockAt(x, y + i, z).setType(Material.CHERRY_LOG);
        }

        int top = y + height;

        int radius = 3;

        for (int dx = -radius; dx <= radius; dx++) {
            for (int dz = -radius; dz <= radius; dz++) {

                if (dx * dx + dz * dz <= radius * radius) {
                    Block b = w.getBlockAt(x + dx, top, z + dz);
                    if (b.getType().isAir()) {
                        b.setType(Material.CHERRY_LEAVES);
                    }
                }
            }
        }
    }
}