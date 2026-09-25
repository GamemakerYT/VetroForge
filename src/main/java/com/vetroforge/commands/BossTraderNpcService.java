package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.EulerAngle;

import java.util.UUID;

public final class BossTraderNpcService implements Listener {
    private static final String TAG = "vetro_boss_trader_npc";
    private static final String MINI_BOSS_TAG = TAG + "_mini_boss";
    private static final String RELIC_TAG = TAG + "_relic";
    private static final String PATH = "warzone.boss.trader.npc";

    private final FactionsPlugin plugin;
    private final BossTraderCommand command;
    private final NamespacedKey tagKey;
    private final NamespacedKey decorKey;
    private BukkitTask auraTask;

    public BossTraderNpcService(FactionsPlugin plugin, BossTraderCommand command) {
        this.plugin = plugin;
        this.command = command;
        this.tagKey = new NamespacedKey(plugin, TAG);
        this.decorKey = new NamespacedKey(plugin, "vetro_boss_trader_decor");
        startAuraTask();
    }

    public void spawnConfigured() {
        Location location = location();
        if (location == null) return;
        UUID uuid = uuid();
        if (uuid != null && entity(uuid) != null) {
            if (!hasDecor()) spawnDecor(location);
            return;
        }
        // Ein bereits gesetzter NPC "fehlt" nur, wenn sein Chunk geladen ist und er dort
        // wirklich nicht existiert. Ist der Chunk einfach nur entladen, existiert der
        // (persistente) NPC trotzdem weiter -- sonst würde hier eine Dublette gespawnt.
        if (uuid != null && location.getWorld() != null
                && !location.getWorld().isChunkLoaded(location.getBlockX() >> 4, location.getBlockZ() >> 4)) {
            return;
        }
        spawnAt(location, plugin.getConfig().getString(PATH + ".name", "&4&lDisparity Trader"));
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
                if (isNpc(entity) || isDecor(entity)) {
                    entity.remove();
                    removed = true;
                }
            }
        }
        plugin.getConfig().set(PATH, null);
        plugin.saveConfig();
        return removed;
    }

    public String info() {
        Location location = location();
        if (location == null || location.getWorld() == null) return ChatColor.GRAY + "Kein Boss-Trader gesetzt.";
        return ChatColor.DARK_RED + "Boss-Trader " + ChatColor.DARK_GRAY + "» "
                + ChatColor.WHITE + location.getWorld().getName()
                + ChatColor.GRAY + " x=" + ChatColor.WHITE + location.getBlockX()
                + ChatColor.GRAY + " y=" + ChatColor.WHITE + location.getBlockY()
                + ChatColor.GRAY + " z=" + ChatColor.WHITE + location.getBlockZ();
    }

    public boolean setSkin(String rawSkin) {
        String skin = rawSkin == null || rawSkin.isBlank() ? "WITHER_SKELETON_SKULL" : rawSkin.trim();
        plugin.getConfig().set(PATH + ".skin", skin);
        plugin.saveConfig();
        Location location = location();
        if (location != null) spawnDecor(location);
        return true;
    }

    public boolean isNpc(Entity entity) {
        if (entity == null) return false;
        if (entity.getScoreboardTags().contains(TAG)) return true;
        Byte value = entity.getPersistentDataContainer().get(tagKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
    }

    private boolean isDecor(Entity entity) {
        if (entity == null) return false;
        if (entity.getScoreboardTags().contains(TAG + "_decor")) return true;
        Byte value = entity.getPersistentDataContainer().get(decorKey, PersistentDataType.BYTE);
        return value != null && value == (byte) 1;
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
        if (isNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamageByEntity(EntityDamageByEntityEvent event) {
        if (isNpc(event.getEntity())) event.setCancelled(true);
    }

    @EventHandler
    public void onChunkLoad(ChunkLoadEvent event) {
        for (Entity entity : event.getChunk().getEntities()) {
            if (isNpc(entity)) {
                try { entity.setCustomNameVisible(true); } catch (Throwable ignored) {}
            }
        }
    }

    private void handle(Entity entity, Player player, Cancellable event) {
        if (!isNpc(entity)) return;
        event.setCancelled(true);
        command.open(player);
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
            try { entity.setProfession(Villager.Profession.CLERIC); } catch (Throwable ignored) {}
            try { entity.setVillagerType(Villager.Type.SWAMP); } catch (Throwable ignored) {}
            try { entity.setVillagerLevel(5); } catch (Throwable ignored) {}
            try { entity.setAdult(); } catch (Throwable ignored) {}
            try { entity.setGlowing(true); } catch (Throwable ignored) {}
            try { entity.setPersistent(true); } catch (Throwable ignored) {}
            if (entity instanceof LivingEntity living) living.setCustomNameVisible(true);
            entity.setCustomName(formatName(rawName));
            entity.setCustomNameVisible(true);
            tag(entity);
        });
        spawnDecor(raw);
        save(villager.getUniqueId(), raw, rawName);
        return villager;
    }

    private void tag(Entity entity) {
        entity.addScoreboardTag(TAG);
        entity.getPersistentDataContainer().set(tagKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void tagDecor(Entity entity) {
        entity.addScoreboardTag(TAG + "_decor");
        entity.getPersistentDataContainer().set(decorKey, PersistentDataType.BYTE, (byte) 1);
    }

    private void spawnDecor(Location raw) {
        if (raw == null || raw.getWorld() == null) return;
        removeDecorOnly();
        Location base = center(raw);
        spawnHolo(base.clone().add(0, 2.45, 0), "&4&lDisparity Trader");
        spawnHolo(base.clone().add(0, 2.15, 0), "&7Boss-Fragmente eintauschen");
        spawnHolo(base.clone().add(0, 1.88, 0), "&c30 Fragmente &8= &fBoss Reward");
        spawnRelic(base.clone().add(0.78, 0.15, 0));
        spawnMiniBoss(base.clone().add(-0.72, 0.0, 0.12));
    }

    private void removeDecorOnly() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isDecor(entity)) entity.remove();
            }
        }
    }

    private boolean hasDecor() {
        for (World world : Bukkit.getWorlds()) {
            for (Entity entity : world.getEntities()) {
                if (isDecor(entity)) return true;
            }
        }
        return false;
    }

    private void spawnHolo(Location location, String text) {
        if (location.getWorld() == null) return;
        location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setMarker(true);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setCollidable(false);
            stand.setCustomNameVisible(true);
            stand.customName(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
            try { stand.setPersistent(true); } catch (Throwable ignored) {}
            tagDecor(stand);
        });
    }

    private void spawnRelic(Location location) {
        if (location.getWorld() == null) return;
        location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setInvisible(true);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setMarker(false);
            stand.setCollidable(false);
            try { stand.setPersistent(true); } catch (Throwable ignored) {}
            try { stand.setItem(EquipmentSlot.HAND, new ItemStack(Material.NETHER_STAR)); } catch (Throwable ignored) {}
            try { stand.setRightArmPose(new org.bukkit.util.EulerAngle(Math.toRadians(292), Math.toRadians(0), Math.toRadians(26))); } catch (Throwable ignored) {}
            tagDecor(stand);
            stand.addScoreboardTag(RELIC_TAG);
        });
    }

    private void spawnMiniBoss(Location location) {
        if (location.getWorld() == null) return;
        location.setYaw(location.getYaw() + 25.0F);
        location.getWorld().spawn(location, ArmorStand.class, stand -> {
            stand.setVisible(true);
            stand.setGravity(false);
            stand.setSmall(true);
            stand.setBasePlate(false);
            stand.setArms(true);
            stand.setMarker(false);
            stand.setCollidable(false);
            try { stand.setPersistent(true); } catch (Throwable ignored) {}
            try { stand.setHeadPose(new EulerAngle(Math.toRadians(348), Math.toRadians(0), Math.toRadians(0))); } catch (Throwable ignored) {}
            try { stand.setRightArmPose(new EulerAngle(Math.toRadians(292), Math.toRadians(0), Math.toRadians(18))); } catch (Throwable ignored) {}
            try { stand.setLeftArmPose(new EulerAngle(Math.toRadians(315), Math.toRadians(0), Math.toRadians(335))); } catch (Throwable ignored) {}
            try {
                stand.getEquipment().setHelmet(skinItem());
                stand.getEquipment().setChestplate(new ItemStack(Material.NETHERITE_CHESTPLATE));
                stand.getEquipment().setLeggings(new ItemStack(Material.NETHERITE_LEGGINGS));
                stand.getEquipment().setBoots(new ItemStack(Material.NETHERITE_BOOTS));
                stand.getEquipment().setItemInMainHand(new ItemStack(Material.NETHERITE_AXE));
            } catch (Throwable ignored) {}
            tagDecor(stand);
            stand.addScoreboardTag(MINI_BOSS_TAG);
        });
    }

    private ItemStack skinItem() {
        String raw = plugin.getConfig().getString(PATH + ".skin", "WITHER_SKELETON_SKULL");
        Material material = Material.matchMaterial(raw == null ? "" : raw.toUpperCase(java.util.Locale.ROOT));
        if (material != null && material.isItem()) return new ItemStack(material);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        if (raw != null && !raw.isBlank()) {
            head.editMeta(meta -> {
                if (meta instanceof SkullMeta skullMeta) {
                    OfflinePlayer owner = Bukkit.getOfflinePlayer(raw);
                    skullMeta.setOwningPlayer(owner);
                }
            });
        }
        return head;
    }

    private void startAuraTask() {
        if (auraTask != null) return;
        auraTask = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            Location location = location();
            if (location == null || location.getWorld() == null) return;
            if (uuid() != null && entity(uuid()) == null) return;
            Location npcCenter = center(location).add(0, 1.05, 0);
            World world = npcCenter.getWorld();
            if (world == null) return;
            double time = (System.currentTimeMillis() % 4000L) / 4000.0D * Math.PI * 2.0D;
            for (int i = 0; i < 8; i++) {
                double angle = time + (Math.PI * 2.0D / 8.0D) * i;
                Location point = npcCenter.clone().add(Math.cos(angle) * 0.72D, Math.sin(angle * 1.5D) * 0.12D, Math.sin(angle) * 0.72D);
                world.spawnParticle(Particle.DUST, point, 1, 0, 0, 0, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(170, 15, 25), 1.2F));
            }
            animateMiniBoss(world, time);
        }, 20L, 10L);
    }

    private void animateMiniBoss(World world, double time) {
        for (Entity entity : world.getEntities()) {
            if (!(entity instanceof ArmorStand stand)) continue;
            if (!stand.getScoreboardTags().contains(MINI_BOSS_TAG) && !stand.getScoreboardTags().contains(RELIC_TAG)) continue;

            Location location = stand.getLocation().clone().add(0, 0.85, 0);
            if (stand.getScoreboardTags().contains(MINI_BOSS_TAG)) {
                double swing = Math.sin(time * 2.0D) * 13.0D;
                double offhand = Math.cos(time * 2.0D) * 8.0D;
                try { stand.setRightArmPose(new EulerAngle(Math.toRadians(292 + swing), Math.toRadians(0), Math.toRadians(18))); } catch (Throwable ignored) {}
                try { stand.setLeftArmPose(new EulerAngle(Math.toRadians(315 + offhand), Math.toRadians(0), Math.toRadians(335))); } catch (Throwable ignored) {}
                try { stand.setHeadPose(new EulerAngle(Math.toRadians(348 + Math.sin(time) * 4.0D), Math.toRadians(Math.sin(time * 0.7D) * 0.08D), 0)); } catch (Throwable ignored) {}
                world.spawnParticle(Particle.DUST, location, 2, 0.18, 0.25, 0.18, 0,
                        new Particle.DustOptions(org.bukkit.Color.fromRGB(120, 0, 20), 1.0F));
                world.spawnParticle(Particle.SMOKE, location.clone().add(0, 0.2, 0), 1, 0.10, 0.12, 0.10, 0.0);
            } else {
                try { stand.setRightArmPose(new EulerAngle(Math.toRadians(292 + Math.sin(time * 2.5D) * 7.0D), 0, Math.toRadians(26))); } catch (Throwable ignored) {}
                world.spawnParticle(Particle.ENCHANT, location, 2, 0.10, 0.10, 0.10, 0.0);
            }
        }
    }

    private void save(UUID uuid, Location location, String rawName) {
        plugin.getConfig().set(PATH + ".uuid", uuid.toString());
        plugin.getConfig().set(PATH + ".world", location.getWorld().getName());
        plugin.getConfig().set(PATH + ".x", location.getX());
        plugin.getConfig().set(PATH + ".y", location.getY());
        plugin.getConfig().set(PATH + ".z", location.getZ());
        plugin.getConfig().set(PATH + ".yaw", location.getYaw());
        plugin.getConfig().set(PATH + ".pitch", location.getPitch());
        plugin.getConfig().set(PATH + ".name", rawName == null || rawName.isBlank() ? "&4&lDisparity Trader" : rawName);
        plugin.saveConfig();
    }

    private Location location() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection(PATH);
        if (section == null) return null;
        World world = Bukkit.getWorld(section.getString("world", "world"));
        if (world == null) return null;
        return new Location(
                world,
                section.getDouble("x"),
                section.getDouble("y"),
                section.getDouble("z"),
                (float) section.getDouble("yaw"),
                (float) section.getDouble("pitch")
        );
    }

    private UUID uuid() {
        String raw = plugin.getConfig().getString(PATH + ".uuid");
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

    private String formatName(String raw) {
        String value = raw == null || raw.isBlank() ? "&4&lDisparity Trader" : raw;
        return ChatColor.translateAlternateColorCodes('&', value);
    }

    private Location center(Location location) {
        return new Location(
                location.getWorld(),
                Math.floor(location.getX()) + 0.5,
                location.getY(),
                Math.floor(location.getZ()) + 0.5,
                location.getYaw(),
                location.getPitch()
        );
    }
}
