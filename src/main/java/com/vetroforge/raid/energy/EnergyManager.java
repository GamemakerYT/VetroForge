// src/main/java/com/vetroforge/raid/energy/EnergyManager.java
package com.vetroforge.raid.energy;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.persistence.YamlIO;
import com.vetroforge.util.VetroForgeUtil;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class EnergyManager {

    private final FactionsPlugin plugin;
    private final File file;

    private final Map<UUID, Integer> energy = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastGain = new ConcurrentHashMap<>();

    public EnergyManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "raid_energy.yml");
        load();
        startRegenTask();
    }

    private void load() {
        FileConfiguration y = YamlIO.load(file);
        if (y.isConfigurationSection("energy")) {
            for (String k : y.getConfigurationSection("energy").getKeys(false)) {
                try { energy.put(UUID.fromString(k), y.getInt("energy." + k, 0)); } catch (Exception ignored) {}
            }
        }
        if (y.isConfigurationSection("lastGain")) {
            for (String k : y.getConfigurationSection("lastGain").getKeys(false)) {
                try { lastGain.put(UUID.fromString(k), y.getLong("lastGain." + k, 0)); } catch (Exception ignored) {}
            }
        }
    }

    public void save() {
        FileConfiguration y = YamlIO.load(file);
        for (var e : energy.entrySet()) y.set("energy." + e.getKey(), e.getValue());
        for (var e : lastGain.entrySet()) y.set("lastGain." + e.getKey(), e.getValue());
        YamlIO.save(y, file);
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    public int get(UUID uuid) {
        regen(uuid);
        return energy.getOrDefault(uuid, getMax(uuid));
    }

    public int getEnergy(Player p) {
        return get(p.getUniqueId());
    }

    public boolean removeEnergy(Player p, int amount) {
        return take(p.getUniqueId(), amount);
    }

    public void set(UUID uuid, int val) {
        energy.put(uuid, Math.max(0, Math.min(getMax(uuid), val)));
        lastGain.putIfAbsent(uuid, System.currentTimeMillis());
        saveAsync();
    }

    public void add(UUID uuid, int val) {
        set(uuid, get(uuid) + val);
    }

    public boolean take(UUID uuid, int val) {
        if (get(uuid) < val) return false;
        set(uuid, get(uuid) - val);
        return true;
    }

    private void regen(UUID uuid) {

        long now = System.currentTimeMillis();
        long last = lastGain.getOrDefault(uuid, now);

        long diff = now - last;
        int gain = (int) (diff / getRegenMillis());

        if (gain <= 0) return;

        int current = energy.getOrDefault(uuid, getMax(uuid));
        energy.put(uuid, Math.min(getMax(uuid), current + gain));
        lastGain.put(uuid, last + gain * getRegenMillis());
        saveAsync();
    }

    private void startRegenTask() {
        Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Player p : Bukkit.getOnlinePlayers()) {

                UUID id = p.getUniqueId();

                int before = get(id);
                regen(id);
                int after = get(id);

                if (after > before) {
                    p.sendActionBar(VetroForgeUtil.tr(plugin, p, "energy.regenerated",
                            VetroForgeUtil.vars("current", after, "cap", getMax(id))));
                }
            }
        }, 20L, 20L * 60);
    }

    public void stop() {
        save();
    }

    public int getMax(UUID uuid) {
        int base = plugin.getConfig().getInt("energy.cap", 10);
        int bonus = plugin.getSkillManager().getEnergyBonus(uuid);
        return base + bonus;
    }

    public long getRegenMillis() {
        return plugin.getConfig().getLong(
                "energy.regenMinutes",
                plugin.getConfig().getLong("energy.tickMinutes", 60)
        ) * 60_000L;
    }

    public long millisToNext(UUID uuid) {
        long now = System.currentTimeMillis();
        long last = lastGain.getOrDefault(uuid, now);

        long interval = getRegenMillis();
        return interval - ((now - last) % interval);
    }
}
