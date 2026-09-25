// src/main/java/com/vetroforge/warzone/WarzoneRegionManager.java
package com.vetroforge.warzone;

import com.vetroforge.FactionsPlugin;
import org.bukkit.*;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

public class WarzoneRegionManager {

    public enum Shape { CIRCLE, CUBOID }

    private final FactionsPlugin plugin;

    private Shape shape = Shape.CIRCLE;
    private String worldName = "world";

    // circle
    private Location center;
    private double radius = 150;

    // cuboid
    private Location pos1, pos2;

    // Wenn aktiv, ist nur diese Box sicherer Spawn. Alles außerhalb ist Warzone.
    private boolean spawnSafeEnabled = false;
    private Location spawnSafePos1, spawnSafePos2;

    public WarzoneRegionManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        loadFromConfig();
    }

    /* ===== setters (für Commands) ===== */
    public void setShape(Shape s){ this.shape = (s == null ? Shape.CIRCLE : s); }
    public void setCircleCenter(Player p){ this.center = p.getLocation(); this.worldName = center.getWorld().getName(); }
    public void setCircleRadius(double r){ this.radius = Math.max(1, r); }
    public void setPos1(Player p){ this.pos1 = p.getLocation(); this.worldName = pos1.getWorld().getName(); }
    public void setPos2(Player p){ this.pos2 = p.getLocation(); this.worldName = pos2.getWorld().getName(); }
    public void setSpawnSafePos1(Player p){ setSpawnSafePosition(true, p); }
    public void setSpawnSafePos2(Player p){ setSpawnSafePosition(false, p); }

    private void setSpawnSafePosition(boolean first, Player p) {
        Location location = p.getLocation();
        if (first) this.spawnSafePos1 = location;
        else this.spawnSafePos2 = location;
        this.worldName = location.getWorld().getName();
        this.spawnSafeEnabled = true;
        saveSpawnSafeToConfig();
    }

    public void clearSpawnSafe(){
        this.spawnSafeEnabled = false;
        this.spawnSafePos1 = null;
        this.spawnSafePos2 = null;
        plugin.getConfig().set("warzone.spawnSafe", null);
        plugin.saveConfig();
    }

    /** Speichert aktuelle Region in config. */
    public void saveToConfig(){
        FileConfiguration c = plugin.getConfig();
        c.set("warzone.shape", shape.name());
        c.set("warzone.world", worldName);
        if (shape == Shape.CIRCLE) {
            if (center != null) {
                c.set("warzone.center.x", center.getX());
                c.set("warzone.center.y", center.getY());
                c.set("warzone.center.z", center.getZ());
            }
            c.set("warzone.radius", radius);
        } else {
            writeLoc(c, "warzone.cuboid.pos1", pos1);
            writeLoc(c, "warzone.cuboid.pos2", pos2);
        }
        plugin.saveConfig();
    }

    public void saveSpawnSafeToConfig(){
        FileConfiguration c = plugin.getConfig();
        c.set("warzone.spawnSafe.enabled", spawnSafeEnabled);
        c.set("warzone.world", worldName);
        c.set("lobby.world", worldName);
        writeLoc(c, "warzone.spawnSafe.pos1", spawnSafePos1);
        writeLoc(c, "warzone.spawnSafe.pos2", spawnSafePos2);
        plugin.saveConfig();
    }

    public void commitCuboid(Player p) {
        p.sendMessage("§aWarzone-Cuboid gesetzt.");
    }

    /* ===== getters ===== */
    public boolean isConfigured(){
        if (shape == Shape.CIRCLE) return center != null && radius > 0 && worldName != null;
        return pos1 != null && pos2 != null && worldName != null;
    }
    public Shape getShape(){ return shape; }
    public String getWorldName(){ return worldName; }
    public Location getCenter(){ return center; }
    public double getRadius(){ return radius; }
    public Cuboid getCuboid(){ return (pos1 != null && pos2 != null) ? new Cuboid(pos1, pos2) : null; }
    public boolean isSpawnSafeMode(){ return spawnSafeEnabled && spawnSafePos1 != null && spawnSafePos2 != null; }
    public Cuboid getSpawnSafeCuboid(){ return isSpawnSafeMode() ? new Cuboid(spawnSafePos1, spawnSafePos2) : null; }
    public boolean isManagedWorld(Location loc) {
        return loc != null
                && loc.getWorld() != null
                && worldName != null
                && loc.getWorld().getName().equalsIgnoreCase(worldName);
    }

    public boolean isLobbySpawnArea(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (isSpawnSafeMode()) {
            return isSpawn(loc);
        }
        return isInsideFallbackSpawnRadius(loc);
    }

    public boolean isSpawn(Location loc) {
        if (loc == null || loc.getWorld() == null) return false;
        if (isSpawnSafeMode()) {
            Cuboid safe = getSpawnSafeCuboid();
            return safe != null && safe.containsXZ(loc);
        }
        return isInsideFallbackSpawnRadius(loc);
    }

    public String flagAt(Location loc) {
        if (isLobbySpawnArea(loc)) return "SPAWN";
        if (isWarzone(loc)) return "WARZONE";
        return "WILD";
    }

    public boolean isWarzone(Location loc) {
        return isInside(loc) && !isLobbySpawnArea(loc);
    }

    /**
     * Alle Orte, an denen PvP-Schaden erlaubt ist: die konfigurierte Warzone-Welt
     * UND jede Faction-Raid-Welt ("faction_&lt;name&gt;"), da Raids dort stattfinden
     * und nicht in der einen konfigurierten Warzone-Welt.
     */
    public boolean isPvpZone(Location loc) {
        if (isWarzone(loc)) return true;
        World world = loc == null ? null : loc.getWorld();
        return world != null && world.getName().startsWith("faction_");
    }

    /* ===== Logik ===== */
    public boolean isInside(Location loc){
        if (!isManagedWorld(loc)) return false;

        if (isSpawnSafeMode()) {
            Cuboid safe = getSpawnSafeCuboid();
            return safe != null && !safe.containsXZ(loc);
        }

        if (shape == Shape.CIRCLE) {
            if (center == null) return false;
            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            return dx*dx + dz*dz <= radius*radius;
        } else {
            if (pos1 == null || pos2 == null) return false;
            return new Cuboid(pos1, pos2).contains(loc);
        }
    }

    public double depth(Location loc) {
        if (!isWarzone(loc)) return 0.0D;
        if (isSpawnSafeMode()) {
            Cuboid safe = getSpawnSafeCuboid();
            if (safe == null) return 0.0D;

            double dx = 0.0D;
            if (loc.getX() < safe.min.getX()) dx = safe.min.getX() - loc.getX();
            else if (loc.getX() > safe.max.getX()) dx = loc.getX() - safe.max.getX();

            double dz = 0.0D;
            if (loc.getZ() < safe.min.getZ()) dz = safe.min.getZ() - loc.getZ();
            else if (loc.getZ() > safe.max.getZ()) dz = loc.getZ() - safe.max.getZ();

            return Math.sqrt(dx * dx + dz * dz);
        }

        if (shape == Shape.CIRCLE) {
            double dx = loc.getX() - center.getX();
            double dz = loc.getZ() - center.getZ();
            return Math.max(0.0D, radius - Math.sqrt(dx * dx + dz * dz));
        }

        Cuboid cuboid = getCuboid();
        if (cuboid == null) return 0.0D;
        double xDepth = Math.min(loc.getX() - cuboid.min.getX(), cuboid.max.getX() - loc.getX());
        double zDepth = Math.min(loc.getZ() - cuboid.min.getZ(), cuboid.max.getZ() - loc.getZ());
        return Math.max(0.0D, Math.min(xDepth, zDepth));
    }

    public int disparityLevel(Location loc) {
        if (!isWarzone(loc)) return 1;
        double blocksPerLevel = Math.max(1.0D, plugin.getConfig().getDouble("warzone.disparity.blocksPerLevel", 25.0D));
        int cap = Math.max(1, plugin.getConfig().getInt("warzone.disparity.maxLevel", 100));
        return Math.max(1, Math.min(cap, 1 + (int) Math.floor(depth(loc) / blocksPerLevel)));
    }

    private boolean isInsideFallbackSpawnRadius(Location loc) {
        Location fallbackSpawn = plugin.getLobbyManager() == null ? null : plugin.getLobbyManager().getSpawn();
        if (fallbackSpawn == null || fallbackSpawn.getWorld() == null) return false;
        if (!fallbackSpawn.getWorld().equals(loc.getWorld())) return false;

        double radius = Math.max(4.0D, plugin.getConfig().getDouble("lobby.spawnRadius", 48.0D));
        double dx = loc.getX() - fallbackSpawn.getX();
        double dz = loc.getZ() - fallbackSpawn.getZ();
        return dx * dx + dz * dz <= radius * radius;
    }

    /* ===== config I/O ===== */
    private void loadFromConfig(){
        FileConfiguration c = plugin.getConfig();
        try { this.shape = Shape.valueOf(c.getString("warzone.shape","CIRCLE").toUpperCase()); }
        catch (Exception ignored){ this.shape = Shape.CIRCLE; }

        this.worldName = c.getString("warzone.world", "world");
        World w = Bukkit.getWorld(worldName);
        if (w == null) return;

        this.spawnSafeEnabled = c.getBoolean("warzone.spawnSafe.enabled", false);
        this.spawnSafePos1 = readLoc(c, w, "warzone.spawnSafe.pos1");
        this.spawnSafePos2 = readLoc(c, w, "warzone.spawnSafe.pos2");

        if (shape == Shape.CIRCLE) {
            double x = c.getDouble("warzone.center.x", 0.5);
            double y = c.getDouble("warzone.center.y", 64);
            double z = c.getDouble("warzone.center.z", 0.5);
            this.center = new Location(w, x, y, z);
            this.radius = Math.max(1, c.getDouble("warzone.radius", 150));
        } else {
            this.pos1 = readLoc(c, w, "warzone.cuboid.pos1");
            this.pos2 = readLoc(c, w, "warzone.cuboid.pos2");
        }
    }

    private void writeLoc(FileConfiguration c, String base, Location l){
        if (l == null || l.getWorld() == null) return;
        c.set(base+".world", l.getWorld().getName());
        c.set(base+".x", l.getX());
        c.set(base+".y", l.getY());
        c.set(base+".z", l.getZ());
    }
    private Location readLoc(FileConfiguration c, World defWorld, String base){
        if (!c.isSet(base+".x")) return null;
        World w = defWorld;
        String wn = c.getString(base+".world", defWorld.getName());
        if (wn != null) {
            World w2 = Bukkit.getWorld(wn);
            if (w2 != null) w = w2;
        }
        double x = c.getDouble(base+".x");
        double y = c.getDouble(base+".y");
        double z = c.getDouble(base+".z");
        return new Location(w, x, y, z);
    }

    /* ===== kleine Hilfsklasse ===== */
    public static final class Cuboid {
        public final Location min, max;
        public Cuboid(Location a, Location b){
            World w = a.getWorld();
            double minX = Math.min(a.getX(), b.getX());
            double minY = Math.min(a.getY(), b.getY());
            double minZ = Math.min(a.getZ(), b.getZ());
            double maxX = Math.max(a.getX(), b.getX());
            double maxY = Math.max(a.getY(), b.getY());
            double maxZ = Math.max(a.getZ(), b.getZ());
            this.min = new Location(w, minX, minY, minZ);
            this.max = new Location(w, maxX, maxY, maxZ);
        }
        public boolean contains(Location l){
            if (l == null || l.getWorld() == null) return false;
            if (!l.getWorld().equals(min.getWorld())) return false;
            return l.getX()>=min.getX() && l.getX()<=max.getX()
                    && l.getY()>=min.getY() && l.getY()<=max.getY()
                    && l.getZ()>=min.getZ() && l.getZ()<=max.getZ();
        }

        public boolean containsXZ(Location l){
            if (l == null || l.getWorld() == null) return false;
            if (!l.getWorld().equals(min.getWorld())) return false;
            return l.getX()>=min.getX() && l.getX()<=max.getX()
                    && l.getZ()>=min.getZ() && l.getZ()<=max.getZ();
        }
    }
}
