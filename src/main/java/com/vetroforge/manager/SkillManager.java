// src/main/java/com/vetroforge/manager/SkillManager.java
package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.skills.def.DefaultSkillDefs;
import com.vetroforge.skills.def.AbilityDef;
import com.vetroforge.util.ActionBarUtil;
import com.vetroforge.skills.SkillType;
import com.vetroforge.skills.def.SkillDef;
import com.vetroforge.skills.data.*;

import org.bukkit.*;
import org.bukkit.boss.*;
import org.bukkit.entity.Player;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class SkillManager {

    private final FactionsPlugin plugin;
    private final Map<UUID, PlayerSkills> data = new ConcurrentHashMap<>();
    private final Map<UUID, BossBar> bars = new ConcurrentHashMap<>();
    private final Map<SkillType, SkillDef> defs;
    private final File file;

    public SkillManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.defs = DefaultSkillDefs.buildDefaults();
        this.file = new File(plugin.getDataFolder(), "data/player-skills.yml");
        load();
    }

    public SkillState getState(Player p, SkillType t) {
        return data
                .computeIfAbsent(p.getUniqueId(), x -> new PlayerSkills())
                .skills.computeIfAbsent(t, x -> new SkillState());
    }

    public SkillDef def(SkillType t) {
        return defs.get(t);
    }

    public int getEnergyBonus(UUID uuid) {

        PlayerSkills ps = data.get(uuid);
        if (ps == null) return 0;

        SkillState st = ps.skills.get(SkillType.RAIDER);
        if (st == null) return 0;

        AbilityState ab = st.abilities.get("energy_efficiency");

        int lvl = (ab == null ? 0 : ab.level);

        // jede Stufe +1 Max Energy
        return lvl;
    }

    public int getCrystalCap(Player p) {
        int base = 100;

        SkillState st = getState(p, SkillType.RAIDER);
        var ab = st.abilities.get("crystal_storage");

        int lvl = (ab == null ? 0 : ab.level);

        return base + (lvl * 25);
    }

    public boolean canUnlock(SkillState st, String ability) {

        if (ability.equals("demolition")) {
            return st.abilities.getOrDefault("pillage", new AbilityState()).level >= 3;
        }

        return true;
    }

    public boolean isEnabled(Player p, SkillType t) {
        if (t == SkillType.CRYSTAL_STORAGE) return false;
        SkillDef def = def(t);
        return def != null && def.enabled && !def.isWorldDisabled(p.getWorld().getName());
    }

    public SkillType defaultSkillTypeFor(Player p) {
        return SkillType.WARRIOR;
    }

    public int getFactionSkillLevel(String faction, SkillType type) {
        // simple fallback (kannst du später auf DB erweitern)
        return 0;
    }

    public int getDemolitionBonusPercent(Player player) {
        SkillState state = getState(player, SkillType.RAIDER);
        AbilityState ability = state.abilities.get("demolition");
        return ability == null ? 0 : Math.max(0, ability.level * 25);
    }

    public boolean upgradeStage(Player p, SkillType t) {
        SkillState st = getState(p, t);
        SkillDef def = def(t);
        if (def == null || !def.enabled) {
            p.sendMessage("§cDieser Skill ist aktuell nicht verfügbar.");
            return false;
        }

        if (st.skillPoints <= 0) {
            p.sendMessage("§cKeine Skillpunkte!");
            return false;
        }
        if (def == null || st.stage >= def.maxStage) {
            p.sendMessage("§cMaximale Stage bereits erreicht.");
            return false;
        }
        int next = st.stage + 1;
        int index = Math.max(0, Math.min(next - 1, def.stageReqLevel.size() - 1));
        int requiredLevel = def.stageReqLevel.isEmpty() ? 0 : def.stageReqLevel.get(index);
        if (st.level < requiredLevel) {
            p.sendMessage("§cDu brauchst Skill-Level " + requiredLevel + ".");
            return false;
        }

        st.skillPoints--;
        st.stage++;
        saveAsync();

        p.sendMessage("§aStage erhöht!");
        p.playSound(p.getLocation(), Sound.BLOCK_BEACON_POWER_SELECT, 1, 1.2f);

        return true;
    }

    public boolean upgradeAbility(Player p, SkillType t, String id) {
        SkillState st = getState(p, t);
        SkillDef def = def(t);
        if (def == null || def.abilities == null) return false;
        var ability = def.abilities.stream().filter(entry -> entry.id.equalsIgnoreCase(id)).findFirst().orElse(null);
        if (ability == null || !ability.enabled || ability.autoLevel) return false;
        AbilityState ab = st.abilities.computeIfAbsent(id, x -> new AbilityState());

        if (ab.level >= ability.maxLevel) {
            p.sendMessage("§cDiese Ability ist bereits maximal ausgebaut.");
            return false;
        }
        if (!canUnlock(st, id)) {
            p.sendMessage("§cDie vorherige Ability-Voraussetzung fehlt.");
            return false;
        }
        int requiredLevel = ability.reqSkillLv.get(Math.min(ab.level, ability.reqSkillLv.size() - 1));
        if (st.level < requiredLevel) {
            p.sendMessage("§cDu brauchst Skill-Level " + requiredLevel + ".");
            return false;
        }
        if (st.abilityPoints < ability.costPerLevel) {
            p.sendMessage("§cKeine Abilitypunkte!");
            return false;
        }

        ab.level++;

        st.abilityPoints -= ability.costPerLevel;
        saveAsync();

        p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 1, 1.2f);
        p.sendMessage("§6Skill §8» §aUpgrade erfolgreich!");

        return true;
    }

    public boolean respec(Player player, SkillType type) {
        SkillDef def = def(type);
        SkillState state = getState(player, type);
        if (def == null || def.abilities == null) return false;

        int refunded = 0;
        for (AbilityDef ability : def.abilities) {
            if (ability.autoLevel) continue;
            AbilityState abilityState = state.abilities.get(ability.id);
            if (abilityState == null || abilityState.level <= 0) continue;
            refunded += abilityState.level * Math.max(1, ability.costPerLevel);
            abilityState.level = 0;
        }
        if (refunded <= 0) {
            player.sendMessage("§eSkill §8» §7Keine manuellen Upgrades zum Zurücksetzen gefunden.");
            return false;
        }
        state.abilityPoints += refunded;
        saveAsync();
        player.playSound(player.getLocation(), Sound.BLOCK_AMETHYST_BLOCK_RESONATE, 0.8f, 1.2f);
        player.sendMessage("§eSkill §8» §aRespec abgeschlossen. §f+" + refunded + " AP §azurückerhalten.");
        return true;
    }

    public void addXp(Player p, SkillType t, int amount) {
        if (p == null || t == null || amount <= 0) return;
        if (!isEnabled(p, t)) return;

        SkillState st = getState(p, t);
        SkillDef def = def(t);
        if (def == null) return;

        st.xp += amount;

        int need = def.xpForLevel(st.level);

        while (st.level < def.levelCap && st.xp >= need) {
            st.xp -= need;
            st.level++;
            st.skillPoints += def.skillPointsPerLevel;
            st.abilityPoints += def.abilityPointsPerLevel;
            syncAutoAbilities(st, def);
            runLevelRewards(p, def, st.level);

            // 🔥 SOUND
            p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1, 1.2f);

            // 🔥 PARTICLES
            Location loc = p.getLocation().add(0,1,0);
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, loc, 40, 0.5,0.8,0.5,0.1);
            p.getWorld().spawnParticle(Particle.END_ROD, loc, 30, 0.4,0.6,0.4,0.05);

            Bukkit.getScheduler().runTaskLater(plugin, () -> {
                p.getWorld().spawnParticle(Particle.FLAME, loc, 20, 0.3,0.3,0.3,0.05);
            }, 5L);

            // 🔥 MESSAGE
            p.sendMessage("§6Skill §8» §a" + t.name() + " Level Up §7(" + st.level + ")");

            // 🔥 ULTIMATE
            if (st.level == 25) {
                p.sendTitle("§6§lULTIMATE", "§eFreigeschaltet!", 10, 50, 10);
                p.getWorld().strikeLightningEffect(p.getLocation());
                p.playSound(p.getLocation(), Sound.ENTITY_ENDER_DRAGON_GROWL, 1, 0.8f);
            }
            need = def.xpForLevel(st.level);
        }
        syncAutoAbilities(st, def);

        // 🔥 ACTIONBAR
        ActionBarUtil.send(p,
                "§e" + t.name() +
                        " §7Lvl §f" + st.level +
                        " §8• §a" + st.xp + "/" + need
        );

        showBar(p, t, st, def);
        saveAsync();
    }

    public synchronized void save() {
        YamlConfiguration yaml = new YamlConfiguration();
        for (var playerEntry : data.entrySet()) {
            String root = "players." + playerEntry.getKey() + ".";
            for (var skillEntry : playerEntry.getValue().skills.entrySet()) {
                String base = root + skillEntry.getKey().name() + ".";
                SkillState state = skillEntry.getValue();
                yaml.set(base + "level", state.level);
                yaml.set(base + "stage", state.stage);
                yaml.set(base + "xp", state.xp);
                yaml.set(base + "skillPoints", state.skillPoints);
                yaml.set(base + "abilityPoints", state.abilityPoints);
                for (var ability : state.abilities.entrySet()) {
                    yaml.set(base + "abilities." + ability.getKey(), ability.getValue().level);
                }
            }
        }
        try {
            if (file.getParentFile() != null) file.getParentFile().mkdirs();
            yaml.save(file);
        } catch (IOException ignored) {
        }
    }

    public void shutdown() {
        save();
        for (BossBar bar : bars.values()) bar.removeAll();
        bars.clear();
    }

    private void saveAsync() {
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::save);
    }

    private void load() {
        YamlConfiguration yaml = YamlConfiguration.loadConfiguration(file);
        var players = yaml.getConfigurationSection("players");
        if (players == null) return;
        for (String rawUuid : players.getKeys(false)) {
            try {
                UUID uuid = UUID.fromString(rawUuid);
                PlayerSkills playerSkills = data.computeIfAbsent(uuid, ignored -> new PlayerSkills());
                for (SkillType type : SkillType.values()) {
                    String base = "players." + rawUuid + "." + type.name() + ".";
                    if (!yaml.isConfigurationSection(base.substring(0, base.length() - 1))) continue;
                    SkillState state = playerSkills.skills.computeIfAbsent(type, ignored -> new SkillState());
                    state.level = Math.max(1, yaml.getInt(base + "level", 1));
                    state.stage = Math.max(0, yaml.getInt(base + "stage", 0));
                    state.xp = Math.max(0, yaml.getInt(base + "xp", 0));
                    state.skillPoints = Math.max(0, yaml.getInt(base + "skillPoints", 0));
                    state.abilityPoints = Math.max(0, yaml.getInt(base + "abilityPoints", 0));
                    var abilities = yaml.getConfigurationSection(base + "abilities");
                    if (abilities != null) {
                        for (String id : abilities.getKeys(false)) {
                            AbilityState ability = state.abilities.computeIfAbsent(id, ignored -> new AbilityState());
                            ability.level = Math.max(0, abilities.getInt(id, 0));
                        }
                    }
                    syncAutoAbilities(state, def(type));
                }
            } catch (IllegalArgumentException ignored) {
            }
        }
    }

    private void runLevelRewards(Player player, SkillDef def, int level) {
        if (player == null || def == null || def.rewardsByLevel == null || def.rewardsByLevel.isEmpty()) return;
        List<String> rewards = def.rewardsByLevel.get(level);
        if (rewards == null || rewards.isEmpty()) return;
        for (String raw : rewards) {
            if (raw == null || raw.isBlank()) continue;
            String command = raw
                    .replace("{player}", player.getName())
                    .replace("%player%", player.getName())
                    .replace("{skill}", def.type.name().toLowerCase(Locale.ROOT))
                    .replace("{level}", String.valueOf(level));
            Bukkit.dispatchCommand(Bukkit.getConsoleSender(), command);
        }
    }

    private void syncAutoAbilities(SkillState state, SkillDef def) {
        if (def == null || def.abilities == null) return;
        for (var ability : def.abilities) {
            if (!ability.enabled || !ability.autoLevel) continue;
            AbilityState current = state.abilities.computeIfAbsent(ability.id, id -> new AbilityState());
            int level = 0;
            for (int required : ability.reqSkillLv) {
                if (state.level >= required && level < ability.maxLevel) level++;
            }
            current.level = Math.max(current.level, level);
        }
    }

    private void showBar(Player p, SkillType t, SkillState st, SkillDef def) {

        int need = def.xpForLevel(st.level);

        BossBar bar = bars.computeIfAbsent(p.getUniqueId(),
                id -> Bukkit.createBossBar("", BarColor.GREEN, BarStyle.SEGMENTED_10));

        double progress = Math.max(0.0D, Math.min(1.0D, st.xp / (double) Math.max(1, need)));
        bar.setProgress(progress);
        bar.setTitle("§e" + t.name() + " §7Lvl §f" + st.level);

        if (!bar.getPlayers().contains(p)) {
            bar.addPlayer(p);
        }

        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            bar.removePlayer(p);
        }, 60L);
    }
}
