// src/main/java/com/vetroforge/world/vworld/VWorldSpawnWarzoneTemplateBuilder.java
package com.vetroforge.world.vworld;

import com.vetroforge.FactionsPlugin;
import org.bukkit.*;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitRunnable;

import java.util.ArrayDeque;
import java.util.Queue;
import java.util.Random;

public final class VWorldSpawnWarzoneTemplateBuilder {

    private final FactionsPlugin plugin;
    private final VetroWorldTemplateService templateService;

    private static final int WORLD_MIN_X = -140;
    private static final int WORLD_MAX_X = 140;
    private static final int WORLD_MIN_Z = -190;
    private static final int WORLD_MAX_Z = 230;

    private static final int SPAWN_Z = -140;
    private static final int RAMP_START_Z = -95;
    private static final int GATE_Z = -18;
    private static final int WARZONE_START_Z = -10;
    private static final int WARZONE_END_Z = 210;

    private static final int WARZONE_HALF_X = 110;

    public VWorldSpawnWarzoneTemplateBuilder(
            FactionsPlugin plugin,
            VetroWorldTemplateService templateService
    ) {
        this.plugin = plugin;
        this.templateService = templateService;
    }

    public void create(String templateName, Player player) {
        if (player == null) return;

        if (templateName == null || templateName.isBlank()) {
            player.sendMessage("§6VWorld §8» §cUngültiger Template-Name.");
            return;
        }

        final String finalTemplateName = templateName;
        final Player targetPlayer = player;

        final int baseY = plugin.getConfig().getInt("world-system.spawn-warzone.y-level", 64);
        final int spawnY = baseY + 18;

        String worldName = worldName(finalTemplateName);

        World createdWorld = Bukkit.getWorld(worldName);

        if (createdWorld == null) {
            WorldCreator creator = new WorldCreator(worldName);
            creator.environment(World.Environment.NORMAL);
            creator.type(WorldType.FLAT);
            creator.generator(new VoidGenerator());

            createdWorld = creator.createWorld();
        }

        if (createdWorld == null) {
            targetPlayer.sendMessage("§6VWorld §8» §cSpawn-Warzone-Template konnte nicht erstellt werden.");
            return;
        }

        final World finalWorld = createdWorld;

        setupWorldRules(finalWorld);

        finalWorld.setSpawnLocation(new Location(
                finalWorld,
                0.5,
                spawnY + 1.0,
                SPAWN_Z + 0.5,
                0.0f,
                0.0f
        ));

        targetPlayer.teleport(new Location(
                finalWorld,
                0.5,
                spawnY + 3.0,
                SPAWN_Z + 0.5,
                0.0f,
                0.0f
        ));

        targetPlayer.sendMessage("§6VWorld §8» §fErstelle Spawn-Warzone-Vorlage §e" + finalTemplateName + "§f...");
        targetPlayer.sendMessage("§8» §7Ja, diesmal mit Rampe und Warzone, nicht nur Rasenplatte. Fortschritt, wild.");

        Queue<BlockOp> queue = new ArrayDeque<>();

        clearArea(queue, finalWorld, baseY, spawnY);
        buildWarzoneGround(queue, finalWorld, baseY);
        buildSpawnPlatform(queue, finalWorld, spawnY);
        buildRamp(queue, finalWorld, baseY, spawnY);
        buildWarzoneGate(queue, finalWorld, baseY);
        buildWarzoneDecor(queue, finalWorld, baseY);
        buildLootdropPads(queue, finalWorld, baseY);
        buildBossArena(queue, finalWorld, baseY);
        buildMarkers(queue, finalWorld, baseY, spawnY);

        runQueue(queue, () -> {
            saveTemplateData(finalTemplateName, baseY, spawnY);

            Location spawn = new Location(
                    finalWorld,
                    0.5,
                    spawnY + 2.0,
                    SPAWN_Z + 0.5,
                    0.0f,
                    0.0f
            );

            finalWorld.setSpawnLocation(spawn);

            if (targetPlayer.isOnline()) {
                targetPlayer.teleport(spawn);

                targetPlayer.sendMessage("§6VWorld §8» §aSpawn-Warzone-Template wurde erstellt.");
                targetPlayer.sendMessage("§8» §7Welt: §f" + finalWorld.getName());
                targetPlayer.sendMessage("§8» §7Spawn oben, geradeaus runter zur Warzone.");
                targetPlayer.sendMessage("§8» §7Jetzt kannst du alles schöner bauen und danach als Schematic speichern.");
                targetPlayer.playSound(
                        targetPlayer.getLocation(),
                        Sound.UI_TOAST_CHALLENGE_COMPLETE,
                        1.0f,
                        1.2f
                );
            }
        });
    }

    private void clearArea(Queue<BlockOp> queue, World world, int baseY, int spawnY) {
        for (int x = WORLD_MIN_X; x <= WORLD_MAX_X; x++) {
            for (int z = WORLD_MIN_Z; z <= WORLD_MAX_Z; z++) {
                for (int y = baseY - 8; y <= spawnY + 35; y++) {
                    queue.add(new BlockOp(world, x, y, z, Material.AIR));
                }
            }
        }
    }

    private void buildWarzoneGround(Queue<BlockOp> queue, World world, int y) {
        for (int x = -WARZONE_HALF_X; x <= WARZONE_HALF_X; x++) {
            for (int z = WARZONE_START_Z; z <= WARZONE_END_Z; z++) {
                queue.add(new BlockOp(world, x, y - 5, z, Material.BEDROCK));
                queue.add(new BlockOp(world, x, y - 4, z, Material.STONE));
                queue.add(new BlockOp(world, x, y - 3, z, Material.STONE));
                queue.add(new BlockOp(world, x, y - 2, z, Material.DIRT));
                queue.add(new BlockOp(world, x, y - 1, z, Material.DIRT));

                Material top = chooseWarzoneTop(x, z);
                queue.add(new BlockOp(world, x, y, z, top));
            }
        }

        for (int x = -125; x <= 125; x++) {
            for (int z = -25; z <= WARZONE_END_Z + 15; z++) {
                if (Math.abs(x) <= WARZONE_HALF_X && z >= WARZONE_START_Z && z <= WARZONE_END_Z) continue;

                queue.add(new BlockOp(world, x, y - 5, z, Material.BEDROCK));
                queue.add(new BlockOp(world, x, y - 4, z, Material.STONE));
                queue.add(new BlockOp(world, x, y - 3, z, Material.STONE));
                queue.add(new BlockOp(world, x, y - 2, z, Material.DIRT));
                queue.add(new BlockOp(world, x, y - 1, z, Material.DIRT));
                queue.add(new BlockOp(world, x, y, z, Material.GRASS_BLOCK));
            }
        }
    }

    private Material chooseWarzoneTop(int x, int z) {
        int h = Math.abs((x * 31 + z * 17) % 100);

        if (h > 92) return Material.CRACKED_DEEPSLATE_TILES;
        if (h > 85) return Material.POLISHED_BLACKSTONE;
        if (h > 77) return Material.COARSE_DIRT;
        if (h > 68) return Material.GRAVEL;
        if (h > 58) return Material.ROOTED_DIRT;

        return Material.GRASS_BLOCK;
    }

    private void buildSpawnPlatform(Queue<BlockOp> queue, World world, int y) {
        for (int x = -42; x <= 42; x++) {
            for (int z = SPAWN_Z - 30; z <= SPAWN_Z + 30; z++) {
                queue.add(new BlockOp(world, x, y - 4, z, Material.POLISHED_DEEPSLATE));
                queue.add(new BlockOp(world, x, y - 3, z, Material.POLISHED_DEEPSLATE));
                queue.add(new BlockOp(world, x, y - 2, z, Material.DEEPSLATE_BRICKS));
                queue.add(new BlockOp(world, x, y - 1, z, Material.DEEPSLATE_BRICKS));

                boolean border = Math.abs(x) == 42 || z == SPAWN_Z - 30 || z == SPAWN_Z + 30;
                boolean path = Math.abs(x) <= 7;

                Material top = border
                        ? Material.POLISHED_BLACKSTONE
                        : path ? Material.SMOOTH_STONE : Material.POLISHED_DEEPSLATE;

                queue.add(new BlockOp(world, x, y, z, top));
            }
        }

        for (int x = -46; x <= 46; x++) {
            queue.add(new BlockOp(world, x, y + 1, SPAWN_Z - 33, Material.POLISHED_BLACKSTONE_WALL));
            queue.add(new BlockOp(world, x, y + 1, SPAWN_Z + 33, Material.POLISHED_BLACKSTONE_WALL));
        }

        for (int z = SPAWN_Z - 33; z <= SPAWN_Z + 33; z++) {
            queue.add(new BlockOp(world, -46, y + 1, z, Material.POLISHED_BLACKSTONE_WALL));
            queue.add(new BlockOp(world, 46, y + 1, z, Material.POLISHED_BLACKSTONE_WALL));
        }

        for (int x = -30; x <= 30; x += 10) {
            queue.add(new BlockOp(world, x, y + 1, SPAWN_Z + 25, Material.SEA_LANTERN));
        }

        queue.add(new BlockOp(world, 0, y + 1, SPAWN_Z, Material.BEACON));
    }

    private void buildRamp(Queue<BlockOp> queue, World world, int baseY, int spawnY) {
        int rampWidth = 17;
        int length = Math.abs(GATE_Z - RAMP_START_Z);

        for (int z = RAMP_START_Z; z <= GATE_Z; z++) {
            double progress = (z - RAMP_START_Z) / (double) length;
            int currentY = (int) Math.round(spawnY - progress * (spawnY - (baseY + 1)));

            for (int x = -rampWidth; x <= rampWidth; x++) {
                for (int y = baseY - 4; y <= currentY - 1; y++) {
                    Material fill = y < currentY - 3 ? Material.DEEPSLATE : Material.DEEPSLATE_BRICKS;
                    queue.add(new BlockOp(world, x, y, z, fill));
                }

                boolean side = Math.abs(x) >= rampWidth - 1;
                boolean center = Math.abs(x) <= 5;

                Material top = side
                        ? Material.POLISHED_BLACKSTONE
                        : center ? Material.SMOOTH_STONE : Material.POLISHED_DEEPSLATE;

                queue.add(new BlockOp(world, x, currentY, z, top));

                if (side) {
                    queue.add(new BlockOp(world, x, currentY + 1, z, Material.POLISHED_BLACKSTONE_WALL));
                }
            }
        }
    }

    private void buildWarzoneGate(Queue<BlockOp> queue, World world, int y) {
        for (int x = -28; x <= 28; x++) {
            queue.add(new BlockOp(world, x, y, GATE_Z, Material.RED_STAINED_GLASS));
        }

        for (int x = -31; x <= 31; x++) {
            for (int z = GATE_Z - 2; z <= GATE_Z + 2; z++) {
                queue.add(new BlockOp(world, x, y - 1, z, Material.POLISHED_BLACKSTONE));
            }
        }

        buildPillar(queue, world, -32, y + 1, GATE_Z, 12);
        buildPillar(queue, world, 32, y + 1, GATE_Z, 12);

        for (int x = -32; x <= 32; x++) {
            queue.add(new BlockOp(world, x, y + 13, GATE_Z, Material.POLISHED_BLACKSTONE));
            if (Math.abs(x) % 5 == 0) {
                queue.add(new BlockOp(world, x, y + 12, GATE_Z, Material.REDSTONE_LAMP));
            }
        }

        for (int x = -24; x <= 24; x += 8) {
            queue.add(new BlockOp(world, x, y + 1, GATE_Z + 3, Material.RED_STAINED_GLASS));
        }
    }

    private void buildPillar(Queue<BlockOp> queue, World world, int x, int y, int z, int height) {
        for (int i = 0; i < height; i++) {
            Material mat = i % 4 == 0 ? Material.CHISELED_POLISHED_BLACKSTONE : Material.POLISHED_BLACKSTONE_BRICKS;
            queue.add(new BlockOp(world, x, y + i, z, mat));
            queue.add(new BlockOp(world, x + 1, y + i, z, mat));
            queue.add(new BlockOp(world, x, y + i, z + 1, mat));
            queue.add(new BlockOp(world, x + 1, y + i, z + 1, mat));
        }
    }

    private void buildWarzoneDecor(Queue<BlockOp> queue, World world, int y) {
        buildRuin(queue, world, -70, y + 1, 45);
        buildRuin(queue, world, 68, y + 1, 75);
        buildRuin(queue, world, -45, y + 1, 145);
        buildRuin(queue, world, 55, y + 1, 165);

        for (int i = 0; i < 18; i++) {
            int x = -95 + i * 11;
            int z = 15 + Math.abs((i * 37) % 165);

            queue.add(new BlockOp(world, x, y + 1, z, Material.COBBLESTONE));
            if (i % 3 == 0) {
                queue.add(new BlockOp(world, x, y + 2, z, Material.MOSSY_COBBLESTONE));
            }
        }
    }

    private void buildRuin(Queue<BlockOp> queue, World world, int cx, int y, int cz) {
        for (int x = -5; x <= 5; x++) {
            for (int z = -5; z <= 5; z++) {
                if (Math.abs(x) == 5 || Math.abs(z) == 5) {
                    queue.add(new BlockOp(world, cx + x, y, cz + z, Material.COBBLESTONE));
                }
            }
        }

        for (int i = 0; i < 5; i++) {
            queue.add(new BlockOp(world, cx - 5, y + i, cz - 5, Material.MOSSY_COBBLESTONE));
            queue.add(new BlockOp(world, cx + 5, y + i, cz + 5, Material.COBBLESTONE));
        }

        queue.add(new BlockOp(world, cx, y, cz, Material.CRACKED_DEEPSLATE_TILES));
    }

    private void buildLootdropPads(Queue<BlockOp> queue, World world, int y) {
        buildPad(queue, world, -55, y, 55, Material.AMETHYST_BLOCK);
        buildPad(queue, world, 55, y, 55, Material.AMETHYST_BLOCK);
        buildPad(queue, world, -55, y, 120, Material.AMETHYST_BLOCK);
        buildPad(queue, world, 55, y, 120, Material.AMETHYST_BLOCK);
    }

    private void buildPad(Queue<BlockOp> queue, World world, int cx, int y, int cz, Material center) {
        for (int x = -4; x <= 4; x++) {
            for (int z = -4; z <= 4; z++) {
                int d = Math.abs(x) + Math.abs(z);

                if (d <= 5) {
                    Material mat = d <= 1 ? center : Material.POLISHED_BLACKSTONE;
                    queue.add(new BlockOp(world, cx + x, y + 1, cz + z, mat));
                }
            }
        }

        queue.add(new BlockOp(world, cx, y + 2, cz, Material.END_ROD));
    }

    private void buildBossArena(Queue<BlockOp> queue, World world, int y) {
        int cx = 0;
        int cz = 165;
        int radius = 28;

        for (int x = -radius; x <= radius; x++) {
            for (int z = -radius; z <= radius; z++) {
                int distSq = x * x + z * z;

                if (distSq <= radius * radius) {
                    Material mat;

                    if (distSq > (radius - 2) * (radius - 2)) {
                        mat = Material.OBSIDIAN;
                    } else if (distSq < 16) {
                        mat = Material.CRYING_OBSIDIAN;
                    } else {
                        mat = Material.CRACKED_DEEPSLATE_TILES;
                    }

                    queue.add(new BlockOp(world, cx + x, y + 1, cz + z, mat));
                }
            }
        }

        queue.add(new BlockOp(world, cx, y + 2, cz, Material.RESPAWN_ANCHOR));

        for (int x = -20; x <= 20; x += 10) {
            queue.add(new BlockOp(world, cx + x, y + 2, cz - 20, Material.SOUL_LANTERN));
            queue.add(new BlockOp(world, cx + x, y + 2, cz + 20, Material.SOUL_LANTERN));
        }
    }

    private void buildMarkers(Queue<BlockOp> queue, World world, int baseY, int spawnY) {
        queue.add(new BlockOp(world, 0, spawnY + 1, SPAWN_Z, Material.GOLD_BLOCK));
        queue.add(new BlockOp(world, 0, baseY + 2, GATE_Z, Material.REDSTONE_BLOCK));
        queue.add(new BlockOp(world, 0, baseY + 2, 20, Material.DIAMOND_BLOCK));
        queue.add(new BlockOp(world, 0, baseY + 3, 165, Material.BEACON));    }

    private void saveTemplateData(String templateName, int baseY, int spawnY) {
        VetroWorldTemplate template = templateService.getOrCreate(templateName);

        template.setSchematic(templateName + ".schem");

        template.setPoint(VetroPointType.PASTEORIGIN, new VetroPoint(0.0, baseY, 0.0, 0.0f, 0.0f));

        template.setPoint(VetroPointType.SPAWN, new VetroPoint(0.5, spawnY + 1.0, SPAWN_Z + 0.5, 0.0f, 0.0f));
        template.setPoint(VetroPointType.WARZONEGATE, new VetroPoint(0.5, baseY + 2.0, GATE_Z + 0.5, 0.0f, 0.0f));
        template.setPoint(VetroPointType.WARZONESPAWN, new VetroPoint(0.5, baseY + 1.0, 20.5, 0.0f, 0.0f));
        template.setPoint(VetroPointType.BOSS, new VetroPoint(0.5, baseY + 2.0, 165.5, 180.0f, 0.0f));

        template.setPoint(VetroPointType.LOOTDROP1, new VetroPoint(-55.5, baseY + 2.0, 55.5, 0.0f, 0.0f));
        template.setPoint(VetroPointType.LOOTDROP2, new VetroPoint(55.5, baseY + 2.0, 55.5, 0.0f, 0.0f));
        template.setPoint(VetroPointType.LOOTDROP3, new VetroPoint(-55.5, baseY + 2.0, 120.5, 0.0f, 0.0f));
        template.setPoint(VetroPointType.LOOTDROP4, new VetroPoint(55.5, baseY + 2.0, 120.5, 0.0f, 0.0f));

        template.setRegion(VetroRegionType.SPAWN, new VetroRegion(
                VetroRegionType.SPAWN,
                -46, spawnY, SPAWN_Z - 35,
                46, 319, SPAWN_Z + 35
        ));

        template.setRegion(VetroRegionType.SPAWN_SAFE, new VetroRegion(
                VetroRegionType.SPAWN_SAFE,
                -50, baseY, SPAWN_Z - 40,
                50, 319, GATE_Z - 1
        ));

        template.setRegion(VetroRegionType.WARZONE_GATE, new VetroRegion(
                VetroRegionType.WARZONE_GATE,
                -35, baseY, GATE_Z - 4,
                35, 319, GATE_Z + 5
        ));

        template.setRegion(VetroRegionType.WARZONE, new VetroRegion(
                VetroRegionType.WARZONE,
                -WARZONE_HALF_X, baseY,
                WARZONE_START_Z,
                WARZONE_HALF_X, 319,
                WARZONE_END_Z
        ));

        template.setRegion(VetroRegionType.LOOTDROP, new VetroRegion(
                VetroRegionType.LOOTDROP,
                -80, baseY,
                35,
                80, 319,
                135
        ));

        template.setRegion(VetroRegionType.BOSS, new VetroRegion(
                VetroRegionType.BOSS,
                -35, baseY,
                130,
                35, 319,
                200
        ));

        template.setRegion(VetroRegionType.TERRAIN, new VetroRegion(
                VetroRegionType.TERRAIN,
                WORLD_MIN_X, baseY - 8,
                WORLD_MIN_Z,
                WORLD_MAX_X, 319,
                WORLD_MAX_Z
        ));

        templateService.setActiveTemplate(templateName);
        templateService.save();
    }

    private void setupWorldRules(World world) {
        trySetRule(world, GameRule.DO_MOB_SPAWNING, false);
        trySetRule(world, GameRule.DO_PATROL_SPAWNING, false);
        trySetRule(world, GameRule.DO_TRADER_SPAWNING, false);
        trySetRule(world, GameRule.DO_WEATHER_CYCLE, false);
        trySetRule(world, GameRule.DO_DAYLIGHT_CYCLE, false);
        trySetRule(world, GameRule.DO_FIRE_TICK, false);
        trySetRule(world, GameRule.MOB_GRIEFING, false);

        world.setTime(6000L);
        world.setStorm(false);
        world.setThundering(false);
    }

    private <T> void trySetRule(World world, GameRule<T> rule, T value) {
        try {
            world.setGameRule(rule, value);
        } catch (Throwable ignored) {}
    }

    private void runQueue(Queue<BlockOp> queue, Runnable done) {
        new BukkitRunnable() {
            @Override
            public void run() {
                int amount = 0;

                while (!queue.isEmpty() && amount < 10000) {
                    BlockOp op = queue.poll();

                    if (op != null) {
                        op.apply();
                    }

                    amount++;
                }

                if (queue.isEmpty()) {
                    cancel();

                    if (done != null) {
                        done.run();
                    }
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private String worldName(String templateName) {
        return "vtemplate_" + templateName.toLowerCase()
                .replaceAll("[^a-z0-9_\\-]", "_");
    }

    private record BlockOp(World world, int x, int y, int z, Material material) {
        private void apply() {
            if (world == null) return;
            world.getBlockAt(x, y, z).setType(material, false);
        }
    }

    private static final class VoidGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int chunkX, int chunkZ, BiomeGrid biomes) {
            ChunkData data = createChunkData(world);

            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    try {
                        biomes.setBiome(x, z, Biome.PLAINS);
                    } catch (Throwable ignored) {}
                }
            }

            return data;
        }

        @Override public boolean shouldGenerateSurface() { return false; }
        @Override public boolean shouldGenerateCaves() { return false; }
        @Override public boolean shouldGenerateMobs() { return false; }
        @Override public boolean shouldGenerateDecorations() { return false; }
        @Override public boolean shouldGenerateStructures() { return false; }
        @Override public boolean shouldGenerateNoise() { return false; }
        @Override public boolean shouldGenerateBedrock() { return false; }
    }
}