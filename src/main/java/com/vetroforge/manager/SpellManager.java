package com.vetroforge.manager;

import com.vetroforge.FactionsPlugin;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.attribute.Attribute;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

public class SpellManager {

    private final FactionsPlugin plugin;

    private final Map<UUID, Map<String, Long>> nextUse = new HashMap<>();

    // 1.21-kompatibel: kein Enum mehr -> kein valueOf(). Wir suchen per Feldname/Key.
    private static final Attribute ATTR_MAX_HEALTH = resolveAttribute("MAX_HEALTH", "GENERIC_MAX_HEALTH", "generic.max_health");

    public SpellManager(FactionsPlugin plugin) {
        this.plugin = plugin;
    }

    public boolean castSpell(Player p, String id) {
        if (p == null || id == null || id.isBlank()) return false;

        String spellId = id.toLowerCase(Locale.ROOT);
        String base = resolveBasePath(spellId);
        if (base == null) {
            p.sendMessage("§cUnbekannter Zauber: §f" + spellId);
            return false;
        }

        FileConfiguration cfg = plugin.getConfig();

        long cooldownMs = 5000L;
        if (cfg.isInt(base + "cooldownSeconds")) {
            cooldownMs = Math.max(0, cfg.getInt(base + "cooldownSeconds", 5)) * 1000L;
        } else if (cfg.isLong(base + "cooldownMs") || cfg.isInt(base + "cooldownMs")) {
            cooldownMs = Math.max(0, cfg.getLong(base + "cooldownMs", 5000L));
        }

        long now = System.currentTimeMillis();
        long readyAt = getNextUse(p.getUniqueId(), spellId);
        if (now < readyAt) {
            long remain = (readyAt - now + 999) / 1000;
            p.sendMessage("§cNoch auf Abklingzeit: §f" + remain + "s");
            return false;
        }

        String effect = cfg.getString(base + "effect", "HEAL");
        int durationSeconds = Math.max(0, cfg.getInt(base + "durationSeconds", 5));
        int amplifier = Math.max(0, cfg.getInt(base + "amplifier", 1));

        boolean ok = applyEffect(p, effect, durationSeconds, amplifier);

        if (ok) {
            setNextUse(p.getUniqueId(), spellId, now + cooldownMs);
        }
        return ok;
    }

    public boolean cast(Player p, String id) { return castSpell(p, id); }

    private String resolveBasePath(String id) {
        String base = "spells.list." + id + ".";
        if (plugin.getConfig().isConfigurationSection("spells.list." + id)) return base;
        base = "spells." + id + ".";
        if (plugin.getConfig().isConfigurationSection("spells." + id)) return base;
        return null;
    }

    private long getNextUse(UUID u, String spellId) {
        Map<String, Long> m = nextUse.get(u);
        if (m == null) return 0L;
        Long ts = m.get(spellId);
        return ts == null ? 0L : ts;
    }

    private void setNextUse(UUID u, String spellId, long ts) {
        nextUse.computeIfAbsent(u, k -> new HashMap<>()).put(spellId, ts);
    }

    private boolean applyEffect(Player p, String effectRaw, int durationSeconds, int amplifier) {
        if (effectRaw == null || effectRaw.isBlank()) return false;
        String eff = effectRaw.toUpperCase(Locale.ROOT);

        switch (eff) {
            case "HEAL" -> {
                double max = 20.0;
                try {
                    var attr = (ATTR_MAX_HEALTH == null) ? null : p.getAttribute(ATTR_MAX_HEALTH);
                    if (attr != null) max = attr.getValue();
                } catch (Throwable ignored) {}
                p.setHealth(Math.min(max, p.getHealth() + 4.0));
                p.getWorld().spawnParticle(resolveParticle("HEART", "HEART"), p.getLocation().add(0, 1, 0), 10, 0.35, 0.35, 0.35, 0.0);
                p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1.05f);
                return true;
            }
            case "SPEED" -> {
                PotionEffectType t = resolveEffectType("SPEED");
                if (t == null) return false;
                giveEffect(p, t, durationSeconds, amplifier);
                return true;
            }
            case "FIRE_RESISTANCE", "FIRE_RES" -> {
                PotionEffectType t = resolveEffectType("FIRE_RESISTANCE");
                if (t == null) return false;
                giveEffect(p, t, durationSeconds, amplifier);
                return true;
            }
            case "STRENGTH", "INCREASE_DAMAGE" -> {
                PotionEffectType t = resolveEffectType("INCREASE_DAMAGE", "STRENGTH");
                if (t == null) return false;
                giveEffect(p, t, durationSeconds, amplifier);
                return true;
            }
            case "JUMP", "JUMP_BOOST" -> {
                PotionEffectType t = resolveEffectType("JUMP", "JUMP_BOOST");
                if (t == null) return false;
                giveEffect(p, t, durationSeconds, amplifier);
                return true;
            }
            case "REGEN", "REGENERATION" -> {
                PotionEffectType t = resolveEffectType("REGENERATION");
                if (t == null) return false;
                giveEffect(p, t, durationSeconds, amplifier);
                return true;
            }
            case "NIGHT_VISION", "NIGHTVISION" -> {
                PotionEffectType t = resolveEffectType("NIGHT_VISION");
                if (t == null) return false;
                giveEffect(p, t, durationSeconds, amplifier);
                return true;
            }
            case "ABSORPTION" -> {
                PotionEffectType t = resolveEffectType("ABSORPTION");
                if (t == null) return false;
                giveEffect(p, t, durationSeconds, amplifier);
                return true;
            }
            default -> {
                PotionEffectType t = resolveEffectType(eff);
                if (t != null) {
                    giveEffect(p, t, durationSeconds, amplifier);
                    return true;
                }
                p.getWorld().spawnParticle(resolveParticle("INSTANT_EFFECT", "SPELL_INSTANT", "SPELL"),
                        p.getLocation().add(0, 1, 0), 12, 0.25, 0.25, 0.25, 0.02);
                p.playSound(p.getLocation(), Sound.ENTITY_PLAYER_LEVELUP, 0.7f, 1.2f);
                p.sendMessage("§7Zauber gewirkt: §e" + effectRaw);
                return true;
            }
        }
    }

    private void giveEffect(Player p, PotionEffectType type, int seconds, int amplifier) {
        int ticks = Math.max(1, seconds) * 20;
        int amp = Math.max(0, amplifier - 1);
        p.addPotionEffect(new PotionEffect(type, ticks, amp, false, true, true));
        p.getWorld().spawnParticle(resolveParticle("INSTANT_EFFECT", "SPELL_INSTANT", "SPELL"),
                p.getLocation().add(0, 1, 0), 8, 0.25, 0.25, 0.25, 0.01);
        p.playSound(p.getLocation(), Sound.ENTITY_EXPERIENCE_ORB_PICKUP, 1f, 1f);
    }

    /* ===================== Resolver ===================== */

    // robustes Attribute-Lookup für 1.20.5+ (keine Enum-API mehr)
    private static Attribute resolveAttribute(String... names) {
        for (String n : names) {
            if (n == null || n.isBlank()) continue;

            // 1) Statisches Feld versuchen (z.B. Attribute.MAX_HEALTH)
            try {
                Field f = Attribute.class.getField(n.replace('.', '_').toUpperCase(Locale.ROOT));
                Object v = f.get(null);
                if (v instanceof Attribute a) return a;
            } catch (Throwable ignored) {}

            // 2) Lookup per Key (NamespacedKey), mögliche Methodennamen abdecken
            String keyStr = n.contains(":") ? n : "minecraft:" + n.toLowerCase(Locale.ROOT);
            NamespacedKey key = NamespacedKey.fromString(keyStr);
            if (key != null) {
                for (String mname : new String[]{"getByKey", "byKey"}) {
                    try {
                        Method m = Attribute.class.getMethod(mname, NamespacedKey.class);
                        Object v = m.invoke(null, key);
                        if (v instanceof Attribute a) return a;
                    } catch (Throwable ignored) {}
                }
            }

            // 3) (Ältere API) getByName
            for (String mname : new String[]{"getByName", "byName"}) {
                try {
                    Method m = Attribute.class.getMethod(mname, String.class);
                    Object v = m.invoke(null, n);
                    if (v instanceof Attribute a) return a;
                } catch (Throwable ignored) {}
            }
        }
        return null;
    }

    private PotionEffectType resolveEffectType(String... names) {
        for (String raw : names) {
            if (raw == null || raw.isBlank()) continue;

            try {
                PotionEffectType t = PotionEffectType.getByName(raw.toUpperCase(Locale.ROOT));
                if (t != null) return t;
            } catch (Throwable ignored) {}

            try {
                NamespacedKey key = NamespacedKey.minecraft(raw.toLowerCase(Locale.ROOT));
                PotionEffectType t = PotionEffectType.getByKey(key);
                if (t != null) return t;
            } catch (Throwable ignored) {}
        }
        return null;
    }

    private Particle resolveParticle(String... names) {
        for (String n : names) {
            if (n == null) continue;
            try { return Particle.valueOf(n); } catch (Throwable ignored) {}
        }
        return Particle.ENCHANT;
    }
}