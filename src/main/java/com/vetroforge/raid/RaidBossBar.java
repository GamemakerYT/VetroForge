// src/main/java/com/vetroforge/raid/RaidBossBar.java
package com.vetroforge.raid;

import net.kyori.adventure.bossbar.BossBar;
import net.kyori.adventure.text.Component;

public class RaidBossBar {

    public static BossBar create(RaidManager.Session s) {
        float progress = (float) s.obsidianBroken / (float) s.obsidianGoal;

        return BossBar.bossBar(
                Component.text("Raid " + s.targetFaction + " " + s.obsidianBroken + "/" + s.obsidianGoal),
                progress,
                BossBar.Color.RED,
                BossBar.Overlay.PROGRESS
        );
    }
}