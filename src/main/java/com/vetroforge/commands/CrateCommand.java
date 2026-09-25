// src/main/java/com/vetroforge/commands/CrateCommand.java
package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.crates.CrateHistoryService;
import com.vetroforge.crates.KeyCrateManager;
import com.vetroforge.items.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public class CrateCommand implements CommandExecutor, TabCompleter {

    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("dd.MM HH:mm")
            .withZone(ZoneId.systemDefault());

    private final FactionsPlugin plugin;
    private final KeyCrateManager crates;

    public CrateCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.crates = plugin.getKeyCrateManager();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (crates == null) {
            sender.sendMessage("§cCrates nicht verfügbar.");
            return true;
        }

        if (args.length == 0) {
            help(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        switch (sub) {
            case "help", "hilfe", "?" -> {
                help(sender, label);
                return true;
            }
            case "list", "ls" -> {
                sender.sendMessage("§8—— §6Crates §8——");
                if (crates.getCrateIds().isEmpty()) {
                    sender.sendMessage("§7Keine Crates in der Config gefunden.");
                } else {
                    crates.getCrateIds().forEach(id -> sender.sendMessage("§8- §f" + id));
                }
                return true;
            }
            case "fuse", "fusion" -> {
                if (!(sender instanceof Player player)) {
                    sender.sendMessage("§cDie Crystal-Fusion-GUI ist nur ingame verfügbar.");
                    return true;
                }
                crates.fuse(player);
                return true;
            }
            case "open" -> {
                sender.sendMessage("§6Crystals §8» §7Bitte löse Crystals beim §fCrystal NPC§7 ein.");
                return true;
            }
            case "give" -> {
                if (!hasAdmin(sender)) {
                    sender.sendMessage(UiDecor.noPermission());
                    return true;
                }
                if (args.length < 3) {
                    sender.sendMessage("§e/" + label + " give <Spieler> <common|rare|epic|legendary|mystic|1-5> [Menge]");
                    return true;
                }
                Player target = Bukkit.getPlayerExact(args[1]);
                if (target == null) {
                    sender.sendMessage("§cSpieler muss online sein, damit Crystals sicher ins Inventar gelegt werden.");
                    return true;
                }
                Rarity rarity = parseRarity(args[2]);
                if (rarity == Rarity.MYSTIC) {
                    sender.sendMessage("§5Mystic §8» §cMystic-Items sind Lootdrop-only. Mystic Crystals werden nicht per Command vergeben.");
                    return true;
                }
                int amount = (args.length >= 4) ? Math.max(1, Math.min(64, parseInt(args[3], 1))) : 1;

                crates.giveKey(target, rarity, amount);
                sender.sendMessage("§a" + rarity.display + " Crystal x" + amount + " an §f" + target.getName() + " §agegeben.");
                target.sendMessage("§8[§6Crates§8] §7Du hast §f" + amount + "x " + rarity.display + " Crystal §7erhalten.");
                return true;
            }
            case "history", "hist" -> {
                if (!sender.hasPermission("vetroforge.crates.history") && !hasAdmin(sender)) {
                    sender.sendMessage(UiDecor.noPermission());
                    return true;
                }
                if (args.length < 2) {
                    sender.sendMessage("§e/" + label + " history <Spieler> [Anzahl]");
                    return true;
                }
                showHistory(sender, args[1], args.length >= 3 ? parseInt(args[2], 10) : 10);
                return true;
            }
            default -> {
                help(sender, label);
                return true;
            }
        }
    }

    /* ================= Helper ================= */

    private int parseInt(String s, int def) { try { return Integer.parseInt(s); } catch (Exception e) { return def; } }

    private Rarity parseRarity(String raw) {
        if (raw == null) return Rarity.COMMON;
        String value = raw.toLowerCase(Locale.ROOT);
        return switch (value) {
            case "2", "rare" -> Rarity.RARE;
            case "3", "epic" -> Rarity.EPIC;
            case "4", "legendary" -> Rarity.LEGENDARY;
            case "5", "mystic", "mythic" -> Rarity.MYSTIC;
            default -> Rarity.COMMON;
        };
    }

    private void help(CommandSender sender, String label) {
        sender.sendMessage("§8—— §6Crate Command Center §8——");
        sender.sendMessage("§e/" + label + " list §7- verfügbare Crates");
        sender.sendMessage("§e/" + label + " open §7- Hinweis zum Crystal NPC");
        sender.sendMessage("§e/" + label + " fuse §7- Crystal-Fusion-GUI öffnen");
        sender.sendMessage("§e/" + label + " give <Spieler> <Rarity> [Menge] §7- Crystals geben");
        sender.sendMessage("§e/" + label + " history <Spieler> [Anzahl] §7- Drops prüfen");
        sender.sendMessage("§8Tipp: §7Crystals funktionieren nur beim Crystal NPC.");
    }

    private boolean hasAdmin(CommandSender sender) {
        return sender.hasPermission("vetroforge.admin") || sender.hasPermission("vetroforge.crates.admin") || sender.isOp();
    }

    private void showHistory(CommandSender sender, String playerName, int limit) {
        OfflinePlayer target = Bukkit.getOfflinePlayer(playerName);
        UUID uuid = target.getUniqueId();
        CrateHistoryService history = crates.getHistory();
        int safeLimit = Math.max(1, Math.min(40, limit));

        sender.sendMessage(ChatColor.DARK_GRAY + "—— " + ChatColor.GOLD + "Crate History: "
                + ChatColor.WHITE + (target.getName() == null ? uuid : target.getName()) + ChatColor.DARK_GRAY + " ——");
        var entries = history.entries(uuid, safeLimit);
        if (entries.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "Keine Crate-Gewinne gefunden.");
            return;
        }
        for (var entry : entries) {
            sender.sendMessage(ChatColor.DARK_GRAY + "- " + ChatColor.GRAY + TIME.format(Instant.ofEpochMilli(entry.time()))
                    + ChatColor.DARK_GRAY + " | " + ChatColor.GOLD + entry.crateId()
                    + ChatColor.DARK_GRAY + " -> " + ChatColor.WHITE + entry.amount() + "x " + entry.itemName());
        }
    }

    /* ============== Tab-Complete ============== */

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] A) {
        List<String> out = new ArrayList<>();
        if (A.length == 1) {
            String p = A[0].toLowerCase(Locale.ROOT);
            for (String option : new String[]{"help","list","open","fuse","give","history"}) {
                if (option.startsWith(p)) out.add(option);
            }
            return out;
        }
        if (A.length == 2 && ("give".equalsIgnoreCase(A[0]) || "history".equalsIgnoreCase(A[0]))) {
            String last = A[1].toLowerCase(Locale.ROOT);
            for (Player op : Bukkit.getOnlinePlayers()) {
                if (op.getName().toLowerCase(Locale.ROOT).startsWith(last)) out.add(op.getName());
            }
            return out;
        }
        if (A.length == 3 && "give".equalsIgnoreCase(A[0])) {
            String p = A[2].toLowerCase(Locale.ROOT);
            for (String t : new String[]{"common","rare","epic","legendary","mystic","1","2","3","4","5"})
                if (t.startsWith(p)) out.add(t);
            return out;
        }
        if (A.length == 4 && "give".equalsIgnoreCase(A[0])) {
            for (String n : new String[]{"1","2","3","5","10","16","32"}) if (n.startsWith(A[3])) out.add(n);
            return out;
        }
        return out;
    }
}
