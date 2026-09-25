// src/main/java/com/vetroforge/raid/energy/EnergyCommand.java
package com.vetroforge.raid.energy;

import com.vetroforge.FactionsPlugin;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.title.Title;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.time.Duration;

public class EnergyCommand implements CommandExecutor {

    private final FactionsPlugin plugin;

    public EnergyCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        if (!(s instanceof Player p)) {
            s.sendMessage("§cNur ingame nutzbar.");
            return true;
        }
        EnergyManager em = plugin.getEnergyManager();
        if (em == null) {
            p.sendMessage(tr(p, "unavailable"));
            return true;
        }

        int cur = em.get(p.getUniqueId());
        int cap = em.getMax(p.getUniqueId());
        long ms  = em.millisToNext(p.getUniqueId());

        // Header + Zähler
        Component header = Component.text(tr(p, "title"), TextColor.color(0xF5B642));
        Component amount = Component.text(" " + cur + "/" + cap, TextColor.color(0xFFFFFF));
        p.sendMessage(Component.text(" "));
        p.sendMessage(boxTop());
        p.sendMessage(center(header.append(amount)));

        // Fortschrittsbalken (20 Segmente)
        p.sendMessage(center(progressBar(cur, cap, 20)));

        // Countdown / Info
        Component line;
        if (cur >= cap) {
            line = Component.text(tr(p, "max"), NamedTextColor.GREEN);
            try {
                p.showTitle(Title.title(
                        Component.text(tr(p, "max_title"), NamedTextColor.GOLD),
                        Component.text(tr(p, "max_subtitle"), NamedTextColor.GREEN),
                        Title.Times.of(Duration.ofMillis(200), Duration.ofMillis(900), Duration.ofMillis(300))
                ));
            } catch (Throwable ignored) {}
        } else {
            line = Component.text(tr(p, "next"), NamedTextColor.GRAY)
                    .append(Component.text(human(ms), NamedTextColor.WHITE))
                    .append(Component.text("  ⏳", NamedTextColor.DARK_GRAY));
            // kurzer ActionBar-Hinweis
            try { p.sendActionBar(Component.text(tr(p, "next_actionbar", "remaining", human(ms)), NamedTextColor.GOLD)); } catch (Throwable ignored) {}
        }
        p.sendMessage(center(line));
        p.sendMessage(center(Component.text(tr(p, "rate", "cap", cap), NamedTextColor.DARK_GRAY)));
        p.sendMessage(center(Component.text("Raid-Shop: ", NamedTextColor.GRAY)
                .append(Component.text("/raidshop", NamedTextColor.GOLD)
                        .clickEvent(ClickEvent.runCommand("/raidshop"))
                        .hoverEvent(HoverEvent.showText(Component.text("Energy einsetzen", NamedTextColor.YELLOW))))));
        p.sendMessage(boxBottom());
        p.sendMessage(Component.text(" "));

        return true;
    }

    /* ===== Deko & Helpers ===== */

    private Component boxTop()    { return com.vetroforge.util.ChatBoxUtil.boxTop(); }
    private Component boxBottom() { return com.vetroforge.util.ChatBoxUtil.boxBottom(); }

    private Component center(Component c) {
        return com.vetroforge.util.ChatBoxUtil.center(c);
    }

    private String human(long ms) {
        long totalSec = Math.max(0, ms / 1000);
        long h = totalSec / 3600;
        long m = (totalSec % 3600) / 60;
        long s = totalSec % 60;
        if (h > 0) return String.format("%d:%02d:%02d", h, m, s);
        return String.format("%02d:%02d", m, s);
    }

    /** Farbiger Balken mit 20 Segmenten. Gefüllte Segmente erhalten einen Verlauf rot→gelb→grün. */
    private Component progressBar(int cur, int cap, int len) {
        double t = cap == 0 ? 0.0 : cur / (double) cap;
        return com.vetroforge.util.ChatBoxUtil.progressBar(t, len);
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, "energy.command." + key, VetroForgeUtil.vars(values));
    }
}
