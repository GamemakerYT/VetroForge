package com.vetroforge.commands;

import com.vetroforge.util.ChatBoxUtil;
import com.vetroforge.util.UiDecor;

import com.vetroforge.warzone.WarzoneMobService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.entity.LivingEntity;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class AdminBossCommand implements CommandExecutor, TabCompleter {
    private final WarzoneMobService service;

    public AdminBossCommand(WarzoneMobService service) { this.service = service; }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!hasAccess(sender)) return message(sender, UiDecor.noPermission());
        String sub = args.length == 0 ? "list" : args[0].toLowerCase(Locale.ROOT);
        if (sub.equals("list")) {
            List<LivingEntity> active = service.activeBosses();
            sender.sendMessage(Component.text(" "));
            sender.sendMessage(ChatBoxUtil.boxTop());
            sender.sendMessage(ChatBoxUtil.center(Component.text("Warzone Bosses", NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD)
                    .append(Component.text("  " + active.size() + " aktiv", NamedTextColor.GRAY))));
            sender.sendMessage(ChatBoxUtil.divider());
            if (active.isEmpty()) {
                sender.sendMessage(ChatBoxUtil.center(Component.text("Kein Boss aktiv.", NamedTextColor.DARK_GRAY)));
            } else {
                active.forEach(entity -> sender.sendMessage(ChatBoxUtil.center(legacy("§8• " + service.describeBoss(entity)))));
            }
            sender.sendMessage(ChatBoxUtil.divider());
            sender.sendMessage(ChatBoxUtil.center(Component.text("Spawnpunkte", NamedTextColor.RED, net.kyori.adventure.text.format.TextDecoration.BOLD)));
            List<String> spawns = service.bossSpawnLines();
            if (spawns.isEmpty()) {
                sender.sendMessage(ChatBoxUtil.center(Component.text("Keine gesetzt.", NamedTextColor.DARK_GRAY)));
            } else {
                spawns.forEach(line -> sender.sendMessage(ChatBoxUtil.center(legacy(line))));
            }
            sender.sendMessage(ChatBoxUtil.boxBottom());
            return true;
        }
        if (sub.equals("set") && sender instanceof Player player && args.length >= 2) {
            if (service.isKnownBoss(args[1]) && !service.isValidBossLocation(player.getLocation())) {
                return message(sender, "§cBoss-Spawnpunkte müssen außerhalb des Spawn-Bereichs in der Warzone liegen.");
            }
            return message(sender, service.setBossSpawn(args[1], player.getLocation())
                    ? "§aSpawnpunkt für §f" + args[1].toLowerCase(Locale.ROOT) + " §agesetzt."
                    : "§cBoss-Typ unbekannt. §7Nutze ravager, inferno oder reaper.");
        }
        if (sub.equals("spawn") && args.length >= 2) {
            return message(sender, service.spawnBossAtConfiguredSpawn(args[1]) == null
                    ? "§cBoss-Typ unbekannt, Spawnpunkt fehlt oder dieser Boss ist bereits aktiv. §7Nutze /adminboss set " + args[1].toLowerCase(Locale.ROOT)
                    : "§aBoss am gespeicherten Spawnpunkt gespawnt.");
        }
        if ((sub.equals("spawnhere") || sub.equals("spawn_here")) && sender instanceof Player player && args.length >= 2) {
            if (service.isKnownBoss(args[1]) && !service.isValidBossLocation(player.getLocation())) {
                return message(sender, "§cBosse können nur außerhalb des Spawn-Bereichs in der Warzone gespawnt werden.");
            }
            return message(sender, service.spawnBoss(args[1], player.getLocation()) == null
                    ? "§cBoss-Typ unbekannt oder dieser Boss ist bereits aktiv. §7Nutze ravager, inferno oder reaper."
                    : "§aBoss hier gespawnt.");
        }
        if (sub.equals("remove") && args.length >= 2) {
            if (args[1].equalsIgnoreCase("all")) {
                return message(sender, "§a" + service.removeAllBosses() + " Boss(e) entfernt.");
            }
            if (args[1].equalsIgnoreCase("nearest") && sender instanceof Player player) {
                return message(sender, service.removeNearestBoss(player.getLocation())
                        ? "§aNächster Boss entfernt."
                        : "§cKein Boss in deiner Welt aktiv.");
            }
            if (service.isKnownBoss(args[1])) {
                int removed = service.removeBosses(args[1]);
                return message(sender, removed > 0
                        ? "§a" + removed + " " + args[1].toLowerCase(Locale.ROOT) + "-Boss(e) entfernt."
                        : "§cKein aktiver Boss dieses Typs.");
            }
            try { return message(sender, service.removeBoss(UUID.fromString(args[1])) ? "§aBoss entfernt." : "§cBoss nicht gefunden."); }
            catch (IllegalArgumentException ignored) { return message(sender, "§cUngültige Boss-UUID."); }
        }
        return message(sender, "§7/" + label + " <set|spawn|spawnhere> <ravager|inferno|reaper> §8| §7list §8| §7remove <uuid|nearest|all|type>");
    }

    private boolean message(CommandSender sender, String text) { sender.sendMessage("§4Boss §8» " + text); return true; }
    private Component legacy(String text) { return LegacyComponentSerializer.legacySection().deserialize(text); }
    private boolean hasAccess(CommandSender sender) { return sender.isOp() || sender.hasPermission("vetroforge.boss.admin"); }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (!hasAccess(sender)) return List.of();
        if (args.length == 1) return filter(List.of("set", "spawn", "spawnhere", "list", "remove"), args[0]);
        if (args.length == 2 && (args[0].equalsIgnoreCase("set") || args[0].equalsIgnoreCase("spawn") || args[0].equalsIgnoreCase("spawnhere"))) {
            return filter(List.of("ravager", "inferno", "reaper"), args[1]);
        }
        if (args.length == 2 && args[0].equalsIgnoreCase("remove")) {
            List<String> values = new java.util.ArrayList<>(List.of("nearest", "all", "ravager", "inferno", "reaper"));
            values.addAll(service.activeBosses().stream().map(entity -> entity.getUniqueId().toString()).toList());
            return filter(values, args[1]);
        }
        return List.of();
    }

    private List<String> filter(List<String> values, String input) {
        String prefix = input.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(prefix)).toList();
    }
}
