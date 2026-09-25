package com.vetroforge.raid.runes;

public enum RuneType {
    RAID_DAMAGE,   // +10% Entity-Damage während Raids (RuneDamageListener)
    TNT_EXPERT,    // +20% Obsidian-Damage bei TNT-Breach (ExplosionListener)
    CRYSTAL_GAIN;  // +5% Crystal-Ertrag bei Raid-Belohnungen (RaidRewardService)

    public String display() {
        return switch (this) {
            case RAID_DAMAGE -> "§6Rune: §eRaid-Schaden §7(+10%)";
            case TNT_EXPERT -> "§6Rune: §cSprengmeister §7(+20% Obsidian)";
            case CRYSTAL_GAIN -> "§6Rune: §aKristallsegen §7(+5% Ertrag)";
        };
    }
}