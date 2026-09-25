package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.worldgen.RaidWorldLayout;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.Levelled;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityExplodeEvent;

/**
 * Simuliert "Regen-Walls": ein Wandblock (kein Obsidian -- das laeuft weiter komplett
 * ueber das bestehende HP-System) der neben einer Wasserquelle steht, kommt nach kurzer
 * Zeit von selbst zurueck, SOLANGE die Wasserquelle zu diesem Zeitpunkt noch da ist.
 * Raider muessen die Quelle also zuerst wegnehmen (Sandstacking/Eimer), bevor der
 * Durchbruch an dieser Stelle dauerhaft haelt -- keine echte Wasserphysik-Nachbildung,
 * sondern eine bewusst additive Simulation obendrauf, die das Obsidian-HP-System nicht
 * anfasst (siehe Abnahme-Dashboard "raid-system-audit").
 */
public final class RegenWallListener implements Listener {

    private static final BlockFace[] FACES = {
            BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST, BlockFace.UP, BlockFace.DOWN
    };

    private final FactionsPlugin plugin;

    public RegenWallListener(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onExplode(EntityExplodeEvent event) {
        for (Block block : event.blockList()) {
            considerForRegen(block, block.getType());
        }
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        considerForRegen(event.getBlock(), event.getBlock().getType());
    }

    private void considerForRegen(Block block, Material originalType) {
        if (originalType == Material.OBSIDIAN || originalType == Material.AIR) return;
        if (!isEnabled()) return;

        World world = block.getWorld();
        if (world == null || !world.getName().startsWith("faction_")) return;
        if (!RaidWorldLayout.inBuild(block.getX(), block.getZ())) return;

        String faction = plugin.getFactionManager() == null ? null : plugin.getFactionManager().getFactionAt(block.getLocation());
        if (faction == null || plugin.getRaidManager() == null || !plugin.getRaidManager().isFactionInRaid(faction)) return;

        if (!hasAdjacentWaterSource(block)) return;

        int x = block.getX(), y = block.getY(), z = block.getZ();
        String worldName = world.getName();
        long delayTicks = Math.max(20L, plugin.getConfig().getLong("raid.regenwall.delaySeconds", 6L) * 20L);

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            World w = Bukkit.getWorld(worldName);
            if (w == null) return;
            Block target = w.getBlockAt(x, y, z);
            if (target.getType() != Material.AIR) return; // schon wieder bebaut/gefuellt -- nicht ueberschreiben
            if (!hasAdjacentWaterSource(target)) return; // Quelle wurde inzwischen entfernt (Sandstacking) -- Durchbruch haelt
            target.setType(originalType, false);
        }, delayTicks);
    }

    private boolean hasAdjacentWaterSource(Block block) {
        for (BlockFace face : FACES) {
            Block neighbor = block.getRelative(face);
            if (neighbor.getType() != Material.WATER) continue;
            if (neighbor.getBlockData() instanceof Levelled levelled && levelled.getLevel() == 0) return true;
        }
        return false;
    }

    private boolean isEnabled() {
        return plugin.getConfig().getBoolean("raid.regenwall.enabled", true);
    }
}
