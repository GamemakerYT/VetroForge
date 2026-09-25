package com.vetroforge.gui;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.StoryManager;
import com.vetroforge.manager.StoryManager.Story;
import com.vetroforge.manager.StoryManager.Step;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class StoryGuiManager implements Listener {

    private final FactionsPlugin plugin;
    // ersetzt das alte localizedName-Tag
    private final NamespacedKey KEY_TAG;

    public StoryGuiManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.KEY_TAG = new NamespacedKey(plugin, "story_action");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /* ============== Public API ============== */

    public void openMain(Player p) {
        if (plugin.getStoryManager().hasActive(p)) openActive(p); else openList(p, 1);
    }

    public void openList(Player p, int page) {
        List<Story> all = new ArrayList<>(plugin.getStoryManager().getAllStories());
        List<String> completed = plugin.getStoryManager().getCompleted(p);
        String active = plugin.getStoryManager().getActiveId(p);

        int pageSize = 21;
        int[] slots = new int[]{10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        int maxPage = Math.max(1, (int)Math.ceil(all.size()/(double)pageSize));
        page = Math.min(Math.max(1, page), maxPage);
        int start = (page-1)*pageSize;
        int end = Math.min(start + pageSize, all.size());

        Inventory inv = Bukkit.createInventory(new Holder(View.LIST, page, 0, null), 54, cc(tr(p,"story.gui.title.list")));
        fill(inv, pane());

        for (int i=start, sIdx=0; i<end; i++, sIdx++) {
            Story st = all.get(i);
            boolean isActive = st.id.equalsIgnoreCase(active);
            boolean isDone = completed.contains(st.id);
            inv.setItem(slots[sIdx], storyCard(p, st, isActive, isDone));
        }

        inv.setItem(45, nav(Material.BARRIER, cc(tr(p,"common.close")), "close"));
        inv.setItem(49, info(Material.PAPER, cc(tr(p,"common.page", map("page", String.valueOf(page), "max", String.valueOf(maxPage))))));
        if (page > 1) inv.setItem(48, nav(Material.ARROW, cc(tr(p,"common.back")), "list:"+(page-1)));
        if (page < maxPage) inv.setItem(50, nav(Material.ARROW, cc(tr(p,"common.next")), "list:"+(page+1)));

        p.openInventory(inv);
    }

    public void openActive(Player p) {
        StoryManager sm = plugin.getStoryManager();
        String id = sm.getActiveId(p);
        if (id == null) { openList(p,1); return; }
        Story st = sm.getStory(id);
        if (st == null) { openList(p,1); return; }

        int idx = sm.getActiveStepIndex(p);
        int prog = sm.getActiveProgress(p);
        if (idx < 0) idx = 0;
        if (idx >= st.steps.size()) idx = st.steps.size()-1;
        Step step = st.steps.get(idx);

        Inventory inv = Bukkit.createInventory(new Holder(View.ACTIVE, 1, idx, id), 54, cc(tr(p,"story.gui.title.active")));
        fill(inv, pane());

        inv.setItem(10, book(ChatColor.stripColor(st.title),
                tr(p,"story.gui.step", map("idx", String.valueOf(idx+1), "total", String.valueOf(st.steps.size()))),
                ChatColor.translateAlternateColorCodes('&', step.display)));

        renderProgressBar(inv, 13, prog, step.target);

        List<String> rewardLore = new ArrayList<>();
        if (st.rewardMoney > 0) rewardLore.add("§7"+tr(p,"story.gui.reward.money", map("amount", String.valueOf(st.rewardMoney))));
        if (st.rewardPrestige > 0) rewardLore.add("§7"+tr(p,"story.gui.reward.prestige", map("amount", String.valueOf(st.rewardPrestige))));
        if (st.rewardFactionCoins > 0) rewardLore.add("§7"+tr(p,"story.gui.reward.fcoins", map("amount", String.valueOf(st.rewardFactionCoins))));
        if (st.rewardItems != null && !st.rewardItems.isEmpty())
            rewardLore.add("§7"+tr(p,"story.gui.reward.items", map("count", String.valueOf(st.rewardItems.size()))));
        inv.setItem(16, info(Material.CHEST, tr(p,"story.gui.rewards"), rewardLore));

        inv.setItem(45, nav(Material.BOOKSHELF, tr(p,"story.gui.to_list"), "list:1"));
        inv.setItem(49, nav(Material.OAK_SIGN, tr(p,"story.gui.abandon"), "abandon:"+id));
        inv.setItem(53, nav(Material.COMPASS, tr(p,"story.gui.hint"), "hint:"+id));

        p.openInventory(inv);
    }

    public void openStartConfirm(Player p, String storyId) {
        Story st = plugin.getStoryManager().getStory(storyId);
        if (st == null) { openList(p,1); return; }
        Inventory inv = Bukkit.createInventory(new Holder(View.START_CONFIRM, 1, 0, storyId), 27, cc(tr(p,"story.gui.title.confirm")));
        fill(inv, pane());
        inv.setItem(11, nav(Material.LIME_WOOL, tr(p,"common.confirm"), "start:yes:"+storyId));
        inv.setItem(15, nav(Material.RED_WOOL,  tr(p,"common.cancel"), "start:no:"+storyId));
        p.openInventory(inv);
    }

    public void openAbandonConfirm(Player p, String storyId) {
        Inventory inv = Bukkit.createInventory(new Holder(View.ABANDON_CONFIRM, 1, 0, storyId), 27, cc(tr(p,"story.gui.title.abandon")));
        fill(inv, pane());
        inv.setItem(11, nav(Material.LIME_WOOL, tr(p,"common.confirm"), "abandon:yes:"+storyId));
        inv.setItem(15, nav(Material.RED_WOOL,  tr(p,"common.cancel"), "abandon:no:"+storyId));
        p.openInventory(inv);
    }

    /* ============== Rendering Helpers ============== */

    private ItemStack storyCard(Player p, Story st, boolean active, boolean done) {
        Material mat = done ? Material.WRITABLE_BOOK : (active ? Material.ENCHANTED_BOOK : Material.BOOK);
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName("§f" + ChatColor.stripColor(st.title));
        List<String> lore = new ArrayList<>();
        if (done) lore.add("§b" + tr(p,"story.gui.state.done"));
        else if (active) lore.add("§e" + tr(p,"story.gui.state.active"));
        else lore.add("§a" + tr(p,"story.gui.state.available"));
        im.setLore(lore);
        setTag(im, "story:"+st.id);
        it.setItemMeta(im);
        return it;
    }

    private void renderProgressBar(Inventory inv, int centerSlot, int progress, int total) {
        int filled = total <= 0 ? 0 : (int)Math.round(9.0 * Math.min(progress, total) / (double) total);
        for (int i = -4; i <= 4; i++) {
            int slot = centerSlot + i;
            Material m = i < -1 ? Material.GRAY_STAINED_GLASS_PANE
                    : (i <= 1 ? Material.LIGHT_GRAY_STAINED_GLASS_PANE
                    : Material.WHITE_STAINED_GLASS_PANE);
            if ((i + 5) <= filled) m = Material.LIME_STAINED_GLASS_PANE;
            inv.setItem(slot, bar(m));
        }
        inv.setItem(centerSlot, info(Material.MAP, "§f"+progress+"§7/§f"+Math.max(1,total), Collections.emptyList()));
    }

    private ItemStack bar(Material m){
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta(); im.setDisplayName(" "); it.setItemMeta(im);
        return it;
    }
    private void fill(Inventory inv, ItemStack f){ for (int i=0;i<inv.getSize();i++) inv.setItem(i,f); }
    private ItemStack pane(){ ItemStack it=new ItemStack(Material.GRAY_STAINED_GLASS_PANE); ItemMeta im=it.getItemMeta(); im.setDisplayName(" "); it.setItemMeta(im); return it; }
    private ItemStack nav(Material m, String name, String tag){
        ItemStack it=new ItemStack(m);
        ItemMeta im=it.getItemMeta();
        im.setDisplayName("§e"+name);
        setTag(im, tag);
        it.setItemMeta(im);
        return it;
    }
    private ItemStack info(Material m, String name){ ItemStack it=new ItemStack(m); ItemMeta im=it.getItemMeta(); im.setDisplayName(name); it.setItemMeta(im); return it; }
    private ItemStack info(Material m, String name, List<String> lore){ ItemStack it=new ItemStack(m); ItemMeta im=it.getItemMeta(); im.setDisplayName(name); im.setLore(lore); it.setItemMeta(im); return it; }
    private ItemStack book(String title, String... lines){
        ItemStack it=new ItemStack(Material.BOOK);
        ItemMeta im=it.getItemMeta();
        im.setDisplayName("§f"+title);
        List<String> lore=new ArrayList<>();
        for (String ln : lines) if (ln!=null && !ln.isEmpty()) lore.add("§7"+ln);
        im.setLore(lore);
        it.setItemMeta(im); return it;
    }

    /* ============== Clicks ============== */

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e){
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder h)) return;
        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;
        String tag = getTag(it);
        if (tag == null || tag.isEmpty()) return;

        if (tag.equals("close")) { p.closeInventory(); return; }

        if (tag.startsWith("story:") && h.view == View.LIST) {
            String id = tag.substring(6);
            if (id.equalsIgnoreCase(plugin.getStoryManager().getActiveId(p))) {
                openActive(p);
            } else {
                openStartConfirm(p, id);
            }
            return;
        }

        if (tag.startsWith("list:")) {
            int to = parseInt(tag.substring(5), 1);
            openList(p, to);
            return;
        }

        if (tag.startsWith("start:")) {
            String[] parts = tag.split(":",3); // start:yes|no:<id>
            if (parts.length == 3) {
                if ("yes".equalsIgnoreCase(parts[1])) {
                    plugin.getStoryManager().start(p, parts[2]);
                    openActive(p);
                } else openList(p, 1);
            }
            return;
        }
        if (tag.startsWith("abandon:")) {
            String[] parts = tag.split(":",3); // abandon:<id>  oder abandon:yes|no:<id>
            if (parts.length == 2) { openAbandonConfirm(p, parts[1]); return; }
            if (parts.length == 3) {
                if ("yes".equalsIgnoreCase(parts[1])) {
                    plugin.getStoryManager().abandon(p);
                    openList(p,1);
                } else openActive(p);
            }
            return;
        }

        if (tag.startsWith("hint:")) {
            p.closeInventory();
            p.sendMessage("§7" + tr(p,"story.gui.hint.msg"));
            return;
        }
    }

    /* ============== Tagging via PDC ============== */

    private void setTag(ItemMeta meta, String value) {
        if (meta == null) return;
        meta.getPersistentDataContainer().set(KEY_TAG, PersistentDataType.STRING, value == null ? "" : value);
    }

    private String getTag(ItemStack item) {
        if (item == null) return null;
        ItemMeta meta = item.getItemMeta();
        if (meta == null) return null;
        PersistentDataContainer pdc = meta.getPersistentDataContainer();
        return pdc.get(KEY_TAG, PersistentDataType.STRING);
    }

    /* ============== Utils ============== */

    private String tr(Player p, String key){ return plugin.getI18n().tr(p, key); }
    private String tr(Player p, String key, Map<String,String> vars){ return plugin.getI18n().tr(p, key, vars); }
    private String cc(String s){ return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }
    private static Map<String,String> map(String k1,String v1){ Map<String,String> m=new HashMap<>(); m.put(k1,v1); return m; }
    private static Map<String,String> map(String k1,String v1,String k2,String v2){ Map<String,String> m=map(k1,v1); m.put(k2,v2); return m; }
    private int parseInt(String s, int def){ try { return Integer.parseInt(s); } catch (Exception e){ return def; } }

    /* ============== Holder ============== */
    private enum View { LIST, ACTIVE, START_CONFIRM, ABANDON_CONFIRM }
    private static final class Holder implements InventoryHolder {
        final View view; final int page; final int stepIdx; final String storyId;
        Holder(View v, int p, int s, String id){ this.view=v; this.page=p; this.stepIdx=s; this.storyId=id; }
        @Override public Inventory getInventory(){ return null; }
    }
}