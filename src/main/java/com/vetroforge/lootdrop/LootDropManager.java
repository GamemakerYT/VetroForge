package com.vetroforge.lootdrop;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.lootdrop.model.ActiveDrop;
import com.vetroforge.lootdrop.model.Tier;
import com.vetroforge.lootdrop.model.MeteorType;
import com.vetroforge.items.CustomItemUtil;
import com.vetroforge.items.Rarity;
import com.vetroforge.util.ActionBarUtil;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.block.Chest;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

public class LootDropManager {

    private static final String META = "vetro_lootdrop";

    private final FactionsPlugin plugin;
    private final Map<UUID, ActiveDrop> active = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> pity = new ConcurrentHashMap<>();
    private final NoDropService noDrop;
    private final DropLogger logger;
    private final DiminishingReturnsService diminishing;
    private final LootTables tables;

    private BukkitTask dropTickTask;     // tickt ActiveDrops + ActionBar
    private BukkitTask lootSbTicker;     // refresht nur das Warzone-Board, keine LootDrop-Sidebar

    // Settings
    private final int baseLifetimeSec    = 5 * 60;  // 5 Minuten
    private final int interdictionRadius = 20;      // blockiert Perlen/Booster
    private final int actionbarRadius    = 150;     // Distanz-Anzeige

    public LootDropManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.noDrop = new NoDropService(plugin);
        this.logger = new DropLogger(new File(plugin.getDataFolder(), "lootdrop.log"));
        this.diminishing = new DiminishingReturnsService();
        this.tables = new LootTables(plugin);

        startDropTicker();
        startScoreboardRefresh();
    }

    public void shutdown() {
        if (dropTickTask != null) { dropTickTask.cancel(); dropTickTask = null; }
        if (lootSbTicker != null) { lootSbTicker.cancel(); lootSbTicker = null; }
        for (ActiveDrop d : active.values()) d.despawn();
        active.clear();
        logger.close();
    }

    /* ================= Spawns ================= */

    /** Startet einen Meteorit von oben; der eigentliche Drop entsteht erst beim Einschlag (Impact). */
    public ActiveDrop spawnRandomDrop(Location skyLoc, Tier forcedTierOrNull) {
        MeteorType meteorType = chooseMeteorType(forcedTierOrNull);
        return spawnRandomDrop(skyLoc, forcedTierOrNull, meteorType);
    }

    private MeteorType chooseMeteorType(Tier forcedTierOrNull) {
        if (forcedTierOrNull == Tier.MYTHIC) return MeteorType.MYSTIC;
        int roll = ThreadLocalRandom.current().nextInt(100);
        if (plugin.getConfig().getBoolean("customitems.eventDrops.enabled", true) && roll < 10) return MeteorType.EVENT;
        if (roll < 30) return MeteorType.RAID;
        return MeteorType.SUPPLY;
    }

    public ActiveDrop spawnRandomDrop(Location skyLoc, Tier forcedTierOrNull, MeteorType meteorType) {
        if (skyLoc == null || skyLoc.getWorld() == null) return null;
        if (!isAllowedDrop(skyLoc)) return null;

        // Boden ermitteln
        Location ground = skyLoc.clone();
        ground.setY(ground.getWorld().getHighestBlockYAt(ground) + 1);

        // Tier bestimmen (inkl. Wochenend-Bias)
        final Tier tier = (forcedTierOrNull != null) ? forcedTierOrNull : pickTierWithWeekendBias();

        // Meteor-Effekt → Callback bei Impact
        LootMeteorEffect.play(plugin, ground, () -> {
            // 1) Kiste setzen (GENAU 1×)
            var block = ground.getBlock();
            if (block.getType() != Material.CHEST) block.setType(Material.CHEST, false);
            var state = block.getState();
            if (!(state instanceof Chest chest)) return;

            // farbiger Name direkt auf der Kiste
            String name = ChatColor.RED + "" + ChatColor.BOLD + "Lootdrop"
                    + ChatColor.DARK_GRAY + " » " + tier.displayName();
            chest.setCustomName(name);
            chest.update(true, false);

            // markieren
            block.setMetadata(META, new FixedMetadataValue(plugin, tier.name()));

            // 2) Inventar BEFÜLLEN, bevor Label/ActiveDrop gebaut werden.
            // Falls beim Hologramm etwas schiefgeht, bleibt keine leere Kiste zurück.
            LootDropChestUtil.Context fillContext = new LootDropChestUtil.Context(plugin, ground, null, 5, tier);
            LootDropChestUtil.fillAtExact(fillContext);
            state = block.getState();
            if (state instanceof Chest filledChest && isInventoryEmpty(filledChest.getBlockInventory())) {
                getLoggerWarning("[LootDrop] Kiste ist nach Fill leer geblieben @ " + fmt(ground));
                LootDropChestUtil.fillAtExact(new LootDropChestUtil.Context(plugin, ground, null, 7, tier));
            }
            LootDropChestUtil.ensureFilled(fillContext);

            // 3) TITEL über der Kiste + Drop-Objekt
            ActiveDrop drop = ActiveDrop.spawn(plugin, ground, tier, meteorType, baseLifetimeSec, interdictionRadius);
            drop.setChestBlock(block); // benötigt get/setChestBlock in ActiveDrop
            active.put(drop.id(), drop);
            Bukkit.getScheduler().runTaskLater(plugin, () -> LootDropChestUtil.ensureFilled(fillContext), 2L);

            // 4) SFX
            try {
                ground.getWorld().playSound(ground, Sound.BLOCK_END_PORTAL_SPAWN, 1f, 1.2f);
                ground.getWorld().spawnParticle(Particle.CAMPFIRE_COSY_SMOKE, ground.clone().add(0.5,1.2,0.5), 10, .2,.2,.2, 0.01);
            } catch (Throwable ignore) {}

// 5) Broadcast: Lootdrop » Tier <Name> • 5:00 • x/y/z
            Bukkit.getOnlinePlayers().forEach(p -> p.sendMessage(tr(p, "lootdrop.spawn",
                    "type", meteorType.display(),
                    "tier", tier.displayName(),
                    "x", ground.getBlockX(),
                    "y", ground.getBlockY(),
                    "z", ground.getBlockZ())));

            logger.logSpawn(drop, ground);
            try {
                if (plugin.discordBridge() != null) {
                    plugin.discordBridge().sendWarzoneLootdropAlert(ground);
                }
            } catch (Throwable ignored) {}
            // 6) Warzone-Board sofort aktualisieren
            try {
                var sb = plugin.getSidebarScoreboardManager();
                if (sb != null) sb.refreshWarzoneBoardForWorld(ground.getWorld());
            } catch (Throwable ignored) {}
        });

        // Hinweis: Rückgabewert absichtlich null – entsteht erst beim Impact.
        return null;
    }

    /** Flare: Drop direkt beim Spieler. */
    public ActiveDrop flareAt(Player p, Tier tierOrNull) {
        if (p == null || p.getWorld() == null) return null;
        Location sky = p.getLocation().clone().add(0, 40, 0);
        sky.getWorld().spawnParticle(Particle.FLASH, p.getLocation(), 8, 0.5, 1.0, 0.5);
        sky.getWorld().playSound(p.getLocation(), Sound.ENTITY_FIREWORK_ROCKET_LAUNCH, 1f, 1f);
        return spawnRandomDrop(sky, tierOrNull);
    }

    /** Storm: Mehrere Drops rund um einen Punkt (MYTHIC). */
    public List<ActiveDrop> storm(Location center, int amount, double spread) {
        List<ActiveDrop> list = new ArrayList<>();
        if (center == null || center.getWorld() == null) return list;

        for (int i = 0; i < amount; i++) {
            double dx = (rnd().nextDouble() - 0.5) * spread;
            double dz = (rnd().nextDouble() - 0.5) * spread;
            Location sky = center.clone().add(dx, 40, dz);
            // entsteht erst beim Impact → Rückgabe i. d. R. null
            spawnRandomDrop(sky, Tier.MYTHIC);
        }
        return list;
    }

    /* ================= Öffnen ================= */

    /** Wird von Listener/Lockpick o. ä. aufgerufen. */
    public void tryOpen(Player p, UUID dropId) {
        ActiveDrop drop = active.get(dropId);
        if (drop == null) { if (p!=null) p.sendMessage(tr(p, "lootdrop.missing")); return; }
        if (drop.consumed()) { if (p!=null) p.sendMessage(tr(p, "lootdrop.already_opened")); return; }
        if (!drop.unlocked()) {
            p.sendMessage(tr(p, "lootdrop.cooling", "seconds", drop.unlockSecondsLeft()));
            return;
        }
        String faction = plugin.getFactionManager() == null ? null : plugin.getFactionManager().getFactionOf(p.getUniqueId());
        drop.claimFaction(faction, plugin.getConfig().getInt("lootdrop.factionExclusiveSeconds", 20));
        if (!drop.canFactionOpen(faction)) {
            p.sendMessage(tr(p, "lootdrop.exclusive", "faction", drop.exclusiveFaction()));
            return;
        }

        // One-Open: claimen
        if (!drop.claimFirst(p.getUniqueId())) {
            p.sendMessage(tr(p, "lootdrop.too_late"));
            return;
        }

        // Diminishing Returns → Gewichte modifizieren
        double penalty = diminishing.penaltyFor(p.getUniqueId());
        Tier rewardTier = applyPity(p.getUniqueId(), drop.tier());
        var rewards = new ArrayList<>(tables.roll(rewardTier, penalty, isDoubleDropActive()));
        if (drop.meteorType() == MeteorType.EVENT) {
            rewards.add(CustomItemUtil.makeEventToken(plugin, rewardTier == Tier.MYTHIC ? 3 : 1));
        }
        ensureClaimCrystal(rewards, rewardTier);

        // Items geben (Rest droppen, falls kein Platz)
        for (var it : rewards) {
            CustomItemUtil.setSource(plugin, it, "lootdrop:" + drop.meteorType().name().toLowerCase(Locale.ROOT));
            if (plugin.getItemProgressionService() != null) {
                plugin.getItemProgressionService().discover(p, it);
                plugin.getItemProgressionService().audit("LOOTDROP", p, it, "drop=" + drop.id());
            }
            Map<Integer, ItemStack> left = p.getInventory().addItem(it);
            left.values().forEach(rem -> p.getWorld().dropItemNaturally(p.getLocation(), rem));
        }
        p.sendMessage(tr(p, "lootdrop.looted", "tier", drop.tier().displayName()));

        logger.logOpen(drop, p, rewards);
        if (plugin.getEventManager() != null) {
            plugin.getEventManager().addProgressIfActive("lootdrop_hunt", 1);
        }
        announceRareFind(p, rewards);
        diminishing.noteOpen(p.getUniqueId());
        drop.finish();
        active.remove(drop.id());

        // Warzone-Board sofort aktualisieren
        try {
            var sb = plugin.getSidebarScoreboardManager();
            if (sb != null) sb.refreshWarzoneBoardForWorld(p.getWorld());
        } catch (Throwable ignored) {}
    }

    private Tier applyPity(UUID player, Tier tier) {
        int current = pity.getOrDefault(player, 0);
        if (tier == Tier.MYTHIC || current >= plugin.getConfig().getInt("lootdrop.pity.opens", 8)) {
            pity.put(player, 0);
            return tier == Tier.MYTHIC ? tier : tier.upBias();
        }
        pity.put(player, current + 1);
        return tier;
    }

    private void ensureClaimCrystal(List<ItemStack> rewards, Tier tier) {
        for (ItemStack item : rewards) {
            if (item == null || item.getType().isAir()) continue;
            if (item.getType() == Material.NETHER_STAR || item.getType() == Material.END_CRYSTAL || item.getType() == Material.AMETHYST_SHARD) return;
            if (plugin.getKeyCrateManager() != null && plugin.getKeyCrateManager().isKeyItem(item)) return;
        }

        Rarity rarity = switch (tier == null ? Tier.GOLD : tier) {
            case BRONZE -> Rarity.RARE;
            case SILVER -> Rarity.EPIC;
            case GOLD -> Rarity.LEGENDARY;
            case MYTHIC -> Rarity.MYSTIC;
        };
        rewards.add(LootItemFactory.guaranteedCrystal(plugin, rarity, 1));
    }

    private void announceRareFind(Player player, List<ItemStack> rewards) {
        for (ItemStack item : rewards) {
            if (CustomItemUtil.baseId(plugin, item) == null) continue;
            if (CustomItemUtil.rarityOf(plugin, item).ordinal() < Rarity.MYSTIC.ordinal()) continue;
            Bukkit.getOnlinePlayers().forEach(viewer -> viewer.sendMessage(tr(viewer, "lootdrop.rare_find",
                    "player", player.getName(), "item", CustomItemUtil.baseId(plugin, item))));
        }
    }

    /* ================= Laufender Tick: Drops + ActionBar ================= */

    private void startDropTicker() {
        if (dropTickTask != null) return;
        dropTickTask = new BukkitRunnable() {
            @Override public void run() {
                Iterator<ActiveDrop> it = active.values().iterator();
                while (it.hasNext()) {
                    ActiveDrop d = it.next();
                    if (!d.tick()) { // abgelaufen
                        logger.logExpire(d);
                        // Scoreboard sofort updaten (Drop verschwunden)
                        try {
                            var sb = plugin.getSidebarScoreboardManager();
                            if (sb != null) {
                                World w = d.location().getWorld();
                                if (w != null) sb.refreshWarzoneBoardForWorld(w);
                            }
                        } catch (Throwable ignored) {}
                        it.remove();
                        continue;
                    }

                    // ActionBar im Umkreis
                    World w = d.location().getWorld();
                    if (w == null) continue;
                    for (Player p : w.getPlayers()) {
                        double dist = safeDist(p.getLocation(), d.location());
                        if (dist <= actionbarRadius) {
                            String txt = ChatColor.GOLD + d.tier().displayName()
                                    + ChatColor.DARK_GRAY + " [" + ChatColor.LIGHT_PURPLE + d.meteorType().display() + ChatColor.DARK_GRAY + "]"
                                    + ChatColor.GRAY + " • "
                                    + ChatColor.WHITE + (d.secondsLeft()/60) + ":" + String.format("%02d", d.secondsLeft()%60)
                                    + ChatColor.GRAY + " • "
                                    + ChatColor.YELLOW + (int) dist + "m"
                                    + ChatColor.GRAY + " • "
                                    + ChatColor.WHITE + d.location().getBlockX() + "/" + d.location().getBlockZ()
                                    + ChatColor.GRAY + " • "
                                    + (d.unlocked() ? tr(p, "lootdrop.actionbar.loot") : tr(p, "lootdrop.actionbar.lock", "seconds", d.unlockSecondsLeft()));
                            ActionBarUtil.send(p, txt);
                        }
                    }
                }
            }
        }.runTaskTimer(plugin, 20L, 20L); // 1s
    }

    /* ================= Separater Tick: Warzone-Board refreshen ================= */

    private void startScoreboardRefresh() {
        if (lootSbTicker != null) return;
        lootSbTicker = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            if (active.isEmpty()) return;
            var sb = plugin.getSidebarScoreboardManager();
            if (sb == null) return;

            // Welche Welten sind betroffen?
            Set<World> worlds = new HashSet<>();
            for (ActiveDrop d : active.values()) {
                World w = d.location().getWorld();
                if (w != null) worlds.add(w);
            }
            // Warzone-Board nur für Spieler in diesen Welten aktualisieren
            for (World w : worlds) sb.refreshWarzoneBoardForWorld(w);
        }, 20L, 10L); // Start nach 1s, dann alle 0.5s
    }

    /* ================= Despawn (leer) ================= */

    /** Finde den aktiven Drop, dessen Kiste genau diesem Block entspricht. */
    public ActiveDrop findByChest(org.bukkit.block.Block b) {
        if (b == null) return null;
        for (ActiveDrop d : active.values()) {
            var cb = d.getChestBlock();
            if (cb == null) continue;
            if (!Objects.equals(cb.getWorld(), b.getWorld())) continue;
            if (cb.getX() == b.getX() && cb.getY() == b.getY() && cb.getZ() == b.getZ()) {
                return d;
            }
        }
        return null;
    }

    /** Despawnt (ohne Refill/Rewards), wenn Inventar leer ist. */
    public void despawnIfEmpty(Chest chest) {
        if (chest == null) return;
        if (!isInventoryEmpty(chest.getBlockInventory())) return;

        ActiveDrop d = findByChest(chest.getBlock());
        if (d != null) {
            d.despawn();              // entfernt Titel + Kiste
            active.remove(d.id());    // aus Map nehmen
        } else if (chest.getBlock().hasMetadata(META)) {
            try { chest.getBlock().removeMetadata(META, plugin); } catch (Throwable ignored) {}
            try { chest.getBlockInventory().clear(); } catch (Throwable ignored) {}
            try { chest.getBlock().setType(Material.AIR, false); } catch (Throwable ignored) {}
        }

        // Warzone-Board sofort aktualisieren
        try {
            var sb = plugin.getSidebarScoreboardManager();
            if (sb != null) sb.refreshWarzoneBoardForWorld(chest.getWorld());
        } catch (Throwable ignored) {}
    }

    private boolean isInventoryEmpty(org.bukkit.inventory.Inventory inv) {
        if (inv == null) return true;
        for (var it : inv.getContents()) {
            if (it != null && it.getType() != Material.AIR) return false;
        }
        return true;
    }

    /* ================= Utils ================= */

    private boolean isAllowedDrop(Location loc) { return noDrop.isAllowedDrop(loc); }

    private Tier pickTierWithWeekendBias() {
        Tier t;
        int r = rnd().nextInt(100); // 0..99
        if (r < 55) t = Tier.BRONZE;        // 55%
        else if (r < 85) t = Tier.SILVER;   // 30%
        else if (r < 97) t = Tier.GOLD;     // 12%
        else t = Tier.MYTHIC;               // 3%
        if (isDoubleDropActive()) t = t.upBias();
        return t;
    }

    public boolean isDoubleDropActive() {
        DayOfWeek d = LocalDate.now().getDayOfWeek();
        boolean weekend = (d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY);
        return plugin.getConfig().getBoolean("lootdrop.doubleWeekend", true) && weekend;
    }

    private static ThreadLocalRandom rnd() { return ThreadLocalRandom.current(); }
    private String fmt(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + "/" + l.getBlockY() + "/" + l.getBlockZ();
    }
    private void getLoggerWarning(String message) {
        try { plugin.getLogger().warning(message); } catch (Throwable ignored) {}
    }
    private double safeDist(Location a, Location b) {
        if (a == null || b == null) return Double.POSITIVE_INFINITY;
        if (a.getWorld() == null || b.getWorld() == null) return Double.POSITIVE_INFINITY;
        if (!Objects.equals(a.getWorld().getUID(), b.getWorld().getUID())) return Double.POSITIVE_INFINITY;
        return a.distance(b);
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, key, VetroForgeUtil.vars(values));
    }

    /* ===== API ===== */
    public Collection<ActiveDrop> getActive() { return active.values(); }
    public ActiveDrop get(UUID id){ return active.get(id); }
}
