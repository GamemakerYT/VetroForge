package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.ReadOnlyItemMenu;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.util.*;

public final class GuideCommand implements CommandExecutor, Listener {
    private final FactionsPlugin plugin;
    private record View(boolean warzone) implements ReadOnlyItemMenu { public Inventory getInventory() { return null; } }
    public GuideCommand(FactionsPlugin plugin) { this.plugin = plugin; Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (sender instanceof Player player) open(player, false); else sender.sendMessage("/guide öffnet ingame den Neulings-Wegweiser.");
        return true;
    }
    private void open(Player p, boolean warzone) {
        Inventory inv = Bukkit.createInventory(new View(warzone), 54, "§9§lVetroForge §8• §fDein Einstieg");
        for (int i = 0; i < 54; i++) inv.setItem(i, card(i < 9 || i >= 45 ? Material.BLUE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE, " "));
        inv.setItem(4, card(Material.COMPASS, "§b§l" + (warzone ? "Warzone verstehen" : "Dein Weg zum ersten Raid"),
                "§7Die Karten öffnen Menüs oder Erklärungen.", "§7Kein automatischer Kauf, Claim oder Teleport."));
        if (warzone) {
            inv.setItem(20, card(Material.IRON_SWORD, "§bVor dem Betreten", "§7In der Warzone erwarten dich PvP und Mobs.", "§7Nimm nur Ausrüstung mit, die du riskieren kannst."));
            inv.setItem(22, card(Material.CHEST, "§bChests & Lootdrops", "§7Suche gesetzte Chest-Punkte und aktive Lootdrops.", "§7Crystal-Rewards löst du bei Varyn ein."));
            inv.setItem(24, card(Material.SHIELD, "§bLieblingsitems", "§7/itemlock on schützt vor Verkauf und Fusion.", "§cDas schützt nicht vor Verlust beim Tod."));
        } else {
            String faction = plugin.getFactionManager().getFactionOf(p.getUniqueId());
            inv.setItem(10, card(Material.CHAINMAIL_CHESTPLATE, "§b1 · Ausrüstung holen", "§7Öffne das Kit-Menü und prüfe deinen Anspruch.", "§eKit-Menü öffnen"));
            inv.setItem(12, card(Material.BLUE_BANNER, "§b2 · Faction finden", faction == null ? "§7Noch keine Faction: finden oder gründen." : "§aDeine Faction: " + faction, "§eFaction-Menü öffnen"));
            inv.setItem(14, card(Material.IRON_SWORD, "§b3 · Warzone verstehen", "§7Risiko, Beute und Item-Schutz kennenlernen.", "§eHinweise ansehen"));
            inv.setItem(16, card(Material.TNT, "§b4 · Ersten Raid planen", "§7Vorbereitung und Join im Raid-Menü prüfen.", "§7Startbedingungen und Rollenrechte gelten weiter.", "§eRaid-Menü öffnen"));
            inv.setItem(28, card(Material.ENCHANTED_BOOK, "§bRüstungssets", "§7Boni, fehlende Teile und Herkunft ansehen.", "§e/sets öffnen"));
            inv.setItem(30, card(Material.WRITABLE_BOOK, "§bGemeinsame Wochenziele", "§7Aufgaben und Belohnungen deiner Faction.", "§eWochenziele öffnen"));
            inv.setItem(32, card(Material.ENDER_CHEST, "§bZustellfach", "§7Bezahlte Web-Shop-Käufe und ihren Status sehen.", "§e/deliveries öffnen"));
            inv.setItem(34, card(Material.HOPPER, "§bGemeinsam Vorräte sammeln", "§7Faction-Vault mit Spendenfach und Zielen.", "§eVault öffnen"));
        }
        inv.setItem(45, card(Material.ARROW, "§bZum Einstieg"));
        inv.setItem(53, card(Material.BARRIER, "§7Schließen"));
        p.openInventory(inv);
    }
    public static String action(int slot) {
        return switch (slot) {
            case 10 -> "kit"; case 12 -> "f"; case 16 -> "raid gui"; case 28 -> "sets";
            case 30 -> "f quests"; case 32 -> "deliveries"; case 34 -> "fvault"; default -> null;
        };
    }
    @EventHandler(priority = EventPriority.LOWEST) public void protect(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void onClick(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof View view)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p) || e.getClick() != ClickType.LEFT
                || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!p.isOnline() || p.getOpenInventory().getTopInventory().getHolder() != view) return;
            if (slot == 53) { p.closeInventory(); return; }
            if (slot == 45) { open(p, false); return; }
            if (view.warzone()) return;
            if (slot == 14) { open(p, true); return; }
            String action = action(slot);
            if (action != null) { p.closeInventory(); p.performCommand(action); }
        });
    }
    @EventHandler(priority = EventPriority.LOWEST) public void onDrag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof View) e.setCancelled(true);
    }
    private static ItemStack card(Material material, String title, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> { meta.setDisplayName(title); meta.setLore(List.of(lore)); meta.addItemFlags(ItemFlag.values()); });
        return item;
    }
}
