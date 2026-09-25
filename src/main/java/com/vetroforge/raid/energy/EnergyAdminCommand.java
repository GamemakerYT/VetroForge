// src/main/java/com/vetroforge/raid/energy/EnergyAdminCommand.java
package com.vetroforge.raid.energy;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class EnergyAdminCommand implements CommandExecutor {

    private final FactionsPlugin plugin;

    public EnergyAdminCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    private String p(String msg) {
        return "§6Energy §8» §f" + msg;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {

        if (!s.hasPermission("vetro.energy.admin")) {
            s.sendMessage(p("§cKeine Rechte."));
            return true;
        }

        if (args.length < 3) {
            s.sendMessage(p("§c/" + label + " <give|set> <player> <amount>"));
            return true;
        }

        OfflinePlayer t = Bukkit.getOfflinePlayer(args[1]);
        if (!t.hasPlayedBefore() && !t.isOnline()) {
            s.sendMessage(p("§cSpieler wurde noch nie auf diesem Server gesehen."));
            return true;
        }

        int amount;
        try {
            amount = Integer.parseInt(args[2]);
        } catch (Exception e) {
            s.sendMessage(p("§cUngültige Zahl."));
            return true;
        }

        EnergyManager em = plugin.getEnergyManager();

        switch (args[0].toLowerCase()) {

            case "give" -> {
                em.add(t.getUniqueId(), amount);
                s.sendMessage(p("§a+" + amount + " Energy für §e" + t.getName()));
            }

            case "set" -> {
                em.set(t.getUniqueId(), amount);
                s.sendMessage(p("§eEnergy gesetzt: §f" + amount));
            }

            default -> {
                s.sendMessage(p("§cUse give/set."));
                return true;
            }
        }

        Player online = t.getPlayer();
        if (online != null) {
            online.sendMessage(p("§7Neue Energy: §e" + em.get(t.getUniqueId()) + "/" + em.getMax(t.getUniqueId())));
        }
        return true;
    }
}