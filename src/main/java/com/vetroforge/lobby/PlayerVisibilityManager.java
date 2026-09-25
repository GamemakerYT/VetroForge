package com.vetroforge.lobby;

import com.vetroforge.FactionsPlugin;
import org.bukkit.entity.Player;

import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

public class PlayerVisibilityManager {
    private final FactionsPlugin plugin;
    private final Set<UUID> hidden = new HashSet<>();

    public PlayerVisibilityManager(FactionsPlugin plugin){ this.plugin = plugin; }

    public boolean isHidden(Player p){ return hidden.contains(p.getUniqueId()); }

    public void setHidden(Player viewer, boolean hide){
        if (hide) hidden.add(viewer.getUniqueId()); else hidden.remove(viewer.getUniqueId());
        for (Player other : plugin.getServer().getOnlinePlayers()){
            if (viewer.equals(other)) continue;
            if (hide) viewer.hidePlayer(plugin, other); else viewer.showPlayer(plugin, other);
        }
    }

    public void refreshFor(Player viewer){
        boolean hide = isHidden(viewer);
        setHidden(viewer, hide);
    }
}