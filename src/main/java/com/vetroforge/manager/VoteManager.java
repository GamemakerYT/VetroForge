/* src/main/java/com/vetroforge/manager/VoteManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import com.votifier.event.VotifierEvent;
import com.votifier.model.Vote;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;

/**
 * Belohnt Spieler bei Votifier-Votes.
 */
/*public class VoteManager implements Listener {
    private final ChestSpawnManager chestSpawnManager;

    public VoteManager(FactionsPlugin plugin) {
        this.chestSpawnManager = plugin.getChestSpawnManager();
        plugin.getServer().getPluginManager().registerEvents(this, plugin);
    }

    @EventHandler
    public void onVote(VotifierEvent event) {
        Vote v = event.getVote();
        Player p = Bukkit.getPlayerExact(v.getUsername());
        if (p != null && p.isOnline()) {
            p.sendMessage("§aDanke fürs Voten! Deine Belohnung wurde gespawnt.");
            chestSpawnManager.spawnChest(p.getLocation());
        }
    }
/

 */