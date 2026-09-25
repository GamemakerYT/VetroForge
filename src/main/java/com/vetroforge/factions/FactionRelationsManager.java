// ===================================================================
// File: src/main/java/com/vetroforge/factions/FactionRelationsManager.java
// ===================================================================
package com.vetroforge.factions;

import com.vetroforge.FactionsPlugin;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.*;

/**
 * Verwaltet Relations zwischen Factions (symmetrisch) + Persistenz.
 * Erwartet, dass FactionsPlugin.getFactionManager().getFactionOf(UUID) existiert.
 */
public class FactionRelationsManager {

    private final FactionsPlugin plugin;
    /** Map<A, Map<B, RelationType>>  (a,b sind faction-names lowercased; OWN wird nie gespeichert) */
    private final Map<String, Map<String, RelationType>> rel = new HashMap<String, Map<String, RelationType>>();
    private final File file;

    public FactionRelationsManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/relations.yml");
    }

    public void load() {
        rel.clear();
        if (!file.exists()) return;
        FileConfiguration cfg = YamlConfiguration.loadConfiguration(file);
        for (String a : cfg.getKeys(false)) {
            String A = a.toLowerCase(java.util.Locale.ROOT);
            Map<String, Object> row = cfg.getConfigurationSection(a).getValues(false);
            Map<String, RelationType> map = new HashMap<String, RelationType>();
            for (Map.Entry<String, Object> e : row.entrySet()) {
                String B = e.getKey().toLowerCase(java.util.Locale.ROOT);
                RelationType t = parse(String.valueOf(e.getValue()));
                if (t != null && t != RelationType.OWN) map.put(B, t);
            }
            if (!map.isEmpty()) rel.put(A, map);
        }
    }

    public void save() {
        FileConfiguration out = new YamlConfiguration();
        for (Map.Entry<String, Map<String, RelationType>> e : rel.entrySet()) {
            String A = e.getKey();
            for (Map.Entry<String, RelationType> e2 : e.getValue().entrySet()) {
                String B = e2.getKey();
                out.set(A + "." + B, e2.getValue().name());
            }
        }
        try {
            file.getParentFile().mkdirs();
            out.save(file);
        } catch (Exception ignored) {}
    }

    /* =================== API =================== */

    public RelationType getRelation(String fa, String fb) {
        if (fa == null || fb == null) return RelationType.NEUTRAL;
        if (fa.equalsIgnoreCase(fb)) return RelationType.OWN;
        Map<String, RelationType> row = rel.get(fa.toLowerCase(java.util.Locale.ROOT));
        if (row == null) return RelationType.NEUTRAL;
        RelationType t = row.get(fb.toLowerCase(java.util.Locale.ROOT));
        return t == null ? RelationType.NEUTRAL : t;
    }

    public void setRelation(String fa, String fb, RelationType type) {
        if (fa == null || fb == null) return;
        if (fa.equalsIgnoreCase(fb)) return; // OWN wird nicht gesetzt
        putOne(fa, fb, type);
        putOne(fb, fa, type);
        save();
    }

    public void clearRelation(String fa, String fb) {
        if (fa == null || fb == null) return;
        if (fa.equalsIgnoreCase(fb)) return;
        removeOne(fa, fb);
        removeOne(fb, fa);
        save();
    }

    public Map<String, RelationType> getAllFor(String fa) {
        Map<String, RelationType> m = rel.get(fa == null ? "" : fa.toLowerCase(java.util.Locale.ROOT));
        return m == null ? Collections.<String, RelationType>emptyMap() : new HashMap<String, RelationType>(m);
    }

    public boolean areFriendly(UUID a, UUID b) {
        String fa = factionOf(a);
        String fb = factionOf(b);
        return getRelation(fa, fb).isFriendly();
    }

    public boolean isHostile(String fa, String fb) { return getRelation(fa, fb).isHostile(); }

    public String factionOf(UUID id) {
        try { return plugin.getFactionManager().getFactionOf(id); } catch (Throwable t) { return null; }
    }

    /* =================== intern =================== */

    private void putOne(String fa, String fb, RelationType t) {
        String A = fa.toLowerCase(java.util.Locale.ROOT);
        String B = fb.toLowerCase(java.util.Locale.ROOT);
        if (t == null || t == RelationType.NEUTRAL) {
            removeOne(A, B);
            return;
        }
        Map<String, RelationType> row = rel.get(A);
        if (row == null) { row = new HashMap<String, RelationType>(); rel.put(A, row); }
        row.put(B, t);
    }

    private void removeOne(String fa, String fb) {
        String A = fa.toLowerCase(java.util.Locale.ROOT);
        String B = fb.toLowerCase(java.util.Locale.ROOT);
        Map<String, RelationType> row = rel.get(A);
        if (row != null) {
            row.remove(B);
            if (row.isEmpty()) rel.remove(A);
        }
    }

    private RelationType parse(String s) {
        if (s == null) return null;
        try { return RelationType.valueOf(s.trim().toUpperCase(java.util.Locale.ROOT)); }
        catch (Exception e){ return null; }
    }
}