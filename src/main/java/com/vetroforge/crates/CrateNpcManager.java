// src/main/java/com/vetroforge/crates/CrateNpcManager.java
package com.vetroforge.crates;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.GradientUtil;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.*;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Entity;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Villager;
import org.bukkit.persistence.PersistentDataType;

import java.util.*;

public class CrateNpcManager {

    public static final String TAG = "vetro_crate_npc";
    private final FactionsPlugin plugin;

    /** Persistente NPC-Info (inkl. Glow-Flag) */
    public record NpcInfo(UUID uuid, String world, double x, double y, double z,
                          float yaw, float pitch, String name, boolean glow) {}

    public CrateNpcManager(FactionsPlugin plugin) { this.plugin = plugin; }

    /* ================= Public API ================= */

    /** Lädt alle in der Config hinterlegten Einträge. */
    public List<NpcInfo> listInfos() {
        List<NpcInfo> out = new ArrayList<>();
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("crate-npcs");
        if (sec == null) return out;

        for (String k : sec.getKeys(false)) {
            ConfigurationSection s = sec.getConfigurationSection(k);
            if (s == null) continue;
            try {
                UUID uuid = UUID.fromString(s.getString("uuid", k));
                String world = s.getString("world");
                double x = s.getDouble("x"), y = s.getDouble("y"), z = s.getDouble("z");
                float yaw = (float) s.getDouble("yaw"), pitch = (float) s.getDouble("pitch");
                String name = s.getString("name", "§bCrystal §fNPC");
                boolean glow = s.getBoolean("glow", false);
                out.add(new NpcInfo(uuid, world, x, y, z, yaw, pitch, name, glow));
            } catch (Throwable ignored) {}
        }
        return out;
    }

    /** Spawned alle aus der Config (UUIDs werden bei Bedarf aktualisiert). */
    public void spawnAll() {
        for (NpcInfo i : listInfos()) spawnOne(i);
    }

    /** Fügt einen neuen Crate-NPC an der Position hinzu. */
    public NpcInfo addNpc(Location loc, String rawName) {
        if (loc == null || loc.getWorld() == null) return null;
        String pretty = formatName(rawName);
        Villager v = spawnVillager(center(loc), pretty, false);
        if (v == null) return null;

        NpcInfo info = new NpcInfo(
                v.getUniqueId(),
                loc.getWorld().getName(),
                loc.getX(), loc.getY(), loc.getZ(),
                loc.getYaw(), loc.getPitch(),
                pretty,
                false
        );
        save(info);
        return info;
    }

    /** Entfernt einen NPC (Welt & Config). */
    public boolean removeNpc(UUID uuid) {
        Entity found = null;
        for (World w : Bukkit.getWorlds()) {
            Entity e = w.getEntity(uuid);
            if (e != null) { e.remove(); found = e; break; }
        }
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("crate-npcs");
        if (sec != null) {
            sec.set(uuid.toString(), null);
            for (String k : new ArrayList<>(sec.getKeys(false))) {
                if (uuid.toString().equalsIgnoreCase(sec.getString(k + ".uuid"))) sec.set(k, null);
            }
            plugin.saveConfig();
        }
        return found != null;
    }

    /** Benennt einen vorhandenen NPC (Welt & Config) um. */
    public boolean rename(UUID uuid, String newRawName) {
        String pretty = formatName(newRawName);
        NpcInfo old = getInfo(uuid);
        if (old == null) return false;

        // Welt
        for (World w : Bukkit.getWorlds()) {
            Entity e = w.getEntity(uuid);
            if (e != null) { setDisplayName(e, pretty); setNameVisible(e, true); break; }
        }
        // Config
        save(new NpcInfo(old.uuid(), old.world(), old.x(), old.y(), old.z(), old.yaw(), old.pitch(), pretty, old.glow()));
        return true;
    }

    /** Glow an/aus (Welt & Config). */
    public boolean setGlow(UUID uuid, boolean glow) {
        boolean applied = false;
        for (World w : Bukkit.getWorlds()) {
            Entity e = w.getEntity(uuid);
            if (e != null) { try { e.setGlowing(glow); } catch (Throwable ignored) {} applied = true; break; }
        }
        NpcInfo old = getInfo(uuid);
        if (old != null) save(new NpcInfo(old.uuid(), old.world(), old.x(), old.y(), old.z(), old.yaw(), old.pitch(), old.name(), glow));
        return applied;
    }

    /** Prüft, ob die Entity ein Crate-NPC ist (Tag/PDC). */
    public boolean isCrateNpc(Entity e) {
        if (e == null) return false;
        if (e.getScoreboardTags().contains(TAG)) return true;
        try {
            Byte b = e.getPersistentDataContainer().get(new NamespacedKey(plugin, TAG), PersistentDataType.BYTE);
            return b != null && b == (byte) 1;
        } catch (Throwable ignored) { return false; }
    }

    /* ================= intern ================= */

    private void spawnOne(NpcInfo i) {
        World w = Bukkit.getWorld(i.world());
        if (w == null) return;

        Location loc = center(new Location(w, i.x(), i.y(), i.z(), i.yaw(), i.pitch()));
        Entity ex = w.getEntity(i.uuid());
        if (ex != null && !ex.isDead()) {
            tagEntity(ex);
            setDisplayName(ex, i.name());
            setNameVisible(ex, true);
            try { ex.setGlowing(i.glow()); } catch (Throwable ignored) {}
            return;
        }

        // Chunk nur (noch) nicht geladen, statt der NPC wirklich verloren? Dann nicht
        // erneut spawnen -- sonst entsteht bei jedem Neustart eine Dublette.
        if (!w.isChunkLoaded(loc.getBlockX() >> 4, loc.getBlockZ() >> 4)) {
            return;
        }

        Villager v = spawnVillager(loc, i.name(), i.glow());
        if (v == null) return;

        // UUID aktualisieren
        save(new NpcInfo(v.getUniqueId(), i.world(), i.x(), i.y(), i.z(), i.yaw(), i.pitch(), i.name(), i.glow()));
    }

    private Villager spawnVillager(Location loc, String legacyName, boolean glow) {
        try {
            Villager v = loc.getWorld().spawn(loc, Villager.class, ent -> {
                ent.setAI(false);
                ent.setInvulnerable(true);
                ent.setSilent(true);
                ent.setCanPickupItems(false);
                ent.setCollidable(false);
                try { ent.setGravity(false); } catch (Throwable ignored) {}
                ent.setRemoveWhenFarAway(false);

                // Unbrauchbarer Villager-Look
                try { ent.setProfession(Villager.Profession.NITWIT); } catch (Throwable ignored) {}
                try { ent.setVillagerType(Villager.Type.PLAINS); } catch (Throwable ignored) {}
                try { ent.setVillagerLevel(1); } catch (Throwable ignored) {}
                try { ent.setAgeLock(true); } catch (Throwable ignored) {}

                setDisplayName(ent, legacyName);
                setNameVisible(ent, true);
                try { ent.setGlowing(glow); } catch (Throwable ignored) {}
            });
            tagEntity(v);
            return v;
        } catch (Throwable t) {
            plugin.getLogger().warning("Crate-NPC konnte nicht gespawnt werden: " + t.getMessage());
            return null;
        }
    }

    private void tagEntity(Entity e) {
        try { e.getPersistentDataContainer().set(new NamespacedKey(plugin, TAG), PersistentDataType.BYTE, (byte) 1); } catch (Throwable ignored) {}
        e.addScoreboardTag(TAG);
    }

    private void save(NpcInfo i) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("crate-npcs");
        if (sec == null) sec = plugin.getConfig().createSection("crate-npcs");

        String key = i.uuid().toString();
        sec.set(key + ".uuid", i.uuid().toString());
        sec.set(key + ".world", i.world());
        sec.set(key + ".x", i.x());
        sec.set(key + ".y", i.y());
        sec.set(key + ".z", i.z());
        sec.set(key + ".yaw", i.yaw());
        sec.set(key + ".pitch", i.pitch());
        sec.set(key + ".name", i.name());
        sec.set(key + ".glow", i.glow());

        plugin.saveConfig();
    }

    private NpcInfo getInfo(UUID uuid) {
        ConfigurationSection sec = plugin.getConfig().getConfigurationSection("crate-npcs");
        if (sec == null) return null;
        ConfigurationSection s = sec.getConfigurationSection(uuid.toString());
        if (s == null) {
            for (String k : sec.getKeys(false)) {
                if (uuid.toString().equalsIgnoreCase(sec.getString(k + ".uuid"))) { s = sec.getConfigurationSection(k); break; }
            }
            if (s == null) return null;
        }
        try {
            return new NpcInfo(
                    uuid,
                    s.getString("world"),
                    s.getDouble("x"), s.getDouble("y"), s.getDouble("z"),
                    (float) s.getDouble("yaw"), (float) s.getDouble("pitch"),
                    s.getString("name", "§bCrystal §fNPC"),
                    s.getBoolean("glow", false)
            );
        } catch (Throwable t) { return null; }
    }

    /* ===== Name/Format ===== */

    /** Automatisches Grün→Weiß + Bold, wenn keine Codes vorhanden. */
    private String formatName(String raw) {
        String s = raw == null ? "" : raw.trim();
        if (s.isEmpty()) s = "Crystal NPC";
        boolean hasCodes = s.contains("§") || s.contains("&");
        if (!hasCodes) s = GradientUtil.npc(" " + s + " ");
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    private void setDisplayName(Entity e, String legacyColored) {
        Component display = LegacyComponentSerializer.legacySection().deserialize(legacyColored == null ? "" : legacyColored);
        try { e.customName(display); return; } catch (Throwable ignored) {}
        try { e.setCustomName(legacyColored); } catch (Throwable ignored) {}
    }

    private void setNameVisible(Entity e, boolean visible) {
        if (e instanceof LivingEntity le) { le.setCustomNameVisible(visible); return; }
        try { e.setCustomNameVisible(visible); } catch (Throwable ignored) {}
    }

    private Location center(Location l) {
        return new Location(l.getWorld(),
                Math.floor(l.getX()) + 0.5,
                l.getY(),
                Math.floor(l.getZ()) + 0.5,
                l.getYaw(), l.getPitch());
    }
}
