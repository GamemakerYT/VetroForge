// src/main/java/com/vetroforge/gui/RaidGui.java
package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.List;
import com.vetroforge.util.VetroForgeUtil;

public class RaidGui {

    private final FactionsPlugin plugin;

    public RaidGui(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    private static final int[] BORDER_SLOTS = {0,1,2,3,5,6,7,8, 9,17, 18,19,20,21,23,24,25,26};

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(new Holder(), 27, tr(p, "gui.title"));

        for (int slot : BORDER_SLOTS) inv.setItem(slot, pane(Material.RED_STAINED_GLASS_PANE));
        for (int slot = 0; slot < inv.getSize(); slot++) if (inv.getItem(slot) == null) inv.setItem(slot, pane(Material.BLACK_STAINED_GLASS_PANE));

        inv.setItem(4, create(
                Material.NETHERITE_SWORD,
                "§4§lRaid-System",
                "§7Scoute Gegner, starte Angriffe",
                "§7und ruest dich im Raid-Shop aus."
        ));

        String faction = getFaction(p);

        int energy = getEnergy(p);
        int crystals = getCrystals(p);

        boolean scouting = false;
        boolean inRaid = false;
        boolean joinable = false;
        String target = null;

        try {
            scouting = plugin.getRaidSessionService() != null && plugin.getRaidSessionService().isScouting(p);
            inRaid = plugin.getRaidSessionService() != null && plugin.getRaidSessionService().isInRaid(p);
            target = plugin.getRaidSessionService() == null ? null
                    : scouting ? plugin.getRaidSessionService().getScoutingTargetFaction(p)
                    : plugin.getRaidSessionService().getActiveRaidTargetForFaction(faction);
            joinable = !inRaid && target != null && !target.isBlank();
        } catch (Throwable ignored) {}

        inv.setItem(10, create(
                inRaid ? Material.PAPER : scouting ? Material.DIAMOND_SWORD : joinable ? Material.ENDER_PEARL : Material.SPYGLASS,
                inRaid ? tr(p, "gui.stats.name") : scouting ? tr(p, "gui.attack.name") : joinable ? tr(p, "gui.join.name") : tr(p, "gui.start.name"),
                inRaid ? tr(p, "gui.stats.description") : scouting ? tr(p, "gui.attack.description") : joinable ? tr(p, "gui.join.description") : tr(p, "gui.start.description"),
                "",
                scouting || joinable || inRaid ? tr(p, "gui.target", "target", target) : tr(p, "gui.costs"),
                scouting || joinable || inRaid ? "" : tr(p, "gui.energy_cost", "current", energy),
                scouting || joinable || inRaid ? "" : tr(p, "gui.crystal_cost", "current", crystals),
                "",
                inRaid ? tr(p, "gui.click_stats") : scouting ? tr(p, "gui.click_attack") : joinable ? tr(p, "gui.click_join") : tr(p, "gui.click_start")
        ));

        inv.setItem(13, create(
                Material.BARRIER,
                tr(p, "gui.cancel.name"),
                tr(p, "gui.cancel.description"),
                "",
                scouting ? tr(p, "gui.cancel.scouting") : inRaid ? tr(p, "gui.cancel.raid") : tr(p, "gui.cancel.none")
        ));

        inv.setItem(16, create(
                Material.EMERALD,
                tr(p, "gui.shop.name"),
                tr(p, "gui.shop.description"),
                "",
                tr(p, "gui.click_open")
        ));

        inv.setItem(22, create(
                scouting ? Material.ENDER_EYE : inRaid ? Material.REDSTONE_BLOCK : Material.GRAY_DYE,
                scouting ? tr(p, "gui.status.scouting") : inRaid ? tr(p, "gui.status.raid") : tr(p, "gui.status.ready"),
                tr(p, "gui.status.faction", "faction", faction == null ? "-" : faction),
                tr(p, "gui.status.target", "target", target == null ? "-" : target),
                tr(p, "gui.status.energy", "energy", energy),
                tr(p, "gui.status.crystals", "crystals", crystals)
        ));

        p.openInventory(inv);
    }

    private String getFaction(Player p) {
        try {
            if (plugin.getFactionManager() == null) return null;
            return plugin.getFactionManager().getFactionOf(p.getUniqueId());
        } catch (Throwable ignored) {
            return null;
        }
    }

    private int getEnergy(Player p) {
        return plugin.getEnergyManager() == null ? 0 : plugin.getEnergyManager().get(p.getUniqueId());
    }

    private int getCrystals(Player player) {
        return plugin.getRaidCrystalManager() == null ? 0 : plugin.getRaidCrystalManager().getAvailable(player);
    }

    private ItemStack create(Material mat, String name, String... lore) {
        return VetroForgeUtil.item(mat, name, lore);
    }

    private ItemStack pane(Material mat) {
        ItemStack item = new ItemStack(mat);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private String tr(Player player, String key, Object... values) {
        return com.vetroforge.raid.RaidText.tr(plugin, player, key, values);
    }

    public static final class Holder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
