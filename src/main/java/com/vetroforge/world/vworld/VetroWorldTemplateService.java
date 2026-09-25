// src/main/java/com/vetroforge/world/vworld/VetroWorldTemplateService.java
package com.vetroforge.world.vworld;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.persistence.YamlIO;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class VetroWorldTemplateService {

    private final FactionsPlugin plugin;
    private final File file;

    private final Map<String, VetroWorldTemplate> templates = new LinkedHashMap<>();

    private String activeTemplateName = "faction_default";

    public VetroWorldTemplateService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "vworld_templates.yml");

        reload();
    }

    public void reload() {
        templates.clear();

        if (!file.exists()) {
            createDefaultFile();
        }

        FileConfiguration cfg = YamlIO.load(file);

        activeTemplateName = cfg.getString("active-template", "faction_default");

        ConfigurationSection root = cfg.getConfigurationSection("templates");

        if (root != null) {
            for (String templateName : root.getKeys(false)) {
                String base = "templates." + templateName;

                VetroWorldTemplate template = new VetroWorldTemplate(templateName);
                template.setSchematic(cfg.getString(base + ".schematic", templateName + ".schem"));

                VetroPoint pasteOrigin = readPoint(cfg, base + ".paste-origin");

                if (pasteOrigin != null) {
                    template.setPasteOrigin(pasteOrigin);
                }

                ConfigurationSection points = cfg.getConfigurationSection(base + ".points");

                if (points != null) {
                    for (String pointKey : points.getKeys(false)) {
                        VetroPointType type = VetroPointType.from(pointKey);
                        if (type == null) continue;

                        VetroPoint point = readPoint(cfg, base + ".points." + pointKey);
                        if (point == null) continue;

                        template.setPoint(type, point);
                    }
                }

                ConfigurationSection regions = cfg.getConfigurationSection(base + ".regions");

                if (regions != null) {
                    for (String regionKey : regions.getKeys(false)) {
                        VetroRegionType type = VetroRegionType.from(regionKey);
                        if (type == null) continue;

                        String path = base + ".regions." + regionKey;

                        VetroRegion region = new VetroRegion(
                                type,
                                cfg.getInt(path + ".min.x"),
                                cfg.getInt(path + ".min.y"),
                                cfg.getInt(path + ".min.z"),
                                cfg.getInt(path + ".max.x"),
                                cfg.getInt(path + ".max.y"),
                                cfg.getInt(path + ".max.z")
                        );

                        template.setRegion(type, region);
                    }
                }

                templates.put(normalize(templateName), template);
            }
        }

        if (!templates.containsKey(normalize(activeTemplateName))) {
            VetroWorldTemplate fallback = new VetroWorldTemplate(activeTemplateName);
            templates.put(normalize(activeTemplateName), fallback);
            save();
        }
    }

    public void save() {
        FileConfiguration cfg = new YamlConfiguration();

        cfg.set("active-template", activeTemplateName);

        for (VetroWorldTemplate template : templates.values()) {
            String base = "templates." + template.name();

            cfg.set(base + ".schematic", template.schematic());

            writePoint(cfg, base + ".paste-origin", template.pasteOrigin());

            for (Map.Entry<VetroPointType, VetroPoint> entry : template.points().entrySet()) {
                writePoint(cfg, base + ".points." + entry.getKey().key(), entry.getValue());
            }

            for (Map.Entry<VetroRegionType, VetroRegion> entry : template.regions().entrySet()) {
                VetroRegion region = entry.getValue();
                String path = base + ".regions." + entry.getKey().key();

                cfg.set(path + ".type", region.type().name());
                cfg.set(path + ".min.x", region.minX());
                cfg.set(path + ".min.y", region.minY());
                cfg.set(path + ".min.z", region.minZ());
                cfg.set(path + ".max.x", region.maxX());
                cfg.set(path + ".max.y", region.maxY());
                cfg.set(path + ".max.z", region.maxZ());
            }
        }

        YamlIO.save(cfg, file);
    }

    public VetroWorldTemplate getActiveTemplate() {
        return getOrCreate(activeTemplateName);
    }

    public String getActiveTemplateName() {
        return activeTemplateName;
    }

    public VetroWorldTemplate getTemplate(String name) {
        if (name == null || name.isBlank()) return null;
        return templates.get(normalize(name));
    }

    public VetroWorldTemplate getOrCreate(String name) {
        String safeName = name == null || name.isBlank() ? "faction_default" : name;
        return templates.computeIfAbsent(normalize(safeName), key -> new VetroWorldTemplate(safeName));
    }

    public void setActiveTemplate(String name) {
        if (name == null || name.isBlank()) return;

        VetroWorldTemplate template = getOrCreate(name);

        activeTemplateName = template.name();
        save();
    }

    public Collection<VetroWorldTemplate> getTemplates() {
        return templates.values();
    }

    public File getSchematicFile(VetroWorldTemplate template) {
        File folder = new File(plugin.getDataFolder(), "schematics/faction_world");

        if (!folder.exists()) {
            folder.mkdirs();
        }

        return new File(folder, template.schematic());
    }

    private void createDefaultFile() {
        FileConfiguration cfg = new YamlConfiguration();

        cfg.set("active-template", "faction_default");
        cfg.set("templates.faction_default.schematic", "faction_default.schem");

        cfg.set("templates.faction_default.paste-origin.x", 0.0);
        cfg.set("templates.faction_default.paste-origin.y", 64.0);
        cfg.set("templates.faction_default.paste-origin.z", 0.0);
        cfg.set("templates.faction_default.paste-origin.yaw", 0.0);
        cfg.set("templates.faction_default.paste-origin.pitch", 0.0);

        YamlIO.save(cfg, file);
    }

    private VetroPoint readPoint(FileConfiguration cfg, String path) {
        if (!cfg.contains(path + ".x")) return null;

        return new VetroPoint(
                cfg.getDouble(path + ".x"),
                cfg.getDouble(path + ".y"),
                cfg.getDouble(path + ".z"),
                (float) cfg.getDouble(path + ".yaw"),
                (float) cfg.getDouble(path + ".pitch")
        );
    }

    private void writePoint(FileConfiguration cfg, String path, VetroPoint point) {
        if (point == null) return;

        cfg.set(path + ".x", point.x());
        cfg.set(path + ".y", point.y());
        cfg.set(path + ".z", point.z());
        cfg.set(path + ".yaw", point.yaw());
        cfg.set(path + ".pitch", point.pitch());
    }

    private String normalize(String input) {
        return input.toLowerCase(Locale.ROOT);
    }
}