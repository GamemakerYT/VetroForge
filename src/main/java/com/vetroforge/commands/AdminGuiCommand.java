package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

public final class AdminGuiCommand implements CommandExecutor, Listener {

    private final FactionsPlugin plugin;
    private final NamespacedKey actionKey;

    public AdminGuiCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "admingui_action");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("Players only.");
            return true;
        }
        if (!player.hasPermission("vetroforge.admin")) {
            player.sendMessage("§cKeine Rechte.");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        Inventory inv = Bukkit.createInventory(new Holder(), 54, "§8VetroForge §9Admin");
        VetroForgeUtil.fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(10, action(Material.BRICKS, "§aBuild Toggle", "build toggle", "§7Schaltet deinen Buildmodus um."));
        inv.setItem(11, action(Material.COMPASS, "§bStaff Tools", "staffgui", "§7Spieler, Invsee, Enderchest, Teleport."));
        inv.setItem(12, action(Material.CHEST, "§6Chest Punkte anzeigen", "chest show", "§7Partikel + ID-Marker."));
        inv.setItem(13, action(Material.REDSTONE_TORCH, "§cClearLag", "clearlag", "§7Items am Boden bereinigen."));
        inv.setItem(14, action(Material.NETHER_STAR, "§bPerformance", "vf doctor performance", "§7TPS, RAM, Prozesse."));
        inv.setItem(15, action(Material.ENDER_EYE, "§dEntities", "vf doctor entities", "§7Entity-Zonen prüfen."));
        inv.setItem(16, action(Material.BEACON, "§4Warzone anzeigen", "warzone show", "§7Rot außen, Grün Spawn-Cutout."));
        inv.setItem(19, action(Material.EMERALD, "§aShop Admin", "adminshop gui", "§7Server-Shop bearbeiten."));
        inv.setItem(20, action(Material.CACTUS, "§2Cactus Shop Debug", "shop debug cactus", "§7Prüft SellShop + Event-Fortschritt."));
        inv.setItem(21, action(Material.GOLD_INGOT, "§eAuktionshaus Admin", "adminah gui", "§7Auktionen prüfen."));
        inv.setItem(22, action(Material.TRIPWIRE_HOOK, "§dCrate Admin", "crateadmin", "§7Crate Rewards bearbeiten."));
        inv.setItem(23, action(Material.CHEST_MINECART, "§6Chest Doctor", "chest doctor", "§7Prüft Warzone-Chest-Punkte."));
        inv.setItem(24, action(Material.WITHER_SKELETON_SKULL, "§4Boss Admin", "adminboss gui", "§7Bosse, Spawnpunkte, Cooldowns."));
        inv.setItem(25, action(Material.REDSTONE, "§cEvent Admin", "eventadmingui", "§7Events starten und prüfen."));
        inv.setItem(28, action(Material.NETHERITE_SWORD, "§cRaid Diagnose", "raidadmin", "§7Hilfe für Start, Status und Full-Test."));
        inv.setItem(29, action(Material.SPAWNER, "§4Disparity Test", "vf test warzonemob", "§7Spawnt einen Testmob nahe dir."));
        inv.setItem(30, action(Material.DRAGON_BREATH, "§dDragonsBreath Test", "vf test dragonsbreath", "§7Gibt dir Testitems für Fähigkeit + Fusion."));
        inv.setItem(31, action(Material.ENCHANTED_BOOK, "§dFusion Books", "fusionbooks", "§7Dropchancen und Upgrade-Regeln."));
        inv.setItem(32, action(Material.EXPERIENCE_BOTTLE, "§bFusion Simulation", "fusionbooks simulate", "§7Prüft Common bis Mythic automatisch."));
        inv.setItem(33, action(Material.OAK_SIGN, "§6Top GUI", "top", "§7Weekly/Season Leaderboards."));
        inv.setItem(34, action(Material.ARMOR_STAND, "§eLeaderboard Holo setzen", "holo set LeaderboardRotation", "§7Setzt rotierendes Top-Hologramm an dir."));
        inv.setItem(40, action(Material.PAPER, "§fBuild Liste", "build list", "§7Wer ist gerade im Buildmodus?"));
        player.openInventory(inv);
        player.playSound(player.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;
        player.closeInventory();
        player.performCommand(action);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private ItemStack action(Material material, String name, String command, String... lore) {
        ItemStack item = VetroForgeUtil.item(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, command);
        item.setItemMeta(meta);
        return item;
    }

    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
