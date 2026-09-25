package com.vetroforge.capture;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.crates.CrateKeyType;
import com.vetroforge.crates.CrateKeyUtil;
import com.vetroforge.warzone.WarzoneCaptureEvent;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.entity.EntityToggleGlideEvent;
import org.bukkit.event.player.PlayerBucketEmptyEvent;
import org.bukkit.event.player.PlayerBucketFillEvent;
import org.bukkit.event.player.PlayerCommandPreprocessEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerPortalEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.player.PlayerTeleportEvent;
import org.bukkit.event.vehicle.VehicleEnterEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class CaptureZoneManager implements Listener {

    public record FlagSnapshot(String id, String name, Location location, String ownerFaction, int progressPercent) {}
    public record SeasonEntry(String faction, int captures) {}

    private final FactionsPlugin plugin;
    private final CaptureZoneBuffService buffs;
    private final Map<String, CaptureFlag> flags = new HashMap<>();
    private final Map<String, BossBar> bars = new HashMap<>();
    private final Map<UUID, CaptureFlag> carries = new HashMap<>();
    private final File seasonFile;
    private final YamlConfiguration seasonData;
    private BukkitTask task;

    public CaptureZoneManager(FactionsPlugin plugin, CaptureZoneBuffService buffs) {
        this.plugin = plugin;
        this.buffs = buffs;
        this.seasonFile = new File(plugin.getDataFolder(), "data/ctf-season.yml");
        this.seasonData = YamlConfiguration.loadConfiguration(seasonFile);
        load();
        Bukkit.getPluginManager().registerEvents(this, plugin);
        task = Bukkit.getScheduler().runTaskTimer(plugin, (Runnable) this::tick, 20L, 10L);
    }

    public void setFlag(String name, Location location) {
        if (location == null || location.getWorld() == null) return;
        String displayName = name == null || name.isBlank() ? "Flag 1" : name.trim();
        String id = normalize(displayName);
        CaptureFlag previous = flags.get(id);
        if (previous != null && previous.ownerFaction != null) buffs.onZoneLost(previous.ownerFaction);
        removeBar(id);
        Location center = location.getBlock().getLocation().add(0.5, 0.0, 0.5);
        center.getBlock().setType(Material.WHITE_BANNER, false);
        flags.put(id, new CaptureFlag(id, displayName, center));
        save();
    }

    public boolean removeFlag(String name) {
        CaptureFlag removed = flags.remove(normalize(name));
        if (removed == null) return false;
        if (removed.ownerFaction != null) buffs.onZoneLost(removed.ownerFaction);
        removeBar(removed.id);
        save();
        return true;
    }

    public List<FlagSnapshot> listFlags() {
        return flags.values().stream()
                .sorted(Comparator.comparing(flag -> flag.name.toLowerCase(Locale.ROOT)))
                .map(this::snapshot)
                .toList();
    }

    public FlagSnapshot getFlag(String name) {
        CaptureFlag flag = flags.get(normalize(name));
        return flag == null ? null : snapshot(flag);
    }

    public void setCenter(Location location) { setFlag("Flag 1", location); }
    public void removeCenter() { removeFlag("Flag 1"); }
    public Location getCenter() { FlagSnapshot flag = getFlag("Flag 1"); return flag == null ? null : flag.location(); }
    public String getOwnerFaction() { FlagSnapshot flag = getFlag("Flag 1"); return flag == null ? null : flag.ownerFaction(); }
    public int getProgressPercent() { FlagSnapshot flag = getFlag("Flag 1"); return flag == null ? 0 : flag.progressPercent(); }

    public List<SeasonEntry> seasonLeaderboard(int limit) {
        ConfigurationSection section = seasonData.getConfigurationSection("factions");
        if (section == null) return List.of();
        return section.getKeys(false).stream()
                .map(faction -> new SeasonEntry(faction, Math.max(0, seasonData.getInt("factions." + faction + ".captures", 0))))
                .sorted(Comparator.comparingInt(SeasonEntry::captures).reversed()
                        .thenComparing(SeasonEntry::faction, String.CASE_INSENSITIVE_ORDER))
                .limit(Math.max(1, limit))
                .toList();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
        for (BossBar bar : bars.values()) {
            try { bar.removeAll(); } catch (Throwable ignored) {}
        }
        bars.clear();
        carries.clear();
    }

    private void load() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("capture.flags");
        if (section != null) {
            for (String id : section.getKeys(false)) {
                String root = "capture.flags." + id;
                String worldName = plugin.getConfig().getString(root + ".world");
                if (worldName == null || Bukkit.getWorld(worldName) == null) continue;
                CaptureFlag flag = new CaptureFlag(id,
                        plugin.getConfig().getString(root + ".name", id),
                        new Location(Bukkit.getWorld(worldName),
                                plugin.getConfig().getDouble(root + ".x"),
                                plugin.getConfig().getDouble(root + ".y"),
                                plugin.getConfig().getDouble(root + ".z")));
                flag.ownerFaction = plugin.getConfig().getString(root + ".owner");
                flags.put(id, flag);
                if (flag.ownerFaction != null) buffs.onZoneCaptured(flag.ownerFaction);
            }
            return;
        }

        String legacyWorld = plugin.getConfig().getString("capture.flag.world");
        if (legacyWorld != null && Bukkit.getWorld(legacyWorld) != null) {
            setFlag("Flag 1", new Location(Bukkit.getWorld(legacyWorld),
                    plugin.getConfig().getDouble("capture.flag.x"),
                    plugin.getConfig().getDouble("capture.flag.y"),
                    plugin.getConfig().getDouble("capture.flag.z")));
            plugin.getConfig().set("capture.flag", null);
            save();
        }
    }

    private void save() {
        plugin.getConfig().set("capture.flags", null);
        for (CaptureFlag flag : flags.values()) {
            String root = "capture.flags." + flag.id;
            plugin.getConfig().set(root + ".name", flag.name);
            plugin.getConfig().set(root + ".world", flag.center.getWorld().getName());
            plugin.getConfig().set(root + ".x", flag.center.getX());
            plugin.getConfig().set(root + ".y", flag.center.getY());
            plugin.getConfig().set(root + ".z", flag.center.getZ());
            plugin.getConfig().set(root + ".owner", flag.ownerFaction);
        }
        plugin.saveConfig();
    }

    private void tick() {
        if (plugin.getFactionManager() == null) return;
        for (CaptureFlag flag : new ArrayList<>(flags.values())) tick(flag);
    }

    private void tick(CaptureFlag flag) {
        if (flag.center.getWorld() == null) return;
        double radius = plugin.getConfig().getDouble("capture.radius", 6.0);
        Map<String, Integer> factions = new HashMap<>();
        for (Player player : flag.center.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(flag.center) > radius * radius) continue;
            String faction = plugin.getFactionManager().getFactionOf(player.getUniqueId());
            if (faction != null && !faction.isBlank()) factions.merge(faction, 1, Integer::sum);
        }

        if (factions.size() != 1) {
            flag.progressTicks = Math.max(0, flag.progressTicks - 10);
            if (flag.progressTicks == 0) clearCarry(flag, true);
            render(flag, radius);
            return;
        }

        String faction = factions.keySet().iterator().next();
        if (faction.equalsIgnoreCase(flag.ownerFaction)) {
            flag.progressTicks = 0;
            clearCarry(flag, false);
            render(flag, radius);
            return;
        }
        if (!faction.equalsIgnoreCase(flag.capturingFaction)) {
            beginCarry(flag, carrierFor(flag, faction, radius), faction);
            flag.progressTicks = 0;
        }

        flag.progressTicks += 10;
        int percent = progress(flag);
        for (Player player : flag.center.getWorld().getPlayers()) {
            if (player.getLocation().distanceSquared(flag.center) <= radius * radius) {
                player.sendActionBar("§6Capture §8» §f" + flag.name + " §8• §f" + faction + " §7erobert §8(§e" + percent + "%§8)");
            }
        }
        if (flag.carrierId != null) {
            Player carrier = Bukkit.getPlayer(flag.carrierId);
            if (carrier != null && carrier.isOnline()) {
                carrier.sendActionBar("§cYou carry the enemy flag §8• §fReach your base");
            }
        }
        render(flag, radius);
        if (flag.progressTicks >= requiredTicks()) capture(flag, faction);
    }

    private void capture(CaptureFlag flag, String faction) {
        Player carrier = flag.carrierId == null ? null : Bukkit.getPlayer(flag.carrierId);
        if (flag.ownerFaction != null) buffs.onZoneLost(flag.ownerFaction);
        flag.ownerFaction = faction;
        buffs.onZoneCaptured(faction);
        clearCarry(flag, false);
        flag.progressTicks = 0;
        Material banner = plugin.getFactionBannerManager() == null ? Material.RED_BANNER
                : plugin.getFactionBannerManager().getBanner(faction);
        flag.center.getBlock().setType(banner, false);
        addSeasonCapture(faction);
        rewardCapture(carrier, faction);
        if (carrier != null) {
            Bukkit.getPluginManager().callEvent(new WarzoneCaptureEvent(carrier, flag.name));
        }
        jewelParticles(flag.center);
        save();
        playSound(flag.center, "ENTITY_ENDER_DRAGON_GROWL", Sound.ENTITY_FIREWORK_ROCKET_TWINKLE);
        flag.center.getWorld().spawnParticle(Particle.FIREWORK, flag.center.clone().add(0.5, 1.2, 0.5), 45, 0.55, 0.55, 0.55, 0.04);
        Bukkit.getOnlinePlayers().forEach(player -> {
            player.sendTitle("§c§lFLAG CAPTURED", "§f" + faction + " §7hat §f" + flag.name + " §7erobert", 5, 45, 10);
            player.sendMessage("§6Capture §8» §f" + faction + " §7hat §f" + flag.name + " §7eingenommen.");
        });
    }

    private void render(CaptureFlag flag, double radius) {
        double progress = Math.min(1.0, flag.progressTicks / (double) requiredTicks());
        renderBar(flag, radius, progress);
        double ring = Math.max(1.0, radius * (1.0 - progress * 0.75));
        Color color = Color.fromRGB((int) (255 * (1.0 - progress)), (int) (255 * progress), 40);
        Particle.DustOptions dust = new Particle.DustOptions(color, 1.2F);
        for (int i = 0; i < 32; i++) {
            double angle = Math.PI * 2.0 * i / 32.0;
            Location point = flag.center.clone().add(Math.cos(angle) * ring, 0.35, Math.sin(angle) * ring);
            flag.center.getWorld().spawnParticle(Particle.DUST, point, 1, dust);
        }
    }

    private void renderBar(CaptureFlag flag, double radius, double progress) {
        BossBar bar = bars.computeIfAbsent(flag.id, ignored ->
                Bukkit.createBossBar("§c§lVetroForge", BarColor.WHITE, BarStyle.SEGMENTED_10));

        String title;
        BarColor color;
        double value;
        if (flag.capturingFaction != null) {
            title = "§c§lVetroForge §8» §f" + flag.capturingFaction + " stole your flag!"
                    + " §8• §7Return in: §f" + returnSecondsLeft(flag) + "s";
            color = BarColor.YELLOW;
            value = progress;
        } else if (flag.ownerFaction != null && !flag.ownerFaction.isBlank()) {
            title = "§c§lVetroForge §8» §f" + flag.ownerFaction + " §7haelt §f" + flag.name;
            color = BarColor.RED;
            value = 1.0D;
        } else {
            title = "§c§lVetroForge §8» §fErobere die Flagge";
            color = BarColor.WHITE;
            value = 0.08D;
        }

        bar.setTitle(title);
        bar.setColor(color);
        bar.setProgress(Math.max(0.0D, Math.min(1.0D, value)));

        double visibleRadius = Math.max(radius * 3.0D, 24.0D);
        for (Player player : Bukkit.getOnlinePlayers()) {
            boolean visible = player.getWorld().equals(flag.center.getWorld())
                    && player.getLocation().distanceSquared(flag.center) <= visibleRadius * visibleRadius;
            if (visible) {
                if (!bar.getPlayers().contains(player)) bar.addPlayer(player);
            } else if (bar.getPlayers().contains(player)) {
                bar.removePlayer(player);
            }
        }
    }

    private void removeBar(String id) {
        BossBar bar = bars.remove(id);
        if (bar != null) {
            try { bar.removeAll(); } catch (Throwable ignored) {}
        }
    }

    private FlagSnapshot snapshot(CaptureFlag flag) {
        return new FlagSnapshot(flag.id, flag.name, flag.center.clone(), flag.ownerFaction, progress(flag));
    }

    private int progress(CaptureFlag flag) {
        return Math.min(100, (int) Math.round(flag.progressTicks * 100.0 / requiredTicks()));
    }

    private int requiredTicks() { return Math.max(20, plugin.getConfig().getInt("capture.seconds", 20) * 20); }

    private int returnSeconds() { return Math.max(5, plugin.getConfig().getInt("capture.returnSeconds", 30)); }

    private int returnSecondsLeft(CaptureFlag flag) {
        if (flag.returnAtMs <= 0L) return returnSeconds();
        return (int) Math.max(0L, Math.ceil((flag.returnAtMs - System.currentTimeMillis()) / 1000.0D));
    }

    private String normalize(String value) {
        String normalized = value == null ? "" : value.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
        return normalized.isBlank() ? "flag_1" : normalized.replaceAll("^_+|_+$", "");
    }

    private Player carrierFor(CaptureFlag flag, String faction, double radius) {
        Player best = null;
        double bestDistance = radius * radius;
        for (Player player : flag.center.getWorld().getPlayers()) {
            String playerFaction = plugin.getFactionManager().getFactionOf(player.getUniqueId());
            if (!faction.equalsIgnoreCase(playerFaction)) continue;
            double distance = player.getLocation().distanceSquared(flag.center);
            if (distance <= bestDistance) {
                bestDistance = distance;
                best = player;
            }
        }
        return best;
    }

    private void beginCarry(CaptureFlag flag, Player carrier, String faction) {
        clearCarry(flag, false);
        flag.capturingFaction = faction;
        flag.returnAtMs = System.currentTimeMillis() + returnSeconds() * 1000L;
        if (carrier == null) return;
        flag.carrierId = carrier.getUniqueId();
        carries.put(carrier.getUniqueId(), flag);
        carrier.sendTitle("§c§lFLAG STOLEN", "§7by §f" + carrier.getName(), 5, 40, 10);
        carrier.sendActionBar("§cYou carry the enemy flag §8• §fReach your base");
        playSound(carrier.getLocation(), "ITEM_GOAT_HORN_SOUND_0", Sound.EVENT_RAID_HORN);
        Bukkit.getOnlinePlayers().forEach(player -> {
            if (!player.getUniqueId().equals(carrier.getUniqueId())) {
                player.sendMessage("§c§lCTF §8» §f" + faction + " §7stole §f" + flag.name + "§7. Carrier: §f" + carrier.getName());
            }
        });
    }

    private void clearCarry(CaptureFlag flag, boolean returned) {
        if (flag.carrierId != null) {
            carries.remove(flag.carrierId);
            if (returned) {
                playSound(flag.center, "BLOCK_BELL_USE", Sound.BLOCK_NOTE_BLOCK_BELL);
                Bukkit.getOnlinePlayers().forEach(player ->
                        player.sendMessage("§c§lCTF §8» §7Die Flagge §f" + flag.name + " §7wurde zurueckgesetzt."));
            }
        }
        flag.carrierId = null;
        flag.returnAtMs = 0L;
        flag.capturingFaction = null;
    }

    private boolean isCarrying(Player player) {
        return player != null && carries.containsKey(player.getUniqueId());
    }

    private void block(Player player, org.bukkit.event.Cancellable event, String reason) {
        if (!isCarrying(player)) return;
        event.setCancelled(true);
        player.sendMessage("§c§lCTF §8» §7Gesperrt waehrend Flag-Carry: §f" + reason);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onCommand(PlayerCommandPreprocessEvent event) {
        if (!isCarrying(event.getPlayer())) return;
        String command = event.getMessage().toLowerCase(Locale.ROOT).split("\\s+", 2)[0];
        for (String blocked : List.of("/spawn", "/home", "/f", "/tpa", "/tpahere", "/warp", "/hub", "/lobby")) {
            if (command.equals(blocked)) {
                event.setCancelled(true);
                event.getPlayer().sendMessage("§c§lCTF §8» §7Dieser Command ist waehrend Flag-Carry gesperrt.");
                return;
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onTeleport(PlayerTeleportEvent event) {
        if (event.getCause() == PlayerTeleportEvent.TeleportCause.ENDER_PEARL
                || event.getCause() == PlayerTeleportEvent.TeleportCause.CHORUS_FRUIT
                || event.getCause() == PlayerTeleportEvent.TeleportCause.NETHER_PORTAL) {
            block(event.getPlayer(), event, "Teleport");
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onPortal(PlayerPortalEvent event) {
        block(event.getPlayer(), event, "Portal");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onGlide(EntityToggleGlideEvent event) {
        if (event.getEntity() instanceof Player player && event.isGliding()) block(player, event, "Elytra");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onInteract(PlayerInteractEvent event) {
        if (!isCarrying(event.getPlayer())) return;
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        ItemStack item = event.getItem();
        if (item == null) return;
        Material type = item.getType();
        if (type == Material.ENDER_PEARL || type == Material.CHORUS_FRUIT || type == Material.ELYTRA) {
            block(event.getPlayer(), event, type.name());
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketEmpty(PlayerBucketEmptyEvent event) {
        block(event.getPlayer(), event, "Bucket");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onBucketFill(PlayerBucketFillEvent event) {
        block(event.getPlayer(), event, "Bucket");
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onVehicle(VehicleEnterEvent event) {
        if (event.getEntered() instanceof Player player) block(player, event, "Mount");
    }

    @EventHandler
    public void onQuit(PlayerQuitEvent event) {
        CaptureFlag flag = carries.get(event.getPlayer().getUniqueId());
        if (flag == null) return;
        flag.progressTicks = 0;
        clearCarry(flag, true);
        render(flag, plugin.getConfig().getDouble("capture.radius", 6.0));
    }

    private void addSeasonCapture(String faction) {
        if (faction == null || faction.isBlank()) return;
        String path = "factions." + faction + ".captures";
        seasonData.set(path, Math.max(0, seasonData.getInt(path, 0)) + 1);
        seasonData.set("updatedAt", System.currentTimeMillis());
        try {
            if (seasonFile.getParentFile() != null) seasonFile.getParentFile().mkdirs();
            seasonData.save(seasonFile);
        } catch (IOException ex) {
            plugin.getLogger().warning("[CTF] Season konnte nicht gespeichert werden: " + ex.getMessage());
        }
    }

    private void rewardCapture(Player player, String faction) {
        int coins = plugin.getConfig().getInt("capture.rewards.coins", 1000);
        int xp = plugin.getConfig().getInt("capture.rewards.xp", 250);
        int elo = plugin.getConfig().getInt("capture.rewards.factionElo", 25);
        String keyName = plugin.getConfig().getString("capture.rewards.key", "Rare");
        if (player != null) {
            coins = plugin.getBoosterManager().applyCoinBoost(player.getUniqueId(), coins);
            if (plugin.getCoinsManager() != null) plugin.getCoinsManager().addCoins(player.getUniqueId(), coins);
            if (plugin.getLevelProgressionService() != null) plugin.getLevelProgressionService().addXp(player, xp, "CTF Capture");
            ItemStack key = CrateKeyUtil.make(plugin, parseKey(keyName), 1);
            if (plugin.getItemInboxService() != null) plugin.getItemInboxService().giveOrStore(player, key, "CTF Capture");
            else player.getInventory().addItem(key);
            player.sendMessage("§c§lCTF §8» §a+" + coins + " Coins §8• §a+" + xp + " XP §8• §a1 Key");
        }
        if (plugin.getFactionUpgradeManager() != null) plugin.getFactionUpgradeManager().addElo(faction, elo);
    }

    private CrateKeyType parseKey(String raw) {
        try { return CrateKeyType.valueOf(String.valueOf(raw).trim().toUpperCase(Locale.ROOT)); }
        catch (Throwable ignored) { return CrateKeyType.RARE; }
    }

    private void jewelParticles(Location location) {
        if (location == null || location.getWorld() == null) return;
        for (int i = 0; i < 64; i++) {
            double angle = Math.PI * 2.0D * i / 64.0D;
            double radius = 1.1D + (i % 4) * 0.18D;
            Location point = location.clone().add(Math.cos(angle) * radius, 0.6D + (i % 6) * 0.18D, Math.sin(angle) * radius);
            location.getWorld().spawnParticle(Particle.HAPPY_VILLAGER, point, 1, 0.02, 0.02, 0.02, 0.0);
            location.getWorld().spawnParticle(Particle.END_ROD, point, 1, 0.01, 0.01, 0.01, 0.0);
        }
    }

    private void playSound(Location location, String preferred, Sound fallback) {
        if (location == null || location.getWorld() == null) return;
        Sound sound = fallback;
        try { sound = Sound.valueOf(preferred); } catch (Throwable ignored) {}
        location.getWorld().playSound(location, sound, 1.0F, 1.0F);
    }

    private static final class CaptureFlag {
        private final String id;
        private final String name;
        private final Location center;
        private String ownerFaction;
        private String capturingFaction;
        private UUID carrierId;
        private long returnAtMs;
        private int progressTicks;

        private CaptureFlag(String id, String name, Location center) {
            this.id = id;
            this.name = name;
            this.center = center;
        }
    }
}
