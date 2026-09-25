// src/main/java/com/vetroforge/commands/ErrorsCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;

public class ErrorsCommand implements CommandExecutor, Listener {
    private static String lastError = "Keine Fehler protokolliert.";
    private static final ArrayDeque<String> HISTORY = new ArrayDeque<>();

    public ErrorsCommand(FactionsPlugin plugin) {
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
        if (HISTORY.isEmpty()) HISTORY.addLast(Instant.now() + " | " + lastError);
    }

    public static void setLastError(String e){
        lastError = (e == null || e.isBlank()) ? "Unbekannter Fehler" : e;
        HISTORY.addLast(Instant.now() + " | " + lastError);
        while (HISTORY.size() > 36) HISTORY.removeFirst();
    }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vetroforge.staff.errors")) {
            s.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (!(s instanceof Player player)) {
            s.sendMessage(ChatColor.DARK_RED+"—————— "+ChatColor.RED+"VetroForge"+ChatColor.WHITE+" • Errors "+ChatColor.DARK_RED+"——————");
            for (String line : HISTORY) s.sendMessage(ChatColor.GRAY + line);
            s.sendMessage(ChatColor.DARK_RED+"——————————————————————————————");
            return true;
        }
        open(player);
        return true;
    }

    private void open(Player player) {
        Inventory inv = org.bukkit.Bukkit.createInventory(new Holder(), 45, "§4§lVetroForge §8• §cErrors");
        fill(inv);
        inv.setItem(4, item(Material.REDSTONE_BLOCK, "§c§lError Center", List.of(
                "§7Letzter bekannter Plugin-Fehler",
                "§7und die letzten Meldungen.",
                "",
                lastError.equals("Keine Fehler protokolliert.") ? "§aAktuell sauber" : "§eReview empfohlen"
        )));
        inv.setItem(20, item(Material.PAPER, "§fLetzter Fehler", wrap(lastError, 36)));
        inv.setItem(24, item(Material.BOOK, "§cFehler-Historie", historyLore()));
        inv.setItem(40, item(Material.BARRIER, "§cSchliessen", List.of("§7Klicken zum Schliessen")));
        player.openInventory(inv);
    }

    private List<String> historyLore() {
        List<String> out = new ArrayList<>();
        List<String> copy = new ArrayList<>(HISTORY);
        int start = Math.max(0, copy.size() - 10);
        for (int i = start; i < copy.size(); i++) out.add("§8• §7" + copy.get(i));
        if (out.isEmpty()) out.add("§aKeine Fehler protokolliert.");
        return out;
    }

    private List<String> wrap(String text, int width) {
        List<String> out = new ArrayList<>();
        String raw = text == null ? "" : text;
        while (raw.length() > width) {
            out.add("§7" + raw.substring(0, width));
            raw = raw.substring(width);
        }
        out.add("§7" + raw);
        return out;
    }

    private void fill(Inventory inv) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);
        for (int slot : new int[]{0,1,2,3,5,6,7,8,36,37,38,39,41,42,43,44}) {
            inv.setItem(slot, item(Material.RED_STAINED_GLASS_PANE, " ", List.of()));
        }
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 40) event.getWhoClicked().closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
