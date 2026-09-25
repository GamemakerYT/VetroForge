// src/main/java/com/vetroforge/worldgen/CustomChunkGenerator.java
package com.vetroforge.worldgen;

import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.generator.ChunkGenerator;

import java.util.Random;

/** Mehrschichtiger Flat-Generator (z. B. Stone/Dirt/Grass) mit optionaler Bedrock-Schicht. */
public class CustomChunkGenerator extends ChunkGenerator {
    private final int groundY;
    private final Material top;   private final int topH;
    private final Material mid;   private final int midH;
    private final Material bot;   private final int botH;
    private final boolean bedrockFloor;

    public CustomChunkGenerator(int groundY,
                                Material top, int topH,
                                Material mid, int midH,
                                Material bot, int botH,
                                boolean bedrockFloor) {
        this.groundY = groundY;
        this.top = top; this.topH = topH;
        this.mid = mid; this.midH = midH;
        this.bot = bot; this.botH = botH;
        this.bedrockFloor = bedrockFloor;
    }

    @Override
    public ChunkData generateChunkData(World world, Random random, int cx, int cz, BiomeGrid biomes) {
        ChunkData data = createChunkData(world);
        for (int x=0;x<16;x++) for (int z=0;z<16;z++) biomes.setBiome(x,z, Biome.PLAINS);

        int y = groundY;
        // bottom layer
        for (int yy=y-botH; yy<y; yy++) if (yy>=0) for (int dx=0;dx<16;dx++) for (int dz=0;dz<16;dz++) data.setBlock(dx,yy,dz,bot);
        // middle
        int midStart = y, midEnd = y+midH;
        for (int yy=midStart; yy<midEnd; yy++) for (int dx=0;dx<16;dx++) for (int dz=0;dz<16;dz++) data.setBlock(dx,yy,dz,mid);
        // top
        int topStart = midEnd, topEnd = midEnd+topH;
        for (int yy=topStart; yy<topEnd; yy++) for (int dx=0;dx<16;dx++) for (int dz=0;dz<16;dz++) data.setBlock(dx,yy,dz,top);
        // bedrock at y=0
        if (bedrockFloor) for (int dx=0;dx<16;dx++) for (int dz=0;dz<16;dz++) data.setBlock(dx,0,dz,Material.BEDROCK);

        return data;
    }

    @Override public boolean shouldGenerateCaves()       { return false; }
    @Override public boolean shouldGenerateMobs()        { return false; }
    @Override public boolean shouldGenerateDecorations() { return false; }
    @Override public boolean shouldGenerateStructures()  { return false; }
    @Override public boolean shouldGenerateNoise()       { return false; }
    @Override public boolean shouldGenerateBedrock()     { return false; }
}
