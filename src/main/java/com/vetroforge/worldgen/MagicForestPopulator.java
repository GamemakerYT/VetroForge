package com.vetroforge.worldgen;

import org.bukkit.*;
import org.bukkit.block.Block;
import org.bukkit.generator.BlockPopulator;
import org.bukkit.Chunk;

import java.util.Random;

import static com.vetroforge.worldgen.RaidWorldLayout.*;

public class MagicForestPopulator extends BlockPopulator {
    private final int innerNoTreeHalf; // bis hier keine Bäume
    private final int outerLimitHalf;  // und nicht außerhalb der Welt
    private final int yLevel;
    private final int chance; // % pro Chunk

    public MagicForestPopulator(int innerNoTreeHalf, int outerLimitHalf, int yLevel, int chancePercent) {
        this.innerNoTreeHalf = innerNoTreeHalf;
        this.outerLimitHalf  = outerLimitHalf;
        this.yLevel          = yLevel;
        this.chance          = Math.max(1, Math.min(100, chancePercent));
    }

    @Override
    public void populate(World world, Random random, Chunk source) {
        if (random.nextInt(100) >= chance) return;

        int bx = (source.getX() << 4) + 2 + random.nextInt(12);
        int bz = (source.getZ() << 4) + 2 + random.nextInt(12);

        // Nur im Berggürtel
        int a = Math.max(Math.abs(bx), Math.abs(bz));
        if (a <= innerNoTreeHalf || a > outerLimitHalf) return;

        int y = highestY(world, bx, bz);
        if (y < yLevel) y = yLevel;

        buildMagicTree(world, bx, y + 1, bz, random);
    }

    private int highestY(World w, int x, int z) {
        int y = Math.min(w.getMaxHeight() - 2, yLevel + MAX_MTN_H + 2);
        while (y > w.getMinHeight() + 1) {
            Material m = w.getBlockAt(x, y, z).getType();
            if (m.isSolid()) return y;
            y--;
        }
        return yLevel;
    }

    private void buildMagicTree(World w, int x, int y, int z, Random rnd) {
        // Stamm
        int h = 7 + rnd.nextInt(5);
        for (int i = 0; i < h; i++) w.getBlockAt(x, y + i, z).setType(Material.WARPED_STEM, false);
        int top = y + h;

        // gläserne Kuppel
        int r = 3 + rnd.nextInt(2);
        for (int dx = -r; dx <= r; dx++) {
            for (int dz = -r; dz <= r; dz++) {
                if (dx*dx + dz*dz <= r*r) {
                    Block b = w.getBlockAt(x + dx, top, z + dz);
                    if (b.getType().isAir()) b.setType(Material.PURPLE_STAINED_GLASS, false);
                }
            }
        }
        // leichte Kaskade
        for (int i = 1; i <= 2; i++) {
            for (int dx = -r + i; dx <= r - i; dx++) {
                for (int dz = -r + i; dz <= r - i; dz++) {
                    if (dx*dx + dz*dz <= (r - i)*(r - i)) {
                        Block b = w.getBlockAt(x + dx, top - i, z + dz);
                        if (b.getType().isAir()) b.setType(Material.LIGHT_BLUE_STAINED_GLASS, false);
                    }
                }
            }
        }
        // Amethyst-Kern
        w.getBlockAt(x, top + 1, z).setType(Material.AMETHYST_BLOCK, false);
    }
}