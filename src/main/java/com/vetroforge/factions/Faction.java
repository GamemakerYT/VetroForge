// src/main/java/com/vetroforge/factions/Faction.java
package com.vetroforge.factions;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

import java.util.*;

public class Faction {

    private String name;
    private int coins;
    private final Set<UUID> members = new HashSet<UUID>();

    // Hinweis: Du nutzt extern bereits einen FactionRelationsManager.
    // Diese Map bleibt für einfache Kompatibilität drin/optional.
    private final Map<String, String> relations = new HashMap<String, String>(); // otherName -> ally|enemy|truce|neutral

    private Location home;

    public Faction(String name) { this.name = name; }

    /* ===== Name ===== */
    public String getName() { return name; }
    public void setName(String n){ this.name = n; }

    /* ===== Coins ===== */
    public int getCoins(){ return coins; }
    public void setCoins(int coins) { this.coins = Math.max(0, coins); }

    /** +amount (<=0 wird ignoriert) */
    public void addCoins(int amount){
        if (amount <= 0) return;
        this.coins = Math.max(0, this.coins + amount);
    }

    /** -amount; liefert true, wenn abgezogen werden konnte */
    public boolean takeCoins(int amount){
        if (amount <= 0) return true;
        if (this.coins < amount) return false;
        this.coins -= amount;
        return true;
    }

    /* ===== Members ===== */
    /** Unmodifiable-View */
    public Set<UUID> getMembers(){ return Collections.unmodifiableSet(members); }

    public boolean addMember(UUID u){ return members.add(u); }
    public boolean removeMember(UUID u){ return members.remove(u); }

    /** Bequemer Alias, falls du plural aufrufst */
    public boolean addMembers(Collection<UUID> ids){
        boolean changed = false;
        if (ids == null) return false;
        for (UUID id : ids) if (members.add(id)) changed = true;
        return changed;
    }

    /* ===== Relations (optional – nutze sonst FactionRelationsManager) ===== */
    /** Unmodifiable-View */
    public Map<String,String> getRelations(){
        Map<String,String> out = new HashMap<String,String>();
        for (Map.Entry<String,String> e : relations.entrySet()) {
            out.put(e.getKey(), e.getValue());
        }
        return Collections.unmodifiableMap(out);
    }

    /** Set single relation */
    public void setRelation(String other, String type){
        if (other == null || type == null) return;
        relations.put(other.toLowerCase(Locale.ROOT), type.toLowerCase(Locale.ROOT));
    }

    /** Bulk setter (Alias, da du `setRelations` erwähnt hast) */
    public void setRelations(Map<String,String> newMap){
        relations.clear();
        if (newMap != null) {
            for (Map.Entry<String,String> e : newMap.entrySet()) {
                if (e.getKey() != null && e.getValue() != null) {
                    relations.put(e.getKey().toLowerCase(Locale.ROOT), e.getValue().toLowerCase(Locale.ROOT));
                }
            }
        }
    }

    public String getRelation(String other){
        if (other == null) return "neutral";
        return relations.getOrDefault(other.toLowerCase(Locale.ROOT), "neutral");
    }

    /* ===== Home ===== */
    public Location getHome(){ return home; }
    public void setHome(Location l){ this.home = l; }

    /* ===== Serialization helpers ===== */
    public static String locToString(Location l){
        if (l == null || l.getWorld() == null) return null;
        return l.getWorld().getName()+":"+l.getX()+":"+l.getY()+":"+l.getZ()+":"+l.getYaw()+":"+l.getPitch();
        // world:x:y:z:yaw:pitch
    }

    public static Location stringToLoc(String s){
        if (s == null || s.isEmpty()) return null;
        String[] p = s.split(":");
        if (p.length < 4) return null;
        World w = Bukkit.getWorld(p[0]);
        if (w == null) return null;
        double x = Double.parseDouble(p[1]);
        double y = Double.parseDouble(p[2]);
        double z = Double.parseDouble(p[3]);
        float yaw = p.length >= 5 ? Float.parseFloat(p[4]) : 0f;
        float pitch = p.length >= 6 ? Float.parseFloat(p[5]) : 0f;
        return new Location(w, x, y, z, yaw, pitch);
    }
}