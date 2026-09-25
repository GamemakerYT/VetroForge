package com.vetroforge.raid.scout;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.raid.RaidText;
import org.bukkit.*;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

import java.util.*;

public class ScoutingService {

    private final FactionsPlugin plugin;
    private final Map<UUID, Backup> backs = new HashMap<>();

    private static final class Backup {
        final GameMode gm; final ItemStack[] inv; final ItemStack[] armor; final ItemStack off;
        final Location loc;
        Backup(Player p) {
            this.gm = p.getGameMode();
            this.inv = p.getInventory().getContents();
            this.armor = p.getInventory().getArmorContents();
            this.off = p.getInventory().getItemInOffHand();
            this.loc = p.getLocation().clone();
        }
    }

    public ScoutingService(FactionsPlugin plugin){ this.plugin = plugin; }

    public void start(Player p, Location lookAt, int seconds) {
        if (p == null || lookAt == null) return;
        if (backs.containsKey(p.getUniqueId())) { RaidText.send(plugin, p, "message.already_scouting"); return; }

        backs.put(p.getUniqueId(), new Backup(p));

        p.getInventory().clear();
        p.setGameMode(GameMode.SPECTATOR);
        p.teleport(lookAt.clone().add(0, 10, 0));
        p.sendTitle(RaidText.tr(plugin, p, "title.scouting"), RaidText.tr(plugin, p, "title.seconds", "seconds", seconds), 5, 30, 5);

        Bukkit.getScheduler().runTaskLater(plugin, () -> stop(p), seconds * 20L);
    }

    public void stop(Player p) {
        Backup b = backs.remove(p.getUniqueId());
        if (b == null) return;
        p.setGameMode(b.gm);
        p.getInventory().setContents(b.inv);
        p.getInventory().setArmorContents(b.armor);
        p.getInventory().setItemInOffHand(b.off);
        if (b.loc.getWorld()!=null) p.teleport(b.loc);
        RaidText.send(plugin, p, "message.scouting_stopped");
    }
}
