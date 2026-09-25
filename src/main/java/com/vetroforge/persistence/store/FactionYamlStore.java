// ===============================================
// File: src/main/java/com/vetroforge/persistence/store/FactionYamlStore.java
// ===============================================
package com.vetroforge.persistence.store;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.factions.FactionManager;
import com.vetroforge.persistence.YamlIO;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.configuration.file.FileConfiguration;

import java.io.File;
import java.util.*;

/**
 * Speichert/liest Fraktionen (Mitglieder, Offiziere, Leader, Allies, Home).
 * Pfad: plugins/VetroForge/data/factions.yml
 */
public class FactionYamlStore {

    private final File file;

    public FactionYamlStore(FactionsPlugin plugin) {
        this.file = new File(plugin.getDataFolder(), "data/factions.yml");
    }

    public FactionManager.Snapshot load() {
        FileConfiguration cfg = YamlIO.load(file);
        FactionManager.Snapshot snap = new FactionManager.Snapshot();

        if (cfg.isConfigurationSection("factions")) {
            for (String name : cfg.getConfigurationSection("factions").getKeys(false)) {
                String base = "factions."+name+".";
                FactionManager.FactionDTO dto = new FactionManager.FactionDTO();
                dto.name = name;
                String leaderStr = cfg.getString(base+"leader");
                if (leaderStr == null) continue;
                try { dto.leader = java.util.UUID.fromString(leaderStr); }
                catch (IllegalArgumentException ex) { continue; }

                dto.createdAt = cfg.getLong(base+"createdAt", System.currentTimeMillis());

                java.util.List<String> off = cfg.getStringList(base+"officers");
                for (String s : off) {
                    try { dto.officers.add(java.util.UUID.fromString(s)); } catch (Exception ignored) {}
                }

                java.util.List<String> mem = cfg.getStringList(base+"members");
                for (String s : mem) {
                    try { dto.members.add(java.util.UUID.fromString(s)); } catch (Exception ignored) {}
                }

                if (cfg.isConfigurationSection(base+"home")) {
                    FactionManager.SerializableLocation sl = new FactionManager.SerializableLocation();
                    sl.world = cfg.getString(base+"home.world");
                    sl.x = cfg.getDouble(base+"home.x");
                    sl.y = cfg.getDouble(base+"home.y");
                    sl.z = cfg.getDouble(base+"home.z");
                    sl.yaw = (float) cfg.getDouble(base+"home.yaw");
                    sl.pitch = (float) cfg.getDouble(base+"home.pitch");
                    dto.home = sl;
                }
                snap.factions.put(name, dto);
            }
        }

        if (cfg.isConfigurationSection("allies")) {
            for (String f : cfg.getConfigurationSection("allies").getKeys(false)) {
                snap.allies.put(f, cfg.getStringList("allies."+f));
            }
        }
        return snap;
    }

    public void save(FactionManager.Snapshot snap) {
        FileConfiguration cfg = YamlIO.load(file);
        cfg.set("factions", null);
        cfg.set("allies", null);

        if (snap != null && snap.factions != null) {
            for (java.util.Map.Entry<String, FactionManager.FactionDTO> e : snap.factions.entrySet()) {
                String base = "factions."+e.getKey()+".";
                FactionManager.FactionDTO dto = e.getValue();

                cfg.set(base+"leader", dto.leader != null ? dto.leader.toString() : null);
                cfg.set(base+"createdAt", dto.createdAt != null ? dto.createdAt.longValue() : System.currentTimeMillis());

                java.util.List<String> off = new java.util.ArrayList<String>();
                for (java.util.UUID u : dto.officers) off.add(u.toString());
                cfg.set(base+"officers", off);

                java.util.List<String> mem = new java.util.ArrayList<String>();
                for (java.util.UUID u : dto.members) mem.add(u.toString());
                cfg.set(base+"members", mem);

                if (dto.home != null) {
                    cfg.set(base+"home.world", dto.home.world);
                    cfg.set(base+"home.x", dto.home.x);
                    cfg.set(base+"home.y", dto.home.y);
                    cfg.set(base+"home.z", dto.home.z);
                    cfg.set(base+"home.yaw", dto.home.yaw);
                    cfg.set(base+"home.pitch", dto.home.pitch);
                }
            }
        }

        if (snap != null && snap.allies != null) {
            for (java.util.Map.Entry<String, java.util.List<String>> e : snap.allies.entrySet()) {
                cfg.set("allies."+e.getKey(), e.getValue());
            }
        }
        YamlIO.save(cfg, file);
    }
}