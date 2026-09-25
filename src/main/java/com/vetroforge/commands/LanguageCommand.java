// src/main/java/com/vetroforge/commands/LanguageCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.util.StringUtil;

import java.util.*;
import java.util.stream.Collectors;

public class LanguageCommand implements CommandExecutor, Listener, TabCompleter {

    private static final String ARROW = "§8» §f";

    private final FactionsPlugin plugin;
    private final NamespacedKey KEY_LANG;

    public LanguageCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.KEY_LANG = new NamespacedKey(plugin, "lang_key");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    /* ===================== Command ===================== */

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!(s instanceof Player p)) { s.sendMessage("Players only."); return true; }

        // /language -> GUI
        // /language gui -> GUI
        // /language de  -> setzen
        if (a.length == 0 || a[0].equalsIgnoreCase("gui")) {
            openGui(p);
            return true;
        }

        String key = a[0].toLowerCase(Locale.ROOT);
        if ("auto".equals(key)) {
            plugin.getI18n().setAuto(p);
            p.sendMessage(plugin.getI18n().tr(p, "language.auto", Map.of("lang", plugin.getI18n().getLanguage(p).toUpperCase(Locale.ROOT))));
            return true;
        }
        if ("debug".equals(key) && p.hasPermission("vetro.language.admin")) {
            Player target = a.length >= 2 ? Bukkit.getPlayer(a[1]) : p;
            p.sendMessage(ChatColor.GRAY + plugin.getI18n().debug(target));
            return true;
        }
        if ("missing".equals(key) && p.hasPermission("vetro.language.admin")) {
            Set<String> missing = plugin.getI18n().missingKeys();
            p.sendMessage(missing.isEmpty() ? ChatColor.GREEN + "All language keys are complete."
                    : ChatColor.RED + "Missing keys: " + ChatColor.GRAY + String.join(", ", missing));
            return true;
        }
        Set<String> supported = getSupported();
        if (!supported.contains(key)) {
            p.sendMessage(ChatColor.RED + "Unbekannte Sprache. " + ChatColor.GRAY + "Verfügbar: " + String.join(", ", supported));
            p.sendMessage(ChatColor.GRAY + "Tipp: /" + l + " gui");
            return true;
        }

        setLang(p, key);
        p.sendMessage("§6Sprache " + ARROW + ChatColor.WHITE + key.toUpperCase(Locale.ROOT));
        return true;
    }

    /* ===================== TabComplete ===================== */

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) {
            List<String> keys = new ArrayList<>(getSupported());
            keys.addAll(List.of("auto", "debug", "missing"));
            List<String> out = new ArrayList<>();
            StringUtil.copyPartialMatches(a[0].toLowerCase(Locale.ROOT), keys, out);
            return out;
        }
        return Collections.emptyList();
    }

    /* ===================== GUI ===================== */

    private String guiTitle(Player p) {
        try { return plugin.getI18n().tr(p, "language.gui.title"); }
        catch (Throwable ignored) { return ChatColor.GOLD + "Sprache wählen"; }
    }

    public void openGui(Player p) {
        Inventory inv = Bukkit.createInventory(new LangHolder(), 27, guiTitle(p));

        // Füllrahmen
        ItemStack pane = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta pm = pane.getItemMeta(); pm.setDisplayName(" "); pane.setItemMeta(pm);
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, pane);

        // Sprachen in hübscher Reihenfolge, aber nur die unterstützen
        List<String> ordered = Arrays.asList("de","en","fr","nl");
        Set<String> supported = getSupported();
        List<String> langs = ordered.stream().filter(supported::contains).collect(Collectors.toList());
        // Rest (falls in config weitere Sprachen stehen)
        for (String s : supported) if (!langs.contains(s)) langs.add(s);

        String current = currentLangKey(p);
        int[] slots = {10, 12, 14, 16};
        for (int i = 0; i < Math.min(slots.length, langs.size()); i++) {
            String key = langs.get(i);
            boolean active = key.equalsIgnoreCase(current);
            inv.setItem(slots[i], langItem(p, key, active));
        }

        p.openInventory(inv);
    }

    private ItemStack langItem(Player viewer, String key, boolean active) {
        Material icon = iconForKey(key);
        ItemStack it = new ItemStack(icon);
        ItemMeta im = it.getItemMeta();

        String name = displayName(viewer, key); // z.B. „Deutsch“, „English“
        im.setDisplayName(ChatColor.YELLOW + "" + ChatColor.BOLD + name);

        List<String> lore = new ArrayList<>();
        lore.add(ChatColor.DARK_GRAY + "Key: " + ChatColor.GRAY + key);
        lore.add(" ");
        lore.add(active ? (ChatColor.GREEN + "Aktuell ausgewählt")
                : (ChatColor.GRAY + "Klicke zum Auswählen"));
        im.setLore(lore);

        im.getPersistentDataContainer().set(KEY_LANG, PersistentDataType.STRING, key);

        if (active) {
            try {
                im.addEnchant(org.bukkit.enchantments.Enchantment.UNBREAKING, 1, true);
                im.addItemFlags(org.bukkit.inventory.ItemFlag.HIDE_ENCHANTS);
            } catch (Throwable ignored) {}
        }

        it.setItemMeta(im);
        return it;
    }

    private Material iconForKey(String key) {
        String k = key.toLowerCase(Locale.ROOT);
        switch (k) {
            case "de": return Material.YELLOW_BANNER; // 🇩🇪
            case "en": return Material.WHITE_BANNER;  // neutral
            case "fr": return Material.BLUE_BANNER;   // 🇫🇷
            case "nl": return Material.ORANGE_BANNER; // 🇳🇱
            default:   return Material.PAPER;
        }
    }

    private String displayName(Player p, String key) {
        // Versuche aus lang-Dateien: language.item.<key>.name
        try {
            String k = "language.item." + key.toLowerCase(Locale.ROOT) + ".name";
            String tr = plugin.getI18n().tr(p, k);
            if (tr != null && !tr.equals(k)) return tr;
        } catch (Throwable ignored) {}
        // Fallback
        switch (key.toLowerCase(Locale.ROOT)) {
            case "de": return "Deutsch";
            case "en": return "English";
            case "fr": return "Français";
            case "nl": return "Nederlands";
            default:   return key.toUpperCase(Locale.ROOT);
        }
    }

    /* ===================== Click Handling ===================== */

    @EventHandler(ignoreCancelled = true, priority = EventPriority.HIGHEST)
    public void onClick(InventoryClickEvent e) {
        if (!(e.getWhoClicked() instanceof Player p)) return;
        Inventory top = e.getView().getTopInventory();
        if (top == null || !(top.getHolder() instanceof LangHolder)) return;
        e.setCancelled(true);

        ItemStack it = e.getCurrentItem();
        if (it == null || !it.hasItemMeta()) return;

        String key = it.getItemMeta().getPersistentDataContainer().get(KEY_LANG, PersistentDataType.STRING);
        if (key == null || key.isBlank()) return;

        setLang(p, key);
        p.closeInventory();
        p.sendMessage("§6VetroForge §8• §eSprache " + ARROW + ChatColor.WHITE + key.toUpperCase(Locale.ROOT));
    }

    /* ===================== Helpers ===================== */

    private void setLang(Player p, String key) {
        // I18n setzen
        try { plugin.getI18n().setLanguage(p, key); } catch (Throwable ignored) {}

        // Optional dein Wiki syncen
        try { if (plugin.getWikiManager() != null) plugin.getWikiManager().setLanguageFor(p, key, true); } catch (Throwable ignored) {}
    }

    private String currentLangKey(Player p) {
        try { return plugin.getI18n().getLanguage(p); }
        catch (Throwable ignored) { return null; }
    }

    private Set<String> getSupported() {
        try { return plugin.getI18n().getSupported(); }
        catch (Throwable ignored) { return new LinkedHashSet<>(Arrays.asList("de","en","fr","nl")); }
    }

    /** eigener Holder ⇒ unabhängig vom Titel-String */
    private static final class LangHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
