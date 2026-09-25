// src/main/java/com/vetroforge/commands/CommandsCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.ChatBoxUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Generierte Komplettuebersicht aller VetroForge-Befehle, die der Spieler laut plugin.yml
 * tatsaechlich nutzen darf (kein "permission:" -&gt; oeffentlich, sonst hasPermission-Check).
 * Ergaenzt /help (kuratierte Kurzuebersicht), ersetzt es nicht.
 */
public final class CommandsCommand implements CommandExecutor, TabCompleter {

    private static final int PER_PAGE = 12;

    private final FactionsPlugin plugin;

    public CommandsCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }

        List<Entry> allowed = allowedCommands(player);
        int maxPage = Math.max(1, (int) Math.ceil(allowed.size() / (double) PER_PAGE));
        int page = 1;
        if (args.length >= 1) {
            try { page = Integer.parseInt(args[0]); } catch (NumberFormatException ignored) {}
        }
        page = Math.max(1, Math.min(page, maxPage));

        player.sendMessage(Component.text(" "));
        player.sendMessage(ChatBoxUtil.boxTop());
        player.sendMessage(ChatBoxUtil.center(Component.text("Befehle", NamedTextColor.AQUA)
                .append(Component.text("  " + allowed.size() + " verfuegbar  §8[" + page + "/" + maxPage + "]", NamedTextColor.GRAY))));
        player.sendMessage(ChatBoxUtil.divider());

        int from = (page - 1) * PER_PAGE;
        int to = Math.min(allowed.size(), from + PER_PAGE);
        if (allowed.isEmpty()) {
            player.sendMessage(ChatBoxUtil.center(Component.text("Keine Befehle verfuegbar.", NamedTextColor.DARK_GRAY)));
        }
        for (int i = from; i < to; i++) {
            Entry entry = allowed.get(i);
            Component line = Component.text("/" + entry.name, NamedTextColor.WHITE)
                    .append(Component.text("  " + entry.description, NamedTextColor.GRAY));
            if (entry.usage != null && !entry.usage.isBlank()) {
                line = line.hoverEvent(HoverEvent.showText(Component.text(entry.usage, NamedTextColor.YELLOW)));
            }
            player.sendMessage(ChatBoxUtil.center(line));
        }

        player.sendMessage(ChatBoxUtil.divider());
        player.sendMessage(ChatBoxUtil.center(Component.text("/commands <seite>  §8» §7naechste Seite ansehen", NamedTextColor.DARK_GRAY)));
        player.sendMessage(ChatBoxUtil.boxBottom());
        return true;
    }

    private List<Entry> allowedCommands(Player player) {
        Map<String, Map<String, Object>> declared = plugin.getDescription().getCommands();
        List<Entry> out = new ArrayList<>();
        for (Map.Entry<String, Map<String, Object>> raw : declared.entrySet()) {
            String name = raw.getKey();
            Map<String, Object> meta = raw.getValue();
            Object permission = meta.get("permission");
            if (permission instanceof String perm && !perm.isBlank() && !player.hasPermission(perm)) continue;

            String description = String.valueOf(meta.getOrDefault("description", ""));
            Object usageRaw = meta.get("usage");
            String usage = usageRaw == null ? null : String.valueOf(usageRaw).replace("<command>", name);
            out.add(new Entry(name, description, usage));
        }
        out.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        return out;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        return List.of();
    }

    private record Entry(String name, String description, String usage) {}
}
