package com.vetroforge.services;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class RankKitService {
    private final FactionsPlugin plugin;

    public RankKitService(FactionsPlugin plugin) {
        this.plugin = plugin;
        initTables();
    }

    public FactionsPlugin plugin() {
        return plugin;
    }

    public boolean canClaim(Player player, KitType type) {
        RankTier tier = tier(player);
        if (!tier.allows(type)) return false;
        long next = nextClaim(player.getUniqueId(), type);
        if (type == KitType.STARTER) return next <= 0L;
        return next <= System.currentTimeMillis();
    }

    public String statusLine(Player player, KitType type) {
        RankTier tier = tier(player);
        if (!tier.allows(type)) return "§cNicht verfuegbar fuer deinen Rang";

        long next = nextClaim(player.getUniqueId(), type);
        if (type == KitType.STARTER) {
            return next > 0L ? "§7Status: §cBereits abgeholt" : "§7Status: §aBereit";
        }
        long remaining = next - System.currentTimeMillis();
        return remaining > 0L
                ? "§7Cooldown: §c" + formatDuration(remaining)
                : "§7Status: §aBereit";
    }

    public void remindStarter(Player player) {
        if (!canClaim(player, KitType.STARTER)) return;
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!player.isOnline() || !canClaim(player, KitType.STARTER)) return;
            player.sendTitle("§c§lStarter Kit wartet", "§7Oeffne §f/kit §7und hole deinen Startbonus.", 10, 55, 15);
            player.sendMessage("§c§lVetroForge §8» §7Dein Starter Kit wartet. §f/kit §7oeffnen und abholen.");
        }, 60L);
    }

    public void claim(Player player, KitType type) {
        RankTier tier = tier(player);
        if (!tier.allows(type)) {
            player.sendMessage(ChatColor.RED + "Dieses Kit ist fuer deinen Rang nicht verfuegbar.");
            return;
        }
        long now = System.currentTimeMillis();
        long next = nextClaim(player.getUniqueId(), type);
        if (type == KitType.STARTER && next > 0L) {
            player.sendMessage(ChatColor.RED + "Dein Starter Kit wurde bereits abgeholt.");
            return;
        }
        if (next > now) {
            player.sendMessage(ChatColor.RED + "Dieses Kit ist noch im Cooldown: "
                    + ChatColor.WHITE + formatDuration(next - now));
            return;
        }

        giveDefaults(player, tier, type);
        runConfiguredCommands(player, tier, type);
        setNextClaim(player.getUniqueId(), type, type == KitType.STARTER ? Long.MAX_VALUE : now + type.cooldownMillis);
        player.sendMessage(ChatColor.GREEN + type.display + " Kit erhalten "
                + ChatColor.DARK_GRAY + "(" + tier.display + ")");
    }

    public RankTier tier(Player player) {
        String rank = rankName(player).toLowerCase(Locale.ROOT).replace(" ", "").replace("-", "");
        if (player.hasPermission("vetroforge.rank.vetroplus") || rank.equals("vetro+") || rank.equals("vetroplus")) {
            return RankTier.VETRO_PLUS;
        }
        if (player.hasPermission("vetroforge.rank.apex") || player.hasPermission("vetroforge.rank.mythic")
                || rank.equals("apex") || rank.equals("mythic")) {
            return RankTier.APEX;
        }
        if (player.hasPermission("vetroforge.rank.obsidian") || rank.equals("obsidian")) return RankTier.OBSIDIAN;
        if (player.hasPermission("vetroforge.rank.vanguard") || rank.equals("vanguard")) return RankTier.VANGUARD;
        if (player.hasPermission("vetroforge.rank.ember") || rank.equals("ember")) return RankTier.EMBER;
        return RankTier.PLAYER;
    }

    private void giveDefaults(Player player, RankTier tier, KitType type) {
        List<ItemStack> items = new ArrayList<>();
        switch (type) {
            case STARTER -> {
                items.add(com.vetroforge.items.CustomItemUtil.makeArmorPiece(plugin, "divine", Material.CHAINMAIL_HELMET, com.vetroforge.items.Rarity.COMMON));
                items.add(com.vetroforge.items.CustomItemUtil.makeArmorPiece(plugin, "divine", Material.CHAINMAIL_CHESTPLATE, com.vetroforge.items.Rarity.COMMON));
                items.add(com.vetroforge.items.CustomItemUtil.makeArmorPiece(plugin, "divine", Material.CHAINMAIL_LEGGINGS, com.vetroforge.items.Rarity.COMMON));
                items.add(com.vetroforge.items.CustomItemUtil.makeArmorPiece(plugin, "divine", Material.CHAINMAIL_BOOTS, com.vetroforge.items.Rarity.COMMON));
                items.add(stoneClub());
                items.add(new ItemStack(Material.COOKED_BEEF, 32));
                items.add(plugin.getKeyCrateManager().createKeyItem(com.vetroforge.items.Rarity.RARE, 2));
                items.add(new ItemStack(Material.ENDER_PEARL, 16));
                items.add(new ItemStack(Material.TNT, 16));
                items.add(new ItemStack(Material.OBSIDIAN, 64));
            }
            case DAILY -> {
                items.add(named(Material.COOKED_BEEF, 16, "§6Daily Food"));
                items.add(named(Material.EXPERIENCE_BOTTLE, tier.ordinal() * 8 + 8, "§aDaily XP"));
                if (tier.ordinal() >= RankTier.EMBER.ordinal()) items.add(named(Material.GOLDEN_APPLE, 1, "§6Daily Apple"));
                if (tier.ordinal() >= RankTier.VANGUARD.ordinal()) items.add(named(Material.ENDER_PEARL, 8, "§5Daily Pearls"));
            }
            case WEEKLY -> {
                items.add(named(Material.OBSIDIAN, 32, "§5Weekly Obsidian"));
                items.add(named(Material.TNT, 32, "§cWeekly TNT"));
                if (tier.ordinal() >= RankTier.VANGUARD.ordinal()) items.add(plugin.getKeyCrateManager().createKeyItem(com.vetroforge.items.Rarity.RARE, 1));
                if (tier.ordinal() >= RankTier.OBSIDIAN.ordinal()) items.add(plugin.getKeyCrateManager().createKeyItem(com.vetroforge.items.Rarity.EPIC, 1));
            }
            case MONTHLY -> {
                if (tier.ordinal() >= RankTier.APEX.ordinal()) {
                    items.add(plugin.getKeyCrateManager().createKeyItem(com.vetroforge.items.Rarity.EPIC, 2));
                    items.add(plugin.getKeyCrateManager().createKeyItem(com.vetroforge.items.Rarity.LEGENDARY, 1));
                }
                if (tier.ordinal() >= RankTier.VETRO_PLUS.ordinal()) items.add(plugin.getKeyCrateManager().createKeyItem(com.vetroforge.items.Rarity.MYSTIC, 1));
            }
        }
        for (ItemStack item : items) {
            if (item == null || item.getType().isAir()) continue;
            player.getInventory().addItem(item).values().forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
        if (type == KitType.MONTHLY && plugin.getBoosterManager() != null) {
            // Vanguard+ monatlicher Booster-Nachschub, skaliert mit dem Rang.
            if (tier.ordinal() >= RankTier.VANGUARD.ordinal()) {
                plugin.getBoosterManager().giveVoucher(player, com.vetroforge.services.BoosterManager.Type.COIN, 1);
            }
            if (tier.ordinal() >= RankTier.OBSIDIAN.ordinal()) {
                plugin.getBoosterManager().giveVoucher(player, com.vetroforge.services.BoosterManager.Type.RAID, 1);
            }
            if (tier.ordinal() >= RankTier.APEX.ordinal()) {
                plugin.getBoosterManager().giveVoucher(player, com.vetroforge.services.BoosterManager.Type.COIN, 1);
            }
            if (tier.ordinal() >= RankTier.VETRO_PLUS.ordinal()) {
                plugin.getBoosterManager().giveVoucher(player, com.vetroforge.services.BoosterManager.Type.RAID, 1);
            }
        }
    }

    private void runConfiguredCommands(Player player, RankTier tier, KitType type) {
        FileConfiguration config = plugin.getConfig();
        List<String> commands = new ArrayList<>();
        commands.addAll(config.getStringList("rankKits." + type.key + ".commands"));
        commands.addAll(config.getStringList("rankKits." + type.key + "." + tier.key + ".commands"));
        for (String raw : commands) {
            String command = raw.replace("%player%", player.getName()).replace("%uuid%", player.getUniqueId().toString());
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }

        ConfigurationSection items = config.getConfigurationSection("rankKits." + type.key + "." + tier.key + ".items");
        if (items == null) return;
        for (String id : items.getKeys(false)) {
            Material material = Material.matchMaterial(items.getString(id + ".material", "STONE"));
            if (material == null) continue;
            int amount = Math.max(1, items.getInt(id + ".amount", 1));
            String name = ChatColor.translateAlternateColorCodes('&', items.getString(id + ".name", material.name()));
            player.getInventory().addItem(named(material, amount, name)).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private void initTables() {
        if (!mysqlEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = connection(); Statement statement = connection.createStatement()) {
                statement.executeUpdate("""
                        CREATE TABLE IF NOT EXISTS rank_kit_claims (
                          uuid VARCHAR(36) NOT NULL,
                          kit_type VARCHAR(32) NOT NULL,
                          next_claim BIGINT NOT NULL,
                          PRIMARY KEY (uuid, kit_type)
                        ) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4
                        """);
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[RankKits] MySQL init fehlgeschlagen: " + throwable.getMessage());
            }
        });
    }

    private long nextClaim(UUID uuid, KitType type) {
        if (!mysqlEnabled()) return 0L;
        try (Connection connection = connection();
             PreparedStatement statement = connection.prepareStatement("SELECT next_claim FROM rank_kit_claims WHERE uuid=? AND kit_type=?")) {
            statement.setString(1, uuid.toString());
            statement.setString(2, type.key);
            try (ResultSet result = statement.executeQuery()) {
                return result.next() ? result.getLong("next_claim") : 0L;
            }
        } catch (Throwable throwable) {
            plugin.getLogger().warning("[RankKits] Cooldown laden fehlgeschlagen: " + throwable.getMessage());
            return Long.MAX_VALUE;
        }
    }

    private void setNextClaim(UUID uuid, KitType type, long nextClaim) {
        if (!mysqlEnabled()) return;
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try (Connection connection = connection();
                 PreparedStatement statement = connection.prepareStatement("""
                         INSERT INTO rank_kit_claims (uuid, kit_type, next_claim)
                         VALUES (?, ?, ?)
                         ON DUPLICATE KEY UPDATE next_claim=VALUES(next_claim)
                         """)) {
                statement.setString(1, uuid.toString());
                statement.setString(2, type.key);
                statement.setLong(3, nextClaim);
                statement.executeUpdate();
            } catch (Throwable throwable) {
                plugin.getLogger().warning("[RankKits] Cooldown speichern fehlgeschlagen: " + throwable.getMessage());
            }
        });
    }

    private ItemStack named(Material material, int amount, String name) {
        ItemStack item = new ItemStack(material, Math.max(1, Math.min(64, amount)));
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            item.setItemMeta(meta);
        }
        return item;
    }

    private ItemStack stoneClub() {
        ItemStack item = com.vetroforge.items.CustomItemUtil.makeBattleHoe(plugin, com.vetroforge.items.Rarity.COMMON);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName("§7§lStone Club");
            List<String> lore = meta.hasLore() && meta.getLore() != null
                    ? new ArrayList<>(meta.getLore())
                    : new ArrayList<>();
            lore.add(0, "§8Starter Weapon");
            lore.add("§7Ein grober Start-Schlaeger fuer die ersten Fights.");
            meta.setLore(lore);
            item.setItemMeta(meta);
        }
        return item;
    }

    private String rankName(Player player) {
        try {
            return plugin.getVetroPermsManager() == null ? "Player" : plugin.getVetroPermsManager().getRank(player);
        } catch (Throwable ignored) {
            return "Player";
        }
    }

    private String formatDuration(long millis) {
        Duration duration = Duration.ofMillis(Math.max(0L, millis));
        long days = duration.toDays();
        long hours = duration.toHoursPart();
        long minutes = duration.toMinutesPart();
        if (days > 0) return days + "d " + hours + "h";
        if (hours > 0) return hours + "h " + minutes + "m";
        return Math.max(1, minutes) + "m";
    }

    private boolean mysqlEnabled() {
        return plugin.getConfig().getBoolean("mysql.enabled", false);
    }

    private Connection connection() throws Exception {
        FileConfiguration config = plugin.getConfig();
        String host = config.getString("mysql.host", "127.0.0.1");
        int port = config.getInt("mysql.port", 3306);
        String database = config.getString("mysql.database", "VF");
        String user = config.getString("mysql.user", "VF");
        String password = config.getString("mysql.password", "");
        String url = "jdbc:mysql://" + host + ":" + port + "/" + database
                + "?useSSL=false&autoReconnect=true&serverTimezone=UTC";
        return DriverManager.getConnection(url, user, password == null ? "" : password);
    }

    public enum KitType {
        STARTER("starter", "Starter", 0L),
        DAILY("daily", "Daily", 24L * 60L * 60L * 1000L),
        WEEKLY("weekly", "Weekly", 7L * 24L * 60L * 60L * 1000L),
        MONTHLY("monthly", "Monthly", 30L * 24L * 60L * 60L * 1000L);

        public final String key;
        public final String display;
        public final long cooldownMillis;

        KitType(String key, String display, long cooldownMillis) {
            this.key = key;
            this.display = display;
            this.cooldownMillis = cooldownMillis;
        }
    }

    public enum RankTier {
        PLAYER("player", "Player"),
        EMBER("ember", "Ember"),
        VANGUARD("vanguard", "Vanguard"),
        OBSIDIAN("obsidian", "Obsidian"),
        APEX("apex", "Apex"),
        VETRO_PLUS("vetroplus", "Vetro+");

        public final String key;
        public final String display;

        RankTier(String key, String display) {
            this.key = key;
            this.display = display;
        }

        boolean allows(KitType type) {
            return switch (type) {
                case STARTER -> true;
                case DAILY -> this.ordinal() >= EMBER.ordinal();
                case WEEKLY -> this.ordinal() >= VANGUARD.ordinal();
                case MONTHLY -> this.ordinal() >= VANGUARD.ordinal();
            };
        }
    }
}
