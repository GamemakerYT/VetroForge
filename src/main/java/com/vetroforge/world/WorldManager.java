// src/main/java/com/vetroforge/world/WorldManager.java
package com.vetroforge.world;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.GradientUtil;
import com.vetroforge.worldgen.MysticRaidArenaGenerator;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import net.kyori.adventure.title.Title;
import org.bukkit.Bukkit;
import org.bukkit.GameRule;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.block.Biome;
import org.bukkit.entity.Player;
import org.bukkit.generator.ChunkGenerator;
import org.bukkit.scheduler.BukkitRunnable;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.StandardOpenOption;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.Random;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

public class WorldManager {

    public enum GeneratorType {
        RAID_ARENA_MYSTIC,
        VOID
    }

    private final FactionsPlugin plugin;
    private final Path worldRoot;
    private final Path backupsRoot;

    private static final DateTimeFormatter TS_FMT = DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");
    private static final String GEN_MARKER_FILE = "vetroforge.gen";

    public WorldManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.worldRoot = Bukkit.getWorldContainer().toPath();
        this.backupsRoot = plugin.getDataFolder().toPath().resolve("backups");

        try {
            Files.createDirectories(backupsRoot);
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte backups-Ordner nicht anlegen: " + e.getMessage());
        }
    }

    public String worldNameForFaction(String faction) {
        if (faction == null || faction.isBlank()) {
            return "faction_unknown";
        }

        return "faction_" + faction.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-.]", "_");
    }

    public List<String> listFactionWorldsOnDisk() {
        List<String> out = new ArrayList<>();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(worldRoot)) {
            for (Path path : ds) {
                if (!Files.isDirectory(path)) continue;

                String name = path.getFileName().toString();

                if (name.startsWith("faction_")) {
                    out.add(name);
                }
            }
        } catch (IOException ignored) {}

        Collections.sort(out);
        return out;
    }

    public boolean createIfMissing(String worldName) {
        try {
            if (Bukkit.getWorld(worldName) != null) {
                return true;
            }

            Path folder = worldRoot.resolve(worldName);

            if (Files.exists(folder)) {
                Bukkit.createWorld(new WorldCreator(worldName));
                return Bukkit.getWorld(worldName) != null;
            }

            return createWorld(worldName, readGenTypeFromConfig()) != null;
        } catch (Throwable t) {
            plugin.getLogger().warning("createIfMissing(" + worldName + "): " + t.getMessage());
            return false;
        }
    }

    public boolean teleportToFactionWorld(Player player, String worldName) {
        if (player == null || worldName == null || worldName.isBlank()) {
            return false;
        }

        if (!createIfMissing(worldName)) {
            return false;
        }

        World world = Bukkit.getWorld(worldName);

        return world != null && player.teleport(world.getSpawnLocation());
    }

    public String infoFactionWorld(String worldName) {
        World world = Bukkit.getWorld(worldName);
        boolean loaded = world != null;

        StringBuilder sb = new StringBuilder();
        sb.append("loaded=").append(loaded);

        if (loaded) {
            Location spawn = world.getSpawnLocation();

            sb.append(", players=").append(world.getPlayers().size());
            sb.append(", spawn=").append(fmtLoc(spawn.getBlockX(), spawn.getBlockY(), spawn.getBlockZ()));
            sb.append(", seed=").append(world.getSeed());
        } else {
            Path dir = worldRoot.resolve(worldName);
            sb.append(", onDisk=").append(Files.exists(dir));
        }

        return sb.toString();
    }

    /** Sichert und entfernt die Welt einer disbandeten Faction, damit der Name wirklich frei wird -
     *  sonst bekommt eine neu erstellte Faction mit demselben Namen sonst die alte Welt (inkl. fremder Baustellen). */
    public void archiveAndDeleteFactionWorld(String factionName) {
        String worldName = worldNameForFaction(factionName);

        try {
            backupWorld(worldName);
        } catch (Throwable ignored) {}

        unloadWorldSafely(worldName);
        deleteWorldFolder(worldName);
    }

    public boolean regenerateFactionWorld(String worldName) {
        try {
            backupWorld(worldName);
        } catch (Throwable ignored) {}

        unloadWorldSafely(worldName);

        if (!deleteWorldFolder(worldName)) {
            return false;
        }

        return createIfMissing(worldName);
    }

    public void unloadWorldSafely(String worldName) {
        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            return;
        }

        try {
            world.save();
        } catch (Throwable ignored) {}

        try {
            Bukkit.unloadWorld(world, true);
        } catch (Throwable ignored) {}
    }

    public boolean deleteWorldFolder(String worldName) {
        Path dir = worldRoot.resolve(worldName);

        if (!Files.exists(dir)) {
            return true;
        }

        try {
            return deleteRecursive(dir);
        } catch (IOException e) {
            plugin.getLogger().warning("deleteWorldFolder(" + worldName + "): " + e.getMessage());
            return false;
        }
    }

    public List<Path> listBackupsForWorld(String worldName) {
        Path dir = backupsRoot.resolve(worldName);

        if (!Files.exists(dir)) {
            return Collections.emptyList();
        }

        List<Path> out = new ArrayList<>();

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(dir, "*.zip")) {
            for (Path path : ds) {
                out.add(path);
            }
        } catch (IOException ignored) {}

        out.sort((a, b) -> Long.compare(fileTime(b), fileTime(a)));
        return out;
    }

    public Path getLatestBackup(String worldName) {
        List<Path> list = listBackupsForWorld(worldName);
        return list.isEmpty() ? null : list.get(0);
    }

    public Path backupWorld(String worldName) {
        Path src = worldRoot.resolve(worldName);

        if (!Files.exists(src)) {
            plugin.getLogger().warning("Backup: Ordner existiert nicht: " + src);
            return null;
        }

        World world = Bukkit.getWorld(worldName);

        if (world != null) {
            try {
                world.save();
            } catch (Throwable ignored) {}
        }

        Path dstDir = backupsRoot.resolve(worldName);

        try {
            Files.createDirectories(dstDir);
        } catch (IOException ignored) {}

        String ts = LocalDateTime.now().format(TS_FMT);
        Path zip = dstDir.resolve(worldName + "_" + ts + ".zip");

        try {
            zipFolder(src, zip);
            return zip;
        } catch (IOException e) {
            plugin.getLogger().warning("Backup fehlgeschlagen: " + e.getMessage());
            return null;
        }
    }

    public boolean restoreFactionWorld(String worldName, String backupFileName) {
        Path backup = backupsRoot.resolve(worldName).resolve(backupFileName);

        if (!Files.exists(backup)) {
            plugin.getLogger().warning("Restore: Backup nicht gefunden: " + backup);
            return false;
        }

        try {
            unloadWorldSafely(worldName);
            deleteWorldFolder(worldName);
            unzipTo(backup, worldRoot.resolve(worldName));
            return createIfMissing(worldName);
        } catch (IOException e) {
            plugin.getLogger().warning("Restore fehlgeschlagen: " + e.getMessage());
            return false;
        }
    }

    public void createFactionWorldWithAnimation(Player player, String factionName) {
        createFactionWorldWithAnimation(player, factionName, null);
    }

    public void createFactionWorldWithAnimation(Player player, String factionName, Runnable onFailure) {
        if (player == null || factionName == null || factionName.isBlank()) {
            return;
        }

        final String titleLegacy = GradientUtil.raidTitle("⚔ Faction-Welt wird erschaffen ⚔");

        final String[] frames = new String[] {
                GradientUtil.gradient("Fundament wird gelegt...", "#FFFFFF", "#DDDDDD"),
                GradientUtil.gradient("Build-Plot wird markiert...", "#FFFFFF", "#DDDDDD"),
                GradientUtil.gradient("Grenzen werden gezogen...", "#FFFFFF", "#DDDDDD"),
                GradientUtil.gradient("Raid-Zone wird vorbereitet...", "#FFFFFF", "#DDDDDD"),
                GradientUtil.gradient("Letzte Details...", "#FFFFFF", "#DDDDDD"),
                GradientUtil.gradient("Gleich geschafft...", "#FFFFFF", "#DDDDDD")
        };

        new BukkitRunnable() {
            int idx = 0;
            int ticks = 0;

            @Override
            public void run() {
                if (!player.isOnline()) {
                    cancel();
                    return;
                }

                String subLegacy = frames[idx++ % frames.length];

                Title.Times times = Title.Times.times(
                        Duration.ofMillis(0),
                        Duration.ofMillis(500),
                        Duration.ofMillis(0)
                );

                player.showTitle(Title.title(
                        LegacyComponentSerializer.legacySection().deserialize(titleLegacy),
                        LegacyComponentSerializer.legacySection().deserialize(subLegacy),
                        times
                ));

                ticks += 10;

                if (ticks < 120) {
                    return;
                }

                cancel();

                World world = createOrLoadFactionWorld(factionName);

                if (world == null) {
                    if (onFailure != null) {
                        try {
                            onFailure.run();
                        } catch (Throwable ignored) {}
                    }
                    player.showTitle(Title.title(
                            LegacyComponentSerializer.legacySection().deserialize(GradientUtil.brandRed("Fehler")),
                            LegacyComponentSerializer.legacySection().deserialize("§cWelt konnte nicht erstellt werden."),
                            Title.Times.times(
                                    Duration.ofMillis(150),
                                    Duration.ofMillis(1600),
                                    Duration.ofMillis(400)
                            )
                    ));
                    return;
                }

                int y = plugin.getConfig().getInt("factions.world.yLevel", 64);
                Location spawn = new Location(world, 0.5, y + 1, 0.5, 0.0f, 0.0f);

                world.setSpawnLocation(spawn);
                player.teleport(spawn);
                if (plugin.getFactionManager() != null) {
                    plugin.getFactionManager().setHome(factionName, spawn);
                    try {
                        if (plugin.getPersistenceManager() != null) {
                            plugin.getPersistenceManager().saveAll();
                        }
                    } catch (Throwable ignored) {}
                }

                player.sendMessage("§6Faction §8» §aDeine Faction-Welt wurde erstellt. §7Viel Spaß und guten Kampf!");
            }
        }.runTaskTimer(plugin, 0L, 10L);
    }

    public World createOrLoadFactionWorld(String factionName) {
        String worldName = worldNameForFaction(factionName);
        Path folder = worldRoot.resolve(worldName);

        if (Files.exists(folder)) {
            if (shouldRegenerateForGenerator(worldName)) {
                plugin.getLogger().warning("[WorldManager] Welt '" + worldName + "' passt nicht zum gewünschten Generator. Regeneriere...");

                if (!regenerateFactionWorld(worldName)) {
                    plugin.getLogger().warning("[WorldManager] Regeneration von '" + worldName + "' fehlgeschlagen.");
                    return null;
                }

                return Bukkit.getWorld(worldName);
            }

            plugin.getLogger().info("[WorldManager] Lade existierende Welt '" + worldName + "'.");
            return Bukkit.createWorld(new WorldCreator(worldName));
        }

        GeneratorType type = readGenTypeFromConfig();

        plugin.getLogger().info("[WorldManager] Erzeuge neue Welt '" + worldName + "' mit Generator " + type.name());
        return createWorld(worldName, type);
    }

    public World createWorld(String worldName, GeneratorType type) {
        WorldCreator wc = new WorldCreator(worldName);

        wc.environment(World.Environment.NORMAL);
        wc.type(WorldType.NORMAL);

        switch (type) {
            case RAID_ARENA_MYSTIC -> {
                int y = plugin.getConfig().getInt("factions.world.yLevel", 64);
                wc.generator(new MysticRaidArenaGenerator(y));
            }

            case VOID -> wc.generator(new SimpleVoidGenerator());

            default -> wc.generator(new MysticRaidArenaGenerator(
                    plugin.getConfig().getInt("factions.world.yLevel", 64)
            ));
        }

        World world = wc.createWorld();

        if (world != null) {
            int y = plugin.getConfig().getInt("factions.world.yLevel", 64);
            Location spawn = new Location(world, 0.5, y + 1, 0.5, 0.0f, 0.0f);

            world.setSpawnLocation(spawn);

            trySetRule(world, GameRule.DO_MOB_SPAWNING, false);
            trySetRule(world, GameRule.DO_PATROL_SPAWNING, false);
            trySetRule(world, GameRule.DO_TRADER_SPAWNING, false);
            trySetRule(world, GameRule.DO_WEATHER_CYCLE, false);
            trySetRule(world, GameRule.DO_DAYLIGHT_CYCLE, false);
            trySetRule(world, GameRule.DO_FIRE_TICK, false);
            trySetRule(world, GameRule.MOB_GRIEFING, false);
            trySetRule(world, GameRule.ANNOUNCE_ADVANCEMENTS, false);
            trySetRule(world, GameRule.SHOW_DEATH_MESSAGES, false);

            world.setTime(6000L);
            world.setStorm(false);
            world.setThundering(false);

            writeGeneratorMarker(worldName, type, y);
        }

        return world;
    }

    public Location getFactionSpawn(String faction) {
        if (faction == null || faction.isBlank()) {
            return null;
        }

        String worldName = worldNameForFaction(faction);

        World world = Bukkit.getWorld(worldName);

        if (world == null) {
            world = createOrLoadFactionWorld(faction);

            if (world == null) {
                return null;
            }
        }

        int y = plugin.getConfig().getInt("factions.world.yLevel", 64);

        return new Location(world, 0.5, y + 1, 0.5, 0.0f, 0.0f);
    }

    public void ensureFactionWorld(String faction) {
        if (faction == null || faction.isBlank()) {
            return;
        }

        createOrLoadFactionWorld(faction);
    }

    private GeneratorType readGenTypeFromConfig() {
        String key = plugin.getConfig()
                .getString("factions.world.generator", "RAID_ARENA_MYSTIC")
                .toUpperCase(Locale.ROOT);

        try {
            return GeneratorType.valueOf(key);
        } catch (IllegalArgumentException ex) {
            return GeneratorType.RAID_ARENA_MYSTIC;
        }
    }

    private boolean shouldRegenerateForGenerator(String worldName) {
        boolean force = plugin.getConfig().getBoolean("factions.world.forceRegenerateOnCreate", false);

        if (!force) {
            return false;
        }

        Path folder = worldRoot.resolve(worldName);
        Path marker = folder.resolve(GEN_MARKER_FILE);

        GeneratorType desired = readGenTypeFromConfig();
        int y = plugin.getConfig().getInt("factions.world.yLevel", 64);

        String want = markerString(desired, y);

        try {
            if (!Files.exists(marker)) {
                plugin.getLogger().info("[WorldManager] Kein Generator-Marker in '" + worldName + "'. Regeneriere.");
                return true;
            }

            String have = Files.readString(marker, StandardCharsets.UTF_8).trim();

            if (!have.equals(want)) {
                plugin.getLogger().info("[WorldManager] Generator-Marker weicht ab. have=" + have + ", want=" + want);
                return true;
            }
        } catch (IOException e) {
            plugin.getLogger().warning("[WorldManager] Konnte Marker nicht lesen. Regeneriere sicherheitshalber: " + e.getMessage());
            return true;
        }

        return false;
    }

    private void writeGeneratorMarker(String worldName, GeneratorType type, int y) {
        Path folder = worldRoot.resolve(worldName);
        Path marker = folder.resolve(GEN_MARKER_FILE);

        try {
            Files.writeString(
                    marker,
                    markerString(type, y),
                    StandardCharsets.UTF_8,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.TRUNCATE_EXISTING
            );
        } catch (IOException e) {
            plugin.getLogger().warning("Konnte Generator-Marker nicht schreiben: " + e.getMessage());
        }
    }

    private String markerString(GeneratorType type, int y) {
        return "generator=" + type.name() + ";y=" + y;
    }

    private String fmtLoc(int x, int y, int z) {
        return x + "," + y + "," + z;
    }

    private long fileTime(Path path) {
        try {
            return Files.getLastModifiedTime(path).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private boolean deleteRecursive(Path root) throws IOException {
        if (!Files.exists(root)) {
            return true;
        }

        Files.walkFileTree(root, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Files.deleteIfExists(file);
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Path dir, IOException exc) throws IOException {
                Files.deleteIfExists(dir);
                return FileVisitResult.CONTINUE;
            }
        });

        return !Files.exists(root);
    }

    private void zipFolder(Path srcDir, Path zipFile) throws IOException {
        try (ZipOutputStream zos = new ZipOutputStream(Files.newOutputStream(zipFile))) {
            Files.walkFileTree(srcDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                    Path rel = srcDir.relativize(file);
                    ZipEntry entry = new ZipEntry(rel.toString().replace('\\', '/'));

                    zos.putNextEntry(entry);
                    Files.copy(file, zos);
                    zos.closeEntry();

                    return FileVisitResult.CONTINUE;
                }
            });
        }
    }

    private void unzipTo(Path zipFile, Path targetDir) throws IOException {
        Files.createDirectories(targetDir);

        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zipFile))) {
            ZipEntry entry;

            while ((entry = zis.getNextEntry()) != null) {
                Path out = targetDir.resolve(entry.getName()).normalize();

                if (!out.startsWith(targetDir)) {
                    throw new IOException("Zip traversal attempt: " + entry.getName());
                }

                if (entry.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());

                    try (OutputStream os = Files.newOutputStream(
                            out,
                            StandardOpenOption.CREATE,
                            StandardOpenOption.TRUNCATE_EXISTING
                    )) {
                        byte[] buffer = new byte[8192];
                        int read;

                        while ((read = zis.read(buffer)) > 0) {
                            os.write(buffer, 0, read);
                        }
                    }
                }

                zis.closeEntry();
            }
        }
    }

    private <T> void trySetRule(World world, GameRule<T> rule, T value) {
        try {
            world.setGameRule(rule, value);
        } catch (Throwable ignored) {}
    }

    private static final class SimpleVoidGenerator extends ChunkGenerator {
        @Override
        public ChunkData generateChunkData(World world, Random random, int cx, int cz, BiomeGrid biomes) {
            for (int x = 0; x < 16; x++) {
                for (int z = 0; z < 16; z++) {
                    biomes.setBiome(x, z, Biome.PLAINS);
                }
            }

            return createChunkData(world);
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
