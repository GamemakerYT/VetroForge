package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.PrestigeUtil;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import java.util.Map;

public final class PrestigeGui implements Listener {

    private final FactionsPlugin plugin;

    public PrestigeGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        int level = plugin.getPrestigeManager().getLevel(player);
        int xp = plugin.getPrestigeManager().getXp(player.getUniqueId());
        int need = plugin.getPrestigeManager().xpNeededFor(level);
        int pct = plugin.getPrestigeManager().getProgressPct(player.getUniqueId());

        Inventory inventory = Bukkit.createInventory(new Holder(), 27,
                VetroForgeUtil.tr(plugin, player, "prestige.gui.title") + " §7• §6" + PrestigeUtil.titleFor(level));
        VetroForgeUtil.fill(inventory, Material.BLACK_STAINED_GLASS_PANE);
        inventory.setItem(11, VetroForgeUtil.item(
                Material.NETHER_STAR,
                VetroForgeUtil.tr(plugin, player, "prestige.gui.overview"),
                VetroForgeUtil.tr(plugin, player, "prestige.gui.level", Map.of("level", String.valueOf(level))),
                VetroForgeUtil.tr(plugin, player, "prestige.gui.rank", Map.of("rank", PrestigeUtil.titleFor(level))),
                VetroForgeUtil.tr(plugin, player, "prestige.gui.xp", Map.of("xp", String.valueOf(xp), "need", String.valueOf(need))),
                plugin.getPrestigeManager().progressBar(player.getUniqueId())
        ));
        inventory.setItem(13, VetroForgeUtil.item(
                Material.EXPERIENCE_BOTTLE,
                VetroForgeUtil.tr(plugin, player, "prestige.gui.next"),
                VetroForgeUtil.tr(plugin, player, "prestige.gui.progress", Map.of("pct", String.valueOf(pct))),
                VetroForgeUtil.tr(plugin, player, "prestige.gui.remaining", Map.of("xp", String.valueOf(plugin.getPrestigeManager().getXpToNext(player.getUniqueId())))),
                "",
                "§8XP erhältst du in der Warzone,",
                "§8durch Kills und Warzone-Kisten."
        ));
        inventory.setItem(15, VetroForgeUtil.item(
                Material.DIAMOND_SWORD,
                "§cWarzone Bonus",
                "§7In der Warzone sammelst du",
                "§7zusätzliche Prestige-XP.",
                "",
                "§eKämpfen. Looten. Aufsteigen."
        ));
        player.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
