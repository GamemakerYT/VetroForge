package com.vetroforge.skills;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.boss.BarColor;
import org.bukkit.boss.BarStyle;
import org.bukkit.boss.BossBar;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public class SkillManager {

    private final FactionsPlugin plugin;
    private final File skillFile;
    private final YamlConfiguration skillConfig;
    private final Map<UUID, SkillProfile> profiles = new ConcurrentHashMap<>();
    private final Map<UUID, Map<SkillType, BossBarContext>> bossBars = new ConcurrentHashMap<>();

    private final double xpBase;
    private final double xpMultiplier;
    private final boolean autoLevelAbilities;
    private final long bossBarDurationTicks;
    private final BarColor bossBarColor;
    private final Set<String> disabledWorlds;
    private final Set<String> disabledSkills;
    private final Set<String> disabledAbilities;
    private final Map<SkillType, Map<String, Integer>> stageRequirements = new EnumMap<>(SkillType.class);
    private final Map<SkillType, Map<String, Integer>> abilityRequirements = new EnumMap<>(SkillType.class);
    private final Map<SkillType, Map<String, Double>> xpRewards = new EnumMap<>(SkillType.class);

    public SkillManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.skillFile = new File(plugin.getDataFolder(), "skills.yml");
        if (!skillFile.exists()) {
            try {
                if (!skillFile.getParentFile().exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    skillFile.getParentFile().mkdirs();
                }
                //noinspection ResultOfMethodCallIgnored
                skillFile.createNewFile();
            } catch (IOException ex) {
                plugin.getPrefixedLogger().warning("Unable to create skills.yml: " + ex.getMessage());
            }
        }
        this.skillConfig = YamlConfiguration.loadConfiguration(skillFile);
        FileConfiguration config = plugin.getConfig();
        this.xpBase = config.getDouble("skills.xpCurve.base", 100.0);
        this.xpMultiplier = config.getDouble("skills.xpCurve.multiplier", 1.4);
        this.autoLevelAbilities = config.getBoolean("skills.autoLevelAbilities", true);
        this.bossBarDurationTicks = (long) (config.getDouble("skills.bossBarSeconds", 3.0) * 20.0);
        this.bossBarColor = parseColor(config.getString("skills.progressBarColor", "BLUE"));
        this.disabledWorlds = new HashSet<>(config.getStringList("skills.disabledWorlds"));
        this.disabledSkills = lowercaseSet(config.getStringList("skills.disabledSkills"));
        this.disabledAbilities = lowercaseSet(config.getStringList("skills.disabledAbilities"));
        loadStageRequirements(config.getConfigurationSection("skills.stageRequirements"));
        loadAbilityRequirements(config.getConfigurationSection("skills.abilityRequirements"));
        loadXpRewards(config.getConfigurationSection("skills.xpRewards"));
    }

    private BarColor parseColor(String name) {
        if (name == null) {
            return BarColor.BLUE;
        }
        try {
            return BarColor.valueOf(name.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            plugin.getPrefixedLogger().warning("Unknown boss bar color '" + name + "', falling back to BLUE");
            return BarColor.BLUE;
        }
    }

    private Set<String> lowercaseSet(Iterable<String> values) {
        Set<String> set = new HashSet<>();
        if (values != null) {
            for (String value : values) {
                if (value != null) {
                    set.add(value.toLowerCase(Locale.ROOT));
                }
            }
        }
        return set;
    }

    private void loadStageRequirements(ConfigurationSection section) {
        stageRequirements.clear();
        if (section == null) {
            return;
        }
        for (String skillKey : section.getKeys(false)) {
            SkillType type = SkillType.fromString(skillKey);
            if (type == null) {
                continue;
            }
            Map<String, Integer> requirements = new HashMap<>();
            ConfigurationSection skillSection = section.getConfigurationSection(skillKey);
            if (skillSection != null) {
                for (String stageKey : skillSection.getKeys(false)) {
                    requirements.put(stageKey.toLowerCase(Locale.ROOT), skillSection.getInt(stageKey));
                }
            }
            stageRequirements.put(type, requirements);
        }
    }

    private void loadAbilityRequirements(ConfigurationSection section) {
        abilityRequirements.clear();
        if (section == null) {
            return;
        }
        for (String skillKey : section.getKeys(false)) {
            SkillType type = SkillType.fromString(skillKey);
            if (type == null) {
                continue;
            }
            Map<String, Integer> requirements = new HashMap<>();
            ConfigurationSection skillSection = section.getConfigurationSection(skillKey);
            if (skillSection != null) {
                for (String abilityKey : skillSection.getKeys(false)) {
                    requirements.put(abilityKey.toLowerCase(Locale.ROOT), skillSection.getInt(abilityKey));
                }
            }
            abilityRequirements.put(type, requirements);
        }
    }

    private void loadXpRewards(ConfigurationSection section) {
        xpRewards.clear();
        if (section == null) {
            return;
        }
        for (String skillKey : section.getKeys(false)) {
            SkillType type = SkillType.fromString(skillKey);
            if (type == null) {
                continue;
            }
            Map<String, Double> rewards = new HashMap<>();
            ConfigurationSection skillSection = section.getConfigurationSection(skillKey);
            if (skillSection != null) {
                for (String rewardKey : skillSection.getKeys(false)) {
                    rewards.put(rewardKey.toLowerCase(Locale.ROOT), skillSection.getDouble(rewardKey));
                }
            }
            xpRewards.put(type, rewards);
        }
    }

    public Map<String, Double> getXpRewards(SkillType type) {
        return xpRewards.getOrDefault(type, Collections.emptyMap());
    }

    public double getRewardValue(SkillType type, String rewardKey) {
        if (type == null || rewardKey == null) {
            return 0;
        }
        Map<String, Double> rewards = xpRewards.get(type);
        if (rewards == null) {
            return 0;
        }
        return rewards.getOrDefault(rewardKey.toLowerCase(Locale.ROOT), 0.0);
    }

    public void reward(Player player, SkillType type, String rewardKey) {
        double value = getRewardValue(type, rewardKey);
        if (value > 0) {
            addXp(player, type, value);
        }
    }

    public void load(Player player) {
        UUID uuid = player.getUniqueId();
        SkillProfile profile = new SkillProfile(uuid);
        ConfigurationSection baseSection = skillConfig.getConfigurationSection("players." + uuid + ".skills");
        if (baseSection != null) {
            for (String skillKey : baseSection.getKeys(false)) {
                SkillType type = SkillType.fromString(skillKey);
                if (type == null) {
                    continue;
                }
                SkillProgress progress = profile.getProgress(type);
                ConfigurationSection progressSection = baseSection.getConfigurationSection(skillKey);
                if (progressSection == null) {
                    continue;
                }
                progress.setLevel(progressSection.getInt("level", 1));
                progress.setXp(progressSection.getDouble("xp", 0));
                progress.setPoints(progressSection.getInt("points", 0));
                progress.getUnlockedStages().clear();
                progress.getUnlockedAbilities().clear();
                progress.getUnlockedStages().addAll(lowercaseSet(progressSection.getStringList("stages")));
                progress.getUnlockedAbilities().addAll(lowercaseSet(progressSection.getStringList("abilities")));
            }
        }
        profiles.put(uuid, profile);
    }

    public void save(Player player) {
        save(player, true);
    }

    private void save(Player player, boolean flush) {
        UUID uuid = player.getUniqueId();
        SkillProfile profile = profiles.get(uuid);
        if (profile == null) {
            return;
        }
        String path = "players." + uuid + ".skills";
        skillConfig.set(path, null);
        for (Map.Entry<SkillType, SkillProgress> entry : profile.getProgressMap().entrySet()) {
            String skillPath = path + "." + entry.getKey().name().toLowerCase(Locale.ROOT);
            SkillProgress progress = entry.getValue();
            skillConfig.set(skillPath + ".level", progress.getLevel());
            skillConfig.set(skillPath + ".xp", progress.getXp());
            skillConfig.set(skillPath + ".points", progress.getPoints());
            skillConfig.set(skillPath + ".stages", progress.getUnlockedStages().stream().toList());
            skillConfig.set(skillPath + ".abilities", progress.getUnlockedAbilities().stream().toList());
        }
        if (flush) {
            writeToDisk();
        }
    }

    public void saveAll() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            save(player, false);
        }
        writeToDisk();
    }

    private void writeToDisk() {
        try {
            skillConfig.save(skillFile);
        } catch (IOException ex) {
            plugin.getPrefixedLogger().warning("Failed to save skills.yml: " + ex.getMessage());
        }
    }

    public SkillProfile getProfile(Player player) {
        return profiles.computeIfAbsent(player.getUniqueId(), SkillProfile::new);
    }

    public void loadOnlinePlayers() {
        for (Player player : Bukkit.getOnlinePlayers()) {
            load(player);
        }
    }

    public void removeProfile(UUID uuid) {
        profiles.remove(uuid);
        removeBossBars(uuid);
    }

    public void addXp(Player player, SkillType type, double amount) {
        if (player == null || type == null || amount <= 0) {
            return;
        }
        if (disabledWorlds.contains(player.getWorld().getName())) {
            return;
        }
        if (disabledSkills.contains(type.name().toLowerCase(Locale.ROOT))) {
            return;
        }
        SkillProfile profile = getProfile(player);
        SkillProgress progress = profile.getProgress(type);
        progress.setXp(progress.getXp() + amount);
        boolean leveled = handleAutoLevel(player, type, progress);
        showBossBar(player, type, progress);
        if (leveled) {
            player.sendMessage("§a" + type.displayName() + " skill leveled up to §b" + progress.getLevel() + "§a!");
        }
    }

    private boolean handleAutoLevel(Player player, SkillType type, SkillProgress progress) {
        boolean leveled = false;
        boolean changed;
        do {
            changed = false;
            double required = getRequiredXp(progress.getLevel());
            if (progress.getXp() >= required) {
                progress.setXp(progress.getXp() - required);
                progress.setLevel(progress.getLevel() + 1);
                progress.setPoints(progress.getPoints() + 1);
                applyUnlocks(player, type, progress);
                changed = true;
                leveled = true;
            }
        } while (changed);
        return leveled;
    }

    private void applyUnlocks(Player player, SkillType type, SkillProgress progress) {
        Map<String, Integer> stageReq = stageRequirements.getOrDefault(type, Collections.emptyMap());
        for (Map.Entry<String, Integer> entry : stageReq.entrySet()) {
            if (progress.getLevel() >= entry.getValue() && !progress.hasStage(entry.getKey())) {
                progress.unlockStage(entry.getKey());
                player.sendMessage("§bStage unlocked: §f" + prettify(entry.getKey()));
            }
        }
        if (!autoLevelAbilities) {
            return;
        }
        Map<String, Integer> abilityReq = abilityRequirements.getOrDefault(type, Collections.emptyMap());
        for (Map.Entry<String, Integer> entry : abilityReq.entrySet()) {
            String ability = entry.getKey();
            if (disabledAbilities.contains(ability)) {
                continue;
            }
            if (progress.getLevel() >= entry.getValue() && !progress.hasAbility(ability)) {
                progress.unlockAbility(ability);
                player.sendMessage("§dAbility unlocked: §f" + prettify(ability));
            }
        }
    }

    private String prettify(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }
        return Character.toUpperCase(value.charAt(0)) + (value.length() > 1 ? value.substring(1) : "");
    }

    private void showBossBar(Player player, SkillType type, SkillProgress progress) {
        if (bossBarDurationTicks <= 0) {
            return;
        }
        double required = getRequiredXp(progress.getLevel());
        double current = progress.getXp();
        double ratio = required <= 0 ? 1.0 : Math.min(1.0, current / required);
        Map<SkillType, BossBarContext> map = bossBars.computeIfAbsent(player.getUniqueId(), id -> new EnumMap<>(SkillType.class));
        BossBarContext context = map.computeIfAbsent(type, key -> new BossBarContext(Bukkit.createBossBar("", bossBarColor, BarStyle.SEGMENTED_10)));
        BossBar bossBar = context.bossBar;
        bossBar.setVisible(true);
        bossBar.setProgress(ratio);
        bossBar.setTitle("§b" + type.displayName() + " §7— §f" + Math.round(current) + "/" + Math.round(required));
        if (!bossBar.getPlayers().contains(player)) {
            bossBar.addPlayer(player);
        }
        if (context.removalTask != null) {
            context.removalTask.cancel();
        }
        BukkitTask task = new BukkitRunnable() {
            @Override
            public void run() {
                bossBar.removePlayer(player);
                bossBar.setVisible(false);
            }
        }.runTaskLater(plugin, bossBarDurationTicks);
        context.removalTask = task;
    }

    public double getRequiredXp(int level) {
        return xpBase * Math.pow(xpMultiplier, Math.max(0, level - 1));
    }

    public void removeBossBars(UUID uuid) {
        Map<SkillType, BossBarContext> map = bossBars.remove(uuid);
        if (map == null) {
            return;
        }
        for (BossBarContext context : map.values()) {
            context.close();
        }
    }

    public void shutdown() {
        for (Map<SkillType, BossBarContext> map : bossBars.values()) {
            for (BossBarContext context : map.values()) {
                context.close();
            }
        }
        bossBars.clear();
    }

    public boolean isWorldDisabled(World world) {
        return world != null && disabledWorlds.contains(world.getName());
    }

    public void debugDrop(Player player, SkillType type) {
        SkillProfile profile = getProfile(player);
        SkillProgress progress = profile.getProgress(type);
        player.sendMessage("Skill " + type.displayName() + " → lvl " + progress.getLevel() + " xp=" + progress.getXp());
    }

    private static class BossBarContext {
        private final BossBar bossBar;
        private BukkitTask removalTask;

        private BossBarContext(BossBar bossBar) {
            this.bossBar = bossBar;
        }

        private void close() {
            if (removalTask != null) {
                removalTask.cancel();
            }
            bossBar.removeAll();
        }
    }
}
