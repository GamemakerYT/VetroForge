// ===============================================
// File: src/main/java/com/vetroforge/persistence/YamlIO.java
// ===============================================
package com.vetroforge.persistence;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;

/** Kleine Hilfsklasse für YAML I/O (sorgt dafür, dass Ordner/Datei existiert). */
public final class YamlIO {
    private YamlIO(){}

    public static FileConfiguration load(File file) {
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            if (!file.exists()) file.createNewFile();
            return YamlConfiguration.loadConfiguration(file);
        } catch (Exception ex) {
            throw new RuntimeException("Could not load yaml: "+file, ex);
        }
    }

    public static void save(FileConfiguration cfg, File file) {
        try {
            if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
            cfg.save(file);
        } catch (Exception ex) {
            throw new RuntimeException("Could not save yaml: "+file, ex);
        }
    }
}