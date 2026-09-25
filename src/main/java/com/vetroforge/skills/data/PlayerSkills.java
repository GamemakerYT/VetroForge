package com.vetroforge.skills.data;

import com.vetroforge.skills.SkillType;

import java.util.EnumMap;
import java.util.Map;

public final class PlayerSkills {
    public final Map<SkillType, SkillState> skills = new EnumMap<>(SkillType.class);
}