package com.vetroforge.warzone;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.commands.BossTraderCommand;
import com.vetroforge.skills.SkillType;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Ambient;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Flying;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Slime;
import org.bukkit.entity.WaterMob;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

public final class WarzoneMobService implements Listener {

    private final FactionsPlugin plugin;
    private final WarzoneRegionManager warzone;
    private final NamespacedKey mobLevel;
    private final NamespacedKey boss;
    private final NamespacedKey bossPhase;
    private final NamespacedKey bossType;
    private final NamespacedKey bossAnchor;
    private BukkitTask task;
    private int ticks;
    private boolean spawningManaged;

    public WarzoneMobService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.warzone = plugin.getWarzoneRegionManager();
        this.mobLevel = new NamespacedKey(plugin, "warzone_mob_level");
        this.boss = new NamespacedKey(plugin, "warzone_boss");
        this.bossPhase = new NamespacedKey(plugin, "warzone_boss_phase");
        this.bossType = new NamespacedKey(plugin, "warzone_boss_type");
        this.bossAnchor = new NamespacedKey(plugin, "warzone_boss_anchor");
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void start() {
        stop();
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 40L, 40L);
    }

    public void stop() {
        if (task != null) task.cancel();
        task = null;
    }

    private void tick() {
        ticks++;
        purgeVanillaMobs();

        List<Player> players = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (warzone != null && warzone.isWarzone(player.getLocation())) players.add(player);
        }
        if (players.isEmpty()) return;

        for (LivingEntity entity : managed()) {
            updateName(entity);
            if (isBoss(entity)) {
                enforceBossAnchor(entity);
                applyBossAura(entity);
            }
        }

        int spawnInterval = Math.max(2, plugin.getConfig().getInt("warzone.mobs.spawn-every-seconds", 12));
        if (ticks % Math.max(1, spawnInterval / 2) == 0) {
            int max = Math.max(1, plugin.getConfig().getInt("warzone.mobs.max-active", 10));
            int need = Math.min(1, max - managed().size());
            for (int i = 0; i < need; i++) spawnMob(players.get(ThreadLocalRandom.current().nextInt(players.size())));
        }

        int bossInterval = Math.max(60, plugin.getConfig().getInt("warzone.mobs.boss-every-seconds", 300));
        if (ticks % Math.max(1, bossInterval / 2) == 0 && managed().stream().noneMatch(this::isBoss)) {
            spawnBoss(players.get(ThreadLocalRandom.current().nextInt(players.size())));
        }
    }

    private void spawnMob(Player player) {
        Location spawn = spawnNear(player);
        int level = disparityLevel(spawn);
        EntityType type = pickMobType(level);
        LivingEntity entity = spawnManaged(spawn, type);
        configure(entity, level, 8.0D + level * 4.0D, false);
    }

    /**
     * Mob-Varianten eskalieren mit der Disparity, statt immer nur Zombie/Skeleton zu sein --
     * die Pools sind kumulativ (hoehere Stufen behalten die einfacheren Mobs als Fuellmaterial).
     * Die Tier-Grenzen (tierLow/tierMedium/tierHigh) sind dieselben wie in WarzoneBossBarManager,
     * damit Optik und Mob-Staerke konsistent zusammen eskalieren.
     */
    private EntityType pickMobType(int level) {
        int tierLow = plugin.getConfig().getInt("warzone.disparity.tierLow", 16);
        int tierMedium = plugin.getConfig().getInt("warzone.disparity.tierMedium", 41);
        int tierHigh = plugin.getConfig().getInt("warzone.disparity.tierHigh", 71);

        List<EntityType> pool = new ArrayList<>(List.of(EntityType.ZOMBIE, EntityType.SKELETON));
        if (level >= tierLow) pool.addAll(List.of(EntityType.HUSK, EntityType.STRAY, EntityType.SPIDER));
        if (level >= tierMedium) pool.addAll(List.of(EntityType.VINDICATOR, EntityType.PILLAGER));
        if (level >= tierHigh) pool.addAll(List.of(EntityType.VEX, EntityType.EVOKER));

        return pool.get(ThreadLocalRandom.current().nextInt(pool.size()));
    }

    private void spawnBoss(Player player) {
        List<BossType> configuredTypes = new ArrayList<>();
        for (BossType type : BossType.values()) {
            if (bossSpawn(type.id) != null) configuredTypes.add(type);
        }
        if (configuredTypes.isEmpty()) return;

        BossType type = configuredTypes.get(ThreadLocalRandom.current().nextInt(configuredTypes.size()));
        spawnBoss(type.id, bossSpawn(type.id));
    }

    public LivingEntity spawnBoss(String rawType, Location location) {
        BossType type = BossType.from(rawType);
        if (type == null || location == null || location.getWorld() == null) return null;
        if (warzone != null && !warzone.isWarzone(location)) return null;
        if (activeBosses().stream().anyMatch(entity -> bossType(entity) == type)) return null;
        int level = Math.max(4, disparityLevel(location) + 2);
        LivingEntity entity = spawnManaged(location, type.entityType);
        entity.getPersistentDataContainer().set(bossType, PersistentDataType.STRING, type.id);
        entity.getPersistentDataContainer().set(bossAnchor, PersistentDataType.STRING, serialize(location));
        configure(entity, level, type.baseHealth + level * 20.0D, true);
        Bukkit.broadcastMessage("§4Warzone Boss §8» " + type.color + type.display + " §7ist erschienen!");
        return entity;
    }

    public LivingEntity spawnBossAtConfiguredSpawn(String rawType) {
        BossType type = BossType.from(rawType);
        if (type == null) return null;
        return spawnBoss(type.id, bossSpawn(type.id));
    }

    public boolean setBossSpawn(String rawType, Location location) {
        BossType type = BossType.from(rawType);
        if (type == null || location == null || location.getWorld() == null) return false;
        if (warzone != null && !warzone.isWarzone(location)) return false;
        String path = "warzone.boss-spawns." + type.id;
        plugin.getConfig().set(path + ".world", location.getWorld().getName());
        plugin.getConfig().set(path + ".x", location.getX());
        plugin.getConfig().set(path + ".y", location.getY());
        plugin.getConfig().set(path + ".z", location.getZ());
        plugin.getConfig().set(path + ".yaw", location.getYaw());
        plugin.getConfig().set(path + ".pitch", location.getPitch());
        plugin.saveConfig();
        return true;
    }

    public List<String> bossSpawnLines() {
        List<String> lines = new ArrayList<>();
        for (BossType type : BossType.values()) {
            Location location = bossSpawn(type.id);
            if (location == null || location.getWorld() == null) {
                lines.add("§8• " + type.color + type.display + " §8» §cnicht gesetzt");
                continue;
            }
            lines.add("§8• " + type.color + type.display + " §8» §7" + location.getWorld().getName()
                    + " §f" + location.getBlockX() + " " + location.getBlockY() + " " + location.getBlockZ());
        }
        return lines;
    }

    private Location bossSpawn(String rawType) {
        BossType type = BossType.from(rawType);
        if (type == null) return null;
        String path = "warzone.boss-spawns." + type.id;
        String worldName = plugin.getConfig().getString(path + ".world");
        if (worldName == null) return null;
        var world = Bukkit.getWorld(worldName);
        if (world == null) return null;
        return new Location(
                world,
                plugin.getConfig().getDouble(path + ".x"),
                plugin.getConfig().getDouble(path + ".y"),
                plugin.getConfig().getDouble(path + ".z"),
                (float) plugin.getConfig().getDouble(path + ".yaw"),
                (float) plugin.getConfig().getDouble(path + ".pitch")
        );
    }

    private LivingEntity spawnManaged(Location location, EntityType type) {
        spawningManaged = true;
        try {
            return (LivingEntity) location.getWorld().spawnEntity(location, type);
        } finally {
            spawningManaged = false;
        }
    }

    public List<LivingEntity> activeBosses() {
        return managed().stream().filter(this::isBoss).toList();
    }

    public boolean isKnownBoss(String rawType) {
        return BossType.from(rawType) != null;
    }

    public boolean isValidBossLocation(Location location) {
        return location != null
                && location.getWorld() != null
                && (warzone == null || warzone.isWarzone(location));
    }

    public boolean removeBoss(UUID uuid) {
        LivingEntity entity = activeBosses().stream().filter(current -> current.getUniqueId().equals(uuid)).findFirst().orElse(null);
        if (entity == null) return false;
        entity.remove();
        return true;
    }

    public int removeBosses(String rawType) {
        BossType type = BossType.from(rawType);
        if (type == null) return 0;
        int removed = 0;
        for (LivingEntity entity : activeBosses()) {
            if (bossType(entity) != type) continue;
            entity.remove();
            removed++;
        }
        return removed;
    }

    public int removeAllBosses() {
        int removed = 0;
        for (LivingEntity entity : activeBosses()) {
            entity.remove();
            removed++;
        }
        return removed;
    }

    public boolean removeNearestBoss(Location origin) {
        if (origin == null || origin.getWorld() == null) return false;
        LivingEntity nearest = null;
        double best = Double.MAX_VALUE;
        for (LivingEntity entity : activeBosses()) {
            if (!entity.getWorld().equals(origin.getWorld())) continue;
            double distance = entity.getLocation().distanceSquared(origin);
            if (distance >= best) continue;
            best = distance;
            nearest = entity;
        }
        if (nearest == null) return false;
        nearest.remove();
        return true;
    }

    public String describeBoss(LivingEntity entity) {
        BossType profile = bossType(entity);
        Location location = entity.getLocation();
        return profile.color + profile.display
                + " §8[" + entity.getUniqueId() + "]"
                + " §8» §7" + location.getWorld().getName()
                + " §f" + location.getBlockX()
                + " " + location.getBlockY()
                + " " + location.getBlockZ();
    }

    private void configure(LivingEntity entity, int level, double health, boolean bossMob) {
        entity.getPersistentDataContainer().set(mobLevel, PersistentDataType.INTEGER, level);
        if (bossMob) entity.getPersistentDataContainer().set(boss, PersistentDataType.BYTE, (byte) 1);
        if (bossMob) entity.getPersistentDataContainer().set(bossPhase, PersistentDataType.INTEGER, 1);
        var attribute = entity.getAttribute(Attribute.MAX_HEALTH);
        if (attribute != null) attribute.setBaseValue(health);
        var speed = entity.getAttribute(Attribute.MOVEMENT_SPEED);
        if (speed != null) {
            if (bossMob) {
                speed.setBaseValue(0.0D);
            } else {
                double base = plugin.getConfig().getDouble("warzone.mobs.speed", 0.14D);
                double perLevel = plugin.getConfig().getDouble("warzone.mobs.speedPerLevel", 0.0015D);
                double cap = plugin.getConfig().getDouble("warzone.mobs.speedCap", 0.32D);
                speed.setBaseValue(Math.max(0.01D, Math.min(cap, base + level * perLevel)));
            }
        }
        if (entity instanceof Mob mob) {
            mob.setCanPickupItems(false);
            if (bossMob) {
                mob.setAI(false);
                mob.setTarget(null);
            }
        }
        if (bossMob) {
            try { entity.setCollidable(false); } catch (Throwable ignored) {}
        }
        entity.setHealth(health);
        entity.setCustomNameVisible(true);
        updateName(entity);
    }

    private Location spawnNear(Player player) {
        var random = ThreadLocalRandom.current();
        for (int attempt = 0; attempt < 12; attempt++) {
            Location location = player.getLocation().clone().add(random.nextInt(-12, 13), 0, random.nextInt(-12, 13));
            location.setY(player.getWorld().getHighestBlockYAt(location) + 1.0D);
            if (warzone == null || (warzone.isWarzone(location) && !warzone.isSpawn(location))) {
                return location;
            }
        }
        Location fallback = player.getLocation().clone();
        fallback.setY(player.getWorld().getHighestBlockYAt(fallback) + 1.0D);
        return fallback;
    }

    private int disparityLevel(Location location) {
        if (warzone == null) return 1;
        return warzone.disparityLevel(location);
    }

    private List<LivingEntity> managed() {
        List<LivingEntity> result = new ArrayList<>();
        for (var world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (entity.getPersistentDataContainer().has(mobLevel, PersistentDataType.INTEGER)) result.add(entity);
            }
        }
        return result;
    }

    /**
     * PDC-Tags anderer, unabhaengig verwalteter NPC-Systeme (Crate-NPC "Varyn", Traveler,
     * Boss-Trader) -- allesamt Villager/WanderingTrader und damit selbst als Mob erkannt.
     * Ohne diese Ausnahme wuerde purgeVanillaMobs() jeden dieser NPCs sofort wieder
     * entfernen, sobald er in der Warzone oder im Lobby-Spawn-Bereich steht, weil er nicht
     * das (Warzone-eigene) mobLevel-Tag traegt.
     */
    private static final String[] PROTECTED_NPC_TAGS = {
            "traveler_npc", "vetro_crate_npc", "vetro_boss_trader_npc"
    };

    private boolean isProtectedNpc(LivingEntity entity) {
        for (String tag : PROTECTED_NPC_TAGS) {
            if (entity.getPersistentDataContainer().has(new NamespacedKey(plugin, tag), PersistentDataType.BYTE)) return true;
        }
        return false;
    }

    private void purgeVanillaMobs() {
        if (warzone == null) return;
        for (var world : Bukkit.getWorlds()) {
            for (LivingEntity entity : world.getLivingEntities()) {
                if (!isVanillaSpawnBlockedType(entity)) continue;
                if (entity.getPersistentDataContainer().has(mobLevel, PersistentDataType.INTEGER)) continue;
                if (isProtectedNpc(entity)) continue;
                Location location = entity.getLocation();
                if (warzone.isLobbySpawnArea(location) || warzone.isWarzone(location)) {
                    entity.remove();
                }
            }
        }
    }

    private boolean isVanillaSpawnBlockedType(LivingEntity entity) {
        if (entity instanceof Player) return false;
        return entity instanceof Mob
                || entity instanceof Monster
                || entity instanceof Animals
                || entity instanceof Slime
                || entity instanceof Ambient
                || entity instanceof WaterMob
                || entity instanceof Flying;
    }

    private boolean isBoss(Entity entity) {
        return entity.getPersistentDataContainer().has(boss, PersistentDataType.BYTE);
    }

    private void enforceBossAnchor(LivingEntity entity) {
        String raw = entity.getPersistentDataContainer().get(bossAnchor, PersistentDataType.STRING);
        Location anchor = deserialize(raw);
        if (anchor == null || anchor.getWorld() == null) return;
        if (!entity.getWorld().equals(anchor.getWorld()) || entity.getLocation().distanceSquared(anchor) > 2.25D) {
            entity.teleport(anchor);
        }
        if (entity instanceof Mob mob) {
            mob.setTarget(null);
        }
    }

    private void updateName(LivingEntity entity) {
        Integer level = entity.getPersistentDataContainer().get(mobLevel, PersistentDataType.INTEGER);
        if (level == null) return;
        int health = (int) Math.ceil(Math.max(0.0D, entity.getHealth()));
        int max = entity.getAttribute(Attribute.MAX_HEALTH) == null ? health
                : (int) Math.ceil(entity.getAttribute(Attribute.MAX_HEALTH).getValue());
        BossType bossProfile = isBoss(entity) ? bossType(entity) : null;
        String type = bossProfile == null ? pretty(entity.getType()) : bossProfile.color + bossProfile.display;
        entity.setCustomName("§6Disparity " + level + " §8• " + type + " §8• §c" + health + "§7/§c" + max + " ♥");
    }

    private String pretty(EntityType type) {
        String value = type.name().toLowerCase(java.util.Locale.ROOT);
        return "§f" + Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    /**
     * Skaliert den ausgehenden Schaden verwalteter Warzone-Mobs mit ihrer Disparity-Stufe --
     * ueber den tatsaechlichen Schadenswert statt nur ueber das ATTACK_DAMAGE-Attribut, damit
     * auch Fernkampf (Skeleton/Stray/Pillager) und Zauber (Evoker) mitskalieren, nicht nur Nahkampf.
     * Bosse sind ausgenommen -- ihre Bedrohung kommt bewusst ueber die Aura-Debuffs, nicht ueber
     * direkten Kontaktschaden (sie haben KI/Bewegung deaktiviert).
     */
    @EventHandler(ignoreCancelled = true)
    public void onMobDamageDealt(EntityDamageByEntityEvent event) {
        LivingEntity source = managedSource(event.getDamager());
        if (source == null || isBoss(source)) return;

        Integer level = source.getPersistentDataContainer().get(mobLevel, PersistentDataType.INTEGER);
        if (level == null) return;

        double perLevel = plugin.getConfig().getDouble("warzone.mobs.damagePerLevel", 0.06D);
        double cap = plugin.getConfig().getDouble("warzone.mobs.damageMultiplierCap", 3.0D);
        double multiplier = 1.0D + Math.min(cap, level * perLevel);
        event.setDamage(event.getDamage() * multiplier);
    }

    /** Findet den verwalteten Warzone-Mob hinter einem Schaden-Verursacher (direkt oder per Projektil). */
    private LivingEntity managedSource(Entity damager) {
        if (damager instanceof LivingEntity living && living.getPersistentDataContainer().has(mobLevel, PersistentDataType.INTEGER)) {
            return living;
        }
        if (damager instanceof Projectile projectile && projectile.getShooter() instanceof LivingEntity living
                && living.getPersistentDataContainer().has(mobLevel, PersistentDataType.INTEGER)) {
            return living;
        }
        return null;
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageEvent event) {
        if (!(event.getEntity() instanceof LivingEntity living)) return;
        if (!living.getPersistentDataContainer().has(mobLevel, PersistentDataType.INTEGER)) return;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (living.isValid() && !living.isDead()) {
                updateName(living);
                updateBossPhase(living);
            }
        });
    }

    @EventHandler
    public void onDeath(EntityDeathEvent event) {
        if (!event.getEntity().getPersistentDataContainer().has(mobLevel, PersistentDataType.INTEGER)) return;
        event.getDrops().clear();
        event.setDroppedExp(0);
        Player killer = event.getEntity().getKiller();
        if (killer != null && plugin.getLevelProgressionService() != null) {
            int level = event.getEntity().getPersistentDataContainer().getOrDefault(mobLevel, PersistentDataType.INTEGER, 1);
            double maxHealth = event.getEntity().getAttribute(Attribute.MAX_HEALTH) == null
                    ? event.getEntity().getHealth()
                    : event.getEntity().getAttribute(Attribute.MAX_HEALTH).getValue();
            int xp = Math.max(1, (int) Math.round(level * 2.0D + maxHealth / 10.0D));
            plugin.getLevelProgressionService().addXp(killer, xp, "Warzone-Mob Disparity " + level);
            int coins = Math.max(1, plugin.getConfig().getInt("warzone.mobs.killCoinsBase", 3)
                    + level * plugin.getConfig().getInt("warzone.mobs.killCoinsPerDisparity", 2));
            if (plugin.getCoinsManager() != null) {
                coins = plugin.getBoosterManager().applyCoinBoost(killer.getUniqueId(), coins);
                plugin.getCoinsManager().addCoins(killer.getUniqueId(), coins);
            }
            if (plugin.getSkillManager() != null) {
                int warriorXp = Math.max(3, plugin.getConfig().getInt("skills.sources.warzoneMobWarriorXp", 8) + level);
                int raiderXp = Math.max(2, plugin.getConfig().getInt("skills.sources.warzoneMobRaiderXp", 4) + level / 2);
                plugin.getSkillManager().addXp(killer, SkillType.WARRIOR, warriorXp);
                plugin.getSkillManager().addXp(killer, SkillType.RAIDER, raiderXp);
                if (isBoss(event.getEntity())) {
                    plugin.getSkillManager().addXp(killer, SkillType.WARRIOR,
                            Math.max(25, plugin.getConfig().getInt("skills.sources.warzoneBossWarriorXp", 60)));
                    plugin.getSkillManager().addXp(killer, SkillType.RAIDER,
                            Math.max(20, plugin.getConfig().getInt("skills.sources.warzoneBossRaiderXp", 45)));
                }
            }
            killer.sendActionBar("§a+" + xp + " XP §8• §6+" + coins + " Coins §8• §7Warzone-Mob");
        }
        if (killer != null && plugin.getItemProgressionService() != null) {
            plugin.getItemProgressionService().recordBestiaryKill(
                    killer,
                    isBoss(event.getEntity()) ? "boss_" + bossType(event.getEntity()).id : event.getEntityType().name(),
                    isBoss(event.getEntity())
            );
        }
        if (isBoss(event.getEntity())) {
            if (plugin.getEventManager() != null) {
                plugin.getEventManager().addProgressIfActive("boss_slayer", 1);
            }
            int fragments = Math.max(1, plugin.getConfig().getInt("warzone.boss.fragmentDropAmount", 1));
            event.getDrops().add(BossTraderCommand.createFragment(plugin, fragments));
            if (plugin.getKeyCrateManager() != null) {
                event.getDrops().add(plugin.getKeyCrateManager().createKeyItem("boss", 1));
            }
            BossType profile = bossType(event.getEntity());
            Bukkit.broadcastMessage("§4Warzone Boss §8» §a" + profile.display
                    + " wurde besiegt. §7Boss-Key und §c" + fragments + " Fragment(e) §7gedroppt.");
        }
    }

    private void updateBossPhase(LivingEntity bossEntity) {
        if (!isBoss(bossEntity)) return;
        var maxAttribute = bossEntity.getAttribute(Attribute.MAX_HEALTH);
        if (maxAttribute == null || maxAttribute.getValue() <= 0.0D) return;
        double ratio = bossEntity.getHealth() / maxAttribute.getValue();
        int phase = ratio <= 0.33D ? 3 : ratio <= 0.66D ? 2 : 1;
        int previous = bossEntity.getPersistentDataContainer().getOrDefault(bossPhase, PersistentDataType.INTEGER, 1);
        if (phase <= previous) return;
        bossEntity.getPersistentDataContainer().set(bossPhase, PersistentDataType.INTEGER, phase);
        if (phase == 2) {
            spawnSummon(bossEntity, EntityType.ZOMBIE);
            spawnSummon(bossEntity, EntityType.SKELETON);
        } else {
            spawnSummon(bossEntity, EntityType.ZOMBIE);
            spawnSummon(bossEntity, EntityType.ZOMBIE);
            spawnSummon(bossEntity, EntityType.SKELETON);
        }
        for (Player player : bossEntity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(bossEntity.getLocation()) > 80.0D * 80.0D) continue;
            player.sendMessage("§4Warzone Boss §8» §cPhase " + phase + " §7ist gestartet.");
            player.playSound(player.getLocation(), Sound.ENTITY_RAVAGER_ROAR, 0.8F, phase == 2 ? 0.9F : 0.7F);
        }
    }

    private void spawnSummon(LivingEntity bossEntity, EntityType type) {
        int level = bossEntity.getPersistentDataContainer().getOrDefault(mobLevel, PersistentDataType.INTEGER, 1);
        LivingEntity summon = spawnManaged(bossEntity.getLocation(), type);
        configure(summon, level, 6.0D + level * 3.0D, false);
    }

    @EventHandler(ignoreCancelled = true, priority = org.bukkit.event.EventPriority.HIGHEST)
    public void onCreatureSpawn(CreatureSpawnEvent event) {
        if (spawningManaged || warzone == null) return;
        if (!(event.getEntity() instanceof Mob)) return;
        if (warzone.isLobbySpawnArea(event.getLocation()) || warzone.isWarzone(event.getLocation())) {
            event.setCancelled(true);
        }
    }

    private void applyBossAura(LivingEntity entity) {
        BossType type = bossType(entity);
        for (Player player : entity.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(entity.getLocation()) > 6.0D * 6.0D) continue;
            switch (type) {
                case RAVAGER -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, 50, 0, true, false));
                    if (ThreadLocalRandom.current().nextDouble() < 0.20D) {
                        player.setVelocity(player.getLocation().toVector().subtract(entity.getLocation().toVector()).normalize().multiply(0.85D).setY(0.35D));
                    }
                }
                case INFERNO -> {
                    player.setFireTicks(Math.max(player.getFireTicks(), 70));
                    if (ThreadLocalRandom.current().nextDouble() < 0.16D) {
                        player.damage(2.0D, entity);
                    }
                }
                case REAPER -> {
                    player.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 50, 0, true, false));
                    player.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 50, 0, true, false));
                }
            }
        }
    }

    private String serialize(Location location) {
        return location.getWorld().getName() + ";"
                + location.getX() + ";"
                + location.getY() + ";"
                + location.getZ() + ";"
                + location.getYaw() + ";"
                + location.getPitch();
    }

    private Location deserialize(String raw) {
        if (raw == null || raw.isBlank()) return null;
        String[] parts = raw.split(";");
        if (parts.length < 4) return null;
        var world = Bukkit.getWorld(parts[0]);
        if (world == null) return null;
        try {
            float yaw = parts.length >= 5 ? Float.parseFloat(parts[4]) : 0.0F;
            float pitch = parts.length >= 6 ? Float.parseFloat(parts[5]) : 0.0F;
            return new Location(
                    world,
                    Double.parseDouble(parts[1]),
                    Double.parseDouble(parts[2]),
                    Double.parseDouble(parts[3]),
                    yaw,
                    pitch
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private BossType bossType(Entity entity) {
        String id = entity.getPersistentDataContainer().get(bossType, PersistentDataType.STRING);
        BossType type = BossType.from(id);
        return type == null ? BossType.RAVAGER : type;
    }

    public enum BossType {
        RAVAGER("ravager", "Disparity Ravager", "§c", EntityType.RAVAGER, 120.0D),
        INFERNO("inferno", "Inferno Warden", "§6", EntityType.BLAZE, 100.0D),
        REAPER("reaper", "Void Reaper", "§5", EntityType.WITHER_SKELETON, 110.0D);

        public final String id;
        public final String display;
        public final String color;
        private final EntityType entityType;
        private final double baseHealth;

        BossType(String id, String display, String color, EntityType entityType, double baseHealth) {
            this.id = id;
            this.display = display;
            this.color = color;
            this.entityType = entityType;
            this.baseHealth = baseHealth;
        }

        public static BossType from(String raw) {
            if (raw == null) return null;
            for (BossType type : values()) if (type.id.equalsIgnoreCase(raw) || type.name().equalsIgnoreCase(raw)) return type;
            return null;
        }
    }
}
