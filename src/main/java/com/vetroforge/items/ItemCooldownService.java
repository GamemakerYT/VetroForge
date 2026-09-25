package com.vetroforge.items;

import com.vetroforge.FactionsPlugin;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class ItemCooldownService {

    private final FactionsPlugin plugin;
    private final Map<String, Long> expires = new ConcurrentHashMap<>();

    public ItemCooldownService(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public long remainingMs(Player player, String ability) {
        return Math.max(0L, expires.getOrDefault(key(player, ability), 0L) - System.currentTimeMillis());
    }

    public long use(Player player, ItemStack item, String ability, long baseMs) {
        long adjusted = Math.max(250L, Math.round(baseMs * (1.0 - CustomItemUtil.cooldownRollOf(plugin, item))));
        expires.put(key(player, ability), System.currentTimeMillis() + adjusted);
        return adjusted;
    }

    private String key(Player player, String ability) {
        UUID uuid = player.getUniqueId();
        return uuid + ":" + ability.toLowerCase();
    }
}
