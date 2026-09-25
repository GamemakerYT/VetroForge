package com.vetroforge.commerce;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.NamespacedKey;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Villager;
import org.bukkit.event.Cancellable;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerInteractAtEntityEvent;
import org.bukkit.event.player.PlayerInteractEntityEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.persistence.PersistentDataType;

import java.util.Locale;
import java.util.UUID;

public final class CommerceNpcService implements Listener {
    public enum Kind {
        SHOP("shop", "§c§lVetroForge §fShop", Villager.Profession.LIBRARIAN, Villager.Type.PLAINS),
        AUCTION("auction", "§6§lAuktionshaus", Villager.Profession.CARTOGRAPHER, Villager.Type.TAIGA);

        final String id;
        final String defaultName;
        final Villager.Profession profession;
        final Villager.Type type;

        Kind(String id, String defaultName, Villager.Profession profession, Villager.Type type) {
            this.id = id;
            this.defaultName = defaultName;
            this.profession = profession;
            this.type = type;
        }
    }

    private static final String ROOT = "commerce.npcs";
    private final FactionsPlugin plugin;
    private final Kind kind;
    private final NamespacedKey typeKey;

    public CommerceNpcService(FactionsPlugin plugin, Kind kind) {
        this.plugin = plugin;
        this.kind = kind;
        this.typeKey = new NamespacedKey(plugin, "commerce_npc_type");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void spawnConfigured() {
        Location location = location();
        if (location == null) return;
        UUID uuid = uuid();
        if (uuid != null && entity(uuid) != null) return;
        spawnAt(location);
    }

    public boolean set(Location location, String rawName) {
        if (location == null || location.getWorld() == null) return false;
        remove();
        return spawnAt(location, rawName) != null;
    }

    public boolean remove() {
        boolean removed = false;
        UUID uuid = uuid();
        if (uuid != null) {
            Entity entity = entity(uuid);
            if (entity != null) {
                entity.remove();
                removed = true;
            }
        }
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isThisNpc(entity)) {
                    entity.remove();
                    removed = true;
                }
            }
        }
        plugin.getConfig().set(path(), null);
        plugin.saveConfig();
        return removed;
    }

    public String info() {
        Location location = location();
        if (location == null || location.getWorld() == null) return ChatColor.GRAY + "Kein NPC gesetzt.";
        return ChatColor.GOLD + displayKind() + ChatColor.DARK_GRAY + " » "
                + ChatColor.WHITE + location.getWorld().getName()
                + ChatColor.GRAY + " x=" + ChatColor.WHITE + location.getBlockX()
                + ChatColor.GRAY + " y=" + ChatColor.WHITE + location.getBlockY()
                + ChatColor.GRAY + " z=" + ChatColor.WHITE + location.getBlockZ();
    }

    public boolean isThisNpc(Entity entity) {
        if (entity == null) return false;
        String tag = entity.getPersistentDataContainer().get(typeKey, PersistentDataType.STRING);
        return kind.id.equalsIgnoreCase(tag) || entity.getScoreboardTags().contains("vetro_commerce_" + kind.id);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClick(PlayerInteractAtEntityEvent event) {
        handle(event.getRightClicked(), event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onRightClickCompat(PlayerInteractEntityEvent event) {
        handle(event.getRightClicked(), event.getPlayer(), event);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (isThisNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (isThisNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (isThisNpc(entity)) {
                try { entity.setCustomNameVisible(true); } catch (Throwable ignored) {}
            }
        }
    }

    private void handle(Entity entity, Player player, Cancellable event) {
        if (!isThisNpc(entity)) return;
        event.setCancelled(true);
        if (kind == Kind.SHOP) {
            ShopGui.openMain(plugin, player);
        } else if (plugin.getAuctionHouseManager() != null) {
            plugin.getAuctionHouseManager().open(player);
        } else {
            player.sendMessage(ChatColor.RED + "Auktionshaus ist nicht geladen.");
        }
    }

    private Villager spawnAt(Location location) {
        return spawnAt(location, plugin.getConfig().getString(path() + ".name", kind.defaultName));
    }

    private Villager spawnAt(Location raw, String rawName) {
        Location location = center(raw);
        Villager villager = location.getWorld().spawn(location, Villager.class, entity -> {
            entity.setAI(false);
            entity.setInvulnerable(true);
            entity.setSilent(true);
            entity.setCanPickupItems(false);
            entity.setCollidable(false);
            try { entity.setGravity(false); } catch (Throwable ignored) {}
            entity.setRemoveWhenFarAway(false);
            try { entity.setProfession(kind.profession); } catch (Throwable ignored) {}
            try { entity.setVillagerType(kind.type); } catch (Throwable ignored) {}
            try { entity.setVillagerLevel(5); } catch (Throwable ignored) {}
            try { entity.setAdult(); } catch (Throwable ignored) {}
            try { entity.setGlowing(true); } catch (Throwable ignored) {}
            try { entity.setPersistent(true); } catch (Throwable ignored) {}
            if (entity instanceof LivingEntity living) living.setCustomNameVisible(true);
            entity.setCustomName(formatName(rawName));
            entity.setCustomNameVisible(true);
            tag(entity);
        });
        save(villager.getUniqueId(), raw, rawName);
        return villager;
    }

    private void tag(Entity entity) {
        entity.addScoreboardTag("vetro_commerce_" + kind.id);
        entity.getPersistentDataContainer().set(typeKey, PersistentDataType.STRING, kind.id);
    }

    private void save(UUID uuid, Location location, String rawName) {
        plugin.getConfig().set(path() + ".uuid", uuid.toString());
        plugin.getConfig().set(path() + ".world", location.getWorld().getName());
        plugin.getConfig().set(path() + ".x", location.getX());
        plugin.getConfig().set(path() + ".y", location.getY());
        plugin.getConfig().set(path() + ".z", location.getZ());
        plugin.getConfig().set(path() + ".yaw", location.getYaw());
        plugin.getConfig().set(path() + ".pitch", location.getPitch());
        plugin.getConfig().set(path() + ".name", rawName == null || rawName.isBlank() ? kind.defaultName : rawName);
        plugin.saveConfig();
    }

    private Location location() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(path());
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) return null;
        return new Location(world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch"));
    }

    private UUID uuid() {
        String raw = plugin.getConfig().getString(path() + ".uuid");
        if (raw == null || raw.isBlank()) return null;
        try { return UUID.fromString(raw); } catch (IllegalArgumentException ignored) { return null; }
    }

    private Entity entity(UUID uuid) {
        for (World world : Bukkit.getWorlds()) {
            Entity entity = world.getEntity(uuid);
            if (entity != null && !entity.isDead()) return entity;
        }
        return null;
    }

    private String path() {
        return ROOT + "." + kind.id;
    }

    private String displayKind() {
        return kind.id.substring(0, 1).toUpperCase(Locale.ROOT) + kind.id.substring(1) + " NPC";
    }

    private String formatName(String raw) {
        String value = raw == null || raw.isBlank() ? kind.defaultName : raw;
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private Location center(Location location) {
        return new Location(location.getWorld(),
                Math.floor(location.getX()) + 0.5,
                location.getY(),
                Math.floor(location.getZ()) + 0.5,
                location.getYaw(),
                location.getPitch());
    }
}
