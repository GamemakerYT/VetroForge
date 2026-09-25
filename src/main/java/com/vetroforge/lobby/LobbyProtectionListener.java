package com.vetroforge.lobby;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.warzone.WarzoneRegionManager;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.World;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Flying;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.Villager;
import org.bukkit.entity.WanderingTrader;
import org.bukkit.entity.WaterMob;
import org.bukkit.event.*;
import org.bukkit.event.entity.*;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.player.*;

public class LobbyProtectionListener implements Listener {
    private final FactionsPlugin plugin;

    public LobbyProtectionListener(FactionsPlugin plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        startSpawnMobPurge();
    }

    private boolean isLobby(Player p){
        WarzoneRegionManager region = plugin.getWarzoneRegionManager();
        return region != null && region.isLobbySpawnArea(p.getLocation());
    }

    /* Auto-Fly ein/aus beim Betreten/Verlassen der Lobby-Welt */
    @EventHandler public void onJoin(PlayerJoinEvent e){ toggleFly(e.getPlayer()); }
    @EventHandler public void onWorldChange(PlayerChangedWorldEvent e){ toggleFly(e.getPlayer()); }
    @EventHandler public void onMove(PlayerMoveEvent e){
        if (e.getTo() == null) return;
        if (e.getFrom().getBlockX() == e.getTo().getBlockX()
                && e.getFrom().getBlockY() == e.getTo().getBlockY()
                && e.getFrom().getBlockZ() == e.getTo().getBlockZ()) return;
        toggleFly(e.getPlayer());
    }
    private void toggleFly(Player p){
        boolean should = isLobby(p);
        boolean manualFly = canUseLobbyFly(p);

        if (p.getGameMode() == GameMode.CREATIVE || p.getGameMode() == GameMode.SPECTATOR) {
            p.setAllowFlight(true);
            return;
        }

        if (should && manualFly) {
            p.setAllowFlight(true);
            return;
        }

        if (should) return;

        if (plugin.canFlyInOwnFaction(p)) {
            p.setAllowFlight(true);
            return;
        }

        p.setAllowFlight(false);
        if (p.getGameMode() == GameMode.SURVIVAL || p.getGameMode() == GameMode.ADVENTURE) {
            p.setFlying(false);
        }
    }

    private boolean canUseLobbyFly(Player p) {
        return p.isOp()
                || p.hasPermission("vetro.fly")
                || p.hasPermission("vetro.lobby.fly")
                || p.hasPermission("vetroforge.lobby.fly")
                || p.hasPermission("vetroforge.admin");
    }

    /* Schutz */
    @EventHandler(ignoreCancelled = true) public void onBreak(BlockBreakEvent e){
        if (isLobby(e.getPlayer()) && !canBuildInLobby(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onPlace(BlockPlaceEvent e){
        if (isLobby(e.getPlayer()) && !canBuildInLobby(e.getPlayer())) e.setCancelled(true);
    }

    private boolean canBuildInLobby(Player p) {
        return p.hasPermission("vetroforge.lobby.build")
                || com.vetroforge.services.BuildModeService.hasAccess(plugin, p, com.vetroforge.services.BuildModeService.Area.SPAWN);
    }
    @EventHandler(ignoreCancelled = true) public void onDrop(PlayerDropItemEvent e){
        if (isScouting(e.getPlayer())) e.setCancelled(true);
    }

    private boolean isScouting(Player p) {
        try {
            return plugin.getRaidSessionService() != null && plugin.getRaidSessionService().isScouting(p);
        } catch (Throwable ignored) {
            return false;
        }
    }
    @EventHandler(ignoreCancelled = true) public void onPickup(PlayerAttemptPickupItemEvent e){
        if (isLobby(e.getPlayer())) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onDamage(EntityDamageEvent e){
        if (e.getEntity() instanceof Player p && isLobby(p)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onHunger(FoodLevelChangeEvent e){
        if (e.getEntity() instanceof Player p && isLobby(p)) e.setCancelled(true);
    }
    @EventHandler(ignoreCancelled = true) public void onPVP(EntityDamageByEntityEvent e){
        if (e.getEntity() instanceof Player && e.getDamager() instanceof Player){
            Player v=(Player)e.getEntity(), d=(Player)e.getDamager();
            if (isLobby(v) || isLobby(d)) e.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent e) {
        WarzoneRegionManager region = plugin.getWarzoneRegionManager();
        if (region != null
                && e.getEntity() instanceof LivingEntity living
                && region.isLobbySpawnArea(e.getLocation())
                && shouldRemoveFromSpawn(living)) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, living::remove);
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntitySpawn(EntitySpawnEvent e) {
        WarzoneRegionManager region = plugin.getWarzoneRegionManager();
        if (region != null
                && e.getEntity() instanceof LivingEntity living
                && region.isLobbySpawnArea(e.getLocation())
                && shouldRemoveFromSpawn(living)) {
            e.setCancelled(true);
            Bukkit.getScheduler().runTask(plugin, living::remove);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onChunkLoad(ChunkLoadEvent e) {
        Bukkit.getScheduler().runTask(plugin, () -> {
            WarzoneRegionManager region = plugin.getWarzoneRegionManager();
            if (region == null) return;

            for (var entity : e.getChunk().getEntities()) {
                if (entity instanceof LivingEntity living
                        && region.isLobbySpawnArea(living.getLocation())
                        && shouldRemoveFromSpawn(living)) {
                    living.remove();
                }
            }
        });
    }

    private void startSpawnMobPurge() {
        Bukkit.getScheduler().runTaskTimer(plugin, this::purgeSpawnMobs, 40L, 100L);
    }

    private void purgeSpawnMobs() {
        WarzoneRegionManager region = plugin.getWarzoneRegionManager();
        if (region == null) return;

        String worldName = region.isSpawnSafeMode()
                ? region.getWorldName()
                : plugin.getConfig().getString("lobby.world", "world");
        World world = Bukkit.getWorld(worldName);
        if (world == null) return;

        for (LivingEntity entity : world.getLivingEntities()) {
            if (region.isLobbySpawnArea(entity.getLocation()) && shouldRemoveFromSpawn(entity)) {
                entity.remove();
            }
        }
    }

    private boolean shouldRemoveFromSpawn(LivingEntity entity) {
        if (entity instanceof Player) return false;
        if (entity instanceof ArmorStand) return false;
        if (entity instanceof Villager || entity instanceof WanderingTrader) return false;

        if (entity instanceof Monster
                || entity instanceof Animals
                || entity instanceof Slime
                || entity instanceof Ambient
                || entity instanceof WaterMob
                || entity instanceof Flying) {
            return true;
        }

        // Spawn bleibt eine echte Safe-Zone: alles Lebendige, was nicht bewusst
        // als NPC/Display gebraucht wird, wird hier entfernt.
        return true;
    }
}
