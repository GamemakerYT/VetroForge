package com.vetroforge.skills;

import java.util.EnumMap;
import java.util.Map;
import java.util.UUID;

public class SkillProfile {

    private final UUID playerId;
    private final Map<SkillType, SkillProgress> progressMap = new EnumMap<>(SkillType.class);

    public SkillProfile(UUID playerId) {
        this.playerId = playerId;
        for (SkillType type : SkillType.values()) {
            progressMap.put(type, new SkillProgress(1, 0, 0));
        }
    }

    public UUID getPlayerId() {
        return playerId;
    }

    public Map<SkillType, SkillProgress> getProgressMap() {
        return progressMap;
    }

    public SkillProgress getProgress(SkillType type) {
        return progressMap.computeIfAbsent(type, key -> new SkillProgress(1, 0, 0));
    }
}
