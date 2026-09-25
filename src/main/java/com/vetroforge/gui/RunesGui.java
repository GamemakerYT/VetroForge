// src/main/java/com/vetroforge/gui/RunesGui.java
package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.raid.RaidText;
import com.vetroforge.raid.runes.RuneType;
import com.vetroforge.raid.runes.RuneUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

import java.util.ArrayList;
import java.util.List;

/**
 * Themed front-end fuer das bestehende Runen-System (RuneUtil/RuneType, bisher nur per
 * "/rune apply <type>" erreichbar): Item in den markierten Slot legen, dann eine der
 * Runen-Buttons anklicken, um sie gegen Kriegsbeute aufzutragen. Die eigentliche
 * Anwendungslogik (Kosten, Dubletten-Check, PDC/Lore) bleibt unveraendert in RuneUtil --
 * diese GUI dupliziert sie nicht, sondern ruft sie direkt auf.
 */
public final class RunesGui implements Listener {

    private static final int ITEM_SLOT = 22;
    private static final int[] RUNE_SLOTS = {11, 13, 15};

    private final FactionsPlugin plugin;

    public RunesGui(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public void open(Player player) {
        Inventory inv = Bukkit.createInventory(new RunesHolder(), 27, "§5§lRunen §8» §7Item verzaubern");
        decorate(inv);
        render(inv, player);
        player.openInventory(inv);
    }

    private void decorate(Inventory inv) {
        ItemStack purple = pane(Material.PURPLE_STAINED_GLASS_PANE);
        ItemStack black = pane(Material.BLACK_STAINED_GLASS_PANE);
        for (int slot = 0; slot < inv.getSize(); slot++) {
            int row = slot / 9;
            int col = slot % 9;
            boolean edge = row == 0 || row == 2 || col == 0 || col == 8;
            inv.setItem(slot, edge ? purple : black);
        }
        inv.setItem(4, info(Material.AMETHYST_SHARD, "§5§lRunen",
                "§7Lege ein Schwert, eine Ruestung",
                "§7oder ein Custom-Item in den Slot.",
                "",
                "§7Klicke danach eine Rune an,",
                "§7um sie gegen §dKriegsbeute§7",
                "§7aufzutragen."));
        inv.setItem(ITEM_SLOT, null);
    }

    private void render(Inventory inv, Player player) {
        ItemStack held = inv.getItem(ITEM_SLOT);
        RuneType[] types = RuneType.values();
        for (int i = 0; i < RUNE_SLOTS.length && i < types.length; i++) {
            inv.setItem(RUNE_SLOTS[i], runeButton(player, types[i], held));
        }
    }

    private ItemStack runeButton(Player player, RuneType type, ItemStack held) {
        int cost = Math.max(0, plugin.getConfig().getInt("runes.cost." + type.name(), 50));
        boolean applied = held != null && !held.getType().isAir() && RuneUtil.hasRune(plugin, held, type);
        int balance = plugin.getRaidCrystalManager() != null ? plugin.getRaidCrystalManager().getAvailable(player) : 0;

        List<String> lore = new ArrayList<>();
        lore.add(type.display());
        lore.add("");
        if (applied) {
            lore.add("§aBereits auf diesem Item.");
        } else {
            lore.add("§7Kosten: §d" + cost + " Kriegsbeute");
            lore.add("§7Dein Guthaben: §f" + balance + " Kriegsbeute");
            lore.add("");
            lore.add(balance >= cost ? "§eKlick §8» §fAuftragen" : "§cNicht genug Kriegsbeute.");
        }

        Material icon = switch (type) {
            case RAID_DAMAGE -> Material.IRON_SWORD;
            case TNT_EXPERT -> Material.TNT;
            case CRYSTAL_GAIN -> Material.AMETHYST_CLUSTER;
        };
        ItemStack item = info(icon, type.display(), lore.toArray(new String[0]));
        if (applied) {
            ItemMeta meta = item.getItemMeta();
            meta.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
            meta.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            item.setItemMeta(meta);
        }
        return item;
    }

    @EventHandler(priority = EventPriority.LOW)
    public void onClick(InventoryClickEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RunesHolder)) return;
        if (!(event.getWhoClicked() instanceof Player player)) return;

        boolean clickedTop = event.getClickedInventory() != null
                && event.getClickedInventory().getHolder() instanceof RunesHolder;
        if (!clickedTop) return;

        int slot = event.getSlot();
        if (slot == ITEM_SLOT) {
            // Normales Ablegen/Entnehmen erlaubt; nach dem Klick neu rendern (naechster Tick,
            // da der Klick selbst den Slot erst danach aktualisiert).
            Bukkit.getScheduler().runTask(plugin, () -> render(event.getInventory(), player));
            return;
        }

        event.setCancelled(true);

        int idx = indexOf(slot);
        if (idx < 0) return;
        RuneType type = RuneType.values()[idx];

        ItemStack held = event.getInventory().getItem(ITEM_SLOT);
        if (held == null || held.getType().isAir()) {
            RaidText.send(plugin, player, "message.rune_item_required");
            return;
        }
        if (RuneUtil.hasRune(plugin, held, type)) {
            RaidText.send(plugin, player, "message.rune_already_applied", "rune", type.name());
            return;
        }

        int cost = Math.max(0, plugin.getConfig().getInt("runes.cost." + type.name(), 50));
        if (cost > 0 && (plugin.getRaidCrystalManager() == null || !plugin.getRaidCrystalManager().consume(player, cost))) {
            RaidText.send(plugin, player, "message.rune_insufficient_crystals", "cost", cost);
            return;
        }

        RuneUtil.addRune(plugin, held, type);
        event.getInventory().setItem(ITEM_SLOT, held);
        RaidText.send(plugin, player, "message.rune_added", "rune", type.name(), "cost", cost);
        try { player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_CHIME, 1f, 1f); } catch (Throwable ignored) {}

        render(event.getInventory(), player);
    }

    @EventHandler
    public void onClose(InventoryCloseEvent event) {
        if (!(event.getView().getTopInventory().getHolder() instanceof RunesHolder)) return;
        ItemStack leftover = event.getInventory().getItem(ITEM_SLOT);
        if (leftover != null && !leftover.getType().isAir() && event.getPlayer() instanceof Player player) {
            player.getInventory().addItem(leftover).values()
                    .forEach(left -> player.getWorld().dropItemNaturally(player.getLocation(), left));
        }
    }

    private int indexOf(int slot) {
        for (int i = 0; i < RUNE_SLOTS.length; i++) if (RUNE_SLOTS[i] == slot) return i;
        return -1;
    }

    private ItemStack pane(Material material) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(" ");
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack info(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private static final class RunesHolder implements InventoryHolder {
        @Override
        public Inventory getInventory() {
            return null;
        }
    }
}
