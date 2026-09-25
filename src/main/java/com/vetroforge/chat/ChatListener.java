// src/main/java/com/vetroforge/chat/ChatListener.java
package com.vetroforge.chat;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.display.NametagTabManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.AsyncPlayerChatEvent;
import org.bukkit.inventory.ItemStack;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class ChatListener implements Listener {
    private static final String ITEM_TOKEN = "[item]";

    private final FactionsPlugin plugin;
    private final Map<String, Long> mentionCooldowns = new ConcurrentHashMap<>();

    public ChatListener(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void onChat(AsyncPlayerChatEvent event) {
        Player player = event.getPlayer();
        var manager = plugin.getChatChannelManager();
        if (manager == null) return;

        var channel = manager.get(player);
        List<Player> recipients = new ArrayList<>(manager.recipients(player, channel));
        String rawMessage = event.getMessage();
        List<Player> mentioned = mentionedPlayers(rawMessage, recipients);
        event.setCancelled(true);

        String channelName = channel.name();
        Bukkit.getScheduler().runTask(plugin, () -> {
            Component message = buildMessage(player, channelName, rawMessage, mentioned);
            for (Player recipient : recipients) {
                recipient.sendMessage(message);
            }
            notifyMentions(player, mentioned);
        });
    }

    private Component buildMessage(Player player, String channel, String rawMessage, List<Player> mentioned) {
        String rankKey = resolveRankKey(player);
        TextColor rankColor = rankColor(rankKey);

        Component prefix = Component.empty();
        if (!"GLOBAL".equalsIgnoreCase(channel)) {
            prefix = Component.text(channelLabel(channel), channelColor(channel))
                    .append(Component.text(" • ", NamedTextColor.DARK_GRAY));
        }

        return prefix
                .append(Component.text(rankName(rankKey), rankColor))
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text(player.getName(), rankColor)
                        .hoverEvent(net.kyori.adventure.text.event.HoverEvent.showText(hover(player, rankKey))))
                .append(Component.text(" » ", NamedTextColor.DARK_GRAY))
                .append(renderMessage(player, rawMessage, mentioned));
    }

    private Component renderMessage(Player player, String rawMessage, List<Player> mentioned) {
        Component out = Component.empty();
        String remaining = rawMessage == null ? "" : rawMessage;
        while (!remaining.isEmpty()) {
            int bestIndex = -1;
            String bestToken = null;
            boolean bestIsItem = false;
            for (Player target : mentioned) {
                String token = "@" + target.getName();
                int index = indexOfIgnoreCase(remaining, token);
                if (index >= 0 && (bestIndex < 0 || index < bestIndex)) {
                    bestIndex = index;
                    bestToken = token;
                    bestIsItem = false;
                }
            }
            int itemIndex = indexOfIgnoreCase(remaining, ITEM_TOKEN);
            if (itemIndex >= 0 && (bestIndex < 0 || itemIndex < bestIndex)) {
                bestIndex = itemIndex;
                bestToken = ITEM_TOKEN;
                bestIsItem = true;
            }
            if (bestIndex < 0 || bestToken == null) break;
            out = out.append(Component.text(remaining.substring(0, bestIndex), NamedTextColor.WHITE));
            out = out.append(bestIsItem
                    ? heldItemComponent(player)
                    : Component.text(remaining.substring(bestIndex, bestIndex + bestToken.length()), NamedTextColor.AQUA));
            remaining = remaining.substring(bestIndex + bestToken.length());
        }
        return out.append(Component.text(remaining, NamedTextColor.WHITE));
    }

    /** Ersetzt den [item]-Token durch das aktuell in der Haupthand gehaltene Item inkl. echtem Vanilla-Hover (Lore, Runen usw). */
    private Component heldItemComponent(Player player) {
        ItemStack item = player.getInventory().getItemInMainHand();
        if (item.getType().isAir()) {
            return Component.text(ITEM_TOKEN, NamedTextColor.WHITE);
        }
        Component name = item.getItemMeta() != null && item.getItemMeta().hasDisplayName()
                ? item.getItemMeta().displayName()
                : Component.translatable(item.translationKey());
        return Component.text("[", NamedTextColor.DARK_GRAY)
                .append(name.colorIfAbsent(NamedTextColor.WHITE))
                .append(Component.text("]", NamedTextColor.DARK_GRAY))
                .hoverEvent(item.asHoverEvent());
    }

    private List<Player> mentionedPlayers(String rawMessage, List<Player> recipients) {
        String message = rawMessage == null ? "" : rawMessage.toLowerCase(Locale.ROOT);
        Set<Player> matches = new LinkedHashSet<>();
        for (Player candidate : recipients) {
            if (candidate == null) continue;
            String token = "@" + candidate.getName().toLowerCase(Locale.ROOT);
            if (message.contains(token)) matches.add(candidate);
        }
        return new ArrayList<>(matches);
    }

    private void notifyMentions(Player sender, List<Player> mentioned) {
        MentionMode mode = mentionMode();
        if (mode == MentionMode.OFF) return;
        for (Player target : mentioned) {
            if (target.equals(sender)) continue;
            if (isMentionCoolingDown(sender.getUniqueId(), target.getUniqueId())) continue;
            if (mode.actionbar) {
                target.sendActionBar(Component.text(sender.getName(), NamedTextColor.AQUA)
                        .append(Component.text(" hat dich erwaehnt", NamedTextColor.GRAY)));
            }
            if (mode.sound) {
                try {
                    target.playSound(target.getLocation(), Sound.BLOCK_NOTE_BLOCK_PLING, 0.8f, 1.6f);
                } catch (Throwable ignored) {
                }
            }
        }
    }

    private boolean isMentionCoolingDown(UUID sender, UUID target) {
        long cooldownMillis = Math.max(0L, plugin.getConfig().getLong("chat.mentions.cooldownSeconds", 8L)) * 1000L;
        if (cooldownMillis <= 0L) return false;
        long now = System.currentTimeMillis();
        String key = sender + ":" + target;
        Long last = mentionCooldowns.get(key);
        if (last != null && now - last < cooldownMillis) return true;
        mentionCooldowns.put(key, now);
        if (mentionCooldowns.size() > 512) {
            mentionCooldowns.entrySet().removeIf(entry -> now - entry.getValue() > cooldownMillis * 4L);
        }
        return false;
    }

    private MentionMode mentionMode() {
        String raw = plugin.getConfig().getString("chat.mentions.mode", "sound+actionbar");
        if (raw == null) return MentionMode.SOUND_ACTIONBAR;
        return switch (raw.trim().toLowerCase(Locale.ROOT)) {
            case "off", "false", "disabled" -> MentionMode.OFF;
            case "sound" -> MentionMode.SOUND;
            case "sound+actionbar", "actionbar+sound", "both" -> MentionMode.SOUND_ACTIONBAR;
            default -> MentionMode.SOUND_ACTIONBAR;
        };
    }

    private int indexOfIgnoreCase(String text, String search) {
        return text.toLowerCase(Locale.ROOT).indexOf(search.toLowerCase(Locale.ROOT));
    }

    private static final TextColor BRAND_BLUE = TextColor.color(0x7fd4ff);

    private Component hover(Player player, String rankKey) {
        Component divider = Component.text("▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬▬", NamedTextColor.DARK_GRAY);
        return divider
                .append(Component.newline())
                .append(Component.text("Vetro", BRAND_BLUE)).append(Component.text("Forge", NamedTextColor.WHITE))
                .append(Component.text(" • ", NamedTextColor.DARK_GRAY))
                .append(Component.text(player.getName(), rankColor(rankKey)))
                .append(Component.newline())
                .append(Component.text("✦ Rank: ", NamedTextColor.GRAY))
                .append(Component.text(rankName(rankKey), rankColor(rankKey)))
                .append(Component.newline())
                .append(Component.text("⬆ Level: ", NamedTextColor.GRAY))
                .append(Component.text(level(player), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("⚔ Faction: ", NamedTextColor.GRAY))
                .append(Component.text(faction(player), NamedTextColor.WHITE))
                .append(Component.newline())
                .append(Component.text("★ Prestige: ", NamedTextColor.GRAY))
                .append(Component.text(prestige(player), NamedTextColor.GOLD))
                .append(Component.newline())
                .append(Component.text("☠ K/D: ", NamedTextColor.GRAY))
                .append(Component.text(kd(player), NamedTextColor.RED))
                .append(Component.newline())
                .append(divider);
    }

    private String rankName(String rankKey) {
        return switch (rankKey) {
            case "ADMIN" -> "Admin";
            case "COADMIN" -> "Co-Admin";
            case "MOD" -> "Mod";
            case "BUILDER" -> "Builder";
            case "SUPPORT" -> "Support";
            default -> "Spieler";
        };
    }

    private TextColor rankColor(String rankKey) {
        return switch (rankKey) {
            case "ADMIN" -> NamedTextColor.DARK_RED;
            case "COADMIN" -> NamedTextColor.RED;
            case "MOD" -> NamedTextColor.GOLD;
            case "BUILDER" -> NamedTextColor.GREEN;
            case "SUPPORT" -> NamedTextColor.AQUA;
            default -> NamedTextColor.GRAY;
        };
    }

    private String channelLabel(String channel) {
        return switch (channel.toUpperCase(Locale.ROOT)) {
            case "LOBBY" -> "Lobby";
            case "FACTION" -> "Faction";
            default -> "";
        };
    }

    private TextColor channelColor(String channel) {
        return switch (channel.toUpperCase(Locale.ROOT)) {
            case "LOBBY" -> NamedTextColor.AQUA;
            case "FACTION" -> NamedTextColor.GREEN;
            default -> NamedTextColor.GRAY;
        };
    }

    private String level(Player player) {
        try {
            if (plugin.getStatsManager() != null) return String.valueOf(plugin.getStatsManager().getLevel(player));
        } catch (Throwable ignored) {
        }
        return "1";
    }

    private String prestige(Player player) {
        try {
            if (plugin.getStatsManager() != null) return String.valueOf(plugin.getStatsManager().getPrestigeLevel(player));
        } catch (Throwable ignored) {
        }
        return "0";
    }

    private String faction(Player player) {
        try {
            String faction = plugin.getFactionManager() == null ? null
                    : plugin.getFactionManager().getFactionOf(player.getUniqueId());
            return faction == null || faction.isBlank() ? "-" : faction;
        } catch (Throwable ignored) {
            return "-";
        }
    }

    private String kd(Player player) {
        try {
            if (plugin.getStatsManager() == null) return "0.00";
            int kills = plugin.getStatsManager().getKills(player.getUniqueId());
            int deaths = plugin.getStatsManager().getDeaths(player.getUniqueId());
            double ratio = deaths <= 0 ? kills : kills / (double) deaths;
            return String.format(Locale.US, "%.2f", ratio);
        } catch (Throwable ignored) {
            return "0.00";
        }
    }

    private String resolveRankKey(Player player) {
        try {
            if (plugin.getVetroPermsManager() != null) {
                String rank = plugin.getVetroPermsManager().getRank(player);
                if (rank != null && !rank.isBlank()) return NametagTabManager.normalizeRank(rank);
            }
        } catch (Throwable ignored) {
        }
        return NametagTabManager.primaryRankFor(player);
    }

    private enum MentionMode {
        OFF(false, false),
        SOUND(true, false),
        SOUND_ACTIONBAR(true, true);

        private final boolean sound;
        private final boolean actionbar;

        MentionMode(boolean sound, boolean actionbar) {
            this.sound = sound;
            this.actionbar = actionbar;
        }
    }
}
