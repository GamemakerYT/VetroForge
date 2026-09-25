package com.vetroforge.services;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.io.IOException;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

public final class BoosterManager {

    public enum Type {
        COIN("coin", "Coin Booster"),
        RAID("raid", "Raid Booster"),
        GLOBAL_COIN("globalcoin", "Globaler Coin Booster");

        public final String id;
        public final String display;

        Type(String id, String display) {
            this.id = id;
            this.display = display;
        }

        public static Type parse(String raw) {
            if (raw == null) return null;
            String value = raw.toLowerCase(Locale.ROOT).replace("_", "").replace("-", "");
            for (Type type : values()) {
                if (type.id.equalsIgnoreCase(value) || type.name().replace("_", "").equalsIgnoreCase(value)) {
                    return type;
                }
            }
            return null;
        }
    }

    private final FactionsPlugin plugin;
    private final File file;
    private final NamespacedKey voucherKey;
    private final ConcurrentMap<UUID, Long> coinBoosters = new ConcurrentHashMap<>();
    private final ConcurrentMap<UUID, Long> raidBoosters = new ConcurrentHashMap<>();
    private volatile long globalCoinUntil;
    private volatile String globalCoinActivator = "";

    public BoosterManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/boosters.yml");
        this.voucherKey = new NamespacedKey(plugin, "booster_type");
        load();
    }

    public void activatePersonal(Player player, Type type) {
        if (player == null || type == Type.GLOBAL_COIN) return;
        long until = System.currentTimeMillis() + personalDurationMillis(type);
        if (type == Type.COIN) coinBoosters.put(player.getUniqueId(), until);
        if (type == Type.RAID) raidBoosters.put(player.getUniqueId(), until);
        save();
        player.playSound(player.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8F, 1.2F);
        player.sendMessage(prefix() + "§a" + type.display + " aktiviert §7(" + formatDuration(until) + "§7).");
    }

    public boolean activatePersonalFromVoucher(Player player, Type type) {
        if (player == null || type == null || type == Type.GLOBAL_COIN) return false;
        if (!hasUnlimitedBoosters(player) && !consumeVoucher(player, type, 1)) {
            player.sendMessage(prefix() + "§cDu hast keinen offenen §f" + type.display + "§c.");
            player.playSound(player.getLocation(), Sound.BLOCK_NOTE_BLOCK_BASS, 0.5F, 0.7F);
            return false;
        }
        activatePersonal(player, type);
        return true;
    }

    public void giveVoucher(Player player, Type type, int amount) {
        if (player == null || type == null || type == Type.GLOBAL_COIN || amount <= 0) return;
        int remaining = amount;
        while (remaining > 0) {
            int stackAmount = Math.min(64, remaining);
            ItemStack voucher = createVoucher(type, stackAmount);
            player.getInventory().addItem(voucher).values()
                    .forEach(item -> player.getWorld().dropItemNaturally(player.getLocation(), item));
            remaining -= stackAmount;
        }
        player.sendMessage(prefix() + "§a+" + amount + " offener " + type.display + " erhalten.");
    }

    public ItemStack createVoucher(Type type, int amount) {
        ItemStack item = new ItemStack(type == Type.RAID ? Material.NETHERITE_SWORD : Material.GOLD_INGOT, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', type == Type.RAID
                ? "&d&lRaid Booster &8(&f2h&8)"
                : "&6&lCoin Booster &8(&f2h&8)"));
        List<String> lore = new ArrayList<>();
        lore.add("§7Rechtsklick oder §f/booster§7 öffnen.");
        lore.add("§7Wird beim Aktivieren verbraucht.");
        lore.add("");
        lore.add("§8VetroForge Booster");
        meta.setLore(lore);
        meta.getPersistentDataContainer().set(voucherKey, PersistentDataType.STRING, type.id);
        item.setItemMeta(meta);
        return item;
    }

    public int countVouchers(Player player, Type type) {
        if (player == null || type == null || type == Type.GLOBAL_COIN) return 0;
        int count = 0;
        for (ItemStack item : player.getInventory().getContents()) {
            if (isVoucher(item, type)) count += item.getAmount();
        }
        return count;
    }

    public String availableText(Player player, Type type) {
        if (hasUnlimitedBoosters(player)) return "§a∞ §7(Admin)";
        int count = countVouchers(player, type);
        return (count > 0 ? "§a" : "§c") + String.valueOf(count);
    }

    public boolean buyGlobalCoin(Player player) {
        if (player == null || plugin.getCoinsManager() == null) return false;
        int cost = plugin.getConfig().getInt("boosters.globalCoin.cost", 40000);
        int balance = plugin.getCoinsManager().getCoins(player.getUniqueId());
        if (balance < cost) {
            player.sendMessage(prefix() + "§cDu brauchst §e" + cost + " Coins§c für den globalen Booster.");
            return false;
        }
        plugin.getCoinsManager().removeCoins(player.getUniqueId(), cost);
        activateGlobalCoin(player.getName());
        return true;
    }

    public void activateGlobalCoin(String activator) {
        long until = System.currentTimeMillis() + globalDurationMillis();
        globalCoinUntil = Math.max(globalCoinUntil, until);
        globalCoinActivator = activator == null || activator.isBlank() ? "Server" : activator;
        save();
        Bukkit.broadcastMessage(prefix() + "§6Globaler Coin Booster §aaktiviert §7von §f" + globalCoinActivator
                + " §8• §7läuft " + formatDuration(globalCoinUntil) + "§7.");
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.playSound(player.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.65F, 1.25F);
        }
    }

    public int applyCoinBoost(UUID uuid, int amount) {
        if (amount <= 0) return amount;
        double multiplier = 1.0D;
        if (uuid != null && isPersonalActive(uuid, Type.COIN)) {
            multiplier *= plugin.getConfig().getDouble("boosters.personalCoin.multiplier", 1.5D);
        }
        if (isGlobalCoinActive()) {
            multiplier *= plugin.getConfig().getDouble("boosters.globalCoin.multiplier", 1.5D);
        }
        return Math.max(1, (int) Math.round(amount * multiplier));
    }

    public int applyRaidXp(Player player, int amount) {
        if (player == null || amount <= 0 || !isPersonalActive(player.getUniqueId(), Type.RAID)) return amount;
        double multiplier = plugin.getConfig().getDouble("boosters.raid.multiplier", 1.5D);
        return Math.max(1, (int) Math.round(amount * multiplier));
    }

    public boolean isPersonalActive(UUID uuid, Type type) {
        if (uuid == null || type == null) return false;
        long now = System.currentTimeMillis();
        ConcurrentMap<UUID, Long> map = type == Type.RAID ? raidBoosters : coinBoosters;
        Long until = map.get(uuid);
        if (until == null) return false;
        if (until <= now) {
            map.remove(uuid);
            return false;
        }
        return true;
    }

    public boolean isGlobalCoinActive() {
        return globalCoinUntil > System.currentTimeMillis();
    }

    public boolean isActive(UUID uuid, Type type) {
        if (type == Type.GLOBAL_COIN) return isGlobalCoinActive();
        return isPersonalActive(uuid, type);
    }

    public String status(UUID uuid) {
        return "§6Coin§7: " + formatStatus(coinBoosters.get(uuid))
                + " §8| §dRaid§7: " + formatStatus(raidBoosters.get(uuid))
                + " §8| §eGlobal§7: " + formatStatus(globalCoinUntil);
    }

    public String remaining(UUID uuid, Type type) {
        if (type == Type.GLOBAL_COIN) return formatStatus(globalCoinUntil);
        if (uuid == null) return "§cAus";
        ConcurrentMap<UUID, Long> map = type == Type.RAID ? raidBoosters : coinBoosters;
        return formatStatus(map.get(uuid));
    }

    public boolean hasUnlimitedBoosters(Player player) {
        return player != null && (player.isOp() || player.hasPermission("vetroforge.booster.free"));
    }

    public String globalActivator() {
        return globalCoinActivator == null || globalCoinActivator.isBlank() ? "Server" : globalCoinActivator;
    }

    private long personalDurationMillis(Type type) {
        String path = type == Type.RAID ? "boosters.raid.durationMinutes" : "boosters.personalCoin.durationMinutes";
        return Duration.ofMinutes(Math.max(1, plugin.getConfig().getInt(path, 120))).toMillis();
    }

    private long globalDurationMillis() {
        return Duration.ofMinutes(Math.max(1, plugin.getConfig().getInt("boosters.globalCoin.durationMinutes", 60))).toMillis();
    }

    private String formatStatus(Long until) {
        if (until == null || until <= System.currentTimeMillis()) return "§cAus";
        return "§a" + formatDuration(until);
    }

    private String formatDuration(long until) {
        long seconds = Math.max(0L, (until - System.currentTimeMillis()) / 1000L);
        long hours = seconds / 3600L;
        long minutes = (seconds % 3600L) / 60L;
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1L, minutes) + "m";
    }

    private String prefix() {
        return ChatColor.AQUA + "" + ChatColor.BOLD + "VetroForge " + ChatColor.DARK_GRAY + "» ";
    }

    private boolean consumeVoucher(Player player, Type type, int amount) {
        int needed = Math.max(1, amount);
        if (countVouchers(player, type) < needed) return false;
        for (ItemStack item : player.getInventory().getContents()) {
            if (!isVoucher(item, type)) continue;
            int take = Math.min(item.getAmount(), needed);
            item.setAmount(item.getAmount() - take);
            needed -= take;
            if (needed <= 0) break;
        }
        return true;
    }

    private boolean isVoucher(ItemStack item, Type type) {
        if (item == null || item.getType().isAir() || !item.hasItemMeta()) return false;
        String id = item.getItemMeta().getPersistentDataContainer().get(voucherKey, PersistentDataType.STRING);
        return type.id.equalsIgnoreCase(String.valueOf(id));
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        globalCoinUntil = yaml.getLong("global.coin.until", 0L);
        globalCoinActivator = yaml.getString("global.coin.activator", "");
        if (yaml.isConfigurationSection("players")) {
            for (String id : yaml.getConfigurationSection("players").getKeys(false)) {
                try {
                    UUID uuid = UUID.fromString(id);
                    long coin = yaml.getLong("players." + id + ".coin", 0L);
                    long raid = yaml.getLong("players." + id + ".raid", 0L);
                    if (coin > System.currentTimeMillis()) coinBoosters.put(uuid, coin);
                    if (raid > System.currentTimeMillis()) raidBoosters.put(uuid, raid);
                } catch (IllegalArgumentException ignored) {}
            }
        }
    }

    public void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        yaml.set("global.coin.until", globalCoinUntil);
        yaml.set("global.coin.activator", globalCoinActivator);
        for (UUID uuid : coinBoosters.keySet()) {
            yaml.set("players." + uuid + ".coin", coinBoosters.get(uuid));
        }
        for (UUID uuid : raidBoosters.keySet()) {
            yaml.set("players." + uuid + ".raid", raidBoosters.get(uuid));
        }
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[Booster] Konnte boosters.yml nicht speichern: " + e.getMessage());
        }
    }
}
