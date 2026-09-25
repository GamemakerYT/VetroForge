package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.entity.Player;

import java.util.Map;

public final class RaidText {

    private RaidText() {
    }

    public static String tr(FactionsPlugin plugin, Player player, String key) {
        return VetroForgeUtil.tr(plugin, player, "raid." + key);
    }

    public static String tr(FactionsPlugin plugin, Player player, String key, Object... values) {
        Map<String, String> vars = VetroForgeUtil.vars(values);
        return VetroForgeUtil.tr(plugin, player, "raid." + key, vars);
    }

    public static void send(FactionsPlugin plugin, Player player, String key, Object... values) {
        if (player != null) player.sendMessage(tr(plugin, player, key, values));
    }
}
