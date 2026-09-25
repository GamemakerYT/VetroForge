package com.vetroforge.services;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.Rarity;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.io.File;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class DailyRewardService {
    private final FactionsPlugin plugin;
    private final File file;
    private final YamlConfiguration yaml;
    private final ZoneId zone = ZoneId.systemDefault();
    private final NamespacedKey xpBottleKey;

    public DailyRewardService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "daily-rewards.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
        this.xpBottleKey = new NamespacedKey(plugin, "level_xp_bottle");
        initTables();
    }

    public FactionsPlugin plugin() {
        return plugin;
    }

    public Status status(Player player) {
        return status(player.getUniqueId());
    }

    public Status status(UUID uuid) {
        RewardState state = load(uuid);
        LocalDate today = LocalDate.now(zone);
        boolean claimedToday = today.equals(state.lastClaim);
        int displayStreak = claimedToday ? state.streak : projectedStreak(state, today);
        return new Status(!claimedToday, displayStreak, state.totalClaims, nextResetText());
    }

    public ClaimResult claim(Player player) {
        UUID uuid = player.getUniqueId();
        RewardState state = load(uuid);
        LocalDate today = LocalDate.now(zone);
        if (today.equals(state.lastClaim)) {
            return new ClaimResult(false, "§cDaily Reward ist schon abgeholt. Reset in §f" + nextResetText(), 0, 0, 0, false, state.streak);
        }

        int streak = projectedStreak(state, today);
        int coins = Math.min(
                plugin.getConfig().getInt("dailyRewards.maxCoins", 750),
                plugin.getConfig().getInt("dailyRewards.baseCoins", 250)
                        + streak * plugin.getConfig().getInt("dailyRewards.coinsPerStreak", 25)
        );
        coins = plugin.getBoosterManager().applyCoinBoost(uuid, coins);
        int xpBottles = Math.min(
                plugin.getConfig().getInt("dailyRewards.maxXpBottles", 6),
                plugin.getConfig().getInt("dailyRewards.baseXpBottles", 2)
                        + Math.max(0, streak - 1) * plugin.getConfig().getInt("dailyRewards.xpBottlesPerStreak", 1)
        );
        int xpPerBottle = Math.max(1, plugin.getConfig().getInt("dailyRewards.xpPerBottle",
                plugin.getConfig().getInt("level.sources.dailyBottle", 10)));
        boolean key = streak % plugin.getConfig().getInt("dailyRewards.keyEveryDays", 7) == 0;

        give(player, coins, xpBottles, xpPerBottle, key, streak);
        save(uuid, new RewardState(today, streak, state.totalClaims + 1));
        return new ClaimResult(true, "§aDaily Reward abgeholt.", coins, xpBottles, xpPerBottle, key, streak);
    }

    private void give(Player player, int coins, int xpBottles, int xpPerBottle, boolean key, int streak) {
        if (plugin.getCoinsManager() != null) {
            plugin.getCoinsManager().addCoins(player.getUniqueId(), coins);
        } else if (plugin.getEconomyManager() != null) {
            plugin.getEconomyManager().deposit(player.getUniqueId(), coins);
        }

        giveItem(player, named(Material.EXPERIENCE_BOTTLE, xpBottles, "§aDaily XP-Flasche §8(+" + xpPerBottle + ")", List.of(
                "§7Kleiner täglicher Fortschritt.",
                "§7Gibt §f" + xpPerBottle + " Level-XP§7.",
                "§8Streak: " + streak
        ), xpPerBottle));
        giveItem(player, named(Material.COOKED_BEEF, 16, "§6Daily Food", List.of("§7Ein kleiner Vorrat für heute.")));
        if (key && plugin.getKeyCrateManager() != null) {
            giveItem(player, plugin.getKeyCrateManager().createKeyItem(Rarity.RARE, 1));
        }
    }

    private int projectedStreak(RewardState state, LocalDate today) {
        if (state.lastClaim == null) return 1;
        if (state.lastClaim.equals(today.minusDays(1))) return Math.max(1, state.streak + 1);
        if (state.lastClaim.equals(today)) return Math.max(1, state.streak);
        return 1;
    }

    private String nextResetText() {
        var now = java.time.ZonedDateTime.now(zone);
        var next = now.toLocalDate().plusDays(1).atStartOfDay(zone);
        Duration duration = Duration.between(now, next);
        long hours = duration.toHours();
        long minutes = duration.toMinutesPart();
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1, minutes) + "m";
    }

    private void giveItem(Player player, ItemStack item) {
        if (item == null || item.getType().isAir()) return;
        player.getInventory().addItem(item).values()
                .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
    }

    private ItemStack named(Material material, int amount, String name, List<String> lore) {
        return named(material, amount, name, lore, 0);
    }

    private ItemStack named(Material material, int amount, String name, List<String> lore, int levelXp) {
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(new ArrayList<>(lore));
            if (levelXp > 0) {
                meta.getPersistentDataContainer().set(xpBottleKey, PersistentDataType.INTEGER, levelXp);
            }
            item.setItemMeta(meta);
        }
        return item;
    }

    private void initTables() {
        if (!mysqlEnabled()) return;
        plugin.getServer().getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS daily_reward_claims (
                          uuid VARCHAR(36) NOT NULL PRIMARY KEY,
                          last_claim DATE NULL,
                          streak INT NOT NULL DEFAULT 0,
                          total_claims INT NOT NULL DEFAULT 0,
                          updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[DailyRewards] MySQL init fehlgeschlagen: " + throwable.getMessage());
            }
        });
    }

    private RewardState load(UUID uuid) {
        if (mysqlEnabled()) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("SELECT last_claim, streak, total_claims FROM daily_reward_claims WHERE uuid=?")) {
                statement.setString(1, uuid.toString());
                try (ResultSet result = statement.executeQuery()) {
                    if (result.next()) {
                        java.sql.Date date = result.getDate("last_claim");
                        return new RewardState(date == null ? null : date.toLocalDate(), result.getInt("streak"), result.getInt("total_claims"));
                    }
                }
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[DailyRewards] Laden fehlgeschlagen: " + throwable.getMessage());
                return new RewardState(LocalDate.now(zone), 0, 0);
            }
            return new RewardState(null, 0, 0);
        }

        String base = "players." + uuid;
        String rawDate = yaml.getString(base + ".lastClaim", "");
        LocalDate date = rawDate == null || rawDate.isBlank() ? null : LocalDate.parse(rawDate);
        return new RewardState(date, yaml.getInt(base + ".streak", 0), yaml.getInt(base + ".totalClaims", 0));
    }

    private void save(UUID uuid, RewardState state) {
        if (mysqlEnabled()) {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO daily_reward_claims (uuid, last_claim, streak, total_claims)
                         VALUES (?, ?, ?, ?)
                         ON DUPLICATE KEY UPDATE last_claim=VALUES(last_claim), streak=VALUES(streak), total_claims=VALUES(total_claims)
                         """)) {
                statement.setString(1, uuid.toString());
                statement.setDate(2, java.sql.Date.valueOf(state.lastClaim));
                statement.setInt(3, state.streak);
                statement.setInt(4, state.totalClaims);
                statement.executeUpdate();
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[DailyRewards] Speichern fehlgeschlagen: " + throwable.getMessage());
            }
            return;
        }

        String base = "players." + uuid;
        yaml.set(base + ".lastClaim", state.lastClaim.toString());
        yaml.set(base + ".streak", state.streak);
        yaml.set(base + ".totalClaims", state.totalClaims);
        try {
            if (!file.getParentFile().exists() && !file.getParentFile().mkdirs()) {
                plugin.getLogger().warning("[DailyRewards] Plugin-Ordner konnte nicht erstellt werden.");
                return;
            }
            yaml.save(file);
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[DailyRewards] daily-rewards.yml konnte nicht gespeichert werden: " + throwable.getMessage());
        }
    }

    private boolean mysqlEnabled() {
        return plugin.getConfig().getBoolean("mysql.enabled", false);
    }

    private Connection connection() throws Exception {
        var config = plugin.getConfig();
        String host = config.getString("mysql.host", "127.0.0.1");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "VF");
        String user = config.getString("mysql.user", "VF");
        String password = config.getString("mysql.password", "");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&autoReconnect=true&serverTimezone=UTC";
        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }

    private record RewardState(LocalDate lastClaim, int streak, int totalClaims) {}

    public record Status(boolean canClaim, int streak, int totalClaims, String resetIn) {}

    public record ClaimResult(boolean claimed, String message, int coins, int xpBottles, int xpPerBottle, boolean key, int streak) {
        public String summary() {
            if (!claimed) return message;
            int totalXp = Math.max(0, xpBottles * xpPerBottle);
            return ChatColor.GOLD + "+" + coins + " Coins"
                    + ChatColor.GRAY + ", " + ChatColor.GREEN + "+" + xpBottles + " XP-Flaschen"
                    + ChatColor.DARK_GRAY + " (" + ChatColor.GRAY + totalXp + " XP" + ChatColor.DARK_GRAY + ")"
                    + (key ? ChatColor.GRAY + ", " + ChatColor.BLUE + "+1 Rare Crystal" : "")
                    + ChatColor.GRAY + " | Streak " + ChatColor.WHITE + streak;
        }
    }
}
