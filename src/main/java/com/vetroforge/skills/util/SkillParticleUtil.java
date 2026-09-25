package com.vetroforge.skills.util;

import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

public class SkillParticleUtil {

    public static void levelUp(Player p) {

        p.getWorld().spawnParticle(
                Particle.TOTEM_OF_UNDYING,
                p.getLocation().add(0, 1, 0),
                40,
                0.5, 0.5, 0.5,
                0.1
        );

        p.playSound(p.getLocation(), Sound.UI_TOAST_CHALLENGE_COMPLETE, 1f, 1f);
    }
}