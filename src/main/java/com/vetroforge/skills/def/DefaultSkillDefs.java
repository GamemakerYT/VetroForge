// src/main/java/com/vetroforge/manager/DefaultSkillDefs.java

package com.vetroforge.skills.def;

import com.vetroforge.skills.SkillType;
import com.vetroforge.skills.def.SkillDef;

import java.util.*;

public class DefaultSkillDefs {

    public static Map<SkillType, SkillDef> buildDefaults() {

        Map<SkillType, SkillDef> map = new EnumMap<>(SkillType.class);

        for (SkillType t : SkillType.values()) {
            List<AbilityDef> abilities = switch (t) {
                case WARRIOR -> List.of(
                        new AbilityDef("damage_boost", "Damage Boost", 5, true, false, 1, List.of(1, 5, 10, 15, 20))
                );
                case RAIDER -> List.of(
                        new AbilityDef("pillage", "Pillage", 3, true, false, 1, List.of(1, 5, 10)),
                        new AbilityDef("demolition", "Demolition", 5, true, false, 1, List.of(5, 10, 15, 20, 25)),
                        new AbilityDef("obsidian_master", "Obsidian Master", 5, true, false, 1, List.of(3, 6, 9, 12, 15)),
                        new AbilityDef("energy_efficiency", "Energy Efficiency", 3, true, false, 1, List.of(10, 20, 30)),
                        new AbilityDef("crystal_storage", "Crystal Storage", 4, true, false, 1, List.of(5, 15, 25, 35))
                );
                case ASSASSIN -> List.of(
                        new AbilityDef("backstab", "Backstab", 5, true, false, 1, List.of(1, 5, 10, 15, 20))
                );
                case HEALER -> List.of(
                        new AbilityDef("recovery", "Recovery", 5, true, false, 1, List.of(1, 5, 10, 15, 20))
                );
                case CRYSTAL_STORAGE -> List.of();
            };
            map.put(t, new SkillDef(
                    t,
                    true,
                    Set.of(),
                    100,
                    1.3,
                    50,
                    1,
                    1,
                    3,
                    List.of(0,10,25,40),
                    new HashMap<>(),
                    abilities
            ));
        }

        return map;
    }
}
