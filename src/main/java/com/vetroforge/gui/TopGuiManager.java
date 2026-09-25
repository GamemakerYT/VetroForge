// src/main/java/com/vetroforge/gui/TopGuiManager.java
package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.stats.StatsManager;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
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

import java.util.*;

public class TopGuiManager implements Listener {

    private final FactionsPlugin plugin;
    private final NamespacedKey TAG_KEY;

    public TopGuiManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.TAG_KEY = new NamespacedKey(plugin, "topgui_tag");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /** Öffnet die Top-Liste (Kills) mit Paginierung. */
    public void open(Player p, int page) {
        StatsManager stats = plugin.getStatsManager();
        List<UUID> all = new ArrayList<>(stats.topByKills(100)); // Top 100 nach Kills

        final int pageSize = 21;
        int maxPage = Math.max(1, (int) Math.ceil(all.size() / (double) pageSize));
        int resolvedPage = Math.min(Math.max(1, page), maxPage);
        int start = (resolvedPage - 1) * pageSize;
        int end = Math.min(start + pageSize, all.size());

        List<UUID> unresolved = new ArrayList<>();
        for (int i = start; i < end; i++) {
            UUID id = all.get(i);
            if (Bukkit.getOfflinePlayer(id).getName() == null) unresolved.add(id);
        }

        if (unresolved.isEmpty() || plugin.getWebApiManager() == null) {
            render(p, resolvedPage, maxPage, all, start, end, stats, Map.of());
            return;
        }

        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            Map<UUID, String> names = plugin.getWebApiManager().lookupCachedNames(unresolved);
            Bukkit.getScheduler().runTask(plugin, () -> {
                if (p.isOnline()) render(p, resolvedPage, maxPage, all, start, end, stats, names);
            });
        });
    }

    private static final int[] TOP_GRID_SLOTS = {10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};

    private void render(Player p, int page, int maxPage, List<UUID> all, int start, int end,
                         StatsManager stats, Map<UUID, String> fallbackNames) {
        Inventory inv = Bukkit.createInventory(new Holder(page, maxPage), 54,
                tr(p, "top.players.title"));
        fill(inv, pane());

        int count = end - start;
        int[] targetSlots = centeredGridSlots(count, TOP_GRID_SLOTS);
        for (int i = start, s = 0; i < end; i++, s++) {
            UUID id = all.get(i);
            inv.setItem(targetSlots[s], headCard(p, i + 1, id, stats, fallbackNames.get(id)));
        }

        // Navi
        inv.setItem(45, nav(Material.BARRIER, tr(p, "gui.close"), "nav:close"));
        inv.setItem(49, info(Material.PAPER, tr(p, "gui.page", VetroForgeUtil.vars("page", page, "max", maxPage))));
        if (page > 1) inv.setItem(48, nav(Material.ARROW, tr(p, "gui.previous"), "nav:prev"));
        if (page < maxPage) inv.setItem(50, nav(Material.ARROW, tr(p, "gui.next"), "nav:next"));

        p.openInventory(inv);
    }

    /**
     * Verteilt n Eintraege zentriert auf ein 7-Spalten-Raster (beliebig viele Zeilen aus
     * gridSlots ableitbar), statt sie sequentiell von oben-links aufzufuellen -- bei wenigen
     * Eintraegen (z.B. nur 6 von 21 moeglichen) sonst schief am linken Rand haengend.
     */
    private int[] centeredGridSlots(int n, int[] gridSlots) {
        if (n <= 0) return new int[0];
        final int cols = 7;
        int totalRows = gridSlots.length / cols;
        int rows = Math.min(totalRows, (int) Math.ceil(n / (double) cols));
        int rowStart = (totalRows - rows) / 2;

        int[] result = new int[Math.min(n, gridSlots.length)];
        int placed = 0;
        int remaining = n;

        for (int r = 0; r < rows && placed < n; r++) {
            int itemsThisRow = Math.min(cols, (int) Math.ceil(remaining / (double) (rows - r)));
            int leftPad = (cols - itemsThisRow) / 2;
            int rowSlotBase = (rowStart + r) * cols;

            for (int c = 0; c < itemsThisRow && placed < n; c++) {
                int gridIndex = rowSlotBase + leftPad + c;
                if (gridIndex < gridSlots.length) {
                    result[placed++] = gridSlots[gridIndex];
                }
            }
            remaining -= itemsThisRow;
        }
        return result;
    }

    private ItemStack headCard(Player viewer, int rank, UUID id, StatsManager stats, String fallbackName) {
        OfflinePlayer op = Bukkit.getOfflinePlayer(id);
        String name = op.getName() != null ? op.getName() : fallbackName != null ? fallbackName : id.toString();

        int kills   = stats.getKills(id);
        int deaths  = stats.getDeaths(id);
        double kdr  = stats.getKDR(id);
        int prestige= stats.getPrestige(id);

        ItemStack head = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta sm = (SkullMeta) head.getItemMeta();
        sm.setOwningPlayer(op);
        sm.setDisplayName(rankPrefix(rank) + "#" + rank + " §f" + name);
        List<String> lore = new ArrayList<>();
        lore.add("§8━━━━━━━━━━━━━");
        lore.add(tr(viewer, "top.players.kills", VetroForgeUtil.vars("amount", kills)));
        lore.add(tr(viewer, "top.players.deaths", VetroForgeUtil.vars("amount", deaths)));
        lore.add(tr(viewer, "top.players.kdr", VetroForgeUtil.vars("amount", String.format(Locale.US, "%.2f", kdr))));
        lore.add(tr(viewer, "top.players.prestige", VetroForgeUtil.vars("amount", prestige)));
        sm.setLore(lore);
        head.setItemMeta(sm);
        return head;
    }

    private String rankPrefix(int rank) {
        return switch (rank) {
            case 1 -> "§6§l";
            case 2 -> "§7§l";
            case 3 -> "§c§l";
            default -> "§7";
        };
    }

    /* ================= Clicks ================= */

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (!(top.getHolder() instanceof Holder h)) return;

        e.setCancelled(true);
        ItemStack it = e.getCurrentItem();
        String tag = getTag(it);
        if (tag == null) return;

        switch (tag) {
            case "nav:close" -> p.closeInventory();
            case "nav:prev"  -> open(p, Math.max(1, h.page - 1));
            case "nav:next"  -> open(p, Math.min(h.maxPage, h.page + 1));
        }
    }

    /* ================= Utils ================= */

    private void setTag(ItemMeta im, String value) {
        im.getPersistentDataContainer().set(TAG_KEY, PersistentDataType.STRING, value);
    }
    private String getTag(ItemStack it) {
        if (it == null || !it.hasItemMeta()) return null;
        return it.getItemMeta().getPersistentDataContainer().get(TAG_KEY, PersistentDataType.STRING);
    }

    private static void fill(Inventory inv, ItemStack f){ for (int i=0;i<inv.getSize();i++) inv.setItem(i,f); }
    private static ItemStack pane(){ ItemStack it=new ItemStack(Material.GRAY_STAINED_GLASS_PANE); ItemMeta im=it.getItemMeta(); im.setDisplayName(" "); it.setItemMeta(im); return it; }

    private ItemStack nav(Material m, String name, String tag) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        setTag(im, tag);
        it.setItemMeta(im);
        return it;
    }

    private static ItemStack info(Material m, String name) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }

    private static final class Holder implements InventoryHolder {
        final int page, maxPage;
        Holder(int page, int maxPage){ this.page=page; this.maxPage=maxPage; }
        @Override public Inventory getInventory(){ return null; }
    }

    private String tr(Player player, String key) {
        return VetroForgeUtil.tr(plugin, player, key);
    }

    private String tr(Player player, String key, Map<String, String> vars) {
        return VetroForgeUtil.tr(plugin, player, key, vars);
    }
}
