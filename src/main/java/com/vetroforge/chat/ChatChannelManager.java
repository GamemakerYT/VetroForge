package com.vetroforge.chat;

import com.vetroforge.FactionsPlugin;
import org.bukkit.entity.Player;

import java.util.*;

public class ChatChannelManager {
    public enum Channel { GLOBAL, LOBBY, FACTION }

    private final FactionsPlugin plugin;
    private final Map<UUID, Channel> current = new HashMap<>();

    public ChatChannelManager(FactionsPlugin plugin){ this.plugin = plugin; }

    public Channel get(Player p){
        return current.getOrDefault(p.getUniqueId(), Channel.GLOBAL);
    }
    public void set(Player p, Channel ch){
        current.put(p.getUniqueId(), ch);
    }

    public Collection<Player> recipients(Player sender, Channel ch){
        switch (ch){
            case GLOBAL: return sender.getWorld().getPlayers(); // oder Bukkit.getOnlinePlayers()
            case LOBBY:
                // Lobby-Welt aus config
                String lobbyWorld = plugin.getConfig().getString("lobby.world","world");
                return plugin.getServer().getWorld(lobbyWorld) != null
                        ? plugin.getServer().getWorld(lobbyWorld).getPlayers()
                        : List.of(sender);
            case FACTION:
                var fm = plugin.getFactionManager();
                if (fm == null) return List.of(sender);
                String fac = fm.getFactionOf(sender.getUniqueId());
                if (fac == null) return List.of(sender);
                List<Player> out = new ArrayList<>();
                for (Player on : plugin.getServer().getOnlinePlayers()){
                    String f2 = fm.getFactionOf(on.getUniqueId());
                    if (fac.equalsIgnoreCase(f2)) out.add(on);
                }
                return out;
        }
        return List.of(sender);
    }
}
