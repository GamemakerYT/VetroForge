// src/main/java/com/vetroforge/i18n/LanguageManager.java
package com.vetroforge.i18n;

import org.bukkit.ChatColor;
import org.bukkit.entity.Player;
import java.util.List;

public class LanguageManager {

    public static final class LangEntry {
        public final String key;
        public final String name;
        public LangEntry(String key, String name){ this.key=key; this.name=name; }
    }

    private final I18nManager i18n;

    public LanguageManager(I18nManager i18n) {
        this.i18n = i18n;
    }

    /** z.B. "de", "en" – Default "de" falls nix gesetzt */
    public String getPlayerLangKey(Player p) {
        String l = i18n.getLanguage(p);
        return (l == null || l.isBlank()) ? "de" : l.toLowerCase();
    }

    /** Alias, damit vorhandener Code kompiliert */
    public void setPlayerLang(Player p, String key) { i18n.setLanguage(p, key); }

    /** Für dein LanguageCommand / GUI */
    public List<LangEntry> available() {
        return List.of(
                new LangEntry("de","Deutsch"),
                new LangEntry("en","English"),
                new LangEntry("fr","Français"),
                new LangEntry("nl","Nederlands")
        );
    }

    /** Titel fürs GUI */
    public String guiTitle(){ return ChatColor.GOLD + "Sprache wählen"; }

    // Optional: direkter Durchgriff falls du’s brauchst
    public I18nManager getI18n(){ return i18n; }
}
