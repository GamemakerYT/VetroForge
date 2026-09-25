package com.vetroforge.skills.def;

import java.util.List;

public final class AbilityDef {
    public final String id;                 // "backstab"
    public final String display;           // "Backstab"
    public final int maxLevel;             // 1..N
    public final boolean enabled;          // global toggle
    public final boolean autoLevel;        // level folgt skill level
    public final int costPerLevel;         // Ability Points pro Level
    public final List<Integer> reqSkillLv; // Minimal-Skill-Level je Ability-Level (size = maxLevel)

    public AbilityDef(String id, String display, int maxLevel,
                      boolean enabled, boolean autoLevel, int costPerLevel, List<Integer> reqSkillLv) {
        this.id = id;
        this.display = display;
        this.maxLevel = Math.max(1, maxLevel);
        this.enabled = enabled;
        this.autoLevel = autoLevel;
        this.costPerLevel = Math.max(0, costPerLevel);
        this.reqSkillLv = (reqSkillLv == null || reqSkillLv.isEmpty())
                ? java.util.Collections.nCopies(this.maxLevel, 1) : reqSkillLv;
    }
}