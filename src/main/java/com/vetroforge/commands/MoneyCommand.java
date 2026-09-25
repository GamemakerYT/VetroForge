package com.vetroforge.commands;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.economy.EconomyManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class MoneyCommand implements CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;
    private final EconomyManager eco;

    private static final class Pending {
        final UUID target;
        final double amount;
        final long createdAt;
        Pending(UUID t, double a){ this.target=t; this.amount=a; this.createdAt=System.currentTimeMillis(); }
    }
    private final Map<UUID, Pending> pending = new ConcurrentHashMap<UUID, Pending>();

    public MoneyCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.eco = plugin.getEconomyManager();
    }

    private String c(String s){ return ChatColor.translateAlternateColorCodes('&', s); }

    @Override
    public boolean onCommand(CommandSender sender, Command cmd, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage(ChatColor.RED + "Nur Spieler.");
            return true;
        }
        Player p = (Player) sender;

        // /money  -> eigenes Geld
        if (args.length == 0) {
            double bal = eco.getBalance(p.getUniqueId());
            p.sendMessage(c("&6Geld: &f" + String.format(java.util.Locale.US, "%.0f", bal)));
            return true;
        }

        // /money <Spieler> -> Geld von anderen sehen
        if (args.length == 1 && !"send".equalsIgnoreCase(args[0])) {
            OfflinePlayer op = Bukkit.getOfflinePlayer(args[0]);
            if (op == null || op.getUniqueId() == null) { p.sendMessage(c("&cSpieler nicht gefunden.")); return true; }
            double bal = eco.getBalance(op.getUniqueId());
            p.sendMessage(c("&6Geld von &f" + (op.getName()!=null?op.getName():op.getUniqueId()) + "&6: &f" + String.format(java.util.Locale.US, "%.0f", bal)));
            return true;
        }

        // /money send <Spieler> <Betrag> [confirm]
        if (args.length >= 3 && "send".equalsIgnoreCase(args[0])) {
            OfflinePlayer target = Bukkit.getOfflinePlayer(args[1]);
            if (target == null || target.getUniqueId() == null) { p.sendMessage(c("&cSpieler nicht gefunden.")); return true; }
            if (target.getUniqueId().equals(p.getUniqueId())) { p.sendMessage(c("&cDu kannst dir selbst kein Geld senden.")); return true; }

            double amount;
            try { amount = Double.parseDouble(args[2]); }
            catch (Exception ex) { p.sendMessage(c("&cBetrag ungültig.")); return true; }
            if (amount <= 0) { p.sendMessage(c("&cBetrag muss > 0 sein.")); return true; }

            // confirm?
            if (args.length >= 4 && "confirm".equalsIgnoreCase(args[3])) {
                Pending pend = pending.get(p.getUniqueId());
                if (pend == null || !pend.target.equals(target.getUniqueId()) || Math.abs(pend.amount - amount) > 0.0001D) {
                    p.sendMessage(c("&cKeine passende Bestätigung offen. Nutze zuerst: &e/"+label+" send "+args[1]+" "+args[2]));
                    return true;
                }
                if (System.currentTimeMillis() - pend.createdAt > 60_000L) {
                    pending.remove(p.getUniqueId());
                    p.sendMessage(c("&cBestätigung abgelaufen. Bitte erneut senden."));
                    return true;
                }

                double bal = eco.getBalance(p.getUniqueId());
                if (bal < amount) { p.sendMessage(c("&cNicht genug Geld.")); return true; }

                eco.withdraw(p.getUniqueId(), amount);
                eco.deposit(target.getUniqueId(), amount);
                pending.remove(p.getUniqueId());

                p.sendMessage(c("&aGesendet: &f" + String.format(java.util.Locale.US, "%.0f", amount) + " &7an &f" + (target.getName()!=null?target.getName():target.getUniqueId())));
                if (target.isOnline() && target.getPlayer()!=null) {
                    target.getPlayer().sendMessage(c("&aEmpfangen: &f" + String.format(java.util.Locale.US, "%.0f", amount) + " &7von &f" + p.getName()));
                }
                return true;
            }

            // create pending
            pending.put(p.getUniqueId(), new Pending(target.getUniqueId(), amount));
            p.sendMessage(c("&eBestätige mit: &f/"+label+" send "+(target.getName()!=null?target.getName():args[1])+" "+args[2]+" confirm"));
            return true;
        }

        // Hilfe
        p.sendMessage(c("&e/"+label+" &7- zeigt dein Geld"));
        p.sendMessage(c("&e/"+label+" <Spieler> &7- zeigt Geld von anderen"));
        p.sendMessage(c("&e/"+label+" send <Spieler> <Betrag> [confirm]"));
        return true;
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<String>();
        if (args.length == 1) {
            if ("send".startsWith(args[0].toLowerCase(java.util.Locale.ROOT))) out.add("send");
            // auch Playernamen vorschlagen
            for (Player op : Bukkit.getOnlinePlayers())
                if (op.getName().toLowerCase(java.util.Locale.ROOT).startsWith(args[0].toLowerCase(java.util.Locale.ROOT)))
                    out.add(op.getName());
        } else if (args.length == 2 && "send".equalsIgnoreCase(args[0])) {
            for (Player op : Bukkit.getOnlinePlayers())
                if (op.getName().toLowerCase(java.util.Locale.ROOT).startsWith(args[1].toLowerCase(java.util.Locale.ROOT)))
                    out.add(op.getName());
        } else if (args.length == 4 && "send".equalsIgnoreCase(args[0])) {
            if ("confirm".startsWith(args[3].toLowerCase(java.util.Locale.ROOT))) out.add("confirm");
        }
        return out;
    }
}