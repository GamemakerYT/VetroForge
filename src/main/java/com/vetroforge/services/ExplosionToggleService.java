package com.vetroforge.services;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.entity.ExplosionPrimeEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

public final class ExplosionToggleService implements Listener, CommandExecutor, TabCompleter {

    private final FactionsPlugin plugin;

    public ExplosionToggleService(FactionsPlugin plugin) {
        this.plugin = plugin;
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    public boolean enabled() {
        return plugin.getConfig().getBoolean("explosions.enabled", true);
    }

    @Override
    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!sender.hasPermission("vetroforge.explosions")) {
            sender.sendMessage("§cKeine Rechte.");
            return true;
        }
        if (args.length == 0 || args[0].equalsIgnoreCase("status")) {
            sender.sendMessage("§6Explosions §8» " + (enabled() ? "§aaktiv" : "§cdeaktiviert"));
            return true;
        }
        String mode = args[0].toLowerCase(Locale.ROOT);
        if (!mode.equals("on") && !mode.equals("off")) {
            sender.sendMessage("§cNutze: /" + label + " <on|off|status>");
            return true;
        }
        boolean value = mode.equals("on");
        plugin.getConfig().set("explosions.enabled", value);
        plugin.saveConfig();
        sender.sendMessage("§6Explosions §8» " + (value ? "§aaktiviert" : "§cdeaktiviert"));
        return true;
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onPrime(ExplosionPrimeEvent event) {
        if (!enabled()) event.setCancelled(true);
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onEntityExplode(EntityExplodeEvent event) {
        if (!enabled()) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @EventHandler(priority = EventPriority.LOWEST, ignoreCancelled = false)
    public void onBlockExplode(BlockExplodeEvent event) {
        if (!enabled()) {
            event.blockList().clear();
            event.setCancelled(true);
        }
    }

    @Override
    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            for (String option : List.of("on", "off", "status")) {
                if (option.startsWith(args[0].toLowerCase(Locale.ROOT))) out.add(option);
            }
        }
        return out;
    }
}
