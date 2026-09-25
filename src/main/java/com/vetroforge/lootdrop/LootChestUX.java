// src/main/java/com/vetroforge/lootdrop/LootChestUX.java
package com.vetroforge.lootdrop;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.Rarity;
import org.bukkit.*;
import org.bukkit.block.Chest;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.metadata.FixedMetadataValue;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class LootChestUX implements Listener {
    public static final String META = "vetro_lootdrop";
    private final FactionsPlugin plugin;
    private final Set<UUID> openCooldown = new HashSet<>();

    public LootChestUX(FactionsPlugin p){
        this.plugin = p;
        Bukkit.getPluginManager().registerEvents(this,p);
    }

    /** Spawnt eine hübsche Kiste mit Titel/Partikeln + markiert sie als LootDrop. */
    public void spawnLootChest(Location loc, Rarity tier){
        loc.getBlock().setType(Material.CHEST,false);
        Chest ch = (Chest) loc.getBlock().getState();
        ch.setCustomName("§c§lLootdrop §8» " + tier.color + tier.name());
        ch.update(true,false);

        loc.getBlock().setMetadata(META, new FixedMetadataValue(plugin, tier.name()));

        loc.getWorld().spawnParticle(Particle.ENCHANT, loc.clone().add(.5,1,.5), 30, .4,.5,.4, .02);
        try { loc.getWorld().playSound(loc, org.bukkit.Sound.BLOCK_END_PORTAL_SPAWN, 1f, 1.2f);}catch(Throwable ignore){}
    }

    /** Debounce + Feedback bei Rare+ */
    @EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
    public void onOpen(InventoryOpenEvent e){
        if (!(e.getInventory().getHolder() instanceof Chest ch)) return;
        var b = ch.getBlock();
        if (!b.hasMetadata(META)) return;

        UUID id = e.getPlayer().getUniqueId();
        if (!openCooldown.add(id)) { e.setCancelled(true); return; }
        Bukkit.getScheduler().runTaskLater(plugin, () -> openCooldown.remove(id), 20L); // 1s debounce

        String rar = b.getMetadata(META).get(0).asString();
        if (!"COMMON".equalsIgnoreCase(rar)) {
            try { b.getWorld().playSound(b.getLocation(), org.bukkit.Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);}catch(Throwable ignore){}
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent e){
        if (!e.getBlock().hasMetadata(META)) return;
        e.setCancelled(true); // nicht abbaubar (oder Timer-gesteuert, wenn du das willst)
    }
}
