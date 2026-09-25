package com.vetroforge.web;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.items.ReadOnlyItemMenu;
import org.bukkit.*;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.event.*;
import org.bukkit.event.inventory.*;
import org.bukkit.inventory.*;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class DeliveriesCommand implements CommandExecutor, Listener {
    private static final int[] SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33,34};
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM. HH:mm").withZone(ZoneId.of("Europe/Berlin"));
    private final FactionsPlugin plugin;
    private static final class Menu implements ReadOnlyItemMenu {
        final int page;
        final List<WebDeliveryService.Delivery> rows;
        final WebDeliveryService.Delivery selected;
        boolean busy;
        Menu(int page, List<WebDeliveryService.Delivery> rows, WebDeliveryService.Delivery selected) {
            this.page = page; this.rows = rows; this.selected = selected;
        }
        public Inventory getInventory() { return null; }
    }
    private static final class AdminMenu implements ReadOnlyItemMenu {
        final int page;
        final List<WebDeliveryService.AdminDelivery> rows;
        final WebDeliveryService.AdminDelivery selected;
        final String action;
        boolean busy;
        AdminMenu(int page, List<WebDeliveryService.AdminDelivery> rows,
                  WebDeliveryService.AdminDelivery selected, String action) {
            this.page = page; this.rows = rows; this.selected = selected; this.action = action;
        }
        public Inventory getInventory() { return null; }
    }
    public DeliveriesCommand(FactionsPlugin plugin) { this.plugin = plugin; Bukkit.getPluginManager().registerEvents(this, plugin); }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("/deliveries öffnet ingame dein Zustellfach."); return true; }
        if (args.length > 0 && args[0].equalsIgnoreCase("admin")) {
            if (!player.hasPermission("vetroforge.delivery.admin")) player.sendMessage("§cDafür fehlt dir die Lieferungs-Adminberechtigung.");
            else openAdmin(player, 0);
            return true;
        }
        open(player, 0);
        return true;
    }
    private void open(Player player, int page) {
        Menu loading = new Menu(page, List.of(), null); loading.busy = true;
        Inventory wait = create(loading, 27, "Zustellfach");
        wait.setItem(13, card(Material.CLOCK, "§bLieferungen werden geladen …"));
        player.openInventory(wait);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            List<WebDeliveryService.Delivery> rows;
            try { rows = plugin.getWebDeliveryService().list(player.getUniqueId(), page); }
            catch (Exception failure) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!current(player, loading)) return;
                    loading.busy = false;
                    wait.setItem(13, card(Material.BARRIER, "§cZustellfach nicht erreichbar", "§7Es wurde nichts ausgezahlt.", "§7Später mit /deliveries erneut öffnen."));
                    wait.setItem(26, card(Material.BARRIER, "§7Schließen"));
                });
                return;
            }
            Bukkit.getScheduler().runTask(plugin, () -> { if (current(player, loading)) render(player, page, rows); });
        });
    }
    private void render(Player player, int page, List<WebDeliveryService.Delivery> rows) {
        Menu menu = new Menu(page, rows, null);
        Inventory inv = create(menu, 54, "Zustellfach");
        inv.setItem(4, card(Material.ENDER_CHEST, "§b§lDeine Web-Shop-Lieferungen", "§7Seite " + (page + 1), "§7Keine erneute Coin-Abbuchung beim Abholen.", "§8AH-Direktkäufe sind hier nicht enthalten."));
        if (rows.isEmpty()) inv.setItem(22, card(Material.CHEST, "§7Keine Lieferungen auf dieser Seite"));
        for (int i = 0; i < Math.min(21, rows.size()); i++) {
            var row = rows.get(i);
            Material icon = Material.matchMaterial(row.material());
            if (icon == null || !icon.isItem() || icon.isAir()) icon = Material.PAPER;
            inv.setItem(SLOTS[i], card(icon, "§f" + row.amount() + "× " + row.material(), statusLabel(row.status()),
                    "§7Preis: §6" + row.price() + " Coins", "§8" + DATE.format(Instant.ofEpochMilli(row.createdAt())),
                    "§8Bestellung: " + row.purchaseId(), "PENDING".equals(row.status()) ? "§eKlicken: Abholung bestätigen" : "§8Nur Statusanzeige"));
        }
        if (page > 0) inv.setItem(45, card(Material.ARROW, "§bVorherige Seite"));
        if (rows.size() > 21) inv.setItem(53, card(Material.ARROW, "§bNächste Seite"));
        inv.setItem(48, card(Material.BARRIER, "§7Schließen"));
        inv.setItem(49, card(Material.CLOCK, "§bAktualisieren"));
        inv.setItem(50, card(Material.BOOK, "§bGut zu wissen", "§7Abholen im Überlebensmodus außerhalb von Raids.",
                "§7Bei vollem Inventar bleibt die Bestellung offen.", "§7Bestätigung offen? Das Team prüft die Übergabe."));
        player.openInventory(inv);
    }
    public static String statusLabel(String status) {
        return switch (status) {
            case "PENDING" -> "§eBereit zur Abholung";
            case "DELIVERED" -> "§aZugestellt";
            case "REFUNDED" -> "§bZurückerstattet";
            case "FAILED" -> "§cKauf fehlgeschlagen";
            case "DELIVERING", "REVIEW" -> "§6Bestätigung offen · Team-Prüfung";
            default -> "§7Status unbekannt · Team-Prüfung";
        };
    }
    private void openAdmin(Player player, int page) {
        AdminMenu loading = new AdminMenu(page, List.of(), null, null); loading.busy = true;
        Inventory wait = create(loading, 27, "Lieferungs-Teamzentrum");
        wait.setItem(13, card(Material.CLOCK, "§bPrüffälle werden geladen …"));
        player.openInventory(wait);
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                List<WebDeliveryService.AdminDelivery> rows = plugin.getWebDeliveryService().listAttention(page);
                Bukkit.getScheduler().runTask(plugin, () -> { if (current(player, loading)) renderAdmin(player, page, rows); });
            } catch (Exception failure) {
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (!current(player, loading)) return;
                    loading.busy = false;
                    wait.setItem(13, card(Material.BARRIER, "§cTeamzentrum nicht erreichbar", "§7Es wurde nichts verändert."));
                });
            }
        });
    }
    private void renderAdmin(Player player, int page, List<WebDeliveryService.AdminDelivery> rows) {
        AdminMenu menu = new AdminMenu(page, rows, null, null);
        Inventory inv = create(menu, 54, "Lieferungs-Teamzentrum");
        inv.setItem(4, card(Material.RECOVERY_COMPASS, "§b§lOffene Übergaben",
                "§7PENDING, REVIEW und DELIVERING", "§7Seite " + (page + 1),
                "§8Automatik bleibt bei begonnenem Handoff gesperrt."));
        if (rows.isEmpty()) inv.setItem(22, card(Material.LIME_DYE, "§aKeine offenen Prüffälle"));
        for (int i = 0; i < Math.min(21, rows.size()); i++) {
            var row = rows.get(i);
            Material material = Material.matchMaterial(row.material());
            if (material == null || material.isAir() || !material.isItem()) material = Material.PAPER;
            inv.setItem(SLOTS[i], card(material, "§f" + row.amount() + "× " + row.material(),
                    statusLabel(row.status()), "§7Typ: §f" + row.type(), "§7Spieler: §f" + row.player(),
                    "§7Preis: §6" + row.price() + " Coins", row.handoffStarted() ? "§cHandoff begonnen · Automatik gesperrt" : "§aNoch kein Handoff",
                    "§8" + row.purchaseId(), "§eKlicken: sicher prüfen"));
        }
        if (page > 0) inv.setItem(45, card(Material.ARROW, "§bVorherige Seite"));
        if (rows.size() > 21) inv.setItem(53, card(Material.ARROW, "§bNächste Seite"));
        inv.setItem(48, card(Material.BARRIER, "§7Schließen"));
        inv.setItem(49, card(Material.CLOCK, "§bAktualisieren"));
        player.openInventory(inv);
    }
    private void showAdminDetail(Player player, int page, WebDeliveryService.AdminDelivery row, String action) {
        AdminMenu menu = new AdminMenu(page, List.of(), row, action);
        Inventory inv = create(menu, 27, action == null ? "Lieferung prüfen" : "Teamaktion bestätigen");
        Material material = Material.matchMaterial(row.material());
        if (material == null || material.isAir() || !material.isItem()) material = Material.PAPER;
        inv.setItem(4, card(material, "§f" + row.amount() + "× " + row.material(),
                statusLabel(row.status()), "§7Spieler: §f" + row.player(), "§7Preis: §6" + row.price() + " Coins",
                "§7Fehler: §f" + Objects.toString(row.error(), "keiner"),
                row.handoffStarted() ? "§cAutomatische Korrektur gesperrt" : "§aNoch kein Inventar-Handoff"));
        if (action == null) {
            if (!row.handoffStarted() && "REVIEW".equals(row.status())) inv.setItem(11, card(Material.CLOCK, "§eErneut zustellen", "§7Setzt den Fall kontrolliert auf PENDING.", "§eErfordert noch eine Bestätigung."));
            if (!row.handoffStarted() && "SHOP".equals(row.type()) && Set.of("PENDING", "REVIEW").contains(row.status()))
                inv.setItem(15, card(Material.GOLD_INGOT, "§bCoins erstatten", "§7Erstattet exakt §6" + row.price() + " Coins§7.", "§eErfordert noch eine Bestätigung."));
            inv.setItem(22, card(Material.ARROW, "§7Zurück zur Liste"));
        } else {
            inv.setItem(11, card(Material.RED_CONCRETE, "§c§lEndgültig bestätigen",
                    "§7Aktion: §f" + ("retry".equals(action) ? "Erneut zustellen" : "Coins erstatten"),
                    "§7Jede Aktion wird mit deinem Namen protokolliert."));
            inv.setItem(15, card(Material.GRAY_DYE, "§7Abbrechen"));
        }
        player.openInventory(inv);
    }
    @EventHandler(priority = EventPriority.LOWEST) public void protect(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu || e.getView().getTopInventory().getHolder() instanceof AdminMenu) e.setCancelled(true);
    }
    @EventHandler(priority = EventPriority.HIGHEST) public void click(InventoryClickEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof AdminMenu admin) {
            adminClick(e, admin);
            return;
        }
        if (!(e.getView().getTopInventory().getHolder() instanceof Menu menu)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player player) || menu.busy || e.getClick() != ClickType.LEFT
                || e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        menu.busy = true;
        Bukkit.getScheduler().runTask(plugin, () -> {
            if (!current(player, menu)) return;
            menu.busy = false;
            if (menu.selected != null) {
                if (slot == 15) { open(player, menu.page); return; }
                if (slot != 11) return;
                menu.busy = true;
                Map<String, Object> result = plugin.getWebDeliveryService().collect(player, menu.selected.purchaseId());
                String status = Objects.toString(result.get("status"), "UNKNOWN");
                player.sendMessage("§9VetroForge §8• " + statusLabel(status));
                if ("PENDING".equals(status)) player.sendMessage("§7Bitte genügend Platz schaffen und außerhalb eines Raids im Survival-Modus abholen.");
                open(player, menu.page); return;
            }
            if (slot == 48 || slot == 26 && e.getView().getTopInventory().getSize() == 27) { player.closeInventory(); return; }
            if (slot == 45 && menu.page > 0) { open(player, menu.page - 1); return; }
            if (slot == 53 && menu.rows.size() > 21) { open(player, menu.page + 1); return; }
            if (slot == 49) { open(player, menu.page); return; }
            for (int i = 0; i < Math.min(21, menu.rows.size()); i++) {
                if (slot != SLOTS[i]) continue;
                var row = menu.rows.get(i);
                if (!"PENDING".equals(row.status())) return;
                Menu confirm = new Menu(menu.page, List.of(), row);
                Inventory inv = create(confirm, 27, "Abholung bestätigen");
                inv.setItem(13, card(Material.CHEST, "§b" + row.amount() + "× " + row.material(), "§7Die Bestellung ist bereits bezahlt.", "§7Keine Teilzustellung bei Platzmangel."));
                inv.setItem(11, card(Material.LIME_DYE, "§aAbholen")); inv.setItem(15, card(Material.RED_DYE, "§cZurück"));
                player.openInventory(inv); return;
            }
        });
    }
    private void adminClick(InventoryClickEvent event, AdminMenu menu) {
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || menu.busy || event.getClick() != ClickType.LEFT
                || event.getClickedInventory() != event.getView().getTopInventory()
                || !player.hasPermission("vetroforge.delivery.admin")) return;
        int slot = event.getRawSlot();
        if (menu.selected == null) {
            if (slot == 48) { player.closeInventory(); return; }
            if (slot == 45 && menu.page > 0) { openAdmin(player, menu.page - 1); return; }
            if (slot == 53 && menu.rows.size() > 21) { openAdmin(player, menu.page + 1); return; }
            if (slot == 49) { openAdmin(player, menu.page); return; }
            for (int i = 0; i < Math.min(21, menu.rows.size()); i++) if (slot == SLOTS[i]) {
                showAdminDetail(player, menu.page, menu.rows.get(i), null); return;
            }
            return;
        }
        if (menu.action == null) {
            if (slot == 22) { openAdmin(player, menu.page); return; }
            if (slot == 11 && !menu.selected.handoffStarted() && "REVIEW".equals(menu.selected.status()))
                showAdminDetail(player, menu.page, menu.selected, "retry");
            else if (slot == 15 && !menu.selected.handoffStarted() && "SHOP".equals(menu.selected.type())
                    && Set.of("PENDING", "REVIEW").contains(menu.selected.status()))
                showAdminDetail(player, menu.page, menu.selected, "refund");
            return;
        }
        if (slot == 15) { showAdminDetail(player, menu.page, menu.selected, null); return; }
        if (slot != 11) return;
        menu.busy = true;
        player.getOpenInventory().setItem(11, card(Material.CLOCK, "§bAktion wird sicher verbucht …"));
        Bukkit.getScheduler().runTaskAsynchronously(plugin, () -> {
            try {
                String result = plugin.getWebDeliveryService().adminResolve(menu.selected.purchaseId(), player.getUniqueId(), player.getName(), menu.action);
                Bukkit.getScheduler().runTask(plugin, () -> { player.sendMessage("§9Lieferungen §8• §a" + result); openAdmin(player, menu.page); });
            } catch (Exception failure) {
                Bukkit.getScheduler().runTask(plugin, () -> { player.sendMessage("§9Lieferungen §8• §cNicht geändert: " + failure.getMessage()); openAdmin(player, menu.page); });
            }
        });
    }
    @EventHandler(priority = EventPriority.LOWEST) public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof Menu || e.getView().getTopInventory().getHolder() instanceof AdminMenu) e.setCancelled(true);
    }
    private static boolean current(Player p, Menu menu) { return p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == menu; }
    public void shutdown() { for (Player p : Bukkit.getOnlinePlayers()) if (p.getOpenInventory().getTopInventory().getHolder() instanceof Menu || p.getOpenInventory().getTopInventory().getHolder() instanceof AdminMenu) p.closeInventory(); }
    private static boolean current(Player p, AdminMenu menu) { return p.isOnline() && p.getOpenInventory().getTopInventory().getHolder() == menu; }
    private static Inventory create(org.bukkit.inventory.InventoryHolder menu, int size, String title) {
        Inventory inv = Bukkit.createInventory(menu, size, "§9§lVetroForge §8• §f" + title);
        for (int i = 0; i < size; i++) inv.setItem(i, card(i < 9 || i >= size - 9 ? Material.BLUE_STAINED_GLASS_PANE : Material.BLACK_STAINED_GLASS_PANE, " "));
        return inv;
    }
    private static ItemStack card(Material material, String title, String... lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> { meta.setDisplayName(title); meta.setLore(List.of(lore)); meta.addItemFlags(ItemFlag.values()); });
        return item;
    }
}
