// src/main/java/com/vetroforge/skills/data/SkillState.java
package com.vetroforge.skills.data;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public class SkillState {
    public int level = 1;
    public int stage = 0;
    public int xp = 0;
    public int skillPoints = 0;
    public int abilityPoints = 0;

    public Map<String, AbilityState> abilities = new HashMap<>();

    // NEU: bereits beanspruchte Rewards (Level → claimed)
    public Set<Integer> claimedRewardLevels = new HashSet<>();
}