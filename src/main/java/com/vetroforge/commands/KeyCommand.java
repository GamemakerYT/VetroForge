// src/main/java/com/vetroforge/commands/KeyCommand.java
package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.crates.KeyCrateManager;
import com.vetroforge.items.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public class KeyCommand implements CommandExecutor, TabCompleter {
    private final FactionsPlugin plugin;

    public KeyCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean isStaff(CommandSender s) {
        return s.hasPermission("vetroforge.staff.crate")
                || s.hasPermission("vetroforge.admin")
                || s.isOp();
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        KeyCrateManager kcm = plugin.getKeyCrateManager();

        if (a.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = a[0].toLowerCase(java.util.Locale.ROOT);

        // /key list  -> zeigt alle in der Config vorhandenen Crate-IDs
        if (sub.equals("list")) {
            sender.sendMessage("§8—— §aCrates §8——");
            for (String id : kcm.getCrateIds()) {
                sender.sendMessage("§7- §f" + id);
            }
            return true;
        }

        if (sub.equals("open")) {
            sender.sendMessage("§6Crystals §8» §7Bitte löse deinen Crystal beim §fCrystal NPC§7 ein.");
            return true;
        }

        if (sub.equals("give")) {
            if (!isStaff(sender)) {
                sender.sendMessage(UiDecor.noPermission());
                return true;
            }
            if (a.length < 2) {
                sender.sendMessage("§e/" + label + " give <Spieler> [common|rare|epic|legendary|mystic] [Menge]");
                return true;
            }
            Player target = Bukkit.getPlayerExact(a[1]);
            if (target == null) {
                sender.sendMessage("§cSpieler offline.");
                return true;
            }

            int amount = 1;
            Rarity rarity = Rarity.COMMON;
            if (a.length >= 3) {
                try {
                    amount = Math.max(1, Integer.parseInt(a[2]));
                } catch (NumberFormatException ignored) {
                    rarity = parseRarity(a[2]);
                    if (a.length >= 4) {
                        try { amount = Math.max(1, Integer.parseInt(a[3])); } catch (NumberFormatException ignored2) {}
                    }
                }
            }

            if (rarity == Rarity.MYSTIC) {
                sender.sendMessage("§5Mystic §8» §cMystic Crystals und Mystic Items bleiben Lootdrop-only.");
                return true;
            }

            kcm.giveKey(target, rarity, amount);
            sender.sendMessage("§a" + rarity.display + " Crystal vergeben: §f" + amount + " §7→ §f" + target.getName());
            return true;
        }

        // Unbekannter Subcommand
        sendHelp(sender, label);
        return true;
    }

    private void sendHelp(CommandSender s, String label) {
        s.sendMessage(ChatColor.GRAY + "Benutzung:");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " list" + ChatColor.GRAY + " – zeigt verfügbare Crates");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " open" + ChatColor.GRAY + " – zeigt den Crystal-NPC-Hinweis");
        s.sendMessage(ChatColor.YELLOW + "/" + label + " give <Spieler> [Rarity] [Menge]" + ChatColor.GRAY + " – gibt Crystals");
    }

    private Rarity parseRarity(String raw) {
        if (raw == null) return Rarity.COMMON;
        return switch (raw.toLowerCase(Locale.ROOT)) {
            case "2", "rare" -> Rarity.RARE;
            case "3", "epic" -> Rarity.EPIC;
            case "4", "legendary" -> Rarity.LEGENDARY;
            case "5", "mystic", "mythic" -> Rarity.MYSTIC;
            default -> Rarity.COMMON;
        };
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] A) {
        List<String> out = new ArrayList<>();
        if (A.length == 1) {
            for (String k : new String[]{"list", "open", "give"}) {
                if (k.startsWith(A[0].toLowerCase())) out.add(k);
            }
            return out;
        }
        if (A.length == 2 && A[0].equalsIgnoreCase("give")) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase().startsWith(A[1].toLowerCase())) out.add(p.getName());
            }
            return out;
        }
        if (A.length == 3 && A[0].equalsIgnoreCase("give")) {
            if (A[0].equalsIgnoreCase("give")) {
                String p = A[2].toLowerCase(Locale.ROOT);
                for (String n : new String[]{"common", "rare", "epic", "legendary", "mystic", "1", "2", "3", "5", "10"}) {
                    if (n.startsWith(p)) out.add(n);
                }
                return out;
            }
        }
        if (A.length == 4 && A[0].equalsIgnoreCase("give")) {
            for (String n : new String[]{"1", "2", "3", "5", "10", "16", "32"}) {
                if (n.startsWith(A[3])) out.add(n);
            }
            return out;
        }
        return out;
    }
}
