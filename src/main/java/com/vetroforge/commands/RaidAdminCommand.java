// src/main/java/com/vetroforge/commands/RaidAdminCommand.java
package com.vetroforge.commands;

import com.vetroforge.util.UiDecor;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.factions.FactionManager;
import com.vetroforge.raid.RaidManager;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class RaidAdminCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final RaidManager rm;
    private final FactionManager fm;

    public RaidAdminCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.rm = plugin.getRaidManager();
        this.fm = plugin.getFactionManager();
    }

    private String p(String msg) {
        return "§6Raid §8» §f" + msg;
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {

        if (!sender.hasPermission("vetroforge.raid.admin")) {
            sender.sendMessage(p(UiDecor.noPermission()));
            return true;
        }

        if (args.length == 0) {
            sendHelp(sender, label);
            return true;
        }

        String sub = args[0].toLowerCase();

        switch (sub) {

            // ================= START =================
            case "start" -> {
                if (args.length < 5) {
                    sender.sendMessage(p("§c/" + label + " start <attacker> <target> <sec> <goal>"));
                    return true;
                }

                String atk = args[1];
                String tgt = args[2];

                if (atk.equalsIgnoreCase(tgt)) {
                    sender.sendMessage(p("§cGleiche Faction geht nicht."));
                    return true;
                }

                if (!exists(atk) || !exists(tgt)) {
                    sender.sendMessage(p("§cFaction existiert nicht."));
                    return true;
                }

                int sec, goal;
                try {
                    sec = Integer.parseInt(args[3]);
                    goal = Integer.parseInt(args[4]);
                } catch (Exception e) {
                    sender.sendMessage(p("§cUngültige Zahl."));
                    return true;
                }

                if (rm.startRaid(atk, tgt, sec, goal)) {
                    broadcast("§cRaid gestartet: §f" + atk + " §7→ §f" + tgt);
                } else {
                    sender.sendMessage(p("§cRaid konnte nicht gestartet werden."));
                }

                return true;
            }

            // ================= STOP =================
            case "stop" -> {
                if (args.length < 2) {
                    sender.sendMessage(p("§c/" + label + " stop <target>"));
                    return true;
                }

                String tgt = args[1];

                if (rm.getSessionByTarget(tgt) == null) {
                    sender.sendMessage(p("§cKein Raid aktiv."));
                    return true;
                }

                rm.stopRaid(tgt, false, "admin");

                broadcast("§eRaid gestoppt: §f" + tgt);

                return true;
            }

            // ================= ADD =================
            case "add" -> {
                if (args.length < 3) {
                    sender.sendMessage(p("§c/" + label + " add <player> <target>"));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(p("§cSpieler offline."));
                    return true;
                }

                String tgt = args[2];

                if (rm.getSessionByTarget(tgt) == null) {
                    sender.sendMessage(p("§cKein Raid aktiv."));
                    return true;
                }

                rm.addRaider(tgt, target.getUniqueId());

                target.sendMessage(p("§aDu wurdest zum Raid hinzugefügt."));
                target.playSound(target.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1);

                sender.sendMessage(p("§aHinzugefügt."));
                return true;
            }

            // ================= SPEC =================
            case "spec" -> {
                if (args.length < 3) {
                    sender.sendMessage(p("§c/" + label + " spec <player> <target>"));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(p("§cOffline."));
                    return true;
                }

                plugin.getRaidSpectatorService().enterSpectator(target, args[2]);

                target.sendMessage(p("§eDu bist jetzt Spectator."));
                target.playSound(target.getLocation(), Sound.ENTITY_ENDERMAN_TELEPORT, 1, 1);

                return true;
            }

            // ================= UNSPEC =================
            case "unspec" -> {
                if (args.length < 2) {
                    sender.sendMessage(p("§c/" + label + " unspec <player>"));
                    return true;
                }

                Player target = Bukkit.getPlayer(args[1]);
                if (target == null) {
                    sender.sendMessage(p("§cOffline."));
                    return true;
                }

                plugin.getRaidSpectatorService().exitSpectator(target);

                target.sendMessage(p("§aSpectator beendet."));
                return true;
            }

            // ================= STATUS =================
            case "status" -> {
                if (args.length < 2) {
                    sender.sendMessage(p("§c/" + label + " status <target>"));
                    return true;
                }

                var s = rm.getSessionByTarget(args[1]);

                if (s == null) {
                    sender.sendMessage(p("§cKein aktiver Raid."));
                    return true;
                }

                sender.sendMessage(p("§7Ziel: §f" + s.targetFaction));
                sender.sendMessage(p("§7Zeit: §f" + s.secondsLeft + "s"));
                sender.sendMessage(p("§7Raider: §f" + s.raiders.size()));
                sender.sendMessage(p("§7Obsidian: §f" + s.obsidianBroken + "/" + s.obsidianGoal));

                return true;
            }

            // ================= ENERGY =================
            case "energy" -> {
                if (args.length < 4) {
                    sender.sendMessage(p("§c/" + label + " energy <give|set> <player> <amount>"));
                    return true;
                }
                Player target = Bukkit.getPlayer(args[2]);
                if (target == null) {
                    sender.sendMessage(p("§cSpieler offline."));
                    return true;
                }
                int amount;
                try {
                    amount = Integer.parseInt(args[3]);
                } catch (NumberFormatException ignored) {
                    sender.sendMessage(p("§cUngültige Zahl."));
                    return true;
                }
                if ("give".equalsIgnoreCase(args[1])) {
                    plugin.getEnergyManager().add(target.getUniqueId(), amount);
                } else if ("set".equalsIgnoreCase(args[1])) {
                    plugin.getEnergyManager().set(target.getUniqueId(), amount);
                } else {
                    sender.sendMessage(p("§cNutze give oder set."));
                    return true;
                }
                int current = plugin.getEnergyManager().get(target.getUniqueId());
                int max = plugin.getEnergyManager().getMax(target.getUniqueId());
                sender.sendMessage(p("§aEnergy aktualisiert: §e" + target.getName() + " §8• §f" + current + "/" + max));
                target.sendMessage(p("§7Neue Energy: §e" + current + "/" + max));
                return true;
            }

            // ================= TEST =================
            case "test", "simulate" -> {
                if (args.length < 3) {
                    sender.sendMessage(p("§c/" + label + " test <attacker> <target> [goal]"));
                    sender.sendMessage(p("§7Simuliert Start, Raider-Join, Obsidian, Fight-Phase und Reward."));
                    return true;
                }

                String attacker = args[1];
                String target = args[2];
                int goal = 3;
                if (args.length >= 4) {
                    try { goal = Math.max(1, Integer.parseInt(args[3])); }
                    catch (NumberFormatException ignored) {
                        sender.sendMessage(p("§cUngültiges Goal."));
                        return true;
                    }
                }

                if (attacker.equalsIgnoreCase(target)) {
                    sender.sendMessage(p("§cAttacker und Target dürfen nicht gleich sein."));
                    return true;
                }
                if (!exists(attacker) || !exists(target)) {
                    sender.sendMessage(p("§cFaction existiert nicht."));
                    return true;
                }
                if (rm.isFactionInRaid(attacker) || rm.isFactionInRaid(target)) {
                    sender.sendMessage(p("§cEine der Factions ist bereits in einem Raid."));
                    return true;
                }

                boolean started = rm.startRaid(attacker, target, 45, goal);
                if (!started) {
                    sender.sendMessage(p("§cTest-Raid konnte nicht gestartet werden."));
                    return true;
                }

                int joined = 0;
                for (UUID member : fm.getMembers(attacker)) {
                    Player online = Bukkit.getPlayer(member);
                    if (online == null || !online.isOnline()) continue;
                    rm.addRaider(target, member);
                    joined++;
                }

                for (int i = 0; i < goal; i++) {
                    rm.addObsidianBreak(target, sender instanceof Player player ? player.getUniqueId() : null);
                }

                sender.sendMessage(p("§aRaid-Test abgeschlossen."));
                sender.sendMessage(p("§7Attacker: §f" + attacker + " §8• §7Target: §f" + target));
                sender.sendMessage(p("§7Online-Raider hinzugefügt: §f" + joined));
                sender.sendMessage(p("§7Obsidian simuliert: §f" + goal + "/" + goal));
                sender.sendMessage(p("§7Reward/Ende wurde über den echten RaidManager ausgelöst."));
                return true;
            }
        }

        sendHelp(sender, label);
        return true;
    }

    // ================= HELP =================
    private void sendHelp(CommandSender s, String l) {
        s.sendMessage("§6=== Raid Admin ===");
        s.sendMessage("§e/" + l + " start <atk> <tgt> <sec> <goal>");
        s.sendMessage("§e/" + l + " stop <tgt>");
        s.sendMessage("§e/" + l + " add <player> <tgt>");
        s.sendMessage("§e/" + l + " spec <player> <tgt>");
        s.sendMessage("§e/" + l + " unspec <player>");
        s.sendMessage("§e/" + l + " status <tgt>");
        s.sendMessage("§e/" + l + " energy <give|set> <player> <amount>");
        s.sendMessage("§e/" + l + " test <attacker> <target> [goal]");
    }

    // ================= UTIL =================
    private boolean exists(String f) {
        return fm.getAllFactionNames().contains(f);
    }

    private void broadcast(String msg) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            player.sendMessage(p(msg));
            player.playSound(player.getLocation(), Sound.ENTITY_WITHER_SPAWN, 0.4f, 1.2f);
        }
    }

    // ================= TAB =================
    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String l, String[] a) {

        List<String> f = fm.getAllFactionNames();

        if (a.length == 1) return List.of("start","stop","add","spec","unspec","status","energy","test","simulate");

        if (a.length == 2 && a[0].equalsIgnoreCase("energy")) return List.of("give", "set");

        if (a.length == 3 && a[0].equalsIgnoreCase("energy")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }

        if (a.length == 2) {
            if (a[0].equalsIgnoreCase("start") || a[0].equalsIgnoreCase("stop") || a[0].equalsIgnoreCase("status")
                    || a[0].equalsIgnoreCase("test") || a[0].equalsIgnoreCase("simulate"))
                return f;
        }

        if (a.length == 3 && (a[0].equalsIgnoreCase("start") || a[0].equalsIgnoreCase("add") || a[0].equalsIgnoreCase("spec")
                || a[0].equalsIgnoreCase("test") || a[0].equalsIgnoreCase("simulate")))
            return f;

        if (a.length == 2 && (a[0].equalsIgnoreCase("add") || a[0].equalsIgnoreCase("spec") || a[0].equalsIgnoreCase("unspec"))) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            return names;
        }

        return List.of();
    }
}
