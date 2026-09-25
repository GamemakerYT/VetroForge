package com.vetroforge.crates;

import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

public final class CrateHistoryService {

    public record Entry(long time, String crateId, String itemName, String material, int amount) {}

    private final File file;
    private final YamlConfiguration yaml;

    public CrateHistoryService(Plugin plugin) {
        this.file = new File(plugin.getDataFolder(), "crate-history.yml");
        this.yaml = YamlConfiguration.loadConfiguration(file);
    }

    public void record(Player player, String crateId, ItemStack item) {
        if (player == null || item == null) return;
        String root = "players." + player.getUniqueId() + ".entries";
        List<String> entries = yaml.getStringList(root);
        entries.add(encode(crateId, item));
        int max = 80;
        if (entries.size() > max) entries = new ArrayList<>(entries.subList(entries.size() - max, entries.size()));
        yaml.set(root, entries);
        yaml.set("players." + player.getUniqueId() + ".name", player.getName());
        save();
    }

    public List<Entry> entries(java.util.UUID uuid, int limit) {
        List<String> raw = yaml.getStringList("players." + uuid + ".entries");
        if (raw.isEmpty()) return Collections.emptyList();

        List<Entry> out = new ArrayList<>();
        for (int i = raw.size() - 1; i >= 0 && out.size() < limit; i--) {
            Entry entry = decode(raw.get(i));
            if (entry != null) out.add(entry);
        }
        return out;
    }

    private String encode(String crateId, ItemStack item) {
        return Instant.now().toEpochMilli()
                + "|" + safe(crateId)
                + "|" + safe(displayName(item))
                + "|" + item.getType().name()
                + "|" + item.getAmount();
    }

    private Entry decode(String line) {
        String[] parts = line.split("\\|", 5);
        if (parts.length != 5) return null;
        try {
            return new Entry(
                    Long.parseLong(parts[0]),
                    parts[1],
                    parts[2],
                    parts[3],
                    Integer.parseInt(parts[4])
            );
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    private String displayName(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        if (meta != null && meta.hasDisplayName()) return ChatColor.stripColor(meta.getDisplayName());
        String lower = item.getType().name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String safe(String input) {
        return input == null ? "unknown" : input.replace("|", "/");
    }

    private void save() {
        try {
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }
}
