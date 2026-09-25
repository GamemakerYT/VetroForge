// src/main/java/com/vetroforge/punish/PunishmentHistoryManager.java
package com.vetroforge.punish;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.*;

public class PunishmentHistoryManager {

    public static final class Entry {
        public final long ts;
        public final String type;     // BAN | UNBAN | MUTE | KICK | REPORT
        public final String reason;
        public final UUID staff;      // kann null sein
        public final long durationMs; // nur bei MUTE

        public Entry(long ts, String type, String reason, UUID staff, long durationMs) {
            this.ts = ts; this.type = type; this.reason = reason; this.staff = staff; this.durationMs = durationMs;
        }
    }

    private final FactionsPlugin plugin;
    private final File file;
    private YamlConfiguration yml;

    public PunishmentHistoryManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "data/punishments.yml");
        load();
    }

    private void load() {
        if (!file.getParentFile().exists()) file.getParentFile().mkdirs();
        this.yml = YamlConfiguration.loadConfiguration(file);
    }

    private void save() {
        try { yml.save(file); } catch (IOException ignored) {}
    }

    /* ---------- Log API ---------- */

    public void logBan(UUID target, String reason, UUID staff, long ts) {
        add(target, new Entry(ts, "BAN", nz(reason, "Ban"), staff, 0));
        inc(target, "count.ban");
    }
    public void logUnban(UUID target, String reason, UUID staff, long ts) {
        add(target, new Entry(ts, "UNBAN", nz(reason, "Unban"), staff, 0));
        inc(target, "count.unban");
    }
    public void logKick(UUID target, String reason, UUID staff, long ts) {
        add(target, new Entry(ts, "KICK", nz(reason, "Kick"), staff, 0));
        inc(target, "count.kick");
    }
    public void logMute(UUID target, String reason, UUID staff, long ts, long durationMs) {
        add(target, new Entry(ts, "MUTE", nz(reason, "Mute"), staff, durationMs));
        inc(target, "count.mute");
    }
    public void logReport(UUID target, String reason, UUID staff, long ts) {
        add(target, new Entry(ts, "REPORT", nz(reason, "Report"), staff, 0));
        inc(target, "count.report");
    }

    /* ---------- Read API ---------- */

    public List<Entry> getHistory(UUID target) {
        List<Map<String,Object>> raw = (List<Map<String,Object>>) yml.getList(key(target, "entries"), Collections.emptyList());
        List<Entry> out = new ArrayList<>();
        if (raw != null) {
            for (Map<String,Object> m : raw) {
                long ts = asLong(m.get("ts"));
                String type = String.valueOf(m.get("type"));
                String reason = String.valueOf(m.get("reason"));
                UUID staff = asUUID(m.get("staff"));
                long dur = asLong(m.get("durationMs"));
                out.add(new Entry(ts, type, reason, staff, dur));
            }
        }
        out.sort(Comparator.comparingLong(e -> e.ts));
        return out;
    }

    public Map<String,Integer> getCounters(UUID target) {
        Map<String,Integer> m = new LinkedHashMap<>();
        m.put("ban", yml.getInt(key(target, "count.ban"), 0));
        m.put("unban", yml.getInt(key(target, "count.unban"), 0));
        m.put("mute", yml.getInt(key(target, "count.mute"), 0));
        m.put("report", yml.getInt(key(target, "count.report"), 0));
        m.put("kick", yml.getInt(key(target, "count.kick"), 0));
        return m;
    }

    public void sendHistory(CommandSender sender, UUID target, int page) {
        sender.sendMessage(ChatColor.DARK_RED + "—— " + ChatColor.RED + "Punishment History"
                + ChatColor.DARK_GRAY + " • " + ChatColor.GRAY + resolveName(target) + ChatColor.DARK_RED + " ——");
        List<String> lines = getHistoryLines(target, Math.max(1, page), 8);
        if (lines.isEmpty()) {
            sender.sendMessage(ChatColor.GRAY + "(keine Einträge)");
            return;
        }
        for (String line : lines) sender.sendMessage(ChatColor.translateAlternateColorCodes('&', line));
    }

    public List<String> getHistoryLines(UUID target, int page, int pageSize) {
        List<Entry> entries = getHistory(target);
        Collections.reverse(entries);
        int safePage = Math.max(1, page);
        int safeSize = Math.max(1, pageSize);
        int from = Math.min(entries.size(), (safePage - 1) * safeSize);
        int to = Math.min(entries.size(), from + safeSize);
        List<String> lines = new ArrayList<>();
        for (Entry entry : entries.subList(from, to)) {
            String staffName = entry.staff == null ? "Console" : resolveName(entry.staff);
            String duration = entry.durationMs > 0 ? " &8(" + Math.max(1, entry.durationMs / 60_000L) + "m)" : "";
            lines.add("&8• &c" + entry.type + duration + " &7von &f" + staffName
                    + " &8- &7" + new Date(entry.ts) + " &8- &f" + entry.reason);
        }
        return lines;
    }

    /* ---------- intern ---------- */

    private void add(UUID target, Entry e) {
        String base = key(target, "entries");
        List<Map<String,Object>> list = (List<Map<String,Object>>) yml.getList(base);
        if (list == null) list = new ArrayList<>();
        Map<String,Object> m = new LinkedHashMap<>();
        m.put("ts", e.ts);
        m.put("type", e.type);
        m.put("reason", e.reason);
        m.put("staff", e.staff == null ? null : e.staff.toString());
        m.put("durationMs", e.durationMs);
        list.add(m);
        yml.set(base, list);
        // Name cache (nett für /history)
        String name = resolveName(target);
        if (name != null) yml.set(key(target, "name"), name);
        save();
    }

    private void inc(UUID target, String path) {
        String full = key(target, path);
        yml.set(full, yml.getInt(full, 0) + 1);
        save();
    }

    private String key(UUID id, String tail){ return "players."+id.toString()+"."+tail; }
    private static String nz(String s, String d){ return (s==null||s.isEmpty())?d:s; }
    private static long asLong(Object o){ try { return Long.parseLong(String.valueOf(o)); } catch (Exception e){ return 0L; } }
    private static UUID asUUID(Object o){ try { return UUID.fromString(String.valueOf(o)); } catch (Exception e){ return null; } }

    private String resolveName(UUID id) {
        OfflinePlayer off = Bukkit.getOfflinePlayer(id);
        return off.getName();
    }
}
