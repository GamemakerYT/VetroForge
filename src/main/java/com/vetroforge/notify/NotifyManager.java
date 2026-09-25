package com.vetroforge.notify;

import com.vetroforge.FactionsPlugin;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Sound;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.*;

/**
 * Staff/Moderation Notify mit Kanälen, Persistenz und VetroPerms-Bridge.
 */
public class NotifyManager {

    /* ========== Datentypen ========== */

    public static final class Settings {
        public boolean enabled = true;
        public boolean soundEnabled = true;
        public String  soundName = "BLOCK_NOTE_BLOCK_PLING";
        public boolean actionbar = false;
        public boolean autoOnJoin = false;
        public long    snoozeUntil = 0L; // epoch ms
        // pro-Channel: enabled?
        public final Map<String, Boolean> channels = new LinkedHashMap<>();
    }

    /* ========== Felder ========== */

    private final FactionsPlugin plugin;

    private final File dataFile;
    private YamlConfiguration yml;

    // Player -> Settings
    private final Map<UUID, Settings> map = new HashMap<>();

    // bekannte Channels (Reihenfolge beibehalten)
    private final LinkedHashSet<String> knownChannels = new LinkedHashSet<>(
            Arrays.asList("reports","tickets","cheat","punish","staffchat")
    );

    private int autosaveTask = -1;

    public NotifyManager(FactionsPlugin plugin) {
        this.plugin = plugin;
        this.dataFile = new File(plugin.getDataFolder(), "data/notify.yml");
        loadConfigDefaults();
        load();
        startAutosave();
    }

    /* ========== Public API ========== */

    public Set<String> getKnownChannels() {
        return Collections.unmodifiableSet(knownChannels);
    }

    public Settings getSettings(UUID id) {
        return map.computeIfAbsent(id, k -> defaults());
    }

    public boolean isEnabled(UUID id) {
        return getSettings(id).enabled;
    }

    public void setEnabled(UUID id, boolean on) {
        getSettings(id).enabled = on;
        saveAsync(id);
    }

    public boolean isChannelEnabled(UUID id, String channel) {
        channel = norm(channel);
        Settings s = getSettings(id);
        return s.channels.getOrDefault(channel, true);
    }

    public void setChannel(UUID id, String channel, boolean on) {
        channel = norm(channel);
        knownChannels.add(channel);
        Settings s = getSettings(id);
        s.channels.put(channel, on);
        saveAsync(id);
    }

    public void setSoundEnabled(UUID id, boolean on) {
        getSettings(id).soundEnabled = on;
        saveAsync(id);
    }

    public void setActionbar(UUID id, boolean on) {
        getSettings(id).actionbar = on;
        saveAsync(id);
    }

    public boolean isAutoOnJoin(UUID id) {
        return getSettings(id).autoOnJoin;
    }

    public void setAutoOnJoin(UUID id, boolean on) {
        getSettings(id).autoOnJoin = on;
        saveAsync(id);
    }

    public void setSoundName(UUID id, String soundName) {
        getSettings(id).soundName = (soundName == null || soundName.isBlank()) ? "BLOCK_NOTE_BLOCK_PLING" : soundName.toUpperCase(Locale.ROOT);
        saveAsync(id);
    }

    public boolean isSnoozed(UUID id) {
        return System.currentTimeMillis() < getSettings(id).snoozeUntil;
    }

    /** Snooze in Millisekunden (0 = aus). */
    public void snooze(UUID id, long millis) {
        getSettings(id).snoozeUntil = (millis <= 0) ? 0L : (System.currentTimeMillis() + millis);
        saveAsync(id);
    }

    /**
     * Broadcast an alle Staff mit aktivem Notify.
     * @param channel   z.B. "reports" | "cheat" ...
     * @param msg       Chat-Text (Legacy-Color erlaubt)
     * @param fallbackSound Sound, falls Spieler keinen eigenen gesetzt hat / Name ungültig ist
     * @param vetroPermNode optionaler VetroPerms-Node (nur Empfänger mit diesem Node), null = jeder Notify-Nutzer
     * @return Anzahl Empfänger
     */
    public int broadcast(String channel, String msg, Sound fallbackSound, String vetroPermNode) {
        String ch = norm(channel);
        int sent = 0;
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (!isEligible(p, ch, vetroPermNode)) continue;

            Settings s = getSettings(p.getUniqueId());
            String prefix = ChatColor.DARK_GRAY + "[" + ChatColor.GOLD + "Notify" + ChatColor.GRAY + ":" + ChatColor.YELLOW + ch + ChatColor.DARK_GRAY + "] " + ChatColor.RESET;
            try { p.sendMessage(prefix + ChatColor.translateAlternateColorCodes('&', msg)); } catch (Throwable ignored) {}

            if (s.actionbar) {
                try { p.sendActionBar(ChatColor.translateAlternateColorCodes('&', "&6Notify &8• &f" + msg)); } catch (Throwable ignored) {}
            }

            if (s.soundEnabled) {
                Sound snd = fallbackSound;
                try { snd = Sound.valueOf(s.soundName); } catch (Throwable ignored) {}
                try { p.playSound(p.getLocation(), snd, 0.6f, 1.3f); } catch (Throwable ignored) {}
            }
            sent++;
        }
        return sent;
    }

    /** Aufräumen (onDisable). */
    public void shutdown() {
        if (autosaveTask != -1) {
            Bukkit.getScheduler().cancelTask(autosaveTask);
            autosaveTask = -1;
        }
        saveAll();
    }

    /* ========== intern: Empfängerfilter ========== */

    private boolean isEligible(Player p, String channel, String vetroPermNode) {
        if (p == null || !p.isOnline()) return false;
        Settings s = getSettings(p.getUniqueId());
        if (!s.enabled) return false;
        if (isSnoozed(p.getUniqueId())) return false;
        if (!s.channels.getOrDefault(channel, true)) return false;

        // Optionale VetroPerms-Anforderung
        if (vetroPermNode != null && !vetroPermNode.isBlank()) {
            if (!hasVetroPerm(p, vetroPermNode)) return false;
        }
        return true;
    }

    /* ========== VetroPerms-Bridge (Reflection) ========== */

    private boolean hasVetroPerm(Player p, String node) {
        if (p == null || node == null || node.isBlank()) return false;
        try {
            Object perms = null;
            for (String m : new String[]{"getVetroPerms","getPerms","getPermissionsManager","getVetroPermsManager"}) {
                try { perms = plugin.getClass().getMethod(m).invoke(plugin); if (perms != null) break; } catch (Throwable ignored) {}
            }
            if (perms != null) {
                for (String m : new String[]{"has","hasPermission","check","test"}) {
                    try {
                        Method mm = perms.getClass().getMethod(m, Player.class, String.class);
                        Object r = mm.invoke(perms, p, node);
                        if (r instanceof Boolean) return (Boolean) r;
                    } catch (Throwable ignored) {}
                    try {
                        Method mm = perms.getClass().getMethod(m, UUID.class, String.class);
                        Object r = mm.invoke(perms, p.getUniqueId(), node);
                        if (r instanceof Boolean) return (Boolean) r;
                    } catch (Throwable ignored) {}
                    try {
                        Method mm = perms.getClass().getMethod(m, String.class, String.class);
                        Object r = mm.invoke(perms, p.getName(), node);
                        if (r instanceof Boolean) return (Boolean) r;
                    } catch (Throwable ignored) {}
                }
            }
        } catch (Throwable ignored) {}

        // Fallback
        try { if (p.isOp()) return true; } catch (Throwable ignored) {}
        try { return p.hasPermission(node); } catch (Throwable ignored) {}
        return false;
    }

    /* ========== Load / Save ========== */

    private void loadConfigDefaults() {
        // Zusätzliche Channels aus config (notify.channels: [..])
        List<String> extra = plugin.getConfig().getStringList("notify.channels");
        if (extra != null) for (String s : extra) if (s != null && !s.isBlank()) knownChannels.add(norm(s));
    }

    private Settings defaults() {
        Settings s = new Settings();
        // alle bekannten Channels default an
        for (String ch : knownChannels) s.channels.put(ch, true);
        return s;
    }

    private String norm(String ch) { return (ch == null ? "" : ch.trim().toLowerCase(Locale.ROOT)); }

    private void load() {
        try {
            if (!dataFile.getParentFile().exists()) dataFile.getParentFile().mkdirs();
            if (!dataFile.exists()) dataFile.createNewFile();
        } catch (IOException ignored) {}

        yml = YamlConfiguration.loadConfiguration(dataFile);
        ConfigurationSection ps = yml.getConfigurationSection("players");
        if (ps == null) return;

        for (String k : ps.getKeys(false)) {
            try {
                UUID id = UUID.fromString(k);
                Settings s = new Settings();
                s.enabled       = ps.getBoolean(k + ".enabled", true);
                s.soundEnabled  = ps.getBoolean(k + ".soundEnabled", true);
                s.soundName     = ps.getString (k + ".soundName", "BLOCK_NOTE_BLOCK_PLING");
                s.actionbar     = ps.getBoolean(k + ".actionbar", false);
                s.autoOnJoin    = ps.getBoolean(k + ".autoOnJoin", false);
                s.snoozeUntil   = ps.getLong   (k + ".snoozeUntil", 0L);

                // Channels laden, unbekannte auffüllen
                ConfigurationSection cs = ps.getConfigurationSection(k + ".channels");
                if (cs != null) {
                    for (String ch : cs.getKeys(false)) s.channels.put(norm(ch), cs.getBoolean(ch, true));
                }
                for (String ch : knownChannels) s.channels.putIfAbsent(ch, true);

                map.put(id, s);
            } catch (Throwable ignored) {}
        }
    }

    private void saveAsync(UUID id) {
        // klein & schnell genug, aber wir sammeln pro Spieler
        Bukkit.getScheduler().runTaskAsynchronously(plugin, this::saveAll);
    }

    private void saveAll() {
        if (yml == null) yml = new YamlConfiguration();
        yml.set("players", null); // clear
        for (Map.Entry<UUID, Settings> e : map.entrySet()) {
            String b = "players." + e.getKey();
            Settings s = e.getValue();
            yml.set(b + ".enabled",      s.enabled);
            yml.set(b + ".soundEnabled", s.soundEnabled);
            yml.set(b + ".soundName",    s.soundName);
            yml.set(b + ".actionbar",    s.actionbar);
            yml.set(b + ".autoOnJoin",   s.autoOnJoin);
            yml.set(b + ".snoozeUntil",  s.snoozeUntil);

            String cb = b + ".channels";
            for (Map.Entry<String, Boolean> ce : s.channels.entrySet()) {
                yml.set(cb + "." + ce.getKey(), ce.getValue());
            }
        }
        try { yml.save(dataFile); } catch (IOException ignored) {}
    }

    private void startAutosave() {
        int sec = Math.max(60, plugin.getConfig().getInt("notify.autosaveSec", 300));
        autosaveTask = Bukkit.getScheduler().scheduleSyncRepeatingTask(plugin, this::saveAll, sec * 20L, sec * 20L);
    }
}
