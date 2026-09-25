package com.vetroforge.events.gui;

import com.vetroforge.events.EventService;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.*;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitTask;
import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;

import java.time.Duration;
import java.time.Instant;
import java.util.*;

public final class EventsGui {

    /** Interior slots free of the fixed info items (20/22/24/40), used to preview event templates. */
    private static final int[] TEMPLATE_SLOTS = {
            10, 11, 12, 13, 14, 15, 16,
            28, 29, 30, 31, 32, 33, 34,
            19, 21, 23, 25,
            37, 38, 39, 41, 42, 43
    };

    private final Plugin plugin;
    private final EventService service;

    // Wer hat das GUI offen?
    private final Map<UUID, Inventory> open = new HashMap<>();
    private BukkitTask refresher;

    public EventsGui(Plugin plugin, EventService service) {
        this.plugin = plugin;
        this.service = service;
        startRefresher();
    }

    private void startRefresher() {
        stopRefresher();
        refresher = Bukkit.getScheduler().runTaskTimer(plugin, this::refreshAll, 20L, 20L); // 1s
    }

    private void stopRefresher() {
        if (refresher != null) refresher.cancel();
        refresher = null;
    }

    public void shutdown() {
        stopRefresher();
        open.clear();
    }

    public void open(Player p) {
        Inventory inv = Bukkit.createInventory(new SimpleHolder(), 54, ChatColor.DARK_PURPLE + "" + ChatColor.BOLD + "Events" + ChatColor.DARK_GRAY + " • " + ChatColor.WHITE + "Community");
        open.put(p.getUniqueId(), inv);
        build(p, inv);
        p.openInventory(inv);
    }

    public void onClose(Player p) {
        open.remove(p.getUniqueId());
    }

    private void refreshAll() {
        if (open.isEmpty()) return;
        // remove viewers who closed inventory
        open.entrySet().removeIf(e -> {
            Player p = Bukkit.getPlayer(e.getKey());
            return p == null || p.getOpenInventory() == null || p.getOpenInventory().getTopInventory() != e.getValue();
        });
        // rebuild
        for (Map.Entry<UUID, Inventory> entry : open.entrySet()) {
            Player player = Bukkit.getPlayer(entry.getKey());
            if (player != null) build(player, entry.getValue());
        }
    }

    private void build(Player player, Inventory inv) {
        for (int i = 0; i < inv.getSize(); i++) inv.setItem(i, null);

        var evOpt = service.getActiveEvent();
        for (int i = 0; i < inv.getSize(); i++) {
            boolean border = i < 9 || i >= 45 || i % 9 == 0 || i % 9 == 8;
            if (border) {
                inv.setItem(i, glass((i % 2 == 0) ? Material.PURPLE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE, " "));
            } else {
                inv.setItem(i, glass(Material.GRAY_STAINED_GLASS_PANE, " "));
            }
        }
        inv.setItem(4, make(Material.NETHER_STAR, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "VetroForge Events",
                List.of(
                        ChatColor.DARK_GRAY + "Community-Ziele",
                        "",
                        ChatColor.GRAY + "Events geben Coins, Crystals, Event Tokens",
                        ChatColor.GRAY + "und besondere Server-Belohnungen.",
                        "",
                        ChatColor.YELLOW + "Fortschritt zählt serverweit."
                )));

        if (evOpt.isEmpty()) {
            inv.setItem(22, make(Material.CLOCK, ChatColor.YELLOW + "" + ChatColor.BOLD + "Kein aktives Event",
                    List.of(tr(player, "events.gui.none_active"),
                            tr(player, "events.gui.upcoming"),
                            "",
                            ChatColor.GRAY + "Neue Events werden vom Team",
                            ChatColor.GRAY + "global gestartet.")));
            placeTemplatePreviews(player, inv, null);
            return;
        }

        var ev = evOpt.get();
        var template = service.getTemplates().stream()
                .filter(current -> current.id.equalsIgnoreCase(ev.id))
                .findFirst()
                .orElse(null);
        inv.setItem(20, make(template == null ? Material.NETHER_STAR : icon(template.icon), ChatColor.AQUA + "" + ChatColor.BOLD + ev.name,
                List.of(template == null ? "§7Aktives Community-Event" : "§7Kategorie: §d" + template.category,
                        template == null || template.description.isBlank() ? "§8Keine Beschreibung" : "§7" + template.description,
                        "",
                        tr(player, "events.gui.progress", "current", ev.current, "goal", ev.goal, "pct", ev.pct()),
                        bar(ev.pct(), 20),
                        ev.endsAt != null ? tr(player, "events.gui.ends", "remaining", humanLeft(ev.endsAt)) : tr(player, "events.gui.no_limit"),
                        "",
                        ev.paused ? ChatColor.YELLOW + "Pausiert" : ChatColor.GREEN + "Läuft gerade")));

        inv.setItem(24, make(Material.PAPER, ChatColor.LIGHT_PURPLE + "" + ChatColor.BOLD + "Event-Details",
                List.of(tr(player, "events.gui.id", "id", ev.id),
                        ev.paused ? tr(player, "events.gui.paused") : tr(player, "events.gui.active"),
                        tr(player, "events.gui.started", "started", ev.startedAt == null ? "-" : java.time.Instant.ofEpochSecond(ev.startedAt.getEpochSecond())),
                        "",
                        "§6Rewards: §f" + rewardPreview(template == null ? List.of() : template.rewards),
                        "",
                        ChatColor.GRAY + "Milestones werden im Chat angekündigt.")));

        inv.setItem(40, make(Material.CHEST, ChatColor.GOLD + "" + ChatColor.BOLD + "Belohnungen",
                List.of(
                        ChatColor.GRAY + "Coins und Crystals landen direkt",
                        ChatColor.GRAY + "bei Spielern oder Factions.",
                        ChatColor.GRAY + "Event Tokens kommen ins Inventar.",
                        "",
                        ChatColor.DARK_GRAY + "LootDrop-, Raid- und Warzone-Events",
                        ChatColor.DARK_GRAY + "zählen automatisch mit."
                )));

        placeTemplatePreviews(player, inv, ev.id);
    }

    /** Zeigt die restlichen Event-Templates (ohne das evtl. aktive) als Vorschau in den freien Slots. */
    private void placeTemplatePreviews(Player player, Inventory inv, String activeTemplateId) {
        List<com.vetroforge.events.EventTemplate> templates = new ArrayList<>(service.getTemplates());
        templates.sort(Comparator.comparing(t -> t.name, String.CASE_INSENSITIVE_ORDER));

        int slotIndex = 0;
        for (var template : templates) {
            if (activeTemplateId != null && template.id.equalsIgnoreCase(activeTemplateId)) continue;
            if (slotIndex >= TEMPLATE_SLOTS.length) break;
            inv.setItem(TEMPLATE_SLOTS[slotIndex++], templateItem(player, template, false));
        }
    }

    private ItemStack templateItem(Player player, com.vetroforge.events.EventTemplate template, boolean active) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Kategorie: §d" + template.category);
        lore.add(template.description == null || template.description.isBlank() ? "§8Keine Beschreibung" : "§7" + template.description);
        lore.add("");
        lore.add(tr(player, "events.gui.goal", "goal", template.goal));
        lore.add(tr(player, "events.gui.duration", "duration", template.duration == null ? tr(player, "events.gui.open") : humanDuration(template.duration)));
        lore.add("§7Rewards: §f" + rewardPreview(template.rewards));
        lore.add("");
        lore.add(active ? "§aAktiv" : tr(player, "events.gui.activation"));
        return make(icon(template.icon), (active ? "§a§l" : "§f§l") + template.name, lore);
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

    private Material icon(String raw) {
        if (raw == null || raw.isBlank()) return Material.PAPER;
        try {
            return Material.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ignored) {
            return Material.PAPER;
        }
    }

    private String bar(int pct, int width) {
        int full = (int) Math.round((pct / 100.0) * width);
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < width; i++) {
            sb.append(i < full ? ChatColor.GREEN : ChatColor.DARK_GRAY).append('█');
        }
        return sb.toString();
    }

    private String humanLeft(Instant endsAt) {
        Duration d = Duration.between(Instant.now(), endsAt);
        long secs = Math.max(0, d.getSeconds());
        long h = secs / 3600;
        long m = (secs % 3600) / 60;
        return (h > 0 ? h + "h " : "") + m + "m";
    }

    private String rewardPreview(List<String> rewards) {
        if (rewards == null || rewards.isEmpty()) return "Keine";
        List<String> readable = new ArrayList<>();
        for (String reward : rewards) {
            if (reward == null || reward.isBlank()) continue;
            if (reward.startsWith("broadcast:")) continue;
            if (reward.startsWith("money:") || reward.startsWith("coins:")) {
                readable.add("§6" + reward.substring(reward.indexOf(':') + 1) + " Coins");
            } else if (reward.startsWith("crystals:")) {
                readable.add("§d" + reward.substring("crystals:".length()) + " Crystals");
            } else if (reward.startsWith("event_tokens:")) {
                readable.add("§b" + reward.substring("event_tokens:".length()) + " Event Tokens");
            } else if (reward.startsWith("command:") || reward.startsWith("console:")) {
                readable.add("§eServer-Befehl");
            } else {
                readable.add("§f" + reward);
            }
        }
        if (readable.isEmpty()) return "Broadcast";
        return String.join("§7, ", readable);
    }

    private String humanDuration(Duration duration) {
        if (duration == null) return "Offen";
        long minutes = Math.max(0, duration.toMinutes());
        long hours = minutes / 60;
        long rest = minutes % 60;
        if (hours <= 0) return rest + "m";
        return rest == 0 ? hours + "h" : hours + "h " + rest + "m";
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr((FactionsPlugin) plugin, player, key, VetroForgeUtil.vars(values));
    }

    /** Markiert das Inventar als unseres. */
    public static final class SimpleHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
