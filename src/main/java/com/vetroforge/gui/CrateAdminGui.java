package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class CrateAdminGui implements Listener {

    private final FactionsPlugin plugin;

    public CrateAdminGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void openCrates(Player player) {
        Inventory inv = Bukkit.createInventory(new CratesHolder(), 54, "§8✦ §6§lCrate Editor §8✦");
        fill(inv);
        int slot = 10;
        for (String id : crateIds()) {
            inv.setItem(slot++, item(icon(id), color(id) + id,
                    "§7Rewards: §f" + plugin.getConfig().getMapList("crates." + id + ".rewards").size(),
                    isLootdropOnly(id) ? "§5Nur Lootdrops" : "§7Quelle: Keys/NPC",
                    "",
                    "§aKlicken zum Bearbeiten"));
            if (slot % 9 == 8) slot += 2;
            if (slot >= 44) break;
        }
        inv.setItem(49, item(Material.BARRIER, "§cSchließen"));
        player.openInventory(inv);
    }

    private void openRewards(Player player, String crateId) {
        Inventory inv = Bukkit.createInventory(new RewardsHolder(crateId), 54,
                "§8✦ §6Crate §8| §f" + crateId + " §8✦");
        fill(inv);
        List<Map<?, ?>> rewards = plugin.getConfig().getMapList("crates." + crateId + ".rewards");
        for (int i = 0; i < Math.min(36, rewards.size()); i++) {
            Map<?, ?> reward = rewards.get(i);
            Material material = Material.matchMaterial(asString(reward.get("material"), "STONE"));
            if (material == null) material = Material.STONE;
            int amount = asInt(reward.get("amount"), 1);
            String name = asString(reward.get("name"), "&fReward");
            inv.setItem(i, item(material, ChatColor.translateAlternateColorCodes('&', name),
                    "§7Amount: §f" + amount,
                    "§7Index: §f" + i,
                    "",
                    "§eShift-Klick: Entfernen"));
        }
        inv.setItem(45, item(Material.ARROW, "§eZurück"));
        inv.setItem(49, item(Material.HOPPER, "§aItem aus Hand hinzufügen",
                "§7Nimmt dein Mainhand-Item",
                "§7und schreibt es in die Config."));
        inv.setItem(53, item(Material.REDSTONE, "§cReload / Speichern",
                "§7Config ist nach Änderungen gespeichert."));
        player.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof CratesHolder || holder instanceof RewardsHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;

        if (holder instanceof CratesHolder) {
            if (event.getRawSlot() == 49) {
                player.closeInventory();
                return;
            }
            ItemStack clicked = event.getCurrentItem();
            if (clicked == null || !clicked.hasItemMeta()) return;
            String crateId = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
            if (crateId != null && crateIds().contains(crateId)) openRewards(player, crateId);
            return;
        }

        RewardsHolder rewardsHolder = (RewardsHolder) holder;
        if (event.getRawSlot() == 45) {
            openCrates(player);
            return;
        }
        if (event.getRawSlot() == 49) {
            addHeldReward(player, rewardsHolder.crateId);
            openRewards(player, rewardsHolder.crateId);
            return;
        }
        if (event.isShiftClick() && event.getRawSlot() >= 0 && event.getRawSlot() < 36) {
            removeReward(player, rewardsHolder.crateId, event.getRawSlot());
            openRewards(player, rewardsHolder.crateId);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof CratesHolder
                || event.getView().getTopInventory().getHolder() instanceof RewardsHolder) {
            event.setCancelled(true);
        }
    }

    private void addHeldReward(Player player, String crateId) {
        ItemStack hand = player.getInventory().getItemInMainHand();
        if (hand == null || hand.getType().isAir()) {
            player.sendMessage("§cHalte ein Item in der Hand.");
            return;
        }
        List<Map<?, ?>> raw = plugin.getConfig().getMapList("crates." + crateId + ".rewards");
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (Map<?, ?> entry : raw) rewards.add(copy(entry));
        Map<String, Object> added = new LinkedHashMap<>();
        added.put("material", hand.getType().name());
        added.put("amount", hand.getAmount());
        if (hand.hasItemMeta() && hand.getItemMeta().hasDisplayName()) {
            added.put("name", hand.getItemMeta().getDisplayName().replace('§', '&'));
        }
        rewards.add(added);
        plugin.getConfig().set("crates." + crateId + ".rewards", rewards);
        plugin.saveConfig();
        player.sendMessage("§aReward zu §f" + crateId + " §ahinzugefügt.");
    }

    private void removeReward(Player player, String crateId, int index) {
        List<Map<?, ?>> raw = plugin.getConfig().getMapList("crates." + crateId + ".rewards");
        if (index < 0 || index >= raw.size()) return;
        List<Map<String, Object>> rewards = new ArrayList<>();
        for (Map<?, ?> entry : raw) rewards.add(copy(entry));
        rewards.remove(index);
        plugin.getConfig().set("crates." + crateId + ".rewards", rewards);
        plugin.saveConfig();
        player.sendMessage("§eReward aus §f" + crateId + " §eentfernt.");
    }

    private List<String> crateIds() {
        ConfigurationSection section = plugin.getConfig().getConfigurationSection("crates");
        if (section == null) return List.of();
        List<String> ids = new ArrayList<>();
        for (String id : section.getKeys(false)) {
            if (plugin.getConfig().isList("crates." + id + ".rewards")) ids.add(id);
        }
        ids.sort(String.CASE_INSENSITIVE_ORDER);
        return ids;
    }

    private boolean isLootdropOnly(String id) {
        return plugin.getConfig().getBoolean("crates." + id + ".lootdropOnly", false);
    }

    private Material icon(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "common" -> Material.NETHER_STAR;
            case "rare" -> Material.LAPIS_LAZULI;
            case "epic" -> Material.AMETHYST_SHARD;
            case "legendary" -> Material.BLAZE_ROD;
            case "mystic" -> Material.NETHER_STAR;
            default -> Material.CHEST;
        };
    }

    private String color(String id) {
        return switch (id.toLowerCase(Locale.ROOT)) {
            case "common" -> "§7";
            case "rare" -> "§9";
            case "epic" -> "§d";
            case "legendary" -> "§6";
            case "mystic" -> "§5";
            default -> "§f";
        };
    }

    private Map<String, Object> copy(Map<?, ?> source) {
        Map<String, Object> out = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : source.entrySet()) out.put(String.valueOf(entry.getKey()), entry.getValue());
        return out;
    }

    private int asInt(Object raw, int fallback) {
        if (raw instanceof Number number) return number.intValue();
        try { return Integer.parseInt(String.valueOf(raw)); } catch (Exception ignored) { return fallback; }
    }

    private String asString(Object raw, String fallback) {
        return raw == null ? fallback : String.valueOf(raw);
    }

    private void fill(Inventory inv) {
        ItemStack ice = item(Material.LIGHT_BLUE_STAINED_GLASS_PANE, " ");
        ItemStack frost = item(Material.CYAN_STAINED_GLASS_PANE, " ");
        ItemStack snow = item(Material.WHITE_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, ice);
        for (int slot : new int[]{0, 1, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 45, 46, 52, 53}) {
            inv.setItem(slot, frost);
        }
        for (int slot : new int[]{2, 3, 5, 6, 47, 48, 50, 51}) {
            inv.setItem(slot, snow);
        }
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(ChatColor.translateAlternateColorCodes('&', name));
        List<String> lines = new ArrayList<>();
        for (String line : lore) lines.add(ChatColor.translateAlternateColorCodes('&', line));
        meta.setLore(lines);
        item.setItemMeta(meta);
        return item;
    }

    private static final class CratesHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final class RewardsHolder implements InventoryHolder {
        private final String crateId;
        private RewardsHolder(String crateId) { this.crateId = crateId; }
        @Override public Inventory getInventory() { return null; }
    }
}
