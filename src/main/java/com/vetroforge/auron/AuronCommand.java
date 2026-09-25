package com.vetroforge.auron;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.OfflinePlayer;
import org.bukkit.Sound;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
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
import org.bukkit.util.StringUtil;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Player-facing {@code /auron} command: staff GUI/report/test surface for the
 * AURON anti-cheat. Reads/writes go straight to {@link AuronAntiCheatService},
 * which keeps its own signal store (see that class's javadoc) instead of the
 * shared {@code com.vetroforge.services.AntiCheatBridge} stub.
 */
public final class AuronCommand implements CommandExecutor, TabCompleter, Listener {

    private static final int[] FLAG_SLOTS = {28, 29, 30, 31, 32, 33, 34, 37, 38, 39, 40, 41, 42, 43};

    private final FactionsPlugin plugin;
    private final AuronAntiCheatService service;
    private final NamespacedKey playerKey;

    public AuronCommand(FactionsPlugin plugin, AuronAntiCheatService service) {
        this.plugin = plugin;
        this.service = service;
        this.playerKey = new NamespacedKey(plugin, "auron_player");
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.auron.view")) {
            sender.sendMessage("§9§lAURON §8» §cDu hast keinen Zugriff auf das AntiCheat-Center.");
            return true;
        }
        if (!(sender instanceof Player player)) {
            sender.sendMessage("AURON: " + (service.isEnabled() ? "aktiv" : "deaktiviert")
                    + ", auffällige Spieler: " + service.flaggedPlayers().size());
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("gui") || args[0].equalsIgnoreCase("status")) {
            openOverview(player);
            return true;
        }
        if (args[0].equalsIgnoreCase("alerts")) {
            boolean enabled = service.toggleAlerts(player);
            player.sendMessage("§9§lAURON §8» §7Live-Warnungen: " + (enabled ? "§aaktiv" : "§cpausiert"));
            return true;
        }
        if (args[0].equalsIgnoreCase("test")) {
            if (!player.hasPermission("vetroforge.auron.test")) {
                player.sendMessage("§9§lAURON §8» §cFür Testsignale fehlt dir die Berechtigung.");
                return true;
            }
            if (args.length < 2 || service.normalizeCheck(args[1]) == null) {
                player.sendMessage("§9§lAURON §8» §7/auron test <speed|vertical|reach|autoclicker|killaura> [Spieler] [Dauer]");
                return true;
            }
            boolean thirdIsDuration = args.length >= 3 && parseDuration(args[2]) > 0L;
            Player target = args.length >= 3 && !thirdIsDuration ? Bukkit.getPlayerExact(args[2]) : player;
            if (target == null) {
                player.sendMessage("§9§lAURON §8» §cDer Testspieler muss online sein.");
                return true;
            }
            String durationRaw = thirdIsDuration ? args[2] : args.length >= 4 ? args[3] : "1m";
            long duration = parseDuration(durationRaw);
            if (duration < 10_000L || duration > 600_000L) {
                player.sendMessage("§9§lAURON §8» §cDauer ungültig. Erlaubt sind 10s bis 10m.");
                return true;
            }
            if (!service.startTracking(target, args[1], duration, player)) {
                player.sendMessage("§9§lAURON §8» §cDie Messung konnte nicht gestartet werden.");
                return true;
            }
            player.sendMessage("§9§lAURON §8» §aLive-Messung gestartet: §f" + target.getName()
                    + " §8• §b" + service.normalizeCheck(args[1]) + " §8• §f" + durationRaw);
            player.sendMessage("§8Keine automatische Strafe. Das Ergebnis erscheint nach Ablauf der Zeit.");
            return true;
        }
        if (args[0].equalsIgnoreCase("player") && args.length >= 2) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            openPlayer(player, target.getUniqueId(), target.getName() == null ? args[1] : target.getName());
            return true;
        }
        if (args[0].equalsIgnoreCase("clear") && args.length >= 2) {
            if (!player.hasPermission("vetroforge.auron.manage")) {
                player.sendMessage("§9§lAURON §8» §cFür das Zurücksetzen fehlt dir die Berechtigung.");
                return true;
            }
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            service.clear(target.getUniqueId());
            player.sendMessage("§9§lAURON §8» §aSignale für §f" + args[1] + " §awurden zurückgesetzt.");
            return true;
        }
        player.sendMessage("§9§lAURON §8» §7/auron §8• §7/auron player <Name> §8• §7/auron alerts §8• §7/auron test <Check> [Spieler] [Dauer]");
        return true;
    }

    private long parseDuration(String value) {
        if (value == null || value.isBlank()) return -1L;
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        try {
            long multiplier = normalized.endsWith("m") ? 60_000L : normalized.endsWith("s") ? 1_000L : -1L;
            if (multiplier < 0L) return -1L;
            return Long.parseLong(normalized.substring(0, normalized.length() - 1)) * multiplier;
        } catch (NumberFormatException ignored) {
            return -1L;
        }
    }

    private void openOverview(Player viewer) {
        Inventory inventory = Bukkit.createInventory(new OverviewHolder(), 54, "§9§lAURON §8• §fAntiCheat Center");
        frame(inventory);

        List<AuronAntiCheatService.PlayerSnapshot> players = service.flaggedPlayers();
        inventory.setItem(4, item(Material.SHIELD, "§9§lAURON §8• §fLive-Schutz",
                service.isEnabled() ? "§a● AntiCheat aktiv" : "§c● AntiCheat deaktiviert",
                service.isPausedByMaintenance() ? "§ePausiert: Wartungsmodus aktiv" : "§7Überwachung läuft serverseitig.",
                "",
                "§7Auffällige Spieler: §f" + players.size(),
                "§7Auto-Bans: §cDeaktiviert",
                "§8Jedes Signal muss vom Team geprüft werden."));

        inventory.setItem(19, item(Material.SUGAR, "§b§lSpeed Check",
                "§7Erkennt extreme horizontale Bewegung.",
                "§8Berücksichtigt Effekte, Eis und Velocity."));
        inventory.setItem(20, item(Material.FEATHER, "§b§lVertical Check",
                "§7Erkennt unnatürliche Aufwärtsbewegung.",
                "§8Jump Boost und Server-Velocity sind ausgenommen."));
        inventory.setItem(22, item(Material.DIAMOND_SWORD, "§b§lReach Check",
                "§7Prüft Nahkampfreichweite zur Hitbox.",
                "§8Ping erhält eine begrenzte Toleranz."));
        inventory.setItem(24, item(Material.CLOCK, "§b§lAutoClicker Indikator",
                "§7Bewertet CPS und gleichförmige Klickabstände.",
                "§8Nur innerhalb eines aktiven Kampfzeitfensters."));
        inventory.setItem(25, item(Material.TARGET, "§b§lKillAura Indikator",
                "§7Kombiniert Blickwinkel, Sichtlinie", "§7und sehr schnelle Zielwechsel."));

        int index = 0;
        for (AuronAntiCheatService.PlayerSnapshot snapshot : players) {
            if (index >= FLAG_SLOTS.length) break;
            inventory.setItem(FLAG_SLOTS[index++], playerItem(snapshot));
        }
        if (players.isEmpty()) {
            inventory.setItem(31, item(Material.LIME_DYE, "§a§lKeine aktiven Auffälligkeiten",
                    "§7In den letzten 15 Minuten liegt", "§7kein belastbares AURON-Signal vor."));
        }

        inventory.setItem(45, item(service.alertsEnabled(viewer) ? Material.BELL : Material.GRAY_DYE,
                service.alertsEnabled(viewer) ? "§a§lWarnungen aktiv" : "§c§lWarnungen pausiert",
                "§7Schaltet deine persönlichen", "§7AURON-Livewarnungen um."));
        inventory.setItem(49, item(Material.CLOCK, "§bAktualisieren", "§7Lädt Signale und Stufen neu."));
        inventory.setItem(53, item(Material.BARRIER, "§cSchließen", "§7AntiCheat-Center verlassen."));
        viewer.openInventory(inventory);
        viewer.playSound(viewer.getLocation(), Sound.BLOCK_BEACON_ACTIVATE, 0.65F, 1.55F);
    }

    private void openPlayer(Player viewer, UUID playerId, String fallbackName) {
        AuronAntiCheatService.PlayerSnapshot snapshot = service.snapshot(playerId);
        String playerName = "Unbekannt".equals(snapshot.playerName()) ? fallbackName : snapshot.playerName();
        Inventory inventory = Bukkit.createInventory(new PlayerHolder(playerId, playerName), 54,
                "§9§lAURON §8• §f" + trim(playerName, 20));
        frame(inventory);
        inventory.setItem(4, playerSummary(snapshot, playerName));

        int slot = 19;
        int shown = 0;
        for (AuronAntiCheatService.FlagRecord flag : snapshot.flags()) {
            if (shown++ >= 14) break;
            inventory.setItem(slot, flagItem(flag));
            slot++;
            if (slot == 26) slot = 28;
            if (slot == 35) slot = 37;
        }
        if (snapshot.flags().isEmpty()) {
            inventory.setItem(22, item(Material.LIME_DYE, "§aKeine gespeicherten Signale",
                    "§7Der Spieler hat aktuell keine", "§7AURON-Hinweise im Zeitfenster."));
        }

        if (viewer.hasPermission("vetroforge.auron.manage") && !snapshot.flags().isEmpty()) {
            inventory.setItem(45, item(Material.REDSTONE_BLOCK, "§c§lSignale zurücksetzen",
                    "§7Entfernt nur AURON-Signale.", "§8Keine Strafe wird verändert."));
        }
        inventory.setItem(49, item(Material.ARROW, "§bZurück", "§7Zur AURON-Übersicht."));
        inventory.setItem(53, item(Material.BARRIER, "§cSchließen", "§7Fenster schließen."));
        viewer.openInventory(inventory);
    }

    @EventHandler
    public void onClick(InventoryClickEvent event) {
        InventoryHolder holder = event.getView().getTopInventory().getHolder();
        if (!(holder instanceof OverviewHolder) && !(holder instanceof PlayerHolder)) return;
        event.setCancelled(true);
        if (!(event.getWhoClicked() instanceof Player player) || event.getClickedInventory() == null
                || event.getClickedInventory() != event.getView().getTopInventory()) return;

        ItemStack clicked = event.getCurrentItem();
        if (clicked == null || !clicked.hasItemMeta()) return;
        String target = clicked.getItemMeta().getPersistentDataContainer().get(playerKey, PersistentDataType.STRING);
        if (target != null) {
            try {
                UUID playerId = UUID.fromString(target);
                openPlayer(player, playerId, Bukkit.getOfflinePlayer(playerId).getName());
            } catch (IllegalArgumentException ignored) {}
            return;
        }

        int slot = event.getRawSlot();
        if (holder instanceof OverviewHolder) {
            if (slot == 45) {
                service.toggleAlerts(player);
                openOverview(player);
            } else if (slot == 49) openOverview(player);
            else if (slot == 53) player.closeInventory();
            return;
        }

        PlayerHolder detail = (PlayerHolder) holder;
        if (slot == 45 && player.hasPermission("vetroforge.auron.manage")) {
            service.clear(detail.playerId());
            player.sendMessage("§9§lAURON §8» §aSignale für §f" + detail.playerName() + " §azurückgesetzt.");
            openOverview(player);
        } else if (slot == 49) openOverview(player);
        else if (slot == 53) player.closeInventory();
    }

    private ItemStack playerItem(AuronAntiCheatService.PlayerSnapshot snapshot) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(snapshot.playerId()));
        meta.setDisplayName(severityColor(snapshot.severity()) + "§l" + snapshot.playerName());
        long tests = snapshot.flags().stream().filter(AuronAntiCheatService.FlagRecord::simulated).count();
        meta.setLore(List.of(
                "§7AURON-Stufe: " + severityColor(snapshot.severity()) + snapshot.severity() + "/5",
                "§7Signalpunkte: §f" + String.format(Locale.GERMAN, "%.1f", snapshot.score()),
                "§7Echte Hinweise: §f" + (snapshot.flags().size() - tests),
                "§7Testsignale: §b" + tests,
                "",
                "§eKlick §8» §fDetails öffnen"
        ));
        meta.getPersistentDataContainer().set(playerKey, PersistentDataType.STRING, snapshot.playerId().toString());
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack playerSummary(AuronAntiCheatService.PlayerSnapshot snapshot, String name) {
        ItemStack item = new ItemStack(Material.PLAYER_HEAD);
        SkullMeta meta = (SkullMeta) item.getItemMeta();
        meta.setOwningPlayer(Bukkit.getOfflinePlayer(snapshot.playerId()));
        meta.setDisplayName("§f§l" + name);
        long tests = snapshot.flags().stream().filter(AuronAntiCheatService.FlagRecord::simulated).count();
        meta.setLore(List.of(
                "§7AURON-Stufe: " + severityColor(snapshot.severity()) + snapshot.severity() + "/5",
                "§7Signalpunkte: §f" + String.format(Locale.GERMAN, "%.1f", snapshot.score()),
                "§7Echte Hinweise: §f" + (snapshot.flags().size() - tests),
                "§7Testsignale: §b" + tests,
                "",
                "§8Hinweise sind keine automatische Schuldentscheidung."
        ));
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack flagItem(AuronAntiCheatService.FlagRecord flag) {
        Material material = switch (flag.check()) {
            case AuronAntiCheatService.CHECK_SPEED -> Material.SUGAR;
            case AuronAntiCheatService.CHECK_VERTICAL -> Material.FEATHER;
            case AuronAntiCheatService.CHECK_REACH -> Material.DIAMOND_SWORD;
            case AuronAntiCheatService.CHECK_AUTOCLICKER -> Material.CLOCK;
            case AuronAntiCheatService.CHECK_KILLAURA -> Material.TARGET;
            default -> Material.PAPER;
        };
        String time = new SimpleDateFormat("HH:mm:ss", Locale.GERMANY).format(new Date(flag.createdAt()));
        return item(material, (flag.simulated() ? "§b§lTEST " : "§c§l") + flag.check() + " §8• §f" + time,
                "§7Signal: §f" + trim(flag.evidence(), 42),
                flag.simulated() ? "§7Gewicht: §bkeine Gefahrenstufe"
                        : "§7Gewicht: §f" + String.format(Locale.GERMAN, "%.1f", flag.score()),
                "§8Nur zusammen mit weiteren Belegen bewerten.");
    }

    private void frame(Inventory inventory) {
        ItemStack pane = item(Material.BLUE_STAINED_GLASS_PANE, " ");
        for (int slot = 0; slot < inventory.getSize(); slot++) {
            int row = slot / 9;
            int column = slot % 9;
            if (row == 0 || row == 5 || column == 0 || column == 8) inventory.setItem(slot, pane);
        }
    }

    private ItemStack item(Material material, String name, String... lore) {
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.setDisplayName(name);
        meta.setLore(List.of(lore));
        item.setItemMeta(meta);
        return item;
    }

    private String severityColor(int severity) {
        if (severity >= 4) return "§4";
        if (severity >= 3) return "§c";
        if (severity >= 2) return "§6";
        return "§e";
    }

    private String trim(String text, int maxLength) {
        if (text == null) return "";
        return text.length() <= maxLength ? text : text.substring(0, maxLength - 3) + "...";
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!sender.hasPermission("vetroforge.auron.view")) return List.of();
        if (args.length == 1) {
            List<String> values = new ArrayList<>(List.of("gui", "status", "player", "alerts"));
            if (sender.hasPermission("vetroforge.auron.manage")) values.add("clear");
            if (sender.hasPermission("vetroforge.auron.test")) values.add("test");
            return StringUtil.copyPartialMatches(args[0], values, new ArrayList<>());
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("test")) {
            return StringUtil.copyPartialMatches(args[1],
                    List.of("speed", "vertical", "reach", "autoclicker", "killaura"), new ArrayList<>());
        }
        if (args.length == 3 && args[0].equalsIgnoreCase("test")) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            List<String> values = new ArrayList<>(names);
            values.add("30s");
            values.add("1m");
            values.add("5m");
            return StringUtil.copyPartialMatches(args[2], values, new ArrayList<>());
        }
        if (args.length == 4 && args[0].equalsIgnoreCase("test")) {
            return StringUtil.copyPartialMatches(args[3], List.of("30s", "1m", "5m", "10m"), new ArrayList<>());
        }
        if (args.length == 2 && (args[0].equalsIgnoreCase("player") || args[0].equalsIgnoreCase("clear"))) {
            List<String> names = Bukkit.getOnlinePlayers().stream().map(Player::getName).toList();
            return StringUtil.copyPartialMatches(args[1], names, new ArrayList<>());
        }
        return List.of();
    }

    private static final class OverviewHolder implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    private record PlayerHolder(UUID playerId, String playerName) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }
}
