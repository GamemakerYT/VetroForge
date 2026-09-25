// src/main/java/com/vetroforge/lootdrop/LockpickGui.java
package com.vetroforge.lootdrop;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.Random;
import java.util.function.Consumer;

public class LockpickGui implements InventoryHolder {

    private final FactionsPlugin plugin;
    private final Consumer<Boolean> callback;
    private final Inventory inv;
    private int targetSlot; // 0..8
    private int cursor = 0;
    private int ticks = 0;
    private int taskId = -1;
    private org.bukkit.event.Listener listener;
    private boolean finished = false;

    public LockpickGui(FactionsPlugin plugin, Consumer<Boolean> callback){
        this.plugin = plugin;
        this.callback = callback;
        this.inv = Bukkit.createInventory(this, 9, ChatColor.RED + "" + ChatColor.BOLD + "Lootdrop" + ChatColor.DARK_GRAY + " » " + ChatColor.WHITE + "Lockpick");
        this.targetSlot = new Random().nextInt(9);
    }

    public void open(Player p){
        draw();
        p.openInventory(inv);
        taskId = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, () -> {
            ticks++;
            cursor = (cursor + 1) % 9;
            draw();
            if (ticks > 9*4) { // ~4 Umdrehungen
                finish(false, p);
            }
        }, 4L, 4L); // zackig
        listener = new org.bukkit.event.Listener(){
            @org.bukkit.event.EventHandler
            public void click(org.bukkit.event.inventory.InventoryClickEvent e){
                if (e.getClickedInventory() != inv) return;
                e.setCancelled(true);
                if (!(e.getWhoClicked() instanceof Player pl)) return;
                if (e.getSlot() != cursor) {
                    pl.playSound(pl, Sound.UI_BUTTON_CLICK, 0.8f, 0.7f);
                    return;
                }
                finish(cursor == targetSlot, pl);
            }
            @org.bukkit.event.EventHandler
            public void close(org.bukkit.event.inventory.InventoryCloseEvent e){
                if (e.getInventory() != inv) return;
                finish(false, (Player) e.getPlayer());
            }
        };
        Bukkit.getPluginManager().registerEvents(listener, plugin);
    }

    private void draw(){
        for (int i=0;i<9;i++){
            inv.setItem(i, pane(i==cursor ? Material.RED_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE, "§8"));
        }
        inv.setItem(targetSlot, pane(Material.REDSTONE_BLOCK, ChatColor.RED + "" + ChatColor.BOLD + "Sweet Spot"));
    }

    private ItemStack pane(Material m, String name){
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta(); im.setDisplayName(name); it.setItemMeta(im);
        return it;
    }

    private void finish(boolean success, Player p){
        if (finished) return;
        finished = true;
        if (taskId != -1) { Bukkit.getScheduler().cancelTask(taskId); taskId = -1; }
        if (listener != null) { org.bukkit.event.HandlerList.unregisterAll(listener); listener = null; }
        try { p.closeInventory(); } catch (Throwable ignored) {}
        if (callback != null) callback.accept(success);
        p.playSound(p, success ? Sound.BLOCK_NOTE_BLOCK_CHIME : Sound.BLOCK_NOTE_BLOCK_BASS, 1f, success?1.5f:0.8f);
    }

    @Override public Inventory getInventory(){ return inv; }
}
