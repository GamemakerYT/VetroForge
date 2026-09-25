package com.vetroforge.instance;

import com.vetroforge.FactionsPlugin;
import org.bukkit.*;

public class RaidInstanceService {

    private final FactionsPlugin plugin;

    public RaidInstanceService(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public World ensureInstance(String targetFactionId) {
        String prefix = plugin.getConfig().getString("raids.instance.worldPrefix", "raid_");
        String name = prefix + sanitize(targetFactionId);
        World w = Bukkit.getWorld(name);
        if (w != null) return w;

        if (!plugin.getConfig().getBoolean("raids.instance.createEmptyIfMissing", true)) {
            return null;
        }

        WorldCreator wc = new WorldCreator(name);
        wc.type(WorldType.NORMAL);
        wc.environment(World.Environment.NORMAL);
        wc.generateStructures(false);
        World created = wc.createWorld();

        if (created != null) {
            created.setAutoSave(false);
            created.setGameRule(GameRule.DO_FIRE_TICK, false);
            created.setGameRule(GameRule.MOB_GRIEFING, false);
            created.setGameRule(GameRule.KEEP_INVENTORY, false);
        }
        // TODO: Später Schematic der Verteidiger-Basis hierhin kopieren (FAWE/WE)
        return created;
    }

    public Location entrySpawn(World w) {
        if (w == null) return null;
        Location s = w.getSpawnLocation().clone();
        s.setY(s.getWorld().getHighestBlockYAt(s) + 2);
        return s;
    }

    private String sanitize(String id) {
        return id.replaceAll("[^a-zA-Z0-9_\\-]", "_");
    }
}