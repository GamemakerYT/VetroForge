// src/main/java/com/vetroforge/world/WorldGeneratorManager.java
package com.vetroforge.world;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.worldgen.MysticRaidArenaGenerator;
import org.bukkit.World;
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;

public class WorldGeneratorManager {

    public enum GeneratorType { RAID_ARENA_MYSTIC }

    private final FactionsPlugin plugin;
    public WorldGeneratorManager(FactionsPlugin plugin){ this.plugin = plugin; }

    public World createWorld(String worldName, GeneratorType type) {
        int y = plugin.getConfig().getInt("factions.world.yLevel", 64);
        WorldCreator wc = new WorldCreator(worldName);
        wc.environment(World.Environment.NORMAL);
        wc.type(WorldType.NORMAL);
        wc.generator(new MysticRaidArenaGenerator(y)); // einzige Option
        return wc.createWorld();
    }
}