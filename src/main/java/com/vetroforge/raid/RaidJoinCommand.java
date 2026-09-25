package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import org.bukkit.command.*;
import org.bukkit.entity.Player;

public class RaidJoinCommand implements CommandExecutor {

    private final FactionsPlugin plugin;

    public RaidJoinCommand(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {

        if (!(s instanceof Player p)) return true;

        plugin.getRaidJoinService().joinRaid(p, args.length == 0 ? null : args[0]);
        return true;
    }
}
