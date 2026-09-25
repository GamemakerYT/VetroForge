// src/main/java/com/vetroforge/world/vworld/VWorldCommand.java
package com.vetroforge.world.vworld;

import com.vetroforge.FactionsPlugin;
import org.bukkit.*;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class VWorldCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final VetroWorldTemplateService templateService;
    private final VetroRegionSelectionService selectionService;
    private final VWorldFlatTemplateBuilder flatTemplateBuilder;
    private final VWorldSpawnWarzoneTemplateBuilder spawnWarzoneTemplateBuilder;

    public VWorldCommand(
            FactionsPlugin plugin,
            VetroWorldTemplateService templateService,
            VetroRegionSelectionService selectionService
    ) {
        this.plugin = plugin;
        this.templateService = templateService;
        this.selectionService = selectionService;
        this.flatTemplateBuilder = new VWorldFlatTemplateBuilder(plugin, templateService);
        this.spawnWarzoneTemplateBuilder = new VWorldSpawnWarzoneTemplateBuilder(plugin, templateService);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Nur Spieler.");
            return true;
        }

        if (!player.hasPermission("vetroforge.vworld.admin")) {
            player.sendMessage("§6VWorld §8» §cKeine Rechte.");
            return true;
        }

        if (args.length == 0) {
            sendHelp(player);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "paste" -> handlePasteShortcut(player, args);
            case "wand" -> giveWand(player);
            case "create" -> handleCreate(player, args);
            case "pos1" -> handlePos1(player);
            case "pos2" -> handlePos2(player);
            case "tp", "teleport" -> handleTeleport(player, args);
            case "worlds", "list" -> sendWorldList(player);
            case "region" -> handleRegion(player, args);
            case "point" -> handlePoint(player, args);
            case "template" -> handleTemplate(player, args);
            case "reload" -> {
                templateService.reload();
                player.sendMessage("§6VWorld §8» §aTemplates wurden neu geladen.");
            }
            default -> sendHelp(player);
        }

        return true;
    }

    private void handleCreate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§6VWorld §8» §cNutze:");
            player.sendMessage("§8» §e/vworld create <template>");
            player.sendMessage("§8» §e/vworld create faction <template>");
            player.sendMessage("§8» §e/vworld create spawnwarzone <template>");
            return;
        }

        if (args.length >= 3) {
            String type = args[1].toLowerCase(Locale.ROOT);
            String name = args[2];

            switch (type) {
                case "faction" -> flatTemplateBuilder.create(name, player);
                case "spawnwarzone", "spawn", "warzone" -> spawnWarzoneTemplateBuilder.create(name, player);
                default -> player.sendMessage("§6VWorld §8» §cUnbekannter Template-Typ: §f" + type);
            }

            return;
        }

        flatTemplateBuilder.create(args[1], player);
    }

    private void handlePos1(Player player) {
        selectionService.setPos1(player, player.getLocation());
        Location loc = player.getLocation();

        player.sendMessage("§6VWorld §8» §fPos1 gesetzt: §e"
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
    }

    private void handlePos2(Player player) {
        selectionService.setPos2(player, player.getLocation());
        Location loc = player.getLocation();

        player.sendMessage("§6VWorld §8» §fPos2 gesetzt: §e"
                + loc.getBlockX() + " " + loc.getBlockY() + " " + loc.getBlockZ());
    }

    private void handleRegion(Player player, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("set")) {
            player.sendMessage("§6VWorld §8» §cNutze: /vworld region set <build|raid|border|terrain|nobreak|spawn|scout>");
            return;
        }

        VetroRegionType type = VetroRegionType.from(args[2]);

        if (type == null) {
            player.sendMessage("§6VWorld §8» §cUnbekannter Region-Typ.");
            return;
        }

        Location pos1 = selectionService.getPos1(player);
        Location pos2 = selectionService.getPos2(player);

        if (pos1 == null || pos2 == null) {
            player.sendMessage("§6VWorld §8» §cSetze zuerst /vworld pos1 und /vworld pos2.");
            return;
        }

        if (!sameWorld(pos1, pos2)) {
            player.sendMessage("§6VWorld §8» §cPos1 und Pos2 müssen in derselben Welt sein.");
            return;
        }

        VetroWorldTemplate template = templateService.getActiveTemplate();
        VetroRegion region = new VetroRegion(type, pos1, pos2);

        template.setRegion(type, region);
        templateService.save();

        player.sendMessage("§6VWorld §8» §aRegion gespeichert: §e" + type.name());
        player.sendMessage("§8» §7Template: §f" + template.name());
    }

    private void handlePoint(Player player, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("set")) {
            player.sendMessage("§6VWorld §8» §cNutze: /vworld point set <spawn|raidspawn|scoutspawn|pasteorigin>");
            return;
        }

        VetroPointType type = VetroPointType.from(args[2]);

        if (type == null) {
            player.sendMessage("§6VWorld §8» §cUnbekannter Point-Typ.");
            return;
        }

        VetroWorldTemplate template = templateService.getActiveTemplate();
        VetroPoint point = VetroPoint.from(player.getLocation());

        template.setPoint(type, point);
        templateService.save();

        player.sendMessage("§6VWorld §8» §aPoint gespeichert: §e" + type.name());
        player.sendMessage("§8» §7Template: §f" + template.name());
    }
// In VWorldCommand.java
// Diese Methoden in die Klasse einfügen.

    private void giveWand(Player player) {
        player.getInventory().addItem(VWorldWand.create(plugin));
        player.sendMessage("§6VWorld §8» §aDu hast die VWorld Wand erhalten.");
        player.sendMessage("§8» §7Left Click: §fPos1");
        player.sendMessage("§8» §7Right Click: §fPos2");
        player.playSound(player.getLocation(), Sound.ITEM_TRIDENT_RETURN, 1.0f, 1.4f);
    }

    private void handlePasteShortcut(Player player, String[] args) {
        if (args.length < 3) {
            player.sendMessage("§6VWorld §8» §cNutze: /vworld paste <template> <world>");
            player.sendMessage("§8» §7Beispiel: §f/vworld paste spawn_warzone world");
            return;
        }

        String templateName = args[1];
        String worldName = args[2];

        VetroWorldTemplate template = templateService.getTemplate(templateName);

        if (template == null) {
            player.sendMessage("§6VWorld §8» §cTemplate nicht gefunden: §f" + templateName);
            return;
        }

        World world = resolveOrCreateWorld(worldName);

        if (world == null) {
            player.sendMessage("§6VWorld §8» §cWelt konnte nicht geladen/erstellt werden: §f" + worldName);
            return;
        }

        pasteTemplateIntoWorld(player, template, world, true);
    }

    private void handleGenerate(Player player, String[] args) {
        if (args.length < 3 || !args[1].equalsIgnoreCase("faction")) {
            player.sendMessage("§6VWorld §8» §cNutze: /vworld generate faction <name> [template]");
            player.sendMessage("§8» §7Beispiel: §f/vworld generate faction FrostBlade");
            player.sendMessage("§8» §7Beispiel: §f/vworld generate faction FrostBlade faction_default");
            return;
        }

        String factionName = args[2];
        String templateName = args.length >= 4
                ? args[3]
                : templateService.getActiveTemplateName();

        VetroWorldTemplate template = templateService.getTemplate(templateName);

        if (template == null) {
            player.sendMessage("§6VWorld §8» §cTemplate nicht gefunden: §f" + templateName);
            return;
        }

        String worldName = "faction_" + sanitizeWorldName(factionName);
        World world = resolveOrCreateWorld(worldName);

        if (world == null) {
            player.sendMessage("§6VWorld §8» §cFaction-Welt konnte nicht erstellt werden.");
            return;
        }

        setupGeneratedWorldRules(world);

        boolean pasted = pasteTemplateIntoWorld(player, template, world, false);

        VetroPoint spawnPoint = template.getPoint(VetroPointType.SPAWN);

        Location spawn = spawnPoint != null
                ? spawnPoint.toLocation(world)
                : world.getSpawnLocation().clone().add(0.5, 1.0, 0.5);

        world.setSpawnLocation(spawn);
        player.teleport(spawn);

        player.sendMessage("§6VWorld §8» §aFaction-Welt erstellt.");
        player.sendMessage("§8» §7Faction: §f" + factionName);
        player.sendMessage("§8» §7Welt: §f" + world.getName());
        player.sendMessage("§8» §7Template: §f" + template.name());
        player.sendMessage("§8» §7Schematic: §f" + (pasted ? "eingefügt" : "nicht eingefügt"));
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1.0f, 1.2f);
    }

    private boolean pasteTemplateIntoWorld(
            Player player,
            VetroWorldTemplate template,
            World world,
            boolean teleportAfterPaste
    ) {
        if (template == null || world == null) {
            return false;
        }

        if (!isWorldEditAvailable()) {
            player.sendMessage("§6VWorld §8» §cFastAsyncWorldEdit/WorldEdit ist nicht geladen.");
            return false;
        }

        File schematicFile = templateService.getSchematicFile(template);

        if (!schematicFile.exists()) {
            player.sendMessage("§6VWorld §8» §cSchematic nicht gefunden:");
            player.sendMessage("§8» §f" + schematicFile.getPath());
            return false;
        }

        Location paste = template.pasteOrigin().toLocation(world);

        try {
            com.vetroforge.schematic.SchematicPasteService pasteService =
                    new com.vetroforge.schematic.SchematicPasteService();

            boolean success = pasteService.paste(schematicFile, paste, true);

            if (!success) {
                player.sendMessage("§6VWorld §8» §cSchematic konnte nicht eingefügt werden.");
                return false;
            }

            if (teleportAfterPaste) {
                player.teleport(paste.clone().add(0.5, 1.0, 0.5));
            }

            player.sendMessage("§6VWorld §8» §aSchematic eingefügt.");
            player.sendMessage("§8» §7Template: §f" + template.name());
            player.sendMessage("§8» §7Welt: §f" + world.getName());
            player.sendMessage("§8» §7Ort: §f"
                    + paste.getBlockX() + " "
                    + paste.getBlockY() + " "
                    + paste.getBlockZ());

            return true;
        } catch (Throwable throwable) {
            player.sendMessage("§6VWorld §8» §cWorldEdit/FAWE konnte die Schematic nicht einfügen.");
            player.sendMessage("§8» §7Fehler: §f" + throwable.getClass().getSimpleName());
            plugin.getLogger().warning("[VWorld] Paste failed: " + throwable.getMessage());
            return false;
        }
    }

    private World resolveOrCreateWorld(String input) {
        if (input == null || input.isBlank()) {
            return null;
        }

        World loaded = Bukkit.getWorld(input);

        if (loaded != null) {
            return loaded;
        }

        try {
            File folder = new File(Bukkit.getWorldContainer(), input);

            if (folder.exists()) {
                return Bukkit.createWorld(new WorldCreator(input));
            }
        } catch (Throwable ignored) {
        }

        try {
            WorldCreator creator = new WorldCreator(input);
            creator.environment(World.Environment.NORMAL);
            creator.type(WorldType.FLAT);

            return creator.createWorld();
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[VWorld] Could not create world " + input + ": " + throwable.getMessage());
            return null;
        }
    }

    private void setupGeneratedWorldRules(World world) {
        if (world == null) {
            return;
        }

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
        } catch (Throwable ignored) {
        }
    }

    private boolean isWorldEditAvailable() {
        return Bukkit.getPluginManager().isPluginEnabled("FastAsyncWorldEdit")
                || Bukkit.getPluginManager().isPluginEnabled("WorldEdit");
    }

    private String sanitizeWorldName(String input) {
        return input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");
    }
    private void handleTemplate(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§6VWorld §8» §cNutze: /vworld template <set|save|info|paste>");
            return;
        }

        String action = args[1].toLowerCase(Locale.ROOT);

        switch (action) {
            case "set" -> {
                if (args.length < 3) {
                    player.sendMessage("§6VWorld §8» §cNutze: /vworld template set <name>");
                    return;
                }

                templateService.setActiveTemplate(args[2]);
                player.sendMessage("§6VWorld §8» §aAktives Template gesetzt: §e" + args[2]);
            }

            case "save" -> {
                templateService.save();
                player.sendMessage("§6VWorld §8» §aTemplates gespeichert.");
            }

            case "info" -> sendTemplateInfo(player);

            case "paste" -> pasteTemplate(player, args);

            default -> player.sendMessage("§6VWorld §8» §cNutze: /vworld template <set|save|info|paste>");
        }
    }

    private void pasteTemplate(Player player, String[] args) {
        if (!isWorldEditAvailable()) {
            player.sendMessage("§6VWorld §8» §cFastAsyncWorldEdit/WorldEdit ist nicht geladen.");
            player.sendMessage("§8» §7Installiere FAWE oder lade den Server neu, wenn FAWE gerade erst hinzugefügt wurde.");
            return;
        }

        VetroWorldTemplate template = args.length >= 3
                ? templateService.getOrCreate(args[2])
                : templateService.getActiveTemplate();

        File schematicFile = templateService.getSchematicFile(template);

        if (!schematicFile.exists()) {
            player.sendMessage("§6VWorld §8» §cSchematic nicht gefunden:");
            player.sendMessage("§8» §f" + schematicFile.getPath());
            return;
        }

        Location paste = template.pasteOrigin().toLocation(player.getWorld());

        try {
            com.vetroforge.schematic.SchematicPasteService pasteService =
                    new com.vetroforge.schematic.SchematicPasteService();

            boolean success = pasteService.paste(schematicFile, paste, true);

            if (!success) {
                player.sendMessage("§6VWorld §8» §cSchematic konnte nicht eingefügt werden.");
                return;
            }

            player.sendMessage("§6VWorld §8» §aSchematic eingefügt.");
            player.sendMessage("§8» §7Template: §f" + template.name());
            player.sendMessage("§8» §7Ort: §f"
                    + paste.getBlockX() + " " + paste.getBlockY() + " " + paste.getBlockZ());
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 1.0f, 1.4f);
        } catch (Throwable throwable) {
            player.sendMessage("§6VWorld §8» §cWorldEdit/FAWE konnte die Schematic nicht einfügen.");
            player.sendMessage("§8» §7Fehler: §f" + throwable.getClass().getSimpleName());
            plugin.getLogger().warning("[VWorld] Paste failed: " + throwable.getMessage());
        }
    }


    private void sendTemplateInfo(Player player) {
        VetroWorldTemplate template = templateService.getActiveTemplate();

        player.sendMessage("§6VWorld §8» §fAktives Template: §e" + template.name());
        player.sendMessage("§8» §7Schematic: §f" + template.schematic());
        player.sendMessage("§8» §7Points: §f" + template.points().size());
        player.sendMessage("§8» §7Regions: §f" + template.regions().size());

        for (VetroPointType type : template.points().keySet()) {
            player.sendMessage("§8- §ePoint §f" + type.name());
        }

        for (VetroRegionType type : template.regions().keySet()) {
            player.sendMessage("§8- §cRegion §f" + type.name());
        }
    }

    private void sendHelp(Player player) {
        player.sendMessage("§6VWorld §8» §fCommands:");
        player.sendMessage("§8» §e/vworld create <template>");
        player.sendMessage("§8» §e/vworld create faction <template>");
        player.sendMessage("§8» §e/vworld create spawnwarzone <template>");
        player.sendMessage("§8» §e/vworld template set <name>");
        player.sendMessage("§8» §e/vworld template save");
        player.sendMessage("§8» §e/vworld template info");
        player.sendMessage("§8» §e/vworld template paste [name]");
        player.sendMessage("§8» §e/vworld pos1");
        player.sendMessage("§8» §e/vworld pos2");
        player.sendMessage("§8» §e/vworld region set <type>");
        player.sendMessage("§8» §e/vworld point set <type>");
        player.sendMessage("§8» §e/vworld reload");
        player.sendMessage("§8» §e/vworld tp <welt|template>");
        player.sendMessage("§8» §e/vworld worlds");
        player.sendMessage("§8» §e/vworld wand");
        player.sendMessage("§8» §e/vworld paste <template> <world>");
        player.sendMessage("§8» §e/vworld generate faction <name> [template]");
    }

    private boolean sameWorld(Location a, Location b) {
        if (a == null || b == null) return false;

        World aw = a.getWorld();
        World bw = b.getWorld();

        if (aw == null || bw == null) return false;

        return aw.getUID().equals(bw.getUID());
    }

    private void handleTeleport(Player player, String[] args) {
        if (args.length < 2) {
            player.sendMessage("§6VWorld §8» §cNutze: /vworld tp <welt|template>");
            player.sendMessage("§8» §7Beispiel: §f/vworld tp SpawnWarzoneTemp");
            player.sendMessage("§8» §7Oder: §f/vworld tp vtemplate_spawnwarzonetemp");
            return;
        }

        String input = args[1];
        World world = resolveWorld(input);

        if (world == null) {
            player.sendMessage("§6VWorld §8» §cWelt nicht gefunden: §f" + input);
            player.sendMessage("§8» §7Nutze §f/vworld worlds§7, um verfügbare Welten zu sehen.");
            return;
        }

        Location target = world.getSpawnLocation().clone().add(0.5, 1.0, 0.5);

        player.teleport(target);
        player.sendMessage("§6VWorld §8» §aTeleportiert zu §e" + world.getName() + "§a.");
        player.playSound(player.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1.0f, 1.2f);
    }

    private World resolveWorld(String input) {
        if (input == null || input.isBlank()) return null;

        World exact = Bukkit.getWorld(input);

        if (exact != null) {
            return exact;
        }

        String templateWorld = "vtemplate_" + input.toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9_\\-]", "_");

        World byTemplateName = Bukkit.getWorld(templateWorld);

        if (byTemplateName != null) {
            return byTemplateName;
        }

        try {
            if (new File(Bukkit.getWorldContainer(), input).exists()) {
                return Bukkit.createWorld(new WorldCreator(input));
            }
        } catch (Throwable ignored) {}

        try {
            if (new File(Bukkit.getWorldContainer(), templateWorld).exists()) {
                return Bukkit.createWorld(new WorldCreator(templateWorld));
            }
        } catch (Throwable ignored) {}

        return null;
    }

    private void sendWorldList(Player player) {
        player.sendMessage("§6VWorld §8» §fGeladene Welten:");

        for (World world : Bukkit.getWorlds()) {
            player.sendMessage("§8» §e" + world.getName()
                    + " §8| §7Spieler: §f" + world.getPlayers().size()
                    + " §8| §7Spawn: §f"
                    + world.getSpawnLocation().getBlockX() + " "
                    + world.getSpawnLocation().getBlockY() + " "
                    + world.getSpawnLocation().getBlockZ());
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String label, String[] args) {
        List<String> out = new ArrayList<>();

        if (args.length == 1) {
            addMatching(out, args[0],
                    "create",
                    "template",
                    "pos1",
                    "pos2",
                    "region",
                    "point",
                    "tp",
                    "teleport",
                    "worlds",
                    "list",
                    "reload",
                    "wand",
                    "paste",
                    "generate"
            );
            return out;
        }
// In VWorldCommand.java
// In onTabComplete zusätzlich einfügen.

        if (args.length == 2 && args[0].equalsIgnoreCase("paste")) {
            for (VetroWorldTemplate template : templateService.getTemplates()) {
                addMatching(out, args[1], template.name());
            }
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("paste")) {
            for (World world : Bukkit.getWorlds()) {
                addMatching(out, args[2], world.getName());
            }
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("generate")) {
            addMatching(out, args[1], "faction");
            return out;
        }

        if (args.length == 4 && args[0].equalsIgnoreCase("generate") && args[1].equalsIgnoreCase("faction")) {
            for (VetroWorldTemplate template : templateService.getTemplates()) {
                addMatching(out, args[3], template.name());
            }
            return out;
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("template")) {
            addMatching(out, args[1], "set", "save", "info", "paste");
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("template")
                && (args[1].equalsIgnoreCase("set") || args[1].equalsIgnoreCase("paste"))) {
            for (VetroWorldTemplate template : templateService.getTemplates()) {
                addMatching(out, args[2], template.name());
            }
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("create")) {
            addMatching(out, args[1], "faction", "spawnwarzone");
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("create")) {
            addMatching(out, args[2], "default", "frost_cycle", "spawn_warzone");
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("region")) {
            addMatching(out, args[1], "set");
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("region") && args[1].equalsIgnoreCase("set")) {
            for (VetroRegionType type : VetroRegionType.values()) {
                addMatching(out, args[2], type.key());
            }
            return out;
        }

        if (args.length == 2 && args[0].equalsIgnoreCase("point")) {
            addMatching(out, args[1], "set");
            return out;
        }

        if (args.length == 3 && args[0].equalsIgnoreCase("point") && args[1].equalsIgnoreCase("set")) {
            for (VetroPointType type : VetroPointType.values()) {
                addMatching(out, args[2], type.key());
            }
            return out;
        }

        if (args.length == 2 && (args[0].equalsIgnoreCase("tp") || args[0].equalsIgnoreCase("teleport"))) {
            for (World world : Bukkit.getWorlds()) {
                addMatching(out, args[1], world.getName());
            }

            for (VetroWorldTemplate template : templateService.getTemplates()) {
                addMatching(out, args[1], template.name());
                addMatching(out, args[1], "vtemplate_" + template.name().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_\\-]", "_"));
            }

            return out;
        }

        return out;
    }

    private void addMatching(List<String> out, String current, String... values) {
        String lower = current == null ? "" : current.toLowerCase(Locale.ROOT);

        for (String value : values) {
            if (value.toLowerCase(Locale.ROOT).startsWith(lower)) {
                out.add(value);
            }
        }
    }
}