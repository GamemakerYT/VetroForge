package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.entity.Player;

/**
 * Verteilt Resource Packs an Spieler.
 */
public class ResourcePackManager {
    private final String packUrl;
    private final String packHash;

    public ResourcePackManager(FactionsPlugin plugin) {
        this.packUrl  = plugin.getConfig().getString("resource.packUrl");
        this.packHash = plugin.getConfig().getString("resource.packHash");
    }

    public void applyTo(Player p) {
        p.setResourcePack(packUrl, packHash);
    }
}