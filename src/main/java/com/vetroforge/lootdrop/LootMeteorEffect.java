// src/main/java/com/vetroforge/lootdrop/LootMeteorEffect.java
package com.vetroforge.lootdrop;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.ActionBarUtil;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.scheduler.BukkitRunnable;

public final class LootMeteorEffect {
    private LootMeteorEffect(){}

    public static void play(FactionsPlugin plugin, Location target, Runnable onImpact){
        if (plugin == null || target == null || target.getWorld() == null) return;

        final World w = target.getWorld();
        final Location pos = target.clone().add(0.5, 25.0, 0.5); // Start 25 Blöcke hoch

        // Partikel robust per Name + Fallbacks
        final Particle P_SPARK = particle("FIREWORKS_SPARK",
                particle("FIREWORK", Particle.CRIT));
        final Particle P_RED   = particle("REDSTONE", null); // DustOptions wenn vorhanden
        final Particle P_SMOKE = particle("SMOKE_LARGE",
                particle("LARGE_SMOKE",
                        particle("SMOKE_NORMAL",
                                particle("SMOKE", Particle.CLOUD))));
        final Particle P_EXPL  = particle("EXPLOSION_LARGE",
                particle("EXPLOSION_HUGE",
                        particle("EXPLOSION_NORMAL",
                                particle("EXPLOSION", Particle.CLOUD))));

        new BukkitRunnable(){
            double vy = -0.6; // Fallgeschwindigkeit
            int ticks = 0;

            @Override public void run() {
                ticks++;

                // Trail
                try {
                    w.spawnParticle(P_SPARK, pos, 6, 0.15, 0.15, 0.15, 0.01);
                    if (P_RED != null) {
                        try {
                            Particle.DustOptions dust = new Particle.DustOptions(Color.fromRGB(180, 20, 240), 1.3f);
                            w.spawnParticle(P_RED, pos, 1, 0, 0, 0, 0, dust, true);
                        } catch (Throwable ignore) {
                            w.spawnParticle(Particle.CRIT, pos, 2, 0.05, 0.05, 0.05, 0.01);
                        }
                    }
                } catch (Throwable ignore){}

                // Bewegung
                pos.add(0, vy, 0);

                // Einschlag?
                if (pos.getY() <= target.getY() + 1.2 || ticks > 200) {
                    try {
                        Location boom = target.clone().add(0.5, 1.0, 0.5);
                        w.spawnParticle(P_EXPL,  boom, 1);
                        w.spawnParticle(P_SMOKE, boom, 25, 0.6, 0.6, 0.6, 0.02);
                        w.playSound(target, org.bukkit.Sound.ENTITY_GENERIC_EXPLODE, 1.0f, 1.0f);
                    } catch (Throwable ignore){}

                    // Overlay + ActionBar um den Einschlag
                    try {
                        ActionBarUtil.showNearby(plugin, target, 64.0);
                    } catch (Throwable ignore){}

                    // Kiste spawnen etc.
                    if (onImpact != null) {
                        try {
                            onImpact.run();
                        } catch (Throwable t) {
                            plugin.getLogger().log(java.util.logging.Level.SEVERE,
                                    "[LootDrop] Impact fehlgeschlagen: " + t.getMessage(), t);
                        }
                    }

                    cancel();
                }
            }
        }.runTaskTimer(plugin, 1L, 1L);
    }

    private static Particle particle(String name, Particle fallback){
        try { return Particle.valueOf(name); } catch (Throwable t) { return fallback; }
    }
}
