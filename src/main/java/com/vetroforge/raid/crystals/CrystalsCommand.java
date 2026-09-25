// src/main/java/com/vetroforge/raid/crystals/CrystalsCommand.java
package com.vetroforge.raid.crystals;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.factions.FactionManager;
import com.vetroforge.raid.RaidCrystalManager;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.text.NumberFormat;
import java.util.*;
import java.util.stream.Collectors;

public class CrystalsCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final NumberFormat nf = NumberFormat.getIntegerInstance(Locale.GERMANY);

    public CrystalsCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] a) {
        if (a.length >= 1 && a[0].equalsIgnoreCase("give")) {
            return give(s, a);
        }

        if (!(s instanceof Player p)) {
            s.sendMessage("§cNur ingame nutzbar.");
            return true;
        }

        // Manager holen
        RaidCrystalManager rc = plugin.getRaidCrystalManager();
        if (rc == null) { p.sendMessage(legacy(tr(p, "unavailable"))); return true; }

        boolean useFaction = rc.useFactionSource();
        String src = tr(p, useFaction ? "source.faction" : "source.player");

        int balance = 0;
        int cap = Math.max(1, plugin.getConfig().getInt("raid.crystals.max", 20));
        String ownerLine;

        if (useFaction) {
            FactionManager fm = plugin.getFactionManager();
            String fac = (fm == null) ? null : fm.getFactionOf(p.getUniqueId());
            if (fac == null) {
                ownerLine = tr(p, "owner.no_faction");
                balance = 0;
            } else {
                balance = rc.getForFaction(fac);
                cap = rc.getCrystalCap(fac);
                ownerLine = tr(p, "owner.faction", "faction", fm.getDisplayName(fac));
            }
        } else {
            balance = rc.getForPlayer(p.getUniqueId());
            ownerLine = tr(p, "owner.player", "player", p.getName());
        }

        // UI
        p.sendMessage(Component.text(" "));
        p.sendMessage(boxTop());
        p.sendMessage(center(Component.text(tr(p, "title"), TextColor.color(0xB37BFF))
                .append(Component.text("  (" + src + ")", NamedTextColor.GRAY))));

        // Zahl + kleiner „Balken“ (0..10k skaliert, nur Deko)
        p.sendMessage(center(bigNumber(balance)));
        p.sendMessage(center(tinyBar(balance, cap)));
        p.sendMessage(center(Component.text(nf.format(balance) + " / " + nf.format(cap), NamedTextColor.GRAY)));

        p.sendMessage(center(legacy(ownerLine)));
        p.sendMessage(center(Component.text(tr(p, "redeem"), NamedTextColor.GRAY)
                .append(Component.text("/raidshop", NamedTextColor.GOLD)
                        .clickEvent(ClickEvent.runCommand("/raidshop"))
                        .hoverEvent(HoverEvent.showText(Component.text("Raid-Shop öffnen", NamedTextColor.GOLD))))));

        // Hinweis-Konfig
        p.sendMessage(center(Component.text(tr(p, "source.label"), NamedTextColor.DARK_GRAY)
                .append(Component.text(src, NamedTextColor.WHITE))
                .append(Component.text("  •  ", NamedTextColor.DARK_GRAY))
                .append(Component.text(tr(p, useFaction ? "mode.shared" : "mode.personal"), NamedTextColor.DARK_GRAY))));

        // Top 5
        List<Map.Entry<String,Integer>> top = rc.topFactions(5);
        if (useFaction && !top.isEmpty()) {
            p.sendMessage(center(Component.text(tr(p, "top"), NamedTextColor.LIGHT_PURPLE)));
            int i = 1;
            for (Map.Entry<String,Integer> e : top) {
                String line = "§8" + i + ". §d" + e.getKey() + " §7— §f" + nf.format(e.getValue()) + "✦";
                p.sendMessage(center(Component.text(line)));
                i++;
            }
        }

        p.sendMessage(boxBottom());
        p.sendMessage(Component.text(" "));

        // ActionBar kleiner Tipp
        try { p.sendActionBar(Component.text(tr(p, "tip"), NamedTextColor.AQUA)); } catch (Throwable ignored) {}

        return true;
    }

    /** Konsolenfähig (Tebex/Webshop): schreibt Crystals immer ins persönliche Guthaben des Spielers. */
    private boolean give(CommandSender s, String[] a) {
        if (!s.hasPermission("vetroforge.crystals.admin")) {
            s.sendMessage("§cKeine Rechte.");
            return true;
        }
        if (a.length < 3) {
            s.sendMessage("§7/crystals give <spieler> <menge>");
            return true;
        }
        RaidCrystalManager rc = plugin.getRaidCrystalManager();
        if (rc == null) {
            s.sendMessage("§cKriegsbeute-System nicht verfügbar.");
            return true;
        }
        var target = Bukkit.getOfflinePlayer(a[1]);
        if (!target.hasPlayedBefore() && !target.isOnline()) {
            s.sendMessage("§cSpieler wurde noch nie auf diesem Server gesehen.");
            return true;
        }
        int amount;
        try {
            amount = Integer.parseInt(a[2]);
        } catch (NumberFormatException e) {
            s.sendMessage("§cUngültige Zahl.");
            return true;
        }
        rc.addForPlayer(target.getUniqueId(), amount);
        int now = rc.getForPlayer(target.getUniqueId());
        s.sendMessage("§a+" + amount + " Kriegsbeute für §e" + (target.getName() == null ? a[1] : target.getName()) + "§a. Neuer Stand: §e" + now);
        Player online = target.getPlayer();
        if (online != null) online.sendMessage("§dKriegsbeute §8» §a+" + amount + " erhalten.");
        return true;
    }

    /* ===== TabComplete ===== */
    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String alias, String[] args) {
        // Platzhalter für später (z.B. /crystals top [limit]) – jetzt leer
        return Collections.emptyList();
    }

    /* ===== UI-Helfer ===== */

    private Component boxTop()    { return com.vetroforge.util.ChatBoxUtil.boxTop(); }
    private Component boxBottom() { return com.vetroforge.util.ChatBoxUtil.boxBottom(); }
    private Component center(Component c) { return com.vetroforge.util.ChatBoxUtil.center(c); }

    private Component bigNumber(int v) {
        String s = nf.format(v) + " ✦";
        return Component.text(s, TextColor.color(0xFFFFFF));
    }

    /** Kleiner, rein visueller Balken (skaliert grob bis 10k). */
    private Component tinyBar(int value, int max) {
        double t = Math.max(0, Math.min(1, value / (double) max));
        int len = 24;
        int full = (int) Math.round(len * t);

        Component left  = Component.text("▕", NamedTextColor.DARK_GRAY);
        Component right = Component.text("▏", NamedTextColor.DARK_GRAY);
        Component bar   = Component.empty();

        for (int i = 0; i < len; i++) {
            boolean filled = i < full;
            double k = (len == 1) ? 1.0 : (i / (double) (len - 1));
            var col = filled ? gradient(k) : NamedTextColor.DARK_GRAY;
            bar = bar.append(Component.text("█", col));
        }
        return left.append(bar).append(right);
    }

    private TextColor gradient(double k) {
        k = Math.max(0, Math.min(1, k));
        int r1=0x9A, g1=0x7C, b1=0xFF; // lila → gold → smaragd
        int r2=0xFF, g2=0xC7, b2=0x5A;
        int r3=0x55, g3=0xFF, b3=0xA0;
        if (k <= 0.5) {
            double t = k / 0.5;
            return TextColor.color(lerp(r1,r2,t), lerp(g1,g2,t), lerp(b1,b2,t));
        } else {
            double t = (k - 0.5) / 0.5;
            return TextColor.color(lerp(r2,r3,t), lerp(g2,g3,t), lerp(b2,b3,t));
        }
    }

    private int lerp(int a, int b, double t) { return (int) Math.round(a + (b - a) * Math.max(0, Math.min(1, t))); }

    private String tr(Player player, String key, Object... values) {
        return com.vetroforge.util.VetroForgeUtil.tr(plugin, player, "crystals.command." + key,
                com.vetroforge.util.VetroForgeUtil.vars(values));
    }

    private Component legacy(String text) {
        return LegacyComponentSerializer.legacySection().deserialize(text);
    }
}
