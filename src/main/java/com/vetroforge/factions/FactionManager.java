// src/main/java/com/vetroforge/factions/FactionManager.java
package com.vetroforge.factions;

import org.bukkit.Location;

import java.util.*;

public class FactionManager {

    public enum Role {
        NONE,
        MEMBER,
        OFFICER,
        LEADER
    }

    private final Map<String, Set<UUID>> factions = new HashMap<>();
    private final Map<UUID, String> playerFaction = new HashMap<>();
    private final Map<UUID, Role> roles = new HashMap<>();
    private final Map<String, Set<String>> allies = new HashMap<>();
    private final Map<String, Location> homes = new HashMap<>();
    private final Map<String, Long> createdAt = new HashMap<>();

    /* ================= CREATE ================= */

    public synchronized boolean createFaction(String name, UUID leader) {
        if (factions.containsKey(name)) return false;

        factions.put(name, new HashSet<>());
        factions.get(name).add(leader);

        playerFaction.put(leader, name);
        roles.put(leader, Role.LEADER);
        createdAt.put(name, System.currentTimeMillis());
        return true;
    }

    public synchronized boolean disband(String name) {
        if (!factions.containsKey(name)) return false;

        for (UUID u : factions.get(name)) {
            playerFaction.remove(u);
            roles.remove(u);
        }

        factions.remove(name);
        allies.remove(name);
        homes.remove(name);
        createdAt.remove(name);
        return true;
    }

    /* ================= BASIC ================= */

    public synchronized String getFactionOf(UUID uuid) {
        return playerFaction.get(uuid);
    }

    public synchronized List<String> getAllFactionNames() {
        return new ArrayList<>(factions.keySet());
    }

    public String getDisplayName(String f) {
        return f;
    }

    /* ================= MEMBERS ================= */

    public synchronized List<UUID> getMembers(String faction) {
        return new ArrayList<>(factions.getOrDefault(faction, new HashSet<>()));
    }

    public synchronized boolean addMember(String faction, UUID uuid) {
        if (!factions.containsKey(faction)) return false;

        factions.get(faction).add(uuid);
        playerFaction.put(uuid, faction);
        roles.put(uuid, Role.MEMBER);
        return true;
    }

    public synchronized boolean leave(UUID uuid) {
        String f = playerFaction.get(uuid);
        if (f == null) return false;

        Set<UUID> members = factions.get(f);
        if (isLeader(uuid) && members != null && members.size() > 1) return false;

        if (members != null) members.remove(uuid);
        playerFaction.remove(uuid);
        roles.remove(uuid);

        if (members == null || members.isEmpty()) {
            factions.remove(f);
            allies.remove(f);
            homes.remove(f);
            createdAt.remove(f);
        }
        return true;
    }

    public synchronized boolean kick(UUID actor, UUID target) {
        String f = getFactionOf(actor);
        if (f == null || !f.equals(getFactionOf(target))) return false;
        if (actor.equals(target)) return false;

        Role actorRole = getRole(actor);
        Role targetRole = getRole(target);
        if (targetRole == Role.LEADER) return false;
        if (actorRole != Role.LEADER && actorRole != Role.OFFICER) return false;
        if (actorRole == Role.OFFICER && targetRole == Role.OFFICER) return false;

        factions.get(f).remove(target);
        playerFaction.remove(target);
        roles.remove(target);
        return true;
    }

    public synchronized boolean removeMember(UUID target) {
        String faction = getFactionOf(target);
        if (faction == null) return false;
        factions.get(faction).remove(target);
        playerFaction.remove(target);
        roles.remove(target);
        return true;
    }

    /* ================= ROLES ================= */

    public synchronized Role getRole(UUID uuid) {
        return roles.getOrDefault(uuid, Role.NONE);
    }

    public synchronized boolean isLeader(UUID uuid) {
        return getRole(uuid) == Role.LEADER;
    }
    public void importSnapshot(Snapshot snap) {
        applySnapshot(snap);
    }
    public synchronized boolean promote(UUID actor, UUID target) {
        String f = getFactionOf(actor);
        if (f == null || !f.equals(getFactionOf(target)) || !isLeader(actor)) return false;
        if (actor.equals(target) || getRole(target) != Role.MEMBER) return false;
        roles.put(target, Role.OFFICER);
        return true;
    }

    public synchronized boolean demote(UUID actor, UUID target) {
        String f = getFactionOf(actor);
        if (f == null || !f.equals(getFactionOf(target)) || !isLeader(actor)) return false;
        if (actor.equals(target) || getRole(target) != Role.OFFICER) return false;
        roles.put(target, Role.MEMBER);
        return true;
    }

    public synchronized boolean setLeader(UUID actor, UUID target) {
        String f = getFactionOf(actor);
        if (f == null || !f.equals(getFactionOf(target)) || !isLeader(actor)) return false;
        if (actor.equals(target)) return false;

        roles.put(actor, Role.MEMBER);
        roles.put(target, Role.LEADER);
        return true;
    }

    /* ================= ALLIES ================= */

    public synchronized Set<String> getAllies(String faction) {
        return new HashSet<>(allies.getOrDefault(faction, new HashSet<>()));
    }

    public synchronized void setAllied(String f1, String f2, boolean state) {
        allies.computeIfAbsent(f1, k -> new HashSet<>());
        allies.computeIfAbsent(f2, k -> new HashSet<>());

        if (state) {
            allies.get(f1).add(f2);
            allies.get(f2).add(f1);
        } else {
            allies.get(f1).remove(f2);
            allies.get(f2).remove(f1);
        }
    }

    /* ================= HOME ================= */

    public synchronized void setHome(String faction, Location loc) {
        homes.put(faction, loc);
    }

    public synchronized Location getHome(String faction) {
        return homes.get(faction);
    }
    // ===== SNAPSHOT SYSTEM (für YAML Store) =====

    public static class Snapshot {
        public Map<String, FactionDTO> factions = new HashMap<>();
        public Map<String, List<String>> allies = new HashMap<>();
    }

    public static class FactionDTO {
        public String name;
        public UUID leader;
        public Long createdAt;

        public Set<UUID> officers = new HashSet<>();
        public Set<UUID> members = new HashSet<>();

        public SerializableLocation home;
    }

    public static class SerializableLocation {
        public String world;
        public double x, y, z;
        public float yaw, pitch;

        public org.bukkit.Location toBukkit() {
            var w = org.bukkit.Bukkit.getWorld(world);
            if (w == null) return null;
            return new org.bukkit.Location(w, x, y, z, yaw, pitch);
        }

        public static SerializableLocation from(org.bukkit.Location loc) {
            if (loc == null) return null;
            SerializableLocation s = new SerializableLocation();
            s.world = loc.getWorld().getName();
            s.x = loc.getX();
            s.y = loc.getY();
            s.z = loc.getZ();
            s.yaw = loc.getYaw();
            s.pitch = loc.getPitch();
            return s;
        }
    }
    public String getFactionAt(org.bukkit.Location loc) {
        if (loc == null || loc.getWorld() == null) return null;

        String worldName = loc.getWorld().getName();

        // faction_<name>
        if (!worldName.startsWith("faction_")) return null;

        return worldName.substring("faction_".length());
    }

    public synchronized String getRandomFaction() {
        if (factions.isEmpty()) return null;
        return new java.util.ArrayList<>(factions.keySet())
                .get(new java.util.Random().nextInt(factions.size()));
    }

    public synchronized void applySnapshot(Snapshot snap) {
        factions.clear();
        playerFaction.clear();
        roles.clear();
        allies.clear();
        homes.clear();
        createdAt.clear();

        if (snap == null) return;

        for (FactionDTO dto : snap.factions.values()) {

            factions.put(dto.name, new HashSet<>());
            createdAt.put(dto.name, dto.createdAt != null ? dto.createdAt : System.currentTimeMillis());

            if (dto.leader != null) {
                factions.get(dto.name).add(dto.leader);
                playerFaction.put(dto.leader, dto.name);
                roles.put(dto.leader, Role.LEADER);
            }

            for (UUID u : dto.officers) {
                factions.get(dto.name).add(u);
                playerFaction.put(u, dto.name);
                roles.put(u, Role.OFFICER);
            }

            for (UUID u : dto.members) {
                factions.get(dto.name).add(u);
                playerFaction.put(u, dto.name);
                roles.put(u, Role.MEMBER);
            }

            if (dto.home != null) {
                homes.put(dto.name, dto.home.toBukkit());
            }
        }

        if (snap.allies != null) {
            for (Map.Entry<String, List<String>> e : snap.allies.entrySet()) {
                allies.put(e.getKey(), new HashSet<>(e.getValue()));
            }
        }
    }
    public synchronized Snapshot exportSnapshot() {
        Snapshot snap = new Snapshot();

        for (String name : factions.keySet()) {

            FactionDTO dto = new FactionDTO();
            dto.name = name;

            Set<UUID> members = factions.getOrDefault(name, new HashSet<>());

            for (UUID u : members) {
                Role r = roles.getOrDefault(u, Role.MEMBER);

                if (r == Role.LEADER) {
                    dto.leader = u;
                } else if (r == Role.OFFICER) {
                    dto.officers.add(u);
                } else {
                    dto.members.add(u);
                }
            }

            Location home = homes.get(name);
            if (home != null) {
                dto.home = SerializableLocation.from(home);
            }

            dto.createdAt = createdAt.getOrDefault(name, System.currentTimeMillis());

            snap.factions.put(name, dto);
        }

        for (Map.Entry<String, Set<String>> e : allies.entrySet()) {
            snap.allies.put(e.getKey(), new ArrayList<>(e.getValue()));
        }

        return snap;
    }
}
