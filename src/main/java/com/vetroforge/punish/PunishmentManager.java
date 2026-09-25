// src/main/java/com/vetroforge/punish/PunishmentManager.java
package com.vetroforge.punish;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;
import org.bukkit.BanEntry;
import org.bukkit.BanList;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import com.vetroforge.persistence.YamlIO;
import java.io.File;
import java.util.Date;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class PunishmentManager {

    public enum Type { BAN, KICK, MUTE, REPORT }

    public static final class PunishEntry {
        public final UUID target;
        public final String type;
        public final String reason;
        public final UUID staff;
        public final long until; // 0 = kein Ablauf

        public PunishEntry(UUID target, String type, String reason, UUID staff, long until){
            this.target = target;
            this.type   = type;
            this.reason = reason;
            this.staff  = staff;
            this.until  = until;
        }
    }

    private final FactionsPlugin plugin;
    private final File statusFile;
    private FileConfiguration statusYml;

    // Status
    private final Map<UUID, PunishEntry> mutes = new ConcurrentHashMap<>();
    private final Set<UUID> banned = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    // Gründe: type -> lang -> liste
    private final EnumMap<Type, Map<String, List<String>>> reasons = new EnumMap<>(Type.class);

    public PunishmentManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.statusFile = new File(plugin.getDataFolder(), "data/punishment-status.yml");
        loadStatus();
        loadReasonsFromConfig();
    }

    /* ======================= Gründe/Anzeige ======================= */

    public void reload() { loadReasonsFromConfig(); }

    public List<String> getReasons(Type type, String langKey) {
        Map<String, List<String>> byLang = reasons.get(type);
        if (byLang == null || byLang.isEmpty()) return Collections.emptyList();
        if (langKey != null && byLang.containsKey(langKey)) return byLang.get(langKey);
        if (byLang.containsKey("de")) return byLang.get("de");
        for (List<String> l : byLang.values()) return l;
        return Collections.emptyList();
    }

    public String getReasonByIndex(Type type, String langKey, int index1) {
        List<String> list = getReasons(type, langKey);
        if (index1 < 1 || index1 > list.size()) return null;
        return list.get(index1 - 1);
    }

    /** Öffentliche, einfache Banner-API (legacy Strings). */
    public void broadcastBanner(String title, List<String> legacyColoredLines){
        String header = ChatColor.DARK_GRAY + "—————— " + ChatColor.RED + "Vetro" + ChatColor.GOLD + "Forge "
                + ChatColor.GRAY + "• " + ChatColor.WHITE + title + ChatColor.DARK_GRAY + " ——————";
        String footer = ChatColor.DARK_GRAY + "——————————————————————————————";

        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(header);
            if (legacyColoredLines != null) for (String l : legacyColoredLines) p.sendMessage(color(l));
            p.sendMessage(footer);
        }
        CommandSender console = Bukkit.getConsoleSender();
        console.sendMessage(header);
        if (legacyColoredLines != null) for (String l : legacyColoredLines) console.sendMessage(color(l));
        console.sendMessage(footer);
    }

    /** Silent-Ausgabe nur an Notify + Konsole. */
    public void announce(String type, List<String> legacyColoredLines, boolean silent) {
        String header = ChatColor.DARK_GRAY + "—————— " + ChatColor.RED + "Vetro" + ChatColor.GOLD + "Forge "
                + ChatColor.GRAY + "• " + ChatColor.WHITE + (type == null ? "" : type) + ChatColor.DARK_GRAY + " ——————";
        String footer = ChatColor.DARK_GRAY + "——————————————————————————————";

        if (silent) {
            for (Player p : Bukkit.getOnlinePlayers()) {
                if (p.hasPermission("vetroforge.notify")) {
                    p.sendMessage(header);
                    if (legacyColoredLines != null) for (String l : legacyColoredLines) p.sendMessage(color(l));
                    p.sendMessage(footer);
                }
            }
            CommandSender console = Bukkit.getConsoleSender();
            console.sendMessage(header);
            if (legacyColoredLines != null) for (String l : legacyColoredLines) console.sendMessage(color(l));
            console.sendMessage(footer);
        } else {
            broadcastBanner(type, legacyColoredLines);
        }
    }

    /** Gründe hübsch auflisten. */
    public void sendReasonsBanner(CommandSender s, Type type, String langKey) {
        String header = ChatColor.DARK_GRAY + "—————— " + ChatColor.RED + "Vetro" + ChatColor.GOLD + "Forge "
                + ChatColor.GRAY + "• " + ChatColor.WHITE + typeTitle(type, langKey) + ChatColor.DARK_GRAY + " ——————";
        String footer = ChatColor.DARK_GRAY + "——————————————————————————————";

        s.sendMessage(header);
        List<String> list = getReasons(type, langKey);
        if (list.isEmpty()) {
            s.sendMessage(ChatColor.GRAY + "(Keine Gründe konfiguriert)");
        } else {
            for (int i = 0; i < list.size(); i++) {
                s.sendMessage(ChatColor.YELLOW + String.valueOf(i + 1) + ChatColor.GRAY + ") " + ChatColor.WHITE + list.get(i));
            }
        }
        s.sendMessage(footer);
    }

    private String typeTitle(Type t, String langKey) {
        String lk = (langKey == null ? "de" : langKey.toLowerCase(Locale.ROOT));
        switch (t) {
            case BAN:   return "Bans";
            case KICK:  return "Kicks";
            case MUTE:  return "Mutes";
            case REPORT:return "Reports";
        }
        return "Punishments";
    }

    private void loadReasonsFromConfig() {
        reasons.clear();
        // erwartete Struktur: punishments.reasons.<type>.<lang>: [ "...", "..." ]
        for (Type t : Type.values()) {
            Map<String, List<String>> byLang = new HashMap<>();
            org.bukkit.configuration.file.FileConfiguration cfg = plugin.getConfig();
            String base = "punishments.reasons." + t.name().toLowerCase(Locale.ROOT);

            Set<String> langs = Collections.emptySet();
            if (cfg.isConfigurationSection(base)) {
                langs = cfg.getConfigurationSection(base).getKeys(false);
            }
            for (String lang : langs) {
                List<String> list = cfg.getStringList(base + "." + lang);
                if (list != null && !list.isEmpty()) byLang.put(lang, list);
            }
            if (byLang.isEmpty()) byLang.putAll(seedDefaults(t));
            reasons.put(t, byLang);
        }
        plugin.saveConfig();
    }

    private Map<String, List<String>> seedDefaults(Type t) {
        Map<String, List<String>> m = new HashMap<>();
        if (t == Type.BAN) {
            m.put("de", Arrays.asList("Cheating/Hacks","Duping/Exploits","Schwerwiegende Beleidigung/Hass","Doxxing/Drohungen","Umgehung von Bans"));
            m.put("en", Arrays.asList("Cheating/Hacks","Duping/Exploits","Severe harassment/hate speech","Doxxing/Threats","Ban evasion"));
        } else if (t == Type.KICK) {
            m.put("de", Arrays.asList("Unangemessenes Verhalten","Spam/Fluten","AFK/Blockieren"));
            m.put("en", Arrays.asList("Inappropriate behavior","Spam/Flood","AFK/Blocking"));
        } else if (t == Type.MUTE) {
            m.put("de", Arrays.asList("Beleidigung","Spam","Werbung"));
            m.put("en", Arrays.asList("Insult","Spam","Advertising"));
        } else if (t == Type.REPORT) {
            m.put("de", Arrays.asList("Verdacht auf Cheating","Beleidigung/Belästigung","Bug/Exploit-Nutzung","Scamming/Diebstahl"));
            m.put("en", Arrays.asList("Suspected cheating","Harassment/Insult","Bug/Exploit abuse","Scamming/Theft"));
        }
        for (Map.Entry<String, List<String>> e : m.entrySet()) {
            String path = "punishments.reasons." + t.name().toLowerCase(Locale.ROOT) + "." + e.getKey();
            if (!plugin.getConfig().isSet(path)) plugin.getConfig().set(path, e.getValue());
        }
        return m;
    }

    /* ==================== Status-API ==================== */

    public boolean isMuted(UUID id){
        PunishEntry e = mutes.get(id);
        if (e == null) return false;
        if (e.until > 0 && System.currentTimeMillis() > e.until) {
            mutes.remove(id);
            saveStatus();
            return false;
        }
        return true;
    }

    public PunishEntry getMute(UUID id) {
        return isMuted(id) ? mutes.get(id) : null;
    }

    public void mute(UUID id, String reason, UUID staff, long millis){
        long until = millis > 0 ? System.currentTimeMillis() + millis : 0L;
        mutes.put(id, new PunishEntry(id, "mute", reason, staff, until));
        saveStatus();
    }

    public void unmute(UUID id){ mutes.remove(id); saveStatus(); }

    public boolean isBanned(UUID id){ return banned.contains(id); }
    public void ban(UUID id){ banned.add(id); saveStatus(); }
    public void unban(UUID id){ banned.remove(id); saveStatus(); }

    /** Öffentliche Helper-Methode – wird von Commands/AIBanManager verwendet. */
    public void setBanned(UUID id, boolean flag){ if (id == null) return; if (flag) ban(id); else unban(id); }

    public String muteMessage(PunishEntry mute) {
        String reason = mute == null || mute.reason == null || mute.reason.isBlank() ? "Mute" : mute.reason;
        String duration = "permanent";
        if (mute != null && mute.until > 0) {
            long seconds = Math.max(1L, (mute.until - System.currentTimeMillis() + 999L) / 1000L);
            duration = seconds >= 60 ? ((seconds + 59) / 60) + "m" : seconds + "s";
        }
        return ChatColor.RED + "Du bist gemutet."
                + ChatColor.GRAY + " Grund: " + ChatColor.WHITE + reason
                + ChatColor.GRAY + " | Restzeit: " + ChatColor.WHITE + duration;
    }

    /* ==================== Utils ==================== */

    private String color(String s){ return ChatColor.translateAlternateColorCodes('&', s == null ? "" : s); }

    private void loadStatus() {
        statusYml = YamlIO.load(statusFile);

        for (String raw : statusYml.getStringList("banned")) {
            try { banned.add(UUID.fromString(raw)); } catch (Exception ignored) {}
        }

        if (statusYml.isConfigurationSection("mutes")) {
            for (String key : Objects.requireNonNull(statusYml.getConfigurationSection("mutes")).getKeys(false)) {
                try {
                    UUID target = UUID.fromString(key);
                    String base = "mutes." + key + ".";
                    String reason = statusYml.getString(base + "reason", "Mute");
                    UUID staff = null;
                    String staffRaw = statusYml.getString(base + "staff", "");
                    if (staffRaw != null && !staffRaw.isBlank()) staff = UUID.fromString(staffRaw);
                    long until = statusYml.getLong(base + "until", 0L);
                    if (until > 0 && until < System.currentTimeMillis()) continue;
                    mutes.put(target, new PunishEntry(target, "mute", reason, staff, until));
                } catch (Exception ignored) {}
            }
        }
        saveStatus();
    }

    private synchronized void saveStatus() {
        if (statusYml == null) statusYml = new YamlConfiguration();

        List<String> banIds = new ArrayList<>();
        for (UUID id : banned) banIds.add(id.toString());
        statusYml.set("banned", banIds);
        statusYml.set("mutes", null);

        for (PunishEntry entry : mutes.values()) {
            if (entry == null || entry.target == null) continue;
            if (entry.until > 0 && entry.until < System.currentTimeMillis()) continue;
            String base = "mutes." + entry.target + ".";
            statusYml.set(base + "reason", entry.reason);
            statusYml.set(base + "staff", entry.staff == null ? null : entry.staff.toString());
            statusYml.set(base + "until", entry.until);
        }

        YamlIO.save(statusYml, statusFile);
    }

    /** Einheitlicher Anzeigename für Sender (Fallback ohne externe Util). */
    public String actorName(CommandSender s) {
        if (s == null) return "Console";
        if (s instanceof Player) return ((Player) s).getName();
        return s.getName() != null ? s.getName() : "Console";
    }
    /** Liefert die letzte (aktuelle) Ban-Info des Spielers über Bukkit-Banliste oder internen Status. */
    public PunishEntry latestBan(UUID id) {
        if (id == null) return null;

        // 1) Bukkit-Banliste nach Name abfragen
        OfflinePlayer off = Bukkit.getOfflinePlayer(id);
        String name = (off != null) ? off.getName() : null;

        if (name != null) {
            BanEntry be = Bukkit.getBanList(BanList.Type.NAME).getBanEntry(name);
            if (be != null) {
                String reason = be.getReason();
                String src = be.getSource();
                // staff-UUID best-effort: wenn aktuell online & Name matcht
                UUID staffUuid = null;
                try {
                    Player sp = Bukkit.getPlayerExact(src);
                    if (sp != null) staffUuid = sp.getUniqueId();
                } catch (Throwable ignored) {}
                Date exp = be.getExpiration();
                long until = (exp == null) ? 0L : exp.getTime();
                return new PunishEntry(id, "ban", reason == null ? "Ban" : reason, staffUuid, until);
            }
        }

        // 2) interner Flag (falls gesetzt)
        if (isBanned(id)) {
            return new PunishEntry(id, "ban", "Ban", null, 0L);
        }
        return null;
    }
    /** Baut den mehrzeiligen Bann-Text (Legacy-Farbcodes), z.B. für Join-Deny/Kick. */
    public String banBannerText(String playerName, String actorName, String reason) {
        String p = (playerName == null || playerName.isBlank()) ? "-" : playerName;
        String a = (actorName == null || actorName.isBlank()) ? "Console" : actorName;
        String r = (reason == null || reason.isBlank()) ? "Ban" : reason;

        StringBuilder sb = new StringBuilder(256);
        sb.append("§cDu wurdest gebannt!\n");
        sb.append("§7Spieler: §f").append(p).append('\n');
        sb.append("§7Grund: §f").append(r).append('\n');
        sb.append("§7Von: §f").append(a).append('\n');
        // Optional: Appeal-Hinweis (gerne in config auslagerbar)
        String appeal = "Schreibe einen Entbannungsantrag auf vetroforge.net";
        try { appeal = plugin.getConfig().getString("punishments.appealHint", appeal); } catch (Throwable ignored) {}
        if (appeal != null && !appeal.isBlank()) {
            sb.append("§8Hinweis: §7").append(appeal);
        }
        return sb.toString();
    }
}
