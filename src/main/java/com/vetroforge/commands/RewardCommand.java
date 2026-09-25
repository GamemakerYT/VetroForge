// src/main/java/com/vetroforge/commands/RewardCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.crates.RewardManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;

public class RewardCommand implements CommandExecutor, Listener {
    private static final String TITLE = ChatColor.DARK_RED + "Rewards";
    private final FactionsPlugin plugin;
    public RewardCommand(FactionsPlugin plugin){
        this.plugin=plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    @Override public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        RewardManager rm = plugin.getKeyCrateManager().getRewardManager();
        if (a.length == 0 && s instanceof Player player) {
            open(player);
            return true;
        }
        if (a.length==0 || a[0].equalsIgnoreCase("list")) {
            s.sendMessage(ChatColor.DARK_RED+"—————— "+ChatColor.RED+"VetroForge"+ChatColor.WHITE+" • Rewards "+ChatColor.DARK_RED+"——————");
            for (RewardManager.Reward r : rm.all()) {
                s.sendMessage(ChatColor.YELLOW+"- "+(r.displayBase.getItemMeta()!=null?r.displayBase.getItemMeta().getDisplayName():r.id)
                        + ChatColor.GRAY+" (id: "+r.id+", w:"+r.weight+")");
            }
            s.sendMessage(ChatColor.DARK_RED+"——————————————————————————————");
            return true;
        }
        s.sendMessage(ChatColor.YELLOW+"/reward list");
        return true;
    }

    private void open(Player player) {
        Inventory inventory = Bukkit.createInventory(null, 54, TITLE);
        fill(inventory);
        String nextBonus = plugin.getConfig().getString("rewards.nextBonus.text", "In 2 Tagen: Neon Orbit Trail (7d)");
        inventory.setItem(13, item(Material.NETHER_STAR, ChatColor.RED + "Nächster Bonus",
                List.of(ChatColor.GRAY + nextBonus, ChatColor.DARK_GRAY + "Daily/VIP/Streak Preview")));
        inventory.setItem(40, item(Material.HOPPER, ChatColor.GREEN + "Collect All",
                List.of(ChatColor.GRAY + "Sammelt offene Daily/VIP/Quest-Rewards.",
                        ChatColor.YELLOW + "Backend-Hook vorbereitet.",
                        ChatColor.DARK_GRAY + "Slot 40 im großen Reward-GUI")));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!TITLE.equals(event.getView().getTitle())) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getRawSlot() == 40) {
            player.sendActionBar(ChatColor.GOLD + "Collect All " + ChatColor.DARK_GRAY + "» "
                    + ChatColor.GRAY + "Noch keine offenen Reward-Quellen verbunden.");
            player.sendMessage(ChatColor.YELLOW + "Rewards " + ChatColor.DARK_GRAY + "» "
                    + ChatColor.GRAY + "Collect-All ist vorbereitet. Daily/VIP/Quest-Quellen werden als nächstes angebunden.");
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (TITLE.equals(event.getView().getTitle())) event.setCancelled(true);
    }

    private void fill(Inventory inventory) {
        ItemStack pane = item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, pane);
    }

    private ItemStack item(Material material, String name, List<String> lore) {
        ItemStack stack = new ItemStack(material);
        ItemMeta meta = stack.getItemMeta();
        if (meta != null) {
            meta.setDisplayName(name);
            meta.setLore(lore);
            stack.setItemMeta(meta);
        }
        return stack;
    }
}
