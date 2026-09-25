// src/main/java/com/vetroforge/manager/WikiManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
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
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class WikiManager implements Listener {

    private final FactionsPlugin plugin;
    private final NamespacedKey TAG_KEY; // für Item-Tags im PDC

    public WikiManager(FactionsPlugin plugin){
        this.plugin = plugin;
        this.TAG_KEY = new NamespacedKey(plugin, "wiki_tag");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /* ===================== Public API ===================== */

    /** Hauptmenü (Kategorien) */
    public void open(Player p){
        Inventory inv = Bukkit.createInventory(new Holder(View.ROOT, null, null, 1), 54, color("&fVetroForge Wiki"));
        fill(inv, pane());

        if (plugin.getConfig().isConfigurationSection("wiki.categories")) {
            int slot = 10;
            for (String id : plugin.getConfig().getConfigurationSection("wiki.categories").getKeys(false)) {
                String base = "wiki.categories."+id+".";
                String name = color(plugin.getConfig().getString(base+"name","&f"+id));
                Material mat = Material.matchMaterial(plugin.getConfig().getString(base+"icon","BOOK"));
                inv.setItem(slot++, icon(mat==null?Material.BOOK:mat, name, "cat:"+id));
                if (slot % 9 == 7) slot += 4; // hübsches Raster
            }
        }

        // Hinweis auf die Suche (separater Command /wikisearch)
        inv.setItem(49, icon(Material.PAPER, color("&eSuche öffnen (/wikisearch)"), "noop"));
        p.openInventory(inv);
    }

    /** Kategorie öffnen (mit Paginierung) */
    public void openCategory(Player p, String catId, int page){
        String base = "wiki.categories."+catId+".items";
        List<String> items = plugin.getConfig().getStringList(base);
        if (items == null) items = Collections.emptyList();

        Inventory inv = Bukkit.createInventory(new Holder(View.CATEGORY, catId, null, page), 54, color("&fWiki: "+catId+" §7p"+page));
        fill(inv, pane());

        int pageSize = 21;
        int[] slots = new int[]{10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        int maxPage = Math.max(1, (int)Math.ceil(items.size()/(double)pageSize));
        page = Math.min(Math.max(1,page), maxPage);

        int start = (page-1)*pageSize;
        int end = Math.min(start+pageSize, items.size());

        int i = 0;
        for (int idx = start; idx < end; idx++) {
            String id = items.get(idx);
            inv.setItem(slots[i++], renderItemCard(p, id));
        }

        inv.setItem(45, icon(Material.ARROW, color("&a« Back"), "back:root"));
        inv.setItem(49, icon(Material.PAPER, color("&7Seite "+page+"/"+maxPage), "noop"));
        if (page > 1) inv.setItem(48, icon(Material.ARROW, color("&eZurück"), "page:"+catId+":"+(page-1)));
        if (page < maxPage) inv.setItem(50, icon(Material.ARROW, color("&aWeiter"), "page:"+catId+":"+(page+1)));

        p.openInventory(inv);
    }

    /** Suchergebnis öffnen */
    public void openSearch(Player p, String term, int page){
        List<String> all = resolveAllItemIds();
        term = term == null ? "" : term.trim().toLowerCase(Locale.ROOT);

        List<String> matches = new ArrayList<>();
        for (String id : all) {
            String name = getItemName(id);
            if (name.toLowerCase(Locale.ROOT).contains(term) || id.toLowerCase(Locale.ROOT).contains(term)) {
                matches.add(id);
            }
        }

        Inventory inv = Bukkit.createInventory(new Holder(View.SEARCH, null, term, page), 54, color("&fWiki Suche: "+term+" §7p"+page));
        fill(inv, pane());

        int pageSize = 21;
        int[] slots = new int[]{10,11,12,13,14,15,16, 19,20,21,22,23,24,25, 28,29,30,31,32,33,34};
        int maxPage = Math.max(1, (int)Math.ceil(matches.size()/(double)pageSize));
        page = Math.min(Math.max(1,page), maxPage);

        int start = (page-1)*pageSize;
        int end = Math.min(start+pageSize, matches.size());

        int i = 0;
        for (int idx = start; idx < end; idx++) {
            String id = matches.get(idx);
            inv.setItem(slots[i++], renderItemCard(p, id));
        }

        inv.setItem(45, icon(Material.ARROW, color("&a« Back"), "back:root"));
        inv.setItem(49, icon(Material.PAPER, color("&7Seite "+page+"/"+maxPage), "noop"));
        if (page > 1) inv.setItem(48, icon(Material.ARROW, color("&eZurück"), "spage:"+term+":"+(page-1)));
        if (page < maxPage) inv.setItem(50, icon(Material.ARROW, color("&aWeiter"), "spage:"+term+":"+(page+1)));

        p.openInventory(inv);
    }

    /** Komfort-Alias fürs Hauptmenü (z.B. von der Language-GUI aufgerufen). */
    public void openWiki(Player p) { open(p); }

    /**
     * Sprache für den Spieler setzen – robust gegen verschiedene LanguageManager-Implementierungen.
     * Fällt auf I18n zurück.
     */
    public void setLanguageFor(Player p, String lang, boolean save) {
        if (p == null || lang == null) return;
        try {
            Object lm = plugin.getLanguageManager();
            if (lm != null) {
                try { // setLanguage(Player,String,boolean)
                    lm.getClass().getMethod("setLanguage", Player.class, String.class, boolean.class)
                            .invoke(lm, p, lang, save);
                    return;
                } catch (NoSuchMethodException ignored) {}
                try { // setLanguage(Player,String)
                    lm.getClass().getMethod("setLanguage", Player.class, String.class)
                            .invoke(lm, p, lang);
                    return;
                } catch (NoSuchMethodException ignored) {}
                try { // setLanguageFor(Player,String,boolean)
                    lm.getClass().getMethod("setLanguageFor", Player.class, String.class, boolean.class)
                            .invoke(lm, p, lang, save);
                    return;
                } catch (NoSuchMethodException ignored) {}
            }
        } catch (Throwable ignored) {}

        // Fallback über I18n
        trySetLanguageFallback(p, lang);
    }

    /* ===================== Rendering ===================== */

    private ItemStack renderItemCard(Player viewer, String itemId){
        String ib = "shop.general.items."+itemId+".";
        Material mat = Material.matchMaterial(plugin.getConfig().getString(ib+"material","STONE"));
        String name  = color(plugin.getConfig().getString(ib+"name","&f"+itemId));
        String desc  = getWikiDescFor(viewer, itemId);

        ItemStack it = new ItemStack(mat==null?Material.STONE:mat);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        im.setLore(wrapGray(desc, 40));
        setTag(im, "it:"+itemId);
        it.setItemMeta(im);
        return it;
    }

    private String getWikiDescFor(Player p, String itemId){
        String lang = resolvePlayerLanguage(p);  // <<— zentrale, robuste Sprach-Ermittlung

        String base = "wiki.items."+itemId+".desc.";
        String d = plugin.getConfig().getString(base+lang, null);
        if (d == null) d = plugin.getConfig().getString("wiki.items."+itemId+".desc", "&7Keine Beschreibung.");
        return ChatColor.translateAlternateColorCodes('&', d == null ? "&7Keine Beschreibung." : d);
    }

    private List<String> resolveAllItemIds(){
        Set<String> out = new LinkedHashSet<>();
        if (plugin.getConfig().isConfigurationSection("wiki.categories")) {
            for (String cat : plugin.getConfig().getConfigurationSection("wiki.categories").getKeys(false)) {
                out.addAll(plugin.getConfig().getStringList("wiki.categories."+cat+".items"));
            }
        }
        return new ArrayList<>(out);
    }

    private String getItemName(String itemId){
        String ib = "shop.general.items."+itemId+".name";
        return ChatColor.stripColor(ChatColor.translateAlternateColorCodes('&',
                plugin.getConfig().getString(ib, itemId)));
    }

    /* ===================== Events ===================== */

    @EventHandler(ignoreCancelled = true)
    public void onClick(InventoryClickEvent e){
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (!(e.getView().getTopInventory().getHolder() instanceof Holder)) return;
        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        String tag = getTag(it);
        if (tag == null) return;

        if (tag.equals("noop")) return;

        // Kategorie klicken
        if (tag.startsWith("cat:")) {
            String catId = tag.substring(4);
            openCategory(p, catId, 1);
            return;
        }

        if (tag.equals("back:root")) { open(p); return; }

        if (tag.startsWith("page:")) {         // page:<cat>:<page>
            String[] parts = tag.split(":");
            openCategory(p, parts[1], parseInt(parts[2],1));
            return;
        }

        if (tag.startsWith("spage:")) {        // spage:<term>:<page>
            String[] parts = tag.split(":");
            openSearch(p, parts[1], parseInt(parts[2],1));
            return;
        }

        if (tag.startsWith("it:")){
            String id = tag.substring(3);
            String name = ChatColor.translateAlternateColorCodes('&',
                    plugin.getConfig().getString("shop.general.items."+id+".name","&f"+id));
            String desc = getWikiDescFor(p, id);
            p.closeInventory();
            p.sendMessage("§8—————— §cVetro§6Forge §7• §fWiki §8——————");
            p.sendMessage("§f" + name);
            p.sendMessage("§7" + desc);
            p.sendMessage("§8——————————————————————————————");
        }
    }

    /* ===================== Utils ===================== */

    private void setTag(ItemMeta meta, String tag) {
        meta.getPersistentDataContainer().set(TAG_KEY, PersistentDataType.STRING, tag);
    }

    private String getTag(ItemStack item) {
        if (item == null || !item.hasItemMeta()) return null;
        String v = item.getItemMeta().getPersistentDataContainer().get(TAG_KEY, PersistentDataType.STRING);
        return (v == null || v.isBlank()) ? null : v;
    }

    private static String color(String s){ return ChatColor.translateAlternateColorCodes('&', s==null?"":s); }

    private ItemStack icon(Material m, String name, String tag){
        ItemStack it=new ItemStack(m);
        ItemMeta im=it.getItemMeta();
        im.setDisplayName(name);
        setTag(im, tag);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack pane(){
        ItemStack it=new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta im=it.getItemMeta(); im.setDisplayName(" "); it.setItemMeta(im);
        return it;
    }

    private void fill(Inventory inv, ItemStack f){ for(int i=0;i<inv.getSize();i++) inv.setItem(i,f); }

    private int parseInt(String s, int def){ try { return Integer.parseInt(s); } catch (Exception e){ return def; } }

    private List<String> wrapGray(String text, int max){
        List<String> out = new ArrayList<>();
        if (text == null) return out;
        String[] words = ChatColor.stripColor(text).split("\\s+");
        StringBuilder line = new StringBuilder();
        for (String w : words){
            if (line.length()+w.length()+1 > max){
                out.add(ChatColor.GRAY + line.toString());
                line.setLength(0);
            }
            if (line.length()>0) line.append(' ');
            line.append(w);
        }
        if (line.length()>0) out.add(ChatColor.GRAY + line.toString());
        return out;
    }

    /* ===================== Holder ===================== */
    private enum View { ROOT, CATEGORY, SEARCH }
    private static final class Holder implements InventoryHolder {
        final View view; final String category; final String term; final int page;
        Holder(View v, String c, String t, int p){ this.view=v; this.category=c; this.term=t; this.page=p; }
        @Override public Inventory getInventory(){ return null; }
    }

    /* ===================== Sprache robust ermitteln + Fallback setzen ===================== */

    private String resolvePlayerLanguage(Player p) {
        // 1) LanguageManager probieren
        String lang = tryGetLanguageFrom(plugin.getLanguageManager(), p);
        if (lang != null) return lang;

        // 2) Alias I18n probieren
        lang = tryGetLanguageFrom(plugin.getI18n(), p);
        if (lang != null) return lang;

        // 3) Default
        return "de";
    }

    private String tryGetLanguageFrom(Object mgr, Player p) {
        if (mgr == null || p == null) return null;
        Class<?> c = mgr.getClass();
        Object ret;

        try { // getLanguage(Player)
            ret = c.getMethod("getLanguage", Player.class).invoke(mgr, p);
            if (ret instanceof String s && !s.isBlank()) return s.toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {}

        try { // getLanguage(UUID)
            ret = c.getMethod("getLanguage", java.util.UUID.class).invoke(mgr, p.getUniqueId());
            if (ret instanceof String s && !s.isBlank()) return s.toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {}

        try { // getPlayerLangKey(Player)
            ret = c.getMethod("getPlayerLangKey", Player.class).invoke(mgr, p);
            if (ret instanceof String s && !s.isBlank()) return s.toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {}

        try { // getLang(Player) – manche Implementationen
            ret = c.getMethod("getLang", Player.class).invoke(mgr, p);
            if (ret instanceof String s && !s.isBlank()) return s.toLowerCase(Locale.ROOT);
        } catch (Throwable ignored) {}

        return null;
    }

    private void trySetLanguageFallback(Player p, String lang) {
        Object i18n = plugin.getI18n();
        if (i18n == null) return;
        Class<?> c = i18n.getClass();
        try { c.getMethod("setLanguage", Player.class, String.class).invoke(i18n, p, lang); return; } catch (Throwable ignored) {}
        try { c.getMethod("setLanguage", java.util.UUID.class, String.class).invoke(i18n, p.getUniqueId(), lang); return; } catch (Throwable ignored) {}
        try { c.getMethod("setPlayerLanguage", Player.class, String.class).invoke(i18n, p, lang); return; } catch (Throwable ignored) {}
        try { c.getMethod("setPlayerLangKey", Player.class, String.class).invoke(i18n, p, lang); } catch (Throwable ignored) {}
    }
}