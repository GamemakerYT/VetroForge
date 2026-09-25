package com.vetroforge.staff;

import com.vetroforge.FactionsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.messaging.PluginMessageListener;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class StaffGuiService implements Listener, PluginMessageListener, CommandExecutor {
    public static final String CHANNEL = "vetro:main";

    private static final String TITLE_MAIN = "§9§lVetroForge §8• §fStaff";
    private static final String TITLE_PLAYERS = "§9§lVetroForge §8• §fPlayers";
    private static final String TITLE_REPORTS = "§9§lVetroForge §8• §fReports";

    private final FactionsPlugin plugin;
    private final Map<UUID, ReportStats> reportStatsCache = new ConcurrentHashMap<>();

    public StaffGuiService(FactionsPlugin plugin) {
        this.plugin = plugin;
        startReportStatsRefreshTask();
    }

    @Override
    public void onPluginMessageReceived(String channel, Player player, byte[] message) {
        if (!CHANNEL.equalsIgnoreCase(channel) || player == null) return;
        try (DataInputStream in = new DataInputStream(new ByteArrayInputStream(message))) {
            String action = in.readUTF();
            if ("OPEN_STAFF_GUI".equalsIgnoreCase(action)) {
                Bukkit.getScheduler().runTask(plugin, () -> openMain(player));
            } else if ("OPEN_REPORT_GUI".equalsIgnoreCase(action)) {
                in.readUTF(); // Reporter-UUID, vom Core mitgesendet aber hier ungenutzt
                String targetName = in.readUTF();
                Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("report " + targetName));
            } else if ("REPORT_LIST".equalsIgnoreCase(action)) {
                String filter = in.readUTF();
                int count = in.readInt();
                List<ReportEntry> reports = new ArrayList<>();
                for (int i = 0; i < count; i++) {
                    long id = in.readLong();
                    String reporterName = in.readUTF();
                    String targetName = in.readUTF();
                    String reason = in.readUTF();
                    String source = in.readUTF();
                    String status = in.readUTF();
                    long createdAt = in.readLong();
                    String assignedToName = in.available() > 0 ? in.readUTF() : "";
                    long assignedAt = in.available() > 0 ? in.readLong() : 0L;
                    reports.add(new ReportEntry(
                            id,
                            reporterName,
                            targetName,
                            reason,
                            source,
                            status,
                            createdAt,
                            assignedToName,
                            assignedAt
                    ));
                }
                Bukkit.getScheduler().runTask(plugin, () -> openReportList(player, reports, ReportFilter.from(filter), 0));
            } else if ("REPORT_STATS".equalsIgnoreCase(action)) {
                int open = in.readInt();
                int old = in.readInt();
                reportStatsCache.put(player.getUniqueId(), new ReportStats(open, old));
                Bukkit.getScheduler().runTask(plugin, () -> {
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof MainHolder) {
                        openMain(player, false);
                    }
                });
            }
        } catch (Exception exception) {
            plugin.getLogger().warning("[StaffGUI] Plugin-Message konnte nicht gelesen werden: " + exception.getMessage());
        }
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) {
            sender.sendMessage(ChatColor.RED + "Nur ingame.");
            return true;
        }
        openMain(player);
        return true;
    }

    public void openMain(Player player) {
        openMain(player, true);
    }

    private void openMain(Player player, boolean requestStats) {
        if (!canOpen(player)) {
            player.sendMessage("§cKeine Rechte fuer das Staff-Center.");
            return;
        }
        ReportStats stats = reportStatsCache.get(player.getUniqueId());
        Inventory inventory = Bukkit.createInventory(new MainHolder(), 54, TITLE_MAIN);
        decorate(inventory);
        inventory.setItem(4, reportStatsItem(stats));
        inventory.setItem(10, button(Material.PLAYER_HEAD, "§b§lPlayer oeffnen", List.of("§7Online-Spieler ansehen.", "§7Stats, History, AI, Report.")));
        inventory.setItem(12, button(Material.WRITABLE_BOOK, "§e§lReports", reportStatsLore(stats)));
        inventory.setItem(14, button(Material.BOOK, "§6§lHistory", List.of("§7Velocity History als", "§7klickbaren Chat-Link.")));
        inventory.setItem(16, button(Material.ENDER_EYE, "§d§lAI Review", List.of("§7AI-Review Link fuer", "§7einen Spieler vorbereiten.")));
        inventory.setItem(28, button(Material.BELL, "§a§lNotify Toggle", List.of("§7Schaltet Staff-Alerts", "§7fuer dich um.")));
        inventory.setItem(30, button(Material.REDSTONE_TORCH, "§c§lAutoNotify", List.of("§7Notify beim Join", "§7automatisch aktivieren.")));
        inventory.setItem(32, button(Material.COMPARATOR, "§9§lStaff online", staffOnlineLore()));
        inventory.setItem(34, button(Material.BARRIER, "§4§lErrors", List.of("§7Oeffnet /errors.")));
        inventory.setItem(49, button(Material.RED_STAINED_GLASS_PANE, "§cSchliessen", List.of()));
        player.openInventory(inventory);
        if (requestStats) requestReportStats(player);
    }

    private void openPlayers(Player viewer, PlayerMode mode, int page) {
        Inventory inventory = Bukkit.createInventory(new PlayersHolder(mode, page), 54, TITLE_PLAYERS);
        decorate(inventory);
        List<Player> players = new ArrayList<>(Bukkit.getOnlinePlayers());
        players.sort(Comparator.comparing(Player::getName, String.CASE_INSENSITIVE_ORDER));
        int start = Math.max(0, page) * 36;
        for (int i = 0; i < 36 && start + i < players.size(); i++) {
            Player target = players.get(start + i);
            inventory.setItem(i, playerHead(target, mode));
        }
        inventory.setItem(45, button(Material.ARROW, "§7Zurueck", List.of("§7Zur Staff-Uebersicht.")));
        inventory.setItem(48, button(Material.ARROW, "§fVorherige Seite", List.of("§7Seite " + Math.max(1, page))));
        inventory.setItem(49, button(Material.PAPER, mode.display, List.of("§7Modus: §f" + mode.cleanName)));
        inventory.setItem(50, button(Material.ARROW, "§fNaechste Seite", List.of("§7Seite " + (page + 2))));
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        Inventory top = event.getView().getTopInventory();
        if (!(top.getHolder() instanceof MainHolder)
                && !(top.getHolder() instanceof PlayersHolder)
                && !(top.getHolder() instanceof ReportsHolder)
                && !(top.getHolder() instanceof ReportDetailsHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player)) return;
        if (event.getClickedInventory() == null || event.getClickedInventory() != top) return;

        int slot = event.getRawSlot();
        if (top.getHolder() instanceof MainHolder) {
            switch (slot) {
                case 10 -> openPlayers(player, PlayerMode.OVERVIEW, 0);
                case 12 -> requestReportList(player, ReportFilter.OPEN);
                case 14 -> openPlayers(player, PlayerMode.HISTORY, 0);
                case 16 -> openPlayers(player, PlayerMode.AI_REVIEW, 0);
                case 28 -> {
                    player.closeInventory();
                    sendCoreAction(player, "TOGGLE_NOTIFY");
                }
                case 30 -> {
                    player.closeInventory();
                    sendCoreAction(player, "TOGGLE_AUTONOTIFY");
                }
                case 32 -> openPlayers(player, PlayerMode.OVERVIEW, 0);
                case 34 -> {
                    player.closeInventory();
                    player.performCommand("errors");
                }
                case 49 -> player.closeInventory();
                default -> {
                }
            }
            return;
        }

        if (top.getHolder() instanceof ReportsHolder holder) {
            if (slot == 45) {
                openMain(player);
                return;
            }
            if (slot == 47) {
                requestReportList(player, ReportFilter.OPEN);
                return;
            }
            if (slot == 48) {
                requestReportList(player, ReportFilter.TODAY);
                return;
            }
            if (slot == 49) {
                requestReportList(player, holder.filter);
                return;
            }
            if (slot == 50) {
                requestReportList(player, ReportFilter.CLOSED);
                return;
            }
            if (slot >= 0 && slot < holder.entries.size()) {
                ReportEntry report = holder.entries.get(slot);
                if (event.getClick() == ClickType.RIGHT || event.getClick() == ClickType.SHIFT_RIGHT) {
                    sendCloseReport(player, report.id());
                } else {
                    openReportDetails(player, report, holder.filter);
                }
            }
            return;
        }

        if (top.getHolder() instanceof ReportDetailsHolder holder) {
            ReportEntry report = holder.report;
            switch (slot) {
                case 20 -> sendProxyLink(player, "History", "/history " + report.targetName() + " all 1");
                case 22 -> runOrSuggest(player, "/tp " + report.targetName(), true);
                case 24 -> spectateAndTeleport(player, report.targetName());
                case 27 -> sendClaimReport(player, report.id());
                case 29 -> runOrSuggest(player, "/warn " + report.targetName() + " ", false);
                case 31 -> sendCloseReport(player, report.id());
                case 33, 45 -> requestReportList(player, holder.filter);
                default -> {
                }
            }
            return;
        }

        PlayersHolder holder = (PlayersHolder) top.getHolder();
        if (slot == 45) {
            openMain(player);
            return;
        }
        if (slot == 48) {
            openPlayers(player, holder.mode, Math.max(0, holder.page - 1));
            return;
        }
        if (slot == 50) {
            openPlayers(player, holder.mode, holder.page + 1);
            return;
        }
        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta() || clicked.getType() != Material.PLAYER_HEAD) return;
        String targetName = ChatColor.stripColor(clicked.getItemMeta().getDisplayName());
        if (targetName == null || targetName.isBlank()) return;
        handlePlayerAction(player, targetName, holder.mode);
    }

    @EventHandler
    public void onDrag(InventoryDragEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (holder instanceof MainHolder || holder instanceof PlayersHolder || holder instanceof ReportsHolder || holder instanceof ReportDetailsHolder) event.setCancelled(true);
    }

    private void handlePlayerAction(Player staff, String targetName, PlayerMode mode) {
        switch (mode) {
            case REPORT -> {
                staff.closeInventory();
                staff.performCommand("report " + targetName);
            }
            case HISTORY -> sendProxyLink(staff, "History", "/history " + targetName + " all 1");
            case AI_REVIEW -> sendProxyLink(staff, "AI Review", "/ai " + targetName);
            case OVERVIEW -> sendOverview(staff, targetName);
        }
    }

    private void sendOverview(Player staff, String targetName) {
        staff.closeInventory();
        staff.sendMessage("§9VetroForge §8» §7Player: §f" + targetName);
        staff.sendMessage(action("Stats", "/stats " + targetName, true)
                .append(Component.text(" §8| "))
                .append(action("Report", "/report " + targetName, true))
                .append(Component.text(" §8| "))
                .append(action("History", "/history " + targetName + " all 1", false))
                .append(Component.text(" §8| "))
                .append(action("AI Review", "/ai " + targetName, false)));
    }

    private void sendProxyLink(Player staff, String label, String command) {
        staff.closeInventory();
        staff.sendMessage(Component.text("§9VetroForge §8» §7" + label + ": ")
                .append(action(command, command, false)));
    }

    private Component action(String text, String command, boolean run) {
        return Component.text("§b[" + text + "]")
                .clickEvent(run ? ClickEvent.runCommand(command) : ClickEvent.suggestCommand(command))
                .hoverEvent(HoverEvent.showText(Component.text((run ? "Ausfuehren: " : "Vorschlagen: ") + command)));
    }

    private void sendCoreAction(Player player, String action) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF(action);
            out.writeUTF(player.getUniqueId().toString());
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (Throwable throwable) {
            player.sendMessage("§cVetroCore ist gerade nicht erreichbar.");
        }
    }

    private void requestReportList(Player player, ReportFilter filter) {
        try {
            player.closeInventory();
            player.sendMessage("§9VetroForge §8» §7Lade Reports: §f" + filter.label + "§7...");
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("REQUEST_REPORT_LIST");
            out.writeUTF(player.getUniqueId().toString());
            out.writeUTF(filter.id);
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (Throwable throwable) {
            player.sendMessage("§cVetroCore ist gerade nicht erreichbar.");
        }
    }

    private void sendCloseReport(Player player, long id) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("CLOSE_REPORT");
            out.writeUTF(player.getUniqueId().toString());
            out.writeLong(id);
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (Throwable throwable) {
            player.sendMessage("§cVetroCore ist gerade nicht erreichbar.");
        }
    }

    private void sendClaimReport(Player player, long id) {
        try {
            player.closeInventory();
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("CLAIM_REPORT");
            out.writeUTF(player.getUniqueId().toString());
            out.writeLong(id);
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (Throwable throwable) {
            player.sendMessage("§cVetroCore ist gerade nicht erreichbar.");
        }
    }

    private void requestReportStats(Player player) {
        try {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            DataOutputStream out = new DataOutputStream(bytes);
            out.writeUTF("REQUEST_REPORT_STATS");
            out.writeUTF(player.getUniqueId().toString());
            out.writeLong(oldThresholdMillis());
            player.sendPluginMessage(plugin, CHANNEL, bytes.toByteArray());
        } catch (Throwable ignored) {
        }
    }

    private void startReportStatsRefreshTask() {
        long seconds = Math.max(5L, plugin.getConfig().getLong("staffgui.reports.refreshSeconds", 15L));
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                try {
                    if (player.getOpenInventory().getTopInventory().getHolder() instanceof MainHolder) {
                        requestReportStats(player);
                    }
                } catch (Throwable ignored) {
                }
            }
        }, seconds * 20L, seconds * 20L);
    }

    private ItemStack reportStatsItem(ReportStats stats) {
        String open = stats == null ? "..." : String.valueOf(stats.open());
        String old = stats == null ? "..." : String.valueOf(stats.old());
        Material material = stats != null && stats.old() > 0 ? Material.REDSTONE_TORCH : Material.BELL;
        return button(material,
                "§e§lReports §8• §aOffen: §f" + open + " §8• §cAlt: §f" + old,
                List.of("§7Live aus VetroCore.", "§7Alt bedeutet " + oldThresholdMinutes() + " Minuten offen."));
    }

    private List<String> reportStatsLore(ReportStats stats) {
        List<String> lore = new ArrayList<>();
        lore.add("§7Report-GUI fuer Spieler");
        lore.add("§7direkt oeffnen.");
        lore.add("");
        if (stats == null) {
            lore.add("§8Zaehler wird geladen...");
        } else {
            lore.add("§aOffen: §f" + stats.open());
            lore.add("§cAlt: §f" + stats.old() + " §8(" + oldThresholdMinutes() + "m+)");
        }
        return lore;
    }

    private void openReportList(Player player, List<ReportEntry> reports, ReportFilter filter, int page) {
        Inventory inventory = Bukkit.createInventory(new ReportsHolder(reports, filter, page), 54, TITLE_REPORTS);
        decorate(inventory);
        int max = Math.min(45, reports.size());
        for (int i = 0; i < max; i++) {
            inventory.setItem(i, reportItem(reports.get(i)));
        }
        if (reports.isEmpty()) {
            inventory.setItem(22, button(Material.GRAY_DYE, "§7Keine offenen Reports", List.of("§8Alles ruhig.")));
        }
        inventory.setItem(45, button(Material.ARROW, "§7Zurueck", List.of("§7Zur Staff-Uebersicht.")));
        inventory.setItem(47, button(filter == ReportFilter.OPEN ? Material.LIME_DYE : Material.GRAY_DYE, "§aOffen", List.of("§7Offene Reports anzeigen.")));
        inventory.setItem(48, button(filter == ReportFilter.TODAY ? Material.CLOCK : Material.GRAY_DYE, "§eHeute", List.of("§7Reports von heute anzeigen.")));
        inventory.setItem(49, button(Material.NETHER_STAR, "§bAktualisieren", List.of("§7Laedt §f" + filter.label + "§7 neu.")));
        inventory.setItem(50, button(filter == ReportFilter.CLOSED ? Material.REDSTONE : Material.GRAY_DYE, "§cGeschlossen", List.of("§7Geschlossene Reports anzeigen.")));
        player.openInventory(inventory);
    }

    private ItemStack reportItem(ReportEntry report) {
        String age = compactAge(System.currentTimeMillis() - report.createdAt());
        return button(reportMaterial(report),
                reportColor(report) + "§lReport #" + report.id(),
                List.of(
                        "§7Reporter: §f" + report.reporterName(),
                        "§7Ziel: §f" + report.targetName(),
                        "§7Grund: §f" + report.reason(),
                        "§7Quelle: §8" + report.source(),
                        "§7Status: §f" + report.status(),
                        "§7Bearbeitet: §f" + assignedLabel(report),
                        "§7Alter: " + reportAgeColor(report) + age + reportAgeHint(report),
                        "",
                        "§aLinksklick §8» §7Details",
                        "§cRechtsklick §8» §7Report schliessen"
                ));
    }

    private void openReportDetails(Player player, ReportEntry report, ReportFilter filter) {
        Inventory inventory = Bukkit.createInventory(new ReportDetailsHolder(report, filter), 54, "§9§lVetroForge §8• §fReport #" + report.id());
        decorate(inventory);
        inventory.setItem(13, reportItem(report));
        inventory.setItem(20, button(Material.BOOK, "§6History", List.of("§7History im Proxy vorschlagen.")));
        inventory.setItem(22, button(Material.ENDER_PEARL, "§bTeleport", List.of("§7Zu §f" + report.targetName() + " §7teleportieren.")));
        inventory.setItem(24, button(Material.ENDER_EYE, spectateButtonName(player), spectateButtonLore(player, report.targetName())));
        inventory.setItem(27, button(Material.NAME_TAG, "§aFall uebernehmen", List.of("§7Markiert Report #" + report.id(), "§7als deinen aktuellen Fall.", "", "§7Aktuell: §f" + assignedLabel(report))));
        inventory.setItem(29, button(Material.PAPER, "§eWarn", List.of("§7Warn-Command vorbereiten.")));
        inventory.setItem(31, button(Material.REDSTONE_BLOCK, "§cReport schliessen", List.of("§7Schliesst Report #" + report.id() + ".")));
        inventory.setItem(33, button(Material.ARROW, "§7Zur Liste", List.of("§7Zurueck zu " + filter.label + ".")));
        inventory.setItem(45, button(Material.ARROW, "§7Zurueck", List.of("§7Zur Report-Liste.")));
        player.openInventory(inventory);
    }

    private void runOrSuggest(Player player, String command, boolean run) {
        player.closeInventory();
        if (run) {
            player.performCommand(command.startsWith("/") ? command.substring(1) : command);
            return;
        }
        player.sendMessage(Component.text("§9VetroForge §8» ")
                .append(action(command, command, false)));
    }

    private void spectateAndTeleport(Player player, String targetName) {
        player.closeInventory();
        boolean vanish = canUseVanish(player);
        if (vanish) player.performCommand("vanish on");
        player.performCommand("gamemode spectator");
        Bukkit.getScheduler().runTask(plugin, () -> player.performCommand("tp " + targetName));
        player.sendMessage("§9VetroForge §8» §7" + (vanish ? "Vanish + " : "") + "Spectate + Teleport: §f" + targetName);
    }

    private String spectateButtonName(Player player) {
        return canUseVanish(player) ? "§5Vanish + Spectate + TP" : "§dSpectate + TP";
    }

    private List<String> spectateButtonLore(Player player, String targetName) {
        List<String> lore = new ArrayList<>();
        if (canUseVanish(player)) lore.add("§7Vanish einschalten, falls verfuegbar.");
        lore.add("§7Spectator aktivieren");
        lore.add("§7und direkt zu §f" + targetName + " §7springen.");
        return lore;
    }

    private boolean canUseVanish(Player player) {
        return player.isOp()
                || player.hasPermission("essentials.vanish")
                || player.hasPermission("minecraft.command.vanish")
                || player.hasPermission("vetroforge.staff.vanish")
                || player.hasPermission("vetroforge.admin");
    }

    private Material reportMaterial(ReportEntry report) {
        if (!isOpen(report)) return Material.GRAY_DYE;
        long minutes = reportAgeMinutes(report);
        if (minutes >= oldThresholdMinutes()) return Material.RED_DYE;
        if (minutes >= waitingThresholdMinutes()) return Material.ORANGE_DYE;
        return Material.LIME_DYE;
    }

    private String reportColor(ReportEntry report) {
        if (!isOpen(report)) return "§7";
        long minutes = reportAgeMinutes(report);
        if (minutes >= oldThresholdMinutes()) return "§c";
        if (minutes >= waitingThresholdMinutes()) return "§6";
        return "§a";
    }

    private String reportAgeColor(ReportEntry report) {
        if (!isOpen(report)) return "§7";
        long minutes = reportAgeMinutes(report);
        if (minutes >= oldThresholdMinutes()) return "§c";
        if (minutes >= waitingThresholdMinutes()) return "§6";
        return "§a";
    }

    private String reportAgeHint(ReportEntry report) {
        if (!isOpen(report)) return " §8• geschlossen";
        long minutes = reportAgeMinutes(report);
        if (minutes >= oldThresholdMinutes()) return " §8• §calt";
        if (minutes >= waitingThresholdMinutes()) return " §8• §6wartet";
        return " §8• §aneu";
    }

    private String assignedLabel(ReportEntry report) {
        String assigned = report.assignedToName();
        if (assigned == null || assigned.isBlank()) return "Niemand";
        return assigned;
    }

    private long oldThresholdMinutes() {
        return Math.max(1L, plugin.getConfig().getLong("staffgui.reports.oldThresholdMinutes", 30L));
    }

    private long waitingThresholdMinutes() {
        long old = oldThresholdMinutes();
        return Math.max(1L, Math.min(old, plugin.getConfig().getLong("staffgui.reports.waitingThresholdMinutes", 10L)));
    }

    private long oldThresholdMillis() {
        return oldThresholdMinutes() * 60L * 1000L;
    }

    private long reportAgeMinutes(ReportEntry report) {
        return Math.max(0L, (System.currentTimeMillis() - report.createdAt()) / 60000L);
    }

    private boolean isOpen(ReportEntry report) {
        return report.status() != null && report.status().equalsIgnoreCase("open");
    }

    private ItemStack playerHead(Player target, PlayerMode mode) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        item.editMeta(meta -> {
            meta.setDisplayName("§f" + target.getName());
            List<String> lore = new ArrayList<>();
            lore.add("§7Modus: §f" + mode.cleanName);
            lore.add("§7Ping: §f" + target.getPing() + "ms");
            lore.add("§7Welt: §f" + target.getWorld().getName());
            lore.add("");
            lore.add("§aKlicken zum Oeffnen.");
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
            if (meta instanceof SkullMeta skullMeta) skullMeta.setOwningPlayer(target);
        });
        return item;
    }

    private void decorate(Inventory inventory) {
        ItemStack black = button(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        ItemStack blue = button(Material.BLUE_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inventory.getSize(); i++) inventory.setItem(i, black);
        for (int i : List.of(0, 1, 7, 8, 9, 17, 36, 44, 45, 46, 52, 53)) inventory.setItem(i, blue);
    }

    private ItemStack button(Material material, String name, List<String> lore) {
        ItemStack item = new ItemStack(material);
        item.editMeta(meta -> {
            meta.setDisplayName(name);
            meta.setLore(lore);
            meta.addItemFlags(ItemFlag.values());
        });
        return item;
    }

    private List<String> staffOnlineLore() {
        List<String> lore = new ArrayList<>();
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (canOpen(player)) lore.add("§8• §f" + player.getName());
        }
        if (lore.isEmpty()) lore.add("§7Kein Staff online.");
        return lore;
    }

    private boolean canOpen(Player player) {
        if (player == null) return false;
        try {
            if (player.isOp()
                    || player.hasPermission("vetroforge.staff.gui")
                    || player.hasPermission("vetroforge.staff.*")
                    || player.hasPermission("vetroforge.admin")) {
                return true;
            }
        } catch (Throwable ignored) {
        }
        try {
            return plugin.getStaffProtectionService() != null && plugin.getStaffProtectionService().isStaff(player);
        } catch (Throwable ignored) {
            return false;
        }
    }

    private enum PlayerMode {
        OVERVIEW("§bPlayer oeffnen", "Player oeffnen"),
        REPORT("§eReport", "Report"),
        HISTORY("§6History", "History"),
        AI_REVIEW("§dAI Review", "AI Review");

        private final String display;
        private final String cleanName;

        PlayerMode(String display, String cleanName) {
            this.display = display;
            this.cleanName = cleanName;
        }
    }

    private static final class MainHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private static final class PlayersHolder implements InventoryHolder {
        private final PlayerMode mode;
        private final int page;

        private PlayersHolder(PlayerMode mode, int page) {
            this.mode = mode;
            this.page = page;
        }

        @Override public Inventory getInventory() { return null; }
    }

    private static final class ReportsHolder implements InventoryHolder {
        private final List<ReportEntry> entries;
        private final ReportFilter filter;
        private final int page;

        private ReportsHolder(List<ReportEntry> entries, ReportFilter filter, int page) {
            this.entries = entries;
            this.filter = filter;
            this.page = page;
        }

        @Override public Inventory getInventory() { return null; }
    }

    private static final class ReportDetailsHolder implements InventoryHolder {
        private final ReportEntry report;
        private final ReportFilter filter;

        private ReportDetailsHolder(ReportEntry report, ReportFilter filter) {
            this.report = report;
            this.filter = filter;
        }

        @Override public Inventory getInventory() { return null; }
    }

    private enum ReportFilter {
        OPEN("open", "Offen"),
        CLOSED("closed", "Geschlossen"),
        TODAY("today", "Heute");

        private final String id;
        private final String label;

        ReportFilter(String id, String label) {
            this.id = id;
            this.label = label;
        }

        private static ReportFilter from(String raw) {
            if (raw == null) return OPEN;
            for (ReportFilter filter : values()) {
                if (filter.id.equalsIgnoreCase(raw)) return filter;
            }
            return OPEN;
        }
    }

    private record ReportEntry(long id, String reporterName, String targetName, String reason, String source, String status, long createdAt, String assignedToName, long assignedAt) {}

    private record ReportStats(int open, int old) {}

    private String compactAge(long millis) {
        long seconds = Math.max(0L, millis / 1000L);
        if (seconds < 60) return seconds + "s";
        long minutes = seconds / 60L;
        if (minutes < 60) return minutes + "m";
        long hours = minutes / 60L;
        if (hours < 24) return hours + "h";
        return (hours / 24L) + "d";
    }
}
