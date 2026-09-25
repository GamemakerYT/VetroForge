// src/main/java/com/vetroforge/lobby/LobbyNavigatorGui.java
package com.vetroforge.lobby;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class LobbyNavigatorGui implements Listener, InventoryHolder {

    public static final String TITLE = "§6Navigator";
    private final FactionsPlugin plugin;
    private final org.bukkit.NamespacedKey KEY;

    public LobbyNavigatorGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.KEY = new org.bukkit.NamespacedKey(plugin, "nav_tag");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(this, 27, TITLE);
        fill(inv);

        inv.setItem(10, button(Material.COMMAND_BLOCK, ChatColor.GOLD + "Events", "events", List.of("§7Öffnet das Event-Board.")));
        inv.setItem(12, button(Material.IRON_AXE, ChatColor.YELLOW + "Warps", "warps", List.of("§7Server-Warpübersicht.")));
        inv.setItem(13, button(Material.DIAMOND_PICKAXE, ChatColor.AQUA + "Forge", "forge", List.of("§7Öffnet die Forge.")));
        inv.setItem(14, button(Material.RED_BED, ChatColor.WHITE + "Lobby-Spawn", "spawn", List.of("§7Teleportiere zum Lobby-Spawn.")));
        inv.setItem(15, button(Material.CHEST, ChatColor.GOLD + "Cosmetics", "cosm", List.of("§7Kategorien & Effekte.")));
        inv.setItem(16, button(Material.GLOWSTONE_DUST, ChatColor.LIGHT_PURPLE + "Sichtbarkeit", "hider", List.of("§7Filter für Spielersichtbarkeit.")));
        inv.setItem(22, button(Material.COMPARATOR, ChatColor.AQUA + "Einstellungen", "settings", List.of("§7Fly, JumpPads, Spawn")));

        p.openInventory(inv);
        click(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof LobbyNavigatorGui)) return;
        if (e.getRawSlot() >= e.getInventory().getSize()) return;
        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String tag = it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (tag == null) return;

        switch (tag) {
            case "events" -> {
                p.closeInventory();
                Bukkit.dispatchCommand(p, "events");
            }
            case "warps" -> {
                p.closeInventory();
                Bukkit.dispatchCommand(p, "warps");
            }
            case "forge" -> {
                p.closeInventory();
                Bukkit.dispatchCommand(p, "forge");
            }
            case "spawn" -> {
                p.closeInventory();
                plugin.getLobbyManager().teleportToLobby(p);
            }
            case "cosm" -> {
                p.closeInventory();
                Bukkit.dispatchCommand(p, "cosmetics");
            }
            case "hider" -> {
                p.closeInventory();
                if (plugin.getVisibilityGui() != null) plugin.getVisibilityGui().open(p);
            }
            case "settings" -> {
                p.closeInventory();
                if (plugin.getLobbySettingsGui() != null) plugin.getLobbySettingsGui().open(p);
            }
        }
        click(p);
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta m = pane.getItemMeta();
        m.setDisplayName(" ");
        pane.setItemMeta(m);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
    }

    private void click(Player p) {
        try {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, .8f, 1.2f);
        } catch (Throwable ignore) {}
    }

    private ItemStack button(Material mat, String name, String tag, List<String> lore) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        im.setLore(lore);
        im.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, tag);
        it.setItemMeta(im);
        return it;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}