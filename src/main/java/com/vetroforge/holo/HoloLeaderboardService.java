package com.vetroforge.holo;

import com.vetroforge.FactionsPlugin;
import com.vetroforge.util.HologramUtil;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.ArmorStand;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

/**
 * Zentraler Manager für mehrere Hologramme:
 *  - /holo set <Type>  → neues Holo mit Auto-Refresh
 *  - /holo remove <id|nearest>
 *  - /holo list / tp / refresh / clearall
 *
 * Persistiert Position+Typ in holograms.yml, spawnt beim nächsten Start neu.
 */
public class HoloLeaderboardService {

    public enum Board {
        TopFactions, TopCrystals, Warzone, TopKills, TopPrestige;

        public static Board parse(String s) {
            if (s == null) return null;
            String n = s.trim().toLowerCase(Locale.ROOT);
            switch (n) {
                case "topfaction":
                case "topfactions":  return TopFactions;
                case "topcrystal":
                case "topcrystals":  return TopCrystals;
                case "warzone":      return Warzone;
                case "topkills":     return TopKills;
                case "topprestige":  return TopPrestige;
                default:             return null;
            }
        }
    }

    private static final class Holo {
        final UUID id = UUID.randomUUID();
        final Board type;
        Location base;                   // Oberste Zeile
        List<ArmorStand> stands = new ArrayList<>();
        Holo(Board type, Location base){ this.type = type; this.base = snap(base); }
    }

    private final FactionsPlugin plugin;
    private final Map<UUID, Holo> holos = new LinkedHashMap<>();
    private BukkitTask refresher;

    private final File storeFile;
    private YamlConfiguration store;

    public HoloLeaderboardService(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.storeFile = new File(plugin.getDataFolder(), "holograms.yml");
        this.store = YamlConfiguration.loadConfiguration(storeFile);

        loadAndSpawn();
        startAutoRefresh();
    }

    /* ======================= Public API ======================= */

    public UUID set(Location base, Board type) {
        Holo h = new Holo(type, base);
        h.stands = HologramUtil.spawnLines(h.base, render(type));
        holos.put(h.id, h);
        saveAll();
        return h.id;
    }

    public boolean remove(UUID id) {
        Holo h = holos.remove(id);
        if (h == null) return false;
        HologramUtil.removeAll(h.stands);
        saveAll();
        return true;
    }

    public UUID removeNearest(Location ref, double radius) {
        Holo nearest = nearest(ref, radius);
        if (nearest == null) return null;
        remove(nearest.id);
        return nearest.id;
    }

    public List<String> list() {
        List<String> out = new ArrayList<>();
        for (Holo h : holos.values()) {
            Location b = h.base;
            out.add(h.id + " §7: §e" + h.type + " §7at §f" + fmt(b));
        }
        if (out.isEmpty()) out.add("— keine —");
        return out;
    }

    public Location getLocation(UUID id) {
        Holo h = holos.get(id);
        return (h == null) ? null : h.base.clone();
    }

    public void clearAll() {
        for (Holo h : holos.values()) HologramUtil.removeAll(h.stands);
        holos.clear();
        saveAll();
    }

    public void refreshNow() {
        for (Holo h : holos.values()) refreshOne(h);
    }

    public void shutdown() {
        stopAutoRefresh();
        for (Holo h : holos.values()) HologramUtil.removeAll(h.stands);
        holos.clear();
    }

    /* ======================= Internals ======================= */

    private void refreshOne(Holo h) {
        List<String> lines = render(h.type);

        if (h.stands.size() == lines.size()) {
            for (int i = 0; i < lines.size(); i++) {
                setName(h.stands.get(i), lines.get(i));
            }
            return;
        }
        HologramUtil.removeAll(h.stands);
        h.stands = HologramUtil.spawnLines(h.base, lines);
    }

    private void startAutoRefresh() {
        stopAutoRefresh();
        long seconds = Math.max(5, plugin.getConfig().getInt("holo.refreshSeconds", 10));
        refresher = Bukkit.getScheduler().runTaskTimer(plugin, () -> {
            for (Holo h : holos.values()) refreshOne(h);
        }, 20L * seconds, 20L * seconds);
    }
    private void stopAutoRefresh() {
        if (refresher != null) try { refresher.cancel(); } catch (Throwable ignored) {}
        refresher = null;
    }

    private void loadAndSpawn() {
        if (store == null) return;
        var sec = store.getConfigurationSection("holos");
        if (sec == null) return;

        for (String key : sec.getKeys(false)) {
            try {
                UUID id = UUID.fromString(key);
                String typeStr = store.getString("holos." + key + ".type", "Warzone");
                Board type = Board.parse(typeStr);
                String world = store.getString("holos." + key + ".world");
                double x = store.getDouble("holos." + key + ".x");
                double y = store.getDouble("holos." + key + ".y");
                double z = store.getDouble("holos." + key + ".z");
                World w = Bukkit.getWorld(world);
                if (type == null || w == null) continue;

                Holo h = new Holo(type, new Location(w, x, y, z));
                h.stands = HologramUtil.spawnLines(h.base, render(type));
                holos.put(id, h);
            } catch (Throwable ignored) {}
        }
    }

    private void saveAll() {
        store = new YamlConfiguration();
        for (Map.Entry<UUID, Holo> e : holos.entrySet()) {
            UUID id = e.getKey();
            Holo h = e.getValue();
            String path = "holos." + id;
            store.set(path + ".type", h.type.name());
            store.set(path + ".world", h.base.getWorld().getName());
            store.set(path + ".x", h.base.getX());
            store.set(path + ".y", h.base.getY());
            store.set(path + ".z", h.base.getZ());
        }
        try { store.save(storeFile); } catch (IOException ignored) {}
    }

    private Holo nearest(Location ref, double radius) {
        if (ref == null || ref.getWorld() == null) return null;
        double r2 = radius * radius;
        Holo best = null;
        double bestD = Double.MAX_VALUE;
        for (Holo h : holos.values()) {
            if (!Objects.equals(h.base.getWorld(), ref.getWorld())) continue;
            double d = h.base.distanceSquared(ref);
            if (d <= r2 && d < bestD) { bestD = d; best = h; }
        }
        return best;
    }

    /* ======================= Rendering ======================= */

    private List<String> render(Board b) {
        switch (b) {
            case TopFactions:  return renderTopFactions();
            case TopCrystals:  return renderTopCrystals();
            case TopKills:     return reflectList(plugin.getStatsManager(), "topKills", 10, "§f• %s");
            case TopPrestige:  return reflectList(plugin.getStatsManager(), "topPrestige", 10, "§f• %s");
            case Warzone:      return renderWarzone();
            default:           return List.of("—");
        }
    }

    private List<String> renderTopFactions() {
        try {
            Object fm = plugin.getFactionManager();
            if (fm == null) return List.of("&d&lTOP FACTIONS", "§7Keine Daten");

            List<String> ids = callStringList(fm, "getAllFactionIds");
            if (ids == null || ids.isEmpty()) {
                ids = callStringList(fm, "getAllFactionNames");
                if (ids == null) return List.of("&d&lTOP FACTIONS", "§7Keine Daten");
                return headerList("&d&lTOP FACTIONS",
                        ids.stream().sorted().limit(10)
                                .map(n -> "§f• " + n).collect(Collectors.toList()));
            }

            class Row { String id; String name; int members; }
            List<Row> rows = new ArrayList<>();
            for (String id : ids) {
                Row r = new Row();
                r.id = id;
                r.name = callString(fm, "getDisplayName", id);
                if (r.name == null) r.name = id;
                r.members = sizeOf(callCollection(fm, "getMembers", id));
                rows.add(r);
            }
            rows.sort(Comparator.comparingInt((Row r) -> r.members).reversed());

            List<String> list = new ArrayList<>();
            for (int i = 0; i < Math.min(10, rows.size()); i++) {
                Row r = rows.get(i);
                list.add("§f• §e" + r.name + " §7(" + r.members + ")");
            }
            return headerList("&d&lTOP FACTIONS", list);
        } catch (Throwable t) {
            return List.of("&d&lTOP FACTIONS", "§7Keine Daten");
        }
    }

    private List<String> renderTopCrystals() {
        try {
            Object bank = plugin.getRaidCrystalManager();
            if (bank != null) {
                List<?> top = callList(bank, "topFactions", 10);
                if (top != null && !top.isEmpty()) {
                    List<String> out = new ArrayList<>();
                    for (Object e : top) {
                        String name = String.valueOf(getField(e, "key", "getKey"));
                        String val  = String.valueOf(getField(e, "value", "getValue"));
                        out.add("§f• §d" + name + " §7- §f" + val + "✦");
                    }
                    return headerList("&5&lTOP CRYSTALS", out);
                }
            }
            Object fm = plugin.getFactionManager();
            if (fm == null) return List.of("&5&lTOP CRYSTALS", "§7Keine Daten");
            List<String> ids = callStringList(fm, "getAllFactionIds");
            if (ids == null) return List.of("&5&lTOP CRYSTALS", "§7Keine Daten");

            class Row { String name; long crystals; }
            List<Row> rows = new ArrayList<>();
            for (String id : ids) {
                Row r = new Row();
                r.name = callString(fm, "getDisplayName", id);
                if (r.name == null) r.name = id;
                long bal = 0L;
                if (bank != null) bal = callLong(bank, "getBalance", id);
                if (bal <= 0) {
                    Object fcm = plugin.getFactionCoinManager();
                    if (fcm != null) bal = callLong(fcm, "getCrystals", id);
                }
                r.crystals = Math.max(0, bal);
                rows.add(r);
            }
            rows.sort(Comparator.comparingLong((Row r) -> r.crystals).reversed());

            List<String> out = new ArrayList<>();
            for (int i = 0; i < Math.min(10, rows.size()); i++) {
                Row r = rows.get(i);
                out.add("§f• §d" + r.name + " §7- §f" + pretty(r.crystals) + "✦");
            }
            return headerList("&5&lTOP CRYSTALS", out);
        } catch (Throwable t) {
            return List.of("&5&lTOP CRYSTALS", "§7Keine Daten");
        }
    }

    private List<String> renderWarzone() {
        try {
            Object rm = plugin.getWarzoneRegionManager();
            if (rm == null) return List.of("&c&l⚔ WARZONE", "&7Keine Daten");

            // EXPLIZIT auf Player mappen → List<Player> (kein <? extends Player>)
            java.util.List<org.bukkit.entity.Player> wz = org.bukkit.Bukkit.getOnlinePlayers().stream()
                    .map(p -> (org.bukkit.entity.Player) p)
                    .filter(p -> safeInside(rm, p.getLocation()))
                    .collect(java.util.stream.Collectors.toList());

            int count = wz.size();

            // Top 3 nach K/D (absteigend)
            java.util.List<org.bukkit.entity.Player> top = wz.stream()
                    .sorted(java.util.Comparator.comparingDouble(this::safeKDR).reversed())
                    .limit(3)
                    .collect(java.util.stream.Collectors.toList());

            java.util.List<String> lines = new java.util.ArrayList<>();
            lines.add("&c&l⚔ WARZONE");
            lines.add("&8——————————————");
            lines.add("&7Aktive Spieler: &f" + count);

            if (count == 0) {
                lines.add("&7Kein Spieler aktiv.");
                return lines;
            }

            lines.add("&7Top K/D:");
            int rank = 1;
            for (org.bukkit.entity.Player p : top) {
                double kd = safeKDR(p);
                String kdCol = (kd >= 2.0) ? "&a" : (kd >= 1.0) ? "&e" : "&c";
                lines.add("&f" + rank + ". " + p.getName() + " &8• " + kdCol
                        + String.format(java.util.Locale.US, "%.2f", kd));
                rank++;
            }

            return lines;
        } catch (Throwable t) {
            return List.of("&c&l⚔ WARZONE", "&7Keine Daten");
        }
    }

    /* ======================= Small Utils ======================= */

    private static Location snap(Location l) {
        Location c = l.clone();
        c.setX(Math.floor(c.getX()) + 0.5);
        c.setZ(Math.floor(c.getZ()) + 0.5);
        return c;
    }
    private static String fmt(Location l) {
        return l.getWorld().getName() + " " + l.getBlockX() + "/" + l.getBlockY() + "/" + l.getBlockZ();
    }
    private static String pretty(long n) {
        if (n >= 1_000_000) return String.format(Locale.US, "%.1fm", n / 1_000_000.0);
        if (n >= 1_000)     return String.format(Locale.US, "%.1fk", n / 1_000.0);
        return String.valueOf(n);
    }
    private static List<String> headerList(String header, List<String> items) {
        List<String> out = new ArrayList<>();
        out.add(header);
        out.addAll(items.isEmpty() ? List.of("§7Keine Daten") : items);
        return out;
    }

    /** Setzt den Namen per Adventure (ohne veraltete Bukkit-API). */
    private void setName(ArmorStand as, String text) {
        try {
            as.customName(LegacyComponentSerializer.legacyAmpersand().deserialize(text));
        } catch (Throwable ignored) {
            // Wenn die Methode im Target-Server fehlt, lassen wir die alte Zeile bestehen.
        }
    }

    /* ===== Reflection-Helpers ===== */

    private static List<String> callStringList(Object o, String method) {
        try {
            Object v = o.getClass().getMethod(method).invoke(o);
            if (v instanceof List<?> L) {
                List<String> out = new ArrayList<>();
                for (Object x : L) out.add(String.valueOf(x));
                return out;
            }
        } catch (Throwable ignored) {}
        return null;
    }
    @SuppressWarnings("unchecked")
    private static Collection<?> callCollection(Object o, String method, Object... args) {
        try {
            Object v = o.getClass().getMethod(method, types(args)).invoke(o, args);
            return (v instanceof Collection<?> c) ? c : null;
        } catch (Throwable ignored) {}
        return null;
    }
    private static String callString(Object o, String method, Object... args) {
        try {
            Object v = o.getClass().getMethod(method, types(args)).invoke(o, args);
            return (v == null) ? null : String.valueOf(v);
        } catch (Throwable ignored) {}
        return null;
    }
    private static long callLong(Object o, String method, Object... args) {
        try {
            Object v = o.getClass().getMethod(method, types(args)).invoke(o, args);
            if (v instanceof Number n) return n.longValue();
        } catch (Throwable ignored) {}
        return 0L;
    }
    private static List<?> callList(Object o, String method, Object... args) {
        try {
            Object v = o.getClass().getMethod(method, types(args)).invoke(o, args);
            return (v instanceof List<?> l) ? l : null;
        } catch (Throwable ignored) {}
        return null;
    }
    private static Object getField(Object o, String f, String getter) {
        if (o == null) return null;
        try { return o.getClass().getMethod(getter).invoke(o); } catch (Throwable ignore) {}
        try { var fld = o.getClass().getDeclaredField(f); fld.setAccessible(true); return fld.get(o); }
        catch (Throwable ignore) {}
        return null;
    }
    private static Class<?>[] types(Object... a) {
        Class<?>[] t = new Class<?>[a.length];
        for (int i = 0; i < a.length; i++) t[i] = (a[i] instanceof Integer) ? int.class : a[i].getClass();
        return t;
    }
    private boolean safeInside(Object regionMgr, org.bukkit.Location loc) {
        try { Object v = regionMgr.getClass().getMethod("isInside", org.bukkit.Location.class).invoke(regionMgr, loc);
            return Boolean.TRUE.equals(v); } catch (Throwable ignored) {}
        return false;
    }
    private double safeKDR(org.bukkit.entity.Player p){
        try { return plugin.getStatsManager().getKDR(p); } catch (Throwable t){ return 0.0; }
    }

    /* ===== Fehlende Helper, die deine Fehlermeldungen verursachten ===== */

    /** Reflektiert eine Top-Liste (z. B. topKills(int)) und formatiert jedes Element. */
    private static List<String> reflectList(Object obj, String method, int n, String fmt) {
        if (obj == null) return List.of("§7Keine Daten");
        try {
            var m = obj.getClass().getMethod(method, int.class);
            Object v = m.invoke(obj, n);
            if (v instanceof List<?> L) {
                List<String> out = new ArrayList<>();
                for (Object o : L) out.add(String.format(Locale.ROOT, fmt, String.valueOf(o)));
                return headerListForMethod(method, out);
            }
        } catch (Throwable ignored) {}
        return headerListForMethod(method, List.of("§7Keine Daten"));
    }

    private static List<String> headerListForMethod(String method, List<String> items) {
        String header;
        switch (method) {
            case "topKills":     header = "&6&lTOP KILLS"; break;
            case "topPrestige":  header = "&b&lTOP PRESTIGE"; break;
            default:             header = "&f&lTOP LIST"; break;
        }
        List<String> out = new ArrayList<>();
        out.add(header);
        out.addAll(items);
        return out;
    }

    private static int sizeOf(Collection<?> c) { return (c == null) ? 0 : c.size(); }
}