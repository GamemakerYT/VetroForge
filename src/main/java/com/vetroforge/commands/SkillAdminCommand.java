package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.manager.SkillManager;
import com.vetroforge.skills.SkillType;
import com.vetroforge.skills.data.SkillState;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.stream.Collectors;

public class SkillAdminCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public SkillAdminCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    private boolean hasPerm(CommandSender s) {
        return s.hasPermission("vetroforge.skilladmin") || s.hasPermission("vetroforge.admin");
    }

    private String c(String s) {
        return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] a) {
        if (!hasPerm(sender)) {
            sender.sendMessage(ChatColor.RED + "Keine Rechte.");
            return true;
        }
        if (a.length == 0) {
            return usage(sender, label, null);
        }

        String sub = a[0].toLowerCase(Locale.ROOT);
        switch (sub) {
            case "addsp":
            case "removesp":
            case "setsp":
            case "resetsp":
            case "addap":
            case "removeap":
            case "setap":
            case "resetap": {
                // /skilladmin <sub> <player> <skill> [amount]
                if (a.length < 3) return usage(sender, label, sub + " <player> <skill> " + (sub.startsWith("reset") ? "" : "<amount>"));

                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "Spieler muss online sein.");
                    return true;
                }

                SkillType type;
                try { type = SkillType.valueOf(a[2].toUpperCase(Locale.ROOT)); }
                catch (Exception ex) { sender.sendMessage(ChatColor.RED + "Unbekanntes Skill: " + a[2]); return true; }

                Integer amount = null;
                if (!sub.startsWith("reset")) {
                    if (a.length < 4) { sender.sendMessage(ChatColor.RED + "Menge fehlt."); return true; }
                    try { amount = Integer.parseInt(a[3]); }
                    catch (Exception e) { sender.sendMessage(ChatColor.RED + "Ungültige Zahl: " + a[3]); return true; }
                }

                apply(sender, target, type, sub, amount == null ? 0 : amount);
                return true;
            }

            case "resetall": {
                // /skilladmin resetall <player>
                if (a.length < 2) return usage(sender, label, "resetall <player>");
                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "Spieler muss online sein.");
                    return true;
                }
                resetAllPoints(target);
                sender.sendMessage(c("&aAlle Skill-/Ability-Punkte von &f" + target.getName() + " &awurden auf &f0 &agesetzt."));
                return true;
            }

            case "addxp": {
                // /skilladmin addxp <player> <skill> <amount>
                if (a.length < 4) return usage(sender, label, "addxp <player> <skill> <amount>");
                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "Spieler muss online sein.");
                    return true;
                }
                SkillType type = parseSkill(sender, a[2]);
                if (type == null) return true;
                int amount = parseAmount(sender, a[3], 1, Integer.MAX_VALUE);
                if (amount < 0) return true;
                plugin.getSkillManager().addXp(target, type, amount);
                sender.sendMessage(c("&a+" + amount + " XP &7für &e" + type.name() + " &7→ &f" + target.getName()));
                return true;
            }

            case "setlevel": {
                // /skilladmin setlevel <player> <skill> <level>
                if (a.length < 4) return usage(sender, label, "setlevel <player> <skill> <level>");
                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "Spieler muss online sein.");
                    return true;
                }
                SkillType type = parseSkill(sender, a[2]);
                if (type == null) return true;
                var def = plugin.getSkillManager().def(type);
                int max = def == null ? 50 : def.levelCap;
                int level = parseAmount(sender, a[3], 1, max);
                if (level < 0) return true;
                SkillState state = plugin.getSkillManager().getState(target, type);
                state.level = level;
                state.xp = 0;
                plugin.getSkillManager().save();
                sender.sendMessage(c("&eLevel gesetzt: &f" + target.getName() + " &7→ &e" + type.name() + " &f" + level));
                target.sendMessage(c("&6Skill &8» &7Dein &e" + type.name() + "&7-Level wurde auf &f" + level + " &7gesetzt."));
                return true;
            }

            case "reset": {
                // /skilladmin reset <player> <skill>
                if (a.length < 3) return usage(sender, label, "reset <player> <skill>");
                Player target = Bukkit.getPlayerExact(a[1]);
                if (target == null || !target.isOnline()) {
                    sender.sendMessage(ChatColor.RED + "Spieler muss online sein.");
                    return true;
                }
                SkillType type = parseSkill(sender, a[2]);
                if (type == null) return true;
                resetSkill(target, type);
                sender.sendMessage(c("&aSkill zurückgesetzt: &f" + target.getName() + " &7→ &e" + type.name()));
                return true;
            }

            default:
                return usage(sender, label, null);
        }
    }

    private SkillType parseSkill(CommandSender sender, String raw) {
        try {
            return SkillType.valueOf(raw.toUpperCase(Locale.ROOT));
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Unbekanntes Skill: " + raw);
            return null;
        }
    }

    private int parseAmount(CommandSender sender, String raw, int min, int max) {
        try {
            int amount = Integer.parseInt(raw);
            if (amount < min || amount > max) {
                sender.sendMessage(ChatColor.RED + "Zahl muss zwischen " + min + " und " + max + " liegen.");
                return -1;
            }
            return amount;
        } catch (Exception ex) {
            sender.sendMessage(ChatColor.RED + "Ungültige Zahl: " + raw);
            return -1;
        }
    }

    private void apply(CommandSender sender, Player p, SkillType type, String sub, int amount) {
        SkillManager sm = plugin.getSkillManager();
        SkillState st = sm.getState(p, type);

        boolean isSP = sub.contains("sp");
        if (sub.startsWith("add")) {
            if (amount <= 0) { sender.sendMessage(ChatColor.RED + "Menge muss > 0 sein."); return; }
            if (isSP) st.skillPoints += amount; else st.abilityPoints += amount;
            sm.save();
            sender.sendMessage(c("&a+" + amount + " " + (isSP ? "Skill" : "Ability") + "-Punkte &7für &e" + type.name() + " &7→ &f" + p.getName()));
            p.sendMessage(c("&aDu hast &f+" + amount + " &a" + (isSP ? "Skill" : "Ability") + "-Punkte für &e" + type.name() + " &aerhalten."));
            return;
        }
        if (sub.startsWith("remove")) {
            if (amount <= 0) { sender.sendMessage(ChatColor.RED + "Menge muss > 0 sein."); return; }
            if (isSP) st.skillPoints = Math.max(0, st.skillPoints - amount);
            else st.abilityPoints = Math.max(0, st.abilityPoints - amount);
            sm.save();
            sender.sendMessage(c("&c-" + amount + " " + (isSP ? "Skill" : "Ability") + "-Punkte &7bei &e" + type.name() + " &7→ &f" + p.getName()));
            p.sendMessage(c("&cDir wurden &f" + amount + " &c" + (isSP ? "Skill" : "Ability") + "-Punkte für &e" + type.name() + " &cabgezogen."));
            return;
        }
        if (sub.startsWith("set")) {
            if (amount < 0) { sender.sendMessage(ChatColor.RED + "Menge darf nicht negativ sein."); return; }
            if (isSP) st.skillPoints = amount; else st.abilityPoints = amount;
            sm.save();
            sender.sendMessage(c("&eSet: &f" + (isSP ? "Skill" : "Ability") + "-Punkte &7(" + type.name() + ") von &f" + p.getName() + " &7→ &f" + amount));
            p.sendMessage(c("&eDeine " + (isSP ? "Skill" : "Ability") + "-Punkte für &e" + type.name() + " &ewurden auf &f" + amount + " &egesetzt."));
            return;
        }
        if (sub.startsWith("reset")) {
            if (isSP) st.skillPoints = 0; else st.abilityPoints = 0;
            sm.save();
            sender.sendMessage(c("&aReset: &f" + (isSP ? "Skill" : "Ability") + "-Punkte &7(" + type.name() + ") von &f" + p.getName() + " &7→ &f0"));
            p.sendMessage(c("&eDeine " + (isSP ? "Skill" : "Ability") + "-Punkte für &e" + type.name() + " &ewurden auf &f0 &ezurückgesetzt."));
        }
    }

    private void resetAllPoints(Player p) {
        SkillManager sm = plugin.getSkillManager();
        for (SkillType t : SkillType.values()) {
            SkillState st = sm.getState(p, t);
            st.skillPoints = 0;
            st.abilityPoints = 0;
        }
        sm.save();
    }

    private void resetSkill(Player player, SkillType type) {
        SkillState state = plugin.getSkillManager().getState(player, type);
        state.level = 1;
        state.stage = 0;
        state.xp = 0;
        state.skillPoints = 0;
        state.abilityPoints = 0;
        state.abilities.clear();
        plugin.getSkillManager().save();
        player.sendMessage(c("&6Skill &8» &e" + type.name() + " &7wurde zurückgesetzt."));
    }

    private boolean usage(CommandSender s, String lbl, String specific) {
        s.sendMessage(c("&6/"+lbl+" addsp &7<player> <skill> <amount> &8- &fSkill-Punkte hinzufügen"));
        s.sendMessage(c("&6/"+lbl+" removesp &7<player> <skill> <amount> &8- &fSkill-Punkte entfernen"));
        s.sendMessage(c("&6/"+lbl+" setsp &7<player> <skill> <amount> &8- &fSkill-Punkte setzen"));
        s.sendMessage(c("&6/"+lbl+" resetsp &7<player> <skill> &8- &fSkill-Punkte auf 0"));
        s.sendMessage(c("&6/"+lbl+" addap &7<player> <skill> <amount> &8- &fAbility-Punkte hinzufügen"));
        s.sendMessage(c("&6/"+lbl+" removeap &7<player> <skill> <amount> &8- &fAbility-Punkte entfernen"));
        s.sendMessage(c("&6/"+lbl+" setap &7<player> <skill> <amount> &8- &fAbility-Punkte setzen"));
        s.sendMessage(c("&6/"+lbl+" resetap &7<player> <skill> &8- &fAbility-Punkte auf 0"));
        s.sendMessage(c("&6/"+lbl+" addxp &7<player> <skill> <amount> &8- &fSkill-XP geben"));
        s.sendMessage(c("&6/"+lbl+" setlevel &7<player> <skill> <level> &8- &fSkill-Level setzen"));
        s.sendMessage(c("&6/"+lbl+" reset &7<player> <skill> &8- &fEin Skill komplett zurücksetzen"));
        s.sendMessage(c("&6/"+lbl+" resetall &7<player> &8- &fAlle Punkte (SP/AP) auf 0"));
        if (specific != null) s.sendMessage(c("&cNutze: &f/"+lbl+" "+specific));
        return true;
    }

    /* ================= Tab-Complete ================= */

    @Override
    public List<String> onTabComplete(CommandSender s, Command c, String alias, String[] A) {
        List<String> out = new ArrayList<>();
        if (!hasPerm(s)) return out;

        if (A.length == 1) {
            List<String> subs = Arrays.asList("addsp","removesp","setsp","resetsp","addap","removeap","setap","resetap","addxp","setlevel","reset","resetall");
            return subs.stream()
                    .filter(x -> x.startsWith(A[0].toLowerCase(Locale.ROOT)))
                    .collect(Collectors.toList());
        }
        if (A.length == 2) {
            // Spieler
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.getName().toLowerCase(Locale.ROOT).startsWith(A[1].toLowerCase(Locale.ROOT))) out.add(p.getName());
            }
            return out;
        }
        if (A.length == 3 && !A[0].equalsIgnoreCase("resetall")) {
            for (SkillType t : SkillType.values()) {
                String n = t.name().toLowerCase(Locale.ROOT);
                if (n.startsWith(A[2].toLowerCase(Locale.ROOT))) out.add(t.name());
            }
            return out;
        }
        if (A.length == 4 && !(A[0].equalsIgnoreCase("resetsp") || A[0].equalsIgnoreCase("resetap") || A[0].equalsIgnoreCase("reset") || A[0].equalsIgnoreCase("resetall"))) {
            return Arrays.asList("1","5","10","25","50","100").stream()
                    .filter(x -> x.startsWith(A[3]))
                    .collect(Collectors.toList());
        }
        return out;
    }
}
