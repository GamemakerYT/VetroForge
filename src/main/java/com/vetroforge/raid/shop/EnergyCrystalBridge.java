package com.vetroforge.raid.shop;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.raid.RaidCrystalManager;

import java.util.UUID;

/** Verknüpft Crystal-Voucher mit dem Raid-Crystal-Guthaben (RaidCrystalManager). */
public final class EnergyCrystalBridge {

    private final FactionsPlugin plugin;

    public enum Target { PLAYER, FACTION }

    public EnergyCrystalBridge(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    /* ================= Einzahlen ================= */

    public boolean depositToPlayer(UUID playerId, int amount) {
        RaidCrystalManager mgr = plugin.getRaidCrystalManager();
        if (mgr == null) return false;
        mgr.addForPlayer(playerId, amount);
        return true;
    }

    public boolean depositToFaction(String factionName, int amount) {
        if (factionName == null || factionName.isBlank()) return false;
        RaidCrystalManager mgr = plugin.getRaidCrystalManager();
        if (mgr == null) return false;
        mgr.addForFaction(factionName, amount);
        return true;
    }

    /* ================= Abfragen (nur fürs Feedback) ================= */

    public Integer getPlayerBalance(UUID playerId) {
        RaidCrystalManager mgr = plugin.getRaidCrystalManager();
        return mgr == null ? null : mgr.getPlayerCrystals(playerId);
    }

    public Integer getFactionBalance(String factionName) {
        RaidCrystalManager mgr = plugin.getRaidCrystalManager();
        return mgr == null ? null : mgr.getCrystals(factionName);
    }
}
