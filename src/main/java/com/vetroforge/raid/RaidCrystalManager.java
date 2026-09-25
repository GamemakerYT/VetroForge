// src/main/java/com/vetroforge/raid/RaidCrystalManager.java
package com.vetroforge.raid;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.skills.SkillType;
import org.bukkit.Bukkit;
import org.bukkit.Sound;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class RaidCrystalManager {

    private final FactionsPlugin plugin;
    private final File file;
    private final Map<UUID, Integer> player = new HashMap<>();
    private final Map<String, Integer> faction = new HashMap<>();

    private boolean useFactionBank;

    public RaidCrystalManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "raid_crystals.yml");
        reload();
    }

    /* ================= LOAD / SAVE ================= */

    public synchronized void reload() {
        player.clear();
        faction.clear();

        YamlConfiguration y = YamlConfiguration.loadConfiguration(file);
        this.useFactionBank = plugin.getConfig().getBoolean("raid.crystals.useFactionBank", true);

        if (y.isConfigurationSection("players")) {
            for (String k : y.getConfigurationSection("players").getKeys(false)) {
                try {
                    player.put(UUID.fromString(k), y.getInt("players." + k, 0));
                } catch (Exception ignored) {}
            }
        }

        if (y.isConfigurationSection("factions")) {
            for (String id : y.getConfigurationSection("factions").getKeys(false)) {
                faction.put(id, y.getInt("factions." + id, 0));
            }
        }
    }

    public synchronized void save() {
        YamlConfiguration y = new YamlConfiguration();

        for (var e : player.entrySet()) {
            y.set("players." + e.getKey(), Math.max(0, e.getValue()));
        }

        for (var e : faction.entrySet()) {
            y.set("factions." + e.getKey(), Math.max(0, e.getValue()));
        }

        try { y.save(file); } catch (IOException ignored) {}
    }
    public int getForPlayer(UUID uuid) {
        return getPlayerCrystals(uuid);
    }


    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    /* ================= CONFIG ================= */

    public boolean useFactionSource() {
        return useFactionBank;
    }

    /**
     * Persoenliches Guthaben (z.B. per /crystals give aus dem Tebex-Webshop vergeben) zaehlt
     * IMMER mit, auch wenn useFactionBank aktiv ist -- sonst landen admin-/webshop-vergebene
     * Crystals in einem Bucket, das getAvailable/consume/refund sonst nie ansehen und der Spieler
     * sie nie ausgeben kann (siehe consume()).
     */
    public synchronized int getAvailable(Player player) {
        if (player == null) return 0;
        if (!useFactionBank) return getPlayerCrystals(player.getUniqueId());

        String factionId = factionOf(player);
        int factionAmt = factionId == null ? 0 : getCrystals(factionId);
        return factionAmt + getPlayerCrystals(player.getUniqueId());
    }

    public synchronized int getAvailableCap(Player player) {
        if (player == null) return getBaseCap();
        if (!useFactionBank) return getBaseCap();

        String factionId = factionOf(player);
        int cap = factionId == null ? getBaseCap() : getCrystalCap(factionId);
        return cap + getPlayerCrystals(player.getUniqueId());
    }

    /** Zieht zuerst vom persoenlichen Guthaben ab (z.B. Webshop-Kauf), Rest aus der Faction-Bank. */
    public synchronized boolean consume(Player player, int amount) {
        if (player == null) return false;
        if (!useFactionBank) return consumeForPlayer(player.getUniqueId(), amount);

        String factionId = factionOf(player);
        int personalAmt = getPlayerCrystals(player.getUniqueId());
        int factionAmt = factionId == null ? 0 : getCrystals(factionId);
        if (personalAmt + factionAmt < amount) return false;

        int fromPersonal = Math.min(amount, personalAmt);
        if (fromPersonal > 0) consumeForPlayer(player.getUniqueId(), fromPersonal);

        int remaining = amount - fromPersonal;
        if (remaining > 0) consumeForFaction(factionId, remaining);

        return true;
    }

    public void refund(Player player, int amount) {
        if (player == null || amount <= 0) return;
        if (!useFactionBank) {
            addForPlayer(player.getUniqueId(), amount);
            return;
        }

        String factionId = factionOf(player);
        if (factionId != null) addForFaction(factionId, amount);
    }

    /* ================= GET ================= */

    public synchronized int getCrystals(String factionId) {
        return faction.getOrDefault(factionId, 0);
    }

    public synchronized int getPlayerCrystals(UUID id) {
        return player.getOrDefault(id, 0);
    }

    /* ================= ADD ================= */
    public synchronized void addForFaction(String factionId, int amount) {
        if (amount <= 0) return;

        int cap = getCrystalCap(factionId);

        int cur = this.faction.getOrDefault(factionId, 0);
        int next = (int) Math.min(cap, (long) cur + amount);

        this.faction.put(factionId, next);
        saveAsync();
    }

    public synchronized void addForPlayer(UUID id, int amount) {
        if (amount <= 0) return;

        player.put(id, (int) Math.min(getBaseCap(), (long) getPlayerCrystals(id) + amount));
        saveAsync();
    }

    /* ================= REMOVE ================= */

    public synchronized void removeCrystals(String factionId, int amount) {
        if (amount <= 0) return;

        int cur = getCrystals(factionId);
        faction.put(factionId, Math.max(0, cur - amount));
        saveAsync();
    }
    public int getForFaction(String factionId) {
        return getCrystals(factionId);
    }
    public int getCrystalCap(String factionId) {

        int base = getBaseCap();

        int bonus = 0;

        try {
            bonus = plugin.getSkillManager()
                    .getFactionSkillLevel(factionId, SkillType.CRYSTAL_STORAGE) * 10;
        } catch (Exception ignored) {}

        int cap = base + bonus;
        try {
            if (plugin.getFactionUpgradeManager() != null) {
                cap = plugin.getFactionUpgradeManager().applyShardBankCap(factionId, cap);
            }
        } catch (Exception ignored) {}

        return cap;
    }

    private int getBaseCap() {
        return Math.max(1, plugin.getConfig().getInt("raid.crystals.max", 100));
    }

    private String factionOf(Player player) {
        if (player == null || plugin.getFactionManager() == null) return null;
        return plugin.getFactionManager().getFactionOf(player.getUniqueId());
    }
    /* ================= CONSUME ================= */

    public synchronized boolean consumeForFaction(String factionId, int amount) {
        if (amount <= 0) return true;

        int cur = getCrystals(factionId);
        if (cur < amount) return false;

        faction.put(factionId, cur - amount);
        saveAsync();

        // 🔥 UX Feedback
        for (Player p : Bukkit.getOnlinePlayers()) {
            String f = plugin.getFactionManager().getFactionOf(p.getUniqueId());
            if (factionId.equalsIgnoreCase(f)) {
                p.sendActionBar(RaidText.tr(plugin, p, "message.crystals_spent", "amount", amount));
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 0.8f);
            }
        }

        return true;
    }

    public synchronized boolean tryConsume(String factionId, int amount) {
        return consumeForFaction(factionId, amount);
    }

    public synchronized boolean consumeForPlayer(UUID id, int amount) {
        if (amount <= 0) return true;

        int cur = getPlayerCrystals(id);
        if (cur < amount) return false;

        player.put(id, cur - amount);
        saveAsync();
        return true;
    }

    /* ================= UTILS ================= */

    public synchronized List<Map.Entry<String, Integer>> topFactions(int limit) {
        List<Map.Entry<String, Integer>> list = new ArrayList<>(faction.entrySet());
        list.sort((a, b) -> Integer.compare(b.getValue(), a.getValue()));
        return limit > 0 && limit < list.size() ? list.subList(0, limit) : list;
    }

    public synchronized void add(String factionId, long amount) {
        if (amount <= 0) return;
        addForFaction(factionId, (int) amount);
    }
}
