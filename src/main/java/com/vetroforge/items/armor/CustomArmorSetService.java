package com.vetroforge.items.armor;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.CustomItemUtil;
import com.vetroforge.items.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.Material;
import org.bukkit.event.block.Action;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Locale;

public final class CustomArmorSetService implements Listener {

    private final FactionsPlugin plugin;
    private final File claimsFile;
    private final YamlConfiguration claims;
    private BukkitTask task;

    public CustomArmorSetService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.claimsFile = new File(plugin.getDataFolder(), "data/custom-armor-set-claims.yml");
        this.claims = YamlConfiguration.loadConfiguration(claimsFile);
        Bukkit.getPluginManager().registerEvents(this, plugin);
        start();
    }

    public void shutdown() {
        if (task != null) task.cancel();
        task = null;
    }

    private void start() {
        shutdown();
        task = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                apply(player);
            }
        }, 20L, 20L);
    }

    private void apply(Player player) {
        ItemStack[] armor = player.getInventory().getArmorContents();
        if (armor.length < 4) return;

        String set = dominantSet(armor);
        if (set == null) return;
        int pieces = countPieces(armor, set);
        Rarity lowest = lowestRarity(armor, set);
        applyEffects(player, set, lowest, pieces);
        if (pieces >= 4) rewardOnce(player, set, lowest);
    }

    private String dominantSet(ItemStack[] armor) {
        java.util.Map<String, Integer> counts = new java.util.HashMap<>();
        for (ItemStack piece : armor) {
            String pieceSet = CustomItemUtil.armorSetOf(plugin, piece);
            if (pieceSet != null) counts.merge(pieceSet, 1, Integer::sum);
        }
        return counts.entrySet().stream().filter(e -> e.getValue() >= 2)
                .max(java.util.Map.Entry.comparingByValue()).map(java.util.Map.Entry::getKey).orElse(null);
    }

    private int countPieces(ItemStack[] armor, String set) {
        int count = 0;
        for (ItemStack piece : armor) if (set.equalsIgnoreCase(CustomItemUtil.armorSetOf(plugin, piece))) count++;
        return count;
    }

    private Rarity lowestRarity(ItemStack[] armor, String set) {
        Rarity lowest = Rarity.MYSTIC;
        for (ItemStack piece : armor) {
            if (!set.equalsIgnoreCase(CustomItemUtil.armorSetOf(plugin, piece))) continue;
            Rarity rarity = CustomItemUtil.rarityOf(plugin, piece);
            if (rarity.ordinal() < lowest.ordinal()) lowest = rarity;
        }
        return lowest;
    }

    private void applyEffects(Player player, String set, Rarity rarity, int pieces) {
        int amplifier = switch (rarity) {
            case COMMON, RARE -> 0;
            case EPIC, LEGENDARY -> 1;
            case MYSTIC -> 2;
        };

        switch (set.toLowerCase(Locale.ROOT)) {
            case "shadow" -> {
                give(player, PotionEffectType.SPEED, amplifier);
                if (pieces >= 4) give(player, PotionEffectType.STRENGTH, Math.max(0, amplifier - 1));
            }
            case "titan" -> {
                give(player, PotionEffectType.RESISTANCE, Math.max(0, amplifier - 1));
                if (pieces >= 4) give(player, PotionEffectType.ABSORPTION, amplifier);
            }
            case "ember" -> {
                give(player, PotionEffectType.FIRE_RESISTANCE, 0);
                if (pieces >= 4) give(player, PotionEffectType.HASTE, amplifier);
            }
            case "void" -> {
                give(player, PotionEffectType.SPEED, Math.max(0, amplifier - 1));
                if (pieces >= 4) give(player, PotionEffectType.JUMP_BOOST, amplifier);
            }
            case "scout" -> give(player, PotionEffectType.SPEED, Math.max(0, amplifier - 1));
            case "ranger" -> give(player, PotionEffectType.JUMP_BOOST, Math.max(0, amplifier - 1));
            case "fortune" -> give(player, PotionEffectType.LUCK, amplifier);
            case "vanguard" -> give(player, PotionEffectType.RESISTANCE, Math.max(0, amplifier - 1));
            case "ender" -> {
                give(player, PotionEffectType.SPEED, Math.max(0, amplifier - 1));
                if (pieces >= 4) give(player, PotionEffectType.NIGHT_VISION, 0);
            }
            case "guardian" -> {
                give(player, PotionEffectType.RESISTANCE, Math.max(0, amplifier - 1));
                if (pieces >= 4) give(player, PotionEffectType.REGENERATION, Math.max(0, amplifier - 1));
            }
            case "berserker" -> {
                if (player.getHealth() <= Math.max(1.0, player.getMaxHealth() * 0.4)) {
                    give(player, PotionEffectType.STRENGTH, amplifier);
                    if (pieces >= 4) give(player, PotionEffectType.SPEED, amplifier);
                }
            }
            case "prospector" -> {
                give(player, PotionEffectType.HASTE, amplifier);
                if (pieces >= 4) give(player, PotionEffectType.LUCK, amplifier);
            }
            case "season_one" -> {
                give(player, PotionEffectType.SPEED, Math.max(0, amplifier - 1));
                if (pieces >= 4) give(player, PotionEffectType.HERO_OF_THE_VILLAGE, 0);
            }
            default -> {
            }
        }
    }

    private void give(Player player, PotionEffectType type, int amplifier) {
        player.addPotionEffect(new PotionEffect(type, 50, amplifier, true, false, true));
    }

    @EventHandler(ignoreCancelled = true)
    public void onDamage(EntityDamageByEntityEvent event) {
        if (!(event.getDamager() instanceof Player attacker)) return;
        if (hasSet(attacker, "berserker", 2) && attacker.getHealth() <= Math.max(1.0, attacker.getMaxHealth() * 0.4)) {
            event.setDamage(event.getDamage() * (hasSet(attacker, "berserker", 4) ? 1.18 : 1.08));
        }
        String ownFaction = plugin.getFactionManager() == null ? null
                : plugin.getFactionManager().getFactionOf(attacker.getUniqueId());
        String locationFaction = plugin.getFactionManager() == null ? null
                : plugin.getFactionManager().getFactionAt(attacker.getLocation());
        if (hasSet(attacker, "guardian", 4) && ownFaction != null
                && ownFaction.equalsIgnoreCase(locationFaction)) {
            event.setDamage(event.getDamage() * 1.08);
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onBreak(BlockBreakEvent event) {
        Player player = event.getPlayer();
        if (!hasSet(player, "raider", 2)) return;
        Material material = event.getBlock().getType();
        if (material == Material.OBSIDIAN || material.name().contains("DOOR")) {
            player.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 40, hasSet(player, "raider", 4) ? 2 : 1));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onPearl(PlayerInteractEvent event) {
        if (event.getAction() != Action.RIGHT_CLICK_AIR && event.getAction() != Action.RIGHT_CLICK_BLOCK) return;
        if (event.getItem() == null || event.getItem().getType() != Material.ENDER_PEARL) return;
        if (!hasSet(event.getPlayer(), "void", 4) && !hasSet(event.getPlayer(), "ender", 4)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> event.getPlayer().setCooldown(Material.ENDER_PEARL, 40), 1L);
        event.getPlayer().sendMessage("§5Void Set §8» §dBlink bereit. §7Pearl-Cooldown reduziert.");
    }

    @EventHandler
    public void onKill(PlayerDeathEvent event) {
        Player killer = event.getEntity().getKiller();
        if (killer == null || !hasSet(killer, "fortune", 4) || plugin.getCoinsManager() == null) return;
        int baseCoins = Math.max(1, plugin.getConfig().getInt("customitems.armorSets.fortune.baseKillCoins", 10));
        double multiplier = Math.max(1.0D, plugin.getConfig().getDouble("customitems.armorSets.fortune.killCoinMultiplier", 2.0D));
        int bonusCoins = Math.max(0, (int) Math.round(baseCoins * (multiplier - 1.0D)));
        if (bonusCoins <= 0) return;
        plugin.getCoinsManager().addCoins(killer.getUniqueId(), bonusCoins);
        killer.sendActionBar("§6Fortune Set §8» §ex" + multiplier + " Coins §8(§e+" + bonusCoins + "§8)");
    }

    public boolean hasSet(Player player, String set, int required) {
        return countPieces(player.getInventory().getArmorContents(), set) >= required;
    }

    private void rewardOnce(Player player, String set, Rarity rarity) {
        String path = "claimed." + player.getUniqueId() + "." + set.toLowerCase(Locale.ROOT) + "." + rarity.name();
        if (claims.getBoolean(path, false)) return;

        int coins = switch (rarity) {
            case COMMON -> 100;
            case RARE -> 250;
            case EPIC -> 600;
            case LEGENDARY -> 1500;
            case MYSTIC -> 4000;
        };
        coins = plugin.getConfig().getInt("customitems.armorSets.rewards." + rarity.name().toLowerCase(Locale.ROOT), coins);

        if (plugin.getCoinsManager() != null) {
            plugin.getCoinsManager().addCoins(player.getUniqueId(), coins);
        }
        claims.set(path, true);
        saveClaims();

        player.sendMessage("§6Armor Set §8» §aFull Set aktiviert: §f" + pretty(set)
                + " §8(" + rarity.color + rarity.display + "§8)");
        player.sendMessage("§6Armor Set §8» §e+" + coins + " Coins §7einmalige Set-Belohnung");
        try { player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1.1f); } catch (Throwable ignored) {}
    }

    private void saveClaims() {
        try {
            File parent = claimsFile.getParentFile();
            if (parent != null && !parent.exists()) parent.mkdirs();
            claims.save(claimsFile);
        } catch (IOException e) {
            plugin.getLogger().warning("[ArmorSets] Claims konnten nicht gespeichert werden: " + e.getMessage());
        }
    }

    private String pretty(String set) {
        return Character.toUpperCase(set.charAt(0)) + set.substring(1).toLowerCase(Locale.ROOT);
    }
}
