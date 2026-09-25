package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.skills.SkillType;
import com.vetroforge.skills.data.SkillState;
import com.vetroforge.skills.util.SkillParticleUtil;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;

public class SkillTreeGui implements Listener {

    private final FactionsPlugin plugin;

    private static final int[] GRID = {
            10,11,12,13,14,
            19,20,21,22,23,
            28,29,30,31,32,
            37,38,39,40,41,
            46,47,48,49,50
    };

    public SkillTreeGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        Bukkit.getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player p, SkillType type) {

        Inventory inv = Bukkit.createInventory(new Holder(type), 54, "§4§lVetroForge §8• §c" + pretty(type) + " Tree");

        SkillState st = plugin.getSkillManager().getState(p, type);

        ItemStack filler = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int i = 0; i < 54; i++) {
            inv.setItem(i, filler);
        }

        // Erst Verbindungen, dann Nodes. Sonst werden freigeschaltete Level von Glas überschrieben.
        drawConnections(inv);

        // 🌳 Nodes
        for (int i = 0; i < GRID.length; i++) {
            int level = i + 1;
            inv.setItem(GRID[i], node(p, level, st.level));
        }

        inv.setItem(53, detailButton(p, type));

        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1, 1.2f);
    }

    private ItemStack node(Player player, int level, int playerLevel) {

        Material mat;
        String status;

        if (level <= playerLevel) {
            mat = Material.EMERALD_BLOCK;
            status = tr(player, "skills.tree.status.unlocked");
        } else if (level == playerLevel + 1) {
            mat = Material.GOLD_BLOCK;
            status = tr(player, "skills.tree.status.next");
        } else {
            mat = Material.COAL_BLOCK;
            status = tr(player, "skills.tree.status.locked");
        }

        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();

        im.setDisplayName(tr(player, "skills.tree.level", VetroForgeUtil.vars("level", level)));

        List<String> lore = new ArrayList<>();
        lore.add("§8Status");
        lore.add(status);
        lore.add("");
        if (level <= playerLevel) {
            lore.add("§7Freigeschaltet und aktiv.");
            lore.add("§8• §aBonus ist in deinem Skillprofil gespeichert");
        } else if (level == playerLevel + 1) {
            lore.add("§7Naechster Meilenstein.");
            lore.add("§8• §eSammle XP fuer diesen Skill");
        } else {
            lore.add("§7Noch gesperrt.");
            lore.add("§8• §7Benoetigt Level §f" + level);
        }
        lore.add("");
        lore.add("§cKlick §8» §7Skill-Details oeffnen");
        im.setLore(lore);

        it.setItemMeta(im);
        return it;
    }

    private void drawConnections(Inventory inv) {

        ItemStack line = pane(Material.RED_STAINED_GLASS_PANE);

        int[] vertical = {7, 16, 25, 34, 43};

        for (int slot : vertical) {
            inv.setItem(slot, line);
        }

        int[] horizontal = {3, 5, 24, 26, 51};

        for (int slot : horizontal) {
            inv.setItem(slot, line);
        }

        inv.setItem(15, pane(Material.GRAY_STAINED_GLASS_PANE));
        inv.setItem(33, pane(Material.GRAY_STAINED_GLASS_PANE));
    }

    private ItemStack pane(Material m) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(" ");
        it.setItemMeta(im);
        return it;
    }

    private ItemStack detailButton(Player player, SkillType type) {
        ItemStack item = new ItemStack(Material.ARROW);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName("§cZurueck zu " + pretty(type));
        meta.setLore(List.of(
                "§7Oeffnet Upgrades, Respec",
                "§7und Ability-Punkte wieder."
        ));
        item.setItemMeta(meta);
        return item;
    }

    private String pretty(SkillType type) {
        String value = type.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String tr(Player player, String key) {
        return VetroForgeUtil.tr(plugin, player, key);
    }

    private String tr(Player player, String key, Map<String, String> vars) {
        return VetroForgeUtil.tr(plugin, player, key, vars);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof Holder holder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (holder.type == null) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;
        if (clicked.getType() == Material.ARROW) {
            plugin.getSkillsGui().openDetail(player, holder.type);
            return;
        }
        if (Arrays.stream(GRID).anyMatch(slot -> slot == event.getRawSlot())) {
            plugin.getSkillsGui().openDetail(player, holder.type);
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        if (event.getView().getTopInventory().getHolder() instanceof Holder) event.setCancelled(true);
    }

    public static final class Holder implements InventoryHolder {
        private final SkillType type;

        private Holder(SkillType type) {
            this.type = type;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
