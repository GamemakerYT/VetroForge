package com.vetroforge.util;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.EntityType;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/** Leichtgewichtige Helper für Hologramm-Zeilen (ArmorStands). */
public final class HologramUtil {
    private HologramUtil() {}

    /**
     * Spawnt mehrere Hologramm-Zeilen (ArmorStands) über/unter der Basis-Location.
     * - X/Z werden auf Blockzentrum gesnappt (+0.5)
     * - Y = obere Zeile, jede weitere -0.27
     * - Farbcodes mit '&' werden unterstützt
     */
    public static List<ArmorStand> spawnLines(Location base, List<String> lines) {
        List<ArmorStand> list = new ArrayList<>();
        if (base == null || base.getWorld() == null || lines == null || lines.isEmpty()) return list;

        World w = base.getWorld();
        Location anchor = base.clone();
        anchor.setX(Math.floor(anchor.getX()) + 0.5);
        anchor.setZ(Math.floor(anchor.getZ()) + 0.5);

        double dy = 0.0; // oberste Zeile
        for (String raw : lines) {
            String line = (raw == null) ? "" : raw;

            Location l = anchor.clone().add(0, dy, 0);
            ArmorStand as = (ArmorStand) w.spawnEntity(l, EntityType.ARMOR_STAND);

            // Grund-Setup
            as.setMarker(true);
            as.setInvisible(true);
            as.setSmall(true);
            as.setGravity(false);
            as.setBasePlate(false);
            as.setCustomNameVisible(true);
            safeCall(as, "setCanTick", false);     // Paper ≥1.20
            safeCall(as, "setPersistent", true);   // despawnt nicht natürlich

            // Name setzen – bevorzugt Adventure, Fallback Legacy
            setNameCompat(as, line);

            list.add(as);
            dy -= 0.27;
        }
        return list;
    }

    /** Entfernt alle ArmorStands einer Hologramm-Gruppe. */
    public static void removeAll(List<ArmorStand> stands) {
        if (stands == null) return;
        for (ArmorStand as : stands) {
            if (as != null && !as.isDead()) {
                try { as.remove(); } catch (Throwable ignored) {}
            }
        }
        stands.clear();
    }

    /* ========= interne Helfer ========= */

    private static void setNameCompat(ArmorStand as, String ampersandText) {
        // Adventure bevorzugen
        try {
            Component comp = LegacyComponentSerializer.legacyAmpersand().deserialize(ampersandText);
            ArmorStand.class.getMethod("customName", Component.class).invoke(as, comp);
            return;
        } catch (NoSuchMethodException ignored) {
            // Spigot/Older
        } catch (Throwable ignored) {}

        // Fallback: klassische Legacy-Farbcodes
        String legacy = ChatColor.translateAlternateColorCodes('&', ampersandText);
        try { as.setCustomName(legacy); } catch (Throwable ignored) {}
    }

    private static void safeCall(ArmorStand as, String method, boolean arg) {
        try {
            Method m = as.getClass().getMethod(method, boolean.class);
            m.invoke(as, arg);
        } catch (Throwable ignored) {}
    }
}