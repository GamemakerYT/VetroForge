// src/main/java/com/vetroforge/lobby/LobbySettingsGui.java
package com.vetroforge.lobby;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
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

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class LobbySettingsGui implements Listener, InventoryHolder {

    public static final String TITLE = ChatColor.AQUA + "Lobby Einstellungen";

    private final FactionsPlugin plugin;
    private final LobbySettingsService settingsService;
    private final NamespacedKey key;

    public LobbySettingsGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.settingsService = new LobbySettingsService(plugin);
        this.key = new NamespacedKey(plugin, "lobby_settings_action");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(this, 27, TITLE);
        fill(inv);

        boolean fly = settingsService.canFly(p);
        boolean doubleJump = settingsService.hasDoubleJump(p);
        float speed = settingsService.getSpeed(p);

        inv.setItem(10, button(
                Material.FEATHER,
                (fly ? "§aFly aktiviert" : "§cFly deaktiviert"),
                "fly",
                Arrays.asList(
                        "§7Aktueller Status: " + (fly ? "§aAN" : "§cAUS"),
                        "",
                        "§8» §fKlicken zum Umschalten"
                )
        ));

        inv.setItem(12, button(
                Material.RABBIT_FOOT,
                (doubleJump ? "§aDoubleJump aktiviert" : "§cDoubleJump deaktiviert"),
                "doublejump",
                Arrays.asList(
                        "§7Aktueller Status: " + (doubleJump ? "§aAN" : "§cAUS"),
                        "",
                        "§8» §fKlicken zum Umschalten"
                )
        ));

        inv.setItem(14, button(
                Material.SUGAR,
                "§eLaufgeschwindigkeit",
                "speed",
                Arrays.asList(
                        "§7Aktuell: §f" + String.format(java.util.Locale.US, "%.2f", speed),
                        "",
                        "§8» §fKlicken zum Durchschalten",
                        "§8» §70.20 / 0.25 / 0.30 / 0.35"
                )
        ));

        inv.setItem(16, button(
                Material.RED_BED,
                "§6Zum Spawn",
                "spawn",
                Arrays.asList(
                        "§8» §fTeleportiert dich zum Lobby-Spawn"
                )
        ));

        inv.setItem(22, button(
                Material.ARROW,
                "§eZurück",
                "back",
                Arrays.asList(
                        "§8» §fZurück zum Navigator"
                )
        ));

        p.openInventory(inv);
        click(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof LobbySettingsGui)) return;
        if (e.getRawSlot() >= e.getView().getTopInventory().getSize()) return;

        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String action = it.getItemMeta().getPersistentDataContainer().get(key, PersistentDataType.STRING);
        if (action == null) return;

        switch (action) {
            case "fly" -> {
                boolean next = !settingsService.canFly(p);
                settingsService.setFly(p, next);
                plugin.getLobbyManager().setFlyMode(p, next);
                p.sendMessage("§6Lobby §8» §fFly " + (next ? "§aaktiviert" : "§cdeaktiviert"));
                open(p);
            }
            case "doublejump" -> {
                boolean next = !settingsService.hasDoubleJump(p);
                settingsService.setDoubleJump(p, next);
                p.sendMessage("§6VetroForge §8• §7DoubleJump " + (next ? "§aaktiviert" : "§cdeaktiviert"));
                open(p);
            }
            case "speed" -> {
                float next = nextSpeed(settingsService.getSpeed(p));
                settingsService.setSpeed(p, next);
                p.sendMessage("§6VetroForge §8• §7Speed gesetzt auf §f" + String.format(java.util.Locale.US, "%.2f", next));
                open(p);
            }
            case "spawn" -> {
                p.closeInventory();
                plugin.getLobbyManager().teleportToLobby(p);
            }
            case "back" -> {
                p.closeInventory();
                if (plugin.getLobbyNavigatorGui() != null) {
                    plugin.getLobbyNavigatorGui().open(p);
                }
            }
        }

        click(p);
    }

    private float nextSpeed(float current) {
        float[] values = new float[]{0.20f, 0.25f, 0.30f, 0.35f};
        for (int i = 0; i < values.length; i++) {
            if (Math.abs(values[i] - current) < 0.001f) {
                return values[(i + 1) % values.length];
            }
        }
        return 0.20f;
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);

        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, pane);
        }
    }

    private ItemStack button(Material material, String name, String action, List<String> lore) {
        ItemStack it = new ItemStack(material);
        ItemMeta meta = it.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(new ArrayList<>(lore));
        meta.getPersistentDataContainer().set(key, PersistentDataType.STRING, action);
        it.setItemMeta(meta);
        return it;
    }

    private void click(Player p) {
        try {
            p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 0.8f, 1.2f);
        } catch (Throwable ignored) {
        }
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}