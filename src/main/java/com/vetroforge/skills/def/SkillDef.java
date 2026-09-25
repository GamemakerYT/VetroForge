package com.vetroforge.skills.def;

import com.vetroforge.skills.SkillType;

import java.util.*;

public final class SkillDef {
    public final SkillType type;
    public final boolean enabled;
    public final Set<String> worldsDisabled; // world names (lowercase)

    // XP Kurve: base * ratio^(level-1)
    public final int xpBase;
    public final double xpRatio;
    public final int levelCap;

    // Punkte pro Level
    public final int skillPointsPerLevel;
    public final int abilityPointsPerLevel;

    // Stages: maxStufe, Req-Level je Stufe (Index 1..maxStage)
    public final int maxStage;
    public final List<Integer> stageReqLevel;

    // Level-Rewards: Level -> List of console commands (with {player})
    public final Map<Integer, List<String>> rewardsByLevel;

    // Fähigkeiten
    public final List<AbilityDef> abilities;

    public SkillDef(SkillType type,
                    boolean enabled,
                    Set<String> worldsDisabled,
                    int xpBase, double xpRatio, int levelCap,
                    int skillPointsPerLevel, int abilityPointsPerLevel,
                    int maxStage, List<Integer> stageReqLevel,
                    Map<Integer, List<String>> rewardsByLevel,
                    List<AbilityDef> abilities) {
        this.type = type;
        this.enabled = enabled;
        this.worldsDisabled = (worldsDisabled == null) ? Collections.emptySet() : worldsDisabled;
        this.xpBase = Math.max(1, xpBase);
        this.xpRatio = Math.max(1.0, xpRatio);
        this.levelCap = Math.max(1, levelCap);
        this.skillPointsPerLevel = Math.max(0, skillPointsPerLevel);
        this.abilityPointsPerLevel = Math.max(0, abilityPointsPerLevel);
        this.maxStage = Math.max(1, maxStage);
        this.stageReqLevel = (stageReqLevel == null || stageReqLevel.isEmpty())
                ? java.util.Collections.nCopies(this.maxStage + 1, 1) : stageReqLevel;
        this.rewardsByLevel = (rewardsByLevel == null) ? new HashMap<>() : rewardsByLevel;
        this.abilities = (abilities == null) ? new ArrayList<>() : abilities;
    }

    public boolean isWorldDisabled(String wName) {
        return wName != null && worldsDisabled.contains(wName.toLowerCase(Locale.ROOT));
    }

    public int xpForLevel(int level) {
        if (level <= 1) return xpBase;
        double v = xpBase * Math.pow(xpRatio, level - 1);
        long l = Math.round(v);
        return (int) Math.max(1, Math.min(Integer.MAX_VALUE, l));
    }
}