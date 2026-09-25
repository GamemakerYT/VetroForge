package com.vetroforge.events.gui;

import com.vetroforge.events.EventManager;
import com.vetroforge.events.EventService;
import com.vetroforge.events.EventTemplate;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

import java.util.*;
import java.util.stream.Collectors;

public final class EventAdminGui {

    private final Plugin plugin;
    private final EventService service;

    // Template-Pagination
    private static final int[] TEMPLATE_SLOTS = {1, 2, 3, 4, 5, 6, 7};
    private int page = 0;
    private int selectedIndex = 0;
    private List<EventTemplate> templates = new ArrayList<>();
    private final Map<UUID, String> pendingStart = new HashMap<>();

    public EventAdminGui(Plugin plugin, EventService service) {
        this.plugin = plugin;
        this.service = service;
        reloadTemplates();
    }

    public void reloadTemplates() {
        templates = service.getTemplates().stream().collect(Collectors.toList());
        if (page >= maxPage()) page = maxPage();
        if (selectedIndex >= templates.size()) selectedIndex = Math.max(0, templates.size() - 1);
    }

    private int maxPage() {
        return Math.max(0, (templates.size() - 1) / TEMPLATE_SLOTS.length);
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(new AdminHolder(), 27, ChatColor.DARK_PURPLE + "Event Admin");
        build(p, inv);
        p.openInventory(inv);
    }

    public void openProgress(Player p) {
        Inventory inv = Bukkit.createInventory(new ProgressHolder(), 27, "§8✦ §b§lVetroForge §8• §fEvent Progress");
        buildProgress(inv);
        p.openInventory(inv);
    }

    public void handleClick(Player p, int slot) {
        if (p.getOpenInventory() != null
                && p.getOpenInventory().getTopInventory() != null
                && p.getOpenInventory().getTopInventory().getHolder() instanceof ProgressHolder) {
            handleProgressClick(p, slot);
            return;
        }

        int templateIndex = templateIndex(slot);
        if (templateIndex >= 0) {
            selectedIndex = templateIndex;
            pendingStart.remove(p.getUniqueId());
            p.playSound(p, Sound.UI_BUTTON_CLICK, 0.7f, 1.35f);
            rebuildOpen(p);
            return;
        }

        switch (slot) {
            case 10: // start (selected template)
                EventTemplate t = selectedTemplate();
                if (t == null) { p.sendMessage("§cKein Template."); return; }
                String pending = pendingStart.get(p.getUniqueId());
                if (!t.id.equalsIgnoreCase(pending)) {
                    pendingStart.put(p.getUniqueId(), t.id);
                    p.sendMessage("§eNochmals klicken, um §f" + t.name + " §eglobal zu starten.");
                    p.playSound(p, Sound.UI_BUTTON_CLICK, 1f, 0.8f);
                } else {
                    pendingStart.remove(p.getUniqueId());
                    try {
                        service.start(t.id);
                        p.sendMessage("§aEvent global gestartet: §f" + t.name);
                        p.playSound(p, Sound.UI_TOAST_CHALLENGE_COMPLETE, 0.8f, 1.2f);
                    }
                    catch (IllegalArgumentException ex){ p.sendMessage("§cUnbekanntes Template."); }
                }
                break;
            case 12: pendingStart.remove(p.getUniqueId()); service.pause();  p.playSound(p, Sound.UI_BUTTON_CLICK, 1f, 1f); break;
            case 13: pendingStart.remove(p.getUniqueId()); service.resume(); p.playSound(p, Sound.UI_BUTTON_CLICK, 1f, 1.1f); break;
            case 14: pendingStart.remove(p.getUniqueId()); service.stop(false); p.playSound(p, Sound.UI_BUTTON_CLICK, 1f, 0.9f); break;
            case 16: pendingStart.remove(p.getUniqueId()); service.reloadTemplates(); reloadTemplates(); p.sendMessage("§aTemplates neu geladen."); break;
            case 17: pendingStart.remove(p.getUniqueId()); openProgress(p); return;

            // progress +/-
            case 20: service.addProgress(-100_000); break;
            case 21: service.addProgress(-10_000);  break;
            case 22: service.addProgress(-1_000);   break;
            case 23: service.addProgress(+1_000);   break;
            case 24: service.addProgress(+10_000);  break;
            case 25: service.addProgress(+100_000); break;

            // pagination
            case 18: if (page > 0) { page--; pendingStart.remove(p.getUniqueId()); } break;
            case 26: if (page < maxPage()) { page++; pendingStart.remove(p.getUniqueId()); } break;
        }
        selectedIndex = Math.min(selectedIndex, Math.max(0, templates.size() - 1));
        // rebuild current open inventory
        rebuildOpen(p);
    }

    private void rebuildOpen(Player p) {
        if (p.getOpenInventory() != null && p.getOpenInventory().getTopInventory() != null
                && p.getOpenInventory().getTopInventory().getHolder() instanceof AdminHolder) {
            build(p, p.getOpenInventory().getTopInventory());
        }
    }

    private EventTemplate selectedTemplate() {
        if (templates.isEmpty()) return null;
        int idx = Math.min(selectedIndex, templates.size()-1);
        return templates.get(idx);
    }

    private int templateIndex(int slot) {
        for (int i = 0; i < TEMPLATE_SLOTS.length; i++) {
            if (TEMPLATE_SLOTS[i] != slot) continue;
            int index = page * TEMPLATE_SLOTS.length + i;
            return index < templates.size() ? index : -1;
        }
        return -1;
    }

    private void build(Player player, Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, null);
        // Rahmen
        for (int i = 0; i < 27; i++) {
            if (isTemplateSlot(i) || i==10||i==12||i==13||i==14||i==16||i==18||i==26|| (i>=20 && i<=25)) continue;
            inv.setItem(i, glass((i%2==0)?Material.GRAY_STAINED_GLASS_PANE:Material.BLACK_STAINED_GLASS_PANE," "));
        }
        buildTemplateRow(inv);
        // Buttons
        EventTemplate selected = selectedTemplate();
        boolean confirm = selected != null && selected.id.equalsIgnoreCase(pendingStart.get(player.getUniqueId()));
        inv.setItem(10, make(confirm ? Material.LIME_CONCRETE : Material.LIME_DYE,
                confirm ? "§a§lBestätigen: Global starten" : "§aStart vorbereiten",
                loreSelected(confirm)));
        inv.setItem(12, make(Material.ORANGE_DYE, "§6Pause", List.of("§7Event pausieren")));
        inv.setItem(13, make(Material.LIGHT_BLUE_DYE, "§bResume", List.of("§7Event fortsetzen")));
        inv.setItem(14, make(Material.RED_DYE, "§cStop", List.of("§7Event stoppen")));
        inv.setItem(16, make(Material.PAPER, "§eReload", List.of("§7Templates neu laden")));
        inv.setItem(17, make(Material.COMPARATOR, "§bProgress Diagnose", List.of(
                "§7Zeigt Current, Source, Base und Raw.",
                "§7Stark für Cactus-Event Debugging.",
                "",
                "§eKlicken zum Öffnen"
        )));

        // Progress Felder
        inv.setItem(20, make(Material.REDSTONE_BLOCK, "§c-100k", List.of("§7Fortschritt verringern")));
        inv.setItem(21, make(Material.REDSTONE, "§c-10k", List.of("§7Fortschritt verringern")));
        inv.setItem(22, make(Material.REDSTONE_TORCH, "§c-1k", List.of("§7Fortschritt verringern")));
        inv.setItem(23, make(Material.EMERALD, "§a+1k", List.of("§7Fortschritt erhöhen")));
        inv.setItem(24, make(Material.EMERALD_BLOCK, "§a+10k", List.of("§7Fortschritt erhöhen")));
        inv.setItem(25, make(Material.DIAMOND_BLOCK, "§a+100k", List.of("§7Fortschritt erhöhen")));

        // Pagination
        inv.setItem(18, make(Material.ARROW, "§7« Zurück", List.of("§7Seite " + (page+1) + "/" + (maxPage()+1))));
        inv.setItem(26, make(Material.ARROW, "§7Weiter »", List.of("§7Seite " + (page+1) + "/" + (maxPage()+1))));
    }

    private void buildTemplateRow(Inventory inv) {
        for (int i = 0; i < TEMPLATE_SLOTS.length; i++) {
            int index = page * TEMPLATE_SLOTS.length + i;
            if (index >= templates.size()) {
                inv.setItem(TEMPLATE_SLOTS[i], glass(Material.PURPLE_STAINED_GLASS_PANE, "§8Kein Event"));
                continue;
            }
            EventTemplate t = templates.get(index);
            boolean selected = index == selectedIndex;
            inv.setItem(TEMPLATE_SLOTS[i], make(icon(t.icon),
                    (selected ? "§d§l" : "§f") + t.name,
                    List.of("§7ID: §f" + t.id,
                            "§7Kategorie: §d" + t.category,
                            "§7Ziel: §f" + t.goal,
                            "§7Dauer: §f" + (t.duration == null ? "offen" : t.duration.toMinutes() + "m"),
                            "",
                            selected ? "§aAusgewählt" : "§eKlicken zum Auswählen")));
        }
    }

    private void buildProgress(Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) {
            inv.setItem(i, glass((i % 2 == 0) ? Material.BLACK_STAINED_GLASS_PANE : Material.GRAY_STAINED_GLASS_PANE, " "));
        }

        EventManager.ProgressDebugSnapshot snap = service instanceof EventManager manager
                ? manager.debugProgressSnapshot()
                : null;
        if (snap == null) {
            inv.setItem(13, make(Material.BARRIER, "§cKein aktives Event", List.of(
                    "§7Starte ein Event mit §f/eventadmin start <id>",
                    "§7oder über das Admin-GUI."
            )));
            inv.setItem(22, make(Material.ARROW, "§eZurück", List.of("§7Zurück zur Event-Admin Übersicht")));
            return;
        }

        inv.setItem(4, make(Material.NETHER_STAR, "§b§l" + snap.name(), List.of(
                "§7ID: §f" + snap.id(),
                "§7Status: " + (snap.paused() ? "§ePausiert" : "§aLäuft"),
                "§7Progress: §f" + snap.current() + "§8/§f" + snap.goal(),
                "§7Prozent: §b" + snap.pct() + "%",
                snap.endsIn() == null || snap.endsIn().isBlank() ? "§7Ende: §foffen" : "§7Ende: §f" + snap.endsIn()
        )));

        inv.setItem(10, make(Material.REDSTONE_TORCH, "§cSource / Raw", List.of(
                "§7Source-Key: §f" + clean(snap.sourceKey()),
                "§7Raw-Zähler: §f" + rawText(snap.sourceRaw()),
                "§7Base beim Start: §f" + snap.sourceBase(),
                "§7Delta aus Source: §f" + snap.sourceDelta(),
                "",
                "§8Current = Raw - Base"
        )));

        inv.setItem(12, make(Material.EXPERIENCE_BOTTLE, "§aCurrent", List.of(
                "§7Aktueller Event-Wert: §f" + snap.current(),
                "§7Goal: §f" + snap.goal(),
                "§7Fehlt noch: §f" + Math.max(0L, snap.goal() - snap.current()),
                "",
                "§7Bei Source-Events zählt nur der",
                "§7Fortschritt seit Eventstart."
        )));

        inv.setItem(14, make(Material.CACTUS, "§2Cactus Debug", List.of(
                "§7Command:",
                "§f/shop debug cactus",
                "",
                "§7Auto-Fix:",
                "§f/shop debug cactus fix"
        )));

        inv.setItem(16, make(Material.CLOCK, "§eRuntime", List.of(
                "§7Gestartet: §f" + snap.startedAt(),
                "§7Endet: §f" + (snap.endsAt() == null ? "offen" : snap.endsAt()),
                "§7Pausiert: " + (snap.paused() ? "§eJa" : "§aNein")
        )));

        inv.setItem(22, make(Material.ARROW, "§eZurück", List.of("§7Zurück zur Event-Admin Übersicht")));
    }

    private void handleProgressClick(Player p, int slot) {
        if (slot == 22) {
            open(p);
            return;
        }
        if (slot == 14) {
            p.closeInventory();
            p.performCommand("shop debug cactus");
            return;
        }
        if (slot == 13 || slot == 4 || slot == 10 || slot == 12 || slot == 16) {
            p.playSound(p, Sound.UI_BUTTON_CLICK, 0.5f, 1.25f);
        }
    }

    private List<String> loreSelected(boolean confirm) {
        EventTemplate t = selectedTemplate();
        if (t == null) return List.of("§7Kein Template verfügbar.");
        List<String> lore = new ArrayList<>(List.of(
                "§7Template: §f" + t.id,
                "§7Name: §f" + t.name,
                "§7Kategorie: §d" + t.category,
                "§7Info: §f" + (t.description == null || t.description.isBlank() ? "—" : t.description),
                "§7Ziel: §f" + t.goal,
                "§7Quelle: §f" + (t.progressSource==null? "—" : t.progressSource),
                "§7Dauer: §f" + (t.duration==null? "—" : t.duration.toMinutes()+"m"),
                ""));
        lore.add(confirm ? "§aKlicken zum GLOBALEN Start" : "§eErster Klick: vorbereiten");
        if (!confirm) lore.add("§7Zweiter Klick bestätigt den Start.");
        return lore;
    }

    private boolean isTemplateSlot(int slot) {
        for (int templateSlot : TEMPLATE_SLOTS) {
            if (templateSlot == slot) return true;
        }
        return false;
    }

    private Material icon(String raw) {
        if (raw == null || raw.isBlank()) return Material.PAPER;
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Material.PAPER;
        }
    }

    private ItemStack make(Material m, String name, List<String> lore) {
        ItemStack it = new ItemStack(m);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        im.setLore(lore);
        it.setItemMeta(im);
        return it;
    }

    private ItemStack glass(Material mat, String name) {
        ItemStack it = new ItemStack(mat);
        ItemMeta im = it.getItemMeta();
        im.setDisplayName(name);
        it.setItemMeta(im);
        return it;
    }

    private String clean(String value) {
        return value == null || value.isBlank() ? "-" : value;
    }

    private String rawText(long value) {
        return value < 0L ? "-" : String.valueOf(value);
    }

    public static final class AdminHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public static final class ProgressHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
