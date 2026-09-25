package com.vetroforge.services;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.services.buildbot.FrostalCoreGenerator;
import com.vetroforge.services.buildbot.VetroMapGenerator;
import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.extent.clipboard.Clipboard;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormat;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardFormats;
import com.sk89q.worldedit.extent.clipboard.io.ClipboardReader;
import com.sk89q.worldedit.math.BlockVector3;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.data.BlockData;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.FileInputStream;
import java.io.FileWriter;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.zip.GZIPInputStream;
import java.util.zip.GZIPOutputStream;

public final class BuildBotService implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final Map<UUID, Preview> previews = new HashMap<>();
    private final Map<UUID, Job> jobs = new HashMap<>();
    private final Map<UUID, UndoRecord> undo = new HashMap<>();
    private final Set<UUID> undoing = new java.util.HashSet<>();
    private final Map<UUID, Location> pos1 = new HashMap<>();
    private final Map<UUID, Location> pos2 = new HashMap<>();
    private final File templatesDir;
    private final File mapStateFile;
    private final File mapFootprintsDir;
    private final File mapModulesDir;
    private final Map<String, ModuleGeometry> mapGeometry;

    public BuildBotService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.templatesDir = new File(plugin.getDataFolder(), "buildbot/templates");
        this.templatesDir.mkdirs();
        this.mapStateFile = new File(plugin.getDataFolder(), "buildbot/map-state.yml");
        this.mapFootprintsDir = new File(plugin.getDataFolder(), "buildbot/map-footprints");
        this.mapModulesDir = new File(plugin.getDataFolder(), "buildbot/map-modules");
        File parent = mapStateFile.getParentFile();
        if (parent != null) parent.mkdirs();
        this.mapFootprintsDir.mkdirs();
        this.mapModulesDir.mkdirs();
        this.mapGeometry = loadMapGeometry();
    }

    public synchronized Map<String, Object> mapOverview() {
        YamlConfiguration state = YamlConfiguration.loadConfiguration(mapStateFile);
        boolean hasAnchor = state.contains("anchor.x") && state.contains("anchor.y") && state.contains("anchor.z");
        int anchorX = state.getInt("anchor.x", 0);
        int anchorY = state.getInt("anchor.y", 0);
        int anchorZ = state.getInt("anchor.z", 0);
        int rotation = Math.floorMod(state.getInt("anchor.rotation", 0), 4);

        List<Map<String, Object>> modules = new ArrayList<>();
        OverviewBounds mapBounds = null;
        long totalBlocks = 0L;
        int builtModules = 0;
        for (String module : VetroMapGenerator.moduleIds()) {
            ModuleGeometry geometry = mapGeometry.get(module);
            if (geometry == null) continue;
            OverviewBounds bounds = overviewBounds(geometry.bounds, hasAnchor, anchorX, anchorY, anchorZ, rotation);
            mapBounds = mapBounds == null ? bounds : mapBounds.include(bounds);
            boolean built = state.contains("built." + module);
            if (built) builtModules++;
            totalBlocks += geometry.blockCount;

            Map<String, Object> row = new java.util.LinkedHashMap<>();
            row.put("id", module);
            row.put("label", moduleLabel(module));
            row.put("color", moduleColor(module));
            row.put("built", built);
            row.put("builtAt", built ? state.getLong("built." + module) : null);
            row.put("source", geometry.source);
            row.put("blockCount", geometry.blockCount);
            row.put("bounds", bounds.asMap());
            modules.add(row);
        }

        Map<String, Object> root = new java.util.LinkedHashMap<>();
        root.put("ok", true);
        root.put("profile", state.getString("profile", VetroMapGenerator.PROFILE_ID));
        root.put("configured", hasAnchor);
        root.put("coordinateSpace", hasAnchor ? "world" : "local");
        root.put("anchor", hasAnchor ? Map.of(
                "world", state.getString("anchor.world", "world"),
                "x", anchorX,
                "y", anchorY,
                "z", anchorZ,
                "rotation", rotation * 90
        ) : null);
        root.put("bounds", mapBounds == null ? Map.of("minX", -128, "maxX", 128, "minZ", -64, "maxZ", 384)
                : mapBounds.padded(16).asMap());
        root.put("modules", modules);
        root.put("builtModules", builtModules);
        root.put("totalModules", modules.size());
        root.put("totalBlocks", totalBlocks);
        root.put("crystal", crystalOverview(state, hasAnchor, anchorX, anchorY, anchorZ, rotation));
        return root;
    }

    private Map<String, ModuleGeometry> loadMapGeometry() {
        Map<String, ModuleGeometry> result = new java.util.LinkedHashMap<>();
        for (String module : VetroMapGenerator.moduleIds()) {
            File schematic = mapModuleSchematic(module);
            if (schematic != null) {
                List<Piece> pieces = loadSchematic(schematic, true);
                if (pieces.isEmpty()) continue;
                VetroMapGenerator.ModuleOffset offset = VetroMapGenerator.moduleOffset(module);
                Bounds local = bounds(pieces);
                Bounds shifted = new Bounds(
                        local.minX + offset.x(), local.minY + offset.y(), local.minZ + offset.z(),
                        local.maxX + offset.x(), local.maxY + offset.y(), local.maxZ + offset.z()
                );
                long blockCount = pieces.stream().filter(piece -> !piece.blockData.getMaterial().isAir()).count();
                result.put(module, new ModuleGeometry(shifted, blockCount, "schematic"));
                continue;
            }

            List<VetroMapGenerator.MapBlock> blocks = VetroMapGenerator.generate(module);
            if (blocks.isEmpty()) continue;
            int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
            int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
            long blockCount = 0L;
            for (VetroMapGenerator.MapBlock block : blocks) {
                minX = Math.min(minX, block.x()); minY = Math.min(minY, block.y()); minZ = Math.min(minZ, block.z());
                maxX = Math.max(maxX, block.x()); maxY = Math.max(maxY, block.y()); maxZ = Math.max(maxZ, block.z());
                if (!block.material().isAir()) blockCount++;
            }
            result.put(module, new ModuleGeometry(new Bounds(minX, minY, minZ, maxX, maxY, maxZ),
                    blockCount, "generated"));
        }
        return Map.copyOf(result);
    }

    private OverviewBounds overviewBounds(Bounds bounds, boolean hasAnchor, int anchorX, int anchorY, int anchorZ, int rotation) {
        if (!hasAnchor) {
            return new OverviewBounds(bounds.minX, bounds.minY, bounds.minZ, bounds.maxX, bounds.maxY, bounds.maxZ);
        }
        int[][] corners = {
                rotateOffset(bounds.minX, bounds.minZ, rotation),
                rotateOffset(bounds.minX, bounds.maxZ, rotation),
                rotateOffset(bounds.maxX, bounds.minZ, rotation),
                rotateOffset(bounds.maxX, bounds.maxZ, rotation)
        };
        int minX = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (int[] corner : corners) {
            minX = Math.min(minX, anchorX + corner[0]);
            maxX = Math.max(maxX, anchorX + corner[0]);
            minZ = Math.min(minZ, anchorZ + corner[1]);
            maxZ = Math.max(maxZ, anchorZ + corner[1]);
        }
        return new OverviewBounds(minX, anchorY + bounds.minY, minZ, maxX, anchorY + bounds.maxY, maxZ);
    }

    private Map<String, Object> crystalOverview(YamlConfiguration state, boolean hasAnchor,
                                                 int anchorX, int anchorY, int anchorZ, int rotation) {
        int[] offset = rotateOffset(VetroMapGenerator.FROSTAL_ENTRANCE_X,
                VetroMapGenerator.FROSTAL_ENTRANCE_Z + 50, rotation);
        Map<String, Object> crystal = new java.util.LinkedHashMap<>();
        crystal.put("state", state.getString("crystal-state", "awakened"));
        crystal.put("updatedAt", state.contains("crystal-state-updated")
                ? state.getLong("crystal-state-updated") : null);
        crystal.put("x", (hasAnchor ? anchorX : 0) + offset[0]);
        crystal.put("y", (hasAnchor ? anchorY : 0) + 3);
        crystal.put("z", (hasAnchor ? anchorZ : 0) + offset[1]);
        return crystal;
    }

    private String moduleLabel(String module) {
        return switch (module) {
            case "spawn" -> "Spawn";
            case "warzone" -> "Warzone";
            case "corruption" -> "Verderbnis";
            case "frost" -> "Frostland";
            case "frostal_core" -> "Frostal Ice";
            default -> module;
        };
    }

    private String moduleColor(String module) {
        return switch (module) {
            case "spawn" -> "#f3a65a";
            case "warzone" -> "#ffd166";
            case "corruption" -> "#ff5d67";
            case "frost" -> "#54baff";
            case "frostal_core" -> "#a777ff";
            default -> "#dcecff";
        };
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§cNur ingame.");
            return true;
        }
        if (command.getName().equalsIgnoreCase("mapsetup")) {
            return onMapCommand(player, label, args);
        }
        if (!player.hasPermission("vetroforge.buildbot") && !player.hasPermission("vetroforge.admin")) {
            player.sendMessage("§cKeine Rechte.");
            return true;
        }
        if (args.length == 0) {
            help(player, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "preview" -> {
                String template = args.length >= 2 ? args[1] : "chest_platform";
                preview(player, template);
            }
            case "schem", "schematic" -> {
                String schematic = args.length >= 2 ? args[1] : "";
                previewSchematic(player, schematic);
            }
            case "pos1" -> {
                pos1.put(player.getUniqueId(), player.getLocation().getBlock().getLocation());
                player.sendMessage("§6BuildBot §8» §aPos1 gesetzt.");
            }
            case "pos2" -> {
                pos2.put(player.getUniqueId(), player.getLocation().getBlock().getLocation());
                player.sendMessage("§6BuildBot §8» §aPos2 gesetzt.");
            }
            case "save" -> {
                String name = args.length >= 2 ? args[1] : "template_" + System.currentTimeMillis();
                saveTemplate(player, name);
            }
            case "confirm", "build" -> confirm(player);
            case "stop", "cancel" -> stop(player, true);
            case "undo" -> undo(player);
            case "speed" -> speed(player, args);
            case "templates", "list" -> templates(player);
            case "repair" -> repair(player, args);
            default -> help(player, label);
        }
        return true;
    }

    private boolean onMapCommand(Player player, String label, String[] args) {
        if (!player.hasPermission("vetroforge.buildbot") && !player.hasPermission("vetroforge.admin")) {
            player.sendMessage("§cKeine Rechte.");
            return true;
        }
        if (args.length == 0) {
            mapHelp(player, label);
            return true;
        }
        String sub = args[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "preview" -> previewMap(player, args.length >= 2 ? args[1] : "all");
            case "state" -> previewCrystalState(player, args.length >= 2 ? args[1] : "");
            case "confirm", "build" -> confirm(player);
            case "stop", "cancel" -> stop(player, true);
            case "undo" -> undo(player);
            case "anchor" -> setMapAnchor(player);
            case "status" -> mapStatus(player);
            case "modules", "list" -> mapModules(player);
            default -> mapHelp(player, label);
        }
        return true;
    }

    private void preview(Player player, String templateId) {
        if (!allowedArea(player)) {
            player.sendMessage("§cBuildBot darf nur in Spawn/Warzone bauen.");
            return;
        }
        String normalized = templateId.toLowerCase(Locale.ROOT);
        List<Piece> pieces = template(normalized);
        if (pieces.isEmpty()) {
            player.sendMessage("§cUnbekanntes Template. Nutze /buildbot templates");
            return;
        }
        Location origin = player.getLocation().getBlock().getLocation();
        int rotation = 0;
        if (normalized.equals("frostal_core") || normalized.equals("frostal")) {
            rotation = rotationQuarter(player.getLocation().getYaw());
            pieces = rotate(pieces, rotation);
        }
        int maxBlocks = Math.max(1, plugin.getConfig().getInt("buildbot.generated.maxBlocks", 120000));
        if (pieces.size() > maxBlocks) {
            player.sendMessage("§6BuildBot §8» §cTemplate zu groß: §f" + pieces.size()
                    + " §7von erlaubten §f" + maxBlocks + "§7 Blöcken.");
            return;
        }
        int outside = countOutside(player, origin, pieces);
        if (outside > 0) {
            player.sendMessage("§6BuildBot §8» §cDer Bau würde die freigegebene Spawn-/Warzone-Region verlassen.");
            player.sendMessage("§8» §7Außerhalb: §f" + outside + " Blöcke§7. Stelle dich weiter in die Eisseite.");
            return;
        }
        int speed = normalized.equals("frostal_core") || normalized.equals("frostal") ? 40 : 8;
        previews.put(player.getUniqueId(), new Preview(normalized, origin, pieces, speed, rotation, false));
        showPreview(player);
        Bounds bounds = bounds(pieces);
        player.sendMessage("§6BuildBot §8» §fPreview §b" + normalized + " §7bereit.");
        player.sendMessage("§8» §7Größe: §f" + bounds.width() + "×" + bounds.height() + "×" + bounds.depth()
                + " §8| §7Blöcke: §f" + pieces.size());
        player.sendMessage("§8» §a/buildbot confirm §7baut automatisch §8| §e/buildbot cancel §7bricht ab");
    }

    private void previewMap(Player player, String requestedModule) {
        String selection = requestedModule.toLowerCase(Locale.ROOT).replace('-', '_');
        if (!selection.equals("all") && !selection.equals(VetroMapGenerator.PROFILE_ID)
                && !VetroMapGenerator.moduleIds().contains(selection)) {
            player.sendMessage("§cUnbekanntes Kartenmodul. Nutze /mapsetup modules");
            return;
        }
        List<Piece> pieces = mapPieces(selection);
        if (pieces.isEmpty()) {
            player.sendMessage("§cDas Kartenmodul ist leer.");
            return;
        }

        MapAnchor anchor = loadMapAnchor(player);
        pieces = rotate(pieces, anchor.rotation);
        int maxBlocks = Math.max(1, plugin.getConfig().getInt("buildbot.map.maxBlocks", 500000));
        if (pieces.size() > maxBlocks) {
            player.sendMessage("§6MapSetup §8» §cZu groß: §f" + pieces.size()
                    + " §7von erlaubten §f" + maxBlocks + "§7 Blöcken.");
            return;
        }
        int outsideHeight = countOutsideWorld(anchor.location, pieces);
        if (outsideHeight > 0) {
            player.sendMessage("§6MapSetup §8» §c" + outsideHeight
                    + " Blöcke würden außerhalb der Welthöhe liegen. Setze den Anker auf eine passende Höhe.");
            return;
        }

        int speed = Math.max(40, plugin.getConfig().getInt("buildbot.map.blocksPerTick", 600));
        String templateId = "map:" + (selection.equals(VetroMapGenerator.PROFILE_ID) ? "all" : selection);
        previews.put(player.getUniqueId(), new Preview(templateId, anchor.location, pieces, speed, anchor.rotation, true));
        showPreview(player);
        Bounds bounds = bounds(pieces);
        player.sendMessage("§6MapSetup §8» §b" + selection + " §7am Master-Anker vorbereitet.");
        player.sendMessage("§8» §7Größe: §f" + bounds.width() + "×" + bounds.height() + "×" + bounds.depth()
                + " §8| §7Blöcke: §f" + pieces.size());
        player.sendMessage("§8» §a/mapsetup confirm §7baut §8| §e/mapsetup cancel §7bricht ab");
    }

    private void previewCrystalState(Player player, String requestedState) {
        String state = requestedState.toLowerCase(Locale.ROOT).replace('-', '_');
        if (!VetroMapGenerator.crystalStates().contains(state)) {
            player.sendMessage("§cNutze: /mapsetup state <dormant|awakened|broken>");
            return;
        }
        MapAnchor anchor = loadStoredMapAnchor();
        if (anchor == null) {
            player.sendMessage("§cNoch kein Kartenanker gespeichert. Nutze zuerst /mapsetup anchor.");
            return;
        }
        List<Piece> pieces = VetroMapGenerator.crystalState(state).stream()
                .map(block -> piece(block.x(), block.y(), block.z(), block.material()))
                .toList();
        pieces = rotate(pieces, anchor.rotation);
        previews.put(player.getUniqueId(), new Preview("mapstate:" + state, anchor.location, pieces,
                Math.max(40, plugin.getConfig().getInt("buildbot.map.blocksPerTick", 600)), anchor.rotation, true));
        showPreview(player);
        player.sendMessage("§6MapSetup §8» §7Kristallzustand §b" + state + " §7vorbereitet.");
        player.sendMessage("§8» §a/mapsetup confirm §7wendet ihn an §8| §e/mapsetup cancel §7bricht ab");
    }

    private void previewSchematic(Player player, String schematicName) {
        if (schematicName == null || schematicName.isBlank()) {
            player.sendMessage("§cNutze: /buildbot schem <datei.schem>");
            return;
        }
        if (!allowedArea(player)) {
            player.sendMessage("§cBuildBot darf nur in Spawn/Warzone bauen.");
            return;
        }
        File file = schematicFile(schematicName);
        if (file == null || !file.isFile()) {
            player.sendMessage("§cSchematic nicht gefunden in plugins/VetroForge/buildbot/schematics oder buildbot/templates.");
            return;
        }
        List<Piece> pieces = loadSchematic(file, false);
        if (pieces.isEmpty()) {
            player.sendMessage("§cSchematic konnte nicht gelesen werden oder ist leer.");
            return;
        }
        Location origin = player.getLocation().getBlock().getLocation();
        previews.put(player.getUniqueId(), new Preview(file.getName(), origin, pieces, 6, 0, false));
        showPreview(player);
        player.sendMessage("§6BuildBot §8» §fSchematic-Preview §e" + file.getName()
                + " §7Blöcke: §f" + pieces.size() + " §8| §a/buildbot confirm");
    }

    private void showPreview(Player player) {
        Preview preview = previews.get(player.getUniqueId());
        if (preview == null) return;
        int maxParticles = Math.max(100, plugin.getConfig().getInt("buildbot.preview.maxParticles", 1400));
        int stride = Math.max(1, (int) Math.ceil(preview.pieces.size() / (double) maxParticles));
        for (int index = 0; index < preview.pieces.size(); index += stride) {
            Piece piece = preview.pieces.get(index);
            Location loc = preview.origin.clone().add(piece.dx, piece.dy, piece.dz).add(0.5, 0.5, 0.5);
            player.spawnParticle(Particle.DUST, loc, 2, 0.08, 0.08, 0.08, 0.0,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 190, 40), 1.0F));
        }
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 0.6F, 1.4F);
    }

    private void confirm(Player player) {
        Preview preview = previews.remove(player.getUniqueId());
        if (preview == null) {
            player.sendMessage("§cKeine Preview offen. Nutze /buildbot preview <template>");
            return;
        }
        if (undoing.contains(player.getUniqueId())) {
            player.sendMessage("§cWarte, bis das laufende Undo abgeschlossen ist.");
            previews.put(player.getUniqueId(), preview);
            return;
        }
        stop(player, false);
        Queue<Piece> queue = new ArrayDeque<>(preview.pieces.stream()
                .sorted(Comparator.comparingInt((Piece p) -> p.dy).thenComparingInt(p -> Math.abs(p.dx) + Math.abs(p.dz)))
                .toList());
        List<Change> changes = new ArrayList<>();
        ArmorStand worker = spawnWorker(player, preview.origin, preview.template);
        Job job = new Job(preview.template, preview.origin, queue, changes, preview.blocksPerTick,
                preview.rotation, preview.bypassRegion, null, worker);
        BukkitTask task = Bukkit.getScheduler().runTaskTimer(plugin, () -> tick(player.getUniqueId()), 1L, 1L);
        job.task = task;
        jobs.put(player.getUniqueId(), job);
        player.sendMessage("§6BuildBot §8» §aBaut §f" + preview.template + " §7Block für Block.");
    }

    private void tick(UUID owner) {
        Job job = jobs.get(owner);
        Player player = Bukkit.getPlayer(owner);
        if (job == null) return;
        if (player == null) {
            jobs.remove(owner);
            if (job.task != null) job.task.cancel();
            if (!job.changes.isEmpty()) {
                undo.put(owner, new UndoRecord(job.template, job.changes, false));
            }
            removeWorker(job);
            return;
        }
        if (currentTps() < 18.0D) {
            if (System.currentTimeMillis() % 2000L < 50L) player.sendMessage("§6BuildBot §8» §ePausiert wegen TPS.");
            return;
        }
        int placed = 0;
        while (placed < job.blocksPerTick && !job.queue.isEmpty()) {
            Piece piece = job.queue.poll();
            Location loc = job.origin.clone().add(piece.dx, piece.dy, piece.dz);
            if (!job.bypassRegion && !allowedLocation(player, loc)) continue;
            BlockData old = loc.getBlock().getBlockData().clone();
            job.changes.add(new Change(loc, old));
            loc.getBlock().setBlockData(piece.blockData, false);
            moveWorker(job, loc);
            if ((job.changes.size() & 7) == 0) {
                loc.getWorld().spawnParticle(Particle.CRIT, loc.clone().add(0.5, 0.7, 0.5), 2,
                        0.15, 0.15, 0.15, 0.01);
            }
            placed++;
        }
        if (job.queue.isEmpty()) {
            stop(player, false);
            removeWorker(job);
            player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.7F, 1.2F);
            player.sendMessage("§6BuildBot §8» §aFertig. §7Undo: §e/buildbot undo");
            if (job.template.equals("frostal_core") || job.template.equals("frostal")) {
                configureFrostalLandmark(job.origin, job.rotation);
                undo.put(owner, new UndoRecord(job.template, job.changes, true));
                player.sendMessage("§8» §bFrostal-Kern §7ist als Boss-Hotspot eingerichtet.");
            } else if (job.template.startsWith("map:")) {
                completeMapBuild(job);
                undo.put(owner, new UndoRecord(job.template, job.changes, true));
                player.sendMessage("§8» §bKartenstatus §7und Modulpositionen wurden gespeichert.");
            } else if (job.template.startsWith("mapstate:")) {
                completeCrystalState(job.template.substring("mapstate:".length()));
                undo.put(owner, new UndoRecord(job.template, job.changes, true));
                player.sendMessage("§8» §bSeason-Zustand §7wurde gespeichert.");
            } else {
                undo.put(owner, new UndoRecord(job.template, job.changes, false));
            }
        }
    }

    private void stop(Player player, boolean message) {
        Job job = jobs.remove(player.getUniqueId());
        if (job != null && job.task != null) {
            try { job.task.cancel(); } catch (Throwable ignored) {}
            removeWorker(job);
            if (!job.changes.isEmpty()) {
                undo.put(player.getUniqueId(), new UndoRecord(job.template, job.changes, false));
            }
            if (message) player.sendMessage("§6BuildBot §8» §cJob gestoppt.");
        }
    }

    private void undo(Player player) {
        UndoRecord record = undo.remove(player.getUniqueId());
        if (record == null || record.changes.isEmpty()) {
            player.sendMessage("§cKein Undo vorhanden.");
            return;
        }
        List<Change> changes = record.changes;
        UUID owner = player.getUniqueId();
        if (!undoing.add(owner)) {
            player.sendMessage("§cEin Undo läuft bereits.");
            return;
        }
        java.util.concurrent.atomic.AtomicInteger index = new java.util.concurrent.atomic.AtomicInteger(changes.size() - 1);
        int perTick = Math.max(50, plugin.getConfig().getInt("buildbot.undo.blocksPerTick", 600));
        final BukkitTask[] task = new BukkitTask[1];
        task[0] = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            int restored = 0;
            while (restored++ < perTick && index.get() >= 0) {
                Change change = changes.get(index.getAndDecrement());
                change.location.getBlock().setBlockData(change.oldData, false);
            }
            if (index.get() < 0) {
                task[0].cancel();
                undoing.remove(owner);
                if (record.configured && (record.template.equals("frostal_core") || record.template.equals("frostal"))) {
                    clearFrostalLandmarkConfig();
                } else if (record.configured && record.template.startsWith("map:")) {
                    clearMapBuildState(record.template.substring("map:".length()));
                } else if (record.configured && record.template.startsWith("mapstate:")) {
                    clearCrystalState();
                }
                if (player.isOnline()) player.sendMessage("§6BuildBot §8» §aLetzten Build vollständig zurückgesetzt.");
            }
        }, 1L, 1L);
        player.sendMessage("§6BuildBot §8» §eUndo gestartet: §f" + changes.size() + " §7Blöcke.");
    }

    private void speed(Player player, String[] args) {
        Preview preview = previews.get(player.getUniqueId());
        if (preview == null) {
            player.sendMessage("§cErst /buildbot preview <template> nutzen.");
            return;
        }
        int speed = 8;
        if (args.length >= 2) {
            try { speed = Integer.parseInt(args[1]); } catch (Throwable ignored) {}
        }
        preview.blocksPerTick = Math.max(1, Math.min(80, speed));
        player.sendMessage("§6BuildBot §8» §fSpeed §a" + preview.blocksPerTick + " §7Blöcke/Tick");
    }

    private void repair(Player player, String[] args) {
        String template = args.length >= 2 && args[1].equalsIgnoreCase("warzone") ? "chest_platform" : "chest_platform";
        preview(player, template);
        player.sendMessage("§6BuildBot §8» §7Repair nutzt aktuell das sichere Chest-Platform-Template.");
    }

    private void templates(Player player) {
        List<String> custom = customTemplates();
        player.sendMessage("§6BuildBot Templates §8» §bfrostal_core§7, §fchest_platform§7, §fcrate_podium§7, §fevent_holo_stand");
        if (!custom.isEmpty()) player.sendMessage("§7Custom §8» §f" + String.join("§7, §f", custom));
    }

    private void help(Player player, String label) {
        player.sendMessage("§8§m------------------------------");
        player.sendMessage("§6BuildBot §8» §fCommands");
        player.sendMessage("§e/" + label + " preview <template>");
        player.sendMessage("§e/" + label + " schem <datei.schem>");
        player.sendMessage("§e/" + label + " pos1 §8| §e/" + label + " pos2 §8| §e/" + label + " save <name>");
        player.sendMessage("§e/" + label + " speed <1-40>");
        player.sendMessage("§e/" + label + " confirm");
        player.sendMessage("§e/" + label + " stop");
        player.sendMessage("§e/" + label + " undo");
        player.sendMessage("§e/" + label + " templates");
        player.sendMessage("§8§m------------------------------");
    }

    private void mapHelp(Player player, String label) {
        player.sendMessage("§8§m------------------------------");
        player.sendMessage("§bMapSetup §8» §fVetroForge Masterkarte");
        player.sendMessage("§e/" + label + " anchor §8- §7Master-Anker setzen");
        player.sendMessage("§e/" + label + " preview <all|modul>");
        player.sendMessage("§e/" + label + " confirm §8| §e/" + label + " cancel §8| §e/" + label + " undo");
        player.sendMessage("§e/" + label + " state <dormant|awakened|broken>");
        player.sendMessage("§e/" + label + " status §8| §e/" + label + " modules");
        player.sendMessage("§8§m------------------------------");
    }

    private void mapModules(Player player) {
        List<String> modules = new ArrayList<>();
        for (String module : VetroMapGenerator.moduleIds()) {
            modules.add((mapModuleSchematic(module) == null ? "§7" : "§a") + module);
        }
        player.sendMessage("§bMapSetup Module §8» §f" + String.join("§8, ", modules));
        player.sendMessage("§8» §aGrün §7= Schematic-Override, §7Grau = eingebauter Generator");
        player.sendMessage("§7Komplettbau §8» §f/mapsetup preview all");
        player.sendMessage("§7Kristall §8» §fdormant§7, §fawakened§7, §fbroken");
    }

    private List<Piece> mapPieces(String selection) {
        List<String> modules = selection.equals("all") || selection.equals(VetroMapGenerator.PROFILE_ID)
                ? VetroMapGenerator.moduleIds()
                : List.of(selection);
        Map<MapPosition, BlockData> merged = new java.util.LinkedHashMap<>();

        // Clear only blocks owned by the previously built module version.
        for (String module : modules) {
            for (MapPosition position : readMapFootprint(module)) {
                merged.put(position, Bukkit.createBlockData(Material.AIR));
            }
        }
        // Current module data wins over the cleanup pass and over earlier modules in build order.
        for (String module : modules) {
            for (Piece piece : resolvedMapModule(module)) {
                merged.put(new MapPosition(piece.dx, piece.dy, piece.dz), piece.blockData);
            }
        }

        List<Piece> pieces = new ArrayList<>(merged.size());
        merged.forEach((position, data) -> pieces.add(new Piece(position.x, position.y, position.z, data)));
        return List.copyOf(pieces);
    }

    private List<MapPosition> readMapFootprint(String module) {
        File file = footprintFile(module);
        if (!file.isFile()) return List.of();
        try (DataInputStream input = new DataInputStream(new BufferedInputStream(
                new GZIPInputStream(new FileInputStream(file))))) {
            String magic = input.readUTF();
            int version = input.readInt();
            int size = input.readInt();
            if (!magic.equals("VFMAP") || version != 1 || size < 0 || size > 2_000_000) return List.of();
            List<MapPosition> positions = new ArrayList<>(size);
            for (int index = 0; index < size; index++) {
                positions.add(new MapPosition(input.readInt(), input.readInt(), input.readInt()));
            }
            return positions;
        } catch (Exception exception) {
            plugin.getLogger().warning("Map-Footprint für " + module + " konnte nicht gelesen werden: "
                    + exception.getMessage());
            return List.of();
        }
    }

    private void backupAndWriteMapFootprint(String module) {
        File file = footprintFile(module);
        File previous = previousFootprintFile(module);
        try {
            if (file.isFile()) Files.copy(file.toPath(), previous.toPath(), StandardCopyOption.REPLACE_EXISTING);
            List<Piece> blocks = resolvedMapModule(module).stream()
                    .filter(piece -> !piece.blockData.getMaterial().isAir())
                    .toList();
            try (DataOutputStream output = new DataOutputStream(new BufferedOutputStream(
                    new GZIPOutputStream(Files.newOutputStream(file.toPath()))))) {
                output.writeUTF("VFMAP");
                output.writeInt(1);
                output.writeInt(blocks.size());
                for (Piece block : blocks) {
                    output.writeInt(block.dx);
                    output.writeInt(block.dy);
                    output.writeInt(block.dz);
                }
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Map-Footprint für " + module + " konnte nicht gespeichert werden: "
                    + exception.getMessage());
        }
    }

    private void restoreMapFootprint(String module) {
        File file = footprintFile(module);
        File previous = previousFootprintFile(module);
        try {
            if (previous.isFile()) {
                Files.move(previous.toPath(), file.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } else {
                Files.deleteIfExists(file.toPath());
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("Map-Footprint für " + module + " konnte nicht zurückgesetzt werden: "
                    + exception.getMessage());
        }
    }

    private File footprintFile(String module) {
        return new File(mapFootprintsDir, module.replaceAll("[^a-z0-9_-]", "_") + ".vfmap.gz");
    }

    private File previousFootprintFile(String module) {
        return new File(mapFootprintsDir, module.replaceAll("[^a-z0-9_-]", "_") + ".previous.vfmap.gz");
    }

    private List<Piece> resolvedMapModule(String module) {
        File schematic = mapModuleSchematic(module);
        if (schematic != null) {
            VetroMapGenerator.ModuleOffset offset = VetroMapGenerator.moduleOffset(module);
            return loadSchematic(schematic, true).stream()
                    .map(piece -> new Piece(piece.dx + offset.x(), piece.dy + offset.y(), piece.dz + offset.z(), piece.blockData))
                    .toList();
        }
        return VetroMapGenerator.generate(module).stream()
                .map(block -> piece(block.x(), block.y(), block.z(), block.material()))
                .toList();
    }

    private File mapModuleSchematic(String module) {
        String safe = module.replaceAll("[^a-z0-9_-]", "_");
        for (String extension : List.of(".schem", ".schematic")) {
            File file = new File(mapModulesDir, safe + extension);
            if (file.isFile()) return file;
        }
        return null;
    }

    private void setMapAnchor(Player player) {
        YamlConfiguration existing = YamlConfiguration.loadConfiguration(mapStateFile);
        for (String module : VetroMapGenerator.moduleIds()) {
            if (existing.contains("built." + module)) {
                player.sendMessage("§cDer Master-Anker ist gesperrt, weil bereits Kartenmodule gebaut wurden.");
                player.sendMessage("§7So verhindert MapSetup eine zweite versetzte Kopie der Karte.");
                return;
            }
        }
        Location location = player.getLocation().getBlock().getLocation();
        int rotation = rotationQuarter(player.getLocation().getYaw());
        YamlConfiguration state = existing;
        state.set("profile", VetroMapGenerator.PROFILE_ID);
        state.set("anchor.world", location.getWorld().getName());
        state.set("anchor.x", location.getBlockX());
        state.set("anchor.y", location.getBlockY());
        state.set("anchor.z", location.getBlockZ());
        state.set("anchor.rotation", rotation);
        saveMapState(state);
        player.sendMessage("§6MapSetup §8» §aMaster-Anker gesetzt. §7Ausrichtung: §f" + rotation * 90 + "°");
        player.sendMessage("§8» §7Dieser Punkt ist die südliche Mitte vor dem Spawn-Tor.");
    }

    private void mapStatus(Player player) {
        MapAnchor anchor = loadStoredMapAnchor();
        if (anchor == null) {
            player.sendMessage("§6MapSetup §8» §eKein Master-Anker gespeichert.");
            return;
        }
        YamlConfiguration state = YamlConfiguration.loadConfiguration(mapStateFile);
        player.sendMessage("§6MapSetup §8» §f" + VetroMapGenerator.PROFILE_ID);
        player.sendMessage("§8» §7Anker: §f" + anchor.location.getWorld().getName() + " "
                + anchor.location.getBlockX() + " " + anchor.location.getBlockY() + " " + anchor.location.getBlockZ()
                + " §8| §7Rotation: §f" + anchor.rotation * 90 + "°");
        List<String> built = new ArrayList<>();
        for (String module : VetroMapGenerator.moduleIds()) {
            if (state.contains("built." + module)) built.add(module);
        }
        player.sendMessage("§8» §7Gebaut: §f" + (built.isEmpty() ? "keine Module" : String.join(", ", built)));
        player.sendMessage("§8» §7Kristall: §b" + state.getString("crystal-state", "awakened"));
    }

    private MapAnchor loadMapAnchor(Player fallbackPlayer) {
        MapAnchor stored = loadStoredMapAnchor();
        if (stored != null) return stored;
        return new MapAnchor(fallbackPlayer.getLocation().getBlock().getLocation(),
                rotationQuarter(fallbackPlayer.getLocation().getYaw()));
    }

    private MapAnchor loadStoredMapAnchor() {
        YamlConfiguration state = YamlConfiguration.loadConfiguration(mapStateFile);
        String worldName = state.getString("anchor.world", "");
        World world = Bukkit.getWorld(worldName);
        if (world == null || !state.contains("anchor.x") || !state.contains("anchor.y") || !state.contains("anchor.z")) {
            return null;
        }
        Location location = new Location(world,
                state.getInt("anchor.x"), state.getInt("anchor.y"), state.getInt("anchor.z"));
        return new MapAnchor(location, Math.floorMod(state.getInt("anchor.rotation", 0), 4));
    }

    private void completeMapBuild(Job job) {
        String selection = job.template.substring("map:".length());
        YamlConfiguration state = YamlConfiguration.loadConfiguration(mapStateFile);
        state.set("profile", VetroMapGenerator.PROFILE_ID);
        state.set("anchor.world", job.origin.getWorld().getName());
        state.set("anchor.x", job.origin.getBlockX());
        state.set("anchor.y", job.origin.getBlockY());
        state.set("anchor.z", job.origin.getBlockZ());
        state.set("anchor.rotation", job.rotation);
        long completedAt = System.currentTimeMillis();
        if (selection.equals("all")) {
            for (String module : VetroMapGenerator.moduleIds()) {
                state.set("built." + module, completedAt);
                backupAndWriteMapFootprint(module);
            }
        } else {
            state.set("built." + selection, completedAt);
            backupAndWriteMapFootprint(selection);
        }
        if (!state.contains("crystal-state")) state.set("crystal-state", "awakened");
        saveMapState(state);

        if (selection.equals("all") || selection.equals("frostal_core")) {
            int[] offset = rotateOffset(VetroMapGenerator.FROSTAL_ENTRANCE_X,
                    VetroMapGenerator.FROSTAL_ENTRANCE_Z, job.rotation);
            configureFrostalLandmark(job.origin.clone().add(offset[0], 0, offset[1]), job.rotation);
        }
    }

    private void completeCrystalState(String crystalState) {
        YamlConfiguration state = YamlConfiguration.loadConfiguration(mapStateFile);
        state.set("crystal-state", crystalState);
        state.set("crystal-state-updated", System.currentTimeMillis());
        saveMapState(state);
        plugin.getConfig().set("story.frostal.crystal-state", crystalState);
        plugin.saveConfig();
    }

    private void clearMapBuildState(String selection) {
        YamlConfiguration state = YamlConfiguration.loadConfiguration(mapStateFile);
        if (selection.equals("all")) {
            state.set("built", null);
            for (String module : VetroMapGenerator.moduleIds()) restoreMapFootprint(module);
            clearFrostalLandmarkConfig();
        } else {
            state.set("built." + selection, null);
            restoreMapFootprint(selection);
            if (selection.equals("frostal_core")) clearFrostalLandmarkConfig();
        }
        saveMapState(state);
    }

    private void clearCrystalState() {
        YamlConfiguration state = YamlConfiguration.loadConfiguration(mapStateFile);
        state.set("crystal-state", null);
        state.set("crystal-state-updated", null);
        saveMapState(state);
        plugin.getConfig().set("story.frostal.crystal-state", null);
        plugin.saveConfig();
    }

    private synchronized void saveMapState(YamlConfiguration state) {
        try {
            state.save(mapStateFile);
        } catch (Exception exception) {
            plugin.getLogger().warning("MapSetup-Status konnte nicht gespeichert werden: " + exception.getMessage());
        }
    }

    private int[] rotateOffset(int x, int z, int quarter) {
        return switch (quarter) {
            case 1 -> new int[]{-z, x};
            case 2 -> new int[]{-x, -z};
            case 3 -> new int[]{z, -x};
            default -> new int[]{x, z};
        };
    }

    private int countOutsideWorld(Location origin, List<Piece> pieces) {
        World world = origin.getWorld();
        if (world == null) return pieces.size();
        int outside = 0;
        int minimum = world.getMinHeight();
        int maximum = world.getMaxHeight();
        for (Piece piece : pieces) {
            int y = origin.getBlockY() + piece.dy;
            if (y < minimum || y >= maximum) outside++;
        }
        return outside;
    }

    private boolean allowedArea(Player player) {
        if (player.hasPermission("vetroforge.buildbot.anywhere")) return true;
        return plugin.getWarzoneRegionManager() != null
                && (plugin.getWarzoneRegionManager().isSpawn(player.getLocation())
                || plugin.getWarzoneRegionManager().isWarzone(player.getLocation()));
    }

    private boolean allowedLocation(Player player, Location loc) {
        if (player.hasPermission("vetroforge.buildbot.anywhere")) return true;
        return plugin.getWarzoneRegionManager() != null
                && (plugin.getWarzoneRegionManager().isSpawn(loc) || plugin.getWarzoneRegionManager().isWarzone(loc));
    }

    private double currentTps() {
        try { return Bukkit.getTPS()[0]; } catch (Throwable ignored) { return 20.0D; }
    }

    private List<Piece> template(String id) {
        List<Piece> custom = loadCustomTemplate(id);
        if (!custom.isEmpty()) return custom;
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "frostal", "frostal_core" -> frostalCore();
            case "crate_podium" -> cratePodium();
            case "event_holo_stand", "holo" -> eventHoloStand();
            default -> chestPlatform();
        };
    }

    private List<Piece> frostalCore() {
        return FrostalCoreGenerator.generate().stream()
                .map(block -> piece(block.x(), block.y(), block.z(), block.material()))
                .toList();
    }

    private int rotationQuarter(float yaw) {
        return Math.floorMod(Math.round(yaw / 90.0F), 4);
    }

    private List<Piece> rotate(List<Piece> pieces, int quarter) {
        if (quarter == 0) return pieces;
        List<Piece> rotated = new ArrayList<>(pieces.size());
        for (Piece piece : pieces) {
            int x = piece.dx;
            int z = piece.dz;
            int rx = switch (quarter) {
                case 1 -> -z;
                case 2 -> -x;
                case 3 -> z;
                default -> x;
            };
            int rz = switch (quarter) {
                case 1 -> x;
                case 2 -> -z;
                case 3 -> -x;
                default -> z;
            };
            rotated.add(new Piece(rx, piece.dy, rz, piece.blockData));
        }
        return List.copyOf(rotated);
    }

    private int countOutside(Player player, Location origin, List<Piece> pieces) {
        int outside = 0;
        for (Piece piece : pieces) {
            if (!allowedLocation(player, origin.clone().add(piece.dx, piece.dy, piece.dz))) outside++;
        }
        return outside;
    }

    private Bounds bounds(List<Piece> pieces) {
        int minX = Integer.MAX_VALUE, minY = Integer.MAX_VALUE, minZ = Integer.MAX_VALUE;
        int maxX = Integer.MIN_VALUE, maxY = Integer.MIN_VALUE, maxZ = Integer.MIN_VALUE;
        for (Piece piece : pieces) {
            minX = Math.min(minX, piece.dx);
            minY = Math.min(minY, piece.dy);
            minZ = Math.min(minZ, piece.dz);
            maxX = Math.max(maxX, piece.dx);
            maxY = Math.max(maxY, piece.dy);
            maxZ = Math.max(maxZ, piece.dz);
        }
        return new Bounds(minX, minY, minZ, maxX, maxY, maxZ);
    }

    private void configureFrostalLandmark(Location entrance, int quarter) {
        int dx = switch (quarter) {
            case 1 -> -50;
            case 3 -> 50;
            default -> 0;
        };
        int dz = switch (quarter) {
            case 0 -> 50;
            case 2 -> -50;
            default -> 0;
        };
        Location hotspot = entrance.clone().add(dx + 0.5D, 1.0D, dz + 0.5D);
        int bossDx = switch (quarter) {
            case 1 -> -42;
            case 3 -> 42;
            default -> 0;
        };
        int bossDz = switch (quarter) {
            case 0 -> 42;
            case 2 -> -42;
            default -> 0;
        };
        Location boss = entrance.clone().add(bossDx + 0.5D, 1.0D, bossDz + 0.5D);
        String base = "warzone.mobs.hotspots.frostal_core";
        plugin.getConfig().set(base + ".world", hotspot.getWorld().getName());
        plugin.getConfig().set(base + ".x", hotspot.getX());
        plugin.getConfig().set(base + ".z", hotspot.getZ());
        plugin.getConfig().set(base + ".radius", 34.0D);
        plugin.saveConfig();
        if (plugin.getWarzoneMobService() != null) {
            plugin.getWarzoneMobService().setBossSpawn("frostal", boss);
        }
    }

    private void clearFrostalLandmarkConfig() {
        plugin.getConfig().set("warzone.mobs.hotspots.frostal_core", null);
        plugin.getConfig().set("warzone.boss-spawns.frostal", null);
        plugin.saveConfig();
    }

    private List<Piece> chestPlatform() {
        List<Piece> out = new ArrayList<>();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) out.add(piece(x, 0, z, Material.POLISHED_BLACKSTONE));
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) out.add(piece(x, 1, z, Material.RED_NETHER_BRICKS));
        out.add(piece(0, 2, 0, Material.CHEST));
        return out;
    }

    private List<Piece> cratePodium() {
        List<Piece> out = new ArrayList<>();
        for (int x = -2; x <= 2; x++) for (int z = -2; z <= 2; z++) out.add(piece(x, 0, z, Material.DEEPSLATE_TILES));
        out.add(piece(-1, 1, -1, Material.REDSTONE_LAMP));
        out.add(piece(1, 1, -1, Material.REDSTONE_LAMP));
        out.add(piece(-1, 1, 1, Material.REDSTONE_LAMP));
        out.add(piece(1, 1, 1, Material.REDSTONE_LAMP));
        out.add(piece(0, 1, 0, Material.GILDED_BLACKSTONE));
        return out;
    }

    private List<Piece> eventHoloStand() {
        List<Piece> out = new ArrayList<>();
        for (int x = -1; x <= 1; x++) for (int z = -1; z <= 1; z++) out.add(piece(x, 0, z, Material.BLACKSTONE));
        out.add(piece(0, 1, 0, Material.BEACON));
        out.add(piece(0, 2, 0, Material.RED_STAINED_GLASS));
        return out;
    }

    private Piece piece(int dx, int dy, int dz, Material material) {
        return new Piece(dx, dy, dz, Bukkit.createBlockData(material));
    }

    private void saveTemplate(Player player, String name) {
        Location a = pos1.get(player.getUniqueId());
        Location b = pos2.get(player.getUniqueId());
        if (a == null || b == null || a.getWorld() == null || b.getWorld() == null || !a.getWorld().equals(b.getWorld())) {
            player.sendMessage("§cSetze zuerst /buildbot pos1 und /buildbot pos2 in derselben Welt.");
            return;
        }
        String safeName = name.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        Location origin = player.getLocation().getBlock().getLocation();
        int minX = Math.min(a.getBlockX(), b.getBlockX()), maxX = Math.max(a.getBlockX(), b.getBlockX());
        int minY = Math.min(a.getBlockY(), b.getBlockY()), maxY = Math.max(a.getBlockY(), b.getBlockY());
        int minZ = Math.min(a.getBlockZ(), b.getBlockZ()), maxZ = Math.max(a.getBlockZ(), b.getBlockZ());
        int maxBlocks = Math.max(1, plugin.getConfig().getInt("buildbot.save.maxBlocks", 5000));
        int count = 0;
        File file = new File(templatesDir, safeName + ".vftpl");
        try (FileWriter writer = new FileWriter(file)) {
            writer.write("# VetroForge BuildBot Template\n");
            for (int x = minX; x <= maxX; x++) {
                for (int y = minY; y <= maxY; y++) {
                    for (int z = minZ; z <= maxZ; z++) {
                        Location loc = new Location(a.getWorld(), x, y, z);
                        if (loc.getBlock().getType().isAir()) continue;
                        writer.write((x - origin.getBlockX()) + ";" + (y - origin.getBlockY()) + ";" + (z - origin.getBlockZ())
                                + ";" + loc.getBlock().getBlockData().getAsString() + "\n");
                        if (++count >= maxBlocks) break;
                    }
                    if (count >= maxBlocks) break;
                }
                if (count >= maxBlocks) break;
            }
            player.sendMessage("§6BuildBot §8» §aTemplate gespeichert: §f" + safeName + " §7Blöcke: §f" + count);
        } catch (Throwable t) {
            player.sendMessage("§cTemplate konnte nicht gespeichert werden: " + t.getMessage());
        }
    }

    private List<Piece> loadCustomTemplate(String id) {
        File file = new File(templatesDir, id.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_") + ".vftpl");
        if (!file.isFile()) return List.of();
        List<Piece> pieces = new ArrayList<>();
        try {
            for (String line : java.nio.file.Files.readAllLines(file.toPath())) {
                if (line.isBlank() || line.startsWith("#")) continue;
                String[] split = line.split(";", 4);
                if (split.length < 4) continue;
                pieces.add(new Piece(Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]),
                        Bukkit.createBlockData(split[3])));
            }
        } catch (Throwable ignored) {
            return List.of();
        }
        return pieces;
    }

    private List<String> customTemplates() {
        File[] files = templatesDir.listFiles((dir, name) -> name.endsWith(".vftpl"));
        if (files == null) return List.of();
        List<String> names = new ArrayList<>();
        for (File file : files) names.add(file.getName().replace(".vftpl", ""));
        names.sort(String::compareToIgnoreCase);
        return names;
    }

    private File schematicFile(String name) {
        String safe = name.replace("/", "").replace("\\", "");
        List<File> candidates = List.of(
                new File(plugin.getDataFolder(), "buildbot/schematics/" + safe),
                new File(plugin.getDataFolder(), "buildbot/schematics/" + safe + ".schem"),
                new File(plugin.getDataFolder(), "buildbot/templates/" + safe),
                new File(plugin.getDataFolder(), "buildbot/templates/" + safe + ".schem")
        );
        for (File file : candidates) if (file.isFile()) return file;
        return null;
    }

    private List<Piece> loadSchematic(File file, boolean includeAir) {
        try {
            ClipboardFormat format = ClipboardFormats.findByFile(file);
            if (format == null) return List.of();
            try (ClipboardReader reader = format.getReader(new FileInputStream(file))) {
                Clipboard clipboard = reader.read();
                BlockVector3 origin = clipboard.getOrigin();
                List<Piece> pieces = new ArrayList<>();
                for (BlockVector3 pos : clipboard.getRegion()) {
                    var block = clipboard.getBlock(pos);
                    Material material = BukkitAdapter.adapt(block.getBlockType());
                    if (material == null || (!includeAir && material.isAir())) continue;
                    BlockData data = BukkitAdapter.adapt(block);
                    pieces.add(new Piece(pos.x() - origin.x(), pos.y() - origin.y(), pos.z() - origin.z(), data));
                }
                return pieces;
            }
        } catch (Throwable ignored) {
            return List.of();
        }
    }

    private ArmorStand spawnWorker(Player player, Location origin, String template) {
        World world = origin.getWorld();
        if (world == null) return null;
        try {
            ArmorStand stand = (ArmorStand) world.spawnEntity(origin.clone().add(0.5, 1.0, 0.5), EntityType.ARMOR_STAND);
            stand.setCustomName(ChatColor.GOLD + "BuildBot " + ChatColor.GRAY + "» " + ChatColor.WHITE + template);
            stand.setCustomNameVisible(true);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setArms(true);
            stand.setBasePlate(false);
            stand.getEquipment().setHelmet(new ItemStack(Material.OBSERVER));
            stand.getEquipment().setChestplate(new ItemStack(Material.LEATHER_CHESTPLATE));
            stand.getEquipment().setItemInMainHand(new ItemStack(Material.DIAMOND_PICKAXE));
            return stand;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private void moveWorker(Job job, Location loc) {
        if (job.worker == null || job.worker.isDead()) return;
        try {
            job.worker.teleport(loc.clone().add(0.5, 1.0, 0.5));
            job.worker.swingMainHand();
        } catch (Throwable ignored) {}
    }

    private void removeWorker(Job job) {
        if (job.worker == null) return;
        try { job.worker.remove(); } catch (Throwable ignored) {}
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (command.getName().equalsIgnoreCase("mapsetup")) {
            if (args.length == 1) {
                for (String option : List.of("anchor", "preview", "state", "confirm", "cancel", "undo", "status", "modules")) {
                    if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(option);
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("preview")) {
                List<String> options = new ArrayList<>(List.of("all"));
                options.addAll(VetroMapGenerator.moduleIds());
                for (String option : options) {
                    if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(option);
                }
            } else if (args.length == 2 && args[0].equalsIgnoreCase("state")) {
                for (String option : List.of("dormant", "awakened", "broken")) {
                    if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(option);
                }
            }
            return out;
        }
        if (args.length == 1) {
            for (String option : List.of("preview", "schem", "pos1", "pos2", "save", "confirm", "speed", "stop", "undo", "templates", "repair")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(option);
            }
        } else if (args.length == 2 && args[0].equalsIgnoreCase("preview")) {
            List<String> options = new ArrayList<>(List.of("frostal_core", "chest_platform", "crate_podium", "event_holo_stand"));
            options.addAll(customTemplates());
            for (String option : options) {
                if (option.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(option);
            }
        }
        return out;
    }

    private record Piece(int dx, int dy, int dz, BlockData blockData) {}
    private record MapPosition(int x, int y, int z) {}
    private record Change(Location location, BlockData oldData) {}
    private record UndoRecord(String template, List<Change> changes, boolean configured) {}
    private record MapAnchor(Location location, int rotation) {}
    private record ModuleGeometry(Bounds bounds, long blockCount, String source) {}
    private record OverviewBounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        OverviewBounds include(OverviewBounds other) {
            return new OverviewBounds(
                    Math.min(minX, other.minX), Math.min(minY, other.minY), Math.min(minZ, other.minZ),
                    Math.max(maxX, other.maxX), Math.max(maxY, other.maxY), Math.max(maxZ, other.maxZ)
            );
        }

        OverviewBounds padded(int padding) {
            return new OverviewBounds(minX - padding, minY, minZ - padding, maxX + padding, maxY, maxZ + padding);
        }

        Map<String, Integer> asMap() {
            Map<String, Integer> values = new java.util.LinkedHashMap<>();
            values.put("minX", minX);
            values.put("minY", minY);
            values.put("minZ", minZ);
            values.put("maxX", maxX);
            values.put("maxY", maxY);
            values.put("maxZ", maxZ);
            return values;
        }
    }
    private record Bounds(int minX, int minY, int minZ, int maxX, int maxY, int maxZ) {
        int width() { return maxX - minX + 1; }
        int height() { return maxY - minY + 1; }
        int depth() { return maxZ - minZ + 1; }
    }

    private static final class Preview {
        final String template;
        final Location origin;
        final List<Piece> pieces;
        int blocksPerTick;
        final int rotation;
        final boolean bypassRegion;

        Preview(String template, Location origin, List<Piece> pieces, int blocksPerTick, int rotation,
                boolean bypassRegion) {
            this.template = template;
            this.origin = origin;
            this.pieces = pieces;
            this.blocksPerTick = blocksPerTick;
            this.rotation = rotation;
            this.bypassRegion = bypassRegion;
        }
    }

    private static final class Job {
        final String template;
        final Location origin;
        final Queue<Piece> queue;
        final List<Change> changes;
        final int blocksPerTick;
        final int rotation;
        final boolean bypassRegion;
        BukkitTask task;
        ArmorStand worker;

        Job(String template, Location origin, Queue<Piece> queue, List<Change> changes, int blocksPerTick,
            int rotation, boolean bypassRegion, BukkitTask task, ArmorStand worker) {
            this.template = template;
            this.origin = origin;
            this.queue = queue;
            this.changes = changes;
            this.blocksPerTick = blocksPerTick;
            this.rotation = rotation;
            this.bypassRegion = bypassRegion;
            this.task = task;
            this.worker = worker;
        }
    }
}
