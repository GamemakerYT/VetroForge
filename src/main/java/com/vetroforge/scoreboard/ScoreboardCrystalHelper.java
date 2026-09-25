package com.vetroforge.scoreboard;

import com.vetroforge.FactionsPlugin;
import org.bukkit.entity.Player;

import java.util.Locale;
import java.util.UUID;

public class ScoreboardCrystalHelper {

    private final FactionsPlugin plugin;

    public ScoreboardCrystalHelper(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public String factionCrystalsLine(Player p) {
        if (plugin.getRaidCrystalManager() == null) return "—";
        if (plugin.getRaidCrystalManager().useFactionSource() && factionOf(p.getUniqueId()) == null) return "—";

        int cur = plugin.getRaidCrystalManager().getAvailable(p);
        int cap = plugin.getRaidCrystalManager().getAvailableCap(p);

        String col = cur >= cap ? "§a" : (cur >= cap / 2 ? "§e" : "§c");
        return col + nf(cur) + "§7/§f" + nf(cap);
    }

    /** Persönliche Energy (aus demselben Manager: player-Depot). */
    public String playerCrystalsLine(Player p) {
        int v = plugin.getRaidCrystalManager().getForPlayer(p.getUniqueId());
        return nf(v);
    }

    /* ---------- helpers ---------- */
    public String energyLine(Player p) {
        int energy = plugin.getEnergyManager().get(p.getUniqueId());
        int cap = plugin.getEnergyManager().getMax(p.getUniqueId());
        return "§e" + energy + "§7/§f" + cap;
    }
    private String factionOf(UUID uuid) {
        try {
            var fm = plugin.getFactionManager();
            return (fm == null) ? null : fm.getFactionOf(uuid);
        } catch (Throwable ignored) {}
        return null;
    }

    private String nf(int x) { return String.format(Locale.US, "%,d", x); }
}
