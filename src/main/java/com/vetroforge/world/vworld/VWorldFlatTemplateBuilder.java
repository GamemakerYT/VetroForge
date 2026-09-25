// src/main/java/com/vetroforge/world/vworld/VWorldFlatTemplateBuilder.java
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

public final class VWorldFlatTemplateBuilder {

    private static final int BUILD_HALF = 100;
    private static final int RAID_WIDTH = 45;
    private static final int RED_EDGE = BUILD_HALF;
    private static final int GREEN_EDGE = BUILD_HALF + RAID_WIDTH + 1;
    private static final int OUTER_HALF = GREEN_EDGE + 80;

    private final FactionsPlugin plugin;
    private final VetroWorldTemplateService templateService;

    public VWorldFlatTemplateBuilder(FactionsPlugin plugin, VetroWorldTemplateService templateService) {
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
        final int y = plugin.getConfig().getInt("world-system.faction-worlds.y-level", 64);

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
            player.sendMessage("§6VWorld §8» §cTemplate-Welt konnte nicht erstellt werden.");
            return;
        }

        final World world = createdWorld;
        final Player targetPlayer = player;

        world.setSpawnLocation(new Location(world, 0.5, y + 1, 0.5, 0.0f, 0.0f));
        setupWorldRules(world);

        targetPlayer.sendMessage("§6VWorld §8» §fErstelle flache Faction-Vorlage §e" + finalTemplateName + "§f...");
        targetPlayer.teleport(new Location(world, 0.5, y + 3, 0.5, 0.0f, 0.0f));

        Queue<BlockOp> queue = new ArrayDeque<>();

        buildBase(queue, world, y);
        buildGlassLines(queue, world, y);
        buildMarkers(queue, world, y);

        runQueue(queue, () -> {
            saveTemplateData(finalTemplateName, world, y);

            if (targetPlayer.isOnline()) {
                targetPlayer.teleport(new Location(world, 0.5, y + 2, 0.5, 0.0f, 0.0f));
                targetPlayer.sendMessage("§6VWorld §8» §aTemplate-Welt wurde erstellt.");
                targetPlayer.sendMessage("§8» §7Welt: §f" + world.getName());
                targetPlayer.sendMessage("§8» §7Build: §f201x201 §8• §7Raid-Zone: §f45 Blöcke");
                targetPlayer.sendMessage("§8» §7Rot: §cBuild-Grenze §8• §7Grün: §aRaid-Grenze");
                targetPlayer.sendMessage("§8» §7Jetzt kannst du außen Berge/Wald/Caves bauen und später als Schematic speichern.");
                targetPlayer.playSound(targetPlayer.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
            }
        });
    }

    private void buildBase(Queue<BlockOp> queue, World world, int y) {
        for (int x = -OUTER_HALF; x <= OUTER_HALF; x++) {
            for (int z = -OUTER_HALF; z <= OUTER_HALF; z++) {
                queue.add(new BlockOp(world, x, y - 5, z, Material.BEDROCK));
                queue.add(new BlockOp(world, x, y - 4, z, Material.STONE));
                queue.add(new BlockOp(world, x, y - 3, z, Material.STONE));
                queue.add(new BlockOp(world, x, y - 2, z, Material.DIRT));
                queue.add(new BlockOp(world, x, y - 1, z, Material.DIRT));
                queue.add(new BlockOp(world, x, y, z, Material.GRASS_BLOCK));

                for (int airY = y + 1; airY <= y + 18; airY++) {
                    queue.add(new BlockOp(world, x, airY, z, Material.AIR));
                }
            }
        }
    }

    private void buildGlassLines(Queue<BlockOp> queue, World world, int y) {
        for (int i = -RED_EDGE; i <= RED_EDGE; i++) {
            queue.add(new BlockOp(world, RED_EDGE, y, i, Material.RED_STAINED_GLASS));
            queue.add(new BlockOp(world, -RED_EDGE, y, i, Material.RED_STAINED_GLASS));
            queue.add(new BlockOp(world, i, y, RED_EDGE, Material.RED_STAINED_GLASS));
            queue.add(new BlockOp(world, i, y, -RED_EDGE, Material.RED_STAINED_GLASS));
        }

        for (int i = -GREEN_EDGE; i <= GREEN_EDGE; i++) {
            queue.add(new BlockOp(world, GREEN_EDGE, y, i, Material.LIME_STAINED_GLASS));
            queue.add(new BlockOp(world, -GREEN_EDGE, y, i, Material.LIME_STAINED_GLASS));
            queue.add(new BlockOp(world, i, y, GREEN_EDGE, Material.LIME_STAINED_GLASS));
            queue.add(new BlockOp(world, i, y, -GREEN_EDGE, Material.LIME_STAINED_GLASS));
        }
    }

    private void buildMarkers(Queue<BlockOp> queue, World world, int y) {
        queue.add(new BlockOp(world, 0, y, 0, Material.GOLD_BLOCK));

        queue.add(new BlockOp(world, RED_EDGE + 8, y, 0, Material.DIAMOND_BLOCK));
        queue.add(new BlockOp(world, RED_EDGE + 8, y + 1, 0, Material.BEACON));

        queue.add(new BlockOp(world, 0, y + 20, 0, Material.EMERALD_BLOCK));
    }

    private void saveTemplateData(String templateName, World world, int y) {
        VetroWorldTemplate template = templateService.getOrCreate(templateName);

        template.setSchematic(templateName + ".schem");

        template.setPoint(VetroPointType.PASTEORIGIN, new VetroPoint(0.0, y, 0.0, 0.0f, 0.0f));
        template.setPoint(VetroPointType.SPAWN, new VetroPoint(0.5, y + 1.0, 0.5, 0.0f, 0.0f));
        template.setPoint(VetroPointType.RAIDSPAWN, new VetroPoint(RED_EDGE + 8.5, y + 1.0, 0.5, -90.0f, 0.0f));
        template.setPoint(VetroPointType.SCOUTSPAWN, new VetroPoint(0.5, y + 22.0, 0.5, 0.0f, 25.0f));

        template.setRegion(
                VetroRegionType.BUILD,
                new VetroRegion(
                        VetroRegionType.BUILD,
                        -BUILD_HALF,
                        y + 1,
                        -BUILD_HALF,
                        BUILD_HALF,
                        319,
                        BUILD_HALF
                )
        );

        template.setRegion(
                VetroRegionType.RAID,
                new VetroRegion(
                        VetroRegionType.RAID,
                        -GREEN_EDGE,
                        y + 1,
                        -GREEN_EDGE,
                        GREEN_EDGE,
                        319,
                        GREEN_EDGE
                )
        );

        template.setRegion(
                VetroRegionType.BORDER,
                new VetroRegion(
                        VetroRegionType.BORDER,
                        -GREEN_EDGE,
                        y,
                        -GREEN_EDGE,
                        GREEN_EDGE,
                        y,
                        GREEN_EDGE
                )
        );

        template.setRegion(
                VetroRegionType.TERRAIN,
                new VetroRegion(
                        VetroRegionType.TERRAIN,
                        -OUTER_HALF,
                        y,
                        -OUTER_HALF,
                        OUTER_HALF,
                        319,
                        OUTER_HALF
                )
        );

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

                while (!queue.isEmpty() && amount < 9000) {
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