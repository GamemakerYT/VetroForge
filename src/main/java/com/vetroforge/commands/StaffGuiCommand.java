package com.vetroforge.commands;

import com.google.common.io.ByteArrayDataOutput;
import com.google.common.io.ByteStreams;
import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public final class StaffGuiCommand implements CommandExecutor, Listener {

    private final FactionsPlugin plugin;
    private final NamespacedKey actionKey;
    private final Map<UUID, List<CoreReportEntry>> coreReportCache = new HashMap<>();
    private final Map<UUID, String> reportFilterCache = new HashMap<>();
    private final Map<UUID, CoreReportStats> coreReportStatsCache = new HashMap<>();

    public StaffGuiCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.actionKey = new NamespacedKey(plugin, "staffgui_action");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage("§9§lVetroForge §8» §7Nur Spieler können dieses Menü öffnen.");
            return true;
        }
        if (!player.hasPermission("vetroforge.staff.gui")) {
            player.sendMessage("§9§lVetroForge §8» §cKeine Rechte.");
            return true;
        }
        openPlayers(player);
        return true;
    }

    public void openPlayers(Player viewer) {
        requestCoreReportStats(viewer);
        Inventory inv = Bukkit.createInventory(new PlayersHolder(), 54, "§8VetroForge §9Staff §7- Spieler");
        VetroForgeUtil.fill(inv, Material.BLACK_STAINED_GLASS_PANE);

        List<Player> online = new ArrayList<>(Bukkit.getOnlinePlayers());
        online.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int slot = 0;
        for (Player target : online) {
            if (slot >= 45) break;
            inv.setItem(slot++, playerHead(target));
        }
        CoreReportStats stats = coreReportStatsCache.get(viewer.getUniqueId());
        inv.setItem(45, action(Material.REDSTONE_TORCH, "§cReports", "reports:open",
                "§7Öffnet die zentrale Core-Reportliste.",
                stats == null ? "§eCore-Status wird geladen..." : "§7Offen: §f" + stats.openReports(),
                stats == null ? "§8VetroCore antwortet gleich." : "§7Alt: §c" + stats.oldReports(),
                "§8Filter: Offen, Meine Fälle, Geschlossen, Heute"));
        inv.setItem(46, action(Material.NETHER_STAR, "§bPerformance", "cmd:vf doctor performance",
                "§7TPS, RAM und Prozess-Check."));
        inv.setItem(47, action(Material.ENDER_EYE, "§dEntities", "cmd:vf doctor entities",
                "§7Entity-Zonen und alte NPCs prüfen."));
        inv.setItem(48, action(Material.BEACON, "§4Warzone", "cmd:warzone show",
                "§7Region sichtbar machen."));
        inv.setItem(49, action(Material.CHEST_MINECART, "§6Chest Doctor", "cmd:chest doctor",
                "§7Warzone-Chest-Punkte prüfen."));
        inv.setItem(50, action(Material.OAK_SIGN, "§eTop Listen", "cmd:top",
                "§7Weekly/Season Leaderboards."));
        inv.setItem(51, action(Material.BRICKS, "§aBuild Liste", "cmd:build list",
                "§7Aktive Buildmodus-Spieler."));
        inv.setItem(52, action(Material.REDSTONE, "§cClearLag", "cmd:clearlag",
                "§7Items am Boden bereinigen."));
        if (viewer.hasPermission("vetroforge.admin")) {
            inv.setItem(53, action(Material.COMMAND_BLOCK, "§9Admin GUI", "cmd:admingui",
                    "§7Öffnet das volle Admin-Control-Menü."));
        }
        viewer.openInventory(inv);
        viewer.playSound(viewer.getLocation(), Sound.UI_BUTTON_CLICK, 0.7f, 1.2f);
    }

    private void openReports(Player viewer) {
        openReports(viewer, reportFilterCache.getOrDefault(viewer.getUniqueId(), "open"));
    }

    private void openReports(Player viewer, String filter) {
        String normalized = normalizeFilter(filter);
        reportFilterCache.put(viewer.getUniqueId(), normalized);
        requestCoreReportStats(viewer);
        requestCoreReports(viewer, normalized);
        List<CoreReportEntry> cached = coreReportCache.get(viewer.getUniqueId());
        if (cached == null) {
            cached = List.of();
        }
        openCoreReports(viewer, normalized, cached, cached.isEmpty());
    }

    public void openCoreReports(Player viewer, String filter, List<CoreReportEntry> reports) {
        String normalized = normalizeFilter(filter);
        reportFilterCache.put(viewer.getUniqueId(), normalized);
        coreReportCache.put(viewer.getUniqueId(), List.copyOf(reports));
        openCoreReports(viewer, normalized, reports, false);
    }

    public void updateCoreReportStats(Player viewer, int openReports, int oldReports) {
        coreReportStatsCache.put(viewer.getUniqueId(), new CoreReportStats(openReports, oldReports));
    }

    private void openCoreReports(Player viewer, String filter, List<CoreReportEntry> reports, boolean loading) {
        Inventory inv = Bukkit.createInventory(new ReportsHolder(), 54, "§9§lVetroForge §8• §fReports");
        VetroForgeUtil.fill(inv, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(4, action(Material.REDSTONE_TORCH, "§c§lReport Center §8• §f" + reports.size() + " Fälle", "noop",
                "§7Quelle: §fVetroCore",
                loading ? "§eDaten werden geladen..." : "§7Klick auf einen Report öffnet Details.",
                "§8Filter: " + filterLabel(filter)));
        inv.setItem(10, filterItem("open", filter, Material.LIME_DYE, "Offen"));
        inv.setItem(12, filterItem("mine", filter, Material.NAME_TAG, "Meine Fälle"));
        inv.setItem(14, filterItem("closed", filter, Material.GRAY_DYE, "Geschlossen"));
        inv.setItem(15, filterItem("today", filter, Material.CLOCK, "Heute"));
        inv.setItem(16, action(Material.EMERALD, "§a§lAktualisieren", "reports:" + filter,
                "§7Lädt die Core-Reportliste neu."));

        int[] slots = {19, 20, 21, 22, 23, 24, 25, 28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};
        int index = 0;
        for (CoreReportEntry report : reports) {
            if (index >= slots.length) break;
            inv.setItem(slots[index++], coreReportItem(viewer, report));
        }
        if (reports.isEmpty()) {
            inv.setItem(31, action(loading ? Material.CLOCK : Material.BARRIER,
                    loading ? "§e§lLade Core-Reports..." : "§7§lKeine Reports gefunden",
                    "noop",
                    loading ? "§7Die Liste aktualisiert sich gleich." : "§7Für diesen Filter gibt es keine Einträge."));
        }
        inv.setItem(45, action(Material.ARROW, "§7Zurück", "back"));
        inv.setItem(53, action(Material.COMPASS, "§9§lStaff Übersicht", "back",
                "§7Zurück zum Staff-Control-Menü."));
        viewer.openInventory(inv);
    }

    // NOTE (adapted from factions-repair): the original had a local-report fallback here
    // (plugin.getReportService().get((int) id), returning a ReportService.ReportEntry) for
    // reports that predate VetroCore's central report store. The current codebase's
    // ReportService is a different, much simpler class (just per-player report-rate tracking,
    // no per-id report storage/ReportEntry type at all - reports live entirely in VetroCore
    // now), so that local fallback no longer has anything to look up. Falls straight through to
    // the same "not found" path the original used when the local lookup came back empty.
    private void openReportDetail(Player viewer, long id) {
        CoreReportEntry coreReport = findCoreReport(viewer, id);
        if (coreReport != null) {
            openCoreReportDetail(viewer, coreReport);
            return;
        }
        viewer.sendMessage("§cDieser Report existiert nicht mehr.");
        openReports(viewer);
    }

    public void openCoreReportDetailById(Player viewer, long id) {
        CoreReportEntry cached = findCoreReport(viewer, id);
        if (cached != null) {
            openCoreReportDetail(viewer, cached);
            return;
        }
        requestCoreReportDetail(viewer, id);
        Inventory inv = Bukkit.createInventory(new ReportDetailHolder(id), 54,
                "§9§lVetroForge §8• §fReport #" + id);
        VetroForgeUtil.fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, action(Material.CLOCK, "§e§lReport wird geladen", "noop",
                "§7VetroCore lädt Fall #" + id + ".",
                "§8Das Detailfenster öffnet sich gleich automatisch."));
        inv.setItem(22, action(Material.MAP, "§b§lDirekter Fall", "noop",
                "§7Der Notify-Klick springt jetzt direkt",
                "§7in diesen Report statt nur in die Liste."));
        inv.setItem(49, action(Material.ARROW, "§7Zurück", "reports:" + reportFilterCache.getOrDefault(viewer.getUniqueId(), "open")));
        viewer.openInventory(inv);
    }

    public void openMissingCoreReport(Player viewer) {
        viewer.sendMessage("§cDieser Core-Report existiert nicht mehr oder wurde gelöscht.");
        openReports(viewer);
    }

    public void openCoreReportDetail(Player viewer, CoreReportEntry report) {
        Inventory inv = Bukkit.createInventory(new ReportDetailHolder(report.id()), 54,
                "§9§lVetroForge §8• §fReport #" + report.id());
        VetroForgeUtil.fill(inv, Material.BLACK_STAINED_GLASS_PANE);

        inv.setItem(4, action(statusMaterial(report), "§9§lReport #" + report.id() + " §8• " + statusName(report), "noop",
                "§7Ziel: §f" + report.targetName(),
                "§7Melder: §f" + report.reporterName(),
                "§7Alter: §f" + report.ageText(),
                "§7Quelle: §8" + report.source(),
                report.assignedToName().isBlank() ? "§7Bearbeiter: §8Noch keiner" : "§7Bearbeiter: §b" + report.assignedToName()));
        inv.setItem(13, action(Material.WRITABLE_BOOK, "§f§lGrund", "noop",
                "§7" + report.reason(),
                "",
                "§8Dieser Fall liegt zentral in VetroCore."));

        inv.setItem(20, claimButton(viewer, report));
        inv.setItem(22, action(Material.ENDER_PEARL, "§a§lTeleport", "tp:" + report.targetName(),
                "§7Springt zum gemeldeten Spieler.",
                "§8Nur möglich, wenn der Spieler online ist."));
        inv.setItem(24, action(Material.PAPER, "§f§lHistory", "cmd:history " + report.targetName(),
                "§7Öffnet die zentrale Core-History.",
                "§8Ban, Mute, Kick und Notizen."));
        inv.setItem(29, action(Material.PLAYER_HEAD, "§e§lSpieler öffnen", "openplayer:" + report.targetName(),
                "§7Öffnet Invsee, Enderchest und Teleport.",
                "§8Nur für Online-Spieler."));
        if ("open".equalsIgnoreCase(report.status())) {
            inv.setItem(33, action(Material.LIME_DYE, "§a§lReport schließen", "closereport:" + report.id(),
                    "§7Markiert den Report in VetroCore als erledigt.",
                    "§8Der Audit-Eintrag bleibt erhalten."));
        } else {
            inv.setItem(33, action(Material.GRAY_DYE, "§7§lBereits geschlossen", "noop",
                    "§7Dieser Report ist erledigt."));
        }
        inv.setItem(45, action(Material.EMERALD, "§a§lListe aktualisieren", "reports:" + reportFilterCache.getOrDefault(viewer.getUniqueId(), "open"),
                "§7Lädt frische Daten aus VetroCore."));
        inv.setItem(49, action(Material.ARROW, "§7Zurück", "reports:" + reportFilterCache.getOrDefault(viewer.getUniqueId(), "open")));
        viewer.openInventory(inv);
    }

    private void openActions(Player viewer, UUID targetId) {
        Player target = Bukkit.getPlayer(targetId);
        if (target == null) {
            viewer.sendMessage("§cSpieler ist nicht mehr online.");
            openPlayers(viewer);
            return;
        }
        Inventory inv = Bukkit.createInventory(new ActionHolder(targetId), 27, "§8Staff §7» §9" + target.getName());
        VetroForgeUtil.fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(4, playerHead(target));
        inv.setItem(10, action(Material.CHEST, "§eInventar ansehen", "invsee:" + target.getName(),
                "§7Staff: nur Ansicht", "§7Admin: kann Items entnehmen"));
        inv.setItem(12, action(Material.ENDER_CHEST, "§dEnderchest ansehen", "endersee:" + target.getName(),
                "§7Staff: nur Ansicht", "§7Admin: kann Items entnehmen"));
        inv.setItem(14, action(Material.ENDER_PEARL, "§aZu Spieler teleportieren", "tp:" + target.getName()));
        inv.setItem(16, action(Material.PAPER, "§bHistory öffnen", "cmd:history " + target.getName()));
        inv.setItem(22, action(Material.ARROW, "§7Zurück", "back"));
        viewer.openInventory(inv);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        if (!(event.getWhoClicked() instanceof Player player)) return;
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof PlayersHolder || holder instanceof ActionHolder || holder instanceof ReportsHolder || holder instanceof ReportDetailHolder)) return;
        event.setCancelled(true);
        if (event.getClickedInventory() != event.getView().getTopInventory()) return;
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String action = clicked.getItemMeta().getPersistentDataContainer().get(actionKey, PersistentDataType.STRING);
        if (action == null) return;

        if (action.startsWith("player:")) {
            openActions(player, UUID.fromString(action.substring("player:".length())));
            return;
        }
        if (action.startsWith("reports:")) {
            openReports(player, action.substring("reports:".length()));
            return;
        }
        if (action.startsWith("report:")) {
            openReportDetail(player, Long.parseLong(action.substring("report:".length())));
            return;
        }
        if (action.startsWith("openplayer:")) {
            Player target = Bukkit.getPlayerExact(action.substring("openplayer:".length()));
            if (target == null) {
                player.sendMessage("§cSpieler ist nicht online.");
                return;
            }
            openActions(player, target.getUniqueId());
            return;
        }
        if (action.startsWith("closereport:")) {
            long id = Long.parseLong(action.substring("closereport:".length()));
            sendCoreReportAction(player, "CLOSE_REPORT", id);
            openCoreReportActionPending(player, id, "§a§lReport wird geschlossen",
                    "§7VetroCore speichert den Abschluss.",
                    "§8Der Fall aktualisiert sich gleich automatisch.");
            return;
        }
        if (action.startsWith("claimreport:")) {
            long id = Long.parseLong(action.substring("claimreport:".length()));
            sendCoreReportAction(player, "CLAIM_REPORT", id);
            openCoreReportActionPending(player, id, "§b§lFall wird übernommen",
                    "§7VetroCore setzt dich als Bearbeiter.",
                    "§8Der Fall aktualisiert sich gleich automatisch.");
            return;
        }
        if (action.startsWith("reportfilter:")) {
            openReports(player, action.substring("reportfilter:".length()));
            return;
        }
        if (action.equals("noop")) return;
        player.closeInventory();
        if (action.equals("back")) {
            openPlayers(player);
        } else if (action.startsWith("invsee:")) {
            player.performCommand("invsee " + action.substring("invsee:".length()));
        } else if (action.startsWith("endersee:")) {
            player.performCommand("endersee " + action.substring("endersee:".length()));
        } else if (action.startsWith("tp:")) {
            player.performCommand("tp " + action.substring("tp:".length()));
        } else if (action.startsWith("cmd:")) {
            player.performCommand(action.substring("cmd:".length()));
        }
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof PlayersHolder || holder instanceof ActionHolder || holder instanceof ReportsHolder || holder instanceof ReportDetailHolder) event.setCancelled(true);
    }

    private ItemStack coreReportItem(Player viewer, CoreReportEntry report) {
        boolean old = isOld(report.createdAt());
        String assignedTo = report.assignedToName() == null ? "" : report.assignedToName();
        boolean mine = assignedTo.equalsIgnoreCase(viewer.getName()) && "open".equalsIgnoreCase(report.status());
        Material material = mine ? Material.LIME_DYE : old ? Material.REDSTONE_BLOCK : ("closed".equalsIgnoreCase(report.status()) ? Material.GRAY_DYE : Material.WRITABLE_BOOK);
        String statusColor = "closed".equalsIgnoreCase(report.status()) ? "§7" : "§a";
        List<String> lore = new ArrayList<>();
        lore.add("§7Von: §f" + report.reporterName());
        lore.add("§7Grund: §f" + report.reason());
        lore.add("§7Quelle: §8" + report.source());
        lore.add("§7Alter: §f" + report.ageText());
        lore.add("§7Status: " + statusColor + report.status());
        if (mine) lore.add("§aMein Fall §8• §7du bist zuständig");
        if (!assignedTo.isBlank()) lore.add("§7Bearbeiter: §b" + assignedTo);
        if (old && "open".equalsIgnoreCase(report.status())) lore.add("§cAlt §8• §7bitte priorisieren");
        lore.add("");
        lore.add("§eKlick §8» §fDetails öffnen");
        String prefix = mine ? "§a§lMEIN FALL §8• " : old && "open".equalsIgnoreCase(report.status()) ? "§4§lALT §8• " : "";
        return action(material, prefix + "§c§l#" + report.id() + " §8• §f" + report.targetName(), "report:" + report.id(),
                lore.toArray(String[]::new));
    }

    private ItemStack claimButton(Player viewer, CoreReportEntry report) {
        String assignedTo = report.assignedToName() == null ? "" : report.assignedToName();
        if (!"open".equalsIgnoreCase(report.status())) {
            return action(Material.GRAY_DYE, "§7§lFall erledigt", "noop",
                    "§7Dieser Report ist bereits geschlossen.",
                    "§8Keine Übernahme mehr nötig.");
        }
        if (assignedTo.equalsIgnoreCase(viewer.getName())) {
            return action(Material.LIME_DYE, "§a§lMein Fall", "noop",
                    "§7Du bearbeitest diesen Report.",
                    "§8Das Team sieht dich als zuständigen Staff.");
        }
        if (!assignedTo.isBlank()) {
            return action(Material.YELLOW_DYE, "§e§lÜbernehmen von " + assignedTo, "claimreport:" + report.id(),
                    "§7Aktuell bearbeitet: §f" + assignedTo,
                    "§7Klick nur, wenn du den Fall wirklich übernimmst.",
                    "§8Der Audit-Eintrag bleibt erhalten.");
        }
        return action(Material.NAME_TAG, "§b§lFall übernehmen", "claimreport:" + report.id(),
                "§7Setzt dich als zuständigen Staff.",
                "§8Danach sieht das Team, wer dran ist.");
    }

    private ItemStack filterItem(String filter, String activeFilter, Material material, String label) {
        boolean active = filter.equalsIgnoreCase(activeFilter);
        return action(active ? Material.LIME_DYE : material,
                (active ? "§a§l" : "§7§l") + label,
                "reportfilter:" + filter,
                active ? "§aAktiver Filter" : "§7Klicken zum Wechseln.",
                "§8Quelle: VetroCore");
    }

    private void requestCoreReports(Player viewer, String filter) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("REQUEST_REPORT_LIST");
            out.writeUTF(viewer.getUniqueId().toString());
            out.writeUTF(normalizeFilter(filter));
            viewer.sendPluginMessage(plugin, "vetro:main", out.toByteArray());
        } catch (Throwable ignored) {
        }
    }

    private void requestCoreReportStats(Player viewer) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("REQUEST_REPORT_STATS");
            out.writeUTF(viewer.getUniqueId().toString());
            out.writeLong(30L * 60L * 1000L);
            viewer.sendPluginMessage(plugin, "vetro:main", out.toByteArray());
        } catch (Throwable ignored) {
        }
    }

    private void requestCoreReportDetail(Player viewer, long id) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF("REQUEST_REPORT_DETAIL");
            out.writeUTF(viewer.getUniqueId().toString());
            out.writeLong(id);
            viewer.sendPluginMessage(plugin, "vetro:main", out.toByteArray());
        } catch (Throwable ignored) {
            viewer.sendMessage("§cVetroCore ist gerade nicht erreichbar.");
        }
    }

    private void openCoreReportActionPending(Player viewer, long id, String title, String... lore) {
        List<CoreReportEntry> cached = coreReportCache.get(viewer.getUniqueId());
        if (cached != null) {
            coreReportCache.put(viewer.getUniqueId(), cached.stream()
                    .filter(report -> report.id() != id)
                    .toList());
        }
        Inventory inv = Bukkit.createInventory(new ReportDetailHolder(id), 54,
                "§9§lVetroForge §8• §fReport #" + id);
        VetroForgeUtil.fill(inv, Material.BLACK_STAINED_GLASS_PANE);
        inv.setItem(22, action(Material.CLOCK, title, "noop", lore));
        inv.setItem(49, action(Material.ARROW, "§7Zurück", "reports:" + reportFilterCache.getOrDefault(viewer.getUniqueId(), "open")));
        viewer.openInventory(inv);
    }

    private void sendCoreReportAction(Player viewer, String action, long id) {
        try {
            ByteArrayDataOutput out = ByteStreams.newDataOutput();
            out.writeUTF(action);
            out.writeUTF(viewer.getUniqueId().toString());
            out.writeLong(id);
            viewer.sendPluginMessage(plugin, "vetro:main", out.toByteArray());
        } catch (Throwable ignored) {
            viewer.sendMessage("§cVetroCore ist gerade nicht erreichbar.");
        }
    }

    private CoreReportEntry findCoreReport(Player viewer, long id) {
        for (CoreReportEntry report : coreReportCache.getOrDefault(viewer.getUniqueId(), List.of())) {
            if (report.id() == id) return report;
        }
        return null;
    }

    private String normalizeFilter(String filter) {
        if (filter == null) return "open";
        return switch (filter.toLowerCase(Locale.ROOT)) {
            case "closed", "today", "mine" -> filter.toLowerCase(Locale.ROOT);
            default -> "open";
        };
    }

    private String filterLabel(String filter) {
        return switch (normalizeFilter(filter)) {
            case "closed" -> "Geschlossen";
            case "today" -> "Heute";
            case "mine" -> "Meine Fälle";
            default -> "Offen";
        };
    }

    private boolean isOld(long createdAt) {
        return createdAt > 0L && System.currentTimeMillis() - createdAt >= 30L * 60L * 1000L;
    }

    private Material statusMaterial(CoreReportEntry report) {
        if ("closed".equalsIgnoreCase(report.status())) return Material.GRAY_DYE;
        return isOld(report.createdAt()) ? Material.REDSTONE_BLOCK : Material.LIME_DYE;
    }

    private String statusName(CoreReportEntry report) {
        if ("closed".equalsIgnoreCase(report.status())) return "§7Geschlossen";
        return isOld(report.createdAt()) ? "§4Alt und offen" : "§aOffen";
    }

    private ItemStack playerHead(Player target) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(target);
        meta.setDisplayName("§a" + target.getName());
        String faction = plugin.getFactionManager() == null ? null : plugin.getFactionManager().getFactionOf(target.getUniqueId());
        meta.setLore(List.of(
                "§7Rank: §f" + primaryGroup(target),
                "§7Faction: §c" + (faction == null ? "-" : plugin.getFactionManager().getDisplayName(faction)),
                "§8Klicken für Tools"
        ));
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, "player:" + target.getUniqueId());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack action(Material material, String name, String action, String... lore) {
        ItemStack item = VetroForgeUtil.item(material, name, lore);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(actionKey, PersistentDataType.STRING, action);
        item.setItemMeta(meta);
        return item;
    }

    private String primaryGroup(Player player) {
        if (player.hasPermission("vetroforge.admin")) return "Admin";
        if (player.hasPermission("vetroforge.staff.gui")) return "Staff";
        return "Spieler";
    }

    private static final class PlayersHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record ActionHolder(UUID targetId) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final class ReportsHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record ReportDetailHolder(long reportId) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    public record CoreReportEntry(
            long id,
            String reporterName,
            String targetName,
            String reason,
            String source,
            String status,
            long createdAt,
            String assignedToName,
            long assignedAt
    ) {
        public String ageText() {
            long seconds = Math.max(0L, (System.currentTimeMillis() - createdAt) / 1000L);
            if (seconds < 60L) return seconds + "s";
            long minutes = seconds / 60L;
            if (minutes < 60L) return minutes + "m";
            long hours = minutes / 60L;
            if (hours < 24L) return hours + "h";
            long days = hours / 24L;
            return days + "d";
        }
    }

    private record CoreReportStats(int openReports, int oldReports) {
    }
}
