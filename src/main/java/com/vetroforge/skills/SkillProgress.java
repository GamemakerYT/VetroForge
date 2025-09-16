package com.vetroforge.skills;

import java.util.HashSet;
import java.util.Objects;
import java.util.Set;

public class SkillProgress {

    private int level;
    private double xp;
    private int points;
    private final Set<String> unlockedStages = new HashSet<>();
    private final Set<String> unlockedAbilities = new HashSet<>();

    public SkillProgress(int level, double xp, int points) {
        this.level = level;
        this.xp = xp;
        this.points = points;
    }

    public int getLevel() {
        return level;
    }

    public void setLevel(int level) {
        this.level = level;
    }

    public double getXp() {
        return xp;
    }

    public void setXp(double xp) {
        this.xp = xp;
    }

    public int getPoints() {
        return points;
    }

    public void setPoints(int points) {
        this.points = points;
    }

    public Set<String> getUnlockedStages() {
        return unlockedStages;
    }

    public Set<String> getUnlockedAbilities() {
        return unlockedAbilities;
    }

    public void unlockStage(String stage) {
        if (stage != null && !stage.isEmpty()) {
            unlockedStages.add(stage.toLowerCase());
        }
    }

    public boolean hasStage(String stage) {
        return stage != null && unlockedStages.contains(stage.toLowerCase());
    }

    public void unlockAbility(String ability) {
        if (ability != null && !ability.isEmpty()) {
            unlockedAbilities.add(ability.toLowerCase());
        }
    }

    public boolean hasAbility(String ability) {
        return ability != null && unlockedAbilities.contains(ability.toLowerCase());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof SkillProgress)) return false;
        SkillProgress that = (SkillProgress) o;
        return level == that.level && Double.compare(that.xp, xp) == 0 && points == that.points && unlockedStages.equals(that.unlockedStages) && unlockedAbilities.equals(that.unlockedAbilities);
    }

    @Override
    public int hashCode() {
        return Objects.hash(level, xp, points, unlockedStages, unlockedAbilities);
    }
}
