// src/main/java/com/vetroforge/gui/SkillsGui.java
package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.SkillManager;
import com.vetroforge.skills.SkillType;
import com.vetroforge.skills.def.AbilityDef;
import com.vetroforge.skills.def.SkillDef;
import com.vetroforge.skills.data.AbilityState;
import com.vetroforge.skills.data.SkillState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.*;
import com.vetroforge.util.VetroForgeUtil;

public class SkillsGui implements Listener {

    private final FactionsPlugin plugin;
    private static final Map<SkillType, Material> ICONS = Map.of(
            SkillType.ASSASSIN, Material.IRON_SWORD,
            SkillType.HEALER, Material.GOLDEN_APPLE,
            SkillType.WARRIOR, Material.SHIELD,
            SkillType.RAIDER, Material.TNT
    );

    public SkillsGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /* =================== OPENERS =================== */

    private static final int[] MAIN_PATH_SLOTS = {11, 13, 15, 20, 22, 24};

    public void openMain(Player p) {
        SkillManager sm = plugin.getSkillManager();
        Inventory inv = Bukkit.createInventory(new SkillsHolder(null), 36, tr(p, "skills.gui.title"));
        border(inv, Material.PURPLE_STAINED_GLASS_PANE);

        inv.setItem(4, headerBook());

        int i = 0;
        for (SkillType t : SkillType.values()) {
            if (!sm.isEnabled(p, t)) continue;
            if (i >= MAIN_PATH_SLOTS.length) break;
            inv.setItem(MAIN_PATH_SLOTS[i++], skillButton(p, t));
        }
        inv.setItem(31, infoBook());
        p.openInventory(inv);
        p.playSound(p.getLocation(), Sound.UI_BUTTON_CLICK, 1f, 1.2f);
    }

    private static final int[] ABILITY_GRID_SLOTS = {
            19,20,21,22,23,24,25,
            28,29,30,31,32,33,34,
            37,38,39,40,41,42,43
    };

    public void openDetail(Player p, SkillType t) {
        SkillManager sm = plugin.getSkillManager();
        SkillDef def = sm.def(t);
        SkillState st = sm.getState(p, t);

        Inventory inv = Bukkit.createInventory(new SkillsHolder(t), 54, tr(p, "skills.gui.detail_title", "skill", pretty(t)));
        border(inv, Material.PURPLE_STAINED_GLASS_PANE);

        // Header + Stage-Upgrade + Skill-Tree-Button, top row together
        inv.setItem(4, headerItem(t, st, def));
        inv.setItem(11, upgradeStageButton(st, def));
        inv.setItem(13, treeButton(p));
        inv.setItem(15, respecButton());

        // Abilities: zentriert im 7x3-Grid statt sequentiell von oben-links aufgefuellt --
        // bei wenigen Abilities (z.B. nur 1) sah ein linksbuendiger Einzeleintrag schief aus.
        if (def.abilities != null) {
            int[] targetSlots = centeredAbilitySlots(def.abilities.size());
            for (int i = 0; i < def.abilities.size() && i < targetSlots.length; i++) {
                inv.setItem(targetSlots[i], abilityButton(st, def.abilities.get(i)));
            }
        }

        inv.setItem(49, backButton(p));
        p.openInventory(inv);
    }

    /**
     * Verteilt n Abilities zentriert auf das ABILITY_GRID_SLOTS-Raster (7 Spalten, 3 Zeilen)
     * statt sie sequentiell von oben-links aufzufuellen. Zeilen werden gleichmaessig belegt
     * und selbst horizontal zentriert; bei weniger als 3 Zeilen wird auch vertikal zentriert
     * (1 Ability landet so exakt in der Mitte des Grids statt oben links).
     */
    private int[] centeredAbilitySlots(int n) {
        if (n <= 0) return new int[0];
        final int cols = 7;
        int rows = Math.min(3, (int) Math.ceil(n / (double) cols));
        int rowStart = (3 - rows) / 2;

        int[] result = new int[Math.min(n, ABILITY_GRID_SLOTS.length)];
        int placed = 0;
        int remaining = n;

        for (int r = 0; r < rows && placed < n; r++) {
            int itemsThisRow = Math.min(cols, (int) Math.ceil(remaining / (double) (rows - r)));
            int leftPad = (cols - itemsThisRow) / 2;
            int rowSlotBase = (rowStart + r) * cols;

            for (int c = 0; c < itemsThisRow && placed < n; c++) {
                int gridIndex = rowSlotBase + leftPad + c;
                if (gridIndex < ABILITY_GRID_SLOTS.length) {
                    result[placed++] = ABILITY_GRID_SLOTS[gridIndex];
                }
            }
            remaining -= itemsThisRow;
        }
        return result;
    }

    /* =================== ITEMS =================== */

    private ItemStack treeButton(Player player) {
        ItemStack it = new ItemStack(Material.MAP);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(tr(player, "skills.gui.tree"));
        im.setLore(Arrays.asList(
                ChatColor.GRAY + "Zeigt Meilensteine 1–25,",
                ChatColor.GRAY + "Rewards und Ultimate."
        ));
        it.setItemMeta(im);
        return it;
    }

    private ItemStack skillButton(Player p, SkillType t) {

        SkillManager sm = plugin.getSkillManager();
        SkillDef def = sm.def(t);
        SkillState st = sm.getState(p, t);

        int need = Math.max(1, def.xpForLevel(st.level));
        String prog = progress(st.xp, need);

        Material m = ICONS.getOrDefault(t, Material.BARRIER); // ✅ t ist hier gültig

        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(ChatColor.YELLOW + pretty(t));
        im.setLore(Arrays.asList(
                ChatColor.GRAY + "Level: " + ChatColor.WHITE + st.level + ChatColor.DARK_GRAY + "  |  "
                        + ChatColor.GRAY + "Stage: " + ChatColor.WHITE + st.stage,
                ChatColor.GRAY + "Fortschritt: " + ChatColor.GREEN + st.xp + ChatColor.GRAY + "/" + ChatColor.WHITE + need,
                prog,
                ChatColor.GRAY + "Skill-Punkte: " + ChatColor.WHITE + st.skillPoints,
                ChatColor.GRAY + "Ability-Punkte: " + ChatColor.WHITE + st.abilityPoints,
                "",
                ChatColor.GREEN + "Klicken: Details & Abilities"
        ));
        it.setItemMeta(im);
        return it;
    }

    private ItemStack headerItem(SkillType t, SkillState st, SkillDef def) {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(ChatColor.GOLD + "Übersicht: " + pretty(t));
        int need = Math.max(1, def.xpForLevel(st.level));
        im.setLore(Arrays.asList(
                ChatColor.GRAY + "Level: " + ChatColor.WHITE + st.level + ChatColor.DARK_GRAY + "  |  "
                        + ChatColor.GRAY + "Stage: " + ChatColor.WHITE + st.stage,
                ChatColor.GRAY + "XP: " + ChatColor.GREEN + st.xp + ChatColor.GRAY + "/" + ChatColor.WHITE + need,
                progress(st.xp, need),
                ChatColor.GRAY + "Skill-Punkte: " + ChatColor.WHITE + st.skillPoints,
                ChatColor.GRAY + "Ability-Punkte: " + ChatColor.WHITE + st.abilityPoints
        ));
        it.setItemMeta(im);
        return it;
    }

    private ItemStack upgradeStageButton(SkillState st, SkillDef def) {
        ItemStack it = new ItemStack(Material.AMETHYST_SHARD);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(ChatColor.LIGHT_PURPLE + "Stage erhöhen");
        int next = Math.min(def.maxStage, st.stage + 1);
        List<String> lore = new ArrayList<>();
        if (st.stage >= def.maxStage) {
            lore.add(ChatColor.GRAY + "Max Stage erreicht.");
        } else {
            // Index sicher clampen (next-1)
            int reqIdx = Math.max(0, Math.min(next - 1, Math.max(0, def.stageReqLevel.size() - 1)));
            int req = def.stageReqLevel.isEmpty() ? 0 : def.stageReqLevel.get(reqIdx);
            lore.add(ChatColor.GRAY + "Nächste Stage: " + ChatColor.WHITE + next);
            lore.add(ChatColor.GRAY + "Benötigt Skill-Level: " + ChatColor.WHITE + req);
            lore.add(ChatColor.GRAY + "Kosten: " + ChatColor.WHITE + "1 Skill-Punkt");
            lore.add("");
            lore.add(ChatColor.GREEN + "Klicken zum Erhöhen");
        }
        im.setLore(lore);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack abilityButton(SkillState st, AbilityDef a) {

        AbilityState as = st.abilities.getOrDefault(a.id, new AbilityState());

        ItemStack it = new ItemStack(a.autoLevel ? Material.BOOK : Material.ENCHANTED_BOOK);
        ItemMeta im = it.getItemMeta();

        im.setDisplayName((a.enabled ? "§b" : "§8") + a.display +
                " §7[" + as.level + "/" + a.maxLevel + "]");

        List<String> lore = new ArrayList<>();

        // 🔥 TYPE
        lore.add("§7Typ: " + (a.autoLevel ? "§eAuto" : "§fManuell"));

        // 💥 STAT BOOST PREVIEW
        lore.add("");
        lore.add("§6Effekt:");

        switch (a.id.toLowerCase()) {

            case "damage_boost" -> {
                double current = as.level * 5;
                double next = (as.level + 1) * 5;

                lore.add("§7Damage Bonus:");
                lore.add(" §8• §fAktuell: §c+" + current + "%");
                if (as.level < a.maxLevel)
                    lore.add(" §8• §fNext: §a+" + next + "%");
            }

            case "demolition" -> {
                double current = as.level * 25;
                double next = (as.level + 1) * 25;

                lore.add("§7TNT Power:");
                lore.add(" §8• §fAktuell: §c+" + current + "%");
                if (as.level < a.maxLevel)
                    lore.add(" §8• §fNext: §a+" + next + "%");
            }

            case "crystal_storage" -> {
                int current = as.level * 25;
                int next = (as.level + 1) * 25;

                lore.add("§7Crystal Cap:");
                lore.add(" §8• §fAktuell: §b+" + current);
                if (as.level < a.maxLevel)
                    lore.add(" §8• §fNext: §a+" + next);
            }

            case "backstab" -> {
                double current = as.level * 6;
                double next = (as.level + 1) * 6;

                lore.add("§7Schaden von hinten:");
                lore.add(" §8• §fAktuell: §c+" + current + "%");
                if (as.level < a.maxLevel)
                    lore.add(" §8• §fNext: §a+" + next + "%");
            }

            case "recovery" -> {
                double current = as.level * 4;
                double next = (as.level + 1) * 4;

                lore.add("§7Notfall-Regeneration:");
                lore.add(" §8• §fAktuell: §a" + current + "s");
                if (as.level < a.maxLevel)
                    lore.add(" §8• §fNext: §a" + next + "s");
            }

            default -> lore.add("§7Keine Daten");
        }

        // 📊 REQUIREMENTS
        lore.add("");
        lore.add("§7Requirements:");

        for (int i = 1; i <= a.maxLevel; i++) {
            int req = a.reqSkillLv.get(Math.min(i - 1, a.reqSkillLv.size() - 1));

            String mark = (i <= as.level) ? "§a✔ " : "§7• ";
            lore.add(mark + "§7Lvl " + i + ": §fSkill " + req);
        }

        // 💸 COST
        if (!a.autoLevel) {
            lore.add("");
            lore.add("§7Kosten: §f" + a.costPerLevel + " AP");
            lore.add("§aKlicken zum upgraden");
        }

        im.setLore(lore);
        it.setItemMeta(im);

        return it;
    }

    private ItemStack backButton(Player player) {
        ItemStack it = new ItemStack(Material.BARRIER);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(tr(player, "common.back"));
        it.setItemMeta(im);
        return it;
    }

    private void fill(Inventory inventory) {
        ItemStack pane = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta meta = pane.getItemMeta();
        meta.setDisplayName(" ");
        pane.setItemMeta(meta);
        for (int slot = 0; slot < inventory.getSize(); slot++) inventory.setItem(slot, pane);
    }

    /** Farbiger Rahmen (erste/letzte Reihe + Seitenspalten), Rest schwarz. */
    private void border(Inventory inv, Material frameMaterial) {
        ItemStack frame = new ItemStack(frameMaterial);
        ItemMeta frameMeta = frame.getItemMeta();
        frameMeta.setDisplayName(" ");
        frame.setItemMeta(frameMeta);

        ItemStack black = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta blackMeta = black.getItemMeta();
        blackMeta.setDisplayName(" ");
        black.setItemMeta(blackMeta);

        int size = inv.getSize();
        int rows = size / 9;
        for (int slot = 0; slot < size; slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean edge = row == 0 || row == rows - 1 || col == 0 || col == 8;
            inv.setItem(slot, edge ? frame : black);
        }
    }

    private ItemStack headerBook() {
        ItemStack it = new ItemStack(Material.NETHER_STAR);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName("§d§lSkills");
        im.setLore(List.of(
                "§7Wähle einen Pfad, um Level,",
                "§7Stages und Abilities zu verwalten."
        ));
        it.setItemMeta(im);
        return it;
    }

    private String progress(int current, int max) {
        double ratio = Math.max(0.0D, Math.min(1.0D, current / (double) Math.max(1, max)));
        int green = (int) Math.round(ratio * 12);
        return ChatColor.DARK_GRAY + "[" + ChatColor.GREEN + "■".repeat(green)
                + ChatColor.DARK_GRAY + "■".repeat(12 - green) + ChatColor.DARK_GRAY + "]";
    }

    private ItemStack infoBook() {
        ItemStack it = new ItemStack(Material.WRITTEN_BOOK);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(ChatColor.YELLOW + "Skill-System 2.0");
        im.setLore(Arrays.asList(
                ChatColor.GRAY + "Vier Pfade mit passiven Boni:",
                ChatColor.DARK_GRAY + "• " + ChatColor.RED + "Warrior " + ChatColor.GRAY + "Damage & Tankiness",
                ChatColor.DARK_GRAY + "• " + ChatColor.DARK_PURPLE + "Assassin " + ChatColor.GRAY + "Burst & Mobility",
                ChatColor.DARK_GRAY + "• " + ChatColor.GREEN + "Healer " + ChatColor.GRAY + "Recovery & Support",
                ChatColor.DARK_GRAY + "• " + ChatColor.GOLD + "Raider " + ChatColor.GRAY + "TNT, Energy, Crystals",
                "",
                ChatColor.GRAY + "Respec gibt manuelle AP zurück."
        ));
        it.setItemMeta(im);
        return it;
    }

    private ItemStack respecButton() {
        ItemStack it = new ItemStack(Material.REDSTONE);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(ChatColor.RED + "Respec manuelle Abilities");
        im.setLore(List.of(
                ChatColor.GRAY + "Setzt manuelle Ability-Upgrades zurück.",
                ChatColor.GRAY + "Auto-Passives bleiben erhalten.",
                "",
                ChatColor.YELLOW + "Klicken zum Respec"
        ));
        it.setItemMeta(im);
        return it;
    }

    private String pretty(SkillType t) {
        String s = t.name().toLowerCase(Locale.ROOT);
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    /* =================== CLICK HANDLER =================== */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getView() == null || e.getView().getTitle() == null) return;

        if (!(e.getInventory().getHolder() instanceof SkillsHolder holder)) return;
        ItemStack clicked = e.getCurrentItem();
        if (clicked == null || clicked.getType() == Material.AIR) return;

        // Hauptmenü
        if (holder.skill == null) {
            e.setCancelled(true);
            if (clicked.hasItemMeta() && clicked.getItemMeta().hasDisplayName()) {
                String dn = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                for (SkillType t : SkillType.values()) {
                    if (dn.equalsIgnoreCase(pretty(t))) {
                        openDetail(p, t);
                        return;
                    }
                }
            }
            return;
        }

        // Detail
        if (holder.skill != null) {
            e.setCancelled(true);

            // Back
            if (clicked.getType() == Material.BARRIER) {
                openMain(p);
                return;
            }

            // SkillType aus Title
            SkillType t = holder.skill;

            // Stage-Upgrade?
            if (clicked.getType() == Material.AMETHYST_SHARD) {
                plugin.getSkillManager().upgradeStage(p, t);
                openDetail(p, t);
                return;
            }

            // Skill-Tree öffnen?
            if (clicked.getType() == Material.MAP) {
                plugin.getSkillTreeGui().open(p, t);
                return;
            }

            if (clicked.getType() == Material.REDSTONE) {
                plugin.getSkillManager().respec(p, t);
                openDetail(p, t);
                return;
            }

            // Ability-Upgrade?
            if (clicked.getType() == Material.ENCHANTED_BOOK || clicked.getType() == Material.BOOK) {
                if (!clicked.hasItemMeta() || clicked.getItemMeta().getDisplayName() == null) return;
                String dn = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
                int cut = dn.indexOf(" [");
                String abilityName = (cut >= 0) ? dn.substring(0, cut) : dn;

                var def = plugin.getSkillManager().def(t);
                if (def.abilities != null) {
                    for (AbilityDef a : def.abilities) {
                        if (a.display.equalsIgnoreCase(abilityName)) {
                            boolean ok = plugin.getSkillManager().upgradeAbility(p, t, a.id);
                            if (ok) openDetail(p, t);
                            return;
                        }
                    }
                }
            }
        }
    }

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onDrag(InventoryDragEvent e) {
        if (e.getInventory().getHolder() instanceof SkillsHolder) {
            e.setCancelled(true);
        }
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, key, VetroForgeUtil.vars(values));
    }

    private static final class SkillsHolder implements InventoryHolder {
        private final SkillType skill;

        private SkillsHolder(SkillType skill) {
            this.skill = skill;
        }

        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
