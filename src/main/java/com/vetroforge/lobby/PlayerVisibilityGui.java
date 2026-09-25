// src/main/java/com/vetroforge/lobby/PlayerVisibilityGui.java
package com.vetroforge.lobby;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.commands.PlayHiderCommand.Mode;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.List;

public class PlayerVisibilityGui implements Listener, InventoryHolder {

    public static final String TITLE = ChatColor.AQUA + "Spielersichtbarkeit";
    private final FactionsPlugin plugin;
    private final NamespacedKey KEY;

    public PlayerVisibilityGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.KEY = new NamespacedKey(plugin, "vis_tag");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(this, 27, TITLE);
        fill(inv);

        inv.setItem(10, buttonHead(p, ChatColor.GREEN + "» Alle anzeigen", "ALL", List.of("§7Zeige alle Spieler.")));
        inv.setItem(12, button(Material.AMETHYST_SHARD, ChatColor.AQUA + "» Nur Team", "STAFF", List.of("§7Zeige nur Teammitglieder.")));
        inv.setItem(14, button(Material.HONEYCOMB, ChatColor.YELLOW + "» Freunde/Faction", "FRIENDS", List.of("§7Zeige nur Spieler deiner Faction.")));
        inv.setItem(16, button(Material.RED_DYE, ChatColor.RED + "» Niemanden anzeigen", "NONE", List.of("§7Verstecke alle Spieler.")));

        p.openInventory(inv);
        click(p);
    }

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!TITLE.equals(e.getView().getTitle())) return;
        if (e.getRawSlot() >= e.getInventory().getSize()) return;
        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String tag = it.getItemMeta().getPersistentDataContainer().get(KEY, PersistentDataType.STRING);
        if (tag == null) return;

        Mode mode;
        try {
            mode = Mode.valueOf(tag);
        } catch (Exception ex) {
            return;
        }

        if (plugin.getPlayHider() != null) {
            plugin.getPlayHider().setMode(p, mode);
        }

        if (plugin.getPlayerVisibilityManager() != null) {
            plugin.getPlayerVisibilityManager().setHidden(p, mode == Mode.NONE);
        }

        p.closeInventory();
        click(p);
    }

    private void fill(Inventory inv) {
        ItemStack pane = new ItemStack(Material.LIGHT_GRAY_STAINED_GLASS_PANE);
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

    private ItemStack buttonHead(Player owner, String name, String tag, List<String> lore) {
        ItemStack it = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) it.getItemMeta();
        sm.setOwningPlayer(owner);
        sm.setDisplayName(name);
        sm.setLore(lore);
        sm.getPersistentDataContainer().set(KEY, PersistentDataType.STRING, tag);
        it.setItemMeta(sm);
        return it;
    }

    @Override
    public Inventory getInventory() {
        return null;
    }
}