// src/main/java/com/vetroforge/manager/VetroPermsManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionAttachment;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/** Internes Permissions- und Rangsystem. */
public class VetroPermsManager {
    private final FactionsPlugin plugin;
    private final Map<UUID, String> rankByPlayer = new ConcurrentHashMap<>();
    private final Map<UUID, PermissionAttachment> attachments = new ConcurrentHashMap<>();
    private final Map<String, Rank> ranks = new LinkedHashMap<>();

    /** Rang-Definition */
    public static class Rank {
        public final String name;
        public final String prefix;
        public final ChatColor color;
        public final List<String> permissions;

        public Rank(String name, String prefix, ChatColor color, List<String> permissions) {
            this.name = name;
            this.prefix = prefix == null ? "" : prefix;
            this.color = color == null ? ChatColor.GRAY : color;
            this.permissions = permissions == null ? Collections.emptyList() : new ArrayList<>(permissions);
        }
    }

    public VetroPermsManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    /* ===================== Public API ===================== */

    /** Alle Ränge (read-only), z.B. für TablistManager. */
    public Map<String, Rank> getRanks() {
        return Collections.unmodifiableMap(ranks);
    }

    /** Nur die Rang-IDs (falls du sie brauchst). */
    public Set<String> getRankIds() {
        return Collections.unmodifiableSet(ranks.keySet());
    }

    /** Rang setzen und in config speichern. */
    public void setRank(Player player, String rank) {
        if (!ranks.containsKey(rank)) throw new IllegalArgumentException("Unbekannter Rang: " + rank);
        String valid = persistLocalRank(player.getUniqueId(), rank);
        syncCentralRank(player.getUniqueId(), valid);
        applyToPlayer(player);
        plugin.onRankChanged(player);
    }

    /** Rang per UUID setzen, z.B. aus Web/API fuer Offline-Spieler. */
    public String setRank(UUID uuid, String rank) {
        if (uuid == null) throw new IllegalArgumentException("UUID fehlt");
        String valid = findRankId(rank);
        if (!ranks.containsKey(valid)) throw new IllegalArgumentException("Unbekannter Rang: " + rank);
        persistLocalRank(uuid, valid);
        syncCentralRank(uuid, valid);
        Player online = plugin.getServer().getPlayer(uuid);
        if (online != null) {
            applyToPlayer(online);
            plugin.onRankChanged(online);
        }
        return valid;
    }

    /** Rang per UUID lesen, auch wenn der Spieler offline ist. */
    public String getRank(UUID uuid) {
        if (uuid == null) return defaultRank();
        String centralRank = centralRank(uuid);
        if (centralRank != null) {
            String localRank = localRank(uuid);
            if (isElevated(localRank) && isDefaultRank(centralRank)) {
                rankByPlayer.put(uuid, localRank);
                syncCentralRank(uuid, localRank);
                return localRank;
            }
            persistLocalRank(uuid, centralRank);
            return centralRank;
        }
        return localRank(uuid);
    }

    /** Rang eines Spielers (Default: Member). */
    public String getRank(Player player) {
        UUID uuid = player.getUniqueId();
        String localRank = localRank(uuid);
        String centralRank = centralRank(uuid);
        if (centralRank != null) {
            if (isElevated(localRank) && isDefaultRank(centralRank)) {
                rankByPlayer.put(uuid, localRank);
                syncCentralRank(uuid, localRank);
                return localRank;
            }
            persistLocalRank(uuid, centralRank);
            return centralRank;
        }
        return localRank;
    }

    /** Farbiges Prefix für Chat/Tab/Nametag. */
    public String getPrefix(Player player) {
        Rank r = ranks.get(getRank(player));
        return r != null ? r.prefix + "§r" : "";
    }

    public Component getRankComponent(Player player) {
        Rank rank = ranks.get(getRank(player));
        return Component.text(rank == null ? "Player" : rank.name, named(rank == null ? ChatColor.GRAY : rank.color));
    }

    public Component getNameTagPrefix(Player player) {
        Rank rank = ranks.get(getRank(player));
        if (rank == null || "Member".equalsIgnoreCase(rank.name)) return Component.empty();
        return Component.text(rank.name, named(rank.color))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY));
    }

    public Component getTabName(Player player) {
        return getNameTagPrefix(player)
                .append(Component.text(player.getName(), named(getColor(player))))
                .append(levelSuffix(player));
    }

    public Component getLevelSuffix(Player player) {
        return levelSuffix(player);
    }

    /** Namensfarbe (Default Grau). */
    public ChatColor getColor(Player player) {
        Rank r = ranks.get(getRank(player));
        return r != null ? r.color : ChatColor.GRAY;
    }

    /** Permissions aller Online-Spieler anwenden. */
    public void applyAll() {
        for (Player p : plugin.getServer().getOnlinePlayers()) applyToPlayer(p);
    }

    /** Permissions eines Spielers anwenden. */
    public void applyToPlayer(Player p) {
        Rank r = ranks.get(getRank(p));
        if (r == null) return;
        PermissionAttachment attachment = attachments.computeIfAbsent(p.getUniqueId(), ignored -> p.addAttachment(plugin));
        new HashSet<>(attachment.getPermissions().keySet()).forEach(attachment::unsetPermission);
        for (String node : r.permissions) {
            try {
                if ("*".equals(node)) {
                    for (Permission permission : plugin.getServer().getPluginManager().getPermissions()) {
                        attachment.setPermission(permission.getName(), true);
                    }
                    attachment.setPermission("*", true);
                    continue;
                }
                attachment.setPermission(node, true);
                if (node.endsWith(".*")) {
                    String prefix = node.substring(0, node.length() - 1).toLowerCase(Locale.ROOT);
                    for (Permission permission : plugin.getServer().getPluginManager().getPermissions()) {
                        String name = permission.getName().toLowerCase(Locale.ROOT);
                        if (name.startsWith(prefix)) attachment.setPermission(permission.getName(), true);
                    }
                }
            } catch (Throwable ignored) {}
        }
        p.recalculatePermissions();
    }

    /** Konfiguration neu laden. */
    public void reload() {
        ranks.clear();
        rankByPlayer.clear();
        loadFromConfig();
    }

    /* ===================== Intern ===================== */

    private void loadFromConfig() {
        // Ränge
        ConfigurationSection rs = plugin.getConfig().getConfigurationSection("perms.ranks");
        if (rs != null) {
            for (String rankKey : rs.getKeys(false)) {
                String base = "perms.ranks." + rankKey;
                String prefix = plugin.getConfig().getString(base + ".prefix", "");
                String colorStr = String.valueOf(plugin.getConfig().getString(base + ".color", "GRAY"));
                ChatColor color = parseColor(colorStr);
                List<String> permissions = plugin.getConfig().getStringList(base + ".permissions");
                ranks.put(rankKey, new Rank(rankKey, prefix, color, permissions));
            }
        }

        // Defaults, falls nichts konfiguriert
        if (ranks.isEmpty()) {
            ranks.put("Admin",   new Rank("Admin",   "[Admin] ", ChatColor.DARK_RED, List.of("vetroforge.*")));
            ranks.put("Content", new Rank("Content", "[Content] ", ChatColor.BLUE, List.of("vetroforge.team", "vetroforge.staff.gui", "vetroforge.event.admin", "vetroforge.story.admin")));
            ranks.put("Mod",     new Rank("Mod",     "[Mod] ",   ChatColor.DARK_AQUA, List.of("vetroforge.staff")));
            ranks.put("Builder", new Rank("Builder", "[Builder] ", ChatColor.AQUA, List.of("vetroforge.build")));
            ranks.put("Support", new Rank("Support", "[Support] ", ChatColor.GREEN, List.of("vetroforge.support")));
            ranks.put("Vetro+", new Rank("Vetro+", "[Vetro+] ", ChatColor.RED, List.of(
                    "vetroforge.rank.vetroplus", "vetroforge.rank.apex", "vetroforge.rank.obsidian",
                    "vetroforge.rank.vanguard", "vetroforge.rank.ember", "vetroforge.pv",
                    "vetroforge.kit.daily", "vetroforge.kit.weekly", "vetroforge.kit.monthly")));
            ranks.put("Apex", new Rank("Apex", "[Apex] ", ChatColor.LIGHT_PURPLE, List.of(
                    "vetroforge.rank.apex", "vetroforge.rank.obsidian", "vetroforge.rank.vanguard",
                    "vetroforge.rank.ember", "vetroforge.pv", "vetroforge.kit.daily",
                    "vetroforge.kit.weekly", "vetroforge.kit.monthly")));
            ranks.put("Obsidian", new Rank("Obsidian", "[Obsidian] ", ChatColor.DARK_PURPLE, List.of(
                    "vetroforge.rank.obsidian", "vetroforge.rank.vanguard", "vetroforge.rank.ember",
                    "vetroforge.pv", "vetroforge.kit.daily", "vetroforge.kit.weekly")));
            ranks.put("Vanguard", new Rank("Vanguard", "[Vanguard] ", ChatColor.GOLD, List.of(
                    "vetroforge.rank.vanguard", "vetroforge.rank.ember", "vetroforge.pv",
                    "vetroforge.kit.daily", "vetroforge.kit.weekly")));
            ranks.put("Ember", new Rank("Ember", "[Ember] ", ChatColor.YELLOW, List.of(
                    "vetroforge.rank.ember", "vetroforge.pv", "vetroforge.kit.daily")));
            ranks.put("Member",  new Rank("Member",  "",         ChatColor.GRAY,     List.of("vetroforge.member")));
        }

        // User-Ränge laden
        ConfigurationSection us = plugin.getConfig().getConfigurationSection("perms.users");
        if (us != null) {
            for (String key : us.getKeys(false)) {
                try {
                    UUID id = UUID.fromString(key);
                    String rn = us.getString(key + ".rank", "Member");
                    rankByPlayer.put(id, ranks.containsKey(rn) ? rn : "Member");
                } catch (IllegalArgumentException ignored) { /* kein UUID */ }
            }
        }
    }

    private String centralRank(UUID uuid) {
        try {
            Class<?> api = centralClass("com.ja.vetroperms.api.VetroPermsAPI");
            if (!(boolean) api.getMethod("isReady").invoke(null)) return null;
            Object service = api.getMethod("get").invoke(null);
            Object rank = service.getClass().getMethod("getRank", UUID.class).invoke(service, uuid);
            return findRankId(switch (((Enum<?>) rank).name()) {
                case "ADMIN" -> "Admin";
                case "CONTENT" -> "Content";
                case "MODERATOR" -> "Mod";
                case "BUILDER" -> "Builder";
                case "SUPPORTER" -> "Support";
                case "EMBER" -> "Ember";
                case "VANGUARD" -> "Vanguard";
                case "OBSIDIAN" -> "Obsidian";
                case "APEX" -> "Apex";
                case "VETRO_PLUS" -> "Vetro+";
                default -> "Member";
            });
        } catch (Throwable ignored) {
            return null;
        }
    }

    private String localRank(UUID uuid) {
        String cached = rankByPlayer.get(uuid);
        if (cached != null && ranks.containsKey(cached)) return cached;
        String configured = plugin.getConfig().getString("perms.users." + uuid + ".rank", "Member");
        String rank = ranks.containsKey(configured) ? configured : findRankId(configured);
        rankByPlayer.put(uuid, rank);
        return rank;
    }

    private String persistLocalRank(UUID uuid, String rank) {
        String valid = ranks.containsKey(rank) ? rank : findRankId(rank);
        rankByPlayer.put(uuid, valid);
        plugin.getConfig().set("perms.users." + uuid + ".rank", valid);
        plugin.saveConfig();
        return valid;
    }

    private boolean isDefaultRank(String rank) {
        return rank == null || rank.equalsIgnoreCase("Member") || rank.equalsIgnoreCase("Player");
    }

    private boolean isElevated(String rank) {
        return rank != null && !isDefaultRank(rank);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void syncCentralRank(UUID uuid, String rank) {
        try {
            Class<?> api = centralClass("com.ja.vetroperms.api.VetroPermsAPI");
            if (!(boolean) api.getMethod("isReady").invoke(null)) return;
            Object service = api.getMethod("get").invoke(null);
            Class<? extends Enum> rankType = (Class<? extends Enum>) centralClass("com.ja.vetroperms.api.Rank");
            Enum centralRank = Enum.valueOf(rankType, switch (rank.toLowerCase(Locale.ROOT)) {
                case "admin", "coadmin", "co-admin" -> "ADMIN";
                case "mod", "moderator" -> "MODERATOR";
                case "builder" -> "BUILDER";
                case "support", "supporter" -> "SUPPORTER";
                case "ember" -> "EMBER";
                case "vanguard" -> "VANGUARD";
                case "obsidian" -> "OBSIDIAN";
                case "apex", "mythic" -> "APEX";
                case "vetro+", "vetroplus", "vetro_plus" -> "VETRO_PLUS";
                default -> "PLAYER";
            });
            service.getClass().getMethod("setRank", UUID.class, rankType).invoke(service, uuid, centralRank);
        } catch (Throwable ignored) {
            // The local config remains a usable fallback when VetroPerms is absent.
        }
    }

    private Class<?> centralClass(String name) throws ClassNotFoundException {
        var central = plugin.getServer().getPluginManager().getPlugin("VetroPerms");
        if (central == null || !central.isEnabled()) throw new ClassNotFoundException(name);
        return Class.forName(name, true, central.getClass().getClassLoader());
    }

    private String findRankId(String preferred) {
        for (String id : ranks.keySet()) {
            if (id.equalsIgnoreCase(preferred)) return id;
        }
        return defaultRank();
    }

    private String defaultRank() {
        return ranks.containsKey("Member") ? "Member" : ranks.keySet().stream().findFirst().orElse("Member");
    }

    private ChatColor parseColor(String s) {
        if (s == null) return ChatColor.GRAY;
        try { return ChatColor.valueOf(s.trim().toUpperCase(Locale.ROOT)); }
        catch (Exception e) { return ChatColor.GRAY; }
    }

    private NamedTextColor named(ChatColor color) {
        if (color == null) return NamedTextColor.GRAY;
        return switch (color) {
            case BLACK -> NamedTextColor.BLACK;
            case DARK_BLUE -> NamedTextColor.DARK_BLUE;
            case DARK_GREEN -> NamedTextColor.DARK_GREEN;
            case DARK_AQUA -> NamedTextColor.DARK_AQUA;
            case DARK_RED -> NamedTextColor.DARK_RED;
            case DARK_PURPLE -> NamedTextColor.DARK_PURPLE;
            case GOLD -> NamedTextColor.GOLD;
            case GRAY -> NamedTextColor.GRAY;
            case DARK_GRAY -> NamedTextColor.DARK_GRAY;
            case BLUE -> NamedTextColor.BLUE;
            case GREEN -> NamedTextColor.GREEN;
            case AQUA -> NamedTextColor.AQUA;
            case RED -> NamedTextColor.RED;
            case LIGHT_PURPLE -> NamedTextColor.LIGHT_PURPLE;
            case YELLOW -> NamedTextColor.YELLOW;
            case WHITE -> NamedTextColor.WHITE;
            default -> NamedTextColor.GRAY;
        };
    }

    private Component levelSuffix(Player player) {
        int level = 1;
        try {
            if (plugin.getStatsManager() != null) {
                level = plugin.getStatsManager().getLevel(player);
            } else if (plugin.getPrestigeManager() != null) {
                level = plugin.getPrestigeManager().getLevel(player);
            }
        } catch (Throwable ignored) {}
        try {
            if (plugin.getLevelProgressionService() != null) {
                level = Math.min(level, plugin.getLevelProgressionService().maxLevel());
            }
        } catch (Throwable ignored) {}
        level = Math.max(1, Math.min(10, level));
        NamedTextColor color = switch (level) {
            case 1, 2 -> NamedTextColor.WHITE;
            case 3, 4 -> NamedTextColor.GREEN;
            case 5, 6 -> NamedTextColor.AQUA;
            case 7, 8 -> NamedTextColor.BLUE;
            case 9 -> NamedTextColor.LIGHT_PURPLE;
            default -> NamedTextColor.GOLD;
        };
        return Component.text(" [" + level + "]", color);
    }
}
