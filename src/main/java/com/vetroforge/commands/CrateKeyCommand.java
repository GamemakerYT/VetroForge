// ===================================================================
// File: src/main/java/com/vetroforge/commands/CrateKeyCommand.java
// ===================================================================
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.crates.KeyCrateManager;
import com.vetroforge.items.Rarity;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.*;

public class CrateKeyCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public CrateKeyCommand(FactionsPlugin plugin) { this.plugin = plugin; }

    @Override
    public boolean onCommand(CommandSender s, Command c, String l, String[] a) {
        if (!s.hasPermission("vetroforge.crates.give")) {
            s.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (a.length < 1) {
            s.sendMessage(ChatColor.YELLOW + "Verwendung: /cratekey <Spieler> [common|rare|epic|legendary|mystic] [Menge]");
            return true;
        }
        Player target = Bukkit.getPlayerExact(a[0]);
        if (target == null) {
            s.sendMessage(ChatColor.RED + "Spieler offline.");
            return true;
        }
        Rarity rarity = Rarity.COMMON;
        int amount = 1;
        if (a.length >= 2) {
            try { amount = Math.max(1, Integer.parseInt(a[1])); }
            catch (NumberFormatException ignored) { rarity = parseRarity(a[1]); }
        }
        if (a.length >= 3) {
            try { amount = Math.max(1, Integer.parseInt(a[2])); } catch (NumberFormatException ignored) {}
        }
        if (rarity == Rarity.MYSTIC) {
            s.sendMessage(ChatColor.DARK_PURPLE + "Mystic " + ChatColor.DARK_GRAY + "» "
                    + ChatColor.RED + "Mystic Crystals und Mystic Items bleiben Lootdrop-only.");
            return true;
        }
        KeyCrateManager kcm = plugin.getKeyCrateManager();
        kcm.giveKey(target, rarity, amount);
        s.sendMessage(ChatColor.GREEN + "OK, " + rarity.display + " Crystal(s) x" + amount + " an " + target.getName() + " gegeben.");
        return true;
    }

    private Rarity parseRarity(String raw) {
        if (raw == null) return Rarity.COMMON;
        try { return Rarity.valueOf(raw.toUpperCase(Locale.ROOT)); }
        catch (IllegalArgumentException ignored) { return raw.equalsIgnoreCase("mythic") ? Rarity.MYSTIC : Rarity.COMMON; }
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {
        if (a.length == 1) {
            List<String> names = new ArrayList<String>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return StringUtil.copyPartialMatches(a[0], names, new ArrayList<String>());
        }
        if (a.length == 2) {
            return StringUtil.copyPartialMatches(a[1], Arrays.asList("common","rare","epic","legendary","mystic","1","2","3","5","10"), new ArrayList<String>());
        }
        if (a.length == 3) {
            return StringUtil.copyPartialMatches(a[2], Arrays.asList("1","2","3","5","10"), new ArrayList<String>());
        }
        return Collections.<String>emptyList();
    }
}
