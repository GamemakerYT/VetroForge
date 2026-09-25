package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.PrestigeUtil;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

public final class StatsGui implements Listener {
    private static final DecimalFormat DF = new DecimalFormat("0.00");
    private final FactionsPlugin plugin;

    private enum Page {
        OVERVIEW("Übersicht", Material.PLAYER_HEAD),
        PVP("PvP", Material.IRON_SWORD),
        RAIDS("Raids", Material.TNT),
        WARZONE("Warzone", Material.COMPASS),
        ECONOMY("Economy", Material.GOLD_INGOT),
        QUESTS("Quests", Material.WRITABLE_BOOK),
        SEASON("Season", Material.NETHER_STAR);

        private final String label;
        private final Material icon;

        Page(String label, Material icon) {
            this.label = label;
            this.icon = icon;
        }
    }

    public StatsGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player viewer, OfflinePlayer target) {
        open(viewer, target, Page.OVERVIEW);
    }

    private void open(Player viewer, OfflinePlayer target, Page page) {
        UUID id = target.getUniqueId();
        String title = "§8✦ §c§lVetro§6§lForge §8| §fStats §8• §b" + page.label;
        Inventory inventory = Bukkit.createInventory(new Holder(id, page), 54, title);
        fillIce(inventory);
        drawTabs(inventory, page);
        drawPage(inventory, target, page);
        inventory.setItem(53, VetroForgeUtil.item(Material.BARRIER, "§cSchließen"));
        viewer.openInventory(inventory);
    }

    private void drawPage(Inventory inventory, OfflinePlayer target, Page page) {
        UUID id = target.getUniqueId();
        var stats = plugin.getStatsManager();
        String faction = faction(id);
        int prestige = stats == null ? 0 : stats.getPrestigeLevel(id);
        int level = stats == null ? 0 : stats.getLevel(id);

        inventory.setItem(4, playerHead(target, faction, format(stats == null ? 0 : stats.getOnlineSeconds(id))));

        switch (page) {
            case OVERVIEW -> {
                inventory.setItem(20, VetroForgeUtil.item(Material.IRON_SWORD, "§c§lPvP Snapshot",
                        "§7Kills: §f" + statKills(id),
                        "§7Deaths: §f" + statDeaths(id),
                        "§7K/D: §e" + DF.format(statKdr(id))));
                inventory.setItem(22, VetroForgeUtil.item(Material.NETHER_STAR, "§6§lProgression",
                        "§7Level: §b" + level,
                        "§7Level XP: §f" + (stats == null ? 0 : stats.getLevelXp(id)),
                        "§7Prestige: " + PrestigeUtil.titleFor(prestige),
                        "§7Prestige XP: §f" + (stats == null ? 0 : stats.getPrestigeXp(id)),
                        "§7Elo: §6" + (stats == null ? 1000 : stats.getElo(id))));
                inventory.setItem(24, VetroForgeUtil.item(Material.GOLD_INGOT, "§e§lEconomy",
                        "§7Coins: §6" + coins(id),
                        "§7Faction: §f" + faction));
            }
            case PVP -> {
                inventory.setItem(20, VetroForgeUtil.item(Material.DIAMOND_SWORD, "§c§lCombat",
                        "§7Kills: §f" + statKills(id),
                        "§7Deaths: §f" + statDeaths(id),
                        "§7Streak: §f" + statStreak(id),
                        "§7K/D: §e" + DF.format(statKdr(id))));
                inventory.setItem(24, VetroForgeUtil.item(Material.SHIELD, "§7§lElo",
                        "§7Player Elo: §6" + (stats == null ? 1000 : stats.getElo(id)),
                        "§8Gold IV+ kann Elo verlieren."));
            }
            case RAIDS -> {
                inventory.setItem(20, VetroForgeUtil.item(Material.TNT, "§c§lRaid Status",
                        "§7Faction: §f" + faction,
                        "§7Energy: §b" + energy(id),
                        "§7Crystals: §d" + crystals(id)));
                inventory.setItem(24, VetroForgeUtil.item(Material.OBSIDIAN, "§5§lRaid Progress",
                        "§7Obsidian und Raid-History",
                        "§7werden im nächsten Raid-Stats-Pass",
                        "§7dauerhaft pro Spieler gespeichert."));
            }
            case WARZONE -> {
                inventory.setItem(20, VetroForgeUtil.item(Material.COMPASS, "§4§lWarzone",
                        "§7Disparity: §c" + disparity(target),
                        "§7Bestiary: §f/item codex",
                        "§7Chests geben Level-XP."));
                inventory.setItem(24, VetroForgeUtil.item(Material.CHEST, "§b§lChest XP History",
                        levelHistory(id).toArray(String[]::new)));
            }
            case ECONOMY -> {
                inventory.setItem(20, VetroForgeUtil.item(Material.GOLD_INGOT, "§e§lCoins",
                        "§7Balance: §6" + coins(id),
                        "§7Money ist nur Alias auf Coins."));
                inventory.setItem(24, VetroForgeUtil.item(Material.EMERALD, "§a§lTrading",
                        "§7Shop, Auction House und Crates",
                        "§7laufen auf Coins als Hauptwährung."));
            }
            case QUESTS -> {
                int targetKills = Math.max(1, plugin.getConfig().getInt("quests.weeklyFaction.kills", 100));
                int reward = Math.max(0, plugin.getConfig().getInt("quests.weeklyFaction.rewardFactionCoins", 250));
                inventory.setItem(20, VetroForgeUtil.item(Material.WRITABLE_BOOK, "§6§lDaily",
                        "§7Daily Kills: §f" + (stats == null ? 0 : stats.getStat(id, "daily_kills")),
                        "§7Belohnungen: §fCoins / XP"));
                inventory.setItem(24, VetroForgeUtil.item(Material.BEACON, "§b§lFaction Weekly",
                        "§7Ziel: §f" + targetKills + " Kills",
                        "§7Reward: §e" + reward + " Faction Coins",
                        "§7Faction: §f" + faction));
            }
            case SEASON -> {
                inventory.setItem(20, VetroForgeUtil.item(Material.NETHER_STAR, "§6§lSeason Verlauf",
                        "§7Prestige: " + PrestigeUtil.titleFor(prestige),
                        "§7Online: §f" + format(stats == null ? 0 : stats.getOnlineSeconds(id))));
                inventory.setItem(24, VetroForgeUtil.item(Material.AMETHYST_SHARD, "§d§lMilestones",
                        "§7Level-Farben wechseln alle 10 Stufen.",
                        "§7Crates und Warzone füttern Progression."));
            }
        }
    }

    private void drawTabs(Inventory inventory, Page active) {
        int slot = 45;
        for (Page page : Page.values()) {
            Material material = page == active ? Material.PACKED_ICE : page.icon;
            inventory.setItem(slot++, VetroForgeUtil.item(material,
                    (page == active ? "§b§l" : "§7") + page.label,
                    page == active ? "§7Aktive Seite" : "§eKlicken zum Öffnen"));
        }
    }

    @EventHandler public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        if (event.getRawSlot() == 53) {
            player.closeInventory();
            return;
        }
        int idx = event.getRawSlot() - 45;
        Page[] pages = Page.values();
        if (idx >= 0 && idx < pages.length) {
            open(player, Bukkit.getOfflinePlayer(holder.targetId), pages[idx]);
        }
    }

    @EventHandler public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private String faction(UUID id) {
        try {
            return Optional.ofNullable(plugin.getFactionManager().getFactionOf(id))
                    .filter(value -> !value.isBlank()).orElse("-");
        } catch (Throwable ignored) {
            return "-";
        }
    }

    private int coins(UUID id) {
        try { return plugin.getCoinsManager() == null ? 0 : plugin.getCoinsManager().getCoins(id); }
        catch (Throwable ignored) { return 0; }
    }

    private int energy(UUID id) {
        try { return plugin.getEnergyManager() == null ? 0 : plugin.getEnergyManager().get(id); }
        catch (Throwable ignored) { return 0; }
    }

    private int crystals(UUID id) {
        try {
            if (plugin.getRaidCrystalManager() == null) return 0;
            String faction = faction(id);
            return "-".equals(faction) ? plugin.getRaidCrystalManager().getPlayerCrystals(id)
                    : plugin.getRaidCrystalManager().getForFaction(faction);
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private int disparity(OfflinePlayer target) {
        Player online = target.getPlayer();
        try {
            if (online == null || plugin.getWarzoneRegionManager() == null) return 0;
            return plugin.getWarzoneRegionManager().disparityLevel(online.getLocation());
        } catch (Throwable ignored) {
            return 0;
        }
    }

    private List<String> levelHistory(UUID id) {
        List<String> lore = new ArrayList<>();
        try {
            if (plugin.getLevelProgressionService() != null) {
                for (String line : plugin.getLevelProgressionService().history(id, 4)) {
                    lore.add("§8• §7" + line);
                }
            }
        } catch (Throwable ignored) {}
        if (lore.isEmpty()) lore.add("§7Noch keine Chest-XP Einträge.");
        return lore;
    }

    private int statKills(UUID id) { return plugin.getStatsManager() == null ? 0 : plugin.getStatsManager().getKills(id); }
    private int statDeaths(UUID id) { return plugin.getStatsManager() == null ? 0 : plugin.getStatsManager().getDeaths(id); }
    private int statStreak(UUID id) { return plugin.getStatsManager() == null ? 0 : plugin.getStatsManager().getStreak(id); }
    private double statKdr(UUID id) { return plugin.getStatsManager() == null ? 0.0D : plugin.getStatsManager().getKDR(id); }

    private String name(OfflinePlayer player) { return player.getName() == null ? player.getUniqueId().toString() : player.getName(); }

    private ItemStack playerHead(OfflinePlayer player, String faction, String onlineTime) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        if (meta != null) {
            meta.setOwningPlayer(player);
            meta.setDisplayName("§b§l" + name(player));
            meta.setLore(List.of("§7Faction: §f" + faction, "§7Online: §f" + onlineTime));
            item.setItemMeta(meta);
        }
        return item;
    }

    private void fillIce(Inventory inventory) {
        ItemStack ice = pane(Material.LIGHT_BLUE_STAINED_GLASS_PANE);
        ItemStack frost = pane(Material.CYAN_STAINED_GLASS_PANE);
        ItemStack snow = pane(Material.WHITE_STAINED_GLASS_PANE);
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, ice);
        for (int slot : new int[]{0, 1, 7, 8, 9, 17, 18, 26, 27, 35, 36, 44, 52, 53}) inventory.setItem(slot, frost);
        for (int slot : new int[]{2, 3, 5, 6, 46, 47, 50, 51}) inventory.setItem(slot, snow);
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(" ");
            item.setItemMeta(meta);
        }
        return item;
    }

    private String format(long seconds) {
        long hours = seconds / 3600, minutes = seconds % 3600 / 60;
        return String.format(Locale.ROOT, "%dh %02dm", hours, minutes);
    }

    private static final class Holder implements InventoryHolder {
        private final UUID targetId;
        private final Page page;

        private Holder(UUID targetId, Page page) {
            this.targetId = targetId;
            this.page = page;
        }

        @Override public Inventory getInventory() { return null; }
    }
}
