// src/main/java/com/vetroforge/i18n/I18nManager.java
package com.vetroforge.i18n;

import com.vetroforge.FactionsPlugin;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

public class I18nManager {

    private static final String NO_LANGUAGE = "\u0000none";

    private final JavaPlugin plugin;
    private final Map<String, YamlConfiguration> bundles = new HashMap<>();
    private final Set<String> supported = new LinkedHashSet<>(Arrays.asList("de","en","fr","nl"));
    private final Set<String> loggedFallbacks = new HashSet<>();
    private final Map<UUID, String> languageCache = new java.util.concurrent.ConcurrentHashMap<>();
    private String defaultLang = "de";

    public I18nManager(JavaPlugin plugin) {
        this.plugin = plugin;
        loadAll();
    }

    public void setDefaultLang(String lang){
        if (lang != null && supported.contains(lang.toLowerCase(Locale.ROOT))) {
            this.defaultLang = lang.toLowerCase(Locale.ROOT);
        }
    }

    public void reload() {
        bundles.clear();
        loadAll();
    }

    private void loadAll() {
        File langDir = new File(plugin.getDataFolder(), "lang");
        if (!langDir.exists()) langDir.mkdirs();

        saveIfMissing("lang/de.yml");
        saveIfMissing("lang/en.yml");
        saveIfMissing("lang/fr.yml");
        saveIfMissing("lang/nl.yml");

        for (String code : supported) {
            File f = new File(langDir, code + ".yml");
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            mergeBundledDefaults(y, f, "lang/" + code + ".yml");
            bundles.put(code, y);
        }
    }

    private void mergeBundledDefaults(YamlConfiguration target, File file, String resourcePath) {
        try (InputStream in = plugin.getResource(resourcePath)) {
            if (in == null) return;
            YamlConfiguration defaults = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8)
            );
            target.setDefaults(defaults);
            target.options().copyDefaults(true);
            target.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("[Language] Could not merge " + resourcePath + ": " + e.getMessage());
        }
    }

    private void saveIfMissing(String path) {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) return;
            File out = new File(plugin.getDataFolder(), path);
            if (!out.exists()) {
                out.getParentFile().mkdirs();
                try (OutputStream os = new FileOutputStream(out)) {
                    os.write(in.readAllBytes());
                }
            }
        } catch (IOException ignored) {}
    }

    /* ======================= Public API (Strings) ======================= */

    public boolean hasLanguage(Player p){
        return getLanguage(p) != null;
    }

    public String getLanguage(Player p){
        if (p == null) return null;
        UUID id = p.getUniqueId();
        String cached = languageCache.get(id);
        if (cached != null) return NO_LANGUAGE.equals(cached) ? null : cached;

        String resolved = resolveLanguage(p);
        languageCache.put(id, resolved == null ? NO_LANGUAGE : resolved);
        return resolved;
    }

    /** Löscht den zwischengespeicherten Sprachwert eines Spielers, z.B. beim Verlassen des Servers. */
    public void clearCache(UUID uuid) {
        if (uuid != null) languageCache.remove(uuid);
    }

    private String resolveLanguage(Player p){
        String shared = loadSharedLanguage(p.getUniqueId());
        if ("auto".equals(shared)) return locale(p);
        if (shared != null) return shared;
        try {
            File f = new File(plugin.getDataFolder(), "playerdata/" + p.getUniqueId() + ".yml");
            if (!f.exists()) return null;
            YamlConfiguration y = YamlConfiguration.loadConfiguration(f);
            String lang = y.getString("lang", null);
            if (lang == null || lang.isBlank()) return null;
            lang = lang.toLowerCase(Locale.ROOT);
            if (!supported.contains(lang)) return null;
            return lang;
        } catch (Throwable t){ return null; }
    }

    public void setLanguage(Player p, String lang){
        if (lang == null) return;
        lang = lang.toLowerCase(Locale.ROOT);
        if (!supported.contains(lang)) return;

        try {
            File dir = new File(plugin.getDataFolder(), "playerdata");
            if (!dir.exists()) dir.mkdirs();
            File f = new File(dir, p.getUniqueId()+".yml");
            YamlConfiguration y = f.exists() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
            y.set("lang", lang);
            y.save(f);
            saveSharedLanguage(p.getUniqueId(), lang);
            languageCache.put(p.getUniqueId(), lang);
        } catch (IOException ignored) {}
    }

    public void setAuto(Player p) {
        if (p == null) return;
        saveLocalLanguage(p, "auto");
        saveSharedLanguage(p.getUniqueId(), "auto");
        languageCache.put(p.getUniqueId(), locale(p));
    }

    public String tr(Player p, String key){
        String lang = getLanguage(p);
        if (lang == null) lang = defaultLang;
        return tr(lang, key);
    }

    public String tr(Player p, String key, Map<String,String> vars){
        String base = tr(p, key);
        return interpolate(base, vars);
    }

    /** Direkt mit Code, falls du außerhalb eines Spieler-Kontexts bist. */
    public String tr(String lang, String key){
        String v = get(lang, key);
        if (v == null) {
            v = get(defaultLang, key);
            if (v == null) v = get("en", key);
            if (v == null) {
                if (loggedFallbacks.add(key)) plugin.getLogger().warning("[Language] Missing translation key: " + key);
                v = key;
            }
        }
        return v;
    }

    public String tr(String lang, String key, Map<String,String> vars){
        return interpolate(tr(lang, key), vars);
    }

    /* ======================= Public API (Listen) ======================= */

    /** Holt eine String-Liste für den Spieler (mit Fallbacks). */
    public List<String> trList(Player p, String key) {
        String lang = getLanguage(p);
        if (lang == null) lang = defaultLang;
        return trList(lang, key);
    }

    /** Holt eine String-Liste für eine Sprache (mit Fallbacks). */
    public List<String> trList(String lang, String key) {
        List<String> list = getList(lang, key);
        if (list == null || list.isEmpty()) {
            list = getList(defaultLang, key);
        }
        if (list == null || list.isEmpty()) {
            list = getList("en", key);
        }
        return (list == null) ? Collections.emptyList() : new ArrayList<>(list);
    }

    public Set<String> getSupported(){ return Collections.unmodifiableSet(supported); }

    public Set<String> missingKeys() {
        Set<String> all = new TreeSet<>();
        for (YamlConfiguration bundle : bundles.values()) all.addAll(bundle.getKeys(true));
        Set<String> missing = new TreeSet<>();
        for (String language : supported) {
            YamlConfiguration bundle = bundles.get(language);
            for (String key : all) if (!bundle.contains(key)) missing.add(language + ":" + key);
        }
        return missing;
    }

    public String debug(Player player) {
        if (player == null) return "Player not found.";
        return "player=" + player.getName() + ", uuid=" + player.getUniqueId()
                + ", stored=" + loadSharedLanguage(player.getUniqueId())
                + ", locale=" + locale(player) + ", resolved=" + getLanguage(player);
    }

    public void applyRemoteBundles(String message) {
        if (message == null || message.isBlank()) return;
        try {
            JsonObject languages = JsonParser.parseString(message).getAsJsonObject().getAsJsonObject("languages");
            for (String language : supported) {
                JsonObject values = languages.getAsJsonObject(language);
                if (values == null) continue;
                values.entrySet().forEach(entry -> bundles.get(language).set(entry.getKey(), entry.getValue().getAsString()));
            }
        } catch (Exception ignored) {}
    }

    /* ======================= Helpers ======================= */

    private String get(String lang, String key){
        YamlConfiguration y = bundles.get(lang);
        if (y == null) return null;
        return y.getString(key, null);
    }

    private List<String> getList(String lang, String key) {
        YamlConfiguration y = bundles.get(lang);
        if (y == null) return null;
        // getStringList gibt nie null zurück, aber wir behandeln zur Sicherheit
        List<String> out = y.getStringList(key);
        return (out == null) ? null : out;
    }

    private String interpolate(String text, Map<String,String> vars){
        if (text == null || vars == null || vars.isEmpty()) return text;
        String out = text;
        for (Map.Entry<String,String> e : vars.entrySet()) {
            out = out.replace("{"+e.getKey()+"}", e.getValue()==null?"":e.getValue());
        }
        return out;
    }

    private String loadSharedLanguage(UUID uuid) {
        if (!sharedLanguageEnabled()) return null;
        try (Connection c = openConnection(); PreparedStatement s = c.prepareStatement(
                "SELECT language_code FROM player_languages WHERE player_uuid = ?")) {
            s.setString(1, uuid.toString());
            try (ResultSet r = s.executeQuery()) {
                if (!r.next()) return null;
                String language = r.getString(1);
                if (language == null) return null;
                language = language.toLowerCase(Locale.ROOT);
                return "auto".equals(language) || supported.contains(language) ? language : null;
            }
        } catch (Exception ignored) {
            return null;
        }
    }

    private void saveSharedLanguage(UUID uuid, String language) {
        if (!sharedLanguageEnabled()) return;
        try (Connection c = openConnection(); PreparedStatement s = c.prepareStatement("""
                INSERT INTO player_languages (player_uuid, language_code) VALUES (?, ?)
                ON DUPLICATE KEY UPDATE language_code = VALUES(language_code)
                """)) {
            s.setString(1, uuid.toString());
            s.setString(2, language);
            s.executeUpdate();
            if (plugin instanceof FactionsPlugin factions && factions.getNetworkBridge() != null) {
                factions.getNetworkBridge().publish("vf:language:update",
                        "{\"version\":1,\"playerUuid\":\"" + uuid + "\",\"preference\":\""
                                + language + "\",\"language\":\"" + ("auto".equals(language) ? "en" : language) + "\"}");
            }
        } catch (Exception ignored) {}
    }

    private Connection openConnection() throws Exception {
        String host = plugin.getConfig().getString("mysql.host", "127.0.0.1");
        int port = plugin.getConfig().getInt("mysql.port", 3306);
        String database = plugin.getConfig().getString("mysql.database", "VF");
        String user = plugin.getConfig().getString("mysql.user", "VF");
        String password = plugin.getConfig().getString("mysql.password", "");
        if (password == null || password.isBlank()) password = System.getenv("VF_MYSQL_PASSWORD");
        return DriverManager.getConnection(
                "jdbc:mysql://" + host + ":" + port + "/" + database
                        + "?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                user,
                password
        );
    }

    private boolean sharedLanguageEnabled() {
        return plugin.getConfig().getBoolean("mysql.enabled", false)
                && plugin.getConfig().getBoolean("i18n.shared.enabled", false);
    }

    private void saveLocalLanguage(Player player, String language) {
        try {
            File dir = new File(plugin.getDataFolder(), "playerdata");
            if (!dir.exists()) dir.mkdirs();
            File file = new File(dir, player.getUniqueId() + ".yml");
            YamlConfiguration yaml = file.exists() ? YamlConfiguration.loadConfiguration(file) : new YamlConfiguration();
            yaml.set("lang", language);
            yaml.save(file);
        } catch (IOException ignored) {}
    }

    private String locale(Player player) {
        try {
            String language = player.locale().getLanguage().toLowerCase(Locale.ROOT);
            return supported.contains(language) ? language : defaultLang;
        } catch (Exception ignored) {
            return defaultLang;
        }
    }
}
