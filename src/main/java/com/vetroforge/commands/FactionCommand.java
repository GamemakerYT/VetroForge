// src/main/java/com/vetroforge/commands/FactionCommand.java
package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.factions.FactionManager;
import com.vetroforge.factions.FactionManager.Role;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.Location;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;

public class FactionCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final FactionManager fm;

    // Einfache Invite-Map: target -> factionName
    private final Map<UUID, String> invites = new HashMap<>();

    public FactionCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.fm = plugin.getFactionManager();
    }

    private String tr(Player player, String key, Object... values) {
        return VetroForgeUtil.tr(plugin, player, "faction.command." + key, VetroForgeUtil.vars(values));
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player p)) {
            sender.sendMessage("Nur Ingame.");
            return true;
        }

        if (args.length == 0) {
            // 1) Neues Menü
            if (plugin.getFactionMenuManager() != null) {
                plugin.getFactionMenuManager().openMain(p);
                return true;
            }
            return true;
        }

        String sub = args[0].toLowerCase(Locale.ROOT);

        if (sub.equals("banner")) {
            if (args.length >= 2 && args[1].equalsIgnoreCase("symbol")) {
                String faction = fm.getFactionOf(p.getUniqueId());
                if (faction == null) { p.sendMessage(tr(p, "no_faction")); return true; }
                if (!fm.isLeader(p.getUniqueId())) { p.sendMessage("§cNur der Faction-Leader darf das Banner-Zeichen ändern."); return true; }
                if (args.length < 3 || !plugin.getFactionBannerManager().setSymbol(faction, args[2])) {
                    p.sendMessage("§cNutze: /f banner symbol <A-Z|0-9|none>");
                    return true;
                }
                p.sendMessage("§6Faction Banner §8» §aZeichen gespeichert: §f" + args[2].toUpperCase(Locale.ROOT));
                return true;
            }
            plugin.getFactionBannerGui().open(p);
            return true;
        }

        if (sub.equals("inbox")) {
            plugin.getFactionInboxService().show(p, args.length >= 2 && args[1].equalsIgnoreCase("clear"));
            return true;
        }

        if (sub.equals("upgrades") || sub.equals("elo")) {
            plugin.getFactionUpgradeManager().open(p);
            return true;
        }

        // ===== CREATE =====
        if (sub.equals("create")) {
            if (args.length < 2) { p.sendMessage(tr(p, "usage.create", "label", label)); return true; }
            String current = fm.getFactionOf(p.getUniqueId());
            if (current != null) { p.sendMessage(tr(p, "already_member", "faction", current)); return true; }
            if (plugin.getWarzoneRegionManager() == null || !plugin.getWarzoneRegionManager().isLobbySpawnArea(p.getLocation())) {
                p.sendMessage("§cDu kannst eine Faction nur im Spawn erstellen.");
                return true;
            }
            int requiredLevel = plugin.getConfig().getInt("factions.create.requiredLevel", 4);
            int level = plugin.getStatsManager() == null ? 0 : plugin.getStatsManager().getLevel(p.getUniqueId());
            if (level < requiredLevel) {
                p.sendMessage("§cDu brauchst Level " + requiredLevel + " für eine eigene Faction. §7Aktuell: §f" + level);
                return true;
            }
            int cost = plugin.getConfig().getInt("factions.create.costCoins", 1000);
            int coins = plugin.getCoinsManager() == null ? 0 : plugin.getCoinsManager().getCoins(p.getUniqueId());
            if (coins < 0) {
                p.sendMessage("§eDeine Coins werden noch geladen. Bitte versuch es gleich nochmal.");
                return true;
            }
            if (coins < cost) {
                p.sendMessage("§cDu brauchst " + cost + " Coins für eine eigene Faction. §7Aktuell: §f" + coins);
                return true;
            }
            String name = args[1];
            if (!name.matches("[A-Za-z0-9_]{2,16}")) {
                p.sendMessage(tr(p, "invalid_name"));
                return true;
            }
            boolean ok = fm.createFaction(name, p.getUniqueId());
            if (!ok) { p.sendMessage(tr(p, "name_taken")); return true; }
            if (plugin.getCoinsManager() != null && cost > 0) {
                plugin.getCoinsManager().removeCoins(p.getUniqueId(), cost);
            }

            p.sendMessage(tr(p, "created", "faction", name));
            p.sendMessage("§6Faction §8» §7" + cost + " Coins bezahlt. Teleport startet in §f6 Sekunden§7.");
            saveFactions();

            // Welt-Animation + Teleport
            plugin.getWorldManager().createFactionWorldWithAnimation(p, name, () -> {
                fm.disband(name);
                if (plugin.getCoinsManager() != null && cost > 0) {
                    plugin.getCoinsManager().addCoins(p.getUniqueId(), cost);
                }
                saveFactions();
                p.sendMessage("§cFaction-Erstellung fehlgeschlagen. §7Coins wurden zurückerstattet.");
            });
            return true;
        }

        // ===== DISBAND =====
        if (sub.equals("disband")) {
            String f = fm.getFactionOf(p.getUniqueId());
            if (f == null) { p.sendMessage(tr(p, "no_faction")); return true; }
            if (!fm.isLeader(p.getUniqueId()) && !p.hasPermission("vetroforge.factions.disband")) {
                p.sendMessage(tr(p, "leader_or_admin"));
                return true;
            }
            if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                p.sendMessage(tr(p, "disband_confirm", "label", label));
                return true;
            }
            boolean ok = fm.disband(f);
            if (ok) {
                saveFactions();
                plugin.getWorldManager().archiveAndDeleteFactionWorld(f);
            }
            p.sendMessage(ok ? tr(p, "disbanded", "faction", f) : tr(p, "disband_failed"));
            return true;
        }

        // ===== INFO =====
        if (sub.equals("info")) {
            String name = args.length >= 2 ? args[1] : fm.getFactionOf(p.getUniqueId());
            if (name == null) { p.sendMessage(tr(p, "not_found")); return true; }
            List<UUID> members = fm.getMembers(name);
            int online = 0;
            for (UUID u : members) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                if (op.isOnline()) online++;
            }
            p.sendMessage(tr(p, "info.name", "faction", fm.getDisplayName(name)));
            p.sendMessage(tr(p, "info.members", "members", members.size(), "online", online));
            return true;
        }

        // ===== LIST / MEMBERS =====
        if (sub.equals("list") || sub.equals("members")) {
            String name = fm.getFactionOf(p.getUniqueId());
            if (name == null) { p.sendMessage(tr(p, "no_faction")); return true; }
            List<UUID> members = fm.getMembers(name);
            p.sendMessage(tr(p, "members.title", "faction", name));
            for (UUID u : members) {
                OfflinePlayer op = Bukkit.getOfflinePlayer(u);
                boolean on = op.isOnline();
                Role r = fm.getRole(u);
                String rn = tr(p, r == Role.LEADER ? "role.leader" : r == Role.OFFICER ? "role.officer" : "role.member");
                p.sendMessage((on ? "§a● " : "§c● ") + rn + " §8• §f" + (op.getName() != null ? op.getName() : u));
            }
            return true;
        }

        // ===== INVITE =====
        if (sub.equals("invite")) {
            String f = fm.getFactionOf(p.getUniqueId());
            if (f == null) { p.sendMessage(tr(p, "no_faction")); return true; }
            Role r = fm.getRole(p.getUniqueId());
            if (r == Role.NONE) { p.sendMessage(tr(p, "no_permission")); return true; }
            if (args.length < 2) { p.sendMessage(tr(p, "usage.invite", "label", label)); return true; }
            Player target = Bukkit.getPlayer(args[1]);
            if (target == null) { p.sendMessage(tr(p, "player_offline")); return true; }
            invites.put(target.getUniqueId(), f);
            p.sendMessage(tr(p, "invite.sent", "player", target.getName()));
            target.sendMessage(tr(target, "invite.received", "faction", f, "label", label));
            return true;
        }

        // ===== ACCEPT =====
        if (sub.equals("accept")) {
            String f = invites.remove(p.getUniqueId());
            if (f == null) { p.sendMessage(tr(p, "invite.none")); return true; }
            boolean ok = fm.addMember(f, p.getUniqueId());
            if (ok) saveFactions();
            p.sendMessage(ok ? tr(p, "joined", "faction", f) : tr(p, "join_failed"));
            return true;
        }

        // ===== LEAVE =====
        if (sub.equals("leave")) {
            String faction = fm.getFactionOf(p.getUniqueId());
            boolean ok = fm.leave(p.getUniqueId());
            if (!ok) {
                p.sendMessage(tr(p, "leave_failed"));
            } else {
                plugin.getFactionInboxService().add(faction, "left", p.getName(), faction);
                saveFactions();
                p.sendMessage(tr(p, "left"));
            }
            return true;
        }

        // ===== KICK / PROMOTE / DEMOTE / SETLEADER =====
        if (sub.equals("kick") || sub.equals("promote") || sub.equals("demote") || sub.equals("setleader")) {
            if (args.length < 2) { p.sendMessage(tr(p, "usage.player", "label", label, "sub", sub)); return true; }
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
            if (op == null || op.getUniqueId() == null) { p.sendMessage(tr(p, "player_not_found")); return true; }
            String playerName = op.getName() != null ? op.getName() : op.getUniqueId().toString();

            boolean ok;
            switch (sub) {
                case "kick":
                    ok = fm.kick(p.getUniqueId(), op.getUniqueId());
                    if (ok) plugin.getFactionInboxService().add(
                            fm.getFactionOf(p.getUniqueId()), "kicked", playerName, p.getName());
                    if (ok) saveFactions();
                    p.sendMessage(ok ? tr(p, "kick.success", "player", playerName) : tr(p, "kick.failed"));
                    break;
                case "promote":
                    ok = fm.promote(p.getUniqueId(), op.getUniqueId());
                    if (ok) saveFactions();
                    p.sendMessage(ok ? tr(p, "promote.success", "player", playerName) : tr(p, "promote.failed"));
                    break;
                case "demote":
                    ok = fm.demote(p.getUniqueId(), op.getUniqueId());
                    if (ok) saveFactions();
                    p.sendMessage(ok ? tr(p, "demote.success", "player", playerName) : tr(p, "demote.failed"));
                    break;
                default: // setleader
                    ok = fm.setLeader(p.getUniqueId(), op.getUniqueId());
                    if (ok) saveFactions();
                    p.sendMessage(ok ? tr(p, "leader.success", "player", playerName) : tr(p, "leader.failed"));
                    break;
            }
            return true;
        }

        // ===== ALLIES =====
        if (sub.equals("allies")) {
            String f = fm.getFactionOf(p.getUniqueId());
            if (f == null) { p.sendMessage(tr(p, "no_faction")); return true; }
            Set<String> al = fm.getAllies(f);
            if (al.isEmpty()) { p.sendMessage(tr(p, "allies.none")); return true; }
            p.sendMessage(tr(p, "allies.list", "faction", f, "allies", String.join(", ", al)));
            return true;
        }

        if (sub.equals("ally")) {
            if (args.length < 3) { p.sendMessage(tr(p, "usage.ally", "label", label)); return true; }
            String mode = args[1].toLowerCase(Locale.ROOT);
            String my = fm.getFactionOf(p.getUniqueId());
            if (my == null) { p.sendMessage(tr(p, "no_faction")); return true; }
            if (!fm.isLeader(p.getUniqueId())) { p.sendMessage(tr(p, "allies.leader_only")); return true; }
            String other = args[2];
            if (my.equalsIgnoreCase(other)) { p.sendMessage(tr(p, "allies.self")); return true; }
            if (!fm.getAllFactionNames().contains(other)) { p.sendMessage(tr(p, "not_found")); return true; }

            if (mode.equals("add")) {
                fm.setAllied(my, other, true);
                saveFactions();
                p.sendMessage(tr(p, "allies.added", "faction", other));
            } else if (mode.equals("remove")) {
                fm.setAllied(my, other, false);
                saveFactions();
                p.sendMessage(tr(p, "allies.removed", "faction", other));
            } else {
                p.sendMessage(tr(p, "usage.ally", "label", label));
            }
            return true;
        }

        // ===== HOME =====
        if (sub.equals("sethome")) {
            String f = fm.getFactionOf(p.getUniqueId());
            if (f == null) { p.sendMessage(tr(p, "no_faction")); return true; }
            Role r = fm.getRole(p.getUniqueId());
            if (r != Role.LEADER && r != Role.OFFICER) { p.sendMessage(tr(p, "home.officer_only")); return true; }
            String expectedWorld = plugin.getWorldManager().worldNameForFaction(f);
            if (!p.getWorld().getName().equals(expectedWorld)) { p.sendMessage(tr(p, "home.wrong_world")); return true; }
            fm.setHome(f, p.getLocation());
            saveFactions();
            p.sendMessage(tr(p, "home.set"));
            return true;
        }

        if (sub.equals("home")) {
            String f = fm.getFactionOf(p.getUniqueId());
            if (f == null) { p.sendMessage(tr(p, "no_faction")); return true; }
            Location l = fm.getHome(f);
            if (l == null) { p.sendMessage(tr(p, "home.missing")); return true; }
            p.teleport(l);
            p.sendMessage(tr(p, "home.teleported"));
            return true;
        }

        // ===== REWIND (Faction-Rewind: zerstoerte Mauer nach einem Raid zurueckkaufen) =====
        if (sub.equals("rewind")) {
            String f = fm.getFactionOf(p.getUniqueId());
            if (f == null) { p.sendMessage(tr(p, "no_faction")); return true; }
            if (!fm.isLeader(p.getUniqueId())) { p.sendMessage("§cNur der Faction-Leader kann die Mauer wiederherstellen."); return true; }
            if (plugin.getRaidManager() == null || !plugin.getRaidManager().hasRewindOffer(f)) {
                p.sendMessage("§cAktuell nichts zum Wiederherstellen. Rewind ist nur kurz nach einem Raid verfuegbar.");
                return true;
            }
            int blocks = plugin.getRaidManager().rewindBlockCount(f);
            int cost = plugin.getRaidManager().rewindCost(f);
            if (args.length < 2 || !args[1].equalsIgnoreCase("confirm")) {
                p.sendMessage("§6Rewind §8» §7" + blocks + " zerstoerte Bloecke koennen wiederhergestellt werden.");
                p.sendMessage("§6Rewind §8» §7Kosten: §e" + cost + " Coins§7. Bestaetige mit §f/f rewind confirm");
                p.sendMessage("§8Truheninhalte werden dabei NICHT wiederhergestellt.");
                return true;
            }
            int coins = plugin.getCoinsManager() == null ? 0 : plugin.getCoinsManager().getCoins(p.getUniqueId());
            if (coins < cost) {
                p.sendMessage("§cDu brauchst " + cost + " Coins fuer den Rewind. §7Aktuell: §f" + coins);
                return true;
            }
            plugin.getCoinsManager().removeCoins(p.getUniqueId(), cost);
            plugin.getRaidManager().performRewind(f);
            p.sendMessage("§aDie Mauer wurde wiederhergestellt. §7(" + blocks + " Bloecke, " + cost + " Coins)");
            return true;
        }

        // Fallback
        p.sendMessage(tr(p, "unknown"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String k : new String[]{
                    "create","disband","info","list","invite","accept","leave",
                    "kick","promote","demote","setleader","allies","ally","sethome","home","banner","upgrades","elo","inbox"
            }) if (k.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(k);
            return out;
        }

        if (args.length == 2 && "ally".equalsIgnoreCase(args[0])) {
            for (String k : new String[]{"add","remove"})
                if (k.startsWith(args[1].toLowerCase(Locale.ROOT))) out.add(k);
            return out;
        }

        if (args.length == 2 && "disband".equalsIgnoreCase(args[0])) {
            if ("confirm".startsWith(args[1].toLowerCase(Locale.ROOT))) out.add("confirm");
            return out;
        }

        if (args.length == 3 && "ally".equalsIgnoreCase(args[0])) {
            String prefix = args[2].toLowerCase(Locale.ROOT);
            for (String name : fm.getAllFactionNames()) {
                if (name.toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(name);
            }
            return out;
        }

        if (args.length == 2 && Arrays.asList("invite","kick","promote","demote","setleader").contains(args[0].toLowerCase(Locale.ROOT))) {
            String prefix = args[1].toLowerCase(Locale.ROOT);
            for (Player op : Bukkit.getOnlinePlayers()) {
                if (op.getName().toLowerCase(Locale.ROOT).startsWith(prefix)) out.add(op.getName());
            }
            return out;
        }

        return out;
    }

    private void saveFactions() {
        try {
            if (plugin.getPersistenceManager() != null) {
                plugin.getPersistenceManager().saveAll();
            }
        } catch (Throwable ignored) {}
    }
}
