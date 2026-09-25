package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
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
import java.util.List;

public final class LevelGui implements Listener {

    private final FactionsPlugin plugin;

    public LevelGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        int level = plugin.getStatsManager().getLevel(player);
        int xp = plugin.getStatsManager().getLevelXp(player);
        int max = plugin.getLevelProgressionService().maxLevel();
        int need = plugin.getLevelProgressionService().xpNeeded(level);
        Inventory inv = Bukkit.createInventory(new Holder(), 54,
                "§8✦ §b§lLevel §8| §fProgression §8✦");
        fill(inv);

        inv.setItem(4, item(Material.NETHER_STAR, "§b§lLevel " + level,
                "§7Fortschritt: §f" + xp + "§8/§f" + need,
                progress(xp, need),
                "§7Max-Level: §f" + max,
                "",
                "§8Quellen: Warzone-Kisten, XP-Flaschen, Crates"));

        int[] milestoneSlots = {19, 20, 21, 22, 23, 24, 25};
        int[] milestones = {1, 2, 4, 6, 8, 9, 10};
        for (int i = 0; i < milestoneSlots.length; i++) {
            int milestone = milestones[i];
            boolean unlocked = level >= milestone;
            inv.setItem(milestoneSlots[i], item(unlocked ? Material.EMERALD_BLOCK : Material.COAL_BLOCK,
                    (unlocked ? "§a" : "§7") + "Milestone " + milestone,
                    unlocked ? "§aFreigeschaltet" : "§7Noch gesperrt",
                    rewardText(milestone)));
        }

        List<String> history = plugin.getLevelProgressionService().history(player.getUniqueId(), 6);
        List<String> lore = new ArrayList<>();
        lore.add("§7Warzone-Kiste: §b+" + plugin.getConfig().getInt("level.chestXp", 12) + " XP");
        lore.add("§7XP-Flasche: §b+" + plugin.getConfig().getInt("level.xpBottle", 15) + " XP");
        lore.add("");
        if (history.isEmpty()) {
            lore.add("§8Noch keine XP-History.");
        } else {
            for (String line : history) lore.add("§8• §f" + line);
        }
        inv.setItem(40, item(Material.CHEST, "§6Chest-XP History", lore.toArray(new String[0])));
        inv.setItem(49, item(Material.BARRIER, "§cSchließen"));

        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.25f);
    }

    private String rewardText(int milestone) {
        return switch (milestone) {
            case 1 -> "§7Start: §fWarzone-XP freigeschaltet";
            case 2 -> "§7Reward: §fMehr Chest-XP sichtbar";
            case 4 -> "§7Reward: §fFaction erstellen möglich";
            case 6 -> "§7Reward: §fBessere Warzone-Chancen";
            case 8 -> "§7Reward: §fRaid-Vorbereitung";
            case 9 -> "§7Reward: §fElite Warzone Zugang";
            case 10 -> "§7Reward: §6Max-Level erreicht";
            default -> "§7Reward folgt.";
        };
    }

    private String progress(int current, int max) {
        int filled = (int) Math.round(Math.max(0, Math.min(1, current / (double) Math.max(1, max))) * 20);
        return "§8[" + "§b■".repeat(filled) + "§7■".repeat(20 - filled) + "§8]";
    }

    private void fill(Inventory inv) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ");
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
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

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 49 && event.getWhoClicked() instanceof Player player) player.closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
