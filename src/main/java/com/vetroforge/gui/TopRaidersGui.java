package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import java.util.Locale;
import java.util.Map;

public class TopRaidersGui implements Listener {

    private final FactionsPlugin plugin;
    public TopRaidersGui(FactionsPlugin plugin){
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(new Holder(), 36, tr(p, "top.crystals.title"));
        VetroForgeUtil.fill(inv, Material.GRAY_STAINED_GLASS_PANE);

        if (plugin.getRaidCrystalManager() == null) {
            inv.setItem(13, VetroForgeUtil.item(Material.BARRIER, tr(p, "top.crystals.unavailable")));
            p.openInventory(inv);
            return;
        }

        List<Map.Entry<String, Integer>> top = plugin.getRaidCrystalManager().topFactions(9);
        int[] slots = {10,11,12,13,14,15,16,21,23};
        int i = 0;
        for (Map.Entry<String, Integer> e : top) {
            if (i >= slots.length) break;
            inv.setItem(slots[i], entry(p, i + 1, e.getKey(), e.getValue()));
            i++;
        }
        if (top.isEmpty()) inv.setItem(13, VetroForgeUtil.item(Material.PAPER, tr(p, "top.crystals.empty")));
        inv.setItem(31, VetroForgeUtil.item(Material.BARRIER, tr(p, "gui.close")));
        p.openInventory(inv);
    }

    private ItemStack entry(Player player, int rank, String fac, int value) {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta im = it.getItemMeta();
        String name = (plugin.getFactionManager()!=null ? plugin.getFactionManager().getDisplayName(fac) : fac);
        im.setDisplayName(tr(player, "top.crystals.entry", VetroForgeUtil.vars(
                "rank", rank,
                "faction", name
        )));
        im.setLore(List.of(tr(player, "top.crystals.amount", VetroForgeUtil.vars(
                "amount", String.format(Locale.US,"%,d", value)
        ))));
        it.setItemMeta(im);
        return it;
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder)) return;
        event.setCancelled(true);
        if (event.getRawSlot() == 31) event.getWhoClicked().closeInventory();
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    private String tr(Player player, String key) {
        return VetroForgeUtil.tr(plugin, player, key);
    }

    private String tr(Player player, String key, Map<String, String> vars) {
        return VetroForgeUtil.tr(plugin, player, key, vars);
    }

    private static final class Holder implements InventoryHolder {
        @Override public Inventory getInventory(){ return null; }
    }
}
